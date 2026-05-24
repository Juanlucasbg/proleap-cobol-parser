package io.proleap.cobol.e2e;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * End-to-end test for the SaleADS Mi Negocio module workflow.
 *
 * Usage:
 * mvn -Dtest=SaleadsMiNegocioFullWorkflowTest
 *     -Dsaleads.e2e.enabled=true
 *     -Dsaleads.url=https://your-environment/login
 *     test
 */
public class SaleadsMiNegocioFullWorkflowTest {

	private static final String DEFAULT_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final String REPORT_LOGIN = "Login";
	private static final String REPORT_MI_NEGOCIO_MENU = "Mi Negocio menu";
	private static final String REPORT_AGREGAR_NEGOCIO_MODAL = "Agregar Negocio modal";
	private static final String REPORT_ADMINISTRAR_NEGOCIOS = "Administrar Negocios view";
	private static final String REPORT_INFO_GENERAL = "Información General";
	private static final String REPORT_DETALLES_CUENTA = "Detalles de la Cuenta";
	private static final String REPORT_TUS_NEGOCIOS = "Tus Negocios";
	private static final String REPORT_TERMINOS = "Términos y Condiciones";
	private static final String REPORT_POLITICA = "Política de Privacidad";

	private WebDriver driver;
	private WebDriverWait wait;
	private Path screenshotDir;
	private String appWindowHandle;

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	@Before
	public void setup() throws IOException {
		Assume.assumeTrue("Set -Dsaleads.e2e.enabled=true to run this test.",
				Boolean.parseBoolean(System.getProperty("saleads.e2e.enabled", "false")));

		final ChromeOptions options = new ChromeOptions();
		if (Boolean.parseBoolean(System.getProperty("saleads.headless", "true"))) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080", "--disable-dev-shm-usage", "--no-sandbox");

		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, Duration.ofSeconds(getTimeoutSeconds()));
		screenshotDir = Paths.get("target", "screenshots", "saleads-mi-negocio");
		Files.createDirectories(screenshotDir);

		final String startUrl = firstNonBlank(System.getProperty("saleads.url"), System.getenv("SALEADS_URL"));
		Assume.assumeTrue(
				"Provide a SaleADS login URL with -Dsaleads.url=<url> or SALEADS_URL for environment-agnostic execution.",
				startUrl != null);
		driver.navigate().to(startUrl);
		waitForUiLoad();
		appWindowHandle = driver.getWindowHandle();
	}

	@Test
	public void saleadsMiNegocioFullWorkflow() {
		runStep(REPORT_LOGIN, this::stepLoginWithGoogle);
		runStep(REPORT_MI_NEGOCIO_MENU, this::stepOpenMiNegocioMenu);
		runStep(REPORT_AGREGAR_NEGOCIO_MODAL, this::stepValidateAgregarNegocioModal);
		runStep(REPORT_ADMINISTRAR_NEGOCIOS, this::stepOpenAdministrarNegocios);
		runStep(REPORT_INFO_GENERAL, this::stepValidateInformacionGeneral);
		runStep(REPORT_DETALLES_CUENTA, this::stepValidateDetallesDeLaCuenta);
		runStep(REPORT_TUS_NEGOCIOS, this::stepValidateTusNegocios);
		runStep(REPORT_TERMINOS, () -> stepValidateLegalLink("Términos y Condiciones", "Términos y Condiciones"));
		runStep(REPORT_POLITICA, () -> stepValidateLegalLink("Política de Privacidad", "Política de Privacidad"));

		assertAllStepsPassed();
	}

	@After
	public void tearDown() {
		try {
			printReport();
		} finally {
			if (driver != null) {
				driver.quit();
			}
		}
	}

	private void stepLoginWithGoogle() throws IOException, InterruptedException {
		clickAnyByVisibleText("Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google");
		waitForUiLoad();
		handleGoogleAccountSelectorIfPresent();
		switchBackToApplicationWindow();
		waitForMainInterface();

		assertTrue("Main app interface did not load.", isVisible(By.xpath("//aside | //nav | //main")));
		assertTrue("Left sidebar navigation is not visible.",
				isVisible(By.xpath("//aside//*[contains(normalize-space(.),'Negocio')]"
						+ " | //nav//*[contains(normalize-space(.),'Negocio')]"
						+ " | //*[contains(normalize-space(.),'Mi Negocio')]")));
		captureScreenshot("01-dashboard-loaded");
	}

	private void stepOpenMiNegocioMenu() throws IOException, InterruptedException {
		expandMiNegocioMenuIfNeeded();
		assertTrue("Expected 'Agregar Negocio' to be visible.", isVisibleText("Agregar Negocio"));
		assertTrue("Expected 'Administrar Negocios' to be visible.", isVisibleText("Administrar Negocios"));
		captureScreenshot("02-mi-negocio-menu-expanded");
	}

	private void stepValidateAgregarNegocioModal() throws IOException, InterruptedException {
		clickAnyByVisibleText("Agregar Negocio");
		assertVisibleTextAny("Crear Nuevo Negocio");
		assertTrue("Input field 'Nombre del Negocio' not found.", isVisible(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio' or @name='nombreNegocio']"
						+ " | //label[contains(normalize-space(.),'Nombre del Negocio')]")));
		assertTrue("Expected business quota text not found.", isVisibleText("Tienes 2 de 3 negocios"));
		assertTrue("'Cancelar' button missing.", isVisibleButtonLike("Cancelar"));
		assertTrue("'Crear Negocio' button missing.", isVisibleButtonLike("Crear Negocio"));
		captureScreenshot("03-agregar-negocio-modal");

		final Optional<WebElement> input = findFirstVisible(By.xpath(
				"//input[@placeholder='Nombre del Negocio' or @aria-label='Nombre del Negocio' or @name='nombreNegocio']"
						+ " | //div[contains(@class,'modal')]//input[1]"));
		if (input.isPresent()) {
			input.get().click();
			input.get().clear();
			input.get().sendKeys("Negocio Prueba Automatizacion");
		}
		clickAnyByVisibleText("Cancelar");
		wait.until(ExpectedConditions.invisibilityOfElementLocated(By.xpath("//*[contains(normalize-space(.),'Crear Nuevo Negocio')]")));
		waitForUiLoad();
	}

	private void stepOpenAdministrarNegocios() throws IOException, InterruptedException {
		expandMiNegocioMenuIfNeeded();
		clickAnyByVisibleText("Administrar Negocios");

		assertVisibleTextAny("Información General", "Informacion General");
		assertVisibleTextAny("Detalles de la Cuenta", "Detalles de la Cuenta");
		assertVisibleTextAny("Tus Negocios");
		assertVisibleTextAny("Sección Legal", "Seccion Legal");
		captureScreenshot("04-administrar-negocios-view");
	}

	private void stepValidateInformacionGeneral() {
		final WebElement section = findSectionByTitle("Información General", "Informacion General");
		final String sectionText = section.getText();

		final String expectedEmail = firstNonBlank(System.getProperty("saleads.user.email"), System.getenv("SALEADS_USER_EMAIL"),
				DEFAULT_ACCOUNT_EMAIL);
		if (expectedEmail != null && sectionText.contains(expectedEmail)) {
			assertTrue("Expected user email is not visible in 'Información General'.", sectionText.contains(expectedEmail));
		} else {
			assertTrue("No user email found in 'Información General'.", containsEmail(sectionText));
		}

		assertTrue("'BUSINESS PLAN' is not visible in 'Información General'.", sectionText.contains("BUSINESS PLAN"));
		assertTrue("'Cambiar Plan' button is not visible.", isVisibleButtonLike("Cambiar Plan"));
		assertTrue("User name is not visible in 'Información General'.", containsUserNameLikeText(sectionText, expectedEmail));
	}

	private void stepValidateDetallesDeLaCuenta() {
		final WebElement section = findSectionByTitle("Detalles de la Cuenta");
		final String sectionText = section.getText();

		assertTrue("'Cuenta creada' is not visible.", sectionText.contains("Cuenta creada"));
		assertTrue("'Estado activo' is not visible.", sectionText.contains("Estado activo"));
		assertTrue("'Idioma seleccionado' is not visible.", sectionText.contains("Idioma seleccionado"));
	}

	private void stepValidateTusNegocios() {
		final WebElement section = findSectionByTitle("Tus Negocios");
		final String sectionText = section.getText();

		assertTrue("Business list is not visible.",
				hasVisibleDescendant(section, By.xpath(".//table | .//ul | .//ol | .//*[@role='list'] | .//tr | .//li"))
						|| sectionText.split("\\R").length >= 3);
		assertTrue("'Agregar Negocio' button not found in 'Tus Negocios'.",
				hasVisibleDescendant(section, buttonLikeLocator("Agregar Negocio")) || isVisibleButtonLike("Agregar Negocio"));
		assertTrue("'Tienes 2 de 3 negocios' is not visible.", sectionText.contains("Tienes 2 de 3 negocios"));
	}

	private void stepValidateLegalLink(final String linkText, final String expectedHeading) throws IOException, InterruptedException {
		final String originalHandle = driver.getWindowHandle();
		final Set<String> handlesBefore = driver.getWindowHandles();

		clickAnyByVisibleText(linkText);
		waitForUiLoad();

		final Optional<String> newHandle = waitForNewWindow(handlesBefore);
		if (newHandle.isPresent()) {
			driver.switchTo().window(newHandle.get());
			waitForUiLoad();
		}

		assertVisibleTextAny(expectedHeading);
		final String bodyText = driver.findElement(By.tagName("body")).getText();
		assertTrue("Legal content text is not visible for '" + linkText + "'.", bodyText.replaceAll("\\s+", " ").length() > 120);
		captureScreenshot("legal-" + toSlug(linkText));

		legalUrls.put(linkText, driver.getCurrentUrl());

		if (newHandle.isPresent()) {
			driver.close();
			driver.switchTo().window(originalHandle);
			waitForUiLoad();
		} else {
			driver.navigate().back();
			waitForUiLoad();
		}
	}

	private void runStep(final String stepName, final CheckedRunnable runnable) {
		try {
			runnable.run();
			report.put(stepName, true);
		} catch (final Throwable throwable) {
			report.put(stepName, false);
			try {
				captureScreenshot("failure-" + toSlug(stepName));
			} catch (final IOException ignored) {
				// best effort screenshot on failure
			}
			System.err.println("[FAIL] " + stepName + " -> " + throwable.getMessage());
		}
	}

	private void assertAllStepsPassed() {
		final List<String> failed = report.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey)
				.collect(Collectors.toList());
		assertTrue("One or more workflow steps failed.\n" + formatReport(), failed.isEmpty());
	}

	private String formatReport() {
		final StringBuilder sb = new StringBuilder();
		sb.append("\n========== SaleADS Mi Negocio Report ==========\n");
		report.forEach((step, passed) -> sb.append(step).append(": ").append(passed ? "PASS" : "FAIL").append('\n'));

		if (!legalUrls.isEmpty()) {
			sb.append("Legal URLs:\n");
			legalUrls.forEach((name, url) -> sb.append(" - ").append(name).append(": ").append(url).append('\n'));
		}

		sb.append("Screenshots directory: ").append(screenshotDir.toAbsolutePath()).append('\n');
		sb.append("===============================================\n");
		return sb.toString();
	}

	private void printReport() {
		if (!report.isEmpty()) {
			System.out.println(formatReport());
		}
	}

	private void clickAnyByVisibleText(final String... labels) throws InterruptedException {
		for (final String label : labels) {
			final Optional<WebElement> target = findFirstVisible(actionLocator(label));
			if (target.isPresent()) {
				clickAndWait(target.get());
				return;
			}
		}
		throw new AssertionError("No visible clickable element found with labels: " + Arrays.toString(labels));
	}

	private void clickAndWait(final WebElement element) throws InterruptedException {
		wait.until(ExpectedConditions.elementToBeClickable(element));
		element.click();
		waitForUiLoad();
	}

	private void waitForUiLoad() throws InterruptedException {
		wait.until(driver -> {
			final Object state = ((JavascriptExecutor) driver).executeScript("return document.readyState");
			return Objects.equals("complete", state);
		});
		Thread.sleep(500);
	}

	private void handleGoogleAccountSelectorIfPresent() throws InterruptedException {
		final Optional<String> possiblePopup = waitForNewWindow(Set.of(appWindowHandle));
		if (possiblePopup.isPresent()) {
			driver.switchTo().window(possiblePopup.get());
			waitForUiLoad();
		}

		final String currentUrl = driver.getCurrentUrl();
		if (currentUrl.contains("accounts.google.com") || isVisibleText("Choose an account") || isVisibleText("Elegir una cuenta")) {
			clickAnyByVisibleText(DEFAULT_ACCOUNT_EMAIL);
			waitForUiLoad();
		}
	}

	private void switchBackToApplicationWindow() {
		for (final String handle : driver.getWindowHandles()) {
			driver.switchTo().window(handle);
			final String currentUrl = driver.getCurrentUrl();
			if (!currentUrl.contains("accounts.google.com")) {
				appWindowHandle = handle;
				return;
			}
		}
		driver.switchTo().window(appWindowHandle);
	}

	private void waitForMainInterface() {
		wait.until(ExpectedConditions.or(
				ExpectedConditions.visibilityOfElementLocated(By.xpath("//aside//*[contains(normalize-space(.),'Negocio')]")),
				ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[contains(normalize-space(.),'Mi Negocio')]")),
				ExpectedConditions.visibilityOfElementLocated(By.xpath("//nav//*[contains(normalize-space(.),'Negocio')]"))));
	}

	private void expandMiNegocioMenuIfNeeded() throws InterruptedException {
		if (!isVisibleText("Administrar Negocios") || !isVisibleText("Agregar Negocio")) {
			clickAnyByVisibleText("Mi Negocio", "Negocio");
		}
		wait.until(ExpectedConditions.or(ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[contains(normalize-space(.),'Agregar Negocio')]")),
				ExpectedConditions.visibilityOfElementLocated(By.xpath("//*[contains(normalize-space(.),'Administrar Negocios')]"))));
	}

	private WebElement findSectionByTitle(final String... titles) {
		for (final String title : titles) {
			final Optional<WebElement> titleElement = findFirstVisible(By.xpath(
					"//h1[contains(normalize-space(.),'" + title + "')]"
							+ " | //h2[contains(normalize-space(.),'" + title + "')]"
							+ " | //h3[contains(normalize-space(.),'" + title + "')]"
							+ " | //h4[contains(normalize-space(.),'" + title + "')]"
							+ " | //*[contains(@class,'title') and contains(normalize-space(.),'" + title + "')]"));
			if (titleElement.isPresent()) {
				final Optional<WebElement> section = findClosestSectionContainer(titleElement.get());
				if (section.isPresent()) {
					return section.get();
				}
			}
		}
		throw new AssertionError("Could not find section for titles: " + Arrays.toString(titles));
	}

	private Optional<WebElement> findClosestSectionContainer(final WebElement header) {
		final List<By> candidates = List.of(By.xpath("./ancestor::section[1]"), By.xpath("./ancestor::article[1]"),
				By.xpath("./ancestor::div[1]"), By.xpath("./parent::*"));
		for (final By candidate : candidates) {
			try {
				final WebElement found = header.findElement(candidate);
				if (found != null && found.isDisplayed()) {
					return Optional.of(found);
				}
			} catch (final Exception ignored) {
				// try next candidate
			}
		}
		return Optional.empty();
	}

	private Optional<String> waitForNewWindow(final Set<String> handlesBefore) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(8))
					.until(webDriver -> webDriver.getWindowHandles().size() > handlesBefore.size());
			return driver.getWindowHandles().stream().filter(handle -> !handlesBefore.contains(handle)).findFirst();
		} catch (final TimeoutException timeoutException) {
			return Optional.empty();
		}
	}

	private void captureScreenshot(final String checkpointName) throws IOException {
		final File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		final Path target = screenshotDir.resolve(timestamp + "-" + toSlug(checkpointName) + ".png");
		Files.copy(src.toPath(), target);
		System.out.println("Saved screenshot: " + target.toAbsolutePath());
	}

	private void assertVisibleTextAny(final String... texts) {
		for (final String text : texts) {
			if (isVisibleText(text)) {
				return;
			}
		}
		throw new AssertionError("None of the expected texts were visible: " + Arrays.toString(texts));
	}

	private boolean isVisibleText(final String text) {
		return isVisible(By.xpath("//*[contains(normalize-space(.),'" + text + "')]"));
	}

	private boolean isVisibleButtonLike(final String text) {
		return isVisible(buttonLikeLocator(text));
	}

	private By buttonLikeLocator(final String text) {
		return By.xpath("//button[contains(normalize-space(.),'" + text + "') or .//*[contains(normalize-space(.),'" + text + "')]]"
				+ " | //a[contains(normalize-space(.),'" + text + "') or .//*[contains(normalize-space(.),'" + text + "')]]"
				+ " | //*[@role='button' and contains(normalize-space(.),'" + text + "')]"
				+ " | //*[@role='menuitem' and contains(normalize-space(.),'" + text + "')]");
	}

	private By actionLocator(final String text) {
		return By.xpath("//button[contains(normalize-space(.),'" + text + "') or .//*[contains(normalize-space(.),'" + text + "')]]"
				+ " | //a[contains(normalize-space(.),'" + text + "') or .//*[contains(normalize-space(.),'" + text + "')]]"
				+ " | //*[@role='button' and contains(normalize-space(.),'" + text + "')]"
				+ " | //*[@role='menuitem' and contains(normalize-space(.),'" + text + "')]"
				+ " | //span[contains(normalize-space(.),'" + text + "')]/ancestor::*[self::button or self::a or @role='button'][1]");
	}

	private boolean isVisible(final By by) {
		try {
			final List<WebElement> found = driver.findElements(by);
			for (final WebElement element : found) {
				if (element.isDisplayed()) {
					return true;
				}
			}
			return false;
		} catch (final Exception ignored) {
			return false;
		}
	}

	private Optional<WebElement> findFirstVisible(final By by) {
		try {
			wait.until(ExpectedConditions.presenceOfElementLocated(by));
			final List<WebElement> elements = driver.findElements(by);
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return Optional.of(element);
				}
			}
		} catch (final Exception ignored) {
			// handled by empty optional return
		}
		return Optional.empty();
	}

	private boolean hasVisibleDescendant(final WebElement root, final By by) {
		try {
			return root.findElements(by).stream().anyMatch(WebElement::isDisplayed);
		} catch (final Exception ignored) {
			return false;
		}
	}

	private boolean containsEmail(final String text) {
		final Pattern emailPattern = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
		final Matcher matcher = emailPattern.matcher(text);
		return matcher.find();
	}

	private boolean containsUserNameLikeText(final String sectionText, final String expectedEmail) {
		final List<String> lines = Arrays.stream(sectionText.split("\\R")).map(String::trim).filter(line -> !line.isBlank())
				.collect(Collectors.toCollection(ArrayList::new));
		lines.removeIf(line -> line.equalsIgnoreCase("Información General") || line.equalsIgnoreCase("Informacion General")
				|| line.equalsIgnoreCase("BUSINESS PLAN") || line.equalsIgnoreCase("Cambiar Plan")
				|| (expectedEmail != null && line.contains(expectedEmail)) || containsEmail(line));
		return lines.stream().anyMatch(line -> line.matches(".*[A-Za-z].*") && line.length() >= 3);
	}

	private long getTimeoutSeconds() {
		final String timeout = System.getProperty("saleads.timeout.seconds", "30");
		return Long.parseLong(timeout);
	}

	private String firstNonBlank(final String... values) {
		for (final String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private String toSlug(final String text) {
		return text.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+)|(-+$)", "");
	}

	@FunctionalInterface
	private interface CheckedRunnable {
		void run() throws Exception;
	}
}
