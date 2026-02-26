package com.testsentinel.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * Page Object for https://the-internet.herokuapp.com
 *
 * Covers the pages used by the TestSentinel demo scenarios:
 *   /           -- home page (list of examples)
 *   /login      -- Form Authentication (happy path + navigation scenarios)
 *   /secure     -- post-login landing page
 *   /checkboxes -- Checkboxes page (used for missing-element scenarios)
 */
public class InternetPage {

    private static final Logger log = LoggerFactory.getLogger(InternetPage.class);

    public static final String BASE_URL      = "https://the-internet.herokuapp.com";
    public static final String LOGIN_URL     = BASE_URL + "/login";
    public static final String SECURE_URL    = BASE_URL + "/secure";
    public static final String CHECKBOXES_URL = BASE_URL + "/checkboxes";

    // ── Home page ──────────────────────────────────────────────────────────────
    private static final By HOME_HEADING     = By.cssSelector("h1.heading");
    private static final By EXAMPLE_LINKS    = By.cssSelector("ul li a");

    // ── Login page ─────────────────────────────────────────────────────────────
    private static final By USERNAME_FIELD   = By.id("username");
    private static final By PASSWORD_FIELD   = By.id("password");
    private static final By LOGIN_BUTTON     = By.cssSelector("button[type='submit']");
    private static final By FLASH_MESSAGE    = By.id("flash");

    // ── Secure page (post-login) ───────────────────────────────────────────────
    private static final By LOGOUT_BUTTON    = By.cssSelector("a.button.secondary");
    private static final By SECURE_HEADING   = By.cssSelector("h2");

    // ── Checkboxes page ────────────────────────────────────────────────────────
    private static final By CHECKBOXES       = By.cssSelector("#checkboxes input[type='checkbox']");

    private final WebDriver driver;
    private final WebDriverWait wait;

    public InternetPage(WebDriver driver) {
        this.driver = driver;
        this.wait   = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    // ── Navigation ─────────────────────────────────────────────────────────────

    public void openHome() {
        driver.get(BASE_URL);
        log.info("InternetPage: Opened home -- title='{}'", driver.getTitle());
    }

    public void openLogin() {
        driver.get(LOGIN_URL);
        log.info("InternetPage: Opened login page");
    }

    public void openCheckboxes() {
        driver.get(CHECKBOXES_URL);
        log.info("InternetPage: Opened checkboxes page");
    }

    public String getCurrentUrl() {
        return driver.getCurrentUrl();
    }

    public String getPageTitle() {
        return driver.getTitle();
    }

    // ── Home page ──────────────────────────────────────────────────────────────

    public boolean isHomePageLoaded() {
        List<WebElement> els = driver.findElements(HOME_HEADING);
        return !els.isEmpty() && els.get(0).isDisplayed();
    }

    public int getExampleLinkCount() {
        return driver.findElements(EXAMPLE_LINKS).size();
    }

    // ── Login page ─────────────────────────────────────────────────────────────

    public boolean isLoginPageLoaded() {
        List<WebElement> els = driver.findElements(USERNAME_FIELD);
        return !els.isEmpty() && els.get(0).isDisplayed();
    }

    public void enterUsername(String username) {
        WebElement field = wait.until(ExpectedConditions.visibilityOfElementLocated(USERNAME_FIELD));
        field.clear();
        field.sendKeys(username);
    }

    public void enterPassword(String password) {
        WebElement field = wait.until(ExpectedConditions.visibilityOfElementLocated(PASSWORD_FIELD));
        field.clear();
        field.sendKeys(password);
    }

    public void clickLoginButton() {
        wait.until(ExpectedConditions.elementToBeClickable(LOGIN_BUTTON)).click();
        log.info("InternetPage: Login button clicked");
    }

    public void login(String username, String password) {
        enterUsername(username);
        enterPassword(password);
        clickLoginButton();
    }

    public String getFlashMessageText() {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(FLASH_MESSAGE)).getText();
    }

    public boolean isFlashMessageSuccess() {
        return getFlashMessageText().contains("You logged into a secure area!");
    }

    public boolean isFlashMessageFailure() {
        return getFlashMessageText().contains("Your username is invalid!")
            || getFlashMessageText().contains("Your password is invalid!");
    }

    // ── Secure page ────────────────────────────────────────────────────────────

    public boolean isSecurePageLoaded() {
        List<WebElement> els = driver.findElements(LOGOUT_BUTTON);
        return !els.isEmpty() && els.get(0).isDisplayed();
    }

    public void clickLogout() {
        wait.until(ExpectedConditions.elementToBeClickable(LOGOUT_BUTTON)).click();
        log.info("InternetPage: Logged out");
    }

    public String getSecureHeadingText() {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(SECURE_HEADING)).getText();
    }

    // ── Checkboxes page ────────────────────────────────────────────────────────

    public boolean isCheckboxesPageLoaded() {
        List<WebElement> els = driver.findElements(CHECKBOXES);
        return !els.isEmpty();
    }

    public int getCheckboxCount() {
        return driver.findElements(CHECKBOXES).size();
    }
}
