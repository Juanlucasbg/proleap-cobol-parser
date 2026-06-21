package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.Assume;
import org.junit.Test;

public class SaleadsMiNegocioFullTest {

  private static final String STEP_LOGIN = "Login";
  private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
  private static final String STEP_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
  private static final String STEP_ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
  private static final String STEP_INFORMACION_GENERAL = "Informaci\u00f3n General";
  private static final String STEP_DETALLES_CUENTA = "Detalles de la Cuenta";
  private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
  private static final String STEP_TERMINOS = "T\u00e9rminos y Condiciones";
  private static final String STEP_POLITICA = "Pol\u00edtica de Privacidad";

  private static final List<String> ORDERED_STEPS = Arrays.asList(
      STEP_LOGIN,
      STEP_MI_NEGOCIO_MENU,
      STEP_AGREGAR_NEGOCIO_MODAL,
      STEP_ADMINISTRAR_NEGOCIOS_VIEW,
      STEP_INFORMACION_GENERAL,
      STEP_DETALLES_CUENTA,
      STEP_TUS_NEGOCIOS,
      STEP_TERMINOS,
      STEP_POLITICA);

  private final Map<String, StepResult> results = new LinkedHashMap<>();

  private Path outputDir;
  private String administrarNegociosUrl;

  @Test
  public void saleadsMiNegocioFullWorkflow() throws IOException {
    Assume.assumeTrue(
        "Enable this E2E test with -Dsaleads.e2e.enabled=true or SALEADS_E2E_ENABLED=true.",
        isTruthy(config("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false")));

    initializeStepResults();
    initializeOutputDir();

    final String loginUrl = config("saleads.login.url", "SALEADS_LOGIN_URL", "").trim();
    final String googleAccountEmail = config(
        "saleads.google.account.email",
        "SALEADS_GOOGLE_ACCOUNT_EMAIL",
        "juanlucasbarbiergarzon@gmail.com").trim();
    final boolean headless = isTruthy(config("saleads.headless", "SALEADS_HEADLESS", "true"));

    try (Playwright playwright = Playwright.create();
        Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
        BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000))) {

      final Page page = context.newPage();

      executeLoginStep(page, loginUrl, googleAccountEmail);
      executeMiNegocioMenuStep(page);
      executeAgregarNegocioModalStep(page);
      executeAdministrarNegociosStep(page);
      executeInformacionGeneralStep(page);
      executeDetallesCuentaStep(page);
      executeTusNegociosStep(page);
      executeTerminosStep(page);
      executePoliticaStep(page);
    } catch (Exception exception) {
      appendDetail(STEP_LOGIN, "Unexpected execution error: " + exception.getMessage());
    }

    writeReportFiles();
    assertTrue(
        "One or more SaleADS Mi Negocio validations failed. See report: " + outputDir.resolve("report.md"),
        allStepsPassed());
  }

  private void executeLoginStep(final Page page, final String loginUrl, final String googleAccountEmail) {
    try {
      if (!loginUrl.isEmpty()) {
        page.navigate(loginUrl, new Page.NavigateOptions().setTimeout(45_000));
        waitForUi(page);
      } else {
        waitForUi(page);
        final String currentUrl = page.url();
        if (currentUrl == null || currentUrl.isEmpty() || "about:blank".equals(currentUrl)) {
          markFail(
              STEP_LOGIN,
              "No login URL configured and browser is not already positioned on a SaleADS login page.",
              page);
          return;
        }
      }

      captureAndAttachScreenshot(STEP_LOGIN, page, "step0_initial_login_page", false);

      final boolean clickedLoginButton = clickAny(page, Arrays.asList(
          "button:has-text('Sign in with Google')",
          "a:has-text('Sign in with Google')",
          "text=/Inicia sesi[o\\u00f3]n con Google/i",
          "button:has-text('Sign in')",
          "a:has-text('Sign in')",
          "text=/Inicia sesi[o\\u00f3]n/i",
          "text=/Sign in with Google|Inicia sesi[o\\u00f3]n con Google|Sign in|Inicia sesi[o\\u00f3]n/i"), true);

      if (!clickedLoginButton) {
        markFail(STEP_LOGIN, "Login button or Sign in with Google control was not found.", page);
        return;
      }

      clickGoogleInsideAnyFrame(page);

      maybeSelectGoogleAccount(page, googleAccountEmail);

      if (waitForDashboard(page, 30_000)) {
        markPass(STEP_LOGIN, "Main app interface and sidebar are visible after login.", page);
      } else {
        markFail(STEP_LOGIN, "Could not confirm dashboard/sidebar after Google login flow.", page);
      }
    } catch (Exception exception) {
      markFail(STEP_LOGIN, "Login step failed: " + exception.getMessage(), page);
    }
  }

  private void executeMiNegocioMenuStep(final Page page) {
    if (!isPassed(STEP_LOGIN)) {
      markPrerequisiteFailed(STEP_MI_NEGOCIO_MENU, STEP_LOGIN);
      return;
    }

    final boolean clickedNegocio = clickAny(page, Arrays.asList(
        "button:has-text('Negocio')",
        "a:has-text('Negocio')",
        "text=/^Negocio$/i"), true);

    final boolean clickedMiNegocio = clickAny(page, Arrays.asList(
        "button:has-text('Mi Negocio')",
        "a:has-text('Mi Negocio')",
        "text=/Mi Negocio/i"), true);

    final boolean agregarVisible = isTextVisible(page, "Agregar Negocio");
    final boolean administrarVisible = isTextVisible(page, "Administrar Negocios");

    if (!clickedNegocio && !clickedMiNegocio) {
      markFail(STEP_MI_NEGOCIO_MENU, "Unable to open Negocio / Mi Negocio navigation item.", page);
      return;
    }

    if (agregarVisible && administrarVisible) {
      markPass(
          STEP_MI_NEGOCIO_MENU,
          "Mi Negocio submenu expanded with Agregar Negocio and Administrar Negocios.",
          page);
    } else {
      markFail(
          STEP_MI_NEGOCIO_MENU,
          "Mi Negocio submenu did not expose all required options.",
          page);
    }
  }

  private void executeAgregarNegocioModalStep(final Page page) {
    if (!isPassed(STEP_MI_NEGOCIO_MENU)) {
      markPrerequisiteFailed(STEP_AGREGAR_NEGOCIO_MODAL, STEP_MI_NEGOCIO_MENU);
      return;
    }

    if (!clickAny(page, Arrays.asList(
        "button:has-text('Agregar Negocio')",
        "a:has-text('Agregar Negocio')",
        "text=/Agregar Negocio/i"), true)) {
      markFail(STEP_AGREGAR_NEGOCIO_MODAL, "Could not click Agregar Negocio.", page);
      return;
    }

    final boolean titleVisible = waitForText(page, "Crear Nuevo Negocio", 10_000);
    final boolean inputVisible = isTextVisible(page, "Nombre del Negocio")
        || isVisible(page.locator("input[placeholder*='Nombre del Negocio'], input[name*='nombre']"));
    final boolean quotaVisible = isTextVisible(page, "Tienes 2 de 3 negocios");
    final boolean cancelVisible = isTextVisible(page, "Cancelar");
    final boolean createVisible = isTextVisible(page, "Crear Negocio");

    if (titleVisible && inputVisible && quotaVisible && cancelVisible && createVisible) {
      final Locator businessNameInput = page.locator("input[placeholder*='Nombre del Negocio'], input[name*='nombre']");
      try {
        if (isVisible(businessNameInput)) {
          businessNameInput.first().click();
          businessNameInput.first().fill("Negocio Prueba Automatizacion");
          waitForUi(page);
        }
      } catch (Exception ignored) {
        // Optional action only.
      }

      clickAny(page, Arrays.asList(
          "button:has-text('Cancelar')",
          "text=/Cancelar/i"), true);
      markPass(STEP_AGREGAR_NEGOCIO_MODAL, "Agregar Negocio modal validated.", page);
    } else {
      markFail(STEP_AGREGAR_NEGOCIO_MODAL, "Agregar Negocio modal content is incomplete.", page);
    }
  }

  private void executeAdministrarNegociosStep(final Page page) {
    if (!isPassed(STEP_MI_NEGOCIO_MENU)) {
      markPrerequisiteFailed(STEP_ADMINISTRAR_NEGOCIOS_VIEW, STEP_MI_NEGOCIO_MENU);
      return;
    }

    clickAny(page, Arrays.asList("text=/Mi Negocio/i"), true);

    if (!clickAny(page, Arrays.asList(
        "button:has-text('Administrar Negocios')",
        "a:has-text('Administrar Negocios')",
        "text=/Administrar Negocios/i"), true)) {
      markFail(STEP_ADMINISTRAR_NEGOCIOS_VIEW, "Could not click Administrar Negocios.", page);
      return;
    }

    waitForUi(page);
    administrarNegociosUrl = page.url();

    final boolean infoGeneralVisible = waitForText(page, "Informaci[o\\u00f3]n General", 15_000);
    final boolean detallesCuentaVisible = isTextVisible(page, "Detalles de la Cuenta");
    final boolean tusNegociosVisible = isTextVisible(page, "Tus Negocios");
    final boolean legalSectionVisible = isTextVisible(page, "Secci[o\\u00f3]n Legal");

    if (infoGeneralVisible && detallesCuentaVisible && tusNegociosVisible && legalSectionVisible) {
      markPass(STEP_ADMINISTRAR_NEGOCIOS_VIEW, "Administrar Negocios sections are visible.", page, true);
    } else {
      markFail(STEP_ADMINISTRAR_NEGOCIOS_VIEW, "Administrar Negocios missing one or more required sections.", page);
    }
  }

  private void executeInformacionGeneralStep(final Page page) {
    if (!isPassed(STEP_ADMINISTRAR_NEGOCIOS_VIEW)) {
      markPrerequisiteFailed(STEP_INFORMACION_GENERAL, STEP_ADMINISTRAR_NEGOCIOS_VIEW);
      return;
    }

    final boolean userNameVisible = isVisible(page.locator("[data-testid*='name'], [class*='name']"))
        || isTextVisible(page, "Bienvenido|Hola");
    final boolean userEmailVisible = isVisible(page.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/"));
    final boolean businessPlanVisible = isTextVisible(page, "BUSINESS PLAN");
    final boolean cambiarPlanVisible = isTextVisible(page, "Cambiar Plan");

    if (userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible) {
      markPass(STEP_INFORMACION_GENERAL, "Informacion General section validated.", page);
    } else {
      markFail(STEP_INFORMACION_GENERAL, "Informacion General is missing required fields.", page);
    }
  }

  private void executeDetallesCuentaStep(final Page page) {
    if (!isPassed(STEP_ADMINISTRAR_NEGOCIOS_VIEW)) {
      markPrerequisiteFailed(STEP_DETALLES_CUENTA, STEP_ADMINISTRAR_NEGOCIOS_VIEW);
      return;
    }

    final boolean cuentaCreadaVisible = isTextVisible(page, "Cuenta creada");
    final boolean estadoActivoVisible = isTextVisible(page, "Estado activo");
    final boolean idiomaSeleccionadoVisible = isTextVisible(page, "Idioma seleccionado");

    if (cuentaCreadaVisible && estadoActivoVisible && idiomaSeleccionadoVisible) {
      markPass(STEP_DETALLES_CUENTA, "Detalles de la Cuenta section validated.", page);
    } else {
      markFail(STEP_DETALLES_CUENTA, "Detalles de la Cuenta is missing required fields.", page);
    }
  }

  private void executeTusNegociosStep(final Page page) {
    if (!isPassed(STEP_ADMINISTRAR_NEGOCIOS_VIEW)) {
      markPrerequisiteFailed(STEP_TUS_NEGOCIOS, STEP_ADMINISTRAR_NEGOCIOS_VIEW);
      return;
    }

    final boolean listVisible = isVisible(page.locator("text=/Tus Negocios/i").first())
        || isVisible(page.locator("[data-testid*='business'], [class*='business']"));
    final boolean agregarButtonVisible = isTextVisible(page, "Agregar Negocio");
    final boolean quotaVisible = isTextVisible(page, "Tienes 2 de 3 negocios");

    if (listVisible && agregarButtonVisible && quotaVisible) {
      markPass(STEP_TUS_NEGOCIOS, "Tus Negocios section validated.", page);
    } else {
      markFail(STEP_TUS_NEGOCIOS, "Tus Negocios is missing required information.", page);
    }
  }

  private void executeTerminosStep(final Page page) {
    if (!isPassed(STEP_ADMINISTRAR_NEGOCIOS_VIEW)) {
      markPrerequisiteFailed(STEP_TERMINOS, STEP_ADMINISTRAR_NEGOCIOS_VIEW);
      return;
    }

    executeLegalLinkValidation(
        page,
        STEP_TERMINOS,
        Arrays.asList(
            "a:has-text('T\\u00e9rminos y Condiciones')",
            "button:has-text('T\\u00e9rminos y Condiciones')",
            "text=/T[e\\u00e9]rminos y Condiciones/i"),
        "T[e\\u00e9]rminos y Condiciones");
  }

  private void executePoliticaStep(final Page page) {
    if (!isPassed(STEP_ADMINISTRAR_NEGOCIOS_VIEW)) {
      markPrerequisiteFailed(STEP_POLITICA, STEP_ADMINISTRAR_NEGOCIOS_VIEW);
      return;
    }

    executeLegalLinkValidation(
        page,
        STEP_POLITICA,
        Arrays.asList(
            "a:has-text('Pol\\u00edtica de Privacidad')",
            "button:has-text('Pol\\u00edtica de Privacidad')",
            "text=/Pol[i\\u00ed]tica de Privacidad/i"),
        "Pol[i\\u00ed]tica de Privacidad");
  }

  private void executeLegalLinkValidation(
      final Page appPage,
      final String stepName,
      final List<String> linkSelectors,
      final String expectedHeadingRegex) {
    try {
      Page targetPage = null;
      boolean openedInNewTab = false;

      try {
        targetPage = appPage.context().waitForPage(
            new BrowserContext.WaitForPageOptions().setTimeout(8_000),
            () -> {
              clickAny(appPage, linkSelectors, false);
            });
        openedInNewTab = targetPage != null;
      } catch (TimeoutError ignored) {
        clickAny(appPage, linkSelectors, true);
        targetPage = appPage;
      }

      waitForUi(targetPage);
      final boolean headingVisible = waitForText(targetPage, expectedHeadingRegex, 12_000);
      final boolean legalTextVisible = hasLongText(targetPage, 120);
      final String finalUrl = targetPage.url();

      if (headingVisible && legalTextVisible) {
        markPass(stepName, "Legal page validated. Final URL: " + finalUrl, targetPage);
      } else {
        markFail(stepName, "Legal page content could not be fully validated. Final URL: " + finalUrl, targetPage);
      }

      results.get(stepName).url = finalUrl;

      if (openedInNewTab) {
        targetPage.close();
        appPage.bringToFront();
      } else {
        try {
          appPage.goBack(new Page.GoBackOptions().setTimeout(8_000));
          waitForUi(appPage);
        } catch (Exception ignored) {
          if (administrarNegociosUrl != null && !administrarNegociosUrl.isEmpty()) {
            appPage.navigate(administrarNegociosUrl);
            waitForUi(appPage);
          }
        }
      }
    } catch (Exception exception) {
      markFail(stepName, "Legal link validation failed: " + exception.getMessage(), appPage);
    }
  }

  private void maybeSelectGoogleAccount(final Page page, final String googleAccountEmail) {
    if (!page.url().contains("accounts.google.com")) {
      return;
    }

    final boolean clickedKnownAccount = clickAny(page, Arrays.asList(
        "text=" + googleAccountEmail,
        "div:has-text('" + googleAccountEmail + "')"), true);

    if (clickedKnownAccount) {
      return;
    }

    final Locator emailInput = page.locator("input[type='email']");
    if (isVisible(emailInput)) {
      emailInput.first().fill(googleAccountEmail);
      clickAny(page, Arrays.asList(
          "button:has-text('Next')",
          "button:has-text('Siguiente')",
          "text=/Next|Siguiente/i"), true);
    }
  }

  private void clickGoogleInsideAnyFrame(final Page page) {
    if (clickAny(page, Arrays.asList(
        "button:has-text('GOOGLE')",
        "text=/^GOOGLE$/i"), true)) {
      return;
    }

    page.frames().forEach(frame -> {
      try {
        final Locator googleButton = frame.locator("button:has-text('GOOGLE'), text=/^GOOGLE$/i");
        if (isVisible(googleButton)) {
          googleButton.first().click();
          waitForUi(page);
        }
      } catch (Exception ignored) {
        // Continue searching frames.
      }
    });
  }

  private boolean waitForDashboard(final Page page, final int timeoutMs) {
    final long startedAt = System.currentTimeMillis();
    while (System.currentTimeMillis() - startedAt < timeoutMs) {
      if (isSidebarVisible(page)) {
        return true;
      }
      page.waitForTimeout(1_000);
    }
    return false;
  }

  private boolean isSidebarVisible(final Page page) {
    return isVisible(page.locator("aside, nav[aria-label*='sidebar'], [class*='sidebar']"))
        && (isTextVisible(page, "Mi Negocio") || isTextVisible(page, "Negocio"));
  }

  private boolean waitForText(final Page page, final String regex, final int timeoutMs) {
    final long startedAt = System.currentTimeMillis();
    while (System.currentTimeMillis() - startedAt < timeoutMs) {
      if (isTextVisible(page, regex)) {
        return true;
      }
      page.waitForTimeout(500);
    }
    return false;
  }

  private boolean isTextVisible(final Page page, final String regex) {
    return isVisible(page.locator("text=/" + regex + "/i"));
  }

  private boolean hasLongText(final Page page, final int minLength) {
    try {
      final String text = page.locator("main, article, body").first().innerText();
      return text != null && text.trim().length() >= minLength;
    } catch (Exception ignored) {
      return false;
    }
  }

  private boolean clickAny(final Page page, final List<String> selectors, final boolean waitAfterClick) {
    for (String selector : selectors) {
      final Locator locator = page.locator(selector);
      if (clickLocator(locator, waitAfterClick ? page : null)) {
        return true;
      }
    }
    return false;
  }

  private boolean clickLocator(final Locator locator, final Page pageToWait) {
    try {
      final int count = Math.min(locator.count(), 4);
      for (int index = 0; index < count; index++) {
        final Locator candidate = locator.nth(index);
        if (candidate.isVisible(new Locator.IsVisibleOptions().setTimeout(1_000))) {
          candidate.click(new Locator.ClickOptions().setTimeout(8_000));
          if (pageToWait != null) {
            waitForUi(pageToWait);
          }
          return true;
        }
      }
    } catch (Exception ignored) {
      // Try next selector.
    }
    return false;
  }

  private boolean isVisible(final Locator locator) {
    try {
      final int count = Math.min(locator.count(), 4);
      for (int index = 0; index < count; index++) {
        if (locator.nth(index).isVisible(new Locator.IsVisibleOptions().setTimeout(1_000))) {
          return true;
        }
      }
    } catch (Exception ignored) {
      return false;
    }
    return false;
  }

  private void waitForUi(final Page page) {
    if (page == null) {
      return;
    }

    try {
      page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(10_000));
    } catch (Exception ignored) {
      // Best effort.
    }

    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(6_000));
    } catch (Exception ignored) {
      // Best effort.
    }

    page.waitForTimeout(500);
  }

  private void initializeStepResults() {
    ORDERED_STEPS.forEach(step -> {
      final StepResult result = new StepResult();
      result.status = "FAIL";
      result.details = "Not executed.";
      results.put(step, result);
    });
  }

  private void initializeOutputDir() throws IOException {
    final String runId = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZone(ZoneOffset.UTC)
        .format(Instant.now());
    outputDir = Paths.get("target", "saleads-mi-negocio", runId);
    Files.createDirectories(outputDir);
  }

  private void writeReportFiles() throws IOException {
    Files.writeString(outputDir.resolve("report.json"), buildJsonReport(), StandardCharsets.UTF_8);
    Files.writeString(outputDir.resolve("report.md"), buildMarkdownReport(), StandardCharsets.UTF_8);
  }

  private String buildJsonReport() {
    final StringBuilder builder = new StringBuilder();
    builder.append("{\n");
    builder.append("  \"name\": \"saleads_mi_negocio_full_test\",\n");
    builder.append("  \"generatedAt\": \"").append(Instant.now()).append("\",\n");
    builder.append("  \"results\": {\n");

    for (int index = 0; index < ORDERED_STEPS.size(); index++) {
      final String step = ORDERED_STEPS.get(index);
      final StepResult result = results.get(step);

      builder.append("    \"").append(jsonEscape(step)).append("\": {\n");
      builder.append("      \"status\": \"").append(jsonEscape(result.status)).append("\",\n");
      builder.append("      \"details\": \"").append(jsonEscape(result.details)).append("\",\n");
      builder.append("      \"url\": \"").append(jsonEscape(result.url)).append("\",\n");
      builder.append("      \"screenshots\": [");
      for (int screenshotIndex = 0; screenshotIndex < result.screenshots.size(); screenshotIndex++) {
        if (screenshotIndex > 0) {
          builder.append(", ");
        }
        builder.append("\"").append(jsonEscape(result.screenshots.get(screenshotIndex))).append("\"");
      }
      builder.append("]\n");
      builder.append("    }");
      if (index < ORDERED_STEPS.size() - 1) {
        builder.append(",");
      }
      builder.append("\n");
    }

    builder.append("  }\n");
    builder.append("}\n");
    return builder.toString();
  }

  private String buildMarkdownReport() {
    final StringBuilder builder = new StringBuilder();
    builder.append("# SaleADS Mi Negocio Full Test Report\n\n");
    builder.append("- Generated: ").append(Instant.now()).append('\n');
    builder.append("- Artifacts directory: ").append(outputDir).append("\n\n");
    builder.append("| Step | Status | Details | URL |\n");
    builder.append("| --- | --- | --- | --- |\n");

    ORDERED_STEPS.forEach(step -> {
      final StepResult result = results.get(step);
      builder.append("| ")
          .append(step)
          .append(" | ")
          .append(result.status)
          .append(" | ")
          .append(escapeMd(result.details))
          .append(" | ")
          .append(escapeMd(result.url))
          .append(" |\n");
    });

    return builder.toString();
  }

  private String escapeMd(final String value) {
    if (value == null) {
      return "";
    }
    return value.replace("|", "\\|").replace("\n", "<br>");
  }

  private String jsonEscape(final String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r");
  }

  private void markPass(final String stepName, final String details, final Page page) {
    markPass(stepName, details, page, false);
  }

  private void markPass(final String stepName, final String details, final Page page, final boolean fullPage) {
    final StepResult result = Objects.requireNonNull(results.get(stepName));
    result.status = "PASS";
    result.details = details;
    result.url = page != null ? page.url() : "";
    captureAndAttachScreenshot(stepName, page, fileSlug(stepName), fullPage);
  }

  private void markFail(final String stepName, final String details, final Page page) {
    final StepResult result = Objects.requireNonNull(results.get(stepName));
    result.status = "FAIL";
    result.details = details;
    result.url = page != null ? page.url() : "";
    captureAndAttachScreenshot(stepName, page, fileSlug(stepName) + "_failure", false);
  }

  private void appendDetail(final String stepName, final String detail) {
    final StepResult result = Objects.requireNonNull(results.get(stepName));
    if (result.details == null || result.details.isEmpty() || "Not executed.".equals(result.details)) {
      result.details = detail;
    } else {
      result.details = result.details + " " + detail;
    }
  }

  private void markPrerequisiteFailed(final String stepName, final String prerequisiteStep) {
    markFail(stepName, "Prerequisite failed: " + prerequisiteStep + ".", null);
  }

  private void captureAndAttachScreenshot(
      final String stepName,
      final Page page,
      final String fileBaseName,
      final boolean fullPage) {
    if (page == null) {
      return;
    }

    final Path screenshotPath = outputDir.resolve(fileBaseName + ".png");
    try {
      page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
      results.get(stepName).screenshots.add(screenshotPath.toString());
    } catch (Exception ignored) {
      // Screenshot capture failure should not crash the run.
    }
  }

  private String fileSlug(final String value) {
    return value
        .toLowerCase()
        .replace(" ", "_")
        .replace("\u00f3", "o")
        .replace("\u00e9", "e")
        .replace("\u00ed", "i");
  }

  private boolean allStepsPassed() {
    for (String step : ORDERED_STEPS) {
      if (!isPassed(step)) {
        return false;
      }
    }
    return true;
  }

  private boolean isPassed(final String stepName) {
    final StepResult result = results.get(stepName);
    return result != null && "PASS".equals(result.status);
  }

  private String config(final String propertyName, final String envName, final String defaultValue) {
    final String propertyValue = System.getProperty(propertyName);
    if (propertyValue != null && !propertyValue.trim().isEmpty()) {
      return propertyValue;
    }

    final String envValue = System.getenv(envName);
    if (envValue != null && !envValue.trim().isEmpty()) {
      return envValue;
    }

    return defaultValue;
  }

  private boolean isTruthy(final String value) {
    if (value == null) {
      return false;
    }
    final String normalized = value.trim().toLowerCase();
    return "true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized);
  }

  private static final class StepResult {
    private String status;
    private String details;
    private String url = "";
    private final List<String> screenshots = new ArrayList<>();
  }
}
