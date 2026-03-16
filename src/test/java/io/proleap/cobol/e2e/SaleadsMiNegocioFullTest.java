package io.proleap.cobol.e2e;

import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * Full Mi Negocio workflow validation for SaleADS.
 *
 * <p>
 * This test is intentionally environment-agnostic:
 * <ul>
 * <li>No hardcoded domain is used.</li>
 * <li>Set {@code -Dsaleads.baseUrl=https://<env-login-page>} to open the login page.</li>
 * <li>Or open the login page manually if running against an already-open browser harness.</li>
 * </ul>
 * </p>
 *
 * <p>
 * Execution is opt-in to avoid impacting the parser test suite by default:
 * run with {@code -Dsaleads.e2e.enabled=true}.
 * </p>
 */
public class SaleadsMiNegocioFullTest {

	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(8);
	private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> reportDetails = new LinkedHashMap<>();

	private Path screenshotDir;
	private WebDriver driver;
	private WebDriverWait wait;

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		Assume.assumeTrue("Enable with -Dsaleads.e2e.enabled=true",
				Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false")));

		final String baseUrl = firstNonBlank(System.getProperty("saleads.baseUrl"), System.getenv("SALEADS_BASE_URL"),
				System.getenv("BASE_URL"));

		setupBrowser();
		try {
			if (baseUrl != null) {
				driver.get(baseUrl);
				waitForUiLoad();
			}

			runStep("Login", () -> {
				clickByAnyVisibleText(Arrays.asList("Sign in with Google", "Iniciar sesion con Google",
						"Iniciar sesion", "Login with Google", "Continuar con Google"), DEFAULT_TIMEOUT);

				waitForUiLoad();

				// Account selector is conditional; click if present.
				clickIfVisibleText("juanlucasbarbiergarzon@gmail.com", SHORT_TIMEOUT);
				waitForUiLoad();

				requireAnyElementVisible("main application interface",
						By.cssSelector("aside, nav, [role='navigation'], [class*='sidebar']"), DEFAULT_TIMEOUT);
				requireAnyVisibleText(Arrays.asList("Negocio", "Mi Negocio"), DEFAULT_TIMEOUT);

				takeScreenshot("01-dashboard-loaded");
			});

			runStep("Mi Negocio menu", () -> {
				requireAnyElementVisible("left sidebar", By.cssSelector("aside, nav, [role='navigation']"),
						DEFAULT_TIMEOUT);

				if (!isTextVisible("Mi Negocio", SHORT_TIMEOUT)) {
					clickByAnyVisibleText(Arrays.asList("Negocio"), DEFAULT_TIMEOUT);
					waitForUiLoad();
				}

				clickByAnyVisibleText(Arrays.asList("Mi Negocio"), DEFAULT_TIMEOUT);
				waitForUiLoad();

				requireAnyVisibleText(Arrays.asList("Agregar Negocio"), DEFAULT_TIMEOUT);
				requireAnyVisibleText(Arrays.asList("Administrar Negocios"), DEFAULT_TIMEOUT);

				takeScreenshot("02-mi-negocio-menu-expanded");
			});

			runStep("Agregar Negocio modal", () -> {
				clickByAnyVisibleText(Arrays.asList("Agregar Negocio"), DEFAULT_TIMEOUT);
				waitForUiLoad();

				requireAnyVisibleText(Arrays.asList("Crear Nuevo Negocio"), DEFAULT_TIMEOUT);

				WebElement input = waitForVisibleInputForBusinessName(DEFAULT_TIMEOUT);
				assertNotNull("Nombre del Negocio input not found", input);

				requireAnyVisibleText(Arrays.asList("Tienes 2 de 3 negocios"), DEFAULT_TIMEOUT);
				requireAnyVisibleText(Arrays.asList("Cancelar"), DEFAULT_TIMEOUT);
				requireAnyVisibleText(Arrays.asList("Crear Negocio"), DEFAULT_TIMEOUT);

				takeScreenshot("03-crear-negocio-modal");

				input.click();
				input.clear();
				input.sendKeys("Negocio Prueba Automatizacion");
				waitForUiLoad();

				clickByAnyVisibleText(Arrays.asList("Cancelar"), DEFAULT_TIMEOUT);
				waitForUiLoad();
			});

			runStep("Administrar Negocios view", () -> {
				if (!isTextVisible("Administrar Negocios", SHORT_TIMEOUT)) {
					clickByAnyVisibleText(Arrays.asList("Mi Negocio"), DEFAULT_TIMEOUT);
					waitForUiLoad();
				}

				clickByAnyVisibleText(Arrays.asList("Administrar Negocios"), DEFAULT_TIMEOUT);
				waitForUiLoad();

				requireAnyVisibleText(Arrays.asList("Informacion General", "Informacion"), DEFAULT_TIMEOUT);
				requireAnyVisibleText(Arrays.asList("Detalles de la Cuenta"), DEFAULT_TIMEOUT);
				requireAnyVisibleText(Arrays.asList("Tus Negocios"), DEFAULT_TIMEOUT);
				requireAnyVisibleText(Arrays.asList("Seccion Legal"), DEFAULT_TIMEOUT);

				takeScreenshot("04-administrar-negocios-view");
			});

			runStep("Informacion General", () -> {
				requireAnyVisibleText(Arrays.asList("BUSINESS PLAN"), DEFAULT_TIMEOUT);
				requireAnyVisibleText(Arrays.asList("Cambiar Plan"), DEFAULT_TIMEOUT);

				WebElement emailElement = findVisibleElementMatchingRegex(EMAIL_PATTERN, DEFAULT_TIMEOUT);
				assertNotNull("User email is not visible", emailElement);

				String pageText = normalizeText(Objects.toString(driver.findElement(By.tagName("body")).getText(), ""));
				boolean hasLikelyUserName = !pageText.isEmpty()
						&& pageText.replaceAll(EMAIL_PATTERN.pattern(), "").replaceAll("[^a-z ]", " ").trim().length() > 8;
				Assert.assertTrue("User name is not clearly visible in Informacion General section", hasLikelyUserName);
			});

			runStep("Detalles de la Cuenta", () -> {
				requireAnyVisibleText(Arrays.asList("Cuenta creada"), DEFAULT_TIMEOUT);
				requireAnyVisibleText(Arrays.asList("Estado activo"), DEFAULT_TIMEOUT);
				requireAnyVisibleText(Arrays.asList("Idioma seleccionado"), DEFAULT_TIMEOUT);
			});

			runStep("Tus Negocios", () -> {
				requireAnyVisibleText(Arrays.asList("Tus Negocios"), DEFAULT_TIMEOUT);
				requireAnyVisibleText(Arrays.asList("Agregar Negocio"), DEFAULT_TIMEOUT);
				requireAnyVisibleText(Arrays.asList("Tienes 2 de 3 negocios"), DEFAULT_TIMEOUT);

				boolean hasBusinessListLikeContent = hasAnyVisibleElement(By.cssSelector("table, ul, ol, [class*='card'], [class*='list']"))
						|| getVisibleTextCount(Arrays.asList("negocio")) >= 2;
				Assert.assertTrue("Business list is not visible", hasBusinessListLikeContent);
			});

			runStep("Terminos y Condiciones", () -> {
				LegalValidationResult legal = openLegalAndReturn("Terminos y Condiciones", "Terminos y Condiciones",
						"08-terminos");
				reportDetails.put("Terminos y Condiciones URL", legal.finalUrl);
			});

			runStep("Politica de Privacidad", () -> {
				LegalValidationResult legal = openLegalAndReturn("Politica de Privacidad", "Politica de Privacidad",
						"09-politica-privacidad");
				reportDetails.put("Politica de Privacidad URL", legal.finalUrl);
			});

			finalizeReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	private void setupBrowser() throws IOException {
		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "true"));
		final String timestamp = LocalDateTime.now().format(TS_FORMAT);
		screenshotDir = Path.of("target", "saleads-artifacts", "screenshots", timestamp);
		Files.createDirectories(screenshotDir);

		ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-notifications");
		options.addArguments("--disable-popup-blocking");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");
		options.addArguments("--lang=es-ES");
		if (headless) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
	}

	private void runStep(final String reportField, final StepAction action) {
		try {
			action.execute();
			report.put(reportField, Boolean.TRUE);
			reportDetails.put(reportField, "PASS");
		} catch (Throwable t) {
			report.put(reportField, Boolean.FALSE);
			reportDetails.put(reportField, "FAIL: " + safeMessage(t));
			try {
				takeScreenshot("error-" + normalizePathToken(reportField));
			} catch (Throwable ignored) {
				// keep original step failure context
			}
		}
	}

	private void finalizeReport() {
		List<String> orderedFields = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
				"Administrar Negocios view", "Informacion General", "Detalles de la Cuenta", "Tus Negocios",
				"Terminos y Condiciones", "Politica de Privacidad");

		StringBuilder sb = new StringBuilder();
		sb.append(System.lineSeparator()).append("=== SaleADS Mi Negocio Workflow Report ===").append(System.lineSeparator());
		for (String field : orderedFields) {
			Boolean status = report.get(field);
			String label = status != null && status.booleanValue() ? "PASS" : "FAIL";
			sb.append("- ").append(field).append(": ").append(label);
			String detail = reportDetails.get(field);
			if (detail != null && detail.startsWith("FAIL:")) {
				sb.append(" (").append(detail).append(")");
			}
			sb.append(System.lineSeparator());
		}

		if (reportDetails.containsKey("Terminos y Condiciones URL")) {
			sb.append("- Terminos y Condiciones final URL: ").append(reportDetails.get("Terminos y Condiciones URL"))
					.append(System.lineSeparator());
		}
		if (reportDetails.containsKey("Politica de Privacidad URL")) {
			sb.append("- Politica de Privacidad final URL: ").append(reportDetails.get("Politica de Privacidad URL"))
					.append(System.lineSeparator());
		}
		sb.append("- Screenshot directory: ").append(screenshotDir.toAbsolutePath()).append(System.lineSeparator());

		boolean allPassed = orderedFields.stream().allMatch(key -> Boolean.TRUE.equals(report.get(key)));
		if (!allPassed) {
			Assert.fail(sb.toString());
		}
		System.out.println(sb);
	}

	private LegalValidationResult openLegalAndReturn(final String linkText, final String headingText,
			final String screenshotName) throws IOException {
		String appHandle = driver.getWindowHandle();
		String beforeUrl = driver.getCurrentUrl();
		Set<String> handlesBefore = driver.getWindowHandles();

		clickByAnyVisibleText(Arrays.asList(linkText), DEFAULT_TIMEOUT);
		waitForUiLoad();

		wait.until(d -> d.getWindowHandles().size() > handlesBefore.size() || !Objects.equals(beforeUrl, d.getCurrentUrl()));

		Set<String> handlesAfter = driver.getWindowHandles();
		if (handlesAfter.size() > handlesBefore.size()) {
			for (String handle : handlesAfter) {
				if (!handlesBefore.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
			waitForUiLoad();
		}

		requireAnyVisibleText(Arrays.asList(headingText), DEFAULT_TIMEOUT);

		String bodyText = normalizeText(Objects.toString(driver.findElement(By.tagName("body")).getText(), ""));
		Assert.assertTrue("Legal content text is not visible", bodyText.length() > 80);

		takeScreenshot(screenshotName);
		String finalUrl = driver.getCurrentUrl();

		// Return to the application tab/window.
		if (!driver.getWindowHandle().equals(appHandle)) {
			driver.close();
			driver.switchTo().window(appHandle);
		} else if (!Objects.equals(beforeUrl, finalUrl)) {
			driver.navigate().back();
		}
		waitForUiLoad();

		return new LegalValidationResult(finalUrl);
	}

	private void waitForUiLoad() {
		wait.until(d -> {
			Object state = ((JavascriptExecutor) d).executeScript("return document.readyState");
			return "complete".equals(state);
		});
		sleep(700);
	}

	private void requireAnyVisibleText(final List<String> textCandidates, final Duration timeout) {
		WebElement element = waitForAnyVisibleText(textCandidates, timeout);
		assertNotNull("Expected visible text not found: " + textCandidates, element);
	}

	private WebElement waitForAnyVisibleText(final List<String> textCandidates, final Duration timeout) {
		WebDriverWait localWait = new WebDriverWait(driver, timeout);
		List<String> normalized = new ArrayList<>();
		for (String candidate : textCandidates) {
			normalized.add(normalizeText(candidate));
		}

		try {
			return localWait.until(d -> {
				List<WebElement> elements = d.findElements(By.xpath("//*[normalize-space(text())!='']"));
				for (WebElement element : elements) {
					try {
						if (!element.isDisplayed()) {
							continue;
						}
						String actual = normalizeText(readElementText(element));
						if (actual.isEmpty()) {
							continue;
						}
						for (String target : normalized) {
							if (actual.contains(target)) {
								return element;
							}
						}
					} catch (StaleElementReferenceException ignored) {
						// element refreshed during polling
					}
				}
				return null;
			});
		} catch (TimeoutException ignored) {
			return null;
		}
	}

	private void clickByAnyVisibleText(final List<String> textCandidates, final Duration timeout) {
		WebElement target = waitForClickableByVisibleText(textCandidates, timeout);
		assertNotNull("Could not find clickable element with text: " + textCandidates, target);
		scrollIntoView(target);
		try {
			target.click();
		} catch (Throwable clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", target);
		}
		waitForUiLoad();
	}

	private WebElement waitForClickableByVisibleText(final List<String> textCandidates, final Duration timeout) {
		WebDriverWait localWait = new WebDriverWait(driver, timeout);
		List<String> normalizedTargets = new ArrayList<>();
		for (String candidate : textCandidates) {
			normalizedTargets.add(normalizeText(candidate));
		}

		try {
			return localWait.until(d -> {
				List<WebElement> candidates = d.findElements(By.cssSelector(
						"button, a, [role='button'], [role='menuitem'], li, div, span, p, h1, h2, h3, h4"));
				WebElement best = null;
				int bestLength = Integer.MAX_VALUE;
				for (WebElement candidate : candidates) {
					try {
						if (!candidate.isDisplayed() || !candidate.isEnabled()) {
							continue;
						}
						String text = normalizeText(readElementText(candidate));
						if (text.isEmpty()) {
							continue;
						}
						for (String target : normalizedTargets) {
							if (text.contains(target) && text.length() < bestLength) {
								best = candidate;
								bestLength = text.length();
							}
						}
					} catch (StaleElementReferenceException ignored) {
						// ignore stale candidates during polling
					}
				}
				return best;
			});
		} catch (TimeoutException ignored) {
			return null;
		}
	}

	private void clickIfVisibleText(final String text, final Duration timeout) {
		WebElement maybe = waitForClickableByVisibleText(Arrays.asList(text), timeout);
		if (maybe != null) {
			scrollIntoView(maybe);
			try {
				maybe.click();
			} catch (Throwable clickError) {
				((JavascriptExecutor) driver).executeScript("arguments[0].click();", maybe);
			}
		}
	}

	private void requireAnyElementVisible(final String label, final By locator, final Duration timeout) {
		WebDriverWait localWait = new WebDriverWait(driver, timeout);
		try {
			WebElement visible = localWait.until(d -> {
				for (WebElement element : d.findElements(locator)) {
					if (element.isDisplayed()) {
						return element;
					}
				}
				return null;
			});
			assertNotNull("Could not find " + label, visible);
		} catch (TimeoutException e) {
			Assert.fail("Could not find " + label + " with locator " + locator);
		}
	}

	private WebElement waitForVisibleInputForBusinessName(final Duration timeout) {
		WebDriverWait localWait = new WebDriverWait(driver, timeout);
		try {
			return localWait.until(d -> {
				List<By> locators = Arrays.asList(
						By.xpath("//input[contains(translate(@placeholder,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'negocio')]"),
						By.xpath("//input[contains(translate(@name,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'negocio')]"),
						By.xpath("//input[contains(translate(@id,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'negocio')]"),
						By.xpath(
								"//*[contains(translate(normalize-space(text()),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'nombre del negocio')]/following::input[1]"));

				for (By locator : locators) {
					for (WebElement element : d.findElements(locator)) {
						if (element.isDisplayed() && element.isEnabled()) {
							return element;
						}
					}
				}
				return null;
			});
		} catch (TimeoutException ignored) {
			return null;
		}
	}

	private WebElement findVisibleElementMatchingRegex(final Pattern pattern, final Duration timeout) {
		WebDriverWait localWait = new WebDriverWait(driver, timeout);
		try {
			return localWait.until(d -> {
				List<WebElement> elements = d.findElements(By.xpath("//*[normalize-space(text())!='']"));
				for (WebElement element : elements) {
					try {
						if (!element.isDisplayed()) {
							continue;
						}
						String text = readElementText(element);
						if (pattern.matcher(text).find()) {
							return element;
						}
					} catch (StaleElementReferenceException ignored) {
						// stale during polling
					}
				}
				return null;
			});
		} catch (TimeoutException ignored) {
			return null;
		}
	}

	private boolean isTextVisible(final String text, final Duration timeout) {
		return waitForAnyVisibleText(Arrays.asList(text), timeout) != null;
	}

	private boolean hasAnyVisibleElement(final By locator) {
		for (WebElement element : driver.findElements(locator)) {
			try {
				if (element.isDisplayed()) {
					return true;
				}
			} catch (StaleElementReferenceException ignored) {
				// skip stale
			}
		}
		return false;
	}

	private int getVisibleTextCount(final List<String> containsNormalizedTerms) {
		int count = 0;
		List<String> normalizedTerms = new ArrayList<>();
		for (String term : containsNormalizedTerms) {
			normalizedTerms.add(normalizeText(term));
		}

		for (WebElement element : driver.findElements(By.xpath("//*[normalize-space(text())!='']"))) {
			try {
				if (!element.isDisplayed()) {
					continue;
				}
				String text = normalizeText(readElementText(element));
				for (String term : normalizedTerms) {
					if (!term.isEmpty() && text.contains(term)) {
						count++;
						break;
					}
				}
			} catch (StaleElementReferenceException ignored) {
				// ignore stale
			}
		}
		return count;
	}

	private void takeScreenshot(final String name) throws IOException {
		FileRef screenshot = asFileRef(((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE));
		Path target = screenshotDir.resolve(normalizePathToken(name) + ".png");
		Files.copy(screenshot.path, target, StandardCopyOption.REPLACE_EXISTING);
	}

	private static String readElementText(final WebElement element) {
		String text = element.getText();
		if (text != null && !text.trim().isEmpty()) {
			return text;
		}
		String ariaLabel = element.getAttribute("aria-label");
		if (ariaLabel != null && !ariaLabel.trim().isEmpty()) {
			return ariaLabel;
		}
		String title = element.getAttribute("title");
		return title == null ? "" : title;
	}

	private static String normalizeText(final String input) {
		if (input == null) {
			return "";
		}
		String noDiacritics = Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		return noDiacritics.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
	}

	private static String normalizePathToken(final String value) {
		return normalizeText(value).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
	}

	private static String firstNonBlank(final String... values) {
		for (String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}

	private static void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
	}

	private static String safeMessage(final Throwable t) {
		return t == null ? "unknown error" : firstNonBlank(t.getMessage(), t.toString());
	}

	private static FileRef asFileRef(final java.io.File file) {
		return new FileRef(file.toPath());
	}

	private interface StepAction {
		void execute() throws Exception;
	}

	private static final class LegalValidationResult {
		private final String finalUrl;

		private LegalValidationResult(final String finalUrl) {
			this.finalUrl = finalUrl;
		}
	}

	private static final class FileRef {
		private final Path path;

		private FileRef(final Path path) {
			this.path = path;
		}
	}
}
