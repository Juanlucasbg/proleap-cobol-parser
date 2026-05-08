package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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
import com.microsoft.playwright.options.WaitUntilState;

/**
 * End-to-end validation for SaleADS "Mi Negocio" workflow.
 *
 * <p>
 * This test is intentionally URL-agnostic. Pass the login URL of the target
 * environment at runtime:
 * </p>
 *
 * <pre>
 * mvn -Dtest=SaleadsMiNegocioWorkflowTest \
 *     -Dsaleads.e2e.enabled=true \
 *     -Dsaleads.login.url=https://&lt;your-environment-login-url&gt; test
 * </pre>
 */
public class SaleadsMiNegocioWorkflowTest {

  private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
  private static final double UI_TIMEOUT_MS = 20_000;
  private static final double SHORT_TIMEOUT_MS = 4_000;

  @Test
  public void saleadsMiNegocioFullWorkflow() throws Exception {
    Assume.assumeTrue("Enable this E2E test with -Dsaleads.e2e.enabled=true", isE2eEnabled());

    final String loginUrl = resolveValue(System.getProperty("saleads.login.url"), System.getenv("SALEADS_LOGIN_URL"));
    Assume.assumeTrue(
        "Set -Dsaleads.login.url=<login page> (or SALEADS_LOGIN_URL) to keep this test environment agnostic.",
        loginUrl != null && !loginUrl.isBlank());

    final boolean headless = Boolean.parseBoolean(resolveValue(System.getProperty("saleads.headless"),
        System.getenv().getOrDefault("SALEADS_HEADLESS", "false")));

    final Map<String, String> report = initReport();
    final Path screenshotDir = createScreenshotDirectory();
    String termsUrl = "";
    String privacyUrl = "";

    try (Playwright playwright = Playwright.create();
        Browser browser = playwright.chromium()
            .launch(new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo(120));
        BrowserContext context = browser.newContext(
            new Browser.NewContextOptions().setViewportSize(1600, 1000).setAcceptDownloads(true))) {

      final Page appPage = context.newPage();
      appPage.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
      waitForUi(appPage);

      // 1) Login with Google
      loginWithGoogle(context, appPage);
      ensureMainInterfaceVisible(appPage);
      screenshot(appPage, screenshotDir, "step01-dashboard-loaded", true);
      report.put("Login", "PASS");

      // 2) Open Mi Negocio menu
      openMiNegocioMenu(appPage);
      ensureTextVisible(appPage, "(?i)agregar\\s+negocio", "Agregar Negocio submenu");
      ensureTextVisible(appPage, "(?i)administrar\\s+negocios", "Administrar Negocios submenu");
      screenshot(appPage, screenshotDir, "step02-mi-negocio-expanded", false);
      report.put("Mi Negocio menu", "PASS");

      // 3) Validate Agregar Negocio modal
      clickByVisibleText(appPage, "(?i)agregar\\s+negocio");
      ensureTextVisible(appPage, "(?i)crear\\s+nuevo\\s+negocio", "Modal title");
      ensureAnyVisible(Arrays.asList(
          appPage.getByLabel(Pattern.compile("(?i)nombre\\s+del\\s+negocio")),
          appPage.getByPlaceholder(Pattern.compile("(?i)nombre\\s+del\\s+negocio")),
          appPage.locator("input[name*='negocio'], input[id*='negocio']")),
          "Nombre del Negocio input");
      ensureTextVisible(appPage, "(?i)tienes\\s+2\\s+de\\s+3\\s+negocios", "Business quota text");
      ensureTextVisible(appPage, "(?i)cancelar", "Cancelar button");
      ensureTextVisible(appPage, "(?i)crear\\s+negocio", "Crear Negocio button");

      typeInFirstVisible(appPage,
          Arrays.asList(
              appPage.getByLabel(Pattern.compile("(?i)nombre\\s+del\\s+negocio")),
              appPage.getByPlaceholder(Pattern.compile("(?i)nombre\\s+del\\s+negocio")),
              appPage.locator("input[name*='negocio'], input[id*='negocio']")),
          "Negocio Prueba Automatizacion");
      screenshot(appPage, screenshotDir, "step03-agregar-negocio-modal", false);
      clickByVisibleText(appPage, "(?i)cancelar");
      waitForUi(appPage);
      report.put("Agregar Negocio modal", "PASS");

      // 4) Open Administrar Negocios
      ensureMiNegocioExpanded(appPage);
      clickByVisibleText(appPage, "(?i)administrar\\s+negocios");
      waitForUi(appPage);
      ensureTextVisible(appPage, "(?i)informaci[oó]n\\s+general", "Información General section");
      ensureTextVisible(appPage, "(?i)detalles\\s+de\\s+la\\s+cuenta", "Detalles de la Cuenta section");
      ensureTextVisible(appPage, "(?i)tus\\s+negocios", "Tus Negocios section");
      ensureTextVisible(appPage, "(?i)secci[oó]n\\s+legal", "Sección Legal section");
      screenshot(appPage, screenshotDir, "step04-administrar-negocios", true);
      report.put("Administrar Negocios view", "PASS");

      // 5) Validate Información General
      ensureAnyVisible(Arrays.asList(
          appPage.getByText(Pattern.compile("(?i)nombre")),
          appPage.getByText(Pattern.compile("(?i)name")),
          appPage.locator("h1, h2, h3").first()),
          "User name indicator");
      ensureTextVisible(appPage, "(?i)[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "User email");
      ensureTextVisible(appPage, "(?i)business\\s*plan", "BUSINESS PLAN");
      ensureTextVisible(appPage, "(?i)cambiar\\s+plan", "Cambiar Plan button");
      report.put("Información General", "PASS");

      // 6) Validate Detalles de la Cuenta
      ensureTextVisible(appPage, "(?i)cuenta\\s+creada", "Cuenta creada");
      ensureTextVisible(appPage, "(?i)estado\\s+activo", "Estado activo");
      ensureTextVisible(appPage, "(?i)idioma\\s+seleccionado", "Idioma seleccionado");
      report.put("Detalles de la Cuenta", "PASS");

      // 7) Validate Tus Negocios
      ensureTextVisible(appPage, "(?i)tus\\s+negocios", "Tus Negocios section title");
      ensureTextVisible(appPage, "(?i)agregar\\s+negocio", "Agregar Negocio button");
      ensureTextVisible(appPage, "(?i)tienes\\s+2\\s+de\\s+3\\s+negocios", "Business quota text");
      ensureAnyVisible(Arrays.asList(
          appPage.locator("[data-testid*='business']"),
          appPage.locator("table, ul, [role='list']")),
          "Business list");
      report.put("Tus Negocios", "PASS");

      // 8) Validate Términos y Condiciones
      Page termsPage = clickLegalLink(context, appPage, "(?i)t[eé]rminos\\s+y\\s+condiciones");
      ensureTextVisible(termsPage, "(?i)t[eé]rminos\\s+y\\s+condiciones", "Terms heading");
      ensureLegalContentVisible(termsPage);
      termsUrl = termsPage.url();
      screenshot(termsPage, screenshotDir, "step08-terminos-y-condiciones", true);
      returnToApplicationTab(appPage, termsPage);
      report.put("Términos y Condiciones", "PASS");

      // 9) Validate Política de Privacidad
      Page privacyPage = clickLegalLink(context, appPage, "(?i)pol[ií]tica\\s+de\\s+privacidad");
      ensureTextVisible(privacyPage, "(?i)pol[ií]tica\\s+de\\s+privacidad", "Privacy heading");
      ensureLegalContentVisible(privacyPage);
      privacyUrl = privacyPage.url();
      screenshot(privacyPage, screenshotDir, "step09-politica-de-privacidad", true);
      returnToApplicationTab(appPage, privacyPage);
      report.put("Política de Privacidad", "PASS");

    } catch (Throwable error) {
      markFirstPendingAsFailed(report, error.getMessage());
      throw error;
    } finally {
      System.out.println("=== SaleADS Mi Negocio Final Report ===");
      report.forEach((step, status) -> System.out.println(step + ": " + status));
      System.out.println("Términos y Condiciones URL: " + (termsUrl.isBlank() ? "N/A" : termsUrl));
      System.out.println("Política de Privacidad URL: " + (privacyUrl.isBlank() ? "N/A" : privacyUrl));
      System.out.println("Screenshots: " + screenshotDir.toAbsolutePath());
      assertTrue("One or more workflow validations failed. Review report in logs.",
          report.values().stream().allMatch(value -> value.startsWith("PASS")));
    }
  }

  private boolean isE2eEnabled() {
    final String value = resolveValue(System.getProperty("saleads.e2e.enabled"), System.getenv("SALEADS_E2E_ENABLED"));
    return value != null && Boolean.parseBoolean(value);
  }

  private String resolveValue(final String first, final String second) {
    if (first != null && !first.isBlank()) {
      return first;
    }
    if (second != null && !second.isBlank()) {
      return second;
    }
    return null;
  }

  private Map<String, String> initReport() {
    final Map<String, String> report = new LinkedHashMap<>();
    report.put("Login", "FAIL - Not executed");
    report.put("Mi Negocio menu", "FAIL - Not executed");
    report.put("Agregar Negocio modal", "FAIL - Not executed");
    report.put("Administrar Negocios view", "FAIL - Not executed");
    report.put("Información General", "FAIL - Not executed");
    report.put("Detalles de la Cuenta", "FAIL - Not executed");
    report.put("Tus Negocios", "FAIL - Not executed");
    report.put("Términos y Condiciones", "FAIL - Not executed");
    report.put("Política de Privacidad", "FAIL - Not executed");
    return report;
  }

  private void markFirstPendingAsFailed(final Map<String, String> report, final String details) {
    for (Map.Entry<String, String> entry : report.entrySet()) {
      if (!entry.getValue().startsWith("PASS")) {
        final String message = details == null || details.isBlank() ? "Unexpected error" : details;
        entry.setValue("FAIL - " + message);
        return;
      }
    }
  }

  private Path createScreenshotDirectory() throws Exception {
    final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    final Path directory = Paths.get("target", "saleads-mi-negocio-screenshots", timestamp);
    Files.createDirectories(directory);
    return directory;
  }

  private void waitForUi(final Page page) {
    try {
      page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(UI_TIMEOUT_MS));
    } catch (PlaywrightException ignored) {
      // Some SPA transitions do not trigger full load states.
    }
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(SHORT_TIMEOUT_MS));
    } catch (PlaywrightException ignored) {
      // Long-polling / websocket traffic can keep network busy.
    }
  }

  private void screenshot(final Page page, final Path directory, final String fileName, final boolean fullPage) {
    page.screenshot(new Page.ScreenshotOptions().setPath(directory.resolve(fileName + ".png")).setFullPage(fullPage));
  }

  private void loginWithGoogle(final BrowserContext context, final Page appPage) {
    final Locator loginButton = firstVisibleLocator(Arrays.asList(
        appPage.getByRole(AriaRole.BUTTON,
            new Page.GetByRoleOptions()
                .setName(Pattern.compile("(?i)sign\\s*in\\s*with\\s*google|iniciar\\s+sesi[oó]n\\s+con\\s+google"))),
        appPage.getByRole(AriaRole.LINK,
            new Page.GetByRoleOptions()
                .setName(Pattern.compile("(?i)sign\\s*in\\s*with\\s*google|iniciar\\s+sesi[oó]n\\s+con\\s+google"))),
        appPage.getByText(Pattern.compile("(?i)sign\\s*in\\s*with\\s*google|iniciar\\s+sesi[oó]n\\s+con\\s+google"))),
        "Google login button");

    final Page authOrAppPage = clickAndCapturePage(context, appPage, loginButton);
    waitForUi(authOrAppPage);

    final Locator accountSelector = firstVisibleLocator(Arrays.asList(
        authOrAppPage.getByText(Pattern.compile(Pattern.quote(ACCOUNT_EMAIL), Pattern.CASE_INSENSITIVE)),
        authOrAppPage.getByRole(AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName(Pattern.compile(Pattern.quote(ACCOUNT_EMAIL), Pattern.CASE_INSENSITIVE))),
        authOrAppPage.getByRole(AriaRole.LINK,
            new Page.GetByRoleOptions().setName(Pattern.compile(Pattern.quote(ACCOUNT_EMAIL), Pattern.CASE_INSENSITIVE)))),
        null);
    if (accountSelector != null) {
      clickAndWait(authOrAppPage, accountSelector);
    }

    appPage.bringToFront();
    waitForUi(appPage);
  }

  private void ensureMainInterfaceVisible(final Page appPage) {
    ensureAnyVisible(Arrays.asList(
        appPage.locator("aside"),
        appPage.getByRole(AriaRole.NAVIGATION),
        appPage.getByText(Pattern.compile("(?i)negocio"))),
        "Main application interface / left sidebar");
  }

  private void openMiNegocioMenu(final Page page) {
    clickByVisibleText(page, "(?i)negocio");
    waitForUi(page);
    clickByVisibleText(page, "(?i)mi\\s+negocio");
    waitForUi(page);
  }

  private void ensureMiNegocioExpanded(final Page page) {
    if (!isVisible(page.getByText(Pattern.compile("(?i)administrar\\s+negocios")), SHORT_TIMEOUT_MS)) {
      final Locator miNegocio = firstVisibleLocator(Arrays.asList(
          page.getByText(Pattern.compile("(?i)mi\\s+negocio")),
          page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)mi\\s+negocio")))),
          "Mi Negocio menu");
      clickAndWait(page, miNegocio);
    }
  }

  private Page clickLegalLink(final BrowserContext context, final Page appPage, final String regex) {
    final Locator legalLink = firstVisibleLocator(Arrays.asList(
        appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile(regex))),
        appPage.getByText(Pattern.compile(regex))),
        "Legal link: " + regex);
    return clickAndCapturePage(context, appPage, legalLink);
  }

  private void returnToApplicationTab(final Page appPage, final Page legalPage) {
    if (legalPage != appPage && !legalPage.isClosed()) {
      legalPage.close();
      appPage.bringToFront();
      waitForUi(appPage);
      return;
    }
    legalPage.goBack();
    waitForUi(legalPage);
  }

  private void ensureLegalContentVisible(final Page page) {
    final String bodyText = page.locator("body").innerText();
    assertTrue("Legal content text should be visible", bodyText != null && bodyText.trim().length() > 120);
  }

  private void clickByVisibleText(final Page page, final String regex) {
    final Locator target = firstVisibleLocator(Arrays.asList(
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile(regex))),
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile(regex))),
        page.getByText(Pattern.compile(regex))),
        "Click target: " + regex);
    clickAndWait(page, target);
  }

  private void typeInFirstVisible(final Page page, final List<Locator> candidates, final String value) {
    final Locator input = firstVisibleLocator(candidates, "Input field");
    input.fill(value);
    waitForUi(page);
  }

  private Page clickAndCapturePage(final BrowserContext context, final Page currentPage, final Locator trigger) {
    try {
      final Page newPage = context.waitForPage(() -> clickAndWait(currentPage, trigger),
          new BrowserContext.WaitForPageOptions().setTimeout(SHORT_TIMEOUT_MS));
      waitForUi(newPage);
      return newPage;
    } catch (PlaywrightException ignored) {
      // The click already happened. If no popup was opened, continue in current tab.
      waitForUi(currentPage);
      return currentPage;
    }
  }

  private void clickAndWait(final Page page, final Locator locator) {
    locator.first().click(new Locator.ClickOptions().setTimeout(UI_TIMEOUT_MS));
    waitForUi(page);
  }

  private void ensureTextVisible(final Page page, final String regex, final String label) {
    final Locator target = page.getByText(Pattern.compile(regex));
    if (!isVisible(target, UI_TIMEOUT_MS)) {
      throw new AssertionError("Expected visible text for " + label + " (pattern: " + regex + ")");
    }
  }

  private void ensureAnyVisible(final List<Locator> candidates, final String label) {
    final Locator visible = firstVisibleLocator(candidates, null);
    if (visible == null) {
      throw new AssertionError("Expected at least one visible locator for " + label);
    }
  }

  private Locator firstVisibleLocator(final List<Locator> candidates, final String label) {
    final long deadline = System.currentTimeMillis() + (long) UI_TIMEOUT_MS;
    while (System.currentTimeMillis() < deadline) {
      for (Locator candidate : candidates) {
        if (candidate != null && isVisible(candidate, 250)) {
          return candidate.first();
        }
      }
      try {
        Thread.sleep(200);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        break;
      }
    }

    if (label != null) {
      throw new AssertionError("Could not find visible locator for " + label);
    }
    return null;
  }

  private boolean isVisible(final Locator locator, final double timeoutMs) {
    try {
      locator.first().waitFor(
          new Locator.WaitForOptions().setTimeout(timeoutMs).setState(WaitForSelectorState.VISIBLE));
      return locator.first().isVisible();
    } catch (PlaywrightException ignored) {
      return false;
    }
  }
}
