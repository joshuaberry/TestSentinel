package com.testsentinel.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * A persistent record created when TestSentinel encounters a condition it cannot
 * classify using its local knowledge base or condition checkers.
 *
 * Records are written to the unknown condition log file (configured via
 * TESTSENTINEL_UNKNOWN_LOG_PATH or defaulting to target/unknown-conditions-log.json).
 *
 * ## Human Review Workflow
 *
 *   1. Run the test suite (offline mode).
 *   2. Run UnknownConditionReviewCli to send NEW records to the Claude AI.
 *      The CLI shows the AI analysis and prompts for Approve / Ignore / Skip.
 *        - Approved records get status=APPROVED with the AI analysis stored in
 *          the ai* fields for reference when creating KB patterns.
 *        - Ignored records get status=IGNORED.
 *   3. For each APPROVED record, optionally create a KB entry in known-conditions.json
 *      and set patternCreatedId to the new pattern's id (status=PATTERN_CREATED).
 *   4. Re-run the suite -- new KB patterns resolve the condition locally.
 *
 * ## Append-Only Contract
 *
 * TestSentinel only appends new records or increments hitCount on dedup.
 * It never deletes records. Engineers update status/notes/patternCreatedId manually.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UnknownConditionRecord {

    /** Human review status */
    public enum Status {
        NEW,             // Not yet reviewed
        REVIEWED,        // Reviewed; engineer added notes but no KB pattern yet
        PATTERN_CREATED, // KB pattern was created -- future occurrences resolve locally
        IGNORED          // One-off or noise; will not be patterned
    }

    // ── Identity ──────────────────────────────────────────────────────────────
    private String  id;           // UUID generated at record time
    private String  contentHash;  // Dedup key: hash of (conditionType+exceptionType+locatorValue+urlPath)
    private Instant recordedAt;
    private int     hitCount = 1; // Incremented on repeated identical condition

    // ── Diagnostic context (from ConditionEvent) ──────────────────────────────
    private String testName;
    private String suiteName;
    private String conditionType;
    private String message;
    private String currentUrl;
    private String locatorStrategy;
    private String locatorValue;
    private String exceptionType;
    private String stackTraceSummary; // First 5 lines only
    private String domSnippet;        // First 500 chars of DOM

    // ── Human review fields (set by engineer, not by TestSentinel) ────────────
    private Status  status        = Status.NEW;
    private String  reviewedBy;
    private Instant reviewedAt;
    private String  patternCreatedId; // KB pattern id if engineer created one
    private String  notes;

    public UnknownConditionRecord() {}

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public String  getId()                              { return id; }
    public void    setId(String id)                     { this.id = id; }

    public String  getContentHash()                     { return contentHash; }
    public void    setContentHash(String contentHash)   { this.contentHash = contentHash; }

    public Instant getRecordedAt()                      { return recordedAt; }
    public void    setRecordedAt(Instant recordedAt)    { this.recordedAt = recordedAt; }

    public int     getHitCount()                        { return hitCount; }
    public void    setHitCount(int hitCount)            { this.hitCount = hitCount; }
    public void    incrementHitCount()                  { this.hitCount++; }

    public String  getTestName()                        { return testName; }
    public void    setTestName(String testName)         { this.testName = testName; }

    public String  getSuiteName()                       { return suiteName; }
    public void    setSuiteName(String suiteName)       { this.suiteName = suiteName; }

    public String  getConditionType()                   { return conditionType; }
    public void    setConditionType(String conditionType) { this.conditionType = conditionType; }

    public String  getMessage()                         { return message; }
    public void    setMessage(String message)           { this.message = message; }

    public String  getCurrentUrl()                      { return currentUrl; }
    public void    setCurrentUrl(String currentUrl)     { this.currentUrl = currentUrl; }

    public String  getLocatorStrategy()                 { return locatorStrategy; }
    public void    setLocatorStrategy(String s)         { this.locatorStrategy = s; }

    public String  getLocatorValue()                    { return locatorValue; }
    public void    setLocatorValue(String locatorValue) { this.locatorValue = locatorValue; }

    public String  getExceptionType()                   { return exceptionType; }
    public void    setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }

    public String  getStackTraceSummary()               { return stackTraceSummary; }
    public void    setStackTraceSummary(String s)       { this.stackTraceSummary = s; }

    public String  getDomSnippet()                      { return domSnippet; }
    public void    setDomSnippet(String domSnippet)     { this.domSnippet = domSnippet; }

    public Status  getStatus()                          { return status; }
    public void    setStatus(Status status)             { this.status = status; }

    public String  getReviewedBy()                      { return reviewedBy; }
    public void    setReviewedBy(String reviewedBy)     { this.reviewedBy = reviewedBy; }

    public Instant getReviewedAt()                      { return reviewedAt; }
    public void    setReviewedAt(Instant reviewedAt)    { this.reviewedAt = reviewedAt; }

    public String  getPatternCreatedId()                { return patternCreatedId; }
    public void    setPatternCreatedId(String id)       { this.patternCreatedId = id; }

    public String  getNotes()                           { return notes; }
    public void    setNotes(String notes)               { this.notes = notes; }

    @Override
    public String toString() {
        return String.format("UnknownConditionRecord{id='%s', conditionType=%s, url='%s', status=%s, hits=%d}",
            id, conditionType, currentUrl, status, hitCount);
    }
}
