package com.testsentinel.executor.handlers;

import com.testsentinel.executor.ActionContext;
import com.testsentinel.executor.ActionHandler;
import com.testsentinel.executor.ActionResult;
import com.testsentinel.executor.HandlesAction;
import com.testsentinel.model.TestOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Demo action handler registered in the test source tree.
 *
 * Because ActionHandlerRegistry scans com.testsentinel.executor.handlers
 * across the full runtime classpath (main + test), this handler is discovered
 * automatically at test execution time without any change to registry code.
 *
 * Risk level: LOW -- safe to execute autonomously.
 *
 * Demonstrates returning a TestOutcome so TestSentinel knows what to do with
 * the test after this action completes. Real handlers use this to make runtime
 * decisions that a static KB pattern cannot anticipate -- for example, returning
 * CONTINUE when a corrective action succeeds, or FAIL_WITH_CONTEXT when it does not.
 */
@HandlesAction("DUMMY_ACTION")
public class DummyActionHandler implements ActionHandler {

    private static final Logger log = LoggerFactory.getLogger(DummyActionHandler.class);

    @Override
    public ActionResult execute(ActionContext ctx) {
        if (ctx.isDryRun()) {
            return ActionResult.skipped("DryRun: would run the dummy action");
        }
        log.info("Ran the dummy action");
        return ActionResult.executed("Dummy action completed")
                           .withTestOutcome(TestOutcome.CONTINUE);
    }
}
