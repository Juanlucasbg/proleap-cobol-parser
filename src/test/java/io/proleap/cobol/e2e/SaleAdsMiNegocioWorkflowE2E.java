package io.proleap.cobol.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
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
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * End-to-end validation for the SaleADS Mi Negocio workflow.
 *
 * <p>This runner is intentionally not named *Test to avoid executing in default CI builds.
 * Run manually with an environment-specific URL:
 *
 * <pre>
 * SALEADS_LOGIN_URL="https://your-environment/login" mvn -DskipTests test-compile
 * mvn -Dexec.mainClass="io.proleap.cobol.e2e.SaleAdsMiNegocioWorkflowE2E" -Dexec.classpathScope=test exec:java
 * </pre>
 */
public class SaleAdsMiNegocioWorkflowE2E {

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
  private static final int DEFAULT_TIMEOUT_MS =
      Integer.parseInt(System.getenv().getOrDefault("SALEADS_TIMEOUT_MS", "30000"));
  private static final String DEFAULT_GOOGLE_EMAIL =
      System.getenv().getOrDefault("SALEADS_GOOGLE_EMAIL", "juanlucasbarbiergarzon@gmail.com");
  private static final List<String> REPORT_FIELDS =
      List.of(
          "Login",
          "Mi Negocio menu",
          "Agregar Negocio modal",
          "Administrar Negocios view",
          "Información General",
          "Detalles de la Cuenta",
          "Tus Negocios",
          "Términos y Condiciones",
          "Política de Privacidad");

  private final Path evidenceDir;
  private final Map<String, StepResult> report = new LinkedHashMap<>();
  private BrowserContext context;
  private Page appPage;
  private String administrarNegociosUrl;
  private String terminosUrl = "N/A";
  private String privacidadUrl = "N/A";

  public SaleAdsMiNegocioWorkflowE2E() throws IOException {
    this.evidenceDir = createEvidenceDirectory();
    REPORT_FIELDS.forEach(field -> report.put(field, new StepResult("FAIL", "Step not executed")));
  }

  public static void main(final String[] args) throws Exception {
    new SaleAdsMiNegocioWorkflowE2E().run();
  }

  public void run() throws IOException {
    final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
    if (isBlank(loginUrl)) {
      throw new IllegalStateException(
          "SALEADS_LOGIN_URL is required. Use the environment login URL (dev/staging/prod).");
    }

    final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));
    final double slowMoMs = Double.parseDouble(System.getenv().getOrDefault("SALEADS_SLOW_MO_MS", "0"));

    try (Playwright playwright = Playwright.create()) {
      final BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions().setHeadless(headless);
      if (slowMoMs > 0) {
        launchOptions.setSlowMo(slowMoMs);
      }

      final Browser browser = playwright.chromium().launch(launchOptions);
      this.context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 900));
      context.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
      context.setDefaultNavigationTimeout(DEFAULT_TIMEOUT_MS);
      this.appPage = context.newPage();
      appPage.navigate(loginUrl);
      waitForUiToLoad(appPage);

      runStep("Login", this::stepLogin);
      runStep("Mi Negocio menu", this::stepMiNegocioMenu);
      runStep("Agregar Negocio modal", this::stepAgregarNegocioModal);
      runStep("Administrar Negocios view", this::stepAdministrarNegocios);
      runStep("Información General", this::stepInformacionGeneral);
      runStep("Detalles de la Cuenta", this::stepDetallesCuenta);
      runStep("Tus Negocios", this::stepTusNegocios);
      runStep("Términos y Condiciones", this::stepTerminos);
      runStep("Política de Privacidad", this::stepPrivacidad);
    } finally {
      writeFinalReport();
    }

    final List<String> failedFields = new ArrayList<>();
    for (Map.Entry<String, StepResult> entry : report.entrySet()) {
      if (!"PASS".equals(entry.getValue().status)) {
        failedFields.add(entry.getKey());
      }
    }
    if (!failedFields.isEmpty()) {
      throw new AssertionError("SaleADS Mi Negocio workflow failed in: " + failedFields);
    }
  }

  private void stepLogin() {
    clickFirstVisibleByText(
        appPage,
        List.of(
            "Sign in with Google",
            "Login with Google",
            "Iniciar sesión con Google",
            "Continuar con Google",
            "Acceder con Google"));

    handleGoogleAccountSelectorIfPresent(DEFAULT_GOOGLE_EMAIL);
    waitForMainInterface();

    if (!isSidebarVisible(appPage)) {
      throw new AssertionError("Sidebar navigation is not visible after login.");
    }
    takeScreenshot(appPage, "01-dashboard-loaded", true);
  }

  private void stepMiNegocioMenu() {
    if (!containsAnyVisibleText(appPage, List.of("Mi Negocio", "Negocio"))) {
      throw new AssertionError("Mi Negocio/Negocio option is not visible in the sidebar.");
    }

    if (containsVisibleText(appPage, "Negocio")) {
      clickFirstVisibleByText(appPage, List.of("Negocio"));
    }
    clickFirstVisibleByText(appPage, List.of("Mi Negocio"));

    assertVisibleText(appPage, "Agregar Negocio");
    assertVisibleText(appPage, "Administrar Negocios");
    takeScreenshot(appPage, "02-mi-negocio-menu-expanded", true);
  }

  private void stepAgregarNegocioModal() {
    clickFirstVisibleByText(appPage, List.of("Agregar Negocio"));

    assertVisibleText(appPage, "Crear Nuevo Negocio");
    assertVisibleText(appPage, "Nombre del Negocio");
    assertVisibleText(appPage, "Tienes 2 de 3 negocios");
    assertVisibleText(appPage, "Cancelar");
    assertVisibleText(appPage, "Crear Negocio");
    takeScreenshot(appPage, "03-agregar-negocio-modal", true);

    final Locator nameField =
        appPage
            .locator(
                "input[placeholder*='Nombre del Negocio'], input[name*='nombre'], input[id*='nombre']")
            .first();
    if (isVisible(nameField, 3000)) {
      nameField.fill("Negocio Prueba Automatización");
    }
    clickFirstVisibleByText(appPage, List.of("Cancelar"));

    final Locator modalTitle = appPage.getByText(Pattern.compile("(?iu)Crear Nuevo Negocio")).first();
    try {
      modalTitle.waitFor(
          new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN).setTimeout(5000));
    } catch (TimeoutError ignored) {
      // The modal may animate out a bit longer depending on environment.
    }
  }

  private void stepAdministrarNegocios() {
    if (!containsVisibleText(appPage, "Administrar Negocios")) {
      clickFirstVisibleByText(appPage, List.of("Mi Negocio"));
    }
    clickFirstVisibleByText(appPage, List.of("Administrar Negocios"));
    waitForUiToLoad(appPage);

    assertVisibleText(appPage, "Información General");
    assertVisibleText(appPage, "Detalles de la Cuenta");
    assertVisibleText(appPage, "Tus Negocios");
    assertVisibleText(appPage, "Sección Legal");
    this.administrarNegociosUrl = appPage.url();
    takeScreenshot(appPage, "04-administrar-negocios-page", true);
  }

  private void stepInformacionGeneral() {
    assertVisibleText(appPage, "Información General");
    assertVisibleText(appPage, "BUSINESS PLAN");
    assertVisibleText(appPage, "Cambiar Plan");

    final String pageText = getBodyText(appPage);
    final Matcher emailMatcher = EMAIL_PATTERN.matcher(pageText);
    if (!emailMatcher.find()) {
      throw new AssertionError("No visible user email found in Información General.");
    }

    if (!containsAnyVisibleText(appPage, List.of("Nombre", "Usuario", "Perfil"))) {
      throw new AssertionError("No visible user name/label found in Información General.");
    }
  }

  private void stepDetallesCuenta() {
    assertVisibleText(appPage, "Detalles de la Cuenta");
    assertVisibleText(appPage, "Cuenta creada");
    assertVisibleText(appPage, "Estado activo");
    assertVisibleText(appPage, "Idioma seleccionado");
  }

  private void stepTusNegocios() {
    assertVisibleText(appPage, "Tus Negocios");
    assertVisibleText(appPage, "Agregar Negocio");
    assertVisibleText(appPage, "Tienes 2 de 3 negocios");
  }

  private void stepTerminos() {
    this.terminosUrl =
        openAndValidateLegalLink(
            "Términos y Condiciones",
            "Términos y Condiciones",
            "08-terminos-y-condiciones");
  }

  private void stepPrivacidad() {
    this.privacidadUrl =
        openAndValidateLegalLink(
            "Política de Privacidad",
            "Política de Privacidad",
            "09-politica-de-privacidad");
  }

  private String openAndValidateLegalLink(
      final String linkText, final String expectedHeading, final String screenshotName) {
    final Page currentPage = appPage;
    final int pagesBefore = context.pages().size();
    clickFirstVisibleByText(currentPage, List.of(linkText));
    waitForUiToLoad(currentPage);

    Page legalPage = currentPage;
    final long popupDeadline = System.currentTimeMillis() + 5000;
    while (System.currentTimeMillis() < popupDeadline) {
      final List<Page> pages = context.pages();
      if (pages.size() > pagesBefore) {
        legalPage = pages.get(pages.size() - 1);
        break;
      }
      currentPage.waitForTimeout(150);
    }
    waitForUiToLoad(legalPage);

    assertVisibleText(legalPage, expectedHeading);
    final String legalContent = getBodyText(legalPage);
    if (legalContent.trim().length() < 120) {
      throw new AssertionError("Legal content appears too short on " + expectedHeading + " page.");
    }

    takeScreenshot(legalPage, screenshotName, true);
    final String finalUrl = legalPage.url();

    if (!Objects.equals(legalPage, currentPage)) {
      legalPage.close();
      currentPage.bringToFront();
    } else {
      returnToApplicationPage();
    }

    return finalUrl;
  }

  private void returnToApplicationPage() {
    if (containsVisibleText(appPage, "Sección Legal")) {
      return;
    }

    try {
      appPage.goBack();
      waitForUiToLoad(appPage);
    } catch (RuntimeException ignored) {
      // If history is unavailable, navigate directly back to account page.
    }

    if (!containsVisibleText(appPage, "Sección Legal") && !isBlank(administrarNegociosUrl)) {
      appPage.navigate(administrarNegociosUrl);
      waitForUiToLoad(appPage);
    }
  }

  private void handleGoogleAccountSelectorIfPresent(final String email) {
    Page googlePage = null;
    for (Page contextPage : context.pages()) {
      final String url = contextPage.url() == null ? "" : contextPage.url();
      if (url.contains("accounts.google.com")) {
        googlePage = contextPage;
        break;
      }
    }

    if (googlePage == null) {
      return;
    }

    waitForUiToLoad(googlePage);
    if (containsVisibleText(googlePage, email)) {
      clickFirstVisibleByText(googlePage, List.of(email));
    }

    // Wait for either popup close or app sidebar visibility.
    final long deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS;
    while (System.currentTimeMillis() < deadline) {
      if (googlePage.isClosed() || isSidebarVisible(appPage)) {
        return;
      }
      appPage.waitForTimeout(250);
    }
  }

  private void waitForMainInterface() {
    final long deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS;
    while (System.currentTimeMillis() < deadline) {
      waitForUiToLoad(appPage);
      if (isSidebarVisible(appPage) && containsAnyVisibleText(appPage, List.of("Negocio", "Mi Negocio"))) {
        return;
      }
      appPage.waitForTimeout(300);
    }
    throw new AssertionError("Main interface did not load after Google login.");
  }

  private void clickFirstVisibleByText(final Page page, final List<String> texts) {
    RuntimeException lastError = null;
    for (String text : texts) {
      try {
        final Locator locator = page.getByText(Pattern.compile("(?iu)^\\s*" + Pattern.quote(text) + "\\s*$")).first();
        locator.waitFor(
            new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(Math.min(DEFAULT_TIMEOUT_MS, 6000)));
        locator.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
        waitForUiToLoad(page);
        return;
      } catch (RuntimeException ex) {
        lastError = ex;
      }
    }
    throw new IllegalStateException("Could not click any of the texts: " + texts, lastError);
  }

  private void assertVisibleText(final Page page, final String text) {
    final Locator locator = page.getByText(Pattern.compile("(?iu)" + Pattern.quote(text))).first();
    locator.waitFor(
        new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));
  }

  private boolean containsVisibleText(final Page page, final String text) {
    try {
      assertVisibleText(page, text);
      return true;
    } catch (RuntimeException ex) {
      return false;
    }
  }

  private boolean containsAnyVisibleText(final Page page, final List<String> texts) {
    for (String text : texts) {
      if (containsVisibleText(page, text)) {
        return true;
      }
    }
    return false;
  }

  private boolean isSidebarVisible(final Page page) {
    return isVisible(page.locator("aside").first(), 1500) || isVisible(page.locator("nav").first(), 1500);
  }

  private boolean isVisible(final Locator locator, final int timeoutMs) {
    try {
      locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
      return true;
    } catch (RuntimeException ex) {
      return false;
    }
  }

  private void waitForUiToLoad(final Page page) {
    try {
      page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    } catch (RuntimeException ignored) {
      // In SPA transitions DOMContentLoaded may not fire again.
    }
    try {
      page.waitForLoadState(
          LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
    } catch (RuntimeException ignored) {
      // Some pages keep sockets open; we still apply a brief settle wait below.
    }
    page.waitForTimeout(500);
  }

  private String getBodyText(final Page page) {
    final String text = page.locator("body").innerText();
    return text == null ? "" : text;
  }

  private void takeScreenshot(final Page page, final String name, final boolean fullPage) {
    final Path screenshotPath = evidenceDir.resolve(name + ".png");
    page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
  }

  private void runStep(final String stepName, final StepExecutable executable) {
    try {
      executable.execute();
      report.put(stepName, new StepResult("PASS", "Validation succeeded"));
      System.out.println("[PASS] " + stepName);
    } catch (Throwable error) {
      final String message = (error.getMessage() == null || error.getMessage().isBlank())
          ? error.getClass().getSimpleName()
          : error.getMessage();
      report.put(stepName, new StepResult("FAIL", message));
      System.out.println("[FAIL] " + stepName + " -> " + message);
    }
  }

  private void writeFinalReport() throws IOException {
    final StringBuilder reportText = new StringBuilder();
    reportText.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
    reportText.append("Evidence directory: ").append(evidenceDir).append(System.lineSeparator());
    reportText.append(System.lineSeparator());

    for (String field : REPORT_FIELDS) {
      final StepResult result = report.get(field);
      reportText
          .append(field)
          .append(": ")
          .append(result.status)
          .append(" - ")
          .append(result.details)
          .append(System.lineSeparator());
    }

    reportText.append(System.lineSeparator());
    reportText.append("Términos y Condiciones URL: ").append(terminosUrl).append(System.lineSeparator());
    reportText.append("Política de Privacidad URL: ").append(privacidadUrl).append(System.lineSeparator());

    Files.writeString(evidenceDir.resolve("final-report.txt"), reportText.toString());
    System.out.println(reportText);
  }

  private Path createEvidenceDirectory() throws IOException {
    final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
    final Path outputDir = Paths.get("target", "saleads-e2e-artifacts", timestamp);
    Files.createDirectories(outputDir);
    return outputDir;
  }

  private boolean isBlank(final String value) {
    return value == null || value.trim().isEmpty();
  }

  @FunctionalInterface
  private interface StepExecutable {
    void execute();
  }

  private static class StepResult {
    private final String status;
    private final String details;

    private StepResult(final String status, final String details) {
      this.status = status;
      this.details = details;
    }
  }
}
