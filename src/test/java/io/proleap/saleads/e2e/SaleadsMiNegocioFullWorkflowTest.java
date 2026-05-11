package io.proleap.saleads.e2e;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.After;
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
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end validation for SaleADS Mi Negocio workflow.
 *
 * <p>Runtime configuration:</p>
 *
 * <ul>
 *   <li>Base URL: -Dsaleads.baseUrl or SALEADS_BASE_URL</li>
 *   <li>Headless mode: -Dsaleads.headless or SALEADS_HEADLESS (default false)</li>
 *   <li>Timeout seconds: -Dsaleads.timeoutSeconds or SALEADS_TIMEOUT_SECONDS (default 30)</li>
 *   <li>Expected user email: -Dsaleads.userEmail or SALEADS_USER_EMAIL</li>
 *   <li>Expected user name (optional): -Dsaleads.userName or SALEADS_USER_NAME</li>
 * </ul>
 */
public class SaleadsMiNegocioFullWorkflowTest {

  private static final String DEFAULT_USER_EMAIL = "juanlucasbarbiergarzon@gmail.com";
  private static final String NEGOCIO_PRUEBA_AUTOMATIZACION = "Negocio Prueba Automatizacion";
  private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+");

  private final Map<String, Boolean> finalReport = new LinkedHashMap<>();
  private final List<String> failures = new ArrayList<>();
  private final Map<String, String> legalUrls = new LinkedHashMap<>();

  private WebDriver driver;
  private WebDriverWait wait;
  private Path screenshotDir;
  private String expectedUserEmail;
  private String expectedUserName;

  @Before
  public void setUp() throws IOException {
    final String baseUrl = readRequiredConfig("saleads.baseUrl", "SALEADS_BASE_URL");
    expectedUserEmail = readConfig("saleads.userEmail", "SALEADS_USER_EMAIL", DEFAULT_USER_EMAIL);
    expectedUserName = readConfig("saleads.userName", "SALEADS_USER_NAME", "");

    final boolean headless =
        Boolean.parseBoolean(readConfig("saleads.headless", "SALEADS_HEADLESS", "false"));
    final int timeoutSeconds =
        Integer.parseInt(readConfig("saleads.timeoutSeconds", "SALEADS_TIMEOUT_SECONDS", "30"));

    final ChromeOptions options = new ChromeOptions();
    options.addArguments("--window-size=1920,1080");
    options.addArguments("--disable-gpu");
    options.addArguments("--no-sandbox");
    if (headless) {
      options.addArguments("--headless=new");
    }

    driver = new ChromeDriver(options);
    wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));

    screenshotDir =
        Path.of(
            "target",
            "saleads-e2e-screenshots",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
    Files.createDirectories(screenshotDir);

    driver.get(baseUrl);
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
    executeStep("Login", this::validateLoginWithGoogle);
    executeStep("Mi Negocio menu", this::validateMiNegocioMenu);
    executeStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
    executeStep("Administrar Negocios view", this::validateAdministrarNegociosView);
    executeStep("Informaci\u00f3n General", this::validateInformacionGeneral);
    executeStep("Detalles de la Cuenta", this::validateDetallesDeLaCuenta);
    executeStep("Tus Negocios", this::validateTusNegocios);
    executeStep("T\u00e9rminos y Condiciones", this::validateTerminosYCondiciones);
    executeStep("Pol\u00edtica de Privacidad", this::validatePoliticaDePrivacidad);

    printFinalReport();
    if (!failures.isEmpty()) {
      fail("SaleADS Mi Negocio workflow failed:\n- " + String.join("\n- ", failures));
    }
  }

  private void validateLoginWithGoogle() {
    clickByFirstVisibleText(
        List.of(
            "Sign in with Google",
            "Iniciar sesion con Google",
            "Iniciar sesi\u00f3n con Google",
            "Continuar con Google"));

    selectGoogleAccountIfPrompted(expectedUserEmail);

    // Validate dashboard and left sidebar are visible.
    waitForAnyVisibleText(List.of("Dashboard", "Inicio", "Negocio"));
    assertVisible(By.xpath("//aside | //nav"), "Left sidebar navigation is not visible.");
    takeScreenshot("01-dashboard-loaded");
  }

  private void validateMiNegocioMenu() {
    clickByFirstVisibleText(List.of("Negocio"));
    clickByFirstVisibleText(List.of("Mi Negocio"));

    assertTextVisible("Agregar Negocio");
    assertTextVisible("Administrar Negocios");
    takeScreenshot("02-mi-negocio-expanded-menu");
  }

  private void validateAgregarNegocioModal() {
    clickByFirstVisibleText(List.of("Agregar Negocio"));

    final WebElement modal =
        waitForVisible(
            By.xpath(
                "//*[(@role='dialog' or contains(@class,'modal')) and .//*[contains(normalize-space(.),"
                    + xpathLiteral("Crear Nuevo Negocio")
                    + ")]]"));

    assertTextVisible("Crear Nuevo Negocio");
    assertVisible(
        By.xpath(
            "//*[contains(normalize-space(.),"
                + xpathLiteral("Nombre del Negocio")
                + ")]"
                + " | //input[@placeholder='Nombre del Negocio']"),
        "Input field 'Nombre del Negocio' not found.");
    assertTextVisible("Tienes 2 de 3 negocios");
    assertTextVisible("Cancelar");
    assertTextVisible("Crear Negocio");
    takeScreenshot("03-crear-nuevo-negocio-modal");

    // Optional action requested in workflow.
    final WebElement nameInput =
        firstDisplayedElement(
            modal.findElements(
                By.xpath(
                    ".//input[@placeholder='Nombre del Negocio' or @type='text' or contains(@name,'nombre')]")));
    assertNotNull("Could not locate business name input in modal.", nameInput);
    nameInput.click();
    nameInput.clear();
    nameInput.sendKeys(NEGOCIO_PRUEBA_AUTOMATIZACION);
    clickByFirstVisibleText(List.of("Cancelar"));
    waitForUiToLoad();
  }

  private void validateAdministrarNegociosView() {
    ensureMiNegocioExpanded();
    clickByFirstVisibleText(List.of("Administrar Negocios"));

    assertAnyTextVisible(List.of("Informacion General", "Informaci\u00f3n General"));
    assertTextVisible("Detalles de la Cuenta");
    assertTextVisible("Tus Negocios");
    assertAnyTextVisible(List.of("Seccion Legal", "Secci\u00f3n Legal"));
    takeScreenshot("04-administrar-negocios-view");
  }

  private void validateInformacionGeneral() {
    final WebElement section = findSectionByTitle(List.of("Informacion General", "Informaci\u00f3n General"));
    final String sectionText = normalizedText(section.getText());

    assertTrue("Expected user email is not visible.", sectionText.contains(expectedUserEmail.toLowerCase()));
    assertTrue("BUSINESS PLAN is not visible.", sectionText.contains("business plan"));
    assertTrue("Cambiar Plan button is not visible.", sectionText.contains("cambiar plan"));

    if (!expectedUserName.isBlank()) {
      assertTrue(
          "Expected user name is not visible.",
          sectionText.contains(expectedUserName.toLowerCase()));
    } else {
      assertTrue(
          "User name is not visible in Informacion General.",
          hasLikelyUserName(sectionText));
    }
  }

  private void validateDetallesDeLaCuenta() {
    final WebElement section = findSectionByTitle("Detalles de la Cuenta");
    final String sectionText = normalizedText(section.getText());

    assertTrue("'Cuenta creada' is not visible.", sectionText.contains("cuenta creada"));
    assertTrue("'Estado activo' is not visible.", sectionText.contains("estado activo"));
    assertTrue("'Idioma seleccionado' is not visible.", sectionText.contains("idioma seleccionado"));
  }

  private void validateTusNegocios() {
    final WebElement section = findSectionByTitle("Tus Negocios");
    final String sectionText = normalizedText(section.getText());

    assertTrue("Business list is not visible.", hasBusinessList(section));
    assertTrue("'Agregar Negocio' button is not visible.", sectionText.contains("agregar negocio"));
    assertTrue("'Tienes 2 de 3 negocios' is not visible.", sectionText.contains("tienes 2 de 3 negocios"));
  }

  private void validateTerminosYCondiciones() {
    validateLegalLink(
        List.of("Terminos y Condiciones", "T\u00e9rminos y Condiciones"),
        List.of("Terminos y Condiciones", "T\u00e9rminos y Condiciones"),
        "05-terminos-y-condiciones");
  }

  private void validatePoliticaDePrivacidad() {
    validateLegalLink(
        List.of("Politica de Privacidad", "Pol\u00edtica de Privacidad"),
        List.of("Politica de Privacidad", "Pol\u00edtica de Privacidad"),
        "06-politica-de-privacidad");
  }

  private void validateLegalLink(
      final List<String> linkTexts, final List<String> headingTexts, final String screenshotName) {
    final String originalHandle = driver.getWindowHandle();
    final Set<String> handlesBefore = driver.getWindowHandles();
    final String urlBefore = driver.getCurrentUrl();

    clickByFirstVisibleText(linkTexts);

    final String newHandle = waitForNewHandle(handlesBefore, urlBefore);
    final boolean openedNewTab = newHandle != null;
    if (openedNewTab) {
      driver.switchTo().window(newHandle);
      waitForUiToLoad();
    }

    assertAnyTextVisible(headingTexts);
    assertTrue("Legal content text is not visible.", hasLegalContentText());
    takeScreenshot(screenshotName);
    legalUrls.put(headingTexts.get(0), driver.getCurrentUrl());

    if (openedNewTab) {
      driver.close();
      driver.switchTo().window(originalHandle);
      waitForUiToLoad();
    } else {
      driver.navigate().back();
      waitForUiToLoad();
    }
  }

  private void ensureMiNegocioExpanded() {
    if (!isTextVisible("Administrar Negocios")) {
      if (isTextVisible("Negocio")) {
        clickByFirstVisibleText(List.of("Negocio"));
      }
      clickByFirstVisibleText(List.of("Mi Negocio"));
    }
  }

  private void executeStep(final String stepName, final StepAction stepAction) {
    try {
      stepAction.run();
      finalReport.put(stepName, true);
    } catch (final Throwable e) {
      finalReport.put(stepName, false);
      failures.add(stepName + ": " + e.getMessage());
      takeScreenshot("fail-" + slug(stepName));
    }
  }

  private void selectGoogleAccountIfPrompted(final String accountEmail) {
    final long endAt = System.currentTimeMillis() + 10_000L;
    String googleHandle = null;

    while (System.currentTimeMillis() < endAt && googleHandle == null) {
      for (final String handle : driver.getWindowHandles()) {
        driver.switchTo().window(handle);
        if (normalizedText(driver.getCurrentUrl()).contains("accounts.google")) {
          googleHandle = handle;
          break;
        }
      }
      if (googleHandle == null) {
        try {
          Thread.sleep(300L);
        } catch (final InterruptedException interruptedException) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    }

    if (googleHandle != null) {
      driver.switchTo().window(googleHandle);
      waitForUiToLoad();
    }

    try {
      if (normalizedText(driver.getCurrentUrl()).contains("accounts.google")) {
        final By emailOption =
            By.xpath(
                "//*[contains(normalize-space(.),"
                    + xpathLiteral(accountEmail)
                    + ") and (self::div or self::span or self::button or self::a)]");
        final WebElement option = waitForVisible(emailOption);
        safeClick(option);
        waitForUiToLoad();
      }
    } catch (final TimeoutException ignored) {
      // Selector might not appear when account session is already active.
    }

    for (final String handle : driver.getWindowHandles()) {
      driver.switchTo().window(handle);
      if (!normalizedText(driver.getCurrentUrl()).contains("accounts.google")) {
        waitForUiToLoad();
        return;
      }
    }
  }

  private void clickByFirstVisibleText(final List<String> visibleTexts) {
    final WebElement element = waitForFirstByVisibleText(visibleTexts);
    safeClick(element);
    waitForUiToLoad();
  }

  private WebElement waitForFirstByVisibleText(final List<String> visibleTexts) {
    for (final String text : visibleTexts) {
      final By locator =
          By.xpath(
              "//button[contains(normalize-space(.),"
                  + xpathLiteral(text)
                  + ")]"
                  + " | //a[contains(normalize-space(.),"
                  + xpathLiteral(text)
                  + ")]"
                  + " | //*[@role='button' and contains(normalize-space(.),"
                  + xpathLiteral(text)
                  + ")]"
                  + " | //span[contains(normalize-space(.),"
                  + xpathLiteral(text)
                  + ")]"
                  + " | //div[contains(normalize-space(.),"
                  + xpathLiteral(text)
                  + ")]");
      try {
        return waitForVisible(locator);
      } catch (final TimeoutException ignored) {
        // Try next visible text candidate.
      }
    }
    throw new TimeoutException("None of the expected texts are visible/clickable: " + visibleTexts);
  }

  private void assertTextVisible(final String text) {
    final By locator = By.xpath("//*[contains(normalize-space(.)," + xpathLiteral(text) + ")]");
    assertVisible(locator, "Expected text was not visible: " + text);
  }

  private void assertAnyTextVisible(final List<String> texts) {
    for (final String text : texts) {
      if (isTextVisible(text)) {
        return;
      }
    }
    throw new TimeoutException("None of the expected texts were visible: " + texts);
  }

  private void assertVisible(final By locator, final String message) {
    final WebElement element = waitForVisible(locator);
    assertNotNull(message, element);
  }

  private WebElement waitForVisible(final By locator) {
    return wait.until(
        webDriver -> {
          final List<WebElement> elements = webDriver.findElements(locator);
          return firstDisplayedElement(elements);
        });
  }

  private WebElement firstDisplayedElement(final List<WebElement> elements) {
    for (final WebElement element : elements) {
      if (element.isDisplayed()) {
        return element;
      }
    }
    return null;
  }

  private void safeClick(final WebElement element) {
    try {
      ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
      element.click();
    } catch (final Exception clickFailure) {
      ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
    }
  }

  private void waitForUiToLoad() {
    wait.until(
        webDriver ->
            "complete".equals(
                String.valueOf(
                    ((JavascriptExecutor) webDriver).executeScript("return document.readyState"))));
  }

  private void waitForAnyVisibleText(final List<String> texts) {
    wait.until(
        webDriver -> {
          for (final String text : texts) {
            if (isTextVisible(text)) {
              return true;
            }
          }
          return false;
        });
  }

  private boolean isTextVisible(final String text) {
    final By locator = By.xpath("//*[contains(normalize-space(.)," + xpathLiteral(text) + ")]");
    return firstDisplayedElement(driver.findElements(locator)) != null;
  }

  private WebElement findSectionByTitle(final String titleText) {
    return findSectionByTitle(List.of(titleText));
  }

  private WebElement findSectionByTitle(final List<String> titleTexts) {
    for (final String titleText : titleTexts) {
      final By locator =
          By.xpath(
              "//*[self::section or self::div][.//*[contains(normalize-space(.),"
                  + xpathLiteral(titleText)
                  + ")]]");
      try {
        return waitForVisible(locator);
      } catch (final TimeoutException ignored) {
        // Try next title variation.
      }
    }
    throw new TimeoutException("Could not locate section with any title variation: " + titleTexts);
  }

  private boolean hasLikelyUserName(final String sectionText) {
    final String[] lines = sectionText.split("\\r?\\n");
    for (final String line : lines) {
      final String trimmed = line.trim();
      if (trimmed.length() >= 3
          && !trimmed.contains("@")
          && !trimmed.equalsIgnoreCase("informacion general")
          && !trimmed.equalsIgnoreCase("business plan")
          && !trimmed.equalsIgnoreCase("cambiar plan")) {
        return true;
      }
    }
    return false;
  }

  private boolean hasBusinessList(final WebElement section) {
    final List<WebElement> listLikeElements =
        section.findElements(By.xpath(".//li | .//tr | .//article | .//tbody//tr"));
    if (firstDisplayedElement(listLikeElements) != null) {
      return true;
    }

    final String text = normalizedText(section.getText());
    return text.contains("negocio");
  }

  private boolean hasLegalContentText() {
    final List<WebElement> contentBlocks =
        driver.findElements(By.xpath("//main//*[self::p or self::li or self::article or self::section]"));
    int totalLength = 0;
    for (final WebElement block : contentBlocks) {
      if (block.isDisplayed()) {
        totalLength += block.getText().trim().length();
      }
    }
    return totalLength > 120;
  }

  private String waitForNewHandle(final Set<String> oldHandles, final String oldUrl) {
    try {
      wait.until(
          webDriver ->
              webDriver.getWindowHandles().size() > oldHandles.size()
                  || !Objects.equals(webDriver.getCurrentUrl(), oldUrl));
    } catch (final TimeoutException ignored) {
      // Continue and return null.
    }

    for (final String handle : driver.getWindowHandles()) {
      if (!oldHandles.contains(handle)) {
        return handle;
      }
    }
    return null;
  }

  private void takeScreenshot(final String checkpointName) {
    if (!(driver instanceof TakesScreenshot)) {
      return;
    }

    try {
      final Path screenshotPath = screenshotDir.resolve(slug(checkpointName) + ".png");
      final java.io.File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
      Files.copy(screenshot.toPath(), screenshotPath, StandardCopyOption.REPLACE_EXISTING);
    } catch (final Exception ignored) {
      // Screenshot failures should not mask validation failures.
    }
  }

  private void printFinalReport() {
    System.out.println("========== SaleADS Mi Negocio Workflow Report ==========");
    for (final Map.Entry<String, Boolean> item : finalReport.entrySet()) {
      final String status = item.getValue() ? "PASS" : "FAIL";
      System.out.println(item.getKey() + ": " + status);
    }
    for (final Map.Entry<String, String> legalUrl : legalUrls.entrySet()) {
      System.out.println("Final URL - " + legalUrl.getKey() + ": " + legalUrl.getValue());
    }
    System.out.println("Screenshots directory: " + screenshotDir.toAbsolutePath());
    System.out.println("========================================================");
  }

  private String readRequiredConfig(final String propertyName, final String envName) {
    final String value = readConfig(propertyName, envName, "");
    if (value.isBlank()) {
      throw new IllegalArgumentException(
          "Missing required configuration. Set -D"
              + propertyName
              + " or environment variable "
              + envName
              + ".");
    }
    return value;
  }

  private String readConfig(final String propertyName, final String envName, final String defaultValue) {
    final String propValue = System.getProperty(propertyName);
    if (propValue != null && !propValue.isBlank()) {
      return propValue.trim();
    }

    final String envValue = System.getenv(envName);
    if (envValue != null && !envValue.isBlank()) {
      return envValue.trim();
    }

    return defaultValue;
  }

  private String normalizedText(final String value) {
    return value == null ? "" : value.toLowerCase().replace('\u00e1', 'a').replace('\u00e9', 'e')
        .replace('\u00ed', 'i').replace('\u00f3', 'o').replace('\u00fa', 'u').replace('\u00f1', 'n');
  }

  private String slug(final String value) {
    return normalizedText(value).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
  }

  private String xpathLiteral(final String value) {
    if (!value.contains("'")) {
      return "'" + value + "'";
    }
    if (!value.contains("\"")) {
      return "\"" + value + "\"";
    }

    final StringBuilder builder = new StringBuilder("concat(");
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
      builder.append(literal);
      if (i < chars.length - 1) {
        builder.append(',');
      }
    }
    builder.append(')');
    return builder.toString();
  }

  @FunctionalInterface
  private interface StepAction {
    void run() throws Exception;
  }
}
