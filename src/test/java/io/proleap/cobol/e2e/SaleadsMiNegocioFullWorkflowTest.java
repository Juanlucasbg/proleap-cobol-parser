package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Assume;
import org.junit.Test;

public class SaleadsMiNegocioFullWorkflowTest {

  private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
  private static final double DEFAULT_TIMEOUT_MS = 15000;
  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

  @Test
  public void saleadsMiNegocioFullTest() throws IOException {
    Assume.assumeTrue(
        "Set SALEADS_RUN_E2E=true to execute this browser test.",
        Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_RUN_E2E", "false")));

    final String baseUrl = trimToNull(System.getenv("SALEADS_BASE_URL"));
    final boolean headless =
        Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));

    final String runId = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
    final Path evidenceDir = Paths.get("target", "saleads-evidence", runId);
    Files.createDirectories(evidenceDir);

    final LinkedHashMap<String, StepResult> report = new LinkedHashMap<>();

    try (Playwright playwright = Playwright.create()) {
      Browser browser =
          playwright
              .chromium()
              .launch(new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo(150));
      BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
      Page page = context.newPage();

      if (baseUrl != null) {
        page.navigate(baseUrl);
        waitForUi(page);
      }

      boolean login =
          runStep(
              report,
              "Login",
              true,
              () -> {
                stepLoginWithGoogle(page, context, baseUrl, evidenceDir);
                return "Dashboard and sidebar visible";
              });

      boolean menu =
          runStep(
              report,
              "Mi Negocio menu",
              login,
              () -> {
                stepOpenMiNegocioMenu(page, evidenceDir);
                return "Mi Negocio expanded with expected submenu";
              });

      boolean agregarNegocioModal =
          runStep(
              report,
              "Agregar Negocio modal",
              menu,
              () -> {
                stepValidateAgregarNegocioModal(page, evidenceDir);
                return "Modal fields and actions validated";
              });

      boolean administrarView =
          runStep(
              report,
              "Administrar Negocios view",
              menu,
              () -> {
                stepOpenAdministrarNegocios(page, evidenceDir);
                return "Account sections loaded";
              });

      boolean informacionGeneral =
          runStep(
              report,
              "Informaci\u00f3n General",
              administrarView,
              () -> {
                stepValidateInformacionGeneral(page);
                return "User identity, plan and CTA visible";
              });

      boolean detallesCuenta =
          runStep(
              report,
              "Detalles de la Cuenta",
              administrarView,
              () -> {
                stepValidateDetallesCuenta(page);
                return "Account details labels visible";
              });

      boolean tusNegocios =
          runStep(
              report,
              "Tus Negocios",
              administrarView,
              () -> {
                stepValidateTusNegocios(page);
                return "Business listing and quota validated";
              });

      boolean terminosCondiciones =
          runStep(
              report,
              "T\u00e9rminos y Condiciones",
              administrarView,
              () -> stepValidateLegalLink(page, "T\u00e9rminos y Condiciones", evidenceDir, "terminos_condiciones"));

      runStep(
          report,
          "Pol\u00edtica de Privacidad",
          administrarView,
          () -> stepValidateLegalLink(page, "Pol\u00edtica de Privacidad", evidenceDir, "politica_privacidad"));

      if (informacionGeneral && detallesCuenta && tusNegocios) {
        captureScreenshot(page, evidenceDir.resolve("final_account_state.png"), true);
      }
    }

    printFinalReport(report);
    assertTrue(buildFailureMessage(report), report.values().stream().allMatch(result -> result.pass));
  }

  private void stepLoginWithGoogle(
      final Page page, final BrowserContext context, final String baseUrl, final Path evidenceDir) {
    if (baseUrl == null && "about:blank".equals(page.url())) {
      throw new IllegalStateException(
          "No login page detected. Set SALEADS_BASE_URL for this environment before running.");
    }

    Locator loginTrigger =
        findVisibleLocator(
            page,
            DEFAULT_TIMEOUT_MS,
            "button:has-text('Google')",
            "a:has-text('Google')",
            "text='Sign in with Google'",
            "text='Continue with Google'",
            "text='Iniciar sesion con Google'",
            "text='Iniciar sesi\u00f3n con Google'",
            "text='Continuar con Google'");

    Page authPage = clickExpectingOptionalPopup(page, context, loginTrigger);
    waitForUi(authPage);

    Locator specificAccount = authPage.locator("text='" + GOOGLE_ACCOUNT_EMAIL + "'").first();
    if (isVisible(specificAccount, 5000)) {
      clickAndWait(specificAccount, authPage);
    }

    if (authPage != page) {
      try {
        authPage.waitForClose(new Page.WaitForCloseOptions().setTimeout(20000));
      } catch (PlaywrightException ignored) {
        // Google auth can keep the popup open; continue using the main tab.
      }
      page.bringToFront();
    }

    waitForUi(page);

    findVisibleLocator(
        page,
        30000,
        "aside",
        "nav",
        "text='Negocio'",
        "text='Mi Negocio'",
        "text='Dashboard'");
    findVisibleLocator(page, 30000, "aside", "text='Negocio'", "text='Mi Negocio'");
    captureScreenshot(page, evidenceDir.resolve("step1_dashboard_loaded.png"), true);
  }

  private void stepOpenMiNegocioMenu(final Page page, final Path evidenceDir) {
    ensureMiNegocioExpanded(page);
    findVisibleLocator(page, DEFAULT_TIMEOUT_MS, "text='Agregar Negocio'");
    findVisibleLocator(page, DEFAULT_TIMEOUT_MS, "text='Administrar Negocios'");
    captureScreenshot(page, evidenceDir.resolve("step2_mi_negocio_expanded.png"), true);
  }

  private void stepValidateAgregarNegocioModal(final Page page, final Path evidenceDir) {
    Locator agregarNegocio = findVisibleLocator(page, DEFAULT_TIMEOUT_MS, "text='Agregar Negocio'");
    clickAndWait(agregarNegocio, page);

    Locator modalTitle = findVisibleLocator(page, DEFAULT_TIMEOUT_MS, "text='Crear Nuevo Negocio'");
    Locator modalContainer =
        page.locator("[role='dialog'], .modal, .ant-modal").filter(new Locator.FilterOptions().setHasText("Crear Nuevo Negocio")).first();
    if (!isVisible(modalContainer, 2000)) {
      modalContainer = modalTitle.locator("xpath=ancestor::*[self::*[@role='dialog'] or contains(@class,'modal')][1]");
    }

    waitUntilVisible(modalTitle, DEFAULT_TIMEOUT_MS);
    findVisibleLocator(page, DEFAULT_TIMEOUT_MS, "text='Nombre del Negocio'");
    findVisibleLocator(page, DEFAULT_TIMEOUT_MS, "text='Tienes 2 de 3 negocios'");
    findVisibleLocator(page, DEFAULT_TIMEOUT_MS, "text='Cancelar'");
    findVisibleLocator(page, DEFAULT_TIMEOUT_MS, "text='Crear Negocio'");

    Locator inputField = modalContainer.locator("input").first();
    if (!isVisible(inputField, 3000)) {
      inputField = page.locator("input[placeholder*='Nombre'], input[name*='nombre'], input[id*='nombre']").first();
    }
    waitUntilVisible(inputField, DEFAULT_TIMEOUT_MS);

    captureScreenshot(page, evidenceDir.resolve("step3_agregar_negocio_modal.png"), true);

    inputField.click();
    inputField.fill("Negocio Prueba Automatizacion");
    Locator cancelarButton = findVisibleLocator(page, DEFAULT_TIMEOUT_MS, "button:has-text('Cancelar')", "text='Cancelar'");
    clickAndWait(cancelarButton, page);
    if (isVisible(modalTitle, 2000)) {
      throw new IllegalStateException("Agregar Negocio modal did not close after Cancelar.");
    }
  }

  private void stepOpenAdministrarNegocios(final Page page, final Path evidenceDir) {
    ensureMiNegocioExpanded(page);

    Locator administrarNegocios =
        findVisibleLocator(
            page,
            DEFAULT_TIMEOUT_MS,
            "text='Administrar Negocios'",
            "a:has-text('Administrar Negocios')",
            "button:has-text('Administrar Negocios')");
    clickAndWait(administrarNegocios, page);

    findVisibleLocator(page, DEFAULT_TIMEOUT_MS, "text='Informacion General'", "text='Informaci\u00f3n General'");
    findVisibleLocator(page, DEFAULT_TIMEOUT_MS, "text='Detalles de la Cuenta'");
    findVisibleLocator(page, DEFAULT_TIMEOUT_MS, "text='Tus Negocios'");
    findVisibleLocator(page, DEFAULT_TIMEOUT_MS, "text='Seccion Legal'", "text='Secci\u00f3n Legal'");
    captureScreenshot(page, evidenceDir.resolve("step4_administrar_negocios.png"), true);
  }

  private void stepValidateInformacionGeneral(final Page page) {
    Locator infoSection = findSectionContainer(page, "Informacion General", "Informaci\u00f3n General");
    String sectionText = safeText(infoSection);
    Matcher matcher = EMAIL_PATTERN.matcher(sectionText);
    if (!matcher.find()) {
      throw new IllegalStateException("No user email detected in Informacion General section.");
    }

    String emailFound = matcher.group();
    String remainingText = sectionText.replace(emailFound, "");
    remainingText =
        remainingText
            .replace("Informacion General", "")
            .replace("Informaci\u00f3n General", "")
            .replace("BUSINESS PLAN", "")
            .replace("Cambiar Plan", "")
            .trim();
    if (remainingText.length() < 3) {
      throw new IllegalStateException("No user name or profile value detected in Informacion General section.");
    }

    findVisibleLocator(page, DEFAULT_TIMEOUT_MS, "text='BUSINESS PLAN'");
    findVisibleLocator(page, DEFAULT_TIMEOUT_MS, "button:has-text('Cambiar Plan')", "text='Cambiar Plan'");
  }

  private void stepValidateDetallesCuenta(final Page page) {
    findVisibleLocator(page, DEFAULT_TIMEOUT_MS, "text='Cuenta creada'");
    findVisibleLocator(page, DEFAULT_TIMEOUT_MS, "text='Estado activo'");
    findVisibleLocator(page, DEFAULT_TIMEOUT_MS, "text='Idioma seleccionado'");
  }

  private void stepValidateTusNegocios(final Page page) {
    Locator businessSection = findSectionContainer(page, "Tus Negocios");
    String sectionText = safeText(businessSection);
    if (sectionText.length() < 20) {
      throw new IllegalStateException("Tus Negocios section looks empty.");
    }

    findVisibleLocator(page, DEFAULT_TIMEOUT_MS, "button:has-text('Agregar Negocio')", "text='Agregar Negocio'");
    findVisibleLocator(page, DEFAULT_TIMEOUT_MS, "text='Tienes 2 de 3 negocios'");
  }

  private String stepValidateLegalLink(
      final Page page, final String linkText, final Path evidenceDir, final String screenshotPrefix) {
    String appUrlBefore = page.url();
    Locator legalLink =
        findVisibleLocator(
            page,
            DEFAULT_TIMEOUT_MS,
            "a:has-text('" + linkText + "')",
            "button:has-text('" + linkText + "')",
            "text='" + linkText + "'");

    Page legalPage = clickExpectingOptionalPopup(page, page.context(), legalLink);
    waitForUi(legalPage);

    findVisibleLocator(
        legalPage,
        DEFAULT_TIMEOUT_MS,
        "h1:has-text('" + linkText + "')",
        "h2:has-text('" + linkText + "')",
        "text='" + linkText + "'");

    String legalContent = safeText(legalPage.locator("main, article, body").first());
    if (legalContent.length() < 80) {
      throw new IllegalStateException("Legal page content appears empty for " + linkText + ".");
    }

    captureScreenshot(legalPage, evidenceDir.resolve("step_" + screenshotPrefix + ".png"), true);
    String finalUrl = legalPage.url();

    if (legalPage != page) {
      legalPage.close();
      page.bringToFront();
      waitForUi(page);
    } else if (!appUrlBefore.equals(finalUrl)) {
      page.navigate(appUrlBefore);
      waitForUi(page);
    }

    return "Validated at URL: " + finalUrl;
  }

  private void ensureMiNegocioExpanded(final Page page) {
    if (isVisible(page.locator("text='Agregar Negocio'").first(), 1200)
        && isVisible(page.locator("text='Administrar Negocios'").first(), 1200)) {
      return;
    }

    Locator negocio = findVisibleLocator(page, DEFAULT_TIMEOUT_MS, "text='Negocio'");
    clickAndWait(negocio, page);

    Locator miNegocio =
        findVisibleLocator(
            page,
            DEFAULT_TIMEOUT_MS,
            "text='Mi Negocio'",
            "a:has-text('Mi Negocio')",
            "button:has-text('Mi Negocio')");
    clickAndWait(miNegocio, page);
  }

  private Page clickExpectingOptionalPopup(
      final Page currentPage, final BrowserContext context, final Locator locator) {
    try {
      Page popup =
          context.waitForPage(
              () -> locator.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS)),
              new BrowserContext.WaitForPageOptions().setTimeout(7000));
      waitForUi(popup);
      return popup;
    } catch (PlaywrightException ignored) {
      waitForUi(currentPage);
      return currentPage;
    }
  }

  private boolean runStep(
      final Map<String, StepResult> report,
      final String stepName,
      final boolean prerequisite,
      final CheckedSupplier<String> stepAction) {
    if (!prerequisite) {
      report.put(stepName, StepResult.fail("Prerequisite step failed"));
      return false;
    }

    try {
      String details = stepAction.get();
      report.put(stepName, StepResult.pass(details == null ? "OK" : details));
      return true;
    } catch (Exception exception) {
      report.put(stepName, StepResult.fail(exception.getMessage()));
      return false;
    }
  }

  private Locator findSectionContainer(final Page page, final String... headings) {
    for (String heading : headings) {
      Locator headingLocator = page.locator("text='" + heading + "'").first();
      if (!isVisible(headingLocator, 2000)) {
        continue;
      }
      Locator section =
          headingLocator.locator("xpath=ancestor::section[1] | ancestor::div[1] | ancestor::article[1]").first();
      if (isVisible(section, 1000)) {
        return section;
      }
    }
    throw new IllegalStateException("Could not locate section for headings: " + String.join(", ", headings));
  }

  private Locator findVisibleLocator(final Page page, final double timeoutMs, final String... selectors) {
    long deadline = System.currentTimeMillis() + (long) timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      for (String selector : selectors) {
        Locator locator = page.locator(selector).first();
        if (isVisible(locator, 800)) {
          return locator;
        }
      }
      page.waitForTimeout(200);
    }
    throw new IllegalStateException("Visible element not found for selectors: " + String.join(" | ", selectors));
  }

  private boolean isVisible(final Locator locator, final double timeoutMs) {
    try {
      waitUntilVisible(locator, timeoutMs);
      return true;
    } catch (PlaywrightException exception) {
      return false;
    }
  }

  private void waitUntilVisible(final Locator locator, final double timeoutMs) {
    locator.waitFor(
        new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(timeoutMs));
  }

  private void clickAndWait(final Locator locator, final Page page) {
    locator.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
    waitForUi(page);
  }

  private void waitForUi(final Page page) {
    page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10000));
    } catch (PlaywrightException ignored) {
      // Some SPAs keep long-lived connections open; DOM ready is enough fallback.
    }
    page.waitForTimeout(350);
  }

  private void captureScreenshot(final Page page, final Path screenshotPath, final boolean fullPage) {
    page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
  }

  private void printFinalReport(final LinkedHashMap<String, StepResult> report) {
    System.out.println("SaleADS Mi Negocio workflow final report:");
    for (Map.Entry<String, StepResult> entry : report.entrySet()) {
      StepResult result = entry.getValue();
      String details = result.details == null ? "" : " (" + result.details + ")";
      System.out.println("- " + entry.getKey() + ": " + (result.pass ? "PASS" : "FAIL") + details);
    }
  }

  private String buildFailureMessage(final LinkedHashMap<String, StepResult> report) {
    StringBuilder builder = new StringBuilder("Failing steps:");
    boolean hasFailures = false;
    for (Map.Entry<String, StepResult> entry : report.entrySet()) {
      if (!entry.getValue().pass) {
        hasFailures = true;
        builder
            .append(System.lineSeparator())
            .append("- ")
            .append(entry.getKey())
            .append(": ")
            .append(entry.getValue().details);
      }
    }
    if (!hasFailures) {
      return "All steps passed";
    }
    return builder.toString();
  }

  private String safeText(final Locator locator) {
    String text = locator.innerText();
    return text == null ? "" : text.trim();
  }

  private String trimToNull(final String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    return trimmed;
  }

  @FunctionalInterface
  private interface CheckedSupplier<T> {
    T get() throws Exception;
  }

  private static final class StepResult {
    private final boolean pass;
    private final String details;

    private StepResult(final boolean pass, final String details) {
      this.pass = pass;
      this.details = details;
    }

    private static StepResult pass(final String details) {
      return new StepResult(true, details);
    }

    private static StepResult fail(final String details) {
      return new StepResult(false, details);
    }
  }
}
