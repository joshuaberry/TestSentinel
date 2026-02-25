package com.testsentinel.hooks;

import com.testsentinel.context.ScenarioContext;
import com.testsentinel.core.ActionPlanAdvisor;
import com.testsentinel.core.TestSentinelClient;
import com.testsentinel.core.TestSentinelConfig;
import com.testsentinel.interceptor.TestSentinelListener;
import com.testsentinel.model.InsightResponse;
import com.testsentinel.support.DriverFactory;
import com.testsentinel.support.SentinelFactory;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-scenario Cucumber hooks — @Before and @After only.
 *
 * Each scenario gets a fresh TestSentinelClient for knowledge base isolation:
 * patterns added in one scenario do not affect other scenarios within the same run.
 *
 * @BeforeAll / @AfterAll must not be in a class with a constructor parameter.
 * Suite-level setup lives in SuiteHooks (no-arg constructor).
 */
public class Hooks {

    private static final Logger log = LoggerFactory.getLogger(Hooks.class);

    private final ScenarioContext ctx;

    public Hooks(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    @Before
    public void setUpScenario(Scenario scenario) {
        log.info("─── Scenario START: {} ───", scenario.getName());

        // Fresh client per scenario for KB isolation
        TestSentinelConfig config   = SentinelFactory.buildConfig();
        TestSentinelClient sentinel = SentinelFactory.createClientFromConfig(config);
        ActionPlanAdvisor  advisor  = SentinelFactory.createAdvisor(config);

        ctx.setSentinel(sentinel);
        ctx.setAdvisor(advisor);
        ctx.setConfig(config);
        ctx.setPhase2Enabled(config.isPhase2Enabled());

        // Fresh listener per scenario — clean step history, correct test name
        TestSentinelListener listener = new TestSentinelListener(
            sentinel,
            scenario.getName(),
            scenario.getUri().toString()
        );
        ctx.setListener(listener);

        // WebDriver wrapped with the listener so exceptions are auto-intercepted
        ctx.setDriver(DriverFactory.createDriver(listener));
    }

    @After
    public void tearDownScenario(Scenario scenario) {
        ctx.syncInsightFromListener();

        InsightResponse insight = ctx.getLastInsight();

        if (scenario.isFailed() && insight != null) {
            scenario.attach(
                buildCucumberAttachment(insight).getBytes(),
                "text/plain",
                "TestSentinel Analysis"
            );
            if (insight.hasActionPlan()) {
                ctx.getAdvisor().logRecommendations(insight);
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
        log.info("─── Scenario END: {} — {} ───",
            scenario.getName(), scenario.isFailed() ? "FAILED" : "PASSED");
    }

    private String buildCucumberAttachment(InsightResponse insight) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== TestSentinel Analysis ===\n");
        sb.append("Category   : ").append(insight.getConditionCategory()).append("\n");
        sb.append("Confidence : ").append(Math.round(insight.getConfidence() * 100)).append("%\n");
        sb.append("Root Cause : ").append(insight.getRootCause()).append("\n");
        sb.append("Outcome    : ").append(insight.getSuggestedTestOutcome()).append("\n");
        sb.append("Source     : ").append(
            insight.isLocalResolution()
                ? "[LOCAL] " + insight.getResolvedFromPattern()
                : (insight.getAnalysisTokens() == 0 ? "[OFFLINE-UNMATCHED]" : "[Claude API]")
        ).append("\n");
        sb.append("Latency    : ").append(insight.getAnalysisLatencyMs()).append("ms\n");
        sb.append("Tokens     : ").append(insight.getAnalysisTokens()).append("\n");

        if (insight.getEvidenceHighlights() != null) {
            sb.append("Evidence   :\n");
            insight.getEvidenceHighlights()
                .forEach(e -> sb.append("  - ").append(e).append("\n"));
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
