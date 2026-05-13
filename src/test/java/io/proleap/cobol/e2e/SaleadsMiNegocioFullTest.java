package io.proleap.cobol.e2e;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

    private static final DateTimeFormatter ARTIFACTS_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("\\b[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}\\b");

    private WebDriver driver;
    private WebDriverWait wait;
    private Path artifactsDir;

    private final Map<String, String> report = new LinkedHashMap<>();
    private final List<String> failures = new ArrayList<>();

    private String terminosUrl = "N/A";
    private String privacidadUrl = "N/A";

    @Before
    public void setUp() throws IOException {
        Assume.assumeTrue(
                "Set SALEADS_E2E_ENABLED=true to run this test.",
                "true".equalsIgnoreCase(env("SALEADS_E2E_ENABLED")));

        final String loginUrl = firstNonBlank(env("SALEADS_LOGIN_URL"), env("SALEADS_URL"), env("BASE_URL"));
        Assume.assumeTrue(
                "Set SALEADS_LOGIN_URL (or SALEADS_URL / BASE_URL) with the current environment login page.",
                loginUrl != null);

        artifactsDir = Path.of("target", "saleads-artifacts", LocalDateTime.now().format(ARTIFACTS_TIMESTAMP));
        Files.createDirectories(artifactsDir);

        final ChromeOptions options = new ChromeOptions();
        if (!"false".equalsIgnoreCase(env("HEADLESS"))) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox");

        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, WAIT_TIMEOUT);
        initReport();

        driver.get(loginUrl);
        waitForUiToLoad();
    }

    @After
    public void tearDown() {
        printFinalReport();
        if (driver != null) {
            driver.quit();
        }
        assertTrue("Failing validations:\n - " + String.join("\n - ", failures), failures.isEmpty());
    }

    @Test
    public void saleadsMiNegocioFullTest() {
        executeStep("Login", this::loginWithGoogleAndValidateDashboard);
        executeStep("Mi Negocio menu", this::openMiNegocioMenu);
        executeStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
        executeStep("Administrar Negocios view", this::openAdministrarNegocios);
        executeStep("Informacion General", this::validateInformacionGeneral);
        executeStep("Detalles de la Cuenta", this::validateDetallesCuenta);
        executeStep("Tus Negocios", this::validateTusNegocios);
        executeStep("Terminos y Condiciones", this::validateTerminosYCondiciones);
        executeStep("Politica de Privacidad", this::validatePoliticaDePrivacidad);
    }

    private void loginWithGoogleAndValidateDashboard() throws IOException {
        clickByVisibleText("Sign in with Google", "Inicia sesion con Google", "Iniciar sesion con Google", "Google");
        waitForUiToLoad();

        if (isElementVisible(By.xpath("//*[contains(normalize-space(.), 'juanlucasbarbiergarzon@gmail.com')]"), 8)) {
            click(By.xpath("//*[contains(normalize-space(.), 'juanlucasbarbiergarzon@gmail.com')]"));
            waitForUiToLoad();
        }

        assertElementVisible(By.xpath("//aside | //nav"));
        assertElementVisible(byText("Negocio"));
        takeScreenshot("01-dashboard-loaded.png");
    }

    private void openMiNegocioMenu() throws IOException {
        clickIfVisible(byText("Negocio"));
        clickByVisibleText("Mi Negocio");
        waitForUiToLoad();

        assertElementVisible(byText("Agregar Negocio"));
        assertElementVisible(byText("Administrar Negocios"));
        takeScreenshot("02-mi-negocio-menu-expanded.png");
    }

    private void validateAgregarNegocioModal() throws IOException {
        clickByVisibleText("Agregar Negocio");
        waitForUiToLoad();

        assertElementVisible(byText("Crear Nuevo Negocio"));
        assertElementVisible(
                By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')] | //label[contains(., 'Nombre del Negocio')]"));
        assertElementVisible(byText("Tienes 2 de 3 negocios"));
        assertElementVisible(byText("Cancelar"));
        assertElementVisible(byText("Crear Negocio"));

        click(By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')] | //label[contains(., 'Nombre del Negocio')]"));
        typeIntoFirstVisibleInput("Nombre del Negocio", "Negocio Prueba Automatizacion");
        takeScreenshot("03-agregar-negocio-modal.png");
        clickByVisibleText("Cancelar");
        waitForUiToLoad();
    }

    private void openAdministrarNegocios() throws IOException {
        if (!isElementVisible(byText("Administrar Negocios"), 2)) {
            clickByVisibleText("Mi Negocio");
            waitForUiToLoad();
        }

        clickByVisibleText("Administrar Negocios");
        waitForUiToLoad();

        assertElementVisible(byText("Informacion General"));
        assertElementVisible(byText("Detalles de la Cuenta"));
        assertElementVisible(byText("Tus Negocios"));
        assertElementVisible(byText("Seccion Legal"));
        takeScreenshot("04-administrar-negocios-view.png");
    }

    private void validateInformacionGeneral() {
        final WebElement section = findSectionByHeading("Informacion General");
        final String sectionText = normalize(section.getText());

        assertTrue("Expected an email address in Informacion General.", EMAIL_PATTERN.matcher(sectionText).find());
        assertTrue("Expected BUSINESS PLAN in Informacion General.", sectionText.contains("business plan"));
        assertTrue("Expected Cambiar Plan button in Informacion General.", sectionText.contains("cambiar plan"));
        assertTrue("Expected user name text in Informacion General.", hasNonTrivialNameLikeText(sectionText));
    }

    private void validateDetallesCuenta() {
        final WebElement section = findSectionByHeading("Detalles de la Cuenta");
        final String sectionText = normalize(section.getText());

        assertTrue("Expected 'Cuenta creada'.", sectionText.contains("cuenta creada"));
        assertTrue("Expected 'Estado activo'.", sectionText.contains("estado activo"));
        assertTrue("Expected 'Idioma seleccionado'.", sectionText.contains("idioma seleccionado"));
    }

    private void validateTusNegocios() {
        final WebElement section = findSectionByHeading("Tus Negocios");
        final String sectionText = normalize(section.getText());

        assertFalse("Expected business list to be visible.", section.findElements(By.xpath(".//*")).isEmpty());
        assertTrue("Expected 'Agregar Negocio' in Tus Negocios.", sectionText.contains("agregar negocio"));
        assertTrue("Expected 'Tienes 2 de 3 negocios' in Tus Negocios.", sectionText.contains("tienes 2 de 3 negocios"));
    }

    private void validateTerminosYCondiciones() throws IOException {
        terminosUrl = openLegalLinkValidateAndReturn(
                "Terminos y Condiciones",
                "terminos y condiciones",
                "08-terminos-y-condiciones.png");
    }

    private void validatePoliticaDePrivacidad() throws IOException {
        privacidadUrl = openLegalLinkValidateAndReturn(
                "Politica de Privacidad",
                "politica de privacidad",
                "09-politica-de-privacidad.png");
    }

    private String openLegalLinkValidateAndReturn(
            final String linkText, final String expectedHeading, final String screenshotName) throws IOException {
        final String appHandle = driver.getWindowHandle();
        final Set<String> oldHandles = new LinkedHashSet<>(driver.getWindowHandles());
        final String oldUrl = driver.getCurrentUrl();

        clickByVisibleText(linkText);
        waitForUiToLoad();

        try {
            wait.until(d -> d.getWindowHandles().size() > oldHandles.size() || !d.getCurrentUrl().equals(oldUrl));
        } catch (final TimeoutException ignored) {
            // The click may only refresh content in-place.
        }

        final Set<String> currentHandles = driver.getWindowHandles();
        final boolean openedNewTab = currentHandles.size() > oldHandles.size();

        if (openedNewTab) {
            for (final String handle : currentHandles) {
                if (!oldHandles.contains(handle)) {
                    driver.switchTo().window(handle);
                    break;
                }
            }
            waitForUiToLoad();
        }

        assertElementVisible(byText(expectedHeading));
        final String bodyText = normalize(driver.findElement(By.tagName("body")).getText());
        assertTrue("Expected legal content text to be visible.", bodyText.length() > 120);
        takeScreenshot(screenshotName);

        final String finalUrl = driver.getCurrentUrl();

        if (openedNewTab) {
            driver.close();
            driver.switchTo().window(appHandle);
            waitForUiToLoad();
        } else {
            driver.navigate().back();
            waitForUiToLoad();
        }

        assertElementVisible(byText("Seccion Legal"));
        return finalUrl;
    }

    private void executeStep(final String reportField, final CheckedRunnable action) {
        try {
            action.run();
            report.put(reportField, "PASS");
        } catch (final Throwable ex) {
            report.put(reportField, "FAIL");
            failures.add(reportField + " -> " + ex.getMessage());
        }
    }

    private void clickIfVisible(final By locator) {
        if (isElementVisible(locator, 5)) {
            click(locator);
        }
    }

    private void clickByVisibleText(final String... possibleTexts) {
        for (final String text : possibleTexts) {
            final By locator = byClickableText(text);
            if (isElementVisible(locator, 5)) {
                click(locator);
                return;
            }
        }
        throw new NoSuchElementException("No clickable element found for texts: " + Arrays.toString(possibleTexts));
    }

    private void click(final By locator) {
        wait.until(ExpectedConditions.elementToBeClickable(locator)).click();
        waitForUiToLoad();
    }

    private void waitForUiToLoad() {
        wait.until(d -> "complete".equals(
                ((org.openqa.selenium.JavascriptExecutor) d).executeScript("return document.readyState")));
    }

    private void assertElementVisible(final By locator) {
        wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    private boolean isElementVisible(final By locator, final int timeoutSeconds) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
                    .until(ExpectedConditions.visibilityOfElementLocated(locator));
            return true;
        } catch (final TimeoutException ex) {
            return false;
        }
    }

    private By byText(final String rawText) {
        final String normalized = normalize(rawText).replace("'", "");
        return By.xpath("//*[contains(translate(normalize-space(.), "
                + "'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑ', 'abcdefghijklmnopqrstuvwxyzaeiouun'), '"
                + normalized
                + "')]");
    }

    private By byClickableText(final String rawText) {
        final String normalized = normalize(rawText).replace("'", "");
        final String translatedText = "contains(translate(normalize-space(.), "
                + "'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑ', 'abcdefghijklmnopqrstuvwxyzaeiouun'), '"
                + normalized
                + "')";
        return By.xpath("//button[" + translatedText + "]"
                + " | //a[" + translatedText + "]"
                + " | //*[@role='button' and " + translatedText + "]"
                + " | //li[" + translatedText + "]");
    }

    private WebElement findSectionByHeading(final String heading) {
        final String normalizedHeading = normalize(heading).replace("'", "");
        final By headingLocator = By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4 or self::span or self::p or self::div]"
                + "[contains(translate(normalize-space(.), "
                + "'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑ', 'abcdefghijklmnopqrstuvwxyzaeiouun'), '"
                + normalizedHeading
                + "')]");
        final WebElement headingElement = wait.until(ExpectedConditions.visibilityOfElementLocated(headingLocator));

        WebElement current = headingElement;
        for (int i = 0; i < 6 && current != null; i++) {
            final String text = normalize(current.getText());
            if (text.contains(normalize(heading)) && text.length() > 40) {
                return current;
            }
            current = current.findElement(By.xpath("./.."));
        }

        return headingElement;
    }

    private void typeIntoFirstVisibleInput(final String labelText, final String value) {
        final List<By> locators = List.of(
                By.xpath("//input[contains(@placeholder, '" + labelText + "')]"),
                By.xpath("//label[contains(., '" + labelText + "')]/following::input[1]"),
                By.xpath("//input"));

        for (final By locator : locators) {
            final List<WebElement> elements = driver.findElements(locator);
            for (final WebElement element : elements) {
                if (element.isDisplayed() && element.isEnabled()) {
                    element.clear();
                    element.sendKeys(value);
                    return;
                }
            }
        }
        throw new NoSuchElementException("No visible input found for label: " + labelText);
    }

    private boolean hasNonTrivialNameLikeText(final String text) {
        final String[] lines = text.split("\\R");
        for (final String line : lines) {
            final String normalizedLine = normalize(line);
            if (normalizedLine.length() < 4
                    || normalizedLine.contains("informacion general")
                    || normalizedLine.contains("business plan")
                    || normalizedLine.contains("cambiar plan")
                    || normalizedLine.contains("cuenta")) {
                continue;
            }
            final String[] words = normalizedLine.split("\\s+");
            int alphaWords = 0;
            for (final String word : words) {
                if (word.matches("[a-z]+")) {
                    alphaWords++;
                }
            }
            if (alphaWords >= 2) {
                return true;
            }
        }
        return false;
    }

    private void takeScreenshot(final String name) throws IOException {
        final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        Files.copy(
                screenshot.toPath(),
                artifactsDir.resolve(name),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private void printFinalReport() {
        System.out.println();
        System.out.println("=== SaleADS Mi Negocio Workflow Report ===");
        for (final Map.Entry<String, String> entry : report.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
        System.out.println("Terminos y Condiciones URL: " + terminosUrl);
        System.out.println("Politica de Privacidad URL: " + privacidadUrl);
        if (artifactsDir != null) {
            System.out.println("Screenshots directory: " + artifactsDir.toAbsolutePath());
        }
        System.out.println("=========================================");
    }

    private void initReport() {
        report.put("Login", "FAIL");
        report.put("Mi Negocio menu", "FAIL");
        report.put("Agregar Negocio modal", "FAIL");
        report.put("Administrar Negocios view", "FAIL");
        report.put("Informacion General", "FAIL");
        report.put("Detalles de la Cuenta", "FAIL");
        report.put("Tus Negocios", "FAIL");
        report.put("Terminos y Condiciones", "FAIL");
        report.put("Politica de Privacidad", "FAIL");
    }

    private String env(final String key) {
        final String value = System.getenv(key);
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(final String... values) {
        for (final String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String normalize(final String text) {
        if (text == null) {
            return "";
        }
        final String lower = text.toLowerCase(Locale.ROOT);
        final String noAccent = Normalizer.normalize(lower, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noAccent.trim();
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
