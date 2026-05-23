package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * End-to-end validation for the SaleADS.ai "Mi Negocio" workflow.
 *
 * <p>This test is environment-agnostic by requiring {@code SALEADS_LOGIN_URL} at runtime
 * rather than hardcoding a domain.
 */
public class SaleadsMiNegocioWorkflowTest {

  private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
  private static final Path EVIDENCE_DIR = Paths.get("target", "saleads-evidence");
  private static final Path REPORT_FILE = EVIDENCE_DIR.resolve("saleads-mi-negocio-report.txt");

  private static final String LOGIN = "Login";
  private static final String MENU = "Mi Negocio menu";
  private static final String MODAL = "Agregar Negocio modal";
  private static final String ADMIN_VIEW = "Administrar Negocios view";
  private static final String INFO_GENERAL = "Información General";
  private static final String ACCOUNT_DETAILS = "Detalles de la Cuenta";
  private static final String BUSINESSES = "Tus Negocios";
  private static final String TERMS = "Términos y Condiciones";
  private static final String PRIVACY = "Política de Privacidad";

  private final Map<String, Boolean> statusByStep = new LinkedHashMap<>();
  private final Map<String, String> legalUrls = new LinkedHashMap<>();

  private Playwright playwright;
  private Browser browser;
  private BrowserContext context;
  private Page page;

  @Before
  public void setUp() throws IOException {
    Files.createDirectories(EVIDENCE_DIR);
    Files.deleteIfExists(REPORT_FILE);

    playwright = Playwright.create();
    final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("HEADLESS", "true"));
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
    context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 960));
    context.setDefaultTimeout(15_000);

    page = context.newPage();
    final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
    if (loginUrl == null || loginUrl.isBlank()) {
      throw new IllegalStateException("SALEADS_LOGIN_URL is required and must point to the login page.");
    }

    page.navigate(loginUrl);
    waitForUiToLoad(page);
  }

  @After
  public void tearDown() {
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
  public void saleadsMiNegocioFullTest() throws IOException {
    initializeReportOrder();

    validateLoginWithGoogle();
    validateMiNegocioMenu();
    validateAgregarNegocioModal();
    validateAdministrarNegociosView();
    validateInformacionGeneral();
    validateDetallesCuenta();
    validateTusNegocios();
    validateTerminosYCondiciones();
    validatePoliticaDePrivacidad();

    writeFinalReport();
    assertTrue("One or more validations failed. See " + REPORT_FILE, collectFailedSteps().isEmpty());
  }

  private void validateLoginWithGoogle() {
    boolean loginButtonFound = clickFirstVisibleText(page,
        List.of("Sign in with Google", "Iniciar sesión con Google", "Inicia sesión con Google",
            "Continuar con Google", "Google"));
    Page popup = null;
    if (loginButtonFound) {
      popup = detectNewPageAfterClick();
    }

    handleGoogleAccountSelection(popup);
    waitForUiToLoad(page);

    final boolean appVisible = isAnyTextVisible(page, List.of("Dashboard", "Panel", "Inicio", "Negocio", "Mi Negocio"));
    final boolean sidebarVisible = isSidebarVisible();
    updateStatus(LOGIN, loginButtonFound && appVisible && sidebarVisible);
    screenshot("01-dashboard-loaded.png", false, page);
  }

  private void validateMiNegocioMenu() {
    clickFirstVisibleText(page, List.of("Negocio"));
    clickFirstVisibleText(page, List.of("Mi Negocio"));

    final boolean submenuExpanded = isAnyTextVisible(page, List.of("Agregar Negocio", "Administrar Negocios"));
    final boolean addVisible = isTextVisible(page, "Agregar Negocio");
    final boolean manageVisible = isTextVisible(page, "Administrar Negocios");
    updateStatus(MENU, submenuExpanded && addVisible && manageVisible);
    screenshot("02-mi-negocio-expanded-menu.png", false, page);
  }

  private void validateAgregarNegocioModal() {
    clickFirstVisibleText(page, List.of("Agregar Negocio"));
    waitForUiToLoad(page);

    final boolean titleVisible = isTextVisible(page, "Crear Nuevo Negocio");
    final boolean inputVisible = isInputVisible();
    final boolean limitsVisible = isTextVisible(page, "Tienes 2 de 3 negocios");
    final boolean cancelVisible = isTextVisible(page, "Cancelar");
    final boolean createVisible = isTextVisible(page, "Crear Negocio");
    updateStatus(MODAL, titleVisible && inputVisible && limitsVisible && cancelVisible && createVisible);
    screenshot("03-agregar-negocio-modal.png", false, page);

    fillBusinessNameIfPresent("Negocio Prueba Automatizacion");
    clickFirstVisibleText(page, List.of("Cancelar"));
    waitForUiToLoad(page);
  }

  private void validateAdministrarNegociosView() {
    if (!isTextVisible(page, "Administrar Negocios")) {
      clickFirstVisibleText(page, List.of("Mi Negocio"));
    }
    clickFirstVisibleText(page, List.of("Administrar Negocios"));
    waitForUiToLoad(page);

    final boolean informacionGeneral = isTextVisible(page, "Información General");
    final boolean detallesCuenta = isTextVisible(page, "Detalles de la Cuenta");
    final boolean tusNegocios = isTextVisible(page, "Tus Negocios");
    final boolean seccionLegal = isAnyTextVisible(page, List.of("Sección Legal", "Seccion Legal"));
    updateStatus(ADMIN_VIEW, informacionGeneral && detallesCuenta && tusNegocios && seccionLegal);
    screenshot("04-administrar-negocios-full-page.png", true, page);
  }

  private void validateInformacionGeneral() {
    final boolean userNameVisible = isAnyTextVisible(page, List.of("Juan", "juan"));
    final boolean emailVisible = isTextVisible(page, ACCOUNT_EMAIL)
        || isRegexVisible(page, Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"));
    final boolean planVisible = isTextVisible(page, "BUSINESS PLAN");
    final boolean changePlanVisible = isTextVisible(page, "Cambiar Plan");
    updateStatus(INFO_GENERAL, userNameVisible && emailVisible && planVisible && changePlanVisible);
  }

  private void validateDetallesCuenta() {
    final boolean createdVisible = isTextVisible(page, "Cuenta creada");
    final boolean activeVisible = isAnyTextVisible(page, List.of("Estado activo", "Estado Activo"));
    final boolean languageVisible = isAnyTextVisible(page, List.of("Idioma seleccionado", "Idioma Seleccionado"));
    updateStatus(ACCOUNT_DETAILS, createdVisible && activeVisible && languageVisible);
  }

  private void validateTusNegocios() {
    final boolean listVisible = isTextVisible(page, "Tus Negocios");
    final boolean addButtonVisible = isTextVisible(page, "Agregar Negocio");
    final boolean limitsVisible = isTextVisible(page, "Tienes 2 de 3 negocios");
    updateStatus(BUSINESSES, listVisible && addButtonVisible && limitsVisible);
  }

  private void validateTerminosYCondiciones() {
    final Page legalPage = openLegalPage("Términos y Condiciones", "Terminos y Condiciones");
    final boolean headingVisible = isAnyTextVisible(legalPage, List.of("Términos y Condiciones", "Terminos y Condiciones"));
    final boolean legalContentVisible = hasSufficientLegalText(legalPage);
    legalUrls.put("terminos_url", legalPage.url());
    screenshot("05-terminos-y-condiciones.png", true, legalPage);
    updateStatus(TERMS, headingVisible && legalContentVisible);
    returnFromLegalPage(legalPage);
  }

  private void validatePoliticaDePrivacidad() {
    final Page legalPage = openLegalPage("Política de Privacidad", "Politica de Privacidad");
    final boolean headingVisible = isAnyTextVisible(legalPage, List.of("Política de Privacidad", "Politica de Privacidad"));
    final boolean legalContentVisible = hasSufficientLegalText(legalPage);
    legalUrls.put("privacidad_url", legalPage.url());
    screenshot("06-politica-de-privacidad.png", true, legalPage);
    updateStatus(PRIVACY, headingVisible && legalContentVisible);
    returnFromLegalPage(legalPage);
  }

  private Page openLegalPage(final String... candidateTexts) {
    final int pagesBeforeClick = context.pages().size();
    final boolean clicked = clickFirstVisibleText(page, List.of(candidateTexts));
    if (!clicked) {
      return page;
    }

    waitForUiToLoad(page);
    page.waitForTimeout(2_000);
    final List<Page> openPages = context.pages();
    if (openPages.size() > pagesBeforeClick) {
      final Page newTab = openPages.get(openPages.size() - 1);
      waitForUiToLoad(newTab);
      return newTab;
    }
    return page;
  }

  private void returnFromLegalPage(final Page legalPage) {
    if (legalPage != page) {
      legalPage.close();
      page.bringToFront();
      waitForUiToLoad(page);
      return;
    }

    try {
      page.goBack(new Page.GoBackOptions().setTimeout(10_000));
      waitForUiToLoad(page);
    } catch (PlaywrightException ignored) {
      // Keep the test resilient when same-tab navigation does not create browser history.
    }
  }

  private void handleGoogleAccountSelection(final Page popup) {
    if (popup != null) {
      waitForUiToLoad(popup);
      clickFirstVisibleText(popup, List.of(ACCOUNT_EMAIL));
      waitForUiToLoad(popup);
      try {
        popup.waitForClose(new Page.WaitForCloseOptions().setTimeout(8_000), () -> {
        });
      } catch (PlaywrightException ignored) {
        // Some environments redirect back without closing immediately.
      }
      return;
    }

    clickFirstVisibleText(page, List.of(ACCOUNT_EMAIL));
    waitForUiToLoad(page);
  }

  private Page detectNewPageAfterClick() {
    page.waitForTimeout(2_500);
    final List<Page> pages = context.pages();
    if (pages.size() > 1) {
      return pages.get(pages.size() - 1);
    }
    return null;
  }

  private boolean clickFirstVisibleText(final Page targetPage, final List<String> texts) {
    for (final String text : texts) {
      final Locator candidate = targetPage.getByText(text, new Page.GetByTextOptions().setExact(true)).first();
      try {
        candidate.waitFor(new Locator.WaitForOptions()
            .setState(WaitForSelectorState.VISIBLE)
            .setTimeout(2_000));
        candidate.click();
        waitForUiToLoad(targetPage);
        return true;
      } catch (PlaywrightException ignored) {
        // Keep trying candidate texts.
      }
    }
    return false;
  }

  private boolean isTextVisible(final Page targetPage, final String text) {
    final Locator locator = targetPage.getByText(text, new Page.GetByTextOptions().setExact(true)).first();
    try {
      locator.waitFor(new Locator.WaitForOptions()
          .setState(WaitForSelectorState.VISIBLE)
          .setTimeout(4_000));
      return true;
    } catch (PlaywrightException ignored) {
      return false;
    }
  }

  private boolean isAnyTextVisible(final Page targetPage, final List<String> texts) {
    for (final String text : texts) {
      if (isTextVisible(targetPage, text)) {
        return true;
      }
    }
    return false;
  }

  private boolean isRegexVisible(final Page targetPage, final Pattern pattern) {
    final Locator locator = targetPage.getByText(pattern).first();
    try {
      locator.waitFor(new Locator.WaitForOptions()
          .setState(WaitForSelectorState.VISIBLE)
          .setTimeout(2_000));
      return true;
    } catch (PlaywrightException ignored) {
      return false;
    }
  }

  private boolean isSidebarVisible() {
    final Locator sidebar = page.locator("aside, nav").first();
    try {
      sidebar.waitFor(new Locator.WaitForOptions()
          .setState(WaitForSelectorState.VISIBLE)
          .setTimeout(5_000));
      return true;
    } catch (PlaywrightException ignored) {
      return false;
    }
  }

  private boolean isInputVisible() {
    final Locator byLabel = page.getByLabel("Nombre del Negocio", new Page.GetByLabelOptions().setExact(false)).first();
    final Locator byPlaceholder = page.locator("input[placeholder*='Nombre del Negocio' i]").first();
    final Locator byName = page.locator("input[name*='negocio' i], input[id*='negocio' i]").first();
    return isLocatorVisible(byLabel) || isLocatorVisible(byPlaceholder) || isLocatorVisible(byName);
  }

  private void fillBusinessNameIfPresent(final String businessName) {
    final Locator byLabel = page.getByLabel("Nombre del Negocio", new Page.GetByLabelOptions().setExact(false)).first();
    final Locator byPlaceholder = page.locator("input[placeholder*='Nombre del Negocio' i]").first();

    if (isLocatorVisible(byLabel)) {
      byLabel.fill(businessName);
      return;
    }
    if (isLocatorVisible(byPlaceholder)) {
      byPlaceholder.fill(businessName);
    }
  }

  private boolean isLocatorVisible(final Locator locator) {
    try {
      locator.waitFor(new Locator.WaitForOptions()
          .setState(WaitForSelectorState.VISIBLE)
          .setTimeout(1_500));
      return true;
    } catch (PlaywrightException ignored) {
      return false;
    }
  }

  private boolean hasSufficientLegalText(final Page legalPage) {
    final String bodyText = legalPage.locator("body").innerText().trim();
    return bodyText.length() > 120;
  }

  private void waitForUiToLoad(final Page targetPage) {
    try {
      targetPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
      targetPage.waitForLoadState(LoadState.NETWORKIDLE);
    } catch (PlaywrightException ignored) {
      // Keep flows resilient if NETWORKIDLE is not reached in SPAs.
    }
    targetPage.waitForTimeout(500);
  }

  private void screenshot(final String fileName, final boolean fullPage, final Page targetPage) {
    targetPage.screenshot(new Page.ScreenshotOptions()
        .setPath(EVIDENCE_DIR.resolve(fileName))
        .setFullPage(fullPage));
  }

  private void initializeReportOrder() {
    statusByStep.put(LOGIN, false);
    statusByStep.put(MENU, false);
    statusByStep.put(MODAL, false);
    statusByStep.put(ADMIN_VIEW, false);
    statusByStep.put(INFO_GENERAL, false);
    statusByStep.put(ACCOUNT_DETAILS, false);
    statusByStep.put(BUSINESSES, false);
    statusByStep.put(TERMS, false);
    statusByStep.put(PRIVACY, false);
  }

  private void updateStatus(final String step, final boolean value) {
    statusByStep.put(step, value);
  }

  private List<String> collectFailedSteps() {
    final List<String> failed = new ArrayList<>();
    for (Map.Entry<String, Boolean> entry : statusByStep.entrySet()) {
      if (!entry.getValue()) {
        failed.add(entry.getKey());
      }
    }
    return failed;
  }

  private void writeFinalReport() throws IOException {
    final List<String> lines = new ArrayList<>();
    lines.add("saleads_mi_negocio_full_test");
    lines.add("final_result=" + (collectFailedSteps().isEmpty() ? "PASS" : "FAIL"));
    lines.add("");

    for (Map.Entry<String, Boolean> entry : statusByStep.entrySet()) {
      lines.add(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
    }

    lines.add("");
    lines.add("terminos_final_url=" + legalUrls.getOrDefault("terminos_url", "N/A"));
    lines.add("privacidad_final_url=" + legalUrls.getOrDefault("privacidad_url", "N/A"));
    lines.add("executed_with_locale=" + Locale.getDefault());

    Files.write(REPORT_FILE, lines, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
  }
}
