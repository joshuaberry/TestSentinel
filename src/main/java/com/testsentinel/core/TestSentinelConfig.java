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
 *   ANTHROPIC_API_KEY      - Your Anthropic API key (required)
 *   TESTSENTINEL_MODEL     - Model to use (default: claude-sonnet-4-6)
 *   TESTSENTINEL_TIMEOUT   - API call timeout in seconds (default: 30)
 *   TESTSENTINEL_MAX_TOKENS - Max output tokens (default: 2048)
 *   TESTSENTINEL_CAPTURE_DOM - Whether to capture DOM snapshots (default: true)
 *   TESTSENTINEL_CAPTURE_SCREENSHOT - Whether to capture screenshots (default: true)
 *   TESTSENTINEL_DOM_MAX_CHARS - Max DOM snapshot length (default: 15000 chars)
 *   TESTSENTINEL_PHASE2_ENABLED - Enable Phase 2 action plan generation (default: false)
 *   TESTSENTINEL_MAX_RISK_LEVEL - Max risk level for recommendations: LOW|MEDIUM|HIGH (default: LOW)
 *   TESTSENTINEL_KNOWLEDGE_BASE_PATH - Path to known-conditions.json (optional; omit to disable local resolution)
 */
public class TestSentinelConfig {

    public static final String DEFAULT_MODEL = "claude-sonnet-4-6";
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;
    public static final int DEFAULT_MAX_TOKENS = 2048;
    public static final int DEFAULT_DOM_MAX_CHARS = 15_000;

    private final String apiKey;
    private final String model;
    private final int timeoutSeconds;
    private final int maxTokens;
    private final boolean captureDOM;
    private final boolean captureScreenshot;
    private final int domMaxChars;
    private final boolean enabled;       // Master switch — set false to disable without removing integration
    private final boolean logPrompts;    // Log full prompts to SLF4J DEBUG for debugging
    private final boolean phase2Enabled; // Enable Phase 2 action plan generation
    private final ActionStep.RiskLevel maxRiskLevel; // Maximum risk level for recommendations
    private final Path knowledgeBasePath; // Path to known-conditions.json; null = KB disabled

    private TestSentinelConfig(Builder b) {
        this.apiKey = b.apiKey;
        this.model = b.model;
        this.timeoutSeconds = b.timeoutSeconds;
        this.maxTokens = b.maxTokens;
        this.captureDOM = b.captureDOM;
        this.captureScreenshot = b.captureScreenshot;
        this.domMaxChars = b.domMaxChars;
        this.enabled = b.enabled;
        this.logPrompts = b.logPrompts;
        this.phase2Enabled = b.phase2Enabled;
        this.maxRiskLevel = b.maxRiskLevel;
        this.knowledgeBasePath = b.knowledgeBasePath;
    }

    // ── Static factory: load from environment variables ───────────────────────

    public static TestSentinelConfig fromEnvironment() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "ANTHROPIC_API_KEY environment variable is not set. " +
                "Get your key at https://console.anthropic.com"
            );
        }
        return builder()
            .apiKey(apiKey)
            .model(envOrDefault("TESTSENTINEL_MODEL", DEFAULT_MODEL))
            .timeoutSeconds(intEnvOrDefault("TESTSENTINEL_TIMEOUT", DEFAULT_TIMEOUT_SECONDS))
            .maxTokens(intEnvOrDefault("TESTSENTINEL_MAX_TOKENS", DEFAULT_MAX_TOKENS))
            .captureDOM(boolEnvOrDefault("TESTSENTINEL_CAPTURE_DOM", true))
            .captureScreenshot(boolEnvOrDefault("TESTSENTINEL_CAPTURE_SCREENSHOT", true))
            .domMaxChars(intEnvOrDefault("TESTSENTINEL_DOM_MAX_CHARS", DEFAULT_DOM_MAX_CHARS))
            .enabled(boolEnvOrDefault("TESTSENTINEL_ENABLED", true))
            .logPrompts(boolEnvOrDefault("TESTSENTINEL_LOG_PROMPTS", false))
            .phase2Enabled(boolEnvOrDefault("TESTSENTINEL_PHASE2_ENABLED", false))
            .maxRiskLevel(riskLevelEnvOrDefault("TESTSENTINEL_MAX_RISK_LEVEL", ActionStep.RiskLevel.LOW))
            .knowledgeBasePath(pathEnvOrNull("TESTSENTINEL_KNOWLEDGE_BASE_PATH"))
            .build();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getApiKey() { return apiKey; }
    public String getModel() { return model; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public int getMaxTokens() { return maxTokens; }
    public boolean isCaptureDOM() { return captureDOM; }
    public boolean isCaptureScreenshot() { return captureScreenshot; }
    public int getDomMaxChars() { return domMaxChars; }
    public boolean isEnabled() { return enabled; }
    public boolean isLogPrompts() { return logPrompts; }
    public boolean isPhase2Enabled() { return phase2Enabled; }
    public ActionStep.RiskLevel getMaxRiskLevel() { return maxRiskLevel; }
    public Path getKnowledgeBasePath() { return knowledgeBasePath; }
    public boolean isKnowledgeBaseEnabled() { return knowledgeBasePath != null; }

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
        private boolean enabled = true;
        private boolean logPrompts = false;
        private boolean phase2Enabled = false;
        private ActionStep.RiskLevel maxRiskLevel = ActionStep.RiskLevel.LOW;
        private Path knowledgeBasePath = null;

        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder model(String model) { this.model = model; return this; }
        public Builder timeoutSeconds(int s) { this.timeoutSeconds = s; return this; }
        public Builder maxTokens(int t) { this.maxTokens = t; return this; }
        public Builder captureDOM(boolean b) { this.captureDOM = b; return this; }
        public Builder captureScreenshot(boolean b) { this.captureScreenshot = b; return this; }
        public Builder domMaxChars(int n) { this.domMaxChars = n; return this; }
        public Builder enabled(boolean b) { this.enabled = b; return this; }
        public Builder logPrompts(boolean b) { this.logPrompts = b; return this; }
        public Builder phase2Enabled(boolean b) { this.phase2Enabled = b; return this; }
        public Builder maxRiskLevel(ActionStep.RiskLevel level) { this.maxRiskLevel = level; return this; }
        public Builder knowledgeBasePath(Path path) { this.knowledgeBasePath = path; return this; }
        public Builder knowledgeBasePath(String path) {
            this.knowledgeBasePath = (path != null && !path.isBlank()) ? Paths.get(path) : null;
            return this;
        }

        public TestSentinelConfig build() {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("apiKey is required");
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
}
