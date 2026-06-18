const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const REQUIRED_STEPS = [
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

function createReport() {
  return REQUIRED_STEPS.reduce((acc, step) => {
    acc[step] = { status: "FAIL", details: "" };
    return acc;
  }, {});
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {});
  await page.waitForTimeout(300);
}

async function visible(locator) {
  return (await locator.count()) > 0 && (await locator.first().isVisible().catch(() => false));
}

async function getFirstVisibleLocator(candidates, errorMessage, timeoutMs = 30000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      if (await visible(candidate)) {
        return candidate.first();
      }
    }

    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  throw new Error(errorMessage);
}

async function clickAndWait(page, locator) {
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click();
  await waitForUi(page);
}

async function takeCheckpoint(page, testInfo, name, options = {}) {
  const screenshotPath = testInfo.outputPath(name);
  await page.screenshot({
    path: screenshotPath,
    fullPage: Boolean(options.fullPage),
  });
  await testInfo.attach(name, {
    path: screenshotPath,
    contentType: "image/png",
  });
  return screenshotPath;
}

async function writeReport(testInfo, report) {
  const reportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");
  await testInfo.attach("final-report", {
    path: reportPath,
    contentType: "application/json",
  });
  return reportPath;
}

async function runAndRecord(stepName, report, failures, action) {
  try {
    const details = await action();
    report[stepName] = {
      status: "PASS",
      details: details || "Validated successfully.",
    };
  } catch (error) {
    report[stepName] = {
      status: "FAIL",
      details: error instanceof Error ? error.message : String(error),
    };
    failures.push(stepName);
  }
}

async function openLegalLinkAndValidate({
  page,
  testInfo,
  linkText,
  headingPattern,
  screenshotName,
}) {
  const context = page.context();
  const link = await getFirstVisibleLocator(
    [
      page.getByRole("link", { name: new RegExp(linkText, "i") }),
      page.getByText(new RegExp(`^${linkText}$`, "i")),
      page.getByText(new RegExp(linkText, "i")),
    ],
    `No se encontró el enlace legal "${linkText}".`,
    20000
  );

  const previousUrl = page.url();
  const popupPromise = context.waitForEvent("page", { timeout: 6000 }).catch(() => null);
  await clickAndWait(page, link);
  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await popup.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {});
    await expect(popup.getByText(headingPattern)).toBeVisible({ timeout: 30000 });
    const legalBody = popup.locator("main, article, body").first();
    await expect(legalBody).toBeVisible();
    const legalText = (await legalBody.innerText()).replace(/\s+/g, " ").trim();
    expect(legalText.length).toBeGreaterThan(120);
    await takeCheckpoint(popup, testInfo, screenshotName, { fullPage: true });
    const finalUrl = popup.url();
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
    return finalUrl;
  }

  await expect(page.getByText(headingPattern)).toBeVisible({ timeout: 30000 });
  const legalBody = page.locator("main, article, body").first();
  await expect(legalBody).toBeVisible();
  const legalText = (await legalBody.innerText()).replace(/\s+/g, " ").trim();
  expect(legalText.length).toBeGreaterThan(120);
  await takeCheckpoint(page, testInfo, screenshotName, { fullPage: true });
  const finalUrl = page.url();

  if (finalUrl !== previousUrl) {
    await page.goBack().catch(() => {});
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = createReport();
  const failures = [];
  const loginUrl = process.env.SALEADS_LOGIN_URL;

  await runAndRecord("Login", report, failures, async () => {
    if (!loginUrl) {
      throw new Error("Set SALEADS_LOGIN_URL to the current environment login page before running this test.");
    }

    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    const googleSignIn = await getFirstVisibleLocator(
      [
        page.getByRole("button", {
          name: /sign in with google|continuar con google|iniciar sesi[oó]n con google|google/i,
        }),
        page.getByRole("link", {
          name: /sign in with google|continuar con google|iniciar sesi[oó]n con google|google/i,
        }),
        page.locator("button, a").filter({ hasText: /google/i }),
      ],
      "No se encontró un botón de login con Google."
    );

    const popupPromise = page.context().waitForEvent("page", { timeout: 6000 }).catch(() => null);
    await clickAndWait(page, googleSignIn);
    const authPopup = await popupPromise;

    if (authPopup) {
      await authPopup.waitForLoadState("domcontentloaded");
      const accountOption = authPopup.getByText(GOOGLE_ACCOUNT_EMAIL).first();
      if (await visible(accountOption)) {
        await accountOption.click();
      }
      await authPopup.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {});
      await page.bringToFront();
      await waitForUi(page);
    } else {
      const accountOptionInPage = page.getByText(GOOGLE_ACCOUNT_EMAIL).first();
      if (await visible(accountOptionInPage)) {
        await clickAndWait(page, accountOptionInPage);
      }
    }

    const sidebar = await getFirstVisibleLocator(
      [
        page.locator("aside").filter({ hasText: /negocio/i }),
        page.locator("nav").filter({ hasText: /negocio/i }),
      ],
      "No se encontró la barra lateral principal luego del login.",
      60000
    );
    await expect(sidebar).toBeVisible();
    await takeCheckpoint(page, testInfo, "01-dashboard-loaded.png", { fullPage: true });
    return "Aplicación principal y barra lateral visibles.";
  });

  await runAndRecord("Mi Negocio menu", report, failures, async () => {
    const negocioSection = await getFirstVisibleLocator(
      [page.getByText(/^Negocio$/i), page.getByText(/Negocio/i)],
      "No se encontró la sección 'Negocio' en el sidebar."
    );
    await clickAndWait(page, negocioSection);

    const miNegocio = await getFirstVisibleLocator(
      [page.getByText(/^Mi Negocio$/i), page.getByRole("button", { name: /mi negocio/i })],
      "No se encontró la opción 'Mi Negocio'."
    );
    await clickAndWait(page, miNegocio);

    await expect(page.getByText(/Agregar Negocio/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Administrar Negocios/i)).toBeVisible({ timeout: 20000 });
    await takeCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png");
    return "Submenú expandido con 'Agregar Negocio' y 'Administrar Negocios'.";
  });

  await runAndRecord("Agregar Negocio modal", report, failures, async () => {
    const agregarNegocio = await getFirstVisibleLocator(
      [page.getByRole("button", { name: /agregar negocio/i }), page.getByText(/Agregar Negocio/i)],
      "No se encontró el botón 'Agregar Negocio'."
    );
    await clickAndWait(page, agregarNegocio);

    const modalTitle = page.getByText(/Crear Nuevo Negocio/i).first();
    await expect(modalTitle).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Nombre del Negocio/i)).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();
    await takeCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");

    const nameField = page.getByLabel(/Nombre del Negocio/i).first();
    if (await visible(nameField)) {
      await nameField.fill("Negocio Prueba Automatización");
    } else {
      const textInput = page
        .locator('input[type="text"], input:not([type]), textarea')
        .filter({ hasNotText: /buscar/i })
        .first();
      if (await visible(textInput)) {
        await textInput.fill("Negocio Prueba Automatización");
      }
    }

    await clickAndWait(page, page.getByRole("button", { name: /Cancelar/i }).first());
    await expect(modalTitle).not.toBeVisible({ timeout: 15000 });
    return "Modal validado y cerrado con cancelar.";
  });

  await runAndRecord("Administrar Negocios view", report, failures, async () => {
    const miNegocio = await getFirstVisibleLocator(
      [page.getByText(/^Mi Negocio$/i), page.getByRole("button", { name: /mi negocio/i })],
      "No se encontró 'Mi Negocio' para re-expandir el menú."
    );
    await clickAndWait(page, miNegocio);

    const administrarNegocios = await getFirstVisibleLocator(
      [
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByText(/Administrar Negocios/i),
      ],
      "No se encontró 'Administrar Negocios'."
    );
    await clickAndWait(page, administrarNegocios);

    await expect(page.getByText(/Información General/i)).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Sección Legal/i)).toBeVisible();
    await takeCheckpoint(page, testInfo, "04-administrar-negocios-view.png", { fullPage: true });
    return "Vista de cuenta cargada con todas las secciones principales.";
  });

  await runAndRecord("Información General", report, failures, async () => {
    await expect(page.getByText(/Información General/i)).toBeVisible();

    const knownEmail = page.getByText(GOOGLE_ACCOUNT_EMAIL).first();
    const emailPattern = page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first();
    if (await visible(knownEmail)) {
      await expect(knownEmail).toBeVisible();
    } else {
      await expect(emailPattern).toBeVisible();
    }

    const likelyName = page.getByText(/juan|lucas|barbier|garzon/i).first();
    const genericNameLabel = page.getByText(/nombre|usuario|name|user/i).first();
    if (await visible(likelyName)) {
      await expect(likelyName).toBeVisible();
    } else {
      await expect(genericNameLabel).toBeVisible();
    }

    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
    return "Nombre/usuario, email, plan y acción de cambio de plan visibles.";
  });

  await runAndRecord("Detalles de la Cuenta", report, failures, async () => {
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
    return "Campos principales de detalles de cuenta visibles.";
  });

  await runAndRecord("Tus Negocios", report, failures, async () => {
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();

    const businessListCandidate = page
      .locator('[role="listitem"], li, .card, .business-item, .business-card')
      .first();
    if (await visible(businessListCandidate)) {
      await expect(businessListCandidate).toBeVisible();
    } else {
      // Fallback for grid/card layouts without semantic list items.
      await expect(page.getByText(/negocio/i).first()).toBeVisible();
    }

    return "Listado de negocios y límite de negocios visibles.";
  });

  await runAndRecord("Términos y Condiciones", report, failures, async () => {
    const finalUrl = await openLegalLinkAndValidate({
      page,
      testInfo,
      linkText: "Términos y Condiciones",
      headingPattern: /Términos y Condiciones/i,
      screenshotName: "05-terminos-y-condiciones.png",
    });
    return `Documento legal validado. URL final: ${finalUrl}`;
  });

  await runAndRecord("Política de Privacidad", report, failures, async () => {
    const finalUrl = await openLegalLinkAndValidate({
      page,
      testInfo,
      linkText: "Política de Privacidad",
      headingPattern: /Política de Privacidad/i,
      screenshotName: "06-politica-de-privacidad.png",
    });
    return `Documento legal validado. URL final: ${finalUrl}`;
  });

  const reportPath = await writeReport(testInfo, report);
  console.log(`Final report saved at: ${reportPath}`);
  console.log(JSON.stringify(report, null, 2));

  if (failures.length > 0) {
    throw new Error(`Validation failures detected: ${failures.join(", ")}`);
  }
});
