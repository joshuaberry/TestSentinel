package com.testsentinel.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * An ordered list of recommended remediation steps produced by TestSentinel analysis.
 *
 * The ActionPlan is attached to InsightResponse when analysis produces recommended actions.
 * Steps are ordered by recommended execution sequence -- execute step[0] first.
 *
 * ## Usage
 * <pre>
 *   InsightResponse insight = sentinel.analyzeException(driver, e, steps, meta);
 *   ActionPlan plan = insight.getActionPlan();
 *
 *   if (plan != null && plan.hasLowRiskActions()) {
 *       // Log recommendations to test report
 *       plan.getLowRiskActions().forEach(action ->
 *           log.info("Recommended: [{}] {}", action.getActionType(), action.getDescription())
 *       );
 *   }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActionPlan {

    private List<ActionStep> actions;
    private String planSummary;       // One-sentence summary of the overall recovery strategy
    private double planConfidence;    // Overall confidence the plan will resolve the condition
    private boolean requiresHuman;   // True if Claude believes human intervention is needed

    public ActionPlan() {}

    // ── Getters ───────────────────────────────────────────────────────────────

    public List<ActionStep> getActions() {
        return actions != null ? actions : Collections.emptyList();
    }
    public String getPlanSummary() { return planSummary; }
    public double getPlanConfidence() { return planConfidence; }
    public boolean isRequiresHuman() { return requiresHuman; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setActions(List<ActionStep> actions) { this.actions = actions; }
    public void setPlanSummary(String planSummary) { this.planSummary = planSummary; }
    public void setPlanConfidence(double planConfidence) { this.planConfidence = planConfidence; }
    public void setRequiresHuman(boolean requiresHuman) { this.requiresHuman = requiresHuman; }

    // ── Convenience Filters ───────────────────────────────────────────────────

    /** Returns only LOW risk steps -- safe for suggestion or automated advisory */
    public List<ActionStep> getLowRiskActions() {
        return getActions().stream()
            .filter(ActionStep::isLowRisk)
            .collect(Collectors.toList());
    }

    /** Returns only MEDIUM risk steps */
    public List<ActionStep> getMediumRiskActions() {
        return getActions().stream()
            .filter(ActionStep::isMediumRisk)
            .collect(Collectors.toList());
    }

    /** Returns only HIGH risk steps */
    public List<ActionStep> getHighRiskActions() {
        return getActions().stream()
            .filter(ActionStep::isHighRisk)
            .collect(Collectors.toList());
    }

    /** Returns steps at or below the given risk level */
    public List<ActionStep> getActionsAtOrBelow(ActionStep.RiskLevel maxRisk) {
        return getActions().stream()
            .filter(a -> a.getRiskLevel() != null && a.getRiskLevel().ordinal() <= maxRisk.ordinal())
            .collect(Collectors.toList());
    }

    /** Returns steps above the given confidence threshold */
    public List<ActionStep> getHighConfidenceActions(double minConfidence) {
        return getActions().stream()
            .filter(a -> a.getConfidence() >= minConfidence)
            .collect(Collectors.toList());
    }

    public boolean hasActions() {
        return actions != null && !actions.isEmpty();
    }

    public boolean hasLowRiskActions() {
        return !getLowRiskActions().isEmpty();
    }

    public int size() {
        return actions != null ? actions.size() : 0;
    }

    /** Returns the first action in the plan, or null if empty */
    public ActionStep firstAction() {
        return hasActions() ? actions.get(0) : null;
    }

    @Override
    public String toString() {
        return String.format("ActionPlan{steps=%d, planConfidence=%.2f, requiresHuman=%b, summary='%s'}",
            size(), planConfidence, requiresHuman, planSummary);
    }
}
