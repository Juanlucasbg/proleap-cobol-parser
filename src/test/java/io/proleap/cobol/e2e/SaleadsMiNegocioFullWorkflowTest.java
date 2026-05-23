package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

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
 * End-to-end workflow test for SaleADS "Mi Negocio" module using visible-text selectors.
 * <p>
 * Required runtime configuration:
 * <ul>
 * <li>Environment variable SALEADS_URL or system property saleads.url with the target login URL.</li>
 * </ul>
 */
public class SaleadsMiNegocioFullWorkflowTest {

  private static final long STEP_TIMEOUT_MS = 30_000;
  private static final long NETWORK_IDLE_TIMEOUT_MS = 7_000;
  private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
  private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
  private static final List<String> REPORT_FIELDS = Arrays.asList(
      "Login",
      "Mi Negocio menu",
      "Agregar Negocio modal",
      "Administrar Negocios view",
      "Informacion General",
      "Detalles de la Cuenta",
      "Tus Negocios",
      "Terminos y Condiciones",
      "Politica de Privacidad");

  @Test
  public void saleadsMiNegocioFullWorkflow() throws Exception {
    final Map<String, String> report = initializeReport();
    final List<String> failures = new ArrayList<>();
    final Path evidenceDir = ensureEvidenceDirectory();

    final String configuredUrl = readConfiguredUrl();
    final boolean headless = readBooleanConfig("SALEADS_HEADLESS", "saleads.headless", true);

    try (Playwright playwright = Playwright.create()) {
      final BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions().setHeadless(headless);
      try (Browser browser = playwright.chromium().launch(launchOptions)) {
        try (BrowserContext context = browser.newContext(new Browser.NewContextOptions().setIgnoreHTTPSErrors(true))) {
          final Page appPage = context.newPage();

          final boolean loginOk = executeStep("Login", report, failures, () -> {
            stepLoginWithGoogle(context, appPage, configuredUrl);
            takeScreenshot(appPage, evidenceDir, "01-dashboard-loaded", true);
          });

          final boolean menuOk = loginOk && executeStep("Mi Negocio menu", report, failures, () -> {
            stepOpenMiNegocioMenu(appPage);
            takeScreenshot(appPage, evidenceDir, "02-mi-negocio-menu-expanded", true);
          });

          final boolean modalOk = menuOk && executeStep("Agregar Negocio modal", report, failures, () -> {
            stepValidateAgregarNegocioModal(appPage);
            takeScreenshot(appPage, evidenceDir, "03-agregar-negocio-modal", true);
          });

          final boolean administrarOk = menuOk && executeStep("Administrar Negocios view", report, failures, () -> {
            stepOpenAdministrarNegocios(appPage);
            takeScreenshot(appPage, evidenceDir, "04-administrar-negocios-page", true);
          });

          if (administrarOk) {
            executeStep("Informacion General", report, failures, () -> stepValidateInformacionGeneral(appPage));
            executeStep("Detalles de la Cuenta", report, failures, () -> stepValidateDetallesCuenta(appPage));
            executeStep("Tus Negocios", report, failures, () -> stepValidateTusNegocios(appPage));
            executeStep("Terminos y Condiciones", report, failures, () -> stepValidateTerminosYCondiciones(context, appPage, evidenceDir));
            executeStep("Politica de Privacidad", report, failures, () -> stepValidatePoliticaPrivacidad(context, appPage, evidenceDir));
          }
        }
      }
    } finally {
      printFinalReport(report);
    }

    final List<String> reportFailures = collectReportFailures(report);
    failures.addAll(reportFailures);
    if (!failures.isEmpty()) {
      fail("SaleADS Mi Negocio workflow validation failed:\n - " + String.join("\n - ", failures));
    }
  }

  private void stepLoginWithGoogle(final BrowserContext context, final Page appPage, final String configuredUrl) {
    appPage.navigate(configuredUrl, new Page.NavigateOptions().setTimeout(STEP_TIMEOUT_MS));
    waitForUiToSettle(appPage);

    final Locator loginButton = firstVisibleLocator(appPage,
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in with Google")),
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Iniciar sesion con Google")),
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Continuar con Google")),
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile(".*Google.*"))),
        appPage.getByText("Sign in with Google"),
        appPage.getByText("Iniciar sesion con Google"),
        appPage.getByText("Continuar con Google"),
        appPage.getByText(Pattern.compile(".*Google.*")));

    final Page googlePopup = waitForPossiblePopup(context, () -> {
      loginButton.click(new Locator.ClickOptions().setTimeout(STEP_TIMEOUT_MS));
    });
    waitForUiToSettle(appPage);

    if (googlePopup != null) {
      selectGoogleAccountIfPrompted(googlePopup, GOOGLE_ACCOUNT_EMAIL);
    } else {
      selectGoogleAccountIfPrompted(appPage, GOOGLE_ACCOUNT_EMAIL);
    }

    waitForUiToSettle(appPage);
    assertSidebarVisible(appPage);
    waitForAnyVisibleText(appPage, Arrays.asList("Negocio", "Mi Negocio"));
  }

  private void stepOpenMiNegocioMenu(final Page appPage) {
    assertSidebarVisible(appPage);
    clickByVisibleText(appPage, "Negocio");
    clickByVisibleText(appPage, "Mi Negocio");

    waitForAnyVisibleText(appPage, Arrays.asList("Agregar Negocio"));
    waitForAnyVisibleText(appPage, Arrays.asList("Administrar Negocios"));
  }

  private void stepValidateAgregarNegocioModal(final Page appPage) {
    clickByVisibleText(appPage, "Agregar Negocio");

    waitForAnyVisibleText(appPage, Arrays.asList("Crear Nuevo Negocio"));
    waitForAnyVisibleText(appPage, Arrays.asList("Nombre del Negocio"));
    waitForAnyVisibleText(appPage, Arrays.asList("Tienes 2 de 3 negocios"));
    waitForAnyVisibleText(appPage, Arrays.asList("Cancelar"));
    waitForAnyVisibleText(appPage, Arrays.asList("Crear Negocio"));

    final Locator nameInput = firstVisibleLocator(appPage,
        appPage.getByLabel("Nombre del Negocio"),
        appPage.getByPlaceholder("Nombre del Negocio"),
        appPage.locator("input[name*='negocio'], input[id*='negocio'], input[placeholder*='Negocio']"));
    nameInput.click(new Locator.ClickOptions().setTimeout(STEP_TIMEOUT_MS));
    nameInput.fill("Negocio Prueba Automatizacion");
    waitForUiToSettle(appPage);

    clickByVisibleText(appPage, "Cancelar");
  }

  private void stepOpenAdministrarNegocios(final Page appPage) {
    if (!isTextVisible(appPage, "Administrar Negocios")) {
      clickByVisibleText(appPage, "Mi Negocio");
    }

    clickByVisibleText(appPage, "Administrar Negocios");

    waitForAnyVisibleText(appPage, Arrays.asList("Informacion General", "Información General"));
    waitForAnyVisibleText(appPage, Arrays.asList("Detalles de la Cuenta"));
    waitForAnyVisibleText(appPage, Arrays.asList("Tus Negocios"));
    waitForAnyVisibleText(appPage, Arrays.asList("Seccion Legal", "Sección Legal"));
  }

  private void stepValidateInformacionGeneral(final Page appPage) {
    final Locator infoSection = sectionByHeading(appPage, Arrays.asList("Informacion General", "Información General"));
    final String sectionText = normalizeWhitespace(infoSection.innerText());

    assertTrue("BUSINESS PLAN not visible in Informacion General section.", sectionText.toUpperCase(Locale.ROOT).contains("BUSINESS PLAN"));
    assertTrue("Cambiar Plan button/text not visible in Informacion General section.",
        sectionText.contains("Cambiar Plan"));
    assertTrue("User email not visible in Informacion General section.",
        EMAIL_PATTERN.matcher(sectionText).find());

    final boolean hasLikelyName = Arrays.stream(sectionText.split("\\R"))
        .map(String::trim)
        .filter(line -> !line.isEmpty())
        .anyMatch(line -> !line.contains("@")
            && !line.equalsIgnoreCase("Informacion General")
            && !line.equalsIgnoreCase("Información General")
            && !line.toUpperCase(Locale.ROOT).contains("BUSINESS PLAN")
            && !line.toLowerCase(Locale.ROOT).contains("cambiar plan")
            && line.length() >= 3);
    assertTrue("User name is not clearly visible in Informacion General section.", hasLikelyName);
  }

  private void stepValidateDetallesCuenta(final Page appPage) {
    final Locator detailsSection = sectionByHeading(appPage, Arrays.asList("Detalles de la Cuenta"));
    final String sectionText = normalizeWhitespace(detailsSection.innerText());

    assertTrue("'Cuenta creada' is not visible in Detalles de la Cuenta.",
        sectionText.toLowerCase(Locale.ROOT).contains("cuenta creada"));
    assertTrue("'Estado activo' is not visible in Detalles de la Cuenta.",
        sectionText.toLowerCase(Locale.ROOT).contains("estado activo"));
    assertTrue("'Idioma seleccionado' is not visible in Detalles de la Cuenta.",
        sectionText.toLowerCase(Locale.ROOT).contains("idioma seleccionado"));
  }

  private void stepValidateTusNegocios(final Page appPage) {
    final Locator businessSection = sectionByHeading(appPage, Arrays.asList("Tus Negocios"));
    final String sectionText = normalizeWhitespace(businessSection.innerText());

    assertTrue("Agregar Negocio button/text is not visible in Tus Negocios section.",
        sectionText.contains("Agregar Negocio"));
    assertTrue("'Tienes 2 de 3 negocios' is not visible in Tus Negocios section.",
        sectionText.contains("Tienes 2 de 3 negocios"));

    final Locator possibleListItems = businessSection.locator("li, [role='listitem'], [role='row'], table tr, [class*='business'], [class*='negocio']");
    assertTrue("Business list is not clearly visible in Tus Negocios section.", possibleListItems.count() > 0 || countMeaningfulLines(sectionText) > 3);
  }

  private void stepValidateTerminosYCondiciones(final BrowserContext context, final Page appPage, final Path evidenceDir) {
    final Page legalPage = clickLegalLinkAndResolveTarget(context, appPage, "Terminos y Condiciones", "Términos y Condiciones");

    waitForAnyVisibleText(legalPage, Arrays.asList("Terminos y Condiciones", "Términos y Condiciones"));
    assertLegalBodyHasContent(legalPage, "Terminos y Condiciones");
    takeScreenshot(legalPage, evidenceDir, "05-terminos-y-condiciones", true);

    final String finalUrl = legalPage.url();
    System.out.println("[EVIDENCE] Terminos y Condiciones URL: " + finalUrl);

    returnToApplicationTab(appPage, legalPage);
  }

  private void stepValidatePoliticaPrivacidad(final BrowserContext context, final Page appPage, final Path evidenceDir) {
    final Page legalPage = clickLegalLinkAndResolveTarget(context, appPage, "Politica de Privacidad", "Política de Privacidad");

    waitForAnyVisibleText(legalPage, Arrays.asList("Politica de Privacidad", "Política de Privacidad"));
    assertLegalBodyHasContent(legalPage, "Politica de Privacidad");
    takeScreenshot(legalPage, evidenceDir, "06-politica-privacidad", true);

    final String finalUrl = legalPage.url();
    System.out.println("[EVIDENCE] Politica de Privacidad URL: " + finalUrl);

    returnToApplicationTab(appPage, legalPage);
  }

  private Page clickLegalLinkAndResolveTarget(final BrowserContext context, final Page appPage, final String... linkText) {
    final Locator link = firstVisibleLocator(appPage, buildTextDrivenLocators(appPage, linkText).toArray(new Locator[0]));
    final Page popupPage = waitForPossiblePopup(context, () -> link.click(new Locator.ClickOptions().setTimeout(STEP_TIMEOUT_MS)));
    if (popupPage != null) {
      waitForUiToSettle(popupPage);
      return popupPage;
    }

    waitForUiToSettle(appPage);
    return appPage;
  }

  private void assertLegalBodyHasContent(final Page legalPage, final String pageName) {
    final String bodyText = normalizeWhitespace(legalPage.locator("body").innerText());
    assertTrue(pageName + " page does not contain enough legal content text.", bodyText.length() > 150);
  }

  private void returnToApplicationTab(final Page appPage, final Page legalPage) {
    if (!Objects.equals(appPage, legalPage)) {
      legalPage.close();
      appPage.bringToFront();
      waitForUiToSettle(appPage);
      return;
    }

    appPage.goBack(new Page.GoBackOptions().setTimeout(STEP_TIMEOUT_MS));
    waitForUiToSettle(appPage);
  }

  private void selectGoogleAccountIfPrompted(final Page page, final String accountEmail) {
    try {
      waitForUiToSettle(page);

      final Locator accountOption = firstVisibleLocator(page,
          page.getByText(accountEmail),
          page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(accountEmail)),
          page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(accountEmail)));
      accountOption.click(new Locator.ClickOptions().setTimeout(5_000));
      waitForUiToSettle(page);
    } catch (PlaywrightException ignored) {
      // Account picker may not appear when session is already authenticated.
    }
  }

  private void clickByVisibleText(final Page page, final String... textOptions) {
    final Locator locator = firstVisibleLocator(page, buildTextDrivenLocators(page, textOptions).toArray(new Locator[0]));
    locator.click(new Locator.ClickOptions().setTimeout(STEP_TIMEOUT_MS));
    waitForUiToSettle(page);
  }

  private List<Locator> buildTextDrivenLocators(final Page page, final String... textOptions) {
    final List<Locator> candidates = new ArrayList<>();
    for (final String text : textOptions) {
      candidates.add(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(text)));
      candidates.add(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(text)));
      candidates.add(page.getByText(text, new Page.GetByTextOptions().setExact(true)));
      candidates.add(page.getByText(text));
    }
    return candidates;
  }

  private Locator sectionByHeading(final Page page, final List<String> headingOptions) {
    for (final String heading : headingOptions) {
      final Locator section = page.locator("section, div, article")
          .filter(new Locator.FilterOptions().setHas(page.getByText(heading, new Page.GetByTextOptions().setExact(true))))
          .first();
      try {
        section.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(5_000));
        return section;
      } catch (PlaywrightException ignored) {
        // Try next heading variant.
      }
    }
    throw new AssertionError("Unable to locate section by headings: " + headingOptions);
  }

  private boolean isTextVisible(final Page page, final String text) {
    final Locator locator = page.getByText(text).first();
    try {
      locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(2_500));
      return true;
    } catch (PlaywrightException ex) {
      return false;
    }
  }

  private void assertSidebarVisible(final Page page) {
    final Locator sidebar = firstVisibleLocator(page,
        page.locator("aside"),
        page.locator("nav"),
        page.locator("[class*='sidebar']"),
        page.locator("[class*='SideBar']"));
    sidebar.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(STEP_TIMEOUT_MS));
  }

  private void waitForAnyVisibleText(final Page page, final List<String> textOptions) {
    for (final String text : textOptions) {
      final Locator locator = page.getByText(text).first();
      try {
        locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(STEP_TIMEOUT_MS));
        return;
      } catch (PlaywrightException ignored) {
        // Try next text option.
      }
    }
    throw new AssertionError("None of the expected texts became visible: " + textOptions);
  }

  private Locator firstVisibleLocator(final Page page, final Locator... locators) {
    for (final Locator locator : locators) {
      final Locator first = locator.first();
      try {
        first.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(3_000));
        return first;
      } catch (PlaywrightException ignored) {
        // Try next locator.
      }
    }
    throw new AssertionError("No visible locator found from provided candidates on page: " + page.url());
  }

  private void waitForUiToSettle(final Page page) {
    page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(STEP_TIMEOUT_MS));
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(NETWORK_IDLE_TIMEOUT_MS));
    } catch (PlaywrightException ignored) {
      // Some pages keep long-running network connections; DOM readiness is enough.
    }
    page.waitForTimeout(600);
  }

  private Page waitForPossiblePopup(final BrowserContext context, final Runnable triggeringAction) {
    try {
      return context.waitForPage(triggeringAction::run, new BrowserContext.WaitForPageOptions().setTimeout(7_000));
    } catch (PlaywrightException popupTimeout) {
      return null;
    }
  }

  private Path ensureEvidenceDirectory() throws Exception {
    final Path evidenceDir = Paths.get("target", "saleads-evidence");
    Files.createDirectories(evidenceDir);
    return evidenceDir;
  }

  private void takeScreenshot(final Page page, final Path evidenceDir, final String checkpointName, final boolean fullPage) {
    final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    final Path screenshotPath = evidenceDir.resolve(timestamp + "-" + checkpointName + ".png");
    page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
    System.out.println("[EVIDENCE] Screenshot saved: " + screenshotPath.toAbsolutePath());
  }

  private String readConfiguredUrl() {
    final String envUrl = System.getenv("SALEADS_URL");
    if (envUrl != null && !envUrl.isBlank()) {
      return envUrl;
    }

    final String propUrl = System.getProperty("saleads.url");
    if (propUrl != null && !propUrl.isBlank()) {
      return propUrl;
    }

    throw new IllegalStateException(
        "Missing SaleADS login URL. Configure SALEADS_URL env var or -Dsaleads.url system property.");
  }

  private boolean readBooleanConfig(final String envName, final String propertyName, final boolean defaultValue) {
    final String envValue = System.getenv(envName);
    if (envValue != null && !envValue.isBlank()) {
      return Boolean.parseBoolean(envValue);
    }

    final String propertyValue = System.getProperty(propertyName);
    if (propertyValue != null && !propertyValue.isBlank()) {
      return Boolean.parseBoolean(propertyValue);
    }

    return defaultValue;
  }

  private Map<String, String> initializeReport() {
    final Map<String, String> report = new LinkedHashMap<>();
    for (final String field : REPORT_FIELDS) {
      report.put(field, "FAIL (not executed)");
    }
    return report;
  }

  private boolean executeStep(final String stepName, final Map<String, String> report, final List<String> failures, final StepAction action) {
    try {
      action.run();
      report.put(stepName, "PASS");
      return true;
    } catch (Throwable throwable) {
      final String message = rootMessage(throwable);
      report.put(stepName, "FAIL (" + message + ")");
      failures.add(stepName + ": " + message);
      return false;
    }
  }

  private List<String> collectReportFailures(final Map<String, String> report) {
    final List<String> reportFailures = new ArrayList<>();
    for (final Map.Entry<String, String> entry : report.entrySet()) {
      if (!entry.getValue().startsWith("PASS")) {
        reportFailures.add(entry.getKey() + " => " + entry.getValue());
      }
    }
    return reportFailures;
  }

  private void printFinalReport(final Map<String, String> report) {
    System.out.println("=== SaleADS Mi Negocio Final Report ===");
    for (final String field : REPORT_FIELDS) {
      System.out.println(field + ": " + report.get(field));
    }
    System.out.println("=== End of Final Report ===");
  }

  private String rootMessage(final Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return normalizeWhitespace(current.getMessage() == null ? current.toString() : current.getMessage());
  }

  private String normalizeWhitespace(final String value) {
    return value.replaceAll("\\s+", " ").trim();
  }

  private int countMeaningfulLines(final String sectionText) {
    int count = 0;
    for (final String line : sectionText.split("\\R")) {
      if (line.trim().length() > 2) {
        count++;
      }
    }
    return count;
  }

  @FunctionalInterface
  private interface StepAction {
    void run() throws Exception;
  }
}
