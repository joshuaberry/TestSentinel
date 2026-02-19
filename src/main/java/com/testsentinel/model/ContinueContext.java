package com.testsentinel.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Populated by Claude when suggestedTestOutcome = CONTINUE.
 *
 * When Claude determines there is no problem and the test should proceed,
 * this object provides the framework with the information it needs to resume
 * intelligently — not just "keep going" but "keep going from here, because of this."
 *
 * ## Populated when conditionCategory is:
 *   NAVIGATED_PAST          — session/cookie carried the user past an expected page
 *   STATE_ALREADY_SATISFIED — a precondition is already true before the test established it
 *
 * ## Usage
 * <pre>
 *   InsightResponse insight = sentinel.analyzeWrongPage(driver, expectedUrl, steps, meta);
 *
 *   if (insight.isContinuable()) {
 *       ContinueContext ctx = insight.getContinueContext();
 *
 *       log.info("TestSentinel: Continue from step '{}' — {}",
 *           ctx.getResumeFromStepHint(), ctx.getContinueReason());
 *
 *       // If the test has named steps, skip to the resume point:
 *       if (ctx.getResumeFromStepHint() != null) {
 *           jumpToStep(ctx.getResumeFromStepHint());
 *       }
 *
 *       // Note any caveats for the test report:
 *       if (ctx.hasCaveats()) {
 *           reporter.addNote("TestSentinel: " + ctx.getCaveats());
 *       }
 *   }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContinueContext {

    /**
     * Human-readable explanation of why the test can continue.
     * Example: "User session cookie is present and the application has already
     * authenticated the user, bypassing the login page the test expected to see."
     */
    private String continueReason;

    /**
     * The observed state that makes continuation valid.
     * Example: "User is on /dashboard — the expected post-login destination."
     */
    private String observedState;

    /**
     * A hint for which named test step to resume from, expressed as a step name
     * or description matching what the test recorded via recordStep().
     *
     * Example: "Verify dashboard header is visible"
     *
     * Null if Claude cannot infer a specific resume point — in that case the
     * test should continue from wherever it currently is.
     */
    private String resumeFromStepHint;

    /**
     * Any caveats the test engineer should be aware of.
     * Example: "The authenticated user identity has not been verified.
     * If the test requires a specific user account, validate session ownership before proceeding."
     *
     * This is where the 'ignore whether the user is the intended user' note lives — Claude
     * will flag it here without blocking continuation.
     */
    private String caveats;

    /**
     * Whether the test should log this as an informational note in the report.
     * True when the continuation involves a state assumption that may affect result validity.
     */
    private boolean noteworthy;

    public ContinueContext() {}

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getContinueReason() { return continueReason; }
    public String getObservedState() { return observedState; }
    public String getResumeFromStepHint() { return resumeFromStepHint; }
    public String getCaveats() { return caveats; }
    public boolean isNoteworthy() { return noteworthy; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setContinueReason(String continueReason) { this.continueReason = continueReason; }
    public void setObservedState(String observedState) { this.observedState = observedState; }
    public void setResumeFromStepHint(String resumeFromStepHint) { this.resumeFromStepHint = resumeFromStepHint; }
    public void setCaveats(String caveats) { this.caveats = caveats; }
    public void setNoteworthy(boolean noteworthy) { this.noteworthy = noteworthy; }

    // ── Convenience ───────────────────────────────────────────────────────────

    public boolean hasResumeHint() { return resumeFromStepHint != null && !resumeFromStepHint.isBlank(); }
    public boolean hasCaveats() { return caveats != null && !caveats.isBlank(); }

    @Override
    public String toString() {
        return String.format("ContinueContext{reason='%s', resumeHint='%s', caveats='%s', noteworthy=%b}",
            continueReason, resumeFromStepHint, caveats, noteworthy);
    }
}
