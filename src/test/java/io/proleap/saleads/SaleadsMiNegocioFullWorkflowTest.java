package io.proleap.saleads;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * End-to-end browser test for SaleADS "Mi Negocio" workflow.
 *
 * <p>This test is environment agnostic: pass the login page URL through either:
 * <ul>
 *   <li>-Dsaleads.login.url=...</li>
 *   <li>SALEADS_LOGIN_URL=...</li>
 * </ul>
 * It does not hardcode any domain.
 */
public class SaleadsMiNegocioFullWorkflowTest {

    private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    @Test
    public void saleadsMiNegocioFullWorkflow() throws IOException {
        final String loginUrl = readConfig("saleads.login.url", "SALEADS_LOGIN_URL");
        Assume.assumeTrue(
                "Set -Dsaleads.login.url or SALEADS_LOGIN_URL to run the SaleADS E2E workflow test.",
                loginUrl != null && !loginUrl.isBlank()
        );

        final boolean headless = Boolean.parseBoolean(readConfigWithDefault("saleads.headless", "SALEADS_HEADLESS", "true"));
        final Path artifactDir = createArtifactDirectory();
        final LinkedHashMap<String, Boolean> report = createReportSkeleton();
        final StringBuilder details = new StringBuilder();
        String termsUrl = "N/A";
        String privacyUrl = "N/A";

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
            BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
            context.setDefaultTimeout(15000);
            Page page = context.newPage();
            page.navigate(loginUrl);
            waitForUiLoad(page);

            // Step 1: Login with Google
            boolean loginClicked = clickUsingVisibleText(page,
                    "Sign in with Google",
                    "Iniciar sesión con Google",
                    "Iniciar sesion con Google",
                    "Continuar con Google",
                    "Login with Google");
            if (loginClicked) {
                clickIfVisible(page, ACCOUNT_EMAIL);
                waitForUiLoad(page);
            }

            boolean sidebarVisible = waitForVisibleText(page, "Negocio", 30000)
                    || waitForVisibleText(page, "Mi Negocio", 30000);
            boolean dashboardVisible = sidebarVisible || hasAnyVisibleText(page, "Dashboard", "Inicio", "Panel");
            boolean loginPass = loginClicked && dashboardVisible && sidebarVisible;
            report.put("Login", loginPass);
            captureScreenshot(page, artifactDir, "01-dashboard-loaded.png", true);
            appendDetail(details, "Login", loginPass);

            // Step 2: Open Mi Negocio menu
            clickIfVisible(page, "Negocio");
            clickUsingVisibleText(page, "Mi Negocio");
            waitForUiLoad(page);

            boolean agregarNegocioVisible = hasAnyVisibleText(page, "Agregar Negocio");
            boolean administrarNegociosVisible = hasAnyVisibleText(page, "Administrar Negocios");
            boolean menuPass = agregarNegocioVisible && administrarNegociosVisible;
            report.put("Mi Negocio menu", menuPass);
            captureScreenshot(page, artifactDir, "02-mi-negocio-expanded.png", false);
            appendDetail(details, "Mi Negocio menu", menuPass);

            // Step 3: Validate Agregar Negocio modal
            clickUsingVisibleText(page, "Agregar Negocio");
            waitForUiLoad(page);

            boolean modalTitle = waitForVisibleText(page, "Crear Nuevo Negocio", 15000);
            boolean businessNameLabel = hasAnyVisibleText(page, "Nombre del Negocio");
            boolean usageText = hasAnyVisibleText(page, "Tienes 2 de 3 negocios");
            boolean cancelButton = hasAnyVisibleText(page, "Cancelar");
            boolean createButton = hasAnyVisibleText(page, "Crear Negocio");

            if (modalTitle && businessNameLabel) {
                Locator input = page.locator("input[name*='nombre' i], input[placeholder*='Nombre' i], input");
                if (input.count() > 0) {
                    input.first().fill("Negocio Prueba Automatizacion");
                }
                clickIfVisible(page, "Cancelar");
                waitForUiLoad(page);
            }

            boolean modalPass = modalTitle && businessNameLabel && usageText && cancelButton && createButton;
            report.put("Agregar Negocio modal", modalPass);
            captureScreenshot(page, artifactDir, "03-agregar-negocio-modal.png", false);
            appendDetail(details, "Agregar Negocio modal", modalPass);

            // Step 4: Open Administrar Negocios
            clickIfVisible(page, "Mi Negocio");
            clickUsingVisibleText(page, "Administrar Negocios");
            waitForUiLoad(page);

            boolean infoGeneralSection = hasAnyVisibleText(page, "Información General", "Informacion General");
            boolean detallesSection = hasAnyVisibleText(page, "Detalles de la Cuenta");
            boolean negociosSection = hasAnyVisibleText(page, "Tus Negocios");
            boolean legalSection = hasAnyVisibleText(page, "Sección Legal", "Seccion Legal");
            boolean administrarPass = infoGeneralSection && detallesSection && negociosSection && legalSection;
            report.put("Administrar Negocios view", administrarPass);
            captureScreenshot(page, artifactDir, "04-administrar-negocios-page.png", true);
            appendDetail(details, "Administrar Negocios view", administrarPass);

            // Step 5: Validate Informacion General
            String pageText = safeInnerText(page.locator("body"));
            boolean hasUserEmail = EMAIL_PATTERN.matcher(pageText).find();
            boolean hasUserName = hasAnyVisibleText(page, "Juan", "Barbier", "Garzon")
                    || hasAnyVisibleText(page, "Nombre")
                    || hasUserEmail;
            boolean hasBusinessPlan = hasAnyVisibleText(page, "BUSINESS PLAN");
            boolean hasCambiarPlan = hasAnyVisibleText(page, "Cambiar Plan");
            boolean infoGeneralPass = hasUserName && hasUserEmail && hasBusinessPlan && hasCambiarPlan;
            report.put("Información General", infoGeneralPass);
            appendDetail(details, "Información General", infoGeneralPass);

            // Step 6: Validate Detalles de la Cuenta
            boolean cuentaCreada = hasAnyVisibleText(page, "Cuenta creada");
            boolean estadoActivo = hasAnyVisibleText(page, "Estado activo");
            boolean idiomaSeleccionado = hasAnyVisibleText(page, "Idioma seleccionado");
            boolean detallesPass = cuentaCreada && estadoActivo && idiomaSeleccionado;
            report.put("Detalles de la Cuenta", detallesPass);
            appendDetail(details, "Detalles de la Cuenta", detallesPass);

            // Step 7: Validate Tus Negocios
            boolean businessListVisible = hasAnyVisibleText(page, "Tus Negocios");
            boolean addBusinessButtonVisible = hasAnyVisibleText(page, "Agregar Negocio");
            boolean usageVisible = hasAnyVisibleText(page, "Tienes 2 de 3 negocios");
            boolean tusNegociosPass = businessListVisible && addBusinessButtonVisible && usageVisible;
            report.put("Tus Negocios", tusNegociosPass);
            appendDetail(details, "Tus Negocios", tusNegociosPass);

            // Step 8: Validate Terminos y Condiciones
            LegalValidationResult termsResult = validateLegalLink(context, page, artifactDir,
                    "Términos y Condiciones", "Terminos y Condiciones", "08-terminos-y-condiciones.png");
            report.put("Términos y Condiciones", termsResult.pass);
            termsUrl = termsResult.finalUrl;
            appendDetail(details, "Términos y Condiciones", termsResult.pass);

            // Step 9: Validate Politica de Privacidad
            LegalValidationResult privacyResult = validateLegalLink(context, page, artifactDir,
                    "Política de Privacidad", "Politica de Privacidad", "09-politica-de-privacidad.png");
            report.put("Política de Privacidad", privacyResult.pass);
            privacyUrl = privacyResult.finalUrl;
            appendDetail(details, "Política de Privacidad", privacyResult.pass);
        } finally {
            writeReport(artifactDir, report, details, termsUrl, privacyUrl);
        }

        boolean allPassed = report.values().stream().allMatch(Boolean::booleanValue);
        Assert.assertTrue("SaleADS Mi Negocio workflow failed.\n" + details, allPassed);
    }

    private static LinkedHashMap<String, Boolean> createReportSkeleton() {
        LinkedHashMap<String, Boolean> report = new LinkedHashMap<>();
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

    private static Path createArtifactDirectory() throws IOException {
        String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path artifactDir = Paths.get("target", "saleads-evidence", runId);
        Files.createDirectories(artifactDir);
        return artifactDir;
    }

    private static String readConfig(String propertyName, String envName) {
        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return null;
    }

    private static String readConfigWithDefault(String propertyName, String envName, String defaultValue) {
        String value = readConfig(propertyName, envName);
        return value == null ? defaultValue : value;
    }

    private static boolean clickUsingVisibleText(Page page, String... texts) {
        for (String text : texts) {
            Locator exact = page.getByText(text, new Page.GetByTextOptions().setExact(true));
            if (isVisible(exact)) {
                exact.first().click();
                waitForUiLoad(page);
                return true;
            }
            Locator partial = page.getByText(text);
            if (isVisible(partial)) {
                partial.first().click();
                waitForUiLoad(page);
                return true;
            }
        }
        return false;
    }

    private static boolean clickIfVisible(Page page, String text) {
        return clickUsingVisibleText(page, text);
    }

    private static boolean waitForVisibleText(Page page, String text, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (hasAnyVisibleText(page, text)) {
                return true;
            }
            page.waitForTimeout(250);
        }
        return false;
    }

    private static boolean hasAnyVisibleText(Page page, String... texts) {
        for (String text : texts) {
            Locator exact = page.getByText(text, new Page.GetByTextOptions().setExact(true));
            if (isVisible(exact)) {
                return true;
            }
            Locator partial = page.getByText(text);
            if (isVisible(partial)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isVisible(Locator locator) {
        return locator.count() > 0 && locator.first().isVisible();
    }

    private static void waitForUiLoad(Page page) {
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE);
        } catch (RuntimeException ignored) {
            // Some pages never reach network idle because of polling; DOM content loaded is still enough.
        }
        page.waitForTimeout(300);
    }

    private static void captureScreenshot(Page page, Path artifactDir, String filename, boolean fullPage) {
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(artifactDir.resolve(filename))
                .setFullPage(fullPage));
    }

    private static LegalValidationResult validateLegalLink(
            BrowserContext context,
            Page applicationPage,
            Path artifactDir,
            String primaryText,
            String fallbackText,
            String screenshotName
    ) {
        List<Page> beforePages = context.pages();
        int beforeCount = beforePages.size();

        boolean clicked = clickUsingVisibleText(applicationPage, primaryText, fallbackText);
        if (!clicked) {
            return new LegalValidationResult(false, "N/A");
        }

        Page activePage = applicationPage;
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (context.pages().size() > beforeCount) {
                activePage = context.pages().get(context.pages().size() - 1);
                break;
            }
            applicationPage.waitForTimeout(200);
        }

        activePage.bringToFront();
        waitForUiLoad(activePage);
        boolean headingVisible = hasAnyVisibleText(activePage, primaryText, fallbackText);
        boolean contentVisible = safeInnerText(activePage.locator("body")).trim().length() > 120;
        String finalUrl = activePage.url();
        captureScreenshot(activePage, artifactDir, screenshotName, true);

        if (activePage != applicationPage) {
            activePage.close();
            applicationPage.bringToFront();
            waitForUiLoad(applicationPage);
        } else {
            try {
                applicationPage.goBack();
                waitForUiLoad(applicationPage);
            } catch (RuntimeException ignored) {
                // Ignore if browser history is not available and continue in current tab.
            }
        }

        return new LegalValidationResult(headingVisible && contentVisible, finalUrl);
    }

    private static String safeInnerText(Locator locator) {
        try {
            return locator.first().innerText();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static void writeReport(
            Path artifactDir,
            Map<String, Boolean> report,
            StringBuilder details,
            String termsUrl,
            String privacyUrl
    ) throws IOException {
        StringBuilder reportOutput = new StringBuilder();
        reportOutput.append("SaleADS Mi Negocio Full Test Report").append(System.lineSeparator());
        reportOutput.append("Artifacts: ").append(artifactDir.toAbsolutePath()).append(System.lineSeparator());
        reportOutput.append(System.lineSeparator());
        for (Map.Entry<String, Boolean> entry : report.entrySet()) {
            reportOutput.append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue() ? "PASS" : "FAIL")
                    .append(System.lineSeparator());
        }
        reportOutput.append(System.lineSeparator());
        reportOutput.append("Terminos y Condiciones URL: ").append(termsUrl).append(System.lineSeparator());
        reportOutput.append("Politica de Privacidad URL: ").append(privacyUrl).append(System.lineSeparator());
        reportOutput.append(System.lineSeparator());
        reportOutput.append("Details").append(System.lineSeparator()).append(details);

        Path reportFile = artifactDir.resolve("final-report.txt");
        Files.writeString(reportFile, reportOutput.toString());
        System.out.println(reportOutput);
        System.out.println("Report file: " + reportFile.toAbsolutePath());
    }

    private static void appendDetail(StringBuilder details, String stepName, boolean pass) {
        details.append(stepName)
                .append(" -> ")
                .append(pass ? "PASS" : "FAIL")
                .append(System.lineSeparator());
    }

    private static final class LegalValidationResult {
        private final boolean pass;
        private final String finalUrl;

        private LegalValidationResult(boolean pass, String finalUrl) {
            this.pass = pass;
            this.finalUrl = finalUrl;
        }
    }
}
