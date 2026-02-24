package com.testsentinel.executor.handlers;

import com.testsentinel.executor.*;
import com.testsentinel.model.ActionType;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

@HandlesAction(ActionType.WAIT_FOR_ELEMENT)
public class WaitForElementHandler implements ActionHandler {
    private static final Logger log = LoggerFactory.getLogger(WaitForElementHandler.class);

    @Override
    public ActionResult execute(ActionContext ctx) {
        String selector  = ctx.getStep().getParam("selector", null);
        String condition = ctx.getStep().getParam("condition", "visible");
        int    timeoutMs = ctx.getStep().getParamInt("timeoutMs", 10_000);
        if (selector == null)
            return ActionResult.skipped("No 'selector' parameter for WAIT_FOR_ELEMENT");
        if (ctx.isDryRun())
            return ActionResult.skipped("DryRun: would wait for '" + selector + "' to be " + condition);
        try {
            WebDriverWait wait = new WebDriverWait(ctx.getDriver(), Duration.ofMillis(timeoutMs));
            By by = By.cssSelector(selector);
            switch (condition.toLowerCase()) {
                case "clickable" -> wait.until(ExpectedConditions.elementToBeClickable(by));
                case "present"   -> wait.until(ExpectedConditions.presenceOfElementLocated(by));
                default          -> wait.until(ExpectedConditions.visibilityOfElementLocated(by));
            }
            return ActionResult.executed("Element '" + selector + "' is " + condition);
        } catch (Exception e) {
            return ActionResult.failed("Timed out waiting for '" + selector + "': " + e.getMessage(), e);
        }
    }
}
