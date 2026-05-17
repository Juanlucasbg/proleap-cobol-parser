const fs = require("node:fs/promises");
const { test, expect } = require("@playwright/test");

const GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const BUSINESS_NAME = "Negocio Prueba Automatizacion";

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informaci\u00f3n General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "T\u00e9rminos y Condiciones",
  "Pol\u00edtica de Privacidad"
];

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function fileSlug(value) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");
}

async function waitForUi(page) {
  try {
    await page.waitForLoadState("networkidle", { timeout: 10_000 });
  } catch {
    await page.waitForLoadState("domcontentloaded", { timeout: 10_000 }).catch(() => {});
  }
}

async function checkpoint(page, testInfo, label, fullPage = false) {
  await page.screenshot({
    path: testInfo.outputPath(`${fileSlug(label)}.png`),
    fullPage
  });
}

async function findClickableByText(page, textOrRegex) {
  const matcher =
    textOrRegex instanceof RegExp ? textOrRegex : new RegExp(escapeRegExp(textOrRegex), "i");
  const candidates = [
    page.getByRole("button", { name: matcher }),
    page.getByRole("link", { name: matcher }),
    page.getByRole("menuitem", { name: matcher }),
    page.getByRole("tab", { name: matcher }),
    page.getByRole("option", { name: matcher }),
    page.getByText(matcher)
  ];

  for (const locator of candidates) {
    const first = locator.first();
    if ((await first.count()) > 0 && (await first.isVisible().catch(() => false))) {
      return first;
    }
  }

  throw new Error(`No visible clickable element found for pattern: ${matcher}`);
}

async function selectGoogleAccountIfVisible(pages, email) {
  const matcher = new RegExp(escapeRegExp(email), "i");

  for (const candidatePage of pages) {
    const accountOption = candidatePage.getByText(matcher).first();
    if (await accountOption.isVisible({ timeout: 3_000 }).catch(() => false)) {
      await accountOption.click({ timeout: 10_000 });
      await waitForUi(candidatePage);
      return true;
    }
  }

  return false;
}

async function isAppShellVisible(page) {
  const asideVisible = await page
    .locator("aside")
    .first()
    .isVisible({ timeout: 2_000 })
    .catch(() => false);
  if (asideVisible) {
    return true;
  }

  const sidebarTextVisible = await page
    .getByText(/Mi Negocio|Negocio|Dashboard/i)
    .first()
    .isVisible({ timeout: 2_000 })
    .catch(() => false);
  return sidebarTextVisible;
}

async function getUserNameCandidate(sectionText) {
  const lines = sectionText
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);

  return (
    lines.find(
      (line) =>
        !line.includes("@") &&
        !/informaci[o\u00f3]n general|business plan|cambiar plan/i.test(line) &&
        /[a-z]/i.test(line)
    ) || null
  );
}

async function openLegalPageAndReturn({
  appPage,
  context,
  linkText,
  headingText,
  screenshotLabel,
  testInfo
}) {
  const legalLink = await findClickableByText(appPage, linkText);
  const appUrlBeforeClick = appPage.url();
  const popupPromise = appPage.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);

  await legalLink.click({ timeout: 15_000 });
  await waitForUi(appPage);

  const popup = await popupPromise;
  const targetPage = popup || appPage;
  await targetPage.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
  await waitForUi(targetPage);

  const headingLocator = targetPage.getByRole("heading", { name: headingText }).first();
  if (!(await headingLocator.isVisible({ timeout: 10_000 }).catch(() => false))) {
    await expect(targetPage.getByText(headingText).first()).toBeVisible({ timeout: 20_000 });
  }

  const legalBodyText = (await targetPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
  if (legalBodyText.length < 120) {
    throw new Error("Legal page content appears too short to be valid.");
  }

  await checkpoint(targetPage, testInfo, screenshotLabel, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close().catch(() => {});
    await appPage.bringToFront();
  } else if (appPage.url() !== appUrlBeforeClick) {
    await appPage
      .goBack({ waitUntil: "domcontentloaded", timeout: 20_000 })
      .catch(async () => {
        await appPage.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded", timeout: 20_000 });
      });
    await waitForUi(appPage);
  }

  const activePage = context.pages().includes(appPage) ? appPage : context.pages()[0];
  return { finalUrl, activePage };
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const results = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const failures = {};
  const legalUrls = {
    terminosYCondiciones: null,
    politicaDePrivacidad: null
  };

  let appPage = page;
  let accountPageUrl = null;
  const initialUrl = page.url();

  const runStep = async (field, action, options = {}) => {
    const required = options.required || [];
    const missingPrerequisites = required.filter((requiredField) => results[requiredField] !== "PASS");
    if (missingPrerequisites.length > 0) {
      const reason = `Skipped because prerequisite validation(s) failed: ${missingPrerequisites.join(
        ", "
      )}`;
      failures[field] = reason;
      results[field] = "FAIL";
      return;
    }

    try {
      await action();
      results[field] = "PASS";
    } catch (error) {
      failures[field] = error instanceof Error ? error.message : String(error);
      results[field] = "FAIL";
    }
  };

  await runStep("Login", async () => {
    if (appPage.url() === "about:blank") {
      const baseUrl = process.env.SALEADS_BASE_URL || process.env.BASE_URL;
      if (!baseUrl) {
        throw new Error(
          "The browser is on about:blank and no SALEADS_BASE_URL/BASE_URL was provided."
        );
      }
      await appPage.goto(baseUrl, { waitUntil: "domcontentloaded", timeout: 30_000 });
      await waitForUi(appPage);
    }

    const loginButton = await findClickableByText(
      appPage,
      /^Google$|Sign in with Google|Inicia sesi[o\u00f3]n con Google|Continuar con Google/i
    );
    const popupPromise = appPage.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);

    await loginButton.click({ timeout: 15_000 });
    await waitForUi(appPage);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
      await waitForUi(popup);
    }

    await selectGoogleAccountIfVisible(context.pages(), GOOGLE_EMAIL);

    const loginStart = Date.now();
    while (Date.now() - loginStart < 45_000) {
      for (const candidatePage of context.pages()) {
        if (await isAppShellVisible(candidatePage)) {
          appPage = candidatePage;
          await appPage.bringToFront();
          await waitForUi(appPage);
          await checkpoint(appPage, testInfo, "dashboard-loaded");
          return;
        }
      }
      await appPage.waitForTimeout(1_000);
    }

    throw new Error("Main application interface or left sidebar did not appear after login.");
  });

  await runStep(
    "Mi Negocio menu",
    async () => {
    if (!(await isAppShellVisible(appPage))) {
      throw new Error("Left sidebar navigation is not visible.");
    }

    const negocioSection = await findClickableByText(appPage, /^Negocio$/i);
    await negocioSection.click({ timeout: 10_000 });
    await waitForUi(appPage);

    const miNegocioOption = await findClickableByText(appPage, /Mi Negocio/i);
    await miNegocioOption.click({ timeout: 10_000 });
    await waitForUi(appPage);

    await expect(appPage.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 15_000 });
    await expect(appPage.getByText(/Administrar Negocios/i).first()).toBeVisible({
      timeout: 15_000
    });
    await checkpoint(appPage, testInfo, "mi-negocio-menu-expanded");
    },
    { required: ["Login"] }
  );

  await runStep(
    "Agregar Negocio modal",
    async () => {
    const agregarNegocioOption = await findClickableByText(appPage, /^Agregar Negocio$/i);
    await agregarNegocioOption.click({ timeout: 10_000 });
    await waitForUi(appPage);

    const modalTitle = appPage.getByText(/Crear Nuevo Negocio/i).first();
    await expect(modalTitle).toBeVisible({ timeout: 15_000 });

    const namedInputByLabel = appPage.getByLabel(/Nombre del Negocio/i).first();
    const inputVisible = await namedInputByLabel.isVisible({ timeout: 3_000 }).catch(() => false);
    const businessNameInput = inputVisible
      ? namedInputByLabel
      : appPage.getByPlaceholder(/Nombre del Negocio/i).first();
    await expect(businessNameInput).toBeVisible({ timeout: 15_000 });

    await expect(appPage.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({
      timeout: 15_000
    });
    await expect(appPage.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible({
      timeout: 15_000
    });
    await expect(appPage.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible({
      timeout: 15_000
    });
    await checkpoint(appPage, testInfo, "agregar-negocio-modal");

    await businessNameInput.click({ timeout: 10_000 });
    await businessNameInput.fill(BUSINESS_NAME, { timeout: 10_000 });
    await appPage.getByRole("button", { name: /Cancelar/i }).first().click({ timeout: 10_000 });
    await waitForUi(appPage);
    },
    { required: ["Mi Negocio menu"] }
  );

  await runStep(
    "Administrar Negocios view",
    async () => {
    const administrarOptionVisible = await appPage
      .getByText(/Administrar Negocios/i)
      .first()
      .isVisible({ timeout: 3_000 })
      .catch(() => false);

    if (!administrarOptionVisible) {
      const negocioSection = await findClickableByText(appPage, /^Negocio$/i);
      await negocioSection.click({ timeout: 10_000 });
      await waitForUi(appPage);
      const miNegocioOption = await findClickableByText(appPage, /Mi Negocio/i);
      await miNegocioOption.click({ timeout: 10_000 });
      await waitForUi(appPage);
    }

    const administrarNegociosOption = await findClickableByText(appPage, /Administrar Negocios/i);
    await administrarNegociosOption.click({ timeout: 10_000 });
    await waitForUi(appPage);

    await expect(appPage.getByText(/Informaci[o\u00f3]n General/i).first()).toBeVisible({
      timeout: 20_000
    });
    await expect(appPage.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({
      timeout: 20_000
    });
    await expect(appPage.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(appPage.getByText(/Secci[o\u00f3]n Legal/i).first()).toBeVisible({
      timeout: 20_000
    });

    accountPageUrl = appPage.url();
    await checkpoint(appPage, testInfo, "administrar-negocios-account-page", true);
    },
    { required: ["Mi Negocio menu"] }
  );

  await runStep(
    "Informaci\u00f3n General",
    async () => {
    const infoGeneralSection = appPage
      .locator("section, div")
      .filter({ hasText: /Informaci[o\u00f3]n General/i })
      .first();
    await expect(infoGeneralSection).toBeVisible({ timeout: 15_000 });

    const infoText = await infoGeneralSection.innerText();
    const usernameCandidate = await getUserNameCandidate(infoText);
    if (!usernameCandidate) {
      throw new Error("Could not identify a visible user name in Informacion General.");
    }

    await expect(appPage.getByText(/[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}/i).first()).toBeVisible({
      timeout: 15_000
    });
    await expect(appPage.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 15_000 });
    await expect(appPage.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible({
      timeout: 15_000
    });
    },
    { required: ["Administrar Negocios view"] }
  );

  await runStep(
    "Detalles de la Cuenta",
    async () => {
    await expect(appPage.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 15_000 });
    await expect(appPage.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 15_000 });
    await expect(appPage.getByText(/Idioma seleccionado/i).first()).toBeVisible({
      timeout: 15_000
    });
    },
    { required: ["Administrar Negocios view"] }
  );

  await runStep(
    "Tus Negocios",
    async () => {
    const businessesSection = appPage
      .locator("section, div")
      .filter({ hasText: /Tus Negocios/i })
      .first();
    await expect(businessesSection).toBeVisible({ timeout: 15_000 });

    await expect(appPage.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible({
      timeout: 15_000
    });
    await expect(appPage.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({
      timeout: 15_000
    });

    const sectionText = (await businessesSection.innerText()).replace(/\s+/g, " ").trim();
    if (sectionText.length < 30) {
      throw new Error("Business list content appears empty.");
    }
    },
    { required: ["Administrar Negocios view"] }
  );

  await runStep(
    "T\u00e9rminos y Condiciones",
    async () => {
    if (accountPageUrl && appPage.url() !== accountPageUrl) {
      await appPage.goto(accountPageUrl, { waitUntil: "domcontentloaded", timeout: 30_000 });
      await waitForUi(appPage);
    }

    const legalResult = await openLegalPageAndReturn({
      appPage,
      context,
      linkText: /T[e\u00e9]rminos y Condiciones/i,
      headingText: /T[e\u00e9]rminos y Condiciones/i,
      screenshotLabel: "terminos-y-condiciones",
      testInfo
    });

    legalUrls.terminosYCondiciones = legalResult.finalUrl;
    appPage = legalResult.activePage;
    },
    { required: ["Administrar Negocios view"] }
  );

  await runStep(
    "Pol\u00edtica de Privacidad",
    async () => {
    if (accountPageUrl && appPage.url() !== accountPageUrl) {
      await appPage.goto(accountPageUrl, { waitUntil: "domcontentloaded", timeout: 30_000 });
      await waitForUi(appPage);
    }

    const legalResult = await openLegalPageAndReturn({
      appPage,
      context,
      linkText: /Pol[i\u00ed]tica de Privacidad/i,
      headingText: /Pol[i\u00ed]tica de Privacidad/i,
      screenshotLabel: "politica-de-privacidad",
      testInfo
    });

    legalUrls.politicaDePrivacidad = legalResult.finalUrl;
    appPage = legalResult.activePage;
    },
    { required: ["Administrar Negocios view"] }
  );

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    environment: {
      initialUrl,
      appUrl: accountPageUrl || appPage.url()
    },
    results,
    legalUrls,
    failures
  };

  const reportJson = JSON.stringify(finalReport, null, 2);
  await fs.writeFile(testInfo.outputPath("final-report.json"), reportJson, "utf8");
  await testInfo.attach("final-report.json", {
    body: Buffer.from(reportJson, "utf8"),
    contentType: "application/json"
  });

  const failedFields = Object.entries(results)
    .filter(([, status]) => status !== "PASS")
    .map(([field]) => field);
  expect(
    failedFields,
    `Validation failures:\n${JSON.stringify(failures, null, 2)}\nFinal report:\n${reportJson}`
  ).toEqual([]);
});
