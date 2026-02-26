package com.testsentinel.executor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as the handler for a named action type.
 *
 * The {@link ActionHandlerRegistry} scans the {@code com.testsentinel.executor.handlers}
 * package at startup, finds every class annotated with {@code @HandlesAction}, and
 * registers it under the given string name. No manual registration required.
 *
 * The value is a plain String so that consumer test repositories can define their own
 * action types without modifying the library's {@link com.testsentinel.model.ActionType}
 * enum. Built-in handlers use the enum name (e.g. {@code "CLICK"}); consumer repos
 * choose any string they like (e.g. {@code "LOGIN_VIA_SSO"}).
 *
 * <pre>
 *   // Core library handler -- references the built-in constant name
 *   {@literal @}HandlesAction("CLICK")
 *   class ClickHandler implements ActionHandler { ... }
 *
 *   // Consumer repo handler -- defines its own type name
 *   {@literal @}HandlesAction("LOGIN_VIA_SSO")
 *   public class SsoLoginHandler implements ActionHandler { ... }
 * </pre>
 *
 * Rules:
 *   - The annotated class must implement {@link ActionHandler}.
 *   - It must have a public no-arg constructor (used by reflection to instantiate it).
 *   - Each action type name should have at most one handler. Duplicates cause startup failure.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface HandlesAction {
    String value();
}
