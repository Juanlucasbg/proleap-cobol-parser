package io.proleap.cobol.e2e.saleads;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;

/**
 * Manual E2E workflow for SaleADS "Mi Negocio".
 *
 * This test is environment-agnostic:
 * - It never hardcodes a SaleADS domain.
 * - It requires SALEADS_LOGIN_URL to be provided by the runtime environment.
 * - It captures screenshots and a final PASS/FAIL report per requested section.
 */
public class SaleadsMiNegocioWorkflowTestManual {

  private static final double STEP_TIMEOUT_MS = 15_000;
  private static final double SHORT_TIMEOUT_MS = 2_500;
  private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

  @Test
  public void saleadsMiNegocioFullTest() throws IOException {
    final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
    Assume.assumeTrue("Set SALEADS_LOGIN_URL to run this workflow.", hasText(loginUrl));

    final Path artifactDirectory = createArtifactDirectory();
    final Map<String, String> stepStatus = new LinkedHashMap<>();
    final Map<String, String> legalUrls = new LinkedHashMap<>();

    try (Playwright playwright = Playwright.create()) {
      BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions().setHeadless(resolveHeadlessMode());
      try (Browser browser = playwright.chromium().launch(launchOptions)) {
        BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
        Page appPage = context.newPage();
        appPage.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        waitForUiLoad(appPage);

        runStep(stepStatus, "Login", () -> {
          stepLoginWithGoogle(context, appPage);
          takeScreenshot(appPage, artifactDirectory.resolve("01-dashboard-loaded.png"), true);
        });

        runStep(stepStatus, "Mi Negocio menu", () -> {
          stepOpenMiNegocioMenu(appPage);
          takeScreenshot(appPage, artifactDirectory.resolve("02-mi-negocio-menu-expanded.png"), false);
        });

        runStep(stepStatus, "Agregar Negocio modal", () -> {
          stepValidateAgregarNegocioModal(appPage);
          takeScreenshot(appPage, artifactDirectory.resolve("03-agregar-negocio-modal.png"), false);
        });

        runStep(stepStatus, "Administrar Negocios view", () -> {
          stepOpenAdministrarNegocios(appPage);
          takeScreenshot(appPage, artifactDirectory.resolve("04-administrar-negocios-full.png"), true);
        });

        runStep(stepStatus, "Información General", () -> stepValidateInformacionGeneral(appPage));
        runStep(stepStatus, "Detalles de la Cuenta", () -> stepValidateDetallesCuenta(appPage));
        runStep(stepStatus, "Tus Negocios", () -> stepValidateTusNegocios(appPage));

        runStep(stepStatus, "Términos y Condiciones", () -> {
          String finalUrl = stepOpenLegalDocument(context, appPage,
              new String[] { "Términos y Condiciones", "Terminos y Condiciones" },
              new String[] { "Términos y Condiciones", "Terminos y Condiciones" },
              artifactDirectory.resolve("08-terminos-y-condiciones.png"));
          legalUrls.put("Términos y Condiciones", finalUrl);
        });

        runStep(stepStatus, "Política de Privacidad", () -> {
          String finalUrl = stepOpenLegalDocument(context, appPage,
              new String[] { "Política de Privacidad", "Politica de Privacidad" },
              new String[] { "Política de Privacidad", "Politica de Privacidad" },
              artifactDirectory.resolve("09-politica-de-privacidad.png"));
          legalUrls.put("Política de Privacidad", finalUrl);
        });

        Path reportPath = artifactDirectory.resolve("final-report.txt");
        writeFinalReport(reportPath, stepStatus, legalUrls);

        List<String> failedSteps = stepStatus.entrySet().stream().filter(entry -> entry.getValue().startsWith("FAIL"))
            .map(Map.Entry::getKey).collect(Collectors.toList());
        Assert.assertTrue("Some workflow validations failed: " + failedSteps + ". Check report: " + reportPath,
            failedSteps.isEmpty());
      }
    }
  }

  private void stepLoginWithGoogle(BrowserContext context, Page appPage) {
    Locator loginWithGoogle = findVisibleClickableByText(appPage, "Sign in with Google", "Iniciar sesión con Google",
        "Iniciar sesion con Google", "Continuar con Google", "Google");

    int initialPageCount = context.pages().size();
    clickAndWait(appPage, loginWithGoogle);

    Page googlePage = resolveNewTab(context, initialPageCount, appPage);
    if (googlePage != appPage) {
      waitForUiLoad(googlePage);
      selectGoogleAccountIfVisible(googlePage, GOOGLE_ACCOUNT_EMAIL);
    } else {
      selectGoogleAccountIfVisible(appPage, GOOGLE_ACCOUNT_EMAIL);
    }

    appPage.bringToFront();
    waitForUiLoad(appPage);

    assertAnyVisible(appPage, "Main application interface should be visible after login.",
        appPage.locator("main").first(),
        appPage.locator("[role='main']").first(),
        appPage.locator("section").first());

    assertAnyVisible(appPage, "Left sidebar navigation should be visible after login.",
        appPage.locator("aside").first(),
        appPage.locator("nav").first(),
        appPage.getByText(Pattern.compile("Negocio", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)).first());
  }

  private void stepOpenMiNegocioMenu(Page appPage) {
    assertAnyVisible(appPage, "Sidebar should be visible before opening Mi Negocio.",
        appPage.locator("aside").first(), appPage.locator("nav").first());

    Locator negocioSection = findVisibleClickableByText(appPage, "Negocio");
    clickAndWait(appPage, negocioSection);

    Locator miNegocio = findVisibleClickableByText(appPage, "Mi Negocio");
    clickAndWait(appPage, miNegocio);

    assertVisibleByText(appPage, "Agregar Negocio", "Agregar Negocio should be visible in expanded menu.");
    assertVisibleByText(appPage, "Administrar Negocios", "Administrar Negocios should be visible in expanded menu.");
  }

  private void stepValidateAgregarNegocioModal(Page appPage) {
    Locator agregarNegocio = findVisibleClickableByText(appPage, "Agregar Negocio");
    clickAndWait(appPage, agregarNegocio);

    assertVisibleByText(appPage, "Crear Nuevo Negocio", "Modal title 'Crear Nuevo Negocio' should be visible.");
    assertAnyVisible(appPage, "Input 'Nombre del Negocio' should be visible.",
        appPage.getByLabel(Pattern.compile("Nombre del Negocio", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
            .first(),
        appPage
            .getByPlaceholder(Pattern.compile("Nombre del Negocio", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
            .first(),
        appPage.locator("input").first());

    assertVisibleByText(appPage, "Tienes 2 de 3 negocios", "Business limit text should be visible in modal.");
    assertVisibleByText(appPage, "Cancelar", "Cancelar button should be visible in modal.");
    assertVisibleByText(appPage, "Crear Negocio", "Crear Negocio button should be visible in modal.");

    Locator negocioNameField = findFirstVisibleLocator(appPage,
        appPage.getByLabel(Pattern.compile("Nombre del Negocio", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
            .first(),
        appPage
            .getByPlaceholder(Pattern.compile("Nombre del Negocio", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
            .first(),
        appPage.locator("input").first());
    negocioNameField.click();
    negocioNameField.fill("Negocio Prueba Automatización");
    waitForUiLoad(appPage);

    Locator cancelar = findVisibleClickableByText(appPage, "Cancelar");
    clickAndWait(appPage, cancelar);
  }

  private void stepOpenAdministrarNegocios(Page appPage) {
    Locator miNegocio = findVisibleClickableByText(appPage, "Mi Negocio");
    clickAndWait(appPage, miNegocio);

    Locator administrar = findVisibleClickableByText(appPage, "Administrar Negocios");
    clickAndWait(appPage, administrar);

    assertVisibleByText(appPage, "Información General", "Section 'Información General' should be visible.");
    assertVisibleByText(appPage, "Detalles de la Cuenta", "Section 'Detalles de la Cuenta' should be visible.");
    assertVisibleByText(appPage, "Tus Negocios", "Section 'Tus Negocios' should be visible.");
    assertVisibleByText(appPage, "Sección Legal", "Section 'Sección Legal' should be visible.");
  }

  private void stepValidateInformacionGeneral(Page appPage) {
    assertAnyVisible(appPage, "User name should be visible in Información General.",
        appPage.locator("[data-testid*=name i]").first(),
        appPage.locator("h1,h2,h3").first(),
        appPage.locator("section").first());
    assertAnyVisible(appPage, "User email should be visible in Información General.",
        appPage.getByText(Pattern.compile("@")).first(),
        appPage.locator("[data-testid*=email i]").first());
    assertVisibleByText(appPage, "BUSINESS PLAN", "Text 'BUSINESS PLAN' should be visible.");
    assertVisibleByText(appPage, "Cambiar Plan", "Button 'Cambiar Plan' should be visible.");
  }

  private void stepValidateDetallesCuenta(Page appPage) {
    assertVisibleByText(appPage, "Cuenta creada", "'Cuenta creada' should be visible.");
    assertVisibleByText(appPage, "Estado activo", "'Estado activo' should be visible.");
    assertVisibleByText(appPage, "Idioma seleccionado", "'Idioma seleccionado' should be visible.");
  }

  private void stepValidateTusNegocios(Page appPage) {
    assertAnyVisible(appPage, "Business list should be visible in 'Tus Negocios'.",
        appPage.locator("table").first(),
        appPage.locator("[role='list']").first(),
        appPage.locator("section").first());
    assertVisibleByText(appPage, "Agregar Negocio", "Button 'Agregar Negocio' should exist in 'Tus Negocios'.");
    assertVisibleByText(appPage, "Tienes 2 de 3 negocios", "Text 'Tienes 2 de 3 negocios' should be visible.");
  }

  private String stepOpenLegalDocument(BrowserContext context, Page appPage, String[] linkTextCandidates,
      String[] headingCandidates, Path screenshotPath) {
    Locator legalLink = findVisibleClickableByText(appPage, linkTextCandidates);
    String appUrlBeforeClick = appPage.url();
    int initialPageCount = context.pages().size();

    clickAndWait(appPage, legalLink);

    Page legalPage = resolveNewTab(context, initialPageCount, appPage);
    waitForUiLoad(legalPage);

    assertVisibleByText(legalPage, headingCandidates, "Legal document heading should be visible.");
    assertAnyVisible(legalPage, "Legal content text should be visible.",
        legalPage.locator("article").first(),
        legalPage.locator("main").first(),
        legalPage.locator("p").first());

    takeScreenshot(legalPage, screenshotPath, true);
    String finalUrl = legalPage.url();

    if (legalPage != appPage) {
      legalPage.close();
      appPage.bringToFront();
      waitForUiLoad(appPage);
    } else if (!Objects.equals(appPage.url(), appUrlBeforeClick)) {
      appPage.goBack(new Page.GoBackOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
      waitForUiLoad(appPage);
    }

    return finalUrl;
  }

  private void selectGoogleAccountIfVisible(Page page, String email) {
    Locator accountLocator = page.getByText(Pattern.compile(Pattern.quote(email), Pattern.CASE_INSENSITIVE));
    if (waitUntilVisible(accountLocator.first(), SHORT_TIMEOUT_MS)) {
      clickAndWait(page, accountLocator.first());
    }
  }

  private void clickAndWait(Page page, Locator locator) {
    locator.click();
    waitForUiLoad(page);
  }

  private void waitForUiLoad(Page page) {
    try {
      page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    } catch (TimeoutError ignored) {
      // Keep going if page already loaded.
    }
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(7_000));
    } catch (TimeoutError ignored) {
      // SPAs can keep network busy; DOM loaded is enough for the next step.
    }
    page.waitForTimeout(350);
  }

  private Locator findVisibleClickableByText(Page page, String... textCandidates) {
    for (String text : textCandidates) {
      Pattern exactNamePattern = Pattern
          .compile("^\\s*" + Pattern.quote(text) + "\\s*$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
      Pattern containsPattern = Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

      List<Locator> locatorCandidates = new ArrayList<>();
      locatorCandidates.add(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(exactNamePattern)));
      locatorCandidates.add(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(exactNamePattern)));
      locatorCandidates.add(page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(exactNamePattern)));
      locatorCandidates.add(page.getByText(text, new Page.GetByTextOptions().setExact(true)));
      locatorCandidates.add(page.getByText(containsPattern));

      for (Locator locator : locatorCandidates) {
        Locator first = locator.first();
        if (waitUntilVisible(first, SHORT_TIMEOUT_MS)) {
          return first;
        }
      }
    }
    throw new AssertionError("Unable to find clickable element by visible text candidates: "
        + String.join(", ", textCandidates));
  }

  private Locator findFirstVisibleLocator(Page page, Locator... locatorCandidates) {
    for (Locator locator : locatorCandidates) {
      if (waitUntilVisible(locator, SHORT_TIMEOUT_MS)) {
        return locator;
      }
    }
    throw new AssertionError("None of the expected locators is visible.");
  }

  private void assertVisibleByText(Page page, String expectedText, String message) {
    assertVisibleByText(page, new String[] { expectedText }, message);
  }

  private void assertVisibleByText(Page page, String[] textCandidates, String message) {
    for (String text : textCandidates) {
      Pattern exactNamePattern = Pattern
          .compile("^\\s*" + Pattern.quote(text) + "\\s*$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
      Pattern containsPattern = Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

      List<Locator> locatorCandidates = new ArrayList<>();
      locatorCandidates.add(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(exactNamePattern)));
      locatorCandidates.add(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(exactNamePattern)));
      locatorCandidates.add(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(exactNamePattern)));
      locatorCandidates.add(page.getByText(text, new Page.GetByTextOptions().setExact(true)));
      locatorCandidates.add(page.getByText(containsPattern));

      for (Locator locator : locatorCandidates) {
        if (waitUntilVisible(locator.first(), SHORT_TIMEOUT_MS)) {
          return;
        }
      }
    }

    Assert.fail(message + " Text candidates: " + String.join(", ", textCandidates));
  }

  private void assertAnyVisible(Page page, String message, Locator... locators) {
    for (Locator locator : locators) {
      if (waitUntilVisible(locator, SHORT_TIMEOUT_MS)) {
        return;
      }
    }
    Assert.fail(message);
  }

  private boolean waitUntilVisible(Locator locator, double timeoutMs) {
    try {
      locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
      return true;
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  private Page resolveNewTab(BrowserContext context, int previousPageCount, Page fallbackPage) {
    int attempts = 0;
    while (attempts < 10) {
      if (context.pages().size() > previousPageCount) {
        return context.pages().get(context.pages().size() - 1);
      }
      fallbackPage.waitForTimeout(300);
      attempts++;
    }
    return fallbackPage;
  }

  private void takeScreenshot(Page page, Path screenshotPath, boolean fullPage) throws IOException {
    Files.createDirectories(screenshotPath.getParent());
    page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
  }

  private Path createArtifactDirectory() throws IOException {
    String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
    Path artifactDirectory = Paths.get("target", "saleads-e2e-artifacts", timestamp);
    Files.createDirectories(artifactDirectory);
    return artifactDirectory;
  }

  private boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private boolean resolveHeadlessMode() {
    String headlessEnv = System.getenv("SALEADS_HEADLESS");
    if (!hasText(headlessEnv)) {
      return true;
    }
    return Boolean.parseBoolean(headlessEnv.trim());
  }

  private void runStep(Map<String, String> stepStatus, String stepName, StepAction action) {
    try {
      action.execute();
      stepStatus.put(stepName, "PASS");
    } catch (Throwable throwable) {
      stepStatus.put(stepName, "FAIL - " + throwable.getMessage());
    }
  }

  private void writeFinalReport(Path reportPath, Map<String, String> stepStatus, Map<String, String> legalUrls)
      throws IOException {
    StringBuilder reportBuilder = new StringBuilder();
    reportBuilder.append("SaleADS Mi Negocio Workflow - Final Report").append(System.lineSeparator());
    reportBuilder.append("Generated at: ").append(LocalDateTime.now()).append(System.lineSeparator());
    reportBuilder.append(System.lineSeparator());
    for (Map.Entry<String, String> entry : stepStatus.entrySet()) {
      reportBuilder.append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
    }
    reportBuilder.append(System.lineSeparator());
    for (Map.Entry<String, String> entry : legalUrls.entrySet()) {
      reportBuilder.append("URL ").append(entry.getKey()).append(": ").append(entry.getValue())
          .append(System.lineSeparator());
    }

    Files.writeString(reportPath, reportBuilder.toString(), StandardCharsets.UTF_8);
    System.out.println(reportBuilder);
    System.out.println("Artifacts written to: " + reportPath.getParent().toAbsolutePath());
  }

  @FunctionalInterface
  private interface StepAction {
    void execute() throws Exception;
  }
}
