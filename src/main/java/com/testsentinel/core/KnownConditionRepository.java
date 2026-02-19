package com.testsentinel.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.testsentinel.model.ConditionEvent;
import com.testsentinel.model.KnownCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Loads, matches, and persists KnownCondition patterns.
 *
 * Patterns are stored in a JSON file on disk and held in memory at runtime.
 * Matching runs entirely in-process — no network, no API call.
 *
 * ## Thread Safety
 * Uses CopyOnWriteArrayList for the in-memory store. findExactMatch() is safe
 * to call from parallel test threads. recordHit() and add() synchronize on
 * persist() which uses atomic file write semantics.
 *
 * ## Loading
 * Loaded once at construction. Call reload() to pick up hand-edits to the JSON
 * file without restarting the test suite (e.g., from a @BeforeSuite hook).
 *
 * ## Scoring
 * Each matching non-null signal contributes 1 point to the score.
 * A pattern is a candidate when score >= pattern.getMinMatchSignals().
 * Among all candidates, the highest scorer wins. Ties broken by hitCount desc
 * (more battle-tested patterns are preferred).
 *
 * ## Signal Fields Evaluated
 *   urlPattern          — substring of ConditionEvent.currentUrl
 *   locatorValuePattern — substring of ConditionEvent.locatorValue
 *   exceptionType       — substring of ConditionEvent.stackTrace
 *   domContains         — substring of ConditionEvent.domSnapshot
 *   conditionType       — exact match of ConditionEvent.conditionType.name()
 *   messageContains     — substring of ConditionEvent.message
 */
public class KnownConditionRepository {

    private static final Logger log = LoggerFactory.getLogger(KnownConditionRepository.class);

    private final Path storePath;
    private final ObjectMapper mapper;
    private final CopyOnWriteArrayList<KnownCondition> conditions = new CopyOnWriteArrayList<>();

    public KnownConditionRepository(Path storePath) {
        this.storePath = storePath;
        this.mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);
        reload();
    }

    // ── Load / Reload ─────────────────────────────────────────────────────────

    /**
     * (Re)loads all patterns from the JSON file on disk.
     * Disabled patterns (enabled=false) are filtered out of the active set.
     * Safe to call at runtime — replaces the in-memory list atomically.
     */
    public void reload() {
        if (!Files.exists(storePath)) {
            log.info("TestSentinel KnowledgeBase: no file at {} — starting empty", storePath);
            return;
        }
        try {
            List<KnownCondition> loaded = mapper.readValue(
                storePath.toFile(),
                mapper.getTypeFactory().constructCollectionType(List.class, KnownCondition.class)
            );
            List<KnownCondition> active = loaded.stream()
                .filter(KnownCondition::isEnabled)
                .toList();
            conditions.clear();
            conditions.addAll(active);
            log.info("TestSentinel KnowledgeBase: loaded {} active patterns ({} total) from {}",
                active.size(), loaded.size(), storePath);
        } catch (IOException e) {
            log.warn("TestSentinel KnowledgeBase: failed to load {}: {}", storePath, e.getMessage());
        }
    }

    // ── Matching ──────────────────────────────────────────────────────────────

    /**
     * Returns the best-matching KnownCondition for the given event, or empty if
     * no pattern meets its own minMatchSignals threshold.
     *
     * When a match is found, call recordHit(match) to increment statistics.
     */
    public Optional<KnownCondition> findExactMatch(ConditionEvent event) {
        if (conditions.isEmpty()) return Optional.empty();

        KnownCondition best = null;
        int bestScore = -1;
        int bestHits = -1;

        for (KnownCondition kc : conditions) {
            int score = score(kc, event);
            if (score >= kc.getMinMatchSignals()) {
                // Prefer higher score; break ties by hitCount (more proven = preferred)
                if (score > bestScore || (score == bestScore && kc.getHitCount() > bestHits)) {
                    best = kc;
                    bestScore = score;
                    bestHits = kc.getHitCount();
                }
            }
        }

        if (best != null) {
            log.debug("TestSentinel KnowledgeBase: matched '{}' (score={}/{})",
                best.getId(), bestScore, countDefinedSignals(best));
        }
        return Optional.ofNullable(best);
    }

    /**
     * Returns all patterns sorted by hitCount descending.
     * Useful for building admin dashboards or reporting.
     */
    public List<KnownCondition> findAll() {
        return conditions.stream()
            .sorted(Comparator.comparingInt(KnownCondition::getHitCount).reversed())
            .toList();
    }

    // ── Mutation ──────────────────────────────────────────────────────────────

    /**
     * Increments the hit counter and last-hit timestamp for a matched pattern,
     * then persists the updated list to disk.
     */
    public synchronized void recordHit(KnownCondition kc) {
        kc.incrementHitCount();
        kc.setLastHit(Instant.now());
        persist();
    }

    /**
     * Adds a new pattern to the active set and persists immediately.
     * If a pattern with the same id already exists, it is replaced.
     */
    public synchronized void add(KnownCondition kc) {
        conditions.removeIf(existing -> existing.getId() != null
            && existing.getId().equals(kc.getId()));
        conditions.add(kc);
        persist();
        log.info("TestSentinel KnowledgeBase: pattern '{}' saved ({} total active)",
            kc.getId(), conditions.size());
    }

    /**
     * Disables a pattern by id without removing it from the file.
     * The pattern remains in the JSON for audit purposes but is excluded from matching.
     */
    public synchronized void disable(String id) {
        conditions.stream()
            .filter(kc -> id.equals(kc.getId()))
            .findFirst()
            .ifPresent(kc -> {
                kc.setEnabled(false);
                conditions.remove(kc);  // Remove from active set
                persist();
                log.info("TestSentinel KnowledgeBase: pattern '{}' disabled", id);
            });
    }

    public int size() { return conditions.size(); }

    // ── Scoring ───────────────────────────────────────────────────────────────

    private int score(KnownCondition kc, ConditionEvent event) {
        int score = 0;

        if (kc.getUrlPattern() != null && event.getCurrentUrl() != null
                && event.getCurrentUrl().contains(kc.getUrlPattern())) {
            score++;
        }
        if (kc.getLocatorValuePattern() != null && event.getLocatorValue() != null
                && event.getLocatorValue().contains(kc.getLocatorValuePattern())) {
            score++;
        }
        if (kc.getExceptionType() != null && event.getStackTrace() != null
                && event.getStackTrace().contains(kc.getExceptionType())) {
            score++;
        }
        if (kc.getDomContains() != null && event.getDomSnapshot() != null
                && event.getDomSnapshot().contains(kc.getDomContains())) {
            score++;
        }
        if (kc.getConditionType() != null && event.getConditionType() != null
                && kc.getConditionType().equals(event.getConditionType().name())) {
            score++;
        }
        if (kc.getMessageContains() != null && event.getMessage() != null
                && event.getMessage().contains(kc.getMessageContains())) {
            score++;
        }
        return score;
    }

    private int countDefinedSignals(KnownCondition kc) {
        int count = 0;
        if (kc.getUrlPattern() != null) count++;
        if (kc.getLocatorValuePattern() != null) count++;
        if (kc.getExceptionType() != null) count++;
        if (kc.getDomContains() != null) count++;
        if (kc.getConditionType() != null) count++;
        if (kc.getMessageContains() != null) count++;
        return count;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void persist() {
        try {
            // Write to temp file then rename for atomic replacement
            Path tmp = storePath.resolveSibling(storePath.getFileName() + ".tmp");

            // Persist all patterns including disabled ones from the original file
            // so they are preserved. Re-read original to get disabled set.
            List<KnownCondition> allPatterns = new ArrayList<>(conditions);
            if (Files.exists(storePath)) {
                try {
                    List<KnownCondition> onDisk = mapper.readValue(
                        storePath.toFile(),
                        mapper.getTypeFactory().constructCollectionType(List.class, KnownCondition.class)
                    );
                    // Add back any disabled patterns not in active set
                    onDisk.stream()
                        .filter(kc -> !kc.isEnabled())
                        .filter(kc -> allPatterns.stream().noneMatch(a -> a.getId() != null
                            && a.getId().equals(kc.getId())))
                        .forEach(allPatterns::add);
                } catch (IOException ignored) {}
            }

            mapper.writeValue(tmp.toFile(), allPatterns);
            Files.move(tmp, storePath,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("TestSentinel KnowledgeBase: failed to persist: {}", e.getMessage());
        }
    }
}
