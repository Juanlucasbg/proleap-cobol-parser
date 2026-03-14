const { test, expect } = require("@playwright/test");

const FINAL_FIELDS = [
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

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function escapeRegex(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page) {
  await page.waitForTimeout(400);
  await page.waitForLoadState("domcontentloaded", { timeout: 15_000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
}

async function screenshotCheckpoint(page, testInfo, fileName, fullPage = false) {
  const path = testInfo.outputPath(fileName);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(fileName, { path, contentType: "image/png" });
}

async function clickByVisibleText(page, text, { exact = true, timeout = 15_000, waitAfterClick = true } = {}) {
  const escaped = escapeRegex(text);
  const exactRegex = new RegExp(`^\\s*${escaped}\\s*$`, "i");
  const fuzzyRegex = new RegExp(escaped, "i");
  const matcher = exact ? exactRegex : fuzzyRegex;

  const locatorCandidates = [
    page.getByRole("button", { name: matcher }),
    page.getByRole("link", { name: matcher }),
    page.getByRole("menuitem", { name: matcher }),
    page.getByRole("tab", { name: matcher }),
    page.getByRole("option", { name: matcher }),
    page.getByLabel(matcher),
    page.getByText(matcher),
  ];

  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    for (const locator of locatorCandidates) {
      const candidate = locator.first();
      const visible = await candidate.isVisible().catch(() => false);
      if (!visible) {
        continue;
      }

      await candidate.scrollIntoViewIfNeeded().catch(() => {});
      await candidate.click();
      if (waitAfterClick) {
        await waitForUi(page);
      }
      return;
    }

    await page.waitForTimeout(250);
  }

  throw new Error(`Could not click a visible element with text: ${text}`);
}

async function isVisibleText(page, text) {
  const matcher = new RegExp(escapeRegex(text), "i");
  return page.getByText(matcher).first().isVisible().catch(() => false);
}

async function openLegalDocumentAndReturn({
  page,
  context,
  linkText,
  headingText,
  screenshotName,
  appReturnUrl,
  testInfo,
}) {
  const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
  await clickByVisibleText(page, linkText, { exact: false, timeout: 15_000, waitAfterClick: false });

  const popup = await popupPromise;
  const legalPage = popup || page;

  await waitForUi(legalPage);
  await expect(legalPage.getByRole("heading", { name: new RegExp(escapeRegex(headingText), "i") }).first()).toBeVisible({
    timeout: 15_000,
  });

  const legalBodyText = await legalPage.locator("body").innerText();
  if (legalBodyText.trim().length < 120) {
    throw new Error(`${headingText} page did not contain enough legal content text.`);
  }

  await screenshotCheckpoint(legalPage, testInfo, screenshotName, true);

  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close().catch(() => {});
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await legalPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
    if (appReturnUrl && page.url() !== appReturnUrl) {
      await page.goto(appReturnUrl, { waitUntil: "domcontentloaded" }).catch(() => {});
      await waitForUi(page);
    }
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const stepStatus = {};
  const failures = {};
  const evidence = {
    terminosUrl: null,
    politicaPrivacidadUrl: null,
  };

  for (const field of FINAL_FIELDS) {
    stepStatus[field] = "FAIL";
  }

  async function runSection(field, fn) {
    try {
      await fn();
      stepStatus[field] = "PASS";
    } catch (error) {
      stepStatus[field] = "FAIL";
      failures[field] = error instanceof Error ? error.message : String(error);
      await screenshotCheckpoint(page, testInfo, `error-${field.replace(/\s+/g, "-").toLowerCase()}.png`, true).catch(() => {});
    }
  }

  await runSection("Login", async () => {
    const configuredLoginUrl = process.env.SALEADS_BASE_URL || process.env.SALEADS_LOGIN_URL;
    if (configuredLoginUrl && /^(about:blank|data:,)$/.test(page.url())) {
      await page.goto(configuredLoginUrl, { waitUntil: "domcontentloaded" });
    }
    await waitForUi(page);

    const loginButtonCandidates = [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Ingresar con Google",
      "Continuar con Google",
      "Login with Google",
      "Google",
    ];

    const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
    let clickedGoogle = false;
    for (const candidate of loginButtonCandidates) {
      try {
        await clickByVisibleText(page, candidate, { exact: false, timeout: 4_000 });
        clickedGoogle = true;
        break;
      } catch (_error) {
        // Keep trying alternatives.
      }
    }
    if (!clickedGoogle) {
      throw new Error("Unable to locate a Google login button.");
    }

    const googlePopup = await popupPromise;
    if (googlePopup) {
      await waitForUi(googlePopup);
      const accountLocator = googlePopup.getByText(new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i")).first();
      if (await accountLocator.isVisible().catch(() => false)) {
        await accountLocator.click();
      }
      await waitForUi(googlePopup);
      await googlePopup.waitForEvent("close", { timeout: 20_000 }).catch(() => {});
      await page.bringToFront();
    } else {
      const accountLocator = page.getByText(new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i")).first();
      if (await accountLocator.isVisible().catch(() => false)) {
        await accountLocator.click();
      }
    }

    await waitForUi(page);
    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Negocio|Mi Negocio/i).first()).toBeVisible({ timeout: 30_000 });
    await screenshotCheckpoint(page, testInfo, "01-dashboard-loaded.png");
  });

  await runSection("Mi Negocio menu", async () => {
    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 15_000 });

    if (await isVisibleText(page, "Negocio")) {
      await clickByVisibleText(page, "Negocio", { exact: false });
    }
    await clickByVisibleText(page, "Mi Negocio", { exact: false, timeout: 15_000 });

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 15_000 });
    await screenshotCheckpoint(page, testInfo, "02-mi-negocio-expanded-menu.png");
  });

  await runSection("Agregar Negocio modal", async () => {
    await clickByVisibleText(page, "Agregar Negocio", { exact: false, timeout: 15_000 });

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible({ timeout: 15_000 });
    const nombreField = page.getByLabel(/Nombre del Negocio/i).first();
    if (!(await nombreField.isVisible().catch(() => false))) {
      await expect(page.getByPlaceholder(/Nombre del Negocio/i).first()).toBeVisible({ timeout: 15_000 });
    } else {
      await expect(nombreField).toBeVisible({ timeout: 15_000 });
    }

    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible({ timeout: 15_000 });

    await screenshotCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");

    const nameInput =
      (await nombreField.isVisible().catch(() => false)) ? nombreField : page.getByPlaceholder(/Nombre del Negocio/i).first();
    await nameInput.click();
    await nameInput.fill("Negocio Prueba Automatización");
    await clickByVisibleText(page, "Cancelar", { exact: false, timeout: 10_000 });
  });

  await runSection("Administrar Negocios view", async () => {
    if (!(await isVisibleText(page, "Administrar Negocios"))) {
      await clickByVisibleText(page, "Mi Negocio", { exact: false, timeout: 12_000 });
    }
    await clickByVisibleText(page, "Administrar Negocios", { exact: false, timeout: 15_000 });

    await expect(page.getByText(/Información General/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible({ timeout: 20_000 });
    await screenshotCheckpoint(page, testInfo, "04-administrar-negocios-page.png", true);
  });

  await runSection("Información General", async () => {
    const infoSection = page.locator("section, div").filter({ hasText: /Información General/i }).first();
    await expect(infoSection).toBeVisible({ timeout: 15_000 });

    const infoText = await infoSection.innerText();
    const hasEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(infoText);
    if (!hasEmail) {
      throw new Error("User email not visible in Información General.");
    }

    const possibleNameLines = infoText
      .split("\n")
      .map((line) => line.trim())
      .filter(
        (line) =>
          line &&
          !line.includes("@") &&
          !/Información General|BUSINESS PLAN|Cambiar Plan|Cuenta creada|Estado activo|Idioma seleccionado/i.test(line)
      );
    if (possibleNameLines.length === 0) {
      throw new Error("User name was not identifiable in Información General.");
    }

    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible({ timeout: 15_000 });
  });

  await runSection("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 15_000 });
  });

  await runSection("Tus Negocios", async () => {
    const businessSection = page.locator("section, div").filter({ hasText: /Tus Negocios/i }).first();
    await expect(businessSection).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 15_000 });
  });

  let appUrlBeforeLegal = page.url();
  await runSection("Términos y Condiciones", async () => {
    appUrlBeforeLegal = page.url();
    evidence.terminosUrl = await openLegalDocumentAndReturn({
      page,
      context,
      linkText: "Términos y Condiciones",
      headingText: "Términos y Condiciones",
      screenshotName: "05-terminos-y-condiciones.png",
      appReturnUrl: appUrlBeforeLegal,
      testInfo,
    });
  });

  await runSection("Política de Privacidad", async () => {
    evidence.politicaPrivacidadUrl = await openLegalDocumentAndReturn({
      page,
      context,
      linkText: "Política de Privacidad",
      headingText: "Política de Privacidad",
      screenshotName: "06-politica-de-privacidad.png",
      appReturnUrl: appUrlBeforeLegal,
      testInfo,
    });
  });

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    statusByField: stepStatus,
    evidence,
    failures,
  };

  await testInfo.attach("saleads-mi-negocio-final-report.json", {
    body: Buffer.from(JSON.stringify(finalReport, null, 2), "utf-8"),
    contentType: "application/json",
  });

  console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT");
  console.log(JSON.stringify(finalReport, null, 2));

  expect(
    Object.values(stepStatus).every((status) => status === "PASS"),
    `One or more validations failed. Report: ${JSON.stringify(finalReport)}`
  ).toBeTruthy();
});
