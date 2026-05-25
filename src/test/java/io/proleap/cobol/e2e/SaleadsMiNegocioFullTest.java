package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;

public class SaleadsMiNegocioFullTest {

	private static final String FIELD_LOGIN = "Login";
	private static final String FIELD_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String FIELD_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String FIELD_ADMINISTRAR_VIEW = "Administrar Negocios view";
	private static final String FIELD_INFO_GENERAL = "Información General";
	private static final String FIELD_DETALLES = "Detalles de la Cuenta";
	private static final String FIELD_TUS_NEGOCIOS = "Tus Negocios";
	private static final String FIELD_TERMINOS = "Términos y Condiciones";
	private static final String FIELD_PRIVACIDAD = "Política de Privacidad";

	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final DateTimeFormatter EVIDENCE_FOLDER_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final Map<String, String> report = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();

	private Path evidenceDir;
	private String termsFinalUrl = "N/A";
	private String privacyFinalUrl = "N/A";

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		Assume.assumeTrue("Enable with -Dsaleads.e2e=true.",
				Boolean.parseBoolean(config("saleads.e2e", "SALEADS_E2E", "false")));

		final String startUrl = firstNonBlank(System.getenv("SALEADS_START_URL"), System.getProperty("saleads.start.url"));
		Assume.assumeTrue("Provide SALEADS_START_URL or -Dsaleads.start.url.", startUrl != null && !startUrl.isBlank());

		final String googleAccount = config("saleads.google.email", "SALEADS_GOOGLE_EMAIL",
				"juanlucasbarbiergarzon@gmail.com");
		final String expectedUserEmail = config("saleads.expected.email", "SALEADS_EXPECTED_USER_EMAIL", googleAccount);
		final String expectedUserName = firstNonBlank(System.getenv("SALEADS_EXPECTED_USER_NAME"),
				System.getProperty("saleads.expected.name"));

		initializeReport();
		evidenceDir = Paths.get("target", "saleads-evidence", EVIDENCE_FOLDER_TIME.format(LocalDateTime.now()));
		Files.createDirectories(evidenceDir);

		try (Playwright playwright = Playwright.create()) {
			final BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
					.setHeadless(Boolean.parseBoolean(config("saleads.headless", "SALEADS_HEADLESS", "true")));
			final Browser browser = playwright.chromium().launch(launchOptions);
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1920, 1080));
			final Page appPage = context.newPage();

			appPage.navigate(startUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			waitForUi(appPage);

			runValidation(FIELD_LOGIN, () -> validateLoginWithGoogle(appPage, googleAccount));
			safeScreenshot(appPage, "01-dashboard-loaded.png", false);

			runValidation(FIELD_MI_NEGOCIO_MENU, () -> validateMiNegocioMenu(appPage));
			safeScreenshot(appPage, "02-mi-negocio-expanded.png", false);

			runValidation(FIELD_AGREGAR_MODAL, () -> validateAgregarNegocioModal(appPage));
			safeScreenshot(appPage, "03-agregar-negocio-modal.png", false);

			runValidation(FIELD_ADMINISTRAR_VIEW, () -> validateAdministrarNegociosView(appPage));
			safeScreenshot(appPage, "04-administrar-negocios-view.png", true);

			runValidation(FIELD_INFO_GENERAL, () -> validateInformacionGeneral(appPage, expectedUserName, expectedUserEmail));
			runValidation(FIELD_DETALLES, () -> validateDetallesCuenta(appPage));
			runValidation(FIELD_TUS_NEGOCIOS, () -> validateTusNegocios(appPage));

			runValidation(FIELD_TERMINOS,
					() -> termsFinalUrl = validateLegalDocument(appPage, "Términos y Condiciones", "05-terminos.png"));
			runValidation(FIELD_PRIVACIDAD, () -> privacyFinalUrl = validateLegalDocument(appPage, "Política de Privacidad",
					"06-politica-privacidad.png"));
		} finally {
			writeFinalReport();
		}

		final boolean hasFailures = report.values().stream().anyMatch(status -> !"PASS".equals(status));
		if (hasFailures) {
			Assert.fail("SaleADS workflow validation has failures. See report at: " + evidenceDir.resolve("final-report.txt"));
		}
	}

	private void validateLoginWithGoogle(final Page appPage, final String googleAccount) {
		final Locator loginButton = requireVisible(appPage, "Google login button",
				"text=/Sign in with Google|Iniciar sesi[oó]n con Google|Continuar con Google/i", "button:has-text(\"Google\")");
		final Page selectedPage = clickAndDetectNewTab(appPage, loginButton);

		if (isVisible(selectedPage, "text=/" + Pattern.quote(googleAccount) + "/i", 6000)) {
			clickAndWait(selectedPage, selectedPage.locator("text=/" + Pattern.quote(googleAccount) + "/i").first());
		}

		if (selectedPage != appPage) {
			try {
				selectedPage.waitForClose(new Page.WaitForCloseOptions().setTimeout(30000));
			} catch (PlaywrightException ignored) {
				// Some Google flows keep a tab open after OAuth completes.
			}
			appPage.bringToFront();
		}

		waitForUi(appPage);
		requireVisible(appPage, "main application area", "main", "aside, nav");
		requireVisible(appPage, "left sidebar", "text=/Negocio/i", "aside, nav");
	}

	private void validateMiNegocioMenu(final Page page) {
		requireVisible(page, "Negocio section", "text=/Negocio/i");
		final Locator miNegocio = requireVisible(page, "Mi Negocio option", "text=/Mi Negocio/i");
		clickAndWait(page, miNegocio);

		requireVisible(page, "Agregar Negocio submenu", "text=/Agregar Negocio/i");
		requireVisible(page, "Administrar Negocios submenu", "text=/Administrar Negocios/i");
	}

	private void validateAgregarNegocioModal(final Page page) {
		final Locator agregarNegocio = requireVisible(page, "Agregar Negocio action", "text=/Agregar Negocio/i");
		clickAndWait(page, agregarNegocio);

		final Locator modal = requireVisible(page, "Crear Nuevo Negocio modal",
				"[role='dialog']:has-text('Crear Nuevo Negocio')", "[aria-modal='true']:has-text('Crear Nuevo Negocio')",
				".modal:has-text('Crear Nuevo Negocio')");

		requireVisibleWithin(modal, "Nombre del Negocio field label", "text=/Nombre del Negocio/i");
		assertTrue("The modal should contain at least one input field.", modal.locator("input").count() > 0);
		requireVisibleWithin(modal, "business quota text", "text=/Tienes\\s*2\\s*de\\s*3\\s*negocios/i");
		requireVisibleWithin(modal, "Cancelar button", "text=/Cancelar/i");
		requireVisibleWithin(modal, "Crear Negocio button", "text=/Crear Negocio/i");

		final Locator input = modal.locator("input").first();
		input.fill("Negocio Prueba Automatización");
		clickAndWait(page, modal.locator("text=/Cancelar/i").first());
	}

	private void validateAdministrarNegociosView(final Page page) {
		if (!isVisible(page, "text=/Administrar Negocios/i", 3000)) {
			final Locator miNegocio = requireVisible(page, "Mi Negocio option", "text=/Mi Negocio/i");
			clickAndWait(page, miNegocio);
		}

		clickAndWait(page, requireVisible(page, "Administrar Negocios link", "text=/Administrar Negocios/i"));
		requireVisible(page, "Información General section", "text=/Informaci[oó]n General/i");
		requireVisible(page, "Detalles de la Cuenta section", "text=/Detalles de la Cuenta/i");
		requireVisible(page, "Tus Negocios section", "text=/Tus Negocios/i");
		requireVisible(page, "Sección Legal section", "text=/Secci[oó]n Legal/i");
	}

	private void validateInformacionGeneral(final Page page, final String expectedUserName, final String expectedUserEmail) {
		final Locator infoSection = requireVisible(page, "Información General section container",
				"section:has-text('Información General')", "div:has-text('Información General')");

		final String sectionText = infoSection.innerText();
		final String normalizedSectionText = sectionText.toLowerCase(Locale.ROOT);

		if (expectedUserName != null && !expectedUserName.isBlank()) {
			assertTrue("Expected user name is not visible.", normalizedSectionText.contains(expectedUserName.toLowerCase(Locale.ROOT)));
		} else {
			assertTrue("A probable user name should be visible.", containsProbableName(sectionText));
		}

		assertTrue("Expected user email is not visible.",
				normalizedSectionText.contains(expectedUserEmail.toLowerCase(Locale.ROOT)) || EMAIL_PATTERN.matcher(sectionText).find());
		assertTrue("BUSINESS PLAN text should be visible.", normalizedSectionText.contains("business plan"));
		assertTrue("'Cambiar Plan' button should be visible.", normalizedSectionText.contains("cambiar plan"));
	}

	private void validateDetallesCuenta(final Page page) {
		final Locator detailsSection = requireVisible(page, "Detalles de la Cuenta section container",
				"section:has-text('Detalles de la Cuenta')", "div:has-text('Detalles de la Cuenta')");
		final String text = detailsSection.innerText().toLowerCase(Locale.ROOT);
		assertTrue("'Cuenta creada' is missing.", text.contains("cuenta creada"));
		assertTrue("'Estado activo' is missing.", text.contains("estado activo"));
		assertTrue("'Idioma seleccionado' is missing.", text.contains("idioma seleccionado"));
	}

	private void validateTusNegocios(final Page page) {
		final Locator businessesSection = requireVisible(page, "Tus Negocios section container",
				"section:has-text('Tus Negocios')", "div:has-text('Tus Negocios')");
		final String text = businessesSection.innerText().toLowerCase(Locale.ROOT);
		assertTrue("The business list should be visible.", businessesSection.locator("li, table, [role='listitem']").count() > 0
				|| text.contains("negocio"));
		assertTrue("The 'Agregar Negocio' button should be visible.", text.contains("agregar negocio"));
		assertTrue("'Tienes 2 de 3 negocios' should be visible.", text.contains("tienes 2 de 3 negocios"));
	}

	private String validateLegalDocument(final Page appPage, final String linkText, final String screenshotName) {
		final Locator link = requireVisible(appPage, linkText + " link", "text=/" + Pattern.quote(linkText) + "/i");
		final Page legalPage = clickAndDetectNewTab(appPage, link);
		waitForUi(legalPage);

		requireVisible(legalPage, linkText + " heading", "text=/" + Pattern.quote(linkText) + "/i");
		assertTrue("Legal content text should be visible.", legalPage.locator("body").innerText().trim().length() > 120);
		safeScreenshot(legalPage, screenshotName, true);

		final String finalUrl = legalPage.url();

		if (legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
		} else {
			try {
				appPage.goBack(new Page.GoBackOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(10000));
			} catch (PlaywrightException ignored) {
				// Legal documents sometimes open in the same SPA route without browser history.
			}
		}

		waitForUi(appPage);
		return finalUrl;
	}

	private void runValidation(final String field, final CheckedRunnable validation) {
		try {
			validation.run();
			report.put(field, "PASS");
		} catch (Throwable throwable) {
			report.put(field, "FAIL");
			failures.add(field + " -> " + throwable.getMessage());
		}
	}

	private Locator requireVisible(final Page page, final String description, final String... selectors) {
		for (final String selector : selectors) {
			final Locator locator = page.locator(selector).first();
			if (waitForVisible(locator, 15000)) {
				return locator;
			}
		}

		throw new AssertionError("Could not locate visible element for: " + description);
	}

	private void requireVisibleWithin(final Locator container, final String description, final String selector) {
		final Locator locator = container.locator(selector).first();
		if (!waitForVisible(locator, 10000)) {
			throw new AssertionError("Could not locate visible element within container for: " + description);
		}
	}

	private boolean waitForVisible(final Locator locator, final long timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout((double) timeoutMs));
			return true;
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private boolean isVisible(final Page page, final String selector, final long timeoutMs) {
		return waitForVisible(page.locator(selector).first(), timeoutMs);
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.click(new Locator.ClickOptions().setTimeout(20000));
		waitForUi(page);
	}

	private Page clickAndDetectNewTab(final Page appPage, final Locator locator) {
		final int pageCountBefore = appPage.context().pages().size();
		clickAndWait(appPage, locator);

		final long deadline = System.currentTimeMillis() + 6000;
		while (System.currentTimeMillis() < deadline) {
			final List<Page> pages = appPage.context().pages();
			if (pages.size() > pageCountBefore) {
				final Page target = pages.get(pages.size() - 1);
				waitForUi(target);
				return target;
			}

			try {
				Thread.sleep(150);
			} catch (InterruptedException interruptedException) {
				Thread.currentThread().interrupt();
				break;
			}
		}

		return appPage;
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (PlaywrightException ignored) {
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10000));
		} catch (PlaywrightException ignored) {
			// Some SPA transitions never reach NETWORKIDLE because of active sockets.
		}
	}

	private void safeScreenshot(final Page page, final String fileName, final boolean fullPage) {
		try {
			page.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(fileName)).setFullPage(fullPage));
		} catch (PlaywrightException screenshotError) {
			failures.add("Screenshot " + fileName + " -> " + screenshotError.getMessage());
		}
	}

	private void initializeReport() {
		report.put(FIELD_LOGIN, "NOT RUN");
		report.put(FIELD_MI_NEGOCIO_MENU, "NOT RUN");
		report.put(FIELD_AGREGAR_MODAL, "NOT RUN");
		report.put(FIELD_ADMINISTRAR_VIEW, "NOT RUN");
		report.put(FIELD_INFO_GENERAL, "NOT RUN");
		report.put(FIELD_DETALLES, "NOT RUN");
		report.put(FIELD_TUS_NEGOCIOS, "NOT RUN");
		report.put(FIELD_TERMINOS, "NOT RUN");
		report.put(FIELD_PRIVACIDAD, "NOT RUN");
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		builder.append("================================").append(System.lineSeparator()).append(System.lineSeparator());

		for (final Map.Entry<String, String> item : report.entrySet()) {
			builder.append(item.getKey()).append(": ").append(item.getValue()).append(System.lineSeparator());
		}

		builder.append(System.lineSeparator());
		builder.append("Términos y Condiciones URL: ").append(termsFinalUrl).append(System.lineSeparator());
		builder.append("Política de Privacidad URL: ").append(privacyFinalUrl).append(System.lineSeparator());

		if (!failures.isEmpty()) {
			builder.append(System.lineSeparator());
			builder.append("Failure details:").append(System.lineSeparator());
			for (final String failure : failures) {
				builder.append("- ").append(failure).append(System.lineSeparator());
			}
		}

		final Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.writeString(reportPath, builder.toString());
		System.out.println(builder);
	}

	private String config(final String propertyName, final String envName, final String defaultValue) {
		return firstNonBlank(System.getProperty(propertyName), System.getenv(envName), defaultValue);
	}

	private String firstNonBlank(final String... values) {
		if (values == null) {
			return null;
		}

		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}

		return null;
	}

	private boolean containsProbableName(final String text) {
		final String[] lines = text.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}

			final String lower = line.toLowerCase(Locale.ROOT);
			if (lower.contains("información general") || lower.contains("business plan") || lower.contains("cambiar plan")
					|| lower.contains("@") || lower.contains("plan")) {
				continue;
			}

			if (line.matches("[\\p{L}][\\p{L} .'-]{2,}")) {
				return true;
			}
		}

		return false;
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
