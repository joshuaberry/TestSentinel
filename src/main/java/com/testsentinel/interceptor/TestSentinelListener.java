package com.testsentinel.interceptor;

import com.testsentinel.core.TestSentinelClient;
import com.testsentinel.model.InsightResponse;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.events.WebDriverListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * A Selenium WebDriverListener that automatically intercepts exceptions
 * and invokes TestSentinel analysis without requiring any changes to existing test code.
 *
 * ## Usage with EventFiringDecorator (Selenium 4.x)
 *
 * <pre>
 *   // In your base test class or driver factory:
 *   WebDriver rawDriver = new ChromeDriver();
 *   TestSentinelClient sentinel = new TestSentinelClient(TestSentinelConfig.fromEnvironment());
 *   TestSentinelListener listener = new TestSentinelListener(sentinel, "LoginTest", "SmokeTests");
 *
 *   WebDriver driver = new EventFiringDecorator<>(listener).decorate(rawDriver);
 *   // Now use driver normally — TestSentinel intercepts automatically
 * </pre>
 *
 * The listener maintains a rolling step history (last 20 steps) based on
 * findElement calls and navigation events.
 */
public class TestSentinelListener implements WebDriverListener {

    private static final Logger log = LoggerFactory.getLogger(TestSentinelListener.class);
    private static final int MAX_STEP_HISTORY = 20;

    private final TestSentinelClient sentinel;
    private final Map<String, String> testMeta;

    // Rolling history of what the test has done — used to enrich the prompt
    private final ConcurrentLinkedDeque<String> stepHistory = new ConcurrentLinkedDeque<>();

    // The last InsightResponse produced — accessible to test code via getLastInsight()
    private volatile InsightResponse lastInsight;

    public TestSentinelListener(TestSentinelClient sentinel, String testName, String suiteName) {
        this.sentinel = sentinel;
        this.testMeta = new HashMap<>();
        testMeta.put("testName", testName);
        testMeta.put("suiteName", suiteName);
        testMeta.put("framework", "Selenium 4 + EventFiringDecorator");
    }

    // ── Step Tracking ─────────────────────────────────────────────────────────

    @Override
    public void beforeFindElement(WebDriver driver, By locator) {
        addStep("Find element: " + locator);
    }

    @Override
    public void beforeGet(WebDriver driver, String url) {
        addStep("Navigate to: " + url);
    }

    @Override
    public void afterGet(WebDriver driver, String url) {
        addStep("Arrived at: " + url);
    }

    @Override
    public void beforeClick(WebElement element) {
        addStep("Click: " + describeElement(element));
    }

    @Override
    public void beforeSendKeys(WebElement element, CharSequence... keysToSend) {
        addStep("Type into: " + describeElement(element));
    }

    // ── Exception Interception ─────────────────────────────────────────────────

    @Override
    public void onError(Object target, Method method, Object[] args, InvocationTargetException e) {
        Throwable cause = e.getCause();
        if (cause == null) return;

        // Only analyze Selenium-level exceptions — not application-level assertions
        if (!cause.getClass().getPackageName().startsWith("org.openqa.selenium")) {
            return;
        }

        log.info("TestSentinel: Intercepted {} on method {}", cause.getClass().getSimpleName(), method.getName());

        // Extract the driver from the target if possible
        WebDriver driver = extractDriver(target);
        if (driver == null) {
            log.warn("TestSentinel: Could not extract WebDriver from event target — skipping analysis");
            return;
        }

        try {
            InsightResponse insight = sentinel.analyzeException(
                driver,
                cause instanceof Exception ex ? ex : new RuntimeException(cause),
                new ArrayList<>(stepHistory),
                testMeta
            );
            this.lastInsight = insight;
            sentinel.logInsight(insight);
        } catch (Exception analysisError) {
            log.error("TestSentinel: Analysis failed: {}", analysisError.getMessage());
        }
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Returns the InsightResponse from the most recent intercepted exception.
     * Call this from your test's catch block or @AfterMethod to access the analysis.
     */
    public InsightResponse getLastInsight() {
        return lastInsight;
    }

    /**
     * Clears the step history and last insight.
     * Call this at the start of each test method to ensure clean per-test history.
     */
    public void reset(String testName) {
        stepHistory.clear();
        lastInsight = null;
        testMeta.put("testName", testName);
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private void addStep(String step) {
        stepHistory.addLast(step);
        while (stepHistory.size() > MAX_STEP_HISTORY) {
            stepHistory.pollFirst();
        }
    }

    private String describeElement(WebElement element) {
        try { return element.getTagName() + "[" + element.getAttribute("id") + "]"; }
        catch (Exception e) { return "element"; }
    }

    private WebDriver extractDriver(Object target) {
        if (target instanceof WebDriver wd) return wd;
        try {
            Method getWrapped = target.getClass().getMethod("getWrappedDriver");
            Object result = getWrapped.invoke(target);
            if (result instanceof WebDriver wd) return wd;
        } catch (Exception ignored) {}
        return null;
    }
}
