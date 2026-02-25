package com.testsentinel.executor.checker;

import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Discovers and holds all {@link ConditionChecker} implementations in priority order.
 *
 * At construction time the registry:
 *   1. Uses the Reflections library to scan {@code com.testsentinel.executor.checker.checks}
 *   2. Finds every class annotated with {@link ChecksCondition}
 *   3. Instantiates each one via its no-arg constructor
 *   4. Sorts by priority ascending (lower number = runs first)
 *
 * Adding a new checker requires only:
 *   - Create a class implementing {@link ConditionChecker} in the checks package
 *   - Annotate it with {@code @ChecksCondition(id = "my-checker", priority = N)}
 *   - No changes to this registry or any other class
 */
public class ConditionCheckerRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConditionCheckerRegistry.class);
    private static final String CHECKS_PACKAGE = "com.testsentinel.executor.checker.checks";

    private final List<ConditionChecker> checkers;

    public ConditionCheckerRegistry() {
        this.checkers = Collections.unmodifiableList(discoverAndSort());
        log.info("ConditionCheckerRegistry: {} checker(s) registered in priority order: {}",
            checkers.size(), checkerIds());
    }

    // ── Primary API ───────────────────────────────────────────────────────────

    /**
     * Returns all checkers in priority order (lowest priority number first).
     */
    public List<ConditionChecker> getCheckers() {
        return checkers;
    }

    /**
     * Returns the number of registered checkers.
     */
    public int size() {
        return checkers.size();
    }

    // ── Discovery ─────────────────────────────────────────────────────────────

    private List<ConditionChecker> discoverAndSort() {
        Reflections reflections = new Reflections(
            new ConfigurationBuilder()
                .forPackage(CHECKS_PACKAGE)
                .setScanners(Scanners.TypesAnnotated)
        );

        Set<Class<?>> annotated = reflections.getTypesAnnotatedWith(ChecksCondition.class);

        List<ConditionChecker> discovered = new ArrayList<>();
        Set<String> seenIds = new java.util.HashSet<>();

        for (Class<?> cls : annotated) {
            ChecksCondition annotation = cls.getAnnotation(ChecksCondition.class);
            String id = annotation.id();

            if (!ConditionChecker.class.isAssignableFrom(cls)) {
                throw new IllegalStateException(
                    "Class " + cls.getName() + " is annotated @ChecksCondition(id=\"" + id +
                    "\") but does not implement ConditionChecker");
            }

            if (seenIds.contains(id)) {
                throw new IllegalStateException(
                    "Duplicate checker id \"" + id + "\" found in " + cls.getName());
            }
            seenIds.add(id);

            try {
                var constructor = cls.getDeclaredConstructor();
                constructor.setAccessible(true);  // support package-private checkers
                ConditionChecker checker = (ConditionChecker) constructor.newInstance();
                discovered.add(checker);
                log.debug("ConditionCheckerRegistry: registered '{}' (priority={}) -> {}",
                    id, annotation.priority(), cls.getSimpleName());
            } catch (Exception e) {
                throw new IllegalStateException(
                    "Failed to instantiate checker " + cls.getName() + ".", e);
            }
        }

        // Sort by priority from annotation (lower = first)
        discovered.sort((a, b) -> {
            int pa = a.getClass().getAnnotation(ChecksCondition.class).priority();
            int pb = b.getClass().getAnnotation(ChecksCondition.class).priority();
            return Integer.compare(pa, pb);
        });

        return discovered;
    }

    private List<String> checkerIds() {
        return checkers.stream()
            .map(c -> c.getClass().getAnnotation(ChecksCondition.class).id())
            .toList();
    }
}
