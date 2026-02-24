package com.testsentinel.executor.checker.checks;

import com.testsentinel.executor.checker.CheckerResult;
import com.testsentinel.executor.checker.ChecksCondition;
import com.testsentinel.executor.checker.ConditionChecker;
import com.testsentinel.model.ActionPlan;
import com.testsentinel.model.ActionStep;
import com.testsentinel.model.ActionType;
import com.testsentinel.model.ConditionEvent;
import com.testsentinel.model.InsightResponse;
import org.openqa.selenium.WebDriver;

import java.util.List;

/**
 * Detects page load / request timeouts.
 *
 * Signals checked (any one is sufficient):
 *   - Exception message contains "timeout" or "timed out"
 *   - Stack trace contains TimeoutException
 *   - Page title is a browser error page (ERR_TIMED_OUT, net::ERR_CONNECTION_TIMED_OUT)
 *   - DOM contains typical timeout error text
 *
 * Runs at priority 10 — cheap text checks, very specific signal.
 */
@ChecksCondition(id = "page-timeout", priority = 10)
public class PageTimeoutChecker implements ConditionChecker {

    private static final List<String> TIMEOUT_SIGNALS = List.of(
        "timeout", "timed out", "timedout", "err_timed_out",
        "net::err_connection_timed_out", "TimeoutException"
    );

    @Override
    public CheckerResult check(WebDriver driver, ConditionEvent event) {
        try {
            if (!isTimeoutSignalPresent(event, driver)) {
                return CheckerResult.noMatch("page-timeout");
            }

            ActionPlan plan = buildPlan();
            return CheckerResult.matched(
                "page-timeout",
                InsightResponse.ConditionCategory.INFRA,
                "Page load timed out. The server did not respond within the expected window. " +
                "A page refresh is recommended; if the issue persists it may indicate a backend " +
                "or network infrastructure problem.",
                0.90,
                plan,
                InsightResponse.SuggestedOutcome.RETRY.name()
            );
        } catch (Exception e) {
            return CheckerResult.noMatch("page-timeout");
        }
    }

    private boolean isTimeoutSignalPresent(ConditionEvent event, WebDriver driver) {
        String message    = event.getMessage()    != null ? event.getMessage().toLowerCase()    : "";
        String stackTrace = event.getStackTrace() != null ? event.getStackTrace().toLowerCase() : "";

        for (String signal : TIMEOUT_SIGNALS) {
            if (message.contains(signal.toLowerCase()) || stackTrace.contains(signal.toLowerCase())) {
                return true;
            }
        }

        // Check live page title for browser error pages
        try {
            String title = driver.getTitle().toLowerCase();
            if (title.contains("timed out") || title.contains("err_timed_out")) {
                return true;
            }
        } catch (Exception ignored) {}

        return false;
    }

    private ActionPlan buildPlan() {
        ActionStep refresh = new ActionStep();
        refresh.setActionType(ActionType.REFRESH_PAGE);
        refresh.setDescription("Refresh the page to retry the timed-out request");
        refresh.setConfidence(0.85);
        refresh.setRiskLevel(ActionStep.RiskLevel.MEDIUM);
        refresh.setRationale("A fresh page load will re-issue the request. Most transient timeouts resolve on retry.");
        refresh.setRequiresVerification(true);

        ActionStep wait = new ActionStep();
        wait.setActionType(ActionType.WAIT_FOR_ELEMENT);
        wait.setParameters(java.util.Map.of("selector", "body", "condition", "present", "timeoutMs", "15000"));
        wait.setDescription("Wait up to 15 seconds for the page body to be present after refresh");
        wait.setConfidence(0.80);
        wait.setRiskLevel(ActionStep.RiskLevel.LOW);
        wait.setRationale("Verify that the refreshed page loaded successfully before proceeding.");

        ActionPlan plan = new ActionPlan();
        plan.setActions(List.of(refresh, wait));
        plan.setPlanSummary("Refresh page and verify load after timeout");
        plan.setPlanConfidence(0.85);
        plan.setRequiresHuman(false);
        return plan;
    }
}
