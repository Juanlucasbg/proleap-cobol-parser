const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");
const path = require("path");

const REPORT_DIR = path.join(process.cwd(), "artifacts", "saleads-mi-negocio");
const REPORT_PATH = path.join(REPORT_DIR, "report.json");
const START_URL_ENV_NAMES = ["SALEADS_URL", "SALEADS_LOGIN_URL", "BASE_URL"];

const accountEmail = "juanlucasbarbiergarzon@gmail.com";

async function ensureDir(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

function sanitize(name) {
  return name
    .toLowerCase()
    .replace(/\s+/g, "-")
    .replace(/[^a-z0-9\-]/g, "")
    .replace(/-+/g, "-");
}

async function screenshot(page, label) {
  const filePath = path.join(REPORT_DIR, `${sanitize(label)}.png`);
  await page.screenshot({ path: filePath, fullPage: true });
  return filePath;
}

function getStartUrl() {
  for (const envName of START_URL_ENV_NAMES) {
    const value = process.env[envName];
    if (value && value.trim()) {
      return value.trim();
    }
  }
  return "";
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle").catch(() => {});
}

function initResults() {
  return {
    Login: { status: "FAIL", details: "", evidence: [] },
    "Mi Negocio menu": { status: "FAIL", details: "", evidence: [] },
    "Agregar Negocio modal": { status: "FAIL", details: "", evidence: [] },
    "Administrar Negocios view": { status: "FAIL", details: "", evidence: [] },
    "Información General": { status: "FAIL", details: "", evidence: [] },
    "Detalles de la Cuenta": { status: "FAIL", details: "", evidence: [] },
    "Tus Negocios": { status: "FAIL", details: "", evidence: [] },
    "Términos y Condiciones": { status: "FAIL", details: "", evidence: [], url: "" },
    "Política de Privacidad": { status: "FAIL", details: "", evidence: [], url: "" }
  };
}

async function firstVisibleActionLocator(page, labels) {
  for (const label of labels) {
    const expression = new RegExp(label, "i");
    const candidates = [
      page.getByRole("button", { name: expression }).first(),
      page.getByRole("link", { name: expression }).first(),
      page.getByText(expression).first()
    ];
    for (const candidate of candidates) {
      if (await candidate.isVisible().catch(() => false)) {
        return candidate;
      }
    }
  }
  return null;
}

async function clickFirstVisible(page, labels) {
  const target = await firstVisibleActionLocator(page, labels);
  if (target) {
    await target.click();
    await waitForUiLoad(page);
    return true;
  }
  return false;
}

async function openLinkPossiblyNewTab(page, linkText) {
  const link = page.getByText(new RegExp(`^${linkText}$`, "i")).first();
  await expect(link, `${linkText} link should be visible`).toBeVisible({ timeout: 15000 });

  const popupPromise = page.waitForEvent("popup", { timeout: 5000 }).catch(() => null);
  await link.click();
  await waitForUiLoad(page);
  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await popup.waitForLoadState("networkidle").catch(() => {});
    return { targetPage: popup, openedInNewTab: true };
  }

  return { targetPage: page, openedInNewTab: false };
}

async function returnToApplication(page, fallbackUrl) {
  const negocioMenu = page.getByText(/^Mi Negocio$/i).first();
  if (await negocioMenu.isVisible().catch(() => false)) {
    return;
  }

  await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
  await waitForUiLoad(page);
  if (await negocioMenu.isVisible().catch(() => false)) {
    return;
  }

  if (fallbackUrl) {
    await page.goto(fallbackUrl, { waitUntil: "domcontentloaded" });
    await waitForUiLoad(page);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  test.setTimeout(10 * 60 * 1000);
  await ensureDir(REPORT_DIR);
  const results = initResults();
  const startUrl = getStartUrl();
  if (startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
    await waitForUiLoad(page);
  }
  const startedUrl = page.url();
  let administrarNegociosUrl = "";

  // Step 1: Login with Google
  try {
    const loginTrigger = await firstVisibleActionLocator(page, [
      "sign in with google",
      "iniciar sesion con google",
      "iniciar sesión con google",
      "google"
    ]);
    expect(loginTrigger).toBeTruthy();

    const loginPopupPromise = page.waitForEvent("popup", { timeout: 8000 }).catch(() => null);
    await loginTrigger.click();
    await waitForUiLoad(page);
    const loginPopup = await loginPopupPromise;
    const authPage = loginPopup || page;
    await authPage.waitForLoadState("domcontentloaded");
    await authPage.waitForLoadState("networkidle").catch(() => {});

    // Optional Google account picker handling.
    const accountChoice = authPage.getByText(new RegExp(accountEmail, "i")).first();
    if (await accountChoice.isVisible().catch(() => false)) {
      await accountChoice.click();
      await waitForUiLoad(authPage);
    }

    if (loginPopup) {
      // Some Google flows keep popup open while app logs in on original page.
      await page.waitForLoadState("domcontentloaded");
      await page.waitForLoadState("networkidle").catch(() => {});
    }

    const sidebar = page
      .locator("aside,nav")
      .filter({ hasText: /negocio|dashboard|inicio/i })
      .first();

    const sidebarVisible = await sidebar.isVisible().catch(() => false);
    if (!sidebarVisible) {
      await expect(page.getByText(/Mi Negocio|Negocio/i).first()).toBeVisible({ timeout: 60000 });
    }
    const shot = await screenshot(page, "step-1-dashboard-loaded");
    results.Login = { status: "PASS", details: "Main interface and sidebar are visible.", evidence: [shot] };
  } catch (error) {
    const shot = await screenshot(page, "step-1-login-failed").catch(() => "");
    results.Login = { status: "FAIL", details: String(error), evidence: shot ? [shot] : [] };
  }

  // Step 2: Open Mi Negocio menu
  try {
    const negocioSection = page.getByText(/^Negocio$/i).first();
    await expect(negocioSection).toBeVisible({ timeout: 30000 });
    await negocioSection.click();
    await waitForUiLoad(page);

    const miNegocio = page.getByText(/^Mi Negocio$/i).first();
    await expect(miNegocio).toBeVisible({ timeout: 15000 });
    await miNegocio.click();
    await waitForUiLoad(page);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible({ timeout: 15000 });
    const shot = await screenshot(page, "step-2-mi-negocio-expanded");
    results["Mi Negocio menu"] = { status: "PASS", details: "Submenu expanded with expected options.", evidence: [shot] };
  } catch (error) {
    const shot = await screenshot(page, "step-2-mi-negocio-failed").catch(() => "");
    results["Mi Negocio menu"] = { status: "FAIL", details: String(error), evidence: shot ? [shot] : [] };
  }

  // Step 3: Validate Agregar Negocio modal
  try {
    await page.getByText(/^Agregar Negocio$/i).first().click();
    await waitForUiLoad(page);

    await expect(page.getByText(/^Crear Nuevo Negocio$/i).first()).toBeVisible({ timeout: 15000 });
    const input = page.getByLabel(/Nombre del Negocio/i).first();
    await expect(input).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /^Cancelar$/i }).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i }).first()).toBeVisible({ timeout: 15000 });

    await input.click();
    await input.fill("Negocio Prueba Automatización");
    const shot = await screenshot(page, "step-3-modal-crear-negocio");
    await page.getByRole("button", { name: /^Cancelar$/i }).first().click();
    await waitForUiLoad(page);

    results["Agregar Negocio modal"] = { status: "PASS", details: "Modal validated and closed with Cancelar.", evidence: [shot] };
  } catch (error) {
    const shot = await screenshot(page, "step-3-agregar-negocio-failed").catch(() => "");
    results["Agregar Negocio modal"] = { status: "FAIL", details: String(error), evidence: shot ? [shot] : [] };
  }

  // Step 4: Open Administrar Negocios
  try {
    const administrar = page.getByText(/^Administrar Negocios$/i).first();
    if (!(await administrar.isVisible().catch(() => false))) {
      const miNegocio = page.getByText(/^Mi Negocio$/i).first();
      if (await miNegocio.isVisible().catch(() => false)) {
        await miNegocio.click();
        await waitForUiLoad(page);
      }
    }

    await page.getByText(/^Administrar Negocios$/i).first().click();
    await waitForUiLoad(page);

    await expect(page.getByText(/^Información General$/i).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/^Detalles de la Cuenta$/i).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/^Tus Negocios$/i).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible({ timeout: 30000 });
    administrarNegociosUrl = page.url();
    const shot = await screenshot(page, "step-4-administrar-negocios");
    results["Administrar Negocios view"] = { status: "PASS", details: "Account page loaded with key sections.", evidence: [shot] };
  } catch (error) {
    const shot = await screenshot(page, "step-4-administrar-negocios-failed").catch(() => "");
    results["Administrar Negocios view"] = { status: "FAIL", details: String(error), evidence: shot ? [shot] : [] };
  }

  // Step 5: Validate Información General
  try {
    const section = page.locator("section,div").filter({ hasText: /Información General/i }).first();
    await expect(section).toBeVisible({ timeout: 15000 });
    await expect(section.getByText(/@/)).toBeVisible({ timeout: 15000 });
    await expect(section.getByText(/BUSINESS PLAN/i)).toBeVisible({ timeout: 15000 });
    await expect(section.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible({ timeout: 15000 });
    results["Información General"] = { status: "PASS", details: "Information section fields are visible.", evidence: [] };
  } catch (error) {
    results["Información General"] = { status: "FAIL", details: String(error), evidence: [] };
  }

  // Step 6: Validate Detalles de la Cuenta
  try {
    const section = page.locator("section,div").filter({ hasText: /Detalles de la Cuenta/i }).first();
    await expect(section).toBeVisible({ timeout: 15000 });
    await expect(section.getByText(/Cuenta creada/i)).toBeVisible({ timeout: 15000 });
    await expect(section.getByText(/Estado activo/i)).toBeVisible({ timeout: 15000 });
    await expect(section.getByText(/Idioma seleccionado/i)).toBeVisible({ timeout: 15000 });
    results["Detalles de la Cuenta"] = { status: "PASS", details: "Account details fields are visible.", evidence: [] };
  } catch (error) {
    results["Detalles de la Cuenta"] = { status: "FAIL", details: String(error), evidence: [] };
  }

  // Step 7: Validate Tus Negocios
  try {
    const section = page.locator("section,div").filter({ hasText: /Tus Negocios/i }).first();
    await expect(section).toBeVisible({ timeout: 15000 });
    await expect(section.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible({ timeout: 15000 });
    await expect(section.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible({ timeout: 15000 });
    results["Tus Negocios"] = { status: "PASS", details: "Business list and controls are visible.", evidence: [] };
  } catch (error) {
    results["Tus Negocios"] = { status: "FAIL", details: String(error), evidence: [] };
  }

  // Step 8: Validate Términos y Condiciones
  try {
    const { targetPage, openedInNewTab } = await openLinkPossiblyNewTab(page, "Términos y Condiciones");
    await expect(targetPage.getByText(/Términos y Condiciones/i).first()).toBeVisible({ timeout: 30000 });
    await expect(targetPage.locator("main,article,body").getByText(/[A-Za-zÁÉÍÓÚáéíóúñÑ]{10,}/).first()).toBeVisible({
      timeout: 30000
    });

    const shot = await screenshot(targetPage, "step-8-terminos-y-condiciones");
    const finalUrl = targetPage.url();
    results["Términos y Condiciones"] = {
      status: "PASS",
      details: "Terms page and legal content are visible.",
      evidence: [shot],
      url: finalUrl
    };

    if (openedInNewTab) {
      await targetPage.close();
      await page.bringToFront();
    } else {
      await returnToApplication(page, administrarNegociosUrl);
    }
  } catch (error) {
    const shot = await screenshot(page, "step-8-terminos-failed").catch(() => "");
    results["Términos y Condiciones"] = {
      status: "FAIL",
      details: String(error),
      evidence: shot ? [shot] : [],
      url: page.url()
    };
  }

  // Step 9: Validate Política de Privacidad
  try {
    const { targetPage, openedInNewTab } = await openLinkPossiblyNewTab(page, "Política de Privacidad");
    await expect(targetPage.getByText(/Política de Privacidad/i).first()).toBeVisible({ timeout: 30000 });
    await expect(targetPage.locator("main,article,body").getByText(/[A-Za-zÁÉÍÓÚáéíóúñÑ]{10,}/).first()).toBeVisible({
      timeout: 30000
    });

    const shot = await screenshot(targetPage, "step-9-politica-de-privacidad");
    const finalUrl = targetPage.url();
    results["Política de Privacidad"] = {
      status: "PASS",
      details: "Privacy page and legal content are visible.",
      evidence: [shot],
      url: finalUrl
    };

    if (openedInNewTab) {
      await targetPage.close();
      await page.bringToFront();
    } else {
      await returnToApplication(page, administrarNegociosUrl);
    }
  } catch (error) {
    const shot = await screenshot(page, "step-9-politica-failed").catch(() => "");
    results["Política de Privacidad"] = {
      status: "FAIL",
      details: String(error),
      evidence: shot ? [shot] : [],
      url: page.url()
    };
  }

  // Step 10: Final report
  const report = {
    testName: "saleads_mi_negocio_full_test",
    startedAt: new Date().toISOString(),
    initialUrl: startedUrl,
    finalAppUrl: page.url(),
    results
  };

  await fs.writeFile(REPORT_PATH, JSON.stringify(report, null, 2), "utf8");
  await test.info().attach("saleads-mi-negocio-report", {
    path: REPORT_PATH,
    contentType: "application/json"
  });

  const failures = Object.entries(results).filter(([, value]) => value.status !== "PASS");
  expect(
    failures,
    `Validation failures: ${failures.map(([key, value]) => `${key}: ${value.details}`).join(" | ")}`
  ).toEqual([]);
});
