#!/usr/bin/env node

import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";
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
  "Política de Privacidad"
];

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT ?? "juanlucasbarbiergarzon@gmail.com";

function parseArgs() {
  const args = process.argv.slice(2);
  const parsed = {
    url: process.env.SALEADS_LOGIN_URL ?? "",
    headless: process.env.HEADLESS === "true",
    slowMo: Number(process.env.SALEADS_SLOW_MO ?? "120")
  };

  for (let i = 0; i < args.length; i += 1) {
    const arg = args[i];
    if (arg === "--url" && args[i + 1]) {
      parsed.url = args[i + 1];
      i += 1;
      continue;
    }

    if (arg === "--headless") {
      parsed.headless = true;
      continue;
    }

    if (arg === "--headed") {
      parsed.headless = false;
      continue;
    }
  }

  return parsed;
}

function nowTag() {
  return new Date().toISOString().replaceAll(":", "-").replaceAll(".", "-");
}

function createDefaultReport() {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = {
      status: "FAIL",
      details: "Not executed."
    };
    return acc;
  }, {});
}

async function ensureDirectory(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

async function waitForUi(page) {
  await page.waitForTimeout(500);
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(
    () => {}
  );
  await page.waitForLoadState("networkidle", { timeout: 8000 }).catch(() => {});
}

async function saveScreenshot(page, evidenceDir, fileName, fullPage = false) {
  const outputPath = path.join(evidenceDir, `${fileName}.png`);
  await page.screenshot({ path: outputPath, fullPage });
  return outputPath;
}

async function pickVisible(page, description, candidates, timeoutMs = 20000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      const first = candidate.first();
      const count = await first.count();
      if (!count) {
        continue;
      }

      const visible = await first.isVisible().catch(() => false);
      if (visible) {
        return first;
      }
    }

    await page.waitForTimeout(250);
  }

  throw new Error(`Timed out waiting for visible element: ${description}`);
}

async function clickAndWait(page, locator) {
  await locator.click();
  await waitForUi(page);
}

async function assertVisible(page, description, candidates, timeoutMs = 20000) {
  return pickVisible(page, description, candidates, timeoutMs);
}

async function clickGoogleLogin(mainPage, context) {
  const googleButton = await assertVisible(
    mainPage,
    "Google login button",
    [
      mainPage.getByRole("button", {
        name: /sign in with google|iniciar sesión con google|continuar con google/i
      }),
      mainPage.getByRole("link", {
        name: /sign in with google|iniciar sesión con google|continuar con google/i
      }),
      mainPage.getByRole("button", { name: /google/i }),
      mainPage.getByText(
        /sign in with google|iniciar sesión con google|continuar con google/i
      )
    ],
    25000
  );

  const popupPromise = context
    .waitForEvent("page", { timeout: 8000 })
    .catch(() => null);

  await clickAndWait(mainPage, googleButton);

  const popupPage = await popupPromise;
  return popupPage;
}

async function maybePickGoogleAccount(page) {
  await waitForUi(page);
  const emailMatcher = new RegExp(
    GOOGLE_ACCOUNT_EMAIL.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"),
    "i"
  );

  const accountCandidate = await pickVisible(
    page,
    "Google account selector",
    [
      page.getByRole("button", { name: emailMatcher }),
      page.getByText(emailMatcher),
      page.getByRole("link", { name: emailMatcher })
    ],
    7000
  ).catch(() => null);

  if (!accountCandidate) {
    return false;
  }

  await clickAndWait(page, accountCandidate);
  return true;
}

async function waitForMainApp(page) {
  const sidebar = await assertVisible(
    page,
    "left sidebar navigation",
    [
      page.locator("aside"),
      page.getByRole("navigation"),
      page.locator('[class*="sidebar"]')
    ],
    70000
  );

  const negocioEntry = await assertVisible(
    page,
    "Negocio navigation entry",
    [page.getByText(/^negocio$/i), page.getByRole("button", { name: /^negocio$/i })],
    20000
  );

  return { sidebar, negocioEntry };
}

async function openMiNegocioMenu(page) {
  const negocioEntry = await assertVisible(
    page,
    "Negocio entry",
    [page.getByRole("button", { name: /^negocio$/i }), page.getByText(/^negocio$/i)]
  );
  await clickAndWait(page, negocioEntry);

  const miNegocioEntry = await assertVisible(
    page,
    "Mi Negocio entry",
    [
      page.getByRole("button", { name: /^mi negocio$/i }),
      page.getByRole("link", { name: /^mi negocio$/i }),
      page.getByText(/^mi negocio$/i)
    ]
  );
  await clickAndWait(page, miNegocioEntry);

  const agregar = await assertVisible(
    page,
    "Agregar Negocio submenu item",
    [
      page.getByRole("button", { name: /^agregar negocio$/i }),
      page.getByRole("link", { name: /^agregar negocio$/i }),
      page.getByText(/^agregar negocio$/i)
    ]
  );

  const administrar = await assertVisible(
    page,
    "Administrar Negocios submenu item",
    [
      page.getByRole("button", { name: /^administrar negocios$/i }),
      page.getByRole("link", { name: /^administrar negocios$/i }),
      page.getByText(/^administrar negocios$/i)
    ]
  );

  return { agregar, administrar };
}

async function openAgregarNegocioModal(page) {
  const agregarNegocio = await assertVisible(
    page,
    "Agregar Negocio option",
    [
      page.getByRole("button", { name: /^agregar negocio$/i }),
      page.getByRole("link", { name: /^agregar negocio$/i }),
      page.getByText(/^agregar negocio$/i)
    ]
  );
  await clickAndWait(page, agregarNegocio);

  await assertVisible(
    page,
    "Crear Nuevo Negocio modal title",
    [page.getByRole("heading", { name: /^crear nuevo negocio$/i }), page.getByText(/^crear nuevo negocio$/i)]
  );
  const nombreInput = await assertVisible(
    page,
    "Nombre del Negocio input",
    [
      page.getByLabel(/nombre del negocio/i),
      page.getByPlaceholder(/nombre del negocio/i),
      page.locator("input").filter({ hasText: /nombre del negocio/i })
    ]
  ).catch(async () => {
    const labeledInput = page
      .locator("label")
      .filter({ hasText: /nombre del negocio/i })
      .locator("..")
      .locator("input");
    return assertVisible(page, "Nombre del Negocio input (fallback)", [labeledInput]);
  });

  await assertVisible(page, "usage text 2 de 3 negocios", [
    page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)
  ]);
  const cancelar = await assertVisible(page, "Cancelar button", [
    page.getByRole("button", { name: /^cancelar$/i }),
    page.getByText(/^cancelar$/i)
  ]);
  await assertVisible(page, "Crear Negocio button", [
    page.getByRole("button", { name: /^crear negocio$/i }),
    page.getByText(/^crear negocio$/i)
  ]);

  await nombreInput.fill("Negocio Prueba Automatización");
  await waitForUi(page);

  return { cancelar };
}

async function openAdministrarNegocios(page) {
  const miNegocio = await assertVisible(
    page,
    "Mi Negocio entry",
    [
      page.getByRole("button", { name: /^mi negocio$/i }),
      page.getByRole("link", { name: /^mi negocio$/i }),
      page.getByText(/^mi negocio$/i)
    ]
  );
  await clickAndWait(page, miNegocio);

  const administrar = await assertVisible(
    page,
    "Administrar Negocios option",
    [
      page.getByRole("button", { name: /^administrar negocios$/i }),
      page.getByRole("link", { name: /^administrar negocios$/i }),
      page.getByText(/^administrar negocios$/i)
    ]
  );
  await clickAndWait(page, administrar);

  await assertVisible(page, "Información General section", [
    page.getByRole("heading", { name: /información general/i }),
    page.getByText(/información general/i)
  ]);
  await assertVisible(page, "Detalles de la Cuenta section", [
    page.getByRole("heading", { name: /detalles de la cuenta/i }),
    page.getByText(/detalles de la cuenta/i)
  ]);
  await assertVisible(page, "Tus Negocios section", [
    page.getByRole("heading", { name: /tus negocios/i }),
    page.getByText(/tus negocios/i)
  ]);
  await assertVisible(page, "Sección Legal section", [
    page.getByRole("heading", { name: /sección legal/i }),
    page.getByText(/sección legal/i)
  ]);
}

async function validateInformacionGeneral(page) {
  await assertVisible(page, "user name", [
    page.locator('[class*="name"]'),
    page.getByText(/@/).locator("..")
  ]);
  await assertVisible(page, "user email", [
    page.getByText(/@/),
    page.locator("a[href^='mailto:']")
  ]);
  await assertVisible(page, "BUSINESS PLAN text", [
    page.getByText(/business plan/i)
  ]);
  await assertVisible(page, "Cambiar Plan button", [
    page.getByRole("button", { name: /cambiar plan/i }),
    page.getByText(/cambiar plan/i)
  ]);
}

async function validateDetallesCuenta(page) {
  await assertVisible(page, "Cuenta creada text", [page.getByText(/cuenta creada/i)]);
  await assertVisible(page, "Estado activo text", [
    page.getByText(/estado activo/i),
    page.getByText(/activo/i)
  ]);
  await assertVisible(page, "Idioma seleccionado text", [
    page.getByText(/idioma seleccionado/i)
  ]);
}

async function validateTusNegocios(page) {
  await assertVisible(page, "business list", [
    page.locator("table"),
    page.locator("ul").filter({ hasText: /negocio/i }),
    page.getByText(/tus negocios/i).locator("..")
  ]);
  await assertVisible(page, "Agregar Negocio button", [
    page.getByRole("button", { name: /^agregar negocio$/i }),
    page.getByText(/^agregar negocio$/i)
  ]);
  await assertVisible(page, "Tienes 2 de 3 negocios text", [
    page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)
  ]);
}

async function validateLegalLink({
  page,
  context,
  linkTextRegex,
  headingRegex,
  evidenceDir,
  screenshotName
}) {
  const appPageUrlBefore = page.url();
  const legalLink = await assertVisible(page, "legal link", [
    page.getByRole("link", { name: linkTextRegex }),
    page.getByText(linkTextRegex)
  ]);

  const popupPromise = context
    .waitForEvent("page", { timeout: 7000 })
    .catch(() => null);
  await clickAndWait(page, legalLink);
  const popupPage = await popupPromise;

  const legalPage = popupPage ?? page;
  await waitForUi(legalPage);

  await assertVisible(legalPage, "legal heading", [
    legalPage.getByRole("heading", { name: headingRegex }),
    legalPage.getByText(headingRegex)
  ]);

  const bodyText = (await legalPage.locator("body").innerText()).trim();
  if (bodyText.length < 150) {
    throw new Error("Legal page content is too short.");
  }

  const screenshotPath = await saveScreenshot(
    legalPage,
    evidenceDir,
    screenshotName,
    true
  );
  const finalUrl = legalPage.url();

  if (popupPage) {
    await page.bringToFront();
    await waitForUi(page);
  } else if (page.url() !== appPageUrlBefore) {
    await page.goBack().catch(() => null);
    await waitForUi(page);
  }

  return { screenshotPath, finalUrl };
}

function writeReportLine(report) {
  console.log("");
  console.log("========== SALEADS MI NEGOCIO FINAL REPORT ==========");
  for (const field of REPORT_FIELDS) {
    const entry = report[field];
    console.log(
      `${field}: ${entry.status}${entry.details ? ` - ${entry.details}` : ""}`
    );
  }
  console.log("=====================================================");
  console.log("");
}

async function run() {
  const options = parseArgs();
  const report = createDefaultReport();
  const runFolder = path.join(
    process.cwd(),
    "artifacts",
    "saleads_mi_negocio_full_test",
    nowTag()
  );
  await ensureDirectory(runFolder);

  const browser = await chromium.launch({
    headless: options.headless,
    slowMo: options.slowMo
  });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 }
  });
  const page = await context.newPage();

  try {
    if (options.url) {
      await page.goto(options.url, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    const popupPage = await clickGoogleLogin(page, context);
    if (popupPage) {
      await popupPage.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(
        () => {}
      );
      await maybePickGoogleAccount(popupPage);
    } else {
      await maybePickGoogleAccount(page);
    }

    await waitForMainApp(page);
    const dashboardScreenshot = await saveScreenshot(
      page,
      runFolder,
      "01-dashboard-loaded",
      true
    );
    report.Login = {
      status: "PASS",
      details: `Dashboard and sidebar visible. Screenshot: ${dashboardScreenshot}`
    };

    await openMiNegocioMenu(page);
    const menuScreenshot = await saveScreenshot(
      page,
      runFolder,
      "02-mi-negocio-menu-expanded",
      false
    );
    report["Mi Negocio menu"] = {
      status: "PASS",
      details: `Mi Negocio expanded with submenu options. Screenshot: ${menuScreenshot}`
    };

    const { cancelar } = await openAgregarNegocioModal(page);
    const modalScreenshot = await saveScreenshot(
      page,
      runFolder,
      "03-agregar-negocio-modal",
      false
    );
    report["Agregar Negocio modal"] = {
      status: "PASS",
      details: `Crear Nuevo Negocio modal validated. Screenshot: ${modalScreenshot}`
    };
    await clickAndWait(page, cancelar);

    await openAdministrarNegocios(page);
    const adminScreenshot = await saveScreenshot(
      page,
      runFolder,
      "04-administrar-negocios-view",
      true
    );
    report["Administrar Negocios view"] = {
      status: "PASS",
      details: `Account page sections validated. Screenshot: ${adminScreenshot}`
    };

    await validateInformacionGeneral(page);
    report["Información General"] = {
      status: "PASS",
      details: "User identity, plan text, and Cambiar Plan button validated."
    };

    await validateDetallesCuenta(page);
    report["Detalles de la Cuenta"] = {
      status: "PASS",
      details: "Cuenta creada, Estado activo, and Idioma seleccionado validated."
    };

    await validateTusNegocios(page);
    report["Tus Negocios"] = {
      status: "PASS",
      details: "Business list, Agregar Negocio button, and limit text validated."
    };

    const termsResult = await validateLegalLink({
      page,
      context,
      linkTextRegex: /términos y condiciones/i,
      headingRegex: /términos y condiciones/i,
      evidenceDir: runFolder,
      screenshotName: "05-terminos-y-condiciones"
    });
    report["Términos y Condiciones"] = {
      status: "PASS",
      details: `Legal content visible. URL: ${termsResult.finalUrl}. Screenshot: ${termsResult.screenshotPath}`
    };

    const privacyResult = await validateLegalLink({
      page,
      context,
      linkTextRegex: /política de privacidad/i,
      headingRegex: /política de privacidad/i,
      evidenceDir: runFolder,
      screenshotName: "06-politica-de-privacidad"
    });
    report["Política de Privacidad"] = {
      status: "PASS",
      details: `Legal content visible. URL: ${privacyResult.finalUrl}. Screenshot: ${privacyResult.screenshotPath}`
    };
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    for (const field of REPORT_FIELDS) {
      if (report[field].status === "PASS") {
        continue;
      }
      report[field] = {
        status: "FAIL",
        details: `Not completed because an earlier step failed. Root cause: ${message}`
      };
    }
  } finally {
    writeReportLine(report);
    const jsonReportPath = path.join(runFolder, "final_report.json");
    await fs.writeFile(jsonReportPath, JSON.stringify(report, null, 2), "utf-8");
    console.log(`Report JSON: ${jsonReportPath}`);
    await context.close();
    await browser.close();

    const allPassed = REPORT_FIELDS.every((field) => report[field].status === "PASS");
    if (!allPassed) {
      process.exitCode = 1;
    }
  }
}

run().catch((error) => {
  console.error("Unhandled error while running the SaleADS workflow test:", error);
  process.exitCode = 1;
});
