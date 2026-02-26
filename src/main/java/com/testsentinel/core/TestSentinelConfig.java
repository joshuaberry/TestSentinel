package com.testsentinel.core;

import com.testsentinel.model.ActionStep;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration for the TestSentinel client.
 *
 * Load from environment variables (recommended) or construct programmatically.
 *
 * Recommended environment variables:
 *   TESTSENTINEL_OFFLINE_MODE      - Run offline; disable all AI API calls (default: true)
 *   TESTSENTINEL_UNKNOWN_LOG_PATH  - Path to write unknown-conditions-log.json (default: target/unknown-conditions-log.json)
 *   ANTHROPIC_API_KEY              - Required only when TESTSENTINEL_OFFLINE_MODE=false
 *   TESTSENTINEL_MODEL             - Model to use (default: claude-sonnet-4-6)
 *   TESTSENTINEL_TIMEOUT           - API call timeout in seconds (default: 30)
 *   TESTSENTINEL_MAX_TOKENS        - Max output tokens (default: 2048)
 *   TESTSENTINEL_CAPTURE_DOM       - Whether to capture DOM snapshots (default: true)
 *   TESTSENTINEL_CAPTURE_SCREENSHOT - Whether to capture screenshots (default: true)
 *   TESTSENTINEL_DOM_MAX_CHARS     - Max DOM snapshot length (default: 15000 chars)
 *   TESTSENTINEL_MAX_RISK_LEVEL    - Max risk level for auto-execution: LOW|MEDIUM|HIGH (default: LOW)
 *   TESTSENTINEL_KNOWLEDGE_BASE_PATH - Path to known-conditions.json (optional; omit to disable local resolution)
 */
public class TestSentinelConfig {

    public static final String DEFAULT_MODEL = "claude-sonnet-4-6";
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;
    public static final int DEFAULT_MAX_TOKENS = 2048;
    public static final int DEFAULT_DOM_MAX_CHARS = 15_000;
    public static final Path DEFAULT_UNKNOWN_LOG_PATH = Paths.get("target/unknown-conditions-log.json");

    private final String apiKey;
    private final String model;
    private final int timeoutSeconds;
    private final int maxTokens;
    private final boolean captureDOM;
    private final boolean captureScreenshot;
    private final int domMaxChars;
    private final boolean apiEnabled;      // Controls Claude API calls
    private final boolean offlineMode;    // When true, API is never called; unknown conditions are recorded
    private final boolean logPrompts;     // Log full prompts to SLF4J DEBUG for debugging
    private final ActionStep.RiskLevel maxRiskLevel;
    private final Path knowledgeBasePath;
    private final Path unknownConditionLogPath; // Where to write unknown condition records; null = disabled

    private TestSentinelConfig(Builder b) {
        this.apiKey                  = b.apiKey;
        this.model                   = b.model;
        this.timeoutSeconds          = b.timeoutSeconds;
        this.maxTokens               = b.maxTokens;
        this.captureDOM              = b.captureDOM;
        this.captureScreenshot       = b.captureScreenshot;
        this.domMaxChars             = b.domMaxChars;
        this.offlineMode             = b.offlineMode;
        this.apiEnabled              = b.offlineMode ? false : b.apiEnabled;
        this.logPrompts              = b.logPrompts;
        this.maxRiskLevel            = b.maxRiskLevel;
        this.knowledgeBasePath       = b.knowledgeBasePath;
        this.unknownConditionLogPath = b.unknownConditionLogPath;
    }

    // ── Static factory: load from environment variables ───────────────────────

    public static TestSentinelConfig fromEnvironment() {
        boolean offlineMode = boolEnvOrDefault("TESTSENTINEL_OFFLINE_MODE", true);

        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (!offlineMode && (apiKey == null || apiKey.isBlank())) {
            throw new IllegalStateException(
                "ANTHROPIC_API_KEY is required when TESTSENTINEL_OFFLINE_MODE=false. " +
                "Set TESTSENTINEL_OFFLINE_MODE=true to run without an API key."
            );
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "DISABLED";
        }

        return builder()
            .apiKey(apiKey)
            .offlineMode(offlineMode)
            .model(envOrDefault("TESTSENTINEL_MODEL", DEFAULT_MODEL))
            .timeoutSeconds(intEnvOrDefault("TESTSENTINEL_TIMEOUT", DEFAULT_TIMEOUT_SECONDS))
            .maxTokens(intEnvOrDefault("TESTSENTINEL_MAX_TOKENS", DEFAULT_MAX_TOKENS))
            .captureDOM(boolEnvOrDefault("TESTSENTINEL_CAPTURE_DOM", true))
            .captureScreenshot(boolEnvOrDefault("TESTSENTINEL_CAPTURE_SCREENSHOT", true))
            .domMaxChars(intEnvOrDefault("TESTSENTINEL_DOM_MAX_CHARS", DEFAULT_DOM_MAX_CHARS))
            .apiEnabled(!offlineMode && boolEnvOrDefault("TESTSENTINEL_ENABLED", true))
            .logPrompts(boolEnvOrDefault("TESTSENTINEL_LOG_PROMPTS", false))
            .maxRiskLevel(riskLevelEnvOrDefault("TESTSENTINEL_MAX_RISK_LEVEL", ActionStep.RiskLevel.LOW))
            .knowledgeBasePath(pathEnvOrNull("TESTSENTINEL_KNOWLEDGE_BASE_PATH"))
            .unknownConditionLogPath(pathEnvOrDefault("TESTSENTINEL_UNKNOWN_LOG_PATH",
                offlineMode ? DEFAULT_UNKNOWN_LOG_PATH : null))
            .build();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getApiKey()                         { return apiKey; }
    public String getModel()                          { return model; }
    public int getTimeoutSeconds()                    { return timeoutSeconds; }
    public int getMaxTokens()                         { return maxTokens; }
    public boolean isCaptureDOM()                     { return captureDOM; }
    public boolean isCaptureScreenshot()              { return captureScreenshot; }
    public int getDomMaxChars()                       { return domMaxChars; }
    public boolean isApiEnabled()                     { return apiEnabled; }
    public boolean isOfflineMode()                    { return offlineMode; }
    public boolean isLogPrompts()                     { return logPrompts; }
    public ActionStep.RiskLevel getMaxRiskLevel()     { return maxRiskLevel; }
    public Path getKnowledgeBasePath()                { return knowledgeBasePath; }
    public boolean isKnowledgeBaseEnabled()           { return knowledgeBasePath != null; }
    public Path getUnknownConditionLogPath()          { return unknownConditionLogPath; }
    public boolean isUnknownConditionLogEnabled()     { return unknownConditionLogPath != null; }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String apiKey;
        private String model = DEFAULT_MODEL;
        private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        private int maxTokens = DEFAULT_MAX_TOKENS;
        private boolean captureDOM = true;
        private boolean captureScreenshot = true;
        private int domMaxChars = DEFAULT_DOM_MAX_CHARS;
        private boolean apiEnabled = false;
        private boolean offlineMode = true;
        private boolean logPrompts = false;
        private ActionStep.RiskLevel maxRiskLevel = ActionStep.RiskLevel.LOW;
        private Path knowledgeBasePath = null;
        private Path unknownConditionLogPath = DEFAULT_UNKNOWN_LOG_PATH;

        public Builder apiKey(String apiKey)                              { this.apiKey = apiKey; return this; }
        public Builder model(String model)                                { this.model = model; return this; }
        public Builder timeoutSeconds(int s)                              { this.timeoutSeconds = s; return this; }
        public Builder maxTokens(int t)                                   { this.maxTokens = t; return this; }
        public Builder captureDOM(boolean b)                              { this.captureDOM = b; return this; }
        public Builder captureScreenshot(boolean b)                       { this.captureScreenshot = b; return this; }
        public Builder domMaxChars(int n)                                 { this.domMaxChars = n; return this; }
        public Builder apiEnabled(boolean b)                              { this.apiEnabled = b; return this; }
        public Builder offlineMode(boolean b)                             { this.offlineMode = b; return this; }
        public Builder logPrompts(boolean b)                              { this.logPrompts = b; return this; }
        public Builder maxRiskLevel(ActionStep.RiskLevel level)           { this.maxRiskLevel = level; return this; }
        public Builder knowledgeBasePath(Path path)                       { this.knowledgeBasePath = path; return this; }
        public Builder unknownConditionLogPath(Path path)                 { this.unknownConditionLogPath = path; return this; }
        public Builder knowledgeBasePath(String path) {
            this.knowledgeBasePath = (path != null && !path.isBlank()) ? Paths.get(path) : null;
            return this;
        }

        public TestSentinelConfig build() {
            if (apiKey == null) {
                apiKey = "DISABLED";
            }
            return new TestSentinelConfig(this);
        }
    }

    // ── Env helpers ───────────────────────────────────────────────────────────

    private static String envOrDefault(String key, String defaultValue) {
        String val = System.getenv(key);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }

    private static int intEnvOrDefault(String key, int defaultValue) {
        try {
            String val = System.getenv(key);
            return (val != null && !val.isBlank()) ? Integer.parseInt(val.trim()) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean boolEnvOrDefault(String key, boolean defaultValue) {
        String val = System.getenv(key);
        if (val == null || val.isBlank()) return defaultValue;
        return "true".equalsIgnoreCase(val.trim()) || "1".equals(val.trim());
    }

    private static ActionStep.RiskLevel riskLevelEnvOrDefault(String key, ActionStep.RiskLevel defaultValue) {
        String val = System.getenv(key);
        if (val == null || val.isBlank()) return defaultValue;
        try { return ActionStep.RiskLevel.valueOf(val.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return defaultValue; }
    }

    private static Path pathEnvOrNull(String key) {
        String val = System.getenv(key);
        return (val != null && !val.isBlank()) ? Paths.get(val.trim()) : null;
    }

    private static Path pathEnvOrDefault(String key, Path defaultValue) {
        String val = System.getenv(key);
        return (val != null && !val.isBlank()) ? Paths.get(val.trim()) : defaultValue;
    }
}
