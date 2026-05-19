package io.proleap.saleads;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.ElementClickInterceptedException;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchWindowException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class SaleadsMiNegocioFullWorkflowTest {

    private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
    private static final String TEST_BUSINESS_NAME = "Negocio Prueba Automatización";
    private static final Path SCREENSHOT_DIR = Paths.get("target", "surefire-reports", "screenshots", "saleads-mi-negocio");

    private final Map<String, String> stepResults = new LinkedHashMap<>();
    private final List<String> failures = new ArrayList<>();

    private WebDriver driver;
    private WebDriverWait wait;
    private String applicationWindow;
    private String termsUrl;
    private String privacyUrl;

    @Before
    public void setUp() throws IOException {
        Files.createDirectories(SCREENSHOT_DIR);

        final ChromeOptions options = new ChromeOptions();
        final String debuggerAddress = readConfig("saleads.debuggerAddress", "SALEADS_DEBUGGER_ADDRESS");
        if (hasText(debuggerAddress)) {
            options.setExperimentalOption("debuggerAddress", debuggerAddress);
        } else {
            WebDriverManager.chromedriver().setup();

            final boolean headless = Boolean.parseBoolean(readConfigOrDefault("saleads.headless", "SALEADS_HEADLESS", "true"));
            if (headless) {
                options.addArguments("--headless=new");
            }

            options.addArguments("--window-size=1920,2000");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-gpu");
        }

        driver = new ChromeDriver(options);
        final long waitSeconds = Long.parseLong(readConfigOrDefault("saleads.waitSeconds", "SALEADS_WAIT_SECONDS", "30"));
        wait = new WebDriverWait(driver, Duration.ofSeconds(waitSeconds));

        if (!hasText(debuggerAddress)) {
            final String loginUrl = resolveLoginUrl();
            driver.get(loginUrl);
            waitForUiToSettle();
        }

        applicationWindow = driver.getWindowHandle();
    }

    @After
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    @Test
    public void saleadsMiNegocioWorkflow() {
        runValidation("Login", this::stepLoginWithGoogle);
        runValidation("Mi Negocio menu", this::stepOpenMiNegocioMenu);
        runValidation("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
        runValidation("Administrar Negocios view", this::stepOpenAdministrarNegocios);
        runValidation("Información General", this::stepValidateInformacionGeneral);
        runValidation("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
        runValidation("Tus Negocios", this::stepValidateTusNegocios);
        runValidation("Términos y Condiciones", this::stepValidateTerminos);
        runValidation("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

        final String finalReport = buildFinalReport();
        System.out.println(finalReport);

        if (!failures.isEmpty()) {
            Assert.fail(finalReport);
        }
    }

    private void stepLoginWithGoogle() {
        final Set<String> windowsBeforeLogin = driver.getWindowHandles();
        clickByFirstAvailableText(Arrays.asList(
                "Sign in with Google",
                "Iniciar sesión con Google",
                "Continuar con Google",
                "Login with Google",
                "Ingresar con Google",
                "Google"
        ));

        switchToNewWindowIfOpened(windowsBeforeLogin);
        clickIfVisible(GOOGLE_ACCOUNT, Duration.ofSeconds(12));

        switchBackToApplicationWindow();

        waitForAnyVisibleText(Arrays.asList("Negocio", "Mi Negocio", "Dashboard", "Panel"));
        assertTextVisible("Negocio");

        screenshot("01-dashboard-loaded");
    }

    private void stepOpenMiNegocioMenu() {
        assertTextVisible("Negocio");
        clickIfVisible("Negocio", Duration.ofSeconds(5));
        clickByVisibleText("Mi Negocio");

        assertTextVisible("Agregar Negocio");
        assertTextVisible("Administrar Negocios");

        screenshot("02-mi-negocio-menu-expanded");
    }

    private void stepValidateAgregarNegocioModal() {
        clickByVisibleText("Agregar Negocio");

        assertTextVisible("Crear Nuevo Negocio");
        assertInputVisibleByLabel("Nombre del Negocio");
        assertTextVisible("Tienes 2 de 3 negocios");
        assertTextVisible("Cancelar");
        assertTextVisible("Crear Negocio");

        screenshot("03-agregar-negocio-modal");

        final WebElement nombreNegocioInput = waitForNombreNegocioInput();
        nombreNegocioInput.click();
        nombreNegocioInput.clear();
        nombreNegocioInput.sendKeys(TEST_BUSINESS_NAME);

        clickByVisibleText("Cancelar");
        waitForUiToSettle();
    }

    private void stepOpenAdministrarNegocios() {
        if (!isVisibleText("Administrar Negocios", Duration.ofSeconds(3))) {
            clickByVisibleText("Mi Negocio");
        }

        clickByVisibleText("Administrar Negocios");

        assertTextVisible("Información General");
        assertTextVisible("Detalles de la Cuenta");
        assertTextVisible("Tus Negocios");
        assertTextVisible("Sección Legal");

        screenshot("04-administrar-negocios-account-page");
    }

    private void stepValidateInformacionGeneral() {
        assertTextVisible("Información General");
        assertEmailVisible();
        assertElementVisible(By.xpath("//*[normalize-space(.)='Información General']/ancestor::*[self::section or self::div][1]//*[string-length(normalize-space()) > 1]"),
                "Expected user information values in Información General");
        assertTextVisible("BUSINESS PLAN");
        assertTextVisible("Cambiar Plan");
    }

    private void stepValidateDetallesCuenta() {
        assertTextVisible("Cuenta creada");
        assertTextVisible("Estado activo");
        assertTextVisible("Idioma seleccionado");
    }

    private void stepValidateTusNegocios() {
        assertTextVisible("Tus Negocios");
        assertTextVisible("Agregar Negocio");
        assertTextVisible("Tienes 2 de 3 negocios");
        assertElementVisible(By.xpath("//*[normalize-space(.)='Tus Negocios']/ancestor::*[self::section or self::div][1]//*[string-length(normalize-space()) > 1]"),
                "Expected visible business list content in Tus Negocios section");
    }

    private void stepValidateTerminos() {
        termsUrl = validateLegalPage("Términos y Condiciones", "Términos y Condiciones", "05-terminos-y-condiciones");
    }

    private void stepValidatePoliticaPrivacidad() {
        privacyUrl = validateLegalPage("Política de Privacidad", "Política de Privacidad", "06-politica-de-privacidad");
    }

    private String validateLegalPage(final String linkText, final String headingText, final String screenshotName) {
        final String originalWindow = driver.getWindowHandle();
        final String originalUrl = driver.getCurrentUrl();
        final Set<String> windowsBeforeClick = driver.getWindowHandles();

        clickByVisibleText(linkText);

        wait.until(d -> d.getWindowHandles().size() > windowsBeforeClick.size()
                || !d.getCurrentUrl().equals(originalUrl)
                || isVisibleText(headingText, Duration.ofSeconds(2)));

        final String newWindow = getNewWindowHandle(windowsBeforeClick);
        if (newWindow != null) {
            driver.switchTo().window(newWindow);
            waitForUiToSettle();
        }

        assertTextVisible(headingText);
        assertLegalContentVisible();
        screenshot(screenshotName);

        final String finalUrl = driver.getCurrentUrl();

        if (newWindow != null) {
            driver.close();
            driver.switchTo().window(originalWindow);
            waitForUiToSettle();
        } else if (!driver.getCurrentUrl().equals(originalUrl)) {
            driver.navigate().back();
            waitForUiToSettle();
        }

        return finalUrl;
    }

    private void runValidation(final String stepName, final ThrowingRunnable validation) {
        try {
            validation.run();
            stepResults.put(stepName, "PASS");
        } catch (final Throwable throwable) {
            stepResults.put(stepName, "FAIL");
            failures.add(stepName + " -> " + throwable.getMessage());
            screenshot("failed-" + stepName);
            switchBackToApplicationWindow();
        }
    }

    private void clickByFirstAvailableText(final List<String> texts) {
        final List<String> errors = new ArrayList<>();

        for (final String text : texts) {
            try {
                clickByVisibleText(text);
                return;
            } catch (final RuntimeException runtimeException) {
                errors.add(text + ": " + runtimeException.getMessage());
            }
        }

        throw new IllegalStateException("Could not click any candidate text: " + errors);
    }

    private void clickByVisibleText(final String text) {
        final WebElement element = waitForVisibleTextElement(text);

        try {
            element.click();
        } catch (final ElementClickInterceptedException ex) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
        }

        waitForUiToSettle();
    }

    private void clickIfVisible(final String text, final Duration timeout) {
        final Optional<WebElement> element = findVisibleTextElement(text, timeout);
        element.ifPresent(webElement -> {
            try {
                webElement.click();
            } catch (final ElementClickInterceptedException ex) {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", webElement);
            }

            waitForUiToSettle();
        });
    }

    private WebElement waitForVisibleTextElement(final String text) {
        return wait.until(driver -> findVisibleTextElement(text, Duration.ofSeconds(1)).orElse(null));
    }

    private Optional<WebElement> findVisibleTextElement(final String text, final Duration timeout) {
        final long deadline = System.currentTimeMillis() + timeout.toMillis();

        while (System.currentTimeMillis() < deadline) {
            final Optional<WebElement> element = findVisibleTextElement(text);
            if (element.isPresent()) {
                return element;
            }

            sleep(200);
        }

        return Optional.empty();
    }

    private Optional<WebElement> findVisibleTextElement(final String text) {
        final String literal = xpathLiteral(text);
        final List<By> locators = Arrays.asList(
                By.xpath("//button[normalize-space(.)=" + literal + "]"),
                By.xpath("//a[normalize-space(.)=" + literal + "]"),
                By.xpath("//*[@role='button' and normalize-space(.)=" + literal + "]"),
                By.xpath("//*[self::span or self::div or self::p or self::li][normalize-space(.)=" + literal + "]"),
                By.xpath("//*[contains(normalize-space(.)," + literal + ")]")
        );

        for (final By locator : locators) {
            final List<WebElement> elements = driver.findElements(locator);
            for (final WebElement element : elements) {
                if (element.isDisplayed()) {
                    return Optional.of(element);
                }
            }
        }

        return Optional.empty();
    }

    private void waitForAnyVisibleText(final List<String> texts) {
        wait.until(driver -> {
            for (final String text : texts) {
                if (findVisibleTextElement(text).isPresent()) {
                    return true;
                }
            }

            return false;
        });
    }

    private void assertTextVisible(final String text) {
        final Optional<WebElement> element = findVisibleTextElement(text, Duration.ofSeconds(10));
        if (!element.isPresent()) {
            throw new AssertionError("Expected visible text not found: " + text);
        }
    }

    private void assertInputVisibleByLabel(final String labelText) {
        final String labelLiteral = xpathLiteral(labelText);
        final By locator = By.xpath(
                "//input[contains(@placeholder," + labelLiteral + ")]"
                        + "|//input[contains(@aria-label," + labelLiteral + ")]"
                        + "|//label[contains(normalize-space(.)," + labelLiteral + ")]/following::input[1]"
        );
        assertElementVisible(locator, "Expected visible input field for label: " + labelText);
    }

    private WebElement waitForNombreNegocioInput() {
        final String labelLiteral = xpathLiteral("Nombre del Negocio");
        final By locator = By.xpath(
                "//input[contains(@placeholder," + labelLiteral + ")]"
                        + "|//input[contains(@aria-label," + labelLiteral + ")]"
                        + "|//label[contains(normalize-space(.)," + labelLiteral + ")]/following::input[1]"
        );

        return wait.until(driver -> {
            final List<WebElement> elements = driver.findElements(locator);
            for (final WebElement element : elements) {
                if (element.isDisplayed()) {
                    return element;
                }
            }

            return null;
        });
    }

    private void assertEmailVisible() {
        assertElementVisible(By.xpath("//*[contains(normalize-space(.), '@') and contains(normalize-space(.), '.') and not(self::script)]"),
                "Expected visible email in account details");
    }

    private void assertLegalContentVisible() {
        assertElementVisible(By.xpath("//p[string-length(normalize-space()) > 40] | //div[string-length(normalize-space()) > 120]"),
                "Expected legal content text to be visible");
    }

    private void assertElementVisible(final By locator, final String errorMessage) {
        final WebElement element = wait.until(driver -> {
            final List<WebElement> elements = driver.findElements(locator);
            for (final WebElement webElement : elements) {
                if (webElement.isDisplayed()) {
                    return webElement;
                }
            }

            return null;
        });

        if (element == null) {
            throw new AssertionError(errorMessage);
        }
    }

    private boolean isVisibleText(final String text, final Duration timeout) {
        return findVisibleTextElement(text, timeout).isPresent();
    }

    private void switchToNewWindowIfOpened(final Set<String> previousHandles) {
        final String newWindow = getNewWindowHandle(previousHandles);
        if (newWindow != null) {
            driver.switchTo().window(newWindow);
            waitForUiToSettle();
        }
    }

    private String getNewWindowHandle(final Set<String> previousHandles) {
        for (final String handle : driver.getWindowHandles()) {
            if (!previousHandles.contains(handle)) {
                return handle;
            }
        }

        return null;
    }

    private void switchBackToApplicationWindow() {
        try {
            if (applicationWindow != null && driver.getWindowHandles().contains(applicationWindow)) {
                driver.switchTo().window(applicationWindow);
                return;
            }

            final Set<String> handles = driver.getWindowHandles();
            if (!handles.isEmpty()) {
                driver.switchTo().window(handles.iterator().next());
            }
        } catch (final NoSuchWindowException ignored) {
            // Ignore and continue; a later step will report a failure if no usable window remains.
        }
    }

    private void waitForUiToSettle() {
        final ExpectedCondition<Boolean> documentReady = webDriver -> {
            final Object readyState = ((JavascriptExecutor) webDriver).executeScript("return document.readyState");
            return "complete".equals(readyState) || "interactive".equals(readyState);
        };

        wait.until(documentReady);
        sleep(600);
    }

    private void screenshot(final String checkpointName) {
        if (driver == null) {
            return;
        }

        final String safeName = checkpointName
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        final Path destination = SCREENSHOT_DIR.resolve(System.currentTimeMillis() + "-" + safeName + ".png");

        try {
            final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (final Exception ignored) {
            // Screenshots are evidence and should not prevent workflow completion.
        }
    }

    private String resolveLoginUrl() {
        final List<String> propertyKeys = Arrays.asList("saleads.loginUrl", "saleads.url", "saleads.baseUrl");
        for (final String propertyKey : propertyKeys) {
            final String value = System.getProperty(propertyKey);
            if (hasText(value)) {
                return value;
            }
        }

        final List<String> envKeys = Arrays.asList("SALEADS_LOGIN_URL", "SALEADS_URL", "SALEADS_BASE_URL");
        for (final String envKey : envKeys) {
            final String value = System.getenv(envKey);
            if (hasText(value)) {
                return value;
            }
        }

        throw new IllegalStateException(
                "No SaleADS login URL configured. Provide -Dsaleads.loginUrl or SALEADS_LOGIN_URL. "
                        + "Use -Dsaleads.debuggerAddress when attaching to a browser already on the login page."
        );
    }

    private String readConfigOrDefault(final String propertyName, final String envName, final String fallbackValue) {
        final String configured = readConfig(propertyName, envName);
        return hasText(configured) ? configured : fallbackValue;
    }

    private String readConfig(final String propertyName, final String envName) {
        final String propertyValue = System.getProperty(propertyName);
        if (hasText(propertyValue)) {
            return propertyValue;
        }

        final String envValue = System.getenv(envName);
        if (hasText(envValue)) {
            return envValue;
        }

        return null;
    }

    private String xpathLiteral(final String value) {
        if (!value.contains("'")) {
            return "'" + value + "'";
        }

        if (!value.contains("\"")) {
            return "\"" + value + "\"";
        }

        final StringBuilder sb = new StringBuilder("concat(");
        final char[] chars = value.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            final String literal;
            if (chars[i] == '\'') {
                literal = "\"'\"";
            } else if (chars[i] == '\"') {
                literal = "'\"'";
            } else {
                literal = "'" + chars[i] + "'";
            }

            sb.append(literal);
            if (i < chars.length - 1) {
                sb.append(',');
            }
        }

        sb.append(')');
        return sb.toString();
    }

    private boolean hasText(final String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void sleep(final long milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (final InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private String buildFinalReport() {
        final List<String> orderedFields = Arrays.asList(
                "Login",
                "Mi Negocio menu",
                "Agregar Negocio modal",
                "Administrar Negocios view",
                "Información General",
                "Detalles de la Cuenta",
                "Tus Negocios",
                "Términos y Condiciones",
                "Política de Privacidad"
        );

        final StringBuilder builder = new StringBuilder();
        builder.append("SaleADS Mi Negocio Workflow Final Report\n");
        builder.append("======================================\n");

        for (final String field : orderedFields) {
            builder.append("- ").append(field).append(": ")
                    .append(stepResults.getOrDefault(field, "NOT EXECUTED"))
                    .append('\n');
        }

        if (hasText(termsUrl)) {
            builder.append("- Términos y Condiciones URL: ").append(termsUrl).append('\n');
        }
        if (hasText(privacyUrl)) {
            builder.append("- Política de Privacidad URL: ").append(privacyUrl).append('\n');
        }
        builder.append("- Screenshots: ").append(SCREENSHOT_DIR).append('\n');

        if (!failures.isEmpty()) {
            builder.append("Failures\n");
            builder.append("--------\n");
            for (final String failure : failures) {
                builder.append("* ").append(failure).append('\n');
            }
        }

        return builder.toString();
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }
}
