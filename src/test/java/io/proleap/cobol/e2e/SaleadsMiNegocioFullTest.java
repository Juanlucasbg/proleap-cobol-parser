package io.proleap.cobol.e2e;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SaleadsMiNegocioFullTest {

	private static final String ENABLE_FLAG = "SALEADS_E2E_ENABLED";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

	private final Map<String, Boolean> finalReport = new LinkedHashMap<>();
	private final Map<String, String> finalReportDetails = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;
	private String termsFinalUrl = "N/A";
	private String privacyFinalUrl = "N/A";

	@Before
	public void setUp() throws IOException {
		final boolean enabled = Boolean.parseBoolean(getEnvOrDefault(ENABLE_FLAG, "false"));
		Assume.assumeTrue("Set " + ENABLE_FLAG + "=true to run this browser E2E workflow.", enabled);

		final ChromeOptions options = new ChromeOptions();
		options.addArguments("--window-size=1600,1200");
		options.addArguments("--disable-notifications");
		options.addArguments("--disable-popup-blocking");

		if (Boolean.parseBoolean(getEnvOrDefault("SALEADS_HEADLESS", "false"))) {
			options.addArguments("--headless=new");
		}

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(30));

		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
		evidenceDir = Paths.get("target", "saleads-e2e", timestamp);
		Files.createDirectories(evidenceDir);

		final String baseUrl = firstNonBlankEnv("SALEADS_BASE_URL", "SALEADS_URL", "BASE_URL");
		if (baseUrl != null) {
			driver.get(baseUrl);
			waitForUiToLoad();
		}
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() throws Exception {
		runAndRecord("Login", this::stepLoginWithGoogle);
		runAndRecord("Mi Negocio menu", this::stepOpenMiNegocioMenu);
		runAndRecord("Agregar Negocio modal", this::stepValidateAgregarNegocioModal);
		runAndRecord("Administrar Negocios view", this::stepOpenAdministrarNegocios);
		runAndRecord("Información General", this::stepValidateInformacionGeneral);
		runAndRecord("Detalles de la Cuenta", this::stepValidateDetallesCuenta);
		runAndRecord("Tus Negocios", this::stepValidateTusNegocios);
		runAndRecord("Términos y Condiciones", this::stepValidateTerminosYCondiciones);
		runAndRecord("Política de Privacidad", this::stepValidatePoliticaDePrivacidad);

		writeFinalReport();

		final List<String> failures = new ArrayList<>();
		for (Map.Entry<String, Boolean> reportEntry : finalReport.entrySet()) {
			if (!reportEntry.getValue()) {
				failures.add(reportEntry.getKey() + " -> " + finalReportDetails.getOrDefault(reportEntry.getKey(), "No detail"));
			}
		}

		assertTrue("SaleADS Mi Negocio workflow has failures: " + failures, failures.isEmpty());
	}

	private void stepLoginWithGoogle() throws Exception {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> beforeClickHandles = driver.getWindowHandles();

		clickByVisibleText("Sign in with Google", "Iniciar sesion con Google", "Iniciar sesión con Google",
				"Continuar con Google", "Acceder con Google");
		handleGoogleAccountChooser(beforeClickHandles, originalHandle);

		waitForAnyVisibleText("Negocio", "Mi Negocio", "Dashboard", "Panel");
		assertTrue("Left sidebar navigation is not visible.",
				isElementVisible(By.xpath("//aside | //nav[.//*[contains(normalize-space(), 'Negocio')]]")));

		takeScreenshot("01_dashboard_loaded");
	}

	private void stepOpenMiNegocioMenu() throws Exception {
		waitForAnyVisibleText("Negocio", "Mi Negocio");

		if (isAnyTextVisible("Negocio")) {
			clickByVisibleText("Negocio");
		}

		clickByVisibleText("Mi Negocio");
		waitForAnyVisibleText("Agregar Negocio", "Administrar Negocios");
		assertTextVisible("Agregar Negocio");
		assertTextVisible("Administrar Negocios");

		takeScreenshot("02_mi_negocio_menu_expanded");
	}

	private void stepValidateAgregarNegocioModal() throws Exception {
		clickByVisibleText("Agregar Negocio");
		waitForAnyVisibleText("Crear Nuevo Negocio");

		assertTextVisible("Crear Nuevo Negocio");
		assertAnyElementVisible(By.xpath("//input[@placeholder='Nombre del Negocio']"),
				By.xpath("//input[@name='nombreNegocio']"), By.xpath("//input[@type='text']"));
		assertTextVisible("Tienes 2 de 3 negocios");
		assertTextVisible("Cancelar");
		assertTextVisible("Crear Negocio");

		takeScreenshot("03_agregar_negocio_modal");

		try {
			WebElement nombreInput = firstVisibleElement(By.xpath("//input[@placeholder='Nombre del Negocio']"),
					By.xpath("//input[@name='nombreNegocio']"), By.xpath("//input[@type='text']"));
			nombreInput.click();
			nombreInput.clear();
			nombreInput.sendKeys("Negocio Prueba Automatizacion");
			clickByVisibleText("Cancelar");
		} catch (RuntimeException ignored) {
			// Optional action. Modal validation above already guarantees core requirements.
		} finally {
			if (isAnyTextVisible("Crear Nuevo Negocio")) {
				try {
					clickByVisibleText("Cancelar");
				} catch (RuntimeException ignored) {
					// Keep workflow moving even if the modal was auto-closed.
				}
			}
		}
	}

	private void stepOpenAdministrarNegocios() throws Exception {
		if (!isAnyTextVisible("Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
		}

		clickByVisibleText("Administrar Negocios");
		waitForAnyVisibleText("Informacion General", "Información General");

		assertAnyTextVisible("Informacion General", "Información General");
		assertAnyTextVisible("Detalles de la Cuenta");
		assertAnyTextVisible("Tus Negocios");
		assertAnyTextVisible("Seccion Legal", "Sección Legal");

		takeScreenshot("04_administrar_negocios_page");
	}

	private void stepValidateInformacionGeneral() {
		assertAnyTextVisible("Informacion General", "Información General");
		assertAnyTextVisible("BUSINESS PLAN");
		assertAnyTextVisible("Cambiar Plan");

		final String bodyText = driver.findElement(By.tagName("body")).getText();
		final boolean hasEmail = bodyText.contains("juanlucasbarbiergarzon@gmail.com")
				|| EMAIL_PATTERN.matcher(bodyText).find();
		assertTrue("Expected a visible user email in Informacion General section.", hasEmail);

		final String sectionText = getSectionText("Informacion General", "Información General");
		final boolean hasNameLikeValue = sectionText.lines()
				.map(String::trim)
				.anyMatch(line -> !line.isEmpty() && !line.equalsIgnoreCase("informacion general")
						&& !line.equalsIgnoreCase("información general") && !line.equalsIgnoreCase("business plan")
						&& !line.equalsIgnoreCase("cambiar plan") && !EMAIL_PATTERN.matcher(line).matches()
						&& line.chars().filter(Character::isLetter).count() >= 3);
		assertTrue("Expected a visible user name in Informacion General section.", hasNameLikeValue);
	}

	private void stepValidateDetallesCuenta() {
		assertAnyTextVisible("Detalles de la Cuenta");
		assertAnyTextVisible("Cuenta creada");
		assertAnyTextVisible("Estado activo");
		assertAnyTextVisible("Idioma seleccionado");
	}

	private void stepValidateTusNegocios() {
		assertAnyTextVisible("Tus Negocios");
		assertAnyTextVisible("Agregar Negocio");
		assertAnyTextVisible("Tienes 2 de 3 negocios");

		final String sectionText = getSectionText("Tus Negocios");
		assertTrue("Business list content is not visible in Tus Negocios section.", sectionText.length() > 30);
	}

	private void stepValidateTerminosYCondiciones() throws Exception {
		termsFinalUrl = openLegalLinkAndValidate("08_terminos_y_condiciones", new String[] { "Terminos y Condiciones",
				"Términos y Condiciones" }, new String[] { "Terminos y Condiciones", "Términos y Condiciones" });
	}

	private void stepValidatePoliticaDePrivacidad() throws Exception {
		privacyFinalUrl = openLegalLinkAndValidate("09_politica_de_privacidad",
				new String[] { "Politica de Privacidad", "Política de Privacidad" },
				new String[] { "Politica de Privacidad", "Política de Privacidad" });
	}

	private String openLegalLinkAndValidate(final String screenshotName, final String[] linkTexts, final String[] headingTexts)
			throws Exception {
		final String appHandle = driver.getWindowHandle();
		final String appUrl = driver.getCurrentUrl();
		final Set<String> beforeHandles = driver.getWindowHandles();

		clickByVisibleText(linkTexts);
		final String destinationHandle = waitForNavigationOrNewTab(beforeHandles, appHandle, appUrl);

		driver.switchTo().window(destinationHandle);
		waitForUiToLoad();
		waitForAnyVisibleText(headingTexts);
		assertAnyTextVisible(headingTexts);

		final String legalText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Legal page content should contain substantial text.", legalText.length() > 200);

		takeScreenshot(screenshotName);
		final String finalUrl = driver.getCurrentUrl();

		if (!destinationHandle.equals(appHandle)) {
			driver.close();
			driver.switchTo().window(appHandle);
		} else {
			driver.navigate().back();
			waitForUiToLoad();
		}

		if (!driver.getCurrentUrl().equals(appUrl) && !isAnyTextVisible("Mi Negocio", "Administrar Negocios")) {
			clickByVisibleText("Mi Negocio");
			clickByVisibleText("Administrar Negocios");
			waitForUiToLoad();
		}

		return finalUrl;
	}

	private String waitForNavigationOrNewTab(final Set<String> beforeHandles, final String appHandle, final String appUrl) {
		return new WebDriverWait(driver, Duration.ofSeconds(20)).until(d -> {
			final Set<String> currentHandles = d.getWindowHandles();
			if (currentHandles.size() > beforeHandles.size()) {
				for (String handle : currentHandles) {
					if (!beforeHandles.contains(handle)) {
						return handle;
					}
				}
			}

			if (!d.getCurrentUrl().equals("about:blank") && !d.getWindowHandle().equals(appHandle)) {
				return d.getWindowHandle();
			}

			if (d.getWindowHandle().equals(appHandle) && !d.getCurrentUrl().equals(appUrl)) {
				return appHandle;
			}

			return null;
		});
	}

	private void handleGoogleAccountChooser(final Set<String> beforeClickHandles, final String originalHandle) {
		final WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(12));

		try {
			shortWait.until((ExpectedCondition<Boolean>) d -> d.getWindowHandles().size() > beforeClickHandles.size()
					|| isAnyTextVisible("Elige una cuenta", "Choose an account", "juanlucasbarbiergarzon@gmail.com")
					|| d.getCurrentUrl().contains("accounts.google"));
		} catch (Exception ignored) {
			// If no chooser appears, continue (session might already be authenticated).
		}

		final Set<String> currentHandles = driver.getWindowHandles();
		if (currentHandles.size() > beforeClickHandles.size()) {
			for (String handle : currentHandles) {
				if (!beforeClickHandles.contains(handle)) {
					driver.switchTo().window(handle);
					break;
				}
			}
		}

		if (isAnyTextVisible("juanlucasbarbiergarzon@gmail.com")) {
			clickByVisibleText("juanlucasbarbiergarzon@gmail.com");
		}

		try {
			new WebDriverWait(driver, Duration.ofSeconds(30)).until(d -> isAnyTextVisible("Negocio", "Mi Negocio")
					|| d.getWindowHandles().size() == 1 && d.getCurrentUrl().contains("saleads"));
		} catch (Exception ignored) {
			// Continue with the default flow; subsequent assertions will confirm dashboard availability.
		}

		if (driver.getWindowHandles().contains(originalHandle)) {
			driver.switchTo().window(originalHandle);
		}
	}

	private void runAndRecord(final String reportField, final CheckedRunnable step) throws Exception {
		try {
			step.run();
			finalReport.put(reportField, true);
			finalReportDetails.put(reportField, "PASS");
		} catch (Throwable t) {
			finalReport.put(reportField, false);
			finalReportDetails.put(reportField, t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
		}
	}

	private void writeFinalReport() throws IOException {
		final StringBuilder sb = new StringBuilder();
		sb.append("saleads_mi_negocio_full_test").append(System.lineSeparator());
		sb.append("Evidence directory: ").append(evidenceDir).append(System.lineSeparator());
		sb.append(System.lineSeparator());
		sb.append("PASS/FAIL by validation step").append(System.lineSeparator());

		final String[] requiredFields = new String[] { "Login", "Mi Negocio menu", "Agregar Negocio modal",
				"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
				"Términos y Condiciones", "Política de Privacidad" };

		for (String field : requiredFields) {
			final boolean pass = finalReport.getOrDefault(field, false);
			final String status = pass ? "PASS" : "FAIL";
			final String detail = finalReportDetails.getOrDefault(field, "No detail");
			sb.append("- ").append(field).append(": ").append(status).append(" | ").append(detail)
					.append(System.lineSeparator());
		}

		sb.append(System.lineSeparator());
		sb.append("Terminos y Condiciones final URL: ").append(termsFinalUrl).append(System.lineSeparator());
		sb.append("Politica de Privacidad final URL: ").append(privacyFinalUrl).append(System.lineSeparator());

		final Path reportPath = evidenceDir.resolve("final_report.txt");
		Files.writeString(reportPath, sb.toString());
		System.out.println(sb);
	}

	private void waitForUiToLoad() {
		new WebDriverWait(driver, Duration.ofSeconds(20))
				.until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState")));
		try {
			Thread.sleep(700);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	private void clickByVisibleText(final String... texts) {
		WebElement element = firstVisibleTextElement(texts);
		try {
			wait.until(d -> element.isDisplayed() && element.isEnabled());
			element.click();
		} catch (Exception clickException) {
			((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
		waitForUiToLoad();
	}

	private void waitForAnyVisibleText(final String... texts) {
		new WebDriverWait(driver, Duration.ofSeconds(30)).until(d -> isAnyTextVisible(texts));
	}

	private void assertAnyTextVisible(final String... texts) {
		assertTrue("Expected one of the following texts to be visible: " + String.join(", ", texts), isAnyTextVisible(texts));
	}

	private void assertTextVisible(final String text) {
		assertAnyTextVisible(text);
	}

	private void assertAnyElementVisible(final By... locators) {
		for (By locator : locators) {
			if (isElementVisible(locator)) {
				return;
			}
		}
		assertTrue("Expected at least one locator to be visible.", false);
	}

	private boolean isAnyTextVisible(final String... texts) {
		for (String text : texts) {
			if (findVisibleByText(text) != null) {
				return true;
			}
		}
		return false;
	}

	private boolean isElementVisible(final By locator) {
		try {
			for (WebElement element : driver.findElements(locator)) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		} catch (Exception ignored) {
			// Locator failures should be treated as not visible.
		}
		return false;
	}

	private WebElement firstVisibleTextElement(final String... texts) {
		for (String text : texts) {
			WebElement element = findVisibleByText(text);
			if (element != null) {
				return findClickableAncestor(element);
			}
		}
		throw new RuntimeException("Could not find a visible element with text options: " + String.join(", ", texts));
	}

	private WebElement findVisibleByText(final String text) {
		final String exactXpath = "//*[normalize-space()=" + xpathLiteral(text) + "]";
		final String containsXpath = "//*[contains(normalize-space(), " + xpathLiteral(text) + ")]";

		for (WebElement el : driver.findElements(By.xpath(exactXpath))) {
			if (el.isDisplayed()) {
				return el;
			}
		}
		for (WebElement el : driver.findElements(By.xpath(containsXpath))) {
			if (el.isDisplayed()) {
				return el;
			}
		}
		return null;
	}

	private WebElement findClickableAncestor(final WebElement element) {
		try {
			return element.findElement(By.xpath(
					"./ancestor-or-self::*[self::button or self::a or @role='button' or @onclick or contains(@class,'button')][1]"));
		} catch (Exception e) {
			return element;
		}
	}

	private WebElement firstVisibleElement(final By... locators) {
		for (By locator : locators) {
			List<WebElement> elements = driver.findElements(locator);
			for (WebElement element : elements) {
				if (element.isDisplayed()) {
					return element;
				}
			}
		}
		throw new RuntimeException("No visible element found for provided locators.");
	}

	private String getSectionText(final String... headings) {
		for (String heading : headings) {
			WebElement headingElement = findVisibleByText(heading);
			if (headingElement != null) {
				try {
					WebElement section = headingElement
							.findElement(By.xpath("./ancestor::*[self::section or self::div][1]"));
					return section.getText();
				} catch (Exception ignored) {
					return headingElement.getText();
				}
			}
		}
		return "";
	}

	private void takeScreenshot(final String checkpoint) throws IOException {
		final Path screenshotPath = evidenceDir.resolve(checkpoint + ".png");
		final byte[] image = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
		Files.copy(new java.io.ByteArrayInputStream(image), screenshotPath, StandardCopyOption.REPLACE_EXISTING);
	}

	private String getEnvOrDefault(final String key, final String defaultValue) {
		final String value = System.getenv(key);
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		return value;
	}

	private String firstNonBlankEnv(final String... keys) {
		for (String key : keys) {
			final String value = System.getenv(key);
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private String xpathLiteral(final String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}

		StringBuilder sb = new StringBuilder("concat(");
		String[] parts = value.split("'");
		for (int i = 0; i < parts.length; i++) {
			sb.append("'").append(parts[i]).append("'");
			if (i < parts.length - 1) {
				sb.append(", \"'\", ");
			}
		}
		sb.append(")");
		return sb.toString();
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
