const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");

const DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

function createStep(name) {
  return {
    name,
    status: "FAIL",
    validations: [],
    errors: [],
    evidence: {},
  };
}

function compactError(error) {
  if (!error) {
    return "Unknown error";
  }

  const message = String(error.message || error);
  return message.length > 250 ? `${message.slice(0, 250)}...` : message;
}

function finalizeStep(step) {
  const hasValidationFailures = step.validations.some((item) => item.result === "FAIL");
  const hasErrors = step.errors.length > 0;
  step.status = hasValidationFailures || hasErrors ? "FAIL" : "PASS";
}

async function waitForUiToSettle(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForTimeout(800);
  await page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => {});
}

async function findFirstVisible(locators) {
  for (const locator of locators) {
    const candidate = locator.first();
    const isVisible = await candidate.isVisible({ timeout: 2500 }).catch(() => false);
    if (isVisible) {
      return candidate;
    }
  }

  return null;
}

async function checkVisible(step, label, locator) {
  try {
    await expect(locator).toBeVisible();
    step.validations.push({ check: label, result: "PASS" });
    return true;
  } catch (error) {
    step.validations.push({
      check: label,
      result: "FAIL",
      details: compactError(error),
    });
    return false;
  }
}

async function clickAndWait(step, label, locator, page) {
  try {
    await locator.scrollIntoViewIfNeeded().catch(() => {});
    await locator.click();
    await waitForUiToSettle(page);
    step.validations.push({ check: `Click '${label}'`, result: "PASS" });
    return true;
  } catch (error) {
    step.errors.push(`Failed clicking '${label}': ${compactError(error)}`);
    return false;
  }
}

async function captureCheckpoint(page, testInfo, fileName, fullPage = false) {
  await page.screenshot({
    path: testInfo.outputPath(fileName),
    fullPage,
  });
}

async function ensureMiNegocioExpanded(page, step) {
  const administrarVisible = await page
    .getByText(/Administrar Negocios/i)
    .first()
    .isVisible({ timeout: 1500 })
    .catch(() => false);

  if (administrarVisible) {
    return true;
  }

  const negocio = await findFirstVisible([
    page.getByRole("button", { name: /Negocio/i }),
    page.getByRole("link", { name: /Negocio/i }),
    page.getByText(/^Negocio$/i),
  ]);

  if (negocio) {
    await clickAndWait(step, "Negocio", negocio, page);
  }

  const miNegocio = await findFirstVisible([
    page.getByRole("button", { name: /Mi Negocio/i }),
    page.getByRole("link", { name: /Mi Negocio/i }),
    page.getByText(/Mi Negocio/i),
  ]);

  if (!miNegocio) {
    step.errors.push("Unable to find 'Mi Negocio' in sidebar.");
    return false;
  }

  await clickAndWait(step, "Mi Negocio", miNegocio, page);
  return true;
}

async function openAndValidateLegalDocument({
  appPage,
  context,
  step,
  linkText,
  expectedHeading,
  checkpointFile,
  testInfo,
}) {
  const link = await findFirstVisible([
    appPage.getByRole("link", { name: new RegExp(linkText, "i") }),
    appPage.getByRole("button", { name: new RegExp(linkText, "i") }),
    appPage.getByText(new RegExp(linkText, "i")),
  ]);

  if (!link) {
    step.errors.push(`Unable to locate legal link: '${linkText}'.`);
    return;
  }

  const oldUrl = appPage.url();
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);

  await clickAndWait(step, linkText, link, appPage);

  let legalPage = await popupPromise;
  if (legalPage) {
    await legalPage.waitForLoadState("domcontentloaded").catch(() => {});
    await waitForUiToSettle(legalPage);
  } else {
    legalPage = appPage;
    await waitForUiToSettle(legalPage);
  }

  await captureCheckpoint(legalPage, testInfo, checkpointFile, true);

  await checkVisible(
    step,
    `Heading '${expectedHeading}' is visible`,
    legalPage.getByRole("heading", { name: new RegExp(expectedHeading, "i") }).first()
  );

  await checkVisible(
    step,
    "Legal content text is visible",
    legalPage.locator("p, li").first()
  );

  step.evidence.finalUrl = legalPage.url();

  if (legalPage !== appPage) {
    await legalPage.close().catch(() => {});
    await appPage.bringToFront().catch(() => {});
    await waitForUiToSettle(appPage);
    return;
  }

  if (appPage.url() !== oldUrl) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      await appPage.goto(oldUrl, { waitUntil: "domcontentloaded" }).catch(() => {});
    });
    await waitForUiToSettle(appPage);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const googleAccount = process.env.GOOGLE_ACCOUNT_EMAIL || DEFAULT_GOOGLE_ACCOUNT;

  const steps = {
    Login: createStep("Login"),
    "Mi Negocio menu": createStep("Mi Negocio menu"),
    "Agregar Negocio modal": createStep("Agregar Negocio modal"),
    "Administrar Negocios view": createStep("Administrar Negocios view"),
    "Información General": createStep("Información General"),
    "Detalles de la Cuenta": createStep("Detalles de la Cuenta"),
    "Tus Negocios": createStep("Tus Negocios"),
    "Términos y Condiciones": createStep("Términos y Condiciones"),
    "Política de Privacidad": createStep("Política de Privacidad"),
  };

  const configuredUrl = process.env.SALEADS_URL || process.env.BASE_URL;
  if (configuredUrl) {
    await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
    await waitForUiToSettle(page);
  } else if (page.url() === "about:blank") {
    throw new Error(
      "No URL is open. Set SALEADS_URL to the current environment login page URL before running the test."
    );
  }

  // Step 1: Login with Google
  {
    const step = steps.Login;

    const loginButton = await findFirstVisible([
      page.getByRole("button", {
        name: /Sign in with Google|Continuar con Google|Iniciar sesión con Google|Google/i,
      }),
      page.getByRole("link", {
        name: /Sign in with Google|Continuar con Google|Iniciar sesión con Google|Google/i,
      }),
      page.getByText(/Sign in with Google|Continuar con Google|Iniciar sesión con Google/i),
    ]);

    if (!loginButton) {
      step.errors.push("Google login button not found.");
    } else {
      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
      await clickAndWait(step, "Sign in with Google", loginButton, page);

      const authPage = (await popupPromise) || page;
      await waitForUiToSettle(authPage);

      const accountOption = await findFirstVisible([
        authPage.getByText(new RegExp(googleAccount, "i")),
        authPage.getByRole("link", { name: new RegExp(googleAccount, "i") }),
        authPage.getByRole("button", { name: new RegExp(googleAccount, "i") }),
      ]);

      if (accountOption) {
        await clickAndWait(step, googleAccount, accountOption, authPage);
      }

      if (authPage !== page) {
        await authPage.waitForClose({ timeout: 20000 }).catch(() => {});
      }
    }

    await waitForUiToSettle(page);
    await checkVisible(step, "Main application interface appears", page.locator("main, [role='main']").first());
    await checkVisible(
      step,
      "Left sidebar navigation is visible",
      page.locator("aside, nav, [class*='sidebar']").first()
    );
    await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png", true);
  }

  // Step 2: Open Mi Negocio menu
  {
    const step = steps["Mi Negocio menu"];
    await ensureMiNegocioExpanded(page, step);

    await checkVisible(step, "'Agregar Negocio' is visible", page.getByText(/Agregar Negocio/i).first());
    await checkVisible(step, "'Administrar Negocios' is visible", page.getByText(/Administrar Negocios/i).first());
    await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png", true);
  }

  // Step 3: Validate Agregar Negocio modal
  {
    const step = steps["Agregar Negocio modal"];
    const agregarNegocio = await findFirstVisible([
      page.getByRole("button", { name: /Agregar Negocio/i }),
      page.getByRole("link", { name: /Agregar Negocio/i }),
      page.getByText(/Agregar Negocio/i),
    ]);

    if (!agregarNegocio) {
      step.errors.push("Unable to find 'Agregar Negocio' option.");
    } else {
      await clickAndWait(step, "Agregar Negocio", agregarNegocio, page);
    }

    const modal = await findFirstVisible([
      page.getByRole("dialog"),
      page.locator("[role='dialog'], [class*='modal'], .modal"),
    ]);

    if (!modal) {
      step.errors.push("Expected modal did not appear.");
    } else {
      await checkVisible(step, "Modal title 'Crear Nuevo Negocio' is visible", modal.getByText(/Crear Nuevo Negocio/i));
      await checkVisible(
        step,
        "Input field 'Nombre del Negocio' exists",
        modal.getByLabel(/Nombre del Negocio/i).or(modal.getByPlaceholder(/Nombre del Negocio/i)).first()
      );
      await checkVisible(step, "Text 'Tienes 2 de 3 negocios' is visible", modal.getByText(/Tienes 2 de 3 negocios/i));
      await checkVisible(step, "Button 'Cancelar' is present", modal.getByRole("button", { name: /Cancelar/i }));
      await checkVisible(step, "Button 'Crear Negocio' is present", modal.getByRole("button", { name: /Crear Negocio/i }));

      const businessNameInput = await findFirstVisible([
        modal.getByLabel(/Nombre del Negocio/i),
        modal.getByPlaceholder(/Nombre del Negocio/i),
      ]);
      if (businessNameInput) {
        await businessNameInput.click();
        await businessNameInput.fill("Negocio Prueba Automatización");
      }

      const cancelButton = await findFirstVisible([modal.getByRole("button", { name: /Cancelar/i })]);
      if (cancelButton) {
        await clickAndWait(step, "Cancelar", cancelButton, page);
      }
    }

    await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png", true);
  }

  // Step 4: Open Administrar Negocios
  {
    const step = steps["Administrar Negocios view"];
    await ensureMiNegocioExpanded(page, step);

    const administrarNegocios = await findFirstVisible([
      page.getByRole("button", { name: /Administrar Negocios/i }),
      page.getByRole("link", { name: /Administrar Negocios/i }),
      page.getByText(/Administrar Negocios/i),
    ]);

    if (!administrarNegocios) {
      step.errors.push("Unable to find 'Administrar Negocios' option.");
    } else {
      await clickAndWait(step, "Administrar Negocios", administrarNegocios, page);
    }

    await checkVisible(step, "Section 'Información General' exists", page.getByText(/Información General/i).first());
    await checkVisible(step, "Section 'Detalles de la Cuenta' exists", page.getByText(/Detalles de la Cuenta/i).first());
    await checkVisible(step, "Section 'Tus Negocios' exists", page.getByText(/Tus Negocios/i).first());
    await checkVisible(step, "Section 'Sección Legal' exists", page.getByText(/Sección Legal/i).first());
    await captureCheckpoint(page, testInfo, "04-administrar-negocios-account-page.png", true);
  }

  // Step 5: Validate Información General
  {
    const step = steps["Información General"];
    const infoSection = page
      .locator("section, div")
      .filter({ has: page.getByText(/Información General/i).first() })
      .first();

    const userNameCandidates = [
      infoSection.getByText(/Nombre/i).first(),
      infoSection.locator("h2,h3,h4,strong,b").first(),
    ];

    await checkVisible(step, "User name is visible", (await findFirstVisible(userNameCandidates)) || userNameCandidates[0]);
    await checkVisible(step, "User email is visible", page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/).first());
    await checkVisible(step, "Text 'BUSINESS PLAN' is visible", page.getByText(/BUSINESS PLAN/i).first());
    await checkVisible(step, "Button 'Cambiar Plan' is visible", page.getByRole("button", { name: /Cambiar Plan/i }).first());
  }

  // Step 6: Validate Detalles de la Cuenta
  {
    const step = steps["Detalles de la Cuenta"];
    await checkVisible(step, "'Cuenta creada' is visible", page.getByText(/Cuenta creada/i).first());
    await checkVisible(step, "'Estado activo' is visible", page.getByText(/Estado activo/i).first());
    await checkVisible(step, "'Idioma seleccionado' is visible", page.getByText(/Idioma seleccionado/i).first());
  }

  // Step 7: Validate Tus Negocios
  {
    const step = steps["Tus Negocios"];
    const tusNegociosSection = page
      .locator("section, div")
      .filter({ has: page.getByText(/Tus Negocios/i).first() })
      .first();

    await checkVisible(
      step,
      "Business list is visible",
      tusNegociosSection.locator("li, tr, [class*='business'], [class*='negocio']").first()
    );
    await checkVisible(step, "Button 'Agregar Negocio' exists", page.getByRole("button", { name: /Agregar Negocio/i }).first());
    await checkVisible(step, "Text 'Tienes 2 de 3 negocios' is visible", page.getByText(/Tienes 2 de 3 negocios/i).first());
  }

  // Step 8: Validate Términos y Condiciones
  await openAndValidateLegalDocument({
    appPage: page,
    context,
    step: steps["Términos y Condiciones"],
    linkText: "Términos y Condiciones",
    expectedHeading: "Términos y Condiciones",
    checkpointFile: "08-terminos-y-condiciones.png",
    testInfo,
  });

  // Step 9: Validate Política de Privacidad
  await openAndValidateLegalDocument({
    appPage: page,
    context,
    step: steps["Política de Privacidad"],
    linkText: "Política de Privacidad",
    expectedHeading: "Política de Privacidad",
    checkpointFile: "09-politica-de-privacidad.png",
    testInfo,
  });

  for (const step of Object.values(steps)) {
    finalizeStep(step);
  }

  const summary = {};
  for (const [field, step] of Object.entries(steps)) {
    summary[field] = step.status;
  }

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    accountUsed: googleAccount,
    results: summary,
    details: steps,
  };

  const reportPath = testInfo.outputPath("saleads_mi_negocio_full_test_report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  // Keep report visible in standard output for CI logs.
  // eslint-disable-next-line no-console
  console.log(JSON.stringify(finalReport, null, 2));

  expect(Object.values(summary).every((status) => status === "PASS")).toBeTruthy();
});
