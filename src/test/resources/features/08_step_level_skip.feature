# ─────────────────────────────────────────────────────────────────────────────
# Feature: TestSentinel — Step-Level Skip Propagation
#
# Problem:
#   When Selenium throws NoSuchElementException on the first call inside a
#   multi-step workflow (e.g. enterUsername → enterPassword → clickLogin),
#   TestSentinel can detect a CONTINUE condition. But without extra plumbing,
#   enterPassword() and clickLogin() would still execute and fail.
#
# Solution:
#   TestSentinelListener.setRaiseOnSkip(true) tells the listener to throw
#   SentinelStepSkipException when the insight says CONTINUE. Because Selenium 4's
#   EventFiringDecorator propagates exceptions thrown by onError() through the full
#   call stack, a single catch at the Gherkin step level is enough to skip the
#   entire multi-step workflow — no changes to page objects required.
#
# Demo scenario:
#   The browser is already on /secure (session cookie has bypassed login).
#   The Gherkin step calls the full 3-operation login workflow:
#     1. findElement(#username).sendKeys(...)  ← NoSuchElementException → SKIP HERE
#     2. findElement(#password).sendKeys(...)  ← never runs
#     3. findElement(button).click()           ← never runs
#   SentinelStepSkipException is caught at the step level. The step passes.
# ─────────────────────────────────────────────────────────────────────────────
@sentinel @step-level-skip
Feature: TestSentinel Step-Level Skip Propagation

  Background:
    Given a knowledge base file is configured

  @step-level-skip-continue
  Scenario: Session cookie bypass skips the entire login workflow at the Gherkin step level
    # Simulate: session cookie already authenticated the user; browser is on /secure.
    # The test now tries to run the full login workflow — all three operations.
    Given the user has already logged in with username "tomsmith" and password "SuperSecretPassword!"
    When the test performs the complete login workflow for "tomsmith" with password "SuperSecretPassword!"
    Then TestSentinel should have produced an insight
    And the insight should be continuable
    And the suggested outcome should be "CONTINUE"
    And the insight should have been resolved locally
    And the insight tokens used should be 0
