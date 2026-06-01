package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
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

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.LoadState;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;

/**
 * End-to-end test for the SaleADS.ai Mi Negocio workflow.
 *
 * <p>
 * Environment variables:
 * <ul>
 * <li>SALEADS_LOGIN_URL (required): login URL for the current environment</li>
 * <li>SALEADS_HEADLESS (optional, default true)</li>
 * <li>SALEADS_GOOGLE_ACCOUNT_EMAIL (optional, default
 * juanlucasbarbiergarzon@gmail.com)</li>
 * </ul>
 */
public class SaleadsMiNegocioFullWorkflowTest {

  private static final String DEFAULT_GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
  private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+");

  @Test
  public void saleadsMiNegocioFullTest() throws IOException {
    final String loginUrl = readConfig("SALEADS_LOGIN_URL", "saleads.login.url", "");
    Assert.assertFalse("SALEADS_LOGIN_URL (or -Dsaleads.login.url) is required.", loginUrl.isBlank());

    final String googleAccountEmail =
        readConfig("SALEADS_GOOGLE_ACCOUNT_EMAIL", "saleads.google.account.email", DEFAULT_GOOGLE_ACCOUNT_EMAIL);
    final boolean headless = Boolean.parseBoolean(readConfig("SALEADS_HEADLESS", "saleads.headless", "true"));

    final Path evidenceDir = createEvidenceDirectory();
    final LinkedHashMap<String, StepResult> results = initializeResults();

    try (Playwright playwright = Playwright.create()) {
      final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
      final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
      final Page page = context.newPage();

      page.navigate(loginUrl);
      waitForUi(page);

      runStep(results, "Login", () -> {
        final Locator googleLoginButton = firstVisible("Google login button",
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in with Google")),
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Iniciar sesión con Google")),
            page.getByText("Sign in with Google"),
            page.getByText("Iniciar sesión con Google"),
            page.getByText("Google"));
        clickAndWait(page, googleLoginButton);

        chooseGoogleAccountIfShown(page, googleAccountEmail);
        waitForUi(page);

        final Locator sidebar = firstVisible("left sidebar",
            page.getByRole(AriaRole.NAVIGATION),
            page.locator("aside"),
            page.getByText("Negocio"));
        assertTrue("Left sidebar should be visible after login.", sidebar.isVisible());
        screenshot(page, evidenceDir.resolve("01-dashboard-loaded.png"), false);
      });

      runStep(results, "Mi Negocio menu", () -> {
        clickAndWait(page, firstVisible("Mi Negocio menu item",
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Mi Negocio")),
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Mi Negocio")),
            page.getByText("Mi Negocio")));

        assertVisibleText(page, "Agregar Negocio");
        assertVisibleText(page, "Administrar Negocios");
        screenshot(page, evidenceDir.resolve("02-mi-negocio-expanded.png"), false);
      });

      runStep(results, "Agregar Negocio modal", () -> {
        clickAndWait(page, firstVisible("Agregar Negocio option",
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Agregar Negocio")),
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Agregar Negocio")),
            page.getByText("Agregar Negocio")));

        assertVisibleText(page, "Crear Nuevo Negocio");
        assertTrue("Expected Nombre del Negocio input field.",
            firstVisible("Nombre del Negocio field",
                page.getByLabel("Nombre del Negocio"),
                page.getByPlaceholder("Nombre del Negocio"),
                page.locator("input[type='text']")).isVisible());
        assertVisibleText(page, "Tienes 2 de 3 negocios");
        assertVisibleText(page, "Cancelar");
        assertVisibleText(page, "Crear Negocio");
        screenshot(page, evidenceDir.resolve("03-agregar-negocio-modal.png"), false);

        final Locator businessNameInput = firstVisible("Nombre del Negocio input", page.getByLabel("Nombre del Negocio"),
            page.getByPlaceholder("Nombre del Negocio"), page.locator("input[type='text']"));
        businessNameInput.click();
        businessNameInput.fill("Negocio Prueba Automatizacion");
        clickAndWait(page, firstVisible("Cancelar button",
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")),
            page.getByText("Cancelar")));
      });

      runStep(results, "Administrar Negocios view", () -> {
        if (!isVisible(page.getByText("Administrar Negocios"))) {
          clickAndWait(page, firstVisible("Mi Negocio menu item",
              page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Mi Negocio")),
              page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Mi Negocio")),
              page.getByText("Mi Negocio")));
        }

        clickAndWait(page, firstVisible("Administrar Negocios option",
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Administrar Negocios")),
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Administrar Negocios")),
            page.getByText("Administrar Negocios")));

        assertVisibleText(page, "Informacion General");
        assertVisibleText(page, "Detalles de la Cuenta");
        assertVisibleText(page, "Tus Negocios");
        assertVisibleText(page, "Seccion Legal");
        screenshot(page, evidenceDir.resolve("04-administrar-negocios-full-page.png"), true);
      });

      runStep(results, "Información General", () -> {
        assertVisibleText(page, "Informacion General");
        assertTrue("Expected an email in Informacion General.",
            EMAIL_PATTERN.matcher(page.content()).find());
        assertVisibleText(page, "BUSINESS PLAN");
        assertVisibleText(page, "Cambiar Plan");
      });

      runStep(results, "Detalles de la Cuenta", () -> {
        assertVisibleText(page, "Cuenta creada");
        assertVisibleText(page, "Estado activo");
        assertVisibleText(page, "Idioma seleccionado");
      });

      runStep(results, "Tus Negocios", () -> {
        assertVisibleText(page, "Tus Negocios");
        assertVisibleText(page, "Agregar Negocio");
        assertVisibleText(page, "Tienes 2 de 3 negocios");
      });

      runStep(results, "Términos y Condiciones", () -> {
        final String url = openAndValidateLegalDocument(page, evidenceDir,
            "Terminos y Condiciones",
            "Terminos y Condiciones",
            "08-terminos-y-condiciones.png");
        results.get("Términos y Condiciones").details = "URL: " + url;
      });

      runStep(results, "Política de Privacidad", () -> {
        final String url = openAndValidateLegalDocument(page, evidenceDir,
            "Politica de Privacidad",
            "Politica de Privacidad",
            "09-politica-de-privacidad.png");
        results.get("Política de Privacidad").details = "URL: " + url;
      });

      context.close();
      browser.close();
    } finally {
      final Path reportPath = writeReport(evidenceDir, results);
      final List<String> failures = collectFailures(results);
      if (!failures.isEmpty()) {
        Assert.fail("saleads_mi_negocio_full_test FAILED. See report: " + reportPath + " | Failures: " + failures);
      }
    }
  }

  private static LinkedHashMap<String, StepResult> initializeResults() {
    final LinkedHashMap<String, StepResult> results = new LinkedHashMap<>();
    results.put("Login", StepResult.pending());
    results.put("Mi Negocio menu", StepResult.pending());
    results.put("Agregar Negocio modal", StepResult.pending());
    results.put("Administrar Negocios view", StepResult.pending());
    results.put("Información General", StepResult.pending());
    results.put("Detalles de la Cuenta", StepResult.pending());
    results.put("Tus Negocios", StepResult.pending());
    results.put("Términos y Condiciones", StepResult.pending());
    results.put("Política de Privacidad", StepResult.pending());
    return results;
  }

  private static void runStep(final Map<String, StepResult> results, final String key, final StepAction action) {
    final StepResult result = results.get(key);
    try {
      action.run();
      result.status = "PASS";
      if (result.details == null || result.details.isBlank()) {
        result.details = "Validated successfully.";
      }
    } catch (final Throwable error) {
      result.status = "FAIL";
      result.details = sanitizeError(error);
    }
  }

  private static String openAndValidateLegalDocument(
      final Page applicationPage,
      final Path evidenceDir,
      final String linkText,
      final String expectedHeadingText,
      final String screenshotName) {
    Page legalPage = null;
    final Locator legalLink = firstVisible("Legal link " + linkText,
        applicationPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkText)),
        applicationPage.getByText(linkText));

    try {
      legalPage = applicationPage.waitForPopup(() -> clickAndWait(applicationPage, legalLink),
          new Page.WaitForPopupOptions().setTimeout(6000));
    } catch (final TimeoutError popupNotOpened) {
      clickAndWait(applicationPage, legalLink);
      legalPage = applicationPage;
    }

    waitForUi(legalPage);
    assertVisibleText(legalPage, expectedHeadingText);

    final String legalText = legalPage.locator("body").innerText();
    assertTrue("Legal content should be visible on " + expectedHeadingText + ".", legalText != null && legalText.trim().length() > 120);

    screenshot(legalPage, evidenceDir.resolve(screenshotName), false);
    final String finalUrl = legalPage.url();

    if (legalPage != applicationPage) {
      legalPage.close();
      applicationPage.bringToFront();
      waitForUi(applicationPage);
    } else {
      applicationPage.goBack();
      waitForUi(applicationPage);
    }

    return finalUrl;
  }

  private static Locator firstVisible(final String description, final Locator... candidates) {
    for (final Locator candidate : candidates) {
      if (candidate == null || candidate.count() == 0) {
        continue;
      }

      final Locator first = candidate.first();
      if (isVisible(first)) {
        return first;
      }
    }

    throw new IllegalStateException("Could not find visible element: " + description);
  }

  private static void assertVisibleText(final Page page, final String textWithoutAccents) {
    final Locator visibleText = firstVisible("text: " + textWithoutAccents,
        page.getByText(textWithoutAccents),
        page.getByText(accentedText(textWithoutAccents)));
    assertTrue("Expected visible text: " + textWithoutAccents, visibleText.isVisible());
  }

  private static String accentedText(final String text) {
    return text
        .replace("Informacion", "Información")
        .replace("Seccion", "Sección")
        .replace("Terminos", "Términos")
        .replace("Politica", "Política")
        .replace("Automatizacion", "Automatización");
  }

  private static void clickAndWait(final Page page, final Locator locator) {
    locator.scrollIntoViewIfNeeded();
    locator.click();
    waitForUi(page);
  }

  private static void chooseGoogleAccountIfShown(final Page page, final String googleAccountEmail) {
    final Locator accountOption = page.getByText(googleAccountEmail);
    if (accountOption.count() == 0 || !isVisible(accountOption.first())) {
      return;
    }

    clickAndWait(page, accountOption.first());
  }

  private static void waitForUi(final Page page) {
    try {
      page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(15000));
    } catch (final TimeoutError ignored) {
      // Some SPA transitions do not trigger all browser lifecycle states.
    }

    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(6000));
    } catch (final TimeoutError ignored) {
      // Long-lived network requests are common in modern SPAs.
    }

    page.waitForTimeout(400);
  }

  private static boolean isVisible(final Locator locator) {
    try {
      return locator.isVisible();
    } catch (final RuntimeException ignored) {
      return false;
    }
  }

  private static void screenshot(final Page page, final Path destination, final boolean fullPage) {
    page.screenshot(new Page.ScreenshotOptions()
        .setPath(destination)
        .setFullPage(fullPage));
  }

  private static Path createEvidenceDirectory() throws IOException {
    final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    final Path evidenceDir = Paths.get("target", "saleads-evidence", "saleads-mi-negocio-full-test-" + timestamp);
    Files.createDirectories(evidenceDir);
    return evidenceDir;
  }

  private static Path writeReport(final Path evidenceDir, final Map<String, StepResult> results) throws IOException {
    final Path reportPath = evidenceDir.resolve("final-report.md");
    final List<String> lines = new ArrayList<>();
    lines.add("# SaleADS Mi Negocio Full Test Report");
    lines.add("");
    lines.add("| Field | Result | Details |");
    lines.add("|---|---|---|");

    for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
      final String details = entry.getValue().details == null ? "" : entry.getValue().details;
      lines.add("| " + entry.getKey() + " | " + entry.getValue().status + " | " + details.replace("\n", " ") + " |");
    }

    lines.add("");
    lines.add("Evidence directory: " + evidenceDir.toAbsolutePath());
    Files.write(reportPath, lines);
    return reportPath;
  }

  private static List<String> collectFailures(final Map<String, StepResult> results) {
    final List<String> failures = new ArrayList<>();
    for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
      if (!"PASS".equals(entry.getValue().status)) {
        failures.add(entry.getKey());
      }
    }
    return failures;
  }

  private static String sanitizeError(final Throwable throwable) {
    final String message = throwable.getMessage();
    if (message == null || message.isBlank()) {
      return throwable.getClass().getSimpleName();
    }

    return message.replace('\n', ' ').replace('\r', ' ');
  }

  private static String readConfig(final String envVarName, final String propertyName, final String defaultValue) {
    final String envValue = System.getenv(envVarName);
    if (envValue != null && !envValue.isBlank()) {
      return envValue.trim();
    }

    final String propertyValue = System.getProperty(propertyName);
    if (propertyValue != null && !propertyValue.isBlank()) {
      return propertyValue.trim();
    }

    return defaultValue;
  }

  @FunctionalInterface
  private interface StepAction {
    void run() throws Exception;
  }

  private static final class StepResult {
    private String status;
    private String details;

    static StepResult pending() {
      final StepResult result = new StepResult();
      result.status = "FAIL";
      result.details = "Not executed.";
      return result;
    }
  }
}
