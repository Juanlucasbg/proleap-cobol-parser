import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright";

const ACCOUNT_EMAIL = process.env.SALEADS_GOOGLE_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";
const LOGIN_URL = process.env.SALEADS_LOGIN_URL;
const HEADLESS = (process.env.HEADLESS ?? "false").toLowerCase() === "true";
const CDP_ENDPOINT = process.env.PW_CDP_ENDPOINT;

const report = {
  Login: "FAIL",
  "Mi Negocio menu": "FAIL",
  "Agregar Negocio modal": "FAIL",
  "Administrar Negocios view": "FAIL",
  "Información General": "FAIL",
  "Detalles de la Cuenta": "FAIL",
  "Tus Negocios": "FAIL",
  "Términos y Condiciones": "FAIL",
  "Política de Privacidad": "FAIL",
};

const urls = {
  "Términos y Condiciones": null,
  "Política de Privacidad": null,
};

const failures = [];

const runStamp = new Date().toISOString().replace(/[:.]/g, "-");
const artifactsDir = path.join(process.cwd(), "artifacts", "saleads-mi-negocio", runStamp);

async function ensureVisible(locator, description, timeout = 12000) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return locator.first();
  } catch {
    throw new Error(`${description} no es visible`);
  }
}

async function firstVisible(candidates, timeout = 3000) {
  for (const locator of candidates) {
    try {
      await locator.first().waitFor({ state: "visible", timeout });
      return locator.first();
    } catch {
      continue;
    }
  }

  return null;
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(1200);
}

async function capture(page, name, fullPage = false) {
  const screenshotPath = path.join(artifactsDir, `${name}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

async function clickAndWait(locator, page) {
  await locator.click();
  await waitForUi(page);
}

async function withStep(name, fn) {
  try {
    await fn();
    report[name] = "PASS";
    console.log(`PASS: ${name}`);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    failures.push({ step: name, error: message });
    report[name] = "FAIL";
    console.error(`FAIL: ${name} -> ${message}`);
  }
}

async function getAppAndPage() {
  if (CDP_ENDPOINT) {
    const browser = await chromium.connectOverCDP(CDP_ENDPOINT);
    const context = browser.contexts()[0] ?? (await browser.newContext());
    const page = context.pages()[0] ?? (await context.newPage());
    return { browser, context, page };
  }

  if (!LOGIN_URL) {
    throw new Error(
      "Debes definir SALEADS_LOGIN_URL cuando no se usa PW_CDP_ENDPOINT para evitar depender de una URL fija en el script."
    );
  }

  const browser = await chromium.launch({ headless: HEADLESS });
  const context = await browser.newContext();
  const page = await context.newPage();

  await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded" });
  await waitForUi(page);
  return { browser, context, page };
}

async function openLegalLinkAndValidate({ appPage, linkRegex, headingRegex, reportKey, shotName }) {
  const context = appPage.context();
  const link = await firstVisible(
    [
      appPage.getByRole("link", { name: linkRegex }),
      appPage.getByText(linkRegex),
      appPage.getByRole("button", { name: linkRegex }),
    ],
    5000
  );

  if (!link) {
    throw new Error(`No se encontró el enlace legal: ${linkRegex}`);
  }

  const popupPromise = context.waitForEvent("page", { timeout: 6000 }).catch(() => null);
  await link.click();
  await waitForUi(appPage);

  const popup = await popupPromise;
  const legalPage = popup ?? appPage;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
    await popup.bringToFront();
    await waitForUi(popup);
  }

  const heading = await firstVisible(
    [legalPage.getByRole("heading", { name: headingRegex }), legalPage.getByText(headingRegex)],
    12000
  );

  if (!heading) {
    throw new Error(`No apareció el heading legal esperado: ${headingRegex}`);
  }

  const legalBody = await firstVisible(
    [
      legalPage.locator("main p"),
      legalPage.locator("article p"),
      legalPage.getByText(/(t[ée]rminos|condiciones|privacidad|datos personales|uso de datos|usuario)/i),
    ],
    8000
  );

  if (!legalBody) {
    throw new Error("No se detectó contenido legal en la página");
  }

  await capture(legalPage, shotName, true);
  urls[reportKey] = legalPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await legalPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(appPage);
  }
}

await fs.mkdir(artifactsDir, { recursive: true });

let browser;

try {
  const app = await getAppAndPage();
  browser = app.browser;
  const page = app.page;

  await withStep("Login", async () => {
    const sidebarAlreadyVisible = await firstVisible(
      [
        page.getByRole("navigation"),
        page.getByText(/Negocio/i),
        page.locator("aside"),
      ],
      4000
    );

    if (!sidebarAlreadyVisible) {
      const loginButton = await firstVisible(
        [
          page.getByRole("button", { name: /(sign in|iniciar sesi[oó]n|continuar|ingresar).*(google)/i }),
          page.getByRole("link", { name: /(sign in|iniciar sesi[oó]n|continuar|ingresar).*(google)/i }),
          page.getByText(/(sign in|iniciar sesi[oó]n|continuar|ingresar).*(google)/i),
        ],
        12000
      );

      if (!loginButton) {
        throw new Error("No se encontró el botón de login con Google");
      }

      const popupPromise = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);
      await clickAndWait(loginButton, page);
      const popup = await popupPromise;
      const authPage = popup ?? page;

      await authPage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});

      if (authPage.url().includes("accounts.google.com")) {
        const accountPicker = await firstVisible(
          [
            authPage.getByText(ACCOUNT_EMAIL, { exact: false }),
            authPage.getByRole("button", { name: new RegExp(ACCOUNT_EMAIL, "i") }),
            authPage.getByRole("link", { name: new RegExp(ACCOUNT_EMAIL, "i") }),
          ],
          8000
        );

        if (accountPicker) {
          await clickAndWait(accountPicker, authPage);
        }
      }

      if (popup) {
        await popup.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
        await page.bringToFront();
      }
    }

    const sidebar = await firstVisible(
      [
        page.getByRole("navigation"),
        page.locator("aside"),
        page.getByText(/Negocio/i),
      ],
      15000
    );

    if (!sidebar) {
      throw new Error("No se visualiza la interfaz principal con sidebar");
    }

    await capture(page, "01-dashboard-loaded", true);
  });

  await withStep("Mi Negocio menu", async () => {
    const miNegocioEntry = await firstVisible(
      [
        page.getByRole("button", { name: /Mi Negocio/i }),
        page.getByRole("link", { name: /Mi Negocio/i }),
        page.getByText(/Mi Negocio/i),
      ],
      12000
    );

    if (!miNegocioEntry) {
      throw new Error("No se encontró la opción Mi Negocio en sidebar");
    }

    await clickAndWait(miNegocioEntry, page);

    await ensureVisible(page.getByText(/Agregar Negocio/i), "Submenú Agregar Negocio");
    await ensureVisible(page.getByText(/Administrar Negocios/i), "Submenú Administrar Negocios");
    await capture(page, "02-mi-negocio-menu-expanded", true);
  });

  await withStep("Agregar Negocio modal", async () => {
    const addBusinessOption = await ensureVisible(page.getByText(/Agregar Negocio/i), "Opción Agregar Negocio");
    await clickAndWait(addBusinessOption, page);

    await ensureVisible(
      page.getByRole("heading", { name: /Crear Nuevo Negocio/i }),
      "Título del modal Crear Nuevo Negocio"
    );
    const nameField = await firstVisible(
      [page.getByLabel(/Nombre del Negocio/i), page.getByPlaceholder(/Nombre del Negocio/i)],
      12000
    );
    if (!nameField) {
      throw new Error("Campo Nombre del Negocio no es visible");
    }
    await ensureVisible(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i), "Texto de límite de negocios");
    await ensureVisible(page.getByRole("button", { name: /Cancelar/i }), "Botón Cancelar");
    const createBtn = await ensureVisible(page.getByRole("button", { name: /Crear Negocio/i }), "Botón Crear Negocio");
    if (!createBtn) {
      throw new Error("No se encontró el botón Crear Negocio");
    }

    await nameField.click();
    await nameField.fill("Negocio Prueba Automatización");
    await capture(page, "03-agregar-negocio-modal", true);
    const cancelButton = await ensureVisible(page.getByRole("button", { name: /Cancelar/i }), "Botón Cancelar");
    await clickAndWait(cancelButton, page);
  });

  await withStep("Administrar Negocios view", async () => {
    const adminEntry = await firstVisible(
      [
        page.getByRole("link", { name: /Administrar Negocios/i }),
        page.getByRole("button", { name: /Administrar Negocios/i }),
        page.getByText(/Administrar Negocios/i),
      ],
      5000
    );

    if (!adminEntry) {
      const miNegocioEntry = await ensureVisible(page.getByText(/Mi Negocio/i), "Mi Negocio");
      await clickAndWait(miNegocioEntry, page);
    }

    const adminEntryVisible = await ensureVisible(
      page.getByText(/Administrar Negocios/i),
      "Opción Administrar Negocios"
    );
    await clickAndWait(adminEntryVisible, page);

    await ensureVisible(page.getByText(/Informaci[oó]n General/i), "Sección Información General");
    await ensureVisible(page.getByText(/Detalles de la Cuenta/i), "Sección Detalles de la Cuenta");
    await ensureVisible(page.getByText(/Tus Negocios/i), "Sección Tus Negocios");
    await ensureVisible(page.getByText(/Secci[oó]n Legal/i), "Sección Legal");
    await capture(page, "04-administrar-negocios-view", true);
  });

  await withStep("Información General", async () => {
    await ensureVisible(page.getByText(/@/), "Email de usuario");
    await ensureVisible(page.getByText(/BUSINESS PLAN/i), "Texto BUSINESS PLAN");
    await ensureVisible(page.getByRole("button", { name: /Cambiar Plan/i }), "Botón Cambiar Plan");

    const possibleUserName = await firstVisible(
      [
        page.locator("section").filter({ hasText: /Informaci[oó]n General/i }).locator("h1,h2,h3,p,span,div"),
        page.getByText(/[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+ [A-ZÁÉÍÓÚÑ][a-záéíóúñ]+/),
      ],
      8000
    );

    if (!possibleUserName) {
      throw new Error("No se detectó nombre de usuario visible en Información General");
    }
  });

  await withStep("Detalles de la Cuenta", async () => {
    await ensureVisible(page.getByText(/Cuenta creada/i), "Campo Cuenta creada");
    await ensureVisible(page.getByText(/Estado activo/i), "Campo Estado activo");
    await ensureVisible(page.getByText(/Idioma seleccionado/i), "Campo Idioma seleccionado");
  });

  await withStep("Tus Negocios", async () => {
    await ensureVisible(page.getByText(/Tus Negocios/i), "Título Tus Negocios");
    await ensureVisible(page.getByRole("button", { name: /Agregar Negocio/i }), "Botón Agregar Negocio");
    await ensureVisible(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i), "Texto Tienes 2 de 3 negocios");

    const businessList = await firstVisible(
      [
        page.locator("section").filter({ hasText: /Tus Negocios/i }).locator("li"),
        page.locator("section").filter({ hasText: /Tus Negocios/i }).locator("table"),
        page.locator("section").filter({ hasText: /Tus Negocios/i }).locator("[role='row']"),
      ],
      8000
    );

    if (!businessList) {
      throw new Error("No se detectó listado de negocios en la sección Tus Negocios");
    }
  });

  await withStep("Términos y Condiciones", async () => {
    await openLegalLinkAndValidate({
      appPage: page,
      linkRegex: /T[ée]rminos y Condiciones/i,
      headingRegex: /T[ée]rminos y Condiciones/i,
      reportKey: "Términos y Condiciones",
      shotName: "05-terminos-y-condiciones",
    });
  });

  await withStep("Política de Privacidad", async () => {
    await openLegalLinkAndValidate({
      appPage: page,
      linkRegex: /Pol[ií]tica de Privacidad/i,
      headingRegex: /Pol[ií]tica de Privacidad/i,
      reportKey: "Política de Privacidad",
      shotName: "06-politica-de-privacidad",
    });
  });
} catch (error) {
  const message = error instanceof Error ? error.message : String(error);
  failures.push({ step: "Inicialización/Ejecución", error: message });
  console.error(`FAIL: Inicialización/Ejecución -> ${message}`);
} finally {
  if (browser) {
    await browser.close().catch(() => {});
  }
}

const finalReport = {
  test_name: "saleads_mi_negocio_full_test",
  status: Object.values(report).every((value) => value === "PASS") ? "PASS" : "FAIL",
  checkpoints: report,
  legal_urls: urls,
  failures,
  artifacts_dir: artifactsDir,
};

const reportPath = path.join(artifactsDir, "final-report.json");
await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");

console.log(JSON.stringify(finalReport, null, 2));

if (finalReport.status === "FAIL") {
  process.exitCode = 1;
}
