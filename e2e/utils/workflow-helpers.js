const fs = require("node:fs");
const path = require("node:path");
const { expect } = require("@playwright/test");

async function waitForUi(page, timeout = 15000) {
  await page.waitForLoadState("domcontentloaded", { timeout });
  await page.waitForLoadState("networkidle", { timeout }).catch(() => {
    // Some apps keep network calls open; DOM readiness is enough.
  });
}

function toPattern(textOrRegex) {
  if (textOrRegex instanceof RegExp) {
    return textOrRegex;
  }

  return new RegExp(textOrRegex, "i");
}

async function clickByVisibleText(page, textOrRegex, options = {}) {
  const locator = page.getByText(toPattern(textOrRegex)).first();
  await expect(locator, `Expected visible text: ${textOrRegex}`).toBeVisible({
    timeout: options.timeout ?? 15000
  });
  await locator.click();
  await waitForUi(page);
}

async function validateVisibleText(page, textOrRegex, timeout = 15000) {
  const locator = page.getByText(toPattern(textOrRegex)).first();
  await expect(locator, `Expected visible text: ${textOrRegex}`).toBeVisible({ timeout });
}

async function fillByLabelOrPlaceholder(page, fieldName, value) {
  const pattern = toPattern(fieldName);
  const byLabel = page.getByLabel(pattern).first();
  if (await byLabel.count()) {
    await byLabel.fill(value);
    return;
  }

  const byPlaceholder = page.getByPlaceholder(pattern).first();
  if (await byPlaceholder.count()) {
    await byPlaceholder.fill(value);
    return;
  }

  const byRoleTextbox = page.getByRole("textbox", { name: pattern }).first();
  if (await byRoleTextbox.count()) {
    await byRoleTextbox.fill(value);
    return;
  }

  const fallback = page.locator("input, textarea").first();
  await expect(
    fallback,
    `Unable to locate input for field '${fieldName}'`
  ).toBeVisible({ timeout: 15000 });
  await fallback.fill(value);
}

async function withPopupOrSameTab(page, action) {
  const popupPromise = page.waitForEvent("popup", { timeout: 8000 }).catch(() => null);
  await action();
  const popup = await popupPromise;

  if (popup) {
    await waitForUi(popup);
    return { targetPage: popup, openedInNewTab: true };
  }

  await waitForUi(page);
  return { targetPage: page, openedInNewTab: false };
}

async function screenshotCheckpoint(page, checkpointName) {
  await waitForUi(page);
  const screenshotsDir = path.join(process.cwd(), "artifacts", "screenshots");
  fs.mkdirSync(screenshotsDir, { recursive: true });
  await page.screenshot({
    path: path.join(screenshotsDir, `${checkpointName}.png`),
    fullPage: true
  });
}

module.exports = {
  clickByVisibleText,
  fillByLabelOrPlaceholder,
  screenshotCheckpoint,
  validateVisibleText,
  waitForUi,
  withPopupOrSameTab
};
