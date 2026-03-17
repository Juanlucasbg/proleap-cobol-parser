package io.proleap.e2e;

import static org.junit.Assert.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.ScreenshotType;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.Assume;
import org.junit.Test;

public class SaleadsMiNegocioFullTest {

  private static final String TEST_NAME = "saleads_mi_negocio_full_test";
  private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
  private static final DateTimeFormatter RUN_ID_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

  private static final String LOGIN = "Login";
  private static final String MI_NEGOCIO_MENU = "Mi Negocio menu";
  private static final String AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
  private static final String ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
  private static final String INFORMACION_GENERAL = "Información General";
  private static final String DETALLES_CUENTA = "Detalles de la Cuenta";
  private static final String TUS_NEGOCIOS = "Tus Negocios";
  private static final String TERMINOS = "Términos y Condiciones";
  private static final String PRIVACIDAD = "Política de Privacidad";

  @Test
  public void runMiNegocioWorkflow() throws IOException {
    final String baseUrl = env("SALEADS_BASE_URL");
    Assume.assumeTrue(
        "Set SALEADS_BASE_URL to the current SaleADS login page URL to run this E2E test.",
        isNotBlank(baseUrl));

    final Path evidenceDir = Paths.get("target", "e2e-evidence", TEST_NAME, RUN_ID_FORMAT.format(LocalDateTime.now()));
    Files.createDirectories(evidenceDir);

    final StepReport report = new StepReport();
    report.init(LOGIN);
    report.init(MI_NEGOCIO_MENU);
    report.init(AGREGAR_NEGOCIO_MODAL);
    report.init(ADMINISTRAR_NEGOCIOS_VIEW);
    report.init(INFORMACION_GENERAL);
    report.init(DETALLES_CUENTA);
    report.init(TUS_NEGOCIOS);
    report.init(TERMINOS);
    report.init(PRIVACIDAD);

    try (Playwright playwright = Playwright.create()) {
      final Browser browser = playwright.chromium().launch(
          new BrowserType.LaunchOptions().setHeadless(boolEnv("SALEADS_HEADLESS", true)));
      final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setIgnoreHTTPSErrors(true));
      final Page page = context.newPage();

      page.navigate(baseUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
      waitForUiLoad(page);

      final boolean loginOk = runStep(report, LOGIN, () -> {
        clickGoogleLogin(page);
        maybeChooseGoogleAccount(page, GOOGLE_ACCOUNT_EMAIL);
        assertVisibleAny(page,
            Pattern.compile("(?i)negocio"),
            Pattern.compile("(?i)sidebar"),
            Pattern.compile("(?i)dashboard"));
        screenshot(page, evidenceDir.resolve("01_dashboard_loaded.png"), false);
      });

      final boolean menuOk = runStep(report, MI_NEGOCIO_MENU, () -> {
        clickText(page, "Negocio");
        clickText(page, "Mi Negocio");
        assertVisibleText(page, "Agregar Negocio");
        assertVisibleText(page, "Administrar Negocios");
        screenshot(page, evidenceDir.resolve("02_mi_negocio_menu_expanded.png"), false);
      }, loginOk);

      final boolean agregarModalOk = runStep(report, AGREGAR_NEGOCIO_MODAL, () -> {
        clickText(page, "Agregar Negocio");
        assertVisibleText(page, "Crear Nuevo Negocio");
        final Locator businessNameInput = firstMatching(page,
            page.getByLabel("Nombre del Negocio"),
            page.getByPlaceholder("Nombre del Negocio"));
        assertVisible(businessNameInput, "Input 'Nombre del Negocio' was not visible.");
        assertVisibleText(page, "Tienes 2 de 3 negocios");
        assertVisibleText(page, "Cancelar");
        assertVisibleText(page, "Crear Negocio");
        screenshot(page, evidenceDir.resolve("03_agregar_negocio_modal.png"), false);

        businessNameInput.fill("Negocio Prueba Automatización");
        clickText(page, "Cancelar");
      }, menuOk);

      final boolean administrarOk = runStep(report, ADMINISTRAR_NEGOCIOS_VIEW, () -> {
        if (!isVisible(page.getByText("Administrar Negocios"))) {
          clickText(page, "Mi Negocio");
        }
        clickText(page, "Administrar Negocios");
        assertVisibleText(page, "Información General");
        assertVisibleText(page, "Detalles de la Cuenta");
        assertVisibleText(page, "Tus Negocios");
        assertVisibleText(page, "Sección Legal");
        screenshot(page, evidenceDir.resolve("04_administrar_negocios_full.png"), true);
      }, agregarModalOk);

      final boolean infoOk = runStep(report, INFORMACION_GENERAL, () -> {
        assertVisibleAny(page, Pattern.compile("(?i)@"), Pattern.compile("(?i)usuario"), Pattern.compile("(?i)nombre"));
        assertVisibleText(page, "BUSINESS PLAN");
        assertVisibleText(page, "Cambiar Plan");
      }, administrarOk);

      final boolean cuentaOk = runStep(report, DETALLES_CUENTA, () -> {
        assertVisibleText(page, "Cuenta creada");
        assertVisibleText(page, "Estado activo");
        assertVisibleText(page, "Idioma seleccionado");
      }, infoOk);

      final boolean negociosOk = runStep(report, TUS_NEGOCIOS, () -> {
        assertVisibleText(page, "Tus Negocios");
        assertVisibleText(page, "Agregar Negocio");
        assertVisibleText(page, "Tienes 2 de 3 negocios");
      }, cuentaOk);

      final boolean terminosOk = runStep(report, TERMINOS, () -> {
        final String accountPageUrl = page.url();
        final LegalPageResult result = openLegalPage(context, page, "Términos y Condiciones");
        assertVisibleText(result.page, "Términos y Condiciones");
        assertLegalContentVisible(result.page);
        screenshot(result.page, evidenceDir.resolve("05_terminos_y_condiciones.png"), true);
        report.addDetail(TERMINOS, "URL: " + result.page.url());
        returnToApplication(page, result.page, accountPageUrl);
      }, negociosOk);

      runStep(report, PRIVACIDAD, () -> {
        final String accountPageUrl = page.url();
        final LegalPageResult result = openLegalPage(context, page, "Política de Privacidad");
        assertVisibleText(result.page, "Política de Privacidad");
        assertLegalContentVisible(result.page);
        screenshot(result.page, evidenceDir.resolve("06_politica_de_privacidad.png"), true);
        report.addDetail(PRIVACIDAD, "URL: " + result.page.url());
        returnToApplication(page, result.page, accountPageUrl);
      }, terminosOk);
    }

    System.out.println(report.render(TEST_NAME, evidenceDir.toString()));
    assertTrue("At least one step failed:\n" + report.render(TEST_NAME, evidenceDir.toString()), report.allPassed());
  }

  private static void clickGoogleLogin(final Page page) {
    final Locator byRole = page.getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*google.*")));
    final Locator byText = page.getByText(Pattern.compile("(?i).*google.*"));
    click(firstMatching(page, byRole, byText), page, "Google login button");
  }

  private static void maybeChooseGoogleAccount(final Page page, final String email) {
    final Locator account = page.getByText(email, new Page.GetByTextOptions().setExact(true));
    if (isVisible(account)) {
      click(account.first(), page, "Google account selector");
    }
  }

  private static void clickText(final Page page, final String text) {
    final Locator byRoleButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(text));
    final Locator byRoleLink = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(text));
    final Locator byText = page.getByText(text, new Page.GetByTextOptions().setExact(true));
    click(firstMatching(page, byRoleButton, byRoleLink, byText), page, "text click: " + text);
  }

  private static void click(final Locator locator, final Page page, final String description) {
    assertVisible(locator, "Could not find a visible element for " + description + ".");
    locator.first().click();
    waitForUiLoad(page);
  }

  private static Locator firstMatching(final Page page, final Locator... candidates) {
    for (final Locator candidate : candidates) {
      if (candidate.count() > 0 && isVisible(candidate.first())) {
        return candidate.first();
      }
    }
    for (final Locator candidate : candidates) {
      if (candidate.count() > 0) {
        return candidate.first();
      }
    }
    throw new AssertionError("No matching locator found.");
  }

  private static void assertVisibleText(final Page page, final String text) {
    final Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(true));
    assertVisible(locator, "Expected visible text: '" + text + "'.");
  }

  private static void assertLegalContentVisible(final Page page) {
    if (isVisible(page.locator("main")) || isVisible(page.locator("article")) || isVisible(page.locator("p").first())) {
      return;
    }
    throw new AssertionError("No legal content container was visible.");
  }

  private static void assertVisibleAny(final Page page, final Pattern... patterns) {
    for (final Pattern pattern : patterns) {
      if (isVisible(page.getByText(pattern))) {
        return;
      }
    }
    if (isVisible(page.locator("aside"))) {
      return;
    }
    throw new AssertionError("None of the expected dashboard/sidebar indicators was visible.");
  }

  private static void assertVisible(final Locator locator, final String message) {
    if (!isVisible(locator)) {
      throw new AssertionError(message);
    }
  }

  private static boolean isVisible(final Locator locator) {
    try {
      locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
      return true;
    } catch (PlaywrightException e) {
      return false;
    }
  }

  private static void waitForUiLoad(final Page page) {
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10000));
    } catch (PlaywrightException ignored) {
      page.waitForTimeout(700);
    }
  }

  private static void screenshot(final Page page, final Path path, final boolean fullPage) {
    page.screenshot(new Page.ScreenshotOptions()
        .setPath(path)
        .setType(ScreenshotType.PNG)
        .setFullPage(fullPage));
  }

  private static LegalPageResult openLegalPage(final BrowserContext context, final Page appPage, final String linkText) {
    final Locator legalLink = firstMatching(appPage,
        appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkText)),
        appPage.getByText(linkText, new Page.GetByTextOptions().setExact(true)));

    try {
      final Page newTab = context.waitForPage(() -> legalLink.first().click(),
          new BrowserContext.WaitForPageOptions().setTimeout(5000));
      waitForUiLoad(newTab);
      return new LegalPageResult(newTab, true);
    } catch (PlaywrightException noNewTab) {
      waitForUiLoad(appPage);
      return new LegalPageResult(appPage, false);
    }
  }

  private static void returnToApplication(final Page appPage, final Page legalPage, final String accountPageUrl) {
    if (legalPage != appPage) {
      legalPage.close();
      appPage.bringToFront();
      waitForUiLoad(appPage);
      return;
    }

    appPage.goBack();
    waitForUiLoad(appPage);
    if (!appPage.url().equals(accountPageUrl)) {
      appPage.navigate(accountPageUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
      waitForUiLoad(appPage);
    }
  }

  private static boolean runStep(final StepReport report, final String stepName, final StepAction action) {
    return runStep(report, stepName, action, true);
  }

  private static boolean runStep(
      final StepReport report,
      final String stepName,
      final StepAction action,
      final boolean canRun) {
    if (!canRun) {
      report.fail(stepName, "Skipped because a previous required step failed.");
      return false;
    }
    try {
      action.run();
      report.pass(stepName);
      return true;
    } catch (Exception | AssertionError e) {
      report.fail(stepName, e.getMessage());
      return false;
    }
  }

  private static boolean boolEnv(final String name, final boolean defaultValue) {
    final String value = env(name);
    if (!isNotBlank(value)) {
      return defaultValue;
    }
    return "true".equalsIgnoreCase(value) || "1".equals(value);
  }

  private static String env(final String name) {
    return System.getenv(name);
  }

  private static boolean isNotBlank(final String value) {
    return value != null && !value.trim().isEmpty();
  }

  private interface StepAction {
    void run() throws Exception;
  }

  private static class LegalPageResult {
    final Page page;
    final boolean openedNewTab;

    private LegalPageResult(final Page page, final boolean openedNewTab) {
      this.page = page;
      this.openedNewTab = openedNewTab;
    }
  }

  private static class StepReport {
    private final LinkedHashMap<String, StepStatus> statuses = new LinkedHashMap<>();

    void init(final String step) {
      statuses.put(step, new StepStatus(false, "NOT_RUN"));
    }

    void pass(final String step) {
      statuses.put(step, new StepStatus(true, "PASS"));
    }

    void fail(final String step, final String detail) {
      final String safeDetail = isNotBlank(detail) ? detail : "FAIL";
      statuses.put(step, new StepStatus(false, safeDetail));
    }

    void addDetail(final String step, final String detail) {
      final StepStatus current = statuses.get(step);
      if (current == null) {
        statuses.put(step, new StepStatus(false, detail));
        return;
      }
      final String merged = current.detail + " | " + detail;
      statuses.put(step, new StepStatus(current.passed, merged));
    }

    boolean allPassed() {
      for (final Map.Entry<String, StepStatus> entry : statuses.entrySet()) {
        if (!entry.getValue().passed) {
          return false;
        }
      }
      return true;
    }

    String render(final String testName, final String evidencePath) {
      final StringBuilder sb = new StringBuilder();
      sb.append("=== ").append(testName).append(" report ===\n");
      for (final Map.Entry<String, StepStatus> entry : statuses.entrySet()) {
        sb.append("- ").append(entry.getKey()).append(": ")
            .append(entry.getValue().passed ? "PASS" : "FAIL")
            .append(" (").append(entry.getValue().detail).append(")\n");
      }
      sb.append("Evidence: ").append(evidencePath).append('\n');
      return sb.toString();
    }
  }

  private static class StepStatus {
    final boolean passed;
    final String detail;

    private StepStatus(final boolean passed, final String detail) {
      this.passed = passed;
      this.detail = detail;
    }
  }
}
