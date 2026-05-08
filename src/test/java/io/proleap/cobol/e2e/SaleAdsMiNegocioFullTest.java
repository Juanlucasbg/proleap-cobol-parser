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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SaleAdsMiNegocioFullTest {

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMIN_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFO_GENERAL = "Informaci\u00F3n General";
	private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "T\u00E9rminos y Condiciones";
	private static final String REPORT_PRIVACIDAD = "Pol\u00EDtica de Privacidad";
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private static final Pattern TEXT_NEGOCIO = Pattern.compile("(?i)\\bNegocio\\b");
	private static final Pattern TEXT_MI_NEGOCIO = Pattern.compile("(?i)Mi\\s+Negocio");
	private static final Pattern TEXT_AGREGAR_NEGOCIO = Pattern.compile("(?i)Agregar\\s+Negocio");
	private static final Pattern TEXT_ADMIN_NEGOCIOS = Pattern.compile("(?i)Administrar\\s+Negocios");
	private static final Pattern TEXT_CREAR_NEGOCIO = Pattern.compile("(?i)Crear\\s+Nuevo\\s+Negocio");
	private static final Pattern TEXT_NOMBRE_NEGOCIO = Pattern.compile("(?i)Nombre\\s+del\\s+Negocio");
	private static final Pattern TEXT_LIMITE_NEGOCIOS = Pattern.compile("(?i)Tienes\\s+2\\s+de\\s+3\\s+negocios");
	private static final Pattern TEXT_CANCELAR = Pattern.compile("(?i)Cancelar");
	private static final Pattern TEXT_CREAR_BUTTON = Pattern.compile("(?i)Crear\\s+Negocio");

	private static final Pattern TEXT_INFO_GENERAL = Pattern.compile("(?i)Informaci[o\\u00F3]n\\s+General");
	private static final Pattern TEXT_DETALLES_CUENTA = Pattern.compile("(?i)Detalles\\s+de\\s+la\\s+Cuenta");
	private static final Pattern TEXT_TUS_NEGOCIOS = Pattern.compile("(?i)Tus\\s+Negocios");
	private static final Pattern TEXT_SECCION_LEGAL = Pattern.compile("(?i)Secci[o\\u00F3]n\\s+Legal");

	private static final Pattern TEXT_BUSINESS_PLAN = Pattern.compile("(?i)BUSINESS\\s+PLAN");
	private static final Pattern TEXT_CAMBIAR_PLAN = Pattern.compile("(?i)Cambiar\\s+Plan");
	private static final Pattern TEXT_CUENTA_CREADA = Pattern.compile("(?i)Cuenta\\s+creada");
	private static final Pattern TEXT_ESTADO_ACTIVO = Pattern.compile("(?i)Estado\\s+activo");
	private static final Pattern TEXT_IDIOMA_SELECCIONADO = Pattern.compile("(?i)Idioma\\s+seleccionado");
	private static final Pattern TEXT_TERMINOS = Pattern.compile("(?i)T[e\\u00E9]rminos\\s+y\\s+Condiciones");
	private static final Pattern TEXT_PRIVACIDAD = Pattern.compile("(?i)Pol[i\\u00ED]tica\\s+de\\s+Privacidad");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final Path reportDirectory = Paths.get("target", "saleads-mi-negocio");
		Files.createDirectories(reportDirectory);

		final Map<String, String> report = new LinkedHashMap<>();
		report.put(REPORT_LOGIN, "FAIL_NOT_RUN");
		report.put(REPORT_MI_NEGOCIO_MENU, "FAIL_NOT_RUN");
		report.put(REPORT_AGREGAR_MODAL, "FAIL_NOT_RUN");
		report.put(REPORT_ADMIN_VIEW, "FAIL_NOT_RUN");
		report.put(REPORT_INFO_GENERAL, "FAIL_NOT_RUN");
		report.put(REPORT_DETALLES_CUENTA, "FAIL_NOT_RUN");
		report.put(REPORT_TUS_NEGOCIOS, "FAIL_NOT_RUN");
		report.put(REPORT_TERMINOS, "FAIL_NOT_RUN");
		report.put(REPORT_PRIVACIDAD, "FAIL_NOT_RUN");

		final Map<String, String> legalUrls = new LinkedHashMap<>();
		final String expectedUserNameToken = readEnvOrDefault("SALEADS_EXPECTED_USER_NAME", "Juan");
		final String loginUrl = resolveLoginUrl();

		try (Playwright playwright = Playwright.create()) {
			Browser browser = playwright.chromium()
					.launch(new BrowserTypeOptions().launchOptions());
			try (browser) {
				BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
				context.setDefaultTimeout(20_000);
				Page page = context.newPage();

				page.navigate(loginUrl);
				page.waitForLoadState(LoadState.DOMCONTENTLOADED);

				runStep(report, REPORT_LOGIN, () -> {
					performLoginWithGoogle(page, context);
					waitForApplicationShell(page);
					takeScreenshot(page, reportDirectory.resolve("01_dashboard_loaded.png"), false);
				});

				runStep(report, REPORT_MI_NEGOCIO_MENU, () -> {
					expandMiNegocioMenu(page);
					waitForVisibleText(page, TEXT_AGREGAR_NEGOCIO, 10_000);
					waitForVisibleText(page, TEXT_ADMIN_NEGOCIOS, 10_000);
					takeScreenshot(page, reportDirectory.resolve("02_mi_negocio_expanded.png"), false);
				});

				runStep(report, REPORT_AGREGAR_MODAL, () -> {
					clickByVisibleText(page, TEXT_AGREGAR_NEGOCIO);
					waitForVisibleText(page, TEXT_CREAR_NEGOCIO, 12_000);
					Locator input = resolveBusinessNameInput(page);
					waitForVisibleText(page, TEXT_LIMITE_NEGOCIOS, 12_000);
					waitForButton(page, TEXT_CANCELAR, 8_000);
					waitForButton(page, TEXT_CREAR_BUTTON, 8_000);
					takeScreenshot(page, reportDirectory.resolve("03_agregar_negocio_modal.png"), false);
					input.fill("Negocio Prueba Automatizacion");
					clickByButtonName(page, TEXT_CANCELAR);
					page.waitForLoadState(LoadState.DOMCONTENTLOADED);
					page.waitForTimeout(700);
				});

				runStep(report, REPORT_ADMIN_VIEW, () -> {
					expandMiNegocioMenu(page);
					clickByVisibleText(page, TEXT_ADMIN_NEGOCIOS);
					waitForVisibleText(page, TEXT_INFO_GENERAL, 15_000);
					waitForVisibleText(page, TEXT_DETALLES_CUENTA, 15_000);
					waitForVisibleText(page, TEXT_TUS_NEGOCIOS, 15_000);
					waitForVisibleText(page, TEXT_SECCION_LEGAL, 15_000);
					takeScreenshot(page, reportDirectory.resolve("04_administrar_negocios_full.png"), true);
				});

				runStep(report, REPORT_INFO_GENERAL, () -> {
					waitForVisibleText(page, Pattern.compile("(?i)" + Pattern.quote(expectedUserNameToken)), 10_000);
					waitForVisibleText(page, EMAIL_PATTERN, 10_000);
					waitForVisibleText(page, TEXT_BUSINESS_PLAN, 10_000);
					waitForVisibleText(page, TEXT_CAMBIAR_PLAN, 10_000);
				});

				runStep(report, REPORT_DETALLES_CUENTA, () -> {
					waitForVisibleText(page, TEXT_CUENTA_CREADA, 10_000);
					waitForVisibleText(page, TEXT_ESTADO_ACTIVO, 10_000);
					waitForVisibleText(page, TEXT_IDIOMA_SELECCIONADO, 10_000);
				});

				runStep(report, REPORT_TUS_NEGOCIOS, () -> {
					waitForVisibleText(page, TEXT_TUS_NEGOCIOS, 10_000);
					waitForVisibleText(page, TEXT_AGREGAR_NEGOCIO, 10_000);
					waitForVisibleText(page, TEXT_LIMITE_NEGOCIOS, 10_000);
					assertBusinessListVisible(page);
				});

				runStep(report, REPORT_TERMINOS, () -> {
					String finalUrl = openLegalLinkAndValidate(page, context, TEXT_TERMINOS,
							reportDirectory.resolve("05_terminos_y_condiciones.png"));
					legalUrls.put("terminos_y_condiciones_url", finalUrl);
				});

				runStep(report, REPORT_PRIVACIDAD, () -> {
					String finalUrl = openLegalLinkAndValidate(page, context, TEXT_PRIVACIDAD,
							reportDirectory.resolve("06_politica_de_privacidad.png"));
					legalUrls.put("politica_de_privacidad_url", finalUrl);
				});
			}
		}

		writeFinalReport(reportDirectory.resolve("final_report.json"), report, legalUrls);
		printFinalReport(report, legalUrls);

		List<String> failedSteps = report.entrySet().stream()
				.filter(entry -> !entry.getValue().startsWith("PASS"))
				.map(Map.Entry::getKey)
				.collect(Collectors.toList());

		if (!failedSteps.isEmpty()) {
			Assert.fail("One or more SaleADS validations failed: " + failedSteps);
		}
	}

	private void runStep(Map<String, String> report, String key, CheckedRunnable action) {
		try {
			action.run();
			report.put(key, "PASS");
		} catch (Throwable ex) {
			report.put(key, "FAIL: " + compactError(ex));
		}
	}

	private void performLoginWithGoogle(Page page, BrowserContext context) {
		Locator googleButton = page.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(Pattern.compile("(?i)(sign\\s*in\\s*with\\s*google|google|iniciar\\s*sesi[o\\u00F3]n\\s*con\\s*google)")))
				.first();

		if (!isVisible(googleButton, 10_000)) {
			googleButton = page.getByText(Pattern.compile("(?i)(sign\\s*in\\s*with\\s*google|google|iniciar\\s*sesi[o\\u00F3]n\\s*con\\s*google)"))
					.first();
		}

		int beforePages = context.pages().size();
		clickAndWait(page, googleButton);

		Page authPage = page;
		if (context.pages().size() > beforePages) {
			authPage = context.pages().get(context.pages().size() - 1);
			authPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		}

		selectGoogleAccountIfPresent(authPage);
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		page.waitForTimeout(1_000);
	}

	private void selectGoogleAccountIfPresent(Page authPage) {
		Pattern accountPattern = Pattern.compile("(?i)" + Pattern.quote(GOOGLE_ACCOUNT_EMAIL));
		Locator accountOption = authPage.getByText(accountPattern).first();
		if (isVisible(accountOption, 8_000)) {
			clickAndWait(authPage, accountOption);
			return;
		}

		Locator accountButton = authPage.getByRole(AriaRole.BUTTON,
				new Page.GetByRoleOptions().setName(accountPattern)).first();
		if (isVisible(accountButton, 4_000)) {
			clickAndWait(authPage, accountButton);
		}
	}

	private void waitForApplicationShell(Page page) {
		waitForVisibleText(page, TEXT_NEGOCIO, 25_000);
		Locator sidebar = page.locator("aside, nav").first();
		if (!isVisible(sidebar, 7_000)) {
			throw new AssertionError("Left sidebar navigation is not visible after login.");
		}
	}

	private void expandMiNegocioMenu(Page page) {
		waitForApplicationShell(page);
		if (!isVisible(page.getByText(TEXT_MI_NEGOCIO).first(), 5_000)) {
			clickByVisibleText(page, TEXT_NEGOCIO);
		}
		clickByVisibleText(page, TEXT_MI_NEGOCIO);
		page.waitForTimeout(700);
	}

	private Locator resolveBusinessNameInput(Page page) {
		Locator byLabel = page.getByLabel(TEXT_NOMBRE_NEGOCIO).first();
		if (isVisible(byLabel, 3_000)) {
			return byLabel;
		}

		Locator byPlaceholder = page.getByPlaceholder(TEXT_NOMBRE_NEGOCIO).first();
		if (isVisible(byPlaceholder, 3_000)) {
			return byPlaceholder;
		}

		Locator byName = page.locator("input[name*='negocio'], input[id*='negocio']").first();
		if (isVisible(byName, 3_000)) {
			return byName;
		}

		throw new AssertionError("Input field 'Nombre del Negocio' was not found.");
	}

	private void assertBusinessListVisible(Page page) {
		Locator section = page.locator("section, div")
				.filter(new Locator.FilterOptions().setHasText(TEXT_TUS_NEGOCIOS))
				.first();
		if (!isVisible(section, 8_000)) {
			throw new AssertionError("Business list section is not visible.");
		}

		Locator businessRows = section.locator("li, tr, [data-testid*='business'], [class*='business']");
		if (businessRows.count() < 1) {
			throw new AssertionError("Business list appears empty or not rendered.");
		}
	}

	private String openLegalLinkAndValidate(Page appPage, BrowserContext context, Pattern linkText, Path screenshotPath) {
		waitForVisibleText(appPage, TEXT_SECCION_LEGAL, 12_000);
		int beforePages = context.pages().size();
		String appUrlBeforeNavigation = appPage.url();

		clickByVisibleText(appPage, linkText);

		Page legalPage = appPage;
		boolean openedNewTab = context.pages().size() > beforePages;
		if (openedNewTab) {
			legalPage = context.pages().get(context.pages().size() - 1);
		}

		legalPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		waitForVisibleText(legalPage, linkText, 20_000);
		assertLegalContentVisible(legalPage);
		takeScreenshot(legalPage, screenshotPath, true);
		String finalUrl = legalPage.url();

		if (openedNewTab) {
			legalPage.close();
			appPage.bringToFront();
			appPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} else {
			try {
				appPage.goBack(new Page.GoBackOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
			} catch (PlaywrightException ex) {
				appPage.navigate(appUrlBeforeNavigation);
			}
			appPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
			waitForVisibleText(appPage, TEXT_SECCION_LEGAL, 15_000);
		}

		return finalUrl;
	}

	private void assertLegalContentVisible(Page page) {
		String bodyText = page.locator("body").innerText();
		if (bodyText == null || bodyText.trim().length() < 120) {
			throw new AssertionError("Legal page content text is too short.");
		}
	}

	private void clickByVisibleText(Page page, Pattern pattern) {
		Locator locator = page.getByText(pattern).first();
		clickAndWait(page, locator);
	}

	private void clickByButtonName(Page page, Pattern pattern) {
		Locator locator = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(pattern)).first();
		clickAndWait(page, locator);
	}

	private void waitForButton(Page page, Pattern buttonText, double timeoutMs) {
		Locator button = page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(buttonText)).first();
		waitForVisible(button, timeoutMs, "Button not visible: " + buttonText.pattern());
	}

	private void waitForVisibleText(Page page, Pattern text, double timeoutMs) {
		Locator locator = page.getByText(text).first();
		waitForVisible(locator, timeoutMs, "Text not visible: " + text.pattern());
	}

	private void waitForVisible(Locator locator, double timeoutMs, String errorMessage) {
		try {
			locator.waitFor(new Locator.WaitForOptions()
					.setState(WaitForSelectorState.VISIBLE)
					.setTimeout(timeoutMs));
		} catch (PlaywrightException ex) {
			throw new AssertionError(errorMessage, ex);
		}
	}

	private boolean isVisible(Locator locator, double timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions()
					.setState(WaitForSelectorState.VISIBLE)
					.setTimeout(timeoutMs));
			return true;
		} catch (PlaywrightException ex) {
			return false;
		}
	}

	private void clickAndWait(Page page, Locator locator) {
		waitForVisible(locator, 10_000, "Element was not clickable because it is not visible.");
		locator.click();
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		page.waitForTimeout(700);
	}

	private void takeScreenshot(Page page, Path path, boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions()
				.setPath(path)
				.setFullPage(fullPage));
	}

	private void writeFinalReport(Path reportPath, Map<String, String> report, Map<String, String> legalUrls) throws IOException {
		StringBuilder json = new StringBuilder();
		json.append("{\n");
		json.append("  \"test_name\": \"saleads_mi_negocio_full_test\",\n");
		json.append("  \"timestamp\": \"").append(Instant.now()).append("\",\n");
		json.append("  \"results\": {\n");

		int index = 0;
		for (Map.Entry<String, String> entry : report.entrySet()) {
			json.append("    \"").append(jsonEscape(entry.getKey())).append("\": \"")
					.append(jsonEscape(entry.getValue())).append("\"");
			if (index < report.size() - 1) {
				json.append(",");
			}
			json.append("\n");
			index++;
		}

		json.append("  },\n");
		json.append("  \"legal_urls\": {\n");
		int legalIndex = 0;
		for (Map.Entry<String, String> entry : legalUrls.entrySet()) {
			json.append("    \"").append(jsonEscape(entry.getKey())).append("\": \"")
					.append(jsonEscape(entry.getValue())).append("\"");
			if (legalIndex < legalUrls.size() - 1) {
				json.append(",");
			}
			json.append("\n");
			legalIndex++;
		}
		json.append("  }\n");
		json.append("}\n");

		Files.write(reportPath, json.toString().getBytes(StandardCharsets.UTF_8));
	}

	private void printFinalReport(Map<String, String> report, Map<String, String> legalUrls) {
		System.out.println("===== SaleADS Mi Negocio Final Report =====");
		for (Map.Entry<String, String> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + entry.getValue());
		}
		if (!legalUrls.isEmpty()) {
			System.out.println("Legal URLs:");
			for (Map.Entry<String, String> entry : legalUrls.entrySet()) {
				System.out.println("  " + entry.getKey() + " -> " + entry.getValue());
			}
		}
		System.out.println("===========================================");
	}

	private String resolveLoginUrl() {
		String directLoginUrl = System.getenv("SALEADS_LOGIN_URL");
		if (directLoginUrl != null && !directLoginUrl.isBlank()) {
			return directLoginUrl;
		}

		String baseUrl = System.getenv("SALEADS_BASE_URL");
		if (baseUrl != null && !baseUrl.isBlank()) {
			return baseUrl;
		}

		throw new IllegalStateException(
				"Set SALEADS_LOGIN_URL or SALEADS_BASE_URL to run this test in the target SaleADS environment.");
	}

	private String readEnvOrDefault(String name, String fallback) {
		String value = System.getenv(name);
		if (value == null || value.isBlank()) {
			return fallback;
		}
		return value;
	}

	private String compactError(Throwable throwable) {
		List<String> parts = new ArrayList<>();
		Throwable cursor = throwable;
		while (cursor != null && parts.size() < 3) {
			String message = cursor.getMessage();
			if (message != null && !message.isBlank()) {
				parts.add(message.replace('\n', ' ').trim());
			}
			cursor = cursor.getCause();
		}
		if (parts.isEmpty()) {
			return throwable.getClass().getSimpleName();
		}
		return String.join(" | ", parts);
	}

	private String jsonEscape(String value) {
		return value
				.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "\\r")
				.replace("\t", "\\t");
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Throwable;
	}

	private static final class BrowserTypeOptions {
		private BrowserTypeOptions() {
		}

		private BrowserType.LaunchOptions launchOptions() {
			boolean headless = !"false".equalsIgnoreCase(System.getenv("SALEADS_HEADLESS"));
			return new BrowserType.LaunchOptions().setHeadless(headless);
		}
	}
}
