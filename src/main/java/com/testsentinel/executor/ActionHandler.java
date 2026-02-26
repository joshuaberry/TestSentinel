package com.testsentinel.executor;

/**
 * Implemented by every class that handles a specific action type.
 *
 * <h3>Registration</h3>
 * Implementations must:
 * <ol>
 *   <li>Be annotated with {@link HandlesAction} (value = your action type string)</li>
 *   <li>Have a public no-arg constructor</li>
 *   <li>Be in the {@code com.testsentinel.executor.handlers} package — on the main
 *       classpath <em>or</em> the test classpath — so the
 *       {@link ActionHandlerRegistry} discovers them automatically at startup</li>
 * </ol>
 *
 * <h3>Returning a test outcome</h3>
 * <p>Handlers can optionally tell TestSentinel what to do with the test after the
 * action completes by chaining {@link ActionResult#withTestOutcome} on the returned
 * result. This is useful when the handler has runtime knowledge that a static
 * knowledge-base pattern cannot anticipate:
 *
 * <pre>
 *   {@literal @}HandlesAction("LOGIN_AND_VERIFY")
 *   public class LoginAndVerifyHandler implements ActionHandler {
 *       {@literal @}Override
 *       public ActionResult execute(ActionContext ctx) {
 *           if (ctx.isDryRun()) return ActionResult.skipped("DryRun");
 *           boolean ok = performLogin(ctx.getDriver());
 *           if (ok) {
 *               return ActionResult.executed("Login succeeded")
 *                                  .withTestOutcome(TestOutcome.CONTINUE);
 *           } else {
 *               return ActionResult.failed("Login failed", null)
 *                                  .withTestOutcome(TestOutcome.FAIL_WITH_CONTEXT);
 *           }
 *       }
 *   }
 * </pre>
 *
 * <p>When no handler sets a {@link com.testsentinel.model.TestOutcome}, the original
 * outcome from the knowledge-base pattern is used unchanged.
 *
 * <h3>Implementation rules</h3>
 * <ul>
 *   <li>Respect {@link ActionContext#isDryRun()} — log intent but take no action when true</li>
 *   <li>Never throw unchecked exceptions — catch and return {@link ActionResult#failed}</li>
 *   <li>Be stateless — the same instance is reused across all scenarios</li>
 * </ul>
 */
public interface ActionHandler {
    ActionResult execute(ActionContext context);
}
