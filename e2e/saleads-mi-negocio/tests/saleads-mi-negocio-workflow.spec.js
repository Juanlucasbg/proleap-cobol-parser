const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const SCREENSHOTS_DIR = path.resolve(__dirname, "..", "artifacts", "screenshots");
const REPORT_PATH = path.resolve(__dirname, "..", "artifacts", "final-report.json");

function ensureDir(dirPath) {
  fs.mkdirSync(dirPath, { recursive: true });
}

function nowStamp() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

function normalizeText(value) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/\s+/g, " ")
    .trim()
    .toLowerCase();
}

function textVariants(...values) {
  return values.flatMap((v) => {
    const source = (v || "").trim();
    if (!source) return [];

    const variants = new Set([source]);
    variants.add(source.normalize("NFD").replace(/[\u0300-\u036f]/g, ""));
    return [...variants];
  });
}

async function waitForUi(page, timeout = 20_000) {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout });
  } catch (_) {
    // Some pages keep long-lived connections; domcontentloaded is enough.
  }
}

async function clickByVisibleText(page, labels, options = {}) {
  const timeout = options.timeout || 20_000;
  const list = Array.isArray(labels) ? labels : [labels];

  for (const label of list) {
    const escaped = label.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const strictRegex = new RegExp(`^\\s*${escaped}\\s*$`, "i");
    const looseRegex = new RegExp(escaped, "i");

    const candidates = [
      page.getByRole("button", { name: strictRegex }),
      page.getByRole("link", { name: strictRegex }),
      page.getByRole("menuitem", { name: strictRegex }),
      page.getByRole("option", { name: strictRegex }),
      page.getByText(strictRegex),
      page.getByRole("button", { name: looseRegex }),
      page.getByRole("link", { name: looseRegex }),
      page.getByRole("menuitem", { name: looseRegex }),
      page.getByText(looseRegex)
    ];

    for (const locator of candidates) {
      const first = locator.first();
      if (await first.isVisible({ timeout: 800 }).catch(() => false)) {
        await first.click({ timeout });
        await waitForUi(page);
        return true;
      }
    }
  }

  return false;
}

async function ensureVisibleText(page, labels, timeout = 20_000) {
  const list = Array.isArray(labels) ? labels : [labels];
  const startedAt = Date.now();

  while (Date.now() - startedAt < timeout) {
    const bodyText = await page.locator("body").innerText().catch(() => "");
    const normalizedBody = normalizeText(bodyText || "");

    for (const label of list) {
      const normalizedLabel = normalizeText(label);
      if (normalizedBody.includes(normalizedLabel)) {
        return true;
      }
    }

    await page.waitForTimeout(500);
  }

  return false;
}

async function isAnyEmailVisible(page) {
  const bodyText = await page.locator("body").innerText().catch(() => "");
  return /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(bodyText || "");
}

async function isLikelyUserNameVisibleNearEmail(page) {
  const emailNode = page
    .locator("text=/[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}/i")
    .first();

  const emailVisible = await emailNode.isVisible({ timeout: 4_000 }).catch(() => false);
  if (!emailVisible) {
    return false;
  }

  return emailNode.evaluate((el) => {
    const normalize = (value) =>
      value
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "")
        .replace(/\s+/g, " ")
        .trim()
        .toLowerCase();

    const container = el.closest("section, article, div") || el.parentElement || document.body;
    const lines = (container.innerText || "")
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean);

    return lines.some((line) => {
      const plain = normalize(line);
      if (plain.includes("@")) return false;
      if (
        plain.includes("informacion general") ||
        plain.includes("business plan") ||
        plain.includes("cambiar plan") ||
        plain.includes("plan")
      ) {
        return false;
      }
      return /^[a-zA-ZÀ-ÿ][a-zA-ZÀ-ÿ' -]{2,}$/.test(line);
    });
  });
}

function defaultResult() {
  return {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Información General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Términos y Condiciones": "FAIL",
    "Política de Privacidad": "FAIL"
  };
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  ensureDir(SCREENSHOTS_DIR);
  ensureDir(path.dirname(REPORT_PATH));

  const stamp = nowStamp();
  const report = defaultResult();
  const evidence = {
    screenshots: {},
    termsUrl: null,
    privacyUrl: null
  };
  const failures = [];
  let appAccountPageUrl = null;

  const runValidationStep = async (reportField, action) => {
    try {
      await action();
      report[reportField] = "PASS";
    } catch (error) {
      report[reportField] = "FAIL";
      failures.push({
        step: reportField,
        message: error instanceof Error ? error.message : String(error)
      });
    }
  };

  const entryUrl = process.env.SALEADS_URL || process.env.SALEADS_LOGIN_URL;

  if (entryUrl) {
    await page.goto(entryUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else {
    await waitForUi(page);
  }

  // Step 1: Login with Google
  await runValidationStep("Login", async () => {
    const loginLabels = textVariants(
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Continuar con Google",
      "Google"
    );
    const clickedLogin = await clickByVisibleText(page, loginLabels, { timeout: 30_000 });
    expect(clickedLogin).toBeTruthy();

    const accountSelected = await clickByVisibleText(page, [ACCOUNT_EMAIL], { timeout: 8_000 });
    if (accountSelected) {
      await waitForUi(page);
    }

    const mainUiVisible = await Promise.race([
      page
        .locator("aside, nav")
        .first()
        .isVisible({ timeout: 45_000 })
        .catch(() => false),
      ensureVisibleText(page, ["Negocio", "Mi Negocio"], 45_000)
    ]);
    expect(mainUiVisible).toBeTruthy();

    const dashboardShot = path.join(SCREENSHOTS_DIR, `${stamp}-01-dashboard-loaded.png`);
    await page.screenshot({ path: dashboardShot, fullPage: true });
    evidence.screenshots.dashboard = dashboardShot;
  });

  // Step 2: Open Mi Negocio menu
  await runValidationStep("Mi Negocio menu", async () => {
    const negocioClicked = await clickByVisibleText(page, textVariants("Negocio", "Mi Negocio"), { timeout: 20_000 });
    expect(negocioClicked).toBeTruthy();

    const agregarVisible = await ensureVisibleText(page, ["Agregar Negocio"], 15_000);
    const administrarVisible = await ensureVisibleText(page, ["Administrar Negocios"], 15_000);
    expect(agregarVisible && administrarVisible).toBeTruthy();

    const expandedMenuShot = path.join(SCREENSHOTS_DIR, `${stamp}-02-mi-negocio-expanded.png`);
    await page.screenshot({ path: expandedMenuShot, fullPage: true });
    evidence.screenshots.menuExpanded = expandedMenuShot;
  });

  // Step 3: Validate Agregar Negocio modal
  await runValidationStep("Agregar Negocio modal", async () => {
    const agregarClicked = await clickByVisibleText(page, ["Agregar Negocio"], { timeout: 20_000 });
    expect(agregarClicked).toBeTruthy();

    const modalChecks = await Promise.all([
      ensureVisibleText(page, ["Crear Nuevo Negocio"], 20_000),
      ensureVisibleText(page, ["Nombre del Negocio"], 20_000),
      ensureVisibleText(page, ["Tienes 2 de 3 negocios"], 20_000),
      ensureVisibleText(page, ["Cancelar"], 20_000),
      ensureVisibleText(page, ["Crear Negocio"], 20_000)
    ]);
    expect(modalChecks.every(Boolean)).toBeTruthy();

    const modalShot = path.join(SCREENSHOTS_DIR, `${stamp}-03-agregar-negocio-modal.png`);
    await page.screenshot({ path: modalShot, fullPage: true });
    evidence.screenshots.agregarModal = modalShot;

    const negocioNameField = page.getByLabel(/nombre del negocio/i).first();
    if (await negocioNameField.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await negocioNameField.click();
      await negocioNameField.fill("Negocio Prueba Automatización");
    } else {
      const placeholderInput = page.getByPlaceholder(/nombre del negocio/i).first();
      if (await placeholderInput.isVisible({ timeout: 3_000 }).catch(() => false)) {
        await placeholderInput.click();
        await placeholderInput.fill("Negocio Prueba Automatización");
      }
    }

    const cancelClicked = await clickByVisibleText(page, ["Cancelar"], { timeout: 10_000 });
    expect(cancelClicked).toBeTruthy();
  });

  // Step 4: Open Administrar Negocios
  await runValidationStep("Administrar Negocios view", async () => {
    const administrarClicked = await clickByVisibleText(page, ["Administrar Negocios"], { timeout: 12_000 });
    if (!administrarClicked) {
      const reopened = await clickByVisibleText(page, textVariants("Mi Negocio", "Negocio"), { timeout: 12_000 });
      expect(reopened).toBeTruthy();
      const secondTry = await clickByVisibleText(page, ["Administrar Negocios"], { timeout: 12_000 });
      expect(secondTry).toBeTruthy();
    }

    const accountSectionsVisible = await Promise.all([
      ensureVisibleText(page, ["Información General"], 30_000),
      ensureVisibleText(page, ["Detalles de la Cuenta"], 30_000),
      ensureVisibleText(page, ["Tus Negocios"], 30_000),
      ensureVisibleText(page, ["Sección Legal"], 30_000)
    ]);
    expect(accountSectionsVisible.every(Boolean)).toBeTruthy();

    const accountPageShot = path.join(SCREENSHOTS_DIR, `${stamp}-04-administrar-negocios-page.png`);
    await page.screenshot({ path: accountPageShot, fullPage: true });
    evidence.screenshots.accountPage = accountPageShot;
    appAccountPageUrl = page.url();
  });

  // Step 5: Validate Información General
  await runValidationStep("Información General", async () => {
    const [nameVisible, specificEmailVisible, anyEmailVisible, businessPlanVisible, changePlanVisible] = await Promise.all([
      isLikelyUserNameVisibleNearEmail(page),
      ensureVisibleText(page, [ACCOUNT_EMAIL], 20_000),
      isAnyEmailVisible(page),
      ensureVisibleText(page, ["BUSINESS PLAN"], 20_000),
      ensureVisibleText(page, ["Cambiar Plan"], 20_000)
    ]);
    expect(nameVisible).toBeTruthy();
    expect(specificEmailVisible || anyEmailVisible).toBeTruthy();
    expect(businessPlanVisible).toBeTruthy();
    expect(changePlanVisible).toBeTruthy();
  });

  // Step 6: Validate Detalles de la Cuenta
  await runValidationStep("Detalles de la Cuenta", async () => {
    const checks = await Promise.all([
      ensureVisibleText(page, ["Cuenta creada"], 20_000),
      ensureVisibleText(page, ["Estado activo"], 20_000),
      ensureVisibleText(page, ["Idioma seleccionado"], 20_000)
    ]);
    expect(checks.every(Boolean)).toBeTruthy();
  });

  // Step 7: Validate Tus Negocios
  await runValidationStep("Tus Negocios", async () => {
    const checks = await Promise.all([
      ensureVisibleText(page, ["Tus Negocios"], 20_000),
      ensureVisibleText(page, ["Agregar Negocio"], 20_000),
      ensureVisibleText(page, ["Tienes 2 de 3 negocios"], 20_000)
    ]);
    expect(checks.every(Boolean)).toBeTruthy();
  });

  // Step 8: Validate Términos y Condiciones
  await runValidationStep("Términos y Condiciones", async () => {
    const targetLabels = textVariants("Términos y Condiciones", "Terminos y Condiciones");
    const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
    const clicked = await clickByVisibleText(page, targetLabels, { timeout: 20_000 });
    expect(clicked).toBeTruthy();

    const popup = await popupPromise;
    let legalPage = page;

    if (popup) {
      legalPage = popup;
      await waitForUi(legalPage, 25_000);
    } else {
      await waitForUi(page, 25_000);
    }

    const legalChecks = await Promise.all([
      ensureVisibleText(legalPage, targetLabels, 25_000),
      ensureVisibleText(legalPage, ["términos", "terminos", "condiciones"], 25_000)
    ]);
    expect(legalChecks.every(Boolean)).toBeTruthy();

    const termsShot = path.join(SCREENSHOTS_DIR, `${stamp}-08-terminos-y-condiciones.png`);
    await legalPage.screenshot({ path: termsShot, fullPage: true });
    evidence.screenshots.terms = termsShot;
    evidence.termsUrl = legalPage.url();

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
    } else if (appAccountPageUrl && page.url() !== appAccountPageUrl) {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
      await waitForUi(page);
    }
  });

  // Step 9: Validate Política de Privacidad
  await runValidationStep("Política de Privacidad", async () => {
    const targetLabels = textVariants("Política de Privacidad", "Politica de Privacidad");
    const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
    const clicked = await clickByVisibleText(page, targetLabels, { timeout: 20_000 });
    expect(clicked).toBeTruthy();

    const popup = await popupPromise;
    let legalPage = page;

    if (popup) {
      legalPage = popup;
      await waitForUi(legalPage, 25_000);
    } else {
      await waitForUi(page, 25_000);
    }

    const privacyChecks = await Promise.all([
      ensureVisibleText(legalPage, targetLabels, 25_000),
      ensureVisibleText(legalPage, ["privacidad", "datos personales", "política"], 25_000)
    ]);
    expect(privacyChecks.every(Boolean)).toBeTruthy();

    const privacyShot = path.join(SCREENSHOTS_DIR, `${stamp}-09-politica-de-privacidad.png`);
    await legalPage.screenshot({ path: privacyShot, fullPage: true });
    evidence.screenshots.privacy = privacyShot;
    evidence.privacyUrl = legalPage.url();

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
    } else if (appAccountPageUrl && page.url() !== appAccountPageUrl) {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
      await waitForUi(page);
    }
  });

  // Step 10: Final report
  fs.writeFileSync(
    REPORT_PATH,
    `${JSON.stringify(
      {
        testName: "saleads_mi_negocio_full_test",
        generatedAt: new Date().toISOString(),
        results: report,
        evidence
      },
      null,
      2
    )}\n`,
    "utf8"
  );

  // Expose a compact summary in test output for CI logs.
  console.log("FINAL_REPORT", JSON.stringify(report));
  console.log("TERMS_URL", evidence.termsUrl || "N/A");
  console.log("PRIVACY_URL", evidence.privacyUrl || "N/A");

  if (failures.length > 0) {
    const summary = failures.map((failure) => `- ${failure.step}: ${failure.message}`).join("\n");
    throw new Error(`Mi Negocio workflow validation failed:\n${summary}`);
  }
});
