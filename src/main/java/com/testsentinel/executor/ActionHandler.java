package com.testsentinel.executor;

/**
 * Implemented by every class that handles a specific
 * {@link com.testsentinel.model.ActionType}.
 *
 * Implementations must:
 *   1. Be annotated with {@link HandlesAction}
 *   2. Have a public no-arg constructor
 *   3. Be in the {@code com.testsentinel.executor.handlers} package so the
 *      {@link ActionHandlerRegistry} can discover them via classpath scanning
 *
 * Implementations should:
 *   - Respect {@link ActionContext#isDryRun()} -- log intent but do nothing when true
 *   - Never throw unchecked exceptions -- catch and return {@link ActionResult#failed}
 *   - Be stateless -- the same instance is reused across all scenarios
 */
public interface ActionHandler {
    ActionResult execute(ActionContext context);
}
