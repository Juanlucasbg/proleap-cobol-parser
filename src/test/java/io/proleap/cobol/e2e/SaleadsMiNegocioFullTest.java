package io.proleap.cobol.e2e;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.internal.AssumptionViolatedException;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(10);
	private static final String GOOGLE_TEST_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private final Map<String, String> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean.parseBoolean(readSetting("saleads.headless", "SALEADS_HEADLESS", "false"));

		if (headless) {
			options.addArguments("--headless=new");
		}

		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		final String userDataDir = readSetting("saleads.chrome.userDataDir", "SALEADS_CHROME_USER_DATA_DIR", null);
		if (userDataDir != null && !userDataDir.isBlank()) {
			options.addArguments("--user-data-dir=" + userDataDir);
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		evidenceDir = Paths.get("target", "saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
		Files.createDirectories(evidenceDir);

		initializeReport();
	}

	@After
	public void tearDown() {
		try {
			printFinalReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final String loginUrl = readSetting("saleads.login.url", "SALEADS_LOGIN_URL", null);
		if (loginUrl == null || loginUrl.isBlank()) {
			throw new AssumptionViolatedException(
					"Missing SaleADS login URL. Set -Dsaleads.login.url=<url> or SALEADS_LOGIN_URL.");
		}

		boolean loginOk = executeStep("Login", () -> {
			driver.get(loginUrl);
			waitForUiLoad();

			clickByText("Sign in with Google", "Iniciar con Google", "Continuar con Google", "Login con Google");
			waitForUiLoad();
			selectGoogleAccountIfVisible(GOOGLE_TEST_ACCOUNT);

			assertAnyVisible("Negocio", "Mi Negocio");
			assertSidebarVisible();
			captureScreenshot("01_dashboard_loaded");
		});

		boolean menuOk = false;
		if (loginOk) {
			menuOk = executeStep("Mi Negocio menu", () -> {
				expandMiNegocioMenu();
				assertAnyVisible("Agregar Negocio");
				assertAnyVisible("Administrar Negocios");
				captureScreenshot("02_mi_negocio_expanded_menu");
			});
		} else {
			markBlocked("Mi Negocio menu");
		}

		boolean modalOk = false;
		if (menuOk) {
			modalOk = executeStep("Agregar Negocio modal", () -> {
				clickByText("Agregar Negocio");
				waitForUiLoad();

				assertAnyVisible("Crear Nuevo Negocio");
				assertAnyVisible("Nombre del Negocio");
				assertAnyVisible("Tienes 2 de 3 negocios");
				assertAnyVisible("Cancelar");
				assertAnyVisible("Crear Negocio");
				captureScreenshot("03_agregar_negocio_modal");

				typeIfPresent("Nombre del Negocio", "Negocio Prueba Automatizacion");
				clickByText("Cancelar");
				waitForUiLoad();
			});
		} else {
			markBlocked("Agregar Negocio modal");
		}

		boolean administrarOk = false;
		if (menuOk) {
			administrarOk = executeStep("Administrar Negocios view", () -> {
				expandMiNegocioMenu();
				clickByText("Administrar Negocios");
				waitForUiLoad();

				assertAnyVisible("Informacion General", "Informaci\u00f3n General");
				assertAnyVisible("Detalles de la Cuenta");
				assertAnyVisible("Tus Negocios");
				assertAnyVisible("Seccion Legal", "Secci\u00f3n Legal");
				captureScreenshot("04_administrar_negocios_view");
			});
		} else {
			markBlocked("Administrar Negocios view");
		}

		if (administrarOk) {
			executeStep("Informaci\u00f3n General", () -> {
				final WebElement infoSection = findSection("Informacion General", "Informaci\u00f3n General");
				final String sectionText = infoSection.getText();

				Assert.assertTrue("Expected a visible user email in Informacion General.",
						EMAIL_PATTERN.matcher(sectionText).find());
				assertContainsText(sectionText, "BUSINESS PLAN");
				findVisibleInScope(infoSection, byText("Cambiar Plan"));

				// There should be at least one non-empty identity line besides labels/plan.
				final String[] lines = sectionText.split("\\R");
				boolean hasCandidateName = false;
				for (final String rawLine : lines) {
					final String line = rawLine.trim();
					if (line.isEmpty()) {
						continue;
					}
					final String normalized = line.toLowerCase();
					if (normalized.contains("informacion general") || normalized.contains("informaci\u00f3n general")
							|| normalized.contains("business plan") || normalized.contains("cambiar plan")
							|| EMAIL_PATTERN.matcher(line).find()) {
						continue;
					}
					if (line.length() >= 3) {
						hasCandidateName = true;
						break;
					}
				}
				Assert.assertTrue("Expected user name or identity text in Informacion General.", hasCandidateName);
			});
		} else {
			markBlocked("Informaci\u00f3n General");
		}

		if (administrarOk) {
			executeStep("Detalles de la Cuenta", () -> {
				final WebElement detailsSection = findSection("Detalles de la Cuenta");
				final String sectionText = detailsSection.getText();
				assertContainsText(sectionText, "Cuenta creada");
				assertContainsText(sectionText, "Estado activo");
				assertContainsText(sectionText, "Idioma seleccionado");
			});
		} else {
			markBlocked("Detalles de la Cuenta");
		}

		if (administrarOk) {
			executeStep("Tus Negocios", () -> {
				final WebElement businessesSection = findSection("Tus Negocios");
				final String sectionText = businessesSection.getText();

				assertContainsText(sectionText, "Tienes 2 de 3 negocios");
				findVisibleInScope(businessesSection, byText("Agregar Negocio"));

				final int visibleRows = businessesSection
						.findElements(By.xpath(".//li | .//tr | .//*[contains(@class, 'business')]")).size();
				Assert.assertTrue("Expected visible business list content.", visibleRows > 0 || sectionText.length() > 40);
			});
		} else {
			markBlocked("Tus Negocios");
		}

		if (administrarOk) {
			executeStep("T\u00e9rminos y Condiciones", () -> validateLegalDocument("T\u00e9rminos y Condiciones",
					"T\u00e9rminos y Condiciones", "05_terminos_y_condiciones"));
		} else {
			markBlocked("T\u00e9rminos y Condiciones");
		}

		if (administrarOk) {
			executeStep("Pol\u00edtica de Privacidad",
					() -> validateLegalDocument("Pol\u00edtica de Privacidad", "Pol\u00edtica de Privacidad",
							"06_politica_de_privacidad"));
		} else {
			markBlocked("Pol\u00edtica de Privacidad");
		}

		Assert.assertTrue("One or more SaleADS Mi Negocio validations failed:\n" + reportSummary(),
				report.values().stream().allMatch(value -> "PASS".equals(value)));
	}

	private void validateLegalDocument(final String linkText, final String headingText, final String screenshotName) {
		final String appTab = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickByText(linkText);
		waitForUiLoad();

		String targetHandle = null;
		try {
			targetHandle = new WebDriverWait(driver, SHORT_TIMEOUT)
					.until((ExpectedCondition<String>) d -> findNewHandle(Objects.requireNonNull(d), handlesBefore));
		} catch (final TimeoutException ignored) {
			// No new tab detected; continue in same tab.
		}

		if (targetHandle != null) {
			driver.switchTo().window(targetHandle);
			waitForUiLoad();
		}

		assertAnyVisible(headingText);
		final String pageText = driver.findElement(By.tagName("body")).getText();
		Assert.assertTrue("Expected legal content text on page.", pageText != null && pageText.trim().length() > 80);

		captureScreenshot(screenshotName);
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (targetHandle != null) {
			driver.close();
			driver.switchTo().window(appTab);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private String findNewHandle(final WebDriver activeDriver, final Set<String> handlesBefore) {
		for (final String handle : activeDriver.getWindowHandles()) {
			if (!handlesBefore.contains(handle)) {
				return handle;
			}
		}
		return null;
	}

	private void expandMiNegocioMenu() {
		clickByText("Negocio");
		waitForUiLoad();

		final List<WebElement> adminOptions = driver.findElements(byText("Administrar Negocios"));
		boolean visible = false;
		for (final WebElement element : adminOptions) {
			if (element.isDisplayed()) {
				visible = true;
				break;
			}
		}
		if (!visible) {
			clickByText("Mi Negocio");
			waitForUiLoad();
		}
	}

	private void selectGoogleAccountIfVisible(final String email) {
		final WebDriverWait shortWait = new WebDriverWait(driver, SHORT_TIMEOUT);
		final List<By> selectors = new ArrayList<>();
		selectors.add(byContainsText(email));
		selectors.add(By.xpath("//div[@data-email=" + xpathLiteral(email) + "]"));
		selectors.add(By.xpath("//li[@data-identifier=" + xpathLiteral(email) + "]"));

		for (final By selector : selectors) {
			try {
				final WebElement accountOption = shortWait.until(ExpectedConditions.visibilityOfElementLocated(selector));
				if (accountOption.isDisplayed()) {
					accountOption.click();
					waitForUiLoad();
					return;
				}
			} catch (final TimeoutException ignored) {
				// Try next selector.
			}
		}
	}

	private void typeIfPresent(final String fieldLabel, final String value) {
		final List<By> selectors = new ArrayList<>();
		selectors.add(By.xpath("//input[@placeholder=" + xpathLiteral(fieldLabel) + "]"));
		selectors.add(By.xpath("//label[normalize-space()=" + xpathLiteral(fieldLabel) + "]/following::input[1]"));
		selectors.add(By.xpath("//input[contains(@name,'negocio') or contains(@id,'negocio')]"));

		for (final By selector : selectors) {
			final List<WebElement> candidates = driver.findElements(selector);
			for (final WebElement candidate : candidates) {
				if (candidate.isDisplayed() && candidate.isEnabled()) {
					candidate.click();
					candidate.clear();
					candidate.sendKeys(value);
					waitForUiLoad();
					return;
				}
			}
		}
	}

	private WebElement findSection(final String... possibleTitles) {
		Exception lastError = null;
		for (final String title : possibleTitles) {
			final By heading = byText(title);
			try {
				final WebElement headingElement = wait.until(ExpectedConditions.visibilityOfElementLocated(heading));
				final WebElement section = headingElement
						.findElement(By.xpath("./ancestor::section[1] | ./ancestor::div[1] | ./ancestor::article[1]"));
				if (section.isDisplayed()) {
					return section;
				}
			} catch (final Exception error) {
				lastError = error;
			}
		}
		throw new AssertionError("Could not locate section for titles: " + String.join(", ", possibleTitles), lastError);
	}

	private void clickByText(final String... texts) {
		Exception lastError = null;
		final WebDriverWait shortWait = new WebDriverWait(driver, SHORT_TIMEOUT);
		for (final String text : texts) {
			try {
				final List<By> selectors = new ArrayList<>();
				selectors.add(byInteractiveText(text));
				selectors.add(byText(text));

				for (final By selector : selectors) {
					final List<WebElement> candidates = shortWait
							.until(ExpectedConditions.presenceOfAllElementsLocatedBy(selector));
					for (final WebElement candidate : candidates) {
						if (candidate.isDisplayed() && candidate.isEnabled()) {
							try {
								wait.until(ExpectedConditions.elementToBeClickable(candidate));
								candidate.click();
							} catch (final Exception clickError) {
								((JavascriptExecutor) driver).executeScript("arguments[0].click();", candidate);
							}
							waitForUiLoad();
							return;
						}
					}
				}
				throw new AssertionError("No clickable visible element found with text: " + text);
			} catch (final Exception error) {
				lastError = error;
			}
		}
		throw new AssertionError("Could not click any element by visible text.", lastError);
	}

	private void assertAnyVisible(final String... texts) {
		for (final String text : texts) {
			final List<By> selectors = new ArrayList<>();
			selectors.add(byText(text));
			selectors.add(byContainsText(text));
			for (final By selector : selectors) {
				final List<WebElement> matches = driver.findElements(selector);
				for (final WebElement match : matches) {
					if (match.isDisplayed()) {
						return;
					}
				}
			}
		}
		throw new AssertionError("Expected visible text not found: " + String.join(" / ", texts));
	}

	private void assertSidebarVisible() {
		final List<By> sidebarSelectors = new ArrayList<>();
		sidebarSelectors.add(By.tagName("aside"));
		sidebarSelectors.add(By.xpath("//nav"));
		sidebarSelectors.add(By.xpath("//*[contains(@class,'sidebar')]"));

		for (final By selector : sidebarSelectors) {
			final List<WebElement> elements = driver.findElements(selector);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return;
				}
			}
		}

		throw new AssertionError("Expected visible left sidebar navigation.");
	}

	private WebElement findVisibleInScope(final WebElement scope, final By selector) {
		final List<WebElement> matches = scope.findElements(selector);
		for (final WebElement match : matches) {
			if (match.isDisplayed()) {
				return match;
			}
		}
		throw new AssertionError("Expected visible element in scope for selector: " + selector);
	}

	private void assertContainsText(final String source, final String expected) {
		Assert.assertTrue("Expected text [" + expected + "] in section content.",
				source != null && source.toLowerCase().contains(expected.toLowerCase()));
	}

	private boolean executeStep(final String stepName, final Runnable action) {
		try {
			action.run();
			report.put(stepName, "PASS");
			return true;
		} catch (final Throwable error) {
			report.put(stepName, "FAIL");
			try {
				captureScreenshot("error_" + sanitize(stepName) + "_" + LocalDateTime.now().format(DATE_FORMAT));
			} catch (final Exception ignored) {
				// Best effort evidence capture.
			}
			return false;
		}
	}

	private void markBlocked(final String stepName) {
		report.put(stepName, "FAIL");
	}

	private void initializeReport() {
		report.put("Login", "FAIL");
		report.put("Mi Negocio menu", "FAIL");
		report.put("Agregar Negocio modal", "FAIL");
		report.put("Administrar Negocios view", "FAIL");
		report.put("Informaci\u00f3n General", "FAIL");
		report.put("Detalles de la Cuenta", "FAIL");
		report.put("Tus Negocios", "FAIL");
		report.put("T\u00e9rminos y Condiciones", "FAIL");
		report.put("Pol\u00edtica de Privacidad", "FAIL");
	}

	private void printFinalReport() {
		System.out.println("=== SaleADS Mi Negocio Full Test Report ===");
		for (final Map.Entry<String, String> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + entry.getValue());
		}
		if (!legalUrls.isEmpty()) {
			System.out.println("=== Legal URL Evidence ===");
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				System.out.println(entry.getKey() + " => " + entry.getValue());
			}
		}
		System.out.println("Evidence directory: " + evidenceDir.toAbsolutePath());
	}

	private String reportSummary() {
		final StringBuilder builder = new StringBuilder();
		for (final Map.Entry<String, String> entry : report.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
		}
		return builder.toString();
	}

	private void waitForUiLoad() {
		wait.until(driver -> ((JavascriptExecutor) driver).executeScript("return document.readyState").equals("complete"));
		wait.until(driver -> {
			final Object result = ((JavascriptExecutor) driver).executeScript(
					"return (window.jQuery ? jQuery.active === 0 : true) && (document.querySelector('.loading, .spinner, [aria-busy=\"true\"]') === null);");
			return result instanceof Boolean && (Boolean) result;
		});
	}

	private void captureScreenshot(final String name) {
		final String fileName = sanitize(name) + ".png";
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		try {
			Files.copy(screenshot.toPath(), evidenceDir.resolve(fileName));
		} catch (final IOException exception) {
			throw new AssertionError("Could not store screenshot: " + fileName, exception);
		}
	}

	private By byText(final String text) {
		final String literal = xpathLiteral(text);
		return By.xpath("//*[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]");
	}

	private By byContainsText(final String text) {
		return By.xpath("//*[contains(normalize-space(), " + xpathLiteral(text) + ")]");
	}

	private By byInteractiveText(final String text) {
		final String literal = xpathLiteral(text);
		return By.xpath("//button[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]"
				+ " | //a[normalize-space()=" + literal + " or contains(normalize-space(), " + literal + ")]"
				+ " | //*[@role='button'][normalize-space()=" + literal + " or contains(normalize-space(), " + literal
				+ ")]");
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		final String[] parts = value.split("'");
		final StringBuilder sb = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				sb.append(", \"'\", ");
			}
			sb.append("'").append(parts[i]).append("'");
		}
		sb.append(")");
		return sb.toString();
	}

	private String readSetting(final String systemProperty, final String envVariable, final String fallback) {
		final String fromSystem = System.getProperty(systemProperty);
		if (fromSystem != null && !fromSystem.isBlank()) {
			return fromSystem.trim();
		}
		final String fromEnv = System.getenv(envVariable);
		if (fromEnv != null && !fromEnv.isBlank()) {
			return fromEnv.trim();
		}
		return fallback;
	}

	private String sanitize(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
	}
}
