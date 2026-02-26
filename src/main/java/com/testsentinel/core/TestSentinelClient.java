package com.testsentinel.core;

import com.testsentinel.api.ClaudeApiGateway;
import com.testsentinel.executor.ActionHandlerRegistry;
import com.testsentinel.executor.ActionPlanExecutor;
import com.testsentinel.executor.ActionResult;
import com.testsentinel.model.ActionStep;
import com.testsentinel.model.ConditionEvent;
import com.testsentinel.model.ConditionType;
import com.testsentinel.model.InsightResponse;
import com.testsentinel.model.KnownCondition;
import com.testsentinel.model.UnknownConditionRecord;
import com.testsentinel.prompt.PromptEngine;
import com.testsentinel.util.ContextCollector;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * TestSentinelClient — the primary integration point for test automation frameworks.
 *
 * ## Resolution Priority (Offline-First)
 * Every call to analyzeEvent() follows this decision tree:
 *
 *   1. Knowledge Base check (local, 0ms, 0 tokens)
 *      If a KnownCondition pattern matches, return a locally-built InsightResponse
 *      immediately. Confidence is 1.0. No API call is made.
 *
 *   2. Offline recording (when offlineMode = true)
 *      If no KB match, record an UnknownConditionRecord for human review and
 *      return a graceful INVESTIGATE response. No API call is made.
 *
 *   3. Claude API call (only when offlineMode = false and API key configured)
 *      Falls back to Claude for root cause analysis when offline mode is disabled.
 *
 * ## Autonomous Action Execution
 * When a KB match includes an action plan, TestSentinelClient automatically
 * executes LOW-risk steps via ActionPlanExecutor when a WebDriver is available
 * (i.e., when analyzeException() or analyzeWrongPage() is called directly).
 *
 * ## Training the Knowledge Base
 * Engineers add patterns directly to known-conditions.json (hand-edit or via
 * addPattern()) after reviewing unknown-conditions-log.json records.
 */
public class TestSentinelClient {

    private static final Logger log = LoggerFactory.getLogger(TestSentinelClient.class);

    private final TestSentinelConfig config;
    private final ContextCollector contextCollector;
    private final ClaudeApiGateway apiGateway;
    private final PromptEngine promptEngine;
    private final KnownConditionRepository knowledgeBase;
    private final LocalResolutionBuilder localBuilder;
    private final UnknownConditionRecorder recorder;
    private final ActionPlanExecutor autoExecutor;

    // Last auto-execution results — useful for test assertions in single-threaded suites
    private volatile List<ActionResult> lastAutoActionResults = Collections.emptyList();

    // Full history of every analysis performed during this client's lifetime.
    // One entry per public analyzeXxx() call, in call order.
    private final List<InsightRecord> insightHistory = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * One entry per analysis call — pairs the produced InsightResponse with the
     * list of ActionResults that were auto-executed immediately after (may be empty).
     */
    public record InsightRecord(InsightResponse insight, List<ActionResult> actions) {}

    public TestSentinelClient(TestSentinelConfig config) {
        this(config, null);
    }

    public TestSentinelClient(TestSentinelConfig config, UnknownConditionRecorder recorder) {
        this.config = config;
        this.recorder = recorder;
        this.contextCollector = new ContextCollector(config);
        this.apiGateway = new ClaudeApiGateway(config);
        this.promptEngine = new PromptEngine();
        this.localBuilder = new LocalResolutionBuilder();
        this.autoExecutor = new ActionPlanExecutor(
            new ActionHandlerRegistry(),
            ActionStep.RiskLevel.LOW,
            false
        );

        if (config.isKnowledgeBaseEnabled()) {
            this.knowledgeBase = new KnownConditionRepository(config.getKnowledgeBasePath());
            log.info("TestSentinel initialized — offline={}, KB={} patterns, unknownLog={}",
                config.isOfflineMode(),
                knowledgeBase.size(),
                recorder != null ? config.getUnknownConditionLogPath() : "disabled");
        } else {
            this.knowledgeBase = null;
            log.info("TestSentinel initialized — offline={}, KB=disabled, unknownLog={}",
                config.isOfflineMode(),
                recorder != null ? config.getUnknownConditionLogPath() : "disabled");
        }
    }

    // ── Primary API ───────────────────────────────────────────────────────────

    /**
     * Analyzes any exception caught during test execution.
     * Auto-executes LOW-risk KB action plan steps when the condition matches locally.
     */
    public InsightResponse analyzeException(WebDriver driver, Exception exception) {
        return analyzeException(driver, exception, Collections.emptyList(), Collections.emptyMap());
    }

    /**
     * Analyzes any exception caught during test execution, with step history and metadata.
     * Auto-executes LOW-risk KB action plan steps when the condition matches locally.
     */
    public InsightResponse analyzeException(
            WebDriver driver,
            Exception exception,
            List<String> priorSteps,
            Map<String, String> testMeta
    ) {
        ConditionType conditionType = mapExceptionToConditionType(exception);
        log.info("TestSentinel: Analyzing {} condition — {}", conditionType, exception.getMessage());

        ConditionEvent event = contextCollector.collect(driver, conditionType, exception, priorSteps, testMeta);
        InsightResponse insight = analyzeEventCore(event);
        autoExecuteIfApplicable(insight, driver, event);
        insightHistory.add(new InsightRecord(insight, new java.util.ArrayList<>(lastAutoActionResults)));
        return insight;
    }

    /**
     * Analyzes a wrong-page condition where the test detects it is on an unexpected URL.
     * Auto-executes LOW-risk KB action plan steps when the condition matches locally.
     */
    public InsightResponse analyzeWrongPage(
            WebDriver driver,
            String expectedUrl,
            List<String> priorSteps,
            Map<String, String> testMeta
    ) {
        log.info("TestSentinel: Analyzing WRONG_PAGE condition — expected={}, actual={}",
            expectedUrl, safeGetUrl(driver));

        ConditionEvent event = contextCollector.collectWrongPage(driver, expectedUrl, priorSteps, testMeta);
        InsightResponse insight = analyzeEventCore(event);
        autoExecuteIfApplicable(insight, driver, event);
        insightHistory.add(new InsightRecord(insight, new java.util.ArrayList<>(lastAutoActionResults)));
        return insight;
    }

    /**
     * Analyzes a pre-built ConditionEvent (no driver available — no auto-execution).
     *
     * Resolution priority:
     *   1. Local knowledge base match  — sub-millisecond, 0 tokens, confidence 1.0
     *   2. Offline recorder            — records for human review when offlineMode=true
     *   3. Claude API call             — only when offlineMode=false and API key is set
     */
    public InsightResponse analyzeEvent(ConditionEvent event) {
        InsightResponse insight = analyzeEventCore(event);
        insightHistory.add(new InsightRecord(insight, Collections.emptyList()));
        return insight;
    }

    /**
     * Core resolution logic shared by all three public analyzeXxx() methods.
     * Does not record to insightHistory — callers do that after actions are resolved.
     */
    private InsightResponse analyzeEventCore(ConditionEvent event) {
        // ── Step 1: Local knowledge base ─────────────────────────────────────
        if (knowledgeBase != null) {
            long kbStart = System.currentTimeMillis();
            Optional<KnownCondition> match = knowledgeBase.findExactMatch(event);
            if (match.isPresent()) {
                KnownCondition kc = match.get();
                long latencyMs = System.currentTimeMillis() - kbStart;
                knowledgeBase.recordHit(kc);
                InsightResponse insight = localBuilder.build(kc, latencyMs);
                log.info("TestSentinel: [LOCAL KB] Pattern '{}' matched — API call skipped ({}ms, 0 tokens)",
                    kc.getId(), latencyMs);
                return insight;
            }
        }

        // ── Step 2: Offline — record for human review ─────────────────────────
        if (config.isOfflineMode()) {
            log.info("TestSentinel: [OFFLINE] No KB match — recording unknown condition for human review");
            if (recorder != null) {
                recorder.record(event);
            }
            return buildOfflineNoMatchResponse(event);
        }

        // ── Step 3: Claude API call ───────────────────────────────────────────
        if (!config.isApiEnabled()) {
            log.debug("TestSentinel: No KB match and API disabled — returning error insight");
            return InsightResponse.error("TestSentinel: no KB match and API is disabled", 0);
        }
        long startMs = System.currentTimeMillis();
        try {
            var userContent = promptEngine.buildUserContent(event);
            return apiGateway.analyze(userContent);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startMs;
            log.error("TestSentinel: Unexpected error during API analysis: {}", e.getMessage(), e);
            return InsightResponse.error("Unexpected error: " + e.getMessage(), latencyMs);
        }
    }

    // ── Knowledge Base Management ─────────────────────────────────────────────

    /**
     * Adds a KnownCondition pattern directly to the knowledge base.
     * The pattern is persisted to the JSON file immediately.
     */
    public void addPattern(KnownCondition kc) {
        if (knowledgeBase == null) {
            log.warn("TestSentinel: Cannot add pattern — TESTSENTINEL_KNOWLEDGE_BASE_PATH not configured");
            return;
        }
        knowledgeBase.add(kc);
        log.info("TestSentinel: Pattern '{}' added to knowledge base directly", kc.getId());
    }

    /**
     * Removes (disables) a pattern from the knowledge base.
     * The pattern is disabled in memory and marked disabled on disk.
     */
    public void removePattern(String id) {
        if (knowledgeBase == null) return;
        knowledgeBase.disable(id);
        log.info("TestSentinel: Pattern '{}' disabled/removed from knowledge base", id);
    }

    /**
     * Promotes a confirmed Claude resolution to the local knowledge base.
     * Only useful when offlineMode=false and a Claude result is available.
     */
    public void recordResolution(ConditionEvent event, InsightResponse insight,
                                  String id, String addedBy) {
        if (knowledgeBase == null) {
            log.warn("TestSentinel: Cannot record resolution — TESTSENTINEL_KNOWLEDGE_BASE_PATH not configured");
            return;
        }
        if (insight.isLocalResolution()) {
            log.debug("TestSentinel: Skipping recordResolution — insight already came from local KB");
            return;
        }

        KnownCondition kc = new KnownCondition();
        kc.setId(id);
        kc.setDescription("Promoted from analysis on " + Instant.now());
        kc.setEnabled(true);

        kc.setUrlPattern(trimToNull(event.getCurrentUrl()));
        kc.setLocatorValuePattern(trimToNull(event.getLocatorValue()));
        kc.setConditionType(
            event.getConditionType() != null ? event.getConditionType().name() : null);

        int signalCount = countNonNull(kc.getUrlPattern(), kc.getLocatorValuePattern(), kc.getConditionType());
        kc.setMinMatchSignals(Math.max(2, signalCount));

        kc.setConditionCategory(
            insight.getConditionCategory() != null ? insight.getConditionCategory().name() : null);
        kc.setRootCause(insight.getRootCause());
        kc.setEvidenceHighlights(insight.getEvidenceHighlights());
        kc.setTransient(insight.isTransient());
        kc.setSuggestedTestOutcome(insight.getSuggestedTestOutcome());
        kc.setActionPlan(insight.getActionPlan());
        kc.setContinueContext(insight.getContinueContext());
        kc.setAddedBy(addedBy);
        kc.setAddedAt(Instant.now());

        knowledgeBase.add(kc);
        log.info("TestSentinel: Pattern '{}' promoted to knowledge base by {}", id, addedBy);
    }

    /**
     * Reloads the knowledge base from disk without restarting the suite.
     */
    public void reloadKnowledgeBase() {
        if (knowledgeBase != null) {
            knowledgeBase.reload();
            log.info("TestSentinel: Knowledge base reloaded — {} active patterns", knowledgeBase.size());
        } else {
            log.warn("TestSentinel: Cannot reload — knowledge base not configured");
        }
    }

    public int knowledgeBaseSize() {
        return knowledgeBase != null ? knowledgeBase.size() : 0;
    }

    public boolean hasPattern(String id) {
        if (knowledgeBase == null || id == null) return false;
        return knowledgeBase.findAll().stream().anyMatch(kc -> id.equals(kc.getId()));
    }

    // ── Recorder Access ───────────────────────────────────────────────────────

    /** Returns the unknown condition recorder, or null if not configured. */
    public UnknownConditionRecorder getRecorder() { return recorder; }

    /** Returns all unknown condition records, or empty if recorder not configured. */
    public List<UnknownConditionRecord> getUnknownConditionRecords() {
        return recorder != null ? recorder.getRecords() : Collections.emptyList();
    }

    /** Returns the action results from the most recent auto-execution (single-threaded suites only). */
    public List<ActionResult> getLastAutoActionResults() { return lastAutoActionResults; }

    /** Returns an unmodifiable view of every insight analysis this client has performed, in call order. */
    public List<InsightRecord> getInsightHistory() { return Collections.unmodifiableList(insightHistory); }

    // ── Convenience Logging ───────────────────────────────────────────────────

    public void logInsight(InsightResponse insight) {
        if (insight == null) return;

        if (insight.isContinuable()) {
            log.info("╔══ TestSentinel: CONTINUE — No Problem Detected ══════════════╗");
            log.info("║  Category  : {}", insight.getConditionCategory());
            log.info("║  Confidence: {}%  |  Source: {}",
                Math.round(insight.getConfidence() * 100),
                insight.isLocalResolution() ? "[LOCAL] " + insight.getResolvedFromPattern() : "Claude API");
            log.info("║  Reason    : {}", insight.getRootCause());
            if (insight.getContinueContext() != null) {
                var ctx = insight.getContinueContext();
                log.info("║  State     : {}", ctx.getObservedState());
                if (ctx.hasResumeHint()) log.info("║  Resume At : {}", ctx.getResumeFromStepHint());
                if (ctx.hasCaveats())    log.info("║  ⚠ Caveat  : {}", ctx.getCaveats());
            }
            log.info("║  Latency   : {}ms  |  Tokens: {}", insight.getAnalysisLatencyMs(), insight.getAnalysisTokens());
            log.info("╚═════════════════════════════════════════════════════════════╝");
            return;
        }

        String source = insight.isLocalResolution()
            ? "[LOCAL:" + insight.getResolvedFromPattern() + "]"
            : (insight.getAnalysisTokens() == 0 ? "[OFFLINE-UNMATCHED]" : "[Claude API]");
        log.info("╔══ TestSentinel Insight {} ═══════════════════════════════════╗", source);
        log.info("║  Category    : {}", insight.getConditionCategory());
        log.info("║  Confidence  : {}%", Math.round(insight.getConfidence() * 100));
        log.info("║  Transient   : {}", insight.isTransient() ? "Yes — retry may resolve" : "No — persistent condition");
        log.info("║  Root Cause  : {}", insight.getRootCause());
        log.info("║  Outcome     : {}", insight.getSuggestedTestOutcome());
        if (insight.getEvidenceHighlights() != null) {
            insight.getEvidenceHighlights().forEach(e -> log.info("║  Evidence    : {}", e));
        }
        if (insight.hasActionPlan()) {
            var plan = insight.getActionPlan();
            log.info("║  Action Plan : {} ({}%)", plan.getPlanSummary(),
                Math.round(plan.getPlanConfidence() * 100));
            for (int i = 0; i < plan.getActions().size(); i++) {
                var step = plan.getActions().get(i);
                log.info("║  Step {}  [{}][{}] {} ({}%)",
                    i + 1, step.getActionType(), step.getRiskLevel(),
                    step.getDescription(), Math.round(step.getConfidence() * 100));
            }
        }
        log.info("║  Latency     : {}ms  |  Tokens: {}", insight.getAnalysisLatencyMs(), insight.getAnalysisTokens());
        log.info("╚═════════════════════════════════════════════════════════════╝");
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private void autoExecuteIfApplicable(InsightResponse insight, WebDriver driver, ConditionEvent event) {
        if (!insight.isLocalResolution() || !insight.hasActionPlan() || driver == null) return;
        log.info("TestSentinel: Auto-executing LOW-risk KB action plan for pattern '{}'",
            insight.getResolvedFromPattern());
        List<ActionResult> results = autoExecutor.execute(insight, driver, event, Collections.emptyList());
        this.lastAutoActionResults = results;
    }

    private InsightResponse buildOfflineNoMatchResponse(ConditionEvent event) {
        InsightResponse r = new InsightResponse();
        r.setConditionId(UUID.randomUUID().toString());
        r.setConditionCategory(InsightResponse.ConditionCategory.UNKNOWN);
        r.setRootCause("No matching pattern found in the local knowledge base. " +
            "This condition has been recorded in the unknown conditions log for human review. " +
            "Add a pattern to known-conditions.json to resolve this condition automatically in future runs.");
        r.setConfidence(0.0);
        r.setTransient(false);
        r.setSuggestedTestOutcome(InsightResponse.SuggestedOutcome.INVESTIGATE.name());
        r.setAnalysisTokens(0);
        r.setAnalysisLatencyMs(0);
        r.setAnalyzedAt(Instant.now());
        r.setRawClaudeResponse("[OFFLINE] No KB match — recorded for human review");
        return r;
    }

    private ConditionType mapExceptionToConditionType(Exception exception) {
        if (exception == null) return ConditionType.EXCEPTION;
        return switch (exception.getClass().getSimpleName()) {
            case "NoSuchElementException"         -> ConditionType.LOCATOR_NOT_FOUND;
            case "TimeoutException"               -> ConditionType.TIMEOUT;
            case "StaleElementReferenceException" -> ConditionType.EXCEPTION;
            case "WebDriverException"             -> ConditionType.EXCEPTION;
            default                               -> ConditionType.EXCEPTION;
        };
    }

    private String safeGetUrl(WebDriver driver) {
        try { return driver.getCurrentUrl(); } catch (Exception e) { return "unavailable"; }
    }

    private static String trimToNull(String s) {
        return (s != null && !s.isBlank()) ? s.trim() : null;
    }

    private static int countNonNull(Object... values) {
        int count = 0;
        for (Object v : values) if (v != null) count++;
        return count;
    }
}
