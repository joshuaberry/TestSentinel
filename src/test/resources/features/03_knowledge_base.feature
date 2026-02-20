# ─────────────────────────────────────────────────────────────────────────────
# Feature: TestSentinel — Knowledge Base Local Resolution
#
# These scenarios demonstrate the knowledge base bypass: a condition that has
# been seen before and recorded by an engineer resolves in sub-millisecond time
# with zero API cost. No call to Claude is ever made.
#
# TestSentinel Capability Demonstrated:
#   - KnownConditionRepository matching
#   - LocalResolutionBuilder constructing InsightResponse without API
#   - insight.isLocalResolution() == true
#   - insight.getResolvedFromPattern() returning the pattern id
#   - confidence == 1.0 for known patterns
#   - analysisTokens == 0 for known patterns
#   - recordResolution() promoting a Claude result to the KB
#
# ─────────────────────────────────────────────────────────────────────────────
@sentinel @knowledge-base
Feature: TestSentinel Knowledge Base Local Resolution

  Background:
    Given the browser is open on the Google homepage
    And a knowledge base file is configured

  @local-resolution
  Scenario: A pre-loaded known pattern resolves without calling Claude
    Given the knowledge base contains the pattern "google-missing-lucky-btn"
    When the test attempts to find an element with css "#i-am-feeling-extra-lucky"
    Then TestSentinel should have produced an insight
    And the insight should have been resolved locally
    And the insight resolved pattern should be "google-missing-lucky-btn"
    And the insight confidence should equal 1.0
    And the insight tokens used should be 0

  @local-resolution
  Scenario: Local resolution is faster than a Claude API call
    Given the knowledge base contains the pattern "google-missing-lucky-btn"
    When the test attempts to find an element with css "#i-am-feeling-extra-lucky"
    Then the analysis latency should be under 100 milliseconds

  @promote-pattern
  Scenario: A Claude result can be promoted to the knowledge base
    Given TestSentinel is enabled with a valid API key
    And the knowledge base does not contain the pattern "google-promoted-test-pattern"
    When the test attempts to find an element with css "#element-that-does-not-exist-yet"
    Then TestSentinel should have produced an insight
    When the engineer promotes the insight as pattern "google-promoted-test-pattern"
    Then the knowledge base should contain the pattern "google-promoted-test-pattern"

  @local-resolution
  Scenario: Second occurrence of a promoted pattern resolves locally
    Given TestSentinel is enabled with a valid API key
    And the knowledge base does not contain the pattern "google-reuse-test-pattern"
    When the test attempts to find an element with css "#reuse-pattern-sentinel-test"
    Then TestSentinel should have produced an insight
    When the engineer promotes the insight as pattern "google-reuse-test-pattern"
    And the test attempts to find an element with css "#reuse-pattern-sentinel-test"
    Then the insight should have been resolved locally
    And the insight tokens used should be 0
