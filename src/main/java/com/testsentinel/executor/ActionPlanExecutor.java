package com.testsentinel.executor;

import com.testsentinel.model.ActionPlan;
import com.testsentinel.model.ActionStep;
import com.testsentinel.model.ActionType;
import com.testsentinel.model.ConditionEvent;
import com.testsentinel.model.InsightResponse;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Executes an {@link ActionPlan} produced by Phase 2 analysis.
 *
 * ## Execution model
 *
 *   1. Steps are executed in order (step[0] first) unless in-plan branching
 *      redirects execution via {@code onSuccess} / {@code onFailure} step ids.
 *   2. A step whose riskLevel exceeds maxRiskLevel is skipped — logged as a
 *      recommendation but not acted upon.
 *   3. If a step has {@code requiresVerification=true} and its handler returns
 *      FAILED, execution stops immediately (fail-fast).
 *   4. In dry-run mode all steps are processed but no browser actions occur.
 *
 * ## In-plan branching (Option 2 within Option 4)
 *
 *   Each ActionStep may carry optional {@code onSuccess} and {@code onFailure}
 *   parameter keys naming the id of the next step to jump to:
 *
 *   <pre>
 *     step id="wait-spinner": WAIT_FOR_ELEMENT selector=".spinner" condition=absent
 *       onSuccess → "verify-loaded"   (spinner gone, check content is ready)
 *       onFailure → "refresh-page"    (spinner stuck, try a refresh)
 *     step id="refresh-page": REFRESH_PAGE
 *     step id="verify-loaded": WAIT_FOR_ELEMENT selector="#main-content"
 *   </pre>
 *
 *   Steps without onSuccess/onFailure simply proceed to the next step in sequence.
 */
public class ActionPlanExecutor {

    private static final Logger log = LoggerFactory.getLogger(ActionPlanExecutor.class);

    private final ActionHandlerRegistry registry;
    private final ActionStep.RiskLevel  maxRiskLevel;
    private final boolean               dryRun;

    public ActionPlanExecutor(
            ActionHandlerRegistry registry,
            ActionStep.RiskLevel maxRiskLevel,
            boolean dryRun) {
        this.registry     = registry;
        this.maxRiskLevel = maxRiskLevel;
        this.dryRun       = dryRun;
    }

    public ActionPlanExecutor(ActionHandlerRegistry registry, ActionStep.RiskLevel maxRiskLevel) {
        this(registry, maxRiskLevel, false);
    }

    // ── Primary API ───────────────────────────────────────────────────────────

    /**
     * Executes the insight's ActionPlan and returns one {@link ActionResult}
     * per step that was visited (skipped steps are included).
     *
     * @param insight       The insight whose ActionPlan will be executed
     * @param driver        Active WebDriver
     * @param event         The original ConditionEvent that triggered analysis
     * @param priorAttempts Previous cascade results — passed to ActionContext
     *                      so handlers can avoid repeating failed actions
     */
    public List<ActionResult> execute(InsightResponse insight,
                                       WebDriver driver,
                                       ConditionEvent event,
                                       List<CascadeResult> priorAttempts) {
        if (!insight.hasActionPlan()) {
            log.debug("ActionPlanExecutor: No action plan — nothing to execute");
            return Collections.emptyList();
        }

        ActionPlan plan = insight.getActionPlan();
        log.info("ActionPlanExecutor: Executing plan '{}' ({} step(s), maxRisk={}, dryRun={})",
            plan.getPlanSummary(), plan.size(), maxRiskLevel, dryRun);

        // Build an id→step map for branch navigation
        Map<String, ActionStep> stepById = plan.getActions().stream()
            .filter(s -> s.getParam("id", null) != null)
            .collect(Collectors.toMap(s -> s.getParam("id", null), s -> s));

        List<ActionResult> results    = new ArrayList<>();
        List<ActionStep>   steps      = plan.getActions();
        int                index      = 0;  // current position in the step list
        int                visited    = 0;  // guard against infinite branch loops
        int                maxVisited = steps.size() * 2 + 10;

        while (index < steps.size() && visited < maxVisited) {
            ActionStep step = steps.get(index);
            visited++;

            ActionResult result = executeStep(visited, step, insight, driver, event, priorAttempts);
            results.add(result);

            // Fail-fast on verified steps
            if (result.isFailed() && step.isRequiresVerification()) {
                log.warn("ActionPlanExecutor: Step failed with requiresVerification=true — halting");
                break;
            }

            // In-plan branching: check onSuccess / onFailure parameters
            String branchKey  = result.isExecuted() ? "onSuccess" : "onFailure";
            String branchStepId = step.getParam(branchKey, null);

            if (branchStepId != null && stepById.containsKey(branchStepId)) {
                ActionStep target = stepById.get(branchStepId);
                index = steps.indexOf(target);
                log.info("ActionPlanExecutor: Branch {} → step id='{}'", branchKey, branchStepId);
            } else {
                index++;
            }
        }

        if (visited >= maxVisited) {
            log.warn("ActionPlanExecutor: Branch loop guard triggered — execution stopped");
        }

        logSummary(results);
        return results;
    }

    /** Convenience overload for callers without cascade context. */
    public List<ActionResult> execute(InsightResponse insight, WebDriver driver) {
        return execute(insight, driver, null, Collections.emptyList());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ActionResult executeStep(int visitNum, ActionStep step,
                                      InsightResponse insight, WebDriver driver,
                                      ConditionEvent event, List<CascadeResult> priorAttempts) {
        ActionType type = step.getActionType();

        log.info("ActionPlanExecutor: Visit #{} — type={}, risk={}, confidence={}, desc='{}'",
            visitNum, type, step.getRiskLevel(),
            String.format("%.0f%%", step.getConfidence() * 100),
            step.getDescription());

        // ── Risk gate ─────────────────────────────────────────────────────────
        if (!isWithinRiskLimit(step)) {
            String msg = String.format(
                "Skipped (risk=%s exceeds maxRiskLevel=%s) — Recommendation: %s",
                step.getRiskLevel(), maxRiskLevel, step.getDescription());
            log.info("ActionPlanExecutor: SKIPPED — {}", msg);
            return ActionResult.skipped(msg);
        }

        if (type == null) {
            return ActionResult.notFound("null");
        }

        return registry.find(type)
            .map(handler -> {
                ActionContext ctx = new ActionContext(
                    driver, step, insight, event, priorAttempts, maxRiskLevel, dryRun);
                try {
                    ActionResult result = handler.execute(ctx);
                    log.info("ActionPlanExecutor: {} — {}", result.getOutcome(), result.getMessage());
                    return result;
                } catch (Exception e) {
                    log.error("ActionPlanExecutor: Handler threw unexpectedly: {}", e.getMessage(), e);
                    return ActionResult.failed("Handler threw: " + e.getMessage(), e);
                }
            })
            .orElseGet(() -> {
                ActionResult r = ActionResult.notFound(type.name());
                log.warn("ActionPlanExecutor: NOT_FOUND — {}", r.getMessage());
                return r;
            });
    }

    private boolean isWithinRiskLimit(ActionStep step) {
        if (step.getRiskLevel() == null) return true;
        return step.getRiskLevel().ordinal() <= maxRiskLevel.ordinal();
    }

    private void logSummary(List<ActionResult> results) {
        long executed = results.stream().filter(ActionResult::isExecuted).count();
        long skipped  = results.stream().filter(ActionResult::isSkipped).count();
        long failed   = results.stream().filter(ActionResult::isFailed).count();
        long notFound = results.stream().filter(ActionResult::isNotFound).count();
        log.info("ActionPlanExecutor: Complete — executed={}, skipped={}, failed={}, notFound={}",
            executed, skipped, failed, notFound);
    }
}
