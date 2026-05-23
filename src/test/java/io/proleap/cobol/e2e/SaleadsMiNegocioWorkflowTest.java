package io.proleap.cobol.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class SaleadsMiNegocioWorkflowTest {

  private static final double DEFAULT_TIMEOUT_MS = 20_000;
  private static final Pattern LOGIN_WITH_GOOGLE_PATTERN = Pattern.compile(
      "(?i)(sign\\s*in\\s*with\\s*google|iniciar\\s*sesi[o\\u00f3]n\\s*con\\s*google|continuar\\s*con\\s*google|ingresar\\s*con\\s*google|google)");
  private static final Pattern GOOGLE_ACCOUNT_PATTERN = Pattern.compile("(?i)juanlucasbarbiergarzon@gmail\\.com");
  private static final Pattern NEGOCIO_PATTERN = Pattern.compile("(?i)^\\s*Negocio\\s*$");
  private static final Pattern MI_NEGOCIO_PATTERN = Pattern.compile("(?i)^\\s*Mi\\s+Negocio\\s*$");
  private static final Pattern AGREGAR_NEGOCIO_PATTERN = Pattern.compile("(?i)^\\s*Agregar\\s+Negocio\\s*$");
  private static final Pattern ADMINISTRAR_NEGOCIOS_PATTERN = Pattern.compile("(?i)^\\s*Administrar\\s+Negocios\\s*$");
  private static final Pattern CREAR_NUEVO_NEGOCIO_PATTERN = Pattern.compile("(?i)Crear\\s+Nuevo\\s+Negocio");
  private static final Pattern NOMBRE_NEGOCIO_PATTERN = Pattern.compile("(?i)Nombre\\s+del\\s+Negocio");
  private static final Pattern CUPO_NEGOCIOS_PATTERN = Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios");
  private static final Pattern INFORMACION_GENERAL_PATTERN = Pattern.compile("(?i)Informaci[o\\u00f3]n\\s+General");
  private static final Pattern DETALLES_CUENTA_PATTERN = Pattern.compile("(?i)Detalles\\s+de\\s+la\\s+Cuenta");
  private static final Pattern TUS_NEGOCIOS_PATTERN = Pattern.compile("(?i)Tus\\s+Negocios");
  private static final Pattern SECCION_LEGAL_PATTERN = Pattern.compile("(?i)Secci[o\\u00f3]n\\s+Legal");
  private static final Pattern BUSINESS_PLAN_PATTERN = Pattern.compile("(?i)BUSINESS\\s+PLAN");
  private static final Pattern CAMBIAR_PLAN_PATTERN = Pattern.compile("(?i)Cambiar\\s+Plan");
  private static final Pattern CUENTA_CREADA_PATTERN = Pattern.compile("(?i)Cuenta\\s+creada");
  private static final Pattern ESTADO_ACTIVO_PATTERN = Pattern.compile("(?i)Estado\\s+activo");
  private static final Pattern IDIOMA_SELECCIONADO_PATTERN = Pattern.compile("(?i)Idioma\\s+seleccionado");
  private static final Pattern TERMINOS_LINK_PATTERN = Pattern.compile("(?i)T[\\u00e9e]rminos\\s+y\\s+Condiciones");
  private static final Pattern TERMINOS_HEADING_PATTERN = Pattern.compile("(?i)T[\\u00e9e]rminos\\s+y\\s+Condiciones");
  private static final Pattern PRIVACIDAD_LINK_PATTERN = Pattern.compile("(?i)Pol[i\\u00ed]tica\\s+de\\s+Privacidad");
  private static final Pattern PRIVACIDAD_HEADING_PATTERN = Pattern.compile("(?i)Pol[i\\u00ed]tica\\s+de\\s+Privacidad");
  private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
  private static final Pattern DEFAULT_USER_NAME_PATTERN = Pattern.compile("(?i)(juan|lucas|barbier|garzon)");

  @Test
  public void saleadsMiNegocioWorkflow() throws IOException {
    final Path evidenceDirectory = createEvidenceDirectory();
    final LinkedHashMap<String, String> report = new LinkedHashMap<>();
    final List<String> failures = new ArrayList<>();
    final Map<String, String> legalUrls = new LinkedHashMap<>();

    try (Playwright playwright = Playwright.create()) {
      final BrowserType browserType = resolveBrowserType(playwright);
      final Browser browser = browserType.launch(new BrowserType.LaunchOptions()
          .setHeadless(Boolean.parseBoolean(envOrDefault("SALEADS_HEADLESS", "true"))));

      try (BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900))) {
        context.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
        Page appPage = context.newPage();
        navigateIfConfigured(appPage);
        waitForUiLoad(appPage);

        appPage = runStep("Login", report, failures, appPage, page -> {
          final boolean hasGoogleLoginButton = isTextVisible(page, LOGIN_WITH_GOOGLE_PATTERN, 7_000);
          if (hasGoogleLoginButton) {
            final Locator loginButton = findVisibleText(page, "Google login button", Arrays.asList(LOGIN_WITH_GOOGLE_PATTERN));
            final Page postLoginPage = clickAndMaybeGetPopup(page, context, loginButton);
            maybeSelectGoogleAccount(postLoginPage, context);
          }

          final Page resolvedAppPage = resolveApplicationPage(context, page);
          assertSidebarVisible(resolvedAppPage);
          assertTextVisible(resolvedAppPage, NEGOCIO_PATTERN, "left sidebar item 'Negocio'");
          captureScreenshot(resolvedAppPage, evidenceDirectory, "01-dashboard-loaded", false);
          return resolvedAppPage;
        });

        appPage = runStep("Mi Negocio menu", report, failures, appPage, page -> {
          clickTextIfPresent(page, NEGOCIO_PATTERN);
          clickByVisibleText(page, MI_NEGOCIO_PATTERN, "Mi Negocio");
          assertTextVisible(page, AGREGAR_NEGOCIO_PATTERN, "submenu item 'Agregar Negocio'");
          assertTextVisible(page, ADMINISTRAR_NEGOCIOS_PATTERN, "submenu item 'Administrar Negocios'");
          captureScreenshot(page, evidenceDirectory, "02-mi-negocio-menu-expanded", false);
          return page;
        });

        appPage = runStep("Agregar Negocio modal", report, failures, appPage, page -> {
          clickByVisibleText(page, AGREGAR_NEGOCIO_PATTERN, "Agregar Negocio");
          assertTextVisible(page, CREAR_NUEVO_NEGOCIO_PATTERN, "modal title 'Crear Nuevo Negocio'");
          assertTextVisible(page, NOMBRE_NEGOCIO_PATTERN, "input label 'Nombre del Negocio'");
          assertTextVisible(page, CUPO_NEGOCIOS_PATTERN, "business quota text");
          assertTextVisible(page, Pattern.compile("(?i)^\\s*Cancelar\\s*$"), "button 'Cancelar'");
          assertTextVisible(page, Pattern.compile("(?i)^\\s*Crear\\s+Negocio\\s*$"), "button 'Crear Negocio'");

          final Locator businessNameInput = page.locator(
              "input[placeholder*='Nombre'], input[name*='nombre'], input[aria-label*='Nombre']").first();
          if (isLocatorVisible(businessNameInput, 3_000)) {
            businessNameInput.click();
            businessNameInput.fill("Negocio Prueba Automatizacion");
          }

          captureScreenshot(page, evidenceDirectory, "03-agregar-negocio-modal", false);
          clickByVisibleText(page, Pattern.compile("(?i)^\\s*Cancelar\\s*$"), "Cancelar");
          return page;
        });

        appPage = runStep("Administrar Negocios view", report, failures, appPage, page -> {
          ensureMiNegocioExpanded(page);
          clickByVisibleText(page, ADMINISTRAR_NEGOCIOS_PATTERN, "Administrar Negocios");
          waitForUiLoad(page);

          assertTextVisible(page, INFORMACION_GENERAL_PATTERN, "section 'Informacion General'");
          assertTextVisible(page, DETALLES_CUENTA_PATTERN, "section 'Detalles de la Cuenta'");
          assertTextVisible(page, TUS_NEGOCIOS_PATTERN, "section 'Tus Negocios'");
          assertTextVisible(page, SECCION_LEGAL_PATTERN, "section 'Seccion Legal'");
          captureScreenshot(page, evidenceDirectory, "04-administrar-negocios-full", true);
          return page;
        });

        appPage = runStep("Informacion General", report, failures, appPage, page -> {
          assertTextVisible(page, EMAIL_PATTERN, "user email");
          assertTextVisible(page, resolveExpectedUserNamePattern(), "user name");
          assertTextVisible(page, BUSINESS_PLAN_PATTERN, "text 'BUSINESS PLAN'");
          assertTextVisible(page, CAMBIAR_PLAN_PATTERN, "button 'Cambiar Plan'");
          return page;
        });

        appPage = runStep("Detalles de la Cuenta", report, failures, appPage, page -> {
          assertTextVisible(page, CUENTA_CREADA_PATTERN, "'Cuenta creada'");
          assertTextVisible(page, ESTADO_ACTIVO_PATTERN, "'Estado activo'");
          assertTextVisible(page, IDIOMA_SELECCIONADO_PATTERN, "'Idioma seleccionado'");
          return page;
        });

        appPage = runStep("Tus Negocios", report, failures, appPage, page -> {
          assertTextVisible(page, TUS_NEGOCIOS_PATTERN, "section title 'Tus Negocios'");
          assertTextVisible(page, AGREGAR_NEGOCIO_PATTERN, "button 'Agregar Negocio'");
          assertTextVisible(page, CUPO_NEGOCIOS_PATTERN, "text 'Tienes 2 de 3 negocios'");
          final Locator businessRows = page.locator("tbody tr, ul li, [data-testid*='business'], [class*='business']");
          Assert.assertTrue("Business list is not visible or is empty.", businessRows.count() > 0);
          return page;
        });

        appPage = runStep("Terminos y Condiciones", report, failures, appPage, page -> {
          final String legalUrl = validateLegalLink(page, context, TERMINOS_LINK_PATTERN, TERMINOS_HEADING_PATTERN,
              evidenceDirectory, "05-terminos-y-condiciones");
          legalUrls.put("Terminos y Condiciones", legalUrl);
          return page;
        });

        appPage = runStep("Politica de Privacidad", report, failures, appPage, page -> {
          final String legalUrl = validateLegalLink(page, context, PRIVACIDAD_LINK_PATTERN, PRIVACIDAD_HEADING_PATTERN,
              evidenceDirectory, "06-politica-de-privacidad");
          legalUrls.put("Politica de Privacidad", legalUrl);
          return page;
        });
      }
    }

    System.out.println("==== SaleADS Mi Negocio Workflow Final Report ====");
    for (Map.Entry<String, String> entry : report.entrySet()) {
      System.out.println(entry.getKey() + ": " + entry.getValue());
    }

    if (!legalUrls.isEmpty()) {
      System.out.println("---- Legal URLs ----");
      legalUrls.forEach((key, value) -> System.out.println(key + ": " + value));
    }

    System.out.println("Evidence directory: " + evidenceDirectory.toAbsolutePath());

    if (!failures.isEmpty()) {
      for (String failure : failures) {
        System.err.println("FAILURE: " + failure);
      }
    }

    Assert.assertTrue("Some workflow validations failed. See report and failures above.", failures.isEmpty());
  }

  private Page runStep(final String stepName, final LinkedHashMap<String, String> report, final List<String> failures,
      final Page currentPage, final PageStep step) {
    try {
      final Page resultingPage = step.execute(currentPage);
      report.put(stepName, "PASS");
      return resultingPage;
    } catch (Throwable throwable) {
      report.put(stepName, "FAIL");
      failures.add(stepName + " -> " + throwable.getMessage());
      return currentPage;
    }
  }

  private BrowserType resolveBrowserType(final Playwright playwright) {
    final String configuredBrowser = envOrDefault("SALEADS_BROWSER", "chromium").toLowerCase();
    if ("firefox".equals(configuredBrowser)) {
      return playwright.firefox();
    }
    if ("webkit".equals(configuredBrowser)) {
      return playwright.webkit();
    }
    return playwright.chromium();
  }

  private void navigateIfConfigured(final Page page) {
    final String configuredUrl = firstNonBlank(System.getenv("SALEADS_LOGIN_URL"),
        System.getenv("SALEADS_URL"), System.getenv("BASE_URL"));
    if (configuredUrl != null) {
      page.navigate(configuredUrl);
    }
  }

  private void maybeSelectGoogleAccount(final Page page, final BrowserContext context) {
    if (clickTextIfPresent(page, GOOGLE_ACCOUNT_PATTERN)) {
      waitForUiLoad(page);
      return;
    }

    for (Page otherPage : context.pages()) {
      if (otherPage != page && clickTextIfPresent(otherPage, GOOGLE_ACCOUNT_PATTERN)) {
        waitForUiLoad(otherPage);
        return;
      }
    }
  }

  private Page resolveApplicationPage(final BrowserContext context, final Page fallbackPage) {
    if (hasSidebarNavigation(fallbackPage)) {
      return fallbackPage;
    }

    for (Page candidate : context.pages()) {
      if (candidate != fallbackPage && hasSidebarNavigation(candidate)) {
        candidate.bringToFront();
        return candidate;
      }
    }

    return fallbackPage;
  }

  private void ensureMiNegocioExpanded(final Page page) {
    if (!isTextVisible(page, ADMINISTRAR_NEGOCIOS_PATTERN, 2_500)) {
      clickByVisibleText(page, MI_NEGOCIO_PATTERN, "Mi Negocio");
    }
  }

  private String validateLegalLink(final Page appPage, final BrowserContext context, final Pattern linkPattern,
      final Pattern expectedHeadingPattern, final Path evidenceDirectory, final String screenshotName) {
    final Locator legalLink = findVisibleText(appPage, "legal link", Arrays.asList(linkPattern));
    final String appUrlBeforeClick = appPage.url();

    Page openedPage = null;
    try {
      openedPage = context.waitForPage(() -> {
        legalLink.click();
      }, new BrowserContext.WaitForPageOptions().setTimeout(7_000));
    } catch (PlaywrightException ignored) {
      // Same-tab navigation is valid, the click already happened.
    }

    final boolean openedInNewTab = openedPage != null;
    final Page legalPage = openedInNewTab ? openedPage : appPage;
    waitForUiLoad(legalPage);

    assertTextVisible(legalPage, expectedHeadingPattern, "legal document heading");
    final String legalBodyText = legalPage.locator("body").innerText();
    Assert.assertTrue("Legal page content text should be visible.", legalBodyText != null
        && legalBodyText.replaceAll("\\s+", " ").trim().length() > 120);

    captureScreenshot(legalPage, evidenceDirectory, screenshotName, true);
    final String finalUrl = legalPage.url();

    if (openedInNewTab) {
      legalPage.close();
      appPage.bringToFront();
      waitForUiLoad(appPage);
    } else {
      appPage.goBack(new Page.GoBackOptions().setTimeout(DEFAULT_TIMEOUT_MS));
      waitForUiLoad(appPage);
      if (!appUrlBeforeClick.equals(appPage.url()) && appUrlBeforeClick.startsWith("http")) {
        appPage.navigate(appUrlBeforeClick);
        waitForUiLoad(appPage);
      }
    }

    return finalUrl;
  }

  private Page clickAndMaybeGetPopup(final Page sourcePage, final BrowserContext context, final Locator locator) {
    Page popup = null;
    try {
      popup = context.waitForPage(() -> {
        locator.click();
      }, new BrowserContext.WaitForPageOptions().setTimeout(7_000));
    } catch (PlaywrightException ignored) {
      // No popup opened. The click callback already ran.
    }

    waitForUiLoad(sourcePage);
    if (popup != null) {
      waitForUiLoad(popup);
      popup.bringToFront();
      return popup;
    }

    return sourcePage;
  }

  private void clickByVisibleText(final Page page, final Pattern pattern, final String description) {
    final Locator locator = findVisibleText(page, description, Arrays.asList(pattern));
    locator.click();
    waitForUiLoad(page);
  }

  private boolean clickTextIfPresent(final Page page, final Pattern pattern) {
    final Locator locator = page.getByText(pattern).first();
    if (isLocatorVisible(locator, 2_500)) {
      locator.click();
      waitForUiLoad(page);
      return true;
    }

    return false;
  }

  private Locator findVisibleText(final Page page, final String description, final List<Pattern> candidatePatterns) {
    for (Pattern pattern : candidatePatterns) {
      final Locator locator = page.getByText(pattern).first();
      if (isLocatorVisible(locator, 3_500)) {
        return locator;
      }
    }

    throw new AssertionError("Could not find visible element: " + description + " using provided text selectors.");
  }

  private void assertTextVisible(final Page page, final Pattern pattern, final String description) {
    final Locator locator = page.getByText(pattern).first();
    locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));
    PlaywrightAssertions.assertThat(locator).isVisible();
    Assert.assertTrue("Expected to find visible text for " + description, locator.count() > 0);
  }

  private void assertSidebarVisible(final Page page) {
    if (hasSidebarNavigation(page)) {
      return;
    }

    throw new AssertionError("Left sidebar navigation is not visible.");
  }

  private boolean hasSidebarNavigation(final Page page) {
    final Locator explicitSidebar = page.locator("aside:visible, nav:visible").first();
    if (isLocatorVisible(explicitSidebar, 4_000)) {
      return true;
    }

    final Locator negocioItem = page.getByText(NEGOCIO_PATTERN).first();
    return isLocatorVisible(negocioItem, 4_000);
  }

  private boolean isTextVisible(final Page page, final Pattern pattern, final double timeoutMs) {
    return isLocatorVisible(page.getByText(pattern).first(), timeoutMs);
  }

  private boolean isLocatorVisible(final Locator locator, final double timeoutMs) {
    try {
      locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
      return true;
    } catch (PlaywrightException ignored) {
      return false;
    }
  }

  private void waitForUiLoad(final Page page) {
    page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5_000));
    } catch (PlaywrightException ignored) {
      // Some apps keep active websocket traffic; DOM loaded is enough in those cases.
    }
    page.waitForTimeout(500);
  }

  private void captureScreenshot(final Page page, final Path evidenceDirectory, final String checkpointName,
      final boolean fullPage) {
    final String fileName = checkpointName.replaceAll("[^a-zA-Z0-9._-]", "-") + ".png";
    final Path screenshotPath = evidenceDirectory.resolve(fileName);
    page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
  }

  private Path createEvidenceDirectory() throws IOException {
    final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    final Path evidencePath = Paths.get("target", "saleads-evidence", runId);
    Files.createDirectories(evidencePath);
    return evidencePath;
  }

  private Pattern resolveExpectedUserNamePattern() {
    final String expectedUserName = System.getenv("SALEADS_EXPECTED_USER_NAME");
    if (expectedUserName != null && !expectedUserName.isBlank()) {
      return Pattern.compile(Pattern.quote(expectedUserName), Pattern.CASE_INSENSITIVE);
    }

    return DEFAULT_USER_NAME_PATTERN;
  }

  private String envOrDefault(final String key, final String defaultValue) {
    final String value = System.getenv(key);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    return value;
  }

  private String firstNonBlank(final String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  @FunctionalInterface
  private interface PageStep {
    Page execute(Page page);
  }
}
