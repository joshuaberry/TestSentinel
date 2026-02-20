package com.testsentinel.steps;

import com.testsentinel.context.ScenarioContext;
import com.testsentinel.pages.InternetPage;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for Feature 01: Form Authentication Happy Path.
 *
 * Exercises normal login/logout flows against the-internet.herokuapp.com/login.
 * TestSentinel is present and observing but should not be triggered.
 */
public class HappyPathSteps {

    private static final Logger log = LoggerFactory.getLogger(HappyPathSteps.class);

    private final ScenarioContext ctx;

    public HappyPathSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    // ── Preconditions ─────────────────────────────────────────────────────────

    @Given("the login page is loaded")
    public void theLoginPageIsLoaded() {
        InternetPage page = new InternetPage(ctx.getDriver());
        assertThat(page.isLoginPageLoaded())
            .as("Login page username field should be visible")
            .isTrue();
        log.info("HappyPathSteps: Login page confirmed loaded");
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    @When("the user enters username {string} and password {string}")
    public void theUserEntersUsernameAndPassword(String username, String password) {
        InternetPage page = new InternetPage(ctx.getDriver());
        page.enterUsername(username);
        page.enterPassword(password);
        log.info("HappyPathSteps: Entered credentials for '{}'", username);
    }

    @And("the user clicks the login button")
    public void theUserClicksTheLoginButton() {
        InternetPage page = new InternetPage(ctx.getDriver());
        page.clickLoginButton();
        log.info("HappyPathSteps: Login button clicked");
    }

    @When("the user clicks the logout button")
    public void theUserClicksTheLogoutButton() {
        InternetPage page = new InternetPage(ctx.getDriver());
        page.clickLogout();
        log.info("HappyPathSteps: Logout button clicked");
    }

    // ── Assertions ────────────────────────────────────────────────────────────

    @Then("the flash message indicates a successful login")
    public void theFlashMessageIndicatesASuccessfulLogin() {
        InternetPage page = new InternetPage(ctx.getDriver());
        assertThat(page.isFlashMessageSuccess())
            .as("Flash message should confirm successful login")
            .isTrue();
        log.info("HappyPathSteps: Flash message confirmed success — '{}'",
            page.getFlashMessageText().trim());
    }

    @And("the secure area heading is visible")
    public void theSecureAreaHeadingIsVisible() {
        InternetPage page = new InternetPage(ctx.getDriver());
        assertThat(page.isSecurePageLoaded())
            .as("Secure area logout button should be visible after login")
            .isTrue();
        log.info("HappyPathSteps: Secure area loaded — '{}'", page.getSecureHeadingText());
    }

    @Then("the page title is {string}")
    public void thePageTitleIs(String expectedTitle) {
        String actual = ctx.getDriver().getTitle();
        assertThat(actual)
            .as("Page title should be '%s'", expectedTitle)
            .isEqualTo(expectedTitle);
        log.info("HappyPathSteps: Page title is '{}'", actual);
    }

    @And("no TestSentinel analysis was triggered")
    public void noTestSentinelAnalysisWasTriggered() {
        ctx.syncInsightFromListener();
        assertThat(ctx.getLastInsight())
            .as("No TestSentinel insight should have been produced on the happy path")
            .isNull();
        log.info("HappyPathSteps: Confirmed — no TestSentinel analysis triggered");
    }
}
