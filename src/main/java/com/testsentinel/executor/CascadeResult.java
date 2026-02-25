package com.testsentinel.executor;

import com.testsentinel.executor.checker.CheckerResult;
import com.testsentinel.model.InsightResponse;

import java.util.List;

/**
 * Records everything that happened during one pass of the cascade loop:
 * which checker (if any) matched, which insight was produced, and what
 * actions were executed.
 *
 * A list of CascadeResults is the final return value of
 * {@link CascadedInsightEngine#analyze} and gives callers a full audit trail.
 *
 * Immutable — use the builder.
 */
public class CascadeResult {

    /** How this cascade pass obtained its insight. */
    public enum Source {
        LOCAL_CHECKER,    // A ConditionChecker matched — no API call
        KNOWLEDGE_BASE,   // KB pattern matched inside analyzeEvent — no API call
        CLAUDE_API,       // Claude API was called
        UNKNOWN_RECORDED, // No match found; condition recorded for human review (offline mode)
        FALLBACK_ERROR    // All paths failed or returned error
    }

    private final int                depth;           // 1-based cascade depth (1 = first attempt)
    private final Source             source;
    private final CheckerResult      checkerResult;   // null when source != LOCAL_CHECKER
    private final InsightResponse    insight;
    private final List<ActionResult> actionResults;
    private final boolean            conditionResolved; // true = no need for another cascade pass

    private CascadeResult(Builder b) {
        this.depth             = b.depth;
        this.source            = b.source;
        this.checkerResult     = b.checkerResult;
        this.insight           = b.insight;
        this.actionResults     = b.actionResults != null ? b.actionResults : List.of();
        this.conditionResolved = b.conditionResolved;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public int                depth()             { return depth; }
    public Source             getSource()         { return source; }
    public CheckerResult      getCheckerResult()  { return checkerResult; }
    public InsightResponse    getInsight()        { return insight; }
    public List<ActionResult> getActionResults()  { return actionResults; }
    public boolean            isConditionResolved() { return conditionResolved; }

    public boolean fromLocalChecker()  { return source == Source.LOCAL_CHECKER; }
    public boolean fromKnowledgeBase() { return source == Source.KNOWLEDGE_BASE; }
    public boolean fromClaudeApi()     { return source == Source.CLAUDE_API; }

    @Override
    public String toString() {
        return String.format("CascadeResult{depth=%d, source=%s, resolved=%b, insight=%s}",
            depth, source, conditionResolved,
            insight != null ? insight.getConditionCategory() : "null");
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private int                depth;
        private Source             source;
        private CheckerResult      checkerResult;
        private InsightResponse    insight;
        private List<ActionResult> actionResults;
        private boolean            conditionResolved;

        public Builder depth(int d)                        { this.depth = d; return this; }
        public Builder source(Source s)                    { this.source = s; return this; }
        public Builder checkerResult(CheckerResult r)      { this.checkerResult = r; return this; }
        public Builder insight(InsightResponse i)          { this.insight = i; return this; }
        public Builder actionResults(List<ActionResult> r) { this.actionResults = r; return this; }
        public Builder conditionResolved(boolean b)        { this.conditionResolved = b; return this; }
        public CascadeResult build()                       { return new CascadeResult(this); }
    }
}
