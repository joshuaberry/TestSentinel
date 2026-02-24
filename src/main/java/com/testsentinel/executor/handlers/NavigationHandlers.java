package com.testsentinel.executor.handlers;

import com.testsentinel.executor.*;
import com.testsentinel.model.ActionType;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// ── REFRESH_PAGE ──────────────────────────────────────────────────────────────

@HandlesAction(ActionType.REFRESH_PAGE)
class RefreshPageHandler implements ActionHandler {
    private static final Logger log = LoggerFactory.getLogger(RefreshPageHandler.class);

    @Override
    public ActionResult execute(ActionContext ctx) {
        if (ctx.isDryRun())
            return ActionResult.skipped("DryRun: would refresh page");
        try {
            String urlBefore = ctx.getDriver().getCurrentUrl();
            ctx.getDriver().navigate().refresh();
            return ActionResult.executed("Page refreshed: " + urlBefore);
        } catch (Exception e) {
            return ActionResult.failed("Could not refresh page: " + e.getMessage(), e);
        }
    }
}

// ── NAVIGATE_BACK ─────────────────────────────────────────────────────────────

@HandlesAction(ActionType.NAVIGATE_BACK)
class NavigateBackHandler implements ActionHandler {
    @Override
    public ActionResult execute(ActionContext ctx) {
        if (ctx.isDryRun())
            return ActionResult.skipped("DryRun: would navigate back");
        try {
            ctx.getDriver().navigate().back();
            return ActionResult.executed("Navigated back to: " + ctx.getDriver().getCurrentUrl());
        } catch (Exception e) {
            return ActionResult.failed("Could not navigate back: " + e.getMessage(), e);
        }
    }
}

// ── NAVIGATE_TO ───────────────────────────────────────────────────────────────

@HandlesAction(ActionType.NAVIGATE_TO)
class NavigateToHandler implements ActionHandler {
    @Override
    public ActionResult execute(ActionContext ctx) {
        String url = ctx.getStep().getParam("url", null);
        if (url == null)
            return ActionResult.skipped("No 'url' parameter for NAVIGATE_TO");
        if (ctx.isDryRun())
            return ActionResult.skipped("DryRun: would navigate to '" + url + "'");
        try {
            ctx.getDriver().get(url);
            return ActionResult.executed("Navigated to: " + url);
        } catch (Exception e) {
            return ActionResult.failed("Could not navigate to '" + url + "': " + e.getMessage(), e);
        }
    }
}
