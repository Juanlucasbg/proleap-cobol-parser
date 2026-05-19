package io.proleap.cobol.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end workflow for validating the Mi Negocio module in SaleADS.
 *
 * Required environment variables:
 * - SALEADS_LOGIN_URL (or SALEADS_BASE_URL / BASE_URL): login page URL for the current environment.
 *
 * Optional environment variables:
 * - SALEADS_GOOGLE_EMAIL: defaults to juanlucasbarbiergarzon@gmail.com.
 * - HEADLESS: defaults to true.
 */
public class SaleadsMiNegocioWorkflowTest {

    private static final String DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
    private static final Duration DEFAULT_WAIT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration SHORT_WAIT_TIMEOUT = Duration.ofSeconds(5);
    private static final DateTimeFormatter EVIDENCE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private WebDriver driver;
    private WebDriverWait wait;
    private Path evidenceDirectory;
    private final WorkflowReport report = new WorkflowReport();

    @Before
    public void setUp() throws IOException {
        this.driver = createDriver();
        this.wait = new WebDriverWait(driver, DEFAULT_WAIT_TIMEOUT);
        this.evidenceDirectory = Files.createDirectories(Path.of("target", "ui-evidence", EVIDENCE_FORMATTER.format(LocalDateTime.now())));

        driver.get(resolveLoginUrl());
        waitForUiToLoad();
    }

    @After
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    @Test
    public void saleadsMiNegocioFullWorkflow() {
        executeLoginStep();
        executeMiNegocioMenuStep();
        executeAgregarNegocioModalStep();
        executeAdministrarNegociosStep();
        executeInformacionGeneralValidation();
        executeDetallesCuentaValidation();
        executeTusNegociosValidation();
        executeLegalPageValidation("Términos y Condiciones", "Términos y Condiciones", "08-terminos-y-condiciones",
                "Términos y Condiciones");
        executeLegalPageValidation("Política de Privacidad", "Política de Privacidad", "09-politica-privacidad",
                "Política de Privacidad");

        String summary = report.toSummary();
        System.out.println(summary);
        Assert.assertTrue(summary, report.allPassed());
    }

    private void executeLoginStep() {
        final String stepName = "Login";

        try {
            clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
                    "Login con Google", "Ingresar con Google");
            waitForUiToLoad();

            // Google account selector is optional; in some runs session is already authenticated.
            clickIfVisible(DEFAULT_GOOGLE_EMAIL, getConfiguredGoogleEmail());
            waitForUiToLoad();

            boolean mainApplicationVisible = isSidebarVisible() || isAnyTextVisible("Negocio", "Mi Negocio");
            boolean sidebarVisible = isSidebarVisible();
            captureScreenshot("01-dashboard-loaded");

            report.record(stepName, mainApplicationVisible && sidebarVisible,
                    "Main app visible=" + mainApplicationVisible + ", sidebar visible=" + sidebarVisible);
        } catch (Exception exception) {
            report.record(stepName, false, exception.getMessage());
        }
    }

    private void executeMiNegocioMenuStep() {
        final String stepName = "Mi Negocio menu";

        try {
            clickIfVisible("Negocio");
            clickByVisibleText("Mi Negocio");

            boolean expanded = isAnyTextVisible("Agregar Negocio") && isAnyTextVisible("Administrar Negocios");
            captureScreenshot("02-mi-negocio-expanded");

            report.record(stepName, expanded,
                    "Submenu expanded=" + expanded + ", Agregar Negocio visible=" + isAnyTextVisible("Agregar Negocio")
                            + ", Administrar Negocios visible=" + isAnyTextVisible("Administrar Negocios"));
        } catch (Exception exception) {
            report.record(stepName, false, exception.getMessage());
        }
    }

    private void executeAgregarNegocioModalStep() {
        final String stepName = "Agregar Negocio modal";

        try {
            clickByVisibleText("Agregar Negocio");
            waitForUiToLoad();

            boolean titleVisible = isAnyTextVisible("Crear Nuevo Negocio");
            boolean fieldVisible = isElementVisible(By.xpath(
                    "//input[@placeholder='Nombre del Negocio' or @name='nombreNegocio' or @aria-label='Nombre del Negocio']"
                            + "| //label[normalize-space()='Nombre del Negocio']"));
            boolean quotaVisible = isAnyTextVisible("Tienes 2 de 3 negocios");
            boolean cancelVisible = isAnyTextVisible("Cancelar");
            boolean createVisible = isAnyTextVisible("Crear Negocio");

            captureScreenshot("03-crear-negocio-modal");

            Optional<WebElement> nameInput = findFirstVisibleElement(By.xpath(
                    "//input[@placeholder='Nombre del Negocio' or @name='nombreNegocio' or @aria-label='Nombre del Negocio']"));
            if (nameInput.isPresent()) {
                nameInput.get().click();
                nameInput.get().clear();
                nameInput.get().sendKeys("Negocio Prueba Automatización");
            }

            clickIfVisible("Cancelar");
            waitForUiToLoad();

            boolean passed = titleVisible && fieldVisible && quotaVisible && cancelVisible && createVisible;
            report.record(stepName, passed,
                    "title=" + titleVisible + ", field=" + fieldVisible + ", quota=" + quotaVisible + ", cancel="
                            + cancelVisible + ", create=" + createVisible);
        } catch (Exception exception) {
            report.record(stepName, false, exception.getMessage());
        }
    }

    private void executeAdministrarNegociosStep() {
        final String stepName = "Administrar Negocios view";

        try {
            ensureMiNegocioMenuExpanded();
            clickByVisibleText("Administrar Negocios");
            waitForUiToLoad();

            boolean infoGeneral = isAnyTextVisible("Información General");
            boolean detallesCuenta = isAnyTextVisible("Detalles de la Cuenta");
            boolean tusNegocios = isAnyTextVisible("Tus Negocios");
            boolean legal = isAnyTextVisible("Sección Legal");

            captureScreenshot("04-administrar-negocios");

            boolean passed = infoGeneral && detallesCuenta && tusNegocios && legal;
            report.record(stepName, passed,
                    "Información General=" + infoGeneral + ", Detalles de la Cuenta=" + detallesCuenta + ", Tus Negocios="
                            + tusNegocios + ", Sección Legal=" + legal);
        } catch (Exception exception) {
            report.record(stepName, false, exception.getMessage());
        }
    }

    private void executeInformacionGeneralValidation() {
        final String stepName = "Información General";

        try {
            boolean userNameVisible = isElementVisible(By.xpath("//section//*[contains(@class,'name') and normalize-space()]"
                    + "| //section//*[contains(normalize-space(), '@')]/preceding::*[normalize-space()][1]"));
            boolean userEmailVisible = isElementVisible(By.xpath("//*[contains(normalize-space(), '@')]"));
            boolean businessPlanVisible = isAnyTextVisible("BUSINESS PLAN");
            boolean cambiarPlanVisible = isAnyTextVisible("Cambiar Plan");

            boolean passed = userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible;
            report.record(stepName, passed,
                    "userName=" + userNameVisible + ", userEmail=" + userEmailVisible + ", BUSINESS PLAN="
                            + businessPlanVisible + ", Cambiar Plan=" + cambiarPlanVisible);
        } catch (Exception exception) {
            report.record(stepName, false, exception.getMessage());
        }
    }

    private void executeDetallesCuentaValidation() {
        final String stepName = "Detalles de la Cuenta";

        try {
            boolean cuentaCreadaVisible = isAnyTextVisible("Cuenta creada");
            boolean estadoActivoVisible = isAnyTextVisible("Estado activo");
            boolean idiomaVisible = isAnyTextVisible("Idioma seleccionado");

            boolean passed = cuentaCreadaVisible && estadoActivoVisible && idiomaVisible;
            report.record(stepName, passed,
                    "Cuenta creada=" + cuentaCreadaVisible + ", Estado activo=" + estadoActivoVisible
                            + ", Idioma seleccionado=" + idiomaVisible);
        } catch (Exception exception) {
            report.record(stepName, false, exception.getMessage());
        }
    }

    private void executeTusNegociosValidation() {
        final String stepName = "Tus Negocios";

        try {
            boolean sectionVisible = isAnyTextVisible("Tus Negocios");
            boolean addBusinessVisible = isAnyTextVisible("Agregar Negocio");
            boolean quotaVisible = isAnyTextVisible("Tienes 2 de 3 negocios");

            boolean passed = sectionVisible && addBusinessVisible && quotaVisible;
            report.record(stepName, passed, "section=" + sectionVisible + ", Agregar Negocio=" + addBusinessVisible
                    + ", quota=" + quotaVisible);
        } catch (Exception exception) {
            report.record(stepName, false, exception.getMessage());
        }
    }

    private void executeLegalPageValidation(final String linkText, final String expectedHeading, final String screenshotName,
            final String stepName) {
        try {
            String applicationWindow = driver.getWindowHandle();
            Set<String> handlesBefore = driver.getWindowHandles();

            clickByVisibleText(linkText);

            String legalWindow = waitForPotentialNewTab(handlesBefore).orElse(applicationWindow);
            if (!legalWindow.equals(applicationWindow)) {
                driver.switchTo().window(legalWindow);
                waitForUiToLoad();
            }

            boolean headingVisible = isAnyTextVisible(expectedHeading);
            boolean legalContentVisible = isElementVisible(By.xpath(
                    "//main//*[string-length(normalize-space()) > 80] | //article//*[string-length(normalize-space()) > 80]"
                            + " | //body//*[string-length(normalize-space()) > 140]"));

            String finalUrl = driver.getCurrentUrl();
            captureScreenshot(screenshotName);

            boolean passed = headingVisible && legalContentVisible;
            report.record(stepName, passed,
                    "heading=" + headingVisible + ", legalContent=" + legalContentVisible + ", finalUrl=" + finalUrl);

            if (!legalWindow.equals(applicationWindow)) {
                driver.close();
                driver.switchTo().window(applicationWindow);
            } else {
                driver.navigate().back();
            }

            waitForUiToLoad();
        } catch (Exception exception) {
            report.record(stepName, false, exception.getMessage());
        }
    }

    private void ensureMiNegocioMenuExpanded() {
        if (!isAnyTextVisible("Administrar Negocios")) {
            clickIfVisible("Mi Negocio");
        }
    }

    private void clickByVisibleText(final String... textVariants) {
        WebElement target = waitForAnyVisibleText(Arrays.asList(textVariants));
        clickElement(target);
    }

    private void clickIfVisible(final String... textVariants) {
        Optional<WebElement> target = findAnyVisibleText(Arrays.asList(textVariants));
        target.ifPresent(this::clickElement);
    }

    private void clickElement(final WebElement element) {
        scrollIntoView(element);

        try {
            wait.until(ExpectedConditions.elementToBeClickable(element)).click();
        } catch (StaleElementReferenceException | TimeoutException | WebDriverException exception) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
        }

        waitForUiToLoad();
    }

    private Optional<String> waitForPotentialNewTab(final Set<String> handlesBeforeClick) {
        long timeoutAt = System.currentTimeMillis() + SHORT_WAIT_TIMEOUT.toMillis();

        while (System.currentTimeMillis() < timeoutAt) {
            Set<String> currentHandles = driver.getWindowHandles();
            if (currentHandles.size() > handlesBeforeClick.size()) {
                for (String handle : currentHandles) {
                    if (!handlesBeforeClick.contains(handle)) {
                        return Optional.of(handle);
                    }
                }
            }

            sleep(200);
        }

        return Optional.empty();
    }

    private WebElement waitForAnyVisibleText(final List<String> textVariants) {
        return wait.until(driverInstance -> findAnyVisibleText(textVariants).orElse(null));
    }

    private Optional<WebElement> findAnyVisibleText(final List<String> textVariants) {
        List<String> expandedVariants = new ArrayList<>(textVariants);
        for (String variant : textVariants) {
            expandedVariants.add(variant.toLowerCase());
        }

        for (String text : expandedVariants) {
            String literal = toXPathLiteral(text);

            Optional<WebElement> exact = findFirstVisibleElement(By.xpath("//*[normalize-space()=" + literal + "]"));
            if (exact.isPresent()) {
                return exact;
            }

            Optional<WebElement> contains = findFirstVisibleElement(By.xpath("//*[contains(normalize-space(), " + literal + ")]"));
            if (contains.isPresent()) {
                return contains;
            }
        }

        return Optional.empty();
    }

    private boolean isAnyTextVisible(final String... textVariants) {
        return findAnyVisibleText(Arrays.asList(textVariants)).isPresent();
    }

    private boolean isSidebarVisible() {
        return isElementVisible(By.xpath("//aside[not(contains(@style,'display: none'))] | //nav[contains(@class,'sidebar')]"));
    }

    private boolean isElementVisible(final By locator) {
        return findFirstVisibleElement(locator).isPresent();
    }

    private Optional<WebElement> findFirstVisibleElement(final By locator) {
        List<WebElement> matches = driver.findElements(locator);
        for (WebElement element : matches) {
            try {
                if (element.isDisplayed()) {
                    return Optional.of(element);
                }
            } catch (NoSuchElementException | StaleElementReferenceException ignored) {
                // Ignore stale elements while polling.
            }
        }
        return Optional.empty();
    }

    private void waitForUiToLoad() {
        wait.until(driverInstance -> "complete".equals(
                String.valueOf(((JavascriptExecutor) driverInstance).executeScript("return document.readyState"))));
        sleep(500);
    }

    private void scrollIntoView(final WebElement element) {
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({behavior: 'instant', block: 'center', inline: 'nearest'});", element);
    }

    private void captureScreenshot(final String checkpointName) {
        try {
            File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Files.copy(screenshot.toPath(), evidenceDirectory.resolve(checkpointName + ".png"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | WebDriverException exception) {
            report.addEvidenceWarning("Could not capture screenshot '" + checkpointName + "': " + exception.getMessage());
        }
    }

    private WebDriver createDriver() {
        boolean headless = isHeadlessEnabled();

        try {
            ChromeOptions chromeOptions = new ChromeOptions();
            if (headless) {
                chromeOptions.addArguments("--headless=new");
            }
            chromeOptions.addArguments("--window-size=1920,1080");
            chromeOptions.addArguments("--no-sandbox");
            chromeOptions.addArguments("--disable-dev-shm-usage");
            return new ChromeDriver(chromeOptions);
        } catch (WebDriverException ignored) {
            FirefoxOptions firefoxOptions = new FirefoxOptions();
            if (headless) {
                firefoxOptions.addArguments("-headless");
            }
            firefoxOptions.addArguments("--width=1920");
            firefoxOptions.addArguments("--height=1080");
            return new FirefoxDriver(firefoxOptions);
        }
    }

    private boolean isHeadlessEnabled() {
        String value = Optional.ofNullable(System.getenv("HEADLESS"))
                .orElseGet(() -> System.getProperty("headless", "true"));
        return !"false".equalsIgnoreCase(value);
    }

    private String resolveLoginUrl() {
        for (String variable : List.of("SALEADS_LOGIN_URL", "SALEADS_BASE_URL", "BASE_URL")) {
            String value = System.getenv(variable);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        String property = System.getProperty("saleads.login.url");
        if (property != null && !property.isBlank()) {
            return property;
        }

        throw new IllegalStateException(
                "Missing login URL. Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL / BASE_URL / -Dsaleads.login.url).");
    }

    private String getConfiguredGoogleEmail() {
        return Optional.ofNullable(System.getenv("SALEADS_GOOGLE_EMAIL")).filter(value -> !value.isBlank())
                .orElse(DEFAULT_GOOGLE_EMAIL);
    }

    private void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for UI to stabilize.", exception);
        }
    }

    private String toXPathLiteral(final String value) {
        if (!value.contains("'")) {
            return "'" + value + "'";
        }

        if (!value.contains("\"")) {
            return "\"" + value + "\"";
        }

        String[] split = value.split("'");
        StringBuilder builder = new StringBuilder("concat(");
        for (int i = 0; i < split.length; i++) {
            builder.append("'").append(split[i]).append("'");
            if (i < split.length - 1) {
                builder.append(", \"'\", ");
            }
        }
        builder.append(")");
        return builder.toString();
    }

    private static final class WorkflowReport {
        private final Map<String, StepResult> results = new LinkedHashMap<>();
        private final List<String> evidenceWarnings = new ArrayList<>();

        private void record(final String stepName, final boolean passed, final String details) {
            results.put(stepName, new StepResult(passed, details));
        }

        private void addEvidenceWarning(final String warning) {
            evidenceWarnings.add(warning);
        }

        private boolean allPassed() {
            return results.values().stream().allMatch(result -> result.passed);
        }

        private String toSummary() {
            StringBuilder builder = new StringBuilder();
            builder.append("\n=== SaleADS Mi Negocio Workflow Report ===\n");
            appendStep(builder, "Login");
            appendStep(builder, "Mi Negocio menu");
            appendStep(builder, "Agregar Negocio modal");
            appendStep(builder, "Administrar Negocios view");
            appendStep(builder, "Información General");
            appendStep(builder, "Detalles de la Cuenta");
            appendStep(builder, "Tus Negocios");
            appendStep(builder, "Términos y Condiciones");
            appendStep(builder, "Política de Privacidad");

            if (!evidenceWarnings.isEmpty()) {
                builder.append("\nEvidence warnings:\n");
                evidenceWarnings.forEach(warning -> builder.append("- ").append(warning).append('\n'));
            }

            return builder.toString();
        }

        private void appendStep(final StringBuilder builder, final String stepName) {
            StepResult result = results.getOrDefault(stepName, StepResult.notExecuted());
            builder.append(stepName).append(": ").append(result.passed ? "PASS" : "FAIL").append(" | ")
                    .append(result.details).append('\n');
        }
    }

    private static final class StepResult {
        private final boolean passed;
        private final String details;

        private StepResult(final boolean passed, final String details) {
            this.passed = passed;
            this.details = details;
        }

        private static StepResult notExecuted() {
            return new StepResult(false, "Not executed");
        }
    }
}
