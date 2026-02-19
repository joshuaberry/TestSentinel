# ─────────────────────────────────────────────────────────────────────────────
# Feature: Google Search — Happy Path
#
# These scenarios exercise the normal, successful path through the Google
# homepage. TestSentinel is initialized and observing, but no analysis is
# triggered because no unexpected conditions occur.
#
# TestSentinel Capability Demonstrated: Transparent observation.
# The presence of TestSentinel has zero impact on passing tests.
# ─────────────────────────────────────────────────────────────────────────────
@smoke @happy-path
Feature: Google Search Happy Path

  Background:
    Given the browser is open on the Google homepage

  @search
  Scenario: User searches for a term and sees results
    Given the search bar is visible
    When the user types "Selenium WebDriver" into the search bar
    And the user submits the search
    Then the results page title contains "Selenium WebDriver"
    And at least one result is displayed

  @search
  Scenario: Search bar accepts input without errors
    Given the search bar is visible
    When the user types "open source testing tools" into the search bar
    Then the search bar contains the text "open source testing tools"
    And no TestSentinel analysis was triggered

  @navigation
  Scenario: Google homepage loads with the expected title
    Then the page title is "Google"
    And the search bar is visible
    And no TestSentinel analysis was triggered

  @search
  Scenario Outline: Searching for different terms always returns results
    Given the search bar is visible
    When the user types "<term>" into the search bar
    And the user submits the search
    Then the results page title contains "<term>"

    Examples:
      | term               |
      | Cucumber BDD       |
      | TestNG framework   |
      | Java Selenium 4    |
