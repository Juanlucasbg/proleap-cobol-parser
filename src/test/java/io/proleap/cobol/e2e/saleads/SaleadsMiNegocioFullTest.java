package io.proleap.cobol.e2e.saleads;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullTest {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	@Test
	public void saleads_mi_negocio_full_test() throws IOException {
		final String loginUrl = firstNonBlank(System.getenv("SALEADS_LOGIN_URL"), System.getProperty("saleads.login.url"));
		Assume.assumeTrue("Set SALEADS_LOGIN_URL or -Dsaleads.login.url to execute this E2E workflow.",
				loginUrl != null && !loginUrl.isBlank());

		final boolean headless = Boolean.parseBoolean(
				firstNonBlank(System.getenv("SALEADS_HEADLESS"), System.getProperty("saleads.headless"), "true"));
		final Path evidenceDir = createEvidenceDir();

		final Map<String, Boolean> report = initializeReport();
		final Map<String, String> legalUrls = new LinkedHashMap<>();
		final List<String> failures = new ArrayList<>();

		try (final Playwright playwright = Playwright.create();
				final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
				final BrowserContext context = browser.newContext()) {
			final Page page = context.newPage();
			page.setDefaultTimeout(20_000);
			page.navigate(loginUrl);
			waitForUiLoad(page);

			runStep("Login", report, failures, () -> {
				loginWithGoogle(context, page);
				assertMainInterfaceLoaded(page);
				screenshot(page, evidenceDir.resolve("01-dashboard-loaded.png"), true);
			});

			runStep("Mi Negocio menu", report, failures, () -> {
				openMiNegocioMenu(page);
				assertVisibleText(page, "Agregar Negocio");
				assertVisibleText(page, "Administrar Negocios");
				screenshot(page, evidenceDir.resolve("02-mi-negocio-menu-expanded.png"), false);
			});

			runStep("Agregar Negocio modal", report, failures, () -> {
				clickAnyText(page, List.of("Agregar Negocio"));
				waitForUiLoad(page);

				assertVisibleText(page, "Crear Nuevo Negocio");
				assertVisibleText(page, "Nombre del Negocio");
				assertVisibleText(page, "Tienes 2 de 3 negocios");
				assertVisibleText(page, "Cancelar");
				assertVisibleText(page, "Crear Negocio");

				fillNombreNegocio(page, "Negocio Prueba Automatización");
				screenshot(page, evidenceDir.resolve("03-agregar-negocio-modal.png"), false);
				clickAnyText(page, List.of("Cancelar"));
				waitForUiLoad(page);
			});

			runStep("Administrar Negocios view", report, failures, () -> {
				openMiNegocioMenu(page);
				clickAnyText(page, List.of("Administrar Negocios"));
				waitForUiLoad(page);

				assertVisibleText(page, "Información General");
				assertVisibleText(page, "Detalles de la Cuenta");
				assertVisibleText(page, "Tus Negocios");
				assertVisibleText(page, "Sección Legal");
				screenshot(page, evidenceDir.resolve("04-administrar-negocios-full-page.png"), true);
			});

			runStep("Información General", report, failures, () -> {
				final String sectionText = extractSectionText(page, "Información General");
				Assert.assertTrue("User name is not visible in 'Información General'.", containsLikelyUserName(sectionText));
				Assert.assertTrue("User email is not visible in 'Información General'.", containsEmail(sectionText));
				assertVisibleText(page, "BUSINESS PLAN");
				assertVisibleText(page, "Cambiar Plan");
			});

			runStep("Detalles de la Cuenta", report, failures, () -> {
				assertVisibleText(page, "Cuenta creada");
				assertVisibleText(page, "Estado activo");
				assertVisibleText(page, "Idioma seleccionado");
			});

			runStep("Tus Negocios", report, failures, () -> {
				final String sectionText = extractSectionText(page, "Tus Negocios");
				final boolean listVisible = hasBusinessList(page) || sectionText.toLowerCase().contains("negocio");
				Assert.assertTrue("Business list is not visible in 'Tus Negocios'.", listVisible);
				assertVisibleText(page, "Agregar Negocio");
				assertVisibleText(page, "Tienes 2 de 3 negocios");
			});

			runStep("Términos y Condiciones", report, failures, () -> {
				final String finalUrl = openLegalLinkAndValidate(context, page, "Términos y Condiciones",
						"Términos y Condiciones", evidenceDir.resolve("05-terminos-y-condiciones.png"));
				legalUrls.put("Términos y Condiciones", finalUrl);
			});

			runStep("Política de Privacidad", report, failures, () -> {
				final String finalUrl = openLegalLinkAndValidate(context, page, "Política de Privacidad",
						"Política de Privacidad", evidenceDir.resolve("06-politica-de-privacidad.png"));
				legalUrls.put("Política de Privacidad", finalUrl);
			});
		} finally {
			writeFinalReport(evidenceDir, report, legalUrls, loginUrl, failures);
		}

		if (!failures.isEmpty()) {
			Assert.fail("SaleADS Mi Negocio workflow failed validations:\n - " + String.join("\n - ", failures));
		}
	}

	private void loginWithGoogle(final BrowserContext context, final Page appPage) {
		final List<String> loginTexts = List.of("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Ingresar con Google", "Login with Google");
		clickAnyText(appPage, loginTexts);
		waitForUiLoad(appPage);

		selectGoogleAccountIfVisible(appPage);
		for (final Page candidatePage : context.pages()) {
			if (candidatePage != appPage && isGoogleAuthPage(candidatePage)) {
				selectGoogleAccountIfVisible(candidatePage);
			}
		}

		waitForUiLoad(appPage);
	}

	private void assertMainInterfaceLoaded(final Page page) {
		final boolean sidebarVisible = isAnyVisible(page, List.of("Negocio", "Mi Negocio"))
				|| (page.locator("aside").count() > 0 && page.locator("aside").first().isVisible());
		Assert.assertTrue("Main application interface or left sidebar was not visible after login.", sidebarVisible);
	}

	private void openMiNegocioMenu(final Page page) {
		clickAnyText(page, List.of("Negocio"));
		waitForUiLoad(page);
		clickAnyText(page, List.of("Mi Negocio"));
		waitForUiLoad(page);
	}

	private String openLegalLinkAndValidate(final BrowserContext context, final Page appPage, final String linkText,
			final String headingText, final Path screenshotPath) {
		openMiNegocioMenu(appPage);
		final int pagesBefore = context.pages().size();
		clickAnyText(appPage, List.of(linkText));
		waitForUiLoad(appPage);

		final Page legalPage = waitForNewPage(context, pagesBefore, appPage);
		final boolean openedInNewTab = legalPage != appPage;
		waitForUiLoad(legalPage);

		assertVisibleText(legalPage, headingText);
		final String legalBodyText = safeTextContent(legalPage, "body");
		Assert.assertTrue("Legal content is not visible for '" + headingText + "'.",
				legalBodyText != null && legalBodyText.trim().length() > 100);
		screenshot(legalPage, screenshotPath, true);

		final String finalUrl = legalPage.url();
		if (openedInNewTab) {
			legalPage.close();
			appPage.bringToFront();
			waitForUiLoad(appPage);
		} else {
			appPage.goBack();
			waitForUiLoad(appPage);
		}

		return finalUrl;
	}

	private void clickAnyText(final Page page, final List<String> texts) {
		for (final String text : texts) {
			final Locator locator = page.locator("text=" + text);
			if (locator.count() > 0) {
				for (int i = 0; i < Math.min(locator.count(), 5); i++) {
					final Locator candidate = locator.nth(i);
					if (candidate.isVisible()) {
						candidate.click();
						waitForUiLoad(page);
						return;
					}
				}
				locator.first().click();
				waitForUiLoad(page);
				return;
			}
		}

		throw new AssertionError("Could not find a clickable element with any text in: " + texts);
	}

	private void assertVisibleText(final Page page, final String text) {
		final Locator locator = page.locator("text=" + text);
		Assert.assertTrue("Expected text not visible: " + text, isLocatorVisible(locator));
	}

	private boolean isAnyVisible(final Page page, final List<String> texts) {
		for (final String text : texts) {
			if (isLocatorVisible(page.locator("text=" + text))) {
				return true;
			}
		}
		return false;
	}

	private boolean isLocatorVisible(final Locator locator) {
		if (locator.count() == 0) {
			return false;
		}
		for (int i = 0; i < Math.min(locator.count(), 8); i++) {
			if (locator.nth(i).isVisible()) {
				return true;
			}
		}
		return false;
	}

	private void fillNombreNegocio(final Page page, final String value) {
		final Locator field = page.locator(
				"input[placeholder*='Nombre del Negocio'], input[name*='negocio' i], input[id*='negocio' i], textarea[placeholder*='Nombre del Negocio']");
		if (field.count() > 0) {
			field.first().click();
			field.first().fill(value);
		}
	}

	private String extractSectionText(final Page page, final String sectionTitle) {
		final String selector = "section:has-text(\"" + sectionTitle + "\"), div:has-text(\"" + sectionTitle
				+ "\"), article:has-text(\"" + sectionTitle + "\")";
		final Locator section = page.locator(selector);
		if (section.count() > 0) {
			final String text = section.first().textContent();
			if (text != null && !text.isBlank()) {
				return text;
			}
		}
		return safeTextContent(page, "body");
	}

	private boolean containsLikelyUserName(final String text) {
		if (text == null || text.isBlank()) {
			return false;
		}

		final String[] lines = text.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}
			if (line.toLowerCase().contains("información general") || line.toLowerCase().contains("business plan")
					|| line.toLowerCase().contains("cambiar plan") || line.toLowerCase().contains("cuenta")
					|| line.toLowerCase().contains("idioma")) {
				continue;
			}
			if (line.contains("@")) {
				continue;
			}
			if (line.matches(".*\\p{L}.*") && line.length() >= 4) {
				return true;
			}
		}

		return false;
	}

	private boolean containsEmail(final String text) {
		return text != null && EMAIL_PATTERN.matcher(text).find();
	}

	private boolean hasBusinessList(final Page page) {
		final Locator rows = page.locator(
				"section:has-text(\"Tus Negocios\") li, section:has-text(\"Tus Negocios\") tr, section:has-text(\"Tus Negocios\") [role='row'], div:has-text(\"Tus Negocios\") li, div:has-text(\"Tus Negocios\") tr, div:has-text(\"Tus Negocios\") [role='row']");
		return rows.count() > 0;
	}

	private void selectGoogleAccountIfVisible(final Page page) {
		final Locator accountLocator = page.locator("text=" + GOOGLE_ACCOUNT_EMAIL);
		if (isLocatorVisible(accountLocator)) {
			accountLocator.first().click();
			waitForUiLoad(page);
		}
	}

	private boolean isGoogleAuthPage(final Page page) {
		final String url = page.url();
		return url != null && url.contains("accounts.google.com");
	}

	private Page waitForNewPage(final BrowserContext context, final int pagesBefore, final Page fallbackPage) {
		for (int i = 0; i < 20; i++) {
			if (context.pages().size() > pagesBefore) {
				return context.pages().get(context.pages().size() - 1);
			}
			sleep(250);
		}
		return fallbackPage;
	}

	private void waitForUiLoad(final Page page) {
		try {
			page.waitForLoadState();
		} catch (final RuntimeException ignored) {
			// Some transitions are DOM-only; timeout here should not fail the whole flow.
		}
		page.waitForTimeout(800);
	}

	private void screenshot(final Page page, final Path screenshotPath, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
	}

	private String safeTextContent(final Page page, final String selector) {
		try {
			final String text = page.textContent(selector);
			return text == null ? "" : text;
		} catch (final RuntimeException ignored) {
			return "";
		}
	}

	private Map<String, Boolean> initializeReport() {
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

	private void writeFinalReport(final Path evidenceDir, final Map<String, Boolean> report,
			final Map<String, String> legalUrls, final String loginUrl, final List<String> failures) throws IOException {
		final Path reportFile = evidenceDir.resolve("final-report.txt");
		final StringBuilder sb = new StringBuilder();

		sb.append("Test: saleads_mi_negocio_full_test").append(System.lineSeparator());
		sb.append("Login URL: ").append(loginUrl).append(System.lineSeparator());
		sb.append(System.lineSeparator());
		sb.append("Validation Results").append(System.lineSeparator());
		sb.append("------------------").append(System.lineSeparator());
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			sb.append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL").append(System.lineSeparator());
		}

		sb.append(System.lineSeparator());
		sb.append("Captured URLs").append(System.lineSeparator());
		sb.append("-------------").append(System.lineSeparator());
		sb.append("Términos y Condiciones: ")
				.append(legalUrls.getOrDefault("Términos y Condiciones", "N/A"))
				.append(System.lineSeparator());
		sb.append("Política de Privacidad: ")
				.append(legalUrls.getOrDefault("Política de Privacidad", "N/A"))
				.append(System.lineSeparator());

		if (!failures.isEmpty()) {
			sb.append(System.lineSeparator());
			sb.append("Failures").append(System.lineSeparator());
			sb.append("--------").append(System.lineSeparator());
			for (final String failure : failures) {
				sb.append("- ").append(failure).append(System.lineSeparator());
			}
		}

		Files.writeString(reportFile, sb.toString(), StandardCharsets.UTF_8);
	}

	private void runStep(final String reportField, final Map<String, Boolean> report, final List<String> failures,
			final StepAction action) {
		try {
			action.run();
			report.put(reportField, true);
		} catch (final Throwable throwable) {
			report.put(reportField, false);
			failures.add(reportField + " -> " + throwable.getMessage());
		}
	}

	private Path createEvidenceDir() throws IOException {
		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		final Path evidenceDir = Path.of("target", "saleads-evidence", timestamp);
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private void sleep(final long ms) {
		try {
			Thread.sleep(ms);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while waiting for page transitions.", interruptedException);
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
