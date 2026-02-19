package com.example.steps;

import com.example.context.ScenarioContext;
import com.example.pages.GooglePage;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for Feature 01: Google Search Happy Path.
 *
 * These steps exercise the normal browser interaction path.
 * TestSentinel is present and observing but should not be triggered.
 */
public class HappyPathSteps {

    private static final Logger log = LoggerFactory.getLogger(HappyPathSteps.class);

    private final ScenarioContext ctx;

    public HappyPathSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    // ── Preconditions ─────────────────────────────────────────────────────────

    @Given("the search bar is visible")
    public void theSearchBarIsVisible() {
        GooglePage page = new GooglePage(ctx.getDriver());
        assertThat(page.isSearchBarVisible())
            .as("Google search bar should be visible on the homepage")
            .isTrue();
        log.info("HappyPathSteps: Search bar confirmed visible");
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    @When("the user types {string} into the search bar")
    public void theUserTypesIntoTheSearchBar(String searchTerm) {
        GooglePage page = new GooglePage(ctx.getDriver());
        page.typeInSearchBar(searchTerm);
        log.info("HappyPathSteps: Typed '{}' into search bar", searchTerm);
    }

    @And("the user submits the search")
    public void theUserSubmitsTheSearch() {
        GooglePage page = new GooglePage(ctx.getDriver());
        page.submitSearch();
        log.info("HappyPathSteps: Search submitted — title is now '{}'",
            ctx.getDriver().getTitle());
    }

    // ── Assertions ────────────────────────────────────────────────────────────

    @Then("the results page title contains {string}")
    public void theResultsPageTitleContains(String expectedFragment) {
        String title = ctx.getDriver().getTitle();
        assertThat(title)
            .as("Page title should contain the search term '%s'", expectedFragment)
            .containsIgnoringCase(expectedFragment);
        log.info("HappyPathSteps: Title '{}' contains '{}'", title, expectedFragment);
    }

    @And("at least one result is displayed")
    public void atLeastOneResultIsDisplayed() {
        GooglePage page = new GooglePage(ctx.getDriver());
        int count = page.getSearchResultCount();
        assertThat(count)
            .as("At least one search result should be displayed")
            .isGreaterThan(0);
        log.info("HappyPathSteps: {} search results displayed", count);
    }

    @Then("the search bar contains the text {string}")
    public void theSearchBarContainsTheText(String expectedText) {
        GooglePage page = new GooglePage(ctx.getDriver());
        String actual = page.getSearchBarText();
        assertThat(actual)
            .as("Search bar should contain '%s'", expectedText)
            .isEqualTo(expectedText);
        log.info("HappyPathSteps: Search bar text is '{}'", actual);
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
        // Sync listener state first
        ctx.syncInsightFromListener();
        assertThat(ctx.getLastInsight())
            .as("No TestSentinel insight should have been produced on the happy path")
            .isNull();
        log.info("HappyPathSteps: Confirmed — no TestSentinel analysis triggered");
    }
}
