package com.testsentinel.steps;

import com.testsentinel.context.ScenarioContext;
import com.testsentinel.core.TestSentinelClient;
import com.testsentinel.model.ConditionEvent;
import com.testsentinel.model.ConditionType;
import com.testsentinel.pages.InternetPage;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions shared across all four features.
 *
 * Covers:
 *   - Opening pages on the-internet.herokuapp.com
 *   - API key guard
 *   - Knowledge base precondition
 *   - Phase 2 enable
 *   - URL expectation storage for navigation scenarios
 *   - Login helper used by navigation scenarios
 */
public class CommonSteps {

    private static final Logger log = LoggerFactory.getLogger(CommonSteps.class);

    private final ScenarioContext ctx;

    public CommonSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    // ── Page navigation ───────────────────────────────────────────────────────

    @Given("the browser is open on the login page")
    public void theBrowserIsOpenOnTheLoginPage() {
        InternetPage page = new InternetPage(ctx.getDriver());
        page.openLogin();
        log.info("CommonSteps: Opened login page — title='{}'", page.getPageTitle());
    }

    @Given("the browser is open on the checkboxes page")
    public void theBrowserIsOpenOnTheCheckboxesPage() {
        InternetPage page = new InternetPage(ctx.getDriver());
        page.openCheckboxes();
        log.info("CommonSteps: Opened checkboxes page");
    }

    // ── Login helper (used by navigation scenarios) ───────────────────────────

    @Given("the user has already logged in with username {string} and password {string}")
    public void theUserHasAlreadyLoggedIn(String username, String password) {
        InternetPage page = new InternetPage(ctx.getDriver());
        page.openLogin();
        page.login(username, password);
        log.info("CommonSteps: Logged in as '{}' — now on '{}'", username, page.getCurrentUrl());
    }

    // ── API key guard ─────────────────────────────────────────────────────────

    @And("TestSentinel is enabled with a valid API key")
    public void testSentinelIsEnabledWithAValidApiKey() {
        if (!ctx.isApiKeyPresent()) {
            // This Background step only appears in features that genuinely require
            // the Claude API (@claude-analysis, @navigation). Skip the scenario
            // cleanly rather than letting it fail deep inside an assertion.
            throw new org.testng.SkipException(
                "ANTHROPIC_API_KEY not set — skipping API-dependent scenario. " +
                "Run with -Dcucumber.filter.tags='not @sentinel' to exclude all API tests.");
        }
        log.info("CommonSteps: API key present — TestSentinel Claude analysis enabled");
    }

    // ── Knowledge base ────────────────────────────────────────────────────────

    @And("a knowledge base file is configured")
    public void aKnowledgeBaseFileIsConfigured() {
        TestSentinelClient sentinel = ctx.getSentinel();
        assertThat(sentinel)
            .as("TestSentinel client must be initialized before KB steps")
            .isNotNull();
        log.info("CommonSteps: KB configured with {} active patterns", sentinel.knowledgeBaseSize());
    }

    // ── Phase 2 ───────────────────────────────────────────────────────────────

    @Given("Phase 2 is enabled")
    public void phase2IsEnabled() {
        // Phase 2 (action plans) is enabled by default via SentinelFactory.buildConfig().
        // This step just confirms / explicitly sets the flag for scenarios that need it.
        ctx.setPhase2Enabled(true);
        log.info("CommonSteps: Phase 2 (action plans) enabled for this scenario");
    }

    // ── Navigation URL expectation helpers ────────────────────────────────────

    @And("the test expects to be on {string}")
    public void theTestExpectsToBeOn(String expectedUrl) {
        storeExpectedUrl(expectedUrl);
        log.info("CommonSteps: Expected URL stored as '{}'", expectedUrl);
    }

    @And("the test expected to be on {string}")
    public void theTestExpectedToBeOn(String expectedUrl) {
        storeExpectedUrl(expectedUrl);
        log.info("CommonSteps: Expected URL stored as '{}'", expectedUrl);
    }

    @And("the test expected to navigate to {string} but is on the secure page")
    public void theTestExpectedToNavigateToButIsOnTheSecurePage(String expectedUrl) {
        storeExpectedUrl(expectedUrl);
        log.info("CommonSteps: Expected URL stored as '{}'", expectedUrl);
    }

    // ── State-satisfied helpers ───────────────────────────────────────────────

    @Given("the checkboxes page is already loaded")
    public void theCheckboxesPageIsAlreadyLoaded() {
        InternetPage page = new InternetPage(ctx.getDriver());
        assertThat(page.isCheckboxesPageLoaded())
            .as("Checkboxes page should already be loaded (STATE_ALREADY_SATISFIED scenario)")
            .isTrue();
        log.info("CommonSteps: Checkboxes page confirmed loaded");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void storeExpectedUrl(String expectedUrl) {
        ConditionEvent event = ConditionEvent.builder()
            .conditionType(ConditionType.WRONG_PAGE)
            .message("Test expected URL: " + expectedUrl
                + " but current URL is: " + ctx.getDriver().getCurrentUrl())
            .currentUrl(ctx.getDriver().getCurrentUrl())
            .expectedUrl(expectedUrl)
            .build();
        ctx.setLastEvent(event);
    }

}
