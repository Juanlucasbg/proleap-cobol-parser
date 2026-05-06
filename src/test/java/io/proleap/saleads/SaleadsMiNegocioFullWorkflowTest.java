package io.proleap.saleads;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String REPORT_FILE_NAME = "saleads-mi-negocio-report.txt";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}");
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, StepResult> results = new LinkedHashMap<>();

	private Playwright playwright;
	private Browser browser;
	private BrowserContext context;
	private Page appPage;
	private Path screenshotDir;
	private Path reportPath;
	private String loginUrl;
	private String googleEmail;

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue("Enable this E2E test with -Dsaleads.e2e=true", Boolean.getBoolean("saleads.e2e"));

		loginUrl = firstNonBlank(System.getProperty("saleads.loginUrl"), System.getenv("SALEADS_LOGIN_URL"));
		Assert.assertNotNull("Provide -Dsaleads.loginUrl=<login-page-url> or SALEADS_LOGIN_URL", loginUrl);

		googleEmail = firstNonBlank(System.getProperty("saleads.google.email"), System.getenv("SALEADS_GOOGLE_EMAIL"));
		if (googleEmail == null) {
			googleEmail = "juanlucasbarbiergarzon@gmail.com";
		}

		screenshotDir = Paths.get("target", "saleads-mi-negocio", "screenshots");
		reportPath = Paths.get("target", "saleads-mi-negocio", REPORT_FILE_NAME);
		Files.createDirectories(screenshotDir);

		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "true"));

		playwright = Playwright.create();
		browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
		context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
		appPage = context.newPage();
		appPage.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
		waitForUi(appPage);
	}

	@After
	public void tearDown() throws IOException {
		if (reportPath != null) {
			writeFinalReport();
		}

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
	public void saleadsMiNegocioFullWorkflow() {
		executeStep("Login", this::stepLoginWithGoogle);
		executeStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		executeStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		executeStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		executeStep("Información General", this::stepValidateInformacionGeneral);
		executeStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		executeStep("Tus Negocios", this::stepValidateTusNegocios);
		executeStep("Términos y Condiciones", () -> stepValidateLegalLink("Términos y Condiciones", "08-terminos-y-condiciones.png"));
		executeStep("Política de Privacidad", () -> stepValidateLegalLink("Política de Privacidad", "09-politica-de-privacidad.png"));

		if (hasFailures()) {
			Assert.fail("SaleADS Mi Negocio workflow has failed validations. Check " + reportPath.toAbsolutePath());
		}
	}

	private void stepLoginWithGoogle() {
		final Locator loginButton = firstVisibleLocator(Arrays.asList(
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
						.setName(Pattern.compile("(?i)(sign in with google|iniciar sesión con google|continuar con google|google)"))),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions()
						.setName(Pattern.compile("(?i)(sign in with google|iniciar sesión con google|continuar con google|google)"))),
				appPage.getByText(Pattern.compile("(?i)(sign in with google|iniciar sesión con google|continuar con google)"))));
		Assert.assertNotNull("Google login button/link was not found", loginButton);

		Page oauthPage = null;
		try {
			oauthPage = context.waitForPage(() -> loginButton.first().click(), new BrowserContext.WaitForPageOptions().setTimeout(7000));
			waitForUi(oauthPage);
		} catch (TimeoutError ignored) {
			clickAndWait(appPage, loginButton, "Google login button");
		}

		if (oauthPage != null) {
			handleGoogleAccountSelection(oauthPage);
			resolveAppPageAfterGoogleAuth();
		} else if (isGooglePage(appPage)) {
			handleGoogleAccountSelection(appPage);
			resolveAppPageAfterGoogleAuth();
		}

		final Locator sidebar = firstVisibleLocator(Arrays.asList(appPage.getByRole(AriaRole.NAVIGATION),
				appPage.locator("aside"), appPage.locator("nav"), appPage.getByText(Pattern.compile("(?i)mi negocio|negocio"))));
		Assert.assertNotNull("Main app sidebar/navigation did not become visible after login", sidebar);
		Assert.assertTrue("Sidebar should be visible after login", sidebar.first().isVisible());

		captureScreenshot(appPage, "01-dashboard-loaded.png", true);
		getResult("Login").details.add("Dashboard screenshot: " + screenshotDir.resolve("01-dashboard-loaded.png").toString());
	}

	private void stepOpenMiNegocioMenu() {
		final Locator negocioSection = appPage.getByText(Pattern.compile("(?i)^\\s*negocio\\s*$"));
		Assert.assertTrue("Sidebar section 'Negocio' should be visible", negocioSection.first().isVisible());

		final Locator miNegocioOption = firstVisibleLocator(Arrays.asList(
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Mi Negocio")),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Mi Negocio")),
				appPage.getByText(Pattern.compile("(?i)^\\s*Mi Negocio\\s*$"))));
		Assert.assertNotNull("Option 'Mi Negocio' was not found", miNegocioOption);
		clickAndWait(appPage, miNegocioOption, "Mi Negocio");

		Assert.assertTrue("'Agregar Negocio' should be visible", appPage.getByText(Pattern.compile("(?i)^\\s*Agregar Negocio\\s*$")).first().isVisible());
		Assert.assertTrue("'Administrar Negocios' should be visible",
				appPage.getByText(Pattern.compile("(?i)^\\s*Administrar Negocios\\s*$")).first().isVisible());

		captureScreenshot(appPage, "02-mi-negocio-expanded.png", false);
		getResult("Mi Negocio menu").details.add("Expanded menu screenshot: " + screenshotDir.resolve("02-mi-negocio-expanded.png"));
	}

	private void stepValidateAgregarNegocioModal() {
		final Locator agregarNegocio = firstVisibleLocator(Arrays.asList(
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Agregar Negocio")),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Agregar Negocio")),
				appPage.getByText(Pattern.compile("(?i)^\\s*Agregar Negocio\\s*$"))));
		Assert.assertNotNull("'Agregar Negocio' action was not found", agregarNegocio);
		clickAndWait(appPage, agregarNegocio, "Agregar Negocio");

		final Locator modalTitle = appPage.getByText(Pattern.compile("(?i)^\\s*Crear Nuevo Negocio\\s*$"));
		modalTitle.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
		Assert.assertTrue("'Crear Nuevo Negocio' title should be visible", modalTitle.first().isVisible());

		final Locator nombreNegocioInput = firstVisibleLocator(Arrays.asList(
				appPage.getByLabel(Pattern.compile("(?i)Nombre del Negocio")),
				appPage.getByPlaceholder(Pattern.compile("(?i)Nombre del Negocio")),
				appPage.locator("input[name*='negocio'], input[id*='negocio']")));
		Assert.assertNotNull("Input 'Nombre del Negocio' should exist", nombreNegocioInput);
		Assert.assertTrue("Text 'Tienes 2 de 3 negocios' should be visible",
				appPage.getByText(Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios")).first().isVisible());
		Assert.assertTrue("Button 'Cancelar' should be visible",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")).first().isVisible());
		Assert.assertTrue("Button 'Crear Negocio' should be visible",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Crear Negocio")).first().isVisible());

		captureScreenshot(appPage, "03-agregar-negocio-modal.png", false);
		getResult("Agregar Negocio modal").details
				.add("Modal screenshot: " + screenshotDir.resolve("03-agregar-negocio-modal.png").toString());

		nombreNegocioInput.first().fill("Negocio Prueba Automatización");
		clickAndWait(appPage, appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")), "Cancelar");
	}

	private void stepOpenAdministrarNegocios() {
		ensureMiNegocioMenuExpanded();

		final Locator administrarNegocios = firstVisibleLocator(Arrays.asList(
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Administrar Negocios")),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Administrar Negocios")),
				appPage.getByText(Pattern.compile("(?i)^\\s*Administrar Negocios\\s*$"))));
		Assert.assertNotNull("Option 'Administrar Negocios' was not found", administrarNegocios);
		clickAndWait(appPage, administrarNegocios, "Administrar Negocios");

		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");

		captureScreenshot(appPage, "04-administrar-negocios-page.png", true);
		getResult("Administrar Negocios view").details
				.add("Account page screenshot: " + screenshotDir.resolve("04-administrar-negocios-page.png").toString());
	}

	private void stepValidateInformacionGeneral() {
		final Locator section = findSectionByHeading("Información General");
		Assert.assertNotNull("Section 'Información General' should exist", section);

		final String sectionText = section.innerText();
		Assert.assertTrue("User email should be visible in 'Información General'", EMAIL_PATTERN.matcher(sectionText).find());
		Assert.assertTrue("Text 'BUSINESS PLAN' should be visible",
				Pattern.compile("(?i)BUSINESS\\s+PLAN").matcher(sectionText).find());
		Assert.assertTrue("Button 'Cambiar Plan' should be visible",
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cambiar Plan")).first().isVisible());
		Assert.assertTrue("A user name should be visible in 'Información General'", containsLikelyUserName(sectionText));
	}

	private void stepValidateDetallesCuenta() {
		final Locator section = findSectionByHeading("Detalles de la Cuenta");
		Assert.assertNotNull("Section 'Detalles de la Cuenta' should exist", section);
		assertSectionContainsText(section, "Cuenta creada");
		assertSectionContainsText(section, "Estado activo");
		assertSectionContainsText(section, "Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		final Locator section = findSectionByHeading("Tus Negocios");
		Assert.assertNotNull("Section 'Tus Negocios' should exist", section);

		final Locator businessList = section.locator("ul li, [role='listitem'], table tbody tr, div[class*='business'], div[class*='negocio']");
		Assert.assertTrue("Business list should be visible", businessList.count() > 0 || section.innerText().contains("Negocio"));

		Assert.assertTrue("Button 'Agregar Negocio' should exist in 'Tus Negocios'",
				section.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Agregar Negocio")).first().isVisible()
						|| appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Agregar Negocio")).first().isVisible());
		Assert.assertTrue("Text 'Tienes 2 de 3 negocios' should be visible",
				section.getByText(Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios")).first().isVisible()
						|| appPage.getByText(Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios")).first().isVisible());
	}

	private void stepValidateLegalLink(final String linkText, final String screenshotName) {
		final Page legalPage;
		final String previousUrl = appPage.url();
		final Locator legalLink = firstVisibleLocator(Arrays.asList(
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkText)),
				appPage.getByText(Pattern.compile("(?i)^\\s*" + Pattern.quote(linkText) + "\\s*$"))));
		Assert.assertNotNull("Legal link '" + linkText + "' was not found", legalLink);

		Page openedPage = null;
		try {
			openedPage = context.waitForPage(() -> legalLink.first().click(), new BrowserContext.WaitForPageOptions().setTimeout(7000));
			waitForUi(openedPage);
		} catch (TimeoutError ignored) {
			clickAndWait(appPage, legalLink, linkText);
		}

		if (openedPage != null) {
			legalPage = openedPage;
		} else {
			legalPage = appPage;
		}

		assertTextVisibleOnPage(legalPage, linkText);
		final String legalText = legalPage.locator("body").innerText();
		Assert.assertTrue("Legal content text should be visible for '" + linkText + "'",
				legalText != null && legalText.replaceAll("\\s+", " ").trim().length() > 200);

		captureScreenshot(legalPage, screenshotName, true);
		getResult(linkText).details.add("Screenshot: " + screenshotDir.resolve(screenshotName));
		getResult(linkText).details.add("Final URL: " + legalPage.url());

		if (openedPage != null) {
			openedPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else if (!previousUrl.equals(appPage.url())) {
			appPage.goBack();
			waitForUi(appPage);
		}
	}

	private void executeStep(final String stepName, final StepExecutable executable) {
		if (hasPrerequisiteFailure(stepName)) {
			final StepResult blocked = getResult(stepName);
			blocked.status = "FAIL";
			blocked.details.add("Not executed due to previous failure");
			return;
		}

		try {
			executable.run();
			getResult(stepName).status = "PASS";
		} catch (Throwable throwable) {
			final StepResult result = getResult(stepName);
			result.status = "FAIL";
			result.details.add(cleanThrowableMessage(throwable));
		}
	}

	private boolean hasPrerequisiteFailure(final String stepName) {
		final int index = REPORT_FIELDS.indexOf(stepName);
		if (index < 0) {
			return false;
		}
		for (int i = 0; i < index; i++) {
			final StepResult previous = results.get(REPORT_FIELDS.get(i));
			if (previous != null && "FAIL".equals(previous.status)) {
				return true;
			}
		}
		return false;
	}

	private StepResult getResult(final String stepName) {
		return results.computeIfAbsent(stepName, StepResult::new);
	}

	private boolean hasFailures() {
		for (final String field : REPORT_FIELDS) {
			final StepResult result = getResult(field);
			if (!"PASS".equals(result.status)) {
				return true;
			}
		}
		return false;
	}

	private void ensureMiNegocioMenuExpanded() {
		if (isTextVisible("Administrar Negocios")) {
			return;
		}
		final Locator miNegocioOption = firstVisibleLocator(Arrays.asList(
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Mi Negocio")),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Mi Negocio")),
				appPage.getByText(Pattern.compile("(?i)^\\s*Mi Negocio\\s*$"))));
		Assert.assertNotNull("Could not re-open 'Mi Negocio' menu", miNegocioOption);
		clickAndWait(appPage, miNegocioOption, "Mi Negocio");
	}

	private void handleGoogleAccountSelection(final Page googlePage) {
		waitForUi(googlePage);
		final Locator account = googlePage.getByText(googleEmail, new Page.GetByTextOptions().setExact(true));
		if (account.count() > 0 && account.first().isVisible()) {
			clickAndWait(googlePage, account, "Google account selection");
		}
	}

	private void resolveAppPageAfterGoogleAuth() {
		for (final Page page : context.pages()) {
			if (page.isClosed()) {
				continue;
			}
			if (!isGooglePage(page)) {
				appPage = page;
				appPage.bringToFront();
				waitForUi(appPage);
			}
		}
	}

	private boolean isGooglePage(final Page page) {
		final String url = page.url();
		return url != null && url.contains("accounts.google.com");
	}

	private Locator firstVisibleLocator(final List<Locator> locators) {
		for (final Locator locator : locators) {
			try {
				if (locator.count() > 0 && locator.first().isVisible()) {
					return locator.first();
				}
			} catch (PlaywrightException ignored) {
				// Continue to next locator strategy.
			}
		}
		return null;
	}

	private void clickAndWait(final Page page, final Locator locator, final String actionName) {
		locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
		locator.first().click();
		waitForUi(page);
		getResult(currentStepName()).details.add("Clicked: " + actionName);
	}

	private String currentStepName() {
		if (results.isEmpty()) {
			return REPORT_FIELDS.get(0);
		}
		String last = REPORT_FIELDS.get(0);
		for (final String key : results.keySet()) {
			last = key;
		}
		return last;
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (PlaywrightException ignored) {
			// Some interactions do not trigger full navigation.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(15000));
		} catch (PlaywrightException ignored) {
			page.waitForTimeout(600);
		}
	}

	private void captureScreenshot(final Page page, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotDir.resolve(fileName)).setFullPage(fullPage));
	}

	private void assertTextVisible(final String text) {
		assertTextVisibleOnPage(appPage, text);
	}

	private void assertTextVisibleOnPage(final Page page, final String text) {
		final Locator locator = page.getByText(Pattern.compile("(?i).*" + Pattern.quote(text) + ".*"));
		locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
		Assert.assertTrue("Text '" + text + "' should be visible", locator.first().isVisible());
	}

	private boolean isTextVisible(final String text) {
		final Locator locator = appPage.getByText(Pattern.compile("(?i).*" + Pattern.quote(text) + ".*"));
		return locator.count() > 0 && locator.first().isVisible();
	}

	private Locator findSectionByHeading(final String headingText) {
		final Locator heading = appPage.getByText(Pattern.compile("(?i)^\\s*" + Pattern.quote(headingText) + "\\s*$"));
		if (heading.count() == 0) {
			return null;
		}
		final Locator section = appPage.locator("section, article, div").filter(new Locator.FilterOptions().setHasText(headingText));
		if (section.count() > 0 && section.first().isVisible()) {
			return section.first();
		}
		return heading.first().locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
	}

	private void assertSectionContainsText(final Locator section, final String text) {
		final Locator match = section.getByText(Pattern.compile("(?i).*" + Pattern.quote(text) + ".*"));
		Assert.assertTrue("Text '" + text + "' should be visible", match.count() > 0 && match.first().isVisible());
	}

	private boolean containsLikelyUserName(final String sectionText) {
		String cleaned = sectionText;
		cleaned = cleaned.replaceAll("(?i)Información General", "");
		cleaned = cleaned.replaceAll("(?i)BUSINESS\\s+PLAN", "");
		cleaned = cleaned.replaceAll("(?i)Cambiar Plan", "");
		cleaned = cleaned.replaceAll("(?i)Tienes\\s+\\d+\\s+de\\s+\\d+\\s+negocios", "");
		cleaned = EMAIL_PATTERN.matcher(cleaned).replaceAll("");

		for (final String line : cleaned.split("\\R")) {
			final String value = line.trim();
			if (value.matches("(?iu)[\\p{L}][\\p{L} .'-]{2,}")) {
				return true;
			}
		}
		return false;
	}

	private void writeFinalReport() throws IOException {
		Files.createDirectories(reportPath.getParent());
		final StringBuilder report = new StringBuilder();
		report.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		report.append("Generated at: ").append(Instant.now()).append(System.lineSeparator());
		report.append("Login URL: ").append(loginUrl == null ? "(not provided)" : loginUrl).append(System.lineSeparator());
		report.append(System.lineSeparator());
		report.append("Final Report").append(System.lineSeparator());
		report.append("============").append(System.lineSeparator());

		for (final String field : REPORT_FIELDS) {
			final StepResult result = getResult(field);
			report.append(field).append(": ").append(result.status).append(System.lineSeparator());
			for (final String detail : result.details) {
				report.append("  - ").append(detail).append(System.lineSeparator());
			}
		}

		Files.write(reportPath, report.toString().getBytes(StandardCharsets.UTF_8));
	}

	private String cleanThrowableMessage(final Throwable throwable) {
		if (throwable == null) {
			return "Unknown error";
		}
		if (throwable.getMessage() != null && !throwable.getMessage().trim().isEmpty()) {
			return throwable.getMessage().trim();
		}
		return throwable.getClass().getSimpleName();
	}

	private String firstNonBlank(final String first, final String second) {
		if (first != null && !first.trim().isEmpty()) {
			return first.trim();
		}
		if (second != null && !second.trim().isEmpty()) {
			return second.trim();
		}
		return null;
	}

	@FunctionalInterface
	private interface StepExecutable {
		void run();
	}

	private static class StepResult {
		private final String stepName;
		private String status = "FAIL";
		private final List<String> details = new ArrayList<>();

		private StepResult(final String stepName) {
			this.stepName = stepName;
		}
	}
}
