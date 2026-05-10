package io.saleads.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class SaleadsMiNegocioFullTest {

  private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
  private static final Pattern GOOGLE_SIGN_IN_PATTERN = Pattern.compile(
      "(?i)(sign\\s*in\\s*with\\s*google|continue\\s*with\\s*google|continuar\\s*con\\s*google|iniciar\\s*sesi[oó]n\\s*con\\s*google|google)");

  private static final Pattern EMAIL_PATTERN = Pattern.compile(
      "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

  private static final String LOGIN_KEY = "Login";
  private static final String MI_NEGOCIO_MENU_KEY = "Mi Negocio menu";
  private static final String AGREGAR_NEGOCIO_MODAL_KEY = "Agregar Negocio modal";
  private static final String ADMINISTRAR_NEGOCIOS_VIEW_KEY = "Administrar Negocios view";
  private static final String INFORMACION_GENERAL_KEY = "Información General";
  private static final String DETALLES_CUENTA_KEY = "Detalles de la Cuenta";
  private static final String TUS_NEGOCIOS_KEY = "Tus Negocios";
  private static final String TERMINOS_KEY = "Términos y Condiciones";
  private static final String PRIVACIDAD_KEY = "Política de Privacidad";

  private final LinkedHashMap<String, Boolean> stepResults = new LinkedHashMap<>();
  private final LinkedHashMap<String, String> stepErrors = new LinkedHashMap<>();

  private Playwright playwright;
  private Browser browser;
  private BrowserContext context;
  private Page appPage;
  private Path evidenceDir;
  private String administrarNegociosUrl;
  private String terminosFinalUrl = "N/A";
  private String privacidadFinalUrl = "N/A";

  @Before
  public void setUp() throws IOException {
    final String saleadsUrl = readConfig("SALEADS_URL", "saleads.url");
    Assume.assumeTrue(
        "Set SALEADS_URL (or -Dsaleads.url) with the login URL for the target environment.",
        saleadsUrl != null && !saleadsUrl.isBlank());

    evidenceDir = createEvidenceDirectory();

    playwright = Playwright.create();
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
        .setHeadless(Boolean.parseBoolean(readConfigWithDefault("HEADLESS", "headless", "true"))));

    context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
    appPage = context.newPage();
    appPage.setDefaultTimeout(resolveTimeoutMs());
    appPage.navigate(saleadsUrl);
    waitForUi(appPage);
  }

  @After
  public void tearDown() {
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

  @Test
  public void saleads_mi_negocio_full_test() throws IOException {
    runStep(LOGIN_KEY, this::stepLoginWithGoogle);
    runStep(MI_NEGOCIO_MENU_KEY, this::stepOpenMiNegocioMenu);
    runStep(AGREGAR_NEGOCIO_MODAL_KEY, this::stepValidateAgregarNegocioModal);
    runStep(ADMINISTRAR_NEGOCIOS_VIEW_KEY, this::stepOpenAdministrarNegocios);
    runStep(INFORMACION_GENERAL_KEY, this::stepValidateInformacionGeneral);
    runStep(DETALLES_CUENTA_KEY, this::stepValidateDetallesCuenta);
    runStep(TUS_NEGOCIOS_KEY, this::stepValidateTusNegocios);
    runStep(TERMINOS_KEY, this::stepValidateTerminosYCondiciones);
    runStep(PRIVACIDAD_KEY, this::stepValidatePoliticaDePrivacidad);

    writeFinalReport();
    assertAllStepsPassed();
  }

  private void stepLoginWithGoogle() throws IOException {
    if (!isSidebarVisible()) {
      final Locator loginButton = requireVisible(appPage, "Google login button",
          appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(GOOGLE_SIGN_IN_PATTERN)),
          appPage.getByText(GOOGLE_SIGN_IN_PATTERN));

      final Page popup = clickAndCapturePopup(loginButton);
      if (popup != null) {
        handleGoogleAccountSelection(popup);
      } else {
        handleGoogleAccountSelection(appPage);
      }
    }

    assertMainAppInterfaceVisible();
    captureCheckpoint(appPage, "01-dashboard-loaded.png", true);
  }

  private void stepOpenMiNegocioMenu() throws IOException {
    final Locator negocioSection = firstVisible(
        appPage.getByText(Pattern.compile("(?i)^Negocio$")),
        appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Negocio$"))),
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Negocio$"))));
    if (negocioSection != null) {
      clickAndWait(appPage, negocioSection);
    }

    final Locator miNegocioItem = requireVisible(appPage, "'Mi Negocio' option",
        appPage.getByText(Pattern.compile("(?i)^Mi\\s*Negocio$")),
        appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Mi\\s*Negocio$"))),
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Mi\\s*Negocio$"))));
    clickAndWait(appPage, miNegocioItem);

    requireVisible(appPage, "'Agregar Negocio' submenu item",
        appPage.getByText(Pattern.compile("(?i)^Agregar\\s*Negocio$")),
        appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Agregar\\s*Negocio$"))),
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Agregar\\s*Negocio$"))));

    requireVisible(appPage, "'Administrar Negocios' submenu item",
        appPage.getByText(Pattern.compile("(?i)^Administrar\\s*Negocios$")),
        appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Administrar\\s*Negocios$"))),
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Administrar\\s*Negocios$"))));

    captureCheckpoint(appPage, "02-mi-negocio-menu-expanded.png", false);
  }

  private void stepValidateAgregarNegocioModal() throws IOException {
    final Locator agregarNegocio = requireVisible(appPage, "'Agregar Negocio' action",
        appPage.getByText(Pattern.compile("(?i)^Agregar\\s*Negocio$")),
        appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Agregar\\s*Negocio$"))),
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Agregar\\s*Negocio$"))));
    clickAndWait(appPage, agregarNegocio);

    final Locator modalTitle = requireVisible(appPage, "Modal title 'Crear Nuevo Negocio'",
        appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Crear\\s*Nuevo\\s*Negocio$"))),
        appPage.getByText(Pattern.compile("(?i)^Crear\\s*Nuevo\\s*Negocio$")));

    final Locator modalContainer = modalTitle.locator("xpath=ancestor::*[@role='dialog' or self::section or self::div][1]");

    final Locator nombreLabel = firstVisible(
        modalContainer.getByLabel(Pattern.compile("(?i)^Nombre\\s*del\\s*Negocio$")),
        modalContainer.getByPlaceholder(Pattern.compile("(?i)^Nombre\\s*del\\s*Negocio$")),
        modalContainer.getByText(Pattern.compile("(?i)^Nombre\\s*del\\s*Negocio$")));
    assertTrue("Input field 'Nombre del Negocio' should exist.", nombreLabel != null);

    requireVisible(appPage, "Business limit text",
        modalContainer.getByText(Pattern.compile("(?i)Tienes\\s*2\\s*de\\s*3\\s*negocios")),
        appPage.getByText(Pattern.compile("(?i)Tienes\\s*2\\s*de\\s*3\\s*negocios")));

    requireVisible(appPage, "'Cancelar' button",
        modalContainer.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(Pattern.compile("(?i)^Cancelar$"))),
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Cancelar$"))));

    requireVisible(appPage, "'Crear Negocio' button",
        modalContainer.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(Pattern.compile("(?i)^Crear\\s*Negocio$"))),
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Crear\\s*Negocio$"))));

    captureCheckpoint(appPage, "03-agregar-negocio-modal.png", false);

    final Locator nombreInput = firstVisible(
        modalContainer.getByLabel(Pattern.compile("(?i)^Nombre\\s*del\\s*Negocio$")),
        modalContainer.getByPlaceholder(Pattern.compile("(?i)^Nombre\\s*del\\s*Negocio$")),
        modalContainer.locator("input"));
    if (nombreInput != null) {
      nombreInput.first().click();
      waitForUi(appPage);
      nombreInput.first().fill("Negocio Prueba Automatización");
      waitForUi(appPage);
    }

    final Locator cancelar = requireVisible(appPage, "Cancel modal action",
        modalContainer.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(Pattern.compile("(?i)^Cancelar$"))),
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Cancelar$"))));
    clickAndWait(appPage, cancelar);
  }

  private void stepOpenAdministrarNegocios() throws IOException {
    final Locator administrarNegocios = firstVisible(
        appPage.getByText(Pattern.compile("(?i)^Administrar\\s*Negocios$")),
        appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Administrar\\s*Negocios$"))),
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Administrar\\s*Negocios$"))));

    if (administrarNegocios == null) {
      final Locator miNegocio = requireVisible(appPage, "Re-open 'Mi Negocio' option",
          appPage.getByText(Pattern.compile("(?i)^Mi\\s*Negocio$")),
          appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Mi\\s*Negocio$"))),
          appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Mi\\s*Negocio$"))));
      clickAndWait(appPage, miNegocio);
    }

    final Locator administrarLink = requireVisible(appPage, "'Administrar Negocios' navigation",
        appPage.getByText(Pattern.compile("(?i)^Administrar\\s*Negocios$")),
        appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Administrar\\s*Negocios$"))),
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Administrar\\s*Negocios$"))));
    clickAndWait(appPage, administrarLink);

    requireVisible(appPage, "'Información General' section",
        appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Informaci[oó]n\\s*General$"))),
        appPage.getByText(Pattern.compile("(?i)^Informaci[oó]n\\s*General$")));
    requireVisible(appPage, "'Detalles de la Cuenta' section",
        appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Detalles\\s*de\\s*la\\s*Cuenta$"))),
        appPage.getByText(Pattern.compile("(?i)^Detalles\\s*de\\s*la\\s*Cuenta$")));
    requireVisible(appPage, "'Tus Negocios' section",
        appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Tus\\s*Negocios$"))),
        appPage.getByText(Pattern.compile("(?i)^Tus\\s*Negocios$")));
    requireVisible(appPage, "'Sección Legal' section",
        appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Secci[oó]n\\s*Legal$"))),
        appPage.getByText(Pattern.compile("(?i)^Secci[oó]n\\s*Legal$")));

    administrarNegociosUrl = appPage.url();
    captureCheckpoint(appPage, "04-administrar-negocios-page.png", true);
  }

  private void stepValidateInformacionGeneral() {
    final Locator infoSection = sectionByHeading("Información General");
    final String sectionText = safeText(infoSection);

    assertTrue("User name should be visible in 'Información General'.",
        sectionText.toLowerCase().contains("nombre"));
    assertTrue("User email should be visible in 'Información General'.",
        EMAIL_PATTERN.matcher(sectionText).find());

    requireVisible(appPage, "'BUSINESS PLAN' label",
        infoSection.getByText(Pattern.compile("(?i)BUSINESS\\s*PLAN")),
        appPage.getByText(Pattern.compile("(?i)BUSINESS\\s*PLAN")));

    requireVisible(appPage, "'Cambiar Plan' button",
        infoSection.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(Pattern.compile("(?i)^Cambiar\\s*Plan$"))),
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Cambiar\\s*Plan$"))));
  }

  private void stepValidateDetallesCuenta() {
    final Locator detailsSection = sectionByHeading("Detalles de la Cuenta");

    requireVisible(appPage, "'Cuenta creada' text",
        detailsSection.getByText(Pattern.compile("(?i)Cuenta\\s*creada")),
        appPage.getByText(Pattern.compile("(?i)Cuenta\\s*creada")));
    requireVisible(appPage, "'Estado activo' text",
        detailsSection.getByText(Pattern.compile("(?i)Estado\\s*activo")),
        appPage.getByText(Pattern.compile("(?i)Estado\\s*activo")));
    requireVisible(appPage, "'Idioma seleccionado' text",
        detailsSection.getByText(Pattern.compile("(?i)Idioma\\s*seleccionado")),
        appPage.getByText(Pattern.compile("(?i)Idioma\\s*seleccionado")));
  }

  private void stepValidateTusNegocios() {
    final Locator negociosSection = sectionByHeading("Tus Negocios");

    assertTrue("Business list should be visible in 'Tus Negocios'.",
        negociosSection.locator("li, table, [role='listitem']").count() > 0
            || safeText(negociosSection).toLowerCase().contains("negocio"));

    requireVisible(appPage, "'Agregar Negocio' button in 'Tus Negocios'",
        negociosSection.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(Pattern.compile("(?i)^Agregar\\s*Negocio$"))),
        negociosSection.getByText(Pattern.compile("(?i)^Agregar\\s*Negocio$")),
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^Agregar\\s*Negocio$"))));

    requireVisible(appPage, "Business limit text in 'Tus Negocios'",
        negociosSection.getByText(Pattern.compile("(?i)Tienes\\s*2\\s*de\\s*3\\s*negocios")),
        appPage.getByText(Pattern.compile("(?i)Tienes\\s*2\\s*de\\s*3\\s*negocios")));
  }

  private void stepValidateTerminosYCondiciones() throws IOException {
    terminosFinalUrl = validateLegalDocument(
        "Términos y Condiciones",
        Pattern.compile("(?i)T[ée]rminos\\s*y\\s*Condiciones"),
        "05-terminos-y-condiciones.png");
  }

  private void stepValidatePoliticaDePrivacidad() throws IOException {
    privacidadFinalUrl = validateLegalDocument(
        "Política de Privacidad",
        Pattern.compile("(?i)Pol[ií]tica\\s*de\\s*Privacidad"),
        "06-politica-de-privacidad.png");
  }

  private String validateLegalDocument(
      final String linkText,
      final Pattern headingPattern,
      final String screenshotName) throws IOException {

    final Locator legalSection = sectionByHeading("Sección Legal");
    final Locator link = requireVisible(appPage, "Legal link: " + linkText,
        legalSection.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(Pattern.compile("(?i)^" + Pattern.quote(linkText) + "$"))),
        legalSection.getByText(Pattern.compile("(?i)^" + Pattern.quote(linkText) + "$")),
        appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^" + Pattern.quote(linkText) + "$"))),
        appPage.getByText(Pattern.compile("(?i)^" + Pattern.quote(linkText) + "$")));

    final String returnUrl = administrarNegociosUrl == null ? appPage.url() : administrarNegociosUrl;
    final Page openedPage = clickAndCapturePopup(link);
    final Page legalPage = openedPage == null ? appPage : openedPage;
    waitForUi(legalPage);

    requireVisible(legalPage, "Legal heading for " + linkText,
        legalPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(headingPattern)),
        legalPage.getByText(headingPattern));

    assertTrue("Legal content text should be visible for " + linkText + ".",
        safeText(legalPage.locator("body")).trim().length() > 100);

    captureCheckpoint(legalPage, screenshotName, true);
    final String finalUrl = legalPage.url();

    if (openedPage != null && !openedPage.isClosed()) {
      openedPage.close();
      appPage.bringToFront();
      waitForUi(appPage);
    } else if (!appPage.url().equals(returnUrl)) {
      try {
        appPage.goBack();
        waitForUi(appPage);
      } catch (PlaywrightException e) {
        appPage.navigate(returnUrl);
        waitForUi(appPage);
      }
    }

    return finalUrl;
  }

  private void runStep(final String stepName, final StepAction action) {
    try {
      action.run();
      stepResults.put(stepName, Boolean.TRUE);
    } catch (Throwable error) {
      stepResults.put(stepName, Boolean.FALSE);
      stepErrors.put(stepName, error.getMessage() == null ? error.toString() : error.getMessage());
    }
  }

  private void assertAllStepsPassed() {
    final StringBuilder summary = new StringBuilder();
    boolean failed = false;
    for (Map.Entry<String, Boolean> entry : stepResults.entrySet()) {
      if (!entry.getValue()) {
        failed = true;
      }
      summary.append(entry.getKey())
          .append(": ")
          .append(Boolean.TRUE.equals(entry.getValue()) ? "PASS" : "FAIL");
      if (!Boolean.TRUE.equals(entry.getValue()) && stepErrors.containsKey(entry.getKey())) {
        summary.append(" - ").append(stepErrors.get(entry.getKey()));
      }
      summary.append(System.lineSeparator());
    }

    if (failed) {
      fail("One or more validations failed.\n" + summary);
    }
  }

  private void writeFinalReport() throws IOException {
    final StringBuilder report = new StringBuilder();
    report.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
    report.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append(System.lineSeparator());
    report.append(System.lineSeparator());
    report.append("Validation report").append(System.lineSeparator());
    for (Map.Entry<String, Boolean> entry : stepResults.entrySet()) {
      report.append("- ")
          .append(entry.getKey())
          .append(": ")
          .append(Boolean.TRUE.equals(entry.getValue()) ? "PASS" : "FAIL");
      if (!Boolean.TRUE.equals(entry.getValue()) && stepErrors.containsKey(entry.getKey())) {
        report.append(" - ").append(stepErrors.get(entry.getKey()));
      }
      report.append(System.lineSeparator());
    }
    report.append(System.lineSeparator());
    report.append("Términos y Condiciones URL: ").append(terminosFinalUrl).append(System.lineSeparator());
    report.append("Política de Privacidad URL: ").append(privacidadFinalUrl).append(System.lineSeparator());

    Files.writeString(evidenceDir.resolve("final-report.txt"), report.toString(), StandardCharsets.UTF_8);
  }

  private Page clickAndCapturePopup(final Locator clickable) {
    try {
      return context.waitForPage(
          new BrowserContext.WaitForPageOptions().setTimeout(7000),
          clickable.first()::click);
    } catch (PlaywrightException popupTimeout) {
      clickAndWait(appPage, clickable);
      return null;
    }
  }

  private void handleGoogleAccountSelection(final Page page) {
    waitForUi(page);

    final Locator emailOption = firstVisible(
        page.getByText(Pattern.compile("(?i)^" + Pattern.quote(GOOGLE_ACCOUNT_EMAIL) + "$")),
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^" + Pattern.quote(GOOGLE_ACCOUNT_EMAIL) + "$"))),
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^" + Pattern.quote(GOOGLE_ACCOUNT_EMAIL) + "$"))));

    if (emailOption != null) {
      emailOption.first().click();
      waitForUi(page);
    }

    if (page != appPage && !page.isClosed()) {
      if (!isGooglePage(page.url())) {
        appPage = page;
      } else {
        for (int i = 0; i < 24 && !page.isClosed(); i++) {
          page.waitForTimeout(500);
        }
      }
    }

    appPage.bringToFront();
    waitForUi(appPage);
  }

  private void assertMainAppInterfaceVisible() {
    final boolean mainVisible = isVisible(appPage.locator("main"))
        || isVisible(appPage.getByText(Pattern.compile("(?i)dashboard|inicio|mi\\s*negocio|negocio")));
    assertTrue("Main application interface should appear after login.", mainVisible);
    assertTrue("Left sidebar navigation should be visible after login.", isSidebarVisible());
  }

  private boolean isSidebarVisible() {
    return isVisible(appPage.locator("aside"))
        || isVisible(appPage.getByText(Pattern.compile("(?i)^Negocio$")))
        || isVisible(appPage.getByText(Pattern.compile("(?i)^Mi\\s*Negocio$")));
  }

  private Locator sectionByHeading(final String headingText) {
    final Pattern headingPattern = Pattern.compile("(?i)^" + Pattern.quote(headingText) + "$");
    final Locator heading = requireVisible(appPage, "Section heading " + headingText,
        appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(headingPattern)),
        appPage.getByText(headingPattern));
    return heading.locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
  }

  private Locator requireVisible(final Page page, final String description, final Locator... candidates) {
    final Locator found = firstVisible(candidates);
    if (found == null) {
      throw new AssertionError(description + " was not found or visible.");
    }
    return found;
  }

  private Locator firstVisible(final Locator... candidates) {
    for (Locator candidate : candidates) {
      if (candidate != null && isVisible(candidate)) {
        return candidate.first();
      }
    }
    return null;
  }

  private boolean isVisible(final Locator locator) {
    try {
      return locator.first().isVisible(new Locator.IsVisibleOptions().setTimeout(2500));
    } catch (PlaywrightException error) {
      return false;
    }
  }

  private void clickAndWait(final Page page, final Locator locator) {
    locator.first().scrollIntoViewIfNeeded();
    locator.first().click();
    waitForUi(page);
  }

  private void waitForUi(final Page page) {
    try {
      page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(12000));
    } catch (PlaywrightException ignored) {
      // Some SPA transitions do not trigger DOMContentLoaded.
    }

    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(7000));
    } catch (PlaywrightException ignored) {
      // Network idle is not guaranteed in all app states.
    }

    page.waitForTimeout(400);
  }

  private void captureCheckpoint(final Page page, final String fileName, final boolean fullPage)
      throws IOException {
    final Path screenshotPath = evidenceDir.resolve(fileName);
    page.screenshot(new Page.ScreenshotOptions()
        .setPath(screenshotPath)
        .setFullPage(fullPage));
  }

  private String readConfig(final String envName, final String propertyName) {
    final String envValue = System.getenv(envName);
    if (envValue != null && !envValue.isBlank()) {
      return envValue;
    }
    final String propertyValue = System.getProperty(propertyName);
    if (propertyValue != null && !propertyValue.isBlank()) {
      return propertyValue;
    }
    return null;
  }

  private String readConfigWithDefault(
      final String envName, final String propertyName, final String defaultValue) {
    final String value = readConfig(envName, propertyName);
    return value == null ? defaultValue : value;
  }

  private double resolveTimeoutMs() {
    final String timeoutRaw = readConfigWithDefault("SALEADS_TIMEOUT_MS", "saleads.timeout.ms", "30000");
    try {
      return Double.parseDouble(timeoutRaw);
    } catch (NumberFormatException ignored) {
      return 30000D;
    }
  }

  private Path createEvidenceDirectory() throws IOException {
    final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    final Path targetPath = Paths.get("target", "saleads-evidence", timestamp);
    Files.createDirectories(targetPath);
    return targetPath;
  }

  private String safeText(final Locator locator) {
    try {
      return locator.innerText();
    } catch (PlaywrightException error) {
      return "";
    }
  }

  private boolean isGooglePage(final String url) {
    if (url == null) {
      return false;
    }
    final String normalized = url.toLowerCase();
    return normalized.contains("google.") || normalized.contains("accounts.google");
  }

  @FunctionalInterface
  private interface StepAction {
    void run() throws Exception;
  }
}
