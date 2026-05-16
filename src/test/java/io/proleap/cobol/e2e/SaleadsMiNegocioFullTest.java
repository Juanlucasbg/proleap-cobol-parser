package io.proleap.cobol.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullTest {

	private static final int DEFAULT_TIMEOUT_MS = 15000;
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
	private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final List<String> REPORT_FIELDS = Arrays.asList(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Información General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"Términos y Condiciones",
			"Política de Privacidad"
	);

	private static final Pattern GOOGLE_ACTION_PATTERN = Pattern.compile(
			"(?i)(sign\\s*in\\s*with\\s*google|iniciar\\s*sesi[oó]n\\s*con\\s*google|continuar\\s*con\\s*google|google)"
	);
	private static final Pattern NEGOCIO_PATTERN = Pattern.compile("(?i)^\\s*Negocio\\s*$");
	private static final Pattern MI_NEGOCIO_PATTERN = Pattern.compile("(?i)^\\s*Mi\\s*Negocio\\s*$");
	private static final Pattern AGREGAR_NEGOCIO_PATTERN = Pattern.compile("(?i)^\\s*Agregar\\s*Negocio\\s*$");
	private static final Pattern ADMINISTRAR_NEGOCIOS_PATTERN = Pattern.compile("(?i)^\\s*Administrar\\s*Negocios\\s*$");
	private static final Pattern TERMINOS_PATTERN = Pattern.compile("(?i)T[ée]rminos\\s*y\\s*Condiciones");
	private static final Pattern PRIVACIDAD_PATTERN = Pattern.compile("(?i)Pol[ií]tica\\s*de\\s*Privacidad");

	private final Map<String, StepOutcome> stepResults = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page appPage;
	private Path artifactsDirectory;

	@Test
	public void saleadsMiNegocioWorkflow() throws Exception {
		final String startUrl = resolveStartUrl();

		Assume.assumeTrue(
				"SALEADS_START_URL (or SALEADS_BASE_URL / BASE_URL / APP_URL) must be set to run this E2E test.",
				startUrl != null && !startUrl.isBlank()
		);

		initializeStepResults();
		artifactsDirectory = createArtifactsDirectory();
		launchBrowser();

		try {
			appPage.navigate(startUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			waitForUiToLoad(appPage);

			boolean continueFlow = executeOrBlock(true, "Login", this::stepLoginWithGoogle);
			continueFlow = executeOrBlock(continueFlow, "Mi Negocio menu", this::stepOpenMiNegocioMenu);
			continueFlow = executeOrBlock(continueFlow, "Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
			continueFlow = executeOrBlock(continueFlow, "Administrar Negocios view", this::stepOpenAdministrarNegocios);
			continueFlow = executeOrBlock(continueFlow, "Información General", this::stepValidateInformacionGeneral);
			continueFlow = executeOrBlock(continueFlow, "Detalles de la Cuenta", this::stepValidateDetallesCuenta);
			continueFlow = executeOrBlock(continueFlow, "Tus Negocios", this::stepValidateTusNegocios);
			continueFlow = executeOrBlock(continueFlow, "Términos y Condiciones", this::stepValidateTerminosYCondiciones);
			executeOrBlock(continueFlow, "Política de Privacidad", this::stepValidatePoliticaPrivacidad);
		} finally {
			writeFinalReport();
			closeBrowser();
		}

		Assert.assertTrue(buildFailureSummary(), allStepsPassed());
	}

	private void stepLoginWithGoogle() throws IOException {
		Locator loginButton = waitForFirstVisible(
				appPage,
				clickableCandidates(appPage, GOOGLE_ACTION_PATTERN),
				"Google login button",
				DEFAULT_TIMEOUT_MS
		);

		int pageCountBefore = context.pages().size();
		clickAndWaitForUi(appPage, loginButton);

		Page newPage = waitForNewPage(pageCountBefore, 7000);
		if (newPage != null) {
			handleGoogleAccountPickerIfPresent(newPage);
		} else if (appPage.url().toLowerCase(Locale.ROOT).contains("accounts.google")) {
			handleGoogleAccountPickerIfPresent(appPage);
		}

		assertVisibleText(appPage, NEGOCIO_PATTERN, "left sidebar entry 'Negocio'");
		Locator mainArea = waitForFirstVisible(
				appPage,
				Arrays.asList(appPage.locator("main"), appPage.locator("[role='main']")),
				"main application area",
				DEFAULT_TIMEOUT_MS
		);
		Assert.assertTrue("Main application interface is not visible.", mainArea.isVisible());
		takeScreenshot(appPage, "01-dashboard-loaded.png", false);
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		optionalClickByText(appPage, NEGOCIO_PATTERN);
		clickByText(appPage, MI_NEGOCIO_PATTERN, "Mi Negocio menu option");

		assertVisibleText(appPage, AGREGAR_NEGOCIO_PATTERN, "'Agregar Negocio' option");
		assertVisibleText(appPage, ADMINISTRAR_NEGOCIOS_PATTERN, "'Administrar Negocios' option");
		takeScreenshot(appPage, "02-mi-negocio-expanded.png", false);
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByText(appPage, AGREGAR_NEGOCIO_PATTERN, "Agregar Negocio option");

		assertVisibleText(appPage, Pattern.compile("(?i)Crear\\s+Nuevo\\s+Negocio"), "modal title 'Crear Nuevo Negocio'");
		Locator nombreInput = waitForBusinessNameInput();
		Assert.assertTrue("Input 'Nombre del Negocio' is not visible.", nombreInput.isVisible());
		assertVisibleText(appPage, Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios"), "business quota text");
		assertVisibleText(appPage, Pattern.compile("(?i)^\\s*Cancelar\\s*$"), "'Cancelar' button");
		assertVisibleText(appPage, Pattern.compile("(?i)Crear\\s+Negocio"), "'Crear Negocio' button");

		takeScreenshot(appPage, "03-agregar-negocio-modal.png", false);

		clickAndWaitForUi(appPage, nombreInput);
		nombreInput.fill("Negocio Prueba Automatizacion");
		clickByText(appPage, Pattern.compile("(?i)^\\s*Cancelar\\s*$"), "Cancelar button in modal");
		Assert.assertFalse(
				"Agregar Negocio modal should be closed after clicking 'Cancelar'.",
				isTextVisible(appPage, Pattern.compile("(?i)Crear\\s+Nuevo\\s+Negocio"), 3000)
		);
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		if (!isTextVisible(appPage, ADMINISTRAR_NEGOCIOS_PATTERN, 2500)) {
			clickByText(appPage, MI_NEGOCIO_PATTERN, "Mi Negocio menu option");
		}

		clickByText(appPage, ADMINISTRAR_NEGOCIOS_PATTERN, "Administrar Negocios option");

		assertVisibleText(appPage, Pattern.compile("(?i)Informaci[oó]n\\s+General"), "'Información General' section");
		assertVisibleText(appPage, Pattern.compile("(?i)Detalles\\s+de\\s+la\\s+Cuenta"), "'Detalles de la Cuenta' section");
		assertVisibleText(appPage, Pattern.compile("(?i)Tus\\s+Negocios"), "'Tus Negocios' section");
		assertVisibleText(appPage, Pattern.compile("(?i)Secci[oó]n\\s+Legal"), "'Sección Legal' section");

		takeScreenshot(appPage, "04-administrar-negocios-view.png", true);
	}

	private void stepValidateInformacionGeneral() {
		String bodyText = appPage.locator("body").innerText();
		assertContainsEmail(bodyText);
		assertContainsLikelyUserNameNearEmail(bodyText);
		assertVisibleText(appPage, Pattern.compile("(?i)BUSINESS\\s+PLAN"), "'BUSINESS PLAN' label");
		assertVisibleText(appPage, Pattern.compile("(?i)Cambiar\\s+Plan"), "'Cambiar Plan' button");
	}

	private void stepValidateDetallesCuenta() {
		assertVisibleText(appPage, Pattern.compile("(?i)Cuenta\\s+creada"), "'Cuenta creada' item");
		assertVisibleText(appPage, Pattern.compile("(?i)Estado\\s+activo"), "'Estado activo' item");
		assertVisibleText(appPage, Pattern.compile("(?i)Idioma\\s+seleccionado"), "'Idioma seleccionado' item");
	}

	private void stepValidateTusNegocios() {
		assertVisibleText(appPage, Pattern.compile("(?i)Tus\\s+Negocios"), "'Tus Negocios' title");
		assertVisibleText(appPage, AGREGAR_NEGOCIO_PATTERN, "'Agregar Negocio' button in business list");
		assertVisibleText(appPage, Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios"), "business quota text");

		String bodyText = appPage.locator("body").innerText();
		Pattern hasBusinessList = Pattern.compile("(?is)Tus\\s+Negocios.*(Negocio|Activo|Principal|Predeterminado)");
		Assert.assertTrue("Business list details are not visible.", hasBusinessList.matcher(bodyText).find());
	}

	private void stepValidateTerminosYCondiciones() throws IOException {
		String finalUrl = openLegalDocumentAndReturn(
				TERMINOS_PATTERN,
				TERMINOS_PATTERN,
				"05-terminos-y-condiciones.png"
		);
		legalUrls.put("Términos y Condiciones", finalUrl);
	}

	private void stepValidatePoliticaPrivacidad() throws IOException {
		String finalUrl = openLegalDocumentAndReturn(
				PRIVACIDAD_PATTERN,
				PRIVACIDAD_PATTERN,
				"06-politica-de-privacidad.png"
		);
		legalUrls.put("Política de Privacidad", finalUrl);
	}

	private String openLegalDocumentAndReturn(final Pattern linkText, final Pattern headingText, final String screenshotName)
			throws IOException {
		int pageCountBefore = context.pages().size();
		clickByText(appPage, linkText, "Legal link " + linkText.pattern());

		Page openedPage = waitForNewPage(pageCountBefore, 6000);
		final boolean openedInNewTab = openedPage != null;
		final Page targetPage = openedInNewTab ? openedPage : appPage;

		waitForUiToLoad(targetPage);
		assertVisibleText(targetPage, headingText, "Legal document heading");

		String bodyText = targetPage.locator("body").innerText();
		Assert.assertTrue("Legal content text is not visible.", bodyText != null && bodyText.trim().length() > 120);
		takeScreenshot(targetPage, screenshotName, true);

		String finalUrl = targetPage.url();

		if (openedInNewTab) {
			targetPage.close();
			appPage.bringToFront();
			waitForUiToLoad(appPage);
		} else {
			if (appPage.goBack(new Page.GoBackOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED)) == null) {
				throw new AssertionError("Unable to return to the application page after opening legal document.");
			}
			waitForUiToLoad(appPage);
		}

		return finalUrl;
	}

	private void handleGoogleAccountPickerIfPresent(final Page googlePage) {
		waitForUiToLoad(googlePage);
		String googleAccount = Optional.ofNullable(System.getenv("SALEADS_GOOGLE_ACCOUNT_EMAIL"))
				.orElse("juanlucasbarbiergarzon@gmail.com");
		Pattern accountPattern = Pattern.compile(Pattern.quote(googleAccount), Pattern.CASE_INSENSITIVE);

		if (isTextVisible(googlePage, accountPattern, 6000)) {
			clickByText(googlePage, accountPattern, "Google account " + googleAccount);
		}

		waitForUiToLoad(appPage);
	}

	private Locator waitForBusinessNameInput() {
		Pattern nombreNegocioPattern = Pattern.compile("(?i)Nombre\\s+del\\s+Negocio");
		List<Locator> candidates = new ArrayList<>();
		candidates.add(appPage.getByLabel(nombreNegocioPattern));
		candidates.add(appPage.getByPlaceholder(nombreNegocioPattern));
		candidates.add(appPage.locator("input[name*='negocio' i], input[id*='negocio' i], input[aria-label*='negocio' i]"));

		return waitForFirstVisible(appPage, candidates, "input 'Nombre del Negocio'", DEFAULT_TIMEOUT_MS);
	}

	private void clickByText(final Page page, final Pattern pattern, final String description) {
		Locator target = waitForFirstVisible(page, clickableCandidates(page, pattern), description, DEFAULT_TIMEOUT_MS);
		clickAndWaitForUi(page, target);
	}

	private void optionalClickByText(final Page page, final Pattern pattern) {
		if (isTextVisible(page, pattern, 3000)) {
			clickByText(page, pattern, "optional click for " + pattern.pattern());
		}
	}

	private void clickAndWaitForUi(final Page page, final Locator target) {
		target.scrollIntoViewIfNeeded();
		target.click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		waitForUiToLoad(page);
	}

	private List<Locator> clickableCandidates(final Page page, final Pattern pattern) {
		List<Locator> candidates = new ArrayList<>();
		candidates.add(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern)));
		candidates.add(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(pattern)));
		candidates.add(page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(pattern)));
		candidates.add(page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(pattern)));
		candidates.add(page.getByText(pattern));
		return candidates;
	}

	private Locator waitForFirstVisible(
			final Page page,
			final List<Locator> candidates,
			final String description,
			final int timeoutMs
	) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		PlaywrightException lastException = null;

		while (System.currentTimeMillis() < deadline) {
			for (Locator candidateGroup : candidates) {
				try {
					int count = candidateGroup.count();
					for (int i = 0; i < count; i++) {
						Locator candidate = candidateGroup.nth(i);
						if (candidate.isVisible(new Locator.IsVisibleOptions().setTimeout(250))) {
							return candidate;
						}
					}
				} catch (PlaywrightException e) {
					lastException = e;
				}
			}

			page.waitForTimeout(200);
		}

		throw new AssertionError(
				"Could not locate visible element for " + description + " within " + timeoutMs + "ms.",
				lastException
		);
	}

	private void assertVisibleText(final Page page, final Pattern textPattern, final String description) {
		Locator visible = waitForFirstVisible(
				page,
				Arrays.asList(page.getByText(textPattern), page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(textPattern))),
				description,
				DEFAULT_TIMEOUT_MS
		);
		Assert.assertTrue("Expected visible element for " + description, visible.isVisible());
	}

	private boolean isTextVisible(final Page page, final Pattern textPattern, final int timeoutMs) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			try {
				Locator textLocator = page.getByText(textPattern);
				int count = textLocator.count();
				for (int i = 0; i < count; i++) {
					if (textLocator.nth(i).isVisible(new Locator.IsVisibleOptions().setTimeout(200))) {
						return true;
					}
				}
			} catch (PlaywrightException ignored) {
				// keep polling until timeout
			}
			page.waitForTimeout(200);
		}
		return false;
	}

	private void assertContainsEmail(final String bodyText) {
		Matcher emailMatcher = EMAIL_PATTERN.matcher(bodyText);
		Assert.assertTrue("User email is not visible in Información General.", emailMatcher.find());
	}

	private void assertContainsLikelyUserNameNearEmail(final String bodyText) {
		String[] lines = bodyText.split("\\R");
		for (int i = 0; i < lines.length; i++) {
			if (EMAIL_PATTERN.matcher(lines[i]).find()) {
				for (int j = i - 1; j >= 0; j--) {
					String candidate = lines[j].trim();
					if (!candidate.isEmpty() && !candidate.contains("@") && candidate.length() >= 3) {
						return;
					}
				}
			}
		}

		Pattern genericNamePattern = Pattern.compile("(?is)(nombre\\s*(de\\s*usuario)?\\s*:?\\s*[\\p{L}][\\p{L}\\s'.-]{2,})");
		Assert.assertTrue("User name is not visible in Información General.", genericNamePattern.matcher(bodyText).find());
	}

	private Page waitForNewPage(final int countBeforeClick, final int timeoutMs) {
		long deadline = System.currentTimeMillis() + timeoutMs;
		while (System.currentTimeMillis() < deadline) {
			List<Page> pages = context.pages();
			if (pages.size() > countBeforeClick) {
				return pages.get(pages.size() - 1);
			}
			appPage.waitForTimeout(200);
		}
		return null;
	}

	private void waitForUiToLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(6000));
		} catch (PlaywrightException ignored) {
			// fallback to short settle wait below
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (PlaywrightException ignored) {
			// websocket-heavy apps can keep network busy; fallback settle wait below
		}

		page.waitForTimeout(700);
	}

	private void takeScreenshot(final Page page, final String fileName, final boolean fullPage) throws IOException {
		Path screenshotPath = artifactsDirectory.resolve(fileName);
		Files.createDirectories(screenshotPath.getParent());
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
	}

	private void launchBrowser() {
		boolean headless = !"false".equalsIgnoreCase(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));
		double slowMo = parseDoubleEnv("SALEADS_SLOWMO_MS").orElse(0d);

		playwright = Playwright.create();
		browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo(slowMo));
		context = browser.newContext(new Browser.NewContextOptions()
				.setViewportSize(1600, 1000)
				.setIgnoreHTTPSErrors(true));
		appPage = context.newPage();
	}

	private void closeBrowser() {
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

	private void initializeStepResults() {
		for (String field : REPORT_FIELDS) {
			stepResults.put(field, StepOutcome.fail("Not executed."));
		}
	}

	private boolean executeOrBlock(final boolean shouldExecute, final String stepName, final CheckedRunnable runnable) {
		if (!shouldExecute) {
			stepResults.put(stepName, StepOutcome.fail("Not executed because a prior step failed."));
			return false;
		}
		return runStep(stepName, runnable);
	}

	private boolean runStep(final String stepName, final CheckedRunnable runnable) {
		try {
			runnable.run();
			stepResults.put(stepName, StepOutcome.pass("Validated successfully."));
			return true;
		} catch (Throwable throwable) {
			String message = throwable.getMessage();
			if (message == null || message.isBlank()) {
				message = throwable.getClass().getSimpleName();
			}
			stepResults.put(stepName, StepOutcome.fail(message));
			return false;
		}
	}

	private boolean allStepsPassed() {
		for (String field : REPORT_FIELDS) {
			StepOutcome outcome = stepResults.get(field);
			if (outcome == null || !outcome.isPass()) {
				return false;
			}
		}
		return true;
	}

	private String buildFailureSummary() {
		StringBuilder builder = new StringBuilder("One or more SaleADS workflow validations failed:");
		for (String field : REPORT_FIELDS) {
			StepOutcome outcome = stepResults.get(field);
			if (outcome != null && !outcome.isPass()) {
				builder.append(System.lineSeparator())
						.append(" - ")
						.append(field)
						.append(": ")
						.append(outcome.detail);
			}
		}
		builder.append(System.lineSeparator())
				.append("Report file: ")
				.append(artifactsDirectory.resolve("final-report.txt"));
		return builder.toString();
	}

	private void writeFinalReport() throws IOException {
		if (artifactsDirectory == null) {
			return;
		}

		StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		reportBuilder.append("Generated at: ").append(LocalDateTime.now()).append(System.lineSeparator());
		reportBuilder.append(System.lineSeparator());

		for (String field : REPORT_FIELDS) {
			StepOutcome outcome = stepResults.get(field);
			if (outcome == null) {
				outcome = StepOutcome.fail("No result captured.");
			}

			reportBuilder.append(field)
					.append(": ")
					.append(outcome.status)
					.append(" - ")
					.append(outcome.detail)
					.append(System.lineSeparator());
		}

		reportBuilder.append(System.lineSeparator());
		for (Map.Entry<String, String> legalUrl : legalUrls.entrySet()) {
			reportBuilder.append(legalUrl.getKey())
					.append(" URL: ")
					.append(legalUrl.getValue())
					.append(System.lineSeparator());
		}

		reportBuilder.append("Artifacts directory: ").append(artifactsDirectory).append(System.lineSeparator());

		Path reportPath = artifactsDirectory.resolve("final-report.txt");
		Files.writeString(reportPath, reportBuilder.toString(), StandardCharsets.UTF_8);

		System.out.println(reportBuilder);
	}

	private Path createArtifactsDirectory() throws IOException {
		String configuredPath = System.getenv().getOrDefault("SALEADS_ARTIFACTS_DIR", "").trim();
		Path baseDirectory;

		if (!configuredPath.isBlank()) {
			baseDirectory = Paths.get(configuredPath);
		} else {
			String timestamp = TIMESTAMP_FORMATTER.format(LocalDateTime.now());
			baseDirectory = Paths.get("target", "saleads-e2e", timestamp);
		}

		Files.createDirectories(baseDirectory);
		return baseDirectory;
	}

	private String resolveStartUrl() {
		List<String> envKeys = Arrays.asList("SALEADS_START_URL", "SALEADS_BASE_URL", "BASE_URL", "APP_URL");
		for (String envKey : envKeys) {
			String value = System.getenv(envKey);
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return null;
	}

	private Optional<Double> parseDoubleEnv(final String envName) {
		String value = System.getenv(envName);
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		try {
			return Optional.of(Double.parseDouble(value.trim()));
		} catch (NumberFormatException ignored) {
			return Optional.empty();
		}
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static final class StepOutcome {
		private final String status;
		private final String detail;

		private StepOutcome(final String status, final String detail) {
			this.status = status;
			this.detail = detail;
		}

		private static StepOutcome pass(final String detail) {
			return new StepOutcome("PASS", detail);
		}

		private static StepOutcome fail(final String detail) {
			return new StepOutcome("FAIL", detail);
		}

		private boolean isPass() {
			return "PASS".equals(status);
		}
	}
}
