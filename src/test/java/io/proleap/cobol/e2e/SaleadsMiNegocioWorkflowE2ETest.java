package io.proleap.cobol.e2e;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
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

public class SaleadsMiNegocioWorkflowE2ETest {

  private static final String DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
  private static final long SHORT_WAIT_MS = 750L;

  private Playwright playwright;
  private Browser browser;
  private BrowserContext context;
  private Page appPage;
  private Path artifactsDir;
  private final Map<String, Boolean> stepResults = new LinkedHashMap<>();
  private final Map<String, String> evidence = new LinkedHashMap<>();

  @Before
  public void setup() {
    final String loginUrl = env("SALEADS_LOGIN_URL");
    Assume.assumeTrue(
        "SALEADS_LOGIN_URL is required and must point to the current environment login page.",
        loginUrl != null && !loginUrl.isBlank());

    playwright = Playwright.create();
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
        .setHeadless(Boolean.parseBoolean(envOrDefault("SALEADS_HEADLESS", "true")))
        .setArgs(List.of("--disable-dev-shm-usage")));
    context = browser.newContext();
    appPage = context.newPage();
    appPage.setDefaultTimeout(Long.parseLong(envOrDefault("SALEADS_TIMEOUT_MS", "20000")));

    artifactsDir = Paths.get("target", "saleads-mi-negocio-artifacts",
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
    createDirectories(artifactsDir);

    appPage.navigate(loginUrl);
    waitForUi(appPage);
  }

  @After
  public void teardown() {
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
    final String googleEmail = envOrDefault("SALEADS_GOOGLE_EMAIL", DEFAULT_GOOGLE_EMAIL);

    stepResults.put("Login", stepLoginWithGoogle(googleEmail));
    stepResults.put("Mi Negocio menu", stepOpenMiNegocioMenu());
    stepResults.put("Agregar Negocio modal", stepValidateAgregarNegocioModal());
    stepResults.put("Administrar Negocios view", stepOpenAdministrarNegociosView());
    stepResults.put("Informacion General", stepValidateInformacionGeneral());
    stepResults.put("Detalles de la Cuenta", stepValidateDetallesCuenta());
    stepResults.put("Tus Negocios", stepValidateTusNegocios());
    stepResults.put("Terminos y Condiciones", stepValidateLegalLink("Términos y Condiciones",
        new String[] {"Términos y Condiciones", "Terminos y Condiciones"}, "08-terminos-y-condiciones.png",
        "Terminos y Condiciones URL"));
    stepResults.put("Politica de Privacidad",
        stepValidateLegalLink("Política de Privacidad", new String[] {"Política de Privacidad", "Politica de Privacidad"},
            "09-politica-de-privacidad.png", "Politica de Privacidad URL"));

    writeReport();
    final boolean allPassed = stepResults.values().stream().allMatch(Boolean::booleanValue);
    Assert.assertTrue("One or more SaleADS Mi Negocio workflow validations failed. See "
        + artifactsDir.resolve("final-report.txt"), allPassed);
  }

  private boolean stepLoginWithGoogle(final String googleEmail) {
    boolean passed = true;

    passed &= clickVisibleText(appPage, "Sign in with Google", "Iniciar sesión con Google", "Iniciar sesion con Google",
        "Continuar con Google", "Google");
    waitForUi(appPage);

    selectGoogleAccountIfVisible(googleEmail);

    final boolean mainUiVisible = waitForAnyVisibleText(appPage, 25000, "Negocio", "Mi Negocio");
    final boolean leftSidebarVisible =
        isVisible(appPage.locator("aside").first()) || isVisible(appPage.locator("nav").first());
    passed &= mainUiVisible && leftSidebarVisible;

    screenshot(appPage, "01-dashboard-loaded.png", false);
    return passed;
  }

  private boolean stepOpenMiNegocioMenu() {
    boolean passed = true;

    // Expand Negocio section if needed, then open Mi Negocio.
    clickVisibleText(appPage, "Negocio");
    waitForUi(appPage);
    passed &= clickVisibleText(appPage, "Mi Negocio");
    waitForUi(appPage);

    final boolean agregarVisible = waitForAnyVisibleText(appPage, 10000, "Agregar Negocio");
    final boolean administrarVisible = waitForAnyVisibleText(appPage, 10000, "Administrar Negocios");
    passed &= agregarVisible && administrarVisible;

    screenshot(appPage, "02-mi-negocio-expanded-menu.png", false);
    return passed;
  }

  private boolean stepValidateAgregarNegocioModal() {
    boolean passed = true;

    passed &= clickVisibleText(appPage, "Agregar Negocio");
    waitForUi(appPage);

    final boolean modalTitleVisible = waitForAnyVisibleText(appPage, 10000, "Crear Nuevo Negocio");
    final boolean nombreFieldVisible = isVisible(appPage.locator(
        "input[placeholder*='Nombre del Negocio'], input[name*='nombre'], input[id*='nombre']").first());
    final boolean limitTextVisible = isAnyVisibleText(appPage, "Tienes 2 de 3 negocios");
    final boolean cancelVisible = isAnyVisibleText(appPage, "Cancelar");
    final boolean createVisible = isAnyVisibleText(appPage, "Crear Negocio");

    passed &= modalTitleVisible && nombreFieldVisible && limitTextVisible && cancelVisible && createVisible;

    if (nombreFieldVisible) {
      final Locator field = appPage.locator(
          "input[placeholder*='Nombre del Negocio'], input[name*='nombre'], input[id*='nombre']").first();
      field.click();
      field.fill("Negocio Prueba Automatizacion");
      waitForUi(appPage);
    }

    screenshot(appPage, "03-agregar-negocio-modal.png", false);

    if (cancelVisible) {
      clickVisibleText(appPage, "Cancelar");
      waitForUi(appPage);
    }

    return passed;
  }

  private boolean stepOpenAdministrarNegociosView() {
    boolean passed = true;

    // Ensure submenu is visible before clicking Administrar Negocios.
    clickVisibleText(appPage, "Mi Negocio");
    waitForUi(appPage);
    passed &= clickVisibleText(appPage, "Administrar Negocios");
    waitForUi(appPage);

    final boolean infoGeneralVisible = waitForAnyVisibleText(appPage, 15000, "Información General", "Informacion General");
    final boolean detallesCuentaVisible =
        waitForAnyVisibleText(appPage, 15000, "Detalles de la Cuenta", "Detalles de la cuenta");
    final boolean tusNegociosVisible = waitForAnyVisibleText(appPage, 15000, "Tus Negocios");
    final boolean legalVisible = waitForAnyVisibleText(appPage, 15000, "Sección Legal", "Seccion Legal");

    passed &= infoGeneralVisible && detallesCuentaVisible && tusNegociosVisible && legalVisible;

    screenshot(appPage, "04-administrar-negocios-full-page.png", true);
    return passed;
  }

  private boolean stepValidateInformacionGeneral() {
    final boolean nameVisible = isVisible(appPage.locator("[data-testid*='name'], [class*='name']").first())
        || isAnyVisibleText(appPage, "Información General", "Informacion General");
    final boolean emailVisible = isVisible(appPage.locator("text=/@/").first());
    final boolean planVisible = isAnyVisibleText(appPage, "BUSINESS PLAN");
    final boolean cambiarPlanVisible = isAnyVisibleText(appPage, "Cambiar Plan");

    return nameVisible && emailVisible && planVisible && cambiarPlanVisible;
  }

  private boolean stepValidateDetallesCuenta() {
    final boolean cuentaCreadaVisible = isAnyVisibleText(appPage, "Cuenta creada");
    final boolean estadoActivoVisible = isAnyVisibleText(appPage, "Estado activo");
    final boolean idiomaVisible = isAnyVisibleText(appPage, "Idioma seleccionado");

    return cuentaCreadaVisible && estadoActivoVisible && idiomaVisible;
  }

  private boolean stepValidateTusNegocios() {
    final boolean listVisible = isVisible(appPage.locator("text=/Negocio|Negocios/").first());
    final boolean agregarButtonVisible = isAnyVisibleText(appPage, "Agregar Negocio");
    final boolean limitTextVisible = isAnyVisibleText(appPage, "Tienes 2 de 3 negocios");

    return listVisible && agregarButtonVisible && limitTextVisible;
  }

  private boolean stepValidateLegalLink(final String linkText, final String[] headingCandidates, final String screenshotName,
      final String evidenceKey) {
    final String originUrl = appPage.url();
    final int pagesBefore = context.pages().size();
    boolean passed = clickVisibleText(appPage, linkText);
    waitForUi(appPage);

    final Page legalPage = detectNewestPage(pagesBefore);
    if (legalPage != appPage) {
      legalPage.bringToFront();
      waitForUi(legalPage);
    }

    final boolean headingVisible = waitForAnyVisibleText(legalPage, 15000, headingCandidates);
    final boolean legalTextVisible = legalPage.locator("p,li,article,section,main").count() > 0;
    passed &= headingVisible && legalTextVisible;

    screenshot(legalPage, screenshotName, true);
    evidence.put(evidenceKey, legalPage.url());

    if (legalPage != appPage) {
      legalPage.close();
      appPage.bringToFront();
      waitForUi(appPage);
    } else {
      returnToApplication(originUrl);
    }

    return passed;
  }

  private void selectGoogleAccountIfVisible(final String googleEmail) {
    for (final Page page : context.pages()) {
      if (isAnyVisibleText(page, googleEmail)) {
        clickVisibleText(page, googleEmail);
        waitForUi(page);
      }
    }

    // Wait for app shell to appear in the application page after account selection.
    waitForAnyVisibleText(appPage, 25000, "Negocio", "Mi Negocio");
  }

  private void waitForUi(final Page page) {
    try {
      page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    } catch (final PlaywrightException ignored) {
      // Ignore and continue with best effort waits.
    }
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE);
    } catch (final PlaywrightException ignored) {
      // Ignore and continue with best effort waits.
    }
    page.waitForTimeout(SHORT_WAIT_MS);
  }

  private void returnToApplication(final String originUrl) {
    if (!appPage.url().equals(originUrl)) {
      appPage.goBack();
      waitForUi(appPage);
    }
  }

  private Page detectNewestPage(final int pagesBefore) {
    final long maxWaitMs = 7000L;
    final long pollMs = 200L;
    long waited = 0L;

    while (waited < maxWaitMs) {
      final List<Page> pages = context.pages();
      if (pages.size() > pagesBefore) {
        return pages.get(pages.size() - 1);
      }
      sleep(pollMs);
      waited += pollMs;
    }

    return appPage;
  }

  private boolean clickVisibleText(final Page page, final String... candidates) {
    for (final String text : candidates) {
      final Locator exact = page.getByText(text, new Page.GetByTextOptions().setExact(true)).first();
      if (isVisible(exact)) {
        exact.click();
        waitForUi(page);
        return true;
      }

      final Locator partial = page.getByText(text).first();
      if (isVisible(partial)) {
        partial.click();
        waitForUi(page);
        return true;
      }
    }

    return false;
  }

  private boolean waitForAnyVisibleText(final Page page, final double timeoutMs, final String... candidates) {
    final double perCandidateTimeout = Math.max(1000D, timeoutMs / Math.max(1, candidates.length));
    for (final String text : candidates) {
      final Locator exact = page.getByText(text, new Page.GetByTextOptions().setExact(true)).first();
      try {
        exact.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(perCandidateTimeout));
        return true;
      } catch (final PlaywrightException ignored) {
        // Try next candidate.
      }

      final Locator partial = page.getByText(text).first();
      try {
        partial.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(perCandidateTimeout));
        return true;
      } catch (final PlaywrightException ignored) {
        // Try next candidate.
      }
    }

    return false;
  }

  private boolean isAnyVisibleText(final Page page, final String... candidates) {
    for (final String text : candidates) {
      final Locator exact = page.getByText(text, new Page.GetByTextOptions().setExact(true)).first();
      if (isVisible(exact)) {
        return true;
      }

      final Locator partial = page.getByText(text).first();
      if (isVisible(partial)) {
        return true;
      }
    }
    return false;
  }

  private boolean isVisible(final Locator locator) {
    try {
      return locator != null && locator.count() > 0 && locator.isVisible();
    } catch (final PlaywrightException ignored) {
      return false;
    }
  }

  private void screenshot(final Page page, final String fileName, final boolean fullPage) {
    page.screenshot(new Page.ScreenshotOptions()
        .setPath(artifactsDir.resolve(fileName))
        .setFullPage(fullPage));
  }

  private void writeReport() {
    final StringBuilder report = new StringBuilder();
    report.append("SaleADS Mi Negocio Full Workflow Report").append(System.lineSeparator());
    report.append("Artifacts directory: ").append(artifactsDir).append(System.lineSeparator()).append(System.lineSeparator());

    for (final Map.Entry<String, Boolean> entry : stepResults.entrySet()) {
      report.append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL").append(System.lineSeparator());
    }

    if (!evidence.isEmpty()) {
      report.append(System.lineSeparator()).append("Evidence").append(System.lineSeparator());
      for (final Map.Entry<String, String> entry : evidence.entrySet()) {
        report.append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
      }
    }

    System.out.println(report);
    writeFile(artifactsDir.resolve("final-report.txt"), report.toString());
  }

  private static void writeFile(final Path file, final String content) {
    try {
      Files.writeString(file, content);
    } catch (final IOException e) {
      throw new UncheckedIOException("Unable to write report file: " + file, e);
    }
  }

  private static void createDirectories(final Path path) {
    try {
      Files.createDirectories(path);
    } catch (final IOException e) {
      throw new UncheckedIOException("Unable to create artifacts directory: " + path, e);
    }
  }

  private static void sleep(final long millis) {
    try {
      Thread.sleep(millis);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for popup page", e);
    }
  }

  private static String env(final String key) {
    return System.getenv(key);
  }

  private static String envOrDefault(final String key, final String defaultValue) {
    final String value = env(key);
    return value == null || value.isBlank() ? defaultValue : value;
  }
}
