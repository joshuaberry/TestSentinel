package com.testsentinel.prompt;

import com.testsentinel.model.ConditionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Builds the structured prompt sent to Claude for Phase 1 analysis.
 *
 * Prompt architecture (in order):
 *  1. System prompt  — establishes Claude's role and output schema
 *  2. User message   — serialized ConditionEvent using XML tags for field delineation
 *
 * The output schema is enforced in the system prompt. Claude is instructed to
 * return ONLY a JSON object matching the InsightResponse structure — no preamble,
 * no markdown fences.
 */
public class PromptEngine {

    private static final Logger log = LoggerFactory.getLogger(PromptEngine.class);

    // ── System Prompt ─────────────────────────────────────────────────────────

    public static final String SYSTEM_PROMPT = """
        You are a Senior Test Automation Diagnostician embedded in an enterprise test automation framework.
        Your role is to analyze unexpected conditions encountered during automated test execution and provide
        structured, evidence-based root cause analysis.

        You have deep expertise in:
        - Web application rendering, DOM lifecycle, and browser behavior
        - Selenium WebDriver failure modes (stale elements, timing, overlays, navigation)
        - Common causes of test flakiness in CI/CD environments
        - HTTP/network error patterns and their impact on UI automation
        - Authentication and session management in automated testing

        ## Analysis Approach
        1. Examine all provided evidence: URL, DOM snapshot, screenshot (if provided), console logs, prior steps
        2. Identify the most likely root cause based on concrete evidence — do not speculate beyond what the data shows
        3. Classify the condition into exactly one category
        4. Note specific DOM patterns, error messages, or URL characteristics that support your conclusion
        5. Assess whether the condition is transient (likely to self-resolve on retry) or persistent

        ## Condition Categories
        - OVERLAY: Modal dialog, cookie consent banner, notification popup, ad overlay blocking interaction
        - LOADING: Page still rendering — spinner visible, skeleton screen, pending XHR/fetch, document.readyState not complete
        - STALE_DOM: Element was found but has been detached due to re-render, SPA route change, or dynamic update
        - NAVIGATION: Test is on wrong page — unexpected redirect, auth redirect, error page, 404
        - INFRA: Server slow or unavailable — timeout, 5xx response, CDN failure, high response time
        - AUTH: Session expired, login wall appeared, CSRF token mismatch
        - TEST_DATA: Expected data not present — empty state, different user context, environment-specific data issue
        - FLAKE: Non-deterministic race condition — element appears and disappears, timing-sensitive interaction
        - APPLICATION_BUG: Genuine defect — element has been removed from the DOM, broken user flow, JavaScript error
        - UNKNOWN: Insufficient evidence to classify with confidence

        ## Suggested Outcomes
        - RETRY: Condition is transient; retrying the action immediately or after a brief wait is likely to succeed
        - SKIP: Condition blocks this specific test but not the entire suite; skip with enriched context
        - FAIL_WITH_CONTEXT: Genuine application issue; fail the test and attach this analysis to the report
        - INVESTIGATE: Ambiguous; flag for human review

        ## Output Format
        Return ONLY a valid JSON object — no markdown code fences, no preamble, no explanation outside the JSON.
        The JSON must exactly match this schema:

        {
          "conditionId": "<generate a UUID v4>",
          "conditionCategory": "<one of the categories above>",
          "rootCause": "<clear, specific, evidence-based explanation in 1-3 sentences>",
          "confidence": <float between 0.0 and 1.0>,
          "evidenceHighlights": [
            "<specific DOM element, CSS class, URL pattern, or log entry that supports your analysis>",
            "<another concrete observation>"
          ],
          "isTransient": <true or false>,
          "suggestedTestOutcome": "<RETRY | SKIP | FAIL_WITH_CONTEXT | INVESTIGATE>"
        }

        If evidence is insufficient for confident classification, use UNKNOWN category with confidence < 0.4
        and suggestedTestOutcome INVESTIGATE.
        """;

    // ── User Message Builder ──────────────────────────────────────────────────

    /**
     * Builds the user message content array for the Claude API call.
     * Returns a list of content blocks — text, and optionally an image block
     * if a screenshot is present in the event.
     */
    public List<Map<String, Object>> buildUserContent(ConditionEvent event) {
        StringBuilder text = new StringBuilder();

        text.append("<condition_event>\n\n");

        // Condition type and message
        text.append("<condition_type>").append(event.getConditionType()).append("</condition_type>\n");
        text.append("<message>").append(escapeXml(event.getMessage())).append("</message>\n\n");

        // URL context
        text.append("<current_url>").append(event.getCurrentUrl()).append("</current_url>\n");
        if (event.getExpectedUrl() != null) {
            text.append("<expected_url>").append(event.getExpectedUrl()).append("</expected_url>\n");
        }

        // Locator context
        if (event.getLocatorStrategy() != null) {
            text.append("\n<locator_context>\n");
            text.append("  Strategy: ").append(event.getLocatorStrategy()).append("\n");
            text.append("  Value: ").append(event.getLocatorValue()).append("\n");
            text.append("</locator_context>\n");
        }

        // Prior test steps (critical for understanding what the test was trying to do)
        if (event.getPriorSteps() != null && !event.getPriorSteps().isEmpty()) {
            text.append("\n<prior_test_steps>\n");
            for (int i = 0; i < event.getPriorSteps().size(); i++) {
                text.append("  Step ").append(i + 1).append(": ").append(event.getPriorSteps().get(i)).append("\n");
            }
            text.append("</prior_test_steps>\n");
        }

        // Console logs
        if (event.getConsoleLogs() != null && !event.getConsoleLogs().isEmpty()) {
            text.append("\n<browser_console_logs>\n");
            event.getConsoleLogs().forEach(l -> text.append("  ").append(l).append("\n"));
            text.append("</browser_console_logs>\n");
        }

        // Stack trace
        if (event.getStackTrace() != null) {
            text.append("\n<stack_trace>\n").append(event.getStackTrace()).append("</stack_trace>\n");
        }

        // DOM snapshot (always last in text — largest block)
        if (event.getDomSnapshot() != null) {
            text.append("\n<dom_snapshot>\n").append(event.getDomSnapshot()).append("\n</dom_snapshot>\n");
        }

        text.append("\n</condition_event>\n\n");
        text.append("Analyze the condition above and return the JSON insight object.");

        // Build content array — text first, then screenshot image if available
        if (event.getScreenshotBase64() != null) {
            // With screenshot: vision analysis enabled
            return List.of(
                Map.of("type", "text", "text", text.toString()),
                Map.of(
                    "type", "image",
                    "source", Map.of(
                        "type", "base64",
                        "media_type", "image/png",
                        "data", event.getScreenshotBase64()
                    )
                ),
                Map.of("type", "text", "text",
                    "The image above is a screenshot of the browser at the time the condition occurred. " +
                    "Use it to identify visual clues: overlays, spinners, error messages, unexpected content.")
            );
        } else {
            // Text-only analysis
            return List.of(Map.of("type", "text", "text", text.toString()));
        }
    }

    private String escapeXml(String input) {
        if (input == null) return "";
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
