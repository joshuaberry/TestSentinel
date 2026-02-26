# ─────────────────────────────────────────────────────────────────────────────
# Feature: TestSentinel — Local Analysis (Knowledge Base + Checker Resolution)
#
# These scenarios demonstrate TestSentinel resolving conditions entirely from
# the local knowledge base — no API call, no network, zero tokens.
#
# This is the offline-first default mode. All scenarios run without any
# environment variables beyond the KB path (auto-detected from test resources).
#
# TestSentinel Capability Demonstrated:
#   - KnownConditionRepository matching (confidence 1.0, 0 tokens)
#   - LocalResolutionBuilder producing InsightResponse without API
#   - insight.isLocalResolution() == true
#   - insight.getResolvedFromPattern() returning the pattern id
#   - KB pattern with action plan satisfying action plan assertions
# ─────────────────────────────────────────────────────────────────────────────
@sentinel @local-analysis
Feature: TestSentinel Local Analysis — Knowledge Base Resolution

  Background:
    Given the browser is open on the login page
    And a knowledge base file is configured

  @local-resolution
  Scenario: KB pattern resolves a phantom button — no API call made
    Given the knowledge base contains the pattern "internet-phantom-button"
    When the test attempts to click a nonexistent element with id "phantom-button"
    Then TestSentinel should have produced an insight
    And the insight should have been resolved locally
    And the insight resolved pattern should be "internet-phantom-button"
    And the insight confidence should equal 1.0
    And the insight tokens used should be 0

  @local-resolution
  Scenario: KB pattern resolves a missing CSS element — zero tokens used
    Given the knowledge base contains the pattern "internet-nonexistent-submit"
    When the test attempts to find an element with css ".nonexistent-submit-button"
    Then TestSentinel should have produced an insight
    And the insight should have been resolved locally
    And the insight tokens used should be 0
    And the insight confidence should equal 1.0

  @local-resolution
  Scenario: KB pattern with action plan satisfies action plan assertions
    Given the knowledge base contains the pattern "internet-checkout-btn"
    When the test attempts to click a nonexistent element with id "checkout-submit-btn"
    Then TestSentinel should have produced an insight
    And the insight should have been resolved locally
    And the insight should contain an action plan
    And the action plan should have at least one step
    And each action step should have a valid risk level

  @local-resolution
  Scenario: KB pattern resolves missing cookie consent element on checkboxes page
    Given the browser is open on the checkboxes page
    And a knowledge base file is configured
    And the knowledge base contains the pattern "internet-no-cookie-consent"
    When the test attempts to find an element with css "#cookie-consent-accept"
    Then TestSentinel should have produced an insight
    And the insight should have been resolved locally
    And the insight tokens used should be 0
