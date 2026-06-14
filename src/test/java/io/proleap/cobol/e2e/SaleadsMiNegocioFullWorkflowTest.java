package io.proleap.cobol.e2e;

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
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
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
import com.microsoft.playwright.options.WaitUntilState;

import io.proleap.cobol.CobolTestBase;

public class SaleadsMiNegocioFullWorkflowTest extends CobolTestBase {

	private static final int VISIBILITY_TIMEOUT_MS = 10_000;
	private static final int ACTION_TIMEOUT_MS = 20_000;
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+");

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MENU = "Mi Negocio menu";
	private static final String STEP_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMIN_VIEW = "Administrar Negocios view";
	private static final String STEP_GENERAL = "Información General";
	private static final String STEP_DETAILS = "Detalles de la Cuenta";
	private static final String STEP_BUSINESSES = "Tus Negocios";
	private static final String STEP_TERMS = "Términos y Condiciones";
	private static final String STEP_PRIVACY = "Política de Privacidad";

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();
	private final List<String> failedSteps = new ArrayList<>();

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page appPage;
	private Path artifactDirectory;

	@Before
	public void setUp() throws IOException {
		initializeReport();
		artifactDirectory = createArtifactDirectory();
		initializeBrowserSession();
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final boolean loginOk = runStep(STEP_LOGIN, this::loginWithGoogle);
		final boolean menuOk = runIfPrerequisiteOk(STEP_MENU, STEP_LOGIN, loginOk, this::openMiNegocioMenu);
		runIfPrerequisiteOk(STEP_MODAL, STEP_MENU, menuOk, this::validateAgregarNegocioModal);

		final boolean adminViewOk = runIfPrerequisiteOk(STEP_ADMIN_VIEW, STEP_MENU, menuOk, this::openAdministrarNegocios);
		runIfPrerequisiteOk(STEP_GENERAL, STEP_ADMIN_VIEW, adminViewOk, this::validateInformacionGeneral);
		runIfPrerequisiteOk(STEP_DETAILS, STEP_ADMIN_VIEW, adminViewOk, this::validateDetallesCuenta);
		runIfPrerequisiteOk(STEP_BUSINESSES, STEP_ADMIN_VIEW, adminViewOk, this::validateTusNegocios);
		runIfPrerequisiteOk(STEP_TERMS, STEP_ADMIN_VIEW, adminViewOk, () -> validateLegalDocument(STEP_TERMS,
				Pattern.compile("(?i)T[eé]rminos\\s+y\\s+Condiciones"), Pattern.compile("(?i)T[eé]rminos\\s+y\\s+Condiciones")));
		runIfPrerequisiteOk(STEP_PRIVACY, STEP_ADMIN_VIEW, adminViewOk,
				() -> validateLegalDocument(STEP_PRIVACY, Pattern.compile("(?i)Pol[ií]tica\\s+de\\s+Privacidad"),
						Pattern.compile("(?i)Pol[ií]tica\\s+de\\s+Privacidad")));

		writeReport();
		Assert.assertTrue("Workflow failed for steps: " + failedSteps + ". Report: " + artifactDirectory.resolve("final-report.json"),
				failedSteps.isEmpty());
	}

	private void initializeBrowserSession() {
		final String cdpUrl = readConfig("SALEADS_CDP_URL", "saleads.cdpUrl");
		final String loginUrl = readConfig("SALEADS_LOGIN_URL", "saleads.loginUrl");
		final boolean headless = Boolean.parseBoolean(readConfigOrDefault("SALEADS_HEADLESS", "saleads.headless", "true"));

		Assume.assumeTrue("Provide SALEADS_LOGIN_URL or SALEADS_CDP_URL to run this workflow test.",
				(cdpUrl != null && !cdpUrl.isBlank()) || (loginUrl != null && !loginUrl.isBlank()));

		playwright = Playwright.create();

		if (cdpUrl != null && !cdpUrl.isBlank()) {
			browser = playwright.chromium().connectOverCDP(cdpUrl);
			context = browser.contexts().isEmpty() ? browser.newContext() : browser.contexts().get(0);
			appPage = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
			appPage.bringToFront();
			waitForUiLoad(appPage);
			return;
		}

		browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
		context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 960));
		appPage = context.newPage();
		appPage.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
		waitForUiLoad(appPage);
	}

	private void loginWithGoogle() {
		final Locator loginButton = firstVisible("Google login button",
				appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)(Sign\\s*in|Iniciar\\s*sesi[oó]n).*Google"))),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Google"))),
				appPage.getByRole(AriaRole.LINK,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)(Sign\\s*in|Iniciar\\s*sesi[oó]n).*Google"))),
				appPage.getByText(Pattern.compile("(?i)Sign\\s*in\\s*with\\s*Google|Iniciar\\s*sesi[oó]n\\s*con\\s*Google")));

		Page popup = null;
		final boolean[] loginClickTriggered = new boolean[] { false };
		try {
			popup = appPage.waitForPopup(new Page.WaitForPopupOptions().setTimeout(7_000), () -> {
				loginClickTriggered[0] = true;
				clickAndWait(loginButton, appPage);
			});
		} catch (PlaywrightException ignored) {
			// No popup appeared; login flow likely continues in the same tab.
		}
		if (!loginClickTriggered[0]) {
			clickAndWait(loginButton, appPage);
		}

		if (popup != null) {
			waitForUiLoad(popup);
			maybeSelectGoogleAccount(popup);
		} else {
			waitForUiLoad(appPage);
			maybeSelectGoogleAccount(appPage);
		}

		waitForUiLoad(appPage);
		firstVisible("Main application interface",
				appPage.getByText(Pattern.compile("(?i)Mi\\s+Negocio|Negocio")),
				appPage.locator("aside"),
				appPage.locator("nav"));

		StepResult result = stepResults.get(STEP_LOGIN);
		result.status = "PASS";
		result.details = "Main interface and sidebar are visible after Google sign-in.";
		result.evidence.add(captureScreenshot(STEP_LOGIN, appPage, "dashboard-loaded", true));
	}

	private void maybeSelectGoogleAccount(final Page page) {
		final Locator accountEntry = page.getByText("juanlucasbarbiergarzon@gmail.com");
		if (isVisible(accountEntry, 7_000)) {
			clickAndWait(accountEntry, page);
		}
	}

	private void openMiNegocioMenu() {
		final Locator negocioSection = firstVisible("Negocio section",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Negocio\\s*$"))),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Negocio\\s*$"))),
				appPage.getByText(Pattern.compile("(?i)^\\s*Negocio\\s*$")));

		clickAndWait(negocioSection, appPage);

		final Locator miNegocioOption = firstVisible("Mi Negocio option",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Mi\\s+Negocio\\s*$"))),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Mi\\s+Negocio\\s*$"))),
				appPage.getByText(Pattern.compile("(?i)^\\s*Mi\\s+Negocio\\s*$")));

		clickAndWait(miNegocioOption, appPage);
		firstVisible("Agregar Negocio submenu item",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Agregar\\s+Negocio\\s*$"))),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Agregar\\s+Negocio\\s*$"))),
				appPage.getByText(Pattern.compile("(?i)^\\s*Agregar\\s+Negocio\\s*$")));
		firstVisible("Administrar Negocios submenu item",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Administrar\\s+Negocios\\s*$"))),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Administrar\\s+Negocios\\s*$"))),
				appPage.getByText(Pattern.compile("(?i)^\\s*Administrar\\s+Negocios\\s*$")));

		StepResult result = stepResults.get(STEP_MENU);
		result.status = "PASS";
		result.details = "Mi Negocio submenu expanded with Agregar Negocio and Administrar Negocios.";
		result.evidence.add(captureScreenshot(STEP_MENU, appPage, "mi-negocio-menu-expanded", true));
	}

	private void validateAgregarNegocioModal() {
		final Locator agregarNegocio = firstVisible("Agregar Negocio action",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Agregar\\s+Negocio\\s*$"))),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Agregar\\s+Negocio\\s*$"))),
				appPage.getByText(Pattern.compile("(?i)^\\s*Agregar\\s+Negocio\\s*$")));
		clickAndWait(agregarNegocio, appPage);

		firstVisible("Crear Nuevo Negocio modal title", appPage.getByText(Pattern.compile("(?i)^\\s*Crear\\s+Nuevo\\s+Negocio\\s*$")));
		final Locator businessNameInput = firstVisible("Nombre del Negocio input",
				appPage.getByLabel(Pattern.compile("(?i)Nombre\\s+del\\s+Negocio")),
				appPage.getByPlaceholder(Pattern.compile("(?i)Nombre\\s+del\\s+Negocio")));
		firstVisible("Business quota text", appPage.getByText(Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios")));
		final Locator cancelButton = firstVisible("Cancelar button",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Cancelar\\s*$"))));
		firstVisible("Crear Negocio button",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Crear\\s+Negocio\\s*$"))));

		businessNameInput.fill("Negocio Prueba Automatizacion");
		waitForUiLoad(appPage);
		StepResult result = stepResults.get(STEP_MODAL);
		result.evidence.add(captureScreenshot(STEP_MODAL, appPage, "agregar-negocio-modal", true));
		clickAndWait(cancelButton, appPage);

		result.status = "PASS";
		result.details = "Crear Nuevo Negocio modal validated and closed with Cancelar.";
	}

	private void openAdministrarNegocios() {
		ensureMiNegocioSubmenuVisible();

		final Locator administrarNegocios = firstVisible("Administrar Negocios option",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Administrar\\s+Negocios\\s*$"))),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Administrar\\s+Negocios\\s*$"))),
				appPage.getByText(Pattern.compile("(?i)^\\s*Administrar\\s+Negocios\\s*$")));
		clickAndWait(administrarNegocios, appPage);

		firstVisible("Informacion General section", appPage.getByText(Pattern.compile("(?i)Informaci[oó]n\\s+General")));
		firstVisible("Detalles de la Cuenta section", appPage.getByText(Pattern.compile("(?i)Detalles\\s+de\\s+la\\s+Cuenta")));
		firstVisible("Tus Negocios section", appPage.getByText(Pattern.compile("(?i)Tus\\s+Negocios")));
		firstVisible("Seccion Legal section", appPage.getByText(Pattern.compile("(?i)Secci[oó]n\\s+Legal")));

		StepResult result = stepResults.get(STEP_ADMIN_VIEW);
		result.status = "PASS";
		result.details = "Administrar Negocios page loaded with all expected sections.";
		result.evidence.add(captureScreenshot(STEP_ADMIN_VIEW, appPage, "administrar-negocios-page", true));
	}

	private void validateInformacionGeneral() {
		final Locator infoHeading = firstVisible("Informacion General heading",
				appPage.getByText(Pattern.compile("(?i)Informaci[oó]n\\s+General")));
		final Locator infoSection = infoHeading.locator("xpath=ancestor::*[self::section or self::div][1]");
		final String sectionText = normalizeWhitespace(safeInnerText(infoSection));

		final Matcher emailMatcher = EMAIL_PATTERN.matcher(sectionText);
		Assert.assertTrue("Expected user email to be visible in Informacion General.", emailMatcher.find());
		Assert.assertTrue("Expected BUSINESS PLAN text in Informacion General.",
				isVisible(appPage.getByText(Pattern.compile("(?i)BUSINESS\\s+PLAN")), VISIBILITY_TIMEOUT_MS));
		firstVisible("Cambiar Plan button",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Cambiar\\s+Plan"))));

		final boolean hasNameLikeLine = Arrays.stream(sectionText.split("\\n"))
				.map(String::trim)
				.filter(line -> !line.isEmpty())
				.anyMatch(line -> !line.contains("@") && !line.equalsIgnoreCase("Informacion General")
						&& !line.equalsIgnoreCase("Información General") && !line.equalsIgnoreCase("BUSINESS PLAN")
						&& !line.equalsIgnoreCase("Cambiar Plan"));
		Assert.assertTrue("Expected a visible user name in Informacion General.", hasNameLikeLine);

		stepResults.get(STEP_GENERAL).status = "PASS";
		stepResults.get(STEP_GENERAL).details = "Informacion General contains user name, email, BUSINESS PLAN and Cambiar Plan.";
	}

	private void validateDetallesCuenta() {
		firstVisible("Cuenta creada label", appPage.getByText(Pattern.compile("(?i)Cuenta\\s+creada")));
		firstVisible("Estado activo label", appPage.getByText(Pattern.compile("(?i)Estado\\s+activo")));
		firstVisible("Idioma seleccionado label", appPage.getByText(Pattern.compile("(?i)Idioma\\s+seleccionado")));

		stepResults.get(STEP_DETAILS).status = "PASS";
		stepResults.get(STEP_DETAILS).details = "Detalles de la Cuenta has Cuenta creada, Estado activo and Idioma seleccionado.";
	}

	private void validateTusNegocios() {
		final Locator heading = firstVisible("Tus Negocios heading", appPage.getByText(Pattern.compile("(?i)Tus\\s+Negocios")));
		final Locator section = heading.locator("xpath=ancestor::*[self::section or self::div][1]");

		firstVisible("Agregar Negocio button in Tus Negocios",
				section.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName(Pattern.compile("(?i)Agregar\\s+Negocio"))),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Agregar\\s+Negocio"))));
		firstVisible("Business quota text in Tus Negocios", appPage.getByText(Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios")));

		final String sectionText = normalizeWhitespace(safeInnerText(section));
		final boolean hasBusinessListContent = section.locator("li").count() > 0 || section.locator("[role='row']").count() > 0
				|| section.locator("tr").count() > 0 || sectionText.split("\\n").length >= 3;
		Assert.assertTrue("Expected business list content in Tus Negocios section.", hasBusinessListContent);

		stepResults.get(STEP_BUSINESSES).status = "PASS";
		stepResults.get(STEP_BUSINESSES).details = "Tus Negocios shows business list, Agregar Negocio, and 2 de 3 negocios.";
	}

	private void validateLegalDocument(final String stepKey, final Pattern linkPattern, final Pattern headingPattern) {
		final Locator legalLink = firstVisible(stepKey + " link",
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkPattern)),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(linkPattern)),
				appPage.getByText(linkPattern));

		Page legalPage;
		final boolean[] legalClickTriggered = new boolean[] { false };
		try {
			legalPage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(8_000), () -> {
				legalClickTriggered[0] = true;
				clickAndWait(legalLink, appPage);
			});
		} catch (PlaywrightException ignored) {
			if (!legalClickTriggered[0]) {
				clickAndWait(legalLink, appPage);
			}
			legalPage = appPage;
		}

		waitForUiLoad(legalPage);
		firstVisible(stepKey + " heading", legalPage.getByText(headingPattern));

		final String legalText = normalizeWhitespace(safeInnerText(legalPage.locator("body")));
		Assert.assertTrue("Expected legal content text in " + stepKey + ".",
				legalText != null && legalText.length() > 120);

		final StepResult result = stepResults.get(stepKey);
		result.status = "PASS";
		result.details = "Validated legal page content.";
		result.finalUrl = legalPage.url();
		result.evidence.add(captureScreenshot(stepKey, legalPage, "legal-page", true));

		if (legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
			waitForUiLoad(appPage);
		} else {
			appPage.goBack(new Page.GoBackOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			waitForUiLoad(appPage);
		}
	}

	private void ensureMiNegocioSubmenuVisible() {
		if (isVisible(appPage.getByText(Pattern.compile("(?i)^\\s*Administrar\\s+Negocios\\s*$")), 2_500)) {
			return;
		}

		final Locator negocioSection = firstVisible("Negocio section for submenu reopen",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Negocio\\s*$"))),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Negocio\\s*$"))),
				appPage.getByText(Pattern.compile("(?i)^\\s*Negocio\\s*$")));
		clickAndWait(negocioSection, appPage);

		final Locator miNegocioOption = firstVisible("Mi Negocio submenu reopen option",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Mi\\s+Negocio\\s*$"))),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Mi\\s+Negocio\\s*$"))),
				appPage.getByText(Pattern.compile("(?i)^\\s*Mi\\s+Negocio\\s*$")));
		clickAndWait(miNegocioOption, appPage);
		firstVisible("Administrar Negocios after submenu reopen",
				appPage.getByText(Pattern.compile("(?i)^\\s*Administrar\\s+Negocios\\s*$")));
	}

	private Locator firstVisible(final String description, final Locator... candidates) {
		for (Locator candidate : candidates) {
			final Locator current = candidate.first();
			try {
				current.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(VISIBILITY_TIMEOUT_MS));
				return current;
			} catch (PlaywrightException ignored) {
				// Try next candidate.
			}
		}
		throw new AssertionError("Unable to find visible element: " + description);
	}

	private boolean isVisible(final Locator locator, final int timeoutMs) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
			return true;
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private void clickAndWait(final Locator locator, final Page page) {
		locator.first().click(new Locator.ClickOptions().setTimeout(ACTION_TIMEOUT_MS));
		waitForUiLoad(page);
	}

	private void waitForUiLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(15_000));
		} catch (PlaywrightException ignored) {
			// Continue trying NETWORKIDLE even if DOMCONTENTLOADED timeout occurs.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(7_000));
		} catch (PlaywrightException ignored) {
			// Some pages keep long-lived connections; ignore timeout.
		}
		page.waitForTimeout(600);
	}

	private String captureScreenshot(final String stepKey, final Page page, final String suffix, final boolean fullPage) {
		final String fileName = sanitizeForFile(stepKey + "-" + suffix) + ".png";
		final Path screenshotPath = artifactDirectory.resolve(fileName);
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
		return screenshotPath.toString();
	}

	private boolean runStep(final String stepKey, final StepAction action) {
		try {
			action.run();
			if ("NOT_RUN".equals(stepResults.get(stepKey).status)) {
				stepResults.get(stepKey).status = "PASS";
				stepResults.get(stepKey).details = "Step completed.";
			}
			return true;
		} catch (Throwable throwable) {
			registerFailure(stepKey, throwable);
			return false;
		}
	}

	private boolean runIfPrerequisiteOk(final String stepKey, final String prerequisite, final boolean prerequisiteOk,
			final StepAction action) {
		if (!prerequisiteOk) {
			final String message = "Skipped because prerequisite failed: " + prerequisite + ".";
			stepResults.get(stepKey).status = "FAIL";
			stepResults.get(stepKey).details = message;
			failedSteps.add(stepKey + ": " + message);
			return false;
		}

		return runStep(stepKey, action);
	}

	private void registerFailure(final String stepKey, final Throwable throwable) {
		final String message = throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
		final StepResult result = stepResults.get(stepKey);
		result.status = "FAIL";
		result.details = message;
		failedSteps.add(stepKey + ": " + message);

		if (appPage != null && !appPage.isClosed()) {
			try {
				result.evidence.add(captureScreenshot(stepKey, appPage, "failure", true));
			} catch (PlaywrightException ignored) {
				// Do not shadow the original error.
			}
		}
	}

	private void initializeReport() {
		stepResults.put(STEP_LOGIN, new StepResult());
		stepResults.put(STEP_MENU, new StepResult());
		stepResults.put(STEP_MODAL, new StepResult());
		stepResults.put(STEP_ADMIN_VIEW, new StepResult());
		stepResults.put(STEP_GENERAL, new StepResult());
		stepResults.put(STEP_DETAILS, new StepResult());
		stepResults.put(STEP_BUSINESSES, new StepResult());
		stepResults.put(STEP_TERMS, new StepResult());
		stepResults.put(STEP_PRIVACY, new StepResult());
	}

	private Path createArtifactDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path dir = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(dir);
		return dir;
	}

	private String readConfig(final String envName, final String propertyName) {
		final String envValue = System.getenv(envName);
		if (envValue != null && !envValue.isBlank()) {
			return envValue.trim();
		}
		final String propertyValue = System.getProperty(propertyName);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue.trim();
		}
		return null;
	}

	private String readConfigOrDefault(final String envName, final String propertyName, final String defaultValue) {
		final String configuredValue = readConfig(envName, propertyName);
		return configuredValue == null ? defaultValue : configuredValue;
	}

	private String safeInnerText(final Locator locator) {
		try {
			return locator.innerText();
		} catch (PlaywrightException ignored) {
			return "";
		}
	}

	private String normalizeWhitespace(final String rawText) {
		if (rawText == null) {
			return "";
		}
		return rawText.replace('\u00A0', ' ').replaceAll("[ \t]+", " ").trim();
	}

	private String sanitizeForFile(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private void writeReport() {
		if (artifactDirectory == null) {
			return;
		}

		final StringBuilder json = new StringBuilder();
		json.append("{\n");
		json.append("  \"workflow\": \"saleads_mi_negocio_full_test\",\n");
		json.append("  \"artifacts_dir\": \"").append(escapeJson(artifactDirectory.toString())).append("\",\n");
		json.append("  \"steps\": {\n");

		int stepIndex = 0;
		for (Map.Entry<String, StepResult> entry : stepResults.entrySet()) {
			final String stepName = entry.getKey();
			final StepResult result = entry.getValue();
			json.append("    \"").append(escapeJson(stepName)).append("\": {\n");
			json.append("      \"status\": \"").append(escapeJson(result.status)).append("\",\n");
			json.append("      \"details\": \"").append(escapeJson(result.details)).append("\",\n");
			json.append("      \"final_url\": \"").append(escapeJson(result.finalUrl)).append("\",\n");
			json.append("      \"evidence\": [");
			for (int i = 0; i < result.evidence.size(); i++) {
				if (i > 0) {
					json.append(", ");
				}
				json.append("\"").append(escapeJson(result.evidence.get(i))).append("\"");
			}
			json.append("]\n");
			json.append("    }");
			if (stepIndex < stepResults.size() - 1) {
				json.append(",");
			}
			json.append("\n");
			stepIndex++;
		}

		json.append("  }\n");
		json.append("}\n");

		try {
			Files.writeString(artifactDirectory.resolve("final-report.json"), json.toString(), StandardCharsets.UTF_8);
		} catch (IOException ioException) {
			throw new RuntimeException("Unable to write final report: " + ioException.getMessage(), ioException);
		} finally {
			closeBrowserObjects();
		}
	}

	private void closeBrowserObjects() {
		try {
			if (context != null) {
				context.close();
			}
		} catch (PlaywrightException ignored) {
			// Best effort cleanup.
		}
		try {
			if (browser != null) {
				browser.close();
			}
		} catch (PlaywrightException ignored) {
			// Best effort cleanup.
		}
		try {
			if (playwright != null) {
				playwright.close();
			}
		} catch (PlaywrightException ignored) {
			// Best effort cleanup.
		}
	}

	private String escapeJson(final String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
	}

	@FunctionalInterface
	private interface StepAction {
		void run();
	}

	private static final class StepResult {
		private String status = "NOT_RUN";
		private String details = "";
		private String finalUrl = "";
		private final List<String> evidence = new ArrayList<>();
	}
}
