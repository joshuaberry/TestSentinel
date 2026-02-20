package com.testsentinel.hooks;

import com.testsentinel.support.SentinelFactory;
import com.testsentinel.core.ActionPlanAdvisor;
import com.testsentinel.core.TestSentinelClient;
import com.testsentinel.core.TestSentinelConfig;
import io.cucumber.java.AfterAll;
import io.cucumber.java.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Suite-level Cucumber hooks — @BeforeAll and @AfterAll only.
 *
 * Cucumber's @BeforeAll / @AfterAll mechanism invokes the annotated static
 * methods by instantiating the declaring class with a no-arg constructor,
 * BEFORE PicoContainer is involved. If this class had a constructor that
 * accepted parameters, Cucumber would fail to instantiate it and silently
 * skip all hooks in the class — including the @Before in Hooks.java that
 * creates the WebDriver.
 *
 * Rule: any class containing @BeforeAll or @AfterAll must have a public
 * no-arg constructor (or no explicit constructor at all).
 *
 * The three shared singletons are package-visible statics so that Hooks.java
 * (the per-scenario class) can read them from its @Before method.
 */
public class SuiteHooks {

    private static final Logger log = LoggerFactory.getLogger(SuiteHooks.class);

    // Package-visible so Hooks.java can read them without a separate accessor layer.
    static TestSentinelClient sharedSentinel;
    static ActionPlanAdvisor  sharedAdvisor;
    static TestSentinelConfig sharedConfig;

    // No-arg constructor — required for @BeforeAll / @AfterAll to work.
    public SuiteHooks() {}

    @BeforeAll
    public static void initSentinel() {
        sharedConfig   = SentinelFactory.buildConfig();
        sharedSentinel = new TestSentinelClient(sharedConfig);
        sharedAdvisor  = SentinelFactory.createAdvisor(sharedConfig);
        log.info("SuiteHooks @BeforeAll: TestSentinel ready — KB={} patterns, phase2={}",
            sharedSentinel.knowledgeBaseSize(), sharedConfig.isPhase2Enabled());
    }

    @AfterAll
    public static void reportSentinelStats() {
        if (sharedSentinel != null) {
            log.info("SuiteHooks @AfterAll: run complete — KB has {} active patterns",
                sharedSentinel.knowledgeBaseSize());
        }
    }
}
