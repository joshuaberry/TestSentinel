package com.testsentinel.example;

import com.testsentinel.core.ActionPlanAdvisor;
import com.testsentinel.core.TestSentinelClient;
import com.testsentinel.core.TestSentinelConfig;
import com.testsentinel.interceptor.TestSentinelListener;
import com.testsentinel.model.ConditionEvent;
import com.testsentinel.model.InsightResponse;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.events.EventFiringDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base test class for TestNG + Selenium suites using TestSentinel.
 *
 * Covers all features through the knowledge base update:
 *   Phase 1  — root cause analysis via Claude API
 *   Phase 2  — action plan generation
 *   Continue — NAVIGATED_PAST / STATE_ALREADY_SATISFIED detection
 *   KB       — local resolution from known-conditions.json (zero API cost)
 *
 * ## Resolution priority in every analysis call:
 *   1. Knowledge base match  → sub-ms, 0 tokens, confidence 1.0
 *   2. Claude API call       → 3-8s, ~1500-3500 tokens
 *
 * ## Configuration (environment variables):
 *   ANTHROPIC_API_KEY                 — required
 *   TESTSENTINEL_KNOWLEDGE_BASE_PATH  — path to known-conditions.json (optional)
 *   TESTSENTINEL_PHASE2_ENABLED       — true to enable action plans (default: false)
 *   TESTSENTINEL_MAX_RISK_LEVEL       — LOW | MEDIUM | HIGH (default: LOW)
 */
public class BaseSeleniumTest {

    protected static final Logger log = LoggerFactory.getLogger(BaseSeleniumTest.class);

    // Shared across suite — all fields are thread-safe by design
    protected static TestSentinelClient sentinel;
    protected static ActionPlanAdvisor  advisor;

    // Per-thread state for parallel execution
    private static final ThreadLocal<WebDriver>             driverHolder      = new ThreadLocal<>();
    private static final ThreadLocal<TestSentinelListener>  listenerHolder    = new ThreadLocal<>();
    private static final ThreadLocal<List<String>>          stepHistoryHolder = new ThreadLocal<>();

    // ── Suite Lifecycle ───────────────────────────────────────────────────────

    @BeforeSuite
    public void initTestSentinel() {
        TestSentinelConfig config = TestSentinelConfig.fromEnvironment();
        sentinel = new TestSentinelClient(config);
        advisor  = new ActionPlanAdvisor(config);
        log.info("TestSentinel initialized — phase2={}, maxRisk={}, knowledgeBase={} patterns",
            config.isPhase2Enabled(),
            config.getMaxRiskLevel(),
            sentinel.knowledgeBaseSize());
    }

    @AfterSuite
    public void tearDownSentinel() {
        log.info("TestSentinel suite complete — knowledge base has {} active patterns",
            sentinel.knowledgeBaseSize());
    }

    // ── Test Lifecycle ────────────────────────────────────────────────────────

    @BeforeMethod
    public void setUpDriver(Method method) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage");
        options.setCapability("goog:loggingPrefs", Map.of("browser", "ALL"));

        WebDriver rawDriver = new ChromeDriver(options);
        rawDriver.manage().window().maximize();

        String testName  = method.getName();
        String suiteName = method.getDeclaringClass().getSimpleName();

        TestSentinelListener listener = new TestSentinelListener(sentinel, testName, suiteName);
        WebDriver decoratedDriver = new EventFiringDecorator<>(listener).decorate(rawDriver);

        driverHolder.set(decoratedDriver);
        listenerHolder.set(listener);
        stepHistoryHolder.set(new ArrayList<>());
    }

    @AfterMethod
    public void tearDown(ITestResult result) {
        TestSentinelListener listener = listenerHolder.get();
        if (listener != null) {
            InsightResponse insight = listener.getLastInsight();
            if (insight != null) {

                if (insight.isContinuable()) {
                    // TestSentinel determined there is no actual problem — state is valid.
                    // Record as informational only; do NOT treat as failure.
                    String source = insight.isLocalResolution()
                        ? "[LOCAL:" + insight.getResolvedFromPattern() + "]"
                        : "[Claude API]";
                    log.info("TestSentinel: CONTINUE {} on test '{}' — {}",
                        source, result.getName(), insight.getRootCause());
                    result.setAttribute("testsentinel_outcome",      "CONTINUE");
                    result.setAttribute("testsentinel_source",       source);
                    result.setAttribute("testsentinel_root_cause",   insight.getRootCause());
                    if (insight.getContinueContext() != null) {
                        result.setAttribute("testsentinel_observed_state",
                            insight.getContinueContext().getObservedState());
                        if (insight.getContinueContext().hasCaveats()) {
                            result.setAttribute("testsentinel_caveat",
                                insight.getContinueContext().getCaveats());
                        }
                    }

                } else if (result.getStatus() == ITestResult.FAILURE) {
                    // Problem path — attach all available insight to the test result
                    String source = insight.isLocalResolution()
                        ? "[LOCAL:" + insight.getResolvedFromPattern() + "]"
                        : "[Claude API]";
                    result.setAttribute("testsentinel_insight",      insight);
                    result.setAttribute("testsentinel_source",       source);
                    result.setAttribute("testsentinel_root_cause",   insight.getRootCause());
                    result.setAttribute("testsentinel_category",     insight.getConditionCategory());
                    result.setAttribute("testsentinel_tokens",       insight.getAnalysisTokens());
                    result.setAttribute("testsentinel_latency_ms",   insight.getAnalysisLatencyMs());
                    if (insight.hasActionPlan()) {
                        result.setAttribute("testsentinel_plan_summary",
                            insight.getActionPlan().getPlanSummary());
                        result.setAttribute("testsentinel_recommendations",
                            advisor.buildReportSummary(insight));
                    }
                }
            }
        }

        WebDriver driver = driverHolder.get();
        if (driver != null) {
            try { driver.quit(); } catch (Exception ignored) {}
            driverHolder.remove();
        }
        listenerHolder.remove();
        stepHistoryHolder.remove();
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    protected WebDriver      getDriver() { return driverHolder.get(); }
    protected List<String>   getSteps()  { return stepHistoryHolder.get(); }

    /** Records a named step for context enrichment. Call before significant actions. */
    protected void recordStep(String description) {
        List<String> steps = stepHistoryHolder.get();
        if (steps != null) steps.add(description);
    }

    // ── Analysis Helpers ──────────────────────────────────────────────────────

    /**
     * Analyzes a caught exception. Checks knowledge base first; calls Claude only
     * if no local pattern matches.
     *
     * <pre>
     *   try {
     *       recordStep("Click submit");
     *       driver.findElement(By.id("submit")).click();
     *   } catch (NoSuchElementException e) {
     *       InsightResponse insight = analyzeAndAdvise(e);
     *       if (insight.isContinuable())  return;        // already submitted
     *       if (insight.isTransient())    retryAction();
     *       else throw new RuntimeException(insight.getRootCause(), e);
     *   }
     * </pre>
     */
    protected InsightResponse analyzeAndAdvise(Exception e) {
        InsightResponse insight = sentinel.analyzeException(
            getDriver(), e, getSteps(),
            Map.of("testName", "current test", "framework", "TestNG")
        );
        sentinel.logInsight(insight);
        if (!insight.isContinuable()) advisor.logRecommendations(insight);
        return insight;
    }

    /**
     * Analyzes a wrong-page or unexpected-state condition without an exception.
     * Use when the test detects an unexpected URL proactively.
     *
     * Returns isContinuable()=true for NAVIGATED_PAST or STATE_ALREADY_SATISFIED.
     *
     * <pre>
     *   if (!driver.getCurrentUrl().contains("/checkout")) {
     *       InsightResponse insight = analyzeAndAdvise(driver.getCurrentUrl(), "/checkout");
     *       if (insight.isContinuable()) {
     *           log.info("Already past checkout — continuing: {}", insight.getRootCause());
     *           return;
     *       }
     *       throw new RuntimeException("Navigation failed: " + insight.getRootCause());
     *   }
     * </pre>
     */
    protected InsightResponse analyzeAndAdvise(String actualUrl, String expectedUrl) {
        InsightResponse insight = sentinel.analyzeWrongPage(
            getDriver(), expectedUrl, getSteps(),
            Map.of("testName", "current test", "actualUrl", actualUrl)
        );
        sentinel.logInsight(insight);
        if (!insight.isContinuable()) advisor.logRecommendations(insight);
        return insight;
    }

    // ── Knowledge Base Helpers ────────────────────────────────────────────────

    /**
     * Promotes a confirmed Claude resolution to the knowledge base so future
     * occurrences of the same pattern resolve locally with zero API cost.
     *
     * Call this after confirming that a Claude-produced insight was correct:
     *
     * <pre>
     *   InsightResponse insight = analyzeAndAdvise(e);
     *   // ... confirm the resolution worked ...
     *   promoteToKnownPattern(lastEvent, insight, "cookie-banner-checkout", "j.smith");
     *   // Next occurrence: resolved in 0ms, 0 tokens
     * </pre>
     *
     * The ConditionEvent is available from the listener after analysis:
     *   ConditionEvent event = listenerHolder.get().getLastEvent();  // add getLastEvent() to listener
     *
     * Or build one manually for proactive registration:
     *   ConditionEvent event = ConditionEvent.builder()
     *       .conditionType(ConditionType.LOCATOR_NOT_FOUND)
     *       .message("Cookie banner blocks interaction")
     *       .currentUrl(driver.getCurrentUrl())
     *       .build();
     */
    protected void promoteToKnownPattern(ConditionEvent event, InsightResponse insight,
                                          String patternId, String addedBy) {
        sentinel.recordResolution(event, insight, patternId, addedBy);
    }

    /**
     * Reloads the knowledge base from disk after hand-editing known-conditions.json.
     * Call from a @BeforeClass or @BeforeSuite hook when editing patterns between runs.
     */
    protected void reloadKnowledgeBase() {
        sentinel.reloadKnowledgeBase();
    }
}
