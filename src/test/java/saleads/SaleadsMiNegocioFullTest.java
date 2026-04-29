package saleads;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SaleadsMiNegocioFullTest {

	private static final int DEFAULT_TIMEOUT_MS = 12000;

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		final String startUrl = trimToNull(readConfig("SALEADS_START_URL"));
		final boolean headless = Boolean.parseBoolean(System.getenv().getOrDefault("SALEADS_HEADLESS", "true"));
		final String googleAccount = trimToNull(System.getenv().getOrDefault("SALEADS_GOOGLE_ACCOUNT",
				"juanlucasbarbiergarzon@gmail.com"));
		final String expectedUserName = trimToNull(System.getenv().getOrDefault("SALEADS_EXPECTED_USER_NAME",
				"Juan Lucas Barbier Garzon"));

		final Path evidenceDir = createEvidenceDir();
		final Path reportPath = evidenceDir.resolve("final-report.txt");

		final LinkedHashMap<String, Boolean> report = new LinkedHashMap<>();
		report.put("Login", false);
		report.put("Mi Negocio menu", false);
		report.put("Agregar Negocio modal", false);
		report.put("Administrar Negocios view", false);
		report.put("Información General", false);
		report.put("Detalles de la Cuenta", false);
		report.put("Tus Negocios", false);
		report.put("Términos y Condiciones", false);
		report.put("Política de Privacidad", false);

		final List<String> errors = new ArrayList<>();
		final Map<String, String> legalUrls = new LinkedHashMap<>();

		try (Playwright playwright = Playwright.create()) {
			final Browser browser = playwright.chromium()
					.launch(new BrowserType.LaunchOptions().setHeadless(headless));
			final BrowserContext context = browser.newContext();
			final Page page = context.newPage();

			if (startUrl == null) {
				errors.addAll(report.keySet());
				writeFinalReport(report, legalUrls, reportPath);
				Assert.fail("SALEADS_START_URL (or -DSALEADS_START_URL) is required for execution.");
			}
			navigateAndWait(page, startUrl);

			final boolean loginPass = stepLoginWithGoogle(context, page, googleAccount, evidenceDir);
			report.put("Login", loginPass);
			if (!loginPass) {
				errors.add("Login");
			}

			final boolean menuPass = stepOpenMiNegocioMenu(page, evidenceDir);
			report.put("Mi Negocio menu", menuPass);
			if (!menuPass) {
				errors.add("Mi Negocio menu");
			}

			final boolean modalPass = stepValidateAgregarNegocioModal(page, evidenceDir);
			report.put("Agregar Negocio modal", modalPass);
			if (!modalPass) {
				errors.add("Agregar Negocio modal");
			}

			final boolean administrarPass = stepOpenAdministrarNegocios(page, evidenceDir);
			report.put("Administrar Negocios view", administrarPass);
			if (!administrarPass) {
				errors.add("Administrar Negocios view");
			}

			final boolean infoGeneralPass = stepValidateInformacionGeneral(page, googleAccount, expectedUserName);
			report.put("Información General", infoGeneralPass);
			if (!infoGeneralPass) {
				errors.add("Información General");
			}

			final boolean detallesPass = stepValidateDetallesCuenta(page);
			report.put("Detalles de la Cuenta", detallesPass);
			if (!detallesPass) {
				errors.add("Detalles de la Cuenta");
			}

			final boolean negociosPass = stepValidateTusNegocios(page);
			report.put("Tus Negocios", negociosPass);
			if (!negociosPass) {
				errors.add("Tus Negocios");
			}

			final boolean termsPass = stepValidateLegalLink(context, page, "T[eé]rminos y Condiciones", "terminos",
					evidenceDir, legalUrls);
			report.put("Términos y Condiciones", termsPass);
			if (!termsPass) {
				errors.add("Términos y Condiciones");
			}
			screenshot(page, evidenceDir.resolve("after-terms-return.png"), false);

			final boolean privacyPass = stepValidateLegalLink(context, page, "Pol[ií]tica de Privacidad", "privacidad",
					evidenceDir, legalUrls);
			report.put("Política de Privacidad", privacyPass);
			if (!privacyPass) {
				errors.add("Política de Privacidad");
			}
			screenshot(page, evidenceDir.resolve("after-privacy-return.png"), false);

			writeFinalReport(report, legalUrls, reportPath);

			final StringBuilder failureMessage = new StringBuilder();
			if (!errors.isEmpty()) {
				failureMessage.append("Failed validations: ").append(String.join(", ", errors));
			}
			Assert.assertTrue(failureMessage.toString(), errors.isEmpty());
		}
	}

	private boolean stepLoginWithGoogle(final BrowserContext context, final Page page, final String googleAccount,
			final Path evidenceDir) {
		try {
			final Locator loginButton = firstVisibleOrThrow(
					page.locator("role=button[name=/Sign in with Google|Iniciar sesi[oó]n con Google|Google/i]"),
					page.locator("role=link[name=/Sign in with Google|Iniciar sesi[oó]n con Google|Google/i]"),
					page.locator("text=/Sign in with Google|Iniciar sesi[oó]n con Google/i"));

			final Set<Page> beforePages = new HashSet<>(context.pages());
			clickAndWait(loginButton, page);

			Page googlePage = waitForNewPage(context, beforePages, 10000);
			if (googlePage == null) {
				googlePage = page;
			}

			if (googleAccount != null) {
				final Locator accountOption = googlePage.locator(
						"text=/" + escapeForRegex(googleAccount) + "/i");
				if (isVisible(accountOption, 8000)) {
					clickAndWait(accountOption, googlePage);
				}
			}

			waitForUi(page);
			final boolean appInterfaceVisible = isVisibleAny(15000, page.locator("text=/Mi Negocio|Negocio/i"),
					page.locator("role=navigation"),
					page.locator("text=/Administrar Negocios|Agregar Negocio/i"));
			final boolean sidebarVisible = isVisibleAny(12000, page.locator("text=/Negocio/i"),
					page.locator("role=navigation"));

			if (appInterfaceVisible && sidebarVisible) {
				screenshot(page, evidenceDir.resolve("01-dashboard-loaded.png"), false);
			}
			return appInterfaceVisible && sidebarVisible;
		} catch (PlaywrightException e) {
			return false;
		}
	}

	private boolean stepOpenMiNegocioMenu(final Page page, final Path evidenceDir) {
		try {
			final Locator negocio = firstVisibleOrThrow(
					page.locator("role=button[name=/^Negocio$/i]"),
					page.locator("role=link[name=/^Negocio$/i]"),
					page.locator("text=/^Negocio$/i"));
			clickAndWait(negocio, page);

			final Locator miNegocio = firstVisibleOrThrow(
					page.locator("role=button[name=/Mi Negocio/i]"),
					page.locator("role=link[name=/Mi Negocio/i]"),
					page.locator("text=/Mi Negocio/i"));
			clickAndWait(miNegocio, page);

			final boolean agregarVisible = isVisibleAny(DEFAULT_TIMEOUT_MS,
					page.locator("role=button[name=/Agregar Negocio/i]"),
					page.locator("role=link[name=/Agregar Negocio/i]"),
					page.locator("text=/Agregar Negocio/i"));
			final boolean administrarVisible = isVisibleAny(DEFAULT_TIMEOUT_MS,
					page.locator("role=button[name=/Administrar Negocios/i]"),
					page.locator("role=link[name=/Administrar Negocios/i]"),
					page.locator("text=/Administrar Negocios/i"));

			if (agregarVisible && administrarVisible) {
				screenshot(page, evidenceDir.resolve("02-mi-negocio-menu-expanded.png"), false);
			}
			return agregarVisible && administrarVisible;
		} catch (PlaywrightException e) {
			return false;
		}
	}

	private boolean stepValidateAgregarNegocioModal(final Page page, final Path evidenceDir) {
		try {
			final Locator agregarNegocio = firstVisibleOrThrow(
					page.locator("role=button[name=/Agregar Negocio/i]"),
					page.locator("role=link[name=/Agregar Negocio/i]"),
					page.locator("text=/Agregar Negocio/i"));
			clickAndWait(agregarNegocio, page);

			final boolean modalTitle = isVisibleAny(DEFAULT_TIMEOUT_MS,
					page.locator("role=heading[name=/Crear Nuevo Negocio/i]"),
					page.locator("text=/Crear Nuevo Negocio/i"));
			final boolean nombreInput = isVisibleAny(DEFAULT_TIMEOUT_MS,
					page.locator("[role='dialog'] input"),
					page.locator("input[placeholder*='Nombre del Negocio']"),
					page.locator("input[name*='negocio' i]"));
			final boolean negociosQuota = isVisibleAny(DEFAULT_TIMEOUT_MS,
					page.locator("text=/Tienes\\s+2\\s+de\\s+3\\s+negocios/i"));
			final boolean cancelarBtn = isVisibleAny(DEFAULT_TIMEOUT_MS,
					page.locator("role=button[name=/Cancelar/i]"),
					page.locator("text=/Cancelar/i"));
			final boolean crearBtn = isVisibleAny(DEFAULT_TIMEOUT_MS,
					page.locator("role=button[name=/Crear Negocio/i]"),
					page.locator("text=/Crear Negocio/i"));

			if (modalTitle) {
				screenshot(page, evidenceDir.resolve("03-agregar-negocio-modal.png"), false);
			}

			Locator input = firstVisibleOrNull(
					page.locator("[role='dialog'] input"),
					page.locator("input[placeholder*='Nombre del Negocio']"),
					page.locator("input[name*='negocio' i]"));
			if (input != null) {
				input.fill("Negocio Prueba Automatización");
				waitForUi(page);
			}

			final Locator cancelar = firstVisibleOrNull(
					page.locator("role=button[name=/Cancelar/i]"),
					page.locator("text=/Cancelar/i"));
			if (cancelar != null) {
				clickAndWait(cancelar, page);
			}

			return modalTitle && nombreInput && negociosQuota && cancelarBtn && crearBtn;
		} catch (PlaywrightException e) {
			return false;
		}
	}

	private boolean stepOpenAdministrarNegocios(final Page page, final Path evidenceDir) {
		try {
			if (!isVisibleAny(2500, page.locator("text=/Administrar Negocios/i"))) {
				final Locator miNegocio = firstVisibleOrNull(
						page.locator("role=button[name=/Mi Negocio/i]"),
						page.locator("role=link[name=/Mi Negocio/i]"),
						page.locator("text=/Mi Negocio/i"));
				if (miNegocio != null) {
					clickAndWait(miNegocio, page);
				}
			}

			final Locator administrar = firstVisibleOrThrow(
					page.locator("role=button[name=/Administrar Negocios/i]"),
					page.locator("role=link[name=/Administrar Negocios/i]"),
					page.locator("text=/Administrar Negocios/i"));
			clickAndWait(administrar, page);

			final boolean infoGeneral = isVisibleAny(DEFAULT_TIMEOUT_MS, page.locator("text=/Informaci[oó]n General/i"));
			final boolean detalles = isVisibleAny(DEFAULT_TIMEOUT_MS, page.locator("text=/Detalles de la Cuenta/i"));
			final boolean tusNegocios = isVisibleAny(DEFAULT_TIMEOUT_MS, page.locator("text=/Tus Negocios/i"));
			final boolean seccionLegal = isVisibleAny(DEFAULT_TIMEOUT_MS, page.locator("text=/Secci[oó]n Legal/i"));

			if (infoGeneral && detalles && tusNegocios && seccionLegal) {
				screenshot(page, evidenceDir.resolve("04-administrar-negocios-view-full.png"), true);
			}
			return infoGeneral && detalles && tusNegocios && seccionLegal;
		} catch (PlaywrightException e) {
			return false;
		}
	}

	private boolean stepValidateInformacionGeneral(final Page page, final String googleAccount, final String expectedUserName) {
		try {
			final boolean userNameVisible;
			if (expectedUserName != null) {
				userNameVisible = isVisibleAny(DEFAULT_TIMEOUT_MS,
						page.locator("text=/" + escapeForRegex(expectedUserName) + "/i"));
			} else {
				userNameVisible = isVisibleAny(DEFAULT_TIMEOUT_MS,
						page.locator("text=/Nombre|Usuario|Perfil/i"));
			}

			final boolean userEmailVisible;
			if (googleAccount != null) {
				userEmailVisible = isVisibleAny(DEFAULT_TIMEOUT_MS,
						page.locator("text=/" + escapeForRegex(googleAccount) + "/i"),
						page.locator("text=/[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}/i"));
			} else {
				userEmailVisible = isVisibleAny(DEFAULT_TIMEOUT_MS,
						page.locator("text=/[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}/i"));
			}

			final boolean businessPlanVisible = isVisibleAny(DEFAULT_TIMEOUT_MS, page.locator("text=/BUSINESS PLAN/i"));
			final boolean cambiarPlanVisible = isVisibleAny(DEFAULT_TIMEOUT_MS,
					page.locator("role=button[name=/Cambiar Plan/i]"),
					page.locator("text=/Cambiar Plan/i"));

			return userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible;
		} catch (PlaywrightException e) {
			return false;
		}
	}

	private boolean stepValidateDetallesCuenta(final Page page) {
		try {
			final boolean cuentaCreada = isVisibleAny(DEFAULT_TIMEOUT_MS, page.locator("text=/Cuenta creada/i"));
			final boolean estadoActivo = isVisibleAny(DEFAULT_TIMEOUT_MS, page.locator("text=/Estado activo/i"));
			final boolean idiomaSeleccionado = isVisibleAny(DEFAULT_TIMEOUT_MS, page.locator("text=/Idioma seleccionado/i"));
			return cuentaCreada && estadoActivo && idiomaSeleccionado;
		} catch (PlaywrightException e) {
			return false;
		}
	}

	private boolean stepValidateTusNegocios(final Page page) {
		try {
			final boolean businessListVisible = isVisibleAny(DEFAULT_TIMEOUT_MS,
					page.locator("text=/Tus Negocios/i"),
					page.locator("text=/Negocio/i"));
			final boolean agregarNegocioBtn = isVisibleAny(DEFAULT_TIMEOUT_MS,
					page.locator("role=button[name=/Agregar Negocio/i]"),
					page.locator("text=/Agregar Negocio/i"));
			final boolean negociosQuota = isVisibleAny(DEFAULT_TIMEOUT_MS,
					page.locator("text=/Tienes\\s+2\\s+de\\s+3\\s+negocios/i"));
			return businessListVisible && agregarNegocioBtn && negociosQuota;
		} catch (PlaywrightException e) {
			return false;
		}
	}

	private boolean stepValidateLegalLink(final BrowserContext context, final Page appPage, final String linkRegex,
			final String screenshotPrefix, final Path evidenceDir, final Map<String, String> legalUrls) {
		try {
			final Locator legalLink = firstVisibleOrThrow(
					appPage.locator("role=link[name=/" + linkRegex + "/i]"),
					appPage.locator("text=/" + linkRegex + "/i"));

			final Set<Page> beforePages = new HashSet<>(context.pages());
			final String appUrlBefore = appPage.url();
			clickAndWait(legalLink, appPage);

			Page legalPage = waitForNewPage(context, beforePages, 5000);
			if (legalPage == null) {
				legalPage = appPage;
			}
			waitForUi(legalPage);

			final boolean headingVisible = isVisibleAny(DEFAULT_TIMEOUT_MS,
					legalPage.locator("role=heading[name=/" + linkRegex + "/i]"),
					legalPage.locator("text=/" + linkRegex + "/i"));
			final String bodyText = legalPage.locator("body").innerText();
			final boolean legalContentVisible = bodyText != null && bodyText.trim().length() > 120;

			screenshot(legalPage, evidenceDir.resolve("0" + ("terminos".equals(screenshotPrefix) ? "5" : "6")
					+ "-" + screenshotPrefix + "-legal-page.png"), true);
			legalUrls.put(screenshotPrefix, legalPage.url());

			if (legalPage != appPage) {
				legalPage.close();
				appPage.bringToFront();
			} else if (!appUrlBefore.equals(appPage.url())) {
				try {
					appPage.goBack(new Page.GoBackOptions().setTimeout((double) DEFAULT_TIMEOUT_MS));
				} catch (PlaywrightException ignored) {
					navigateAndWait(appPage, appUrlBefore);
				}
			}
			waitForUi(appPage);
			return headingVisible && legalContentVisible;
		} catch (PlaywrightException e) {
			return false;
		}
	}

	private void writeFinalReport(final LinkedHashMap<String, Boolean> report, final Map<String, String> legalUrls,
			final Path reportPath) throws IOException {
		final StringBuilder sb = new StringBuilder();
		sb.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		sb.append("Final Report").append(System.lineSeparator());
		sb.append("--------------------------------").append(System.lineSeparator());

		for (Map.Entry<String, Boolean> entry : report.entrySet()) {
			sb.append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL").append(System.lineSeparator());
		}

		sb.append(System.lineSeparator()).append("Captured URLs").append(System.lineSeparator());
		sb.append("Términos y Condiciones: ").append(legalUrls.getOrDefault("terminos", "N/A"))
				.append(System.lineSeparator());
		sb.append("Política de Privacidad: ").append(legalUrls.getOrDefault("privacidad", "N/A"))
				.append(System.lineSeparator());

		Files.writeString(reportPath, sb.toString());
		System.out.println(sb);
	}

	private static Path createEvidenceDir() throws IOException {
		final String runId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path dir = Path.of("target", "saleads-evidence", "saleads-mi-negocio-" + runId);
		Files.createDirectories(dir);
		return dir;
	}

	private static void navigateAndWait(final Page page, final String url) {
		page.navigate(url);
		waitForUi(page);
	}

	private static void clickAndWait(final Locator locator, final Page page) {
		locator.first().click(new Locator.ClickOptions().setTimeout((double) DEFAULT_TIMEOUT_MS));
		waitForUi(page);
	}

	private static void waitForUi(final Page page) {
		try {
			page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(8000));
		} catch (PlaywrightException ignored) {
			page.waitForTimeout(700);
		}
	}

	private static boolean isVisibleAny(final int timeoutMs, final Locator... locators) {
		for (Locator locator : locators) {
			if (isVisible(locator, timeoutMs)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isVisible(final Locator locator, final int timeoutMs) {
		try {
			locator.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE)
					.setTimeout((double) timeoutMs));
			return true;
		} catch (PlaywrightException e) {
			return false;
		}
	}

	private static Locator firstVisibleOrNull(final Locator... locators) {
		for (Locator locator : locators) {
			if (isVisible(locator, 3000)) {
				return locator.first();
			}
		}
		return null;
	}

	private static Locator firstVisibleOrThrow(final Locator... locators) {
		Locator locator = firstVisibleOrNull(locators);
		if (locator == null) {
			throw new PlaywrightException("None of the expected locators became visible.");
		}
		return locator;
	}

	private static Page waitForNewPage(final BrowserContext context, final Set<Page> beforePages, final int timeoutMs) {
		long start = System.currentTimeMillis();
		while (System.currentTimeMillis() - start < timeoutMs) {
			for (Page page : context.pages()) {
				if (!beforePages.contains(page)) {
					return page;
				}
			}
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return null;
			}
		}
		return null;
	}

	private static void screenshot(final Page page, final Path targetPath, final boolean fullPage) {
		try {
			Files.createDirectories(targetPath.getParent());
			page.screenshot(new Page.ScreenshotOptions().setPath(targetPath).setFullPage(fullPage));
		} catch (Exception e) {
			throw new RuntimeException("Unable to capture screenshot: " + targetPath, e);
		}
	}

	private static String trimToNull(final String value) {
		if (value == null || value.trim().isEmpty()) {
			return null;
		}
		return value.trim();
	}

	private static String readConfig(final String key) {
		final String envValue = System.getenv(key);
		if (envValue != null) {
			return envValue;
		}
		return System.getProperty(key);
	}

	private static String escapeForRegex(final String value) {
		return value
				.replace("\\", "\\\\")
				.replace(".", "\\.")
				.replace("+", "\\+")
				.replace("*", "\\*")
				.replace("?", "\\?")
				.replace("^", "\\^")
				.replace("$", "\\$")
				.replace("(", "\\(")
				.replace(")", "\\)")
				.replace("[", "\\[")
				.replace("]", "\\]")
				.replace("{", "\\{")
				.replace("}", "\\}")
				.replace("|", "\\|");
	}
}
