package com.testsentinel.executor.handlers;

import com.testsentinel.executor.*;
import com.testsentinel.model.ActionType;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

// ── DISMISS_OVERLAY ───────────────────────────────────────────────────────────

@HandlesAction(ActionType.DISMISS_OVERLAY)
class DismissOverlayHandler implements ActionHandler {
    private static final Logger log = LoggerFactory.getLogger(DismissOverlayHandler.class);

    @Override
    public ActionResult execute(ActionContext ctx) {
        String selector = ctx.getStep().getParam("selector", null);
        String method   = ctx.getStep().getParam("method", "click");
        if (ctx.isDryRun())
            return ActionResult.skipped("DryRun: would dismiss overlay via " + method);
        try {
            switch (method.toLowerCase()) {
                case "escape" -> {
                    ctx.getDriver().findElement(By.cssSelector("body")).sendKeys(Keys.ESCAPE);
                    return ActionResult.executed("Dismissed overlay via Escape key");
                }
                case "click_outside" -> {
                    ((org.openqa.selenium.JavascriptExecutor) ctx.getDriver())
                        .executeScript("document.elementFromPoint(0,0).click();");
                    return ActionResult.executed("Dismissed overlay by clicking outside");
                }
                default -> {
                    if (selector == null)
                        return ActionResult.skipped("No 'selector' for DISMISS_OVERLAY click method");
                    List<WebElement> els = ctx.getDriver().findElements(By.cssSelector(selector));
                    if (els.isEmpty() || !els.get(0).isDisplayed())
                        return ActionResult.skipped("Overlay close button '" + selector + "' not visible");
                    els.get(0).click();
                    return ActionResult.executed("Dismissed overlay by clicking: " + selector);
                }
            }
        } catch (Exception e) {
            return ActionResult.failed("Could not dismiss overlay: " + e.getMessage(), e);
        }
    }
}

// ── ACCEPT_ALERT ──────────────────────────────────────────────────────────────

@HandlesAction(ActionType.ACCEPT_ALERT)
class AcceptAlertHandler implements ActionHandler {
    @Override
    public ActionResult execute(ActionContext ctx) {
        if (ctx.isDryRun())
            return ActionResult.skipped("DryRun: would accept browser alert");
        try {
            Alert alert = new WebDriverWait(ctx.getDriver(), Duration.ofSeconds(5))
                .until(ExpectedConditions.alertIsPresent());
            String text = alert.getText();
            alert.accept();
            return ActionResult.executed("Accepted alert: '" + text + "'");
        } catch (Exception e) {
            return ActionResult.failed("Could not accept alert: " + e.getMessage(), e);
        }
    }
}

// ── DISMISS_ALERT ─────────────────────────────────────────────────────────────

@HandlesAction(ActionType.DISMISS_ALERT)
class DismissAlertHandler implements ActionHandler {
    @Override
    public ActionResult execute(ActionContext ctx) {
        if (ctx.isDryRun())
            return ActionResult.skipped("DryRun: would dismiss browser alert");
        try {
            Alert alert = new WebDriverWait(ctx.getDriver(), Duration.ofSeconds(5))
                .until(ExpectedConditions.alertIsPresent());
            String text = alert.getText();
            alert.dismiss();
            return ActionResult.executed("Dismissed alert: '" + text + "'");
        } catch (Exception e) {
            return ActionResult.failed("Could not dismiss alert: " + e.getMessage(), e);
        }
    }
}
