const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const TEST_NAME = "saleads_mi_negocio_full_test";
const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function sanitizeFileName(name) {
  return name
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");
}

async function waitForUiToSettle(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
}

async function clickByVisibleText(page, textRegex) {
  const options = [
    page.getByRole("button", { name: textRegex }),
    page.getByRole("link", { name: textRegex }),
    page.getByText(textRegex).first(),
  ];

  for (const locator of options) {
    if (await locator.count()) {
      await expect(locator.first()).toBeVisible();
      await locator.first().click();
      await waitForUiToSettle(page);
      return true;
    }
  }

  return false;
}

async function firstVisibleByText(page, textRegex) {
  const options = [
    page.getByRole("button", { name: textRegex }),
    page.getByRole("link", { name: textRegex }),
    page.getByText(textRegex),
  ];

  for (const locator of options) {
    const count = await locator.count();
    for (let i = 0; i < count; i += 1) {
      const candidate = locator.nth(i);
      if (await candidate.isVisible().catch(() => false)) {
        return candidate;
      }
    }
  }

  return null;
}

async function ensureOnLoginPage(page) {
  const url = process.env.SALEADS_URL;

  if (url) {
    await page.goto(url, { waitUntil: "domcontentloaded" });
    await waitForUiToSettle(page);
    return;
  }

  if (page.url() === "about:blank") {
    throw new Error(
      "No SALEADS_URL provided and current page is about:blank. " +
        "Set SALEADS_URL to the current environment login page."
    );
  }
}

async function openLegalDocument(page, context, linkText, screenshotPath) {
  const link = page.getByRole("link", { name: new RegExp(linkText, "i") }).first();
  await expect(link).toBeVisible();

  const originalPage = page;
  const originalUrl = page.url();
  const newTabPromise = context.waitForEvent("page", { timeout: 5000 }).catch(() => null);

  await link.click();
  let targetPage = await newTabPromise;

  if (targetPage) {
    await targetPage.waitForLoadState("domcontentloaded");
    await targetPage.bringToFront();
  } else {
    targetPage = page;
    await waitForUiToSettle(targetPage);
  }

  await expect(
    targetPage.getByRole("heading", { name: new RegExp(linkText, "i") }).first()
  ).toBeVisible({ timeout: 15000 });

  const legalBodyText = (await targetPage.locator("body").innerText()).trim();
  if (legalBodyText.length < 150) {
    throw new Error(`Legal content for "${linkText}" appears too short.`);
  }

  await targetPage.screenshot({ path: screenshotPath, fullPage: true });
  const finalUrl = targetPage.url();

  if (targetPage !== originalPage) {
    await targetPage.close();
    await originalPage.bringToFront();
  } else if (finalUrl !== originalUrl) {
    await originalPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUiToSettle(originalPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const artifactsDir = path.join(
    process.cwd(),
    "test-results",
    TEST_NAME
  );
  fs.mkdirSync(artifactsDir, { recursive: true });

  const report = {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Informaci\u00f3n General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "T\u00e9rminos y Condiciones": "FAIL",
    "Pol\u00edtica de Privacidad": "FAIL",
    evidence: {
      screenshots: {},
      urls: {},
    },
    errors: [],
  };

  const capture = async (label, fullPage = false) => {
    const fileName = `${sanitizeFileName(label)}.png`;
    const screenshotPath = path.join(artifactsDir, fileName);
    await page.screenshot({ path: screenshotPath, fullPage });
    report.evidence.screenshots[label] = screenshotPath;
  };

  try {
    // Step 1: Login with Google
    await ensureOnLoginPage(page);
    await waitForUiToSettle(page);

    const loginButton =
      (await firstVisibleByText(page, /sign in with google|iniciar con google|google/i)) ||
      (await firstVisibleByText(page, /iniciar sesi[o\u00f3]n|sign in|login/i));

    if (!loginButton) {
      throw new Error("Could not find a login button by visible text.");
    }

    const googlePopupPromise = context
      .waitForEvent("page", { timeout: 10000 })
      .catch(() => null);
    await loginButton.click();
    const googlePopup = await googlePopupPromise;
    const authPage = googlePopup || page;
    await waitForUiToSettle(authPage);

    const accountSelector = authPage.getByText(GOOGLE_ACCOUNT_EMAIL).first();
    if (await accountSelector.count()) {
      await accountSelector.click();
      await waitForUiToSettle(authPage);
    }

    if (googlePopup) {
      await page.bringToFront();
      await waitForUiToSettle(page);
    }

    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 30000 });
    report.Login = "PASS";
    await capture("dashboard_loaded");

    // Step 2: Open Mi Negocio menu
    const negocioClicked = await clickByVisibleText(page, /negocio/i);
    const miNegocioClicked = await clickByVisibleText(page, /mi negocio/i);

    if (!negocioClicked && !miNegocioClicked) {
      throw new Error("Could not find Negocio / Mi Negocio menu by visible text.");
    }

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();
    report["Mi Negocio menu"] = "PASS";
    await capture("mi_negocio_menu_expanded");

    // Step 3: Validate Agregar Negocio modal
    await page.getByText(/agregar negocio/i).first().click();
    await waitForUiToSettle(page);

    const modal = page.getByRole("dialog").filter({ hasText: /crear nuevo negocio/i }).first();
    await expect(modal).toBeVisible();
    await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();
    await expect(modal.getByLabel(/nombre del negocio/i)).toBeVisible();
    await expect(modal.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /crear negocio/i })).toBeVisible();
    await capture("agregar_negocio_modal");

    await modal.getByLabel(/nombre del negocio/i).fill("Negocio Prueba Automatizacion");
    await modal.getByRole("button", { name: /cancelar/i }).click();
    await waitForUiToSettle(page);
    report["Agregar Negocio modal"] = "PASS";

    // Step 4: Open Administrar Negocios
    await clickByVisibleText(page, /mi negocio/i);
    await page.getByText(/administrar negocios/i).first().click();
    await waitForUiToSettle(page);

    await expect(page.getByText(/informaci[o\u00f3]n general/i).first()).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByText(/secci[o\u00f3]n legal/i).first()).toBeVisible();
    report["Administrar Negocios view"] = "PASS";
    await capture("administrar_negocios_page", true);

    // Step 5: Validate Informacion General
    const infoSection = page
      .locator("section,div")
      .filter({ hasText: /informaci[o\u00f3]n general/i })
      .first();
    await expect(infoSection).toBeVisible();
    await expect(infoSection.getByText(/@/).first()).toBeVisible();
    await expect(infoSection.getByText(/business plan/i)).toBeVisible();
    await expect(infoSection.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
    const hasNameLabel = await infoSection.getByText(/nombre|name/i).first().count();
    if (!hasNameLabel) {
      // Fallback: require at least one non-empty profile-like line.
      const profileText = (await infoSection.innerText()).trim();
      if (profileText.length < 30) {
        throw new Error("Could not confidently validate visible user name in Informacion General.");
      }
    }
    report["Informaci\u00f3n General"] = "PASS";

    // Step 6: Validate Detalles de la Cuenta
    const accountDetails = page
      .locator("section,div")
      .filter({ hasText: /detalles de la cuenta/i })
      .first();
    await expect(accountDetails).toBeVisible();
    await expect(accountDetails.getByText(/cuenta creada/i)).toBeVisible();
    await expect(accountDetails.getByText(/estado activo/i)).toBeVisible();
    await expect(accountDetails.getByText(/idioma seleccionado/i)).toBeVisible();
    report["Detalles de la Cuenta"] = "PASS";

    // Step 7: Validate Tus Negocios
    const businessSection = page
      .locator("section,div")
      .filter({ hasText: /tus negocios/i })
      .first();
    await expect(businessSection).toBeVisible();
    await expect(businessSection.getByRole("button", { name: /agregar negocio/i })).toBeVisible();
    await expect(businessSection.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    const businessListVisible = (await businessSection.locator("li, article, tr").count()) > 0;
    if (!businessListVisible) {
      throw new Error("Business list is not visible in Tus Negocios section.");
    }
    report["Tus Negocios"] = "PASS";

    // Step 8: Validate Terminos y Condiciones
    const termsUrl = await openLegalDocument(
      page,
      context,
      "T\u00e9rminos y Condiciones",
      path.join(artifactsDir, "terminos_y_condiciones.png")
    );
    report["T\u00e9rminos y Condiciones"] = "PASS";
    report.evidence.screenshots["T\u00e9rminos y Condiciones"] = path.join(
      artifactsDir,
      "terminos_y_condiciones.png"
    );
    report.evidence.urls["T\u00e9rminos y Condiciones"] = termsUrl;

    // Step 9: Validate Politica de Privacidad
    const privacyUrl = await openLegalDocument(
      page,
      context,
      "Pol\u00edtica de Privacidad",
      path.join(artifactsDir, "politica_de_privacidad.png")
    );
    report["Pol\u00edtica de Privacidad"] = "PASS";
    report.evidence.screenshots["Pol\u00edtica de Privacidad"] = path.join(
      artifactsDir,
      "politica_de_privacidad.png"
    );
    report.evidence.urls["Pol\u00edtica de Privacidad"] = privacyUrl;
  } catch (error) {
    report.errors.push(error.message);
    throw error;
  } finally {
    const reportPath = path.join(artifactsDir, `${TEST_NAME}_report.json`);
    fs.writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf-8");
    console.log("\nFinal report:");
    console.table(
      Object.entries(report)
        .filter(([key]) => !["evidence", "errors"].includes(key))
        .map(([key, value]) => ({ step: key, status: value }))
    );
    console.log(`Report file: ${reportPath}`);
  }
});
