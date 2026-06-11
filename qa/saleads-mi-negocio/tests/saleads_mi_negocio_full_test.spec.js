const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";
const START_URL =
  process.env.SALEADS_START_URL ||
  process.env.SALEADS_LOGIN_URL ||
  process.env.BASE_URL;

const REPORT_FIELDS = [
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

function createDefaultReport() {
  return REPORT_FIELDS.reduce((accumulator, field) => {
    accumulator[field] = "FAIL";
    return accumulator;
  }, {});
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(600);
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible({ timeout: 20000 });
  await locator.click();
  await waitForUiLoad(page);
}

async function firstVisibleLocator(page, candidates, timeoutMs, description) {
  const startedAt = Date.now();

  while (Date.now() - startedAt < timeoutMs) {
    for (const locator of candidates) {
      if ((await locator.count()) > 0 && (await locator.first().isVisible().catch(() => false))) {
        return locator.first();
      }
    }

    await page.waitForTimeout(300);
  }

  throw new Error(`No visible element found for: ${description}`);
}

async function assertAnyVisible(page, candidates, timeoutMs, description) {
  await firstVisibleLocator(page, candidates, timeoutMs, description);
}

async function captureCheckpoint(page, testInfo, fileName, fullPage = false) {
  const screenshotPath = testInfo.outputPath(fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(fileName, {
    path: screenshotPath,
    contentType: "image/png"
  });
}

async function runStep(report, failures, field, callback) {
  try {
    await callback();
    report[field] = "PASS";
  } catch (error) {
    report[field] = "FAIL";
    failures.push(`${field}: ${error.message}`);
  }
}

async function assertUserNameAndEmailVisible(page) {
  const bodyText = await page.locator("body").innerText();
  const emailPattern = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/;
  const foundEmail = bodyText.match(emailPattern);

  if (!foundEmail) {
    throw new Error("No visible user email found.");
  }

  const filteredLines = bodyText
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 0);

  const possibleName = filteredLines.find((line) => {
    const isLikelyName = /[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}/.test(line);
    const isKnownLabel =
      /información general|detalles de la cuenta|business plan|cambiar plan|cuenta creada|estado activo|idioma seleccionado|tus negocios|sección legal/i.test(
        line
      );
    return isLikelyName && !line.includes("@") && !isKnownLabel;
  });

  if (!possibleName) {
    throw new Error("No visible user name found.");
  }
}

async function clickLegalAndValidate({
  page,
  testInfo,
  linkText,
  headingPattern,
  screenshotFileName
}) {
  const linkLocator = await firstVisibleLocator(
    page,
    [
      page.getByRole("link", { name: new RegExp(linkText, "i") }),
      page.getByText(new RegExp(`^${linkText}$`, "i")),
      page.getByText(new RegExp(linkText, "i"))
    ],
    15000,
    linkText
  );

  const context = page.context();
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  await linkLocator.click();
  const popup = await popupPromise;

  let targetPage = page;

  if (popup) {
    targetPage = popup;
  }

  await waitForUiLoad(targetPage);

  await assertAnyVisible(
    targetPage,
    [
      targetPage.getByRole("heading", { name: headingPattern }),
      targetPage.getByText(headingPattern)
    ],
    20000,
    `Heading ${headingPattern}`
  );

  const legalBodyText = await targetPage.locator("body").innerText();
  if (legalBodyText.trim().length < 120) {
    throw new Error(`Legal content is unexpectedly short for ${linkText}.`);
  }

  await captureCheckpoint(targetPage, testInfo, screenshotFileName, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUiLoad(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      if (START_URL) {
        await page.goto(START_URL, { waitUntil: "domcontentloaded" });
      }
    });
    await waitForUiLoad(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = createDefaultReport();
  const failures = [];
  const legalUrls = {
    terminosYCondiciones: "N/A",
    politicaDePrivacidad: "N/A"
  };

  if (START_URL) {
    await page.goto(START_URL, { waitUntil: "domcontentloaded" });
    await waitForUiLoad(page);
  } else if (page.url() === "about:blank") {
    throw new Error(
      "Set SALEADS_START_URL (or SALEADS_LOGIN_URL/BASE_URL) so the test starts on SaleADS login."
    );
  }

  await runStep(report, failures, "Login", async () => {
    const loginTrigger = await firstVisibleLocator(
      page,
      [
        page.getByRole("button", { name: /google|sign in|iniciar sesión|iniciar sesion/i }),
        page.getByRole("link", { name: /google|sign in|iniciar sesión|iniciar sesion/i }),
        page.getByText(/google|sign in|iniciar sesión|iniciar sesion/i)
      ],
      30000,
      "Google login button"
    );

    const popupPromise = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await loginTrigger.click();
    const popup = await popupPromise;
    await waitForUiLoad(page);

    const authPage = popup || page;
    const accountLocator = authPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false });
    if ((await accountLocator.count()) > 0 && (await accountLocator.first().isVisible().catch(() => false))) {
      await accountLocator.first().click();
      await waitForUiLoad(authPage);
    }

    if (popup) {
      await popup.waitForEvent("close", { timeout: 20000 }).catch(() => {});
      await page.bringToFront();
      await waitForUiLoad(page);
    }

    await assertAnyVisible(
      page,
      [page.locator("aside"), page.getByRole("navigation"), page.getByText(/negocio/i)],
      45000,
      "main application interface and sidebar"
    );

    await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png", true);
  });

  await runStep(report, failures, "Mi Negocio menu", async () => {
    const negocioSection = await firstVisibleLocator(
      page,
      [page.getByText(/^Negocio$/i), page.getByRole("link", { name: /^Negocio$/i })],
      20000,
      "Negocio section"
    );

    await clickAndWait(page, negocioSection);

    const miNegocioItem = await firstVisibleLocator(
      page,
      [page.getByText(/^Mi Negocio$/i), page.getByRole("link", { name: /^Mi Negocio$/i })],
      20000,
      "Mi Negocio item"
    );

    await clickAndWait(page, miNegocioItem);

    await assertAnyVisible(
      page,
      [page.getByText(/^Agregar Negocio$/i), page.getByRole("link", { name: /^Agregar Negocio$/i })],
      20000,
      "Agregar Negocio"
    );
    await assertAnyVisible(
      page,
      [
        page.getByText(/^Administrar Negocios$/i),
        page.getByRole("link", { name: /^Administrar Negocios$/i })
      ],
      20000,
      "Administrar Negocios"
    );

    await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png", true);
  });

  await runStep(report, failures, "Agregar Negocio modal", async () => {
    const agregarNegocioMenuItem = await firstVisibleLocator(
      page,
      [page.getByText(/^Agregar Negocio$/i), page.getByRole("link", { name: /^Agregar Negocio$/i })],
      20000,
      "Agregar Negocio menu option"
    );

    await clickAndWait(page, agregarNegocioMenuItem);

    await assertAnyVisible(
      page,
      [page.getByRole("heading", { name: /Crear Nuevo Negocio/i }), page.getByText(/Crear Nuevo Negocio/i)],
      15000,
      "Crear Nuevo Negocio title"
    );

    await assertAnyVisible(
      page,
      [page.getByLabel(/Nombre del Negocio/i), page.getByPlaceholder(/Nombre del Negocio/i)],
      15000,
      "Nombre del Negocio input"
    );
    await assertAnyVisible(
      page,
      [page.getByText(/Tienes 2 de 3 negocios/i)],
      15000,
      "Tienes 2 de 3 negocios"
    );
    await assertAnyVisible(
      page,
      [page.getByRole("button", { name: /^Cancelar$/i }), page.getByText(/^Cancelar$/i)],
      15000,
      "Cancelar button"
    );
    await assertAnyVisible(
      page,
      [page.getByRole("button", { name: /^Crear Negocio$/i }), page.getByText(/^Crear Negocio$/i)],
      15000,
      "Crear Negocio button"
    );

    await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png", true);

    const businessNameInput = await firstVisibleLocator(
      page,
      [page.getByLabel(/Nombre del Negocio/i), page.getByPlaceholder(/Nombre del Negocio/i)],
      5000,
      "Nombre del Negocio optional input"
    );
    await businessNameInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page, page.getByRole("button", { name: /^Cancelar$/i }));
  });

  await runStep(report, failures, "Administrar Negocios view", async () => {
    const administrarNegociosItemLocator = page.getByText(/^Administrar Negocios$/i);
    const isAdministrarVisible =
      (await administrarNegociosItemLocator.count()) > 0 &&
      (await administrarNegociosItemLocator.first().isVisible().catch(() => false));

    if (!isAdministrarVisible) {
      const miNegocioToggle = await firstVisibleLocator(
        page,
        [page.getByText(/^Mi Negocio$/i), page.getByRole("link", { name: /^Mi Negocio$/i })],
        10000,
        "Mi Negocio toggle"
      );
      await clickAndWait(page, miNegocioToggle);
    }

    const administrarNegociosItem = await firstVisibleLocator(
      page,
      [
        page.getByText(/^Administrar Negocios$/i),
        page.getByRole("link", { name: /^Administrar Negocios$/i })
      ],
      15000,
      "Administrar Negocios"
    );

    await clickAndWait(page, administrarNegociosItem);

    await assertAnyVisible(page, [page.getByText(/Información General/i)], 20000, "Información General section");
    await assertAnyVisible(
      page,
      [page.getByText(/Detalles de la Cuenta/i)],
      20000,
      "Detalles de la Cuenta section"
    );
    await assertAnyVisible(page, [page.getByText(/Tus Negocios/i)], 20000, "Tus Negocios section");
    await assertAnyVisible(page, [page.getByText(/Sección Legal/i)], 20000, "Sección Legal section");

    await captureCheckpoint(page, testInfo, "04-administrar-negocios-vista-completa.png", true);
  });

  await runStep(report, failures, "Información General", async () => {
    await assertAnyVisible(page, [page.getByText(/Información General/i)], 15000, "Información General");
    await assertUserNameAndEmailVisible(page);
    await assertAnyVisible(page, [page.getByText(/BUSINESS PLAN/i)], 15000, "BUSINESS PLAN text");
    await assertAnyVisible(
      page,
      [page.getByRole("button", { name: /Cambiar Plan/i }), page.getByText(/Cambiar Plan/i)],
      15000,
      "Cambiar Plan button"
    );
  });

  await runStep(report, failures, "Detalles de la Cuenta", async () => {
    await assertAnyVisible(page, [page.getByText(/Cuenta creada/i)], 15000, "Cuenta creada text");
    await assertAnyVisible(page, [page.getByText(/Estado activo/i)], 15000, "Estado activo text");
    await assertAnyVisible(
      page,
      [page.getByText(/Idioma seleccionado/i)],
      15000,
      "Idioma seleccionado text"
    );
  });

  await runStep(report, failures, "Tus Negocios", async () => {
    await assertAnyVisible(page, [page.getByText(/Tus Negocios/i)], 15000, "Tus Negocios heading");
    await assertAnyVisible(
      page,
      [page.getByRole("button", { name: /^Agregar Negocio$/i }), page.getByText(/^Agregar Negocio$/i)],
      15000,
      "Agregar Negocio button"
    );
    await assertAnyVisible(
      page,
      [page.getByText(/Tienes 2 de 3 negocios/i)],
      15000,
      "Tienes 2 de 3 negocios text"
    );
  });

  await runStep(report, failures, "Términos y Condiciones", async () => {
    legalUrls.terminosYCondiciones = await clickLegalAndValidate({
      page,
      testInfo,
      linkText: "Términos y Condiciones",
      headingPattern: /Términos y Condiciones/i,
      screenshotFileName: "05-terminos-y-condiciones.png"
    });
  });

  await runStep(report, failures, "Política de Privacidad", async () => {
    legalUrls.politicaDePrivacidad = await clickLegalAndValidate({
      page,
      testInfo,
      linkText: "Política de Privacidad",
      headingPattern: /Política de Privacidad/i,
      screenshotFileName: "06-politica-de-privacidad.png"
    });
  });

  const reportPayload = {
    report,
    finalUrls: legalUrls
  };
  const reportPath = testInfo.outputPath("final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(reportPayload, null, 2), "utf8");
  await testInfo.attach("final-report.json", {
    path: reportPath,
    contentType: "application/json"
  });
  console.log(`FINAL_REPORT ${JSON.stringify(reportPayload)}`);

  if (failures.length > 0) {
    throw new Error(`Validation failures:\n- ${failures.join("\n- ")}`);
  }
});
