package io.proleap.saleads.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * End-to-end workflow validation for SaleADS Mi Negocio module.
 *
 * <p>Required runtime input:
 * <ul>
 *   <li>Environment variable {@code SALEADS_LOGIN_URL} (or system property {@code saleads.loginUrl})</li>
 * </ul>
 *
 * <p>Optional runtime input:
 * <ul>
 *   <li>Environment variable {@code SALEADS_GOOGLE_ACCOUNT} (defaults to juanlucasbarbiergarzon@gmail.com)</li>
 *   <li>System property {@code saleads.headless} (defaults to true)</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

  private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
  private static final Pattern GOOGLE_SIGN_IN_PATTERN = Pattern.compile(
      "(?i)(sign in with google|iniciar sesi[oó]n con google|inicia sesi[oó]n con google|continuar con google|google)"
  );
  private static final Pattern BUSINESS_CAPACITY_PATTERN =
      Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios");

  private final Map<String, Boolean> stepResults = new LinkedHashMap<>();
  private final Map<String, String> stepErrors = new LinkedHashMap<>();

  private Playwright playwright;
  private Browser browser;
  private BrowserContext context;
  private Page appPage;
  private Path evidenceDir;
  private String termsAndConditionsUrl;
  private String privacyPolicyUrl;

  @Before
  public void setUp() throws IOException {
    String loginUrl = firstNonBlank(
        System.getProperty("saleads.loginUrl"),
        System.getenv("SALEADS_LOGIN_URL"),
        System.getenv("SALEADS_URL")
    );

    Assume.assumeTrue(
        "Set SALEADS_LOGIN_URL (or -Dsaleads.loginUrl) to execute SaleADS E2E workflow.",
        loginUrl != null && !loginUrl.isBlank()
    );

    String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneOffset.UTC)
        .format(Instant.now());
    evidenceDir = Paths.get("target", "saleads-evidence", timestamp);
    Files.createDirectories(evidenceDir);

    boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "true"));

    playwright = Playwright.create();
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
    context = browser.newContext(
        new Browser.NewContextOptions().setViewportSize(1440, 900)
    );
    appPage = context.newPage();
    appPage.navigate(loginUrl);
    waitForUiLoad(appPage);
  }

  @After
  public void tearDown() throws IOException {
    try {
      writeFinalReport();
    } finally {
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
  }

  @Test
  public void saleadsMiNegocioFullWorkflow() {
    runStep("Login", this::validateLoginWithGoogleAndDashboard);
    runStep("Mi Negocio menu", this::validateMiNegocioMenu);
    runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
    runStep("Administrar Negocios view", this::validateAdministrarNegociosView);
    runStep("Información General", this::validateInformacionGeneralSection);
    runStep("Detalles de la Cuenta", this::validateDetallesCuentaSection);
    runStep("Tus Negocios", this::validateTusNegociosSection);
    runStep("Términos y Condiciones", this::validateTermsAndConditions);
    runStep("Política de Privacidad", this::validatePrivacyPolicy);

    boolean hasFailures = stepResults.values().stream().anyMatch(result -> !result);
    Assert.assertFalse(
        "One or more Mi Negocio workflow steps failed. See final report under " + evidenceDir,
        hasFailures
    );
  }

  private void validateLoginWithGoogleAndDashboard() {
    Locator loginButton = firstVisible(
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(GOOGLE_SIGN_IN_PATTERN)),
        appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(GOOGLE_SIGN_IN_PATTERN)),
        appPage.getByText(GOOGLE_SIGN_IN_PATTERN)
    );

    Page popupPage = clickAndMaybeCaptureNewTab(loginButton, appPage);
    if (popupPage != null) {
      selectGoogleAccountIfVisible(popupPage);
    } else {
      selectGoogleAccountIfVisible(appPage);
    }

    waitForUiLoad(appPage);
    assertAnyVisible("Main application interface",
        appPage.getByText(Pattern.compile("(?i)dashboard|panel|inicio|mi negocio|negocio")),
        appPage.locator("main"),
        appPage.locator("aside"));

    assertAnyVisible("Left sidebar navigation",
        appPage.locator("aside"),
        appPage.locator("nav"),
        appPage.getByText(Pattern.compile("(?i)negocio")));

    captureScreenshot("01-dashboard-loaded.png", appPage, true);
  }

  private void validateMiNegocioMenu() {
    openMiNegocioSubmenu();

    assertAnyVisible("Agregar Negocio submenu option",
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)agregar negocio"))),
        appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)agregar negocio"))),
        appPage.getByText(Pattern.compile("(?i)agregar negocio")));

    assertAnyVisible("Administrar Negocios submenu option",
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)administrar negocios"))),
        appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)administrar negocios"))),
        appPage.getByText(Pattern.compile("(?i)administrar negocios")));

    captureScreenshot("02-mi-negocio-menu-expanded.png", appPage, true);
  }

  private void validateAgregarNegocioModal() {
    openMiNegocioSubmenu();
    clickAndWait(firstVisible(
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)agregar negocio"))),
        appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)agregar negocio"))),
        appPage.getByText(Pattern.compile("(?i)agregar negocio"))
    ), appPage);

    assertAnyVisible("Crear Nuevo Negocio modal title",
        appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)crear nuevo negocio"))),
        appPage.getByText(Pattern.compile("(?i)crear nuevo negocio")));

    assertAnyVisible("Nombre del Negocio input",
        appPage.getByLabel(Pattern.compile("(?i)nombre del negocio")),
        appPage.getByPlaceholder(Pattern.compile("(?i)nombre del negocio")),
        appPage.locator("input"));

    assertAnyVisible("Tienes 2 de 3 negocios text",
        appPage.getByText(BUSINESS_CAPACITY_PATTERN));

    assertAnyVisible("Cancelar button",
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cancelar"))));
    assertAnyVisible("Crear Negocio button",
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)crear negocio"))));

    captureScreenshot("03-agregar-negocio-modal.png", appPage, true);

    Locator nombreNegocioInput = firstVisible(
        appPage.getByLabel(Pattern.compile("(?i)nombre del negocio")),
        appPage.getByPlaceholder(Pattern.compile("(?i)nombre del negocio")),
        appPage.locator("input")
    );
    nombreNegocioInput.click();
    nombreNegocioInput.fill("Negocio Prueba Automatizacion");
    waitForUiLoad(appPage);

    clickAndWait(firstVisible(
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cancelar"))),
        appPage.getByText(Pattern.compile("(?i)cancelar"))
    ), appPage);
  }

  private void validateAdministrarNegociosView() {
    openMiNegocioSubmenu();
    clickAndWait(firstVisible(
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)administrar negocios"))),
        appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)administrar negocios"))),
        appPage.getByText(Pattern.compile("(?i)administrar negocios"))
    ), appPage);

    waitForUiLoad(appPage);

    assertAnyVisible("Información General section",
        appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)informaci[oó]n general"))),
        appPage.getByText(Pattern.compile("(?i)informaci[oó]n general")));
    assertAnyVisible("Detalles de la Cuenta section",
        appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)detalles de la cuenta"))),
        appPage.getByText(Pattern.compile("(?i)detalles de la cuenta")));
    assertAnyVisible("Tus Negocios section",
        appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)tus negocios"))),
        appPage.getByText(Pattern.compile("(?i)tus negocios")));
    assertAnyVisible("Sección Legal section",
        appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)secci[oó]n legal"))),
        appPage.getByText(Pattern.compile("(?i)secci[oó]n legal")));

    captureScreenshot("04-administrar-negocios-view.png", appPage, true);
  }

  private void validateInformacionGeneralSection() {
    assertAnyVisible("User name visible",
        appPage.locator("section").getByText(Pattern.compile("(?i)usuario|nombre")),
        appPage.getByText(Pattern.compile("(?i)usuario|nombre")));
    assertAnyVisible("User email visible",
        appPage.getByText(Pattern.compile("(?i)[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")));
    assertAnyVisible("BUSINESS PLAN text visible",
        appPage.getByText(Pattern.compile("(?i)business plan")));
    assertAnyVisible("Cambiar Plan button visible",
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cambiar plan"))),
        appPage.getByText(Pattern.compile("(?i)cambiar plan")));
  }

  private void validateDetallesCuentaSection() {
    assertAnyVisible("Cuenta creada visible",
        appPage.getByText(Pattern.compile("(?i)cuenta creada")));
    assertAnyVisible("Estado activo visible",
        appPage.getByText(Pattern.compile("(?i)estado activo|activo")));
    assertAnyVisible("Idioma seleccionado visible",
        appPage.getByText(Pattern.compile("(?i)idioma seleccionado|idioma")));
  }

  private void validateTusNegociosSection() {
    assertAnyVisible("Business list visible",
        appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)tus negocios"))),
        appPage.getByText(Pattern.compile("(?i)tus negocios")));
    assertAnyVisible("Agregar Negocio button exists",
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)agregar negocio"))),
        appPage.getByText(Pattern.compile("(?i)agregar negocio")));
    assertAnyVisible("Tienes 2 de 3 negocios text visible",
        appPage.getByText(BUSINESS_CAPACITY_PATTERN));
  }

  private void validateTermsAndConditions() {
    termsAndConditionsUrl = validateLegalLinkAndReturn(
        "Términos y Condiciones",
        Pattern.compile("(?i)t[ée]rminos y condiciones"),
        "05-terminos-y-condiciones.png"
    );
  }

  private void validatePrivacyPolicy() {
    privacyPolicyUrl = validateLegalLinkAndReturn(
        "Política de Privacidad",
        Pattern.compile("(?i)pol[ií]tica de privacidad"),
        "06-politica-de-privacidad.png"
    );
  }

  private String validateLegalLinkAndReturn(
      String linkText,
      Pattern headingPattern,
      String screenshotName
  ) {
    String applicationUrl = appPage.url();
    Locator link = firstVisible(
        appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(headingPattern)),
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(headingPattern)),
        appPage.getByText(headingPattern)
    );

    Page legalPage = clickAndMaybeCaptureNewTab(link, appPage);
    boolean openedInNewTab = legalPage != null && legalPage != appPage;
    Page targetPage = openedInNewTab ? legalPage : appPage;

    waitForUiLoad(targetPage);
    assertAnyVisible(linkText + " heading visible",
        targetPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(headingPattern)),
        targetPage.getByText(headingPattern));
    assertAnyVisible(linkText + " legal content visible",
        targetPage.locator("p"),
        targetPage.locator("article"),
        targetPage.locator("main"));

    captureScreenshot(screenshotName, targetPage, true);
    String finalUrl = targetPage.url();

    if (openedInNewTab) {
      legalPage.close();
      appPage.bringToFront();
      waitForUiLoad(appPage);
    } else if (!Objects.equals(applicationUrl, appPage.url())) {
      try {
        appPage.goBack(new Page.GoBackOptions().setTimeout(15000));
      } catch (PlaywrightException ignored) {
        appPage.navigate(applicationUrl);
      }
      waitForUiLoad(appPage);
    }

    return finalUrl;
  }

  private void openMiNegocioSubmenu() {
    Locator negocioMenu = firstVisible(
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^negocio$|negocio"))),
        appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^negocio$|negocio"))),
        appPage.getByText(Pattern.compile("(?i)^negocio$|negocio"))
    );
    clickAndWait(negocioMenu, appPage);

    Locator miNegocio = firstVisible(
        appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)mi negocio"))),
        appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)mi negocio"))),
        appPage.getByText(Pattern.compile("(?i)mi negocio"))
    );
    clickAndWait(miNegocio, appPage);

    waitForUiLoad(appPage);
  }

  private void selectGoogleAccountIfVisible(Page page) {
    String accountEmail = firstNonBlank(
        System.getenv("SALEADS_GOOGLE_ACCOUNT"),
        DEFAULT_GOOGLE_ACCOUNT
    );
    Locator accountOption = page.getByText(accountEmail);
    if (isVisible(accountOption)) {
      clickAndWait(accountOption, page);
    }
  }

  private void clickAndWait(Locator locator, Page page) {
    Locator target = locator.first();
    target.waitFor(new Locator.WaitForOptions()
        .setState(WaitForSelectorState.VISIBLE)
        .setTimeout(20000));
    target.click();
    waitForUiLoad(page);
  }

  private Page clickAndMaybeCaptureNewTab(Locator locator, Page currentPage) {
    try {
      Page popup = context.waitForPage(() -> locator.first().click());
      waitForUiLoad(popup);
      return popup;
    } catch (PlaywrightException ignored) {
      clickAndWait(locator, currentPage);
      return null;
    }
  }

  private void waitForUiLoad(Page page) {
    try {
      page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    } catch (PlaywrightException ignored) {
      // Some SPA transitions do not trigger a full document event.
    }
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10000));
    } catch (PlaywrightException ignored) {
      // Keep the workflow resilient on pages with long-lived network calls.
    }
    page.waitForTimeout(500);
  }

  private void assertAnyVisible(String assertionLabel, Locator... candidates) {
    for (Locator candidate : candidates) {
      if (isVisible(candidate)) {
        return;
      }
    }
    throw new AssertionError("Expected visible element for: " + assertionLabel);
  }

  private Locator firstVisible(Locator... candidates) {
    for (Locator candidate : candidates) {
      if (isVisible(candidate)) {
        return candidate;
      }
    }
    throw new AssertionError("Could not find any visible locator among the provided candidates.");
  }

  private boolean isVisible(Locator locator) {
    try {
      if (locator.count() == 0) {
        return false;
      }
      return locator.first().isVisible();
    } catch (PlaywrightException ignored) {
      return false;
    }
  }

  private void captureScreenshot(String filename, Page page, boolean fullPage) {
    page.screenshot(new Page.ScreenshotOptions()
        .setPath(evidenceDir.resolve(filename))
        .setFullPage(fullPage));
  }

  private void runStep(String stepName, StepAction action) {
    try {
      action.execute();
      stepResults.put(stepName, true);
    } catch (Throwable throwable) {
      stepResults.put(stepName, false);
      stepErrors.put(stepName, throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
    }
  }

  private void writeFinalReport() throws IOException {
    if (evidenceDir == null) {
      return;
    }

    StringBuilder reportBuilder = new StringBuilder();
    reportBuilder.append("SaleADS Mi Negocio Full Test Report\n");
    reportBuilder.append("===================================\n\n");
    reportBuilder.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append('\n');
    reportBuilder.append("Términos y Condiciones URL: ").append(defaultString(termsAndConditionsUrl)).append('\n');
    reportBuilder.append("Política de Privacidad URL: ").append(defaultString(privacyPolicyUrl)).append('\n');
    reportBuilder.append('\n');

    for (Map.Entry<String, Boolean> entry : stepResults.entrySet()) {
      reportBuilder.append("- ")
          .append(entry.getKey())
          .append(": ")
          .append(entry.getValue() ? "PASS" : "FAIL")
          .append('\n');
      if (!entry.getValue() && stepErrors.containsKey(entry.getKey())) {
        reportBuilder.append("  reason: ").append(stepErrors.get(entry.getKey())).append('\n');
      }
    }

    if (!stepErrors.isEmpty()) {
      reportBuilder.append('\n')
          .append("Failures:\n")
          .append(stepErrors.entrySet().stream()
              .map(entry -> "* " + entry.getKey() + " -> " + entry.getValue())
              .collect(Collectors.joining("\n")))
          .append('\n');
    }

    String report = reportBuilder.toString();
    Files.writeString(evidenceDir.resolve("final-report.txt"), report, StandardCharsets.UTF_8);
    System.out.println(report);
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private String defaultString(String value) {
    return value == null || value.isBlank() ? "N/A" : value;
  }

  @FunctionalInterface
  private interface StepAction {
    void execute();
  }
}
