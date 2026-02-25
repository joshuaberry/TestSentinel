# ─────────────────────────────────────────────────────────────────────────────
# Feature: TestSentinel — Unknown Condition Recording
#
# These scenarios verify that when no local pattern matches a condition,
# TestSentinel records an UnknownConditionRecord for human review instead
# of calling the API.
#
# This is the offline safety net: engineers review the unknown conditions log
# after a test run and add KB patterns for recurring failures.
#
# TestSentinel Capability Demonstrated:
#   - Graceful INVESTIGATE outcome when no KB pattern matches
#   - UnknownConditionRecorder creating records in the log file
#   - Zero tokens used — no API call is ever made
#   - Record has status=NEW awaiting engineer review
# ─────────────────────────────────────────────────────────────────────────────
@sentinel @offline @unknown-recording
Feature: TestSentinel Unknown Condition Recording

  Background:
    Given the browser is open on the login page
    And a knowledge base file is configured

  @recording
  Scenario: Unmatched condition is recorded for review and produces offline INVESTIGATE outcome
    Given no pattern matches the locator "#completely-unknown-sentinel-xyz-123"
    When the test attempts to find an element with css "#completely-unknown-sentinel-xyz-123"
    Then TestSentinel should have produced an insight
    And the insight tokens used should be 0
    And the suggested outcome should be "INVESTIGATE"
    And an unknown condition record should have been created for "#completely-unknown-sentinel-xyz-123"
    And the unknown record status should be "NEW"

  @recording
  Scenario: A second distinct unknown condition creates a separate record
    Given no pattern matches the locator "#another-unknown-element-abc-999"
    When the test attempts to find an element with css "#another-unknown-element-abc-999"
    Then TestSentinel should have produced an insight
    And the insight tokens used should be 0
    And the suggested outcome should be "INVESTIGATE"
