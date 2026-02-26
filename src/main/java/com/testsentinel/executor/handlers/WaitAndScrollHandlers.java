package com.testsentinel.executor.handlers;

import com.testsentinel.executor.*;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

// ── WAIT_FOR_URL ──────────────────────────────────────────────────────────────

@HandlesAction("WAIT_FOR_URL")
class WaitForUrlHandler implements ActionHandler {
    private static final Logger log = LoggerFactory.getLogger(WaitForUrlHandler.class);

    @Override
    public ActionResult execute(ActionContext ctx) {
        String pattern   = ctx.getStep().getParam("pattern", null);
        int    timeoutMs = ctx.getStep().getParamInt("timeoutMs", 10_000);
        if (pattern == null)
            return ActionResult.skipped("No 'pattern' parameter for WAIT_FOR_URL");
        if (ctx.isDryRun())
            return ActionResult.skipped("DryRun: would wait for URL containing '" + pattern + "'");
        try {
            new WebDriverWait(ctx.getDriver(), Duration.ofMillis(timeoutMs))
                .until(ExpectedConditions.urlContains(pattern));
            return ActionResult.executed("URL now contains '" + pattern + "'");
        } catch (Exception e) {
            return ActionResult.failed("Timed out waiting for URL '" + pattern + "': " + e.getMessage(), e);
        }
    }
}

// ── WAIT_FIXED ────────────────────────────────────────────────────────────────

@HandlesAction("WAIT_FIXED")
class WaitFixedHandler implements ActionHandler {
    private static final Logger log = LoggerFactory.getLogger(WaitFixedHandler.class);
    private static final int MAX_WAIT_MS = 10_000;

    @Override
    public ActionResult execute(ActionContext ctx) {
        int waitMs = Math.min(ctx.getStep().getParamInt("waitMs", 1_000), MAX_WAIT_MS);
        if (ctx.isDryRun())
            return ActionResult.skipped("DryRun: would wait " + waitMs + "ms");
        try {
            Thread.sleep(waitMs);
            return ActionResult.executed("Waited " + waitMs + "ms");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ActionResult.failed("Sleep interrupted", e);
        }
    }
}

// ── SCROLL_TO_ELEMENT ─────────────────────────────────────────────────────────

@HandlesAction("SCROLL_TO_ELEMENT")
class ScrollToElementHandler implements ActionHandler {
    private static final Logger log = LoggerFactory.getLogger(ScrollToElementHandler.class);

    @Override
    public ActionResult execute(ActionContext ctx) {
        String selector = ctx.getStep().getParam("selector", null);
        if (selector == null)
            return ActionResult.skipped("No 'selector' parameter for SCROLL_TO_ELEMENT");
        if (ctx.isDryRun())
            return ActionResult.skipped("DryRun: would scroll to '" + selector + "'");
        try {
            WebElement el = ctx.getDriver().findElement(By.cssSelector(selector));
            ((JavascriptExecutor) ctx.getDriver())
                .executeScript("arguments[0].scrollIntoView({behavior:'smooth',block:'center'});", el);
            return ActionResult.executed("Scrolled to element: " + selector);
        } catch (Exception e) {
            return ActionResult.failed("Could not scroll to '" + selector + "': " + e.getMessage(), e);
        }
    }
}

// ── SCROLL_TO_TOP ─────────────────────────────────────────────────────────────

@HandlesAction("SCROLL_TO_TOP")
class ScrollToTopHandler implements ActionHandler {
    @Override
    public ActionResult execute(ActionContext ctx) {
        if (ctx.isDryRun())
            return ActionResult.skipped("DryRun: would scroll to top of page");
        try {
            ((JavascriptExecutor) ctx.getDriver()).executeScript("window.scrollTo(0, 0);");
            return ActionResult.executed("Scrolled to top of page");
        } catch (Exception e) {
            return ActionResult.failed("Could not scroll to top: " + e.getMessage(), e);
        }
    }
}
