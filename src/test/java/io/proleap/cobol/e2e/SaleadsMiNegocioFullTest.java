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
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final String GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final double DEFAULT_TIMEOUT_MS = 30000;
	private static final double UI_SETTLE_DELAY_MS = 800;
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, StepResult> stepResults = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();
	private Path evidenceDir;

	@Test
	public void runSaleadsMiNegocioWorkflow() throws Exception {
		evidenceDir = createEvidenceDir();

		final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "false"));
		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");

		try (Playwright playwright = Playwright.create();
		     Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
			     .setHeadless(headless)
			     .setSlowMo(250));
		     BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000))) {

			final Page page = context.newPage();
			page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);

			if (loginUrl != null && !loginUrl.isBlank()) {
				page.navigate(loginUrl);
				waitForUi(page);
			}

			runStep("Login", () -> stepLoginWithGoogle(page));
			runStep("Mi Negocio menu", () -> stepOpenMiNegocioMenu(page));
			runStep("Agregar Negocio modal", () -> stepValidateAgregarNegocioModal(page));
			runStep("Administrar Negocios view", () -> stepOpenAdministrarNegocios(page));
			runStep("Información General", () -> stepValidateInformacionGeneral(page));
			runStep("Detalles de la Cuenta", () -> stepValidateDetallesCuenta(page));
			runStep("Tus Negocios", () -> stepValidateTusNegocios(page));
			runStep("Términos y Condiciones", () -> stepValidateLegalDocument(page, context, "Términos y Condiciones"));
			runStep("Política de Privacidad", () -> stepValidateLegalDocument(page, context, "Política de Privacidad"));
		}

		final Path reportPath = writeFinalReport();
		final boolean allPassed = stepResults.values().stream().allMatch(StepResult::passed);
		Assert.assertTrue("One or more validations failed. Review report: " + reportPath.toAbsolutePath(), allPassed);
	}

	private StepResult stepLoginWithGoogle(final Page page) throws Exception {
		final Locator loginButton = pickFirst(
			page.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(Pattern.compile("(?i)(sign in with google|iniciar sesi[oó]n con google|continuar con google|google)"))),
			page.getByText(Pattern.compile("(?i)(sign in with google|iniciar sesi[oó]n con google|continuar con google)"))
		);

		assertVisible(loginButton, "Login button or 'Sign in with Google'");

		Page popupPage = null;
		try {
			popupPage = page.waitForPopup(new Page.WaitForPopupOptions().setTimeout(8000), () -> {
				loginButton.click();
				waitForUi(page);
			});
		} catch (PlaywrightException ignored) {
			// Google login can open in the same tab.
		}

		final Page authPage = popupPage != null ? popupPage : page;
		waitForUi(authPage);

		final Locator accountOption = authPage.getByText(GOOGLE_EMAIL, new Page.GetByTextOptions().setExact(true));
		if (accountOption.count() > 0 && accountOption.first().isVisible()) {
			accountOption.first().click();
			waitForUi(authPage);
		}

		waitForNegocioSidebar(page);

		final String screenshot = takeScreenshot(page, "01-dashboard-loaded.png", true);
		return new StepResult(true, "Dashboard loaded and left sidebar navigation is visible.", screenshot);
	}

	private StepResult stepOpenMiNegocioMenu(final Page page) throws Exception {
		clickByVisibleText(page, "Negocio");
		clickByVisibleText(page, "Mi Negocio");

		assertVisible(page.getByText(Pattern.compile("(?i)^\\s*Agregar Negocio\\s*$")).first(), "'Agregar Negocio' option");
		assertVisible(page.getByText(Pattern.compile("(?i)^\\s*Administrar Negocios\\s*$")).first(), "'Administrar Negocios' option");

		final String screenshot = takeScreenshot(page, "02-mi-negocio-expanded.png", false);
		return new StepResult(true, "'Mi Negocio' submenu expanded with expected options.", screenshot);
	}

	private StepResult stepValidateAgregarNegocioModal(final Page page) throws Exception {
		clickByVisibleText(page, "Agregar Negocio");

		final Locator modalTitle = page.getByText(Pattern.compile("(?i)Crear Nuevo Negocio")).first();
		assertVisible(modalTitle, "Modal title 'Crear Nuevo Negocio'");

		Locator businessNameInput = page.getByLabel(Pattern.compile("(?i)Nombre del Negocio")).first();
		if (businessNameInput.count() == 0) {
			businessNameInput = page.getByPlaceholder(Pattern.compile("(?i)Nombre del Negocio")).first();
		}
		assertVisible(businessNameInput, "Input field 'Nombre del Negocio'");
		assertVisible(page.getByText(Pattern.compile("(?i)Tienes\\s*2\\s*de\\s*3\\s*negocios")).first(), "Business quota text");
		assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Cancelar\\s*$"))).first(),
			"Button 'Cancelar'");
		assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Crear Negocio\\s*$"))).first(),
			"Button 'Crear Negocio'");

		final String screenshot = takeScreenshot(page, "03-agregar-negocio-modal.png", false);

		businessNameInput.click();
		businessNameInput.fill("Negocio Prueba Automatización");
		waitForUi(page);
		clickByVisibleText(page, "Cancelar");
		modalTitle.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.HIDDEN).setTimeout(DEFAULT_TIMEOUT_MS));

		return new StepResult(true, "Agregar Negocio modal validated and closed.", screenshot);
	}

	private StepResult stepOpenAdministrarNegocios(final Page page) throws Exception {
		final Locator administrarNegociosOption = page.getByText(Pattern.compile("(?i)^\\s*Administrar Negocios\\s*$")).first();
		if (!isVisible(administrarNegociosOption)) {
			clickByVisibleText(page, "Mi Negocio");
		}

		clickByVisibleText(page, "Administrar Negocios");

		assertVisible(page.getByText(Pattern.compile("(?i)Información General")).first(), "Section 'Información General'");
		assertVisible(page.getByText(Pattern.compile("(?i)Detalles de la Cuenta")).first(), "Section 'Detalles de la Cuenta'");
		assertVisible(page.getByText(Pattern.compile("(?i)Tus Negocios")).first(), "Section 'Tus Negocios'");
		assertVisible(page.getByText(Pattern.compile("(?i)Sección Legal")).first(), "Section 'Sección Legal'");

		final String screenshot = takeScreenshot(page, "04-administrar-negocios.png", true);
		return new StepResult(true, "Administrar Negocios page loaded with all required sections.", screenshot);
	}

	private StepResult stepValidateInformacionGeneral(final Page page) {
		final Locator infoSection = sectionContaining(page, "Información General");
		final String infoText = safeInnerText(infoSection);

		Assert.assertTrue("User name should be visible in Información General section.", hasLikelyUserName(infoText));
		Assert.assertTrue("User email should be visible in Información General section.", EMAIL_PATTERN.matcher(infoText).find());
		assertVisible(page.getByText(Pattern.compile("(?i)BUSINESS PLAN")).first(), "Text 'BUSINESS PLAN'");
		assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Cambiar Plan"))).first(),
			"Button 'Cambiar Plan'");

		return new StepResult(true, "Información General validated (name, email, plan and button).", null);
	}

	private StepResult stepValidateDetallesCuenta(final Page page) {
		assertVisible(page.getByText(Pattern.compile("(?i)Cuenta creada")).first(), "'Cuenta creada' field");
		assertVisible(page.getByText(Pattern.compile("(?i)Estado activo")).first(), "'Estado activo' field");
		assertVisible(page.getByText(Pattern.compile("(?i)Idioma seleccionado")).first(), "'Idioma seleccionado' field");

		return new StepResult(true, "Detalles de la Cuenta validated.", null);
	}

	private StepResult stepValidateTusNegocios(final Page page) {
		final Locator businessesSection = sectionContaining(page, "Tus Negocios");
		final String businessesText = safeInnerText(businessesSection);

		assertVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Agregar Negocio"))).first(),
			"Button 'Agregar Negocio'");
		assertVisible(page.getByText(Pattern.compile("(?i)Tienes\\s*2\\s*de\\s*3\\s*negocios")).first(), "Business quota text");
		Assert.assertTrue("Business list should be visible in 'Tus Negocios'.",
			businessesSection.locator("li, article, tr").count() > 0 || businessesText.split("\\R").length >= 4);

		return new StepResult(true, "'Tus Negocios' section validated.", null);
	}

	private StepResult stepValidateLegalDocument(final Page page, final BrowserContext context, final String legalLinkText) throws Exception {
		final Locator legalLink = page.getByText(Pattern.compile("(?i)^\\s*" + Pattern.quote(legalLinkText) + "\\s*$")).first();
		assertVisible(legalLink, "Legal link '" + legalLinkText + "'");
		final String appUrlBefore = page.url();

		Page legalPage = null;
		try {
			legalPage = context.waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(7000), () -> {
				legalLink.click();
				waitForUi(page);
			});
		} catch (PlaywrightException ignored) {
			// Link can navigate in the same tab.
		}

		final Page targetPage = legalPage != null ? legalPage : page;
		waitForUi(targetPage);

		assertVisible(targetPage.getByText(Pattern.compile("(?i)" + Pattern.quote(legalLinkText))).first(),
			"Heading '" + legalLinkText + "'");
		final String legalBodyText = targetPage.locator("body").innerText();
		Assert.assertTrue("Legal content text should be visible for '" + legalLinkText + "'.", legalBodyText.trim().length() > 150);

		final String finalUrl = targetPage.url();
		legalUrls.put(legalLinkText, finalUrl);
		final String screenshot = takeScreenshot(targetPage,
			"Términos y Condiciones".equals(legalLinkText) ? "05-terminos-y-condiciones.png" : "06-politica-de-privacidad.png",
			true);

		if (targetPage != page) {
			targetPage.close();
			page.bringToFront();
		} else if (!sameUrlIgnoringHash(appUrlBefore, targetPage.url())) {
			page.goBack();
			waitForUi(page);
		}

		return new StepResult(true, "Validated legal page and captured URL: " + finalUrl, screenshot);
	}

	private void runStep(final String stepName, final StepAction action) {
		try {
			final StepResult result = action.run();
			stepResults.put(stepName, result);
		} catch (Throwable error) {
			stepResults.put(stepName, new StepResult(false, summarizeError(error), null));
		}
	}

	private void clickByVisibleText(final Page page, final String visibleText) {
		final Locator locator = pickFirst(
			page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*" + Pattern.quote(visibleText) + "\\s*$"))),
			page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*" + Pattern.quote(visibleText) + "\\s*$"))),
			page.getByText(Pattern.compile("(?i)^\\s*" + Pattern.quote(visibleText) + "\\s*$"))
		);
		assertVisible(locator, "'" + visibleText + "'");
		locator.click();
		waitForUi(page);
	}

	private void waitForNegocioSidebar(final Page page) {
		final Locator negocio = page.getByText(Pattern.compile("(?i)^\\s*Negocio\\s*$")).first();
		assertVisible(negocio, "Left sidebar with 'Negocio'");
	}

	private Locator sectionContaining(final Page page, final String headingText) {
		final Locator heading = page.getByText(Pattern.compile("(?i)" + Pattern.quote(headingText))).first();
		assertVisible(heading, "Section heading '" + headingText + "'");

		final Locator section = page.locator("section,article,div").filter(new Locator.FilterOptions().setHas(heading)).first();
		if (section.count() > 0) {
			return section;
		}
		return page.locator("body").first();
	}

	private String takeScreenshot(final Page page, final String fileName, final boolean fullPage) throws IOException {
		final Path screenshotPath = evidenceDir.resolve(fileName);
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
		return screenshotPath.toString();
	}

	private Path writeFinalReport() throws IOException {
		final List<String> orderedReportFields = List.of(
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

		final StringBuilder reportBuilder = new StringBuilder();
		reportBuilder.append("Test: ").append(TEST_NAME).append(System.lineSeparator());
		reportBuilder.append("Generated: ").append(LocalDateTime.now()).append(System.lineSeparator()).append(System.lineSeparator());

		for (final String field : orderedReportFields) {
			final StepResult result = stepResults.getOrDefault(field, new StepResult(false, "Step was not executed.", null));
			reportBuilder.append(field).append(": ").append(result.passed ? "PASS" : "FAIL").append(System.lineSeparator());

			if (result.details != null && !result.details.isBlank()) {
				reportBuilder.append("  Details: ").append(result.details).append(System.lineSeparator());
			}

			if (result.screenshotPath != null && !result.screenshotPath.isBlank()) {
				reportBuilder.append("  Screenshot: ").append(result.screenshotPath).append(System.lineSeparator());
			}
		}

		if (!legalUrls.isEmpty()) {
			reportBuilder.append(System.lineSeparator()).append("Final URLs:").append(System.lineSeparator());
			for (Map.Entry<String, String> urlEntry : legalUrls.entrySet()) {
				reportBuilder.append("  ").append(urlEntry.getKey()).append(": ").append(urlEntry.getValue()).append(System.lineSeparator());
			}
		}

		final Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.writeString(reportPath, reportBuilder.toString(), StandardCharsets.UTF_8);
		System.out.println(reportBuilder);
		return reportPath;
	}

	private Path createEvidenceDir() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path dir = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(dir);
		return dir;
	}

	private void waitForUi(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		page.waitForTimeout(UI_SETTLE_DELAY_MS);
	}

	private void assertVisible(final Locator locator, final String label) {
		locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));
		Assert.assertTrue(label + " should be visible.", locator.first().isVisible());
	}

	private boolean isVisible(final Locator locator) {
		try {
			return locator.count() > 0 && locator.first().isVisible();
		} catch (PlaywrightException e) {
			return false;
		}
	}

	private Locator pickFirst(final Locator... options) {
		for (final Locator option : options) {
			if (option.count() > 0) {
				return option.first();
			}
		}
		return options[0].first();
	}

	private String safeInnerText(final Locator locator) {
		try {
			return locator.innerText();
		} catch (PlaywrightException e) {
			return "";
		}
	}

	private boolean hasLikelyUserName(final String infoText) {
		for (final String rawLine : infoText.split("\\R")) {
			final String line = rawLine.trim();
			if (line.length() < 4) {
				continue;
			}
			if (line.contains("@")) {
				continue;
			}
			final String lineUpper = line.toUpperCase();
			if (lineUpper.contains("INFORMACIÓN GENERAL")
				|| lineUpper.contains("BUSINESS PLAN")
				|| lineUpper.contains("CAMBIAR PLAN")) {
				continue;
			}
			if (line.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ]{3,}.*")) {
				return true;
			}
		}
		return false;
	}

	private boolean sameUrlIgnoringHash(final String left, final String right) {
		return stripHash(left).equals(stripHash(right));
	}

	private String stripHash(final String value) {
		final int hashIndex = value.indexOf('#');
		return hashIndex >= 0 ? value.substring(0, hashIndex) : value;
	}

	private String summarizeError(final Throwable error) {
		final String message = error.getMessage();
		if (message == null || message.isBlank()) {
			return error.getClass().getSimpleName();
		}
		return message.replaceAll("\\s+", " ").trim();
	}

	@FunctionalInterface
	private interface StepAction {
		StepResult run() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String details;
		private final String screenshotPath;

		private StepResult(final boolean passed, final String details, final String screenshotPath) {
			this.passed = passed;
			this.details = details;
			this.screenshotPath = screenshotPath;
		}

		private boolean passed() {
			return passed;
		}
	}
}
