package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Assume;
import org.junit.Test;

/**
 * End-to-end validation for the SaleADS "Mi Negocio" workflow.
 *
 * <p>Usage:
 *
 * <ul>
 *   <li>Set RUN_SALEADS_E2E=true
 *   <li>Set SALEADS_LOGIN_URL to the current environment login page
 *   <li>Run:
 *       <pre>mvn -Dtest=io.proleap.cobol.e2e.SaleadsMiNegocioWorkflowTest test</pre>
 * </ul>
 */
public class SaleadsMiNegocioWorkflowTest {

  private static final String REQUIRED_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
  private static final double DEFAULT_TIMEOUT_MS = 20_000;
  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

  @Test
  public void saleadsMiNegocioFullWorkflow() throws IOException {
    final boolean runE2E = Boolean.parseBoolean(System.getenv().getOrDefault("RUN_SALEADS_E2E", "false"));
    Assume.assumeTrue("Set RUN_SALEADS_E2E=true to execute this E2E.", runE2E);

    final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
    Assume.assumeTrue(
        "SALEADS_LOGIN_URL is required so the test can run on any environment without hardcoding domains.",
        loginUrl != null && !loginUrl.isBlank());

    final Path artifactsDir = Paths.get("target", "saleads-artifacts");
    final Path screenshotsDir = artifactsDir.resolve("screenshots");
    Files.createDirectories(screenshotsDir);

    final Map<String, Boolean> report = new LinkedHashMap<>();
    final Map<String, String> details = new LinkedHashMap<>();

    report.put("Login", false);
    report.put("Mi Negocio menu", false);
    report.put("Agregar Negocio modal", false);
    report.put("Administrar Negocios view", false);
    report.put("Información General", false);
    report.put("Detalles de la Cuenta", false);
    report.put("Tus Negocios", false);
    report.put("Términos y Condiciones", false);
    report.put("Política de Privacidad", false);

    String termsUrl = "";
    String privacyUrl = "";

    try (Playwright playwright = Playwright.create()) {
      final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));
      final Browser browser =
          playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
      final BrowserContext context = browser.newContext();
      final Page page = context.newPage();
      page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);

      page.navigate(loginUrl);
      waitForUiLoad(page);

      Page appPage = performGoogleLogin(page);
      waitForUiLoad(appPage);

      final boolean loginMainInterface = hasVisibleText(appPage, "Negocio", DEFAULT_TIMEOUT_MS);
      final boolean loginSidebar = isAnyVisible(List.of(appPage.locator("aside"), appPage.locator("nav")));
      report.put("Login", loginMainInterface && loginSidebar);
      details.put("Login", "Main interface visible=" + loginMainInterface + ", sidebar visible=" + loginSidebar);
      captureScreenshot(appPage, screenshotsDir, "01-dashboard-loaded", false);

      expandMiNegocioMenu(appPage);
      final boolean hasAgregar = hasVisibleText(appPage, "Agregar Negocio", DEFAULT_TIMEOUT_MS);
      final boolean hasAdministrar = hasVisibleText(appPage, "Administrar Negocios", DEFAULT_TIMEOUT_MS);
      report.put("Mi Negocio menu", hasAgregar && hasAdministrar);
      details.put("Mi Negocio menu", "Agregar visible=" + hasAgregar + ", Administrar visible=" + hasAdministrar);
      captureScreenshot(appPage, screenshotsDir, "02-mi-negocio-menu-expanded", false);

      clickByText(appPage, "Agregar Negocio");
      final boolean modalTitle = hasVisibleText(appPage, "Crear Nuevo Negocio", DEFAULT_TIMEOUT_MS);
      final boolean nombreInput = isAnyVisible(List.of(appPage.getByLabel("Nombre del Negocio")));
      final boolean negociosCount = hasVisibleText(appPage, "Tienes 2 de 3 negocios", DEFAULT_TIMEOUT_MS);
      final boolean cancelButton = hasVisibleText(appPage, "Cancelar", DEFAULT_TIMEOUT_MS);
      final boolean createButton = hasVisibleText(appPage, "Crear Negocio", DEFAULT_TIMEOUT_MS);
      report.put(
          "Agregar Negocio modal",
          modalTitle && nombreInput && negociosCount && cancelButton && createButton);
      details.put(
          "Agregar Negocio modal",
          "title="
              + modalTitle
              + ", nombreInput="
              + nombreInput
              + ", negocioCount="
              + negociosCount
              + ", cancelar="
              + cancelButton
              + ", crear="
              + createButton);
      captureScreenshot(appPage, screenshotsDir, "03-agregar-negocio-modal", false);

      if (nombreInput) {
        appPage.getByLabel("Nombre del Negocio").fill("Negocio Prueba Automatizacion");
      }
      clickByText(appPage, "Cancelar");

      expandMiNegocioMenu(appPage);
      clickByText(appPage, "Administrar Negocios");
      waitForUiLoad(appPage);

      final boolean infoGeneral = hasVisibleText(appPage, "Información General", DEFAULT_TIMEOUT_MS);
      final boolean detallesCuenta = hasVisibleText(appPage, "Detalles de la Cuenta", DEFAULT_TIMEOUT_MS);
      final boolean tusNegocios = hasVisibleText(appPage, "Tus Negocios", DEFAULT_TIMEOUT_MS);
      final boolean seccionLegal = hasVisibleText(appPage, "Sección Legal", DEFAULT_TIMEOUT_MS);
      report.put("Administrar Negocios view", infoGeneral && detallesCuenta && tusNegocios && seccionLegal);
      details.put(
          "Administrar Negocios view",
          "informacionGeneral="
              + infoGeneral
              + ", detallesCuenta="
              + detallesCuenta
              + ", tusNegocios="
              + tusNegocios
              + ", seccionLegal="
              + seccionLegal);
      captureScreenshot(appPage, screenshotsDir, "04-administrar-negocios-view", true);

      final String bodyText = safeBodyText(appPage);
      final boolean emailVisible = EMAIL_PATTERN.matcher(bodyText).find();
      final boolean userNameVisible = hasNameLikeText(bodyText);
      final boolean businessPlanVisible = hasVisibleText(appPage, "BUSINESS PLAN", DEFAULT_TIMEOUT_MS);
      final boolean cambiarPlanVisible = hasVisibleText(appPage, "Cambiar Plan", DEFAULT_TIMEOUT_MS);
      report.put("Información General", userNameVisible && emailVisible && businessPlanVisible && cambiarPlanVisible);
      details.put(
          "Información General",
          "userName="
              + userNameVisible
              + ", email="
              + emailVisible
              + ", businessPlan="
              + businessPlanVisible
              + ", cambiarPlan="
              + cambiarPlanVisible);

      final boolean cuentaCreada = hasVisibleText(appPage, "Cuenta creada", DEFAULT_TIMEOUT_MS);
      final boolean estadoActivo = hasVisibleText(appPage, "Estado activo", DEFAULT_TIMEOUT_MS);
      final boolean idiomaSeleccionado = hasVisibleText(appPage, "Idioma seleccionado", DEFAULT_TIMEOUT_MS);
      report.put("Detalles de la Cuenta", cuentaCreada && estadoActivo && idiomaSeleccionado);
      details.put(
          "Detalles de la Cuenta",
          "cuentaCreada=" + cuentaCreada + ", estadoActivo=" + estadoActivo + ", idioma=" + idiomaSeleccionado);

      final boolean businessListVisible = hasVisibleText(appPage, "Tus Negocios", DEFAULT_TIMEOUT_MS);
      final boolean agregarNegocioButtonVisible = hasVisibleText(appPage, "Agregar Negocio", DEFAULT_TIMEOUT_MS);
      final boolean businessCountVisible = hasVisibleText(appPage, "Tienes 2 de 3 negocios", DEFAULT_TIMEOUT_MS);
      report.put("Tus Negocios", businessListVisible && agregarNegocioButtonVisible && businessCountVisible);
      details.put(
          "Tus Negocios",
          "businessList="
              + businessListVisible
              + ", agregarButton="
              + agregarNegocioButtonVisible
              + ", businessCount="
              + businessCountVisible);

      LegalValidationResult termsResult =
          openAndValidateLegal(
              appPage,
              "Términos y Condiciones",
              "Términos y Condiciones",
              screenshotsDir,
              "05-terminos-y-condiciones");
      report.put("Términos y Condiciones", termsResult.valid());
      details.put("Términos y Condiciones", termsResult.detail());
      termsUrl = termsResult.url();

      LegalValidationResult privacyResult =
          openAndValidateLegal(
              appPage,
              "Política de Privacidad",
              "Política de Privacidad",
              screenshotsDir,
              "06-politica-de-privacidad");
      report.put("Política de Privacidad", privacyResult.valid());
      details.put("Política de Privacidad", privacyResult.detail());
      privacyUrl = privacyResult.url();

      browser.close();
    } finally {
      writeReport(artifactsDir, report, details, termsUrl, privacyUrl);
    }

    assertTrue("SaleADS Mi Negocio workflow has failing validations. Check target/saleads-artifacts/report.md", allPass(report));
  }

  private Page performGoogleLogin(Page page) {
    Locator loginButton =
        firstVisible(
            List.of(
                page.getByRole(
                    AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName(Pattern.compile("(?i)(sign in|iniciar|continuar).*google"))),
                page.getByText("Sign in with Google"),
                page.getByText("Iniciar con Google"),
                page.getByText("Continuar con Google"),
                page.getByText("Google")));

    if (loginButton == null) {
      throw new AssertionError("Google login button was not found.");
    }

    Page popup = null;
    try {
      popup =
          page.waitForPopup(
              new Page.WaitForPopupOptions().setTimeout(8_000), () -> click(loginButton, page));
    } catch (PlaywrightException ignored) {
      // Popup did not open; login likely continues in current page.
    }

    if (popup != null && !popup.isClosed()) {
      waitForUiLoad(popup);
      selectGoogleAccountIfVisible(popup);
      waitForUiLoad(popup);

      if (hasVisibleText(popup, "Negocio", 12_000)) {
        return popup;
      }

      waitForUiLoad(page);
      return hasVisibleText(page, "Negocio", 12_000) ? page : popup;
    }

    // No popup case: the click already happened inside waitForPopup callback.
    selectGoogleAccountIfVisible(page);
    waitForUiLoad(page);
    return page;
  }

  private void selectGoogleAccountIfVisible(Page page) {
    Locator account = page.getByText(REQUIRED_GOOGLE_ACCOUNT, new Page.GetByTextOptions().setExact(true));
    try {
      if (account.first().isVisible(new Locator.IsVisibleOptions().setTimeout(8_000))) {
        click(account.first(), page);
      }
    } catch (PlaywrightException ignored) {
      // The account picker may not appear if session already exists.
    }
  }

  private void expandMiNegocioMenu(Page page) {
    clickByText(page, "Negocio");
    clickByText(page, "Mi Negocio");
    waitForUiLoad(page);
  }

  private LegalValidationResult openAndValidateLegal(
      Page appPage,
      String linkText,
      String headingText,
      Path screenshotsDir,
      String screenshotName)
      throws IOException {
    final String originalUrl = appPage.url();
    Page newPage = null;

    try {
      newPage =
          appPage.waitForPopup(
              new Page.WaitForPopupOptions().setTimeout(8_000),
              () -> clickByText(appPage, linkText));
    } catch (PlaywrightException ignored) {
      // No popup: link probably navigated in the same tab after the click.
    }

    Page legalPage = (newPage != null) ? newPage : appPage;
    waitForUiLoad(legalPage);

    boolean headingVisible = hasVisibleText(legalPage, headingText, DEFAULT_TIMEOUT_MS);
    boolean legalContentVisible = safeBodyText(legalPage).length() > 200;
    captureScreenshot(legalPage, screenshotsDir, screenshotName, true);

    String finalUrl = legalPage.url();
    String detail = "heading=" + headingVisible + ", contentVisible=" + legalContentVisible + ", url=" + finalUrl;

    if (newPage != null) {
      newPage.close();
      appPage.bringToFront();
      waitForUiLoad(appPage);
    } else if (!originalUrl.equals(appPage.url())) {
      appPage.goBack();
      waitForUiLoad(appPage);
    }

    return new LegalValidationResult(headingVisible && legalContentVisible, detail, finalUrl);
  }

  private void clickByText(Page page, String text) {
    Locator locator =
        firstVisible(
            List.of(
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + Pattern.quote(text)))),
                page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + Pattern.quote(text)))),
                page.getByText(text, new Page.GetByTextOptions().setExact(true)),
                page.getByText(text)));

    if (locator == null) {
      throw new AssertionError("Could not locate clickable text: " + text);
    }
    click(locator, page);
  }

  private void click(Locator locator, Page page) {
    locator.first().click();
    waitForUiLoad(page);
  }

  private void waitForUiLoad(Page page) {
    try {
      page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    } catch (PlaywrightException ignored) {
      // Some SPA interactions do not trigger full document state changes.
    }

    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(6_000));
    } catch (PlaywrightException ignored) {
      // Network idle may not occur due to long polling.
    }
  }

  private boolean hasVisibleText(Page page, String text, double timeoutMs) {
    try {
      page.getByText(text, new Page.GetByTextOptions().setExact(false))
          .first()
          .waitFor(
              new Locator.WaitForOptions()
                  .setState(WaitForSelectorState.VISIBLE)
                  .setTimeout(timeoutMs));
      return true;
    } catch (PlaywrightException ignored) {
      return false;
    }
  }

  private Locator firstVisible(List<Locator> candidates) {
    for (Locator candidate : candidates) {
      try {
        candidate
            .first()
            .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(6_000));
        return candidate.first();
      } catch (PlaywrightException ignored) {
        // Try next candidate.
      }
    }
    return null;
  }

  private boolean isAnyVisible(List<Locator> candidates) {
    for (Locator locator : candidates) {
      try {
        if (locator.first().isVisible()) {
          return true;
        }
      } catch (PlaywrightException ignored) {
        // Ignore and continue searching.
      }
    }
    return false;
  }

  private String safeBodyText(Page page) {
    try {
      String text = page.locator("body").innerText();
      return text == null ? "" : text.trim();
    } catch (PlaywrightException ex) {
      return "";
    }
  }

  private boolean hasNameLikeText(String bodyText) {
    String[] lines = bodyText.split("\\R");
    for (String rawLine : lines) {
      String line = rawLine.trim();
      if (line.isEmpty() || line.length() < 3 || line.length() > 80) {
        continue;
      }
      if (line.contains("@")) {
        continue;
      }
      if (line.matches(".*\\d.*")) {
        continue;
      }
      if (line.equalsIgnoreCase("Información General")
          || line.equalsIgnoreCase("Detalles de la Cuenta")
          || line.equalsIgnoreCase("Tus Negocios")
          || line.equalsIgnoreCase("Sección Legal")
          || line.equalsIgnoreCase("BUSINESS PLAN")
          || line.equalsIgnoreCase("Cambiar Plan")) {
        continue;
      }

      Matcher matcher = Pattern.compile("^[\\p{L}][\\p{L} .'-]{2,}$").matcher(line);
      if (matcher.find()) {
        return true;
      }
    }
    return false;
  }

  private void captureScreenshot(Page page, Path screenshotsDir, String label, boolean fullPage)
      throws IOException {
    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    String safeLabel = label.replaceAll("[^a-zA-Z0-9._-]", "_");
    Path outputPath = screenshotsDir.resolve(timestamp + "-" + safeLabel + ".png");

    page.screenshot(new Page.ScreenshotOptions().setPath(outputPath).setFullPage(fullPage));
  }

  private void writeReport(
      Path artifactsDir,
      Map<String, Boolean> report,
      Map<String, String> details,
      String termsUrl,
      String privacyUrl)
      throws IOException {
    StringBuilder builder = new StringBuilder();
    builder.append("# SaleADS Mi Negocio Workflow Report").append(System.lineSeparator()).append(System.lineSeparator());
    builder
        .append("Generated at: ")
        .append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        .append(System.lineSeparator())
        .append(System.lineSeparator());

    for (Map.Entry<String, Boolean> entry : report.entrySet()) {
      builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL").append(System.lineSeparator());
      builder.append("  - Details: ").append(details.getOrDefault(entry.getKey(), "n/a")).append(System.lineSeparator());
    }

    builder.append(System.lineSeparator());
    builder.append("## Legal URLs").append(System.lineSeparator());
    builder.append("- Términos y Condiciones: ").append(termsUrl).append(System.lineSeparator());
    builder.append("- Política de Privacidad: ").append(privacyUrl).append(System.lineSeparator());

    Files.createDirectories(artifactsDir);
    Files.writeString(artifactsDir.resolve("report.md"), builder.toString());
  }

  private boolean allPass(Map<String, Boolean> report) {
    for (boolean status : report.values()) {
      if (!status) {
        return false;
      }
    }
    return true;
  }

  private record LegalValidationResult(boolean valid, String detail, String url) {}
}
