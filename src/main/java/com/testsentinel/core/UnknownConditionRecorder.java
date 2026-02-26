package com.testsentinel.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.testsentinel.model.ConditionEvent;
import com.testsentinel.model.UnknownConditionRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Records conditions that could not be classified by local checkers or the knowledge base.
 *
 * Each unmatched condition is appended to a JSON log file for human review.
 * Duplicate conditions (same type + locator + URL path) increment a hitCount counter
 * rather than creating new records, keeping the log focused on distinct failure types.
 *
 * ## Thread Safety
 * All writes are synchronized. The underlying file uses atomic rename semantics.
 *
 * ## File Format
 * JSON array of UnknownConditionRecord objects, pretty-printed.
 * Engineers hand-edit status, notes, and patternCreatedId fields after review.
 */
public class UnknownConditionRecorder {

    private static final Logger log = LoggerFactory.getLogger(UnknownConditionRecorder.class);
    private static final int DOM_SNIPPET_MAX = 500;
    private static final int STACK_LINES_MAX = 5;

    private final Path logPath;
    private final ObjectMapper mapper;

    public UnknownConditionRecorder(Path logPath) {
        this.logPath = logPath;
        this.mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

        // Ensure parent directories exist
        try {
            Path parent = logPath.getParent();
            if (parent != null) Files.createDirectories(parent);
        } catch (IOException e) {
            log.warn("UnknownConditionRecorder: Could not create parent directories for {}: {}", logPath, e.getMessage());
        }
    }

    // ── Primary API ───────────────────────────────────────────────────────────

    /**
     * Records an unmatched condition. If a record with the same content hash already
     * exists, increments its hitCount instead of creating a duplicate.
     *
     * @return the record that was created or updated
     */
    public synchronized UnknownConditionRecord record(ConditionEvent event) {
        List<UnknownConditionRecord> records = loadRecords();

        String hash = computeHash(event);

        // Check for existing record with same hash
        for (UnknownConditionRecord existing : records) {
            if (hash.equals(existing.getContentHash())) {
                // Don't re-record if a KB pattern was already created for this
                if (existing.getStatus() == UnknownConditionRecord.Status.PATTERN_CREATED) {
                    log.debug("UnknownConditionRecorder: Skipping record -- pattern '{}' already created for hash {}",
                        existing.getPatternCreatedId(), hash);
                    return existing;
                }
                existing.incrementHitCount();
                save(records);
                log.info("UnknownConditionRecorder: Hit #{} for existing record '{}' ({})",
                    existing.getHitCount(), existing.getId(), existing.getConditionType());
                return existing;
            }
        }

        // Create new record
        UnknownConditionRecord record = buildRecord(event, hash);
        records.add(record);
        save(records);

        log.info("UnknownConditionRecorder: New unknown condition recorded -- id={}, type={}, url={}",
            record.getId(), record.getConditionType(), record.getCurrentUrl());
        log.info("UnknownConditionRecorder: Review at {} -- set status to PATTERN_CREATED after adding KB entry",
            logPath.toAbsolutePath());

        return record;
    }

    /**
     * Returns all recorded conditions. Safe to call from tests for assertions.
     */
    public List<UnknownConditionRecord> getRecords() {
        return Collections.unmodifiableList(loadRecords());
    }

    /**
     * Marks a record as reviewed by an engineer.
     */
    public synchronized void markReviewed(String id, String reviewerName, String notes) {
        List<UnknownConditionRecord> records = loadRecords();
        for (UnknownConditionRecord r : records) {
            if (id.equals(r.getId())) {
                r.setStatus(UnknownConditionRecord.Status.REVIEWED);
                r.setReviewedBy(reviewerName);
                r.setReviewedAt(Instant.now());
                r.setNotes(notes);
                save(records);
                log.info("UnknownConditionRecorder: Record '{}' marked REVIEWED by {}", id, reviewerName);
                return;
            }
        }
        log.warn("UnknownConditionRecorder: Record '{}' not found for markReviewed", id);
    }

    /**
     * Marks a record as having a corresponding KB pattern created.
     * Future occurrences of this condition should resolve via the KB instead.
     */
    public synchronized void markPatternCreated(String id, String patternId) {
        List<UnknownConditionRecord> records = loadRecords();
        for (UnknownConditionRecord r : records) {
            if (id.equals(r.getId())) {
                r.setStatus(UnknownConditionRecord.Status.PATTERN_CREATED);
                r.setPatternCreatedId(patternId);
                r.setReviewedAt(Instant.now());
                save(records);
                log.info("UnknownConditionRecorder: Record '{}' linked to KB pattern '{}'", id, patternId);
                return;
            }
        }
        log.warn("UnknownConditionRecorder: Record '{}' not found for markPatternCreated", id);
    }

    /** Returns the path to the log file. */
    public Path getLogPath() { return logPath; }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private UnknownConditionRecord buildRecord(ConditionEvent event, String hash) {
        UnknownConditionRecord r = new UnknownConditionRecord();
        r.setId(UUID.randomUUID().toString());
        r.setContentHash(hash);
        r.setRecordedAt(Instant.now());
        r.setHitCount(1);
        r.setStatus(UnknownConditionRecord.Status.NEW);

        if (event.getConditionType() != null)   r.setConditionType(event.getConditionType().name());
        if (event.getMessage() != null)          r.setMessage(truncate(event.getMessage(), 500));
        if (event.getCurrentUrl() != null)       r.setCurrentUrl(event.getCurrentUrl());
        if (event.getLocatorStrategy() != null)  r.setLocatorStrategy(event.getLocatorStrategy());
        if (event.getLocatorValue() != null)     r.setLocatorValue(event.getLocatorValue());
        if (event.getDomSnapshot() != null)      r.setDomSnippet(truncate(event.getDomSnapshot(), DOM_SNIPPET_MAX));

        if (event.getStackTrace() != null) {
            String exType = extractExceptionType(event.getStackTrace());
            if (exType != null) r.setExceptionType(exType);
            r.setStackTraceSummary(firstNLines(event.getStackTrace(), STACK_LINES_MAX));
        }

        // Pull test name and suite from framework meta if available
        if (event.getFrameworkMeta() != null) {
            r.setTestName(event.getFrameworkMeta().get("testName"));
            r.setSuiteName(event.getFrameworkMeta().get("suiteName"));
        }

        return r;
    }

    /**
     * Content hash used for deduplication.
     * Hashes: conditionType + exceptionType + locatorValue + URL path segment.
     */
    private String computeHash(ConditionEvent event) {
        try {
            String condType = event.getConditionType() != null ? event.getConditionType().name() : "";
            String exType   = event.getStackTrace() != null ? extractExceptionType(event.getStackTrace()) : "";
            if (exType == null) exType = "";
            String locator  = event.getLocatorValue() != null ? event.getLocatorValue() : "";
            String urlPath  = extractPath(event.getCurrentUrl());

            String key = condType + "|" + exType + "|" + locator + "|" + urlPath;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.substring(0, 16); // 16 hex chars is sufficient for dedup
        } catch (Exception e) {
            return UUID.randomUUID().toString().substring(0, 16);
        }
    }

    private String extractExceptionType(String stackTrace) {
        if (stackTrace == null) return null;
        int colon = stackTrace.indexOf(':');
        int newline = stackTrace.indexOf('\n');
        int end = (colon > 0 && (newline < 0 || colon < newline)) ? colon : newline;
        if (end <= 0) return null;
        String fqn = stackTrace.substring(0, end).trim();
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }

    private String extractPath(String url) {
        if (url == null) return "";
        try {
            java.net.URI uri = new java.net.URI(url);
            return uri.getPath() != null ? uri.getPath() : "";
        } catch (Exception e) {
            return url.contains("/") ? url.substring(url.indexOf('/')) : "";
        }
    }

    private String firstNLines(String text, int n) {
        if (text == null) return null;
        String[] lines = text.split("\n");
        int count = Math.min(n, lines.length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(lines[i]);
            if (i < count - 1) sb.append("\n");
        }
        if (lines.length > n) sb.append("\n... ").append(lines.length - n).append(" more lines");
        return sb.toString();
    }

    private String truncate(String text, int max) {
        if (text == null || text.length() <= max) return text;
        return text.substring(0, max) + "...";
    }

    // ── File I/O ──────────────────────────────────────────────────────────────

    private List<UnknownConditionRecord> loadRecords() {
        if (!Files.exists(logPath)) return new ArrayList<>();
        try {
            return mapper.readValue(
                logPath.toFile(),
                mapper.getTypeFactory().constructCollectionType(List.class, UnknownConditionRecord.class)
            );
        } catch (IOException e) {
            log.warn("UnknownConditionRecorder: Failed to load {}: {}", logPath, e.getMessage());
            return new ArrayList<>();
        }
    }

    private void save(List<UnknownConditionRecord> records) {
        try {
            Path tmp = logPath.resolveSibling(logPath.getFileName() + ".tmp");
            mapper.writeValue(tmp.toFile(), records);
            Files.move(tmp, logPath,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("UnknownConditionRecorder: Failed to save records to {}: {}", logPath, e.getMessage());
        }
    }
}
