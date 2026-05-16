const fs = require("fs");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
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

function createReport() {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, "NOT_RUN"]));
}

function normalizeName(value) {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForTimeout(700);
}

async function clickAndWait(locator, page) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function firstVisible(candidates) {
  for (const candidate of candidates) {
    const locator = candidate.first();
    if (await locator.isVisible().catch(() => false)) {
      return locator;
    }
  }

  return null;
}

async function captureCheckpoint(page, testInfo, checkpointName, fullPage = false) {
  const screenshotPath = testInfo.outputPath(`${normalizeName(checkpointName)}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(checkpointName, { path: screenshotPath, contentType: "image/png" });
}

async function runStep(report, key, stepLogic, dependencies = []) {
  const blockedBy = dependencies.find((dependency) => report[dependency] !== "PASS");
  if (blockedBy) {
    report[key] = `BLOCKED: dependency '${blockedBy}' was not PASS`;
    return;
  }

  try {
    await stepLogic();
    report[key] = "PASS";
  } catch (error) {
    report[key] = `FAIL: ${error.message}`;
  }
}

async function maybeSelectGoogleAccount(googlePage) {
  const accountOption = googlePage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });
  if (await accountOption.isVisible().catch(() => false)) {
    await clickAndWait(accountOption, googlePage);
  }
}

async function ensureLoginPageContext(page) {
  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL || process.env.BASE_URL;
  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
    return;
  }

  if (page.url() === "about:blank") {
    throw new Error(
      "No login page was preloaded. Set SALEADS_LOGIN_URL (or SALEADS_URL / BASE_URL) to run the test in any environment."
    );
  }
}

async function validateLegalPage({ page, context, linkText, headingRegex, screenshotName, testInfo }) {
  const link = await firstVisible([
    page.getByRole("link", { name: new RegExp(`^${escapeRegExp(linkText)}$`, "i") }),
    page.getByRole("button", { name: new RegExp(`^${escapeRegExp(linkText)}$`, "i") }),
    page.getByText(new RegExp(`^${escapeRegExp(linkText)}$`, "i")),
  ]);

  expect(link, `Legal link '${linkText}' should be visible`).not.toBeNull();

  const popupPromise = context.waitForEvent("page", { timeout: 6000 }).catch(() => null);
  await link.click();
  await waitForUi(page);

  const popup = await popupPromise;
  const legalPage = popup || page;

  await legalPage.waitForLoadState("domcontentloaded").catch(() => {});
  await expect(legalPage.getByText(headingRegex).first()).toBeVisible();

  const bodyText = (await legalPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
  expect(bodyText.length).toBeGreaterThan(120);

  await captureCheckpoint(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
  } else {
    await legalPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createReport();
  const evidence = {};

  await runStep(report, "Login", async () => {
    await ensureLoginPageContext(page);

    const signInWithGoogle = await firstVisible([
      page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
      page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google|google/i),
    ]);

    expect(signInWithGoogle, "Sign in with Google control should be visible").not.toBeNull();

    const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
    await signInWithGoogle.click();
    await waitForUi(page);

    const googlePopup = await popupPromise;
    if (googlePopup) {
      await googlePopup.waitForLoadState("domcontentloaded").catch(() => {});
      await maybeSelectGoogleAccount(googlePopup);
      await googlePopup.waitForEvent("close", { timeout: 30000 }).catch(() => {});
      await page.bringToFront();
    } else if (page.url().includes("accounts.google")) {
      await maybeSelectGoogleAccount(page);
    }

    await waitForUi(page);

    const sidebar = await firstVisible([
      page.locator("aside").filter({ hasText: /Negocio|Mi Negocio/i }).first(),
      page.locator("nav").filter({ hasText: /Negocio|Mi Negocio/i }).first(),
    ]);

    expect(sidebar, "Left sidebar should be visible after login").not.toBeNull();
    await expect(sidebar).toBeVisible();

    await captureCheckpoint(page, testInfo, "dashboard-loaded");
  });

  await runStep(
    report,
    "Mi Negocio menu",
    async () => {
      const negocioLabel = await firstVisible([
        page.getByText(/^Negocio$/i),
        page.getByRole("button", { name: /^Negocio$/i }),
      ]);
      expect(negocioLabel, "Negocio section should be visible").not.toBeNull();

      const miNegocioOption = await firstVisible([
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i),
      ]);
      expect(miNegocioOption, "Mi Negocio option should be visible").not.toBeNull();
      await clickAndWait(miNegocioOption, page);

      await expect(page.getByText(/^Agregar Negocio$/i)).toBeVisible();
      await expect(page.getByText(/^Administrar Negocios$/i)).toBeVisible();

      await captureCheckpoint(page, testInfo, "mi-negocio-menu-expanded");
    },
    ["Login"]
  );

  await runStep(
    report,
    "Agregar Negocio modal",
    async () => {
      const agregarNegocioItem = await firstVisible([
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i),
      ]);
      expect(agregarNegocioItem, "Agregar Negocio option should be visible").not.toBeNull();
      await clickAndWait(agregarNegocioItem, page);

      await expect(page.getByText(/^Crear Nuevo Negocio$/i)).toBeVisible();
      await expect(page.getByLabel(/Nombre del Negocio/i)).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
      await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible();

      await captureCheckpoint(page, testInfo, "agregar-negocio-modal");

      await page.getByLabel(/Nombre del Negocio/i).fill("Negocio Prueba Automatización");
      await clickAndWait(page.getByRole("button", { name: /^Cancelar$/i }), page);
    },
    ["Mi Negocio menu"]
  );

  await runStep(
    report,
    "Administrar Negocios view",
    async () => {
      const adminOptionVisible = await page.getByText(/^Administrar Negocios$/i).isVisible().catch(() => false);
      if (!adminOptionVisible) {
        const miNegocioOption = await firstVisible([
          page.getByRole("button", { name: /^Mi Negocio$/i }),
          page.getByRole("link", { name: /^Mi Negocio$/i }),
          page.getByText(/^Mi Negocio$/i),
        ]);
        expect(miNegocioOption, "Mi Negocio should be visible to reopen menu").not.toBeNull();
        await clickAndWait(miNegocioOption, page);
      }

      const administrarNegocios = await firstVisible([
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i),
      ]);
      expect(administrarNegocios, "Administrar Negocios option should be visible").not.toBeNull();
      await clickAndWait(administrarNegocios, page);

      await expect(page.getByText(/^Información General$/i)).toBeVisible();
      await expect(page.getByText(/^Detalles de la Cuenta$/i)).toBeVisible();
      await expect(page.getByText(/^Tus Negocios$/i)).toBeVisible();
      await expect(page.getByText(/Sección Legal/i)).toBeVisible();

      await captureCheckpoint(page, testInfo, "administrar-negocios-page", true);
    },
    ["Agregar Negocio modal"]
  );

  await runStep(
    report,
    "Información General",
    async () => {
      const pageText = await page.locator("body").innerText();
      const compactText = pageText.replace(/\s+/g, " ");
      const emailMatch = compactText.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi);
      expect(emailMatch && emailMatch.length > 0).toBeTruthy();
      expect(compactText).toMatch(/BUSINESS PLAN/i);

      const hasNameLikeText = compactText
        .split(" ")
        .filter(Boolean)
        .some((token, index, allTokens) => {
          const twoWords = `${token} ${allTokens[index + 1] || ""}`.trim();
          return /^[A-Za-zÀ-ÿ'\-]{2,}\s[A-Za-zÀ-ÿ'\-]{2,}$/.test(twoWords);
        });
      expect(hasNameLikeText).toBeTruthy();

      await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
    },
    ["Administrar Negocios view"]
  );

  await runStep(
    report,
    "Detalles de la Cuenta",
    async () => {
      await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
      await expect(page.getByText(/Estado activo/i)).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
    },
    ["Administrar Negocios view"]
  );

  await runStep(
    report,
    "Tus Negocios",
    async () => {
      await expect(page.getByText(/^Tus Negocios$/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();

      const sectionText = (await page.locator("body").innerText()).replace(/\s+/g, " ");
      expect(sectionText).toMatch(/Tus Negocios/i);
    },
    ["Administrar Negocios view"]
  );

  await runStep(
    report,
    "Términos y Condiciones",
    async () => {
      evidence.termsUrl = await validateLegalPage({
        page,
        context,
        linkText: "Términos y Condiciones",
        headingRegex: /Términos y Condiciones/i,
        screenshotName: "terminos-y-condiciones",
        testInfo,
      });
    },
    ["Administrar Negocios view"]
  );

  await runStep(
    report,
    "Política de Privacidad",
    async () => {
      evidence.privacyUrl = await validateLegalPage({
        page,
        context,
        linkText: "Política de Privacidad",
        headingRegex: /Política de Privacidad/i,
        screenshotName: "politica-de-privacidad",
        testInfo,
      });
    },
    ["Administrar Negocios view"]
  );

  const finalReport = {
    report,
    evidence,
    generatedAt: new Date().toISOString(),
  };

  const reportPath = testInfo.outputPath("mi-negocio-final-report.json");
  fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2));
  await testInfo.attach("mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  const failedSteps = Object.entries(report).filter(([, value]) => value !== "PASS");
  expect(
    failedSteps,
    `The following steps did not pass:\n${failedSteps.map(([name, status]) => `- ${name}: ${status}`).join("\n")}`
  ).toEqual([]);
});
