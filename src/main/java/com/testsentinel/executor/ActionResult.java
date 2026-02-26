package com.testsentinel.executor;

import com.testsentinel.model.TestOutcome;

import java.util.List;

/**
 * The result returned by an {@link ActionHandler} after attempting to execute one
 * {@link com.testsentinel.model.ActionStep}.
 *
 * <p>Immutable — use the static factories. Chain {@link #withTestOutcome} to signal
 * how TestSentinel should handle the test after this action completes.
 *
 * <h3>Signalling a test outcome</h3>
 * <pre>
 *   // The handler succeeded AND knows the test should continue:
 *   return ActionResult.executed("Session restored").withTestOutcome(TestOutcome.CONTINUE);
 *
 *   // The handler failed AND the test should be marked as failed:
 *   return ActionResult.failed("Could not restore session", ex)
 *                      .withTestOutcome(TestOutcome.FAIL_WITH_CONTEXT);
 * </pre>
 *
 * <p>Setting a {@link TestOutcome} is optional. When present it overrides the static
 * outcome stored in the knowledge-base pattern, allowing handlers to make runtime
 * decisions that a static pattern cannot anticipate.
 */
public class ActionResult {

    private final ActionOutcome outcome;
    private final String        message;
    private final Throwable     error;       // non-null only when outcome == FAILED
    private final TestOutcome   testOutcome; // null when handler does not override

    private ActionResult(ActionOutcome outcome, String message, Throwable error) {
        this(outcome, message, error, null);
    }

    private ActionResult(ActionOutcome outcome, String message, Throwable error,
                         TestOutcome testOutcome) {
        this.outcome     = outcome;
        this.message     = message;
        this.error       = error;
        this.testOutcome = testOutcome;
    }

    // ── Static factories ──────────────────────────────────────────────────────

    public static ActionResult executed(String message) {
        return new ActionResult(ActionOutcome.EXECUTED, message, null);
    }

    public static ActionResult skipped(String reason) {
        return new ActionResult(ActionOutcome.SKIPPED, reason, null);
    }

    public static ActionResult failed(String message, Throwable cause) {
        return new ActionResult(ActionOutcome.FAILED, message, cause);
    }

    public static ActionResult notFound(String actionType) {
        return new ActionResult(ActionOutcome.NOT_FOUND,
            "No handler registered for action type: " + actionType, null);
    }

    // ── Fluent test-outcome builder ───────────────────────────────────────────

    /**
     * Returns a new {@code ActionResult} with the given {@link TestOutcome} attached.
     *
     * <p>Use this to tell TestSentinel what to do with the test after this action
     * completes. When multiple steps in a plan each set a {@code TestOutcome}, the
     * last non-null outcome in execution order takes effect.
     *
     * @param outcome the desired test outcome; {@code null} clears any prior setting
     * @return a new immutable instance with the outcome attached
     */
    public ActionResult withTestOutcome(TestOutcome outcome) {
        return new ActionResult(this.outcome, this.message, this.error, outcome);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public ActionOutcome getOutcome()     { return outcome; }
    public String        getMessage()     { return message; }
    public Throwable     getError()       { return error; }

    /** The test-level outcome this handler recommends, or {@code null} if not set. */
    public TestOutcome   getTestOutcome() { return testOutcome; }

    /** {@code true} when the handler attached a test-level outcome recommendation. */
    public boolean hasTestOutcome()       { return testOutcome != null; }

    public boolean isExecuted()  { return outcome == ActionOutcome.EXECUTED; }
    public boolean isSkipped()   { return outcome == ActionOutcome.SKIPPED; }
    public boolean isFailed()    { return outcome == ActionOutcome.FAILED; }
    public boolean isNotFound()  { return outcome == ActionOutcome.NOT_FOUND; }

    // ── Static helper ─────────────────────────────────────────────────────────

    /**
     * Scans a list of results and returns the last non-null {@link TestOutcome}
     * found in execution order, or {@code null} if no handler set one.
     *
     * <p>The last-wins rule reflects the terminal state of the action sequence:
     * later steps run after earlier ones succeed, so the final handler's opinion
     * is the most up-to-date assessment of the test's situation.
     *
     * @param results ordered list of results from {@link ActionPlanExecutor}
     * @return the effective test outcome, or {@code null} if none was set
     */
    public static TestOutcome resolveTestOutcome(List<ActionResult> results) {
        TestOutcome resolved = null;
        for (ActionResult r : results) {
            if (r.testOutcome != null) resolved = r.testOutcome;
        }
        return resolved;
    }

    @Override
    public String toString() {
        return testOutcome != null
            ? String.format("ActionResult{outcome=%s, testOutcome=%s, message='%s'}",
                outcome, testOutcome, message)
            : String.format("ActionResult{outcome=%s, message='%s'}", outcome, message);
    }
}
