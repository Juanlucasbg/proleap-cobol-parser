const fs = require("fs");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

const STEP_FIELDS = [
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

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await sleep(800);
}

function slugify(text) {
  return text
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

async function isVisible(locator, timeout = 1500) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function findVisibleOrThrow(candidates, description, timeout = 20000) {
  const deadline = Date.now() + timeout;

  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      const first = candidate.first();
      if (await first.isVisible().catch(() => false)) {
        return first;
      }
    }
    await sleep(300);
  }

  throw new Error(`No visible element found for: ${description}`);
}

async function findVisibleOrNull(candidates, timeout = 5000) {
  try {
    return await findVisibleOrThrow(candidates, "optional locator", timeout);
  } catch {
    return null;
  }
}

async function clickAndWait(page, locator) {
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click();
  await waitForUi(page);
}

async function captureCheckpoint(page, testInfo, label, checkpoints) {
  const fileName = `${String(checkpoints.length + 1).padStart(2, "0")}-${slugify(label)}.png`;
  const checkpointPath = testInfo.outputPath(fileName);
  await page.screenshot({ path: checkpointPath, fullPage: true });
  checkpoints.push({ label, path: checkpointPath });
}

async function runStep(stepName, report, failures, fn) {
  try {
    await fn();
    report[stepName] = { status: "PASS" };
  } catch (error) {
    report[stepName] = { status: "FAIL", error: error.message };
    failures.push(stepName);
  }
}

async function openLegalTarget(page, locator) {
  const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
  await locator.click();
  await waitForUi(page);

  const popup = await popupPromise;
  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await sleep(1000);
    return { openedInPopup: true, targetPage: popup };
  }

  return { openedInPopup: false, targetPage: page };
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = Object.fromEntries(STEP_FIELDS.map((field) => [field, { status: "FAIL", error: "Not executed" }]));
  const failures = [];
  const checkpoints = [];
  const finalUrls = {};

  await runStep("Login", report, failures, async () => {
    const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || process.env.BASE_URL;
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    const loginButton = await findVisibleOrThrow(
      [
        page.getByRole("button", { name: /sign in with google|continuar con google|iniciar con google/i }),
        page.getByRole("button", { name: /google/i }),
        page.getByText(/sign in with google|continuar con google|google/i),
      ],
      "Google login button"
    );

    const authPopupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
    await clickAndWait(page, loginButton);
    const authPopup = await authPopupPromise;
    const authPage = authPopup || page;

    const accountSelector = authPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });
    if (await isVisible(accountSelector, 12000)) {
      await accountSelector.click();
      await waitForUi(authPage);
    }

    if (authPopup) {
      await page.bringToFront();
      await waitForUi(page);
    }

    const sidebarVisible =
      (await isVisible(page.locator("aside"), 20000)) ||
      (await isVisible(page.getByRole("navigation"), 20000)) ||
      (await isVisible(page.getByText(/mi negocio|negocio/i), 20000));

    if (!sidebarVisible) {
      throw new Error("Main application or left sidebar was not detected after login.");
    }

    await captureCheckpoint(page, testInfo, "dashboard-loaded", checkpoints);
  });

  await runStep("Mi Negocio menu", report, failures, async () => {
    const negocioSection = await findVisibleOrNull(
      [page.getByRole("button", { name: /^Negocio$/i }), page.getByText(/^Negocio$/i)],
      5000
    );
    if (negocioSection) {
      await clickAndWait(page, negocioSection);
    }

    const miNegocio = await findVisibleOrThrow(
      [page.getByRole("button", { name: /^Mi Negocio$/i }), page.getByText(/^Mi Negocio$/i)],
      "Mi Negocio sidebar option"
    );
    await clickAndWait(page, miNegocio);

    await expect(page.getByText(/^Agregar Negocio$/i)).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i)).toBeVisible();

    await captureCheckpoint(page, testInfo, "mi-negocio-menu-expanded", checkpoints);
  });

  await runStep("Agregar Negocio modal", report, failures, async () => {
    const agregarNegocio = await findVisibleOrThrow(
      [page.getByRole("button", { name: /^Agregar Negocio$/i }), page.getByText(/^Agregar Negocio$/i)],
      "Agregar Negocio option"
    );
    await clickAndWait(page, agregarNegocio);

    await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
    const businessNameInput = await findVisibleOrThrow(
      [page.getByLabel(/Nombre del Negocio/i), page.getByPlaceholder(/Nombre del Negocio/i)],
      "Nombre del Negocio input",
      10000
    );
    await expect(businessNameInput).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatizacion");
    await captureCheckpoint(page, testInfo, "agregar-negocio-modal", checkpoints);
    await clickAndWait(page, page.getByRole("button", { name: /^Cancelar$/i }));
  });

  await runStep("Administrar Negocios view", report, failures, async () => {
    const administrarNegocios = await findVisibleOrNull(
      [page.getByRole("button", { name: /^Administrar Negocios$/i }), page.getByText(/^Administrar Negocios$/i)],
      4000
    );

    if (!administrarNegocios) {
      const miNegocio = await findVisibleOrThrow(
        [page.getByRole("button", { name: /^Mi Negocio$/i }), page.getByText(/^Mi Negocio$/i)],
        "Mi Negocio option to re-open menu"
      );
      await clickAndWait(page, miNegocio);
    }

    const administrarNegociosAction = await findVisibleOrThrow(
      [page.getByRole("button", { name: /^Administrar Negocios$/i }), page.getByText(/^Administrar Negocios$/i)],
      "Administrar Negocios option"
    );
    await clickAndWait(page, administrarNegociosAction);

    await expect(page.getByText(/Informacion General|Información General/i)).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Seccion Legal|Sección Legal/i)).toBeVisible();

    await captureCheckpoint(page, testInfo, "administrar-negocios-account-page", checkpoints);
  });

  await runStep("Información General", report, failures, async () => {
    const infoSection = page.locator("section,div").filter({ hasText: /Informacion General|Información General/i }).first();
    await expect(infoSection).toBeVisible();

    const emailInSection = infoSection.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
    await expect(emailInSection.first()).toBeVisible();
    await expect(infoSection.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(infoSection.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

    const hasUserNameNearEmail = await infoSection.evaluate((node) => {
      const texts = Array.from(node.querySelectorAll("h1,h2,h3,h4,h5,p,span,strong,div"))
        .map((element) => (element.textContent || "").trim())
        .filter(Boolean);
      const emailIndex = texts.findIndex((text) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(text));
      if (emailIndex === -1) return false;

      return texts
        .slice(Math.max(0, emailIndex - 4), emailIndex)
        .some(
          (text) =>
            !text.includes("@") &&
            !/BUSINESS PLAN|Cambiar Plan|Informacion General|Información General/i.test(text)
        );
    });

    if (!hasUserNameNearEmail) {
      throw new Error("User name was not clearly detectable near the user email.");
    }
  });

  await runStep("Detalles de la Cuenta", report, failures, async () => {
    const detailsSection = page
      .locator("section,div")
      .filter({ hasText: /Detalles de la Cuenta/i })
      .first();
    await expect(detailsSection).toBeVisible();
    await expect(detailsSection.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(detailsSection.getByText(/Estado activo/i)).toBeVisible();
    await expect(detailsSection.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runStep("Tus Negocios", report, failures, async () => {
    const businessSection = page.locator("section,div").filter({ hasText: /Tus Negocios/i }).first();
    await expect(businessSection).toBeVisible();
    await expect(businessSection.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible();
    await expect(businessSection.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();

    const businessListVisible = await businessSection.evaluate((node) => {
      const obviousList =
        node.querySelectorAll("li, tr, [role='row'], [data-testid*='business'], [data-testid*='negocio']").length > 0;
      const nonEmptyLines = (node.textContent || "")
        .split("\n")
        .map((line) => line.trim())
        .filter(Boolean);
      return obviousList || nonEmptyLines.length >= 5;
    });

    if (!businessListVisible) {
      throw new Error("Business list was not clearly visible in 'Tus Negocios'.");
    }
  });

  await runStep("Términos y Condiciones", report, failures, async () => {
    const termsLink = await findVisibleOrThrow(
      [page.getByRole("link", { name: /Terminos y Condiciones|Términos y Condiciones/i }), page.getByText(/Terminos y Condiciones|Términos y Condiciones/i)],
      "Términos y Condiciones link"
    );

    const { openedInPopup, targetPage } = await openLegalTarget(page, termsLink);
    await expect(targetPage.getByText(/Terminos y Condiciones|Términos y Condiciones/i)).toBeVisible();

    const legalParagraph = targetPage.locator("p,li,article,main div").filter({ hasText: /./ }).first();
    await expect(legalParagraph).toBeVisible();

    finalUrls.termsAndConditions = targetPage.url();
    await captureCheckpoint(targetPage, testInfo, "terminos-y-condiciones", checkpoints);

    if (openedInPopup) {
      await targetPage.close();
      await page.bringToFront();
      await waitForUi(page);
    }
  });

  await runStep("Política de Privacidad", report, failures, async () => {
    const privacyLink = await findVisibleOrThrow(
      [page.getByRole("link", { name: /Politica de Privacidad|Política de Privacidad/i }), page.getByText(/Politica de Privacidad|Política de Privacidad/i)],
      "Política de Privacidad link"
    );

    const { openedInPopup, targetPage } = await openLegalTarget(page, privacyLink);
    await expect(targetPage.getByText(/Politica de Privacidad|Política de Privacidad/i)).toBeVisible();

    const legalParagraph = targetPage.locator("p,li,article,main div").filter({ hasText: /./ }).first();
    await expect(legalParagraph).toBeVisible();

    finalUrls.privacyPolicy = targetPage.url();
    await captureCheckpoint(targetPage, testInfo, "politica-de-privacidad", checkpoints);

    if (openedInPopup) {
      await targetPage.close();
      await page.bringToFront();
      await waitForUi(page);
    }
  });

  const outputReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    results: report,
    finalUrls,
    screenshots: checkpoints,
  };

  const reportPath = testInfo.outputPath("saleads_mi_negocio_full_report.json");
  fs.writeFileSync(reportPath, JSON.stringify(outputReport, null, 2), "utf8");
  await testInfo.attach("saleads-mi-negocio-report", { path: reportPath, contentType: "application/json" });

  if (failures.length > 0) {
    throw new Error(`Validation failures: ${failures.join(", ")}`);
  }
});
