const fs = require("fs/promises");
const path = require("path");
const { test, expect } = require("@playwright/test");

const ARTIFACTS_DIR = path.join(__dirname, "..", "reports");
const SCREENSHOTS_DIR = path.join(ARTIFACTS_DIR, "screenshots");
const REPORT_FILE = path.join(ARTIFACTS_DIR, "saleads-mi-negocio-full-test-report.json");
const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

const STEP_KEYS = {
  LOGIN: "Login",
  MENU: "Mi Negocio menu",
  ADD_MODAL: "Agregar Negocio modal",
  ADMIN_VIEW: "Administrar Negocios view",
  GENERAL_INFO: "Información General",
  ACCOUNT_DETAILS: "Detalles de la Cuenta",
  BUSINESS_LIST: "Tus Negocios",
  TERMS: "Términos y Condiciones",
  PRIVACY: "Política de Privacidad"
};

const NEW_BUSINESS_NAME = "Negocio Prueba Automatización";

function nowIso() {
  return new Date().toISOString();
}

function getConfiguredLoginUrl() {
  const raw =
    process.env.SALEADS_LOGIN_URL ||
    process.env.saleads_login_url ||
    process.env["saleads.login.url"] ||
    "";
  return raw.trim() || null;
}

function toReportLine(item) {
  return `${item.name}: ${item.status}${item.reason ? ` (${item.reason})` : ""}`;
}

async function ensureArtifactsFolders() {
  await fs.mkdir(SCREENSHOTS_DIR, { recursive: true });
}

async function saveStepScreenshot(page, fileName) {
  const fullPath = path.join(SCREENSHOTS_DIR, fileName);
  await page.screenshot({ path: fullPath, fullPage: true });
  return fullPath;
}

function createRunState() {
  return {
    startedAt: nowIso(),
    environment: {
      baseURL: getConfiguredLoginUrl(),
      initialPageUrl: null
    },
    steps: Object.values(STEP_KEYS).map((name) => ({
      name,
      status: "PENDING",
      reason: "",
      evidence: []
    })),
    notes: []
  };
}

function getStep(state, name) {
  return state.steps.find((step) => step.name === name);
}

function passStep(state, name, reason = "Validated") {
  const step = getStep(state, name);
  step.status = "PASS";
  step.reason = reason;
}

function failStep(state, name, reason) {
  const step = getStep(state, name);
  if (step.status !== "PASS") {
    step.status = "FAIL";
    step.reason = reason;
  }
}

function skipStep(state, name, reason) {
  const step = getStep(state, name);
  if (step.status === "PENDING") {
    step.status = "FAIL";
    step.reason = `Skipped because prerequisite failed: ${reason}`;
  }
}

function attachEvidence(state, name, evidenceValue) {
  const step = getStep(state, name);
  step.evidence.push(evidenceValue);
}

async function waitAfterAction(page) {
  await Promise.race([
    page.waitForLoadState("domcontentloaded", { timeout: 8000 }).catch(() => {}),
    page.waitForTimeout(1200)
  ]);
}

async function maybeSelectGoogleAccount(page) {
  const emailRegex = new RegExp(escapeRegExp(GOOGLE_ACCOUNT_EMAIL), "i");
  const candidates = [
    page.getByText(emailRegex).first(),
    page.getByRole("button", { name: emailRegex }).first(),
    page.getByRole("link", { name: emailRegex }).first()
  ];

  for (const candidate of candidates) {
    if (await candidate.isVisible().catch(() => false)) {
      await candidate.click({ timeout: 10000 });
      await waitAfterAction(page);
      return true;
    }
  }
  return false;
}

async function assertLikelyUserNameVisible(page) {
  const rawText = await page.locator("body").innerText();
  const lines = rawText
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);

  const excluded = [
    /informaci[oó]n general/i,
    /detalles de la cuenta/i,
    /tus negocios/i,
    /secci[oó]n legal/i,
    /business plan/i,
    /cambiar plan/i,
    /cuenta creada/i,
    /estado activo/i,
    /idioma seleccionado/i,
    /agregar negocio/i,
    /administrar negocios/i,
    /mi negocio/i,
    /t[eé]rminos/i,
    /pol[ií]tica/i,
    /dashboard/i,
    /panel/i,
    /negocio/i
  ];

  const looksLikeName = lines.find((line) => {
    if (line.length < 4 || line.length > 60) return false;
    if (line.includes("@")) return false;
    if (excluded.some((re) => re.test(line))) return false;
    return /^[A-Za-zÀ-ÖØ-öø-ÿ'’.-]+(?:\s+[A-Za-zÀ-ÖØ-öø-ÿ'’.-]+){1,3}$/.test(line);
  });

  if (!looksLikeName) {
    throw new Error("Could not identify a visible user name text.");
  }

  return looksLikeName;
}

async function clickByVisibleText(page, labels) {
  for (const label of labels) {
    const normalized = label.replace(/\s+/g, " ").trim();
    const target = page.getByText(new RegExp(`^\\s*${escapeRegExp(normalized)}\\s*$`, "i")).first();
    if (await target.isVisible().catch(() => false)) {
      await target.click({ timeout: 10000 });
      await waitAfterAction(page);
      return normalized;
    }
  }
  return null;
}

async function clickRoleButtonOrText(page, labels) {
  for (const label of labels) {
    const button = page.getByRole("button", { name: new RegExp(escapeRegExp(label), "i") }).first();
    if (await button.isVisible().catch(() => false)) {
      await button.click({ timeout: 10000 });
      await waitAfterAction(page);
      return `button:${label}`;
    }
  }
  return clickByVisibleText(page, labels);
}

async function capturePopupOrSameTab(page, triggerFn) {
  const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
  const priorUrl = page.url();
  await triggerFn();
  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
    return { kind: "popup", page: popup, url: popup.url(), priorUrl };
  }

  await page.waitForLoadState("domcontentloaded", { timeout: 10000 }).catch(() => {});
  return { kind: "same-tab", page, url: page.url(), priorUrl };
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function writeReport(state) {
  const finalized = {
    ...state,
    finishedAt: nowIso(),
    summaryLines: state.steps.map(toReportLine)
  };
  await fs.writeFile(REPORT_FILE, JSON.stringify(finalized, null, 2), "utf8");
  return finalized;
}

async function assertAnyVisible(page, expectedTexts) {
  for (const text of expectedTexts) {
    const locator = page.getByText(new RegExp(escapeRegExp(text), "i")).first();
    if (await locator.isVisible().catch(() => false)) {
      return text;
    }
  }
  throw new Error(`None of the expected texts are visible: ${expectedTexts.join(", ")}`);
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
    await ensureArtifactsFolders();
    const state = createRunState();
    let dashboardReady = false;
    let menuReady = false;
    let adminViewReady = false;

    const loginUrl = getConfiguredLoginUrl();
    if (!loginUrl) {
      throw new Error(
        "Missing environment-agnostic login URL. Set SALEADS_LOGIN_URL or saleads.login.url before running this test."
      );
    }

    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await page.waitForTimeout(1500);
    state.environment.initialPageUrl = page.url();

    // STEP 1: Login with Google
    try {
      const navResult = await capturePopupOrSameTab(page, async () => {
        const clicked = await clickRoleButtonOrText(page, [
          "Sign in with Google",
          "Iniciar sesión con Google",
          "Login with Google",
          "Entrar con Google"
        ]);
        if (!clicked) {
          throw new Error("Could not find a Google login button by visible text.");
        }
      });

      if (navResult.kind === "popup") {
        await maybeSelectGoogleAccount(navResult.page);
        await Promise.race([
          navResult.page.waitForEvent("close", { timeout: 15000 }).catch(() => {}),
          page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {})
        ]);
      } else {
        await maybeSelectGoogleAccount(page);
      }

      await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
      await page.waitForTimeout(2000);

      await assertAnyVisible(page, ["Negocio", "Mi Negocio", "Dashboard", "Panel"]);
      const sidebarByRole = await page.getByRole("navigation").first().isVisible().catch(() => false);
      const sidebarByText =
        (await page.getByText(/Negocio/i).first().isVisible().catch(() => false)) ||
        (await page.getByText(/Mi Negocio/i).first().isVisible().catch(() => false));
      expect(sidebarByRole || sidebarByText).toBeTruthy();
      dashboardReady = true;
      passStep(state, STEP_KEYS.LOGIN, "Main interface and sidebar are visible after login.");
      const shot = await saveStepScreenshot(page, "01-dashboard-after-login.png");
      attachEvidence(state, STEP_KEYS.LOGIN, shot);
    } catch (error) {
      failStep(state, STEP_KEYS.LOGIN, error.message);
    }

    // STEP 2: Open Mi Negocio menu
    if (!dashboardReady) {
      skipStep(state, STEP_KEYS.MENU, "Login step failed");
    } else {
      try {
        await clickByVisibleText(page, ["Negocio", "Mi Negocio"]);
        await assertAnyVisible(page, ["Agregar Negocio"]);
        await assertAnyVisible(page, ["Administrar Negocios"]);
        menuReady = true;
        passStep(state, STEP_KEYS.MENU, "Mi Negocio expanded and submenu options are visible.");
        const shot = await saveStepScreenshot(page, "02-mi-negocio-expanded-menu.png");
        attachEvidence(state, STEP_KEYS.MENU, shot);
      } catch (error) {
        failStep(state, STEP_KEYS.MENU, error.message);
      }
    }

    // STEP 3: Validate Agregar Negocio modal
    if (!menuReady) {
      skipStep(state, STEP_KEYS.ADD_MODAL, "Mi Negocio menu step failed");
    } else {
      try {
        const clicked = await clickByVisibleText(page, ["Agregar Negocio"]);
        if (!clicked) {
          throw new Error("Could not click 'Agregar Negocio'.");
        }

        await assertAnyVisible(page, ["Crear Nuevo Negocio"]);
        let businessNameInput = page.getByLabel(/Nombre del Negocio/i).first();
        let inputVisible = await businessNameInput.isVisible().catch(() => false);
        if (!inputVisible) {
          businessNameInput = page.getByPlaceholder(/Nombre del Negocio/i).first();
          inputVisible = await businessNameInput.isVisible().catch(() => false);
        }
        expect(inputVisible).toBeTruthy();

        await assertAnyVisible(page, ["Tienes 2 de 3 negocios"]);
        await assertAnyVisible(page, ["Cancelar"]);
        await assertAnyVisible(page, ["Crear Negocio"]);

        // Optional actions.
        if (await businessNameInput.isVisible().catch(() => false)) {
          await businessNameInput.click();
          await businessNameInput.fill(NEW_BUSINESS_NAME);
        }

        const modalShot = await saveStepScreenshot(page, "03-agregar-negocio-modal.png");
        attachEvidence(state, STEP_KEYS.ADD_MODAL, modalShot);

        await clickRoleButtonOrText(page, ["Cancelar"]);
        passStep(state, STEP_KEYS.ADD_MODAL, "Agregar Negocio modal fields and actions are correct.");
      } catch (error) {
        failStep(state, STEP_KEYS.ADD_MODAL, error.message);
      }
    }

    // STEP 4: Open Administrar Negocios
    if (!menuReady) {
      skipStep(state, STEP_KEYS.ADMIN_VIEW, "Mi Negocio menu step failed");
    } else {
      try {
        if (!(await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false))) {
          await clickByVisibleText(page, ["Negocio", "Mi Negocio"]);
        }
        const clicked = await clickByVisibleText(page, ["Administrar Negocios"]);
        if (!clicked) {
          throw new Error("Could not click 'Administrar Negocios'.");
        }

        await assertAnyVisible(page, ["Información General"]);
        await assertAnyVisible(page, ["Detalles de la Cuenta"]);
        await assertAnyVisible(page, ["Tus Negocios"]);
        await assertAnyVisible(page, ["Sección Legal"]);

        adminViewReady = true;
        passStep(state, STEP_KEYS.ADMIN_VIEW, "Account page sections are visible.");
        const shot = await saveStepScreenshot(page, "04-administrar-negocios-cuenta.png");
        attachEvidence(state, STEP_KEYS.ADMIN_VIEW, shot);
      } catch (error) {
        failStep(state, STEP_KEYS.ADMIN_VIEW, error.message);
      }
    }

    // STEP 5: Validate Información General
    if (!adminViewReady) {
      skipStep(state, STEP_KEYS.GENERAL_INFO, "Administrar Negocios view step failed");
    } else {
      try {
        const userName = await assertLikelyUserNameVisible(page);
        const looksLikeEmail = page.locator("text=/[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}/").first();
        expect(await looksLikeEmail.isVisible()).toBeTruthy();
        await assertAnyVisible(page, ["BUSINESS PLAN"]);
        await assertAnyVisible(page, ["Cambiar Plan"]);
        passStep(state, STEP_KEYS.GENERAL_INFO, `User profile and plan details are visible (name detected: ${userName}).`);
      } catch (error) {
        failStep(state, STEP_KEYS.GENERAL_INFO, error.message);
      }
    }

    // STEP 6: Validate Detalles de la Cuenta
    if (!adminViewReady) {
      skipStep(state, STEP_KEYS.ACCOUNT_DETAILS, "Administrar Negocios view step failed");
    } else {
      try {
        await assertAnyVisible(page, ["Cuenta creada"]);
        await assertAnyVisible(page, ["Estado activo"]);
        await assertAnyVisible(page, ["Idioma seleccionado"]);
        passStep(state, STEP_KEYS.ACCOUNT_DETAILS, "Account details fields are visible.");
      } catch (error) {
        failStep(state, STEP_KEYS.ACCOUNT_DETAILS, error.message);
      }
    }

    // STEP 7: Validate Tus Negocios
    if (!adminViewReady) {
      skipStep(state, STEP_KEYS.BUSINESS_LIST, "Administrar Negocios view step failed");
    } else {
      try {
        await assertAnyVisible(page, ["Tus Negocios"]);
        await assertAnyVisible(page, ["Agregar Negocio"]);
        await assertAnyVisible(page, ["Tienes 2 de 3 negocios"]);
        passStep(state, STEP_KEYS.BUSINESS_LIST, "Business list and limits are visible.");
      } catch (error) {
        failStep(state, STEP_KEYS.BUSINESS_LIST, error.message);
      }
    }

    // STEP 8: Validate Términos y Condiciones
    if (!adminViewReady) {
      skipStep(state, STEP_KEYS.TERMS, "Administrar Negocios view step failed");
    } else {
      try {
        const navResult = await capturePopupOrSameTab(page, async () => {
          const clicked = await clickByVisibleText(page, ["Términos y Condiciones", "Terminos y Condiciones"]);
          if (!clicked) {
            throw new Error("Could not click 'Términos y Condiciones'.");
          }
        });

        await assertAnyVisible(navResult.page, ["Términos y Condiciones", "Terminos y Condiciones"]);
        const legalContent = navResult.page.locator("body");
        const contentText = (await legalContent.innerText()).trim();
        expect(contentText.length).toBeGreaterThan(80);
        const termsShot = await saveStepScreenshot(navResult.page, "08-terminos-y-condiciones.png");
        attachEvidence(state, STEP_KEYS.TERMS, termsShot);
        attachEvidence(state, STEP_KEYS.TERMS, `Final URL: ${navResult.url}`);

        if (navResult.kind === "popup") {
          await navResult.page.close();
        } else if (navResult.url !== navResult.priorUrl) {
          await page.goBack().catch(() => {});
          await waitAfterAction(page);
        }

        passStep(state, STEP_KEYS.TERMS, "Terms page opened, content validated, and app tab restored.");
      } catch (error) {
        failStep(state, STEP_KEYS.TERMS, error.message);
      }
    }

    // STEP 9: Validate Política de Privacidad
    if (!adminViewReady) {
      skipStep(state, STEP_KEYS.PRIVACY, "Administrar Negocios view step failed");
    } else {
      try {
        const navResult = await capturePopupOrSameTab(page, async () => {
          const clicked = await clickByVisibleText(page, ["Política de Privacidad", "Politica de Privacidad"]);
          if (!clicked) {
            throw new Error("Could not click 'Política de Privacidad'.");
          }
        });

        await assertAnyVisible(navResult.page, ["Política de Privacidad", "Politica de Privacidad"]);
        const legalContent = navResult.page.locator("body");
        const contentText = (await legalContent.innerText()).trim();
        expect(contentText.length).toBeGreaterThan(80);
        const privacyShot = await saveStepScreenshot(navResult.page, "09-politica-de-privacidad.png");
        attachEvidence(state, STEP_KEYS.PRIVACY, privacyShot);
        attachEvidence(state, STEP_KEYS.PRIVACY, `Final URL: ${navResult.url}`);

        if (navResult.kind === "popup") {
          await navResult.page.close();
        } else if (navResult.url !== navResult.priorUrl) {
          await page.goBack().catch(() => {});
          await waitAfterAction(page);
        }

        passStep(state, STEP_KEYS.PRIVACY, "Privacy page opened, content validated, and app tab restored.");
      } catch (error) {
        failStep(state, STEP_KEYS.PRIVACY, error.message);
      }
    }

    const report = await writeReport(state);
    await testInfo.attach("saleads-mi-negocio-final-report", {
      body: JSON.stringify(report, null, 2),
      contentType: "application/json"
    });

    // STEP 10: Final report expectation (hard fail when any previous step failed).
    const failed = report.steps.filter((step) => step.status !== "PASS");
    if (failed.length > 0) {
      throw new Error(`Workflow finished with failures: ${failed.map(toReportLine).join(" | ")}`);
    }
  });
});
