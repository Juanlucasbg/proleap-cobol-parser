const path = require("path");
const fs = require("fs/promises");
const { test, expect } = require("@playwright/test");

const ACCOUNT_EMAIL =
  process.env.SALEADS_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const LOGIN_URL = process.env.SALEADS_LOGIN_URL;
const ARTIFACTS_ROOT =
  process.env.SALEADS_ARTIFACTS_DIR ||
  path.join(process.cwd(), "artifacts", "saleads-mi-negocio");

function timestampFolder() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

async function waitForUiToLoad(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20000 });
  await page.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => {});
  await page.waitForTimeout(700);
}

async function clickAndWait(locator, page) {
  await locator.click();
  await waitForUiToLoad(page);
}

async function takeCheckpoint(page, artifactsDir, name, fullPage = false) {
  const filePath = path.join(artifactsDir, `${name}.png`);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function findFirstVisible(page, candidates, timeoutMs = 20000) {
  const startedAt = Date.now();

  while (Date.now() - startedAt < timeoutMs) {
    for (const candidate of candidates) {
      const locator = candidate(page).first();
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }
    await page.waitForTimeout(250);
  }

  throw new Error("Could not find a visible element for expected candidates.");
}

async function clickGoogleAccountIfVisible(targetPage) {
  const accountLocator = await findFirstVisible(
    targetPage,
    [
      (p) => p.getByRole("button", { name: new RegExp(ACCOUNT_EMAIL, "i") }),
      (p) => p.getByRole("link", { name: new RegExp(ACCOUNT_EMAIL, "i") }),
      (p) => p.getByText(ACCOUNT_EMAIL, { exact: false })
    ],
    12000
  ).catch(() => null);

  if (!accountLocator) {
    return false;
  }

  await accountLocator.click();
  await waitForUiToLoad(targetPage);
  return true;
}

async function runStep(report, stepName, fn) {
  try {
    await fn();
    report.results[stepName] = "PASS";
  } catch (error) {
    report.results[stepName] = "FAIL";
    report.errors[stepName] = error.message;
  }
}

async function validateLegalDocument({
  page,
  context,
  linkText,
  expectedHeading,
  screenshotName,
  artifactsDir
}) {
  const link = await findFirstVisible(page, [
    (p) => p.getByRole("link", { name: new RegExp(linkText, "i") }),
    (p) => p.getByRole("button", { name: new RegExp(linkText, "i") }),
    (p) => p.getByText(new RegExp(linkText, "i"))
  ]);

  const newPagePromise = context
    .waitForEvent("page", { timeout: 7000 })
    .catch(() => null);

  await link.click();
  await page.waitForTimeout(500);

  let legalPage = await newPagePromise;
  const openedInNewTab = Boolean(legalPage);

  if (!legalPage) {
    legalPage = page;
  }

  await waitForUiToLoad(legalPage);

  await expect(
    legalPage.getByRole("heading", { name: new RegExp(expectedHeading, "i") }).first()
  ).toBeVisible();

  const legalText = (await legalPage.locator("body").innerText()).trim();
  expect(legalText.length).toBeGreaterThan(100);

  const finalUrl = legalPage.url();
  await takeCheckpoint(legalPage, artifactsDir, screenshotName, true);

  if (openedInNewTab) {
    await legalPage.close();
    await page.bringToFront();
  } else {
    await legalPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUiToLoad(legalPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const runFolder = path.join(ARTIFACTS_ROOT, timestampFolder());
  await fs.mkdir(runFolder, { recursive: true });

  const report = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    accountEmail: ACCOUNT_EMAIL,
    loginUrlInput: LOGIN_URL || null,
    results: {
      Login: "NOT_RUN",
      "Mi Negocio menu": "NOT_RUN",
      "Agregar Negocio modal": "NOT_RUN",
      "Administrar Negocios view": "NOT_RUN",
      "Información General": "NOT_RUN",
      "Detalles de la Cuenta": "NOT_RUN",
      "Tus Negocios": "NOT_RUN",
      "Términos y Condiciones": "NOT_RUN",
      "Política de Privacidad": "NOT_RUN"
    },
    legalUrls: {
      "Términos y Condiciones": null,
      "Política de Privacidad": null
    },
    checkpoints: [],
    errors: {}
  };

  if (LOGIN_URL) {
    await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded" });
    await waitForUiToLoad(page);
  } else if (page.url() === "about:blank") {
    await expect
      .poll(() => page.url(), {
        timeout: 120000,
        message:
          "No SALEADS_LOGIN_URL was provided. Navigate to any SaleADS login page to continue."
      })
      .not.toBe("about:blank");
  }

  await runStep(report, "Login", async () => {
    const googleButton = await findFirstVisible(page, [
      (p) =>
        p.getByRole("button", {
          name: /sign in with google|iniciar sesión con google|continuar con google|google/i
        }),
      (p) =>
        p.getByRole("link", {
          name: /sign in with google|iniciar sesión con google|continuar con google|google/i
        }),
      (p) =>
        p.getByText(
          /sign in with google|iniciar sesión con google|continuar con google|google/i
        )
    ]);

    const popupPromise = context
      .waitForEvent("page", { timeout: 8000 })
      .catch(() => null);

    await clickAndWait(googleButton, page);

    const popup = await popupPromise;
    const googlePage = popup || page;

    if (popup) {
      await popup.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
    }

    await clickGoogleAccountIfVisible(googlePage);

    if (popup) {
      await popup.waitForEvent("close", { timeout: 20000 }).catch(() => {});
      await page.bringToFront();
    }

    await waitForUiToLoad(page);
    await expect(page.locator("aside, nav").first()).toBeVisible();
    await expect(page.getByText(/negocio/i).first()).toBeVisible();

    report.checkpoints.push(
      await takeCheckpoint(page, runFolder, "01-dashboard-loaded", true)
    );
  });

  await runStep(report, "Mi Negocio menu", async () => {
    await expect(page.getByText(/negocio/i).first()).toBeVisible();

    const miNegocioOption = await findFirstVisible(page, [
      (p) => p.getByRole("button", { name: /mi negocio/i }),
      (p) => p.getByRole("link", { name: /mi negocio/i }),
      (p) => p.getByText(/mi negocio/i)
    ]);

    await clickAndWait(miNegocioOption, page);

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();

    report.checkpoints.push(await takeCheckpoint(page, runFolder, "02-menu-expanded"));
  });

  await runStep(report, "Agregar Negocio modal", async () => {
    const addBusinessOption = await findFirstVisible(page, [
      (p) => p.getByRole("button", { name: /agregar negocio/i }),
      (p) => p.getByRole("link", { name: /agregar negocio/i }),
      (p) => p.getByText(/agregar negocio/i)
    ]);

    await clickAndWait(addBusinessOption, page);

    await expect(page.getByText(/crear nuevo negocio/i).first()).toBeVisible();
    await expect(page.getByLabel(/nombre del negocio/i)).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    report.checkpoints.push(await takeCheckpoint(page, runFolder, "03-modal"));

    const businessNameInput = page.getByLabel(/nombre del negocio/i);
    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page.getByRole("button", { name: /cancelar/i }), page);

    await expect(page.getByText(/crear nuevo negocio/i).first()).not.toBeVisible();
  });

  await runStep(report, "Administrar Negocios view", async () => {
    const adminOptionVisible = await page
      .getByText(/administrar negocios/i)
      .first()
      .isVisible()
      .catch(() => false);

    if (!adminOptionVisible) {
      const miNegocioOption = await findFirstVisible(page, [
        (p) => p.getByRole("button", { name: /mi negocio/i }),
        (p) => p.getByRole("link", { name: /mi negocio/i }),
        (p) => p.getByText(/mi negocio/i)
      ]);
      await clickAndWait(miNegocioOption, page);
    }

    const manageBusinessesOption = await findFirstVisible(page, [
      (p) => p.getByRole("button", { name: /administrar negocios/i }),
      (p) => p.getByRole("link", { name: /administrar negocios/i }),
      (p) => p.getByText(/administrar negocios/i)
    ]);

    await clickAndWait(manageBusinessesOption, page);

    await expect(page.getByText(/información general/i).first()).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByText(/sección legal/i).first()).toBeVisible();

    report.checkpoints.push(
      await takeCheckpoint(page, runFolder, "04-account-page-full", true)
    );
  });

  await runStep(report, "Información General", async () => {
    await expect(page.getByText(/business plan/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();

    const userNameOrEmail = await page
      .locator("body")
      .innerText()
      .then((text) => text.includes("@") && text.trim().length > 0);
    expect(userNameOrEmail).toBeTruthy();
  });

  await runStep(report, "Detalles de la Cuenta", async () => {
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep(report, "Tus Negocios", async () => {
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();

    const addBusinessButton = await findFirstVisible(page, [
      (p) => p.getByRole("button", { name: /agregar negocio/i }),
      (p) => p.getByRole("link", { name: /agregar negocio/i }),
      (p) => p.getByText(/agregar negocio/i)
    ]);
    await expect(addBusinessButton).toBeVisible();
  });

  await runStep(report, "Términos y Condiciones", async () => {
    report.legalUrls["Términos y Condiciones"] = await validateLegalDocument({
      page,
      context,
      linkText: "Términos y Condiciones",
      expectedHeading: "Términos y Condiciones",
      screenshotName: "05-terminos-y-condiciones",
      artifactsDir: runFolder
    });
  });

  await runStep(report, "Política de Privacidad", async () => {
    report.legalUrls["Política de Privacidad"] = await validateLegalDocument({
      page,
      context,
      linkText: "Política de Privacidad",
      expectedHeading: "Política de Privacidad",
      screenshotName: "06-politica-de-privacidad",
      artifactsDir: runFolder
    });
  });

  const reportPath = path.join(runFolder, "final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");

  // Fail the test if any mandatory validation step failed.
  const failedSteps = Object.entries(report.results)
    .filter(([, status]) => status !== "PASS")
    .map(([name]) => name);

  expect(
    failedSteps,
    `Some validation steps failed. See report at ${reportPath}`
  ).toEqual([]);
});
