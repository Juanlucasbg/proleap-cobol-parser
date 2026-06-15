package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
import com.microsoft.playwright.options.WaitUntilState;

public class SaleadsMiNegocioFullTest {

  private static final String STEP_LOGIN = "Login";
  private static final String STEP_MENU = "Mi Negocio menu";
  private static final String STEP_MODAL = "Agregar Negocio modal";
  private static final String STEP_ADMIN = "Administrar Negocios view";
  private static final String STEP_INFO = "Información General";
  private static final String STEP_ACCOUNT = "Detalles de la Cuenta";
  private static final String STEP_BUSINESSES = "Tus Negocios";
  private static final String STEP_TERMS = "Términos y Condiciones";
  private static final String STEP_PRIVACY = "Política de Privacidad";

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

  @Test
  public void saleadsMiNegocioFullWorkflow() throws Exception {
    final Path outputDir = createOutputDirectory();
    final Map<String, StepResult> results = initResults();

    final String loginUrl = readSetting("SALEADS_LOGIN_URL", "saleads.login.url");
    final boolean headless = Boolean.parseBoolean(readSettingWithDefault("SALEADS_HEADLESS", "saleads.headless", "true"));
    final double slowMo = Double.parseDouble(readSettingWithDefault("SALEADS_SLOW_MO_MS", "saleads.slow.mo.ms", "0"));

    try (Playwright playwright = Playwright.create()) {
      final BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
          .setHeadless(headless)
          .setSlowMo(slowMo);

      try (Browser browser = playwright.chromium().launch(launchOptions);
          BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1920, 1080))) {
        final Page appPage = context.newPage();

        if (isBlank(loginUrl)) {
          failStep(results.get(STEP_LOGIN), "Missing SALEADS_LOGIN_URL / saleads.login.url. Cannot start from login page.");
          failRemainingFrom(results, STEP_MENU, "Prerequisite failed: login did not complete.");
          persistReports(results, outputDir);
          assertTrue("Login precondition failed. Report: " + outputDir.toAbsolutePath(), false);
          return;
        }

        appPage.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        waitForUi(appPage);
        capture(appPage, outputDir, "step0_initial_login_page.png", true, results.get(STEP_LOGIN));

        final boolean loginOk = runLoginStep(appPage, results.get(STEP_LOGIN), outputDir);
        if (!loginOk) {
          failRemainingFrom(results, STEP_MENU, "Prerequisite failed: login did not complete.");
          persistReports(results, outputDir);
          assertTrue("Login step failed. Report: " + outputDir.toAbsolutePath(), false);
          return;
        }

        if (!runMiNegocioMenuStep(appPage, results.get(STEP_MENU), outputDir)) {
          failRemainingFrom(results, STEP_MODAL, "Prerequisite failed: Mi Negocio menu step failed.");
          persistReports(results, outputDir);
          assertTrue("Mi Negocio menu step failed. Report: " + outputDir.toAbsolutePath(), false);
          return;
        }

        if (!runAgregarNegocioModalStep(appPage, results.get(STEP_MODAL), outputDir)) {
          failRemainingFrom(results, STEP_ADMIN, "Prerequisite failed: Agregar Negocio modal step failed.");
          persistReports(results, outputDir);
          assertTrue("Agregar Negocio modal step failed. Report: " + outputDir.toAbsolutePath(), false);
          return;
        }

        if (!runAdministrarNegociosStep(appPage, results.get(STEP_ADMIN), outputDir)) {
          failRemainingFrom(results, STEP_INFO, "Prerequisite failed: Administrar Negocios view step failed.");
          persistReports(results, outputDir);
          assertTrue("Administrar Negocios view failed. Report: " + outputDir.toAbsolutePath(), false);
          return;
        }

        runInformacionGeneralStep(appPage, results.get(STEP_INFO));
        runDetallesCuentaStep(appPage, results.get(STEP_ACCOUNT));
        runTusNegociosStep(appPage, results.get(STEP_BUSINESSES));
        runLegalLinkStep(appPage, context, "T[eé]rminos\\s+y\\s+Condiciones", "T[eé]rminos\\s+y\\s+Condiciones",
            "step8_terminos_y_condiciones.png", results.get(STEP_TERMS), outputDir);
        runLegalLinkStep(appPage, context, "Pol[ií]tica\\s+de\\s+Privacidad", "Pol[ií]tica\\s+de\\s+Privacidad",
            "step9_politica_de_privacidad.png", results.get(STEP_PRIVACY), outputDir);

        persistReports(results, outputDir);
        assertTrue("One or more workflow validations failed. Report: " + outputDir.toAbsolutePath(), allPassed(results));
      }
    }
  }

  private boolean runLoginStep(final Page page, final StepResult result, final Path outputDir) {
    final Locator loginButton = firstVisible(page,
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
            .setName(Pattern.compile("(sign\\s*in|inicia\\s+sesi[oó]n|google)", Pattern.CASE_INSENSITIVE))),
        page.getByText(Pattern.compile("sign\\s*in\\s*with\\s*google", Pattern.CASE_INSENSITIVE)),
        page.getByText(Pattern.compile("inicia\\s+sesi[oó]n\\s+con\\s+google", Pattern.CASE_INSENSITIVE)),
        page.getByText(Pattern.compile("inicia\\s+sesi[oó]n", Pattern.CASE_INSENSITIVE)));

    if (loginButton == null) {
      failStep(result, "Could not find login / Sign in with Google button.");
      return false;
    }

    clickAndWait(page, loginButton);

    final Locator accountChooser = page.getByText(Pattern.compile("juanlucasbarbiergarzon@gmail\\.com", Pattern.CASE_INSENSITIVE)).first();
    if (isVisible(accountChooser, 5_000)) {
      clickAndWait(page, accountChooser);
    }

    waitForUi(page);
    capture(page, outputDir, "step1_dashboard_after_login.png", true, result);

    final boolean leftSidebarVisible = isVisible(page.locator("aside"), 10_000)
        || isVisible(page.getByRole(AriaRole.NAVIGATION), 10_000)
        || isVisible(page.getByText(Pattern.compile("Negocio", Pattern.CASE_INSENSITIVE)).first(), 10_000);
    final boolean stillAtIdentityProvider = page.url().contains("accounts.google.")
        || page.url().contains("keycloak");

    if (leftSidebarVisible && !stillAtIdentityProvider) {
      passStep(result, "Main application UI and left sidebar are visible.");
      return true;
    }

    failStep(result, "Login did not reach authenticated app shell. Current URL: " + page.url());
    return false;
  }

  private boolean runMiNegocioMenuStep(final Page page, final StepResult result, final Path outputDir) {
    final Locator miNegocio = firstVisible(page,
        page.getByRole(AriaRole.LINK,
            new Page.GetByRoleOptions().setName(Pattern.compile("Mi\\s+Negocio", Pattern.CASE_INSENSITIVE))),
        page.getByRole(AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName(Pattern.compile("Mi\\s+Negocio", Pattern.CASE_INSENSITIVE))),
        page.getByText(Pattern.compile("Mi\\s+Negocio", Pattern.CASE_INSENSITIVE)).first());

    if (miNegocio == null) {
      failStep(result, "Could not find 'Mi Negocio' in the left sidebar.");
      return false;
    }

    clickAndWait(page, miNegocio);
    final boolean addVisible = isVisible(page.getByText(Pattern.compile("Agregar\\s+Negocio", Pattern.CASE_INSENSITIVE)).first(), 10_000);
    final boolean adminVisible =
        isVisible(page.getByText(Pattern.compile("Administrar\\s+Negocios", Pattern.CASE_INSENSITIVE)).first(), 10_000);

    capture(page, outputDir, "step2_mi_negocio_expanded.png", true, result);

    if (addVisible && adminVisible) {
      passStep(result, "Mi Negocio submenu expanded and required options are visible.");
      return true;
    }

    failStep(result, "Submenu did not expose 'Agregar Negocio' and 'Administrar Negocios'.");
    return false;
  }

  private boolean runAgregarNegocioModalStep(final Page page, final StepResult result, final Path outputDir) {
    final Locator addBusiness = page.getByText(Pattern.compile("Agregar\\s+Negocio", Pattern.CASE_INSENSITIVE)).first();
    if (!isVisible(addBusiness, 10_000)) {
      failStep(result, "Could not find 'Agregar Negocio' option.");
      return false;
    }

    clickAndWait(page, addBusiness);

    final Locator modalTitle = page.getByText(Pattern.compile("Crear\\s+Nuevo\\s+Negocio", Pattern.CASE_INSENSITIVE)).first();
    final boolean titleVisible = isVisible(modalTitle, 10_000);
    final boolean nameFieldVisible = isVisible(page.getByLabel(Pattern.compile("Nombre\\s+del\\s+Negocio", Pattern.CASE_INSENSITIVE)).first(), 5_000)
        || isVisible(page.getByPlaceholder(Pattern.compile("Nombre\\s+del\\s+Negocio", Pattern.CASE_INSENSITIVE)).first(), 5_000);
    final boolean quotaVisible = isVisible(page.getByText(Pattern.compile("Tienes\\s+2\\s+de\\s+3\\s+negocios", Pattern.CASE_INSENSITIVE)).first(), 5_000);
    final boolean cancelVisible = isVisible(
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("Cancelar", Pattern.CASE_INSENSITIVE))).first(), 5_000);
    final boolean createVisible = isVisible(
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("Crear\\s+Negocio", Pattern.CASE_INSENSITIVE))).first(), 5_000);

    capture(page, outputDir, "step3_agregar_negocio_modal.png", true, result);

    if (!(titleVisible && nameFieldVisible && quotaVisible && cancelVisible && createVisible)) {
      failStep(result, "Agregar Negocio modal is missing one or more required elements.");
      return false;
    }

    final Locator nameField = firstVisible(page,
        page.getByLabel(Pattern.compile("Nombre\\s+del\\s+Negocio", Pattern.CASE_INSENSITIVE)).first(),
        page.getByPlaceholder(Pattern.compile("Nombre\\s+del\\s+Negocio", Pattern.CASE_INSENSITIVE)).first());

    if (nameField != null) {
      nameField.click();
      nameField.fill("Negocio Prueba Automatizacion");
    }

    final Locator cancelButton =
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("Cancelar", Pattern.CASE_INSENSITIVE))).first();
    if (isVisible(cancelButton, 3_000)) {
      clickAndWait(page, cancelButton);
    }

    passStep(result, "Crear Nuevo Negocio modal validated successfully.");
    return true;
  }

  private boolean runAdministrarNegociosStep(final Page page, final StepResult result, final Path outputDir) {
    final Locator adminLink = page.getByText(Pattern.compile("Administrar\\s+Negocios", Pattern.CASE_INSENSITIVE)).first();
    if (!isVisible(adminLink, 5_000)) {
      final Locator miNegocio = page.getByText(Pattern.compile("Mi\\s+Negocio", Pattern.CASE_INSENSITIVE)).first();
      if (isVisible(miNegocio, 5_000)) {
        clickAndWait(page, miNegocio);
      }
    }

    if (!isVisible(adminLink, 10_000)) {
      failStep(result, "Could not find 'Administrar Negocios'.");
      return false;
    }

    clickAndWait(page, adminLink);

    final boolean infoGeneralVisible =
        isVisible(page.getByText(Pattern.compile("Informaci[oó]n\\s+General", Pattern.CASE_INSENSITIVE)).first(), 10_000);
    final boolean accountDetailsVisible =
        isVisible(page.getByText(Pattern.compile("Detalles\\s+de\\s+la\\s+Cuenta", Pattern.CASE_INSENSITIVE)).first(), 10_000);
    final boolean businessesVisible = isVisible(page.getByText(Pattern.compile("Tus\\s+Negocios", Pattern.CASE_INSENSITIVE)).first(), 10_000);
    final boolean legalVisible = isVisible(
        page.getByText(Pattern.compile("(Secci[oó]n\\s+Legal|T[eé]rminos\\s+y\\s+Condiciones)", Pattern.CASE_INSENSITIVE)).first(), 10_000);

    capture(page, outputDir, "step4_administrar_negocios_page.png", true, result);

    if (infoGeneralVisible && accountDetailsVisible && businessesVisible && legalVisible) {
      passStep(result, "Administrar Negocios view loaded with all required sections.");
      return true;
    }

    failStep(result, "One or more required Administrar Negocios sections are missing.");
    return false;
  }

  private void runInformacionGeneralStep(final Page page, final StepResult result) {
    final boolean userNameVisible = isVisible(
        page.getByText(Pattern.compile("(Usuario|Nombre|Perfil)", Pattern.CASE_INSENSITIVE)).first(), 5_000);
    final boolean emailVisible = isVisible(page.getByText(EMAIL_PATTERN).first(), 5_000);
    final boolean planVisible = isVisible(page.getByText(Pattern.compile("BUSINESS\\s+PLAN", Pattern.CASE_INSENSITIVE)).first(), 5_000);
    final boolean changePlanVisible =
        isVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("Cambiar\\s+Plan", Pattern.CASE_INSENSITIVE))).first(),
            5_000);

    if (userNameVisible && emailVisible && planVisible && changePlanVisible) {
      passStep(result, "Informacion General section validated.");
      return;
    }

    failStep(result, "Informacion General is missing one or more required elements.");
  }

  private void runDetallesCuentaStep(final Page page, final StepResult result) {
    final boolean createdVisible =
        isVisible(page.getByText(Pattern.compile("Cuenta\\s+creada", Pattern.CASE_INSENSITIVE)).first(), 5_000);
    final boolean activeVisible =
        isVisible(page.getByText(Pattern.compile("Estado\\s+activo", Pattern.CASE_INSENSITIVE)).first(), 5_000);
    final boolean languageVisible =
        isVisible(page.getByText(Pattern.compile("Idioma\\s+seleccionado", Pattern.CASE_INSENSITIVE)).first(), 5_000);

    if (createdVisible && activeVisible && languageVisible) {
      passStep(result, "Detalles de la Cuenta section validated.");
      return;
    }

    failStep(result, "Detalles de la Cuenta is missing one or more required labels.");
  }

  private void runTusNegociosStep(final Page page, final StepResult result) {
    final boolean sectionVisible = isVisible(page.getByText(Pattern.compile("Tus\\s+Negocios", Pattern.CASE_INSENSITIVE)).first(), 5_000);
    final boolean addButtonVisible =
        isVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("Agregar\\s+Negocio", Pattern.CASE_INSENSITIVE))).first(),
            5_000);
    final boolean quotaVisible = isVisible(page.getByText(Pattern.compile("Tienes\\s+2\\s+de\\s+3\\s+negocios", Pattern.CASE_INSENSITIVE)).first(), 5_000);
    final boolean businessListVisible = isVisible(page.locator("ul,table,[role='list']").first(), 5_000)
        || isVisible(page.getByText(Pattern.compile("negocio", Pattern.CASE_INSENSITIVE)).first(), 5_000);

    if (sectionVisible && addButtonVisible && quotaVisible && businessListVisible) {
      passStep(result, "Tus Negocios section validated.");
      return;
    }

    failStep(result, "Tus Negocios section is missing required list, button, or quota text.");
  }

  private void runLegalLinkStep(final Page appPage, final BrowserContext context, final String linkPattern,
      final String headingPattern, final String screenshotName, final StepResult result, final Path outputDir) {
    final Locator legalLink = appPage.getByText(Pattern.compile(linkPattern, Pattern.CASE_INSENSITIVE)).first();
    if (!isVisible(legalLink, 10_000)) {
      failStep(result, "Could not locate legal link: " + linkPattern);
      return;
    }

    Page legalPage = null;
    boolean popupOpened = false;
    try {
      legalPage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(8_000), () -> clickAndWait(appPage, legalLink));
      popupOpened = true;
    } catch (PlaywrightException popupNotOpened) {
      clickAndWait(appPage, legalLink);
      legalPage = appPage;
    }

    waitForUi(legalPage);

    final boolean headingVisible = isVisible(legalPage.getByText(Pattern.compile(headingPattern, Pattern.CASE_INSENSITIVE)).first(), 10_000);
    final boolean contentVisible = isVisible(legalPage.locator("p").first(), 5_000)
        || isVisible(legalPage.getByText(Pattern.compile("(datos|privacidad|terminos|condiciones|uso)", Pattern.CASE_INSENSITIVE)).first(), 5_000);

    result.finalUrl = legalPage.url();
    capture(legalPage, outputDir, screenshotName, true, result);

    if (headingVisible && contentVisible) {
      passStep(result, "Legal page validated at URL: " + legalPage.url());
    } else {
      failStep(result, "Legal page did not show expected heading/content. URL: " + legalPage.url());
    }

    if (popupOpened && legalPage != appPage) {
      legalPage.close();
      appPage.bringToFront();
    } else {
      try {
        appPage.goBack(new Page.GoBackOptions().setTimeout(10_000));
        waitForUi(appPage);
      } catch (PlaywrightException ignored) {
        appPage.bringToFront();
      }
    }
  }

  private static Map<String, StepResult> initResults() {
    final Map<String, StepResult> results = new LinkedHashMap<>();
    results.put(STEP_LOGIN, StepResult.defaultFail(STEP_LOGIN));
    results.put(STEP_MENU, StepResult.defaultFail(STEP_MENU));
    results.put(STEP_MODAL, StepResult.defaultFail(STEP_MODAL));
    results.put(STEP_ADMIN, StepResult.defaultFail(STEP_ADMIN));
    results.put(STEP_INFO, StepResult.defaultFail(STEP_INFO));
    results.put(STEP_ACCOUNT, StepResult.defaultFail(STEP_ACCOUNT));
    results.put(STEP_BUSINESSES, StepResult.defaultFail(STEP_BUSINESSES));
    results.put(STEP_TERMS, StepResult.defaultFail(STEP_TERMS));
    results.put(STEP_PRIVACY, StepResult.defaultFail(STEP_PRIVACY));
    return results;
  }

  private void failRemainingFrom(final Map<String, StepResult> results, final String stepName, final String reason) {
    boolean shouldFail = false;
    for (final Map.Entry<String, StepResult> entry : results.entrySet()) {
      if (Objects.equals(entry.getKey(), stepName)) {
        shouldFail = true;
      }
      if (shouldFail && entry.getValue().detail.startsWith("Not executed")) {
        failStep(entry.getValue(), reason);
      }
    }
  }

  private static Locator firstVisible(final Page page, final Locator... options) {
    for (final Locator option : options) {
      if (option != null && isVisible(option, 2_500)) {
        return option;
      }
    }
    return null;
  }

  private static boolean isVisible(final Locator locator, final double timeoutMillis) {
    try {
      return locator.isVisible(new Locator.IsVisibleOptions().setTimeout(timeoutMillis));
    } catch (PlaywrightException e) {
      return false;
    }
  }

  private static void clickAndWait(final Page page, final Locator locator) {
    locator.click(new Locator.ClickOptions().setTimeout(10_000));
    waitForUi(page);
  }

  private static void waitForUi(final Page page) {
    page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(8_000));
    } catch (PlaywrightException ignored) {
      // Some pages keep background requests open indefinitely.
    }
  }

  private static void passStep(final StepResult result, final String detail) {
    result.status = "PASS";
    result.detail = detail;
  }

  private static void failStep(final StepResult result, final String detail) {
    result.status = "FAIL";
    result.detail = detail;
  }

  private static boolean allPassed(final Map<String, StepResult> results) {
    return results.values().stream().allMatch(r -> "PASS".equals(r.status));
  }

  private static void capture(final Page page, final Path outputDir, final String fileName, final boolean fullPage,
      final StepResult stepResult) {
    final Path path = outputDir.resolve(fileName);
    page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
    stepResult.screenshots.add(path.toString());
  }

  private static Path createOutputDirectory() throws IOException {
    final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX")
        .format(OffsetDateTime.now(ZoneOffset.UTC))
        .replace(":", "");
    final Path outputDir = Paths.get("target", "saleads-mi-negocio", timestamp);
    Files.createDirectories(outputDir);
    return outputDir;
  }

  private static String readSetting(final String envName, final String propertyName) {
    final String envValue = System.getenv(envName);
    if (!isBlank(envValue)) {
      return envValue.trim();
    }

    final String propertyValue = System.getProperty(propertyName);
    if (!isBlank(propertyValue)) {
      return propertyValue.trim();
    }

    return null;
  }

  private static String readSettingWithDefault(final String envName, final String propertyName, final String defaultValue) {
    final String value = readSetting(envName, propertyName);
    return isBlank(value) ? defaultValue : value;
  }

  private static boolean isBlank(final String value) {
    return value == null || value.trim().isEmpty();
  }

  private static void persistReports(final Map<String, StepResult> results, final Path outputDir) throws IOException {
    final String json = buildJsonReport(results);
    final String markdown = buildMarkdownReport(results, outputDir);
    Files.writeString(outputDir.resolve("report.json"), json, StandardCharsets.UTF_8);
    Files.writeString(outputDir.resolve("report.md"), markdown, StandardCharsets.UTF_8);
  }

  private static String buildJsonReport(final Map<String, StepResult> results) {
    final StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    sb.append("  \"name\": \"saleads_mi_negocio_full_test\",\n");
    sb.append("  \"generated_at\": \"").append(OffsetDateTime.now(ZoneOffset.UTC)).append("\",\n");
    sb.append("  \"results\": [\n");

    int i = 0;
    for (final StepResult result : results.values()) {
      sb.append("    {\n");
      sb.append("      \"field\": \"").append(escapeJson(result.field)).append("\",\n");
      sb.append("      \"status\": \"").append(result.status).append("\",\n");
      sb.append("      \"detail\": \"").append(escapeJson(result.detail)).append("\",\n");
      sb.append("      \"final_url\": \"").append(escapeJson(result.finalUrl)).append("\",\n");
      sb.append("      \"screenshots\": [");
      for (int s = 0; s < result.screenshots.size(); s++) {
        sb.append("\"").append(escapeJson(result.screenshots.get(s))).append("\"");
        if (s < result.screenshots.size() - 1) {
          sb.append(", ");
        }
      }
      sb.append("]\n");
      sb.append("    }");
      if (i < results.size() - 1) {
        sb.append(",");
      }
      sb.append("\n");
      i++;
    }

    sb.append("  ]\n");
    sb.append("}\n");
    return sb.toString();
  }

  private static String buildMarkdownReport(final Map<String, StepResult> results, final Path outputDir) {
    final StringBuilder sb = new StringBuilder();
    sb.append("# SaleADS Mi Negocio Workflow Report\n\n");
    sb.append("- Generated at: ").append(OffsetDateTime.now(ZoneOffset.UTC)).append("\n");
    sb.append("- Output directory: ").append(outputDir.toAbsolutePath()).append("\n\n");
    sb.append("| Field | Status | Detail |\n");
    sb.append("|---|---|---|\n");

    for (final StepResult result : results.values()) {
      sb.append("| ")
          .append(result.field)
          .append(" | ")
          .append(result.status)
          .append(" | ")
          .append(result.detail.replace("|", "\\|"))
          .append(" |\n");
    }

    sb.append("\n## Evidence\n");
    for (final StepResult result : results.values()) {
      if (!result.screenshots.isEmpty()) {
        sb.append("- ").append(result.field).append(":\n");
        for (final String screenshot : result.screenshots) {
          sb.append("  - ").append(screenshot).append("\n");
        }
      }
      if (!isBlank(result.finalUrl)) {
        sb.append("  - Final URL: ").append(result.finalUrl).append("\n");
      }
    }

    return sb.toString();
  }

  private static String escapeJson(final String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r");
  }

  private static class StepResult {
    private final String field;
    private String status;
    private String detail;
    private String finalUrl;
    private final List<String> screenshots = new ArrayList<>();

    private StepResult(final String field, final String status, final String detail) {
      this.field = field;
      this.status = status;
      this.detail = detail;
      this.finalUrl = "";
    }

    private static StepResult defaultFail(final String field) {
      return new StepResult(field, "FAIL", "Not executed.");
    }
  }
}
