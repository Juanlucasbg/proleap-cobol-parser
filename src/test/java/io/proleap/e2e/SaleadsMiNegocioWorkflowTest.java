package io.proleap.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SaleadsMiNegocioWorkflowTest {

  private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
  private static final Pattern NEGOCIO_LABEL = Pattern.compile("(?i)^\\s*negocio\\s*$");
  private static final Pattern MI_NEGOCIO_LABEL = Pattern.compile("(?i)mi\\s+negocio");
  private static final Pattern AGREGAR_NEGOCIO_LABEL = Pattern.compile("(?i)agregar\\s+negocio");
  private static final Pattern ADMINISTRAR_NEGOCIOS_LABEL = Pattern.compile("(?i)administrar\\s+negocios");
  private static final Pattern CREAR_NUEVO_NEGOCIO_LABEL = Pattern.compile("(?i)crear\\s+nuevo\\s+negocio");
  private static final Pattern TIENES_DOS_DE_TRES = Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios");
  private static final Pattern INFO_GENERAL_LABEL = Pattern.compile("(?i)informaci[o\\u00F3]n\\s+general");
  private static final Pattern DETALLES_CUENTA_LABEL = Pattern.compile("(?i)detalles\\s+de\\s+la\\s+cuenta");
  private static final Pattern TUS_NEGOCIOS_LABEL = Pattern.compile("(?i)tus\\s+negocios");
  private static final Pattern LEGAL_SECTION_LABEL = Pattern.compile("(?i)secci[o\\u00F3]n\\s+legal");
  private static final Pattern TERMINOS_LABEL = Pattern.compile("(?i)t[e\\u00E9]rminos\\s+y\\s+condiciones");
  private static final Pattern PRIVACIDAD_LABEL = Pattern.compile("(?i)pol[i\\u00ED]tica\\s+de\\s+privacidad");
  private static final Pattern GOOGLE_SIGN_IN_LABEL =
      Pattern.compile("(?i)(sign\\s*in\\s*with\\s*google|iniciar\\s*sesi[o\\u00F3]n\\s*con\\s*google|continuar\\s*con\\s*google|google)");
  private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
  private static final long DEFAULT_TIMEOUT_MS = 20_000;

  private final DateTimeFormatter timestampFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
  private Path screenshotDir;
  private int screenshotCounter = 1;

  @Test
  public void saleadsMiNegocioFullWorkflow() throws IOException {
    final String loginUrl = env("SALEADS_LOGIN_URL", "");
    Assume.assumeTrue(
        "Set SALEADS_LOGIN_URL to the current SaleADS login page for your environment.",
        !loginUrl.trim().isEmpty());

    final boolean headless = Boolean.parseBoolean(env("SALEADS_HEADLESS", "true"));
    screenshotDir = createScreenshotDir();

    final Map<String, StepStatus> report = initializeReport();

    try (Playwright playwright = Playwright.create()) {
      final Browser browser =
          playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
      final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 900));
      final Page page = context.newPage();

      page.navigate(loginUrl);
      waitForUiToLoad(page);

      runLoginStep(page, report);
      runOpenMiNegocioStep(page, report);
      runAgregarNegocioModalStep(page, report);
      runAdministrarNegociosStep(page, report);
      runInformacionGeneralStep(page, report);
      runDetallesCuentaStep(page, report);
      runTusNegociosStep(page, report);
      runLegalStep(page, context, TERMINOS_LABEL, TERMINOS_LABEL, "T\u00E9rminos y Condiciones", report);
      runLegalStep(page, context, PRIVACIDAD_LABEL, PRIVACIDAD_LABEL, "Pol\u00EDtica de Privacidad", report);
    }

    printReport(report);
    assertNoFailures(report);
  }

  private void runLoginStep(final Page page, final Map<String, StepStatus> report) {
    clickByPattern(page, GOOGLE_SIGN_IN_LABEL);
    chooseGoogleAccountIfVisible(page, GOOGLE_ACCOUNT_EMAIL);
    waitForUiToLoad(page);

    final boolean mainInterfaceVisible =
        waitForTextVisible(page, NEGOCIO_LABEL, DEFAULT_TIMEOUT_MS) || waitForTextVisible(page, MI_NEGOCIO_LABEL, DEFAULT_TIMEOUT_MS);
    record(report, "Login", "Main application interface appears", mainInterfaceVisible, page.url());

    final boolean sidebarVisible =
        isVisible(page.locator("aside").first()) || isVisible(page.locator("nav").first());
    record(report, "Login", "Left sidebar navigation is visible", sidebarVisible, page.url());

    captureScreenshot(page, "dashboard_loaded", false);
  }

  private void runOpenMiNegocioStep(final Page page, final Map<String, StepStatus> report) {
    clickByPattern(page, NEGOCIO_LABEL);
    clickByPattern(page, MI_NEGOCIO_LABEL);

    final boolean agregarVisible = waitForTextVisible(page, AGREGAR_NEGOCIO_LABEL, DEFAULT_TIMEOUT_MS);
    final boolean administrarVisible = waitForTextVisible(page, ADMINISTRAR_NEGOCIOS_LABEL, DEFAULT_TIMEOUT_MS);

    record(report, "Mi Negocio menu", "Submenu expands", agregarVisible || administrarVisible, page.url());
    record(report, "Mi Negocio menu", "'Agregar Negocio' is visible", agregarVisible, page.url());
    record(report, "Mi Negocio menu", "'Administrar Negocios' is visible", administrarVisible, page.url());

    captureScreenshot(page, "mi_negocio_menu_expanded", false);
  }

  private void runAgregarNegocioModalStep(final Page page, final Map<String, StepStatus> report) {
    clickByPattern(page, AGREGAR_NEGOCIO_LABEL);

    final boolean modalTitleVisible = waitForTextVisible(page, CREAR_NUEVO_NEGOCIO_LABEL, DEFAULT_TIMEOUT_MS);
    record(report, "Agregar Negocio modal", "Modal title 'Crear Nuevo Negocio' is visible", modalTitleVisible, page.url());

    final boolean inputExists =
        exists(page.getByLabel(Pattern.compile("(?i)nombre\\s+del\\s+negocio")))
            || exists(page.getByPlaceholder(Pattern.compile("(?i)nombre\\s+del\\s+negocio")))
            || exists(page.locator("input[name*='nombre'], input[id*='nombre']"));
    record(report, "Agregar Negocio modal", "Input field 'Nombre del Negocio' exists", inputExists, page.url());

    final boolean quotaVisible = waitForTextVisible(page, TIENES_DOS_DE_TRES, 5000);
    record(report, "Agregar Negocio modal", "Text 'Tienes 2 de 3 negocios' is visible", quotaVisible, page.url());

    final boolean cancelarVisible = waitForTextVisible(page, Pattern.compile("(?i)^\\s*cancelar\\s*$"), 5000);
    final boolean crearNegocioVisible = waitForTextVisible(page, Pattern.compile("(?i)crear\\s+negocio"), 5000);
    record(report, "Agregar Negocio modal", "Button 'Cancelar' is present", cancelarVisible, page.url());
    record(report, "Agregar Negocio modal", "Button 'Crear Negocio' is present", crearNegocioVisible, page.url());

    if (inputExists) {
      final Locator nameField = firstExisting(
          page.getByLabel(Pattern.compile("(?i)nombre\\s+del\\s+negocio")),
          page.getByPlaceholder(Pattern.compile("(?i)nombre\\s+del\\s+negocio")),
          page.locator("input[name*='nombre'], input[id*='nombre']"));

      if (nameField != null) {
        nameField.first().click();
        waitForUiToLoad(page);
        nameField.first().fill("Negocio Prueba Automatizacion");
        waitForUiToLoad(page);
      }
    }

    captureScreenshot(page, "agregar_negocio_modal", false);

    if (cancelarVisible) {
      clickByPattern(page, Pattern.compile("(?i)^\\s*cancelar\\s*$"));
    }
  }

  private void runAdministrarNegociosStep(final Page page, final Map<String, StepStatus> report) {
    if (!waitForTextVisible(page, ADMINISTRAR_NEGOCIOS_LABEL, 3000)) {
      clickByPattern(page, NEGOCIO_LABEL);
      clickByPattern(page, MI_NEGOCIO_LABEL);
    }

    clickByPattern(page, ADMINISTRAR_NEGOCIOS_LABEL);
    waitForUiToLoad(page);

    final boolean infoGeneralVisible = waitForTextVisible(page, INFO_GENERAL_LABEL, DEFAULT_TIMEOUT_MS);
    final boolean detallesVisible = waitForTextVisible(page, DETALLES_CUENTA_LABEL, DEFAULT_TIMEOUT_MS);
    final boolean tusNegociosVisible = waitForTextVisible(page, TUS_NEGOCIOS_LABEL, DEFAULT_TIMEOUT_MS);
    final boolean legalVisible = waitForTextVisible(page, LEGAL_SECTION_LABEL, DEFAULT_TIMEOUT_MS);

    record(report, "Administrar Negocios view", "Section 'Informaci\u00F3n General' exists", infoGeneralVisible, page.url());
    record(report, "Administrar Negocios view", "Section 'Detalles de la Cuenta' exists", detallesVisible, page.url());
    record(report, "Administrar Negocios view", "Section 'Tus Negocios' exists", tusNegociosVisible, page.url());
    record(report, "Administrar Negocios view", "Section 'Secci\u00F3n Legal' exists", legalVisible, page.url());

    captureScreenshot(page, "administrar_negocios_full", true);
  }

  private void runInformacionGeneralStep(final Page page, final Map<String, StepStatus> report) {
    final String sectionText = safeSectionText(page, INFO_GENERAL_LABEL);

    final boolean userEmailVisible = EMAIL_PATTERN.matcher(sectionText).find();
    record(report, "Informaci\u00F3n General", "User email is visible", userEmailVisible, extractFirstEmail(sectionText));

    final boolean userNameVisible = hasLikelyUserName(sectionText);
    record(report, "Informaci\u00F3n General", "User name is visible", userNameVisible, truncate(sectionText));

    final boolean businessPlanVisible = waitForTextVisible(page, Pattern.compile("(?i)business\\s+plan"), 5000);
    final boolean cambiarPlanVisible = waitForTextVisible(page, Pattern.compile("(?i)cambiar\\s+plan"), 5000);
    record(report, "Informaci\u00F3n General", "Text 'BUSINESS PLAN' is visible", businessPlanVisible, page.url());
    record(report, "Informaci\u00F3n General", "Button 'Cambiar Plan' is visible", cambiarPlanVisible, page.url());
  }

  private void runDetallesCuentaStep(final Page page, final Map<String, StepStatus> report) {
    final boolean cuentaCreadaVisible = waitForTextVisible(page, Pattern.compile("(?i)cuenta\\s+creada"), 5000);
    final boolean estadoActivoVisible = waitForTextVisible(page, Pattern.compile("(?i)estado\\s+activo"), 5000);
    final boolean idiomaVisible = waitForTextVisible(page, Pattern.compile("(?i)idioma\\s+seleccionado"), 5000);

    record(report, "Detalles de la Cuenta", "'Cuenta creada' is visible", cuentaCreadaVisible, page.url());
    record(report, "Detalles de la Cuenta", "'Estado activo' is visible", estadoActivoVisible, page.url());
    record(report, "Detalles de la Cuenta", "'Idioma seleccionado' is visible", idiomaVisible, page.url());
  }

  private void runTusNegociosStep(final Page page, final Map<String, StepStatus> report) {
    final String sectionText = safeSectionText(page, TUS_NEGOCIOS_LABEL);
    final boolean businessListVisible = sectionText.length() > 30;
    final boolean addButtonVisible = waitForTextVisible(page, AGREGAR_NEGOCIO_LABEL, 5000);
    final boolean quotaVisible = waitForTextVisible(page, TIENES_DOS_DE_TRES, 5000);

    record(report, "Tus Negocios", "Business list is visible", businessListVisible, truncate(sectionText));
    record(report, "Tus Negocios", "Button 'Agregar Negocio' exists", addButtonVisible, page.url());
    record(report, "Tus Negocios", "Text 'Tienes 2 de 3 negocios' is visible", quotaVisible, page.url());
  }

  private void runLegalStep(
      final Page page,
      final BrowserContext context,
      final Pattern linkPattern,
      final Pattern headingPattern,
      final String reportStepName,
      final Map<String, StepStatus> report) {

    final int pagesBeforeClick = context.pages().size();
    final String appUrlBefore = page.url();

    clickByPattern(page, linkPattern);

    Page targetPage = page;
    for (int i = 0; i < 10; i++) {
      if (context.pages().size() > pagesBeforeClick) {
        targetPage = context.pages().get(context.pages().size() - 1);
        break;
      }
      page.waitForTimeout(400);
    }
    waitForUiToLoad(targetPage);

    final boolean headingVisible = waitForTextVisible(targetPage, headingPattern, DEFAULT_TIMEOUT_MS);
    final String legalBody = safeBodyText(targetPage);
    final boolean legalContentVisible = legalBody.trim().length() > 150;

    record(report, reportStepName, "Heading is visible", headingVisible, targetPage.url());
    record(report, reportStepName, "Legal content text is visible", legalContentVisible, truncate(legalBody));
    record(report, reportStepName, "Final URL captured", !targetPage.url().trim().isEmpty(), targetPage.url());

    captureScreenshot(targetPage, "legal_" + sanitize(reportStepName), false);

    if (targetPage != page) {
      targetPage.close();
      page.bringToFront();
      waitForUiToLoad(page);
    } else {
      try {
        page.goBack();
        waitForUiToLoad(page);
      } catch (PlaywrightException ex) {
        page.navigate(appUrlBefore);
        waitForUiToLoad(page);
      }
    }
  }

  private Map<String, StepStatus> initializeReport() {
    final Map<String, StepStatus> report = new LinkedHashMap<>();
    report.put("Login", new StepStatus());
    report.put("Mi Negocio menu", new StepStatus());
    report.put("Agregar Negocio modal", new StepStatus());
    report.put("Administrar Negocios view", new StepStatus());
    report.put("Informaci\u00F3n General", new StepStatus());
    report.put("Detalles de la Cuenta", new StepStatus());
    report.put("Tus Negocios", new StepStatus());
    report.put("T\u00E9rminos y Condiciones", new StepStatus());
    report.put("Pol\u00EDtica de Privacidad", new StepStatus());
    return report;
  }

  private void clickByPattern(final Page page, final Pattern labelPattern) {
    final Locator byButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(labelPattern));
    final Locator byLink = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(labelPattern));
    final Locator byMenuItem = page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(labelPattern));
    final Locator byText = page.getByText(labelPattern);

    final Locator target = firstExisting(byButton, byLink, byMenuItem, byText);
    if (target == null) {
      throw new AssertionError("Could not locate clickable element by text pattern: " + labelPattern.pattern());
    }
    target.first().click();
    waitForUiToLoad(page);
  }

  private Locator firstExisting(final Locator... locators) {
    for (final Locator locator : locators) {
      if (locator != null && exists(locator)) {
        return locator;
      }
    }
    return null;
  }

  private boolean exists(final Locator locator) {
    try {
      return locator.count() > 0;
    } catch (PlaywrightException ex) {
      return false;
    }
  }

  private boolean waitForTextVisible(final Page page, final Pattern pattern, final double timeoutMs) {
    final Locator textLocator = page.getByText(pattern);
    try {
      textLocator.first().waitFor(
          new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
      return true;
    } catch (PlaywrightException ex) {
      return false;
    }
  }

  private void chooseGoogleAccountIfVisible(final Page page, final String accountEmail) {
    final Locator accountLocator = page.getByText(accountEmail, new Page.GetByTextOptions().setExact(true));
    if (exists(accountLocator) && isVisible(accountLocator.first())) {
      accountLocator.first().click();
      waitForUiToLoad(page);
    }
  }

  private boolean isVisible(final Locator locator) {
    try {
      return locator.isVisible();
    } catch (PlaywrightException ex) {
      return false;
    }
  }

  private void waitForUiToLoad(final Page page) {
    try {
      page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    } catch (PlaywrightException ignored) {
      // No-op: some SPA transitions do not trigger DOM content events.
    }
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(8000));
    } catch (PlaywrightException ignored) {
      // No-op: continuous network traffic can keep this state from firing.
    }
    page.waitForTimeout(600);
  }

  private String safeSectionText(final Page page, final Pattern sectionHeadingPattern) {
    final Locator sectionHeading = page.getByText(sectionHeadingPattern);
    if (!exists(sectionHeading)) {
      return "";
    }

    final Locator sectionContainer =
        page.locator("xpath=//*[self::section or self::div][.//*[normalize-space()]]")
            .filter(new Locator.FilterOptions().setHas(sectionHeading.first()));
    if (!exists(sectionContainer)) {
      return "";
    }

    try {
      return sectionContainer.first().innerText();
    } catch (PlaywrightException ex) {
      return "";
    }
  }

  private String safeBodyText(final Page page) {
    try {
      return page.locator("body").innerText();
    } catch (PlaywrightException ex) {
      return "";
    }
  }

  private boolean hasLikelyUserName(final String text) {
    if (text == null || text.trim().isEmpty()) {
      return false;
    }

    final String[] lines = text.split("\\R");
    for (final String line : lines) {
      final String normalized = line.trim();
      if (normalized.isEmpty()) {
        continue;
      }
      if (INFO_GENERAL_LABEL.matcher(normalized).find()) {
        continue;
      }
      if (EMAIL_PATTERN.matcher(normalized).find()) {
        continue;
      }
      if (normalized.length() >= 3 && normalized.split("\\s+").length >= 2) {
        return true;
      }
    }
    return false;
  }

  private String extractFirstEmail(final String text) {
    final Matcher matcher = EMAIL_PATTERN.matcher(text == null ? "" : text);
    if (matcher.find()) {
      return matcher.group();
    }
    return "";
  }

  private void captureScreenshot(final Page page, final String checkpoint, final boolean fullPage) {
    final String fileName = String.format("%02d_%s.png", screenshotCounter++, sanitize(checkpoint));
    final Path screenshotPath = screenshotDir.resolve(fileName);
    page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
  }

  private Path createScreenshotDir() throws IOException {
    final String stamp = LocalDateTime.now().format(timestampFormat);
    final Path dir = Paths.get("target", "saleads-mi-negocio-evidence", stamp);
    Files.createDirectories(dir);
    return dir;
  }

  private String sanitize(final String raw) {
    return raw.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
  }

  private String truncate(final String value) {
    if (value == null) {
      return "";
    }
    final String normalized = value.replaceAll("\\s+", " ").trim();
    if (normalized.length() <= 160) {
      return normalized;
    }
    return normalized.substring(0, 160) + "...";
  }

  private void record(
      final Map<String, StepStatus> report,
      final String stepName,
      final String checkDescription,
      final boolean pass,
      final String detail) {
    final StepStatus stepStatus = report.get(stepName);
    if (stepStatus == null) {
      throw new IllegalStateException("Missing report step: " + stepName);
    }
    stepStatus.record(checkDescription, pass, detail);
  }

  private void printReport(final Map<String, StepStatus> report) {
    System.out.println("===== SaleADS Mi Negocio workflow report =====");
    for (final Map.Entry<String, StepStatus> entry : report.entrySet()) {
      final String stepName = entry.getKey();
      final StepStatus status = entry.getValue();
      System.out.println((status.passed ? "PASS" : "FAIL") + " :: " + stepName);
      for (final String check : status.checks) {
        System.out.println("  - " + check);
      }
    }
    System.out.println("Screenshot directory: " + screenshotDir.toAbsolutePath());
  }

  private void assertNoFailures(final Map<String, StepStatus> report) {
    final List<String> failedSteps = new ArrayList<>();
    for (final Map.Entry<String, StepStatus> entry : report.entrySet()) {
      if (!entry.getValue().passed) {
        failedSteps.add(entry.getKey());
      }
    }

    if (!failedSteps.isEmpty()) {
      Assert.fail("Workflow validation failed for: " + String.join(", ", failedSteps));
    }
  }

  private String env(final String name, final String defaultValue) {
    final String value = System.getenv(name);
    return value == null ? defaultValue : value;
  }

  private static class StepStatus {
    private final List<String> checks = new ArrayList<>();
    private boolean passed = true;

    private void record(final String description, final boolean pass, final String detail) {
      if (!pass) {
        passed = false;
      }
      final String line = (pass ? "PASS" : "FAIL") + " - " + description + (detail == null || detail.isEmpty() ? "" : " [" + detail + "]");
      checks.add(line);
    }
  }
}
