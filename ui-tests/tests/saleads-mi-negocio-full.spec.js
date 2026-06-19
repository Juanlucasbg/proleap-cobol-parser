const fs = require("node:fs/promises");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

const TEST_NAME = "saleads_mi_negocio_full_test";
const DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad",
];

function sanitizeFileName(value) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");
}

async function waitForUiToSettle(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});
  await page.waitForTimeout(1_000);
}

async function clickAndWait(page, locator) {
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await Promise.all([
    page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {}),
    locator.click(),
  ]);
  await page.waitForTimeout(1_000);
}

async function firstVisibleLocator(candidates) {
  for (const locator of candidates) {
    const candidate = locator.first();
    if (await candidate.isVisible().catch(() => false)) {
      return candidate;
    }
  }
  throw new Error("No visible locator found for expected element.");
}

async function capture(page, artifactsDir, name, fullPage = false) {
  const fileName = `${sanitizeFileName(name)}.png`;
  const screenshotPath = path.join(artifactsDir, fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

function initializeReport() {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
}

test(TEST_NAME, async ({ page, context }) => {
  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.BASE_URL;
  const googleAccount = process.env.SALEADS_GOOGLE_ACCOUNT || DEFAULT_GOOGLE_ACCOUNT;
  const expectedUserName = process.env.SALEADS_EXPECTED_USER_NAME || "";

  if (!loginUrl) {
    throw new Error(
      "Missing environment URL. Set SALEADS_LOGIN_URL (or BASE_URL) to the SaleADS login page of the current environment."
    );
  }

  const artifactsDir = path.join(__dirname, "..", "artifacts", `run-${Date.now()}`);
  await fs.mkdir(artifactsDir, { recursive: true });

  const report = initializeReport();
  const errors = [];
  const evidence = {
    screenshots: [],
    finalUrls: {
      terminosYCondiciones: null,
      politicaDePrivacidad: null,
    },
  };

  const runStep = async (label, callback) => {
    try {
      await callback();
      report[label] = "PASS";
    } catch (error) {
      report[label] = "FAIL";
      errors.push(`${label}: ${error.message}`);
    }
  };

  const ensureInMiNegocio = async () => {
    const negocioTrigger = await firstVisibleLocator([
      page.getByRole("button", { name: /^negocio$/i }),
      page.getByRole("link", { name: /^negocio$/i }),
      page.getByText(/^Negocio$/i),
    ]);
    await clickAndWait(page, negocioTrigger);

    const miNegocioOption = await firstVisibleLocator([
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i),
    ]);
    await clickAndWait(page, miNegocioOption);
  };

  const openLegalPage = async (linkNameRegex, headingRegex, screenshotName) => {
    const legalLink = await firstVisibleLocator([
      page.getByRole("link", { name: linkNameRegex }),
      page.getByRole("button", { name: linkNameRegex }),
      page.getByText(linkNameRegex),
    ]);

    const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
    await clickAndWait(page, legalLink);
    let targetPage = await popupPromise;

    if (targetPage) {
      await targetPage.waitForLoadState("domcontentloaded", { timeout: 45_000 }).catch(() => {});
      await targetPage.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
    } else {
      targetPage = page;
      await waitForUiToSettle(targetPage);
    }

    await expect(targetPage.getByRole("heading", { name: headingRegex }).first()).toBeVisible();
    const legalContent = await targetPage.locator("body").innerText();
    if (legalContent.trim().length < 200) {
      throw new Error("Legal content appears too short.");
    }

    const shot = await capture(targetPage, artifactsDir, screenshotName, true);
    evidence.screenshots.push(shot);
    const finalUrl = targetPage.url();

    if (targetPage !== page) {
      await targetPage.close().catch(() => {});
      await page.bringToFront();
      await waitForUiToSettle(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
      await waitForUiToSettle(page);
    }

    return finalUrl;
  };

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUiToSettle(page);

  await runStep("Login", async () => {
    const signInWithGoogle = await firstVisibleLocator([
      page.getByRole("button", { name: /google/i }),
      page.getByRole("link", { name: /google/i }),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
    ]);

    const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);
    await clickAndWait(page, signInWithGoogle);
    const popup = await popupPromise;

    if (popup) {
      await popup.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
      const accountOption = popup.getByText(googleAccount, { exact: true });
      if (await accountOption.isVisible({ timeout: 8_000 }).catch(() => false)) {
        await accountOption.click();
      }
      await popup.waitForEvent("close", { timeout: 30_000 }).catch(() => {});
    } else {
      const accountOption = page.getByText(googleAccount, { exact: true });
      if (await accountOption.isVisible({ timeout: 8_000 }).catch(() => false)) {
        await clickAndWait(page, accountOption);
      }
    }

    await waitForUiToSettle(page);
    const sidebar = await firstVisibleLocator([
      page.locator("aside"),
      page.getByRole("navigation"),
      page.locator("[class*='sidebar']"),
    ]);
    await expect(sidebar).toBeVisible();

    const dashboardShot = await capture(page, artifactsDir, "01-dashboard-loaded", true);
    evidence.screenshots.push(dashboardShot);
  });

  await runStep("Mi Negocio menu", async () => {
    await ensureInMiNegocio();
    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();

    const menuShot = await capture(page, artifactsDir, "02-mi-negocio-menu-expanded");
    evidence.screenshots.push(menuShot);
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await firstVisibleLocator([
      page.getByRole("button", { name: /agregar negocio/i }),
      page.getByRole("link", { name: /agregar negocio/i }),
      page.getByText(/agregar negocio/i),
    ]);
    await clickAndWait(page, agregarNegocio);

    const modalTitle = page.getByRole("heading", { name: /crear nuevo negocio/i }).first();
    await expect(modalTitle).toBeVisible();

    const nombreInput = await firstVisibleLocator([
      page.getByLabel(/nombre del negocio/i),
      page.getByPlaceholder(/nombre del negocio/i),
      page.locator("input[name*='negocio' i]"),
    ]);
    await expect(nombreInput).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i }).first()).toBeVisible();

    const modalShot = await capture(page, artifactsDir, "03-crear-nuevo-negocio-modal");
    evidence.screenshots.push(modalShot);

    await nombreInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page, page.getByRole("button", { name: /cancelar/i }).first());
    await expect(modalTitle).not.toBeVisible({ timeout: 10_000 });
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarVisible = await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      await ensureInMiNegocio();
    }

    const administrarNegocios = await firstVisibleLocator([
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i),
    ]);
    await clickAndWait(page, administrarNegocios);
    await waitForUiToSettle(page);

    await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByText(/secci[oó]n legal/i).first()).toBeVisible();

    const accountShot = await capture(page, artifactsDir, "04-administrar-negocios-cuenta", true);
    evidence.screenshots.push(accountShot);
  });

  await runStep("Información General", async () => {
    await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible();
    await expect(page.getByText(/business plan/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible();

    const bodyText = await page.locator("body").innerText();
    if (!/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(bodyText)) {
      throw new Error("User email is not visible.");
    }

    if (expectedUserName) {
      const expectedNameRegex = new RegExp(expectedUserName.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i");
      if (!expectedNameRegex.test(bodyText)) {
        throw new Error(`Expected user name '${expectedUserName}' is not visible.`);
      }
    } else if (!/juan|lucas|barbier|garzon/i.test(bodyText)) {
      throw new Error("Could not verify the user name visibility.");
    }
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
  });

  await runStep("Términos y Condiciones", async () => {
    evidence.finalUrls.terminosYCondiciones = await openLegalPage(
      /t[ée]rminos y condiciones/i,
      /t[ée]rminos y condiciones/i,
      "05-terminos-y-condiciones"
    );
  });

  await runStep("Política de Privacidad", async () => {
    evidence.finalUrls.politicaDePrivacidad = await openLegalPage(
      /pol[íi]tica de privacidad/i,
      /pol[íi]tica de privacidad/i,
      "06-politica-de-privacidad"
    );
  });

  const finalReport = {
    testName: TEST_NAME,
    generatedAt: new Date().toISOString(),
    targetLoginUrl: loginUrl,
    report,
    evidence,
    errors,
  };

  const reportPath = path.join(artifactsDir, "final-report.json");
  await fs.writeFile(reportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf8");
  console.log(JSON.stringify(finalReport, null, 2));

  expect(errors, `Validation errors:\n${errors.join("\n")}`).toEqual([]);
});
