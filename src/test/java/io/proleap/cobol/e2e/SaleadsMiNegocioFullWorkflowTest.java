package io.proleap.cobol.e2e;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class SaleadsMiNegocioFullWorkflowTest {

  private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
  private static final Pattern GOOGLE_SIGNIN_PATTERN = Pattern.compile("(?i)(continuar|ingresar|iniciar sesi[oó]n|sign in|login).*(google)|google");
  private static final Pattern NEGOCIO_COUNTER_PATTERN = Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios");

  @Test
  public void saleadsMiNegocioFullWorkflow() throws Exception {
    final boolean enabled = Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled",
        System.getenv().getOrDefault("SALEADS_E2E_ENABLED", "false")));
    Assume.assumeTrue(
        "Skipping SaleADS E2E test. Enable with -Dsaleads.e2e.enabled=true or SALEADS_E2E_ENABLED=true.",
        enabled);

    final String initialUrl = firstNonBlank(
        System.getProperty("saleads.url"),
        System.getenv("SALEADS_URL"),
        System.getProperty("saleads.login.url"),
        System.getenv("SALEADS_LOGIN_URL"));

    final String googleAccount = firstNonBlank(
        System.getProperty("saleads.google.account"),
        System.getenv("SALEADS_GOOGLE_ACCOUNT"),
        "juanlucasbarbiergarzon@gmail.com");

    final Path screenshotDir = createScreenshotDir();
    final Map<String, Boolean> report = new LinkedHashMap<>();
    final Map<String, String> evidence = new LinkedHashMap<>();

    try (Playwright playwright = Playwright.create()) {
      final boolean headed = Boolean.parseBoolean(
          System.getProperty("saleads.headed", System.getenv().getOrDefault("SALEADS_HEADED", "false")));
      final Browser browser = playwright.chromium()
          .launch(new BrowserType.LaunchOptions().setHeadless(!headed));
      final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
      final Page appPage = context.newPage();

      if (initialUrl != null) {
        appPage.navigate(initialUrl);
        waitForUi(appPage);
      }

      final boolean loginPass = stepLoginWithGoogle(appPage, context, googleAccount, screenshotDir, evidence);
      report.put("Login", loginPass);

      final boolean miNegocioMenuPass = stepOpenMiNegocioMenu(appPage, screenshotDir, evidence);
      report.put("Mi Negocio menu", miNegocioMenuPass);

      final boolean agregarNegocioModalPass = stepValidateAgregarNegocioModal(appPage, screenshotDir, evidence);
      report.put("Agregar Negocio modal", agregarNegocioModalPass);

      final boolean administrarNegociosPass = stepOpenAdministrarNegocios(appPage, screenshotDir, evidence);
      report.put("Administrar Negocios view", administrarNegociosPass);

      final boolean informacionGeneralPass = stepValidateInformacionGeneral(appPage);
      report.put("Información General", informacionGeneralPass);

      final boolean detallesCuentaPass = stepValidateDetallesCuenta(appPage);
      report.put("Detalles de la Cuenta", detallesCuentaPass);

      final boolean tusNegociosPass = stepValidateTusNegocios(appPage);
      report.put("Tus Negocios", tusNegociosPass);

      final String appUrlBeforeLegal = appPage.url();
      final boolean terminosPass = stepValidateLegalLink(
          "Términos y Condiciones",
          Pattern.compile("(?i)t[eé]rminos\\s+y\\s+condiciones"),
          "08_terminos_condiciones",
          appPage,
          context,
          appUrlBeforeLegal,
          screenshotDir,
          evidence);
      report.put("Términos y Condiciones", terminosPass);

      final boolean politicaPass = stepValidateLegalLink(
          "Política de Privacidad",
          Pattern.compile("(?i)pol[ií]tica\\s+de\\s+privacidad"),
          "09_politica_privacidad",
          appPage,
          context,
          appUrlBeforeLegal,
          screenshotDir,
          evidence);
      report.put("Política de Privacidad", politicaPass);

      printFinalReport(report, evidence, screenshotDir);

      final StringBuilder failedSteps = new StringBuilder();
      for (Map.Entry<String, Boolean> entry : report.entrySet()) {
        if (!entry.getValue()) {
          if (failedSteps.length() > 0) {
            failedSteps.append(", ");
          }
          failedSteps.append(entry.getKey());
        }
      }

      Assert.assertTrue("SaleADS Mi Negocio workflow failures: " + failedSteps, failedSteps.length() == 0);
    }
  }

  private boolean stepLoginWithGoogle(
      final Page appPage,
      final BrowserContext context,
      final String googleAccount,
      final Path screenshotDir,
      final Map<String, String> evidence) {
    try {
      Page googlePage = appPage;
      try {
        googlePage = context.waitForPage(
            () -> clickAnyVisibleText(appPage,
                new String[] {"Sign in with Google", "Continuar con Google", "Ingresar con Google", "Google"},
                GOOGLE_SIGNIN_PATTERN),
            new BrowserContext.WaitForPageOptions().setTimeout(7000));
        waitForUi(googlePage);
      } catch (PlaywrightException ignored) {
        // No popup opened, continue on current page.
        clickAnyVisibleText(appPage,
            new String[] {"Sign in with Google", "Continuar con Google", "Ingresar con Google", "Google"},
            GOOGLE_SIGNIN_PATTERN);
        waitForUi(appPage);
      }

      final Locator accountLocator = googlePage.getByText(Pattern.compile(Pattern.quote(googleAccount)));
      if (isVisible(accountLocator)) {
        accountLocator.first().click();
        waitForUi(googlePage);
      }

      final boolean appInterfaceVisible = isVisible(appPage.locator("main, [role='main'], body"));
      final boolean sidebarVisible =
          isVisible(appPage.getByText(Pattern.compile("(?i)negocio")).first())
              || isVisible(appPage.locator("aside, nav").first());

      final Path dashboardScreenshot = screenshot(appPage, screenshotDir, "01_dashboard_loaded", false);
      evidence.put("dashboard_screenshot", dashboardScreenshot.toString());
      return appInterfaceVisible && sidebarVisible;
    } catch (Throwable t) {
      safeFailureScreenshot(appPage, screenshotDir, "01_login_failed", evidence);
      return false;
    }
  }

  private boolean stepOpenMiNegocioMenu(final Page appPage, final Path screenshotDir, final Map<String, String> evidence) {
    try {
      clickAnyVisibleText(appPage, new String[] {"Mi Negocio"}, Pattern.compile("(?i)mi\\s+negocio"));
      waitForUi(appPage);

      final boolean agregarVisible = isVisible(appPage.getByText(Pattern.compile("(?i)agregar\\s+negocio")).first());
      final boolean administrarVisible = isVisible(appPage.getByText(Pattern.compile("(?i)administrar\\s+negocios")).first());

      final Path expandedMenuScreenshot = screenshot(appPage, screenshotDir, "02_mi_negocio_menu_expanded", false);
      evidence.put("mi_negocio_menu_screenshot", expandedMenuScreenshot.toString());
      return agregarVisible && administrarVisible;
    } catch (Throwable t) {
      safeFailureScreenshot(appPage, screenshotDir, "02_mi_negocio_menu_failed", evidence);
      return false;
    }
  }

  private boolean stepValidateAgregarNegocioModal(
      final Page appPage,
      final Path screenshotDir,
      final Map<String, String> evidence) {
    try {
      clickVisibleText(appPage, Pattern.compile("(?i)agregar\\s+negocio"));
      waitForUi(appPage);

      final Locator modalTitle = appPage.getByText(Pattern.compile("(?i)crear\\s+nuevo\\s+negocio"));
      final Locator businessNameField = appPage.getByLabel(Pattern.compile("(?i)nombre\\s+del\\s+negocio"));
      final Locator negociosCounter = appPage.getByText(NEGOCIO_COUNTER_PATTERN);
      final Locator cancelButton = appPage.getByRole(AriaRole.BUTTON,
          new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cancelar")));
      final Locator createButton = appPage.getByRole(AriaRole.BUTTON,
          new Page.GetByRoleOptions().setName(Pattern.compile("(?i)crear\\s+negocio")));

      final boolean modalValid =
          isVisible(modalTitle)
              && isVisible(businessNameField)
              && isVisible(negociosCounter)
              && isVisible(cancelButton)
              && isVisible(createButton);

      final Path modalScreenshot = screenshot(appPage, screenshotDir, "03_agregar_negocio_modal", false);
      evidence.put("agregar_negocio_modal_screenshot", modalScreenshot.toString());

      if (isVisible(businessNameField)) {
        businessNameField.first().click();
        waitForUi(appPage);
        businessNameField.first().fill("Negocio Prueba Automatización");
      }
      if (isVisible(cancelButton)) {
        cancelButton.first().click();
        waitForUi(appPage);
      }

      return modalValid;
    } catch (Throwable t) {
      safeFailureScreenshot(appPage, screenshotDir, "03_agregar_negocio_modal_failed", evidence);
      return false;
    }
  }

  private boolean stepOpenAdministrarNegocios(
      final Page appPage,
      final Path screenshotDir,
      final Map<String, String> evidence) {
    try {
      if (!isVisible(appPage.getByText(Pattern.compile("(?i)administrar\\s+negocios")).first())) {
        clickVisibleText(appPage, Pattern.compile("(?i)mi\\s+negocio"));
        waitForUi(appPage);
      }

      clickVisibleText(appPage, Pattern.compile("(?i)administrar\\s+negocios"));
      waitForUi(appPage);

      final boolean infoGeneral = isVisible(appPage.getByText(Pattern.compile("(?i)informaci[oó]n\\s+general")).first());
      final boolean detallesCuenta = isVisible(appPage.getByText(Pattern.compile("(?i)detalles\\s+de\\s+la\\s+cuenta")).first());
      final boolean tusNegocios = isVisible(appPage.getByText(Pattern.compile("(?i)tus\\s+negocios")).first());
      final boolean seccionLegal = isVisible(appPage.getByText(Pattern.compile("(?i)secci[oó]n\\s+legal")).first());

      final Path accountScreenshot = screenshot(appPage, screenshotDir, "04_administrar_negocios_view", true);
      evidence.put("administrar_negocios_screenshot", accountScreenshot.toString());
      return infoGeneral && detallesCuenta && tusNegocios && seccionLegal;
    } catch (Throwable t) {
      safeFailureScreenshot(appPage, screenshotDir, "04_administrar_negocios_failed", evidence);
      return false;
    }
  }

  private boolean stepValidateInformacionGeneral(final Page appPage) {
    try {
      final boolean userEmailVisible = isVisible(appPage.getByText(EMAIL_PATTERN).first());
      final boolean userNameVisible =
          isVisible(appPage.getByText(Pattern.compile("(?i)nombre|usuario|juan")).first())
              || isVisible(appPage.locator("h1, h2, h3").first());
      final boolean businessPlanVisible = isVisible(appPage.getByText(Pattern.compile("(?i)business\\s+plan")).first());
      final boolean cambiarPlanVisible = isVisible(appPage.getByRole(AriaRole.BUTTON,
          new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cambiar\\s+plan"))));
      return userEmailVisible && userNameVisible && businessPlanVisible && cambiarPlanVisible;
    } catch (Throwable t) {
      return false;
    }
  }

  private boolean stepValidateDetallesCuenta(final Page appPage) {
    try {
      final boolean cuentaCreadaVisible = isVisible(appPage.getByText(Pattern.compile("(?i)cuenta\\s+creada")).first());
      final boolean estadoActivoVisible = isVisible(appPage.getByText(Pattern.compile("(?i)estado\\s+activo")).first());
      final boolean idiomaVisible = isVisible(appPage.getByText(Pattern.compile("(?i)idioma\\s+seleccionado")).first());
      return cuentaCreadaVisible && estadoActivoVisible && idiomaVisible;
    } catch (Throwable t) {
      return false;
    }
  }

  private boolean stepValidateTusNegocios(final Page appPage) {
    try {
      final boolean headingVisible = isVisible(appPage.getByText(Pattern.compile("(?i)tus\\s+negocios")).first());
      final boolean agregarNegocioVisible = isVisible(appPage.getByText(Pattern.compile("(?i)agregar\\s+negocio")).first());
      final boolean negociosCounterVisible = isVisible(appPage.getByText(NEGOCIO_COUNTER_PATTERN).first());
      final Locator listCandidates = appPage.locator("li, [role='listitem'], table tbody tr, .business-card, .card");
      final boolean listVisible = listCandidates.count() > 0 || isVisible(appPage.getByText(Pattern.compile("(?i)negocio")).first());
      return headingVisible && agregarNegocioVisible && negociosCounterVisible && listVisible;
    } catch (Throwable t) {
      return false;
    }
  }

  private boolean stepValidateLegalLink(
      final String linkText,
      final Pattern expectedHeading,
      final String screenshotPrefix,
      final Page appPage,
      final BrowserContext context,
      final String appReturnUrl,
      final Path screenshotDir,
      final Map<String, String> evidence) {
    try {
      final Page legalPage;
      try {
        legalPage = context.waitForPage(
            () -> clickVisibleText(appPage, Pattern.compile("(?i)" + Pattern.quote(linkText))),
            new BrowserContext.WaitForPageOptions().setTimeout(7000));
        waitForUi(legalPage);
      } catch (PlaywrightException ignored) {
        clickVisibleText(appPage, Pattern.compile("(?i)" + Pattern.quote(linkText)));
        waitForUi(appPage);
        legalPage = appPage;
      }

      final boolean headingVisible = isVisible(legalPage.getByText(expectedHeading).first());
      final String bodyText = legalPage.locator("body").innerText();
      final boolean hasLegalContent = bodyText != null && bodyText.trim().length() > 200;

      final Path legalScreenshot = screenshot(legalPage, screenshotDir, screenshotPrefix, true);
      evidence.put(screenshotPrefix + "_screenshot", legalScreenshot.toString());
      evidence.put(screenshotPrefix + "_url", legalPage.url());

      if (legalPage != appPage) {
        legalPage.close();
        appPage.bringToFront();
        waitForUi(appPage);
      } else if (appReturnUrl != null && !appReturnUrl.isEmpty()) {
        appPage.navigate(appReturnUrl);
        waitForUi(appPage);
      }

      return headingVisible && hasLegalContent;
    } catch (Throwable t) {
      safeFailureScreenshot(appPage, screenshotDir, screenshotPrefix + "_failed", evidence);
      return false;
    }
  }

  private void clickAnyVisibleText(final Page page, final String[] exactTexts, final Pattern fallbackPattern) {
    for (final String text : exactTexts) {
      if (tryClickExactVisibleText(page, text)) {
        return;
      }
    }
    clickVisibleText(page, fallbackPattern);
  }

  private boolean tryClickExactVisibleText(final Page page, final String text) {
    final Locator button = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(text));
    if (isVisible(button)) {
      button.first().click();
      waitForUi(page);
      return true;
    }
    final Locator link = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(text));
    if (isVisible(link)) {
      link.first().click();
      waitForUi(page);
      return true;
    }
    final Locator plainText = page.getByText(text);
    if (isVisible(plainText)) {
      plainText.first().click();
      waitForUi(page);
      return true;
    }
    return false;
  }

  private void clickVisibleText(final Page page, final Pattern textPattern) {
    final Locator button = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(textPattern));
    if (isVisible(button)) {
      button.first().click();
      waitForUi(page);
      return;
    }

    final Locator link = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(textPattern));
    if (isVisible(link)) {
      link.first().click();
      waitForUi(page);
      return;
    }

    final Locator plainText = page.getByText(textPattern);
    if (isVisible(plainText)) {
      plainText.first().click();
      waitForUi(page);
      return;
    }

    throw new AssertionError("Unable to click visible element with text pattern: " + textPattern);
  }

  private static boolean isVisible(final Locator locator) {
    try {
      return locator != null && locator.first().isVisible();
    } catch (Throwable t) {
      return false;
    }
  }

  private static void waitForUi(final Page page) {
    try {
      page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(8000));
    } catch (PlaywrightException ignored) {
      // Ignore pages that do not trigger this state repeatedly (SPA transitions).
    }
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(8000));
    } catch (PlaywrightException ignored) {
      // Some applications keep long-lived requests open; small pause is enough.
    }
    page.waitForTimeout(600);
  }

  private static Path createScreenshotDir() throws Exception {
    final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now());
    final Path dir = Paths.get("target", "saleads-e2e-screenshots", timestamp);
    Files.createDirectories(dir);
    return dir;
  }

  private static Path screenshot(final Page page, final Path dir, final String name, final boolean fullPage) {
    final Path path = dir.resolve(name + ".png");
    page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
    return path;
  }

  private static void safeFailureScreenshot(
      final Page page,
      final Path screenshotDir,
      final String name,
      final Map<String, String> evidence) {
    try {
      final Path failedStepScreenshot = screenshot(page, screenshotDir, name, true);
      evidence.put(name + "_screenshot", failedStepScreenshot.toString());
    } catch (Throwable ignored) {
      // Best effort evidence only.
    }
  }

  private static String firstNonBlank(final String... values) {
    for (final String value : values) {
      if (value != null && !value.trim().isEmpty()) {
        return value.trim();
      }
    }
    return null;
  }

  private static void printFinalReport(
      final Map<String, Boolean> report,
      final Map<String, String> evidence,
      final Path screenshotDir) {
    final String separator = "====================================================";
    System.out.println(separator);
    System.out.println("SaleADS Mi Negocio Workflow Final Report");
    System.out.println(separator);
    for (Map.Entry<String, Boolean> entry : report.entrySet()) {
      System.out.println(String.format("%s: %s", entry.getKey(), entry.getValue() ? "PASS" : "FAIL"));
    }
    System.out.println(separator);
    System.out.println("Evidence");
    System.out.println("Screenshot directory: " + screenshotDir.toAbsolutePath());
    for (Map.Entry<String, String> entry : evidence.entrySet()) {
      System.out.println(String.format("%s: %s", entry.getKey(), entry.getValue()));
    }
    System.out.println(separator);
  }
}
