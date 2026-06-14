const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");
const path = require("node:path");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const SALEADS_BASE_URL = process.env.SALEADS_BASE_URL;
const ARTIFACTS_DIR =
  process.env.SALEADS_ARTIFACTS_DIR || path.resolve(process.cwd(), "artifacts/saleads-mi-negocio");

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

function sectionTemplate() {
  return {
    status: "FAIL",
    checks: [],
    evidence: []
  };
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUiToSettle(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function findClickableByVisibleText(scope, text) {
  const textRegex = new RegExp(escapeRegExp(text), "i");
  const candidates = [
    scope.getByRole("button", { name: textRegex }).first(),
    scope.getByRole("link", { name: textRegex }).first(),
    scope.getByRole("menuitem", { name: textRegex }).first(),
    scope.getByRole("tab", { name: textRegex }).first(),
    scope.getByText(textRegex).first()
  ];

  for (const locator of candidates) {
    const visible = await locator
      .waitFor({ state: "visible", timeout: 2_000 })
      .then(() => true)
      .catch(() => false);
    if (visible) {
      return locator;
    }
  }

  return null;
}

async function clickByVisibleText(page, text) {
  const locator = await findClickableByVisibleText(page, text);
  if (!locator) {
    throw new Error(`No visible clickable element found for text: "${text}"`);
  }

  await locator.click();
  await waitForUiToSettle(page);
}

async function clickFirstAvailableText(page, candidates) {
  for (const candidate of candidates) {
    const locator = await findClickableByVisibleText(page, candidate);
    if (locator) {
      await locator.click();
      await waitForUiToSettle(page);
      return candidate;
    }
  }

  throw new Error(`None of the candidate texts were clickable: ${candidates.join(", ")}`);
}

async function getBusinessNameInput(page) {
  const labeledInput = page.getByLabel(/Nombre del Negocio/i).first();
  const labelVisible = await labeledInput
    .waitFor({ state: "visible", timeout: 5_000 })
    .then(() => true)
    .catch(() => false);

  if (labelVisible) {
    return labeledInput;
  }

  const placeholderInput = page.getByPlaceholder(/Nombre del Negocio/i).first();
  await placeholderInput.waitFor({ state: "visible", timeout: 10_000 });
  return placeholderInput;
}

async function verifyCheck(section, description, checkFn) {
  try {
    await checkFn();
    section.checks.push({ description, status: "PASS" });
    return true;
  } catch (error) {
    section.checks.push({ description, status: "FAIL", error: String(error.message || error) });
    return false;
  }
}

async function takeCheckpoint(page, section, checkpointName, { fullPage = false } = {}) {
  await fs.mkdir(ARTIFACTS_DIR, { recursive: true });
  const fileName = `${Date.now()}-${checkpointName}.png`;
  const filePath = path.join(ARTIFACTS_DIR, fileName);
  await page.screenshot({ path: filePath, fullPage });
  section.evidence.push({ type: "screenshot", path: filePath });
}

async function openLegalDocument(page, text, headingRegex) {
  const popupPromise = page.waitForEvent("popup", { timeout: 10_000 }).catch(() => null);
  await clickByVisibleText(page, text);
  const popup = await popupPromise;
  const documentPage = popup || page;
  await waitForUiToSettle(documentPage);

  return {
    documentPage,
    openedInNewTab: Boolean(popup),
    headingRegex
  };
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page }) => {
    test.setTimeout(8 * 60 * 1000);

    const report = {
      name: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      baseUrl: SALEADS_BASE_URL || null,
      steps: Object.fromEntries(REPORT_FIELDS.map((name) => [name, sectionTemplate()]))
    };

    if (SALEADS_BASE_URL) {
      await page.goto(SALEADS_BASE_URL, { waitUntil: "domcontentloaded" });
      await waitForUiToSettle(page);
    }

    const currentUrl = page.url();
    if (currentUrl === "about:blank") {
      throw new Error(
        "Browser is on about:blank. Provide SALEADS_BASE_URL for the target environment login page."
      );
    }

    // 1) Login with Google
    {
      const section = report.steps["Login"];
      const loginPopupPromise = page.waitForEvent("popup", { timeout: 10_000 }).catch(() => null);

      await verifyCheck(section, "Locate and click login with Google button", async () => {
        await clickFirstAvailableText(page, [
          "Sign in with Google",
          "Iniciar sesión con Google",
          "Continuar con Google",
          "Google"
        ]);
      });

      const loginPopup = await loginPopupPromise;
      const googlePage = loginPopup || (/accounts\.google\.com/i.test(page.url()) ? page : null);

      if (googlePage) {
        await verifyCheck(section, `Select Google account "${GOOGLE_ACCOUNT_EMAIL}" when prompted`, async () => {
          const emailLocator = googlePage.getByText(new RegExp(escapeRegExp(GOOGLE_ACCOUNT_EMAIL), "i")).first();
          await emailLocator.waitFor({ state: "visible", timeout: 20_000 });
          await emailLocator.click();
          await waitForUiToSettle(googlePage);
        });

        if (loginPopup) {
          await loginPopup.waitForEvent("close", { timeout: 40_000 }).catch(() => {});
          await page.bringToFront();
        }
      }

      await verifyCheck(section, "Main application interface appears", async () => {
        const mainLocator = page.locator("main, [role='main']").first();
        await expect(mainLocator).toBeVisible({ timeout: 40_000 });
      });

      await verifyCheck(section, "Left sidebar navigation is visible", async () => {
        const sidebarCandidates = [
          page.locator("aside").first(),
          page.getByRole("navigation").first(),
          page.getByText(/Negocio|Mi Negocio|Dashboard|Inicio/i).first()
        ];

        let visible = false;
        for (const candidate of sidebarCandidates) {
          visible = await candidate
            .waitFor({ state: "visible", timeout: 10_000 })
            .then(() => true)
            .catch(() => false);
          if (visible) {
            break;
          }
        }

        if (!visible) {
          throw new Error("No sidebar/navigation candidate became visible.");
        }
      });

      await takeCheckpoint(page, section, "01-dashboard-loaded");
      section.status = section.checks.every((check) => check.status === "PASS") ? "PASS" : "FAIL";
    }

    // 2) Open Mi Negocio menu
    {
      const section = report.steps["Mi Negocio menu"];
      await verifyCheck(section, "Open Mi Negocio option from left sidebar", async () => {
        await clickByVisibleText(page, "Mi Negocio");
      });

      await verifyCheck(section, "Submenu is expanded", async () => {
        const addBusiness = page.getByText(/Agregar Negocio/i).first();
        const manageBusiness = page.getByText(/Administrar Negocios/i).first();
        await expect(addBusiness).toBeVisible({ timeout: 15_000 });
        await expect(manageBusiness).toBeVisible({ timeout: 15_000 });
      });

      await verifyCheck(section, "Agregar Negocio is visible", async () => {
        await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 10_000 });
      });

      await verifyCheck(section, "Administrar Negocios is visible", async () => {
        await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 10_000 });
      });

      await takeCheckpoint(page, section, "02-mi-negocio-expanded");
      section.status = section.checks.every((check) => check.status === "PASS") ? "PASS" : "FAIL";
    }

    // 3) Validate Agregar Negocio modal
    {
      const section = report.steps["Agregar Negocio modal"];

      await verifyCheck(section, "Open Agregar Negocio modal", async () => {
        await clickByVisibleText(page, "Agregar Negocio");
      });

      await verifyCheck(section, "Modal title 'Crear Nuevo Negocio' is visible", async () => {
        await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible({ timeout: 15_000 });
      });

      await verifyCheck(section, "Input field 'Nombre del Negocio' exists", async () => {
        const input = await getBusinessNameInput(page);
        await expect(input).toBeVisible({ timeout: 10_000 });
      });

      await verifyCheck(section, "Text 'Tienes 2 de 3 negocios' is visible", async () => {
        await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({ timeout: 10_000 });
      });

      await verifyCheck(section, "Buttons 'Cancelar' and 'Crear Negocio' are present", async () => {
        await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible({ timeout: 10_000 });
        await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible({ timeout: 10_000 });
      });

      await takeCheckpoint(page, section, "03-agregar-negocio-modal");

      await verifyCheck(section, "Optional: type business name and cancel modal", async () => {
        const input = await getBusinessNameInput(page);
        await input.click();
        await input.fill("Negocio Prueba Automatización");
        await clickByVisibleText(page, "Cancelar");
      });
      section.status = section.checks.every((check) => check.status === "PASS") ? "PASS" : "FAIL";
    }

    // 4) Open Administrar Negocios
    {
      const section = report.steps["Administrar Negocios view"];

      await verifyCheck(section, "Ensure Mi Negocio menu is expanded", async () => {
        const manageVisible = await page
          .getByText(/Administrar Negocios/i)
          .first()
          .waitFor({ state: "visible", timeout: 4_000 })
          .then(() => true)
          .catch(() => false);

        if (!manageVisible) {
          await clickByVisibleText(page, "Mi Negocio");
        }
      });

      await verifyCheck(section, "Open Administrar Negocios", async () => {
        await clickByVisibleText(page, "Administrar Negocios");
      });

      await verifyCheck(section, "Section 'Información General' exists", async () => {
        await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible({ timeout: 20_000 });
      });

      await verifyCheck(section, "Section 'Detalles de la Cuenta' exists", async () => {
        await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 20_000 });
      });

      await verifyCheck(section, "Section 'Tus Negocios' exists", async () => {
        await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20_000 });
      });

      await verifyCheck(section, "Section 'Sección Legal' exists", async () => {
        await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible({ timeout: 20_000 });
      });

      await takeCheckpoint(page, section, "04-administrar-negocios-page", { fullPage: true });
      section.status = section.checks.every((check) => check.status === "PASS") ? "PASS" : "FAIL";
    }

    // 5) Validate Información General
    {
      const section = report.steps["Información General"];
      await verifyCheck(section, "User name is visible", async () => {
        const nameCandidates = [
          page.getByText(/@[a-z0-9_.-]+/i).first(),
          page.locator("section").filter({ hasText: /Informaci[oó]n General/i }).getByText(/[A-Za-z]{2,}\s+[A-Za-z]{2,}/).first()
        ];
        let visible = false;
        for (const candidate of nameCandidates) {
          visible = await candidate
            .waitFor({ state: "visible", timeout: 5_000 })
            .then(() => true)
            .catch(() => false);
          if (visible) {
            break;
          }
        }
        if (!visible) {
          throw new Error("No visible user name candidate found.");
        }
      });

      await verifyCheck(section, "User email is visible", async () => {
        await expect(page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()).toBeVisible({
          timeout: 10_000
        });
      });

      await verifyCheck(section, "Text 'BUSINESS PLAN' is visible", async () => {
        await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 10_000 });
      });

      await verifyCheck(section, "Button 'Cambiar Plan' is visible", async () => {
        await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible({ timeout: 10_000 });
      });

      section.status = section.checks.every((check) => check.status === "PASS") ? "PASS" : "FAIL";
    }

    // 6) Validate Detalles de la Cuenta
    {
      const section = report.steps["Detalles de la Cuenta"];
      await verifyCheck(section, "'Cuenta creada' is visible", async () => {
        await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 10_000 });
      });
      await verifyCheck(section, "'Estado activo' is visible", async () => {
        await expect(page.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 10_000 });
      });
      await verifyCheck(section, "'Idioma seleccionado' is visible", async () => {
        await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 10_000 });
      });
      section.status = section.checks.every((check) => check.status === "PASS") ? "PASS" : "FAIL";
    }

    // 7) Validate Tus Negocios
    {
      const section = report.steps["Tus Negocios"];
      await verifyCheck(section, "Business list is visible", async () => {
        const listCandidates = [
          page.locator("table").first(),
          page.locator("ul li").first(),
          page.getByText(/Tus Negocios/i).first()
        ];
        let visible = false;
        for (const candidate of listCandidates) {
          visible = await candidate
            .waitFor({ state: "visible", timeout: 5_000 })
            .then(() => true)
            .catch(() => false);
          if (visible) {
            break;
          }
        }
        if (!visible) {
          throw new Error("Business list candidate was not visible.");
        }
      });

      await verifyCheck(section, "Button 'Agregar Negocio' exists", async () => {
        await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible({ timeout: 10_000 });
      });

      await verifyCheck(section, "Text 'Tienes 2 de 3 negocios' is visible", async () => {
        await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({ timeout: 10_000 });
      });

      section.status = section.checks.every((check) => check.status === "PASS") ? "PASS" : "FAIL";
    }

    // 8) Validate Términos y Condiciones
    {
      const section = report.steps["Términos y Condiciones"];
      await verifyCheck(section, "Open Términos y Condiciones document", async () => {
        const legalOpen = await openLegalDocument(page, "Términos y Condiciones", /T[eé]rminos y Condiciones/i);
        const { documentPage, openedInNewTab, headingRegex } = legalOpen;

        await expect(documentPage.getByText(headingRegex).first()).toBeVisible({ timeout: 20_000 });
        const legalContent = (await documentPage.locator("main, article, body").allInnerTexts())
          .join(" ")
          .trim();
        if (legalContent.length < 100) {
          throw new Error("Legal document content appears too short.");
        }

        await takeCheckpoint(documentPage, section, "05-terminos-y-condiciones", { fullPage: true });
        section.evidence.push({ type: "url", value: documentPage.url() });

        if (openedInNewTab) {
          await documentPage.close();
          await page.bringToFront();
          await waitForUiToSettle(page);
        } else {
          await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
          await waitForUiToSettle(page);
        }
      });
      section.status = section.checks.every((check) => check.status === "PASS") ? "PASS" : "FAIL";
    }

    // 9) Validate Política de Privacidad
    {
      const section = report.steps["Política de Privacidad"];
      await verifyCheck(section, "Open Política de Privacidad document", async () => {
        const legalOpen = await openLegalDocument(page, "Política de Privacidad", /Pol[ií]tica de Privacidad/i);
        const { documentPage, openedInNewTab, headingRegex } = legalOpen;

        await expect(documentPage.getByText(headingRegex).first()).toBeVisible({ timeout: 20_000 });
        const legalContent = (await documentPage.locator("main, article, body").allInnerTexts())
          .join(" ")
          .trim();
        if (legalContent.length < 100) {
          throw new Error("Privacy policy content appears too short.");
        }

        await takeCheckpoint(documentPage, section, "06-politica-de-privacidad", { fullPage: true });
        section.evidence.push({ type: "url", value: documentPage.url() });

        if (openedInNewTab) {
          await documentPage.close();
          await page.bringToFront();
          await waitForUiToSettle(page);
        } else {
          await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
          await waitForUiToSettle(page);
        }
      });
      section.status = section.checks.every((check) => check.status === "PASS") ? "PASS" : "FAIL";
    }

    const overallStatus = REPORT_FIELDS.every((name) => report.steps[name].status === "PASS") ? "PASS" : "FAIL";
    report.overallStatus = overallStatus;

    await fs.mkdir(ARTIFACTS_DIR, { recursive: true });
    const reportPath = path.join(ARTIFACTS_DIR, "final-report.json");
    await fs.writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf-8");

    await test.info().attach("saleads-final-report", {
      path: reportPath,
      contentType: "application/json"
    });

    expect(overallStatus, `Final report saved at ${reportPath}`).toBe("PASS");
  });
});
