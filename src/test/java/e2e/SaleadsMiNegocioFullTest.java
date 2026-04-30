package e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public class SaleadsMiNegocioFullTest {

  private static final String DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
  private static final int DEFAULT_TIMEOUT_MS = 30000;
  private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

  @Test
  public void saleadsMiNegocioFullWorkflow() throws Exception {
    final boolean enabled = Boolean.parseBoolean(env("E2E_SALEADS_ENABLED", "false"));
    final String baseUrl = env("SALEADS_BASE_URL", "").trim();
    Assume.assumeTrue("Set E2E_SALEADS_ENABLED=true to run SaleADS E2E.", enabled);
    Assume.assumeTrue("Set SALEADS_BASE_URL to the active environment login page.", !baseUrl.isEmpty());

    final String googleEmail = env("SALEADS_GOOGLE_EMAIL", DEFAULT_GOOGLE_EMAIL);
    final boolean headless = Boolean.parseBoolean(env("E2E_HEADLESS", "true"));
    final Path evidenceDir = Paths.get(env("E2E_SCREENSHOT_DIR", "target/saleads-evidence"));
    Files.createDirectories(evidenceDir);

    final Map<String, StepStatus> report = initReport();
    final Map<String, String> legalUrls = new LinkedHashMap<>();

    try (Playwright playwright = Playwright.create()) {
      Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
      BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
      Page appPage = context.newPage();
      appPage.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
      appPage.navigate(baseUrl);
      waitForUiSettle(appPage);

      runStep(report, "Login", () -> {
        Locator googleLoginButton = firstVisible(
          appPage,
          "button:has-text('Sign in with Google')",
          "button:has-text('Iniciar sesión con Google')",
          "button:has-text('Continuar con Google')",
          "button:has-text('Ingresar con Google')",
          "[role='button']:has-text('Google')"
        );
        clickAndWait(appPage, googleLoginButton);
        selectGoogleAccountIfPresent(appPage, context, googleEmail);

        requireAnyVisible(
          appPage,
          "Main application interface",
          "aside",
          "nav",
          "main",
          "text=/Mi\\s+Negocio/i"
        );
        requireAnyVisible(appPage, "Left sidebar navigation", "aside", "nav");
        screenshot(appPage, evidenceDir, "01-dashboard-loaded", false);
      });

      runStep(report, "Mi Negocio menu", () -> {
        Locator miNegocio = firstVisible(
          appPage,
          "text='Mi Negocio'",
          "text=/\\bNegocio\\b/i"
        );
        clickAndWait(appPage, miNegocio);

        requireAnyVisible(appPage, "'Agregar Negocio' visible", "text='Agregar Negocio'");
        requireAnyVisible(appPage, "'Administrar Negocios' visible", "text='Administrar Negocios'");
        screenshot(appPage, evidenceDir, "02-mi-negocio-menu-expanded", false);
      });

      runStep(report, "Agregar Negocio modal", () -> {
        Locator agregarNegocio = firstVisible(appPage, "text='Agregar Negocio'");
        clickAndWait(appPage, agregarNegocio);

        requireAnyVisible(appPage, "Modal title", "text='Crear Nuevo Negocio'");

        Locator businessNameInput = firstVisible(
          appPage,
          "input[placeholder='Nombre del Negocio']",
          "label:has-text('Nombre del Negocio') + input",
          "input[name*='negocio']"
        );
        Assert.assertTrue("Nombre del Negocio input should be visible", businessNameInput.isVisible());

        requireAnyVisible(appPage, "Limit text", "text='Tienes 2 de 3 negocios'");
        requireAnyVisible(appPage, "Cancelar button", "button:has-text('Cancelar')");
        requireAnyVisible(appPage, "Crear Negocio button", "button:has-text('Crear Negocio')");

        screenshot(appPage, evidenceDir, "03-agregar-negocio-modal", false);

        businessNameInput.fill("Negocio Prueba Automatización");
        clickAndWait(appPage, firstVisible(appPage, "button:has-text('Cancelar')"));
      });

      runStep(report, "Administrar Negocios view", () -> {
        if (!isAnyVisible(appPage, "text='Administrar Negocios'")) {
          clickAndWait(appPage, firstVisible(appPage, "text='Mi Negocio'", "text=/\\bNegocio\\b/i"));
        }
        clickAndWait(appPage, firstVisible(appPage, "text='Administrar Negocios'"));

        requireAnyVisible(appPage, "Información General section", "text='Información General'");
        requireAnyVisible(appPage, "Detalles de la Cuenta section", "text='Detalles de la Cuenta'");
        requireAnyVisible(appPage, "Tus Negocios section", "text='Tus Negocios'");
        requireAnyVisible(appPage, "Sección Legal section", "text='Sección Legal'");

        screenshot(appPage, evidenceDir, "04-administrar-negocios-page", true);
      });

      runStep(report, "Información General", () -> {
        requireAnyVisible(appPage, "User name visible", "[data-testid*='name']", "text=/^[A-Z][a-z]+\\s+[A-Z].*/");
        requireAnyVisible(appPage, "User email visible", "text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/");
        requireAnyVisible(appPage, "BUSINESS PLAN text", "text='BUSINESS PLAN'");
        requireAnyVisible(appPage, "Cambiar Plan button", "button:has-text('Cambiar Plan')");
      });

      runStep(report, "Detalles de la Cuenta", () -> {
        requireAnyVisible(appPage, "Cuenta creada", "text='Cuenta creada'");
        requireAnyVisible(appPage, "Estado activo", "text='Estado activo'");
        requireAnyVisible(appPage, "Idioma seleccionado", "text='Idioma seleccionado'");
      });

      runStep(report, "Tus Negocios", () -> {
        requireAnyVisible(
          appPage,
          "Business list visible",
          "text='Tus Negocios'",
          "table",
          "[role='table']",
          "[role='list']"
        );
        requireAnyVisible(appPage, "Agregar Negocio button exists", "button:has-text('Agregar Negocio')", "text='Agregar Negocio'");
        requireAnyVisible(appPage, "Tienes 2 de 3 negocios text", "text='Tienes 2 de 3 negocios'");
      });

      runStep(report, "Términos y Condiciones", () -> {
        String url = validateLegalLinkAndReturn(
          appPage,
          "Términos y Condiciones",
          "Términos y Condiciones",
          evidenceDir,
          "08-terminos-y-condiciones"
        );
        legalUrls.put("Términos y Condiciones", url);
      });

      runStep(report, "Política de Privacidad", () -> {
        String url = validateLegalLinkAndReturn(
          appPage,
          "Política de Privacidad",
          "Política de Privacidad",
          evidenceDir,
          "09-politica-de-privacidad"
        );
        legalUrls.put("Política de Privacidad", url);
      });

      writeFinalReport(report, legalUrls, evidenceDir);
      context.close();
      browser.close();
    }

    Assert.assertTrue("Final report includes failed validations:\n" + summarize(report), allPassed(report));
  }

  private static String validateLegalLinkAndReturn(Page appPage,
                                                   String linkText,
                                                   String headingText,
                                                   Path evidenceDir,
                                                   String screenshotName) {
    String appUrlBefore = appPage.url();
    Page targetPage;
    boolean popupOpened = false;

    try {
      targetPage = appPage.waitForPopup(
        new Page.WaitForPopupOptions().setTimeout(5000),
        () -> clickAndWait(appPage, firstVisible(appPage, "text='" + linkText + "'"))
      );
      popupOpened = true;
    } catch (PlaywrightException ex) {
      clickAndWait(appPage, firstVisible(appPage, "text='" + linkText + "'"));
      targetPage = appPage;
    }

    waitForUiSettle(targetPage);
    requireAnyVisible(targetPage, headingText + " heading", "text='" + headingText + "'");
    requireAnyVisible(
      targetPage,
      headingText + " legal text",
      "article",
      "main",
      "text=/t[eé]rminos|privacidad|legal|condiciones/i"
    );
    screenshot(targetPage, evidenceDir, screenshotName, true);

    String finalUrl = targetPage.url();

    if (popupOpened) {
      targetPage.close();
      appPage.bringToFront();
      waitForUiSettle(appPage);
    } else {
      appPage.goBack();
      if (!appPage.url().equals(appUrlBefore)) {
        appPage.navigate(appUrlBefore);
      }
      waitForUiSettle(appPage);
    }

    return finalUrl;
  }

  private static void runStep(Map<String, StepStatus> report, String stepName, Runnable action) {
    try {
      action.run();
      report.put(stepName, StepStatus.pass());
    } catch (Throwable t) {
      report.put(stepName, StepStatus.fail(t.getMessage()));
    }
  }

  private static void clickAndWait(Page page, Locator locator) {
    locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
    locator.first().click();
    waitForUiSettle(page);
  }

  private static void waitForUiSettle(Page page) {
    try {
      page.waitForLoadState(LoadState.DOMCONTENTLOADED);
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
    } catch (PlaywrightException ignored) {
      // Not every click triggers navigation/network-idle; keep progressing.
    }
    page.waitForTimeout(500);
  }

  private static void selectGoogleAccountIfPresent(Page appPage, BrowserContext context, String email) {
    Locator accountOption = null;

    if (isAnyVisible(appPage, "text='" + email + "'")) {
      accountOption = appPage.locator("text='" + email + "'");
    } else {
      for (Page candidate : context.pages()) {
        if (candidate.isClosed()) {
          continue;
        }
        if (isAnyVisible(candidate, "text='" + email + "'")) {
          accountOption = candidate.locator("text='" + email + "'");
          break;
        }
      }
    }

    if (accountOption != null && accountOption.first().isVisible()) {
      accountOption.first().click();
      waitForUiSettle(appPage);
    }
  }

  private static Locator firstVisible(Page page, String... selectors) {
    long deadline = System.currentTimeMillis() + DEFAULT_TIMEOUT_MS;
    while (System.currentTimeMillis() < deadline) {
      for (String selector : selectors) {
        Locator candidate = page.locator(selector).first();
        try {
          if (candidate.count() > 0 && candidate.isVisible()) {
            return candidate;
          }
        } catch (PlaywrightException ignored) {
          // Keep searching across selectors until timeout.
        }
      }
      page.waitForTimeout(250);
    }
    throw new AssertionError("No visible selector found: " + String.join(" | ", selectors));
  }

  private static void requireAnyVisible(Page page, String description, String... selectors) {
    Locator visible = firstVisible(page, selectors);
    Assert.assertTrue(description + " should be visible", visible.isVisible());
  }

  private static boolean isAnyVisible(Page page, String... selectors) {
    for (String selector : selectors) {
      Locator candidate = page.locator(selector).first();
      try {
        if (candidate.count() > 0 && candidate.isVisible()) {
          return true;
        }
      } catch (PlaywrightException ignored) {
        // Ignore transient selector failures and continue.
      }
    }
    return false;
  }

  private static void screenshot(Page page, Path evidenceDir, String checkpoint, boolean fullPage) {
    String fileName = LocalDateTime.now().format(FILE_TS) + "-" + checkpoint + ".png";
    Path path = evidenceDir.resolve(fileName);
    page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
  }

  private static Map<String, StepStatus> initReport() {
    Map<String, StepStatus> report = new LinkedHashMap<>();
    report.put("Login", StepStatus.notRun());
    report.put("Mi Negocio menu", StepStatus.notRun());
    report.put("Agregar Negocio modal", StepStatus.notRun());
    report.put("Administrar Negocios view", StepStatus.notRun());
    report.put("Información General", StepStatus.notRun());
    report.put("Detalles de la Cuenta", StepStatus.notRun());
    report.put("Tus Negocios", StepStatus.notRun());
    report.put("Términos y Condiciones", StepStatus.notRun());
    report.put("Política de Privacidad", StepStatus.notRun());
    return report;
  }

  private static void writeFinalReport(Map<String, StepStatus> report, Map<String, String> legalUrls, Path evidenceDir) throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("SaleADS Mi Negocio full workflow report\n");
    sb.append("Generated at: ").append(LocalDateTime.now()).append("\n\n");
    sb.append("Validation status:\n");
    for (Map.Entry<String, StepStatus> entry : report.entrySet()) {
      sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue().status);
      if (!entry.getValue().reason.isEmpty()) {
        sb.append(" (").append(entry.getValue().reason).append(")");
      }
      sb.append("\n");
    }

    sb.append("\nFinal URLs:\n");
    sb.append("- Términos y Condiciones: ").append(legalUrls.getOrDefault("Términos y Condiciones", "N/A")).append("\n");
    sb.append("- Política de Privacidad: ").append(legalUrls.getOrDefault("Política de Privacidad", "N/A")).append("\n");

    Path reportFile = evidenceDir.resolve("final-report.txt");
    Files.writeString(reportFile, sb.toString());
    System.out.println(sb);
  }

  private static boolean allPassed(Map<String, StepStatus> report) {
    return report.values().stream().allMatch(status -> "PASS".equals(status.status));
  }

  private static String summarize(Map<String, StepStatus> report) {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, StepStatus> entry : report.entrySet()) {
      sb.append(entry.getKey()).append(": ").append(entry.getValue().status);
      if (!entry.getValue().reason.isEmpty()) {
        sb.append(" (").append(entry.getValue().reason).append(")");
      }
      sb.append("\n");
    }
    return sb.toString();
  }

  private static String env(String key, String defaultValue) {
    String value = System.getenv(key);
    return value == null || value.isBlank() ? defaultValue : value;
  }

  private static final class StepStatus {
    private final String status;
    private final String reason;

    private StepStatus(String status, String reason) {
      this.status = status;
      this.reason = reason == null ? "" : reason;
    }

    static StepStatus pass() {
      return new StepStatus("PASS", "");
    }

    static StepStatus fail(String reason) {
      return new StepStatus("FAIL", reason);
    }

    static StepStatus notRun() {
      return new StepStatus("NOT_RUN", "");
    }
  }
}
