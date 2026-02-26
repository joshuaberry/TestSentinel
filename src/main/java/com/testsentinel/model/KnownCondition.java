package com.testsentinel.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * A single engineer-maintained pattern that TestSentinel can resolve locally
 * without calling the Claude API.
 *
 * When a ConditionEvent matches a KnownCondition with sufficient signal agreement,
 * the InsightResponse is constructed directly from the stored fields -- zero API cost,
 * sub-millisecond resolution.
 *
 * ## Lifecycle
 * 1. A condition fires. Claude analyzes it (first occurrence -- API call made).
 * 2. The resolution is confirmed as correct by an engineer.
 * 3. sentinel.recordResolution(event, insight, "pattern-id", "engineer") promotes
 *    it to a KnownCondition and persists it to known-conditions.json.
 * 4. All future occurrences of the same pattern resolve locally.
 *
 * ## Matching Signals
 * All signal fields are optional. A pattern is considered a match when the
 * number of non-null signals that agree >= minMatchSignals (default: 2).
 * String signals use "contains" matching (case-sensitive).
 *
 * Recommended signal combinations:
 *   - urlPattern + locatorValuePattern          (locator failure on known page)
 *   - urlPattern + domContains                  (known overlay on known page)
 *   - exceptionType + domContains               (known error with known DOM marker)
 *   - conditionType + urlPattern + domContains  (highest specificity, minMatchSignals=3)
 *
 * ## Storage
 * Persisted as a JSON array in TESTSENTINEL_KNOWLEDGE_BASE_PATH.
 * Hand-editable. Use enabled=false to temporarily disable a pattern without deleting it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class KnownCondition {

    // ── Identity ──────────────────────────────────────────────────────────────
    private String id;           // Short human-readable key, e.g. "cookie-banner-checkout"
    private String description;  // One-sentence description for logs and admin review
    private boolean enabled;     // Set false to disable without deleting

    // ── Matching Signals ──────────────────────────────────────────────────────
    // All optional. "contains" match unless noted otherwise.
    private String urlPattern;           // Substring of ConditionEvent.currentUrl
    private String locatorValuePattern;  // Substring of ConditionEvent.locatorValue
    private String exceptionType;        // Simple class name in ConditionEvent.stackTrace, e.g. "NoSuchElementException"
    private String domContains;          // Substring of ConditionEvent.domSnapshot
    private String conditionType;        // Exact match of ConditionEvent.conditionType.name()
    private String messageContains;      // Substring of ConditionEvent.message
    private int minMatchSignals;         // Signals that must agree for a hit (default: 2)

    // ── Resolution (returned verbatim when matched) ───────────────────────────
    private String conditionCategory;        // InsightResponse.ConditionCategory name
    private String rootCause;               // Plain-English explanation
    private List<String> evidenceHighlights; // Concrete observations
    private boolean isTransient;
    private String suggestedTestOutcome;     // InsightResponse.SuggestedOutcome name
    private ActionPlan actionPlan;           // Fully pre-built Phase 2 action plan; may be null
    private ContinueContext continueContext; // Populated when suggestedTestOutcome=CONTINUE

    // ── Metadata ──────────────────────────────────────────────────────────────
    private int hitCount;     // Incremented each time this pattern resolves a condition
    private Instant lastHit;
    private String addedBy;
    private Instant addedAt;
    private String notes;     // Free-text engineer notes -- not used in matching

    public KnownCondition() {
        this.enabled = true;
        this.minMatchSignals = 2;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getDescription() { return description; }
    public boolean isEnabled() { return enabled; }
    public String getUrlPattern() { return urlPattern; }
    public String getLocatorValuePattern() { return locatorValuePattern; }
    public String getExceptionType() { return exceptionType; }
    public String getDomContains() { return domContains; }
    public String getConditionType() { return conditionType; }
    public String getMessageContains() { return messageContains; }
    public int getMinMatchSignals() { return minMatchSignals; }
    public String getConditionCategory() { return conditionCategory; }
    public String getRootCause() { return rootCause; }
    public List<String> getEvidenceHighlights() { return evidenceHighlights; }
    public boolean isTransient() { return isTransient; }
    public String getSuggestedTestOutcome() { return suggestedTestOutcome; }
    public ActionPlan getActionPlan() { return actionPlan; }
    public ContinueContext getContinueContext() { return continueContext; }
    public int getHitCount() { return hitCount; }
    public Instant getLastHit() { return lastHit; }
    public String getAddedBy() { return addedBy; }
    public Instant getAddedAt() { return addedAt; }
    public String getNotes() { return notes; }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setId(String id) { this.id = id; }
    public void setDescription(String description) { this.description = description; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setUrlPattern(String urlPattern) { this.urlPattern = urlPattern; }
    public void setLocatorValuePattern(String locatorValuePattern) { this.locatorValuePattern = locatorValuePattern; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }
    public void setDomContains(String domContains) { this.domContains = domContains; }
    public void setConditionType(String conditionType) { this.conditionType = conditionType; }
    public void setMessageContains(String messageContains) { this.messageContains = messageContains; }
    public void setMinMatchSignals(int minMatchSignals) { this.minMatchSignals = minMatchSignals; }
    public void setConditionCategory(String conditionCategory) { this.conditionCategory = conditionCategory; }
    public void setRootCause(String rootCause) { this.rootCause = rootCause; }
    public void setEvidenceHighlights(List<String> evidenceHighlights) { this.evidenceHighlights = evidenceHighlights; }
    public void setTransient(boolean transient_) { this.isTransient = transient_; }
    public void setSuggestedTestOutcome(String suggestedTestOutcome) { this.suggestedTestOutcome = suggestedTestOutcome; }
    public void setActionPlan(ActionPlan actionPlan) { this.actionPlan = actionPlan; }
    public void setContinueContext(ContinueContext continueContext) { this.continueContext = continueContext; }
    public void setHitCount(int hitCount) { this.hitCount = hitCount; }
    public void setLastHit(Instant lastHit) { this.lastHit = lastHit; }
    public void setAddedBy(String addedBy) { this.addedBy = addedBy; }
    public void setAddedAt(Instant addedAt) { this.addedAt = addedAt; }
    public void setNotes(String notes) { this.notes = notes; }

    // ── Convenience ───────────────────────────────────────────────────────────

    /** Called by KnownConditionRepository each time this pattern resolves a condition */
    public void incrementHitCount() { this.hitCount++; }

    @Override
    public String toString() {
        return String.format("KnownCondition{id='%s', hits=%d, enabled=%b, minSignals=%d}",
            id, hitCount, enabled, minMatchSignals);
    }
}
