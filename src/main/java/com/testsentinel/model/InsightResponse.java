package com.testsentinel.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The structured output from TestSentinel analysis.
 *
 * Phase 1 fields: root cause, category, confidence, evidence, transient flag, suggested outcome.
 * Phase 2 adds:   actionPlan — an ordered list of recommended remediation steps with risk levels.
 *
 * The actionPlan field will be null when Phase 1 mode is active and populated
 * when Phase 2 mode is active (controlled by TestSentinelConfig.isPhase2Enabled()).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class InsightResponse {

    private String conditionId;
    private ConditionCategory conditionCategory;
    private String rootCause;
    private double confidence;           // 0.0 – 1.0
    private List<String> evidenceHighlights;
    private boolean isTransient;
    private String suggestedTestOutcome;  // RETRY | SKIP | FAIL_WITH_CONTEXT | INVESTIGATE
    private int analysisTokens;
    private long analysisLatencyMs;
    private Instant analyzedAt;
    private String rawClaudeResponse;    // Preserved for debugging

    // ── Phase 2 Addition ──────────────────────────────────────────────────────
    private ActionPlan actionPlan;        // Null in Phase 1 mode; populated in Phase 2 mode

    // ── Continue Context (populated when suggestedTestOutcome = CONTINUE) ─────
    private ContinueContext continueContext;

    // ── Condition Categories ──────────────────────────────────────────────────

    public enum ConditionCategory {
        OVERLAY,                  // Modal, cookie banner, cookie consent, ad overlay
        LOADING,                  // Spinner, skeleton screen, page still hydrating
        STALE_DOM,                // Element was present but detached / re-rendered
        NAVIGATION,               // Wrong page, unexpected redirect, auth redirect
        INFRA,                    // Slow response, timeout, CDN failure
        AUTH,                     // Session expired, login wall
        TEST_DATA,                // Missing or corrupted test data
        FLAKE,                    // Non-deterministic rendering, race condition
        APPLICATION_BUG,          // Genuine defect — element removed, broken flow
        NAVIGATED_PAST,           // Test already reached destination via prior session/state
        STATE_ALREADY_SATISFIED,  // Precondition the test was about to establish is already true
        UNKNOWN                   // Claude could not classify with sufficient confidence
    }

    // ── Suggested Test Outcomes ───────────────────────────────────────────────

    public enum SuggestedOutcome {
        CONTINUE,          // No problem — state is valid; test may proceed from current position
        RETRY,             // Condition is transient; retry the action
        SKIP,              // Condition blocks this test but not the suite
        FAIL_WITH_CONTEXT, // Genuine failure; attach insight to the failure report
        INVESTIGATE        // Unclear; human review needed
    }

    public InsightResponse() {}

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getConditionId() { return conditionId; }
    public ConditionCategory getConditionCategory() { return conditionCategory; }
    public String getRootCause() { return rootCause; }
    public double getConfidence() { return confidence; }
    public List<String> getEvidenceHighlights() { return evidenceHighlights; }
    public boolean isTransient() { return isTransient; }
    public String getSuggestedTestOutcome() { return suggestedTestOutcome; }
    public int getAnalysisTokens() { return analysisTokens; }
    public long getAnalysisLatencyMs() { return analysisLatencyMs; }
    public Instant getAnalyzedAt() { return analyzedAt; }
    public String getRawClaudeResponse() { return rawClaudeResponse; }

    // ── Phase 2 getter ────────────────────────────────────────────────────────
    public ActionPlan getActionPlan() { return actionPlan; }
    public ContinueContext getContinueContext() { return continueContext; }

    // ── Setters (used by Jackson deserialization) ─────────────────────────────

    public void setConditionId(String conditionId) { this.conditionId = conditionId; }
    public void setConditionCategory(ConditionCategory conditionCategory) { this.conditionCategory = conditionCategory; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public void setEvidenceHighlights(List<String> evidenceHighlights) { this.evidenceHighlights = evidenceHighlights; }
    public void setTransient(boolean transient_) { this.isTransient = transient_; }
    public void setSuggestedTestOutcome(String suggestedTestOutcome) { this.suggestedTestOutcome = suggestedTestOutcome; }
    public void setAnalysisTokens(int analysisTokens) { this.analysisTokens = analysisTokens; }
    public void setAnalysisLatencyMs(long analysisLatencyMs) { this.analysisLatencyMs = analysisLatencyMs; }
    public void setAnalyzedAt(Instant analyzedAt) { this.analyzedAt = analyzedAt; }
    public void setRawClaudeResponse(String rawClaudeResponse) { this.rawClaudeResponse = rawClaudeResponse; }

    // ── Phase 2 setter ────────────────────────────────────────────────────────
    public void setActionPlan(ActionPlan actionPlan) { this.actionPlan = actionPlan; }
    public void setContinueContext(ContinueContext continueContext) { this.continueContext = continueContext; }

    // ── Convenience ───────────────────────────────────────────────────────────

    public boolean isHighConfidence() { return confidence >= 0.80; }
    public boolean isLowConfidence() { return confidence < 0.50; }

    /** Phase 2: true if an ActionPlan was returned with at least one step */
    public boolean hasActionPlan() { return actionPlan != null && actionPlan.hasActions(); }

    /**
     * Returns true when Claude determined there is no problem — the test should
     * continue executing from its current position without retry or failure.
     *
     * This covers two categories:
     *   NAVIGATED_PAST          — test already reached its intended destination via prior session state
     *   STATE_ALREADY_SATISFIED — the precondition the test was about to establish is already true
     *
     * Usage in test code:
     * <pre>
     *   if (insight.isContinuable()) {
     *       log.info("TestSentinel: No problem detected — continuing. Reason: {}", insight.getRootCause());
     *       // proceed with the next test step
     *   }
     * </pre>
     */
    public boolean isContinuable() {
        return SuggestedOutcome.CONTINUE.name().equals(suggestedTestOutcome)
            || conditionCategory == ConditionCategory.NAVIGATED_PAST
            || conditionCategory == ConditionCategory.STATE_ALREADY_SATISFIED;
    }

    /**
     * Explicit check on the suggestedTestOutcome field only (ignores category).
     * Prefer isContinuable() for most usage — it catches both category and outcome signals.
     */
    public boolean isContinueOutcome() {
        return SuggestedOutcome.CONTINUE.name().equals(suggestedTestOutcome);
    }

    @Override
    public String toString() {
        return String.format(
            "InsightResponse{id='%s', category=%s, confidence=%.2f, transient=%b, rootCause='%s'}",
            conditionId, conditionCategory, confidence, isTransient, rootCause
        );
    }

    // ── Static factory for error cases ────────────────────────────────────────

    public static InsightResponse error(String reason, long latencyMs) {
        InsightResponse r = new InsightResponse();
        r.conditionId = UUID.randomUUID().toString();
        r.conditionCategory = ConditionCategory.UNKNOWN;
        r.rootCause = "TestSentinel analysis failed: " + reason;
        r.confidence = 0.0;
        r.isTransient = false;
        r.suggestedTestOutcome = SuggestedOutcome.INVESTIGATE.name();
        r.analysisLatencyMs = latencyMs;
        r.analyzedAt = Instant.now();
        return r;
    }
}
