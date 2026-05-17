package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.After;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end coverage for the SaleADS "Mi Negocio" workflow.
 *
 * <p>The test is environment-agnostic: pass the login page via SALEADS_LOGIN_URL.
 */
public class SaleadsMiNegocioWorkflowTest {

  private static final String LOGIN = "Login";
  private static final String MI_NEGOCIO_MENU = "Mi Negocio menu";
  private static final String AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
  private static final String ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
  private static final String INFORMACION_GENERAL = "Información General";
  private static final String DETALLES_CUENTA = "Detalles de la Cuenta";
  private static final String TUS_NEGOCIOS = "Tus Negocios";
  private static final String TERMINOS = "Términos y Condiciones";
  private static final String PRIVACIDAD = "Política de Privacidad";

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
  private static final Pattern NAME_PATTERN =
      Pattern.compile("(?iu)\\b\\p{L}{2,}(?:\\s+\\p{L}{2,})+\\b");

  private final Map<String, StepResult> report = new LinkedHashMap<>();
  private final Map<String, String> legalUrls = new LinkedHashMap<>();
  private final List<String> orderedSteps =
      List.of(
          LOGIN,
          MI_NEGOCIO_MENU,
          AGREGAR_NEGOCIO_MODAL,
          ADMINISTRAR_NEGOCIOS_VIEW,
          INFORMACION_GENERAL,
          DETALLES_CUENTA,
          TUS_NEGOCIOS,
          TERMINOS,
          PRIVACIDAD);

  private WebDriver driver;
  private WebDriverWait wait;
  private Path evidenceDir;

  @Before
  public void setUp() throws IOException {
    final String loginUrl = readEnv("SALEADS_LOGIN_URL");
    Assume.assumeTrue(
        "Set SALEADS_LOGIN_URL with the SaleADS login page for current environment.",
        loginUrl != null && !loginUrl.isBlank());

    final boolean headless = Boolean.parseBoolean(readEnvOrDefault("SALEADS_HEADLESS", "true"));
    final int timeoutSeconds = Integer.parseInt(readEnvOrDefault("SALEADS_TIMEOUT_SECONDS", "25"));
    final ChromeOptions options = new ChromeOptions();
    options.addArguments("--window-size=1920,1080");
    options.addArguments("--disable-dev-shm-usage");
    options.addArguments("--no-sandbox");
    if (headless) {
      options.addArguments("--headless=new");
    }

    driver = new ChromeDriver(options);
    wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
    evidenceDir = Path.of(readEnvOrDefault("SALEADS_EVIDENCE_DIR", "target/saleads-mi-negocio-evidence"));
    Files.createDirectories(evidenceDir);
  }

  @After
  public void tearDown() throws IOException {
    try {
      if (evidenceDir != null) {
        writeReport();
      }
    } finally {
      if (driver != null) {
        driver.quit();
      }
    }
  }

  @Test
  public void saleadsMiNegocioFullWorkflow() throws IOException {
    final String loginUrl = readEnvOrDefault("SALEADS_LOGIN_URL", "").trim();
    final String googleEmail =
        readEnvOrDefault("SALEADS_GOOGLE_ACCOUNT_EMAIL", "juanlucasbarbiergarzon@gmail.com").trim();
    final String expectedName = readEnv("SALEADS_EXPECTED_USER_NAME");

    driver.get(loginUrl);
    waitForUiToLoad();

    runStep(
        LOGIN,
        () -> {
          clickAnyByVisibleText(
              List.of(
                  "Sign in with Google",
                  "Iniciar sesión con Google",
                  "Iniciar sesion con Google",
                  "Continuar con Google",
                  "Google"));
          selectGoogleAccountIfPrompted(googleEmail);
          assertVisibleText("Negocio");
          assertSidebarVisible();
          captureScreenshot("01-dashboard");
        });

    runStep(
        MI_NEGOCIO_MENU,
        () -> {
          clickAnyByVisibleText(List.of("Negocio"));
          clickAnyByVisibleText(List.of("Mi Negocio"));
          assertVisibleText("Agregar Negocio");
          assertVisibleText("Administrar Negocios");
          captureScreenshot("02-mi-negocio-menu-expandido");
        });

    runStep(
        AGREGAR_NEGOCIO_MODAL,
        () -> {
          clickAnyByVisibleText(List.of("Agregar Negocio"));
          assertVisibleText("Crear Nuevo Negocio");
          assertVisibleText("Nombre del Negocio");
          assertVisibleText("Tienes 2 de 3 negocios");
          assertVisibleText("Cancelar");
          assertVisibleText("Crear Negocio");
          captureScreenshot("03-modal-crear-negocio");

          clickAnyByVisibleText(List.of("Nombre del Negocio"));
          final WebElement negocioInput = findVisibleElementContainingText("Nombre del Negocio");
          final WebElement inputField =
              findDescendant(negocioInput, By.xpath(".//ancestor::div[1]//input[1]"));
          inputField.clear();
          inputField.sendKeys("Negocio Prueba Automatización");
          clickAnyByVisibleText(List.of("Cancelar"));
          wait.until(ExpectedConditions.invisibilityOfElementLocated(byVisibleText("Crear Nuevo Negocio")));
        });

    runStep(
        ADMINISTRAR_NEGOCIOS_VIEW,
        () -> {
          ensureMiNegocioExpanded();
          clickAnyByVisibleText(List.of("Administrar Negocios"));
          assertVisibleText("Información General");
          assertVisibleText("Detalles de la Cuenta");
          assertVisibleText("Tus Negocios");
          assertVisibleText("Sección Legal");
          captureScreenshot("04-administrar-negocios");
        });

    runStep(
        INFORMACION_GENERAL,
        () -> {
          final WebElement section = findSectionByHeading("Información General");
          final String sectionText = section.getText();
          assertTrue("User email must be visible", sectionText.contains(googleEmail));
          if (expectedName != null && !expectedName.isBlank()) {
            assertTrue("Expected user name must be visible", sectionText.contains(expectedName));
          } else {
            assertTrue("A probable user name must be visible", NAME_PATTERN.matcher(sectionText).find());
          }
          assertVisibleInsideSection(section, "BUSINESS PLAN");
          assertVisibleInsideSection(section, "Cambiar Plan");
        });

    runStep(
        DETALLES_CUENTA,
        () -> {
          final WebElement section = findSectionByHeading("Detalles de la Cuenta");
          assertVisibleInsideSection(section, "Cuenta creada");
          assertVisibleInsideSection(section, "Estado activo");
          assertVisibleInsideSection(section, "Idioma seleccionado");
        });

    runStep(
        TUS_NEGOCIOS,
        () -> {
          final WebElement section = findSectionByHeading("Tus Negocios");
          assertVisibleInsideSection(section, "Agregar Negocio");
          assertVisibleInsideSection(section, "Tienes 2 de 3 negocios");
          final String sectionText = section.getText();
          final long visibleLines =
              sectionText.lines().map(String::trim).filter(line -> !line.isBlank()).count();
          assertTrue("Business list must be visible", visibleLines >= 3);
        });

    runStep(
        TERMINOS,
        () -> {
          final String finalUrl =
              openLegalLinkAndValidate(
                  "Términos y Condiciones",
                  List.of("Términos y Condiciones", "Terminos y Condiciones"),
                  "05-terminos-y-condiciones");
          legalUrls.put(TERMINOS, finalUrl);
        });

    runStep(
        PRIVACIDAD,
        () -> {
          final String finalUrl =
              openLegalLinkAndValidate(
                  "Política de Privacidad",
                  List.of("Política de Privacidad", "Politica de Privacidad"),
                  "06-politica-de-privacidad");
          legalUrls.put(PRIVACIDAD, finalUrl);
        });

    final List<String> failedSteps =
        report.entrySet().stream()
            .filter(entry -> !entry.getValue().passed())
            .map(Map.Entry::getKey)
            .collect(Collectors.toCollection(ArrayList::new));
    assertTrue("Workflow contains failed validations: " + failedSteps, failedSteps.isEmpty());
  }

  private void runStep(final String stepName, final ThrowingRunnable action) {
    try {
      action.run();
      report.put(stepName, StepResult.pass());
    } catch (Exception | AssertionError error) {
      final String screenshotName = "error-" + sanitizeFileName(stepName);
      try {
        captureScreenshot(screenshotName);
      } catch (Exception ignored) {
        // Failure screenshot is best effort.
      }
      report.put(stepName, StepResult.fail(error.getMessage()));
    }
  }

  private void clickAnyByVisibleText(final List<String> textCandidates) {
    Exception latestError = null;

    for (final String text : textCandidates) {
      try {
        final WebElement element = wait.until(ExpectedConditions.elementToBeClickable(clickableByText(text)));
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
        element.click();
        waitForUiToLoad();
        return;
      } catch (Exception error) {
        latestError = error;
      }
    }

    throw new IllegalStateException("Unable to click any visible text candidate: " + textCandidates, latestError);
  }

  private By clickableByText(final String text) {
    final String literal = asXPathLiteral(text);
    final String xpath =
        "("
            + "//*[contains(normalize-space(.), "
            + literal
            + ")]/ancestor-or-self::*[self::button or self::a or @role='button'][1]"
            + " | "
            + "//*[contains(normalize-space(.), "
            + literal
            + ")][1]"
            + ")[1]";
    return By.xpath(xpath);
  }

  private By byVisibleText(final String text) {
    return By.xpath("//*[contains(normalize-space(.), " + asXPathLiteral(text) + ")]");
  }

  private WebElement findVisibleElementContainingText(final String text) {
    return wait.until(ExpectedConditions.visibilityOfElementLocated(byVisibleText(text)));
  }

  private void assertVisibleText(final String text) {
    wait.until(ExpectedConditions.visibilityOfElementLocated(byVisibleText(text)));
  }

  private void assertSidebarVisible() {
    final Set<By> sidebarCandidates = new LinkedHashSet<>();
    sidebarCandidates.add(By.tagName("aside"));
    sidebarCandidates.add(By.xpath("//nav"));
    sidebarCandidates.add(By.xpath("//*[@role='navigation']"));

    for (final By locator : sidebarCandidates) {
      final List<WebElement> elements = driver.findElements(locator);
      for (final WebElement element : elements) {
        if (element.isDisplayed()) {
          return;
        }
      }
    }

    throw new AssertionError("Left sidebar navigation is not visible.");
  }

  private void ensureMiNegocioExpanded() {
    final List<WebElement> administrar = driver.findElements(byVisibleText("Administrar Negocios"));
    final boolean alreadyExpanded = administrar.stream().anyMatch(WebElement::isDisplayed);
    if (!alreadyExpanded) {
      clickAnyByVisibleText(List.of("Mi Negocio"));
    }
  }

  private WebElement findSectionByHeading(final String heading) {
    final WebElement headingElement = findVisibleElementContainingText(heading);
    try {
      return headingElement.findElement(By.xpath("./ancestor::*[self::section or self::article or self::div][1]"));
    } catch (Exception ignored) {
      return headingElement;
    }
  }

  private void assertVisibleInsideSection(final WebElement section, final String text) {
    final List<WebElement> matching =
        section.findElements(By.xpath(".//*[contains(normalize-space(.), " + asXPathLiteral(text) + ")]"));
    final boolean visible = matching.stream().anyMatch(WebElement::isDisplayed);
    assertTrue("Expected text not visible in section: " + text, visible);
  }

  private WebElement findDescendant(final WebElement root, final By locator) {
    return root.findElement(locator);
  }

  private String openLegalLinkAndValidate(
      final String linkText, final List<String> headingCandidates, final String screenshotName) {
    final String originalHandle = driver.getWindowHandle();
    final Set<String> handlesBefore = driver.getWindowHandles();

    clickAnyByVisibleText(List.of(linkText));

    final String activeHandle = detectActiveHandleAfterNavigation(originalHandle, handlesBefore);
    final boolean switchedToNewTab = !activeHandle.equals(originalHandle);
    if (switchedToNewTab) {
      driver.switchTo().window(activeHandle);
      waitForUiToLoad();
    }

    boolean headingFound = false;
    for (final String heading : headingCandidates) {
      if (isTextVisible(heading, Duration.ofSeconds(10))) {
        headingFound = true;
        break;
      }
    }
    assertTrue("Legal heading must be visible for " + linkText, headingFound);

    final String bodyText = driver.findElement(By.tagName("body")).getText();
    assertTrue("Legal content must be visible for " + linkText, bodyText != null && bodyText.trim().length() > 120);
    captureScreenshot(screenshotName);
    final String finalUrl = driver.getCurrentUrl();

    if (switchedToNewTab) {
      driver.close();
      driver.switchTo().window(originalHandle);
    } else {
      driver.navigate().back();
    }
    waitForUiToLoad();
    return finalUrl;
  }

  private String detectActiveHandleAfterNavigation(
      final String originalHandle, final Set<String> handlesBeforeClick) {
    try {
      final WebDriverWait handleWait = new WebDriverWait(driver, Duration.ofSeconds(8));
      return handleWait.until(
          driverRef -> {
            final Set<String> handlesAfter = driverRef.getWindowHandles();
            if (handlesAfter.size() > handlesBeforeClick.size()) {
              for (final String handle : handlesAfter) {
                if (!handlesBeforeClick.contains(handle)) {
                  return handle;
                }
              }
            }
            return null;
          });
    } catch (TimeoutException ignored) {
      return originalHandle;
    }
  }

  private void selectGoogleAccountIfPrompted(final String googleEmail) {
    final List<String> candidates =
        List.of(
            "juanlucasbarbiergarzon@gmail.com",
            googleEmail,
            "Choose an account",
            "Elige una cuenta",
            "Selecciona una cuenta");

    for (final String candidate : candidates) {
      if (isTextVisible(candidate, Duration.ofSeconds(10))) {
        if (candidate.equals(googleEmail) || candidate.equals("juanlucasbarbiergarzon@gmail.com")) {
          clickAnyByVisibleText(List.of(candidate));
        }
        waitForUiToLoad();
        return;
      }
    }
  }

  private boolean isTextVisible(final String text, final Duration timeout) {
    try {
      new WebDriverWait(driver, timeout)
          .until(ExpectedConditions.visibilityOfElementLocated(byVisibleText(text)));
      return true;
    } catch (Exception ignored) {
      return false;
    }
  }

  private void waitForUiToLoad() {
    wait.until(
        driverRef ->
            Boolean.TRUE.equals(
                ((JavascriptExecutor) driverRef)
                    .executeScript(
                        "return document.readyState === 'complete' &&"
                            + " (window.jQuery ? window.jQuery.active === 0 : true);")));
  }

  private void captureScreenshot(final String name) throws IOException {
    final byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
    final Path screenshotPath = evidenceDir.resolve(sanitizeFileName(name) + ".png");
    Files.write(screenshotPath, screenshot);
  }

  private void writeReport() throws IOException {
    for (final String step : orderedSteps) {
      report.putIfAbsent(step, StepResult.fail("Not executed."));
    }

    final StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"generatedAt\": \"").append(Instant.now()).append("\",\n");
    json.append("  \"evidenceDir\": \"").append(escapeJson(evidenceDir.toString())).append("\",\n");
    json.append("  \"results\": {\n");

    for (int i = 0; i < orderedSteps.size(); i++) {
      final String stepName = orderedSteps.get(i);
      final StepResult result = report.get(stepName);
      json.append("    \"")
          .append(escapeJson(stepName))
          .append("\": {\n")
          .append("      \"status\": \"")
          .append(result.passed() ? "PASS" : "FAIL")
          .append("\",\n")
          .append("      \"details\": \"")
          .append(escapeJson(result.details()))
          .append("\"\n")
          .append("    }");
      if (i < orderedSteps.size() - 1) {
        json.append(",");
      }
      json.append("\n");
    }
    json.append("  },\n");
    json.append("  \"legalUrls\": {\n");

    int index = 0;
    for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
      json.append("    \"")
          .append(escapeJson(entry.getKey()))
          .append("\": \"")
          .append(escapeJson(entry.getValue()))
          .append("\"");
      if (index < legalUrls.size() - 1) {
        json.append(",");
      }
      json.append("\n");
      index++;
    }
    json.append("  }\n");
    json.append("}\n");

    final Path reportPath = Path.of("target/saleads-mi-negocio-report.json");
    Files.createDirectories(reportPath.getParent());
    Files.writeString(reportPath, json.toString(), StandardCharsets.UTF_8);
  }

  private String escapeJson(final String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r");
  }

  private String sanitizeFileName(final String value) {
    return value.toLowerCase().replaceAll("[^a-z0-9._-]+", "-");
  }

  private String readEnvOrDefault(final String key, final String fallback) {
    final String value = System.getenv(key);
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value;
  }

  private String readEnv(final String key) {
    return System.getenv(key);
  }

  private String asXPathLiteral(final String value) {
    if (!value.contains("'")) {
      return "'" + value + "'";
    }
    if (!value.contains("\"")) {
      return "\"" + value + "\"";
    }

    final StringBuilder literal = new StringBuilder("concat(");
    final String[] parts = value.split("'");
    for (int i = 0; i < parts.length; i++) {
      if (i > 0) {
        literal.append(",\"'\",");
      }
      literal.append("'").append(parts[i]).append("'");
    }
    literal.append(")");
    return literal.toString();
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  private record StepResult(boolean passed, String details) {
    private static StepResult pass() {
      return new StepResult(true, "All validations passed.");
    }

    private static StepResult fail(final String details) {
      final String safeDetails =
          details == null || details.isBlank() ? "Validation failed without details." : details;
      return new StepResult(false, safeDetails);
    }
  }
}
