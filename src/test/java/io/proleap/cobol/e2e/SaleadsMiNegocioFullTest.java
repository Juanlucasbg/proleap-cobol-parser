package io.proleap.cobol.e2e;

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
import java.io.IOException;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;

public class SaleadsMiNegocioFullTest {

	private static final String ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final int DEFAULT_TIMEOUT_MS = 30000;
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
	private static final Pattern LOGIN_WITH_GOOGLE_PATTERN = Pattern.compile(
			"(?i)(sign\\s*in\\s*with\\s*google|iniciar\\s*sesi[o\\u00f3]n\\s*con\\s*google|ingresar\\s*con\\s*google)");
	private static final Pattern MI_NEGOCIO_PATTERN = Pattern.compile("(?i)mi\\s+negocio");
	private static final Pattern AGREGAR_NEGOCIO_PATTERN = Pattern.compile("(?i)agregar\\s+negocio");
	private static final Pattern ADMINISTRAR_NEGOCIOS_PATTERN = Pattern.compile("(?i)administrar\\s+negocios");
	private static final Pattern CREAR_NUEVO_NEGOCIO_PATTERN = Pattern.compile("(?i)crear\\s+nuevo\\s+negocio");
	private static final Pattern NOMBRE_NEGOCIO_PATTERN = Pattern.compile("(?i)nombre\\s+del\\s+negocio");
	private static final Pattern NEGOCIOS_LIMIT_PATTERN = Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios");
	private static final Pattern INFORMACION_GENERAL_PATTERN = Pattern.compile("(?i)informaci[o\\u00f3]n\\s+general");
	private static final Pattern DETALLES_CUENTA_PATTERN = Pattern.compile("(?i)detalles\\s+de\\s+la\\s+cuenta");
	private static final Pattern TUS_NEGOCIOS_PATTERN = Pattern.compile("(?i)tus\\s+negocios");
	private static final Pattern SECCION_LEGAL_PATTERN = Pattern.compile("(?i)secci[o\\u00f3]n\\s+legal");
	private static final Pattern TERMINOS_PATTERN = Pattern.compile("(?i)t[e\\u00e9]rminos\\s+y\\s+condiciones");
	private static final Pattern POLITICA_PATTERN = Pattern.compile("(?i)pol[i\\u00ed]tica\\s+de\\s+privacidad");
	private static final Pattern BUSINESS_PLAN_PATTERN = Pattern.compile("(?i)business\\s+plan");
	private static final Pattern CAMBIAR_PLAN_PATTERN = Pattern.compile("(?i)cambiar\\s+plan");

	private final Map<String, Boolean> finalReport = new LinkedHashMap<>();
	private final Map<String, String> reportDetails = new LinkedHashMap<>();

	private Path evidenceDir;
	private int timeoutMs;

	@Test
	public void saleadsMiNegocioWorkflow() throws Exception {
		timeoutMs = Integer.parseInt(System.getProperty("saleads.timeout.ms", String.valueOf(DEFAULT_TIMEOUT_MS)));
		evidenceDir = buildEvidenceDir();
		Files.createDirectories(evidenceDir);
		initReport();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
					.setHeadless(Boolean.parseBoolean(System.getProperty("saleads.headless", "true"))));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1200));
			try {
				Page appPage = context.newPage();
				openLoginPage(appPage);
				appPage = runLoginStep(context, appPage);
				runMiNegocioMenuStep(appPage);
				runAgregarNegocioModalStep(appPage);
				runAdministrarNegociosStep(appPage);
				runInformacionGeneralStep(appPage);
				runDetallesCuentaStep(appPage);
				runTusNegociosStep(appPage);
				appPage = runLegalLinkStep(context, appPage, TERMINOS_PATTERN, "terminos-condiciones.png",
						"T\u00e9rminos y Condiciones");
				appPage = runLegalLinkStep(context, appPage, POLITICA_PATTERN, "politica-privacidad.png",
						"Pol\u00edtica de Privacidad");
			} finally {
				context.close();
				browser.close();
			}
		} finally {
			writeReportFile();
		}

		final List<String> failedSteps = finalReport.entrySet().stream().filter(entry -> !entry.getValue())
				.map(Map.Entry::getKey).collect(Collectors.toList());
		Assert.assertTrue("Failed validations: " + failedSteps + ". See report at " + evidenceDir.resolve("final-report.txt"),
				failedSteps.isEmpty());
	}

	private void initReport() {
		finalReport.put("Login", false);
		finalReport.put("Mi Negocio menu", false);
		finalReport.put("Agregar Negocio modal", false);
		finalReport.put("Administrar Negocios view", false);
		finalReport.put("Informaci\u00f3n General", false);
		finalReport.put("Detalles de la Cuenta", false);
		finalReport.put("Tus Negocios", false);
		finalReport.put("T\u00e9rminos y Condiciones", false);
		finalReport.put("Pol\u00edtica de Privacidad", false);
	}

	private Path buildEvidenceDir() {
		final String runId = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT).format(LocalDateTime.now());
		return Paths.get("target", "saleads-evidence", runId);
	}

	private void openLoginPage(final Page page) {
		final String loginUrl = readPropertyOrEnv("saleads.url", "SALEADS_URL");
		if (loginUrl == null || loginUrl.isBlank()) {
			throw new IllegalStateException(
					"Missing login URL. Configure -Dsaleads.url=<current-env-login-url> or SALEADS_URL.");
		}

		page.navigate(loginUrl, new Page.NavigateOptions().setTimeout((double) timeoutMs));
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
	}

	private Page runLoginStep(final BrowserContext context, Page appPage) {
		try {
			final Locator loginButton = firstVisibleLocator(appPage,
					appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(LOGIN_WITH_GOOGLE_PATTERN)),
					appPage.getByText(LOGIN_WITH_GOOGLE_PATTERN),
					appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(LOGIN_WITH_GOOGLE_PATTERN)));
			requireVisible(loginButton, "Google login button was not found.");

			Page activePage = clickAndResolveActivePage(context, appPage, loginButton);
			final Locator accountOption = activePage.getByText(ACCOUNT_EMAIL);
			if (isVisible(accountOption)) {
				clickAndWait(activePage, accountOption.first());
			}

			final Page resolvedAppPage = waitForApplicationPage(context, appPage);
			waitForUiIdle(resolvedAppPage);

			final boolean mainUiVisible = isVisible(resolvedAppPage.locator("aside"))
					|| isVisible(resolvedAppPage.getByText(Pattern.compile("(?i)(dashboard|inicio|panel|negocio)")));
			final boolean sidebarVisible = isVisible(resolvedAppPage.locator("aside"))
					|| isVisible(resolvedAppPage.getByText(Pattern.compile("(?i)negocio")));

			takeScreenshot(resolvedAppPage, "dashboard-loaded.png", true);
			recordStep("Login", StepOutcome.of(mainUiVisible && sidebarVisible,
					"mainUiVisible=" + mainUiVisible + ", sidebarVisible=" + sidebarVisible));
			return resolvedAppPage;
		} catch (Exception e) {
			recordStepFailure("Login", e);
			return appPage;
		}
	}

	private void runMiNegocioMenuStep(final Page page) {
		executeStep("Mi Negocio menu", () -> {
			clickIfVisible(page, Pattern.compile("(?i)^negocio$"));
			final Locator miNegocio = firstVisibleLocator(page, page.getByText(MI_NEGOCIO_PATTERN),
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(MI_NEGOCIO_PATTERN)));
			requireVisible(miNegocio, "'Mi Negocio' option was not visible.");
			clickAndWait(page, miNegocio.first());

			final boolean agregarVisible = isVisible(page.getByText(AGREGAR_NEGOCIO_PATTERN));
			final boolean administrarVisible = isVisible(page.getByText(ADMINISTRAR_NEGOCIOS_PATTERN));
			takeScreenshot(page, "mi-negocio-menu-expanded.png", false);
			return StepOutcome.of(agregarVisible && administrarVisible,
					"agregarVisible=" + agregarVisible + ", administrarVisible=" + administrarVisible);
		});
	}

	private void runAgregarNegocioModalStep(final Page page) {
		executeStep("Agregar Negocio modal", () -> {
			final Locator agregarNegocio = firstVisibleLocator(page,
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)),
					page.getByText(AGREGAR_NEGOCIO_PATTERN));
			requireVisible(agregarNegocio, "'Agregar Negocio' action was not found.");
			clickAndWait(page, agregarNegocio.first());

			final boolean modalTitleVisible = isVisible(page.getByText(CREAR_NUEVO_NEGOCIO_PATTERN));
			final boolean inputVisible = isVisible(page.getByLabel(NOMBRE_NEGOCIO_PATTERN))
					|| isVisible(page.getByPlaceholder(NOMBRE_NEGOCIO_PATTERN))
					|| isVisible(page.getByText(NOMBRE_NEGOCIO_PATTERN));
			final boolean limitTextVisible = isVisible(page.getByText(NEGOCIOS_LIMIT_PATTERN));
			final boolean cancelarVisible = isVisible(page.getByRole(AriaRole.BUTTON,
					new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cancelar"))));
			final boolean crearVisible = isVisible(page.getByRole(AriaRole.BUTTON,
					new Page.GetByRoleOptions().setName(Pattern.compile("(?i)crear\\s+negocio"))));

			if (inputVisible) {
				final Locator nombreInput = firstVisibleLocator(page, page.getByLabel(NOMBRE_NEGOCIO_PATTERN),
						page.getByPlaceholder(NOMBRE_NEGOCIO_PATTERN), page.locator("input").first());
				if (nombreInput != null) {
					nombreInput.fill("Negocio Prueba Automatizacion");
				}
			}

			takeScreenshot(page, "agregar-negocio-modal.png", false);
			clickIfVisible(page, Pattern.compile("(?i)^cancelar$"));

			return StepOutcome.of(modalTitleVisible && inputVisible && limitTextVisible && cancelarVisible && crearVisible,
					"modalTitleVisible=" + modalTitleVisible + ", inputVisible=" + inputVisible + ", limitTextVisible="
							+ limitTextVisible + ", cancelarVisible=" + cancelarVisible + ", crearVisible=" + crearVisible);
		});
	}

	private void runAdministrarNegociosStep(final Page page) {
		executeStep("Administrar Negocios view", () -> {
			if (!isVisible(page.getByText(ADMINISTRAR_NEGOCIOS_PATTERN))) {
				clickIfVisible(page, MI_NEGOCIO_PATTERN);
			}
			final Locator administrar = firstVisibleLocator(page, page.getByText(ADMINISTRAR_NEGOCIOS_PATTERN),
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(ADMINISTRAR_NEGOCIOS_PATTERN)),
					page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(ADMINISTRAR_NEGOCIOS_PATTERN)));
			requireVisible(administrar, "'Administrar Negocios' option was not visible.");
			clickAndWait(page, administrar.first());
			waitForUiIdle(page);

			final boolean infoGeneralVisible = isVisible(page.getByText(INFORMACION_GENERAL_PATTERN));
			final boolean detallesVisible = isVisible(page.getByText(DETALLES_CUENTA_PATTERN));
			final boolean tusNegociosVisible = isVisible(page.getByText(TUS_NEGOCIOS_PATTERN));
			final boolean seccionLegalVisible = isVisible(page.getByText(SECCION_LEGAL_PATTERN));

			takeScreenshot(page, "administrar-negocios-page.png", true);
			return StepOutcome.of(infoGeneralVisible && detallesVisible && tusNegociosVisible && seccionLegalVisible,
					"infoGeneralVisible=" + infoGeneralVisible + ", detallesVisible=" + detallesVisible
							+ ", tusNegociosVisible=" + tusNegociosVisible + ", seccionLegalVisible=" + seccionLegalVisible);
		});
	}

	private void runInformacionGeneralStep(final Page page) {
		executeStep("Informaci\u00f3n General", () -> {
			final String infoText = sectionOrBodyText(page, INFORMACION_GENERAL_PATTERN);
			final boolean userEmailVisible = EMAIL_PATTERN.matcher(infoText).find();
			final boolean userNameVisible = hasLikelyUserName(infoText);
			final boolean businessPlanVisible = isVisible(page.getByText(BUSINESS_PLAN_PATTERN));
			final boolean cambiarPlanVisible = isVisible(
					page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(CAMBIAR_PLAN_PATTERN)));
			return StepOutcome.of(userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible,
					"userNameVisible=" + userNameVisible + ", userEmailVisible=" + userEmailVisible
							+ ", businessPlanVisible=" + businessPlanVisible + ", cambiarPlanVisible=" + cambiarPlanVisible);
		});
	}

	private void runDetallesCuentaStep(final Page page) {
		executeStep("Detalles de la Cuenta", () -> {
			final boolean cuentaCreadaVisible = isVisible(page.getByText(Pattern.compile("(?i)cuenta\\s+creada")));
			final boolean estadoActivoVisible = isVisible(page.getByText(Pattern.compile("(?i)estado\\s+activo")));
			final boolean idiomaSeleccionadoVisible = isVisible(
					page.getByText(Pattern.compile("(?i)idioma\\s+seleccionado")));
			return StepOutcome.of(cuentaCreadaVisible && estadoActivoVisible && idiomaSeleccionadoVisible,
					"cuentaCreadaVisible=" + cuentaCreadaVisible + ", estadoActivoVisible=" + estadoActivoVisible
							+ ", idiomaSeleccionadoVisible=" + idiomaSeleccionadoVisible);
		});
	}

	private void runTusNegociosStep(final Page page) {
		executeStep("Tus Negocios", () -> {
			final String negociosText = sectionOrBodyText(page, TUS_NEGOCIOS_PATTERN);
			final boolean businessListVisible = negociosText.toLowerCase(Locale.ROOT).contains("negocio")
					|| isVisible(page.locator("ul li")) || isVisible(page.locator("table tbody tr"));
			final boolean agregarNegocioButtonVisible = isVisible(page.getByRole(AriaRole.BUTTON,
					new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)))
					|| isVisible(page.getByText(AGREGAR_NEGOCIO_PATTERN));
			final boolean limitTextVisible = isVisible(page.getByText(NEGOCIOS_LIMIT_PATTERN));
			return StepOutcome.of(businessListVisible && agregarNegocioButtonVisible && limitTextVisible,
					"businessListVisible=" + businessListVisible + ", agregarNegocioButtonVisible="
							+ agregarNegocioButtonVisible + ", limitTextVisible=" + limitTextVisible);
		});
	}

	private Page runLegalLinkStep(final BrowserContext context, final Page appPage, final Pattern linkPattern,
			final String screenshotFileName, final String reportKey) {
		try {
			final Locator legalLink = firstVisibleLocator(appPage,
					appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkPattern)),
					appPage.getByText(linkPattern));
			requireVisible(legalLink, "Legal link was not visible for " + reportKey);

			final int pageCountBeforeClick = context.pages().size();
			clickAndWait(appPage, legalLink.first());
			waitForUiIdle(appPage);

			Page legalPage = appPage;
			for (int i = 0; i < 20; i++) {
				if (context.pages().size() > pageCountBeforeClick) {
					legalPage = context.pages().get(context.pages().size() - 1);
					break;
				}
				appPage.waitForTimeout(250);
			}

			legalPage.bringToFront();
			legalPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
			waitForUiIdle(legalPage);

			final boolean headingVisible = isVisible(legalPage.getByText(linkPattern));
			final String legalText = safeInnerText(legalPage.locator("body"));
			final boolean legalContentVisible = legalText != null && legalText.trim().length() > 100;
			final String finalUrl = legalPage.url();
			takeScreenshot(legalPage, screenshotFileName, true);

			if (legalPage != appPage && !legalPage.isClosed()) {
				legalPage.close();
				appPage.bringToFront();
			} else if (legalPage == appPage && !appPage.url().equals(readPropertyOrEnv("saleads.url", "SALEADS_URL"))) {
				appPage.goBack();
				waitForUiIdle(appPage);
			}

			recordStep(reportKey, StepOutcome.of(headingVisible && legalContentVisible,
					"headingVisible=" + headingVisible + ", legalContentVisible=" + legalContentVisible + ", finalUrl="
							+ finalUrl));
			return appPage;
		} catch (Exception e) {
			recordStepFailure(reportKey, e);
			return appPage;
		}
	}

	private Page waitForApplicationPage(final BrowserContext context, final Page currentPage) {
		for (int i = 0; i < 60; i++) {
			for (final Page candidate : context.pages()) {
				try {
					if (!candidate.isClosed() && (isVisible(candidate.locator("aside"))
							|| isVisible(candidate.getByText(Pattern.compile("(?i)(mi\\s+negocio|negocio|dashboard)"))))) {
						candidate.bringToFront();
						return candidate;
					}
				} catch (PlaywrightException ignored) {
					// ignore transient detached page states while auth transitions.
				}
			}
			currentPage.waitForTimeout(1000);
		}
		return currentPage;
	}

	private Page clickAndResolveActivePage(final BrowserContext context, final Page currentPage, final Locator locator) {
		final int pagesBeforeClick = context.pages().size();
		clickAndWait(currentPage, locator);
		for (int i = 0; i < 20; i++) {
			if (context.pages().size() > pagesBeforeClick) {
				final Page newPage = context.pages().get(context.pages().size() - 1);
				newPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
				return newPage;
			}
			currentPage.waitForTimeout(250);
		}
		return currentPage;
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.first().click(new Locator.ClickOptions().setTimeout((double) timeoutMs));
		waitForUiIdle(page);
	}

	private void waitForUiIdle(final Page page) {
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout((double) timeoutMs));
		} catch (TimeoutError ignored) {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		}
		page.waitForTimeout(400);
	}

	private void clickIfVisible(final Page page, final Pattern textPattern) {
		final Locator locator = firstVisibleLocator(page,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(textPattern)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(textPattern)),
				page.getByText(textPattern));
		if (locator != null) {
			clickAndWait(page, locator.first());
		}
	}

	private Locator firstVisibleLocator(final Page page, final Locator... candidates) {
		for (final Locator candidate : candidates) {
			if (candidate == null) {
				continue;
			}
			try {
				final int limit = Math.min(candidate.count(), 5);
				for (int i = 0; i < limit; i++) {
					final Locator nth = candidate.nth(i);
					if (nth.isVisible(new Locator.IsVisibleOptions().setTimeout(500))) {
						return nth;
					}
				}
			} catch (PlaywrightException ignored) {
				// ignore candidate resolution errors and continue to next selector.
			}
		}
		return null;
	}

	private boolean isVisible(final Locator locator) {
		if (locator == null) {
			return false;
		}
		try {
			final int count = Math.min(locator.count(), 5);
			for (int i = 0; i < count; i++) {
				if (locator.nth(i).isVisible(new Locator.IsVisibleOptions().setTimeout(1000))) {
					return true;
				}
			}
			return false;
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private void requireVisible(final Locator locator, final String errorMessage) {
		if (locator == null || !isVisible(locator)) {
			throw new IllegalStateException(errorMessage);
		}
	}

	private void takeScreenshot(final Page page, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(fileName)).setFullPage(fullPage));
	}

	private String sectionOrBodyText(final Page page, final Pattern sectionHeading) {
		final Locator heading = firstVisibleLocator(page, page.getByText(sectionHeading));
		if (heading != null) {
			final Locator parentContainer = heading.locator("xpath=ancestor::*[self::section or self::div][1]");
			final String sectionText = safeInnerText(parentContainer);
			if (sectionText != null && !sectionText.isBlank()) {
				return sectionText;
			}
		}
		final String bodyText = safeInnerText(page.locator("body"));
		return bodyText == null ? "" : bodyText;
	}

	private String safeInnerText(final Locator locator) {
		try {
			return locator.innerText();
		} catch (PlaywrightException e) {
			return "";
		}
	}

	private boolean hasLikelyUserName(final String text) {
		final List<String> stopwords = Arrays.asList("informacion general", "informaci\u00f3n general", "business plan",
				"cambiar plan", "detalles de la cuenta", "tus negocios", "seccion legal", "secci\u00f3n legal");
		for (final String rawLine : text.split("\\R")) {
			final String line = rawLine.trim();
			if (line.length() < 3 || line.length() > 60 || line.contains("@")) {
				continue;
			}
			if (!line.matches(".*[A-Za-z].*")) {
				continue;
			}
			final String normalized = line.toLowerCase(Locale.ROOT);
			if (stopwords.contains(normalized)) {
				continue;
			}
			if (normalized.contains("estado activo") || normalized.contains("idioma seleccionado")
					|| normalized.contains("cuenta creada") || normalized.contains("agregar negocio")) {
				continue;
			}
			return true;
		}
		return false;
	}

	private String readPropertyOrEnv(final String propertyName, final String envName) {
		return Optional.ofNullable(System.getProperty(propertyName)).filter(value -> !value.isBlank())
				.orElseGet(() -> Optional.ofNullable(System.getenv(envName)).orElse(null));
	}

	private void writeReportFile() throws IOException {
		final List<String> lines = new ArrayList<>();
		lines.add("SaleADS Mi Negocio Full Test Report");
		lines.add("Evidence directory: " + evidenceDir.toAbsolutePath());
		lines.add("");
		for (final Map.Entry<String, Boolean> entry : finalReport.entrySet()) {
			final String stepName = entry.getKey();
			final String status = entry.getValue() ? "PASS" : "FAIL";
			final String detail = reportDetails.getOrDefault(stepName, "No detail recorded.");
			lines.add(stepName + ": " + status);
			lines.add("  " + detail);
		}
		Files.write(evidenceDir.resolve("final-report.txt"), lines);
	}

	private void executeStep(final String stepName, final StepRunner runner) {
		try {
			final StepOutcome outcome = runner.run();
			recordStep(stepName, outcome);
		} catch (Exception e) {
			recordStepFailure(stepName, e);
		}
	}

	private interface StepRunner {
		StepOutcome run() throws Exception;
	}

	private void recordStep(final String stepName, final StepOutcome outcome) {
		finalReport.put(stepName, outcome.pass);
		reportDetails.put(stepName, outcome.detail);
	}

	private static final class StepOutcome {
		private final boolean pass;
		private final String detail;

		private StepOutcome(final boolean pass, final String detail) {
			this.pass = pass;
			this.detail = detail;
		}

		private static StepOutcome of(final boolean pass, final String detail) {
			return new StepOutcome(pass, detail);
		}
	}

	private void recordStepFailure(final String stepName, final Exception e) {
		finalReport.put(stepName, false);
		reportDetails.put(stepName, e.getMessage() == null ? e.getClass().getSimpleName()
				: e.getClass().getSimpleName() + ": " + e.getMessage());
	}
}
