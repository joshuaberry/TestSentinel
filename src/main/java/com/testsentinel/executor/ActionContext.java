package com.testsentinel.executor;

import com.testsentinel.model.ActionStep;
import com.testsentinel.model.ConditionEvent;
import com.testsentinel.model.InsightResponse;
import org.openqa.selenium.WebDriver;

import java.util.Collections;
import java.util.List;

/**
 * Passed to every {@link ActionHandler} when it is invoked.
 *
 * Carries:
 *   - the WebDriver instance to act upon
 *   - the specific ActionStep being executed (parameters, risk, rationale)
 *   - the full InsightResponse for context (root cause, category, condition)
 *   - the original ConditionEvent that triggered analysis
 *   - the list of prior cascade attempts so handlers can avoid repeating
 *     actions that already failed
 *   - the configured max risk level from TestSentinelConfig
 *   - dryRun flag -- when true, handlers should log intent but not act
 */
public class ActionContext {

    private final WebDriver                driver;
    private final ActionStep               step;
    private final InsightResponse          insight;
    private final ConditionEvent           event;
    private final List<CascadeResult>      priorAttempts;
    private final ActionStep.RiskLevel     maxRiskLevel;
    private final boolean                  dryRun;

    public ActionContext(
            WebDriver driver,
            ActionStep step,
            InsightResponse insight,
            ConditionEvent event,
            List<CascadeResult> priorAttempts,
            ActionStep.RiskLevel maxRiskLevel,
            boolean dryRun) {
        this.driver        = driver;
        this.step          = step;
        this.insight       = insight;
        this.event         = event;
        this.priorAttempts = priorAttempts != null ? priorAttempts : Collections.emptyList();
        this.maxRiskLevel  = maxRiskLevel;
        this.dryRun        = dryRun;
    }

    /** Convenience constructor for callers that do not use cascading. */
    public ActionContext(
            WebDriver driver,
            ActionStep step,
            InsightResponse insight,
            ActionStep.RiskLevel maxRiskLevel,
            boolean dryRun) {
        this(driver, step, insight, null, Collections.emptyList(), maxRiskLevel, dryRun);
    }

    public WebDriver            getDriver()        { return driver; }
    public ActionStep           getStep()          { return step; }
    public InsightResponse      getInsight()       { return insight; }
    public ConditionEvent       getEvent()         { return event; }
    public List<CascadeResult>  getPriorAttempts() { return priorAttempts; }
    public ActionStep.RiskLevel getMaxRiskLevel()  { return maxRiskLevel; }
    public boolean              isDryRun()         { return dryRun; }

    /** True if any prior cascade attempt already executed the given ActionType. */
    public boolean wasAlreadyAttempted(com.testsentinel.model.ActionType type) {
        return priorAttempts.stream()
            .flatMap(r -> r.getActionResults().stream())
            .filter(ActionResult::isExecuted)
            .anyMatch(r -> r.getMessage() != null && r.getMessage().contains(type.name()));
    }
}
