package io.proleap.cobol.e2e;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end workflow test for SaleADS "Mi Negocio" module.
 *
 * <p>Configuration:</p>
 * <ul>
 *   <li>SALEADS_LOGIN_URL or -Dsaleads.login.url (required)</li>
 *   <li>SALEADS_HEADLESS or -Dsaleads.headless (optional, default false)</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private WebDriver driver;
    private WebDriverWait wait;
    private Path evidenceDir;
    private Path reportFile;
    private String applicationWindow;
    private String terminosFinalUrl = "N/A";
    private String politicaFinalUrl = "N/A";

    private final LinkedHashMap<String, String> report = new LinkedHashMap<>();
    private final List<String> failures = new ArrayList<>();

    @Before
    public void setUp() throws IOException {
        final ChromeOptions options = new ChromeOptions();
        final boolean headless = Boolean.parseBoolean(resolveConfig("saleads.headless", "SALEADS_HEADLESS", "false"));
        if (headless) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox");

        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

        final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        evidenceDir = Paths.get("target", "saleads-evidence", timestamp);
        Files.createDirectories(evidenceDir);
        reportFile = evidenceDir.resolve("saleads_mi_negocio_full_test_report.txt");

        initializeReportFields();
    }

    @After
    public void tearDown() {
        writeFinalReport();
        if (driver != null) {
            driver.quit();
        }
    }

    @Test
    public void saleadsMiNegocioFullWorkflow() {
        final String loginUrl = resolveConfig("saleads.login.url", "SALEADS_LOGIN_URL", "");
        Assert.assertFalse(
                "SALEADS_LOGIN_URL or -Dsaleads.login.url is required to keep the test environment-agnostic.",
                loginUrl.isBlank());

        driver.get(loginUrl);
        waitForUiLoad();
        applicationWindow = driver.getWindowHandle();

        executeStep("Login", this::stepLoginWithGoogle);
        executeStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
        executeStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
        executeStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
        executeStep("Informacion General", this::stepValidateInformacionGeneral);
        executeStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
        executeStep("Tus Negocios", this::stepValidateTusNegocios);
        executeStep("Terminos y Condiciones", this::stepValidateTerminosYCondiciones);
        executeStep("Politica de Privacidad", this::stepValidatePoliticaPrivacidad);

        writeFinalReport();
        if (!failures.isEmpty()) {
            Assert.fail("Workflow validations failed:\n- " + String.join("\n- ", failures));
        }
    }

    private void stepLoginWithGoogle() {
        final Set<String> beforeHandles = new LinkedHashSet<>(driver.getWindowHandles());
        clickFirstVisibleText(
                "Sign in with Google",
                "Iniciar sesion con Google",
                "Inicia sesion con Google",
                "Continuar con Google",
                "Login with Google");

        maybeSelectGoogleAccount(beforeHandles, "juanlucasbarbiergarzon@gmail.com");
        waitForUiLoad();

        assertAnyTextVisible("Negocio", "Mi Negocio");
        assertSidebarVisible();
        takeScreenshot("01_dashboard_loaded");
    }

    private void stepOpenMiNegocioMenu() {
        assertSidebarVisible();

        if (!isTextVisible("Agregar Negocio")) {
            clickFirstVisibleText("Mi Negocio", "Negocio");
        }

        assertTextVisible("Agregar Negocio");
        assertTextVisible("Administrar Negocios");
        takeScreenshot("02_mi_negocio_expanded_menu");
    }

    private void stepValidateAgregarNegocioModal() {
        clickFirstVisibleText("Agregar Negocio");

        assertTextVisible("Crear Nuevo Negocio");
        final WebElement negocioNameInput = findNombreNegocioInput();
        Assert.assertNotNull("Input field 'Nombre del Negocio' was not found.", negocioNameInput);
        assertTextVisible("Tienes 2 de 3 negocios");
        assertTextVisible("Cancelar");
        assertTextVisible("Crear Negocio");

        takeScreenshot("03_agregar_negocio_modal");

        negocioNameInput.click();
        negocioNameInput.clear();
        negocioNameInput.sendKeys("Negocio Prueba Automatizacion");
        clickFirstVisibleText("Cancelar");
        waitForUiLoad();
    }

    private void stepOpenAdministrarNegocios() {
        if (!isTextVisible("Administrar Negocios")) {
            clickFirstVisibleText("Mi Negocio", "Negocio");
        }

        clickFirstVisibleText("Administrar Negocios");
        waitForUiLoad();

        assertAnyTextVisible("Informacion General", "Información General");
        assertAnyTextVisible("Detalles de la Cuenta");
        assertTextVisible("Tus Negocios");
        assertAnyTextVisible("Seccion Legal", "Sección Legal", "Legal");
        takeScreenshot("04_administrar_negocios_account_page");
    }

    private void stepValidateInformacionGeneral() {
        assertAnyTextVisible("Informacion General", "Información General");
        assertTextVisible("BUSINESS PLAN");
        assertAnyTextVisible("Cambiar Plan");

        final String pageText = bodyText();
        Assert.assertTrue("User email was not visible in Informacion General.", EMAIL_PATTERN.matcher(pageText).find());

        final boolean hasNombreLabel = isTextVisible("Nombre") || isTextVisible("Usuario");
        final boolean hasValueLikeName = pageText.replaceAll("\\s+", " ").matches("(?s).*[A-Z][a-z]+\\s+[A-Z][a-z]+.*");
        Assert.assertTrue("User name was not clearly visible in Informacion General.", hasNombreLabel || hasValueLikeName);
    }

    private void stepValidateDetallesCuenta() {
        assertAnyTextVisible("Detalles de la Cuenta");
        assertAnyTextVisible("Cuenta creada", "Cuenta Creada");
        assertAnyTextVisible("Estado activo", "Estado Activo", "Activo");
        assertAnyTextVisible("Idioma seleccionado", "Idioma Seleccionado");
    }

    private void stepValidateTusNegocios() {
        assertTextVisible("Tus Negocios");
        assertTextVisible("Agregar Negocio");
        assertTextVisible("Tienes 2 de 3 negocios");

        final WebElement section = visibleElement(By.xpath(
                "//*[normalize-space()='Tus Negocios']/ancestor::*[self::section or self::div][1]"));
        final int entries = section.findElements(By.xpath(".//*[self::li or self::tr or contains(@class,'card')]")).size();
        Assert.assertTrue("Business list was not visible in 'Tus Negocios'.", entries > 0 || section.getText().length() > 80);
    }

    private void stepValidateTerminosYCondiciones() {
        final String heading = "Términos y Condiciones";
        clickLegalLinkAndValidate(
                heading,
                "08_terminos_y_condiciones",
                url -> terminosFinalUrl = url);
    }

    private void stepValidatePoliticaPrivacidad() {
        final String heading = "Política de Privacidad";
        clickLegalLinkAndValidate(
                heading,
                "09_politica_de_privacidad",
                url -> politicaFinalUrl = url);
    }

    private void clickLegalLinkAndValidate(final String linkText, final String screenshotName, final UrlConsumer urlConsumer) {
        final Set<String> beforeHandles = new LinkedHashSet<>(driver.getWindowHandles());
        if ("Términos y Condiciones".equals(linkText)) {
            clickFirstVisibleText("Términos y Condiciones", "Terminos y Condiciones");
        } else if ("Política de Privacidad".equals(linkText)) {
            clickFirstVisibleText("Política de Privacidad", "Politica de Privacidad");
        } else {
            clickFirstVisibleText(linkText);
        }

        String originalHandle = driver.getWindowHandle();
        String workingHandle = originalHandle;
        boolean openedNewTab = false;

        try {
            wait.withTimeout(Duration.ofSeconds(12)).until(d -> d.getWindowHandles().size() > beforeHandles.size());
            for (final String handle : driver.getWindowHandles()) {
                if (!beforeHandles.contains(handle)) {
                    driver.switchTo().window(handle);
                    workingHandle = handle;
                    openedNewTab = true;
                    break;
                }
            }
        } catch (final TimeoutException ignored) {
            // Link may navigate in the same tab.
        } finally {
            wait.withTimeout(DEFAULT_TIMEOUT);
        }

        waitForUiLoad();
        if ("Términos y Condiciones".equals(linkText)) {
            assertAnyTextVisible("Términos y Condiciones", "Terminos y Condiciones");
        } else if ("Política de Privacidad".equals(linkText)) {
            assertAnyTextVisible("Política de Privacidad", "Politica de Privacidad");
        } else {
            assertAnyTextVisible(linkText);
        }
        final String legalBody = bodyText().trim();
        Assert.assertTrue("Legal content text is not visible for " + linkText + ".", legalBody.length() > 120);
        takeScreenshot(screenshotName);
        urlConsumer.accept(driver.getCurrentUrl());

        if (openedNewTab) {
            driver.close();
            driver.switchTo().window(applicationWindow);
            waitForUiLoad();
            return;
        }

        driver.navigate().back();
        waitForUiLoad();
        if (!driver.getWindowHandle().equals(workingHandle)) {
            driver.switchTo().window(applicationWindow);
            waitForUiLoad();
        }
    }

    private void maybeSelectGoogleAccount(final Set<String> beforeHandles, final String accountEmail) {
        waitForUiLoad();

        try {
            wait.withTimeout(Duration.ofSeconds(10)).until(d -> d.getWindowHandles().size() > beforeHandles.size());
            for (final String handle : driver.getWindowHandles()) {
                if (!beforeHandles.contains(handle)) {
                    driver.switchTo().window(handle);
                    break;
                }
            }
        } catch (final TimeoutException ignored) {
            // Google selector might be in the same window.
        } finally {
            wait.withTimeout(DEFAULT_TIMEOUT);
        }

        final By accountBy = By.xpath("//*[normalize-space()='" + accountEmail + "']");
        final List<WebElement> accountMatches = driver.findElements(accountBy);
        if (!accountMatches.isEmpty() && accountMatches.get(0).isDisplayed()) {
            safeClick(accountMatches.get(0));
            waitForUiLoad();
        }

        if (!driver.getWindowHandle().equals(applicationWindow) && driver.getWindowHandles().contains(applicationWindow)) {
            driver.switchTo().window(applicationWindow);
            waitForUiLoad();
        }
    }

    private void assertSidebarVisible() {
        final List<WebElement> sidebars = driver.findElements(By.xpath("//aside | //nav"));
        final boolean anyVisible = sidebars.stream().anyMatch(WebElement::isDisplayed);
        Assert.assertTrue("Left sidebar navigation is not visible.", anyVisible);
    }

    private WebElement findNombreNegocioInput() {
        final List<By> candidates = List.of(
                By.xpath("//input[@placeholder='Nombre del Negocio']"),
                By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]"),
                By.xpath("//*[normalize-space()='Nombre del Negocio']/following::input[1]"));

        for (final By by : candidates) {
            final List<WebElement> elements = driver.findElements(by);
            if (!elements.isEmpty() && elements.get(0).isDisplayed()) {
                return elements.get(0);
            }
        }
        return null;
    }

    private void clickFirstVisibleText(final String... texts) {
        Throwable lastError = null;

        for (final String text : texts) {
            final String safe = xpathLiteral(text);
            final List<By> locators = List.of(
                    By.xpath("//*[self::button or self::a or @role='button'][normalize-space()=" + safe + "]"),
                    By.xpath("//*[normalize-space()=" + safe + "]"));

            for (final By locator : locators) {
                try {
                    final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
                    safeClick(element);
                    waitForUiLoad();
                    return;
                } catch (final Throwable t) {
                    lastError = t;
                }
            }
        }

        throw new AssertionError("Could not click an element with any visible text in " + List.of(texts), lastError);
    }

    private void safeClick(final WebElement element) {
        try {
            new Actions(driver).moveToElement(element).pause(Duration.ofMillis(100)).click().perform();
        } catch (final Throwable ignored) {
            try {
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
                element.click();
            } catch (final Throwable fallbackError) {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
            }
        }
    }

    private void assertTextVisible(final String text) {
        final String literal = xpathLiteral(text);
        final By exact = By.xpath("//*[normalize-space()=" + literal + "]");
        visibleElement(exact);
    }

    private void assertAnyTextVisible(final String... options) {
        Throwable lastError = null;
        for (final String option : options) {
            try {
                assertTextVisible(option);
                return;
            } catch (final Throwable t) {
                lastError = t;
            }
        }
        throw new AssertionError("None of the expected texts were visible: " + List.of(options), lastError);
    }

    private boolean isTextVisible(final String text) {
        final String literal = xpathLiteral(text);
        final List<WebElement> matches = driver.findElements(By.xpath("//*[normalize-space()=" + literal + "]"));
        return matches.stream().anyMatch(WebElement::isDisplayed);
    }

    private WebElement visibleElement(final By by) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(by));
    }

    private void waitForUiLoad() {
        final ExpectedCondition<Boolean> pageReady = wd -> {
            final Object state = ((JavascriptExecutor) wd).executeScript("return document.readyState");
            return "complete".equals(String.valueOf(state));
        };
        wait.until(pageReady);
        try {
            Thread.sleep(500);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String bodyText() {
        return driver.findElement(By.tagName("body")).getText();
    }

    private void executeStep(final String reportField, final Runnable action) {
        try {
            action.run();
            report.put(reportField, "PASS");
        } catch (final Throwable t) {
            report.put(reportField, "FAIL");
            failures.add(reportField + " -> " + t.getMessage());
            takeScreenshot("FAILED_" + reportField.replaceAll("[^A-Za-z0-9_]+", "_"));
        }
    }

    private void initializeReportFields() {
        report.put("Login", "NOT_RUN");
        report.put("Mi Negocio menu", "NOT_RUN");
        report.put("Agregar Negocio modal", "NOT_RUN");
        report.put("Administrar Negocios view", "NOT_RUN");
        report.put("Informacion General", "NOT_RUN");
        report.put("Detalles de la Cuenta", "NOT_RUN");
        report.put("Tus Negocios", "NOT_RUN");
        report.put("Terminos y Condiciones", "NOT_RUN");
        report.put("Politica de Privacidad", "NOT_RUN");
    }

    private void takeScreenshot(final String checkpointName) {
        if (!(driver instanceof TakesScreenshot)) {
            return;
        }

        try {
            final byte[] png = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            final String fileName = checkpointName + "_" + System.currentTimeMillis() + ".png";
            Files.write(evidenceDir.resolve(fileName), png);
        } catch (final IOException ignored) {
            // Screenshot is evidence only and should never block the run.
        }
    }

    private void writeFinalReport() {
        if (reportFile == null) {
            return;
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("saleads_mi_negocio_full_test").append('\n');
        sb.append("Generated at: ").append(LocalDateTime.now()).append('\n');
        sb.append('\n');
        sb.append("Results by field:").append('\n');
        for (final Map.Entry<String, String> entry : report.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        }
        sb.append('\n');
        sb.append("Evidence:").append('\n');
        sb.append("- Terminos y Condiciones final URL: ").append(terminosFinalUrl).append('\n');
        sb.append("- Politica de Privacidad final URL: ").append(politicaFinalUrl).append('\n');
        sb.append("- Screenshots directory: ").append(evidenceDir.toAbsolutePath()).append('\n');

        if (!failures.isEmpty()) {
            sb.append('\n').append("Failures:").append('\n');
            for (final String failure : failures) {
                sb.append("- ").append(failure).append('\n');
            }
        }

        try {
            Files.writeString(reportFile, sb.toString(), StandardCharsets.UTF_8);
        } catch (final IOException ignored) {
            // Final report write should not hide test assertion result.
        }
    }

    private static String resolveConfig(final String property, final String env, final String fallback) {
        final String propertyValue = System.getProperty(property);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue.trim();
        }
        final String envValue = System.getenv(env);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }
        return fallback;
    }

    private static String xpathLiteral(final String value) {
        if (!value.contains("'")) {
            return "'" + value + "'";
        }

        final String[] parts = value.split("'");
        final StringBuilder result = new StringBuilder("concat(");
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                result.append(",\"'\",");
            }
            result.append("'").append(parts[i]).append("'");
        }
        result.append(")");
        return result.toString();
    }

    @FunctionalInterface
    private interface UrlConsumer {
        void accept(String url);
    }
}
