package io.proleap.saleads;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullWorkflowTest {

    private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private static final String REPORT_LOGIN = "Login";
    private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
    private static final String REPORT_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
    private static final String REPORT_ADMINISTRAR_NEGOCIOS = "Administrar Negocios view";
    private static final String REPORT_INFORMACION_GENERAL = "Información General";
    private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
    private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
    private static final String REPORT_TERMINOS = "Términos y Condiciones";
    private static final String REPORT_PRIVACIDAD = "Política de Privacidad";

    private final Map<String, Boolean> reportStatus = new LinkedHashMap<>();
    private final Map<String, String> legalUrls = new LinkedHashMap<>();

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page appPage;
    private Path evidenceDir;

    @Before
    public void setUp() throws IOException {
        String headlessValue = firstNonBlank(System.getProperty("saleads.headless"), System.getenv("SALEADS_HEADLESS"));
        boolean headless = headlessValue == null || Boolean.parseBoolean(headlessValue);

        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
        context.setDefaultTimeout(25_000);

        evidenceDir = Paths.get("target", "saleads-mi-negocio-evidence");
        Files.createDirectories(evidenceDir);

        reportStatus.put(REPORT_LOGIN, false);
        reportStatus.put(REPORT_MI_NEGOCIO_MENU, false);
        reportStatus.put(REPORT_AGREGAR_NEGOCIO_MODAL, false);
        reportStatus.put(REPORT_ADMINISTRAR_NEGOCIOS, false);
        reportStatus.put(REPORT_INFORMACION_GENERAL, false);
        reportStatus.put(REPORT_DETALLES_CUENTA, false);
        reportStatus.put(REPORT_TUS_NEGOCIOS, false);
        reportStatus.put(REPORT_TERMINOS, false);
        reportStatus.put(REPORT_PRIVACIDAD, false);
    }

    @After
    public void tearDown() {
        if (context != null) {
            context.close();
        }
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @Test
    public void saleadsMiNegocioFullWorkflow() throws IOException {
        try {
            boolean loginOk = stepLoginWithGoogleAndValidateDashboard();
            mark(REPORT_LOGIN, loginOk);

            boolean menuOk = loginOk && stepOpenMiNegocioMenu();
            mark(REPORT_MI_NEGOCIO_MENU, menuOk);

            boolean agregarModalOk = menuOk && stepValidateAgregarNegocioModal();
            mark(REPORT_AGREGAR_NEGOCIO_MODAL, agregarModalOk);

            boolean administrarOk = menuOk && stepOpenAdministrarNegocios();
            mark(REPORT_ADMINISTRAR_NEGOCIOS, administrarOk);

            boolean infoGeneralOk = administrarOk && stepValidateInformacionGeneral();
            mark(REPORT_INFORMACION_GENERAL, infoGeneralOk);

            boolean detallesOk = administrarOk && stepValidateDetallesCuenta();
            mark(REPORT_DETALLES_CUENTA, detallesOk);

            boolean tusNegociosOk = administrarOk && stepValidateTusNegocios();
            mark(REPORT_TUS_NEGOCIOS, tusNegociosOk);

            boolean terminosOk = administrarOk
                    && stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones", "08-terminos");
            mark(REPORT_TERMINOS, terminosOk);

            boolean privacidadOk = administrarOk
                    && stepValidateLegalLink("Política de Privacidad", "Política de Privacidad", "09-privacidad");
            mark(REPORT_PRIVACIDAD, privacidadOk);
        } finally {
            writeFinalReport();
        }

        List<String> failedChecks = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : reportStatus.entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) {
                failedChecks.add(entry.getKey());
            }
        }
        Assert.assertTrue("Workflow validations failed: " + failedChecks, failedChecks.isEmpty());
    }

    private boolean stepLoginWithGoogleAndValidateDashboard() {
        try {
            String loginUrl = resolveLoginUrl();
            appPage = context.newPage();
            appPage.navigate(loginUrl);
            waitForUiLoad(appPage);

            Locator loginButton = firstVisible(appPage,
                    textSelector("Sign in with Google"),
                    textSelector("Iniciar sesión con Google"),
                    textSelector("Continuar con Google"),
                    "button:has-text('Google')");

            clickAndWait(loginButton, appPage);

            handleGoogleAccountSelectionIfVisible();
            waitForApplicationPageAfterLogin();

            boolean appVisible = hasVisibleText(appPage, "Negocio") || hasVisibleText(appPage, "Mi Negocio");
            boolean sidebarVisible = appPage.locator("aside").count() > 0 || hasVisibleText(appPage, "Negocio");

            if (!(appVisible && sidebarVisible)) {
                return false;
            }

            screenshot("01-dashboard-loaded", true);
            return true;
        } catch (RuntimeException ex) {
            safeErrorScreenshot("01-login-error");
            return false;
        }
    }

    private boolean stepOpenMiNegocioMenu() {
        try {
            Locator miNegocio = firstVisible(appPage, textSelector("Mi Negocio"));
            clickAndWait(miNegocio, appPage);

            boolean submenuExpanded = hasVisibleText(appPage, "Agregar Negocio") && hasVisibleText(appPage, "Administrar Negocios");
            if (!submenuExpanded) {
                return false;
            }

            screenshot("02-mi-negocio-menu-expanded", true);
            return true;
        } catch (RuntimeException ex) {
            safeErrorScreenshot("02-mi-negocio-menu-error");
            return false;
        }
    }

    private boolean stepValidateAgregarNegocioModal() {
        try {
            Locator agregarNegocio = firstVisible(appPage, textSelector("Agregar Negocio"));
            clickAndWait(agregarNegocio, appPage);

            if (!hasVisibleText(appPage, "Crear Nuevo Negocio")) {
                return false;
            }

            boolean fieldExists = appPage.locator("input[placeholder*='Nombre del Negocio'], input[name*='nombre'], input").count() > 0;
            boolean limitTextVisible = hasVisibleText(appPage, "Tienes 2 de 3 negocios");
            boolean cancelVisible = hasVisibleText(appPage, "Cancelar");
            boolean createVisible = hasVisibleText(appPage, "Crear Negocio");

            if (!(fieldExists && limitTextVisible && cancelVisible && createVisible)) {
                return false;
            }

            screenshot("03-agregar-negocio-modal", true);

            Locator nameField = firstVisible(appPage,
                    "input[placeholder*='Nombre del Negocio']",
                    "input[name*='nombre']",
                    "input");
            nameField.fill("Negocio Prueba Automatización");
            clickAndWait(firstVisible(appPage, textSelector("Cancelar")), appPage);
            return true;
        } catch (RuntimeException ex) {
            safeErrorScreenshot("03-agregar-modal-error");
            return false;
        }
    }

    private boolean stepOpenAdministrarNegocios() {
        try {
            if (!hasVisibleText(appPage, "Administrar Negocios")) {
                clickAndWait(firstVisible(appPage, textSelector("Mi Negocio")), appPage);
            }

            clickAndWait(firstVisible(appPage, textSelector("Administrar Negocios")), appPage);
            waitForUiLoad(appPage);

            boolean hasInfo = hasVisibleText(appPage, "Información General");
            boolean hasAccountDetails = hasVisibleText(appPage, "Detalles de la Cuenta");
            boolean hasBusinesses = hasVisibleText(appPage, "Tus Negocios");
            boolean hasLegal = hasVisibleText(appPage, "Sección Legal");

            if (!(hasInfo && hasAccountDetails && hasBusinesses && hasLegal)) {
                return false;
            }

            screenshot("04-administrar-negocios", true);
            return true;
        } catch (RuntimeException ex) {
            safeErrorScreenshot("04-administrar-negocios-error");
            return false;
        }
    }

    private boolean stepValidateInformacionGeneral() {
        try {
            boolean sectionVisible = hasVisibleText(appPage, "Información General");
            boolean hasEmail = EMAIL_PATTERN.matcher(appPage.content()).find();
            boolean hasNameIndicator = hasAnyVisibleText(appPage, "Nombre", "Usuario", "Perfil", "Name");
            boolean hasBusinessPlan = hasVisibleText(appPage, "BUSINESS PLAN");
            boolean hasCambiarPlan = hasVisibleText(appPage, "Cambiar Plan");
            return sectionVisible && hasEmail && hasNameIndicator && hasBusinessPlan && hasCambiarPlan;
        } catch (RuntimeException ex) {
            safeErrorScreenshot("05-informacion-general-error");
            return false;
        }
    }

    private boolean stepValidateDetallesCuenta() {
        try {
            return hasVisibleText(appPage, "Cuenta creada")
                    && hasVisibleText(appPage, "Estado activo")
                    && hasVisibleText(appPage, "Idioma seleccionado");
        } catch (RuntimeException ex) {
            safeErrorScreenshot("06-detalles-cuenta-error");
            return false;
        }
    }

    private boolean stepValidateTusNegocios() {
        try {
            boolean hasSection = hasVisibleText(appPage, "Tus Negocios");
            boolean hasButton = hasVisibleText(appPage, "Agregar Negocio");
            boolean hasLimitText = hasVisibleText(appPage, "Tienes 2 de 3 negocios");
            boolean listVisible = appPage.locator("text=/Negocio/i").count() > 0;
            return hasSection && hasButton && hasLimitText && listVisible;
        } catch (RuntimeException ex) {
            safeErrorScreenshot("07-tus-negocios-error");
            return false;
        }
    }

    private boolean stepValidateLegalLink(String linkText, String headingText, String screenshotName) {
        try {
            Page legalPage = openLegalPage(linkText);
            waitForUiLoad(legalPage);

            boolean headingVisible = hasVisibleText(legalPage, headingText);
            String bodyText = legalPage.locator("body").first().innerText();
            boolean legalTextVisible = bodyText != null && bodyText.trim().length() > 120;

            if (!(headingVisible && legalTextVisible)) {
                return false;
            }

            screenshot(legalPage, screenshotName, true);
            legalUrls.put(linkText, legalPage.url());

            cleanupLegalPage(legalPage);
            return true;
        } catch (RuntimeException ex) {
            safeErrorScreenshot(screenshotName + "-error");
            return false;
        }
    }

    private Page openLegalPage(String linkText) {
        Locator legalLink = firstVisible(appPage, textSelector(linkText));
        int pagesBefore = context.pages().size();
        clickAndWait(legalLink, appPage);

        long deadline = System.currentTimeMillis() + 8_000;
        while (System.currentTimeMillis() < deadline) {
            List<Page> pages = context.pages();
            if (pages.size() > pagesBefore) {
                Page newest = pages.get(pages.size() - 1);
                newest.bringToFront();
                return newest;
            }
            appPage.waitForTimeout(200);
        }
        return appPage;
    }

    private void cleanupLegalPage(Page legalPage) {
        if (legalPage != appPage) {
            legalPage.close();
            appPage.bringToFront();
            waitForUiLoad(appPage);
            return;
        }

        try {
            appPage.goBack();
            waitForUiLoad(appPage);
        } catch (PlaywrightException ignored) {
            appPage.bringToFront();
        }
    }

    private void handleGoogleAccountSelectionIfVisible() {
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline) {
            for (Page candidate : context.pages()) {
                Locator account = candidate.locator(textSelector(GOOGLE_ACCOUNT_EMAIL)).first();
                if (isVisible(account, 500)) {
                    clickAndWait(account, candidate);
                    waitForUiLoad(candidate);
                    return;
                }
            }
            appPage.waitForTimeout(300);
        }
    }

    private void waitForApplicationPageAfterLogin() {
        long deadline = System.currentTimeMillis() + 45_000;
        while (System.currentTimeMillis() < deadline) {
            for (Page candidate : context.pages()) {
                if (hasVisibleText(candidate, "Negocio") || hasVisibleText(candidate, "Mi Negocio")) {
                    appPage = candidate;
                    appPage.bringToFront();
                    waitForUiLoad(appPage);
                    return;
                }
            }
            appPage.waitForTimeout(500);
        }
    }

    private boolean hasAnyVisibleText(Page page, String... texts) {
        for (String text : texts) {
            if (hasVisibleText(page, text)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasVisibleText(Page page, String text) {
        return isVisible(page.locator(textSelector(text)).first(), 2_000);
    }

    private boolean isVisible(Locator locator, double timeoutMs) {
        try {
            locator.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(timeoutMs));
            return true;
        } catch (PlaywrightException ex) {
            return false;
        }
    }

    private Locator firstVisible(Page page, String... selectors) {
        for (String selector : selectors) {
            Locator locator = page.locator(selector).first();
            if (isVisible(locator, 2_000)) {
                return locator;
            }
        }
        throw new IllegalStateException("None of the selectors were visible: " + String.join(", ", selectors));
    }

    private void clickAndWait(Locator locator, Page page) {
        locator.click();
        waitForUiLoad(page);
    }

    private void waitForUiLoad(Page page) {
        try {
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        } catch (PlaywrightException ignored) {
            // Some SPA transitions don't trigger full document events.
        }
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE);
        } catch (PlaywrightException ignored) {
            // Some pages keep websockets open; fallback sleep keeps flow stable.
        }
        page.waitForTimeout(700);
    }

    private void screenshot(String fileName, boolean fullPage) {
        screenshot(appPage, fileName, fullPage);
    }

    private void screenshot(Page page, String fileName, boolean fullPage) {
        Path path = evidenceDir.resolve(fileName + ".png");
        page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
    }

    private void safeErrorScreenshot(String fileName) {
        if (appPage == null) {
            return;
        }
        try {
            screenshot(fileName + "-" + Instant.now().toEpochMilli(), true);
        } catch (RuntimeException ignored) {
            // Ignore screenshot failures while already handling another failure.
        }
    }

    private String resolveLoginUrl() {
        String loginUrl = firstNonBlank(System.getProperty("saleads.loginUrl"), System.getenv("SALEADS_LOGIN_URL"));
        if (loginUrl == null) {
            throw new IllegalStateException("Missing login URL. Set -Dsaleads.loginUrl or SALEADS_LOGIN_URL.");
        }
        return loginUrl;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.trim().isEmpty()) {
            return first.trim();
        }
        if (second != null && !second.trim().isEmpty()) {
            return second.trim();
        }
        return null;
    }

    private String textSelector(String text) {
        String escaped = text.replace("\\", "\\\\").replace("\"", "\\\"");
        return "text=\"" + escaped + "\"";
    }

    private void mark(String field, boolean passed) {
        reportStatus.put(field, passed);
    }

    private void writeFinalReport() throws IOException {
        StringBuilder builder = new StringBuilder();
        builder.append("saleads_mi_negocio_full_test\n");
        builder.append("Generated at: ").append(Instant.now()).append("\n\n");

        for (Map.Entry<String, Boolean> entry : reportStatus.entrySet()) {
            builder.append(entry.getKey()).append(": ").append(Boolean.TRUE.equals(entry.getValue()) ? "PASS" : "FAIL").append("\n");
        }

        builder.append("\nCaptured URLs:\n");
        if (legalUrls.isEmpty()) {
            builder.append("- none\n");
        } else {
            for (Map.Entry<String, String> entry : legalUrls.entrySet()) {
                builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }

        Path reportPath = evidenceDir.resolve("10-final-report.txt");
        Files.writeString(reportPath, builder.toString());
    }
}
