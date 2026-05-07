package io.proleap.cobol.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * Full E2E flow for SaleADS "Mi Negocio".
 *
 * <p>
 * Run explicitly (kept opt-in to avoid affecting regular parser builds):
 * </p>
 *
 * <pre>
 * mvn -Dtest=SaleadsMiNegocioFullWorkflowTest \
 *   -Dsaleads.e2e.enabled=true \
 *   -Dsaleads.url=https://&lt;current-environment-login-url&gt; \
 *   test
 * </pre>
 */
public class SaleadsMiNegocioFullWorkflowTest {

  private static final String STEP_LOGIN = "Login";
  private static final String STEP_MENU = "Mi Negocio menu";
  private static final String STEP_MODAL = "Agregar Negocio modal";
  private static final String STEP_ADMIN = "Administrar Negocios view";
  private static final String STEP_INFO = "Información General";
  private static final String STEP_ACCOUNT = "Detalles de la Cuenta";
  private static final String STEP_BUSINESSES = "Tus Negocios";
  private static final String STEP_TERMS = "Términos y Condiciones";
  private static final String STEP_PRIVACY = "Política de Privacidad";
  private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

  @Test
  public void saleadsMiNegocioFullTest() throws IOException {
    Assume.assumeTrue("Set -Dsaleads.e2e.enabled=true to run this opt-in UI workflow test.",
        Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false")));

    final Map<String, String> report = initReport();
    final String targetUrl = firstNonBlank(System.getProperty("saleads.url"), System.getenv("SALEADS_URL"),
        System.getenv("SALEADS_LOGIN_URL"));
    final Path evidenceDir = createEvidenceDirectory();
    String termsUrl = "";
    String privacyUrl = "";
    String blockingStep = null;

    try (Playwright playwright = Playwright.create()) {
      final Browser browser = playwright.chromium()
          .launch(new BrowserType.LaunchOptions().setHeadless(Boolean.parseBoolean(System.getProperty("saleads.headless", "true"))));
      final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
      final Page page = context.newPage();

      if (isBlank(targetUrl)) {
        throw new IllegalStateException(
            "Missing login URL. Provide -Dsaleads.url=<SaleADS login URL> (works for any environment).");
      }

      page.navigate(targetUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
      waitForUi(page);

      if (blockingStep == null) {
        try {
          executeLoginWithGoogle(page, context);
          captureScreenshot(page, evidenceDir.resolve("01-dashboard-loaded.png"), false);
          report.put(STEP_LOGIN, "PASS");
        } catch (Exception e) {
          report.put(STEP_LOGIN, "FAIL - " + e.getMessage());
          blockingStep = STEP_LOGIN;
        }
      }

      if (blockingStep == null) {
        try {
          expandMiNegocioMenu(page);
          assertVisible(page.getByText("Agregar Negocio", new Page.GetByTextOptions().setExact(true)),
              "Agregar Negocio should be visible.");
          assertVisible(page.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(true)),
              "Administrar Negocios should be visible.");
          captureScreenshot(page, evidenceDir.resolve("02-mi-negocio-expanded.png"), false);
          report.put(STEP_MENU, "PASS");
        } catch (Exception e) {
          report.put(STEP_MENU, "FAIL - " + e.getMessage());
          blockingStep = STEP_MENU;
        }
      }

      if (blockingStep == null) {
        try {
          clickByVisibleText(page, "Agregar Negocio");
          assertVisible(page.getByText("Crear Nuevo Negocio", new Page.GetByTextOptions().setExact(true)),
              "Modal title 'Crear Nuevo Negocio' should be visible.");
          final Locator nombreInput = firstVisibleLocator(
              page.getByLabel("Nombre del Negocio", new Page.GetByLabelOptions().setExact(true)),
              page.getByPlaceholder("Nombre del Negocio"),
              page.locator("input[name*='nombre'], input[id*='nombre']"));
          assertTrue("Nombre del Negocio input should exist.", nombreInput.count() > 0);
          assertVisible(page.getByText("Tienes 2 de 3 negocios"), "Business quota text should be visible.");
          assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")),
              "Cancelar button should be visible.");
          assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Crear Negocio")),
              "Crear Negocio button should be visible.");

          captureScreenshot(page, evidenceDir.resolve("03-agregar-negocio-modal.png"), false);
          nombreInput.first().fill("Negocio Prueba Automatización");
          clickByVisibleText(page, "Cancelar");
          waitForUi(page);
          report.put(STEP_MODAL, "PASS");
        } catch (Exception e) {
          report.put(STEP_MODAL, "FAIL - " + e.getMessage());
          blockingStep = STEP_MODAL;
        }
      }

      if (blockingStep == null) {
        try {
          expandMiNegocioMenu(page);
          clickByVisibleText(page, "Administrar Negocios");
          waitForUi(page);

          assertVisible(page.getByText("Información General"), "Información General section should exist.");
          assertVisible(page.getByText("Detalles de la Cuenta"), "Detalles de la Cuenta section should exist.");
          assertVisible(page.getByText("Tus Negocios"), "Tus Negocios section should exist.");
          assertVisible(page.getByText("Sección Legal"), "Sección Legal section should exist.");

          captureScreenshot(page, evidenceDir.resolve("04-administrar-negocios-full.png"), true);
          report.put(STEP_ADMIN, "PASS");
        } catch (Exception e) {
          report.put(STEP_ADMIN, "FAIL - " + e.getMessage());
          blockingStep = STEP_ADMIN;
        }
      }

      if (blockingStep == null) {
        try {
          final Locator infoSection = sectionContaining(page, "Información General");
          final String infoText = infoSection.innerText();
          final Matcher emailMatcher = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE)
              .matcher(infoText);
          assertTrue("User email should be visible in Información General.", emailMatcher.find());
          assertTrue("Specific Google account email should be visible.",
              infoText.contains(GOOGLE_ACCOUNT_EMAIL) || page.getByText(GOOGLE_ACCOUNT_EMAIL).count() > 0);
          assertTrue("A likely user name should be visible in Información General.", containsLikelyName(infoText));
          assertVisible(page.getByText("BUSINESS PLAN"), "BUSINESS PLAN text should be visible.");
          assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cambiar Plan")),
              "Cambiar Plan button should be visible.");
          report.put(STEP_INFO, "PASS");
        } catch (Exception e) {
          report.put(STEP_INFO, "FAIL - " + e.getMessage());
          blockingStep = STEP_INFO;
        }
      }

      if (blockingStep == null) {
        try {
          final Locator detailsSection = sectionContaining(page, "Detalles de la Cuenta");
          final String detailsText = detailsSection.innerText();
          assertTrue("Cuenta creada should be visible.", detailsText.contains("Cuenta creada"));
          assertTrue("Estado activo should be visible.", detailsText.contains("Estado activo"));
          assertTrue("Idioma seleccionado should be visible.", detailsText.contains("Idioma seleccionado"));
          report.put(STEP_ACCOUNT, "PASS");
        } catch (Exception e) {
          report.put(STEP_ACCOUNT, "FAIL - " + e.getMessage());
          blockingStep = STEP_ACCOUNT;
        }
      }

      if (blockingStep == null) {
        try {
          final Locator businessesSection = sectionContaining(page, "Tus Negocios");
          assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Agregar Negocio")),
              "Agregar Negocio button should exist in Tus Negocios.");
          assertVisible(page.getByText("Tienes 2 de 3 negocios"), "Business quota text should be visible.");
          final String businessesText = businessesSection.innerText();
          assertTrue("Business list should be visible.", hasVisibleBusinessList(businessesSection, businessesText));
          report.put(STEP_BUSINESSES, "PASS");
        } catch (Exception e) {
          report.put(STEP_BUSINESSES, "FAIL - " + e.getMessage());
          blockingStep = STEP_BUSINESSES;
        }
      }

      if (blockingStep == null) {
        try {
          termsUrl = validateLegalDocument(page, context, "Términos y Condiciones", "Términos y Condiciones",
              evidenceDir.resolve("05-terminos-y-condiciones.png"));
          report.put(STEP_TERMS, "PASS");
        } catch (Exception e) {
          report.put(STEP_TERMS, "FAIL - " + e.getMessage());
          blockingStep = STEP_TERMS;
        }
      }

      if (blockingStep == null) {
        try {
          privacyUrl = validateLegalDocument(page, context, "Política de Privacidad", "Política de Privacidad",
              evidenceDir.resolve("06-politica-de-privacidad.png"));
          report.put(STEP_PRIVACY, "PASS");
        } catch (Exception e) {
          report.put(STEP_PRIVACY, "FAIL - " + e.getMessage());
          blockingStep = STEP_PRIVACY;
        }
      }

      if (blockingStep != null) {
        markBlockedSteps(report, blockingStep);
      }
    } finally {
      writeReport(report, evidenceDir, termsUrl, privacyUrl);
    }

    final long failedSteps = report.values().stream().filter(value -> value.startsWith("FAIL")).count();
    assertTrue("At least one workflow step failed. Report: " + report, failedSteps == 0);
  }

  private static void executeLoginWithGoogle(final Page page, final BrowserContext context) {
    final Locator signInButton = firstVisibleLocator(
        page.getByRole(AriaRole.BUTTON,
            new Page.GetByRoleOptions().setName(Pattern.compile("(?i)(sign\\s*in|iniciar sesi[oó]n).*(google)|google"))),
        page.getByText(Pattern.compile("(?i)sign in with google|iniciar sesi[oó]n con google")),
        page.locator("button:has-text('Google'), a:has-text('Google')"));

    if (signInButton.count() == 0) {
      throw new IllegalStateException("Could not find login button / 'Sign in with Google'.");
    }

    Page googleOrSamePage;
    try {
      googleOrSamePage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(12000), () -> {
        signInButton.first().click();
      });
      googleOrSamePage.waitForLoadState(LoadState.DOMCONTENTLOADED);
    } catch (TimeoutError timeoutError) {
      signInButton.first().click();
      googleOrSamePage = page;
      waitForUi(page);
    }

    selectGoogleAccountIfPresent(googleOrSamePage);
    waitForUi(page);

    final Locator mainInterfaceSignal = firstVisibleLocator(page.getByText("Negocio"), page.locator("aside"),
        page.getByRole(AriaRole.NAVIGATION));
    assertTrue("Main application interface should be visible after login.", mainInterfaceSignal.count() > 0);
    assertTrue("Left sidebar should be visible after login.", isAnyVisible(page.locator("aside"), page.locator("nav")));
  }

  private static void selectGoogleAccountIfPresent(final Page page) {
    final Locator accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, new Page.GetByTextOptions().setExact(true));
    if (accountOption.count() > 0 && accountOption.first().isVisible()) {
      accountOption.first().click();
      waitForUi(page);
    }
  }

  private static void expandMiNegocioMenu(final Page page) {
    final Locator agregarNegocio = page.getByText("Agregar Negocio", new Page.GetByTextOptions().setExact(true));
    final Locator administrarNegocios = page.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(true));

    if (agregarNegocio.count() > 0 && administrarNegocios.count() > 0 && agregarNegocio.first().isVisible()
        && administrarNegocios.first().isVisible()) {
      return;
    }

    clickByVisibleText(page, "Mi Negocio");
    waitForUi(page);
    assertVisible(agregarNegocio, "'Agregar Negocio' should be visible after expanding Mi Negocio.");
    assertVisible(administrarNegocios, "'Administrar Negocios' should be visible after expanding Mi Negocio.");
  }

  private static String validateLegalDocument(final Page appPage, final BrowserContext context, final String linkText,
      final String headingText, final Path screenshotPath) {
    final Locator link = appPage.getByText(linkText, new Page.GetByTextOptions().setExact(true));
    assertTrue("Legal link '" + linkText + "' should exist.", link.count() > 0);

    Page legalPage;
    boolean openedInNewTab = false;
    try {
      legalPage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(8000), () -> {
        link.first().click();
      });
      legalPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
      openedInNewTab = true;
    } catch (TimeoutError timeoutError) {
      link.first().click();
      legalPage = appPage;
      waitForUi(legalPage);
    }

    assertVisible(legalPage.getByText(headingText), "Heading '" + headingText + "' should be visible.");
    final String pageText = legalPage.locator("body").innerText();
    assertTrue("Legal content text should be visible on " + headingText + ".", pageText.length() > 200);
    captureScreenshot(legalPage, screenshotPath, true);
    final String finalUrl = legalPage.url();

    if (openedInNewTab) {
      legalPage.close();
      appPage.bringToFront();
      waitForUi(appPage);
    } else {
      try {
        appPage.goBack(new Page.GoBackOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        waitForUi(appPage);
      } catch (Exception ignored) {
        // Some environments may open the legal route in-app without history entries.
      }
    }

    return finalUrl;
  }

  private static Locator sectionContaining(final Page page, final String headingText) {
    final Locator heading = page.getByText(headingText, new Page.GetByTextOptions().setExact(true)).first();
    assertVisible(heading, "Expected heading not visible: " + headingText);

    final Locator section = page.locator("section, article, div").filter(new Locator.FilterOptions().setHas(heading)).first();
    if (section.count() == 0) {
      throw new IllegalStateException("Could not resolve containing section for: " + headingText);
    }
    return section;
  }

  private static boolean hasVisibleBusinessList(final Locator section, final String sectionText) {
    final int explicitItems = section.locator("li, [role='row'], tr, .business-item, [data-testid*='business']").count();
    if (explicitItems > 0) {
      return true;
    }
    final String normalized = sectionText.replaceAll("\\s+", " ").trim();
    return normalized.length() > 60;
  }

  private static boolean containsLikelyName(final String text) {
    final String[] lines = text.split("\\R");
    for (String line : lines) {
      final String normalized = line.trim();
      if (normalized.isEmpty()) {
        continue;
      }
      if (normalized.equals("Información General") || normalized.equals("BUSINESS PLAN")
          || normalized.equals("Cambiar Plan") || normalized.contains("@")) {
        continue;
      }
      if (normalized.matches("[\\p{L}]+([\\p{L}\\s\\-']{1,})")) {
        return true;
      }
    }
    return false;
  }

  private static void clickByVisibleText(final Page page, final String text) {
    final Locator candidate = firstVisibleLocator(
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(text).setExact(true)),
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(text).setExact(true)),
        page.getByText(text, new Page.GetByTextOptions().setExact(true)));

    if (candidate.count() == 0) {
      throw new IllegalStateException("Could not click element by visible text: " + text);
    }

    candidate.first().click();
    waitForUi(page);
  }

  private static Locator firstVisibleLocator(final Locator... locators) {
    for (Locator locator : locators) {
      if (locator != null && locator.count() > 0) {
        try {
          locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(5000));
          return locator;
        } catch (Exception ignored) {
          // Try next candidate.
        }
      }
    }
    return locators.length > 0 ? locators[0] : null;
  }

  private static void assertVisible(final Locator locator, final String message) {
    assertTrue(message, locator.count() > 0 && locator.first().isVisible());
  }

  private static boolean isAnyVisible(final Locator... locators) {
    for (Locator locator : locators) {
      if (locator != null && locator.count() > 0 && locator.first().isVisible()) {
        return true;
      }
    }
    return false;
  }

  private static void waitForUi(final Page page) {
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(15000));
    } catch (Exception ignored) {
      page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(15000));
    }
  }

  private static void captureScreenshot(final Page page, final Path path, final boolean fullPage) {
    ensureDirectory(path.getParent());
    page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
  }

  private static Map<String, String> initReport() {
    final LinkedHashMap<String, String> report = new LinkedHashMap<>();
    report.put(STEP_LOGIN, "NOT_RUN");
    report.put(STEP_MENU, "NOT_RUN");
    report.put(STEP_MODAL, "NOT_RUN");
    report.put(STEP_ADMIN, "NOT_RUN");
    report.put(STEP_INFO, "NOT_RUN");
    report.put(STEP_ACCOUNT, "NOT_RUN");
    report.put(STEP_BUSINESSES, "NOT_RUN");
    report.put(STEP_TERMS, "NOT_RUN");
    report.put(STEP_PRIVACY, "NOT_RUN");
    return report;
  }

  private static void markBlockedSteps(final Map<String, String> report, final String blockingStep) {
    boolean afterBlockingStep = false;
    for (Map.Entry<String, String> entry : report.entrySet()) {
      if (entry.getKey().equals(blockingStep)) {
        afterBlockingStep = true;
        continue;
      }
      if (afterBlockingStep && entry.getValue().equals("NOT_RUN")) {
        entry.setValue("FAIL - blocked after " + blockingStep);
      }
    }
  }

  private static void writeReport(final Map<String, String> report, final Path evidenceDir, final String termsUrl,
      final String privacyUrl) throws IOException {
    ensureDirectory(evidenceDir);

    final StringBuilder json = new StringBuilder();
    json.append("{\n");
    json.append("  \"test\": \"saleads_mi_negocio_full_test\",\n");
    json.append("  \"evidence_dir\": \"").append(escapeJson(evidenceDir.toAbsolutePath().toString())).append("\",\n");
    json.append("  \"final_urls\": {\n");
    json.append("    \"terminos_y_condiciones\": \"").append(escapeJson(termsUrl)).append("\",\n");
    json.append("    \"politica_de_privacidad\": \"").append(escapeJson(privacyUrl)).append("\"\n");
    json.append("  },\n");
    json.append("  \"results\": {\n");

    int index = 0;
    for (Map.Entry<String, String> entry : report.entrySet()) {
      json.append("    \"").append(escapeJson(entry.getKey())).append("\": \"")
          .append(escapeJson(entry.getValue())).append("\"");
      if (index < report.size() - 1) {
        json.append(",");
      }
      json.append("\n");
      index++;
    }

    json.append("  }\n");
    json.append("}\n");

    Files.writeString(evidenceDir.resolve("final-report.json"), json.toString(), StandardCharsets.UTF_8);
  }

  private static Path createEvidenceDirectory() {
    final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
    final Path path = Paths.get("target", "saleads-evidence", "saleads_mi_negocio_full_test", timestamp);
    ensureDirectory(path);
    return path;
  }

  private static void ensureDirectory(final Path path) {
    if (path == null) {
      return;
    }
    try {
      Files.createDirectories(path);
    } catch (IOException e) {
      throw new RuntimeException("Could not create directory: " + path, e);
    }
  }

  private static String firstNonBlank(final String... values) {
    for (String value : values) {
      if (!isBlank(value)) {
        return value.trim();
      }
    }
    return null;
  }

  private static boolean isBlank(final String value) {
    return value == null || value.trim().isEmpty();
  }

  private static String escapeJson(final String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

}
