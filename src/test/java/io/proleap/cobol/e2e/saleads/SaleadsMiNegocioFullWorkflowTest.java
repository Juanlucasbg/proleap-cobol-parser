package io.proleap.cobol.e2e.saleads;

import static org.junit.Assert.assertTrue;

import java.io.File;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.After;
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
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMINISTRAR_NEGOCIOS_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFO_GENERAL = "Información General";
	private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Términos y Condiciones";
	private static final String REPORT_PRIVACIDAD = "Política de Privacidad";

	private final LinkedHashMap<String, String> finalReport = new LinkedHashMap<>();
	private final List<String> failedSteps = new ArrayList<>();
	private final LinkedHashMap<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String loginUrl;

	@Before
	public void setUp() throws Exception {
		final String runE2e = System.getenv("RUN_SALEADS_MI_NEGOCIO_E2E");
		Assume.assumeTrue("Skipping SaleADS E2E. Set RUN_SALEADS_MI_NEGOCIO_E2E=true to execute.",
				"true".equalsIgnoreCase(runE2e));

		loginUrl = readEnv("SALEADS_LOGIN_URL");
		Assume.assumeTrue("Skipping SaleADS E2E. Set SALEADS_LOGIN_URL for the target environment login page.",
				loginUrl != null && !loginUrl.isBlank());

		evidenceDir = Path.of("target", "saleads-evidence",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
		Files.createDirectories(evidenceDir);

		final ChromeOptions options = new ChromeOptions();
		if (!"false".equalsIgnoreCase(readEnvOrDefault("SALEADS_HEADLESS", "true"))) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		driver.get(loginUrl);
		waitForUiLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void shouldValidateSaleadsMiNegocioWorkflow() throws Exception {
		runStep(REPORT_LOGIN, this::loginWithGoogle);
		runStep(REPORT_MI_NEGOCIO_MENU, this::openMiNegocioMenu);
		runStep(REPORT_AGREGAR_NEGOCIO_MODAL, this::validateAgregarNegocioModal);
		runStep(REPORT_ADMINISTRAR_NEGOCIOS_VIEW, this::openAdministrarNegocios);
		runStep(REPORT_INFO_GENERAL, this::validateInformacionGeneral);
		runStep(REPORT_DETALLES_CUENTA, this::validateDetallesDeLaCuenta);
		runStep(REPORT_TUS_NEGOCIOS, this::validateTusNegocios);
		runStep(REPORT_TERMINOS, () -> validateLegalDocument("Términos y Condiciones"));
		runStep(REPORT_PRIVACIDAD, () -> validateLegalDocument("Política de Privacidad"));

		final String reportText = buildReportText();
		Files.writeString(evidenceDir.resolve("final-report.txt"), reportText);
		System.out.println(reportText);

		assertTrue("SaleADS Mi Negocio workflow has failing validations. See final report above.",
				failedSteps.isEmpty());
	}

	private void loginWithGoogle() throws Exception {
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Ingresar con Google", "Google");
		waitForUiLoad();
		clickIfVisible("juanlucasbarbiergarzon@gmail.com", Duration.ofSeconds(10));

		waitVisibleAny(List.of(By.cssSelector("aside"), By.cssSelector("[class*='sidebar']"), containsText("Negocio")));
		captureScreenshot("01-dashboard-loaded");
	}

	private void openMiNegocioMenu() throws Exception {
		waitVisibleAny(List.of(By.cssSelector("aside"), By.cssSelector("[class*='sidebar']"), containsText("Negocio")));
		waitVisibleText("Negocio");
		clickByVisibleText("Mi Negocio");

		waitVisibleText("Agregar Negocio");
		waitVisibleText("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void validateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");
		waitVisibleText("Crear Nuevo Negocio");
		waitVisibleText("Nombre del Negocio");
		waitVisibleText("Tienes 2 de 3 negocios");
		waitVisibleText("Cancelar");
		waitVisibleText("Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal");

		final WebElement nameInput = waitVisibleAny(List.of(
				By.xpath("//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"),
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//input[contains(@aria-label, 'Nombre del Negocio')]")));
		nameInput.click();
		nameInput.clear();
		nameInput.sendKeys("Negocio Prueba Automatizacion");
		waitForUiLoad();
		clickByVisibleText("Cancelar");
		waitUntilInvisible(containsText("Crear Nuevo Negocio"));
	}

	private void openAdministrarNegocios() throws Exception {
		if (!isVisible(containsText("Administrar Negocios"), Duration.ofSeconds(3))) {
			clickByVisibleText("Mi Negocio");
		}
		clickByVisibleText("Administrar Negocios");

		waitVisibleText("Información General");
		waitVisibleText("Detalles de la Cuenta");
		waitVisibleText("Tus Negocios");
		waitVisibleAny(List.of(containsText("Sección Legal"), containsText("Terminos y Condiciones"),
				containsText("Términos y Condiciones")));
		captureScreenshot("04-administrar-negocios-page");
	}

	private void validateInformacionGeneral() {
		final WebElement infoSection = resolveSectionContainer("Información General");
		waitVisibleAny(List.of(containsText("BUSINESS PLAN"), containsText("Business Plan")));
		waitVisibleText("Cambiar Plan");

		final Pattern emailPattern = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
		final Pattern namePattern = Pattern.compile("^[\\p{L}][\\p{L}\\s'.-]{2,}$");

		final String infoSectionText = infoSection.getText();
		final boolean hasEmail = emailPattern.matcher(infoSectionText).find();
		assertTrue("User email is not visible in Informacion General.", hasEmail);

		final boolean hasNameLikeText = Arrays.stream(infoSectionText.split("\\R")).map(String::trim)
				.anyMatch(line -> !line.isBlank() && !line.equalsIgnoreCase("Información General")
						&& !line.equalsIgnoreCase("Informacion General") && !line.equalsIgnoreCase("BUSINESS PLAN")
						&& !line.equalsIgnoreCase("Cambiar Plan") && !line.contains("@") && namePattern.matcher(line).matches());
		assertTrue("User name is not visible in Informacion General.", hasNameLikeText);
	}

	private void validateDetallesDeLaCuenta() {
		waitVisibleText("Detalles de la Cuenta");
		waitVisibleAny(List.of(containsText("Cuenta creada"), containsText("Creada")));
		waitVisibleAny(List.of(containsText("Estado activo"), containsText("Activo")));
		waitVisibleAny(List.of(containsText("Idioma seleccionado"), containsText("Idioma")));
	}

	private void validateTusNegocios() {
		final WebElement section = resolveSectionContainer("Tus Negocios");
		waitVisibleText("Agregar Negocio");
		waitVisibleText("Tienes 2 de 3 negocios");

		final List<WebElement> businessCandidates = section.findElements(
				By.xpath(".//*[self::li or self::tr or contains(@class, 'business') or contains(@class, 'negocio')]"));
		final boolean hasListStructure = !businessCandidates.isEmpty();
		final boolean hasRichTextContent = section.getText().split("\\R").length > 3;
		assertTrue("Business list is not visible in Tus Negocios.", hasListStructure || hasRichTextContent);
	}

	private void validateLegalDocument(final String label) throws Exception {
		final String originalHandle = driver.getWindowHandle();
		final String originalUrl = driver.getCurrentUrl();
		final Set<String> originalHandles = new LinkedHashSet<>(driver.getWindowHandles());

		final String asciiLabel = stripAccents(label);
		clickByVisibleText(label, asciiLabel);
		waitForUiLoad();

		wait.until((ExpectedCondition<Boolean>) webDriver -> {
			final boolean openedTab = webDriver.getWindowHandles().size() > originalHandles.size();
			final boolean sameTabNavigation = !webDriver.getCurrentUrl().equals(originalUrl);
			return openedTab || sameTabNavigation;
		});

		boolean openedNewTab = false;
		if (driver.getWindowHandles().size() > originalHandles.size()) {
			openedNewTab = true;
			for (final String handle : driver.getWindowHandles()) {
				if (!originalHandles.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
			waitForUiLoad();
		}

		waitVisibleAny(List.of(containsText(label), containsText(asciiLabel)));
		final String legalText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Legal content text was not visible for " + label + ".", legalText != null && legalText.length() > 120);
		captureScreenshot(sanitize(label));
		legalUrls.put(label, driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private void runStep(final String reportField, final Step step) {
		try {
			step.run();
			finalReport.put(reportField, "PASS");
		} catch (final Throwable throwable) {
			final String message = throwable.getMessage() == null ? throwable.getClass().getSimpleName()
					: throwable.getMessage();
			finalReport.put(reportField, "FAIL - " + message);
			failedSteps.add(reportField + ": " + message);
		}
	}

	private String buildReportText() {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio Final Report").append(System.lineSeparator());
		for (final Map.Entry<String, String> entry : finalReport.entrySet()) {
			builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
		}
		if (!legalUrls.isEmpty()) {
			builder.append(System.lineSeparator()).append("Final URLs").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : legalUrls.entrySet()) {
				builder.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
		}
		if (!failedSteps.isEmpty()) {
			builder.append(System.lineSeparator()).append("Failed validations").append(System.lineSeparator());
			for (final String failedStep : failedSteps) {
				builder.append("- ").append(failedStep).append(System.lineSeparator());
			}
		}
		builder.append(System.lineSeparator()).append("Evidence directory: ").append(evidenceDir.toAbsolutePath());
		return builder.toString();
	}

	private void clickByVisibleText(final String... labels) {
		for (final String label : labels) {
			final String xpath = "//*[self::a or self::button or @role='button' or self::span or self::div]"
					+ "[contains(normalize-space(.), " + toXPathLiteral(label) + ")]";
			if (!isVisible(By.xpath(xpath), Duration.ofSeconds(10))) {
				continue;
			}
			final List<WebElement> candidates = driver.findElements(By.xpath(xpath));
			for (final WebElement candidate : candidates) {
				if (candidate.isDisplayed()) {
					try {
						candidate.click();
						waitForUiLoad();
						return;
					} catch (final Exception clickError) {
						clickViaJs(candidate);
						waitForUiLoad();
						return;
					}
				}
			}
		}

		throw new IllegalStateException("Unable to click any element for labels: " + Arrays.toString(labels));
	}

	private void clickIfVisible(final String visibleText, final Duration timeout) {
		final long end = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < end) {
			final List<WebElement> elements = driver.findElements(containsText(visibleText));
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					try {
						element.click();
						waitForUiLoad();
						return;
					} catch (final Exception clickError) {
						clickViaJs(element);
						waitForUiLoad();
						return;
					}
				}
			}
			sleep(250);
		}
	}

	private void clickViaJs(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
	}

	private WebElement waitVisibleText(final String text) {
		return waitVisibleAny(List.of(containsText(text)));
	}

	private WebElement waitVisibleAny(final List<By> locators) {
		for (final By locator : locators) {
			try {
				final WebElement element = new WebDriverWait(driver, Duration.ofSeconds(5))
						.until(webDriver -> firstVisible(locator));
				if (element != null) {
					return element;
				}
			} catch (final TimeoutException ignored) {
				// try next locator
			}
		}

		throw new TimeoutException("None of the expected elements were visible: " + locators);
	}

	private WebElement firstVisible(final By by) {
		final List<WebElement> elements = driver.findElements(by);
		for (final WebElement element : elements) {
			if (element.isDisplayed()) {
				return element;
			}
		}
		return null;
	}

	private boolean isVisible(final By by, final Duration timeout) {
		final long end = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < end) {
			if (firstVisible(by) != null) {
				return true;
			}
			sleep(150);
		}
		return false;
	}

	private void waitUntilInvisible(final By by) {
		wait.until(webDriver -> {
			final Optional<WebElement> element = webDriver.findElements(by).stream().filter(WebElement::isDisplayed).findFirst();
			return element.isEmpty();
		});
	}

	private By containsText(final String text) {
		return By.xpath("//*[contains(normalize-space(.), " + toXPathLiteral(text) + ")]");
	}

	private String toXPathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}

		final StringBuilder result = new StringBuilder("concat(");
		final char[] chars = text.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			if (i > 0) {
				result.append(", ");
			}
			if (chars[i] == '\'') {
				result.append("\"'\"");
			} else {
				result.append("'").append(chars[i]).append("'");
			}
		}
		result.append(")");
		return result.toString();
	}

	private void waitForUiLoad() {
		wait.until(webDriver -> "complete".equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState")));
		sleep(400);
	}

	private void captureScreenshot(final String fileName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final String safeName = sanitize(fileName);
		Files.copy(screenshot.toPath(), evidenceDir.resolve(safeName + ".png"), StandardCopyOption.REPLACE_EXISTING);
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for UI load.", interruptedException);
		}
	}

	private String sanitize(final String value) {
		return stripAccents(value).toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String stripAccents(final String value) {
		return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
	}

	private WebElement resolveSectionContainer(final String sectionTitle) {
		final WebElement heading = waitVisibleText(sectionTitle);
		final List<WebElement> containers = heading
				.findElements(By.xpath("ancestor::*[self::section or self::article or self::div][1]"));
		return containers.isEmpty() ? driver.findElement(By.tagName("body")) : containers.get(0);
	}

	private String readEnv(final String key) {
		return System.getenv(key);
	}

	private String readEnvOrDefault(final String key, final String defaultValue) {
		final String value = readEnv(key);
		return value == null || value.isBlank() ? defaultValue : value;
	}

	@FunctionalInterface
	private interface Step {
		void run() throws Exception;
	}
}
