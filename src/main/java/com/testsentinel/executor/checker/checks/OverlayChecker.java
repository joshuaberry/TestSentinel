package com.testsentinel.executor.checker.checks;

import com.testsentinel.executor.checker.CheckerResult;
import com.testsentinel.executor.checker.ChecksCondition;
import com.testsentinel.executor.checker.ConditionChecker;
import com.testsentinel.model.ActionPlan;
import com.testsentinel.model.ActionStep;
import com.testsentinel.model.ConditionEvent;
import com.testsentinel.model.InsightResponse;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.util.List;
import java.util.Map;

/**
 * Detects modal dialogs, cookie banners, and overlay elements that may be
 * intercepting clicks and causing element-not-found or not-clickable errors.
 *
 * Checks:
 *   1. Live DOM scan for common overlay selectors
 *   2. DOM snapshot text for overlay keywords
 *
 * Runs at priority 20 -- requires a live DOM check but cheap and very common.
 */
@ChecksCondition(id = "overlay", priority = 20)
public class OverlayChecker implements ConditionChecker {

    private static final List<String> OVERLAY_SELECTORS = List.of(
        "[class*='modal'][style*='display: block']",
        "[class*='modal'].show",
        "[class*='overlay']:not([style*='display: none'])",
        "[class*='cookie-banner']",
        "[id*='cookie-consent']",
        "[class*='gdpr']",
        "[role='dialog'][aria-modal='true']",
        ".modal-backdrop.show"
    );

    private static final List<String> OVERLAY_DOM_KEYWORDS = List.of(
        "modal-open", "overlay--visible", "cookie-banner--active"
    );

    @Override
    public CheckerResult check(WebDriver driver, ConditionEvent event) {
        try {
            String detectedSelector = findActiveOverlay(driver);
            if (detectedSelector == null && !domSnapshotIndicatesOverlay(event)) {
                return CheckerResult.noMatch("overlay");
            }

            String selector = detectedSelector != null ? detectedSelector : "[role='dialog']";
            ActionPlan plan = buildPlan(selector);

            return CheckerResult.matched(
                "overlay",
                InsightResponse.ConditionCategory.OVERLAY,
                "An overlay or modal dialog is blocking interaction. " +
                "Detected selector: " + selector + ". " +
                "The element the test is trying to interact with is likely obscured.",
                0.88,
                plan,
                InsightResponse.SuggestedOutcome.RETRY.name()
            );
        } catch (Exception e) {
            return CheckerResult.noMatch("overlay");
        }
    }

    private String findActiveOverlay(WebDriver driver) {
        for (String selector : OVERLAY_SELECTORS) {
            try {
                List<WebElement> els = driver.findElements(By.cssSelector(selector));
                if (!els.isEmpty() && els.get(0).isDisplayed()) {
                    return selector;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private boolean domSnapshotIndicatesOverlay(ConditionEvent event) {
        if (event.getDomSnapshot() == null) return false;
        String dom = event.getDomSnapshot().toLowerCase();
        return OVERLAY_DOM_KEYWORDS.stream().anyMatch(dom::contains);
    }

    private ActionPlan buildPlan(String overlaySelector) {
        ActionStep dismiss = new ActionStep();
        dismiss.setActionType("DISMISS_OVERLAY");
        dismiss.setParameters(Map.of("selector", overlaySelector, "method", "click"));
        dismiss.setDescription("Dismiss the overlay blocking test interaction");
        dismiss.setConfidence(0.85);
        dismiss.setRiskLevel(ActionStep.RiskLevel.LOW);
        dismiss.setRationale("The overlay must be dismissed before the target element can be interacted with.");

        ActionStep escFallback = new ActionStep();
        escFallback.setActionType("DISMISS_OVERLAY");
        escFallback.setParameters(Map.of("method", "escape"));
        escFallback.setDescription("Fallback: dismiss overlay using Escape key");
        escFallback.setConfidence(0.70);
        escFallback.setRiskLevel(ActionStep.RiskLevel.LOW);
        escFallback.setRationale("If close button click failed, Escape key is a universal dismiss signal.");

        // Wire branching: if dismiss click fails → try escape
        dismiss.setParameters(Map.of(
            "selector", overlaySelector,
            "method", "click",
            "onFailure", "esc-fallback",
            "id", "dismiss-click"
        ));
        escFallback.setParameters(Map.of(
            "method", "escape",
            "id", "esc-fallback"
        ));

        ActionPlan plan = new ActionPlan();
        plan.setActions(List.of(dismiss, escFallback));
        plan.setPlanSummary("Dismiss blocking overlay then retry original action");
        plan.setPlanConfidence(0.85);
        plan.setRequiresHuman(false);
        return plan;
    }
}
