package com.testsentinel.executor.checker.checks;

import com.testsentinel.executor.checker.CheckerResult;
import com.testsentinel.executor.checker.ConditionChecker;
import com.testsentinel.executor.checker.ConditionCheckerRegistry;
import com.testsentinel.model.ConditionEvent;
import com.testsentinel.model.ConditionType;
import com.testsentinel.model.InsightResponse;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for individual ConditionChecker implementations.
 *
 * These tests verify checker signal detection using synthetic ConditionEvent objects.
 * No WebDriver, no network, no browser — pure Java.
 *
 * Checkers that rely on a live WebDriver (ElementInDomChecker, OverlayChecker live scan)
 * are tested via DOM snapshot and event-only paths; WebDriver is passed as null, relying
 * on each checker's internal try-catch to handle null gracefully.
 */
public class CheckerUnitTest {

    // Instantiated directly — same package gives access to package-private checkers
    private PageTimeoutChecker     pageTimeoutChecker;
    private StaleElementChecker    staleElementChecker;
    private AssertionFailureChecker assertionFailureChecker;
    private OverlayChecker         overlayChecker;
    private AuthRedirectChecker    authRedirectChecker;
    private WrongPageChecker       wrongPageChecker;
    private ElementInDomChecker    elementInDomChecker;

    @BeforeClass
    public void setUp() {
        pageTimeoutChecker      = new PageTimeoutChecker();
        staleElementChecker     = new StaleElementChecker();
        assertionFailureChecker = new AssertionFailureChecker();
        overlayChecker          = new OverlayChecker();
        authRedirectChecker     = new AuthRedirectChecker();
        wrongPageChecker        = new WrongPageChecker();
        elementInDomChecker     = new ElementInDomChecker();
    }

    // ════════════════════════════════════════════════════════════════════════
    // ConditionCheckerRegistry — discovery smoke test
    // ════════════════════════════════════════════════════════════════════════

    @Test
    public void registry_discoversAllCheckers() {
        ConditionCheckerRegistry registry = new ConditionCheckerRegistry();
        List<ConditionChecker> checkers = registry.getCheckers();

        assertThat(checkers).isNotEmpty();
        assertThat(registry.size()).isGreaterThanOrEqualTo(6);

        List<String> ids = checkers.stream()
            .map(c -> c.getClass().getAnnotation(
                com.testsentinel.executor.checker.ChecksCondition.class).id())
            .toList();

        assertThat(ids).contains(
            "page-timeout", "stale-element", "assertion-failure",
            "overlay", "auth-redirect", "wrong-page", "element-hidden"
        );
    }

    @Test
    public void registry_checkersAreInPriorityOrder() {
        ConditionCheckerRegistry registry = new ConditionCheckerRegistry();
        List<ConditionChecker> checkers = registry.getCheckers();

        for (int i = 0; i < checkers.size() - 1; i++) {
            int p1 = checkers.get(i).getClass()
                .getAnnotation(com.testsentinel.executor.checker.ChecksCondition.class).priority();
            int p2 = checkers.get(i + 1).getClass()
                .getAnnotation(com.testsentinel.executor.checker.ChecksCondition.class).priority();
            assertThat(p1).isLessThanOrEqualTo(p2);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // PageTimeoutChecker
    // ════════════════════════════════════════════════════════════════════════

    @Test
    public void pageTimeout_matchesOnTimeoutMessage() {
        ConditionEvent event = ConditionEvent.builder()
            .conditionType(ConditionType.LOCATOR_NOT_FOUND)
            .message("Timed out waiting for page to load")
            .currentUrl("https://example.com")
            .build();

        CheckerResult result = pageTimeoutChecker.check(null, event);

        assertThat(result.isMatched()).isTrue();
        assertThat(result.getCheckerId()).isEqualTo("page-timeout");
        assertThat(result.getCategory()).isEqualTo(InsightResponse.ConditionCategory.INFRA);
        assertThat(result.getConfidence()).isGreaterThan(0.8);
        assertThat(result.getActionPlan()).isNotNull();
    }

    @Test
    public void pageTimeout_matchesOnStackTrace() {
        ConditionEvent event = ConditionEvent.builder()
            .conditionType(ConditionType.TIMEOUT)
            .message("Element not found")
            .stackTrace("org.openqa.selenium.TimeoutException: Expected condition failed")
            .currentUrl("https://slow-app.example.com")
            .build();

        CheckerResult result = pageTimeoutChecker.check(null, event);

        assertThat(result.isMatched()).isTrue();
        assertThat(result.getActionPlan().getActions()).hasSizeGreaterThan(0);
    }

    @Test
    public void pageTimeout_noMatchForUnrelatedError() {
        ConditionEvent event = ConditionEvent.builder()
            .conditionType(ConditionType.LOCATOR_NOT_FOUND)
            .message("No such element: #submit-btn")
            .currentUrl("https://example.com/checkout")
            .build();

        CheckerResult result = pageTimeoutChecker.check(null, event);

        assertThat(result.isNoMatch()).isTrue();
    }

    // ════════════════════════════════════════════════════════════════════════
    // StaleElementChecker
    // ════════════════════════════════════════════════════════════════════════

    @Test
    public void staleElement_matchesOnExceptionMessage() {
        ConditionEvent event = ConditionEvent.builder()
            .conditionType(ConditionType.LOCATOR_NOT_FOUND)
            .message("stale element reference: element is not attached to the page document")
            .currentUrl("https://spa.example.com/dashboard")
            .build();

        CheckerResult result = staleElementChecker.check(null, event);

        assertThat(result.isMatched()).isTrue();
        assertThat(result.getCheckerId()).isEqualTo("stale-element");
        assertThat(result.getCategory()).isEqualTo(InsightResponse.ConditionCategory.STALE_DOM);
        assertThat(result.getActionPlan()).isNotNull();
        assertThat(result.getActionPlan().getActions()).hasSizeGreaterThan(0);
    }

    @Test
    public void staleElement_matchesOnStackTrace() {
        ConditionEvent event = ConditionEvent.builder()
            .conditionType(ConditionType.LOCATOR_NOT_FOUND)
            .message("Cannot click element")
            .stackTrace("org.openqa.selenium.StaleElementReferenceException: stale element")
            .currentUrl("https://react-app.example.com")
            .build();

        CheckerResult result = staleElementChecker.check(null, event);

        assertThat(result.isMatched()).isTrue();
    }

    @Test
    public void staleElement_noMatchForTimeoutError() {
        ConditionEvent event = ConditionEvent.builder()
            .conditionType(ConditionType.LOCATOR_NOT_FOUND)
            .message("Timed out waiting for element")
            .currentUrl("https://example.com")
            .build();

        CheckerResult result = staleElementChecker.check(null, event);

        assertThat(result.isNoMatch()).isTrue();
    }

    @Test
    public void staleElement_actionPlanContainsLowRiskSteps() {
        ConditionEvent event = ConditionEvent.builder()
            .conditionType(ConditionType.LOCATOR_NOT_FOUND)
            .message("stale element reference")
            .currentUrl("https://example.com")
            .build();

        CheckerResult result = staleElementChecker.check(null, event);

        assertThat(result.isMatched()).isTrue();
        boolean allLow = result.getActionPlan().getActions().stream()
            .allMatch(s -> s.getRiskLevel() == com.testsentinel.model.ActionStep.RiskLevel.LOW);
        assertThat(allLow)
            .as("All stale-element remediation steps should be LOW risk")
            .isTrue();
    }

    // ════════════════════════════════════════════════════════════════════════
    // AssertionFailureChecker
    // ════════════════════════════════════════════════════════════════════════

    @Test
    public void assertionFailure_matchesOnAssertionErrorClass() {
        ConditionEvent event = ConditionEvent.builder()
            .conditionType(ConditionType.ASSERTION_FAILURE)
            .message("AssertionError: expected 'Welcome' but was 'Error'")
            .currentUrl("https://app.example.com")
            .build();

        CheckerResult result = assertionFailureChecker.check(null, event);

        assertThat(result.isMatched()).isTrue();
        assertThat(result.getCheckerId()).isEqualTo("assertion-failure");
        assertThat(result.getCategory()).isEqualTo(InsightResponse.ConditionCategory.APPLICATION_BUG);
        assertThat(result.getConfidence()).isGreaterThan(0.8);
    }

    @Test
    public void assertionFailure_matchesOnAssertJMessage() {
        ConditionEvent event = ConditionEvent.builder()
            .conditionType(ConditionType.ASSERTION_FAILURE)
            .message("Expecting actual:\n  \"Error Page\"\nto be equal to:\n  \"Dashboard\"")
            .currentUrl("https://example.com")
            .build();

        CheckerResult result = assertionFailureChecker.check(null, event);

        assertThat(result.isMatched()).isTrue();
    }

    @Test
    public void assertionFailure_matchesOnStackTrace() {
        ConditionEvent event = ConditionEvent.builder()
            .conditionType(ConditionType.ASSERTION_FAILURE)
            .message("Test step failed")
            .stackTrace("org.assertj.core.api.AssertionError: expected condition not met\n" +
                        "\tat org.assertj.core.api.AbstractAssert.isTrue(AbstractAssert.java:86)")
            .currentUrl("https://example.com")
            .build();

        CheckerResult result = assertionFailureChecker.check(null, event);

        assertThat(result.isMatched()).isTrue();
    }

    @Test
    public void assertionFailure_noMatchForConnectionError() {
        ConditionEvent event = ConditionEvent.builder()
            .conditionType(ConditionType.LOCATOR_NOT_FOUND)
            .message("Connection refused to remote server")
            .currentUrl("https://example.com")
            .build();

        CheckerResult result = assertionFailureChecker.check(null, event);

        assertThat(result.isNoMatch()).isTrue();
    }

    @Test
    public void assertionFailure_noActionPlan() {
        ConditionEvent event = ConditionEvent.builder()
            .conditionType(ConditionType.ASSERTION_FAILURE)
            .message("AssertionError: expected [true] but found [false]")
            .currentUrl("https://example.com")
            .build();

        CheckerResult result = assertionFailureChecker.check(null, event);

        // Assertion failures have no auto-executable action plan — human review needed
        assertThat(result.isMatched()).isTrue();
        assertThat(result.getActionPlan()).isNull();
        assertThat(result.getSuggestedOutcome()).isEqualTo("FAIL_WITH_CONTEXT");
    }

    // ════════════════════════════════════════════════════════════════════════
    // AuthRedirectChecker
    // ════════════════════════════════════════════════════════════════════════

    @Test
    public void authRedirect_matchesOnLoginUrl() {
        ConditionEvent event = ConditionEvent.builder()
            .conditionType(ConditionType.LOCATOR_NOT_FOUND)
            .message("Element #dashboard-widget not found")
            .currentUrl("https://app.example.com/login?returnUrl=%2Fdashboard")
            .build();

        CheckerResult result = authRedirectChecker.check(null, event);

        assertThat(result.isMatched()).isTrue();
        assertThat(result.getCheckerId()).isEqualTo("auth-redirect");
        assertThat(result.getCategory()).isEqualTo(InsightResponse.ConditionCategory.AUTH);
    }

    @Test
    public void authRedirect_matchesOnSessionExpiredUrl() {
        ConditionEvent event = ConditionEvent.builder()
            .conditionType(ConditionType.LOCATOR_NOT_FOUND)
            .message("Element not found")
            .currentUrl("https://example.com/session-expired")
            .build();

        CheckerResult result = authRedirectChecker.check(null, event);

        assertThat(result.isMatched()).isTrue();
    }

    @Test
    public void authRedirect_noMatchForNormalPage() {
        ConditionEvent event = ConditionEvent.builder()
            .conditionType(ConditionType.LOCATOR_NOT_FOUND)
            .message("Element #checkout-btn not found")
            .currentUrl("https://shop.example.com/cart")
            .build();

        CheckerResult result = authRedirectChecker.check(null, event);

        assertThat(result.isNoMatch()).isTrue();
    }

    // ════════════════════════════════════════════════════════════════════════
    // WrongPageChecker
    // ════════════════════════════════════════════════════════════════════════

    @Test
    public void wrongPage_matchesWhenUrlsMismatch() {
        ConditionEvent event = ConditionEvent.builder()
            .conditionType(ConditionType.WRONG_PAGE)
            .message("Expected /checkout but on /login")
            .currentUrl("https://example.com/login")
            .expectedUrl("https://example.com/checkout")
            .build();

        CheckerResult result = wrongPageChecker.check(null, event);

        assertThat(result.isMatched()).isTrue();
        assertThat(result.getCheckerId()).isEqualTo("wrong-page");
        assertThat(result.getCategory()).isEqualTo(InsightResponse.ConditionCategory.NAVIGATION);
    }

    @Test
    public void wrongPage_noMatchWhenUrlsMatch() {
        ConditionEvent event = ConditionEvent.builder()
            .conditionType(ConditionType.WRONG_PAGE)
            .message("URL check")
            .currentUrl("https://example.com/checkout")
            .expectedUrl("https://example.com/checkout")
            .build();

        CheckerResult result = wrongPageChecker.check(null, event);

        assertThat(result.isNoMatch()).isTrue();
    }

    @Test
    public void wrongPage_noMatchWhenExpectedUrlMissing() {
        ConditionEvent event = ConditionEvent.builder()
            .conditionType(ConditionType.WRONG_PAGE)
            .message("URL check")
            .currentUrl("https://example.com/login")
            .build();

        CheckerResult result = wrongPageChecker.check(null, event);

        assertThat(result.isNoMatch()).isTrue();
    }

    // ════════════════════════════════════════════════════════════════════════
    // OverlayChecker — DOM snapshot path (no WebDriver needed)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    public void overlay_matchesOnDomSnapshot() {
        ConditionEvent event = ConditionEvent.builder()
            .conditionType(ConditionType.LOCATOR_NOT_FOUND)
            .message("Element #submit not found")
            .currentUrl("https://shop.example.com/checkout")
            .domSnapshot("<html><body class='modal-open'>" +
                         "<div class='modal show'>Cookie notice</div>" +
                         "</body></html>")
            .build();

        // null driver → findActiveOverlay returns null → fall through to DOM snapshot check
        CheckerResult result = overlayChecker.check(null, event);

        assertThat(result.isMatched()).isTrue();
        assertThat(result.getCheckerId()).isEqualTo("overlay");
        assertThat(result.getCategory()).isEqualTo(InsightResponse.ConditionCategory.OVERLAY);
        assertThat(result.getActionPlan()).isNotNull();
    }

    @Test
    public void overlay_noMatchWithoutDomSignals() {
        ConditionEvent event = ConditionEvent.builder()
            .conditionType(ConditionType.LOCATOR_NOT_FOUND)
            .message("Element #submit not found")
            .currentUrl("https://example.com/form")
            .domSnapshot("<html><body><form id='main-form'></form></body></html>")
            .build();

        CheckerResult result = overlayChecker.check(null, event);

        assertThat(result.isNoMatch()).isTrue();
    }

    // ════════════════════════════════════════════════════════════════════════
    // ElementInDomChecker — null driver → noMatch (element query impossible)
    // ════════════════════════════════════════════════════════════════════════

    @Test
    public void elementHidden_noMatchWhenDriverIsNull() {
        ConditionEvent event = ConditionEvent.builder()
            .conditionType(ConditionType.LOCATOR_NOT_FOUND)
            .message("Element #hidden-btn not found")
            .currentUrl("https://example.com")
            .locatorValue("#hidden-btn")
            .build();

        // Without a real driver, element query throws NPE caught inside checker
        CheckerResult result = elementInDomChecker.check(null, event);

        assertThat(result.isNoMatch()).isTrue();
    }

    @Test
    public void elementHidden_noMatchForNonLocatorEvent() {
        ConditionEvent event = ConditionEvent.builder()
            .conditionType(ConditionType.WRONG_PAGE)
            .message("Wrong page")
            .currentUrl("https://example.com/login")
            .expectedUrl("https://example.com/checkout")
            .locatorValue("#some-element")
            .build();

        CheckerResult result = elementInDomChecker.check(null, event);

        // Only fires for LOCATOR_NOT_FOUND
        assertThat(result.isNoMatch()).isTrue();
    }
}
