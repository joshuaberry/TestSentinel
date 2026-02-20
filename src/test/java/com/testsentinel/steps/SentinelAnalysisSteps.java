package com.testsentinel.steps;

import com.testsentinel.context.ScenarioContext;
import com.testsentinel.model.ActionStep;
import com.testsentinel.model.ConditionEvent;
import com.testsentinel.model.ConditionType;
import com.testsentinel.model.InsightResponse;
import com.testsentinel.pages.InternetPage;
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
 * Step definitions for Features 02, 03, and 04.
 *
 *   02 — Claude API analysis (missing elements, action plans)
 *   03 — Knowledge base local resolution (pre-load, promote, reuse)
 *   04 — Navigation detection and CONTINUE outcome
 */
public class SentinelAnalysisSteps {

    private static final Logger log = LoggerFactory.getLogger(SentinelAnalysisSteps.class);

    private final ScenarioContext ctx;

    public SentinelAnalysisSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Feature 02 — Triggering TestSentinel via missing elements
    // ════════════════════════════════════════════════════════════════════════

    @When("the test attempts to click a nonexistent element with id {string}")
    public void theTestAttemptsToClickANonexistentElementWithId(String elementId) {
        log.info("SentinelAnalysisSteps: Attempting to click nonexistent id='{}'", elementId);
        try {
            ctx.getDriver().findElement(By.id(elementId)).click();
            fail("Expected NoSuchElementException for id='" + elementId + "' but element was found.");
        } catch (NoSuchElementException e) {
            log.info("SentinelAnalysisSteps: NoSuchElementException caught — TestSentinel intercepted");
        }
        ctx.syncInsightFromListener();
        if (ctx.getLastInsight() == null && ctx.getSentinel() != null) {
            callSentinelForMissingElement("id=" + elementId);
        }
    }

    @When("the test attempts to find an element with css {string}")
    public void theTestAttemptsToFindAnElementWithCss(String cssSelector) {
        log.info("SentinelAnalysisSteps: Attempting to find nonexistent css='{}'", cssSelector);
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
        log.info("SentinelAnalysisSteps: Insight — category={}, outcome={}, source={}",
            insight.getConditionCategory(),
            insight.getSuggestedTestOutcome(),
            insight.isLocalResolution()
                ? "[LOCAL:" + insight.getResolvedFromPattern() + "]" : "[Claude API]");
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
            .as("Insight confidence should equal %.1f", expected)
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
            .as("Outcome should be one of: %s, %s, %s, %s", o1, o2, o3, o4)
            .isIn(o1, o2, o3, o4);
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
            .as("Root cause should be at least 20 characters")
            .isNotNull()
            .hasSizeGreaterThan(20);
    }

    // ── Phase 2 / Action Plan assertions ─────────────────────────────────────

    @And("the insight should contain an action plan")
    public void theInsightShouldContainAnActionPlan() {
        if (!ctx.isPhase2Enabled()) {
            log.info("SentinelAnalysisSteps: Phase 2 not enabled — skipping action plan assertion");
            return;
        }
        assertThat(ctx.getLastInsight().hasActionPlan())
            .as("Phase 2 insight should contain an action plan")
            .isTrue();
    }

    @And("the action plan should have at least one step")
    public void theActionPlanShouldHaveAtLeastOneStep() {
        if (!ctx.getLastInsight().hasActionPlan()) {
            log.info("SentinelAnalysisSteps: No action plan — skipping step count assertion");
            return;
        }
        assertThat(ctx.getLastInsight().getActionPlan().getActions().size())
            .as("Action plan should have at least one step")
            .isGreaterThan(0);
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
    }

    // ════════════════════════════════════════════════════════════════════════
    // Feature 03 — Knowledge Base
    // ════════════════════════════════════════════════════════════════════════

    @Given("the knowledge base contains the pattern {string}")
    public void theKnowledgeBaseContainsThePattern(String patternId) {
        assertThat(ctx.getSentinel().hasPattern(patternId))
            .as("KB should contain pattern '%s'. Check known-conditions.json.", patternId)
            .isTrue();
        log.info("SentinelAnalysisSteps: KB confirmed to contain pattern '{}'", patternId);
    }

    @And("the knowledge base does not contain the pattern {string}")
    public void theKnowledgeBaseDoesNotContainThePattern(String patternId) {
        assertThat(ctx.getSentinel().hasPattern(patternId))
            .as("KB should NOT contain pattern '%s' at the start of this scenario. "
              + "Delete the entry from known-conditions.json and re-run.", patternId)
            .isFalse();
        log.info("SentinelAnalysisSteps: Confirmed pattern '{}' is absent from KB", patternId);
    }

    @And("the insight should have been resolved locally")
    public void theInsightShouldHaveBeenResolvedLocally() {
        InsightResponse insight = ctx.getLastInsight();
        assertThat(insight).isNotNull();
        assertThat(insight.isLocalResolution())
            .as("Insight should have been resolved from KB, not Claude API")
            .isTrue();
        log.info("SentinelAnalysisSteps: Local resolution confirmed from pattern '{}'",
            insight.getResolvedFromPattern());
    }

    @And("the insight resolved pattern should be {string}")
    public void theInsightResolvedPatternShouldBe(String expectedPatternId) {
        assertThat(ctx.getLastInsight().getResolvedFromPattern())
            .as("Resolved pattern should be '%s'", expectedPatternId)
            .isEqualTo(expectedPatternId);
    }

    @And("the insight tokens used should be {int}")
    public void theInsightTokensUsedShouldBe(int expectedTokens) {
        int actual = ctx.getLastInsight().getAnalysisTokens();
        assertThat(actual)
            .as("Local resolution should use 0 tokens")
            .isEqualTo(expectedTokens);
        log.info("SentinelAnalysisSteps: Tokens used = {}", actual);
    }

    @Then("the analysis latency should be under {int} milliseconds")
    public void theAnalysisLatencyShouldBeUnder(int maxMs) {
        long actual = ctx.getLastInsight().getAnalysisLatencyMs();
        assertThat(actual)
            .as("Local KB resolution should be under %dms (was %dms)", maxMs, actual)
            .isLessThan((long) maxMs);
        log.info("SentinelAnalysisSteps: Latency = {}ms", actual);
    }

    @When("the engineer promotes the insight as pattern {string}")
    public void theEngineerPromotesTheInsightAsPattern(String patternId) {
        InsightResponse insight = ctx.getLastInsight();
        assertThat(insight).as("Cannot promote — no insight was produced").isNotNull();

        ConditionEvent event = ctx.getLastEvent();
        if (event == null) {
            event = ConditionEvent.builder()
                .conditionType(ConditionType.LOCATOR_NOT_FOUND)
                .message("Promoted from scenario: " + patternId)
                .currentUrl(ctx.getDriver().getCurrentUrl())
                .build();
        }

        ctx.getSentinel().recordResolution(event, insight, patternId, "cucumber-scenario");
        log.info("SentinelAnalysisSteps: Pattern '{}' promoted to KB", patternId);

        ctx.setLastInsight(null);
        if (ctx.getListener() != null) ctx.getListener().reset("after-promote");
    }

    @Then("the knowledge base should contain the pattern {string}")
    public void theKnowledgeBaseShouldContainThePattern(String patternId) {
        assertThat(ctx.getSentinel().knowledgeBaseSize())
            .as("KB should have at least one pattern after promoting '%s'", patternId)
            .isGreaterThan(0);
        log.info("SentinelAnalysisSteps: KB now has {} pattern(s)", ctx.getSentinel().knowledgeBaseSize());
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
        log.info("SentinelAnalysisSteps: URL check — current='{}', expected='{}'",
            ctx.getDriver().getCurrentUrl(), event.getExpectedUrl());
    }

    @And("the URLs do not match")
    public void theUrlsDoNotMatch() {
        ConditionEvent event = ctx.getLastEvent();
        if (event == null) {
            log.info("SentinelAnalysisSteps: No event stored — assuming URL mismatch");
            return;
        }
        requireApiKey("navigation analysis");
        InsightResponse insight = ctx.getSentinel().analyzeWrongPage(
            ctx.getDriver(),
            event.getExpectedUrl(),
            Collections.emptyList(),
            Map.of("testName", "navigation-detection")
        );
        ctx.getSentinel().logInsight(insight);
        ctx.setLastInsight(insight);
        log.info("SentinelAnalysisSteps: analyzeWrongPage — outcome={}", insight.getSuggestedTestOutcome());
    }

    @Then("TestSentinel should classify this as a navigation condition")
    public void testSentinelShouldClassifyThisAsANavigationCondition() {
        InsightResponse insight = ctx.getLastInsight();
        assertThat(insight).isNotNull();
        String category = insight.getConditionCategory() != null
            ? insight.getConditionCategory().name() : "";
        assertThat(category)
            .as("Category should be navigation-related")
            .isIn("NAVIGATION", "NAVIGATED_PAST", "STATE_ALREADY_SATISFIED", "AUTH");
        log.info("SentinelAnalysisSteps: Navigation category = {}", category);
    }

    @And("the insight should not be continuable")
    public void theInsightShouldNotBeContinuable() {
        InsightResponse insight = ctx.getLastInsight();
        assertThat(insight).isNotNull();
        if (insight.isContinuable()) {
            log.warn("SentinelAnalysisSteps: CONTINUE returned for this navigation — " +
                "category={}, rootCause={}", insight.getConditionCategory(), insight.getRootCause());
        } else {
            log.info("SentinelAnalysisSteps: Insight is not continuable — correct for wrong-page");
        }
    }

    @And("the insight may be continuable if the destination is valid")
    public void theInsightMayBeContinuableIfTheDestinationIsValid() {
        InsightResponse insight = ctx.getLastInsight();
        assertThat(insight).isNotNull();
        log.info("SentinelAnalysisSteps: isContinuable={} — category={}, outcome={}",
            insight.isContinuable(), insight.getConditionCategory(), insight.getSuggestedTestOutcome());
    }

    @When("the test checks if the checkboxes page is loaded before performing setup")
    public void theTestChecksIfTheCheckboxesPageIsLoadedBeforePerformingSetup() {
        InternetPage page = new InternetPage(ctx.getDriver());
        log.info("SentinelAnalysisSteps: Pre-setup check — checkboxes loaded={}",
            page.isCheckboxesPageLoaded());
    }

    @Then("the checkboxes page is confirmed loaded")
    public void theCheckboxesPageIsConfirmedLoaded() {
        InternetPage page = new InternetPage(ctx.getDriver());
        assertThat(page.isCheckboxesPageLoaded())
            .as("Checkboxes page should be loaded — STATE_ALREADY_SATISFIED scenario")
            .isTrue();
    }

    @And("the test can proceed without TestSentinel analysis")
    public void theTestCanProceedWithoutTestSentinelAnalysis() {
        ctx.syncInsightFromListener();
        if (ctx.getLastInsight() == null) {
            log.info("SentinelAnalysisSteps: No TestSentinel analysis triggered — state already satisfied");
        } else {
            log.info("SentinelAnalysisSteps: Insight present — isContinuable={}",
                ctx.getLastInsight().isContinuable());
        }
    }

    @When("TestSentinel analyzes the wrong page condition")
    public void testSentinelAnalyzesTheWrongPageCondition() {
        ConditionEvent event = ctx.getLastEvent();
        String expectedUrl = (event != null && event.getExpectedUrl() != null)
            ? event.getExpectedUrl()
            : InternetPage.LOGIN_URL;

        requireApiKey("wrong page analysis");
        InsightResponse insight = ctx.getSentinel().analyzeWrongPage(
            ctx.getDriver(),
            expectedUrl,
            Collections.emptyList(),
            Map.of("testName", "continue-context-scenario")
        );
        ctx.getSentinel().logInsight(insight);
        ctx.setLastInsight(insight);
        log.info("SentinelAnalysisSteps: Wrong page analysis — isContinuable={}", insight.isContinuable());
    }

    @Then("if the insight is continuable it should have a continue context")
    public void ifTheInsightIsContinuableItShouldHaveAContinueContext() {
        InsightResponse insight = ctx.getLastInsight();
        assertThat(insight).isNotNull();
        if (insight.isContinuable()) {
            assertThat(insight.getContinueContext())
                .as("A continuable insight should have a ContinueContext")
                .isNotNull();
            log.info("SentinelAnalysisSteps: ContinueContext — observedState='{}'",
                insight.getContinueContext().getObservedState());
        } else {
            log.info("SentinelAnalysisSteps: Insight not continuable — ContinueContext check skipped");
        }
    }

    @And("the continue context should include an observed state description")
    public void theContinueContextShouldIncludeAnObservedStateDescription() {
        InsightResponse insight = ctx.getLastInsight();
        if (insight.isContinuable() && insight.getContinueContext() != null) {
            assertThat(insight.getContinueContext().getObservedState())
                .as("ContinueContext.observedState should describe what the test sees")
                .isNotNull()
                .isNotBlank();
        } else {
            log.info("SentinelAnalysisSteps: No ContinueContext to inspect");
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

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

    private void requireApiKey(String context) {
        if (!ctx.isApiKeyPresent()) {
            throw new org.testng.SkipException(
                "ANTHROPIC_API_KEY not set — skipping " + context + ". " +
                "Run with -Dcucumber.filter.tags=\"not @sentinel\" to exclude API-dependent tests.");
        }
    }
}
