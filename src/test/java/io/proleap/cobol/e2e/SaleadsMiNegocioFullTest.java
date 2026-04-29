package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * Full E2E workflow for SaleADS "Mi Negocio" module.
 *
 * <p>Execution configuration:
 *
 * <ul>
 *   <li>SALEADS_START_URL: Login page URL for the current environment (required).
 *   <li>SALEADS_HEADLESS: true/false, default true.
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

  private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
  private static final int SHORT_TIMEOUT_MS = 5_000;
  private static final int DEFAULT_TIMEOUT_MS = 20_000;
  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

  private final LinkedHashMap<String, String> finalReport = new LinkedHashMap<>();

  private Playwright playwright;
  private Browser browser;
  private BrowserContext context;
  private Page appPage;
  private Path evidenceDir;
  private String termsFinalUrl = "N/A";
  private String privacyFinalUrl = "N/A";

  @Before
  public void setUp() throws IOException {
    final String startUrl = System.getenv("SALEADS_START_URL");
    Assume.assumeTrue(
        "Skipping SaleADS E2E test because SALEADS_START_URL is not set.",
        startUrl != null && !startUrl.isBlank());

    evidenceDir = Paths.get("target", "saleads-evidence");
    Files.createDirectories(evidenceDir);

    final boolean headless =
        Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));

    playwright = Playwright.create();
    browser =
        playwright
            .chromium()
            .launch(new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo(100D));
    context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
    appPage = context.newPage();
    appPage.navigate(startUrl);
    waitForUi(appPage);
  }

  @After
  public void tearDown() throws IOException {
    if (evidenceDir != null) {
      writeFinalReport();
    }

    if (context != null) {
      context.close();
    }
    if (browser != null) {
      browser.close();
    }
    if (playwright != null) {
      playwright.close();
    }
  }

  @Test
  public void saleadsMiNegocioFullWorkflow() {
    runStep("Login", this::stepLoginWithGoogle);
    runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
    runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
    runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
    runStep("Información General", this::stepValidateInformacionGeneral);
    runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
    runStep("Tus Negocios", this::stepValidateTusNegocios);
    runStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
    runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

    final boolean allPassed = finalReport.values().stream().allMatch(v -> v.startsWith("PASS"));
    assertTrue("One or more validations failed. Check target/saleads-evidence/final-report.txt", allPassed);
  }

  private void stepLoginWithGoogle() {
    final Locator loginButton =
        visibleLocator(
            appPage,
            "text=/Sign in with Google/i",
            "text=/Iniciar sesión con Google/i",
            "button:has-text('Google')",
            "a:has-text('Google')");

    clickAndWaitUi(appPage, loginButton);

    final Page googleSurface = resolveGoogleSurface();
    maybeChooseGoogleAccount(googleSurface);

    appPage.bringToFront();
    waitForUi(appPage);

    assertVisible(
        appPage,
        "Main interface should be visible",
        "aside",
        "nav",
        "text=/Negocio/i",
        "text=/Mi\\s*Negocio/i");

    takeScreenshot(appPage, "01-dashboard-loaded", false);
  }

  private void stepOpenMiNegocioMenu() {
    final Locator negocioSection =
        visibleLocator(appPage, "text=/^Negocio$/i", "button:has-text('Negocio')", "a:has-text('Negocio')");
    clickAndWaitUi(appPage, negocioSection);

    final Locator miNegocioOption =
        visibleLocator(
            appPage, "text=/Mi\\s*Negocio/i", "button:has-text('Mi Negocio')", "a:has-text('Mi Negocio')");
    clickAndWaitUi(appPage, miNegocioOption);

    assertVisible(appPage, "'Agregar Negocio' should be visible", "text=/^Agregar\\s+Negocio$/i");
    assertVisible(appPage, "'Administrar Negocios' should be visible", "text=/Administrar\\s+Negocios/i");

    takeScreenshot(appPage, "02-mi-negocio-menu-expanded", false);
  }

  private void stepValidateAgregarNegocioModal() {
    clickAndWaitUi(appPage, visibleLocator(appPage, "text=/^Agregar\\s+Negocio$/i"));
    final Locator modal =
        visibleLocator(appPage, "div[role='dialog']", "div:has-text('Crear Nuevo Negocio')");

    assertVisible(modal, "Modal title should be visible", "text=/Crear\\s+Nuevo\\s+Negocio/i");
    assertVisible(
        modal,
        "'Nombre del Negocio' input should exist",
        "input[placeholder*='Nombre del Negocio']",
        "label:has-text('Nombre del Negocio')",
        "input[name*='nombre']");
    assertVisible(modal, "'Tienes 2 de 3 negocios' text should be visible", "text=/Tienes\\s+2\\s+de\\s+3\\s+negocios/i");
    assertVisible(modal, "'Cancelar' button should be present", "button:has-text('Cancelar')");
    assertVisible(modal, "'Crear Negocio' button should be present", "button:has-text('Crear Negocio')");

    takeScreenshot(appPage, "03-agregar-negocio-modal", false);

    final Locator nombreNegocioInput =
        visibleLocator(
            modal,
            "input[placeholder*='Nombre del Negocio']",
            "label:has-text('Nombre del Negocio') + input",
            "input[name*='nombre']",
            "input[type='text']");
    nombreNegocioInput.click();
    nombreNegocioInput.fill("Negocio Prueba Automatización");
    clickAndWaitUi(appPage, visibleLocator(modal, "button:has-text('Cancelar')"));

    assertNotVisible(appPage, "Modal should close after cancel", "text=/Crear\\s+Nuevo\\s+Negocio/i");
  }

  private void stepOpenAdministrarNegocios() {
    if (!isVisible(appPage, "text=/Administrar\\s+Negocios/i")) {
      clickAndWaitUi(appPage, visibleLocator(appPage, "text=/Mi\\s*Negocio/i"));
    }

    clickAndWaitUi(appPage, visibleLocator(appPage, "text=/Administrar\\s+Negocios/i"));

    assertVisible(appPage, "'Información General' section should exist", "text=/Información\\s+General/i");
    assertVisible(appPage, "'Detalles de la Cuenta' section should exist", "text=/Detalles\\s+de\\s+la\\s+Cuenta/i");
    assertVisible(appPage, "'Tus Negocios' section should exist", "text=/Tus\\s+Negocios/i");
    assertVisible(appPage, "'Sección Legal' section should exist", "text=/Sección\\s+Legal/i");

    takeScreenshot(appPage, "04-administrar-negocios-view", true);
  }

  private void stepValidateInformacionGeneral() {
    final String infoText = sectionTextByHeading("Información General");
    final boolean hasEmail = EMAIL_PATTERN.matcher(infoText).find();
    final boolean hasName = hasLikelyUserName(infoText);

    assertTrue("User email should be visible in Información General", hasEmail);
    assertTrue("User name should be visible in Información General", hasName);
    assertVisible(appPage, "'BUSINESS PLAN' text should be visible", "text=/BUSINESS\\s+PLAN/i");
    assertVisible(appPage, "'Cambiar Plan' button should be visible", "text=/Cambiar\\s+Plan/i");
  }

  private void stepValidateDetallesCuenta() {
    assertVisible(appPage, "'Cuenta creada' should be visible", "text=/Cuenta\\s+creada/i");
    assertVisible(appPage, "'Estado activo' should be visible", "text=/Estado\\s+activo/i");
    assertVisible(appPage, "'Idioma seleccionado' should be visible", "text=/Idioma\\s+seleccionado/i");
  }

  private void stepValidateTusNegocios() {
    final String negociosText = sectionTextByHeading("Tus Negocios");
    assertTrue("Business list should be visible in 'Tus Negocios'", hasBusinessListContent(negociosText));

    assertVisible(appPage, "'Agregar Negocio' button should exist", "text=/^Agregar\\s+Negocio$/i");
    assertVisible(appPage, "'Tienes 2 de 3 negocios' should be visible", "text=/Tienes\\s+2\\s+de\\s+3\\s+negocios/i");
  }

  private void stepValidateTerminosYCondiciones() {
    final Page legalPage = openLegalLink("Términos y Condiciones");
    assertVisible(legalPage, "Terms page heading should be visible", "text=/Términos\\s+y\\s+Condiciones/i");
    assertLegalContentVisible(legalPage, "Términos y Condiciones");
    termsFinalUrl = legalPage.url();
    takeScreenshot(legalPage, "08-terminos-y-condiciones", true);
    returnToApplicationTab(legalPage);
  }

  private void stepValidatePoliticaPrivacidad() {
    final Page legalPage = openLegalLink("Política de Privacidad");
    assertVisible(legalPage, "Privacy page heading should be visible", "text=/Política\\s+de\\s+Privacidad/i");
    assertLegalContentVisible(legalPage, "Política de Privacidad");
    privacyFinalUrl = legalPage.url();
    takeScreenshot(legalPage, "09-politica-de-privacidad", true);
    returnToApplicationTab(legalPage);
  }

  private void runStep(final String stepName, final Runnable action) {
    try {
      action.run();
      finalReport.put(stepName, "PASS");
    } catch (Throwable t) {
      finalReport.put(stepName, "FAIL - " + sanitizeMessage(t.getMessage()));
      takeFailureScreenshot(stepName);
    }
  }

  private String sectionTextByHeading(final String heading) {
    final Locator headingLocator = visibleLocator(appPage, "text=/" + Pattern.quote(heading) + "/i");
    final Locator section = headingLocator.locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
    return section.innerText();
  }

  private boolean hasLikelyUserName(final String text) {
    for (final String rawLine : text.split("\\R")) {
      final String line = rawLine.trim();
      if (line.isEmpty()) {
        continue;
      }
      if (line.equalsIgnoreCase("Información General")
          || line.equalsIgnoreCase("BUSINESS PLAN")
          || line.equalsIgnoreCase("Cambiar Plan")
          || line.matches("(?i).*\\b(plan|cuenta|estado|idioma)\\b.*")) {
        continue;
      }
      if (EMAIL_PATTERN.matcher(line).find()) {
        continue;
      }
      if (line.length() >= 3) {
        return true;
      }
    }
    return false;
  }

  private boolean hasBusinessListContent(final String text) {
    int usefulLines = 0;
    for (final String rawLine : text.split("\\R")) {
      final String line = rawLine.trim();
      if (line.isEmpty()
          || line.equalsIgnoreCase("Tus Negocios")
          || line.equalsIgnoreCase("Agregar Negocio")
          || line.matches("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios")) {
        continue;
      }
      usefulLines++;
    }
    return usefulLines > 0;
  }

  private void assertLegalContentVisible(final Page page, final String documentName) {
    final Locator paragraphs = page.locator("p, li, h2, h3");
    assertTrue(documentName + " content should be visible", paragraphs.count() > 2);
  }

  private Page openLegalLink(final String linkText) {
    final int pagesBefore = context.pages().size();
    final String previousUrl = appPage.url();
    clickAndWaitUi(
        appPage,
        visibleLocator(
            appPage,
            "text=/" + Pattern.quote(linkText) + "/i",
            "a:has-text('" + linkText + "')",
            "button:has-text('" + linkText + "')"));

    appPage.waitForTimeout(1_500);
    final int pagesAfter = context.pages().size();

    if (pagesAfter > pagesBefore) {
      final Page newTab = context.pages().get(pagesAfter - 1);
      newTab.bringToFront();
      waitForUi(newTab);
      return newTab;
    }

    if (!previousUrl.equals(appPage.url())) {
      waitForUi(appPage);
      return appPage;
    }

    waitForUi(appPage);
    return appPage;
  }

  private void returnToApplicationTab(final Page currentPage) {
    if (currentPage != appPage) {
      currentPage.close();
      appPage.bringToFront();
      waitForUi(appPage);
      return;
    }

    try {
      appPage.goBack();
      waitForUi(appPage);
    } catch (PlaywrightException ignored) {
      appPage.bringToFront();
      waitForUi(appPage);
    }
  }

  private Page resolveGoogleSurface() {
    final int pages = context.pages().size();
    if (pages > 1) {
      final Page latestPage = context.pages().get(pages - 1);
      latestPage.bringToFront();
      waitForUi(latestPage);
      return latestPage;
    }
    return appPage;
  }

  private void maybeChooseGoogleAccount(final Page page) {
    final Locator possibleAccount =
        firstVisibleLocator(
            page,
            "text=/" + Pattern.quote(GOOGLE_ACCOUNT_EMAIL) + "/i",
            "[data-email='" + GOOGLE_ACCOUNT_EMAIL + "']",
            "div:has-text('" + GOOGLE_ACCOUNT_EMAIL + "')");

    if (possibleAccount != null) {
      clickAndWaitUi(page, possibleAccount);
      page.waitForTimeout(1_000);
    }
  }

  private void clickAndWaitUi(final Page page, final Locator locator) {
    locator.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
    waitForUi(page);
  }

  private void waitForUi(final Page page) {
    try {
      page.waitForLoadState(
          LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(SHORT_TIMEOUT_MS));
    } catch (PlaywrightException ignored) {
      // Some UI interactions do not trigger a navigation state change.
    }

    try {
      page.waitForLoadState(
          LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(SHORT_TIMEOUT_MS));
    } catch (PlaywrightException ignored) {
      // Some pages maintain active network connections (e.g. websockets).
    }

    page.waitForTimeout(600);
  }

  private void assertVisible(final Page page, final String errorMessage, final String... selectors) {
    if (firstVisibleLocator(page, selectors) == null) {
      throw new AssertionError(errorMessage);
    }
  }

  private void assertVisible(final Locator root, final String errorMessage, final String... selectors) {
    if (firstVisibleLocator(root, selectors) == null) {
      throw new AssertionError(errorMessage);
    }
  }

  private void assertNotVisible(final Page page, final String errorMessage, final String selector) {
    if (isVisible(page, selector)) {
      throw new AssertionError(errorMessage);
    }
  }

  private boolean isVisible(final Page page, final String selector) {
    try {
      final Locator locator = page.locator(selector).first();
      locator.waitFor(
          new Locator.WaitForOptions()
              .setState(WaitForSelectorState.VISIBLE)
              .setTimeout(2_000));
      return true;
    } catch (PlaywrightException ignored) {
      return false;
    }
  }

  private Locator visibleLocator(final Page page, final String... selectors) {
    final Locator locator = firstVisibleLocator(page, selectors);
    if (locator == null) {
      throw new AssertionError("No visible element found for selectors: " + String.join(", ", selectors));
    }
    return locator;
  }

  private Locator visibleLocator(final Locator root, final String... selectors) {
    final Locator locator = firstVisibleLocator(root, selectors);
    if (locator == null) {
      throw new AssertionError("No visible element found for selectors: " + String.join(", ", selectors));
    }
    return locator;
  }

  private Locator firstVisibleLocator(final Page page, final String... selectors) {
    for (final String selector : selectors) {
      try {
        final Locator candidate = page.locator(selector).first();
        candidate.waitFor(
            new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(SHORT_TIMEOUT_MS));
        return candidate;
      } catch (PlaywrightException ignored) {
        // Try next selector.
      }
    }
    return null;
  }

  private Locator firstVisibleLocator(final Locator root, final String... selectors) {
    for (final String selector : selectors) {
      try {
        final Locator candidate = root.locator(selector).first();
        candidate.waitFor(
            new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(SHORT_TIMEOUT_MS));
        return candidate;
      } catch (PlaywrightException ignored) {
        // Try next selector.
      }
    }
    return null;
  }

  private void takeScreenshot(final Page page, final String fileName, final boolean fullPage) {
    page.screenshot(
        new Page.ScreenshotOptions().setPath(evidenceDir.resolve(fileName + ".png")).setFullPage(fullPage));
  }

  private void takeFailureScreenshot(final String stepName) {
    if (appPage == null) {
      return;
    }

    final String safeName = stepName.toLowerCase().replaceAll("[^a-z0-9]+", "-");
    try {
      takeScreenshot(appPage, "failure-" + safeName, true);
    } catch (PlaywrightException ignored) {
      // Ignore screenshot failures to preserve main test result logging.
    }
  }

  private void writeFinalReport() throws IOException {
    if (evidenceDir == null) {
      return;
    }

    final StringBuilder sb = new StringBuilder();
    sb.append("saleads_mi_negocio_full_test\n");
    sb.append("executedAt=")
        .append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        .append("\n\n");

    for (final Map.Entry<String, String> entry : finalReport.entrySet()) {
      sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
    }

    sb.append("\nFinal URL - Términos y Condiciones: ").append(termsFinalUrl).append("\n");
    sb.append("Final URL - Política de Privacidad: ").append(privacyFinalUrl).append("\n");

    Files.writeString(evidenceDir.resolve("final-report.txt"), sb.toString(), StandardCharsets.UTF_8);
    System.out.println(sb);
  }

  private String sanitizeMessage(final String message) {
    if (message == null || message.isBlank()) {
      return "Unknown error";
    }
    return message.replace('\n', ' ').trim();
  }
}
