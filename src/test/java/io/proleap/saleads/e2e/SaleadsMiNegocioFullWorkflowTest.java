package io.proleap.saleads.e2e;

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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullWorkflowTest {

	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("\\b[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[A-Za-z]{2,}\\b");

	private static final String STEP_LOGIN = "Login";
	private static final String STEP_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String STEP_AGREGAR_MODAL = "Agregar Negocio modal";
	private static final String STEP_ADMINISTRAR = "Administrar Negocios view";
	private static final String STEP_INFO_GENERAL = "Información General";
	private static final String STEP_DETALLES = "Detalles de la Cuenta";
	private static final String STEP_TUS_NEGOCIOS = "Tus Negocios";
	private static final String STEP_TERMINOS = "Términos y Condiciones";
	private static final String STEP_PRIVACIDAD = "Política de Privacidad";

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	private final Map<String, Boolean> stepStatus = new LinkedHashMap<>();
	private final Map<String, String> stepDetails = new LinkedHashMap<>();

	@Before
	public void setUp() throws IOException {
		final String loginUrl = System.getenv("SALEADS_LOGIN_URL");
		final String debuggerAddress = System.getenv("SALEADS_CHROME_DEBUGGER_ADDRESS");
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL or SALEADS_CHROME_DEBUGGER_ADDRESS to run this test in the target environment.",
				hasText(loginUrl) || hasText(debuggerAddress));

		final ChromeOptions options = new ChromeOptions();
		if (!hasText(debuggerAddress)) {
			options.addArguments("--window-size=1920,1080");
			options.addArguments("--disable-dev-shm-usage");
			options.addArguments("--no-sandbox");
			if (envFlag("SALEADS_HEADLESS", true)) {
				options.addArguments("--headless=new");
			}
		} else {
			options.setExperimentalOption("debuggerAddress", debuggerAddress);
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(envInt("SALEADS_TIMEOUT_SECONDS", 25)));

		if (hasText(loginUrl)) {
			driver.get(loginUrl);
		}

		waitForUiToLoad();
		evidenceDir = Path.of("target", "saleads-evidence",
				new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date()));
		Files.createDirectories(evidenceDir);
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		runStepLogin();
		runStepMiNegocioMenu();
		runStepAgregarNegocioModal();
		runStepAdministrarNegocios();
		runStepInformacionGeneral();
		runStepDetallesCuenta();
		runStepTusNegocios();
		runStepTerminos();
		runStepPrivacidad();

		final String report = writeFinalReport();
		Assert.assertTrue(report, allStepsPass());
	}

	private void runStepLogin() throws IOException {
		boolean ok = true;
		final List<String> notes = new ArrayList<>();

		ok &= clickAnyText("Sign in with Google", "Iniciar sesión con Google", "Inicia sesión con Google",
				"Continuar con Google", "Google");
		notes.add("Google login button clicked.");

		if (isAnyTextVisible("juanlucasbarbiergarzon@gmail.com")) {
			ok &= clickAnyText("juanlucasbarbiergarzon@gmail.com");
			notes.add("Google account selected.");
		} else {
			notes.add("Google account selector not shown.");
		}

		waitForUiToLoad();
		final boolean mainInterfaceVisible = isAnyTextVisible("Negocio", "Mi Negocio", "Dashboard", "Inicio");
		final boolean sidebarVisible = isSidebarVisible();
		ok &= mainInterfaceVisible;
		ok &= sidebarVisible;

		takeScreenshot("01-dashboard-loaded");
		notes.add("Main interface visible: " + mainInterfaceVisible);
		notes.add("Sidebar visible: " + sidebarVisible);
		recordStep(STEP_LOGIN, ok, String.join(" ", notes));
	}

	private void runStepMiNegocioMenu() throws IOException {
		boolean ok = true;
		final List<String> notes = new ArrayList<>();

		ok &= clickAnyText("Negocio", "Mi Negocio");
		waitForUiToLoad();

		final boolean submenuExpanded = isAnyTextVisible("Agregar Negocio", "Administrar Negocios");
		final boolean agregarVisible = isAnyTextVisible("Agregar Negocio");
		final boolean administrarVisible = isAnyTextVisible("Administrar Negocios");
		ok &= submenuExpanded;
		ok &= agregarVisible;
		ok &= administrarVisible;

		takeScreenshot("02-mi-negocio-menu-expanded");
		notes.add("Submenu expanded: " + submenuExpanded);
		notes.add("Agregar Negocio visible: " + agregarVisible);
		notes.add("Administrar Negocios visible: " + administrarVisible);
		recordStep(STEP_MI_NEGOCIO_MENU, ok, String.join(" ", notes));
	}

	private void runStepAgregarNegocioModal() throws IOException {
		boolean ok = true;
		final List<String> notes = new ArrayList<>();

		ok &= clickAnyText("Agregar Negocio");
		waitForUiToLoad();

		final boolean modalTitleVisible = isAnyTextVisible("Crear Nuevo Negocio");
		final boolean inputVisible = isInputForNombreDelNegocioVisible();
		final boolean limitVisible = isAnyTextVisible("Tienes 2 de 3 negocios");
		final boolean cancelVisible = isAnyTextVisible("Cancelar");
		final boolean crearVisible = isAnyTextVisible("Crear Negocio");
		ok &= modalTitleVisible;
		ok &= inputVisible;
		ok &= limitVisible;
		ok &= cancelVisible;
		ok &= crearVisible;

		final WebElement nameInput = findNombreNegocioInput();
		if (nameInput != null) {
			nameInput.clear();
			nameInput.sendKeys("Negocio Prueba Automatización");
			waitForUiToLoad();
			notes.add("Optional input completed.");
		}
		clickAnyText("Cancelar");
		waitForUiToLoad();

		takeScreenshot("03-agregar-negocio-modal");
		notes.add("Modal title visible: " + modalTitleVisible);
		notes.add("Nombre del Negocio input visible: " + inputVisible);
		notes.add("Limit text visible: " + limitVisible);
		notes.add("Cancelar visible: " + cancelVisible);
		notes.add("Crear Negocio visible: " + crearVisible);
		recordStep(STEP_AGREGAR_MODAL, ok, String.join(" ", notes));
	}

	private void runStepAdministrarNegocios() throws IOException {
		boolean ok = true;
		final List<String> notes = new ArrayList<>();

		if (!isAnyTextVisible("Administrar Negocios")) {
			clickAnyText("Mi Negocio", "Negocio");
			waitForUiToLoad();
		}
		ok &= clickAnyText("Administrar Negocios");
		waitForUiToLoad();

		final boolean infoGeneral = isAnyTextVisible("Información General");
		final boolean detallesCuenta = isAnyTextVisible("Detalles de la Cuenta");
		final boolean tusNegocios = isAnyTextVisible("Tus Negocios");
		final boolean legal = isAnyTextVisible("Sección Legal", "Seccion Legal");
		ok &= infoGeneral;
		ok &= detallesCuenta;
		ok &= tusNegocios;
		ok &= legal;

		takeScreenshot("04-administrar-negocios-view");
		notes.add("Información General visible: " + infoGeneral);
		notes.add("Detalles de la Cuenta visible: " + detallesCuenta);
		notes.add("Tus Negocios visible: " + tusNegocios);
		notes.add("Sección Legal visible: " + legal);
		recordStep(STEP_ADMINISTRAR, ok, String.join(" ", notes));
	}

	private void runStepInformacionGeneral() {
		boolean ok = true;
		final List<String> notes = new ArrayList<>();

		final String expectedEmail = firstNonBlank(System.getenv("SALEADS_EXPECTED_USER_EMAIL"),
				"juanlucasbarbiergarzon@gmail.com");
		final String expectedName = System.getenv("SALEADS_EXPECTED_USER_NAME");
		final String bodyText = readBodyText();

		final boolean userNameVisible = hasText(expectedName) ? isAnyTextVisible(expectedName)
				: isAnyTextVisible("Nombre", "Usuario", "Perfil");
		final boolean userEmailVisible = hasText(expectedEmail) ? isAnyTextVisible(expectedEmail)
				: EMAIL_PATTERN.matcher(bodyText).find();
		final boolean businessPlanVisible = isAnyTextVisible("BUSINESS PLAN");
		final boolean cambiarPlanVisible = isAnyTextVisible("Cambiar Plan");
		ok &= userNameVisible;
		ok &= userEmailVisible;
		ok &= businessPlanVisible;
		ok &= cambiarPlanVisible;

		notes.add("User name visible: " + userNameVisible);
		notes.add("User email visible: " + userEmailVisible);
		notes.add("BUSINESS PLAN visible: " + businessPlanVisible);
		notes.add("Cambiar Plan visible: " + cambiarPlanVisible);
		recordStep(STEP_INFO_GENERAL, ok, String.join(" ", notes));
	}

	private void runStepDetallesCuenta() {
		boolean ok = true;
		final List<String> notes = new ArrayList<>();

		final boolean cuentaCreadaVisible = isAnyTextVisible("Cuenta creada");
		final boolean estadoActivoVisible = isAnyTextVisible("Estado activo");
		final boolean idiomaVisible = isAnyTextVisible("Idioma seleccionado");
		ok &= cuentaCreadaVisible;
		ok &= estadoActivoVisible;
		ok &= idiomaVisible;

		notes.add("Cuenta creada visible: " + cuentaCreadaVisible);
		notes.add("Estado activo visible: " + estadoActivoVisible);
		notes.add("Idioma seleccionado visible: " + idiomaVisible);
		recordStep(STEP_DETALLES, ok, String.join(" ", notes));
	}

	private void runStepTusNegocios() {
		boolean ok = true;
		final List<String> notes = new ArrayList<>();

		final boolean titleVisible = isAnyTextVisible("Tus Negocios");
		final boolean agregarVisible = isAnyTextVisible("Agregar Negocio");
		final boolean limitVisible = isAnyTextVisible("Tienes 2 de 3 negocios");
		ok &= titleVisible;
		ok &= agregarVisible;
		ok &= limitVisible;

		notes.add("Business list title visible: " + titleVisible);
		notes.add("Agregar Negocio button visible: " + agregarVisible);
		notes.add("Limit text visible: " + limitVisible);
		recordStep(STEP_TUS_NEGOCIOS, ok, String.join(" ", notes));
	}

	private void runStepTerminos() throws IOException {
		final LegalNavigationResult result = openAndValidateLegal("Términos y Condiciones", "Terminos y Condiciones",
				"términos y condiciones", "terminos y condiciones", "08-terminos-y-condiciones");

		recordStep(STEP_TERMINOS, result.ok,
				"Heading visible: " + result.headingVisible + " Content visible: " + result.contentVisible
						+ " Final URL: " + result.finalUrl);
	}

	private void runStepPrivacidad() throws IOException {
		final LegalNavigationResult result = openAndValidateLegal("Política de Privacidad", "Politica de Privacidad",
				"política de privacidad", "politica de privacidad", "09-politica-de-privacidad");

		recordStep(STEP_PRIVACIDAD, result.ok,
				"Heading visible: " + result.headingVisible + " Content visible: " + result.contentVisible
						+ " Final URL: " + result.finalUrl);
	}

	private LegalNavigationResult openAndValidateLegal(final String linkTextPrimary, final String linkTextFallback,
			final String headingPrimaryLower, final String headingFallbackLower, final String screenshotName)
			throws IOException {
		boolean ok = true;
		final String originalHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();
		final String urlBefore = safeCurrentUrl();

		ok &= clickAnyText(linkTextPrimary, linkTextFallback);

		waitForNewTabOrNavigation(handlesBefore, urlBefore);
		String currentHandle = driver.getWindowHandle();
		for (final String handle : driver.getWindowHandles()) {
			if (!handlesBefore.contains(handle)) {
				driver.switchTo().window(handle);
				currentHandle = handle;
				break;
			}
		}
		waitForUiToLoad();

		final String bodyLower = readBodyText().toLowerCase(Locale.ROOT);
		final boolean headingVisible = bodyLower.contains(headingPrimaryLower) || bodyLower.contains(headingFallbackLower);
		final boolean contentVisible = bodyLower.length() > 200;
		ok &= headingVisible;
		ok &= contentVisible;

		takeScreenshot(screenshotName);
		final String finalUrl = safeCurrentUrl();

		if (!currentHandle.equals(originalHandle)) {
			driver.close();
			driver.switchTo().window(originalHandle);
		} else {
			driver.navigate().back();
		}
		waitForUiToLoad();

		return new LegalNavigationResult(ok, headingVisible, contentVisible, finalUrl);
	}

	private void waitForNewTabOrNavigation(final Set<String> handlesBefore, final String urlBefore) {
		try {
			wait.until((ExpectedCondition<Boolean>) wd -> {
				final boolean newTab = wd.getWindowHandles().size() > handlesBefore.size();
				final boolean urlChanged = !safeCurrentUrl().equals(urlBefore);
				return newTab || urlChanged;
			});
		} catch (final TimeoutException ignored) {
		}
	}

	private boolean isInputForNombreDelNegocioVisible() {
		return findNombreNegocioInput() != null;
	}

	private WebElement findNombreNegocioInput() {
		final List<By> selectors = List.of(
				By.xpath("//label[normalize-space()='Nombre del Negocio']/following::input[1]"),
				By.xpath("//input[@placeholder='Nombre del Negocio' or contains(@placeholder,'Nombre')]"),
				By.xpath("//input[@name='businessName' or @id='businessName']"));
		for (final By selector : selectors) {
			final List<WebElement> found = driver.findElements(selector);
			for (final WebElement element : found) {
				if (element.isDisplayed()) {
					return element;
				}
			}
		}
		return null;
	}

	private boolean clickAnyText(final String... texts) {
		for (final String text : texts) {
			final WebElement element = findDisplayedElementByText(text, true);
			if (element != null) {
				clickElement(element);
				return true;
			}
		}
		return false;
	}

	private WebElement findDisplayedElementByText(final String text, final boolean clickableOnly) {
		final String exactXpath = "//*[normalize-space(.)=" + xpathLiteral(text) + "]";
		final String containsXpath = "//*[contains(normalize-space(.)," + xpathLiteral(text) + ")]";
		final List<WebElement> candidates = new ArrayList<>();
		candidates.addAll(driver.findElements(By.xpath(exactXpath)));
		candidates.addAll(driver.findElements(By.xpath(containsXpath)));

		for (final WebElement element : candidates) {
			if (!element.isDisplayed()) {
				continue;
			}
			if (!clickableOnly || element.isEnabled()) {
				return element;
			}
		}
		return null;
	}

	private boolean isAnyTextVisible(final String... texts) {
		for (final String text : texts) {
			if (findDisplayedElementByText(text, false) != null) {
				return true;
			}
		}
		return false;
	}

	private boolean isSidebarVisible() {
		final List<By> selectors = List.of(
				By.xpath("//aside"),
				By.xpath("//nav"),
				By.xpath("//*[contains(@class,'sidebar')]"),
				By.xpath("//*[contains(@class,'SideBar')]"));
		for (final By selector : selectors) {
			for (final WebElement element : driver.findElements(selector)) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		}
		return false;
	}

	private void clickElement(final WebElement element) {
		try {
			((JavascriptExecutor) driver).executeScript(
					"arguments[0].scrollIntoView({block:'center', inline:'nearest'});", element);
		} catch (final Exception ignored) {
		}

		try {
			element.click();
		} catch (final Exception e) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private void waitForUiToLoad() {
		if (driver == null) {
			return;
		}

		try {
			wait.until((ExpectedCondition<Boolean>) wd -> {
				final Object state = ((JavascriptExecutor) wd).executeScript("return document.readyState");
				return "complete".equals(state);
			});
		} catch (final Exception ignored) {
		}

		try {
			Thread.sleep(700);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void takeScreenshot(final String name) throws IOException {
		if (!(driver instanceof TakesScreenshot)) {
			return;
		}
		final Path screenshot = evidenceDir.resolve(name + ".png");
		final java.io.File source = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(source.toPath(), screenshot, StandardCopyOption.REPLACE_EXISTING);
	}

	private void recordStep(final String stepName, final boolean ok, final String detail) {
		stepStatus.put(stepName, ok);
		stepDetails.put(stepName, detail);
	}

	private boolean allStepsPass() {
		for (final Boolean value : stepStatus.values()) {
			if (!Boolean.TRUE.equals(value)) {
				return false;
			}
		}
		return true;
	}

	private String writeFinalReport() throws IOException {
		final StringBuilder builder = new StringBuilder();
		builder.append("SaleADS Mi Negocio Full Workflow Report").append(System.lineSeparator());
		builder.append("Evidence directory: ").append(evidenceDir.toAbsolutePath()).append(System.lineSeparator());
		builder.append(System.lineSeparator());

		final List<String> orderedSteps = List.of(STEP_LOGIN, STEP_MI_NEGOCIO_MENU, STEP_AGREGAR_MODAL, STEP_ADMINISTRAR,
				STEP_INFO_GENERAL, STEP_DETALLES, STEP_TUS_NEGOCIOS, STEP_TERMINOS, STEP_PRIVACIDAD);

		for (final String step : orderedSteps) {
			final boolean ok = Boolean.TRUE.equals(stepStatus.get(step));
			builder.append(step).append(": ").append(ok ? "PASS" : "FAIL").append(System.lineSeparator());
			builder.append("  ").append(firstNonBlank(stepDetails.get(step), "No details available."))
					.append(System.lineSeparator());
		}

		final String report = builder.toString();
		Files.writeString(evidenceDir.resolve("final-report.txt"), report);
		return report;
	}

	private String readBodyText() {
		try {
			final WebElement body = driver.findElement(By.tagName("body"));
			return firstNonBlank(body.getText(), "");
		} catch (final Exception e) {
			return "";
		}
	}

	private String safeCurrentUrl() {
		try {
			return firstNonBlank(driver.getCurrentUrl(), "");
		} catch (final Exception e) {
			return "";
		}
	}

	private boolean hasText(final String value) {
		return value != null && !value.trim().isEmpty();
	}

	private boolean envFlag(final String key, final boolean defaultValue) {
		final String value = System.getenv(key);
		if (!hasText(value)) {
			return defaultValue;
		}
		return "1".equals(value.trim()) || "true".equalsIgnoreCase(value.trim())
				|| "yes".equalsIgnoreCase(value.trim());
	}

	private int envInt(final String key, final int defaultValue) {
		final String value = System.getenv(key);
		if (!hasText(value)) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (final NumberFormatException e) {
			return defaultValue;
		}
	}

	private String firstNonBlank(final String value, final String fallback) {
		if (hasText(value)) {
			return value.trim();
		}
		return fallback;
	}

	private String xpathLiteral(final String text) {
		if (!text.contains("'")) {
			return "'" + text + "'";
		}

		final String[] parts = text.split("'");
		final StringBuilder literal = new StringBuilder("concat(");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				literal.append(", \"'\", ");
			}
			literal.append("'").append(parts[i]).append("'");
		}
		literal.append(")");
		return literal.toString();
	}

	private static class LegalNavigationResult {
		private final boolean ok;
		private final boolean headingVisible;
		private final boolean contentVisible;
		private final String finalUrl;

		private LegalNavigationResult(final boolean ok, final boolean headingVisible, final boolean contentVisible,
				final String finalUrl) {
			this.ok = ok;
			this.headingVisible = headingVisible;
			this.contentVisible = contentVisible;
			this.finalUrl = finalUrl;
		}
	}
}
