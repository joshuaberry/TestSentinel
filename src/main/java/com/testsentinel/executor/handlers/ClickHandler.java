// ─────────────────────────────────────────────────────────────────────────────
// FILE: ClickHandler.java
// ─────────────────────────────────────────────────────────────────────────────
package com.testsentinel.executor.handlers;

import com.testsentinel.executor.ActionContext;
import com.testsentinel.executor.ActionHandler;
import com.testsentinel.executor.ActionResult;
import com.testsentinel.executor.HandlesAction;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

@HandlesAction("CLICK")
public class ClickHandler implements ActionHandler {
    private static final Logger log = LoggerFactory.getLogger(ClickHandler.class);

    @Override
    public ActionResult execute(ActionContext ctx) {
        String selector = ctx.getStep().getParam("selector", null);
        if (selector == null)
            return ActionResult.skipped("No 'selector' parameter provided for CLICK");
        if (ctx.isDryRun())
            return ActionResult.skipped("DryRun: would click '" + selector + "'");
        try {
            WebElement el = new WebDriverWait(ctx.getDriver(), Duration.ofSeconds(10))
                .until(ExpectedConditions.elementToBeClickable(By.cssSelector(selector)));
            el.click();
            return ActionResult.executed("Clicked element: " + selector);
        } catch (Exception e) {
            return ActionResult.failed("Could not click '" + selector + "': " + e.getMessage(), e);
        }
    }
}
