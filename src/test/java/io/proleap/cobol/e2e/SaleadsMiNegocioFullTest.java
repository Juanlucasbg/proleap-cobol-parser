package io.proleap.cobol.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullTest {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final String DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	@Test
	public void saleadsMiNegocioWorkflow() throws IOException {
		final boolean enabled = Boolean.parseBoolean(readSetting("saleads.e2e.enabled", "SALEADS_E2E_ENABLED", "false"));
		Assume.assumeTrue("Enable with -Dsaleads.e2e.enabled=true or SALEADS_E2E_ENABLED=true", enabled);

		final String loginUrl = readSetting("saleads.login.url", "SALEADS_LOGIN_URL", "").trim();
		Assume.assumeTrue("Provide login URL via -Dsaleads.login.url or SALEADS_LOGIN_URL", !loginUrl.isEmpty());

		final boolean headless = Boolean.parseBoolean(readSetting("saleads.headless", "SALEADS_HEADLESS", "true"));
		final double slowMo = Double.parseDouble(readSetting("saleads.slowmo.ms", "SALEADS_SLOWMO_MS", "0"));
		final String googleEmail = readSetting("saleads.google.email", "SALEADS_GOOGLE_EMAIL", DEFAULT_GOOGLE_EMAIL);
		final Path evidenceDir = createEvidenceDir();

		final LinkedHashMap<String, Boolean> report = new LinkedHashMap<>();
		final LinkedHashMap<String, String> failures = new LinkedHashMap<>();
		final LinkedHashMap<String, String> urls = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions().setHeadless(headless).setSlowMo(slowMo);
			try (Browser browser = playwright.chromium().launch(launchOptions);
			     BrowserContext context = browser.newContext();
			     Page page = context.newPage()) {
				page.setDefaultTimeout(30_000);

				page.navigate(loginUrl);
				waitForUi(page);

				report.put("Login", runStep("Login", failures, () -> stepLogin(page, context, googleEmail, evidenceDir)));
				report.put("Mi Negocio menu", runStep("Mi Negocio menu", failures, () -> stepOpenMiNegocioMenu(page, evidenceDir)));
				report.put("Agregar Negocio modal", runStep("Agregar Negocio modal", failures, () -> stepValidateAgregarNegocioModal(page, evidenceDir)));
				report.put("Administrar Negocios view", runStep("Administrar Negocios view", failures, () -> stepOpenAdministrarNegocios(page, evidenceDir)));
				report.put("Información General", runStep("Información General", failures, () -> stepValidateInformacionGeneral(page)));
				report.put("Detalles de la Cuenta", runStep("Detalles de la Cuenta", failures, () -> stepValidateDetallesDeLaCuenta(page)));
				report.put("Tus Negocios", runStep("Tus Negocios", failures, () -> stepValidateTusNegocios(page)));
				report.put("Términos y Condiciones", runStep("Términos y Condiciones", failures,
					() -> stepValidateLegalLink(page, context, "Términos y Condiciones", "terms-and-conditions", urls, evidenceDir)));
				report.put("Política de Privacidad", runStep("Política de Privacidad", failures,
					() -> stepValidateLegalLink(page, context, "Política de Privacidad", "privacy-policy", urls, evidenceDir)));
			}
		}

		final String summary = buildSummary(report, failures, urls, evidenceDir);
		Files.writeString(evidenceDir.resolve("final-report.txt"), summary, StandardCharsets.UTF_8);

		final List<String> failedSteps = new ArrayList<>();
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			if (!entry.getValue()) {
				failedSteps.add(entry.getKey());
			}
		}

		if (!failedSteps.isEmpty()) {
			Assert.fail("One or more workflow validations failed: " + String.join(", ", failedSteps) + "\n\n" + summary);
		}
	}

	private boolean stepLogin(final Page page, final BrowserContext context, final String googleEmail, final Path evidenceDir) {
		final boolean dashboardAlreadyVisible = hasVisible(page, "aside", "nav", "text=Negocio");
		if (!dashboardAlreadyVisible) {
			final Locator signInButton = firstVisible(page,
				"button:has-text('Sign in with Google')",
				"button:has-text('Iniciar sesión con Google')",
				"button:has-text('Iniciar sesion con Google')",
				"button:has-text('Continuar con Google')",
				"a:has-text('Google')",
				"text=/Sign in with Google|Iniciar sesi[oó]n con Google|Continuar con Google/i");

			if (signInButton == null) {
				return false;
			}

			final int pagesBefore = context.pages().size();
			signInButton.click();
			waitForUi(page);

			Page authPage = detectNewPage(context, pagesBefore, page);
			trySelectGoogleAccount(authPage, googleEmail);
			waitForUi(page);

			if (authPage != page && !isClosed(authPage)) {
				authPage.bringToFront();
				waitForUi(authPage);
				trySelectGoogleAccount(authPage, googleEmail);
			}
		}

		final boolean mainInterfaceVisible = hasVisible(page, "aside", "nav", "text=Negocio", "text=Mi Negocio");
		final boolean sidebarVisible = hasVisible(page, "aside", "text=Negocio", "text=Mi Negocio");
		screenshot(page, evidenceDir, "01-dashboard-loaded.png", false);
		return mainInterfaceVisible && sidebarVisible;
	}

	private boolean stepOpenMiNegocioMenu(final Page page, final Path evidenceDir) {
		final Locator miNegocio = firstVisible(page, "text=Mi Negocio", ":text-is('Mi Negocio')");
		if (miNegocio == null) {
			return false;
		}

		miNegocio.click();
		waitForUi(page);

		final boolean submenuExpanded = hasVisible(page, "text=Agregar Negocio") && hasVisible(page, "text=Administrar Negocios");
		screenshot(page, evidenceDir, "02-mi-negocio-menu-expanded.png", false);
		return submenuExpanded;
	}

	private boolean stepValidateAgregarNegocioModal(final Page page, final Path evidenceDir) {
		final Locator agregarNegocio = firstVisible(page, "text=Agregar Negocio", "button:has-text('Agregar Negocio')");
		if (agregarNegocio == null) {
			return false;
		}
		agregarNegocio.click();
		waitForUi(page);

		final boolean titleVisible = hasVisible(page, "text=Crear Nuevo Negocio");
		final boolean inputVisible = hasVisible(page,
			"label:has-text('Nombre del Negocio')",
			"input[placeholder*='Nombre del Negocio']",
			"input[name*='nombre']");
		final boolean quotaVisible = hasVisible(page, "text=Tienes 2 de 3 negocios");
		final boolean buttonsVisible = hasVisible(page, "button:has-text('Cancelar')") && hasVisible(page, "button:has-text('Crear Negocio')");

		if (inputVisible) {
			final Locator input = firstVisible(page,
				"input[placeholder*='Nombre del Negocio']",
				"input[name*='nombre']",
				"input");
			if (input != null) {
				input.click();
				input.fill("Negocio Prueba Automatización");
			}
		}

		screenshot(page, evidenceDir, "03-agregar-negocio-modal.png", false);

		final Locator cancelar = firstVisible(page, "button:has-text('Cancelar')", "text=Cancelar");
		if (cancelar != null) {
			cancelar.click();
			waitForUi(page);
		}

		return titleVisible && inputVisible && quotaVisible && buttonsVisible;
	}

	private boolean stepOpenAdministrarNegocios(final Page page, final Path evidenceDir) {
		if (!hasVisible(page, "text=Administrar Negocios")) {
			final Locator miNegocio = firstVisible(page, "text=Mi Negocio", ":text-is('Mi Negocio')");
			if (miNegocio != null) {
				miNegocio.click();
				waitForUi(page);
			}
		}

		final Locator administrar = firstVisible(page, "text=Administrar Negocios");
		if (administrar == null) {
			return false;
		}
		administrar.click();
		waitForUi(page);

		final boolean informacionGeneral = hasVisible(page, "text=Información General");
		final boolean detallesCuenta = hasVisible(page, "text=Detalles de la Cuenta");
		final boolean tusNegocios = hasVisible(page, "text=Tus Negocios");
		final boolean seccionLegal = hasVisible(page, "text=Sección Legal");

		screenshot(page, evidenceDir, "04-administrar-negocios-full.png", true);
		return informacionGeneral && detallesCuenta && tusNegocios && seccionLegal;
	}

	private boolean stepValidateInformacionGeneral(final Page page) {
		final boolean sectionVisible = hasVisible(page, "text=Información General");
		final String infoText = sectionText(page, "Información General");
		final boolean userEmailVisible = EMAIL_PATTERN.matcher(infoText).find() || EMAIL_PATTERN.matcher(page.locator("body").innerText()).find();
		final boolean userNameVisible = containsAny(page, "text=Nombre", "text=Usuario", "text=Name");
		final boolean businessPlanVisible = hasVisible(page, "text=BUSINESS PLAN");
		final boolean cambiarPlanVisible = hasVisible(page, "button:has-text('Cambiar Plan')", "text=Cambiar Plan");
		return sectionVisible && userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible;
	}

	private boolean stepValidateDetallesDeLaCuenta(final Page page) {
		return hasVisible(page, "text=Cuenta creada")
			&& hasVisible(page, "text=Estado activo")
			&& hasVisible(page, "text=Idioma seleccionado");
	}

	private boolean stepValidateTusNegocios(final Page page) {
		final boolean headingVisible = hasVisible(page, "text=Tus Negocios");
		final boolean addButtonVisible = hasVisible(page, "button:has-text('Agregar Negocio')", "text=Agregar Negocio");
		final boolean quotaVisible = hasVisible(page, "text=Tienes 2 de 3 negocios");
		final String negociosText = sectionText(page, "Tus Negocios");
		final boolean listVisible = negociosText.length() > 80 && negociosText.toLowerCase().contains("negocio");
		return headingVisible && listVisible && addButtonVisible && quotaVisible;
	}

	private boolean stepValidateLegalLink(final Page appPage, final BrowserContext context, final String linkText,
	                                      final String screenshotName, final Map<String, String> urls, final Path evidenceDir) {
		final Locator legalLink = firstVisible(appPage,
			"a:has-text('" + linkText + "')",
			"button:has-text('" + linkText + "')",
			"text=" + linkText);
		if (legalLink == null) {
			return false;
		}

		final int pagesBefore = context.pages().size();
		legalLink.click();
		waitForUi(appPage);

		final Page legalPage = detectNewPage(context, pagesBefore, appPage);
		waitForUi(legalPage);

		final boolean headingVisible = hasVisible(legalPage, "text=" + linkText, "h1:has-text('" + linkText + "')");
		final String bodyText = legalPage.locator("body").innerText();
		final boolean legalContentVisible = bodyText != null && bodyText.trim().length() > 120;
		urls.put(linkText, legalPage.url());
		screenshot(legalPage, evidenceDir, "0" + ("Términos y Condiciones".equals(linkText) ? "8" : "9") + "-" + screenshotName + ".png", true);

		if (legalPage != appPage) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			appPage.goBack();
			waitForUi(appPage);
		}

		return headingVisible && legalContentVisible;
	}

	private boolean runStep(final String name, final Map<String, String> failures, final Step step) {
		try {
			return step.run();
		} catch (Throwable throwable) {
			failures.put(name, throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
			return false;
		}
	}

	private static String readSetting(final String systemProperty, final String envVariable, final String fallback) {
		final String fromProperty = System.getProperty(systemProperty);
		if (fromProperty != null && !fromProperty.trim().isEmpty()) {
			return fromProperty.trim();
		}
		final String fromEnv = System.getenv(envVariable);
		if (fromEnv != null && !fromEnv.trim().isEmpty()) {
			return fromEnv.trim();
		}
		return fallback;
	}

	private static Path createEvidenceDir() throws IOException {
		final String configured = readSetting("saleads.e2e.output.dir", "SALEADS_E2E_OUTPUT_DIR", "");
		final Path baseDir;
		if (!configured.isEmpty()) {
			baseDir = Path.of(configured);
		} else {
			baseDir = Path.of("target", "e2e-artifacts");
		}
		final String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()).replace(":", "-");
		final Path dir = baseDir.resolve("saleads_mi_negocio_full_test_" + timestamp);
		Files.createDirectories(dir);
		return dir;
	}

	private static void screenshot(final Page page, final Path evidenceDir, final String fileName, final boolean fullPage) {
		try {
			page.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(fileName)).setFullPage(fullPage));
		} catch (PlaywrightException ignored) {
			// Continue test execution even when screenshot capture fails.
		}
	}

	private static void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		} catch (PlaywrightException ignored) {
			// The page may already be loaded.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE);
		} catch (PlaywrightException ignored) {
			// Some SPAs keep network connections open; ignore timeout.
		}
		try {
			page.waitForTimeout(1_000);
		} catch (PlaywrightException ignored) {
			// Ignore temporary page lifecycle issues.
		}
	}

	private static Locator firstVisible(final Page page, final String... selectors) {
		for (int attempt = 0; attempt < 12; attempt++) {
			for (String selector : selectors) {
				try {
					final Locator locator = page.locator(selector).first();
					if (locator.count() > 0 && locator.isVisible()) {
						return locator;
					}
				} catch (PlaywrightException ignored) {
					// Move to next selector.
				}
			}
			page.waitForTimeout(500);
		}
		return null;
	}

	private static boolean hasVisible(final Page page, final String... selectors) {
		return firstVisible(page, selectors) != null;
	}

	private static boolean containsAny(final Page page, final String... selectors) {
		for (String selector : selectors) {
			try {
				if (page.locator(selector).count() > 0) {
					return true;
				}
			} catch (PlaywrightException ignored) {
				// Continue searching selectors.
			}
		}
		return false;
	}

	private static void trySelectGoogleAccount(final Page page, final String googleEmail) {
		try {
			final Locator emailOption = firstVisible(page,
				"text=" + googleEmail,
				"div:has-text('" + googleEmail + "')",
				"li:has-text('" + googleEmail + "')");
			if (emailOption != null) {
				emailOption.click();
				waitForUi(page);
			}
		} catch (PlaywrightException ignored) {
			// Account chooser not displayed in this run.
		}
	}

	private static Page detectNewPage(final BrowserContext context, final int beforePages, final Page fallback) {
		for (int i = 0; i < 20; i++) {
			final List<Page> pages = context.pages();
			if (pages.size() > beforePages) {
				return pages.get(pages.size() - 1);
			}
			fallback.waitForTimeout(250);
		}
		return fallback;
	}

	private static boolean isClosed(final Page page) {
		try {
			page.url();
			return false;
		} catch (PlaywrightException ex) {
			return true;
		}
	}

	private static String sectionText(final Page page, final String sectionHeading) {
		final Locator section = page.locator("section:has-text('" + sectionHeading + "'), div:has-text('" + sectionHeading + "')").first();
		try {
			if (section.count() > 0) {
				return section.innerText();
			}
		} catch (PlaywrightException ignored) {
			// Fall through to page text.
		}
		return page.locator("body").innerText();
	}

	private static String buildSummary(final LinkedHashMap<String, Boolean> report, final Map<String, String> failures,
	                                   final Map<String, String> urls, final Path evidenceDir) {
		final StringBuilder builder = new StringBuilder();
		builder.append("saleads_mi_negocio_full_test\n");
		builder.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append('\n');
		builder.append('\n');
		builder.append("Final report:\n");
		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL").append('\n');
			if (!entry.getValue() && failures.containsKey(entry.getKey())) {
				builder.append("  reason: ").append(failures.get(entry.getKey())).append('\n');
			}
		}

		if (!urls.isEmpty()) {
			builder.append('\n');
			builder.append("Final legal URLs:\n");
			for (Map.Entry<String, String> entry : urls.entrySet()) {
				builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
			}
		}

		return builder.toString();
	}

	@FunctionalInterface
	private interface Step {
		boolean run();
	}
}
