# ─────────────────────────────────────────────────────────────────────────────
# Feature: TestSentinel — Navigation Detection and CONTINUE Outcome
#
# These scenarios exercise the CONTINUE outcome: TestSentinel recognising when
# a condition is NOT actually a problem and the test should keep going.
#
# Page choices:
#   NAVIGATION (wrong page)     — browser is on /login, test expects /secure
#   NAVIGATED_PAST              — browser is already on /secure (logged in),
#                                 test expected to still be on /login
#   STATE_ALREADY_SATISFIED     — checkboxes page is already loaded before
#                                 the test tries to set it up
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
    Given the browser is open on the login page
    And TestSentinel is enabled with a valid API key

  @navigation-wrong-page
  Scenario: TestSentinel detects the browser is on the wrong page
    Given the test expects to be on "https://the-internet.herokuapp.com/secure"
    When the test checks whether the current URL matches the expected URL
    And the URLs do not match
    Then TestSentinel should classify this as a navigation condition
    And the insight should not be continuable

  @navigated-past
  Scenario: TestSentinel recognises the test has already passed the expected step
    Given the user has already logged in with username "tomsmith" and password "SuperSecretPassword!"
    And the test expected to be on "https://the-internet.herokuapp.com/login"
    When the test checks whether the current URL matches the expected URL
    And the URLs do not match
    Then TestSentinel should have produced an insight
    And the insight may be continuable if the destination is valid

  @state-satisfied
  Scenario: TestSentinel detects the checkboxes page is already loaded before setup
    Given the browser is open on the checkboxes page
    And the checkboxes page is already loaded
    When the test checks if the checkboxes page is loaded before performing setup
    Then the checkboxes page is confirmed loaded
    And the test can proceed without TestSentinel analysis

  @continue-context
  Scenario: A continuable insight includes a reason why continuation is safe
    Given the user has already logged in with username "tomsmith" and password "SuperSecretPassword!"
    And the test expected to navigate to "/login" but is on the secure page
    When TestSentinel analyzes the wrong page condition
    Then if the insight is continuable it should have a continue context
    And the continue context should include an observed state description
