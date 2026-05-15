const fs = require("fs/promises");
const { test, expect } = require("@playwright/test");

const STEP_LABELS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad"
];

function createStepTracker() {
  return STEP_LABELS.reduce((accumulator, label) => {
    accumulator[label] = {
      status: "FAIL",
      details: "Not executed"
    };
    return accumulator;
  }, {});
}

async function waitForUiToLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
}

async function clickAndWait(locator, page) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiToLoad(page);
}

async function firstVisible(candidates, errorMessage) {
  for (const candidate of candidates) {
    const locator = candidate.first();
    if (await locator.isVisible().catch(() => false)) {
      return locator;
    }
  }
  throw new Error(errorMessage);
}

async function captureCheckpoint(page, testInfo, name, options = {}) {
  const screenshotPath = testInfo.outputPath(name);
  await page.screenshot({ path: screenshotPath, ...options });
  await testInfo.attach(name, {
    path: screenshotPath,
    contentType: "image/png"
  });
}

async function runStep(stepTracker, stepLabel, action, page, testInfo) {
  try {
    await action();
    stepTracker[stepLabel] = {
      status: "PASS",
      details: "Validation passed."
    };
  } catch (error) {
    stepTracker[stepLabel] = {
      status: "FAIL",
      details: error instanceof Error ? error.message : String(error)
    };
    await captureCheckpoint(
      page,
      testInfo,
      `${stepLabel.toLowerCase().replace(/[^a-z0-9]+/gi, "-")}-failed.png`,
      { fullPage: true }
    ).catch(() => {});
  }
}

async function openLegalPageAndValidate({
  page,
  linkText,
  headingText,
  screenshotName,
  testInfo
}) {
  const legalLink = await firstVisible(
    [
      page.getByRole("link", { name: new RegExp(`^${linkText}$`, "i") }),
      page.getByRole("button", { name: new RegExp(`^${linkText}$`, "i") }),
      page.getByText(new RegExp(`^${linkText}$`, "i"))
    ],
    `No se encontró el enlace/botón legal "${linkText}".`
  );

  const popupPromise = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);
  await legalLink.click();
  await waitForUiToLoad(page);

  const popupPage = await popupPromise;
  const targetPage = popupPage || page;
  await targetPage.waitForLoadState("domcontentloaded");
  await waitForUiToLoad(targetPage);

  const headingLocator = await firstVisible(
    [
      targetPage.getByRole("heading", { name: new RegExp(headingText, "i") }),
      targetPage.getByText(new RegExp(headingText, "i"))
    ],
    `No se encontró el heading "${headingText}".`
  );
  await expect(headingLocator).toBeVisible();

  const visibleContent = await targetPage.locator("body").innerText();
  expect(visibleContent.trim().length).toBeGreaterThan(120);

  await captureCheckpoint(targetPage, testInfo, screenshotName, { fullPage: true });
  const finalUrl = targetPage.url();

  if (popupPage) {
    await popupPage.close();
    await page.bringToFront();
    await waitForUiToLoad(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUiToLoad(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const stepTracker = createStepTracker();
  const legalUrls = {
    terminosYCondiciones: null,
    politicaDePrivacidad: null
  };

  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL;
  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  }

  await waitForUiToLoad(page);
  if (page.url() === "about:blank") {
    throw new Error(
      "No hay URL cargada. Define SALEADS_LOGIN_URL con la página de login del entorno actual."
    );
  }

  await runStep(
    stepTracker,
    "Login",
    async () => {
      const loginButton = await firstVisible(
        [
          page.getByRole("button", { name: /google|sign in|iniciar sesi[oó]n/i }),
          page.getByRole("link", { name: /google|sign in|iniciar sesi[oó]n/i }),
          page.getByText(/Sign in with Google|Iniciar sesi[oó]n con Google|Google/i)
        ],
        "No se encontró el botón de login con Google."
      );

      const popupPromise = page.context().waitForEvent("page", { timeout: 12000 }).catch(() => null);
      await loginButton.click();
      await waitForUiToLoad(page);

      const googlePopup = await popupPromise;
      if (googlePopup) {
        await googlePopup.waitForLoadState("domcontentloaded");
        const accountOption = googlePopup.getByText("juanlucasbarbiergarzon@gmail.com").first();
        if (await accountOption.isVisible().catch(() => false)) {
          await accountOption.click();
          await waitForUiToLoad(googlePopup);
        }
        await googlePopup.waitForEvent("close", { timeout: 20000 }).catch(() => {});
        await page.bringToFront();
      } else if (page.url().includes("accounts.google.com")) {
        const accountOption = page.getByText("juanlucasbarbiergarzon@gmail.com").first();
        if (await accountOption.isVisible().catch(() => false)) {
          await clickAndWait(accountOption, page);
        }
      }

      await waitForUiToLoad(page);
      await expect(page.locator("main, [role='main']").first()).toBeVisible();
      await expect(page.locator("aside, nav, [role='navigation']").first()).toBeVisible();
      await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png", { fullPage: true });
    },
    page,
    testInfo
  );

  await runStep(
    stepTracker,
    "Mi Negocio menu",
    async () => {
      const negocioSection = await firstVisible(
        [
          page.getByRole("button", { name: /^Negocio$/i }),
          page.getByRole("link", { name: /^Negocio$/i }),
          page.getByText(/^Negocio$/i)
        ],
        "No se encontró la sección Negocio en el sidebar."
      );
      await clickAndWait(negocioSection, page);

      const miNegocioOption = await firstVisible(
        [
          page.getByRole("button", { name: /^Mi Negocio$/i }),
          page.getByRole("link", { name: /^Mi Negocio$/i }),
          page.getByText(/^Mi Negocio$/i)
        ],
        "No se encontró la opción Mi Negocio."
      );
      await clickAndWait(miNegocioOption, page);

      await expect(page.getByText("Agregar Negocio", { exact: true })).toBeVisible();
      await expect(page.getByText("Administrar Negocios", { exact: true })).toBeVisible();
      await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png");
    },
    page,
    testInfo
  );

  await runStep(
    stepTracker,
    "Agregar Negocio modal",
    async () => {
      const agregarNegocioOption = await firstVisible(
        [
          page.getByRole("menuitem", { name: /^Agregar Negocio$/i }),
          page.getByRole("button", { name: /^Agregar Negocio$/i }),
          page.getByRole("link", { name: /^Agregar Negocio$/i }),
          page.getByText(/^Agregar Negocio$/i)
        ],
        "No se encontró el acceso Agregar Negocio."
      );
      await clickAndWait(agregarNegocioOption, page);

      const modal = page.getByRole("dialog").first();
      await expect(modal).toBeVisible();
      await expect(modal.getByText("Crear Nuevo Negocio", { exact: true })).toBeVisible();
      await expect(modal.getByText("Nombre del Negocio", { exact: true })).toBeVisible();
      await expect(modal.getByText("Tienes 2 de 3 negocios", { exact: true })).toBeVisible();
      await expect(modal.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
      await expect(modal.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible();

      const businessNameInput = await firstVisible(
        [
          modal.getByLabel("Nombre del Negocio"),
          modal.getByPlaceholder("Nombre del Negocio"),
          modal.locator("input[type='text']")
        ],
        "No se encontró el input Nombre del Negocio."
      );
      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatización");
      await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");

      const cancelButton = modal.getByRole("button", { name: /^Cancelar$/i });
      await clickAndWait(cancelButton, page);
      await expect(modal).toBeHidden();
    },
    page,
    testInfo
  );

  await runStep(
    stepTracker,
    "Administrar Negocios view",
    async () => {
      const miNegocioOption = await firstVisible(
        [
          page.getByRole("button", { name: /^Mi Negocio$/i }),
          page.getByRole("link", { name: /^Mi Negocio$/i }),
          page.getByText(/^Mi Negocio$/i)
        ],
        "No se encontró Mi Negocio para volver a expandir el menú."
      );
      await clickAndWait(miNegocioOption, page);

      const administrarNegociosOption = await firstVisible(
        [
          page.getByRole("menuitem", { name: /^Administrar Negocios$/i }),
          page.getByRole("button", { name: /^Administrar Negocios$/i }),
          page.getByRole("link", { name: /^Administrar Negocios$/i }),
          page.getByText(/^Administrar Negocios$/i)
        ],
        "No se encontró la opción Administrar Negocios."
      );
      await clickAndWait(administrarNegociosOption, page);

      await expect(page.getByText("Información General", { exact: true })).toBeVisible();
      await expect(page.getByText("Detalles de la Cuenta", { exact: true })).toBeVisible();
      await expect(page.getByText("Tus Negocios", { exact: true })).toBeVisible();
      await expect(page.getByText("Sección Legal", { exact: true })).toBeVisible();
      await captureCheckpoint(page, testInfo, "04-administrar-negocios-full-page.png", {
        fullPage: true
      });
    },
    page,
    testInfo
  );

  await runStep(
    stepTracker,
    "Información General",
    async () => {
      const infoGeneralSection = page
        .locator("section, div")
        .filter({ has: page.getByText("Información General", { exact: true }) })
        .first();
      await expect(infoGeneralSection).toBeVisible();

      const emailLocator = await firstVisible(
        [page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/)],
        "No se encontró el email del usuario."
      );
      await expect(emailLocator).toBeVisible();

      const userNameLocator = await firstVisible(
        [page.getByText(/Nombre|Usuario|User/i)],
        "No se encontró un identificador de nombre de usuario."
      );
      await expect(userNameLocator).toBeVisible();

      await expect(page.getByText("BUSINESS PLAN", { exact: true })).toBeVisible();
      await expect(page.getByRole("button", { name: /^Cambiar Plan$/i })).toBeVisible();
    },
    page,
    testInfo
  );

  await runStep(
    stepTracker,
    "Detalles de la Cuenta",
    async () => {
      await expect(page.getByText("Detalles de la Cuenta", { exact: true })).toBeVisible();
      await expect(page.getByText("Cuenta creada", { exact: true })).toBeVisible();
      await expect(page.getByText("Estado activo", { exact: true })).toBeVisible();
      await expect(page.getByText("Idioma seleccionado", { exact: true })).toBeVisible();
    },
    page,
    testInfo
  );

  await runStep(
    stepTracker,
    "Tus Negocios",
    async () => {
      await expect(page.getByText("Tus Negocios", { exact: true })).toBeVisible();

      const addBusinessButton = await firstVisible(
        [
          page.getByRole("button", { name: /^Agregar Negocio$/i }),
          page.getByRole("link", { name: /^Agregar Negocio$/i }),
          page.getByText(/^Agregar Negocio$/i)
        ],
        "No se encontró el botón Agregar Negocio en Tus Negocios."
      );
      await expect(addBusinessButton).toBeVisible();

      await expect(page.getByText("Tienes 2 de 3 negocios", { exact: true })).toBeVisible();
      await expect(page.locator("ul, table, [role='list'], [role='table']").first()).toBeVisible();
    },
    page,
    testInfo
  );

  await runStep(
    stepTracker,
    "Términos y Condiciones",
    async () => {
      legalUrls.terminosYCondiciones = await openLegalPageAndValidate({
        page,
        linkText: "Términos y Condiciones",
        headingText: "Términos y Condiciones",
        screenshotName: "05-terminos-y-condiciones.png",
        testInfo
      });
    },
    page,
    testInfo
  );

  await runStep(
    stepTracker,
    "Política de Privacidad",
    async () => {
      legalUrls.politicaDePrivacidad = await openLegalPageAndValidate({
        page,
        linkText: "Política de Privacidad",
        headingText: "Política de Privacidad",
        screenshotName: "06-politica-de-privacidad.png",
        testInfo
      });
    },
    page,
    testInfo
  );

  const report = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    steps: stepTracker,
    legalUrls
  };

  const reportPath = testInfo.outputPath("saleads_mi_negocio_full_report.json");
  await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");
  await testInfo.attach("saleads-mi-negocio-full-report", {
    path: reportPath,
    contentType: "application/json"
  });

  console.table(
    Object.entries(stepTracker).map(([name, result]) => ({
      step: name,
      status: result.status,
      details: result.details
    }))
  );
  console.log("Términos y Condiciones URL:", legalUrls.terminosYCondiciones);
  console.log("Política de Privacidad URL:", legalUrls.politicaDePrivacidad);

  const failedSteps = Object.entries(stepTracker)
    .filter(([, result]) => result.status !== "PASS")
    .map(([name, result]) => `${name}: ${result.details}`);

  expect(
    failedSteps,
    `Hubo validaciones fallidas:\n${failedSteps.join("\n") || "(sin detalle)"}`
  ).toEqual([]);
});
