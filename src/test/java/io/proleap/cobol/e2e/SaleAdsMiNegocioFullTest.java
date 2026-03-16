package io.proleap.cobol.e2e;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
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
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleAdsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, String> report = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		Assume.assumeTrue(
				"Skipping SaleADS E2E test. Set SALEADS_E2E_ENABLED=true and SALEADS_START_URL to run this workflow.",
				"true".equalsIgnoreCase(getEnv("SALEADS_E2E_ENABLED", "false")));

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1600,1200");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		if (!"false".equalsIgnoreCase(getEnv("SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(getTimeoutSeconds()));
		evidenceDir = Path.of("target", "surefire-reports", "saleads-mi-negocio");
		Files.createDirectories(evidenceDir);

		final String startUrl = getEnv("SALEADS_START_URL", "");
		Assume.assumeTrue("SALEADS_START_URL is required to open the active SaleADS environment login page.",
				!startUrl.isBlank());
		driver.get(startUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() throws IOException {
		if (evidenceDir != null) {
			writeFinalReport();
		}

		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		executeStep("Login", () -> {
			clickFirstVisibleText("Sign in with Google", "Iniciar con Google", "Continuar con Google", "Google");
			selectGoogleAccountIfPrompted(GOOGLE_ACCOUNT_EMAIL);
			waitForUiToLoad();

			assertTextVisible("Negocio");
			assertSidebarVisible();
			takeScreenshot("01_dashboard_loaded.png");
		});

		executeStep("Mi Negocio menu", () -> {
			expandMiNegocioMenu();
			assertTextVisible("Agregar Negocio");
			assertTextVisible("Administrar Negocios");
			takeScreenshot("02_mi_negocio_menu_expanded.png");
		});

		executeStep("Agregar Negocio modal", () -> {
			clickFirstVisibleText("Agregar Negocio");
			assertTextVisible("Crear Nuevo Negocio");
			final WebElement nombreNegocioInput = findInputByLabel("Nombre del Negocio");
			Assert.assertNotNull("Input 'Nombre del Negocio' was not found.", nombreNegocioInput);
			assertTextVisible("Tienes 2 de 3 negocios");
			assertTextVisible("Cancelar");
			assertTextVisible("Crear Negocio");

			takeScreenshot("03_agregar_negocio_modal.png");

			// Optional interaction requested by the scenario.
			nombreNegocioInput.click();
			nombreNegocioInput.clear();
			nombreNegocioInput.sendKeys("Negocio Prueba Automatización");
			clickFirstVisibleText("Cancelar");
			waitForUiToLoad();
		});

		executeStep("Administrar Negocios view", () -> {
			expandMiNegocioMenu();
			clickFirstVisibleText("Administrar Negocios");
			waitForUiToLoad();

			assertTextVisible("Información General");
			assertTextVisible("Detalles de la Cuenta");
			assertTextVisible("Tus Negocios");
			assertTextVisible("Sección Legal");
			takeScreenshot("04_administrar_negocios_page.png");
		});

		executeStep("Información General", () -> {
			final WebElement section = findSectionByHeading("Información General");
			Assert.assertNotNull("Section 'Información General' was not found.", section);

			final String sectionText = normalizeWhitespace(section.getText());
			final Matcher emailMatcher = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
					.matcher(sectionText);
			Assert.assertTrue("User email is not visible in 'Información General'.", emailMatcher.find());
			Assert.assertTrue("Text 'BUSINESS PLAN' is not visible in 'Información General'.",
					sectionText.contains("BUSINESS PLAN"));
			Assert.assertTrue("Button/text 'Cambiar Plan' is not visible in 'Información General'.",
					sectionText.contains("Cambiar Plan"));

			final long nonEmptyLines = Arrays.stream(sectionText.split("\\R")).map(String::trim).filter(s -> !s.isEmpty())
					.count();
			Assert.assertTrue("User name is not visible in 'Información General'.", nonEmptyLines >= 4);
		});

		executeStep("Detalles de la Cuenta", () -> {
			final WebElement section = findSectionByHeading("Detalles de la Cuenta");
			Assert.assertNotNull("Section 'Detalles de la Cuenta' was not found.", section);

			final String sectionText = normalizeWhitespace(section.getText());
			Assert.assertTrue("'Cuenta creada' is not visible.", sectionText.contains("Cuenta creada"));
			Assert.assertTrue("'Estado activo' is not visible.", sectionText.contains("Estado activo"));
			Assert.assertTrue("'Idioma seleccionado' is not visible.", sectionText.contains("Idioma seleccionado"));
		});

		executeStep("Tus Negocios", () -> {
			final WebElement section = findSectionByHeading("Tus Negocios");
			Assert.assertNotNull("Section 'Tus Negocios' was not found.", section);

			final String sectionText = normalizeWhitespace(section.getText());
			Assert.assertTrue("Button/text 'Agregar Negocio' is not visible in 'Tus Negocios'.",
					sectionText.contains("Agregar Negocio"));
			Assert.assertTrue("Text 'Tienes 2 de 3 negocios' is not visible in 'Tus Negocios'.",
					sectionText.contains("Tienes 2 de 3 negocios"));

			final List<WebElement> candidateBusinessItems = section.findElements(By.xpath(
					".//*[self::li or self::tr or self::article or (self::div and (contains(@class, 'business') or contains(@class, 'negocio')))][normalize-space()]"));
			Assert.assertTrue("Business list is not visible.", !candidateBusinessItems.isEmpty());
		});

		executeStep("Términos y Condiciones", () -> {
			final String finalUrl = openLegalDocumentAndReturn("Términos y Condiciones", "Terminos y Condiciones",
					"Términos y Condiciones", "Terminos y Condiciones", "08_terminos_y_condiciones.png");
			report.put("Términos y Condiciones URL", finalUrl);
		});

		executeStep("Política de Privacidad", () -> {
			final String finalUrl = openLegalDocumentAndReturn("Política de Privacidad", "Politica de Privacidad",
					"Política de Privacidad", "Politica de Privacidad", "09_politica_de_privacidad.png");
			report.put("Política de Privacidad URL", finalUrl);
		});

		Assert.assertTrue("Workflow has validation failures:\n" + String.join("\n", failures), failures.isEmpty());
	}

	private void executeStep(final String name, final ThrowingRunnable step) {
		try {
			step.run();
			report.put(name, "PASS");
		} catch (final Throwable ex) {
			report.put(name, "FAIL");
			failures.add(name + " -> " + ex.getMessage());
		}
	}

	private void selectGoogleAccountIfPrompted(final String email) {
		final Set<String> originalHandles = new LinkedHashSet<>(driver.getWindowHandles());

		try {
			waitShort().until((ExpectedCondition<Boolean>) d -> d != null && d.getWindowHandles().size() > 1);
		} catch (final TimeoutException ignored) {
			// Google selector might open in the same tab.
		}

		for (final String handle : driver.getWindowHandles()) {
			driver.switchTo().window(handle);
			waitForUiToLoad();

			final boolean inGoogleContext = driver.getCurrentUrl().contains("accounts.google.")
					|| !findElementsContainingText(email).isEmpty();

			if (inGoogleContext) {
				clickFirstVisibleText(email);
				break;
			}
		}

		if (!driver.getWindowHandles().isEmpty()) {
			driver.switchTo().window(driver.getWindowHandles().iterator().next());
		}

		for (final String handle : originalHandles) {
			if (driver.getWindowHandles().contains(handle)) {
				driver.switchTo().window(handle);
				return;
			}
		}
	}

	private void expandMiNegocioMenu() {
		if (isTextVisible("Agregar Negocio") && isTextVisible("Administrar Negocios")) {
			return;
		}

		if (isTextVisible("Negocio")) {
			clickFirstVisibleText("Negocio");
		}
		clickFirstVisibleText("Mi Negocio");
		waitForUiToLoad();
	}

	private String openLegalDocumentAndReturn(final String linkText, final String fallbackLinkText,
			final String headingText, final String fallbackHeadingText, final String screenshotName) throws IOException {
		final String originalWindow = driver.getWindowHandle();
		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		final String currentUrlBeforeClick = driver.getCurrentUrl();

		clickFirstVisibleText(linkText, fallbackLinkText);

		String activeWindow = originalWindow;
		try {
			waitShort().until((ExpectedCondition<Boolean>) d -> d != null
					&& d.getWindowHandles().size() > handlesBeforeClick.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!handlesBeforeClick.contains(handle)) {
					activeWindow = handle;
					break;
				}
			}
		} catch (final TimeoutException ignored) {
			// Link may have opened in the same tab.
		}

		driver.switchTo().window(activeWindow);

		if (activeWindow.equals(originalWindow)) {
			wait.until((ExpectedCondition<Boolean>) d -> d != null
					&& (!d.getCurrentUrl().equals(currentUrlBeforeClick) || isTextVisible(headingText)
							|| isTextVisible(fallbackHeadingText)));
		}

		waitForUiToLoad();
		waitForAnyVisibleText(headingText, fallbackHeadingText);

		final String legalContent = normalizeWhitespace(driver.findElement(By.tagName("body")).getText());
		Assert.assertTrue("Legal content text is not visible.", legalContent.length() > 120);

		takeScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (!activeWindow.equals(originalWindow)) {
			driver.close();
			driver.switchTo().window(originalWindow);
		} else {
			driver.navigate().back();
		}
		waitForUiToLoad();
		return finalUrl;
	}

	private void clickFirstVisibleText(final String... texts) {
		final List<String> candidates = Arrays.asList(texts);
		for (final String text : candidates) {
			for (final WebElement element : findClickableElementsContainingText(text)) {
				if (element.isDisplayed() && element.isEnabled()) {
					try {
						wait.until(ExpectedConditions.elementToBeClickable(element));
						element.click();
						waitForUiToLoad();
						return;
					} catch (final Exception clickException) {
						((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
						waitForUiToLoad();
						return;
					}
				}
			}
		}

		throw new IllegalStateException("No clickable element found for texts: " + candidates);
	}

	private WebElement findInputByLabel(final String labelText) {
		for (final WebElement label : findElementsContainingText(labelText)) {
			if (!"label".equalsIgnoreCase(label.getTagName()) || !label.isDisplayed()) {
				continue;
			}

			final String forAttribute = label.getAttribute("for");
			if (forAttribute != null && !forAttribute.isBlank()) {
				final List<WebElement> byId = driver.findElements(By.id(forAttribute));
				if (!byId.isEmpty() && byId.get(0).isDisplayed()) {
					return byId.get(0);
				}
			}

			final List<WebElement> nestedInputs = label.findElements(By.xpath(".//input"));
			if (!nestedInputs.isEmpty() && nestedInputs.get(0).isDisplayed()) {
				return nestedInputs.get(0);
			}

			final List<WebElement> siblingInputs = label.findElements(By.xpath("./following::input[1]"));
			if (!siblingInputs.isEmpty() && siblingInputs.get(0).isDisplayed()) {
				return siblingInputs.get(0);
			}
		}

		final List<WebElement> placeholders = driver
				.findElements(By.xpath("//input[contains(@placeholder, " + toXPathLiteral(labelText) + ")]"));
		return placeholders.isEmpty() ? null : placeholders.get(0);
	}

	private WebElement findSectionByHeading(final String headingText) {
		waitForAnyVisibleText(headingText);
		final List<WebElement> headings = findElementsContainingText(headingText);

		for (final WebElement heading : headings) {
			if (!heading.isDisplayed()) {
				continue;
			}

			final List<WebElement> parentSections = heading
					.findElements(By.xpath("./ancestor::*[self::section or self::article or self::div][1]"));
			if (!parentSections.isEmpty() && parentSections.get(0).isDisplayed()) {
				return parentSections.get(0);
			}
		}

		return null;
	}

	private void assertTextVisible(final String text) {
		waitForAnyVisibleText(text);
		Assert.assertTrue("Expected text is not visible: " + text, isTextVisible(text));
	}

	private void assertSidebarVisible() {
		final List<WebElement> sidebars = driver.findElements(By.xpath("//aside|//nav"));
		boolean visibleSidebarFound = false;
		for (final WebElement sidebar : sidebars) {
			if (sidebar.isDisplayed()) {
				visibleSidebarFound = true;
				break;
			}
		}
		Assert.assertTrue("Left sidebar navigation is not visible.", visibleSidebarFound);
	}

	private void waitForAnyVisibleText(final String... texts) {
		wait.until((ExpectedCondition<Boolean>) d -> {
			if (d == null) {
				return false;
			}
			for (final String text : texts) {
				if (isTextVisible(text)) {
					return true;
				}
			}
			return false;
		});
	}

	private boolean isTextVisible(final String text) {
		final List<WebElement> elements = findElementsContainingText(text);
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return true;
			}
		}
		return false;
	}

	private List<WebElement> findElementsContainingText(final String text) {
		final String escapedText = toXPathLiteral(text);
		final String xpath = "//*[contains(normalize-space(.), " + escapedText + ")]";
		return driver.findElements(By.xpath(xpath));
	}

	private List<WebElement> findClickableElementsContainingText(final String text) {
		final String escapedText = toXPathLiteral(text);
		final String xpath = "//*[self::button or self::a or @role='button' or "
				+ "(self::input and (@type='button' or @type='submit')) or self::span or self::div or self::li]"
				+ "[contains(normalize-space(.), " + escapedText + ")]";
		return driver.findElements(By.xpath(xpath));
	}

	private void waitForUiToLoad() {
		wait.until((ExpectedCondition<Boolean>) d -> {
			if (d == null) {
				return false;
			}
			return "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState"));
		});
	}

	private void takeScreenshot(final String fileName) throws IOException {
		final Path targetFile = evidenceDir.resolve(fileName);
		final Path screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(screenshot, targetFile, StandardCopyOption.REPLACE_EXISTING);
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder content = new StringBuilder();
		content.append("# SaleADS Mi Negocio Workflow - Final Report").append(System.lineSeparator())
				.append(System.lineSeparator());

		for (final Map.Entry<String, String> entry : report.entrySet()) {
			content.append("- ").append(entry.getKey()).append(": ").append(entry.getValue())
					.append(System.lineSeparator());
		}

		if (!failures.isEmpty()) {
			content.append(System.lineSeparator()).append("## Failures").append(System.lineSeparator());
			for (final String failure : failures) {
				content.append("- ").append(failure).append(System.lineSeparator());
			}
		}

		Files.createDirectories(evidenceDir);
		Files.writeString(evidenceDir.resolve("final-report.md"), content.toString(), StandardCharsets.UTF_8);
	}

	private int getTimeoutSeconds() {
		return Integer.parseInt(getEnv("SALEADS_TIMEOUT_SECONDS", "30"));
	}

	private String getEnv(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		return value == null ? defaultValue : value.trim();
	}

	private WebDriverWait waitShort() {
		return new WebDriverWait(driver, Duration.ofSeconds(10));
	}

	private String normalizeWhitespace(final String value) {
		return value == null ? "" : value.replace('\u00A0', ' ').trim();
	}

	private String toXPathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}

		final String[] parts = value.split("'");
		final StringBuilder xpathBuilder = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			xpathBuilder.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				xpathBuilder.append(", \"'\", ");
			}
		}
		xpathBuilder.append(")");
		return xpathBuilder.toString();
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}
}
