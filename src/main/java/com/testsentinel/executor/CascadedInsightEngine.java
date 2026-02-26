package com.testsentinel.executor;

import com.testsentinel.core.TestSentinelClient;
import com.testsentinel.core.TestSentinelConfig;
import com.testsentinel.executor.checker.CheckerResult;
import com.testsentinel.executor.checker.ConditionChecker;
import com.testsentinel.executor.checker.ConditionCheckerRegistry;
import com.testsentinel.model.ConditionEvent;
import com.testsentinel.model.InsightResponse;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Option 4 orchestrator -- cascaded condition analysis with local-first, API-fallback.
 *
 * ## Resolution order per cascade pass
 *
 *   1. LOCAL CHECKERS (fastest, free)
 *      Registered {@link ConditionChecker} implementations inspect the live driver state
 *      in priority order. The first match short-circuits -- no KB or API call is made.
 *      Checkers cover known patterns: page timeouts, overlays, auth redirects, stale
 *      elements, wrong page, hidden elements.
 *
 *   2. KNOWLEDGE BASE (fast, free)
 *      If no checker matched, the TestSentinelClient checks its in-memory KnownCondition
 *      patterns against the event. Matches are sub-millisecond and use 0 API tokens.
 *
 *   3. CLAUDE API (slowest, costs tokens)
 *      Only called when both local checkers and KB found nothing. The event is sent to
 *      Claude, which returns an InsightResponse with a root cause and ActionPlan.
 *
 * ## Cascade loop
 *
 *   After each pass, the engine executes the insight's ActionPlan, then checks whether
 *   the condition still exists (by re-running checkers on the updated driver state).
 *   If it does, a new pass begins with the updated event, and the prior CascadeResult
 *   is added to the priorAttempts list so handlers avoid repeating failed actions.
 *
 *   The loop stops when:
 *     - A pass resolves the condition (checkers all return NO_MATCH on re-check)
 *     - The insight says CONTINUE, SKIP, or FAIL_WITH_CONTEXT
 *     - maxDepth is reached (default: 3)
 *     - A step with requiresVerification=true fails
 *
 * ## Usage
 *
 * <pre>
 *   CascadedInsightEngine engine = new CascadedInsightEngine(
 *       sentinelClient,
 *       new ConditionCheckerRegistry(),
 *       new ActionHandlerRegistry(),
 *       config
 *   );
 *
 *   List{@literal <}CascadeResult{@literal >} results = engine.analyze(driver, event);
 *   CascadeResult final = results.get(results.size() - 1);
 * </pre>
 */
public class CascadedInsightEngine {

    private static final Logger log = LoggerFactory.getLogger(CascadedInsightEngine.class);

    private static final int DEFAULT_MAX_DEPTH = 3;

    private final TestSentinelClient        sentinelClient;
    private final ConditionCheckerRegistry  checkerRegistry;
    private final ActionPlanExecutor        executor;
    private final int                       maxDepth;

    public CascadedInsightEngine(
            TestSentinelClient sentinelClient,
            ConditionCheckerRegistry checkerRegistry,
            ActionHandlerRegistry handlerRegistry,
            TestSentinelConfig config) {
        this(sentinelClient, checkerRegistry, handlerRegistry, config, DEFAULT_MAX_DEPTH);
    }

    public CascadedInsightEngine(
            TestSentinelClient sentinelClient,
            ConditionCheckerRegistry checkerRegistry,
            ActionHandlerRegistry handlerRegistry,
            TestSentinelConfig config,
            int maxDepth) {
        this.sentinelClient  = sentinelClient;
        this.checkerRegistry = checkerRegistry;
        this.executor        = new ActionPlanExecutor(
            handlerRegistry,
            config.getMaxRiskLevel(),
            false
        );
        this.maxDepth = maxDepth;
    }

    // ── Primary API ───────────────────────────────────────────────────────────

    /**
     * Analyzes the condition event using the cascade strategy and returns the
     * full history of cascade passes. The final element is the last attempt.
     *
     * Never throws -- all exceptions are caught and returned as error insights.
     */
    public List<CascadeResult> analyze(WebDriver driver, ConditionEvent event) {
        List<CascadeResult> history = new ArrayList<>();

        log.info("CascadedInsightEngine: Starting cascade analysis (maxDepth={}) for {} at {}",
            maxDepth, event.getConditionType(), event.getCurrentUrl());

        for (int depth = 1; depth <= maxDepth; depth++) {
            log.info("CascadedInsightEngine: -- Cascade pass {}/{} ----------------------", depth, maxDepth);

            CascadeResult result = runPass(depth, driver, event, history);
            history.add(result);

            // Stop if resolved or if the outcome says to stop
            if (result.isConditionResolved()) {
                log.info("CascadedInsightEngine: Condition resolved at depth {} -- stopping", depth);
                break;
            }

            if (shouldStopCascade(result)) {
                log.info("CascadedInsightEngine: Stopping cascade -- outcome={}",
                    result.getInsight() != null ? result.getInsight().getSuggestedTestOutcome() : "null");
                break;
            }

            if (depth < maxDepth) {
                // Refresh the event with the current URL for the next pass
                event = refreshEvent(driver, event);
            }
        }

        logFinalSummary(history);
        return Collections.unmodifiableList(history);
    }

    // ── Pass execution ────────────────────────────────────────────────────────

    private CascadeResult runPass(int depth, WebDriver driver,
                                   ConditionEvent event, List<CascadeResult> priorAttempts) {
        // ── Step 1: Local checkers ────────────────────────────────────────────
        for (ConditionChecker checker : checkerRegistry.getCheckers()) {
            CheckerResult checkerResult;
            try {
                checkerResult = checker.check(driver, event);
            } catch (Exception e) {
                log.warn("CascadedInsightEngine: Checker {} threw unexpectedly: {}",
                    checker.getClass().getSimpleName(), e.getMessage());
                continue;
            }

            if (checkerResult.isMatched()) {
                log.info("CascadedInsightEngine: [LOCAL CHECKER] '{}' matched -- category={}, confidence={}",
                    checkerResult.getCheckerId(), checkerResult.getCategory(),
                    String.format("%.0f%%", checkerResult.getConfidence() * 100));

                InsightResponse insight = buildInsightFromChecker(checkerResult);
                List<ActionResult> actionResults = executor.execute(insight, driver, event, priorAttempts);
                boolean resolved = verifyResolved(driver, event, priorAttempts);

                return CascadeResult.builder()
                    .depth(depth)
                    .source(CascadeResult.Source.LOCAL_CHECKER)
                    .checkerResult(checkerResult)
                    .insight(insight)
                    .actionResults(actionResults)
                    .conditionResolved(resolved)
                    .build();
            }
        }

        // ── Step 2: KB + API via TestSentinelClient ───────────────────────────
        log.info("CascadedInsightEngine: No local checker matched -- delegating to TestSentinelClient");
        InsightResponse insight;
        try {
            insight = sentinelClient.analyzeEvent(event);
        } catch (Exception e) {
            log.error("CascadedInsightEngine: TestSentinelClient threw: {}", e.getMessage(), e);
            insight = InsightResponse.error("Analysis failed: " + e.getMessage(), 0);
        }

        CascadeResult.Source source;
        if (insight.isLocalResolution()) {
            source = CascadeResult.Source.KNOWLEDGE_BASE;
        } else if (insight.getAnalysisTokens() == 0 &&
                   InsightResponse.SuggestedOutcome.INVESTIGATE.name().equals(insight.getSuggestedTestOutcome())) {
            source = CascadeResult.Source.UNKNOWN_RECORDED;
        } else {
            source = CascadeResult.Source.CLAUDE_API;
        }

        log.info("CascadedInsightEngine: [{}] category={}, confidence={}",
            source,
            insight.getConditionCategory(),
            String.format("%.0f%%", insight.getConfidence() * 100));

        List<ActionResult> actionResults = executor.execute(insight, driver, event, priorAttempts);
        boolean resolved = verifyResolved(driver, event, priorAttempts);

        return CascadeResult.builder()
            .depth(depth)
            .source(source)
            .checkerResult(null)
            .insight(insight)
            .actionResults(actionResults)
            .conditionResolved(resolved)
            .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Re-runs all checkers against the current driver state to determine whether
     * the original condition has been resolved by the actions taken this pass.
     */
    private boolean verifyResolved(WebDriver driver, ConditionEvent event,
                                    List<CascadeResult> priorAttempts) {
        for (ConditionChecker checker : checkerRegistry.getCheckers()) {
            try {
                CheckerResult r = checker.check(driver, event);
                if (r.isMatched()) {
                    log.debug("CascadedInsightEngine: Condition still present -- '{}' matched on re-check",
                        r.getCheckerId());
                    return false;
                }
            } catch (Exception ignored) {}
        }
        log.debug("CascadedInsightEngine: No checker matched on re-check -- condition appears resolved");
        return true;
    }

    /**
     * Returns true when the cascade should stop regardless of whether the
     * condition appears resolved. Stops on: SKIP, FAIL_WITH_CONTEXT,
     * CONTINUE (no problem), or INVESTIGATE (human needed).
     */
    private boolean shouldStopCascade(CascadeResult result) {
        if (result.getInsight() == null) return false;
        String outcome = result.getInsight().getSuggestedTestOutcome();
        if (outcome == null) return false;
        return switch (outcome) {
            case "SKIP", "FAIL_WITH_CONTEXT", "CONTINUE", "INVESTIGATE" -> true;
            default -> false;
        };
    }

    /**
     * Builds a new ConditionEvent with the updated current URL after actions
     * were executed, preserving the original condition type and diagnostics.
     */
    private ConditionEvent refreshEvent(WebDriver driver, ConditionEvent original) {
        try {
            String newUrl = driver.getCurrentUrl();
            if (newUrl.equals(original.getCurrentUrl())) return original;
            return ConditionEvent.builder()
                .conditionType(original.getConditionType())
                .message(original.getMessage())
                .currentUrl(newUrl)
                .expectedUrl(original.getExpectedUrl())
                .locatorStrategy(original.getLocatorStrategy())
                .locatorValue(original.getLocatorValue())
                .stackTrace(original.getStackTrace())
                .priorSteps(original.getPriorSteps())
                .frameworkMeta(original.getFrameworkMeta())
                .build();
        } catch (Exception e) {
            return original;
        }
    }

    /**
     * Converts a {@link CheckerResult} into a full {@link InsightResponse}
     * so the ActionPlanExecutor can consume it uniformly.
     */
    private InsightResponse buildInsightFromChecker(CheckerResult r) {
        InsightResponse insight = new InsightResponse();
        insight.setConditionId(UUID.randomUUID().toString());
        insight.setConditionCategory(r.getCategory());
        insight.setRootCause(r.getDiagnosis());
        insight.setConfidence(r.getConfidence());
        insight.setSuggestedTestOutcome(r.getSuggestedOutcome());
        insight.setActionPlan(r.getActionPlan());
        insight.setAnalysisTokens(0);
        insight.setAnalysisLatencyMs(0);
        insight.setAnalyzedAt(Instant.now());
        insight.setRawClaudeResponse("[LOCAL CHECKER] " + r.getCheckerId());
        // Note: resolvedFromPattern stays null -- this was a live checker, not a KB pattern.
        // Callers can distinguish via CascadeResult.getSource() == LOCAL_CHECKER.
        return insight;
    }

    private void logFinalSummary(List<CascadeResult> history) {
        log.info("CascadedInsightEngine: Analysis complete -- {} pass(es)", history.size());
        for (CascadeResult r : history) {
            log.info("  Pass {}: source={}, category={}, resolved={}, actions={}",
                r.depth(), r.getSource(),
                r.getInsight() != null ? r.getInsight().getConditionCategory() : "null",
                r.isConditionResolved(),
                r.getActionResults().size());
        }
    }
}
