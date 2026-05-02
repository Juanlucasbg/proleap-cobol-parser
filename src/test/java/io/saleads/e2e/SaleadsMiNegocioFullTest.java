package io.saleads.e2e;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final List<String> REPORT_FIELDS = Arrays.asList(
			"Login",
			"Mi Negocio menu",
			"Agregar Negocio modal",
			"Administrar Negocios view",
			"Información General",
			"Detalles de la Cuenta",
			"Tus Negocios",
			"Términos y Condiciones",
			"Política de Privacidad");

	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, StepResult> results = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private Path reportPath;
	private String appWindowHandle;

	private final String loginUrl = firstNonBlank(
			System.getProperty("saleads.login.url"),
			System.getenv("SALEADS_LOGIN_URL"),
			System.getProperty("saleads.base.url"),
			System.getenv("SALEADS_BASE_URL"));
	private final String googleAccountEmail = firstNonBlank(
			System.getProperty("saleads.google.account"),
			System.getenv("SALEADS_GOOGLE_ACCOUNT"),
			"juanlucasbarbiergarzon@gmail.com");

	@Before
	public void setUp() throws Exception {
		for (final String field : REPORT_FIELDS) {
			results.put(field, StepResult.fail("Not executed."));
		}

		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
				.format(LocalDateTime.now());
		evidenceDir = Paths.get("target", "saleads-mi-negocio-evidence", timestamp);
		Files.createDirectories(evidenceDir);
		reportPath = evidenceDir.resolve("final-report.md");

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--disable-gpu");
		options.addArguments("--headless=new");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(25));

		if (loginUrl != null) {
			driver.get(loginUrl);
			waitForUiToLoad();
		}

		appWindowHandle = driver.getWindowHandle();
	}

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		try {
			final boolean loginOk = stepLogin();
			if (!loginOk) {
				markBlocked("Mi Negocio menu", "Blocked because login did not succeed.");
				markBlocked("Agregar Negocio modal", "Blocked because login did not succeed.");
				markBlocked("Administrar Negocios view", "Blocked because login did not succeed.");
				markBlocked("Información General", "Blocked because login did not succeed.");
				markBlocked("Detalles de la Cuenta", "Blocked because login did not succeed.");
				markBlocked("Tus Negocios", "Blocked because login did not succeed.");
				markBlocked("Términos y Condiciones", "Blocked because login did not succeed.");
				markBlocked("Política de Privacidad", "Blocked because login did not succeed.");
				return;
			}

			stepOpenMiNegocioMenu();
			stepAgregarNegocioModal();
			final boolean administrarOk = stepAdministrarNegociosView();

			if (administrarOk) {
				stepInformacionGeneral();
				stepDetallesCuenta();
				stepTusNegocios();
				stepLegalDocument(
						"Términos y Condiciones",
						Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"),
						Arrays.asList("Términos y Condiciones", "Terminos y Condiciones"));
				stepLegalDocument(
						"Política de Privacidad",
						Arrays.asList("Política de Privacidad", "Politica de Privacidad"),
						Arrays.asList("Política de Privacidad", "Politica de Privacidad"));
			} else {
				markBlocked("Información General", "Blocked because 'Administrar Negocios' did not load.");
				markBlocked("Detalles de la Cuenta", "Blocked because 'Administrar Negocios' did not load.");
				markBlocked("Tus Negocios", "Blocked because 'Administrar Negocios' did not load.");
				markBlocked("Términos y Condiciones", "Blocked because 'Administrar Negocios' did not load.");
				markBlocked("Política de Privacidad", "Blocked because 'Administrar Negocios' did not load.");
			}
		} finally {
			writeFinalReport();
		}

		final List<String> failed = new ArrayList<>();
		for (final String field : REPORT_FIELDS) {
			if (!results.get(field).passed) {
				failed.add(field);
			}
		}

		Assert.assertTrue(
				"One or more validations failed: " + failed + ". See report: " + reportPath.toAbsolutePath(),
				failed.isEmpty());
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	private boolean stepLogin() {
		try {
			if (loginUrl == null && isBlank(driver.getCurrentUrl())) {
				markFail("Login",
						"Login page URL not configured. Provide SALEADS_LOGIN_URL or saleads.login.url.");
				return false;
			}

			clickFirstVisibleByText(Arrays.asList(
					"Sign in with Google",
					"Iniciar sesión con Google",
					"Continuar con Google",
					"Acceder con Google",
					"Google"));
			waitForUiToLoad();
			handleGoogleAccountSelectionIfNeeded();

			final boolean appVisible = isAnyTextVisible(
					Arrays.asList("Negocio", "Mi Negocio", "Dashboard", "Panel"),
					Duration.ofSeconds(60));
			final boolean sidebarVisible = isAnyTextVisible(
					Arrays.asList("Negocio", "Mi Negocio"),
					Duration.ofSeconds(40));

			if (appVisible && sidebarVisible) {
				final Path screenshot = captureScreenshot("01-dashboard-loaded");
				markPass("Login", "Dashboard and left sidebar are visible.", screenshot, driver.getCurrentUrl());
				return true;
			}

			markFail("Login", "Main application UI or sidebar was not detected after Google login.");
			return false;
		} catch (final Exception e) {
			markFail("Login", "Login with Google failed: " + e.getMessage());
			return false;
		}
	}

	private boolean stepOpenMiNegocioMenu() {
		try {
			clickIfVisibleByText("Negocio");
			waitForUiToLoad();
			clickFirstVisibleByText(Arrays.asList("Mi Negocio"));
			waitForUiToLoad();

			final boolean agregar = isTextVisible("Agregar Negocio", Duration.ofSeconds(15));
			final boolean administrar = isTextVisible("Administrar Negocios", Duration.ofSeconds(15));

			if (agregar && administrar) {
				final Path screenshot = captureScreenshot("02-mi-negocio-menu-expanded");
				markPass("Mi Negocio menu", "Submenu expanded with expected options.", screenshot, null);
				return true;
			}

			markFail("Mi Negocio menu",
					"Expected menu options were not visible. Agregar=" + agregar + ", Administrar=" + administrar + ".");
			return false;
		} catch (final Exception e) {
			markFail("Mi Negocio menu", "Could not open Mi Negocio menu: " + e.getMessage());
			return false;
		}
	}

	private boolean stepAgregarNegocioModal() {
		try {
			clickFirstVisibleByText(Arrays.asList("Agregar Negocio"));
			waitForUiToLoad();

			final boolean title = isAnyTextVisible(
					Arrays.asList("Crear Nuevo Negocio"),
					Duration.ofSeconds(15));
			final boolean negocioInput = isElementVisible(
					By.xpath("//input[contains(@placeholder,'Nombre del Negocio') "
							+ "or contains(@aria-label,'Nombre del Negocio') "
							+ "or @name='businessName' "
							+ "or @id='businessName']"),
					Duration.ofSeconds(15))
					|| isElementVisible(
							By.xpath("//label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]"),
							Duration.ofSeconds(15));
			final boolean quota = isAnyTextVisible(Arrays.asList("Tienes 2 de 3 negocios"), Duration.ofSeconds(15));
			final boolean cancelar = isAnyTextVisible(Arrays.asList("Cancelar"), Duration.ofSeconds(15));
			final boolean crear = isAnyTextVisible(Arrays.asList("Crear Negocio"), Duration.ofSeconds(15));

			if (title && negocioInput && quota && cancelar && crear) {
				typeInBusinessNameIfPresent("Negocio Prueba Automatización");
				final Path screenshot = captureScreenshot("03-agregar-negocio-modal");
				clickIfVisibleByText("Cancelar");
				waitForUiToLoad();
				markPass("Agregar Negocio modal", "Modal displayed with all required fields and actions.", screenshot,
						null);
				return true;
			}

			markFail("Agregar Negocio modal",
					"Modal validation failed. title=" + title
							+ ", input=" + negocioInput
							+ ", quota=" + quota
							+ ", cancelar=" + cancelar
							+ ", crear=" + crear + ".");
			return false;
		} catch (final Exception e) {
			markFail("Agregar Negocio modal", "Could not validate Agregar Negocio modal: " + e.getMessage());
			return false;
		}
	}

	private boolean stepAdministrarNegociosView() {
		try {
			clickIfVisibleByText("Mi Negocio");
			waitForUiToLoad();
			clickFirstVisibleByText(Arrays.asList("Administrar Negocios"));
			waitForUiToLoad();

			final boolean info = isTextVisible("Información General", Duration.ofSeconds(25));
			final boolean detalles = isTextVisible("Detalles de la Cuenta", Duration.ofSeconds(25));
			final boolean negocios = isTextVisible("Tus Negocios", Duration.ofSeconds(25));
			final boolean legal = isTextVisible("Sección Legal", Duration.ofSeconds(25));

			if (info && detalles && negocios && legal) {
				final Path screenshot = captureFullPageScreenshot("04-administrar-negocios-page-full");
				markPass("Administrar Negocios view", "Account page sections are visible.", screenshot,
						driver.getCurrentUrl());
				return true;
			}

			markFail("Administrar Negocios view",
					"Missing expected sections. info=" + info
							+ ", detalles=" + detalles
							+ ", negocios=" + negocios
							+ ", legal=" + legal + ".");
			return false;
		} catch (final Exception e) {
			markFail("Administrar Negocios view", "Could not load Administrar Negocios: " + e.getMessage());
			return false;
		}
	}

	private boolean stepInformacionGeneral() {
		try {
			final String body = getBodyText();
			final boolean userNameVisible = hasLikelyUserNameVisibleInInfoSection();
			final boolean emailVisible = EMAIL_PATTERN.matcher(body).find();
			final boolean businessPlan = isTextVisible("BUSINESS PLAN", Duration.ofSeconds(10));
			final boolean cambiarPlan = isTextVisible("Cambiar Plan", Duration.ofSeconds(10));

			if (userNameVisible && emailVisible && businessPlan && cambiarPlan) {
				markPass("Información General", "User data and plan controls are visible.", null, null);
				return true;
			}

			markFail("Información General",
					"Validation failed. userName=" + userNameVisible
							+ ", email=" + emailVisible
							+ ", businessPlan=" + businessPlan
							+ ", cambiarPlan=" + cambiarPlan + ".");
			return false;
		} catch (final Exception e) {
			markFail("Información General", "Could not validate Información General: " + e.getMessage());
			return false;
		}
	}

	private boolean stepDetallesCuenta() {
		try {
			final boolean cuentaCreada = isTextVisible("Cuenta creada", Duration.ofSeconds(10));
			final boolean estadoActivo = isAnyTextVisible(Arrays.asList("Estado activo", "Estado Activo"),
					Duration.ofSeconds(10));
			final boolean idioma = isAnyTextVisible(Arrays.asList("Idioma seleccionado", "Idioma Seleccionado"),
					Duration.ofSeconds(10));

			if (cuentaCreada && estadoActivo && idioma) {
				markPass("Detalles de la Cuenta", "All account details labels are visible.", null, null);
				return true;
			}

			markFail("Detalles de la Cuenta",
					"Validation failed. cuentaCreada=" + cuentaCreada
							+ ", estadoActivo=" + estadoActivo
							+ ", idioma=" + idioma + ".");
			return false;
		} catch (final Exception e) {
			markFail("Detalles de la Cuenta", "Could not validate account details section: " + e.getMessage());
			return false;
		}
	}

	private boolean stepTusNegocios() {
		try {
			final boolean section = isTextVisible("Tus Negocios", Duration.ofSeconds(10));
			final boolean agregar = isTextVisible("Agregar Negocio", Duration.ofSeconds(10));
			final boolean quota = isTextVisible("Tienes 2 de 3 negocios", Duration.ofSeconds(10));

			if (section && agregar && quota) {
				markPass("Tus Negocios", "Business list and controls are visible.", null, null);
				return true;
			}

			markFail("Tus Negocios",
					"Validation failed. section=" + section + ", agregar=" + agregar + ", quota=" + quota + ".");
			return false;
		} catch (final Exception e) {
			markFail("Tus Negocios", "Could not validate Tus Negocios section: " + e.getMessage());
			return false;
		}
	}

	private boolean stepLegalDocument(
			final String reportField,
			final List<String> linkTexts,
			final List<String> headingTexts) {
		try {
			final Set<String> handlesBefore = driver.getWindowHandles();
			final String previousUrl = driver.getCurrentUrl();
			clickFirstVisibleByText(linkTexts);
			waitForUiToLoad();

			final String legalHandle = switchToNewHandleIfOpened(handlesBefore).orElse(driver.getWindowHandle());
			driver.switchTo().window(legalHandle);
			waitForUiToLoad();

			final boolean headingVisible = isAnyTextVisible(headingTexts, Duration.ofSeconds(20));
			final String legalText = getBodyText();
			final boolean legalContentVisible = legalText != null && legalText.trim().length() > 200;
			final String finalUrl = driver.getCurrentUrl();
			final Path screenshot = captureScreenshot("legal-" + toSlug(reportField));

			if (headingVisible && legalContentVisible) {
				markPass(reportField, "Legal page content loaded correctly.", screenshot, finalUrl);
			} else {
				markFail(reportField,
						"Legal page validation failed. headingVisible=" + headingVisible
								+ ", legalContentVisible=" + legalContentVisible + ".");
			}

			returnToApplicationTab(handlesBefore, previousUrl);
			return results.get(reportField).passed;
		} catch (final Exception e) {
			markFail(reportField, "Could not validate legal page: " + e.getMessage());
			return false;
		}
	}

	private void handleGoogleAccountSelectionIfNeeded() throws Exception {
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(90).toMillis();

		while (System.currentTimeMillis() < deadline) {
			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				final String url = driver.getCurrentUrl();
				final boolean googleContext = url != null && url.contains("accounts.google.com");

				if (googleContext) {
					clickIfVisibleByText(googleAccountEmail);
					clickIfVisibleByText("Continuar");
					clickIfVisibleByText("Continue");
					waitForUiToLoad();
				}
			}

			driver.switchTo().window(appWindowHandle);
			if (isAnyTextVisible(Arrays.asList("Negocio", "Mi Negocio", "Dashboard", "Panel"), Duration.ofSeconds(2))) {
				return;
			}
		}
	}

	private Optional<String> switchToNewHandleIfOpened(final Set<String> handlesBefore) {
		final long deadline = System.currentTimeMillis() + Duration.ofSeconds(10).toMillis();
		while (System.currentTimeMillis() < deadline) {
			final Set<String> afterClick = driver.getWindowHandles();
			if (afterClick.size() > handlesBefore.size()) {
				for (final String handle : afterClick) {
					if (!handlesBefore.contains(handle)) {
						return Optional.of(handle);
					}
				}
			}
			sleepSilently(300);
		}
		return Optional.empty();
	}

	private void returnToApplicationTab(final Set<String> handlesBefore, final String previousUrl) {
		final Set<String> currentHandles = new LinkedHashSet<>(driver.getWindowHandles());
		if (currentHandles.size() > handlesBefore.size()) {
			for (final String handle : currentHandles) {
				if (!handlesBefore.contains(handle)) {
					driver.switchTo().window(handle);
					driver.close();
				}
			}
			driver.switchTo().window(appWindowHandle);
			waitForUiToLoad();
			return;
		}

		if (!driver.getCurrentUrl().equals(previousUrl)) {
			driver.navigate().back();
			waitForUiToLoad();
		}

		driver.switchTo().window(appWindowHandle);
		waitForUiToLoad();
	}

	private void typeInBusinessNameIfPresent(final String value) {
		final List<By> inputLocators = Arrays.asList(
				By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"),
				By.xpath("//label[contains(normalize-space(.),'Nombre del Negocio')]/following::input[1]"));

		for (final By locator : inputLocators) {
			final List<WebElement> candidates = driver.findElements(locator);
			for (final WebElement candidate : candidates) {
				if (candidate.isDisplayed()) {
					candidate.click();
					candidate.clear();
					candidate.sendKeys(value);
					return;
				}
			}
		}
	}

	private void clickFirstVisibleByText(final List<String> texts) {
		for (final String text : texts) {
			if (clickIfVisibleByText(text)) {
				return;
			}
		}
		throw new IllegalStateException("Unable to click any element by visible text: " + texts);
	}

	private boolean clickIfVisibleByText(final String text) {
		final By locator = By.xpath("//*[contains(normalize-space(.), " + toXPathLiteral(text) + ")]");
		final List<WebElement> matches = driver.findElements(locator);
		for (final WebElement element : matches) {
			if (element.isDisplayed()) {
				try {
					scrollIntoView(element);
					wait.until(ExpectedConditions.elementToBeClickable(element)).click();
					waitForUiToLoad();
					return true;
				} catch (final Exception clickException) {
					try {
						scrollIntoView(element);
						((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
						waitForUiToLoad();
						return true;
					} catch (final Exception ignored) {
						// Try next candidate.
					}
				}
			}
		}
		return false;
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		return isAnyTextVisible(Arrays.asList(text), timeout);
	}

	private boolean isAnyTextVisible(final List<String> texts, final Duration timeout) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		for (final String text : texts) {
			try {
				shortWait.until(ExpectedConditions
						.visibilityOfElementLocated(By.xpath("//*[contains(normalize-space(.), " + toXPathLiteral(text)
								+ ")]")));
				return true;
			} catch (final TimeoutException ignored) {
				// Try next text variation.
			}
		}
		return false;
	}

	private boolean isElementVisible(final By locator, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(ExpectedConditions.visibilityOfElementLocated(locator));
			return true;
		} catch (final TimeoutException timeoutException) {
			return false;
		}
	}

	private void waitForUiToLoad() {
		try {
			wait.until(driver -> "complete".equals(
					String.valueOf(((JavascriptExecutor) driver).executeScript("return document.readyState"))));
		} catch (final Exception ignored) {
			// Continue even if readystate check is not available.
		}
		sleepSilently(500);
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript(
				"arguments[0].scrollIntoView({block: 'center', inline: 'center'});",
				element);
	}

	private String getBodyText() {
		return driver.findElement(By.tagName("body")).getText();
	}

	private boolean hasLikelyUserNameVisibleInInfoSection() {
		final String body = getBodyText();
		final int headingIndex = body.indexOf("Información General");
		if (headingIndex < 0) {
			return false;
		}

		final String slice = body.substring(headingIndex);
		final String[] lines = slice.split("\\R");
		for (final String line : lines) {
			final String cleanLine = line.trim();
			if (cleanLine.length() < 4) {
				continue;
			}
			if (cleanLine.contains("@")
					|| cleanLine.contains("Información General")
					|| cleanLine.contains("BUSINESS PLAN")
					|| cleanLine.contains("Cambiar Plan")) {
				continue;
			}
			final Matcher matcher = Pattern.compile("[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}(\\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,})+")
					.matcher(cleanLine);
			if (matcher.find()) {
				return true;
			}
		}
		return false;
	}

	private Path captureScreenshot(final String name) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path target = evidenceDir.resolve(name + ".png");
		Files.copy(screenshot.toPath(), target);
		return target.toAbsolutePath();
	}

	private Path captureFullPageScreenshot(final String name) throws IOException {
		final Dimension originalSize = driver.manage().window().getSize();
		try {
			final long fullHeight = Math.max(
					1080L,
					Math.min(4500L, getDocumentHeight()));
			driver.manage().window().setSize(new Dimension(originalSize.width, (int) fullHeight));
			waitForUiToLoad();
			return captureScreenshot(name);
		} finally {
			driver.manage().window().setSize(originalSize);
			waitForUiToLoad();
		}
	}

	private long getDocumentHeight() {
		try {
			final Object value = ((JavascriptExecutor) driver).executeScript(
					"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);");
			if (value instanceof Number) {
				return ((Number) value).longValue();
			}
		} catch (final Exception ignored) {
			// Fall back to a default value.
		}
		return 2000L;
	}

	private void markPass(final String field, final String details, final Path screenshot, final String url) {
		final StepResult result = StepResult.pass(details);
		if (screenshot != null) {
			result.screenshot = screenshot.toString();
		}
		if (url != null && !url.isBlank()) {
			result.url = url;
		}
		results.put(field, result);
	}

	private void markFail(final String field, final String details) {
		results.put(field, StepResult.fail(details));
	}

	private void markBlocked(final String field, final String details) {
		if (!results.get(field).passed) {
			results.put(field, StepResult.fail(details));
		}
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("# SaleADS Mi Negocio Full Test Report\n\n");
		report.append("- Generated at: ").append(LocalDateTime.now()).append("\n");
		report.append("- Evidence directory: ").append(evidenceDir.toAbsolutePath()).append("\n");
		report.append("- Google account target: ").append(googleAccountEmail).append("\n");
		report.append("- Login URL source: ").append(loginUrl == null ? "Not provided" : loginUrl).append("\n\n");
		report.append("| Validation | Result | Details | Screenshot | Final URL |\n");
		report.append("|---|---|---|---|---|\n");

		for (final String field : REPORT_FIELDS) {
			final StepResult result = results.get(field);
			report.append("| ")
					.append(field)
					.append(" | ")
					.append(result.passed ? "PASS" : "FAIL")
					.append(" | ")
					.append(escapeForTable(result.details))
					.append(" | ")
					.append(result.screenshot == null ? "-" : result.screenshot)
					.append(" | ")
					.append(result.url == null ? "-" : result.url)
					.append(" |\n");
		}

		Files.write(reportPath, report.toString().getBytes(StandardCharsets.UTF_8));
	}

	private String escapeForTable(final String value) {
		if (value == null) {
			return "";
		}
		return value.replace("|", "\\|").replace("\n", " ").trim();
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		final String[] parts = value.split("'");
		final StringBuilder literal = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				literal.append(", \"'\", ");
			}
			literal.append("'").append(parts[i]).append("'");
		}
		literal.append(")");
		return literal.toString();
	}

	private String toSlug(final String value) {
		return value.toLowerCase()
				.replace("á", "a")
				.replace("é", "e")
				.replace("í", "i")
				.replace("ó", "o")
				.replace("ú", "u")
				.replace("ñ", "n")
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("(^-|-$)", "");
	}

	private static void sleepSilently(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private static boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty();
	}

	private static String firstNonBlank(final String... candidates) {
		for (final String candidate : candidates) {
			if (!isBlank(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private static class StepResult {
		private final boolean passed;
		private final String details;
		private String screenshot;
		private String url;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult pass(final String details) {
			return new StepResult(true, details);
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details);
		}
	}
}
