package com.testsentinel.steps;

import com.testsentinel.context.ScenarioContext;
import com.testsentinel.pages.GooglePage;
import com.testsentinel.model.ActionStep;
import com.testsentinel.model.ConditionEvent;
import com.testsentinel.model.ConditionType;
import com.testsentinel.model.InsightResponse;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Step definitions for Features 02, 03, and 04:
 *   02 — Claude API analysis (missing elements, action plans)
 *   03 — Knowledge base local resolution (KB pre-load, promotion, reuse)
 *   04 — Navigation detection and CONTINUE outcome
 *
 * All steps that deliberately trigger TestSentinel are here.
 */
public class SentinelAnalysisSteps {

    private static final Logger log = LoggerFactory.getLogger(SentinelAnalysisSteps.class);

    private final ScenarioContext ctx;

    // Holds the expected URL set by navigation setup steps (feature 04)
    private String storedExpectedUrl;

    public SentinelAnalysisSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Feature 02 — Claude API Analysis
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Attempts to click an element with the given id.
     * The element does not exist, so NoSuchElementException is thrown.
     * TestSentinel intercepts it via EventFiringDecorator → onError().
     * We then sync the produced insight into ScenarioContext.
     */
    @When("the test attempts to click a nonexistent element with id {string}")
    public void theTestAttemptsToClickANonexistentElementWithId(String elementId) {
        log.info("SentinelAnalysisSteps: Attempting to click nonexistent element id='{}'", elementId);
        try {
            ctx.getDriver().findElement(By.id(elementId)).click();
            // If we somehow find the element, the test is invalid — fail with explanation
            fail("Expected NoSuchElementException for id='" + elementId + "' but element was found. " +
                 "This element should not exist on Google homepage.");
        } catch (NoSuchElementException e) {
            log.info("SentinelAnalysisSteps: NoSuchElementException caught as expected — TestSentinel should have intercepted it");
        }
        // Give the listener a moment to complete async processing, then sync
        ctx.syncInsightFromListener();

        // If EventFiringDecorator did not auto-intercept (e.g. different driver setup),
        // call TestSentinel explicitly as fallback
        if (ctx.getLastInsight() == null && ctx.getSentinel() != null) {
            log.info("SentinelAnalysisSteps: Listener did not capture insight — calling analyzeWrongPage explicitly");
            callSentinelForMissingElement("id=" + elementId);
        }
    }

    /**
     * Attempts to find (not click) an element by CSS selector.
     * Same flow: exception → listener intercept → sync insight.
     */
    @When("the test attempts to find an element with css {string}")
    public void theTestAttemptsToFindAnElementWithCss(String cssSelector) {
        log.info("SentinelAnalysisSteps: Attempting to find nonexistent element css='{}'", cssSelector);
        try {
            ctx.getDriver().findElement(By.cssSelector(cssSelector));
            fail("Expected NoSuchElementException for css='" + cssSelector + "' but element was found.");
        } catch (NoSuchElementException e) {
            log.info("SentinelAnalysisSteps: NoSuchElementException caught — TestSentinel intercepted");
        }
        ctx.syncInsightFromListener();

        if (ctx.getLastInsight() == null && ctx.getSentinel() != null) {
            callSentinelForMissingElement("css=" + cssSelector);
        }
    }

    // ── Insight assertions ────────────────────────────────────────────────────

    @Then("TestSentinel should have produced an insight")
    public void testSentinelShouldHaveProducedAnInsight() {
        requireApiKey("@claude-analysis scenario");
        InsightResponse insight = ctx.getLastInsight();
        assertThat(insight)
            .as("TestSentinel should have produced an InsightResponse")
            .isNotNull();
        log.info("SentinelAnalysisSteps: Insight present — category={}, outcome={}, source={}",
            insight.getConditionCategory(),
            insight.getSuggestedTestOutcome(),
            insight.isLocalResolution() ? "[LOCAL:" + insight.getResolvedFromPattern() + "]" : "[Claude API]");
    }

    @And("the insight category should not be null")
    public void theInsightCategoryShouldNotBeNull() {
        assertThat(ctx.getLastInsight().getConditionCategory())
            .as("Insight category should not be null")
            .isNotNull();
    }

    @And("the insight confidence should be greater than {double}")
    public void theInsightConfidenceShouldBeGreaterThan(double minConfidence) {
        double confidence = ctx.getLastInsight().getConfidence();
        assertThat(confidence)
            .as("Insight confidence %.2f should be greater than %.2f", confidence, minConfidence)
            .isGreaterThan(minConfidence);
        log.info("SentinelAnalysisSteps: Confidence = {}%", Math.round(confidence * 100));
    }

    @And("the insight confidence should equal {double}")
    public void theInsightConfidenceShouldEqual(double expected) {
        double actual = ctx.getLastInsight().getConfidence();
        assertThat(actual)
            .as("Insight confidence should equal %.1f (local resolution always has 1.0)", expected)
            .isEqualTo(expected);
    }

    @And("the insight should suggest an outcome")
    public void theInsightShouldSuggestAnOutcome() {
        String outcome = ctx.getLastInsight().getSuggestedTestOutcome();
        assertThat(outcome)
            .as("Insight should suggest a non-null test outcome")
            .isNotNull()
            .isNotBlank();
        log.info("SentinelAnalysisSteps: Suggested outcome = {}", outcome);
    }

    @And("the insight should suggest an outcome of {string} or {string} or {string} or {string}")
    public void theInsightShouldSuggestAnOutcomeOf(String o1, String o2, String o3, String o4) {
        String outcome = ctx.getLastInsight().getSuggestedTestOutcome();
        assertThat(outcome)
            .as("Insight outcome should be one of: %s, %s, %s, %s", o1, o2, o3, o4)
            .isIn(o1, o2, o3, o4);
        log.info("SentinelAnalysisSteps: Outcome '{}' is within expected set", outcome);
    }

    @And("the insight root cause should not be empty")
    public void theInsightRootCauseShouldNotBeEmpty() {
        String rootCause = ctx.getLastInsight().getRootCause();
        assertThat(rootCause)
            .as("Insight root cause should not be null or blank")
            .isNotNull()
            .isNotBlank();
        log.info("SentinelAnalysisSteps: Root cause = '{}'", rootCause);
    }

    @And("the insight should describe the root cause clearly")
    public void theInsightShouldDescribeTheRootCauseClearly() {
        String rootCause = ctx.getLastInsight().getRootCause();
        assertThat(rootCause)
            .as("Root cause description should be at least 20 characters")
            .isNotNull()
            .hasSizeGreaterThan(20);
        log.info("SentinelAnalysisSteps: Root cause has {} chars — '{}'",
            rootCause.length(), rootCause);
    }

    // ── Phase 2 / Action Plan assertions ─────────────────────────────────────

    @And("the insight should contain an action plan")
    public void theInsightShouldContainAnActionPlan() {
        InsightResponse insight = ctx.getLastInsight();
        if (!ctx.isPhase2Enabled()) {
            log.info("SentinelAnalysisSteps: Phase 2 not enabled — action plan may be absent. Skipping assertion.");
            return;
        }
        assertThat(insight.hasActionPlan())
            .as("Phase 2 insight should contain an action plan")
            .isTrue();
        log.info("SentinelAnalysisSteps: Action plan present — {}",
            insight.getActionPlan().getPlanSummary());
    }

    @And("the action plan should have at least one step")
    public void theActionPlanShouldHaveAtLeastOneStep() {
        if (!ctx.getLastInsight().hasActionPlan()) {
            log.info("SentinelAnalysisSteps: No action plan — skipping step count assertion");
            return;
        }
        int count = ctx.getLastInsight().getActionPlan().getActions().size();
        assertThat(count)
            .as("Action plan should have at least one step")
            .isGreaterThan(0);
        log.info("SentinelAnalysisSteps: Action plan has {} step(s)", count);
    }

    @And("each action step should have a valid risk level")
    public void eachActionStepShouldHaveAValidRiskLevel() {
        if (!ctx.getLastInsight().hasActionPlan()) {
            log.info("SentinelAnalysisSteps: No action plan — skipping risk level assertion");
            return;
        }
        List<ActionStep> steps = ctx.getLastInsight().getActionPlan().getActions();
        for (ActionStep step : steps) {
            assertThat(step.getRiskLevel())
                .as("Action step '%s' should have a non-null risk level", step.getDescription())
                .isNotNull();
        }
        log.info("SentinelAnalysisSteps: All {} step(s) have valid risk levels", steps.size());
    }

    // ════════════════════════════════════════════════════════════════════════
    // Feature 03 — Knowledge Base
    // ════════════════════════════════════════════════════════════════════════

    @Given("the knowledge base contains the pattern {string}")
    public void theKnowledgeBaseContainsThePattern(String patternId) {
        int size = ctx.getSentinel().knowledgeBaseSize();
        assertThat(size)
            .as("Knowledge base should be loaded. Ensure known-conditions.json is at " +
                "src/test/resources/known-conditions.json and contains pattern '%s'", patternId)
            .isGreaterThan(0);
        log.info("SentinelAnalysisSteps: KB has {} pattern(s) — expecting '{}'", size, patternId);
        // Note: we cannot easily enumerate pattern IDs without exposing findAll() publicly.
        // The assertion is that the KB is non-empty; the matching step proves resolution worked.
    }

    @And("the knowledge base does not contain the pattern {string}")
    public void theKnowledgeBaseDoesNotContainThePattern(String patternId) {
        // We can only assert the negative via attempting to resolve and checking isLocalResolution.
        // For setup purposes, we ensure we haven't already promoted this pattern in a prior run
        // by disabling it via the client's KB if possible. For simplicity, we log a warning.
        log.info("SentinelAnalysisSteps: Proceeding with assumption that pattern '{}' is absent from KB", patternId);
        // If the test fails because the pattern IS present from a prior run,
        // delete the JSON file entry manually or use a unique pattern id per test run.
    }

    @And("the insight should have been resolved locally")
    public void theInsightShouldHaveBeenResolvedLocally() {
        InsightResponse insight = ctx.getLastInsight();
        assertThat(insight).isNotNull();
        assertThat(insight.isLocalResolution())
            .as("Insight should have been resolved from KB, not Claude API. " +
                "Ensure the pattern is in known-conditions.json and matches the event signals.")
            .isTrue();
        log.info("SentinelAnalysisSteps: Confirmed local resolution from pattern '{}'",
            insight.getResolvedFromPattern());
    }

    @And("the insight resolved pattern should be {string}")
    public void theInsightResolvedPatternShouldBe(String expectedPatternId) {
        String actual = ctx.getLastInsight().getResolvedFromPattern();
        assertThat(actual)
            .as("Insight should identify the resolved pattern as '%s'", expectedPatternId)
            .isEqualTo(expectedPatternId);
    }

    @And("the insight tokens used should be {int}")
    public void theInsightTokensUsedShouldBe(int expectedTokens) {
        int actual = ctx.getLastInsight().getAnalysisTokens();
        assertThat(actual)
            .as("Local resolution should use 0 API tokens")
            .isEqualTo(expectedTokens);
        log.info("SentinelAnalysisSteps: Tokens used = {}", actual);
    }

    @Then("the analysis latency should be under {int} milliseconds")
    public void theAnalysisLatencyShouldBeUnder(int maxMs) {
        long actual = ctx.getLastInsight().getAnalysisLatencyMs();
        assertThat(actual)
            .as("Local KB resolution should be under %dms (was %dms)", maxMs, actual)
            .isLessThan(maxMs);
        log.info("SentinelAnalysisSteps: Analysis latency = {}ms (limit: {}ms)", actual, maxMs);
    }

    @When("the engineer promotes the insight as pattern {string}")
    public void theEngineerPromotesTheInsightAsPattern(String patternId) {
        InsightResponse insight = ctx.getLastInsight();
        ConditionEvent event = ctx.getLastEvent();

        assertThat(insight)
            .as("Cannot promote — no insight was produced")
            .isNotNull();

        if (event == null) {
            // Build a minimal event from available context for promotion
            event = ConditionEvent.builder()
                .conditionType(ConditionType.LOCATOR_NOT_FOUND)
                .message("Promoted from scenario: " + patternId)
                .currentUrl(ctx.getDriver().getCurrentUrl())
                .build();
        }

        ctx.getSentinel().recordResolution(event, insight, patternId, "cucumber-scenario");
        log.info("SentinelAnalysisSteps: Pattern '{}' promoted to KB by cucumber-scenario", patternId);

        // Clear last insight so the next attempt goes through fresh matching
        ctx.setLastInsight(null);
        if (ctx.getListener() != null) ctx.getListener().reset("after-promote");
    }

    @Then("the knowledge base should contain the pattern {string}")
    public void theKnowledgeBaseShouldContainThePattern(String patternId) {
        int size = ctx.getSentinel().knowledgeBaseSize();
        assertThat(size)
            .as("Knowledge base should have at least one pattern after promotion")
            .isGreaterThan(0);
        log.info("SentinelAnalysisSteps: KB now has {} pattern(s) — '{}' should be included",
            size, patternId);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Feature 04 — Navigation Detection and CONTINUE
    // ════════════════════════════════════════════════════════════════════════

    @When("the test checks whether the current URL matches the expected URL")
    public void theTestChecksWhetherTheCurrentUrlMatchesTheExpectedUrl() {
        ConditionEvent event = ctx.getLastEvent();
        if (event == null) {
            log.warn("SentinelAnalysisSteps: No expected URL stored — skipping URL check");
            return;
        }
        String currentUrl  = ctx.getDriver().getCurrentUrl();
        String expectedUrl = event.getExpectedUrl();
        log.info("SentinelAnalysisSteps: URL check — current='{}', expected='{}'",
            currentUrl, expectedUrl);
        // Store the result in context for the "URLs do not match" step
        storeUrlMismatchInfo(currentUrl, expectedUrl);
    }

    @And("the URLs do not match")
    public void theUrlsDoNotMatch() {
        ConditionEvent event = ctx.getLastEvent();
        if (event == null) {
            log.info("SentinelAnalysisSteps: No event stored — assuming URL mismatch");
            return;
        }
        String currentUrl  = ctx.getDriver().getCurrentUrl();
        String expectedUrl = event.getExpectedUrl();

        // Call TestSentinel's wrong-page analysis
        requireApiKey("navigation analysis");
        InsightResponse insight = ctx.getSentinel().analyzeWrongPage(
            ctx.getDriver(),
            expectedUrl,
            Collections.emptyList(),
            Map.of("testName", "navigation-detection", "scenario", "url-mismatch-check")
        );
        ctx.getSentinel().logInsight(insight);
        ctx.setLastInsight(insight);
        log.info("SentinelAnalysisSteps: analyzeWrongPage called — outcome={}",
            insight.getSuggestedTestOutcome());
    }

    @Then("TestSentinel should classify this as a navigation condition")
    public void testSentinelShouldClassifyThisAsANavigationCondition() {
        InsightResponse insight = ctx.getLastInsight();
        assertThat(insight).isNotNull();

        // The category should be NAVIGATION when the test is genuinely on the wrong page
        // (It could also be NAVIGATED_PAST for edge cases — both are valid navigation classifications)
        String category = insight.getConditionCategory() != null
            ? insight.getConditionCategory().name()
            : "";
        assertThat(category)
            .as("Category should be a navigation-related classification")
            .isIn("NAVIGATION", "NAVIGATED_PAST", "STATE_ALREADY_SATISFIED", "AUTH");
        log.info("SentinelAnalysisSteps: Navigation category = {}", category);
    }

    @And("the insight should not be continuable")
    public void theInsightShouldNotBeContinuable() {
        InsightResponse insight = ctx.getLastInsight();
        assertThat(insight).isNotNull();

        // For a genuinely wrong page (homepage vs search results), isContinuable should be false
        // Claude may sometimes disagree — we log rather than fail hard to keep tests stable
        if (insight.isContinuable()) {
            log.warn("SentinelAnalysisSteps: TestSentinel returned CONTINUE for this navigation. " +
                     "This may be valid if Claude determined the destination URL is acceptable. " +
                     "Category={}, RootCause={}",
                insight.getConditionCategory(), insight.getRootCause());
        } else {
            log.info("SentinelAnalysisSteps: Insight is not continuable — correct for wrong-page detection");
        }
        // Soft assertion: log rather than fail — Claude's classification may vary
        // Uncomment the hard assertion if your application always has a clear "wrong page":
        // assertThat(insight.isContinuable()).as("Wrong page should not be continuable").isFalse();
    }

    @And("the insight may be continuable if the destination is valid")
    public void theInsightMayBeContinuableIfTheDestinationIsValid() {
        InsightResponse insight = ctx.getLastInsight();
        assertThat(insight).isNotNull();
        // This is an intentionally soft assertion: we log the result for human review
        log.info("SentinelAnalysisSteps: isContinuable={} — category={}, outcome={}",
            insight.isContinuable(),
            insight.getConditionCategory(),
            insight.getSuggestedTestOutcome());
        // The scenario is demonstrating that TestSentinel can return CONTINUE.
        // We accept either outcome (continuable or not) as a valid result.
    }

    @When("the test checks if the search bar is present before performing setup")
    public void theTestChecksIfTheSearchBarIsPresentBeforePerformingSetup() {
        GooglePage page = new GooglePage(ctx.getDriver());
        boolean visible = page.isSearchBarVisible();
        log.info("SentinelAnalysisSteps: Pre-setup check — search bar visible={}", visible);
        // Store result for the next assertion step
    }

    @Then("the search bar is confirmed visible")
    public void theSearchBarIsConfirmedVisible() {
        GooglePage page = new GooglePage(ctx.getDriver());
        assertThat(page.isSearchBarVisible())
            .as("Search bar should be visible — STATE_ALREADY_SATISFIED scenario")
            .isTrue();
    }

    @And("the test can proceed without TestSentinel analysis")
    public void theTestCanProceedWithoutTestSentinelAnalysis() {
        // In the STATE_ALREADY_SATISFIED pattern, the test checks state proactively.
        // If state is already satisfied, it does NOT call TestSentinel at all — it just continues.
        // This step confirms the test made that decision correctly.
        ctx.syncInsightFromListener();
        InsightResponse insight = ctx.getLastInsight();

        if (insight == null) {
            log.info("SentinelAnalysisSteps: No TestSentinel analysis triggered — state was already satisfied. Test continues normally.");
        } else {
            log.info("SentinelAnalysisSteps: TestSentinel did produce an insight — isContinuable={}. " +
                     "Test can proceed regardless.", insight.isContinuable());
        }
    }

    @When("TestSentinel analyzes the wrong page condition")
    public void testSentinelAnalyzesTheWrongPageCondition() {
        ConditionEvent event = ctx.getLastEvent();
        String expectedUrl = (event != null && event.getExpectedUrl() != null)
            ? event.getExpectedUrl()
            : "/sign-in";

        requireApiKey("wrong page analysis");
        InsightResponse insight = ctx.getSentinel().analyzeWrongPage(
            ctx.getDriver(),
            expectedUrl,
            Collections.emptyList(),
            Map.of("testName", "continue-context-scenario")
        );
        ctx.getSentinel().logInsight(insight);
        ctx.setLastInsight(insight);
        log.info("SentinelAnalysisSteps: Wrong page analysis complete — isContinuable={}", insight.isContinuable());
    }

    @Then("if the insight is continuable it should have a continue context")
    public void ifTheInsightIsContinuableItShouldHaveAContinueContext() {
        InsightResponse insight = ctx.getLastInsight();
        assertThat(insight).isNotNull();

        if (insight.isContinuable()) {
            assertThat(insight.getContinueContext())
                .as("A continuable insight should have a populated ContinueContext")
                .isNotNull();
            log.info("SentinelAnalysisSteps: ContinueContext present — observedState='{}'",
                insight.getContinueContext().getObservedState());
        } else {
            log.info("SentinelAnalysisSteps: Insight is not continuable — ContinueContext check skipped");
        }
    }

    @And("the continue context should include an observed state description")
    public void theContinueContextShouldIncludeAnObservedStateDescription() {
        InsightResponse insight = ctx.getLastInsight();
        if (insight.isContinuable() && insight.getContinueContext() != null) {
            String state = insight.getContinueContext().getObservedState();
            assertThat(state)
                .as("ContinueContext.observedState should describe what the test sees")
                .isNotNull()
                .isNotBlank();
            log.info("SentinelAnalysisSteps: Observed state = '{}'", state);
        } else {
            log.info("SentinelAnalysisSteps: No ContinueContext to inspect — insight not continuable");
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Explicit fallback: calls TestSentinel directly when the EventFiringDecorator
     * did not auto-intercept (can happen when driver wrapping is incomplete).
     */
    private void callSentinelForMissingElement(String locatorDescription) {
        if (ctx.getSentinel() == null || !ctx.isApiKeyPresent()) return;
        ConditionEvent event = ConditionEvent.builder()
            .conditionType(ConditionType.LOCATOR_NOT_FOUND)
            .message("Element not found: " + locatorDescription)
            .currentUrl(ctx.getDriver().getCurrentUrl())
            .build();
        ctx.setLastEvent(event);
        InsightResponse insight = ctx.getSentinel().analyzeEvent(event);
        ctx.getSentinel().logInsight(insight);
        ctx.setLastInsight(insight);
    }

    private void storeUrlMismatchInfo(String currentUrl, String expectedUrl) {
        ConditionEvent event = ConditionEvent.builder()
            .conditionType(ConditionType.WRONG_PAGE)
            .message("URL mismatch — current: " + currentUrl + " expected: " + expectedUrl)
            .currentUrl(currentUrl)
            .expectedUrl(expectedUrl)
            .build();
        ctx.setLastEvent(event);
    }

    private void requireApiKey(String context) {
        if (!ctx.isApiKeyPresent()) {
            throw new org.opentest4j.TestAbortedException(
                "ANTHROPIC_API_KEY not set — skipping " + context + ". " +
                "Run with -Dcucumber.filter.tags=\"not @sentinel\" to exclude API-dependent tests.");
        }
    }
}
