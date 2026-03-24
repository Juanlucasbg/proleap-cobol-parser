package io.proleap.cobol.e2e;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Assume;
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

public class SaleadsMiNegocioWorkflowE2ETest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final Duration LONG_TIMEOUT = Duration.ofSeconds(60);
	private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
			.withZone(ZoneOffset.UTC);
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final List<String> REPORT_FIELDS = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	@Test
	public void saleadsMiNegocioFullWorkflowTest() throws Exception {
		final boolean enabled = Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false"));
		Assume.assumeTrue("Enable with -Dsaleads.e2e.enabled=true", enabled);

		final String loginUrl = firstNonBlank(System.getProperty("saleads.login.url"), System.getenv("SALEADS_LOGIN_URL"));
		Assume.assumeTrue("Provide login URL via -Dsaleads.login.url or SALEADS_LOGIN_URL", loginUrl != null);

		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.e2e.headless", "true"));
		final Path outputDir = Paths.get("target", "saleads-e2e");
		final Path screenshotsDir = outputDir.resolve("screenshots");
		Files.createDirectories(screenshotsDir);

		final LinkedHashMap<String, Boolean> report = new LinkedHashMap<>();
		final LinkedHashMap<String, String> reportNotes = new LinkedHashMap<>();
		final LinkedHashMap<String, String> legalUrls = new LinkedHashMap<>();
		final List<String> screenshots = new ArrayList<>();
		for (final String field : REPORT_FIELDS) {
			report.put(field, false);
			reportNotes.put(field, "Not executed.");
		}

		WebDriver driver = null;
		WebDriverWait wait = null;
		final Instant startedAt = Instant.now();
		try {
			driver = buildDriver(headless);
			wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
			final WebDriver activeDriver = driver;
			final WebDriverWait activeWait = wait;
			activeDriver.manage().window().setSize(new Dimension(1920, 1080));
			activeDriver.get(loginUrl);
			waitForUiLoad(activeDriver);

			runStep("Login", report, reportNotes, () -> {
				final boolean clickedGoogle = clickByVisibleText(activeDriver, activeWait, Arrays.asList("Sign in with Google",
						"Iniciar sesión con Google", "Iniciar sesion con Google", "Continuar con Google", "Google"),
						LONG_TIMEOUT);
				if (!clickedGoogle) {
					return false;
				}

				waitForUiLoad(activeDriver);
				selectGoogleAccountIfVisible(activeDriver, activeWait, GOOGLE_ACCOUNT_EMAIL);
				final boolean dashboardLoaded = waitForAnyVisibleText(activeDriver,
						Arrays.asList("Mi Negocio", "Negocio", "Administrar Negocios"), LONG_TIMEOUT);
				final boolean sidebarVisible = waitForVisible(activeDriver, By.cssSelector("aside, nav"), DEFAULT_TIMEOUT);
				screenshots.add(takeScreenshot(activeDriver, screenshotsDir, "01_dashboard_loaded"));
				return dashboardLoaded && sidebarVisible;
			});

			runStep("Mi Negocio menu", report, reportNotes, () -> {
				ensureMiNegocioExpanded(activeDriver, activeWait);
				final boolean agregarVisible = isAnyVisibleText(activeDriver, Arrays.asList("Agregar Negocio"));
				final boolean administrarVisible = isAnyVisibleText(activeDriver, Arrays.asList("Administrar Negocios"));
				screenshots.add(takeScreenshot(activeDriver, screenshotsDir, "02_mi_negocio_expanded"));
				return agregarVisible && administrarVisible;
			});

			runStep("Agregar Negocio modal", report, reportNotes, () -> {
				final boolean clickedAgregar = clickByVisibleText(activeDriver, activeWait, Arrays.asList("Agregar Negocio"),
						DEFAULT_TIMEOUT);
				if (!clickedAgregar) {
					return false;
				}

				final boolean titleVisible = waitForAnyVisibleText(activeDriver, Arrays.asList("Crear Nuevo Negocio"),
						DEFAULT_TIMEOUT);
				final boolean nameInputVisible = waitForVisible(activeDriver,
						By.xpath("//input[contains(@placeholder, \"Nombre\") and contains(@placeholder, \"Negocio\")]"
								+ " | //input[contains(@name, \"negocio\") or contains(@id, \"negocio\")]"),
						DEFAULT_TIMEOUT);
				final boolean limitTextVisible = waitForAnyVisibleText(activeDriver, Arrays.asList("Tienes 2 de 3 negocios"),
						DEFAULT_TIMEOUT);
				final boolean cancelVisible = isAnyVisibleText(activeDriver, Arrays.asList("Cancelar"));
				final boolean createVisible = isAnyVisibleText(activeDriver, Arrays.asList("Crear Negocio"));
				screenshots.add(takeScreenshot(activeDriver, screenshotsDir, "03_agregar_negocio_modal"));

				// Optional action requested in the workflow.
				typeInNombreDelNegocioIfPossible(activeDriver, "Negocio Prueba Automatización");
				clickByVisibleText(activeDriver, activeWait, Arrays.asList("Cancelar"), SHORT_TIMEOUT);

				return titleVisible && nameInputVisible && limitTextVisible && cancelVisible && createVisible;
			});

			runStep("Administrar Negocios view", report, reportNotes, () -> {
				ensureMiNegocioExpanded(activeDriver, activeWait);
				final boolean clickedAdministrar = clickByVisibleText(activeDriver, activeWait, Arrays.asList("Administrar Negocios"),
						DEFAULT_TIMEOUT);
				if (!clickedAdministrar) {
					return false;
				}

				final boolean infoGeneral = waitForAnyVisibleText(activeDriver, Arrays.asList("Información General"),
						DEFAULT_TIMEOUT);
				final boolean detallesCuenta = waitForAnyVisibleText(activeDriver, Arrays.asList("Detalles de la Cuenta"),
						DEFAULT_TIMEOUT);
				final boolean tusNegocios = waitForAnyVisibleText(activeDriver, Arrays.asList("Tus Negocios"), DEFAULT_TIMEOUT);
				final boolean legalSection = waitForAnyVisibleText(activeDriver, Arrays.asList("Sección Legal"), DEFAULT_TIMEOUT);
				screenshots.add(takeFullPageScreenshot(activeDriver, screenshotsDir, "04_administrar_negocios_view"));
				return infoGeneral && detallesCuenta && tusNegocios && legalSection;
			});

			runStep("Información General", report, reportNotes, () -> {
				final boolean userNameVisible = anyElementExists(activeDriver,
						By.xpath("//section//*[contains(@class, \"name\") or contains(@class, \"user\")]"
								+ " | //*[contains(@class, \"profile\") or contains(@class, \"avatar\")]"));
				final boolean userEmailVisible = anyElementExists(activeDriver,
						By.xpath("//*[contains(text(), \"@\") and (self::span or self::div or self::p or self::a)]"));
				final boolean businessPlanVisible = isAnyVisibleText(activeDriver,
						Arrays.asList("BUSINESS PLAN", "Business Plan"));
				final boolean cambiarPlanVisible = isAnyVisibleText(activeDriver, Arrays.asList("Cambiar Plan"));
				return userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible;
			});

			runStep("Detalles de la Cuenta", report, reportNotes, () -> {
				final boolean cuentaCreadaVisible = isAnyVisibleText(activeDriver, Arrays.asList("Cuenta creada"));
				final boolean estadoVisible = isAnyVisibleText(activeDriver, Arrays.asList("Estado activo", "Estado", "Activo"));
				final boolean idiomaVisible = isAnyVisibleText(activeDriver,
						Arrays.asList("Idioma seleccionado", "Idioma"));
				return cuentaCreadaVisible && estadoVisible && idiomaVisible;
			});

			runStep("Tus Negocios", report, reportNotes, () -> {
				final boolean businessListVisible = isAnyVisibleText(activeDriver, Arrays.asList("Tus Negocios"))
						&& anyElementExists(activeDriver,
								By.xpath("//section//*[contains(., \"Negocio\") or contains(., \"negocio\")]"));
				final boolean addBusinessButton = isAnyVisibleText(activeDriver, Arrays.asList("Agregar Negocio"));
				final boolean limitText = isAnyVisibleText(activeDriver, Arrays.asList("Tienes 2 de 3 negocios"));
				return businessListVisible && addBusinessButton && limitText;
			});

			runStep("Términos y Condiciones", report, reportNotes, () -> {
				return validateLegalLink(activeDriver, activeWait, "Términos y Condiciones", "Términos y Condiciones",
						"05_terminos_condiciones", screenshotsDir, screenshots, legalUrls);
			});

			runStep("Política de Privacidad", report, reportNotes, () -> {
				return validateLegalLink(activeDriver, activeWait, "Política de Privacidad", "Política de Privacidad",
						"06_politica_privacidad", screenshotsDir, screenshots, legalUrls);
			});
		} finally {
			final Path reportFile = writeFinalReport(outputDir, startedAt, report, reportNotes, legalUrls, screenshots,
					loginUrl);
			if (driver != null) {
				driver.quit();
			}

			final boolean allPassed = report.values().stream().allMatch(Boolean::booleanValue);
			Assert.assertTrue("Workflow validation failed. Final report: " + reportFile.toAbsolutePath(), allPassed);
		}
	}

	private static WebDriver buildDriver(final boolean headless) {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--disable-gpu");
		options.addArguments("--lang=es-ES");
		if (headless) {
			options.addArguments("--headless=new");
		}
		return new ChromeDriver(options);
	}

	private static void runStep(final String stepName, final Map<String, Boolean> report,
			final Map<String, String> reportNotes, final Supplier<Boolean> action) {
		try {
			final boolean passed = Boolean.TRUE.equals(action.get());
			report.put(stepName, passed);
			reportNotes.put(stepName, passed ? "PASS" : "FAIL");
		} catch (final Exception ex) {
			report.put(stepName, false);
			reportNotes.put(stepName, "ERROR: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
		}
	}

	private static boolean validateLegalLink(final WebDriver driver, final WebDriverWait wait, final String linkText,
			final String expectedHeading, final String screenshotName, final Path screenshotsDir,
			final List<String> screenshots, final Map<String, String> legalUrls) {
		final String appWindow = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();
		final String appUrl = driver.getCurrentUrl();

		final boolean clicked = clickByVisibleText(driver, wait, Arrays.asList(linkText), DEFAULT_TIMEOUT);
		if (!clicked) {
			return false;
		}

		waitForUiLoad(driver);
		boolean openedNewTab = false;
		try {
			new WebDriverWait(driver, SHORT_TIMEOUT).until(drv -> drv.getWindowHandles().size() > handlesBefore.size()
					|| !drv.getCurrentUrl().equals(appUrl));
		} catch (final TimeoutException ignored) {
			// The page may have updated in place without changing URL.
		}

		final Set<String> handlesAfter = driver.getWindowHandles();
		if (handlesAfter.size() > handlesBefore.size()) {
			final Set<String> newHandles = handlesAfter.stream().filter(h -> !handlesBefore.contains(h))
					.collect(Collectors.toSet());
			if (!newHandles.isEmpty()) {
				driver.switchTo().window(newHandles.iterator().next());
				openedNewTab = true;
				waitForUiLoad(driver);
			}
		}

		final boolean headingVisible = waitForAnyVisibleText(driver, Arrays.asList(expectedHeading), DEFAULT_TIMEOUT);
		final boolean legalContentVisible = getVisibleBodyText(driver).trim().length() > 120;
		screenshots.add(takeScreenshot(driver, screenshotsDir, screenshotName));
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiLoad(driver);
		} else {
			// Return to the application if navigation happened in the same tab.
			if (!driver.getCurrentUrl().equals(appUrl)) {
				driver.navigate().back();
				waitForUiLoad(driver);
			}
		}

		return headingVisible && legalContentVisible;
	}

	private static void ensureMiNegocioExpanded(final WebDriver driver, final WebDriverWait wait) {
		if (!isAnyVisibleText(driver, Arrays.asList("Agregar Negocio", "Administrar Negocios"))) {
			clickByVisibleText(driver, wait, Arrays.asList("Negocio"), SHORT_TIMEOUT);
			clickByVisibleText(driver, wait, Arrays.asList("Mi Negocio"), SHORT_TIMEOUT);
		}
		waitForUiLoad(driver);
	}

	private static void typeInNombreDelNegocioIfPossible(final WebDriver driver, final String text) {
		final List<By> locators = Arrays.asList(
				By.xpath("//input[contains(@placeholder, \"Nombre del Negocio\")]"),
				By.xpath("//input[contains(@placeholder, \"Nombre\") and contains(@placeholder, \"Negocio\")]"),
				By.xpath("//input[contains(@name, \"negocio\") or contains(@id, \"negocio\")]"));
		for (final By locator : locators) {
			final List<WebElement> elements = driver.findElements(locator);
			for (final WebElement element : elements) {
				if (element.isDisplayed() && element.isEnabled()) {
					element.click();
					element.clear();
					element.sendKeys(text);
					waitForUiLoad(driver);
					return;
				}
			}
		}
	}

	private static void selectGoogleAccountIfVisible(final WebDriver driver, final WebDriverWait wait,
			final String email) {
		final Instant deadline = Instant.now().plusSeconds(25);
		while (Instant.now().isBefore(deadline)) {
			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				final boolean selected = clickByVisibleText(driver, wait, Arrays.asList(email), Duration.ofSeconds(2));
				if (selected) {
					waitForUiLoad(driver);
					return;
				}
			}
			sleep(600);
		}
	}

	private static boolean clickByVisibleText(final WebDriver driver, final WebDriverWait wait, final List<String> texts,
			final Duration timeout) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		for (final String text : texts) {
			try {
				final By locator = By.xpath("//*[self::a or self::button or @role='button' or self::span or self::div]"
						+ "[contains(normalize-space(.), " + xpathLiteral(text) + ")]");
				final WebElement element = localWait.until(drv -> firstDisplayed(drv.findElements(locator)));
				if (element != null) {
					try {
						localWait.until(ExpectedConditions.elementToBeClickable(element)).click();
					} catch (final Exception clickException) {
						((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
					}
					waitForUiLoad(driver);
					return true;
				}
			} catch (final TimeoutException ignored) {
				// Try next candidate text.
			}
		}
		return false;
	}

	private static boolean waitForAnyVisibleText(final WebDriver driver, final List<String> texts, final Duration timeout) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		for (final String text : texts) {
			try {
				final By locator = By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]");
				localWait.until(drv -> {
					final WebElement visible = firstDisplayed(drv.findElements(locator));
					return visible != null ? visible : null;
				});
				return true;
			} catch (final TimeoutException ignored) {
				// Try next text.
			}
		}
		return false;
	}

	private static boolean isAnyVisibleText(final WebDriver driver, final List<String> texts) {
		for (final String text : texts) {
			final By locator = By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]");
			if (firstDisplayed(driver.findElements(locator)) != null) {
				return true;
			}
		}
		return false;
	}

	private static boolean waitForVisible(final WebDriver driver, final By locator, final Duration timeout) {
		try {
			new WebDriverWait(driver, timeout).until(drv -> firstDisplayed(drv.findElements(locator)));
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private static WebElement firstDisplayed(final List<WebElement> elements) {
		for (final WebElement element : elements) {
			try {
				if (element.isDisplayed()) {
					return element;
				}
			} catch (final Exception ignored) {
				// Skip stale or detached elements.
			}
		}
		return null;
	}

	private static boolean anyElementExists(final WebDriver driver, final By locator) {
		return firstDisplayed(driver.findElements(locator)) != null;
	}

	private static String takeScreenshot(final WebDriver driver, final Path screenshotsDir, final String namePrefix) {
		try {
			final String fileName = timestampedName(namePrefix) + ".png";
			final byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			final Path target = screenshotsDir.resolve(fileName);
			Files.write(target, bytes);
			return target.toString();
		} catch (final IOException ex) {
			return "SCREENSHOT_ERROR: " + ex.getMessage();
		}
	}

	private static String takeFullPageScreenshot(final WebDriver driver, final Path screenshotsDir, final String namePrefix) {
		final Dimension original = driver.manage().window().getSize();
		try {
			final JavascriptExecutor js = (JavascriptExecutor) driver;
			final long width = ((Number) js.executeScript(
					"return Math.max(document.body.scrollWidth, document.documentElement.scrollWidth, 1920);"))
					.longValue();
			final long height = ((Number) js.executeScript(
					"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight, 1080);"))
					.longValue();
			final int safeWidth = (int) Math.min(width, 2560);
			final int safeHeight = (int) Math.min(height, 6000);
			driver.manage().window().setSize(new Dimension(safeWidth, safeHeight));
			waitForUiLoad(driver);
			return takeScreenshot(driver, screenshotsDir, namePrefix);
		} finally {
			driver.manage().window().setSize(original);
			waitForUiLoad(driver);
		}
	}

	private static Path writeFinalReport(final Path outputDir, final Instant startedAt,
			final LinkedHashMap<String, Boolean> report, final LinkedHashMap<String, String> notes,
			final LinkedHashMap<String, String> legalUrls, final List<String> screenshots, final String loginUrl)
			throws IOException {
		final StringBuilder sb = new StringBuilder();
		sb.append("SaleADS Mi Negocio Full Test Report").append(System.lineSeparator());
		sb.append("Started At (UTC): ").append(startedAt).append(System.lineSeparator());
		sb.append("Finished At (UTC): ").append(Instant.now()).append(System.lineSeparator());
		sb.append("Login URL: ").append(loginUrl).append(System.lineSeparator());
		sb.append(System.lineSeparator());
		sb.append("Results").append(System.lineSeparator());
		for (final String field : REPORT_FIELDS) {
			final boolean passed = Boolean.TRUE.equals(report.get(field));
			sb.append("- ").append(field).append(": ").append(passed ? "PASS" : "FAIL");
			final String note = notes.get(field);
			if (note != null && !note.isBlank()) {
				sb.append(" (").append(note).append(")");
			}
			sb.append(System.lineSeparator());
		}

		sb.append(System.lineSeparator()).append("Final URLs").append(System.lineSeparator());
		if (legalUrls.isEmpty()) {
			sb.append("- Not captured").append(System.lineSeparator());
		} else {
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
		}

		sb.append(System.lineSeparator()).append("Screenshots").append(System.lineSeparator());
		for (final String screenshot : screenshots) {
			sb.append("- ").append(screenshot).append(System.lineSeparator());
		}

		final Path reportPath = outputDir.resolve("final-report.txt");
		Files.write(reportPath, sb.toString().getBytes(StandardCharsets.UTF_8));
		return reportPath;
	}

	private static String getVisibleBodyText(final WebDriver driver) {
		final List<WebElement> bodyList = driver.findElements(By.tagName("body"));
		if (bodyList.isEmpty()) {
			return "";
		}
		return bodyList.get(0).getText();
	}

	private static String timestampedName(final String baseName) {
		final String normalized = Normalizer.normalize(baseName, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "")
				.replaceAll("[^a-zA-Z0-9._-]", "_");
		return TS_FORMATTER.format(Instant.now()) + "_" + normalized;
	}

	private static void waitForUiLoad(final WebDriver driver) {
		try {
			new WebDriverWait(driver, DEFAULT_TIMEOUT).until(drv -> {
				final Object result = ((JavascriptExecutor) drv).executeScript("return document.readyState");
				return "complete".equals(result);
			});
		} catch (final Exception ignored) {
			// Keep moving even if JS execution is blocked.
		}
		sleep(600);
	}

	private static String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private static String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder sb = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String part;
			if (chars[i] == '\'') {
				part = "\"'\"";
			} else if (chars[i] == '"') {
				part = "'\"'";
			} else {
				part = "'" + chars[i] + "'";
			}
			sb.append(part);
			if (i < chars.length - 1) {
				sb.append(", ");
			}
		}
		sb.append(")");
		return sb.toString();
	}

	private static void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}
}
