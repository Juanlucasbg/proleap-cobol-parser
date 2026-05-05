const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const TARGET_URL =
  process.env.SALEADS_URL || process.env.BASE_URL || process.env.APP_URL || "";
const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";
const SCREENSHOT_DIR =
  process.env.SALEADS_SCREENSHOT_DIR || "test-results/saleads-mi-negocio";

function ensureScreenshotDir() {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(700);
}

async function firstVisible(locators, timeout = 10000) {
  for (const locator of locators) {
    if (!locator) {
      continue;
    }
    const candidate = locator.first();
    try {
      await candidate.waitFor({ state: "visible", timeout });
      return candidate;
    } catch (_error) {
      // try next locator
    }
  }
  return null;
}

async function clickVisible(locators, page, description) {
  const target = await firstVisible(locators);
  expect(target, `Unable to locate visible element for: ${description}`).not.toBeNull();
  await target.click();
  await waitForUi(page);
}

async function capture(page, name, fullPage = false) {
  ensureScreenshotDir();
  const filePath = path.join(SCREENSHOT_DIR, `${name}.png`);
  await page.screenshot({ path: filePath, fullPage });
}

async function runStep(report, fieldName, fn) {
  try {
    await fn();
    report[fieldName] = "PASS";
  } catch (error) {
    const reason = error instanceof Error ? error.message.split("\n")[0] : String(error);
    report[fieldName] = `FAIL - ${reason}`;
  }
}

async function clickLegalLinkAndValidate({
  page,
  context,
  linkTextRegex,
  headingRegex,
  screenshotName
}) {
  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await clickVisible(
    [
      page.getByRole("link", { name: linkTextRegex }),
      page.getByRole("button", { name: linkTextRegex }),
      page.getByText(linkTextRegex)
    ],
    page,
    `legal link ${linkTextRegex}`
  );

  const popup = await popupPromise;
  const legalPage = popup || page;

  await legalPage.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
  await legalPage.waitForTimeout(700);

  const heading = await firstVisible(
    [legalPage.getByRole("heading", { name: headingRegex }), legalPage.getByText(headingRegex)],
    15000
  );
  expect(heading, `Could not find legal heading matching ${headingRegex}`).not.toBeNull();

  const bodyText = await legalPage.locator("body").innerText();
  expect(bodyText.trim().length, "Legal content appears empty.").toBeGreaterThan(80);

  await capture(legalPage, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  if (TARGET_URL) {
    await page.goto(TARGET_URL, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else if (page.url() === "about:blank") {
    throw new Error(
      "No SALEADS_URL/BASE_URL/APP_URL provided and current page is about:blank. Set an env URL or start from the SaleADS login page."
    );
  }

  const report = {
    Login: "NOT_RUN",
    "Mi Negocio menu": "NOT_RUN",
    "Agregar Negocio modal": "NOT_RUN",
    "Administrar Negocios view": "NOT_RUN",
    "Información General": "NOT_RUN",
    "Detalles de la Cuenta": "NOT_RUN",
    "Tus Negocios": "NOT_RUN",
    "Términos y Condiciones": "NOT_RUN",
    "Política de Privacidad": "NOT_RUN"
  };

  const evidence = {
    terminosUrl: "",
    privacidadUrl: ""
  };

  await runStep(report, "Login", async () => {
    const sidebarAlreadyVisible = await firstVisible(
      [
        page.locator("aside"),
        page.getByRole("navigation").filter({ hasText: /Negocio|Mi Negocio/i }),
        page.getByText(/Mi Negocio/i)
      ],
      5000
    );

    if (!sidebarAlreadyVisible) {
      const googlePopupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);

      await clickVisible(
        [
          page.getByRole("button", {
            name: /sign in with google|iniciar sesion con google|iniciar sesion|google/i
          }),
          page.getByRole("link", {
            name: /sign in with google|iniciar sesion con google|iniciar sesion|google/i
          }),
          page.getByText(/sign in with google|iniciar sesion con google|google/i)
        ],
        page,
        "Google login action"
      );

      const accountRegex = new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i");
      const googlePopup = await googlePopupPromise;
      const accountPickerPage = googlePopup || page;
      await accountPickerPage
        .waitForLoadState("domcontentloaded", { timeout: 20000 })
        .catch(() => {});

      const accountOption = await firstVisible(
        [
          accountPickerPage.getByText(accountRegex),
          accountPickerPage.getByRole("button", { name: accountRegex }),
          accountPickerPage.getByRole("link", { name: accountRegex })
        ],
        12000
      );

      if (accountOption) {
        await accountOption.click();
        await waitForUi(accountPickerPage);
      }

      if (googlePopup) {
        await page.bringToFront();
      }
    }

    const mainApp = await firstVisible(
      [
        page.getByRole("navigation").filter({ hasText: /Negocio|Mi Negocio/i }),
        page.locator("aside"),
        page.getByText(/Mi Negocio/i)
      ],
      30000
    );
    expect(mainApp, "Main app UI did not appear after login.").not.toBeNull();

    const sidebar = await firstVisible([page.locator("aside"), page.getByRole("navigation")], 10000);
    expect(sidebar, "Left sidebar navigation is not visible.").not.toBeNull();

    await capture(page, "01-dashboard-loaded", true);
  });

  await runStep(report, "Mi Negocio menu", async () => {
    await expect(page.getByText(/Negocio/i).first()).toBeVisible({ timeout: 15000 });

    await clickVisible(
      [
        page.getByRole("button", { name: /Mi Negocio/i }),
        page.getByRole("link", { name: /Mi Negocio/i }),
        page.getByText(/^Mi Negocio$/i)
      ],
      page,
      "Mi Negocio sidebar option"
    );

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 10000 });

    await capture(page, "02-mi-negocio-menu-expanded", true);
  });

  await runStep(report, "Agregar Negocio modal", async () => {
    await clickVisible(
      [
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i)
      ],
      page,
      "Agregar Negocio action"
    );

    const dialog = await firstVisible(
      [page.getByRole("dialog"), page.locator('[role="dialog"]'), page.locator(".modal").first()],
      12000
    );
    expect(dialog, "Expected Agregar Negocio modal did not appear.").not.toBeNull();

    await expect(dialog.getByText(/Crear Nuevo Negocio/i)).toBeVisible({ timeout: 8000 });

    const nombreField = await firstVisible(
      [
        dialog.getByLabel(/Nombre del Negocio/i),
        dialog.getByPlaceholder(/Nombre del Negocio/i),
        dialog.locator("input").first()
      ],
      8000
    );
    expect(nombreField, "Nombre del Negocio input was not found.").not.toBeNull();

    await expect(dialog.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible({ timeout: 8000 });
    await expect(dialog.getByRole("button", { name: /Cancelar/i })).toBeVisible({ timeout: 8000 });
    await expect(dialog.getByRole("button", { name: /Crear Negocio/i })).toBeVisible({ timeout: 8000 });

    await nombreField.fill("Negocio Prueba Automatizacion");
    await waitForUi(page);
    await capture(page, "03-agregar-negocio-modal");
    await dialog.getByRole("button", { name: /Cancelar/i }).click();
    await waitForUi(page);
  });

  await runStep(report, "Administrar Negocios view", async () => {
    const administrarVisible = await page
      .getByText(/Administrar Negocios/i)
      .first()
      .isVisible()
      .catch(() => false);
    if (!administrarVisible) {
      await clickVisible(
        [
          page.getByRole("button", { name: /Mi Negocio/i }),
          page.getByRole("link", { name: /Mi Negocio/i }),
          page.getByText(/^Mi Negocio$/i)
        ],
        page,
        "Expand Mi Negocio again"
      );
    }

    await clickVisible(
      [
        page.getByRole("button", { name: /Administrar Negocios/i }),
        page.getByRole("link", { name: /Administrar Negocios/i }),
        page.getByText(/Administrar Negocios/i)
      ],
      page,
      "Administrar Negocios option"
    );

    await expect(page.getByText(/Informacion General|Informaci.n General/i).first()).toBeVisible({
      timeout: 20000
    });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Seccion Legal|Secci.n Legal/i).first()).toBeVisible({
      timeout: 20000
    });

    await capture(page, "04-administrar-negocios-view", true);
  });

  await runStep(report, "Información General", async () => {
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible({
      timeout: 10000
    });

    const emailLocator = page.locator(
      'text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/'
    );
    await expect(emailLocator.first()).toBeVisible({ timeout: 10000 });

    const infoText = await page.locator("body").innerText();
    const lines = infoText
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean);
    const hasLikelyName = lines.some(
      (line) => line.length >= 4 && !line.includes("@") && /^[A-Za-z][A-Za-z\s.'-]+$/.test(line)
    );
    expect(hasLikelyName, "Could not identify a likely user name in Informacion General.").toBeTruthy();
  });

  await runStep(report, "Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 10000 });
  });

  await runStep(report, "Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible({
      timeout: 10000
    });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 10000 });
  });

  await runStep(report, "Términos y Condiciones", async () => {
    evidence.terminosUrl = await clickLegalLinkAndValidate({
      page,
      context,
      linkTextRegex: /Terminos y Condiciones|T.rminos y Condiciones/i,
      headingRegex: /Terminos y Condiciones|T.rminos y Condiciones/i,
      screenshotName: "05-terminos-y-condiciones"
    });
  });

  await runStep(report, "Política de Privacidad", async () => {
    evidence.privacidadUrl = await clickLegalLinkAndValidate({
      page,
      context,
      linkTextRegex: /Politica de Privacidad|Pol.tica de Privacidad/i,
      headingRegex: /Politica de Privacidad|Pol.tica de Privacidad/i,
      screenshotName: "06-politica-de-privacidad"
    });
  });

  const finalPayload = {
    test: "saleads_mi_negocio_full_test",
    report,
    evidence
  };

  // Step 10: final report with PASS / FAIL per requested validation field.
  console.log("=== Final Report: Mi Negocio Workflow ===");
  console.table(report);
  console.log(`Terminos y Condiciones URL: ${evidence.terminosUrl || "N/A"}`);
  console.log(`Politica de Privacidad URL: ${evidence.privacidadUrl || "N/A"}`);

  await test
    .info()
    .attach("saleads-mi-negocio-final-report", {
      body: Buffer.from(JSON.stringify(finalPayload, null, 2)),
      contentType: "application/json"
    });

  for (const [stepName, outcome] of Object.entries(report)) {
    expect(
      outcome.startsWith("PASS"),
      `Validation failed for "${stepName}". Recorded status: ${outcome}`
    ).toBeTruthy();
  }
});
