package com.example.support;

import com.testsentinel.interceptor.TestSentinelListener;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.events.EventFiringDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates ChromeDriver instances wrapped with the TestSentinelListener.
 *
 * The EventFiringDecorator wraps every WebDriver call. When a Selenium
 * exception is thrown, TestSentinelListener.onError() is invoked automatically,
 * triggering analysis without any test code changes.
 *
 * ## Headless mode
 * Controlled by the system property "headless" (default: true for CI).
 * To run with a visible browser:
 *   mvn test -Dheadless=false
 */
public class DriverFactory {

    private static final Logger log = LoggerFactory.getLogger(DriverFactory.class);

    private DriverFactory() {}

    /**
     * Creates a ChromeDriver and wraps it with the given TestSentinelListener.
     * Call this once per scenario in the @Before hook.
     */
    public static WebDriver createDriver(TestSentinelListener listener) {
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();

        boolean headless = !"false".equalsIgnoreCase(System.getProperty("headless", "true"));
        if (headless) {
            options.addArguments("--headless=new");
            log.info("DriverFactory: Running in headless mode");
        } else {
            log.info("DriverFactory: Running with visible browser");
        }

        options.addArguments(
            "--no-sandbox",
            "--disable-dev-shm-usage",
            "--disable-gpu",
            "--window-size=1440,900",
            "--disable-search-engine-choice-screen",  // suppresses Google's consent dialogs in newer Chrome
            "--disable-features=PrivacySandboxSettings4"
        );

        // Enable browser console log capture for TestSentinel context collection
        options.setCapability("goog:loggingPrefs",
            java.util.Map.of("browser", "ALL", "performance", "ALL"));

        WebDriver rawDriver = new ChromeDriver(options);

        // Wrap with EventFiringDecorator — this is what makes TestSentinel automatic
        WebDriver decoratedDriver = new EventFiringDecorator<>(listener).decorate(rawDriver);

        log.info("DriverFactory: WebDriver created and wrapped with TestSentinelListener");
        return decoratedDriver;
    }

    /**
     * Quits the driver safely, swallowing any teardown exceptions.
     */
    public static void quit(WebDriver driver) {
        if (driver != null) {
            try {
                driver.quit();
                log.debug("DriverFactory: WebDriver quit successfully");
            } catch (Exception e) {
                log.warn("DriverFactory: Exception during driver quit: {}", e.getMessage());
            }
        }
    }
}
