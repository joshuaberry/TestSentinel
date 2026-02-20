package com.testsentinel;

import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import org.testng.annotations.DataProvider;

/**
 * TestNG entry point for the Cucumber test suite.
 *
 * AbstractTestNGCucumberTests discovers this class and runs all scenarios
 * whose feature files match the path in {@code features}.
 *
 * ## Running subsets
 *
 *   All tests:
 *     mvn test
 *
 *   Smoke only:
 *     mvn test -Dcucumber.filter.tags="@smoke"
 *
 *   Happy path only:
 *     mvn test -Dcucumber.filter.tags="@happy-path"
 *
 *   TestSentinel analysis tests only (requires API key):
 *     mvn test -Dcucumber.filter.tags="@claude-analysis"
 *
 *   Knowledge base tests only:
 *     mvn test -Dcucumber.filter.tags="@knowledge-base"
 *
 *   Skip tests requiring a live API key:
 *     mvn test -Dcucumber.filter.tags="not @sentinel"
 *
 * ## Parallel execution
 *   Set parallel=true in the @DataProvider to run scenarios in parallel threads.
 *   TestSentinelClient is thread-safe; ScenarioContext is per-thread.
 *
 * ## Reports
 *   HTML report:  target/cucumber-reports/cucumber-pretty.html
 *   JSON report:  target/cucumber-reports/CucumberTestReport.json
 */
@CucumberOptions(
    features = "src/test/resources/features",
    glue     = "com.testsentinel.steps",   // Scans com.example.steps, com.example.hooks, com.example.context
    plugin   = {
        "pretty",
        "html:target/cucumber-reports/cucumber-pretty.html",
        "json:target/cucumber-reports/CucumberTestReport.json",
        "timeline:target/cucumber-reports/timeline"
    },
    monochrome = false,
    publish    = false          // Set true to publish to cucumber.io (requires token)
)
public class CucumberRunner extends AbstractTestNGCucumberTests {

    /**
     * Override to enable parallel scenario execution.
     * Set parallel = true and configure thread count in testng.xml if desired.
     */
    @Override
    @DataProvider(parallel = false)
    public Object[][] scenarios() {
        return super.scenarios();
    }
}
