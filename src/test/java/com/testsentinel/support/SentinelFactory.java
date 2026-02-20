package com.testsentinel.support;

import com.testsentinel.core.ActionPlanAdvisor;
import com.testsentinel.core.TestSentinelClient;
import com.testsentinel.core.TestSentinelConfig;
import com.testsentinel.model.ActionStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Builds and configures the TestSentinelClient for the test project.
 *
 * Reads all configuration from environment variables and system properties
 * so the test project works out-of-the-box in any CI/CD environment.
 *
 * ## Environment Variables
 *
 *   ANTHROPIC_API_KEY                — required for Claude API calls
 *   TESTSENTINEL_KNOWLEDGE_BASE_PATH — optional; path to known-conditions.json
 *   TESTSENTINEL_PHASE2_ENABLED      — "true" to enable action plans
 *   TESTSENTINEL_MAX_RISK_LEVEL      — LOW | MEDIUM | HIGH (default: LOW)
 *
 * ## System Properties (can also be set in Maven Surefire config)
 *
 *   kb.path        — overrides TESTSENTINEL_KNOWLEDGE_BASE_PATH
 *   phase2.enabled — overrides TESTSENTINEL_PHASE2_ENABLED
 *
 * ## Fallback API Key
 * If ANTHROPIC_API_KEY is not set, a dummy key "DISABLED" is used.
 * The client is created but will return error InsightResponses.
 * This allows tests to run without an API key when using the knowledge base only.
 */
public class SentinelFactory {

    private static final Logger log = LoggerFactory.getLogger(SentinelFactory.class);

    /** Dummy value that prevents NPE when no API key is configured */
    private static final String DUMMY_KEY = "DISABLED";

    private SentinelFactory() {}

    public static TestSentinelClient createClient() {
        TestSentinelConfig config = buildConfig();
        TestSentinelClient client = new TestSentinelClient(config);
        log.info("SentinelFactory: Client created — KB={} patterns, phase2={}",
            client.knowledgeBaseSize(), config.isPhase2Enabled());
        return client;
    }

    public static ActionPlanAdvisor createAdvisor(TestSentinelConfig config) {
        return new ActionPlanAdvisor(config);
    }

    public static TestSentinelConfig buildConfig() {
        String apiKey = resolveApiKey();
        Path   kbPath = resolveKbPath();
        boolean phase2 = resolvePhase2();

        TestSentinelConfig.Builder builder = TestSentinelConfig.builder()
            .apiKey(apiKey)
            .phase2Enabled(phase2)
            .maxRiskLevel(ActionStep.RiskLevel.LOW)
            .captureDOM(true)
            .captureScreenshot(false)      // Keep CI fast — screenshots slow DOM tests
            .domMaxChars(10_000)
            .apiEnabled(!DUMMY_KEY.equals(apiKey));

        if (kbPath != null) {
            builder.knowledgeBasePath(kbPath);
            log.info("SentinelFactory: Knowledge base at {}", kbPath);
        }

        return builder.build();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static String resolveApiKey() {
        // Priority: system property > env var > dummy
        String fromProp = System.getProperty("ANTHROPIC_API_KEY");
        if (fromProp != null && !fromProp.isBlank()) return fromProp;

        String fromEnv = System.getenv("ANTHROPIC_API_KEY");
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv;

        log.warn("SentinelFactory: ANTHROPIC_API_KEY not set — Claude API calls will fail. " +
                 "KB-only scenarios will still work.");
        return DUMMY_KEY;
    }

    private static Path resolveKbPath() {
        // Priority: system property > env var > default location next to test resources
        String fromProp = System.getProperty("kb.path");
        if (fromProp != null && !fromProp.isBlank()) return Paths.get(fromProp);

        String fromEnv = System.getenv("TESTSENTINEL_KNOWLEDGE_BASE_PATH");
        if (fromEnv != null && !fromEnv.isBlank()) return Paths.get(fromEnv);

        // Default: src/test/resources/known-conditions.json
        Path defaultPath = Paths.get("src/test/resources/known-conditions.json");
        if (Files.exists(defaultPath)) {
            log.info("SentinelFactory: Using default KB path {}", defaultPath.toAbsolutePath());
            return defaultPath;
        }

        return null;
    }

    private static boolean resolvePhase2() {
        // Phase 2 is enabled by default. Override with -Dphase2.enabled=false
        // or TESTSENTINEL_PHASE2_ENABLED=false to disable.
        String fromProp = System.getProperty("phase2.enabled");
        if (fromProp != null) return "true".equalsIgnoreCase(fromProp);

        String fromEnv = System.getenv("TESTSENTINEL_PHASE2_ENABLED");
        if (fromEnv != null) return "true".equalsIgnoreCase(fromEnv);

        return true; // default on
    }
}
