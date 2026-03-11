const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");
const path = require("node:path");

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

function createInitialReport() {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = "FAIL";
    return acc;
  }, {});
}

function sanitizeFileName(name) {
  return name
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9_-]+/g, "_")
    .replace(/_+/g, "_")
    .replace(/^_|_$/g, "")
    .toLowerCase();
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 7000 }).catch(() => {});
  await page
    .locator('[role="progressbar"], [aria-busy="true"], .spinner, .loading')
    .first()
    .waitFor({ state: "hidden", timeout: 3000 })
    .catch(() => {});
  await page.waitForTimeout(400);
}

async function takeCheckpoint(page, outputDir, checkpointName, fullPage = false) {
  const fileName = `${String(Date.now())}_${sanitizeFileName(checkpointName)}.png`;
  const screenshotPath = path.join(outputDir, fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

async function firstVisibleLocator(candidates, timeoutMs = 15000) {
  const startedAt = Date.now();
  const pollMs = 250;

  while (Date.now() - startedAt < timeoutMs) {
    for (const candidate of candidates) {
      const locator = candidate.first();
      const isVisible = await locator.isVisible().catch(() => false);
      if (isVisible) {
        return locator;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, pollMs));
  }

  throw new Error("No visible locator matched within timeout.");
}

async function selectGoogleAccountIfPrompted(targetPage) {
  const emailOption = targetPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
  if (await emailOption.isVisible().catch(() => false)) {
    await emailOption.click();
    await waitForUiLoad(targetPage);
  }
}

async function validateLegalPage(legalPage, headingRegex) {
  const heading = await firstVisibleLocator(
    [
      legalPage.getByRole("heading", { name: headingRegex }),
      legalPage.getByText(headingRegex),
    ],
    20000
  );
  await expect(heading).toBeVisible();

  const contentContainer = await firstVisibleLocator(
    [legalPage.locator("main"), legalPage.locator("article"), legalPage.locator("body")],
    10000
  );
  const contentText = (await contentContainer.innerText()).replace(/\s+/g, " ").trim();
  if (contentText.length < 120) {
    throw new Error("Legal content is too short or not visible enough.");
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createInitialReport();
  const failures = [];
  const legalUrls = {
    "Términos y Condiciones": "",
    "Política de Privacidad": "",
  };
  const runId = `${Date.now()}`;
  const screenshotDir = path.join("e2e-artifacts", "screenshots", "saleads_mi_negocio_full_test", runId);
  const reportDir = path.join("e2e-artifacts", "reports");
  await fs.mkdir(screenshotDir, { recursive: true });
  await fs.mkdir(reportDir, { recursive: true });

  async function runStep(fieldName, stepRunner) {
    try {
      await stepRunner();
      report[fieldName] = "PASS";
    } catch (error) {
      report[fieldName] = "FAIL";
      failures.push(`${fieldName}: ${error.message}`);
      // Keep executing all steps to produce a complete PASS/FAIL report.
    }
  }

  await runStep("Login", async () => {
    const baseUrl = process.env.SALEADS_URL || process.env.BASE_URL;
    if (baseUrl) {
      await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
      await waitForUiLoad(page);
    }

    const googleLoginButton = await firstVisibleLocator(
      [
        page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i }),
        page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
      ],
      30000
    );

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await googleLoginButton.click();
    await waitForUiLoad(page);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await selectGoogleAccountIfPrompted(popup);
      await popup.waitForEvent("close", { timeout: 90000 }).catch(() => {});
      await page.bringToFront();
      await waitForUiLoad(page);
    } else {
      await selectGoogleAccountIfPrompted(page);
      await waitForUiLoad(page);
    }

    const sidebar = await firstVisibleLocator(
      [
        page.getByRole("navigation"),
        page.locator("aside"),
        page.locator("nav"),
      ],
      30000
    );
    await expect(sidebar).toBeVisible();

    const negocioOrMiNegocio = await firstVisibleLocator(
      [page.getByText(/negocio|mi negocio/i), sidebar.getByText(/negocio|mi negocio/i)],
      15000
    );
    await expect(negocioOrMiNegocio).toBeVisible();

    await takeCheckpoint(page, screenshotDir, "dashboard_loaded", true);
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = await firstVisibleLocator(
      [
        page.getByText(/^Negocio$/i),
        page.getByRole("button", { name: /negocio/i }),
        page.getByRole("link", { name: /negocio/i }),
      ],
      15000
    );
    await negocioSection.click();
    await waitForUiLoad(page);

    const miNegocioOption = await firstVisibleLocator(
      [
        page.getByText(/^Mi Negocio$/i),
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
      ],
      15000
    );
    await miNegocioOption.click();
    await waitForUiLoad(page);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
    await takeCheckpoint(page, screenshotDir, "mi_negocio_menu_expanded", false);
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await firstVisibleLocator(
      [
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i),
      ],
      15000
    );
    await agregarNegocio.click();
    await waitForUiLoad(page);

    const modal = await firstVisibleLocator(
      [
        page.getByRole("dialog", { name: /Crear Nuevo Negocio/i }),
        page.locator('[role="dialog"]').filter({ hasText: /Crear Nuevo Negocio/i }),
      ],
      15000
    );
    await expect(modal).toBeVisible();
    await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();

    const nombreDelNegocioInput = await firstVisibleLocator(
      [
        modal.getByLabel(/Nombre del Negocio/i),
        modal.getByRole("textbox", { name: /Nombre del Negocio/i }),
        modal.getByPlaceholder(/Nombre del Negocio/i),
      ],
      10000
    );
    await expect(nombreDelNegocioInput).toBeVisible();
    await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();
    await takeCheckpoint(page, screenshotDir, "agregar_negocio_modal", false);

    await nombreDelNegocioInput.click();
    await nombreDelNegocioInput.fill("Negocio Prueba Automatización");
    await modal.getByRole("button", { name: /Cancelar/i }).click();
    await waitForUiLoad(page);
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarVisible = await page.getByText(/^Administrar Negocios$/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      const miNegocioToggle = await firstVisibleLocator(
        [
          page.getByText(/^Mi Negocio$/i),
          page.getByRole("button", { name: /^Mi Negocio$/i }),
          page.getByRole("link", { name: /^Mi Negocio$/i }),
        ],
        12000
      );
      await miNegocioToggle.click();
      await waitForUiLoad(page);
    }

    const administrarNegocios = await firstVisibleLocator(
      [
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i),
      ],
      15000
    );
    await administrarNegocios.click();
    await waitForUiLoad(page);

    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();
    await takeCheckpoint(page, screenshotDir, "administrar_negocios_view", true);
  });

  await runStep("Información General", async () => {
    const infoSection = page.locator("section, div").filter({ hasText: /Información General/i }).first();
    await expect(infoSection).toBeVisible();

    const infoSectionText = (await infoSection.innerText()).split("\n").map((line) => line.trim()).filter(Boolean);
    const emailLine = infoSectionText.find((line) => /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/.test(line));
    if (!emailLine) {
      throw new Error("User email is not visible in Información General.");
    }

    const nameLine = infoSectionText.find(
      (line) =>
        !/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/.test(line) &&
        !/informaci[oó]n general|business plan|cambiar plan/i.test(line) &&
        /[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}/.test(line)
    );
    if (!nameLine) {
      throw new Error("User name is not clearly visible in Información General.");
    }

    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    const cambiarPlan = await firstVisibleLocator(
      [page.getByRole("button", { name: /Cambiar Plan/i }), page.getByText(/Cambiar Plan/i)],
      10000
    );
    await expect(cambiarPlan).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    const businessSection = page.locator("section, div").filter({ hasText: /Tus Negocios/i }).first();
    await expect(businessSection).toBeVisible();
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();

    const candidateListItems = businessSection.locator("li, tr, [class*='card'], [class*='business']");
    const candidateCount = await candidateListItems.count();
    if (candidateCount < 1) {
      throw new Error("Business list is not visible in Tus Negocios.");
    }
  });

  async function openLegalLinkAndReturn(linkRegex, headingRegex, reportField, checkpointName) {
    const legalSection = page.locator("section, div").filter({ hasText: /Sección Legal/i }).first();
    const legalLink = await firstVisibleLocator(
      [
        legalSection.getByRole("link", { name: linkRegex }),
        page.getByRole("link", { name: linkRegex }),
        page.getByText(linkRegex),
      ],
      15000
    );

    const appUrlBeforeClick = page.url();
    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await legalLink.click();
    await waitForUiLoad(page);

    const popup = await popupPromise;
    const legalPage = popup || page;
    await legalPage.waitForLoadState("domcontentloaded");
    await waitForUiLoad(legalPage);

    await validateLegalPage(legalPage, headingRegex);
    legalUrls[reportField] = legalPage.url();
    await takeCheckpoint(legalPage, screenshotDir, checkpointName, true);

    if (popup) {
      await popup.close().catch(() => {});
      await page.bringToFront();
      await waitForUiLoad(page);
      return;
    }

    if (page.url() !== appUrlBeforeClick) {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
      await waitForUiLoad(page);
    }
  }

  await runStep("Términos y Condiciones", async () => {
    await openLegalLinkAndReturn(
      /T[eé]rminos y Condiciones/i,
      /T[eé]rminos y Condiciones/i,
      "Términos y Condiciones",
      "terminos_y_condiciones"
    );
  });

  await runStep("Política de Privacidad", async () => {
    await openLegalLinkAndReturn(
      /Pol[ií]tica de Privacidad/i,
      /Pol[ií]tica de Privacidad/i,
      "Política de Privacidad",
      "politica_de_privacidad"
    );
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    report,
    legalUrls,
    failures,
  };

  const finalReportPath = path.join(reportDir, "saleads_mi_negocio_full_test.report.json");
  await fs.writeFile(finalReportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf8");
  await testInfo.attach("final-report", {
    body: Buffer.from(JSON.stringify(finalReport, null, 2), "utf8"),
    contentType: "application/json",
  });

  // Print a concise summary in CI logs.
  console.table(report);
  console.log("Legal URLs:", legalUrls);
  console.log("Final report saved at:", finalReportPath);

  if (failures.length > 0) {
    throw new Error(`saleads_mi_negocio_full_test failed:\n- ${failures.join("\n- ")}`);
  }
});
