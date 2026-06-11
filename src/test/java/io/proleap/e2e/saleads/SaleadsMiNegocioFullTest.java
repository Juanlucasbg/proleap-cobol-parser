package io.proleap.e2e.saleads;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end workflow for SaleADS.ai "Mi Negocio" module.
 *
 * <p>This test is environment-agnostic (dev/staging/prod) and does not hardcode any domain. Provide
 * a login URL with one of:
 *
 * <ul>
 *   <li>Environment variable: SALEADS_LOGIN_URL
 *   <li>System property: -Dsaleads.login.url=...
 * </ul>
 *
 * <p>Screenshots are saved under {@code target/saleads-e2e-screenshots}.
 */
public class SaleadsMiNegocioFullTest {

  private static final String LOGIN_REPORT_KEY = "Login";
  private static final String MI_NEGOCIO_MENU_REPORT_KEY = "Mi Negocio menu";
  private static final String AGREGAR_NEGOCIO_MODAL_REPORT_KEY = "Agregar Negocio modal";
  private static final String ADMINISTRAR_NEGOCIOS_VIEW_REPORT_KEY = "Administrar Negocios view";
  private static final String INFORMACION_GENERAL_REPORT_KEY = "Información General";
  private static final String DETALLES_CUENTA_REPORT_KEY = "Detalles de la Cuenta";
  private static final String TUS_NEGOCIOS_REPORT_KEY = "Tus Negocios";
  private static final String TERMINOS_REPORT_KEY = "Términos y Condiciones";
  private static final String POLITICA_REPORT_KEY = "Política de Privacidad";

  private final Map<String, Boolean> reportBySection = new LinkedHashMap<>();
  private final List<String> failures = new ArrayList<>();
  private final Map<String, String> capturedLegalUrls = new LinkedHashMap<>();

  private WebDriver driver;
  private WebDriverWait wait;
  private Path screenshotDir;
  private String appWindowHandle;
  private int screenshotCounter;

  @Before
  public void setUp() throws IOException {
    final String loginUrl = firstNonBlank(System.getenv("SALEADS_LOGIN_URL"), System.getProperty("saleads.login.url"));
    Assume.assumeTrue(
        "Skipping SaleADS E2E test because SALEADS_LOGIN_URL or -Dsaleads.login.url is required.",
        loginUrl != null);

    final ChromeOptions options = new ChromeOptions();
    options.addArguments("--headless=new");
    options.addArguments("--window-size=1920,1080");
    options.addArguments("--disable-gpu");
    options.addArguments("--no-sandbox");
    options.addArguments("--disable-dev-shm-usage");

    driver = new ChromeDriver(options);
    wait = new WebDriverWait(driver, Duration.ofSeconds(25));
    screenshotDir = Paths.get("target", "saleads-e2e-screenshots");
    Files.createDirectories(screenshotDir);

    driver.get(loginUrl);
    waitForUiLoad();
    appWindowHandle = driver.getWindowHandle();
  }

  @After
  public void tearDown() {
    if (driver != null) {
      driver.quit();
    }
  }

  @Test
  public void saleadsMiNegocioFullWorkflow() {
    runSection(LOGIN_REPORT_KEY, this::stepLoginWithGoogle);
    runSection(MI_NEGOCIO_MENU_REPORT_KEY, this::stepOpenMiNegocioMenu);
    runSection(AGREGAR_NEGOCIO_MODAL_REPORT_KEY, this::stepValidateAgregarNegocioModal);
    runSection(ADMINISTRAR_NEGOCIOS_VIEW_REPORT_KEY, this::stepOpenAdministrarNegociosView);
    runSection(INFORMACION_GENERAL_REPORT_KEY, this::stepValidateInformacionGeneral);
    runSection(DETALLES_CUENTA_REPORT_KEY, this::stepValidateDetallesCuenta);
    runSection(TUS_NEGOCIOS_REPORT_KEY, this::stepValidateTusNegocios);
    runSection(
        TERMINOS_REPORT_KEY,
        () -> stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones", "terms-page"));
    runSection(
        POLITICA_REPORT_KEY,
        () -> stepValidateLegalLink("Política de Privacidad", "Política de Privacidad", "privacy-page"));

    printFinalReport();
    Assert.assertTrue("SaleADS Mi Negocio workflow failures:\n" + String.join("\n", failures), failures.isEmpty());
  }

  private void stepLoginWithGoogle() throws IOException {
    if (!isSidebarVisible()) {
      clickFirstVisibleText(
          Arrays.asList(
              "Sign in with Google",
              "Iniciar sesión con Google",
              "Continuar con Google",
              "Iniciar sesión",
              "Login"));
      waitForUiLoad();

      final Optional<WebElement> accountOption = findVisibleByContainsText("juanlucasbarbiergarzon@gmail.com");
      if (accountOption.isPresent()) {
        clickAndWait(accountOption.get());
      }
    }

    waitForAnySidebarSignal();
    takeScreenshot("dashboard-loaded");
  }

  private void stepOpenMiNegocioMenu() throws IOException {
    waitForAnySidebarSignal();

    findVisibleByExactText("Negocio").ifPresent(this::clickAndWait);
    clickFirstVisibleText(Arrays.asList("Mi Negocio", "Negocio"));

    assertVisibleContainsText("Agregar Negocio");
    assertVisibleContainsText("Administrar Negocios");
    takeScreenshot("mi-negocio-menu-expanded");
  }

  private void stepValidateAgregarNegocioModal() throws IOException {
    clickFirstVisibleText(Arrays.asList("Agregar Negocio"));
    assertVisibleContainsText("Crear Nuevo Negocio");
    assertVisibleContainsText("Nombre del Negocio");
    assertVisibleContainsText("Tienes 2 de 3 negocios");
    assertVisibleExactText("Cancelar");
    assertVisibleContainsText("Crear Negocio");
    takeScreenshot("agregar-negocio-modal");

    final Optional<WebElement> businessNameField =
        findFirstVisible(
            By.xpath(
                "//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio' or @name='businessName']"
                    + " | //label[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1]"));

    if (businessNameField.isPresent()) {
      businessNameField.get().clear();
      businessNameField.get().sendKeys("Negocio Prueba Automatización");
    }

    clickFirstVisibleText(Arrays.asList("Cancelar"));
    waitForUiLoad();
    wait.until(d -> d.findElements(By.xpath("//*[contains(normalize-space(), 'Crear Nuevo Negocio')]")).isEmpty());
  }

  private void stepOpenAdministrarNegociosView() throws IOException {
    if (!findVisibleByContainsText("Administrar Negocios").isPresent()) {
      clickFirstVisibleText(Arrays.asList("Mi Negocio", "Negocio"));
    }

    clickFirstVisibleText(Arrays.asList("Administrar Negocios"));
    waitForUiLoad();

    assertVisibleContainsText("Información General");
    assertVisibleContainsText("Detalles de la Cuenta");
    assertVisibleContainsText("Tus Negocios");
    assertVisibleContainsText("Sección Legal");
    takeFullPageLikeScreenshot("administrar-negocios-page");
  }

  private void stepValidateInformacionGeneral() {
    assertVisibleContainsText("BUSINESS PLAN");
    assertVisibleContainsText("Cambiar Plan");
    assertVisibleByPattern("(?i).+@.+\\..+");
    assertAnyVisibleUserName();
  }

  private void stepValidateDetallesCuenta() {
    assertVisibleContainsText("Cuenta creada");
    assertVisibleContainsText("Estado activo");
    assertVisibleContainsText("Idioma seleccionado");
  }

  private void stepValidateTusNegocios() {
    assertVisibleContainsText("Tus Negocios");
    assertVisibleContainsText("Agregar Negocio");
    assertVisibleContainsText("Tienes 2 de 3 negocios");
  }

  private void stepValidateLegalLink(String linkText, String expectedHeading, String screenshotName)
      throws IOException {
    final String originalHandle = driver.getWindowHandle();
    final String originalUrl = driver.getCurrentUrl();
    final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());

    clickFirstVisibleText(Arrays.asList(linkText));
    waitForUiLoad();

    final boolean openedNewTab = switchToNewlyOpenedTab(handlesBeforeClick);
    assertVisibleContainsText(expectedHeading);
    assertLegalBodyIsVisible();
    takeScreenshot(screenshotName);
    capturedLegalUrls.put(expectedHeading, driver.getCurrentUrl());

    if (openedNewTab) {
      driver.close();
      driver.switchTo().window(originalHandle);
      waitForUiLoad();
    } else if (!driver.getCurrentUrl().equals(originalUrl)) {
      driver.navigate().back();
      waitForUiLoad();
    }

    driver.switchTo().window(appWindowHandle);
    waitForUiLoad();
  }

  private void runSection(String sectionName, StepRunnable step) {
    try {
      step.run();
      reportBySection.put(sectionName, true);
    } catch (Throwable throwable) {
      reportBySection.put(sectionName, false);
      failures.add(sectionName + " -> " + throwable.getMessage());
    }
  }

  private void printFinalReport() {
    System.out.println("=== SaleADS Mi Negocio Final Report ===");
    for (Map.Entry<String, Boolean> entry : reportBySection.entrySet()) {
      final String status = entry.getValue() ? "PASS" : "FAIL";
      System.out.println(entry.getKey() + ": " + status);
    }

    if (!capturedLegalUrls.isEmpty()) {
      System.out.println("--- Captured legal URLs ---");
      for (Map.Entry<String, String> entry : capturedLegalUrls.entrySet()) {
        System.out.println(entry.getKey() + ": " + entry.getValue());
      }
    }
  }

  private void waitForAnySidebarSignal() {
    wait.until(
        d ->
            !d.findElements(By.xpath("//aside | //nav")).isEmpty()
                && (containsVisibleText("Mi Negocio") || containsVisibleText("Negocio")));
  }

  private boolean isSidebarVisible() {
    return !driver.findElements(By.xpath("//aside | //nav")).isEmpty()
        && (containsVisibleText("Mi Negocio") || containsVisibleText("Negocio"));
  }

  private boolean containsVisibleText(String text) {
    return findVisibleByContainsText(text).isPresent();
  }

  private void assertVisibleContainsText(String text) {
    final By by = By.xpath("//*[contains(normalize-space(), " + xPathLiteral(text) + ")]");
    wait.until(d -> firstVisible(d.findElements(by)).isPresent());
  }

  private void assertVisibleExactText(String text) {
    final By by = By.xpath("//*[normalize-space() = " + xPathLiteral(text) + "]");
    wait.until(d -> firstVisible(d.findElements(by)).isPresent());
  }

  private void assertVisibleByPattern(String regex) {
    wait.until(
        d ->
            d.findElements(By.xpath("//*[normalize-space() != '']")).stream()
                .filter(WebElement::isDisplayed)
                .map(WebElement::getText)
                .anyMatch(text -> text != null && text.matches("(?s).*" + regex + ".*")));
  }

  private void assertAnyVisibleUserName() {
    wait.until(
        d ->
            d.findElements(By.xpath("//*[normalize-space() != '']")).stream()
                .filter(WebElement::isDisplayed)
                .map(WebElement::getText)
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .anyMatch(text -> text.matches("[\\p{L}][\\p{L}\\s'.-]{2,}")));
  }

  private void assertLegalBodyIsVisible() {
    wait.until(
        d ->
            !d.findElements(By.xpath("//p[string-length(normalize-space()) > 40] | //li[string-length(normalize-space()) > 40]"))
                .isEmpty());
  }

  private void clickFirstVisibleText(List<String> candidateTexts) {
    for (String candidateText : candidateTexts) {
      final Optional<WebElement> exact = findVisibleByExactText(candidateText);
      if (exact.isPresent()) {
        clickAndWait(exact.get());
        return;
      }

      final Optional<WebElement> contains = findVisibleByContainsText(candidateText);
      if (contains.isPresent()) {
        clickAndWait(contains.get());
        return;
      }
    }

    throw new NoSuchElementException("Could not find visible element with any text: " + candidateTexts);
  }

  private Optional<WebElement> findVisibleByExactText(String text) {
    final String xpath =
        "//*[self::button or self::a or self::span or self::div or self::p or self::h1 or self::h2 or self::h3 or self::li]"
            + "[normalize-space() = "
            + xPathLiteral(text)
            + "]";
    return findFirstVisible(By.xpath(xpath));
  }

  private Optional<WebElement> findVisibleByContainsText(String text) {
    final String xpath =
        "//*[self::button or self::a or self::span or self::div or self::p or self::h1 or self::h2 or self::h3 or self::li]"
            + "[contains(normalize-space(), "
            + xPathLiteral(text)
            + ")]";
    return findFirstVisible(By.xpath(xpath));
  }

  private Optional<WebElement> findFirstVisible(By by) {
    return firstVisible(driver.findElements(by));
  }

  private Optional<WebElement> firstVisible(List<WebElement> elements) {
    for (WebElement element : elements) {
      try {
        if (element.isDisplayed()) {
          return Optional.of(element);
        }
      } catch (StaleElementReferenceException ignored) {
        // Skip stale references and continue.
      }
    }

    return Optional.empty();
  }

  private void clickAndWait(WebElement element) {
    try {
      element.click();
    } catch (RuntimeException clickException) {
      ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
    }
    waitForUiLoad();
  }

  private boolean switchToNewlyOpenedTab(Set<String> handlesBeforeClick) {
    final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(8));
    try {
      shortWait.until((ExpectedCondition<Boolean>) d -> d.getWindowHandles().size() > handlesBeforeClick.size());
    } catch (TimeoutException timeoutException) {
      return false;
    }

    final Set<String> handlesAfterClick = new LinkedHashSet<>(driver.getWindowHandles());
    handlesAfterClick.removeAll(handlesBeforeClick);

    if (handlesAfterClick.isEmpty()) {
      return false;
    }

    driver.switchTo().window(handlesAfterClick.iterator().next());
    waitForUiLoad();
    return true;
  }

  private void waitForUiLoad() {
    wait.until(
        d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
    try {
      Thread.sleep(400);
    } catch (InterruptedException interruptedException) {
      Thread.currentThread().interrupt();
    }
  }

  private void takeScreenshot(String checkpointName) throws IOException {
    screenshotCounter++;
    final String sanitizedName = checkpointName.replaceAll("[^a-zA-Z0-9\\-_]+", "-");
    final String fileName = String.format("%02d-%s.png", screenshotCounter, sanitizedName);
    final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
    Files.copy(screenshot.toPath(), screenshotDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
  }

  private void takeFullPageLikeScreenshot(String checkpointName) throws IOException {
    final Dimension originalSize = driver.manage().window().getSize();
    driver.manage().window().setSize(new Dimension(1920, 3000));
    waitForUiLoad();
    takeScreenshot(checkpointName);
    driver.manage().window().setSize(originalSize);
    waitForUiLoad();
  }

  private String firstNonBlank(String... values) {
    return Arrays.stream(values).filter(value -> value != null && !value.trim().isEmpty()).findFirst().orElse(null);
  }

  private String xPathLiteral(String text) {
    if (!text.contains("'")) {
      return "'" + text + "'";
    }
    if (!text.contains("\"")) {
      return "\"" + text + "\"";
    }

    final String concatenated =
        Arrays.stream(text.split("'"))
            .map(fragment -> "'" + fragment + "'")
            .collect(Collectors.joining(", \"'\", "));

    return "concat(" + concatenated + ")";
  }

  private interface StepRunnable {
    void run() throws Exception;
  }
}
