const fs = require("node:fs/promises");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

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

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function createReport() {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 8000 });
  } catch (_error) {
    // Some views keep websocket traffic open; domcontentloaded is enough.
  }
  await page.waitForTimeout(500);
}

async function writeCheckpoint(page, evidenceDir, name, fullPage = false) {
  const safeName = name.toLowerCase().replace(/[^a-z0-9-]+/g, "-");
  const screenshotPath = path.join(evidenceDir, `${safeName}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
}

async function getFirstVisibleLocator(candidates) {
  for (const candidate of candidates) {
    const locator = candidate.first();
    if (await locator.isVisible().catch(() => false)) {
      return locator;
    }
  }
  throw new Error("No visible locator found from candidate list.");
}

async function clickByVisibleText(page, text) {
  const textRegex = new RegExp(escapeRegExp(text), "i");
  const clickable = await getFirstVisibleLocator([
    page.getByRole("button", { name: textRegex }),
    page.getByRole("link", { name: textRegex }),
    page.getByRole("menuitem", { name: textRegex }),
    page.getByText(textRegex)
  ]);
  await clickable.click();
  await waitForUi(page);
}

async function expectVisibleText(pageOrLocator, text) {
  const textRegex = new RegExp(escapeRegExp(text), "i");
  const locator = pageOrLocator.getByText(textRegex).first();
  await expect(locator).toBeVisible();
}

async function executeSection(report, issues, sectionName, callback) {
  try {
    await callback();
    report[sectionName] = "PASS";
  } catch (error) {
    report[sectionName] = "FAIL";
    issues.push(`${sectionName}: ${error.message}`);
  }
}

async function validateLegalLink({
  page,
  context,
  linkText,
  headingText,
  evidenceDir,
  report,
  issues
}) {
  await executeSection(report, issues, linkText, async () => {
    const linkRegex = new RegExp(escapeRegExp(linkText), "i");
    const link = await getFirstVisibleLocator([
      page.getByRole("link", { name: linkRegex }),
      page.getByRole("button", { name: linkRegex }),
      page.getByText(linkRegex)
    ]);

    const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await link.click();
    await waitForUi(page);

    const popup = await popupPromise;
    const legalPage = popup ?? page;
    if (popup) {
      await waitForUi(legalPage);
    }

    await expect(legalPage.getByRole("heading", { name: new RegExp(escapeRegExp(headingText), "i") }).first()).toBeVisible();
    await expect(
      legalPage
        .locator("main, article, section, body")
        .getByText(/\S+/)
        .first()
    ).toBeVisible();

    await writeCheckpoint(legalPage, evidenceDir, linkText, true);
    await fs.appendFile(path.join(evidenceDir, "legal-urls.txt"), `${linkText}: ${legalPage.url()}\n`, "utf8");

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await legalPage.goBack().catch(() => {});
      await waitForUi(page);
    }
  });
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const startUrl = process.env.SALEADS_START_URL;
  test.skip(!startUrl, "Set SALEADS_START_URL to the current environment login page before running this test.");

  const runId = new Date().toISOString().replace(/[:.]/g, "-");
  const baseEvidenceDir = process.env.SALEADS_EVIDENCE_DIR || path.join(__dirname, "evidence");
  const evidenceDir = path.join(baseEvidenceDir, `saleads-mi-negocio-${runId}`);
  await fs.mkdir(evidenceDir, { recursive: true });

  const report = createReport();
  const issues = [];

  await page.goto(startUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  await executeSection(report, issues, "Login", async () => {
    const loginTrigger = await getFirstVisibleLocator([
      page.getByRole("button", { name: /google|sign in/i }),
      page.getByRole("link", { name: /google|sign in/i }),
      page.getByText(/google|sign in/i)
    ]);

    const googlePopupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await loginTrigger.click();
    await waitForUi(page);
    const googlePopup = await googlePopupPromise;

    const accountEmailText = "juanlucasbarbiergarzon@gmail.com";
    if (googlePopup) {
      await waitForUi(googlePopup);
      const accountChoice = googlePopup.getByText(accountEmailText, { exact: false }).first();
      if (await accountChoice.isVisible().catch(() => false)) {
        await accountChoice.click();
      }
    } else {
      const accountChoice = page.getByText(accountEmailText, { exact: false }).first();
      if (await accountChoice.isVisible().catch(() => false)) {
        await accountChoice.click();
        await waitForUi(page);
      }
    }

    await expect(
      page.locator("aside, nav").filter({ hasText: /Negocio|Mi Negocio/i }).first()
    ).toBeVisible();
    await writeCheckpoint(page, evidenceDir, "dashboard-loaded");
  });

  await executeSection(report, issues, "Mi Negocio menu", async () => {
    await clickByVisibleText(page, "Mi Negocio");
    await expectVisibleText(page, "Agregar Negocio");
    await expectVisibleText(page, "Administrar Negocios");
    await writeCheckpoint(page, evidenceDir, "mi-negocio-menu-expanded");
  });

  await executeSection(report, issues, "Agregar Negocio modal", async () => {
    await clickByVisibleText(page, "Agregar Negocio");
    const modal = page.getByRole("dialog").first();
    await expect(modal).toBeVisible();
    await expectVisibleText(modal, "Crear Nuevo Negocio");

    const nameField = await getFirstVisibleLocator([
      modal.getByLabel(/Nombre del Negocio/i),
      modal.getByPlaceholder(/Nombre del Negocio/i),
      modal.locator("input[name*='nombre'], input[id*='nombre'], input").first()
    ]);
    await expect(nameField).toBeVisible();
    await expectVisibleText(modal, "Tienes 2 de 3 negocios");
    await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    await nameField.click();
    await nameField.fill("Negocio Prueba Automatización");
    await writeCheckpoint(page, evidenceDir, "agregar-negocio-modal");
    await modal.getByRole("button", { name: /Cancelar/i }).click();
    await waitForUi(page);
  });

  await executeSection(report, issues, "Administrar Negocios view", async () => {
    const administrarVisible = await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      await clickByVisibleText(page, "Mi Negocio");
    }

    await clickByVisibleText(page, "Administrar Negocios");

    await expectVisibleText(page, "Información General");
    await expectVisibleText(page, "Detalles de la Cuenta");
    await expectVisibleText(page, "Tus Negocios");
    await expectVisibleText(page, "Sección Legal");
    await writeCheckpoint(page, evidenceDir, "administrar-negocios-view", true);
  });

  await executeSection(report, issues, "Información General", async () => {
    const section = page.locator("section, div, article").filter({ hasText: /Información General/i }).first();
    await expect(section).toBeVisible();
    await expect(section.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/).first()).toBeVisible();
    await expectVisibleText(section, "BUSINESS PLAN");
    await expect(section.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

    const lines = (await section.innerText()).split("\n").map((line) => line.trim()).filter(Boolean);
    const hasPotentialName = lines.some((line) => {
      const isEmail = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(line);
      const ignored = /Información General|BUSINESS PLAN|Cambiar Plan/i.test(line);
      return !isEmail && !ignored && /[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}/.test(line);
    });
    expect(hasPotentialName).toBeTruthy();
  });

  await executeSection(report, issues, "Detalles de la Cuenta", async () => {
    const section = page.locator("section, div, article").filter({ hasText: /Detalles de la Cuenta/i }).first();
    await expect(section).toBeVisible();
    await expectVisibleText(section, "Cuenta creada");
    await expectVisibleText(section, "Estado activo");
    await expectVisibleText(section, "Idioma seleccionado");
  });

  await executeSection(report, issues, "Tus Negocios", async () => {
    const section = page.locator("section, div, article").filter({ hasText: /Tus Negocios/i }).first();
    await expect(section).toBeVisible();
    await expect(section.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
    await expectVisibleText(section, "Tienes 2 de 3 negocios");
    await expect(section.locator("li, table, [data-testid*='business'], [class*='business']").first()).toBeVisible();
  });

  await validateLegalLink({
    page,
    context,
    linkText: "Términos y Condiciones",
    headingText: "Términos y Condiciones",
    evidenceDir,
    report,
    issues
  });

  await validateLegalLink({
    page,
    context,
    linkText: "Política de Privacidad",
    headingText: "Política de Privacidad",
    evidenceDir,
    report,
    issues
  });

  const finalReport = {
    generatedAt: new Date().toISOString(),
    startUrl,
    evidenceDir,
    report,
    issues
  };

  await fs.writeFile(
    path.join(evidenceDir, "final-report.json"),
    `${JSON.stringify(finalReport, null, 2)}\n`,
    "utf8"
  );

  console.log("Final validation report:");
  console.table(report);
  if (issues.length > 0) {
    console.error("Validation issues:");
    for (const issue of issues) {
      console.error(`- ${issue}`);
    }
  }

  const failedSections = Object.entries(report).filter(([, status]) => status !== "PASS");
  expect(
    failedSections,
    failedSections.length === 0 ? "All requested validations passed." : "Some requested validations failed."
  ).toEqual([]);
});
