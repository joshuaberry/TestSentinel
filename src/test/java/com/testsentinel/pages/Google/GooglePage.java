package com.testsentinel.pages.Google;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * Page Object for the Google homepage and search results page.
 *
 * Keeps all locators in one place. Step definitions call methods here --
 * they never interact with WebDriver directly.
 *
 * Google's DOM structure changes periodically. If a locator breaks, update
 * only this class.
 */
public class GooglePage {

    private static final Logger log = LoggerFactory.getLogger(GooglePage.class);

    private static final String GOOGLE_URL = "https://www.google.com";

    // ── Locators ──────────────────────────────────────────────────────────────
    // Google uses several possible selectors for the search bar across regions
    private static final By SEARCH_BAR_PRIMARY   = By.name("q");
    private static final By SEARCH_BAR_TEXTAREA  = By.cssSelector("textarea[name='q']");
    private static final By SEARCH_BAR_INPUT     = By.cssSelector("input[name='q']");
    private static final By SEARCH_RESULTS       = By.cssSelector("#search .g");
    private static final By ACCEPT_COOKIES_BTN   = By.cssSelector("#L2AGLb, #W0wltc, button[id*='accept']");
    private static final By GOOGLE_SEARCH_BTN    = By.cssSelector("input[name='btnK'], button[aria-label='Google Search']");

    private final WebDriver driver;
    private final WebDriverWait wait;

    public GooglePage(WebDriver driver) {
        this.driver = driver;
        this.wait   = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    public void open() {
        driver.get(GOOGLE_URL);
        dismissCookieBannerIfPresent();
    }

    public String getCurrentUrl() {
        return driver.getCurrentUrl();
    }

    public String getPageTitle() {
        return driver.getTitle();
    }

    // ── Search Bar ────────────────────────────────────────────────────────────

    /**
     * Returns true if the search bar is visible on the current page.
     * Does not throw if the element is absent.
     */
    public boolean isSearchBarVisible() {
        List<WebElement> elements = driver.findElements(SEARCH_BAR_TEXTAREA);
        if (elements.isEmpty()) elements = driver.findElements(SEARCH_BAR_INPUT);
        if (elements.isEmpty()) elements = driver.findElements(SEARCH_BAR_PRIMARY);
        return !elements.isEmpty() && elements.get(0).isDisplayed();
    }

    /**
     * Waits for the search bar and returns it. Tries multiple known locators.
     */
    public WebElement getSearchBar() {
        try {
            return wait.until(ExpectedConditions.visibilityOfElementLocated(SEARCH_BAR_TEXTAREA));
        } catch (Exception e) {
            try {
                return wait.until(ExpectedConditions.visibilityOfElementLocated(SEARCH_BAR_INPUT));
            } catch (Exception e2) {
                return wait.until(ExpectedConditions.visibilityOfElementLocated(SEARCH_BAR_PRIMARY));
            }
        }
    }

    /**
     * Types into the search bar. Clears any existing content first.
     */
    public void typeInSearchBar(String text) {
        WebElement bar = getSearchBar();
        bar.clear();
        bar.sendKeys(text);
    }

    /**
     * Returns the current text content of the search bar.
     */
    public String getSearchBarText() {
        return getSearchBar().getAttribute("value");
    }

    /**
     * Submits the search by clicking the "Google Search" button.
     * Falls back to pressing Enter if the button is not visible (Google hides
     * it until focus moves away from the search bar on some layouts).
     */
    public void submitSearch() {
        // Move focus away from the search bar so Google reveals the search buttons
        try {
            WebElement bar = getSearchBar();
            bar.sendKeys(Keys.TAB);
        } catch (Exception ignored) {}

        try {
            WebElement btn = wait.until(ExpectedConditions.elementToBeClickable(GOOGLE_SEARCH_BTN));
            btn.click();
            log.info("GooglePage: Search submitted via Google Search button");
        } catch (Exception e) {
            log.info("GooglePage: Search button not clickable -- falling back to Enter key");
            getSearchBar().sendKeys(Keys.ENTER);
        }
        waitForResultsPage();
    }

    // ── Search Results ────────────────────────────────────────────────────────

    public void waitForResultsPage() {
        wait.until(ExpectedConditions.titleContains("Google"));
    }

    public List<WebElement> getSearchResults() {
        wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(SEARCH_RESULTS));
        return driver.findElements(SEARCH_RESULTS);
    }

    public int getSearchResultCount() {
        return getSearchResults().size();
    }

    // ── Cookie Banner ─────────────────────────────────────────────────────────

    /**
     * Dismisses the cookie consent banner if it appears.
     * Safe to call even when no banner is present.
     */
    public void dismissCookieBannerIfPresent() {
        try {
            List<WebElement> btns = driver.findElements(ACCEPT_COOKIES_BTN);
            if (!btns.isEmpty() && btns.get(0).isDisplayed()) {
                btns.get(0).click();
                log.info("GooglePage: Cookie banner dismissed");
            }
        } catch (Exception ignored) {
            // Banner was not present or disappeared before we could click
        }
    }
}
