package com.testsentinel.executor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as the handler for a specific {@link com.testsentinel.model.ActionType}.
 *
 * The {@link ActionHandlerRegistry} scans the {@code com.testsentinel.executor.handlers}
 * package at startup, finds every class annotated with {@code @HandlesAction}, and
 * registers it under the named ActionType. No manual registration required.
 *
 * <pre>
 *   {@literal @}HandlesAction(ActionType.CLICK)
 *   public class ClickHandler implements ActionHandler {
 *       public ActionResult execute(ActionContext ctx) { ... }
 *   }
 * </pre>
 *
 * Rules:
 *   - The annotated class must implement {@link ActionHandler}.
 *   - It must have a public no-arg constructor (used by reflection to instantiate it).
 *   - Each ActionType should have at most one handler. Duplicates cause startup failure.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface HandlesAction {
    com.testsentinel.model.ActionType value();
}
