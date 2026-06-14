package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;

/**
 * End-to-end validation for SaleADS.ai "Mi Negocio" workflow.
 *
 * <p>
 * Configuration:
 * <ul>
 * <li>saleads.login.url system property or SALEADS_LOGIN_URL env var (required)</li>
 * <li>SALEADS_HEADLESS env var (optional, default true)</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

  private static final String FIELD_LOGIN = "Login";
  private static final String FIELD_MENU = "Mi Negocio menu";
  private static final String FIELD_MODAL = "Agregar Negocio modal";
  private static final String FIELD_ADMIN_VIEW = "Administrar Negocios view";
  private static final String FIELD_INFO = "Información General";
  private static final String FIELD_ACCOUNT_DETAILS = "Detalles de la Cuenta";
  private static final String FIELD_BUSINESSES = "Tus Negocios";
  private static final String FIELD_TERMS = "Términos y Condiciones";
  private static final String FIELD_PRIVACY = "Política de Privacidad";

  private static final long CLICK_SETTLE_MS = 600L;
  private static final long SHORT_TIMEOUT_MS = 5_000L;
  private static final DateTimeFormatter RUN_STAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT);

  private final LinkedHashMap<String, StepResult> report = new LinkedHashMap<>();
  private Path runDir;

  @Test
  public void saleadsMiNegocioFullTest() throws IOException {
    initializeReport();
    runDir = createRunDir();

    final String loginUrl = resolveLoginUrl();
    if (loginUrl == null || loginUrl.isBlank()) {
      failStep(FIELD_LOGIN, "Missing SALEADS_LOGIN_URL or -Dsaleads.login.url; cannot start from login page.");
      markRemainingAsPrerequisiteFailures(FIELD_LOGIN);
      writeReportFiles();
      assertTrue("SaleADS workflow failed. Report: " + runDir.resolve("report.md"), allStepsPassed());
      return;
    }

    final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));

    try (Playwright playwright = Playwright.create()) {
      final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
      try (BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000))) {
        final Page page = context.newPage();
        page.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        waitForUi(page);
        putEvidence(FIELD_LOGIN, "login_url", loginUrl);
        putEvidence(FIELD_LOGIN, "initial_page", safeUrl(page));

        final boolean loginPassed = stepLoginWithGoogle(page);
        if (!loginPassed) {
          markRemainingAsPrerequisiteFailures(FIELD_LOGIN);
          captureStepScreenshot(page, FIELD_LOGIN, "final_state_after_login_failure.png", true);
        } else {
          stepOpenMiNegocioMenu(page);
          stepValidateAgregarNegocioModal(page);
          stepOpenAdministrarNegocios(page);
          stepValidateInformacionGeneral(page);
          stepValidateDetallesCuenta(page);
          stepValidateTusNegocios(page);
          stepValidateLegalDocument(page, context, FIELD_TERMS, "T[ée]rminos\\s+y\\s+Condiciones", "T[ée]rminos\\s+y\\s+Condiciones", "step8_terminos.png");
          stepValidateLegalDocument(page, context, FIELD_PRIVACY, "Pol[íi]tica\\s+de\\s+Privacidad", "Pol[íi]tica\\s+de\\s+Privacidad", "step9_politica.png");
        }
      }
    } catch (Exception ex) {
      failStep(FIELD_LOGIN, "Unexpected exception while running workflow: " + ex.getMessage());
      markRemainingAsPrerequisiteFailures(FIELD_LOGIN);
    }

    writeReportFiles();
    assertTrue("SaleADS workflow failed. Report: " + runDir.resolve("report.md"), allStepsPassed());
  }

  private boolean stepLoginWithGoogle(final Page page) {
    boolean clickedLogin = clickTextInPageOrFrames(page,
        "Sign\\s*in\\s*with\\s*Google",
        "Iniciar\\s+sesi[oó]n\\s+con\\s+Google",
        "Continuar\\s+con\\s+Google",
        "GOOGLE");

    if (!clickedLogin) {
      clickedLogin = clickTextInPageOrFrames(page,
          "Sign\\s*in",
          "Iniciar\\s+sesi[oó]n",
          "Inicia\\s+sesi[oó]n",
          "Acceder");
      if (clickedLogin) {
        clickedLogin = clickTextInPageOrFrames(page,
            "Sign\\s*in\\s*with\\s*Google",
            "Iniciar\\s+sesi[oó]n\\s+con\\s+Google",
            "Continuar\\s+con\\s+Google",
            "GOOGLE");
      }
    }

    if (!clickedLogin) {
      failStep(FIELD_LOGIN, "Could not find a visible login or Google login button.");
      return false;
    }

    clickTextInPageOrFrames(page, "juanlucasbarbiergarzon@gmail\\.com");
    waitForUi(page);

    final boolean sidebarVisible = isVisible(page.locator("aside, [role='navigation']").first(), 2_000L);
    final boolean negocioVisible = isVisibleTextInPageOrFrames(page, "Mi\\s+Negocio|Negocio|Dashboard|Panel");

    captureStepScreenshot(page, FIELD_LOGIN, "step1_dashboard.png", true);

    if (sidebarVisible && negocioVisible) {
      passStep(FIELD_LOGIN, "Main application interface loaded and left navigation is visible.");
      return true;
    }

    failStep(FIELD_LOGIN,
        "Login did not reach an authenticated dashboard with visible sidebar/navigation (Google OAuth may require manual credentials).");
    return false;
  }

  private void stepOpenMiNegocioMenu(final Page page) {
    clickTextInPageOrFrames(page, "Negocio");
    clickTextInPageOrFrames(page, "Mi\\s+Negocio");
    waitForUi(page);

    final boolean agregarVisible = isVisibleTextInPageOrFrames(page, "Agregar\\s+Negocio");
    final boolean administrarVisible = isVisibleTextInPageOrFrames(page, "Administrar\\s+Negocios");

    captureStepScreenshot(page, FIELD_MENU, "step2_menu_expandido.png", true);

    if (agregarVisible && administrarVisible) {
      passStep(FIELD_MENU, "Mi Negocio submenu expanded with expected options.");
      return;
    }

    failStep(FIELD_MENU, "Mi Negocio submenu is missing one or more expected options.");
  }

  private void stepValidateAgregarNegocioModal(final Page page) {
    clickTextInPageOrFrames(page, "Agregar\\s+Negocio");
    waitForUi(page);

    final boolean titleVisible = isVisibleTextInPageOrFrames(page, "Crear\\s+Nuevo\\s+Negocio");
    final boolean fieldLabelVisible = isVisibleTextInPageOrFrames(page, "Nombre\\s+del\\s+Negocio");
    final boolean planTextVisible = isVisibleTextInPageOrFrames(page, "Tienes\\s+2\\s+de\\s+3\\s+negocios");
    final boolean cancelVisible = isVisibleTextInPageOrFrames(page, "Cancelar");
    final boolean createVisible = isVisibleTextInPageOrFrames(page, "Crear\\s+Negocio");

    captureStepScreenshot(page, FIELD_MODAL, "step3_modal_agregar_negocio.png", true);

    // Optional action requested by workflow definition.
    tryFillBusinessName(page, "Negocio Prueba Automatización");
    clickTextInPageOrFrames(page, "Cancelar");
    waitForUi(page);

    if (titleVisible && fieldLabelVisible && planTextVisible && cancelVisible && createVisible) {
      passStep(FIELD_MODAL, "Crear Nuevo Negocio modal validated successfully.");
      return;
    }

    failStep(FIELD_MODAL, "Agregar Negocio modal did not show all required elements.");
  }

  private void stepOpenAdministrarNegocios(final Page page) {
    if (!isVisibleTextInPageOrFrames(page, "Administrar\\s+Negocios")) {
      clickTextInPageOrFrames(page, "Mi\\s+Negocio");
      waitForUi(page);
    }

    clickTextInPageOrFrames(page, "Administrar\\s+Negocios");
    waitForUi(page);

    final boolean infoGeneral = isVisibleTextInPageOrFrames(page, "Informaci[oó]n\\s+General");
    final boolean detallesCuenta = isVisibleTextInPageOrFrames(page, "Detalles\\s+de\\s+la\\s+Cuenta");
    final boolean tusNegocios = isVisibleTextInPageOrFrames(page, "Tus\\s+Negocios");
    final boolean seccionLegal = isVisibleTextInPageOrFrames(page, "Secci[oó]n\\s+Legal");

    captureStepScreenshot(page, FIELD_ADMIN_VIEW, "step4_administrar_negocios.png", true);

    if (infoGeneral && detallesCuenta && tusNegocios && seccionLegal) {
      passStep(FIELD_ADMIN_VIEW, "Administrar Negocios view contains all required sections.");
      return;
    }

    failStep(FIELD_ADMIN_VIEW, "Administrar Negocios view is missing one or more required sections.");
  }

  private void stepValidateInformacionGeneral(final Page page) {
    final boolean userNameVisible = isVisibleTextInPageOrFrames(page,
        "[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}");
    final boolean emailVisible = isVisibleTextInPageOrFrames(page,
        "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}");
    final boolean businessPlanVisible = isVisibleTextInPageOrFrames(page, "BUSINESS\\s*PLAN");
    final boolean cambiarPlanVisible = isVisibleTextInPageOrFrames(page, "Cambiar\\s+Plan");

    if (userNameVisible && emailVisible && businessPlanVisible && cambiarPlanVisible) {
      passStep(FIELD_INFO, "Información General shows user and plan data.");
      return;
    }

    failStep(FIELD_INFO, "Información General is missing one or more required validations.");
  }

  private void stepValidateDetallesCuenta(final Page page) {
    final boolean cuentaCreada = isVisibleTextInPageOrFrames(page, "Cuenta\\s+creada");
    final boolean estadoActivo = isVisibleTextInPageOrFrames(page, "Estado\\s+activo");
    final boolean idiomaSeleccionado = isVisibleTextInPageOrFrames(page, "Idioma\\s+seleccionado");

    if (cuentaCreada && estadoActivo && idiomaSeleccionado) {
      passStep(FIELD_ACCOUNT_DETAILS, "Detalles de la Cuenta validated successfully.");
      return;
    }

    failStep(FIELD_ACCOUNT_DETAILS, "Detalles de la Cuenta is missing one or more required texts.");
  }

  private void stepValidateTusNegocios(final Page page) {
    final boolean sectionVisible = isVisibleTextInPageOrFrames(page, "Tus\\s+Negocios");
    final boolean addButtonVisible = isVisibleTextInPageOrFrames(page, "Agregar\\s+Negocio");
    final boolean planTextVisible = isVisibleTextInPageOrFrames(page, "Tienes\\s+2\\s+de\\s+3\\s+negocios");
    final Locator possibleListItems = page.locator("ul li, [role='listitem'], table tbody tr, [data-testid*='business']");
    final boolean businessListVisible = possibleListItems.count() > 0 || isVisibleTextInPageOrFrames(page, "Negocio");

    if (sectionVisible && addButtonVisible && planTextVisible && businessListVisible) {
      passStep(FIELD_BUSINESSES, "Tus Negocios section validated successfully.");
      return;
    }

    failStep(FIELD_BUSINESSES, "Tus Negocios section is missing one or more required validations.");
  }

  private void stepValidateLegalDocument(final Page appPage, final BrowserContext context, final String reportField,
      final String linkTextRegex, final String headingRegex, final String screenshotName) {
    final String appUrlBefore = safeUrl(appPage);
    final AtomicBoolean clicked = new AtomicBoolean(false);

    Page targetPage = appPage;
    boolean openedPopup = false;
    try {
      targetPage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(4_000), () -> {
        clicked.set(clickTextInPageOrFrames(appPage, linkTextRegex));
      });
      openedPopup = true;
    } catch (PlaywrightException popupNotOpened) {
      if (!clicked.get()) {
        clicked.set(clickTextInPageOrFrames(appPage, linkTextRegex));
      }
      waitForUi(appPage);
    }

    if (!clicked.get()) {
      failStep(reportField, "Could not click legal document link by visible text.");
      return;
    }

    waitForUi(targetPage);

    final boolean headingVisible = isVisibleTextInPageOrFrames(targetPage, headingRegex);
    final boolean contentVisible = hasLegalContent(targetPage);

    captureStepScreenshot(targetPage, reportField, screenshotName, true);
    putEvidence(reportField, "final_url", safeUrl(targetPage));

    if (headingVisible && contentVisible) {
      passStep(reportField, "Legal document validated successfully.");
    } else {
      failStep(reportField, "Legal document page did not expose expected heading/content.");
    }

    if (openedPopup) {
      targetPage.close();
      appPage.bringToFront();
      waitForUi(appPage);
      return;
    }

    // Same-tab navigation cleanup.
    if (!safeUrl(appPage).equals(appUrlBefore)) {
      try {
        appPage.goBack(new Page.GoBackOptions().setTimeout(SHORT_TIMEOUT_MS));
        waitForUi(appPage);
      } catch (PlaywrightException goBackException) {
        try {
          appPage.navigate(appUrlBefore, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
          waitForUi(appPage);
        } catch (PlaywrightException ignored) {
          // Best-effort cleanup only.
        }
      }
    }
  }

  private void tryFillBusinessName(final Page page, final String value) {
    try {
      final Locator labelLocator = page.locator("label:has-text(\"Nombre del Negocio\")").first();
      if (labelLocator.count() > 0) {
        final String forAttribute = labelLocator.getAttribute("for");
        if (forAttribute != null && !forAttribute.isBlank()) {
          final Locator inputByLabelFor = page.locator("#" + forAttribute).first();
          if (isVisible(inputByLabelFor, 2_000L)) {
            inputByLabelFor.click();
            waitForUi(page);
            inputByLabelFor.fill(value);
            waitForUi(page);
            return;
          }
        }
      }

      final Locator fallbackInput = page.locator("input[placeholder*='Nombre'], input").first();
      if (isVisible(fallbackInput, 2_000L)) {
        fallbackInput.click();
        waitForUi(page);
        fallbackInput.fill(value);
        waitForUi(page);
      }
    } catch (PlaywrightException ignored) {
      // Optional action; no hard failure if field interaction is unavailable.
    }
  }

  private boolean clickTextInPageOrFrames(final Page page, final String... regexes) {
    for (String regex : regexes) {
      if (clickByRegex(page.locator("text=/" + regex + "/i"), page)) {
        return true;
      }
      for (Frame frame : page.frames()) {
        if (clickByRegex(frame.locator("text=/" + regex + "/i"), page)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean clickByRegex(final Locator locator, final Page uiPage) {
    try {
      if (locator.count() == 0) {
        return false;
      }

      final Locator first = locator.first();
      first.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(2_000));
      first.click(new Locator.ClickOptions().setTimeout(SHORT_TIMEOUT_MS));
      waitForUi(uiPage);
      return true;
    } catch (PlaywrightException exception) {
      return false;
    }
  }

  private boolean isVisibleTextInPageOrFrames(final Page page, final String regex) {
    if (isVisible(page.locator("text=/" + regex + "/i").first(), 1_500L)) {
      return true;
    }

    final List<Frame> frames = page.frames();
    for (Frame frame : frames) {
      if (isVisible(frame.locator("text=/" + regex + "/i").first(), 1_500L)) {
        return true;
      }
    }
    return false;
  }

  private boolean isVisible(final Locator locator, final long timeoutMs) {
    try {
      if (locator.count() == 0) {
        return false;
      }
      locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout((double) timeoutMs));
      return true;
    } catch (PlaywrightException exception) {
      return false;
    }
  }

  private boolean hasLegalContent(final Page page) {
    try {
      final String bodyText = page.locator("body").innerText();
      return bodyText != null && bodyText.trim().length() > 120;
    } catch (PlaywrightException exception) {
      return false;
    }
  }

  private void waitForUi(final Page page) {
    try {
      page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(SHORT_TIMEOUT_MS));
    } catch (PlaywrightException ignored) {
      // Best-effort waiting only.
    }
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(2_000));
    } catch (PlaywrightException ignored) {
      // Best-effort waiting only.
    }
    page.waitForTimeout(CLICK_SETTLE_MS);
  }

  private void initializeReport() {
    report.clear();
    report.put(FIELD_LOGIN, StepResult.notExecuted(FIELD_LOGIN));
    report.put(FIELD_MENU, StepResult.notExecuted(FIELD_MENU));
    report.put(FIELD_MODAL, StepResult.notExecuted(FIELD_MODAL));
    report.put(FIELD_ADMIN_VIEW, StepResult.notExecuted(FIELD_ADMIN_VIEW));
    report.put(FIELD_INFO, StepResult.notExecuted(FIELD_INFO));
    report.put(FIELD_ACCOUNT_DETAILS, StepResult.notExecuted(FIELD_ACCOUNT_DETAILS));
    report.put(FIELD_BUSINESSES, StepResult.notExecuted(FIELD_BUSINESSES));
    report.put(FIELD_TERMS, StepResult.notExecuted(FIELD_TERMS));
    report.put(FIELD_PRIVACY, StepResult.notExecuted(FIELD_PRIVACY));
  }

  private void markRemainingAsPrerequisiteFailures(final String prerequisiteField) {
    boolean markNext = false;
    for (Map.Entry<String, StepResult> entry : report.entrySet()) {
      if (entry.getKey().equals(prerequisiteField)) {
        markNext = true;
        continue;
      }
      if (markNext && !entry.getValue().passed) {
        entry.getValue().details = "Prerequisite failed: " + prerequisiteField;
      }
    }
  }

  private void passStep(final String field, final String details) {
    final StepResult step = report.get(field);
    if (step != null) {
      step.passed = true;
      step.details = details;
    }
  }

  private void failStep(final String field, final String details) {
    final StepResult step = report.get(field);
    if (step != null) {
      step.passed = false;
      step.details = details;
    }
  }

  private void putEvidence(final String field, final String key, final String value) {
    final StepResult step = report.get(field);
    if (step != null && value != null) {
      step.evidence.put(key, value);
    }
  }

  private void captureStepScreenshot(final Page page, final String field, final String screenshotName, final boolean fullPage) {
    final Path screenshotPath = runDir.resolve(screenshotName);
    try {
      page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
      putEvidence(field, "screenshot", screenshotPath.toString());
    } catch (PlaywrightException exception) {
      putEvidence(field, "screenshot_error", exception.getMessage());
    }
  }

  private boolean allStepsPassed() {
    for (StepResult stepResult : report.values()) {
      if (!stepResult.passed) {
        return false;
      }
    }
    return true;
  }

  private void writeReportFiles() throws IOException {
    final Path markdownPath = runDir.resolve("report.md");
    final Path jsonPath = runDir.resolve("report.json");

    Files.writeString(markdownPath, renderMarkdownReport(), StandardCharsets.UTF_8);
    Files.writeString(jsonPath, renderJsonReport(), StandardCharsets.UTF_8);
  }

  private String renderMarkdownReport() {
    final StringBuilder builder = new StringBuilder();
    builder.append("# SaleADS Mi Negocio - Final Report\n\n");
    builder.append("Artifacts directory: `").append(runDir).append("`\n\n");

    for (Map.Entry<String, StepResult> entry : report.entrySet()) {
      final StepResult result = entry.getValue();
      builder.append("## ").append(entry.getKey()).append("\n");
      builder.append("- Status: **").append(result.passed ? "PASS" : "FAIL").append("**\n");
      builder.append("- Details: ").append(result.details == null ? "" : result.details).append("\n");
      if (!result.evidence.isEmpty()) {
        builder.append("- Evidence:\n");
        for (Map.Entry<String, String> evidenceEntry : result.evidence.entrySet()) {
          builder.append("  - ").append(evidenceEntry.getKey()).append(": `").append(evidenceEntry.getValue()).append("`\n");
        }
      }
      builder.append("\n");
    }

    return builder.toString();
  }

  private String renderJsonReport() {
    final StringBuilder builder = new StringBuilder();
    builder.append("{\n");
    builder.append("  \"artifactsDir\": \"").append(escapeJson(runDir.toString())).append("\",\n");
    builder.append("  \"results\": [\n");

    int index = 0;
    for (Map.Entry<String, StepResult> entry : report.entrySet()) {
      final StepResult result = entry.getValue();
      builder.append("    {\n");
      builder.append("      \"field\": \"").append(escapeJson(entry.getKey())).append("\",\n");
      builder.append("      \"status\": \"").append(result.passed ? "PASS" : "FAIL").append("\",\n");
      builder.append("      \"details\": \"").append(escapeJson(result.details == null ? "" : result.details)).append("\",\n");
      builder.append("      \"evidence\": {\n");
      int evIndex = 0;
      for (Map.Entry<String, String> evidenceEntry : result.evidence.entrySet()) {
        builder.append("        \"").append(escapeJson(evidenceEntry.getKey())).append("\": \"")
            .append(escapeJson(evidenceEntry.getValue())).append("\"");
        if (evIndex < result.evidence.size() - 1) {
          builder.append(",");
        }
        builder.append("\n");
        evIndex++;
      }
      builder.append("      }\n");
      builder.append("    }");
      if (index < report.size() - 1) {
        builder.append(",");
      }
      builder.append("\n");
      index++;
    }

    builder.append("  ]\n");
    builder.append("}\n");
    return builder.toString();
  }

  private String escapeJson(final String text) {
    return text.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r");
  }

  private Path createRunDir() throws IOException {
    final String stamp = ZonedDateTime.now(ZoneOffset.UTC).format(RUN_STAMP_FORMAT);
    final Path directory = Paths.get(System.getProperty("java.io.tmpdir"), "saleads-mi-negocio-" + stamp);
    Files.createDirectories(directory);
    return directory;
  }

  private String resolveLoginUrl() {
    final String propertyValue = System.getProperty("saleads.login.url");
    if (propertyValue != null && !propertyValue.isBlank()) {
      return propertyValue.trim();
    }
    final String envValue = System.getenv("SALEADS_LOGIN_URL");
    if (envValue != null && !envValue.isBlank()) {
      return envValue.trim();
    }
    return null;
  }

  private String safeUrl(final Page page) {
    try {
      return page.url();
    } catch (PlaywrightException exception) {
      return "";
    }
  }

  private static final class StepResult {
    private final String field;
    private boolean passed;
    private String details;
    private final LinkedHashMap<String, String> evidence;

    private StepResult(final String field) {
      this.field = field;
      this.passed = false;
      this.details = "Not executed.";
      this.evidence = new LinkedHashMap<>();
    }

    private static StepResult notExecuted(final String field) {
      return new StepResult(field);
    }
  }
}
