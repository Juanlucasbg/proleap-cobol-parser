import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { chromium } from "playwright";

const TEST_NAME = "saleads_mi_negocio_full_test";
const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
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

const now = new Date();
const runId = now.toISOString().replaceAll(":", "-");
const artifactsDir = path.resolve(
  process.env.SALEADS_ARTIFACTS_DIR ?? `artifacts/${TEST_NAME}/${runId}`,
);
const screenshotsDir = path.join(artifactsDir, "screenshots");

const statusByField = Object.fromEntries(
  REPORT_FIELDS.map((fieldName) => [
    fieldName,
    {
      status: "FAIL",
      details: "Not executed",
      evidence: [],
      metadata: {},
    },
  ]),
);

const report = {
  name: TEST_NAME,
  startedAt: now.toISOString(),
  environment: {
    loginUrl: process.env.SALEADS_LOGIN_URL ?? null,
    headless: process.env.HEADLESS ?? "true",
    locale: process.env.SALEADS_LOCALE ?? "es-ES",
  },
  steps: [],
};

let browserContext;
let appPage;

await fs.mkdir(screenshotsDir, { recursive: true });

try {
  browserContext = await chromium.launchPersistentContext(
    path.resolve(process.env.SALEADS_USER_DATA_DIR ?? ".saleads-browser-profile"),
    {
      channel: process.env.PLAYWRIGHT_BROWSER_CHANNEL,
      headless: process.env.HEADLESS !== "false",
      locale: process.env.SALEADS_LOCALE ?? "es-ES",
      viewport: { width: 1440, height: 900 },
    },
  );

  appPage = browserContext.pages()[0] ?? (await browserContext.newPage());

  if (process.env.SALEADS_LOGIN_URL) {
    await appPage.goto(process.env.SALEADS_LOGIN_URL, {
      waitUntil: "domcontentloaded",
    });
  } else if (appPage.url().startsWith("about:blank")) {
    throw new Error(
      "SALEADS_LOGIN_URL is required unless the browser is already on the SaleADS login screen.",
    );
  }

  await waitForUi(appPage);

  await runStep("Login", async () => {
    const loginButton = await findVisibleByText(appPage, [
      "Sign in with Google",
      "Iniciar sesión con Google",
      "Iniciar Sesión con Google",
      "Continuar con Google",
      "Google",
    ]);

    if (!loginButton) {
      throw new Error(
        "No se encontró el botón de login con Google usando texto visible.",
      );
    }

    const maybePopup = browserContext
      .waitForEvent("page", { timeout: 7_000 })
      .catch(() => null);

    await loginButton.click();
    await waitForUi(appPage);

    const popupPage = await maybePopup;
    if (popupPage) {
      await popupPage.waitForLoadState("domcontentloaded");
      await maybeSelectGoogleAccount(popupPage, GOOGLE_ACCOUNT_EMAIL);
      await waitForUi(popupPage);
    } else {
      await maybeSelectGoogleAccount(appPage, GOOGLE_ACCOUNT_EMAIL);
    }

    appPage = await waitForSidebarPage(browserContext, 45_000);

    if (!appPage) {
      throw new Error("No fue posible confirmar la interfaz principal.");
    }

    await ensureVisible(
      appPage,
      appPage.locator("aside, nav").first(),
      "No se detectó barra lateral visible.",
    );
    await ensureVisible(
      appPage,
      appPage.getByText(/Negocio|Mi Negocio/i).first(),
      "No se detectó navegación de Negocio en la barra lateral.",
    );

    const screenshot = await captureShot(appPage, "01-dashboard-loaded");
    return {
      details: "Interfaz principal cargada y barra lateral visible.",
      evidence: [screenshot],
    };
  });

  await runStep("Mi Negocio menu", async () => {
    await openMiNegocioMenu(appPage);

    const agregarNegocio = appPage.getByText("Agregar Negocio", { exact: true });
    const administrarNegocios = appPage.getByText("Administrar Negocios", {
      exact: true,
    });

    await ensureVisible(
      appPage,
      agregarNegocio,
      "No se visualiza la opción 'Agregar Negocio'.",
    );
    await ensureVisible(
      appPage,
      administrarNegocios,
      "No se visualiza la opción 'Administrar Negocios'.",
    );

    const screenshot = await captureShot(appPage, "02-mi-negocio-expanded");
    return {
      details: "Menú Mi Negocio expandido con opciones esperadas.",
      evidence: [screenshot],
    };
  });

  await runStep("Agregar Negocio modal", async () => {
    const addOption = await findVisibleByText(appPage, ["Agregar Negocio"]);
    if (!addOption) {
      throw new Error("No se encontró 'Agregar Negocio' para abrir modal.");
    }

    await addOption.click();
    await waitForUi(appPage);

    const modalTitle = appPage.getByText("Crear Nuevo Negocio", { exact: true });
    await ensureVisible(
      appPage,
      modalTitle,
      "No apareció el modal 'Crear Nuevo Negocio'.",
    );

    const businessNameField = appPage.getByLabel("Nombre del Negocio", {
      exact: false,
    });
    await ensureVisible(
      appPage,
      businessNameField,
      "No se encontró campo 'Nombre del Negocio'.",
    );
    await ensureVisible(
      appPage,
      appPage.getByText("Tienes 2 de 3 negocios", { exact: false }),
      "No se encontró el texto 'Tienes 2 de 3 negocios'.",
    );
    await ensureVisible(
      appPage,
      appPage.getByRole("button", { name: "Cancelar", exact: true }),
      "No se encontró botón 'Cancelar'.",
    );
    await ensureVisible(
      appPage,
      appPage.getByRole("button", { name: "Crear Negocio", exact: true }),
      "No se encontró botón 'Crear Negocio'.",
    );

    await businessNameField.fill("Negocio Prueba Automatización");
    await appPage.getByRole("button", { name: "Cancelar", exact: true }).click();
    await waitForUi(appPage);

    const screenshot = await captureShot(appPage, "03-agregar-negocio-modal");
    return {
      details: "Modal validado con campos, texto de límite y botones.",
      evidence: [screenshot],
    };
  });

  await runStep("Administrar Negocios view", async () => {
    await openMiNegocioMenu(appPage);

    const manageOption = await findVisibleByText(appPage, ["Administrar Negocios"]);
    if (!manageOption) {
      throw new Error("No se encontró 'Administrar Negocios'.");
    }

    await manageOption.click();
    await waitForUi(appPage);

    await ensureVisible(
      appPage,
      appPage.getByText("Información General", { exact: true }),
      "No se encontró sección 'Información General'.",
    );
    await ensureVisible(
      appPage,
      appPage.getByText("Detalles de la Cuenta", { exact: true }),
      "No se encontró sección 'Detalles de la Cuenta'.",
    );
    await ensureVisible(
      appPage,
      appPage.getByText("Tus Negocios", { exact: true }),
      "No se encontró sección 'Tus Negocios'.",
    );
    await ensureVisible(
      appPage,
      appPage.getByText("Sección Legal", { exact: true }),
      "No se encontró sección 'Sección Legal'.",
    );

    const screenshot = await captureShot(appPage, "04-administrar-negocios-view", {
      fullPage: true,
    });
    return {
      details: "Vista de administración de cuenta cargada correctamente.",
      evidence: [screenshot],
    };
  });

  await runStep("Información General", async () => {
    await ensureVisible(
      appPage,
      appPage.getByText(/BUSINESS PLAN/i),
      "No se encontró texto 'BUSINESS PLAN'.",
    );
    await ensureVisible(
      appPage,
      appPage.getByRole("button", { name: "Cambiar Plan", exact: true }),
      "No se encontró botón 'Cambiar Plan'.",
    );
    await ensureVisible(
      appPage,
      appPage.getByText(/@/).first(),
      "No se detectó email visible en información general.",
    );

    const visibleHeader = await appPage
      .locator("h1, h2, h3, p, span, strong")
      .first()
      .textContent();

    return {
      details: "Se validaron email visible, plan y acción de cambio.",
      metadata: { topVisibleText: visibleHeader?.trim() ?? "" },
    };
  });

  await runStep("Detalles de la Cuenta", async () => {
    await ensureVisible(
      appPage,
      appPage.getByText("Cuenta creada", { exact: false }),
      "No se encontró texto 'Cuenta creada'.",
    );
    await ensureVisible(
      appPage,
      appPage.getByText("Estado activo", { exact: false }),
      "No se encontró texto 'Estado activo'.",
    );
    await ensureVisible(
      appPage,
      appPage.getByText("Idioma seleccionado", { exact: false }),
      "No se encontró texto 'Idioma seleccionado'.",
    );

    return { details: "Etiquetas de estado y configuración visibles." };
  });

  await runStep("Tus Negocios", async () => {
    await ensureVisible(
      appPage,
      appPage.getByText("Tus Negocios", { exact: true }),
      "No se encontró título 'Tus Negocios'.",
    );
    await ensureVisible(
      appPage,
      appPage.getByRole("button", { name: "Agregar Negocio", exact: true }),
      "No se encontró botón 'Agregar Negocio' en la sección.",
    );
    await ensureVisible(
      appPage,
      appPage.getByText("Tienes 2 de 3 negocios", { exact: false }),
      "No se encontró texto de límite de negocios.",
    );

    return { details: "Sección y capacidad de negocios validadas." };
  });

  await runStep("Términos y Condiciones", async () => {
    const validation = await validateLegalLink({
      page: appPage,
      context: browserContext,
      linkText: "Términos y Condiciones",
      expectedHeading: "Términos y Condiciones",
      screenshotName: "05-terminos-y-condiciones",
    });
    return {
      details: "Página legal de términos validada.",
      evidence: [validation.screenshot],
      metadata: { finalUrl: validation.finalUrl },
    };
  });

  await runStep("Política de Privacidad", async () => {
    const validation = await validateLegalLink({
      page: appPage,
      context: browserContext,
      linkText: "Política de Privacidad",
      expectedHeading: "Política de Privacidad",
      screenshotName: "06-politica-de-privacidad",
    });
    return {
      details: "Página legal de privacidad validada.",
      evidence: [validation.screenshot],
      metadata: { finalUrl: validation.finalUrl },
    };
  });
} catch (error) {
  report.globalError = error instanceof Error ? error.message : String(error);
} finally {
  if (browserContext) {
    await browserContext.close();
  }
}

report.finishedAt = new Date().toISOString();
report.summary = REPORT_FIELDS.map((fieldName) => ({
  field: fieldName,
  status: statusByField[fieldName].status,
}));

await fs.writeFile(
  path.join(artifactsDir, "final-report.json"),
  `${JSON.stringify(
    {
      report,
      statusByField,
    },
    null,
    2,
  )}\n`,
);

const markdownLines = [
  "# SaleADS Mi Negocio Full Test Report",
  "",
  `- Test Name: ${TEST_NAME}`,
  `- Started At: ${report.startedAt}`,
  `- Finished At: ${report.finishedAt}`,
  "",
  "| Field | Status | Details |",
  "| --- | --- | --- |",
];

for (const fieldName of REPORT_FIELDS) {
  const fieldResult = statusByField[fieldName];
  markdownLines.push(
    `| ${fieldName} | ${fieldResult.status} | ${escapePipes(fieldResult.details)} |`,
  );
}

markdownLines.push("", "## Evidence", "");
for (const fieldName of REPORT_FIELDS) {
  const fieldResult = statusByField[fieldName];
  if (fieldResult.evidence.length === 0) {
    continue;
  }
  markdownLines.push(`- **${fieldName}**`);
  for (const evidencePath of fieldResult.evidence) {
    markdownLines.push(`  - ${evidencePath}`);
  }
}

markdownLines.push("", "## URLs", "");
for (const fieldName of REPORT_FIELDS) {
  const fieldResult = statusByField[fieldName];
  if (!fieldResult.metadata?.finalUrl) {
    continue;
  }
  markdownLines.push(`- ${fieldName}: ${fieldResult.metadata.finalUrl}`);
}

await fs.writeFile(
  path.join(artifactsDir, "final-report.md"),
  `${markdownLines.join("\n")}\n`,
);

console.log(JSON.stringify(statusByField, null, 2));
console.log(`Artifacts generated at: ${artifactsDir}`);

if (Object.values(statusByField).some((result) => result.status !== "PASS")) {
  process.exitCode = 1;
}

async function runStep(fieldName, action) {
  const stepEntry = {
    field: fieldName,
    startedAt: new Date().toISOString(),
  };

  try {
    const result = (await action()) ?? {};
    const details = result.details ?? "Validation completed.";
    const evidence = result.evidence ?? [];
    const metadata = result.metadata ?? {};

    statusByField[fieldName] = {
      status: "PASS",
      details,
      evidence,
      metadata,
    };
    stepEntry.status = "PASS";
    stepEntry.details = details;
    stepEntry.evidence = evidence;
    stepEntry.metadata = metadata;
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    statusByField[fieldName] = {
      status: "FAIL",
      details: message,
      evidence: [],
      metadata: {},
    };
    stepEntry.status = "FAIL";
    stepEntry.details = message;
  } finally {
    stepEntry.finishedAt = new Date().toISOString();
    report.steps.push(stepEntry);
  }
}

async function validateLegalLink({
  page,
  context,
  linkText,
  expectedHeading,
  screenshotName,
}) {
  const link = await findVisibleByText(page, [linkText]);
  if (!link) {
    throw new Error(`No se encontró el enlace '${linkText}'.`);
  }

  const popupPromise = context
    .waitForEvent("page", { timeout: 7_000 })
    .catch(() => null);

  await link.click();
  await waitForUi(page);

  const maybeNewPage = await popupPromise;
  const legalPage = maybeNewPage ?? page;
  await legalPage.waitForLoadState("domcontentloaded");
  await waitForUi(legalPage);

  await ensureVisible(
    legalPage,
    legalPage.getByText(expectedHeading, { exact: false }),
    `No se encontró encabezado '${expectedHeading}'.`,
  );

  const legalText = await legalPage.locator("main, article, body").first().innerText();
  if (!legalText || legalText.trim().length < 60) {
    throw new Error(`No se encontró contenido legal suficiente en '${linkText}'.`);
  }

  const screenshot = await captureShot(legalPage, screenshotName, { fullPage: true });
  const finalUrl = legalPage.url();

  if (maybeNewPage) {
    await maybeNewPage.close();
    await waitForUi(page);
  } else {
    await legalPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
    await waitForUi(page);
  }

  return { screenshot, finalUrl };
}

async function waitForSidebarPage(context, timeoutMs) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    for (const page of context.pages()) {
      if (page.isClosed()) {
        continue;
      }

      const hasSidebar = await page.locator("aside, nav").first().isVisible().catch(() => false);
      const hasNegocioText = await page
        .getByText(/Negocio|Mi Negocio/i)
        .first()
        .isVisible()
        .catch(() => false);

      if (hasSidebar && hasNegocioText) {
        return page;
      }
    }
    await pause(1_000);
  }

  return null;
}

async function maybeSelectGoogleAccount(page, email) {
  const account = page.getByText(email, { exact: true });
  const exists = await account.first().isVisible().catch(() => false);
  if (!exists) {
    return false;
  }
  await account.first().click();
  await waitForUi(page);
  return true;
}

async function openMiNegocioMenu(page) {
  const negocio = await findVisibleByText(page, ["Negocio"]);
  if (negocio) {
    await negocio.click();
    await waitForUi(page);
  }

  const miNegocio = await findVisibleByText(page, ["Mi Negocio"]);
  if (miNegocio) {
    await miNegocio.click();
    await waitForUi(page);
  }
}

async function ensureVisible(page, locator, errorMessage) {
  await locator.first().waitFor({ state: "visible", timeout: 20_000 }).catch(() => {
    throw new Error(errorMessage);
  });
  await waitForUi(page);
}

async function findVisibleByText(page, textCandidates) {
  const candidateFactories = [];

  for (const text of textCandidates) {
    const normalized = new RegExp(escapeRegExp(text), "i");
    candidateFactories.push(() => page.getByRole("button", { name: normalized }));
    candidateFactories.push(() => page.getByRole("link", { name: normalized }));
    candidateFactories.push(() => page.getByRole("menuitem", { name: normalized }));
    candidateFactories.push(() => page.getByRole("tab", { name: normalized }));
    candidateFactories.push(() => page.getByText(normalized, { exact: false }));
  }

  for (const build of candidateFactories) {
    const locator = build().first();
    const isVisible = await locator.isVisible().catch(() => false);
    if (isVisible) {
      return locator;
    }
  }

  return null;
}

async function captureShot(page, checkpointName, options = {}) {
  const screenshotPath = path.join(
    screenshotsDir,
    `${String(report.steps.length + 1).padStart(2, "0")}-${checkpointName}.png`,
  );
  await page.screenshot({
    path: screenshotPath,
    fullPage: options.fullPage ?? false,
  });
  return screenshotPath;
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => null);
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => null);
  await page.waitForTimeout(700).catch(() => null);
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function escapePipes(value) {
  return value.replaceAll("|", "\\|");
}

function pause(ms) {
  return new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
}
