package io.proleap.e2e.saleads;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

public class SaleadsMiNegocioFullTest {

  private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
  private static final long SHORT_WAIT_MS = 800L;
  private static final long DEFAULT_TIMEOUT_MS = 15000L;
  private static final long POPUP_WAIT_MS = 10000L;
  private static final DateTimeFormatter TS_FORMAT =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

  private final Map<String, StepResult> report = new LinkedHashMap<>();
  private final String runId = LocalDateTime.now().format(TS_FORMAT);

  private Playwright playwright;
  private Browser browser;
  private BrowserContext context;
  private Page appPage;
  private boolean ownsBrowser;
  private Path evidenceDir;

  @Before
  public void setUp() throws IOException {
    final String cdpUrl = nonBlank(System.getenv("PLAYWRIGHT_CDP_URL"));
    final String baseUrl = nonBlank(System.getenv("SALEADS_BASE_URL"));

    Assume.assumeTrue(
        "Set PLAYWRIGHT_CDP_URL (reuse existing browser session) or SALEADS_BASE_URL (login URL).",
        cdpUrl != null || baseUrl != null);

    initializeReport();

    evidenceDir = Paths.get("target", "saleads-mi-negocio-evidence", runId);
    Files.createDirectories(evidenceDir);

    playwright = Playwright.create();

    if (cdpUrl != null) {
      ownsBrowser = false;
      browser = playwright.chromium().connectOverCDP(cdpUrl);
      List<BrowserContext> contexts = browser.contexts();
      context = contexts.isEmpty() ? browser.newContext() : contexts.get(0);
      appPage = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
    } else {
      ownsBrowser = true;
      boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("HEADLESS", "true"));
      browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
      context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
      appPage = context.newPage();
      appPage.navigate(baseUrl);
    }

    appPage.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
    waitForUiLoad(appPage);
  }

  @After
  public void tearDown() throws IOException {
    if (!report.isEmpty() && evidenceDir != null) {
      writeFinalReport();
    }

    if (ownsBrowser) {
      if (context != null) {
        context.close();
      }
      if (browser != null) {
        browser.close();
      }
    }

    if (playwright != null) {
      playwright.close();
    }
  }

  @Test
  public void saleadsMiNegocioFullWorkflow() throws IOException {
    runStep("Login", this::stepLoginWithGoogle);
    runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
    runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
    runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
    runStep("Información General", this::stepValidateInformacionGeneral);
    runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
    runStep("Tus Negocios", this::stepValidateTusNegocios);
    runStep("Términos y Condiciones", this::stepValidateTerminos);
    runStep("Política de Privacidad", this::stepValidatePrivacidad);

    final String summary = renderReport();
    System.out.println(summary);

    Assert.assertTrue("One or more workflow validations failed.\n" + summary, allStepsPassed());
  }

  private String stepLoginWithGoogle() {
    Locator loginButton =
        waitForVisible(
            "Google login button",
            appPage.locator("button:has-text(\"Google\")"),
            appPage.locator("[role='button']:has-text(\"Google\")"),
            appPage.locator("a:has-text(\"Google\")"),
            appPage.getByText(Pattern.compile("Google", Pattern.CASE_INSENSITIVE)));

    int pageCountBefore = context.pages().size();
    clickAndWait(appPage, loginButton, "Google login");

    Page authPage = waitForNewPageIfAny(pageCountBefore, POPUP_WAIT_MS);
    if (authPage == null) {
      authPage = appPage;
    }

    selectGoogleAccountIfVisible(authPage);

    waitForVisible(
        "main application interface",
        appPage.locator("main"),
        appPage.locator("aside"),
        appPage.getByText(Pattern.compile("Mi\\s+Negocio", Pattern.CASE_INSENSITIVE)));

    waitForVisible(
        "left sidebar navigation",
        appPage.locator("aside"),
        appPage.locator("nav"),
        appPage.getByText(Pattern.compile("Negocio", Pattern.CASE_INSENSITIVE)));

    captureCheckpoint(appPage, "01-dashboard-loaded", false);
    return "Dashboard loaded and left sidebar is visible.";
  }

  private String stepOpenMiNegocioMenu() {
    waitForVisible(
        "Negocio section",
        appPage.getByText(Pattern.compile("Negocio", Pattern.CASE_INSENSITIVE)));

    Locator miNegocio =
        waitForVisible(
            "Mi Negocio option",
            appPage.getByText(Pattern.compile("Mi\\s+Negocio", Pattern.CASE_INSENSITIVE)));
    clickAndWait(appPage, miNegocio, "Mi Negocio");

    waitForVisible(
        "Agregar Negocio item",
        appPage.getByText(Pattern.compile("Agregar\\s+Negocio", Pattern.CASE_INSENSITIVE)));
    waitForVisible(
        "Administrar Negocios item",
        appPage.getByText(Pattern.compile("Administrar\\s+Negocios", Pattern.CASE_INSENSITIVE)));

    captureCheckpoint(appPage, "02-mi-negocio-menu-expanded", false);
    return "Mi Negocio expanded with Agregar/Administrar options visible.";
  }

  private String stepValidateAgregarNegocioModal() {
    Locator agregarNegocio =
        waitForVisible(
            "Agregar Negocio menu item",
            appPage.getByText(Pattern.compile("Agregar\\s+Negocio", Pattern.CASE_INSENSITIVE)));
    clickAndWait(appPage, agregarNegocio, "Agregar Negocio");

    waitForVisible(
        "Crear Nuevo Negocio modal title",
        appPage.getByText(Pattern.compile("Crear\\s+Nuevo\\s+Negocio", Pattern.CASE_INSENSITIVE)));

    Locator nombreInput =
        waitForVisible(
            "Nombre del Negocio input",
            appPage.getByLabel(Pattern.compile("Nombre\\s+del\\s+Negocio", Pattern.CASE_INSENSITIVE)),
            appPage.locator("input[placeholder*='Nombre']"));

    waitForVisible(
        "Tienes 2 de 3 negocios text",
        appPage.getByText(Pattern.compile("Tienes\\s*2\\s*de\\s*3\\s*negocios", Pattern.CASE_INSENSITIVE)));

    Locator cancelarButton =
        waitForVisible(
            "Cancelar button",
            appPage.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Pattern.compile("Cancelar", Pattern.CASE_INSENSITIVE))),
            appPage.getByText(Pattern.compile("Cancelar", Pattern.CASE_INSENSITIVE)));

    waitForVisible(
        "Crear Negocio button",
        appPage.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName(Pattern.compile("Crear\\s+Negocio", Pattern.CASE_INSENSITIVE))),
        appPage.getByText(Pattern.compile("Crear\\s+Negocio", Pattern.CASE_INSENSITIVE)));

    captureCheckpoint(appPage, "03-crear-negocio-modal", false);

    nombreInput.fill("Negocio Prueba Automatizacion");
    clickAndWait(appPage, cancelarButton, "Cancelar modal");

    return "Crear Nuevo Negocio modal validated and closed with Cancelar.";
  }

  private String stepOpenAdministrarNegocios() {
    if (!isVisible(appPage.getByText(Pattern.compile("Administrar\\s+Negocios", Pattern.CASE_INSENSITIVE)))) {
      Locator miNegocio =
          waitForVisible(
              "Mi Negocio option",
              appPage.getByText(Pattern.compile("Mi\\s+Negocio", Pattern.CASE_INSENSITIVE)));
      clickAndWait(appPage, miNegocio, "Mi Negocio re-expand");
    }

    Locator administrarNegocios =
        waitForVisible(
            "Administrar Negocios option",
            appPage.getByText(Pattern.compile("Administrar\\s+Negocios", Pattern.CASE_INSENSITIVE)));
    clickAndWait(appPage, administrarNegocios, "Administrar Negocios");

    waitForVisible(
        "Información General section",
        appPage.getByText(Pattern.compile("Informaci[oó]n\\s+General", Pattern.CASE_INSENSITIVE)));
    waitForVisible(
        "Detalles de la Cuenta section",
        appPage.getByText(Pattern.compile("Detalles\\s+de\\s+la\\s+Cuenta", Pattern.CASE_INSENSITIVE)));
    waitForVisible(
        "Tus Negocios section",
        appPage.getByText(Pattern.compile("Tus\\s+Negocios", Pattern.CASE_INSENSITIVE)));
    waitForVisible(
        "Sección Legal section",
        appPage.getByText(Pattern.compile("Secci[oó]n\\s+Legal", Pattern.CASE_INSENSITIVE)));

    captureCheckpoint(appPage, "04-administrar-negocios-page", true);
    return "Administrar Negocios account page loaded.";
  }

  private String stepValidateInformacionGeneral() {
    waitForVisible(
        "user email",
        appPage.getByText(
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", Pattern.CASE_INSENSITIVE)));

    waitForVisible(
        "user name",
        appPage.locator("[class*='name'], [data-testid*='name']"),
        appPage.getByText(Pattern.compile("Nombre|Usuario|Perfil", Pattern.CASE_INSENSITIVE)));

    waitForVisible(
        "BUSINESS PLAN text",
        appPage.getByText(Pattern.compile("BUSINESS\\s+PLAN", Pattern.CASE_INSENSITIVE)));

    waitForVisible(
        "Cambiar Plan button",
        appPage.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName(Pattern.compile("Cambiar\\s+Plan", Pattern.CASE_INSENSITIVE))),
        appPage.getByText(Pattern.compile("Cambiar\\s+Plan", Pattern.CASE_INSENSITIVE)));

    return "User name/email, BUSINESS PLAN, and Cambiar Plan are visible.";
  }

  private String stepValidateDetallesCuenta() {
    waitForVisible(
        "Cuenta creada text",
        appPage.getByText(Pattern.compile("Cuenta\\s+creada", Pattern.CASE_INSENSITIVE)));
    waitForVisible(
        "Estado activo text",
        appPage.getByText(Pattern.compile("Estado\\s+activo", Pattern.CASE_INSENSITIVE)));
    waitForVisible(
        "Idioma seleccionado text",
        appPage.getByText(Pattern.compile("Idioma\\s+seleccionado", Pattern.CASE_INSENSITIVE)));

    return "Cuenta creada, Estado activo, and Idioma seleccionado are visible.";
  }

  private String stepValidateTusNegocios() {
    waitForVisible(
        "Tus Negocios section",
        appPage.getByText(Pattern.compile("Tus\\s+Negocios", Pattern.CASE_INSENSITIVE)));

    waitForVisible(
        "Agregar Negocio button in account page",
        appPage.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName(Pattern.compile("Agregar\\s+Negocio", Pattern.CASE_INSENSITIVE))),
        appPage.getByText(Pattern.compile("Agregar\\s+Negocio", Pattern.CASE_INSENSITIVE)));

    waitForVisible(
        "Tienes 2 de 3 negocios text",
        appPage.getByText(Pattern.compile("Tienes\\s*2\\s*de\\s*3\\s*negocios", Pattern.CASE_INSENSITIVE)));

    waitForVisible(
        "business list content",
        appPage.locator("table tbody tr"),
        appPage.locator("ul li"),
        appPage.locator("[role='listitem']"));

    return "Business list, Agregar Negocio button, and quota text are visible.";
  }

  private String stepValidateTerminos() {
    return validateLegalLink(
        "Términos y Condiciones",
        Pattern.compile("T[eé]rminos\\s+y\\s+Condiciones", Pattern.CASE_INSENSITIVE),
        "08-terminos-y-condiciones");
  }

  private String stepValidatePrivacidad() {
    return validateLegalLink(
        "Política de Privacidad",
        Pattern.compile("Pol[ií]tica\\s+de\\s+Privacidad", Pattern.CASE_INSENSITIVE),
        "09-politica-de-privacidad");
  }

  private String validateLegalLink(String linkText, Pattern headingPattern, String screenshotPrefix) {
    String appUrlBefore = appPage.url();

    Locator link =
        waitForVisible(
            linkText + " link",
            appPage.getByRole(com.microsoft.playwright.options.AriaRole.LINK,
                new Page.GetByRoleOptions().setName(Pattern.compile(Pattern.quote(linkText), Pattern.CASE_INSENSITIVE))),
            appPage.getByText(Pattern.compile(Pattern.quote(linkText), Pattern.CASE_INSENSITIVE)));

    int pageCountBefore = context.pages().size();
    clickAndWait(appPage, link, linkText);

    Page legalPage = waitForNewPageIfAny(pageCountBefore, POPUP_WAIT_MS);
    boolean openedNewTab = legalPage != null;
    if (legalPage == null) {
      legalPage = appPage;
    }

    waitForUiLoad(legalPage);

    waitForVisible(
        linkText + " heading",
        legalPage.getByText(headingPattern));

    // Legal pages usually include readable body text in paragraphs or list items.
    waitForVisible(
        linkText + " legal content",
        legalPage.locator("p"),
        legalPage.locator("li"));

    captureCheckpoint(legalPage, screenshotPrefix, true);
    String finalUrl = legalPage.url();

    if (openedNewTab) {
      legalPage.close();
      appPage.bringToFront();
      waitForUiLoad(appPage);
    } else if (!finalUrl.equals(appUrlBefore)) {
      try {
        appPage.goBack();
        waitForUiLoad(appPage);
      } catch (PlaywrightException ignored) {
        // If there is no browser history, continue on current page.
      }
    }

    return "Validated legal page at URL: " + finalUrl;
  }

  private void selectGoogleAccountIfVisible(Page candidatePage) {
    waitForUiLoad(candidatePage);

    Locator account =
        candidatePage.getByText(Pattern.compile(Pattern.quote(GOOGLE_ACCOUNT_EMAIL), Pattern.CASE_INSENSITIVE));
    if (isVisible(account)) {
      clickAndWait(candidatePage, account.first(), "Google account selection");
      waitForUiLoad(appPage);
    }
  }

  private void runStep(String reportKey, StepAction action) {
    try {
      String detail = action.run();
      report.put(reportKey, StepResult.pass(detail));
    } catch (Throwable t) {
      report.put(reportKey, StepResult.fail(t.getMessage()));
      captureFailure(reportKey);
    }
  }

  private Locator waitForVisible(String description, Locator... candidates) {
    long deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS;

    while (System.currentTimeMillis() < deadline) {
      for (Locator candidate : candidates) {
        Locator visible = firstVisible(candidate);
        if (visible != null) {
          try {
            visible.waitFor(
                new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(1000));
            return visible;
          } catch (PlaywrightException ignored) {
            // keep polling until timeout
          }
        }
      }
      appPage.waitForTimeout(250);
    }

    throw new AssertionError("Could not find visible element for: " + description);
  }

  private Locator firstVisible(Locator locator) {
    try {
      int count = locator.count();
      for (int i = 0; i < count; i++) {
        Locator nth = locator.nth(i);
        if (nth.isVisible()) {
          return nth;
        }
      }
    } catch (PlaywrightException ignored) {
      // return null if locator became stale
    }
    return null;
  }

  private boolean isVisible(Locator locator) {
    return firstVisible(locator) != null;
  }

  private void clickAndWait(Page sourcePage, Locator locator, String actionLabel) {
    try {
      locator.scrollIntoViewIfNeeded();
    } catch (PlaywrightException ignored) {
      // scrolling is best-effort
    }

    try {
      locator.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
    } catch (PlaywrightException ex) {
      throw new AssertionError("Could not click " + actionLabel + ": " + ex.getMessage(), ex);
    }

    waitForUiLoad(sourcePage);
  }

  private Page waitForNewPageIfAny(int pageCountBefore, long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      List<Page> pages = context.pages();
      if (pages.size() > pageCountBefore) {
        Page newest = pages.get(pages.size() - 1);
        waitForUiLoad(newest);
        return newest;
      }
      appPage.waitForTimeout(200);
    }
    return null;
  }

  private void waitForUiLoad(Page page) {
    try {
      page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    } catch (PlaywrightException ignored) {
      // no-op
    }
    try {
      page.waitForLoadState(
          LoadState.NETWORKIDLE,
          new Page.WaitForLoadStateOptions().setTimeout(5000));
    } catch (PlaywrightException ignored) {
      // some apps keep sockets open, so NETWORKIDLE can time out
    }
    page.waitForTimeout(SHORT_WAIT_MS);
  }

  private void captureCheckpoint(Page page, String filePrefix, boolean fullPage) {
    try {
      Path screenshotPath = evidenceDir.resolve(filePrefix + ".png");
      page.screenshot(
          new Page.ScreenshotOptions()
              .setPath(screenshotPath)
              .setFullPage(fullPage));
    } catch (PlaywrightException ignored) {
      // screenshot failures should not block workflow assertions
    }
  }

  private void captureFailure(String reportKey) {
    if (evidenceDir == null || appPage == null) {
      return;
    }
    String safeName = reportKey.toLowerCase().replaceAll("[^a-z0-9]+", "-");
    captureCheckpoint(appPage, "failure-" + safeName, true);
  }

  private void initializeReport() {
    report.put("Login", StepResult.pending());
    report.put("Mi Negocio menu", StepResult.pending());
    report.put("Agregar Negocio modal", StepResult.pending());
    report.put("Administrar Negocios view", StepResult.pending());
    report.put("Información General", StepResult.pending());
    report.put("Detalles de la Cuenta", StepResult.pending());
    report.put("Tus Negocios", StepResult.pending());
    report.put("Términos y Condiciones", StepResult.pending());
    report.put("Política de Privacidad", StepResult.pending());
  }

  private boolean allStepsPassed() {
    for (StepResult value : report.values()) {
      if (!value.passed) {
        return false;
      }
    }
    return true;
  }

  private String renderReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("SaleADS Mi Negocio Final Report\n");
    sb.append("Run ID: ").append(runId).append('\n');
    sb.append("Evidence directory: ").append(evidenceDir).append('\n');
    sb.append('\n');

    for (Map.Entry<String, StepResult> entry : report.entrySet()) {
      sb.append("- ")
          .append(entry.getKey())
          .append(": ")
          .append(entry.getValue().passed ? "PASS" : "FAIL")
          .append(" | ")
          .append(entry.getValue().detail)
          .append('\n');
    }
    return sb.toString();
  }

  private void writeFinalReport() throws IOException {
    String content = renderReport();
    Path reportFile = evidenceDir.resolve("final-report.txt");
    Files.writeString(reportFile, content, StandardCharsets.UTF_8);
  }

  private static String nonBlank(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  @FunctionalInterface
  private interface StepAction {
    String run() throws Exception;
  }

  private static final class StepResult {
    private final boolean passed;
    private final String detail;

    private StepResult(boolean passed, String detail) {
      this.passed = passed;
      this.detail = detail;
    }

    private static StepResult pass(String detail) {
      return new StepResult(true, detail == null ? "Completed." : detail);
    }

    private static StepResult fail(String detail) {
      return new StepResult(false, detail == null ? "Validation failed." : detail);
    }

    private static StepResult pending() {
      return new StepResult(false, "Not executed.");
    }
  }
}
