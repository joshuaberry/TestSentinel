package com.testsentinel.executor.checker;

import com.testsentinel.model.ActionPlan;
import com.testsentinel.model.InsightResponse;

/**
 * The result returned by a {@link ConditionChecker} after inspecting the current
 * driver state.
 *
 * A checker either:
 *   - MATCHED  -- it recognised the condition and provides a ready-made diagnosis
 *                (category, root cause, confidence, action plan). No API call needed.
 *   - NO_MATCH -- the checker did not recognise this condition; try the next one.
 *
 * Immutable -- use the static factories.
 */
public class CheckerResult {

    public enum Status { MATCHED, NO_MATCH }

    private final Status               status;
    private final String               checkerId;      // name of the checker that produced this
    private final String               diagnosis;      // human-readable description of what was found
    private final InsightResponse.ConditionCategory category;
    private final double               confidence;     // 0.0 – 1.0
    private final ActionPlan           actionPlan;     // recommended steps (may be null)
    private final String               suggestedOutcome;

    private CheckerResult(Status status, String checkerId, String diagnosis,
                          InsightResponse.ConditionCategory category, double confidence,
                          ActionPlan actionPlan, String suggestedOutcome) {
        this.status          = status;
        this.checkerId       = checkerId;
        this.diagnosis       = diagnosis;
        this.category        = category;
        this.confidence      = confidence;
        this.actionPlan      = actionPlan;
        this.suggestedOutcome = suggestedOutcome;
    }

    // ── Static factories ──────────────────────────────────────────────────────

    /** The checker recognised the condition. */
    public static CheckerResult matched(String checkerId,
                                         InsightResponse.ConditionCategory category,
                                         String diagnosis,
                                         double confidence,
                                         ActionPlan actionPlan,
                                         String suggestedOutcome) {
        return new CheckerResult(Status.MATCHED, checkerId, diagnosis,
            category, confidence, actionPlan, suggestedOutcome);
    }

    /** The checker did not recognise this condition. */
    public static CheckerResult noMatch(String checkerId) {
        return new CheckerResult(Status.NO_MATCH, checkerId, null,
            null, 0.0, null, null);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public boolean isMatched()   { return status == Status.MATCHED; }
    public boolean isNoMatch()   { return status == Status.NO_MATCH; }

    public Status              getStatus()          { return status; }
    public String              getCheckerId()       { return checkerId; }
    public String              getDiagnosis()       { return diagnosis; }
    public InsightResponse.ConditionCategory getCategory() { return category; }
    public double              getConfidence()      { return confidence; }
    public ActionPlan          getActionPlan()      { return actionPlan; }
    public String              getSuggestedOutcome(){ return suggestedOutcome; }

    @Override
    public String toString() {
        return status == Status.MATCHED
            ? String.format("CheckerResult{MATCHED, checker=%s, category=%s, confidence=%.2f}",
                checkerId, category, confidence)
            : String.format("CheckerResult{NO_MATCH, checker=%s}", checkerId);
    }
}
