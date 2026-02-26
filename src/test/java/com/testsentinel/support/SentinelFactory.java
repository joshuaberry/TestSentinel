package com.testsentinel.support;

import com.testsentinel.core.ActionPlanAdvisor;
import com.testsentinel.core.TestSentinelClient;
import com.testsentinel.core.TestSentinelConfig;
import com.testsentinel.core.UnknownConditionRecorder;
import com.testsentinel.model.ActionStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Builds and configures the TestSentinelClient for the test project.
 *
 * Defaults to offline mode -- no API key required.
 *
 * ## Environment Variables
 *
 *   TESTSENTINEL_OFFLINE_MODE        -- "false" to enable Claude API calls (default: true)
 *   TESTSENTINEL_KNOWLEDGE_BASE_PATH -- optional; path to known-conditions.json
 *   TESTSENTINEL_UNKNOWN_LOG_PATH    -- path for unknown condition records (default: target/unknown-conditions-log.json)
 *   ANTHROPIC_API_KEY                -- required only when TESTSENTINEL_OFFLINE_MODE=false
 *   TESTSENTINEL_PHASE2_ENABLED      -- "true" to enable action plans (default: true)
 *   TESTSENTINEL_MAX_RISK_LEVEL      -- LOW | MEDIUM | HIGH (default: LOW)
 *
 * ## System Properties (can also be set in Maven Surefire config)
 *
 *   kb.path         -- overrides TESTSENTINEL_KNOWLEDGE_BASE_PATH
 *   offline.mode    -- overrides TESTSENTINEL_OFFLINE_MODE
 *   unknown.log.path -- overrides TESTSENTINEL_UNKNOWN_LOG_PATH
 */
public class SentinelFactory {

    private static final Logger log = LoggerFactory.getLogger(SentinelFactory.class);

    private SentinelFactory() {}

    /** Creates a fully-configured TestSentinelClient with recorder attached if configured. */
    public static TestSentinelClient createClient() {
        return createClientFromConfig(buildConfig());
    }

    /**
     * Creates a TestSentinelClient from an existing config, attaching the recorder
     * if an unknown condition log path is configured.
     */
    public static TestSentinelClient createClientFromConfig(TestSentinelConfig config) {
        UnknownConditionRecorder recorder = null;
        if (config.getUnknownConditionLogPath() != null) {
            recorder = new UnknownConditionRecorder(config.getUnknownConditionLogPath());
            log.info("SentinelFactory: Unknown condition recorder at {}", config.getUnknownConditionLogPath());
        }
        TestSentinelClient client = new TestSentinelClient(config, recorder);
        log.info("SentinelFactory: Client created -- offline={}, KB={} patterns, phase2={}",
            config.isOfflineMode(), client.knowledgeBaseSize(), config.isPhase2Enabled());
        return client;
    }

    public static ActionPlanAdvisor createAdvisor(TestSentinelConfig config) {
        return new ActionPlanAdvisor(config);
    }

    public static TestSentinelConfig buildConfig() {
        boolean offlineMode = resolveOfflineMode();
        String  apiKey      = resolveApiKey(offlineMode);
        Path    kbPath      = resolveKbPath();
        boolean phase2      = resolvePhase2();
        Path    unknownLog  = resolveUnknownLogPath(offlineMode);

        TestSentinelConfig.Builder builder = TestSentinelConfig.builder()
            .apiKey(apiKey)
            .offlineMode(offlineMode)
            .phase2Enabled(phase2)
            .maxRiskLevel(ActionStep.RiskLevel.LOW)
            .captureDOM(true)
            .captureScreenshot(false)
            .domMaxChars(10_000)
            .apiEnabled(!offlineMode && !"DISABLED".equals(apiKey))
            .unknownConditionLogPath(unknownLog);

        if (kbPath != null) {
            builder.knowledgeBasePath(kbPath);
            log.info("SentinelFactory: Knowledge base at {}", kbPath);
        }

        return builder.build();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static boolean resolveOfflineMode() {
        String fromProp = System.getProperty("offline.mode");
        if (fromProp != null) return "true".equalsIgnoreCase(fromProp);

        String fromEnv = System.getenv("TESTSENTINEL_OFFLINE_MODE");
        if (fromEnv != null) return "true".equalsIgnoreCase(fromEnv);

        return true; // default: offline
    }

    private static String resolveApiKey(boolean offlineMode) {
        if (offlineMode) return "DISABLED";

        String fromProp = System.getProperty("ANTHROPIC_API_KEY");
        if (fromProp != null && !fromProp.isBlank()) return fromProp;

        String fromEnv = System.getenv("ANTHROPIC_API_KEY");
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv;

        log.warn("SentinelFactory: ANTHROPIC_API_KEY not set and offline.mode=false -- " +
                 "API calls will fail. Set TESTSENTINEL_OFFLINE_MODE=true or provide a key.");
        return "DISABLED";
    }

    private static Path resolveKbPath() {
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
        String fromProp = System.getProperty("phase2.enabled");
        if (fromProp != null) return "true".equalsIgnoreCase(fromProp);

        String fromEnv = System.getenv("TESTSENTINEL_PHASE2_ENABLED");
        if (fromEnv != null) return "true".equalsIgnoreCase(fromEnv);

        return true; // default on
    }

    private static Path resolveUnknownLogPath(boolean offlineMode) {
        String fromProp = System.getProperty("unknown.log.path");
        if (fromProp != null && !fromProp.isBlank()) return Paths.get(fromProp);

        String fromEnv = System.getenv("TESTSENTINEL_UNKNOWN_LOG_PATH");
        if (fromEnv != null && !fromEnv.isBlank()) return Paths.get(fromEnv);

        // In offline mode, always create the unknown log so unmatched conditions are captured
        return offlineMode ? TestSentinelConfig.DEFAULT_UNKNOWN_LOG_PATH : null;
    }
}
