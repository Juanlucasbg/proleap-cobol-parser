package saleads;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class SaleadsMiNegocioFullTest {

  private static final String LOGIN_URL_ENV = "SALEADS_LOGIN_URL";
  private static final String E2E_ENABLED_ENV = "SALEADS_E2E_ENABLED";
  private static final String ACCOUNT_EMAIL_ENV = "SALEADS_GOOGLE_ACCOUNT_EMAIL";
  private static final String HEADLESS_ENV = "SALEADS_HEADLESS";
  private static final String EVIDENCE_DIR_ENV = "SALEADS_EVIDENCE_DIR";
  private static final String TIMEOUT_ENV = "SALEADS_TIMEOUT_MS";
  private static final String ACCOUNT_EMAIL_DEFAULT = "juanlucasbarbiergarzon@gmail.com";
  private static final String EVIDENCE_DIR_DEFAULT = "target/saleads-evidence";
  private static final int DEFAULT_TIMEOUT_MS = 30000;
  private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+");

  @Test
  public void saleadsMiNegocioFullWorkflow() throws IOException {
    Assume.assumeTrue(
        "Set SALEADS_E2E_ENABLED=true to run the SaleADS Mi Negocio end-to-end workflow.",
        readBooleanEnv(E2E_ENABLED_ENV, false));

    final int timeoutMs = readIntEnv(TIMEOUT_ENV, DEFAULT_TIMEOUT_MS);
    final boolean headless = readBooleanEnv(HEADLESS_ENV, true);
    final String loginUrl = readStringEnv(LOGIN_URL_ENV, "");
    Assume.assumeTrue(
        "Set SALEADS_LOGIN_URL to the current environment login page URL.",
        !loginUrl.isEmpty());
    final String accountEmail = readStringEnv(ACCOUNT_EMAIL_ENV, ACCOUNT_EMAIL_DEFAULT);
    final Path evidenceDir = Paths.get(readStringEnv(EVIDENCE_DIR_ENV, EVIDENCE_DIR_DEFAULT));
    Files.createDirectories(evidenceDir);

    final LinkedHashMap<String, Boolean> report = new LinkedHashMap<>();
    final List<String> notes = new ArrayList<>();
    final List<String> legalUrls = new ArrayList<>();

    try (Playwright playwright = Playwright.create()) {
      Browser browser = playwright.chromium().launch(
          new BrowserType.LaunchOptions().setHeadless(headless));
      BrowserContext context = browser.newContext();
      Page appPage = context.newPage();

      appPage.navigate(loginUrl);
      waitForUi(appPage, timeoutMs);

      report.put("Login", runStep(() -> validateLogin(appPage, context, accountEmail, evidenceDir, timeoutMs), notes));
      report.put("Mi Negocio menu", runStep(() -> validateMiNegocioMenu(appPage, evidenceDir, timeoutMs), notes));
      report.put("Agregar Negocio modal", runStep(() -> validateAgregarNegocioModal(appPage, evidenceDir, timeoutMs), notes));
      report.put("Administrar Negocios view", runStep(() -> validateAdministrarNegociosView(appPage, evidenceDir, timeoutMs), notes));
      report.put("Información General", runStep(() -> validateInformacionGeneral(appPage, accountEmail, timeoutMs), notes));
      report.put("Detalles de la Cuenta", runStep(() -> validateDetallesCuenta(appPage, timeoutMs), notes));
      report.put("Tus Negocios", runStep(() -> validateTusNegocios(appPage, timeoutMs), notes));

      LegalCheckResult termsResult = runLegalStep(
          () -> validateLegalLink(appPage, context, "Términos y Condiciones",
              "Términos y Condiciones", "08-terminos-y-condiciones.png", evidenceDir, timeoutMs),
          "Términos y Condiciones", notes);
      report.put("Términos y Condiciones", termsResult.passed);
      legalUrls.add("Términos y Condiciones URL: " + termsResult.finalUrl);

      LegalCheckResult privacyResult = runLegalStep(
          () -> validateLegalLink(appPage, context, "Política de Privacidad",
              "Política de Privacidad", "09-politica-de-privacidad.png", evidenceDir, timeoutMs),
          "Política de Privacidad", notes);
      report.put("Política de Privacidad", privacyResult.passed);
      legalUrls.add("Política de Privacidad URL: " + privacyResult.finalUrl);

      writeFinalReport(evidenceDir, report, legalUrls, notes);
      browser.close();
    }

    String finalReportText = formatReport(report, legalUrls, notes);
    Assert.assertTrue("SaleADS Mi Negocio workflow failed:\n" + finalReportText, report.values().stream().allMatch(Boolean::booleanValue));
  }

  private boolean validateLogin(Page appPage, BrowserContext context, String accountEmail, Path evidenceDir, int timeoutMs) {
    Locator googleLogin = findVisible(appPage, Arrays.asList(
        "button:has-text(\"Sign in with Google\")",
        "button:has-text(\"Iniciar sesión con Google\")",
        "button:has-text(\"Continuar con Google\")",
        "text=Sign in with Google",
        "text=Iniciar sesión con Google",
        "text=Google"), timeoutMs, "Google login button");

    int pageCountBeforeClick = context.pages().size();
    clickAndWait(appPage, googleLogin, timeoutMs);

    Page authPage = detectNewTab(context, appPage, pageCountBeforeClick, timeoutMs / 2);
    if (authPage != appPage) {
      authPage.bringToFront();
      waitForUi(authPage, timeoutMs);
    }

    selectGoogleAccountIfVisible(authPage, accountEmail, timeoutMs);
    if (authPage != appPage) {
      authPage.waitForTimeout(1500);
      appPage.bringToFront();
    }

    waitForUi(appPage, timeoutMs);
    boolean appVisible = isAnyVisible(appPage, Arrays.asList(
        "text=Negocio",
        "text=Mi Negocio",
        "aside",
        "nav"), timeoutMs);
    Assert.assertTrue("Main application interface/left sidebar was not visible after login.", appVisible);
    checkpointScreenshot(appPage, evidenceDir.resolve("01-dashboard-loaded.png"), false);
    return true;
  }

  private boolean validateMiNegocioMenu(Page appPage, Path evidenceDir, int timeoutMs) {
    Locator negocioSection = findVisible(appPage, Arrays.asList(
        "text=Negocio",
        "a:has-text(\"Negocio\")",
        "button:has-text(\"Negocio\")"), timeoutMs, "Negocio section");
    clickAndWait(appPage, negocioSection, timeoutMs);

    Locator miNegocio = findVisible(appPage, Arrays.asList(
        "text=Mi Negocio",
        "a:has-text(\"Mi Negocio\")",
        "button:has-text(\"Mi Negocio\")"), timeoutMs, "Mi Negocio menu item");
    clickAndWait(appPage, miNegocio, timeoutMs);

    boolean agregarVisible = isAnyVisible(appPage, Arrays.asList("text=Agregar Negocio"), timeoutMs);
    boolean administrarVisible = isAnyVisible(appPage, Arrays.asList("text=Administrar Negocios"), timeoutMs);

    Assert.assertTrue("Mi Negocio submenu did not show Agregar Negocio.", agregarVisible);
    Assert.assertTrue("Mi Negocio submenu did not show Administrar Negocios.", administrarVisible);
    checkpointScreenshot(appPage, evidenceDir.resolve("02-mi-negocio-menu-expanded.png"), false);
    return true;
  }

  private boolean validateAgregarNegocioModal(Page appPage, Path evidenceDir, int timeoutMs) {
    Locator agregarNegocio = findVisible(appPage, Arrays.asList(
        "text=Agregar Negocio",
        "button:has-text(\"Agregar Negocio\")",
        "a:has-text(\"Agregar Negocio\")"), timeoutMs, "Agregar Negocio");
    clickAndWait(appPage, agregarNegocio, timeoutMs);

    boolean modalTitle = isAnyVisible(appPage, Arrays.asList("text=Crear Nuevo Negocio"), timeoutMs);
    boolean nombreInput = isAnyVisible(appPage, Arrays.asList(
        "input[placeholder*='Nombre del Negocio']",
        "input[name*='nombre']",
        "input[id*='nombre']"), timeoutMs);
    boolean cuotaText = isAnyVisible(appPage, Arrays.asList("text=Tienes 2 de 3 negocios"), timeoutMs);
    boolean cancelarBtn = isAnyVisible(appPage, Arrays.asList("button:has-text(\"Cancelar\")", "text=Cancelar"), timeoutMs);
    boolean crearBtn = isAnyVisible(appPage, Arrays.asList("button:has-text(\"Crear Negocio\")", "text=Crear Negocio"), timeoutMs);

    Assert.assertTrue("Crear Nuevo Negocio modal title not visible.", modalTitle);
    Assert.assertTrue("Nombre del Negocio input not visible.", nombreInput);
    Assert.assertTrue("Quota text 'Tienes 2 de 3 negocios' not visible.", cuotaText);
    Assert.assertTrue("Cancelar button not visible.", cancelarBtn);
    Assert.assertTrue("Crear Negocio button not visible.", crearBtn);

    Locator nombreField = findVisible(appPage, Arrays.asList(
        "input[placeholder*='Nombre del Negocio']",
        "input[name*='nombre']",
        "input[id*='nombre']"), timeoutMs, "Nombre del Negocio field");
    nombreField.fill("Negocio Prueba Automatización");
    waitForUi(appPage, timeoutMs);

    checkpointScreenshot(appPage, evidenceDir.resolve("03-agregar-negocio-modal.png"), false);
    Locator cancelar = findVisible(appPage, Arrays.asList(
        "button:has-text(\"Cancelar\")",
        "text=Cancelar"), timeoutMs, "Cancelar button");
    clickAndWait(appPage, cancelar, timeoutMs);
    return true;
  }

  private boolean validateAdministrarNegociosView(Page appPage, Path evidenceDir, int timeoutMs) {
    if (!isAnyVisible(appPage, Arrays.asList("text=Administrar Negocios"), 2000)) {
      Locator miNegocio = findVisible(appPage, Arrays.asList(
          "text=Mi Negocio",
          "button:has-text(\"Mi Negocio\")",
          "a:has-text(\"Mi Negocio\")"), timeoutMs, "Mi Negocio");
      clickAndWait(appPage, miNegocio, timeoutMs);
    }

    Locator administrar = findVisible(appPage, Arrays.asList(
        "text=Administrar Negocios",
        "button:has-text(\"Administrar Negocios\")",
        "a:has-text(\"Administrar Negocios\")"), timeoutMs, "Administrar Negocios");
    clickAndWait(appPage, administrar, timeoutMs);

    boolean infoGeneral = isAnyVisible(appPage, Arrays.asList("text=Información General"), timeoutMs);
    boolean detallesCuenta = isAnyVisible(appPage, Arrays.asList("text=Detalles de la Cuenta"), timeoutMs);
    boolean tusNegocios = isAnyVisible(appPage, Arrays.asList("text=Tus Negocios"), timeoutMs);
    boolean seccionLegal = isAnyVisible(appPage, Arrays.asList("text=Sección Legal"), timeoutMs);

    Assert.assertTrue("Información General section not visible.", infoGeneral);
    Assert.assertTrue("Detalles de la Cuenta section not visible.", detallesCuenta);
    Assert.assertTrue("Tus Negocios section not visible.", tusNegocios);
    Assert.assertTrue("Sección Legal section not visible.", seccionLegal);
    checkpointScreenshot(appPage, evidenceDir.resolve("04-administrar-negocios-page.png"), true);
    return true;
  }

  private boolean validateInformacionGeneral(Page appPage, String accountEmail, int timeoutMs) {
    Locator infoSection = findVisible(appPage, Arrays.asList(
        "section:has-text(\"Información General\")",
        "div:has-text(\"Información General\")",
        "text=Información General"), timeoutMs, "Información General section");
    String infoText = infoSection.innerText();

    boolean emailVisible = infoText.contains(accountEmail) || EMAIL_PATTERN.matcher(infoText).find();
    boolean businessPlanVisible = infoText.contains("BUSINESS PLAN");
    boolean cambiarPlanVisible = infoText.contains("Cambiar Plan");
    boolean userNameVisible = hasLikelyUserName(infoText);

    Assert.assertTrue("User name is not visible in Información General.", userNameVisible);
    Assert.assertTrue("User email is not visible in Información General.", emailVisible);
    Assert.assertTrue("BUSINESS PLAN text is not visible.", businessPlanVisible);
    Assert.assertTrue("Cambiar Plan button is not visible.", cambiarPlanVisible);
    return true;
  }

  private boolean validateDetallesCuenta(Page appPage, int timeoutMs) {
    Locator detallesSection = findVisible(appPage, Arrays.asList(
        "section:has-text(\"Detalles de la Cuenta\")",
        "div:has-text(\"Detalles de la Cuenta\")",
        "text=Detalles de la Cuenta"), timeoutMs, "Detalles de la Cuenta section");
    String detailsText = detallesSection.innerText();

    Assert.assertTrue("'Cuenta creada' is not visible.", detailsText.contains("Cuenta creada"));
    Assert.assertTrue("'Estado activo' is not visible.", detailsText.contains("Estado activo"));
    Assert.assertTrue("'Idioma seleccionado' is not visible.", detailsText.contains("Idioma seleccionado"));
    return true;
  }

  private boolean validateTusNegocios(Page appPage, int timeoutMs) {
    Locator negociosSection = findVisible(appPage, Arrays.asList(
        "section:has-text(\"Tus Negocios\")",
        "div:has-text(\"Tus Negocios\")",
        "text=Tus Negocios"), timeoutMs, "Tus Negocios section");
    String negociosText = negociosSection.innerText();

    boolean listVisible = negociosText.contains("Tus Negocios")
        && (negociosText.contains("Negocio") || negociosText.contains("business"));
    boolean addButtonVisible = negociosText.contains("Agregar Negocio");
    boolean quotaTextVisible = negociosText.contains("Tienes 2 de 3 negocios");

    Assert.assertTrue("Business list is not visible in Tus Negocios section.", listVisible);
    Assert.assertTrue("Agregar Negocio button is not visible in Tus Negocios section.", addButtonVisible);
    Assert.assertTrue("Text 'Tienes 2 de 3 negocios' is not visible in Tus Negocios section.", quotaTextVisible);
    return true;
  }

  private LegalCheckResult validateLegalLink(Page appPage, BrowserContext context, String linkText, String expectedHeading,
      String screenshotName, Path evidenceDir, int timeoutMs) {
    Locator link = findVisible(appPage, Arrays.asList(
        "a:has-text(\"" + linkText + "\")",
        "button:has-text(\"" + linkText + "\")",
        "text=" + linkText), timeoutMs, linkText + " link");

    int pagesBefore = context.pages().size();
    String appUrlBefore = appPage.url();

    clickAndWait(appPage, link, timeoutMs);
    Page targetPage = detectNewTab(context, appPage, pagesBefore, timeoutMs / 2);
    if (targetPage != appPage) {
      targetPage.bringToFront();
      waitForUi(targetPage, timeoutMs);
    }

    boolean headingVisible = isAnyVisible(targetPage, Arrays.asList(
        "h1:has-text(\"" + expectedHeading + "\")",
        "h2:has-text(\"" + expectedHeading + "\")",
        "text=" + expectedHeading), timeoutMs);

    String pageText = targetPage.locator("body").innerText();
    boolean legalTextVisible = pageText != null && pageText.trim().length() > 200;

    checkpointScreenshot(targetPage, evidenceDir.resolve(screenshotName), true);
    String finalUrl = targetPage.url();

    if (targetPage != appPage) {
      targetPage.close();
      appPage.bringToFront();
      waitForUi(appPage, timeoutMs);
    } else if (!appPage.url().equals(appUrlBefore)) {
      appPage.goBack();
      waitForUi(appPage, timeoutMs);
    }

    return new LegalCheckResult(headingVisible && legalTextVisible, finalUrl);
  }

  private static boolean runStep(Step step, List<String> notes) {
    try {
      return step.run();
    } catch (AssertionError | RuntimeException e) {
      notes.add(e.getMessage() == null ? e.toString() : e.getMessage());
      return false;
    }
  }

  private static LegalCheckResult runLegalStep(LegalStep step, String stepName, List<String> notes) {
    try {
      return step.run();
    } catch (AssertionError | RuntimeException e) {
      notes.add(e.getMessage() == null ? e.toString() : e.getMessage());
      return new LegalCheckResult(false, "UNAVAILABLE (" + stepName + " validation failed)");
    }
  }

  private static void waitForUi(Page page, int timeoutMs) {
    page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE,
          new Page.WaitForLoadStateOptions().setTimeout((double) timeoutMs));
    } catch (PlaywrightException ignored) {
      // Some views keep open network connections; continue after DOM is stable.
    }
    page.waitForTimeout(400);
  }

  private static Locator findVisible(Page page, List<String> selectors, int timeoutMs, String elementDescription) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      for (String selector : selectors) {
        Locator locator = page.locator(selector).first();
        try {
          if (locator.count() > 0 && locator.isVisible()) {
            return locator;
          }
        } catch (PlaywrightException ignored) {
          // Transient stale locator states are retried.
        }
      }
      page.waitForTimeout(250);
    }
    throw new AssertionError("Could not find visible element: " + elementDescription + " (selectors: " + selectors + ")");
  }

  private static boolean isAnyVisible(Page page, List<String> selectors, int timeoutMs) {
    try {
      findVisible(page, selectors, timeoutMs, "any selector");
      return true;
    } catch (AssertionError e) {
      return false;
    }
  }

  private static void clickAndWait(Page page, Locator locator, int timeoutMs) {
    locator.click();
    waitForUi(page, timeoutMs);
  }

  private static void selectGoogleAccountIfVisible(Page authPage, String accountEmail, int timeoutMs) {
    if (!isAnyVisible(authPage, Arrays.asList("text=" + accountEmail), timeoutMs / 3)) {
      return;
    }
    Locator account = findVisible(authPage, Arrays.asList("text=" + accountEmail), timeoutMs / 2, "Google account");
    clickAndWait(authPage, account, timeoutMs);
  }

  private static Page detectNewTab(BrowserContext context, Page fallbackPage, int previousPageCount, int timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      List<Page> pages = context.pages();
      if (pages.size() > previousPageCount) {
        return pages.get(pages.size() - 1);
      }
      fallbackPage.waitForTimeout(200);
    }
    return fallbackPage;
  }

  private static void checkpointScreenshot(Page page, Path path, boolean fullPage) {
    page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
  }

  private static boolean hasLikelyUserName(String sectionText) {
    String[] lines = sectionText.split("\\R");
    for (String line : lines) {
      String normalized = line.trim();
      if (normalized.isEmpty()) {
        continue;
      }
      if (normalized.toLowerCase().contains("información general")
          || normalized.toLowerCase().contains("business plan")
          || normalized.toLowerCase().contains("cambiar plan")
          || normalized.toLowerCase().contains("nombre")
          || normalized.toLowerCase().contains("usuario")
          || EMAIL_PATTERN.matcher(normalized).find()) {
        continue;
      }
      Matcher matcher = Pattern.compile("[A-Za-zÁÉÍÓÚáéíóúÑñ]{2,}").matcher(normalized);
      int tokenCount = 0;
      while (matcher.find()) {
        tokenCount++;
      }
      if (tokenCount >= 2) {
        return true;
      }
    }
    return false;
  }

  private static void writeFinalReport(Path evidenceDir, Map<String, Boolean> report, List<String> legalUrls, List<String> notes)
      throws IOException {
    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    StringBuilder sb = new StringBuilder();
    sb.append("SaleADS Mi Negocio Workflow Report").append(System.lineSeparator());
    sb.append("Generated at: ").append(timestamp).append(System.lineSeparator()).append(System.lineSeparator());

    for (Map.Entry<String, Boolean> entry : report.entrySet()) {
      sb.append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL").append(System.lineSeparator());
    }

    sb.append(System.lineSeparator());
    for (String legalUrl : legalUrls) {
      sb.append(legalUrl).append(System.lineSeparator());
    }

    if (!notes.isEmpty()) {
      sb.append(System.lineSeparator()).append("Notes:").append(System.lineSeparator());
      for (String note : notes) {
        sb.append("- ").append(note).append(System.lineSeparator());
      }
    }

    Files.writeString(evidenceDir.resolve("final-report.txt"), sb.toString());
  }

  private static String formatReport(Map<String, Boolean> report, List<String> legalUrls, List<String> notes) {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, Boolean> entry : report.entrySet()) {
      sb.append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL").append(System.lineSeparator());
    }
    for (String legalUrl : legalUrls) {
      sb.append(legalUrl).append(System.lineSeparator());
    }
    if (!notes.isEmpty()) {
      sb.append("Notes: ").append(notes).append(System.lineSeparator());
    }
    return sb.toString();
  }

  private static int readIntEnv(String name, int defaultValue) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static String readStringEnv(String name, String defaultValue) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    return value.trim();
  }

  private static boolean readBooleanEnv(String name, boolean defaultValue) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    return Boolean.parseBoolean(value.trim());
  }

  private interface Step {
    boolean run();
  }

  private interface LegalStep {
    LegalCheckResult run();
  }

  private static final class LegalCheckResult {
    private final boolean passed;
    private final String finalUrl;

    private LegalCheckResult(boolean passed, String finalUrl) {
      this.passed = passed;
      this.finalUrl = finalUrl;
    }
  }
}
