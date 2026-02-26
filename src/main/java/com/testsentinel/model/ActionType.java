package com.testsentinel.model;

/**
 * Catalogue of built-in action type names used by the TestSentinel library.
 *
 * These string constants are the names that the library's built-in handlers
 * are registered under. They appear as the {@code actionType} field in JSON
 * action plans and as the value of {@code @HandlesAction} on each handler.
 *
 * Consumer test repositories can define any additional action type by:
 *   1. Creating a class implementing {@link com.testsentinel.executor.ActionHandler}
 *      in the {@code com.testsentinel.executor.handlers} package of their test source tree
 *   2. Annotating it {@code @HandlesAction("MY_CUSTOM_ACTION")} -- any string name
 *   3. Adding that action type string to their known-condition JSON action plans
 *
 * No modification to this enum or any library class is required.
 *
 * Risk levels are documented here as guidance for the risk-gating system:
 *   LOW    -- safe to execute autonomously, no side effects beyond the current page
 *   MEDIUM -- side effects possible; require explicit opt-in config
 *   HIGH   -- data mutation, navigation away, or external system calls; always require opt-in
 */
public enum ActionType {

    // ── Browser Interaction ──────────────────────────────────────────────────
    CLICK,              // Click an element by selector                      LOW
    CLICK_IF_PRESENT,   // Click an element only if it exists (safe dismiss)  LOW
    WAIT_FOR_ELEMENT,   // Wait until an element is present/visible           LOW
    WAIT_FOR_URL,       // Wait until the URL matches a pattern               LOW
    WAIT_FIXED,         // Wait a fixed number of milliseconds                LOW
    SCROLL_TO_ELEMENT,  // Scroll element into view                           LOW
    SCROLL_TO_TOP,      // Scroll page to top                                 LOW
    DISMISS_OVERLAY,    // Dismiss overlay via Escape key or close button     LOW
    ACCEPT_ALERT,       // Accept a browser alert/confirm dialog              LOW
    DISMISS_ALERT,      // Dismiss a browser alert/confirm dialog             LOW
    REFRESH_PAGE,       // Reload the current page                            MEDIUM
    NAVIGATE_BACK,      // Click browser back button                          MEDIUM
    NAVIGATE_TO,        // Navigate to a specific URL                         MEDIUM
    EXECUTE_SCRIPT,     // Execute JavaScript in browser context              HIGH

    // ── Wait Strategies ──────────────────────────────────────────────────────
    RETRY_ACTION,       // Retry the original failing action                  LOW
    CLEAR_COOKIES,      // Clear browser cookies and reload                   MEDIUM
    SWITCH_TO_FRAME,    // Switch WebDriver focus to an iframe                LOW
    SWITCH_TO_DEFAULT,  // Switch WebDriver focus back to main frame          LOW

    // ── Investigation / APM (Phase 3 external calls) ─────────────────────────
    QUERY_APM,          // Query Dynatrace, Datadog, or New Relic             LOW (read-only)
    CAPTURE_HAR,        // Capture HTTP Archive for network analysis          LOW
    CAPTURE_SCREENSHOT, // Take an additional diagnostic screenshot           LOW

    // ── Test Flow Control ────────────────────────────────────────────────────
    SKIP_STEP,
    SKIP_TEST,          // Mark test as skipped with enriched reason          MEDIUM
    ABORT_SUITE,        // Abort the entire test suite                        HIGH

    // ── Custom ───────────────────────────────────────────────────────────────
    CUSTOM              // Framework-specific action handled by custom adapter HIGH
}
