package io.proleap.e2e;

import static org.junit.Assert.assertTrue;

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

import org.junit.Assert;
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
 * End-to-end workflow validation for SaleADS "Mi Negocio" module.
 * <p>
 * Environment-agnostic configuration:
 * <ul>
 * <li>Use {@code SALEADS_URL} or {@code -Dsaleads.url=} to start from a login URL in any environment.</li>
 * <li>Use {@code SALEADS_CDP_URL} or {@code -Dsaleads.cdp.url=} to connect to an existing browser page
 * already positioned on the login screen.</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

  private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
  private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
  private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

  private static final String STEP_LOGIN = "Login";
  private static final String STEP_MENU = "Mi Negocio menu";
  private static final String STEP_MODAL = "Agregar Negocio modal";
  private static final String STEP_ADMIN = "Administrar Negocios view";
  private static final String STEP_INFO_GENERAL = "Información General";
  private static final String STEP_ACCOUNT_DETAILS = "Detalles de la Cuenta";
  private static final String STEP_BUSINESSES = "Tus Negocios";
  private static final String STEP_TERMS = "Términos y Condiciones";
  private static final String STEP_PRIVACY = "Política de Privacidad";

  private final Map<String, String> report = new LinkedHashMap<>();
  private final List<String> failures = new ArrayList<>();
  private final Map<String, String> legalUrls = new LinkedHashMap<>();
  private final Path evidenceDir = Paths.get("target", "saleads-evidence");

  @Test
  public void saleadsMiNegocioFullWorkflow() throws Exception {
    initializeReport();
    Files.createDirectories(evidenceDir);

    final String saleadsUrl = readConfig("saleads.url", "SALEADS_URL");
    final String cdpUrl = readConfig("saleads.cdp.url", "SALEADS_CDP_URL");
    final boolean headless = !"false".equalsIgnoreCase(readConfig("saleads.headless", "SALEADS_HEADLESS"));

    Assume.assumeTrue(
        "Set SALEADS_URL (or -Dsaleads.url) for direct navigation, or SALEADS_CDP_URL (or -Dsaleads.cdp.url) to reuse an existing login page.",
        hasText(saleadsUrl) || hasText(cdpUrl));

    try (Playwright playwright = Playwright.create()) {
      final BrowserSession session = openSession(playwright, saleadsUrl, cdpUrl, headless);
      final Page appPage = session.page;
      final BrowserContext context = session.context;

      appPage.setDefaultTimeout(20_000);
      appPage.setDefaultNavigationTimeout(30_000);
      waitForUiSettled(appPage);

      runStep(STEP_LOGIN, () -> {
        loginWithGoogle(appPage);
        assertTextVisible(appPage, "Negocio");
        assertSidebarVisible(appPage);
        captureScreenshot(appPage, "01_dashboard_loaded", false);
      });

      runStep(STEP_MENU, () -> {
        openMiNegocioMenu(appPage);
        assertTextVisible(appPage, "Agregar Negocio");
        assertTextVisible(appPage, "Administrar Negocios");
        captureScreenshot(appPage, "02_mi_negocio_menu_expanded", false);
      });

      runStep(STEP_MODAL, () -> {
        Locator addBusiness = findVisibleByText(appPage, "Agregar Negocio", "Agregar Negocio option");
        clickAndWait(appPage, addBusiness, "Agregar Negocio");

        assertTextVisible(appPage, "Crear Nuevo Negocio");
        Locator businessNameInput = firstVisible("Nombre del Negocio input",
            appPage.getByLabel("Nombre del Negocio"),
            appPage.getByPlaceholder("Nombre del Negocio"),
            appPage.locator("input[name*=nombre i], input[id*=nombre i]"));
        assertVisible(businessNameInput, "Nombre del Negocio input should exist");

        assertTextVisible(appPage, "Tienes 2 de 3 negocios");
        assertTextVisible(appPage, "Cancelar");
        assertTextVisible(appPage, "Crear Negocio");
        captureScreenshot(appPage, "03_agregar_negocio_modal", false);

        clickAndWait(appPage, businessNameInput, "Nombre del Negocio field");
        businessNameInput.fill("Negocio Prueba Automatización");
        clickAndWait(appPage, findVisibleByText(appPage, "Cancelar", "Cancelar button"), "Cancelar");
      });

      runStep(STEP_ADMIN, () -> {
        ensureMiNegocioMenuExpanded(appPage);
        clickAndWait(appPage, findVisibleByText(appPage, "Administrar Negocios", "Administrar Negocios"), "Administrar Negocios");

        assertTextVisible(appPage, "Información General");
        assertTextVisible(appPage, "Detalles de la Cuenta");
        assertTextVisible(appPage, "Tus Negocios");
        assertTextVisible(appPage, "Sección Legal");
        captureScreenshot(appPage, "04_administrar_negocios_view", true);
      });

      runStep(STEP_INFO_GENERAL, () -> {
        String infoText = sectionText(appPage, "Información General");
        assertTrue("User email should be visible in Información General", EMAIL_PATTERN.matcher(infoText).find());
        assertVisibleUserNameLikeText(infoText);
        assertContainsIgnoringCase(infoText, "BUSINESS PLAN", "BUSINESS PLAN should be visible");
        assertTextVisible(appPage, "Cambiar Plan");
      });

      runStep(STEP_ACCOUNT_DETAILS, () -> {
        String detailsText = sectionText(appPage, "Detalles de la Cuenta");
        assertContainsIgnoringCase(detailsText, "Cuenta creada", "'Cuenta creada' should be visible");
        assertContainsIgnoringCase(detailsText, "Estado activo", "'Estado activo' should be visible");
        assertContainsIgnoringCase(detailsText, "Idioma seleccionado", "'Idioma seleccionado' should be visible");
      });

      runStep(STEP_BUSINESSES, () -> {
        String businessesText = sectionText(appPage, "Tus Negocios");
        assertContainsIgnoringCase(businessesText, "Agregar Negocio", "'Agregar Negocio' should be visible in Tus Negocios");
        assertContainsIgnoringCase(businessesText, "Tienes 2 de 3 negocios", "'Tienes 2 de 3 negocios' should be visible");
        Locator listItems = appPage.locator("li, [role='listitem'], table tr, .business-item");
        assertTrue("Business list should be visible", listItems.count() > 0 || businessesText.length() > 50);
      });

      runStep(STEP_TERMS, () -> {
        String termsUrl = openLegalAndValidate(appPage, context, "Términos y Condiciones",
            Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"),
            "05_terminos_condiciones");
        legalUrls.put(STEP_TERMS, termsUrl);
      });

      runStep(STEP_PRIVACY, () -> {
        String privacyUrl = openLegalAndValidate(appPage, context, "Política de Privacidad",
            Arrays.asList("Política de Privacidad", "Politica de Privacidad"),
            "06_politica_privacidad");
        legalUrls.put(STEP_PRIVACY, privacyUrl);
      });

      if (session.browser != null) {
        session.browser.close();
      }
    } finally {
      printReport();
    }

    if (!failures.isEmpty()) {
      Assert.fail("SaleADS Mi Negocio workflow failed:\n- " + String.join("\n- ", failures));
    }
  }

  private void initializeReport() {
    report.put(STEP_LOGIN, "FAIL");
    report.put(STEP_MENU, "FAIL");
    report.put(STEP_MODAL, "FAIL");
    report.put(STEP_ADMIN, "FAIL");
    report.put(STEP_INFO_GENERAL, "FAIL");
    report.put(STEP_ACCOUNT_DETAILS, "FAIL");
    report.put(STEP_BUSINESSES, "FAIL");
    report.put(STEP_TERMS, "FAIL");
    report.put(STEP_PRIVACY, "FAIL");
  }

  private BrowserSession openSession(final Playwright playwright, final String saleadsUrl, final String cdpUrl,
      final boolean headless) {
    if (hasText(cdpUrl)) {
      Browser browser = playwright.chromium().connectOverCDP(cdpUrl);
      BrowserContext context = browser.contexts().isEmpty() ? browser.newContext() : browser.contexts().get(0);
      Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
      page.bringToFront();
      return new BrowserSession(browser, context, page);
    }

    Browser browser = playwright.chromium().launch(
        new BrowserType.LaunchOptions().setHeadless(headless).setChannel("chrome"));
    BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
    Page page = context.newPage();
    page.navigate(saleadsUrl);
    return new BrowserSession(browser, context, page);
  }

  private void loginWithGoogle(final Page appPage) {
    Locator googleLogin = firstVisible("Google login button",
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in with Google")),
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Iniciar sesión con Google")),
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Continuar con Google")),
        appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Sign in with Google")),
        appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Iniciar sesión con Google")),
        appPage.locator("button:has-text('Google'), a:has-text('Google'), [role='button']:has-text('Google')"));

    Page googlePopup = null;
    try {
      googlePopup = appPage.waitForPopup(() -> {
        googleLogin.click();
        waitForUiSettled(appPage);
      }, new Page.WaitForPopupOptions().setTimeout(8_000));
    } catch (PlaywrightException ignored) {
      waitForUiSettled(appPage);
    }

    if (googlePopup != null) {
      chooseGoogleAccountIfVisible(googlePopup);
      waitForUiSettled(googlePopup);
      try {
        googlePopup.waitForClose(new Page.WaitForCloseOptions().setTimeout(25_000));
      } catch (PlaywrightException ignored) {
        // Popup may remain open after login; the dashboard check below is the source of truth.
      }
    } else {
      chooseGoogleAccountIfVisible(appPage);
    }

    assertSidebarVisible(appPage);
    waitForUiSettled(appPage);
  }

  private void chooseGoogleAccountIfVisible(final Page page) {
    try {
      Locator account = page.getByText(GOOGLE_ACCOUNT_EMAIL,
          new Page.GetByTextOptions().setExact(true));
      account.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10_000));
      clickAndWait(page, account, "Google account selection");
    } catch (PlaywrightException ignored) {
      // If account chooser does not appear, continue with whichever session is already active.
    }
  }

  private void openMiNegocioMenu(final Page appPage) {
    Locator negocioSection = findVisibleByText(appPage, "Negocio", "Negocio section");
    clickAndWait(appPage, negocioSection, "Negocio section");
    clickAndWait(appPage, findVisibleByText(appPage, "Mi Negocio", "Mi Negocio option"), "Mi Negocio option");
  }

  private void ensureMiNegocioMenuExpanded(final Page appPage) {
    Locator administrar = appPage.getByText("Administrar Negocios",
        new Page.GetByTextOptions().setExact(true));
    try {
      administrar.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(1_500));
    } catch (PlaywrightException ignored) {
      openMiNegocioMenu(appPage);
    }
  }

  private String openLegalAndValidate(final Page appPage, final BrowserContext context, final String linkText,
      final List<String> validHeadings, final String screenshotPrefix) {
    Locator link = findVisibleByText(appPage, linkText, linkText + " link");
    Page legalPage;
    boolean newTab = false;

    try {
      legalPage = context.waitForPage(() -> {
        link.click();
        waitForUiSettled(appPage);
      }, new BrowserContext.WaitForPageOptions().setTimeout(8_000));
      newTab = true;
    } catch (PlaywrightException ignored) {
      clickAndWait(appPage, link, linkText + " link");
      legalPage = appPage;
    }

    waitForUiSettled(legalPage);
    assertAnyTextVisible(legalPage, validHeadings);

    String legalBody = legalPage.locator("body").innerText();
    assertTrue("Legal content text should be visible for " + linkText, legalBody != null && legalBody.trim().length() > 200);
    captureScreenshot(legalPage, screenshotPrefix, true);

    String finalUrl = legalPage.url();

    if (newTab) {
      legalPage.close();
      appPage.bringToFront();
      waitForUiSettled(appPage);
    } else {
      try {
        legalPage.goBack(new Page.GoBackOptions().setTimeout(20_000));
        waitForUiSettled(legalPage);
      } catch (PlaywrightException ignored) {
        // If navigation history is unavailable, keep current page and continue.
      }
    }

    return finalUrl;
  }

  private Locator findVisibleByText(final Page page, final String text, final String description) {
    return firstVisible(description,
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(text)),
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(text)),
        page.getByText(text, new Page.GetByTextOptions().setExact(true)),
        page.getByText(text));
  }

  private Locator firstVisible(final String description, final Locator... candidates) {
    for (Locator candidate : candidates) {
      int maxCandidates = 0;
      try {
        maxCandidates = (int) Math.min(candidate.count(), 6);
      } catch (PlaywrightException ignored) {
        continue;
      }

      for (int i = 0; i < maxCandidates; i++) {
        Locator option = candidate.nth(i);
        try {
          option.waitFor(new Locator.WaitForOptions()
              .setState(WaitForSelectorState.VISIBLE)
              .setTimeout(2_000));
          return option;
        } catch (PlaywrightException ignored) {
          // Try next candidate.
        }
      }
    }

    throw new AssertionError("Could not find visible element for: " + description);
  }

  private void clickAndWait(final Page page, final Locator target, final String actionName) {
    assertVisible(target, "Element should be visible before click: " + actionName);
    target.click();
    waitForUiSettled(page);
  }

  private void waitForUiSettled(final Page page) {
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(12_000));
    } catch (PlaywrightException ignored) {
      // Some SPAs keep network busy; proceed with a short UI settle wait.
    }
    page.waitForTimeout(700);
  }

  private void assertTextVisible(final Page page, final String text) {
    Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(true));
    assertVisible(locator.first(), "Text should be visible: " + text);
  }

  private void assertAnyTextVisible(final Page page, final List<String> candidates) {
    for (String text : candidates) {
      try {
        assertTextVisible(page, text);
        return;
      } catch (AssertionError ignored) {
        // Try next heading variant.
      }
    }
    throw new AssertionError("None of the expected headings are visible: " + candidates);
  }

  private void assertSidebarVisible(final Page page) {
    Locator sidebar = firstVisible("left sidebar navigation",
        page.locator("aside nav"),
        page.locator("aside"),
        page.getByRole(AriaRole.NAVIGATION),
        page.locator("[class*=sidebar i], [id*=sidebar i]"));
    assertVisible(sidebar, "Left sidebar navigation should be visible");
  }

  private String sectionText(final Page page, final String heading) {
    Locator headingLocator = findVisibleByText(page, heading, heading + " heading");
    Locator container = headingLocator.locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
    String text = "";
    try {
      text = container.innerText();
    } catch (PlaywrightException ignored) {
      // Fallback to full body text.
    }
    if (text == null || text.trim().length() < 10) {
      text = page.locator("body").innerText();
    }
    return text;
  }

  private void assertVisibleUserNameLikeText(final String sourceText) {
    String normalized = sourceText == null ? "" : sourceText.replace('\u00A0', ' ');
    String[] lines = normalized.split("\\R");
    for (String line : lines) {
      String value = line.trim();
      if (value.length() < 3 || value.length() > 80) {
        continue;
      }
      if (value.contains("@")) {
        continue;
      }
      String lower = value.toLowerCase();
      if (lower.contains("información general") || lower.contains("business plan") || lower.contains("cambiar plan")) {
        continue;
      }
      if (value.matches(".*\\p{L}.*")) {
        return;
      }
    }
    throw new AssertionError("User name-like text is not visible in Información General section");
  }

  private void assertContainsIgnoringCase(final String text, final String expected, final String message) {
    assertTrue(message, text != null && text.toLowerCase().contains(expected.toLowerCase()));
  }

  private void assertVisible(final Locator locator, final String message) {
    try {
      locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20_000));
    } catch (PlaywrightException ex) {
      throw new AssertionError(message, ex);
    }
  }

  private void runStep(final String stepName, final ThrowingRunnable step) {
    try {
      step.run();
      report.put(stepName, "PASS");
    } catch (Throwable t) {
      report.put(stepName, "FAIL");
      failures.add(stepName + " -> " + t.getMessage());
    }
  }

  private void captureScreenshot(final Page page, final String checkpoint, final boolean fullPage) {
    String filename = LocalDateTime.now().format(TS) + "_" + checkpoint + ".png";
    page.screenshot(new Page.ScreenshotOptions()
        .setPath(evidenceDir.resolve(filename))
        .setFullPage(fullPage));
  }

  private String readConfig(final String propertyKey, final String envKey) {
    String fromProperty = System.getProperty(propertyKey);
    if (hasText(fromProperty)) {
      return fromProperty.trim();
    }
    String fromEnv = System.getenv(envKey);
    return hasText(fromEnv) ? fromEnv.trim() : null;
  }

  private boolean hasText(final String value) {
    return value != null && !value.trim().isEmpty();
  }

  private void printReport() {
    System.out.println();
    System.out.println("=== SaleADS Mi Negocio Full Test Report ===");
    for (Map.Entry<String, String> entry : report.entrySet()) {
      System.out.printf("%s: %s%n", entry.getKey(), entry.getValue());
    }
    if (!legalUrls.isEmpty()) {
      System.out.println("--- Legal URLs ---");
      for (Map.Entry<String, String> entry : legalUrls.entrySet()) {
        System.out.printf("%s URL: %s%n", entry.getKey(), entry.getValue());
      }
    }
    System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
    System.out.println("==========================================");
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  private static final class BrowserSession {
    private final Browser browser;
    private final BrowserContext context;
    private final Page page;

    private BrowserSession(final Browser browser, final BrowserContext context, final Page page) {
      this.browser = browser;
      this.context = context;
      this.page = page;
    }
  }
}
