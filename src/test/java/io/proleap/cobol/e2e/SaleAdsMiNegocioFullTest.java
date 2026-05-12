package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleAdsMiNegocioFullTest {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

    private WebDriver driver;
    private WebDriverWait wait;
    private Path screenshotDir;
    private String appWindowHandle;
    private final Map<String, Boolean> stepReport = new LinkedHashMap<>();
    private final Map<String, String> stepNotes = new LinkedHashMap<>();

    @Before
    public void setUp() throws IOException {
        driver = buildDriver();
        wait = new WebDriverWait(driver, Duration.ofSeconds(getIntConfig("saleads.waitSeconds", "SALEADS_WAIT_SECONDS", 25)));
        screenshotDir = initScreenshotDirectory();

        final boolean skipNavigation = getBooleanConfig("saleads.skipNavigation", "SALEADS_SKIP_NAVIGATION", false);
        if (!skipNavigation) {
            final String loginUrl = getStringConfig("saleads.loginUrl", "SALEADS_LOGIN_URL")
                    .orElseThrow(() -> new IllegalStateException(
                            "Missing login URL. Set saleads.loginUrl or SALEADS_LOGIN_URL."));
            driver.get(loginUrl);
            waitForUiLoad();
        }
    }

    @After
    public void tearDown() {
        printFinalReport();
        if (driver != null) {
            driver.quit();
        }
    }

    @Test
    public void saleadsMiNegocioFullWorkflow() {
        boolean loginOk = runStep("Login", this::loginWithGoogleAndValidateSidebar);
        appWindowHandle = safeCurrentWindow();

        boolean menuOk = loginOk ? runStep("Mi Negocio menu", this::openMiNegocioAndValidateMenu)
                : failStepDueToDependency("Mi Negocio menu", "Login failed.");

        boolean addBusinessModalOk = menuOk ? runStep("Agregar Negocio modal", this::validateAgregarNegocioModal)
                : failStepDueToDependency("Agregar Negocio modal", "Mi Negocio menu failed.");

        boolean adminOk = menuOk ? runStep("Administrar Negocios view", this::openAdministrarNegociosAndValidateSections)
                : failStepDueToDependency("Administrar Negocios view", "Mi Negocio menu failed.");

        boolean infoGeneralOk = adminOk ? runStep("Informaci\u00F3n General", this::validateInformacionGeneralSection)
                : failStepDueToDependency("Informaci\u00F3n General", "Administrar Negocios view failed.");

        boolean detallesCuentaOk = adminOk ? runStep("Detalles de la Cuenta", this::validateDetallesCuentaSection)
                : failStepDueToDependency("Detalles de la Cuenta", "Administrar Negocios view failed.");

        boolean negociosOk = adminOk ? runStep("Tus Negocios", this::validateTusNegociosSection)
                : failStepDueToDependency("Tus Negocios", "Administrar Negocios view failed.");

        boolean termsOk = adminOk
                ? runStep("T\u00E9rminos y Condiciones", () -> validateLegalLink(
                        Arrays.asList("T\u00E9rminos y Condiciones", "Terminos y Condiciones"),
                        Arrays.asList("T\u00E9rminos y Condiciones", "Terminos y Condiciones"),
                        "checkpoint-terms"))
                : failStepDueToDependency("T\u00E9rminos y Condiciones", "Administrar Negocios view failed.");

        boolean privacyOk = adminOk
                ? runStep("Pol\u00EDtica de Privacidad", () -> validateLegalLink(
                        Arrays.asList("Pol\u00EDtica de Privacidad", "Politica de Privacidad"),
                        Arrays.asList("Pol\u00EDtica de Privacidad", "Politica de Privacidad"),
                        "checkpoint-privacy"))
                : failStepDueToDependency("Pol\u00EDtica de Privacidad", "Administrar Negocios view failed.");

        assertTrue("One or more SaleADS workflow validations failed:\n" + formatReport(),
                loginOk && menuOk && addBusinessModalOk && adminOk && infoGeneralOk && detallesCuentaOk && negociosOk
                        && termsOk && privacyOk);
    }

    private void loginWithGoogleAndValidateSidebar() throws Exception {
        clickByVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesi\u00F3n con Google", "Continuar con Google",
                "Ingresar con Google", "Google"));
        waitForUiLoad();
        switchToNewestWindowIfAny();
        selectGoogleAccountIfVisible();
        switchBackToApplicationWindow();
        assertAnyTextVisible(Arrays.asList("Negocio", "Mi Negocio"), 45);
        takeCheckpoint("checkpoint-dashboard");
    }

    private void openMiNegocioAndValidateMenu() throws Exception {
        if (!isAnyTextVisible(Arrays.asList("Mi Negocio"), 5)) {
            clickByVisibleText(Arrays.asList("Negocio"));
        }
        clickByVisibleText(Arrays.asList("Mi Negocio"));
        waitForUiLoad();
        assertTextVisible("Agregar Negocio", 20);
        assertTextVisible("Administrar Negocios", 20);
        takeCheckpoint("checkpoint-mi-negocio-menu");
    }

    private void validateAgregarNegocioModal() throws Exception {
        clickByVisibleText(Arrays.asList("Agregar Negocio"));
        waitForUiLoad();
        assertTextVisible("Crear Nuevo Negocio", 20);
        assertTextVisible("Nombre del Negocio", 20);
        assertTextVisible("Tienes 2 de 3 negocios", 20);
        assertTextVisible("Cancelar", 20);
        assertTextVisible("Crear Negocio", 20);
        takeCheckpoint("checkpoint-agregar-negocio-modal");

        final Optional<WebElement> businessNameInput = findInputByLabelText("Nombre del Negocio");
        if (businessNameInput.isPresent()) {
            businessNameInput.get().click();
            businessNameInput.get().clear();
            businessNameInput.get().sendKeys("Negocio Prueba Automatizacion");
        }
        clickByVisibleText(Arrays.asList("Cancelar"));
        waitForUiLoad();
    }

    private void openAdministrarNegociosAndValidateSections() throws Exception {
        if (!isAnyTextVisible(Arrays.asList("Administrar Negocios"), 5)) {
            clickByVisibleText(Arrays.asList("Mi Negocio"));
            waitForUiLoad();
        }
        clickByVisibleText(Arrays.asList("Administrar Negocios"));
        waitForUiLoad();
        assertTextVisible("Informaci\u00F3n General", 30);
        assertTextVisible("Detalles de la Cuenta", 30);
        assertTextVisible("Tus Negocios", 30);
        assertTextVisible("Secci\u00F3n Legal", 30);
        takeCheckpoint("checkpoint-administrar-negocios");
    }

    private void validateInformacionGeneralSection() {
        final WebElement section = sectionByHeading("Informaci\u00F3n General");
        final String sectionText = section.getText();
        if (!EMAIL_PATTERN.matcher(sectionText).find()) {
            throw new AssertionError("User email was not found in Informacion General.");
        }
        if (!sectionText.contains("BUSINESS PLAN")) {
            throw new AssertionError("BUSINESS PLAN text was not found in Informacion General.");
        }
        if (!sectionText.contains("Cambiar Plan")) {
            throw new AssertionError("Cambiar Plan button/text was not found in Informacion General.");
        }
    }

    private void validateDetallesCuentaSection() {
        final WebElement section = sectionByHeading("Detalles de la Cuenta");
        final String sectionText = section.getText();
        assertContains(sectionText, "Cuenta creada", "Cuenta creada");
        assertContains(sectionText, "Estado activo", "Estado activo");
        assertContains(sectionText, "Idioma seleccionado", "Idioma seleccionado");
    }

    private void validateTusNegociosSection() {
        final WebElement section = sectionByHeading("Tus Negocios");
        final String sectionText = section.getText();
        assertContains(sectionText, "Agregar Negocio", "Agregar Negocio");
        assertContains(sectionText, "Tienes 2 de 3 negocios", "Tienes 2 de 3 negocios");
    }

    private void validateLegalLink(List<String> linkTexts, List<String> headingTexts, String screenshotName)
            throws Exception {
        final String originalWindow = safeCurrentWindow();
        final String originalUrl = driver.getCurrentUrl();
        final Set<String> handlesBefore = driver.getWindowHandles();

        clickByVisibleText(linkTexts);
        waitForUiLoad();
        String activeWindow = waitForWindowOrUrlChange(handlesBefore, originalUrl, 20);
        if (activeWindow != null) {
            driver.switchTo().window(activeWindow);
            waitForUiLoad();
        }

        assertAnyTextVisible(headingTexts, 30);
        takeCheckpoint(screenshotName);
        stepNotes.put(screenshotName + "-url", "Final URL: " + driver.getCurrentUrl());

        if (activeWindow != null) {
            driver.close();
            driver.switchTo().window(originalWindow);
            waitForUiLoad();
            return;
        }

        driver.navigate().back();
        waitForUiLoad();
    }

    private WebDriver buildDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-gpu");
        if (getBooleanConfig("saleads.headless", "SALEADS_HEADLESS", true)) {
            options.addArguments("--headless=new");
        }

        Optional<String> remoteUrl = getStringConfig("saleads.remoteUrl", "SELENIUM_REMOTE_URL");
        if (remoteUrl.isPresent()) {
            try {
                return new RemoteWebDriver(new URL(remoteUrl.get()), options);
            } catch (MalformedURLException ex) {
                throw new IllegalStateException("Invalid Selenium remote URL: " + remoteUrl.get(), ex);
            }
        }
        return new ChromeDriver(options);
    }

    private Path initScreenshotDirectory() throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        Path baseDir = Paths.get(System.getProperty("saleads.screenshotDir",
                System.getenv().getOrDefault("SALEADS_SCREENSHOT_DIR", "target/saleads-screenshots")));
        Path runDir = baseDir.resolve("saleads_mi_negocio_full_test-" + timestamp);
        Files.createDirectories(runDir);
        return runDir;
    }

    private boolean runStep(String stepName, StepAction action) {
        try {
            action.run();
            stepReport.put(stepName, Boolean.TRUE);
            return true;
        } catch (Exception ex) {
            stepReport.put(stepName, Boolean.FALSE);
            stepNotes.put(stepName, ex.getMessage());
            takeCheckpoint("error-" + slug(stepName));
            return false;
        }
    }

    private boolean failStepDueToDependency(String stepName, String reason) {
        stepReport.put(stepName, Boolean.FALSE);
        stepNotes.put(stepName, "Not executed due to previous failure: " + reason);
        return false;
    }

    private void clickByVisibleText(List<String> textCandidates) {
        for (String text : textCandidates) {
            List<By> locators = Arrays.asList(
                    By.xpath("//button[normalize-space()=" + xpathLiteral(text) + "]"),
                    By.xpath("//a[normalize-space()=" + xpathLiteral(text) + "]"),
                    By.xpath("//*[@role='button' and normalize-space()=" + xpathLiteral(text) + "]"),
                    By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]"));

            for (By locator : locators) {
                List<WebElement> elements = driver.findElements(locator);
                for (WebElement element : elements) {
                    if (!element.isDisplayed()) {
                        continue;
                    }
                    clickElement(element);
                    waitForUiLoad();
                    return;
                }
            }
        }
        throw new NoSuchElementException("Unable to click visible element with any text in: " + textCandidates);
    }

    private void clickElement(WebElement element) {
        try {
            wait.until(ExpectedConditions.elementToBeClickable(element)).click();
        } catch (Exception ignored) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
        }
    }

    private void selectGoogleAccountIfVisible() {
        if (isAnyTextVisible(Arrays.asList(GOOGLE_ACCOUNT_EMAIL), 6)) {
            clickByVisibleText(Arrays.asList(GOOGLE_ACCOUNT_EMAIL));
            waitForUiLoad();
        }
    }

    private void switchToNewestWindowIfAny() {
        final Set<String> handlesBefore = driver.getWindowHandles();
        waitForUiLoad();
        if (handlesBefore.size() > 1) {
            String newest = handlesBefore.stream().reduce((first, second) -> second).orElse(null);
            if (newest != null) {
                driver.switchTo().window(newest);
                waitForUiLoad();
            }
        }
    }

    private void switchBackToApplicationWindow() {
        if (isAnyTextVisible(Arrays.asList("Negocio", "Mi Negocio"), 2)) {
            appWindowHandle = safeCurrentWindow();
            return;
        }
        Set<String> handles = driver.getWindowHandles();
        for (String handle : handles) {
            driver.switchTo().window(handle);
            if (isAnyTextVisible(Arrays.asList("Negocio", "Mi Negocio"), 5)) {
                appWindowHandle = handle;
                return;
            }
        }
        throw new AssertionError("Could not find application window after Google login.");
    }

    private String waitForWindowOrUrlChange(Set<String> handlesBefore, String originalUrl, int timeoutSeconds) {
        WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
        try {
            shortWait.until((ExpectedCondition<Boolean>) wd -> wd != null
                    && (wd.getWindowHandles().size() > handlesBefore.size() || !wd.getCurrentUrl().equals(originalUrl)));
        } catch (TimeoutException ignored) {
            throw new AssertionError("No navigation or new tab detected after legal link click.");
        }

        Set<String> handlesAfter = driver.getWindowHandles();
        if (handlesAfter.size() > handlesBefore.size()) {
            return handlesAfter.stream().filter(handle -> !handlesBefore.contains(handle)).findFirst().orElse(null);
        }
        return null;
    }

    private WebElement sectionByHeading(String headingText) {
        By heading = By.xpath("//*[normalize-space()=" + xpathLiteral(headingText) + "]");
        WebElement headingElement = wait.until(ExpectedConditions.visibilityOfElementLocated(heading));
        List<By> parentCandidates = Arrays.asList(
                By.xpath("./ancestor::section[1]"),
                By.xpath("./ancestor::div[contains(@class,'card')][1]"),
                By.xpath("./ancestor::div[1]"));

        for (By parentLocator : parentCandidates) {
            List<WebElement> parents = headingElement.findElements(parentLocator);
            if (!parents.isEmpty()) {
                return parents.get(0);
            }
        }
        return headingElement;
    }

    private Optional<WebElement> findInputByLabelText(String labelText) {
        List<By> candidates = Arrays.asList(
                By.xpath("//input[@placeholder=" + xpathLiteral(labelText) + "]"),
                By.xpath("//label[contains(normalize-space(), " + xpathLiteral(labelText) + ")]/following::input[1]"),
                By.xpath("//*[contains(normalize-space(), " + xpathLiteral(labelText) + ")]/following::input[1]"));

        for (By locator : candidates) {
            List<WebElement> elements = driver.findElements(locator);
            for (WebElement element : elements) {
                if (element.isDisplayed()) {
                    return Optional.of(element);
                }
            }
        }
        return Optional.empty();
    }

    private void assertAnyTextVisible(List<String> texts, int timeoutSeconds) {
        WebDriverWait customWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
        boolean found = texts.stream().anyMatch(text -> isTextVisibleWithWait(text, customWait));
        if (!found) {
            throw new AssertionError("None of expected texts are visible: " + texts);
        }
    }

    private void assertTextVisible(String text, int timeoutSeconds) {
        WebDriverWait customWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
        if (!isTextVisibleWithWait(text, customWait)) {
            throw new AssertionError("Expected text not visible: " + text);
        }
    }

    private boolean isAnyTextVisible(List<String> texts, int timeoutSeconds) {
        WebDriverWait customWait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
        return texts.stream().anyMatch(text -> isTextVisibleWithWait(text, customWait));
    }

    private boolean isTextVisibleWithWait(String text, WebDriverWait customWait) {
        try {
            customWait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]")));
            return true;
        } catch (TimeoutException ex) {
            return false;
        }
    }

    private void waitForUiLoad() {
        try {
            wait.until(webDriver -> {
                if (webDriver == null) {
                    return false;
                }
                Object state = ((JavascriptExecutor) webDriver).executeScript("return document.readyState");
                return "complete".equals(state);
            });
        } catch (Exception ignored) {
            // Some external pages can block script execution briefly.
        }

        try {
            Thread.sleep(500);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void takeCheckpoint(String name) {
        if (!(driver instanceof TakesScreenshot)) {
            return;
        }
        try {
            byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            Files.write(screenshotDir.resolve(name + ".png"), screenshot);
        } catch (Exception ex) {
            stepNotes.put(name + "-screenshot", "Failed to save screenshot: " + ex.getMessage());
        }
    }

    private String safeCurrentWindow() {
        try {
            return driver.getWindowHandle();
        } catch (Exception ex) {
            return null;
        }
    }

    private void assertContains(String actual, String expected, String label) {
        if (!actual.contains(expected)) {
            throw new AssertionError("Expected text '" + label + "' was not found.");
        }
    }

    private void printFinalReport() {
        if (stepReport.isEmpty()) {
            return;
        }
        System.out.println("=== SaleADS Mi Negocio Workflow Final Report ===");
        System.out.println(formatReport());
        System.out.println("Screenshots directory: " + screenshotDir);
    }

    private String formatReport() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : stepReport.entrySet()) {
            String status = Boolean.TRUE.equals(entry.getValue()) ? "PASS" : "FAIL";
            String note = stepNotes.getOrDefault(entry.getKey(), "");
            if (!note.isEmpty()) {
                lines.add("- " + entry.getKey() + ": " + status + " (" + note + ")");
            } else {
                lines.add("- " + entry.getKey() + ": " + status);
            }
        }

        String extras = stepNotes.entrySet().stream()
                .filter(e -> e.getKey().endsWith("-url") || e.getKey().endsWith("-screenshot"))
                .map(e -> "- " + e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("\n"));
        if (!extras.isEmpty()) {
            lines.add(extras);
        }

        return String.join("\n", lines);
    }

    private Optional<String> getStringConfig(String systemProperty, String envVar) {
        String propertyValue = System.getProperty(systemProperty);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return Optional.of(propertyValue.trim());
        }
        String envValue = System.getenv(envVar);
        if (envValue != null && !envValue.isBlank()) {
            return Optional.of(envValue.trim());
        }
        return Optional.empty();
    }

    private int getIntConfig(String systemProperty, String envVar, int defaultValue) {
        Optional<String> configuredValue = getStringConfig(systemProperty, envVar);
        if (configuredValue.isPresent()) {
            try {
                return Integer.parseInt(configuredValue.get());
            } catch (NumberFormatException ex) {
                throw new IllegalStateException("Invalid integer for " + systemProperty + "/" + envVar + ": "
                        + configuredValue.get(), ex);
            }
        }
        return defaultValue;
    }

    private boolean getBooleanConfig(String systemProperty, String envVar, boolean defaultValue) {
        return getStringConfig(systemProperty, envVar).map(Boolean::parseBoolean).orElse(defaultValue);
    }

    private String xpathLiteral(String value) {
        if (!value.contains("'")) {
            return "'" + value + "'";
        }
        return "concat('" + value.replace("'", "',\"'\",'") + "')";
    }

    private String slug(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
    }

    @FunctionalInterface
    private interface StepAction {
        void run() throws Exception;
    }
}
