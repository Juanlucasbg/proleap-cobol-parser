const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";

const REPORT_FIELDS = [
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

async function waitForUi(page) {
  await page.waitForTimeout(800);
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 7000 }).catch(() => {});
}

async function findVisible(candidates) {
  for (const locator of candidates) {
    const first = locator.first();
    if (await first.isVisible().catch(() => false)) {
      return first;
    }
  }

  return null;
}

async function clickAndWait(locator, page) {
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click({ timeout: 15000 });
  await waitForUi(page);
}

async function getSidebar(page) {
  return findVisible([
    page.locator("aside"),
    page.getByRole("navigation"),
    page.locator('[class*="sidebar"]'),
  ]);
}

async function selectGoogleAccountIfPrompted(targetPage, email) {
  const accountOption = await findVisible([
    targetPage.getByText(email, { exact: true }),
    targetPage.getByRole("link", { name: email }),
    targetPage.getByRole("button", { name: email }),
  ]);

  if (accountOption) {
    await clickAndWait(accountOption, targetPage);
  }
}

async function openLegalDocument({
  appPage,
  linkText,
  expectedHeadingRegex,
  screenshotName,
  testInfo,
}) {
  const link = await findVisible([
    appPage.getByRole("link", { name: linkText }),
    appPage.getByText(linkText, { exact: true }),
  ]);

  if (!link) {
    throw new Error(`No se encontró el enlace legal "${linkText.source}".`);
  }

  const popupPromise = appPage
    .waitForEvent("popup", { timeout: 8000 })
    .catch(() => null);
  const navigationPromise = appPage
    .waitForNavigation({ waitUntil: "domcontentloaded", timeout: 10000 })
    .catch(() => null);

  await link.click();

  const popup = await popupPromise;
  let targetPage = appPage;
  let openedInNewTab = false;

  if (popup) {
    targetPage = popup;
    openedInNewTab = true;
    await targetPage.waitForLoadState("domcontentloaded");
    await waitForUi(targetPage);
  } else {
    await navigationPromise;
    await waitForUi(appPage);
  }

  await expect(targetPage.getByRole("heading", { name: expectedHeadingRegex }))
    .toBeVisible({ timeout: 15000 })
    .catch(async () => {
      await expect(targetPage.getByText(expectedHeadingRegex)).toBeVisible({
        timeout: 15000,
      });
    });

  const bodyText = await targetPage.locator("body").innerText();
  if (bodyText.trim().length < 150) {
    throw new Error(`El contenido legal para "${linkText.source}" es insuficiente.`);
  }

  await targetPage.screenshot({
    path: testInfo.outputPath(screenshotName),
    fullPage: true,
  });

  const finalUrl = targetPage.url();

  if (openedInNewTab) {
    await targetPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const loginUrl = process.env.SALEADS_URL || process.env.SALEADS_LOGIN_URL;
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const evidence = {};
  const failures = [];

  const runStep = async (label, stepFn) => {
    try {
      await stepFn();
      report[label] = "PASS";
    } catch (error) {
      failures.push(`${label}: ${error.message}`);
    }
  };

  await runStep("Login", async () => {
    if (!loginUrl) {
      throw new Error(
        "Falta SALEADS_URL o SALEADS_LOGIN_URL. La prueba no usa dominio fijo y requiere URL por variable de entorno."
      );
    }

    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    const loginButton = await findVisible([
      page.getByRole("button", { name: /google|sign in|iniciar sesi[oó]n/i }),
      page.getByText(/google|sign in|iniciar sesi[oó]n/i),
    ]);

    if (!loginButton) {
      throw new Error("No se encontró el botón de login con Google.");
    }

    const popupPromise = page.waitForEvent("popup", { timeout: 8000 }).catch(() => null);
    await clickAndWait(loginButton, page);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await waitForUi(popup);
      await selectGoogleAccountIfPrompted(popup, GOOGLE_ACCOUNT_EMAIL);
    } else {
      await selectGoogleAccountIfPrompted(page, GOOGLE_ACCOUNT_EMAIL);
    }

    const sidebar = await getSidebar(page);
    if (!sidebar) {
      throw new Error("No se encontró la barra lateral tras el login.");
    }

    await expect(sidebar).toBeVisible({ timeout: 20000 });
    await page.screenshot({
      path: testInfo.outputPath("01-dashboard-loaded.png"),
      fullPage: true,
    });
    evidence.dashboardScreenshot = "01-dashboard-loaded.png";
  });

  await runStep("Mi Negocio menu", async () => {
    const sidebar = await getSidebar(page);
    if (!sidebar) {
      throw new Error("No se encontró la navegación lateral.");
    }

    const negocioSection = await findVisible([
      sidebar.getByRole("button", { name: /negocio/i }),
      sidebar.getByRole("link", { name: /negocio/i }),
      sidebar.getByText(/^Negocio$/i),
      page.getByText(/^Negocio$/i),
    ]);
    if (!negocioSection) {
      throw new Error('No se encontró la sección "Negocio".');
    }
    await clickAndWait(negocioSection, page);

    const miNegocioOption = await findVisible([
      sidebar.getByRole("button", { name: /mi negocio/i }),
      sidebar.getByRole("link", { name: /mi negocio/i }),
      sidebar.getByText(/mi negocio/i),
      page.getByText(/mi negocio/i),
    ]);
    if (!miNegocioOption) {
      throw new Error('No se encontró la opción "Mi Negocio".');
    }
    await clickAndWait(miNegocioOption, page);

    await expect(page.getByText(/Agregar Negocio/i)).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/Administrar Negocios/i)).toBeVisible({
      timeout: 10000,
    });

    await page.screenshot({
      path: testInfo.outputPath("02-mi-negocio-expanded-menu.png"),
      fullPage: true,
    });
    evidence.miNegocioMenuScreenshot = "02-mi-negocio-expanded-menu.png";
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocioOption = await findVisible([
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);
    if (!agregarNegocioOption) {
      throw new Error('No se encontró "Agregar Negocio".');
    }
    await clickAndWait(agregarNegocioOption, page);

    const modal = await findVisible([
      page.getByRole("dialog").filter({ hasText: /Crear Nuevo Negocio/i }),
      page.locator('[role="dialog"]').filter({ hasText: /Crear Nuevo Negocio/i }),
    ]);
    if (!modal) {
      throw new Error("No se abrió el modal Crear Nuevo Negocio.");
    }

    await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
    await expect(
      modal
        .getByLabel(/Nombre del Negocio/i)
        .or(modal.getByPlaceholder(/Nombre del Negocio/i))
    ).toBeVisible();
    await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

    const businessNameField =
      (await findVisible([
        modal.getByLabel(/Nombre del Negocio/i),
        modal.getByPlaceholder(/Nombre del Negocio/i),
      ])) || modal.locator("input").first();

    await businessNameField.click();
    await businessNameField.fill("Negocio Prueba Automatización");

    await page.screenshot({
      path: testInfo.outputPath("03-agregar-negocio-modal.png"),
      fullPage: true,
    });
    evidence.agregarNegocioModalScreenshot = "03-agregar-negocio-modal.png";

    await clickAndWait(modal.getByRole("button", { name: /Cancelar/i }), page);
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarNegociosOption = await findVisible([
      page.getByRole("button", { name: /Administrar Negocios/i }),
      page.getByRole("link", { name: /Administrar Negocios/i }),
      page.getByText(/Administrar Negocios/i),
    ]);

    if (!administrarNegociosOption) {
      const miNegocioOption = await findVisible([
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i),
      ]);
      if (!miNegocioOption) {
        throw new Error('No se encontró "Mi Negocio" para reexpandir el menú.');
      }
      await clickAndWait(miNegocioOption, page);
    }

    const administrarNegocios = await findVisible([
      page.getByRole("button", { name: /Administrar Negocios/i }),
      page.getByRole("link", { name: /Administrar Negocios/i }),
      page.getByText(/Administrar Negocios/i),
    ]);
    if (!administrarNegocios) {
      throw new Error('No se encontró la opción "Administrar Negocios".');
    }
    await clickAndWait(administrarNegocios, page);

    await expect(page.getByText(/Información General/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Sección Legal/i)).toBeVisible({ timeout: 15000 });

    await page.screenshot({
      path: testInfo.outputPath("04-administrar-negocios-page.png"),
      fullPage: true,
    });
    evidence.administrarNegociosScreenshot = "04-administrar-negocios-page.png";
  });

  await runStep("Información General", async () => {
    const body = page.locator("body");
    const bodyText = await body.innerText();

    const emailRegex = /\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/i;
    if (!emailRegex.test(bodyText)) {
      throw new Error("No se detectó un correo visible en Información General.");
    }

    await expect(body).toContainText(/BUSINESS PLAN/i);
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

    if (!bodyText.includes(GOOGLE_ACCOUNT_EMAIL)) {
      throw new Error(
        `No se encontró el correo esperado del usuario (${GOOGLE_ACCOUNT_EMAIL}).`
      );
    }

    const hasNameLabel = /nombre/i.test(bodyText);
    if (!hasNameLabel) {
      throw new Error("No se encontró un nombre de usuario visible.");
    }
  });

  await runStep("Detalles de la Cuenta", async () => {
    const body = page.locator("body");
    await expect(body).toContainText(/Cuenta creada/i);
    await expect(body).toContainText(/Estado activo/i);
    await expect(body).toContainText(/Idioma seleccionado/i);
  });

  await runStep("Tus Negocios", async () => {
    const body = page.locator("body");
    await expect(body).toContainText(/Tus Negocios/i);
    await expect(page.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
    await expect(body).toContainText(/Tienes 2 de 3 negocios/i);
  });

  await runStep("Términos y Condiciones", async () => {
    evidence.terminosUrl = await openLegalDocument({
      appPage: page,
      linkText: /T[ée]rminos y Condiciones/i,
      expectedHeadingRegex: /T[ée]rminos y Condiciones/i,
      screenshotName: "05-terminos-y-condiciones.png",
      testInfo,
    });
  });

  await runStep("Política de Privacidad", async () => {
    evidence.politicaPrivacidadUrl = await openLegalDocument({
      appPage: page,
      linkText: /Pol[íi]tica de Privacidad/i,
      expectedHeadingRegex: /Pol[íi]tica de Privacidad/i,
      screenshotName: "06-politica-de-privacidad.png",
      testInfo,
    });
  });

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    accountUsed: GOOGLE_ACCOUNT_EMAIL,
    results: report,
    evidence,
    failures,
  };

  await testInfo.attach("final-report", {
    body: JSON.stringify(finalReport, null, 2),
    contentType: "application/json",
  });

  console.log(JSON.stringify(finalReport, null, 2));

  expect(
    failures,
    `Validaciones fallidas:\n${failures.map((failure) => `- ${failure}`).join("\n")}`
  ).toEqual([]);
});
