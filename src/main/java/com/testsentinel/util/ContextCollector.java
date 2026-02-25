package com.testsentinel.util;

import com.testsentinel.core.TestSentinelConfig;
import com.testsentinel.model.ConditionEvent;
import com.testsentinel.model.ConditionType;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Assembles the full ConditionEvent diagnostic payload from a live WebDriver session.
 *
 * Called by the ConditionInterceptor after an unexpected condition is caught.
 * Handles all failures gracefully — a collection error should never hide the
 * original condition or crash the test run.
 */
public class ContextCollector {

    private static final Logger log = LoggerFactory.getLogger(ContextCollector.class);

    private final TestSentinelConfig config;

    public ContextCollector(TestSentinelConfig config) {
        this.config = config;
    }

    /**
     * Builds a ConditionEvent from the current driver state and a caught exception.
     *
     * @param driver       The active WebDriver instance
     * @param conditionType The classified type of condition
     * @param exception    The caught exception (may be null for non-exception conditions)
     * @param priorSteps   Ordered list of test step descriptions leading to this point
     * @param testMeta     Test name, suite name, etc.
     * @return A fully populated ConditionEvent ready for analysis
     */
    public ConditionEvent collect(
            WebDriver driver,
            ConditionType conditionType,
            Exception exception,
            List<String> priorSteps,
            Map<String, String> testMeta
    ) {
        ConditionEvent.Builder builder = ConditionEvent.builder()
            .conditionType(conditionType)
            .message(exception != null ? exception.getMessage() : "Condition detected: " + conditionType)
            .priorSteps(priorSteps)
            .frameworkMeta(buildFrameworkMeta(testMeta));

        if (exception != null) {
            builder.stackTrace(stackTraceToString(exception));
        }

        // Enrich locator details for LOCATOR_NOT_FOUND
        if (conditionType == ConditionType.LOCATOR_NOT_FOUND && exception instanceof NoSuchElementException) {
            enrichLocatorContext(builder, exception);
        }

        // Collect current URL (safe — rarely fails)
        try {
            builder.currentUrl(driver.getCurrentUrl());
        } catch (Exception e) {
            log.warn("TestSentinel: Could not capture current URL: {}", e.getMessage());
            builder.currentUrl("unavailable");
        }

        // Collect DOM snapshot
        if (config.isCaptureDOM()) {
            try {
                String dom = driver.getPageSource();
                builder.domSnapshot(truncateDOM(dom, config.getDomMaxChars()));
            } catch (Exception e) {
                log.warn("TestSentinel: Could not capture DOM snapshot: {}", e.getMessage());
            }
        }

        // Collect screenshot
        if (config.isCaptureScreenshot()) {
            try {
                if (driver instanceof TakesScreenshot) {
                    String screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BASE64);
                    builder.screenshotBase64(screenshot);
                }
            } catch (Exception e) {
                log.warn("TestSentinel: Could not capture screenshot: {}", e.getMessage());
            }
        }

        // Collect browser console logs
        try {
            List<String> consoleLogs = collectConsoleLogs(driver);
            if (!consoleLogs.isEmpty()) {
                builder.consoleLogs(consoleLogs);
            }
        } catch (Exception e) {
            log.debug("TestSentinel: Console log collection not available: {}", e.getMessage());
        }

        // Collect environment metadata
        builder.environmentMeta(buildEnvironmentMeta(driver));

        return builder.build();
    }

    /**
     * Convenience overload — builds a ConditionEvent for a WRONG_PAGE condition
     * where there is no exception, just a URL mismatch.
     */
    public ConditionEvent collectWrongPage(
            WebDriver driver,
            String expectedUrl,
            List<String> priorSteps,
            Map<String, String> testMeta
    ) {
        ConditionEvent.Builder builder = ConditionEvent.builder()
            .conditionType(ConditionType.WRONG_PAGE)
            .message("Expected URL pattern '" + expectedUrl + "' but found '" + safeGetUrl(driver) + "'")
            .expectedUrl(expectedUrl)
            .priorSteps(priorSteps)
            .frameworkMeta(buildFrameworkMeta(testMeta));

        try { builder.currentUrl(driver.getCurrentUrl()); } catch (Exception ignored) {}

        if (config.isCaptureDOM()) {
            try { builder.domSnapshot(truncateDOM(driver.getPageSource(), config.getDomMaxChars())); } catch (Exception ignored) {}
        }
        if (config.isCaptureScreenshot()) {
            try {
                if (driver instanceof TakesScreenshot) {
                    builder.screenshotBase64(((TakesScreenshot) driver).getScreenshotAs(OutputType.BASE64));
                }
            } catch (Exception ignored) {}
        }

        builder.environmentMeta(buildEnvironmentMeta(driver));
        return builder.build();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void enrichLocatorContext(ConditionEvent.Builder builder, Exception exception) {
        // Extract locator strategy and value from the exception message.
        // Chrome formats: Unable to locate element: {"method":"css selector","selector":"[id=\"x\"]"}
        //   or           Unable to locate element: {"method":"css selector","selector":"#x"}
        //
        // The selector value may contain escaped quotes (e.g., [id=\"x\"]), so we cannot
        // use indexOf('"') to find the closing quote — it hits the embedded \" first.
        // Instead we scan to the closing `"}` of the JSON object, which is always the end.
        String msg = exception.getMessage();
        if (msg == null) return;

        if (msg.contains("\"method\"")) {
            try {
                int methodStart = msg.indexOf("\"method\":\"") + 10;
                int methodEnd   = msg.indexOf("\"", methodStart);
                builder.locatorStrategy(msg.substring(methodStart, methodEnd));
            } catch (Exception ignored) {}
        }
        if (msg.contains("\"selector\"")) {
            try {
                int selStart    = msg.indexOf("\"selector\":\"") + 12;
                // End is at the closing `"}` of the JSON object
                int closingBrace = msg.indexOf("\"}", selStart);
                if (closingBrace > selStart) {
                    builder.locatorValue(msg.substring(selStart, closingBrace));
                }
            } catch (Exception ignored) {}
        }
    }

    private List<String> collectConsoleLogs(WebDriver driver) {
        try {
            return driver.manage().logs().get(LogType.BROWSER)
                .getAll()
                .stream()
                .map(LogEntry::toString)
                .limit(50)  // Cap to 50 most recent entries
                .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Truncates the DOM snapshot intelligently:
     * - Strips script and style tag content (high token cost, low diagnostic value)
     * - Truncates from the middle to preserve head and tail of document
     */
    private String truncateDOM(String dom, int maxChars) {
        if (dom == null) return null;

        // Strip script content
        dom = dom.replaceAll("(?si)<script[^>]*>.*?</script>", "<script>[removed]</script>");
        // Strip style content
        dom = dom.replaceAll("(?si)<style[^>]*>.*?</style>", "<style>[removed]</style>");

        if (dom.length() <= maxChars) return dom;

        // Keep first 60% and last 40% to preserve visible area and footer context
        int keep = maxChars - 50;
        int headChars = (int)(keep * 0.6);
        int tailChars = keep - headChars;

        return dom.substring(0, headChars) +
            "\n\n... [DOM TRUNCATED — " + (dom.length() - maxChars) + " chars removed] ...\n\n" +
            dom.substring(dom.length() - tailChars);
    }

    private Map<String, String> buildFrameworkMeta(Map<String, String> testMeta) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("framework", "Selenium Java");
        meta.put("frameworkVersion", "4.x");
        if (testMeta != null) meta.putAll(testMeta);
        return meta;
    }

    private Map<String, String> buildEnvironmentMeta(WebDriver driver) {
        Map<String, String> meta = new LinkedHashMap<>();
        try {
            if (driver instanceof JavascriptExecutor js) {
                Object userAgent = js.executeScript("return navigator.userAgent;");
                if (userAgent != null) meta.put("userAgent", userAgent.toString());
                Object viewport = js.executeScript(
                    "return window.innerWidth + 'x' + window.innerHeight;");
                if (viewport != null) meta.put("viewport", viewport.toString());
            }
        } catch (Exception ignored) {}
        return meta;
    }

    private String safeGetUrl(WebDriver driver) {
        try { return driver.getCurrentUrl(); } catch (Exception e) { return "unavailable"; }
    }

    private String stackTraceToString(Exception e) {
        if (e == null) return null;
        StringBuilder sb = new StringBuilder();
        sb.append(e.getClass().getName()).append(": ").append(e.getMessage()).append("\n");
        // Include first 15 stack frames — enough for diagnosis without excessive tokens
        StackTraceElement[] frames = e.getStackTrace();
        int limit = Math.min(15, frames.length);
        for (int i = 0; i < limit; i++) {
            sb.append("  at ").append(frames[i]).append("\n");
        }
        if (frames.length > limit) {
            sb.append("  ... ").append(frames.length - limit).append(" more frames\n");
        }
        return sb.toString();
    }
}
