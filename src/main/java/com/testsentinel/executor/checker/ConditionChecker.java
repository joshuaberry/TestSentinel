package com.testsentinel.executor.checker;

import com.testsentinel.model.ConditionEvent;
import org.openqa.selenium.WebDriver;

/**
 * Inspects the current browser state and determines whether a specific condition
 * is present -- without making any API call.
 *
 * Implementations must:
 *   1. Be annotated with {@link ChecksCondition}
 *   2. Have a public no-arg constructor
 *   3. Live in {@code com.testsentinel.executor.checker.checks} so the
 *      {@link ConditionCheckerRegistry} can discover them via classpath scanning
 *
 * Implementations should:
 *   - Be fast -- checkers run synchronously before any API call
 *   - Be stateless -- the same instance is reused across all events
 *   - Never throw -- catch all exceptions and return {@link CheckerResult#noMatch}
 *   - Return {@link CheckerResult#noMatch} when uncertain -- false positives are
 *     worse than misses because they prevent Claude from seeing the real cause
 */
public interface ConditionChecker {

    /**
     * Inspect the driver and event and return a diagnosis or NO_MATCH.
     *
     * @param driver  The active WebDriver -- may be used to inspect page state
     * @param event   The condition event that triggered analysis
     * @return        MATCHED with diagnosis, or NO_MATCH
     */
    CheckerResult check(WebDriver driver, ConditionEvent event);

    /**
     * The priority order for this checker. Lower numbers run first.
     * Default is 100. Checkers with the same priority run in undefined order.
     *
     * Use low numbers (10–50) for cheap, highly specific checks.
     * Use high numbers (150+) for expensive or broad checks.
     */
    default int priority() { return 100; }
}
