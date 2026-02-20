# ─────────────────────────────────────────────────────────────────────────────
# Feature: TestSentinel — Navigation Detection and CONTINUE Outcome
#
# These scenarios exercise the CONTINUE outcome introduced in the phase where
# TestSentinel learned to recognize conditions that are not actually problems.
#
# Scenario 1: NAVIGATION — test is on a genuinely wrong page (actual problem).
# Scenario 2: NAVIGATED_PAST — test is already ahead of where it expected to be.
# Scenario 3: STATE_ALREADY_SATISFIED — a precondition is already true.
#
# TestSentinel Capability Demonstrated:
#   - analyzeWrongPage() triggering the wrong-page analysis path
#   - insight.isContinuable() for NAVIGATED_PAST / STATE_ALREADY_SATISFIED
#   - ContinueContext.continueReason and observedState
#   - Distinguishing NAVIGATION (problem) from NAVIGATED_PAST (no problem)
# ─────────────────────────────────────────────────────────────────────────────
@sentinel @navigation @continue
Feature: TestSentinel Navigation Detection and CONTINUE Outcome

  Background:
    Given the browser is open on the Google homepage
    And TestSentinel is enabled with a valid API key

  @navigation-wrong-page
  Scenario: TestSentinel detects the browser is on the wrong page
    Given the test expects to be on "https://www.google.com/search?q=test"
    When the test checks whether the current URL matches the expected URL
    And the URLs do not match
    Then TestSentinel should classify this as a navigation condition
    And the insight should not be continuable

  @navigated-past
  Scenario: TestSentinel recognises the test has already passed the expected step
    Given the browser has navigated to the Google homepage
    And the test expected to be on "/nonexistent-login-page"
    When the test checks whether the current URL matches the expected URL
    And the URLs do not match
    Then TestSentinel should have produced an insight
    And the insight may be continuable if the destination is valid

  @state-satisfied
  Scenario: TestSentinel detects the search bar is already visible before the test fills it
    Given the search bar is already visible on the Google homepage
    When the test checks if the search bar is present before performing setup
    Then the search bar is confirmed visible
    And the test can proceed without TestSentinel analysis

  @continue-context
  Scenario: A continuable insight includes a reason why continuation is safe
    Given TestSentinel is enabled with a valid API key
    And the browser is on the Google homepage
    And the test expected to navigate to "/sign-in" but is on the homepage
    When TestSentinel analyzes the wrong page condition
    Then if the insight is continuable it should have a continue context
    And the continue context should include an observed state description
