package io.proleap.saleads;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class SaleAdsMiNegocioFullWorkflowTest {

  private static final int DEFAULT_TIMEOUT_MS = 30_000;
  private static final String EXPECTED_EMAIL_DEFAULT = "juanlucasbarbiergarzon@gmail.com";
  private static final String STEP_LOGIN = "Login";
  private static final String STEP_MENU = "Mi Negocio menu";
  private static final String STEP_MODAL = "Agregar Negocio modal";
  private static final String STEP_ADMIN_VIEW = "Administrar Negocios view";
  private static final String STEP_INFO_GENERAL = "Información General";
  private static final String STEP_ACCOUNT_DETAILS = "Detalles de la Cuenta";
  private static final String STEP_BUSINESSES = "Tus Negocios";
  private static final String STEP_TERMS = "Términos y Condiciones";
  private static final String STEP_PRIVACY = "Política de Privacidad";
  private static final List<String> REPORT_ORDER = Arrays.asList(
      STEP_LOGIN,
      STEP_MENU,
      STEP_MODAL,
      STEP_ADMIN_VIEW,
      STEP_INFO_GENERAL,
      STEP_ACCOUNT_DETAILS,
      STEP_BUSINESSES,
      STEP_TERMS,
      STEP_PRIVACY);

  private final Map<String, StepResult> results = new LinkedHashMap<>();
  private final String expectedEmail =
      readConfig("saleads.expected.email", "SALEADS_EXPECTED_EMAIL", EXPECTED_EMAIL_DEFAULT);
  private final String expectedName =
      readConfig("saleads.expected.name", "SALEADS_EXPECTED_NAME", "").trim();
  private Playwright playwright;
  private Browser browser;
  private BrowserContext context;
  private Page page;
  private Path screenshotDir;

  @Before
  public void setUp() throws Exception {
    final String loginUrl = readConfig("saleads.login.url", "SALEADS_LOGIN_URL", "").trim();
    Assert.assertFalse(
        "Provide saleads.login.url (system property) or SALEADS_LOGIN_URL (env var).",
        loginUrl.isEmpty());

    for (String step : REPORT_ORDER) {
      results.put(step, StepResult.notRun());
    }

    screenshotDir = createScreenshotDir();
    playwright = Playwright.create();
    final boolean headless = Boolean.parseBoolean(readConfig(
        "saleads.headless", "SALEADS_HEADLESS", "true"));
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
    context = browser.newContext(new Browser.NewContextOptions().setIgnoreHTTPSErrors(true));
    page = context.newPage();
    page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
    page.navigate(loginUrl);
    waitForUiSettled(page);
  }

  @After
  public void tearDown() {
    try {
      printFinalReport();
    } finally {
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
  }

  @Test
  public void saleadsMiNegocioFullWorkflow() {
    runStep(STEP_LOGIN, this::loginWithGoogleAndValidateApp);
    runStep(STEP_MENU, this::openMiNegocioAndValidateMenu);
    runStep(STEP_MODAL, this::validateAgregarNegocioModal);
    runStep(STEP_ADMIN_VIEW, this::openAdministrarNegociosAndValidateSections);
    runStep(STEP_INFO_GENERAL, this::validateInformacionGeneral);
    runStep(STEP_ACCOUNT_DETAILS, this::validateDetallesCuenta);
    runStep(STEP_BUSINESSES, this::validateTusNegocios);
    runStep(STEP_TERMS, () -> validateLegalPage(
        "Términos y Condiciones",
        "Términos y Condiciones",
        "08-terminos-y-condiciones.png"));
    runStep(STEP_PRIVACY, () -> validateLegalPage(
        "Política de Privacidad",
        "Política de Privacidad",
        "09-politica-de-privacidad.png"));

    final List<String> failedSteps = results.entrySet().stream()
        .filter(entry -> !entry.getValue().pass)
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());
    Assert.assertTrue("Failed validations: " + failedSteps, failedSteps.isEmpty());
  }

  private void loginWithGoogleAndValidateApp() {
    final Locator loginButton = waitForAnyVisible(page,
        "Google login button",
        "button:has-text('Sign in with Google')",
        "button:has-text('Iniciar sesión con Google')",
        "button:has-text('Continuar con Google')",
        "text=/Sign in with Google|Iniciar sesión con Google|Continuar con Google/i");

    final Page potentialAuthPage = clickAndCapturePotentialPopup(loginButton);
    final Page authPage = potentialAuthPage == null ? page : potentialAuthPage;

    chooseGoogleAccountIfPrompted(authPage);
    if (authPage != page) {
      page.bringToFront();
      waitForUiSettled(page);
    }

    waitForAnyVisible(page,
        "Main application interface",
        "aside",
        "nav",
        "text=Negocio",
        "text=Mi Negocio");
    waitForAnyVisible(page,
        "Left sidebar navigation",
        "aside:has-text('Negocio')",
        "nav:has-text('Negocio')",
        "text=Mi Negocio");
    takeScreenshot(page, "01-dashboard-loaded.png", false);
  }

  private void openMiNegocioAndValidateMenu() {
    final Locator miNegocioOption = waitForAnyVisible(page,
        "Mi Negocio option",
        "text=Mi Negocio",
        "a:has-text('Mi Negocio')",
        "button:has-text('Mi Negocio')");
    miNegocioOption.click();
    waitForUiSettled(page);

    waitForAnyVisible(page, "Agregar Negocio submenu option", "text=Agregar Negocio");
    waitForAnyVisible(page, "Administrar Negocios submenu option", "text=Administrar Negocios");
    takeScreenshot(page, "02-mi-negocio-menu-expanded.png", false);
  }

  private void validateAgregarNegocioModal() {
    final Locator agregarNegocio = waitForAnyVisible(page,
        "Agregar Negocio option",
        "text=Agregar Negocio",
        "a:has-text('Agregar Negocio')",
        "button:has-text('Agregar Negocio')");
    agregarNegocio.click();
    waitForUiSettled(page);

    waitForAnyVisible(page, "Crear Nuevo Negocio title", "text=Crear Nuevo Negocio");
    final Locator nombreNegocioInput = waitForAnyVisible(page,
        "Nombre del Negocio input",
        "input[placeholder*='Nombre del Negocio']",
        "input[name*='nombre']",
        "input[id*='nombre']",
        "[role='dialog'] input");
    waitForAnyVisible(page, "Business quota text", "text=/Tienes\\s+2\\s+de\\s+3\\s+negocios/i");
    waitForAnyVisible(page, "Cancelar button", "button:has-text('Cancelar')");
    waitForAnyVisible(page, "Crear Negocio button", "button:has-text('Crear Negocio')");
    takeScreenshot(page, "03-agregar-negocio-modal.png", false);

    nombreNegocioInput.fill("Negocio Prueba Automatización");
    waitForAnyVisible(page, "Cancelar button", "button:has-text('Cancelar')").click();
    waitForUiSettled(page);
  }

  private void openAdministrarNegociosAndValidateSections() {
    final Locator miNegocioOption = waitForAnyVisible(page,
        "Mi Negocio option before admin navigation",
        "text=Mi Negocio",
        "a:has-text('Mi Negocio')",
        "button:has-text('Mi Negocio')");
    miNegocioOption.click();
    waitForUiSettled(page);

    final Locator administrarNegocios = waitForAnyVisible(page,
        "Administrar Negocios option",
        "text=Administrar Negocios",
        "a:has-text('Administrar Negocios')",
        "button:has-text('Administrar Negocios')");
    administrarNegocios.click();
    waitForUiSettled(page);

    waitForAnyVisible(page, "Información General section", "text=Información General");
    waitForAnyVisible(page, "Detalles de la Cuenta section", "text=Detalles de la Cuenta");
    waitForAnyVisible(page, "Tus Negocios section", "text=Tus Negocios");
    waitForAnyVisible(page, "Sección Legal section", "text=Sección Legal");
    takeScreenshot(page, "04-administrar-negocios-view.png", true);
  }

  private void validateInformacionGeneral() {
    if (!expectedName.isEmpty()) {
      waitForAnyVisible(page, "Expected user name", "text=" + expectedName);
    } else {
      waitForAnyVisible(page,
          "User name label/value",
          "text=/Nombre/i",
          "text=/Usuario/i",
          "text=/Perfil/i");
    }

    waitForAnyVisible(page,
        "User email",
        "text=" + expectedEmail,
        "text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/");
    waitForAnyVisible(page, "BUSINESS PLAN text", "text=BUSINESS PLAN");
    waitForAnyVisible(page, "Cambiar Plan button", "button:has-text('Cambiar Plan')");
  }

  private void validateDetallesCuenta() {
    waitForAnyVisible(page, "Cuenta creada text", "text=Cuenta creada");
    waitForAnyVisible(page, "Estado activo text", "text=Estado activo");
    waitForAnyVisible(page, "Idioma seleccionado text", "text=Idioma seleccionado");
  }

  private void validateTusNegocios() {
    waitForAnyVisible(page, "Tus Negocios section title", "text=Tus Negocios");
    waitForAnyVisible(page, "Agregar Negocio button in businesses section", "text=Agregar Negocio");
    waitForAnyVisible(page, "Business quota text", "text=/Tienes\\s+2\\s+de\\s+3\\s+negocios/i");

    final Locator listCandidates = page.locator("table, ul, ol, [role='list'], [role='table'], .card");
    Assert.assertTrue("Business list is visible", listCandidates.count() > 0);
  }

  private void validateLegalPage(String linkText, String headingText, String screenshotName) {
    final Page appPage = page;
    final Locator legalLink = waitForAnyVisible(appPage,
        linkText + " link",
        "a:has-text('" + linkText + "')",
        "button:has-text('" + linkText + "')",
        "text=" + linkText);

    final Page destinationPage = clickAndCapturePotentialPopup(legalLink);
    final Page activePage = destinationPage == null ? appPage : destinationPage;
    waitForUiSettled(activePage);

    waitForAnyVisible(activePage,
        headingText + " heading",
        "h1:has-text('" + headingText + "')",
        "h2:has-text('" + headingText + "')",
        "text=" + headingText);
    final String legalText = activePage.locator("body").innerText();
    Assert.assertTrue("Legal content text is visible", legalText != null && legalText.trim().length() > 120);

    takeScreenshot(activePage, screenshotName, true);

    final String finalUrl = activePage.url();
    final String currentStep = headingText.equals("Términos y Condiciones") ? STEP_TERMS : STEP_PRIVACY;
    results.put(currentStep, StepResult.pass("URL: " + finalUrl));

    if (activePage != appPage) {
      activePage.close();
      appPage.bringToFront();
      waitForUiSettled(appPage);
    } else {
      appPage.goBack();
      waitForUiSettled(appPage);
    }
  }

  private void chooseGoogleAccountIfPrompted(Page authPage) {
    final Locator account = authPage.locator("text=" + expectedEmail).first();
    try {
      if (account.count() > 0 && account.isVisible()) {
        account.click();
        waitForUiSettled(authPage);
      }
    } catch (PlaywrightException ignored) {
      // Account picker may not appear when session is already authenticated.
    }
  }

  private Page clickAndCapturePotentialPopup(Locator locator) {
    try {
      return context.waitForPage(locator::click, new BrowserContext.WaitForPageOptions().setTimeout(6_000));
    } catch (PlaywrightException ignored) {
      waitForUiSettled(page);
      return null;
    }
  }

  private void runStep(String stepName, CheckedStep step) {
    try {
      step.run();
      if (!results.get(stepName).pass) {
        results.put(stepName, StepResult.pass("Validated successfully"));
      }
    } catch (Throwable throwable) {
      results.put(stepName, StepResult.fail(safeMessage(throwable)));
    }
  }

  private Locator waitForAnyVisible(Page targetPage, String description, String... selectors) {
    final long deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS;
    while (System.currentTimeMillis() < deadline) {
      for (String selector : selectors) {
        final Locator locator = targetPage.locator(selector).first();
        try {
          if (locator.count() > 0 && locator.isVisible()) {
            return locator;
          }
        } catch (PlaywrightException ignored) {
          // Keep polling until timeout.
        }
      }
      targetPage.waitForTimeout(500);
    }
    throw new AssertionError("Expected visible element not found: " + description
        + ". Selectors: " + Arrays.toString(selectors));
  }

  private void takeScreenshot(Page targetPage, String fileName, boolean fullPage) {
    targetPage.screenshot(new Page.ScreenshotOptions()
        .setPath(screenshotDir.resolve(fileName))
        .setFullPage(fullPage));
  }

  private void waitForUiSettled(Page targetPage) {
    try {
      targetPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
    } catch (PlaywrightException ignored) {
      // Keep going if page is SPA-like and does not trigger state transitions.
    }
    targetPage.waitForTimeout(900);
  }

  private Path createScreenshotDir() throws Exception {
    final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
    final Path dir = Paths.get("target", "saleads-evidence", timestamp);
    Files.createDirectories(dir);
    return dir;
  }

  private void printFinalReport() {
    System.out.println();
    System.out.println("=== SaleADS Mi Negocio Full Test Report ===");
    for (String key : REPORT_ORDER) {
      final StepResult result = results.getOrDefault(key, StepResult.notRun());
      final String status = result.pass ? "PASS" : "FAIL";
      System.out.println("- " + key + ": " + status + " | " + result.details);
    }
    if (screenshotDir != null) {
      System.out.println("Evidence folder: " + screenshotDir.toAbsolutePath());
    }
    System.out.println("==========================================");
  }

  private String safeMessage(Throwable throwable) {
    return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
  }

  private String readConfig(String propertyName, String envName, String defaultValue) {
    final String systemValue = System.getProperty(propertyName);
    if (systemValue != null && !systemValue.trim().isEmpty()) {
      return systemValue.trim();
    }

    final String envValue = System.getenv(envName);
    if (envValue != null && !envValue.trim().isEmpty()) {
      return envValue.trim();
    }

    return defaultValue;
  }

  @FunctionalInterface
  private interface CheckedStep {
    void run();
  }

  private static class StepResult {

    private final boolean pass;
    private final String details;

    private StepResult(boolean pass, String details) {
      this.pass = pass;
      this.details = details;
    }

    private static StepResult pass(String details) {
      return new StepResult(true, details);
    }

    private static StepResult fail(String details) {
      return new StepResult(false, details);
    }

    private static StepResult notRun() {
      return fail("Not executed");
    }
  }
}
