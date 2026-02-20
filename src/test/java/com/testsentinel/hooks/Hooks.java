package com.testsentinel.hooks;

import com.testsentinel.context.ScenarioContext;
import com.testsentinel.support.DriverFactory;
import com.testsentinel.support.SentinelFactory;
import com.testsentinel.core.ActionPlanAdvisor;
import com.testsentinel.core.TestSentinelClient;
import com.testsentinel.core.TestSentinelConfig;
import com.testsentinel.interceptor.TestSentinelListener;
import com.testsentinel.model.InsightResponse;
import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cucumber lifecycle hooks.
 *
 * @BeforeAll  — Creates the shared TestSentinelClient once per test run.
 *               Cucumber 7 @BeforeAll runs before any scenario.
 *
 * @Before     — Creates a fresh WebDriver + TestSentinelListener per scenario.
 *               Resets the listener so step history from prior scenarios is clean.
 *
 * @After      — Quits the driver. If the scenario failed AND TestSentinel has an
 *               insight, attaches a formatted summary to the Cucumber report.
 *
 * @AfterAll   — Logs KB statistics for the whole run.
 */
public class Hooks {

    private static final Logger log = LoggerFactory.getLogger(Hooks.class);

    // Shared across all scenarios — created once per test run
    private static TestSentinelClient sharedSentinel;
    private static ActionPlanAdvisor  sharedAdvisor;
    private static TestSentinelConfig sharedConfig;

    // Injected by PicoContainer into each step class that needs it
    private final ScenarioContext ctx;

    public Hooks(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    // ── Suite-level lifecycle ─────────────────────────────────────────────────

    @BeforeAll
    public static void initSentinel() {
        sharedConfig   = SentinelFactory.buildConfig();
        sharedSentinel = new TestSentinelClient(sharedConfig);
        sharedAdvisor  = SentinelFactory.createAdvisor(sharedConfig);
        log.info("Hooks @BeforeAll: TestSentinel initialized — KB={} patterns, phase2={}",
            sharedSentinel.knowledgeBaseSize(), sharedConfig.isPhase2Enabled());
    }

    @AfterAll
    public static void reportSentinelStats() {
        if (sharedSentinel != null) {
            log.info("Hooks @AfterAll: Test run complete — KB has {} active patterns",
                sharedSentinel.knowledgeBaseSize());
        }
    }

    // ── Scenario-level lifecycle ──────────────────────────────────────────────

    @Before
    public void setUpScenario(Scenario scenario) {
        log.info("─── Scenario START: {} [{}] ───", scenario.getName(), scenario.getId());

        // Push shared instances into the per-scenario context
        ctx.setSentinel(sharedSentinel);
        ctx.setAdvisor(sharedAdvisor);
        ctx.setConfig(sharedConfig);
        ctx.setPhase2Enabled(sharedConfig.isPhase2Enabled());

        // Create a fresh listener for this scenario (clean step history)
        TestSentinelListener listener = new TestSentinelListener(
            sharedSentinel,
            scenario.getName(),
            scenario.getUri().toString()
        );
        ctx.setListener(listener);

        // Create the WebDriver and wrap it with the listener
        ctx.setDriver(DriverFactory.createDriver(listener));
    }

    @After
    public void tearDownScenario(Scenario scenario) {
        // Sync any auto-intercepted insight from the listener
        ctx.syncInsightFromListener();

        InsightResponse insight = ctx.getLastInsight();

        if (scenario.isFailed() && insight != null) {
            // Attach TestSentinel analysis to the Cucumber report
            String summary = buildCucumberAttachment(insight);
            scenario.attach(summary.getBytes(), "text/plain", "TestSentinel Analysis");
            log.info("Hooks @After: TestSentinel insight attached to failed scenario");

            // Log the advisor's full recommendation table
            if (insight.hasActionPlan()) {
                sharedAdvisor.logRecommendations(insight);
            }
        }

        if (insight != null && insight.isContinuable()) {
            scenario.attach(
                ("TestSentinel CONTINUE: " + insight.getRootCause()).getBytes(),
                "text/plain",
                "TestSentinel: No Problem Detected"
            );
        }

        DriverFactory.quit(ctx.getDriver());
        log.info("─── Scenario END: {} — {} ───", scenario.getName(),
            scenario.isFailed() ? "FAILED" : "PASSED");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildCucumberAttachment(InsightResponse insight) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== TestSentinel Analysis ===\n");
        sb.append("Category   : ").append(insight.getConditionCategory()).append("\n");
        sb.append("Confidence : ").append(Math.round(insight.getConfidence() * 100)).append("%\n");
        sb.append("Root Cause : ").append(insight.getRootCause()).append("\n");
        sb.append("Outcome    : ").append(insight.getSuggestedTestOutcome()).append("\n");
        sb.append("Source     : ").append(
            insight.isLocalResolution() ? "[LOCAL] " + insight.getResolvedFromPattern() : "[Claude API]"
        ).append("\n");
        sb.append("Latency    : ").append(insight.getAnalysisLatencyMs()).append("ms\n");
        sb.append("Tokens     : ").append(insight.getAnalysisTokens()).append("\n");

        if (insight.getEvidenceHighlights() != null) {
            sb.append("Evidence   :\n");
            insight.getEvidenceHighlights().forEach(e -> sb.append("  - ").append(e).append("\n"));
        }

        if (insight.hasActionPlan()) {
            sb.append("\n--- Action Plan ---\n");
            sb.append("Strategy: ").append(insight.getActionPlan().getPlanSummary()).append("\n");
            insight.getActionPlan().getActions().forEach(step ->
                sb.append(String.format("  [%s][%s] %s%n",
                    step.getRiskLevel(), step.getActionType(), step.getDescription()))
            );
        }

        return sb.toString();
    }
}
