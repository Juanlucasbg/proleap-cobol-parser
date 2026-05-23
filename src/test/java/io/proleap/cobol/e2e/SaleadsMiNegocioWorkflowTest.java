package io.proleap.cobol.e2e;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.Assume;
import org.junit.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;

/**
 * End-to-end validation for the SaleADS "Mi Negocio" workflow.
 *
 * <p>Environment variables:
 * <ul>
 *   <li>SALEADS_BASE_URL (required): Login URL for the target SaleADS environment.</li>
 *   <li>SALEADS_HEADLESS (optional): false to run headed mode. Defaults to true.</li>
 *   <li>SALEADS_GOOGLE_ACCOUNT (optional): Account to select when Google account chooser appears.
 *       Defaults to juanlucasbarbiergarzon@gmail.com.</li>
 *   <li>SALEADS_EXPECTED_USER_NAME (optional): Expected user name in "Informacion General".</li>
 * </ul>
 */
public class SaleadsMiNegocioWorkflowTest {

	private static final String DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		final String baseUrl = env("SALEADS_BASE_URL");
		Assume.assumeTrue("Set SALEADS_BASE_URL to run the SaleADS E2E workflow.",
				baseUrl != null && !baseUrl.isBlank());

		final String googleAccount = envOrDefault("SALEADS_GOOGLE_ACCOUNT", DEFAULT_GOOGLE_ACCOUNT);
		final String expectedUserName = env("SALEADS_EXPECTED_USER_NAME");
		final boolean headless = !"false".equalsIgnoreCase(envOrDefault("SALEADS_HEADLESS", "true"));
		final Path artifactsDir = prepareArtifactsDirectory();

		final LinkedHashMap<String, String> report = createInitialReport();
		final List<String> failureDetails = new ArrayList<>();
		final List<String> finalUrls = new ArrayList<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium().launch(new Browser.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1600, 1200));
			final Page appPage = context.newPage();
			appPage.setDefaultTimeout(30_000);

			appPage.navigate(baseUrl, new Page.NavigateOptions().setWaitUntil(LoadState.NETWORKIDLE));
			waitForUi(appPage);

			runStep(report, failureDetails, "Login", () -> {
				loginWithGoogle(context, appPage, googleAccount);
				assertMainInterface(appPage);
				takeScreenshot(appPage, artifactsDir.resolve("01-dashboard-loaded.png"), true);
			});

			runStep(report, failureDetails, "Mi Negocio menu", () -> {
				openMiNegocioMenu(appPage);
				assertTextVisible(appPage, "Agregar Negocio");
				assertTextVisible(appPage, "Administrar Negocios");
				takeScreenshot(appPage, artifactsDir.resolve("02-mi-negocio-expanded.png"), true);
			});

			runStep(report, failureDetails, "Agregar Negocio modal", () -> {
				clickVisibleByText(appPage, "Agregar Negocio");
				assertTextVisible(appPage, "Crear Nuevo Negocio");
				assertTextVisible(appPage, "Nombre del Negocio");
				assertTextVisible(appPage, "Tienes 2 de 3 negocios");
				assertTextVisible(appPage, "Cancelar");
				assertTextVisible(appPage, "Crear Negocio");
				takeScreenshot(appPage, artifactsDir.resolve("03-agregar-negocio-modal.png"), true);

				Locator nombreNegocioInput = firstVisible(
						appPage.locator("input[placeholder*='Nombre']"),
						appPage.locator("input[name*='nombre']"),
						appPage.locator("input").filter(new Locator.FilterOptions().setHasText("")));
				if (nombreNegocioInput != null) {
					nombreNegocioInput.fill("Negocio Prueba Automatizacion");
				}
				clickVisibleByText(appPage, "Cancelar");
				waitForUi(appPage);
			});

			runStep(report, failureDetails, "Administrar Negocios view", () -> {
				ensureMiNegocioExpanded(appPage);
				clickVisibleByText(appPage, "Administrar Negocios");
				waitForUi(appPage);
				assertTextVisible(appPage, "Informacion General", "Información General");
				assertTextVisible(appPage, "Detalles de la Cuenta");
				assertTextVisible(appPage, "Tus Negocios");
				assertTextVisible(appPage, "Seccion Legal", "Sección Legal");
				takeScreenshot(appPage, artifactsDir.resolve("04-administrar-negocios-view.png"), true);
			});

			runStep(report, failureDetails, "Información General", () -> {
				assertTextVisible(appPage, "Informacion General", "Información General");
				assertTrue("Expected a visible user email in Informacion General view.",
						EMAIL_PATTERN.matcher(appPage.innerText("body")).find());
				assertTextVisible(appPage, "BUSINESS PLAN");
				assertClickableByText(appPage, "Cambiar Plan");
				assertTrue("Expected to find a user name line in Informacion General.",
						hasLikelyUserNameLine(appPage.innerText("body"), expectedUserName));
			});

			runStep(report, failureDetails, "Detalles de la Cuenta", () -> {
				assertTextVisible(appPage, "Cuenta creada");
				assertTextVisible(appPage, "Estado activo");
				assertTextVisible(appPage, "Idioma seleccionado");
			});

			runStep(report, failureDetails, "Tus Negocios", () -> {
				assertTextVisible(appPage, "Tus Negocios");
				assertClickableByText(appPage, "Agregar Negocio");
				assertTextVisible(appPage, "Tienes 2 de 3 negocios");
				assertTrue("Expected business content to be visible in Tus Negocios section.",
						containsBusinessSectionContent(appPage.innerText("body")));
			});

			runStep(report, failureDetails, "Términos y Condiciones", () -> {
				String finalUrl = validateLegalLink(context, appPage, "Términos y Condiciones", artifactsDir,
						"05-terminos-y-condiciones.png");
				finalUrls.add("Terminos y Condiciones: " + finalUrl);
			});

			runStep(report, failureDetails, "Política de Privacidad", () -> {
				String finalUrl = validateLegalLink(context, appPage, "Política de Privacidad", artifactsDir,
						"06-politica-de-privacidad.png");
				finalUrls.add("Politica de Privacidad: " + finalUrl);
			});
		}

		writeReportFile(artifactsDir, report, finalUrls, failureDetails);
		printReportToStdout(report, finalUrls, artifactsDir, failureDetails);
		assertFalse("One or more Mi Negocio workflow validations failed. Check report in " + artifactsDir,
				hasAnyFail(report));
	}

	private static LinkedHashMap<String, String> createInitialReport() {
		LinkedHashMap<String, String> report = new LinkedHashMap<>();
		report.put("Login", "FAIL");
		report.put("Mi Negocio menu", "FAIL");
		report.put("Agregar Negocio modal", "FAIL");
		report.put("Administrar Negocios view", "FAIL");
		report.put("Información General", "FAIL");
		report.put("Detalles de la Cuenta", "FAIL");
		report.put("Tus Negocios", "FAIL");
		report.put("Términos y Condiciones", "FAIL");
		report.put("Política de Privacidad", "FAIL");
		return report;
	}

	private static void runStep(Map<String, String> report, List<String> failureDetails, String reportKey, StepBody stepBody) {
		try {
			stepBody.run();
			report.put(reportKey, "PASS");
		} catch (Throwable error) {
			report.put(reportKey, "FAIL");
			failureDetails.add(reportKey + ": " + error.getMessage());
		}
	}

	private static void loginWithGoogle(BrowserContext context, Page appPage, String googleAccount) {
		Locator loginButton = firstVisible(
				appPage.locator("button:has-text('Google'), [role='button']:has-text('Google'), a:has-text('Google')"),
				appPage.getByText(Pattern.compile("(?i)(iniciar\\s+sesi[oó]n\\s+con\\s+google|sign\\s*in\\s*with\\s*google|google)")));
		assertNotNull("Could not find the login with Google button.", loginButton);

		Page popup = clickAndMaybeGetPopup(context, () -> clickAndWait(loginButton, appPage));
		if (popup != null) {
			waitForUi(popup);
			selectGoogleAccountIfVisible(popup, googleAccount);
		} else {
			selectGoogleAccountIfVisible(appPage, googleAccount);
		}

		waitForUi(appPage);
	}

	private static void openMiNegocioMenu(Page appPage) {
		clickVisibleByText(appPage, "Mi Negocio");
		waitForUi(appPage);
	}

	private static void ensureMiNegocioExpanded(Page appPage) {
		if (isTextVisible(appPage, "Administrar Negocios")) {
			return;
		}
		openMiNegocioMenu(appPage);
	}

	private static void assertMainInterface(Page appPage) {
		assertTrue("Expected left sidebar navigation to be visible.",
				appPage.locator("aside, nav").first().isVisible(new Locator.IsVisibleOptions().setTimeout(10_000)));
		assertTextVisible(appPage, "Negocio");
	}

	private static String validateLegalLink(BrowserContext context, Page appPage, String linkText, Path artifactsDir,
			String screenshotFileName) {
		Locator legalLink = visibleByText(appPage, linkText);
		assertNotNull("Could not find legal link: " + linkText, legalLink);

		Page legalPage = clickAndMaybeGetPopup(context, () -> clickAndWait(legalLink, appPage));
		Page target = legalPage != null ? legalPage : appPage;
		waitForUi(target);

		assertTextVisible(target, linkText);
		String targetText = target.innerText("body");
		assertTrue("Expected legal content text for " + linkText, targetText != null && targetText.trim().length() > 200);
		takeScreenshot(target, artifactsDir.resolve(screenshotFileName), true);
		String finalUrl = target.url();

		if (legalPage != null) {
			legalPage.close();
			appPage.bringToFront();
			waitForUi(appPage);
		} else {
			appPage.goBack();
			waitForUi(appPage);
		}

		return finalUrl;
	}

	private static void selectGoogleAccountIfVisible(Page page, String googleAccount) {
		Locator accountEntry = firstVisible(
				page.getByText(Pattern.compile(Pattern.quote(googleAccount), Pattern.CASE_INSENSITIVE)),
				page.locator("div:has-text('" + googleAccount + "'), li:has-text('" + googleAccount + "')"));
		if (accountEntry != null) {
			clickAndWait(accountEntry, page);
		}
	}

	private static Page clickAndMaybeGetPopup(BrowserContext context, Runnable clickAction) {
		AtomicBoolean executed = new AtomicBoolean(false);
		try {
			return context.waitForPage(() -> {
				executed.set(true);
				clickAction.run();
			}, new BrowserContext.WaitForPageOptions().setTimeout(8_000));
		} catch (PlaywrightException noPopup) {
			if (!executed.get()) {
				clickAction.run();
			}
			return null;
		}
	}

	private static void clickVisibleByText(Page page, String text) {
		Locator target = visibleByText(page, text);
		assertNotNull("Could not find visible element with text: " + text, target);
		clickAndWait(target, page);
	}

	private static void assertClickableByText(Page page, String text) {
		Locator target = visibleByText(page, text);
		assertNotNull("Could not find clickable text element: " + text, target);
		assertTrue("Element exists but is not visible/clickable: " + text,
				target.isVisible(new Locator.IsVisibleOptions().setTimeout(5_000)));
	}

	private static Locator visibleByText(Page page, String text) {
		return firstVisible(
				page.locator("button:has-text('" + text + "'), [role='button']:has-text('" + text + "'), a:has-text('" + text + "')"),
				page.getByText(Pattern.compile("(?i)" + Pattern.quote(text))));
	}

	private static void assertTextVisible(Page page, String... candidates) {
		List<Locator> locators = Arrays.stream(candidates)
				.map(candidate -> page.getByText(Pattern.compile("(?i)" + Pattern.quote(candidate))))
				.collect(Collectors.toList());
		Locator visible = firstVisible(locators.toArray(new Locator[0]));
		assertNotNull("Expected to find one of these visible texts: " + Arrays.toString(candidates), visible);
	}

	private static boolean isTextVisible(Page page, String text) {
		Locator locator = page.getByText(Pattern.compile("(?i)" + Pattern.quote(text))).first();
		try {
			return locator.isVisible(new Locator.IsVisibleOptions().setTimeout(3_000));
		} catch (PlaywrightException ignored) {
			return false;
		}
	}

	private static Locator firstVisible(Locator... locators) {
		for (Locator locator : locators) {
			if (locator == null) {
				continue;
			}
			Locator first = locator.first();
			try {
				if (first.isVisible(new Locator.IsVisibleOptions().setTimeout(3_000))) {
					return first;
				}
			} catch (PlaywrightException ignored) {
				// keep trying candidates
			}
		}
		return null;
	}

	private static void clickAndWait(Locator locator, Page page) {
		locator.scrollIntoViewIfNeeded();
		locator.click();
		waitForUi(page);
	}

	private static void waitForUi(Page page) {
		try {
			page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(12_000));
		} catch (PlaywrightException ignored) {
			// Some UI interactions do not trigger a navigation lifecycle event.
		}
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(12_000));
		} catch (PlaywrightException ignored) {
			// Network idle may never happen in some SPA screens due to polling requests.
		}
		page.waitForTimeout(500);
	}

	private static void takeScreenshot(Page page, Path screenshotPath, boolean fullPage) {
		page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setFullPage(fullPage));
	}

	private static Path prepareArtifactsDirectory() throws IOException {
		Path artifactsDir = Paths.get("target", "saleads-e2e-artifacts", LocalDateTime.now().format(TS_FORMAT));
		Files.createDirectories(artifactsDir);
		return artifactsDir;
	}

	private static void writeReportFile(Path artifactsDir, Map<String, String> report, List<String> finalUrls,
			List<String> failures) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("SaleADS Mi Negocio workflow report").append(System.lineSeparator());
		sb.append("=================================").append(System.lineSeparator()).append(System.lineSeparator());
		report.forEach((key, value) -> sb.append("- ").append(key).append(": ").append(value).append(System.lineSeparator()));
		sb.append(System.lineSeparator()).append("Final URLs").append(System.lineSeparator());
		if (finalUrls.isEmpty()) {
			sb.append("- (none captured)").append(System.lineSeparator());
		} else {
			finalUrls.forEach(url -> sb.append("- ").append(url).append(System.lineSeparator()));
		}
		sb.append(System.lineSeparator()).append("Failure details").append(System.lineSeparator());
		if (failures.isEmpty()) {
			sb.append("- (none)").append(System.lineSeparator());
		} else {
			failures.forEach(failure -> sb.append("- ").append(failure).append(System.lineSeparator()));
		}
		Files.writeString(artifactsDir.resolve("final-report.txt"), sb.toString());
	}

	private static void printReportToStdout(Map<String, String> report, List<String> finalUrls, Path artifactsDir,
			List<String> failures) {
		System.out.println();
		System.out.println("===== SaleADS Mi Negocio Final Report =====");
		report.forEach((key, value) -> System.out.printf("%s: %s%n", key, value));
		System.out.println();
		System.out.println("Captured URLs:");
		if (finalUrls.isEmpty()) {
			System.out.println("- none");
		} else {
			finalUrls.forEach(url -> System.out.println("- " + url));
		}
		if (!failures.isEmpty()) {
			System.out.println();
			System.out.println("Step failures:");
			failures.forEach(failure -> System.out.println("- " + failure));
		}
		System.out.println();
		System.out.println("Artifacts directory: " + artifactsDir.toAbsolutePath());
		System.out.println("===========================================");
	}

	private static boolean hasLikelyUserNameLine(String pageText, String expectedUserName) {
		if (expectedUserName != null && !expectedUserName.isBlank()) {
			return pageText != null && pageText.toLowerCase(Locale.ROOT).contains(expectedUserName.toLowerCase(Locale.ROOT));
		}
		if (pageText == null || pageText.isBlank()) {
			return false;
		}

		String[] excludedTerms = {
				"informacion general", "información general", "business plan", "cambiar plan", "cuenta creada",
				"estado activo", "idioma seleccionado", "tus negocios", "seccion legal", "sección legal"
		};
		return Arrays.stream(pageText.split("\\R"))
				.map(String::trim)
				.filter(line -> !line.isEmpty())
				.filter(line -> !line.contains("@"))
				.filter(line -> line.length() >= 3 && line.length() <= 60)
				.anyMatch(line -> {
					String lower = line.toLowerCase(Locale.ROOT);
					for (String excluded : excludedTerms) {
						if (lower.contains(excluded)) {
							return false;
						}
					}
					return line.matches("[\\p{L}][\\p{L}\\s'\\-.]{2,}");
				});
	}

	private static boolean containsBusinessSectionContent(String pageText) {
		if (pageText == null || pageText.isBlank()) {
			return false;
		}
		String normalized = pageText.toLowerCase(Locale.ROOT);
		return normalized.contains("tus negocios")
				&& normalized.contains("agregar negocio")
				&& normalized.contains("tienes 2 de 3 negocios");
	}

	private static boolean hasAnyFail(Map<String, String> report) {
		return report.values().stream().anyMatch("FAIL"::equalsIgnoreCase);
	}

	private static String env(String key) {
		return System.getenv(key);
	}

	private static String envOrDefault(String key, String defaultValue) {
		String value = env(key);
		return value == null || value.isBlank() ? defaultValue : value;
	}

	@FunctionalInterface
	private interface StepBody {
		void run() throws Exception;
	}
}
