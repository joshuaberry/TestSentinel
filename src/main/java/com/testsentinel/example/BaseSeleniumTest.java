package com.testsentinel.example;

import com.testsentinel.core.TestSentinelClient;
import com.testsentinel.core.TestSentinelConfig;
import com.testsentinel.interceptor.TestSentinelListener;
import com.testsentinel.model.InsightResponse;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
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
 * Base test class demonstrating TestSentinel integration with TestNG + Selenium.
 *
 * Integration Strategy Used Here:
 * ─────────────────────────────────────────────────────────────────────────────
 * OPTION A (Zero-touch via EventFiringDecorator):
 *   - TestSentinelListener wraps the driver via EventFiringDecorator
 *   - All Selenium exceptions are automatically intercepted and analyzed
 *   - No changes needed in individual test methods
 *   - Access the last insight via listener.getLastInsight() in @AfterMethod
 *
 * OPTION B (Explicit in test method):
 *   - Call sentinel.analyzeException(driver, e, steps, meta) in catch blocks
 *   - More control; better for targeted analysis in specific test methods
 *
 * Both options are demonstrated below.
 * ─────────────────────────────────────────────────────────────────────────────
 */
public class BaseSeleniumTest {

    protected static final Logger log = LoggerFactory.getLogger(BaseSeleniumTest.class);

    // Shared across suite — thread-safe
    protected static TestSentinelClient sentinel;

    // Per-thread (for parallel execution)
    private static final ThreadLocal<WebDriver> driverHolder = new ThreadLocal<>();
    private static final ThreadLocal<TestSentinelListener> listenerHolder = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> stepHistoryHolder = new ThreadLocal<>();

    // ── Suite Setup ───────────────────────────────────────────────────────────

    @BeforeSuite
    public void initTestSentinel() {
        // Initialize from environment variables
        // Set ANTHROPIC_API_KEY in your environment or CI/CD secrets
        sentinel = new TestSentinelClient(TestSentinelConfig.fromEnvironment());
        log.info("TestSentinel initialized for suite");
    }

    @AfterSuite
    public void tearDownSentinel() {
        log.info("TestSentinel suite complete");
    }

    // ── Test Setup ────────────────────────────────────────────────────────────

    @BeforeMethod
    public void setUpDriver(Method method) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--no-sandbox", "--disable-dev-shm-usage");
        // Enable browser console log capture
        options.setCapability("goog:loggingPrefs", Map.of("browser", "ALL"));

        WebDriver rawDriver = new ChromeDriver(options);
        rawDriver.manage().window().maximize();

        // OPTION A: Wrap with EventFiringDecorator for automatic interception
        String testName = method.getName();
        String suiteName = method.getDeclaringClass().getSimpleName();

        TestSentinelListener listener = new TestSentinelListener(sentinel, testName, suiteName);
        WebDriver decoratedDriver = new EventFiringDecorator<>(listener).decorate(rawDriver);

        driverHolder.set(decoratedDriver);
        listenerHolder.set(listener);
        stepHistoryHolder.set(new ArrayList<>());

        log.info("Driver initialized for test: {}", testName);
    }

    @AfterMethod
    public void tearDown(ITestResult result) {
        // Attach insight to test result if available (Option A auto-interception)
        TestSentinelListener listener = listenerHolder.get();
        if (listener != null) {
            InsightResponse insight = listener.getLastInsight();
            if (insight != null && result.getStatus() == ITestResult.FAILURE) {
                // Attach the insight as a TestNG attribute — visible in reports
                result.setAttribute("testsentinel_insight", insight);
                result.setAttribute("testsentinel_root_cause", insight.getRootCause());
                result.setAttribute("testsentinel_category", insight.getConditionCategory());

                log.info("TestSentinel insight attached to failed test: {}",
                    result.getName());
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

    // ── Accessor Methods for Subclasses ──────────────────────────────────────

    protected WebDriver getDriver() { return driverHolder.get(); }
    protected List<String> getSteps() { return stepHistoryHolder.get(); }

    /**
     * Manually record a test step for context enrichment.
     * Call this before significant actions for richer analysis context.
     */
    protected void recordStep(String description) {
        List<String> steps = stepHistoryHolder.get();
        if (steps != null) {
            steps.add(description);
            log.debug("Step recorded: {}", description);
        }
    }

    // ── OPTION B: Explicit Analysis Helper ───────────────────────────────────

    /**
     * OPTION B integration: Call this in your catch block for explicit,
     * targeted analysis with full step context.
     *
     * <pre>
     *   try {
     *       recordStep("Click submit button");
     *       driver.findElement(By.id("submit")).click();
     *   } catch (NoSuchElementException e) {
     *       InsightResponse insight = analyzeAndLog(e);
     *       if (insight.isTransient()) {
     *           Thread.sleep(2000);
     *           // retry...
     *       } else {
     *           throw e;
     *       }
     *   }
     * </pre>
     */
    protected InsightResponse analyzeAndLog(Exception e) {
        InsightResponse insight = sentinel.analyzeException(
            getDriver(),
            e,
            getSteps(),
            Map.of("testName", "current test", "framework", "TestNG")
        );
        sentinel.logInsight(insight);
        return insight;
    }

    // ── Example Test Methods ──────────────────────────────────────────────────

    /**
     * Example: OPTION A — Zero-touch. The listener auto-intercepts the exception.
     * TestSentinel analysis appears in logs automatically.
     * Access the insight in @AfterMethod via listener.getLastInsight().
     */
    // @Test
    // public void exampleOptionA() {
    //     getDriver().get("https://your-app.com/checkout");
    //     // If this throws NoSuchElementException, listener auto-analyzes it
    //     getDriver().findElement(By.id("checkout-button")).click();
    // }

    /**
     * Example: OPTION B — Explicit analysis with step context.
     */
    // @Test
    // public void exampleOptionB() {
    //     recordStep("Navigate to checkout page");
    //     getDriver().get("https://your-app.com/checkout");
    //
    //     recordStep("Click checkout button");
    //     try {
    //         getDriver().findElement(By.id("checkout-button")).click();
    //     } catch (NoSuchElementException e) {
    //         InsightResponse insight = analyzeAndLog(e);
    //         // insight.getConditionCategory() -> OVERLAY (if cookie modal is blocking)
    //         // insight.isTransient() -> true
    //         // insight.getSuggestedTestOutcome() -> "RETRY"
    //         softAssert.fail("Element not found — see TestSentinel insight: " + insight.getRootCause());
    //     }
    // }
}
