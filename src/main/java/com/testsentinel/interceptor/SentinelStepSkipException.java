package com.testsentinel.interceptor;

import com.testsentinel.model.InsightResponse;

/**
 * Signals that TestSentinel has determined the current step should be skipped
 * because the condition is not a genuine failure (e.g., session-cookie bypass).
 *
 * <h3>How skip propagation works</h3>
 *
 * <p>Selenium 4's {@code EventFiringDecorator} always re-throws the original
 * Selenium exception after calling {@code onError()} — it does not propagate
 * exceptions thrown by the listener. Skip propagation therefore relies on
 * Java's ordinary exception mechanism:
 *
 * <ol>
 *   <li>The first Selenium call in the workflow (e.g. {@code findElement(#username)})
 *       throws {@code NoSuchElementException}.</li>
 *   <li>The decorator calls {@link TestSentinelListener#onError} which stores the
 *       analysis result in {@link TestSentinelListener#getLastInsight()}.</li>
 *   <li>The {@code NoSuchElementException} propagates to the step definition's
 *       {@code catch(NoSuchElementException)} block — all subsequent operations
 *       in the same {@code try} block (e.g. {@code findElement(#password)},
 *       {@code click(button)}) are <em>already skipped</em> by the exception.</li>
 *   <li>The step definition checks {@code insight.isContinuable()}:
 *       <ul>
 *         <li>If {@code true} → throw {@code SentinelStepSkipException} to signal
 *             "step was intentionally skipped" to any surrounding framework code,
 *             or simply return normally so the step passes.</li>
 *         <li>If {@code false} → re-throw the original exception as a genuine failure.</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h3>Recommended step-definition pattern</h3>
 * <pre>
 *   {@literal @}When("the test performs the complete login workflow")
 *   public void loginWorkflow() {
 *       try {
 *           // All three operations in one try block.
 *           // When on /secure, findElement(#username) throws NoSuchElementException;
 *           // findElement(#password) and click(button) are never reached.
 *           driver.findElement(By.id("username")).sendKeys(username);
 *           driver.findElement(By.id("password")).sendKeys(password);
 *           driver.findElement(By.cssSelector("button")).click();
 *       } catch (NoSuchElementException e) {
 *           sentinel.syncInsight(); // pull analysis result from listener
 *           InsightResponse insight = sentinel.getLastInsight();
 *           if (insight != null && insight.isContinuable()) {
 *               log.info("Login step skipped — {}", insight.getRootCause());
 *               // Step PASSES — throw SentinelStepSkipException here if you want
 *               // surrounding framework code to know the step was intentionally skipped.
 *               throw SentinelStepSkipException.from(insight, e);
 *           }
 *           throw e; // genuine failure
 *       }
 *   }
 * </pre>
 *
 * <h3>Page object pattern</h3>
 * <p>If the step definition calls a page object that internally performs multiple
 * driver operations (e.g. {@code page.login(username, password)}), the same mechanism
 * applies — the {@code NoSuchElementException} from the first failing call propagates
 * through the page object to the step definition, skipping all subsequent page object
 * calls on the way out.
 */
public class SentinelStepSkipException extends RuntimeException {

    private final InsightResponse insight;

    private SentinelStepSkipException(InsightResponse insight, Throwable originalCause) {
        super("TestSentinel: step skipped — " + insight.getRootCause(), originalCause);
        this.insight = insight;
    }

    /**
     * Creates a {@code SentinelStepSkipException} wrapping the given insight and
     * the original Selenium exception that triggered analysis.
     */
    public static SentinelStepSkipException from(InsightResponse insight, Throwable originalCause) {
        return new SentinelStepSkipException(insight, originalCause);
    }

    /** The full TestSentinel analysis that determined the step should be skipped. */
    public InsightResponse getInsight() {
        return insight;
    }

    /**
     * Shorthand for {@code getInsight().isContinuable()}.
     * {@code true} means the test has already reached its goal — the step should
     * be treated as successfully complete rather than failed or pending.
     */
    public boolean isContinuable() {
        return insight.isContinuable();
    }
}
