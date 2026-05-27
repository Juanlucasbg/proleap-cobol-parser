import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright";

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

const GOOGLE_ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const ARTIFACTS_DIR = path.resolve(
  process.env.SALEADS_ARTIFACTS_DIR || "artifacts/saleads-mi-negocio",
);
const HEADLESS = parseBoolean(process.env.HEADLESS, true);

function parseBoolean(value, defaultValue) {
  if (value === undefined) return defaultValue;
  return ["1", "true", "yes", "on"].includes(String(value).toLowerCase());
}

function createFieldResult() {
  return {
    status: "PASS",
    checks: [],
    errors: [],
    evidence: [],
  };
}

const report = Object.fromEntries(
  REPORT_FIELDS.map((field) => [field, createFieldResult()]),
);
const metadata = {
  startedAt: new Date().toISOString(),
  googleAccountEmail: GOOGLE_ACCOUNT_EMAIL,
  urls: {},
  artifactsDir: ARTIFACTS_DIR,
};

function recordCheck(field, description, passed, details = "") {
  const target = report[field];
  target.checks.push({ description, passed, details });
  if (!passed) {
    target.status = "FAIL";
    target.errors.push(details || description);
  }
}

function recordPresence(field, description, present, missingDetails) {
  recordCheck(
    field,
    description,
    Boolean(present),
    present ? "" : missingDetails,
  );
}

function finalizeFieldStatuses() {
  for (const field of REPORT_FIELDS) {
    const target = report[field];
    if (target.checks.length === 0) {
      target.status = "FAIL";
      if (!target.errors.includes("Step not executed")) {
        target.errors.push("Step not executed");
      }
      continue;
    }

    const hasFailures = target.checks.some((check) => !check.passed);
    target.status = hasFailures ? "FAIL" : "PASS";
  }
}

async function ensureArtifactsDir() {
  await fs.mkdir(ARTIFACTS_DIR, { recursive: true });
}

async function waitForUi(page, timeout = 15000) {
  await page.waitForLoadState("domcontentloaded", { timeout }).catch(() => {});
  await page.waitForTimeout(1000);
}

async function findVisible(page, candidates, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      const locator = candidate.first();
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }
    await page.waitForTimeout(250);
  }
  return null;
}

async function screenshot(page, field, filename, options = {}) {
  const targetPath = path.join(ARTIFACTS_DIR, filename);
  await page.screenshot({ path: targetPath, ...options });
  report[field].evidence.push(targetPath);
}

async function clickAndWait(locator, page) {
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click();
  await waitForUi(page);
}

async function validateVisibleText(page, field, label, regex, timeoutMs = 12000) {
  const locator = page.getByText(regex).first();
  try {
    await locator.waitFor({ state: "visible", timeout: timeoutMs });
    recordCheck(field, label, true);
    return locator;
  } catch (error) {
    recordCheck(field, label, false, `${label} no visible (${error.message})`);
    return null;
  }
}

async function validateEmailVisible(page, field) {
  const emailRegex = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;
  const locator = page.getByText(emailRegex).first();
  try {
    await locator.waitFor({ state: "visible", timeout: 12000 });
    recordCheck(field, "User email is visible", true);
  } catch (error) {
    recordCheck(field, "User email is visible", false, error.message);
  }
}

async function validateLikelyUserName(page, field) {
  const nameFound = await page.evaluate(() => {
    const sections = Array.from(
      document.querySelectorAll("section, article, main, div"),
    ).filter((element) =>
      /informaci[oó]n general/i.test(element.textContent || ""),
    );

    const nameRegex = /^[A-Za-zÀ-ÿ'`.-]+(?:\s+[A-Za-zÀ-ÿ'`.-]+)+$/;
    for (const section of sections) {
      const lines = (section.innerText || "")
        .split("\n")
        .map((line) => line.trim())
        .filter(Boolean)
        .slice(0, 50);

      if (
        lines.some(
          (line) => line.length >= 4 && line.length <= 80 && nameRegex.test(line),
        )
      ) {
        return true;
      }
    }
    return false;
  });

  recordPresence(
    field,
    "User name is visible",
    nameFound,
    "No se detectó texto con formato de nombre en Información General",
  );
}

async function openLegalDocument({
  appPage,
  field,
  linkLabel,
  headingRegex,
  screenshotFile,
  metadataKey,
}) {
  const context = appPage.context();
  const legalSection = await findVisible(appPage, [
    appPage.locator("section, article, div").filter({ hasText: /secci[oó]n legal/i }),
  ]);

  if (!legalSection) {
    recordCheck(
      field,
      "Sección Legal container is visible",
      false,
      "No se encontró la Sección Legal en la vista de cuenta",
    );
    return;
  }

  const link = await findVisible(appPage, [
    legalSection.getByRole("link", { name: new RegExp(linkLabel, "i") }),
    legalSection.getByRole("button", { name: new RegExp(linkLabel, "i") }),
    legalSection.getByText(new RegExp(linkLabel, "i")),
  ]);

  if (!link) {
    recordCheck(field, `${linkLabel} link is visible`, false, "No se encontró link");
    return;
  }

  recordCheck(field, `${linkLabel} link is visible`, true);

  let legalPage = appPage;
  let popup = null;

  try {
    [popup] = await Promise.all([
      context.waitForEvent("page", { timeout: 8000 }).catch(() => null),
      clickAndWait(link, appPage),
    ]);
  } catch (error) {
    recordCheck(field, `Open ${linkLabel}`, false, error.message);
  }

  if (popup) {
    legalPage = popup;
    await legalPage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
    await legalPage.waitForTimeout(1000);
  }

  const heading = await findVisible(legalPage, [
    legalPage.getByRole("heading", { name: headingRegex }),
    legalPage.getByText(headingRegex),
  ]);

  recordPresence(
    field,
    `Heading ${headingRegex} is visible`,
    heading,
    `No se encontró heading ${headingRegex}`,
  );

  const legalContentFound = await legalPage.evaluate(() => {
    const paragraphLike = Array.from(
      document.querySelectorAll("p, li, article, section, div"),
    )
      .map((el) => (el.textContent || "").trim())
      .filter((text) => text.length > 100);
    return paragraphLike.length > 0;
  });

  recordPresence(
    field,
    "Legal content text is visible",
    legalContentFound,
    "No se detectó contenido legal extenso",
  );

  metadata.urls[metadataKey] = legalPage.url();
  await screenshot(legalPage, field, screenshotFile, { fullPage: true });

  if (popup) {
    await popup.close().catch(() => {});
    await appPage.bringToFront().catch(() => {});
    await waitForUi(appPage);
    return;
  }

  await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
  await waitForUi(appPage);
}

async function run() {
  await ensureArtifactsDir();

  let browser;
  let context;
  let page;
  let closeBrowser = true;

  const cdpUrl = process.env.SALEADS_CDP_URL;
  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL;

  try {
    if (cdpUrl) {
      browser = await chromium.connectOverCDP(cdpUrl);
      context = browser.contexts()[0] || (await browser.newContext());
      page = context.pages()[0] || (await context.newPage());
      closeBrowser = false;
    } else {
      browser = await chromium.launch({ headless: HEADLESS });
      context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
      page = await context.newPage();

      if (!loginUrl) {
        throw new Error(
          "Falta SALEADS_LOGIN_URL o SALEADS_CDP_URL. El flujo no puede iniciar desde una URL fija.",
        );
      }

      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    metadata.urls.initial = page.url();

    // Step 1: Login with Google
    const loginButton = await findVisible(page, [
      page.getByRole("button", { name: /google|sign in|iniciar sesi[oó]n|login/i }),
      page.getByRole("link", { name: /google|sign in|iniciar sesi[oó]n|login/i }),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i),
    ]);

    recordPresence(
      "Login",
      "Login button or Google sign in is visible",
      loginButton,
      "No se encontró botón de login con Google",
    );

    if (loginButton) {
      await clickAndWait(loginButton, page);
    }

    const googleAccount = await findVisible(
      page,
      [
        page.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")),
        page.getByRole("link", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
        page.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
      ],
      8000,
    );

    if (googleAccount) {
      await clickAndWait(googleAccount, page);
      recordCheck("Login", `Google account ${GOOGLE_ACCOUNT_EMAIL} selected`, true);
    }

    const sidebar = await findVisible(page, [
      page.locator("aside").filter({ hasText: /mi negocio|administrar negocios|agregar negocio/i }),
      page.locator("nav").filter({ hasText: /mi negocio|administrar negocios|agregar negocio/i }),
    ]);

    const appMain = await findVisible(page, [
      page.getByText(/mi negocio|administrar negocios|informaci[oó]n general|tus negocios/i),
      page.locator("main").filter({ hasText: /mi negocio|informaci[oó]n general|tus negocios/i }),
    ]);

    recordPresence(
      "Login",
      "Main application interface appears",
      appMain,
      "No se detectó la interfaz principal",
    );
    recordPresence(
      "Login",
      "Left sidebar navigation is visible",
      sidebar,
      "No se detectó la barra lateral izquierda",
    );
    await screenshot(page, "Login", "01-dashboard.png");

    // Step 2: Open Mi Negocio menu
    const negocioSection = await findVisible(page, [
      page.getByRole("button", { name: /^negocio$/i }),
      page.getByRole("link", { name: /^negocio$/i }),
      page.getByText(/^negocio$/i),
    ]);

    if (negocioSection) {
      await clickAndWait(negocioSection, page);
      recordCheck("Mi Negocio menu", "Negocio section found and clicked", true);
    } else {
      recordCheck("Mi Negocio menu", "Negocio section found and clicked", false);
    }

    const miNegocio = await findVisible(page, [
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i),
    ]);

    if (miNegocio) {
      await clickAndWait(miNegocio, page);
      recordCheck("Mi Negocio menu", "Mi Negocio option clicked", true);
    } else {
      recordCheck("Mi Negocio menu", "Mi Negocio option clicked", false);
    }

    await validateVisibleText(
      page,
      "Mi Negocio menu",
      "Agregar Negocio is visible",
      /agregar negocio/i,
    );
    await validateVisibleText(
      page,
      "Mi Negocio menu",
      "Administrar Negocios is visible",
      /administrar negocios/i,
    );
    await screenshot(page, "Mi Negocio menu", "02-mi-negocio-menu-expanded.png");

    // Step 3: Validate Agregar Negocio modal
    const agregarNegocioMenuOption = await findVisible(page, [
      page.getByRole("button", { name: /agregar negocio/i }),
      page.getByRole("link", { name: /agregar negocio/i }),
      page.getByText(/agregar negocio/i),
    ]);

    if (agregarNegocioMenuOption) {
      await clickAndWait(agregarNegocioMenuOption, page);
      recordCheck("Agregar Negocio modal", "Agregar Negocio clicked", true);
    } else {
      recordCheck("Agregar Negocio modal", "Agregar Negocio clicked", false);
    }

    await validateVisibleText(
      page,
      "Agregar Negocio modal",
      "Modal title Crear Nuevo Negocio is visible",
      /crear nuevo negocio/i,
    );
    await validateVisibleText(
      page,
      "Agregar Negocio modal",
      "Nombre del Negocio input exists",
      /nombre del negocio/i,
    );
    await validateVisibleText(
      page,
      "Agregar Negocio modal",
      "Tienes 2 de 3 negocios text is visible",
      /tienes\s*2\s*de\s*3\s*negocios/i,
    );
    await validateVisibleText(
      page,
      "Agregar Negocio modal",
      "Cancelar button is present",
      /^cancelar$/i,
    );
    await validateVisibleText(
      page,
      "Agregar Negocio modal",
      "Crear Negocio button is present",
      /crear negocio/i,
    );
    await screenshot(page, "Agregar Negocio modal", "03-agregar-negocio-modal.png");

    const nombreInput = await findVisible(page, [
      page.getByLabel(/nombre del negocio/i),
      page.getByPlaceholder(/nombre del negocio/i),
      page.locator("input").filter({ hasText: /nombre del negocio/i }),
      page.locator("input[name*='negocio'], input[id*='negocio']"),
    ]);

    if (nombreInput) {
      await nombreInput.fill("Negocio Prueba Automatización");
      recordCheck(
        "Agregar Negocio modal",
        "Optional input typing completed",
        true,
      );
    }

    const cancelarButton = await findVisible(page, [
      page.getByRole("button", { name: /^cancelar$/i }),
      page.getByText(/^cancelar$/i),
    ]);
    if (cancelarButton) {
      await clickAndWait(cancelarButton, page);
      recordCheck("Agregar Negocio modal", "Modal closed with Cancelar", true);
    }

    // Step 4: Open Administrar Negocios
    const miNegocioForAdmin = await findVisible(page, [
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i),
    ]);
    if (miNegocioForAdmin) {
      await clickAndWait(miNegocioForAdmin, page);
    }

    const administrarNegocios = await findVisible(page, [
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i),
    ]);

    if (administrarNegocios) {
      await clickAndWait(administrarNegocios, page);
      recordCheck("Administrar Negocios view", "Administrar Negocios clicked", true);
    } else {
      recordCheck("Administrar Negocios view", "Administrar Negocios clicked", false);
    }

    await validateVisibleText(
      page,
      "Administrar Negocios view",
      "Información General section exists",
      /informaci[oó]n general/i,
    );
    await validateVisibleText(
      page,
      "Administrar Negocios view",
      "Detalles de la Cuenta section exists",
      /detalles de la cuenta/i,
    );
    await validateVisibleText(
      page,
      "Administrar Negocios view",
      "Tus Negocios section exists",
      /tus negocios/i,
    );
    await validateVisibleText(
      page,
      "Administrar Negocios view",
      "Sección Legal section exists",
      /secci[oó]n legal/i,
    );
    await screenshot(page, "Administrar Negocios view", "04-administrar-negocios.png", {
      fullPage: true,
    });

    // Step 5: Validate Información General
    await validateLikelyUserName(page, "Información General");
    await validateEmailVisible(page, "Información General");
    await validateVisibleText(
      page,
      "Información General",
      "BUSINESS PLAN is visible",
      /business plan/i,
    );
    await validateVisibleText(
      page,
      "Información General",
      "Cambiar Plan button is visible",
      /cambiar plan/i,
    );

    // Step 6: Validate Detalles de la Cuenta
    await validateVisibleText(
      page,
      "Detalles de la Cuenta",
      "Cuenta creada is visible",
      /cuenta creada/i,
    );
    await validateVisibleText(
      page,
      "Detalles de la Cuenta",
      "Estado activo is visible",
      /estado activo/i,
    );
    await validateVisibleText(
      page,
      "Detalles de la Cuenta",
      "Idioma seleccionado is visible",
      /idioma seleccionado/i,
    );

    // Step 7: Validate Tus Negocios
    await validateVisibleText(
      page,
      "Tus Negocios",
      "Business list is visible",
      /tus negocios/i,
    );
    await validateVisibleText(
      page,
      "Tus Negocios",
      "Agregar Negocio button exists",
      /agregar negocio/i,
    );
    await validateVisibleText(
      page,
      "Tus Negocios",
      "Tienes 2 de 3 negocios text is visible",
      /tienes\s*2\s*de\s*3\s*negocios/i,
    );

    // Step 8: Validate Términos y Condiciones
    await openLegalDocument({
      appPage: page,
      field: "Términos y Condiciones",
      linkLabel: "Términos y Condiciones",
      headingRegex: /t[eé]rminos y condiciones/i,
      screenshotFile: "05-terminos-y-condiciones.png",
      metadataKey: "terminosYCondicionesUrl",
    });

    // Step 9: Validate Política de Privacidad
    await openLegalDocument({
      appPage: page,
      field: "Política de Privacidad",
      linkLabel: "Política de Privacidad",
      headingRegex: /pol[ií]tica de privacidad/i,
      screenshotFile: "06-politica-de-privacidad.png",
      metadataKey: "politicaDePrivacidadUrl",
    });

    metadata.urls.finalAppPage = page.url();
  } finally {
    if (browser && closeBrowser) {
      await browser.close();
    }
  }

  metadata.finishedAt = new Date().toISOString();
  finalizeFieldStatuses();
  const overallPass = REPORT_FIELDS.every((field) => report[field].status === "PASS");

  const finalPayload = {
    name: "saleads_mi_negocio_full_test",
    overallStatus: overallPass ? "PASS" : "FAIL",
    report,
    metadata,
  };

  const reportPath = path.join(ARTIFACTS_DIR, "final-report.json");
  await fs.writeFile(reportPath, `${JSON.stringify(finalPayload, null, 2)}\n`, "utf8");

  console.log(JSON.stringify(finalPayload, null, 2));
  process.exitCode = overallPass ? 0 : 1;
}

run().catch(async (error) => {
  finalizeFieldStatuses();
  const failedPayload = {
    name: "saleads_mi_negocio_full_test",
    overallStatus: "FAIL",
    fatalError: error.message,
    report,
    metadata: {
      ...metadata,
      finishedAt: new Date().toISOString(),
    },
  };

  await ensureArtifactsDir();
  await fs.writeFile(
    path.join(ARTIFACTS_DIR, "final-report.json"),
    `${JSON.stringify(failedPayload, null, 2)}\n`,
    "utf8",
  );
  console.error(JSON.stringify(failedPayload, null, 2));
  process.exitCode = 1;
});
