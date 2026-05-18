package io.proleap.saleads.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import java.util.stream.Collectors;

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

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern LOGIN_BUTTON_PATTERN = Pattern.compile(
			"(?i)(sign in with google|login with google|iniciar sesi.n con google|continuar con google|google)");
	private static final Pattern NEGOCIO_PATTERN = Pattern.compile("(?i)negocio");
	private static final Pattern MI_NEGOCIO_PATTERN = Pattern.compile("(?i)mi\\s+negocio");
	private static final Pattern AGREGAR_NEGOCIO_PATTERN = Pattern.compile("(?i)agregar\\s+negocio");
	private static final Pattern ADMINISTRAR_NEGOCIOS_PATTERN = Pattern.compile("(?i)administrar\\s+negocios");
	private static final Pattern CREAR_NUEVO_NEGOCIO_PATTERN = Pattern.compile("(?i)crear\\s+nuevo\\s+negocio");
	private static final Pattern NOMBRE_NEGOCIO_PATTERN = Pattern.compile("(?i)nombre\\s+del\\s+negocio");
	private static final Pattern NEGOCIOS_QUOTA_PATTERN = Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios");
	private static final Pattern INFO_GENERAL_PATTERN = Pattern.compile("(?i)informaci.n\\s+general");
	private static final Pattern DETALLES_CUENTA_PATTERN = Pattern.compile("(?i)detalles\\s+de\\s+la\\s+cuenta");
	private static final Pattern TUS_NEGOCIOS_PATTERN = Pattern.compile("(?i)tus\\s+negocios");
	private static final Pattern LEGAL_SECTION_PATTERN = Pattern.compile("(?i)(secci.n\\s+legal|legal)");
	private static final Pattern BUSINESS_PLAN_PATTERN = Pattern.compile("(?i)business\\s+plan");
	private static final Pattern CAMBIAR_PLAN_PATTERN = Pattern.compile("(?i)cambiar\\s+plan");
	private static final Pattern CUENTA_CREADA_PATTERN = Pattern.compile("(?i)cuenta\\s+creada");
	private static final Pattern ESTADO_ACTIVO_PATTERN = Pattern.compile("(?i)estado\\s+activo");
	private static final Pattern IDIOMA_SELECCIONADO_PATTERN = Pattern.compile("(?i)idioma\\s+seleccionado");
	private static final Pattern TERMINOS_LINK_PATTERN = Pattern.compile("(?i)(t.rminos\\s+y\\s+condiciones|terminos\\s+y\\s+condiciones)");
	private static final Pattern PRIVACIDAD_LINK_PATTERN = Pattern.compile("(?i)(pol.tica\\s+de\\s+privacidad|politica\\s+de\\s+privacidad)");
	private static final Pattern TERMINOS_HEADING_PATTERN = Pattern.compile("(?i)(t.rminos\\s+y\\s+condiciones|terminos\\s+y\\s+condiciones)");
	private static final Pattern PRIVACIDAD_HEADING_PATTERN = Pattern.compile("(?i)(pol.tica\\s+de\\s+privacidad|politica\\s+de\\s+privacidad)");

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final String loginUrl = firstNonBlank(System.getProperty("saleads.login.url"), System.getenv("SALEADS_LOGIN_URL"));
		Assume.assumeTrue("Set saleads.login.url or SALEADS_LOGIN_URL to execute this E2E test.",
				loginUrl != null && !loginUrl.isBlank());

		final String googleAccount = firstNonBlank(System.getProperty("saleads.google.account"),
				System.getenv("SALEADS_GOOGLE_ACCOUNT"), DEFAULT_GOOGLE_ACCOUNT);
		final boolean headless = Boolean.parseBoolean(firstNonBlank(System.getProperty("saleads.headless"), "true"));

		final Path runDir = Paths.get("target", "saleads-mi-negocio-full-test",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		final Path screenshotsDir = runDir.resolve("screenshots");
		Files.createDirectories(screenshotsDir);

		final List<StepResult> results = new ArrayList<>();
		final Map<String, String> externalUrls = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
			final Page appPage = context.newPage();
			appPage.navigate(loginUrl);
			waitForUi(appPage);

			runStep(results, "Login", appPage, screenshotsDir.resolve("01-login-failure.png"), () -> {
				final Locator loginButton = visibleLocatorOrThrow(appPage, "Google login button",
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(LOGIN_BUTTON_PATTERN)),
						appPage.getByText(LOGIN_BUTTON_PATTERN));
				final Page googlePage = clickAndGetPopup(context, appPage, loginButton);
				if (googlePage != null) {
					waitForUi(googlePage);
					selectGoogleAccountIfVisible(googlePage, googleAccount);
					googlePage.waitForTimeout(1200);
				}

				waitForUi(appPage);
				visibleLocatorOrThrow(appPage, "Main application interface", appPage.getByText(NEGOCIO_PATTERN),
						appPage.getByRole(AriaRole.NAVIGATION));
				visibleLocatorOrThrow(appPage, "Left sidebar navigation", appPage.getByRole(AriaRole.NAVIGATION));
				takeScreenshot(appPage, screenshotsDir.resolve("01-dashboard.png"), true);
			});

			runStep(results, "Mi Negocio menu", appPage, screenshotsDir.resolve("02-mi-negocio-menu-failure.png"), () -> {
				openMiNegocioMenu(appPage);
				visibleLocatorOrThrow(appPage, "Agregar Negocio menu entry", appPage.getByText(AGREGAR_NEGOCIO_PATTERN));
				visibleLocatorOrThrow(appPage, "Administrar Negocios menu entry",
						appPage.getByText(ADMINISTRAR_NEGOCIOS_PATTERN));
				takeScreenshot(appPage, screenshotsDir.resolve("02-mi-negocio-menu-expanded.png"), false);
			});

			runStep(results, "Agregar Negocio modal", appPage, screenshotsDir.resolve("03-agregar-negocio-modal-failure.png"), () -> {
				Locator agregarNegocio = visibleLocatorOrThrow(appPage, "Agregar Negocio", appPage.getByText(AGREGAR_NEGOCIO_PATTERN));
				clickAndWait(agregarNegocio, appPage);

				visibleLocatorOrThrow(appPage, "Modal title Crear Nuevo Negocio", appPage.getByText(CREAR_NUEVO_NEGOCIO_PATTERN));
				Locator nombreField = visibleLocatorOrThrow(appPage, "Nombre del Negocio input",
						appPage.getByLabel(NOMBRE_NEGOCIO_PATTERN), appPage.getByPlaceholder(NOMBRE_NEGOCIO_PATTERN),
						appPage.getByText(NOMBRE_NEGOCIO_PATTERN));
				visibleLocatorOrThrow(appPage, "Business quota text", appPage.getByText(NEGOCIOS_QUOTA_PATTERN));
				visibleLocatorOrThrow(appPage, "Cancelar button",
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cancelar"))));
				visibleLocatorOrThrow(appPage, "Crear Negocio button",
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)crear\\s+negocio"))));
				takeScreenshot(appPage, screenshotsDir.resolve("03-agregar-negocio-modal.png"), false);

				nombreField.click();
				nombreField.fill("Negocio Prueba Automatizacion");
				clickAndWait(visibleLocatorOrThrow(appPage, "Cancelar button",
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cancelar")))),
						appPage);
			});

			runStep(results, "Administrar Negocios view", appPage,
					screenshotsDir.resolve("04-administrar-negocios-view-failure.png"), () -> {
				openMiNegocioMenu(appPage);
				clickAndWait(visibleLocatorOrThrow(appPage, "Administrar Negocios", appPage.getByText(ADMINISTRAR_NEGOCIOS_PATTERN)),
						appPage);
				waitForUi(appPage);
				visibleLocatorOrThrow(appPage, "Informacion General section", appPage.getByText(INFO_GENERAL_PATTERN));
				visibleLocatorOrThrow(appPage, "Detalles de la Cuenta section", appPage.getByText(DETALLES_CUENTA_PATTERN));
				visibleLocatorOrThrow(appPage, "Tus Negocios section", appPage.getByText(TUS_NEGOCIOS_PATTERN));
				visibleLocatorOrThrow(appPage, "Seccion Legal section", appPage.getByText(LEGAL_SECTION_PATTERN));
				takeScreenshot(appPage, screenshotsDir.resolve("04-administrar-negocios-full-page.png"), true);
			});

			runStep(results, "Informaci\u00F3n General", appPage,
					screenshotsDir.resolve("05-informacion-general-failure.png"), () -> {
				visibleLocatorOrThrow(appPage, "User name", appPage.getByText(Pattern.compile("[A-Za-z]{2,}\\s+[A-Za-z]{2,}")));
				visibleLocatorOrThrow(appPage, "User email",
						appPage.getByText(Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")));
				visibleLocatorOrThrow(appPage, "BUSINESS PLAN text", appPage.getByText(BUSINESS_PLAN_PATTERN));
				visibleLocatorOrThrow(appPage, "Cambiar Plan button",
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CAMBIAR_PLAN_PATTERN)));
			});

			runStep(results, "Detalles de la Cuenta", appPage,
					screenshotsDir.resolve("06-detalles-cuenta-failure.png"), () -> {
				visibleLocatorOrThrow(appPage, "Cuenta creada", appPage.getByText(CUENTA_CREADA_PATTERN));
				visibleLocatorOrThrow(appPage, "Estado activo", appPage.getByText(ESTADO_ACTIVO_PATTERN));
				visibleLocatorOrThrow(appPage, "Idioma seleccionado", appPage.getByText(IDIOMA_SELECCIONADO_PATTERN));
			});

			runStep(results, "Tus Negocios", appPage, screenshotsDir.resolve("07-tus-negocios-failure.png"), () -> {
				visibleLocatorOrThrow(appPage, "Tus Negocios section", appPage.getByText(TUS_NEGOCIOS_PATTERN));
				visibleLocatorOrThrow(appPage, "Agregar Negocio button",
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)));
				visibleLocatorOrThrow(appPage, "Business quota text", appPage.getByText(NEGOCIOS_QUOTA_PATTERN));
			});

			runStep(results, "T\u00E9rminos y Condiciones", appPage,
					screenshotsDir.resolve("08-terminos-y-condiciones-failure.png"), () -> {
				Page termsPage = openLegalDocument(context, appPage, TERMINOS_LINK_PATTERN);
				visibleLocatorOrThrow(termsPage, "Terminos y Condiciones heading", termsPage.getByText(TERMINOS_HEADING_PATTERN));
				visibleLocatorOrThrow(termsPage, "Legal content text", termsPage.locator("p"),
						termsPage.getByText(Pattern.compile(".{40,}")));
				takeScreenshot(termsPage, screenshotsDir.resolve("08-terminos-y-condiciones.png"), true);
				externalUrls.put("T\u00E9rminos y Condiciones", termsPage.url());
				closeOrReturnToApp(appPage, termsPage);
			});

			runStep(results, "Pol\u00EDtica de Privacidad", appPage,
					screenshotsDir.resolve("09-politica-privacidad-failure.png"), () -> {
				Page privacyPage = openLegalDocument(context, appPage, PRIVACIDAD_LINK_PATTERN);
				visibleLocatorOrThrow(privacyPage, "Politica de Privacidad heading", privacyPage.getByText(PRIVACIDAD_HEADING_PATTERN));
				visibleLocatorOrThrow(privacyPage, "Legal content text", privacyPage.locator("p"),
						privacyPage.getByText(Pattern.compile(".{40,}")));
				takeScreenshot(privacyPage, screenshotsDir.resolve("09-politica-de-privacidad.png"), true);
				externalUrls.put("Pol\u00EDtica de Privacidad", privacyPage.url());
				closeOrReturnToApp(appPage, privacyPage);
			});
		}

		writeReport(runDir.resolve("report.md"), results, externalUrls, screenshotsDir);
		assertTrue("One or more SaleADS validations failed. Check report: " + runDir.resolve("report.md"), allPassed(results));
	}

	private void openMiNegocioMenu(final Page page) {
		final Locator negocio = visibleLocatorOrThrow(page, "Negocio section", page.getByText(NEGOCIO_PATTERN));
		clickAndWait(negocio, page);
		final Locator miNegocio = visibleLocatorOrThrow(page, "Mi Negocio option", page.getByText(MI_NEGOCIO_PATTERN));
		clickAndWait(miNegocio, page);
	}

	private Page openLegalDocument(final BrowserContext context, final Page appPage, final Pattern linkPattern) {
		final Locator legalLink = visibleLocatorOrThrow(appPage, "Legal link", appPage.getByText(linkPattern),
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(linkPattern)),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkPattern)));
		final Page popup = clickAndGetPopup(context, appPage, legalLink);
		if (popup != null) {
			waitForUi(popup);
			return popup;
		}
		waitForUi(appPage);
		return appPage;
	}

	private void closeOrReturnToApp(final Page appPage, final Page documentPage) {
		if (documentPage != appPage) {
			documentPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
			return;
		}
		try {
			appPage.goBack();
			waitForUi(appPage);
		} catch (PlaywrightException ignored) {
			appPage.bringToFront();
		}
	}

	private Page clickAndGetPopup(final BrowserContext context, final Page sourcePage, final Locator clickable) {
		final int pagesBefore = context.pages().size();
		clickable.click();
		waitForUi(sourcePage);

		for (int attempt = 0; attempt < 15; attempt++) {
			final List<Page> pages = context.pages();
			if (pages.size() > pagesBefore) {
				return pages.get(pages.size() - 1);
			}
			sourcePage.waitForTimeout(200);
		}
		return null;
	}

	private void selectGoogleAccountIfVisible(final Page googlePage, final String accountEmail) {
		try {
			Locator account = googlePage.getByText(Pattern.compile(Pattern.quote(accountEmail)));
			account.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(6000));
			account.first().click();
			waitForUi(googlePage);
		} catch (PlaywrightException ignored) {
			// Continue when account chooser is not shown (already authenticated or different Google flow).
		}
	}

	private void clickAndWait(final Locator locator, final Page page) {
		locator.first().click();
		waitForUi(page);
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(10000));
		} catch (PlaywrightException ignored) {
			// Some SPA transitions do not trigger full load events.
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10000));
		} catch (PlaywrightException ignored) {
			// Keep moving even if long polling prevents network-idle.
		}
		page.waitForTimeout(700);
	}

	private Locator visibleLocatorOrThrow(final Page page, final String description, final Locator... candidates) {
		for (Locator candidate : candidates) {
			try {
				Locator first = candidate.first();
				first.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(8000));
				return first;
			} catch (PlaywrightException ignored) {
				// Try next candidate.
			}
		}

		throw new AssertionError("Could not locate: " + description);
	}

	private void takeScreenshot(final Page page, final Path screenshotPath, final boolean fullPage) throws IOException {
		Files.createDirectories(screenshotPath.getParent());
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
	}

	private void runStep(final List<StepResult> results, final String stepName, final Page page, final Path failureScreenshotPath,
			final CheckedRunnable runnable) {
		try {
			runnable.run();
			results.add(StepResult.pass(stepName));
		} catch (Throwable throwable) {
			final String screenshotInfo = takeScreenshotSafely(page, failureScreenshotPath);
			results.add(StepResult.fail(stepName, throwable.getMessage() + screenshotInfo));
		}
	}

	private String takeScreenshotSafely(final Page page, final Path screenshotPath) {
		try {
			takeScreenshot(page, screenshotPath, true);
			return " (failure screenshot: " + screenshotPath + ")";
		} catch (Throwable ignored) {
			return "";
		}
	}

	private boolean allPassed(final List<StepResult> results) {
		return results.stream().allMatch(step -> step.passed);
	}

	private void writeReport(final Path reportPath, final List<StepResult> results, final Map<String, String> externalUrls,
			final Path screenshotsDir) throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("# SaleADS Mi Negocio Full Workflow Report\n\n");
		report.append("## Step status\n\n");

		final Map<String, String> requiredFields = new LinkedHashMap<>();
		requiredFields.put("Login", statusFor(results, "Login"));
		requiredFields.put("Mi Negocio menu", statusFor(results, "Mi Negocio menu"));
		requiredFields.put("Agregar Negocio modal", statusFor(results, "Agregar Negocio modal"));
		requiredFields.put("Administrar Negocios view", statusFor(results, "Administrar Negocios view"));
		requiredFields.put("Informaci\u00F3n General", statusFor(results, "Informaci\u00F3n General"));
		requiredFields.put("Detalles de la Cuenta", statusFor(results, "Detalles de la Cuenta"));
		requiredFields.put("Tus Negocios", statusFor(results, "Tus Negocios"));
		requiredFields.put("T\u00E9rminos y Condiciones", statusFor(results, "T\u00E9rminos y Condiciones"));
		requiredFields.put("Pol\u00EDtica de Privacidad", statusFor(results, "Pol\u00EDtica de Privacidad"));

		for (Map.Entry<String, String> entry : requiredFields.entrySet()) {
			report.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
		}

		report.append("\n## Error details\n\n");
		final List<StepResult> failed = results.stream().filter(result -> !result.passed).collect(Collectors.toList());
		if (failed.isEmpty()) {
			report.append("- None\n");
		} else {
			for (StepResult failedStep : failed) {
				report.append("- ").append(failedStep.name).append(": ").append(failedStep.details).append('\n');
			}
		}

		report.append("\n## Final legal URLs\n\n");
		if (externalUrls.isEmpty()) {
			report.append("- None captured\n");
		} else {
			for (Map.Entry<String, String> urlEntry : externalUrls.entrySet()) {
				report.append("- ").append(urlEntry.getKey()).append(": ").append(urlEntry.getValue()).append('\n');
			}
		}

		report.append("\n## Screenshot directory\n\n");
		report.append("- ").append(screenshotsDir.toString()).append('\n');

		Files.createDirectories(reportPath.getParent());
		Files.writeString(reportPath, report.toString(), StandardCharsets.UTF_8);
	}

	private String statusFor(final List<StepResult> results, final String stepName) {
		return results.stream().filter(result -> result.name.equals(stepName)).findFirst().map(result -> result.passed ? "PASS" : "FAIL")
				.orElse("NOT_RUN");
	}

	private String firstNonBlank(final String... values) {
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final String name;
		private final boolean passed;
		private final String details;

		private StepResult(final String name, final boolean passed, final String details) {
			this.name = name;
			this.passed = passed;
			this.details = details;
		}

		private static StepResult pass(final String name) {
			return new StepResult(name, true, "");
		}

		private static StepResult fail(final String name, final String details) {
			return new StepResult(name, false, details == null ? "No details" : details);
		}
	}
}
