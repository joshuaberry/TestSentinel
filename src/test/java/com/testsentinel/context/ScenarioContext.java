package com.testsentinel.context;

import com.testsentinel.core.ActionPlanAdvisor;
import com.testsentinel.core.TestSentinelClient;
import com.testsentinel.core.TestSentinelConfig;
import com.testsentinel.interceptor.TestSentinelListener;
import com.testsentinel.model.ConditionEvent;
import com.testsentinel.model.InsightResponse;
import org.openqa.selenium.WebDriver;

/**
 * Shared state for a single Cucumber scenario.
 *
 * Injected by Cucumber's PicoContainer dependency injection into every step
 * definition class that declares it as a constructor parameter. One instance
 * is created per scenario, torn down in the @After hook.
 *
 * Holds:
 *   driver         -- the WebDriver for the current scenario
 *   sentinel       -- the TestSentinelClient (shared across all scenarios)
 *   advisor        -- the ActionPlanAdvisor (shared across all scenarios)
 *   listener       -- the per-scenario listener (resets between scenarios)
 *   lastInsight    -- the most recently produced InsightResponse
 *   lastEvent      -- the most recently analyzed ConditionEvent (for KB promotion)
 *   apiKeyPresent  -- whether ANTHROPIC_API_KEY is available in the environment
 */
public class ScenarioContext {

    // ── Set once by Hooks.beforeScenario(), used by all step definitions ──────
    private WebDriver driver;
    private TestSentinelListener listener;

    // ── Set in Hooks.beforeSuite() via WorldConfigurator, shared across scenarios ──
    // In Cucumber + PicoContainer there is no true @BeforeSuite scope, so these
    // are stored as instance fields on ScenarioContext and re-initialised if null.
    private TestSentinelClient sentinel;
    private ActionPlanAdvisor  advisor;
    private TestSentinelConfig config;

    // ── Per-scenario output from TestSentinel ─────────────────────────────────
    private InsightResponse lastInsight;
    private ConditionEvent  lastEvent;    // Set manually when calling analyzeWrongPage

    // ── Flags ─────────────────────────────────────────────────────────────────
    private boolean apiKeyPresent;
    private boolean phase2Enabled;

    public ScenarioContext() {
        this.apiKeyPresent = isApiKeyPresent();
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public WebDriver getDriver()              { return driver; }
    public void      setDriver(WebDriver d)   { this.driver = d; }

    public TestSentinelListener getListener()                { return listener; }
    public void                 setListener(TestSentinelListener l) { this.listener = l; }

    public TestSentinelClient getSentinel()                  { return sentinel; }
    public void               setSentinel(TestSentinelClient s) { this.sentinel = s; }

    public ActionPlanAdvisor getAdvisor()                    { return advisor; }
    public void              setAdvisor(ActionPlanAdvisor a) { this.advisor = a; }

    public TestSentinelConfig getConfig()                    { return config; }
    public void               setConfig(TestSentinelConfig c) { this.config = c; }

    public InsightResponse getLastInsight()                  { return lastInsight; }
    public void            setLastInsight(InsightResponse i) { this.lastInsight = i; }

    public ConditionEvent  getLastEvent()                    { return lastEvent; }
    public void            setLastEvent(ConditionEvent e)    { this.lastEvent = e; }

    public boolean isApiKeyPresent()  {
        String key = System.getenv("ANTHROPIC_API_KEY");
        return key != null && !key.isBlank();
    }

    public boolean isPhase2Enabled()            { return phase2Enabled; }
    public void    setPhase2Enabled(boolean b)  { this.phase2Enabled = b; }

    /**
     * Pulls the latest insight from the listener (auto-interception path) and
     * stores it in lastInsight. Call this in step definitions after an action
     * that may have triggered TestSentinel via EventFiringDecorator.
     */
    public void syncInsightFromListener() {
        if (listener != null && listener.getLastInsight() != null) {
            this.lastInsight = listener.getLastInsight();
        }
    }
}
