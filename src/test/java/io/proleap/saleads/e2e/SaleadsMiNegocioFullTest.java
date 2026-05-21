package io.proleap.saleads.e2e;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);
	private static final String DEFAULT_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, Boolean> report = new LinkedHashMap<String, Boolean>();
	private final Map<String, String> evidence = new LinkedHashMap<String, String>();

	private WebDriver driver;
	private WebDriverWait wait;
	private String baseUrl;
	private String accountEmail;
	private String evidenceDir;
	private String appWindowHandle;

	@Before
	public void setUp() {
		final boolean runE2e = getBooleanConfig("saleads.run.e2e", "SALEADS_RUN_E2E", false);
		Assume.assumeTrue(
				"Set saleads.run.e2e=true (or SALEADS_RUN_E2E=true) to execute this external E2E workflow.",
				runE2e);

		baseUrl = getConfig("saleads.baseUrl", "SALEADS_BASE_URL", "").trim();
		Assume.assumeTrue("Set saleads.baseUrl (or SALEADS_BASE_URL) to the current environment login page.",
				!baseUrl.isEmpty());

		accountEmail = getConfig("saleads.accountEmail", "SALEADS_ACCOUNT_EMAIL", DEFAULT_ACCOUNT_EMAIL);
		evidenceDir = getConfig("saleads.evidenceDir", "SALEADS_EVIDENCE_DIR", "target/saleads-evidence");

		final ChromeOptions options = new ChromeOptions();
		if (getBooleanConfig("saleads.headless", "SALEADS_HEADLESS", true)) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
		wait.pollingEvery(Duration.ofMillis(250));
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioWorkflow() {
		runStep("Login", new StepAction() {
			@Override
			public void run() throws Exception {
				stepLoginWithGoogle();
			}
		});
		runStep("Mi Negocio menu", new StepAction() {
			@Override
			public void run() throws Exception {
				stepOpenMiNegocioMenu();
			}
		});
		runStep("Agregar Negocio modal", new StepAction() {
			@Override
			public void run() throws Exception {
				stepValidateAgregarNegocioModal();
			}
		});
		runStep("Administrar Negocios view", new StepAction() {
			@Override
			public void run() throws Exception {
				stepOpenAdministrarNegocios();
			}
		});
		runStep("Información General", new StepAction() {
			@Override
			public void run() throws Exception {
				stepValidateInformacionGeneral();
			}
		});
		runStep("Detalles de la Cuenta", new StepAction() {
			@Override
			public void run() throws Exception {
				stepValidateDetallesDeLaCuenta();
			}
		});
		runStep("Tus Negocios", new StepAction() {
			@Override
			public void run() throws Exception {
				stepValidateTusNegocios();
			}
		});
		runStep("Términos y Condiciones", new StepAction() {
			@Override
			public void run() throws Exception {
				stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones", "05-terminos");
			}
		});
		runStep("Política de Privacidad", new StepAction() {
			@Override
			public void run() throws Exception {
				stepValidateLegalLink("Política de Privacidad", "Política de Privacidad", "06-privacidad");
			}
		});

		printFinalReport();
		Assert.assertTrue("One or more workflow validations failed. Review the report printed in test output.",
				allStepsPassed());
	}

	private void stepLoginWithGoogle() throws Exception {
		driver.get(baseUrl);
		waitForUiLoad();
		appWindowHandle = driver.getWindowHandle();

		if (!isSidebarVisible()) {
			final Set<String> handlesBeforeLogin = driver.getWindowHandles();
			clickFirstVisibleClickable(Duration.ofSeconds(20), clickableByText("Sign in with Google"),
					clickableByText("Iniciar sesión con Google"), clickableByText("Ingresar con Google"),
					clickableByText("Continuar con Google"), clickableByText("Google"));
			waitForUiLoad();
			handleGoogleAccountSelection(handlesBeforeLogin);
		}

		Assert.assertTrue("Main application interface did not appear after login.", isMainInterfaceVisible());
		Assert.assertTrue("Left sidebar navigation is not visible.", isSidebarVisible());
		evidence.put("Dashboard screenshot", takeScreenshot("01-dashboard-loaded"));
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		assertVisibleText("Negocio");
		clickFirstVisibleClickable(Duration.ofSeconds(20), clickableByText("Mi Negocio"), textContains("Mi Negocio"));
		waitForUiLoad();
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Administrar Negocios");
		evidence.put("Mi Negocio menu screenshot", takeScreenshot("02-mi-negocio-menu-expanded"));
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickFirstVisibleClickable(Duration.ofSeconds(20), clickableByText("Agregar Negocio"));
		waitForUiLoad();

		assertVisibleText("Crear Nuevo Negocio");
		final WebElement businessNameInput = firstVisibleElement(Duration.ofSeconds(15),
				By.xpath("//input[contains(@placeholder, 'Nombre del Negocio')]"),
				By.xpath("//input[ancestor::*[contains(normalize-space(.), " + xpathLiteral("Nombre del Negocio")
						+ ")]]"));
		Assert.assertTrue("Input field 'Nombre del Negocio' does not exist.", businessNameInput.isDisplayed());
		assertVisibleText("Tienes 2 de 3 negocios");
		assertVisibleText("Cancelar");
		assertVisibleText("Crear Negocio");

		evidence.put("Agregar Negocio modal screenshot", takeScreenshot("03-agregar-negocio-modal"));

		businessNameInput.click();
		businessNameInput.clear();
		businessNameInput.sendKeys("Negocio Prueba Automatización");
		clickFirstVisibleClickable(Duration.ofSeconds(10), clickableByText("Cancelar"));
		waitForUiLoad();
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isVisible(textContains("Administrar Negocios"), Duration.ofSeconds(4))) {
			clickFirstVisibleClickable(Duration.ofSeconds(10), clickableByText("Mi Negocio"));
			waitForUiLoad();
		}

		clickFirstVisibleClickable(Duration.ofSeconds(15), clickableByText("Administrar Negocios"));
		waitForUiLoad();

		assertVisibleText("Información General");
		assertVisibleText("Detalles de la Cuenta");
		assertVisibleText("Tus Negocios");
		assertVisibleText("Sección Legal");

		evidence.put("Administrar Negocios screenshot", takeScreenshot("04-administrar-negocios"));
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = sectionByHeading("Información General");
		Assert.assertTrue("Información General section was not found.", section != null);
		Assert.assertTrue("User name is not visible in Información General.",
				hasLikelyUserName(section.getText().replace('\n', ' ')));
		Assert.assertTrue("User email is not visible in Información General.",
				!section.findElements(By.xpath(".//*[contains(normalize-space(.), '@')]")).isEmpty());
		assertVisibleText("BUSINESS PLAN");
		assertVisibleText("Cambiar Plan");
	}

	private void stepValidateDetallesDeLaCuenta() {
		assertVisibleText("Cuenta creada");
		assertVisibleText("Estado activo");
		assertVisibleText("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		final WebElement section = sectionByHeading("Tus Negocios");
		Assert.assertTrue("Tus Negocios section was not found.", section != null);
		assertVisibleText("Agregar Negocio");
		assertVisibleText("Tienes 2 de 3 negocios");

		final List<WebElement> listCandidates = new ArrayList<WebElement>();
		listCandidates.addAll(section.findElements(By.xpath(".//li")));
		listCandidates.addAll(section.findElements(By.xpath(".//table//tr")));
		listCandidates.addAll(section.findElements(By.xpath(".//div[contains(@class, 'business')]")));
		final boolean hasList = !listCandidates.isEmpty() || section.getText().trim().length() > 60;
		Assert.assertTrue("Business list is not visible in Tus Negocios.", hasList);
	}

	private void stepValidateLegalLink(final String linkText, final String expectedHeading, final String screenshotName)
			throws Exception {
		final String originalWindow = driver.getWindowHandle();
		final String originUrl = driver.getCurrentUrl();
		final Set<String> beforeHandles = driver.getWindowHandles();

		clickFirstVisibleClickable(Duration.ofSeconds(20), clickableByText(linkText), linkByText(linkText));
		waitForUiLoad();

		boolean openedNewTab = false;
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10))
					.until(new ExpectedCondition<Boolean>() {
						@Override
						public Boolean apply(final WebDriver input) {
							return input.getWindowHandles().size() > beforeHandles.size();
						}
					});
			openedNewTab = true;
		} catch (final TimeoutException ignored) {
			openedNewTab = false;
		}

		if (openedNewTab) {
			final Set<String> currentHandles = driver.getWindowHandles();
			for (final String handle : currentHandles) {
				if (!beforeHandles.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		}

		waitForUiLoad();
		assertVisibleText(expectedHeading);
		Assert.assertTrue("Legal content text is not visible on " + expectedHeading + ".",
				driver.findElement(By.tagName("body")).getText().trim().length() > 120);

		evidence.put(expectedHeading + " screenshot", takeScreenshot(screenshotName));
		evidence.put(expectedHeading + " URL", driver.getCurrentUrl());

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originalWindow);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
			if (!driver.getCurrentUrl().equals(originUrl) && appWindowHandle != null) {
				driver.switchTo().window(appWindowHandle);
			}
		}
	}

	private void handleGoogleAccountSelection(final Set<String> handlesBeforeLogin) {
		if (driver.getWindowHandles().size() > handlesBeforeLogin.size()) {
			for (final String handle : driver.getWindowHandles()) {
				if (!handlesBeforeLogin.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		}

		if (isVisible(textContains(accountEmail), Duration.ofSeconds(15))) {
			clickFirstVisibleClickable(Duration.ofSeconds(15), clickableByText(accountEmail), textContains(accountEmail));
			waitForUiLoad();
		}

		if (appWindowHandle != null && driver.getWindowHandles().contains(appWindowHandle)) {
			driver.switchTo().window(appWindowHandle);
			waitForUiLoad();
		}
	}

	private void runStep(final String fieldName, final StepAction action) {
		try {
			action.run();
			report.put(fieldName, Boolean.TRUE);
		} catch (final Throwable t) {
			report.put(fieldName, Boolean.FALSE);
			evidence.put(fieldName + " error", t.getMessage());
		}
	}

	private void printFinalReport() {
		System.out.println("===== SaleADS Mi Negocio Workflow Report =====");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			System.out.println(entry.getKey() + ": " + (entry.getValue().booleanValue() ? "PASS" : "FAIL"));
		}
		System.out.println("--------------- Evidence ---------------");
		for (final Map.Entry<String, String> entry : evidence.entrySet()) {
			System.out.println(entry.getKey() + ": " + entry.getValue());
		}
		System.out.println("========================================");
	}

	private boolean allStepsPassed() {
		for (final Boolean status : report.values()) {
			if (!status.booleanValue()) {
				return false;
			}
		}
		return true;
	}

	private void clickFirstVisibleClickable(final Duration timeout, final By... locators) {
		final WebElement element = firstVisibleElement(timeout, locators);
		scrollIntoView(element);
		wait.until(new ExpectedCondition<Boolean>() {
			@Override
			public Boolean apply(final WebDriver d) {
				return element.isDisplayed() && element.isEnabled();
			}
		});
		element.click();
		waitForUiLoad();
	}

	private WebElement firstVisibleElement(final Duration timeout, final By... locators) {
		final long deadline = System.currentTimeMillis() + timeout.toMillis();

		while (System.currentTimeMillis() < deadline) {
			for (final By locator : locators) {
				final List<WebElement> elements = driver.findElements(locator);
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return element;
					}
				}
			}
			sleep(200);
		}

		throw new AssertionError("Unable to find a visible element with the provided locators.");
	}

	private void waitForUiLoad() {
		wait.until(new ExpectedCondition<Boolean>() {
			@Override
			public Boolean apply(final WebDriver d) {
				final Object readyState = ((JavascriptExecutor) d).executeScript("return document.readyState");
				return "complete".equals(readyState);
			}
		});
		sleep(400);
	}

	private void assertVisibleText(final String text) {
		Assert.assertTrue("Text is not visible: " + text, isVisible(textContains(text), Duration.ofSeconds(20)));
	}

	private boolean isVisible(final By locator, final Duration timeout) {
		try {
			final WebElement element = new WebDriverWait(driver, timeout).until(new ExpectedCondition<WebElement>() {
				@Override
				public WebElement apply(final WebDriver d) {
					final List<WebElement> elements = d.findElements(locator);
					for (final WebElement candidate : elements) {
						if (candidate.isDisplayed()) {
							return candidate;
						}
					}
					return null;
				}
			});
			return element != null && element.isDisplayed();
		} catch (final TimeoutException ex) {
			return false;
		}
	}

	private boolean isMainInterfaceVisible() {
		return isVisible(By.tagName("main"), Duration.ofSeconds(20))
				|| isVisible(By.xpath("//div[contains(@class, 'dashboard')]"), Duration.ofSeconds(20))
				|| isSidebarVisible();
	}

	private boolean isSidebarVisible() {
		return isVisible(By.xpath("//aside"), Duration.ofSeconds(8))
				|| isVisible(By.xpath("//nav[contains(@class, 'sidebar')]"), Duration.ofSeconds(8))
				|| isVisible(textContains("Negocio"), Duration.ofSeconds(8));
	}

	private WebElement sectionByHeading(final String heading) {
		try {
			return firstVisibleElement(Duration.ofSeconds(15), By.xpath("//section[.//*[contains(normalize-space(.), "
					+ xpathLiteral(heading) + ")]]"),
					By.xpath("//div[.//*[contains(normalize-space(.), " + xpathLiteral(heading) + ")]]"));
		} catch (final AssertionError ex) {
			return null;
		}
	}

	private boolean hasLikelyUserName(final String text) {
		if (text == null) {
			return false;
		}

		final String cleaned = text.replace("Información General", "").replace("BUSINESS PLAN", "")
				.replace("Cambiar Plan", "");
		final String[] tokens = cleaned.split("\\s+");
		int validNameTokens = 0;
		for (final String token : tokens) {
			if (token.contains("@") || token.length() < 2) {
				continue;
			}
			if (token.matches("[A-Za-zÁÉÍÓÚáéíóúÑñ-]{2,}")) {
				validNameTokens++;
			}
		}
		return validNameTokens >= 2;
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
	}

	private String takeScreenshot(final String checkpointName) throws IOException {
		final String safeName = checkpointName.replaceAll("[^A-Za-z0-9-_]", "_");
		final Path dir = Paths.get(evidenceDir);
		Files.createDirectories(dir);

		final String filename = System.currentTimeMillis() + "-" + safeName + ".png";
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path destination = dir.resolve(filename);
		Files.copy(screenshot.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
		return destination.toAbsolutePath().toString();
	}

	private String getConfig(final String propertyName, final String envName, final String defaultValue) {
		final String property = System.getProperty(propertyName);
		if (property != null && !property.trim().isEmpty()) {
			return property.trim();
		}
		final String env = System.getenv(envName);
		if (env != null && !env.trim().isEmpty()) {
			return env.trim();
		}
		return defaultValue;
	}

	private boolean getBooleanConfig(final String propertyName, final String envName, final boolean defaultValue) {
		final String rawValue = getConfig(propertyName, envName, String.valueOf(defaultValue));
		return "true".equalsIgnoreCase(rawValue) || "1".equals(rawValue);
	}

	private By textContains(final String text) {
		return By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(text) + ")]");
	}

	private By clickableByText(final String text) {
		return By.xpath("//*[self::button or self::a or @role='button' or self::span][contains(normalize-space(.), "
				+ xpathLiteral(text) + ")]");
	}

	private By linkByText(final String text) {
		return By.xpath("//a[contains(normalize-space(.), " + xpathLiteral(text) + ")]");
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		final String[] parts = value.split("'");
		final StringBuilder concat = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				concat.append(", \"'\", ");
			}
			concat.append("'").append(parts[i]).append("'");
		}
		concat.append(")");
		return concat.toString();
	}

	private void sleep(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Sleep interrupted", ex);
		}
	}

	private interface StepAction {
		void run() throws Exception;
	}
}
