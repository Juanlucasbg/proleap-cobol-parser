package io.proleap.saleads.e2e;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final Pattern LOGIN_WITH_GOOGLE_PATTERN = Pattern.compile(
			"(?i)(sign\\s*in\\s*with\\s*google|inicia(r)?\\s+sesi[oó]n\\s+con\\s+google|continuar\\s+con\\s+google|google)");
	private static final Pattern NEGOCIO_PATTERN = Pattern.compile("(?i)^\\s*negocio\\s*$");
	private static final Pattern MI_NEGOCIO_PATTERN = Pattern.compile("(?i)^\\s*mi\\s+negocio\\s*$");
	private static final Pattern AGREGAR_NEGOCIO_PATTERN = Pattern.compile("(?i)^\\s*agregar\\s+negocio\\s*$");
	private static final Pattern ADMINISTRAR_NEGOCIOS_PATTERN = Pattern.compile("(?i)^\\s*administrar\\s+negocios\\s*$");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)\\b[\\w.%+-]+@[\\w.-]+\\.[a-z]{2,}\\b");

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final LinkedHashMap<String, Boolean> report = new LinkedHashMap<>();
		report.put("Login", Boolean.FALSE);
		report.put("Mi Negocio menu", Boolean.FALSE);
		report.put("Agregar Negocio modal", Boolean.FALSE);
		report.put("Administrar Negocios view", Boolean.FALSE);
		report.put("Información General", Boolean.FALSE);
		report.put("Detalles de la Cuenta", Boolean.FALSE);
		report.put("Tus Negocios", Boolean.FALSE);
		report.put("Términos y Condiciones", Boolean.FALSE);
		report.put("Política de Privacidad", Boolean.FALSE);

		final List<String> failures = new ArrayList<>();
		final LinkedHashMap<String, String> evidenceUrls = new LinkedHashMap<>();

		final Path evidenceDir = createEvidenceDirectory();

		try (Playwright playwright = Playwright.create()) {
			final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "true"));
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 1024));
			final Page appPage = context.newPage();

			final String loginUrlFromProperty = System.getProperty("saleads.login.url");
			final String loginUrlFromEnv = System.getenv("SALEADS_LOGIN_URL");
			final String loginUrl = loginUrlFromProperty != null && !loginUrlFromProperty.isBlank()
					? loginUrlFromProperty.trim()
					: (loginUrlFromEnv != null && !loginUrlFromEnv.isBlank() ? loginUrlFromEnv.trim() : null);
			if (loginUrl != null) {
				appPage.navigate(loginUrl);
				waitForUi(appPage);
			}

			runStep("Login", report, failures, () -> {
				loginWithGoogle(context, appPage);
				final Locator sidebar = firstVisible("left sidebar navigation", appPage,
						appPage.locator("aside"),
						appPage.getByRole(AriaRole.NAVIGATION),
						appPage.getByText(NEGOCIO_PATTERN));
				Assert.assertTrue("Left sidebar navigation should be visible after login.", isVisible(sidebar, 5000));
				screenshot(appPage, evidenceDir, "01-dashboard-loaded", true);
			});

			runStep("Mi Negocio menu", report, failures, () -> {
				openMiNegocioMenu(appPage);
				Assert.assertTrue("Agregar Negocio should be visible in Mi Negocio submenu.",
						isVisible(firstVisible("Agregar Negocio submenu option", appPage,
								appPage.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)),
								appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)),
								appPage.getByText(AGREGAR_NEGOCIO_PATTERN)), 5000));
				Assert.assertTrue("Administrar Negocios should be visible in Mi Negocio submenu.",
						isVisible(firstVisible("Administrar Negocios submenu option", appPage,
								appPage.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(ADMINISTRAR_NEGOCIOS_PATTERN)),
								appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(ADMINISTRAR_NEGOCIOS_PATTERN)),
								appPage.getByText(ADMINISTRAR_NEGOCIOS_PATTERN)), 5000));
				screenshot(appPage, evidenceDir, "02-mi-negocio-menu-expanded", false);
			});

			runStep("Agregar Negocio modal", report, failures, () -> {
				final Locator agregarNegocio = firstVisible("Agregar Negocio", appPage,
						appPage.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)),
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)),
						appPage.getByText(AGREGAR_NEGOCIO_PATTERN));
				clickAndWaitForUi(appPage, agregarNegocio);

				Assert.assertTrue("Modal title 'Crear Nuevo Negocio' should be visible.",
						isVisible(firstVisible("Crear Nuevo Negocio title", appPage,
								appPage.getByRole(AriaRole.HEADING,
										new Page.GetByRoleOptions().setName(Pattern.compile("(?i)crear\\s+nuevo\\s+negocio"))),
								appPage.getByText(Pattern.compile("(?i)crear\\s+nuevo\\s+negocio"))), 5000));

				final Locator nombreNegocioInput = firstVisible("Nombre del Negocio input", appPage,
						appPage.getByLabel(Pattern.compile("(?i)nombre\\s+del\\s+negocio")),
						appPage.getByPlaceholder(Pattern.compile("(?i)nombre\\s+del\\s+negocio")),
						appPage.locator("input[name*='nombre'], input[id*='nombre']"));
				Assert.assertTrue("Input field 'Nombre del Negocio' should be visible.",
						isVisible(nombreNegocioInput, 5000));
				Assert.assertTrue("Text 'Tienes 2 de 3 negocios' should be visible.",
						isVisible(firstVisible("Tienes 2 de 3 negocios text", appPage,
								appPage.getByText(Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios"))), 5000));
				Assert.assertTrue("Button 'Cancelar' should be visible.",
						isVisible(firstVisible("Cancelar button", appPage,
								appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")),
								appPage.getByText(Pattern.compile("(?i)^\\s*cancelar\\s*$"))), 5000));
				Assert.assertTrue("Button 'Crear Negocio' should be visible.",
						isVisible(firstVisible("Crear Negocio button", appPage,
								appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)crear\\s+negocio"))),
								appPage.getByText(Pattern.compile("(?i)^\\s*crear\\s+negocio\\s*$"))), 5000));

				screenshot(appPage, evidenceDir, "03-agregar-negocio-modal", false);

				nombreNegocioInput.click();
				waitForUi(appPage);
				nombreNegocioInput.fill("Negocio Prueba Automatización");
				waitForUi(appPage);
				clickAndWaitForUi(appPage, firstVisible("Cancelar button", appPage,
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")),
						appPage.getByText(Pattern.compile("(?i)^\\s*cancelar\\s*$"))));
			});

			runStep("Administrar Negocios view", report, failures, () -> {
				ensureMiNegocioExpanded(appPage);
				clickAndWaitForUi(appPage, firstVisible("Administrar Negocios", appPage,
						appPage.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(ADMINISTRAR_NEGOCIOS_PATTERN)),
						appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(ADMINISTRAR_NEGOCIOS_PATTERN)),
						appPage.getByText(ADMINISTRAR_NEGOCIOS_PATTERN)));

				Assert.assertTrue("Section 'Información General' should be visible.", isVisible(
						firstVisible("Información General", appPage,
								appPage.getByRole(AriaRole.HEADING,
										new Page.GetByRoleOptions().setName(Pattern.compile("(?i)informaci[oó]n\\s+general"))),
								appPage.getByText(Pattern.compile("(?i)informaci[oó]n\\s+general"))),
						5000));
				Assert.assertTrue("Section 'Detalles de la Cuenta' should be visible.", isVisible(
						firstVisible("Detalles de la Cuenta", appPage,
								appPage.getByRole(AriaRole.HEADING,
										new Page.GetByRoleOptions().setName(Pattern.compile("(?i)detalles\\s+de\\s+la\\s+cuenta"))),
								appPage.getByText(Pattern.compile("(?i)detalles\\s+de\\s+la\\s+cuenta"))),
						5000));
				Assert.assertTrue("Section 'Tus Negocios' should be visible.", isVisible(
						firstVisible("Tus Negocios", appPage,
								appPage.getByRole(AriaRole.HEADING,
										new Page.GetByRoleOptions().setName(Pattern.compile("(?i)tus\\s+negocios"))),
								appPage.getByText(Pattern.compile("(?i)tus\\s+negocios"))),
						5000));
				Assert.assertTrue("Section 'Sección Legal' should be visible.", isVisible(
						firstVisible("Sección Legal", appPage,
								appPage.getByRole(AriaRole.HEADING,
										new Page.GetByRoleOptions().setName(Pattern.compile("(?i)secci[oó]n\\s+legal"))),
								appPage.getByText(Pattern.compile("(?i)secci[oó]n\\s+legal"))),
						5000));
				screenshot(appPage, evidenceDir, "04-administrar-negocios-view", true);
			});

			runStep("Información General", report, failures, () -> {
				final Locator infoSection = sectionContainer(appPage, Pattern.compile("(?i)informaci[oó]n\\s+general"));
				final String infoText = infoSection.innerText();
				Assert.assertTrue("User email should be visible in Información General.",
						EMAIL_PATTERN.matcher(infoText).find() || infoText.contains(GOOGLE_ACCOUNT_EMAIL));
				Assert.assertTrue("User name should be visible in Información General.",
						hasLikelyUserName(infoText));
				Assert.assertTrue("Text 'BUSINESS PLAN' should be visible.",
						infoText.toUpperCase().contains("BUSINESS PLAN")
								|| isVisible(firstVisible("BUSINESS PLAN text", appPage,
										appPage.getByText(Pattern.compile("(?i)business\\s+plan"))), 5000));
				Assert.assertTrue("Button 'Cambiar Plan' should be visible.",
						isVisible(firstVisible("Cambiar Plan button", appPage,
								appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)cambiar\\s+plan"))),
								appPage.getByText(Pattern.compile("(?i)cambiar\\s+plan"))), 5000));
			});

			runStep("Detalles de la Cuenta", report, failures, () -> {
				final Locator detailsSection = sectionContainer(appPage, Pattern.compile("(?i)detalles\\s+de\\s+la\\s+cuenta"));
				Assert.assertTrue("'Cuenta creada' should be visible.",
						isVisible(detailsSection.getByText(Pattern.compile("(?i)cuenta\\s+creada")).first(), 5000));
				Assert.assertTrue("'Estado activo' should be visible.",
						isVisible(detailsSection.getByText(Pattern.compile("(?i)estado\\s+activo")).first(), 5000));
				Assert.assertTrue("'Idioma seleccionado' should be visible.",
						isVisible(detailsSection.getByText(Pattern.compile("(?i)idioma\\s+seleccionado")).first(), 5000));
			});

			runStep("Tus Negocios", report, failures, () -> {
				final Locator businessesSection = sectionContainer(appPage, Pattern.compile("(?i)tus\\s+negocios"));
				Assert.assertTrue("Business list should be visible.",
						businessesSection.locator("li, tr, [role='row'], [class*='business'], [class*='negocio']").count() > 0
								|| businessesSection.innerText().toLowerCase().contains("negocio"));
				Assert.assertTrue("Button 'Agregar Negocio' should be visible.",
						isVisible(businessesSection.getByRole(AriaRole.BUTTON,
								new Locator.GetByRoleOptions().setName(AGREGAR_NEGOCIO_PATTERN)).first(), 5000)
								|| isVisible(businessesSection.getByText(AGREGAR_NEGOCIO_PATTERN).first(), 5000));
				Assert.assertTrue("Text 'Tienes 2 de 3 negocios' should be visible.",
						isVisible(businessesSection.getByText(Pattern.compile("(?i)tienes\\s+2\\s+de\\s+3\\s+negocios"))
								.first(), 5000));
			});

			runStep("Términos y Condiciones", report, failures, () -> {
				final String finalUrl = validateLegalDocument(context, appPage, "Términos y Condiciones",
						Pattern.compile("(?i)t[ée]rminos\\s+y\\s+condiciones"),
						"05-terminos-y-condiciones", evidenceDir);
				evidenceUrls.put("Términos y Condiciones URL", finalUrl);
			});

			runStep("Política de Privacidad", report, failures, () -> {
				final String finalUrl = validateLegalDocument(context, appPage, "Política de Privacidad",
						Pattern.compile("(?i)pol[ií]tica\\s+de\\s+privacidad"),
						"06-politica-de-privacidad", evidenceDir);
				evidenceUrls.put("Política de Privacidad URL", finalUrl);
			});

			final String reportText = buildFinalReport(report, failures, evidenceUrls);
			Files.writeString(evidenceDir.resolve("final-report.txt"), reportText, StandardCharsets.UTF_8);
			System.out.println(reportText);

			if (!failures.isEmpty()) {
				Assert.fail("One or more workflow validations failed. See final report in: " + evidenceDir.toAbsolutePath());
			}
		}
	}

	private void loginWithGoogle(final BrowserContext context, final Page appPage) {
		final Locator googleLogin = firstVisible("Google login button", appPage,
				appPage.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(LOGIN_WITH_GOOGLE_PATTERN)),
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(LOGIN_WITH_GOOGLE_PATTERN)),
				appPage.getByText(LOGIN_WITH_GOOGLE_PATTERN));

		final Page googlePage = clickAndCaptureNewPage(context, googleLogin);
		if (googlePage != null) {
			waitForUi(googlePage);
			selectGoogleAccountIfVisible(googlePage, GOOGLE_ACCOUNT_EMAIL);
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			waitForUi(appPage);
			selectGoogleAccountIfVisible(appPage, GOOGLE_ACCOUNT_EMAIL);
			waitForUi(appPage);
		}
	}

	private void openMiNegocioMenu(final Page page) {
		final Locator negocioSection = firstVisible("Negocio section", page,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(NEGOCIO_PATTERN)),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(NEGOCIO_PATTERN)),
				page.getByText(NEGOCIO_PATTERN));
		clickAndWaitForUi(page, negocioSection);

		final Locator miNegocio = firstVisible("Mi Negocio", page,
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(MI_NEGOCIO_PATTERN)),
				page.getByRole(AriaRole.MENUITEM, new Page.GetByRoleOptions().setName(MI_NEGOCIO_PATTERN)),
				page.getByText(MI_NEGOCIO_PATTERN));
		clickAndWaitForUi(page, miNegocio);
	}

	private void ensureMiNegocioExpanded(final Page page) {
		if (isVisible(page.getByText(ADMINISTRAR_NEGOCIOS_PATTERN).first(), 1200)) {
			return;
		}
		if (isVisible(page.getByText(MI_NEGOCIO_PATTERN).first(), 2000)) {
			clickAndWaitForUi(page, page.getByText(MI_NEGOCIO_PATTERN).first());
		}
		if (!isVisible(page.getByText(ADMINISTRAR_NEGOCIOS_PATTERN).first(), 2000)) {
			openMiNegocioMenu(page);
		}
	}

	private String validateLegalDocument(final BrowserContext context, final Page appPage, final String linkText,
			final Pattern headingPattern, final String screenshotName, final Path evidenceDir) {
		final Locator link = firstVisible(linkText + " link", appPage,
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(Pattern.compile("(?i)" + Pattern.quote(linkText)))),
				appPage.getByText(Pattern.compile("(?i)" + Pattern.quote(linkText))));

		final String applicationUrlBefore = appPage.url();
		final Page legalPage = clickAndCaptureNewPage(context, link);

		final Page pageToValidate = legalPage != null ? legalPage : appPage;
		waitForUi(pageToValidate);

		Assert.assertTrue("Heading '" + linkText + "' should be visible.",
				isVisible(firstVisible(linkText + " heading", pageToValidate,
						pageToValidate.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(headingPattern)),
						pageToValidate.getByText(headingPattern)), 5000));
		Assert.assertTrue("Legal content text should be visible for " + linkText + ".",
				pageToValidate.locator("p, article, section, main").count() > 0
						|| pageToValidate.innerText("body").trim().length() > 80);

		screenshot(pageToValidate, evidenceDir, screenshotName, true);
		final String finalUrl = pageToValidate.url();

		if (legalPage != null) {
			appPage.bringToFront();
			waitForUi(appPage);
		} else if (!applicationUrlBefore.equals(appPage.url())) {
			appPage.goBack();
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private Locator sectionContainer(final Page page, final Pattern headingPattern) {
		final Locator heading = firstVisible("section heading " + headingPattern, page,
				page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(headingPattern)),
				page.getByText(headingPattern));
		final Locator parentSection = heading.locator("xpath=ancestor::section[1]");
		if (isVisible(parentSection.first(), 1000)) {
			return parentSection.first();
		}
		final Locator parentCard = heading.locator("xpath=ancestor::*[self::div or self::article][1]");
		if (isVisible(parentCard.first(), 1000)) {
			return parentCard.first();
		}
		return heading;
	}

	private boolean hasLikelyUserName(final String sectionText) {
		final String[] lines = sectionText.split("\\R");
		for (final String rawLine : lines) {
			final String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}
			final String lower = line.toLowerCase();
			if (lower.contains("información general") || lower.contains("informacion general")
					|| lower.contains("business plan") || lower.contains("cambiar plan")
					|| EMAIL_PATTERN.matcher(line).find() || lower.contains("plan")) {
				continue;
			}
			if (line.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*") && line.length() >= 3) {
				return true;
			}
		}
		return false;
	}

	private void runStep(final String stepName, final Map<String, Boolean> report, final List<String> failures,
			final StepAction stepAction) {
		try {
			stepAction.execute();
			report.put(stepName, Boolean.TRUE);
		} catch (final Throwable throwable) {
			report.put(stepName, Boolean.FALSE);
			final String message = throwable.getMessage() == null ? throwable.getClass().getName() : throwable.getMessage();
			failures.add(stepName + ": " + message);
		}
	}

	private Locator firstVisible(final String description, final Page page, final Locator... candidates) {
		for (final Locator candidate : candidates) {
			final Locator first = candidate.first();
			if (isVisible(first, 3000)) {
				return first;
			}
		}
		throw new AssertionError("Could not find visible element for: " + description + " on URL " + page.url());
	}

	private boolean isVisible(final Locator locator, final int timeoutMs) {
		try {
			locator.waitFor(new Locator.WaitForOptions()
					.setState(WaitForSelectorState.VISIBLE)
					.setTimeout((double) timeoutMs));
			return true;
		} catch (final PlaywrightException ignored) {
			return false;
		}
	}

	private void clickAndWaitForUi(final Page page, final Locator locator) {
		locator.click();
		waitForUi(page);
	}

	private Page clickAndCaptureNewPage(final BrowserContext context, final Locator locator) {
		try {
			return context.waitForPage(locator::click,
					new BrowserContext.WaitForPageOptions().setTimeout(7000));
		} catch (final PlaywrightException ignored) {
			locator.click();
			return null;
		}
	}

	private void selectGoogleAccountIfVisible(final Page page, final String email) {
		final Locator accountOption = page.getByText(Pattern.compile("(?i)" + Pattern.quote(email))).first();
		if (isVisible(accountOption, 5000)) {
			accountOption.click();
			waitForUi(page);
		}
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(7000));
		} catch (final PlaywrightException ignored) {
			try {
				page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(7000));
			} catch (final PlaywrightException ignoredAgain) {
				// no-op: some SPAs do not reliably expose load-state changes after route transitions.
			}
		}
		page.waitForTimeout(500);
	}

	private void screenshot(final Page page, final Path evidenceDir, final String name, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions()
				.setPath(evidenceDir.resolve(name + ".png"))
				.setFullPage(fullPage));
	}

	private Path createEvidenceDirectory() throws IOException {
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path evidenceDir = Paths.get("target", "saleads-evidence", timestamp);
		Files.createDirectories(evidenceDir);
		return evidenceDir;
	}

	private String buildFinalReport(final LinkedHashMap<String, Boolean> report, final List<String> failures,
			final LinkedHashMap<String, String> evidenceUrls) {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio Workflow - Final Report").append(System.lineSeparator());
		builder.append("===========================================").append(System.lineSeparator());
		builder.append(System.lineSeparator());
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ")
					.append(Boolean.TRUE.equals(entry.getValue()) ? "PASS" : "FAIL")
					.append(System.lineSeparator());
		}
		if (!evidenceUrls.isEmpty()) {
			builder.append(System.lineSeparator());
			builder.append("Captured URLs").append(System.lineSeparator());
			builder.append("-------------").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : evidenceUrls.entrySet()) {
				builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue())
						.append(System.lineSeparator());
			}
		}
		if (!failures.isEmpty()) {
			builder.append(System.lineSeparator());
			builder.append("Failures").append(System.lineSeparator());
			builder.append("--------").append(System.lineSeparator());
			for (final String failure : failures) {
				builder.append("- ").append(failure).append(System.lineSeparator());
			}
		}
		return builder.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void execute() throws Exception;
	}
}
