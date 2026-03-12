const fs = require("node:fs");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

const ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const LOGIN_URL = process.env.SALEADS_LOGIN_URL;
const ARTIFACTS_DIR = path.join(__dirname, "..", "artifacts");

function slugify(input) {
  return input
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
}

function compactText(input) {
  return (input || "").replace(/\s+/g, " ").trim();
}

async function saveCheckpoint(page, label, fullPage = true) {
  await fs.promises.mkdir(ARTIFACTS_DIR, { recursive: true });
  const filename = `${Date.now()}-${slugify(label)}.png`;
  const outputPath = path.join(ARTIFACTS_DIR, filename);
  await page.screenshot({ path: outputPath, fullPage });
  return outputPath;
}

async function waitForUi(page, waitMs = 700) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(waitMs);
}

async function clickAndWait(locator, page) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function findFirstVisible(locators, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const locator of locators) {
      const candidate = locator.first();
      const count = await candidate.count();
      if (count === 0) {
        continue;
      }

      const visible = await candidate.isVisible().catch(() => false);
      if (visible) {
        return candidate;
      }
    }

    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  throw new Error("No visible matching locator found.");
}

async function ensureMiNegocioExpanded(page) {
  const agregarVisible = await page
    .getByText(/Agregar Negocio/i)
    .first()
    .isVisible()
    .catch(() => false);
  const administrarVisible = await page
    .getByText(/Administrar Negocios/i)
    .first()
    .isVisible()
    .catch(() => false);

  if (agregarVisible && administrarVisible) {
    return;
  }

  const negocioTrigger = await findFirstVisible([
    page.getByRole("button", { name: /Mi Negocio/i }),
    page.getByRole("link", { name: /Mi Negocio/i }),
    page.getByText(/Mi Negocio/i),
  ]);

  await clickAndWait(negocioTrigger, page);
}

async function openLegalLinkAndValidate({
  page,
  context,
  linkNameRegex,
  headingRegex,
  checkpointLabel,
}) {
  const link = await findFirstVisible([
    page.getByRole("link", { name: linkNameRegex }),
    page.getByText(linkNameRegex),
  ]);

  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  await link.click();

  let targetPage = await popupPromise;
  if (targetPage) {
    await targetPage.waitForLoadState("domcontentloaded");
    await targetPage.bringToFront();
  } else {
    targetPage = page;
    await waitForUi(targetPage, 900);
  }

  const heading = targetPage.getByRole("heading", { name: headingRegex }).first();
  if ((await heading.count()) > 0) {
    await expect(heading).toBeVisible();
  } else {
    await expect(targetPage.getByText(headingRegex).first()).toBeVisible();
  }

  const bodyText = compactText(await targetPage.locator("body").textContent());
  expect(bodyText.length, "Expected legal content body text to be visible.").toBeGreaterThan(120);

  const screenshotPath = await saveCheckpoint(targetPage, checkpointLabel);
  const finalUrl = targetPage.url();

  if (targetPage !== page) {
    await targetPage.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
    await waitForUi(page);
  }

  return { screenshotPath, finalUrl };
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  await fs.promises.mkdir(ARTIFACTS_DIR, { recursive: true });

  const report = {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Información General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Términos y Condiciones": "FAIL",
    "Política de Privacidad": "FAIL",
  };

  const evidence = {
    dashboardScreenshot: null,
    expandedMenuScreenshot: null,
    modalScreenshot: null,
    accountPageScreenshot: null,
    termsScreenshot: null,
    termsUrl: null,
    privacyScreenshot: null,
    privacyUrl: null,
  };

  const failures = [];

  async function runSection(reportField, callback) {
    try {
      await callback();
      report[reportField] = "PASS";
    } catch (error) {
      report[reportField] = "FAIL";
      failures.push(`${reportField}: ${error.message}`);
    }
  }

  await runSection("Login", async () => {
    if (LOGIN_URL) {
      await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded" });
      await waitForUi(page, 1200);
    } else {
      expect(
        page.url() !== "about:blank",
        "Set SALEADS_LOGIN_URL or pre-open the login page before running.",
      ).toBeTruthy();
    }

    const loginButton = await findFirstVisible([
      page.getByRole("button", { name: /Sign in with Google/i }),
      page.getByRole("button", { name: /Google/i }),
      page.getByText(/Sign in with Google/i),
      page.getByText(/Iniciar sesión con Google/i),
    ]);

    const popupPromise = context.waitForEvent("page", { timeout: 9000 }).catch(() => null);
    await clickAndWait(loginButton, page);
    const popup = await popupPromise;

    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      const accountOption = await findFirstVisible(
        [
          popup.getByText(ACCOUNT_EMAIL, { exact: true }),
          popup.getByRole("button", { name: ACCOUNT_EMAIL }),
          popup.getByRole("link", { name: ACCOUNT_EMAIL }),
        ],
        12000,
      );
      await accountOption.click();

      await popup.waitForTimeout(1000);
      await page.bringToFront();
      await waitForUi(page, 1200);
    } else {
      const inlineAccount = await findFirstVisible(
        [
          page.getByText(ACCOUNT_EMAIL, { exact: true }),
          page.getByRole("button", { name: ACCOUNT_EMAIL }),
          page.getByRole("link", { name: ACCOUNT_EMAIL }),
        ],
        8000,
      );
      await clickAndWait(inlineAccount, page);
    }

    const sidebar = page.locator("aside").first();
    if ((await sidebar.count()) > 0) {
      await expect(sidebar).toBeVisible();
    } else {
      await expect(page.getByRole("navigation").first()).toBeVisible();
    }

    await expect(page.getByText(/Negocio|Mi Negocio/i).first()).toBeVisible();
    evidence.dashboardScreenshot = await saveCheckpoint(page, "dashboard-loaded");
  });

  await runSection("Mi Negocio menu", async () => {
    const negocioLabel = await findFirstVisible([
      page.getByText(/^Negocio$/i),
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
      page.getByText(/Negocio/i),
    ]);
    await clickAndWait(negocioLabel, page);

    const miNegocioOption = await findFirstVisible([
      page.getByRole("button", { name: /Mi Negocio/i }),
      page.getByRole("link", { name: /Mi Negocio/i }),
      page.getByText(/Mi Negocio/i),
    ]);
    await clickAndWait(miNegocioOption, page);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

    evidence.expandedMenuScreenshot = await saveCheckpoint(page, "mi-negocio-menu-expanded");
  });

  await runSection("Agregar Negocio modal", async () => {
    await ensureMiNegocioExpanded(page);

    const agregarNegocioMenu = await findFirstVisible([
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);

    await clickAndWait(agregarNegocioMenu, page);

    const modal = page.getByRole("dialog").filter({ hasText: /Crear Nuevo Negocio/i }).first();
    if ((await modal.count()) > 0) {
      await expect(modal).toBeVisible();
    } else {
      await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
    }

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
    await expect(page.getByLabel(/Nombre del Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i }).first()).toBeVisible();

    evidence.modalScreenshot = await saveCheckpoint(page, "agregar-negocio-modal");

    await page.getByLabel(/Nombre del Negocio/i).first().click();
    await page
      .getByLabel(/Nombre del Negocio/i)
      .first()
      .fill("Negocio Prueba Automatización");
    await clickAndWait(page.getByRole("button", { name: /^Cancelar$/i }).first(), page);
  });

  await runSection("Administrar Negocios view", async () => {
    await ensureMiNegocioExpanded(page);

    const administrarNegociosMenu = await findFirstVisible([
      page.getByRole("button", { name: /^Administrar Negocios$/i }),
      page.getByRole("link", { name: /^Administrar Negocios$/i }),
      page.getByText(/^Administrar Negocios$/i),
    ]);

    await clickAndWait(administrarNegociosMenu, page);

    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();

    evidence.accountPageScreenshot = await saveCheckpoint(page, "administrar-negocios-account-page");
  });

  await runSection("Información General", async () => {
    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();

    const bodyText = compactText(await page.locator("body").textContent());
    const hasEmail = bodyText.includes(ACCOUNT_EMAIL) || /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(bodyText);
    expect(hasEmail, "Expected user email to be visible.").toBeTruthy();

    const hasNameLikeText = /[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}/.test(bodyText);
    expect(hasNameLikeText, "Expected user name-like text to be visible.").toBeTruthy();
  });

  await runSection("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runSection("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
  });

  await runSection("Términos y Condiciones", async () => {
    const result = await openLegalLinkAndValidate({
      page,
      context,
      linkNameRegex: /Términos y Condiciones/i,
      headingRegex: /Términos y Condiciones/i,
      checkpointLabel: "terminos-y-condiciones",
    });

    evidence.termsScreenshot = result.screenshotPath;
    evidence.termsUrl = result.finalUrl;
  });

  await runSection("Política de Privacidad", async () => {
    const result = await openLegalLinkAndValidate({
      page,
      context,
      linkNameRegex: /Política de Privacidad/i,
      headingRegex: /Política de Privacidad/i,
      checkpointLabel: "politica-de-privacidad",
    });

    evidence.privacyScreenshot = result.screenshotPath;
    evidence.privacyUrl = result.finalUrl;
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    report,
    evidence,
    failures,
  };

  const finalReportPath = path.join(ARTIFACTS_DIR, "final-report.json");
  await fs.promises.writeFile(finalReportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await test.info().attach("final-report", {
    path: finalReportPath,
    contentType: "application/json",
  });

  expect(
    failures,
    `One or more validation groups failed:\n${failures.join("\n")}`,
  ).toHaveLength(0);
});
