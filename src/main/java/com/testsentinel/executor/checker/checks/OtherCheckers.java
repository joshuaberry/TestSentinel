package com.testsentinel.executor.checker.checks;

import com.testsentinel.executor.checker.CheckerResult;
import com.testsentinel.executor.checker.ChecksCondition;
import com.testsentinel.executor.checker.ConditionChecker;
import com.testsentinel.model.ActionPlan;
import com.testsentinel.model.ActionStep;
import com.testsentinel.model.ActionType;
import com.testsentinel.model.ConditionEvent;
import com.testsentinel.model.InsightResponse;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import java.util.List;
import java.util.Map;

// ── AuthRedirectChecker ────────────────────────────────────────────────────────

/**
 * Detects session expiry and authentication redirects.
 *
 * Signals:
 *   - Current URL contains /login, /signin, /auth, /sso
 *   - Page title contains "sign in", "log in", "session expired"
 *   - DOM snapshot contains login form keywords
 *
 * Priority 15 — URL check is instant; very definitive signal.
 */
@ChecksCondition(id = "auth-redirect", priority = 15)
class AuthRedirectChecker implements ConditionChecker {

    private static final List<String> AUTH_URL_SEGMENTS = List.of(
        "/login", "/signin", "/sign-in", "/auth", "/sso", "/logout", "/session-expired"
    );
    private static final List<String> AUTH_DOM_SIGNALS = List.of(
        "session has expired", "please log in", "please sign in", "your session"
    );

    @Override
    public CheckerResult check(WebDriver driver, ConditionEvent event) {
        try {
            String url = event.getCurrentUrl() != null ? event.getCurrentUrl().toLowerCase() : "";
            boolean urlMatch = AUTH_URL_SEGMENTS.stream().anyMatch(url::contains);

            boolean domMatch = false;
            if (!urlMatch && event.getDomSnapshot() != null) {
                String dom = event.getDomSnapshot().toLowerCase();
                domMatch = AUTH_DOM_SIGNALS.stream().anyMatch(dom::contains);
            }

            if (!urlMatch && !domMatch) return CheckerResult.noMatch("auth-redirect");

            ActionPlan plan = buildPlan();
            return CheckerResult.matched(
                "auth-redirect",
                InsightResponse.ConditionCategory.AUTH,
                "The test session has expired or been redirected to a login page (URL: " +
                event.getCurrentUrl() + "). Authentication needs to be re-established.",
                0.92,
                plan,
                InsightResponse.SuggestedOutcome.SKIP.name()
            );
        } catch (Exception e) {
            return CheckerResult.noMatch("auth-redirect");
        }
    }

    private ActionPlan buildPlan() {
        ActionStep skip = new ActionStep();
        skip.setActionType(ActionType.SKIP_TEST);
        skip.setParameters(Map.of("reason",
            "Test session expired — authentication redirect detected. " +
            "Re-run after refreshing test credentials."));
        skip.setDescription("Skip this test — session expired, re-auth required");
        skip.setConfidence(0.90);
        skip.setRiskLevel(ActionStep.RiskLevel.MEDIUM);
        skip.setRationale("Re-authenticating mid-test risks corrupting test state. Skip and re-run.");

        ActionPlan plan = new ActionPlan();
        plan.setActions(List.of(skip));
        plan.setPlanSummary("Skip test — session expired, requires re-authentication");
        plan.setPlanConfidence(0.90);
        plan.setRequiresHuman(false);
        return plan;
    }
}

// ── StaleElementChecker ───────────────────────────────────────────────────────

/**
 * Detects StaleElementReferenceException — the element was found but became
 * detached from the DOM (typically due to a React/Angular re-render).
 *
 * Priority 10 — purely exception-message based, zero DOM interaction.
 */
@ChecksCondition(id = "stale-element", priority = 10)
class StaleElementChecker implements ConditionChecker {

    @Override
    public CheckerResult check(WebDriver driver, ConditionEvent event) {
        try {
            String msg   = event.getMessage()    != null ? event.getMessage()    : "";
            String trace = event.getStackTrace() != null ? event.getStackTrace() : "";

            boolean stale = msg.contains("stale element") || msg.contains("StaleElementReference") ||
                            trace.contains("StaleElementReferenceException");
            if (!stale) return CheckerResult.noMatch("stale-element");

            ActionPlan plan = buildPlan();
            return CheckerResult.matched(
                "stale-element",
                InsightResponse.ConditionCategory.STALE_DOM,
                "A stale element reference was detected — the DOM was re-rendered after the element " +
                "was located, causing the reference to become invalid. This is typically caused by a " +
                "React or Angular component re-mounting.",
                0.95,
                plan,
                InsightResponse.SuggestedOutcome.RETRY.name()
            );
        } catch (Exception e) {
            return CheckerResult.noMatch("stale-element");
        }
    }

    private ActionPlan buildPlan() {
        ActionStep wait = new ActionStep();
        wait.setActionType(ActionType.WAIT_FIXED);
        wait.setParameters(Map.of("waitMs", "500"));
        wait.setDescription("Wait 500ms for DOM re-render to stabilise");
        wait.setConfidence(0.85);
        wait.setRiskLevel(ActionStep.RiskLevel.LOW);
        wait.setRationale("A short pause allows the component to finish re-rendering before re-locating.");

        ActionStep retry = new ActionStep();
        retry.setActionType(ActionType.RETRY_ACTION);
        retry.setParameters(Map.of("delayMs", "500", "maxRetries", "3"));
        retry.setDescription("Re-locate the element and retry the original action");
        retry.setConfidence(0.88);
        retry.setRiskLevel(ActionStep.RiskLevel.LOW);
        retry.setRationale("After the DOM stabilises, re-locating the element produces a fresh reference.");

        ActionPlan plan = new ActionPlan();
        plan.setActions(List.of(wait, retry));
        plan.setPlanSummary("Wait for DOM stabilisation then retry action");
        plan.setPlanConfidence(0.88);
        plan.setRequiresHuman(false);
        return plan;
    }
}

// ── WrongPageChecker ──────────────────────────────────────────────────────────

/**
 * Detects wrong-page conditions by comparing the current URL against the
 * expected URL stored in the ConditionEvent.
 *
 * Only fires when both currentUrl and expectedUrl are present on the event.
 * Priority 5 — pure string comparison, zero overhead.
 */
@ChecksCondition(id = "wrong-page", priority = 5)
class WrongPageChecker implements ConditionChecker {

    @Override
    public CheckerResult check(WebDriver driver, ConditionEvent event) {
        try {
            String current  = event.getCurrentUrl();
            String expected = event.getExpectedUrl();
            if (current == null || expected == null) return CheckerResult.noMatch("wrong-page");
            if (current.contains(expected) || expected.contains(current)) {
                return CheckerResult.noMatch("wrong-page");
            }

            ActionPlan plan = buildPlan(expected);
            return CheckerResult.matched(
                "wrong-page",
                InsightResponse.ConditionCategory.NAVIGATION,
                "The test is on the wrong page. Expected URL pattern '" + expected +
                "' but currently at '" + current + "'.",
                0.95,
                plan,
                InsightResponse.SuggestedOutcome.RETRY.name()
            );
        } catch (Exception e) {
            return CheckerResult.noMatch("wrong-page");
        }
    }

    private ActionPlan buildPlan(String expectedUrl) {
        ActionStep navigate = new ActionStep();
        navigate.setActionType(ActionType.NAVIGATE_TO);
        navigate.setParameters(Map.of("url", expectedUrl));
        navigate.setDescription("Navigate directly to the expected URL: " + expectedUrl);
        navigate.setConfidence(0.90);
        navigate.setRiskLevel(ActionStep.RiskLevel.MEDIUM);
        navigate.setRationale("Direct navigation to the expected URL corrects the wrong-page condition.");

        ActionPlan plan = new ActionPlan();
        plan.setActions(List.of(navigate));
        plan.setPlanSummary("Navigate to expected URL");
        plan.setPlanConfidence(0.90);
        plan.setRequiresHuman(false);
        return plan;
    }
}

// ── ElementInDomChecker ───────────────────────────────────────────────────────

/**
 * Detects the case where an element IS present in the DOM but is hidden or
 * off-screen — distinct from element-not-found (which means it's absent entirely).
 *
 * Checks whether the locator value from the ConditionEvent finds elements that
 * are present but not displayed.
 *
 * Priority 30 — requires a live DOM query but only runs for LOCATOR_NOT_FOUND.
 */
@ChecksCondition(id = "element-hidden", priority = 30)
class ElementInDomChecker implements ConditionChecker {

    @Override
    public CheckerResult check(WebDriver driver, ConditionEvent event) {
        try {
            String locator = event.getLocatorValue();
            if (locator == null || locator.isBlank()) return CheckerResult.noMatch("element-hidden");

            // Only relevant for LOCATOR_NOT_FOUND condition type
            if (event.getConditionType() != com.testsentinel.model.ConditionType.LOCATOR_NOT_FOUND) {
                return CheckerResult.noMatch("element-hidden");
            }

            // Check if element exists in DOM but is hidden
            var elements = driver.findElements(By.cssSelector(locator));
            if (elements.isEmpty()) return CheckerResult.noMatch("element-hidden");

            boolean allHidden = elements.stream().noneMatch(el -> {
                try { return el.isDisplayed(); } catch (Exception e) { return false; }
            });

            if (!allHidden) return CheckerResult.noMatch("element-hidden");

            ActionPlan plan = buildPlan(locator);
            return CheckerResult.matched(
                "element-hidden",
                InsightResponse.ConditionCategory.LOADING,
                "Element '" + locator + "' exists in the DOM but is not visible. " +
                "It may be hidden behind another element, have display:none, or be off-screen.",
                0.87,
                plan,
                InsightResponse.SuggestedOutcome.RETRY.name()
            );
        } catch (Exception e) {
            return CheckerResult.noMatch("element-hidden");
        }
    }

    private ActionPlan buildPlan(String locator) {
        ActionStep scroll = new ActionStep();
        scroll.setActionType(ActionType.SCROLL_TO_ELEMENT);
        scroll.setParameters(Map.of("selector", locator));
        scroll.setDescription("Scroll the hidden element into the viewport");
        scroll.setConfidence(0.75);
        scroll.setRiskLevel(ActionStep.RiskLevel.LOW);
        scroll.setRationale("Scrolling into view may trigger visibility and make the element interactable.");

        ActionStep wait = new ActionStep();
        wait.setActionType(ActionType.WAIT_FOR_ELEMENT);
        wait.setParameters(Map.of("selector", locator, "condition", "visible", "timeoutMs", "5000"));
        wait.setDescription("Wait for element to become visible after scroll");
        wait.setConfidence(0.78);
        wait.setRiskLevel(ActionStep.RiskLevel.LOW);
        wait.setRationale("Confirm visibility before attempting interaction.");

        ActionPlan plan = new ActionPlan();
        plan.setActions(List.of(scroll, wait));
        plan.setPlanSummary("Scroll element into view and wait for visibility");
        plan.setPlanConfidence(0.78);
        plan.setRequiresHuman(false);
        return plan;
    }
}

// ── AssertionFailureChecker ───────────────────────────────────────────────────

/**
 * Detects test assertion failures (AssertionError, AssertJ, JUnit, TestNG assertions).
 *
 * Signals checked (any one is sufficient):
 *   - Exception message or stack trace contains "AssertionError"
 *   - Stack trace contains common assertion libraries (assertj, junit, testng)
 *   - Message contains typical assertion failure phrases
 *
 * Priority 25 — purely text-based, no DOM or network interaction.
 */
@ChecksCondition(id = "assertion-failure", priority = 25)
class AssertionFailureChecker implements ConditionChecker {

    private static final List<String> ASSERTION_CLASS_SIGNALS = List.of(
        "AssertionError", "AssertionFailedError", "ComparisonFailure",
        "org.assertj", "org.junit.Assert", "org.testng.Assert"
    );

    private static final List<String> ASSERTION_MESSAGE_SIGNALS = List.of(
        "expected:", "but was:", "expected [", "to be equal to", "to contain",
        "Expecting", "Expected condition", "assertion failed"
    );

    @Override
    public CheckerResult check(WebDriver driver, ConditionEvent event) {
        try {
            String message    = event.getMessage()    != null ? event.getMessage()    : "";
            String stackTrace = event.getStackTrace() != null ? event.getStackTrace() : "";

            boolean classSignal   = ASSERTION_CLASS_SIGNALS.stream()
                .anyMatch(s -> message.contains(s) || stackTrace.contains(s));

            boolean messageSignal = ASSERTION_MESSAGE_SIGNALS.stream()
                .anyMatch(s -> message.toLowerCase().contains(s.toLowerCase()));

            if (!classSignal && !messageSignal) {
                return CheckerResult.noMatch("assertion-failure");
            }

            return CheckerResult.matched(
                "assertion-failure",
                InsightResponse.ConditionCategory.APPLICATION_BUG,
                "A test assertion failed — the application returned a value that did not " +
                "match the expected state. This is typically a genuine test failure (application bug " +
                "or incorrect test data) rather than a test infrastructure problem.",
                0.88,
                null,   // No action plan — assertion failures require human investigation
                InsightResponse.SuggestedOutcome.FAIL_WITH_CONTEXT.name()
            );
        } catch (Exception e) {
            return CheckerResult.noMatch("assertion-failure");
        }
    }
}
