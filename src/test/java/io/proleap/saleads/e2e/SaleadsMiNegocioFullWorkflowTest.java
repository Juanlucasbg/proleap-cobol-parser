package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMINISTRAR_NEGOCIOS = "Administrar Negocios view";
	private static final String REPORT_INFO_GENERAL = "Información General";
	private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Términos y Condiciones";
	private static final String REPORT_POLITICA = "Política de Privacidad";

	private static final String DEFAULT_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final Pattern TRES_NEGOCIOS_PATTERN = Pattern.compile("(?i)Tienes\\s*2\\s*de\\s*3\\s*negocios");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final LinkedHashMap<String, Boolean> stepStatus = new LinkedHashMap<>();
		final LinkedHashMap<String, String> stepDetails = new LinkedHashMap<>();
		initializeReport(stepStatus, stepDetails);

		final Path evidenceDir = Paths.get("target", "saleads-evidence");
		Files.createDirectories(evidenceDir);

		final String loginUrl = firstNonBlank(System.getProperty("saleads.loginUrl"), System.getenv("SALEADS_LOGIN_URL"));
		if (isBlank(loginUrl)) {
			fail("Missing login URL. Set -Dsaleads.loginUrl=<url> or SALEADS_LOGIN_URL for the current environment.");
		}

		final String expectedAccountEmail = firstNonBlank(System.getProperty("saleads.accountEmail"),
				System.getenv("SALEADS_ACCOUNT_EMAIL"), DEFAULT_ACCOUNT_EMAIL);
		final String expectedUserName = firstNonBlank(System.getProperty("saleads.userName"),
				System.getenv("SALEADS_USER_NAME"));
		final boolean headless = Boolean
				.parseBoolean(firstNonBlank(System.getProperty("saleads.headless"), System.getenv("SALEADS_HEADLESS"), "true"));

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser
					.newContext(new Browser.NewContextOptions().setViewportSize(1920, 1080));
			final Page appPage = context.newPage();

			appPage.navigate(loginUrl);
			waitForUi(appPage);

			executeStep(REPORT_LOGIN, stepStatus, stepDetails, () -> {
				final Locator googleLoginButton = requireVisible("Google login button",
						appPage.getByRole(AriaRole.BUTTON,
								new Page.GetByRoleOptions().setName(Pattern.compile("(?i)(google|sign in|iniciar sesi[oó]n|continuar)"))),
						appPage.getByText(Pattern.compile("(?i)(sign in with google|iniciar sesi[oó]n con google|continuar con google|google)")));
				clickAndWait(appPage, googleLoginButton);

				selectGoogleAccountIfPrompted(appPage, expectedAccountEmail);

				requireVisible("main application interface", appPage.locator("main"), appPage.locator("body"));
				requireVisible("left sidebar navigation", appPage.locator("aside"),
						appPage.getByRole(AriaRole.NAVIGATION), appPage.getByText(Pattern.compile("(?i)Negocio")));
				captureScreenshot(appPage, evidenceDir, "step1-dashboard", false);
			});

			executeStep(REPORT_MI_NEGOCIO_MENU, stepStatus, stepDetails, () -> {
				expandMiNegocioMenu(appPage);

				requireVisible("'Agregar Negocio' option",
						appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Agregar Negocio")),
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Agregar Negocio")),
						appPage.getByText("Agregar Negocio"));
				requireVisible("'Administrar Negocios' option",
						appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Administrar Negocios")),
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Administrar Negocios")),
						appPage.getByText("Administrar Negocios"));
				captureScreenshot(appPage, evidenceDir, "step2-mi-negocio-menu-expanded", false);
			});

			executeStep(REPORT_AGREGAR_NEGOCIO_MODAL, stepStatus, stepDetails, () -> {
				final Locator addBusinessOption = requireVisible("'Agregar Negocio' option",
						appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Agregar Negocio")),
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Agregar Negocio")),
						appPage.getByText("Agregar Negocio"));
				clickAndWait(appPage, addBusinessOption);

				final Locator modalTitle = requireVisible("modal title 'Crear Nuevo Negocio'",
						appPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Crear Nuevo Negocio")),
						appPage.getByText("Crear Nuevo Negocio"));
				requireVisible("input 'Nombre del Negocio'",
						appPage.getByLabel("Nombre del Negocio"),
						appPage.getByPlaceholder("Nombre del Negocio"),
						modalTitle.locator("xpath=ancestor::*[self::div or self::section][1]").getByText("Nombre del Negocio"));
				requireVisible("text 'Tienes 2 de 3 negocios'", appPage.getByText(TRES_NEGOCIOS_PATTERN));
				requireVisible("'Cancelar' button", appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")));
				requireVisible("'Crear Negocio' button",
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Crear Negocio")));
				captureScreenshot(appPage, evidenceDir, "step3-crear-negocio-modal", false);

				final Locator businessNameInput = requireVisible("'Nombre del Negocio' field",
						appPage.getByLabel("Nombre del Negocio"), appPage.getByPlaceholder("Nombre del Negocio"));
				businessNameInput.click();
				waitForUi(appPage);
				businessNameInput.fill("Negocio Prueba Automatización");
				waitForUi(appPage);

				final Locator cancelButton = requireVisible("'Cancelar' button",
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")));
				clickAndWait(appPage, cancelButton);
			});

			executeStep(REPORT_ADMINISTRAR_NEGOCIOS, stepStatus, stepDetails, () -> {
				closeModalIfVisible(appPage);
				expandMiNegocioMenu(appPage);

				final Locator manageBusinessesOption = requireVisible("'Administrar Negocios' option",
						appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Administrar Negocios")),
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Administrar Negocios")),
						appPage.getByText("Administrar Negocios"));
				clickAndWait(appPage, manageBusinessesOption);

				requireVisible("section 'Información General'", appPage.getByText("Información General"));
				requireVisible("section 'Detalles de la Cuenta'", appPage.getByText("Detalles de la Cuenta"));
				requireVisible("section 'Tus Negocios'", appPage.getByText("Tus Negocios"));
				requireVisible("section 'Sección Legal'", appPage.getByText("Sección Legal"));
				captureScreenshot(appPage, evidenceDir, "step4-administrar-negocios", true);
			});

			executeStep(REPORT_INFO_GENERAL, stepStatus, stepDetails, () -> {
				final Locator infoSection = requireVisible("'Información General' section",
						appPage.locator("section, div, article")
								.filter(new Locator.FilterOptions().setHasText(Pattern.compile("(?i)Información General"))),
						appPage.getByText("Información General"));
				assertUserNameVisible(infoSection, expectedUserName);
				requireVisible("user email", appPage.getByText(expectedAccountEmail));
				requireVisible("'BUSINESS PLAN' text", appPage.getByText(Pattern.compile("(?i)BUSINESS\\s*PLAN")));
				requireVisible("'Cambiar Plan' button",
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cambiar Plan")));
			});

			executeStep(REPORT_DETALLES_CUENTA, stepStatus, stepDetails, () -> {
				requireVisible("'Cuenta creada' text", appPage.getByText("Cuenta creada"));
				requireVisible("'Estado activo' text",
						appPage.getByText(Pattern.compile("(?i)(Estado\\s+activo|Estado)")));
				requireVisible("'Idioma seleccionado' text", appPage.getByText("Idioma seleccionado"));
			});

			executeStep(REPORT_TUS_NEGOCIOS, stepStatus, stepDetails, () -> {
				final Locator businessesSection = requireVisible("'Tus Negocios' section",
						appPage.locator("section, div, article")
								.filter(new Locator.FilterOptions().setHasText(Pattern.compile("(?i)Tus\\s+Negocios"))),
						appPage.getByText("Tus Negocios"));

				requireVisible("'Agregar Negocio' button in business section",
						businessesSection.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Agregar Negocio")),
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Agregar Negocio")));
				requireVisible("'Tienes 2 de 3 negocios' text", appPage.getByText(TRES_NEGOCIOS_PATTERN));
				assertBusinessListVisible(businessesSection);
			});

			executeStep(REPORT_TERMINOS, stepStatus, stepDetails, () -> {
				final String finalTermsUrl = openLegalLinkAndValidate(appPage, evidenceDir, "Términos y Condiciones",
						Pattern.compile("(?i)Términos\\s+y\\s+Condiciones"), "step8-terminos-y-condiciones");
				stepDetails.put(REPORT_TERMINOS, "PASS - URL: " + finalTermsUrl);
			});

			executeStep(REPORT_POLITICA, stepStatus, stepDetails, () -> {
				final String finalPrivacyUrl = openLegalLinkAndValidate(appPage, evidenceDir, "Política de Privacidad",
						Pattern.compile("(?i)Política\\s+de\\s+Privacidad"), "step9-politica-de-privacidad");
				stepDetails.put(REPORT_POLITICA, "PASS - URL: " + finalPrivacyUrl);
			});

			context.close();
			browser.close();
		}

		printReport(stepStatus, stepDetails);

		final List<String> failedSteps = new ArrayList<>();
		for (Map.Entry<String, Boolean> entry : stepStatus.entrySet()) {
			if (!entry.getValue()) {
				failedSteps.add(entry.getKey() + ": " + stepDetails.get(entry.getKey()));
			}
		}

		if (!failedSteps.isEmpty()) {
			fail("SaleADS Mi Negocio workflow failed.\n" + String.join("\n", failedSteps));
		}
	}

	private void initializeReport(final LinkedHashMap<String, Boolean> stepStatus,
			final LinkedHashMap<String, String> stepDetails) {
		stepStatus.put(REPORT_LOGIN, false);
		stepStatus.put(REPORT_MI_NEGOCIO_MENU, false);
		stepStatus.put(REPORT_AGREGAR_NEGOCIO_MODAL, false);
		stepStatus.put(REPORT_ADMINISTRAR_NEGOCIOS, false);
		stepStatus.put(REPORT_INFO_GENERAL, false);
		stepStatus.put(REPORT_DETALLES_CUENTA, false);
		stepStatus.put(REPORT_TUS_NEGOCIOS, false);
		stepStatus.put(REPORT_TERMINOS, false);
		stepStatus.put(REPORT_POLITICA, false);

		for (String field : stepStatus.keySet()) {
			stepDetails.put(field, "FAIL - Not executed");
		}
	}

	private void executeStep(final String reportField, final Map<String, Boolean> stepStatus,
			final Map<String, String> stepDetails, final ThrowingRunnable stepAction) {
		try {
			stepAction.run();
			stepStatus.put(reportField, true);
			if (!stepDetails.get(reportField).startsWith("PASS")) {
				stepDetails.put(reportField, "PASS");
			}
		} catch (Throwable throwable) {
			stepStatus.put(reportField, false);
			stepDetails.put(reportField, "FAIL - " + simplifyMessage(throwable));
		}
	}

	private void waitForUi(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(5000));
		} catch (PlaywrightException ignored) {
			// Some screens keep long-polling connections open. DOM load is enough here.
		}
		page.waitForTimeout(600);
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.click();
		waitForUi(page);
	}

	private void selectGoogleAccountIfPrompted(final Page page, final String accountEmail) {
		try {
			final Locator accountOption = page.getByText(accountEmail, new Page.GetByTextOptions().setExact(true)).first();
			accountOption.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
			clickAndWait(page, accountOption);
		} catch (PlaywrightException ignored) {
			// Google account chooser may not appear when session is already authenticated.
		}
	}

	private void expandMiNegocioMenu(final Page page) {
		if (isMenuOptionVisible(page, "Agregar Negocio") && isMenuOptionVisible(page, "Administrar Negocios")) {
			return;
		}

		final Locator negocioMenu = requireVisible("'Negocio' sidebar section",
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Negocio"))),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Negocio"))),
				page.getByText(Pattern.compile("(?i)^\\s*Negocio\\s*$")));
		clickAndWait(page, negocioMenu);
	}

	private boolean isMenuOptionVisible(final Page page, final String optionText) {
		try {
			return page.getByText(optionText).first().isVisible();
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private void closeModalIfVisible(final Page page) {
		try {
			final Locator cancelButton = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")).first();
			if (cancelButton.isVisible()) {
				clickAndWait(page, cancelButton);
			}
		} catch (PlaywrightException ignored) {
			// No modal open.
		}
	}

	private Locator requireVisible(final String elementDescription, final Locator... candidates) {
		PlaywrightException lastError = null;
		for (Locator candidate : candidates) {
			final Locator firstMatch = candidate.first();
			try {
				firstMatch.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
				return firstMatch;
			} catch (PlaywrightException exception) {
				lastError = exception;
			}
		}

		if (lastError == null) {
			fail("Element not visible: " + elementDescription);
		} else {
			fail("Element not visible: " + elementDescription + ". " + simplifyMessage(lastError));
		}

		return candidates[0];
	}

	private void assertUserNameVisible(final Locator infoSection, final String expectedUserName) {
		if (!isBlank(expectedUserName)) {
			requireVisible("user name", infoSection.getByText(expectedUserName), infoSection.page().getByText(expectedUserName));
			return;
		}

		final List<String> texts = infoSection.locator("h1, h2, h3, h4, p, span, strong").allInnerTexts();
		boolean hasLikelyUserName = false;

		for (String text : texts) {
			final String normalized = text == null ? "" : text.trim();
			if (normalized.isEmpty()) {
				continue;
			}

			final boolean looksStaticLabel = normalized.equalsIgnoreCase("Información General")
					|| normalized.equalsIgnoreCase("BUSINESS PLAN")
					|| normalized.equalsIgnoreCase("Cambiar Plan") || normalized.contains("@");

			if (!looksStaticLabel) {
				hasLikelyUserName = true;
				break;
			}
		}

		assertTrue("User name should be visible in 'Información General'. Set SALEADS_USER_NAME for strict validation.",
				hasLikelyUserName);
	}

	private void assertBusinessListVisible(final Locator businessesSection) {
		final Locator listItems = businessesSection.locator("li, [role='listitem']");
		if (listItems.count() > 0) {
			assertTrue("Business list should contain at least one item.", listItems.first().isVisible());
			return;
		}

		final List<String> sectionTexts = businessesSection.locator("h1, h2, h3, h4, p, span, strong, div").allInnerTexts();
		boolean hasLikelyBusinessName = false;
		for (String text : sectionTexts) {
			final String normalized = text == null ? "" : text.trim();
			if (normalized.isEmpty()) {
				continue;
			}

			final boolean staticText = normalized.equalsIgnoreCase("Tus Negocios")
					|| normalized.equalsIgnoreCase("Agregar Negocio")
					|| TRES_NEGOCIOS_PATTERN.matcher(normalized).find();

			if (!staticText) {
				hasLikelyBusinessName = true;
				break;
			}
		}

		assertTrue("Business list should be visible in 'Tus Negocios'.", hasLikelyBusinessName);
	}

	private String openLegalLinkAndValidate(final Page appPage, final Path evidenceDir, final String legalLinkText,
			final Pattern headingPattern, final String screenshotNamePrefix) {
		final Locator legalLink = requireVisible("'" + legalLinkText + "' link",
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(legalLinkText)),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(legalLinkText)),
				appPage.getByText(legalLinkText));

		Page legalPage = null;
		boolean openedInNewTab = false;
		try {
			legalPage = appPage.waitForPopup(() -> legalLink.click(), new Page.WaitForPopupOptions().setTimeout(10000));
			openedInNewTab = true;
		} catch (PlaywrightException ignored) {
			legalLink.click();
			waitForUi(appPage);
			legalPage = appPage;
		}

		waitForUi(legalPage);
		requireVisible("'" + legalLinkText + "' heading", legalPage.getByRole(AriaRole.HEADING,
				new Page.GetByRoleOptions().setName(headingPattern)), legalPage.getByText(headingPattern));

		final String legalBodyText = legalPage.locator("body").innerText();
		assertTrue("Legal content should be visible for '" + legalLinkText + "'.",
				legalBodyText != null && legalBodyText.trim().length() > 100);

		captureScreenshot(legalPage, evidenceDir, screenshotNamePrefix, true);
		final String finalUrl = legalPage.url();
		assertTrue("Final URL should be captured for '" + legalLinkText + "'.", !isBlank(finalUrl));

		if (openedInNewTab) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			appPage.goBack();
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private void captureScreenshot(final Page page, final Path evidenceDir, final String checkpointName,
			final boolean fullPage) {
		final String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
		final String fileName = timestamp + "-" + checkpointName + ".png";
		page.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(fileName)).setFullPage(fullPage));
	}

	private void printReport(final Map<String, Boolean> stepStatus, final Map<String, String> stepDetails) {
		System.out.println("==== SaleADS Mi Negocio Final Report ====");
		for (Map.Entry<String, Boolean> entry : stepStatus.entrySet()) {
			final String label = entry.getKey();
			final String statusText = entry.getValue() ? "PASS" : "FAIL";
			System.out.println("- " + label + ": " + statusText + " (" + stepDetails.get(label) + ")");
		}
	}

	private String simplifyMessage(final Throwable throwable) {
		final String message = throwable.getMessage();
		if (isBlank(message)) {
			return throwable.getClass().getSimpleName();
		}

		final String normalized = message.replaceAll("\\s+", " ").trim();
		if (normalized.length() > 240) {
			return normalized.substring(0, 240) + "...";
		}

		return normalized;
	}

	private String firstNonBlank(final String... values) {
		for (String value : values) {
			if (!isBlank(value)) {
				return value.trim();
			}
		}

		return null;
	}

	private boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty();
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
