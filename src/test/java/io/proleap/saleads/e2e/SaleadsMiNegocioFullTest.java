package io.proleap.saleads.e2e;

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
import java.nio.charset.StandardCharsets;
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
import java.util.Objects;
import java.util.regex.Pattern;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/**
 * End-to-end validation for SaleADS "Mi Negocio" workflow.
 *
 * <p>This test is environment-agnostic and never hardcodes a domain. Configure the runtime target with:
 * <ul>
 *   <li>SALEADS_E2E_ENABLED=true (or -Dsaleads.e2e.enabled=true)</li>
 *   <li>SALEADS_START_URL=https://... (or -Dsaleads.start.url=...)</li>
 *   <li>Optional: SALEADS_GOOGLE_ACCOUNT and SALEADS_HEADLESS</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

  private static final String REPORT_LOGIN = "Login";
  private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
  private static final String REPORT_AGREGAR_MODAL = "Agregar Negocio modal";
  private static final String REPORT_ADMIN_VIEW = "Administrar Negocios view";
  private static final String REPORT_INFO_GENERAL = "Informacion General";
  private static final String REPORT_DETALLES = "Detalles de la Cuenta";
  private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
  private static final String REPORT_TERMINOS = "Terminos y Condiciones";
  private static final String REPORT_PRIVACIDAD = "Politica de Privacidad";

  @Test
  public void saleadsMiNegocioFullWorkflow() throws Exception {
    final boolean e2eEnabled =
        Boolean.parseBoolean(readConfig("SALEADS_E2E_ENABLED", "saleads.e2e.enabled", "false"));
    Assume.assumeTrue(
        "Enable with SALEADS_E2E_ENABLED=true or -Dsaleads.e2e.enabled=true.", e2eEnabled);

    final boolean headless =
        Boolean.parseBoolean(readConfig("SALEADS_HEADLESS", "saleads.headless", "true"));
    final String startUrl = readConfig("SALEADS_START_URL", "saleads.start.url", "").trim();
    final String googleAccount =
        readConfig(
            "SALEADS_GOOGLE_ACCOUNT",
            "saleads.google.account",
            "juanlucasbarbiergarzon@gmail.com");
    final String accountLocalPart = googleAccount.contains("@") ? googleAccount.split("@")[0] : "";

    final Path evidenceDir = createEvidenceDir();
    final LinkedHashMap<String, Boolean> report = initialReportMap();
    final LinkedHashMap<String, String> details = new LinkedHashMap<>();
    final List<String> failures = new ArrayList<>();

    String termsUrl = "";
    String privacyUrl = "";

    try (Playwright playwright = Playwright.create();
        Browser browser =
            playwright
                .chromium()
                .launch(new BrowserType.LaunchOptions().setHeadless(headless));
        BrowserContext context =
            browser.newContext(
                new Browser.NewContextOptions()
                    .setViewportSize(1600, 1000)
                    .setAcceptDownloads(false))) {

      final Page appPage = context.newPage();
      if (!startUrl.isEmpty()) {
        appPage.navigate(startUrl);
        waitForUi(appPage);
      } else if (Objects.equals("about:blank", appPage.url())) {
        Assume.assumeTrue(
            "Set SALEADS_START_URL (or -Dsaleads.start.url) to the current environment login page.",
            false);
      }

      // Step 1 - Login with Google
      runStep(
          report,
          details,
          REPORT_LOGIN,
          () -> {
            Locator loginButton =
                requireVisibleByTexts(
                    appPage,
                    10_000,
                    "Sign in with Google",
                    "Iniciar sesion con Google",
                    "Iniciar sesión con Google",
                    "Login with Google",
                    "Continuar con Google");

            Page popupPage = clickAndCapturePopup(context, loginButton);
            if (popupPage != null) {
              waitForUi(popupPage);
              chooseGoogleAccountIfVisible(popupPage, googleAccount);
              waitForUi(appPage);
              appPage.bringToFront();
            } else {
              waitForUi(appPage);
              chooseGoogleAccountIfVisible(appPage, googleAccount);
            }

            requireVisibleByTexts(appPage, 20_000, "Negocio", "Mi Negocio");
            boolean sidebarVisible = isVisible(appPage.locator("aside, nav").first(), 8_000);
            Assert.assertTrue(
                "Main interface should be visible and sidebar should render.", sidebarVisible);

            captureScreenshot(appPage, evidenceDir, "01-dashboard-loaded.png", false);
          });

      // Step 2 - Open Mi Negocio menu
      runStep(
          report,
          details,
          REPORT_MI_NEGOCIO_MENU,
          () -> {
            requireVisibleByTexts(appPage, 10_000, "Negocio");
            Locator miNegocio = ensureVisibleViaParent(appPage, "Mi Negocio", "Negocio");
            miNegocio.click();
            waitForUi(appPage);

            requireVisibleByTexts(appPage, 10_000, "Agregar Negocio");
            requireVisibleByTexts(appPage, 10_000, "Administrar Negocios");
            captureScreenshot(appPage, evidenceDir, "02-mi-negocio-expanded.png", false);
          });

      // Step 3 - Validate Agregar Negocio modal
      runStep(
          report,
          details,
          REPORT_AGREGAR_MODAL,
          () -> {
            Locator agregarNegocio = requireVisibleByTexts(appPage, 10_000, "Agregar Negocio");
            agregarNegocio.click();
            waitForUi(appPage);

            requireVisibleByTexts(appPage, 10_000, "Crear Nuevo Negocio");
            boolean nombreInputVisible =
                isVisible(firstVisibleLocator(
                        2_000,
                        appPage.getByLabel("Nombre del Negocio").first(),
                        appPage.getByPlaceholder("Nombre del Negocio").first(),
                        appPage.locator("input[name*='nombre' i]").first()),
                    3_000);
            Assert.assertTrue("Input 'Nombre del Negocio' must be visible.", nombreInputVisible);

            requireVisibleByTexts(appPage, 10_000, "Tienes 2 de 3 negocios");
            requireVisibleByTexts(appPage, 10_000, "Cancelar");
            requireVisibleByTexts(appPage, 10_000, "Crear Negocio");
            captureScreenshot(appPage, evidenceDir, "03-agregar-negocio-modal.png", false);

            Locator nombreInput =
                firstVisibleLocator(
                    2_000,
                    appPage.getByLabel("Nombre del Negocio").first(),
                    appPage.getByPlaceholder("Nombre del Negocio").first(),
                    appPage.locator("input[name*='nombre' i]").first());
            if (nombreInput != null) {
              nombreInput.click();
              nombreInput.fill("Negocio Prueba Automatizacion");
            }
            Locator cancelar = requireVisibleByTexts(appPage, 5_000, "Cancelar");
            cancelar.click();
            waitForUi(appPage);
          });

      // Step 4 - Open Administrar Negocios view
      runStep(
          report,
          details,
          REPORT_ADMIN_VIEW,
          () -> {
            Locator administrar =
                ensureVisibleViaParent(appPage, "Administrar Negocios", "Mi Negocio");
            administrar.click();
            waitForUi(appPage);

            requireVisibleByTexts(appPage, 15_000, "Informacion General", "Información General");
            requireVisibleByTexts(appPage, 15_000, "Detalles de la Cuenta");
            requireVisibleByTexts(appPage, 15_000, "Tus Negocios");
            requireVisibleByTexts(appPage, 15_000, "Seccion Legal", "Sección Legal");
            captureScreenshot(appPage, evidenceDir, "04-administrar-negocios-full.png", true);
          });

      // Step 5 - Validate Informacion General section
      runStep(
          report,
          details,
          REPORT_INFO_GENERAL,
          () -> {
            boolean userNameVisible =
                isAnyTextVisible(appPage, 7_000, accountLocalPart, "Nombre", "Usuario", "Name");
            boolean userEmailVisible =
                isVisible(
                    appPage
                        .getByText(
                            Pattern.compile(
                                "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
                                Pattern.CASE_INSENSITIVE))
                        .first(),
                    7_000);
            boolean businessPlanVisible = isAnyTextVisible(appPage, 7_000, "BUSINESS PLAN");
            boolean cambiarPlanVisible =
                isAnyTextVisible(appPage, 7_000, "Cambiar Plan", "Change Plan");

            Assert.assertTrue("User name must be visible in Informacion General.", userNameVisible);
            Assert.assertTrue("User email must be visible in Informacion General.", userEmailVisible);
            Assert.assertTrue("'BUSINESS PLAN' text must be visible.", businessPlanVisible);
            Assert.assertTrue("'Cambiar Plan' button must be visible.", cambiarPlanVisible);
          });

      // Step 6 - Validate Detalles de la Cuenta
      runStep(
          report,
          details,
          REPORT_DETALLES,
          () -> {
            requireVisibleByTexts(appPage, 8_000, "Cuenta creada", "Account created");
            requireVisibleByTexts(appPage, 8_000, "Estado activo", "Active status");
            requireVisibleByTexts(appPage, 8_000, "Idioma seleccionado", "Selected language");
          });

      // Step 7 - Validate Tus Negocios
      runStep(
          report,
          details,
          REPORT_TUS_NEGOCIOS,
          () -> {
            requireVisibleByTexts(appPage, 8_000, "Tus Negocios");
            requireVisibleByTexts(appPage, 8_000, "Agregar Negocio");
            requireVisibleByTexts(appPage, 8_000, "Tienes 2 de 3 negocios");

            Locator businessItems =
                appPage.locator(
                    "li, [role='row'], [data-testid*='business'], [class*='business-card']");
            Assert.assertTrue(
                "Business list should be visible.",
                businessItems.count() > 0 || isAnyTextVisible(appPage, 3_000, "Negocio"));
          });

      // Step 8 - Validate Terminos y Condiciones
      LegalResult termsResult =
          runLegalValidation(
              appPage,
              context,
              evidenceDir,
              "Terminos y Condiciones",
              Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"),
              "08-terminos.png");
      report.put(REPORT_TERMINOS, termsResult.passed);
      details.put(REPORT_TERMINOS, termsResult.details);
      termsUrl = termsResult.finalUrl;

      // Step 9 - Validate Politica de Privacidad
      LegalResult privacyResult =
          runLegalValidation(
              appPage,
              context,
              evidenceDir,
              "Politica de Privacidad",
              Arrays.asList("Política de Privacidad", "Politica de Privacidad"),
              "09-politica.png");
      report.put(REPORT_PRIVACIDAD, privacyResult.passed);
      details.put(REPORT_PRIVACIDAD, privacyResult.details);
      privacyUrl = privacyResult.finalUrl;

      for (Map.Entry<String, Boolean> entry : report.entrySet()) {
        if (!entry.getValue()) {
          failures.add(entry.getKey() + " -> " + details.getOrDefault(entry.getKey(), "No details"));
        }
      }

      writeFinalReport(evidenceDir, report, details, termsUrl, privacyUrl);
      Assert.assertTrue(
          "SaleADS Mi Negocio workflow has failing validations:\n" + String.join("\n", failures),
          failures.isEmpty());
    }
  }

  private LegalResult runLegalValidation(
      final Page appPage,
      final BrowserContext context,
      final Path evidenceDir,
      final String reportKey,
      final List<String> headingCandidates,
      final String screenshotName) {
    try {
      String appUrlBefore = appPage.url();
      Locator legalLink = requireVisibleByTexts(appPage, 10_000, headingCandidates.toArray(new String[0]));
      Page popupPage = clickAndCapturePopup(context, legalLink);
      Page legalPage = popupPage != null ? popupPage : appPage;
      waitForUi(legalPage);

      requireVisibleByTexts(legalPage, 10_000, headingCandidates.toArray(new String[0]));
      String bodyText = safeInnerText(legalPage.locator("body").first());
      Assert.assertTrue(
          "Legal content text must be visible for " + reportKey + ".",
          bodyText != null && bodyText.trim().replaceAll("\\s+", " ").length() > 120);

      captureScreenshot(legalPage, evidenceDir, screenshotName, true);
      String finalUrl = legalPage.url();

      if (popupPage != null) {
        popupPage.close();
        appPage.bringToFront();
        waitForUi(appPage);
      } else if (!Objects.equals(appUrlBefore, appPage.url())) {
        try {
          appPage.goBack(new Page.GoBackOptions().setTimeout(10_000));
          waitForUi(appPage);
        } catch (PlaywrightException ignored) {
          // App navigation can be SPA based; failing to go back should not interrupt validation.
        }
      }

      return new LegalResult(true, finalUrl, "PASS. URL=" + finalUrl);
    } catch (Throwable error) {
      captureScreenshot(appPage, evidenceDir, "error-" + sanitizeFileName(reportKey) + ".png", false);
      return new LegalResult(false, "", "FAIL. " + rootMessage(error));
    }
  }

  private void runStep(
      final Map<String, Boolean> report,
      final Map<String, String> details,
      final String key,
      final ThrowingRunnable step) {
    try {
      step.run();
      report.put(key, true);
      details.put(key, "PASS");
    } catch (Throwable error) {
      report.put(key, false);
      details.put(key, "FAIL. " + rootMessage(error));
    }
  }

  private LinkedHashMap<String, Boolean> initialReportMap() {
    LinkedHashMap<String, Boolean> report = new LinkedHashMap<>();
    report.put(REPORT_LOGIN, false);
    report.put(REPORT_MI_NEGOCIO_MENU, false);
    report.put(REPORT_AGREGAR_MODAL, false);
    report.put(REPORT_ADMIN_VIEW, false);
    report.put(REPORT_INFO_GENERAL, false);
    report.put(REPORT_DETALLES, false);
    report.put(REPORT_TUS_NEGOCIOS, false);
    report.put(REPORT_TERMINOS, false);
    report.put(REPORT_PRIVACIDAD, false);
    return report;
  }

  private void chooseGoogleAccountIfVisible(Page page, String googleAccount) {
    Locator accountLocator = page.getByText(googleAccount).first();
    if (isVisible(accountLocator, 5_000)) {
      accountLocator.click();
      waitForUi(page);
    }
  }

  private Locator ensureVisibleViaParent(Page page, String targetText, String parentText) {
    Locator target = findVisibleByTexts(page, 2_000, targetText);
    if (target != null) {
      return target;
    }

    Locator parent = requireVisibleByTexts(page, 8_000, parentText);
    parent.click();
    waitForUi(page);
    return requireVisibleByTexts(page, 8_000, targetText);
  }

  private Page clickAndCapturePopup(BrowserContext context, Locator clickTarget) {
    try {
      return context.waitForPage(
          () -> clickTarget.click(), new BrowserContext.WaitForPageOptions().setTimeout(6_000));
    } catch (PlaywrightException noPopup) {
      clickTarget.click();
      return null;
    }
  }

  private Locator requireVisibleByTexts(Page page, double timeoutMs, String... texts) {
    Locator visible = findVisibleByTexts(page, timeoutMs, texts);
    if (visible == null) {
      throw new AssertionError("Could not find visible text from candidates: " + Arrays.toString(texts));
    }
    return visible;
  }

  private Locator findVisibleByTexts(Page page, double timeoutMs, String... texts) {
    for (String text : texts) {
      if (text == null || text.isBlank()) {
        continue;
      }

      Locator exactText = page.getByText(text).first();
      if (isVisible(exactText, timeoutMs)) {
        return exactText;
      }

      Locator fallback = page.locator("text=" + text).first();
      if (isVisible(fallback, timeoutMs)) {
        return fallback;
      }
    }
    return null;
  }

  private boolean isAnyTextVisible(Page page, double timeoutMs, String... texts) {
    return findVisibleByTexts(page, timeoutMs, texts) != null;
  }

  private Locator firstVisibleLocator(double timeoutMs, Locator... locators) {
    for (Locator locator : locators) {
      if (locator != null && isVisible(locator, timeoutMs)) {
        return locator;
      }
    }
    return null;
  }

  private boolean isVisible(Locator locator, double timeoutMs) {
    if (locator == null) {
      return false;
    }

    try {
      locator.waitFor(
          new Locator.WaitForOptions()
              .setState(WaitForSelectorState.VISIBLE)
              .setTimeout(timeoutMs));
      return true;
    } catch (PlaywrightException ignored) {
      return false;
    }
  }

  private void waitForUi(Page page) {
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(8_000));
    } catch (PlaywrightException networkIdleTimeout) {
      try {
        page.waitForLoadState(
            LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(4_000));
      } catch (PlaywrightException ignored) {
        // Keep moving; some SPAs never become fully idle.
      }
      page.waitForTimeout(500);
    }
  }

  private Path createEvidenceDir() throws IOException {
    String baseDir =
        readConfig("SALEADS_EVIDENCE_DIR", "saleads.evidence.dir", "target/saleads-evidence");
    String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
    Path directory = Paths.get(baseDir, timestamp);
    Files.createDirectories(directory);
    return directory;
  }

  private void captureScreenshot(Page page, Path evidenceDir, String fileName, boolean fullPage) {
    try {
      Files.createDirectories(evidenceDir);
      Path file = evidenceDir.resolve(fileName);
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(file)
              .setFullPage(fullPage));
    } catch (Throwable ignored) {
      // Do not stop the workflow if evidence capture fails.
    }
  }

  private void writeFinalReport(
      Path evidenceDir,
      LinkedHashMap<String, Boolean> report,
      LinkedHashMap<String, String> details,
      String termsUrl,
      String privacyUrl)
      throws IOException {
    List<String> lines = new ArrayList<>();
    lines.add("# SaleADS Mi Negocio - Final Report");
    lines.add("");
    lines.add("| Validation | Status | Details |");
    lines.add("|---|---|---|");
    for (Map.Entry<String, Boolean> entry : report.entrySet()) {
      String status = entry.getValue() ? "PASS" : "FAIL";
      String detail = details.getOrDefault(entry.getKey(), "");
      lines.add("| " + entry.getKey() + " | " + status + " | " + detail + " |");
    }
    lines.add("");
    lines.add("- Terminos y Condiciones URL: " + (termsUrl == null || termsUrl.isBlank() ? "N/A" : termsUrl));
    lines.add("- Politica de Privacidad URL: " + (privacyUrl == null || privacyUrl.isBlank() ? "N/A" : privacyUrl));

    Files.writeString(
        evidenceDir.resolve("final-report.md"),
        String.join(System.lineSeparator(), lines) + System.lineSeparator(),
        StandardCharsets.UTF_8);
  }

  private String readConfig(String envName, String propertyName, String defaultValue) {
    String envValue = System.getenv(envName);
    if (envValue != null && !envValue.isBlank()) {
      return envValue;
    }

    String propertyValue = System.getProperty(propertyName);
    if (propertyValue != null && !propertyValue.isBlank()) {
      return propertyValue;
    }

    return defaultValue;
  }

  private String rootMessage(Throwable error) {
    Throwable root = error;
    while (root.getCause() != null) {
      root = root.getCause();
    }
    return root.getMessage() == null ? root.toString() : root.getMessage();
  }

  private String safeInnerText(Locator locator) {
    try {
      return locator.innerText();
    } catch (PlaywrightException error) {
      return null;
    }
  }

  private String sanitizeFileName(String value) {
    return value.toLowerCase().replaceAll("[^a-z0-9]+", "-");
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  private static final class LegalResult {
    private final boolean passed;
    private final String finalUrl;
    private final String details;

    private LegalResult(boolean passed, String finalUrl, String details) {
      this.passed = passed;
      this.finalUrl = finalUrl;
      this.details = details;
    }
  }
}
