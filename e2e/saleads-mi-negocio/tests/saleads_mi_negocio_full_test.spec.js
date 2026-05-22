const path = require("node:path");
const fs = require("node:fs/promises");
const { test, expect } = require("@playwright/test");

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informacion General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Terminos y Condiciones",
  "Politica de Privacidad",
];

const SCREENSHOT_DIR = path.resolve(__dirname, "..", "artifacts", "screenshots");
const REPORT_PATH = path.resolve(__dirname, "..", "artifacts", "saleads_mi_negocio_report.json");

const ACCOUNT_EMAIL = process.env.GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";

function toSlug(value) {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(1200);
}

async function findFirstVisible(candidates) {
  for (const candidate of candidates) {
    if (!candidate) {
      continue;
    }

    const first = candidate.first();
    if (await first.isVisible().catch(() => false)) {
      return first;
    }
  }

  return null;
}

async function clickAndWait(page, locator) {
  await locator.click();
  await waitForUi(page);
}

async function takeCheckpoint(page, fileName, fullPage = false) {
  await fs.mkdir(SCREENSHOT_DIR, { recursive: true });
  await page.screenshot({
    path: path.join(SCREENSHOT_DIR, fileName),
    fullPage,
  });
}

async function handleGoogleAccountSelection(page) {
  await waitForUi(page);

  const accountChoice = await findFirstVisible([
    page.getByText(ACCOUNT_EMAIL, { exact: false }),
    page.getByRole("button", { name: new RegExp(escapeRegExp(ACCOUNT_EMAIL), "i") }),
    page.getByRole("link", { name: new RegExp(escapeRegExp(ACCOUNT_EMAIL), "i") }),
  ]);

  if (accountChoice) {
    await clickAndWait(page, accountChoice);
    return;
  }

  const emailInput = await findFirstVisible([
    page.getByLabel(/email|correo/i),
    page.getByPlaceholder(/email|correo/i),
    page.locator('input[type="email"]'),
  ]);

  if (emailInput) {
    await emailInput.fill(ACCOUNT_EMAIL);

    const nextButton = await findFirstVisible([
      page.getByRole("button", { name: /next|siguiente/i }),
      page.getByText(/next|siguiente/i),
    ]);

    if (nextButton) {
      await clickAndWait(page, nextButton);
    }
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  test.setTimeout(10 * 60 * 1000);

  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const failureDetails = [];
  const legalUrls = {
    terminosYCondiciones: null,
    politicaDePrivacidad: null,
  };

  async function runValidation(field, callback) {
    try {
      await callback();
      report[field] = "PASS";
    } catch (error) {
      report[field] = "FAIL";
      failureDetails.push(`${field}: ${error.message}`);
      await takeCheckpoint(page, `failure-${toSlug(field)}.png`, true).catch(() => {});
    }
  }

  const loginUrl = process.env.SALEADS_LOGIN_URL;
  await expect(
    loginUrl,
    "SALEADS_LOGIN_URL is required. Provide the login URL for dev, staging, or production."
  ).toBeTruthy();

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  await runValidation("Login", async () => {
    const googleButton = await findFirstVisible([
      page.getByRole("button", { name: /google/i }),
      page.getByText(/sign in with google|iniciar sesion con google|continuar con google/i),
      page.getByRole("link", { name: /google/i }),
    ]);

    expect(googleButton, "Google login button was not found.").toBeTruthy();

    const popupPromise = page.waitForEvent("popup", { timeout: 10000 }).catch(() => null);
    await clickAndWait(page, googleButton);
    const popup = await popupPromise;

    if (popup) {
      await handleGoogleAccountSelection(popup);
      await popup.waitForTimeout(1500);
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await handleGoogleAccountSelection(page);
      await waitForUi(page);
    }

    const mainContent = await findFirstVisible([
      page.locator("main"),
      page.getByRole("main"),
      page.locator('[class*="dashboard"]'),
    ]);
    expect(mainContent, "Main application content did not appear after login.").toBeTruthy();

    const sidebar = await findFirstVisible([
      page.locator("aside").filter({ hasText: /negocio|mi negocio/i }),
      page.getByRole("navigation").filter({ hasText: /negocio|mi negocio/i }),
      page.locator('[class*="sidebar"]').filter({ hasText: /negocio|mi negocio/i }),
    ]);
    expect(sidebar, "Left sidebar navigation is not visible.").toBeTruthy();

    await takeCheckpoint(page, "01-dashboard-loaded.png", true);
  });

  await runValidation("Mi Negocio menu", async () => {
    const negocioSection = await findFirstVisible([
      page.getByText(/^Negocio$/i),
      page.getByText(/Negocio/i),
    ]);
    expect(negocioSection, "The 'Negocio' section is not visible in the sidebar.").toBeTruthy();

    const miNegocioEntry = await findFirstVisible([
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/^Mi Negocio$/i),
    ]);
    expect(miNegocioEntry, "The 'Mi Negocio' option is not visible.").toBeTruthy();

    await clickAndWait(page, miNegocioEntry);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

    await takeCheckpoint(page, "02-mi-negocio-expanded.png");
  });

  await runValidation("Agregar Negocio modal", async () => {
    const agregarNegocio = await findFirstVisible([
      page.getByRole("button", { name: /agregar negocio/i }),
      page.getByRole("link", { name: /agregar negocio/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);
    expect(agregarNegocio, "'Agregar Negocio' option is not available.").toBeTruthy();

    await clickAndWait(page, agregarNegocio);

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Cancelar/i).first()).toBeVisible();
    await expect(page.getByText(/Crear Negocio/i).first()).toBeVisible();

    const businessNameInput = await findFirstVisible([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.getByRole("dialog").locator("input"),
    ]);
    expect(businessNameInput, "'Nombre del Negocio' input was not found in the modal.").toBeTruthy();

    await businessNameInput.fill("Negocio Prueba Automatizacion");
    await takeCheckpoint(page, "03-agregar-negocio-modal.png");

    const cancelarButton = await findFirstVisible([
      page.getByRole("button", { name: /^Cancelar$/i }),
      page.getByText(/^Cancelar$/i),
    ]);
    expect(cancelarButton, "'Cancelar' button was not found in the modal.").toBeTruthy();
    await clickAndWait(page, cancelarButton);
  });

  await runValidation("Administrar Negocios view", async () => {
    const administrarVisible = await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      const miNegocioEntry = await findFirstVisible([
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/^Mi Negocio$/i),
      ]);
      expect(miNegocioEntry, "Could not re-open 'Mi Negocio' menu.").toBeTruthy();
      await clickAndWait(page, miNegocioEntry);
    }

    const administrarNegocios = await findFirstVisible([
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByText(/^Administrar Negocios$/i),
    ]);
    expect(administrarNegocios, "'Administrar Negocios' option is not visible.").toBeTruthy();

    await clickAndWait(page, administrarNegocios);

    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();

    await takeCheckpoint(page, "04-administrar-negocios-view.png", true);
  });

  await runValidation("Informacion General", async () => {
    const section = page.locator("section, div").filter({ hasText: /Informaci[oó]n General/i }).first();
    await expect(section).toBeVisible();

    const sectionText = await section.innerText();
    expect(sectionText).toMatch(/BUSINESS PLAN/i);
    expect(sectionText).toMatch(/Cambiar Plan/i);
    expect(sectionText).toMatch(new RegExp(escapeRegExp(ACCOUNT_EMAIL), "i"));

    const lines = sectionText
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean);
    const probableNameLine = lines.find(
      (line) =>
        !/informaci[oó]n general|business plan|cambiar plan|@|plan/i.test(line) &&
        /[a-z]/i.test(line)
    );
    expect(probableNameLine, "A user name line was not detected in 'Informacion General'.").toBeTruthy();
  });

  await runValidation("Detalles de la Cuenta", async () => {
    const section = page.locator("section, div").filter({ hasText: /Detalles de la Cuenta/i }).first();
    await expect(section).toBeVisible();

    const sectionText = await section.innerText();
    expect(sectionText).toMatch(/Cuenta creada/i);
    expect(sectionText).toMatch(/Estado activo/i);
    expect(sectionText).toMatch(/Idioma seleccionado/i);
  });

  await runValidation("Tus Negocios", async () => {
    const section = page.locator("section, div").filter({ hasText: /Tus Negocios/i }).first();
    await expect(section).toBeVisible();

    const sectionText = await section.innerText();
    expect(sectionText).toMatch(/Agregar Negocio/i);
    expect(sectionText).toMatch(/Tienes 2 de 3 negocios/i);

    const businessItems = await section.locator("li, [role='row'], [class*='business'], [data-testid*='business']").count();
    expect(businessItems, "No business list entries were detected in 'Tus Negocios'.").toBeGreaterThan(0);
  });

  async function validateLegalLink(linkText, headingPattern, screenshotFileName, reportKey) {
    const legalLink = await findFirstVisible([
      page.getByRole("link", { name: new RegExp(escapeRegExp(linkText), "i") }),
      page.getByText(new RegExp(`^${escapeRegExp(linkText)}$`, "i")),
      page.getByText(new RegExp(escapeRegExp(linkText), "i")),
    ]);
    expect(legalLink, `Could not find legal link '${linkText}'.`).toBeTruthy();

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickAndWait(page, legalLink);
    const popup = await popupPromise;
    const targetPage = popup || page;

    await waitForUi(targetPage);

    const heading = await findFirstVisible([
      targetPage.getByRole("heading", { name: headingPattern }),
      targetPage.getByText(headingPattern),
    ]);
    expect(heading, `Heading '${headingPattern}' was not found.`).toBeTruthy();

    const bodyText = await targetPage.locator("body").innerText();
    expect(bodyText.length, "Legal page appears to be empty.").toBeGreaterThan(150);

    await takeCheckpoint(targetPage, screenshotFileName, true);
    legalUrls[reportKey] = targetPage.url();

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
      await waitForUi(page);
    }
  }

  await runValidation("Terminos y Condiciones", async () => {
    await validateLegalLink(
      "Términos y Condiciones",
      /T[eé]rminos y Condiciones/i,
      "05-terminos-y-condiciones.png",
      "terminosYCondiciones"
    );
  });

  await runValidation("Politica de Privacidad", async () => {
    await validateLegalLink(
      "Política de Privacidad",
      /Pol[ií]tica de Privacidad/i,
      "06-politica-de-privacidad.png",
      "politicaDePrivacidad"
    );
  });

  await fs.mkdir(path.dirname(REPORT_PATH), { recursive: true });
  const finalReport = {
    generatedAt: new Date().toISOString(),
    accountEmail: ACCOUNT_EMAIL,
    results: report,
    legalUrls,
    failures: failureDetails,
  };
  await fs.writeFile(REPORT_PATH, JSON.stringify(finalReport, null, 2), "utf8");
  // eslint-disable-next-line no-console
  console.log(`Final report written at: ${REPORT_PATH}`);
  // eslint-disable-next-line no-console
  console.log(JSON.stringify(finalReport, null, 2));

  expect(
    failureDetails,
    `One or more workflow validations failed:\n${failureDetails.join("\n")}`
  ).toEqual([]);
});
