package com.testsentinel.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The input payload for every TestSentinel analysis request.
 * Built by the ConditionInterceptor and passed to the TestSentinelClient.
 *
 * Use the nested Builder for construction:
 * <pre>
 *   ConditionEvent event = ConditionEvent.builder()
 *       .conditionType(ConditionType.LOCATOR_NOT_FOUND)
 *       .message(e.getMessage())
 *       .currentUrl(driver.getCurrentUrl())
 *       .domSnapshot(driver.getPageSource())
 *       .screenshot(screenshotBase64)
 *       .build();
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConditionEvent {

    // ── Required ──────────────────────────────────────────────────────────────
    private ConditionType conditionType;
    private String message;
    private String currentUrl;
    private Instant timestamp;

    // ── Navigation context ────────────────────────────────────────────────────
    private String expectedUrl;

    // ── Locator context (for LOCATOR_NOT_FOUND) ───────────────────────────────
    private String locatorStrategy;   // "CSS", "XPATH", "ID", "NAME", etc.
    private String locatorValue;      // The actual selector string that failed

    // ── Page state ────────────────────────────────────────────────────────────
    private String domSnapshot;       // driver.getPageSource() -- truncated by ContextCollector
    private String screenshotBase64;  // PNG encoded as base64 -- enables vision analysis
    private List<String> consoleLogs; // Browser console output
    private List<Map<String, Object>> networkRequests; // Pending/failed XHR/fetch

    // ── Test context ──────────────────────────────────────────────────────────
    private List<String> priorSteps;  // Ordered list of step descriptions leading to condition
    private Map<String, String> frameworkMeta; // testName, suiteName, framework, executionId
    private Map<String, String> environmentMeta; // browser, browserVersion, os, viewport, baseUrl, environment

    // ── Exception detail ──────────────────────────────────────────────────────
    private String stackTrace;

    private ConditionEvent() {}

    // ── Getters ───────────────────────────────────────────────────────────────

    public ConditionType getConditionType() { return conditionType; }
    public String getMessage() { return message; }
    public String getCurrentUrl() { return currentUrl; }
    public Instant getTimestamp() { return timestamp; }
    public String getExpectedUrl() { return expectedUrl; }
    public String getLocatorStrategy() { return locatorStrategy; }
    public String getLocatorValue() { return locatorValue; }
    public String getDomSnapshot() { return domSnapshot; }
    public String getScreenshotBase64() { return screenshotBase64; }
    public List<String> getConsoleLogs() { return consoleLogs; }
    public List<Map<String, Object>> getNetworkRequests() { return networkRequests; }
    public List<String> getPriorSteps() { return priorSteps; }
    public Map<String, String> getFrameworkMeta() { return frameworkMeta; }
    public Map<String, String> getEnvironmentMeta() { return environmentMeta; }
    public String getStackTrace() { return stackTrace; }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final ConditionEvent event = new ConditionEvent();

        public Builder conditionType(ConditionType type) {
            event.conditionType = type;
            event.timestamp = Instant.now();
            return this;
        }
        public Builder message(String message) { event.message = message; return this; }
        public Builder currentUrl(String url) { event.currentUrl = url; return this; }
        public Builder expectedUrl(String url) { event.expectedUrl = url; return this; }
        public Builder locatorStrategy(String strategy) { event.locatorStrategy = strategy; return this; }
        public Builder locatorValue(String value) { event.locatorValue = value; return this; }
        public Builder domSnapshot(String dom) { event.domSnapshot = dom; return this; }
        public Builder screenshotBase64(String screenshot) { event.screenshotBase64 = screenshot; return this; }
        public Builder consoleLogs(List<String> logs) { event.consoleLogs = logs; return this; }
        public Builder networkRequests(List<Map<String, Object>> requests) { event.networkRequests = requests; return this; }
        public Builder priorSteps(List<String> steps) { event.priorSteps = steps; return this; }
        public Builder frameworkMeta(Map<String, String> meta) { event.frameworkMeta = meta; return this; }
        public Builder environmentMeta(Map<String, String> meta) { event.environmentMeta = meta; return this; }
        public Builder stackTrace(String stackTrace) { event.stackTrace = stackTrace; return this; }

        public ConditionEvent build() {
            if (event.conditionType == null) throw new IllegalStateException("conditionType is required");
            if (event.message == null || event.message.isBlank()) throw new IllegalStateException("message is required");
            if (event.currentUrl == null) event.currentUrl = "unknown";
            if (event.timestamp == null) event.timestamp = Instant.now();
            return event;
        }
    }
}
