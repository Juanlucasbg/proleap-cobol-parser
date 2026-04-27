package io.proleap.saleads.e2e;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
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
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SaleadsMiNegocioFullTest {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private WebDriver driver;
    private WebDriverWait wait;
    private Path evidenceDir;

    private final Map<String, Boolean> reportStatus = new LinkedHashMap<>();
    private final Map<String, String> reportDetails = new LinkedHashMap<>();
    private final Map<String, String> reportUrls = new LinkedHashMap<>();

    @Before
    public void setUp() throws Exception {
        final boolean runEnabled = Boolean.parseBoolean(readConfig("saleads.run.enabled", "SALEADS_RUN_ENABLED", "false"));
        Assume.assumeTrue("Skipping test: enable with SALEADS_RUN_ENABLED=true (or -Dsaleads.run.enabled=true).", runEnabled);

        final String startUrl = readConfig("saleads.start.url", "SALEADS_START_URL", "").trim();

        final ChromeOptions options = new ChromeOptions();
        options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox", "--disable-gpu");
        if (Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "true"))) {
            options.addArguments("--headless=new");
        }

        final String remoteWebDriverUrl = readConfig("saleads.remote.url", "SALEADS_REMOTE_URL", "").trim();
        if (remoteWebDriverUrl.isEmpty()) {
            driver = new ChromeDriver(options);
        } else {
            driver = new RemoteWebDriver(parseUrl(remoteWebDriverUrl), options);
        }

        wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
        evidenceDir = createEvidenceDir();
        if (!startUrl.isEmpty()) {
            driver.get(startUrl);
        }
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
        runStep("Login", this::stepLoginWithGoogle);
        runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
        runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
        runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
        runStep("Información General", this::stepValidateInformacionGeneral);
        runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
        runStep("Tus Negocios", this::stepValidateTusNegocios);
        runStep("Términos y Condiciones", () -> {
            final String url = stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones", "08-terminos");
            reportUrls.put("Términos y Condiciones URL", url);
        });
        runStep("Política de Privacidad", () -> {
            final String url = stepValidateLegalLink("Política de Privacidad", "Política de Privacidad", "09-politica-privacidad");
            reportUrls.put("Política de Privacidad URL", url);
        });

        printFinalReport();
        assertNoStepFailed();
    }

    private void stepLoginWithGoogle() {
        clickByVisibleText(
                "Sign in with Google",
                "Iniciar sesión con Google",
                "Iniciar sesion con Google",
                "Continuar con Google",
                "Login with Google"
        );

        clickIfVisible("juanlucasbarbiergarzon@gmail.com", Duration.ofSeconds(15));
        waitForUiToLoad();

        assertAnyTextVisible("Negocio", "Mi Negocio", "Dashboard", "Inicio");
        assertSidebarVisible();
        screenshot("01-dashboard-loaded");
    }

    private void stepOpenMiNegocioMenu() {
        clickIfVisible("Negocio", Duration.ofSeconds(10));
        clickByVisibleText("Mi Negocio");
        waitForUiToLoad();

        assertAnyTextVisible("Agregar Negocio");
        assertAnyTextVisible("Administrar Negocios");
        screenshot("02-mi-negocio-menu-expanded");
    }

    private void stepValidateAgregarNegocioModal() {
        clickByVisibleText("Agregar Negocio");
        waitForUiToLoad();

        assertAnyTextVisible("Crear Nuevo Negocio");
        assertInputByLabelVisible("Nombre del Negocio");
        assertAnyTextVisible("Tienes 2 de 3 negocios");
        assertAnyTextVisible("Cancelar");
        assertAnyTextVisible("Crear Negocio");
        screenshot("03-agregar-negocio-modal");

        typeIfVisible("Nombre del Negocio", "Negocio Prueba Automatización");
        clickByVisibleText("Cancelar");
        waitForUiToLoad();
    }

    private void stepOpenAdministrarNegocios() {
        clickIfVisible("Mi Negocio", Duration.ofSeconds(10));
        clickByVisibleText("Administrar Negocios");
        waitForUiToLoad();

        assertAnyTextVisible("Información General");
        assertAnyTextVisible("Detalles de la Cuenta");
        assertAnyTextVisible("Tus Negocios");
        assertAnyTextVisible("Sección Legal");
        screenshot("04-administrar-negocios-view");
    }

    private void stepValidateInformacionGeneral() {
        assertUserNameVisible();
        assertEmailVisible();
        assertAnyTextVisible("BUSINESS PLAN");
        assertAnyTextVisible("Cambiar Plan");
    }

    private void stepValidateDetallesCuenta() {
        assertAnyTextVisible("Cuenta creada");
        assertAnyTextVisible("Estado activo");
        assertAnyTextVisible("Idioma seleccionado");
    }

    private void stepValidateTusNegocios() {
        assertAnyTextVisible("Tus Negocios");
        assertAnyTextVisible("Agregar Negocio");
        assertAnyTextVisible("Tienes 2 de 3 negocios");
    }

    private String stepValidateLegalLink(final String linkText, final String headingText, final String screenshotName) {
        final String originalHandle = driver.getWindowHandle();
        final String originalUrl = driver.getCurrentUrl();
        final Set<String> existingHandles = new LinkedHashSet<>(driver.getWindowHandles());

        clickByVisibleText(linkText);

        String legalHandle = originalHandle;
        boolean switchedToNewTab = false;
        try {
            new WebDriverWait(driver, Duration.ofSeconds(12))
                    .until(d -> d.getWindowHandles().size() > existingHandles.size());
            final Set<String> newHandles = new LinkedHashSet<>(driver.getWindowHandles());
            newHandles.removeAll(existingHandles);
            if (!newHandles.isEmpty()) {
                legalHandle = newHandles.iterator().next();
                driver.switchTo().window(legalHandle);
                switchedToNewTab = true;
            }
        } catch (final TimeoutException ignored) {
            new WebDriverWait(driver, Duration.ofSeconds(12))
                    .until(d -> !Objects.equals(d.getCurrentUrl(), originalUrl));
        }

        waitForUiToLoad();
        assertAnyTextVisible(headingText);
        assertLegalContentVisible();
        screenshot(screenshotName);
        final String finalUrl = driver.getCurrentUrl();

        if (switchedToNewTab) {
            driver.close();
            driver.switchTo().window(originalHandle);
            waitForUiToLoad();
        } else {
            driver.navigate().back();
            waitForUiToLoad();
        }

        return finalUrl;
    }

    private void runStep(final String reportField, final CheckedRunnable action) {
        try {
            action.run();
            reportStatus.put(reportField, true);
            reportDetails.put(reportField, "PASS");
        } catch (final Throwable throwable) {
            reportStatus.put(reportField, false);
            final String detail = throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
            reportDetails.put(reportField, "FAIL - " + detail);
            screenshot("failure-" + sanitizeForPath(reportField));
        }
    }

    private void assertNoStepFailed() {
        final List<String> failed = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : reportStatus.entrySet()) {
            if (!entry.getValue()) {
                failed.add(entry.getKey());
            }
        }

        Assert.assertTrue("One or more validations failed: " + failed, failed.isEmpty());
    }

    private void printFinalReport() {
        System.out.println("==== saleads_mi_negocio_full_test report ====");
        for (Map.Entry<String, String> entry : reportDetails.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
        for (Map.Entry<String, String> entry : reportUrls.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
        System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
        System.out.println("=============================================");
    }

    private void clickByVisibleText(final String... labels) {
        TimeoutException lastException = null;
        for (String label : labels) {
            final List<By> locators = textLocators(label);
            for (By locator : locators) {
                try {
                    final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
                    scrollIntoView(element);
                    try {
                        element.click();
                    } catch (final Exception clickException) {
                        js().executeScript("arguments[0].click();", element);
                    }
                    waitForUiToLoad();
                    return;
                } catch (TimeoutException timeoutException) {
                    lastException = timeoutException;
                }
            }
        }

        throw new AssertionError("Could not click any visible text option: " + String.join(", ", labels), lastException);
    }

    private void clickIfVisible(final String label, final Duration timeout) {
        final Duration effectiveTimeout = timeout == null ? Duration.ofSeconds(5) : timeout;
        final WebDriverWait shortWait = new WebDriverWait(driver, effectiveTimeout);

        for (By locator : textLocators(label)) {
            try {
                final WebElement element = shortWait.until(ExpectedConditions.elementToBeClickable(locator));
                scrollIntoView(element);
                element.click();
                waitForUiToLoad();
                return;
            } catch (TimeoutException ignored) {
                // best effort
            }
        }
    }

    private void assertAnyTextVisible(final String... texts) {
        TimeoutException lastException = null;
        for (String text : texts) {
            for (By locator : textOnlyLocators(text)) {
                try {
                    wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
                    return;
                } catch (TimeoutException timeoutException) {
                    lastException = timeoutException;
                }
            }
        }
        throw new AssertionError("Expected visible text not found. Options: " + String.join(", ", texts), lastException);
    }

    private void assertInputByLabelVisible(final String label) {
        final String literal = toXPathLiteral(label);
        final By labelInputLocator = By.xpath(
                "(//label[normalize-space(.)=" + literal + "]/following::input[1] | " +
                        "//*[@placeholder=" + literal + "] | " +
                        "//*[contains(normalize-space(.), " + literal + ")]/following::input[1])[1]"
        );
        wait.until(ExpectedConditions.visibilityOfElementLocated(labelInputLocator));
    }

    private void typeIfVisible(final String label, final String value) {
        final String literal = toXPathLiteral(label);
        final By inputLocator = By.xpath(
                "(//label[normalize-space(.)=" + literal + "]/following::input[1] | " +
                        "//input[@placeholder=" + literal + "] | " +
                        "//input[contains(@placeholder, " + literal + ")])[1]"
        );
        final List<WebElement> elements = driver.findElements(inputLocator);
        if (!elements.isEmpty() && elements.get(0).isDisplayed()) {
            final WebElement input = elements.get(0);
            scrollIntoView(input);
            input.clear();
            input.sendKeys(value);
            waitForUiToLoad();
        }
    }

    private void assertSidebarVisible() {
        final List<By> sidebarLocators = List.of(
                By.cssSelector("aside"),
                By.cssSelector("nav"),
                By.xpath("//*[contains(@class,'sidebar') or contains(@class,'SideBar')]")
        );

        for (By locator : sidebarLocators) {
            final List<WebElement> elements = driver.findElements(locator);
            if (!elements.isEmpty() && elements.get(0).isDisplayed()) {
                return;
            }
        }

        // Fallback: menu labels are visible even if semantic sidebar tags differ.
        assertAnyTextVisible("Negocio", "Mi Negocio");
    }

    private void assertEmailVisible() {
        final By emailLocator = By.xpath("//*[contains(text(),'@') and contains(text(),'.')]");
        wait.until(ExpectedConditions.visibilityOfElementLocated(emailLocator));
    }

    private void assertUserNameVisible() {
        final String expectedName = readConfig("saleads.expected.user.name", "SALEADS_EXPECTED_USER_NAME", "").trim();
        if (!expectedName.isEmpty()) {
            assertAnyTextVisible(expectedName);
            return;
        }

        final WebElement container = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("//*[normalize-space(.)='Información General']/ancestor::*[self::section or self::div][1]")));
        final List<WebElement> candidates = container.findElements(By.xpath(".//*[normalize-space(text())!='']"));

        for (WebElement candidate : candidates) {
            if (!candidate.isDisplayed()) {
                continue;
            }
            final String text = candidate.getText().trim();
            if (text.length() < 3 || text.length() > 80) {
                continue;
            }
            if (text.contains("@")) {
                continue;
            }
            if (text.equalsIgnoreCase("Información General")
                    || text.equalsIgnoreCase("BUSINESS PLAN")
                    || text.equalsIgnoreCase("Cambiar Plan")
                    || text.equalsIgnoreCase("Cuenta creada")
                    || text.equalsIgnoreCase("Estado activo")
                    || text.equalsIgnoreCase("Idioma seleccionado")
                    || text.equalsIgnoreCase("Tus Negocios")
                    || text.equalsIgnoreCase("Sección Legal")) {
                continue;
            }
            if (text.matches(".*[A-Za-zÁÉÍÓÚÜÑáéíóúüñ].*")) {
                return;
            }
        }

        throw new AssertionError(
                "User name was not clearly visible. Set SALEADS_EXPECTED_USER_NAME for a strict assertion if needed.");
    }

    private void assertLegalContentVisible() {
        final By contentLocator = By.xpath("//body//*[string-length(normalize-space(.)) > 80]");
        wait.until(ExpectedConditions.visibilityOfElementLocated(contentLocator));
    }

    private List<By> textLocators(final String text) {
        final String literal = toXPathLiteral(text);
        return List.of(
                By.xpath("//*[self::button or self::a or @role='button'][normalize-space(.)=" + literal + "]"),
                By.xpath("//*[self::button or self::a or @role='button'][contains(normalize-space(.), " + literal + ")]"),
                By.xpath("//*[normalize-space(.)=" + literal + "]"),
                By.xpath("//*[contains(normalize-space(.), " + literal + ")]")
        );
    }

    private List<By> textOnlyLocators(final String text) {
        final String literal = toXPathLiteral(text);
        return List.of(
                By.xpath("//*[normalize-space(.)=" + literal + "]"),
                By.xpath("//*[contains(normalize-space(.), " + literal + ")]")
        );
    }

    private void waitForUiToLoad() {
        wait.until(d -> "complete".equals(js().executeScript("return document.readyState")));

        final List<By> loaders = List.of(
                By.cssSelector("[aria-busy='true']"),
                By.cssSelector("[role='progressbar']"),
                By.cssSelector(".loading"),
                By.cssSelector(".spinner"),
                By.cssSelector(".ant-spin-spinning")
        );
        for (By loader : loaders) {
            try {
                new WebDriverWait(driver, Duration.ofSeconds(2))
                        .until(ExpectedConditions.invisibilityOfElementLocated(loader));
            } catch (TimeoutException ignored) {
                // Ignore and continue. Some pages keep static spinner nodes in DOM.
            }
        }
    }

    private void scrollIntoView(final WebElement element) {
        js().executeScript("arguments[0].scrollIntoView({block:'center', inline:'center'});", element);
    }

    private JavascriptExecutor js() {
        return (JavascriptExecutor) driver;
    }

    private Path createEvidenceDir() {
        final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        final Path dir = Path.of("target", "saleads-evidence", "saleads-mi-negocio-" + timestamp);
        try {
            Files.createDirectories(dir);
            return dir;
        } catch (Exception e) {
            throw new RuntimeException("Could not create evidence directory: " + dir, e);
        }
    }

    private void screenshot(final String checkpointName) {
        if (!(driver instanceof TakesScreenshot)) {
            return;
        }
        try {
            final File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            final Path destination = evidenceDir.resolve(sanitizeForPath(checkpointName) + ".png");
            Files.copy(source.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
            // Screenshot capture is evidence-only; do not break the test because of I/O failures.
        }
    }

    private URL parseUrl(final String value) {
        try {
            return new URL(value);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid SALEADS_REMOTE_URL: " + value, e);
        }
    }

    private String sanitizeForPath(final String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9-_]+", "-").replaceAll("-{2,}", "-");
    }

    private String readConfig(final String systemPropertyName, final String envVarName, final String defaultValue) {
        final String fromSystemProperty = System.getProperty(systemPropertyName);
        if (fromSystemProperty != null) {
            return fromSystemProperty;
        }
        final String fromEnvironment = System.getenv(envVarName);
        return fromEnvironment == null ? defaultValue : fromEnvironment;
    }

    private String toXPathLiteral(final String text) {
        if (!text.contains("'")) {
            return "'" + text + "'";
        }
        if (!text.contains("\"")) {
            return "\"" + text + "\"";
        }
        final StringBuilder result = new StringBuilder("concat(");
        final char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            final char ch = chars[i];
            if (ch == '\'') {
                result.append("\"'\"");
            } else if (ch == '"') {
                result.append("'\"'");
            } else {
                int j = i;
                while (j < chars.length && chars[j] != '\'' && chars[j] != '"') {
                    j++;
                }
                result.append("'").append(text, i, j).append("'");
                i = j - 1;
            }
            if (i < chars.length - 1) {
                result.append(",");
            }
        }
        result.append(")");
        return result.toString();
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run();
    }
}
