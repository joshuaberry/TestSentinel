package com.testsentinel.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * A single recommended remediation action returned by Phase 2 analysis.
 *
 * ActionSteps are ordered by priority within an ActionPlan — step[0] should
 * be attempted before step[1], etc. Each step carries a confidence score
 * (how likely this action is to resolve the condition) and a riskLevel
 * that the framework uses to gate autonomous execution.
 *
 * ## Risk Gating
 * The consuming framework should respect riskLevel before taking any action:
 *
 *   LOW    → Safe to suggest or auto-execute (no data changes, recoverable)
 *   MEDIUM → Suggest to user; require TESTSENTINEL_ALLOW_MEDIUM_RISK=true to auto-execute
 *   HIGH   → Always present as recommendation only; never auto-execute in Phase 2
 *            (Phase 3 adds explicit HIGH-risk opt-in per action type)
 *
 * ## Parameters by ActionType
 * Common parameter keys (all optional — Claude populates what it can infer):
 *
 *   CLICK / CLICK_IF_PRESENT:
 *     "selector"      CSS selector for the target element
 *     "xpath"         XPath alternative if CSS is unreliable
 *     "description"   Human-readable element description for logging
 *
 *   WAIT_FOR_ELEMENT:
 *     "selector"      CSS selector to wait for
 *     "condition"     "visible" | "clickable" | "present" (default: "visible")
 *     "timeoutMs"     Max wait in milliseconds (default: 10000)
 *
 *   WAIT_FIXED:
 *     "waitMs"        Milliseconds to wait (e.g., 500, 1000, 2000)
 *
 *   NAVIGATE_TO:
 *     "url"           Target URL (absolute or relative)
 *
 *   DISMISS_OVERLAY:
 *     "selector"      Close button selector (Claude infers from DOM)
 *     "method"        "click" | "escape" | "click_outside"
 *
 *   RETRY_ACTION:
 *     "delayMs"       Wait before retry (default: 1000)
 *     "maxRetries"    Max retry attempts (default: 3)
 *
 *   EXECUTE_SCRIPT:
 *     "script"        JavaScript to execute (HIGH risk — requires explicit opt-in)
 *
 *   QUERY_APM:
 *     "tool"          "dynatrace" | "datadog" | "newrelic" | "splunk"
 *     "query"         Tool-specific query expression
 *     "timeWindowMin" Look-back window in minutes (default: 15)
 *
 *   SKIP_TEST:
 *     "reason"        Enriched skip reason for test report
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActionStep {

    public enum RiskLevel { LOW, MEDIUM, HIGH }

    private ActionType actionType;
    private String description;           // Plain-language description for logging and reports
    private Map<String, Object> parameters; // Action-specific parameters
    private double confidence;            // 0.0 – 1.0: probability this action resolves the condition
    private RiskLevel riskLevel;          // Gating level for autonomous execution
    private String rationale;            // Why Claude recommends this action
    private boolean requiresVerification; // If true, Phase 3 should verify success before proceeding

    public ActionStep() {}

    // ── Getters ───────────────────────────────────────────────────────────────

    public ActionType getActionType() { return actionType; }
    public String getDescription() { return description; }
    public Map<String, Object> getParameters() { return parameters; }
    public double getConfidence() { return confidence; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public String getRationale() { return rationale; }
    public boolean isRequiresVerification() { return requiresVerification; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setActionType(ActionType actionType) { this.actionType = actionType; }
    public void setDescription(String description) { this.description = description; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public void setRiskLevel(RiskLevel riskLevel) { this.riskLevel = riskLevel; }
    public void setRationale(String rationale) { this.rationale = rationale; }
    public void setRequiresVerification(boolean requiresVerification) { this.requiresVerification = requiresVerification; }

    // ── Convenience ───────────────────────────────────────────────────────────

    public boolean isLowRisk() { return riskLevel == RiskLevel.LOW; }
    public boolean isMediumRisk() { return riskLevel == RiskLevel.MEDIUM; }
    public boolean isHighRisk() { return riskLevel == RiskLevel.HIGH; }
    public boolean isHighConfidence() { return confidence >= 0.80; }

    /**
     * Returns the parameter value as a String, or defaultValue if not present.
     */
    public String getParam(String key, String defaultValue) {
        if (parameters == null) return defaultValue;
        Object val = parameters.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    /**
     * Returns the parameter value as an int, or defaultValue if not present or not parseable.
     */
    public int getParamInt(String key, int defaultValue) {
        if (parameters == null) return defaultValue;
        Object val = parameters.get(key);
        if (val == null) return defaultValue;
        try { return Integer.parseInt(val.toString()); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    @Override
    public String toString() {
        return String.format("ActionStep{type=%s, risk=%s, confidence=%.2f, desc='%s'}",
            actionType, riskLevel, confidence, description);
    }
}
