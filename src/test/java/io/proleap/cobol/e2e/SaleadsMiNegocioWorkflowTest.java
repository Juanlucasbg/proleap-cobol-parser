package io.proleap.cobol.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;

public class SaleadsMiNegocioWorkflowTest {

	private static final double DEFAULT_TIMEOUT_MS = 30_000;
	private static final DateTimeFormatter RUN_ID_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final String loginUrl = env("SALEADS_LOGIN_URL");
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL with the current SaleADS login page. The test is URL-agnostic and does not hardcode domains.",
				loginUrl != null && !loginUrl.isBlank());

		final String googleAccount = envOrDefault("SALEADS_GOOGLE_ACCOUNT", "juanlucasbarbiergarzon@gmail.com");
		final String expectedUserName = envOrDefault("SALEADS_EXPECTED_USER_NAME", "").trim();
		final boolean headless = Boolean.parseBoolean(envOrDefault("SALEADS_HEADLESS", "true"));
		final double timeoutMs = parseTimeout(envOrDefault("SALEADS_TIMEOUT_MS", String.valueOf((int) DEFAULT_TIMEOUT_MS)));
		final Path screenshotDir = createScreenshotDir();

		final Map<String, Boolean> report = initReport();
		final Map<String, String> legalUrls = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setIgnoreHTTPSErrors(true));
			final Page appPage = context.newPage();
			appPage.setDefaultTimeout(timeoutMs);

			appPage.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			waitForUi(appPage);

			final boolean loginPassed = runStep(report, "Login", () -> {
				executeGoogleLogin(appPage, googleAccount, timeoutMs);
				validateSidebarLoaded(appPage);
				capture(appPage, screenshotDir, "01-dashboard-loaded", true);
			});

			final boolean miNegocioMenuPassed = loginPassed && runStep(report, "Mi Negocio menu", () -> {
				clickVisibleText(appPage, "Mi Negocio");
				waitForUi(appPage);
				assertTextVisible(appPage, "Agregar Negocio");
				assertTextVisible(appPage, "Administrar Negocios");
				capture(appPage, screenshotDir, "02-mi-negocio-menu-expanded", true);
			});
			if (!miNegocioMenuPassed) {
				report.put("Agregar Negocio modal", false);
				report.put("Administrar Negocios view", false);
				report.put("Información General", false);
				report.put("Detalles de la Cuenta", false);
				report.put("Tus Negocios", false);
				report.put("Términos y Condiciones", false);
				report.put("Política de Privacidad", false);
				printReport(report, legalUrls, screenshotDir);
				assertNoFailures(report);
				return;
			}

			final boolean agregarNegocioModalPassed = runStep(report, "Agregar Negocio modal", () -> {
				clickVisibleText(appPage, "Agregar Negocio");
				assertTextVisible(appPage, "Crear Nuevo Negocio");
				assertVisible(firstVisible(appPage.getByLabel(Pattern.compile("(?i)Nombre del Negocio")),
						appPage.getByPlaceholder(Pattern.compile("(?i)Nombre del Negocio"))),
						"Input 'Nombre del Negocio' no visible.");
				assertTextVisible(appPage, "Tienes 2 de 3 negocios");
				assertButtonVisible(appPage, "Cancelar");
				assertButtonVisible(appPage, "Crear Negocio");
				capture(appPage, screenshotDir, "03-agregar-negocio-modal", true);

				// Optional interaction requested in the workflow.
				final Locator nombreNegocioInput = firstVisible(appPage.getByLabel(Pattern.compile("(?i)Nombre del Negocio")),
						appPage.getByPlaceholder(Pattern.compile("(?i)Nombre del Negocio")));
				nombreNegocioInput.fill("Negocio Prueba Automatización");
				clickButton(appPage, "Cancelar");
				waitForUi(appPage);
			});

			final boolean administrarNegociosViewPassed = agregarNegocioModalPassed
					&& runStep(report, "Administrar Negocios view", () -> {
						if (!isTextVisible(appPage, "Administrar Negocios")) {
							clickVisibleText(appPage, "Mi Negocio");
							waitForUi(appPage);
						}
						clickVisibleText(appPage, "Administrar Negocios");
						waitForUi(appPage);

						assertTextVisible(appPage, "Información General");
						assertTextVisible(appPage, "Detalles de la Cuenta");
						assertTextVisible(appPage, "Tus Negocios");
						assertTextVisible(appPage, "Sección Legal");
						capture(appPage, screenshotDir, "04-administrar-negocios", true);
					});

			if (!administrarNegociosViewPassed) {
				report.put("Información General", false);
				report.put("Detalles de la Cuenta", false);
				report.put("Tus Negocios", false);
				report.put("Términos y Condiciones", false);
				report.put("Política de Privacidad", false);
				printReport(report, legalUrls, screenshotDir);
				assertNoFailures(report);
				return;
			}

			runStep(report, "Información General", () -> {
				assertUserNameVisible(appPage, expectedUserName);
				assertEmailVisible(appPage, googleAccount);
				assertTextVisible(appPage, "BUSINESS PLAN");
				assertButtonVisible(appPage, "Cambiar Plan");
			});

			runStep(report, "Detalles de la Cuenta", () -> {
				assertTextVisible(appPage, "Cuenta creada");
				assertTextVisible(appPage, "Estado activo");
				assertTextVisible(appPage, "Idioma seleccionado");
			});

			runStep(report, "Tus Negocios", () -> {
				assertTextVisible(appPage, "Tus Negocios");
				assertButtonVisible(appPage, "Agregar Negocio");
				assertTextVisible(appPage, "Tienes 2 de 3 negocios");
			});

			runStep(report, "Términos y Condiciones", () -> {
				final String finalUrl = validateLegalLink(appPage, "Términos y Condiciones", "08-terminos-condiciones",
						screenshotDir, timeoutMs);
				legalUrls.put("Términos y Condiciones", finalUrl);
			});

			runStep(report, "Política de Privacidad", () -> {
				final String finalUrl = validateLegalLink(appPage, "Política de Privacidad", "09-politica-privacidad",
						screenshotDir, timeoutMs);
				legalUrls.put("Política de Privacidad", finalUrl);
			});

			printReport(report, legalUrls, screenshotDir);
			assertNoFailures(report);
		}
	}

	private static void executeGoogleLogin(final Page appPage, final String googleAccount, final double timeoutMs) {
		final Locator googleButton = firstVisible(
				appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)sign\\s*in\\s*with\\s*google"))),
				appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)iniciar\\s*sesi[oó]n\\s*con\\s*google"))),
				appPage.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(Pattern.compile("(?i)continuar\\s*con\\s*google"))),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)google"))));
		assertVisible(googleButton, "No se encontró botón de login con Google.");

		Page authPage = null;
		try {
			appPage.setDefaultTimeout(Math.min(timeoutMs, 7_000));
			authPage = appPage.waitForPopup(() -> googleButton.click());
		} catch (final TimeoutError e) {
			// Same-tab navigation is valid; continue on the application page.
		} finally {
			appPage.setDefaultTimeout(timeoutMs);
		}

		final Page activePage = authPage != null ? authPage : appPage;
		waitForUi(activePage);

		if (isTextVisible(activePage, googleAccount)) {
			clickVisibleText(activePage, googleAccount);
			waitForUi(activePage);
		}

		if (authPage != null && !authPage.isClosed()) {
			waitForUi(authPage);
			appPage.bringToFront();
		}

		// Dashboard load can take time after OAuth redirect.
		waitForAnyText(appPage, List.of("Mi Negocio", "Negocio", "Dashboard"), 120_000);
		waitForUi(appPage);
	}

	private static void validateSidebarLoaded(final Page page) {
		final Locator sidebar = firstVisible(page.locator("aside"), page.locator("nav"));
		assertVisible(sidebar, "No se detectó sidebar/menú lateral tras el login.");
	}

	private static String validateLegalLink(final Page appPage, final String linkText, final String screenshotName,
			final Path screenshotDir, final double timeoutMs) {
		final String originalUrl = appPage.url();
		final Locator link = firstVisible(
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + Pattern.quote(linkText)))),
				appPage.getByText(Pattern.compile("(?i)" + Pattern.quote(linkText))));
		assertVisible(link, "No se encontró el enlace legal: " + linkText);

		Page legalPage = null;
		try {
			appPage.setDefaultTimeout(7_000);
			legalPage = appPage.waitForPopup(() -> link.click());
		} catch (final TimeoutError e) {
			// Same-tab navigation is valid.
		} finally {
			appPage.setDefaultTimeout(timeoutMs);
		}

		final Page targetPage = legalPage != null ? legalPage : appPage;
		waitForUi(targetPage);
		assertTextVisible(targetPage, linkText);
		assertLegalContentVisible(targetPage, linkText);
		capture(targetPage, screenshotDir, screenshotName, true);
		final String finalUrl = targetPage.url();

		if (legalPage != null) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else if (!originalUrl.equals(appPage.url())) {
			appPage.goBack(new Page.GoBackOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private static void assertLegalContentVisible(final Page page, final String linkText) {
		final String bodyText = page.locator("body").innerText();
		Assert.assertTrue("No se encontró contenido legal visible para " + linkText + ".",
				bodyText != null && bodyText.trim().length() > 80);
	}

	private static void assertUserNameVisible(final Page page, final String expectedUserName) {
		if (!expectedUserName.isBlank()) {
			assertTextVisible(page, expectedUserName);
			return;
		}

		final String text = page.locator("body").innerText();
		final Matcher matcher = Pattern.compile("(?m)^[\\p{L}][\\p{L} .'-]{2,}$").matcher(text);
		final List<String> ignoredLabels = List.of("Información General", "Detalles de la Cuenta", "Tus Negocios",
				"Sección Legal", "BUSINESS PLAN", "Cuenta creada", "Estado activo", "Idioma seleccionado");
		while (matcher.find()) {
			final String candidate = matcher.group().trim();
			if (!ignoredLabels.contains(candidate) && !candidate.contains("@")) {
				return;
			}
		}

		Assert.fail(
				"No se pudo validar de forma robusta el nombre del usuario. Configure SALEADS_EXPECTED_USER_NAME para validación estricta.");
	}

	private static void assertEmailVisible(final Page page, final String expectedEmail) {
		final String bodyText = page.locator("body").innerText();
		if (bodyText.contains(expectedEmail)) {
			return;
		}
		Assert.assertTrue("No se encontró email visible en Información General.", EMAIL_PATTERN.matcher(bodyText).find());
	}

	private static void clickVisibleText(final Page page, final String text) {
		final Locator target = firstVisible(
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + Pattern.quote(text)))),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + Pattern.quote(text)))),
				page.getByText(Pattern.compile("(?i)" + Pattern.quote(text))));
		assertVisible(target, "No se encontró elemento clickeable con texto: " + text);
		target.click();
		waitForUi(page);
	}

	private static void clickButton(final Page page, final String text) {
		final Locator button = firstVisible(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + Pattern.quote(text)))),
				page.getByText(Pattern.compile("(?i)" + Pattern.quote(text))));
		assertVisible(button, "No se encontró botón: " + text);
		button.click();
		waitForUi(page);
	}

	private static void assertButtonVisible(final Page page, final String buttonText) {
		final Locator button = firstVisible(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + Pattern.quote(buttonText)))),
				page.getByText(Pattern.compile("(?i)" + Pattern.quote(buttonText))));
		assertVisible(button, "No se encontró botón visible: " + buttonText);
	}

	private static boolean isTextVisible(final Page page, final String text) {
		try {
			final Locator locator = page.getByText(Pattern.compile("(?i)" + Pattern.quote(text))).first();
			locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(4_000));
			return locator.isVisible();
		} catch (final RuntimeException e) {
			return false;
		}
	}

	private static void assertTextVisible(final Page page, final String text) {
		final Locator locator = page.getByText(Pattern.compile("(?i)" + Pattern.quote(text))).first();
		locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));
		Assert.assertTrue("No se encontró texto visible: " + text, locator.isVisible());
	}

	private static void waitForAnyText(final Page page, final List<String> texts, final double timeoutMs) {
		final long deadlineMs = System.currentTimeMillis() + (long) timeoutMs;
		while (System.currentTimeMillis() < deadlineMs) {
			for (final String text : texts) {
				if (isTextVisible(page, text)) {
					return;
				}
			}
			page.waitForTimeout(500);
		}
		Assert.fail("Ninguno de los textos esperados fue visible: " + texts);
	}

	private static Locator firstVisible(final Locator... options) {
		for (final Locator option : options) {
			if (option == null) {
				continue;
			}
			try {
				if (option.count() > 0) {
					final Locator first = option.first();
					first.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(3_000));
					if (first.isVisible()) {
						return first;
					}
				}
			} catch (final RuntimeException e) {
				// Try next candidate locator.
			}
		}
		return null;
	}

	private static void assertVisible(final Locator locator, final String failureMessage) {
		Assert.assertNotNull(failureMessage, locator);
		Assert.assertTrue(failureMessage, locator.isVisible());
	}

	private static void waitForUi(final Page page) {
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE);
		} catch (final RuntimeException e) {
			// Some environments keep active connections. DOM readiness is enough.
		}
		page.waitForTimeout(400);
	}

	private static Path createScreenshotDir() throws IOException {
		final String configured = envOrDefault("SALEADS_SCREENSHOT_DIR", "target/saleads-evidence");
		final Path runDir = Path.of(configured, "run-" + RUN_ID_FORMATTER.format(LocalDateTime.now()));
		Files.createDirectories(runDir);
		return runDir;
	}

	private static void capture(final Page page, final Path screenshotDir, final String name, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotDir.resolve(name + ".png")).setFullPage(fullPage));
	}

	private static boolean runStep(final Map<String, Boolean> report, final String reportKey, final Runnable action) {
		try {
			action.run();
			report.put(reportKey, true);
			return true;
		} catch (final RuntimeException | AssertionError e) {
			System.err.println("[FAIL] " + reportKey + " -> " + e.getMessage());
			report.put(reportKey, false);
			return false;
		}
	}

	private static Map<String, Boolean> initReport() {
		final Map<String, Boolean> report = new LinkedHashMap<>();
		report.put("Login", false);
		report.put("Mi Negocio menu", false);
		report.put("Agregar Negocio modal", false);
		report.put("Administrar Negocios view", false);
		report.put("Información General", false);
		report.put("Detalles de la Cuenta", false);
		report.put("Tus Negocios", false);
		report.put("Términos y Condiciones", false);
		report.put("Política de Privacidad", false);
		return report;
	}

	private static void printReport(final Map<String, Boolean> report, final Map<String, String> legalUrls,
			final Path screenshotDir) {
		System.out.println("== SaleADS Mi Negocio Workflow Report ==");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
		}
		System.out.println("Screenshots directory: " + screenshotDir.toAbsolutePath());
		for (final Map.Entry<String, String> urlEntry : legalUrls.entrySet()) {
			System.out.println(urlEntry.getKey() + " final URL: " + urlEntry.getValue());
		}
	}

	private static void assertNoFailures(final Map<String, Boolean> report) {
		final List<String> failed = new ArrayList<>();
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			if (!entry.getValue()) {
				failed.add(entry.getKey());
			}
		}
		Assert.assertTrue("Hay validaciones fallidas: " + failed, failed.isEmpty());
	}

	private static String env(final String key) {
		return System.getenv(key);
	}

	private static String envOrDefault(final String key, final String fallback) {
		final String value = env(key);
		return value == null || value.isBlank() ? fallback : value;
	}

	private static double parseTimeout(final String rawTimeout) {
		try {
			return Double.parseDouble(rawTimeout);
		} catch (final NumberFormatException e) {
			return DEFAULT_TIMEOUT_MS;
		}
	}
}
