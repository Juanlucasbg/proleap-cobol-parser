package io.proleap.cobol.e2e;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.text.Normalizer;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public class SaleadsMiNegocioFullTest {

	private static final String GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);
	private static final Duration SHORT_TIMEOUT = Duration.ofSeconds(6);
	private static final Pattern EMAIL_PATTERN = Pattern
			.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
	private static final List<String> REPORT_ORDER = Arrays.asList("Login", "Mi Negocio menu", "Agregar Negocio modal",
			"Administrar Negocios view", "Información General", "Detalles de la Cuenta", "Tus Negocios",
			"Términos y Condiciones", "Política de Privacidad");

	private final Map<String, Boolean> report = new LinkedHashMap<>();
	private final Map<String, String> legalUrls = new LinkedHashMap<>();

	private WebDriver driver;
	private WebDriverWait wait;
	private Path evidenceDir;

	@Test
	public void saleadsMiNegocioFullTest() throws Exception {
		final String loginUrl = trimToNull(System.getenv("SALEADS_LOGIN_URL"));
		Assume.assumeTrue(
				"Set SALEADS_LOGIN_URL to the current environment login page (domain is not hardcoded in the test).",
				loginUrl != null);

		setUpDriverAndEvidence();

		report.put("Login", runStep("Login", () -> stepLoginWithGoogle(loginUrl)));
		report.put("Mi Negocio menu", runStep("Mi Negocio menu", this::stepOpenMiNegocioMenu));
		report.put("Agregar Negocio modal", runStep("Agregar Negocio modal", this::stepValidateAgregarNegocioModal));
		report.put("Administrar Negocios view",
				runStep("Administrar Negocios view", this::stepOpenAdministrarNegocios));
		report.put("Información General", runStep("Información General", this::stepValidateInformacionGeneral));
		report.put("Detalles de la Cuenta", runStep("Detalles de la Cuenta", this::stepValidateDetallesCuenta));
		report.put("Tus Negocios", runStep("Tus Negocios", this::stepValidateTusNegocios));
		report.put("Términos y Condiciones",
				runStep("Términos y Condiciones", () -> stepValidateLegalLink("Términos y Condiciones",
						"Términos y Condiciones", "step8-terminos-y-condiciones.png", "Términos y Condiciones URL")));
		report.put("Política de Privacidad",
				runStep("Política de Privacidad", () -> stepValidateLegalLink("Política de Privacidad",
						"Política de Privacidad", "step9-politica-de-privacidad.png", "Política de Privacidad URL")));

		writeFinalReport();
		Assert.assertTrue("One or more validation steps failed.\n" + buildSummary(), allStepsPassed());
	}

	@After
	public void tearDown() {
		if (driver != null) {
			driver.quit();
		}
	}

	private void setUpDriverAndEvidence() throws IOException {
		final String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(OffsetDateTime.now());
		evidenceDir = Path.of("target", "saleads-evidence", timestamp);
		Files.createDirectories(evidenceDir);

		WebDriverManager.chromedriver().setup();
		final ChromeOptions options = new ChromeOptions();
		if (!"false".equalsIgnoreCase(System.getenv("SALEADS_HEADLESS"))) {
			options.addArguments("--headless=new");
		}
		options.addArguments("--window-size=1920,1080");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		driver = new ChromeDriver(options);
		wait = new WebDriverWait(driver, DEFAULT_TIMEOUT);
	}

	private boolean stepLoginWithGoogle(final String loginUrl) throws Exception {
		driver.get(loginUrl);
		waitForUiLoad();

		final Set<String> handlesBeforeGoogleClick = new LinkedHashSet<>(driver.getWindowHandles());
		clickFirstAvailable(Arrays.asList("Sign in with Google", "Iniciar sesión con Google", "Ingresar con Google",
				"Continuar con Google", "Google"));
		waitForUiLoad();
		selectGoogleAccountIfVisible(GOOGLE_ACCOUNT_EMAIL, handlesBeforeGoogleClick);

		final boolean mainAppVisible = waitForAnyVisibleText(
				Arrays.asList("Negocio", "Mi Negocio", "Dashboard", "Inicio"), DEFAULT_TIMEOUT);
		final boolean sidebarVisible = isSidebarVisible();
		captureScreenshot("step1-dashboard-loaded.png");
		return mainAppVisible && sidebarVisible;
	}

	private boolean stepOpenMiNegocioMenu() throws Exception {
		clickIfPresent("Negocio");
		clickFirstAvailable(List.of("Mi Negocio"));
		waitForUiLoad();

		final boolean agregarNegocioVisible = isVisibleText("Agregar Negocio");
		final boolean administrarNegociosVisible = isVisibleText("Administrar Negocios");
		captureScreenshot("step2-mi-negocio-menu-expanded.png");
		return agregarNegocioVisible && administrarNegociosVisible;
	}

	private boolean stepValidateAgregarNegocioModal() throws Exception {
		clickFirstAvailable(List.of("Agregar Negocio"));
		waitForUiLoad();

		final boolean titleVisible = waitForAnyVisibleText(List.of("Crear Nuevo Negocio"), DEFAULT_TIMEOUT);
		final boolean nameInputVisible = isAnyElementDisplayed(Arrays.asList(
				By.xpath("//label[contains(normalize-space(.), " + xpathLiteral("Nombre del Negocio") + ")]"),
				By.xpath("//input[@placeholder=" + xpathLiteral("Nombre del Negocio") + "]"),
				By.xpath("//input[contains(translate(@name,'NOMBRE','nombre'),'nombre')]"),
				By.xpath("//input[contains(translate(@id,'NOMBRE','nombre'),'nombre')]")));
		final boolean limitVisible = isVisibleText("Tienes 2 de 3 negocios");
		final boolean cancelarVisible = isVisibleText("Cancelar");
		final boolean crearNegocioVisible = isVisibleText("Crear Negocio");

		captureScreenshot("step3-crear-nuevo-negocio-modal.png");

		final WebElement nombreNegocioInput = firstVisibleElement(Arrays.asList(
				By.xpath("//input[@placeholder=" + xpathLiteral("Nombre del Negocio") + "]"),
				By.xpath("//input[contains(translate(@name,'NOMBRE','nombre'),'nombre')]"),
				By.xpath("//input[contains(translate(@id,'NOMBRE','nombre'),'nombre')]")));
		if (nombreNegocioInput != null) {
			nombreNegocioInput.click();
			nombreNegocioInput.clear();
			nombreNegocioInput.sendKeys("Negocio Prueba Automatización");
		}

		clickIfPresent("Cancelar");
		waitForUiLoad();
		return titleVisible && nameInputVisible && limitVisible && cancelarVisible && crearNegocioVisible;
	}

	private boolean stepOpenAdministrarNegocios() throws Exception {
		if (!isVisibleText("Administrar Negocios")) {
			clickIfPresent("Mi Negocio");
		}
		clickFirstAvailable(List.of("Administrar Negocios"));
		waitForUiLoad();

		final boolean infoGeneral = waitForAnyVisibleText(Arrays.asList("Información General", "Informacion General"),
				DEFAULT_TIMEOUT);
		final boolean detallesCuenta = isVisibleAny(Arrays.asList("Detalles de la Cuenta", "Detalles de la cuenta"));
		final boolean tusNegocios = isVisibleAny(Arrays.asList("Tus Negocios", "Tus negocios"));
		final boolean seccionLegal = isVisibleAny(Arrays.asList("Sección Legal", "Seccion Legal"));

		captureScreenshot("step4-administrar-negocios-view.png");
		return infoGeneral && detallesCuenta && tusNegocios && seccionLegal;
	}

	private boolean stepValidateInformacionGeneral() {
		final WebElement infoSection = findSectionByHeading(Arrays.asList("Información General", "Informacion General"));
		final String sectionText = infoSection != null ? safeText(infoSection) : bodyText();
		final boolean userEmailVisible = EMAIL_PATTERN.matcher(sectionText).find();
		final boolean businessPlanVisible = containsNormalized(sectionText, "BUSINESS PLAN")
				|| isVisibleText("BUSINESS PLAN");
		final boolean cambiarPlanVisible = isVisibleText("Cambiar Plan");

		boolean userNameVisible = false;
		for (final String line : sectionText.split("\\R+")) {
			final String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			if (EMAIL_PATTERN.matcher(trimmed).find()) {
				continue;
			}
			if (containsNormalized(trimmed, "informacion general") || containsNormalized(trimmed, "business plan")
					|| containsNormalized(trimmed, "cambiar plan")) {
				continue;
			}
			userNameVisible = true;
			break;
		}

		return userNameVisible && userEmailVisible && businessPlanVisible && cambiarPlanVisible;
	}

	private boolean stepValidateDetallesCuenta() {
		final WebElement detailsSection = findSectionByHeading(Arrays.asList("Detalles de la Cuenta",
				"Detalles de la cuenta", "Detalles de Cuenta", "Detalles de cuenta"));
		final String sectionText = detailsSection != null ? safeText(detailsSection) : bodyText();
		return containsNormalized(sectionText, "cuenta creada") && containsNormalized(sectionText, "estado activo")
				&& containsNormalized(sectionText, "idioma seleccionado");
	}

	private boolean stepValidateTusNegocios() {
		final WebElement negociosSection = findSectionByHeading(Arrays.asList("Tus Negocios", "Tus negocios"));
		final String sectionText = negociosSection != null ? safeText(negociosSection) : bodyText();
		final boolean businessListVisible = negociosSection != null && hasListLikeContent(negociosSection, sectionText);
		final boolean addButtonVisible = containsNormalized(sectionText, "agregar negocio") || isVisibleText("Agregar Negocio");
		final boolean quotaVisible = containsNormalized(sectionText, "tienes 2 de 3 negocios")
				|| isVisibleText("Tienes 2 de 3 negocios");
		return businessListVisible && addButtonVisible && quotaVisible;
	}

	private boolean stepValidateLegalLink(final String linkText, final String headingText, final String screenshotName,
			final String urlReportKey) throws Exception {
		final String appHandle = driver.getWindowHandle();
		final String appUrl = driver.getCurrentUrl();
		final Set<String> handlesBefore = new LinkedHashSet<>(driver.getWindowHandles());

		clickFirstAvailable(Arrays.asList(linkText, stripAccents(linkText)));
		waitForUiLoad();

		final String newHandle = waitForNewWindowHandle(handlesBefore, Duration.ofSeconds(10));
		if (newHandle != null) {
			driver.switchTo().window(newHandle);
			waitForUiLoad();
		}

		final boolean headingVisible = waitForAnyVisibleText(textVariants(headingText), DEFAULT_TIMEOUT);
		final String legalBodyText = bodyText();
		final boolean contentVisible = legalBodyText != null && legalBodyText.trim().length() >= 120;
		captureScreenshot(screenshotName);
		legalUrls.put(urlReportKey, driver.getCurrentUrl());

		if (newHandle != null) {
			driver.close();
			driver.switchTo().window(appHandle);
		} else if (!Objects.equals(appUrl, driver.getCurrentUrl())) {
			driver.navigate().back();
		}

		waitForUiLoad();
		waitForAnyVisibleText(Arrays.asList("Información General", "Informacion General", "Tus Negocios"), DEFAULT_TIMEOUT);
		return headingVisible && contentVisible;
	}

	private boolean runStep(final String stepName, final Step step) {
		try {
			return step.run();
		} catch (final Exception exception) {
			System.err.println("Step failed: " + stepName + " -> " + exception.getMessage());
			captureScreenshotSafe("error-" + slug(stepName) + ".png");
			return false;
		}
	}

	private void selectGoogleAccountIfVisible(final String email, final Set<String> handlesBeforeSelection)
			throws Exception {
		final String currentHandle = driver.getWindowHandle();
		String popupHandle = null;

		for (final String handle : driver.getWindowHandles()) {
			if (!handlesBeforeSelection.contains(handle)) {
				popupHandle = handle;
				break;
			}
		}
		if (popupHandle == null) {
			for (final String handle : driver.getWindowHandles()) {
				if (!Objects.equals(handle, currentHandle)) {
					popupHandle = handle;
					break;
				}
			}
		}
		if (popupHandle == null) {
			popupHandle = waitForNewWindowHandle(handlesBeforeSelection, Duration.ofSeconds(8));
		}

		if (popupHandle != null) {
			driver.switchTo().window(popupHandle);
			waitForUiLoad();
		}

		final WebElement accountTile = firstVisibleElement(Arrays.asList(
				By.xpath("//*[contains(normalize-space(.), " + xpathLiteral(email) + ")]"),
				By.xpath("//div[contains(@data-identifier, " + xpathLiteral(email) + ")]")));

		if (accountTile != null) {
			accountTile.click();
			waitForUiLoad();
		}

		if (popupHandle != null) {
			waitUntilHandleGoneOrTimeout(popupHandle, Duration.ofSeconds(15));
			driver.switchTo().window(currentHandle);
			waitForUiLoad();
		}
	}

	private void clickFirstAvailable(final List<String> candidateTexts) throws Exception {
		Exception lastError = null;
		for (final String candidate : candidateTexts) {
			for (final String variant : textVariants(candidate)) {
				for (final By locator : clickableLocators(variant)) {
					try {
						final WebElement element = new WebDriverWait(driver, SHORT_TIMEOUT)
								.until(ExpectedConditions.elementToBeClickable(locator));
						scrollIntoView(element);
						element.click();
						waitForUiLoad();
						return;
					} catch (final Exception clickError) {
						lastError = clickError;
					}
				}
			}
		}
		throw new NoSuchElementException("Unable to click any candidate text: " + candidateTexts + ". Last error: "
				+ (lastError != null ? lastError.getMessage() : "n/a"));
	}

	private void clickIfPresent(final String text) {
		try {
			clickFirstAvailable(List.of(text));
		} catch (final Exception ignored) {
			// Optional click action.
		}
	}

	private List<By> clickableLocators(final String text) {
		final String literal = xpathLiteral(text);
		final String textMatch = "contains(normalize-space(.), " + literal + ")";

		return Arrays.asList(By.xpath("//button[" + textMatch + "]"), By.xpath("//a[" + textMatch + "]"),
				By.xpath("//*[@role='button' and " + textMatch + "]"),
				By.xpath("//span[" + textMatch + "]/ancestor::*[self::button or self::a][1]"),
				By.xpath(
						"//*[self::button or self::a or self::span or self::div][string-length(normalize-space(.)) < 90 and "
								+ textMatch + "]"));
	}

	private boolean waitForAnyVisibleText(final List<String> texts, final Duration timeout) {
		try {
			return new WebDriverWait(driver, timeout).until(webDriver -> {
				for (final String text : texts) {
					if (isVisibleText(text)) {
						return true;
					}
				}
				return false;
			});
		} catch (final Exception ignored) {
			return false;
		}
	}

	private boolean isVisibleAny(final List<String> texts) {
		for (final String text : texts) {
			if (isVisibleText(text)) {
				return true;
			}
		}
		return false;
	}

	private boolean isVisibleText(final String text) {
		for (final String variant : textVariants(text)) {
			final String literal = xpathLiteral(variant);
			final List<WebElement> elements = driver.findElements(
					By.xpath("//*[contains(normalize-space(.), " + literal + ") and not(self::script) and not(self::style)]"));
			for (final WebElement element : elements) {
				if (element.isDisplayed()) {
					return true;
				}
			}
		}
		return containsNormalized(bodyText(), text);
	}

	private WebElement firstVisibleElement(final List<By> locators) {
		for (final By locator : locators) {
			for (final WebElement element : driver.findElements(locator)) {
				if (element.isDisplayed()) {
					return element;
				}
			}
		}
		return null;
	}

	private boolean isAnyElementDisplayed(final List<By> locators) {
		return firstVisibleElement(locators) != null;
	}

	private WebElement findSectionByHeading(final List<String> headingCandidates) {
		for (final String heading : headingCandidates) {
			for (final String variant : textVariants(heading)) {
				final String literal = xpathLiteral(variant);
				final List<By> locators = Arrays.asList(
						By.xpath("//*[self::h1 or self::h2 or self::h3 or self::h4][contains(normalize-space(.), " + literal
								+ ")]/ancestor::*[self::section or self::article or self::div][1]"),
						By.xpath("//*[contains(normalize-space(.), " + literal
								+ ")]/ancestor::*[self::section or self::article or self::div][1]"));
				final WebElement section = firstVisibleElement(locators);
				if (section != null) {
					return section;
				}
			}
		}
		return null;
	}

	private boolean hasListLikeContent(final WebElement section, final String fallbackText) {
		final List<WebElement> structuralElements = section.findElements(
				By.xpath(".//*[self::li or self::tr or contains(@class,'card') or contains(@class,'item') or contains(@class,'business')]"));
		for (final WebElement structuralElement : structuralElements) {
			if (structuralElement.isDisplayed()) {
				return true;
			}
		}
		int meaningfulLines = 0;
		for (final String line : fallbackText.split("\\R+")) {
			if (!line.trim().isEmpty()) {
				meaningfulLines++;
			}
		}
		return meaningfulLines >= 4;
	}

	private String waitForNewWindowHandle(final Set<String> handlesBefore, final Duration timeout) throws InterruptedException {
		final long deadline = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < deadline) {
			final Set<String> now = driver.getWindowHandles();
			if (now.size() > handlesBefore.size()) {
				for (final String handle : now) {
					if (!handlesBefore.contains(handle)) {
						return handle;
					}
				}
			}
			Thread.sleep(250);
		}
		return null;
	}

	private void waitUntilHandleGoneOrTimeout(final String handle, final Duration timeout) throws InterruptedException {
		final long deadline = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < deadline) {
			if (!driver.getWindowHandles().contains(handle)) {
				return;
			}
			Thread.sleep(250);
		}
	}

	private boolean isSidebarVisible() {
		final List<WebElement> sidebarCandidates = driver.findElements(By.cssSelector("aside, nav"));
		for (final WebElement candidate : sidebarCandidates) {
			if (candidate.isDisplayed()) {
				return true;
			}
		}
		return isVisibleAny(Arrays.asList("Negocio", "Mi Negocio"));
	}

	private void captureScreenshot(final String fileName) throws IOException {
		final File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
		Files.copy(screenshot.toPath(), evidenceDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
	}

	private void captureScreenshotSafe(final String fileName) {
		try {
			captureScreenshot(fileName);
		} catch (final Exception ignored) {
			// Best-effort evidence capture only.
		}
	}

	private void waitForUiLoad() throws InterruptedException {
		wait.until(webDriver -> {
			try {
				final Object state = ((JavascriptExecutor) webDriver).executeScript("return document.readyState");
				return "complete".equals(state) || "interactive".equals(state);
			} catch (final Exception ignored) {
				return true;
			}
		});
		Thread.sleep(500);
	}

	private void scrollIntoView(final WebElement element) {
		try {
			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", element);
		} catch (final Exception ignored) {
			// Best-effort scrolling.
		}
	}

	private void writeFinalReport() throws IOException {
		final List<String> lines = new ArrayList<>();
		lines.add("saleads_mi_negocio_full_test");
		lines.add("generated_at_utc=" + OffsetDateTime.now());

		for (final String key : REPORT_ORDER) {
			final boolean pass = Boolean.TRUE.equals(report.get(key));
			lines.add(key + ": " + (pass ? "PASS" : "FAIL"));
		}
		for (final Map.Entry<String, String> legalUrlEntry : legalUrls.entrySet()) {
			lines.add(legalUrlEntry.getKey() + ": " + legalUrlEntry.getValue());
		}

		final Path reportPath = evidenceDir.resolve("final-report.txt");
		Files.write(reportPath, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING);

		for (final String line : lines) {
			System.out.println(line);
		}
	}

	private boolean allStepsPassed() {
		for (final String key : REPORT_ORDER) {
			if (!Boolean.TRUE.equals(report.get(key))) {
				return false;
			}
		}
		return true;
	}

	private String buildSummary() {
		final List<String> lines = new ArrayList<>();
		for (final String key : REPORT_ORDER) {
			lines.add(key + "=" + (Boolean.TRUE.equals(report.get(key)) ? "PASS" : "FAIL"));
		}
		for (final Map.Entry<String, String> legalUrlEntry : legalUrls.entrySet()) {
			lines.add(legalUrlEntry.getKey() + "=" + legalUrlEntry.getValue());
		}
		return String.join("\n", lines);
	}

	private String bodyText() {
		try {
			return driver.findElement(By.tagName("body")).getText();
		} catch (final Exception ignored) {
			return "";
		}
	}

	private String safeText(final WebElement element) {
		try {
			return element.getText();
		} catch (final Exception ignored) {
			return "";
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
		final String[] parts = value.split("'");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				builder.append(",\"'\",");
			}
			builder.append("'").append(parts[i]).append("'");
		}
		builder.append(")");
		return builder.toString();
	}

	private List<String> textVariants(final String text) {
		final Set<String> variants = new LinkedHashSet<>();
		variants.add(text);
		final String withoutAccents = stripAccents(text);
		variants.add(withoutAccents);
		return new ArrayList<>(variants);
	}

	private String stripAccents(final String value) {
		return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
	}

	private boolean containsNormalized(final String source, final String target) {
		if (source == null || target == null) {
			return false;
		}
		final String normalizedSource = stripAccents(source).toLowerCase();
		final String normalizedTarget = stripAccents(target).toLowerCase();
		return normalizedSource.contains(normalizedTarget);
	}

	private String trimToNull(final String value) {
		if (value == null) {
			return null;
		}
		final String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String slug(final String value) {
		return value.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	@FunctionalInterface
	private interface Step {
		boolean run() throws Exception;
	}
}
