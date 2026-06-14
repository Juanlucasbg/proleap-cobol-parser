package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * Environment-agnostic UI workflow validation for SaleADS "Mi Negocio".
 *
 * <p>
 * Required environment variable:
 * </p>
 * <ul>
 * <li>SALEADS_LOGIN_URL: Login page URL for the current environment (dev, staging, production).</li>
 * </ul>
 *
 * <p>
 * Optional environment variables:
 * </p>
 * <ul>
 * <li>SALEADS_HEADLESS: true/false, defaults to true.</li>
 * <li>SALEADS_GOOGLE_ACCOUNT: defaults to juanlucasbarbiergarzon@gmail.com.</li>
 * </ul>
 */
public class SaleadsMiNegocioWorkflowIT {

  private static final int DEFAULT_TIMEOUT_MS = 30_000;
  private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

  @Test
  public void saleadsMiNegocioFullWorkflow() throws IOException {
    final String loginUrl = requireEnv("SALEADS_LOGIN_URL");
    final String googleAccount = env("SALEADS_GOOGLE_ACCOUNT", "juanlucasbarbiergarzon@gmail.com");
    final boolean headless = Boolean.parseBoolean(env("SALEADS_HEADLESS", "true"));
    final Path evidenceDir = createEvidenceDirectory();
    final Map<String, Boolean> report = new LinkedHashMap<>();
    final Map<String, String> details = new LinkedHashMap<>();
    final Map<String, String> finalUrls = new LinkedHashMap<>();

    try (Playwright playwright = Playwright.create()) {
      final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
      final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
      Page appPage = context.newPage();
      appPage.navigate(loginUrl);
      waitForUi(appPage);

      runStep("Login", report, details, () -> stepLoginWithGoogle(appPage, googleAccount, evidenceDir));
      runStep("Mi Negocio menu", report, details, () -> stepOpenMiNegocioMenu(appPage, evidenceDir));
      runStep("Agregar Negocio modal", report, details, () -> stepValidateAgregarNegocioModal(appPage, evidenceDir));
      runStep("Administrar Negocios view", report, details, () -> stepOpenAdministrarNegocios(appPage, evidenceDir));
      runStep("Información General", report, details, () -> stepValidateInformacionGeneral(appPage));
      runStep("Detalles de la Cuenta", report, details, () -> stepValidateDetallesCuenta(appPage));
      runStep("Tus Negocios", report, details, () -> stepValidateTusNegocios(appPage));
      runStep("Términos y Condiciones", report, details, () -> {
        final String url = stepValidateLegalDocument(context, appPage, "Términos y Condiciones",
            Pattern.compile("(?i)T[ée]rminos\\s+y\\s+Condiciones"),
            evidenceDir.resolve("08-terminos-y-condiciones.png"));
        finalUrls.put("Términos y Condiciones", url);
      });
      runStep("Política de Privacidad", report, details, () -> {
        final String url = stepValidateLegalDocument(context, appPage, "Política de Privacidad",
            Pattern.compile("(?i)Pol[íi]tica\\s+de\\s+Privacidad"),
            evidenceDir.resolve("09-politica-de-privacidad.png"));
        finalUrls.put("Política de Privacidad", url);
      });

      writeFinalReport(evidenceDir, report, details, finalUrls);
      browser.close();
    }

    final String summary = buildSummary(report, details);
    assertTrue(summary, report.values().stream().allMatch(Boolean.TRUE::equals));
  }

  private void stepLoginWithGoogle(final Page page, final String googleAccount, final Path evidenceDir) {
    Locator loginButton = page.getByRole(AriaRole.BUTTON,
        new Page.GetByRoleOptions().setName(Pattern.compile("(?i)(sign in|iniciar sesi[óo]n|continuar).*google|google")));

    if (loginButton.count() == 0) {
      loginButton = page.getByText(Pattern.compile("(?i)(sign in|iniciar sesi[óo]n|continuar).*google|google"));
    }

    clickFirstVisible(loginButton, page, "Google login button");
    waitForUi(page);
    selectGoogleAccountIfPresent(page, googleAccount);
    waitForUi(page);

    expectVisible(page.locator("aside, nav").first(), "left sidebar navigation");
    expectVisible(page.getByText(Pattern.compile("(?i)Negocio")).first(), "main application interface");
    captureScreenshot(page, evidenceDir.resolve("01-dashboard-loaded.png"), false);
  }

  private void stepOpenMiNegocioMenu(final Page page, final Path evidenceDir) {
    clickByVisibleText(page, "Negocio");
    clickByVisibleText(page, "Mi Negocio");
    expectVisible(page.getByText("Agregar Negocio", new Page.GetByTextOptions().setExact(true)).first(),
        "Agregar Negocio option");
    expectVisible(page.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(true)).first(),
        "Administrar Negocios option");
    captureScreenshot(page, evidenceDir.resolve("02-mi-negocio-menu-expanded.png"), false);
  }

  private void stepValidateAgregarNegocioModal(final Page page, final Path evidenceDir) {
    clickByVisibleText(page, "Agregar Negocio");
    expectVisible(page.getByText("Crear Nuevo Negocio", new Page.GetByTextOptions().setExact(true)).first(),
        "Crear Nuevo Negocio modal title");
    expectVisible(page.getByText("Nombre del Negocio", new Page.GetByTextOptions().setExact(true)).first(),
        "Nombre del Negocio field");
    expectVisible(page.getByText(Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios")).first(),
        "business quota text");
    expectVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")).first(),
        "Cancelar button");
    expectVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Crear Negocio")).first(),
        "Crear Negocio button");
    captureScreenshot(page, evidenceDir.resolve("03-agregar-negocio-modal.png"), false);

    Locator nameField = page.getByLabel("Nombre del Negocio");
    if (nameField.count() == 0) {
      nameField = page.locator("input[placeholder*='Negocio'], input[name*='negocio'], input[id*='negocio']").first();
    } else {
      nameField = nameField.first();
    }

    if (nameField.count() > 0) {
      nameField.click();
      nameField.fill("Negocio Prueba Automatizacion");
    }

    clickByVisibleText(page, "Cancelar");
    waitForUi(page);
  }

  private void stepOpenAdministrarNegocios(final Page page, final Path evidenceDir) {
    Locator administrar = page.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(true)).first();

    if (administrar.count() == 0 || !administrar.isVisible()) {
      clickByVisibleText(page, "Mi Negocio");
      administrar = page.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(true)).first();
    }

    clickFirstVisible(administrar, page, "Administrar Negocios option");
    expectVisible(page.getByText("Información General", new Page.GetByTextOptions().setExact(true)).first(),
        "Información General section");
    expectVisible(page.getByText("Detalles de la Cuenta", new Page.GetByTextOptions().setExact(true)).first(),
        "Detalles de la Cuenta section");
    expectVisible(page.getByText("Tus Negocios", new Page.GetByTextOptions().setExact(true)).first(),
        "Tus Negocios section");
    expectVisible(page.getByText("Sección Legal", new Page.GetByTextOptions().setExact(true)).first(),
        "Sección Legal section");
    captureScreenshot(page, evidenceDir.resolve("04-administrar-negocios-account-page.png"), true);
  }

  private void stepValidateInformacionGeneral(final Page page) {
    expectVisible(page.getByText("Información General", new Page.GetByTextOptions().setExact(true)).first(),
        "Información General heading");
    expectVisible(page.getByText(EMAIL_PATTERN).first(), "user email");
    expectVisible(page.getByText("BUSINESS PLAN", new Page.GetByTextOptions().setExact(true)).first(),
        "BUSINESS PLAN text");
    expectVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cambiar Plan")).first(),
        "Cambiar Plan button");

    final String pageText = page.content();
    final Pattern likelyNamePattern = Pattern.compile(">[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)+<");
    assertTrue("User name is not clearly visible in Información General section.",
        likelyNamePattern.matcher(pageText).find());
  }

  private void stepValidateDetallesCuenta(final Page page) {
    expectVisible(page.getByText("Cuenta creada", new Page.GetByTextOptions().setExact(true)).first(),
        "'Cuenta creada' label");
    expectVisible(page.getByText("Estado activo", new Page.GetByTextOptions().setExact(true)).first(),
        "'Estado activo' label");
    expectVisible(page.getByText("Idioma seleccionado", new Page.GetByTextOptions().setExact(true)).first(),
        "'Idioma seleccionado' label");
  }

  private void stepValidateTusNegocios(final Page page) {
    expectVisible(page.getByText("Tus Negocios", new Page.GetByTextOptions().setExact(true)).first(),
        "'Tus Negocios' section");
    expectVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Agregar Negocio")).first(),
        "'Agregar Negocio' button");
    expectVisible(page.getByText(Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios")).first(),
        "business quota text");
  }

  private String stepValidateLegalDocument(final BrowserContext context, final Page appPage, final String linkText,
      final Pattern headingPattern, final Path screenshotPath) {
    final Page targetPage = openLinkAndResolveTargetPage(context, appPage, linkText);
    expectVisible(targetPage.getByText(headingPattern).first(), headingPattern + " heading");
    expectVisible(targetPage.locator("p, article, main").first(), "legal content text");
    captureScreenshot(targetPage, screenshotPath, true);
    final String finalUrl = targetPage.url();

    if (targetPage != appPage) {
      targetPage.close();
      appPage.bringToFront();
      waitForUi(appPage);
    } else {
      appPage.goBack();
      waitForUi(appPage);
    }

    return finalUrl;
  }

  private Page openLinkAndResolveTargetPage(final BrowserContext context, final Page appPage, final String linkText) {
    try {
      final Page newPage = context.waitForPage(() -> clickByVisibleText(appPage, linkText),
          new BrowserContext.WaitForPageOptions().setTimeout(8_000));
      waitForUi(newPage);
      return newPage;
    } catch (PlaywrightException ignored) {
      waitForUi(appPage);
      return appPage;
    }
  }

  private void selectGoogleAccountIfPresent(final Page page, final String accountEmail) {
    try {
      final Locator account = page.getByText(accountEmail, new Page.GetByTextOptions().setExact(true)).first();
      if (account.count() > 0 && account.isVisible()) {
        account.click();
        waitForUi(page);
      }
    } catch (PlaywrightException ignored) {
      // Account selector is optional; continue when Google immediately returns to the app.
    }
  }

  private void clickByVisibleText(final Page page, final String text) {
    Locator locator = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(text)).first();
    if (locator.count() == 0) {
      locator = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(text)).first();
    }
    if (locator.count() == 0) {
      locator = page.getByText(text, new Page.GetByTextOptions().setExact(true)).first();
    }

    clickFirstVisible(locator, page, "'" + text + "'");
  }

  private void clickFirstVisible(final Locator locator, final Page page, final String elementDescription) {
    if (locator.count() == 0) {
      throw new AssertionError("Unable to find " + elementDescription + " on the page.");
    }

    locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));
    locator.click();
    waitForUi(page);
  }

  private void expectVisible(final Locator locator, final String description) {
    if (locator.count() == 0) {
      throw new AssertionError("Expected to find " + description + ", but locator did not match any elements.");
    }
    locator.first().waitFor(
        new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));
  }

  private void waitForUi(final Page page) {
    try {
      page.waitForLoadState(LoadState.DOMCONTENTLOADED);
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10_000));
    } catch (TimeoutError ignored) {
      // Network-idle is best effort; dynamic UIs may keep background requests active.
    }
    page.waitForTimeout(800);
  }

  private void captureScreenshot(final Page page, final Path file, final boolean fullPage) {
    page.screenshot(new Page.ScreenshotOptions().setPath(file).setFullPage(fullPage));
  }

  private void writeFinalReport(final Path evidenceDir, final Map<String, Boolean> report, final Map<String, String> details,
      final Map<String, String> finalUrls) throws IOException {
    StringBuilder content = new StringBuilder();
    content.append("SaleADS Mi Negocio Full Test Report\n");
    content.append("=================================\n\n");
    for (Map.Entry<String, Boolean> entry : report.entrySet()) {
      content.append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL").append('\n');
      if (!entry.getValue()) {
        content.append("  Reason: ").append(details.get(entry.getKey())).append('\n');
      }
    }
    if (!finalUrls.isEmpty()) {
      content.append("\nFinal URLs\n");
      content.append("----------\n");
      for (Map.Entry<String, String> finalUrl : finalUrls.entrySet()) {
        content.append(finalUrl.getKey()).append(": ").append(finalUrl.getValue()).append('\n');
      }
    }

    final Path reportPath = evidenceDir.resolve("10-final-report.txt");
    Files.writeString(reportPath, content.toString());
    System.out.println(content);
    System.out.println("Evidence saved to: " + evidenceDir.toAbsolutePath());
  }

  private String buildSummary(final Map<String, Boolean> report, final Map<String, String> details) {
    StringBuilder summary = new StringBuilder("One or more SaleADS validations failed.\n");
    for (Map.Entry<String, Boolean> entry : report.entrySet()) {
      summary.append("- ").append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL");
      if (!entry.getValue()) {
        summary.append(" (").append(details.get(entry.getKey())).append(")");
      }
      summary.append('\n');
    }
    return summary.toString();
  }

  private void runStep(final String stepName, final Map<String, Boolean> report, final Map<String, String> details,
      final CheckedRunnable runnable) {
    try {
      runnable.run();
      report.put(stepName, true);
      details.put(stepName, "PASS");
    } catch (Exception | AssertionError ex) {
      report.put(stepName, false);
      details.put(stepName, normalizeMessage(ex));
    }
  }

  private String normalizeMessage(final Throwable throwable) {
    final String message = throwable.getMessage();
    if (message == null || message.isBlank()) {
      return throwable.getClass().getSimpleName();
    }
    return message.replaceAll("\\s+", " ").trim();
  }

  private Path createEvidenceDirectory() throws IOException {
    final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).format(LocalDateTime.now());
    final Path evidenceDir = Path.of("target", "saleads-evidence", timestamp);
    Files.createDirectories(evidenceDir);
    return evidenceDir;
  }

  private String requireEnv(final String key) {
    final String value = System.getenv(key);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Missing required environment variable: " + key
          + ". Set it to the SaleADS login URL for the target environment.");
    }
    return value;
  }

  private String env(final String key, final String defaultValue) {
    final String value = System.getenv(key);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    return Normalizer.normalize(value, Normalizer.Form.NFC);
  }

  @FunctionalInterface
  private interface CheckedRunnable {
    void run() throws Exception;
  }
}
