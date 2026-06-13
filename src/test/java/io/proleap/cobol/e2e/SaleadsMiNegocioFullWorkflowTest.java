package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, String> report = new LinkedHashMap<>();
	private final Map<String, String> reportMetadata = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private String appWindowHandle;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		report.put("Login", "NOT_RUN");
		report.put("Mi Negocio menu", "NOT_RUN");
		report.put("Agregar Negocio modal", "NOT_RUN");
		report.put("Administrar Negocios view", "NOT_RUN");
		report.put("Información General", "NOT_RUN");
		report.put("Detalles de la Cuenta", "NOT_RUN");
		report.put("Tus Negocios", "NOT_RUN");
		report.put("Términos y Condiciones", "NOT_RUN");
		report.put("Política de Privacidad", "NOT_RUN");

		final var timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		evidenceDir = Path.of("target", "saleads-evidence", timestamp);
		Files.createDirectories(evidenceDir);
	}

	@After
	public void tearDown() throws IOException {
		try {
			writeFinalReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final String baseUrl = valueOrNull(System.getenv("SALEADS_BASE_URL"), System.getProperty("saleads.baseUrl"));
		Assume.assumeTrue(
				"Set SALEADS_BASE_URL or -Dsaleads.baseUrl to the SaleADS login page for the desired environment.",
				baseUrl != null);

		initializeWebDriver();
		driver.get(baseUrl);
		waitForUiLoad();
		appWindowHandle = driver.getWindowHandle();

		final boolean loginOk = runStep("Login", this::stepLoginWithGoogle);
		final boolean menuOk = runDependentStep("Mi Negocio menu", loginOk, this::stepOpenMiNegocioMenu);
		final boolean modalOk = runDependentStep("Agregar Negocio modal", menuOk, this::stepValidateAgregarNegocioModal);
		final boolean adminOk = runDependentStep("Administrar Negocios view", menuOk || modalOk, this::stepOpenAdministrarNegocios);
		runDependentStep("Información General", adminOk, this::stepValidateInformacionGeneral);
		runDependentStep("Detalles de la Cuenta", adminOk, this::stepValidateDetallesCuenta);
		runDependentStep("Tus Negocios", adminOk, this::stepValidateTusNegocios);
		runDependentStep("Términos y Condiciones", adminOk, this::stepValidateTerminosYCondiciones);
		runDependentStep("Política de Privacidad", adminOk, this::stepValidatePoliticaPrivacidad);

		assertTrue("One or more validations failed. See report at: " + evidenceDir.resolve("final-report.txt"),
				failures.isEmpty());
	}

	private boolean stepLoginWithGoogle() {
		final boolean loginClicked = clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Iniciar con Google",
				"Continuar con Google");

		if (!loginClicked) {
			return false;
		}

		// Google account chooser may appear in either the same tab or a popup.
		switchToNewestWindowIfNeeded();
		clickByVisibleText(GOOGLE_ACCOUNT);

		waitForAnyCondition(Duration.ofSeconds(90), this::isSidebarVisible, () -> textVisible("Mi Negocio"),
				() -> textVisible("Negocio"));
		switchToApplicationWindow();
		waitForUiLoad();

		final boolean dashboardVisible = isSidebarVisible();
		safeScreenshot("01-dashboard-loaded");
		return dashboardVisible;
	}

	private boolean stepOpenMiNegocioMenu() {
		clickByVisibleText("Negocio");
		final boolean menuClicked = clickByVisibleText("Mi Negocio");
		waitForUiLoad();

		final boolean agregarVisible = textVisible("Agregar Negocio");
		final boolean administrarVisible = textVisible("Administrar Negocios");
		safeScreenshot("02-mi-negocio-menu-expanded");
		return menuClicked && agregarVisible && administrarVisible;
	}

	private boolean stepValidateAgregarNegocioModal() {
		final boolean addClicked = clickByVisibleText("Agregar Negocio");
		waitForUiLoad();

		final boolean titleVisible = textVisible("Crear Nuevo Negocio");
		final boolean negocioInputVisible = elementVisible(By.xpath(
				"//input[contains(@placeholder, 'Nombre del Negocio') or contains(@aria-label, 'Nombre del Negocio') or contains(@name, 'nombre')]"));
		final boolean quotaVisible = textVisible("Tienes 2 de 3 negocios");
		final boolean cancelVisible = textVisible("Cancelar");
		final boolean createVisible = textVisible("Crear Negocio");

		if (negocioInputVisible) {
			final List<WebElement> inputs = driver.findElements(By.xpath(
					"//input[contains(@placeholder, 'Nombre del Negocio') or contains(@aria-label, 'Nombre del Negocio') or contains(@name, 'nombre')]"));
			if (!inputs.isEmpty()) {
				final WebElement input = inputs.get(0);
				scrollIntoView(input);
				input.clear();
				input.sendKeys("Negocio Prueba Automatización");
				waitForUiLoad();
			}

			clickByVisibleText("Cancelar");
			waitForUiLoad();
		}

		safeScreenshot("03-crear-negocio-modal");
		return addClicked && titleVisible && negocioInputVisible && quotaVisible && cancelVisible && createVisible;
	}

	private boolean stepOpenAdministrarNegocios() {
		if (!textVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
		}

		final boolean manageClicked = clickByVisibleText("Administrar Negocios");
		waitForUiLoad();

		final boolean infoVisible = textVisible("Información General", "Informacion General");
		final boolean detailsVisible = textVisible("Detalles de la Cuenta");
		final boolean businessesVisible = textVisible("Tus Negocios");
		final boolean legalVisible = textVisible("Sección Legal", "Seccion Legal");
		safeScreenshot("04-administrar-negocios-page");
		return manageClicked && infoVisible && detailsVisible && businessesVisible && legalVisible;
	}

	private boolean stepValidateInformacionGeneral() {
		final String pageText = driver.findElement(By.tagName("body")).getText();
		final boolean hasEmail = EMAIL_PATTERN.matcher(pageText).find();
		final boolean hasPlan = textVisible("BUSINESS PLAN");
		final boolean hasChangePlan = textVisible("Cambiar Plan");

		final boolean hasNameLikeLine = pageText.lines().map(String::trim)
				.anyMatch(line -> !line.isEmpty() && !line.contains("@") && !"BUSINESS PLAN".equalsIgnoreCase(line)
						&& !"CAMBIAR PLAN".equalsIgnoreCase(line) && line.length() > 3);

		return hasNameLikeLine && hasEmail && hasPlan && hasChangePlan;
	}

	private boolean stepValidateDetallesCuenta() {
		return textVisible("Cuenta creada") && textVisible("Estado activo") && textVisible("Idioma seleccionado");
	}

	private boolean stepValidateTusNegocios() {
		final boolean headingVisible = textVisible("Tus Negocios");
		final boolean addButtonVisible = textVisible("Agregar Negocio");
		final boolean quotaVisible = textVisible("Tienes 2 de 3 negocios");
		return headingVisible && addButtonVisible && quotaVisible;
	}

	private boolean stepValidateTerminosYCondiciones() {
		return validateLegalLink("Términos y Condiciones", "Terminos y Condiciones",
				"05-terminos-condiciones", "Términos y Condiciones URL", "Términos y Condiciones", "Terminos y Condiciones",
				"condiciones");
	}

	private boolean stepValidatePoliticaPrivacidad() {
		return validateLegalLink("Política de Privacidad", "Politica de Privacidad",
				"06-politica-privacidad", "Política de Privacidad URL", "Política de Privacidad", "Politica de Privacidad",
				"privacidad");
	}

	private boolean validateLegalLink(final String primaryLabel, final String fallbackLabel,
			final String screenshotName, final String metadataKey, final String headingPrimary, final String headingFallback,
			final String contentKeyword) {
		final Set<String> oldHandles = driver.getWindowHandles();
		final boolean clicked = clickByVisibleText(primaryLabel, fallbackLabel);

		if (!clicked) {
			return false;
		}

		waitForUiLoad();
		waitForAnyCondition(Duration.ofSeconds(30), () -> hasNewHandle(oldHandles),
				() -> textVisible(headingPrimary, headingFallback));

		final String newHandle = findNewHandle(oldHandles);
		if (newHandle != null) {
			driver.switchTo().window(newHandle);
			waitForUiLoad();
		}

		final boolean headingVisible = textVisible(headingPrimary, headingFallback);
		final String text = driver.findElement(By.tagName("body")).getText().toLowerCase();
		final boolean hasLegalContent = text.length() > 200 && text.contains(contentKeyword.toLowerCase());
		final String finalUrl = driver.getCurrentUrl();
		reportMetadata.put(metadataKey, finalUrl);
		safeScreenshot(screenshotName);

		if (newHandle != null) {
			driver.close();
			driver.switchTo().window(appWindowHandle);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}

		return headingVisible && hasLegalContent;
	}

	private boolean runDependentStep(final String field, final boolean dependencyOk, final StepBody stepBody) {
		if (!dependencyOk) {
			report.put(field, "FAIL");
			failures.add(field + " blocked by a previous failure.");
			return false;
		}

		return runStep(field, stepBody);
	}

	private boolean runStep(final String field, final StepBody stepBody) {
		try {
			final boolean passed = stepBody.run();

			if (passed) {
				report.put(field, "PASS");
			} else {
				report.put(field, "FAIL");
				failures.add(field + " did not satisfy all validations.");
			}

			return passed;
		} catch (final Exception ex) {
			report.put(field, "FAIL");
			failures.add(field + " failed with exception: " + ex.getMessage());
			safeScreenshot("error-" + slug(field));
			return false;
		}
	}

	private boolean clickByVisibleText(final String... labels) {
		final long timeoutMs = System.currentTimeMillis() + Duration.ofSeconds(20).toMillis();

		while (System.currentTimeMillis() < timeoutMs) {
			for (final String label : labels) {
				final List<WebElement> candidates = findClickableByText(label);
				for (final WebElement candidate : candidates) {
					if (candidate.isDisplayed() && candidate.isEnabled()) {
						try {
							scrollIntoView(candidate);
							wait.until(ExpectedConditions.elementToBeClickable(candidate));
							candidate.click();
							waitForUiLoad();
							return true;
						} catch (final Exception clickError) {
							try {
								((JavascriptExecutor) driver).executeScript("arguments[0].click();", candidate);
								waitForUiLoad();
								return true;
							} catch (final Exception ignored) {
								// Try another candidate/label.
							}
						}
					}
				}
			}

			sleep(300);
		}

		return false;
	}

	private List<WebElement> findClickableByText(final String text) {
		final String literal = xpathLiteral(text);
		final String xpath = "(//button[contains(normalize-space(.), " + literal + ")]"
				+ " | //a[contains(normalize-space(.), " + literal + ")]"
				+ " | //*[@role='button' and contains(normalize-space(.), " + literal + ")]"
				+ " | //*[contains(normalize-space(.), " + literal + ")]/ancestor::button[1]"
				+ " | //*[contains(normalize-space(.), " + literal + ")]/ancestor::a[1])";
		final List<WebElement> found = driver.findElements(By.xpath(xpath));

		final Set<WebElement> deduped = new LinkedHashSet<>(found);
		return new ArrayList<>(deduped);
	}

	private String findNewHandle(final Set<String> oldHandles) {
		for (final String handle : driver.getWindowHandles()) {
			if (!oldHandles.contains(handle)) {
				return handle;
			}
		}

		return null;
	}

	private boolean hasNewHandle(final Set<String> oldHandles) {
		return findNewHandle(oldHandles) != null;
	}

	private boolean textVisible(final String... options) {
		for (final String option : options) {
			if (elementVisible(By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(option) + ")]"))) {
				return true;
			}
		}

		return false;
	}

	private boolean elementVisible(final By by) {
		try {
			wait.until(ExpectedConditions.visibilityOfElementLocated(by));
			return true;
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private void switchToNewestWindowIfNeeded() {
		final String newestHandle = findNewestHandle();
		if (newestHandle != null && !newestHandle.equals(driver.getWindowHandle())) {
			driver.switchTo().window(newestHandle);
			waitForUiLoad();
		}
	}

	private String findNewestHandle() {
		String newest = null;
		for (final String handle : driver.getWindowHandles()) {
			newest = handle;
		}
		return newest;
	}

	private void switchToApplicationWindow() {
		if (appWindowHandle != null && driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
		} else {
			final String fallback = findNewestHandle();
			if (fallback != null) {
				driver.switchTo().window(fallback);
				appWindowHandle = fallback;
			}
		}
	}

	private boolean isSidebarVisible() {
		final List<WebElement> asides = driver.findElements(By.xpath("//aside | //nav"));
		for (final WebElement aside : asides) {
			if (aside.isDisplayed() && aside.getText() != null && !aside.getText().trim().isEmpty()) {
				return true;
			}
		}

		return false;
	}

	private void waitForAnyCondition(final Duration timeout, final Condition... conditions) {
		final WebDriverWait shortWait = new WebDriverWait(driver, timeout);
		shortWait.until((ExpectedCondition<Boolean>) ignoredDriver -> {
			for (final Condition condition : conditions) {
				if (condition.evaluate()) {
					return true;
				}
			}
			return false;
		});
	}

	private void waitForUiLoad() {
		try {
			wait.until(webDriver -> "complete"
					.equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState")));
		} catch (final Exception ignored) {
			// Some transitions/spa routes may not expose a stable readyState moment.
		}

		sleep(500);
	}

	private void safeScreenshot(final String name) {
		try {
			final Path target = evidenceDir.resolve(slug(name) + ".png");
			final var image = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			Files.copy(image.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		} catch (final Exception ignored) {
			// Best-effort evidence collection.
		}
	}

	private void writeFinalReport() throws IOException {
		final Path reportFile = evidenceDir.resolve("final-report.txt");
		final StringBuilder sb = new StringBuilder();
		sb.append("SaleADS Mi Negocio Full Test").append(System.lineSeparator());
		sb.append("Generated at: ").append(LocalDateTime.now()).append(System.lineSeparator());
		sb.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append(System.lineSeparator())
				.append(System.lineSeparator());
		sb.append("Step status report:").append(System.lineSeparator());

		for (final Map.Entry<String, String> entry : report.entrySet()) {
			sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
		}

		if (!reportMetadata.isEmpty()) {
			sb.append(System.lineSeparator());
			sb.append("Captured metadata:").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : reportMetadata.entrySet()) {
				sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
		}

		if (!failures.isEmpty()) {
			sb.append(System.lineSeparator());
			sb.append("Failures:").append(System.lineSeparator());
			for (final String failure : failures) {
				sb.append("- ").append(failure).append(System.lineSeparator());
			}
		}

		Files.writeString(reportFile, sb.toString());
		System.out.println(sb);
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final String[] split = value.split("'");
		final StringBuilder sb = new StringBuilder("concat(");
		for (int i = 0; i < split.length; i++) {
			if (i > 0) {
				sb.append(", \"'\", ");
			}
			sb.append("'").append(split[i]).append("'");
		}
		sb.append(")");
		return sb.toString();
	}

	private String slug(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private String valueOrNull(final String... values) {
		for (final String value : values) {
			if (value != null && !value.trim().isEmpty()) {
				return value.trim();
			}
		}
		return null;
	}

	private void sleep(final long ms) {
		try {
			Thread.sleep(ms);
		} catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface StepBody {
		boolean run();
	}

	@FunctionalInterface
	private interface Condition {
		boolean evaluate();
	}

	private void initializeWebDriver() {
		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--headless=new");
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(30));
	}
}
