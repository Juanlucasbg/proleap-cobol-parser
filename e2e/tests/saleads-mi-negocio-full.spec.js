const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const SCREENSHOT_DIR = path.join(__dirname, "..", "artifacts", "screenshots");
const REPORT_DIR = path.join(__dirname, "..", "artifacts", "reports");

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

function nowStamp() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

function slugify(value) {
  return value
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");
}

function buildReport() {
  const report = {};
  for (const field of REPORT_FIELDS) {
    report[field] = { status: "FAIL", details: "Not executed" };
  }
  return report;
}

function markReport(report, field, status, details) {
  report[field] = {
    status: status ? "PASS" : "FAIL",
    details: details || (status ? "Validation passed" : "Validation failed"),
  };
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20_000 });
  try {
    await page.waitForLoadState("networkidle", { timeout: 6_000 });
  } catch (_error) {
    // Some pages keep background requests open; domcontentloaded is enough here.
  }
  await page.waitForTimeout(350);
}

async function resolveFirstVisible(candidates, waitMs = 5_000) {
  for (const candidate of candidates) {
    const first = candidate.first();
    try {
      await first.waitFor({ state: "visible", timeout: waitMs });
      return first;
    } catch (_error) {
      // Try next locator candidate.
    }
  }
  return null;
}

async function clickAndWait(locator, page) {
  await expect(locator).toBeVisible({ timeout: 15_000 });
  await locator.click();
  await waitForUi(page);
}

async function checkpoint(page, name) {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
  const filePath = path.join(SCREENSHOT_DIR, `${nowStamp()}-${slugify(name)}.png`);
  await page.screenshot({ path: filePath, fullPage: true });
  return filePath;
}

async function isMainAppVisible(page) {
  const sidebar = await resolveFirstVisible([
    page.locator("aside"),
    page.getByRole("navigation"),
    page.getByText(/mi negocio|negocio/i),
  ], 2_000);
  return Boolean(sidebar);
}

async function ensureMiNegocioExpanded(page) {
  const agregarNegocioVisible = await page
    .getByRole("button", { name: /agregar negocio/i })
    .first()
    .isVisible()
    .catch(() => false);
  const administrarNegociosVisible = await page
    .getByRole("button", { name: /administrar negocios/i })
    .first()
    .isVisible()
    .catch(() => false);

  if (agregarNegocioVisible && administrarNegociosVisible) {
    return;
  }

  const miNegocioTrigger = await resolveFirstVisible([
    page.getByRole("button", { name: /mi negocio/i }),
    page.getByRole("link", { name: /mi negocio/i }),
    page.getByText(/mi negocio/i),
  ]);

  if (!miNegocioTrigger) {
    throw new Error("No se encontró el menú 'Mi Negocio' en la barra lateral.");
  }

  await clickAndWait(miNegocioTrigger, page);
}

async function validateLegalPage({
  page,
  linkName,
  headingPattern,
  screenshotName,
}) {
  const legalLink = await resolveFirstVisible([
    page.getByRole("link", { name: linkName }),
    page.getByText(linkName),
  ]);

  if (!legalLink) {
    throw new Error(`No se encontró el enlace legal '${linkName}'.`);
  }

  const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await legalLink.click();
  await waitForUi(page);

  const popupPage = await popupPromise;
  const targetPage = popupPage || page;

  await waitForUi(targetPage);

  const heading = await resolveFirstVisible([
    targetPage.getByRole("heading", { name: headingPattern }),
    targetPage.getByText(headingPattern),
  ]);
  if (!heading) {
    throw new Error(
      `No se encontró el título legal esperado para '${linkName}' (${String(
        headingPattern
      )}).`
    );
  }
  await expect(heading).toBeVisible({ timeout: 15_000 });

  const bodyText = (await targetPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
  if (bodyText.length < 300) {
    throw new Error(`El contenido legal de '${linkName}' parece incompleto.`);
  }

  const screenshotPath = await checkpoint(targetPage, screenshotName);
  const finalUrl = targetPage.url();

  if (popupPage) {
    await popupPage.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
  }

  return { finalUrl, screenshotPath };
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login con Google y validación completa de Mi Negocio", async ({ page }) => {
    const report = buildReport();
    const checkpointEvidence = {};
    const legalUrls = {};

    if (process.env.SALEADS_LOGIN_URL) {
      await page.goto(process.env.SALEADS_LOGIN_URL, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    } else if (process.env.SALEADS_BASE_URL) {
      await page.goto(process.env.SALEADS_BASE_URL, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    // Step 1: Login with Google
    try {
      const alreadyLoggedIn = await isMainAppVisible(page);
      if (!alreadyLoggedIn) {
        const loginButton = await resolveFirstVisible([
          page.getByRole("button", { name: /google/i }),
          page.getByRole("link", { name: /google/i }),
          page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
        ]);
        if (!loginButton) {
          throw new Error("No se encontró el botón de login con Google.");
        }

        const popupPromise = page.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
        await clickAndWait(loginButton, page);
        const popupPage = await popupPromise;

        if (popupPage) {
          await waitForUi(popupPage);
          const accountSelector = await resolveFirstVisible(
            [
              popupPage.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")),
              popupPage.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
              popupPage.getByRole("link", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
            ],
            3_000
          );
          if (accountSelector) {
            await clickAndWait(accountSelector, popupPage);
          }
          await page.bringToFront();
          await waitForUi(page);
        } else {
          const accountSelectorInline = await resolveFirstVisible(
            [
              page.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")),
              page.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
            ],
            4_000
          );
          if (accountSelectorInline) {
            await clickAndWait(accountSelectorInline, page);
          }
        }
      }
      const sidebar = await resolveFirstVisible(
        [page.locator("aside"), page.getByRole("navigation"), page.getByText(/mi negocio|negocio/i)],
        15_000
      );
      if (!sidebar) {
        throw new Error("No apareció la interfaz principal o la barra lateral.");
      }

      await expect(sidebar).toBeVisible({ timeout: 20_000 });
      checkpointEvidence.dashboard = await checkpoint(page, "01-dashboard-loaded");
      markReport(report, "Login", true, "Interfaz principal y sidebar visibles.");
    } catch (error) {
      markReport(report, "Login", false, error.message);
    }

    // Step 2: Open Mi Negocio menu
    try {
      const negocioSection = await resolveFirstVisible([
        page.getByRole("button", { name: /^negocio$/i }),
        page.getByRole("link", { name: /^negocio$/i }),
        page.getByText(/^Negocio$/),
      ], 2_500);
      if (negocioSection) {
        await clickAndWait(negocioSection, page);
      }

      await ensureMiNegocioExpanded(page);

      const agregarSubmenu = await resolveFirstVisible([
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByRole("link", { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i),
      ]);
      const administrarSubmenu = await resolveFirstVisible([
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i),
      ]);
      if (!agregarSubmenu || !administrarSubmenu) {
        throw new Error("No se visualizaron ambos submenús de Mi Negocio.");
      }

      await expect(agregarSubmenu).toBeVisible();
      await expect(administrarSubmenu).toBeVisible();
      checkpointEvidence.miNegocioMenu = await checkpoint(page, "02-mi-negocio-menu-expanded");
      markReport(report, "Mi Negocio menu", true, "Submenú expandido con opciones esperadas.");
    } catch (error) {
      markReport(report, "Mi Negocio menu", false, error.message);
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      const agregarNegocio = await resolveFirstVisible([
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByRole("link", { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i),
      ]);
      if (!agregarNegocio) {
        throw new Error("No se encontró la opción 'Agregar Negocio'.");
      }

      await clickAndWait(agregarNegocio, page);

      const modal = await resolveFirstVisible([
        page.getByRole("dialog"),
        page.locator("[role='dialog']"),
      ]);
      if (!modal) {
        throw new Error("No apareció la ventana modal de crear negocio.");
      }

      await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();
      const nombreInput = await resolveFirstVisible([
        modal.getByLabel(/nombre del negocio/i),
        modal.getByPlaceholder(/nombre del negocio/i),
        modal.locator("input").first(),
      ]);
      if (!nombreInput) {
        throw new Error("No existe el campo 'Nombre del Negocio'.");
      }
      await expect(modal.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();

      const cancelarBtn = await resolveFirstVisible([
        modal.getByRole("button", { name: /cancelar/i }),
        modal.getByText(/^Cancelar$/),
      ]);
      const crearNegocioBtn = await resolveFirstVisible([
        modal.getByRole("button", { name: /crear negocio/i }),
        modal.getByText(/crear negocio/i),
      ]);
      if (!cancelarBtn || !crearNegocioBtn) {
        throw new Error("No se encontraron los botones 'Cancelar' y 'Crear Negocio'.");
      }

      await nombreInput.fill("Negocio Prueba Automatización");
      checkpointEvidence.agregarNegocioModal = await checkpoint(page, "03-crear-nuevo-negocio-modal");
      await clickAndWait(cancelarBtn, page);

      markReport(
        report,
        "Agregar Negocio modal",
        true,
        "Modal validado y cerrado con botón Cancelar."
      );
    } catch (error) {
      markReport(report, "Agregar Negocio modal", false, error.message);
    }

    // Step 4: Open Administrar Negocios
    try {
      await ensureMiNegocioExpanded(page);
      const administrarNegocios = await resolveFirstVisible([
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i),
      ]);
      if (!administrarNegocios) {
        throw new Error("No se encontró la opción 'Administrar Negocios'.");
      }
      await clickAndWait(administrarNegocios, page);

      await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible();
      await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
      await expect(page.getByText(/tus negocios/i)).toBeVisible();
      await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible();

      checkpointEvidence.administrarNegocios = await checkpoint(
        page,
        "04-administrar-negocios-account-page"
      );
      markReport(report, "Administrar Negocios view", true, "Vista de cuenta cargada correctamente.");
    } catch (error) {
      markReport(report, "Administrar Negocios view", false, error.message);
    }

    // Step 5: Validate Información General
    try {
      await expect(page.getByText(/business plan/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();

      const infoGeneralContainer = page
        .locator("section,article,div")
        .filter({ hasText: /informaci[oó]n general/i })
        .first();
      await expect(infoGeneralContainer).toBeVisible();

      const emailField = page
        .locator("body")
        .getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)
        .first();
      await expect(emailField).toBeVisible();

      const infoText = (await infoGeneralContainer.innerText()).replace(/\s+/g, " ").trim();
      const hasCandidateName = infoText
        .split(/[\n,]/)
        .map((value) => value.trim())
        .some((value) => {
          if (!value) return false;
          if (/informaci[oó]n general|business plan|cambiar plan|@/i.test(value)) return false;
          return value.length >= 3;
        });

      if (!hasCandidateName) {
        throw new Error("No se detectó un email de usuario visible.");
      }

      markReport(report, "Información General", true, "Nombre/email, plan y botón de cambio visibles.");
    } catch (error) {
      markReport(report, "Información General", false, error.message);
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      await expect(page.getByText(/cuenta creada/i)).toBeVisible();
      await expect(page.getByText(/estado activo/i)).toBeVisible();
      await expect(page.getByText(/idioma seleccionado/i)).toBeVisible();
      markReport(report, "Detalles de la Cuenta", true, "Campos de detalle de cuenta presentes.");
    } catch (error) {
      markReport(report, "Detalles de la Cuenta", false, error.message);
    }

    // Step 7: Validate Tus Negocios
    try {
      await expect(page.getByText(/tus negocios/i)).toBeVisible();
      await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();

      const addBusinessButton = await resolveFirstVisible([
        page.getByRole("button", { name: /^agregar negocio$/i }),
        page.getByRole("link", { name: /^agregar negocio$/i }),
        page.getByText(/^Agregar Negocio$/),
      ]);
      if (!addBusinessButton) {
        throw new Error("No se encontró botón 'Agregar Negocio' dentro de Tus Negocios.");
      }
      await expect(addBusinessButton).toBeVisible();

      markReport(report, "Tus Negocios", true, "Listado y controles de negocios visibles.");
    } catch (error) {
      markReport(report, "Tus Negocios", false, error.message);
    }

    // Step 8: Validate Términos y Condiciones
    try {
      const termsResult = await validateLegalPage({
        page,
        linkName: /t[eé]rminos y condiciones/i,
        headingPattern: /t[eé]rminos y condiciones/i,
        screenshotName: "05-terminos-y-condiciones",
      });
      checkpointEvidence.terminos = termsResult.screenshotPath;
      legalUrls.terminos = termsResult.finalUrl;
      markReport(
        report,
        "Términos y Condiciones",
        true,
        `Contenido legal visible. URL: ${termsResult.finalUrl}`
      );
    } catch (error) {
      markReport(report, "Términos y Condiciones", false, error.message);
    }

    // Step 9: Validate Política de Privacidad
    try {
      const privacyResult = await validateLegalPage({
        page,
        linkName: /pol[ií]tica de privacidad/i,
        headingPattern: /pol[ií]tica de privacidad/i,
        screenshotName: "06-politica-de-privacidad",
      });
      checkpointEvidence.politica = privacyResult.screenshotPath;
      legalUrls.politica = privacyResult.finalUrl;
      markReport(
        report,
        "Política de Privacidad",
        true,
        `Contenido legal visible. URL: ${privacyResult.finalUrl}`
      );
    } catch (error) {
      markReport(report, "Política de Privacidad", false, error.message);
    }

    // Step 10: Final report
    fs.mkdirSync(REPORT_DIR, { recursive: true });
    const reportPayload = {
      test: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      statusByField: report,
      evidence: checkpointEvidence,
      legalUrls,
    };
    const reportFile = path.join(REPORT_DIR, `${nowStamp()}-saleads-mi-negocio-report.json`);
    fs.writeFileSync(reportFile, JSON.stringify(reportPayload, null, 2), "utf-8");

    const printable = REPORT_FIELDS.map((field) => ({
      step: field,
      status: report[field].status,
      details: report[field].details,
    }));
    console.table(printable);
    console.log(`Report JSON: ${reportFile}`);

    const failedSteps = REPORT_FIELDS.filter((field) => report[field].status !== "PASS");
    expect(
      failedSteps,
      `Validaciones con FAIL: ${failedSteps.join(", ")}. Ver reporte: ${reportFile}`
    ).toEqual([]);
  });
});
