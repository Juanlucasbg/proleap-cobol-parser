const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const GOOGLE_EMAIL =
  process.env.SALEADS_GOOGLE_EMAIL || "juanlucasbarbiergarzon@gmail.com";

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

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function exactTextPattern(label) {
  return new RegExp(`^\\s*${escapeRegExp(label)}\\s*$`, "i");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForTimeout(700);
}

async function firstVisibleLocator(candidates) {
  for (const locator of candidates) {
    const candidate = locator.first();
    const visible = await candidate.isVisible().catch(() => false);
    if (visible) {
      return candidate;
    }
  }
  return null;
}

async function clickUsingVisibleText(page, textOrPattern) {
  const locator =
    typeof textOrPattern === "string"
      ? page.getByText(exactTextPattern(textOrPattern)).first()
      : page.getByText(textOrPattern).first();

  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function takeCheckpoint(page, evidenceDir, name, options = {}) {
  const target = options.targetPage || page;
  const checkpointPath = path.join(evidenceDir, `${name}.png`);
  await target.screenshot({
    path: checkpointPath,
    fullPage: options.fullPage || false,
  });
  return checkpointPath;
}

async function validateLegalLink({
  page,
  context,
  evidenceDir,
  linkPattern,
  headingPattern,
  screenshotName,
}) {
  const linkLocator = await firstVisibleLocator([
    page.getByRole("link", { name: linkPattern }),
    page.getByText(linkPattern),
  ]);

  if (!linkLocator) {
    throw new Error(`No se encontro el enlace legal: ${linkPattern}`);
  }

  const [newTab] = await Promise.all([
    context.waitForEvent("page", { timeout: 7000 }).catch(() => null),
    linkLocator.click(),
  ]);

  const legalPage = newTab || page;
  await waitForUi(legalPage);

  const headingLocator = await firstVisibleLocator([
    legalPage.getByRole("heading", { name: headingPattern }),
    legalPage.getByText(headingPattern),
  ]);

  if (!headingLocator) {
    throw new Error(`No se encontro el encabezado legal: ${headingPattern}`);
  }

  const legalContent = (await legalPage.locator("body").innerText()).trim();
  if (legalContent.length < 120) {
    throw new Error("El contenido legal visible es demasiado corto.");
  }

  const screenshotPath = await takeCheckpoint(legalPage, evidenceDir, screenshotName, {
    targetPage: legalPage,
    fullPage: true,
  });

  const finalUrl = legalPage.url();

  if (newTab) {
    await newTab.close();
    await page.bringToFront();
  } else {
    await legalPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
  }

  return { screenshotPath, finalUrl };
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const evidenceDir = path.join(testInfo.outputDir, "evidence");
  fs.mkdirSync(evidenceDir, { recursive: true });

  const results = {};
  const setResult = (field, status, details = {}) => {
    results[field] = { status, ...details };
  };

  const executeStep = async (field, action) => {
    try {
      await action();
      if (!results[field]) {
        setResult(field, "PASS");
      }
    } catch (error) {
      setResult(field, "FAIL", {
        error: error instanceof Error ? error.message : String(error),
      });
    }
  };

  await executeStep("Login", async () => {
    const startUrl =
      process.env.SALEADS_START_URL ||
      process.env.SALEADS_LOGIN_URL ||
      process.env.BASE_URL;

    if (startUrl) {
      await page.goto(startUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "No URL disponible. Defina SALEADS_START_URL o SALEADS_LOGIN_URL."
      );
    }

    const sidebarLocator = await firstVisibleLocator([
      page.getByText(/Mi\s+Negocio/i),
      page.getByText(/Negocio/i),
    ]);

    if (!sidebarLocator) {
      const loginTrigger = await firstVisibleLocator([
        page.getByRole("button", { name: /google/i }),
        page.getByRole("link", { name: /google/i }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
        page.getByText(/iniciar sesi[oó]n|login|acceder/i),
      ]);

      if (!loginTrigger) {
        throw new Error("No se encontro el boton de inicio de sesion.");
      }

      const [popup] = await Promise.all([
        context.waitForEvent("page", { timeout: 10000 }).catch(() => null),
        loginTrigger.click(),
      ]);

      const googlePage = popup || page;
      await waitForUi(googlePage);

      const emailOption = googlePage.getByText(GOOGLE_EMAIL, { exact: false }).first();
      const canSelectEmail = await emailOption.isVisible().catch(() => false);
      if (canSelectEmail) {
        await emailOption.click();
        await waitForUi(googlePage);
      }

      if (popup) {
        await page.bringToFront();
      }
    }

    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 60000 });
    await expect(page.getByText(/Negocio|Mi\s+Negocio/i).first()).toBeVisible({
      timeout: 60000,
    });

    const screenshotPath = await takeCheckpoint(page, evidenceDir, "01-dashboard-loaded");
    setResult("Login", "PASS", { screenshot: screenshotPath });
  });

  await executeStep("Mi Negocio menu", async () => {
    const negocioLink = await firstVisibleLocator([
      page.getByText(exactTextPattern("Negocio")),
      page.getByRole("link", { name: /Negocio/i }),
      page.getByText(/Negocio/i),
    ]);

    if (negocioLink) {
      await negocioLink.click();
      await waitForUi(page);
    }

    await clickUsingVisibleText(page, /Mi\s+Negocio/i);
    await expect(page.getByText(/Agregar\s+Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar\s+Negocios/i).first()).toBeVisible();

    const screenshotPath = await takeCheckpoint(page, evidenceDir, "02-mi-negocio-menu-expanded");
    setResult("Mi Negocio menu", "PASS", { screenshot: screenshotPath });
  });

  await executeStep("Agregar Negocio modal", async () => {
    await clickUsingVisibleText(page, /Agregar\s+Negocio/i);

    await expect(page.getByText(/Crear\s+Nuevo\s+Negocio/i).first()).toBeVisible();

    const labeledInput = page.getByLabel(/Nombre del Negocio/i).first();
    const placeholderInput = page.getByPlaceholder(/Nombre del Negocio/i).first();
    const hasInput =
      (await labeledInput.isVisible().catch(() => false)) ||
      (await placeholderInput.isVisible().catch(() => false));
    expect(hasInput).toBe(true);

    await expect(page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear\s+Negocio/i }).first()).toBeVisible();

    const screenshotPath = await takeCheckpoint(page, evidenceDir, "03-agregar-negocio-modal");

    if (await labeledInput.isVisible().catch(() => false)) {
      await labeledInput.fill("Negocio Prueba Automatizacion");
    } else {
      await placeholderInput.fill("Negocio Prueba Automatizacion");
    }

    await page.getByRole("button", { name: /Cancelar/i }).first().click();
    await waitForUi(page);

    setResult("Agregar Negocio modal", "PASS", { screenshot: screenshotPath });
  });

  await executeStep("Administrar Negocios view", async () => {
    const miNegocioVisible = await page
      .getByText(/Administrar\s+Negocios/i)
      .first()
      .isVisible()
      .catch(() => false);
    if (!miNegocioVisible) {
      await clickUsingVisibleText(page, /Mi\s+Negocio/i);
    }

    await clickUsingVisibleText(page, /Administrar\s+Negocios/i);

    await expect(page.getByText(/Informaci[oó]n\s+General/i).first()).toBeVisible({
      timeout: 60000,
    });
    await expect(page.getByText(/Detalles\s+de\s+la\s+Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus\s+Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Secci[oó]n\s+Legal/i).first()).toBeVisible();

    const screenshotPath = await takeCheckpoint(page, evidenceDir, "04-administrar-negocios", {
      fullPage: true,
    });
    setResult("Administrar Negocios view", "PASS", { screenshot: screenshotPath });
  });

  await executeStep("Información General", async () => {
    await expect(page.getByText(/BUSINESS\s+PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar\s+Plan/i }).first()).toBeVisible();

    const profileInfo = (await page.locator("body").innerText()).toLowerCase();
    const hasEmail = /@/.test(profileInfo);
    expect(hasEmail).toBe(true);
  });

  await executeStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta\s+creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado\s+activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma\s+seleccionado/i).first()).toBeVisible();
  });

  await executeStep("Tus Negocios", async () => {
    await expect(page.getByText(/Tus\s+Negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Agregar\s+Negocio/i }).first()).toBeVisible();
    await expect(page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i).first()).toBeVisible();
  });

  await executeStep("Términos y Condiciones", async () => {
    const data = await validateLegalLink({
      page,
      context,
      evidenceDir,
      linkPattern: /T[eé]rminos\s+y\s+Condiciones/i,
      headingPattern: /T[eé]rminos\s+y\s+Condiciones/i,
      screenshotName: "08-terminos-y-condiciones",
    });
    setResult("Términos y Condiciones", "PASS", data);
  });

  await executeStep("Política de Privacidad", async () => {
    const data = await validateLegalLink({
      page,
      context,
      evidenceDir,
      linkPattern: /Pol[ií]tica\s+de\s+Privacidad/i,
      headingPattern: /Pol[ií]tica\s+de\s+Privacidad/i,
      screenshotName: "09-politica-de-privacidad",
    });
    setResult("Política de Privacidad", "PASS", data);
  });

  const finalReport = {};
  for (const field of REPORT_FIELDS) {
    finalReport[field] = results[field] || {
      status: "FAIL",
      error: "Step not executed.",
    };
  }

  const reportPath = path.join(evidenceDir, "10-final-report.json");
  fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  // Keep final report visible in terminal output for automation collectors.
  console.log("saleads_mi_negocio_full_test_report");
  console.log(JSON.stringify(finalReport, null, 2));

  const failures = Object.entries(finalReport).filter(
    ([, value]) => value.status !== "PASS"
  );

  expect(
    failures,
    `Validation failures: ${failures.map(([name]) => name).join(", ")}`
  ).toEqual([]);
});
