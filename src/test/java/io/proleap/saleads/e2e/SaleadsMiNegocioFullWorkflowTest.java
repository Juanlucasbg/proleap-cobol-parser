package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;

/**
 * End-to-end UI workflow for SaleADS Mi Negocio module.
 *
 * <p>
 * This test is intentionally URL-agnostic:
 * <ul>
 * <li>It assumes the login page is already open by default.</li>
 * <li>You can set SALEADS_BASE_URL to force navigation if needed.</li>
 * </ul>
 */
public class SaleadsMiNegocioFullWorkflowTest {

  private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
  private static final String TEST_NAME = "saleads_mi_negocio_full_test";
  private static final long DEFAULT_UI_WAIT_MS = 10_000;
  private static final long LONG_WAIT_MS = 20_000;

  private Playwright playwright;
  private Browser browser;
  private BrowserContext context;
  private Page page;

  private Path outputDir;
  private final Map<String, Boolean> report = new LinkedHashMap<>();
  private final Map<String, String> reportDetails = new LinkedHashMap<>();
  private final Map<String, String> evidence = new LinkedHashMap<>();

  @Before
  public void setUp() throws IOException {
    outputDir = createOutputDir();

    playwright = Playwright.create();
    BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
        .setHeadless(getBooleanEnv("SALEADS_HEADLESS", true))
        .setSlowMo(getLongEnv("SALEADS_SLOW_MO_MS", 250L));
    browser = playwright.chromium().launch(launchOptions);

    Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
        .setViewportSize(1600, 1000);
    context = browser.newContext(contextOptions);
    page = context.newPage();

    String baseUrl = System.getenv("SALEADS_BASE_URL");
    if (baseUrl != null && !baseUrl.isBlank()) {
      page.navigate(baseUrl.trim());
      waitForUiToLoad(page);
    }
  }

  @After
  public void tearDown() throws IOException {
    writeReportJson();
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
  public void runMiNegocioWorkflow() {
    // 1) Login
    runStep("Login", this::stepLoginWithGoogle);

    // 2) Open Mi Negocio menu
    runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);

    // 3) Validate Agregar Negocio modal
    runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);

    // 4) Open Administrar Negocios
    runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);

    // 5) Validate Informacion General
    runStep("Información General", this::stepValidateInformacionGeneral);

    // 6) Validate Detalles de la Cuenta
    runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);

    // 7) Validate Tus Negocios
    runStep("Tus Negocios", this::stepValidateTusNegocios);

    // 8) Validate Terminos y Condiciones
    runStep("Términos y Condiciones", this::stepValidateTerminos);

    // 9) Validate Politica de Privacidad
    runStep("Política de Privacidad", this::stepValidatePrivacidad);

    assertAllStepsPassed();
  }

  private void stepLoginWithGoogle() {
    if (isBlankPage(page)) {
      throw new IllegalStateException(
          "No login page loaded. Provide SALEADS_BASE_URL or open SaleADS login before running.");
    }

    // Assumes login page is already open in current environment.
    Page googlePage = clickByTextAnyAndCapturePopup(
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Continuar con Google",
        "Google");
    waitForUiToLoad(googlePage);

    // If account selector appears, choose requested account.
    chooseGoogleAccountIfPresent(googlePage);
    waitForUiToLoad(googlePage);

    Page appPage = waitForAnyPageWithTexts("Mi Negocio", "Negocio", "Dashboard", "Inicio");
    if (appPage != null) {
      page = appPage;
      page.bringToFront();
      waitForUiToLoad(page);
    }

    expectVisibleAny("main application interface",
        "Negocio", "Mi Negocio", "Dashboard", "Inicio");
    expectVisibleAny("left sidebar navigation",
        "Mi Negocio", "Negocio", "Administrar Negocios");

    screenshot("01_dashboard_loaded.png", true);
  }

  private void stepOpenMiNegocioMenu() {
    clickByTextAny("Negocio", "Mi Negocio");
    waitForUiToLoad(page);

    expectVisibleText("Agregar Negocio");
    expectVisibleText("Administrar Negocios");
    screenshot("02_mi_negocio_menu_expanded.png", true);
  }

  private void stepValidateAgregarNegocioModal() {
    clickByTextAny("Agregar Negocio");
    waitForUiToLoad(page);

    expectVisibleAny("Crear Nuevo Negocio modal title", "Crear Nuevo Negocio");
    expectVisibleAny("Nombre del Negocio field", "Nombre del Negocio");
    expectVisibleAny("business quota text", "Tienes 2 de 3 negocios");
    expectVisibleAny("Cancelar button", "Cancelar");
    expectVisibleAny("Crear Negocio button", "Crear Negocio");
    screenshot("03_agregar_negocio_modal.png", true);

    // Optional interaction requested by workflow.
    fillIfVisible("Nombre del Negocio", "Negocio Prueba Automatización");
    clickByTextAny("Cancelar", "Close", "Cerrar");
    waitForUiToLoad(page);
  }

  private void stepOpenAdministrarNegocios() {
    ensureMiNegocioExpanded();
    clickByTextAny("Administrar Negocios");
    waitForUiToLoad(page);

    expectVisibleText("Información General");
    expectVisibleText("Detalles de la Cuenta");
    expectVisibleText("Tus Negocios");
    expectVisibleAny("Sección Legal", "Sección Legal", "Legal");
    screenshot("04_administrar_negocios_account_page.png", true);
  }

  private void stepValidateInformacionGeneral() {
    // User name and email vary by account/environment; validate using robust patterns.
    expectVisibleRegex("user email", Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"));
    expectVisibleAny("user name", "Juan", "Perfil", "Cuenta", "Usuario");
    expectVisibleAny("BUSINESS PLAN text", "BUSINESS PLAN", "Business Plan");
    expectVisibleAny("Cambiar Plan button", "Cambiar Plan");
  }

  private void stepValidateDetallesCuenta() {
    expectVisibleAny("Cuenta creada", "Cuenta creada");
    expectVisibleAny("Estado activo", "Estado activo", "Activa", "Activo");
    expectVisibleAny("Idioma seleccionado", "Idioma seleccionado", "Idioma");
  }

  private void stepValidateTusNegocios() {
    expectVisibleAny("business list", "Tus Negocios", "Negocio");
    expectVisibleAny("Agregar Negocio button", "Agregar Negocio");
    expectVisibleAny("business quota text", "Tienes 2 de 3 negocios");
  }

  private void stepValidateTerminos() {
    Page target = clickLegalLinkAndSwitch("Términos y Condiciones");

    expectVisibleInPage(target, "Términos y Condiciones");
    expectLegalContentVisible(target);

    String finalUrl = target.url();
    evidence.put("Términos y Condiciones URL", finalUrl);
    screenshot(target, "05_terminos_y_condiciones.png", true);

    returnToAppTab(target);
  }

  private void stepValidatePrivacidad() {
    Page target = clickLegalLinkAndSwitch("Política de Privacidad");

    expectVisibleInPage(target, "Política de Privacidad");
    expectLegalContentVisible(target);

    String finalUrl = target.url();
    evidence.put("Política de Privacidad URL", finalUrl);
    screenshot(target, "06_politica_de_privacidad.png", true);

    returnToAppTab(target);
  }

  private void runStep(final String stepName, final Runnable stepAction) {
    Instant start = Instant.now();
    try {
      stepAction.run();
      report.put(stepName, true);
      reportDetails.put(stepName, "PASS");
    } catch (Throwable t) {
      report.put(stepName, false);
      String message = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
      reportDetails.put(stepName, "FAIL: " + message);
      screenshot("FAIL_" + sanitizeFileName(stepName) + ".png", true);
    } finally {
      Duration elapsed = Duration.between(start, Instant.now());
      evidence.put(stepName + " Duration", elapsed.toMillis() + "ms");
    }
  }

  private void assertAllStepsPassed() {
    StringBuilder failures = new StringBuilder();
    for (Map.Entry<String, Boolean> entry : report.entrySet()) {
      if (!entry.getValue()) {
        failures.append(System.lineSeparator())
            .append("- ")
            .append(entry.getKey())
            .append(": ")
            .append(reportDetails.get(entry.getKey()));
      }
    }
    assertTrue("One or more steps failed:" + failures, failures.length() == 0);
  }

  private void clickByTextAny(final String... options) {
    for (String option : options) {
      Locator locator = page.getByText(option, new Page.GetByTextOptions().setExact(false)).first();
      if (locator.isVisible(new Locator.IsVisibleOptions().setTimeout(1500))) {
        locator.click();
        waitForUiToLoad(page);
        return;
      }
    }
    throw new IllegalStateException("Could not click any option: " + String.join(", ", options));
  }

  private Page clickByTextAnyAndCapturePopup(final String... options) {
    for (String option : options) {
      Locator locator = page.getByText(option, new Page.GetByTextOptions().setExact(false)).first();
      if (locator.isVisible(new Locator.IsVisibleOptions().setTimeout(1500))) {
        try {
          Page popup = page.waitForPopup(() -> locator.click());
          waitForUiToLoad(popup);
          return popup;
        } catch (PlaywrightException ignored) {
          locator.click();
          waitForUiToLoad(page);
          return page;
        }
      }
    }
    throw new IllegalStateException("Could not click any option: " + String.join(", ", options));
  }

  private void expectVisibleText(final String text) {
    Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(false)).first();
    locator.waitFor(new Locator.WaitForOptions()
        .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
        .setTimeout(LONG_WAIT_MS));
  }

  private void expectVisibleAny(final String label, final String... options) {
    for (String option : options) {
      Locator locator = page.getByText(option, new Page.GetByTextOptions().setExact(false)).first();
      if (locator.isVisible(new Locator.IsVisibleOptions().setTimeout(1500))) {
        return;
      }
    }
    throw new IllegalStateException("Expected visible " + label + " using options: " + String.join(", ", options));
  }

  private void expectVisibleRegex(final String label, final Pattern pattern) {
    String content = page.content();
    if (!pattern.matcher(content).find()) {
      throw new IllegalStateException("Expected " + label + " matching regex: " + pattern.pattern());
    }
  }

  private void fillIfVisible(final String fieldLabel, final String value) {
    Locator inputByLabel = page.getByLabel(fieldLabel, new Page.GetByLabelOptions().setExact(false)).first();
    if (inputByLabel.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
      inputByLabel.fill(value);
      return;
    }
    Locator inputByPlaceholder = page.getByPlaceholder(fieldLabel, new Page.GetByPlaceholderOptions().setExact(false)).first();
    if (inputByPlaceholder.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
      inputByPlaceholder.fill(value);
    }
  }

  private void ensureMiNegocioExpanded() {
    Locator administrar = page.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(false)).first();
    if (!administrar.isVisible(new Locator.IsVisibleOptions().setTimeout(1500))) {
      clickByTextAny("Mi Negocio", "Negocio");
      waitForUiToLoad(page);
    }
  }

  private Page clickLegalLinkAndSwitch(final String linkText) {
    Locator link = page.getByText(linkText, new Page.GetByTextOptions().setExact(false)).first();
    link.waitFor(new Locator.WaitForOptions()
        .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
        .setTimeout(LONG_WAIT_MS));

    Page popup = null;
    try {
      popup = page.waitForPopup(() -> link.click());
    } catch (PlaywrightException ignored) {
      link.click();
      waitForUiToLoad(page);
    }

    Page target = popup != null ? popup : page;
    waitForUiToLoad(target);
    return target;
  }

  private void expectVisibleInPage(final Page target, final String text) {
    Locator locator = target.getByText(text, new Page.GetByTextOptions().setExact(false)).first();
    locator.waitFor(new Locator.WaitForOptions()
        .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE)
        .setTimeout(LONG_WAIT_MS));
  }

  private void expectLegalContentVisible(final Page target) {
    String content = target.content().toLowerCase(Locale.ROOT);
    boolean hasLegalText = content.contains("término")
        || content.contains("termino")
        || content.contains("privacidad")
        || content.contains("datos")
        || content.contains("legal");
    if (!hasLegalText) {
      throw new IllegalStateException("Could not validate legal content text in legal page.");
    }
  }

  private void returnToAppTab(final Page target) {
    if (target != page) {
      target.close();
    }
    page.bringToFront();
    waitForUiToLoad(page);
  }

  private void chooseGoogleAccountIfPresent(final Page targetPage) {
    Locator account = targetPage.getByText(GOOGLE_ACCOUNT, new Page.GetByTextOptions().setExact(false)).first();
    if (account.isVisible(new Locator.IsVisibleOptions().setTimeout(5000))) {
      account.click();
      waitForUiToLoad(targetPage);
    }
  }

  private Page waitForAnyPageWithTexts(final String... options) {
    Instant deadline = Instant.now().plusMillis(LONG_WAIT_MS);
    while (Instant.now().isBefore(deadline)) {
      List<Page> pages = new ArrayList<>(context.pages());
      for (Page candidate : pages) {
        if (candidate.isClosed()) {
          continue;
        }
        if (isAnyTextVisible(candidate, options)) {
          return candidate;
        }
      }
      page.waitForTimeout(500);
    }
    return null;
  }

  private boolean isAnyTextVisible(final Page target, final String... options) {
    for (String option : options) {
      try {
        Locator locator = target.getByText(option, new Page.GetByTextOptions().setExact(false)).first();
        if (locator.isVisible(new Locator.IsVisibleOptions().setTimeout(750))) {
          return true;
        }
      } catch (PlaywrightException ignored) {
        // Continue scanning candidate pages.
      }
    }
    return false;
  }

  private boolean isBlankPage(final Page target) {
    String url = target.url();
    return url == null || url.isBlank() || "about:blank".equalsIgnoreCase(url.trim());
  }

  private void waitForUiToLoad(final Page target) {
    try {
      target.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_UI_WAIT_MS));
    } catch (TimeoutError ignored) {
      // Some SPA transitions do not trigger a new load state.
    }
    target.waitForTimeout(500);
  }

  private void screenshot(final String fileName, final boolean fullPage) {
    screenshot(page, fileName, fullPage);
  }

  private void screenshot(final Page target, final String fileName, final boolean fullPage) {
    Path shotPath = outputDir.resolve(fileName);
    target.screenshot(new Page.ScreenshotOptions()
        .setPath(shotPath)
        .setFullPage(fullPage));
    evidence.put("screenshot:" + fileName, shotPath.toAbsolutePath().toString());
  }

  private void writeReportJson() throws IOException {
    Path reportPath = outputDir.resolve("final_report.json");
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    sb.append("  \"name\": \"").append(TEST_NAME).append("\",\n");
    sb.append("  \"results\": {\n");

    int index = 0;
    for (Map.Entry<String, Boolean> entry : report.entrySet()) {
      String key = escapeJson(entry.getKey());
      String status = entry.getValue() ? "PASS" : "FAIL";
      String detail = escapeJson(reportDetails.getOrDefault(entry.getKey(), status));
      sb.append("    \"").append(key).append("\": { \"status\": \"").append(status).append("\", \"detail\": \"").append(detail).append("\" }");
      if (++index < report.size()) {
        sb.append(",");
      }
      sb.append("\n");
    }

    sb.append("  },\n");
    sb.append("  \"evidence\": {\n");
    int eIndex = 0;
    for (Map.Entry<String, String> entry : evidence.entrySet()) {
      sb.append("    \"").append(escapeJson(entry.getKey())).append("\": \"")
          .append(escapeJson(entry.getValue())).append("\"");
      if (++eIndex < evidence.size()) {
        sb.append(",");
      }
      sb.append("\n");
    }
    sb.append("  }\n");
    sb.append("}\n");

    try (FileWriter writer = new FileWriter(reportPath.toFile())) {
      writer.write(sb.toString());
    }
  }

  private static boolean getBooleanEnv(final String name, final boolean defaultValue) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    return Boolean.parseBoolean(value.trim());
  }

  private static long getLongEnv(final String name, final long defaultValue) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException ex) {
      return defaultValue;
    }
  }

  private static Path createOutputDir() throws IOException {
    String custom = System.getenv("SALEADS_OUTPUT_DIR");
    Path base = custom == null || custom.isBlank()
        ? Paths.get("target", "saleads-e2e")
        : Paths.get(custom);
    Files.createDirectories(base);

    Path testDir = base.resolve(String.valueOf(System.currentTimeMillis()));
    Files.createDirectories(testDir);
    return testDir;
  }

  private static String sanitizeFileName(final String value) {
    return value.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  private static String escapeJson(final String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
