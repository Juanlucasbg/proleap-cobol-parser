package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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

/**
 * Full SaleADS "Mi Negocio" workflow validation.
 *
 * <p>This test is gated and runs only when SALEADS_E2E_ENABLED=true. It intentionally does not
 * hardcode any SaleADS URL and can run against any environment by passing runtime inputs.
 *
 * <p>Optional environment variables:
 *
 * <ul>
 *   <li>SALEADS_E2E_ENABLED=true (required to execute)
 *   <li>SALEADS_LOGIN_URL=https://... (recommended; if omitted, current page must already be login)
 *   <li>SALEADS_GOOGLE_ACCOUNT=email@example.com (defaults to required account from spec)
 *   <li>SALEADS_HEADLESS=true|false (defaults to true)
 *   <li>SALEADS_BROWSER_PROFILE_DIR=/path/to/chrome-profile (recommended for Google session reuse)
 * </ul>
 */
public class SaleadsMiNegocioWorkflowTest {

  private static final int DEFAULT_TIMEOUT_MS = 20_000;
  private static final int SHORT_TIMEOUT_MS = 5_000;
  private static final Path EVIDENCE_DIR = Paths.get("target", "saleads-evidence");
  private static final String FINAL_REPORT_PATH = "final-report.json";
  private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

  private final Map<String, String> report = new LinkedHashMap<>();
  private final List<String> failures = new ArrayList<>();
  private final Map<String, String> capturedUrls = new LinkedHashMap<>();
  private final Map<String, String> screenshots = new LinkedHashMap<>();

  @Test
  public void saleadsMiNegocioFullWorkflow() throws IOException {
    Assume.assumeTrue(
        "Set SALEADS_E2E_ENABLED=true to run SaleADS E2E workflow.",
        Boolean.parseBoolean(env("SALEADS_E2E_ENABLED", "false")));

    initializeReport();
    Files.createDirectories(EVIDENCE_DIR);

    BrowserSession session = null;
    try {
      session = createBrowserSession();
      final Page page = session.page;
      final String loginUrl = env("SALEADS_LOGIN_URL", "").trim();

      if (!loginUrl.isEmpty()) {
        page.navigate(loginUrl);
      }
      stabilizeUi(page);

      runStep("Login", page, () -> validateLoginWithGoogle(page));
      runStep("Mi Negocio menu", page, () -> validateMiNegocioMenu(page));
      runStep("Agregar Negocio modal", page, () -> validateAgregarNegocioModal(page));
      runStep("Administrar Negocios view", page, () -> validateAdministrarNegociosView(page));
      runStep("Información General", page, () -> validateInformacionGeneral(page));
      runStep("Detalles de la Cuenta", page, () -> validateDetallesCuenta(page));
      runStep("Tus Negocios", page, () -> validateTusNegocios(page));
      runStep("Términos y Condiciones", page, () -> validateTerminosYCondiciones(page));
      runStep("Política de Privacidad", page, () -> validatePoliticaDePrivacidad(page));
    } finally {
      writeFinalReport();
      if (session != null) {
        session.close();
      }
    }

    assertTrue(
        "One or more SaleADS workflow validations failed: " + String.join(" | ", failures),
        failures.isEmpty());
  }

  private void validateLoginWithGoogle(final Page page) {
    final Locator signInWithGoogleButton =
        requireVisible(
            "Google login button",
            page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions()
                    .setName(
                        Pattern.compile(
                            "(?i)(sign\\s*in|iniciar\\s*sesi[oó]n|login|continuar).*(google)|google"))),
            page.getByText(Pattern.compile("(?i)sign\\s*in\\s*with\\s*google")),
            page.getByText(Pattern.compile("(?i)iniciar\\s*sesi[oó]n\\s*con\\s*google")),
            page.getByText(Pattern.compile("(?i)google")));
    clickAndWait(page, signInWithGoogleButton);

    final String googleAccount = env("SALEADS_GOOGLE_ACCOUNT", DEFAULT_GOOGLE_ACCOUNT);
    final Locator accountOption = page.getByText(googleAccount, new Page.GetByTextOptions().setExact(true));
    if (isVisible(accountOption, SHORT_TIMEOUT_MS)) {
      clickAndWait(page, accountOption.first());
    }

    requireVisible(
        "main application interface",
        page.locator("main"),
        page.locator("[data-testid='app-layout']"),
        page.getByRole(AriaRole.MAIN));
    requireVisible(
        "left sidebar navigation",
        page.locator("aside"),
        page.getByRole(AriaRole.NAVIGATION),
        page.getByText(Pattern.compile("(?i)^\\s*negocio\\s*$")));

    captureScreenshot(page, "dashboard-loaded.png", true);
  }

  private void validateMiNegocioMenu(final Page page) {
    final Locator negocio =
        requireVisible(
            "Negocio section",
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)negocio"))),
            page.getByText(Pattern.compile("(?i)^\\s*negocio\\s*$")));
    clickAndWait(page, negocio);

    final Locator miNegocio =
        requireVisible(
            "Mi Negocio menu item",
            page.getByRole(
                AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)mi\\s*negocio"))),
            page.getByText(Pattern.compile("(?i)^\\s*mi\\s*negocio\\s*$")));
    clickAndWait(page, miNegocio);

    requireVisible(
        "Agregar Negocio menu option",
        page.getByRole(
            AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)agregar\\s*negocio"))),
        page.getByText(Pattern.compile("(?i)^\\s*agregar\\s*negocio\\s*$")));
    requireVisible(
        "Administrar Negocios menu option",
        page.getByRole(
            AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName(Pattern.compile("(?i)administrar\\s*negocios"))),
        page.getByText(Pattern.compile("(?i)^\\s*administrar\\s*negocios\\s*$")));

    captureScreenshot(page, "mi-negocio-menu-expanded.png", false);
  }

  private void validateAgregarNegocioModal(final Page page) {
    final Locator agregarNegocio =
        requireVisible(
            "Agregar Negocio action",
            page.getByRole(
                AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)agregar\\s*negocio"))),
            page.getByText(Pattern.compile("(?i)^\\s*agregar\\s*negocio\\s*$")));
    clickAndWait(page, agregarNegocio);

    final Locator modalTitle =
        requireVisible(
            "Crear Nuevo Negocio modal title",
            page.getByRole(
                AriaRole.HEADING,
                new Page.GetByRoleOptions().setName(Pattern.compile("(?i)crear\\s*nuevo\\s*negocio"))),
            page.getByText(Pattern.compile("(?i)crear\\s*nuevo\\s*negocio")));
    modalTitle.waitFor(
        new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(DEFAULT_TIMEOUT_MS));

    requireVisible(
        "Nombre del Negocio field",
        page.getByLabel(Pattern.compile("(?i)nombre\\s*del\\s*negocio")),
        page.getByPlaceholder(Pattern.compile("(?i)nombre\\s*del\\s*negocio")),
        page.locator("[role='dialog'] input, .modal input, input[type='text']"));
    requireVisible(
        "Business plan count text",
        page.getByText(Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios")));
    requireVisible(
        "Cancelar button",
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cancelar"))),
        page.getByText(Pattern.compile("(?i)^\\s*cancelar\\s*$")));
    requireVisible(
        "Crear Negocio button",
        page.getByRole(
            AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)crear\\s*negocio"))),
        page.getByText(Pattern.compile("(?i)^\\s*crear\\s*negocio\\s*$")));

    captureScreenshot(page, "agregar-negocio-modal.png", false);

    final Locator nombreNegocioField =
        requireVisible(
            "Nombre del Negocio input for optional typing",
            page.getByLabel(Pattern.compile("(?i)nombre\\s*del\\s*negocio")),
            page.getByPlaceholder(Pattern.compile("(?i)nombre\\s*del\\s*negocio")),
            page.locator("input[type='text']"));
    nombreNegocioField.fill("Negocio Prueba Automatización");
    stabilizeUi(page);

    final Locator cancelar =
        requireVisible(
            "Cancelar button to close modal",
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cancelar"))),
            page.getByText(Pattern.compile("(?i)^\\s*cancelar\\s*$")));
    clickAndWait(page, cancelar);
  }

  private void validateAdministrarNegociosView(final Page page) {
    ensureMiNegocioExpanded(page);

    final Locator administrarNegocios =
        requireVisible(
            "Administrar Negocios option",
            page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Pattern.compile("(?i)administrar\\s*negocios"))),
            page.getByRole(
                AriaRole.LINK,
                new Page.GetByRoleOptions().setName(Pattern.compile("(?i)administrar\\s*negocios"))),
            page.getByText(Pattern.compile("(?i)^\\s*administrar\\s*negocios\\s*$")));
    clickAndWait(page, administrarNegocios);

    requireVisible(
        "Información General section",
        page.getByRole(
            AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)informaci[oó]n\\s*general"))),
        page.getByText(Pattern.compile("(?i)informaci[oó]n\\s*general")));
    requireVisible(
        "Detalles de la Cuenta section",
        page.getByRole(
            AriaRole.HEADING,
            new Page.GetByRoleOptions().setName(Pattern.compile("(?i)detalles\\s*de\\s*la\\s*cuenta"))),
        page.getByText(Pattern.compile("(?i)detalles\\s*de\\s*la\\s*cuenta")));
    requireVisible(
        "Tus Negocios section",
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)tus\\s*negocios"))),
        page.getByText(Pattern.compile("(?i)tus\\s*negocios")));
    requireVisible(
        "Sección Legal section",
        page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)secci[oó]n\\s*legal"))),
        page.getByText(Pattern.compile("(?i)secci[oó]n\\s*legal")));

    captureScreenshot(page, "administrar-negocios-account-page.png", true);
  }

  private void validateInformacionGeneral(final Page page) {
    requireVisible(
        "user name within Información General",
        page.getByText(Pattern.compile("(?i)nombre")),
        page.locator("h1, h2, h3, p, span").filter(new Locator.FilterOptions().setHasText(Pattern.compile(".+\\s.+"))));
    requireVisible(
        "user email within Información General",
        page.getByText(Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")));
    requireVisible("BUSINESS PLAN text", page.getByText(Pattern.compile("(?i)business\\s*plan")));
    requireVisible(
        "Cambiar Plan button",
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cambiar\\s*plan"))),
        page.getByText(Pattern.compile("(?i)^\\s*cambiar\\s*plan\\s*$")));
  }

  private void validateDetallesCuenta(final Page page) {
    requireVisible("Cuenta creada text", page.getByText(Pattern.compile("(?i)cuenta\\s*creada")));
    requireVisible("Estado activo text", page.getByText(Pattern.compile("(?i)estado\\s*activo")));
    requireVisible("Idioma seleccionado text", page.getByText(Pattern.compile("(?i)idioma\\s*seleccionado")));
  }

  private void validateTusNegocios(final Page page) {
    requireVisible("Tus Negocios title", page.getByText(Pattern.compile("(?i)tus\\s*negocios")));
    requireVisible(
        "Agregar Negocio button in business list",
        page.getByRole(
            AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)agregar\\s*negocio"))),
        page.getByText(Pattern.compile("(?i)^\\s*agregar\\s*negocio\\s*$")));
    requireVisible(
        "Tienes 2 de 3 negocios text", page.getByText(Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios")));
  }

  private void validateTerminosYCondiciones(final Page page) {
    validateLegalPage(
        page,
        "Términos y Condiciones",
        Pattern.compile("(?i)t[eé]rminos\\s*y\\s*condiciones"),
        "terminos-y-condiciones.png",
        "Términos y Condiciones");
  }

  private void validatePoliticaDePrivacidad(final Page page) {
    validateLegalPage(
        page,
        "Política de Privacidad",
        Pattern.compile("(?i)pol[ií]tica\\s*de\\s*privacidad"),
        "politica-de-privacidad.png",
        "Política de Privacidad");
  }

  private void validateLegalPage(
      final Page appPage,
      final String linkText,
      final Pattern headingPattern,
      final String screenshotFileName,
      final String reportUrlKey) {
    final String appUrlBeforeClick = appPage.url();
    final Locator legalLink =
        requireVisible(
            linkText + " link/button",
            appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + Pattern.quote(linkText)))),
            appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + Pattern.quote(linkText)))),
            appPage.getByText(linkText, new Page.GetByTextOptions().setExact(true)),
            appPage.getByText(Pattern.compile("(?i)" + Pattern.quote(linkText))));

    Page legalPage = null;
    boolean openedInPopup = false;

    try {
      legalPage =
          appPage.waitForPopup(
              new Page.WaitForPopupOptions().setTimeout(7_000),
              () -> legalLink.first().click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS)));
      openedInPopup = true;
    } catch (PlaywrightException ignored) {
      legalLink.first().click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
      legalPage = appPage;
    }

    stabilizeUi(legalPage);
    requireVisible(
        linkText + " heading",
        legalPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(headingPattern)),
        legalPage.getByText(headingPattern));
    requireVisible(
        linkText + " legal body content",
        legalPage.locator("main p"),
        legalPage.locator("article p"),
        legalPage.locator("p"));

    captureScreenshot(legalPage, screenshotFileName, true);
    capturedUrls.put(reportUrlKey, legalPage.url());

    if (openedInPopup) {
      legalPage.close();
      appPage.bringToFront();
      stabilizeUi(appPage);
      return;
    }

    try {
      appPage.goBack(new Page.GoBackOptions().setTimeout(DEFAULT_TIMEOUT_MS));
    } catch (PlaywrightException ignored) {
      if (appUrlBeforeClick != null && !appUrlBeforeClick.isEmpty()) {
        appPage.navigate(appUrlBeforeClick);
      }
    }
    stabilizeUi(appPage);
  }

  private void ensureMiNegocioExpanded(final Page page) {
    final Locator administrarNegociosVisibleNow =
        page.getByText(Pattern.compile("(?i)^\\s*administrar\\s*negocios\\s*$"));
    if (isVisible(administrarNegociosVisibleNow, SHORT_TIMEOUT_MS)) {
      return;
    }

    final Locator miNegocio =
        requireVisible(
            "Mi Negocio menu for re-expand",
            page.getByRole(
                AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)mi\\s*negocio"))),
            page.getByText(Pattern.compile("(?i)^\\s*mi\\s*negocio\\s*$")));
    clickAndWait(page, miNegocio);
  }

  private BrowserSession createBrowserSession() {
    final boolean headless = Boolean.parseBoolean(env("SALEADS_HEADLESS", "true"));
    final String browserProfileDir = env("SALEADS_BROWSER_PROFILE_DIR", "").trim();
    final Playwright playwright = Playwright.create();

    if (!browserProfileDir.isEmpty()) {
      final BrowserType.LaunchPersistentContextOptions persistentContextOptions =
          new BrowserType.LaunchPersistentContextOptions().setHeadless(headless);
      final BrowserContext context =
          playwright.chromium().launchPersistentContext(Paths.get(browserProfileDir), persistentContextOptions);
      final Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
      page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
      return new BrowserSession(playwright, null, context, page);
    }

    final Browser browser =
        playwright
            .chromium()
            .launch(new BrowserType.LaunchOptions().setHeadless(headless).setTimeout(DEFAULT_TIMEOUT_MS));
    final BrowserContext context = browser.newContext();
    final Page page = context.newPage();
    page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
    return new BrowserSession(playwright, browser, context, page);
  }

  private void runStep(final String stepKey, final Page page, final ThrowingRunnable stepAction) {
    try {
      stepAction.run();
      report.put(stepKey, "PASS");
    } catch (Throwable error) {
      report.put(stepKey, "FAIL");
      failures.add(stepKey + ": " + safeMessage(error));
      captureFailureArtifact(stepKey, page);
    }
  }

  private void initializeReport() {
    report.clear();
    report.put("Login", "FAIL");
    report.put("Mi Negocio menu", "FAIL");
    report.put("Agregar Negocio modal", "FAIL");
    report.put("Administrar Negocios view", "FAIL");
    report.put("Información General", "FAIL");
    report.put("Detalles de la Cuenta", "FAIL");
    report.put("Tus Negocios", "FAIL");
    report.put("Términos y Condiciones", "FAIL");
    report.put("Política de Privacidad", "FAIL");
  }

  private void writeFinalReport() throws IOException {
    final StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"generatedAt\": \"").append(escapeJson(OffsetDateTime.now().toString())).append("\",\n");
    json.append("  \"steps\": {\n");

    int stepIndex = 0;
    for (Map.Entry<String, String> step : report.entrySet()) {
      json.append("    \"").append(escapeJson(step.getKey())).append("\": \"").append(escapeJson(step.getValue())).append("\"");
      stepIndex++;
      json.append(stepIndex < report.size() ? ",\n" : "\n");
    }
    json.append("  },\n");

    json.append("  \"finalUrls\": {\n");
    int urlIndex = 0;
    for (Map.Entry<String, String> urlEntry : capturedUrls.entrySet()) {
      json
          .append("    \"")
          .append(escapeJson(urlEntry.getKey()))
          .append("\": \"")
          .append(escapeJson(urlEntry.getValue()))
          .append("\"");
      urlIndex++;
      json.append(urlIndex < capturedUrls.size() ? ",\n" : "\n");
    }
    json.append("  },\n");

    json.append("  \"screenshots\": {\n");
    int screenshotIndex = 0;
    for (Map.Entry<String, String> screenshot : screenshots.entrySet()) {
      json
          .append("    \"")
          .append(escapeJson(screenshot.getKey()))
          .append("\": \"")
          .append(escapeJson(screenshot.getValue()))
          .append("\"");
      screenshotIndex++;
      json.append(screenshotIndex < screenshots.size() ? ",\n" : "\n");
    }
    json.append("  },\n");

    json.append("  \"failures\": [\n");
    for (int i = 0; i < failures.size(); i++) {
      json.append("    \"").append(escapeJson(failures.get(i))).append("\"");
      json.append(i < failures.size() - 1 ? ",\n" : "\n");
    }
    json.append("  ]\n");
    json.append("}\n");

    Files.writeString(EVIDENCE_DIR.resolve(FINAL_REPORT_PATH), json.toString(), StandardCharsets.UTF_8);
  }

  private void captureFailureArtifact(final String stepKey, final Page page) {
    // Best effort: a screenshot may fail if no stable page is available.
    try {
      final String fileName = sanitizeFileName(stepKey) + "-failure.png";
      final Path screenshotPath = EVIDENCE_DIR.resolve(fileName);
      page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(true));
      screenshots.put(stepKey + " (failure)", screenshotPath.toString());
    } catch (Exception ignored) {
      // Ignore reporting helper issues to keep the main failure intact.
    }
  }

  private void clickAndWait(final Page page, final Locator locator) {
    locator.first().scrollIntoViewIfNeeded();
    locator.first().click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
    stabilizeUi(page);
  }

  private void stabilizeUi(final Page page) {
    try {
      page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
    } catch (PlaywrightException ignored) {
      // Some SPA transitions do not trigger a full document load event.
    }
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(SHORT_TIMEOUT_MS));
    } catch (PlaywrightException ignored) {
      // Network idle can remain busy on analytics websockets; this is expected.
    }
    page.waitForTimeout(500);
  }

  private void captureScreenshot(final Page page, final String fileName, final boolean fullPage) {
    final Path screenshotPath = EVIDENCE_DIR.resolve(fileName);
    page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
    screenshots.put(fileName, screenshotPath.toString());
  }

  private Locator requireVisible(final String description, final Locator... candidates) {
    for (Locator candidate : candidates) {
      final Locator current = candidate.first();
      try {
        current.waitFor(
            new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(SHORT_TIMEOUT_MS));
        return current;
      } catch (PlaywrightException ignored) {
        // Keep trying alternate visible-text selectors.
      }
    }
    throw new AssertionError("Could not locate visible element: " + description);
  }

  private boolean isVisible(final Locator locator, final int timeoutMs) {
    try {
      locator
          .first()
          .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
      return true;
    } catch (PlaywrightException ignored) {
      return false;
    }
  }

  private String env(final String key, final String fallback) {
    final String value = System.getenv(key);
    return value == null ? fallback : value;
  }

  private String safeMessage(final Throwable throwable) {
    final String message = throwable.getMessage();
    if (message == null || message.trim().isEmpty()) {
      return throwable.getClass().getSimpleName();
    }
    return message.replace("\n", " ").trim();
  }

  private String sanitizeFileName(final String value) {
    return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
  }

  private String escapeJson(final String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\b", "\\b")
        .replace("\f", "\\f")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run();
  }

  private static final class BrowserSession {
    private final Playwright playwright;
    private final Browser browser;
    private final BrowserContext context;
    private final Page page;

    private BrowserSession(
        final Playwright playwright, final Browser browser, final BrowserContext context, final Page page) {
      this.playwright = playwright;
      this.browser = browser;
      this.context = context;
      this.page = page;
    }

    private void close() {
      try {
        context.close();
      } finally {
        if (browser != null) {
          browser.close();
        }
        playwright.close();
      }
    }
  }
}
