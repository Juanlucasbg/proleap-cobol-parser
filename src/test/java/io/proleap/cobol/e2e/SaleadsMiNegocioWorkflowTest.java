package io.proleap.cobol.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class SaleadsMiNegocioWorkflowTest {

  private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
  private static final double TIMEOUT_MS = 12000;
  private static final double SHORT_TIMEOUT_MS = 2500;

  @Test
  public void saleadsMiNegocioFullWorkflow() throws IOException {
    final Map<String, Boolean> report = new LinkedHashMap<>();
    final List<String> failures = new ArrayList<>();
    final Path evidenceDir = Path.of("target", "saleads-evidence", "mi-negocio");
    Files.createDirectories(evidenceDir);

    report.put("Login", false);
    report.put("Mi Negocio menu", false);
    report.put("Agregar Negocio modal", false);
    report.put("Administrar Negocios view", false);
    report.put("Información General", false);
    report.put("Detalles de la Cuenta", false);
    report.put("Tus Negocios", false);
    report.put("Términos y Condiciones", false);
    report.put("Política de Privacidad", false);

    try (Playwright playwright = Playwright.create()) {
      Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(isHeadless()));
      BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
      Page appPage = context.newPage();
      try {
        navigateIfConfigured(appPage);
        runStep(report, failures, "Login", () -> {
          Locator loginButton = firstVisibleLocator(appPage,
            buttonByName(appPage, "Sign in with Google"),
            buttonByName(appPage, "Iniciar sesión con Google"),
            buttonByName(appPage, "Inicia sesión con Google"),
            buttonByName(appPage, "Continuar con Google"),
            linkByName(appPage, "Sign in with Google"),
            textLocator(appPage, "Google")
          );

          clickAndWait(appPage, loginButton);

          Locator googleAccount = textLocator(appPage, GOOGLE_ACCOUNT);
          if (isVisible(googleAccount, 7000)) {
            clickAndWait(appPage, googleAccount);
          }

          waitVisible(anySidebar(appPage), "left sidebar navigation");
          waitVisible(textLocator(appPage, "Negocio"), "Negocio navigation section");
          screenshot(appPage, evidenceDir, "01-dashboard-loaded.png", false);
        });

        runStep(report, failures, "Mi Negocio menu", () -> {
          waitVisible(textLocator(appPage, "Negocio"), "Negocio section");

          Locator miNegocio = firstVisibleLocator(appPage,
            linkByName(appPage, "Mi Negocio"),
            buttonByName(appPage, "Mi Negocio"),
            textLocator(appPage, "Mi Negocio")
          );
          clickAndWait(appPage, miNegocio);

          waitVisible(textLocator(appPage, "Agregar Negocio"), "Agregar Negocio option");
          waitVisible(textLocator(appPage, "Administrar Negocios"), "Administrar Negocios option");
          screenshot(appPage, evidenceDir, "02-mi-negocio-menu-expanded.png", false);
        });

        runStep(report, failures, "Agregar Negocio modal", () -> {
          Locator agregarNegocio = firstVisibleLocator(appPage,
            linkByName(appPage, "Agregar Negocio"),
            buttonByName(appPage, "Agregar Negocio"),
            textLocator(appPage, "Agregar Negocio")
          );
          clickAndWait(appPage, agregarNegocio);

          waitVisible(textLocator(appPage, "Crear Nuevo Negocio"), "Crear Nuevo Negocio modal title");
          Locator nombreInput = firstVisibleLocator(appPage,
            appPage.getByLabel(Pattern.compile("(?iu).*Nombre del Negocio.*")),
            appPage.getByPlaceholder(Pattern.compile("(?iu).*Nombre del Negocio.*")),
            appPage.locator("input[name*='nombre'], input[name*='businessName'], input[type='text']").first()
          );
          waitVisible(nombreInput, "Nombre del Negocio input");
          waitVisible(textLocator(appPage, "Tienes 2 de 3 negocios"), "business quota text");
          waitVisible(buttonByName(appPage, "Cancelar"), "Cancelar button");
          waitVisible(buttonByName(appPage, "Crear Negocio"), "Crear Negocio button");
          screenshot(appPage, evidenceDir, "03-agregar-negocio-modal.png", false);

          if (isVisible(nombreInput, SHORT_TIMEOUT_MS)) {
            nombreInput.fill("Negocio Prueba Automatizacion");
          }
          clickAndWait(appPage, buttonByName(appPage, "Cancelar"));
        });

        runStep(report, failures, "Administrar Negocios view", () -> {
          Locator administrarNegocios = firstVisibleLocator(appPage,
            linkByName(appPage, "Administrar Negocios"),
            buttonByName(appPage, "Administrar Negocios"),
            textLocator(appPage, "Administrar Negocios")
          );
          clickAndWait(appPage, administrarNegocios);

          waitVisible(textLocator(appPage, "Información General"), "Información General section");
          waitVisible(textLocator(appPage, "Detalles de la Cuenta"), "Detalles de la Cuenta section");
          waitVisible(textLocator(appPage, "Tus Negocios"), "Tus Negocios section");
          waitVisible(firstVisibleLocator(appPage,
            textLocator(appPage, "Sección Legal"),
            textLocator(appPage, "Seccion Legal")
          ), "Sección Legal section");
          screenshot(appPage, evidenceDir, "04-administrar-negocios-full.png", true);
        });

        runStep(report, failures, "Información General", () -> {
          waitVisible(textLocator(appPage, "Información General"), "Información General heading");
          waitVisible(anyEmail(appPage), "user email");
          waitVisible(textLocator(appPage, "BUSINESS PLAN"), "BUSINESS PLAN text");
          waitVisible(buttonByName(appPage, "Cambiar Plan"), "Cambiar Plan button");

          Locator nameCandidate = firstVisibleLocator(appPage,
            textLocator(appPage, "Nombre"),
            textLocator(appPage, "Usuario"),
            textLocator(appPage, "Perfil"),
            appPage.locator("h1, h2, h3").first()
          );
          waitVisible(nameCandidate, "user name information");
        });

        runStep(report, failures, "Detalles de la Cuenta", () -> {
          waitVisible(textLocator(appPage, "Detalles de la Cuenta"), "Detalles de la Cuenta heading");
          waitVisible(textLocator(appPage, "Cuenta creada"), "Cuenta creada text");
          waitVisible(textLocator(appPage, "Estado activo"), "Estado activo text");
          waitVisible(textLocator(appPage, "Idioma seleccionado"), "Idioma seleccionado text");
        });

        runStep(report, failures, "Tus Negocios", () -> {
          waitVisible(textLocator(appPage, "Tus Negocios"), "Tus Negocios heading");
          waitVisible(buttonByName(appPage, "Agregar Negocio"), "Agregar Negocio button");
          waitVisible(textLocator(appPage, "Tienes 2 de 3 negocios"), "business quota text");

          Locator listLike = appPage.locator("li, [role='row'], [data-testid*='business'], [class*='business']");
          if (listLike.count() == 0) {
            throw new AssertionError("Business list is not visible.");
          }
        });

        runStep(report, failures, "Términos y Condiciones", () -> {
          LegalResult termsResult = openLegalDocument(
            context,
            appPage,
            firstVisibleLocator(appPage,
              linkByName(appPage, "Términos y Condiciones"),
              linkByName(appPage, "Terminos y Condiciones"),
              buttonByName(appPage, "Términos y Condiciones"),
              textLocator(appPage, "Términos y Condiciones"),
              textLocator(appPage, "Terminos y Condiciones")
            ),
            Pattern.compile("(?iu).*Términos y Condiciones.*|.*Terminos y Condiciones.*"),
            evidenceDir,
            "05-terminos-y-condiciones.png"
          );
          if (!termsResult.finalUrl.isBlank()) {
            System.out.println("[EVIDENCE] Términos y Condiciones URL: " + termsResult.finalUrl);
          }
        });

        runStep(report, failures, "Política de Privacidad", () -> {
          LegalResult privacyResult = openLegalDocument(
            context,
            appPage,
            firstVisibleLocator(appPage,
              linkByName(appPage, "Política de Privacidad"),
              linkByName(appPage, "Politica de Privacidad"),
              buttonByName(appPage, "Política de Privacidad"),
              textLocator(appPage, "Política de Privacidad"),
              textLocator(appPage, "Politica de Privacidad")
            ),
            Pattern.compile("(?iu).*Política de Privacidad.*|.*Politica de Privacidad.*"),
            evidenceDir,
            "06-politica-de-privacidad.png"
          );
          if (!privacyResult.finalUrl.isBlank()) {
            System.out.println("[EVIDENCE] Política de Privacidad URL: " + privacyResult.finalUrl);
          }
        });
      } finally {
        context.close();
        browser.close();
      }
    }

    StringBuilder reportTable = new StringBuilder();
    reportTable.append("\n=== SaleADS Mi Negocio Final Report ===\n");
    boolean allPassed = true;
    for (Map.Entry<String, Boolean> entry : report.entrySet()) {
      reportTable
        .append("- ")
        .append(entry.getKey())
        .append(": ")
        .append(entry.getValue() ? "PASS" : "FAIL")
        .append('\n');
      allPassed = allPassed && entry.getValue();
    }
    System.out.println(reportTable);

    if (!failures.isEmpty()) {
      System.out.println("=== Failure details ===");
      for (String failure : failures) {
        System.out.println("- " + failure);
      }
    }

    Assert.assertTrue("One or more workflow steps failed. See report above.", allPassed);
  }

  private void navigateIfConfigured(Page page) {
    String loginUrl = env("SALEADS_LOGIN_URL");
    if (loginUrl == null || loginUrl.isBlank()) {
      loginUrl = env("SALEADS_BASE_URL");
    }
    if (loginUrl != null && !loginUrl.isBlank()) {
      page.navigate(loginUrl.trim());
      waitForUi(page);
      return;
    }

    if ("about:blank".equals(page.url())) {
      throw new IllegalStateException(
        "No login URL configured. Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL) " +
          "or pre-load the browser on the SaleADS login page before running this test."
      );
    }
  }

  private LegalResult openLegalDocument(
    BrowserContext context,
    Page appPage,
    Locator linkLocator,
    Pattern headingPattern,
    Path evidenceDir,
    String screenshotName
  ) {
    int pagesBefore = context.pages().size();
    clickAndWait(appPage, linkLocator);

    Page targetPage = waitForNewPage(context, pagesBefore);
    boolean openedNewTab = targetPage != null;
    if (!openedNewTab) {
      targetPage = appPage;
    }

    waitForUi(targetPage);
    waitVisible(targetPage.getByText(headingPattern).first(), "legal document heading");

    Locator legalContent = targetPage.locator("article, main, section, p");
    if (legalContent.count() == 0) {
      throw new AssertionError("Legal content text is not visible.");
    }

    screenshot(targetPage, evidenceDir, screenshotName, true);
    String finalUrl = targetPage.url();

    if (openedNewTab) {
      targetPage.close();
      appPage.bringToFront();
      waitForUi(appPage);
    }

    return new LegalResult(finalUrl);
  }

  private Page waitForNewPage(BrowserContext context, int pagesBefore) {
    long end = System.currentTimeMillis() + 7000;
    while (System.currentTimeMillis() < end) {
      if (context.pages().size() > pagesBefore) {
        List<Page> pages = context.pages();
        return pages.get(pages.size() - 1);
      }
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }
    return null;
  }

  private void runStep(Map<String, Boolean> report, List<String> failures, String stepName, Runnable stepWork) {
    try {
      stepWork.run();
      report.put(stepName, true);
    } catch (Throwable t) {
      report.put(stepName, false);
      failures.add(stepName + " -> " + String.valueOf(t.getMessage()));
    }
  }

  private Locator firstVisibleLocator(Page page, Locator... candidates) {
    for (Locator candidate : candidates) {
      if (candidate != null && isVisible(candidate, SHORT_TIMEOUT_MS)) {
        return candidate.first();
      }
    }
    throw new AssertionError("No visible locator found among the provided text-first candidates.");
  }

  private Locator buttonByName(Page page, String text) {
    return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*" + Pattern.quote(text) + ".*"))).first();
  }

  private Locator linkByName(Page page, String text) {
    return page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?iu).*" + Pattern.quote(text) + ".*"))).first();
  }

  private Locator textLocator(Page page, String text) {
    return page.getByText(Pattern.compile("(?iu).*" + Pattern.quote(text) + ".*")).first();
  }

  private Locator anySidebar(Page page) {
    return page.locator("aside, nav, [role='navigation']").first();
  }

  private Locator anyEmail(Page page) {
    return page.getByText(Pattern.compile("(?iu)[a-z0-9._%+\\-]+@[a-z0-9.\\-]+\\.[a-z]{2,}")).first();
  }

  private boolean isVisible(Locator locator, double timeoutMs) {
    try {
      return locator.first().isVisible(new Locator.IsVisibleOptions().setTimeout(timeoutMs));
    } catch (RuntimeException ignored) {
      return false;
    }
  }

  private void waitVisible(Locator locator, String description) {
    try {
      locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(TIMEOUT_MS));
    } catch (RuntimeException e) {
      throw new AssertionError("Expected visible element not found: " + description, e);
    }
  }

  private void clickAndWait(Page page, Locator locator) {
    locator.first().click(new Locator.ClickOptions().setTimeout(TIMEOUT_MS));
    waitForUi(page);
  }

  private void waitForUi(Page page) {
    try {
      page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(TIMEOUT_MS));
    } catch (RuntimeException ignored) {
      // Some SPA transitions do not emit a full load; continue with best effort.
    }
    try {
      page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
    } catch (RuntimeException ignored) {
      // Allow background network activity while still waiting a short settle period.
    }
    page.waitForTimeout(500);
  }

  private void screenshot(Page page, Path evidenceDir, String fileName, boolean fullPage) {
    page.screenshot(new Page.ScreenshotOptions()
      .setPath(evidenceDir.resolve(fileName))
      .setFullPage(fullPage));
  }

  private boolean isHeadless() {
    String value = env("HEADLESS");
    return value == null || !"false".equalsIgnoreCase(value.trim());
  }

  private String env(String key) {
    return System.getenv(key);
  }

  private static final class LegalResult {
    private final String finalUrl;

    private LegalResult(String finalUrl) {
      this.finalUrl = finalUrl == null ? "" : finalUrl;
    }
  }
}
