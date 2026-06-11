package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
 * Manual-capable E2E workflow for SaleADS "Mi Negocio".
 *
 * Runtime parameters:
 * -Dsaleads.startUrl=<current-environment-login-url> (or SALEADS_START_URL)
 * -Dsaleads.googleEmail=<google-email> (or SALEADS_GOOGLE_EMAIL)
 * -Dsaleads.headless=true|false (or SALEADS_HEADLESS, default true)
 * -Dsaleads.screenshotDir=<output-dir> (or SALEADS_SCREENSHOT_DIR)
 */
public class SaleadsMiNegocioFullWorkflowIT {

  private static final long DEFAULT_TIMEOUT_MS = 30000;
  private static final String DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
  private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
  private static final Pattern EMAIL_PATTERN = Pattern
      .compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", Pattern.CASE_INSENSITIVE);

  private final Map<String, StepResult> report = new LinkedHashMap<>();

  private BrowserContext context;
  private Page appPage;
  private Path screenshotDir;
  private int screenshotCounter;

  @Test
  public void saleadsMiNegocioWorkflow() throws Exception {
    final String startUrl = firstNonBlank(System.getProperty("saleads.startUrl"), System.getenv("SALEADS_START_URL"));
    Assume.assumeTrue("Set saleads.startUrl or SALEADS_START_URL to run this E2E workflow test.",
        startUrl != null && !startUrl.isBlank());

    final String googleEmail = firstNonBlank(System.getProperty("saleads.googleEmail"),
        System.getenv("SALEADS_GOOGLE_EMAIL"), DEFAULT_GOOGLE_EMAIL);
    final boolean headless = Boolean.parseBoolean(
        firstNonBlank(System.getProperty("saleads.headless"), System.getenv("SALEADS_HEADLESS"), "true"));
    final String screenshotRoot = firstNonBlank(System.getProperty("saleads.screenshotDir"),
        System.getenv("SALEADS_SCREENSHOT_DIR"), "target/saleads-e2e-screenshots");

    screenshotDir = createScreenshotDir(screenshotRoot);

    try (Playwright playwright = Playwright.create()) {
      final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
      try {
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1920, 1080));
        context.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
        context.setDefaultNavigationTimeout(DEFAULT_TIMEOUT_MS);
        appPage = context.newPage();
        appPage.navigate(startUrl, new Page.NavigateOptions().setWaitUntil(LoadState.DOMCONTENTLOADED));
        waitForUiLoad(appPage);

        runStep("Login", () -> {
          stepLoginWithGoogle(googleEmail);
          return "Dashboard and sidebar detected.";
        });
        runStep("Mi Negocio menu", () -> {
          stepOpenMiNegocioMenu();
          return "Mi Negocio menu expanded with expected options.";
        });
        runStep("Agregar Negocio modal", () -> {
          stepValidateAgregarNegocioModal();
          return "Crear Nuevo Negocio modal validated.";
        });
        runStep("Administrar Negocios view", () -> {
          stepOpenAdministrarNegocios();
          return "Account page sections validated.";
        });
        runStep("Información General", () -> {
          stepValidateInformacionGeneral();
          return "Name/email/plan/change-plan controls visible.";
        });
        runStep("Detalles de la Cuenta", () -> {
          stepValidateDetallesCuenta();
          return "Account details labels visible.";
        });
        runStep("Tus Negocios", () -> {
          stepValidateTusNegocios();
          return "Business section validated.";
        });
        runStep("Términos y Condiciones", () -> {
          final String finalUrl = stepValidateLegalDocument("Términos y Condiciones", "terminos-y-condiciones");
          return "Final URL: " + finalUrl;
        });
        runStep("Política de Privacidad", () -> {
          final String finalUrl = stepValidateLegalDocument("Política de Privacidad", "politica-de-privacidad");
          return "Final URL: " + finalUrl;
        });
      } finally {
        browser.close();
      }
    }

    final String summary = formatSummary();
    System.out.println(summary);
    assertTrue("One or more validations failed:\n" + summary, allStepsPassed());
  }

  private void stepLoginWithGoogle(final String googleEmail) {
    final Pattern loginPattern = Pattern.compile(
        "(?i)(sign\\s*in\\s*with\\s*google|continue\\s*with\\s*google|iniciar\\s*sesi[oó]n\\s*con\\s*google|continuar\\s*con\\s*google|google)");
    final Locator loginButton = requireVisible(firstVisibleCandidates(appPage,
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(loginPattern)),
        appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(loginPattern)),
        appPage.getByText(loginPattern)), "Google login button");

    Page popup = null;
    try {
      popup = appPage.waitForPopup(() -> loginButton.click(),
          new Page.WaitForPopupOptions().setTimeout(9000));
    } catch (PlaywrightException ignored) {
      loginButton.click();
    }
    waitForUiLoad(appPage);

    final Page authPage = popup != null ? popup : appPage;
    selectGoogleAccountIfShown(authPage, googleEmail);

    waitForAppInterface();
    captureScreenshot(appPage, "dashboard-loaded", false);
  }

  private void stepOpenMiNegocioMenu() {
    final Locator negocioSection = requireVisible(findByText(appPage, "Negocio"), "Negocio section");
    clickAndWait(negocioSection, appPage);

    final Locator miNegocio = requireVisible(findByText(appPage, "Mi Negocio"), "Mi Negocio menu");
    clickAndWait(miNegocio, appPage);

    requireVisible(findByText(appPage, "Agregar Negocio"), "Agregar Negocio option");
    requireVisible(findByText(appPage, "Administrar Negocios"), "Administrar Negocios option");
    captureScreenshot(appPage, "mi-negocio-expanded-menu", false);
  }

  private void stepValidateAgregarNegocioModal() {
    final Locator agregarNegocioMenu = requireVisible(findByText(appPage, "Agregar Negocio"), "Agregar Negocio");
    clickAndWait(agregarNegocioMenu, appPage);

    requireVisible(findByText(appPage, "Crear Nuevo Negocio"), "Crear Nuevo Negocio modal title");
    final Locator nombreInput = requireVisible(firstVisibleCandidates(appPage,
        appPage.getByLabel(Pattern.compile("(?i)Nombre\\s+del\\s+Negocio")),
        appPage.getByPlaceholder(Pattern.compile("(?i)Nombre\\s+del\\s+Negocio"))), "Nombre del Negocio input");
    requireVisible(findByText(appPage, "Tienes 2 de 3 negocios"), "Tienes 2 de 3 negocios text");
    final Locator cancelar = requireVisible(findByText(appPage, "Cancelar"), "Cancelar button");
    requireVisible(findByText(appPage, "Crear Negocio"), "Crear Negocio button");
    captureScreenshot(appPage, "agregar-negocio-modal", false);

    // Optional interaction requested in workflow definition.
    nombreInput.click();
    nombreInput.fill("Negocio Prueba Automatización");
    clickAndWait(cancelar, appPage);
  }

  private void stepOpenAdministrarNegocios() {
    ensureMiNegocioExpanded();

    final Locator administrar = requireVisible(findByText(appPage, "Administrar Negocios"), "Administrar Negocios");
    clickAndWait(administrar, appPage);

    requireVisible(findByText(appPage, "Información General"), "Información General section");
    requireVisible(findByText(appPage, "Detalles de la Cuenta"), "Detalles de la Cuenta section");
    requireVisible(findByText(appPage, "Tus Negocios"), "Tus Negocios section");
    requireVisible(findByText(appPage, "Sección Legal"), "Sección Legal section");
    captureScreenshot(appPage, "administrar-negocios-page", true);
  }

  private void stepValidateInformacionGeneral() {
    final Locator accountEmail = requireVisible(firstVisibleCandidates(appPage, appPage.getByText(EMAIL_PATTERN)),
        "user email");
    final String email = accountEmail.innerText().trim();
    assertTrue("Expected a valid user email in Información General but found: " + email, EMAIL_PATTERN.matcher(email).find());

    assertTrue("Expected a user name value to be visible.",
        isLikelyUserNameVisible(findSectionContainer("Información General").orElse(appPage.locator("body"))));
    requireVisible(findByText(appPage, "BUSINESS PLAN"), "BUSINESS PLAN text");
    requireVisible(findByText(appPage, "Cambiar Plan"), "Cambiar Plan button");
  }

  private void stepValidateDetallesCuenta() {
    requireVisible(findByText(appPage, "Cuenta creada"), "Cuenta creada label");
    requireVisible(findByText(appPage, "Estado activo"), "Estado activo label");
    requireVisible(findByText(appPage, "Idioma seleccionado"), "Idioma seleccionado label");
  }

  private void stepValidateTusNegocios() {
    final Locator section = requireVisible(findByText(appPage, "Tus Negocios"), "Tus Negocios section");
    requireVisible(findByText(appPage, "Agregar Negocio"), "Agregar Negocio button");
    requireVisible(findByText(appPage, "Tienes 2 de 3 negocios"), "Tienes 2 de 3 negocios text");

    final Locator listCandidates = section.locator("xpath=ancestor::*[self::section or self::article or self::div][1]")
        .locator("li, tr, [role='row'], [data-testid*='business'], [class*='business']");
    assertTrue("Expected business list content in 'Tus Negocios'.", safeCount(listCandidates) > 0);
  }

  private String stepValidateLegalDocument(final String linkText, final String screenshotName) {
    final Locator link = requireVisible(findByText(appPage, linkText), linkText + " link");
    link.scrollIntoViewIfNeeded();

    Page legalPage = null;
    try {
      legalPage = context.waitForPage(() -> link.click(), new BrowserContext.WaitForPageOptions().setTimeout(9000));
    } catch (PlaywrightException ignored) {
      link.click();
    }

    final Page activePage = legalPage != null ? legalPage : appPage;
    waitForUiLoad(activePage);
    requireVisible(findByText(activePage, linkText), linkText + " heading");

    final String legalText = activePage.locator("body").innerText().trim();
    assertTrue("Expected legal content text for " + linkText + ".", legalText.length() > 200);

    captureScreenshot(activePage, screenshotName, true);
    final String finalUrl = activePage.url();

    if (legalPage != null) {
      legalPage.close();
      appPage.bringToFront();
      waitForUiLoad(appPage);
    } else {
      activePage.goBack(new Page.GoBackOptions().setWaitUntil(LoadState.DOMCONTENTLOADED));
      waitForUiLoad(appPage);
    }

    return finalUrl;
  }

  private void ensureMiNegocioExpanded() {
    if (isVisible(findByText(appPage, "Administrar Negocios"))) {
      return;
    }

    final Locator menu = requireVisible(findByText(appPage, "Mi Negocio"), "Mi Negocio menu");
    clickAndWait(menu, appPage);
  }

  private void selectGoogleAccountIfShown(final Page authPage, final String googleEmail) {
    waitForUiLoad(authPage);

    final Locator emailOption = firstVisibleCandidates(authPage,
        authPage.getByText(Pattern.compile("(?i)^\\s*" + Pattern.quote(googleEmail) + "\\s*$")),
        authPage.locator("[data-email='" + googleEmail + "']"));

    if (!isVisible(emailOption)) {
      return;
    }

    clickAndWait(requireVisible(emailOption, "Google account selector for " + googleEmail), authPage);
  }

  private void waitForAppInterface() {
    final Pattern sidebarPattern = Pattern.compile("(?i)(Negocio|Mi\\s+Negocio)");
    requireVisible(firstVisibleCandidates(appPage, appPage.getByText(sidebarPattern)), "main app sidebar");
  }

  private Optional<Locator> findSectionContainer(final String sectionTitle) {
    final Locator heading = findByText(appPage, sectionTitle);
    if (!isVisible(heading)) {
      return Optional.empty();
    }

    final Locator container = heading.first().locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
    return Optional.of(container);
  }

  private boolean isLikelyUserNameVisible(final Locator scope) {
    final List<String> disallowed = Arrays.asList("INFORMACIÓN GENERAL", "BUSINESS PLAN", "CAMBIAR PLAN", "DETALLES DE LA CUENTA",
        "CUENTA CREADA", "ESTADO ACTIVO", "IDIOMA SELECCIONADO");
    final List<String> texts = scope.locator("h1, h2, h3, h4, p, span, strong, div").allInnerTexts();

    for (final String text : texts) {
      final String normalized = text.trim();
      final String upper = normalized.toUpperCase(Locale.ROOT);
      if (normalized.isBlank() || EMAIL_PATTERN.matcher(normalized).find() || disallowed.contains(upper)) {
        continue;
      }

      final boolean looksLikeName = normalized.matches(".*[A-Za-zÀ-ÿ].*") && normalized.length() >= 3
          && !normalized.contains("http") && !normalized.matches(".*\\d{3,}.*");
      if (looksLikeName) {
        return true;
      }
    }

    return false;
  }

  private void runStep(final String stepName, final StepAction stepAction) {
    try {
      final String detail = firstNonBlank(stepAction.run(), "Validated successfully.");
      report.put(stepName, StepResult.pass(detail));
    } catch (Throwable throwable) {
      captureFailureScreenshot(stepName);
      final String detail = firstNonBlank(throwable.getMessage(), throwable.getClass().getSimpleName());
      report.put(stepName, StepResult.fail(detail));
    }
  }

  private void captureFailureScreenshot(final String stepName) {
    if (appPage == null || screenshotDir == null) {
      return;
    }

    try {
      captureScreenshot(appPage, "failure-" + stepName.toLowerCase(Locale.ROOT).replace(' ', '-'), true);
    } catch (Throwable ignored) {
      // Best effort evidence capture.
    }
  }

  private String formatSummary() {
    final StringBuilder builder = new StringBuilder();
    builder.append("SaleADS Mi Negocio workflow report\n");
    builder.append("Screenshot directory: ").append(screenshotDir.toAbsolutePath()).append('\n');

    for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
      final StepResult result = entry.getValue();
      builder.append("- ").append(entry.getKey()).append(": ").append(result.passed ? "PASS" : "FAIL");
      builder.append(" | ").append(result.detail).append('\n');
    }

    return builder.toString();
  }

  private boolean allStepsPassed() {
    return report.values().stream().allMatch(step -> step.passed);
  }

  private void clickAndWait(final Locator locator, final Page page) {
    locator.scrollIntoViewIfNeeded();
    locator.click();
    waitForUiLoad(page);
  }

  private void waitForUiLoad(final Page page) {
    page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
    } catch (PlaywrightException ignored) {
      // Some routes keep polling in background; DOMContentLoaded remains sufficient.
    }
    page.waitForTimeout(450);
  }

  private Locator findByText(final Page page, final String text) {
    final Pattern exact = Pattern.compile("(?i)^\\s*" + Pattern.quote(text) + "\\s*$");
    final Pattern contains = Pattern.compile("(?i)" + Pattern.quote(text));
    return firstVisibleCandidates(page, page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(exact)),
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(exact)),
        page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(exact)),
        page.getByText(exact), page.getByText(contains));
  }

  private Locator firstVisibleCandidates(final Page page, final Locator... candidates) {
    for (final Locator candidate : candidates) {
      if (candidate == null) {
        continue;
      }

      final int count = Math.min(safeCount(candidate), 10);
      for (int i = 0; i < count; i++) {
        final Locator option = candidate.nth(i);
        if (isVisible(option)) {
          return option;
        }
      }
    }

    if (candidates.length == 0) {
      throw new IllegalArgumentException("At least one locator candidate is required.");
    }

    return candidates[0].first();
  }

  private Locator requireVisible(final Locator locator, final String description) {
    try {
      locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));
      return locator.first();
    } catch (PlaywrightException exception) {
      throw new AssertionError("Expected visible element not found: " + description, exception);
    }
  }

  private boolean isVisible(final Locator locator) {
    if (locator == null) {
      return false;
    }

    try {
      return locator.first().isVisible(new Locator.IsVisibleOptions().setTimeout(800));
    } catch (PlaywrightException ignored) {
      return false;
    }
  }

  private int safeCount(final Locator locator) {
    try {
      return locator.count();
    } catch (PlaywrightException ignored) {
      return 0;
    }
  }

  private void captureScreenshot(final Page page, final String checkpointName, final boolean fullPage) {
    final String fileName = String.format("%02d-%s.png", ++screenshotCounter, sanitizeFileName(checkpointName));
    final Path screenshotPath = screenshotDir.resolve(fileName);
    page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
  }

  private Path createScreenshotDir(final String rootPath) throws Exception {
    final Path directory = Paths.get(rootPath).resolve(LocalDateTime.now().format(TIMESTAMP_FORMAT));
    Files.createDirectories(directory);
    return directory;
  }

  private String sanitizeFileName(final String value) {
    return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]+", "-").replaceAll("^-+|-+$", "");
  }

  private String firstNonBlank(final String... values) {
    for (final String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  @FunctionalInterface
  private interface StepAction {
    String run() throws Exception;
  }

  private static class StepResult {
    private final boolean passed;
    private final String detail;

    private StepResult(final boolean passed, final String detail) {
      this.passed = passed;
      this.detail = detail;
    }

    private static StepResult pass(final String detail) {
      return new StepResult(true, detail);
    }

    private static StepResult fail(final String detail) {
      return new StepResult(false, detail);
    }
  }
}
