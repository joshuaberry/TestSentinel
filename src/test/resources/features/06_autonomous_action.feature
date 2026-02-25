# ─────────────────────────────────────────────────────────────────────────────
# Feature: TestSentinel — Autonomous Action Execution
#
# These scenarios verify that when a KB pattern includes a LOW-risk action plan,
# TestSentinel automatically executes those steps via ActionPlanExecutor.
#
# The action plan advisor confirms which steps are safe to execute within the
# configured maxRiskLevel (default: LOW).
#
# MEDIUM and HIGH risk steps are always advisory-only — logged but not executed.
#
# TestSentinel Capability Demonstrated:
#   - KB pattern with action plan triggers auto-execution after local match
#   - ActionPlanAdvisor.getExecutableSteps() filtering by risk level
#   - LOW-risk steps are executable within default configuration
#   - MEDIUM-risk steps are NEVER auto-executed (advisory only)
# ─────────────────────────────────────────────────────────────────────────────
@sentinel @offline @autonomous
Feature: TestSentinel Autonomous Action Execution

  Background:
    Given the browser is open on the login page
    And a knowledge base file is configured

  @action-plan
  Scenario: KB pattern with LOW-risk CAPTURE_SCREENSHOT step provides executable recommendation
    Given the knowledge base contains the pattern "internet-autonomous-action-test"
    When the test attempts to find an element with css "#autonomous-action-test-element"
    Then TestSentinel should have produced an insight
    And the insight should have been resolved locally
    And the insight should contain an action plan
    And the action plan should have at least one LOW-risk step
    And the advisor confirms at least one step is executable

  @action-plan
  Scenario: MEDIUM-risk action plan steps are advisory-only at default risk limit
    Given the knowledge base contains the pattern "internet-checkout-btn"
    When the test attempts to click a nonexistent element with id "checkout-submit-btn"
    Then TestSentinel should have produced an insight
    And the insight should have been resolved locally
    And the insight should contain an action plan
    And the advisor confirms at least one step is executable
    And no MEDIUM or HIGH risk steps are auto-executable at current risk limit
