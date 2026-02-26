package com.testsentinel.model;

/**
 * The test-level outcome a custom {@link com.testsentinel.executor.ActionHandler}
 * recommends after it finishes executing.
 *
 * <p>Returning a {@code TestOutcome} from an action handler is optional — it is only
 * needed when the handler has runtime knowledge that should override the static outcome
 * specified in the knowledge-base pattern. For example, a {@code LOGIN_AND_VERIFY}
 * handler that actually attempts authentication knows at runtime whether the login
 * succeeded or not; a static KB pattern cannot know this in advance.
 *
 * <h3>Usage</h3>
 * <pre>
 *   {@literal @}HandlesAction("LOGIN_AND_VERIFY")
 *   public class LoginAndVerifyHandler implements ActionHandler {
 *       {@literal @}Override
 *       public ActionResult execute(ActionContext ctx) {
 *           boolean success = performLogin(ctx.getDriver());
 *           if (success) {
 *               return ActionResult.executed("Login succeeded — continuing")
 *                                  .withTestOutcome(TestOutcome.CONTINUE);
 *           } else {
 *               return ActionResult.failed("Login failed after retry", null)
 *                                  .withTestOutcome(TestOutcome.FAIL_WITH_CONTEXT);
 *           }
 *       }
 *   }
 * </pre>
 *
 * <p>When no handler sets a {@code TestOutcome}, the original outcome from the
 * knowledge-base pattern is used unchanged.
 *
 * <p>When multiple steps execute and more than one sets a {@code TestOutcome},
 * the last non-null outcome in execution order wins — representing the terminal
 * state of the action sequence.
 */
public enum TestOutcome {

    /** No problem — the action resolved the condition; the test may proceed. */
    CONTINUE,

    /** The action was partially successful; the original action should be retried. */
    RETRY,

    /** The action cannot resolve the condition; skip this test but continue the suite. */
    SKIP,

    /** A genuine failure; attach the insight to the test failure report. */
    FAIL_WITH_CONTEXT,

    /** The handler could not determine the outcome; a human should investigate. */
    INVESTIGATE
}
