package io.proleap.cobol.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;

public class SaleAdsMiNegocioWorkflowTest {

  private static final String LOGIN_RESULT_KEY = "Login";
  private static final String MENU_RESULT_KEY = "Mi Negocio menu";
  private static final String MODAL_RESULT_KEY = "Agregar Negocio modal";
  private static final String ADMIN_RESULT_KEY = "Administrar Negocios view";
  private static final String GENERAL_RESULT_KEY = "Información General";
  private static final String DETAILS_RESULT_KEY = "Detalles de la Cuenta";
  private static final String BUSINESSES_RESULT_KEY = "Tus Negocios";
  private static final String TERMS_RESULT_KEY = "Términos y Condiciones";
  private static final String PRIVACY_RESULT_KEY = "Política de Privacidad";

  @Test
  public void saleadsMiNegocioFullWorkflow() throws Exception {
    final String loginUrl = requiredEnv("SALEADS_LOGIN_URL");
    final String googleAccountEmail =
        envOrDefault("SALEADS_GOOGLE_ACCOUNT_EMAIL", "juanlucasbarbiergarzon@gmail.com");
    final boolean headless = Boolean.parseBoolean(envOrDefault("SALEADS_HEADLESS", "false"));

    final Path screenshotDir = createScreenshotDirectory();

    final Map<String, Boolean> resultByStep = new LinkedHashMap<>();
    resultByStep.put(LOGIN_RESULT_KEY, false);
    resultByStep.put(MENU_RESULT_KEY, false);
    resultByStep.put(MODAL_RESULT_KEY, false);
    resultByStep.put(ADMIN_RESULT_KEY, false);
    resultByStep.put(GENERAL_RESULT_KEY, false);
    resultByStep.put(DETAILS_RESULT_KEY, false);
    resultByStep.put(BUSINESSES_RESULT_KEY, false);
    resultByStep.put(TERMS_RESULT_KEY, false);
    resultByStep.put(PRIVACY_RESULT_KEY, false);

    final Map<String, String> finalUrls = new LinkedHashMap<>();

    try (Playwright playwright = Playwright.create()) {
      Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
      BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
      Page appPage = context.newPage();

      appPage.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
      waitForUi(appPage);

      boolean loginPassed =
          runStep(
              resultByStep,
              LOGIN_RESULT_KEY,
              () -> {
                clickLoginWithGoogle(appPage, context);
                selectGoogleAccountIfVisible(appPage, googleAccountEmail);
                waitForUi(appPage);
                assertAnyVisible(
                    appPage,
                    Arrays.asList("aside", "nav", "[class*='sidebar']", "text=Negocio", "text=Mi Negocio"),
                    "Expected dashboard/sidebar after Google login.");
                captureScreenshot(appPage, screenshotDir.resolve("01-dashboard-loaded.png"), false);
              });

      boolean menuPassed =
          loginPassed
              && runStep(
                  resultByStep,
                  MENU_RESULT_KEY,
                  () -> {
                    openMiNegocioMenu(appPage);
                    assertTextVisible(appPage, "Agregar Negocio");
                    assertTextVisible(appPage, "Administrar Negocios");
                    captureScreenshot(appPage, screenshotDir.resolve("02-mi-negocio-expanded.png"), false);
                  });

      boolean modalPassed =
          menuPassed
              && runStep(
                  resultByStep,
                  MODAL_RESULT_KEY,
                  () -> {
                    clickByVisibleText(
                        appPage,
                        Arrays.asList(
                            "button:has-text(\"Agregar Negocio\")",
                            "[role='button']:has-text(\"Agregar Negocio\")",
                            "text=Agregar Negocio"));

                    assertTextVisible(appPage, "Crear Nuevo Negocio");
                    assertAnyVisible(
                        appPage,
                        Arrays.asList(
                            "label:has-text(\"Nombre del Negocio\")",
                            "input[placeholder*='Nombre del Negocio']",
                            "input[name*='negocio']",
                            "input"),
                        "Expected Nombre del Negocio field in modal.");
                    assertTextVisible(appPage, "Tienes 2 de 3 negocios");
                    assertTextVisible(appPage, "Cancelar");
                    assertTextVisible(appPage, "Crear Negocio");

                    captureScreenshot(appPage, screenshotDir.resolve("03-agregar-negocio-modal.png"), false);

                    Locator businessNameInput =
                        firstVisible(
                            appPage,
                            Arrays.asList(
                                "input[placeholder*='Nombre del Negocio']",
                                "input[name*='negocio']",
                                "input[type='text']"),
                            2000);
                    businessNameInput.fill("Negocio Prueba Automatización");
                    clickByVisibleText(
                        appPage,
                        Arrays.asList(
                            "button:has-text(\"Cancelar\")",
                            "[role='button']:has-text(\"Cancelar\")",
                            "text=Cancelar"));
                    waitForUi(appPage);
                  });

      boolean adminPassed =
          modalPassed
              && runStep(
                  resultByStep,
                  ADMIN_RESULT_KEY,
                  () -> {
                    if (!isTextVisible(appPage, "Administrar Negocios", 1500)) {
                      openMiNegocioMenu(appPage);
                    }
                    clickByVisibleText(
                        appPage,
                        Arrays.asList(
                            "a:has-text(\"Administrar Negocios\")",
                            "button:has-text(\"Administrar Negocios\")",
                            "text=Administrar Negocios"));
                    waitForUi(appPage);

                    assertTextVisible(appPage, "Información General");
                    assertTextVisible(appPage, "Detalles de la Cuenta");
                    assertTextVisible(appPage, "Tus Negocios");
                    assertAnyVisible(
                        appPage,
                        Arrays.asList(
                            "text=Sección Legal",
                            "text=Terminos y Condiciones",
                            "text=Términos y Condiciones"),
                        "Expected legal section in account page.");

                    captureScreenshot(appPage, screenshotDir.resolve("04-administrar-negocios-full.png"), true);
                  });

      boolean generalPassed =
          adminPassed
              && runStep(
                  resultByStep,
                  GENERAL_RESULT_KEY,
                  () -> {
                    assertTextVisible(appPage, "Información General");
                    assertAnyVisible(
                        appPage,
                        Arrays.asList(
                            "text=Nombre",
                            "text=Usuario",
                            "text=Name",
                            "text=Correo",
                            "text=Email"),
                        "Expected user information labels in Información General.");
                    assertBodyContainsEmail(appPage);
                    assertTextVisible(appPage, "BUSINESS PLAN");
                    assertTextVisible(appPage, "Cambiar Plan");
                  });

      boolean detailsPassed =
          generalPassed
              && runStep(
                  resultByStep,
                  DETAILS_RESULT_KEY,
                  () -> {
                    assertTextVisible(appPage, "Cuenta creada");
                    assertAnyVisible(
                        appPage,
                        Arrays.asList("text=Estado activo", "text=Activo", "text=estado activo"),
                        "Expected account active status.");
                    assertAnyVisible(
                        appPage,
                        Arrays.asList("text=Idioma seleccionado", "text=Idioma"),
                        "Expected language selection text.");
                  });

      boolean businessesPassed =
          detailsPassed
              && runStep(
                  resultByStep,
                  BUSINESSES_RESULT_KEY,
                  () -> {
                    assertTextVisible(appPage, "Tus Negocios");
                    assertAnyVisible(
                        appPage,
                        Arrays.asList(
                            "button:has-text(\"Agregar Negocio\")",
                            "[role='button']:has-text(\"Agregar Negocio\")",
                            "text=Agregar Negocio"),
                        "Expected Agregar Negocio button in business list.");
                    assertTextVisible(appPage, "Tienes 2 de 3 negocios");
                  });

      boolean termsPassed =
          businessesPassed
              && runStep(
                  resultByStep,
                  TERMS_RESULT_KEY,
                  () -> {
                    String termsUrl =
                        validateLegalPage(
                            appPage,
                            context,
                            "Términos y Condiciones",
                            "Términos y Condiciones",
                            screenshotDir.resolve("05-terminos-condiciones.png"));
                    finalUrls.put(TERMS_RESULT_KEY, termsUrl);
                  });

      boolean privacyPassed =
          termsPassed
              && runStep(
                  resultByStep,
                  PRIVACY_RESULT_KEY,
                  () -> {
                    String privacyUrl =
                        validateLegalPage(
                            appPage,
                            context,
                            "Política de Privacidad",
                            "Política de Privacidad",
                            screenshotDir.resolve("06-politica-privacidad.png"));
                    finalUrls.put(PRIVACY_RESULT_KEY, privacyUrl);
                  });

      if (!loginPassed) {
        markRemainingAsFailed(resultByStep, LOGIN_RESULT_KEY);
      } else if (!menuPassed) {
        markRemainingAsFailed(resultByStep, MENU_RESULT_KEY);
      } else if (!modalPassed) {
        markRemainingAsFailed(resultByStep, MODAL_RESULT_KEY);
      } else if (!adminPassed) {
        markRemainingAsFailed(resultByStep, ADMIN_RESULT_KEY);
      } else if (!generalPassed) {
        markRemainingAsFailed(resultByStep, GENERAL_RESULT_KEY);
      } else if (!detailsPassed) {
        markRemainingAsFailed(resultByStep, DETAILS_RESULT_KEY);
      } else if (!businessesPassed) {
        markRemainingAsFailed(resultByStep, BUSINESSES_RESULT_KEY);
      } else if (!termsPassed) {
        markRemainingAsFailed(resultByStep, TERMS_RESULT_KEY);
      } else if (!privacyPassed) {
        markRemainingAsFailed(resultByStep, PRIVACY_RESULT_KEY);
      }

      printFinalReport(resultByStep, finalUrls, screenshotDir);
      Assert.assertTrue(
          "One or more SaleADS Mi Negocio validations failed.",
          resultByStep.values().stream().allMatch(Boolean::booleanValue));
    }
  }

  private void clickLoginWithGoogle(Page appPage, BrowserContext context) {
    Locator loginButton =
        firstVisible(
            appPage,
            Arrays.asList(
                "button:has-text(\"Sign in with Google\")",
                "button:has-text(\"Iniciar sesión con Google\")",
                "button:has-text(\"Continuar con Google\")",
                "a:has-text(\"Google\")",
                "button:has-text(\"Google\")",
                "[role='button']:has-text(\"Google\")"),
            10000);

    Page popup = null;
    try {
      popup =
          context.waitForPage(
              () -> loginButton.click(),
              new BrowserContext.WaitForPageOptions().setTimeout(5000));
    } catch (PlaywrightException ignored) {
      loginButton.click();
    }

    if (popup != null) {
      waitForUi(popup);
      selectGoogleAccountIfVisible(popup, envOrDefault("SALEADS_GOOGLE_ACCOUNT_EMAIL", "juanlucasbarbiergarzon@gmail.com"));
      appPage.bringToFront();
      waitForUi(appPage);
    }
  }

  private void openMiNegocioMenu(Page page) {
    if (!isTextVisible(page, "Mi Negocio", 1500) && isTextVisible(page, "Negocio", 1500)) {
      clickByVisibleText(
          page,
          Arrays.asList(
              "text=Negocio",
              "button:has-text(\"Negocio\")",
              "[role='button']:has-text(\"Negocio\")"));
      waitForUi(page);
    }

    clickByVisibleText(
        page,
        Arrays.asList(
            "text=Mi Negocio",
            "a:has-text(\"Mi Negocio\")",
            "button:has-text(\"Mi Negocio\")",
            "[role='button']:has-text(\"Mi Negocio\")"));
    waitForUi(page);
  }

  private String validateLegalPage(
      Page appPage, BrowserContext context, String linkText, String headingText, Path screenshotPath) {
    Page legalPage = null;
    String appUrlBefore = appPage.url();

    try {
      legalPage =
          context.waitForPage(
              () ->
                  clickByVisibleText(
                      appPage,
                      Arrays.asList(
                          "a:has-text(\"" + linkText + "\")",
                          "button:has-text(\"" + linkText + "\")",
                          "[role='link']:has-text(\"" + linkText + "\")",
                          "text=" + linkText)),
              new BrowserContext.WaitForPageOptions().setTimeout(5000));
    } catch (PlaywrightException ignored) {
      clickByVisibleText(
          appPage,
          Arrays.asList(
              "a:has-text(\"" + linkText + "\")",
              "button:has-text(\"" + linkText + "\")",
              "[role='link']:has-text(\"" + linkText + "\")",
              "text=" + linkText));
    }

    Page activePage = legalPage != null ? legalPage : appPage;
    waitForUi(activePage);
    assertTextVisible(activePage, headingText);

    String legalBody = activePage.textContent("body");
    Assert.assertNotNull("Expected legal content body for " + headingText, legalBody);
    Assert.assertTrue(
        "Expected visible legal content for " + headingText,
        legalBody.replaceAll("\\s+", " ").trim().length() > 100);

    captureScreenshot(activePage, screenshotPath, true);
    String finalUrl = activePage.url();

    if (legalPage != null) {
      legalPage.close();
      appPage.bringToFront();
      waitForUi(appPage);
    } else if (!appPage.url().equals(appUrlBefore)) {
      appPage.navigate(appUrlBefore, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
      waitForUi(appPage);
    }

    return finalUrl;
  }

  private void selectGoogleAccountIfVisible(Page page, String email) {
    try {
      Locator accountLocator = page.locator("text=" + email).first();
      accountLocator.waitFor(
          new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
      accountLocator.click();
      waitForUi(page);
    } catch (PlaywrightException ignored) {
      // Google account chooser may not appear if an authenticated session already exists.
    }
  }

  private void clickByVisibleText(Page page, List<String> selectors) {
    Locator locator = firstVisible(page, selectors, 10000);
    locator.click();
    waitForUi(page);
  }

  private void assertTextVisible(Page page, String text) {
    Locator locator = page.locator("text=" + text).first();
    locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
  }

  private boolean isTextVisible(Page page, String text, double timeoutMs) {
    try {
      page.locator("text=" + text)
          .first()
          .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
      return true;
    } catch (PlaywrightException ignored) {
      return false;
    }
  }

  private void assertAnyVisible(Page page, List<String> selectors, String errorMessage) {
    try {
      firstVisible(page, selectors, 10000);
    } catch (PlaywrightException exception) {
      throw new AssertionError(errorMessage, exception);
    }
  }

  private Locator firstVisible(Page page, List<String> selectors, double timeoutMs) {
    PlaywrightException lastError = null;
    for (String selector : selectors) {
      try {
        Locator locator = page.locator(selector).first();
        locator.waitFor(
            new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
        return locator;
      } catch (PlaywrightException error) {
        lastError = error;
      }
    }
    if (lastError != null) {
      throw lastError;
    }
    throw new PlaywrightException("No visible selector matched: " + selectors);
  }

  private boolean runStep(
      Map<String, Boolean> resultByStep, String stepKey, CheckedRunnable action) {
    try {
      action.run();
      resultByStep.put(stepKey, true);
      return true;
    } catch (Throwable failure) {
      resultByStep.put(stepKey, false);
      System.err.println("[FAIL] " + stepKey + " -> " + failure.getMessage());
      return false;
    }
  }

  private void assertBodyContainsEmail(Page page) {
    String body = page.textContent("body");
    Assert.assertNotNull("Page body should be available to validate email", body);
    Assert.assertTrue(
        "Expected a visible user email in Información General.",
        body.matches("(?s).*[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}.*"));
  }

  private void markRemainingAsFailed(Map<String, Boolean> resultByStep, String lastAttemptedKey) {
    boolean markAsFailed = false;
    for (Map.Entry<String, Boolean> entry : resultByStep.entrySet()) {
      if (markAsFailed) {
        entry.setValue(false);
      }
      if (entry.getKey().equals(lastAttemptedKey)) {
        markAsFailed = true;
      }
    }
  }

  private void waitForUi(Page page) {
    try {
      page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(7000));
    } catch (PlaywrightException ignored) {
      // Some SPA transitions do not trigger a formal load event.
    }
    page.waitForTimeout(600);
  }

  private void captureScreenshot(Page page, Path outputPath, boolean fullPage) {
    page.screenshot(new Page.ScreenshotOptions().setPath(outputPath).setFullPage(fullPage));
  }

  private Path createScreenshotDirectory() throws Exception {
    String baseOutputPath = envOrDefault("SALEADS_SCREENSHOTS_DIR", "target/saleads-screenshots");
    String runId = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
    Path screenshotDir = Paths.get(baseOutputPath, runId);
    Files.createDirectories(screenshotDir);
    return screenshotDir;
  }

  private String requiredEnv(String key) {
    String value = System.getenv(key);
    Assert.assertNotNull("Missing required environment variable: " + key, value);
    Assert.assertTrue(
        "Environment variable " + key + " must not be blank.",
        value != null && !value.trim().isEmpty());
    return value.trim();
  }

  private String envOrDefault(String key, String defaultValue) {
    String value = System.getenv(key);
    return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
  }

  private void printFinalReport(
      Map<String, Boolean> resultByStep, Map<String, String> finalUrls, Path screenshotDir) {
    String report =
        resultByStep.entrySet().stream()
            .map(entry -> "- " + entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"))
            .collect(Collectors.joining("\n"));

    System.out.println("=== SaleADS Mi Negocio Workflow Report ===");
    System.out.println(report);
    System.out.println("Screenshots directory: " + screenshotDir.toAbsolutePath());
    if (!finalUrls.isEmpty()) {
      System.out.println("Final legal URLs:");
      for (Map.Entry<String, String> entry : finalUrls.entrySet()) {
        System.out.println("- " + entry.getKey() + ": " + entry.getValue());
      }
    }
    System.out.println("==========================================");
  }

  @FunctionalInterface
  private interface CheckedRunnable {
    void run() throws Exception;
  }
}
