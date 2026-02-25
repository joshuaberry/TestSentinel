package com.testsentinel.hooks;

import com.testsentinel.support.SentinelFactory;
import com.testsentinel.core.TestSentinelConfig;
import io.cucumber.java.AfterAll;
import io.cucumber.java.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Suite-level Cucumber hooks — @BeforeAll and @AfterAll only.
 *
 * Builds and validates shared config at suite start. Per-scenario
 * TestSentinelClient instances are created fresh in Hooks.java to
 * provide knowledge base isolation between scenarios.
 *
 * Rule: any class containing @BeforeAll or @AfterAll must have a public
 * no-arg constructor (or no explicit constructor at all).
 */
public class SuiteHooks {

    private static final Logger log = LoggerFactory.getLogger(SuiteHooks.class);

    // Shared config — read-only after @BeforeAll; scenarios create their own clients
    static TestSentinelConfig sharedConfig;

    public SuiteHooks() {}

    @BeforeAll
    public static void initSentinel() {
        sharedConfig = SentinelFactory.buildConfig();
        log.info("SuiteHooks @BeforeAll: TestSentinel config ready — offline={}, phase2={}, KB={}",
            sharedConfig.isOfflineMode(),
            sharedConfig.isPhase2Enabled(),
            sharedConfig.isKnowledgeBaseEnabled() ? sharedConfig.getKnowledgeBasePath() : "disabled");
        log.info("SuiteHooks @BeforeAll: Each scenario gets a fresh TestSentinelClient for KB isolation");
    }

    @AfterAll
    public static void reportSentinelStats() {
        log.info("SuiteHooks @AfterAll: Test suite complete");
        if (sharedConfig != null && sharedConfig.isUnknownConditionLogEnabled()) {
            log.info("SuiteHooks @AfterAll: Review unknown conditions at {}",
                sharedConfig.getUnknownConditionLogPath().toAbsolutePath());
        }
    }
}
