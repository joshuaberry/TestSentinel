# ─────────────────────────────────────────────────────────────────────────────
# Feature: Form Authentication — Happy Path
#
# These scenarios exercise the normal, successful path through the login page
# at https://the-internet.herokuapp.com/login.
# TestSentinel is initialized and observing, but no analysis is triggered
# because no unexpected conditions occur.
#
# TestSentinel Capability Demonstrated: Transparent observation.
# The presence of TestSentinel has zero impact on passing tests.
# ─────────────────────────────────────────────────────────────────────────────
@smoke @happy-path
Feature: Form Authentication Happy Path

  Background:
    Given the browser is open on the login page

  @login
  Scenario: User logs in with valid credentials and sees the secure area
    Given the login page is loaded
    When the user enters username "tomsmith" and password "SuperSecretPassword!"
    And the user clicks the login button
    Then the flash message indicates a successful login
    And the secure area heading is visible
    And no TestSentinel analysis was triggered

  @login
  Scenario: Login page loads with the expected title
    Then the page title is "The Internet"
    And the login page is loaded
    And no TestSentinel analysis was triggered

  @login
  Scenario: Login form accepts username input without errors
    Given the login page is loaded
    When the user enters username "tomsmith" and password "SuperSecretPassword!"
    Then the login page is loaded
    And no TestSentinel analysis was triggered

  @login
  Scenario: User logs out after a successful login
    Given the login page is loaded
    When the user enters username "tomsmith" and password "SuperSecretPassword!"
    And the user clicks the login button
    Then the flash message indicates a successful login
    When the user clicks the logout button
    Then the login page is loaded
