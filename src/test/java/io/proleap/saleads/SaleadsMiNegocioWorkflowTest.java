package io.proleap.saleads;

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
import java.util.regex.Pattern;

public class SaleadsMiNegocioWorkflowTest {

	private static final double SHORT_TIMEOUT_MS = 3_000;
	private static final double DEFAULT_TIMEOUT_MS = 10_000;
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFO_GENERAL = "Información General";
	private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Términos y Condiciones";
	private static final String REPORT_POLITICA = "Política de Privacidad";

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final Map<String, StepResult> report = new LinkedHashMap<>();
		final Path evidenceDir = createEvidenceDirectory();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserTypeOptions().launchOptions());
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setIgnoreHTTPSErrors(true));
			final Page page = context.newPage();

			report.put(REPORT_LOGIN, executeStep(() -> {
				final String loginUrl = resolveLoginUrl();
				if (loginUrl != null) {
					page.navigate(loginUrl);
				}
				waitForUi(page);

				final Locator loginButton = pickVisibleLocator("Google login button",
						page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Google")),
						page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Google")),
						page.getByText("Sign in with Google", new Page.GetByTextOptions().setExact(true)),
						page.getByText("Iniciar sesión con Google", new Page.GetByTextOptions().setExact(true)),
						page.getByText("Continuar con Google", new Page.GetByTextOptions().setExact(true)));

				final int initialPages = context.pages().size();
				clickAndWait(page, loginButton);

				final Page googlePage = waitForNewPage(context, initialPages, 8_000);
				if (googlePage != null) {
					selectGoogleAccountIfVisible(googlePage);
					waitForUi(googlePage);
				} else {
					selectGoogleAccountIfVisible(page);
				}

				final Locator sidebar = pickVisibleLocator("left sidebar navigation",
						page.locator("aside"),
						page.getByRole(AriaRole.NAVIGATION),
						page.locator("[class*='sidebar']"));
				Assert.assertTrue("Left sidebar should be visible after login.", isVisible(sidebar, DEFAULT_TIMEOUT_MS));
				screenshot(page, evidenceDir, "01-dashboard-loaded.png", false);
				return "Dashboard loaded and sidebar visible";
			}));

			report.put(REPORT_MI_NEGOCIO_MENU, executeStep(() -> {
				final Locator negocioOption = pickVisibleLocator("'Negocio' option",
						page.getByText("Negocio", new Page.GetByTextOptions().setExact(true)),
						page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Negocio")),
						page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Negocio")));
				clickAndWait(page, negocioOption);

				final Locator miNegocioOption = pickVisibleLocator("'Mi Negocio' option",
						page.getByText("Mi Negocio", new Page.GetByTextOptions().setExact(true)),
						page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Mi Negocio")),
						page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Mi Negocio")));
				clickAndWait(page, miNegocioOption);

				final Locator agregarNegocio = pickVisibleLocator("'Agregar Negocio' submenu",
						page.getByText("Agregar Negocio", new Page.GetByTextOptions().setExact(true)),
						page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Agregar Negocio")),
						page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Agregar Negocio")));
				final Locator administrarNegocios = pickVisibleLocator("'Administrar Negocios' submenu",
						page.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(true)),
						page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Administrar Negocios")),
						page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Administrar Negocios")));

				Assert.assertTrue("'Agregar Negocio' should be visible.", isVisible(agregarNegocio, DEFAULT_TIMEOUT_MS));
				Assert.assertTrue("'Administrar Negocios' should be visible.", isVisible(administrarNegocios, DEFAULT_TIMEOUT_MS));
				screenshot(page, evidenceDir, "02-mi-negocio-expanded-menu.png", false);
				return "Mi Negocio menu expanded";
			}));

			report.put(REPORT_AGREGAR_NEGOCIO_MODAL, executeStep(() -> {
				final Locator agregarNegocio = pickVisibleLocator("'Agregar Negocio' action",
						page.getByText("Agregar Negocio", new Page.GetByTextOptions().setExact(true)),
						page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Agregar Negocio")),
						page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Agregar Negocio")));
				clickAndWait(page, agregarNegocio);

				assertTextVisible(page, "Crear Nuevo Negocio");
				assertTextVisible(page, "Nombre del Negocio");
				assertTextVisible(page, "Tienes 2 de 3 negocios");
				final Locator cancelButton = pickVisibleLocator("'Cancelar' button",
						page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Cancelar")),
						page.getByText("Cancelar", new Page.GetByTextOptions().setExact(true)));
				final Locator createButton = pickVisibleLocator("'Crear Negocio' button",
						page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Crear Negocio")),
						page.getByText("Crear Negocio", new Page.GetByTextOptions().setExact(true)));

				Assert.assertTrue("'Cancelar' button should be visible.", isVisible(cancelButton, DEFAULT_TIMEOUT_MS));
				Assert.assertTrue("'Crear Negocio' button should be visible.", isVisible(createButton, DEFAULT_TIMEOUT_MS));
				screenshot(page, evidenceDir, "03-agregar-negocio-modal.png", false);

				final Locator nombreInput = pickVisibleLocator("'Nombre del Negocio' input",
						page.getByLabel("Nombre del Negocio"),
						page.getByPlaceholder("Nombre del Negocio"),
						page.locator("input[placeholder*='Negocio']"),
						page.locator("input[name*='negocio']"));
				nombreInput.fill("Negocio Prueba Automatización");
				clickAndWait(page, cancelButton);
				return "Agregar Negocio modal validated";
			}));

			report.put(REPORT_ADMINISTRAR_NEGOCIOS_VIEW, executeStep(() -> {
				ensureMiNegocioExpanded(page);
				final Locator administrarNegocios = pickVisibleLocator("'Administrar Negocios' option",
						page.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(true)),
						page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Administrar Negocios")),
						page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Administrar Negocios")));
				clickAndWait(page, administrarNegocios);

				assertTextVisible(page, "Información General");
				assertTextVisible(page, "Detalles de la Cuenta");
				assertTextVisible(page, "Tus Negocios");
				assertTextVisible(page, "Sección Legal");
				screenshot(page, evidenceDir, "04-administrar-negocios-full.png", true);
				return "Administrar Negocios page loaded";
			}));

			report.put(REPORT_INFO_GENERAL, executeStep(() -> {
				assertTextVisible(page, "Información General");
				assertTextVisible(page, "BUSINESS PLAN");
				assertTextVisible(page, "Cambiar Plan");

				final Locator infoSection = sectionContaining(page, "Información General");
				final String infoText = infoSection.innerText();
				Assert.assertTrue("User email should be visible in Información General.",
						EMAIL_PATTERN.matcher(infoText).find());
				Assert.assertTrue("User name should be visible in Información General.",
						hasLikelyUserName(infoText));
				return "Información General values visible";
			}));

			report.put(REPORT_DETALLES_CUENTA, executeStep(() -> {
				assertTextVisible(page, "Detalles de la Cuenta");
				assertTextVisible(page, "Cuenta creada");
				assertTextVisible(page, "Estado activo");
				assertTextVisible(page, "Idioma seleccionado");
				return "Detalles de la Cuenta visible";
			}));

			report.put(REPORT_TUS_NEGOCIOS, executeStep(() -> {
				assertTextVisible(page, "Tus Negocios");
				assertTextVisible(page, "Agregar Negocio");
				assertTextVisible(page, "Tienes 2 de 3 negocios");
				return "Tus Negocios validated";
			}));

			report.put(REPORT_TERMINOS, executeStep(() -> {
				final String finalUrl = openLegalDocumentAndValidate(context, page, evidenceDir,
						"Términos y Condiciones", "Términos y Condiciones", "05-terminos-y-condiciones.png");
				return "Validated at URL: " + finalUrl;
			}));

			report.put(REPORT_POLITICA, executeStep(() -> {
				final String finalUrl = openLegalDocumentAndValidate(context, page, evidenceDir,
						"Política de Privacidad", "Política de Privacidad", "06-politica-de-privacidad.png");
				return "Validated at URL: " + finalUrl;
			}));
		}

		final String reportText = buildReport(report);
		System.out.println(reportText);
		Assert.assertTrue("One or more workflow validations failed.\n" + reportText, allPassed(report));
	}

	private void ensureMiNegocioExpanded(final Page page) {
		final Locator administrar = page.getByText("Administrar Negocios", new Page.GetByTextOptions().setExact(true));
		if (isVisible(administrar, SHORT_TIMEOUT_MS)) {
			return;
		}

		final List<Locator> expandCandidates = Arrays.asList(
				page.getByText("Mi Negocio", new Page.GetByTextOptions().setExact(true)),
				page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Mi Negocio")),
				page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Mi Negocio")),
				page.getByText("Negocio", new Page.GetByTextOptions().setExact(true)));
		for (final Locator candidate : expandCandidates) {
			if (isVisible(candidate, SHORT_TIMEOUT_MS)) {
				clickAndWait(page, candidate);
				if (isVisible(administrar, SHORT_TIMEOUT_MS)) {
					return;
				}
			}
		}
		Assert.fail("Unable to expand 'Mi Negocio' menu.");
	}

	private String openLegalDocumentAndValidate(final BrowserContext context, final Page appPage, final Path evidenceDir,
			final String linkText, final String headingText, final String screenshotName) {
		final Locator legalLink = pickVisibleLocator("'" + linkText + "' link",
				appPage.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(linkText)),
				appPage.getByText(linkText, new Page.GetByTextOptions().setExact(true)));

		final String appUrlBeforeClick = appPage.url();
		final int initialPages = context.pages().size();
		clickAndWait(appPage, legalLink);

		final Page maybeNewTab = waitForNewPage(context, initialPages, 6_000);
		final Page legalPage = maybeNewTab != null ? maybeNewTab : appPage;
		waitForUi(legalPage);

		assertTextVisible(legalPage, headingText);
		final String legalBody = legalPage.locator("body").innerText();
		Assert.assertTrue("Legal content should be visible for " + headingText + ".", legalBody.trim().length() > 100);
		screenshot(legalPage, evidenceDir, screenshotName, true);
		final String finalUrl = legalPage.url();
		Assert.assertTrue("Final URL should be present for " + headingText + ".", finalUrl != null && !finalUrl.isBlank());

		if (maybeNewTab != null) {
			maybeNewTab.close();
			appPage.bringToFront();
		} else if (!appPage.url().equals(appUrlBeforeClick)) {
			appPage.goBack();
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private void selectGoogleAccountIfVisible(final Page page) {
		final Locator account = page.getByText("juanlucasbarbiergarzon@gmail.com", new Page.GetByTextOptions().setExact(true));
		if (isVisible(account, SHORT_TIMEOUT_MS)) {
			clickAndWait(page, account);
		}
	}

	private String resolveLoginUrl() {
		final String fromProperty = System.getProperty("saleads.login.url");
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty;
		}
		final String fromEnv = System.getenv("SALEADS_LOGIN_URL");
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv;
		}
		return null;
	}

	private StepResult executeStep(final StepAction action) {
		try {
			final String details = action.run();
			return new StepResult(true, details == null ? "OK" : details);
		} catch (final Throwable t) {
			return new StepResult(false, t.getMessage() == null ? t.toString() : t.getMessage());
		}
	}

	private Locator pickVisibleLocator(final String description, final Locator... candidates) {
		for (final Locator candidate : candidates) {
			if (candidate != null && isVisible(candidate, SHORT_TIMEOUT_MS)) {
				return candidate.first();
			}
		}
		Assert.fail("Could not find visible element for: " + description);
		return null;
	}

	private void assertTextVisible(final Page page, final String text) {
		final Locator exact = page.getByText(text, new Page.GetByTextOptions().setExact(true));
		if (isVisible(exact, SHORT_TIMEOUT_MS)) {
			return;
		}
		final Locator partial = page.getByText(text);
		Assert.assertTrue("Expected visible text: " + text, isVisible(partial, DEFAULT_TIMEOUT_MS));
	}

	private Locator sectionContaining(final Page page, final String sectionTitle) {
		final Locator section = page.locator(
				"xpath=//*[self::section or self::div][.//*[contains(normalize-space(), \"" + sectionTitle + "\")]]").first();
		Assert.assertTrue("Section should be visible: " + sectionTitle, isVisible(section, DEFAULT_TIMEOUT_MS));
		return section;
	}

	private boolean hasLikelyUserName(final String text) {
		final List<String> ignored = Arrays.asList(
				"información general",
				"business plan",
				"cambiar plan");

		final String[] lines = text.split("\\R");
		for (final String raw : lines) {
			final String line = raw.trim();
			if (line.isBlank()) {
				continue;
			}
			if (line.contains("@")) {
				continue;
			}
			if (ignored.contains(line.toLowerCase(Locale.ROOT))) {
				continue;
			}
			return true;
		}
		return false;
	}

	private boolean isVisible(final Locator locator, final double timeoutMs) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions()
					.setState(WaitForSelectorState.VISIBLE)
					.setTimeout(timeoutMs));
			return true;
		} catch (final PlaywrightException e) {
			return false;
		}
	}

	private void clickAndWait(final Page page, final Locator locator) {
		locator.first().click(new Locator.ClickOptions().setTimeout(DEFAULT_TIMEOUT_MS));
		waitForUi(page);
	}

	private void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(8_000));
		} catch (final PlaywrightException ignored) {
			// Some pages keep active connections; fall back to a deterministic short wait.
		}
		page.waitForTimeout(700);
	}

	private Page waitForNewPage(final BrowserContext context, final int initialPageCount, final long timeoutMs) {
		final long startedAt = System.currentTimeMillis();
		while (System.currentTimeMillis() - startedAt < timeoutMs) {
			final List<Page> pages = new ArrayList<>(context.pages());
			if (pages.size() > initialPageCount) {
				return pages.get(pages.size() - 1);
			}
			try {
				Thread.sleep(200);
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
				return null;
			}
		}
		return null;
	}

	private void screenshot(final Page page, final Path evidenceDir, final String name, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions()
				.setPath(evidenceDir.resolve(name))
				.setFullPage(fullPage));
	}

	private String buildReport(final Map<String, StepResult> report) {
		final StringBuilder sb = new StringBuilder("SaleADS Mi Negocio Workflow Report\n");
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			sb.append("- ")
					.append(entry.getKey())
					.append(": ")
					.append(entry.getValue().passed ? "PASS" : "FAIL")
					.append(" | ")
					.append(entry.getValue().details)
					.append('\n');
		}
		return sb.toString();
	}

	private boolean allPassed(final Map<String, StepResult> report) {
		for (final StepResult result : report.values()) {
			if (!result.passed) {
				return false;
			}
		}
		return true;
	}

	private Path createEvidenceDirectory() throws IOException {
		final String defaultPath = "target/saleads-evidence/" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final String configuredPath = System.getProperty("saleads.evidence.dir", defaultPath);
		final Path path = Paths.get(configuredPath);
		Files.createDirectories(path);
		return path;
	}

	private interface StepAction {
		String run() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}
	}

	private static final class BrowserTypeOptions {
		private BrowserTypeOptions() {
		}

		private BrowserType.LaunchOptions launchOptions() {
			return new BrowserType.LaunchOptions()
					.setHeadless(Boolean.parseBoolean(System.getProperty("saleads.headless", "true")));
		}
	}
}
