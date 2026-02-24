package com.testsentinel.executor.checker;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a {@link ConditionChecker} implementation available for
 * reflection-based discovery by the {@link ConditionCheckerRegistry}.
 *
 * <pre>
 *   {@literal @}ChecksCondition(id = "page-timeout", priority = 10)
 *   public class PageTimeoutChecker implements ConditionChecker {
 *       public CheckerResult check(WebDriver driver, ConditionEvent event) { ... }
 *   }
 * </pre>
 *
 * Rules:
 *   - The annotated class must implement {@link ConditionChecker}.
 *   - It must have a public no-arg constructor.
 *   - ids must be unique across all checkers — duplicates cause startup failure.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ChecksCondition {

    /**
     * Unique identifier for this checker. Used in logging and {@link CheckerResult}.
     */
    String id();

    /**
     * Execution priority. Lower = earlier. Default 100.
     * Overrides {@link ConditionChecker#priority()} when set.
     */
    int priority() default 100;
}
