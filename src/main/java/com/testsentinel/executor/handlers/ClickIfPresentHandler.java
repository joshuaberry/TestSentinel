package com.testsentinel.executor.handlers;

import com.testsentinel.executor.*;
import com.testsentinel.model.ActionType;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@HandlesAction(ActionType.CLICK_IF_PRESENT)
public class ClickIfPresentHandler implements ActionHandler {
    private static final Logger log = LoggerFactory.getLogger(ClickIfPresentHandler.class);

    @Override
    public ActionResult execute(ActionContext ctx) {
        String selector = ctx.getStep().getParam("selector", null);
        if (selector == null)
            return ActionResult.skipped("No 'selector' parameter for CLICK_IF_PRESENT");
        if (ctx.isDryRun())
            return ActionResult.skipped("DryRun: would click '" + selector + "' if present");
        try {
            List<WebElement> els = ctx.getDriver().findElements(By.cssSelector(selector));
            if (els.isEmpty() || !els.get(0).isDisplayed()) {
                return ActionResult.skipped("Element '" + selector + "' not present -- skipped safely");
            }
            els.get(0).click();
            return ActionResult.executed("Clicked present element: " + selector);
        } catch (Exception e) {
            return ActionResult.failed("CLICK_IF_PRESENT failed for '" + selector + "': " + e.getMessage(), e);
        }
    }
}
