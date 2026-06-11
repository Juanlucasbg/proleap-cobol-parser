const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT_EMAIL ||
  "juanlucasbarbiergarzon@gmail.com";

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

function resolveLoginUrl() {
  if (process.env.SALEADS_LOGIN_URL) {
    return process.env.SALEADS_LOGIN_URL;
  }

  if (process.env.SALEADS_BASE_URL) {
    return `${process.env.SALEADS_BASE_URL.replace(/\/$/, "")}/login`;
  }

  return null;
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle").catch(() => {});
}

async function takeScreenshot(page, screenshotsDir, filename) {
  const output = path.join(screenshotsDir, filename);
  await page.screenshot({ path: output, fullPage: true });
  return output;
}

function createReportStore() {
  const status = {};
  const details = {};

  for (const field of REPORT_FIELDS) {
    status[field] = "FAIL";
    details[field] = "Not executed";
  }

  return { status, details };
}

function markPass(report, field, detail) {
  report.status[field] = "PASS";
  report.details[field] = detail;
}

function markFail(report, field, error) {
  report.status[field] = "FAIL";
  report.details[field] = error instanceof Error ? error.message : String(error);
}

function markSkipped(report, field, reason) {
  report.status[field] = "FAIL";
  report.details[field] = `Skipped: ${reason}`;
}

async function clickAndTrackPage(context, currentPage, locator) {
  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);

  await locator.click();
  await waitForUi(currentPage);

  const popup = await popupPromise;
  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await popup.waitForLoadState("networkidle").catch(() => {});
    return { targetPage: popup, openedNewTab: true };
  }

  return { targetPage: currentPage, openedNewTab: false };
}

async function assertLegalPage(targetPage, titlePattern) {
  await expect(targetPage.getByRole("heading", { name: titlePattern }).first()).toBeVisible();
  const legalBody = targetPage.locator("p, li, article, section").first();
  await expect(legalBody).toBeVisible();
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  test.setTimeout(300000);

  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
  const screenshotsDir = path.join("test-results", "saleads-mi-negocio", timestamp);
  fs.mkdirSync(screenshotsDir, { recursive: true });

  const report = createReportStore();
  const evidence = {
    screenshotsDir,
    terminosUrl: null,
    privacidadUrl: null,
    checkpoints: {},
  };

  let appPage = page;
  const loginUrl = resolveLoginUrl();

  try {
    if (page.url() === "about:blank") {
      if (!loginUrl) {
        throw new Error(
          "Missing SALEADS_LOGIN_URL or SALEADS_BASE_URL. The test requires an environment-specific login page."
        );
      }
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    const loginButton = page
      .getByRole("button", {
        name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
      })
      .first();
    await expect(loginButton).toBeVisible();

    const authPopup = await clickAndTrackPage(context, page, loginButton);
    if (authPopup.openedNewTab) {
      const accountOption = authPopup.targetPage.getByText(GOOGLE_ACCOUNT_EMAIL, {
        exact: true,
      });
      if (await accountOption.isVisible().catch(() => false)) {
        await accountOption.click();
      }
    } else {
      const inlineAccount = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });
      if (await inlineAccount.isVisible().catch(() => false)) {
        await inlineAccount.click();
        await waitForUi(page);
      }
    }

    const sidebar = page.locator("aside, nav").first();
    await expect(sidebar).toBeVisible({ timeout: 45000 });
    evidence.checkpoints.dashboard = await takeScreenshot(
      page,
      screenshotsDir,
      "01-dashboard-loaded.png"
    );
    markPass(report, "Login", "Dashboard and left sidebar are visible after Google sign-in.");
  } catch (error) {
    markFail(report, "Login", error);
  }

  if (report.status["Login"] !== "PASS") {
    for (const field of REPORT_FIELDS) {
      if (field !== "Login") {
        markSkipped(report, field, "Login step failed.");
      }
    }
  } else {
    try {
      const negocioSection = page.getByText(/^Negocio$/i).first();
      if (await negocioSection.isVisible().catch(() => false)) {
        await negocioSection.click();
        await waitForUi(page);
      }

      const miNegocio = page.getByText(/^Mi Negocio$/i).first();
      await expect(miNegocio).toBeVisible();
      await miNegocio.click();
      await waitForUi(page);

      await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
      await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();

      evidence.checkpoints.miNegocioMenu = await takeScreenshot(
        page,
        screenshotsDir,
        "02-mi-negocio-menu-expanded.png"
      );
      markPass(report, "Mi Negocio menu", "Mi Negocio submenu expanded with required options.");
    } catch (error) {
      markFail(report, "Mi Negocio menu", error);
    }

    try {
      const agregarNegocio = page.getByText(/^Agregar Negocio$/i).first();
      await expect(agregarNegocio).toBeVisible();
      await agregarNegocio.click();
      await waitForUi(page);

      const modal = page.getByRole("dialog").first();
      await expect(modal).toBeVisible();
      await expect(modal.getByText("Crear Nuevo Negocio")).toBeVisible();
      await expect(modal.getByLabel("Nombre del Negocio")).toBeVisible();
      await expect(modal.getByText("Tienes 2 de 3 negocios")).toBeVisible();
      await expect(modal.getByRole("button", { name: "Cancelar" })).toBeVisible();
      await expect(modal.getByRole("button", { name: "Crear Negocio" })).toBeVisible();

      evidence.checkpoints.agregarNegocioModal = await takeScreenshot(
        page,
        screenshotsDir,
        "03-agregar-negocio-modal.png"
      );

      const nombreInput = modal.getByLabel("Nombre del Negocio");
      await nombreInput.fill("Negocio Prueba Automatizacion");
      await modal.getByRole("button", { name: "Cancelar" }).click();
      await expect(modal).not.toBeVisible();

      markPass(report, "Agregar Negocio modal", "Modal fields and action buttons validated.");
    } catch (error) {
      markFail(report, "Agregar Negocio modal", error);
    }

    try {
      const administrarOption = page.getByText(/^Administrar Negocios$/i).first();
      if (!(await administrarOption.isVisible().catch(() => false))) {
        const miNegocio = page.getByText(/^Mi Negocio$/i).first();
        await miNegocio.click();
        await waitForUi(page);
      }

      await expect(administrarOption).toBeVisible();
      await administrarOption.click();
      await waitForUi(page);

      await expect(page.getByText("Información General")).toBeVisible();
      await expect(page.getByText("Detalles de la Cuenta")).toBeVisible();
      await expect(page.getByText("Tus Negocios")).toBeVisible();
      await expect(page.getByText("Sección Legal")).toBeVisible();

      evidence.checkpoints.administrarNegocios = await takeScreenshot(
        page,
        screenshotsDir,
        "04-administrar-negocios-view.png"
      );
      markPass(
        report,
        "Administrar Negocios view",
        "Main account sections are present in Administrar Negocios."
      );
    } catch (error) {
      markFail(report, "Administrar Negocios view", error);
    }

    try {
      const infoGeneralSection = page
        .locator("section, div")
        .filter({ has: page.getByText("Información General") })
        .first();
      await expect(infoGeneralSection).toBeVisible();

      await expect(infoGeneralSection.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/)).toBeVisible();
      await expect(infoGeneralSection.getByText(/Nombre|Usuario|Name|User/i).first()).toBeVisible();
      await expect(page.getByText("BUSINESS PLAN")).toBeVisible();
      await expect(page.getByRole("button", { name: "Cambiar Plan" })).toBeVisible();

      markPass(report, "Información General", "User, plan and action button are visible.");
    } catch (error) {
      markFail(report, "Información General", error);
    }

    try {
      await expect(page.getByText("Detalles de la Cuenta")).toBeVisible();
      await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
      await expect(page.getByText(/Estado activo/i)).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();

      markPass(report, "Detalles de la Cuenta", "Account details section fields are visible.");
    } catch (error) {
      markFail(report, "Detalles de la Cuenta", error);
    }

    try {
      const negociosSection = page
        .locator("section, div")
        .filter({ has: page.getByText("Tus Negocios") })
        .first();
      await expect(negociosSection).toBeVisible();
      await expect(negociosSection.getByRole("button", { name: "Agregar Negocio" })).toBeVisible();
      await expect(negociosSection.getByText("Tienes 2 de 3 negocios")).toBeVisible();

      const listItem = negociosSection.locator("li, [role='listitem'], table tbody tr, article, .card").first();
      await expect(listItem).toBeVisible();

      markPass(report, "Tus Negocios", "Business list, quota text and add button are visible.");
    } catch (error) {
      markFail(report, "Tus Negocios", error);
    }

    try {
      const terminosLink = page.getByRole("link", { name: "Términos y Condiciones" }).first();
      await expect(terminosLink).toBeVisible();

      const terminosTarget = await clickAndTrackPage(context, page, terminosLink);
      await assertLegalPage(terminosTarget.targetPage, /Términos y Condiciones/i);
      evidence.terminosUrl = terminosTarget.targetPage.url();
      evidence.checkpoints.terminos = await takeScreenshot(
        terminosTarget.targetPage,
        screenshotsDir,
        "05-terminos-y-condiciones.png"
      );

      if (terminosTarget.openedNewTab) {
        await terminosTarget.targetPage.close();
        await appPage.bringToFront();
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await waitForUi(page);
      }

      markPass(
        report,
        "Términos y Condiciones",
        `Legal page validated. Final URL: ${evidence.terminosUrl}`
      );
    } catch (error) {
      markFail(report, "Términos y Condiciones", error);
    }

    try {
      const privacidadLink = page.getByRole("link", { name: "Política de Privacidad" }).first();
      await expect(privacidadLink).toBeVisible();

      const privacidadTarget = await clickAndTrackPage(context, page, privacidadLink);
      await assertLegalPage(privacidadTarget.targetPage, /Pol[ií]tica de Privacidad/i);
      evidence.privacidadUrl = privacidadTarget.targetPage.url();
      evidence.checkpoints.privacidad = await takeScreenshot(
        privacidadTarget.targetPage,
        screenshotsDir,
        "06-politica-de-privacidad.png"
      );

      if (privacidadTarget.openedNewTab) {
        await privacidadTarget.targetPage.close();
        await appPage.bringToFront();
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await waitForUi(page);
      }

      markPass(
        report,
        "Política de Privacidad",
        `Legal page validated. Final URL: ${evidence.privacidadUrl}`
      );
    } catch (error) {
      markFail(report, "Política de Privacidad", error);
    }
  }

  const outputReportPath = path.join(screenshotsDir, "saleads-mi-negocio-final-report.json");
  fs.writeFileSync(
    outputReportPath,
    JSON.stringify(
      {
        name: "saleads_mi_negocio_full_test",
        generatedAt: new Date().toISOString(),
        report: report.status,
        details: report.details,
        evidence,
      },
      null,
      2
    )
  );

  console.log("Final Report (PASS/FAIL):");
  console.table(report.status);
  console.log(`Detailed report written to: ${outputReportPath}`);

  const failed = Object.entries(report.status)
    .filter(([, status]) => status !== "PASS")
    .map(([field]) => field);

  expect(
    failed,
    failed.length
      ? `Failed report fields: ${failed.join(", ")}. See ${outputReportPath} for details.`
      : "All report fields passed."
  ).toEqual([]);
});
