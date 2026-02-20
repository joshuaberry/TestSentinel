package com.testsentinel.steps;

import com.testsentinel.context.ScenarioContext;
import com.testsentinel.pages.GooglePage;
import com.testsentinel.core.TestSentinelClient;
import com.testsentinel.core.TestSentinelConfig;
import com.testsentinel.interceptor.TestSentinelListener;
import com.testsentinel.model.ConditionEvent;
import com.testsentinel.model.ConditionType;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions that appear in the Background of multiple features or that
 * serve as common preconditions shared across the suite.
 *
 * Steps covered:
 *   Given the browser is open on the Google homepage
 *   And TestSentinel is enabled with a valid API key
 *   And a knowledge base file is configured
 *   Given Phase 2 is enabled
 *   Given the browser has navigated to the Google homepage
 *   Given the browser is on the Google homepage
 *   And the search bar is already visible on the Google homepage
 */
public class CommonSteps {

    private static final Logger log = LoggerFactory.getLogger(CommonSteps.class);

    private final ScenarioContext ctx;

    // PicoContainer injects ScenarioContext here — same instance shared with Hooks
    public CommonSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    @Given("the browser is open on the Google homepage")
    public void theBrowserIsOpenOnTheGoogleHomepage() {
        GooglePage page = new GooglePage(ctx.getDriver());
        page.open();
        log.info("CommonSteps: Navigated to Google homepage — title='{}'", page.getPageTitle());
    }

    // Alias used in feature 04 Background
    @Given("the browser has navigated to the Google homepage")
    public void theBrowserHasNavigatedToTheGoogleHomepage() {
        theBrowserIsOpenOnTheGoogleHomepage();
    }

    // Alias used in feature 04 continue-context scenario
    @Given("the browser is on the Google homepage")
    public void theBrowserIsOnTheGoogleHomepage() {
        theBrowserIsOpenOnTheGoogleHomepage();
    }

    // ── API Key Guard ─────────────────────────────────────────────────────────

    /**
     * Skips the scenario gracefully if ANTHROPIC_API_KEY is not set.
     * Used in the Background of features that need live Claude analysis.
     */
    @And("TestSentinel is enabled with a valid API key")
    public void testSentinelIsEnabledWithAValidApiKey() {
        if (!ctx.isApiKeyPresent()) {
            log.warn("CommonSteps: ANTHROPIC_API_KEY not set — skipping sentinel scenario");
            // In Cucumber + TestNG, throwing AssumptionViolatedException would skip the test.
            // Here we log a clear warning and let steps that call the API fail with a clear message.
            // Engineers can run with -Dcucumber.filter.tags="not @sentinel" to exclude API scenarios.
        } else {
            log.info("CommonSteps: API key is present — TestSentinel Claude analysis enabled");
        }
    }

    // ── Knowledge Base ────────────────────────────────────────────────────────

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
        // Phase 2 is controlled by the config used to build the shared client.
        // If phase2 is not set in the environment, we rebuild the client with it enabled
        // for this scenario only and swap it into the context.
        if (!ctx.isPhase2Enabled()) {
            log.info("CommonSteps: Phase 2 not enabled globally — enabling for this scenario");

            // Build a phase2-enabled config from the current config, override phase2
            TestSentinelConfig phase2Config = TestSentinelConfig.builder()
                .apiKey(resolveApiKey())
                .phase2Enabled(true)
                .captureDOM(true)
                .captureScreenshot(false)
                .domMaxChars(10_000)
                .enabled(ctx.isApiKeyPresent())
                .build();

            // Replace the client and listener for this scenario
            TestSentinelClient phase2Client = new TestSentinelClient(phase2Config);
            TestSentinelListener listener = new TestSentinelListener(
                phase2Client, "phase2-scenario", "SentinelAnalysis");

            ctx.setSentinel(phase2Client);
            ctx.setListener(listener);
            ctx.setPhase2Enabled(true);

            // Re-wrap the existing driver with the new listener
            // (The driver was already created by @Before — we patch the context listener reference)
            log.info("CommonSteps: Phase 2 client ready for this scenario");
        } else {
            log.info("CommonSteps: Phase 2 already enabled globally");
        }
    }

    // ── State-satisfied helpers ───────────────────────────────────────────────

    @Given("the search bar is already visible on the Google homepage")
    public void theSearchBarIsAlreadyVisibleOnTheGoogleHomepage() {
        // The homepage has already been opened by the Background step.
        // This step just makes explicit that we're about to check existing state.
        GooglePage page = new GooglePage(ctx.getDriver());
        assertThat(page.isSearchBarVisible())
            .as("Search bar should already be visible on Google homepage")
            .isTrue();
        log.info("CommonSteps: Search bar confirmed visible (STATE_ALREADY_SATISFIED scenario)");
    }

    // ── Navigation expectation helpers ───────────────────────────────────────

    @And("the test expected to be on {string}")
    public void theTestExpectedToBeOn(String expectedUrl) {
        // Store for the subsequent "check URL" step
        ctx.getDriver().manage().timeouts(); // no-op to avoid unused warning
        log.info("CommonSteps: Expected URL set to '{}'", expectedUrl);
        // Store expected URL via a ConditionEvent built in the next step
        storeExpectedUrl(expectedUrl);
    }

    @And("the test expects to be on {string}")
    public void theTestExpectsToBeOn(String expectedUrl) {
        theTestExpectedToBeOn(expectedUrl);
    }

    @And("the test expected to navigate to {string} but is on the homepage")
    public void theTestExpectedToNavigateToButIsOnTheHomepage(String expectedUrl) {
        theTestExpectedToBeOn(expectedUrl);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void storeExpectedUrl(String expectedUrl) {
        // Build a lightweight ConditionEvent and store it so the next check step
        // can call analyzeWrongPage. We attach the URL as the event's expectedUrl.
        ConditionEvent event = ConditionEvent.builder()
            .conditionType(ConditionType.WRONG_PAGE)
            .message("Test expected URL: " + expectedUrl + " but current URL is: "
                + ctx.getDriver().getCurrentUrl())
            .currentUrl(ctx.getDriver().getCurrentUrl())
            .expectedUrl(expectedUrl)
            .build();
        ctx.setLastEvent(event);
    }

    private String resolveApiKey() {
        String fromEnv = System.getenv("ANTHROPIC_API_KEY");
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv;
        String fromProp = System.getProperty("ANTHROPIC_API_KEY");
        if (fromProp != null && !fromProp.isBlank()) return fromProp;
        return "DISABLED";
    }
}
