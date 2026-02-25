# ─────────────────────────────────────────────────────────────────────────────
# Feature: TestSentinel — Knowledge Base Local Resolution
#
# These scenarios demonstrate the knowledge base bypass: a condition that has
# been seen before resolves in sub-millisecond time with zero API cost.
# No call to Claude is ever made.
#
# TestSentinel Capability Demonstrated:
#   - KnownConditionRepository matching
#   - LocalResolutionBuilder constructing InsightResponse without API
#   - insight.isLocalResolution() == true
#   - insight.getResolvedFromPattern() returning the pattern id
#   - confidence == 1.0 for known patterns
#   - analysisTokens == 0 for known patterns
#   - addPattern() adding a KnownCondition directly without Claude
# ─────────────────────────────────────────────────────────────────────────────
@sentinel @knowledge-base
Feature: TestSentinel Knowledge Base Local Resolution

  Background:
    Given the browser is open on the login page
    And a knowledge base file is configured

  @local-resolution @josh
  Scenario: A pre-loaded known pattern resolves without calling Claude
    Given the knowledge base contains the pattern "internet-missing-sso-btn"
    When the test attempts to find an element with css "#sso-login-button"
    Then TestSentinel should have produced an insight
    And the insight should have been resolved locally
    And the insight resolved pattern should be "internet-missing-sso-btn"
    And the insight confidence should equal 1.0
    And the insight tokens used should be 0

  @local-resolution
  Scenario: Local resolution is faster than an API call
    Given the knowledge base contains the pattern "internet-missing-sso-btn"
    When the test attempts to find an element with css "#sso-login-button"
    Then the analysis latency should be under 100 milliseconds

  @direct-add
  Scenario: A pattern can be added directly to the knowledge base without Claude
    Given the knowledge base does not contain the pattern "internet-direct-add-test"
    When the engineer adds pattern "internet-direct-add-test" for element "#direct-add-sentinel-test"
    Then the knowledge base should contain the pattern "internet-direct-add-test"

  @local-resolution @direct-add
  Scenario: A directly-added pattern resolves the next occurrence locally
    Given the knowledge base does not contain the pattern "internet-direct-reuse-test"
    When the engineer adds pattern "internet-direct-reuse-test" for element "#direct-reuse-sentinel-test"
    And the test attempts to find an element with css "#direct-reuse-sentinel-test"
    Then the insight should have been resolved locally
    And the insight tokens used should be 0
