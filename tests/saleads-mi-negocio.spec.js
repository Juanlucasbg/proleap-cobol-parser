const fs = require("node:fs");
const path = require("node:path");
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

test("saleads_mi_negocio_full_test", async ({ browser }, testInfo) => {
  const context = await browser.newContext();
  const page = await context.newPage();
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, { status: "FAIL", details: "Not executed" }]));
  const failures = [];
  const evidenceDir = path.join(testInfo.outputDir, "saleads-evidence");
  fs.mkdirSync(evidenceDir, { recursive: true });

  const runStep = async (fieldName, action) => {
    try {
      await action();
      report[fieldName] = { status: "PASS", details: "Validation passed" };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      report[fieldName] = { status: "FAIL", details: message };
      failures.push(fieldName);
      await captureScreenshot(page, evidenceDir, `${toSlug(fieldName)}-failure.png`, true).catch(() => {});
    }
  };

  try {
    await runStep("Login", async () => {
      await openLoginPage(page);
      const loginButton = await findClickableByAnyText(page, [
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Ingresar con Google",
        "Continuar con Google",
      ]);

      const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
      await loginButton.click();
      await waitForUiLoad(page);

      const popup = await popupPromise;
      const googleSurface = popup ?? page;
      await waitForUiLoad(googleSurface);

      const accountOption = googleSurface.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false });
      if (await accountOption.first().isVisible().catch(() => false)) {
        await accountOption.first().click();
        await waitForUiLoad(googleSurface);
      }

      if (popup) {
        await popup.waitForEvent("close", { timeout: 35000 }).catch(() => {});
      }

      await waitForUiLoad(page);
      await firstVisible(
        [
          page.locator("aside"),
          page.locator("nav"),
          page.getByText(/mi negocio|negocio|dashboard/i),
        ],
        30000,
      );
      await captureScreenshot(page, evidenceDir, "01-dashboard-loaded.png", true);
    });

    await runStep("Mi Negocio menu", async () => {
      requirePassed(report, "Login", "Mi Negocio menu");
      const negocioSection = await findClickableByAnyText(page, ["Negocio"]);
      await clickWithUiWait(page, negocioSection);

      const miNegocioOption = await findClickableByAnyText(page, ["Mi Negocio"]);
      await clickWithUiWait(page, miNegocioOption);

      await firstVisible([page.getByText(/Agregar Negocio/i)], 12000);
      await firstVisible([page.getByText(/Administrar Negocios/i)], 12000);
      await captureScreenshot(page, evidenceDir, "02-mi-negocio-expanded-menu.png", false);
    });

    await runStep("Agregar Negocio modal", async () => {
      requirePassed(report, "Mi Negocio menu", "Agregar Negocio modal");
      const agregarNegocio = await findClickableByAnyText(page, ["Agregar Negocio"]);
      await clickWithUiWait(page, agregarNegocio);

      await firstVisible(
        [
          page.getByRole("heading", { name: /Crear Nuevo Negocio/i }),
          page.getByText(/Crear Nuevo Negocio/i),
        ],
        15000,
      );
      await firstVisible(
        [
          page.getByLabel(/Nombre del Negocio/i),
          page.getByPlaceholder(/Nombre del Negocio/i),
          page.getByText(/Nombre del Negocio/i),
        ],
        10000,
      );
      await firstVisible([page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)], 10000);
      await firstVisible([page.getByRole("button", { name: /Cancelar/i }), page.getByText(/^Cancelar$/i)], 10000);
      await firstVisible([page.getByRole("button", { name: /Crear Negocio/i }), page.getByText(/Crear Negocio/i)], 10000);

      await captureScreenshot(page, evidenceDir, "03-agregar-negocio-modal.png", false);

      const businessNameInput = page.getByLabel(/Nombre del Negocio/i).first();
      if (await businessNameInput.isVisible().catch(() => false)) {
        await businessNameInput.fill("Negocio Prueba Automatización");
      }

      const cancelButton = await firstVisible([page.getByRole("button", { name: /Cancelar/i }), page.getByText(/^Cancelar$/i)], 10000);
      await clickWithUiWait(page, cancelButton);
    });

    await runStep("Administrar Negocios view", async () => {
      requirePassed(report, "Mi Negocio menu", "Administrar Negocios view");
      const administrarVisible = await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false);
      if (!administrarVisible) {
        const miNegocioOption = await findClickableByAnyText(page, ["Mi Negocio"]);
        await clickWithUiWait(page, miNegocioOption);
      }

      const administrarNegocios = await findClickableByAnyText(page, ["Administrar Negocios"]);
      await clickWithUiWait(page, administrarNegocios);

      await firstVisible([page.getByText(/Información General/i)], 15000);
      await firstVisible([page.getByText(/Detalles de la Cuenta/i)], 15000);
      await firstVisible([page.getByText(/Tus Negocios/i)], 15000);
      await firstVisible([page.getByText(/Sección Legal/i)], 15000);
      await captureScreenshot(page, evidenceDir, "04-administrar-negocios-page-full.png", true);
    });

    await runStep("Información General", async () => {
      requirePassed(report, "Administrar Negocios view", "Información General");
      const section = await sectionByHeading(page, /Información General/i);
      const sectionText = await section.innerText();
      const emailMatches = sectionText.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi) || [];
      if (emailMatches.length === 0) {
        throw new Error("No visible email detected in Información General.");
      }

      const lines = sectionText
        .split("\n")
        .map((line) => line.trim())
        .filter(Boolean);
      const hasPotentialName = lines.some((line) => {
        if (line.includes("@")) return false;
        if (line.length < 3) return false;
        return !/información general|business plan|cambiar plan|cuenta creada|estado activo|idioma seleccionado|correo|email/i.test(
          line,
        );
      });
      if (!hasPotentialName) {
        throw new Error("No visible user name detected in Información General.");
      }

      await firstVisible([page.getByText(/BUSINESS PLAN/i)], 10000);
      await firstVisible([page.getByRole("button", { name: /Cambiar Plan/i }), page.getByText(/Cambiar Plan/i)], 10000);
    });

    await runStep("Detalles de la Cuenta", async () => {
      requirePassed(report, "Administrar Negocios view", "Detalles de la Cuenta");
      const section = await sectionByHeading(page, /Detalles de la Cuenta/i);
      const sectionText = await section.innerText();
      assertContains(sectionText, /Cuenta creada/i, "Missing 'Cuenta creada' in Detalles de la Cuenta.");
      assertContains(sectionText, /Estado activo/i, "Missing 'Estado activo' in Detalles de la Cuenta.");
      assertContains(sectionText, /Idioma seleccionado/i, "Missing 'Idioma seleccionado' in Detalles de la Cuenta.");
    });

    await runStep("Tus Negocios", async () => {
      requirePassed(report, "Administrar Negocios view", "Tus Negocios");
      const section = await sectionByHeading(page, /Tus Negocios/i);
      const sectionText = await section.innerText();

      await firstVisible([section.getByRole("button", { name: /Agregar Negocio/i }), section.getByText(/Agregar Negocio/i)], 10000);
      assertContains(sectionText, /Tienes\s*2\s*de\s*3\s*negocios/i, "Missing 'Tienes 2 de 3 negocios' in Tus Negocios.");

      const itemCount =
        (await section.locator("li").count()) +
        (await section.locator("[role='listitem']").count()) +
        (await section.locator("tbody tr").count()) +
        (await section.locator("[class*='card']").count());
      if (itemCount === 0 && sectionText.split("\n").filter((line) => line.trim().length > 0).length < 6) {
        throw new Error("Business list is not clearly visible in Tus Negocios.");
      }
    });

    await runStep("Términos y Condiciones", async () => {
      requirePassed(report, "Administrar Negocios view", "Términos y Condiciones");
      const finalUrl = await validateLegalNavigation({
        page,
        context,
        linkText: "Términos y Condiciones",
        expectedHeading: /Términos y Condiciones/i,
        screenshotName: "05-terminos-y-condiciones.png",
        evidenceDir,
      });
      report["Términos y Condiciones"].details = `Validation passed. URL: ${finalUrl}`;
    });

    await runStep("Política de Privacidad", async () => {
      requirePassed(report, "Administrar Negocios view", "Política de Privacidad");
      const finalUrl = await validateLegalNavigation({
        page,
        context,
        linkText: "Política de Privacidad",
        expectedHeading: /Política de Privacidad/i,
        screenshotName: "06-politica-de-privacidad.png",
        evidenceDir,
      });
      report["Política de Privacidad"].details = `Validation passed. URL: ${finalUrl}`;
    });
  } finally {
    const reportPath = path.join(evidenceDir, "final-report.json");
    fs.writeFileSync(reportPath, JSON.stringify(report, null, 2), "utf-8");
    await testInfo.attach("saleads-mi-negocio-final-report", {
      body: JSON.stringify(report, null, 2),
      contentType: "application/json",
    });
    await context.close();
  }

  expect(
    failures,
    `One or more validation groups failed. Final report:\n${JSON.stringify(report, null, 2)}`,
  ).toEqual([]);
});

async function openLoginPage(page) {
  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL;
  if (!loginUrl) {
    throw new Error(
      "Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL) to the current environment login page. The test does not hardcode domains.",
    );
  }

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUiLoad(page);
}

async function validateLegalNavigation({ page, context, linkText, expectedHeading, screenshotName, evidenceDir }) {
  const appPage = page;
  const appUrlBefore = appPage.url();
  const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
  const legalLink = await findClickableByAnyText(appPage, [linkText]);
  await legalLink.scrollIntoViewIfNeeded();
  await legalLink.click();

  let destinationPage = await popupPromise;
  const openedNewTab = Boolean(destinationPage);
  destinationPage = destinationPage ?? appPage;

  await waitForUiLoad(destinationPage);
  await firstVisible(
    [
      destinationPage.getByRole("heading", { name: expectedHeading }),
      destinationPage.getByText(expectedHeading),
    ],
    15000,
  );

  const destinationText = (await destinationPage.locator("body").innerText()).trim();
  if (destinationText.length < 120) {
    throw new Error(`Legal page for '${linkText}' does not contain enough visible content.`);
  }

  const finalUrl = destinationPage.url();
  await captureScreenshot(destinationPage, evidenceDir, screenshotName, true);

  if (openedNewTab) {
    await destinationPage.close();
    await appPage.bringToFront();
    await waitForUiLoad(appPage);
  } else if (appPage.url() !== appUrlBefore) {
    await appPage
      .goBack({ waitUntil: "domcontentloaded" })
      .catch(async () => appPage.goto(appUrlBefore, { waitUntil: "domcontentloaded" }));
    await waitForUiLoad(appPage);
  }

  return finalUrl;
}

async function clickWithUiWait(page, locator) {
  await locator.scrollIntoViewIfNeeded();
  await locator.click();
  await waitForUiLoad(page);
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 25000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function findClickableByAnyText(page, textOptions) {
  const candidates = [];
  for (const text of textOptions) {
    const pattern = new RegExp(escapeRegex(text), "i");
    candidates.push(
      page.getByRole("button", { name: pattern }).first(),
      page.getByRole("link", { name: pattern }).first(),
      page.getByRole("menuitem", { name: pattern }).first(),
      page.getByRole("tab", { name: pattern }).first(),
      page.getByText(pattern).first(),
    );
  }
  return firstVisible(candidates, 15000);
}

async function firstVisible(locators, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    for (const locator of locators) {
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error("Expected one matching visible element, but none appeared in time.");
}

async function sectionByHeading(page, headingPattern) {
  const heading = await firstVisible([page.getByRole("heading", { name: headingPattern }).first(), page.getByText(headingPattern).first()], 15000);
  const section = heading.locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
  if (!(await section.isVisible().catch(() => false))) {
    throw new Error(`Could not resolve visible section for heading '${headingPattern}'.`);
  }
  return section;
}

async function captureScreenshot(page, evidenceDir, fileName, fullPage) {
  const filePath = path.join(evidenceDir, fileName);
  await page.screenshot({ path: filePath, fullPage });
}

function assertContains(value, pattern, errorMessage) {
  if (!pattern.test(value)) {
    throw new Error(errorMessage);
  }
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function toSlug(value) {
  return value
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function requirePassed(report, prerequisiteField, currentField) {
  if (report[prerequisiteField]?.status !== "PASS") {
    throw new Error(`Blocked '${currentField}' because prerequisite '${prerequisiteField}' did not pass.`);
  }
}
