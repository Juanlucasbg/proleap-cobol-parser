package io.proleap.cobol.e2e;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

public class SaleadsMiNegocioFullWorkflowTest {

  private static final String WORKFLOW_NAME = "saleads_mi_negocio_full_test";
  private static final String LOGIN_URL_ENV = "SALEADS_LOGIN_URL";
  private static final String GOOGLE_ACCOUNT_ENV = "SALEADS_GOOGLE_ACCOUNT_EMAIL";
  private static final String HEADLESS_ENV = "SALEADS_HEADLESS";
  private static final long DEFAULT_TIMEOUT_MS = 15000;
  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

  private final LinkedHashMap<String, String> results = new LinkedHashMap<>();
  private final LinkedHashMap<String, String> notes = new LinkedHashMap<>();
  private final LinkedHashMap<String, String> legalUrls = new LinkedHashMap<>();
  private final List<String> screenshots = new ArrayList<>();

  private Path outputDir;
  private Page appPage;

  @Test
  public void saleadsMiNegocioFullWorkflow() throws IOException {
    initializeReport();
    outputDir = Paths.get("target", "e2e", WORKFLOW_NAME);
    Files.createDirectories(outputDir);

    final String loginUrl = env(LOGIN_URL_ENV);
    Assume.assumeTrue(
        "Set " + LOGIN_URL_ENV + " to the SaleADS login page URL for the current environment.",
        loginUrl != null && !loginUrl.isBlank());

    final String googleEmail = envOrDefault(GOOGLE_ACCOUNT_ENV, "juanlucasbarbiergarzon@gmail.com");
    final boolean headless = Boolean.parseBoolean(envOrDefault(HEADLESS_ENV, "true"));

    try (
        Playwright playwright = Playwright.create();
        Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
        BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000))) {

      runStep("Login", () -> loginWithGoogle(context, loginUrl, googleEmail));
      runStep("Mi Negocio menu", this::openMiNegocioMenu);
      runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
      runStep("Administrar Negocios view", this::openAdministrarNegociosView);
      runStep("Información General", this::validateInformacionGeneral);
      runStep("Detalles de la Cuenta", this::validateDetallesDeLaCuenta);
      runStep("Tus Negocios", this::validateTusNegocios);
      runStep("Términos y Condiciones",
          () -> validateLegalPage(
              "Términos y Condiciones",
              new String[] {"Términos y Condiciones", "Terminos y Condiciones"},
              new String[] {"Términos y Condiciones", "Terminos y Condiciones"}));
      runStep("Política de Privacidad",
          () -> validateLegalPage(
              "Política de Privacidad",
              new String[] {"Política de Privacidad", "Politica de Privacidad"},
              new String[] {"Política de Privacidad", "Politica de Privacidad"}));
    } finally {
      writeFinalReport();
    }

    List<String> failedFields = results.entrySet().stream()
        .filter(entry -> !"PASS".equals(entry.getValue()))
        .map(Map.Entry::getKey)
        .collect(Collectors.toList());

    Assert.assertTrue(
        "Some workflow validations failed. Check report at "
            + outputDir.resolve("final_report.txt")
            + ". Failed: "
            + failedFields,
        failedFields.isEmpty());
  }

  private void loginWithGoogle(BrowserContext context, String loginUrl, String googleEmail) {
    appPage = context.newPage();
    appPage.navigate(loginUrl);
    waitForUi(appPage);

    if (!isLeftSidebarVisible(appPage)) {
      Locator loginButton = findVisibleText(appPage,
          "Sign in with Google",
          "Iniciar sesión con Google",
          "Iniciar sesion con Google",
          "Continuar con Google",
          "Google");
      if (loginButton == null) {
        throw new AssertionError("Could not locate the login button or 'Sign in with Google'.");
      }

      int pagesBefore = context.pages().size();
      clickAndWaitForUi(appPage, loginButton);
      Page authPage = resolveNewlyOpenedPage(context, pagesBefore, appPage);

      selectGoogleAccountIfShown(authPage, googleEmail);
      appPage = waitForApplicationPage(context, appPage, authPage);
    }

    Assert.assertTrue("Main application interface did not appear after login.", isLeftSidebarVisible(appPage));
    captureScreenshot(appPage, "01-dashboard-loaded.png", false);
  }

  private void openMiNegocioMenu() {
    ensureAppPage();
    assertTextVisible(appPage, "Negocio");

    Locator miNegocio = findVisibleText(appPage, "Mi Negocio");
    if (miNegocio == null) {
      throw new AssertionError("Could not find 'Mi Negocio' in left sidebar.");
    }
    clickAndWaitForUi(appPage, miNegocio);

    assertTextVisible(appPage, "Agregar Negocio");
    assertTextVisible(appPage, "Administrar Negocios");
    captureScreenshot(appPage, "02-mi-negocio-expanded.png", false);
  }

  private void validateAgregarNegocioModal() {
    ensureAppPage();
    Locator agregarNegocio = findVisibleText(appPage, "Agregar Negocio");
    if (agregarNegocio == null) {
      throw new AssertionError("Could not click 'Agregar Negocio' because it is not visible.");
    }

    clickAndWaitForUi(appPage, agregarNegocio);

    assertTextVisible(appPage, "Crear Nuevo Negocio");
    Locator nombreNegocioInput = findNombreNegocioInput(appPage);
    if (nombreNegocioInput == null) {
      throw new AssertionError("Input field 'Nombre del Negocio' is not visible.");
    }
    assertTextVisible(appPage, "Tienes 2 de 3 negocios");
    assertTextVisible(appPage, "Cancelar");
    assertTextVisible(appPage, "Crear Negocio");

    captureScreenshot(appPage, "03-crear-nuevo-negocio-modal.png", false);

    clickAndWaitForUi(appPage, nombreNegocioInput);
    nombreNegocioInput.fill("Negocio Prueba Automatización");
    clickByVisibleText(appPage, "Cancelar");
    waitForUi(appPage);
  }

  private void openAdministrarNegociosView() {
    ensureAppPage();
    if (findVisibleText(appPage, "Administrar Negocios") == null) {
      Locator miNegocio = findVisibleText(appPage, "Mi Negocio");
      if (miNegocio == null) {
        throw new AssertionError("Cannot expand 'Mi Negocio' to reach 'Administrar Negocios'.");
      }
      clickAndWaitForUi(appPage, miNegocio);
    }

    clickByVisibleText(appPage, "Administrar Negocios");
    waitForUi(appPage);

    assertTextVisible(appPage, "Información General", "Informacion General");
    assertTextVisible(appPage, "Detalles de la Cuenta", "Detalles de la Cuenta");
    assertTextVisible(appPage, "Tus Negocios");
    assertTextVisible(appPage, "Sección Legal", "Seccion Legal");

    captureScreenshot(appPage, "04-administrar-negocios-full.png", true);
  }

  private void validateInformacionGeneral() {
    ensureAppPage();
    Locator infoSection = sectionFromHeading(appPage, "Información General", "Informacion General");
    if (infoSection == null) {
      throw new AssertionError("Could not locate the 'Información General' section.");
    }

    String sectionText = infoSection.innerText();
    if (!EMAIL_PATTERN.matcher(sectionText).find()) {
      throw new AssertionError("User email is not visible in 'Información General'.");
    }

    if (!containsLikelyPersonName(sectionText)) {
      throw new AssertionError("User name is not clearly visible in 'Información General'.");
    }

    assertTextVisible(appPage, "BUSINESS PLAN");
    assertTextVisible(appPage, "Cambiar Plan");
  }

  private void validateDetallesDeLaCuenta() {
    ensureAppPage();
    assertTextVisible(appPage, "Cuenta creada");
    assertTextVisible(appPage, "Estado activo");
    assertTextVisible(appPage, "Idioma seleccionado");
  }

  private void validateTusNegocios() {
    ensureAppPage();
    Locator negociosSection = sectionFromHeading(appPage, "Tus Negocios");
    if (negociosSection == null) {
      throw new AssertionError("Could not locate 'Tus Negocios' section.");
    }

    assertTextVisible(appPage, "Agregar Negocio");
    assertTextVisible(appPage, "Tienes 2 de 3 negocios");

    String sectionText = negociosSection.innerText();
    if (sectionText.lines().map(String::trim).filter(line -> !line.isBlank()).count() < 4) {
      throw new AssertionError("'Tus Negocios' list content does not appear visible.");
    }
  }

  private void validateLegalPage(String reportField, String[] linkTexts, String[] headingTexts) {
    ensureAppPage();

    Locator legalLink = findVisibleText(appPage, linkTexts);
    if (legalLink == null) {
      throw new AssertionError("Legal link not visible for: " + reportField);
    }

    BrowserContext context = appPage.context();
    int pagesBefore = context.pages().size();
    clickAndWaitForUi(appPage, legalLink);
    Page legalPage = resolveNewlyOpenedPage(context, pagesBefore, appPage);
    waitForUi(legalPage);

    assertTextVisible(legalPage, headingTexts);
    assertLegalBodyHasContent(legalPage, reportField);

    captureScreenshot(legalPage, "legal-" + slugify(reportField) + ".png", true);
    legalUrls.put(reportField, legalPage.url());

    cleanupLegalNavigation(legalPage);
  }

  private void cleanupLegalNavigation(Page legalPage) {
    if (legalPage != appPage) {
      legalPage.close();
      appPage.bringToFront();
      waitForUi(appPage);
      return;
    }

    appPage.goBack();
    waitForUi(appPage);
  }

  private Page waitForApplicationPage(BrowserContext context, Page originalAppPage, Page authPage) {
    long deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS;
    while (System.currentTimeMillis() < deadline) {
      for (Page candidate : context.pages()) {
        if (isLeftSidebarVisible(candidate)) {
          return candidate;
        }
      }
      waitShort(originalAppPage);
      if (authPage != originalAppPage && !authPage.isClosed()) {
        waitShort(authPage);
      }
    }

    throw new AssertionError("Could not detect application sidebar after login flow.");
  }

  private void selectGoogleAccountIfShown(Page authPage, String googleEmail) {
    Locator accountLocator = findVisibleText(authPage, googleEmail);
    if (accountLocator != null) {
      clickAndWaitForUi(authPage, accountLocator);
      return;
    }

    Locator useAnotherAccount = findVisibleText(authPage, "Use another account", "Usar otra cuenta");
    if (useAnotherAccount != null) {
      throw new AssertionError("Google account chooser is visible but the configured account was not found: " + googleEmail);
    }
  }

  private void runStep(String field, StepExecutable step) {
    try {
      step.execute();
      results.put(field, "PASS");
      notes.put(field, "Validation passed.");
    } catch (Throwable error) {
      results.put(field, "FAIL");
      notes.put(field, compactError(error));
      captureFailure(field);
    }
  }

  private void initializeReport() {
    List<String> fields = Arrays.asList(
        "Login",
        "Mi Negocio menu",
        "Agregar Negocio modal",
        "Administrar Negocios view",
        "Información General",
        "Detalles de la Cuenta",
        "Tus Negocios",
        "Términos y Condiciones",
        "Política de Privacidad");

    for (String field : fields) {
      results.put(field, "BLOCKED");
      notes.put(field, "Step not executed.");
    }
  }

  private void writeFinalReport() throws IOException {
    StringBuilder report = new StringBuilder();
    report.append("Workflow: ").append(WORKFLOW_NAME).append('\n');
    report.append("Generated at: ").append(Instant.now()).append('\n');
    report.append('\n');
    report.append("Final Report (PASS/FAIL)\n");
    for (Map.Entry<String, String> entry : results.entrySet()) {
      String field = entry.getKey();
      report.append("- ").append(field).append(": ").append(entry.getValue());
      String note = notes.get(field);
      if (note != null && !note.isBlank()) {
        report.append(" (").append(note).append(")");
      }
      report.append('\n');
    }

    if (!legalUrls.isEmpty()) {
      report.append('\n').append("Final URLs\n");
      for (Map.Entry<String, String> entry : legalUrls.entrySet()) {
        report.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
      }
    }

    if (!screenshots.isEmpty()) {
      report.append('\n').append("Screenshots\n");
      for (String screenshot : screenshots) {
        report.append("- ").append(screenshot).append('\n');
      }
    }

    Files.writeString(outputDir.resolve("final_report.txt"), report.toString(), StandardCharsets.UTF_8);
  }

  private void captureFailure(String field) {
    if (appPage == null || outputDir == null) {
      return;
    }

    try {
      captureScreenshot(appPage, "failure-" + slugify(field) + ".png", true);
    } catch (Throwable ignored) {
      // Do not hide the original test failure if screenshot capture fails.
    }
  }

  private void assertLegalBodyHasContent(Page page, String reportField) {
    Locator body = page.locator("body");
    body.first().waitFor(new Locator.WaitForOptions()
        .setState(WaitForSelectorState.VISIBLE)
        .setTimeout(DEFAULT_TIMEOUT_MS));
    String text = body.innerText().trim();

    if (text.length() < 120) {
      throw new AssertionError("Legal content text looks too short for: " + reportField);
    }
  }

  private Locator sectionFromHeading(Page page, String... headings) {
    Locator heading = findVisibleText(page, headings);
    if (heading == null) {
      return null;
    }

    Locator section = heading.locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
    if (section.count() == 0) {
      return null;
    }
    return section.first();
  }

  private boolean containsLikelyPersonName(String text) {
    return text.lines()
        .map(String::trim)
        .filter(line -> !line.isBlank())
        .anyMatch(line ->
            !line.contains("@")
                && line.length() >= 3
                && !equalsAnyIgnoreCase(line, "Información General", "Informacion General", "BUSINESS PLAN",
                    "Cambiar Plan", "Cuenta creada", "Estado activo", "Idioma seleccionado"));
  }

  private void assertTextVisible(Page page, String... candidates) {
    Locator located = findVisibleText(page, candidates);
    if (located == null) {
      throw new AssertionError("Expected visible text not found: " + Arrays.toString(candidates));
    }
  }

  private Locator findNombreNegocioInput(Page page) {
    Locator byLabel = page.getByLabel("Nombre del Negocio");
    if (isVisible(byLabel)) {
      return firstVisible(byLabel);
    }

    Locator byPlaceholder = page.getByPlaceholder("Nombre del Negocio");
    if (isVisible(byPlaceholder)) {
      return firstVisible(byPlaceholder);
    }

    Locator byName = page.locator("input[name*='negocio' i]");
    if (isVisible(byName)) {
      return firstVisible(byName);
    }

    return null;
  }

  private void clickByVisibleText(Page page, String... candidates) {
    Locator locator = findVisibleText(page, candidates);
    if (locator == null) {
      throw new AssertionError("Could not click element with visible text: " + Arrays.toString(candidates));
    }
    clickAndWaitForUi(page, locator);
  }

  private Locator findVisibleText(Page page, String... candidates) {
    for (String candidate : candidates) {
      Locator exact = page.getByText(candidate, new Page.GetByTextOptions().setExact(true));
      Locator exactVisible = firstVisible(exact);
      if (exactVisible != null) {
        return exactVisible;
      }

      Locator partial = page.getByText(candidate);
      Locator partialVisible = firstVisible(partial);
      if (partialVisible != null) {
        return partialVisible;
      }
    }
    return null;
  }

  private Locator firstVisible(Locator locator) {
    int count = locator.count();
    for (int index = 0; index < Math.min(count, 8); index++) {
      Locator candidate = locator.nth(index);
      if (candidate.isVisible()) {
        return candidate;
      }
    }
    return null;
  }

  private boolean isVisible(Locator locator) {
    return firstVisible(locator) != null;
  }

  private void clickAndWaitForUi(Page page, Locator locator) {
    locator.scrollIntoViewIfNeeded();
    locator.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
    waitForUi(page);
  }

  private void waitForUi(Page page) {
    page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE,
          new Page.WaitForLoadStateOptions().setTimeout(6000));
    } catch (Throwable ignored) {
      // Some applications keep open network connections; DOM content loaded is enough fallback.
    }
    waitShort(page);
  }

  private void waitShort(Page page) {
    page.waitForTimeout(350);
  }

  private boolean isLeftSidebarVisible(Page page) {
    return findVisibleText(page, "Mi Negocio", "Negocio", "Administrar Negocios") != null;
  }

  private Page resolveNewlyOpenedPage(BrowserContext context, int pagesBefore, Page fallback) {
    long deadline = System.currentTimeMillis() + 5000;
    while (System.currentTimeMillis() < deadline) {
      if (context.pages().size() > pagesBefore) {
        return context.pages().get(context.pages().size() - 1);
      }
      waitShort(fallback);
    }
    return fallback;
  }

  private void captureScreenshot(Page page, String fileName, boolean fullPage) {
    Path screenshotPath = outputDir.resolve(fileName);
    page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
    screenshots.add(screenshotPath.toString());
  }

  private void ensureAppPage() {
    if (appPage == null) {
      throw new AssertionError("Application page is not initialized.");
    }
  }

  private String compactError(Throwable error) {
    String message = error.getMessage();
    if (message == null || message.isBlank()) {
      return error.getClass().getSimpleName();
    }
    return message.replaceAll("\\s+", " ").trim();
  }

  private boolean equalsAnyIgnoreCase(String value, String... candidates) {
    for (String candidate : candidates) {
      if (candidate.equalsIgnoreCase(value)) {
        return true;
      }
    }
    return false;
  }

  private String slugify(String value) {
    return value.toLowerCase()
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace("ñ", "n")
        .replaceAll("[^a-z0-9]+", "-")
        .replaceAll("(^-|-$)", "");
  }

  private String env(String variableName) {
    return System.getenv(variableName);
  }

  private String envOrDefault(String variableName, String defaultValue) {
    String value = env(variableName);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    return value;
  }

  @FunctionalInterface
  private interface StepExecutable {
    void execute();
  }
}
