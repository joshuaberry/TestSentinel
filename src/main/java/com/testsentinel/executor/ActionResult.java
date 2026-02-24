package com.testsentinel.executor;

/**
 * The result returned by an {@link ActionHandler} after attempting to execute one
 * {@link com.testsentinel.model.ActionStep}.
 *
 * Immutable — use the static factories.
 */
public class ActionResult {

    private final ActionOutcome outcome;
    private final String message;
    private final Throwable error;  // non-null only when outcome == FAILED

    private ActionResult(ActionOutcome outcome, String message, Throwable error) {
        this.outcome = outcome;
        this.message = message;
        this.error   = error;
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

    // ── Getters ───────────────────────────────────────────────────────────────

    public ActionOutcome getOutcome()  { return outcome; }
    public String        getMessage()  { return message; }
    public Throwable     getError()    { return error; }

    public boolean isExecuted()  { return outcome == ActionOutcome.EXECUTED; }
    public boolean isSkipped()   { return outcome == ActionOutcome.SKIPPED; }
    public boolean isFailed()    { return outcome == ActionOutcome.FAILED; }
    public boolean isNotFound()  { return outcome == ActionOutcome.NOT_FOUND; }

    @Override
    public String toString() {
        return String.format("ActionResult{outcome=%s, message='%s'}", outcome, message);
    }
}
