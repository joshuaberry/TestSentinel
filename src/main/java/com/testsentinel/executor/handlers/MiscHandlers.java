package com.testsentinel.executor.handlers;

import com.testsentinel.executor.*;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// ── RETRY_ACTION ──────────────────────────────────────────────────────────────

@HandlesAction("RETRY_ACTION")
class RetryActionHandler implements ActionHandler {
    private static final Logger log = LoggerFactory.getLogger(RetryActionHandler.class);

    @Override
    public ActionResult execute(ActionContext ctx) {
        int delayMs    = ctx.getStep().getParamInt("delayMs", 1_000);
        int maxRetries = ctx.getStep().getParamInt("maxRetries", 3);
        if (ctx.isDryRun())
            return ActionResult.skipped(
                "DryRun: would retry original action up to " + maxRetries + " time(s) after " + delayMs + "ms");
        // Retry is a signal to the enclosing test framework -- we log it as advisory
        // since the actual retry mechanism lives in the test runner, not the executor.
        log.info("ActionHandler RETRY_ACTION: recommend retrying original action " +
            "(maxRetries={}, delayMs={}). Test framework should honour this.", maxRetries, delayMs);
        return ActionResult.executed(
            "Retry advisory issued -- maxRetries=" + maxRetries + ", delayMs=" + delayMs);
    }
}

// ── CLEAR_COOKIES ─────────────────────────────────────────────────────────────

@HandlesAction("CLEAR_COOKIES")
class ClearCookiesHandler implements ActionHandler {
    @Override
    public ActionResult execute(ActionContext ctx) {
        if (ctx.isDryRun())
            return ActionResult.skipped("DryRun: would clear all cookies and reload");
        try {
            ctx.getDriver().manage().deleteAllCookies();
            ctx.getDriver().navigate().refresh();
            return ActionResult.executed("All cookies cleared and page reloaded");
        } catch (Exception e) {
            return ActionResult.failed("Could not clear cookies: " + e.getMessage(), e);
        }
    }
}

// ── SWITCH_TO_FRAME ───────────────────────────────────────────────────────────

@HandlesAction("SWITCH_TO_FRAME")
class SwitchToFrameHandler implements ActionHandler {
    @Override
    public ActionResult execute(ActionContext ctx) {
        String selector = ctx.getStep().getParam("selector", null);
        if (selector == null)
            return ActionResult.skipped("No 'selector' parameter for SWITCH_TO_FRAME");
        if (ctx.isDryRun())
            return ActionResult.skipped("DryRun: would switch to frame '" + selector + "'");
        try {
            WebElement frame = ctx.getDriver().findElement(By.cssSelector(selector));
            ctx.getDriver().switchTo().frame(frame);
            return ActionResult.executed("Switched to frame: " + selector);
        } catch (Exception e) {
            return ActionResult.failed("Could not switch to frame '" + selector + "': " + e.getMessage(), e);
        }
    }
}

// ── SWITCH_TO_DEFAULT ─────────────────────────────────────────────────────────

@HandlesAction("SWITCH_TO_DEFAULT")
class SwitchToDefaultHandler implements ActionHandler {
    @Override
    public ActionResult execute(ActionContext ctx) {
        if (ctx.isDryRun())
            return ActionResult.skipped("DryRun: would switch to default content");
        try {
            ctx.getDriver().switchTo().defaultContent();
            return ActionResult.executed("Switched to default content");
        } catch (Exception e) {
            return ActionResult.failed("Could not switch to default content: " + e.getMessage(), e);
        }
    }
}

// ── EXECUTE_SCRIPT (HIGH risk) ────────────────────────────────────────────────

@HandlesAction("EXECUTE_SCRIPT")
class ExecuteScriptHandler implements ActionHandler {
    private static final Logger log = LoggerFactory.getLogger(ExecuteScriptHandler.class);

    @Override
    public ActionResult execute(ActionContext ctx) {
        String script = ctx.getStep().getParam("script", null);
        if (script == null)
            return ActionResult.skipped("No 'script' parameter for EXECUTE_SCRIPT");
        if (ctx.isDryRun())
            return ActionResult.skipped("DryRun: would execute script: " + script);
        // Risk gate is already enforced by ActionPlanExecutor -- if we get here,
        // the caller has explicitly set maxRiskLevel to HIGH.
        try {
            Object result = ((JavascriptExecutor) ctx.getDriver()).executeScript(script);
            return ActionResult.executed("Script executed -- result: " + result);
        } catch (Exception e) {
            return ActionResult.failed("Script execution failed: " + e.getMessage(), e);
        }
    }
}

// ── CAPTURE_SCREENSHOT ────────────────────────────────────────────────────────

@HandlesAction("CAPTURE_SCREENSHOT")
class CaptureScreenshotHandler implements ActionHandler {
    private static final Logger log = LoggerFactory.getLogger(CaptureScreenshotHandler.class);

    @Override
    public ActionResult execute(ActionContext ctx) {
        if (ctx.isDryRun())
            return ActionResult.skipped("DryRun: would capture diagnostic screenshot");
        try {
            if (ctx.getDriver() instanceof TakesScreenshot ts) {
                String base64 = ts.getScreenshotAs(OutputType.BASE64);
                log.info("ActionHandler CAPTURE_SCREENSHOT: screenshot captured ({} chars base64)",
                    base64.length());
                return ActionResult.executed("Screenshot captured (" + base64.length() + " chars base64)");
            }
            return ActionResult.skipped("WebDriver does not support screenshots");
        } catch (Exception e) {
            return ActionResult.failed("Screenshot failed: " + e.getMessage(), e);
        }
    }
}

// ── QUERY_APM (advisory -- no live APM integration in this build) ──────────────

@HandlesAction("QUERY_APM")
class QueryApmHandler implements ActionHandler {
    private static final Logger log = LoggerFactory.getLogger(QueryApmHandler.class);

    @Override
    public ActionResult execute(ActionContext ctx) {
        String tool  = ctx.getStep().getParam("tool", "unknown");
        String query = ctx.getStep().getParam("query", "");
        log.info("ActionHandler QUERY_APM: advisory -- tool={}, query='{}'. " +
            "Integrate a real APM client here for Phase 3.", tool, query);
        return ActionResult.skipped(
            "QUERY_APM advisory: check " + tool + " for '" + query + "' -- no live integration configured");
    }
}

// ── CAPTURE_HAR (advisory) ────────────────────────────────────────────────────

@HandlesAction("CAPTURE_HAR")
class CaptureHarHandler implements ActionHandler {
    private static final Logger log = LoggerFactory.getLogger(CaptureHarHandler.class);

    @Override
    public ActionResult execute(ActionContext ctx) {
        log.info("ActionHandler CAPTURE_HAR: advisory -- attach a BrowserMob Proxy or " +
            "Playwright network interceptor here for Phase 3.");
        return ActionResult.skipped("CAPTURE_HAR advisory -- no HAR capture integration configured");
    }
}

// ── SKIP_TEST ─────────────────────────────────────────────────────────────────

@HandlesAction("SKIP_TEST")
class SkipTestHandler implements ActionHandler {
    @Override
    public ActionResult execute(ActionContext ctx) {
        String reason = ctx.getStep().getParam("reason",
            ctx.getStep().getDescription() != null
                ? ctx.getStep().getDescription()
                : ctx.getInsight().getRootCause());
        if (ctx.isDryRun())
            return ActionResult.skipped("DryRun: would throw SkipException -- reason: " + reason);
        throw new org.testng.SkipException("TestSentinel SKIP_TEST: " + reason);
    }
}

// ── ABORT_SUITE (HIGH risk -- advisory only) ───────────────────────────────────

@HandlesAction("ABORT_SUITE")
class AbortSuiteHandler implements ActionHandler {
    private static final Logger log = LoggerFactory.getLogger(AbortSuiteHandler.class);

    @Override
    public ActionResult execute(ActionContext ctx) {
        // ABORT_SUITE is HIGH risk. ActionPlanExecutor will only reach here if
        // maxRiskLevel == HIGH. We log strongly and return rather than actually
        // killing the JVM -- callers can promote this to a hard abort if desired.
        String reason = ctx.getStep().getParam("reason",
            "TestSentinel recommended aborting the suite");
        log.error("ActionHandler ABORT_SUITE: {} -- rootCause: {}",
            reason, ctx.getInsight().getRootCause());
        if (ctx.isDryRun())
            return ActionResult.skipped("DryRun: would abort suite -- reason: " + reason);
        return ActionResult.executed("ABORT_SUITE advisory logged -- escalate to suite controller if needed");
    }
}

// ── CUSTOM ────────────────────────────────────────────────────────────────────

@HandlesAction("CUSTOM")
class CustomHandler implements ActionHandler {
    private static final Logger log = LoggerFactory.getLogger(CustomHandler.class);

    @Override
    public ActionResult execute(ActionContext ctx) {
        // Replace this implementation with your project-specific logic.
        // Parameters from the ActionStep are available via ctx.getStep().getParam(...).
        log.warn("ActionHandler CUSTOM: no custom implementation registered. " +
            "Override CustomHandler with your project-specific logic.");
        return ActionResult.skipped("CUSTOM handler not implemented -- override CustomHandler");
    }
}
