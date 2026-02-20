# ─────────────────────────────────────────────────────────────────────────────
# Feature: TestSentinel — Claude API Analysis
#
# These scenarios deliberately cause unexpected conditions on the-internet so
# TestSentinel invokes the Claude API for root cause analysis.
#
# TestSentinel Capability Demonstrated:
#   - Automatic exception interception via EventFiringDecorator
#   - Claude API call (Phase 1 analysis)
#   - Structured InsightResponse with category + outcome
#   - Phase 2 ActionPlan (when TESTSENTINEL_PHASE2_ENABLED=true)
#
# Note: These scenarios REQUIRE a valid ANTHROPIC_API_KEY environment variable.
# They will be skipped gracefully if the API key is absent.
# ─────────────────────────────────────────────────────────────────────────────
@sentinel @claude-analysis
Feature: TestSentinel Claude API Analysis

  Background:
    Given the browser is open on the login page
    And TestSentinel is enabled with a valid API key

  @missing-element
  Scenario: TestSentinel analyzes a missing element and produces an insight
    When the test attempts to click a nonexistent element with id "phantom-button"
    Then TestSentinel should have produced an insight
    And the insight category should not be null
    And the insight confidence should be greater than 0.0
    And the insight should suggest an outcome

  @missing-element
  Scenario: TestSentinel correctly classifies a missing submit button
    When the test attempts to find an element with css ".nonexistent-submit-button"
    Then TestSentinel should have produced an insight
    And the insight confidence should be greater than 0.5
    And the insight root cause should not be empty
    And the insight should suggest an outcome of "RETRY" or "FAIL_WITH_CONTEXT" or "SKIP" or "INVESTIGATE"

  @phase2
  Scenario: TestSentinel Phase 2 produces an action plan for a missing element
    Given Phase 2 is enabled
    When the test attempts to click a nonexistent element with id "checkout-submit-btn"
    Then TestSentinel should have produced an insight
    And the insight should contain an action plan
    And the action plan should have at least one step
    And each action step should have a valid risk level

  @transient
  Scenario: TestSentinel analyzes a missing overlay element on the checkboxes page
    Given the browser is open on the checkboxes page
    When the test attempts to find an element with css "#cookie-consent-accept"
    Then TestSentinel should have produced an insight
    And the insight should describe the root cause clearly
