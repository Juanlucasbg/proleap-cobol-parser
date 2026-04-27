const { test, expect } = require("@playwright/test");

const CHECKPOINT_DIR = "artifacts/saleads-mi-negocio-full";
const LEGAL_LINK_TIMEOUT_MS = 25000;

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const results = {
    "Login": "FAIL",
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
    terminosUrl: "",
    politicaUrl: "",
  };

  async function waitForUiIdle() {
    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(800);
  }

  async function snap(name, fullPage = false) {
    await page.screenshot({
      path: `${CHECKPOINT_DIR}/${name}.png`,
      fullPage,
    });
  }

  async function clickElementByVisibleText(label) {
    const roleCandidates = [
      page.getByRole("button", { name: new RegExp(`^${label}$`, "i") }).first(),
      page.getByRole("link", { name: new RegExp(`^${label}$`, "i") }).first(),
      page.getByText(label, { exact: true }).first(),
    ];

    for (const candidate of roleCandidates) {
      if (await candidate.isVisible().catch(() => false)) {
        await candidate.click();
        await waitForUiIdle();
        return;
      }
    }

    throw new Error(`No visible element found with text: ${label}`);
  }

  async function loginWithGoogle() {
    const googleLogin = page
      .getByRole("button", { name: /google|sign in with google|iniciar.*google/i })
      .first();
    await expect(googleLogin).toBeVisible();

    const popupPromise = context
      .waitForEvent("page", { timeout: 8000 })
      .then((p) => p)
      .catch(() => null);

    await googleLogin.click();
    let googlePage = await popupPromise;

    if (googlePage) {
      await googlePage.waitForLoadState("domcontentloaded");
      const accountOnPopup = googlePage
        .getByText("juanlucasbarbiergarzon@gmail.com", { exact: true })
        .first();
      if (await accountOnPopup.isVisible().catch(() => false)) {
        await accountOnPopup.click();
      }
      await googlePage.waitForTimeout(1000);
      await googlePage.close().catch(() => {});
      await page.bringToFront();
    } else {
      const accountChooser = page
        .getByText("juanlucasbarbiergarzon@gmail.com", { exact: true })
        .first();
      if (await accountChooser.isVisible().catch(() => false)) {
        await accountChooser.click();
      }
    }

    await waitForUiIdle();
  }

  async function clickLegalLinkAndReturn(linkText, expectedHeading) {
    const appUrlBefore = page.url();
    const appTab = page;
    const link = page
      .locator("a, button, [role='link'], [role='button']")
      .filter({ hasText: new RegExp(`^${linkText}$`, "i") })
      .first();
    await expect(link, `Expected legal entry "${linkText}" to be visible`).toBeVisible();

    let externalPage = null;
    const maybePopup = context
      .waitForEvent("page", { timeout: LEGAL_LINK_TIMEOUT_MS })
      .then((popup) => popup)
      .catch(() => null);

    await link.click();
    externalPage = await maybePopup;

    if (externalPage) {
      await externalPage.waitForLoadState("domcontentloaded");
      await expect(externalPage.getByText(expectedHeading, { exact: true }).first()).toBeVisible();
      await expect(externalPage.locator("main, article, body")).toContainText(/\S+/);
      await externalPage.screenshot({
        path: `${CHECKPOINT_DIR}/${linkText.replace(/\s+/g, "-").toLowerCase()}.png`,
        fullPage: true,
      });
      const finalUrl = externalPage.url();
      await externalPage.close();
      await page.bringToFront();
      await waitForUiIdle();
      return finalUrl;
    }

    await waitForUiIdle();
    await expect(page.getByText(expectedHeading, { exact: true }).first()).toBeVisible();
    await expect(page.locator("main, article, body")).toContainText(/\S+/);
    await snap(linkText.replace(/\s+/g, "-").toLowerCase(), true);
    const finalUrl = page.url();

    await page.goBack().catch(async () => {
      if (appUrlBefore && appUrlBefore !== finalUrl) {
        await page.goto(appUrlBefore, { waitUntil: "domcontentloaded" });
      }
    });
    await appTab.bringToFront();
    await waitForUiIdle();

    return finalUrl;
  }

  async function markStep(stepName, fn) {
    try {
      await fn();
      results[stepName] = "PASS";
      return true;
    } catch (error) {
      results[stepName] = "FAIL";
      // Keep executing remaining validations so final report always has all fields.
      test.info().annotations.push({
        type: `step-error:${stepName}`,
        description: error instanceof Error ? error.message : String(error),
      });
      return false;
    }
  }

  const loginOk = await markStep("Login", async () => {
    await loginWithGoogle();

    await expect(page.locator("aside")).toBeVisible();
    await snap("01-dashboard-loaded");
  });

  const miNegocioMenuOk = await markStep("Mi Negocio menu", async () => {
    await clickElementByVisibleText("Negocio");
    await clickElementByVisibleText("Mi Negocio");

    await expect(page.getByText("Agregar Negocio", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Administrar Negocios", { exact: true }).first()).toBeVisible();
    await snap("02-mi-negocio-menu-expanded");
  });

  await markStep("Agregar Negocio modal", async () => {
    if (!miNegocioMenuOk) {
      await clickElementByVisibleText("Negocio");
      await clickElementByVisibleText("Mi Negocio");
    }

    await clickElementByVisibleText("Agregar Negocio");
    await expect(page.getByText("Crear Nuevo Negocio", { exact: true }).first()).toBeVisible();
    const nameField = page
      .getByLabel("Nombre del Negocio")
      .or(page.getByPlaceholder("Nombre del Negocio"))
      .first();
    await expect(nameField).toBeVisible();
    await expect(page.getByText("Tienes 2 de 3 negocios", { exact: true }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: "Cancelar" }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: "Crear Negocio" }).first()).toBeVisible();
    await snap("03-agregar-negocio-modal");

    await nameField.click();
    await nameField.fill("Negocio Prueba Automatización");
    await page.getByRole("button", { name: "Cancelar" }).first().click();
    await waitForUiIdle();
  });

  const administrarNegociosOk = await markStep("Administrar Negocios view", async () => {
    if (!loginOk) {
      throw new Error("No se pudo completar login; no es posible validar Administrar Negocios.");
    }

    const administrar = page.getByText("Administrar Negocios", { exact: true }).first();
    if (!(await administrar.isVisible().catch(() => false))) {
      await clickElementByVisibleText("Mi Negocio");
    }
    await administrar.click();
    await waitForUiIdle();

    await expect(page.getByText("Información General", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Detalles de la Cuenta", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Tus Negocios", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Sección Legal", { exact: true }).first()).toBeVisible();
    await snap("04-administrar-negocios", true);
  });

  await markStep("Información General", async () => {
    if (!administrarNegociosOk) {
      throw new Error("No se pudo abrir Administrar Negocios para validar Información General.");
    }

    await expect(page.getByText(/@/, { exact: false }).first()).toBeVisible();
    await expect(page.getByText("BUSINESS PLAN", { exact: true }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: "Cambiar Plan" }).first()).toBeVisible();

    const profileArea = page.getByText(/@/, { exact: false }).first().locator("xpath=ancestor::*[self::section or self::div][1]");
    await expect(profileArea).toContainText(/\S+\s+\S+/);
  });

  await markStep("Detalles de la Cuenta", async () => {
    if (!administrarNegociosOk) {
      throw new Error("No se pudo abrir Administrar Negocios para validar Detalles de la Cuenta.");
    }

    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await markStep("Tus Negocios", async () => {
    if (!administrarNegociosOk) {
      throw new Error("No se pudo abrir Administrar Negocios para validar Tus Negocios.");
    }

    await expect(page.getByText("Tus Negocios", { exact: true }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: "Agregar Negocio" }).first()).toBeVisible();
    await expect(page.getByText("Tienes 2 de 3 negocios", { exact: true }).first()).toBeVisible();
    await expect(page.locator("section, div").filter({ hasText: "Tus Negocios" }).first()).toContainText(/\S+/);
  });

  await markStep("Términos y Condiciones", async () => {
    if (!administrarNegociosOk) {
      throw new Error("No se pudo abrir Administrar Negocios para validar la sección legal.");
    }
    evidence.terminosUrl = await clickLegalLinkAndReturn("Términos y Condiciones", "Términos y Condiciones");
  });

  await markStep("Política de Privacidad", async () => {
    if (!administrarNegociosOk) {
      throw new Error("No se pudo abrir Administrar Negocios para validar la sección legal.");
    }
    evidence.politicaUrl = await clickLegalLinkAndReturn("Política de Privacidad", "Política de Privacidad");
  });

  test.info().annotations.push({
    type: "saleads-mi-negocio-final-report",
    description: JSON.stringify(
      {
        results,
        evidence,
      },
      null,
      2
    ),
  });
  console.log("saleads_mi_negocio_full_test_final_report");
  console.log(
    JSON.stringify(
      {
        results,
        evidence,
      },
      null,
      2
    )
  );

  const failed = Object.entries(results).filter(([, status]) => status === "FAIL");
  expect(
    failed,
    `Final report contains failures: ${JSON.stringify(
      {
        failed,
        evidence,
      },
      null,
      2
    )}`
  ).toEqual([]);
});
