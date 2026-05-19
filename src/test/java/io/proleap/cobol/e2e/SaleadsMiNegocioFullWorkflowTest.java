package io.proleap.cobol.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * End-to-end workflow for SaleADS "Mi Negocio".
 *
 * <p>This test is intentionally opt-in so existing CI remains unchanged.
 * Enable it with: SALEADS_E2E_ENABLED=true and SALEADS_URL set to the login URL of the
 * target SaleADS environment (dev/staging/prod).</p>
 */
public class SaleadsMiNegocioFullWorkflowTest {

  private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
  private static final DateTimeFormatter ARTIFACT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
  private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}");

  private final LinkedHashMap<String, Boolean> stepReport = new LinkedHashMap<>();
  private final LinkedHashMap<String, String> legalUrls = new LinkedHashMap<>();
  private final LinkedHashMap<String, String> failures = new LinkedHashMap<>();
  private Path evidenceDir;

  @Test
  public void saleadsMiNegocioFullTest() throws Exception {
    Assume.assumeTrue("Set SALEADS_E2E_ENABLED=true to run SaleADS E2E UI flow.", isEnabled());

    final String saleadsUrl = firstNonBlank(
        System.getenv("SALEADS_URL"),
        System.getProperty("saleads.url")
    );
    Assert.assertNotNull(
        "SALEADS_URL or -Dsaleads.url must point to the login page of the current environment.",
        saleadsUrl
    );

    evidenceDir = Files.createDirectories(Paths.get(
        "target",
        "saleads-evidence",
        LocalDateTime.now().format(ARTIFACT_TIME_FORMAT)
    ));

    try (Playwright playwright = Playwright.create()) {
      final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
          .setHeadless(isHeadless())
      );
      final BrowserContext context = browser.newContext(new Browser.NewContextOptions()
          .setViewportSize(1440, 960)
      );
      final Page appPage = context.newPage();
      appPage.setDefaultTimeout(20_000);

      appPage.navigate(saleadsUrl);
      waitForUiLoad(appPage);

      runStep("Login", () -> stepLogin(appPage));
      runStep("Mi Negocio menu", () -> stepOpenMiNegocioMenu(appPage));
      runStep("Agregar Negocio modal", () -> stepAgregarNegocioModal(appPage));
      runStep("Administrar Negocios view", () -> stepAdministrarNegocios(appPage));
      runStep("Información General", () -> stepInformacionGeneral(appPage));
      runStep("Detalles de la Cuenta", () -> stepDetallesCuenta(appPage));
      runStep("Tus Negocios", () -> stepTusNegocios(appPage));
      runStep("Términos y Condiciones", () ->
          validateLegalLink(appPage, "Términos y Condiciones", "Términos y Condiciones", "terminos-y-condiciones")
      );
      runStep("Política de Privacidad", () ->
          validateLegalLink(appPage, "Política de Privacidad", "Política de Privacidad", "politica-de-privacidad")
      );

      browser.close();
    }

    assertFinalReport();
  }

  private void stepLogin(final Page page) {
    if (!isSidebarVisible(page)) {
      final Locator loginButton = firstVisibleText(page,
          "Sign in with Google",
          "Iniciar sesión con Google",
          "Continuar con Google",
          "Ingresar con Google",
          "Login with Google"
      );

      Page googleFlowPage = null;
      try {
        googleFlowPage = page.context().waitForPage(() -> loginButton.click());
        waitForUiLoad(googleFlowPage);
      } catch (PlaywrightException ignored) {
        clickAndWait(page, loginButton);
      }

      if (googleFlowPage != null) {
        maybeClickAccount(googleFlowPage);
        waitForUiLoad(page);
      } else {
        maybeClickAccount(page);
      }
    }

    assertVisibleText(page, "Negocio");
    Assert.assertTrue("Left sidebar navigation should be visible after login.", isSidebarVisible(page));
    screenshot(page, "01-dashboard-loaded", true);
  }

  private void stepOpenMiNegocioMenu(final Page page) {
    Assert.assertTrue("Expected left sidebar navigation to be visible.", isSidebarVisible(page));

    clickTextIfVisible(page, "Negocio");
    clickByText(page, "Mi Negocio");

    assertVisibleText(page, "Agregar Negocio");
    assertVisibleText(page, "Administrar Negocios");
    screenshot(page, "02-mi-negocio-menu-expanded", false);
  }

  private void stepAgregarNegocioModal(final Page page) {
    clickByText(page, "Agregar Negocio");

    assertVisibleText(page, "Crear Nuevo Negocio");
    assertVisibleText(page, "Nombre del Negocio");
    assertVisibleText(page, "Tienes 2 de 3 negocios");
    assertVisibleText(page, "Cancelar");
    assertVisibleText(page, "Crear Negocio");
    screenshot(page, "03-agregar-negocio-modal", false);

    final Locator businessNameInput = firstVisibleLabel(page, "Nombre del Negocio");
    businessNameInput.fill("Negocio Prueba Automatización");
    clickByText(page, "Cancelar");
  }

  private void stepAdministrarNegocios(final Page page) {
    if (!isTextVisible(page, "Administrar Negocios")) {
      clickTextIfVisible(page, "Mi Negocio");
    }
    clickByText(page, "Administrar Negocios");

    assertVisibleText(page, "Información General");
    assertVisibleText(page, "Detalles de la Cuenta");
    assertVisibleText(page, "Tus Negocios");
    assertVisibleText(page, "Sección Legal");
    screenshot(page, "04-administrar-negocios-page", true);
  }

  private void stepInformacionGeneral(final Page page) {
    assertVisibleText(page, "Información General");
    assertVisibleText(page, ACCOUNT_EMAIL);
    assertVisibleText(page, "BUSINESS PLAN");
    assertVisibleText(page, "Cambiar Plan");

    final Locator emailLocator = firstVisibleText(page, ACCOUNT_EMAIL);
    final String blockText = Optional.ofNullable(emailLocator.locator("xpath=ancestor::*[1]").innerText()).orElse("");
    final boolean hasNameLikeText = blockText.lines()
        .map(String::trim)
        .anyMatch(line -> !line.isEmpty() && !line.equalsIgnoreCase(ACCOUNT_EMAIL) && !line.contains("@"));
    Assert.assertTrue("Expected user name text in Información General section.", hasNameLikeText);
  }

  private void stepDetallesCuenta(final Page page) {
    assertVisibleText(page, "Detalles de la Cuenta");
    assertVisibleText(page, "Cuenta creada");
    assertVisibleText(page, "Estado activo");
    assertVisibleText(page, "Idioma seleccionado");
  }

  private void stepTusNegocios(final Page page) {
    assertVisibleText(page, "Tus Negocios");
    assertVisibleText(page, "Agregar Negocio");
    assertVisibleText(page, "Tienes 2 de 3 negocios");

    final String pageText = page.locator("body").innerText();
    final long nonStaticLines = pageText.lines()
        .map(String::trim)
        .filter(line -> !line.isEmpty())
        .filter(line -> !line.equals("Tus Negocios"))
        .filter(line -> !line.equals("Agregar Negocio"))
        .filter(line -> !line.equals("Tienes 2 de 3 negocios"))
        .count();
    Assert.assertTrue("Expected visible business list content in Tus Negocios.", nonStaticLines > 0);
  }

  private void validateLegalLink(
      final Page appPage,
      final String linkText,
      final String headingText,
      final String screenshotName
  ) {
    if (!isTextVisible(appPage, linkText)) {
      assertVisibleText(appPage, "Sección Legal");
    }

    final Locator link = firstVisibleText(appPage, linkText);
    Page legalPage = null;
    boolean openedNewTab = false;

    try {
      legalPage = appPage.context().waitForPage(() -> link.click());
      openedNewTab = true;
      waitForUiLoad(legalPage);
    } catch (PlaywrightException ignored) {
      clickAndWait(appPage, link);
      legalPage = appPage;
    }

    assertVisibleText(legalPage, headingText);
    final String legalText = legalPage.locator("body").innerText();
    final boolean hasSubstantialContent = legalText != null && legalText.replace(headingText, "").trim().length() > 120;
    Assert.assertTrue("Expected legal content text to be visible for " + linkText, hasSubstantialContent);

    screenshot(legalPage, "05-" + screenshotName, true);
    legalUrls.put(linkText, legalPage.url());

    if (openedNewTab) {
      legalPage.close();
      appPage.bringToFront();
      waitForUiLoad(appPage);
    } else {
      appPage.goBack();
      waitForUiLoad(appPage);
    }
  }

  private void maybeClickAccount(final Page page) {
    if (isTextVisible(page, ACCOUNT_EMAIL)) {
      clickByText(page, ACCOUNT_EMAIL);
      return;
    }

    // Some Google flows display account items as cards where partial text match is enough.
    final Locator accountByPartial = page.getByText(Pattern.compile(Pattern.quote(ACCOUNT_EMAIL), Pattern.CASE_INSENSITIVE)).first();
    if (isVisible(accountByPartial)) {
      clickAndWait(page, accountByPartial);
    }
  }

  private void runStep(final String stepName, final Runnable stepAction) {
    try {
      stepAction.run();
      stepReport.put(stepName, true);
    } catch (AssertionError | RuntimeException ex) {
      stepReport.put(stepName, false);
      failures.put(stepName, ex.getMessage() == null ? ex.toString() : ex.getMessage());
    }
  }

  private void assertFinalReport() {
    final List<String> order = List.of(
        "Login",
        "Mi Negocio menu",
        "Agregar Negocio modal",
        "Administrar Negocios view",
        "Información General",
        "Detalles de la Cuenta",
        "Tus Negocios",
        "Términos y Condiciones",
        "Política de Privacidad"
    );

    final StringBuilder summary = new StringBuilder();
    summary.append("\nSaleADS Mi Negocio Workflow Report\n");
    summary.append("Evidence directory: ").append(evidenceDir).append("\n");
    for (final String field : order) {
      final boolean passed = Boolean.TRUE.equals(stepReport.get(field));
      summary.append("- ").append(field).append(": ").append(passed ? "PASS" : "FAIL").append("\n");
      if (!passed && failures.containsKey(field)) {
        summary.append("  reason: ").append(failures.get(field)).append("\n");
      }
    }
    if (!legalUrls.isEmpty()) {
      summary.append("Legal URLs:\n");
      for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
        summary.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
      }
    }

    System.out.println(summary);

    final List<String> failedSteps = new ArrayList<>();
    for (final String field : order) {
      if (!Boolean.TRUE.equals(stepReport.get(field))) {
        failedSteps.add(field);
      }
    }
    Assert.assertTrue("Failed steps: " + failedSteps + "\n" + summary, failedSteps.isEmpty());
  }

  private boolean isSidebarVisible(final Page page) {
    return isVisible(page.locator("aside").first())
        || isVisible(page.locator("nav").first())
        || isTextVisible(page, "Mi Negocio");
  }

  private Locator firstVisibleText(final Page page, final String... candidates) {
    for (final String candidate : candidates) {
      final Pattern exactPattern = Pattern.compile(
          "^\\s*" + Pattern.quote(candidate) + "\\s*$",
          Pattern.CASE_INSENSITIVE
      );
      final Locator exact = page.getByText(exactPattern).first();
      if (isVisible(exact)) {
        return exact;
      }

      final Locator partial = page.getByText(Pattern.compile(Pattern.quote(candidate), Pattern.CASE_INSENSITIVE)).first();
      if (isVisible(partial)) {
        return partial;
      }
    }
    throw new AssertionError("None of the expected text candidates are visible: " + List.of(candidates));
  }

  private Locator firstVisibleLabel(final Page page, final String labelText) {
    final Locator byLabel = page.getByLabel(labelText).first();
    if (isVisible(byLabel)) {
      return byLabel;
    }

    final Locator placeholder = page.getByPlaceholder(labelText).first();
    if (isVisible(placeholder)) {
      return placeholder;
    }

    final Locator textboxByRole = page.getByRole(com.microsoft.playwright.options.AriaRole.TEXTBOX,
        new Page.GetByRoleOptions().setName(Pattern.compile(Pattern.quote(labelText), Pattern.CASE_INSENSITIVE))).first();
    if (isVisible(textboxByRole)) {
      return textboxByRole;
    }

    throw new AssertionError("No visible input found for label: " + labelText);
  }

  private void clickByText(final Page page, final String... candidates) {
    final Locator target = firstVisibleText(page, candidates);
    clickAndWait(page, target);
  }

  private void clickTextIfVisible(final Page page, final String text) {
    final Locator textLocator = page.getByText(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE)).first();
    if (isVisible(textLocator)) {
      clickAndWait(page, textLocator);
    }
  }

  private void assertVisibleText(final Page page, final String text) {
    final Locator locator = firstVisibleText(page, text);
    Assert.assertTrue("Expected visible text: " + text, isVisible(locator));
  }

  private boolean isTextVisible(final Page page, final String text) {
    final Locator locator = page.getByText(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE)).first();
    return isVisible(locator);
  }

  private boolean isVisible(final Locator locator) {
    try {
      locator.waitFor(new Locator.WaitForOptions()
          .setState(Locator.WaitForOptions.State.VISIBLE)
          .setTimeout(2_500)
      );
      return locator.isVisible();
    } catch (PlaywrightException ex) {
      return false;
    }
  }

  private void clickAndWait(final Page page, final Locator locator) {
    locator.click(new Locator.ClickOptions().setTimeout(10_000));
    waitForUiLoad(page);
  }

  private void waitForUiLoad(final Page page) {
    try {
      page.waitForLoadState(Page.LoadState.DOMCONTENTLOADED);
    } catch (PlaywrightException ignored) {
      // Some SPA transitions do not trigger this event consistently.
    }
    try {
      page.waitForLoadState(Page.LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(8_000));
    } catch (PlaywrightException ignored) {
      // NETWORKIDLE is best-effort in SPAs.
    }
  }

  private void screenshot(final Page page, final String name, final boolean fullPage) {
    final String sanitized = sanitize(name);
    page.screenshot(new Page.ScreenshotOptions()
        .setPath(evidenceDir.resolve(sanitized + ".png"))
        .setFullPage(fullPage)
    );
  }

  private String sanitize(final String value) {
    final String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "");
    return normalized
        .toLowerCase()
        .replaceAll("[^a-z0-9._-]+", "-")
        .replaceAll("^-+|-+$", "");
  }

  private boolean isEnabled() {
    return Boolean.parseBoolean(firstNonBlank(
        System.getenv("SALEADS_E2E_ENABLED"),
        System.getProperty("saleads.e2e.enabled"),
        "false"
    ));
  }

  private boolean isHeadless() {
    return Boolean.parseBoolean(firstNonBlank(
        System.getenv("SALEADS_HEADLESS"),
        System.getProperty("saleads.headless"),
        "true"
    ));
  }

  private String firstNonBlank(final String... candidates) {
    for (final String candidate : candidates) {
      if (candidate != null && !candidate.trim().isEmpty()) {
        return candidate.trim();
      }
    }
    return null;
  }
}
