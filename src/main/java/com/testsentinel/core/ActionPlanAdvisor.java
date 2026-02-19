package com.testsentinel.core;

import com.testsentinel.model.ActionPlan;
import com.testsentinel.model.ActionStep;
import com.testsentinel.model.InsightResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Applies risk-gating logic to an ActionPlan and presents filtered recommendations
 * to the test framework.
 *
 * Phase 2 produces an ActionPlan with all recommended steps — including MEDIUM and HIGH
 * risk steps that should never be auto-executed. The ActionPlanAdvisor is the enforcement
 * layer that ensures the framework only acts on steps it has been explicitly configured to act on.
 *
 * ## Usage in @AfterMethod or catch block
 * <pre>
 *   InsightResponse insight = sentinel.analyzeException(driver, e, steps, meta);
 *   ActionPlanAdvisor advisor = new ActionPlanAdvisor(config);
 *
 *   // Get the steps that are safe to present as recommendations (all LOW risk):
 *   List<ActionStep> safeSteps = advisor.getSafeRecommendations(insight);
 *
 *   // Log them to the test report:
 *   advisor.logRecommendations(insight);
 *
 *   // In Phase 3, the executor will call advisor.getExecutableSteps() before acting.
 * </pre>
 *
 * ## Risk Gate Configuration
 * The maximum executable risk level is controlled by:
 *   TESTSENTINEL_MAX_RISK_LEVEL=LOW|MEDIUM|HIGH  (default: LOW)
 * or programmatically via TestSentinelConfig.Builder.maxRiskLevel()
 */
public class ActionPlanAdvisor {

    private static final Logger log = LoggerFactory.getLogger(ActionPlanAdvisor.class);

    private final TestSentinelConfig config;

    public ActionPlanAdvisor(TestSentinelConfig config) {
        this.config = config;
    }

    /**
     * Returns all LOW-risk steps from the plan, regardless of config.
     * These are always safe to present as recommendations.
     */
    public List<ActionStep> getSafeRecommendations(InsightResponse insight) {
        if (!insight.hasActionPlan()) return List.of();
        return insight.getActionPlan().getLowRiskActions();
    }

    /**
     * Returns steps filtered by the configured max risk level.
     * In Phase 2, this is used for recommendation display only.
     * In Phase 3, this gates what the executor will actually run.
     */
    public List<ActionStep> getExecutableSteps(InsightResponse insight) {
        if (!insight.hasActionPlan()) return List.of();
        ActionStep.RiskLevel maxRisk = config.getMaxRiskLevel();
        return insight.getActionPlan().getActionsAtOrBelow(maxRisk);
    }

    /**
     * Returns the first executable step, or null if the plan is empty or all steps
     * exceed the configured risk threshold.
     */
    public ActionStep getFirstExecutableStep(InsightResponse insight) {
        List<ActionStep> steps = getExecutableSteps(insight);
        return steps.isEmpty() ? null : steps.get(0);
    }

    /**
     * Returns true if the plan has any steps within the configured risk threshold.
     */
    public boolean hasExecutableSteps(InsightResponse insight) {
        return !getExecutableSteps(insight).isEmpty();
    }

    /**
     * Logs the full ActionPlan as a formatted recommendation summary.
     * Includes risk indicators so engineers know which steps are advisory vs executable.
     *
     * Output example:
     * ┌── TestSentinel Action Plan ────────────────────────────────────────┐
     * │  Strategy: Dismiss cookie modal then retry element interaction      │
     * │  Confidence: 91%  |  Requires Human: No                            │
     * │  ─────────────────────────────────────────────────────────────────  │
     * │  [1] [AUTO] CLICK_IF_PRESENT (#cookie-banner .accept-btn)  LOW 92% │
     * │  [2] [AUTO] WAIT_FIXED (500ms)                             LOW 88% │
     * │  [3] [AUTO] RETRY_ACTION (retry after 1000ms)              LOW 85% │
     * └────────────────────────────────────────────────────────────────────┘
     */
    public void logRecommendations(InsightResponse insight) {
        if (!insight.hasActionPlan()) {
            log.info("TestSentinel: No action plan available (Phase 1 mode or analysis unavailable)");
            return;
        }
        ActionPlan plan = insight.getActionPlan();
        ActionStep.RiskLevel maxRisk = config.getMaxRiskLevel();

        log.info("┌── TestSentinel Action Plan ─────────────────────────────────────┐");
        log.info("│  Strategy   : {}", plan.getPlanSummary());
        log.info("│  Confidence : {}%  |  Requires Human: {}",
            Math.round(plan.getPlanConfidence() * 100),
            plan.isRequiresHuman() ? "YES" : "No");
        log.info("│  ─────────────────────────────────────────────────────────────  │");

        for (int i = 0; i < plan.getActions().size(); i++) {
            ActionStep step = plan.getActions().get(i);
            boolean executable = step.getRiskLevel() != null &&
                step.getRiskLevel().ordinal() <= maxRisk.ordinal();
            String tag = executable ? "RECOMMEND" : "ADVISORY ";
            String params = formatParams(step);
            log.info("│  [{}] [{}] {} {}  {} {}%",
                i + 1, tag, step.getActionType(), params,
                step.getRiskLevel(), Math.round(step.getConfidence() * 100));
            if (step.getRationale() != null) {
                log.info("│        → {}", step.getRationale());
            }
        }

        if (plan.isRequiresHuman()) {
            log.info("│  ⚠  Human review required before proceeding               ⚠  │");
        }
        log.info("└────────────────────────────────────────────────────────────────┘");
    }

    /**
     * Returns a plain-text summary of all recommended steps suitable for
     * embedding in a TestNG/JUnit test report failure message.
     */
    public String buildReportSummary(InsightResponse insight) {
        if (!insight.hasActionPlan()) return "";

        StringBuilder sb = new StringBuilder();
        ActionPlan plan = insight.getActionPlan();
        sb.append("\n--- TestSentinel Recommendations ---\n");
        sb.append("Strategy: ").append(plan.getPlanSummary()).append("\n");

        for (int i = 0; i < plan.getActions().size(); i++) {
            ActionStep step = plan.getActions().get(i);
            sb.append(String.format("  %d. [%s][%s] %s%n",
                i + 1, step.getRiskLevel(), step.getActionType(), step.getDescription()));
        }

        if (plan.isRequiresHuman()) {
            sb.append("⚠  Human intervention required.\n");
        }
        return sb.toString();
    }

    private String formatParams(ActionStep step) {
        if (step.getParameters() == null || step.getParameters().isEmpty()) return "";
        // Show the most relevant parameter for log brevity
        String selector = step.getParam("selector", null);
        if (selector != null) return "(" + selector + ")";
        String waitMs = step.getParam("waitMs", null);
        if (waitMs != null) return "(" + waitMs + "ms)";
        String url = step.getParam("url", null);
        if (url != null) return "(" + url + ")";
        return "";
    }
}
