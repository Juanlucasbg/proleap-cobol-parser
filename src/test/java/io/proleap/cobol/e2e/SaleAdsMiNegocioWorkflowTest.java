package io.proleap.cobol.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.After;
import org.junit.Assert;
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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleAdsMiNegocioWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(30);
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

	private final Map<String, String> finalReport = new LinkedHashMap<>();
	private final List<String> failures = new ArrayList<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String applicationWindowHandle;

	@Before
	public void setUp() throws IOException {
		WebDriverManager.chromedriver().setup();

		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean.parseBoolean(System.getProperty("saleads.headless", "true"));
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, WAIT_TIMEOUT);
		evidenceDir = Path.of("target", "saleads-evidence");
		Files.createDirectories(evidenceDir);

		initializeReport();

		final String loginUrl = getRuntimeValue("saleads.loginUrl", "SALEADS_LOGIN_URL");
		if (loginUrl != null && !loginUrl.isBlank()) {
			driver.get(loginUrl);
			waitForUiLoad();
		}
	}

	@After
	public void tearDown() {
		printFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		executeStep("Login", this::stepLoginWithGoogle);
		executeStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		executeStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		executeStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		executeStep("Información General", this::stepValidateInformacionGeneral);
		executeStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		executeStep("Tus Negocios", this::stepValidateTusNegocios);
		executeStep("Términos y Condiciones", this::stepValidateTerminos);
		executeStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

		final boolean anyFailed = finalReport.values().stream().anyMatch(value -> value.startsWith("FAIL"));
		Assert.assertFalse("One or more validations failed.\n" + buildFinalReport(), anyFailed);
	}

	private void stepLoginWithGoogle() throws IOException {
		if ("about:blank".equals(driver.getCurrentUrl())) {
			throw new IllegalStateException("Browser is on about:blank. Set -Dsaleads.loginUrl or SALEADS_LOGIN_URL.");
		}

		clickByAnyVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google",
				"Login with Google");
		waitForUiLoad();
		selectGoogleAccountIfVisible(GOOGLE_ACCOUNT_EMAIL);
		waitForMainApplication();

		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException {
		ensureMiNegocioExpanded();

		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException {
		clickByAnyVisibleText("Agregar Negocio");
		waitForUiLoad();

		assertVisibleText("Crear Nuevo Negocio");
		assertInputForLabelOrPlaceholder("Nombre del Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal");

		typeIntoInput("Nombre del Negocio", "Negocio Prueba Automatización");
		clickByAnyVisibleText("Cancelar");
		waitForUiLoad();
	}

	private void stepOpenAdministrarNegocios() throws IOException {
		ensureMiNegocioExpanded();
		clickByAnyVisibleText("Administrar Negocios");
		waitForUiLoad();

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");
		captureScreenshot("04-administrar-negocios-page-full");
	}

	private void stepValidateInformacionGeneral() {
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");

		if (!isAnyElementTextMatching(EMAIL_PATTERN)) {
			throw new AssertionError("User email is not visible.");
		}

		if (!isVisibleByAnyText("Nombre", "Nombre de usuario", "Usuario", "Perfil")) {
			throw new AssertionError("User name label/value is not visible.");
		}
	}

	private void stepValidateDetallesCuenta() {
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertVisibleText("Tus Negocios");
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");
	}

	private void stepValidateTerminos() throws IOException {
		final String termsUrl = openLegalLinkAndValidate("Términos y Condiciones", "05-terminos-condiciones");
		System.out.println("Términos y Condiciones URL: " + termsUrl);
	}

	private void stepValidatePoliticaPrivacidad() throws IOException {
		final String policyUrl = openLegalLinkAndValidate("Política de Privacidad", "06-politica-privacidad");
		System.out.println("Política de Privacidad URL: " + policyUrl);
	}

	private String openLegalLinkAndValidate(final String linkText, final String screenshotName) throws IOException {
		ensureOnAdministrarNegociosView();

		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		final String currentUrl = driver.getCurrentUrl();
		clickByAnyVisibleText(linkText);
		waitForUiLoad();

		final String destinationHandle = waitForNewTabOrSameTab(handlesBeforeClick, currentUrl);
		driver.switchTo().window(destinationHandle);
		waitForUiLoad();

		assertVisibleText(linkText);
		assertAnyLegalTextVisible();
		captureScreenshot(screenshotName);

		final String finalUrl = driver.getCurrentUrl();

		if (!destinationHandle.equals(applicationWindowHandle)) {
			driver.close();
			driver.switchTo().window(applicationWindowHandle);
		} else {
			driver.navigate().back();
			waitForUiLoad();
			ensureOnAdministrarNegociosView();
		}

		return finalUrl;
	}

	private void ensureOnAdministrarNegociosView() {
		if (!isVisibleByText("Información General")) {
			ensureMiNegocioExpanded();
			clickByAnyVisibleText("Administrar Negocios");
			waitForUiLoad();
		}
		assertVisibleText("Información General");
		assertVisibleText("Sección Legal");
	}

	private void ensureMiNegocioExpanded() {
		if (!isVisibleByText("Agregar Negocio") || !isVisibleByText("Administrar Negocios")) {
			if (isVisibleByText("Negocio")) {
				clickByAnyVisibleText("Negocio");
				waitForUiLoad();
			}
			clickByAnyVisibleText("Mi Negocio");
			waitForUiLoad();
		}
	}

	private void selectGoogleAccountIfVisible(final String email) {
		final long deadline = System.currentTimeMillis() + WAIT_TIMEOUT.toMillis();
		while (System.currentTimeMillis() < deadline) {
			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				final String url = driver.getCurrentUrl();
				if (url != null && url.contains("accounts.google.com")) {
					clickIfVisibleByAnySelector(
							By.xpath("//*[normalize-space(.)=" + xpathLiteral(email) + "]"),
							By.xpath("//div[@data-email=" + xpathLiteral(email) + "]"));
					waitForUiLoad();
				}
			}

			driver.switchTo().window(driver.getWindowHandles().iterator().next());

			if (isVisibleByAnyText("Negocio", "Mi Negocio", "Dashboard", "Panel")) {
				return;
			}

			sleep(500);
		}
	}

	private void waitForMainApplication() {
		waitForUiLoad();
		final By sidebarByText = By.xpath("//aside//*[contains(normalize-space(.), 'Negocio')]"
				+ "| //nav//*[contains(normalize-space(.), 'Negocio')]"
				+ "| //*[contains(@class,'sidebar')]//*[contains(normalize-space(.), 'Negocio')]");
		wait.until(ExpectedConditions.visibilityOfElementLocated(sidebarByText));
		applicationWindowHandle = driver.getWindowHandle();
	}

	private void clickByAnyVisibleText(final String... texts) {
		for (final String text : texts) {
			if (clickIfVisibleByText(text)) {
				waitForUiLoad();
				return;
			}
		}
		throw new AssertionError("Could not find clickable element by text: " + String.join(", ", texts));
	}

	private boolean clickIfVisibleByText(final String text) {
		final String literal = xpathLiteral(text);
		final List<By> selectors = List.of(
				By.xpath("//button[normalize-space(.)=" + literal + "]"),
				By.xpath("//a[normalize-space(.)=" + literal + "]"),
				By.xpath("//*[@role='button' and normalize-space(.)=" + literal + "]"),
				By.xpath("//*[contains(@class,'btn') and normalize-space(.)=" + literal + "]"),
				By.xpath("//*[self::button or self::a or @role='button' or self::span or self::div or self::li]"
						+ "[contains(normalize-space(.), " + literal + ")]"));

		return clickIfVisibleByAnySelector(selectors.toArray(new By[0]));
	}

	private boolean clickIfVisibleByAnySelector(final By... selectors) {
		for (final By selector : selectors) {
			try {
				final WebElement element = wait.until(ExpectedConditions.visibilityOfElementLocated(selector));
				((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
				try {
					wait.until(ExpectedConditions.elementToBeClickable(element)).click();
				} catch (final Exception clickException) {
					((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
				}
				return true;
			} catch (final TimeoutException ignored) {
				// Try next selector.
			}
		}
		return false;
	}

	private void assertVisibleText(final String text) {
		final String literal = xpathLiteral(text);
		final By by = By.xpath("//*[normalize-space(.)=" + literal + "]"
				+ " | //*[contains(normalize-space(.), " + literal + ")]");
		wait.until(ExpectedConditions.visibilityOfElementLocated(by));
	}

	private boolean isVisibleByText(final String text) {
		try {
			final String literal = xpathLiteral(text);
			final By by = By.xpath("//*[normalize-space(.)=" + literal + "]"
					+ " | //*[contains(normalize-space(.), " + literal + ")]");
			wait.withTimeout(Duration.ofSeconds(2)).until(ExpectedConditions.visibilityOfElementLocated(by));
			wait.withTimeout(WAIT_TIMEOUT);
			return true;
		} catch (final Exception e) {
			wait.withTimeout(WAIT_TIMEOUT);
			return false;
		}
	}

	private boolean isVisibleByAnyText(final String... texts) {
		for (final String text : texts) {
			if (isVisibleByText(text)) {
				return true;
			}
		}
		return false;
	}

	private void assertInputForLabelOrPlaceholder(final String labelOrPlaceholder) {
		final String literal = xpathLiteral(labelOrPlaceholder);
		final By inputBy = By.xpath("//label[contains(normalize-space(.), " + literal + ")]"
				+ "/following::input[1]"
				+ " | //input[@placeholder=" + literal + "]"
				+ " | //input[contains(@aria-label, " + literal + ")]");
		wait.until(ExpectedConditions.visibilityOfElementLocated(inputBy));
	}

	private void typeIntoInput(final String labelOrPlaceholder, final String value) {
		final String literal = xpathLiteral(labelOrPlaceholder);
		final By inputBy = By.xpath("//label[contains(normalize-space(.), " + literal + ")]"
				+ "/following::input[1]"
				+ " | //input[@placeholder=" + literal + "]"
				+ " | //input[contains(@aria-label, " + literal + ")]");
		final WebElement input = wait.until(ExpectedConditions.visibilityOfElementLocated(inputBy));
		input.clear();
		input.sendKeys(value);
	}

	private boolean isAnyElementTextMatching(final Pattern pattern) {
		return driver.findElements(By.xpath("//*[normalize-space(text())!='']")).stream()
				.map(WebElement::getText)
				.map(String::trim)
				.anyMatch(text -> pattern.matcher(text).matches());
	}

	private void assertAnyLegalTextVisible() {
		final List<WebElement> paragraphs = driver.findElements(By.xpath("//p[normalize-space(text())!='']"));
		if (paragraphs.isEmpty()) {
			final String pageText = driver.findElement(By.tagName("body")).getText();
			if (pageText == null || pageText.trim().length() < 80) {
				throw new AssertionError("Legal content text is not visible.");
			}
		}
	}

	private String waitForNewTabOrSameTab(final Set<String> handlesBefore, final String previousUrl) {
		wait.until(d -> d.getWindowHandles().size() > handlesBefore.size() || !previousUrl.equals(d.getCurrentUrl()));

		final Set<String> handlesAfter = driver.getWindowHandles();
		for (final String handle : handlesAfter) {
			if (!handlesBefore.contains(handle)) {
				return handle;
			}
		}
		return driver.getWindowHandle();
	}

	private void waitForUiLoad() {
		wait.until(driverInstance -> {
			final Object ready = ((JavascriptExecutor) driverInstance).executeScript("return document.readyState");
			return "complete".equals(ready);
		});
		sleep(300);
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		final String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
		final Path screenshotPath = evidenceDir.resolve(timestamp + "-" + checkpointName + ".png");
		final Path tempFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
		Files.copy(tempFile, screenshotPath, StandardCopyOption.REPLACE_EXISTING);
	}

	private void executeStep(final String stepName, final StepExecutable stepExecutable) {
		try {
			stepExecutable.execute();
			finalReport.put(stepName, "PASS");
		} catch (final Exception e) {
			final String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			final String failureMessage = "FAIL - " + reason;
			finalReport.put(stepName, failureMessage);
			failures.add(stepName + ": " + reason);
		}
	}

	private void initializeReport() {
		finalReport.put("Login", "NOT_RUN");
		finalReport.put("Mi Negocio menu", "NOT_RUN");
		finalReport.put("Agregar Negocio modal", "NOT_RUN");
		finalReport.put("Administrar Negocios view", "NOT_RUN");
		finalReport.put("Información General", "NOT_RUN");
		finalReport.put("Detalles de la Cuenta", "NOT_RUN");
		finalReport.put("Tus Negocios", "NOT_RUN");
		finalReport.put("Términos y Condiciones", "NOT_RUN");
		finalReport.put("Política de Privacidad", "NOT_RUN");
	}

	private void printFinalReport() {
		System.out.println("==== SaleADS Mi Negocio Final Report ====");
		System.out.println(buildFinalReport());
		if (!failures.isEmpty()) {
			System.out.println("Failures:");
			failures.forEach(item -> System.out.println(" - " + item));
		}
	}

	private String buildFinalReport() {
		return finalReport.entrySet().stream()
				.map(entry -> entry.getKey() + ": " + entry.getValue())
				.collect(Collectors.joining("\n"));
	}

	private String getRuntimeValue(final String propertyName, final String envName) {
		final String fromProperty = System.getProperty(propertyName);
		if (fromProperty != null && !fromProperty.isBlank()) {
			return fromProperty;
		}
		final String fromEnv = System.getenv(envName);
		return (fromEnv == null || fromEnv.isBlank()) ? null : fromEnv;
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Thread interrupted while waiting.", e);
		}
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final StringBuilder builder = new StringBuilder("concat(");
		for (int i = 0; i < value.length(); i++) {
			final char c = value.charAt(i);
			if (c == '\'') {
				builder.append("\"'\"");
			} else if (c == '"') {
				builder.append("'\"'");
			} else {
				builder.append("'").append(c).append("'");
			}
			if (i < value.length() - 1) {
				builder.append(",");
			}
		}
		builder.append(")");
		return builder.toString();
	}

	@FunctionalInterface
	private interface StepExecutable {
		void execute() throws Exception;
	}
}
