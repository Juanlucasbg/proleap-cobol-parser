package io.proleap.ui;

import org.junit.After;
import org.junit.Assert;
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
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Environment-agnostic Selenium workflow for SaleADS "Mi Negocio".
 *
 * Run options (all optional):
 * -Dsaleads.baseUrl=https://...
 * -Dsaleads.browser=chrome|firefox|edge (default: chrome)
 * -Dsaleads.headless=true|false (default: true)
 * -Dsaleads.googleEmail=juanlucasbarbiergarzon@gmail.com
 * -Dsaleads.timeoutSeconds=25
 * -Dsaleads.screenshotsDir=target/surefire-reports/saleads-mi-negocio
 */
public class SaleAdsMiNegocioFullTest {

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.ROOT).withZone(ZoneOffset.UTC);

    private static final String DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
    private static final String TEST_NAME = "saleads_mi_negocio_full_test";
    private static final List<String> FINAL_REPORT_FIELDS = Arrays.asList(
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

    private WebDriver driver;
    private WebDriverWait wait;
    private Duration timeout;
    private Path screenshotsDir;
    private Map<String, Boolean> report;
    private String appWindowHandle;

    @Before
    public void setUp() throws IOException {
        this.timeout = Duration.ofSeconds(Long.parseLong(System.getProperty("saleads.timeoutSeconds", "25")));
        this.driver = createDriver();
        this.wait = new WebDriverWait(driver, timeout);
        this.report = new LinkedHashMap<>();
        this.screenshotsDir = prepareScreenshotsDir();
    }

    @After
    public void tearDown() {
        try {
            if (report != null && !report.isEmpty()) {
                printFinalReport(report);
            }
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

    @Test
    public void saleadsMiNegocioFullWorkflow() throws Exception {
        for (String field : FINAL_REPORT_FIELDS) {
            report.put(field, false);
        }

        String baseUrl = Optional.ofNullable(System.getProperty("saleads.baseUrl"))
                .orElseGet(() -> Optional.ofNullable(System.getenv("SALEADS_BASE_URL")).orElse(""));

        Assert.assertFalse(
                "saleads.baseUrl (or SALEADS_BASE_URL env var) is required because this repository cannot infer runtime URL.",
                baseUrl.trim().isEmpty()
        );

        driver.get(baseUrl);
        waitForUiAfterAction();
        appWindowHandle = driver.getWindowHandle();

        List<String> failed = new ArrayList<>();

        report.put("Login", stepLoginWithGoogle());
        report.put("Mi Negocio menu", stepOpenMiNegocioMenu());
        report.put("Agregar Negocio modal", stepValidateAgregarNegocioModal());
        report.put("Administrar Negocios view", stepOpenAdministrarNegocios());
        report.put("Información General", stepValidateInformacionGeneral());
        report.put("Detalles de la Cuenta", stepValidateDetallesCuenta());
        report.put("Tus Negocios", stepValidateTusNegocios());
        report.put("Términos y Condiciones", stepValidateTerminosYCondiciones());
        report.put("Política de Privacidad", stepValidatePoliticaPrivacidad());

        for (Map.Entry<String, Boolean> e : report.entrySet()) {
            if (!Boolean.TRUE.equals(e.getValue())) {
                failed.add(e.getKey());
            }
        }

        Assert.assertTrue("Workflow had failed sections: " + String.join(", ", failed), failed.isEmpty());
    }

    private boolean stepLoginWithGoogle() {
        try {
            WebElement loginButton = findFirstVisibleByText(
                    "Sign in with Google",
                    "Iniciar sesión con Google",
                    "Continuar con Google",
                    "Google"
            );
            safeClick(loginButton);
            waitForUiAfterAction();
            selectGoogleAccountIfPrompted(System.getProperty("saleads.googleEmail", DEFAULT_GOOGLE_EMAIL));

            boolean mainUiVisible = findFirstVisibleByTextOptional(
                    "Dashboard",
                    "Inicio",
                    "Negocio",
                    "Mi Negocio"
            ).isPresent();

            boolean leftSidebarVisible = hasVisible(By.xpath(
                    "//*[self::aside or contains(@class,'sidebar') or contains(@class,'SideBar') or contains(@class,'sidenav')]"
            )) || findFirstVisibleByTextOptional("Negocio", "Mi Negocio").isPresent();

            boolean pass = mainUiVisible && leftSidebarVisible;
            if (pass) {
                takeScreenshot("01-dashboard-loaded");
            }
            return pass;
        } catch (Exception ex) {
            safeScreenshotOnFailure("01-login-failed");
            return false;
        }
    }

    private boolean stepOpenMiNegocioMenu() {
        try {
            WebElement negocioLabel = findFirstVisibleByText("Negocio");
            safeClick(negocioLabel);
            waitForUiAfterAction();

            WebElement miNegocio = findFirstVisibleByText("Mi Negocio");
            safeClick(miNegocio);
            waitForUiAfterAction();

            boolean agregarVisible = findFirstVisibleByTextOptional("Agregar Negocio").isPresent();
            boolean administrarVisible = findFirstVisibleByTextOptional("Administrar Negocios").isPresent();
            boolean expanded = agregarVisible && administrarVisible;

            if (expanded) {
                takeScreenshot("02-mi-negocio-menu-expanded");
            }
            return expanded;
        } catch (Exception ex) {
            safeScreenshotOnFailure("02-mi-negocio-menu-failed");
            return false;
        }
    }

    private boolean stepValidateAgregarNegocioModal() {
        try {
            WebElement agregarNegocio = findFirstVisibleByText("Agregar Negocio");
            safeClick(agregarNegocio);
            waitForUiAfterAction();

            boolean titleVisible = findFirstVisibleByTextOptional("Crear Nuevo Negocio").isPresent();
            boolean inputVisible = hasVisibleByPossibleSelectors(
                    By.xpath("//input[@placeholder='Nombre del Negocio']"),
                    By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]//following::input[1]"),
                    By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]")
            );
            boolean limitVisible = findFirstVisibleByTextOptional("Tienes 2 de 3 negocios").isPresent();
            boolean cancelVisible = findFirstVisibleByTextOptional("Cancelar").isPresent();
            boolean createVisible = findFirstVisibleByTextOptional("Crear Negocio").isPresent();

            boolean pass = titleVisible && inputVisible && limitVisible && cancelVisible && createVisible;
            if (pass) {
                takeScreenshot("03-agregar-negocio-modal");

                Optional<WebElement> input = firstVisibleByPossibleSelectors(
                        By.xpath("//input[@placeholder='Nombre del Negocio']"),
                        By.xpath("//label[contains(normalize-space(),'Nombre del Negocio')]//following::input[1]"),
                        By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]")
                );

                if (input.isPresent()) {
                    safeClick(input.get());
                    input.get().clear();
                    input.get().sendKeys("Negocio Prueba Automatización");
                }
                findFirstVisibleByTextOptional("Cancelar").ifPresent(this::safeClick);
                waitForUiAfterAction();
            } else {
                safeScreenshotOnFailure("03-agregar-negocio-modal-failed");
            }

            return pass;
        } catch (Exception ex) {
            safeScreenshotOnFailure("03-agregar-negocio-modal-failed");
            return false;
        }
    }

    private boolean stepOpenAdministrarNegocios() {
        try {
            if (!findFirstVisibleByTextOptional("Administrar Negocios").isPresent()) {
                findFirstVisibleByTextOptional("Mi Negocio").ifPresent(this::safeClick);
                waitForUiAfterAction();
            }

            WebElement administrarNegocios = findFirstVisibleByText("Administrar Negocios");
            safeClick(administrarNegocios);
            waitForUiAfterAction();

            boolean infoGeneral = findFirstVisibleByTextOptional("Información General").isPresent();
            boolean detalles = findFirstVisibleByTextOptional("Detalles de la Cuenta").isPresent();
            boolean tusNegocios = findFirstVisibleByTextOptional("Tus Negocios").isPresent();
            boolean legal = findFirstVisibleByTextOptional("Sección Legal").isPresent();

            boolean pass = infoGeneral && detalles && tusNegocios && legal;
            if (pass) {
                takeScreenshot("04-administrar-negocios-full-page");
            } else {
                safeScreenshotOnFailure("04-administrar-negocios-failed");
            }
            return pass;
        } catch (Exception ex) {
            safeScreenshotOnFailure("04-administrar-negocios-failed");
            return false;
        }
    }

    private boolean stepValidateInformacionGeneral() {
        try {
            boolean userName = hasVisibleByPossibleSelectors(
                    By.xpath("//section//*[contains(@class,'name') and string-length(normalize-space()) > 0]"),
                    By.xpath("//*[contains(normalize-space(),'@')]/preceding::*[1]")
            ) || findFirstVisibleByTextOptional("Información General").isPresent();

            boolean userEmail = hasVisible(By.xpath("//*[contains(normalize-space(),'@')]"));
            boolean businessPlan = findFirstVisibleByTextOptional("BUSINESS PLAN").isPresent();
            boolean cambiarPlan = findFirstVisibleByTextOptional("Cambiar Plan").isPresent();

            return userName && userEmail && businessPlan && cambiarPlan;
        } catch (Exception ex) {
            safeScreenshotOnFailure("05-informacion-general-failed");
            return false;
        }
    }

    private boolean stepValidateDetallesCuenta() {
        try {
            boolean cuentaCreada = findFirstVisibleByTextOptional("Cuenta creada").isPresent();
            boolean estadoActivo = findFirstVisibleByTextOptional("Estado activo").isPresent();
            boolean idioma = findFirstVisibleByTextOptional("Idioma seleccionado").isPresent();
            return cuentaCreada && estadoActivo && idioma;
        } catch (Exception ex) {
            safeScreenshotOnFailure("06-detalles-cuenta-failed");
            return false;
        }
    }

    private boolean stepValidateTusNegocios() {
        try {
            boolean section = findFirstVisibleByTextOptional("Tus Negocios").isPresent();
            boolean agregarBtn = findFirstVisibleByTextOptional("Agregar Negocio").isPresent();
            boolean limit = findFirstVisibleByTextOptional("Tienes 2 de 3 negocios").isPresent();
            boolean listVisible = hasVisible(By.xpath(
                    "//*[contains(normalize-space(),'Tus Negocios')]//following::*[self::ul or self::table or contains(@class,'list') or contains(@class,'card')][1]"
            )) || hasVisible(By.xpath("//*[contains(normalize-space(),'Negocio')]"));
            return section && agregarBtn && limit && listVisible;
        } catch (Exception ex) {
            safeScreenshotOnFailure("07-tus-negocios-failed");
            return false;
        }
    }

    private boolean stepValidateTerminosYCondiciones() {
        return validateLegalLink(
                "Términos y Condiciones",
                "terminos-y-condiciones"
        );
    }

    private boolean stepValidatePoliticaPrivacidad() {
        return validateLegalLink(
                "Política de Privacidad",
                "politica-de-privacidad"
        );
    }

    private boolean validateLegalLink(String visibleText, String screenshotSlug) {
        try {
            Optional<WebElement> link = findFirstVisibleByTextOptional(visibleText);
            if (!link.isPresent()) {
                return false;
            }

            String previousUrl = driver.getCurrentUrl();
            Set<String> oldHandles = driver.getWindowHandles();
            safeClick(link.get());
            waitForUiAfterAction();

            boolean switchedToNewWindow = switchToNewWindowIfPresent(oldHandles);
            waitForUiAfterAction();

            boolean headingVisible = findFirstVisibleByTextOptional(visibleText).isPresent();
            boolean legalContentVisible = hasVisible(By.xpath(
                    "//main//*[string-length(normalize-space()) > 120] | //article//*[string-length(normalize-space()) > 120] | //body//*[string-length(normalize-space()) > 200]"
            ));
            boolean moved = switchedToNewWindow || !driver.getCurrentUrl().equals(previousUrl);
            boolean pass = moved && headingVisible && legalContentVisible;

            String finalUrl = driver.getCurrentUrl();
            if (pass) {
                takeScreenshot("08-" + screenshotSlug);
                System.out.println("[EVIDENCE] " + visibleText + " final URL: " + finalUrl);
            } else {
                safeScreenshotOnFailure("08-" + screenshotSlug + "-failed");
            }

            if (switchedToNewWindow) {
                driver.close();
                driver.switchTo().window(appWindowHandle);
                waitForUiAfterAction();
            } else {
                driver.navigate().back();
                waitForUiAfterAction();
            }

            return pass;
        } catch (Exception ex) {
            safeScreenshotOnFailure("08-legal-link-failed");
            tryReturnToAppWindow();
            return false;
        }
    }

    private void selectGoogleAccountIfPrompted(String email) {
        try {
            Duration shortTimeout = Duration.ofSeconds(8);
            WebDriverWait shortWait = new WebDriverWait(driver, shortTimeout);
            shortWait.until(anyVisible(
                    By.xpath("//*[contains(normalize-space(),'Choose an account')]"),
                    By.xpath("//*[contains(normalize-space(),'Elige una cuenta')]"),
                    By.xpath("//*[contains(normalize-space(),'" + email + "')]")
            ));
            Optional<WebElement> account = firstVisibleByPossibleSelectors(
                    By.xpath("//*[contains(normalize-space(),'" + email + "')]")
            );
            account.ifPresent(this::safeClick);
            waitForUiAfterAction();
        } catch (TimeoutException ignored) {
            // In some environments user is already authenticated and there is no account selector.
        }
    }

    private ExpectedCondition<Boolean> anyVisible(By... selectors) {
        return webDriver -> {
            for (By selector : selectors) {
                List<WebElement> elements = webDriver.findElements(selector);
                for (WebElement element : elements) {
                    if (element.isDisplayed()) {
                        return true;
                    }
                }
            }
            return false;
        };
    }

    private void safeClick(WebElement element) {
        wait.until(ExpectedConditions.elementToBeClickable(element));
        element.click();
    }

    private Optional<WebElement> findFirstVisibleByTextOptional(String... texts) {
        for (String text : texts) {
            List<By> selectors = Arrays.asList(
                    By.xpath("//*[normalize-space()='" + text + "']"),
                    By.xpath("//*[contains(normalize-space(),'" + text + "')]")
            );
            for (By selector : selectors) {
                List<WebElement> elements = driver.findElements(selector);
                for (WebElement element : elements) {
                    if (element.isDisplayed()) {
                        return Optional.of(element);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private WebElement findFirstVisibleByText(String... texts) {
        Optional<WebElement> element = findFirstVisibleByTextOptional(texts);
        if (!element.isPresent()) {
            throw new NoSuchElementException("Unable to find visible element with any text in: " + Arrays.toString(texts));
        }
        return element.get();
    }

    private boolean hasVisible(By selector) {
        List<WebElement> elements = driver.findElements(selector);
        for (WebElement element : elements) {
            if (element.isDisplayed()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasVisibleByPossibleSelectors(By... selectors) {
        return firstVisibleByPossibleSelectors(selectors).isPresent();
    }

    private Optional<WebElement> firstVisibleByPossibleSelectors(By... selectors) {
        for (By selector : selectors) {
            List<WebElement> elements = driver.findElements(selector);
            for (WebElement element : elements) {
                if (element.isDisplayed()) {
                    return Optional.of(element);
                }
            }
        }
        return Optional.empty();
    }

    private boolean switchToNewWindowIfPresent(Set<String> oldHandles) {
        try {
            wait.until(driver -> driver.getWindowHandles().size() > oldHandles.size());
            Set<String> current = driver.getWindowHandles();
            for (String handle : current) {
                if (!oldHandles.contains(handle)) {
                    driver.switchTo().window(handle);
                    return true;
                }
            }
            return false;
        } catch (TimeoutException ex) {
            return false;
        }
    }

    private void waitForUiAfterAction() {
        wait.until(webDriver -> ((JavascriptExecutor) webDriver)
                .executeScript("return document.readyState").toString().equals("complete"));
        sleep(750);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Path prepareScreenshotsDir() throws IOException {
        String defaultDir = "target/surefire-reports/saleads-mi-negocio/" + TS_FORMAT.format(Instant.now());
        String configured = System.getProperty("saleads.screenshotsDir", defaultDir);
        Path dir = Path.of(configured);
        Files.createDirectories(dir);
        return dir;
    }

    private void takeScreenshot(String slug) {
        if (!(driver instanceof TakesScreenshot)) {
            return;
        }
        try {
            Path target = screenshotsDir.resolve(slug + ".png");
            Files.copy(((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath(), target,
                    StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[EVIDENCE] Screenshot: " + target.toAbsolutePath());
        } catch (IOException ex) {
            System.out.println("[WARN] Could not write screenshot for " + slug + ": " + ex.getMessage());
        }
    }

    private void safeScreenshotOnFailure(String slug) {
        try {
            takeScreenshot(slug);
        } catch (Exception ignored) {
            // Keep test flow resilient to evidence failures.
        }
    }

    private void printFinalReport(Map<String, Boolean> statuses) {
        System.out.println("===== Final Report: " + TEST_NAME + " =====");
        for (String key : FINAL_REPORT_FIELDS) {
            boolean pass = Boolean.TRUE.equals(statuses.get(key));
            System.out.println(key + ": " + (pass ? "PASS" : "FAIL"));
        }
    }

    private void tryReturnToAppWindow() {
        try {
            if (appWindowHandle != null && driver.getWindowHandles().contains(appWindowHandle)) {
                driver.switchTo().window(appWindowHandle);
                waitForUiAfterAction();
                return;
            }
            if (!driver.getWindowHandles().isEmpty()) {
                String first = driver.getWindowHandles().iterator().next();
                driver.switchTo().window(first);
                waitForUiAfterAction();
            }
        } catch (Exception ignored) {
            // best effort
        }
    }

    private WebDriver createDriver() {
        String browser = System.getProperty("saleads.browser", "chrome").toLowerCase(Locale.ROOT);
        boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "true"));

        switch (browser) {
            case "firefox":
                FirefoxOptions firefoxOptions = new FirefoxOptions();
                if (headless) {
                    firefoxOptions.addArguments("-headless");
                }
                return new FirefoxDriver(firefoxOptions);
            case "edge":
                EdgeOptions edgeOptions = new EdgeOptions();
                if (headless) {
                    edgeOptions.addArguments("--headless=new");
                }
                return new EdgeDriver(edgeOptions);
            case "chrome":
            default:
                ChromeOptions chromeOptions = new ChromeOptions();
                if (headless) {
                    chromeOptions.addArguments("--headless=new");
                }
                chromeOptions.addArguments("--window-size=1920,1080");
                chromeOptions.addArguments("--disable-gpu");
                chromeOptions.addArguments("--no-sandbox");
                return new ChromeDriver(chromeOptions);
        }
    }
}
