package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
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
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;

public class SaleadsMiNegocioFullTest {

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MENU = "Mi Negocio menu";
	private static final String STEP_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN_VIEW = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Información General";
	private static final String STEP_ACCOUNT_DETAILS = "Detalles de la Cuenta";
	private static final String STEP_BUSINESSES = "Tus Negocios";
	private static final String STEP_TERMS = "Términos y Condiciones";
	private static final String STEP_PRIVACY = "Política de Privacidad";

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern POSSIBLE_NAME_PATTERN = Pattern.compile("(?m)^[\\p{L}][\\p{L} .'-]{2,}$");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final String baseUrl = readConfig("saleads.baseUrl", "SALEADS_BASE_URL");
		Assume.assumeTrue(
				"Skipping SaleADS E2E: set SALEADS_BASE_URL or -Dsaleads.baseUrl to the login page of your environment.",
				baseUrl != null && !baseUrl.isBlank());

		final String googleAccount = readConfigOrDefault("saleads.googleAccount", "SALEADS_GOOGLE_ACCOUNT",
				"juanlucasbarbiergarzon@gmail.com");
		final boolean headless = Boolean.parseBoolean(
				readConfigOrDefault("saleads.headless", "SALEADS_HEADLESS", "true"));
		final Path evidenceDir = createEvidenceDirectory();

		final LinkedHashMap<String, StepResult> report = new LinkedHashMap<>();
		final List<String> legalUrls = new ArrayList<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless));
			try (BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900))) {
				final Page page = context.newPage();
				page.navigate(baseUrl);
				waitForUiToSettle(page);

				runStep(report, STEP_LOGIN, () -> {
					performGoogleLogin(page, context, googleAccount);
					assertMainAppVisible(page);
					captureScreenshot(page, evidenceDir.resolve("01-dashboard.png"), false);
				});

				runStep(report, STEP_MENU, () -> {
					openMiNegocioMenu(page);
					assertVisibleText(page, "Agregar Negocio", true, 10_000);
					assertVisibleText(page, "Administrar Negocios", true, 10_000);
					captureScreenshot(page, evidenceDir.resolve("02-mi-negocio-menu-expanded.png"), false);
				});

				runStep(report, STEP_MODAL, () -> {
					clickVisibleText(page, "Agregar Negocio", true, 10_000);
					assertVisibleText(page, "Crear Nuevo Negocio", true, 10_000);
					assertFieldVisible(page, "Nombre del Negocio", 10_000);
					assertVisibleText(page, "Tienes 2 de 3 negocios", false, 10_000);
					assertVisibleText(page, "Cancelar", true, 10_000);
					assertVisibleText(page, "Crear Negocio", true, 10_000);
					captureScreenshot(page, evidenceDir.resolve("03-agregar-negocio-modal.png"), false);

					fillFieldIfPresent(page, "Nombre del Negocio", "Negocio Prueba Automatización");
					clickVisibleText(page, "Cancelar", true, 10_000);
				});

				runStep(report, STEP_ADMIN_VIEW, () -> {
					if (!isTextVisible(page, "Administrar Negocios", true, 2_000)) {
						clickVisibleText(page, "Mi Negocio", true, 10_000);
					}
					clickVisibleText(page, "Administrar Negocios", true, 10_000);

					assertVisibleText(page, "Información General", true, 15_000);
					assertVisibleText(page, "Detalles de la Cuenta", true, 15_000);
					assertVisibleText(page, "Tus Negocios", true, 15_000);
					assertVisibleText(page, "Sección Legal", true, 15_000);
					captureScreenshot(page, evidenceDir.resolve("04-administrar-negocios-full-page.png"), true);
				});

				runStep(report, STEP_INFO_GENERAL, () -> {
					assertVisibleEmail(page, 10_000);
					assertTrue("Expected a visible user name in the account overview.", hasVisibleLikelyUserName(page));
					assertVisibleText(page, "BUSINESS PLAN", false, 10_000);
					assertVisibleText(page, "Cambiar Plan", true, 10_000);
				});

				runStep(report, STEP_ACCOUNT_DETAILS, () -> {
					assertVisibleText(page, "Cuenta creada", false, 10_000);
					assertVisibleText(page, "Estado activo", false, 10_000);
					assertVisibleText(page, "Idioma seleccionado", false, 10_000);
				});

				runStep(report, STEP_BUSINESSES, () -> {
					assertVisibleText(page, "Tus Negocios", true, 10_000);
					assertVisibleText(page, "Agregar Negocio", true, 10_000);
					assertVisibleText(page, "Tienes 2 de 3 negocios", false, 10_000);
					assertTrue("Expected business list content to be visible.", hasBusinessListContent(page));
				});

				runStep(report, STEP_TERMS, () -> {
					final String url = validateLegalLink(page, context, "Términos y Condiciones", "Términos y Condiciones",
							evidenceDir.resolve("05-terminos-y-condiciones.png"));
					legalUrls.add("Términos y Condiciones URL: " + url);
				});

				runStep(report, STEP_PRIVACY, () -> {
					final String url = validateLegalLink(page, context, "Política de Privacidad", "Política de Privacidad",
							evidenceDir.resolve("06-politica-de-privacidad.png"));
					legalUrls.add("Política de Privacidad URL: " + url);
				});
			}
		}

		printFinalReport(report, legalUrls, evidenceDir);
		assertNoFailedSteps(report);
	}

	private void performGoogleLogin(final Page page, final BrowserContext context, final String googleAccount) {
		final Locator googleButton = findFirstVisibleText(page, List.of(
				"Sign in with Google",
				"Iniciar sesión con Google",
				"Continuar con Google",
				"Google"), 10_000);

		final int pagesBeforeClick = context.pages().size();
		googleButton.click();
		waitForUiToSettle(page);

		final Page popupOrCurrent = detectNewTab(context, pagesBeforeClick, 8_000);
		if (popupOrCurrent != null) {
			popupOrCurrent.bringToFront();
			waitForUiToSettle(popupOrCurrent);
			selectGoogleAccountIfPrompted(popupOrCurrent, googleAccount);
			waitUntilTabClosedOrTimeout(popupOrCurrent, 15_000);
			page.bringToFront();
		} else {
			selectGoogleAccountIfPrompted(page, googleAccount);
		}

		waitForAnyVisibleText(page, List.of("Negocio", "Mi Negocio", "Dashboard", "Inicio"), 30_000);
		waitForUiToSettle(page);
	}

	private void openMiNegocioMenu(final Page page) {
		clickVisibleText(page, "Negocio", true, 10_000);
		clickVisibleText(page, "Mi Negocio", true, 10_000);
	}

	private String validateLegalLink(final Page appPage, final BrowserContext context, final String linkText,
			final String headingText, final Path screenshotPath) {
		final int pagesBeforeClick = context.pages().size();
		final String appUrl = appPage.url();

		clickVisibleText(appPage, linkText, true, 10_000);
		final Page legalPage = detectNewTab(context, pagesBeforeClick, 5_000);
		final Page targetPage = legalPage == null ? appPage : legalPage;
		waitForUiToSettle(targetPage);

		assertVisibleText(targetPage, headingText, true, 15_000);
		assertLegalBodyVisible(targetPage, headingText);
		captureScreenshot(targetPage, screenshotPath, true);
		final String finalUrl = targetPage.url();

		if (legalPage != null) {
			legalPage.close();
			appPage.bringToFront();
			waitForUiToSettle(appPage);
		} else {
			try {
				appPage.goBack();
				waitForUiToSettle(appPage);
			} catch (PlaywrightException e) {
				appPage.navigate(appUrl);
				waitForUiToSettle(appPage);
			}
		}

		return finalUrl;
	}

	private void assertMainAppVisible(final Page page) {
		final boolean sidebarVisible = isLocatorVisible(page.locator("aside").first(), 8_000)
				|| isLocatorVisible(page.getByRole(AriaRole.NAVIGATION).first(), 8_000);
		assertTrue("Expected left sidebar navigation to be visible after login.", sidebarVisible);
	}

	private void assertVisibleText(final Page page, final String text, final boolean exact, final double timeoutMs) {
		final Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(exact)).first();
		if (!isLocatorVisible(locator, timeoutMs)) {
			throw new AssertionError("Expected text to be visible: " + text);
		}
	}

	private void assertFieldVisible(final Page page, final String label, final double timeoutMs) {
		Locator field = page.getByLabel(label, new Page.GetByLabelOptions().setExact(false)).first();
		if (!isLocatorVisible(field, timeoutMs)) {
			field = page.getByPlaceholder(label).first();
		}
		if (!isLocatorVisible(field, timeoutMs)) {
			throw new AssertionError("Expected input field to be visible: " + label);
		}
	}

	private void assertVisibleEmail(final Page page, final double timeoutMs) {
		final Locator email = page.getByText(EMAIL_PATTERN).first();
		if (!isLocatorVisible(email, timeoutMs)) {
			throw new AssertionError("Expected user email to be visible.");
		}
	}

	private boolean hasVisibleLikelyUserName(final Page page) {
		final String bodyText = page.locator("body").innerText();
		final Matcher matcher = POSSIBLE_NAME_PATTERN.matcher(bodyText);

		while (matcher.find()) {
			final String candidate = matcher.group().trim();
			if (candidate.isBlank()) {
				continue;
			}
			final String normalized = candidate.toLowerCase(Locale.ROOT);
			if (normalized.contains("@")
					|| normalized.contains("información general")
					|| normalized.contains("detalles de la cuenta")
					|| normalized.contains("tus negocios")
					|| normalized.contains("sección legal")
					|| normalized.contains("business plan")
					|| normalized.contains("cambiar plan")) {
				continue;
			}
			return true;
		}

		return false;
	}

	private boolean hasBusinessListContent(final Page page) {
		final String bodyText = page.locator("body").innerText().toLowerCase(Locale.ROOT);
		final int sectionIndex = bodyText.indexOf("tus negocios");
		if (sectionIndex < 0) {
			return false;
		}

		final String sectionTail = bodyText.substring(sectionIndex);
		final String[] lines = sectionTail.split("\\R");
		int meaningfulLines = 0;
		for (String line : lines) {
			final String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			if (trimmed.equals("tus negocios")
					|| trimmed.equals("agregar negocio")
					|| trimmed.contains("tienes 2 de 3 negocios")) {
				continue;
			}
			meaningfulLines++;
			if (meaningfulLines >= 1) {
				return true;
			}
		}

		return false;
	}

	private void assertLegalBodyVisible(final Page page, final String headingText) {
		final String text = page.locator("body").innerText();
		final String withoutHeading = text.replace(headingText, "").trim();
		if (withoutHeading.length() < 120) {
			throw new AssertionError("Expected legal content body text to be visible for: " + headingText);
		}
	}

	private void clickVisibleText(final Page page, final String text, final boolean exact, final double timeoutMs) {
		final Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(exact)).first();
		if (!isLocatorVisible(locator, timeoutMs)) {
			throw new AssertionError("Unable to find clickable text: " + text);
		}
		locator.click();
		waitForUiToSettle(page);
	}

	private void fillFieldIfPresent(final Page page, final String label, final String value) {
		Locator field = page.getByLabel(label, new Page.GetByLabelOptions().setExact(false)).first();
		if (!isLocatorVisible(field, 5_000)) {
			field = page.getByPlaceholder(label).first();
		}
		if (isLocatorVisible(field, 5_000)) {
			field.fill(value);
			waitForUiToSettle(page);
		}
	}

	private Locator findFirstVisibleText(final Page page, final List<String> candidates, final double timeoutMs) {
		for (final String candidate : candidates) {
			final Locator byText = page.getByText(candidate, new Page.GetByTextOptions().setExact(false)).first();
			if (isLocatorVisible(byText, timeoutMs / candidates.size())) {
				return byText;
			}
		}

		final Locator byButtonRole = page.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(Pattern.compile("google", Pattern.CASE_INSENSITIVE))).first();
		if (isLocatorVisible(byButtonRole, timeoutMs)) {
			return byButtonRole;
		}

		throw new AssertionError("Could not locate Google login button using visible text.");
	}

	private void selectGoogleAccountIfPrompted(final Page page, final String googleAccount) {
		final Locator accountOption = page.getByText(googleAccount, new Page.GetByTextOptions().setExact(true)).first();
		if (isLocatorVisible(accountOption, 8_000)) {
			accountOption.click();
			waitForUiToSettle(page);
		}
	}

	private void waitForAnyVisibleText(final Page page, final List<String> texts, final double timeoutMs) {
		final long start = System.currentTimeMillis();
		while (System.currentTimeMillis() - start <= timeoutMs) {
			for (final String text : texts) {
				if (isTextVisible(page, text, false, 1_000)) {
					return;
				}
			}
		}

		throw new AssertionError("None of the expected texts became visible: " + texts);
	}

	private boolean isTextVisible(final Page page, final String text, final boolean exact, final double timeoutMs) {
		final Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(exact)).first();
		return isLocatorVisible(locator, timeoutMs);
	}

	private boolean isLocatorVisible(final Locator locator, final double timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return true;
		} catch (PlaywrightException e) {
			return false;
		}
	}

	private Page detectNewTab(final BrowserContext context, final int pagesBeforeClick, final long timeoutMs) {
		final long start = System.currentTimeMillis();
		while (System.currentTimeMillis() - start <= timeoutMs) {
			final List<Page> pages = context.pages();
			if (pages.size() > pagesBeforeClick) {
				return pages.get(pages.size() - 1);
			}
			pause(200);
		}
		return null;
	}

	private void waitUntilTabClosedOrTimeout(final Page page, final long timeoutMs) {
		final long start = System.currentTimeMillis();
		while (System.currentTimeMillis() - start <= timeoutMs) {
			if (page.isClosed()) {
				return;
			}
			pause(200);
		}
	}

	private void waitForUiToSettle(final Page page) {
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(12_000));
		} catch (PlaywrightException ignored) {
			// Some SPA interactions do not produce network idle; a short pause still improves stability.
		}
		page.waitForTimeout(700);
	}

	private void runStep(final LinkedHashMap<String, StepResult> report, final String stepName,
			final StepAction action) {
		final StepResult result = new StepResult();
		try {
			action.run();
			result.passed = true;
			result.details = "PASS";
		} catch (Throwable t) {
			result.passed = false;
			result.details = "FAIL - " + sanitizeFailureMessage(t);
		}
		report.put(stepName, result);
	}

	private void captureScreenshot(final Page page, final Path path, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
	}

	private void printFinalReport(final LinkedHashMap<String, StepResult> report, final List<String> legalUrls,
			final Path evidenceDir) {
		System.out.println();
		System.out.println("=========== SaleADS Mi Negocio Final Report ===========");
		for (Map.Entry<String, StepResult> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue().passed ? "PASS" : "FAIL"));
			if (!entry.getValue().passed) {
				System.out.println("  Reason: " + entry.getValue().details);
			}
		}
		for (String legalUrl : legalUrls) {
			System.out.println(legalUrl);
		}
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
		System.out.println("=======================================================");
		System.out.println();
	}

	private void assertNoFailedSteps(final LinkedHashMap<String, StepResult> report) {
		final List<String> failures = new ArrayList<>();
		for (Map.Entry<String, StepResult> entry : report.entrySet()) {
			if (!entry.getValue().passed) {
				failures.add(entry.getKey() + " => " + entry.getValue().details);
			}
		}
		if (!failures.isEmpty()) {
			Assert.fail("SaleADS workflow validations failed:\n - " + String.join("\n - ", failures));
		}
	}

	private String readConfig(final String propertyName, final String envName) {
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}
		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}
		return null;
	}

	private String readConfigOrDefault(final String propertyName, final String envName, final String defaultValue) {
		final String value = readConfig(propertyName, envName);
		return value == null ? defaultValue : value;
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path evidenceDir = Path.of("target", "saleads-evidence", timestamp);
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	private String sanitizeFailureMessage(final Throwable throwable) {
		String message = throwable.getMessage();
		if (message == null || message.isBlank()) {
			message = throwable.getClass().getSimpleName();
		}
		return message.replaceAll("\\s+", " ").trim();
	}

	private void pause(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private static class StepResult {
		private boolean passed;
		private String details;
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
