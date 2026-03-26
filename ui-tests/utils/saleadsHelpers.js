const fs = require("fs/promises");
const path = require("path");
const { expect } = require("@playwright/test");

const SCREENSHOT_DIR = path.resolve(__dirname, "..", "screenshots");

async function ensureDir(dir) {
  await fs.mkdir(dir, { recursive: true });
}

async function takeCheckpoint(page, name) {
  await ensureDir(SCREENSHOT_DIR);
  const filename = `${new Date().toISOString().replace(/[:.]/g, "-")}-${name}.png`;
  const filePath = path.join(SCREENSHOT_DIR, filename);
  await page.screenshot({ path: filePath, fullPage: true });
  return filePath;
}

async function saveFinalReport(report) {
  const reportDir = path.resolve(__dirname, "..", "artifacts");
  await ensureDir(reportDir);
  const reportPath = path.join(reportDir, "saleads_mi_negocio_report.json");
  await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");
  return reportPath;
}

async function waitUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(500);
}

async function clickByText(pageOrScope, text, options = {}) {
  const exact = options.exact ?? false;
  const candidateLocators = [
    pageOrScope.getByRole("button", { name: text, exact }).first(),
    pageOrScope.getByRole("link", { name: text, exact }).first(),
    pageOrScope.getByText(text, { exact }).first()
  ];

  for (const locator of candidateLocators) {
    if (await locator.isVisible().catch(() => false)) {
      await locator.click();
      return;
    }
  }

  await expect(candidateLocators[2], `Expected clickable text "${text}"`).toBeVisible();
  await candidateLocators[2].click();
}

async function assertVisibleText(pageOrScope, text, options = {}) {
  const locator = pageOrScope.getByText(text, { exact: options.exact ?? false }).first();
  await expect(locator, `Expected text "${text}" to be visible`).toBeVisible();
}

function createStepTracker() {
  const statuses = new Map([
    ["Login", "FAIL"],
    ["Mi Negocio menu", "FAIL"],
    ["Agregar Negocio modal", "FAIL"],
    ["Administrar Negocios view", "FAIL"],
    ["Información General", "FAIL"],
    ["Detalles de la Cuenta", "FAIL"],
    ["Tus Negocios", "FAIL"],
    ["Términos y Condiciones", "FAIL"],
    ["Política de Privacidad", "FAIL"]
  ]);

  return {
    pass(stepName) {
      statuses.set(stepName, "PASS");
    },
    fail(stepName) {
      statuses.set(stepName, "FAIL");
    },
    toObject() {
      return Object.fromEntries(statuses.entries());
    }
  };
}

module.exports = {
  SCREENSHOT_DIR,
  takeCheckpoint,
  saveFinalReport,
  waitUi,
  clickByText,
  assertVisibleText,
  createStepTracker
};
