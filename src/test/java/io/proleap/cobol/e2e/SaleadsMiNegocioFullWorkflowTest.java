package io.proleap.cobol.e2e;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

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

/**
 * End-to-end workflow test for SaleADS "Mi Negocio" module.
 *
 * Runtime configuration:
 * - SALEADS_BASE_URL or -Dsaleads.baseUrl : login page URL for current environment.
 * - SALEADS_GOOGLE_ACCOUNT or -Dsaleads.googleAccount : Google account to select.
 * - SALEADS_HEADLESS or -Dsaleads.headless : true/false (defaults to true).
 */
public class SaleadsMiNegocioFullWorkflowTest {

  private static final Pattern LOGIN_BUTTON_PATTERN = Pattern
      .compile("(?i)(sign in|iniciar sesi[oó]n|continuar|acceder).{0,30}google|google");
  private static final Pattern NEGOCIO_PATTERN = Pattern.compile("(?i)^negocio$");
  private static final Pattern MI_NEGOCIO_PATTERN = Pattern.compile("(?i)mi\\s+negocio");
  private static final Pattern AGREGAR_NEGOCIO_PATTERN = Pattern.compile("(?i)agregar\\s+negocio");
  private static final Pattern ADMINISTRAR_NEGOCIOS_PATTERN = Pattern.compile("(?i)administrar\\s+negocios");
  private static final Pattern CREAR_NUEVO_NEGOCIO_PATTERN = Pattern.compile("(?i)crear\\s+nuevo\\s+negocio");
  private static final Pattern NOMBRE_NEGOCIO_PATTERN = Pattern.compile("(?i)nombre\\s+del\\s+negocio");
  private static final Pattern NEGOCIOS_LIMIT_PATTERN = Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios");
  private static final Pattern INFORMACION_GENERAL_PATTERN = Pattern.compile("(?i)informaci[oó]n\\s+general");
  private static final Pattern DETALLES_CUENTA_PATTERN = Pattern.compile("(?i)detalles\\s+de\\s+la\\s+cuenta");
  private static final Pattern TUS_NEGOCIOS_PATTERN = Pattern.compile("(?i)tus\\s+negocios");
  private static final Pattern SECCION_LEGAL_PATTERN = Pattern.compile("(?i)secci[oó]n\\s+legal");
  private static final Pattern BUSINESS_PLAN_PATTERN = Pattern.compile("(?i)business\\s+plan");
  private static final Pattern CAMBIAR_PLAN_PATTERN = Pattern.compile("(?i)cambiar\\s+plan");
  private static final Pattern CUENTA_CREADA_PATTERN = Pattern.compile("(?i)cuenta\\s+creada");
  private static final Pattern ESTADO_ACTIVO_PATTERN = Pattern.compile("(?i)estado\\s+activo");
  private static final Pattern IDIOMA_SELECCIONADO_PATTERN = Pattern.compile("(?i)idioma\\s+seleccionado");
  private static final Pattern TERMINOS_PATTERN = Pattern.compile("(?i)t[eé]rminos\\s+y\\s+condiciones");
  private static final Pattern POLITICA_PATTERN = Pattern.compile("(?i)pol[ií]tica\\s+de\\s+privacidad");
  private static final long SHORT_TIMEOUT_MS = 10_000;
  private static final long DEFAULT_TIMEOUT_MS = 25_000;

  @Test
  public void saleadsMiNegocioFullTest() throws IOException {
    final String baseUrl = firstNonBlank(System.getenv("SALEADS_BASE_URL"), System.getProperty("saleads.baseUrl"));
    Assume.assumeTrue("Set SALEADS_BASE_URL or -Dsaleads.baseUrl to run this workflow test.",
        baseUrl != null && !baseUrl.isBlank());

    final String googleAccount = firstNonBlank(System.getenv("SALEADS_GOOGLE_ACCOUNT"),
        System.getProperty("saleads.googleAccount"), "juanlucasbarbiergarzon@gmail.com");
    final boolean headless = Boolean.parseBoolean(firstNonBlank(System.getenv("SALEADS_HEADLESS"),
        System.getProperty("saleads.headless"), "true"));

    final Path evidenceDir = Paths.get("target", "saleads-mi-negocio-evidence");
    Files.createDirectories(evidenceDir);

    final LinkedHashMap<String, Boolean> report = new LinkedHashMap<>();
    final List<String> failures = new ArrayList<>();
    final Map<String, String> urls = new LinkedHashMap<>();
    urls.put("generatedAt", Instant.now().toString());

    try (Playwright playwright = Playwright.create()) {
      final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
      final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1200));
      final Page appPage = context.newPage();

      appPage.navigate(baseUrl);
      waitForUi(appPage);

      report.put("Login", stepLogin(appPage, context, googleAccount, evidenceDir, failures));
      report.put("Mi Negocio menu", stepOpenMiNegocioMenu(appPage, evidenceDir, failures));
      report.put("Agregar Negocio modal", stepAgregarNegocioModal(appPage, evidenceDir, failures));
      report.put("Administrar Negocios view", stepOpenAdministrarNegocios(appPage, evidenceDir, failures));
      report.put("Información General", stepValidateInformacionGeneral(appPage, failures));
      report.put("Detalles de la Cuenta", stepValidateDetallesCuenta(appPage, failures));
      report.put("Tus Negocios", stepValidateTusNegocios(appPage, failures));

      final LegalStepResult terminos = stepValidateLegalLink(appPage, context, evidenceDir, TERMINOS_PATTERN,
          "08-terminos-y-condiciones.png");
      report.put("Términos y Condiciones", terminos.passed);
      urls.put("terminosYCondicionesUrl", terminos.finalUrl);
      if (!terminos.passed) {
        failures.add("Términos y Condiciones validation failed.");
      }

      final LegalStepResult politica = stepValidateLegalLink(appPage, context, evidenceDir, POLITICA_PATTERN,
          "09-politica-de-privacidad.png");
      report.put("Política de Privacidad", politica.passed);
      urls.put("politicaDePrivacidadUrl", politica.finalUrl);
      if (!politica.passed) {
        failures.add("Política de Privacidad validation failed.");
      }

      writeFinalReport(evidenceDir.resolve("10-final-report.txt"), report, urls, failures);
      browser.close();
    }

    Assert.assertTrue("One or more validations failed. See target/saleads-mi-negocio-evidence/10-final-report.txt",
        failures.isEmpty());
  }

  private boolean stepLogin(final Page appPage, final BrowserContext context, final String googleAccount,
      final Path evidenceDir, final List<String> failures) {
    try {
      final Locator loginTrigger = findLoginTrigger(appPage);
      Page popup = null;
      try {
        popup = context.waitForPage(() -> clickLocator(loginTrigger), new BrowserContext.WaitForPageOptions()
            .setTimeout(SHORT_TIMEOUT_MS));
      } catch (final PlaywrightException noPopupExpected) {
        clickLocator(loginTrigger);
      }

      waitForUi(appPage);
      if (popup != null) {
        waitForUi(popup);
        maybeSelectGoogleAccount(popup, googleAccount);
        try {
          popup.waitForClose(new Page.WaitForCloseOptions().setTimeout(DEFAULT_TIMEOUT_MS));
        } catch (final PlaywrightException ignored) {
          // Popup may stay open in some auth flows.
        }
      } else {
        maybeSelectGoogleAccount(appPage, googleAccount);
      }

      waitForUi(appPage);
      final boolean mainInterfaceVisible = visibleByAnyText(appPage,
          List.of(MI_NEGOCIO_PATTERN, INFORMACION_GENERAL_PATTERN, DETALLES_CUENTA_PATTERN));
      final boolean leftSidebarVisible = visibleByAnyText(appPage,
          List.of(NEGOCIO_PATTERN, MI_NEGOCIO_PATTERN, AGREGAR_NEGOCIO_PATTERN));
      screenshot(appPage, evidenceDir.resolve("01-dashboard-loaded.png"), false);

      if (!mainInterfaceVisible) {
        failures.add("Login: main application interface is not visible.");
      }
      if (!leftSidebarVisible) {
        failures.add("Login: left sidebar navigation is not visible.");
      }
      return mainInterfaceVisible && leftSidebarVisible;
    } catch (final Exception e) {
      failures.add("Login failed: " + e.getMessage());
      return false;
    }
  }

  private boolean stepOpenMiNegocioMenu(final Page appPage, final Path evidenceDir, final List<String> failures) {
    try {
      clickVisibleText(appPage, NEGOCIO_PATTERN);
      clickVisibleText(appPage, MI_NEGOCIO_PATTERN);
      waitForUi(appPage);

      final boolean agregarVisible = visibleByText(appPage, AGREGAR_NEGOCIO_PATTERN);
      final boolean administrarVisible = visibleByText(appPage, ADMINISTRAR_NEGOCIOS_PATTERN);
      screenshot(appPage, evidenceDir.resolve("02-mi-negocio-menu-expanded.png"), false);

      if (!agregarVisible) {
        failures.add("Mi Negocio menu: 'Agregar Negocio' is not visible.");
      }
      if (!administrarVisible) {
        failures.add("Mi Negocio menu: 'Administrar Negocios' is not visible.");
      }
      return agregarVisible && administrarVisible;
    } catch (final Exception e) {
      failures.add("Mi Negocio menu failed: " + e.getMessage());
      return false;
    }
  }

  private boolean stepAgregarNegocioModal(final Page appPage, final Path evidenceDir, final List<String> failures) {
    try {
      clickVisibleText(appPage, AGREGAR_NEGOCIO_PATTERN);
      waitForUi(appPage);

      final boolean modalTitleVisible = visibleByText(appPage, CREAR_NUEVO_NEGOCIO_PATTERN);
      final boolean inputVisible = visibleByLabelOrPlaceholder(appPage, NOMBRE_NEGOCIO_PATTERN);
      final boolean limitVisible = visibleByText(appPage, NEGOCIOS_LIMIT_PATTERN);
      final boolean cancelVisible = visibleByText(appPage, Pattern.compile("(?i)cancelar"));
      final boolean createVisible = visibleByText(appPage, Pattern.compile("(?i)crear\\s+negocio"));

      final Locator input = findByLabelOrPlaceholder(appPage, NOMBRE_NEGOCIO_PATTERN);
      if (input.count() > 0) {
        input.first().click();
        input.first().fill("Negocio Prueba Automatización");
        waitForUi(appPage);
      }

      screenshot(appPage, evidenceDir.resolve("03-agregar-negocio-modal.png"), false);
      if (cancelVisible) {
        clickVisibleText(appPage, Pattern.compile("(?i)cancelar"));
      } else {
        appPage.keyboard().press("Escape");
      }
      waitForUi(appPage);

      if (!modalTitleVisible) {
        failures.add("Agregar Negocio modal: title 'Crear Nuevo Negocio' is not visible.");
      }
      if (!inputVisible) {
        failures.add("Agregar Negocio modal: input 'Nombre del Negocio' is not visible.");
      }
      if (!limitVisible) {
        failures.add("Agregar Negocio modal: 'Tienes 2 de 3 negocios' is not visible.");
      }
      if (!cancelVisible) {
        failures.add("Agregar Negocio modal: button 'Cancelar' is not visible.");
      }
      if (!createVisible) {
        failures.add("Agregar Negocio modal: button 'Crear Negocio' is not visible.");
      }
      return modalTitleVisible && inputVisible && limitVisible && cancelVisible && createVisible;
    } catch (final Exception e) {
      failures.add("Agregar Negocio modal failed: " + e.getMessage());
      return false;
    }
  }

  private boolean stepOpenAdministrarNegocios(final Page appPage, final Path evidenceDir, final List<String> failures) {
    try {
      if (!visibleByText(appPage, ADMINISTRAR_NEGOCIOS_PATTERN)) {
        clickVisibleText(appPage, MI_NEGOCIO_PATTERN);
        waitForUi(appPage);
      }
      clickVisibleText(appPage, ADMINISTRAR_NEGOCIOS_PATTERN);
      waitForUi(appPage);

      final boolean infoGeneralVisible = visibleByText(appPage, INFORMACION_GENERAL_PATTERN);
      final boolean detallesVisible = visibleByText(appPage, DETALLES_CUENTA_PATTERN);
      final boolean tusNegociosVisible = visibleByText(appPage, TUS_NEGOCIOS_PATTERN);
      final boolean legalVisible = visibleByText(appPage, SECCION_LEGAL_PATTERN);
      screenshot(appPage, evidenceDir.resolve("04-administrar-negocios.png"), true);

      if (!infoGeneralVisible) {
        failures.add("Administrar Negocios view: 'Información General' is not visible.");
      }
      if (!detallesVisible) {
        failures.add("Administrar Negocios view: 'Detalles de la Cuenta' is not visible.");
      }
      if (!tusNegociosVisible) {
        failures.add("Administrar Negocios view: 'Tus Negocios' is not visible.");
      }
      if (!legalVisible) {
        failures.add("Administrar Negocios view: 'Sección Legal' is not visible.");
      }
      return infoGeneralVisible && detallesVisible && tusNegociosVisible && legalVisible;
    } catch (final Exception e) {
      failures.add("Administrar Negocios view failed: " + e.getMessage());
      return false;
    }
  }

  private boolean stepValidateInformacionGeneral(final Page appPage, final List<String> failures) {
    try {
      final boolean userNameVisible = visibleByAnyText(appPage,
          List.of(Pattern.compile("(?i)bienvenido"), Pattern.compile("^[A-Za-z].{2,}$")));
      final boolean userEmailVisible = visibleByRegex(appPage,
          Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[A-Za-z]{2,}"));
      final boolean planVisible = visibleByText(appPage, BUSINESS_PLAN_PATTERN);
      final boolean changePlanVisible = visibleByText(appPage, CAMBIAR_PLAN_PATTERN);

      if (!userNameVisible) {
        failures.add("Información General: user name is not visible.");
      }
      if (!userEmailVisible) {
        failures.add("Información General: user email is not visible.");
      }
      if (!planVisible) {
        failures.add("Información General: 'BUSINESS PLAN' is not visible.");
      }
      if (!changePlanVisible) {
        failures.add("Información General: 'Cambiar Plan' button is not visible.");
      }
      return userNameVisible && userEmailVisible && planVisible && changePlanVisible;
    } catch (final Exception e) {
      failures.add("Información General validation failed: " + e.getMessage());
      return false;
    }
  }

  private boolean stepValidateDetallesCuenta(final Page appPage, final List<String> failures) {
    try {
      final boolean cuentaCreadaVisible = visibleByText(appPage, CUENTA_CREADA_PATTERN);
      final boolean estadoActivoVisible = visibleByText(appPage, ESTADO_ACTIVO_PATTERN);
      final boolean idiomaVisible = visibleByText(appPage, IDIOMA_SELECCIONADO_PATTERN);

      if (!cuentaCreadaVisible) {
        failures.add("Detalles de la Cuenta: 'Cuenta creada' is not visible.");
      }
      if (!estadoActivoVisible) {
        failures.add("Detalles de la Cuenta: 'Estado activo' is not visible.");
      }
      if (!idiomaVisible) {
        failures.add("Detalles de la Cuenta: 'Idioma seleccionado' is not visible.");
      }
      return cuentaCreadaVisible && estadoActivoVisible && idiomaVisible;
    } catch (final Exception e) {
      failures.add("Detalles de la Cuenta validation failed: " + e.getMessage());
      return false;
    }
  }

  private boolean stepValidateTusNegocios(final Page appPage, final List<String> failures) {
    try {
      final boolean listVisible = visibleByText(appPage, TUS_NEGOCIOS_PATTERN);
      final boolean addBusinessVisible = visibleByText(appPage, AGREGAR_NEGOCIO_PATTERN);
      final boolean limitVisible = visibleByText(appPage, NEGOCIOS_LIMIT_PATTERN);

      if (!listVisible) {
        failures.add("Tus Negocios: business list section is not visible.");
      }
      if (!addBusinessVisible) {
        failures.add("Tus Negocios: button 'Agregar Negocio' is not visible.");
      }
      if (!limitVisible) {
        failures.add("Tus Negocios: text 'Tienes 2 de 3 negocios' is not visible.");
      }
      return listVisible && addBusinessVisible && limitVisible;
    } catch (final Exception e) {
      failures.add("Tus Negocios validation failed: " + e.getMessage());
      return false;
    }
  }

  private LegalStepResult stepValidateLegalLink(final Page appPage, final BrowserContext context, final Path evidenceDir,
      final Pattern linkTextPattern, final String screenshotName) {
    Page legalPage = null;
    boolean openedNewTab = false;
    String finalUrl = "";

    try {
      if (visibleByText(appPage, SECCION_LEGAL_PATTERN)) {
        appPage.getByText(SECCION_LEGAL_PATTERN).first().scrollIntoViewIfNeeded();
      }

      try {
        legalPage = context.waitForPage(() -> clickVisibleText(appPage, linkTextPattern), new BrowserContext.WaitForPageOptions()
            .setTimeout(SHORT_TIMEOUT_MS));
        openedNewTab = true;
      } catch (final PlaywrightException noPopupExpected) {
        clickVisibleText(appPage, linkTextPattern);
        legalPage = appPage;
      }

      waitForUi(legalPage);
      final boolean headingVisible = visibleByText(legalPage, linkTextPattern);
      final String bodyText = Optional.ofNullable(legalPage.locator("body").first().innerText()).orElse("").trim();
      final boolean legalContentVisible = bodyText.length() > 120;
      finalUrl = legalPage.url();
      screenshot(legalPage, evidenceDir.resolve(screenshotName), true);

      if (openedNewTab) {
        legalPage.close();
        appPage.bringToFront();
      } else {
        try {
          appPage.goBack(new Page.GoBackOptions().setTimeout(SHORT_TIMEOUT_MS));
        } catch (final PlaywrightException ignored) {
          // Some routes are SPA transitions without browser history.
        }
      }
      waitForUi(appPage);

      return new LegalStepResult(headingVisible && legalContentVisible, finalUrl);
    } catch (final Exception e) {
      return new LegalStepResult(false, finalUrl.isBlank() ? "ERROR: " + e.getMessage() : finalUrl);
    }
  }

  private void writeFinalReport(final Path reportPath, final LinkedHashMap<String, Boolean> report,
      final Map<String, String> urls, final List<String> failures) throws IOException {
    final StringBuilder builder = new StringBuilder();
    builder.append("SaleADS Mi Negocio Full Workflow Report").append(System.lineSeparator());
    builder.append("=====================================").append(System.lineSeparator());
    builder.append(System.lineSeparator());

    for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
      builder.append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL").append(System.lineSeparator());
    }

    builder.append(System.lineSeparator());
    builder.append("Evidence URLs").append(System.lineSeparator());
    builder.append("-------------").append(System.lineSeparator());
    for (final Map.Entry<String, String> entry : urls.entrySet()) {
      builder.append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
    }

    if (!failures.isEmpty()) {
      builder.append(System.lineSeparator());
      builder.append("Failures").append(System.lineSeparator());
      builder.append("--------").append(System.lineSeparator());
      for (final String failure : failures) {
        builder.append("- ").append(failure).append(System.lineSeparator());
      }
    }

    Files.writeString(reportPath, builder.toString(), StandardCharsets.UTF_8);
  }

  private Locator findLoginTrigger(final Page page) {
    Locator locator = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(LOGIN_BUTTON_PATTERN));
    if (locator.count() > 0) {
      return locator.first();
    }

    locator = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(LOGIN_BUTTON_PATTERN));
    if (locator.count() > 0) {
      return locator.first();
    }

    locator = page.getByText(LOGIN_BUTTON_PATTERN);
    if (locator.count() > 0) {
      return locator.first();
    }
    throw new IllegalStateException("Login button or 'Sign in with Google' trigger was not found.");
  }

  private void maybeSelectGoogleAccount(final Page page, final String googleAccount) {
    if (googleAccount == null || googleAccount.isBlank()) {
      return;
    }

    try {
      final Locator accountLocator = page.getByText(googleAccount);
      if (accountLocator.count() > 0 && accountLocator.first().isVisible()) {
        clickLocator(accountLocator.first());
      }
    } catch (final PlaywrightException ignored) {
      // Account selector does not appear if the session is already authenticated.
    }
  }

  private boolean visibleByText(final Page page, final Pattern pattern) {
    try {
      final Locator locator = page.getByText(pattern);
      if (locator.count() == 0) {
        return false;
      }
      locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE)
          .setTimeout(SHORT_TIMEOUT_MS));
      return locator.first().isVisible();
    } catch (final PlaywrightException ignored) {
      return false;
    }
  }

  private boolean visibleByRegex(final Page page, final Pattern pattern) {
    return visibleByText(page, pattern);
  }

  private boolean visibleByAnyText(final Page page, final List<Pattern> patterns) {
    for (final Pattern pattern : patterns) {
      if (visibleByText(page, pattern)) {
        return true;
      }
    }
    return false;
  }

  private void clickVisibleText(final Page page, final Pattern textPattern) {
    final Locator byText = page.getByText(textPattern);
    if (byText.count() == 0) {
      throw new IllegalStateException("Element with visible text pattern not found: " + textPattern);
    }
    clickLocator(byText.first());
  }

  private void clickLocator(final Locator locator) {
    locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));
    locator.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
  }

  private boolean visibleByLabelOrPlaceholder(final Page page, final Pattern labelPattern) {
    final Locator locator = findByLabelOrPlaceholder(page, labelPattern);
    if (locator.count() == 0) {
      return false;
    }
    try {
      locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE)
          .setTimeout(SHORT_TIMEOUT_MS));
      return locator.first().isVisible();
    } catch (final PlaywrightException ignored) {
      return false;
    }
  }

  private Locator findByLabelOrPlaceholder(final Page page, final Pattern labelPattern) {
    Locator locator = page.getByLabel(labelPattern);
    if (locator.count() > 0) {
      return locator;
    }

    locator = page.getByPlaceholder(labelPattern);
    if (locator.count() > 0) {
      return locator;
    }

    return page.locator("input").filter(new Locator.FilterOptions().setHasText(labelPattern.pattern()));
  }

  private void waitForUi(final Page page) {
    page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(SHORT_TIMEOUT_MS));
    } catch (final PlaywrightException ignored) {
      // Some screens keep long-polling connections open.
    }
    page.waitForTimeout(400);
  }

  private void screenshot(final Page page, final Path outputPath, final boolean fullPage) {
    page.screenshot(new Page.ScreenshotOptions().setPath(outputPath).setFullPage(fullPage));
  }

  private static String firstNonBlank(final String... values) {
    for (final String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private static final class LegalStepResult {
    private final boolean passed;
    private final String finalUrl;

    private LegalStepResult(final boolean passed, final String finalUrl) {
      this.passed = passed;
      this.finalUrl = finalUrl;
    }
  }
}
