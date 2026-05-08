package io.proleap.cobol.e2e;

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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.Assume;
import org.junit.Test;

public class SaleAdsMiNegocioFullWorkflowTest {

  private static final double DEFAULT_TIMEOUT_MS = 15000;
  private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

  @Test
  public void saleadsMiNegocioFullTest() throws IOException {
    final String saleAdsUrl = readConfig("SALEADS_URL", "saleads.url");
    Assume.assumeTrue(
        "Set SALEADS_URL env var or -Dsaleads.url system property to run the SaleADS E2E workflow.",
        saleAdsUrl != null && !saleAdsUrl.isBlank());

    final boolean headless = Boolean.parseBoolean(readConfigOrDefault("SALEADS_HEADLESS", "true"));
    final Path evidenceDir = createEvidenceDirectory();
    final Map<String, Boolean> report = initReport();
    final Map<String, String> legalUrls = new LinkedHashMap<>();

    try (Playwright playwright = Playwright.create()) {
      final Browser browser =
          playwright
              .chromium()
              .launch(
                  new BrowserType.LaunchOptions()
                      .setHeadless(headless)
                      .setArgs(new String[] {"--start-maximized"}));
      final BrowserContext context = browser.newContext();
      final Page page = context.newPage();
      page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);

      page.navigate(saleAdsUrl);
      waitForUi(page);

      runStep(
          report,
          "Login",
          () -> {
            loginWithGoogle(page, context);
            require(
                visible(page.locator("aside").first()) || visible(page.locator("nav").first()),
                "Left sidebar navigation is not visible after login.");
            require(
                visible(text(page, "Negocio")) || visible(text(page, "Mi Negocio")),
                "Main application interface was not detected after login.");
            screenshot(page, evidenceDir, "01-dashboard-loaded", true);
          });

      runStep(
          report,
          "Mi Negocio menu",
          () -> {
            clickAndWait(page, text(page, "Mi Negocio"), "Mi Negocio");
            require(visible(text(page, "Agregar Negocio")), "'Agregar Negocio' is not visible.");
            require(
                visible(text(page, "Administrar Negocios")),
                "'Administrar Negocios' is not visible.");
            screenshot(page, evidenceDir, "02-mi-negocio-expanded", true);
          });

      runStep(
          report,
          "Agregar Negocio modal",
          () -> {
            clickAndWait(page, text(page, "Agregar Negocio"), "Agregar Negocio");
            final Locator modal = page.locator("[role='dialog']").first();
            require(visible(modal), "Expected 'Agregar Negocio' modal was not opened.");
            require(visible(text(modal, "Crear Nuevo Negocio")), "Missing modal title.");
            require(visible(text(modal, "Nombre del Negocio")), "Missing 'Nombre del Negocio' field.");
            require(visible(text(modal, "Tienes 2 de 3 negocios")), "Missing business quota text.");
            require(visible(text(modal, "Cancelar")), "Missing 'Cancelar' button.");
            require(visible(text(modal, "Crear Negocio")), "Missing 'Crear Negocio' button.");
            screenshot(page, evidenceDir, "03-agregar-negocio-modal", true);

            final Locator nameInput = modal.locator("input").first();
            if (visible(nameInput)) {
              nameInput.click();
              nameInput.fill("Negocio Prueba Automatizacion");
            }
            clickAndWait(page, text(modal, "Cancelar"), "Cancelar");
          });

      runStep(
          report,
          "Administrar Negocios view",
          () -> {
            if (!visible(text(page, "Administrar Negocios"))) {
              clickAndWait(page, text(page, "Mi Negocio"), "Mi Negocio");
            }
            clickAndWait(page, text(page, "Administrar Negocios"), "Administrar Negocios");
            require(visible(text(page, "Informacion General")) || visible(text(page, "Información General")),
                "Missing section 'Informacion General'.");
            require(
                visible(text(page, "Detalles de la Cuenta")),
                "Missing section 'Detalles de la Cuenta'.");
            require(visible(text(page, "Tus Negocios")), "Missing section 'Tus Negocios'.");
            require(
                visible(text(page, "Seccion Legal")) || visible(text(page, "Sección Legal")),
                "Missing section 'Seccion Legal'.");
            screenshot(page, evidenceDir, "04-administrar-negocios-page", true);
          });

      runStep(
          report,
          "Información General",
          () -> {
            final Locator section = section(page, "Informacion General", "Información General");
            require(visible(section), "Informacion General section is not visible.");

            final String expectedName = readConfig("SALEADS_USER_NAME", "saleads.user.name");
            final boolean userNameVisible =
                (expectedName != null && !expectedName.isBlank() && visible(text(section, expectedName)))
                    || visible(text(section, "Nombre"))
                    || visible(text(section, "Usuario"));
            require(userNameVisible, "User name was not detected in Informacion General.");

            final boolean emailVisible =
                visible(text(section, GOOGLE_ACCOUNT_EMAIL))
                    || visible(section.locator("xpath=.//*[contains(text(),'@')]").first());
            require(emailVisible, "User email was not detected in Informacion General.");
            require(visible(text(section, "BUSINESS PLAN")), "Missing text 'BUSINESS PLAN'.");
            require(visible(text(section, "Cambiar Plan")), "Missing button 'Cambiar Plan'.");
          });

      runStep(
          report,
          "Detalles de la Cuenta",
          () -> {
            final Locator section = section(page, "Detalles de la Cuenta");
            require(visible(section), "Detalles de la Cuenta section is not visible.");
            require(visible(text(section, "Cuenta creada")), "Missing 'Cuenta creada'.");
            require(visible(text(section, "Estado activo")), "Missing 'Estado activo'.");
            require(visible(text(section, "Idioma seleccionado")), "Missing 'Idioma seleccionado'.");
          });

      runStep(
          report,
          "Tus Negocios",
          () -> {
            final Locator section = section(page, "Tus Negocios");
            require(visible(section), "Tus Negocios section is not visible.");
            require(
                section.locator("li, tr, [class*='negocio'], [class*='business']").count() > 0
                    || visible(text(section, "Negocio")),
                "Business list was not detected.");
            require(visible(text(section, "Agregar Negocio")), "Missing 'Agregar Negocio' button.");
            require(visible(text(section, "Tienes 2 de 3 negocios")), "Missing business quota text.");
          });

      runStep(
          report,
          "Términos y Condiciones",
          () -> {
            final Page legalPage = openLegalPage(context, page, "Terminos y Condiciones", "Términos y Condiciones");
            require(
                visible(text(legalPage, "Terminos y Condiciones"))
                    || visible(text(legalPage, "Términos y Condiciones")),
                "Heading 'Terminos y Condiciones' was not found.");
            require(
                legalPage.locator("body").innerText().trim().length() > 120,
                "Legal terms content looks too short or missing.");
            screenshot(legalPage, evidenceDir, "08-terminos-y-condiciones", true);
            legalUrls.put("Términos y Condiciones", legalPage.url());
            returnToApplication(page, legalPage);
          });

      runStep(
          report,
          "Política de Privacidad",
          () -> {
            final Page legalPage = openLegalPage(context, page, "Politica de Privacidad", "Política de Privacidad");
            require(
                visible(text(legalPage, "Politica de Privacidad"))
                    || visible(text(legalPage, "Política de Privacidad")),
                "Heading 'Politica de Privacidad' was not found.");
            require(
                legalPage.locator("body").innerText().trim().length() > 120,
                "Privacy legal content looks too short or missing.");
            screenshot(legalPage, evidenceDir, "09-politica-de-privacidad", true);
            legalUrls.put("Política de Privacidad", legalPage.url());
            returnToApplication(page, legalPage);
          });
    }

    final String finalReport = buildFinalReport(report, legalUrls, evidenceDir);
    System.out.println(finalReport);
    assertTrue("One or more SaleADS workflow validations failed.\n" + finalReport, allPassed(report));
  }

  private static void loginWithGoogle(final Page page, final BrowserContext context) {
    final Locator googleButton =
        firstVisible(
            page.getByRole(
                    AriaRole.BUTTON,
                    new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*google.*")))
                .first(),
            page.getByRole(
                    AriaRole.LINK,
                    new Page.GetByRoleOptions().setName(Pattern.compile("(?i).*google.*")))
                .first(),
            text(page, "Sign in with Google"),
            text(page, "Iniciar sesión con Google"),
            text(page, "Google"));

    require(googleButton != null, "Could not find Google login button.");

    Page googlePage = null;
    try {
      googlePage =
          context.waitForPage(
              () -> googleButton.click(),
              new BrowserContext.WaitForPageOptions().setTimeout(DEFAULT_TIMEOUT_MS));
    } catch (PlaywrightException ignored) {
      waitForUi(page);
    }

    if (googlePage != null) {
      googlePage.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
      waitForUi(googlePage);
      final Locator accountEmail = text(googlePage, GOOGLE_ACCOUNT_EMAIL);
      if (visible(accountEmail)) {
        accountEmail.click();
      }
      try {
        googlePage.waitForClose(new Page.WaitForCloseOptions().setTimeout(10000));
      } catch (PlaywrightException ignored) {
        waitForUi(googlePage);
      }
    } else {
      final Locator accountEmail = text(page, GOOGLE_ACCOUNT_EMAIL);
      if (visible(accountEmail)) {
        clickAndWait(page, accountEmail, GOOGLE_ACCOUNT_EMAIL);
      }
    }

    waitForUi(page);
  }

  private static Page openLegalPage(
      final BrowserContext context, final Page appPage, final String... textCandidates) {
    final Locator link = firstVisible(text(appPage, textCandidates[0]), text(appPage, textCandidates[1]));
    require(link != null, "Could not find legal link: " + textCandidates[0]);

    Page targetPage = null;
    try {
      targetPage =
          context.waitForPage(
              () -> link.click(),
              new BrowserContext.WaitForPageOptions().setTimeout(8000));
    } catch (PlaywrightException ignored) {
      waitForUi(appPage);
      targetPage = appPage;
    }

    waitForUi(targetPage);
    return targetPage;
  }

  private static void returnToApplication(final Page appPage, final Page currentPage) {
    if (currentPage == appPage) {
      try {
        appPage.goBack(new Page.GoBackOptions().setTimeout(10000));
      } catch (PlaywrightException ignored) {
      }
      waitForUi(appPage);
      return;
    }

    try {
      currentPage.close();
    } catch (PlaywrightException ignored) {
    }
    appPage.bringToFront();
    waitForUi(appPage);
  }

  private static void clickAndWait(final Page page, final Locator locator, final String elementName) {
    require(locator != null && visible(locator), "Could not find clickable element: " + elementName);
    locator.click();
    waitForUi(page);
  }

  private static Locator section(final Page page, final String... titleCandidates) {
    for (String title : titleCandidates) {
      final Locator heading = text(page, title);
      if (visible(heading)) {
        final Locator container =
            heading.locator("xpath=ancestor::*[self::section or self::div][1]").first();
        if (visible(container)) {
          return container;
        }
      }
    }
    return page.locator("#missing-section").first();
  }

  private static Locator text(final Page page, final String value) {
    return page.getByText(Pattern.compile(Pattern.quote(value), Pattern.CASE_INSENSITIVE)).first();
  }

  private static Locator text(final Locator locator, final String value) {
    return locator.getByText(Pattern.compile(Pattern.quote(value), Pattern.CASE_INSENSITIVE)).first();
  }

  private static Locator firstVisible(final Locator... locators) {
    for (Locator locator : locators) {
      if (locator != null && visible(locator)) {
        return locator;
      }
    }
    return null;
  }

  private static boolean visible(final Locator locator) {
    if (locator == null) {
      return false;
    }
    try {
      return locator.isVisible(new Locator.IsVisibleOptions().setTimeout(2000));
    } catch (PlaywrightException ignored) {
      return false;
    }
  }

  private static void waitForUi(final Page page) {
    page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
    } catch (PlaywrightException ignored) {
      // Some SPAs keep background requests alive permanently, so NETWORKIDLE can legitimately time out.
    }
  }

  private static void screenshot(
      final Page page, final Path evidenceDir, final String filename, final boolean fullPage) {
    final Path output = evidenceDir.resolve(filename + ".png");
    page.screenshot(new Page.ScreenshotOptions().setPath(output).setFullPage(fullPage));
  }

  private static Path createEvidenceDirectory() throws IOException {
    final String runId = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
    final Path path = Paths.get("target", "saleads-evidence", runId);
    Files.createDirectories(path);
    return path;
  }

  private static Map<String, Boolean> initReport() {
    final Map<String, Boolean> report = new LinkedHashMap<>();
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

  private static void runStep(
      final Map<String, Boolean> report, final String key, final ThrowingRunnable action) {
    try {
      action.run();
      report.put(key, true);
    } catch (Throwable t) {
      report.put(key, false);
      System.err.println("Step failed [" + key + "]: " + t.getMessage());
    }
  }

  private static void require(final boolean condition, final String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  private static boolean allPassed(final Map<String, Boolean> report) {
    for (boolean value : report.values()) {
      if (!value) {
        return false;
      }
    }
    return true;
  }

  private static String buildFinalReport(
      final Map<String, Boolean> report, final Map<String, String> legalUrls, final Path evidenceDir) {
    final StringBuilder sb = new StringBuilder();
    sb.append("\n========== SaleADS Mi Negocio Final Report ==========\n");
    report.forEach((k, v) -> sb.append(" - ").append(k).append(": ").append(v ? "PASS" : "FAIL").append("\n"));
    legalUrls.forEach((k, v) -> sb.append(" - ").append(k).append(" URL: ").append(v).append("\n"));
    sb.append(" - Evidence directory: ").append(evidenceDir.toAbsolutePath()).append("\n");
    sb.append("=====================================================\n");
    return sb.toString();
  }

  private static String readConfig(final String envVar, final String systemProperty) {
    final String envValue = System.getenv(envVar);
    if (envValue != null && !envValue.isBlank()) {
      return envValue;
    }
    return System.getProperty(systemProperty);
  }

  private static String readConfigOrDefault(final String envVar, final String defaultValue) {
    final String value = System.getenv(envVar);
    return value == null || value.isBlank() ? defaultValue : value;
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }
}
