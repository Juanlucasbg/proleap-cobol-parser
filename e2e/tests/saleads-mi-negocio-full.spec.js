const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");
const path = require("node:path");

const GOOGLE_EMAIL =
  process.env.SALEADS_GOOGLE_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const SALEADS_URL =
  process.env.SALEADS_URL || process.env.BASE_URL || process.env.APP_URL;

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

function emptyReport() {
  return Object.fromEntries(REPORT_FIELDS.map((key) => [key, "FAIL"]));
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(900);
}

async function captureCheckpoint(page, testInfo, name, fullPage = false) {
  const fileName = `${Date.now()}-${name}.png`;
  const target = testInfo.outputPath(fileName);
  await page.screenshot({ path: target, fullPage });
  await testInfo.attach(name, { path: target, contentType: "image/png" });
  return target;
}

async function firstVisible(locators, description) {
  for (const locator of locators) {
    const count = await locator.count();
    const limit = Math.min(count, 8);

    for (let i = 0; i < limit; i += 1) {
      const candidate = locator.nth(i);
      if (await candidate.isVisible()) {
        return candidate;
      }
    }
  }

  throw new Error(`No visible element found for: ${description}`);
}

async function getClickableByText(scope, regex, description) {
  return firstVisible(
    [
      scope.getByRole("button", { name: regex }),
      scope.getByRole("link", { name: regex }),
      scope.getByRole("menuitem", { name: regex }),
      scope.getByRole("tab", { name: regex }),
      scope.getByText(regex),
    ],
    description
  );
}

async function isAnyVisible(locators) {
  for (const locator of locators) {
    const count = await locator.count();
    const limit = Math.min(count, 6);

    for (let i = 0; i < limit; i += 1) {
      if (await locator.nth(i).isVisible()) {
        return true;
      }
    }
  }

  return false;
}

async function clickAndWait(locator, page) {
  await locator.click();
  await waitForUi(page);
}

async function ensureOnLoginOrDashboard(page) {
  if (SALEADS_URL) {
    await page.goto(SALEADS_URL, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
    return;
  }

  if (page.url() === "about:blank") {
    throw new Error(
      "No app URL available. Set SALEADS_URL (or BASE_URL/APP_URL) to run in your environment."
    );
  }
}

async function maybeSelectGoogleAccount(targetPage) {
  const hasAccountChooser = await isAnyVisible([
    targetPage.getByText(new RegExp(GOOGLE_EMAIL, "i")),
    targetPage.getByRole("button", { name: new RegExp(GOOGLE_EMAIL, "i") }),
    targetPage.getByRole("link", { name: new RegExp(GOOGLE_EMAIL, "i") }),
  ]);

  if (hasAccountChooser) {
    const account = await firstVisible(
      [
        targetPage.getByText(new RegExp(GOOGLE_EMAIL, "i")),
        targetPage.getByRole("button", { name: new RegExp(GOOGLE_EMAIL, "i") }),
        targetPage.getByRole("link", { name: new RegExp(GOOGLE_EMAIL, "i") }),
      ],
      `Google account ${GOOGLE_EMAIL}`
    );
    await account.click();
    await waitForUi(targetPage);
  }
}

async function ensureSidebarVisible(page) {
  const sidebar = await firstVisible(
    [
      page.locator("aside"),
      page.locator("nav").filter({ hasText: /Negocio|Mi Negocio|Dashboard/i }),
      page.locator("[class*='sidebar']"),
    ],
    "left sidebar"
  );
  await expect(sidebar).toBeVisible();
  return sidebar;
}

async function expandMiNegocioMenu(page) {
  const sidebar = await ensureSidebarVisible(page);

  const openState = await isAnyVisible([
    sidebar.getByText(/Agregar Negocio/i),
    sidebar.getByText(/Administrar Negocios/i),
  ]);
  if (openState) {
    return sidebar;
  }

  const negocioCandidate = await getClickableByText(
    sidebar,
    /Negocio/i,
    "Negocio section"
  ).catch(() => null);
  if (negocioCandidate) {
    await clickAndWait(negocioCandidate, page);
  }

  const miNegocio = await getClickableByText(
    sidebar,
    /Mi Negocio/i,
    "Mi Negocio menu item"
  );
  await clickAndWait(miNegocio, page);
  return sidebar;
}

function validateEmailExists(sectionText) {
  return /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/.test(sectionText);
}

function validateLikelyNameExists(sectionText) {
  const lines = sectionText
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);

  return lines.some(
    (line) =>
      /^[\p{L}][\p{L}\s.'-]{2,}$/u.test(line) &&
      !/informaci[oó]n|business plan|cambiar plan|cuenta/i.test(line)
  );
}

async function runStep(report, errors, stepName, runner) {
  try {
    await runner();
    report[stepName] = "PASS";
  } catch (error) {
    errors.push(`${stepName}: ${error.message}`);
    report[stepName] = "FAIL";
  }
}

async function validateLegalDestination(page, testInfo, options) {
  const { linkText, headingText, screenshotName } = options;
  const appUrlBefore = page.url();
  const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
  const legalLink = await getClickableByText(page, linkText, `Legal link ${linkText}`);
  await legalLink.click();

  const popup = await popupPromise;
  const targetPage = popup || page;
  await waitForUi(targetPage);

  await firstVisible(
    [
      targetPage.getByRole("heading", { name: headingText }),
      targetPage.getByText(headingText),
    ],
    `Heading ${headingText}`
  );

  const legalBody = (await targetPage.locator("body").innerText()).trim();
  if (legalBody.length < 140) {
    throw new Error("Legal content appears too short.");
  }

  const screenshotPath = await captureCheckpoint(
    targetPage,
    testInfo,
    screenshotName,
    true
  );
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close().catch(() => {});
    await page.bringToFront();
  } else if (page.url() !== appUrlBefore) {
    const backResult = await page.goBack({ waitUntil: "domcontentloaded" }).catch(
      () => null
    );
    if (!backResult && /^https?:\/\//.test(appUrlBefore)) {
      await page.goto(appUrlBefore, { waitUntil: "domcontentloaded" });
    }
    await waitForUi(page);
  }

  return { finalUrl, screenshotPath };
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = emptyReport();
  const errors = [];
  const evidence = {};
  const finalUrls = {};

  await runStep(report, errors, "Login", async () => {
    await ensureOnLoginOrDashboard(page);
    await waitForUi(page);

    const alreadyLoggedIn = await isAnyVisible([
      page.getByText(/Mi Negocio/i),
      page.getByText(/Negocio/i),
      page.locator("aside"),
    ]);

    if (!alreadyLoggedIn) {
      const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
      const loginButton = await getClickableByText(
        page,
        /sign in with google|iniciar sesion con google|ingresar con google|continuar con google|google/i,
        "Google login button"
      );
      await clickAndWait(loginButton, page);

      const popup = await popupPromise;
      if (popup) {
        await waitForUi(popup);
        await maybeSelectGoogleAccount(popup);
      } else {
        await maybeSelectGoogleAccount(page);
      }
    }

    await ensureSidebarVisible(page);
    evidence.dashboard = await captureCheckpoint(page, testInfo, "dashboard-loaded", true);
  });

  await runStep(report, errors, "Mi Negocio menu", async () => {
    const sidebar = await expandMiNegocioMenu(page);

    await firstVisible([sidebar.getByText(/Agregar Negocio/i)], "Agregar Negocio in menu");
    await firstVisible(
      [sidebar.getByText(/Administrar Negocios/i)],
      "Administrar Negocios in menu"
    );

    evidence.miNegocioMenu = await captureCheckpoint(
      page,
      testInfo,
      "mi-negocio-expanded-menu",
      false
    );
  });

  await runStep(report, errors, "Agregar Negocio modal", async () => {
    const addBusinessMenu = await getClickableByText(
      page,
      /Agregar Negocio/i,
      "Agregar Negocio menu item"
    );
    await clickAndWait(addBusinessMenu, page);

    const modal = await firstVisible(
      [
        page.getByRole("dialog"),
        page.locator("[role='dialog']"),
        page.locator(".modal"),
      ],
      "Crear Nuevo Negocio modal"
    );

    await firstVisible(
      [modal.getByText(/Crear Nuevo Negocio/i), page.getByText(/Crear Nuevo Negocio/i)],
      "Modal title Crear Nuevo Negocio"
    );
    await firstVisible(
      [
        modal.getByLabel(/Nombre del Negocio/i),
        modal.getByPlaceholder(/Nombre del Negocio/i),
        modal.getByText(/Nombre del Negocio/i),
      ],
      "Nombre del Negocio input"
    );
    await firstVisible([modal.getByText(/Tienes 2 de 3 negocios/i)], "Limit text");
    await firstVisible([modal.getByRole("button", { name: /Cancelar/i })], "Cancelar button");
    await firstVisible(
      [modal.getByRole("button", { name: /Crear Negocio/i })],
      "Crear Negocio button"
    );

    evidence.agregarNegocioModal = await captureCheckpoint(
      page,
      testInfo,
      "agregar-negocio-modal",
      false
    );

    const nameField = await firstVisible(
      [
        modal.getByLabel(/Nombre del Negocio/i),
        modal.getByPlaceholder(/Nombre del Negocio/i),
        modal.locator("input[type='text']").first(),
      ],
      "Modal name field"
    );
    await nameField.click();
    await nameField.fill("Negocio Prueba Automatizacion");

    const cancel = await firstVisible(
      [modal.getByRole("button", { name: /Cancelar/i })],
      "Cancelar button"
    );
    await clickAndWait(cancel, page);
  });

  await runStep(report, errors, "Administrar Negocios view", async () => {
    await expandMiNegocioMenu(page);

    const adminBusinesses = await getClickableByText(
      page,
      /Administrar Negocios/i,
      "Administrar Negocios menu item"
    );
    await clickAndWait(adminBusinesses, page);

    await firstVisible([page.getByText(/Informaci[oó]n General/i)], "Informacion General");
    await firstVisible([page.getByText(/Detalles de la Cuenta/i)], "Detalles de la Cuenta");
    await firstVisible([page.getByText(/Tus Negocios/i)], "Tus Negocios");
    await firstVisible([page.getByText(/Secci[oó]n Legal/i)], "Seccion Legal");

    evidence.administrarNegocios = await captureCheckpoint(
      page,
      testInfo,
      "administrar-negocios-page",
      true
    );
  });

  await runStep(report, errors, "Información General", async () => {
    const infoSection = await firstVisible(
      [
        page.locator("section, article, div").filter({ hasText: /Informaci[oó]n General/i }),
        page.locator("main"),
      ],
      "Informacion General section"
    );

    const sectionText = (await infoSection.innerText()).trim();
    if (!validateLikelyNameExists(sectionText)) {
      throw new Error("Could not confirm user name in Informacion General.");
    }
    if (!validateEmailExists(sectionText)) {
      throw new Error("Could not confirm user email in Informacion General.");
    }

    await firstVisible([page.getByText(/BUSINESS PLAN/i)], "BUSINESS PLAN text");
    await firstVisible(
      [page.getByRole("button", { name: /Cambiar Plan/i }), page.getByText(/Cambiar Plan/i)],
      "Cambiar Plan button"
    );
  });

  await runStep(report, errors, "Detalles de la Cuenta", async () => {
    const detailsSection = await firstVisible(
      [
        page.locator("section, article, div").filter({ hasText: /Detalles de la Cuenta/i }),
        page.locator("main"),
      ],
      "Detalles de la Cuenta section"
    );
    const detailsText = await detailsSection.innerText();

    if (!/Cuenta creada/i.test(detailsText)) {
      throw new Error("Cuenta creada is not visible.");
    }
    if (!/Estado activo/i.test(detailsText)) {
      throw new Error("Estado activo is not visible.");
    }
    if (!/Idioma seleccionado/i.test(detailsText)) {
      throw new Error("Idioma seleccionado is not visible.");
    }
  });

  await runStep(report, errors, "Tus Negocios", async () => {
    const businessSection = await firstVisible(
      [
        page.locator("section, article, div").filter({ hasText: /Tus Negocios/i }),
        page.locator("main"),
      ],
      "Tus Negocios section"
    );

    const addButton = await firstVisible(
      [
        businessSection.getByRole("button", { name: /Agregar Negocio/i }),
        page.getByRole("button", { name: /Agregar Negocio/i }),
      ],
      "Agregar Negocio button in Tus Negocios"
    );
    await expect(addButton).toBeVisible();

    await firstVisible(
      [businessSection.getByText(/Tienes 2 de 3 negocios/i), page.getByText(/Tienes 2 de 3 negocios/i)],
      "Business count text"
    );

    const businessText = (await businessSection.innerText()).trim();
    if (businessText.length < 30) {
      throw new Error("Business list section seems empty.");
    }
  });

  await runStep(report, errors, "Términos y Condiciones", async () => {
    const legal = await validateLegalDestination(page, testInfo, {
      linkText: /T[eé]rminos y Condiciones/i,
      headingText: /T[eé]rminos y Condiciones/i,
      screenshotName: "terminos-y-condiciones",
    });
    finalUrls.terminosYCondiciones = legal.finalUrl;
    evidence.terminosYCondiciones = legal.screenshotPath;
  });

  await runStep(report, errors, "Política de Privacidad", async () => {
    const legal = await validateLegalDestination(page, testInfo, {
      linkText: /Pol[ií]tica de Privacidad/i,
      headingText: /Pol[ií]tica de Privacidad/i,
      screenshotName: "politica-de-privacidad",
    });
    finalUrls.politicaDePrivacidad = legal.finalUrl;
    evidence.politicaDePrivacidad = legal.screenshotPath;
  });

  const artifactsDir = path.resolve(__dirname, "..", "artifacts");
  await fs.mkdir(artifactsDir, { recursive: true });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    executedAt: new Date().toISOString(),
    environment: {
      urlSource: SALEADS_URL ? "env" : "existing browser page",
      saleadsUrl: SALEADS_URL || null,
      googleEmail: GOOGLE_EMAIL,
    },
    report,
    finalUrls,
    evidence,
    errors,
  };

  const reportPath = path.join(artifactsDir, "saleads_mi_negocio_full_test_report.json");
  await fs.writeFile(reportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  console.table(report);
  if (errors.length > 0) {
    // Keep concise but actionable failures in test output.
    console.error(errors.join("\n"));
  }

  expect(
    Object.values(report).every((value) => value === "PASS"),
    `At least one validation failed. See ${reportPath}`
  ).toBeTruthy();
});
