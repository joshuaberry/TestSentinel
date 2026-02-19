package com.testsentinel.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.testsentinel.core.TestSentinelConfig;
import com.testsentinel.model.InsightResponse;
import com.testsentinel.prompt.PromptEngine;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Manages all communication with the Anthropic Claude API.
 *
 * Responsibilities:
 *  - Constructs the API request payload
 *  - Submits to https://api.anthropic.com/v1/messages
 *  - Handles HTTP errors and retries (1 retry on 529 / 5xx)
 *  - Parses and validates the response
 *  - Extracts token usage for cost monitoring
 *
 * Thread-safe. The OkHttpClient is shared across calls.
 */
public class ClaudeApiGateway {

    private static final Logger log = LoggerFactory.getLogger(ClaudeApiGateway.class);

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private final TestSentinelConfig config;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final PromptEngine promptEngine;

    public ClaudeApiGateway(TestSentinelConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(config.getTimeoutSeconds(), TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules(); // Registers JavaTimeModule for Instant
        this.promptEngine = new PromptEngine();
    }

    /**
     * Calls the Claude API with the prepared user content blocks and returns
     * a parsed InsightResponse.
     *
     * @param userContent  Content blocks built by PromptEngine.buildUserContent()
     * @return InsightResponse parsed from Claude's JSON output
     */
    public InsightResponse analyze(List<Map<String, Object>> userContent) {
        long startMs = System.currentTimeMillis();

        try {
            String requestBody = buildRequestBody(userContent);

            if (config.isLogPrompts()) {
                log.debug("TestSentinel API Request:\n{}", requestBody);
            }

            String responseBody = executeWithRetry(requestBody, 2);
            long latencyMs = System.currentTimeMillis() - startMs;

            return parseResponse(responseBody, latencyMs);

        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startMs;
            log.error("TestSentinel: Claude API call failed after {}ms: {}", latencyMs, e.getMessage());
            return InsightResponse.error(e.getMessage(), latencyMs);
        }
    }

    // ── Request Building ──────────────────────────────────────────────────────

    private String buildRequestBody(List<Map<String, Object>> userContent) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", config.getModel());
        root.put("max_tokens", config.getMaxTokens());

        // Use Phase 2 system prompt when phase2 is enabled, Phase 1 otherwise
        String systemPrompt = config.isPhase2Enabled()
            ? PromptEngine.SYSTEM_PROMPT_PHASE2
            : PromptEngine.SYSTEM_PROMPT_PHASE1;
        root.put("system", systemPrompt);

        // Messages array
        ArrayNode messages = root.putArray("messages");
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");

        // Content array
        ArrayNode contentArray = userMessage.putArray("content");
        for (Map<String, Object> block : userContent) {
            contentArray.addPOJO(block);
        }

        return objectMapper.writeValueAsString(root);
    }

    // ── HTTP Execution with Retry ─────────────────────────────────────────────

    private String executeWithRetry(String requestBody, int maxAttempts) throws IOException {
        IOException lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Request request = new Request.Builder()
                    .url(ANTHROPIC_API_URL)
                    .addHeader("x-api-key", config.getApiKey())
                    .addHeader("anthropic-version", ANTHROPIC_VERSION)
                    .addHeader("content-type", "application/json")
                    .post(RequestBody.create(requestBody, JSON_MEDIA_TYPE))
                    .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    String body = response.body() != null ? response.body().string() : "";

                    if (response.isSuccessful()) {
                        return body;
                    }

                    // Rate limited (429) or overloaded (529) — wait and retry
                    if ((response.code() == 429 || response.code() == 529) && attempt < maxAttempts) {
                        log.warn("TestSentinel: API returned {} on attempt {}. Retrying in 5s...",
                            response.code(), attempt);
                        Thread.sleep(5000);
                        continue;
                    }

                    throw new IOException("Anthropic API returned HTTP " + response.code() +
                        ": " + body.substring(0, Math.min(200, body.length())));
                }
            } catch (IOException e) {
                lastException = e;
                if (attempt < maxAttempts) {
                    log.warn("TestSentinel: API call attempt {} failed: {}. Retrying...",
                        attempt, e.getMessage());
                    try { Thread.sleep(2000); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted during retry wait", ie);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted during retry wait", e);
            }
        }
        throw lastException != null ? lastException : new IOException("All retry attempts exhausted");
    }

    // ── Response Parsing ──────────────────────────────────────────────────────

    private InsightResponse parseResponse(String responseBody, long latencyMs) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // Extract token usage
            int inputTokens = 0, outputTokens = 0;
            if (root.has("usage")) {
                JsonNode usage = root.get("usage");
                inputTokens = usage.path("input_tokens").asInt(0);
                outputTokens = usage.path("output_tokens").asInt(0);
            }
            int totalTokens = inputTokens + outputTokens;

            // Extract the text content from Claude's response
            String claudeText = extractTextContent(root);

            if (config.isLogPrompts()) {
                log.debug("TestSentinel API Response ({}ms, {} tokens):\n{}", latencyMs, totalTokens, claudeText);
            }

            // Clean and parse the JSON response
            String cleanJson = cleanJsonResponse(claudeText);
            InsightResponse insight = objectMapper.readValue(cleanJson, InsightResponse.class);
            insight.setAnalysisTokens(totalTokens);
            insight.setAnalysisLatencyMs(latencyMs);
            insight.setAnalyzedAt(Instant.now());
            insight.setRawClaudeResponse(claudeText);

            // Ensure conditionId is set even if Claude omits it
            if (insight.getConditionId() == null || insight.getConditionId().isBlank()) {
                insight.setConditionId(UUID.randomUUID().toString());
            }

            log.info("TestSentinel: Analysis complete — category={}, confidence={}%, transient={}, latency={}ms, tokens={}",
                insight.getConditionCategory(),
                Math.round(insight.getConfidence() * 100),
                insight.isTransient(),
                latencyMs,
                totalTokens);

            return insight;

        } catch (Exception e) {
            log.error("TestSentinel: Failed to parse Claude response: {}", e.getMessage());
            log.debug("TestSentinel: Raw response was: {}", responseBody.substring(0, Math.min(500, responseBody.length())));
            return InsightResponse.error("Response parse failure: " + e.getMessage(), latencyMs);
        }
    }

    private String extractTextContent(JsonNode root) {
        // Claude response structure: { content: [ { type: "text", text: "..." } ] }
        if (root.has("content") && root.get("content").isArray()) {
            for (JsonNode block : root.get("content")) {
                if ("text".equals(block.path("type").asText())) {
                    return block.path("text").asText("");
                }
            }
        }
        return root.toString();
    }

    /**
     * Strips markdown code fences if Claude includes them despite instructions not to.
     * Claude is very good about this with the right system prompt, but defensive parsing
     * is always appropriate.
     */
    private String cleanJsonResponse(String text) {
        if (text == null) return "{}";
        text = text.trim();

        // Strip ```json ... ``` or ``` ... ```
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                text = text.substring(firstNewline + 1, lastFence).trim();
            }
        }

        // Find the JSON object boundaries (defensive — handles any leading text)
        int jsonStart = text.indexOf('{');
        int jsonEnd = text.lastIndexOf('}');
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            text = text.substring(jsonStart, jsonEnd + 1);
        }

        return text;
    }
}
