package io.proleap.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;

/**
 * End-to-end validation for SaleADS Mi Negocio workflow.
 *
 * <p>Execution is environment-agnostic and requires explicit configuration via environment variables:
 *
 * <ul>
 *   <li>SALEADS_BASE_URL: Environment URL (optional but recommended)
 *   <li>SALEADS_HEADLESS: true|false (default false)
 *   <li>SALEADS_GOOGLE_EMAIL: account to select when Google chooser appears
 *   <li>SALEADS_EVIDENCE_DIR: where screenshots/report are stored
 * </ul>
 */
public class SaleadsMiNegocioWorkflowTest {

  private static final String DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
  private static final String DEFAULT_EVIDENCE_DIR = "target/e2e-evidence/saleads-mi-negocio";
  private static final int DEFAULT_TIMEOUT_MS = 30000;
  private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

  @Test
  public void saleadsMiNegocioFullWorkflow() throws Exception {
    final String baseUrl = envOrNull("SALEADS_BASE_URL");
    assumeTrue(
        "Set SALEADS_BASE_URL to the SaleADS login page of your target environment.",
        baseUrl != null && !baseUrl.isBlank());

    final String googleEmail = env("SALEADS_GOOGLE_EMAIL", DEFAULT_GOOGLE_EMAIL);
    final boolean headless = Boolean.parseBoolean(env("SALEADS_HEADLESS", "false"));
    final Path evidenceDir = prepareEvidenceDir(env("SALEADS_EVIDENCE_DIR", DEFAULT_EVIDENCE_DIR));

    final WorkflowReport report = new WorkflowReport();
    report.startStep("Login");
    report.startStep("Mi Negocio menu");
    report.startStep("Agregar Negocio modal");
    report.startStep("Administrar Negocios view");
    report.startStep("Información General");
    report.startStep("Detalles de la Cuenta");
    report.startStep("Tus Negocios");
    report.startStep("Términos y Condiciones");
    report.startStep("Política de Privacidad");

    try (Playwright playwright = Playwright.create()) {
      final BrowserType.LaunchOptions launchOptions =
          new BrowserType.LaunchOptions().setHeadless(headless);
      try (Browser browser = playwright.chromium().launch(launchOptions)) {
        final BrowserContext context = browser.newContext();
        final Page page = context.newPage();
        page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);

        navigateToLogin(page, baseUrl);
        performGoogleLogin(page, context, googleEmail);
        validateMainAppLoaded(page);
        screenshot(page, evidenceDir.resolve("01-dashboard-loaded.png"), true);
        report.pass("Login");

        openMiNegocioMenu(page);
        validateMiNegocioExpanded(page);
        screenshot(page, evidenceDir.resolve("02-mi-negocio-menu-expanded.png"), false);
        report.pass("Mi Negocio menu");

        validateAgregarNegocioModal(page, evidenceDir);
        report.pass("Agregar Negocio modal");

        openAdministrarNegocios(page);
        validateAdministrarNegociosView(page);
        screenshot(page, evidenceDir.resolve("04-administrar-negocios-full-page.png"), true);
        report.pass("Administrar Negocios view");

        validateInformacionGeneral(page);
        report.pass("Información General");

        validateDetallesCuenta(page);
        report.pass("Detalles de la Cuenta");

        validateTusNegocios(page);
        report.pass("Tus Negocios");

        validateLegalLink(page, context, "Términos y Condiciones", evidenceDir, report);
        validateLegalLink(page, context, "Política de Privacidad", evidenceDir, report);
      }
    } catch (Exception ex) {
      report.markFirstPendingAsFailed(ex);
      throw ex;
    } finally {
      report.writeTo(evidenceDir.resolve("final-report.txt"));
      assertTrue("Some workflow checks failed. See final-report.txt", report.allPassed());
    }
  }

  private static void navigateToLogin(final Page page, final String baseUrl) {
    if (baseUrl == null || baseUrl.isBlank()) {
      fail("SALEADS_BASE_URL is required and must point to the current environment login page.");
    }
    page.navigate(baseUrl);
    waitForUiSettled(page);
  }

  private static void performGoogleLogin(
      final Page page, final BrowserContext context, final String googleEmail) {
    // Google flow can happen on same page or popup; handle both.
    final Optional<Page> chooserPopup =
        waitForPageOpenIfPresent(context, () -> clickByTextOrRole(page, "Sign in with Google", "Google"));
    waitForUiSettled(page);
    if (chooserPopup.isPresent()) {
      final Page googlePage = chooserPopup.get();
      googlePage.bringToFront();
      selectGoogleAccountIfVisible(googlePage, googleEmail);
    } else {
      selectGoogleAccountIfVisible(page, googleEmail);
    }
  }

  private static Optional<Page> waitForPageOpenIfPresent(
      final BrowserContext context, final Runnable action) {
    try {
      final Page newPage =
          context.waitForPage(
              action,
              new BrowserContext.WaitForPageOptions()
                  .setTimeout(5000)
                  .setPredicate(candidate -> candidate != null));
      return Optional.ofNullable(newPage);
    } catch (RuntimeException ignored) {
      // No new page within timeout; flow likely continued in the same page.
    }
    return Optional.empty();
  }

  private static void selectGoogleAccountIfVisible(final Page page, final String googleEmail) {
    waitForUiSettled(page);
    final Locator accountByText = page.getByText(googleEmail);
    if (accountByText.count() > 0) {
      accountByText.first().click();
      waitForUiSettled(page);
      return;
    }

    final Locator useAnotherAccount = page.getByText("Use another account");
    if (useAnotherAccount.count() > 0) {
      fail("Google account chooser did not include expected account: " + googleEmail);
    }
  }

  private static void validateMainAppLoaded(final Page page) {
    assertTrue("Expected app interface after login", hasAnyVisible(page, "Dashboard", "Negocio", "Mi Negocio"));
    assertTrue("Expected left sidebar navigation", isSidebarVisible(page));
  }

  private static void openMiNegocioMenu(final Page page) {
    clickByText(page, "Negocio");
    waitForUiSettled(page);
    clickByText(page, "Mi Negocio");
    waitForUiSettled(page);
  }

  private static void validateMiNegocioExpanded(final Page page) {
    assertTrue("Expected Agregar Negocio in expanded menu", visibleByText(page, "Agregar Negocio"));
    assertTrue(
        "Expected Administrar Negocios in expanded menu", visibleByText(page, "Administrar Negocios"));
  }

  private static void validateAgregarNegocioModal(final Page page, final Path evidenceDir) {
    clickByText(page, "Agregar Negocio");
    waitForUiSettled(page);

    assertTrue("Expected modal title", visibleByText(page, "Crear Nuevo Negocio"));
    assertTrue("Expected Nombre del Negocio input", visibleByLabelOrPlaceholder(page, "Nombre del Negocio"));
    assertTrue("Expected business quota text", visibleByText(page, "Tienes 2 de 3 negocios"));
    assertTrue("Expected Cancelar button", visibleButton(page, "Cancelar"));
    assertTrue("Expected Crear Negocio button", visibleButton(page, "Crear Negocio"));

    screenshot(page, evidenceDir.resolve("03-agregar-negocio-modal.png"), false);

    final Locator input = firstVisibleByLabelOrPlaceholder(page, "Nombre del Negocio");
    if (input != null) {
      input.click();
      input.fill("Negocio Prueba Automatización");
    }
    clickByTextOrRole(page, "Cancelar", "Cancelar");
    waitForUiSettled(page);
  }

  private static void openAdministrarNegocios(final Page page) {
    if (!visibleByText(page, "Administrar Negocios")) {
      clickByText(page, "Mi Negocio");
      waitForUiSettled(page);
    }
    clickByText(page, "Administrar Negocios");
    waitForUiSettled(page);
  }

  private static void validateAdministrarNegociosView(final Page page) {
    assertTrue("Expected Información General section", visibleByText(page, "Información General"));
    assertTrue("Expected Detalles de la Cuenta section", visibleByText(page, "Detalles de la Cuenta"));
    assertTrue("Expected Tus Negocios section", visibleByText(page, "Tus Negocios"));
    assertTrue("Expected Sección Legal section", visibleByText(page, "Sección Legal"));
  }

  private static void validateInformacionGeneral(final Page page) {
    assertTrue("Expected user name visible", hasAnyVisible(page, "Juan", "Perfil", "Nombre"));
    assertTrue("Expected user email visible", visibleByTextContains(page, "@"));
    assertTrue("Expected BUSINESS PLAN text", visibleByText(page, "BUSINESS PLAN"));
    assertTrue("Expected Cambiar Plan button", visibleButton(page, "Cambiar Plan"));
  }

  private static void validateDetallesCuenta(final Page page) {
    assertTrue("Expected Cuenta creada", visibleByText(page, "Cuenta creada"));
    assertTrue("Expected Estado activo", visibleByText(page, "Estado activo"));
    assertTrue("Expected Idioma seleccionado", visibleByText(page, "Idioma seleccionado"));
  }

  private static void validateTusNegocios(final Page page) {
    assertTrue("Expected Tus Negocios section", visibleByText(page, "Tus Negocios"));
    assertTrue("Expected Agregar Negocio button", visibleByText(page, "Agregar Negocio"));
    assertTrue("Expected business quota text", visibleByText(page, "Tienes 2 de 3 negocios"));
  }

  private static void validateLegalLink(
      final Page appPage,
      final BrowserContext context,
      final String linkText,
      final Path evidenceDir,
      final WorkflowReport report) {
    final String reportKey = linkText;
    final String appUrlBefore = appPage.url();
    Page legalPage = appPage;
    final Optional<Page> maybeNewTab = waitForPageOpenIfPresent(context, () -> clickByText(appPage, linkText));
    waitForUiSettled(appPage);
    if (maybeNewTab.isPresent()) {
      legalPage = maybeNewTab.get();
      legalPage.bringToFront();
      waitForUiSettled(legalPage);
    }

    assertTrue("Expected legal heading: " + linkText, visibleByText(legalPage, linkText));
    assertTrue("Expected legal content text for: " + linkText, hasLegalBodyText(legalPage));

    final String slug = slugify(linkText);
    screenshot(legalPage, evidenceDir.resolve("legal-" + slug + ".png"), true);
    Files.writeString(evidenceDir.resolve("legal-" + slug + "-url.txt"), legalPage.url());

    if (legalPage != appPage && !legalPage.isClosed()) {
      legalPage.close();
      appPage.bringToFront();
      waitForUiSettled(appPage);
    } else if (legalPage == appPage && !appUrlBefore.equals(appPage.url())) {
      appPage.goBack();
      waitForUiSettled(appPage);
    }

    report.pass(reportKey);
  }

  private static boolean hasLegalBodyText(final Page page) {
    final String content = page.content();
    final String normalized = content.toLowerCase(Locale.ROOT);
    return normalized.contains("terminos")
        || normalized.contains("términos")
        || normalized.contains("privacidad")
        || normalized.contains("condiciones");
  }

  private static boolean isSidebarVisible(final Page page) {
    final Locator nav = page.locator("aside, nav");
    if (nav.count() > 0) {
      for (int i = 0; i < nav.count(); i++) {
        if (nav.nth(i).isVisible()) {
          return true;
        }
      }
    }
    return false;
  }

  private static void clickByTextOrRole(final Page page, final String text, final String roleName) {
    final Locator byRoleButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(roleName));
    if (byRoleButton.count() > 0 && byRoleButton.first().isVisible()) {
      byRoleButton.first().click();
      return;
    }
    clickByText(page, text);
  }

  private static void clickByText(final Page page, final String text) {
    final Locator exact = page.getByText(text, new Page.GetByTextOptions().setExact(true));
    if (exact.count() > 0 && exact.first().isVisible()) {
      exact.first().click();
      return;
    }

    final Locator partial = page.getByText(text);
    if (partial.count() > 0 && partial.first().isVisible()) {
      partial.first().click();
      return;
    }

    fail("Could not click element by visible text: " + text);
  }

  private static boolean visibleByText(final Page page, final String text) {
    final Locator exact = page.getByText(text, new Page.GetByTextOptions().setExact(true));
    if (exact.count() > 0 && exact.first().isVisible()) {
      return true;
    }
    final Locator partial = page.getByText(text);
    return partial.count() > 0 && partial.first().isVisible();
  }

  private static boolean visibleByTextContains(final Page page, final String text) {
    final Locator partial = page.getByText(text);
    return partial.count() > 0 && partial.first().isVisible();
  }

  private static boolean hasAnyVisible(final Page page, final String... candidates) {
    for (final String candidate : candidates) {
      if (visibleByText(page, candidate)) {
        return true;
      }
    }
    return false;
  }

  private static boolean visibleByLabelOrPlaceholder(final Page page, final String label) {
    final Locator byLabel = page.getByLabel(label);
    if (byLabel.count() > 0 && byLabel.first().isVisible()) {
      return true;
    }

    final Locator byPlaceholder = page.getByPlaceholder(label);
    return byPlaceholder.count() > 0 && byPlaceholder.first().isVisible();
  }

  private static Locator firstVisibleByLabelOrPlaceholder(final Page page, final String label) {
    final Locator byLabel = page.getByLabel(label);
    if (byLabel.count() > 0 && byLabel.first().isVisible()) {
      return byLabel.first();
    }
    final Locator byPlaceholder = page.getByPlaceholder(label);
    if (byPlaceholder.count() > 0 && byPlaceholder.first().isVisible()) {
      return byPlaceholder.first();
    }
    return null;
  }

  private static boolean visibleButton(final Page page, final String name) {
    final Locator byRole = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name));
    if (byRole.count() > 0 && byRole.first().isVisible()) {
      return true;
    }
    return visibleByText(page, name);
  }

  private static void waitForUiSettled(final Page page) {
    page.waitForLoadState();
    try {
      page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
    } catch (RuntimeException ignored) {
      // Some SPAs keep active network connections; fallback is enough.
    }
    page.waitForTimeout(800);
  }

  private static void screenshot(final Page page, final Path path, final boolean fullPage) {
    page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
  }

  private static Path prepareEvidenceDir(final String rootDir) throws Exception {
    final String timestamp = LocalDateTime.now().format(TS);
    final Path base = Paths.get(rootDir, timestamp);
    Files.createDirectories(base);
    return base;
  }

  private static String env(final String key, final String fallback) {
    final String value = System.getenv(key);
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value.trim();
  }

  private static String envOrNull(final String key) {
    final String value = System.getenv(key);
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private static String slugify(final String text) {
    return text
        .toLowerCase(Locale.ROOT)
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace("ñ", "n")
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("^-+|-+$", "");
  }

  private static final class WorkflowReport {
    private final Map<String, String> statuses = new LinkedHashMap<>();
    private final List<String> details = new ArrayList<>();

    void startStep(final String step) {
      statuses.put(step, "FAIL");
    }

    void pass(final String step) {
      statuses.put(step, "PASS");
    }

    void markFirstPendingAsFailed(final Exception ex) {
      details.add("Failure: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
    }

    boolean allPassed() {
      return statuses.values().stream().allMatch("PASS"::equals);
    }

    void writeTo(final Path path) throws Exception {
      final StringBuilder sb = new StringBuilder();
      sb.append("SaleADS Mi Negocio Workflow - Final Report").append(System.lineSeparator());
      sb.append("=========================================").append(System.lineSeparator());
      for (Map.Entry<String, String> entry : statuses.entrySet()) {
        sb.append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
      }
      if (!details.isEmpty()) {
        sb.append(System.lineSeparator()).append("Details").append(System.lineSeparator());
        sb.append("-------").append(System.lineSeparator());
        for (String line : details) {
          sb.append(line).append(System.lineSeparator());
        }
      }
      Files.writeString(path, sb.toString());
    }
  }
}
