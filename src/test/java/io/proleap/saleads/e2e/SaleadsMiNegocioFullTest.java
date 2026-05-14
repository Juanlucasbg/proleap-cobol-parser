package io.proleap.saleads.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * End-to-end validation for SaleADS "Mi Negocio" workflow.
 *
 * <p>Skipped by default. Run with:
 * <pre>
 *   mvn -Dtest=SaleadsMiNegocioFullTest -Dsaleads.e2e.enabled=true test
 * </pre>
 */
public class SaleadsMiNegocioFullTest {

  private static final double UI_TIMEOUT_MS = 30_000;
  private static final double SHORT_TIMEOUT_MS = 8_000;
  private static final String ENABLE_PROPERTY = "saleads.e2e.enabled";
  private static final String LOGIN_URL_KEY = "SALEADS_LOGIN_URL";
  private static final String CDP_URL_KEY = "SALEADS_CDP_URL";
  private static final Path OUTPUT_DIR = Paths.get("target", "saleads-mi-negocio-full-test");
  private static final Path SCREENSHOT_DIR = OUTPUT_DIR.resolve("screenshots");
  private static final Path REPORT_FILE = OUTPUT_DIR.resolve("final-report.txt");

  @Test
  public void saleadsMiNegocioFullWorkflow() throws IOException {
    Assume.assumeTrue(
        "Set -D" + ENABLE_PROPERTY + "=true to execute this live E2E test.",
        Boolean.parseBoolean(System.getProperty(ENABLE_PROPERTY, "false")));

    Files.createDirectories(SCREENSHOT_DIR);

    final Map<String, StepResult> report = initReport();
    final String[] legalUrls = {"NOT_VISITED", "NOT_VISITED"};

    BrowserSession session = null;
    try (Playwright playwright = Playwright.create()) {
      session = createSession(playwright);
      final BrowserContext context = session.context;
      final Page appPage = session.page;

      runStep(report, "Login", () -> {
        loginWithGoogle(appPage);
        expectVisible("Main application interface", locateMainInterface(appPage));
        expectVisible("Left sidebar navigation", locateSidebar(appPage));
        screenshot(appPage, "01-dashboard-loaded.png", false);
      });

      runStep(report, "Mi Negocio menu", () -> {
        openMiNegocioMenu(appPage);
        expectVisible("Agregar Negocio submenu option", locateAgregarNegocio(appPage));
        expectVisible("Administrar Negocios submenu option", locateAdministrarNegocios(appPage));
        screenshot(appPage, "02-mi-negocio-menu-expanded.png", false);
      });

      runStep(report, "Agregar Negocio modal", () -> {
        clickAndWait(appPage, locateAgregarNegocio(appPage));
        expectVisible("Modal title Crear Nuevo Negocio", appPage.getByText("Crear Nuevo Negocio"));
        expectVisible(
            "Nombre del Negocio field",
            firstVisibleOrThrow(
                "Nombre del Negocio field",
                appPage.getByLabel("Nombre del Negocio"),
                appPage.getByPlaceholder("Nombre del Negocio"),
                appPage.getByRole(
                    AriaRole.TEXTBOX,
                    new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Nombre del Negocio")))));
        expectVisible(
            "Tienes 2 de 3 negocios text",
            appPage.getByText(Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios")));
        expectVisible("Cancelar button", appPage.getByRole(AriaRole.BUTTON, named("Cancelar")));
        expectVisible(
            "Crear Negocio button",
            appPage.getByRole(AriaRole.BUTTON, named("Crear Negocio")));
        screenshot(appPage, "03-agregar-negocio-modal.png", false);

        final Locator nombreInput =
            firstVisibleOrThrow(
                "Nombre del Negocio field",
                appPage.getByLabel("Nombre del Negocio"),
                appPage.getByPlaceholder("Nombre del Negocio"));
        nombreInput.fill("Negocio Prueba Automatizacion");
        clickAndWait(appPage, appPage.getByRole(AriaRole.BUTTON, named("Cancelar")));
      });

      runStep(report, "Administrar Negocios view", () -> {
        if (!isVisible(locateAdministrarNegocios(appPage), SHORT_TIMEOUT_MS)) {
          openMiNegocioMenu(appPage);
        }
        clickAndWait(appPage, locateAdministrarNegocios(appPage));
        expectVisible(
            "Informacion General section",
            appPage.getByText(Pattern.compile("(?i)Informaci[oó]n General")));
        expectVisible(
            "Detalles de la Cuenta section",
            appPage.getByText(Pattern.compile("(?i)Detalles de la Cuenta")));
        expectVisible(
            "Tus Negocios section",
            appPage.getByText(Pattern.compile("(?i)Tus Negocios")));
        expectVisible(
            "Seccion Legal section",
            appPage.getByText(Pattern.compile("(?i)Secci[oó]n Legal")));
        screenshot(appPage, "04-administrar-negocios-page.png", true);
      });

      runStep(report, "Información General", () -> {
        final Locator section = locateSection(appPage, "Informaci[oó]n General");
        expectVisible("Informacion General container", section);
        expectVisible(
            "User name visible",
            section.getByText(Pattern.compile("(?i)(Nombre|Usuario)")));
        expectVisible("User email visible", locateEmailValue(section));
        expectVisible(
            "BUSINESS PLAN text",
            appPage.getByText(Pattern.compile("(?i)BUSINESS\\s+PLAN")));
        expectVisible(
            "Cambiar Plan button",
            appPage.getByRole(AriaRole.BUTTON, named("Cambiar Plan")));
      });

      runStep(report, "Detalles de la Cuenta", () -> {
        final Locator section = locateSection(appPage, "Detalles de la Cuenta");
        expectVisible("Cuenta creada text", section.getByText("Cuenta creada"));
        expectVisible("Estado activo text", section.getByText("Estado activo"));
        expectVisible("Idioma seleccionado text", section.getByText("Idioma seleccionado"));
      });

      runStep(report, "Tus Negocios", () -> {
        final Locator section = locateSection(appPage, "Tus Negocios");
        expectVisible("Business list", section.locator("ul, table, [role='list'], [role='table']"));
        expectVisible(
            "Agregar Negocio button",
            section.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Agregar Negocio")));
        expectVisible(
            "Tienes 2 de 3 negocios text",
            section.getByText(Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios")));
      });

      runStep(report, "Términos y Condiciones", () -> {
        legalUrls[0] = openAndValidateLegalPage(
            context,
            appPage,
            "Términos y Condiciones",
            "Términos y Condiciones",
            "05-terminos-y-condiciones.png");
      });

      runStep(report, "Política de Privacidad", () -> {
        legalUrls[1] = openAndValidateLegalPage(
            context,
            appPage,
            "Política de Privacidad",
            "Política de Privacidad",
            "06-politica-de-privacidad.png");
      });
    } finally {
      if (session != null) {
        session.close();
      }
      writeReport(report, legalUrls[0], legalUrls[1]);
    }

    assertAllPassed(report);
  }

  private static BrowserSession createSession(final Playwright playwright) {
    final String cdpUrl = envOrProperty(CDP_URL_KEY);
    if (cdpUrl != null && !cdpUrl.isBlank()) {
      final Browser browser = playwright.chromium().connectOverCDP(cdpUrl);
      final BrowserContext context =
          browser.contexts().isEmpty() ? browser.newContext() : browser.contexts().get(0);
      final Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
      page.bringToFront();
      waitForUi(page);
      return new BrowserSession(browser, context, page, false);
    }

    final Browser browser =
        playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
    final BrowserContext context = browser.newContext();
    final Page page = context.newPage();
    final String loginUrl = envOrProperty(LOGIN_URL_KEY);
    if (loginUrl == null || loginUrl.isBlank()) {
      throw new IllegalStateException(
          "When " + CDP_URL_KEY + " is not provided, set " + LOGIN_URL_KEY + ".");
    }
    page.navigate(loginUrl);
    waitForUi(page);
    return new BrowserSession(browser, context, page, true);
  }

  private static void loginWithGoogle(final Page page) {
    final Locator loginButton =
        firstVisibleOrThrow(
            "Google login button",
            page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions()
                    .setName(Pattern.compile("(?i).*(Google|Iniciar sesi[oó]n|Sign in).*"))),
            page.getByRole(
                AriaRole.LINK,
                new Page.GetByRoleOptions()
                    .setName(Pattern.compile("(?i).*(Google|Iniciar sesi[oó]n|Sign in).*"))),
            page.getByText(Pattern.compile("(?i)(Sign in with Google|Iniciar sesi[oó]n con Google)")));
    clickAndWait(page, loginButton);

    // If account chooser appears, choose the required Google account.
    final Locator accountOption = page.getByText("juanlucasbarbiergarzon@gmail.com");
    if (isVisible(accountOption, SHORT_TIMEOUT_MS)) {
      clickAndWait(page, accountOption);
    }
  }

  private static void openMiNegocioMenu(final Page page) {
    expectVisible("Sidebar navigation", locateSidebar(page));
    expectVisible("Negocio section", page.getByText(Pattern.compile("(?i)Negocio")));
    final Locator menu =
        firstVisibleOrThrow(
            "Mi Negocio menu option",
            page.getByRole(AriaRole.BUTTON, named("Mi Negocio")),
            page.getByRole(AriaRole.LINK, named("Mi Negocio")),
            page.getByText("Mi Negocio"));
    clickAndWait(page, menu);
  }

  private static Locator locateAgregarNegocio(final Page page) {
    return firstVisibleOrThrow(
        "Agregar Negocio option",
        page.getByRole(AriaRole.BUTTON, named("Agregar Negocio")),
        page.getByRole(AriaRole.LINK, named("Agregar Negocio")),
        page.getByText("Agregar Negocio"));
  }

  private static Locator locateAdministrarNegocios(final Page page) {
    return firstVisibleOrThrow(
        "Administrar Negocios option",
        page.getByRole(AriaRole.BUTTON, named("Administrar Negocios")),
        page.getByRole(AriaRole.LINK, named("Administrar Negocios")),
        page.getByText("Administrar Negocios"));
  }

  private static String openAndValidateLegalPage(
      final BrowserContext context,
      final Page appPage,
      final String linkText,
      final String headingText,
      final String screenshotName) {
    final Locator link =
        firstVisibleOrThrow(
            linkText + " link",
            appPage.getByRole(AriaRole.LINK, named(linkText)),
            appPage.getByText(linkText));

    Page legalPage = appPage;
    boolean openedNewTab = false;
    try {
      legalPage =
          context.waitForPage(
              () -> link.first().click(new Locator.ClickOptions().setTimeout(UI_TIMEOUT_MS)),
              new BrowserContext.WaitForPageOptions().setTimeout(SHORT_TIMEOUT_MS));
      openedNewTab = true;
    } catch (PlaywrightException ignored) {
      clickAndWait(appPage, link);
    }

    waitForUi(legalPage);
    expectVisible(
        headingText + " heading",
        firstVisibleOrThrow(
            headingText + " heading",
            legalPage.getByRole(AriaRole.HEADING, named(headingText)),
            legalPage.getByText(headingText)));

    final String body = legalPage.locator("body").innerText();
    if (body == null || body.trim().length() < 80) {
      throw new AssertionError("Legal content text is too short for: " + headingText);
    }

    screenshot(legalPage, screenshotName, true);
    final String finalUrl = legalPage.url();

    if (openedNewTab && legalPage != appPage) {
      legalPage.close();
      appPage.bringToFront();
      waitForUi(appPage);
    } else if (!openedNewTab) {
      appPage.goBack();
      waitForUi(appPage);
    }
    return finalUrl;
  }

  private static Locator locateMainInterface(final Page page) {
    return firstVisibleOrThrow(
        "Main interface",
        page.locator("main"),
        page.locator("[role='main']"),
        page.locator("[class*='dashboard']"),
        page.locator("body"));
  }

  private static Locator locateSidebar(final Page page) {
    return firstVisibleOrThrow(
        "Sidebar",
        page.locator("aside"),
        page.getByRole(AriaRole.NAVIGATION),
        page.locator("[class*='sidebar']"));
  }

  private static Locator locateSection(final Page page, final String headingRegex) {
    final Locator heading = firstVisibleOrThrow("Section heading " + headingRegex,
        page.locator("text=/" + headingRegex + "/i"));
    final Locator sectionAncestor = heading.locator("xpath=ancestor::section[1]");
    if (isVisible(sectionAncestor, SHORT_TIMEOUT_MS)) {
      return sectionAncestor;
    }
    final Locator divAncestor = heading.locator("xpath=ancestor::div[1]");
    if (isVisible(divAncestor, SHORT_TIMEOUT_MS)) {
      return divAncestor;
    }
    return heading;
  }

  private static Locator locateEmailValue(final Locator within) {
    return firstVisibleOrThrow(
        "User email",
        within.getByText(Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")),
        within.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/"));
  }

  private static void clickAndWait(final Page page, final Locator locator) {
    locator.first().click(new Locator.ClickOptions().setTimeout(UI_TIMEOUT_MS));
    waitForUi(page);
  }

  private static void waitForUi(final Page page) {
    page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    try {
      page.waitForLoadState(
          LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(SHORT_TIMEOUT_MS));
    } catch (PlaywrightException ignored) {
      // Some pages keep long-lived connections; DOM content loaded is enough fallback.
    }
  }

  private static void screenshot(final Page page, final String filename, final boolean fullPage) {
    page.screenshot(
        new Page.ScreenshotOptions()
            .setPath(SCREENSHOT_DIR.resolve(filename))
            .setFullPage(fullPage));
  }

  private static Locator firstVisibleOrThrow(final String label, final Locator... options) {
    for (Locator option : options) {
      if (isVisible(option, SHORT_TIMEOUT_MS)) {
        return option.first();
      }
    }
    throw new AssertionError("Unable to find visible element: " + label);
  }

  private static boolean isVisible(final Locator locator, final double timeoutMs) {
    try {
      return locator.first().isVisible(new Locator.IsVisibleOptions().setTimeout(timeoutMs));
    } catch (PlaywrightException ignored) {
      return false;
    }
  }

  private static void expectVisible(final String label, final Locator locator) {
    if (!isVisible(locator, UI_TIMEOUT_MS)) {
      throw new AssertionError("Expected visible element: " + label);
    }
  }

  private static Map<String, StepResult> initReport() {
    final Map<String, StepResult> map = new LinkedHashMap<>();
    map.put("Login", StepResult.pending());
    map.put("Mi Negocio menu", StepResult.pending());
    map.put("Agregar Negocio modal", StepResult.pending());
    map.put("Administrar Negocios view", StepResult.pending());
    map.put("Información General", StepResult.pending());
    map.put("Detalles de la Cuenta", StepResult.pending());
    map.put("Tus Negocios", StepResult.pending());
    map.put("Términos y Condiciones", StepResult.pending());
    map.put("Política de Privacidad", StepResult.pending());
    return map;
  }

  private static void runStep(
      final Map<String, StepResult> report, final String stepName, final ThrowingRunnable action) {
    try {
      action.run();
      report.put(stepName, StepResult.pass("PASS"));
    } catch (Throwable error) {
      report.put(stepName, StepResult.fail(error.getMessage()));
    }
  }

  private static void writeReport(
      final Map<String, StepResult> report, final String termsUrl, final String privacyUrl)
      throws IOException {
    final StringBuilder out = new StringBuilder();
    out.append("saleads_mi_negocio_full_test final report\n");
    out.append("=======================================\n\n");
    for (Map.Entry<String, StepResult> entry : report.entrySet()) {
      out.append("- ")
          .append(entry.getKey())
          .append(": ")
          .append(entry.getValue().status)
          .append(" (")
          .append(entry.getValue().detail == null ? "-" : entry.getValue().detail)
          .append(")\n");
    }
    out.append("\nFinal URL - Términos y Condiciones: ").append(termsUrl).append('\n');
    out.append("Final URL - Política de Privacidad: ").append(privacyUrl).append('\n');
    Files.writeString(REPORT_FILE, out.toString(), StandardCharsets.UTF_8);
  }

  private static void assertAllPassed(final Map<String, StepResult> report) {
    final StringBuilder failures = new StringBuilder();
    for (Map.Entry<String, StepResult> entry : report.entrySet()) {
      if (!"PASS".equals(entry.getValue().status)) {
        failures
            .append("\n- ")
            .append(entry.getKey())
            .append(": ")
            .append(entry.getValue().status)
            .append(" (")
            .append(entry.getValue().detail)
            .append(")");
      }
    }
    Assert.assertTrue(
        "One or more validations failed. Report path: " + REPORT_FILE + failures,
        failures.length() == 0);
  }

  private static Page.GetByRoleOptions named(final String text) {
    return new Page.GetByRoleOptions().setName(text);
  }

  private static String envOrProperty(final String key) {
    final String propValue = System.getProperty(key);
    if (propValue != null && !propValue.isBlank()) {
      return propValue;
    }
    final String envValue = System.getenv(key);
    return (envValue == null || envValue.isBlank()) ? null : envValue;
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  private static final class BrowserSession {
    private final Browser browser;
    private final BrowserContext context;
    private final Page page;
    private final boolean closeBrowserOnExit;

    private BrowserSession(
        final Browser browser,
        final BrowserContext context,
        final Page page,
        final boolean closeBrowserOnExit) {
      this.browser = browser;
      this.context = context;
      this.page = page;
      this.closeBrowserOnExit = closeBrowserOnExit;
    }

    private void close() {
      if (closeBrowserOnExit) {
        browser.close();
      }
    }
  }

  private static final class StepResult {
    private final String status;
    private final String detail;

    private StepResult(final String status, final String detail) {
      this.status = status;
      this.detail = detail;
    }

    private static StepResult pending() {
      return new StepResult("NOT_RUN", null);
    }

    private static StepResult pass(final String detail) {
      return new StepResult("PASS", detail);
    }

    private static StepResult fail(final String detail) {
      return new StepResult("FAIL", detail);
    }
  }
}
