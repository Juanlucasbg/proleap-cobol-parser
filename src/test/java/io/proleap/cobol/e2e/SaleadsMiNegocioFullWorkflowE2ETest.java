package io.proleap.cobol.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Full E2E workflow validation for SaleADS "Mi Negocio".
 *
 * Execution notes:
 * - Disabled by default to avoid impacting regular parser test runs.
 * - Enable with: -Dsaleads.e2e.enabled=true
 * - Provide target login page URL dynamically with: -Dsaleads.startUrl=https://...
 */
public class SaleadsMiNegocioFullWorkflowE2ETest {

  private static final double DEFAULT_TIMEOUT_MS = 20_000;
  private static final double SHORT_TIMEOUT_MS = 7_000;
  private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
  private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");

  private final Map<String, StepResult> reportByField = new LinkedHashMap<>();
  private final Map<String, String> legalUrls = new LinkedHashMap<>();

  private Playwright playwright;
  private Browser browser;
  private BrowserContext context;
  private Page page;
  private Path artifactsDir;
  private String accountPageUrl;

  @Before
  public void setUp() throws IOException {
    final boolean enabled = Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false"));
    Assume.assumeTrue("SaleADS E2E is disabled. Run with -Dsaleads.e2e.enabled=true", enabled);

    final String startUrl = firstNonBlank(
        System.getProperty("saleads.startUrl"),
        System.getenv("SALEADS_START_URL")
    );
    Assume.assumeTrue("Provide SaleADS login URL via -Dsaleads.startUrl or SALEADS_START_URL.",
        startUrl != null && !startUrl.isBlank());

    artifactsDir = Files.createDirectories(Path.of("target", "saleads-e2e-artifacts",
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now())));

    playwright = Playwright.create();
    final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "true"));
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
    context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 900));
    page = context.newPage();

    page.navigate(startUrl);
    waitForUiLoad(page);
  }

  @After
  public void tearDown() throws IOException {
    writeFinalReport();

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
    runStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
    runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

    final List<String> failedSteps = reportByField.entrySet().stream()
        .filter(entry -> !entry.getValue().passed)
        .map(entry -> entry.getKey() + " -> " + entry.getValue().details)
        .collect(Collectors.toList());

    Assert.assertTrue("SaleADS workflow validation failures:\n" + String.join("\n", failedSteps), failedSteps.isEmpty());
  }

  private void stepLoginWithGoogle() throws IOException {
    final Locator loginButton = requiredVisible("Google login button", SHORT_TIMEOUT_MS,
        page.locator("button:has-text(\"Sign in with Google\")"),
        page.locator("button:has-text(\"Iniciar sesión con Google\")"),
        page.locator("button:has-text(\"Continuar con Google\")"),
        page.locator("[role='button']:has-text(\"Google\")"),
        page.locator("text=Sign in with Google"),
        page.locator("text=Iniciar sesión con Google"),
        page.locator("text=Continuar con Google")
    );

    final Page authenticationPage = clickAndCapturePossibleNewPage(loginButton, page);
    maybeSelectGoogleAccount(authenticationPage);
    if (authenticationPage != page) {
      waitForUiLoad(page);
    }

    requiredVisible("main application interface", DEFAULT_TIMEOUT_MS,
        page.locator("main"),
        page.locator("[role='main']"),
        page.locator("aside"),
        page.locator("nav")
    );

    requiredVisible("left sidebar navigation", DEFAULT_TIMEOUT_MS,
        page.locator("aside:has-text(\"Negocio\")"),
        page.locator("nav:has-text(\"Negocio\")"),
        page.locator("text=Negocio")
    );

    captureScreenshot(page, "01-dashboard-loaded.png", true);
  }

  private void stepOpenMiNegocioMenu() throws IOException {
    requiredVisible("left sidebar", DEFAULT_TIMEOUT_MS,
        page.locator("aside"),
        page.locator("nav")
    );

    clickAndWait(requiredVisible("Mi Negocio option", DEFAULT_TIMEOUT_MS,
        page.locator("aside >> text=Mi Negocio"),
        page.locator("nav >> text=Mi Negocio"),
        page.locator("text=Mi Negocio")
    ), page);

    requiredVisible("Agregar Negocio submenu option", DEFAULT_TIMEOUT_MS,
        page.locator("text=Agregar Negocio")
    );
    requiredVisible("Administrar Negocios submenu option", DEFAULT_TIMEOUT_MS,
        page.locator("text=Administrar Negocios")
    );

    captureScreenshot(page, "02-mi-negocio-menu-expanded.png", false);
  }

  private void stepValidateAgregarNegocioModal() throws IOException {
    clickAndWait(requiredVisible("Agregar Negocio menu item", DEFAULT_TIMEOUT_MS,
        page.locator("text=Agregar Negocio")
    ), page);

    requiredVisible("Crear Nuevo Negocio modal title", DEFAULT_TIMEOUT_MS,
        page.locator("text=Crear Nuevo Negocio")
    );

    final Locator businessNameInput = requiredVisible("Nombre del Negocio input", DEFAULT_TIMEOUT_MS,
        page.locator("input[placeholder*='Nombre del Negocio']"),
        page.locator("input[name*='negocio']"),
        page.locator("input[name*='nombre']"),
        page.locator("input[type='text']")
    );

    requiredVisible("business limit text", DEFAULT_TIMEOUT_MS,
        page.locator("text=Tienes 2 de 3 negocios")
    );
    requiredVisible("Cancelar button", DEFAULT_TIMEOUT_MS,
        page.locator("button:has-text(\"Cancelar\")")
    );
    requiredVisible("Crear Negocio button", DEFAULT_TIMEOUT_MS,
        page.locator("button:has-text(\"Crear Negocio\")")
    );

    captureScreenshot(page, "03-agregar-negocio-modal.png", false);

    clickAndWait(businessNameInput, page);
    page.keyboard().type("Negocio Prueba Automatización");
    clickAndWait(requiredVisible("Cancelar button", DEFAULT_TIMEOUT_MS,
        page.locator("button:has-text(\"Cancelar\")")
    ), page);

    assertNotVisible("Crear Nuevo Negocio modal", page.locator("text=Crear Nuevo Negocio"));
  }

  private void stepOpenAdministrarNegocios() throws IOException {
    ensureMiNegocioExpanded();

    clickAndWait(requiredVisible("Administrar Negocios option", DEFAULT_TIMEOUT_MS,
        page.locator("text=Administrar Negocios")
    ), page);

    requiredVisible("Información General section", DEFAULT_TIMEOUT_MS,
        page.locator("text=Información General")
    );
    requiredVisible("Detalles de la Cuenta section", DEFAULT_TIMEOUT_MS,
        page.locator("text=Detalles de la Cuenta")
    );
    requiredVisible("Tus Negocios section", DEFAULT_TIMEOUT_MS,
        page.locator("text=Tus Negocios")
    );
    requiredVisible("Sección Legal section", DEFAULT_TIMEOUT_MS,
        page.locator("text=Sección Legal")
    );

    accountPageUrl = page.url();
    captureScreenshot(page, "04-administrar-negocios-full-page.png", true);
  }

  private void stepValidateInformacionGeneral() {
    requiredVisible("Información General heading", DEFAULT_TIMEOUT_MS, page.locator("text=Información General"));
    requiredVisible("BUSINESS PLAN text", DEFAULT_TIMEOUT_MS, page.locator("text=BUSINESS PLAN"));
    requiredVisible("Cambiar Plan button", DEFAULT_TIMEOUT_MS, page.locator("button:has-text(\"Cambiar Plan\")"));

    final String infoText = sectionText("Información General");
    Assert.assertTrue("Expected a visible user email in Información General.",
        EMAIL_PATTERN.matcher(infoText).find());

    final boolean hasPossibleName = Arrays.stream(infoText.split("\\R"))
        .map(String::trim)
        .filter(line -> !line.isEmpty())
        .filter(line -> !line.toLowerCase(Locale.ROOT).contains("información general"))
        .filter(line -> !line.toLowerCase(Locale.ROOT).contains("business plan"))
        .filter(line -> !line.toLowerCase(Locale.ROOT).contains("cambiar plan"))
        .anyMatch(line -> !line.contains("@"));

    Assert.assertTrue("Expected a visible user name in Información General.", hasPossibleName);
  }

  private void stepValidateDetallesCuenta() {
    requiredVisible("Detalles de la Cuenta heading", DEFAULT_TIMEOUT_MS, page.locator("text=Detalles de la Cuenta"));
    requiredVisible("Cuenta creada text", DEFAULT_TIMEOUT_MS, page.locator("text=Cuenta creada"));
    requiredVisible("Estado activo text", DEFAULT_TIMEOUT_MS, page.locator("text=Estado activo"));
    requiredVisible("Idioma seleccionado text", DEFAULT_TIMEOUT_MS, page.locator("text=Idioma seleccionado"));
  }

  private void stepValidateTusNegocios() {
    requiredVisible("Tus Negocios heading", DEFAULT_TIMEOUT_MS, page.locator("text=Tus Negocios"));
    requiredVisible("Agregar Negocio button", DEFAULT_TIMEOUT_MS,
        page.locator("button:has-text(\"Agregar Negocio\")"),
        page.locator("text=Agregar Negocio"));
    requiredVisible("business limit text", DEFAULT_TIMEOUT_MS, page.locator("text=Tienes 2 de 3 negocios"));

    final String businessesText = sectionText("Tus Negocios");
    final List<String> candidateLines = Arrays.stream(businessesText.split("\\R"))
        .map(String::trim)
        .filter(line -> !line.isEmpty())
        .filter(line -> !line.equalsIgnoreCase("Tus Negocios"))
        .filter(line -> !line.equalsIgnoreCase("Agregar Negocio"))
        .collect(Collectors.toList());

    Assert.assertTrue("Expected visible business list content in Tus Negocios section.", candidateLines.size() >= 2);
  }

  private void stepValidateTerminosYCondiciones() throws IOException {
    final String url = validateLegalLink("Términos y Condiciones", "Terminos y Condiciones",
        "05-terminos-y-condiciones.png", "Términos y Condiciones");
    legalUrls.put("Términos y Condiciones", url);
  }

  private void stepValidatePoliticaPrivacidad() throws IOException {
    final String url = validateLegalLink("Política de Privacidad", "Politica de Privacidad",
        "06-politica-de-privacidad.png", "Política de Privacidad");
    legalUrls.put("Política de Privacidad", url);
  }

  private String validateLegalLink(final String primaryText, final String fallbackText,
                                   final String screenshotName, final String expectedHeading) throws IOException {
    requiredVisible("Sección Legal heading", DEFAULT_TIMEOUT_MS, page.locator("text=Sección Legal"));

    final Locator link = requiredVisible("legal link " + primaryText, DEFAULT_TIMEOUT_MS,
        page.locator("a:has-text(\"" + primaryText + "\")"),
        page.locator("button:has-text(\"" + primaryText + "\")"),
        page.locator("text=" + primaryText),
        page.locator("a:has-text(\"" + fallbackText + "\")"),
        page.locator("button:has-text(\"" + fallbackText + "\")"),
        page.locator("text=" + fallbackText)
    );

    final Page legalPage = clickAndCapturePossibleNewPage(link, page);
    final boolean openedInNewTab = legalPage != page;
    waitForUiLoad(legalPage);

    requiredVisible("legal heading " + expectedHeading, DEFAULT_TIMEOUT_MS,
        legalPage.locator("h1:has-text(\"" + expectedHeading + "\")"),
        legalPage.locator("h2:has-text(\"" + expectedHeading + "\")"),
        legalPage.locator("text=" + expectedHeading)
    );

    final String legalText = legalPage.locator("body").innerText();
    Assert.assertTrue("Expected legal content text on " + expectedHeading + " page.", legalText.trim().length() > 180);
    captureScreenshot(legalPage, screenshotName, true);

    final String currentUrl = legalPage.url();
    if (openedInNewTab) {
      legalPage.close();
      page.bringToFront();
      waitForUiLoad(page);
    } else if (accountPageUrl != null && !accountPageUrl.isBlank()) {
      page.navigate(accountPageUrl);
      waitForUiLoad(page);
    }

    return currentUrl;
  }

  private void ensureMiNegocioExpanded() {
    if (!isVisible(page.locator("text=Administrar Negocios"), 1_500)) {
      clickAndWait(requiredVisible("Mi Negocio option", DEFAULT_TIMEOUT_MS,
          page.locator("aside >> text=Mi Negocio"),
          page.locator("nav >> text=Mi Negocio"),
          page.locator("text=Mi Negocio")
      ), page);
    }
  }

  private void maybeSelectGoogleAccount(final Page activePage) {
    final Locator accountLocator = firstVisible(SHORT_TIMEOUT_MS,
        activePage.locator("text=" + GOOGLE_ACCOUNT_EMAIL),
        activePage.locator("[data-email='" + GOOGLE_ACCOUNT_EMAIL + "']"),
        activePage.locator("div:has-text(\"" + GOOGLE_ACCOUNT_EMAIL + "\")")
    );

    if (accountLocator != null) {
      clickAndWait(accountLocator, activePage);
    }
  }

  private void runStep(final String reportField, final CheckedRunnable action) {
    try {
      action.run();
      reportByField.put(reportField, StepResult.pass());
    } catch (Throwable error) {
      reportByField.put(reportField, StepResult.fail(error));
    }
  }

  private Locator requiredVisible(final String description, final double timeoutMs, final Locator... candidates) {
    final Locator locator = firstVisible(timeoutMs, candidates);
    if (locator == null) {
      throw new AssertionError("Could not find visible element: " + description);
    }
    return locator;
  }

  private Locator firstVisible(final double timeoutMs, final Locator... candidates) {
    for (Locator candidate : candidates) {
      final Locator first = candidate.first();
      try {
        first.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(timeoutMs));
        return first;
      } catch (PlaywrightException ignored) {
        // try next candidate
      }
    }
    return null;
  }

  private boolean isVisible(final Locator locator, final double timeoutMs) {
    try {
      locator.first().waitFor(new Locator.WaitForOptions()
          .setState(WaitForSelectorState.VISIBLE)
          .setTimeout(timeoutMs));
      return true;
    } catch (PlaywrightException ignored) {
      return false;
    }
  }

  private void clickAndWait(final Locator locator, final Page targetPage) {
    locator.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
    waitForUiLoad(targetPage);
  }

  private Page clickAndCapturePossibleNewPage(final Locator locator, final Page sourcePage) {
    try {
      final Page newPage = context.waitForPage(
          () -> locator.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS)),
          new BrowserContext.WaitForPageOptions().setTimeout(SHORT_TIMEOUT_MS));
      waitForUiLoad(newPage);
      return newPage;
    } catch (PlaywrightException ignored) {
      clickAndWait(locator, sourcePage);
      return sourcePage;
    }
  }

  private void waitForUiLoad(final Page targetPage) {
    targetPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
    try {
      targetPage.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(SHORT_TIMEOUT_MS));
    } catch (PlaywrightException ignored) {
      // Some views keep background requests alive, DOMCONTENTLOADED is enough.
    }
    targetPage.waitForTimeout(500);
  }

  private void assertNotVisible(final String description, final Locator locator) {
    try {
      locator.first().waitFor(new Locator.WaitForOptions()
          .setState(WaitForSelectorState.HIDDEN)
          .setTimeout(DEFAULT_TIMEOUT_MS));
    } catch (PlaywrightException error) {
      throw new AssertionError(description + " is still visible.", error);
    }
  }

  private String sectionText(final String heading) {
    final Locator sectionRoot = requiredVisible("section heading " + heading, DEFAULT_TIMEOUT_MS, page.locator("text=" + heading))
        .locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
    return sectionRoot.innerText();
  }

  private void captureScreenshot(final Page targetPage, final String filename, final boolean fullPage) throws IOException {
    final Path screenshotPath = artifactsDir.resolve(filename);
    targetPage.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
  }

  private void writeFinalReport() throws IOException {
    if (artifactsDir == null) {
      return;
    }

    final List<String> lines = new ArrayList<>();
    lines.add("saleads_mi_negocio_full_test");
    lines.add("timestamp=" + LocalDateTime.now());
    lines.add("");
    lines.add("Final Report:");

    final List<String> reportFields = List.of(
        "Login",
        "Mi Negocio menu",
        "Agregar Negocio modal",
        "Administrar Negocios view",
        "Información General",
        "Detalles de la Cuenta",
        "Tus Negocios",
        "Términos y Condiciones",
        "Política de Privacidad"
    );

    for (String field : reportFields) {
      final StepResult result = reportByField.getOrDefault(field, StepResult.fail("NOT_EXECUTED"));
      lines.add("- " + field + ": " + (result.passed ? "PASS" : "FAIL"));
      if (!result.details.isBlank()) {
        lines.add("  details: " + result.details);
      }
      if (legalUrls.containsKey(field)) {
        lines.add("  final_url: " + legalUrls.get(field));
      }
    }

    Files.writeString(artifactsDir.resolve("final-report.txt"),
        String.join(System.lineSeparator(), lines), StandardCharsets.UTF_8);
  }

  private static String firstNonBlank(final String first, final String second) {
    if (first != null && !first.isBlank()) {
      return first;
    }
    if (second != null && !second.isBlank()) {
      return second;
    }
    return null;
  }

  @FunctionalInterface
  private interface CheckedRunnable {
    void run() throws Exception;
  }

  private static final class StepResult {
    private final boolean passed;
    private final String details;

    private StepResult(final boolean passed, final String details) {
      this.passed = passed;
      this.details = details;
    }

    private static StepResult pass() {
      return new StepResult(true, "");
    }

    private static StepResult fail(final Throwable throwable) {
      final String message = throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
      return new StepResult(false, message);
    }

    private static StepResult fail(final String message) {
      return new StepResult(false, message);
    }
  }
}
