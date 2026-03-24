package io.proleap.cobol.e2e;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleAdsMiNegocioFullTest {

	private static final String EMAIL_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
	private static final String E2E_ENABLED_PROPERTY = "saleads.e2e.enabled";
	private static final String BASE_URL_PROPERTY = "saleads.baseUrl";
	private static final String DEBUGGER_ADDRESS_PROPERTY = "saleads.debuggerAddress";
	private static final String HEADLESS_PROPERTY = "saleads.headless";
	private static final String EXPECTED_USER_NAME_PROPERTY = "saleads.userName";
	private static final String EXPECTED_USER_EMAIL_PROPERTY = "saleads.userEmail";

	private static final DateTimeFormatter FILE_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMINISTRAR_VIEW = "Administrar Negocios view";
	private static final String REPORT_INFO_GENERAL = "Información General";
	private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Términos y Condiciones";
	private static final String REPORT_POLITICA = "Política de Privacidad";

	private static final List<String> FINAL_REPORT_FIELDS = Arrays.asList(REPORT_LOGIN, REPORT_MI_NEGOCIO_MENU,
			REPORT_AGREGAR_MODAL, REPORT_ADMINISTRAR_VIEW, REPORT_INFO_GENERAL, REPORT_DETALLES_CUENTA,
			REPORT_TUS_NEGOCIOS, REPORT_TERMINOS, REPORT_POLITICA);

	private WebDriver driver;
	private WebDriverWait wait;
	private Path artifactsDir;
	private Path screenshotsDir;
	private Path reportPath;
	private boolean workflowExecuted;
	private boolean e2eEnabled;

	private final LinkedHashMap<String, Boolean> finalReport = new LinkedHashMap<>();
	private final LinkedHashMap<String, List<String>> reportNotes = new LinkedHashMap<>();
	private final LinkedHashMap<String, String> legalFinalUrls = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		e2eEnabled = Boolean.parseBoolean(readConfig(E2E_ENABLED_PROPERTY, "false"));
		Assume.assumeTrue(
				"Set -Dsaleads.e2e.enabled=true to execute this environment-dependent workflow test.",
				e2eEnabled);

		artifactsDir = Paths.get("target", "saleads-mi-negocio");
		screenshotsDir = artifactsDir.resolve("screenshots");
		reportPath = artifactsDir.resolve("final-report.txt");
		Files.createDirectories(screenshotsDir);

		for (final String field : FINAL_REPORT_FIELDS) {
			finalReport.put(field, Boolean.FALSE);
		}

		final ChromeOptions options = new ChromeOptions();
		final String debuggerAddress = readConfig(DEBUGGER_ADDRESS_PROPERTY);
		final boolean hasDebuggerAddress = !isBlank(debuggerAddress);
		final boolean headless = Boolean.parseBoolean(readConfig(HEADLESS_PROPERTY, "false"));

		if (hasDebuggerAddress) {
			options.setExperimentalOption("debuggerAddress", debuggerAddress.trim());
		} else if (headless) {
			options.addArguments("--headless=new");
		}

		options.addArguments("--disable-dev-shm-usage", "--no-sandbox", "--window-size=1600,1200");

		driver = new ChromeDriver(options);
		driver.manage().window().setSize(new Dimension(1600, 1200));
		wait = new WebDriverWait(driver, Duration.ofSeconds(30));

		final String configuredBaseUrl = readConfig(BASE_URL_PROPERTY);
		final String currentUrl = safeCurrentUrl();
		if (!isBlank(configuredBaseUrl)
				&& (isBlank(currentUrl) || currentUrl.startsWith("about:") || currentUrl.startsWith("data:"))) {
			driver.navigate().to(configuredBaseUrl.trim());
			waitForUiToLoad();
		}
	}

	@After
	public void tearDown() throws IOException {
		if (workflowExecuted) {
			writeFinalReport();
		}

		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws IOException {
		workflowExecuted = true;

		runLoginStep();
		runMiNegocioMenuStep();
		runAgregarNegocioModalStep();
		runAdministrarNegociosStep();
		runInformacionGeneralStep();
		runDetallesDeLaCuentaStep();
		runTusNegociosStep();
		runLegalStep(REPORT_TERMINOS, "Términos y Condiciones", "Términos y Condiciones", "step8_terminos");
		runLegalStep(REPORT_POLITICA, "Política de Privacidad", "Política de Privacidad", "step9_politica");

		writeFinalReport();

		final List<String> failedFields = new ArrayList<>();
		for (final String field : FINAL_REPORT_FIELDS) {
			if (!Boolean.TRUE.equals(finalReport.get(field))) {
				failedFields.add(field);
			}
		}

		Assert.assertTrue("Some validations failed: " + failedFields + ". See report at " + reportPath.toAbsolutePath(),
				failedFields.isEmpty());
	}

	private void runLoginStep() {
		final String reportKey = REPORT_LOGIN;
		try {
			final boolean clickedLogin = clickByAnyVisibleText("Sign in with Google", "Iniciar sesión con Google",
					"Continuar con Google", "Google");

			if (!clickedLogin && isMainInterfaceAndSidebarVisible()) {
				captureScreenshot("step1_dashboard_already_loaded");
				recordResult(reportKey, true, "Google login control not shown because session appears already authenticated.");
				return;
			}

			if (!clickedLogin) {
				recordResult(reportKey, false, "Could not find login button with Google text.");
				captureScreenshot("step1_login_button_missing");
				return;
			}

			trySelectGoogleAccount(EMAIL_ACCOUNT);
			waitForUiToLoad();

			final boolean mainInterfaceVisible = isMainInterfaceAndSidebarVisible();

			captureScreenshot("step1_dashboard_loaded");

			recordResult(reportKey, mainInterfaceVisible,
					"Validated main interface and sidebar after login.");
		} catch (final Exception ex) {
			recordResult(reportKey, false, "Login step failed: " + ex.getMessage());
			captureScreenshot("step1_login_failure");
		}
	}

	private void runMiNegocioMenuStep() {
		final String reportKey = REPORT_MI_NEGOCIO_MENU;
		try {
			if (isVisibleText("Negocio", 5)) {
				clickIfVisibleTextPresent("Negocio");
			}

			final boolean clickedMiNegocio = clickByAnyVisibleText("Mi Negocio");
			if (!clickedMiNegocio) {
				recordResult(reportKey, false, "Could not click 'Mi Negocio'.");
				captureScreenshot("step2_mi_negocio_not_found");
				return;
			}

			final boolean submenuExpanded = isVisibleText("Agregar Negocio", 10) && isVisibleText("Administrar Negocios", 10);
			captureScreenshot("step2_mi_negocio_expanded");
			recordResult(reportKey, submenuExpanded, "Validated submenu expansion.");
		} catch (final Exception ex) {
			recordResult(reportKey, false, "Mi Negocio menu step failed: " + ex.getMessage());
			captureScreenshot("step2_mi_negocio_failure");
		}
	}

	private void runAgregarNegocioModalStep() {
		final String reportKey = REPORT_AGREGAR_MODAL;
		try {
			final boolean clickedAgregar = clickByAnyVisibleText("Agregar Negocio");
			if (!clickedAgregar) {
				recordResult(reportKey, false, "Could not click 'Agregar Negocio'.");
				captureScreenshot("step3_agregar_negocio_not_found");
				return;
			}

			waitForUiToLoad();

			final boolean titleVisible = isVisibleText("Crear Nuevo Negocio", 10);
			final Optional<WebElement> modal = findVisibleElement(By.xpath(
					"//*[@role='dialog' or contains(@class,'modal') or contains(@class,'Modal')][.//*[contains(normalize-space(.), "
							+ xpathLiteral("Crear Nuevo Negocio") + ")]]"),
					5);

			final boolean nombreFieldExists = modal.isPresent() ? hasElementInside(modal.get(), By.xpath(
					".//input[contains(@placeholder,'Nombre del Negocio') or @aria-label='Nombre del Negocio' or @name='nombreNegocio' or contains(@id,'nombre')]"))
					: isVisible(By.xpath(
							"//input[contains(@placeholder,'Nombre del Negocio') or @aria-label='Nombre del Negocio' or @name='nombreNegocio' or contains(@id,'nombre')]"),
							5);

			final boolean quotaVisible = isVisibleText("Tienes 2 de 3 negocios", 8);
			final boolean cancelVisible = isVisibleText("Cancelar", 8);
			final boolean createVisible = isVisibleText("Crear Negocio", 8);

			captureScreenshot("step3_crear_negocio_modal");

			if (modal.isPresent()) {
				final Optional<WebElement> nameField = findVisibleElementInside(modal.get(), By.xpath(
						".//input[contains(@placeholder,'Nombre del Negocio') or @aria-label='Nombre del Negocio' or @name='nombreNegocio' or contains(@id,'nombre')]"));
				if (nameField.isPresent()) {
					nameField.get().click();
					nameField.get().sendKeys("Negocio Prueba Automatización");
					waitForUiToLoad();
				}

				clickByTextInside(modal.get(), "Cancelar");
				waitForUiToLoad();
			} else {
				clickIfVisibleTextPresent("Cancelar");
			}

			final boolean stepPass = titleVisible && nombreFieldExists && quotaVisible && cancelVisible && createVisible;
			recordResult(reportKey, stepPass, "Validated 'Crear Nuevo Negocio' modal fields and buttons.");
		} catch (final Exception ex) {
			recordResult(reportKey, false, "Agregar Negocio modal step failed: " + ex.getMessage());
			captureScreenshot("step3_agregar_modal_failure");
		}
	}

	private void runAdministrarNegociosStep() {
		final String reportKey = REPORT_ADMINISTRAR_VIEW;
		try {
			if (!isVisibleText("Administrar Negocios", 3)) {
				clickIfVisibleTextPresent("Mi Negocio");
				waitForUiToLoad();
			}

			final boolean clickedAdministrar = clickByAnyVisibleText("Administrar Negocios");
			if (!clickedAdministrar) {
				recordResult(reportKey, false, "Could not click 'Administrar Negocios'.");
				captureScreenshot("step4_administrar_not_found");
				return;
			}

			waitForUiToLoad();

			final boolean infoGeneral = isVisibleText("Información General", 15);
			final boolean detalles = isVisibleText("Detalles de la Cuenta", 15);
			final boolean negocios = isVisibleText("Tus Negocios", 15);
			final boolean legal = isVisibleText("Sección Legal", 15);

			captureFullPageScreenshot("step4_administrar_negocios");
			recordResult(reportKey, infoGeneral && detalles && negocios && legal,
					"Validated required account sections are visible.");
		} catch (final Exception ex) {
			recordResult(reportKey, false, "Administrar Negocios step failed: " + ex.getMessage());
			captureScreenshot("step4_administrar_failure");
		}
	}

	private void runInformacionGeneralStep() {
		final String reportKey = REPORT_INFO_GENERAL;
		try {
			final Optional<WebElement> section = findSectionContaining("Información General");
			final String sectionText = section.map(WebElement::getText).orElseGet(this::safeBodyText);

			final boolean userNameVisible = isUserNameVisible(section.orElse(null), sectionText);
			final boolean userEmailVisible = isUserEmailVisible(sectionText);
			final boolean businessPlanVisible = isVisibleText("BUSINESS PLAN", 8);
			final boolean cambiarPlanVisible = isVisibleText("Cambiar Plan", 8);

			recordResult(reportKey, userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible,
					"Validated user and plan details under Información General.");
		} catch (final Exception ex) {
			recordResult(reportKey, false, "Información General validation failed: " + ex.getMessage());
			captureScreenshot("step5_info_general_failure");
		}
	}

	private void runDetallesDeLaCuentaStep() {
		final String reportKey = REPORT_DETALLES_CUENTA;
		try {
			final boolean cuentaCreada = isVisibleText("Cuenta creada", 8);
			final boolean estadoActivo = isVisibleText("Estado activo", 8);
			final boolean idiomaSeleccionado = isVisibleText("Idioma seleccionado", 8);
			recordResult(reportKey, cuentaCreada && estadoActivo && idiomaSeleccionado,
					"Validated account detail labels.");
		} catch (final Exception ex) {
			recordResult(reportKey, false, "Detalles de la Cuenta validation failed: " + ex.getMessage());
			captureScreenshot("step6_detalles_failure");
		}
	}

	private void runTusNegociosStep() {
		final String reportKey = REPORT_TUS_NEGOCIOS;
		try {
			final Optional<WebElement> section = findSectionContaining("Tus Negocios");
			final boolean businessListVisible = section.isPresent() && hasBusinessList(section.get());
			final boolean addButtonVisible = isVisibleText("Agregar Negocio", 8);
			final boolean quotaVisible = isVisibleText("Tienes 2 de 3 negocios", 8);
			recordResult(reportKey, businessListVisible && addButtonVisible && quotaVisible,
					"Validated business list, quota and add button.");
		} catch (final Exception ex) {
			recordResult(reportKey, false, "Tus Negocios validation failed: " + ex.getMessage());
			captureScreenshot("step7_tus_negocios_failure");
		}
	}

	private void runLegalStep(final String reportKey, final String linkText, final String headingText,
			final String screenshotName) {
		try {
			final String appWindow = driver.getWindowHandle();
			final String appUrl = safeCurrentUrl();
			final Set<String> handlesBeforeClick = new LinkedHashSet<>(driver.getWindowHandles());

			final boolean clickedLink = clickByAnyVisibleText(linkText);
			if (!clickedLink) {
				recordResult(reportKey, false, "Could not click legal link: " + linkText);
				captureScreenshot(screenshotName + "_link_missing");
				return;
			}

			waitForPotentialNavigation(appUrl, handlesBeforeClick);

			final Set<String> handlesAfterClick = new LinkedHashSet<>(driver.getWindowHandles());
			boolean openedNewTab = handlesAfterClick.size() > handlesBeforeClick.size();
			String legalWindow = appWindow;

			if (openedNewTab) {
				for (final String handle : handlesAfterClick) {
					if (!handlesBeforeClick.contains(handle)) {
						legalWindow = handle;
						break;
					}
				}
				driver.switchTo().window(legalWindow);
				waitForUiToLoad();
			}

			final boolean headingVisible = isVisibleText(headingText, 20);
			final boolean legalContentVisible = isLegalContentVisible();
			final String finalUrl = safeCurrentUrl();
			legalFinalUrls.put(reportKey, finalUrl);

			captureScreenshot(screenshotName);
			recordResult(reportKey, headingVisible && legalContentVisible, "Final URL: " + finalUrl);

			if (openedNewTab) {
				if (!Objects.equals(legalWindow, appWindow)) {
					driver.close();
				}
				driver.switchTo().window(appWindow);
				waitForUiToLoad();
			} else if (!Objects.equals(finalUrl, appUrl)) {
				driver.navigate().back();
				waitForUiToLoad();
			}
		} catch (final Exception ex) {
			recordResult(reportKey, false, "Legal step failed for '" + linkText + "': " + ex.getMessage());
			captureScreenshot(screenshotName + "_failure");
		}
	}

	private void waitForPotentialNavigation(final String appUrl, final Set<String> handlesBeforeClick) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(12)).until(anyOf(
					drv -> drv.getWindowHandles().size() > handlesBeforeClick.size(),
					drv -> !Objects.equals(safeCurrentUrl(), appUrl)));
		} catch (final TimeoutException ignored) {
			// No hard failure here; some environments may navigate slowly or remain in the same URL.
		}
	}

	private boolean clickByAnyVisibleText(final String... texts) {
		for (final String text : texts) {
			final Optional<WebElement> maybeElement = findClickableByText(text, 8);
			if (maybeElement.isPresent()) {
				clickElement(maybeElement.get());
				waitForUiToLoad();
				return true;
			}
		}
		return false;
	}

	private boolean clickIfVisibleTextPresent(final String text) {
		final Optional<WebElement> maybeElement = findClickableByText(text, 2);
		if (maybeElement.isPresent()) {
			clickElement(maybeElement.get());
			waitForUiToLoad();
			return true;
		}
		return false;
	}

	private Optional<WebElement> findClickableByText(final String text, final int timeoutSeconds) {
		final String literal = xpathLiteral(text);
		final String xpath = String.join(" | ",
				"//button[contains(normalize-space(.), " + literal + ")]",
				"//a[contains(normalize-space(.), " + literal + ")]",
				"//*[@role='button' and contains(normalize-space(.), " + literal + ")]",
				"//*[@role='menuitem' and contains(normalize-space(.), " + literal + ")]",
				"//label[contains(normalize-space(.), " + literal + ")]",
				"//span[contains(normalize-space(.), " + literal + ")]/ancestor::button[1]",
				"//span[contains(normalize-space(.), " + literal + ")]/ancestor::a[1]",
				"//*[contains(normalize-space(.), " + literal + ")]");

		return findVisibleElement(By.xpath(xpath), timeoutSeconds);
	}

	private Optional<WebElement> findVisibleElement(final By by, final int timeoutSeconds) {
		try {
			final WebElement element = new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds)).until(drv -> {
				final List<WebElement> candidates = drv.findElements(by);
				for (final WebElement candidate : candidates) {
					if (candidate.isDisplayed()) {
						return candidate;
					}
				}
				return null;
			});
			return Optional.ofNullable(element);
		} catch (final TimeoutException ex) {
			return Optional.empty();
		}
	}

	private Optional<WebElement> findVisibleElementInside(final WebElement root, final By by) {
		final List<WebElement> candidates = root.findElements(by);
		for (final WebElement candidate : candidates) {
			if (candidate.isDisplayed()) {
				return Optional.of(candidate);
			}
		}
		return Optional.empty();
	}

	private boolean hasElementInside(final WebElement root, final By by) {
		return !root.findElements(by).isEmpty();
	}

	private boolean clickByTextInside(final WebElement root, final String text) {
		final String literal = xpathLiteral(text);
		final List<WebElement> candidates = root.findElements(By.xpath(
				".//button[contains(normalize-space(.), " + literal + ")] | .//a[contains(normalize-space(.), " + literal
						+ ")] | .//*[@role='button' and contains(normalize-space(.), " + literal + ")]"));
		for (final WebElement candidate : candidates) {
			if (candidate.isDisplayed()) {
				clickElement(candidate);
				return true;
			}
		}
		return false;
	}

	private void clickElement(final WebElement element) {
		try {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
		} catch (final Exception ignored) {
			// Fallback to a direct click below if JS scroll fails.
		}

		try {
			element.click();
		} catch (final Exception clickFailure) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	private boolean isVisibleText(final String text, final int timeoutSeconds) {
		final String literal = xpathLiteral(text);
		return isVisible(By.xpath("//*[contains(normalize-space(.), " + literal + ")]"), timeoutSeconds);
	}

	private boolean isVisible(final By by, final int timeoutSeconds) {
		return findVisibleElement(by, timeoutSeconds).isPresent();
	}

	private boolean isMainInterfaceAndSidebarVisible() {
		final boolean mainInterfaceVisible = isVisible(
				By.xpath("//aside | //nav | //main//*[contains(normalize-space(.), 'Mi Negocio')]"), 20);
		final boolean leftSidebarVisible = isVisible(By.xpath(
				"//aside//*[contains(normalize-space(.), 'Negocio')] | //nav//*[contains(normalize-space(.), 'Negocio')]"),
				15) || isVisibleText("Negocio", 15);
		return mainInterfaceVisible && leftSidebarVisible;
	}

	private void trySelectGoogleAccount(final String email) {
		try {
			final String originWindow = driver.getWindowHandle();
			final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());

			waitForPotentialNavigation(safeCurrentUrl(), handlesBefore);
			final Set<String> handlesAfter = new LinkedHashSet<>(driver.getWindowHandles());
			String activeWindow = originWindow;

			if (handlesAfter.size() > handlesBefore.size()) {
				for (final String handle : handlesAfter) {
					if (!handlesBefore.contains(handle)) {
						activeWindow = handle;
						break;
					}
				}
				driver.switchTo().window(activeWindow);
				waitForUiToLoad();
			}

			clickIfVisibleTextPresent(email);

			if (!Objects.equals(activeWindow, originWindow)) {
				driver.switchTo().window(originWindow);
				waitForUiToLoad();
			}
		} catch (final Exception ex) {
			addNote(REPORT_LOGIN, "Google account selector was not explicitly handled: " + ex.getMessage());
		}
	}

	private Optional<WebElement> findSectionContaining(final String headingText) {
		final String literal = xpathLiteral(headingText);
		final String xpath = "//section[.//*[contains(normalize-space(.), " + literal + ")]] | "
				+ "//div[contains(@class,'card') and .//*[contains(normalize-space(.), " + literal + ")]] | "
				+ "//div[.//*[self::h1 or self::h2 or self::h3 or self::h4][contains(normalize-space(.), " + literal + ")]]";
		return findVisibleElement(By.xpath(xpath), 8);
	}

	private boolean isUserNameVisible(final WebElement section, final String sectionText) {
		final String expectedName = readConfig(EXPECTED_USER_NAME_PROPERTY);
		if (!isBlank(expectedName) && isVisibleText(expectedName.trim(), 3)) {
			return true;
		}

		if (section != null && hasExplicitNameValue(section)) {
			return true;
		}

		return inferNameFromText(sectionText);
	}

	private boolean hasExplicitNameValue(final WebElement section) {
		final List<WebElement> labeledElements = section.findElements(
				By.xpath(".//*[contains(translate(normalize-space(.), 'NOMBRE', 'nombre'), 'nombre')]"));
		for (final WebElement element : labeledElements) {
			final String text = normalizeSpaces(element.getText());
			if (text.matches("(?i).*nombre\\s*[:\\-]\\s*.+")) {
				return true;
			}

			final List<WebElement> siblings = element.findElements(By.xpath("following-sibling::*[normalize-space(.)!='']"));
			if (!siblings.isEmpty() && !isBlank(siblings.get(0).getText())) {
				return true;
			}
		}
		return false;
	}

	private boolean inferNameFromText(final String text) {
		final List<String> lines = Arrays.asList(text.split("\\R"));
		for (final String rawLine : lines) {
			final String line = normalizeSpaces(rawLine);
			if (isBlank(line)) {
				continue;
			}

			final String upper = line.toUpperCase(Locale.ROOT);
			if (upper.contains("INFORMACIÓN GENERAL") || upper.contains("BUSINESS PLAN") || upper.contains("CAMBIAR PLAN")
					|| upper.contains("DETALLES DE LA CUENTA") || upper.contains("CUENTA CREADA")
					|| upper.contains("ESTADO ACTIVO") || upper.contains("IDIOMA SELECCIONADO")
					|| upper.contains("SECCIÓN LEGAL") || upper.contains("TÉRMINOS Y CONDICIONES")
					|| upper.contains("POLÍTICA DE PRIVACIDAD") || upper.contains("NOMBRE")
					|| upper.contains("CORREO") || upper.contains("EMAIL") || upper.contains("@")) {
				continue;
			}

			if (line.matches("^[\\p{L}][\\p{L} .'-]{2,}$") && line.split("\\s+").length >= 2) {
				return true;
			}
		}
		return false;
	}

	private boolean isUserEmailVisible(final String sectionText) {
		final String configuredEmail = readConfig(EXPECTED_USER_EMAIL_PROPERTY);
		if (!isBlank(configuredEmail) && sectionText.contains(configuredEmail.trim())) {
			return true;
		}

		final Matcher matcher = EMAIL_PATTERN.matcher(sectionText);
		return matcher.find();
	}

	private boolean hasBusinessList(final WebElement section) {
		final List<WebElement> listItems = section.findElements(By.xpath(
				".//li | .//tr | .//*[@role='listitem'] | .//*[@data-testid='business-item'] | .//*[contains(@class,'business-item')]"));
		if (!listItems.isEmpty()) {
			for (final WebElement item : listItems) {
				if (item.isDisplayed() && !isBlank(item.getText())) {
					return true;
				}
			}
		}

		final List<String> lines = Arrays.asList(section.getText().split("\\R"));
		int candidateBusinessRows = 0;
		for (final String rawLine : lines) {
			final String line = normalizeSpaces(rawLine);
			if (isBlank(line)) {
				continue;
			}
			final String upper = line.toUpperCase(Locale.ROOT);
			if (upper.contains("TUS NEGOCIOS") || upper.contains("AGREGAR NEGOCIO") || upper.contains("TIENES 2 DE 3 NEGOCIOS")
					|| upper.contains("SECCIÓN LEGAL")) {
				continue;
			}
			candidateBusinessRows++;
		}
		return candidateBusinessRows > 0;
	}

	private boolean isLegalContentVisible() {
		final List<WebElement> longParagraphs = driver.findElements(By.xpath(
				"//main//*[self::p or self::li][string-length(normalize-space(.)) > 60] | "
						+ "//article//*[self::p or self::li][string-length(normalize-space(.)) > 60] | "
						+ "//body//*[self::p or self::li][string-length(normalize-space(.)) > 60]"));
		if (!longParagraphs.isEmpty()) {
			return true;
		}

		final String body = safeBodyText();
		return !isBlank(body) && body.trim().length() > 250;
	}

	private void waitForUiToLoad() {
		try {
			wait.until((ExpectedCondition<Boolean>) drv -> {
				final Object state = ((JavascriptExecutor) drv).executeScript("return document.readyState");
				return "complete".equals(String.valueOf(state));
			});
		} catch (final Exception ignored) {
			// Single page apps may not transition document.readyState between interactions.
		}

		try {
			Thread.sleep(450);
		} catch (final InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	private void recordResult(final String reportKey, final boolean pass, final String note) {
		finalReport.put(reportKey, pass);
		addNote(reportKey, note);
	}

	private void addNote(final String reportKey, final String note) {
		if (isBlank(note)) {
			return;
		}
		reportNotes.computeIfAbsent(reportKey, ignored -> new ArrayList<>()).add(note);
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder report = new StringBuilder();
		report.append("SaleADS Mi Negocio Full Workflow Report").append(System.lineSeparator());
		report.append("Generated at: ").append(LocalDateTime.now()).append(System.lineSeparator());
		report.append("Artifacts directory: ").append(artifactsDir.toAbsolutePath()).append(System.lineSeparator());
		report.append(System.lineSeparator());
		report.append("Final status by required field:").append(System.lineSeparator());

		for (final String field : FINAL_REPORT_FIELDS) {
			final boolean pass = Boolean.TRUE.equals(finalReport.get(field));
			report.append("- ").append(field).append(": ").append(pass ? "PASS" : "FAIL").append(System.lineSeparator());

			final List<String> notes = reportNotes.get(field);
			if (notes != null) {
				for (final String note : notes) {
					report.append("  - ").append(note).append(System.lineSeparator());
				}
			}
		}

		if (!legalFinalUrls.isEmpty()) {
			report.append(System.lineSeparator()).append("Captured legal URLs:").append(System.lineSeparator());
			for (final Map.Entry<String, String> entry : legalFinalUrls.entrySet()) {
				report.append("- ").append(entry.getKey()).append(": ").append(entry.getValue()).append(System.lineSeparator());
			}
		}

		Files.writeString(reportPath, report.toString(), StandardCharsets.UTF_8);
		System.out.println(report);
	}

	private void captureScreenshot(final String checkpointName) {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}

		final String timestamp = LocalDateTime.now().format(FILE_DATE_TIME);
		final String fileName = timestamp + "_" + sanitizeFileComponent(checkpointName) + ".png";
		final Path screenshotPath = screenshotsDir.resolve(fileName);

		try {
			final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
			Files.copy(screenshot.toPath(), screenshotPath);
		} catch (final Exception ex) {
			addNote("Screenshots", "Failed to capture screenshot '" + checkpointName + "': " + ex.getMessage());
		}
	}

	private void captureFullPageScreenshot(final String checkpointName) {
		final String timestamp = LocalDateTime.now().format(FILE_DATE_TIME);
		final String fileName = timestamp + "_" + sanitizeFileComponent(checkpointName) + "_full.png";
		final Path screenshotPath = screenshotsDir.resolve(fileName);

		if (driver instanceof ChromeDriver) {
			try {
				final ChromeDriver chrome = (ChromeDriver) driver;
				final Map<String, Object> cdpResult = chrome.executeCdpCommand("Page.captureScreenshot",
						Map.of("format", "png", "fromSurface", true, "captureBeyondViewport", true));
				final String encoded = String.valueOf(cdpResult.get("data"));
				Files.write(screenshotPath, Base64.getDecoder().decode(encoded));
				return;
			} catch (final Exception ignored) {
				// Fall back to viewport screenshot if CDP full-page capture is unavailable.
			}
		}

		captureScreenshot(checkpointName);
	}

	private String readConfig(final String key) {
		final String systemValue = System.getProperty(key);
		if (!isBlank(systemValue)) {
			return systemValue;
		}

		final String envKey = key.toUpperCase(Locale.ROOT).replace('.', '_');
		return System.getenv(envKey);
	}

	private String readConfig(final String key, final String defaultValue) {
		final String value = readConfig(key);
		return isBlank(value) ? defaultValue : value;
	}

	private String safeCurrentUrl() {
		try {
			return driver.getCurrentUrl();
		} catch (final Exception ex) {
			return "";
		}
	}

	private String safeBodyText() {
		try {
			return driver.findElement(By.tagName("body")).getText();
		} catch (final Exception ex) {
			return "";
		}
	}

	private String sanitizeFileComponent(final String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
	}

	private String normalizeSpaces(final String value) {
		if (value == null) {
			return "";
		}
		return value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
	}

	private boolean isBlank(final String value) {
		return value == null || value.trim().isEmpty();
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		final StringBuilder concat = new StringBuilder("concat(");
		final char[] chars = value.toCharArray();
		for (int i = 0; i < chars.length; i++) {
			final String piece = String.valueOf(chars[i]);
			if ("'".equals(piece)) {
				concat.append("\"'\"");
			} else if ("\"".equals(piece)) {
				concat.append("'\"'");
			} else {
				concat.append("'").append(piece).append("'");
			}
			if (i + 1 < chars.length) {
				concat.append(",");
			}
		}
		concat.append(")");
		return concat.toString();
	}

	@SafeVarargs
	private final ExpectedCondition<Boolean> anyOf(final ExpectedCondition<Boolean>... conditions) {
		return drv -> {
			for (final ExpectedCondition<Boolean> condition : conditions) {
				try {
					if (Boolean.TRUE.equals(condition.apply(drv))) {
						return true;
					}
				} catch (final Exception ignored) {
					// Keep evaluating fallback conditions.
				}
			}
			return false;
		};
	}
}
