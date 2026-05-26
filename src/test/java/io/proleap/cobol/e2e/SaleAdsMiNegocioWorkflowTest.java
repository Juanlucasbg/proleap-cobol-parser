package io.proleap.cobol.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SaleAdsMiNegocioWorkflowTest {

  private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
  private static final long SHORT_TIMEOUT_MS = 5_000;
  private static final long MEDIUM_TIMEOUT_MS = 12_000;
  private static final long LONG_TIMEOUT_MS = 30_000;
  private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
  private static final DateTimeFormatter RUN_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

  private Path evidenceDirectory;

  @Test
  public void saleadsMiNegocioFullTest() throws IOException {
    final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
    Assume.assumeTrue(
        "Set SALEADS_LOGIN_URL to the login page of the current SaleADS environment.",
        loginUrl != null && !loginUrl.isBlank());

    final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));
    final LinkedHashMap<String, Boolean> report = initializeReport();
    final List<String> notes = new ArrayList<>();
    final String[] termsUrl = new String[1];
    final String[] privacyUrl = new String[1];

    evidenceDirectory = Paths.get("target", "saleads-mi-negocio-evidence",
        LocalDateTime.now().format(RUN_ID_FORMATTER));
    Files.createDirectories(evidenceDirectory);

    try (Playwright playwright = Playwright.create();
         Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
         BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000))) {

      Page appPage = context.newPage();
      appPage.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
      waitForUi(appPage);

      report.put("Login", runStep("Login", notes, () -> stepLoginWithGoogle(context, appPage)));
      report.put("Mi Negocio menu", runStep("Mi Negocio menu", notes, () -> stepOpenMiNegocioMenu(appPage)));
      report.put("Agregar Negocio modal",
          runStep("Agregar Negocio modal", notes, () -> stepValidateAgregarNegocioModal(appPage)));
      report.put("Administrar Negocios view",
          runStep("Administrar Negocios view", notes, () -> stepOpenAdministrarNegocios(appPage)));
      report.put("Información General",
          runStep("Información General", notes, () -> stepValidateInformacionGeneral(appPage)));
      report.put("Detalles de la Cuenta",
          runStep("Detalles de la Cuenta", notes, () -> stepValidateDetallesCuenta(appPage)));
      report.put("Tus Negocios", runStep("Tus Negocios", notes, () -> stepValidateTusNegocios(appPage)));
      report.put("Términos y Condiciones",
          runStep("Términos y Condiciones", notes, () -> termsUrl[0] = stepValidateLegalLink(
              context, appPage, "Términos y Condiciones", "Términos y Condiciones", "08-terminos-y-condiciones.png")));
      report.put("Política de Privacidad",
          runStep("Política de Privacidad", notes, () -> privacyUrl[0] = stepValidateLegalLink(
              context, appPage, "Política de Privacidad", "Política de Privacidad", "09-politica-de-privacidad.png")));
    }

    if (termsUrl[0] != null && !termsUrl[0].isBlank()) {
      notes.add("Final URL (Términos y Condiciones): " + termsUrl[0]);
    }
    if (privacyUrl[0] != null && !privacyUrl[0].isBlank()) {
      notes.add("Final URL (Política de Privacidad): " + privacyUrl[0]);
    }

    String finalReport = formatFinalReport(report, notes);
    Files.writeString(evidenceDirectory.resolve("final-report.txt"), finalReport);
    System.out.println(finalReport);

    Assert.assertTrue("At least one SaleADS Mi Negocio validation failed.\n" + finalReport,
        report.values().stream().allMatch(Boolean::booleanValue));
  }

  private void stepLoginWithGoogle(BrowserContext context, Page appPage) throws IOException {
    List<String> loginTexts = Arrays.asList(
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Continuar con Google",
        "Google");

    Page popup = clickAndCaptureNewTab(appPage, context, loginTexts);
    Page activeLoginPage = popup != null ? popup : appPage;
    waitForUi(activeLoginPage);

    maybeSelectGoogleAccount(activeLoginPage);

    if (popup != null) {
      waitForUi(appPage);
      appPage.bringToFront();
    }

    // Main interface and left sidebar confirmation after login.
    assertAnyTextVisible(appPage, LONG_TIMEOUT_MS, "Negocio", "Mi Negocio", "Dashboard");
    boolean sidebarVisible = isVisible(appPage.locator("aside").first(), MEDIUM_TIMEOUT_MS)
        || isVisible(appPage.locator("nav").first(), MEDIUM_TIMEOUT_MS);
    Assert.assertTrue("Expected the left sidebar navigation to be visible.", sidebarVisible);

    takeScreenshot(appPage, "01-dashboard-loaded.png", false);
  }

  private void stepOpenMiNegocioMenu(Page appPage) throws IOException {
    clickByVisibleText(appPage, Arrays.asList("Mi Negocio", "Negocio"));
    assertTextVisible(appPage, "Agregar Negocio", MEDIUM_TIMEOUT_MS);
    assertTextVisible(appPage, "Administrar Negocios", MEDIUM_TIMEOUT_MS);
    takeScreenshot(appPage, "02-mi-negocio-menu-expanded.png", false);
  }

  private void stepValidateAgregarNegocioModal(Page appPage) throws IOException {
    clickByVisibleText(appPage, Arrays.asList("Agregar Negocio"));
    assertTextVisible(appPage, "Crear Nuevo Negocio", MEDIUM_TIMEOUT_MS);
    assertTextVisible(appPage, "Tienes 2 de 3 negocios", MEDIUM_TIMEOUT_MS);
    assertTextVisible(appPage, "Cancelar", MEDIUM_TIMEOUT_MS);
    assertTextVisible(appPage, "Crear Negocio", MEDIUM_TIMEOUT_MS);

    boolean nameFieldVisible = isVisible(appPage.getByLabel("Nombre del Negocio").first(), SHORT_TIMEOUT_MS)
        || isVisible(appPage.getByPlaceholder("Nombre del Negocio").first(), SHORT_TIMEOUT_MS)
        || isVisible(appPage.getByText("Nombre del Negocio").first(), SHORT_TIMEOUT_MS);
    Assert.assertTrue("Expected the 'Nombre del Negocio' input field to be visible.", nameFieldVisible);

    takeScreenshot(appPage, "03-agregar-negocio-modal.png", false);

    Locator nameInput = firstVisible(
        appPage.getByLabel("Nombre del Negocio").first(),
        appPage.getByPlaceholder("Nombre del Negocio").first(),
        appPage.locator("input[type='text']").first());
    if (nameInput != null) {
      nameInput.fill("Negocio Prueba Automatización");
      waitForUi(appPage);
    }
    clickByVisibleText(appPage, Arrays.asList("Cancelar"));
  }

  private void stepOpenAdministrarNegocios(Page appPage) throws IOException {
    if (!isVisible(appPage.getByText("Administrar Negocios").first(), SHORT_TIMEOUT_MS)) {
      clickByVisibleText(appPage, Arrays.asList("Mi Negocio", "Negocio"));
    }

    clickByVisibleText(appPage, Arrays.asList("Administrar Negocios"));
    assertTextVisible(appPage, "Información General", LONG_TIMEOUT_MS);
    assertTextVisible(appPage, "Detalles de la Cuenta", LONG_TIMEOUT_MS);
    assertTextVisible(appPage, "Tus Negocios", LONG_TIMEOUT_MS);
    assertTextVisible(appPage, "Sección Legal", LONG_TIMEOUT_MS);
    takeScreenshot(appPage, "04-administrar-negocios.png", true);
  }

  private void stepValidateInformacionGeneral(Page appPage) {
    String bodyText = normalizeText(appPage.textContent("body"));
    Matcher emailMatcher = EMAIL_PATTERN.matcher(bodyText);
    Assert.assertTrue("Expected a visible user email in 'Información General'.", emailMatcher.find());

    String expectedNameToken = System.getenv().getOrDefault("SALEADS_EXPECTED_NAME_TOKEN", "juan")
        .toLowerCase(Locale.ROOT);
    Assert.assertTrue("Expected a visible user name token in 'Información General'.",
        bodyText.toLowerCase(Locale.ROOT).contains(expectedNameToken));

    assertTextVisible(appPage, "BUSINESS PLAN", MEDIUM_TIMEOUT_MS);
    assertTextVisible(appPage, "Cambiar Plan", MEDIUM_TIMEOUT_MS);
  }

  private void stepValidateDetallesCuenta(Page appPage) {
    assertTextVisible(appPage, "Cuenta creada", MEDIUM_TIMEOUT_MS);
    assertTextVisible(appPage, "Estado activo", MEDIUM_TIMEOUT_MS);
    assertTextVisible(appPage, "Idioma seleccionado", MEDIUM_TIMEOUT_MS);
  }

  private void stepValidateTusNegocios(Page appPage) {
    assertTextVisible(appPage, "Tus Negocios", MEDIUM_TIMEOUT_MS);
    assertTextVisible(appPage, "Agregar Negocio", MEDIUM_TIMEOUT_MS);
    assertTextVisible(appPage, "Tienes 2 de 3 negocios", MEDIUM_TIMEOUT_MS);
  }

  private String stepValidateLegalLink(BrowserContext context, Page appPage, String linkText, String headingText,
      String screenshotName) throws IOException {
    Page destinationPage = clickAndCaptureNewTab(appPage, context, Arrays.asList(linkText));
    if (destinationPage == null) {
      destinationPage = appPage;
      waitForUi(destinationPage);
    } else {
      waitForUi(destinationPage);
    }

    assertTextVisible(destinationPage, headingText, LONG_TIMEOUT_MS);
    String bodyText = normalizeText(destinationPage.textContent("body"));
    Assert.assertTrue("Expected legal content text for '" + headingText + "'.", bodyText.length() > 200);
    takeScreenshot(destinationPage, screenshotName, true);
    String finalUrl = destinationPage.url();

    if (destinationPage != appPage) {
      destinationPage.close();
      appPage.bringToFront();
      waitForUi(appPage);
    } else {
      try {
        appPage.goBack(new Page.GoBackOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
      } catch (PlaywrightException ignored) {
        // Some legal views can be in-page overlays; if no history entry exists we stay on current view.
      }
      waitForUi(appPage);
    }

    return finalUrl;
  }

  private Page clickAndCaptureNewTab(Page sourcePage, BrowserContext context, List<String> textCandidates) {
    int initialPages = context.pages().size();
    clickByVisibleText(sourcePage, textCandidates);

    long deadline = System.currentTimeMillis() + MEDIUM_TIMEOUT_MS;
    while (System.currentTimeMillis() < deadline) {
      List<Page> pages = context.pages();
      if (pages.size() > initialPages) {
        return pages.get(pages.size() - 1);
      }
      sourcePage.waitForTimeout(200);
    }
    return null;
  }

  private void maybeSelectGoogleAccount(Page page) {
    Locator account = page.getByText(GOOGLE_ACCOUNT).first();
    if (isVisible(account, MEDIUM_TIMEOUT_MS)) {
      account.click();
      waitForUi(page);
    }
  }

  private void clickByVisibleText(Page page, List<String> textCandidates) {
    Locator locator = findVisibleText(page, textCandidates, LONG_TIMEOUT_MS);
    Assert.assertNotNull("Could not find clickable text among: " + textCandidates, locator);
    locator.click();
    waitForUi(page);
  }

  private Locator findVisibleText(Page page, List<String> textCandidates, long timeoutMs) {
    long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      for (String textCandidate : textCandidates) {
        Locator byText = page.getByText(textCandidate).first();
        if (isVisible(byText, 350)) {
          return byText;
        }
      }
      page.waitForTimeout(200);
    }
    return null;
  }

  private Locator firstVisible(Locator... locators) {
    for (Locator locator : locators) {
      if (isVisible(locator, 1_000)) {
        return locator;
      }
    }
    return null;
  }

  private void assertAnyTextVisible(Page page, long timeoutMs, String... textCandidates) {
    Locator found = findVisibleText(page, Arrays.asList(textCandidates), timeoutMs);
    Assert.assertNotNull("None of the expected texts were visible: " + Arrays.toString(textCandidates), found);
  }

  private void assertTextVisible(Page page, String text, long timeoutMs) {
    Locator locator = page.getByText(text).first();
    boolean visible = isVisible(locator, timeoutMs);
    Assert.assertTrue("Expected visible text: " + text, visible);
  }

  private boolean isVisible(Locator locator, long timeoutMs) {
    try {
      locator.waitFor(new Locator.WaitForOptions()
          .setState(WaitForSelectorState.VISIBLE)
          .setTimeout((double) timeoutMs));
      return true;
    } catch (PlaywrightException e) {
      return false;
    }
  }

  private void waitForUi(Page page) {
    try {
      page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(15_000));
    } catch (PlaywrightException ignored) {
      // Some UI actions do not trigger navigation; this wait is best effort.
    }
    page.waitForTimeout(600);
  }

  private void takeScreenshot(Page page, String fileName, boolean fullPage) throws IOException {
    Files.createDirectories(evidenceDirectory);
    page.screenshot(new Page.ScreenshotOptions()
        .setPath(evidenceDirectory.resolve(fileName))
        .setFullPage(fullPage));
  }

  private boolean runStep(String stepName, List<String> notes, StepAction action) {
    try {
      action.run();
      notes.add(stepName + ": PASS");
      return true;
    } catch (Throwable throwable) {
      String message = throwable.getMessage();
      if (message == null || message.isBlank()) {
        message = throwable.getClass().getSimpleName();
      }
      notes.add(stepName + ": FAIL - " + message);
      return false;
    }
  }

  private LinkedHashMap<String, Boolean> initializeReport() {
    LinkedHashMap<String, Boolean> report = new LinkedHashMap<>();
    report.put("Login", false);
    report.put("Mi Negocio menu", false);
    report.put("Agregar Negocio modal", false);
    report.put("Administrar Negocios view", false);
    report.put("Información General", false);
    report.put("Detalles de la Cuenta", false);
    report.put("Tus Negocios", false);
    report.put("Términos y Condiciones", false);
    report.put("Política de Privacidad", false);
    return report;
  }

  private String formatFinalReport(LinkedHashMap<String, Boolean> report, List<String> notes) {
    StringBuilder builder = new StringBuilder();
    builder.append("SaleADS Mi Negocio - Final Report\n");
    builder.append("Evidence directory: ").append(evidenceDirectory).append("\n\n");
    for (Map.Entry<String, Boolean> entry : report.entrySet()) {
      builder.append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL").append("\n");
    }
    if (!notes.isEmpty()) {
      builder.append("\nExecution notes:\n");
      for (String note : notes) {
        builder.append("- ").append(note).append("\n");
      }
    }
    return builder.toString();
  }

  private String normalizeText(String text) {
    if (text == null) {
      return "";
    }
    return text.replaceAll("\\s+", " ").trim();
  }

  @FunctionalInterface
  private interface StepAction {
    void run() throws Exception;
  }
}
