package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Assume;
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

public class SaleadsMiNegocioFullWorkflowTest {

  private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
  private static final String BUSINESS_NAME = "Negocio Prueba Automatizacion";

  @Test
  public void saleadsMiNegocioFullTest() throws Exception {
    final String startUrl = envOrDefault("SALEADS_START_URL", "").trim();
    Assume.assumeTrue("Set SALEADS_START_URL to the login page for the target SaleADS environment.",
        !startUrl.isEmpty());

    final Path evidenceDir = createEvidenceDirectory();
    final Map<String, StepStatus> report = initializeReport();

    try (Playwright playwright = Playwright.create()) {
      final boolean headless = Boolean.parseBoolean(envOrDefault("SALEADS_HEADLESS", "true"));
      final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
      final BrowserContext context = browser.newContext(
          new Browser.NewContextOptions().setViewportSize(1440, 900));
      final Page page = context.newPage();

      page.navigate(startUrl);
      waitForUiLoad(page);

      final boolean loginOk = runStep(report, "Login", () -> {
        stepLoginWithGoogle(page, context);
        expectAnyVisible(page, "main application interface", 15_000,
            Pattern.compile("(?iu)dashboard"),
            Pattern.compile("(?iu)negocio"),
            Pattern.compile("(?iu)mi\\s+negocio"));
        expectSidebarVisible(page);
        takeScreenshot(page, evidenceDir, "01-dashboard-loaded.png", false);
      });

      if (!loginOk) {
        blockRemaining(report, "Blocked by failed login step.");
      } else {
        final boolean menuOk = runStep(report, "Mi Negocio menu", () -> {
          clickByVisibleText(page, "Negocio");
          clickByVisibleText(page, "Mi Negocio");

          expectVisibleText(page, "Agregar Negocio", 10_000);
          expectVisibleText(page, "Administrar Negocios", 10_000);
          takeScreenshot(page, evidenceDir, "02-mi-negocio-menu-expanded.png", false);
        });

        if (!menuOk) {
          blockRemaining(report, "Blocked by failed Mi Negocio menu step.");
        } else {
          final boolean modalOk = runStep(report, "Agregar Negocio modal", () -> {
            clickByVisibleText(page, "Agregar Negocio");
            expectVisibleText(page, "Crear Nuevo Negocio", 10_000);
            expectBusinessNameField(page);
            expectVisibleText(page, "Tienes 2 de 3 negocios", 10_000);
            expectVisibleText(page, "Cancelar", 10_000);
            expectVisibleText(page, "Crear Negocio", 10_000);
            takeScreenshot(page, evidenceDir, "03-agregar-negocio-modal.png", false);

            final Locator businessNameField = resolveBusinessNameField(page);
            businessNameField.click();
            businessNameField.fill(BUSINESS_NAME);
            clickByVisibleText(page, "Cancelar");
            waitForUiLoad(page);
          });

          if (!modalOk) {
            blockRemaining(report, "Blocked by failed Agregar Negocio modal step.");
          } else {
            final boolean adminViewOk = runStep(report, "Administrar Negocios view", () -> {
              ensureMiNegocioMenuExpanded(page);
              clickByVisibleText(page, "Administrar Negocios");
              waitForUiLoad(page);

              expectAnyVisible(page, "Informacion General", 15_000,
                  Pattern.compile("(?iu)Informaci[o\\u00f3]n\\s+General"));
              expectVisibleText(page, "Detalles de la Cuenta", 15_000);
              expectVisibleText(page, "Tus Negocios", 15_000);
              expectAnyVisible(page, "Seccion Legal", 15_000,
                  Pattern.compile("(?iu)Secci[o\\u00f3]n\\s+Legal"));
              takeScreenshot(page, evidenceDir, "04-administrar-negocios-view.png", true);
            });

            if (!adminViewOk) {
              blockRemaining(report, "Blocked by failed Administrar Negocios view step.");
            } else {
              runStep(report, "Informaci\u00f3n General", () -> {
                expectVisibleText(page, GOOGLE_ACCOUNT, 10_000);
                expectAnyVisible(page, "user name", 10_000,
                    Pattern.compile("(?iu)nombre"),
                    Pattern.compile("(?iu)juan"),
                    Pattern.compile("(?iu)perfil"));
                expectVisibleText(page, "BUSINESS PLAN", 10_000);
                expectVisibleText(page, "Cambiar Plan", 10_000);
              });

              runStep(report, "Detalles de la Cuenta", () -> {
                expectVisibleText(page, "Cuenta creada", 10_000);
                expectVisibleText(page, "Estado activo", 10_000);
                expectVisibleText(page, "Idioma seleccionado", 10_000);
              });

              runStep(report, "Tus Negocios", () -> {
                expectVisibleText(page, "Tus Negocios", 10_000);
                expectVisibleText(page, "Agregar Negocio", 10_000);
                expectVisibleText(page, "Tienes 2 de 3 negocios", 10_000);
              });

              runStep(report, "T\u00e9rminos y Condiciones", () -> {
                final String legalUrl = openLegalDocument(page, context,
                    Pattern.compile("(?iu)T[\\u00e9e]rminos\\s+y\\s+Condiciones"),
                    Pattern.compile("(?iu)T[\\u00e9e]rminos\\s+y\\s+Condiciones"),
                    evidenceDir, "05-terminos-y-condiciones.png");
                report.get("T\u00e9rminos y Condiciones").details = "Final URL: " + legalUrl;
              });

              runStep(report, "Pol\u00edtica de Privacidad", () -> {
                final String legalUrl = openLegalDocument(page, context,
                    Pattern.compile("(?iu)Pol[\\u00edi]tica\\s+de\\s+Privacidad"),
                    Pattern.compile("(?iu)Pol[\\u00edi]tica\\s+de\\s+Privacidad"),
                    evidenceDir, "06-politica-de-privacidad.png");
                report.get("Pol\u00edtica de Privacidad").details = "Final URL: " + legalUrl;
              });
            }
          }
        }
      }
    } finally {
      printFinalReport(report);
    }

    final List<String> failed = new ArrayList<>();
    for (Map.Entry<String, StepStatus> entry : report.entrySet()) {
      if (!entry.getValue().passed) {
        final String details = entry.getValue().details == null ? "" : " - " + entry.getValue().details;
        failed.add(entry.getKey() + details);
      }
    }
    assertTrue("Some validations failed:\n" + String.join("\n", failed), failed.isEmpty());
  }

  private boolean runStep(final Map<String, StepStatus> report, final String stepName, final StepAction action) {
    final StepStatus status = report.get(stepName);
    if (status.blocked) {
      return false;
    }

    try {
      action.run();
      status.passed = true;
      return true;
    } catch (Throwable throwable) {
      status.passed = false;
      status.details = throwable.getMessage();
      return false;
    }
  }

  private void blockRemaining(final Map<String, StepStatus> report, final String reason) {
    for (StepStatus status : report.values()) {
      if (status.passed) {
        continue;
      }
      if (status.details == null) {
        status.blocked = true;
        status.details = reason;
      }
    }
  }

  private void stepLoginWithGoogle(final Page appPage, final BrowserContext context) {
    final int pagesBeforeLoginClick = context.pages().size();
    clickFirstMatching(appPage, 15_000,
        Pattern.compile("(?iu)Sign\\s*in\\s*with\\s*Google"),
        Pattern.compile("(?iu)Iniciar\\s+sesi[o\\u00f3]n\\s+con\\s+Google"),
        Pattern.compile("(?iu)Google"));

    final Page loginPage = waitForNewPage(context, pagesBeforeLoginClick, appPage, 8_000);
    final Page activeAuthPage = loginPage == null ? appPage : loginPage;
    trySelectGoogleAccount(activeAuthPage);

    if (loginPage != null) {
      loginPage.waitForTimeout(2000);
      try {
        loginPage.close();
      } catch (PlaywrightException ignored) {
        // Google popup often closes itself after account selection.
      }
    }

    waitForUiLoad(appPage);
    expectSidebarVisible(appPage);
  }

  private String openLegalDocument(final Page appPage, final BrowserContext context, final Pattern linkPattern,
      final Pattern headingPattern, final Path evidenceDir, final String screenshotName) {
    final int pagesBeforeClick = context.pages().size();
    clickFirstMatching(appPage, 10_000, linkPattern);
    waitForUiLoad(appPage);

    final Page legalPage = waitForNewPage(context, pagesBeforeClick, appPage, 8_000);
    if (legalPage != null) {
      legalPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
      expectAnyVisible(legalPage, "legal heading", 15_000, headingPattern);
      expectLegalBodyContent(legalPage);
      takeScreenshot(legalPage, evidenceDir, screenshotName, true);

      final String url = legalPage.url();
      legalPage.close();
      appPage.bringToFront();
      waitForUiLoad(appPage);
      return url;
    }

    expectAnyVisible(appPage, "legal heading", 15_000, headingPattern);
    expectLegalBodyContent(appPage);
    takeScreenshot(appPage, evidenceDir, screenshotName, true);

    final String url = appPage.url();
    appPage.goBack();
    waitForUiLoad(appPage);
    return url;
  }

  private void ensureMiNegocioMenuExpanded(final Page page) {
    if (!isTextVisible(page, "Administrar Negocios")) {
      clickByVisibleText(page, "Mi Negocio");
      waitForUiLoad(page);
    }
  }

  private void clickByVisibleText(final Page page, final String text) {
    clickFirstMatching(page, 10_000,
        Pattern.compile("(?iu)^\\s*" + Pattern.quote(text) + "\\s*$"),
        Pattern.compile("(?iu)" + Pattern.quote(text)));
  }

  private void clickFirstMatching(final Page page, final double timeoutMs, final Pattern... patterns) {
    for (Pattern pattern : patterns) {
      final Locator locator = page.getByText(pattern).first();
      if (isVisible(locator, timeoutMs)) {
        locator.click();
        waitForUiLoad(page);
        return;
      }
    }

    final StringBuilder joined = new StringBuilder();
    for (int i = 0; i < patterns.length; i++) {
      if (i > 0) {
        joined.append(", ");
      }
      joined.append(patterns[i].pattern());
    }
    throw new AssertionError("Could not find clickable element matching patterns: " + joined);
  }

  private void expectVisibleText(final Page page, final String text, final double timeoutMs) {
    expectAnyVisible(page, text, timeoutMs,
        Pattern.compile("(?iu)^\\s*" + Pattern.quote(text) + "\\s*$"),
        Pattern.compile("(?iu)" + Pattern.quote(text)));
  }

  private void expectAnyVisible(final Page page, final String description, final double timeoutMs,
      final Pattern... patterns) {
    final long deadline = System.currentTimeMillis() + (long) timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      for (Pattern pattern : patterns) {
        if (isVisible(page.getByText(pattern).first(), 300)) {
          return;
        }
      }
      page.waitForTimeout(200);
    }
    throw new AssertionError("Expected visible text for: " + description);
  }

  private void expectSidebarVisible(final Page page) {
    final Locator sidebar = page.locator("aside, nav").first();
    if (!isVisible(sidebar, 15_000)) {
      throw new AssertionError("Left sidebar navigation is not visible.");
    }
  }

  private void expectBusinessNameField(final Page page) {
    if (isVisible(resolveBusinessNameField(page), 10_000)) {
      return;
    }
    throw new AssertionError("Input field 'Nombre del Negocio' was not found.");
  }

  private Locator resolveBusinessNameField(final Page page) {
    final Locator byLabel = page.getByLabel(Pattern.compile("(?iu)Nombre\\s+del\\s+Negocio")).first();
    if (isVisible(byLabel, 300)) {
      return byLabel;
    }

    final Locator byPlaceholder = page
        .getByPlaceholder(Pattern.compile("(?iu)Nombre\\s+del\\s+Negocio"))
        .first();
    if (isVisible(byPlaceholder, 300)) {
      return byPlaceholder;
    }

    return page.locator("input[type='text']").first();
  }

  private void expectLegalBodyContent(final Page page) {
    final Locator paragraphs = page.locator("main p, article p, p");
    if (paragraphs.count() > 0) {
      return;
    }
    throw new AssertionError("Legal content body text is not visible.");
  }

  private void trySelectGoogleAccount(final Page page) {
    final Locator account = page.getByText(Pattern.compile("(?iu)" + Pattern.quote(GOOGLE_ACCOUNT))).first();
    if (isVisible(account, 8_000)) {
      account.click();
      waitForUiLoad(page);
      return;
    }

    // If selector is not shown, auth may already be completed or use a one-click account flow.
    page.waitForTimeout(2000);
  }

  private Page waitForNewPage(final BrowserContext context, final int previousPageCount, final Page pollingPage,
      final long timeoutMs) {
    final long deadline = System.currentTimeMillis() + timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      if (context.pages().size() > previousPageCount) {
        final List<Page> pages = context.pages();
        return pages.get(pages.size() - 1);
      }
      pollingPage.waitForTimeout(200);
    }
    return null;
  }

  private boolean isVisible(final Locator locator, final double timeoutMs) {
    try {
      locator.waitFor(new Locator.WaitForOptions()
          .setState(WaitForSelectorState.VISIBLE)
          .setTimeout(timeoutMs));
      return true;
    } catch (PlaywrightException exception) {
      return false;
    }
  }

  private boolean isTextVisible(final Page page, final String text) {
    return isVisible(page.getByText(Pattern.compile("(?iu)" + Pattern.quote(text))).first(), 500);
  }

  private void waitForUiLoad(final Page page) {
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE);
    } catch (PlaywrightException ignored) {
      page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    }
  }

  private void takeScreenshot(final Page page, final Path evidenceDir, final String fileName, final boolean fullPage) {
    page.screenshot(new Page.ScreenshotOptions()
        .setPath(evidenceDir.resolve(fileName))
        .setFullPage(fullPage));
  }

  private Path createEvidenceDirectory() throws IOException {
    final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    final Path evidenceDir = Paths.get("target", "saleads-evidence", timestamp);
    Files.createDirectories(evidenceDir);
    return evidenceDir;
  }

  private String envOrDefault(final String key, final String defaultValue) {
    final String value = System.getenv(key);
    return value == null ? defaultValue : value;
  }

  private Map<String, StepStatus> initializeReport() {
    final Map<String, StepStatus> report = new LinkedHashMap<>();
    report.put("Login", new StepStatus());
    report.put("Mi Negocio menu", new StepStatus());
    report.put("Agregar Negocio modal", new StepStatus());
    report.put("Administrar Negocios view", new StepStatus());
    report.put("Informaci\u00f3n General", new StepStatus());
    report.put("Detalles de la Cuenta", new StepStatus());
    report.put("Tus Negocios", new StepStatus());
    report.put("T\u00e9rminos y Condiciones", new StepStatus());
    report.put("Pol\u00edtica de Privacidad", new StepStatus());
    return report;
  }

  private void printFinalReport(final Map<String, StepStatus> report) {
    System.out.println();
    System.out.println("==== SaleADS Mi Negocio Workflow Final Report ====");
    for (Map.Entry<String, StepStatus> entry : report.entrySet()) {
      final String status = entry.getValue().passed ? "PASS" : "FAIL";
      final String details = entry.getValue().details == null ? "" : " | " + entry.getValue().details;
      System.out.println("- " + entry.getKey() + ": " + status + details);
    }
    System.out.println("==================================================");
  }

  @FunctionalInterface
  private interface StepAction {
    void run() throws Exception;
  }

  private static final class StepStatus {
    private boolean passed;
    private boolean blocked;
    private String details;
  }
}
