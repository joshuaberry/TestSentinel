# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

Maven and Java are not on PATH. Use these full paths:

```bash
export JAVA_HOME="/c/Users/joshu/.jdks/openjdk-25.0.2"
MVN="/c/Program Files/JetBrains/IntelliJ IDEA 2025.3.1/plugins/maven/lib/maven3/bin/mvn"

# Compile only
"$MVN" compile test-compile

# Full test suite (44 tests: Cucumber BDD + TestNG unit tests)
"$MVN" test

# Run a single Cucumber tag
"$MVN" test -Dcucumber.filter.tags=@knowledge-base

# Run headlessly
"$MVN" test -Dheadless=true

# Online mode (requires API key)
TESTSENTINEL_OFFLINE_MODE=false ANTHROPIC_API_KEY=sk-ant-... "$MVN" test
```

Surefire is configured to pick up two entry points: `**/CucumberRunner.java` (BDD) and `**/*UnitTest.java` (fast unit tests, no WebDriver).

Tests hit `https://the-internet.herokuapp.com` — an internet connection is required.

## Architecture

### Resolution cascade (the core idea)

Every Selenium exception flows through three tiers in order, stopping at the first match:

```
1. Local Checkers   — pattern-match against live driver state (0ms, 0 tokens)
2. Knowledge Base   — substring-score against known-conditions.json (0ms, 0 tokens)
3. Claude API       — LLM root-cause analysis (disabled by default, costs tokens)
```

`CascadedInsightEngine` orchestrates the full loop: run a pass → execute action plan → re-run checkers to verify resolution → repeat up to `maxDepth=3`. `TestSentinelClient` handles the KB→API fallback portion (steps 2 and 3 above) and is what the BDD tests use directly.

### Plugin discovery (reflection-based, zero wiring)

Both registries use the Reflections library to scan a fixed package at startup:

| Registry | Annotation | Scans package |
|---|---|---|
| `ConditionCheckerRegistry` | `@ChecksCondition(id, priority)` | `com.testsentinel.executor.checker.checks` |
| `ActionHandlerRegistry` | `@HandlesAction(ActionType.X)` | `com.testsentinel.executor.handlers` |

To add a new checker: implement `ConditionChecker`, annotate with `@ChecksCondition`, drop into the `checks` package. No other files change. Same pattern for handlers. Both registries call `constructor.setAccessible(true)` so package-private classes work fine.

### Knowledge Base matching

`KnownConditionRepository` scores each enabled `KnownCondition` by counting how many signal fields are non-null and match the `ConditionEvent`. Available signals: `urlPattern`, `locatorValuePattern`, `conditionType`, `exceptionType`, `messageContains`, `domContains`. A pattern is a candidate when `score >= minMatchSignals`. All patterns in `known-conditions.json` use `minMatchSignals=3` — never lower this, it causes false positives.

**Chrome CSS-escape gotcha**: ChromeDriver reports `#foo\-bar` (backslash-escaped hyphens) in `NoSuchElementException` messages. `ContextCollector.enrichLocatorContext()` strips these with `.replaceAll("\\\\(.)", "$1")` before KB matching. KB `locatorValuePattern` fields should use plain hyphens.

### Key files

| File | Role |
|---|---|
| `core/TestSentinelConfig.java` | All config; `fromEnvironment()` reads env vars; default is `offlineMode=true` |
| `core/TestSentinelClient.java` | Primary API: `analyzeException()`, `analyzeWrongPage()`, `analyzeEvent()`, `addPattern()` |
| `executor/CascadedInsightEngine.java` | Full cascade loop (checkers → KB → API → action → re-check) |
| `interceptor/TestSentinelListener.java` | Selenium 4 `WebDriverListener`; attach via `EventFiringDecorator` |
| `core/KnownConditionRepository.java` | Loads/matches/persists `known-conditions.json`; thread-safe; atomic file writes |
| `core/UnknownConditionRecorder.java` | Writes `target/unknown-conditions-log.json`; deduplicates by content hash |
| `util/ContextCollector.java` | Builds `ConditionEvent` from live driver state (DOM, screenshot, console logs) |
| `executor/checker/checks/OtherCheckers.java` | Houses multiple package-private checkers in one file (stale, auth, wrong-page, assertion, hidden) |

### Test structure

The BDD suite (`src/test/resources/features/`) is numbered by feature area:
- `01_happy_path` — driver + listener wiring
- `02_local_analysis` — KB matching; confirms 0 tokens, <100ms, `isLocalResolution()`
- `03_knowledge_base` — direct `addPattern()` and reuse
- `04_navigation_continue` — `WRONG_PAGE` condition → `CONTINUE` outcome
- `05_unknown_condition_recording` — unmatched conditions → `unknown-conditions-log.json`
- `06_autonomous_action` — `CAPTURE_SCREENSHOT` LOW-risk auto-execution

`Hooks.java` creates a **fresh `TestSentinelClient` per scenario** using `SentinelFactory`. This provides KB isolation between scenarios. `SuiteHooks.java` holds only the shared `TestSentinelConfig`.

`CheckerUnitTest.java` (TestNG, same package as checkers for package-private access) tests all checkers without a WebDriver.

## Dependency notes

- **OkHttp 5.x / KMP**: The `okhttp` Maven artifact is a 767-byte KMP stub. Maven Java projects must declare `okhttp-jvm` as the artifact ID. Package names are unchanged (`okhttp3.*`).
- **Jackson annotations**: Since Jackson 2.20, `jackson-annotations` drops the patch version. Use `jackson.annotations.version=2.21` (no `.x`) as a separate POM property alongside `jackson.version=2.21.1`.
