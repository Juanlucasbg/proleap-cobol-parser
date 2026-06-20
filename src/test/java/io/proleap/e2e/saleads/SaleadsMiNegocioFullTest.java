package io.proleap.e2e.saleads;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SaleadsMiNegocioFullTest {

	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final int DEFAULT_TIMEOUT_MS = 30000;

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final String loginUrl = requiredConfig("SALEADS_LOGIN_URL", "saleads.login.url");
		Assume.assumeTrue("SALEADS_LOGIN_URL (or -Dsaleads.login.url) is required.", loginUrl != null && !loginUrl.isBlank());

		final String googleAccount = optionalConfig("SALEADS_GOOGLE_ACCOUNT", "saleads.google.account", DEFAULT_GOOGLE_ACCOUNT);
		final boolean headless = Boolean.parseBoolean(optionalConfig("SALEADS_HEADLESS", "saleads.headless", "true"));
		final Path evidenceDir = createEvidenceDirectory();

		final Map<String, StepResult> report = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1000));
			final Page appPage = context.newPage();
			appPage.setDefaultTimeout(DEFAULT_TIMEOUT_MS);

			recordStep(report, "Login", () -> {
				appPage.navigate(loginUrl);
				appPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
				appPage.waitForLoadState(LoadState.NETWORKIDLE);
				loginWithGoogle(appPage, googleAccount);
				assertVisibleText(appPage, "Negocio");
				takeScreenshot(appPage, evidenceDir.resolve("01-dashboard-loaded.png"), false);
			});

			recordStep(report, "Mi Negocio menu", () -> {
				expandMiNegocioMenu(appPage);
				assertVisibleText(appPage, "Agregar Negocio");
				assertVisibleText(appPage, "Administrar Negocios");
				takeScreenshot(appPage, evidenceDir.resolve("02-mi-negocio-menu-expanded.png"), false);
			});

			recordStep(report, "Agregar Negocio modal", () -> {
				clickByText(appPage, "Agregar Negocio");
				assertVisibleText(appPage, "Crear Nuevo Negocio");
				fillFieldByLabel(appPage, "Nombre del Negocio", "Negocio Prueba Automatización");
				assertVisibleText(appPage, "Tienes 2 de 3 negocios");
				assertVisibleText(appPage, "Cancelar");
				assertVisibleText(appPage, "Crear Negocio");
				takeScreenshot(appPage, evidenceDir.resolve("03-agregar-negocio-modal.png"), false);
				clickByText(appPage, "Cancelar");
				appPage.waitForLoadState(LoadState.NETWORKIDLE);
			});

			recordStep(report, "Administrar Negocios view", () -> {
				expandMiNegocioMenu(appPage);
				clickByText(appPage, "Administrar Negocios");
				appPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
				appPage.waitForLoadState(LoadState.NETWORKIDLE);
				assertVisibleText(appPage, "Información General");
				assertVisibleText(appPage, "Detalles de la Cuenta");
				assertVisibleText(appPage, "Tus Negocios");
				assertVisibleText(appPage, "Sección Legal");
				takeScreenshot(appPage, evidenceDir.resolve("04-administrar-negocios-full-page.png"), true);
			});

			recordStep(report, "Información General", () -> {
				assertAnyVisibleText(appPage, List.of("BUSINESS PLAN", "Business Plan"));
				assertVisibleText(appPage, "Cambiar Plan");
				assertAnyVisibleText(appPage, List.of("@", "correo", "email"));
				assertAnyVisibleText(appPage, List.of("Nombre", "Usuario", "Perfil", "Cuenta"));
			});

			recordStep(report, "Detalles de la Cuenta", () -> {
				assertVisibleText(appPage, "Cuenta creada");
				assertVisibleText(appPage, "Estado activo");
				assertVisibleText(appPage, "Idioma seleccionado");
			});

			recordStep(report, "Tus Negocios", () -> {
				assertVisibleText(appPage, "Tus Negocios");
				assertVisibleText(appPage, "Agregar Negocio");
				assertVisibleText(appPage, "Tienes 2 de 3 negocios");
			});

			recordStep(report, "Términos y Condiciones", () -> {
				final String finalUrl = validateLegalLink(appPage, "Términos y Condiciones", "Términos y Condiciones",
						evidenceDir.resolve("05-terminos-y-condiciones.png"));
				report.put("Términos y Condiciones", StepResult.pass("URL final: " + finalUrl));
			});

			recordStep(report, "Política de Privacidad", () -> {
				final String finalUrl = validateLegalLink(appPage, "Política de Privacidad", "Política de Privacidad",
						evidenceDir.resolve("06-politica-de-privacidad.png"));
				report.put("Política de Privacidad", StepResult.pass("URL final: " + finalUrl));
			});

			writeReport(evidenceDir, report);
			printReport(report, evidenceDir);
			assertAllPassed(report);
		}
	}

	private void loginWithGoogle(final Page appPage, final String googleAccountEmail) {
		final int pagesBeforeClick = appPage.context().pages().size();
		clickAnyByText(appPage, List.of("Sign in with Google", "Ingresar con Google", "Continuar con Google", "Google"));
		appPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		appPage.waitForLoadState(LoadState.NETWORKIDLE);

		final Page googlePage = waitForPotentialNewPage(appPage.context(), pagesBeforeClick);
		final Page authPage = googlePage == null ? appPage : googlePage;

		tryClickText(authPage, googleAccountEmail);
		authPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		authPage.waitForLoadState(LoadState.NETWORKIDLE);

		if (googlePage != null) {
			if (!googlePage.isClosed()) {
				googlePage.close();
			}
			appPage.bringToFront();
		}

		assertAnyVisibleText(appPage, List.of("Negocio", "Dashboard", "Inicio", "Panel"));
	}

	private void expandMiNegocioMenu(final Page appPage) {
		tryClickText(appPage, "Negocio");
		appPage.waitForLoadState(LoadState.NETWORKIDLE);
		clickByText(appPage, "Mi Negocio");
		appPage.waitForLoadState(LoadState.NETWORKIDLE);
	}

	private String validateLegalLink(final Page appPage, final String linkText, final String expectedHeading, final Path screenshotPath) {
		final String originalUrl = appPage.url();
		final int pagesBeforeClick = appPage.context().pages().size();

		clickByText(appPage, linkText);
		appPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
		appPage.waitForLoadState(LoadState.NETWORKIDLE);

		final Page legalPage = waitForPotentialNewPage(appPage.context(), pagesBeforeClick);
		final Page pageToValidate = legalPage == null ? appPage : legalPage;

		assertVisibleText(pageToValidate, expectedHeading);
		assertAnyVisibleText(pageToValidate, List.of("contenido", "legal", "términos", "privacidad", "condiciones"));
		takeScreenshot(pageToValidate, screenshotPath, true);

		final String finalUrl = pageToValidate.url();

		if (legalPage != null) {
			if (!legalPage.isClosed()) {
				legalPage.close();
			}
			appPage.bringToFront();
		} else {
			try {
				appPage.goBack();
				appPage.waitForLoadState(LoadState.NETWORKIDLE);
			} catch (final RuntimeException ignored) {
				appPage.navigate(originalUrl);
				appPage.waitForLoadState(LoadState.NETWORKIDLE);
			}
		}

		return finalUrl;
	}

	private void recordStep(final Map<String, StepResult> report, final String stepName, final CheckedStep step) {
		try {
			step.execute();
			if (!report.containsKey(stepName)) {
				report.put(stepName, StepResult.pass("Validation passed"));
			}
		} catch (final Throwable e) {
			report.put(stepName, StepResult.fail(e.getMessage()));
		}
	}

	private void assertAllPassed(final Map<String, StepResult> report) {
		final StringBuilder failures = new StringBuilder();
		for (final Map.Entry<String, StepResult> step : report.entrySet()) {
			if (!step.getValue().passed) {
				failures.append("- ").append(step.getKey()).append(": ").append(step.getValue().details).append('\n');
			}
		}

		if (failures.length() > 0) {
			Assert.fail("SaleADS Mi Negocio workflow failed:\n" + failures);
		}
	}

	private void printReport(final Map<String, StepResult> report, final Path evidenceDir) {
		System.out.println("SaleADS Mi Negocio workflow report");
		System.out.println("Evidence directory: " + evidenceDir);

		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			System.out.println("- " + entry.getKey() + ": " + (entry.getValue().passed ? "PASS" : "FAIL")
					+ " (" + entry.getValue().details + ")");
		}
	}

	private void writeReport(final Path evidenceDir, final Map<String, StepResult> report) throws IOException {
		final StringBuilder json = new StringBuilder();
		json.append("{\n");
		json.append("  \"workflow\": \"saleads_mi_negocio_full_test\",\n");
		json.append("  \"generatedAt\": \"").append(LocalDateTime.now()).append("\",\n");
		json.append("  \"results\": {\n");

		int index = 0;
		for (final Map.Entry<String, StepResult> entry : report.entrySet()) {
			json.append("    \"").append(escapeJson(entry.getKey())).append("\": {\n");
			json.append("      \"status\": \"").append(entry.getValue().passed ? "PASS" : "FAIL").append("\",\n");
			json.append("      \"details\": \"").append(escapeJson(entry.getValue().details)).append("\"\n");
			json.append("    }");
			index++;
			if (index < report.size()) {
				json.append(',');
			}
			json.append('\n');
		}

		json.append("  }\n");
		json.append("}\n");
		Files.writeString(evidenceDir.resolve("final-report.json"), json.toString(), StandardCharsets.UTF_8);
	}

	private String escapeJson(final String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private void takeScreenshot(final Page page, final Path path, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(path).setFullPage(fullPage));
	}

	private void fillFieldByLabel(final Page page, final String label, final String value) {
		final Locator field = page.getByLabel(label, new Page.GetByLabelOptions().setExact(false)).first();
		field.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));
		field.click();
		field.fill(value);
	}

	private void assertVisibleText(final Page page, final String text) {
		final Locator locator = page.getByText(text, new Page.GetByTextOptions().setExact(false)).first();
		locator.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(DEFAULT_TIMEOUT_MS));
		Assert.assertTrue("Expected visible text: " + text, locator.isVisible());
	}

	private void assertAnyVisibleText(final Page page, final List<String> candidates) {
		RuntimeException lastError = null;
		for (final String candidate : candidates) {
			try {
				assertVisibleText(page, candidate);
				return;
			} catch (final RuntimeException e) {
				lastError = e;
			}
		}
		throw new AssertionError("None of the expected texts were visible: " + candidates, lastError);
	}

	private void clickByText(final Page page, final String text) {
		clickAnyByText(page, List.of(text));
	}

	private void clickAnyByText(final Page page, final List<String> texts) {
		RuntimeException lastError = null;

		for (final String text : texts) {
			try {
				final Locator button = page.getByRole(AriaRole.BUTTON,
						new Page.GetByRoleOptions().setName(text)).first();
				if (button.isVisible()) {
					button.click();
					page.waitForLoadState(LoadState.NETWORKIDLE);
					return;
				}
			} catch (final RuntimeException e) {
				lastError = e;
			}

			try {
				final Locator link = page.getByRole(AriaRole.LINK,
						new Page.GetByRoleOptions().setName(text)).first();
				if (link.isVisible()) {
					link.click();
					page.waitForLoadState(LoadState.NETWORKIDLE);
					return;
				}
			} catch (final RuntimeException e) {
				lastError = e;
			}

			try {
				final Locator byText = page.getByText(text, new Page.GetByTextOptions().setExact(false)).first();
				byText.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(5000));
				byText.click();
				page.waitForLoadState(LoadState.NETWORKIDLE);
				return;
			} catch (final RuntimeException e) {
				lastError = e;
			}
		}

		throw new AssertionError("Could not click any element by visible texts: " + texts, lastError);
	}

	private void tryClickText(final Page page, final String text) {
		try {
			clickByText(page, text);
		} catch (final RuntimeException ignored) {
			// This click is optional and only applies when an account picker is shown.
		}
	}

	private Page waitForPotentialNewPage(final BrowserContext context, final int previousPageCount) {
		final long timeoutAt = System.currentTimeMillis() + 10000L;
		while (System.currentTimeMillis() < timeoutAt) {
			if (context.pages().size() > previousPageCount) {
				return context.pages().get(context.pages().size() - 1);
			}

			try {
				Thread.sleep(200L);
			} catch (final InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
		}
		return null;
	}

	private Path createEvidenceDirectory() throws IOException {
		final String runId = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		final Path dir = Path.of("target", "saleads-evidence", runId);
		return Files.createDirectories(dir);
	}

	private String requiredConfig(final String envKey, final String propertyKey) {
		final String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}
		return System.getProperty(propertyKey);
	}

	private String optionalConfig(final String envKey, final String propertyKey, final String defaultValue) {
		final String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.isBlank()) {
			return envValue;
		}
		final String propertyValue = System.getProperty(propertyKey);
		if (propertyValue != null && !propertyValue.isBlank()) {
			return propertyValue;
		}
		return defaultValue;
	}

	@FunctionalInterface
	private interface CheckedStep {
		void execute();
	}

	private static class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details == null ? "" : details;
		}

		private static StepResult pass(final String details) {
			return new StepResult(true, details);
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details);
		}
	}
}
