package io.proleap.saleads;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import org.junit.Assume;
import org.junit.Test;

/**
 * End-to-end validation for SaleADS Mi Negocio module workflow.
 *
 * Required environment variables:
 * - SALEADS_RUN_E2E=true
 * - SALEADS_LOGIN_URL=https://<current-environment-login-page>
 *
 * Optional environment variables:
 * - SALEADS_GOOGLE_EMAIL (default: juanlucasbarbiergarzon@gmail.com)
 * - SALEADS_EXPECTED_USER_NAME
 * - SALEADS_HEADLESS (default: true)
 */
public class SaleadsMiNegocioFullTest {

  private static final String STEP_LOGIN = "Login";
  private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
  private static final String STEP_AGREGAR_MODAL = "Agregar Negocio modal";
  private static final String STEP_ADMIN_VIEW = "Administrar Negocios view";
  private static final String STEP_INFO_GENERAL = "Información General";
  private static final String STEP_DETALLES_CUENTA = "Detalles de la Cuenta";
  private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
  private static final String STEP_TERMINOS = "Términos y Condiciones";
  private static final String STEP_PRIVACIDAD = "Política de Privacidad";

  private final Map<String, String> finalReport = new LinkedHashMap<>();
  private final Map<String, String> legalUrls = new LinkedHashMap<>();
  private final List<String> failureDetails = new ArrayList<>();

  @Test
  public void saleadsMiNegocioFullTest() throws IOException {
    final boolean runE2E = Boolean.parseBoolean(env("SALEADS_RUN_E2E", "false"));
    Assume.assumeTrue("Skipping SaleADS E2E test. Set SALEADS_RUN_E2E=true to run it.", runE2E);

    final String loginUrl = env("SALEADS_LOGIN_URL", "");
    Assume.assumeTrue("SALEADS_LOGIN_URL is required for this E2E test.", !loginUrl.trim().isEmpty());

    final String email = env("SALEADS_GOOGLE_EMAIL", "juanlucasbarbiergarzon@gmail.com");
    final String expectedUserName = env("SALEADS_EXPECTED_USER_NAME", "").trim();
    final boolean headless = Boolean.parseBoolean(env("SALEADS_HEADLESS", "true"));

    final Path evidenceDir = Files.createDirectories(Paths.get(
        "target",
        "saleads-evidence",
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))));

    System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());

    try (Playwright playwright = Playwright.create()) {
      Browser browser = playwright.chromium().launch(new Browser.LaunchOptions().setHeadless(headless));
      BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1920, 1080));
      Page page = context.newPage();

      page.navigate(loginUrl);
      waitForUiLoad(page);

      stepLoginWithGoogle(page, context, evidenceDir, email);
      stepOpenMiNegocioMenu(page, evidenceDir);
      stepValidateAgregarNegocioModal(page, evidenceDir);
      stepOpenAdministrarNegocios(page, evidenceDir);
      stepValidateInformacionGeneral(page, email, expectedUserName);
      stepValidateDetallesCuenta(page);
      stepValidateTusNegocios(page);
      stepValidateLegalLink(page, context, evidenceDir, "Términos y Condiciones", "Términos y Condiciones",
          STEP_TERMINOS);
      stepValidateLegalLink(page, context, evidenceDir, "Política de Privacidad", "Política de Privacidad",
          STEP_PRIVACIDAD);
    }

    printFinalReport();
    assertTrue("One or more validations failed:\n" + String.join("\n", failureDetails), failureDetails.isEmpty());
  }

  private void stepLoginWithGoogle(
      final Page page,
      final BrowserContext context,
      final Path evidenceDir,
      final String email) {
    runStep(STEP_LOGIN, () -> {
      clickAnyVisibleText(page, Arrays.asList(
          "Sign in with Google",
          "Iniciar sesión con Google",
          "Inicia sesión con Google",
          "Continuar con Google",
          "Acceder con Google",
          "Google"));

      waitForUiLoad(page);
      selectGoogleAccountIfPrompted(context, page, email);
      waitForUiLoad(page);

      assertVisibleTextAny(page, Arrays.asList("Negocio", "Mi Negocio", "Dashboard", "Inicio"), 60_000);
      assertTrue("Left sidebar navigation is not visible.", isSidebarVisible(page));
      takeScreenshot(page, evidenceDir, "01_dashboard_loaded.png", false);
    });
  }

  private void stepOpenMiNegocioMenu(final Page page, final Path evidenceDir) {
    runStep(STEP_MI_NEGOCIO_MENU, () -> {
      clickIfVisible(page, "Negocio");
      clickAnyVisibleText(page, Arrays.asList("Mi Negocio"));
      waitForUiLoad(page);

      assertVisibleText(page, "Agregar Negocio", 15_000);
      assertVisibleText(page, "Administrar Negocios", 15_000);
      takeScreenshot(page, evidenceDir, "02_mi_negocio_menu_expanded.png", false);
    });
  }

  private void stepValidateAgregarNegocioModal(final Page page, final Path evidenceDir) {
    runStep(STEP_AGREGAR_MODAL, () -> {
      clickAnyVisibleText(page, Arrays.asList("Agregar Negocio"));
      waitForUiLoad(page);

      assertVisibleText(page, "Crear Nuevo Negocio", 15_000);
      assertVisibleText(page, "Nombre del Negocio", 15_000);
      assertVisibleText(page, "Tienes 2 de 3 negocios", 15_000);
      assertVisibleText(page, "Cancelar", 15_000);
      assertVisibleText(page, "Crear Negocio", 15_000);
      takeScreenshot(page, evidenceDir, "03_agregar_negocio_modal.png", false);

      // Optional actions for modal interaction. They should not fail the step if unavailable.
      try {
        Locator nombreInput = firstVisibleLocator(page, Arrays.asList(
            page.getByLabel("Nombre del Negocio"),
            page.getByPlaceholder("Nombre del Negocio"),
            page.locator("input[name*='nombre' i], input[id*='nombre' i]")));
        nombreInput.fill("Negocio Prueba Automatizacion");
      } catch (Throwable ignored) {
      }

      clickAnyVisibleText(page, Arrays.asList("Cancelar"));
      waitForUiLoad(page);
    });
  }

  private void stepOpenAdministrarNegocios(final Page page, final Path evidenceDir) {
    runStep(STEP_ADMIN_VIEW, () -> {
      if (!isVisibleText(page, "Administrar Negocios", 3_000)) {
        clickIfVisible(page, "Mi Negocio");
        clickIfVisible(page, "Negocio");
      }

      clickAnyVisibleText(page, Arrays.asList("Administrar Negocios"));
      waitForUiLoad(page);

      assertVisibleText(page, "Información General", 20_000);
      assertVisibleText(page, "Detalles de la Cuenta", 20_000);
      assertVisibleText(page, "Tus Negocios", 20_000);
      assertVisibleText(page, "Sección Legal", 20_000);
      takeScreenshot(page, evidenceDir, "04_administrar_negocios_full.png", true);
    });
  }

  private void stepValidateInformacionGeneral(
      final Page page,
      final String email,
      final String expectedUserName) {
    runStep(STEP_INFO_GENERAL, () -> {
      assertVisibleText(page, "Información General", 15_000);

      if (isVisibleText(page, email, 4_000)) {
        assertVisibleText(page, email, 4_000);
      } else {
        assertVisibleRegex(page, "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", 4_000);
      }

      if (!expectedUserName.isEmpty()) {
        assertVisibleText(page, expectedUserName, 4_000);
      } else {
        assertTrue("User name was not detected in Informacion General.", isLikelyUserNameVisible(page));
      }

      assertVisibleText(page, "BUSINESS PLAN", 10_000);
      assertVisibleText(page, "Cambiar Plan", 10_000);
    });
  }

  private void stepValidateDetallesCuenta(final Page page) {
    runStep(STEP_DETALLES_CUENTA, () -> {
      assertVisibleText(page, "Cuenta creada", 10_000);
      assertVisibleText(page, "Estado activo", 10_000);
      assertVisibleText(page, "Idioma seleccionado", 10_000);
    });
  }

  private void stepValidateTusNegocios(final Page page) {
    runStep(STEP_TUS_NEGOCIOS, () -> {
      assertVisibleText(page, "Tus Negocios", 10_000);
      assertVisibleText(page, "Agregar Negocio", 10_000);
      assertVisibleText(page, "Tienes 2 de 3 negocios", 10_000);
      assertTrue("Business list is not visible.", isBusinessListVisible(page));
    });
  }

  private void stepValidateLegalLink(
      final Page page,
      final BrowserContext context,
      final Path evidenceDir,
      final String linkText,
      final String headingText,
      final String stepName) {
    runStep(stepName, () -> {
      Page legalPage = clickAndResolveLegalPage(page, context, linkText);
      waitForUiLoad(legalPage);

      assertVisibleText(legalPage, headingText, 20_000);
      String bodyText = legalPage.locator("body").innerText();
      assertTrue("Legal content is empty for " + linkText, bodyText != null && bodyText.trim().length() > 120);

      final String fileName = "08_09_" + sanitize(linkText) + ".png";
      takeScreenshot(legalPage, evidenceDir, fileName, true);
      String finalUrl = legalPage.url();
      legalUrls.put(stepName, finalUrl);
      System.out.println(linkText + " URL: " + finalUrl);
      appendEvidenceLine(evidenceDir.resolve("legal_urls.txt"), linkText + ": " + finalUrl);

      if (legalPage != page) {
        legalPage.close();
        page.bringToFront();
        waitForUiLoad(page);
      } else {
        legalPage.goBack();
        waitForUiLoad(legalPage);
      }
    });
  }

  private Page clickAndResolveLegalPage(final Page appPage, final BrowserContext context, final String linkText)
      throws InterruptedException {
    int pagesBefore = context.pages().size();
    clickAnyVisibleText(appPage, Arrays.asList(linkText));
    waitForUiLoad(appPage);

    long deadline = System.currentTimeMillis() + 15_000;
    while (System.currentTimeMillis() < deadline) {
      if (context.pages().size() > pagesBefore) {
        for (Page candidate : context.pages()) {
          if (candidate != appPage && !candidate.isClosed()) {
            candidate.bringToFront();
            return candidate;
          }
        }
      }
      Thread.sleep(250);
    }

    return appPage;
  }

  private void selectGoogleAccountIfPrompted(final BrowserContext context, final Page originalPage, final String email)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + 45_000;
    while (System.currentTimeMillis() < deadline) {
      for (Page candidate : context.pages()) {
        if (candidate.isClosed()) {
          continue;
        }

        String url = candidate.url();
        boolean isGoogleFlow = url.contains("accounts.google.com")
            || isVisibleText(candidate, "Elige una cuenta", 500)
            || isVisibleText(candidate, "Choose an account", 500);

        if (!isGoogleFlow) {
          continue;
        }

        clickIfVisible(candidate, email);
        clickIfVisible(candidate, "juanlucasbarbiergarzon@gmail.com");
        waitForUiLoad(candidate);
        originalPage.bringToFront();
        return;
      }
      Thread.sleep(300);
    }
  }

  private void runStep(final String step, final CheckedRunnable action) {
    try {
      action.run();
      finalReport.put(step, "PASS");
    } catch (Throwable throwable) {
      finalReport.put(step, "FAIL");
      failureDetails.add(step + ": " + throwable.getMessage());
    }
  }

  private void printFinalReport() {
    System.out.println("==== SaleADS Mi Negocio - Final Report ====");
    for (String step : Arrays.asList(
        STEP_LOGIN,
        STEP_MI_NEGOCIO_MENU,
        STEP_AGREGAR_MODAL,
        STEP_ADMIN_VIEW,
        STEP_INFO_GENERAL,
        STEP_DETALLES_CUENTA,
        STEP_TUS_NEGOCIOS,
        STEP_TERMINOS,
        STEP_PRIVACIDAD)) {
      String status = finalReport.getOrDefault(step, "FAIL");
      System.out.println(step + ": " + status);
    }

    if (!legalUrls.isEmpty()) {
      System.out.println("---- Final legal URLs ----");
      for (Map.Entry<String, String> entry : legalUrls.entrySet()) {
        System.out.println(entry.getKey() + ": " + entry.getValue());
      }
    }

    if (!failureDetails.isEmpty()) {
      System.out.println("---- Failure details ----");
      for (String detail : failureDetails) {
        System.out.println(detail);
      }
    }
  }

  private void clickAnyVisibleText(final Page page, final List<String> texts) {
    for (String text : texts) {
      Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(false)).first();
      try {
        if (locator.count() > 0 && locator.isVisible()) {
          locator.click();
          waitForUiLoad(page);
          return;
        }
      } catch (PlaywrightException ignored) {
      }
    }
    throw new AssertionError("Could not click any visible element for texts: " + texts);
  }

  private void clickIfVisible(final Page page, final String text) {
    try {
      Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(false)).first();
      if (locator.count() > 0 && locator.isVisible()) {
        locator.click();
        waitForUiLoad(page);
      }
    } catch (PlaywrightException ignored) {
    }
  }

  private boolean isVisibleText(final Page page, final String text, final double timeoutMs) {
    long deadline = System.currentTimeMillis() + (long) timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      try {
        Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(false)).first();
        if (locator.count() > 0 && locator.isVisible()) {
          return true;
        }
      } catch (PlaywrightException ignored) {
      }

      try {
        Thread.sleep(200);
      } catch (InterruptedException ignored) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return false;
  }

  private void assertVisibleText(final Page page, final String text, final double timeoutMs) {
    assertTrue("Expected visible text not found: " + text, isVisibleText(page, text, timeoutMs));
  }

  private void assertVisibleTextAny(final Page page, final List<String> texts, final double timeoutMs)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + (long) timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      for (String text : texts) {
        if (isVisibleText(page, text, 500)) {
          return;
        }
      }
      Thread.sleep(250);
    }
    throw new AssertionError("None of the expected texts are visible: " + texts);
  }

  private void assertVisibleRegex(final Page page, final String regex, final double timeoutMs) throws InterruptedException {
    Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    long deadline = System.currentTimeMillis() + (long) timeoutMs;
    while (System.currentTimeMillis() < deadline) {
      String body = page.locator("body").innerText();
      if (body != null && pattern.matcher(body).find()) {
        return;
      }
      Thread.sleep(200);
    }
    throw new AssertionError("Expected regex not found in page: " + regex);
  }

  private Locator firstVisibleLocator(final Page page, final List<Locator> locators) {
    for (Locator locator : locators) {
      try {
        if (locator.count() > 0 && locator.first().isVisible()) {
          return locator.first();
        }
      } catch (PlaywrightException ignored) {
      }
    }
    throw new AssertionError("Expected at least one visible input locator.");
  }

  private boolean isSidebarVisible(final Page page) {
    try {
      if (page.locator("aside").first().isVisible()) {
        return true;
      }
    } catch (PlaywrightException ignored) {
    }

    try {
      return page.locator("nav").first().isVisible();
    } catch (PlaywrightException ignored) {
      return false;
    }
  }

  private boolean isLikelyUserNameVisible(final Page page) {
    String body = page.locator("body").innerText();
    if (body == null || body.isEmpty()) {
      return false;
    }

    String normalized = body.toLowerCase();
    return normalized.contains("juan")
        || normalized.contains("lucas")
        || normalized.contains("barbier")
        || normalized.contains("garzon");
  }

  private boolean isBusinessListVisible(final Page page) {
    return page.locator("[role='list'] [role='listitem']").count() > 0
        || page.locator("ul li").count() > 0
        || page.locator("table tbody tr").count() > 0
        || page.locator("[class*='business' i]").count() > 0
        || page.locator("[data-testid*='business' i]").count() > 0;
  }

  private void takeScreenshot(final Page page, final Path evidenceDir, final String fileName, final boolean fullPage) {
    page.screenshot(new Page.ScreenshotOptions()
        .setPath(evidenceDir.resolve(fileName))
        .setFullPage(fullPage));
  }

  private void waitForUiLoad(final Page page) {
    try {
      page.waitForLoadState(LoadState.DOMCONTENTLOADED);
    } catch (PlaywrightException ignored) {
    }

    try {
      page.waitForLoadState(LoadState.NETWORKIDLE);
    } catch (PlaywrightException ignored) {
    }
  }

  private String sanitize(final String value) {
    return value.toLowerCase()
        .replace(" ", "_")
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace("ñ", "n");
  }

  private String env(final String key, final String fallback) {
    String value = System.getenv(key);
    return value == null ? fallback : value;
  }

  private void appendEvidenceLine(final Path filePath, final String line) throws IOException {
    Files.writeString(
        filePath,
        line + System.lineSeparator(),
        StandardOpenOption.CREATE,
        StandardOpenOption.APPEND);
  }

  @FunctionalInterface
  private interface CheckedRunnable {
    void run() throws Exception;
  }
}
