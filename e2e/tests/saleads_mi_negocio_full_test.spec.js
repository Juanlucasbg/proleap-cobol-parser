const fs = require("node:fs");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const START_URL = process.env.SALEADS_START_URL || process.env.SALEADS_URL || process.env.BASE_URL;

function timestamp() {
  return new Date().toISOString().replaceAll(":", "-");
}

function sanitizeFileName(name) {
  return name
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9-_]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .toLowerCase();
}

async function waitForUiToSettle(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);

  try {
    await page.waitForLoadState("networkidle", { timeout: 8000 });
  } catch {
    // Some screens keep background requests active indefinitely.
  }
}

async function isVisible(locator) {
  try {
    return await locator.first().isVisible();
  } catch {
    return false;
  }
}

async function firstVisible(candidates, errorMessage) {
  for (const locator of candidates) {
    if (await isVisible(locator)) {
      return locator.first();
    }
  }

  throw new Error(errorMessage);
}

async function clickAndWait(locator, page) {
  await locator.click();
  await waitForUiToSettle(page);
}

async function maybeSelectGoogleAccount(targetPage) {
  const accountCandidate = targetPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });
  if (await isVisible(accountCandidate)) {
    await clickAndWait(accountCandidate, targetPage);
    return true;
  }

  const accountButtonCandidate = targetPage
    .getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") });
  if (await isVisible(accountButtonCandidate)) {
    await clickAndWait(accountButtonCandidate, targetPage);
    return true;
  }

  return false;
}

async function screenshot(page, dir, name, fullPage = false) {
  const fileName = `${sanitizeFileName(name)}.png`;
  const filePath = path.join(dir, fileName);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

function markResult(report, field, status, details) {
  report.results[field] = {
    status,
    details,
  };
}

async function executeStep(report, field, action) {
  try {
    await action();
    markResult(report, field, "PASS", "");
  } catch (error) {
    markResult(report, field, "FAIL", error.message);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const runId = timestamp();
  const artifactDir = path.join(process.cwd(), "artifacts", "saleads-mi-negocio", runId);
  fs.mkdirSync(artifactDir, { recursive: true });

  const report = {
    test_name: "saleads_mi_negocio_full_test",
    generated_at: new Date().toISOString(),
    start_url: START_URL || "(no explicit URL provided, using current page)",
    screenshots_dir: artifactDir,
    legal_urls: {
      "Términos y Condiciones": "",
      "Política de Privacidad": "",
    },
    results: {},
  };

  if (START_URL) {
    await page.goto(START_URL, { waitUntil: "domcontentloaded" });
  }
  await waitForUiToSettle(page);

  await executeStep(report, "Login", async () => {
    const loginButton = await firstVisible(
      [
        page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google/i }),
        page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google/i }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google/i),
      ],
      "Google login button was not found.",
    );

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await loginButton.click();
    await waitForUiToSettle(page);

    const popupPage = await popupPromise;
    if (popupPage) {
      await popupPage.waitForLoadState("domcontentloaded");
      await maybeSelectGoogleAccount(popupPage);
      await popupPage.waitForTimeout(1000);
      await popupPage.waitForEvent("close", { timeout: 20000 }).catch(() => {});
      await page.bringToFront();
    } else {
      await maybeSelectGoogleAccount(page);
    }

    await waitForUiToSettle(page);
    const sidebar = await firstVisible(
      [
        page.locator("aside"),
        page.getByRole("navigation"),
        page.locator('[class*="sidebar"], [id*="sidebar"]'),
      ],
      "Sidebar was not visible after login.",
    );
    await expect(sidebar).toBeVisible();

    await screenshot(page, artifactDir, "01-dashboard-loaded");
  });

  await executeStep(report, "Mi Negocio menu", async () => {
    const negocioOption = await firstVisible(
      [
        page.getByRole("button", { name: /^negocio$/i }),
        page.getByRole("link", { name: /^negocio$/i }),
        page.getByText(/^negocio$/i),
      ],
      "The 'Negocio' menu option was not found.",
    );
    await clickAndWait(negocioOption, page);

    const miNegocioOption = await firstVisible(
      [
        page.getByRole("button", { name: /^mi negocio$/i }),
        page.getByRole("link", { name: /^mi negocio$/i }),
        page.getByText(/^mi negocio$/i),
      ],
      "The 'Mi Negocio' option was not found.",
    );
    await clickAndWait(miNegocioOption, page);

    await expect(page.getByText(/^agregar negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/^administrar negocios$/i).first()).toBeVisible();

    await screenshot(page, artifactDir, "02-mi-negocio-menu-expanded");
  });

  await executeStep(report, "Agregar Negocio modal", async () => {
    const addBusinessMenuOption = await firstVisible(
      [
        page.getByRole("button", { name: /^agregar negocio$/i }),
        page.getByRole("link", { name: /^agregar negocio$/i }),
        page.getByText(/^agregar negocio$/i),
      ],
      "The 'Agregar Negocio' option was not found.",
    );
    await clickAndWait(addBusinessMenuOption, page);

    const modalTitle = page.getByText(/^crear nuevo negocio$/i).first();
    await expect(modalTitle).toBeVisible();
    const nameInput = await firstVisible(
      [page.getByLabel(/^nombre del negocio$/i), page.getByPlaceholder(/nombre del negocio/i)],
      "The 'Nombre del Negocio' field was not found in modal.",
    );
    await expect(nameInput).toBeVisible();
    await expect(page.getByText(/tienes\s+2\s+de\s+3\s+negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^cancelar$/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^crear negocio$/i }).first()).toBeVisible();

    await screenshot(page, artifactDir, "03-agregar-negocio-modal");

    await nameInput.click();
    await nameInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page.getByRole("button", { name: /^cancelar$/i }).first(), page);
  });

  await executeStep(report, "Administrar Negocios view", async () => {
    if (!(await isVisible(page.getByText(/^administrar negocios$/i).first()))) {
      const miNegocioOption = await firstVisible(
        [
          page.getByRole("button", { name: /^mi negocio$/i }),
          page.getByRole("link", { name: /^mi negocio$/i }),
          page.getByText(/^mi negocio$/i),
        ],
        "Could not expand 'Mi Negocio' menu to find 'Administrar Negocios'.",
      );
      await clickAndWait(miNegocioOption, page);
    }

    const manageBusinesses = await firstVisible(
      [
        page.getByRole("button", { name: /^administrar negocios$/i }),
        page.getByRole("link", { name: /^administrar negocios$/i }),
        page.getByText(/^administrar negocios$/i),
      ],
      "The 'Administrar Negocios' option was not found.",
    );
    await clickAndWait(manageBusinesses, page);

    await expect(page.getByText(/^informaci[oó]n general$/i).first()).toBeVisible();
    await expect(page.getByText(/^detalles de la cuenta$/i).first()).toBeVisible();
    await expect(page.getByText(/^tus negocios$/i).first()).toBeVisible();
    await expect(page.getByText(/^secci[oó]n legal$/i).first()).toBeVisible();

    await screenshot(page, artifactDir, "04-administrar-negocios-page", true);
  });

  await executeStep(report, "Información General", async () => {
    const infoSection = page.locator("section,div").filter({ hasText: /informaci[oó]n general/i }).first();
    await expect(infoSection).toBeVisible();

    const infoText = await infoSection.innerText();
    const lines = infoText
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean);

    const hasEmail = lines.some((line) => /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(line));
    if (!hasEmail) {
      throw new Error("User email was not detected in 'Información General'.");
    }

    const hasUserName = lines.some(
      (line) =>
        line.length >= 3 &&
        !/informaci[oó]n general|business plan|cambiar plan|@/i.test(line),
    );
    if (!hasUserName) {
      throw new Error("User name was not detected in 'Información General'.");
    }

    await expect(page.getByText(/business plan/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^cambiar plan$/i }).first()).toBeVisible();
  });

  await executeStep(report, "Detalles de la Cuenta", async () => {
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible();
  });

  await executeStep(report, "Tus Negocios", async () => {
    await expect(page.getByText(/^tus negocios$/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^agregar negocio$/i }).first()).toBeVisible();
    await expect(page.getByText(/tienes\s+2\s+de\s+3\s+negocios/i).first()).toBeVisible();
  });

  async function validateLegalLink(linkName, headingRegex, screenshotName) {
    const appPage = page;
    const previousUrl = appPage.url();
    const link = await firstVisible(
      [
        appPage.getByRole("link", { name: new RegExp(`^${linkName}$`, "i") }),
        appPage.getByText(new RegExp(`^${linkName}$`, "i")),
      ],
      `Could not find legal link '${linkName}'.`,
    );

    const newTabPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
    await link.click();
    const newTab = await newTabPromise;

    const legalPage = newTab || appPage;
    await waitForUiToSettle(legalPage);

    await expect(legalPage.getByText(headingRegex).first()).toBeVisible();
    const bodyText = await legalPage.locator("body").innerText();
    if (bodyText.replace(/\s+/g, " ").trim().length < 120) {
      throw new Error(`Legal content for '${linkName}' appears empty or too short.`);
    }

    report.legal_urls[linkName] = legalPage.url();
    await screenshot(legalPage, artifactDir, screenshotName, true);

    if (newTab) {
      await newTab.close();
      await appPage.bringToFront();
      await waitForUiToSettle(appPage);
      return;
    }

    if (appPage.url() !== previousUrl) {
      await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
      await waitForUiToSettle(appPage);
    }
  }

  await executeStep(report, "Términos y Condiciones", async () => {
    await validateLegalLink(
      "Términos y Condiciones",
      /t[eé]rminos y condiciones/i,
      "05-terminos-y-condiciones",
    );
  });

  await executeStep(report, "Política de Privacidad", async () => {
    await validateLegalLink(
      "Política de Privacidad",
      /pol[ií]tica de privacidad/i,
      "06-politica-de-privacidad",
    );
  });

  const reportPath = path.join(artifactDir, "mi-negocio-final-report.json");
  fs.writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");

  const failedFields = Object.entries(report.results)
    .filter(([, data]) => data.status === "FAIL")
    .map(([field, data]) => `${field}: ${data.details}`);

  expect(failedFields, `Final report saved at ${reportPath}`).toEqual([]);
});
