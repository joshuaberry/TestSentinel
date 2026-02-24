package com.testsentinel.executor;

/**
 * The result of a single handler's execution attempt.
 *
 *   EXECUTED   — handler ran and the action was performed successfully
 *   SKIPPED    — handler decided not to act (element absent, risk gate, dry-run mode)
 *   FAILED     — handler ran but the action threw an exception or did not succeed
 *   NOT_FOUND  — no handler was registered for this ActionType
 */
public enum ActionOutcome {
    EXECUTED,
    SKIPPED,
    FAILED,
    NOT_FOUND
}
