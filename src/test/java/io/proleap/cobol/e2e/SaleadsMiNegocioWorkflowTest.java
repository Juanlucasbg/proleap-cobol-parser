package io.proleap.cobol.e2e;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.ChromeOptions;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class SaleadsMiNegocioWorkflowTest {

	private static final String LOGIN_KEY = "Login";
	private static final String MI_NEGOCIO_MENU_KEY = "Mi Negocio menu";
	private static final String AGREGAR_NEGOCIO_MODAL_KEY = "Agregar Negocio modal";
	private static final String ADMINISTRAR_NEGOCIOS_VIEW_KEY = "Administrar Negocios view";
	private static final String INFORMACION_GENERAL_KEY = "Información General";
	private static final String DETALLES_CUENTA_KEY = "Detalles de la Cuenta";
	private static final String TUS_NEGOCIOS_KEY = "Tus Negocios";
	private static final String TERMINOS_KEY = "Términos y Condiciones";
	private static final String PRIVACIDAD_KEY = "Política de Privacidad";

	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
			Pattern.CASE_INSENSITIVE);

	private final Map<String, StepOutcome> report = new LinkedHashMap<>();
	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceRunDirectory;
	private int timeoutSeconds;
	private String loginUrl;
	private String googleAccountEmail;
	private String expectedUserName;
	private String applicationHandle;

	@Before
	public void setUp() throws IOException {
		initializeReportSlots();

		timeoutSeconds = Integer.parseInt(getConfig("SALEADS_TIMEOUT_SECONDS", "saleads.timeout.seconds", "25"));
		loginUrl = getConfig("SALEADS_LOGIN_URL", "saleads.login.url", "");
		googleAccountEmail = getConfig("SALEADS_GOOGLE_ACCOUNT_EMAIL", "saleads.google.account.email",
				"juanlucasbarbiergarzon@gmail.com");
		expectedUserName = getConfig("SALEADS_EXPECTED_USER_NAME", "saleads.expected.user.name", "");

		final boolean headless = Boolean
				.parseBoolean(getConfig("SALEADS_HEADLESS", "saleads.headless", "true"));

		final String runStamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
		evidenceRunDirectory = Path.of("target", "saleads-evidence", runStamp);
		Files.createDirectories(evidenceRunDirectory);

		final ChromeOptions options = new ChromeOptions();
		if (headless) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds));
	}

	@After
	public void tearDown() throws IOException {
		writeFinalReport();
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		final boolean login = executeStep(LOGIN_KEY, this::stepLoginWithGoogle);
		final boolean miNegocioMenu = executeStep(MI_NEGOCIO_MENU_KEY,
				() -> requirePreviousStep(login, this::stepOpenMiNegocioMenu));
		final boolean agregarNegocioModal = executeStep(AGREGAR_NEGOCIO_MODAL_KEY,
				() -> requirePreviousStep(miNegocioMenu, this::stepValidateAgregarNegocioModal));
		final boolean administrarNegociosView = executeStep(ADMINISTRAR_NEGOCIOS_VIEW_KEY,
				() -> requirePreviousStep(miNegocioMenu, this::stepOpenAdministrarNegocios));
		final boolean informacionGeneral = executeStep(INFORMACION_GENERAL_KEY,
				() -> requirePreviousStep(administrarNegociosView, this::stepValidateInformacionGeneral));
		final boolean detallesCuenta = executeStep(DETALLES_CUENTA_KEY,
				() -> requirePreviousStep(administrarNegociosView, this::stepValidateDetallesCuenta));
		final boolean tusNegocios = executeStep(TUS_NEGOCIOS_KEY,
				() -> requirePreviousStep(administrarNegociosView, this::stepValidateTusNegocios));
		final boolean terminos = executeStep(TERMINOS_KEY, () -> requirePreviousStep(tusNegocios,
				() -> stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones", "08-terminos")));
		final boolean privacidad = executeStep(PRIVACIDAD_KEY, () -> requirePreviousStep(tusNegocios,
				() -> stepValidateLegalLink("Política de Privacidad", "Política de Privacidad", "09-privacidad")));

		Assert.assertTrue(failureSummary(),
				login && miNegocioMenu && agregarNegocioModal && administrarNegociosView && informacionGeneral
						&& detallesCuenta && tusNegocios && terminos && privacidad);
	}

	private StepOutcome stepLoginWithGoogle() {
		if (loginUrl.isBlank()) {
			return StepOutcome.fail(
					"SALEADS_LOGIN_URL is required. This test is environment-agnostic and does not hardcode any domain.",
					null, safeCurrentUrl());
		}

		driver.get(loginUrl);
		waitForUiToLoad();
		applicationHandle = driver.getWindowHandle();

		clickByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
		waitForUiToLoad();
		selectGoogleAccountIfVisible();

		final boolean applicationVisible = switchToWindowContainingAnyVisibleText(timeoutSeconds * 2, "Mi Negocio",
				"Negocio", "Dashboard", "Panel");
		final boolean sidebarVisible = isSidebarVisible();
		final String screenshotPath = captureScreenshot("01-dashboard");

		final List<String> missing = new ArrayList<>();
		if (!applicationVisible) {
			missing.add("Main application interface not detected after Google login.");
		}
		if (!sidebarVisible) {
			missing.add("Left sidebar navigation is not visible.");
		}

		if (missing.isEmpty()) {
			return StepOutcome.pass("Dashboard and sidebar are visible after login.", screenshotPath, safeCurrentUrl());
		}
		return StepOutcome.fail(String.join(" ", missing), screenshotPath, safeCurrentUrl());
	}

	private StepOutcome stepOpenMiNegocioMenu() {
		if (!isTextVisible("Mi Negocio", 3)) {
			clickByVisibleText("Negocio");
		}

		clickByVisibleText("Mi Negocio");
		final boolean agregarVisible = waitForAnyVisibleText(10, "Agregar Negocio");
		final boolean administrarVisible = waitForAnyVisibleText(10, "Administrar Negocios");
		final String screenshotPath = captureScreenshot("02-mi-negocio-menu-expanded");

		final List<String> missing = new ArrayList<>();
		if (!agregarVisible) {
			missing.add("'Agregar Negocio' is not visible.");
		}
		if (!administrarVisible) {
			missing.add("'Administrar Negocios' is not visible.");
		}

		if (missing.isEmpty()) {
			return StepOutcome.pass("Mi Negocio submenu expanded correctly.", screenshotPath, safeCurrentUrl());
		}
		return StepOutcome.fail(String.join(" ", missing), screenshotPath, safeCurrentUrl());
	}

	private StepOutcome stepValidateAgregarNegocioModal() {
		clickByVisibleText("Agregar Negocio");
		final WebElement modalTitle = findVisibleElementByText("Crear Nuevo Negocio", 10);
		final WebElement modal = modalTitle
				.findElement(By.xpath("./ancestor::*[self::div or self::section or self::article][1]"));

		final boolean titleVisible = modalTitle.isDisplayed();
		final boolean nameInputVisible = isVisibleInContext(modal,
				By.xpath(".//label[contains(normalize-space(),'Nombre del Negocio')]"))
				|| isVisibleInContext(modal, By.xpath(
						".//input[contains(@placeholder,'Nombre del Negocio') or contains(@aria-label,'Nombre del Negocio')]"));
		final boolean quotaVisible = isVisibleInContext(modal, By.xpath(".//*[contains(normalize-space(),'2 de 3')]"));
		final boolean cancelarVisible = isVisibleInContext(modal,
				By.xpath(".//*[normalize-space()='Cancelar']"));
		final boolean crearVisible = isVisibleInContext(modal,
				By.xpath(".//*[normalize-space()='Crear Negocio']"));

		if (nameInputVisible) {
			try {
				final WebElement input = modal.findElement(By.xpath(".//input"));
				clickAndWait(input);
				input.clear();
				input.sendKeys("Negocio Prueba Automatización");
			} catch (final NoSuchElementException ignored) {
				// Optional action only.
			}
		}
		final String screenshotPath = captureScreenshot("03-agregar-negocio-modal");
		clickWithinContextByText(modal, "Cancelar");

		final List<String> missing = new ArrayList<>();
		if (!titleVisible) {
			missing.add("Modal title 'Crear Nuevo Negocio' is missing.");
		}
		if (!nameInputVisible) {
			missing.add("Input field 'Nombre del Negocio' is missing.");
		}
		if (!quotaVisible) {
			missing.add("Text 'Tienes 2 de 3 negocios' is missing.");
		}
		if (!cancelarVisible) {
			missing.add("Button 'Cancelar' is missing.");
		}
		if (!crearVisible) {
			missing.add("Button 'Crear Negocio' is missing.");
		}

		if (missing.isEmpty()) {
			return StepOutcome.pass("Agregar Negocio modal validated.", screenshotPath, safeCurrentUrl());
		}
		return StepOutcome.fail(String.join(" ", missing), screenshotPath, safeCurrentUrl());
	}

	private StepOutcome stepOpenAdministrarNegocios() {
		if (!isTextVisible("Administrar Negocios", 3)) {
			clickByVisibleText("Mi Negocio");
		}
		clickByVisibleText("Administrar Negocios");

		final boolean infoGeneral = waitForAnyVisibleText(timeoutSeconds, "Información General");
		final boolean detallesCuenta = waitForAnyVisibleText(timeoutSeconds, "Detalles de la Cuenta");
		final boolean tusNegocios = waitForAnyVisibleText(timeoutSeconds, "Tus Negocios");
		final boolean seccionLegal = waitForAnyVisibleText(timeoutSeconds, "Sección Legal");
		final String screenshotPath = captureScreenshot("04-administrar-negocios-view");

		final List<String> missing = new ArrayList<>();
		if (!infoGeneral) {
			missing.add("'Información General' section is missing.");
		}
		if (!detallesCuenta) {
			missing.add("'Detalles de la Cuenta' section is missing.");
		}
		if (!tusNegocios) {
			missing.add("'Tus Negocios' section is missing.");
		}
		if (!seccionLegal) {
			missing.add("'Sección Legal' section is missing.");
		}

		if (missing.isEmpty()) {
			return StepOutcome.pass("Administrar Negocios page loaded with all required sections.", screenshotPath,
					safeCurrentUrl());
		}
		return StepOutcome.fail(String.join(" ", missing), screenshotPath, safeCurrentUrl());
	}

	private StepOutcome stepValidateInformacionGeneral() {
		final String sectionText = extractSectionText("Información General");
		final boolean userNameVisible = hasProbableUserName(sectionText);
		final boolean userEmailVisible = EMAIL_PATTERN.matcher(sectionText).find()
				|| sectionText.contains(googleAccountEmail);
		final boolean businessPlanVisible = containsIgnoringCase(sectionText, "BUSINESS PLAN");
		final boolean cambiarPlanVisible = containsIgnoringCase(sectionText, "Cambiar Plan");

		final List<String> missing = new ArrayList<>();
		if (!userNameVisible) {
			missing.add("User name is not visible.");
		}
		if (!userEmailVisible) {
			missing.add("User email is not visible.");
		}
		if (!businessPlanVisible) {
			missing.add("Text 'BUSINESS PLAN' is not visible.");
		}
		if (!cambiarPlanVisible) {
			missing.add("Button 'Cambiar Plan' is not visible.");
		}

		if (missing.isEmpty()) {
			return StepOutcome.pass("Información General validations passed.", null, safeCurrentUrl());
		}
		return StepOutcome.fail(String.join(" ", missing), null, safeCurrentUrl());
	}

	private StepOutcome stepValidateDetallesCuenta() {
		final String sectionText = extractSectionText("Detalles de la Cuenta");
		final boolean cuentaCreada = containsIgnoringCase(sectionText, "Cuenta creada");
		final boolean estadoActivo = containsIgnoringCase(sectionText, "Estado activo");
		final boolean idiomaSeleccionado = containsIgnoringCase(sectionText, "Idioma seleccionado");

		final List<String> missing = new ArrayList<>();
		if (!cuentaCreada) {
			missing.add("'Cuenta creada' is not visible.");
		}
		if (!estadoActivo) {
			missing.add("'Estado activo' is not visible.");
		}
		if (!idiomaSeleccionado) {
			missing.add("'Idioma seleccionado' is not visible.");
		}

		if (missing.isEmpty()) {
			return StepOutcome.pass("Detalles de la Cuenta validations passed.", null, safeCurrentUrl());
		}
		return StepOutcome.fail(String.join(" ", missing), null, safeCurrentUrl());
	}

	private StepOutcome stepValidateTusNegocios() {
		final String sectionText = extractSectionText("Tus Negocios");
		final WebElement section = findSection("Tus Negocios");
		final boolean agregarNegocioVisible = containsIgnoringCase(sectionText, "Agregar Negocio");
		final boolean quotaVisible = containsIgnoringCase(sectionText, "2 de 3 negocios");
		final boolean listVisible = hasBusinessItems(section, sectionText);

		final List<String> missing = new ArrayList<>();
		if (!listVisible) {
			missing.add("Business list is not visible.");
		}
		if (!agregarNegocioVisible) {
			missing.add("Button 'Agregar Negocio' is not visible.");
		}
		if (!quotaVisible) {
			missing.add("Text 'Tienes 2 de 3 negocios' is not visible.");
		}

		if (missing.isEmpty()) {
			return StepOutcome.pass("Tus Negocios validations passed.", null, safeCurrentUrl());
		}
		return StepOutcome.fail(String.join(" ", missing), null, safeCurrentUrl());
	}

	private StepOutcome stepValidateLegalLink(final String linkText, final String expectedHeading,
			final String screenshotName) {
		final String currentHandle = driver.getWindowHandle();
		final Set<String> previousHandles = new HashSet<>(driver.getWindowHandles());

		clickByVisibleText(linkText);

		final String newTabHandle = waitForNewHandle(previousHandles, 10);
		final boolean openedNewTab = newTabHandle != null;
		if (openedNewTab) {
			driver.switchTo().window(newTabHandle);
			waitForUiToLoad();
		} else {
			waitForUiToLoad();
		}

		final boolean headingVisible = waitForAnyVisibleText(timeoutSeconds, expectedHeading);
		final String bodyText = getBodyText();
		final boolean legalContentVisible = bodyText != null && bodyText.trim().length() > 120;
		final String screenshotPath = captureScreenshot(screenshotName);
		final String finalUrl = safeCurrentUrl();

		if (openedNewTab) {
			driver.close();
			driver.switchTo().window(currentHandle);
			waitForUiToLoad();
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		if (applicationHandle != null && driver.getWindowHandles().contains(applicationHandle)) {
			driver.switchTo().window(applicationHandle);
		}

		final List<String> missing = new ArrayList<>();
		if (!headingVisible) {
			missing.add("Heading '" + expectedHeading + "' is not visible.");
		}
		if (!legalContentVisible) {
			missing.add("Legal content text is not visible.");
		}

		if (missing.isEmpty()) {
			return StepOutcome.pass("Validated " + linkText + ".", screenshotPath, finalUrl);
		}
		return StepOutcome.fail(String.join(" ", missing), screenshotPath, finalUrl);
	}

	private void selectGoogleAccountIfVisible() {
		for (final String handle : driver.getWindowHandles()) {
			driver.switchTo().window(handle);
			final String url = safeCurrentUrl().toLowerCase(Locale.ROOT);
			final boolean looksLikeGoogle = url.contains("google")
					|| isTextVisible("Choose an account", 2)
					|| isTextVisible("Elige una cuenta", 2);
			if (!looksLikeGoogle) {
				continue;
			}

			if (isTextVisible(googleAccountEmail, 4)) {
				clickByVisibleText(googleAccountEmail);
				waitForUiToLoad();
				return;
			}
		}
	}

	private boolean switchToWindowContainingAnyVisibleText(final int maxSeconds, final String... texts) {
		final long deadline = System.currentTimeMillis() + (maxSeconds * 1000L);
		while (System.currentTimeMillis() < deadline) {
			for (final String handle : driver.getWindowHandles()) {
				driver.switchTo().window(handle);
				waitForUiToLoad();
				for (final String text : texts) {
					if (isTextVisible(text, 2)) {
						applicationHandle = handle;
						return true;
					}
				}
			}
			sleepSilently(600);
		}
		return false;
	}

	private String waitForNewHandle(final Set<String> previousHandles, final int maxSeconds) {
		final long deadline = System.currentTimeMillis() + (maxSeconds * 1000L);
		while (System.currentTimeMillis() < deadline) {
			final Set<String> currentHandles = driver.getWindowHandles();
			if (currentHandles.size() > previousHandles.size()) {
				for (final String handle : currentHandles) {
					if (!previousHandles.contains(handle)) {
						return handle;
					}
				}
			}
			sleepSilently(300);
		}
		return null;
	}

	private String extractSectionText(final String sectionTitle) {
		return findSection(sectionTitle).getText();
	}

	private WebElement findSection(final String sectionTitle) {
		final WebElement sectionHeader = findVisibleElementByText(sectionTitle, timeoutSeconds);
		return sectionHeader.findElement(By.xpath("./ancestor::*[self::section or self::article or self::div][1]"));
	}

	private boolean hasBusinessItems(final WebElement section, final String sectionText) {
		final List<WebElement> listCandidates = section.findElements(By.xpath(
				".//li[normalize-space()] | .//tr[normalize-space()] | .//*[@role='row'][normalize-space()] | .//article[normalize-space()]"));
		if (!listCandidates.isEmpty()) {
			return true;
		}

		final String[] lines = sectionText.split("\\R+");
		int nonEmptyLines = 0;
		for (final String line : lines) {
			if (!line.trim().isEmpty()) {
				nonEmptyLines++;
			}
		}
		return nonEmptyLines >= 4;
	}

	private boolean hasProbableUserName(final String sectionText) {
		if (!expectedUserName.isBlank()) {
			return containsIgnoringCase(sectionText, expectedUserName);
		}

		final List<String> ignoredTokens = Arrays.asList("información general", "business plan", "cambiar plan",
				"email", "correo", "usuario", "plan");
		for (final String rawLine : sectionText.split("\\R+")) {
			final String line = rawLine.trim();
			if (line.isEmpty()) {
				continue;
			}
			final String lowerLine = line.toLowerCase(Locale.ROOT);
			if (EMAIL_PATTERN.matcher(line).find()) {
				continue;
			}
			boolean ignored = false;
			for (final String ignoredToken : ignoredTokens) {
				if (lowerLine.contains(ignoredToken)) {
					ignored = true;
					break;
				}
			}
			if (ignored) {
				continue;
			}
			if (line.length() >= 3 && line.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*")) {
				return true;
			}
		}
		return false;
	}

	private boolean isSidebarVisible() {
		final List<By> locators = Arrays.asList(By.xpath("//aside"),
				By.xpath("//nav[contains(@class,'sidebar')]"),
				By.xpath("//*[contains(@class,'sidebar')]"),
				By.xpath("//nav"));
		for (final By locator : locators) {
			try {
				if (!driver.findElements(locator).isEmpty() && driver.findElement(locator).isDisplayed()) {
					return true;
				}
			} catch (final NoSuchElementException ignored) {
				// try next locator
			}
		}
		return false;
	}

	private void clickWithinContextByText(final WebElement context, final String text) {
		final String literal = xpathLiteral(text);
		final List<WebElement> candidates = context.findElements(By.xpath(
				".//*[normalize-space()=" + literal + "]/ancestor-or-self::*[self::button or self::a or @role='button'][1]"));
		if (candidates.isEmpty()) {
			throw new NoSuchElementException("Unable to click '" + text + "' inside given context.");
		}
		clickAndWait(candidates.get(0));
	}

	private void clickByVisibleText(final String... candidateTexts) {
		NoSuchElementException lastException = null;
		for (final String text : candidateTexts) {
			try {
				final WebElement element = findClickableByVisibleText(text, 8);
				clickAndWait(element);
				return;
			} catch (final NoSuchElementException e) {
				lastException = e;
			}
		}

		throw new NoSuchElementException(
				"Could not click any candidate text: " + Arrays.toString(candidateTexts), lastException);
	}

	private WebElement findClickableByVisibleText(final String text, final int seconds) {
		final String literal = xpathLiteral(text);
		final List<By> locators = Arrays.asList(
				By.xpath("(//*[normalize-space()=" + literal + "]/ancestor-or-self::*[self::button or self::a or @role='button'][1])[1]"),
				By.xpath("(//button[normalize-space()=" + literal + "])[1]"),
				By.xpath("(//a[normalize-space()=" + literal + "])[1]"),
				By.xpath("(//*[normalize-space()=" + literal + "])[1]"));
		final WebDriverWait localWait = new WebDriverWait(driver, Duration.ofSeconds(seconds));

		for (final By locator : locators) {
			try {
				final WebElement element = localWait.until(ExpectedConditions.visibilityOfElementLocated(locator));
				return localWait.until(ExpectedConditions.elementToBeClickable(element));
			} catch (final TimeoutException ignored) {
				// try next locator
			}
		}
		throw new NoSuchElementException("Unable to find clickable element for text: " + text);
	}

	private WebElement findVisibleElementByText(final String text, final int seconds) {
		final String literal = xpathLiteral(text);
		final By locator = By.xpath("//*[normalize-space()=" + literal + "]");
		return new WebDriverWait(driver, Duration.ofSeconds(seconds))
				.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	private boolean isTextVisible(final String text, final int seconds) {
		try {
			findVisibleElementByText(text, seconds);
			return true;
		} catch (final TimeoutException e) {
			return false;
		}
	}

	private boolean waitForAnyVisibleText(final int maxSeconds, final String... texts) {
		final long deadline = System.currentTimeMillis() + (maxSeconds * 1000L);
		while (System.currentTimeMillis() < deadline) {
			for (final String text : texts) {
				if (isTextVisible(text, 1)) {
					return true;
				}
			}
			sleepSilently(300);
		}
		return false;
	}

	private boolean isVisibleInContext(final WebElement context, final By locator) {
		try {
			for (final WebElement element : context.findElements(locator)) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		} catch (final NoSuchElementException ignored) {
			return false;
		}
		return false;
	}

	private void clickAndWait(final WebElement element) {
		scrollIntoView(element);
		try {
			element.click();
		} catch (final RuntimeException clickFailure) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private void scrollIntoView(final WebElement element) {
		((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
	}

	private void waitForUiToLoad() {
		final ExpectedCondition<Boolean> documentReady = webDriver -> "complete"
				.equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState"));
		wait.until(documentReady);

		final List<By> busyIndicators = Arrays.asList(By.cssSelector(".loading"), By.cssSelector(".loader"),
				By.cssSelector(".spinner"), By.cssSelector("[aria-busy='true']"));
		for (final By locator : busyIndicators) {
			try {
				new WebDriverWait(driver, Duration.ofSeconds(2)).until(ExpectedConditions.invisibilityOfElementLocated(locator));
			} catch (final TimeoutException ignored) {
				// ignore absent or persistent non-blocking indicators
			}
		}
		sleepSilently(350);
	}

	private String captureScreenshot(final String checkpointName) {
		if (driver == null) {
			return null;
		}

		final String sanitized = checkpointName.replaceAll("[^A-Za-z0-9._-]", "_");
		final Path screenshotPath = evidenceRunDirectory.resolve(sanitized + ".png");
		try {
			final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			Files.copy(screenshot.toPath(), screenshotPath, StandardCopyOption.REPLACE_EXISTING);
			return screenshotPath.toString();
		} catch (final IOException ioException) {
			return null;
		}
	}

	private String getBodyText() {
		try {
			return driver.findElement(By.tagName("body")).getText();
		} catch (final NoSuchElementException ignored) {
			return "";
		}
	}

	private String safeCurrentUrl() {
		try {
			return driver == null ? "" : driver.getCurrentUrl();
		} catch (final RuntimeException ignored) {
			return "";
		}
	}

	private boolean executeStep(final String key, final StepRunner runner) {
		try {
			final StepOutcome outcome = runner.run();
			report.put(key, outcome);
			return outcome.passed;
		} catch (final Exception exception) {
			final String screenshot = captureScreenshot(key.replaceAll("[^A-Za-z0-9._-]", "_") + "-error");
			report.put(key, StepOutcome.fail("Exception: " + exception.getMessage(), screenshot, safeCurrentUrl()));
			return false;
		}
	}

	private StepOutcome requirePreviousStep(final boolean previousPassed, final StepRunner nextStep) throws Exception {
		if (!previousPassed) {
			return StepOutcome.fail("Blocked because a previous required step failed.", null, safeCurrentUrl());
		}
		return nextStep.run();
	}

	private void initializeReportSlots() {
		report.clear();
		for (final String key : Arrays.asList(LOGIN_KEY, MI_NEGOCIO_MENU_KEY, AGREGAR_NEGOCIO_MODAL_KEY,
				ADMINISTRAR_NEGOCIOS_VIEW_KEY, INFORMACION_GENERAL_KEY, DETALLES_CUENTA_KEY, TUS_NEGOCIOS_KEY,
				TERMINOS_KEY, PRIVACIDAD_KEY)) {
			report.put(key, StepOutcome.fail("Not executed yet.", null, ""));
		}
	}

	private String failureSummary() {
		final StringBuilder summary = new StringBuilder("SaleADS Mi Negocio workflow failures:\n");
		for (final Map.Entry<String, StepOutcome> entry : report.entrySet()) {
			if (!entry.getValue().passed) {
				summary.append("- ").append(entry.getKey()).append(": ").append(entry.getValue().detail).append('\n');
			}
		}
		return summary.toString();
	}

	private void writeFinalReport() throws IOException {
		if (evidenceRunDirectory == null) {
			return;
		}

		final StringBuilder markdown = new StringBuilder();
		markdown.append("# SaleADS Mi Negocio Workflow Report\n\n");
		markdown.append("| Step | Status | Details | URL | Screenshot |\n");
		markdown.append("|---|---|---|---|---|\n");

		for (final Map.Entry<String, StepOutcome> entry : report.entrySet()) {
			final StepOutcome outcome = entry.getValue();
			markdown.append("| ").append(entry.getKey()).append(" | ")
					.append(outcome.passed ? "PASS" : "FAIL").append(" | ")
					.append(escapeForTable(outcome.detail)).append(" | ")
					.append(escapeForTable(outcome.url)).append(" | ")
					.append(escapeForTable(outcome.screenshotPath)).append(" |\n");
		}

		final Path reportPath = evidenceRunDirectory.resolve("final-report.md");
		Files.writeString(reportPath, markdown.toString(), StandardCharsets.UTF_8);
	}

	private String escapeForTable(final String value) {
		if (value == null) {
			return "";
		}
		return value.replace("|", "\\|").replace("\n", " ").replace("\r", " ");
	}

	private String getConfig(final String envName, final String propertyName, final String defaultValue) {
		final String property = System.getProperty(propertyName);
		if (property != null && !property.isBlank()) {
			return property.trim();
		}
		final String env = System.getenv(envName);
		if (env != null && !env.isBlank()) {
			return env.trim();
		}
		return defaultValue;
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder result = new StringBuilder("concat(");
		final String[] parts = value.split("'");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				result.append(",\"'\",");
			}
			result.append("'").append(parts[i]).append("'");
		}
		result.append(")");
		return result.toString();
	}

	private boolean containsIgnoringCase(final String text, final String probe) {
		return text.toLowerCase(Locale.ROOT).contains(probe.toLowerCase(Locale.ROOT));
	}

	private void sleepSilently(final long millis) {
		try {
			Thread.sleep(millis);
		} catch (final InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
		}
	}

	@FunctionalInterface
	private interface StepRunner {
		StepOutcome run() throws Exception;
	}

	private static final class StepOutcome {
		private final boolean passed;
		private final String detail;
		private final String screenshotPath;
		private final String url;

		private StepOutcome(final boolean passed, final String detail, final String screenshotPath, final String url) {
			this.passed = passed;
			this.detail = detail;
			this.screenshotPath = screenshotPath;
			this.url = url;
		}

		private static StepOutcome pass(final String detail, final String screenshotPath, final String url) {
			return new StepOutcome(true, detail, screenshotPath, url);
		}

		private static StepOutcome fail(final String detail, final String screenshotPath, final String url) {
			return new StepOutcome(false, detail, screenshotPath, url);
		}
	}
}
