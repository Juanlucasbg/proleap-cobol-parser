package io.proleap.cobol.e2e.saleads;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.Assume;
import org.junit.Test;

public class SaleadsMiNegocioWorkflowTest {

  private static final String GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
  private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

  private final Map<String, Boolean> stepResults = new LinkedHashMap<>();
  private final List<String> stepFailures = new ArrayList<>();
  private Path evidenceDir;

  @Test
  public void saleadsMiNegocioWorkflow() throws IOException {
    final String loginUrl = resolveLoginUrl();
    Assume.assumeTrue(
        "Set SALEADS_LOGIN_URL env var or -Dsaleads.login.url to run this E2E test.",
        loginUrl != null && !loginUrl.isBlank());

    evidenceDir = Paths.get("target", "saleads-evidence", LocalDateTime.now().format(TS_FORMAT));
    Files.createDirectories(evidenceDir);

    final boolean headless =
        Boolean.parseBoolean(System.getProperty("saleads.headless", System.getenv().getOrDefault("SALEADS_HEADLESS", "true")));

    try (Playwright playwright = Playwright.create()) {
      final Browser browser =
          playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
      final BrowserContext context =
          browser.newContext(new Browser.NewContextOptions().setViewportSize(1920, 1080));
      final Page page = context.newPage();
      page.setDefaultTimeout(30_000);

      page.navigate(loginUrl);
      waitForUiToLoad(page);

      runStep("Login", () -> stepLoginWithGoogle(context, page));
      runStep("Mi Negocio menu", () -> stepOpenMiNegocioMenu(page));
      runStep("Agregar Negocio modal", () -> stepValidateAgregarNegocioModal(page));
      runStep("Administrar Negocios view", () -> stepOpenAdministrarNegocios(page));
      runStep("Informacion General", () -> stepValidateInformacionGeneral(page));
      runStep("Detalles de la Cuenta", () -> stepValidateDetallesCuenta(page));
      runStep("Tus Negocios", () -> stepValidateTusNegocios(page));
      runStep("Terminos y Condiciones", () -> stepValidateLegalLink(context, page, "Terminos y Condiciones"));
      runStep("Politica de Privacidad", () -> stepValidateLegalLink(context, page, "Politica de Privacidad"));

      printFinalReport();
      assertTrue("One or more workflow validations failed.\n" + String.join("\n", stepFailures), stepFailures.isEmpty());
    }
  }

  private void stepLoginWithGoogle(final BrowserContext context, final Page page) {
    final Locator signInButton =
        firstVisible(
            page,
            page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(Pattern.compile("(?i)(sign in|iniciar sesion|google)"))),
            page.getByText(Pattern.compile("(?i)(sign in with google|iniciar sesion con google|google)")),
            page.locator("button:has-text('Google')"),
            page.locator("[data-testid*='google']"));

    final Page popup = clickAndCapturePopup(context, signInButton);
    final Page authPage = popup == null ? page : popup;
    waitForUiToLoad(authPage);

    trySelectGoogleAccount(authPage);

    if (popup != null) {
      popup.waitForClose(new Page.WaitForCloseOptions().setTimeout(20_000));
    }

    waitForUiToLoad(page);
    assertVisibleAny(
        page,
        "main application interface",
        page.locator("main"),
        page.getByText(Pattern.compile("(?i)(dashboard|panel|inicio|negocio)")));
    assertVisibleAny(
        page,
        "left sidebar navigation",
        page.locator("aside"),
        page.getByText(Pattern.compile("(?i)negocio")));

    takeScreenshot(page, "01-dashboard-loaded", false);
  }

  private void stepOpenMiNegocioMenu(final Page page) {
    clickByVisibleText(page, "(?i)mi\\s*negocio");
    assertVisibleAny(
        page,
        "submenu item 'Agregar Negocio'",
        page.getByText(Pattern.compile("(?i)agregar\\s*negocio")));
    assertVisibleAny(
        page,
        "submenu item 'Administrar Negocios'",
        page.getByText(Pattern.compile("(?i)administrar\\s*negocios")));

    takeScreenshot(page, "02-mi-negocio-menu-expanded", false);
  }

  private void stepValidateAgregarNegocioModal(final Page page) {
    clickByVisibleText(page, "(?i)^agregar\\s*negocio$");

    assertVisibleAny(
        page,
        "modal title 'Crear Nuevo Negocio'",
        page.getByText(Pattern.compile("(?i)crear\\s*nuevo\\s*negocio")));
    assertVisibleAny(
        page,
        "input field 'Nombre del Negocio'",
        page.getByLabel(Pattern.compile("(?i)nombre\\s*del\\s*negocio")),
        page.getByPlaceholder(Pattern.compile("(?i)nombre\\s*del\\s*negocio")),
        page.getByText(Pattern.compile("(?i)nombre\\s*del\\s*negocio")));
    assertVisibleAny(
        page,
        "text 'Tienes 2 de 3 negocios'",
        page.getByText(Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios")));
    assertVisibleAny(page, "button 'Cancelar'", page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")));
    assertVisibleAny(
        page,
        "button 'Crear Negocio'",
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)crear\\s*negocio"))));

    takeScreenshot(page, "03-agregar-negocio-modal", false);

    final Locator input =
        firstVisible(
            page,
            page.getByLabel(Pattern.compile("(?i)nombre\\s*del\\s*negocio")),
            page.getByPlaceholder(Pattern.compile("(?i)nombre\\s*del\\s*negocio")),
            page.locator("input").first());
    input.click();
    input.fill("Negocio Prueba Automatizacion");
    clickByVisibleText(page, "(?i)^cancelar$");
  }

  private void stepOpenAdministrarNegocios(final Page page) {
    ensureMiNegocioExpanded(page);
    clickByVisibleText(page, "(?i)administrar\\s*negocios");

    assertVisibleAny(
        page,
        "section 'Informacion General'",
        page.getByText(Pattern.compile("(?i)informaci[oó]n\\s*general")));
    assertVisibleAny(
        page,
        "section 'Detalles de la Cuenta'",
        page.getByText(Pattern.compile("(?i)detalles\\s*de\\s*la\\s*cuenta")));
    assertVisibleAny(
        page,
        "section 'Tus Negocios'",
        page.getByText(Pattern.compile("(?i)tus\\s*negocios")));
    assertVisibleAny(
        page,
        "section 'Seccion Legal'",
        page.getByText(Pattern.compile("(?i)secci[oó]n\\s*legal")));

    takeScreenshot(page, "04-administrar-negocios-page", true);
  }

  private void stepValidateInformacionGeneral(final Page page) {
    final String bodyText = page.locator("body").innerText();
    assertTrue("Expected user email to be visible.", EMAIL_PATTERN.matcher(bodyText).find());
    assertVisibleAny(
        page,
        "text 'BUSINESS PLAN'",
        page.getByText(Pattern.compile("(?i)business\\s*plan")));
    assertVisibleAny(
        page,
        "button 'Cambiar Plan'",
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cambiar\\s*plan"))),
        page.getByText(Pattern.compile("(?i)cambiar\\s*plan")));

    // Validate there is at least one likely profile name-like token.
    assertTrue(
        "Expected user name-like text to be visible.",
        Pattern.compile("(?im)^[A-ZA-Za-z][A-Za-z\\s]{2,}$").matcher(bodyText).find());
  }

  private void stepValidateDetallesCuenta(final Page page) {
    assertVisibleAny(
        page,
        "label 'Cuenta creada'",
        page.getByText(Pattern.compile("(?i)cuenta\\s*creada")));
    assertVisibleAny(
        page,
        "label 'Estado activo'",
        page.getByText(Pattern.compile("(?i)estado\\s*activo")));
    assertVisibleAny(
        page,
        "label 'Idioma seleccionado'",
        page.getByText(Pattern.compile("(?i)idioma\\s*seleccionado")));
  }

  private void stepValidateTusNegocios(final Page page) {
    assertVisibleAny(page, "section 'Tus Negocios'", page.getByText(Pattern.compile("(?i)tus\\s*negocios")));
    assertVisibleAny(
        page,
        "button 'Agregar Negocio'",
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)agregar\\s*negocio"))),
        page.getByText(Pattern.compile("(?i)agregar\\s*negocio")));
    assertVisibleAny(
        page,
        "text 'Tienes 2 de 3 negocios'",
        page.getByText(Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios")));

    final Locator listLikeContent = page.locator("li, [role='listitem'], tr, [data-testid*='business']");
    assertTrue("Expected business list content to be visible.", listLikeContent.count() > 0 || page.locator("body").innerText().contains("Negocio"));
  }

  private void stepValidateLegalLink(final BrowserContext context, final Page appPage, final String label) {
    final String labelRegex = label.replace(" ", "\\s*").replace("Terminos", "T[eé]rminos").replace("Politica", "Pol[ií]tica");
    final Page legalPage = clickTextAndCapturePopup(context, appPage, "(?i)" + labelRegex);
    final Page targetPage = legalPage == null ? appPage : legalPage;
    waitForUiToLoad(targetPage);

    assertVisibleAny(targetPage, "legal heading '" + label + "'", targetPage.getByText(Pattern.compile("(?i)" + labelRegex)));

    final String legalText = targetPage.locator("body").innerText();
    assertTrue("Expected legal content text to be visible for " + label + ".", legalText != null && legalText.trim().length() >= 120);

    final String filePrefix = label.toLowerCase().replace(' ', '-');
    takeScreenshot(targetPage, "0" + (label.startsWith("Term") ? "8" : "9") + "-" + filePrefix, true);
    System.out.println("[EVIDENCE] " + label + " final URL: " + targetPage.url());

    if (legalPage != null) {
      legalPage.close();
      appPage.bringToFront();
    } else {
      safeGoBack(appPage);
    }

    waitForUiToLoad(appPage);
  }

  private void trySelectGoogleAccount(final Page authPage) {
    final Locator account =
        firstVisibleOrNull(
            authPage,
            authPage.getByText(Pattern.compile("(?i)" + Pattern.quote(GOOGLE_EMAIL))),
            authPage.locator("div[data-identifier='" + GOOGLE_EMAIL + "']"),
            authPage.locator("text=" + GOOGLE_EMAIL));
    if (account != null) {
      clickAndWaitForUi(authPage, account);
    }
  }

  private void ensureMiNegocioExpanded(final Page page) {
    final Locator adminItem = page.getByText(Pattern.compile("(?i)administrar\\s*negocios"));
    if (adminItem.count() > 0 && adminItem.first().isVisible()) {
      return;
    }
    clickByVisibleText(page, "(?i)mi\\s*negocio");
  }

  private void clickByVisibleText(final Page page, final String patternRegex) {
    final Locator target = firstVisible(page, page.getByText(Pattern.compile(patternRegex)));
    clickAndWaitForUi(page, target);
  }

  private Locator firstVisible(final Page page, final Locator... candidates) {
    final Locator located = firstVisibleOrNull(page, candidates);
    if (located == null) {
      throw new AssertionError("No visible locator found among candidates.");
    }
    return located;
  }

  private Locator firstVisibleOrNull(final Page page, final Locator... candidates) {
    for (final Locator candidate : candidates) {
      if (candidate == null || candidate.count() == 0) {
        continue;
      }
      try {
        final Locator first = candidate.first();
        first.waitFor(
            new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(7_000));
        return first;
      } catch (PlaywrightException ignored) {
        // Try next visible candidate.
      }
    }
    return null;
  }

  private void assertVisibleAny(final Page page, final String description, final Locator... candidates) {
    final Locator visible = firstVisibleOrNull(page, candidates);
    if (visible == null) {
      throw new AssertionError("Expected visible element not found: " + description);
    }
  }

  private void clickAndWaitForUi(final Page page, final Locator locator) {
    locator.scrollIntoViewIfNeeded();
    locator.click();
    waitForUiToLoad(page);
  }

  private Page clickAndCapturePopup(final BrowserContext context, final Locator locator) {
    try {
      return context.waitForPage(
          () -> clickAndWaitForUi(locator.page(), locator),
          new BrowserContext.WaitForPageOptions().setTimeout(8_000));
    } catch (PlaywrightException ignored) {
      return null;
    }
  }

  private Page clickTextAndCapturePopup(
      final BrowserContext context, final Page page, final String visibleTextRegex) {
    final Locator clickable = firstVisible(page, page.getByText(Pattern.compile(visibleTextRegex)));
    return clickAndCapturePopup(context, clickable);
  }

  private void waitForUiToLoad(final Page page) {
    try {
      page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(10_000));
    } catch (PlaywrightException ignored) {
      // Some SPA transitions never emit DOMContentLoaded again.
    }
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5_000));
    } catch (PlaywrightException ignored) {
      // NETWORKIDLE may not be reached with persistent websockets.
    }
    page.waitForTimeout(700);
  }

  private void safeGoBack(final Page page) {
    try {
      page.goBack(new Page.GoBackOptions().setTimeout(8_000));
    } catch (PlaywrightException ignored) {
      // Link might not have changed tab/history; continue on current page.
    }
  }

  private void takeScreenshot(final Page page, final String checkpoint, final boolean fullPage) {
    final String timestamp = LocalDateTime.now().format(TS_FORMAT);
    final Path destination = evidenceDir.resolve(checkpoint + "-" + timestamp + ".png");
    page.screenshot(new Page.ScreenshotOptions().setPath(destination).setFullPage(fullPage));
    System.out.println("[EVIDENCE] Screenshot: " + destination);
  }

  private String resolveLoginUrl() {
    final String[] candidates = {
      System.getProperty("saleads.login.url"),
      System.getenv("SALEADS_LOGIN_URL")
    };
    return Arrays.stream(candidates).filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
  }

  private void runStep(final String stepName, final StepExecution action) {
    try {
      action.run();
      stepResults.put(stepName, Boolean.TRUE);
    } catch (Throwable throwable) {
      stepResults.put(stepName, Boolean.FALSE);
      stepFailures.add(stepName + ": " + throwable.getMessage());
      System.err.println("[STEP FAIL] " + stepName + " -> " + throwable.getMessage());
    }
  }

  private void printFinalReport() {
    System.out.println("=== SaleADS Mi Negocio Workflow Final Report ===");
    for (final String key :
        Arrays.asList(
            "Login",
            "Mi Negocio menu",
            "Agregar Negocio modal",
            "Administrar Negocios view",
            "Informacion General",
            "Detalles de la Cuenta",
            "Tus Negocios",
            "Terminos y Condiciones",
            "Politica de Privacidad")) {
      final boolean passed = Boolean.TRUE.equals(stepResults.get(key));
      System.out.println(String.format("%s: %s", key, passed ? "PASS" : "FAIL"));
    }
    System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
    System.out.println("===============================================");
  }

  @FunctionalInterface
  private interface StepExecution {
    void run() throws Exception;
  }
}
