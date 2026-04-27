const { test, expect } = require("@playwright/test");
const path = require("path");
const fs = require("fs/promises");

const CHECKPOINTS_DIR = path.join(__dirname, "..", "test-results", "checkpoints");
const REPORT_PATH = path.join(__dirname, "..", "test-results", "saleads-mi-negocio-report.json");

function normalizeText(value) {
  return value
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .toLowerCase()
    .replace(/\s+/g, " ")
    .trim();
}

function asRegex(text) {
  const escaped = text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return new RegExp(escaped, "i");
}

async function ensureVisibleText(page, text, timeout = 25_000) {
  await expect(page.getByText(asRegex(text))).toBeVisible({ timeout });
}

function shortenError(error) {
  if (!error) {
    return "Unknown error";
  }
  const asText = String(error.message || error);
  return asText.length > 500 ? `${asText.slice(0, 500)}...` : asText;
}

async function waitForUiSettled(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
}

async function clickByTextAndWait(page, text, options = {}) {
  const locator = page.getByText(asRegex(text), { exact: false }).first();
  await expect(locator).toBeVisible({ timeout: options.timeout ?? 20_000 });
  await locator.click();
  await waitForUiSettled(page);
}

async function saveCheckpoint(page, name, fullPage = false) {
  await fs.mkdir(CHECKPOINTS_DIR, { recursive: true });
  const fileName = `${name.replace(/\s+/g, "-").toLowerCase()}.png`;
  const filePath = path.join(CHECKPOINTS_DIR, fileName);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function maybeSelectGoogleAccount(page, accountEmail) {
  const accountOption = page.getByText(asRegex(accountEmail)).first();
  if (await accountOption.isVisible({ timeout: 5_000 }).catch(() => false)) {
    await accountOption.click();
    await waitForUiSettled(page);
  }
}

async function withNewTabSupport(page, triggerAction) {
  const context = page.context();
  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await triggerAction();
  const popup = await popupPromise;
  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    return { targetPage: popup, openedInNewTab: true };
  }
  await page.waitForLoadState("domcontentloaded");
  return { targetPage: page, openedInNewTab: false };
}

async function ensureLoginPageContext(page) {
  const configuredUrl = process.env.SALEADS_BASE_URL?.trim();
  if (configuredUrl) {
    await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
    await waitForUiSettled(page);
  }

  const currentUrl = page.url();
  if (!/^https?:\/\//i.test(currentUrl)) {
    throw new Error(
      "No active SaleADS login page detected. Open the login page first, or provide SALEADS_BASE_URL."
    );
  }
}

async function runStep(results, stepName, handler, options = {}) {
  const precondition = options.precondition ?? true;
  const dependencyError = options.dependencyError ?? "Skipped because a prerequisite step failed.";
  if (!precondition) {
    results[stepName].status = "FAIL";
    results[stepName].details.push(dependencyError);
    return false;
  }

  try {
    const detail = await handler();
    results[stepName].status = "PASS";
    if (detail) {
      results[stepName].details.push(detail);
    }
    return true;
  } catch (error) {
    results[stepName].status = "FAIL";
    results[stepName].details.push(shortenError(error));
    return false;
  }
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  const results = {
    Login: { status: "FAIL", details: [] },
    "Mi Negocio menu": { status: "FAIL", details: [] },
    "Agregar Negocio modal": { status: "FAIL", details: [] },
    "Administrar Negocios view": { status: "FAIL", details: [] },
    "Información General": { status: "FAIL", details: [] },
    "Detalles de la Cuenta": { status: "FAIL", details: [] },
    "Tus Negocios": { status: "FAIL", details: [] },
    "Términos y Condiciones": { status: "FAIL", details: [] },
    "Política de Privacidad": { status: "FAIL", details: [] }
  };

  const checkpoints = [];
  const accountEmail = "juanlucasbarbiergarzon@gmail.com";
  let loginPassed = false;
  let miNegocioMenuPassed = false;
  let administrarNegociosPassed = false;

  await test.step("Step 1 - Login with Google", async () => {
    loginPassed = await runStep(results, "Login", async () => {
      await ensureLoginPageContext(page);

      const { targetPage, openedInNewTab } = await withNewTabSupport(page, async () => {
        const googleButton = page
          .locator("button, a, div[role='button']")
          .filter({ hasText: /google|sign in|iniciar sesi[oó]n|continuar/i })
          .first();

        await expect(googleButton).toBeVisible({ timeout: 30_000 });
        await googleButton.click();
        await waitForUiSettled(page);
      });

      await maybeSelectGoogleAccount(targetPage, accountEmail);

      if (openedInNewTab) {
        await targetPage.waitForEvent("close", { timeout: 60_000 }).catch(() => null);
        await page.bringToFront();
      }

      await ensureVisibleText(page, "Negocio", 60_000);
      const sidebar = page.locator("nav, aside").first();
      await expect(sidebar).toBeVisible({ timeout: 30_000 });

      const shot = await saveCheckpoint(page, "01-dashboard-loaded");
      checkpoints.push({ step: 1, path: shot });
      return "Main interface loaded and sidebar visible.";
    });
  });

  await test.step("Step 2 - Open Mi Negocio menu", async () => {
    miNegocioMenuPassed = await runStep(
      results,
      "Mi Negocio menu",
      async () => {
      await ensureVisibleText(page, "Negocio");
      await clickByTextAndWait(page, "Mi Negocio");
      await ensureVisibleText(page, "Agregar Negocio");
      await ensureVisibleText(page, "Administrar Negocios");

      const shot = await saveCheckpoint(page, "02-mi-negocio-expanded");
      checkpoints.push({ step: 2, path: shot });
      return "Submenu expanded and options are visible.";
      },
      { precondition: loginPassed, dependencyError: "Skipped because Login failed." }
    );
  });

  await test.step("Step 3 - Validate Agregar Negocio modal", async () => {
    await runStep(
      results,
      "Agregar Negocio modal",
      async () => {
      await clickByTextAndWait(page, "Agregar Negocio");
      await ensureVisibleText(page, "Crear Nuevo Negocio");
      await ensureVisibleText(page, "Nombre del Negocio");
      await ensureVisibleText(page, "Tienes 2 de 3 negocios");
      await ensureVisibleText(page, "Cancelar");
      await ensureVisibleText(page, "Crear Negocio");

      const nameInput = page.getByLabel(asRegex("Nombre del Negocio")).first();
      if (await nameInput.isVisible({ timeout: 5_000 }).catch(() => false)) {
        await nameInput.click();
        await nameInput.fill("Negocio Prueba Automatización");
      }

      const shot = await saveCheckpoint(page, "03-agregar-negocio-modal");
      checkpoints.push({ step: 3, path: shot });
      await clickByTextAndWait(page, "Cancelar");
      return "Modal validated and closed via Cancelar.";
      },
      { precondition: miNegocioMenuPassed, dependencyError: "Skipped because Mi Negocio menu step failed." }
    );
  });

  await test.step("Step 4 - Open Administrar Negocios", async () => {
    administrarNegociosPassed = await runStep(
      results,
      "Administrar Negocios view",
      async () => {
      const administrarOption = page.getByText(asRegex("Administrar Negocios")).first();
      if (!(await administrarOption.isVisible({ timeout: 3_000 }).catch(() => false))) {
        await clickByTextAndWait(page, "Mi Negocio");
      }

      await clickByTextAndWait(page, "Administrar Negocios");
      await ensureVisibleText(page, "Información General", 45_000);
      await ensureVisibleText(page, "Detalles de la Cuenta");
      await ensureVisibleText(page, "Tus Negocios");
      await ensureVisibleText(page, "Sección Legal");

      const shot = await saveCheckpoint(page, "04-administrar-negocios-page", true);
      checkpoints.push({ step: 4, path: shot });
      return "All main sections are visible.";
      },
      { precondition: miNegocioMenuPassed, dependencyError: "Skipped because Mi Negocio menu step failed." }
    );
  });

  await test.step("Step 5 - Validate Información General", async () => {
    await runStep(
      results,
      "Información General",
      async () => {
      await ensureVisibleText(page, "Información General");
      await ensureVisibleText(page, "BUSINESS PLAN");
      await ensureVisibleText(page, "Cambiar Plan");

      const infoSection = page
        .locator("section, div")
        .filter({ has: page.getByText(asRegex("Información General")) })
        .first();
      const infoText = await infoSection.innerText();
      const normalized = normalizeText(infoText);
      const emailMatch = infoText.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
      expect(emailMatch?.[0], "Expected visible user email in Información General").toBeTruthy();

      const candidateName = infoText
        .split("\n")
        .map((line) => line.trim())
        .find((line) => {
          if (!line) {
            return false;
          }
          const normalizedLine = normalizeText(line);
          if (normalizedLine.includes("@")) {
            return false;
          }
          if (
            normalizedLine.includes("informacion general") ||
            normalizedLine.includes("business plan") ||
            normalizedLine.includes("cambiar plan")
          ) {
            return false;
          }
          return /[a-zA-Z\u00C0-\u024F]{3,}/.test(line);
        });

      expect(candidateName, `Expected visible user name. Section text: ${normalized}`).toBeTruthy();
      return `User name and email visible (${emailMatch[0]}).`;
      },
      {
        precondition: administrarNegociosPassed,
        dependencyError: "Skipped because Administrar Negocios view step failed."
      }
    );
  });

  await test.step("Step 6 - Validate Detalles de la Cuenta", async () => {
    await runStep(
      results,
      "Detalles de la Cuenta",
      async () => {
      await ensureVisibleText(page, "Detalles de la Cuenta");
      await ensureVisibleText(page, "Cuenta creada");
      await ensureVisibleText(page, "Estado activo");
      await ensureVisibleText(page, "Idioma seleccionado");
      return "All expected detail labels visible.";
      },
      {
        precondition: administrarNegociosPassed,
        dependencyError: "Skipped because Administrar Negocios view step failed."
      }
    );
  });

  await test.step("Step 7 - Validate Tus Negocios", async () => {
    await runStep(
      results,
      "Tus Negocios",
      async () => {
      await ensureVisibleText(page, "Tus Negocios");
      await ensureVisibleText(page, "Agregar Negocio");
      await ensureVisibleText(page, "Tienes 2 de 3 negocios");

      const businessSection = page
        .locator("section, div")
        .filter({ has: page.getByText(asRegex("Tus Negocios")) })
        .first();
      const sectionText = await businessSection.innerText();
      expect(sectionText.trim().length, "Expected business list content in Tus Negocios").toBeGreaterThan(40);
      return "Business list section and quota text visible.";
      },
      {
        precondition: administrarNegociosPassed,
        dependencyError: "Skipped because Administrar Negocios view step failed."
      }
    );
  });

  await test.step("Step 8 - Validate Términos y Condiciones", async () => {
    await runStep(
      results,
      "Términos y Condiciones",
      async () => {
      await ensureVisibleText(page, "Sección Legal");

      const { targetPage, openedInNewTab } = await withNewTabSupport(page, async () => {
        await clickByTextAndWait(page, "Términos y Condiciones");
      });

      await expect(targetPage).toHaveURL(/https?:\/\//i);
      await ensureVisibleText(targetPage, "Términos y Condiciones", 45_000);
      const bodyText = normalizeText(await targetPage.locator("body").innerText());
      expect(bodyText.length).toBeGreaterThan(120);

      const shot = await saveCheckpoint(targetPage, "08-terminos-condiciones");
      checkpoints.push({ step: 8, path: shot });
      const finalUrl = targetPage.url();

      if (openedInNewTab) {
        await targetPage.close();
        await page.bringToFront();
        await waitForUiSettled(page);
      }
      return `Validated legal page. URL: ${finalUrl}`;
      },
      {
        precondition: administrarNegociosPassed,
        dependencyError: "Skipped because Administrar Negocios view step failed."
      }
    );
  });

  await test.step("Step 9 - Validate Política de Privacidad", async () => {
    await runStep(
      results,
      "Política de Privacidad",
      async () => {
      const { targetPage, openedInNewTab } = await withNewTabSupport(page, async () => {
        await clickByTextAndWait(page, "Política de Privacidad");
      });

      await expect(targetPage).toHaveURL(/https?:\/\//i);
      await ensureVisibleText(targetPage, "Política de Privacidad", 45_000);
      const bodyText = normalizeText(await targetPage.locator("body").innerText());
      expect(bodyText.length).toBeGreaterThan(120);

      const shot = await saveCheckpoint(targetPage, "09-politica-privacidad");
      checkpoints.push({ step: 9, path: shot });
      const finalUrl = targetPage.url();

      if (openedInNewTab) {
        await targetPage.close();
        await page.bringToFront();
        await waitForUiSettled(page);
      }
      return `Validated legal page. URL: ${finalUrl}`;
      },
      {
        precondition: administrarNegociosPassed,
        dependencyError: "Skipped because Administrar Negocios view step failed."
      }
    );
  });

  await test.step("Step 10 - Final report", async () => {
    const report = {
      testName: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      checkpoints,
      results
    };
    await fs.mkdir(path.dirname(REPORT_PATH), { recursive: true });
    await fs.writeFile(REPORT_PATH, JSON.stringify(report, null, 2), "utf8");
    console.log("FINAL_REPORT", JSON.stringify(results));

    const failedFields = Object.entries(results)
      .filter(([, value]) => value.status !== "PASS")
      .map(([name]) => name);
    expect(
      failedFields,
      `The following report fields failed validation: ${failedFields.join(", ")}`
    ).toEqual([]);
  });
});
