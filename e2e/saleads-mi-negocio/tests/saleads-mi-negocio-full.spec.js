const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const TEST_NAME = "saleads_mi_negocio_full_test";
const GOOGLE_ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
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

const initialFieldState = Object.fromEntries(
  REPORT_FIELDS.map((field) => [field, { status: "NOT_RUN", details: "" }])
);

function slugify(value) {
  return value
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

test(TEST_NAME, async ({ page, context }, testInfo) => {
  const runId = `${Date.now()}-${testInfo.retry}`;
  const artifactsDir = path.resolve(__dirname, "..", "artifacts", `run-${runId}`);
  fs.mkdirSync(artifactsDir, { recursive: true });

  const report = JSON.parse(JSON.stringify(initialFieldState));
  const evidence = {
    screenshots: {},
    urls: {}
  };

  async function waitForUiReady(targetPage = page) {
    await targetPage
      .waitForLoadState("domcontentloaded", { timeout: 45_000 })
      .catch(() => {});
    await targetPage
      .waitForLoadState("networkidle", { timeout: 8_000 })
      .catch(() => {});
    await targetPage.waitForTimeout(600);
  }

  async function captureCheckpoint(label, targetPage = page, fullPage = false) {
    const fileName = `${Object.keys(evidence.screenshots).length + 1}-${slugify(label)}.png`;
    const screenshotPath = path.join(artifactsDir, fileName);
    await targetPage.screenshot({ path: screenshotPath, fullPage });
    evidence.screenshots[label] = screenshotPath;
  }

  async function findFirstVisible(candidates) {
    for (const candidate of candidates) {
      const locator = candidate.first();
      const visible = await locator.isVisible({ timeout: 2_500 }).catch(() => false);
      if (visible) {
        return locator;
      }
    }
    return null;
  }

  async function clickAndWait(locator, targetPage = page) {
    await expect(locator).toBeVisible({ timeout: 30_000 });
    await locator.scrollIntoViewIfNeeded().catch(() => {});
    await locator.click();
    await waitForUiReady(targetPage);
  }

  async function runValidation(field, callback, dependencies = []) {
    const blockedBy = dependencies.find((dependency) => report[dependency]?.status !== "PASS");
    if (blockedBy) {
      report[field] = {
        status: "FAIL",
        details: `Blocked because '${blockedBy}' did not pass.`
      };
      return false;
    }

    try {
      await callback();
      report[field] = { status: "PASS", details: "Validation succeeded." };
      return true;
    } catch (error) {
      report[field] = {
        status: "FAIL",
        details: error instanceof Error ? error.message : String(error)
      };
      await captureCheckpoint(`failure-${field}`).catch(() => {});
      return false;
    }
  }

  async function getSectionByHeading(headingPattern) {
    const heading = page.getByText(headingPattern).first();
    await expect(heading).toBeVisible({ timeout: 30_000 });
    const section = page.locator("section, article, div").filter({ has: heading }).first();
    return section;
  }

  async function validateLegalPage(
    field,
    linkPattern,
    headingPattern,
    urlKey,
    screenshotLabel,
    dependencies = []
  ) {
    return runValidation(
      field,
      async () => {
        const legalSection = await getSectionByHeading(/Sección Legal/i);
        const link = await findFirstVisible([
          legalSection.getByRole("link", { name: linkPattern }),
          legalSection.getByText(linkPattern),
          page.getByRole("link", { name: linkPattern }),
          page.getByText(linkPattern)
        ]);

        if (!link) {
          throw new Error(`Could not find legal link for ${field}.`);
        }

        const appUrlBeforeClick = page.url();
        const popupPromise = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);
        await clickAndWait(link, page);
        const popup = await popupPromise;
        const legalPage = popup || page;

        await waitForUiReady(legalPage);
        await expect(legalPage.getByText(headingPattern).first()).toBeVisible({
          timeout: 60_000
        });

        const legalText = (await legalPage.locator("body").innerText())
          .replace(/\s+/g, " ")
          .trim();
        if (legalText.length < 250) {
          throw new Error(
            `${field} content appears too short to confirm legal text visibility.`
          );
        }

        evidence.urls[urlKey] = legalPage.url();
        await captureCheckpoint(screenshotLabel, legalPage, true);

        if (popup) {
          await popup.close().catch(() => {});
          await page.bringToFront();
          await waitForUiReady(page);
          return;
        }

        if (page.url() !== appUrlBeforeClick) {
          await page.goBack({ timeout: 30_000, waitUntil: "domcontentloaded" }).catch(() => {});
          await waitForUiReady(page);
        }
      },
      dependencies
    );
  }

  const testStart = new Date().toISOString();

  await runValidation("Login", async () => {
    const configuredUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL;
    if (configuredUrl) {
      await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
      await waitForUiReady(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "No login URL provided. Set SALEADS_LOGIN_URL or SALEADS_BASE_URL to run this test without hardcoding a domain."
      );
    }

    const googleLoginTrigger = await findFirstVisible([
      page.getByRole("button", { name: /Sign in with Google/i }),
      page.getByRole("button", { name: /Iniciar sesión con Google/i }),
      page.getByRole("button", { name: /Continuar con Google/i }),
      page.getByRole("link", { name: /Google/i }),
      page.getByText(/Google/i)
    ]);

    if (!googleLoginTrigger) {
      throw new Error("Could not locate login trigger for Google sign-in.");
    }

    const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await clickAndWait(googleLoginTrigger, page);
    const popup = await popupPromise;
    const googlePage = popup || page;

    await waitForUiReady(googlePage);

    const accountOption = await findFirstVisible([
      googlePage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }),
      googlePage.getByRole("button", { name: new RegExp(escapeRegExp(GOOGLE_ACCOUNT_EMAIL), "i") }),
      googlePage.getByRole("link", { name: new RegExp(escapeRegExp(GOOGLE_ACCOUNT_EMAIL), "i") })
    ]);

    if (accountOption) {
      await clickAndWait(accountOption, googlePage);
    }

    if (popup) {
      await popup.waitForClose({ timeout: 60_000 }).catch(() => {});
      await page.bringToFront();
    }

    await waitForUiReady(page);
    await expect(page.locator("aside").first()).toBeVisible({ timeout: 60_000 });
    await expect(page.getByText(/Mi Negocio|Negocio/i).first()).toBeVisible({
      timeout: 60_000
    });
    await captureCheckpoint("dashboard-load", page, true);
  });

  await runValidation(
    "Mi Negocio menu",
    async () => {
    const negocioSection = await findFirstVisible([
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i)
    ]);

    if (negocioSection) {
      await clickAndWait(negocioSection, page);
    }

    const miNegocioOption = await findFirstVisible([
      page.getByRole("button", { name: /Mi Negocio/i }),
      page.getByRole("link", { name: /Mi Negocio/i }),
      page.getByText(/Mi Negocio/i)
    ]);

    if (!miNegocioOption) {
      throw new Error("Could not locate 'Mi Negocio' menu option.");
    }

    await clickAndWait(miNegocioOption, page);
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({
      timeout: 30_000
    });
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible({
      timeout: 30_000
    });
      await captureCheckpoint("mi-negocio-menu-expanded");
    },
    ["Login"]
  );

  await runValidation(
    "Agregar Negocio modal",
    async () => {
    const addBusinessMenuItem = await findFirstVisible([
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i)
    ]);

    if (!addBusinessMenuItem) {
      throw new Error("Could not find 'Agregar Negocio' option.");
    }

    await clickAndWait(addBusinessMenuItem, page);

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible({
      timeout: 30_000
    });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({
      timeout: 30_000
    });

    const businessNameInput = await findFirstVisible([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input[name*='nombre'], input[id*='nombre']")
    ]);

    if (!businessNameInput) {
      throw new Error("Could not locate the 'Nombre del Negocio' input field.");
    }

    await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible({
      timeout: 30_000
    });
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible({
      timeout: 30_000
    });

    await captureCheckpoint("agregar-negocio-modal");
    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");

    const cancelButton = page.getByRole("button", { name: /^Cancelar$/i });
      await clickAndWait(cancelButton, page);
      await expect(page.getByText(/Crear Nuevo Negocio/i)).toHaveCount(0, { timeout: 15_000 });
    },
    ["Mi Negocio menu"]
  );

  await runValidation(
    "Administrar Negocios view",
    async () => {
    const administrarNegociosItem = await findFirstVisible([
      page.getByRole("button", { name: /^Administrar Negocios$/i }),
      page.getByRole("link", { name: /^Administrar Negocios$/i }),
      page.getByText(/^Administrar Negocios$/i)
    ]);

    if (!administrarNegociosItem) {
      const miNegocioOption = await findFirstVisible([
        page.getByRole("button", { name: /Mi Negocio/i }),
        page.getByRole("link", { name: /Mi Negocio/i }),
        page.getByText(/Mi Negocio/i)
      ]);

      if (!miNegocioOption) {
        throw new Error("Could not expand 'Mi Negocio' to reach 'Administrar Negocios'.");
      }

      await clickAndWait(miNegocioOption, page);
    }

    const administrarNegocios = await findFirstVisible([
      page.getByRole("button", { name: /^Administrar Negocios$/i }),
      page.getByRole("link", { name: /^Administrar Negocios$/i }),
      page.getByText(/^Administrar Negocios$/i)
    ]);

    if (!administrarNegocios) {
      throw new Error("Could not find the 'Administrar Negocios' option.");
    }

    await clickAndWait(administrarNegocios, page);

    await expect(page.getByText(/Información General/i).first()).toBeVisible({
      timeout: 45_000
    });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({
      timeout: 45_000
    });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 45_000 });
      await expect(page.getByText(/Sección Legal/i).first()).toBeVisible({ timeout: 45_000 });
      await captureCheckpoint("administrar-negocios-page", page, true);
    },
    ["Mi Negocio menu"]
  );

  await runValidation(
    "Información General",
    async () => {
    const infoSection = await getSectionByHeading(/Información General/i);
    await expect(infoSection.getByText(/BUSINESS PLAN/i).first()).toBeVisible({
      timeout: 30_000
    });

    const changePlanButton = await findFirstVisible([
      infoSection.getByRole("button", { name: /Cambiar Plan/i }),
      infoSection.getByText(/Cambiar Plan/i)
    ]);
    if (!changePlanButton) {
      throw new Error("Could not find 'Cambiar Plan' in 'Información General'.");
    }

    const emailLocator = infoSection
      .getByText(/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/)
      .first();
    await expect(emailLocator).toBeVisible({ timeout: 30_000 });

    const expectedUserName = process.env.SALEADS_EXPECTED_USER_NAME;
    if (expectedUserName) {
      await expect(
        infoSection
          .getByText(new RegExp(escapeRegExp(expectedUserName), "i"))
          .first()
      ).toBeVisible({ timeout: 30_000 });
      return;
    }

    const normalizedText = (await infoSection.innerText()).replace(/\s+/g, " ").trim();
    const hasNameLikePattern = /\b[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\b/.test(
      normalizedText
    );

      if (!hasNameLikePattern) {
        throw new Error(
          "Could not confidently identify user name text. Set SALEADS_EXPECTED_USER_NAME for strict validation."
        );
      }
    },
    ["Administrar Negocios view"]
  );

  await runValidation(
    "Detalles de la Cuenta",
    async () => {
    const detailsSection = await getSectionByHeading(/Detalles de la Cuenta/i);
    await expect(detailsSection.getByText(/Cuenta creada/i).first()).toBeVisible({
      timeout: 30_000
    });
    await expect(detailsSection.getByText(/Estado activo/i).first()).toBeVisible({
      timeout: 30_000
    });
      await expect(
        detailsSection.getByText(/Idioma seleccionado/i).first()
      ).toBeVisible({
        timeout: 30_000
      });
    },
    ["Administrar Negocios view"]
  );

  await runValidation(
    "Tus Negocios",
    async () => {
    const businessesSection = await getSectionByHeading(/Tus Negocios/i);
    const addBusinessButton = await findFirstVisible([
      businessesSection.getByRole("button", { name: /^Agregar Negocio$/i }),
      businessesSection.getByText(/^Agregar Negocio$/i)
    ]);

    if (!addBusinessButton) {
      throw new Error("Missing 'Agregar Negocio' button in 'Tus Negocios'.");
    }

    await expect(
      businessesSection.getByText(/Tienes 2 de 3 negocios/i).first()
    ).toBeVisible({ timeout: 30_000 });

    const rowCount = await businessesSection.locator("li, tr, [role='row']").count();
    const textSnapshot = (await businessesSection.innerText()).trim();
      if (rowCount === 0 && !/negocio/i.test(textSnapshot)) {
        throw new Error("Could not confirm visible business list in 'Tus Negocios'.");
      }
    },
    ["Administrar Negocios view"]
  );

  await validateLegalPage(
    "Términos y Condiciones",
    /Términos y Condiciones/i,
    /Términos y Condiciones/i,
    "terminos_url",
    "terminos-y-condiciones",
    ["Administrar Negocios view"]
  );

  await validateLegalPage(
    "Política de Privacidad",
    /Política de Privacidad/i,
    /Política de Privacidad/i,
    "politica_privacidad_url",
    "politica-de-privacidad",
    ["Administrar Negocios view"]
  );

  const failedValidations = REPORT_FIELDS.filter(
    (field) => report[field].status !== "PASS"
  );
  const overallStatus = failedValidations.length === 0 ? "PASS" : "FAIL";

  const finalReport = {
    name: TEST_NAME,
    startedAt: testStart,
    finishedAt: new Date().toISOString(),
    overallStatus,
    report,
    evidence
  };

  const finalReportPath = path.join(artifactsDir, "final-report.json");
  fs.writeFileSync(finalReportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("final-report", {
    path: finalReportPath,
    contentType: "application/json"
  });

  console.log(JSON.stringify(finalReport, null, 2));
  expect(failedValidations, `Failed validations: ${failedValidations.join(", ")}`).toEqual([]);
});
