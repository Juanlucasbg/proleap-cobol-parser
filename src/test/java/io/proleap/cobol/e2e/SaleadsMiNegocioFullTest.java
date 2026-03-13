package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class SaleadsMiNegocioFullTest {

  private static final Pattern LOGIN_WITH_GOOGLE_PATTERN =
      Pattern.compile("(?i)(sign\\s*in\\s*with\\s*google|iniciar\\s*sesi[oó]n\\s*con\\s*google)");
  private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

  private static final String STEP_LOGIN = "Login";
  private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
  private static final String STEP_AGREGAR_MODAL = "Agregar Negocio modal";
  private static final String STEP_ADMINISTRAR_VIEW = "Administrar Negocios view";
  private static final String STEP_INFO_GENERAL = "Información General";
  private static final String STEP_DETALLES_CUENTA = "Detalles de la Cuenta";
  private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
  private static final String STEP_TERMINOS = "Términos y Condiciones";
  private static final String STEP_PRIVACIDAD = "Política de Privacidad";

  private final LinkedHashMap<String, StepStatus> stepReport = new LinkedHashMap<>();
  private final LinkedHashMap<String, String> legalUrls = new LinkedHashMap<>();

  private Playwright playwright;
  private Browser browser;
  private BrowserContext context;
  private Page page;
  private Path evidenceDir;

  @Before
  public void setUp() throws IOException {
    initializeReport();

    final String timestamp =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    evidenceDir = Paths.get("target", "saleads-evidence", timestamp);
    Files.createDirectories(evidenceDir);

    playwright = Playwright.create();
    final boolean headless = Boolean.parseBoolean(getConfig("saleads.headless", "true"));
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
    context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1200));
    context.setDefaultTimeout(20000);
    page = context.newPage();

    final String loginUrl = getConfig("saleads.login.url", "");
    if (!loginUrl.isBlank()) {
      page.navigate(loginUrl);
      waitForUi(page);
    }
  }

  @After
  public void tearDown() {
    try {
      printFinalReport();
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
    runStep(
        STEP_LOGIN,
        () -> {
          Locator loginButton = requireVisible("Login with Google button", page.getByText(LOGIN_WITH_GOOGLE_PATTERN));
          clickGoogleLoginAndSelectAccount(loginButton);

          requireVisible("Main app interface", page.getByText(Pattern.compile("(?i)(dashboard|panel|inicio|negocio|mi\\s+negocio)")));
          assertTrue("Left sidebar should be visible", isVisible(page.locator("aside, nav")));
          screenshot("01-dashboard-loaded", false, page);
        });

    runStep(
        STEP_MI_NEGOCIO_MENU,
        () -> {
          clickByText(page, Pattern.compile("(?i)^\\s*Mi\\s+Negocio\\s*$"), "Mi Negocio menu");
          waitForUi(page);

          requireVisible("Agregar Negocio item", page.getByText(Pattern.compile("(?i)^\\s*Agregar\\s+Negocio\\s*$")));
          requireVisible("Administrar Negocios item", page.getByText(Pattern.compile("(?i)^\\s*Administrar\\s+Negocios\\s*$")));
          screenshot("02-mi-negocio-menu-expanded", false, page);
        });

    runStep(
        STEP_AGREGAR_MODAL,
        () -> {
          clickByText(page, Pattern.compile("(?i)^\\s*Agregar\\s+Negocio\\s*$"), "Agregar Negocio");

          requireVisible("Crear Nuevo Negocio title", page.getByText(Pattern.compile("(?i)Crear\\s+Nuevo\\s+Negocio")));
          Locator negocioInput = requireVisible("Nombre del Negocio input", page.getByPlaceholder("Nombre del Negocio"));
          requireVisible("Business limit text", page.getByText(Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios")));
          requireVisible("Cancelar button", page.getByText(Pattern.compile("(?i)^\\s*Cancelar\\s*$")));
          requireVisible("Crear Negocio button", page.getByText(Pattern.compile("(?i)^\\s*Crear\\s+Negocio\\s*$")));
          screenshot("03-agregar-negocio-modal", false, page);

          negocioInput.click();
          negocioInput.fill("Negocio Prueba Automatizacion");
          clickByText(page, Pattern.compile("(?i)^\\s*Cancelar\\s*$"), "Cancelar modal");
        });

    runStep(
        STEP_ADMINISTRAR_VIEW,
        () -> {
          if (!isVisible(page.getByText(Pattern.compile("(?i)^\\s*Administrar\\s+Negocios\\s*$")))) {
            clickByText(page, Pattern.compile("(?i)^\\s*Mi\\s+Negocio\\s*$"), "Mi Negocio menu");
          }

          clickByText(page, Pattern.compile("(?i)^\\s*Administrar\\s+Negocios\\s*$"), "Administrar Negocios");
          waitForUi(page);

          requireVisible("Informacion General section", page.getByText(Pattern.compile("(?i)Informaci[oó]n\\s+General")));
          requireVisible("Detalles de la Cuenta section", page.getByText(Pattern.compile("(?i)Detalles\\s+de\\s+la\\s+Cuenta")));
          requireVisible("Tus Negocios section", page.getByText(Pattern.compile("(?i)Tus\\s+Negocios")));
          requireVisible("Seccion Legal section", page.getByText(Pattern.compile("(?i)Secci[oó]n\\s+Legal")));
          screenshot("04-administrar-negocios", true, page);
        });

    runStep(
        STEP_INFO_GENERAL,
        () -> {
          assertTrue("User name should be visible", isVisible(page.getByText(Pattern.compile("(?i)^[a-záéíóúñ]+\\s+[a-záéíóúñ].+"))));
          requireVisible("User email", page.getByText(Pattern.compile("(?i).+@.+\\..+")));
          requireVisible("BUSINESS PLAN text", page.getByText(Pattern.compile("(?i)BUSINESS\\s+PLAN")));
          requireVisible("Cambiar Plan button", page.getByText(Pattern.compile("(?i)Cambiar\\s+Plan")));
        });

    runStep(
        STEP_DETALLES_CUENTA,
        () -> {
          requireVisible("'Cuenta creada' text", page.getByText(Pattern.compile("(?i)Cuenta\\s+creada")));
          requireVisible("'Estado activo' text", page.getByText(Pattern.compile("(?i)Estado\\s+activo")));
          requireVisible("'Idioma seleccionado' text", page.getByText(Pattern.compile("(?i)Idioma\\s+seleccionado")));
        });

    runStep(
        STEP_TUS_NEGOCIOS,
        () -> {
          requireVisible("Tus Negocios section", page.getByText(Pattern.compile("(?i)Tus\\s+Negocios")));
          requireVisible("Agregar Negocio button", page.getByText(Pattern.compile("(?i)^\\s*Agregar\\s+Negocio\\s*$")));
          requireVisible("Business limit text", page.getByText(Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios")));
        });

    runStep(
        STEP_TERMINOS,
        () -> {
          String url =
              openLegalLinkAndValidate(
                  "Términos y Condiciones",
                  Pattern.compile("(?i)T[ée]rminos\\s+y\\s+Condiciones"),
                  "05-terminos-y-condiciones");
          legalUrls.put("Términos y Condiciones URL", url);
        });

    runStep(
        STEP_PRIVACIDAD,
        () -> {
          String url =
              openLegalLinkAndValidate(
                  "Política de Privacidad",
                  Pattern.compile("(?i)Pol[íi]tica\\s+de\\s+Privacidad"),
                  "06-politica-de-privacidad");
          legalUrls.put("Política de Privacidad URL", url);
        });

    assertAllStepsPassed();
  }

  private void initializeReport() {
    stepReport.put(STEP_LOGIN, StepStatus.pending());
    stepReport.put(STEP_MI_NEGOCIO_MENU, StepStatus.pending());
    stepReport.put(STEP_AGREGAR_MODAL, StepStatus.pending());
    stepReport.put(STEP_ADMINISTRAR_VIEW, StepStatus.pending());
    stepReport.put(STEP_INFO_GENERAL, StepStatus.pending());
    stepReport.put(STEP_DETALLES_CUENTA, StepStatus.pending());
    stepReport.put(STEP_TUS_NEGOCIOS, StepStatus.pending());
    stepReport.put(STEP_TERMINOS, StepStatus.pending());
    stepReport.put(STEP_PRIVACIDAD, StepStatus.pending());
  }

  private void runStep(final String stepName, final Runnable stepLogic) {
    try {
      stepLogic.run();
      stepReport.put(stepName, StepStatus.pass("PASS"));
    } catch (Throwable error) {
      stepReport.put(stepName, StepStatus.fail(error.getMessage()));
      screenshot("failure-" + sanitize(stepName), true, page);
    }
  }

  private void assertAllStepsPassed() {
    StringBuilder failures = new StringBuilder();
    for (Map.Entry<String, StepStatus> entry : stepReport.entrySet()) {
      if (!entry.getValue().passed) {
        failures
            .append("- ")
            .append(entry.getKey())
            .append(": ")
            .append(entry.getValue().detail)
            .append('\n');
      }
    }

    if (failures.length() > 0) {
      throw new AssertionError("Final report contains failed steps:\n" + failures);
    }
  }

  private void clickGoogleLoginAndSelectAccount(final Locator loginButton) {
    Page googlePage = null;
    try {
      googlePage =
          page.waitForPopup(
              () -> clickAndWait(page, loginButton),
              new Page.WaitForPopupOptions().setTimeout(7000));
    } catch (PlaywrightException ignored) {
      clickAndWait(page, loginButton);
    }

    Page accountSelectionPage = googlePage == null ? page : googlePage;

    Locator account =
        accountSelectionPage.getByText(
            Pattern.compile("(?i)^\\s*" + Pattern.quote(GOOGLE_ACCOUNT_EMAIL) + "\\s*$"));
    if (isVisible(account)) {
      clickAndWait(accountSelectionPage, account);
    }

    if (googlePage != null) {
      try {
        googlePage.waitForTimeout(1500);
      } catch (PlaywrightException ignored) {
        // Continue even if popup timing is different.
      }
    }

    page.bringToFront();
    waitForUi(page);
  }

  private String openLegalLinkAndValidate(
      final String linkText, final Pattern headingPattern, final String screenshotName) {
    Page legalPage = page;
    boolean openedPopup = false;

    try {
      legalPage =
          page.waitForPopup(
              () -> clickByText(page, Pattern.compile("(?i)^\\s*" + Pattern.quote(linkText) + "\\s*$"), linkText),
              new Page.WaitForPopupOptions().setTimeout(5000));
      openedPopup = true;
    } catch (PlaywrightException noPopup) {
      clickByText(page, Pattern.compile("(?i)^\\s*" + Pattern.quote(linkText) + "\\s*$"), linkText);
      legalPage = page;
    }

    waitForUi(legalPage);
    requireVisible("Legal heading: " + linkText, legalPage.getByText(headingPattern));
    String legalContent = legalPage.locator("body").innerText();
    assertTrue("Expected legal content text to be visible", legalContent != null && legalContent.trim().length() > 120);
    screenshot(screenshotName, true, legalPage);
    String finalUrl = legalPage.url();

    if (openedPopup) {
      legalPage.close();
      page.bringToFront();
      waitForUi(page);
    } else {
      page.goBack();
      waitForUi(page);
    }

    return finalUrl;
  }

  private void clickByText(final Page targetPage, final Pattern pattern, final String description) {
    Locator target = requireVisible(description, targetPage.getByText(pattern));
    clickAndWait(targetPage, target);
  }

  private void clickAndWait(final Page targetPage, final Locator locator) {
    locator.first().click();
    waitForUi(targetPage);
  }

  private Locator requireVisible(final String description, final Locator... candidates) {
    for (Locator candidate : candidates) {
      if (isVisible(candidate)) {
        return candidate.first();
      }
    }

    throw new AssertionError("Could not find visible element: " + description);
  }

  private boolean isVisible(final Locator locator) {
    try {
      locator.first().waitFor(new Locator.WaitForOptions().setTimeout(8000));
      return locator.first().isVisible();
    } catch (PlaywrightException e) {
      return false;
    }
  }

  private void waitForUi(final Page targetPage) {
    try {
      targetPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
      targetPage.waitForLoadState(LoadState.NETWORKIDLE);
    } catch (PlaywrightException ignored) {
      // Some UI actions do not trigger network activity; still pause briefly for paint.
    }
    targetPage.waitForTimeout(800);
  }

  private void screenshot(final String name, final boolean fullPage, final Page targetPage) {
    if (targetPage == null) {
      return;
    }
    try {
      targetPage.screenshot(
          new Page.ScreenshotOptions()
              .setPath(evidenceDir.resolve(sanitize(name) + ".png"))
              .setFullPage(fullPage));
    } catch (PlaywrightException ignored) {
      // Best effort evidence capture.
    }
  }

  private void printFinalReport() {
    System.out.println("===== SaleADS Mi Negocio Final Report =====");
    for (Map.Entry<String, StepStatus> entry : stepReport.entrySet()) {
      String status = entry.getValue().passed ? "PASS" : "FAIL";
      System.out.println(entry.getKey() + ": " + status);
      if (!entry.getValue().passed) {
        System.out.println("  Reason: " + entry.getValue().detail);
      }
    }
    if (!legalUrls.isEmpty()) {
      System.out.println("Captured legal URLs:");
      for (Map.Entry<String, String> urlEntry : legalUrls.entrySet()) {
        System.out.println(" - " + urlEntry.getKey() + ": " + urlEntry.getValue());
      }
    }
    System.out.println("Evidence folder: " + evidenceDir.toAbsolutePath());
    System.out.println("===========================================");
  }

  private String sanitize(final String value) {
    return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
  }

  private String getConfig(final String propertyName, final String defaultValue) {
    String property = System.getProperty(propertyName);
    if (property != null && !property.isBlank()) {
      return property;
    }
    String env = System.getenv(propertyName.toUpperCase().replace('.', '_'));
    if (env != null && !env.isBlank()) {
      return env;
    }
    return defaultValue;
  }

  private static class StepStatus {
    private final boolean passed;
    private final String detail;

    private StepStatus(final boolean passed, final String detail) {
      this.passed = passed;
      this.detail = detail == null ? "" : detail;
    }

    private static StepStatus pending() {
      return new StepStatus(false, "Not executed");
    }

    private static StepStatus pass(final String detail) {
      return new StepStatus(true, detail);
    }

    private static StepStatus fail(final String detail) {
      return new StepStatus(false, detail);
    }
  }
}
