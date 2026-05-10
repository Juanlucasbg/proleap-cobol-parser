package io.proleap.saleads;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class SaleadsMiNegocioFullTest {

  private static final String EMAIL_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);

  private static final String STEP_LOGIN = "Login";
  private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
  private static final String STEP_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
  private static final String STEP_ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
  private static final String STEP_INFO_GENERAL = "Informaci\u00f3n General";
  private static final String STEP_DETALLES_CUENTA = "Detalles de la Cuenta";
  private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
  private static final String STEP_TERMINOS = "T\u00e9rminos y Condiciones";
  private static final String STEP_POLITICA = "Pol\u00edtica de Privacidad";

  @Test
  public void saleadsMiNegocioFullWorkflow() throws Exception {
    Assume.assumeTrue(
        "Set SALEADS_RUN_E2E=true to run this browser workflow.",
        Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_RUN_E2E", "false")));

    final Path outputDir = createOutputDirectory();
    final WorkflowReport report = new WorkflowReport(outputDir);
    final List<Throwable> failures = new ArrayList<>();

    Playwright playwright = null;
    Browser browser = null;
    BrowserContext context = null;
    Page appPage = null;

    try {
      playwright = Playwright.create();
      browser =
          playwright
              .chromium()
              .launch(
                  new BrowserType.LaunchOptions()
                      .setHeadless(
                          Boolean.parseBoolean(
                              System.getenv().getOrDefault("SALEADS_HEADLESS", "true"))));
      context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
      appPage = context.newPage();

      final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
      Assume.assumeTrue(
          "Set SALEADS_LOGIN_URL to the SaleADS login page for the target environment.",
          loginUrl != null && !loginUrl.isBlank());
      appPage.navigate(loginUrl);
      waitForUi(appPage);

      runStep(
          report,
          failures,
          STEP_LOGIN,
          () -> {
            stepLoginWithGoogle(context, appPage, report);
          });

      runStep(
          report,
          failures,
          STEP_MI_NEGOCIO_MENU,
          () -> {
            stepOpenMiNegocioMenu(appPage, report);
          });

      runStep(
          report,
          failures,
          STEP_AGREGAR_NEGOCIO_MODAL,
          () -> {
            stepValidateAgregarNegocioModal(appPage, report);
          });

      runStep(
          report,
          failures,
          STEP_ADMINISTRAR_NEGOCIOS_VIEW,
          () -> {
            stepOpenAdministrarNegocios(appPage, report);
          });

      runStep(
          report,
          failures,
          STEP_INFO_GENERAL,
          () -> {
            stepValidateInformacionGeneral(appPage);
          });

      runStep(
          report,
          failures,
          STEP_DETALLES_CUENTA,
          () -> {
            stepValidateDetallesCuenta(appPage);
          });

      runStep(
          report,
          failures,
          STEP_TUS_NEGOCIOS,
          () -> {
            stepValidateTusNegocios(appPage);
          });

      runStep(
          report,
          failures,
          STEP_TERMINOS,
          () -> {
            validateLegalLink(
                context,
                appPage,
                report,
                "Terminos y Condiciones",
                "Terminos y Condiciones",
                "08_terminos-condiciones.png",
                "terminos_url");
          });

      runStep(
          report,
          failures,
          STEP_POLITICA,
          () -> {
            validateLegalLink(
                context,
                appPage,
                report,
                "Politica de Privacidad",
                "Politica de Privacidad",
                "09_politica-privacidad.png",
                "politica_url");
          });
    } finally {
      report.writeFinalReport();
      if (appPage != null && !appPage.isClosed()) {
        screenshot(appPage, report.outputDir.resolve("99_final_state.png"), false);
      }
      if (context != null) {
        context.close();
      }
      if (browser != null) {
        browser.close();
      }
      if (playwright != null) {
        playwright.close();
      }
    }

    if (!failures.isEmpty()) {
      Assert.fail(
          "One or more SaleADS workflow validations failed. See "
              + outputDir.resolve("final-report.txt"));
    }
  }

  private void stepLoginWithGoogle(BrowserContext context, Page appPage, WorkflowReport report) {
    Locator loginButton =
        findVisibleText(
            appPage,
            "Sign in with Google",
            "Iniciar sesion con Google",
            "Iniciar sesi\u00f3n con Google",
            "Continuar con Google",
            "Login with Google");
    assertNotNull("Google login button was not found.", loginButton);

    Page googlePage = null;
    try {
      googlePage =
          context.waitForPage(
              new BrowserContext.WaitForPageOptions().setTimeout(8_000),
              () -> {
                loginButton.click();
              });
    } catch (PlaywrightException ignored) {
      loginButton.click();
    }
    waitForUi(appPage);

    if (googlePage != null) {
      waitForUi(googlePage);
      clickAccountIfVisible(googlePage, EMAIL_ACCOUNT);
      waitForUi(appPage);
    } else {
      clickAccountIfVisible(appPage, EMAIL_ACCOUNT);
      waitForUi(appPage);
    }

    assertAnyVisibleText(
        appPage,
        "Main application interface should appear after login.",
        "Negocio",
        "Mi Negocio",
        "Dashboard",
        "Inicio");

    boolean sidebarVisible =
        isVisible(appPage.locator("aside").first(), 4_000)
            || isVisible(appPage.locator("nav").first(), 4_000);
    assertTrue("Left sidebar navigation is not visible.", sidebarVisible);

    screenshot(appPage, report.outputDir.resolve("01_dashboard_loaded.png"), false);
  }

  private void stepOpenMiNegocioMenu(Page appPage, WorkflowReport report) {
    clickVisibleText(appPage, "Mi Negocio", "Negocio");
    waitForUi(appPage);

    assertAnyVisibleText(
        appPage,
        "Mi Negocio submenu should include Agregar Negocio.",
        "Agregar Negocio",
        "Agregar negocio");
    assertAnyVisibleText(
        appPage,
        "Mi Negocio submenu should include Administrar Negocios.",
        "Administrar Negocios",
        "Administrar negocios");

    screenshot(appPage, report.outputDir.resolve("02_mi-negocio-menu-expanded.png"), false);
  }

  private void stepValidateAgregarNegocioModal(Page appPage, WorkflowReport report) {
    clickVisibleText(appPage, "Agregar Negocio", "Agregar negocio");
    waitForUi(appPage);

    assertAnyVisibleText(appPage, "Missing modal title.", "Crear Nuevo Negocio");
    assertAnyVisibleText(
        appPage, "Missing Nombre del Negocio field.", "Nombre del Negocio", "Nombre negocio");
    assertAnyVisibleText(
        appPage, "Missing negocios quota text.", "Tienes 2 de 3 negocios", "2 de 3 negocios");
    assertAnyVisibleText(appPage, "Missing Cancelar button.", "Cancelar");
    assertAnyVisibleText(appPage, "Missing Crear Negocio button.", "Crear Negocio");

    screenshot(appPage, report.outputDir.resolve("03_agregar-negocio-modal.png"), false);

    Locator nameInput = findVisibleLabel(appPage, "Nombre del Negocio", "Nombre negocio");
    if (nameInput != null) {
      nameInput.click();
      nameInput.fill("Negocio Prueba Automatizacion");
      waitForUi(appPage);
    }

    clickVisibleText(appPage, "Cancelar");
    waitForUi(appPage);
  }

  private void stepOpenAdministrarNegocios(Page appPage, WorkflowReport report) {
    if (!isAnyTextVisible(appPage, "Administrar Negocios", "Administrar negocios")) {
      clickVisibleText(appPage, "Mi Negocio", "Negocio");
      waitForUi(appPage);
    }

    clickVisibleText(appPage, "Administrar Negocios", "Administrar negocios");
    waitForUi(appPage);

    assertAnyVisibleText(appPage, "Informacion General section is missing.", "Informacion General", "Informaci\u00f3n General");
    assertAnyVisibleText(appPage, "Detalles de la Cuenta section is missing.", "Detalles de la Cuenta");
    assertAnyVisibleText(appPage, "Tus Negocios section is missing.", "Tus Negocios");
    assertAnyVisibleText(appPage, "Seccion Legal section is missing.", "Seccion Legal", "Secci\u00f3n Legal");

    screenshot(appPage, report.outputDir.resolve("04_administrar-negocios-full.png"), true);
  }

  private void stepValidateInformacionGeneral(Page appPage) {
    final String bodyText = safeBodyText(appPage);
    final Matcher emailMatcher = EMAIL_PATTERN.matcher(bodyText);
    assertTrue("Expected a visible user email.", emailMatcher.find());

    String withoutEmail = bodyText.replace(emailMatcher.group(), "").trim();
    assertTrue("Expected visible user name text near account info.", withoutEmail.length() > 20);

    assertAnyVisibleText(appPage, "Missing BUSINESS PLAN text.", "BUSINESS PLAN");
    assertAnyVisibleText(appPage, "Missing Cambiar Plan button.", "Cambiar Plan");
  }

  private void stepValidateDetallesCuenta(Page appPage) {
    assertAnyVisibleText(appPage, "Missing Cuenta creada text.", "Cuenta creada");
    assertAnyVisibleText(appPage, "Missing Estado activo text.", "Estado activo");
    assertAnyVisibleText(appPage, "Missing Idioma seleccionado text.", "Idioma seleccionado");
  }

  private void stepValidateTusNegocios(Page appPage) {
    assertAnyVisibleText(appPage, "Business list should be visible.", "Tus Negocios");
    assertAnyVisibleText(appPage, "Missing Agregar Negocio button in businesses section.", "Agregar Negocio");
    assertAnyVisibleText(appPage, "Missing businesses quota text.", "Tienes 2 de 3 negocios", "2 de 3 negocios");
  }

  private void validateLegalLink(
      BrowserContext context,
      Page appPage,
      WorkflowReport report,
      String linkLabel,
      String headingLabel,
      String screenshotName,
      String urlKey) {
    Page targetPage = appPage;
    boolean openedNewTab = false;

    try {
      targetPage =
          context.waitForPage(
              new BrowserContext.WaitForPageOptions().setTimeout(6_000),
              () -> {
                clickVisibleText(appPage, linkLabel, withAccents(linkLabel));
              });
      openedNewTab = true;
    } catch (PlaywrightException ignored) {
      clickVisibleText(appPage, linkLabel, withAccents(linkLabel));
      waitForUi(appPage);
      targetPage = appPage;
    }

    waitForUi(targetPage);
    assertAnyVisibleText(
        targetPage,
        "Expected legal heading is missing for " + linkLabel + ".",
        headingLabel,
        withAccents(headingLabel));

    String legalText = safeBodyText(targetPage);
    assertTrue(
        "Expected legal content text on " + linkLabel + ".",
        legalText != null && legalText.trim().length() > 120);

    screenshot(targetPage, report.outputDir.resolve(screenshotName), true);
    report.metadata.put(urlKey, targetPage.url());

    if (openedNewTab) {
      targetPage.close();
      appPage.bringToFront();
      waitForUi(appPage);
    } else {
      appPage.goBack();
      waitForUi(appPage);
    }
  }

  private void runStep(
      WorkflowReport report, List<Throwable> failures, String stepName, ThrowingRunnable stepAction) {
    try {
      stepAction.run();
      report.statuses.put(stepName, "PASS");
    } catch (Throwable t) {
      report.statuses.put(stepName, "FAIL");
      report.failures.put(stepName, t.getMessage() == null ? t.toString() : t.getMessage());
      failures.add(t);
    }
  }

  private String safeBodyText(Page page) {
    return page.locator("body").innerText();
  }

  private Locator findVisibleText(Page page, String... labels) {
    for (String label : labels) {
      Locator exact = page.getByText(label, new Page.GetByTextOptions().setExact(true)).first();
      if (isVisible(exact, 1_500)) {
        return exact;
      }
    }
    for (String label : labels) {
      Locator partial = page.getByText(label).first();
      if (isVisible(partial, 1_500)) {
        return partial;
      }
    }
    return null;
  }

  private Locator findVisibleLabel(Page page, String... labels) {
    for (String label : labels) {
      Locator byLabel = page.getByLabel(label).first();
      if (isVisible(byLabel, 1_500)) {
        return byLabel;
      }
    }
    return null;
  }

  private void clickVisibleText(Page page, String... labels) {
    Locator locator = findVisibleText(page, labels);
    assertNotNull("Could not find clickable text: " + String.join(", ", labels), locator);
    locator.click();
    waitForUi(page);
  }

  private void clickAccountIfVisible(Page page, String accountEmail) {
    Locator account = findVisibleText(page, accountEmail);
    if (account != null) {
      account.click();
      waitForUi(page);
    }
  }

  private void assertAnyVisibleText(Page page, String message, String... labels) {
    assertNotNull(message, findVisibleText(page, labels));
  }

  private boolean isAnyTextVisible(Page page, String... labels) {
    return findVisibleText(page, labels) != null;
  }

  private boolean isVisible(Locator locator, int timeoutMs) {
    try {
      return locator.isVisible(new Locator.IsVisibleOptions().setTimeout((double) timeoutMs));
    } catch (PlaywrightException ignored) {
      return false;
    }
  }

  private void waitForUi(Page page) {
    try {
      page.waitForLoadState(
          LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(10_000));
    } catch (PlaywrightException ignored) {
      // Best effort wait: some views are SPA fragments without load event transitions.
    }
    try {
      page.waitForLoadState(
          LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(8_000));
    } catch (PlaywrightException ignored) {
      // Best effort wait for in-flight UI requests.
    }
    page.waitForTimeout(700);
  }

  private void screenshot(Page page, Path path, boolean fullPage) {
    page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
  }

  private String withAccents(String text) {
    String lower = text.toLowerCase(Locale.ROOT);
    if ("terminos y condiciones".equals(lower)) {
      return "T\u00e9rminos y Condiciones";
    }
    if ("politica de privacidad".equals(lower)) {
      return "Pol\u00edtica de Privacidad";
    }
    return text;
  }

  private Path createOutputDirectory() throws IOException {
    String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    Path dir = Paths.get("target", "saleads-mi-negocio-evidence", runId);
    Files.createDirectories(dir);
    return dir;
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  private static class WorkflowReport {
    private final Path outputDir;
    private final Map<String, String> statuses = new LinkedHashMap<>();
    private final Map<String, String> failures = new LinkedHashMap<>();
    private final Map<String, String> metadata = new LinkedHashMap<>();

    private WorkflowReport(Path outputDir) {
      this.outputDir = outputDir;
      statuses.put(STEP_LOGIN, "FAIL");
      statuses.put(STEP_MI_NEGOCIO_MENU, "FAIL");
      statuses.put(STEP_AGREGAR_NEGOCIO_MODAL, "FAIL");
      statuses.put(STEP_ADMINISTRAR_NEGOCIOS_VIEW, "FAIL");
      statuses.put(STEP_INFO_GENERAL, "FAIL");
      statuses.put(STEP_DETALLES_CUENTA, "FAIL");
      statuses.put(STEP_TUS_NEGOCIOS, "FAIL");
      statuses.put(STEP_TERMINOS, "FAIL");
      statuses.put(STEP_POLITICA, "FAIL");
    }

    private void writeFinalReport() throws IOException {
      StringBuilder reportText = new StringBuilder();
      reportText.append("saleads_mi_negocio_full_test\n\n");
      reportText.append("Final report:\n");
      for (Map.Entry<String, String> status : statuses.entrySet()) {
        reportText.append("- ").append(status.getKey()).append(": ").append(status.getValue()).append('\n');
      }

      if (!metadata.isEmpty()) {
        reportText.append("\nCaptured URLs:\n");
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
          reportText.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        }
      }

      if (!failures.isEmpty()) {
        reportText.append("\nFailure details:\n");
        for (Map.Entry<String, String> failure : failures.entrySet()) {
          reportText.append("- ").append(failure.getKey()).append(": ").append(failure.getValue()).append('\n');
        }
      }

      Files.writeString(outputDir.resolve("final-report.txt"), reportText.toString());
      System.out.println(reportText);
    }
  }
}
