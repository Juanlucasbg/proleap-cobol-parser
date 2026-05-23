package io.proleap.saleads.e2e;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.Assert;
import org.junit.Test;

public class SaleadsMiNegocioFullWorkflowTest {

  private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
  private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

  @Test
  public void saleadsMiNegocioFullTest() throws Exception {
    final Path evidenceDir = createEvidenceDirectory();
    final Map<String, Boolean> report = initializeReport();
    final List<String> failures = new ArrayList<>();

    try (Playwright playwright = Playwright.create()) {
      final Browser browser =
          playwright
              .chromium()
              .launch(new BrowserType.LaunchOptions().setHeadless(isHeadlessEnabled()));
      final BrowserContext context = browser.newContext();
      final Page appPage = context.newPage();

      final String startUrl = resolveStartUrl();
      if (startUrl == null) {
        throw new IllegalStateException(
            "Missing start URL. Set SALEADS_START_URL or -Dsaleads.startUrl to the login page "
                + "for the current environment.");
      }

      appPage.navigate(startUrl);
      waitForUiToSettle(appPage);

      runStep(
          "Login",
          report,
          failures,
          () -> {
            loginWithGoogle(appPage, context);
            assertSidebarVisible(appPage);
            screenshot(appPage, evidenceDir, "01-dashboard-loaded", false);
          });

      runStep(
          "Mi Negocio menu",
          report,
          failures,
          () -> {
            openMiNegocioMenu(appPage);
            assertVisibleText(appPage, "Agregar Negocio");
            assertVisibleText(appPage, "Administrar Negocios");
            screenshot(appPage, evidenceDir, "02-mi-negocio-menu-expanded", false);
          });

      runStep(
          "Agregar Negocio modal",
          report,
          failures,
          () -> {
            clickByText(appPage, Pattern.compile("^Agregar\\s+Negocio$", Pattern.CASE_INSENSITIVE));
            assertVisibleText(appPage, "Crear Nuevo Negocio");
            assertVisibleText(appPage, "Nombre del Negocio");
            assertVisibleText(appPage, "Tienes 2 de 3 negocios");
            assertVisibleText(appPage, "Cancelar");
            assertVisibleText(appPage, "Crear Negocio");
            screenshot(appPage, evidenceDir, "03-agregar-negocio-modal", false);
            fillByLabelIfPresent(appPage, "Nombre del Negocio", "Negocio Prueba Automatizacion");
            clickByText(appPage, Pattern.compile("^Cancelar$", Pattern.CASE_INSENSITIVE));
          });

      runStep(
          "Administrar Negocios view",
          report,
          failures,
          () -> {
            ensureMiNegocioExpanded(appPage);
            clickByText(
                appPage, Pattern.compile("^Administrar\\s+Negocios$", Pattern.CASE_INSENSITIVE));
            assertVisibleText(appPage, "Informacion General");
            assertVisibleText(appPage, "Detalles de la Cuenta");
            assertVisibleText(appPage, "Tus Negocios");
            assertVisibleText(appPage, "Seccion Legal");
            screenshot(appPage, evidenceDir, "04-administrar-negocios-view", true);
          });

      runStep(
          "Informacion General",
          report,
          failures,
          () -> {
            assertVisibleText(appPage, "BUSINESS PLAN");
            assertVisibleText(appPage, "Cambiar Plan");
            assertEmailVisible(appPage);
            assertLikelyUserNameVisible(appPage);
          });

      runStep(
          "Detalles de la Cuenta",
          report,
          failures,
          () -> {
            assertVisibleText(appPage, "Cuenta creada");
            assertVisibleText(appPage, "Estado activo");
            assertVisibleText(appPage, "Idioma seleccionado");
          });

      runStep(
          "Tus Negocios",
          report,
          failures,
          () -> {
            assertVisibleText(appPage, "Tus Negocios");
            assertVisibleText(appPage, "Agregar Negocio");
            assertVisibleText(appPage, "Tienes 2 de 3 negocios");
            final Locator businessRows =
                appPage.locator(
                    "li:visible, [role='row']:visible, tr:visible, [data-testid*='business']:visible");
            Assert.assertTrue("Business list should contain visible rows.", businessRows.count() > 0);
          });

      runStep(
          "Terminos y Condiciones",
          report,
          failures,
          () -> openLegalDocumentAndReturn(
              appPage,
              context,
              evidenceDir,
              "Terminos y Condiciones",
              Pattern.compile(
                  "^T[eé]rminos\\s+y\\s+Condiciones$", Pattern.CASE_INSENSITIVE),
              Pattern.compile(
                  "T[eé]rminos\\s+y\\s+Condiciones|Condiciones\\s+de\\s+uso",
                  Pattern.CASE_INSENSITIVE)));

      runStep(
          "Politica de Privacidad",
          report,
          failures,
          () -> openLegalDocumentAndReturn(
              appPage,
              context,
              evidenceDir,
              "Politica de Privacidad",
              Pattern.compile("^Pol[ií]tica\\s+de\\s+Privacidad$", Pattern.CASE_INSENSITIVE),
              Pattern.compile(
                  "Pol[ií]tica\\s+de\\s+Privacidad|Privacidad", Pattern.CASE_INSENSITIVE)));

      printFinalReport(report);

      if (!failures.isEmpty()) {
        Assert.fail("Workflow validation failures:\n - " + String.join("\n - ", failures));
      }
    }
  }

  private static void loginWithGoogle(final Page appPage, final BrowserContext context) {
    clickByAnyLocator(
        appPage,
        appPage
            .getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions()
                    .setName(Pattern.compile("Google|Iniciar|Sign\\s*in", Pattern.CASE_INSENSITIVE)))
            .first(),
        appPage.getByText(Pattern.compile("Google|Iniciar|Sign\\s*in", Pattern.CASE_INSENSITIVE)).first());

    handleGoogleAccountSelector(context);
    waitForUiToSettle(appPage);
  }

  private static void openMiNegocioMenu(final Page page) {
    clickIfPresent(page, Pattern.compile("^Negocio$", Pattern.CASE_INSENSITIVE));
    clickByText(page, Pattern.compile("^Mi\\s+Negocio$", Pattern.CASE_INSENSITIVE));
    waitForUiToSettle(page);
  }

  private static void ensureMiNegocioExpanded(final Page page) {
    if (page.getByText(Pattern.compile("^Administrar\\s+Negocios$", Pattern.CASE_INSENSITIVE)).count()
        == 0) {
      openMiNegocioMenu(page);
      return;
    }

    if (!page
        .getByText(Pattern.compile("^Administrar\\s+Negocios$", Pattern.CASE_INSENSITIVE))
        .first()
        .isVisible()) {
      clickByText(page, Pattern.compile("^Mi\\s+Negocio$", Pattern.CASE_INSENSITIVE));
      waitForUiToSettle(page);
    }
  }

  private static void openLegalDocumentAndReturn(
      final Page appPage,
      final BrowserContext context,
      final Path evidenceDir,
      final String label,
      final Pattern linkPattern,
      final Pattern headingPattern) {
    final int initialPages = context.pages().size();
    final String originalUrl = appPage.url();

    clickByText(appPage, linkPattern);
    waitForUiToSettle(appPage);

    Page legalPage = appPage;
    if (context.pages().size() > initialPages) {
      legalPage = context.pages().get(context.pages().size() - 1);
      legalPage.bringToFront();
      waitForUiToSettle(legalPage);
    }

    assertVisiblePattern(legalPage, headingPattern);
    Assert.assertTrue(
        "Legal content should be visible for " + label,
        legalPage.locator("main:visible, article:visible, section:visible, p:visible").count() > 0);
    screenshot(legalPage, evidenceDir, sanitize(label) + "-legal", true);
    System.out.println(label + " final URL: " + legalPage.url());

    if (legalPage != appPage) {
      legalPage.close();
      appPage.bringToFront();
      waitForUiToSettle(appPage);
    } else if (!appPage.url().equals(originalUrl)) {
      appPage.goBack();
      waitForUiToSettle(appPage);
    }
  }

  private static void handleGoogleAccountSelector(final BrowserContext context) {
    final long deadline = System.currentTimeMillis() + 20_000L;
    while (System.currentTimeMillis() < deadline) {
      for (final Page page : context.pages()) {
        final String url = page.url();
        if (url != null && url.contains("accounts.google.com")) {
          final Locator emailChoice =
              page.getByText(Pattern.compile(Pattern.quote(GOOGLE_ACCOUNT), Pattern.CASE_INSENSITIVE))
                  .first();
          if (emailChoice.count() > 0) {
            clickAndWait(page, emailChoice);
            return;
          }
        }
      }
      sleep(250);
    }
  }

  private static void assertSidebarVisible(final Page page) {
    final Locator sidebar = page.locator("aside:visible, nav:visible").first();
    sidebar.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
    Assert.assertTrue("Left sidebar navigation should be visible.", sidebar.isVisible());
  }

  private static void assertEmailVisible(final Page page) {
    final Locator emailLocator = page.getByText(EMAIL_PATTERN).first();
    emailLocator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
    Assert.assertTrue("User email should be visible.", emailLocator.isVisible());
  }

  private static void assertLikelyUserNameVisible(final Page page) {
    final Locator candidates =
        page.locator(
            "h1:visible, h2:visible, h3:visible, [data-testid*='name']:visible, [class*='name']:visible");
    Assert.assertTrue("User name should be visible.", candidates.count() > 0);
  }

  private static void assertVisibleText(final Page page, final String text) {
    final Pattern diacriticsInsensitivePattern = diacriticsInsensitiveRegex(text);
    assertVisiblePattern(page, diacriticsInsensitivePattern);
  }

  private static void assertVisiblePattern(final Page page, final Pattern pattern) {
    final Locator target = page.getByText(pattern).first();
    target.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
    Assert.assertTrue("Expected visible text pattern: " + pattern, target.isVisible());
  }

  private static void clickIfPresent(final Page page, final Pattern pattern) {
    final Locator text = page.getByText(pattern).first();
    if (text.count() > 0 && text.isVisible()) {
      clickAndWait(page, text);
    }
  }

  private static void clickByText(final Page page, final Pattern pattern) {
    final Locator text = page.getByText(pattern).first();
    Assert.assertTrue("Could not find visible text: " + pattern, text.count() > 0);
    clickAndWait(page, text);
  }

  private static void clickByAnyLocator(final Page page, final Locator... locators) {
    for (final Locator locator : locators) {
      if (locator.count() > 0 && locator.isVisible()) {
        clickAndWait(page, locator);
        return;
      }
    }
    Assert.fail("Could not find a visible locator to click.");
  }

  private static void clickAndWait(final Page page, final Locator locator) {
    locator.click();
    waitForUiToSettle(page);
  }

  private static void fillByLabelIfPresent(final Page page, final String label, final String value) {
    final Locator input = page.getByLabel(diacriticsInsensitiveRegex(label)).first();
    if (input.count() > 0 && input.isVisible()) {
      input.fill(value);
      return;
    }
    final Locator fallback = page.locator("input:visible, textarea:visible");
    if (fallback.count() > 0) {
      fallback.first().fill(value);
    }
  }

  private static void screenshot(
      final Page page, final Path evidenceDir, final String baseName, final boolean fullPage) {
    page.screenshot(
        new Page.ScreenshotOptions()
            .setPath(evidenceDir.resolve(sanitize(baseName) + ".png"))
            .setFullPage(fullPage));
  }

  private static void waitForUiToSettle(final Page page) {
    try {
      page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    } catch (final TimeoutError ignored) {
      // UI occasionally updates in-place without full load events.
    }
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10_000));
    } catch (final TimeoutError ignored) {
      // Some pages keep persistent network activity, so this should not fail the test.
    }
    sleep(500);
  }

  private static Pattern diacriticsInsensitiveRegex(final String text) {
    final StringBuilder regex = new StringBuilder();
    for (final char ch : text.toCharArray()) {
      if (Character.isWhitespace(ch)) {
        regex.append("\\s+");
        continue;
      }

      switch (Character.toLowerCase(ch)) {
        case 'a':
          regex.append("[aáAÁ]");
          break;
        case 'e':
          regex.append("[eéEÉ]");
          break;
        case 'i':
          regex.append("[iíIÍ]");
          break;
        case 'o':
          regex.append("[oóOÓ]");
          break;
        case 'u':
          regex.append("[uúUÚ]");
          break;
        case 'n':
          regex.append("[nñNÑ]");
          break;
        default:
          regex.append(Pattern.quote(String.valueOf(ch)));
          break;
      }
    }
    return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
  }

  private static String resolveStartUrl() {
    final String propertyUrl = System.getProperty("saleads.startUrl");
    if (propertyUrl != null && !propertyUrl.isBlank()) {
      return propertyUrl.trim();
    }
    final String envUrl = System.getenv("SALEADS_START_URL");
    if (envUrl != null && !envUrl.isBlank()) {
      return envUrl.trim();
    }
    return null;
  }

  private static boolean isHeadlessEnabled() {
    final String property = System.getProperty("saleads.headless");
    if (property != null && !property.isBlank()) {
      return Boolean.parseBoolean(property);
    }
    final String env = System.getenv("SALEADS_HEADLESS");
    if (env != null && !env.isBlank()) {
      return Boolean.parseBoolean(env);
    }
    return true;
  }

  private static Path createEvidenceDirectory() throws Exception {
    final Path targetDir = Paths.get("target", "saleads-evidence", LocalDateTime.now().format(TS));
    return Files.createDirectories(targetDir);
  }

  private static Map<String, Boolean> initializeReport() {
    final Map<String, Boolean> report = new LinkedHashMap<>();
    report.put("Login", false);
    report.put("Mi Negocio menu", false);
    report.put("Agregar Negocio modal", false);
    report.put("Administrar Negocios view", false);
    report.put("Informacion General", false);
    report.put("Detalles de la Cuenta", false);
    report.put("Tus Negocios", false);
    report.put("Terminos y Condiciones", false);
    report.put("Politica de Privacidad", false);
    return report;
  }

  private static void runStep(
      final String reportKey,
      final Map<String, Boolean> report,
      final List<String> failures,
      final CheckedRunnable runnable) {
    try {
      runnable.run();
      report.put(reportKey, true);
    } catch (final Throwable t) {
      report.put(reportKey, false);
      failures.add(reportKey + " -> " + t.getMessage());
    }
  }

  private static void printFinalReport(final Map<String, Boolean> report) {
    System.out.println("==== SaleADS Mi Negocio workflow report ====");
    for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
      System.out.printf("%s: %s%n", entry.getKey(), entry.getValue() ? "PASS" : "FAIL");
    }
  }

  private static String sanitize(final String value) {
    return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
  }

  private static void sleep(final long millis) {
    try {
      Thread.sleep(millis);
    } catch (final InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interrupted while waiting for UI update.", ie);
    }
  }

  @FunctionalInterface
  private interface CheckedRunnable {
    void run() throws Exception;
  }
}
