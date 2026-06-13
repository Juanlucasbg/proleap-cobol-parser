const fs = require("fs/promises");
const path = require("path");
const { test, expect } = require("@playwright/test");

const LOGIN_EMAIL = "juanlucasbarbiergarzon@gmail.com";

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informacion General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Terminos y Condiciones",
  "Politica de Privacidad"
];

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 45_000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
}

async function isLocatorVisible(locator) {
  const count = Math.min(await locator.count(), 8);
  for (let index = 0; index < count; index += 1) {
    const entry = locator.nth(index);
    if (await entry.isVisible().catch(() => false)) {
      return true;
    }
  }

  return false;
}

async function findFirstVisible(locators) {
  for (const locator of locators) {
    const count = Math.min(await locator.count(), 8);
    for (let index = 0; index < count; index += 1) {
      const entry = locator.nth(index);
      if (await entry.isVisible().catch(() => false)) {
        return entry;
      }
    }
  }

  return null;
}

function selectorCandidates(page, textOrRegex) {
  return [
    page.getByRole("button", { name: textOrRegex }),
    page.getByRole("link", { name: textOrRegex }),
    page.getByRole("menuitem", { name: textOrRegex }),
    page.getByRole("tab", { name: textOrRegex }),
    page.getByText(textOrRegex)
  ];
}

async function clickByVisibleText(page, textOrRegex, notFoundMessage) {
  const target = await findFirstVisible(selectorCandidates(page, textOrRegex));
  if (!target) {
    throw new Error(notFoundMessage);
  }

  await target.click();
  await waitForUi(page);
  return target;
}

async function expectTextVisible(page, textOrRegex, notFoundMessage) {
  const target = await findFirstVisible([
    page.getByRole("heading", { name: textOrRegex }),
    page.getByText(textOrRegex)
  ]);

  if (!target) {
    throw new Error(notFoundMessage);
  }

  await expect(target).toBeVisible();
}

async function takeCheckpoint(page, artifactsDir, fileName, fullPage = false) {
  const imagePath = path.join(artifactsDir, fileName);
  await page.screenshot({ path: imagePath, fullPage });
}

async function openLegalLinkAndValidate({
  page,
  context,
  artifactsDir,
  linkName,
  headingPattern,
  screenshotName,
  report,
  reportField,
  reportUrlField
}) {
  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await clickByVisibleText(page, linkName, `No se encontro el enlace legal: ${linkName}`);
  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded", { timeout: 45_000 }).catch(() => {});
    await waitForUi(popup);
    await expectTextVisible(
      popup,
      headingPattern,
      `No se encontro el encabezado esperado en la nueva pestana: ${headingPattern}`
    );
    await takeCheckpoint(popup, artifactsDir, screenshotName, true);
    report.evidence[reportUrlField] = popup.url();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await expectTextVisible(
      page,
      headingPattern,
      `No se encontro el encabezado esperado en la pagina actual: ${headingPattern}`
    );
    await takeCheckpoint(page, artifactsDir, screenshotName, true);
    report.evidence[reportUrlField] = page.url();
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
  }

  report.results[reportField] = "PASS";
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || process.env.BASE_URL;
  if (!loginUrl) {
    throw new Error(
      "Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL/BASE_URL) to the SaleADS login page for the target environment."
    );
  }

  const artifactsDir = path.join(process.cwd(), "artifacts", `run-${Date.now()}`);
  await fs.mkdir(artifactsDir, { recursive: true });

  const report = {
    generatedAt: new Date().toISOString(),
    environmentUrl: loginUrl,
    results: Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"])),
    notes: [],
    evidence: {
      dashboardScreenshot: "",
      menuScreenshot: "",
      modalScreenshot: "",
      accountPageScreenshot: "",
      termsScreenshot: "",
      termsFinalUrl: "",
      privacyScreenshot: "",
      privacyFinalUrl: ""
    }
  };

  const runStep = async (field, fn) => {
    try {
      await fn();
      report.results[field] = "PASS";
    } catch (error) {
      report.results[field] = "FAIL";
      report.notes.push(`${field}: ${error.message}`);
    }
  };

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  await runStep("Login", async () => {
    const accountPopupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await clickByVisibleText(
      page,
      /sign in with google|iniciar sesion con google|continuar con google|google/i,
      "No se encontro el boton de inicio de sesion con Google."
    );

    const accountPopup = await accountPopupPromise;
    const authPage = accountPopup || page;
    await authPage.waitForLoadState("domcontentloaded", { timeout: 45_000 }).catch(() => {});
    await waitForUi(authPage);

    const emailCandidate = await findFirstVisible([
      authPage.getByRole("button", { name: LOGIN_EMAIL }),
      authPage.getByRole("link", { name: LOGIN_EMAIL }),
      authPage.getByText(LOGIN_EMAIL)
    ]);
    if (emailCandidate) {
      await emailCandidate.click();
      await waitForUi(accountPopup || page);
    }

    await expectTextVisible(
      page,
      /Negocio|Mi Negocio|Dashboard|Panel/i,
      "No se detecto la interfaz principal luego del login."
    );
    await expectTextVisible(page, /Negocio|Mi Negocio/i, "No se detecto la barra lateral izquierda.");

    const screenshotName = "01-dashboard-loaded.png";
    await takeCheckpoint(page, artifactsDir, screenshotName, true);
    report.evidence.dashboardScreenshot = screenshotName;
  });

  await runStep("Mi Negocio menu", async () => {
    await clickByVisibleText(page, /Negocio/i, "No se encontro la seccion 'Negocio' en la barra lateral.");
    await clickByVisibleText(page, /Mi Negocio/i, "No se encontro la opcion 'Mi Negocio'.");
    await expectTextVisible(page, /Agregar Negocio/i, "No se encontro 'Agregar Negocio' en el submenu.");
    await expectTextVisible(page, /Administrar Negocios/i, "No se encontro 'Administrar Negocios' en el submenu.");

    const screenshotName = "02-mi-negocio-menu-expanded.png";
    await takeCheckpoint(page, artifactsDir, screenshotName, true);
    report.evidence.menuScreenshot = screenshotName;
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickByVisibleText(page, /Agregar Negocio/i, "No se encontro la accion 'Agregar Negocio'.");
    await expectTextVisible(page, /Crear Nuevo Negocio/i, "No se encontro el modal 'Crear Nuevo Negocio'.");
    await expectTextVisible(
      page,
      /Nombre del Negocio/i,
      "No se encontro el campo 'Nombre del Negocio' en el modal."
    );
    await expectTextVisible(
      page,
      /Tienes 2 de 3 negocios/i,
      "No se encontro el texto 'Tienes 2 de 3 negocios' en el modal."
    );
    await expectTextVisible(page, /Cancelar/i, "No se encontro el boton 'Cancelar'.");
    await expectTextVisible(page, /Crear Negocio/i, "No se encontro el boton 'Crear Negocio'.");

    const businessInput = await findFirstVisible([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input[type='text']")
    ]);
    if (businessInput) {
      await businessInput.click();
      await businessInput.fill("Negocio Prueba Automatizacion");
    }

    const screenshotName = "03-agregar-negocio-modal.png";
    await takeCheckpoint(page, artifactsDir, screenshotName, true);
    report.evidence.modalScreenshot = screenshotName;

    await clickByVisibleText(page, /Cancelar/i, "No se encontro el boton 'Cancelar' para cerrar el modal.");
  });

  await runStep("Administrar Negocios view", async () => {
    const manageVisible = await isLocatorVisible(page.getByText(/Administrar Negocios/i));
    if (!manageVisible) {
      await clickByVisibleText(page, /Mi Negocio/i, "No se pudo reabrir el menu 'Mi Negocio'.");
    }

    await clickByVisibleText(page, /Administrar Negocios/i, "No se encontro 'Administrar Negocios'.");
    await expectTextVisible(page, /Informaci[oó]n General/i, "No existe la seccion 'Informacion General'.");
    await expectTextVisible(page, /Detalles de la Cuenta/i, "No existe la seccion 'Detalles de la Cuenta'.");
    await expectTextVisible(page, /Tus Negocios/i, "No existe la seccion 'Tus Negocios'.");
    await expectTextVisible(page, /Secci[oó]n Legal/i, "No existe la seccion 'Seccion Legal'.");

    const screenshotName = "04-administrar-negocios-account-page.png";
    await takeCheckpoint(page, artifactsDir, screenshotName, true);
    report.evidence.accountPageScreenshot = screenshotName;
  });

  await runStep("Informacion General", async () => {
    await expectTextVisible(page, /Informaci[oó]n General/i, "No existe 'Informacion General'.");
    await expectTextVisible(page, LOGIN_EMAIL, "No se visualiza el correo del usuario.");
    await expectTextVisible(page, /BUSINESS PLAN/i, "No se visualiza el texto 'BUSINESS PLAN'.");
    await expectTextVisible(page, /Cambiar Plan/i, "No se visualiza el boton 'Cambiar Plan'.");

    const hasNameSignals = await findFirstVisible([
      page.getByText(/Nombre/i),
      page.getByText(/Usuario/i),
      page.getByText(/Perfil/i),
      page.getByText(/Juan|Lucas|Barbier|Garzon/i)
    ]);
    if (!hasNameSignals) {
      throw new Error("No se encontraron indicadores visibles del nombre de usuario.");
    }
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expectTextVisible(page, /Cuenta creada/i, "No se visualiza 'Cuenta creada'.");
    await expectTextVisible(page, /Estado activo/i, "No se visualiza 'Estado activo'.");
    await expectTextVisible(page, /Idioma seleccionado/i, "No se visualiza 'Idioma seleccionado'.");
  });

  await runStep("Tus Negocios", async () => {
    await expectTextVisible(page, /Tus Negocios/i, "No existe la seccion 'Tus Negocios'.");
    await expectTextVisible(page, /Agregar Negocio/i, "No existe el boton 'Agregar Negocio'.");
    await expectTextVisible(page, /Tienes 2 de 3 negocios/i, "No se visualiza 'Tienes 2 de 3 negocios'.");

    const listVisible = await findFirstVisible([
      page.locator("ul li"),
      page.locator("table tbody tr"),
      page.getByRole("row")
    ]);
    if (!listVisible) {
      throw new Error("No se detecto una lista visible de negocios.");
    }
  });

  await runStep("Terminos y Condiciones", async () => {
    await openLegalLinkAndValidate({
      page,
      context,
      artifactsDir,
      linkName: /T[eé]rminos y Condiciones/i,
      headingPattern: /T[eé]rminos y Condiciones/i,
      screenshotName: "05-terminos-y-condiciones.png",
      report,
      reportField: "Terminos y Condiciones",
      reportUrlField: "termsFinalUrl"
    });
    report.evidence.termsScreenshot = "05-terminos-y-condiciones.png";
  });

  await runStep("Politica de Privacidad", async () => {
    await openLegalLinkAndValidate({
      page,
      context,
      artifactsDir,
      linkName: /Pol[ií]tica de Privacidad/i,
      headingPattern: /Pol[ií]tica de Privacidad/i,
      screenshotName: "06-politica-de-privacidad.png",
      report,
      reportField: "Politica de Privacidad",
      reportUrlField: "privacyFinalUrl"
    });
    report.evidence.privacyScreenshot = "06-politica-de-privacidad.png";
  });

  const finalSummary = {
    generatedAt: report.generatedAt,
    login: report.results.Login,
    miNegocioMenu: report.results["Mi Negocio menu"],
    agregarNegocioModal: report.results["Agregar Negocio modal"],
    administrarNegociosView: report.results["Administrar Negocios view"],
    informacionGeneral: report.results["Informacion General"],
    detallesDeLaCuenta: report.results["Detalles de la Cuenta"],
    tusNegocios: report.results["Tus Negocios"],
    terminosYCondiciones: report.results["Terminos y Condiciones"],
    politicaDePrivacidad: report.results["Politica de Privacidad"],
    termsFinalUrl: report.evidence.termsFinalUrl,
    privacyFinalUrl: report.evidence.privacyFinalUrl,
    artifactsDir
  };

  await testInfo.attach("final-report.json", {
    body: Buffer.from(JSON.stringify({ summary: finalSummary, raw: report }, null, 2)),
    contentType: "application/json"
  });
  console.log(`FINAL_REPORT ${JSON.stringify(finalSummary, null, 2)}`);

  const failedChecks = REPORT_FIELDS.filter((field) => report.results[field] !== "PASS");
  expect(
    failedChecks,
    `Validation failures: ${failedChecks.join(", ")}.\nDetailed report:\n${JSON.stringify(report, null, 2)}`
  ).toEqual([]);
});
