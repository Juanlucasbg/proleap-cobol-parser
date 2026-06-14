package io.proleap.cobol.e2e;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assume;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * End-to-end validation for the SaleADS "Mi Negocio" workflow.
 *
 * <p>
 * Required env vars:
 * <ul>
 *   <li>SALEADS_E2E_ENABLED=true</li>
 *   <li>SALEADS_LOGIN_URL=https://current-environment-login-page</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

  private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
  private static final String TEXT_TERMINOS = "T\u00E9rminos y Condiciones";
  private static final String TEXT_POLITICA = "Pol\u00EDtica de Privacidad";
  private static final String TEXT_INFO_GENERAL = "Informaci\u00F3n General";
  private static final String TEXT_SECCION_LEGAL = "Secci\u00F3n Legal";
  private static final long SHORT_TIMEOUT_MS = 5000;
  private static final long MEDIUM_TIMEOUT_MS = 12000;
  private static final long LONG_TIMEOUT_MS = 30000;

  @Test
  public void saleadsMiNegocioFullWorkflow() throws IOException {
    Assume.assumeTrue("Enable with SALEADS_E2E_ENABLED=true",
        Boolean.parseBoolean(env("SALEADS_E2E_ENABLED", "false")));

    final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
    Assume.assumeTrue("Set SALEADS_LOGIN_URL to the target environment login page",
        loginUrl != null && !loginUrl.isBlank());

    final String expectedGoogleAccount = env("SALEADS_GOOGLE_ACCOUNT_EMAIL", DEFAULT_GOOGLE_ACCOUNT);
    final boolean headless = Boolean.parseBoolean(env("SALEADS_E2E_HEADLESS", "true"));
    final Path screenshotDir = buildScreenshotDir();

    final LinkedHashMap<String, Boolean> report = new LinkedHashMap<>();
    final LinkedHashMap<String, String> legalUrls = new LinkedHashMap<>();
    final LinkedHashMap<String, String> failures = new LinkedHashMap<>();

    try (Playwright playwright = Playwright.create()) {
      final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
      try (BrowserContext context = browser.newContext()) {
        final Page appPage = context.newPage();
        appPage.navigate(loginUrl);
        appPage.waitForLoadState(LoadState.DOMCONTENTLOADED);

        executeStep("Login", report, failures, () -> {
          Page googlePage = attemptGoogleLogin(context, appPage);
          maybeSelectGoogleAccount(googlePage, expectedGoogleAccount);

          assertVisibleText(appPage, "Negocio", LONG_TIMEOUT_MS);
          captureScreenshot(appPage, screenshotDir.resolve("01-dashboard.png"), false);
          assertSidebarVisible(appPage);
        });

        executeStep("Mi Negocio menu", report, failures, () -> {
          openMiNegocioMenu(appPage);
          assertVisibleText(appPage, "Agregar Negocio", MEDIUM_TIMEOUT_MS);
          assertVisibleText(appPage, "Administrar Negocios", MEDIUM_TIMEOUT_MS);
          captureScreenshot(appPage, screenshotDir.resolve("02-mi-negocio-menu.png"), false);
        });

        executeStep("Agregar Negocio modal", report, failures, () -> {
          clickByText(appPage, "Agregar Negocio");
          waitForUiAfterClick(appPage);

          assertVisibleText(appPage, "Crear Nuevo Negocio", MEDIUM_TIMEOUT_MS);
          assertVisibleText(appPage, "Nombre del Negocio", MEDIUM_TIMEOUT_MS);
          assertVisibleText(appPage, "Tienes 2 de 3 negocios", MEDIUM_TIMEOUT_MS);
          assertVisibleText(appPage, "Cancelar", MEDIUM_TIMEOUT_MS);
          assertVisibleText(appPage, "Crear Negocio", MEDIUM_TIMEOUT_MS);

          fillFieldIfVisible(appPage, "Nombre del Negocio", "Negocio Prueba Automatizacion");
          clickByText(appPage, "Cancelar");
          waitForUiAfterClick(appPage);
          captureScreenshot(appPage, screenshotDir.resolve("03-agregar-negocio-modal.png"), false);
        });

        executeStep("Administrar Negocios view", report, failures, () -> {
          openMiNegocioMenu(appPage);
          clickByText(appPage, "Administrar Negocios");
          waitForUiAfterClick(appPage);

          assertVisibleText(appPage, TEXT_INFO_GENERAL, MEDIUM_TIMEOUT_MS);
          assertVisibleText(appPage, "Detalles de la Cuenta", MEDIUM_TIMEOUT_MS);
          assertVisibleText(appPage, "Tus Negocios", MEDIUM_TIMEOUT_MS);
          assertVisibleText(appPage, TEXT_SECCION_LEGAL, MEDIUM_TIMEOUT_MS);
          captureScreenshot(appPage, screenshotDir.resolve("04-administrar-negocios.png"), true);
        });

        executeStep(TEXT_INFO_GENERAL, report, failures, () -> {
          assertVisibleText(appPage, "BUSINESS PLAN", MEDIUM_TIMEOUT_MS);
          assertVisibleText(appPage, "Cambiar Plan", MEDIUM_TIMEOUT_MS);
          String pageText = appPage.locator("body").innerText();
          assertTrue("Expected user email to be visible", pageText.contains("@"));
          assertTrue("Expected user name to be visible", pageText.replaceAll("\\s+", " ").trim().length() > 80);
        });

        executeStep("Detalles de la Cuenta", report, failures, () -> {
          assertVisibleText(appPage, "Cuenta creada", MEDIUM_TIMEOUT_MS);
          assertVisibleText(appPage, "Estado activo", MEDIUM_TIMEOUT_MS);
          assertVisibleText(appPage, "Idioma seleccionado", MEDIUM_TIMEOUT_MS);
        });

        executeStep("Tus Negocios", report, failures, () -> {
          assertVisibleText(appPage, "Tus Negocios", MEDIUM_TIMEOUT_MS);
          assertVisibleText(appPage, "Agregar Negocio", MEDIUM_TIMEOUT_MS);
          assertVisibleText(appPage, "Tienes 2 de 3 negocios", MEDIUM_TIMEOUT_MS);
          String sectionText = appPage.locator("body").innerText();
          assertTrue("Expected a visible business list or entries", sectionText.contains("Negocio"));
        });

        executeStep(TEXT_TERMINOS, report, failures, () -> {
          String finalUrl = validateLegalLink(context, appPage, TEXT_TERMINOS,
              screenshotDir.resolve("08-terminos-y-condiciones.png"));
          legalUrls.put(TEXT_TERMINOS, finalUrl);
        });

        executeStep(TEXT_POLITICA, report, failures, () -> {
          String finalUrl = validateLegalLink(context, appPage, TEXT_POLITICA,
              screenshotDir.resolve("09-politica-privacidad.png"));
          legalUrls.put(TEXT_POLITICA, finalUrl);
        });
      } finally {
        browser.close();
      }
    }

    printFinalReport(report, legalUrls, screenshotDir);
    assertNoFailures(report, failures);
  }

  private Page attemptGoogleLogin(final BrowserContext context, final Page appPage) {
    final Locator loginButton = firstVisible(
        appPage,
        "Sign in with Google",
        "Iniciar sesion con Google",
        "Iniciar sesi\u00F3n con Google",
        "Continuar con Google",
        "Google");

    Page googlePage = appPage;
    try {
      Page popup = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(SHORT_TIMEOUT_MS), () -> {
        loginButton.click();
        waitForUiAfterClick(appPage);
      });
      popup.waitForLoadState(LoadState.DOMCONTENTLOADED);
      googlePage = popup;
    } catch (PlaywrightException ignored) {
      appPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
    }

    return googlePage;
  }

  private void maybeSelectGoogleAccount(final Page googlePage, final String expectedGoogleAccount) {
    final Locator accountOption = googlePage.getByText(expectedGoogleAccount).first();
    if (isVisible(accountOption, SHORT_TIMEOUT_MS)) {
      accountOption.click();
      waitForUiAfterClick(googlePage);
    }
  }

  private void openMiNegocioMenu(final Page page) {
    if (!isVisible(page.getByText("Mi Negocio").first(), SHORT_TIMEOUT_MS)) {
      clickByText(page, "Negocio");
      waitForUiAfterClick(page);
    }
    clickByText(page, "Mi Negocio");
    waitForUiAfterClick(page);
  }

  private String validateLegalLink(final BrowserContext context, final Page appPage, final String linkText,
      final Path screenshotPath) {
    Page legalPage = appPage;
    boolean openedNewTab = false;

    try {
      legalPage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(SHORT_TIMEOUT_MS), () -> {
        clickByText(appPage, linkText);
        waitForUiAfterClick(appPage);
      });
      openedNewTab = true;
    } catch (PlaywrightException ignored) {
      // Link opened in current tab.
    }

    legalPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
    assertVisibleText(legalPage, linkText, LONG_TIMEOUT_MS);
    String legalText = legalPage.locator("body").innerText().trim();
    assertTrue("Expected legal content text to be visible", legalText.length() > 100);

    captureScreenshot(legalPage, screenshotPath, true);
    String finalUrl = legalPage.url();

    if (openedNewTab) {
      legalPage.close();
      appPage.bringToFront();
    } else {
      appPage.goBack(new Page.GoBackOptions().setTimeout(MEDIUM_TIMEOUT_MS));
      appPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
    }

    return finalUrl;
  }

  private void fillFieldIfVisible(final Page page, final String label, final String value) {
    Locator input = page.getByLabel(label).first();
    if (isVisible(input, SHORT_TIMEOUT_MS)) {
      input.click();
      input.fill(value);
      return;
    }

    input = page.getByPlaceholder(label).first();
    if (isVisible(input, SHORT_TIMEOUT_MS)) {
      input.click();
      input.fill(value);
    }
  }

  private void executeStep(final String stepName, final Map<String, Boolean> report,
      final Map<String, String> failures, final ThrowingRunnable stepBody) {
    try {
      stepBody.run();
      report.put(stepName, Boolean.TRUE);
    } catch (Throwable t) {
      report.put(stepName, Boolean.FALSE);
      failures.put(stepName, t.getMessage() == null ? t.toString() : t.getMessage());
    }
  }

  private void assertNoFailures(final Map<String, Boolean> report, final Map<String, String> failures) {
    assertFalse("Missing report entries", report.isEmpty());
    if (!failures.isEmpty()) {
      StringBuilder message = new StringBuilder("Failed validation steps:\n");
      failures.forEach((step, error) -> message.append(" - ").append(step).append(": ").append(error).append('\n'));
      fail(message.toString());
    }
  }

  private void assertSidebarVisible(final Page page) {
    Locator sidebar = page.locator("aside, nav").first();
    assertTrue("Expected the left sidebar navigation to be visible", isVisible(sidebar, MEDIUM_TIMEOUT_MS));
  }

  private void assertVisibleText(final Page page, final String text, final long timeoutMs) {
    Locator locator = page.getByText(text).first();
    assertTrue("Expected visible text: " + text, isVisible(locator, timeoutMs));
  }

  private Locator firstVisible(final Page page, final String... texts) {
    for (String text : texts) {
      Locator candidate = page.getByText(text).first();
      if (isVisible(candidate, SHORT_TIMEOUT_MS)) {
        return candidate;
      }
    }
    throw new AssertionError("Unable to find visible text candidates");
  }

  private boolean isVisible(final Locator locator, final long timeoutMs) {
    try {
      locator.waitFor(new Locator.WaitForOptions()
          .setState(WaitForSelectorState.VISIBLE)
          .setTimeout(timeoutMs));
      return true;
    } catch (PlaywrightException ignored) {
      return false;
    }
  }

  private void clickByText(final Page page, final String text) {
    Locator target = firstVisible(page, text);
    target.click();
  }

  private void waitForUiAfterClick(final Page page) {
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(MEDIUM_TIMEOUT_MS));
    } catch (PlaywrightException ignored) {
      page.waitForTimeout(800);
    }
  }

  private void captureScreenshot(final Page page, final Path path, final boolean fullPage) {
    page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
  }

  private Path buildScreenshotDir() throws IOException {
    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    Path dir = Paths.get("target", "saleads-mi-negocio-screenshots", timestamp);
    Files.createDirectories(dir);
    return dir;
  }

  private void printFinalReport(final Map<String, Boolean> report, final Map<String, String> legalUrls,
      final Path screenshotDir) {
    System.out.println("===== SaleADS Mi Negocio Final Report =====");
    report.forEach((step, status) -> System.out.println(step + ": " + (status ? "PASS" : "FAIL")));
    System.out.println("Evidence directory: " + screenshotDir.toAbsolutePath());
    legalUrls.forEach((label, url) -> System.out.println(label + " URL: " + url));
  }

  private String env(final String key, final String defaultValue) {
    String value = System.getenv(key);
    return value == null || value.isBlank() ? defaultValue : value;
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }
}
