package com.testsentinel.core;

import com.testsentinel.api.ClaudeApiGateway;
import com.testsentinel.model.ConditionEvent;
import com.testsentinel.model.ConditionType;
import com.testsentinel.model.InsightResponse;
import com.testsentinel.model.KnownCondition;
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

/**
 * TestSentinelClient — the primary integration point for test automation frameworks.
 *
 * ## Resolution Priority
 * Every call to analyzeEvent() follows this decision tree:
 *
 *   1. Knowledge Base check (local, 0ms, 0 tokens)
 *      If a KnownCondition pattern matches, return a locally-built InsightResponse immediately.
 *      No API call is made. Confidence is 1.0. latencyMs is sub-millisecond.
 *
 *   2. Claude API call (network, 3-8s, ~1500-3500 tokens)
 *      If no local match, build ConditionEvent, call Claude, parse response.
 *
 * ## Training the Knowledge Base
 * When Claude produces a resolution that an engineer confirms as correct, call:
 *   sentinel.recordResolution(event, insight, "pattern-id", "engineer-name")
 *
 * This writes a KnownCondition to the JSON file. The next occurrence of the same
 * pattern resolves locally — permanently, for free.
 *
 * ## Quick Start
 * <pre>
 *   TestSentinelConfig config = TestSentinelConfig.fromEnvironment();
 *   // Set TESTSENTINEL_KNOWLEDGE_BASE_PATH=/path/to/known-conditions.json to enable KB
 *
 *   TestSentinelClient sentinel = new TestSentinelClient(config);
 *
 *   try {
 *       driver.findElement(By.cssSelector(".submit-btn")).click();
 *   } catch (NoSuchElementException e) {
 *       InsightResponse insight = sentinel.analyzeException(driver, e, steps, meta);
 *       // insight.isLocalResolution() == true if resolved from KB
 *       // insight.isLocalResolution() == false if Claude was called
 *   }
 * </pre>
 *
 * Thread-safe. Designed to be shared across parallel test threads.
 */
public class TestSentinelClient {

    private static final Logger log = LoggerFactory.getLogger(TestSentinelClient.class);

    private final TestSentinelConfig config;
    private final ContextCollector contextCollector;
    private final ClaudeApiGateway apiGateway;
    private final PromptEngine promptEngine;
    private final KnownConditionRepository knowledgeBase; // null when KB not configured
    private final LocalResolutionBuilder localBuilder;

    public TestSentinelClient(TestSentinelConfig config) {
        this.config = config;
        this.contextCollector = new ContextCollector(config);
        this.apiGateway = new ClaudeApiGateway(config);
        this.promptEngine = new PromptEngine();
        this.localBuilder = new LocalResolutionBuilder();

        if (config.isKnowledgeBaseEnabled()) {
            this.knowledgeBase = new KnownConditionRepository(config.getKnowledgeBasePath());
            log.info("TestSentinel initialized — model={}, enabled={}, knowledgeBase={} patterns",
                config.getModel(), config.isEnabled(), knowledgeBase.size());
        } else {
            this.knowledgeBase = null;
            log.info("TestSentinel initialized — model={}, enabled={}, knowledgeBase=disabled",
                config.getModel(), config.isEnabled());
        }
    }

    // ── Primary API ───────────────────────────────────────────────────────────

    /**
     * Analyzes any exception caught during test execution.
     * Checks knowledge base first; calls Claude only if no local match found.
     */
    public InsightResponse analyzeException(WebDriver driver, Exception exception) {
        return analyzeException(driver, exception, Collections.emptyList(), Collections.emptyMap());
    }

    /**
     * Analyzes any exception caught during test execution, with step history and metadata.
     */
    public InsightResponse analyzeException(
            WebDriver driver,
            Exception exception,
            List<String> priorSteps,
            Map<String, String> testMeta
    ) {
        if (!config.isEnabled()) {
            log.debug("TestSentinel is disabled. Returning empty insight.");
            return InsightResponse.error("TestSentinel is disabled", 0);
        }

        ConditionType conditionType = mapExceptionToConditionType(exception);
        log.info("TestSentinel: Analyzing {} condition — {}", conditionType, exception.getMessage());

        ConditionEvent event = contextCollector.collect(driver, conditionType, exception, priorSteps, testMeta);
        return analyzeEvent(event);
    }

    /**
     * Analyzes a wrong-page condition where the test detects it is on an unexpected URL.
     * Checks knowledge base first; calls Claude only if no local match found.
     */
    public InsightResponse analyzeWrongPage(
            WebDriver driver,
            String expectedUrl,
            List<String> priorSteps,
            Map<String, String> testMeta
    ) {
        if (!config.isEnabled()) {
            return InsightResponse.error("TestSentinel is disabled", 0);
        }

        log.info("TestSentinel: Analyzing WRONG_PAGE condition — expected={}, actual={}",
            expectedUrl, safeGetUrl(driver));

        ConditionEvent event = contextCollector.collectWrongPage(driver, expectedUrl, priorSteps, testMeta);
        return analyzeEvent(event);
    }

    /**
     * Analyzes a pre-built ConditionEvent.
     *
     * Resolution priority:
     *   1. Local knowledge base match  — sub-millisecond, 0 tokens, confidence 1.0
     *   2. Claude API call             — 3-8s, ~1500-3500 tokens, confidence from model
     */
    public InsightResponse analyzeEvent(ConditionEvent event) {
        if (!config.isEnabled()) {
            return InsightResponse.error("TestSentinel is disabled", 0);
        }

        // ── Step 1: Local knowledge base ──────────────────────────────────────
        if (knowledgeBase != null) {
            long kbStart = System.currentTimeMillis();
            Optional<KnownCondition> match = knowledgeBase.findExactMatch(event);
            if (match.isPresent()) {
                KnownCondition kc = match.get();
                long latencyMs = System.currentTimeMillis() - kbStart;
                knowledgeBase.recordHit(kc);
                InsightResponse insight = localBuilder.build(kc, latencyMs);
                log.info("TestSentinel: [LOCAL] Pattern '{}' matched — API call skipped ({}ms, 0 tokens)",
                    kc.getId(), latencyMs);
                return insight;
            }
        }

        // ── Step 2: Claude API call ───────────────────────────────────────────
        long startMs = System.currentTimeMillis();
        try {
            var userContent = promptEngine.buildUserContent(event);
            return apiGateway.analyze(userContent);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startMs;
            log.error("TestSentinel: Unexpected error during analysis: {}", e.getMessage(), e);
            return InsightResponse.error("Unexpected error: " + e.getMessage(), latencyMs);
        }
    }

    // ── Knowledge Base Training ───────────────────────────────────────────────

    /**
     * Promotes a confirmed Claude resolution to the local knowledge base.
     *
     * After calling this, the next occurrence of the same condition pattern will
     * be resolved locally in sub-millisecond time with zero API cost.
     *
     * Signals (urlPattern, locatorValuePattern, conditionType) are inferred from the
     * ConditionEvent. Review the JSON file afterward to add domContains or exceptionType
     * signals for tighter matching specificity.
     *
     * @param event    The original ConditionEvent that triggered the analysis
     * @param insight  The InsightResponse confirmed to be correct by an engineer
     * @param id       Short human-readable pattern key, e.g. "cookie-banner-checkout"
     * @param addedBy  Engineer name or ID for audit trail
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
        kc.setDescription("Promoted from Claude analysis on " + Instant.now());
        kc.setEnabled(true);

        // Infer matching signals from the event
        kc.setUrlPattern(trimToNull(event.getCurrentUrl()));
        kc.setLocatorValuePattern(trimToNull(event.getLocatorValue()));
        kc.setConditionType(
            event.getConditionType() != null ? event.getConditionType().name() : null);
        // domContains and exceptionType are intentionally not auto-populated — they require
        // manual curation to avoid over-broad matches. Add them directly in the JSON file.

        int signalCount = countNonNull(kc.getUrlPattern(), kc.getLocatorValuePattern(), kc.getConditionType());
        kc.setMinMatchSignals(signalCount > 1 ? 2 : 1);

        // Copy resolution verbatim from confirmed insight
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
        log.info("TestSentinel: Pattern '{}' added to knowledge base by {} — future occurrences resolve locally",
            id, addedBy);
    }

    /**
     * Reloads the knowledge base from disk without restarting the suite.
     * Call this from a @BeforeSuite or @BeforeClass hook after hand-editing known-conditions.json.
     */
    public void reloadKnowledgeBase() {
        if (knowledgeBase != null) {
            knowledgeBase.reload();
            log.info("TestSentinel: Knowledge base reloaded — {} active patterns", knowledgeBase.size());
        } else {
            log.warn("TestSentinel: Cannot reload — knowledge base not configured");
        }
    }

    /**
     * Returns the number of active patterns currently loaded.
     * Returns 0 if the knowledge base is not configured.
     */
    public int knowledgeBaseSize() {
        return knowledgeBase != null ? knowledgeBase.size() : 0;
    }

    /**
     * Returns true if the knowledge base contains a pattern with the given id.
     * Returns false if the knowledge base is not configured or the id is not found.
     */
    public boolean hasPattern(String id) {
        if (knowledgeBase == null || id == null) return false;
        return knowledgeBase.findAll().stream()
            .anyMatch(kc -> id.equals(kc.getId()));
    }

    // ── Convenience Logging ───────────────────────────────────────────────────

    /**
     * Logs the InsightResponse at INFO level in a human-readable format.
     * Shows [LOCAL] source tag when resolved from the knowledge base.
     */
    public void logInsight(InsightResponse insight) {
        if (insight == null) return;

        // CONTINUE path — green-light signal
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
                if (ctx.hasResumeHint())  log.info("║  Resume At : {}", ctx.getResumeFromStepHint());
                if (ctx.hasCaveats())     log.info("║  ⚠ Caveat  : {}", ctx.getCaveats());
            }
            log.info("║  Latency   : {}ms  |  Tokens: {}", insight.getAnalysisLatencyMs(), insight.getAnalysisTokens());
            log.info("╚═════════════════════════════════════════════════════════════╝");
            return;
        }

        // Problem path
        String source = insight.isLocalResolution()
            ? "[LOCAL:" + insight.getResolvedFromPattern() + "]"
            : "[Claude API]";
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
            log.info("║  ─────────────────────────────────────────────────────────── ║");
            log.info("║  Action Plan : {} ({}%)", plan.getPlanSummary(),
                Math.round(plan.getPlanConfidence() * 100));
            log.info("║  Human Needed: {}", plan.isRequiresHuman() ? "Yes" : "No");
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
