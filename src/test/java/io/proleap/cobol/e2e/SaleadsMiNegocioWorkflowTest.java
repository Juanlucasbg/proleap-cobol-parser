package io.proleap.cobol.e2e;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class SaleadsMiNegocioWorkflowTest {

	private static final int SHORT_TIMEOUT_MS = 3_000;
	private static final int UI_TIMEOUT_MS = 15_000;
	private static final List<String> REPORT_FIELDS = Arrays.asList(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Informacion General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"Terminos y Condiciones",
			"Politica de Privacidad");

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		Assume.assumeTrue(
				"Skipping SaleADS E2E test. Set SALEADS_E2E_ENABLE=true to execute this workflow.",
				Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_E2E_ENABLE", "false")));

		final String baseUrl = System.getenv("SALEADS_BASE_URL");
		final String googleAccount = System.getenv().getOrDefault("SALEADS_GOOGLE_ACCOUNT",
				"juanlucasbarbiergarzon@gmail.com");
		final String expectedUserName = System.getenv().getOrDefault("SALEADS_EXPECTED_USER_NAME", "Juan");
		final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "false"));
		final Path evidenceDir = evidenceDirectory();
		final Map<String, Boolean> statusByField = new LinkedHashMap<>();
		final Map<String, String> noteByField = new LinkedHashMap<>();
		for (final String field : REPORT_FIELDS) {
			statusByField.put(field, null);
			noteByField.put(field, "");
		}

		String termsUrl = "";
		String privacyUrl = "";

		try (Playwright playwright = Playwright.create()) {
			final BrowserContext context = createContext(playwright, headless);
			try (context) {
				final Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
				page.setDefaultTimeout(UI_TIMEOUT_MS);

				if (baseUrl != null && !baseUrl.isBlank()) {
					page.navigate(baseUrl);
					waitForUiLoad(page);
				}

				final boolean loginPass = runStep("Login", statusByField, noteByField, () -> {
					final Locator loginButton = firstExisting(page,
							page.getByText(Pattern.compile("(?i)(sign in with google|iniciar sesi[oó]n con google|continuar con google|google)")),
							page.getByText(Pattern.compile("(?i)(login|iniciar sesi[oó]n|acceder)")));
					clickAndWait(page, loginButton, context);

					final Page authPage = latestPage(context);
					final Locator accountOption = authPage
							.getByText(Pattern.compile("(?i)" + Pattern.quote(googleAccount)));
					if (accountOption.count() > 0) {
						clickAndWait(authPage, accountOption.first(), context);
					}

					assertVisible(page.getByText(Pattern.compile("(?i)(negocio|dashboard|inicio)")),
							"Main application interface is not visible after login.");
					assertVisible(firstExisting(page,
							page.locator("aside"),
							page.locator("nav").first(),
							page.getByText(Pattern.compile("(?i)negocio"))),
							"Left sidebar navigation is not visible.");

					screenshot(page, evidenceDir, "01-dashboard", false);
				});

				if (loginPass) {
					runStep("Mi Negocio menu", statusByField, noteByField, () -> {
						final Locator negocioOption = firstExisting(page,
								page.getByText(Pattern.compile("(?i)^negocio$")),
								page.getByText(Pattern.compile("(?i)negocio")));
						clickAndWait(page, negocioOption, context);

						final Locator miNegocioOption = page.getByText(Pattern.compile("(?i)mi negocio"));
						clickAndWait(page, miNegocioOption.first(), context);

						assertVisible(page.getByText(Pattern.compile("(?i)agregar negocio")),
								"'Agregar Negocio' is not visible.");
						assertVisible(page.getByText(Pattern.compile("(?i)administrar negocios")),
								"'Administrar Negocios' is not visible.");

						screenshot(page, evidenceDir, "02-mi-negocio-menu", false);
					});
				}

				if (isPassed(statusByField, "Mi Negocio menu")) {
					runStep("Agregar Negocio modal", statusByField, noteByField, () -> {
						clickAndWait(page, page.getByText(Pattern.compile("(?i)agregar negocio")).first(), context);

						assertVisible(page.getByText(Pattern.compile("(?i)crear nuevo negocio")),
								"Modal title 'Crear Nuevo Negocio' is not visible.");
						assertVisible(page.getByText(Pattern.compile("(?i)nombre del negocio")),
								"Input field 'Nombre del Negocio' was not found.");
						assertVisible(page.getByText(Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios")),
								"Text 'Tienes 2 de 3 negocios' is not visible.");
						assertVisible(page.getByText(Pattern.compile("(?i)cancelar")),
								"'Cancelar' button is not visible.");
						assertVisible(page.getByText(Pattern.compile("(?i)crear negocio")),
								"'Crear Negocio' button is not visible.");

						final Locator businessNameInput = page.locator("input").first();
						businessNameInput.click();
						businessNameInput.fill("Negocio Prueba Automatizacion");
						clickAndWait(page, page.getByText(Pattern.compile("(?i)^cancelar$")).first(), context);

						screenshot(page, evidenceDir, "03-agregar-negocio-modal", false);
					});
				}

				if (isPassed(statusByField, "Agregar Negocio modal")) {
					runStep("Administrar Negocios view", statusByField, noteByField, () -> {
						final Locator administrar = page.getByText(Pattern.compile("(?i)administrar negocios"));
						if (administrar.count() == 0 || !administrar.first().isVisible()) {
							final Locator miNegocio = page.getByText(Pattern.compile("(?i)mi negocio"));
							clickAndWait(page, miNegocio.first(), context);
						}

						clickAndWait(page, administrar.first(), context);

						assertVisible(page.getByText(Pattern.compile("(?i)informaci[oó]n general")),
								"'Informacion General' section is not visible.");
						assertVisible(page.getByText(Pattern.compile("(?i)detalles de la cuenta")),
								"'Detalles de la Cuenta' section is not visible.");
						assertVisible(page.getByText(Pattern.compile("(?i)tus negocios")),
								"'Tus Negocios' section is not visible.");
						assertVisible(page.getByText(Pattern.compile("(?i)(secci[oó]n legal|legal)")),
								"'Seccion Legal' section is not visible.");

						screenshot(page, evidenceDir, "04-administrar-negocios", true);
					});
				}

				if (isPassed(statusByField, "Administrar Negocios view")) {
					runStep("Informacion General", statusByField, noteByField, () -> {
						assertVisible(page.getByText(Pattern.compile("(?i)" + Pattern.quote(expectedUserName))),
								"User name is not visible.");
						assertVisible(page.getByText(Pattern.compile("(?i)" + Pattern.quote(googleAccount))),
								"User email is not visible.");
						assertVisible(page.getByText(Pattern.compile("(?i)business\\s*plan")),
								"'BUSINESS PLAN' text is not visible.");
						assertVisible(page.getByText(Pattern.compile("(?i)cambiar plan")),
								"'Cambiar Plan' button is not visible.");
					});

					runStep("Detalles de la Cuenta", statusByField, noteByField, () -> {
						assertVisible(page.getByText(Pattern.compile("(?i)cuenta creada")),
								"'Cuenta creada' is not visible.");
						assertVisible(page.getByText(Pattern.compile("(?i)estado activo")),
								"'Estado activo' is not visible.");
						assertVisible(page.getByText(Pattern.compile("(?i)idioma seleccionado")),
								"'Idioma seleccionado' is not visible.");
					});

					runStep("Tus Negocios", statusByField, noteByField, () -> {
						assertVisible(page.getByText(Pattern.compile("(?i)tus negocios")),
								"'Tus Negocios' section is not visible.");
						assertVisible(page.getByText(Pattern.compile("(?i)agregar negocio")),
								"'Agregar Negocio' button is not visible in 'Tus Negocios'.");
						assertVisible(page.getByText(Pattern.compile("(?i)tienes\\s*2\\s*de\\s*3\\s*negocios")),
								"'Tienes 2 de 3 negocios' is not visible.");

						final int negocioMentions = page.getByText(Pattern.compile("(?i)negocio")).count();
						Assert.assertTrue("Business list appears to be missing.", negocioMentions >= 3);
					});

					final LegalValidationResult termsResult = validateLegalLink(page, context, evidenceDir,
							"Terminos y Condiciones", "(t[eé]rminos|terminos) y condiciones", "05-terminos");
					statusByField.put("Terminos y Condiciones", termsResult.pass);
					noteByField.put("Terminos y Condiciones", termsResult.note);
					termsUrl = termsResult.url;

					final LegalValidationResult privacyResult = validateLegalLink(page, context, evidenceDir,
							"Politica de Privacidad", "pol[ií]tica de privacidad", "06-politica-privacidad");
					statusByField.put("Politica de Privacidad", privacyResult.pass);
					noteByField.put("Politica de Privacidad", privacyResult.note);
					privacyUrl = privacyResult.url;
				}
			}
		} finally {
			fillMissingAsFailed(statusByField, noteByField);
			writeReport(evidenceDir, statusByField, noteByField, termsUrl, privacyUrl);
		}

		Assert.assertTrue("One or more workflow validations failed. See report: " + evidenceDir.resolve("final-report.md"),
				allPassed(statusByField));
	}

	private static BrowserContext createContext(final Playwright playwright, final boolean headless) throws IOException {
		final String userDataDir = System.getenv("SALEADS_USER_DATA_DIR");
		final Path profilePath;
		if (userDataDir != null && !userDataDir.isBlank()) {
			profilePath = Paths.get(userDataDir);
		} else {
			profilePath = Files.createTempDirectory("saleads-playwright-profile");
		}

		Files.createDirectories(profilePath);
		return playwright.chromium().launchPersistentContext(profilePath,
				new BrowserType.LaunchPersistentContextOptions().setHeadless(headless).setViewportSize(1440, 900));
	}

	private static boolean runStep(final String stepName, final Map<String, Boolean> statusByField,
			final Map<String, String> noteByField, final Runnable action) {
		try {
			action.run();
			statusByField.put(stepName, true);
			noteByField.put(stepName, "PASS");
			return true;
		} catch (final Throwable ex) {
			statusByField.put(stepName, false);
			noteByField.put(stepName, shortError(ex));
			return false;
		}
	}

	private static LegalValidationResult validateLegalLink(final Page appPage, final BrowserContext context,
			final Path evidenceDir, final String reportField, final String linkText, final String screenshotName) {
		final LegalValidationResult result = new LegalValidationResult();
		final String startingUrl = appPage.url();

		try {
			final Locator link = appPage.getByText(Pattern.compile("(?i)" + linkText));
			final int pagesBefore = context.pages().size();
			clickAndWait(appPage, link.first(), context);

			Page target = appPage;
			if (context.pages().size() > pagesBefore) {
				target = latestPage(context);
				target.bringToFront();
				waitForUiLoad(target);
			}

			assertVisible(target.getByText(Pattern.compile("(?i)" + linkText)),
					reportField + " heading is not visible.");
			final int paragraphCount = target.locator("p").count();
			Assert.assertTrue(reportField + " content text is not visible.", paragraphCount > 0);

			screenshot(target, evidenceDir, screenshotName, true);
			result.url = target.url();
			result.pass = true;
			result.note = "PASS";

			if (target != appPage) {
				target.close();
				appPage.bringToFront();
			} else {
				try {
					appPage.goBack(new Page.GoBackOptions().setTimeout(UI_TIMEOUT_MS));
				} catch (final Exception ignored) {
					if (startingUrl != null && !startingUrl.isBlank()) {
						appPage.navigate(startingUrl);
					}
				}
				waitForUiLoad(appPage);
			}
		} catch (final Throwable ex) {
			result.pass = false;
			result.note = shortError(ex);
		}

		return result;
	}

	private static void fillMissingAsFailed(final Map<String, Boolean> statusByField, final Map<String, String> noteByField) {
		for (final String field : REPORT_FIELDS) {
			if (statusByField.get(field) == null) {
				statusByField.put(field, false);
				noteByField.put(field, "Step was not executed because a previous step failed.");
			}
		}
	}

	private static boolean allPassed(final Map<String, Boolean> statusByField) {
		for (final Boolean passed : statusByField.values()) {
			if (!Boolean.TRUE.equals(passed)) {
				return false;
			}
		}
		return true;
	}

	private static boolean isPassed(final Map<String, Boolean> statusByField, final String key) {
		return Boolean.TRUE.equals(statusByField.get(key));
	}

	private static void writeReport(final Path evidenceDir, final Map<String, Boolean> statusByField,
			final Map<String, String> noteByField, final String termsUrl, final String privacyUrl) throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("# SaleADS Mi Negocio Workflow Report\n\n");
		report.append("| Validation | Result | Notes |\n");
		report.append("|---|---|---|\n");
		for (final String field : REPORT_FIELDS) {
			final boolean passed = Boolean.TRUE.equals(statusByField.get(field));
			final String result = passed ? "PASS" : "FAIL";
			final String note = noteByField.getOrDefault(field, "").replace("\n", " ").trim();
			report.append("| ").append(field).append(" | ").append(result).append(" | ").append(note).append(" |\n");
		}

		report.append("\n## Captured URLs\n\n");
		report.append("- Terminos y Condiciones: ").append(termsUrl == null ? "" : termsUrl).append("\n");
		report.append("- Politica de Privacidad: ").append(privacyUrl == null ? "" : privacyUrl).append("\n");

		report.append("\n## Evidence directory\n\n");
		report.append("- ").append(evidenceDir.toAbsolutePath()).append("\n");

		Files.writeString(evidenceDir.resolve("final-report.md"), report.toString(), StandardCharsets.UTF_8);
	}

	private static Path evidenceDirectory() throws IOException {
		final String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path path = Paths.get("target", "saleads-evidence", stamp);
		Files.createDirectories(path);
		return path;
	}

	private static void screenshot(final Page page, final Path evidenceDir, final String fileName, final boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(evidenceDir.resolve(fileName + ".png")).setFullPage(fullPage));
	}

	private static void clickAndWait(final Page page, final Locator locator, final BrowserContext context) {
		final int pagesBefore = context.pages().size();
		locator.first().click();
		waitForUiLoad(page);
		final int pagesAfter = context.pages().size();
		if (pagesAfter > pagesBefore) {
			waitForUiLoad(latestPage(context));
		}
	}

	private static Page latestPage(final BrowserContext context) {
		return context.pages().get(context.pages().size() - 1);
	}

	private static void waitForUiLoad(final Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(UI_TIMEOUT_MS));
		} catch (final TimeoutError ignored) {
		}

		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(SHORT_TIMEOUT_MS));
		} catch (final TimeoutError ignored) {
		}

		page.waitForTimeout(600);
	}

	private static void assertVisible(final Locator locator, final String errorMessage) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions().setTimeout(UI_TIMEOUT_MS));
		} catch (final Exception ex) {
			throw new AssertionError(errorMessage, ex);
		}
	}

	@SafeVarargs
	private static Locator firstExisting(final Page page, final Locator... locators) {
		for (final Locator locator : locators) {
			if (locator != null && locator.count() > 0) {
				return locator.first();
			}
		}
		throw new IllegalStateException("Could not find any of the expected locators on page: " + page.url());
	}

	private static String shortError(final Throwable ex) {
		final String message = ex.getMessage();
		if (message == null || message.isBlank()) {
			return ex.getClass().getSimpleName();
		}

		final String normalized = message.replace('\n', ' ').trim();
		return normalized.length() > 220 ? normalized.substring(0, 220) + "..." : normalized;
	}

	private static final class LegalValidationResult {
		private boolean pass;
		private String note = "";
		private String url = "";
	}
}
