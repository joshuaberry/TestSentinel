package com.testsentinel.core;

import com.testsentinel.model.InsightResponse;
import com.testsentinel.model.KnownCondition;

import java.time.Instant;
import java.util.UUID;

/**
 * Constructs a complete InsightResponse from a KnownCondition record.
 *
 * The result is structurally identical to a Claude-produced response.
 * All existing callers -- TestSentinelListener, ActionPlanAdvisor, BaseSeleniumTest --
 * receive and use it without modification.
 *
 * Key differences from a Claude-produced response that are visible in logs/reports:
 *   - confidence is always 1.0 (known pattern = certain)
 *   - analysisTokens is 0 (no API call made)
 *   - analysisLatencyMs is sub-millisecond
 *   - rawClaudeResponse starts with "[LOCAL]" for debugging
 *   - resolvedFromPattern field carries the pattern id for audit/reporting
 */
public class LocalResolutionBuilder {

    /**
     * Builds an InsightResponse from a matched KnownCondition.
     *
     * @param kc        The matched pattern
     * @param latencyMs Time spent in matching (typically 0-1ms)
     * @return A fully populated InsightResponse ready for use by all framework code
     */
    public InsightResponse build(KnownCondition kc, long latencyMs) {
        InsightResponse r = new InsightResponse();

        r.setConditionId(UUID.randomUUID().toString());
        r.setConditionCategory(
            InsightResponse.ConditionCategory.valueOf(kc.getConditionCategory()));
        r.setRootCause(kc.getRootCause());
        r.setConfidence(1.0);
        r.setEvidenceHighlights(kc.getEvidenceHighlights());
        r.setTransient(kc.isTransient());
        r.setSuggestedTestOutcome(kc.getSuggestedTestOutcome());
        r.setActionPlan(kc.getActionPlan());
        r.setContinueContext(kc.getContinueContext());
        r.setAnalysisTokens(0);
        r.setAnalysisLatencyMs(latencyMs);
        r.setAnalyzedAt(Instant.now());
        r.setRawClaudeResponse("[LOCAL] Resolved from known pattern: " + kc.getId());
        r.setResolvedFromPattern(kc.getId());

        return r;
    }
}
