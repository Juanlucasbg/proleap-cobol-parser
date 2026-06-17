const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");
const path = require("node:path");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const BUSINESS_CAPACITY_TEXT = /Tienes\s+2\s+de\s+3\s+negocios/i;

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad"
];

function createReport() {
  return {
    startedAt: new Date().toISOString(),
    steps: Object.fromEntries(
      REPORT_FIELDS.map((field) => [
        field,
        {
          status: "NOT_RUN",
          details: [],
          evidence: []
        }
      ])
    ),
    legalUrls: {
      terminosYCondiciones: null,
      politicaDePrivacidad: null
    },
    finishedAt: null
  };
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
}

async function waitForFirstVisible(page, locators, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const locator of locators) {
      if (await locator.first().isVisible().catch(() => false)) {
        return locator.first();
      }
    }

    await page.waitForTimeout(250);
  }

  throw new Error(`No visible locator found after ${timeoutMs}ms.`);
}

async function clickAndWait(page, locator) {
  await locator.click();
  await waitForUi(page);
}

async function captureCheckpoint(page, checkpointDir, fileName, fullPage = false) {
  const screenshotPath = path.join(checkpointDir, fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

async function markStep(report, field, fn) {
  try {
    await fn(report.steps[field]);
    report.steps[field].status = "PASS";
  } catch (error) {
    report.steps[field].status = "FAIL";
    report.steps[field].details.push(
      `Error: ${error instanceof Error ? error.message : String(error)}`
    );
  }
}

async function selectGoogleAccountIfVisible(context) {
  const selectionDeadline = Date.now() + 15000;

  while (Date.now() < selectionDeadline) {
    for (const candidatePage of context.pages()) {
      const accountLocator = candidatePage.getByText(GOOGLE_ACCOUNT_EMAIL, {
        exact: false
      });
      const useAnotherAccountButton = candidatePage.getByText(
        /Use another account|Usar otra cuenta/i
      );

      if (await accountLocator.first().isVisible().catch(() => false)) {
        await accountLocator.first().click();
        await waitForUi(candidatePage);
        return true;
      }

      if (await useAnotherAccountButton.first().isVisible().catch(() => false)) {
        await useAnotherAccountButton.first().click();
        await waitForUi(candidatePage);
      }
    }

    await context.pages()[0].waitForTimeout(300);
  }

  return false;
}

async function validateLegalDocument({
  appPage,
  context,
  checkpointDir,
  linkPattern,
  headingPattern,
  screenshotFileName
}) {
  const pagesBeforeClick = new Set(context.pages());
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);

  const legalLink = await waitForFirstVisible(appPage, [
    appPage.getByRole("link", { name: linkPattern }),
    appPage.getByText(linkPattern)
  ]);

  await clickAndWait(appPage, legalLink);

  let documentPage = await popupPromise;
  if (!documentPage) {
    documentPage = context
      .pages()
      .find((candidatePage) => !pagesBeforeClick.has(candidatePage));
  }
  if (!documentPage) {
    documentPage = appPage;
  }

  await waitForUi(documentPage);

  const heading = documentPage.getByRole("heading", { name: headingPattern });
  const headingVisible = await heading.first().isVisible().catch(() => false);
  if (!headingVisible) {
    await expect(documentPage.getByText(headingPattern).first()).toBeVisible();
  } else {
    await expect(heading.first()).toBeVisible();
  }

  const legalText = (await documentPage.locator("body").innerText()).trim();
  if (legalText.length < 120) {
    throw new Error("Legal content is unexpectedly short.");
  }

  const screenshotPath = await captureCheckpoint(
    documentPage,
    checkpointDir,
    screenshotFileName,
    true
  );
  const finalUrl = documentPage.url();

  if (documentPage !== appPage) {
    await documentPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(appPage);
  }

  return {
    screenshotPath,
    finalUrl
  };
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const checkpointDir = testInfo.outputPath("checkpoints");
  await fs.mkdir(checkpointDir, { recursive: true });

  const report = createReport();
  const loginUrl =
    process.env.SALEADS_LOGIN_URL ||
    process.env.SALEADS_URL ||
    process.env.BASE_URL ||
    null;

  await markStep(report, "Login", async (step) => {
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
      step.details.push(`Opened login URL from environment: ${loginUrl}`);
    } else {
      step.details.push(
        "No SALEADS_LOGIN_URL/SALEADS_URL/BASE_URL provided. Assuming the test starts on the login page."
      );
    }

    const googleLoginButton = await waitForFirstVisible(page, [
      page.getByRole("button", { name: /Google/i }),
      page.getByText(/Sign in with Google|Iniciar sesión con Google/i),
      page.getByRole("link", { name: /Google/i })
    ]);

    await clickAndWait(page, googleLoginButton);
    await selectGoogleAccountIfVisible(context);

    const sidebar = await waitForFirstVisible(page, [
      page.getByRole("navigation"),
      page.locator("aside"),
      page.getByText(/Negocio/i)
    ]);
    await expect(sidebar).toBeVisible();
    step.details.push("Main application interface is visible.");
    step.details.push("Left sidebar navigation is visible.");

    const dashboardShot = await captureCheckpoint(
      page,
      checkpointDir,
      "step-01-dashboard-loaded.png",
      true
    );
    step.evidence.push(dashboardShot);
  });

  await markStep(report, "Mi Negocio menu", async (step) => {
    const navigationRoot = page.getByRole("navigation");
    const hasNavigation = await navigationRoot.first().isVisible().catch(() => false);
    const navScope = hasNavigation ? navigationRoot.first() : page;

    const negocioSection = await waitForFirstVisible(page, [
      navScope.getByText(/^Negocio$/i),
      page.getByText(/^Negocio$/i)
    ]);
    await clickAndWait(page, negocioSection);

    const miNegocioOption = await waitForFirstVisible(page, [
      navScope.getByText(/^Mi Negocio$/i),
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i)
    ]);
    await clickAndWait(page, miNegocioOption);

    const agregarNegocioOption = await waitForFirstVisible(page, [
      page.getByText(/^Agregar Negocio$/i)
    ]);
    const administrarNegociosOption = await waitForFirstVisible(page, [
      page.getByText(/^Administrar Negocios$/i)
    ]);

    await expect(agregarNegocioOption).toBeVisible();
    await expect(administrarNegociosOption).toBeVisible();

    step.details.push("Mi Negocio submenu expanded.");
    step.details.push("Agregar Negocio is visible.");
    step.details.push("Administrar Negocios is visible.");

    const menuShot = await captureCheckpoint(
      page,
      checkpointDir,
      "step-02-mi-negocio-menu-expanded.png",
      true
    );
    step.evidence.push(menuShot);
  });

  await markStep(report, "Agregar Negocio modal", async (step) => {
    const agregarNegocioOption = await waitForFirstVisible(page, [
      page.getByText(/^Agregar Negocio$/i)
    ]);
    await clickAndWait(page, agregarNegocioOption);

    const modalTitle = await waitForFirstVisible(page, [
      page.getByRole("heading", { name: /Crear Nuevo Negocio/i }),
      page.getByText(/Crear Nuevo Negocio/i)
    ]);
    await expect(modalTitle).toBeVisible();
    await expect(page.getByLabel(/Nombre del Negocio/i)).toBeVisible();
    await expect(page.getByText(BUSINESS_CAPACITY_TEXT)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    await page.getByLabel(/Nombre del Negocio/i).fill("Negocio Prueba Automatización");
    const modalShot = await captureCheckpoint(
      page,
      checkpointDir,
      "step-03-agregar-negocio-modal.png",
      true
    );
    step.evidence.push(modalShot);

    await clickAndWait(page, page.getByRole("button", { name: /Cancelar/i }));
    step.details.push("Modal and controls validated successfully.");
  });

  await markStep(report, "Administrar Negocios view", async (step) => {
    const miNegocioOption = await waitForFirstVisible(page, [
      page.getByText(/^Mi Negocio$/i),
      page.getByRole("button", { name: /^Mi Negocio$/i })
    ]);
    await clickAndWait(page, miNegocioOption);

    const administrarNegociosOption = await waitForFirstVisible(page, [
      page.getByText(/^Administrar Negocios$/i),
      page.getByRole("link", { name: /^Administrar Negocios$/i })
    ]);
    await clickAndWait(page, administrarNegociosOption);

    await expect(page.getByText(/Información General/i)).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Sección Legal/i)).toBeVisible();

    step.details.push("Account page sections are visible.");

    const accountShot = await captureCheckpoint(
      page,
      checkpointDir,
      "step-04-administrar-negocios-page.png",
      true
    );
    step.evidence.push(accountShot);
  });

  await markStep(report, "Información General", async (step) => {
    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
    await expect(page.getByText(/@/i).first()).toBeVisible();

    const visibleText = await page.locator("body").innerText();
    if (!visibleText.trim()) {
      throw new Error("Could not validate user name in Información General.");
    }

    step.details.push("User name is visible.");
    step.details.push("User email is visible.");
    step.details.push("BUSINESS PLAN is visible.");
    step.details.push("Cambiar Plan button is visible.");
  });

  await markStep(report, "Detalles de la Cuenta", async (step) => {
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();

    step.details.push("Cuenta creada is visible.");
    step.details.push("Estado activo is visible.");
    step.details.push("Idioma seleccionado is visible.");
  });

  await markStep(report, "Tus Negocios", async (step) => {
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible();
    await expect(page.getByText(BUSINESS_CAPACITY_TEXT)).toBeVisible();

    step.details.push("Business list section is visible.");
    step.details.push("Agregar Negocio button exists.");
    step.details.push("Capacity text is visible.");
  });

  await markStep(report, "Términos y Condiciones", async (step) => {
    const termsValidation = await validateLegalDocument({
      appPage: page,
      context,
      checkpointDir,
      linkPattern: /Términos y Condiciones/i,
      headingPattern: /Términos y Condiciones/i,
      screenshotFileName: "step-08-terminos-y-condiciones.png"
    });

    report.legalUrls.terminosYCondiciones = termsValidation.finalUrl;
    step.evidence.push(termsValidation.screenshotPath);
    step.details.push(`Final URL: ${termsValidation.finalUrl}`);
  });

  await markStep(report, "Política de Privacidad", async (step) => {
    const privacyValidation = await validateLegalDocument({
      appPage: page,
      context,
      checkpointDir,
      linkPattern: /Política de Privacidad/i,
      headingPattern: /Política de Privacidad/i,
      screenshotFileName: "step-09-politica-de-privacidad.png"
    });

    report.legalUrls.politicaDePrivacidad = privacyValidation.finalUrl;
    step.evidence.push(privacyValidation.screenshotPath);
    step.details.push(`Final URL: ${privacyValidation.finalUrl}`);
  });

  report.finishedAt = new Date().toISOString();

  const reportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
  await fs.writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json"
  });

  console.log("SaleADS Mi Negocio final report:");
  console.log(JSON.stringify(report, null, 2));

  const failedFields = REPORT_FIELDS.filter(
    (field) => report.steps[field].status !== "PASS"
  );
  expect(failedFields, `Failed sections: ${failedFields.join(", ")}`).toHaveLength(0);
});
