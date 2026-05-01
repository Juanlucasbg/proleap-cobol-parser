package io.proleap.saleads.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.Assert.fail;

public class SaleadsMiNegocioFullTest {

	private static final String TEST_NAME = "saleads_mi_negocio_full_test";
	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final int WAIT_SHORT_MS = 2500;
	private static final int WAIT_MEDIUM_MS = 8000;
	private static final int WAIT_LONG_MS = 20000;
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		Assume.assumeTrue(
				"Enable this E2E test with -Dsaleads.e2e.enabled=true or SALEADS_E2E_ENABLED=true",
				readBooleanConfig("saleads.e2e.enabled", "SALEADS_E2E_ENABLED"));

		final String loginUrl = readConfig("saleads.loginUrl", "SALEADS_LOGIN_URL").orElse("");
		final String cdpUrl = readConfig("saleads.cdpUrl", "SALEADS_CDP_URL").orElse("");

		Assume.assumeTrue(
				"Provide SALEADS_LOGIN_URL (any environment login page) or SALEADS_CDP_URL (already opened browser context).",
				!loginUrl.isBlank() || !cdpUrl.isBlank());

		final boolean headless = readBooleanConfig("saleads.headless", "SALEADS_HEADLESS", true);
		final Path evidenceDir = createEvidenceDirectory();

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

		final Map<String, String> details = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			Browser browser;
			if (!cdpUrl.isBlank()) {
				browser = playwright.chromium().connectOverCDP(cdpUrl);
			} else {
				browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			}

			try {
				final BrowserContext context = browser.contexts().isEmpty() ? browser.newContext() : browser.contexts().get(0);
				final Page appPage = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
				appPage.setDefaultTimeout(WAIT_LONG_MS);

				if (!loginUrl.isBlank()) {
					appPage.navigate(loginUrl);
				}
				waitForUiAfterAction(appPage);

				// Step 1: Login with Google.
				handleGoogleLogin(appPage, context);
				waitForUiAfterAction(appPage);
				final boolean appInterfaceVisible = isAnyTextVisible(appPage, "Negocio", "Mi Negocio", "Dashboard", "Inicio");
				final boolean sidebarVisible = isAnyTextVisible(appPage, "Negocio", "Mi Negocio");
				report.put("Login", appInterfaceVisible && sidebarVisible);
				details.put("Login", "appInterfaceVisible=" + appInterfaceVisible + ", sidebarVisible=" + sidebarVisible);
				takeScreenshot(appPage, evidenceDir.resolve("step1_dashboard_loaded.png"), false);

				// Step 2: Open Mi Negocio menu.
				clickIfVisibleAndWait(appPage, "Negocio");
				clickVisibleTextAndWait(appPage, "Mi Negocio");
				final boolean menuExpanded = isAnyTextVisible(appPage, "Agregar Negocio", "Administrar Negocios");
				final boolean agregarVisible = isTextVisible(appPage, "Agregar Negocio");
				final boolean administrarVisible = isTextVisible(appPage, "Administrar Negocios");
				report.put("Mi Negocio menu", menuExpanded && agregarVisible && administrarVisible);
				details.put("Mi Negocio menu", "menuExpanded=" + menuExpanded + ", agregarVisible=" + agregarVisible
						+ ", administrarVisible=" + administrarVisible);
				takeScreenshot(appPage, evidenceDir.resolve("step2_mi_negocio_expanded.png"), false);

				// Step 3: Validate Agregar Negocio modal.
				clickVisibleTextAndWait(appPage, "Agregar Negocio");
				final boolean modalTitle = waitForTextVisible(appPage, "Crear Nuevo Negocio", WAIT_MEDIUM_MS);
				final boolean nombreField = hasVisibleLocator(appPage,
						"input[placeholder*='Nombre del Negocio']",
						"input[name*='nombre']",
						"input[id*='nombre']");
				final boolean limitText = isTextVisible(appPage, "Tienes 2 de 3 negocios");
				final boolean cancelButton = isTextVisible(appPage, "Cancelar");
				final boolean createButton = isTextVisible(appPage, "Crear Negocio");
				report.put("Agregar Negocio modal", modalTitle && nombreField && limitText && cancelButton && createButton);
				details.put("Agregar Negocio modal",
						"modalTitle=" + modalTitle + ", nombreField=" + nombreField + ", limitText=" + limitText
								+ ", cancelButton=" + cancelButton + ", createButton=" + createButton);
				takeScreenshot(appPage, evidenceDir.resolve("step3_agregar_negocio_modal.png"), false);

				fillBusinessNameIfVisible(appPage, "Negocio Prueba Automatización");
				clickIfVisibleAndWait(appPage, "Cancelar");

				// Step 4: Open Administrar Negocios.
				if (!isTextVisible(appPage, "Administrar Negocios")) {
					clickIfVisibleAndWait(appPage, "Mi Negocio");
				}
				clickVisibleTextAndWait(appPage, "Administrar Negocios");
				waitForUiAfterAction(appPage);

				final boolean infoGeneral = isTextVisible(appPage, "Información General");
				final boolean detallesCuenta = isTextVisible(appPage, "Detalles de la Cuenta");
				final boolean tusNegocios = isTextVisible(appPage, "Tus Negocios");
				final boolean legalSection = isAnyTextVisible(appPage, "Sección Legal", "Legal");
				report.put("Administrar Negocios view", infoGeneral && detallesCuenta && tusNegocios && legalSection);
				details.put("Administrar Negocios view",
						"infoGeneral=" + infoGeneral + ", detallesCuenta=" + detallesCuenta + ", tusNegocios="
								+ tusNegocios + ", legalSection=" + legalSection);
				takeScreenshot(appPage, evidenceDir.resolve("step4_administrar_negocios_full_page.png"), true);

				// Step 5: Validate Información General.
				final String infoGeneralText = extractSectionText(appPage, "Información General");
				final boolean emailVisible = EMAIL_PATTERN.matcher(appPage.locator("body").innerText()).find();
				final boolean userNameVisible = inferNameVisible(infoGeneralText);
				final boolean businessPlanVisible = isTextVisible(appPage, "BUSINESS PLAN");
				final boolean cambiarPlanVisible = isTextVisible(appPage, "Cambiar Plan");
				report.put("Información General",
						userNameVisible && emailVisible && businessPlanVisible && cambiarPlanVisible);
				details.put("Información General",
						"userNameVisible=" + userNameVisible + ", emailVisible=" + emailVisible
								+ ", businessPlanVisible=" + businessPlanVisible + ", cambiarPlanVisible="
								+ cambiarPlanVisible);

				// Step 6: Validate Detalles de la Cuenta.
				final boolean cuentaCreada = isTextVisible(appPage, "Cuenta creada");
				final boolean estadoActivo = isAnyTextVisible(appPage, "Estado activo", "Activo");
				final boolean idiomaSeleccionado = isTextVisible(appPage, "Idioma seleccionado");
				report.put("Detalles de la Cuenta", cuentaCreada && estadoActivo && idiomaSeleccionado);
				details.put("Detalles de la Cuenta",
						"cuentaCreada=" + cuentaCreada + ", estadoActivo=" + estadoActivo + ", idiomaSeleccionado="
								+ idiomaSeleccionado);

				// Step 7: Validate Tus Negocios.
				final String tusNegociosText = extractSectionText(appPage, "Tus Negocios");
				final boolean listVisible = inferBusinessListVisible(tusNegociosText);
				final boolean addBusinessButton = isTextVisible(appPage, "Agregar Negocio");
				final boolean businessQuota = isTextVisible(appPage, "Tienes 2 de 3 negocios");
				report.put("Tus Negocios", listVisible && addBusinessButton && businessQuota);
				details.put("Tus Negocios",
						"listVisible=" + listVisible + ", addBusinessButton=" + addBusinessButton + ", businessQuota="
								+ businessQuota);

				// Step 8: Validate Términos y Condiciones.
				final LegalValidationResult termsResult = validateLegalDocument(
						context,
						appPage,
						"Términos y Condiciones",
						new String[]{"Términos y Condiciones", "Terminos y Condiciones"},
						evidenceDir.resolve("step8_terminos_y_condiciones.png"));
				report.put("Términos y Condiciones", termsResult.validHeading && termsResult.validContent);
				details.put("Términos y Condiciones",
						"validHeading=" + termsResult.validHeading + ", validContent=" + termsResult.validContent
								+ ", finalUrl=" + termsResult.finalUrl);

				// Step 9: Validate Política de Privacidad.
				final LegalValidationResult privacyResult = validateLegalDocument(
						context,
						appPage,
						"Política de Privacidad",
						new String[]{"Política de Privacidad", "Politica de Privacidad"},
						evidenceDir.resolve("step9_politica_de_privacidad.png"));
				report.put("Política de Privacidad", privacyResult.validHeading && privacyResult.validContent);
				details.put("Política de Privacidad",
						"validHeading=" + privacyResult.validHeading + ", validContent=" + privacyResult.validContent
								+ ", finalUrl=" + privacyResult.finalUrl);
			} finally {
				browser.close();
			}
		}

		printFinalReport(report, details, evidenceDir);

		final List<String> failedFields = report.entrySet()
				.stream()
				.filter(entry -> !entry.getValue())
				.map(Map.Entry::getKey)
				.collect(Collectors.toList());

		if (!failedFields.isEmpty()) {
			fail("SaleADS Mi Negocio workflow failed validations: " + failedFields);
		}
	}

	private LegalValidationResult validateLegalDocument(
			final BrowserContext context,
			final Page appPage,
			final String linkText,
			final String[] headingCandidates,
			final Path screenshotPath) {
		final String appUrlBefore = appPage.url();
		final int pageCountBefore = context.pages().size();

		clickVisibleTextAndWait(appPage, linkText);

		Page legalPage = appPage;
		boolean openedNewTab = false;

		for (int i = 0; i < 20; i++) {
			if (context.pages().size() > pageCountBefore) {
				legalPage = context.pages().get(context.pages().size() - 1);
				openedNewTab = true;
				break;
			}
			appPage.waitForTimeout(200);
		}

		waitForUiAfterAction(legalPage);

		boolean headingVisible = false;
		for (final String heading : headingCandidates) {
			if (waitForTextVisible(legalPage, heading, WAIT_MEDIUM_MS)) {
				headingVisible = true;
				break;
			}
		}

		final String bodyText = legalPage.locator("body").innerText();
		final boolean legalContentVisible = bodyText != null && bodyText.trim().length() > 200;

		takeScreenshot(legalPage, screenshotPath, true);

		final String finalUrl = legalPage.url();

		if (openedNewTab) {
			legalPage.close();
			appPage.bringToFront();
			waitForUiAfterAction(appPage);
		} else {
			returnToApplicationPage(appPage, appUrlBefore);
		}

		return new LegalValidationResult(headingVisible, legalContentVisible, finalUrl);
	}

	private void returnToApplicationPage(final Page appPage, final String appUrlBefore) {
		if (appPage.url().equals(appUrlBefore)) {
			return;
		}
		try {
			appPage.goBack(new Page.GoBackOptions().setTimeout(WAIT_MEDIUM_MS));
			waitForUiAfterAction(appPage);
		} catch (final PlaywrightException e) {
			if (appUrlBefore != null && !appUrlBefore.isBlank()) {
				appPage.navigate(appUrlBefore);
				waitForUiAfterAction(appPage);
			}
		}
	}

	private void handleGoogleLogin(final Page appPage, final BrowserContext context) {
		final int pagesBefore = context.pages().size();
		clickVisibleTextAndWait(appPage,
				"Sign in with Google",
				"Iniciar sesión con Google",
				"Iniciar con Google",
				"Continuar con Google",
				"Acceder con Google");

		Page googlePage = appPage;
		for (int i = 0; i < 12; i++) {
			if (context.pages().size() > pagesBefore) {
				googlePage = context.pages().get(context.pages().size() - 1);
				break;
			}
			appPage.waitForTimeout(200);
		}

		if (isTextVisible(googlePage, GOOGLE_ACCOUNT)) {
			clickVisibleTextAndWait(googlePage, GOOGLE_ACCOUNT);
		}

		if (googlePage != appPage) {
			try {
				googlePage.waitForClose(new Page.WaitForCloseOptions().setTimeout(WAIT_LONG_MS));
			} catch (final PlaywrightException ignored) {
				// Some Google flows keep the tab open; continue with app page validation.
			}
			appPage.bringToFront();
		}
	}

	private void fillBusinessNameIfVisible(final Page page, final String value) {
		final List<Locator> candidates = Arrays.asList(
				page.locator("input[placeholder*='Nombre del Negocio']"),
				page.locator("input[placeholder*='Negocio']"),
				page.locator("input[name*='nombre']"),
				page.locator("input[id*='nombre']"));
		for (final Locator locator : candidates) {
			if (locator.count() == 0) {
				continue;
			}
			final Locator first = locator.first();
			try {
				first.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(WAIT_SHORT_MS));
				first.click();
				first.fill(value);
				waitForUiAfterAction(page);
				return;
			} catch (final PlaywrightException ignored) {
				// Try next candidate selector.
			}
		}
	}

	private static boolean hasVisibleLocator(final Page page, final String... cssSelectors) {
		for (final String selector : cssSelectors) {
			final Locator locator = page.locator(selector);
			if (locator.count() == 0) {
				continue;
			}
			try {
				locator.first().waitFor(
						new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(WAIT_SHORT_MS));
				return true;
			} catch (final PlaywrightException ignored) {
				// Try next selector.
			}
		}
		return false;
	}

	private static String extractSectionText(final Page page, final String sectionTitle) {
		final List<Locator> sectionCandidates = Arrays.asList(
				page.locator("section"),
				page.locator("article"),
				page.locator("div"));
		for (final Locator candidate : sectionCandidates) {
			final int count = Math.min(candidate.count(), 100);
			for (int i = 0; i < count; i++) {
				final Locator item = candidate.nth(i);
				final String text;
				try {
					text = item.innerText();
				} catch (final PlaywrightException e) {
					continue;
				}
				if (text == null || text.isBlank()) {
					continue;
				}
				if (text.toLowerCase(Locale.ROOT).contains(sectionTitle.toLowerCase(Locale.ROOT))) {
					return text;
				}
			}
		}
		return "";
	}

	private static boolean inferNameVisible(final String infoGeneralText) {
		if (infoGeneralText == null || infoGeneralText.isBlank()) {
			return false;
		}
		final String[] lines = infoGeneralText.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.isBlank()) {
				continue;
			}
			if (line.equalsIgnoreCase("Información General")
					|| line.equalsIgnoreCase("BUSINESS PLAN")
					|| line.equalsIgnoreCase("Cambiar Plan")) {
				continue;
			}
			if (EMAIL_PATTERN.matcher(line).find()) {
				continue;
			}
			if (line.length() >= 3 && Character.isLetter(line.charAt(0))) {
				return true;
			}
		}
		return false;
	}

	private static boolean inferBusinessListVisible(final String tusNegociosText) {
		if (tusNegociosText == null || tusNegociosText.isBlank()) {
			return false;
		}
		final List<String> lines = Arrays.stream(tusNegociosText.split("\\R"))
				.map(String::trim)
				.filter(s -> !s.isBlank())
				.collect(Collectors.toList());

		int meaningfulLines = 0;
		for (final String line : lines) {
			final String lower = line.toLowerCase(Locale.ROOT);
			if (lower.contains("tus negocios")
					|| lower.contains("agregar negocio")
					|| lower.contains("tienes 2 de 3 negocios")) {
				continue;
			}
			meaningfulLines++;
		}
		return meaningfulLines >= 1;
	}

	private static void waitForUiAfterAction(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(WAIT_MEDIUM_MS));
		} catch (final PlaywrightException ignored) {
			// DOMContentLoaded may already be complete.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(WAIT_MEDIUM_MS));
		} catch (final PlaywrightException ignored) {
			// Some pages keep active connections.
		}
		page.waitForTimeout(400);
	}

	private static void clickVisibleTextAndWait(final Page page, final String... labels) {
		PlaywrightException lastException = null;
		for (final String label : labels) {
			for (final Locator locator : textCandidateLocators(page, label)) {
				try {
					locator.waitFor(
							new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(WAIT_MEDIUM_MS));
					locator.scrollIntoViewIfNeeded();
					locator.click(new Locator.ClickOptions().setTimeout(WAIT_MEDIUM_MS));
					waitForUiAfterAction(page);
					return;
				} catch (final PlaywrightException e) {
					lastException = e;
				}
			}
		}
		throw new AssertionError("Unable to click any visible label: " + Arrays.toString(labels), lastException);
	}

	private static void clickIfVisibleAndWait(final Page page, final String label) {
		for (final Locator locator : textCandidateLocators(page, label)) {
			try {
				locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(WAIT_SHORT_MS));
				locator.click(new Locator.ClickOptions().setTimeout(WAIT_SHORT_MS));
				waitForUiAfterAction(page);
				return;
			} catch (final PlaywrightException ignored) {
				// Try other locator variants.
			}
		}
	}

	private static boolean isTextVisible(final Page page, final String label) {
		return waitForTextVisible(page, label, WAIT_SHORT_MS);
	}

	private static boolean waitForTextVisible(final Page page, final String label, final int timeoutMs) {
		for (final Locator locator : textCandidateLocators(page, label)) {
			try {
				locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
				return true;
			} catch (final PlaywrightException ignored) {
				// Try other locator variants.
			}
		}
		return false;
	}

	private static boolean isAnyTextVisible(final Page page, final String... labels) {
		for (final String label : labels) {
			if (isTextVisible(page, label)) {
				return true;
			}
		}
		return false;
	}

	private static List<Locator> textCandidateLocators(final Page page, final String label) {
		return Arrays.asList(
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(label)).first(),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(label)).first(),
				page.getByText(label, new Page.GetByTextOptions().setExact(true)).first(),
				page.getByText(label, new Page.GetByTextOptions().setExact(false)).first());
	}

	private static void takeScreenshot(final Page page, final Path outputPath, final boolean fullPage) {
		try {
			Files.createDirectories(outputPath.getParent());
			page.screenshot(new Page.ScreenshotOptions().setPath(outputPath).setFullPage(fullPage));
		} catch (final IOException e) {
			throw new RuntimeException("Unable to create screenshot folder for " + outputPath, e);
		}
	}

	private static Path createEvidenceDirectory() {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		final Path path = Paths.get("target", "saleads-evidence", TEST_NAME + "_" + timestamp);
		try {
			Files.createDirectories(path);
		} catch (final IOException e) {
			throw new RuntimeException("Could not create evidence directory: " + path, e);
		}
		return path;
	}

	private static Optional<String> readConfig(final String systemProperty, final String envVar) {
		final String fromProperty = System.getProperty(systemProperty);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return Optional.of(fromProperty.trim());
		}
		final String fromEnv = System.getenv(envVar);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return Optional.of(fromEnv.trim());
		}
		return Optional.empty();
	}

	private static boolean readBooleanConfig(final String systemProperty, final String envVar) {
		return readBooleanConfig(systemProperty, envVar, false);
	}

	private static boolean readBooleanConfig(final String systemProperty, final String envVar, final boolean defaultValue) {
		final Optional<String> value = readConfig(systemProperty, envVar);
		return value.map(v -> "true".equalsIgnoreCase(v) || "1".equals(v)).orElse(defaultValue);
	}

	private static void printFinalReport(
			final Map<String, Boolean> report,
			final Map<String, String> details,
			final Path evidenceDir) {
		System.out.println("===== SaleADS Mi Negocio Final Report =====");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue() ? "PASS" : "FAIL"));
			final String detail = details.get(entry.getKey());
			if (detail != null && !detail.isBlank()) {
				System.out.println("  details: " + detail);
			}
		}
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
		System.out.println("===========================================");
	}

	private static final class LegalValidationResult {
		private final boolean validHeading;
		private final boolean validContent;
		private final String finalUrl;

		private LegalValidationResult(final boolean validHeading, final boolean validContent, final String finalUrl) {
			this.validHeading = validHeading;
			this.validContent = validContent;
			this.finalUrl = finalUrl;
		}
	}

}
