const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

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

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
  await page.waitForLoadState("networkidle", { timeout: 7000 }).catch(() => {});
}

async function findVisibleWithTimeout(page, locators, timeoutMs = 15000) {
  const startedAt = Date.now();

  while (Date.now() - startedAt < timeoutMs) {
    for (const locator of locators) {
      const first = locator.first();
      if (await first.isVisible().catch(() => false)) {
        return first;
      }
    }
    await page.waitForTimeout(300);
  }

  return null;
}

async function saveCheckpoint(page, testInfo, name, fullPage = false) {
  const checkpointsDir = path.join(testInfo.outputDir, "checkpoints");
  fs.mkdirSync(checkpointsDir, { recursive: true });

  const screenshotPath = path.join(checkpointsDir, name);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(name, { path: screenshotPath, contentType: "image/png" });
}

test("SaleADS Mi Negocio full workflow", async ({ page }, testInfo) => {
  test.setTimeout(240000);

  const status = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const failures = [];
  const evidence = {
    terminosUrl: null,
    politicaUrl: null
  };

  async function runStep(label, action) {
    try {
      await action();
      status[label] = "PASS";
    } catch (error) {
      status[label] = "FAIL";
      failures.push(`${label}: ${error.message}`);
      testInfo.annotations.push({
        type: "error",
        description: `${label}: ${error.message}`
      });
    }
  }

  async function openAndValidateLegalLink({
    linkTextRegex,
    headingRegex,
    screenshotName,
    evidenceKey
  }) {
    const appUrlBefore = page.url();
    const link = await findVisibleWithTimeout(
      page,
      [page.getByRole("link", { name: linkTextRegex }), page.getByText(linkTextRegex)],
      15000
    );
    expect(link, `No se encontró enlace legal: ${linkTextRegex}`).toBeTruthy();

    const popupPromise = page.context().waitForEvent("page", { timeout: 12000 }).catch(() => null);
    await link.click();
    await waitForUi(page);

    const popup = await popupPromise;
    const legalPage = popup || page;
    await legalPage.waitForLoadState("domcontentloaded");
    await legalPage.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});

    const heading = await findVisibleWithTimeout(
      legalPage,
      [legalPage.getByRole("heading", { name: headingRegex }), legalPage.getByText(headingRegex)],
      15000
    );
    expect(heading, `No se encontró heading legal: ${headingRegex}`).toBeTruthy();

    const legalText = await legalPage.locator("body").innerText();
    expect(
      legalText.replace(/\s+/g, " ").trim().length,
      "No se detectó contenido legal suficiente."
    ).toBeGreaterThan(120);

    await saveCheckpoint(legalPage, testInfo, screenshotName, true);
    evidence[evidenceKey] = legalPage.url();

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
    } else if (page.url() !== appUrlBefore) {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
        await page.goto(appUrlBefore, { waitUntil: "domcontentloaded" });
      });
      await waitForUi(page);
    }
  }

  await runStep("Login", async () => {
    const targetUrl = process.env.SALEADS_URL || process.env.BASE_URL;
    if (targetUrl) {
      await page.goto(targetUrl, { waitUntil: "domcontentloaded" });
    }

    await waitForUi(page);

    const googleButton = await findVisibleWithTimeout(
      page,
      [
        page.getByRole("button", { name: /google/i }),
        page.getByRole("link", { name: /google/i }),
        page.getByText(/sign in with google|inicia sesi[oó]n con google|continuar con google/i)
      ],
      25000
    );
    expect(googleButton, "No se encontró botón de Login con Google.").toBeTruthy();

    const popupPromise = page.context().waitForEvent("page", { timeout: 12000 }).catch(() => null);
    await googleButton.click();
    await waitForUi(page);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");

      const accountOption = await findVisibleWithTimeout(
        popup,
        [popup.getByText("juanlucasbarbiergarzon@gmail.com", { exact: false })],
        10000
      );

      if (accountOption) {
        await accountOption.click();
      }

      await popup.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
      if (!popup.isClosed()) {
        await popup.close().catch(() => {});
      }
      await page.bringToFront();
    }

    const mainInterface = await findVisibleWithTimeout(
      page,
      [page.locator("aside"), page.getByRole("navigation"), page.locator('[class*="sidebar"]')],
      60000
    );

    expect(mainInterface, "No se cargó la interfaz principal o sidebar.").toBeTruthy();
    await saveCheckpoint(page, testInfo, "01-dashboard-loaded.png");
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = await findVisibleWithTimeout(
      page,
      [page.getByRole("button", { name: /negocio/i }), page.getByText(/^Negocio$/i)],
      20000
    );
    expect(negocioSection, "No se encontró sección Negocio.").toBeTruthy();
    await negocioSection.click();
    await waitForUi(page);

    const miNegocioOption = await findVisibleWithTimeout(
      page,
      [page.getByRole("button", { name: /mi negocio/i }), page.getByText(/mi negocio/i)],
      15000
    );
    expect(miNegocioOption, "No se encontró opción Mi Negocio.").toBeTruthy();
    await miNegocioOption.click();
    await waitForUi(page);

    const agregarNegocio = await findVisibleWithTimeout(
      page,
      [page.getByRole("button", { name: /agregar negocio/i }), page.getByText(/agregar negocio/i)],
      15000
    );
    const administrarNegocios = await findVisibleWithTimeout(
      page,
      [
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i)
      ],
      15000
    );

    expect(agregarNegocio, "No se encontró Agregar Negocio.").toBeTruthy();
    expect(administrarNegocios, "No se encontró Administrar Negocios.").toBeTruthy();
    await saveCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png");
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await findVisibleWithTimeout(
      page,
      [page.getByRole("button", { name: /^Agregar Negocio$/i }), page.getByText(/^Agregar Negocio$/i)],
      15000
    );
    expect(agregarNegocio, "No se encontró botón Agregar Negocio (menú).").toBeTruthy();
    await agregarNegocio.click();
    await waitForUi(page);

    const modalTitle = await findVisibleWithTimeout(page, [page.getByText(/crear nuevo negocio/i)], 10000);
    const nombreNegocioInput = page.getByLabel(/nombre del negocio/i).first();
    const negociosLimitText = await findVisibleWithTimeout(
      page,
      [page.getByText(/tienes 2 de 3 negocios/i)],
      10000
    );
    const cancelarButton = await findVisibleWithTimeout(
      page,
      [page.getByRole("button", { name: /cancelar/i })],
      10000
    );
    const crearNegocioButton = await findVisibleWithTimeout(
      page,
      [page.getByRole("button", { name: /crear negocio/i })],
      10000
    );

    expect(modalTitle, "No se encontró modal Crear Nuevo Negocio.").toBeTruthy();
    await expect(nombreNegocioInput).toBeVisible();
    expect(negociosLimitText, "No se encontró texto de límite de negocios.").toBeTruthy();
    expect(cancelarButton, "No se encontró botón Cancelar.").toBeTruthy();
    expect(crearNegocioButton, "No se encontró botón Crear Negocio.").toBeTruthy();

    await saveCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");

    await nombreNegocioInput.click();
    await nombreNegocioInput.fill("Negocio Prueba Automatización");
    await cancelarButton.click();
    await waitForUi(page);
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarNegocios = await findVisibleWithTimeout(
      page,
      [
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i)
      ],
      20000
    );
    expect(administrarNegocios, "No se encontró Administrar Negocios.").toBeTruthy();
    await administrarNegocios.click();
    await waitForUi(page);

    const informacionGeneral = await findVisibleWithTimeout(
      page,
      [page.getByText(/informaci[oó]n general/i)],
      20000
    );
    const detallesCuenta = await findVisibleWithTimeout(
      page,
      [page.getByText(/detalles de la cuenta/i)],
      20000
    );
    const tusNegocios = await findVisibleWithTimeout(page, [page.getByText(/tus negocios/i)], 20000);
    const seccionLegal = await findVisibleWithTimeout(page, [page.getByText(/secci[oó]n legal/i)], 20000);

    expect(informacionGeneral, "No se encontró Información General.").toBeTruthy();
    expect(detallesCuenta, "No se encontró Detalles de la Cuenta.").toBeTruthy();
    expect(tusNegocios, "No se encontró Tus Negocios.").toBeTruthy();
    expect(seccionLegal, "No se encontró Sección Legal.").toBeTruthy();

    await saveCheckpoint(page, testInfo, "04-administrar-negocios-view-full.png", true);
  });

  await runStep("Información General", async () => {
    const userName = await findVisibleWithTimeout(
      page,
      [
        page.getByText(/@[a-z0-9._-]+\.[a-z]{2,}/i),
        page.getByText(/nombre|usuario/i),
        page.locator("text=/\\S+\\s+\\S+/").first()
      ],
      12000
    );
    const userEmail = await findVisibleWithTimeout(
      page,
      [page.getByText(/[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}/i)],
      12000
    );
    const businessPlan = await findVisibleWithTimeout(page, [page.getByText(/business plan/i)], 12000);
    const cambiarPlan = await findVisibleWithTimeout(
      page,
      [page.getByRole("button", { name: /cambiar plan/i }), page.getByText(/cambiar plan/i)],
      12000
    );

    expect(userName, "No se encontró nombre de usuario.").toBeTruthy();
    expect(userEmail, "No se encontró email de usuario.").toBeTruthy();
    expect(businessPlan, "No se encontró texto BUSINESS PLAN.").toBeTruthy();
    expect(cambiarPlan, "No se encontró botón Cambiar Plan.").toBeTruthy();
  });

  await runStep("Detalles de la Cuenta", async () => {
    const cuentaCreada = await findVisibleWithTimeout(page, [page.getByText(/cuenta creada/i)], 12000);
    const estadoActivo = await findVisibleWithTimeout(page, [page.getByText(/estado activo/i)], 12000);
    const idiomaSeleccionado = await findVisibleWithTimeout(
      page,
      [page.getByText(/idioma seleccionado/i)],
      12000
    );

    expect(cuentaCreada, "No se encontró campo Cuenta creada.").toBeTruthy();
    expect(estadoActivo, "No se encontró campo Estado activo.").toBeTruthy();
    expect(idiomaSeleccionado, "No se encontró campo Idioma seleccionado.").toBeTruthy();
  });

  await runStep("Tus Negocios", async () => {
    const businessList = await findVisibleWithTimeout(page, [page.getByText(/tus negocios/i)], 12000);
    const agregarNegocioButton = await findVisibleWithTimeout(
      page,
      [page.getByRole("button", { name: /agregar negocio/i }), page.getByText(/agregar negocio/i)],
      12000
    );
    const negociosLimitText = await findVisibleWithTimeout(
      page,
      [page.getByText(/tienes 2 de 3 negocios/i)],
      12000
    );

    expect(businessList, "No se encontró listado de negocios.").toBeTruthy();
    expect(agregarNegocioButton, "No se encontró botón Agregar Negocio.").toBeTruthy();
    expect(negociosLimitText, "No se encontró texto de límite de negocios.").toBeTruthy();
  });

  await runStep("Términos y Condiciones", async () => {
    await openAndValidateLegalLink({
      linkTextRegex: /t[eé]rminos y condiciones/i,
      headingRegex: /t[eé]rminos y condiciones/i,
      screenshotName: "05-terminos-y-condiciones.png",
      evidenceKey: "terminosUrl"
    });
  });

  await runStep("Política de Privacidad", async () => {
    await openAndValidateLegalLink({
      linkTextRegex: /pol[ií]tica de privacidad/i,
      headingRegex: /pol[ií]tica de privacidad/i,
      screenshotName: "06-politica-de-privacidad.png",
      evidenceKey: "politicaUrl"
    });
  });

  const report = {
    ...status,
    "Términos y Condiciones URL": evidence.terminosUrl,
    "Política de Privacidad URL": evidence.politicaUrl
  };

  const reportPath = path.join(testInfo.outputDir, "saleads-mi-negocio-report.json");
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2), "utf-8");
  await testInfo.attach("final-report", { path: reportPath, contentType: "application/json" });

  console.log("FINAL_REPORT_START");
  console.log(JSON.stringify(report, null, 2));
  console.log("FINAL_REPORT_END");

  expect(
    failures,
    `Se encontraron validaciones con FAIL:\n${failures.map((item) => ` - ${item}`).join("\n")}`
  ).toEqual([]);
});
