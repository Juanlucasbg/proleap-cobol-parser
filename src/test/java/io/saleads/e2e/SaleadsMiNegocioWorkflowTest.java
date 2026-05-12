package io.saleads.e2e;

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
import java.util.regex.Pattern;
import org.junit.Test;

public class SaleadsMiNegocioWorkflowTest {

  private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
  private static final Pattern GOOGLE_LOGIN_PATTERN =
      Pattern.compile("(sign in|iniciar sesi\\u00f3n).*(google)|google", Pattern.CASE_INSENSITIVE);
  private static final Pattern NEGOCIO_PATTERN = Pattern.compile("Negocio", Pattern.CASE_INSENSITIVE);
  private static final Pattern MI_NEGOCIO_PATTERN = Pattern.compile("Mi Negocio", Pattern.CASE_INSENSITIVE);
  private static final Pattern AGREGAR_NEGOCIO_PATTERN =
      Pattern.compile("Agregar Negocio", Pattern.CASE_INSENSITIVE);
  private static final Pattern ADMINISTRAR_NEGOCIOS_PATTERN =
      Pattern.compile("Administrar Negocios", Pattern.CASE_INSENSITIVE);
  private static final Pattern CREAR_NUEVO_NEGOCIO_PATTERN =
      Pattern.compile("Crear Nuevo Negocio", Pattern.CASE_INSENSITIVE);
  private static final Pattern NOMBRE_NEGOCIO_PATTERN =
      Pattern.compile("Nombre del Negocio", Pattern.CASE_INSENSITIVE);
  private static final Pattern LIMITE_NEGOCIOS_PATTERN =
      Pattern.compile("Tienes\\s*2\\s*de\\s*3\\s*negocios", Pattern.CASE_INSENSITIVE);
  private static final Pattern INFORMACION_GENERAL_PATTERN =
      Pattern.compile("Informaci\\u00f3n General", Pattern.CASE_INSENSITIVE);
  private static final Pattern DETALLES_CUENTA_PATTERN =
      Pattern.compile("Detalles de la Cuenta", Pattern.CASE_INSENSITIVE);
  private static final Pattern TUS_NEGOCIOS_PATTERN =
      Pattern.compile("Tus Negocios", Pattern.CASE_INSENSITIVE);
  private static final Pattern SECCION_LEGAL_PATTERN =
      Pattern.compile("Secci\\u00f3n Legal", Pattern.CASE_INSENSITIVE);
  private static final Pattern TERMINOS_PATTERN =
      Pattern.compile("T\\u00e9rminos y Condiciones", Pattern.CASE_INSENSITIVE);
  private static final Pattern POLITICA_PATTERN =
      Pattern.compile("Pol\\u00edtica de Privacidad", Pattern.CASE_INSENSITIVE);
  private static final Pattern BUSINESS_PLAN_PATTERN =
      Pattern.compile("BUSINESS PLAN", Pattern.CASE_INSENSITIVE);
  private static final Pattern CAMBIAR_PLAN_PATTERN =
      Pattern.compile("Cambiar Plan", Pattern.CASE_INSENSITIVE);
  private static final Pattern CUENTA_CREADA_PATTERN =
      Pattern.compile("Cuenta creada", Pattern.CASE_INSENSITIVE);
  private static final Pattern ESTADO_ACTIVO_PATTERN =
      Pattern.compile("Estado activo", Pattern.CASE_INSENSITIVE);
  private static final Pattern IDIOMA_SELECCIONADO_PATTERN =
      Pattern.compile("Idioma seleccionado", Pattern.CASE_INSENSITIVE);
  private static final Pattern LEGAL_CONTENT_PATTERN =
      Pattern.compile("(uso|datos|informaci\\u00f3n|usuarios|servicio|servicios|condiciones)",
          Pattern.CASE_INSENSITIVE);
  private static final String TEST_BUSINESS_NAME = "Negocio Prueba Automatizacion";

  @Test
  public void saleadsMiNegocioFullWorkflow() throws IOException {
    final Map<String, String> report = new LinkedHashMap<>();
    final Map<String, String> finalUrls = new LinkedHashMap<>();
    final Path evidenceDir = createEvidenceDirectory();

    try (Playwright playwright = Playwright.create()) {
      Browser browser = playwright.chromium().launch(
          new BrowserType.LaunchOptions().setHeadless(resolveHeadless()));
      BrowserContext context = browser.newContext(
          new Browser.NewContextOptions().setViewportSize(1600, 1200));
      Page page = context.newPage();
      page.setDefaultTimeout(20000);

      page.navigate(resolveLoginUrl());
      waitForUi(page);

      executeStep(report, "Login", () -> {
        loginWithGoogle(page);
        expectVisible(
            "Main interface must appear after login.",
            page.getByRole(AriaRole.NAVIGATION),
            page.locator("aside"),
            page.getByText(NEGOCIO_PATTERN));
        expectVisible(
            "Left sidebar navigation must be visible.",
            page.getByText(NEGOCIO_PATTERN),
            page.getByText(MI_NEGOCIO_PATTERN));
        captureScreenshot(page, evidenceDir, "01-dashboard-loaded.png", true);
      });

      executeStep(report, "Mi Negocio menu", () -> {
        openMiNegocioMenu(page);
        expectVisible("'Agregar Negocio' should be visible in expanded submenu.",
            page.getByText(AGREGAR_NEGOCIO_PATTERN));
        expectVisible("'Administrar Negocios' should be visible in expanded submenu.",
            page.getByText(ADMINISTRAR_NEGOCIOS_PATTERN));
        captureScreenshot(page, evidenceDir, "02-mi-negocio-menu-expanded.png", false);
      });

      executeStep(report, "Agregar Negocio modal", () -> {
        clickByVisibleText(page, AGREGAR_NEGOCIO_PATTERN);
        waitForUi(page);
        expectVisible("Modal title should be visible.", page.getByText(CREAR_NUEVO_NEGOCIO_PATTERN));
        expectVisible("'Nombre del Negocio' field must be available.",
            page.getByLabel(NOMBRE_NEGOCIO_PATTERN),
            page.getByPlaceholder(NOMBRE_NEGOCIO_PATTERN),
            page.getByText(NOMBRE_NEGOCIO_PATTERN));
        expectVisible("'Tienes 2 de 3 negocios' text should appear.",
            page.getByText(LIMITE_NEGOCIOS_PATTERN));
        expectVisible("'Cancelar' and 'Crear Negocio' buttons should exist.",
            page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Pattern.compile("Cancelar", Pattern.CASE_INSENSITIVE))));
        expectVisible("'Crear Negocio' button should exist.",
            page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Pattern.compile("Crear Negocio", Pattern.CASE_INSENSITIVE))));
        captureScreenshot(page, evidenceDir, "03-agregar-negocio-modal.png", false);

        fillBusinessNameIfPossible(page, TEST_BUSINESS_NAME);
        clickByVisibleText(page, Pattern.compile("Cancelar", Pattern.CASE_INSENSITIVE));
        waitForUi(page);
      });

      executeStep(report, "Administrar Negocios view", () -> {
        openMiNegocioMenu(page);
        clickByVisibleText(page, ADMINISTRAR_NEGOCIOS_PATTERN);
        waitForUi(page);
        expectVisible("'Informacion General' section should exist.",
            page.getByText(INFORMACION_GENERAL_PATTERN));
        expectVisible("'Detalles de la Cuenta' section should exist.",
            page.getByText(DETALLES_CUENTA_PATTERN));
        expectVisible("'Tus Negocios' section should exist.",
            page.getByText(TUS_NEGOCIOS_PATTERN));
        expectVisible("'Seccion Legal' section should exist.",
            page.getByText(SECCION_LEGAL_PATTERN));
        captureScreenshot(page, evidenceDir, "04-administrar-negocios-view.png", true);
      });

      executeStep(report, "Informaci\u00f3n General", () -> {
        expectVisible("User name should be visible.", page.locator("[class*='user'], [id*='user'], h1, h2"));
        expectVisible("User email should be visible.",
            page.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/"));
        expectVisible("'BUSINESS PLAN' text should be visible.", page.getByText(BUSINESS_PLAN_PATTERN));
        expectVisible("'Cambiar Plan' button should be visible.",
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CAMBIAR_PLAN_PATTERN)),
            page.getByText(CAMBIAR_PLAN_PATTERN));
      });

      executeStep(report, "Detalles de la Cuenta", () -> {
        expectVisible("'Cuenta creada' should be visible.", page.getByText(CUENTA_CREADA_PATTERN));
        expectVisible("'Estado activo' should be visible.", page.getByText(ESTADO_ACTIVO_PATTERN));
        expectVisible("'Idioma seleccionado' should be visible.",
            page.getByText(IDIOMA_SELECCIONADO_PATTERN));
      });

      executeStep(report, "Tus Negocios", () -> {
        expectVisible("Business list should be visible.",
            page.getByText(TUS_NEGOCIOS_PATTERN),
            page.locator("table"),
            page.locator("[role='list']"));
        expectVisible("'Agregar Negocio' button should exist.",
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)),
            page.getByText(AGREGAR_NEGOCIO_PATTERN));
        expectVisible("'Tienes 2 de 3 negocios' text should be visible.",
            page.getByText(LIMITE_NEGOCIOS_PATTERN));
      });

      executeStep(report, "T\u00e9rminos y Condiciones", () -> {
        String finalUrl = validateLegalDocument(page, TERMINOS_PATTERN, TERMINOS_PATTERN,
            evidenceDir, "05-terminos-y-condiciones.png");
        finalUrls.put("T\u00e9rminos y Condiciones", finalUrl);
      });

      executeStep(report, "Pol\u00edtica de Privacidad", () -> {
        String finalUrl = validateLegalDocument(page, POLITICA_PATTERN, POLITICA_PATTERN,
            evidenceDir, "06-politica-de-privacidad.png");
        finalUrls.put("Pol\u00edtica de Privacidad", finalUrl);
      });

      printFinalReport(report, finalUrls, evidenceDir);
      assertAllStepsPassed(report);
    }
  }

  private static void loginWithGoogle(Page page) {
    int pageCountBeforeClick = page.context().pages().size();
    clickByVisibleText(page, GOOGLE_LOGIN_PATTERN);
    waitForUi(page);

    Page popup = getNewPageIfOpened(page, pageCountBeforeClick);
    if (popup != null) {
      popup.waitForLoadState(LoadState.DOMCONTENTLOADED);
      waitForUi(popup);
      Locator accountOption = popup.getByText(Pattern.compile(Pattern.quote(GOOGLE_ACCOUNT_EMAIL), Pattern.CASE_INSENSITIVE));
      if (isVisible(accountOption)) {
        accountOption.first().click();
        waitForUi(popup);
      }
      if (!popup.isClosed()) {
        popup.waitForTimeout(2000);
      }
      page.bringToFront();
      waitForUi(page);
      return;
    }

    Locator sameTabAccountOption = page.getByText(
        Pattern.compile(Pattern.quote(GOOGLE_ACCOUNT_EMAIL), Pattern.CASE_INSENSITIVE));
    if (isVisible(sameTabAccountOption)) {
      sameTabAccountOption.first().click();
      waitForUi(page);
    }
  }

  private static void openMiNegocioMenu(Page page) {
    if (!isVisible(page.getByText(MI_NEGOCIO_PATTERN))) {
      clickByVisibleText(page, NEGOCIO_PATTERN);
      waitForUi(page);
    }
    clickByVisibleText(page, MI_NEGOCIO_PATTERN);
    waitForUi(page);
  }

  private static void fillBusinessNameIfPossible(Page page, String businessName) {
    List<Locator> fieldCandidates = List.of(
        page.getByLabel(NOMBRE_NEGOCIO_PATTERN),
        page.getByPlaceholder(NOMBRE_NEGOCIO_PATTERN),
        page.locator("input[name*='negocio'], input[id*='negocio']")
    );
    for (Locator candidate : fieldCandidates) {
      if (isVisible(candidate)) {
        candidate.first().fill(businessName);
        return;
      }
    }
  }

  private static String validateLegalDocument(
      Page mainPage,
      Pattern linkText,
      Pattern headingText,
      Path evidenceDir,
      String screenshotName) {
    int pagesBefore = mainPage.context().pages().size();
    clickByVisibleText(mainPage, linkText);
    waitForUi(mainPage);

    Page legalPage = getNewPageIfOpened(mainPage, pagesBefore);
    if (legalPage == null) {
      legalPage = mainPage;
    } else {
      legalPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
      waitForUi(legalPage);
    }

    expectVisible("Legal page heading should be visible.", legalPage.getByText(headingText));
    expectVisible("Legal content text should be visible.",
        legalPage.getByText(LEGAL_CONTENT_PATTERN),
        legalPage.locator("main p"),
        legalPage.locator("article p"),
        legalPage.locator("p"));
    captureScreenshot(legalPage, evidenceDir, screenshotName, true);
    String finalUrl = legalPage.url();

    if (legalPage != mainPage) {
      legalPage.close();
      mainPage.bringToFront();
      waitForUi(mainPage);
    } else {
      mainPage.goBack();
      waitForUi(mainPage);
    }

    return finalUrl;
  }

  private static void clickByVisibleText(Page page, Pattern textPattern) {
    List<Locator> candidates = List.of(
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(textPattern)),
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(textPattern)),
        page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(textPattern)),
        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(textPattern)),
        page.getByText(textPattern)
    );

    for (Locator candidate : candidates) {
      if (isVisible(candidate)) {
        candidate.first().click();
        return;
      }
    }
    throw new AssertionError("No visible element found with text pattern: " + textPattern.pattern());
  }

  private static Page getNewPageIfOpened(Page currentPage, int previousCount) {
    for (int i = 0; i < 20; i++) {
      List<Page> pages = currentPage.context().pages();
      if (pages.size() > previousCount) {
        return pages.get(pages.size() - 1);
      }
      currentPage.waitForTimeout(250);
    }
    return null;
  }

  private static void expectVisible(String message, Locator... locators) {
    PlaywrightException lastException = null;
    for (Locator locator : locators) {
      try {
        locator.first().waitFor(
            new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
        return;
      } catch (PlaywrightException e) {
        lastException = e;
      }
    }
    throw new AssertionError(message, lastException);
  }

  private static boolean isVisible(Locator locator) {
    try {
      return locator.count() > 0 && locator.first().isVisible();
    } catch (PlaywrightException e) {
      return false;
    }
  }

  private static void captureScreenshot(Page page, Path evidenceDir, String filename, boolean fullPage) {
    page.screenshot(new Page.ScreenshotOptions()
        .setPath(evidenceDir.resolve(filename))
        .setFullPage(fullPage));
  }

  private static void waitForUi(Page page) {
    try {
      page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    } catch (PlaywrightException ignored) {
      // Pages that are already stable can throw on strict waits; a short fallback pause is enough.
    }
    page.waitForTimeout(800);
  }

  private static String resolveLoginUrl() {
    String envUrl = System.getenv("SALEADS_LOGIN_URL");
    if (envUrl == null || envUrl.trim().isEmpty()) {
      throw new IllegalStateException(
          "Set SALEADS_LOGIN_URL to the login page URL for the current SaleADS environment.");
    }
    return envUrl.trim();
  }

  private static boolean resolveHeadless() {
    String headless = System.getenv("SALEADS_HEADLESS");
    if (headless == null || headless.trim().isEmpty()) {
      return true;
    }
    return Boolean.parseBoolean(headless);
  }

  private static Path createEvidenceDirectory() throws IOException {
    String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
    Path evidenceDir = Paths.get("target", "saleads-evidence", timestamp);
    Files.createDirectories(evidenceDir);
    return evidenceDir;
  }

  private static void executeStep(Map<String, String> report, String stepName, Runnable stepAction) {
    try {
      stepAction.run();
      report.put(stepName, "PASS");
    } catch (Throwable throwable) {
      report.put(stepName, "FAIL: " + normalizeMessage(throwable.getMessage()));
    }
  }

  private static String normalizeMessage(String message) {
    if (message == null || message.trim().isEmpty()) {
      return "No error message was provided.";
    }
    return message.replace('\n', ' ').trim();
  }

  private static void printFinalReport(
      Map<String, String> report,
      Map<String, String> finalUrls,
      Path evidenceDir) {
    System.out.println("=== SaleADS Mi Negocio Workflow Final Report ===");
    report.forEach((step, status) -> System.out.printf("- %s: %s%n", step, status));
    finalUrls.forEach((docName, url) -> System.out.printf("- %s URL: %s%n", docName, url));
    System.out.printf("- Screenshots directory: %s%n", evidenceDir.toAbsolutePath());
  }

  private static void assertAllStepsPassed(Map<String, String> report) {
    boolean allPassed = report.values().stream().allMatch(value -> value.startsWith("PASS"));
    assertTrue("One or more SaleADS Mi Negocio workflow checks failed. See report output.", allPassed);
  }
}
