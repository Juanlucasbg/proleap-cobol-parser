import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const EMAIL_TO_SELECT = "juanlucasbarbiergarzon@gmail.com";
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

const startedAt = new Date().toISOString();
const timestamp = startedAt.replace(/[:.]/g, "-");
const artifactsRoot = path.resolve(
  process.env.ARTIFACTS_DIR || path.join(__dirname, "artifacts", timestamp)
);
const screenshotsDir = path.join(artifactsRoot, "screenshots");
const saleadsUrl = process.env.SALEADS_URL || process.env.BASE_URL || "";
const cdpUrl = process.env.PLAYWRIGHT_CDP_URL || process.env.BROWSER_CDP_URL || "";
const headless = `${process.env.HEADLESS ?? "true"}`.toLowerCase() !== "false";

const stepResults = Object.fromEntries(
  REPORT_FIELDS.map((field) => [field, { status: "FAIL", details: "Not executed yet." }])
);
const evidence = {
  screenshots: [],
  urls: {
    termsAndConditions: null,
    privacyPolicy: null
  }
};

function setResult(field, status, details) {
  stepResults[field] = { status, details };
}

function normalizeName(name) {
  return name.replace(/[^a-zA-Z0-9_-]+/g, "_");
}

async function ensureArtifacts() {
  await fs.mkdir(screenshotsDir, { recursive: true });
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 12000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {});
  await page.waitForTimeout(700);
}

async function screenshot(page, label, fullPage = false) {
  const fileName = `${Date.now()}_${normalizeName(label)}.png`;
  const outputPath = path.join(screenshotsDir, fileName);
  await page.screenshot({ path: outputPath, fullPage });
  evidence.screenshots.push(outputPath);
  return outputPath;
}

async function isVisible(locator, timeout = 2500) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function isAppShellVisible(page, timeout = 2500) {
  return (
    (await isVisible(page.locator("aside"), timeout)) ||
    (await isVisible(page.getByRole("navigation"), timeout)) ||
    (await isVisible(page.getByText(/Mi Negocio|Negocio|Dashboard|Inicio/i), timeout))
  );
}

async function clickFirstVisible(page, locators, label, required = true) {
  for (const locator of locators) {
    if (await isVisible(locator)) {
      await locator.first().click();
      await waitForUi(page);
      return true;
    }
  }

  if (required) {
    throw new Error(`Could not find visible element for: ${label}`);
  }

  return false;
}

async function assertAnyVisible(label, locators, timeout = 10000) {
  for (const locator of locators) {
    if (await isVisible(locator, timeout)) {
      return true;
    }
  }

  throw new Error(`Validation failed. Expected visible: ${label}`);
}

async function runStep(field, fn) {
  try {
    await fn();
    setResult(field, "PASS", "Validation succeeded.");
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    setResult(field, "FAIL", message);
    console.error(`[${field}] FAIL: ${message}`);
  }
}

async function clickLegalLinkAndValidate({
  appPage,
  context,
  linkRegex,
  headingRegex,
  screenshotLabel,
  reportField,
  urlField
}) {
  const popupPromise = appPage.waitForEvent("popup", { timeout: 9000 }).catch(() => null);
  const navPromise = appPage
    .waitForNavigation({ waitUntil: "domcontentloaded", timeout: 9000 })
    .catch(() => null);

  await clickFirstVisible(
    appPage,
    [
      appPage.getByRole("link", { name: linkRegex }),
      appPage.getByRole("button", { name: linkRegex }),
      appPage.getByText(linkRegex)
    ],
    `${reportField} link`
  );

  const popup = await popupPromise;
  if (!popup) {
    await navPromise;
  }

  const legalPage = popup || appPage;
  await waitForUi(legalPage);

  await assertAnyVisible(`${reportField} heading`, [
    legalPage.getByRole("heading", { name: headingRegex }),
    legalPage.getByText(headingRegex)
  ]);

  const bodyText = (await legalPage.locator("body").innerText()).trim();
  if (bodyText.length < 120) {
    throw new Error(`${reportField} did not contain enough legal content text.`);
  }

  evidence.urls[urlField] = legalPage.url();
  await screenshot(legalPage, screenshotLabel, true);

  if (popup) {
    await popup.close();
    const appTab = context.pages()[0] || appPage;
    await appTab.bringToFront().catch(() => {});
    await waitForUi(appTab);
  } else {
    await legalPage.goBack({ waitUntil: "domcontentloaded", timeout: 10000 }).catch(() => {});
    await waitForUi(legalPage);
  }
}

async function main() {
  await ensureArtifacts();

  let browser;
  let context;
  let page;

  if (cdpUrl) {
    browser = await chromium.connectOverCDP(cdpUrl);
    context = browser.contexts()[0] || (await browser.newContext());
    page = context.pages()[0] || (await context.newPage());
  } else {
    browser = await chromium.launch({ headless });
    context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    page = await context.newPage();
    if (!saleadsUrl) {
      throw new Error(
        "Set SALEADS_URL/BASE_URL, or provide PLAYWRIGHT_CDP_URL to attach to an already-open SaleADS login page."
      );
    }
    await page.goto(saleadsUrl, { waitUntil: "domcontentloaded", timeout: 30000 });
    await waitForUi(page);
  }

  await runStep("Login", async () => {
    if (await isAppShellVisible(page, 2500)) {
      await screenshot(page, "dashboard_loaded");
      return;
    }

    const popupPromise = page.waitForEvent("popup", { timeout: 9000 }).catch(() => null);

    const directGoogleClicked = await clickFirstVisible(
      page,
      [
        page.getByRole("button", {
          name: /Sign in with Google|Iniciar sesi[oó]n con Google|Continuar con Google|Ingresar con Google/i
        }),
        page.getByRole("link", {
          name: /Sign in with Google|Iniciar sesi[oó]n con Google|Continuar con Google|Ingresar con Google/i
        }),
        page.getByText(
          /Sign in with Google|Iniciar sesi[oó]n con Google|Continuar con Google|Ingresar con Google/i
        )
      ],
      "google login button",
      false
    );

    if (!directGoogleClicked) {
      await clickFirstVisible(
        page,
        [
          page.getByRole("button", {
            name: /Login|Log in|Iniciar sesi[oó]n|Acceder|Entrar/i
          }),
          page.getByRole("link", {
            name: /Login|Log in|Iniciar sesi[oó]n|Acceder|Entrar/i
          })
        ],
        "login button"
      );

      await clickFirstVisible(
        page,
        [
          page.getByRole("button", {
            name: /Google|Sign in with Google|Iniciar sesi[oó]n con Google/i
          }),
          page.getByRole("link", {
            name: /Google|Sign in with Google|Iniciar sesi[oó]n con Google/i
          }),
          page.getByText(/Google|Sign in with Google|Iniciar sesi[oó]n con Google/i)
        ],
        "google provider option"
      );
    }

    const authPopup = await popupPromise;
    const authPage = authPopup || page;
    await waitForUi(authPage);

    await clickFirstVisible(
      authPage,
      [
        authPage.getByText(EMAIL_TO_SELECT, { exact: true }),
        authPage.getByRole("link", { name: new RegExp(EMAIL_TO_SELECT, "i") }),
        authPage.getByRole("button", { name: new RegExp(EMAIL_TO_SELECT, "i") })
      ],
      "google account selector",
      false
    );

    if (authPopup) {
      await authPopup.waitForEvent("close", { timeout: 30000 }).catch(() => {});
    }

    await page.bringToFront().catch(() => {});
    await waitForUi(page);

    await assertAnyVisible("main application interface", [
      page.getByRole("main"),
      page.locator("main"),
      page.getByText(/Dashboard|Inicio|Panel|Negocio|Mi Negocio/i)
    ]);

    await assertAnyVisible("left sidebar navigation", [page.locator("aside"), page.getByRole("navigation")]);

    await screenshot(page, "dashboard_loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    await clickFirstVisible(
      page,
      [
        page.getByRole("button", { name: /^Negocio$/i }),
        page.getByRole("link", { name: /^Negocio$/i }),
        page.getByText(/^Negocio$/i)
      ],
      "Negocio section"
    );

    await clickFirstVisible(
      page,
      [
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i)
      ],
      "Mi Negocio menu"
    );

    await assertAnyVisible("Agregar Negocio option", [
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i)
    ]);

    await assertAnyVisible("Administrar Negocios option", [
      page.getByRole("link", { name: /^Administrar Negocios$/i }),
      page.getByRole("button", { name: /^Administrar Negocios$/i }),
      page.getByText(/^Administrar Negocios$/i)
    ]);

    await screenshot(page, "mi_negocio_menu_expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickFirstVisible(
      page,
      [
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i)
      ],
      "Agregar Negocio option"
    );

    await assertAnyVisible("Crear Nuevo Negocio modal title", [
      page.getByRole("heading", { name: /Crear Nuevo Negocio/i }),
      page.getByText(/Crear Nuevo Negocio/i)
    ]);

    await assertAnyVisible("Nombre del Negocio input", [
      page.getByRole("textbox", { name: /Nombre del Negocio/i }),
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator(
        "input[name*='negocio' i], input[placeholder*='nombre del negocio' i], input[aria-label*='nombre del negocio' i]"
      )
    ]);

    await assertAnyVisible("business limit text", [
      page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i)
    ]);

    await assertAnyVisible("Cancelar button", [
      page.getByRole("button", { name: /^Cancelar$/i }),
      page.getByText(/^Cancelar$/i)
    ]);

    await assertAnyVisible("Crear Negocio button", [
      page.getByRole("button", { name: /^Crear Negocio$/i }),
      page.getByText(/^Crear Negocio$/i)
    ]);

    await screenshot(page, "agregar_negocio_modal");

    const businessNameFieldCandidates = [
      page.getByRole("textbox", { name: /Nombre del Negocio/i }),
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator(
        "input[name*='negocio' i], input[placeholder*='nombre del negocio' i], input[aria-label*='nombre del negocio' i]"
      )
    ];

    for (const field of businessNameFieldCandidates) {
      if (await isVisible(field)) {
        await field.fill("Negocio Prueba Automatización");
        break;
      }
    }

    await clickFirstVisible(
      page,
      [
        page.getByRole("button", { name: /^Cancelar$/i }),
        page.getByText(/^Cancelar$/i)
      ],
      "Cancelar button on modal"
    );
  });

  await runStep("Administrar Negocios view", async () => {
    await clickFirstVisible(
      page,
      [
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i)
      ],
      "Mi Negocio menu (re-expand if needed)",
      false
    );

    await clickFirstVisible(
      page,
      [
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i)
      ],
      "Administrar Negocios option"
    );

    await assertAnyVisible("Información General section", [
      page.getByRole("heading", { name: /Informaci[oó]n General/i }),
      page.getByText(/Informaci[oó]n General/i)
    ]);

    await assertAnyVisible("Detalles de la Cuenta section", [
      page.getByRole("heading", { name: /Detalles de la Cuenta/i }),
      page.getByText(/Detalles de la Cuenta/i)
    ]);

    await assertAnyVisible("Tus Negocios section", [
      page.getByRole("heading", { name: /Tus Negocios/i }),
      page.getByText(/Tus Negocios/i)
    ]);

    await assertAnyVisible("Sección Legal section", [
      page.getByRole("heading", { name: /Secci[oó]n Legal/i }),
      page.getByText(/Secci[oó]n Legal/i)
    ]);

    await screenshot(page, "administrar_negocios_account_page", true);
  });

  await runStep("Información General", async () => {
    await assertAnyVisible("user name", [
      page.getByText(/[A-Za-z]+(?:\s+[A-Za-z]+){1,}/)
    ]);
    await assertAnyVisible("user email", [
      page.getByText(/^[^\s@]+@[^\s@]+\.[^\s@]+$/)
    ]);
    await assertAnyVisible("BUSINESS PLAN text", [page.getByText(/BUSINESS PLAN/i)]);
    await assertAnyVisible("Cambiar Plan button", [
      page.getByRole("button", { name: /Cambiar Plan/i }),
      page.getByText(/Cambiar Plan/i)
    ]);
  });

  await runStep("Detalles de la Cuenta", async () => {
    await assertAnyVisible("Cuenta creada", [page.getByText(/Cuenta creada/i)]);
    await assertAnyVisible("Estado activo", [page.getByText(/Estado activo/i)]);
    await assertAnyVisible("Idioma seleccionado", [page.getByText(/Idioma seleccionado/i)]);
  });

  await runStep("Tus Negocios", async () => {
    await assertAnyVisible("business list", [
      page.getByText(/Tus Negocios/i),
      page.locator("section").filter({ hasText: /Tus Negocios/i })
    ]);
    await assertAnyVisible("Agregar Negocio button", [
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i)
    ]);
    await assertAnyVisible("Tienes 2 de 3 negocios text", [
      page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i)
    ]);
  });

  await runStep("Términos y Condiciones", async () => {
    await clickLegalLinkAndValidate({
      appPage: page,
      context,
      linkRegex: /T[eé]rminos y Condiciones/i,
      headingRegex: /T[eé]rminos y Condiciones/i,
      screenshotLabel: "terminos_y_condiciones",
      reportField: "Términos y Condiciones",
      urlField: "termsAndConditions"
    });
  });

  await runStep("Política de Privacidad", async () => {
    await clickLegalLinkAndValidate({
      appPage: page,
      context,
      linkRegex: /Pol[ií]tica de Privacidad/i,
      headingRegex: /Pol[ií]tica de Privacidad/i,
      screenshotLabel: "politica_de_privacidad",
      reportField: "Política de Privacidad",
      urlField: "privacyPolicy"
    });
  });

  const finishedAt = new Date().toISOString();
  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    startedAt,
    finishedAt,
    environment: {
      saleadsUrl: saleadsUrl || null,
      attachedViaCdp: Boolean(cdpUrl),
      headless
    },
    results: stepResults,
    evidence
  };

  const reportPath = path.join(artifactsRoot, "final-report.json");
  await fs.writeFile(reportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf-8");

  console.log("=== Final Report ===");
  console.table(
    Object.entries(stepResults).map(([field, value]) => ({
      field,
      status: value.status,
      details: value.details
    }))
  );
  console.log(`Terms URL: ${evidence.urls.termsAndConditions || "N/A"}`);
  console.log(`Privacy URL: ${evidence.urls.privacyPolicy || "N/A"}`);
  console.log(`Report saved at: ${reportPath}`);

  const hasFailures = Object.values(stepResults).some((item) => item.status !== "PASS");
  await browser.close();
  process.exitCode = hasFailures ? 1 : 0;
}

main().catch(async (error) => {
  console.error("Fatal execution error:", error);

  const finishedAt = new Date().toISOString();
  const fallbackReport = {
    name: "saleads_mi_negocio_full_test",
    startedAt,
    finishedAt,
    fatalError: error instanceof Error ? error.message : String(error),
    results: stepResults,
    evidence
  };

  try {
    await ensureArtifacts();
    const reportPath = path.join(artifactsRoot, "final-report.json");
    await fs.writeFile(reportPath, `${JSON.stringify(fallbackReport, null, 2)}\n`, "utf-8");
    console.error(`Partial report saved at: ${reportPath}`);
  } catch {
    // Best-effort fallback only.
  }

  process.exitCode = 1;
});
