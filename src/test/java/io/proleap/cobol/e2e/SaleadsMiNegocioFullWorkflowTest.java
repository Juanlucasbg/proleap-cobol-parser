package io.proleap.cobol.e2e;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> failureDetails = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Before
	public void setUp() throws IOException {
		final boolean runE2e = Boolean.parseBoolean(readConfig("SALEADS_RUN_E2E", "false"));
		Assume.assumeTrue(
				"Set SALEADS_RUN_E2E=true to run this E2E workflow test against a live SaleADS environment.", runE2e);

		final String loginUrl = readConfig("SALEADS_LOGIN_URL", "").trim();
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL to the current environment login page (dev/staging/prod).", !loginUrl.isEmpty());

		WebDriverManager.chromedriver().setup();

		final ChromeOptions options = new ChromeOptions();
		final boolean headless = Boolean.parseBoolean(readConfig("SALEADS_HEADLESS", "true"));
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);

		evidenceDir = Paths.get("target", "saleads-evidence",
				DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()));
		Files.createDirectories(evidenceDir);

		driver.get(loginUrl);
		waitForUiToLoad();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		runStep("Login", this::stepLoginWithGoogle);
		runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runStep("Información General", this::stepValidateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runStep("Tus Negocios", this::stepValidateTusNegocios);
		runStep("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		runStep("Política de Privacidad", this::stepValidatePoliticaPrivacidad);

		final String finalReport = buildFinalReport();
		System.out.println(finalReport);
		Assert.assertTrue("Mi Negocio workflow validations failed.\n" + finalReport, report.values().stream()
				.allMatch(Boolean::booleanValue));
	}

	private void stepLoginWithGoogle() throws Exception {
		final WebElement loginButton = waitForAnyVisibleText(
				Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Login with Google"),
				DEFAULT_TIMEOUT);
		clickAndWait(loginButton);
		selectGoogleAccountIfPrompted();

		waitForAnyVisibleText(Arrays.asList("Negocio", "Mi Negocio"), Duration.ofSeconds(90));
		waitForAnyVisibleElement(Arrays.asList(By.tagName("aside"), By.xpath("//nav")), Duration.ofSeconds(45));
		takeScreenshot("01-dashboard-loaded.png");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		waitForAnyVisibleText(Arrays.asList("Negocio"), DEFAULT_TIMEOUT);
		final WebElement miNegocio = waitForAnyVisibleText(Arrays.asList("Mi Negocio"), DEFAULT_TIMEOUT);
		clickAndWait(miNegocio);

		waitForAnyVisibleText(Arrays.asList("Agregar Negocio"), DEFAULT_TIMEOUT);
		waitForAnyVisibleText(Arrays.asList("Administrar Negocios"), DEFAULT_TIMEOUT);
		takeScreenshot("02-mi-negocio-menu-expanded.png");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		final WebElement agregarNegocio = waitForAnyVisibleText(Arrays.asList("Agregar Negocio"), DEFAULT_TIMEOUT);
		clickAndWait(agregarNegocio);

		final WebElement modalTitle = waitForAnyVisibleText(Arrays.asList("Crear Nuevo Negocio"), DEFAULT_TIMEOUT);
		final WebElement modal = findClosestModalContainer(modalTitle);
		Assert.assertTrue("'Nombre del Negocio' must be visible",
				containerContainsText(modal, "Nombre del Negocio"));
		Assert.assertTrue("'Tienes 2 de 3 negocios' must be visible",
				containerContainsText(modal, "Tienes 2 de 3 negocios"));
		Assert.assertNotNull("'Cancelar' button must be present", findVisibleInContainerByText(modal, "Cancelar"));
		Assert.assertNotNull("'Crear Negocio' button must be present", findVisibleInContainerByText(modal, "Crear Negocio"));

		final List<WebElement> modalInputs = modal.findElements(By.xpath(".//input[not(@type='hidden')]"));
		Assert.assertFalse("Input field 'Nombre del Negocio' must exist", modalInputs.isEmpty());

		modalInputs.get(0).click();
		modalInputs.get(0).clear();
		modalInputs.get(0).sendKeys("Negocio Prueba Automatización");
		waitForUiToLoad();
		takeScreenshot("03-agregar-negocio-modal.png");
		clickAndWait(findVisibleInContainerByText(modal, "Cancelar"));
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		ensureMiNegocioMenuExpanded();
		final WebElement administrarNegocios = waitForAnyVisibleText(Arrays.asList("Administrar Negocios"), DEFAULT_TIMEOUT);
		clickAndWait(administrarNegocios);

		waitForAnyVisibleText(Arrays.asList("Información General"), DEFAULT_TIMEOUT);
		waitForAnyVisibleText(Arrays.asList("Detalles de la Cuenta"), DEFAULT_TIMEOUT);
		waitForAnyVisibleText(Arrays.asList("Tus Negocios"), DEFAULT_TIMEOUT);
		waitForAnyVisibleText(Arrays.asList("Sección Legal"), DEFAULT_TIMEOUT);

		takeFullPageScreenshot("04-administrar-negocios-full.png");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement infoHeading = waitForAnyVisibleText(Arrays.asList("Información General"), DEFAULT_TIMEOUT);
		final WebElement infoSection = findSectionContainer(infoHeading);
		final List<String> sectionTexts = extractVisibleTexts(infoSection);
		final boolean hasUserEmail = sectionTexts.stream().anyMatch(text -> text.contains("@"));
		final boolean hasUserName = sectionTexts.stream().anyMatch(this::looksLikeUserName);
		Assert.assertTrue("User email must be visible", hasUserEmail);
		Assert.assertTrue("User name must be visible", hasUserName);
		waitForAnyVisibleText(Arrays.asList("BUSINESS PLAN"), DEFAULT_TIMEOUT);
		waitForAnyVisibleText(Arrays.asList("Cambiar Plan"), DEFAULT_TIMEOUT);
	}

	private void stepValidateDetallesCuenta() {
		waitForAnyVisibleText(Arrays.asList("Cuenta creada"), DEFAULT_TIMEOUT);
		waitForAnyVisibleText(Arrays.asList("Estado activo"), DEFAULT_TIMEOUT);
		waitForAnyVisibleText(Arrays.asList("Idioma seleccionado"), DEFAULT_TIMEOUT);
	}

	private void stepValidateTusNegocios() {
		final WebElement tusNegociosHeading = waitForAnyVisibleText(Arrays.asList("Tus Negocios"), DEFAULT_TIMEOUT);
		final WebElement tusNegociosSection = findSectionContainer(tusNegociosHeading);
		Assert.assertTrue("Business list must be visible", countVisibleElementsInContainer(tusNegociosSection,
				By.xpath(".//ul|.//table|.//div[contains(@class,'business') or contains(@class,'negocio')]")) > 0);
		waitForAnyVisibleText(Arrays.asList("Agregar Negocio"), DEFAULT_TIMEOUT);
		waitForAnyVisibleText(Arrays.asList("Tienes 2 de 3 negocios"), DEFAULT_TIMEOUT);
	}

	private void stepValidateTerminosYCondiciones() throws Exception {
		final String termsUrl = openLegalDocumentAndReturn("Términos y Condiciones",
				Arrays.asList("Términos y Condiciones"), "05-terminos-y-condiciones.png");
		legalUrls.put("Términos y Condiciones", termsUrl);
	}

	private void stepValidatePoliticaPrivacidad() throws Exception {
		final String privacyUrl = openLegalDocumentAndReturn("Política de Privacidad",
				Arrays.asList("Política de Privacidad"), "06-politica-de-privacidad.png");
		legalUrls.put("Política de Privacidad", privacyUrl);
	}

	private String openLegalDocumentAndReturn(final String linkText, final List<String> headingTexts, final String screenshot)
			throws Exception {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> previousHandles = new LinkedHashSet<>(driver.getWindowHandles());
		final String previousUrl = driver.getCurrentUrl();

		final WebElement link = waitForAnyVisibleText(Arrays.asList(linkText), DEFAULT_TIMEOUT);
		clickAndWait(link);

		String targetHandle = originalHandle;
		boolean openedNewTab = false;
		final long timeoutAt = System.currentTimeMillis() + DEFAULT_TIMEOUT.toMillis();

		while (System.currentTimeMillis() < timeoutAt) {
			final Set<String> currentHandles = new LinkedHashSet<>(driver.getWindowHandles());
			if (currentHandles.size() > previousHandles.size()) {
				currentHandles.removeAll(previousHandles);
				targetHandle = currentHandles.iterator().next();
				openedNewTab = true;
				break;
			}
			if (!driver.getCurrentUrl().equals(previousUrl)) {
				break;
			}
			Thread.sleep(250);
		}

		driver.switchTo().window(targetHandle);
		waitForUiToLoad();
		waitForAnyVisibleText(headingTexts, Duration.ofSeconds(45));
		assertLegalContentVisible();
		takeScreenshot(screenshot);
		final String finalUrl = driver.getCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(originalHandle);
		} else {
			driver.navigate().back();
		}
		waitForUiToLoad();
		waitForAnyVisibleText(Arrays.asList("Sección Legal", "Tus Negocios"), DEFAULT_TIMEOUT);
		return finalUrl;
	}

	private void assertLegalContentVisible() {
		final List<WebElement> contentBlocks = new ArrayList<>();
		contentBlocks.addAll(driver.findElements(By.xpath("//article//*[string-length(normalize-space()) > 40]")));
		contentBlocks.addAll(driver.findElements(By.xpath("//main//*[string-length(normalize-space()) > 40]")));
		contentBlocks.addAll(driver.findElements(By.xpath("//p[string-length(normalize-space()) > 40]")));

		Assert.assertTrue("Legal content text must be visible",
				contentBlocks.stream().anyMatch(WebElement::isDisplayed));
	}

	private void ensureMiNegocioMenuExpanded() throws Exception {
		if (!isAnyTextVisible(Arrays.asList("Administrar Negocios"))) {
			final WebElement miNegocio = waitForAnyVisibleText(Arrays.asList("Mi Negocio"), DEFAULT_TIMEOUT);
			clickAndWait(miNegocio);
		}
		waitForAnyVisibleText(Arrays.asList("Administrar Negocios"), DEFAULT_TIMEOUT);
	}

	private void selectGoogleAccountIfPrompted() throws Exception {
		final String startingHandle = driver.getWindowHandle();
		final long timeoutAt = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
		while (System.currentTimeMillis() < timeoutAt) {
			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				final WebElement accountTile = findVisibleElementByTexts(
						Arrays.asList(GOOGLE_ACCOUNT_EMAIL), Duration.ofSeconds(2));
				if (accountTile != null) {
					clickAndWait(accountTile);
					return;
				}
			}
			Thread.sleep(250);
		}
		driver.switchTo().window(startingHandle);
	}

	private void runStep(final String label, final StepAction action) {
		try {
			action.run();
			report.put(label, true);
		} catch (final Throwable throwable) {
			report.put(label, false);
			failureDetails.put(label, throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
			try {
				takeScreenshot("failed-" + sanitizeFileName(label) + ".png");
			} catch (final Exception ignored) {
				// Best-effort evidence capture for failed steps.
			}
		}
	}

	private String buildFinalReport() {
		final StringBuilder sb = new StringBuilder();
		sb.append("\n=== SaleADS Mi Negocio Workflow Report ===\n");
		for (final Map.Entry<String, Boolean> entry : report.entrySet()) {
			sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue() ? "PASS" : "FAIL");
			if (!entry.getValue() && failureDetails.containsKey(entry.getKey())) {
				sb.append(" -> ").append(failureDetails.get(entry.getKey()));
			}
			sb.append('\n');
		}
		for (final Map.Entry<String, String> urlEntry : legalUrls.entrySet()) {
			sb.append("  ").append(urlEntry.getKey()).append(" URL: ").append(urlEntry.getValue()).append('\n');
		}
		sb.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append('\n');
		return sb.toString();
	}

	private long countVisibleElementsInContainer(final WebElement container, final By by) {
		return container.findElements(by).stream().filter(WebElement::isDisplayed).count();
	}

	private boolean isAnyTextVisible(final List<String> texts) {
		for (final String text : texts) {
			final List<WebElement> candidates = driver.findElements(By.xpath("//*[contains(normalize-space(.), "
					+ xpathLiteral(text) + ")]"));
			for (final WebElement candidate : candidates) {
				if (candidate.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private WebElement waitForAnyVisibleText(final List<String> texts, final Duration timeout) {
		final WebElement element = findVisibleElementByTexts(texts, timeout);
		if (element == null) {
			throw new AssertionError("Could not find visible element with text candidates: " + texts);
		}
		return element;
	}

	private WebElement findVisibleElementByTexts(final List<String> texts, final Duration timeout) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		return localWait.until(webDriver -> {
			for (final String text : texts) {
				final List<WebElement> candidates = webDriver.findElements(By.xpath("//*[contains(normalize-space(.), "
						+ xpathLiteral(text) + ")]"));
				for (final WebElement candidate : candidates) {
					if (candidate.isDisplayed()) {
						return candidate;
					}
				}
			}
			return null;
		});
	}

	private WebElement waitForAnyVisibleElement(final List<By> locators, final Duration timeout) {
		final WebDriverWait localWait = new WebDriverWait(driver, timeout);
		return localWait.until(webDriver -> {
			for (final By locator : locators) {
				final List<WebElement> elements = webDriver.findElements(locator);
				for (final WebElement element : elements) {
					if (element.isDisplayed()) {
						return element;
					}
				}
			}
			return null;
		});
	}

	private void clickAndWait(final WebElement element) throws Exception {
		try {
			wait.until(ExpectedConditions.elementToBeClickable(element)).click();
		} catch (final Exception clickError) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		wait.until(webDriver -> {
			final String readyState = String
					.valueOf(((JavascriptExecutor) webDriver).executeScript("return document.readyState"));
			return "complete".equals(readyState) || "interactive".equals(readyState);
		});
	}

	private WebElement findClosestModalContainer(final WebElement anchorElement) {
		final List<By> locators = Arrays.asList(
				By.xpath("./ancestor::*[@role='dialog'][1]"),
				By.xpath("./ancestor::*[contains(@class,'modal')][1]"),
				By.xpath("./ancestor::*[contains(@class,'dialog')][1]"));
		for (final By locator : locators) {
			final List<WebElement> candidates = anchorElement.findElements(locator);
			if (!candidates.isEmpty()) {
				return candidates.get(0);
			}
		}
		return anchorElement;
	}

	private WebElement findSectionContainer(final WebElement anchorElement) {
		final List<By> locators = Arrays.asList(
				By.xpath("./ancestor::section[1]"),
				By.xpath("./ancestor::article[1]"),
				By.xpath("./ancestor::div[contains(@class,'card')][1]"),
				By.xpath("./ancestor::div[contains(@class,'section')][1]"),
				By.xpath("./ancestor::div[1]"));
		for (final By locator : locators) {
			final List<WebElement> candidates = anchorElement.findElements(locator);
			if (!candidates.isEmpty() && candidates.get(0).isDisplayed()) {
				return candidates.get(0);
			}
		}
		return anchorElement;
	}

	private boolean containerContainsText(final WebElement container, final String text) {
		final List<WebElement> matches = container.findElements(By.xpath(".//*[contains(normalize-space(.), "
				+ xpathLiteral(text) + ")]"));
		return matches.stream().anyMatch(WebElement::isDisplayed);
	}

	private WebElement findVisibleInContainerByText(final WebElement container, final String text) {
		final List<WebElement> matches = container.findElements(By.xpath(".//*[contains(normalize-space(.), "
				+ xpathLiteral(text) + ")]"));
		for (final WebElement match : matches) {
			if (match.isDisplayed()) {
				return match;
			}
		}
		return null;
	}

	private List<String> extractVisibleTexts(final WebElement container) {
		final List<String> texts = new ArrayList<>();
		for (final WebElement element : container.findElements(By.xpath(".//*[string-length(normalize-space()) > 0]"))) {
			if (element.isDisplayed()) {
				final String text = element.getText();
				if (text != null && !text.trim().isEmpty()) {
					texts.add(text.trim());
				}
			}
		}
		return texts;
	}

	private boolean looksLikeUserName(final String text) {
		final String normalized = text.trim();
		if (normalized.length() < 3 || normalized.length() > 80 || normalized.contains("@")) {
			return false;
		}
		final String lower = normalized.toLowerCase();
		final List<String> nonNameTokens = Arrays.asList("información general", "business plan", "cambiar plan",
				"detalles de la cuenta", "tus negocios", "sección legal", "cuenta creada", "estado activo",
				"idioma seleccionado", "agregar negocio", "administrar negocios", "tienes ");
		if (nonNameTokens.stream().anyMatch(lower::contains)) {
			return false;
		}
		return normalized.matches(".*\\p{L}.*");
	}

	private Path takeScreenshot(final String fileName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final Path target = evidenceDir.resolve(fileName);
		Files.copy(screenshot.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
		return target;
	}

	private Path takeFullPageScreenshot(final String fileName) throws IOException {
		final JavascriptExecutor js = (JavascriptExecutor) driver;
		final Long pageHeight = (Long) js.executeScript(
				"return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);");
		final Dimension originalSize = driver.manage().window().getSize();
		driver.manage().window().setSize(new Dimension(originalSize.width, Math.min(pageHeight.intValue(), 4000)));
		try {
			return takeScreenshot(fileName);
		} finally {
			driver.manage().window().setSize(originalSize);
		}
	}

	private String sanitizeFileName(final String input) {
		return input.replaceAll("[^a-zA-Z0-9-_\\.]", "_");
	}

	private String readConfig(final String key, final String defaultValue) {
		final String fromProperty = System.getProperty(key);
		if (fromProperty != null && !fromProperty.trim().isEmpty()) {
			return fromProperty.trim();
		}
		final String fromEnv = System.getenv(key);
		if (fromEnv != null && !fromEnv.trim().isEmpty()) {
			return fromEnv.trim();
		}
		return defaultValue;
	}

	private String xpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}
		if (!text.contains("\"")) {
			return "\"" + text + "\"";
		}
		final StringBuilder sb = new StringBuilder("concat(");
		final char[] chars = text.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(chars[i] == '\'' ? "\"'\"" : "'" + chars[i] + "'");
		}
		sb.append(')');
		return sb.toString();
	}

	@FunctionalInterface
	private interface StepAction {
		void run() throws Exception;
	}
}
