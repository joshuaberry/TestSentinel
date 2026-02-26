package com.testsentinel.hooks;

import com.testsentinel.core.TestSentinelClient;
import com.testsentinel.core.TestSentinelConfig;
import com.testsentinel.model.InsightResponse;
import com.testsentinel.support.SentinelFactory;
import io.cucumber.java.AfterAll;
import io.cucumber.java.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Suite-level Cucumber hooks -- @BeforeAll and @AfterAll only.
 *
 * Builds and validates shared config at suite start. Per-scenario
 * TestSentinelClient instances are created fresh in Hooks.java to
 * provide knowledge base isolation between scenarios.
 *
 * Rule: any class containing @BeforeAll or @AfterAll must have a public
 * no-arg constructor (or no explicit constructor at all).
 */
public class SuiteHooks {

    private static final Logger log = LoggerFactory.getLogger(SuiteHooks.class);

    // Shared config -- read-only after @BeforeAll; scenarios create their own clients
    static TestSentinelConfig sharedConfig;

    // Accumulated per-scenario reports, registered by Hooks.@After for the suite summary
    private static final Queue<ScenarioReport> scenarioReports = new ConcurrentLinkedQueue<>();

    /**
     * One row in the suite summary -- the scenario name paired with every insight
     * analysis that was performed during that scenario.
     */
    public record ScenarioReport(String scenarioName, List<TestSentinelClient.InsightRecord> history) {}

    public SuiteHooks() {}

    /** Called by Hooks.@After to accumulate per-scenario data for the suite summary. */
    static void register(String scenarioName, List<TestSentinelClient.InsightRecord> history) {
        scenarioReports.add(new ScenarioReport(scenarioName, new ArrayList<>(history)));
    }

    @BeforeAll
    public static void initSentinel() {
        sharedConfig = SentinelFactory.buildConfig();
        scenarioReports.clear();
        log.info("SuiteHooks @BeforeAll: TestSentinel config ready -- offline={}, phase2={}, KB={}",
            sharedConfig.isOfflineMode(),
            sharedConfig.isPhase2Enabled(),
            sharedConfig.isKnowledgeBaseEnabled() ? sharedConfig.getKnowledgeBasePath() : "disabled");
        log.info("SuiteHooks @BeforeAll: Each scenario gets a fresh TestSentinelClient for KB isolation");
    }

    @AfterAll
    public static void reportSentinelStats() {
        logSuiteSummary();
        if (sharedConfig != null && sharedConfig.isUnknownConditionLogEnabled()) {
            log.info("SuiteHooks @AfterAll: Review unknown conditions at {}",
                sharedConfig.getUnknownConditionLogPath().toAbsolutePath());
        }
    }

    // ── Suite summary ─────────────────────────────────────────────────────────

    private static void logSuiteSummary() {
        List<ScenarioReport> reports = new ArrayList<>(scenarioReports);
        long totalAnalyses = reports.stream().mapToLong(r -> r.history().size()).sum();

        log.info("+================================================================+");
        log.info("|           TestSentinel Suite Summary                           |");
        log.info("+================================================================+");
        log.info("|  Scenarios: {}  |  Total analyses: {}", reports.size(), totalAnalyses);

        if (totalAnalyses == 0) {
            log.info("|  No insights were tripped across any scenario.");
            log.info("+================================================================+");
            return;
        }

        // Aggregate across all scenarios
        Map<String, int[]> aggregateInsights = new LinkedHashMap<>();
        Map<String, int[]> aggregateActions  = new LinkedHashMap<>();

        for (ScenarioReport report : reports) {
            for (var rec : report.history()) {
                String key = insightKey(rec.insight());
                aggregateInsights.computeIfAbsent(key, k -> new int[]{0})[0]++;
                if (!rec.actions().isEmpty() && rec.insight().hasActionPlan()) {
                    var steps = rec.insight().getActionPlan().getActions();
                    for (int i = 0; i < rec.actions().size() && i < steps.size(); i++) {
                        String ak = key + " > " + steps.get(i).getActionType()
                            + " -> " + rec.actions().get(i).getOutcome();
                        aggregateActions.computeIfAbsent(ak, k -> new int[]{0})[0]++;
                    }
                }
            }
        }

        log.info("|  Unique patterns: {}", aggregateInsights.size());
        log.info("+-- Aggregate ---------------------------------------------------+");
        for (var entry : aggregateInsights.entrySet()) {
            log.info("|    [{}] -- {} hit(s)", entry.getKey(), entry.getValue()[0]);
        }
        if (!aggregateActions.isEmpty()) {
            log.info("|  Actions:");
            for (var ae : aggregateActions.entrySet()) {
                log.info("|    {} -- {} time(s)", ae.getKey(), ae.getValue()[0]);
            }
        }

        // By scenario
        log.info("+-- By Scenario -------------------------------------------------+");
        for (ScenarioReport report : reports) {
            if (report.history().isEmpty()) {
                log.info("|  [{}]: no insights tripped", report.scenarioName());
                continue;
            }

            // Group this scenario's history by insight key
            Map<String, List<TestSentinelClient.InsightRecord>> grouped = new LinkedHashMap<>();
            for (var rec : report.history()) {
                grouped.computeIfAbsent(insightKey(rec.insight()), k -> new ArrayList<>()).add(rec);
            }

            log.info("|  Scenario: {}", report.scenarioName());
            for (var entry : grouped.entrySet()) {
                var recs     = entry.getValue();
                var ins      = recs.get(0).insight();
                String cntSuffix = recs.size() > 1 ? " x" + recs.size() : "";
                log.info("|    [{}] {} -> {}{}", entry.getKey(),
                    ins.getConditionCategory(),
                    ins.getSuggestedTestOutcome() != null ? ins.getSuggestedTestOutcome() : "N/A",
                    cntSuffix);

                Map<String, int[]> actionCounts = new LinkedHashMap<>();
                for (var rec : recs) {
                    if (!rec.actions().isEmpty() && rec.insight().hasActionPlan()) {
                        var steps = rec.insight().getActionPlan().getActions();
                        for (int i = 0; i < rec.actions().size() && i < steps.size(); i++) {
                            String ak = steps.get(i).getActionType()
                                + " -> " + rec.actions().get(i).getOutcome();
                            actionCounts.computeIfAbsent(ak, k -> new int[]{0})[0]++;
                        }
                    }
                }
                if (actionCounts.isEmpty()) {
                    log.info("|      Actions: (none)");
                } else {
                    for (var ae : actionCounts.entrySet()) {
                        String acntSuffix = ae.getValue()[0] > 1 ? " x" + ae.getValue()[0] : "";
                        log.info("|      Action: {}{}", ae.getKey(), acntSuffix);
                    }
                }
            }
        }

        log.info("+================================================================+");
    }

    private static String insightKey(InsightResponse insight) {
        if (insight.isLocalResolution()) {
            return insight.getResolvedFromPattern() != null
                ? insight.getResolvedFromPattern() : "LOCAL-UNKNOWN";
        }
        return insight.getAnalysisTokens() == 0
            ? "OFFLINE:" + insight.getConditionCategory()
            : "API:" + insight.getConditionCategory();
    }
}
