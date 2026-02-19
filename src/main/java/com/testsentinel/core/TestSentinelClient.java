package com.testsentinel.core;

import com.testsentinel.api.ClaudeApiGateway;
import com.testsentinel.model.ConditionEvent;
import com.testsentinel.model.ConditionType;
import com.testsentinel.model.InsightResponse;
import com.testsentinel.prompt.PromptEngine;
import com.testsentinel.util.ContextCollector;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * TestSentinelClient — the primary integration point for test automation frameworks.
 *
 * ## Quick Start
 * <pre>
 *   // 1. Initialize once per test suite (or per test class)
 *   TestSentinelClient sentinel = new TestSentinelClient(
 *       TestSentinelConfig.fromEnvironment()
 *   );
 *
 *   // 2. In your test, wrap risky operations:
 *   try {
 *       driver.findElement(By.cssSelector(".submit-btn")).click();
 *   } catch (NoSuchElementException e) {
 *       InsightResponse insight = sentinel.analyzeException(driver, e, stepHistory, testMeta);
 *       // Attach insight to test report, decide retry/fail based on insight
 *       reporter.attachInsight(insight);
 *       if (insight.isTransient()) {
 *           // retry logic
 *       } else {
 *           throw e; // genuine failure
 *       }
 *   }
 * </pre>
 *
 * The client is thread-safe and designed to be shared across parallel test threads.
 */
public class TestSentinelClient {

    private static final Logger log = LoggerFactory.getLogger(TestSentinelClient.class);

    private final TestSentinelConfig config;
    private final ContextCollector contextCollector;
    private final ClaudeApiGateway apiGateway;
    private final PromptEngine promptEngine;

    public TestSentinelClient(TestSentinelConfig config) {
        this.config = config;
        this.contextCollector = new ContextCollector(config);
        this.apiGateway = new ClaudeApiGateway(config);
        this.promptEngine = new PromptEngine();

        log.info("TestSentinel initialized — model={}, enabled={}", config.getModel(), config.isEnabled());
    }

    // ── Primary API ───────────────────────────────────────────────────────────

    /**
     * Analyzes any exception caught during test execution.
     * Automatically maps common Selenium exceptions to the appropriate ConditionType.
     *
     * @param driver    Active WebDriver session
     * @param exception The caught exception
     * @return InsightResponse with root cause analysis
     */
    public InsightResponse analyzeException(WebDriver driver, Exception exception) {
        return analyzeException(driver, exception, Collections.emptyList(), Collections.emptyMap());
    }

    /**
     * Analyzes any exception caught during test execution, with test step history and metadata.
     *
     * @param driver     Active WebDriver session
     * @param exception  The caught exception
     * @param priorSteps Ordered list of test step descriptions (e.g., ["Navigate to login page", "Enter username"])
     * @param testMeta   Test metadata map (e.g., {"testName": "loginTest", "suiteName": "SmokeTests"})
     * @return InsightResponse with root cause analysis
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
     *
     * @param driver      Active WebDriver session
     * @param expectedUrl The URL pattern or full URL the test expected
     * @param priorSteps  Ordered list of test step descriptions
     * @param testMeta    Test metadata map
     * @return InsightResponse with navigation root cause analysis
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
     * Analyzes a pre-built ConditionEvent directly.
     * Use this for custom conditions or when you need full control over the payload.
     *
     * @param event A fully constructed ConditionEvent
     * @return InsightResponse with root cause analysis
     */
    public InsightResponse analyzeEvent(ConditionEvent event) {
        if (!config.isEnabled()) {
            return InsightResponse.error("TestSentinel is disabled", 0);
        }

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

    // ── Convenience logging ───────────────────────────────────────────────────

    /**
     * Logs the InsightResponse to SLF4J at INFO level in a human-readable format.
     * Includes Phase 2 ActionPlan details when present.
     */
    public void logInsight(InsightResponse insight) {
        if (insight == null) return;

        // CONTINUE path — distinct formatting so it stands out as a green-light signal
        if (insight.isContinuable()) {
            log.info("╔══ TestSentinel: CONTINUE \u2014 No Problem Detected ══════════════╗");
            log.info("║  Category  : {}", insight.getConditionCategory());
            log.info("║  Confidence: {}%", Math.round(insight.getConfidence() * 100));
            log.info("║  Reason    : {}", insight.getRootCause());
            if (insight.getContinueContext() != null) {
                var ctx = insight.getContinueContext();
                log.info("║  State     : {}", ctx.getObservedState());
                if (ctx.hasResumeHint()) {
                    log.info("║  Resume At : {}", ctx.getResumeFromStepHint());
                }
                if (ctx.hasCaveats()) {
                    log.info("║  \u26A0 Caveat  : {}", ctx.getCaveats());
                }
            }
            log.info("║  Latency   : {}ms  |  Tokens: {}", insight.getAnalysisLatencyMs(), insight.getAnalysisTokens());
            log.info("╚\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255D");
            return;
        }

        // Problem path — existing format
        log.info("╔══ TestSentinel Insight \u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557");
        log.info("║  Category    : {}", insight.getConditionCategory());
        log.info("║  Confidence  : {}%", Math.round(insight.getConfidence() * 100));
        log.info("║  Transient   : {}", insight.isTransient() ? "Yes \u2014 retry may resolve" : "No \u2014 persistent condition");
        log.info("║  Root Cause  : {}", insight.getRootCause());
        log.info("║  Outcome     : {}", insight.getSuggestedTestOutcome());
        if (insight.getEvidenceHighlights() != null) {
            insight.getEvidenceHighlights().forEach(e -> log.info("║  Evidence    : {}", e));
        }
        if (insight.hasActionPlan()) {
            var plan = insight.getActionPlan();
            log.info("║  \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500 \u2551");
            log.info("║  Action Plan : {} (confidence {}%)", plan.getPlanSummary(),
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
        log.info("╚\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255D");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ConditionType mapExceptionToConditionType(Exception exception) {
        if (exception == null) return ConditionType.EXCEPTION;
        String className = exception.getClass().getSimpleName();
        return switch (className) {
            case "NoSuchElementException"      -> ConditionType.LOCATOR_NOT_FOUND;
            case "TimeoutException"            -> ConditionType.TIMEOUT;
            case "StaleElementReferenceException" -> ConditionType.EXCEPTION;
            case "WebDriverException"          -> ConditionType.EXCEPTION;
            default                            -> ConditionType.EXCEPTION;
        };
    }

    private String safeGetUrl(WebDriver driver) {
        try { return driver.getCurrentUrl(); } catch (Exception e) { return "unavailable"; }
    }
}
