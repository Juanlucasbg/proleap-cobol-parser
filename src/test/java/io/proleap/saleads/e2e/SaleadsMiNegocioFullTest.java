package io.proleap.saleads.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.StaleElementReferenceException;
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

	private static final Duration DEFAULT_WAIT = Duration.ofSeconds(30);
	private static final Duration SHORT_WAIT = Duration.ofSeconds(10);
	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

	private final Map<String, String> finalReport = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDirectory;
	private String appWindowHandle;

	@Before
	public void setUp() throws IOException {
		initializeReport();
		final String loginUrl = firstNonBlank(System.getenv("SALEADS_LOGIN_URL"), System.getProperty("saleads.login.url"));
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL (or -Dsaleads.login.url) to the current environment login page before running this test.",
				loginUrl != null && !loginUrl.isBlank());

		final ChromeOptions options = new ChromeOptions();
		if (isHeadlessEnabled()) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1600,1000");
		options.addArguments("--disable-gpu");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");

		driver = new ChromeDriver(options);
		driver.manage().window().setSize(new Dimension(1600, 1000));
		wait = new WebDriverWait(driver, DEFAULT_WAIT);
		screenshotDirectory = Files.createDirectories(Path.of("target", "saleads-screenshots"));

		driver.get(loginUrl);
		waitForUiToLoad();
		appWindowHandle = driver.getWindowHandle();
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflowTest() {
		runStep("Login", this::loginWithGoogleAndValidateDashboard);
		runStep("Mi Negocio menu", this::openMiNegocioMenuAndValidate);
		runStep("Agregar Negocio modal", this::validateAgregarNegocioModal);
		runStep("Administrar Negocios view", this::openAdministrarNegociosAndValidate);
		runStep("Información General", this::validateInformacionGeneral);
		runStep("Detalles de la Cuenta", this::validateDetallesCuenta);
		runStep("Tus Negocios", this::validateTusNegocios);
		runStep("Términos y Condiciones", () -> validateLegalLink("Términos y Condiciones", "Términos y Condiciones",
				"08-terminos-y-condiciones"));
		runStep("Política de Privacidad",
				() -> validateLegalLink("Política de Privacidad", "Política de Privacidad", "09-politica-de-privacidad"));

		final String reportOutput = buildFinalReport();
		System.out.println(reportOutput);

		final List<String> failedSteps = finalReport.entrySet().stream().filter(entry -> !entry.getValue().startsWith("PASS"))
				.map(Map.Entry::getKey).collect(Collectors.toList());
		Assert.assertTrue("One or more SaleADS workflow validations failed.\n" + reportOutput, failedSteps.isEmpty());
	}

	private void loginWithGoogleAndValidateDashboard() {
		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Ingresar con Google", "Continuar con Google",
				"Login with Google");
		selectGoogleAccountIfVisible();
		assertMainInterfaceVisible();
		captureScreenshot("01-dashboard-loaded");
	}

	private void openMiNegocioMenuAndValidate() {
		ensureMiNegocioMenuExpanded();
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void validateAgregarNegocioModal() {
		clickByVisibleText("Agregar Negocio");
		assertTextVisible("Crear Nuevo Negocio");
		findNombreDelNegocioInput();
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");
		captureScreenshot("03-agregar-negocio-modal");

		final WebElement businessNameInput = findNombreDelNegocioInput();
		businessNameInput.click();
		waitForUiToLoad();
		businessNameInput.sendKeys("Negocio Prueba Automatización");
		clickByVisibleText("Cancelar");
	}

	private void openAdministrarNegociosAndValidate() {
		ensureMiNegocioMenuExpanded();
		clickByVisibleText("Administrar Negocios");
		waitForUiToLoad();
		assertTextVisible("Información General");
		assertTextVisible("Detalles de la Cuenta");
		assertTextVisible("Tus Negocios");
		assertTextVisible("Sección Legal");
		captureScreenshot("04-administrar-negocios-view");
	}

	private void validateInformacionGeneral() {
		final WebElement section = sectionByHeading("Información General");
		assertEmailVisible(section);
		assertLikelyUserNameVisible(section);
		assertTextVisibleInSection(section, "BUSINESS PLAN");
		assertTextVisibleInSection(section, "Cambiar Plan");
	}

	private void validateDetallesCuenta() {
		final WebElement section = sectionByHeading("Detalles de la Cuenta");
		assertTextVisibleInSection(section, "Cuenta creada");
		assertTextVisibleInSection(section, "Estado activo");
		assertTextVisibleInSection(section, "Idioma seleccionado");
	}

	private void validateTusNegocios() {
		final WebElement section = sectionByHeading("Tus Negocios");
		assertTextVisibleInSection(section, "Agregar Negocio");
		assertTextVisibleInSection(section, "Tienes 2 de 3 negocios");

		final List<WebElement> structuredListItems = visibleElements(section,
				By.xpath(".//li | .//tr[.//td] | .//*[contains(@class,'business') or contains(@class,'negocio')]"));
		final boolean hasStructuredItems = !structuredListItems.isEmpty();

		final List<String> textItems = visibleElements(section, By.xpath(".//*[self::p or self::span or self::div]")).stream()
				.map(WebElement::getText).map(String::trim).filter(text -> text.length() > 2).collect(Collectors.toList());
		final boolean hasReadableBusinessContent = textItems.size() >= 3;

		Assert.assertTrue("Business list is not visible in 'Tus Negocios' section.",
				hasStructuredItems || hasReadableBusinessContent);
	}

	private void validateLegalLink(final String linkText, final String headingText, final String screenshotName) {
		final WebElement legalSection = sectionByHeading("Sección Legal");
		final Set<String> handlesBeforeClick = driver.getWindowHandles();
		clickByVisibleTextWithin(legalSection, linkText);
		waitForUiToLoad();

		final String originalWindow = appWindowHandle != null ? appWindowHandle : driver.getWindowHandle();
		final Optional<String> newWindowHandle = waitForNewWindow(handlesBeforeClick);
		if (newWindowHandle.isPresent()) {
			driver.switchTo().window(newWindowHandle.get());
			waitForUiToLoad();
		}

		assertTextVisible(headingText);
		assertLegalContentVisible();
		captureScreenshot(screenshotName);
		legalUrls.put(linkText, driver.getCurrentUrl());

		if (newWindowHandle.isPresent()) {
			driver.close();
			driver.switchTo().window(originalWindow);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}
	}

	private void runStep(final String stepName, final StepAction stepAction) {
		try {
			stepAction.run();
			finalReport.put(stepName, "PASS");
		} catch (final Throwable throwable) {
			captureScreenshot(stepName.toLowerCase().replace(" ", "-") + "-failure");
			finalReport.put(stepName, "FAIL - " + sanitizeFailureMessage(throwable));
		}
	}

	private void ensureMiNegocioMenuExpanded() {
		if (isTextVisible("Agregar Negocio") && isTextVisible("Administrar Negocios")) {
			return;
		}

		if (isTextVisible("Mi Negocio")) {
			clickByVisibleText("Mi Negocio");
		} else {
			clickByVisibleText("Negocio");
			clickByVisibleText("Mi Negocio");
		}

		if (!(isTextVisible("Agregar Negocio") && isTextVisible("Administrar Negocios"))) {
			clickByVisibleText("Negocio", "Mi Negocio");
		}

		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");
	}

	private void selectGoogleAccountIfVisible() {
		final WebDriverWait shortWait = new WebDriverWait(driver, SHORT_WAIT);
		try {
			final WebElement accountOption = shortWait.until(ExpectedConditions.visibilityOfElementLocated(
					By.xpath("//*[contains(normalize-space(.),'" + GOOGLE_ACCOUNT_EMAIL + "')]")));
			scrollIntoView(accountOption);
			accountOption.click();
			waitForUiToLoad();
		} catch (final TimeoutException ignored) {
			// Some environments may bypass the account chooser if already authenticated.
		}
	}

	private void assertMainInterfaceVisible() {
		final WebElement sidebar = waitForAnyVisible(By.xpath("//aside"), By.xpath("//*[contains(@class,'sidebar')]"),
				By.xpath("//nav[contains(@class,'sidebar') or @aria-label='Sidebar']"), By.xpath("//nav"));
		Assert.assertTrue("Left sidebar navigation is not visible.", sidebar.isDisplayed());
		assertTextVisible("Negocio");
	}

	private WebElement sectionByHeading(final String headingText) {
		final By headingLocator = By
				.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4 or self::span or self::p][normalize-space(.)='"
						+ headingText + "']");
		wait.until(ExpectedConditions.visibilityOfElementLocated(headingLocator));
		final WebElement headingElement = firstVisibleElement(headingLocator);
		Assert.assertNotNull("Could not find heading: " + headingText, headingElement);

		WebElement container = headingElement;
		for (int i = 0; i < 5; i++) {
			final WebElement parent = parentOf(container);
			if (parent == null) {
				break;
			}
			if (parent.findElements(headingLocator).size() > 0 && parent.findElements(By.xpath(".//*")).size() > 4) {
				container = parent;
				break;
			}
			container = parent;
		}
		return container;
	}

	private WebElement findNombreDelNegocioInput() {
		final List<By> selectors = Arrays.asList(By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//label[normalize-space(.)='Nombre del Negocio']/following::input[1]"),
				By.xpath("//input[contains(@aria-label,'Nombre del Negocio')]"));

		for (final By selector : selectors) {
			final WebElement element = firstVisibleElement(selector);
			if (element != null) {
				return element;
			}
		}
		throw new AssertionError("Input field 'Nombre del Negocio' was not found.");
	}

	private Optional<String> waitForNewWindow(final Set<String> handlesBeforeClick) {
		try {
			final WebDriverWait shortWait = new WebDriverWait(driver, SHORT_WAIT);
			shortWait.until((ExpectedCondition<Boolean>) drv -> drv.getWindowHandles().size() > handlesBeforeClick.size());
			for (final String handle : driver.getWindowHandles()) {
				if (!handlesBeforeClick.contains(handle)) {
					return Optional.of(handle);
				}
			}
		} catch (final TimeoutException ignored) {
			// Link may navigate in the same tab; this is acceptable.
		}
		return Optional.empty();
	}

	private void assertLegalContentVisible() {
		final List<WebElement> legalContentBlocks = visibleElements(driver, By.xpath(
				"//*[self::p or self::div or self::span][string-length(normalize-space(.)) > 60 and not(self::script)]"));
		Assert.assertTrue("Legal content text is not visible.", !legalContentBlocks.isEmpty());
	}

	private void assertLikelyUserNameVisible(final WebElement section) {
		final Set<String> staticLabels = new LinkedHashSet<>(Arrays.asList("Información General", "BUSINESS PLAN",
				"Cambiar Plan", "Cuenta creada", "Estado activo", "Idioma seleccionado"));

		final List<String> values = visibleElements(section, By.xpath(".//*[self::h1 or self::h2 or self::h3 or self::p or self::span or self::div]"))
				.stream().map(WebElement::getText).map(String::trim).filter(text -> !text.isEmpty())
				.filter(text -> !text.contains("@")).filter(text -> !staticLabels.contains(text)).collect(Collectors.toList());

		Assert.assertTrue("User name is not visible in 'Información General'.", !values.isEmpty());
	}

	private void assertEmailVisible(final WebElement section) {
		final List<WebElement> emailElements = visibleElements(section, By.xpath(".//*[contains(normalize-space(.), '@')]"));
		Assert.assertTrue("User email is not visible in 'Información General'.", !emailElements.isEmpty());
	}

	private void assertTextVisibleInSection(final WebElement section, final String text) {
		final By textLocator = By.xpath(".//*[normalize-space(.)='" + text + "']");
		final List<WebElement> elements = visibleElements(section, textLocator);
		Assert.assertTrue("Expected text '" + text + "' is not visible in the expected section.", !elements.isEmpty());
	}

	private void assertTextVisible(final String text) {
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[normalize-space(.)='" + text + "']")));
	}

	private boolean isTextVisible(final String text) {
		return !visibleElements(driver, By.xpath("//*[normalize-space(.)='" + text + "']")).isEmpty();
	}

	private void clickByVisibleText(final String... texts) {
		for (final String text : texts) {
			final WebElement element = firstVisibleElement(By.xpath("//*[self::button or self::a or @role='button' or self::span or self::div][normalize-space(.)='"
					+ text + "']"));
			if (element != null) {
				scrollIntoView(element);
				wait.until(ExpectedConditions.elementToBeClickable(element));
				element.click();
				waitForUiToLoad();
				return;
			}
		}
		throw new AssertionError("Could not click any of the expected text options: " + Arrays.toString(texts));
	}

	private void clickByVisibleTextWithin(final WebElement container, final String text) {
		final List<WebElement> matches = visibleElements(container, By.xpath(".//*[self::a or self::button or @role='button'][normalize-space(.)='"
				+ text + "']"));
		if (matches.isEmpty()) {
			throw new AssertionError("Could not find clickable text '" + text + "' inside its section.");
		}
		final WebElement element = matches.get(0);
		scrollIntoView(element);
		wait.until(ExpectedConditions.elementToBeClickable(element));
		element.click();
		waitForUiToLoad();
	}

	private WebElement waitForAnyVisible(final By... candidates) {
		wait.until(driver -> Arrays.stream(candidates).anyMatch(candidate -> !visibleElements(driver, candidate).isEmpty()));
		for (final By candidate : candidates) {
			final WebElement element = firstVisibleElement(candidate);
			if (element != null) {
				return element;
			}
		}
		throw new AssertionError("None of the candidate elements became visible.");
	}

	private WebElement firstVisibleElement(final By locator) {
		final List<WebElement> elements = visibleElements(driver, locator);
		return elements.isEmpty() ? null : elements.get(0);
	}

	private List<WebElement> visibleElements(final SearchContext context, final By locator) {
		try {
			return context.findElements(locator).stream().filter(WebElement::isDisplayed).collect(Collectors.toList());
		} catch (final StaleElementReferenceException | NoSuchElementException ignored) {
			return new ArrayList<>();
		}
	}

	private void waitForUiToLoad() {
		wait.until(driver -> {
			final Object readyState = ((JavascriptExecutor) driver).executeScript("return document.readyState");
			return "complete".equals(readyState);
		});
		wait.until(ExpectedConditions.or(ExpectedConditions.invisibilityOfElementLocated(
				By.xpath("//*[contains(@class,'loading') or contains(@class,'spinner') or @aria-busy='true']")),
				ExpectedConditions.presenceOfAllElementsLocatedBy(By.xpath("//*"))));
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
	}

	private void captureScreenshot(final String checkpointName) {
		if (screenshotDirectory == null || !(driver instanceof TakesScreenshot)) {
			return;
		}
		try {
			final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
			final Path targetPath = screenshotDirectory.resolve(timestamp + "-" + checkpointName + ".png");
			final Path sourcePath = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE).toPath();
			Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
		} catch (final IOException ignored) {
			// Screenshot capture should not break the workflow validations.
		}
	}

	private String sanitizeFailureMessage(final Throwable throwable) {
		final String rawMessage = throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName();
		return rawMessage.replace('\n', ' ').trim();
	}

	private String buildFinalReport() {
		final StringBuilder builder = new StringBuilder();
		builder.append(System.lineSeparator()).append("SaleADS Mi Negocio Workflow Final Report").append(System.lineSeparator());
		finalReport.forEach((step, status) -> builder.append("- ").append(step).append(": ").append(status).append(System.lineSeparator()));
		if (!legalUrls.isEmpty()) {
			builder.append("Captured legal URLs:").append(System.lineSeparator());
			legalUrls.forEach((label, url) -> builder.append("  - ").append(label).append(": ").append(url).append(System.lineSeparator()));
		}
		builder.append("Screenshots directory: ").append(screenshotDirectory.toAbsolutePath());
		return builder.toString();
	}

	private void initializeReport() {
		finalReport.put("Login", "NOT RUN");
		finalReport.put("Mi Negocio menu", "NOT RUN");
		finalReport.put("Agregar Negocio modal", "NOT RUN");
		finalReport.put("Administrar Negocios view", "NOT RUN");
		finalReport.put("Información General", "NOT RUN");
		finalReport.put("Detalles de la Cuenta", "NOT RUN");
		finalReport.put("Tus Negocios", "NOT RUN");
		finalReport.put("Términos y Condiciones", "NOT RUN");
		finalReport.put("Política de Privacidad", "NOT RUN");
	}

	private boolean isHeadlessEnabled() {
		final String headlessFlag = firstNonBlank(System.getenv("SALEADS_HEADLESS"), System.getProperty("saleads.headless"));
		return headlessFlag == null || Boolean.parseBoolean(headlessFlag);
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return null;
	}

	private WebElement parentOf(final WebElement element) {
		try {
			return element.findElement(By.xpath(".."));
		} catch (final NoSuchElementException ignored) {
			return null;
		}
	}

	@FunctionalInterface
	private interface StepAction {
		void run();
	}
}
