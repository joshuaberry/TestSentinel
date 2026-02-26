package com.testsentinel.executor;

import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Discovers and holds all {@link ActionHandler} implementations.
 *
 * At construction time the registry:
 *   1. Uses the Reflections library to scan {@code com.testsentinel.executor.handlers}
 *   2. Finds every class annotated with {@link HandlesAction}
 *   3. Instantiates each one via its no-arg constructor
 *   4. Registers it under the String name declared in the annotation
 *
 * Adding a new handler requires only:
 *   - Create a class implementing {@link ActionHandler} in the handlers package
 *   - Annotate it with {@code @HandlesAction("YOUR_ACTION_NAME")}
 *   - No changes to this registry or any other class
 *
 * Consumer test repositories can define their own action types as plain strings
 * without modifying the library's ActionType catalogue.
 *
 * Duplicate registrations (two handlers for the same action name) cause an
 * {@link IllegalStateException} at startup so the error is never silent.
 */
public class ActionHandlerRegistry {

    private static final Logger log = LoggerFactory.getLogger(ActionHandlerRegistry.class);
    private static final String HANDLERS_PACKAGE = "com.testsentinel.executor.handlers";

    private final Map<String, ActionHandler> registry = new HashMap<>();

    public ActionHandlerRegistry() {
        discoverAndRegister();
        log.info("ActionHandlerRegistry: {} handler(s) registered", registry.size());
    }

    // ── Lookup ────────────────────────────────────────────────────────────────

    /**
     * Returns the handler for the given action type name, or empty if none is registered.
     */
    public Optional<ActionHandler> find(String actionType) {
        return Optional.ofNullable(registry.get(actionType));
    }

    /**
     * Returns true if a handler is registered for the given action type name.
     */
    public boolean hasHandler(String actionType) {
        return registry.containsKey(actionType);
    }

    /**
     * Returns the number of registered handlers.
     */
    public int size() {
        return registry.size();
    }

    // ── Discovery ─────────────────────────────────────────────────────────────

    private void discoverAndRegister() {
        Reflections reflections = new Reflections(
            new ConfigurationBuilder()
                .forPackage(HANDLERS_PACKAGE)
                .setScanners(Scanners.TypesAnnotated)
        );

        Set<Class<?>> annotated = reflections.getTypesAnnotatedWith(HandlesAction.class);

        for (Class<?> cls : annotated) {
            HandlesAction annotation = cls.getAnnotation(HandlesAction.class);
            String actionType = annotation.value();

            if (!ActionHandler.class.isAssignableFrom(cls)) {
                throw new IllegalStateException(
                    "Class " + cls.getName() + " is annotated @HandlesAction(\"" + actionType +
                    "\") but does not implement ActionHandler");
            }

            if (registry.containsKey(actionType)) {
                throw new IllegalStateException(
                    "Duplicate handler for action type '" + actionType +
                    "': " + registry.get(actionType).getClass().getName() +
                    " and " + cls.getName());
            }

            try {
                var constructor = cls.getDeclaredConstructor();
                constructor.setAccessible(true);  // support package-private handlers
                ActionHandler handler = (ActionHandler) constructor.newInstance();
                registry.put(actionType, handler);
                log.debug("ActionHandlerRegistry: registered {} -> {}",
                    actionType, cls.getSimpleName());
            } catch (Exception e) {
                throw new IllegalStateException(
                    "Failed to instantiate handler " + cls.getName() +
                    " for action type '" + actionType + "'.", e);
            }
        }
    }
}
