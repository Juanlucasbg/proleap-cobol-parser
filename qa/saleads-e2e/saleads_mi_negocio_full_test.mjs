import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "@playwright/test";

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

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const LOGIN_URL =
  process.env.SALEADS_LOGIN_URL ||
  process.env.SALEADS_URL ||
  process.env.BASE_URL ||
  process.env.APP_URL ||
  "";

function createReport() {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, "NOT RUN"]));
}

function sanitizeName(value) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .toLowerCase();
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 10000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(900);
}

async function waitForAnyVisible(page, candidates, description, timeoutMs = 20000) {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeoutMs) {
    for (const candidate of candidates) {
      const locator =
        typeof candidate === "string"
          ? page.getByText(new RegExp(candidate, "i")).first()
          : candidate.first();
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }
    await page.waitForTimeout(350);
  }
  throw new Error(`No fue posible ubicar elemento visible: ${description}`);
}

async function assertVisible(page, locator, description, timeoutMs = 20000) {
  await locator.first().waitFor({ state: "visible", timeout: timeoutMs }).catch(() => {
    throw new Error(`No visible: ${description}`);
  });
}

async function clickAndWait(locator, page) {
  await locator.first().click();
  await waitForUiLoad(page);
}

async function capture(page, evidenceDir, name, fullPage = false) {
  const fileName = `${String(Date.now())}_${sanitizeName(name)}.png`;
  const filePath = path.join(evidenceDir, fileName);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function findClickableByText(page, textPattern) {
  const regex = textPattern instanceof RegExp ? textPattern : new RegExp(textPattern, "i");
  return waitForAnyVisible(
    page,
    [
      page.getByRole("button", { name: regex }),
      page.getByRole("link", { name: regex }),
      page.getByText(regex),
    ],
    `clickable with text ${regex.toString()}`
  );
}

async function maybeSelectGoogleAccount(context, page, popupPage) {
  const candidatePages = [popupPage, page, ...context.pages()].filter(Boolean);
  for (const candidate of candidatePages) {
    await candidate.waitForLoadState("domcontentloaded", { timeout: 7000 }).catch(() => {});
    const accountOption = candidate.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
    if (await accountOption.isVisible().catch(() => false)) {
      await accountOption.click();
      await waitForUiLoad(candidate);
      return true;
    }
  }
  return false;
}

async function validateLegalLink({
  page,
  context,
  evidenceDir,
  linkTextRegex,
  headingRegex,
  screenshotLabel,
}) {
  const originPage = page;
  const link = await findClickableByText(originPage, linkTextRegex);
  const newPagePromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);

  await link.click();
  await waitForUiLoad(originPage);

  let destinationPage = await newPagePromise;
  let openedNewTab = true;
  if (!destinationPage) {
    destinationPage = originPage;
    openedNewTab = false;
  } else {
    await destinationPage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
    await waitForUiLoad(destinationPage);
  }

  await assertVisible(
    destinationPage,
    destinationPage.getByRole("heading", { name: headingRegex }).first(),
    `encabezado ${headingRegex.toString()}`
  );

  await waitForAnyVisible(
    destinationPage,
    [
      destinationPage.locator("main p"),
      destinationPage.locator("article p"),
      destinationPage.locator("body p"),
      destinationPage.getByText(/t[eé]rminos|condiciones|privacidad|datos|informaci[oó]n/i),
    ],
    `contenido legal ${headingRegex.toString()}`
  );

  const screenshot = await capture(destinationPage, evidenceDir, screenshotLabel, true);
  const url = destinationPage.url();

  if (openedNewTab) {
    await destinationPage.close();
    await originPage.bringToFront();
    await waitForUiLoad(originPage);
  } else {
    await destinationPage.goBack({ waitUntil: "domcontentloaded", timeout: 15000 }).catch(() => {});
    await waitForUiLoad(destinationPage);
  }

  return { screenshot, url };
}

async function main() {
  if (!LOGIN_URL) {
    throw new Error(
      "Falta URL de login. Define SALEADS_LOGIN_URL, SALEADS_URL, BASE_URL o APP_URL."
    );
  }

  const runId = new Date().toISOString().replace(/[:.]/g, "-");
  const evidenceDir = path.resolve(process.cwd(), "artifacts", runId);
  await fs.mkdir(evidenceDir, { recursive: true });

  const report = createReport();
  const details = {
    evidenceDir,
    urls: {},
    screenshots: [],
  };

  const browser = await chromium.launch({
    headless: process.env.HEADLESS !== "false",
    slowMo: Number(process.env.SLOW_MO_MS || 0),
  });
  const context = await browser.newContext();
  const page = await context.newPage();

  const step = async (field, fn) => {
    try {
      await fn();
      report[field] = "PASS";
    } catch (error) {
      report[field] = `FAIL - ${error.message}`;
      throw error;
    }
  };

  try {
    await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded", timeout: 45000 });
    await waitForUiLoad(page);

    await step("Login", async () => {
      const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
      const loginButton = await findClickableByText(
        page,
        /(sign in with google|google|iniciar sesi[oó]n con google)/i
      );
      await clickAndWait(loginButton, page);
      const popup = await popupPromise;
      await maybeSelectGoogleAccount(context, page, popup);

      await waitForUiLoad(page);
      await waitForAnyVisible(page, [page.locator("aside"), page.getByRole("navigation")], "left sidebar");
      await assertVisible(page, page.getByText(/negocio/i).first(), "texto Negocio en sidebar");

      const screenshot = await capture(page, evidenceDir, "01_dashboard_loaded");
      details.screenshots.push({ step: "Login", path: screenshot });
    });

    await step("Mi Negocio menu", async () => {
      const negocio = await findClickableByText(page, /^negocio$/i);
      await clickAndWait(negocio, page);

      const miNegocio = await findClickableByText(page, /mi negocio/i);
      await clickAndWait(miNegocio, page);

      await assertVisible(page, page.getByText(/agregar negocio/i), "submenu Agregar Negocio");
      await assertVisible(page, page.getByText(/administrar negocios/i), "submenu Administrar Negocios");

      const screenshot = await capture(page, evidenceDir, "02_mi_negocio_menu_expanded");
      details.screenshots.push({ step: "Mi Negocio menu", path: screenshot });
    });

    await step("Agregar Negocio modal", async () => {
      const agregarNegocio = await findClickableByText(page, /agregar negocio/i);
      await clickAndWait(agregarNegocio, page);

      await assertVisible(page, page.getByText(/crear nuevo negocio/i), "título del modal");
      await assertVisible(page, page.getByLabel(/nombre del negocio/i), "input Nombre del Negocio");
      await assertVisible(page, page.getByText(/tienes 2 de 3 negocios/i), "texto límite de negocios");
      await assertVisible(page, page.getByRole("button", { name: /cancelar/i }), "botón Cancelar");
      await assertVisible(page, page.getByRole("button", { name: /crear negocio/i }), "botón Crear Negocio");

      const screenshot = await capture(page, evidenceDir, "03_agregar_negocio_modal");
      details.screenshots.push({ step: "Agregar Negocio modal", path: screenshot });

      const businessNameInput = page.getByLabel(/nombre del negocio/i).first();
      await businessNameInput.fill("Negocio Prueba Automatización");
      await clickAndWait(page.getByRole("button", { name: /cancelar/i }).first(), page);
    });

    await step("Administrar Negocios view", async () => {
      if (!(await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false))) {
        const miNegocio = await findClickableByText(page, /mi negocio/i);
        await clickAndWait(miNegocio, page);
      }

      const administrar = await findClickableByText(page, /administrar negocios/i);
      await clickAndWait(administrar, page);

      await assertVisible(page, page.getByText(/informaci[oó]n general/i), "sección Información General");
      await assertVisible(page, page.getByText(/detalles de la cuenta/i), "sección Detalles de la Cuenta");
      await assertVisible(page, page.getByText(/tus negocios/i), "sección Tus Negocios");
      await assertVisible(page, page.getByText(/secci[oó]n legal/i), "sección Legal");

      const screenshot = await capture(page, evidenceDir, "04_administrar_negocios_view", true);
      details.screenshots.push({ step: "Administrar Negocios view", path: screenshot });
    });

    await step("Información General", async () => {
      await waitForAnyVisible(
        page,
        [page.getByText(/nombre/i), page.getByText(/usuario/i), page.getByText(/perfil/i)],
        "nombre de usuario visible"
      );
      await waitForAnyVisible(
        page,
        [page.getByText(/@/), page.getByText(/correo|email/i)],
        "email de usuario visible"
      );
      await waitForAnyVisible(
        page,
        [page.getByText(/plan/i), page.getByText(/business plan/i)],
        "texto de plan visible"
      );
      await assertVisible(page, page.getByText(/business plan/i), "texto BUSINESS PLAN");
      await assertVisible(page, page.getByRole("button", { name: /cambiar plan/i }), "botón Cambiar Plan");
    });

    await step("Detalles de la Cuenta", async () => {
      await assertVisible(page, page.getByText(/cuenta creada/i), "campo Cuenta creada");
      await assertVisible(page, page.getByText(/estado activo/i), "campo Estado activo");
      await assertVisible(page, page.getByText(/idioma seleccionado/i), "campo Idioma seleccionado");
    });

    await step("Tus Negocios", async () => {
      await assertVisible(page, page.getByText(/tus negocios/i), "sección Tus Negocios");
      await assertVisible(page, page.getByText(/tienes 2 de 3 negocios/i), "contador de negocios");
      await assertVisible(page, page.getByRole("button", { name: /agregar negocio/i }), "botón Agregar Negocio");
    });

    await step("Términos y Condiciones", async () => {
      const legalResult = await validateLegalLink({
        page,
        context,
        evidenceDir,
        linkTextRegex: /t[eé]rminos y condiciones/i,
        headingRegex: /t[eé]rminos y condiciones/i,
        screenshotLabel: "05_terminos_y_condiciones",
      });
      details.urls["Términos y Condiciones"] = legalResult.url;
      details.screenshots.push({ step: "Términos y Condiciones", path: legalResult.screenshot });
    });

    await step("Política de Privacidad", async () => {
      const legalResult = await validateLegalLink({
        page,
        context,
        evidenceDir,
        linkTextRegex: /pol[ií]tica de privacidad/i,
        headingRegex: /pol[ií]tica de privacidad/i,
        screenshotLabel: "06_politica_de_privacidad",
      });
      details.urls["Política de Privacidad"] = legalResult.url;
      details.screenshots.push({ step: "Política de Privacidad", path: legalResult.screenshot });
    });
  } catch (error) {
    for (const field of REPORT_FIELDS) {
      if (report[field] === "NOT RUN") {
        report[field] = "FAIL - blocked by previous step failure";
      }
    }
    console.error(`Execution halted: ${error.message}`);
  } finally {
    const finalReport = {
      name: "saleads_mi_negocio_full_test",
      loginUrl: LOGIN_URL,
      executedAt: new Date().toISOString(),
      report,
      evidenceDir,
      details,
    };

    const finalReportPath = path.join(evidenceDir, "final_report.json");
    await fs.writeFile(finalReportPath, JSON.stringify(finalReport, null, 2), "utf8");
    console.log(JSON.stringify(finalReport, null, 2));

    await context.close();
    await browser.close();

    const failed = Object.values(report).some((status) => status.startsWith("FAIL"));
    process.exitCode = failed ? 1 : 0;
  }
}

main().catch((error) => {
  console.error("Fatal error running saleads_mi_negocio_full_test:", error);
  process.exit(1);
});
