package io.proleap.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
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
import com.microsoft.playwright.options.WaitUntilState;

/**
 * End-to-end validation for SaleADS "Mi Negocio" workflow.
 *
 * <p>
 * The test is environment-agnostic and intentionally does not hardcode any
 * SaleADS domain. To run it, provide the login/start URL through
 * {@code saleads.start.url} system property (or {@code SALEADS_START_URL} env
 * var).
 * </p>
 *
 * <p>
 * Execution is opt-in to avoid breaking unrelated CI pipelines in this
 * repository. Set {@code saleads.test.enabled=true} (or
 * {@code SALEADS_TEST_ENABLED=true}) to run.
 * </p>
 */
public class SaleadsMiNegocioWorkflowE2ETest {

  private static final int DEFAULT_TIMEOUT_MS = 15000;
  private static final int POPUP_TIMEOUT_MS = 6000;
  private static final String GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
  private static final DateTimeFormatter EVIDENCE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

  private final Map<String, StepResult> report = new LinkedHashMap<>();

  private Playwright playwright;
  private Browser browser;
  private BrowserContext context;
  private Page page;
  private Path evidenceDir;

  @Before
  public void setUp() throws IOException {
    final boolean enabled = Boolean.parseBoolean(
        valueFromPropertyOrEnv("saleads.test.enabled", "SALEADS_TEST_ENABLED", "false"));
    Assume.assumeTrue("Set saleads.test.enabled=true to execute SaleADS E2E workflow.", enabled);

    evidenceDir = Files.createDirectories(Path.of("target", "saleads-e2e", LocalDateTime.now().format(EVIDENCE_TIMESTAMP)));

    playwright = Playwright.create();
    final boolean headless = Boolean.parseBoolean(
        valueFromPropertyOrEnv("saleads.headless", "SALEADS_HEADLESS", "true"));
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
    context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
    page = context.newPage();

    final String startUrl = valueFromPropertyOrEnv("saleads.start.url", "SALEADS_START_URL", "");
    if (!startUrl.isBlank()) {
      page.navigate(startUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
    }
    waitForUiToLoad(page);
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
  public void saleads_mi_negocio_full_test() {
    runStep("Login", this::loginWithGoogle);
    runStep("Mi Negocio menu", this::openMiNegocioMenu);
    runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
    runStep("Administrar Negocios view", this::openAdministrarNegocios);
    runStep("Informaci\u00f3n General", this::validateInformacionGeneral);
    runStep("Detalles de la Cuenta", this::validateDetallesCuenta);
    runStep("Tus Negocios", this::validateTusNegocios);
    runStep("T\u00e9rminos y Condiciones", this::validateTerminosYCondiciones);
    runStep("Pol\u00edtica de Privacidad", this::validatePoliticaPrivacidad);

    final String summary = buildReportSummary();
    System.out.println(summary);

    final List<String> failed = new ArrayList<>();
    for (Map.Entry<String, StepResult> entry : report.entrySet()) {
      if ("FAIL".equals(entry.getValue().status)) {
        failed.add(entry.getKey() + ": " + entry.getValue().detail);
      }
    }
    assertTrue("One or more SaleADS validations failed:\n" + String.join("\n", failed) + "\n\n" + summary,
        failed.isEmpty());
  }

  private String loginWithGoogle() {
    assertTrue("Expected browser to be already on login page, but current URL is blank. "
        + "Set saleads.start.url / SALEADS_START_URL.",
        !Objects.equals("about:blank", page.url()));

    final Locator loginButton = firstVisibleLocator("Google login button",
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
            .setName(Pattern.compile("(?i)(sign\\s*in\\s*with\\s*google|iniciar\\s*sesi[o\\u00f3]n\\s*con\\s*google|continuar\\s*con\\s*google|google)"))),
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions()
            .setName(Pattern.compile("(?i)(google|iniciar\\s*sesi[o\\u00f3]n|sign\\s*in)"))),
        page.getByText(Pattern.compile("(?i)(sign\\s*in\\s*with\\s*google|iniciar\\s*sesi[o\\u00f3]n\\s*con\\s*google|continuar\\s*con\\s*google)")));

    final Page popupOrNull = clickPossiblyOpeningPopup(loginButton);
    if (popupOrNull != null) {
      waitForUiToLoad(popupOrNull);
      maybeSelectGoogleAccount(popupOrNull);
      waitForUiToLoad(page);
    } else {
      maybeSelectGoogleAccount(page);
    }

    assertVisible("main application interface", page.getByRole(AriaRole.MAIN), page.locator("main"));
    assertVisible("left sidebar navigation", page.locator("aside"), page.locator("nav"),
        page.getByText(Pattern.compile("(?i)negocio")));
    screenshot(page, "01-dashboard-loaded.png", true);
    return "Dashboard loaded with sidebar visible.";
  }

  private String openMiNegocioMenu() {
    final Locator negocioSection = firstVisibleLocator("Negocio section",
        page.getByRole(AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^negocio$|\\bnegocio\\b"))),
        page.getByText(Pattern.compile("(?i)^negocio$")));
    clickAndWait(page, negocioSection);

    final Locator miNegocio = firstVisibleLocator("Mi Negocio option",
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)mi\\s*negocio"))),
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)mi\\s*negocio"))),
        page.getByText(Pattern.compile("(?i)mi\\s*negocio")));
    clickAndWait(page, miNegocio);

    assertVisible("Agregar Negocio in submenu", page.getByText(Pattern.compile("(?i)agregar\\s*negocio")),
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)agregar\\s*negocio"))),
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)agregar\\s*negocio"))));

    assertVisible("Administrar Negocios in submenu",
        page.getByText(Pattern.compile("(?i)administrar\\s*negocios")),
        page.getByRole(AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName(Pattern.compile("(?i)administrar\\s*negocios"))),
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)administrar\\s*negocios"))));

    screenshot(page, "02-mi-negocio-menu-expanded.png", false);
    return "Mi Negocio submenu expanded and options visible.";
  }

  private String validateAgregarNegocioModal() {
    final Locator agregarNegocio = firstVisibleLocator("Agregar Negocio action",
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)agregar\\s*negocio"))),
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)agregar\\s*negocio"))),
        page.getByText(Pattern.compile("(?i)agregar\\s*negocio")));
    clickAndWait(page, agregarNegocio);

    final Locator modalTitle = firstVisibleLocator("Crear Nuevo Negocio modal title",
        page.getByRole(AriaRole.HEADING,
            new Page.GetByRoleOptions().setName(Pattern.compile("(?i)crear\\s*nuevo\\s*negocio"))),
        page.getByText(Pattern.compile("(?i)crear\\s*nuevo\\s*negocio")));
    assertVisible("Nombre del Negocio input",
        page.getByLabel(Pattern.compile("(?i)nombre\\s*del\\s*negocio")),
        page.getByPlaceholder("Nombre del Negocio"),
        page.locator("input[name*='nombre' i]"));
    assertVisible("quota text", page.getByText(Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios")));
    assertVisible("Cancelar button",
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cancelar"))),
        page.getByText(Pattern.compile("(?i)^cancelar$")));
    assertVisible("Crear Negocio button",
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)crear\\s*negocio"))),
        page.getByText(Pattern.compile("(?i)crear\\s*negocio")));

    screenshot(page, "03-agregar-negocio-modal.png", false);

    final Locator nombreInput = firstVisibleLocator("Nombre del Negocio input for optional action",
        page.getByLabel(Pattern.compile("(?i)nombre\\s*del\\s*negocio")),
        page.getByPlaceholder("Nombre del Negocio"),
        page.locator("input[name*='nombre' i]"));
    nombreInput.first().click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
    waitForUiToLoad(page);
    nombreInput.first().fill("Negocio Prueba Automatizacion");

    final Locator cancelarButton = firstVisibleLocator("Cancelar button for closing modal",
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cancelar"))),
        page.getByText(Pattern.compile("(?i)^cancelar$")));
    clickAndWait(page, cancelarButton);
    assertTrue("Expected 'Crear Nuevo Negocio' modal to be closed after Cancelar.",
        modalTitle.first().isHidden(new Locator.IsHiddenOptions().setTimeout(DEFAULT_TIMEOUT_MS)));
    return "Agregar Negocio modal validated and closed with Cancelar.";
  }

  private String openAdministrarNegocios() {
    Locator administrarNegocios = page.getByText(Pattern.compile("(?i)administrar\\s*negocios"));
    if (!isVisible(administrarNegocios, 1500)) {
      final Locator miNegocio = firstVisibleLocator("Mi Negocio option for re-expansion",
          page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)mi\\s*negocio"))),
          page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)mi\\s*negocio"))),
          page.getByText(Pattern.compile("(?i)mi\\s*negocio")));
      clickAndWait(page, miNegocio);
      administrarNegocios = page.getByText(Pattern.compile("(?i)administrar\\s*negocios"));
    }

    final Locator administrarAction = firstVisibleLocator("Administrar Negocios action",
        administrarNegocios,
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)administrar\\s*negocios"))),
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)administrar\\s*negocios"))));
    clickAndWait(page, administrarAction);

    assertVisible("Informacion General section",
        page.getByRole(AriaRole.HEADING,
            new Page.GetByRoleOptions().setName(Pattern.compile("(?i)informaci[o\\u00f3]n\\s*general"))),
        page.getByText(Pattern.compile("(?i)informaci[o\\u00f3]n\\s*general")));
    assertVisible("Detalles de la Cuenta section",
        page.getByRole(AriaRole.HEADING,
            new Page.GetByRoleOptions().setName(Pattern.compile("(?i)detalles\\s*de\\s*la\\s*cuenta"))),
        page.getByText(Pattern.compile("(?i)detalles\\s*de\\s*la\\s*cuenta")));
    assertVisible("Tus Negocios section",
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)tus\\s*negocios"))),
        page.getByText(Pattern.compile("(?i)tus\\s*negocios")));
    assertVisible("Seccion Legal section",
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)secci[o\\u00f3]n\\s*legal"))),
        page.getByText(Pattern.compile("(?i)secci[o\\u00f3]n\\s*legal")));

    screenshot(page, "04-administrar-negocios-account-page.png", true);
    return "Administrar Negocios view loaded with all required sections.";
  }

  private String validateInformacionGeneral() {
    final String pageText = page.textContent("body");
    assertMatches("Expected user email to be visible.", pageText,
        Pattern.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}"));
    assertTrue("Expected user name hint to be visible.",
        containsPattern(pageText, Pattern.compile("(?i)(nombre|usuario|juan)")));
    assertTrue("Expected BUSINESS PLAN text to be visible.",
        containsPattern(pageText, Pattern.compile("(?i)business\\s*plan")));
    assertVisible("Cambiar Plan button",
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cambiar\\s*plan"))),
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cambiar\\s*plan"))),
        page.getByText(Pattern.compile("(?i)cambiar\\s*plan")));
    return "Informacion General validated (name hint, email, BUSINESS PLAN, Cambiar Plan).";
  }

  private String validateDetallesCuenta() {
    assertVisible("Cuenta creada text", page.getByText(Pattern.compile("(?i)cuenta\\s*creada")));
    assertVisible("Estado activo text", page.getByText(Pattern.compile("(?i)estado\\s*activo")));
    assertVisible("Idioma seleccionado text", page.getByText(Pattern.compile("(?i)idioma\\s*seleccionado")));
    return "Detalles de la Cuenta validated.";
  }

  private String validateTusNegocios() {
    assertVisible("Tus Negocios section",
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)tus\\s*negocios"))),
        page.getByText(Pattern.compile("(?i)tus\\s*negocios")));
    assertVisible("Agregar Negocio button in business list",
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)agregar\\s*negocio"))),
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)agregar\\s*negocio"))),
        page.getByText(Pattern.compile("(?i)agregar\\s*negocio")));
    assertVisible("Tienes 2 de 3 negocios text",
        page.getByText(Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios")));
    final Locator businessRows = page.locator(
        "section:has-text('Tus Negocios') [role='row'], section:has-text('Tus Negocios') li, section:has-text('Tus Negocios') .card");
    assertTrue("Expected business list content to be visible in 'Tus Negocios'.", businessRows.count() > 0);
    return "Tus Negocios validated.";
  }

  private String validateTerminosYCondiciones() {
    final String url = validateLegalDocument(
        Pattern.compile("(?i)t[e\\u00e9]rminos\\s*y\\s*condiciones"),
        Pattern.compile("(?i)t[e\\u00e9]rminos\\s*y\\s*condiciones"),
        "05-terminos-y-condiciones.png");
    return "Legal page validated. Final URL: " + url;
  }

  private String validatePoliticaPrivacidad() {
    final String url = validateLegalDocument(
        Pattern.compile("(?i)pol[i\\u00ed]tica\\s*de\\s*privacidad"),
        Pattern.compile("(?i)pol[i\\u00ed]tica\\s*de\\s*privacidad"),
        "06-politica-de-privacidad.png");
    return "Legal page validated. Final URL: " + url;
  }

  private String validateLegalDocument(final Pattern linkText, final Pattern headingText, final String screenshotName) {
    final Locator link = firstVisibleLocator("legal link " + linkText.pattern(),
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkText)),
        page.getByText(linkText));

    final Page legalPage = clickPossiblyOpeningPopup(link);
    final Page targetPage = legalPage == null ? page : legalPage;
    waitForUiToLoad(targetPage);

    assertVisible(targetPage, "legal heading",
        targetPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(headingText)),
        targetPage.getByText(headingText));

    final String legalText = targetPage.textContent("body");
    assertTrue("Expected legal page to contain descriptive text content.",
        legalText != null && legalText.replaceAll("\\s+", " ").trim().length() > 120);

    screenshot(targetPage, screenshotName, true);
    final String finalUrl = targetPage.url();

    if (legalPage != null) {
      legalPage.close();
      page.bringToFront();
      waitForUiToLoad(page);
    } else {
      try {
        page.goBack(new Page.GoBackOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
      } catch (final RuntimeException ignored) {
        // If navigation history is unavailable, keep current page context.
      }
      waitForUiToLoad(page);
    }

    return finalUrl;
  }

  private void runStep(final String reportField, final StepAction action) {
    try {
      final String detail = action.run();
      report.put(reportField, StepResult.pass(detail));
    } catch (final Throwable throwable) {
      report.put(reportField, StepResult.fail(sanitizeError(throwable)));
    }
  }

  private String buildReportSummary() {
    final StringBuilder sb = new StringBuilder("SaleADS Mi Negocio workflow report:\n");
    for (Map.Entry<String, StepResult> entry : report.entrySet()) {
      sb.append(" - ").append(entry.getKey()).append(": ").append(entry.getValue().status);
      if (!entry.getValue().detail.isBlank()) {
        sb.append(" (").append(entry.getValue().detail).append(")");
      }
      sb.append('\n');
    }
    sb.append("Evidence directory: ").append(evidenceDir.toAbsolutePath());
    return sb.toString();
  }

  private void clickAndWait(final Page targetPage, final Locator locator) {
    locator.first().click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
    waitForUiToLoad(targetPage);
  }

  private Page clickPossiblyOpeningPopup(final Locator clickable) {
    try {
      final Page popup = page.waitForPopup(new Page.WaitForPopupOptions().setTimeout(POPUP_TIMEOUT_MS),
          () -> clickable.first().click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS)));
      waitForUiToLoad(page);
      waitForUiToLoad(popup);
      return popup;
    } catch (final TimeoutError noPopupOpened) {
      waitForUiToLoad(page);
      return null;
    }
  }

  private void maybeSelectGoogleAccount(final Page targetPage) {
    final Locator account = targetPage.getByText(GOOGLE_EMAIL, new Page.GetByTextOptions().setExact(true));
    if (isVisible(account, 6000)) {
      clickAndWait(targetPage, account);
    }
  }

  private void waitForUiToLoad(final Page targetPage) {
    try {
      targetPage.waitForLoadState(LoadState.NETWORKIDLE,
          new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
    } catch (final RuntimeException ignored) {
      try {
        targetPage.waitForLoadState(LoadState.DOMCONTENTLOADED,
            new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
      } catch (final RuntimeException ignoredAgain) {
        // Fallback to short static wait when explicit load states are not emitted.
      }
    }
    targetPage.waitForTimeout(400);
  }

  private void screenshot(final Page targetPage, final String fileName, final boolean fullPage) {
    targetPage.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(fileName)).setFullPage(fullPage));
  }

  private Locator firstVisibleLocator(final String description, final Locator... candidates) {
    for (Locator candidate : candidates) {
      if (isVisible(candidate, 2000)) {
        return candidate.first();
      }
    }
    throw new AssertionError("Could not find visible locator for: " + description);
  }

  private void assertVisible(final String description, final Locator... candidates) {
    firstVisibleLocator(description, candidates);
  }

  private void assertVisible(final Page targetPage, final String description, final Locator... candidates) {
    for (Locator candidate : candidates) {
      if (isVisible(candidate, 2000)) {
        return;
      }
    }
    throw new AssertionError("Could not find visible locator for: " + description + " on page " + targetPage.url());
  }

  private boolean isVisible(final Locator locator, final int timeoutMs) {
    try {
      return locator.first().isVisible(new Locator.IsVisibleOptions().setTimeout(timeoutMs));
    } catch (final RuntimeException ignored) {
      return false;
    }
  }

  private boolean containsPattern(final String text, final Pattern pattern) {
    if (text == null) {
      return false;
    }
    final Matcher matcher = pattern.matcher(text);
    return matcher.find();
  }

  private void assertMatches(final String message, final String text, final Pattern pattern) {
    assertTrue(message, containsPattern(text, pattern));
  }

  private String sanitizeError(final Throwable throwable) {
    final String message = throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    return message.replace('\n', ' ').trim();
  }

  private String valueFromPropertyOrEnv(final String propertyKey, final String envKey, final String defaultValue) {
    final String fromProperty = System.getProperty(propertyKey);
    if (fromProperty != null && !fromProperty.isBlank()) {
      return fromProperty.trim();
    }
    final String fromEnv = System.getenv(envKey);
    if (fromEnv != null && !fromEnv.isBlank()) {
      return fromEnv.trim();
    }
    return defaultValue;
  }

  @FunctionalInterface
  private interface StepAction {
    String run();
  }

  private static final class StepResult {
    private final String status;
    private final String detail;

    private StepResult(final String status, final String detail) {
      this.status = status;
      this.detail = detail == null ? "" : detail;
    }

    private static StepResult pass(final String detail) {
      return new StepResult("PASS", detail);
    }

    private static StepResult fail(final String detail) {
      return new StepResult("FAIL", detail);
    }
  }

}
