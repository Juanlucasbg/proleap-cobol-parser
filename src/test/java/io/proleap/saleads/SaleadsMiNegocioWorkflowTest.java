package io.proleap.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

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

/**
 * End-to-end UI test for the SaleADS "Mi Negocio" workflow.
 *
 * <p>
 * This test is environment-agnostic and does not hardcode a domain. It expects
 * the login page URL to be provided through one of:
 * <ul>
 * <li>JVM property: saleads.startUrl</li>
 * <li>Environment variable: SALEADS_START_URL</li>
 * <li>Environment variable: SALEADS_LOGIN_URL</li>
 * <li>Environment variable: SALEADS_BASE_URL</li>
 * </ul>
 */
public class SaleadsMiNegocioWorkflowTest {

  private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
  private static final Path SCREENSHOT_DIR = Paths.get("target", "saleads-evidence", "mi-negocio");

  private final Map<String, StepOutcome> report = new LinkedHashMap<>();
  private final Map<String, String> legalUrls = new LinkedHashMap<>();

  private Playwright playwright;
  private Browser browser;
  private BrowserContext context;
  private Page appPage;

  @Before
  public void setUp() throws IOException {
    Files.createDirectories(SCREENSHOT_DIR);

    final String startUrl = firstNonBlank(
        System.getProperty("saleads.startUrl"),
        System.getenv("SALEADS_START_URL"),
        System.getenv("SALEADS_LOGIN_URL"),
        System.getenv("SALEADS_BASE_URL"));

    Assume.assumeTrue(
        "Set saleads.startUrl or SALEADS_START_URL/SALEADS_LOGIN_URL/SALEADS_BASE_URL to run this E2E test.",
        startUrl != null && !startUrl.isBlank());

    final boolean headless = Boolean.parseBoolean(firstNonBlank(
        System.getProperty("saleads.headless"),
        System.getenv("SALEADS_HEADLESS"),
        "true"));

    playwright = Playwright.create();
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
    context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
    appPage = context.newPage();

    appPage.navigate(startUrl);
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
  public void saleadsMiNegocioFullWorkflow() {
    runStep("Login", this::stepLoginWithGoogle);
    runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
    runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
    runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
    runStep("Información General", this::stepValidateInformacionGeneral);
    runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
    runStep("Tus Negocios", this::stepValidateTusNegocios);
    runStep("Términos y Condiciones", this::stepValidateTerminos);
    runStep("Política de Privacidad", this::stepValidatePrivacidad);

    printFinalReport();

    final StringBuilder failedSteps = new StringBuilder();
    for (Map.Entry<String, StepOutcome> entry : report.entrySet()) {
      if (!entry.getValue().passed) {
        if (failedSteps.length() > 0) {
          failedSteps.append(", ");
        }
        failedSteps.append(entry.getKey());
      }
    }

    assertTrue("Some workflow steps failed: " + failedSteps, failedSteps.length() == 0);
  }

  private void stepLoginWithGoogle() {
    final Locator loginButton = firstVisible(
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)sign in with google"))),
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)iniciar.*google"))),
        appPage.getByText(Pattern.compile("(?i)google")));

    clickAndWaitForPossiblePopup(appPage, loginButton);

    maybeSelectGoogleAccount();

    assertAnyVisible("Main application interface", appPage.locator("main"), appPage.locator("[role='main']"),
        appPage.getByText(Pattern.compile("(?i)dashboard")), appPage.getByText(Pattern.compile("(?i)negocio")));
    assertAnyVisible("Left sidebar navigation", appPage.locator("aside"),
        appPage.getByRole(AriaRole.NAVIGATION), appPage.getByText(Pattern.compile("(?i)negocio")));

    takeScreenshot(appPage, "01-dashboard-loaded", false);
  }

  private void stepOpenMiNegocioMenu() {
    assertAnyVisible("Negocio section", appPage.getByText(Pattern.compile("(?i)^\\s*negocio\\s*$")));

    final Locator miNegocio = firstVisible(
        appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)mi negocio"))),
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)mi negocio"))),
        appPage.getByText(Pattern.compile("(?i)mi negocio")));

    clickAndWait(appPage, miNegocio);

    assertAnyVisible("'Agregar Negocio' menu item", appPage.getByText(Pattern.compile("(?i)agregar negocio")));
    assertAnyVisible("'Administrar Negocios' menu item", appPage.getByText(Pattern.compile("(?i)administrar negocios")));

    takeScreenshot(appPage, "02-mi-negocio-expanded", false);
  }

  private void stepValidateAgregarNegocioModal() {
    final Locator agregarNegocio = firstVisible(appPage.getByText(Pattern.compile("(?i)^\\s*agregar negocio\\s*$")));
    clickAndWait(appPage, agregarNegocio);

    assertAnyVisible("Modal title 'Crear Nuevo Negocio'",
        appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)crear nuevo negocio"))),
        appPage.getByText(Pattern.compile("(?i)crear nuevo negocio")));

    final Locator nombreNegocioField = firstVisible(
        appPage.getByLabel(Pattern.compile("(?i)nombre del negocio")),
        appPage.getByPlaceholder(Pattern.compile("(?i)nombre del negocio")),
        appPage.locator("input[type='text']"));
    assertTrue("Input 'Nombre del Negocio' should be visible", isVisible(nombreNegocioField));

    assertAnyVisible("Business quota text", appPage.getByText(Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios")));
    assertAnyVisible("'Cancelar' button",
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*cancelar\\s*$"))));
    assertAnyVisible("'Crear Negocio' button",
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)crear negocio"))));

    takeScreenshot(appPage, "03-agregar-negocio-modal", false);

    clickAndWait(appPage, nombreNegocioField);
    nombreNegocioField.fill("Negocio Prueba Automatización");
    clickAndWait(appPage, firstVisible(
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*cancelar\\s*$"))),
        appPage.getByText(Pattern.compile("(?i)^\\s*cancelar\\s*$"))));
  }

  private void stepOpenAdministrarNegocios() {
    if (!isVisible(appPage.getByText(Pattern.compile("(?i)administrar negocios")))) {
      clickAndWait(appPage, firstVisible(appPage.getByText(Pattern.compile("(?i)mi negocio"))));
    }

    clickAndWait(appPage, firstVisible(appPage.getByText(Pattern.compile("(?i)administrar negocios"))));

    assertAnyVisible("Section 'Información General'",
        appPage.getByText(Pattern.compile("(?i)informaci[oó]n general")));
    assertAnyVisible("Section 'Detalles de la Cuenta'",
        appPage.getByText(Pattern.compile("(?i)detalles de la cuenta")));
    assertAnyVisible("Section 'Tus Negocios'", appPage.getByText(Pattern.compile("(?i)tus negocios")));
    assertAnyVisible("Section 'Sección Legal'", appPage.getByText(Pattern.compile("(?i)secci[oó]n legal")));

    takeScreenshot(appPage, "04-administrar-negocios-page", true);
  }

  private void stepValidateInformacionGeneral() {
    assertAnyVisible("User name", appPage.locator("[data-testid*='name' i]"), appPage.locator("text=/\\S+\\s+\\S+/"));
    assertAnyVisible("User email", appPage.locator("text=/[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}/i"));
    assertAnyVisible("Text 'BUSINESS PLAN'", appPage.getByText(Pattern.compile("(?i)business plan")));
    assertAnyVisible("Button 'Cambiar Plan'",
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cambiar plan"))),
        appPage.getByText(Pattern.compile("(?i)cambiar plan")));
  }

  private void stepValidateDetallesCuenta() {
    assertAnyVisible("'Cuenta creada' is visible", appPage.getByText(Pattern.compile("(?i)cuenta creada")));
    assertAnyVisible("'Estado activo' is visible", appPage.getByText(Pattern.compile("(?i)estado activo")));
    assertAnyVisible("'Idioma seleccionado' is visible", appPage.getByText(Pattern.compile("(?i)idioma seleccionado")));
  }

  private void stepValidateTusNegocios() {
    assertAnyVisible("Business list is visible",
        appPage.getByText(Pattern.compile("(?i)tus negocios")),
        appPage.locator("ul, table"));
    assertAnyVisible("'Agregar Negocio' exists", appPage.getByText(Pattern.compile("(?i)agregar negocio")));
    assertAnyVisible("Business quota text", appPage.getByText(Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios")));
  }

  private void stepValidateTerminos() {
    openLegalPageAndValidate(
        "Términos y Condiciones",
        Pattern.compile("(?i)t[eé]rminos y condiciones"),
        "08-terminos-y-condiciones",
        "Términos y Condiciones");
  }

  private void stepValidatePrivacidad() {
    openLegalPageAndValidate(
        "Política de Privacidad",
        Pattern.compile("(?i)pol[ií]tica de privacidad"),
        "09-politica-de-privacidad",
        "Política de Privacidad");
  }

  private void openLegalPageAndValidate(final String linkText, final Pattern headingPattern, final String screenshotName,
      final String reportKey) {
    final Locator legalLink = firstVisible(
        appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + Pattern.quote(linkText)))),
        appPage.getByText(Pattern.compile("(?i)" + Pattern.quote(linkText))));

    final Page legalPage = clickAndWaitForPossiblePopup(appPage, legalLink);
    waitForUi(legalPage);

    assertAnyVisible("Heading '" + linkText + "'",
        legalPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(headingPattern)),
        legalPage.getByText(headingPattern));

    final String legalBody = legalPage.textContent("body");
    assertTrue("Legal content text should be visible for " + linkText,
        legalBody != null && legalBody.trim().length() > 100);

    takeScreenshot(legalPage, screenshotName, true);
    legalUrls.put(reportKey, legalPage.url());

    if (legalPage != appPage) {
      legalPage.close();
      appPage.bringToFront();
      waitForUi(appPage);
    } else if (appPage.url() != null && !normalized(appPage.url()).contains("mi-negocio")) {
      appPage.goBack();
      waitForUi(appPage);
    }
  }

  private Page clickAndWaitForPossiblePopup(final Page sourcePage, final Locator target) {
    try {
      final Page popup = sourcePage.context().waitForPage(() -> clickAndWait(sourcePage, target),
          new BrowserContext.WaitForPageOptions().setTimeout(6000));
      waitForUi(popup);
      return popup;
    } catch (TimeoutError timeoutError) {
      waitForUi(sourcePage);
      return sourcePage;
    }
  }

  private void maybeSelectGoogleAccount() {
    for (Page page : context.pages()) {
      final Locator account = page.getByText(Pattern.compile("(?i)" + Pattern.quote(GOOGLE_ACCOUNT_EMAIL)));
      if (isVisible(account, 5000)) {
        clickAndWait(page, account);
        appPage.bringToFront();
        waitForUi(appPage);
        return;
      }
    }
  }

  private void runStep(final String stepName, final StepAction action) {
    try {
      action.run();
      report.put(stepName, StepOutcome.pass());
    } catch (Throwable throwable) {
      report.put(stepName, StepOutcome.fail(throwable.getMessage()));
    }
  }

  private void printFinalReport() {
    System.out.println("=== SaleADS Mi Negocio Workflow Final Report ===");
    for (Map.Entry<String, StepOutcome> entry : report.entrySet()) {
      final StepOutcome outcome = entry.getValue();
      System.out.println(entry.getKey() + ": " + (outcome.passed ? "PASS" : "FAIL")
          + (outcome.details == null ? "" : " - " + outcome.details));
    }
    if (!legalUrls.isEmpty()) {
      System.out.println("Captured legal URLs:");
      for (Map.Entry<String, String> legalUrl : legalUrls.entrySet()) {
        System.out.println("- " + legalUrl.getKey() + ": " + legalUrl.getValue());
      }
    }
    System.out.println("Screenshots directory: " + SCREENSHOT_DIR.toAbsolutePath());
  }

  private void clickAndWait(final Page page, final Locator target) {
    target.first().click();
    waitForUi(page);
  }

  private void waitForUi(final Page page) {
    page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10000));
    } catch (TimeoutError ignored) {
      // Some screens keep websocket/network activity open indefinitely.
    }
    page.waitForTimeout(500);
  }

  private void takeScreenshot(final Page page, final String checkpointName, final boolean fullPage) {
    final String fileName = Instant.now().toEpochMilli() + "-" + slugify(checkpointName) + ".png";
    final Path filePath = SCREENSHOT_DIR.resolve(fileName);
    page.screenshot(new Page.ScreenshotOptions().setPath(filePath).setFullPage(fullPage));
  }

  private void assertAnyVisible(final String description, final Locator... candidates) {
    for (Locator candidate : candidates) {
      if (candidate != null && isVisible(candidate)) {
        return;
      }
    }
    throw new AssertionError("Expected visible element for: " + description);
  }

  private Locator firstVisible(final Locator... candidates) {
    for (Locator candidate : candidates) {
      if (candidate != null && isVisible(candidate)) {
        return candidate.first();
      }
    }
    throw new AssertionError("No visible locator found among candidates.");
  }

  private boolean isVisible(final Locator locator) {
    return isVisible(locator, 1500);
  }

  private boolean isVisible(final Locator locator, final int timeoutMs) {
    try {
      locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  private String firstNonBlank(final String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private String slugify(final String value) {
    final String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    return normalized.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
  }

  private String normalized(final String value) {
    return value == null ? "" : value.toLowerCase();
  }

  private interface StepAction {
    void run() throws Exception;
  }

  private static final class StepOutcome {
    private final boolean passed;
    private final String details;

    private StepOutcome(final boolean passed, final String details) {
      this.passed = passed;
      this.details = details;
    }

    private static StepOutcome pass() {
      return new StepOutcome(true, null);
    }

    private static StepOutcome fail(final String details) {
      return new StepOutcome(false, details);
    }
  }
}
