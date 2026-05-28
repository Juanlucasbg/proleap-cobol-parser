package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Locator.IsVisibleOptions;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.GetByRoleOptions;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;

/**
 * Domain-agnostic E2E workflow for SaleADS.ai -> Mi Negocio.
 *
 * Required runtime variables:
 * - SALEADS_LOGIN_URL: login page URL for the current environment.
 *
 * Optional runtime variables:
 * - SALEADS_HEADLESS=true|false (default: true)
 * - SALEADS_TIMEOUT_MS (default: 30000)
 * - SALEADS_GOOGLE_EMAIL (default: juanlucasbarbiergarzon@gmail.com)
 * - SALEADS_ARTIFACTS_DIR (default: target/saleads-mi-negocio-artifacts)
 */
public class SaleadsMiNegocioFullWorkflowE2E {

  private static final String DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
  private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
  private static final Pattern LOGIN_BUTTON_PATTERN = Pattern.compile(
      "(?i)(iniciar sesion con google|inicia sesi[oó]n con google|continuar con google|sign in with google|google)");

  private final Map<String, String> stepStatus = new LinkedHashMap<>();
  private final List<String> notes = new ArrayList<>();

  private Playwright playwright;
  private Browser browser;
  private BrowserContext context;
  private Page appPage;
  private Path artifactsDir;
  private long timeoutMs;
  private String googleEmail;
  private String termsUrl = "N/A";
  private String privacyUrl = "N/A";

  @Test
  public void runSaleadsMiNegocioWorkflow() throws IOException {
    initializeReport();
    boolean allPassed = false;

    try {
      initializeBrowser();
      stepLogin();
      stepMiNegocioMenu();
      stepAgregarNegocioModal();
      stepAdministrarNegocios();
      stepInformacionGeneral();
      stepDetallesDeLaCuenta();
      stepTusNegocios();
      stepTerminosYCondiciones();
      stepPoliticaDePrivacidad();
      allPassed = !stepStatus.containsValue("FAIL");
    } catch (Exception e) {
      notes.add("Unexpected test error: " + safeMessage(e));
      markRemainingAsFail();
    } finally {
      ensureArtifactsDir();
      writeReport();
      closeResources();
    }

    assertTrue("One or more SaleADS Mi Negocio workflow validations failed. See report in " + artifactsDir, allPassed);
  }

  private void initializeReport() {
    stepStatus.put("Login", "FAIL");
    stepStatus.put("Mi Negocio menu", "FAIL");
    stepStatus.put("Agregar Negocio modal", "FAIL");
    stepStatus.put("Administrar Negocios view", "FAIL");
    stepStatus.put("Información General", "FAIL");
    stepStatus.put("Detalles de la Cuenta", "FAIL");
    stepStatus.put("Tus Negocios", "FAIL");
    stepStatus.put("Términos y Condiciones", "FAIL");
    stepStatus.put("Política de Privacidad", "FAIL");
  }

  private void initializeBrowser() throws IOException {
    String loginUrl = envOrProperty("SALEADS_LOGIN_URL", null);
    if (loginUrl == null || loginUrl.isBlank()) {
      throw new IllegalStateException("SALEADS_LOGIN_URL is required to run this workflow.");
    }

    timeoutMs = parseLong(envOrProperty("SALEADS_TIMEOUT_MS", "30000"), 30000L);
    googleEmail = envOrProperty("SALEADS_GOOGLE_EMAIL", DEFAULT_GOOGLE_EMAIL);
    boolean headless = Boolean.parseBoolean(envOrProperty("SALEADS_HEADLESS", "true"));

    String baseArtifacts = envOrProperty("SALEADS_ARTIFACTS_DIR", "target/saleads-mi-negocio-artifacts");
    artifactsDir = Paths.get(baseArtifacts).resolve(TS.format(ZonedDateTime.now(ZoneOffset.UTC)));
    Files.createDirectories(artifactsDir);

    playwright = Playwright.create();
    BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
        .setHeadless(headless)
        .setTimeout((double) timeoutMs);
    browser = playwright.chromium().launch(launchOptions);
    context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1200));
    appPage = context.newPage();
    appPage.setDefaultTimeout(timeoutMs);
    appPage.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
    waitForUiLoad(appPage);
  }

  private void stepLogin() {
    String stepKey = "Login";
    try {
      Locator loginButton = firstVisibleLocator(
          appPage.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName(LOGIN_BUTTON_PATTERN)),
          appPage.getByRole(AriaRole.LINK, new GetByRoleOptions().setName(LOGIN_BUTTON_PATTERN)),
          appPage.getByText("Inicia sesión con Google"),
          appPage.getByText("Sign in with Google"));

      Page maybePopup = clickHandlingPopup(loginButton);
      if (maybePopup != null) {
        selectGoogleAccountIfVisible(maybePopup);
      } else {
        selectGoogleAccountIfVisible(appPage);
      }

      waitForUiLoad(appPage);
      boolean mainInterfaceVisible = anyVisible(
          appPage.locator("aside"),
          appPage.getByText("Negocio"),
          appPage.getByText("Mi Negocio"));

      if (mainInterfaceVisible) {
        screenshot("01-dashboard-loaded", appPage, false);
        stepStatus.put(stepKey, "PASS");
      } else {
        notes.add("Login completed but main interface/sidebar was not detected.");
      }
    } catch (Exception e) {
      notes.add("Login step failed: " + safeMessage(e));
    }
  }

  private void stepMiNegocioMenu() {
    String stepKey = "Mi Negocio menu";
    try {
      clickAndWait(appPage.getByText("Negocio"));
      clickAndWait(appPage.getByText("Mi Negocio"));

      boolean expanded = anyVisible(
          appPage.getByText("Agregar Negocio"),
          appPage.getByText("Administrar Negocios"));

      boolean agregarVisible = isVisible(appPage.getByText("Agregar Negocio"), 5000);
      boolean administrarVisible = isVisible(appPage.getByText("Administrar Negocios"), 5000);

      if (expanded && agregarVisible && administrarVisible) {
        screenshot("02-mi-negocio-menu-expanded", appPage, false);
        stepStatus.put(stepKey, "PASS");
      } else {
        notes.add("Mi Negocio menu did not expose expected submenu options.");
      }
    } catch (Exception e) {
      notes.add("Mi Negocio menu step failed: " + safeMessage(e));
    }
  }

  private void stepAgregarNegocioModal() {
    String stepKey = "Agregar Negocio modal";
    try {
      clickAndWait(appPage.getByText("Agregar Negocio"));

      boolean titleVisible = isVisible(appPage.getByText("Crear Nuevo Negocio"), timeoutMs);
      boolean inputVisible = anyVisible(
          appPage.getByLabel("Nombre del Negocio"),
          appPage.getByPlaceholder("Nombre del Negocio"),
          appPage.getByText("Nombre del Negocio"));
      boolean quotaVisible = isVisible(appPage.getByText("Tienes 2 de 3 negocios"), 8000);
      boolean cancelVisible = isVisible(appPage.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName("Cancelar")), 8000);
      boolean createVisible = isVisible(appPage.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName("Crear Negocio")), 8000);

      if (titleVisible && inputVisible && quotaVisible && cancelVisible && createVisible) {
        screenshot("03-agregar-negocio-modal", appPage, false);

        // Optional action requested by workflow.
        Locator nameInput = firstVisibleLocator(
            appPage.getByLabel("Nombre del Negocio"),
            appPage.getByPlaceholder("Nombre del Negocio"),
            appPage.locator("input[type='text']"));
        nameInput.fill("Negocio Prueba Automatizacion");
        waitForUiLoad(appPage);
        clickAndWait(appPage.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName("Cancelar")));

        stepStatus.put(stepKey, "PASS");
      } else {
        notes.add("Agregar Negocio modal is missing one or more required elements.");
        dismissModalIfVisible();
      }
    } catch (Exception e) {
      notes.add("Agregar Negocio modal step failed: " + safeMessage(e));
      dismissModalIfVisible();
    }
  }

  private void stepAdministrarNegocios() {
    String stepKey = "Administrar Negocios view";
    try {
      if (!isVisible(appPage.getByText("Administrar Negocios"), 3000)) {
        clickAndWait(appPage.getByText("Mi Negocio"));
      }
      clickAndWait(appPage.getByText("Administrar Negocios"));

      boolean infoGeneral = isVisible(appPage.getByText("Información General"), timeoutMs);
      boolean detallesCuenta = isVisible(appPage.getByText("Detalles de la Cuenta"), timeoutMs);
      boolean tusNegocios = isVisible(appPage.getByText("Tus Negocios"), timeoutMs);
      boolean seccionLegal = anyVisible(appPage.getByText("Sección Legal"), appPage.getByText("Términos y Condiciones"));

      if (infoGeneral && detallesCuenta && tusNegocios && seccionLegal) {
        screenshot("04-administrar-negocios-view", appPage, true);
        stepStatus.put(stepKey, "PASS");
      } else {
        notes.add("Administrar Negocios view is missing expected sections.");
      }
    } catch (Exception e) {
      notes.add("Administrar Negocios step failed: " + safeMessage(e));
    }
  }

  private void stepInformacionGeneral() {
    String stepKey = "Información General";
    try {
      boolean userNameVisible = anyVisible(
          appPage.getByText(Pattern.compile("(?i)juan|lucas|barbier|garzon")),
          appPage.locator("text=/[A-Za-zÁÉÍÓÚÑáéíóúñ]{3,}\\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{3,}/"));
      boolean userEmailVisible = anyVisible(
          appPage.getByText(googleEmail),
          appPage.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/"));
      boolean planVisible = isVisible(appPage.getByText("BUSINESS PLAN"), 8000);
      boolean changePlanVisible = isVisible(appPage.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName("Cambiar Plan")), 8000);

      if (userNameVisible && userEmailVisible && planVisible && changePlanVisible) {
        stepStatus.put(stepKey, "PASS");
      } else {
        notes.add("Información General did not meet all expected validations.");
      }
    } catch (Exception e) {
      notes.add("Información General validation failed: " + safeMessage(e));
    }
  }

  private void stepDetallesDeLaCuenta() {
    String stepKey = "Detalles de la Cuenta";
    try {
      boolean cuentaCreada = isVisible(appPage.getByText("Cuenta creada"), 8000);
      boolean estadoActivo = anyVisible(appPage.getByText("Estado activo"), appPage.getByText("Estado Activo"));
      boolean idiomaSeleccionado = isVisible(appPage.getByText("Idioma seleccionado"), 8000);

      if (cuentaCreada && estadoActivo && idiomaSeleccionado) {
        stepStatus.put(stepKey, "PASS");
      } else {
        notes.add("Detalles de la Cuenta did not meet all expected validations.");
      }
    } catch (Exception e) {
      notes.add("Detalles de la Cuenta validation failed: " + safeMessage(e));
    }
  }

  private void stepTusNegocios() {
    String stepKey = "Tus Negocios";
    try {
      boolean businessListVisible = anyVisible(
          appPage.getByText("Tus Negocios"),
          appPage.locator("[data-testid*='business']"));
      boolean agregarNegocioButtonVisible = isVisible(appPage.getByText("Agregar Negocio"), 8000);
      boolean quotaVisible = isVisible(appPage.getByText("Tienes 2 de 3 negocios"), 8000);

      if (businessListVisible && agregarNegocioButtonVisible && quotaVisible) {
        stepStatus.put(stepKey, "PASS");
      } else {
        notes.add("Tus Negocios did not meet all expected validations.");
      }
    } catch (Exception e) {
      notes.add("Tus Negocios validation failed: " + safeMessage(e));
    }
  }

  private void stepTerminosYCondiciones() {
    String stepKey = "Términos y Condiciones";
    try {
      LegalResult result = openLegalPage("Términos y Condiciones");
      termsUrl = result.url;

      boolean headingVisible = isVisible(result.page.getByText("Términos y Condiciones"), timeoutMs);
      boolean legalContentVisible = result.page.locator("main p, article p, p").count() > 0;
      screenshot("05-terminos-y-condiciones", result.page, true);

      if (headingVisible && legalContentVisible) {
        stepStatus.put(stepKey, "PASS");
      } else {
        notes.add("Términos y Condiciones page content was not fully validated.");
      }

      returnToApp(result);
    } catch (Exception e) {
      notes.add("Términos y Condiciones validation failed: " + safeMessage(e));
    }
  }

  private void stepPoliticaDePrivacidad() {
    String stepKey = "Política de Privacidad";
    try {
      LegalResult result = openLegalPage("Política de Privacidad");
      privacyUrl = result.url;

      boolean headingVisible = isVisible(result.page.getByText("Política de Privacidad"), timeoutMs);
      boolean legalContentVisible = result.page.locator("main p, article p, p").count() > 0;
      screenshot("06-politica-de-privacidad", result.page, true);

      if (headingVisible && legalContentVisible) {
        stepStatus.put(stepKey, "PASS");
      } else {
        notes.add("Política de Privacidad page content was not fully validated.");
      }

      returnToApp(result);
    } catch (Exception e) {
      notes.add("Política de Privacidad validation failed: " + safeMessage(e));
    }
  }

  private LegalResult openLegalPage(String linkText) {
    Locator link = firstVisibleLocator(
        appPage.getByRole(AriaRole.LINK, new GetByRoleOptions().setName(linkText)),
        appPage.getByText(linkText));

    Page popupPage = null;
    try {
      popupPage = context.waitForPage(() -> link.first().click(),
          new BrowserContext.WaitForPageOptions().setTimeout(timeoutMs));
    } catch (TimeoutError popupTimeout) {
      link.first().click();
    }

    waitForUiLoad(appPage);
    Page targetPage = popupPage != null ? popupPage : appPage;
    targetPage.bringToFront();
    waitForUiLoad(targetPage);

    return new LegalResult(targetPage, targetPage.url(), popupPage != null);
  }

  private void returnToApp(LegalResult result) {
    if (result.openedInNewTab) {
      result.page.close();
      appPage.bringToFront();
      waitForUiLoad(appPage);
      return;
    }

    appPage.goBack(new Page.GoBackOptions().setTimeout(timeoutMs));
    waitForUiLoad(appPage);
  }

  private void selectGoogleAccountIfVisible(Page page) {
    Locator account = page.getByText(googleEmail);
    if (isVisible(account, 6000)) {
      account.first().click();
      waitForUiLoad(page);
    }
  }

  @SafeVarargs
  private final Locator firstVisibleLocator(Locator... locators) {
    for (Locator locator : locators) {
      if (isVisible(locator, 3000)) {
        return locator.first();
      }
    }
    throw new IllegalStateException("No visible locator matched the expected UI element.");
  }

  private void clickAndWait(Locator locator) {
    locator.first().click();
    waitForUiLoad(appPage);
  }

  private Page clickHandlingPopup(Locator locator) {
    try {
      return context.waitForPage(() -> locator.first().click(),
          new BrowserContext.WaitForPageOptions().setTimeout(timeoutMs));
    } catch (TimeoutError timeout) {
      locator.first().click();
      waitForUiLoad(appPage);
      return null;
    }
  }

  private void waitForUiLoad(Page page) {
    try {
      page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(timeoutMs));
    } catch (PlaywrightException ignored) {
      // Some actions do not trigger navigation.
    }
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(Math.max(2000, timeoutMs / 2)));
    } catch (PlaywrightException ignored) {
      // Network idle may never happen in SPAs.
    }
    page.waitForTimeout(500);
  }

  private boolean anyVisible(Locator... locators) {
    for (Locator locator : locators) {
      if (isVisible(locator, 3000)) {
        return true;
      }
    }
    return false;
  }

  private boolean isVisible(Locator locator, long waitMs) {
    try {
      return locator.first().isVisible(new IsVisibleOptions().setTimeout((double) waitMs));
    } catch (PlaywrightException e) {
      return false;
    }
  }

  private void dismissModalIfVisible() {
    Locator cancelButton = appPage.getByRole(AriaRole.BUTTON, new GetByRoleOptions().setName("Cancelar"));
    if (isVisible(cancelButton, 2000)) {
      clickAndWait(cancelButton);
    }
  }

  private void screenshot(String fileName, Page page, boolean fullPage) {
    Path target = artifactsDir.resolve(fileName + ".png");
    page.screenshot(new Page.ScreenshotOptions().setPath(target).setFullPage(fullPage));
  }

  private void writeReport() throws IOException {
    Path reportPath = artifactsDir.resolve("final-report.json");
    StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"generatedAtUtc\": \"").append(escape(TS.format(ZonedDateTime.now(ZoneOffset.UTC)))).append("\",\n");
    json.append("  \"artifactsDir\": \"").append(escape(artifactsDir.toAbsolutePath().toString())).append("\",\n");
    json.append("  \"finalUrlTerminosYCondiciones\": \"").append(escape(termsUrl)).append("\",\n");
    json.append("  \"finalUrlPoliticaDePrivacidad\": \"").append(escape(privacyUrl)).append("\",\n");
    json.append("  \"results\": {\n");

    int index = 0;
    for (Map.Entry<String, String> entry : stepStatus.entrySet()) {
      json.append("    \"").append(escape(entry.getKey())).append("\": \"").append(escape(entry.getValue())).append("\"");
      if (index < stepStatus.size() - 1) {
        json.append(",");
      }
      json.append("\n");
      index++;
    }
    json.append("  },\n");
    json.append("  \"notes\": [\n");
    for (int i = 0; i < notes.size(); i++) {
      json.append("    \"").append(escape(notes.get(i))).append("\"");
      if (i < notes.size() - 1) {
        json.append(",");
      }
      json.append("\n");
    }
    json.append("  ]\n");
    json.append("}\n");

    Files.writeString(reportPath, json.toString(), StandardCharsets.UTF_8);
  }

  private void ensureArtifactsDir() throws IOException {
    if (artifactsDir == null) {
      String baseArtifacts = envOrProperty("SALEADS_ARTIFACTS_DIR", "target/saleads-mi-negocio-artifacts");
      artifactsDir = Paths.get(baseArtifacts).resolve(TS.format(ZonedDateTime.now(ZoneOffset.UTC)));
      Files.createDirectories(artifactsDir);
    }
  }

  private void markRemainingAsFail() {
    for (String key : stepStatus.keySet()) {
      if (!"PASS".equals(stepStatus.get(key))) {
        stepStatus.put(key, "FAIL");
      }
    }
  }

  private void closeResources() {
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

  private String envOrProperty(String key, String defaultValue) {
    String value = System.getenv(key);
    if (value != null && !value.isBlank()) {
      return value;
    }
    String systemValue = System.getProperty(key);
    if (systemValue != null && !systemValue.isBlank()) {
      return systemValue;
    }
    return defaultValue;
  }

  private long parseLong(String value, long fallback) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private String safeMessage(Exception e) {
    String message = e.getMessage();
    return message == null ? e.getClass().getSimpleName() : message;
  }

  private String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static final class LegalResult {
    private final Page page;
    private final String url;
    private final boolean openedInNewTab;

    private LegalResult(Page page, String url, boolean openedInNewTab) {
      this.page = page;
      this.url = url;
      this.openedInNewTab = openedInNewTab;
    }
  }
}
