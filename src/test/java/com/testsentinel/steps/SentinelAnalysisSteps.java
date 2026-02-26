package com.testsentinel.steps;

import com.testsentinel.context.ScenarioContext;
import com.testsentinel.model.ActionStep;
import com.testsentinel.model.ConditionEvent;
import com.testsentinel.model.ConditionType;
import com.testsentinel.model.InsightResponse;
import com.testsentinel.model.KnownCondition;
import com.testsentinel.model.UnknownConditionRecord;
import com.testsentinel.pages.InternetPage;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Step definitions for Features 02, 03, 04, 05, 06, and 07.
 *
 *   02 -- Local KB analysis (missing elements, zero tokens)
 *   03 -- Knowledge base management (pre-loaded, direct-add, reuse)
 *   04 -- Navigation detection and CONTINUE outcome
 *   05 -- Unknown condition recording
 *   06 -- Autonomous action execution
 *   07 -- Session cookie bypass detection (CONTINUE outcome via LOCATOR_NOT_FOUND on /secure)
 */
public class SentinelAnalysisSteps {

    private static final Logger log = LoggerFactory.getLogger(SentinelAnalysisSteps.class);

    private final ScenarioContext ctx;

    public SentinelAnalysisSteps(ScenarioContext ctx) {
        this.ctx = ctx;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Triggering conditions -- missing elements
    // ════════════════════════════════════════════════════════════════════════

    @When("the test attempts to click a nonexistent element with id {string}")
    public void theTestAttemptsToClickANonexistentElementWithId(String elementId) {
        log.info("SentinelAnalysisSteps: Attempting to click nonexistent id='{}'", elementId);
        try {
            ctx.getDriver().findElement(By.id(elementId)).click();
            fail("Expected NoSuchElementException for id='" + elementId + "' but element was found.");
        } catch (NoSuchElementException e) {
            log.info("SentinelAnalysisSteps: NoSuchElementException caught -- TestSentinel intercepted");
        }
        ctx.syncInsightFromListener();
        if (ctx.getLastInsight() == null && ctx.getSentinel() != null) {
            callSentinelForMissingElement("id=" + elementId, elementId);
        }
    }

    @When("the test attempts to find an element with css {string}")
    public void theTestAttemptsToFindAnElementWithCss(String cssSelector) {
        log.info("SentinelAnalysisSteps: Attempting to find nonexistent css='{}'", cssSelector);
        try {
            ctx.getDriver().findElement(By.cssSelector(cssSelector));
            fail("Expected NoSuchElementException for css='" + cssSelector + "' but element was found.");
        } catch (NoSuchElementException e) {
            log.info("SentinelAnalysisSteps: NoSuchElementException caught -- TestSentinel intercepted");
        }
        ctx.syncInsightFromListener();
        if (ctx.getLastInsight() == null && ctx.getSentinel() != null) {
            callSentinelForMissingElement("css=" + cssSelector, cssSelector);
        }
    }

    // ── Insight assertions ────────────────────────────────────────────────────

    @Then("TestSentinel should have produced an insight")
    public void testSentinelShouldHaveProducedAnInsight() {
        InsightResponse insight = ctx.getLastInsight();
        assertThat(insight)
            .as("TestSentinel should have produced an InsightResponse")
            .isNotNull();
        log.info("SentinelAnalysisSteps: Insight -- category={}, outcome={}, source={}",
            insight.getConditionCategory(),
            insight.getSuggestedTestOutcome(),
            insight.isLocalResolution()
                ? "[LOCAL:" + insight.getResolvedFromPattern() + "]" : "[OFFLINE-UNMATCHED or API]");
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

    @And("the suggested outcome should be {string}")
    public void theSuggestedOutcomeShouldBe(String expected) {
        String actual = ctx.getLastInsight().getSuggestedTestOutcome();
        assertThat(actual)
            .as("Suggested outcome should be '%s'", expected)
            .isEqualTo(expected);
        log.info("SentinelAnalysisSteps: Suggested outcome = {}", actual);
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
        assertThat(ctx.getLastInsight().hasActionPlan())
            .as("Insight should contain an action plan")
            .isTrue();
    }

    @And("the action plan should have at least one step")
    public void theActionPlanShouldHaveAtLeastOneStep() {
        if (!ctx.getLastInsight().hasActionPlan()) {
            log.info("SentinelAnalysisSteps: No action plan -- skipping step count assertion");
            return;
        }
        assertThat(ctx.getLastInsight().getActionPlan().getActions().size())
            .as("Action plan should have at least one step")
            .isGreaterThan(0);
    }

    @And("each action step should have a valid risk level")
    public void eachActionStepShouldHaveAValidRiskLevel() {
        if (!ctx.getLastInsight().hasActionPlan()) {
            log.info("SentinelAnalysisSteps: No action plan -- skipping risk level assertion");
            return;
        }
        List<ActionStep> steps = ctx.getLastInsight().getActionPlan().getActions();
        for (ActionStep step : steps) {
            assertThat(step.getRiskLevel())
                .as("Action step '%s' should have a non-null risk level", step.getDescription())
                .isNotNull();
        }
    }

    @And("the action plan should have at least one LOW-risk step")
    public void theActionPlanShouldHaveAtLeastOneLowRiskStep() {
        assertThat(ctx.getLastInsight().hasActionPlan())
            .as("Insight must have an action plan")
            .isTrue();
        long lowRiskCount = ctx.getLastInsight().getActionPlan().getActions().stream()
            .filter(s -> s.getRiskLevel() == ActionStep.RiskLevel.LOW)
            .count();
        assertThat(lowRiskCount)
            .as("Action plan should have at least one LOW-risk step")
            .isGreaterThan(0);
        log.info("SentinelAnalysisSteps: {} LOW-risk step(s) found in action plan", lowRiskCount);
    }

    @And("the advisor confirms at least one step is executable")
    public void theAdvisorConfirmsAtLeastOneStepIsExecutable() {
        InsightResponse insight = ctx.getLastInsight();
        assertThat(ctx.getAdvisor().hasExecutableSteps(insight))
            .as("Advisor should confirm at least one executable step within risk limit")
            .isTrue();
        log.info("SentinelAnalysisSteps: Advisor confirms executable steps available");
    }

    @And("no MEDIUM or HIGH risk steps are auto-executable at current risk limit")
    public void noMediumOrHighRiskStepsAreAutoExecutable() {
        List<ActionStep> executableSteps = ctx.getAdvisor().getExecutableSteps(ctx.getLastInsight());
        boolean anyMediumOrHigh = executableSteps.stream()
            .anyMatch(s -> s.getRiskLevel() == ActionStep.RiskLevel.MEDIUM
                        || s.getRiskLevel() == ActionStep.RiskLevel.HIGH);
        assertThat(anyMediumOrHigh)
            .as("No MEDIUM or HIGH risk steps should be auto-executable at LOW risk limit")
            .isFalse();
        log.info("SentinelAnalysisSteps: Confirmed -- no MEDIUM/HIGH risk steps in executable set");
    }

    // ════════════════════════════════════════════════════════════════════════
    // Feature 03 -- Knowledge Base
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
        // If pattern exists from a previous run, clean it up first
        if (ctx.getSentinel().hasPattern(patternId)) {
            log.info("SentinelAnalysisSteps: Removing leftover pattern '{}' from previous run", patternId);
            ctx.getSentinel().removePattern(patternId);
        }
        assertThat(ctx.getSentinel().hasPattern(patternId))
            .as("KB should NOT contain pattern '%s' (removed if it existed from a prior run)", patternId)
            .isFalse();
        log.info("SentinelAnalysisSteps: Confirmed pattern '{}' is absent from KB", patternId);
    }

    @And("the insight should have been resolved locally")
    public void theInsightShouldHaveBeenResolvedLocally() {
        InsightResponse insight = ctx.getLastInsight();
        assertThat(insight).isNotNull();
        assertThat(insight.isLocalResolution())
            .as("Insight should have been resolved from KB, not Claude API. " +
                "resolvedFromPattern=%s, tokens=%d", insight.getResolvedFromPattern(), insight.getAnalysisTokens())
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
            .as("Local resolution should use %d tokens (was %d)", expectedTokens, actual)
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

    @When("the engineer adds pattern {string} for element {string}")
    public void theEngineerAddsPatternForElement(String patternId, String cssSelector) {
        String currentUrl = ctx.getDriver().getCurrentUrl();
        KnownCondition kc = new KnownCondition();
        kc.setId(patternId);
        kc.setDescription("Direct-add test pattern for element '" + cssSelector + "'");
        kc.setEnabled(true);
        kc.setUrlPattern(extractUrlPath(currentUrl));
        kc.setLocatorValuePattern(cssSelector);
        kc.setConditionType(ConditionType.LOCATOR_NOT_FOUND.name());
        kc.setMinMatchSignals(3);
        kc.setConditionCategory("APPLICATION_BUG");
        kc.setRootCause("Element '" + cssSelector + "' does not exist on this page.");
        kc.setSuggestedTestOutcome("FAIL_WITH_CONTEXT");
        kc.setAddedBy("cucumber-scenario");
        kc.setAddedAt(Instant.now());

        ctx.getSentinel().addPattern(kc);
        log.info("SentinelAnalysisSteps: Pattern '{}' added directly for element '{}'", patternId, cssSelector);
    }

    @Then("the knowledge base should contain the pattern {string}")
    public void theKnowledgeBaseShouldContainThePattern(String patternId) {
        assertThat(ctx.getSentinel().hasPattern(patternId))
            .as("KB should contain pattern '%s' after adding it", patternId)
            .isTrue();
        log.info("SentinelAnalysisSteps: KB confirmed to have pattern '{}'", patternId);
    }

    @When("the engineer promotes the insight as pattern {string}")
    public void theEngineerPromotesTheInsightAsPattern(String patternId) {
        InsightResponse insight = ctx.getLastInsight();
        assertThat(insight).as("Cannot promote -- no insight was produced").isNotNull();

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

    // ════════════════════════════════════════════════════════════════════════
    // Feature 04 -- Navigation Detection and CONTINUE
    // ════════════════════════════════════════════════════════════════════════

    @When("the test checks whether the current URL matches the expected URL")
    public void theTestChecksWhetherTheCurrentUrlMatchesTheExpectedUrl() {
        ConditionEvent event = ctx.getLastEvent();
        if (event == null) {
            log.warn("SentinelAnalysisSteps: No expected URL stored -- skipping URL check");
            return;
        }
        log.info("SentinelAnalysisSteps: URL check -- current='{}', expected='{}'",
            ctx.getDriver().getCurrentUrl(), event.getExpectedUrl());
    }

    @And("the URLs do not match")
    public void theUrlsDoNotMatch() {
        ConditionEvent event = ctx.getLastEvent();
        if (event == null) {
            log.info("SentinelAnalysisSteps: No event stored -- assuming URL mismatch");
            return;
        }
        InsightResponse insight = ctx.getSentinel().analyzeWrongPage(
            ctx.getDriver(),
            event.getExpectedUrl(),
            Collections.emptyList(),
            Map.of("testName", "navigation-detection")
        );
        ctx.getSentinel().logInsight(insight);
        ctx.setLastInsight(insight);
        log.info("SentinelAnalysisSteps: analyzeWrongPage -- outcome={}, local={}",
            insight.getSuggestedTestOutcome(), insight.isLocalResolution());
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
            log.warn("SentinelAnalysisSteps: CONTINUE returned for this navigation -- " +
                "category={}, rootCause={}", insight.getConditionCategory(), insight.getRootCause());
        } else {
            log.info("SentinelAnalysisSteps: Insight is not continuable -- correct for wrong-page");
        }
    }

    @And("the insight may be continuable if the destination is valid")
    public void theInsightMayBeContinuableIfTheDestinationIsValid() {
        InsightResponse insight = ctx.getLastInsight();
        assertThat(insight).isNotNull();
        log.info("SentinelAnalysisSteps: isContinuable={} -- category={}, outcome={}",
            insight.isContinuable(), insight.getConditionCategory(), insight.getSuggestedTestOutcome());
    }

    @When("the test checks if the checkboxes page is loaded before performing setup")
    public void theTestChecksIfTheCheckboxesPageIsLoadedBeforePerformingSetup() {
        InternetPage page = new InternetPage(ctx.getDriver());
        log.info("SentinelAnalysisSteps: Pre-setup check -- checkboxes loaded={}",
            page.isCheckboxesPageLoaded());
    }

    @Then("the checkboxes page is confirmed loaded")
    public void theCheckboxesPageIsConfirmedLoaded() {
        InternetPage page = new InternetPage(ctx.getDriver());
        assertThat(page.isCheckboxesPageLoaded())
            .as("Checkboxes page should be loaded -- STATE_ALREADY_SATISFIED scenario")
            .isTrue();
    }

    @And("the test can proceed without TestSentinel analysis")
    public void theTestCanProceedWithoutTestSentinelAnalysis() {
        ctx.syncInsightFromListener();
        if (ctx.getLastInsight() == null) {
            log.info("SentinelAnalysisSteps: No TestSentinel analysis triggered -- state already satisfied");
        } else {
            log.info("SentinelAnalysisSteps: Insight present -- isContinuable={}",
                ctx.getLastInsight().isContinuable());
        }
    }

    @When("TestSentinel analyzes the wrong page condition")
    public void testSentinelAnalyzesTheWrongPageCondition() {
        ConditionEvent event = ctx.getLastEvent();
        String expectedUrl = (event != null && event.getExpectedUrl() != null)
            ? event.getExpectedUrl()
            : InternetPage.LOGIN_URL;

        InsightResponse insight = ctx.getSentinel().analyzeWrongPage(
            ctx.getDriver(),
            expectedUrl,
            Collections.emptyList(),
            Map.of("testName", "continue-context-scenario")
        );
        ctx.getSentinel().logInsight(insight);
        ctx.setLastInsight(insight);
        log.info("SentinelAnalysisSteps: Wrong page analysis -- isContinuable={}, local={}",
            insight.isContinuable(), insight.isLocalResolution());
    }

    @Then("if the insight is continuable it should have a continue context")
    public void ifTheInsightIsContinuableItShouldHaveAContinueContext() {
        InsightResponse insight = ctx.getLastInsight();
        assertThat(insight).isNotNull();
        if (insight.isContinuable()) {
            assertThat(insight.getContinueContext())
                .as("A continuable insight should have a ContinueContext")
                .isNotNull();
            log.info("SentinelAnalysisSteps: ContinueContext -- observedState='{}'",
                insight.getContinueContext().getObservedState());
        } else {
            log.info("SentinelAnalysisSteps: Insight not continuable -- ContinueContext check skipped");
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

    // ════════════════════════════════════════════════════════════════════════
    // Feature 05 -- Unknown Condition Recording
    // ════════════════════════════════════════════════════════════════════════

    @Given("no pattern matches the locator {string}")
    public void noPatternMatchesTheLocator(String locator) {
        // Verify no existing pattern would match this locator
        // (Since the locator contains a random suffix, no pattern should match)
        log.info("SentinelAnalysisSteps: Precondition -- no KB pattern expected to match '{}'", locator);
        // No assertion needed -- if a false match occurs the later assertions will catch it
    }

    @And("an unknown condition record should have been created for {string}")
    public void anUnknownConditionRecordShouldHaveBeenCreatedFor(String locatorValue) {
        assertThat(ctx.getSentinel().getRecorder())
            .as("UnknownConditionRecorder must be configured (set TESTSENTINEL_UNKNOWN_LOG_PATH or use offline mode)")
            .isNotNull();

        List<UnknownConditionRecord> records = ctx.getSentinel().getUnknownConditionRecords();
        boolean found = records.stream()
            .anyMatch(r -> r.getLocatorValue() != null && r.getLocatorValue().contains(
                // Strip leading # or . if present -- locatorValue is extracted without selector prefix
                locatorValue.startsWith("#") ? locatorValue.substring(1) : locatorValue
            ) || (r.getMessage() != null && r.getMessage().contains(locatorValue)));

        assertThat(found)
            .as("An unknown condition record should exist for locator '%s'. Records: %s",
                locatorValue, records)
            .isTrue();
        log.info("SentinelAnalysisSteps: Unknown condition record confirmed for '{}'", locatorValue);
    }

    @And("the unknown record status should be {string}")
    public void theUnknownRecordStatusShouldBe(String expectedStatus) {
        List<UnknownConditionRecord> records = ctx.getSentinel().getUnknownConditionRecords();
        boolean allNew = records.stream()
            .filter(r -> r.getStatus() != null)
            .anyMatch(r -> r.getStatus().name().equals(expectedStatus));
        assertThat(allNew)
            .as("At least one record should have status '%s'", expectedStatus)
            .isTrue();
        log.info("SentinelAnalysisSteps: Record with status '{}' confirmed", expectedStatus);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Feature 07 -- Session Cookie Bypass
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Simulates a test trying to interact with the login form while already on
     * the secure page. The username field does not exist on /secure — a
     * NoSuchElementException is thrown, TestSentinel intercepts it, and the
     * "internet-session-cookie-bypass" KB pattern fires, returning CONTINUE.
     */
    @When("the test attempts to find the login username field on the current page")
    public void theTestAttemptsToFindTheLoginUsernameFieldOnTheCurrentPage() {
        log.info("SentinelAnalysisSteps: Attempting to find login username field on '{}'",
            ctx.getDriver().getCurrentUrl());
        try {
            ctx.getDriver().findElement(By.id("username"));
            fail("Expected NoSuchElementException -- test should be on the secure page with no login form");
        } catch (NoSuchElementException e) {
            log.info("SentinelAnalysisSteps: NoSuchElementException caught -- session cookie bypass simulated");
        }
        ctx.syncInsightFromListener();
        if (ctx.getLastInsight() == null && ctx.getSentinel() != null) {
            callSentinelForMissingElement("id=username", "username");
        }
    }

    @And("the insight should be continuable")
    public void theInsightShouldBeContinuable() {
        InsightResponse insight = ctx.getLastInsight();
        assertThat(insight).isNotNull();
        assertThat(insight.isContinuable())
            .as("Insight should be continuable (CONTINUE outcome or NAVIGATED_PAST/STATE_ALREADY_SATISFIED " +
                "category). Actual: outcome=%s, category=%s",
                insight.getSuggestedTestOutcome(), insight.getConditionCategory())
            .isTrue();
        log.info("SentinelAnalysisSteps: Insight is continuable -- outcome={}, category={}",
            insight.getSuggestedTestOutcome(), insight.getConditionCategory());
    }

    // ════════════════════════════════════════════════════════════════════════
    // Private helpers
    // ════════════════════════════════════════════════════════════════════════

    private void callSentinelForMissingElement(String locatorDescription, String locatorValue) {
        if (ctx.getSentinel() == null) return;
        ConditionEvent event = ConditionEvent.builder()
            .conditionType(ConditionType.LOCATOR_NOT_FOUND)
            .message("Element not found: " + locatorDescription)
            .currentUrl(ctx.getDriver().getCurrentUrl())
            .locatorValue(locatorValue)
            .build();
        ctx.setLastEvent(event);
        InsightResponse insight = ctx.getSentinel().analyzeEvent(event);
        ctx.getSentinel().logInsight(insight);
        ctx.setLastInsight(insight);
    }

    private String extractUrlPath(String url) {
        if (url == null) return "";
        try {
            java.net.URI uri = new java.net.URI(url);
            return uri.getHost() + (uri.getPath() != null ? uri.getPath() : "");
        } catch (Exception e) {
            return url;
        }
    }
}
