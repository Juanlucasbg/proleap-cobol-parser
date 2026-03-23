const path = require("node:path");
const fs = require("node:fs/promises");
const { test, expect } = require("@playwright/test");

const REPORT_DIR = path.join("test-results", "saleads-mi-negocio");
const SCREENSHOT_DIR = path.join(REPORT_DIR, "screenshots");
const REPORT_FILE = path.join(REPORT_DIR, "final-report.json");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const LOGIN_URL = process.env.SALEADS_LOGIN_URL;

const STEP_FIELDS = [
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

function initializeReport() {
  const results = {};
  for (const field of STEP_FIELDS) {
    results[field] = {
      status: "FAIL",
      details: "Not executed.",
    };
  }
  return results;
}

function normalizeWhitespace(value) {
  return value.replace(/\s+/g, " ").trim();
}

function sanitizeFileName(value) {
  return value
    .normalize("NFKD")
    .replace(/[^\w.-]+/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "")
    .toLowerCase();
}

async function waitForUi(page) {
  await Promise.allSettled([
    page.waitForLoadState("domcontentloaded", { timeout: 10000 }),
    page.waitForLoadState("networkidle", { timeout: 10000 }),
  ]);
  await page.waitForTimeout(400);
}

async function isVisible(locator, timeout = 4000) {
  try {
    await locator.waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function firstVisible(candidates, timeout = 5000) {
  for (const candidate of candidates) {
    const locator = candidate.first();
    if (await isVisible(locator, timeout)) {
      return locator;
    }
  }
  return null;
}

async function clickAndWait(page, locator) {
  await locator.click();
  await waitForUi(page);
}

async function saveScreenshot(page, name, artifacts, fullPage = false) {
  const fileName = `${sanitizeFileName(name)}.png`;
  const filePath = path.join(SCREENSHOT_DIR, fileName);
  await page.screenshot({ path: filePath, fullPage });
  artifacts.screenshots.push(filePath);
}

async function sectionContainerByHeading(page, headingPattern) {
  const heading = page.getByRole("heading", { name: headingPattern }).first();
  await expect(heading).toBeVisible();

  const sectionCandidate = heading.locator("xpath=ancestor::section[1]");
  if ((await sectionCandidate.count()) > 0) {
    return sectionCandidate.first();
  }

  return heading.locator("xpath=ancestor::div[1]").first();
}

async function runStep(report, stepName, prerequisites, callback) {
  const failedPrerequisite = prerequisites.find(
    (step) => report[step] && report[step].status !== "PASS",
  );

  if (failedPrerequisite) {
    report[stepName] = {
      status: "FAIL",
      details: `Not executed because prerequisite "${failedPrerequisite}" did not pass.`,
    };
    return;
  }

  try {
    await callback();
    report[stepName] = {
      status: "PASS",
      details: "All validations passed.",
    };
  } catch (error) {
    report[stepName] = {
      status: "FAIL",
      details: error instanceof Error ? error.message : String(error),
    };
  }
}

async function maybePickGoogleAccount(page) {
  const targetAccount = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
  if (await isVisible(targetAccount, 7000)) {
    await clickAndWait(page, targetAccount);
  }
}

async function validateLegalLink({
  page,
  linkPattern,
  headingPattern,
  screenshotName,
  reportKey,
  report,
  prerequisites,
  legalUrls,
  artifacts,
}) {
  await runStep(report, reportKey, prerequisites, async () => {
    const legalLink = await firstVisible([
      page.getByRole("link", { name: linkPattern }),
      page.getByRole("button", { name: linkPattern }),
      page.getByText(linkPattern),
    ]);

    if (!legalLink) {
      throw new Error(`Could not find legal link: ${linkPattern}`);
    }

    const beforeClickUrl = page.url();
    const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);

    await legalLink.click();
    const popup = await popupPromise;

    const legalPage = popup || page;
    await waitForUi(legalPage);

    if (!popup && page.url() === beforeClickUrl) {
      await page.waitForURL((url) => url !== beforeClickUrl, { timeout: 10000 }).catch(() => {});
    }

    await expect(
      legalPage.getByRole("heading", { name: headingPattern }).first(),
      `Expected legal heading ${headingPattern} to be visible.`,
    ).toBeVisible({ timeout: 20000 });

    const legalText = normalizeWhitespace(await legalPage.locator("body").innerText());
    if (legalText.length < 120) {
      throw new Error("Legal content appears too short or not visible.");
    }

    await saveScreenshot(legalPage, screenshotName, artifacts, true);
    legalUrls[reportKey] = legalPage.url();

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
      return;
    }

    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
  });
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  await fs.mkdir(SCREENSHOT_DIR, { recursive: true });

  const report = initializeReport();
  const legalUrls = {};
  const artifacts = { screenshots: [] };

  await runStep(report, "Login", [], async () => {
    if (LOGIN_URL) {
      await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "SALEADS_LOGIN_URL is required when starting from about:blank. Set an environment-specific login page URL.",
      );
    }

    const loginButton = await firstVisible([
      page.getByRole("button", {
        name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
      }),
      page.getByRole("link", {
        name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
      }),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
    ]);

    if (!loginButton) {
      throw new Error('Could not find "Sign in with Google" button.');
    }

    const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
    await clickAndWait(page, loginButton);
    const googlePopup = await popupPromise;

    if (googlePopup) {
      await waitForUi(googlePopup);
      await maybePickGoogleAccount(googlePopup);
      await waitForUi(page);
    } else {
      await maybePickGoogleAccount(page);
      await waitForUi(page);
    }

    const sidebar = await firstVisible(
      [
        page.locator("aside"),
        page.getByRole("navigation"),
        page.locator("nav"),
      ],
      15000,
    );

    if (!sidebar) {
      throw new Error("Left sidebar navigation is not visible after login.");
    }

    await expect(page.getByText(/Mi Negocio|Negocio/i).first()).toBeVisible({ timeout: 30000 });
    await saveScreenshot(page, "01-dashboard-loaded", artifacts);
  });

  await runStep(report, "Mi Negocio menu", ["Login"], async () => {
    const negocioSection = await firstVisible([
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i),
      page.getByText(/Mi Negocio/i),
    ]);

    if (!negocioSection) {
      throw new Error('Could not find "Negocio" section in left sidebar.');
    }

    await clickAndWait(page, negocioSection);

    const miNegocioOption = await firstVisible([
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i),
    ]);

    if (miNegocioOption) {
      await clickAndWait(page, miNegocioOption);
    }

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();
    await saveScreenshot(page, "02-mi-negocio-menu-expanded", artifacts);
  });

  await runStep(report, "Agregar Negocio modal", ["Mi Negocio menu"], async () => {
    const addBusinessButton = await firstVisible([
      page.getByRole("menuitem", { name: /^Agregar Negocio$/i }),
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);

    if (!addBusinessButton) {
      throw new Error('Could not find "Agregar Negocio" option.');
    }

    await clickAndWait(page, addBusinessButton);

    const modal = await firstVisible(
      [
        page.getByRole("dialog").filter({ hasText: /Crear Nuevo Negocio/i }),
        page.locator('[role="dialog"]').filter({ hasText: /Crear Nuevo Negocio/i }),
      ],
      15000,
    );

    if (!modal) {
      throw new Error('Could not find modal with title "Crear Nuevo Negocio".');
    }

    await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
    await expect(modal.getByLabel(/Nombre del Negocio/i)).toBeVisible();
    await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible();

    await saveScreenshot(page, "03-agregar-negocio-modal", artifacts);

    const businessNameInput = modal.getByLabel(/Nombre del Negocio/i);
    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page, modal.getByRole("button", { name: /^Cancelar$/i }));
  });

  await runStep(report, "Administrar Negocios view", ["Agregar Negocio modal"], async () => {
    if (!(await isVisible(page.getByText(/^Administrar Negocios$/i).first(), 3000))) {
      const miNegocioToggle = await firstVisible([
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i),
      ]);

      if (miNegocioToggle) {
        await clickAndWait(page, miNegocioToggle);
      }
    }

    const manageBusinessesOption = await firstVisible([
      page.getByRole("menuitem", { name: /^Administrar Negocios$/i }),
      page.getByRole("button", { name: /^Administrar Negocios$/i }),
      page.getByRole("link", { name: /^Administrar Negocios$/i }),
      page.getByText(/^Administrar Negocios$/i),
    ]);

    if (!manageBusinessesOption) {
      throw new Error('Could not find "Administrar Negocios" option.');
    }

    await clickAndWait(page, manageBusinessesOption);

    await expect(page.getByRole("heading", { name: /Informaci[oó]n General/i })).toBeVisible();
    await expect(page.getByRole("heading", { name: /Detalles de la Cuenta/i })).toBeVisible();
    await expect(page.getByRole("heading", { name: /Tus Negocios/i })).toBeVisible();
    await expect(page.getByRole("heading", { name: /Secci[oó]n Legal/i })).toBeVisible();

    await saveScreenshot(page, "04-administrar-negocios-view", artifacts, true);
  });

  await runStep(report, "Información General", ["Administrar Negocios view"], async () => {
    const infoSection = await sectionContainerByHeading(page, /Informaci[oó]n General/i);
    const sectionText = normalizeWhitespace(await infoSection.innerText());

    const emailMatch = sectionText.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
    if (!emailMatch) {
      throw new Error("User email is not visible in Información General.");
    }

    const userNameSignals = await Promise.all([
      isVisible(infoSection.locator("h1,h2,h3,h4,strong").first(), 2000),
      isVisible(infoSection.getByText(/nombre|usuario|perfil/i).first(), 2000),
    ]);

    if (!userNameSignals.some(Boolean)) {
      throw new Error("Could not confirm user name visibility in Información General.");
    }

    await expect(infoSection.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(infoSection.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
  });

  await runStep(report, "Detalles de la Cuenta", ["Administrar Negocios view"], async () => {
    const detailsSection = await sectionContainerByHeading(page, /Detalles de la Cuenta/i);

    await expect(detailsSection.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(detailsSection.getByText(/Estado activo/i)).toBeVisible();
    await expect(detailsSection.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runStep(report, "Tus Negocios", ["Administrar Negocios view"], async () => {
    const businessesSection = await sectionContainerByHeading(page, /Tus Negocios/i);

    await expect(businessesSection.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(
      businessesSection.getByRole("button", { name: /^Agregar Negocio$/i }).first(),
    ).toBeVisible();

    const listItemCount = await businessesSection
      .locator("li, tr, [role='row'], [class*='business'], [class*='negocio']")
      .count();
    if (listItemCount === 0) {
      throw new Error("Business list is not visible in Tus Negocios section.");
    }
  });

  await validateLegalLink({
    page,
    linkPattern: /T[eé]rminos y Condiciones/i,
    headingPattern: /T[eé]rminos y Condiciones/i,
    screenshotName: "05-terminos-y-condiciones",
    reportKey: "Términos y Condiciones",
    report,
    prerequisites: ["Administrar Negocios view"],
    legalUrls,
    artifacts,
  });

  await validateLegalLink({
    page,
    linkPattern: /Pol[ií]tica de Privacidad/i,
    headingPattern: /Pol[ií]tica de Privacidad/i,
    screenshotName: "06-politica-de-privacidad",
    reportKey: "Política de Privacidad",
    report,
    prerequisites: ["Administrar Negocios view", "Términos y Condiciones"],
    legalUrls,
    artifacts,
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    configuration: {
      loginUrlProvided: Boolean(LOGIN_URL),
      loginUrl: LOGIN_URL || null,
      googleAccountEmail: GOOGLE_ACCOUNT_EMAIL,
    },
    results: report,
    legalUrls,
    screenshots: artifacts.screenshots,
  };

  await fs.writeFile(REPORT_FILE, JSON.stringify(finalReport, null, 2), "utf8");
  console.log(JSON.stringify(finalReport, null, 2));

  const failedSteps = STEP_FIELDS.filter((step) => report[step].status !== "PASS");
  expect(
    failedSteps,
    `One or more workflow validations failed: ${failedSteps.join(", ")}`,
  ).toEqual([]);
});
