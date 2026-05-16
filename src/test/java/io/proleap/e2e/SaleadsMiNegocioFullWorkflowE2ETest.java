package io.proleap.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.openqa.selenium.By;
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

public class SaleadsMiNegocioFullWorkflowE2ETest {

	private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(25);
	private static final Duration SHORT_WAIT = Duration.ofSeconds(8);
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Path EVIDENCE_DIR = Paths.get("target", "saleads-evidence");

	private String terminosUrl = "N/A";
	private String politicaUrl = "N/A";

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final LinkedHashMap<String, StepResult> stepResults = new LinkedHashMap<>();
		final ChromeOptions options = new ChromeOptions();
		final boolean headless = getBooleanConfig("SALEADS_HEADLESS", "saleads.headless", true);

		if (headless) {
			options.addArguments("--headless=new");
		}

		options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox");

		WebDriver driver = new ChromeDriver(options);
		WebDriverWait wait = new WebDriverWait(driver, WAIT_TIMEOUT);

		try {
			Files.createDirectories(EVIDENCE_DIR);
			prepareInitialPage(driver, wait);

			runStep(stepResults, "Login", () -> {
				clickAnyVisibleText(driver, wait, Arrays.asList("Sign in with Google", "Iniciar sesión con Google",
						"Inicia sesión con Google", "Continuar con Google", "Ingresar con Google", "Login con Google"));

				selectGoogleAccountIfPrompted(driver, wait);

				assertAnyTextVisible(driver, wait, Arrays.asList("Negocio", "Mi Negocio", "Dashboard", "Inicio"));
				assertSidebarVisible(driver);
				takeScreenshot(driver, "01-dashboard-loaded");
			});

			runStep(stepResults, "Mi Negocio menu", () -> {
				clickTextIfVisible(driver, wait, "Negocio");
				clickAnyVisibleText(driver, wait, Arrays.asList("Mi Negocio", "Mi negocio"));
				waitForUiLoad(driver, wait);

				assertAnyTextVisible(driver, wait, Arrays.asList("Agregar Negocio", "Agregar negocio"));
				assertAnyTextVisible(driver, wait, Arrays.asList("Administrar Negocios", "Administrar negocios"));
				takeScreenshot(driver, "02-mi-negocio-menu-expanded");
			});

			runStep(stepResults, "Agregar Negocio modal", () -> {
				clickAnyVisibleText(driver, wait, Arrays.asList("Agregar Negocio", "Agregar negocio"));
				assertAnyTextVisible(driver, wait, Arrays.asList("Crear Nuevo Negocio", "Crear nuevo negocio"));

				WebElement businessNameInput = findVisible(driver, wait,
						By.xpath("//input[contains(@placeholder,'Nombre del Negocio')]"
								+ " | //label[contains(normalize-space(),'Nombre del Negocio')]/following::input[1]"));
				Assert.assertNotNull("No se encontró el input 'Nombre del Negocio'.", businessNameInput);

				assertAnyTextVisible(driver, wait, Arrays.asList("Tienes 2 de 3 negocios"));
				assertAnyTextVisible(driver, wait, Arrays.asList("Cancelar"));
				assertAnyTextVisible(driver, wait, Arrays.asList("Crear Negocio", "Crear negocio"));
				takeScreenshot(driver, "03-crear-nuevo-negocio-modal");

				businessNameInput.click();
				businessNameInput.clear();
				businessNameInput.sendKeys("Negocio Prueba Automatización");
				clickAnyVisibleText(driver, wait, Arrays.asList("Cancelar"));

				By modalTitle = byVisibleText("Crear Nuevo Negocio");
				wait.withTimeout(SHORT_WAIT).until(ExpectedConditions.invisibilityOfElementLocated(modalTitle));
				wait.withTimeout(WAIT_TIMEOUT);
			});

			runStep(stepResults, "Administrar Negocios view", () -> {
				expandMiNegocioIfCollapsed(driver, wait);
				clickAnyVisibleText(driver, wait, Arrays.asList("Administrar Negocios", "Administrar negocios"));

				assertAnyTextVisible(driver, wait, Arrays.asList("Información General", "Informacion General"));
				assertAnyTextVisible(driver, wait, Arrays.asList("Detalles de la Cuenta", "Detalles de la cuenta"));
				assertAnyTextVisible(driver, wait, Arrays.asList("Tus Negocios", "Tus negocios"));
				assertAnyTextVisible(driver, wait, Arrays.asList("Sección Legal", "Seccion Legal"));
				takeScreenshot(driver, "04-administrar-negocios-cuenta");
			});

			runStep(stepResults, "Información General", () -> {
				assertAnyTextVisible(driver, wait, Arrays.asList("BUSINESS PLAN", "Business Plan"));
				assertAnyTextVisible(driver, wait, Arrays.asList("Cambiar Plan", "Cambiar plan"));

				WebElement emailElement = findVisible(driver, wait, By.xpath("//*[contains(normalize-space(),'@')]"));
				Assert.assertNotNull("No se encontró el email del usuario.", emailElement);

				Assert.assertTrue("No se encontró un nombre de usuario visible.", hasLikelyUserNameVisible(driver));
			});

			runStep(stepResults, "Detalles de la Cuenta", () -> {
				assertAnyTextVisible(driver, wait, Arrays.asList("Cuenta creada"));
				assertAnyTextVisible(driver, wait, Arrays.asList("Estado activo", "Estado Activo"));
				assertAnyTextVisible(driver, wait, Arrays.asList("Idioma seleccionado"));
			});

			runStep(stepResults, "Tus Negocios", () -> {
				assertAnyTextVisible(driver, wait, Arrays.asList("Tus Negocios", "Tus negocios"));
				assertAnyTextVisible(driver, wait, Arrays.asList("Agregar Negocio", "Agregar negocio"));
				assertAnyTextVisible(driver, wait, Arrays.asList("Tienes 2 de 3 negocios"));
				Assert.assertTrue("No se detectó una lista visible de negocios.", hasBusinessListVisible(driver));
			});

			runStep(stepResults, "Términos y Condiciones", () -> {
				terminosUrl = openLegalLinkAndValidate(driver, wait, "Términos y Condiciones", "Términos y Condiciones",
						"08-terminos-y-condiciones");
			});

			runStep(stepResults, "Política de Privacidad", () -> {
				politicaUrl = openLegalLinkAndValidate(driver, wait, "Política de Privacidad", "Política de Privacidad",
						"09-politica-de-privacidad");
			});
		} finally {
			printFinalReport(stepResults);
			driver.quit();
		}

		assertAllPassed(stepResults);
	}

	private void prepareInitialPage(final WebDriver driver, final WebDriverWait wait) {
		String loginUrl = getConfig("SALEADS_LOGIN_URL", "saleads.loginUrl");
		if (loginUrl != null && !loginUrl.trim().isEmpty()) {
			driver.get(loginUrl.trim());
			waitForUiLoad(driver, wait);
		}
	}

	private void expandMiNegocioIfCollapsed(final WebDriver driver, final WebDriverWait wait) {
		if (!isAnyTextVisible(driver, Arrays.asList("Administrar Negocios", "Administrar negocios"))) {
			clickAnyVisibleText(driver, wait, Arrays.asList("Mi Negocio", "Mi negocio"));
			waitForUiLoad(driver, wait);
		}
	}

	private String openLegalLinkAndValidate(final WebDriver driver, final WebDriverWait wait, final String linkText,
			final String headingText, final String screenshotName) {
		final String appWindow = driver.getWindowHandle();
		final Set<String> existingWindows = driver.getWindowHandles();
		final String previousUrl = driver.getCurrentUrl();

		clickAnyVisibleText(driver, wait, Arrays.asList(linkText));

		wait.until(d -> d.getWindowHandles().size() > existingWindows.size() || !d.getCurrentUrl().equals(previousUrl));

		Set<String> currentWindows = driver.getWindowHandles();
		boolean switchedToNewTab = false;

		if (currentWindows.size() > existingWindows.size()) {
			for (String handle : currentWindows) {
				if (!existingWindows.contains(handle)) {
					driver.switchTo().window(handle);
					switchedToNewTab = true;
					break;
				}
			}
		}

		waitForUiLoad(driver, wait);
		assertAnyTextVisible(driver, wait, Arrays.asList(headingText));
		Assert.assertTrue("No se detectó contenido legal visible en la página: " + headingText, hasLegalContentVisible(driver));

		takeScreenshot(driver, screenshotName);
		String finalUrl = driver.getCurrentUrl();

		if (switchedToNewTab) {
			driver.close();
			driver.switchTo().window(appWindow);
			waitForUiLoad(driver, wait);
		} else {
			driver.navigate().back();
			waitForUiLoad(driver, wait);
		}

		return finalUrl;
	}

	private void selectGoogleAccountIfPrompted(final WebDriver driver, final WebDriverWait wait) {
		List<By> selectors = Arrays.asList(
				By.xpath("//*[@data-identifier='" + GOOGLE_ACCOUNT_EMAIL + "']"),
				By.xpath("//*[normalize-space()='" + GOOGLE_ACCOUNT_EMAIL + "']"),
				By.xpath("//*[contains(@aria-label,'" + GOOGLE_ACCOUNT_EMAIL + "')]"));

		for (By selector : selectors) {
			WebElement account = tryFindVisible(driver, selector, SHORT_WAIT);
			if (account != null) {
				safeClick(driver, account);
				waitForUiLoad(driver, wait);
				return;
			}
		}
	}

	private void runStep(final Map<String, StepResult> stepResults, final String stepName, final ThrowingRunnable step) {
		try {
			step.run();
			stepResults.put(stepName, StepResult.pass("OK"));
		} catch (Throwable error) {
			stepResults.put(stepName, StepResult.fail(error.getMessage()));
		}
	}

	private void assertAllPassed(final LinkedHashMap<String, StepResult> stepResults) {
		StringBuilder failureSummary = new StringBuilder();
		for (Map.Entry<String, StepResult> entry : stepResults.entrySet()) {
			if (!entry.getValue().passed) {
				failureSummary.append("- ").append(entry.getKey()).append(": ").append(entry.getValue().details).append("\n");
			}
		}

		if (failureSummary.length() > 0) {
			Assert.fail("Fallaron validaciones del workflow Mi Negocio:\n" + failureSummary);
		}
	}

	private void printFinalReport(final LinkedHashMap<String, StepResult> stepResults) {
		System.out.println("=== SaleADS Mi Negocio Full Test Report ===");
		for (Map.Entry<String, StepResult> entry : stepResults.entrySet()) {
			String status = entry.getValue().passed ? "PASS" : "FAIL";
			System.out.println(entry.getKey() + ": " + status + " - " + entry.getValue().details);
		}
		System.out.println("Términos y Condiciones URL: " + terminosUrl);
		System.out.println("Política de Privacidad URL: " + politicaUrl);
		System.out.println("Evidencia en: " + EVIDENCE_DIR.toAbsolutePath());
	}

	private boolean hasLegalContentVisible(final WebDriver driver) {
		List<WebElement> paragraphs = driver.findElements(By.xpath("//p[normalize-space()] | //li[normalize-space()]"));
		int visibleTextBlocks = 0;

		for (WebElement element : paragraphs) {
			if (element.isDisplayed() && element.getText().trim().length() > 25) {
				visibleTextBlocks++;
			}
		}

		return visibleTextBlocks >= 2;
	}

	private boolean hasBusinessListVisible(final WebDriver driver) {
		List<WebElement> candidates = driver.findElements(By.xpath(
				"//*[contains(translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚ','abcdefghijklmnopqrstuvwxyzáéíóú'),'tus negocios')]/ancestor::*[1]//li"
						+ " | //*[contains(translate(@class,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'business')]"
						+ " | //table//tr"));

		for (WebElement candidate : candidates) {
			if (candidate.isDisplayed()) {
				return true;
			}
		}

		return false;
	}

	private boolean hasLikelyUserNameVisible(final WebDriver driver) {
		List<WebElement> texts = driver.findElements(By.xpath("//h1 | //h2 | //h3 | //p | //span | //div"));
		for (WebElement textElement : texts) {
			if (!textElement.isDisplayed()) {
				continue;
			}

			String value = textElement.getText().trim();
			if (value.isEmpty()) {
				continue;
			}

			String upper = value.toUpperCase(Locale.ROOT);
			if (value.contains("@")) {
				continue;
			}
			if (upper.contains("INFORMACIÓN GENERAL") || upper.contains("INFORMACION GENERAL")
					|| upper.contains("BUSINESS PLAN") || upper.contains("CAMBIAR PLAN") || upper.contains("CUENTA CREADA")
					|| upper.contains("ESTADO ACTIVO") || upper.contains("IDIOMA")) {
				continue;
			}
			if (value.length() >= 5 && value.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*")) {
				return true;
			}
		}
		return false;
	}

	private void assertSidebarVisible(final WebDriver driver) {
		List<By> sidebarCandidates = Arrays.asList(By.cssSelector("aside"), By.xpath("//nav"),
				By.xpath("//*[contains(translate(@class,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'sidebar')]"));

		for (By candidate : sidebarCandidates) {
			WebElement element = tryFindVisible(driver, candidate, SHORT_WAIT);
			if (element != null) {
				return;
			}
		}

		Assert.fail("No se encontró la barra lateral de navegación.");
	}

	private void clickTextIfVisible(final WebDriver driver, final WebDriverWait wait, final String text) {
		if (isAnyTextVisible(driver, Arrays.asList(text))) {
			clickAnyVisibleText(driver, wait, Arrays.asList(text));
		}
	}

	private void clickAnyVisibleText(final WebDriver driver, final WebDriverWait wait, final List<String> texts) {
		for (String text : texts) {
			WebElement firstVisible = tryFindVisible(driver, byVisibleText(text), SHORT_WAIT);
			if (firstVisible != null && safeClick(driver, firstVisible)) {
				waitForUiLoad(driver, wait);
				return;
			}

			List<WebElement> candidates = driver.findElements(byVisibleText(text));
			for (WebElement candidate : candidates) {
				if (!candidate.isDisplayed()) {
					continue;
				}
				if (safeClick(driver, candidate)) {
					waitForUiLoad(driver, wait);
					return;
				}
			}
		}

		Assert.fail("No se encontró ningún elemento clickeable con textos: " + texts);
	}

	private void assertAnyTextVisible(final WebDriver driver, final WebDriverWait wait, final List<String> texts) {
		for (String text : texts) {
			WebElement element = tryFindVisible(driver, byVisibleText(text), SHORT_WAIT);
			if (element != null) {
				return;
			}
		}

		waitForUiLoad(driver, wait);
		Assert.fail("No se encontró texto visible para ninguna de estas opciones: " + texts);
	}

	private boolean isAnyTextVisible(final WebDriver driver, final List<String> texts) {
		for (String text : texts) {
			if (tryFindVisible(driver, byVisibleText(text), Duration.ofSeconds(2)) != null) {
				return true;
			}
		}
		return false;
	}

	private WebElement findVisible(final WebDriver driver, final WebDriverWait wait, final By locator) {
		return wait.until(d -> {
			for (WebElement element : d.findElements(locator)) {
				if (element.isDisplayed()) {
					return element;
				}
			}
			return null;
		});
	}

	private WebElement tryFindVisible(final WebDriver driver, final By locator, final Duration timeout) {
		try {
			WebDriverWait shortWait = new WebDriverWait(driver, timeout);
			return shortWait.until(d -> {
				for (WebElement element : d.findElements(locator)) {
					if (element.isDisplayed()) {
						return element;
					}
				}
				return null;
			});
		} catch (TimeoutException timeoutException) {
			return null;
		}
	}

	private boolean safeClick(final WebDriver driver, final WebElement element) {
		WebElement clickable = element;

		if (!isDirectlyClickable(element)) {
			List<WebElement> clickableAncestors = element
					.findElements(By.xpath("./ancestor-or-self::*[self::button or self::a or @role='button']"));
			for (WebElement ancestor : clickableAncestors) {
				if (ancestor.isDisplayed()) {
					clickable = ancestor;
					break;
				}
			}
		}

		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", clickable);

		try {
			clickable.click();
			return true;
		} catch (Exception clickError) {
			try {
				((JavascriptExecutor) driver).executeScript("arguments[0].click();", clickable);
				return true;
			} catch (Exception jsClickError) {
				return false;
			}
		}
	}

	private boolean isDirectlyClickable(final WebElement element) {
		final String tag = element.getTagName().toLowerCase(Locale.ROOT);
		return "button".equals(tag) || "a".equals(tag) || "input".equals(tag);
	}

	private void waitForUiLoad(final WebDriver driver, final WebDriverWait wait) {
		wait.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		try {
			Thread.sleep(600);
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	private Path takeScreenshot(final WebDriver driver, final String name) {
		try {
			String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
			Path destination = EVIDENCE_DIR.resolve(timestamp + "-" + name + ".png");
			byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			Files.write(destination, screenshot);
			return destination;
		} catch (IOException ioException) {
			throw new RuntimeException("No se pudo guardar screenshot para: " + name, ioException);
		}
	}

	private By byVisibleText(final String text) {
		String escaped = escapeXpathLiteral(text);
		return By.xpath("//*[normalize-space()=" + escaped + "] | //*[contains(normalize-space(), " + escaped + ")]");
	}

	private String escapeXpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		StringBuilder builder = new StringBuilder("concat(");
		String[] parts = value.split("'");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				builder.append(", \"'\", ");
			}
			builder.append("'").append(parts[i]).append("'");
		}
		builder.append(")");
		return builder.toString();
	}

	private String getConfig(final String envKey, final String propertyKey) {
		String envValue = System.getenv(envKey);
		if (envValue != null && !envValue.trim().isEmpty()) {
			return envValue;
		}
		String propertyValue = System.getProperty(propertyKey);
		if (propertyValue != null && !propertyValue.trim().isEmpty()) {
			return propertyValue;
		}
		return null;
	}

	private boolean getBooleanConfig(final String envKey, final String propertyKey, final boolean defaultValue) {
		String raw = getConfig(envKey, propertyKey);
		if (raw == null) {
			return defaultValue;
		}
		return "true".equalsIgnoreCase(raw.trim()) || "1".equals(raw.trim()) || "yes".equalsIgnoreCase(raw.trim());
	}

	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private static final class StepResult {
		private final boolean passed;
		private final String details;

		private StepResult(final boolean passed, final String details) {
			this.passed = passed;
			this.details = details;
		}

		private static StepResult pass(final String details) {
			return new StepResult(true, details);
		}

		private static StepResult fail(final String details) {
			return new StepResult(false, details == null ? "Sin detalle" : details);
		}
	}
}
