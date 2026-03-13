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
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullTest {

	private static final int DEFAULT_TIMEOUT_MS = 15000;
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private static final String LOGIN_FIELD = "Login";
	private static final String MENU_FIELD = "Mi Negocio menu";
	private static final String MODAL_FIELD = "Agregar Negocio modal";
	private static final String ADMIN_FIELD = "Administrar Negocios view";
	private static final String INFO_GENERAL_FIELD = "Informaci\u00f3n General";
	private static final String ACCOUNT_DETAILS_FIELD = "Detalles de la Cuenta";
	private static final String BUSINESSES_FIELD = "Tus Negocios";
	private static final String TERMS_FIELD = "T\u00e9rminos y Condiciones";
	private static final String PRIVACY_FIELD = "Pol\u00edtica de Privacidad";

	private static final List<String> REPORT_FIELDS = List.of(LOGIN_FIELD, MENU_FIELD, MODAL_FIELD, ADMIN_FIELD,
			INFO_GENERAL_FIELD, ACCOUNT_DETAILS_FIELD, BUSINESSES_FIELD, TERMS_FIELD, PRIVACY_FIELD);

	@Test
	public void saleadsMiNegocioFullTest() throws IOException {
		final StepReport report = new StepReport(REPORT_FIELDS);
		final Path artifactDir = createArtifactDir();
		final Path screenshotDir = Files.createDirectories(artifactDir.resolve("screenshots"));
		report.screenshotDir = screenshotDir.toString();

		final String loginUrl = requiredEnv("SALEADS_LOGIN_URL");
		final boolean headless = booleanEnv("SALEADS_HEADLESS", true);
		final int slowMoMs = intEnv("SALEADS_SLOW_MO_MS", 0);
		final String browserChannel = trimToNull(System.getenv("SALEADS_BROWSER_CHANNEL"));

		try (Playwright playwright = Playwright.create()) {
			final BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions().setHeadless(headless);
			if (slowMoMs > 0) {
				launchOptions.setSlowMo((double) slowMoMs);
			}
			if (browserChannel != null) {
				launchOptions.setChannel(browserChannel);
			}

			final Browser browser = playwright.chromium().launch(launchOptions);
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1200));
			final Page page = context.newPage();
			page.setDefaultTimeout(DEFAULT_TIMEOUT_MS);
			page.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			waitForUi(page);

			Page appPage = null;
			boolean menuValidated = false;
			boolean adminValidated = false;

			appPage = stepLogin(context, page, report, screenshotDir);
			if (appPage != null) {
				menuValidated = stepOpenMiNegocioMenu(appPage, report, screenshotDir);
			} else {
				report.markBlocked(MENU_FIELD, "Blocked because login failed.");
			}

			if (menuValidated) {
				stepValidateAgregarNegocioModal(appPage, report, screenshotDir);
				adminValidated = stepOpenAdministrarNegocios(appPage, report, screenshotDir);
			} else {
				report.markBlocked(MODAL_FIELD, "Blocked because Mi Negocio menu is unavailable.");
				report.markBlocked(ADMIN_FIELD, "Blocked because Mi Negocio menu is unavailable.");
			}

			if (adminValidated) {
				stepValidateInformacionGeneral(appPage, report);
				stepValidateDetallesCuenta(appPage, report);
				stepValidateTusNegocios(appPage, report);
				stepValidateLegalDocument(appPage, context, report, TERMS_FIELD,
						Pattern.compile("(?i)T[\\u00e9e]rminos\\s+y\\s+Condiciones"),
						Pattern.compile("(?i)T[\\u00e9e]rminos\\s+y\\s+Condiciones"),
						screenshotDir.resolve("08-terminos-y-condiciones.png"));
				stepValidateLegalDocument(appPage, context, report, PRIVACY_FIELD,
						Pattern.compile("(?i)Pol[\\u00edi]tica\\s+de\\s+Privacidad"),
						Pattern.compile("(?i)Pol[\\u00edi]tica\\s+de\\s+Privacidad"),
						screenshotDir.resolve("09-politica-de-privacidad.png"));
			} else {
				report.markBlocked(INFO_GENERAL_FIELD, "Blocked because Administrar Negocios did not load.");
				report.markBlocked(ACCOUNT_DETAILS_FIELD, "Blocked because Administrar Negocios did not load.");
				report.markBlocked(BUSINESSES_FIELD, "Blocked because Administrar Negocios did not load.");
				report.markBlocked(TERMS_FIELD, "Blocked because Administrar Negocios did not load.");
				report.markBlocked(PRIVACY_FIELD, "Blocked because Administrar Negocios did not load.");
			}

			browser.close();
		} catch (RuntimeException ex) {
			report.globalError = ex.getMessage();
			throw ex;
		} finally {
			writeReport(artifactDir.resolve("saleads_mi_negocio_full_test-report.json"), report);
		}

		Assert.assertTrue(report.failureSummary(), report.allPassed());
	}

	private Page stepLogin(final BrowserContext context, final Page page, final StepReport report, final Path screenshotDir) {
		try {
			final Locator loginButton = firstVisible(
					page.getByRole(AriaRole.BUTTON,
							new Page.GetByRoleOptions().setName(
									Pattern.compile("(?i)(sign in with google|iniciar sesi[o\\u00f3]n con google|continuar con google|google)"))),
					page.getByRole(AriaRole.LINK,
							new Page.GetByRoleOptions().setName(
									Pattern.compile("(?i)(sign in with google|iniciar sesi[o\\u00f3]n con google|continuar con google|google)"))),
					page.getByText(Pattern.compile("(?i)(sign in with google|iniciar sesi[o\\u00f3]n con google|continuar con google)")));

			clickAndWait(loginButton, page);
			selectGoogleAccountIfVisible(context);

			final Page appPage = waitForApplicationPage(context);
			final boolean sidebarVisible = isTextVisible(appPage, Pattern.compile("(?i)\\b(Negocio|Mi\\s+Negocio)\\b"));
			final boolean mainVisible = isVisible(appPage.locator("main, [role='main'], aside").first(), 5000);

			if (!sidebarVisible || !mainVisible) {
				throw new IllegalStateException("Main app interface or left sidebar not detected after login.");
			}

			takeScreenshot(appPage, screenshotDir.resolve("01-dashboard-loaded.png"), true);
			report.markPass(LOGIN_FIELD, "Dashboard loaded and left sidebar is visible.");
			return appPage;
		} catch (Exception ex) {
			report.markFail(LOGIN_FIELD, ex.getMessage());
			return null;
		}
	}

	private boolean stepOpenMiNegocioMenu(final Page appPage, final StepReport report, final Path screenshotDir) {
		try {
			ensureMiNegocioExpanded(appPage);
			final boolean agregarVisible = isTextVisible(appPage, Pattern.compile("(?i)^\\s*Agregar\\s+Negocio\\s*$"));
			final boolean administrarVisible = isTextVisible(appPage, Pattern.compile("(?i)^\\s*Administrar\\s+Negocios\\s*$"));

			if (!agregarVisible || !administrarVisible) {
				throw new IllegalStateException("Mi Negocio submenu did not expose both expected options.");
			}

			takeScreenshot(appPage, screenshotDir.resolve("02-mi-negocio-menu-expanded.png"), false);
			report.markPass(MENU_FIELD, "Submenu expanded with Agregar Negocio and Administrar Negocios.");
			return true;
		} catch (Exception ex) {
			report.markFail(MENU_FIELD, ex.getMessage());
			return false;
		}
	}

	private void stepValidateAgregarNegocioModal(final Page appPage, final StepReport report, final Path screenshotDir) {
		try {
			final Locator agregarNegocio = firstVisible(
					appPage.getByRole(AriaRole.LINK,
							new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Agregar\\s+Negocio\\s*$"))),
					appPage.getByRole(AriaRole.BUTTON,
							new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Agregar\\s+Negocio\\s*$"))),
					appPage.getByText(Pattern.compile("(?i)^\\s*Agregar\\s+Negocio\\s*$")));

			clickAndWait(agregarNegocio, appPage);

			requireTextVisible(appPage, Pattern.compile("(?i)^\\s*Crear\\s+Nuevo\\s+Negocio\\s*$"),
					"Modal title 'Crear Nuevo Negocio' is not visible.");
			final Locator nombreNegocioInput = firstVisible(
					appPage.getByLabel(Pattern.compile("(?i)Nombre\\s+del\\s+Negocio")),
					appPage.getByPlaceholder(Pattern.compile("(?i)Nombre\\s+del\\s+Negocio")));
			if (!isVisible(nombreNegocioInput, 4000)) {
				throw new IllegalStateException("Input field 'Nombre del Negocio' is not visible.");
			}
			requireTextVisible(appPage, Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios"),
					"Text 'Tienes 2 de 3 negocios' is not visible.");
			requireVisible(
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Cancelar\\s*$"))),
					"Button 'Cancelar' is not visible.");
			requireVisible(
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Crear\\s+Negocio\\s*$"))),
					"Button 'Crear Negocio' is not visible.");

			takeScreenshot(appPage, screenshotDir.resolve("03-agregar-negocio-modal.png"), false);
			nombreNegocioInput.click();
			nombreNegocioInput.fill("Negocio Prueba Automatizacion");
			waitForUi(appPage);
			clickAndWait(
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Cancelar\\s*$")))
							.first(),
					appPage);

			report.markPass(MODAL_FIELD,
					"Modal validated: title, input, business count text and action buttons are present.");
		} catch (Exception ex) {
			report.markFail(MODAL_FIELD, ex.getMessage());
		}
	}

	private boolean stepOpenAdministrarNegocios(final Page appPage, final StepReport report, final Path screenshotDir) {
		try {
			ensureMiNegocioExpanded(appPage);
			final Locator administrarNegocios = firstVisible(
					appPage.getByRole(AriaRole.LINK,
							new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Administrar\\s+Negocios\\s*$"))),
					appPage.getByRole(AriaRole.BUTTON,
							new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Administrar\\s+Negocios\\s*$"))),
					appPage.getByText(Pattern.compile("(?i)^\\s*Administrar\\s+Negocios\\s*$")));
			clickAndWait(administrarNegocios, appPage);

			requireTextVisible(appPage, Pattern.compile("(?i)Informaci[o\\u00f3]n\\s+General"),
					"Section 'Informacion General' is not visible.");
			requireTextVisible(appPage, Pattern.compile("(?i)Detalles\\s+de\\s+la\\s+Cuenta"),
					"Section 'Detalles de la Cuenta' is not visible.");
			requireTextVisible(appPage, Pattern.compile("(?i)Tus\\s+Negocios"),
					"Section 'Tus Negocios' is not visible.");
			requireTextVisible(appPage, Pattern.compile("(?i)Secci[o\\u00f3]n\\s+Legal"),
					"Section 'Seccion Legal' is not visible.");

			takeScreenshot(appPage, screenshotDir.resolve("04-administrar-negocios-page.png"), true);
			report.markPass(ADMIN_FIELD, "Administrar Negocios loaded with all expected account sections.");
			return true;
		} catch (Exception ex) {
			report.markFail(ADMIN_FIELD, ex.getMessage());
			return false;
		}
	}

	private void stepValidateInformacionGeneral(final Page appPage, final StepReport report) {
		try {
			final String sectionText = extractTextAroundHeading(appPage, Pattern.compile("(?i)Informaci[o\\u00f3]n\\s+General"));
			final boolean hasEmail = EMAIL_PATTERN.matcher(sectionText).find();
			final boolean hasLikelyName = containsLikelyName(sectionText);
			final boolean hasBusinessPlan = isTextVisible(appPage, Pattern.compile("(?i)BUSINESS\\s+PLAN"));
			final boolean hasCambiarPlan = isVisible(
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)Cambiar\\s+Plan")))
							.first(),
					5000);

			if (!hasLikelyName || !hasEmail || !hasBusinessPlan || !hasCambiarPlan) {
				throw new IllegalStateException(
						"Informacion General validation failed. Required: user name, user email, BUSINESS PLAN, Cambiar Plan.");
			}

			report.markPass(INFO_GENERAL_FIELD,
					"Informacion General contains user identity, email, plan label and Cambiar Plan action.");
		} catch (Exception ex) {
			report.markFail(INFO_GENERAL_FIELD, ex.getMessage());
		}
	}

	private void stepValidateDetallesCuenta(final Page appPage, final StepReport report) {
		try {
			requireTextVisible(appPage, Pattern.compile("(?i)Cuenta\\s+creada"), "'Cuenta creada' is not visible.");
			requireTextVisible(appPage, Pattern.compile("(?i)Estado\\s+activo"), "'Estado activo' is not visible.");
			requireTextVisible(appPage, Pattern.compile("(?i)Idioma\\s+seleccionado"),
					"'Idioma seleccionado' is not visible.");
			report.markPass(ACCOUNT_DETAILS_FIELD,
					"Detalles de la Cuenta has Cuenta creada, Estado activo and Idioma seleccionado.");
		} catch (Exception ex) {
			report.markFail(ACCOUNT_DETAILS_FIELD, ex.getMessage());
		}
	}

	private void stepValidateTusNegocios(final Page appPage, final StepReport report) {
		try {
			requireTextVisible(appPage, Pattern.compile("(?i)Tus\\s+Negocios"), "'Tus Negocios' section is not visible.");
			final String sectionText = extractTextAroundHeading(appPage, Pattern.compile("(?i)Tus\\s+Negocios"));
			final boolean listVisible = sectionText.lines().map(String::trim).filter(line -> !line.isEmpty()).count() >= 4;
			final boolean addButtonVisible = isVisible(
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Agregar\\s+Negocio\\s*$")))
							.first(),
					5000)
					|| isVisible(appPage.getByRole(AriaRole.LINK,
							new Page.GetByRoleOptions().setName(Pattern.compile("(?i)^\\s*Agregar\\s+Negocio\\s*$"))).first(), 5000);
			final boolean hasCapacityText = sectionText.matches("(?is).*Tienes\\s+2\\s+de\\s+3\\s+negocios.*");

			if (!listVisible || !addButtonVisible || !hasCapacityText) {
				throw new IllegalStateException(
						"Tus Negocios validation failed. Required: list visible, Agregar Negocio button, and 'Tienes 2 de 3 negocios'.");
			}

			report.markPass(BUSINESSES_FIELD, "Tus Negocios list, add action, and capacity text are visible.");
		} catch (Exception ex) {
			report.markFail(BUSINESSES_FIELD, ex.getMessage());
		}
	}

	private void stepValidateLegalDocument(final Page appPage, final BrowserContext context, final StepReport report,
			final String reportField, final Pattern triggerPattern, final Pattern headingPattern, final Path screenshotPath) {
		try {
			final Locator legalLink = firstVisible(
					appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(triggerPattern)),
					appPage.getByText(triggerPattern));
			final String appUrlBefore = appPage.url();

			Page legalPage;
			boolean openedNewTab = false;
			try {
				legalPage = context.waitForPage(() -> legalLink.click(),
						new BrowserContext.WaitForPageOptions().setTimeout(7000));
				openedNewTab = true;
				waitForUi(legalPage);
			} catch (PlaywrightException e) {
				legalPage = appPage;
				waitForUi(legalPage);
			}

			final boolean headingVisible = isVisible(
					legalPage.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(headingPattern)).first(), 6000)
					|| isTextVisible(legalPage, headingPattern);
			final String bodyText = safeInnerText(legalPage.locator("body").first());
			final boolean legalContentVisible = bodyText.trim().length() > 120;
			takeScreenshot(legalPage, screenshotPath, true);
			final String finalUrl = legalPage.url();

			if (TERMS_FIELD.equals(reportField)) {
				report.termsUrl = finalUrl;
			}
			if (PRIVACY_FIELD.equals(reportField)) {
				report.privacyUrl = finalUrl;
			}

			if (!headingVisible || !legalContentVisible) {
				throw new IllegalStateException("Legal page validation failed for field '" + reportField + "'.");
			}

			if (openedNewTab) {
				legalPage.close();
				appPage.bringToFront();
				waitForUi(appPage);
			} else {
				try {
					appPage.goBack(new Page.GoBackOptions().setTimeout(DEFAULT_TIMEOUT_MS));
					waitForUi(appPage);
				} catch (PlaywrightException backError) {
					appPage.navigate(appUrlBefore, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
					waitForUi(appPage);
				}
			}

			report.markPass(reportField, "Validated legal heading and content. Final URL: " + finalUrl);
		} catch (Exception ex) {
			report.markFail(reportField, ex.getMessage());
		}
	}

	private void ensureMiNegocioExpanded(final Page appPage) {
		if (isTextVisible(appPage, Pattern.compile("(?i)^\\s*Agregar\\s+Negocio\\s*$"))
				&& isTextVisible(appPage, Pattern.compile("(?i)^\\s*Administrar\\s+Negocios\\s*$"))) {
			return;
		}

		clickIfVisible(appPage, Pattern.compile("(?i)^\\s*Negocio\\s*$"));
		clickIfVisible(appPage, Pattern.compile("(?i)^\\s*Mi\\s+Negocio\\s*$"));
		waitForUi(appPage);
	}

	private Page waitForApplicationPage(final BrowserContext context) {
		for (int i = 0; i < 60; i++) {
			for (Page candidate : context.pages()) {
				if (isTextVisible(candidate, Pattern.compile("(?i)\\b(Negocio|Mi\\s+Negocio)\\b"))) {
					candidate.bringToFront();
					waitForUi(candidate);
					return candidate;
				}
			}
			context.pages().get(0).waitForTimeout(500);
		}
		throw new IllegalStateException("Unable to detect the SaleADS application page after login.");
	}

	private void selectGoogleAccountIfVisible(final BrowserContext context) {
		for (int i = 0; i < 30; i++) {
			for (Page candidate : context.pages()) {
				final Locator accountEntry = candidate.getByText(Pattern.compile(Pattern.quote(GOOGLE_ACCOUNT_EMAIL), Pattern.CASE_INSENSITIVE))
						.first();
				if (isVisible(accountEntry, 800)) {
					accountEntry.click();
					waitForUi(candidate);
					return;
				}
			}
			context.pages().get(0).waitForTimeout(500);
		}
	}

	private static Path createArtifactDir() throws IOException {
		final String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-");
		return Files.createDirectories(Path.of("target", "e2e-artifacts", "saleads-mi-negocio-full-test", timestamp));
	}

	private static void writeReport(final Path reportPath, final StepReport report) throws IOException {
		Files.createDirectories(reportPath.getParent());
		Files.writeString(reportPath, report.toJson(), StandardCharsets.UTF_8);
	}

	private static void takeScreenshot(final Page page, final Path targetPath, final boolean fullPage) {
		try {
			Files.createDirectories(targetPath.getParent());
		} catch (IOException ioException) {
			throw new IllegalStateException("Unable to create screenshot directory: " + targetPath.getParent(), ioException);
		}
		page.screenshot(new Page.ScreenshotOptions().setPath(targetPath).setFullPage(fullPage));
	}

	private static void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10000));
		} catch (PlaywrightException ignored) {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(10000));
		}
		page.waitForTimeout(600);
	}

	private static void clickAndWait(final Locator locator, final Page page) {
		locator.first().click();
		waitForUi(page);
	}

	private static Locator firstVisible(final Locator... candidates) {
		for (Locator candidate : candidates) {
			final Locator first = candidate.first();
			if (isVisible(first, 2500)) {
				return first;
			}
		}
		throw new IllegalStateException("Could not find any matching visible element.");
	}

	private static boolean clickIfVisible(final Page page, final Pattern textPattern) {
		try {
			final Locator clickable = firstVisible(
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(textPattern)),
					page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(textPattern)),
					page.getByText(textPattern));
			clickAndWait(clickable, page);
			return true;
		} catch (Exception ignored) {
			return false;
		}
	}

	private static void requireVisible(final Locator locator, final String errorMessage) {
		if (!isVisible(locator.first(), 5000)) {
			throw new IllegalStateException(errorMessage);
		}
	}

	private static void requireTextVisible(final Page page, final Pattern textPattern, final String errorMessage) {
		if (!isTextVisible(page, textPattern)) {
			throw new IllegalStateException(errorMessage);
		}
	}

	private static boolean isTextVisible(final Page page, final Pattern textPattern) {
		return isVisible(page.getByText(textPattern).first(), 3000)
				|| isVisible(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(textPattern)).first(), 3000)
				|| isVisible(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(textPattern)).first(), 3000)
				|| isVisible(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(textPattern)).first(), 3000);
	}

	private static boolean isVisible(final Locator locator, final int timeoutMs) {
		try {
			return locator.isVisible(new Locator.IsVisibleOptions().setTimeout(timeoutMs));
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private static String extractTextAroundHeading(final Page page, final Pattern headingPattern) {
		final Locator heading = page.getByText(headingPattern).first();
		if (!isVisible(heading, 4000)) {
			return "";
		}
		try {
			final Object evaluated = heading.evaluate(
					"element => { let current = element; while (current) { const text = (current.innerText || '').trim(); if (text.split('\\n').length >= 4) return text; current = current.parentElement; } return (element.innerText || ''); }");
			return evaluated == null ? "" : evaluated.toString();
		} catch (PlaywrightException ex) {
			return safeInnerText(heading);
		}
	}

	private static String safeInnerText(final Locator locator) {
		try {
			return locator.innerText();
		} catch (PlaywrightException ex) {
			return "";
		}
	}

	private static boolean containsLikelyName(final String sectionText) {
		for (String rawLine : sectionText.split("\\R")) {
			final String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}
			final String lower = line.toLowerCase(Locale.ROOT);
			if (lower.contains("@") || lower.contains("informaci") || lower.contains("business plan")
					|| lower.contains("cambiar plan")) {
				continue;
			}

			final String lettersOnly = line.replaceAll("[^A-Za-z\\s]", " ").trim();
			if (lettersOnly.split("\\s+").length >= 2) {
				return true;
			}
		}
		return false;
	}

	private static String requiredEnv(final String key) {
		final String value = trimToNull(System.getenv(key));
		if (value == null) {
			throw new IllegalStateException("Environment variable '" + key
					+ "' is required. Provide the login page URL for the target SaleADS environment.");
		}
		return value;
	}

	private static boolean booleanEnv(final String key, final boolean defaultValue) {
		final String value = trimToNull(System.getenv(key));
		if (value == null) {
			return defaultValue;
		}
		return Boolean.parseBoolean(value);
	}

	private static int intEnv(final String key, final int defaultValue) {
		final String value = trimToNull(System.getenv(key));
		if (value == null) {
			return defaultValue;
		}
		return Integer.parseInt(value);
	}

	private static String trimToNull(final String value) {
		if (value == null) {
			return null;
		}
		final String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static class StepReport {

		private final Map<String, String> statusByField = new LinkedHashMap<>();
		private final Map<String, String> detailsByField = new LinkedHashMap<>();

		private String screenshotDir;
		private String termsUrl;
		private String privacyUrl;
		private String globalError;

		private StepReport(final List<String> fields) {
			for (String field : fields) {
				statusByField.put(field, "FAIL");
				detailsByField.put(field, "Not executed.");
			}
		}

		private void markPass(final String field, final String details) {
			statusByField.put(field, "PASS");
			detailsByField.put(field, details);
		}

		private void markFail(final String field, final String details) {
			statusByField.put(field, "FAIL");
			detailsByField.put(field, details == null ? "Validation failed." : details);
		}

		private void markBlocked(final String field, final String details) {
			if (!"PASS".equals(statusByField.get(field))) {
				markFail(field, details);
			}
		}

		private boolean allPassed() {
			return statusByField.values().stream().allMatch("PASS"::equals);
		}

		private String failureSummary() {
			final StringBuilder summary = new StringBuilder("SaleADS Mi Negocio workflow failures:");
			for (Map.Entry<String, String> entry : statusByField.entrySet()) {
				if ("FAIL".equals(entry.getValue())) {
					summary.append(System.lineSeparator()).append("- ").append(entry.getKey()).append(": ")
							.append(detailsByField.get(entry.getKey()));
				}
			}
			return summary.toString();
		}

		private String toJson() {
			final StringBuilder sb = new StringBuilder();
			sb.append("{\n");
			sb.append("  \"name\": \"saleads_mi_negocio_full_test\",\n");
			sb.append("  \"generatedAt\": \"").append(escapeJson(Instant.now().toString())).append("\",\n");
			sb.append("  \"results\": [\n");
			int index = 0;
			for (Map.Entry<String, String> entry : statusByField.entrySet()) {
				final String field = entry.getKey();
				sb.append("    {\n");
				sb.append("      \"field\": \"").append(escapeJson(field)).append("\",\n");
				sb.append("      \"status\": \"").append(escapeJson(entry.getValue())).append("\",\n");
				sb.append("      \"details\": \"").append(escapeJson(detailsByField.get(field))).append("\"\n");
				sb.append("    }");
				if (index < statusByField.size() - 1) {
					sb.append(",");
				}
				sb.append("\n");
				index++;
			}
			sb.append("  ],\n");
			sb.append("  \"evidence\": {\n");
			sb.append("    \"screenshotDir\": \"").append(escapeJson(nullToEmpty(screenshotDir))).append("\",\n");
			sb.append("    \"terminosUrl\": \"").append(escapeJson(nullToEmpty(termsUrl))).append("\",\n");
			sb.append("    \"politicaPrivacidadUrl\": \"").append(escapeJson(nullToEmpty(privacyUrl))).append("\"\n");
			sb.append("  }");
			if (globalError != null) {
				sb.append(",\n");
				sb.append("  \"globalError\": \"").append(escapeJson(globalError)).append("\"\n");
			} else {
				sb.append("\n");
			}
			sb.append("}\n");
			return sb.toString();
		}

		private static String nullToEmpty(final String value) {
			return value == null ? "" : value;
		}

		private static String escapeJson(final String value) {
			if (value == null) {
				return "";
			}
			return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
		}
	}
}
