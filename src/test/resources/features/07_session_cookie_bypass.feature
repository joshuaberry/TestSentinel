# ─────────────────────────────────────────────────────────────────────────────
# Feature: TestSentinel — Session Cookie Bypass Detection
#
# Real-world scenario:
#   A test script opens the login page and tries to fill in the username field.
#   But the application detects an active session cookie and silently redirects
#   the browser to the secure (post-login) page instead. Selenium then throws
#   NoSuchElementException because the username field doesn't exist on /secure.
#
#   A human tester would immediately recognise this: "Oh, I'm already logged in —
#   I don't need to fill in the login form." Traditional automation just fails.
#   TestSentinel recognises the pattern and returns CONTINUE.
#
# How it works (KB matching):
#   ConditionEvent signals:
#     urlPattern      → the-internet.herokuapp.com/secure      (we're on /secure)
#     locatorValue    → username                               (looking for login field)
#     conditionType   → LOCATOR_NOT_FOUND                      (NoSuchElementException)
#   Score = 3 = minMatchSignals → pattern "internet-session-cookie-bypass" fires
#   Outcome: CONTINUE — skip the login steps, continue from current authenticated state
#
# All scenarios resolve locally — 0 tokens, no API call.
# ─────────────────────────────────────────────────────────────────────────────
@sentinel @session-cookie-bypass
Feature: TestSentinel Session Cookie Bypass Detection

  Background:
    Given a knowledge base file is configured

  @session-cookie-bypass-continue
  Scenario: TestSentinel detects session cookie bypass and recommends CONTINUE
    # Simulate: a session cookie has already authenticated the user and redirected
    # the browser to /secure. The test now tries to interact with the login form.
    Given the user has already logged in with username "tomsmith" and password "SuperSecretPassword!"
    When the test attempts to find the login username field on the current page
    Then TestSentinel should have produced an insight
    And the insight should be continuable
    And the insight should have been resolved locally
    And the suggested outcome should be "CONTINUE"
    And the insight tokens used should be 0

  @session-cookie-bypass-context
  Scenario: A session cookie bypass insight explains why the login step can be skipped
    Given the user has already logged in with username "tomsmith" and password "SuperSecretPassword!"
    When the test attempts to find the login username field on the current page
    Then TestSentinel should have produced an insight
    And if the insight is continuable it should have a continue context
    And the continue context should include an observed state description

  @session-cookie-bypass-action-plan
  Scenario: A session cookie bypass insight includes a LOW-risk SKIP_STEP action plan
    Given the user has already logged in with username "tomsmith" and password "SuperSecretPassword!"
    When the test attempts to find the login username field on the current page
    Then TestSentinel should have produced an insight
    And the insight should contain an action plan
    And the action plan should have at least one LOW-risk step
