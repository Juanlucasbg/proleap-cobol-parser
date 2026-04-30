package io.proleap.cobol.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;

/**
 * Environment-agnostic SaleADS.ai UI validation for the Mi Negocio workflow.
 *
 * <p>Runtime options:
 * <ul>
 *   <li>SALEADS_BASE_URL (optional): navigates to this URL before running.</li>
 *   <li>SALEADS_GOOGLE_EMAIL (optional): Google account to select if selector appears.</li>
 *   <li>SALEADS_HEADLESS (optional, default true): browser headless mode flag.</li>
 * </ul>
 */
public class SaleadsMiNegocioFullTest {

	private static final double DEFAULT_TIMEOUT_MS = 30000;
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");
	private static final String BASE_URL = System.getenv("SALEADS_BASE_URL");
	private static final String GOOGLE_EMAIL = System.getenv().getOrDefault("SALEADS_GOOGLE_EMAIL",
			"juanlucasbarbiergarzon@gmail.com");
	private static final boolean HEADLESS = Boolean
			.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final Map<String, String> report = new LinkedHashMap<>();
		final Map<String, String> details = new LinkedHashMap<>();
		final Map<String, String> legalUrls = new LinkedHashMap<>();
		final Path evidenceDir = createEvidenceDirectory();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(HEADLESS).setTimeout(DEFAULT_TIMEOUT_MS));
			final BrowserContext context = browser.newContext(
					new Browser.NewContextOptions().setViewportSize(1920, 1080).setIgnoreHTTPSErrors(true));
			final Page page = context.newPage();

			runStep("Login", report, details, () -> {
				loginWithGoogle(page);
				assertTextVisible(page, "Negocio", "Mi Negocio");
				screenshot(page, evidenceDir, "01-dashboard-loaded", false);
			});

			runStep("Mi Negocio menu", report, details, () -> {
				clickIfVisible(page, "Negocio");
				clickByVisibleText(page, "Mi Negocio");
				assertTextVisible(page, "Agregar Negocio");
				assertTextVisible(page, "Administrar Negocios");
				screenshot(page, evidenceDir, "02-mi-negocio-expanded", false);
			});

			runStep("Agregar Negocio modal", report, details, () -> {
				clickByVisibleText(page, "Agregar Negocio");
				assertTextVisible(page, "Crear Nuevo Negocio");
				assertTextVisible(page, "Nombre del Negocio");
				assertTextVisible(page, "Tienes 2 de 3 negocios");
				assertTextVisible(page, "Cancelar", "Crear Negocio");
				screenshot(page, evidenceDir, "03-agregar-negocio-modal", false);

				fillIfVisible(page, "Nombre del Negocio", "Negocio Prueba Automatización");
				clickByVisibleText(page, "Cancelar");
			});

			runStep("Administrar Negocios view", report, details, () -> {
				clickIfVisible(page, "Mi Negocio");
				clickByVisibleText(page, "Administrar Negocios");
				assertTextVisible(page, "Información General");
				assertTextVisible(page, "Detalles de la Cuenta");
				assertTextVisible(page, "Tus Negocios");
				assertTextVisible(page, "Sección Legal");
				screenshot(page, evidenceDir, "04-administrar-negocios", true);
			});

			runStep("Información General", report, details, () -> {
				assertTextVisible(page, "Información General");
				assertTextVisible(page, "BUSINESS PLAN");
				assertTextVisible(page, "Cambiar Plan");

				final String body = page.locator("body").innerText();
				Assert.assertTrue("Expected user email to be visible.", EMAIL_PATTERN.matcher(body).find());
				Assert.assertTrue("Expected user name-like text to be visible.",
						containsLikelyName(body, Arrays.asList("Información General", "BUSINESS PLAN", "Cambiar Plan")));
			});

			runStep("Detalles de la Cuenta", report, details, () -> {
				assertTextVisible(page, "Cuenta creada");
				assertTextVisible(page, "Estado activo");
				assertTextVisible(page, "Idioma seleccionado");
			});

			runStep("Tus Negocios", report, details, () -> {
				assertTextVisible(page, "Tus Negocios");
				assertTextVisible(page, "Agregar Negocio");
				assertTextVisible(page, "Tienes 2 de 3 negocios");
				final String body = page.locator("body").innerText();
				Assert.assertTrue("Expected business list content in Tus Negocios section.", body.contains("Negocio"));
			});

			runStep("Términos y Condiciones", report, details, () -> {
				final String termsUrl = validateLegalLink(page, context, "Términos y Condiciones",
						Pattern.compile("(?i)t[eé]rminos\\s+y\\s+condiciones"), evidenceDir,
						"05-terminos-y-condiciones");
				legalUrls.put("Términos y Condiciones URL", termsUrl);
			});

			runStep("Política de Privacidad", report, details, () -> {
				final String privacyUrl = validateLegalLink(page, context, "Política de Privacidad",
						Pattern.compile("(?i)pol[ií]tica\\s+de\\s+privacidad"), evidenceDir, "06-politica-privacidad");
				legalUrls.put("Política de Privacidad URL", privacyUrl);
			});

			writeFinalReport(evidenceDir, report, details, legalUrls);
			browser.close();
		}

		Assert.assertTrue("One or more workflow validations failed. Check target/saleads-evidence for details.",
				report.values().stream().allMatch("PASS"::equals));
	}

	private void loginWithGoogle(final Page page) {
		if (BASE_URL != null && !BASE_URL.isBlank()) {
			page.navigate(BASE_URL);
		}

		waitForUiLoad(page);
		final List<String> loginTexts = Arrays.asList("Sign in with Google", "Iniciar sesión con Google",
				"Continuar con Google", "Login with Google", "Google");
		final Locator loginTrigger = firstVisibleLocator(page, loginTexts);

		Page popup = null;
		try {
			popup = page.waitForPopup(new Page.WaitForPopupOptions().setTimeout(8000), () -> clickAndWait(page, loginTrigger));
		} catch (final PlaywrightException ignored) {
			clickAndWait(page, loginTrigger);
		}

		final Page googlePage = popup != null ? popup : page;
		selectGoogleAccountIfPrompted(googlePage);

		if (popup != null) {
			waitForUiLoad(page);
		} else {
			waitForUiLoad(googlePage);
		}

		assertAnyVisible(page, Arrays.asList("Negocio", "Mi Negocio", "Dashboard", "Inicio"));
	}

	private void selectGoogleAccountIfPrompted(final Page page) {
		final Locator accountLocator = page.getByText(GOOGLE_EMAIL, new Page.GetByTextOptions().setExact(true)).first();
		if (isVisible(accountLocator, 10000)) {
			clickAndWait(page, accountLocator);
		}
	}

	private String validateLegalLink(final Page appPage, final BrowserContext context, final String linkText,
			final Pattern expectedHeading, final Path evidenceDir, final String screenshotName) {
		Page targetPage = null;

		try {
			targetPage = appPage.waitForPopup(new Page.WaitForPopupOptions().setTimeout(8000),
					() -> clickByVisibleText(appPage, linkText));
		} catch (final PlaywrightException ignored) {
			clickByVisibleText(appPage, linkText);
			targetPage = appPage;
		}

		waitForUiLoad(targetPage);
		final Locator heading = targetPage.getByText(expectedHeading).first();
		heading.waitFor(new Locator.WaitForOptions().setState(Locator.WaitForOptions.State.VISIBLE)
				.setTimeout(DEFAULT_TIMEOUT_MS));
		assertThat(heading).isVisible();

		final String legalText = targetPage.locator("body").innerText();
		Assert.assertTrue("Expected legal content text to be visible for " + linkText, legalText != null
				&& legalText.trim().length() > 250);

		screenshot(targetPage, evidenceDir, screenshotName, true);
		final String finalUrl = targetPage.url();

		if (targetPage != appPage) {
			targetPage.close();
			appPage.bringToFront();
			waitForUiLoad(appPage);
		} else if (!isVisible(appPage.getByText("Sección Legal").first(), 2000)) {
			appPage.goBack();
			waitForUiLoad(appPage);
		}

		return finalUrl;
	}

	private void fillIfVisible(final Page page, final String fieldText, final String value) {
		Locator input = page.getByLabel(fieldText).first();
		if (!isVisible(input, 1500)) {
			input = page.getByPlaceholder(fieldText).first();
		}
		if (!isVisible(input, 1500)) {
			input = page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName(fieldText)).first();
		}
		input.waitFor(new Locator.WaitForOptions().setState(Locator.WaitForOptions.State.VISIBLE)
				.setTimeout(DEFAULT_TIMEOUT_MS));
		input.click();
		input.fill(value);
	}

	private void clickByVisibleText(final Page page, final String text) {
		final Locator locator = firstVisibleLocator(page, Arrays.asList(text));
		clickAndWait(page, locator);
	}

	private void clickIfVisible(final Page page, final String text) {
		final Locator candidate = page.getByText(text, new Page.GetByTextOptions().setExact(true)).first();
		if (isVisible(candidate, 1200)) {
			clickAndWait(page, candidate);
		}
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.scrollIntoViewIfNeeded();
		locator.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		waitForUiLoad(page);
	}

	private void waitForUiLoad(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE,
					new Page.WaitForLoadStateOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		} catch (final PlaywrightException ignored) {
			// NETWORKIDLE is not guaranteed in all applications, so DOMCONTENTLOADED is the strict minimum.
		}
		page.waitForTimeout(700);
	}

	private Locator firstVisibleLocator(final Page page, final List<String> texts) {
		for (final String text : texts) {
			final List<Locator> candidates = Arrays.asList(
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(text)).first(),
					page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(text)).first(),
					page.getByText(text, new Page.GetByTextOptions().setExact(true)).first(),
					page.getByText(text).first());

			for (final Locator candidate : candidates) {
				if (isVisible(candidate, 1500)) {
					return candidate;
				}
			}
		}

		throw new AssertionError("Unable to find visible element with texts: " + texts);
	}

	private void assertTextVisible(final Page page, final String... texts) {
		for (final String text : texts) {
			final Locator locator = firstVisibleLocator(page, Arrays.asList(text));
			assertThat(locator).isVisible();
		}
	}

	private void assertAnyVisible(final Page page, final List<String> texts) {
		for (final String text : texts) {
			final Locator locator = page.getByText(text).first();
			if (isVisible(locator, 1500)) {
				return;
			}
		}
		throw new AssertionError("None of the expected visible texts were found: " + texts);
	}

	private boolean isVisible(final Locator locator, final double timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(Locator.WaitForOptions.State.VISIBLE)
					.setTimeout(timeoutMs));
			return true;
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private void screenshot(final Page page, final Path evidenceDir, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(fileName + ".png")).setFullPage(fullPage));
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path evidenceDir = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	private void writeFinalReport(final Path evidenceDir, final Map<String, String> report,
			final Map<String, String> details, final Map<String, String> legalUrls) throws IOException {
		final StringBuilder sb = new StringBuilder();
		sb.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		sb.append(System.lineSeparator());
		sb.append("Final validation results:").append(System.lineSeparator());
		sb.append("Login: ").append(report.getOrDefault("Login", "FAIL")).append(System.lineSeparator());
		sb.append("Mi Negocio menu: ").append(report.getOrDefault("Mi Negocio menu", "FAIL")).append(System.lineSeparator());
		sb.append("Agregar Negocio modal: ").append(report.getOrDefault("Agregar Negocio modal", "FAIL"))
				.append(System.lineSeparator());
		sb.append("Administrar Negocios view: ").append(report.getOrDefault("Administrar Negocios view", "FAIL"))
				.append(System.lineSeparator());
		sb.append("Información General: ").append(report.getOrDefault("Información General", "FAIL"))
				.append(System.lineSeparator());
		sb.append("Detalles de la Cuenta: ").append(report.getOrDefault("Detalles de la Cuenta", "FAIL"))
				.append(System.lineSeparator());
		sb.append("Tus Negocios: ").append(report.getOrDefault("Tus Negocios", "FAIL")).append(System.lineSeparator());
		sb.append("Términos y Condiciones: ").append(report.getOrDefault("Términos y Condiciones", "FAIL"))
				.append(System.lineSeparator());
		sb.append("Política de Privacidad: ").append(report.getOrDefault("Política de Privacidad", "FAIL"))
				.append(System.lineSeparator());
		sb.append(System.lineSeparator());
		sb.append("Captured legal URLs:").append(System.lineSeparator());
		sb.append("Términos y Condiciones URL: ")
				.append(legalUrls.getOrDefault("Términos y Condiciones URL", "N/A")).append(System.lineSeparator());
		sb.append("Política de Privacidad URL: ")
				.append(legalUrls.getOrDefault("Política de Privacidad URL", "N/A")).append(System.lineSeparator());

		if (!details.isEmpty()) {
			sb.append(System.lineSeparator());
			sb.append("Failure details:").append(System.lineSeparator());
			for (final Map.Entry<String, String> detail : details.entrySet()) {
				sb.append("- ").append(detail.getKey()).append(": ").append(detail.getValue()).append(System.lineSeparator());
			}
		}

		Files.writeString(evidenceDir.resolve("final-report.txt"), sb.toString(), StandardCharsets.UTF_8);
	}

	private void runStep(final String stepName, final Map<String, String> report, final Map<String, String> details,
			final ThrowingRunnable action) {
		try {
			action.run();
			report.put(stepName, "PASS");
		} catch (final Throwable throwable) {
			report.put(stepName, "FAIL");
			details.put(stepName, throwable.getMessage());
		}
	}

	private boolean containsLikelyName(final String text, final List<String> excludedValues) {
		return Arrays.stream(text.split("\\R")).map(String::trim).filter(line -> !line.isEmpty())
				.filter(line -> line.length() >= 3 && line.length() <= 60).filter(line -> !EMAIL_PATTERN.matcher(line).find())
				.filter(line -> excludedValues.stream().noneMatch(excluded -> excluded.equalsIgnoreCase(line)))
				.anyMatch(line -> line.matches("(?iu)[\\p{L}][\\p{L}\\s.'-]{2,}"));
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
