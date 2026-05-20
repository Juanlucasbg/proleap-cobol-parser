package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.Assume;
import org.junit.Test;

/**
 * End-to-end test for the SaleADS "Mi Negocio" workflow.
 *
 * <p>Run only when explicitly enabled:
 *
 * <ul>
 *   <li>Property: -Dsaleads.e2e.enabled=true
 *   <li>Environment: SALEADS_LOGIN_URL (or -Dsaleads.login.url=...)
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

  private static final Pattern GOOGLE_LOGIN_TEXT =
      Pattern.compile("(?i)(sign\\s*in\\s*with\\s*google|iniciar\\s*sesi[o\\u00F3]n\\s*con\\s*google|continuar\\s*con\\s*google)");

  private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

  private static final Pattern USER_NAME_PATTERN =
      Pattern.compile("(?i)(juan\\s*lucas|juanlucasbarbiergarzon)");

  private static final String REPORT_LOGIN = "Login";
  private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
  private static final String REPORT_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
  private static final String REPORT_ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
  private static final String REPORT_INFORMACION_GENERAL = "Informacion General";
  private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
  private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
  private static final String REPORT_TERMINOS = "Terminos y Condiciones";
  private static final String REPORT_POLITICA = "Politica de Privacidad";

  private static final DateTimeFormatter TS_FORMATTER =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

  @FunctionalInterface
  private interface StepAction {
    void run() throws Exception;
  }

  @Test
  public void saleadsMiNegocioFullWorkflow() throws IOException {
    final boolean enabled =
        Boolean.parseBoolean(
            firstNonBlank(System.getProperty("saleads.e2e.enabled"), System.getenv("SALEADS_E2E_ENABLED"), "false"));
    Assume.assumeTrue("Enable with -Dsaleads.e2e.enabled=true", enabled);

    final String loginUrl =
        firstNonBlank(System.getProperty("saleads.login.url"), System.getenv("SALEADS_LOGIN_URL"));
    Assume.assumeTrue("Provide SALEADS_LOGIN_URL or -Dsaleads.login.url", loginUrl != null && !loginUrl.isBlank());

    final Map<String, String> stepResults = initReportMap();
    final Map<String, String> evidenceUrls = new LinkedHashMap<>();
    final String runTs = TS_FORMATTER.format(Instant.now());
    final Path evidenceDir = Path.of("target", "saleads-mi-negocio-evidence", runTs);
    Files.createDirectories(evidenceDir);

    try (Playwright playwright = Playwright.create()) {
      final boolean headed =
          Boolean.parseBoolean(
              firstNonBlank(System.getProperty("saleads.headed"), System.getenv("SALEADS_HEADED"), "false"));
      final Browser browser =
          playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(!headed));
      final BrowserContext context =
          browser.newContext(new Browser.NewContextOptions().setViewportSize(1920, 1080));
      final Page appPage = context.newPage();

      appPage.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
      waitForUi(appPage);

      final boolean loginOk =
          runStep(
              REPORT_LOGIN,
              stepResults,
              () -> {
                stepLoginWithGoogle(appPage);
                ensureMainInterfaceVisible(appPage);
                screenshot(appPage, evidenceDir.resolve("01-dashboard-loaded.png"), false);
              });

      final boolean menuOk =
          runStep(
              REPORT_MI_NEGOCIO_MENU,
              stepResults,
              () -> {
                requirePrerequisite(loginOk, REPORT_LOGIN);
                expandMiNegocioMenu(appPage);
                assertVisibleText(appPage, Pattern.compile("(?i)agregar\\s*negocio"), "Agregar Negocio");
                assertVisibleText(appPage, Pattern.compile("(?i)administrar\\s*negocios"), "Administrar Negocios");
                screenshot(appPage, evidenceDir.resolve("02-mi-negocio-menu-expanded.png"), false);
              });

      final boolean modalOk =
          runStep(
              REPORT_AGREGAR_NEGOCIO_MODAL,
              stepResults,
              () -> {
                requirePrerequisite(menuOk, REPORT_MI_NEGOCIO_MENU);
                clickByVisibleText(appPage, Pattern.compile("(?i)agregar\\s*negocio"));
                assertVisibleText(appPage, Pattern.compile("(?i)crear\\s*nuevo\\s*negocio"), "Crear Nuevo Negocio");
                final Locator nombreNegocioInput =
                    findVisible(
                        appPage,
                        "Nombre del Negocio input",
                        appPage.getByRole(
                            AriaRole.TEXTBOX,
                            new Page.GetByRoleOptions().setName(Pattern.compile("(?i)nombre\\s*del\\s*negocio"))),
                        appPage.locator("input[name*='negocio' i], input[placeholder*='Negocio' i], input"));
                nombreNegocioInput.fill("Negocio Prueba Automatizacion");
                assertVisibleText(appPage, Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios"), "Tienes 2 de 3 negocios");
                assertVisibleText(appPage, Pattern.compile("(?i)cancelar"), "Cancelar");
                assertVisibleText(appPage, Pattern.compile("(?i)crear\\s*negocio"), "Crear Negocio");
                screenshot(appPage, evidenceDir.resolve("03-agregar-negocio-modal.png"), false);
                clickByVisibleText(appPage, Pattern.compile("(?i)^cancelar$"));
              });

      final boolean administrarOk =
          runStep(
              REPORT_ADMINISTRAR_NEGOCIOS_VIEW,
              stepResults,
              () -> {
                requirePrerequisite(modalOk, REPORT_AGREGAR_NEGOCIO_MODAL);
                if (!isVisible(appPage.getByText(Pattern.compile("(?i)administrar\\s*negocios")))) {
                  expandMiNegocioMenu(appPage);
                }
                clickByVisibleText(appPage, Pattern.compile("(?i)administrar\\s*negocios"));
                assertVisibleText(appPage, Pattern.compile("(?i)informaci[o\\u00F3]n\\s*general"), "Informacion General");
                assertVisibleText(appPage, Pattern.compile("(?i)detalles\\s*de\\s*la\\s*cuenta"), "Detalles de la Cuenta");
                assertVisibleText(appPage, Pattern.compile("(?i)tus\\s*negocios"), "Tus Negocios");
                assertVisibleText(appPage, Pattern.compile("(?i)secci[o\\u00F3]n\\s*legal"), "Seccion Legal");
                screenshot(appPage, evidenceDir.resolve("04-administrar-negocios-view.png"), true);
              });

      final boolean informacionGeneralOk =
          runStep(
              REPORT_INFORMACION_GENERAL,
              stepResults,
              () -> {
                requirePrerequisite(administrarOk, REPORT_ADMINISTRAR_NEGOCIOS_VIEW);
                assertVisibleText(appPage, USER_NAME_PATTERN, "User name");
                assertVisibleText(appPage, Pattern.compile(Pattern.quote(ACCOUNT_EMAIL), Pattern.CASE_INSENSITIVE), "User email");
                assertVisibleText(appPage, Pattern.compile("(?i)business\\s*plan"), "BUSINESS PLAN");
                assertVisibleText(appPage, Pattern.compile("(?i)cambiar\\s*plan"), "Cambiar Plan");
              });

      final boolean detallesCuentaOk =
          runStep(
              REPORT_DETALLES_CUENTA,
              stepResults,
              () -> {
                requirePrerequisite(administrarOk, REPORT_ADMINISTRAR_NEGOCIOS_VIEW);
                assertVisibleText(appPage, Pattern.compile("(?i)cuenta\\s*creada"), "Cuenta creada");
                assertVisibleText(appPage, Pattern.compile("(?i)estado\\s*activo"), "Estado activo");
                assertVisibleText(appPage, Pattern.compile("(?i)idioma\\s*seleccionado"), "Idioma seleccionado");
              });

      final boolean tusNegociosOk =
          runStep(
              REPORT_TUS_NEGOCIOS,
              stepResults,
              () -> {
                requirePrerequisite(administrarOk, REPORT_ADMINISTRAR_NEGOCIOS_VIEW);
                assertVisibleText(appPage, Pattern.compile("(?i)tus\\s*negocios"), "Tus Negocios");
                assertVisibleText(appPage, Pattern.compile("(?i)agregar\\s*negocio"), "Agregar Negocio");
                assertVisibleText(appPage, Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios"), "Tienes 2 de 3 negocios");
                assertBusinessListVisible(appPage);
              });

      runStep(
          REPORT_TERMINOS,
          stepResults,
          () -> {
            requirePrerequisite(detallesCuentaOk && tusNegociosOk && informacionGeneralOk, "Account page sections");
            final String url =
                validateLegalPage(
                    appPage,
                    Pattern.compile("(?i)t[e\\u00E9]rminos\\s*y\\s*condiciones"),
                    Pattern.compile("(?i)t[e\\u00E9]rminos\\s*y\\s*condiciones"),
                    evidenceDir.resolve("05-terminos-y-condiciones.png"));
            evidenceUrls.put(REPORT_TERMINOS, url);
          });

      runStep(
          REPORT_POLITICA,
          stepResults,
          () -> {
            requirePrerequisite(detallesCuentaOk && tusNegociosOk && informacionGeneralOk, "Account page sections");
            final String url =
                validateLegalPage(
                    appPage,
                    Pattern.compile("(?i)pol[i\\u00ED]tica\\s*de\\s*privacidad"),
                    Pattern.compile("(?i)pol[i\\u00ED]tica\\s*de\\s*privacidad"),
                    evidenceDir.resolve("06-politica-de-privacidad.png"));
            evidenceUrls.put(REPORT_POLITICA, url);
          });
    }

    final Path reportPath = evidenceDir.resolve("final-report.json");
    Files.writeString(reportPath, buildReportJson(stepResults, evidenceUrls, evidenceDir), StandardCharsets.UTF_8);
    assertAllPass(stepResults, reportPath);
  }

  private static void stepLoginWithGoogle(final Page appPage) {
    final Locator loginButton =
        findVisible(
            appPage,
            "Google login button",
            appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(GOOGLE_LOGIN_TEXT)),
            appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(GOOGLE_LOGIN_TEXT)),
            appPage.getByText(GOOGLE_LOGIN_TEXT));

    final PopupResult popupResult = clickWithOptionalPopup(appPage, loginButton);
    final Page authPage = popupResult.popupPage != null ? popupResult.popupPage : appPage;

    final Locator accountChoice =
        authPage.getByText(ACCOUNT_EMAIL, new Page.GetByTextOptions().setExact(false));
    if (isVisible(accountChoice)) {
      clickAndWait(authPage, accountChoice.first());
    }

    if (popupResult.popupPage != null) {
      waitForUi(appPage);
      if (!popupResult.popupPage.isClosed()) {
        popupResult.popupPage.waitForTimeout(500);
      }
    }
    waitForUi(appPage);
  }

  private static void ensureMainInterfaceVisible(final Page page) {
    final Locator mainShell =
        findVisible(
            page,
            "Main application interface",
            page.getByRole(AriaRole.MAIN),
            page.locator("main"),
            page.getByText(Pattern.compile("(?i)(dashboard|inicio|panel|negocio)")));
    assertTrue("Main interface is not visible", mainShell.isVisible());

    final Locator sidebar =
        findVisible(
            page,
            "Left sidebar navigation",
            page.getByRole(AriaRole.NAVIGATION),
            page.getByText(Pattern.compile("(?i)negocio")));
    assertTrue("Left sidebar navigation is not visible", sidebar.isVisible());
  }

  private static void expandMiNegocioMenu(final Page page) {
    clickByVisibleText(page, Pattern.compile("(?i)mi\\s*negocio"));
    waitForUi(page);
  }

  private static void assertBusinessListVisible(final Page page) {
    final Locator businessItems =
        page.locator("[role='listitem'], li, tr").filter(new Locator.FilterOptions().setHasText(Pattern.compile("(?i)negocio")));
    assertTrue("Business list is not visible", businessItems.count() > 0);
  }

  private static String validateLegalPage(
      final Page appPage, final Pattern linkText, final Pattern expectedHeading, final Path screenshotPath) {
    final String returnUrl = appPage.url();
    final Locator legalLink =
        findVisible(
            appPage,
            "Legal link: " + linkText.pattern(),
            appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkText)),
            appPage.getByText(linkText));

    final PopupResult popupResult = clickWithOptionalPopup(appPage, legalLink);
    final Page legalPage = popupResult.popupPage != null ? popupResult.popupPage : appPage;
    waitForUi(legalPage);

    assertVisibleText(legalPage, expectedHeading, "Legal heading");
    final String legalBody = legalPage.locator("body").innerText();
    assertTrue("Legal content text is not visible", legalBody != null && legalBody.trim().length() > 100);
    screenshot(legalPage, screenshotPath, true);

    final String finalUrl = legalPage.url();
    if (popupResult.popupPage != null) {
      popupResult.popupPage.close();
      appPage.bringToFront();
      waitForUi(appPage);
    } else {
      try {
        appPage.goBack(new Page.GoBackOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
      } catch (RuntimeException ignored) {
        appPage.navigate(returnUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
      }
      waitForUi(appPage);
    }
    return finalUrl;
  }

  private static PopupResult clickWithOptionalPopup(final Page page, final Locator locator) {
    try {
      final Page popup =
          page.waitForPopup(
              new Page.WaitForPopupOptions().setTimeout(5000),
              () -> {
                clickAndWait(page, locator);
              });
      waitForUi(popup);
      return new PopupResult(popup);
    } catch (TimeoutError timeoutError) {
      waitForUi(page);
      return new PopupResult(null);
    }
  }

  private static void clickByVisibleText(final Page page, final Pattern textPattern) {
    final Locator clickable =
        findVisible(
            page,
            "Clickable text: " + textPattern.pattern(),
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(textPattern)),
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(textPattern)),
            page.getByText(textPattern));
    clickAndWait(page, clickable);
  }

  private static void clickAndWait(final Page page, final Locator locator) {
    locator.first().click();
    waitForUi(page);
  }

  private static void waitForUi(final Page page) {
    page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
    } catch (TimeoutError ignored) {
      // Some views keep background network activity alive; DOM loaded is enough for assertions.
    }
    page.waitForTimeout(400);
  }

  private static void screenshot(final Page page, final Path path, final boolean fullPage) {
    page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
  }

  private static void assertVisibleText(final Page page, final Pattern pattern, final String description) {
    final Locator locator = findVisible(page, description, page.getByText(pattern));
    locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
  }

  private static Locator findVisible(final Page page, final String description, final Locator... options) {
    final long deadline = System.currentTimeMillis() + 10000;
    while (System.currentTimeMillis() < deadline) {
      for (final Locator option : options) {
        if (isVisible(option)) {
          return option.first();
        }
      }
      page.waitForTimeout(200);
    }
    throw new AssertionError("Could not find visible element for: " + description);
  }

  private static boolean isVisible(final Locator locator) {
    try {
      return locator != null && locator.count() > 0 && locator.first().isVisible();
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  private static void requirePrerequisite(final boolean condition, final String prerequisiteName) {
    if (!condition) {
      throw new IllegalStateException("Prerequisite failed: " + prerequisiteName);
    }
  }

  private static boolean runStep(
      final String stepName, final Map<String, String> stepResults, final StepAction action) {
    try {
      action.run();
      stepResults.put(stepName, "PASS");
      return true;
    } catch (Throwable throwable) {
      final String reason =
          throwable.getMessage() == null || throwable.getMessage().isBlank()
              ? throwable.getClass().getSimpleName()
              : throwable.getMessage().replaceAll("\\s+", " ").trim();
      stepResults.put(stepName, "FAIL: " + reason);
      return false;
    }
  }

  private static void assertAllPass(final Map<String, String> stepResults, final Path reportPath) {
    final StringBuilder failures = new StringBuilder();
    for (final Map.Entry<String, String> entry : stepResults.entrySet()) {
      if (!entry.getValue().startsWith("PASS")) {
        if (failures.length() > 0) {
          failures.append("; ");
        }
        failures.append(entry.getKey()).append(" => ").append(entry.getValue());
      }
    }
    assertTrue("Final report contains failures (" + reportPath + "): " + failures, failures.length() == 0);
  }

  private static Map<String, String> initReportMap() {
    final Map<String, String> map = new LinkedHashMap<>();
    map.put(REPORT_LOGIN, "FAIL: Not executed");
    map.put(REPORT_MI_NEGOCIO_MENU, "FAIL: Not executed");
    map.put(REPORT_AGREGAR_NEGOCIO_MODAL, "FAIL: Not executed");
    map.put(REPORT_ADMINISTRAR_NEGOCIOS_VIEW, "FAIL: Not executed");
    map.put(REPORT_INFORMACION_GENERAL, "FAIL: Not executed");
    map.put(REPORT_DETALLES_CUENTA, "FAIL: Not executed");
    map.put(REPORT_TUS_NEGOCIOS, "FAIL: Not executed");
    map.put(REPORT_TERMINOS, "FAIL: Not executed");
    map.put(REPORT_POLITICA, "FAIL: Not executed");
    return map;
  }

  private static String buildReportJson(
      final Map<String, String> stepResults, final Map<String, String> evidenceUrls, final Path evidenceDir) {
    final StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"generatedAt\": \"").append(Instant.now()).append("\",\n");
    json.append("  \"evidenceDirectory\": \"").append(escapeJson(evidenceDir.toString())).append("\",\n");
    json.append("  \"results\": {\n");
    int index = 0;
    for (final Map.Entry<String, String> entry : stepResults.entrySet()) {
      json.append("    \"").append(escapeJson(entry.getKey())).append("\": \"").append(escapeJson(entry.getValue())).append("\"");
      if (index < stepResults.size() - 1) {
        json.append(",");
      }
      json.append("\n");
      index++;
    }
    json.append("  },\n");
    json.append("  \"finalUrls\": {\n");
    int urlIndex = 0;
    for (final Map.Entry<String, String> entry : evidenceUrls.entrySet()) {
      json.append("    \"").append(escapeJson(entry.getKey())).append("\": \"").append(escapeJson(entry.getValue())).append("\"");
      if (urlIndex < evidenceUrls.size() - 1) {
        json.append(",");
      }
      json.append("\n");
      urlIndex++;
    }
    json.append("  }\n");
    json.append("}\n");
    return json.toString();
  }

  private static String escapeJson(final String input) {
    return input.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static String firstNonBlank(final String... values) {
    for (final String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private static final class PopupResult {
    private final Page popupPage;

    private PopupResult(final Page popupPage) {
      this.popupPage = popupPage;
    }
  }
}
