const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");
const path = require("path");

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad"
];

const ARTIFACTS_DIR = path.resolve(__dirname, "..", "artifacts");
const SCREENSHOTS_DIR = path.join(ARTIFACTS_DIR, "screenshots");

function nowStamp() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

function escapeRegex(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function ensureArtifactsDirs() {
  await fs.mkdir(SCREENSHOTS_DIR, { recursive: true });
}

async function settleUi(page, delayMs = 900) {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 7000 });
  } catch (_error) {
    // Some apps keep websocket/network connections active; domcontentloaded is enough.
  }
  await page.waitForTimeout(delayMs);
}

async function visibleByText(scope, textOrRegex) {
  const matcher =
    textOrRegex instanceof RegExp
      ? textOrRegex
      : new RegExp(escapeRegex(textOrRegex), "i");

  const strategies = [
    scope.getByRole("button", { name: matcher }),
    scope.getByRole("link", { name: matcher }),
    scope.getByRole("menuitem", { name: matcher }),
    scope.getByRole("tab", { name: matcher }),
    scope.getByRole("heading", { name: matcher }),
    scope.getByLabel(matcher),
    scope.getByText(matcher)
  ];

  for (const locator of strategies) {
    const first = locator.first();
    if (await first.isVisible().catch(() => false)) {
      return first;
    }
  }

  return null;
}

async function clickVisible(scope, textOrRegex, pageToSettle) {
  const target = await visibleByText(scope, textOrRegex);
  if (!target) {
    throw new Error(`No visible element found for: ${textOrRegex.toString()}`);
  }
  await target.click();
  await settleUi(pageToSettle);
  return target;
}

async function textExists(scope, textOrRegex) {
  const element = await visibleByText(scope, textOrRegex);
  return Boolean(element);
}

async function saveCheckpoint(page, name, evidenceLog) {
  const fileName = `${nowStamp()}-${name}.png`;
  const outputPath = path.join(SCREENSHOTS_DIR, fileName);
  await page.screenshot({ path: outputPath, fullPage: true });
  evidenceLog.push(outputPath);
  return outputPath;
}

async function writeReport(report, details) {
  const stamp = nowStamp();
  const jsonPath = path.join(ARTIFACTS_DIR, `report-${stamp}.json`);
  const mdPath = path.join(ARTIFACTS_DIR, `report-${stamp}.md`);

  const payload = {
    executedAt: new Date().toISOString(),
    report,
    details
  };

  await fs.writeFile(jsonPath, JSON.stringify(payload, null, 2), "utf8");

  const markdownRows = REPORT_FIELDS.map(
    (field) => `| ${field} | ${report[field] || "FAIL"} |`
  ).join("\n");
  const md = [
    "# SaleADS Mi Negocio - Final Report",
    "",
    "| Step | Result |",
    "|---|---|",
    markdownRows,
    "",
    "## URLs",
    `- Términos y Condiciones: ${details.terminosUrl || "N/A"}`,
    `- Política de Privacidad: ${details.politicaUrl || "N/A"}`,
    "",
    "## Errors",
    ...(details.errors.length ? details.errors.map((item) => `- ${item}`) : ["- None"]),
    "",
    "## Checkpoint Screenshots",
    ...(details.screenshots.length
      ? details.screenshots.map((file) => `- ${file}`)
      : ["- None"])
  ].join("\n");

  await fs.writeFile(mdPath, md, "utf8");
  return { jsonPath, mdPath };
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  await ensureArtifactsDirs();

  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const details = {
    errors: [],
    screenshots: [],
    terminosUrl: null,
    politicaUrl: null
  };

  const failStep = (field, error) => {
    report[field] = "FAIL";
    details.errors.push(`${field}: ${error instanceof Error ? error.message : String(error)}`);
  };

  try {
    const startUrl =
      process.env.SALEADS_START_URL || process.env.SALEADS_URL || process.env.BASE_URL;

    if (startUrl) {
      await page.goto(startUrl, { waitUntil: "domcontentloaded" });
    }
    await settleUi(page);

    const startupBlockedReason =
      page.url() === "about:blank"
        ? "Set SALEADS_START_URL (or SALEADS_URL/BASE_URL) so the test starts on the login page."
        : null;

    // Step 1: Login with Google.
    try {
      if (startupBlockedReason) {
        throw new Error(startupBlockedReason);
      }
      const loginMatcher = /sign in with google|continuar con google|iniciar sesión con google|google/i;
      const maybePopup = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
      await clickVisible(page, loginMatcher, page);
      const popup = await maybePopup;

      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        const accountOption = await visibleByText(popup, ACCOUNT_EMAIL);
        if (accountOption) {
          await accountOption.click();
          await settleUi(popup);
        }
        await popup.close().catch(() => {});
      } else {
        const accountOption = await visibleByText(page, ACCOUNT_EMAIL);
        if (accountOption) {
          await accountOption.click();
          await settleUi(page);
        }
      }

      const sidebarVisible =
        (await textExists(page, /mi negocio|negocio/i)) ||
        (await page.locator("aside, nav").first().isVisible().catch(() => false));
      expect(sidebarVisible).toBeTruthy();

      report["Login"] = "PASS";
      await saveCheckpoint(page, "01-dashboard-loaded", details.screenshots);
    } catch (error) {
      failStep("Login", error);
    }

    // Step 2: Open Mi Negocio menu.
    try {
      if (report["Login"] !== "PASS") {
        throw new Error("Prerequisite failed: Login step did not pass.");
      }
      await clickVisible(page, /mi negocio/i, page);
      const hasAgregar = await textExists(page, /agregar negocio/i);
      const hasAdministrar = await textExists(page, /administrar negocios/i);
      expect(hasAgregar && hasAdministrar).toBeTruthy();

      report["Mi Negocio menu"] = "PASS";
      await saveCheckpoint(page, "02-mi-negocio-menu-expanded", details.screenshots);
    } catch (error) {
      failStep("Mi Negocio menu", error);
    }

    // Step 3: Validate Agregar Negocio modal.
    try {
      if (report["Mi Negocio menu"] !== "PASS") {
        throw new Error("Prerequisite failed: Mi Negocio menu step did not pass.");
      }
      await clickVisible(page, /agregar negocio/i, page);
      const modalTitle = await visibleByText(page, /crear nuevo negocio/i);
      expect(modalTitle).toBeTruthy();

      const hasNombre = await textExists(page, /nombre del negocio/i);
      const hasLimit = await textExists(page, /tienes\s*2\s*de\s*3\s*negocios/i);
      const hasCancelar = await textExists(page, /cancelar/i);
      const hasCrear = await textExists(page, /crear negocio/i);

      expect(hasNombre && hasLimit && hasCancelar && hasCrear).toBeTruthy();

      const nameField = page.getByLabel(/nombre del negocio/i).first();
      if (await nameField.isVisible().catch(() => false)) {
        await nameField.click();
        await nameField.fill("Negocio Prueba Automatización");
        await settleUi(page, 500);
      }
      await clickVisible(page, /cancelar/i, page);

      report["Agregar Negocio modal"] = "PASS";
      await saveCheckpoint(page, "03-agregar-negocio-modal", details.screenshots);
    } catch (error) {
      failStep("Agregar Negocio modal", error);
    }

    // Step 4: Open Administrar Negocios view.
    try {
      if (report["Mi Negocio menu"] !== "PASS") {
        throw new Error("Prerequisite failed: Mi Negocio menu step did not pass.");
      }
      if (!(await textExists(page, /administrar negocios/i))) {
        await clickVisible(page, /mi negocio/i, page);
      }
      await clickVisible(page, /administrar negocios/i, page);

      const sections = [
        /información general/i,
        /detalles de la cuenta/i,
        /tus negocios/i,
        /sección legal/i
      ];
      for (const section of sections) {
        expect(await textExists(page, section)).toBeTruthy();
      }

      report["Administrar Negocios view"] = "PASS";
      await saveCheckpoint(page, "04-administrar-negocios-view", details.screenshots);
    } catch (error) {
      failStep("Administrar Negocios view", error);
    }

    // Step 5: Validate Información General.
    try {
      if (report["Administrar Negocios view"] !== "PASS") {
        throw new Error("Prerequisite failed: Administrar Negocios view step did not pass.");
      }
      const hasEmail = await textExists(page, /@/);
      const hasPlan = await textExists(page, /business plan/i);
      const hasCambiarPlan = await textExists(page, /cambiar plan/i);
      const hasUserNameLabel =
        (await textExists(page, /nombre/i)) ||
        (await page.locator("h1, h2, h3, p, span").first().isVisible().catch(() => false));

      expect(hasEmail && hasPlan && hasCambiarPlan && hasUserNameLabel).toBeTruthy();
      report["Información General"] = "PASS";
    } catch (error) {
      failStep("Información General", error);
    }

    // Step 6: Validate Detalles de la Cuenta.
    try {
      if (report["Administrar Negocios view"] !== "PASS") {
        throw new Error("Prerequisite failed: Administrar Negocios view step did not pass.");
      }
      const hasCuentaCreada = await textExists(page, /cuenta creada/i);
      const hasEstadoActivo = await textExists(page, /estado activo/i);
      const hasIdioma = await textExists(page, /idioma seleccionado/i);
      expect(hasCuentaCreada && hasEstadoActivo && hasIdioma).toBeTruthy();
      report["Detalles de la Cuenta"] = "PASS";
    } catch (error) {
      failStep("Detalles de la Cuenta", error);
    }

    // Step 7: Validate Tus Negocios.
    try {
      if (report["Administrar Negocios view"] !== "PASS") {
        throw new Error("Prerequisite failed: Administrar Negocios view step did not pass.");
      }
      const hasList = await textExists(page, /tus negocios/i);
      const hasAgregar = await textExists(page, /agregar negocio/i);
      const hasLimit = await textExists(page, /tienes\s*2\s*de\s*3\s*negocios/i);
      expect(hasList && hasAgregar && hasLimit).toBeTruthy();
      report["Tus Negocios"] = "PASS";
    } catch (error) {
      failStep("Tus Negocios", error);
    }

    // Step 8: Validate Términos y Condiciones.
    try {
      if (report["Administrar Negocios view"] !== "PASS") {
        throw new Error("Prerequisite failed: Administrar Negocios view step did not pass.");
      }
      const termsLink = await visibleByText(page, /términos y condiciones/i);
      if (!termsLink) {
        throw new Error("No se encontró el enlace 'Términos y Condiciones'.");
      }

      const newTabPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
      await termsLink.click();
      const maybeNewTab = await newTabPromise;
      const legalPage = maybeNewTab || page;
      await settleUi(legalPage);

      const hasHeading = await textExists(legalPage, /términos y condiciones/i);
      const legalText = await legalPage.locator("body").innerText();
      expect(hasHeading && legalText.length > 120).toBeTruthy();

      details.terminosUrl = legalPage.url();
      await saveCheckpoint(legalPage, "08-terminos-y-condiciones", details.screenshots);

      if (maybeNewTab) {
        await maybeNewTab.close();
        await page.bringToFront();
        await settleUi(page, 400);
      }

      report["Términos y Condiciones"] = "PASS";
    } catch (error) {
      failStep("Términos y Condiciones", error);
    }

    // Step 9: Validate Política de Privacidad.
    try {
      if (report["Administrar Negocios view"] !== "PASS") {
        throw new Error("Prerequisite failed: Administrar Negocios view step did not pass.");
      }
      const privacyLink = await visibleByText(page, /política de privacidad/i);
      if (!privacyLink) {
        throw new Error("No se encontró el enlace 'Política de Privacidad'.");
      }

      const newTabPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
      await privacyLink.click();
      const maybeNewTab = await newTabPromise;
      const legalPage = maybeNewTab || page;
      await settleUi(legalPage);

      const hasHeading = await textExists(legalPage, /política de privacidad/i);
      const legalText = await legalPage.locator("body").innerText();
      expect(hasHeading && legalText.length > 120).toBeTruthy();

      details.politicaUrl = legalPage.url();
      await saveCheckpoint(legalPage, "09-politica-de-privacidad", details.screenshots);

      if (maybeNewTab) {
        await maybeNewTab.close();
        await page.bringToFront();
        await settleUi(page, 400);
      }

      report["Política de Privacidad"] = "PASS";
    } catch (error) {
      failStep("Política de Privacidad", error);
    }
  } finally {
    const reportFiles = await writeReport(report, details);
    console.log(`SaleADS report JSON: ${reportFiles.jsonPath}`);
    console.log(`SaleADS report Markdown: ${reportFiles.mdPath}`);
    console.table(report);
  }

  const failed = Object.entries(report)
    .filter(([, status]) => status !== "PASS")
    .map(([field]) => field);
  expect(
    failed,
    `Validation failures in: ${failed.length ? failed.join(", ") : "none"}`
  ).toEqual([]);
});
