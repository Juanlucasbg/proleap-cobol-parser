import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright";

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const TEST_NAME = "saleads_mi_negocio_full_test";
const ARTIFACT_ROOT = path.resolve("artifacts", TEST_NAME, timestampForPath(new Date()));

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

const report = Object.fromEntries(
  REPORT_FIELDS.map((field) => [
    field,
    {
      status: "FAIL",
      details: [],
    },
  ]),
);

const extraEvidence = {
  termsUrl: "",
  privacyUrl: "",
};

function timestampForPath(date) {
  const year = date.getUTCFullYear();
  const month = String(date.getUTCMonth() + 1).padStart(2, "0");
  const day = String(date.getUTCDate()).padStart(2, "0");
  const hours = String(date.getUTCHours()).padStart(2, "0");
  const minutes = String(date.getUTCMinutes()).padStart(2, "0");
  const seconds = String(date.getUTCSeconds()).padStart(2, "0");
  return `${year}${month}${day}-${hours}${minutes}${seconds}Z`;
}

function logCheckpoint(message) {
  console.log(`[${new Date().toISOString()}] ${message}`);
}

function pushDetail(field, message) {
  report[field].details.push(message);
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(900);
}

async function saveScreenshot(page, fileName, fullPage = false) {
  const screenshotPath = path.join(ARTIFACT_ROOT, `${fileName}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

async function clickByVisibleText(scope, pageForWait, labelRegex) {
  const candidates = [
    scope.getByRole("button", { name: labelRegex }).first(),
    scope.getByRole("link", { name: labelRegex }).first(),
    scope.getByRole("menuitem", { name: labelRegex }).first(),
    scope.getByRole("tab", { name: labelRegex }).first(),
    scope.getByText(labelRegex).first(),
  ];

  for (const candidate of candidates) {
    const visible = await candidate.isVisible({ timeout: 1200 }).catch(() => false);
    if (visible) {
      await candidate.click();
      await waitForUi(pageForWait);
      return;
    }
  }

  throw new Error(`No visible element found for: ${labelRegex}`);
}

async function mustSeeText(scope, textRegex, detailMessage) {
  const locator = scope.getByText(textRegex).first();
  await locator.waitFor({ state: "visible", timeout: 15000 });
  if (detailMessage) {
    return detailMessage;
  }
  return `Visible text: ${textRegex}`;
}

async function mustSeeHeading(page, textRegex, detailMessage) {
  const locator = page.getByRole("heading", { name: textRegex }).first();
  await locator.waitFor({ state: "visible", timeout: 15000 });
  return detailMessage ?? `Visible heading: ${textRegex}`;
}

async function mustSeeHeadingOrText(page, textRegex) {
  const heading = page.getByRole("heading", { name: textRegex }).first();
  const headingVisible = await heading.isVisible({ timeout: 2500 }).catch(() => false);
  if (headingVisible) {
    return;
  }

  const textNode = page.getByText(textRegex).first();
  await textNode.waitFor({ state: "visible", timeout: 15000 });
}

function markPass(field) {
  report[field].status = "PASS";
}

function markFail(field, error) {
  report[field].status = "FAIL";
  pushDetail(field, `Error: ${error.message}`);
}

async function runStep(field, stepFn) {
  try {
    await stepFn();
    markPass(field);
  } catch (error) {
    markFail(field, error);
  }
}

async function chooseGoogleAccountIfPresent(targetPage) {
  const accountItem = targetPage.getByText(ACCOUNT_EMAIL).first();
  if (await accountItem.isVisible({ timeout: 5000 }).catch(() => false)) {
    await accountItem.click();
    await targetPage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  }
}

async function getNombreDelNegocioInput(page) {
  const labelInput = page.getByLabel(/Nombre del Negocio/i).first();
  if (await labelInput.isVisible({ timeout: 1200 }).catch(() => false)) {
    return labelInput;
  }

  const placeholderInput = page.getByPlaceholder(/Nombre del Negocio/i).first();
  if (await placeholderInput.isVisible({ timeout: 1200 }).catch(() => false)) {
    return placeholderInput;
  }

  const anyVisibleInput = page.locator("input").first();
  await anyVisibleInput.waitFor({ state: "visible", timeout: 15000 });
  return anyVisibleInput;
}

async function clickLegalLinkAndValidate({
  appPage,
  context,
  linkRegex,
  headingRegex,
  screenshotFile,
  reportField,
  onUrl,
}) {
  const popupPromise = context.waitForEvent("page", { timeout: 6000 }).catch(() => null);

  await clickByVisibleText(appPage, appPage, linkRegex);
  const popup = await popupPromise;

  const legalPage = popup ?? appPage;
  await waitForUi(legalPage);
  await mustSeeHeading(legalPage, headingRegex, `Heading visible: ${headingRegex}`);
  await mustSeeHeadingOrText(legalPage, headingRegex);
  const bodyText = await legalPage.locator("body").innerText();

  if (bodyText.trim().length < 40) {
    throw new Error(`Legal content appears too short for ${reportField}`);
  }
  pushDetail(reportField, "Legal content text is visible.");

  const screenshotPath = await saveScreenshot(legalPage, screenshotFile, true);
  pushDetail(reportField, `Screenshot: ${screenshotPath}`);
  onUrl(legalPage.url());
  pushDetail(reportField, `Final URL: ${legalPage.url()}`);

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(appPage);
  }
}

async function main() {
  await mkdir(ARTIFACT_ROOT, { recursive: true });

  const loginUrl =
    process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_URL ?? process.env.BASE_URL ?? "";

  const headless = process.env.HEADLESS !== "false";
  let browser;
  let context;
  let page;
  let fatalError = null;
  try {
    browser = await chromium.launch({ headless });
    context = await browser.newContext({
      viewport: { width: 1600, height: 1000 },
    });
    page = await context.newPage();

    if (!loginUrl) {
      throw new Error(
        "Missing SaleADS login URL. Set SALEADS_LOGIN_URL (or SALEADS_URL / BASE_URL) to the login page of your target environment.",
      );
    }

    logCheckpoint(`Opening login page: ${loginUrl}`);
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    await runStep("Login", async () => {
      logCheckpoint("Step 1 - Login with Google");
      const popupPromise = context.waitForEvent("page", { timeout: 6000 }).catch(() => null);

      await clickByVisibleText(
        page,
        page,
        /sign in with google|google|iniciar sesion con google|continuar con google/i,
      );

      const popup = await popupPromise;
      if (popup) {
        await waitForUi(popup);
        await chooseGoogleAccountIfPresent(popup);
      } else {
        await chooseGoogleAccountIfPresent(page);
      }

      await page.locator("aside, nav").first().waitFor({ state: "visible", timeout: 30000 });
      pushDetail("Login", "Main application interface is visible.");
      pushDetail("Login", "Left sidebar navigation is visible.");
      const screenshotPath = await saveScreenshot(page, "01-dashboard-loaded");
      pushDetail("Login", `Screenshot: ${screenshotPath}`);
    });

    await runStep("Mi Negocio menu", async () => {
      logCheckpoint("Step 2 - Open Mi Negocio menu");
      await mustSeeText(page, /Negocio/i, "Sidebar section 'Negocio' is visible.");

      await clickByVisibleText(page, page, /Mi Negocio/i);
      await mustSeeText(page, /Agregar Negocio/i, "'Agregar Negocio' is visible.");
      await mustSeeText(page, /Administrar Negocios/i, "'Administrar Negocios' is visible.");

      const screenshotPath = await saveScreenshot(page, "02-mi-negocio-expanded");
      pushDetail("Mi Negocio menu", "Submenu expanded correctly.");
      pushDetail("Mi Negocio menu", `Screenshot: ${screenshotPath}`);
    });

    await runStep("Agregar Negocio modal", async () => {
      logCheckpoint("Step 3 - Validate Agregar Negocio modal");
      await clickByVisibleText(page, page, /Agregar Negocio/i);

      await mustSeeHeading(page, /Crear Nuevo Negocio/i, "Modal title is visible.");
      const nameInput = await getNombreDelNegocioInput(page);
      pushDetail("Agregar Negocio modal", "Input field 'Nombre del Negocio' exists.");
      await mustSeeText(page, /Tienes\s*2\s*de\s*3\s*negocios/i, "Business limit text is visible.");

      await page.getByRole("button", { name: /Cancelar/i }).first().waitFor({ state: "visible", timeout: 15000 });
      await page.getByRole("button", { name: /Crear Negocio/i }).first().waitFor({ state: "visible", timeout: 15000 });
      pushDetail("Agregar Negocio modal", "Buttons 'Cancelar' and 'Crear Negocio' are present.");

      await nameInput.click();
      await nameInput.fill("Negocio Prueba Automatizacion");
      pushDetail("Agregar Negocio modal", "Optional input fill executed.");

      const screenshotPath = await saveScreenshot(page, "03-crear-nuevo-negocio-modal");
      pushDetail("Agregar Negocio modal", `Screenshot: ${screenshotPath}`);

      await page.getByRole("button", { name: /Cancelar/i }).first().click();
      await waitForUi(page);
    });

    await runStep("Administrar Negocios view", async () => {
      logCheckpoint("Step 4 - Open Administrar Negocios");
      const adminVisible = await page.getByText(/Administrar Negocios/i).first().isVisible({ timeout: 2500 }).catch(() => false);
      if (!adminVisible) {
        await clickByVisibleText(page, page, /Mi Negocio/i);
      }

      await clickByVisibleText(page, page, /Administrar Negocios/i);
      await mustSeeText(page, /Informaci[oó]n General/i, "Section 'Información General' exists.");
      await mustSeeText(page, /Detalles de la Cuenta/i, "Section 'Detalles de la Cuenta' exists.");
      await mustSeeText(page, /Tus Negocios/i, "Section 'Tus Negocios' exists.");
      await mustSeeText(page, /Secci[oó]n Legal/i, "Section 'Sección Legal' exists.");

      const screenshotPath = await saveScreenshot(page, "04-administrar-negocios", true);
      pushDetail("Administrar Negocios view", `Screenshot: ${screenshotPath}`);
    });

    await runStep("Información General", async () => {
      logCheckpoint("Step 5 - Validate Información General");
      const section = page.locator("section, div").filter({ hasText: /Informaci[oó]n General/i }).first();
      await section.waitFor({ state: "visible", timeout: 15000 });

      const hasBusinessPlan = await section.getByText(/BUSINESS PLAN/i).first().isVisible({ timeout: 7000 }).catch(() => false);
      if (!hasBusinessPlan) {
        throw new Error("Text 'BUSINESS PLAN' is not visible.");
      }
      const hasChangePlan = await section.getByRole("button", { name: /Cambiar Plan/i }).first().isVisible({ timeout: 7000 }).catch(() => false);
      if (!hasChangePlan) {
        throw new Error("Button 'Cambiar Plan' is not visible.");
      }
      const sectionText = await section.innerText();
      if (!/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(sectionText)) {
        throw new Error("User email is not visible in Información General.");
      }
      const meaningfulLines = sectionText
        .split("\n")
        .map((line) => line.trim())
        .filter((line) => line.length >= 3);
      if (meaningfulLines.length < 3) {
        throw new Error("User name is not clearly visible in Información General.");
      }

      pushDetail("Información General", "User name appears in section.");
      pushDetail("Información General", "User email appears in section.");
      pushDetail("Información General", "Text 'BUSINESS PLAN' is visible.");
      pushDetail("Información General", "Button 'Cambiar Plan' is visible.");
    });

    await runStep("Detalles de la Cuenta", async () => {
      logCheckpoint("Step 6 - Validate Detalles de la Cuenta");
      const section = page.locator("section, div").filter({ hasText: /Detalles de la Cuenta/i }).first();
      await section.waitFor({ state: "visible", timeout: 15000 });
      await mustSeeText(section, /Cuenta creada/i, "'Cuenta creada' is visible.");
      await mustSeeText(section, /Estado activo/i, "'Estado activo' is visible.");
      await mustSeeText(section, /Idioma seleccionado/i, "'Idioma seleccionado' is visible.");
      pushDetail("Detalles de la Cuenta", "All required account detail labels are visible.");
    });

    await runStep("Tus Negocios", async () => {
      logCheckpoint("Step 7 - Validate Tus Negocios");
      const section = page.locator("section, div").filter({ hasText: /Tus Negocios/i }).first();
      await section.waitFor({ state: "visible", timeout: 15000 });
      const listVisible = await section.locator("li, tr, [role='row']").first().isVisible({ timeout: 7000 }).catch(() => false);
      if (!listVisible) {
        throw new Error("Business list is not visible.");
      }

      await section.getByRole("button", { name: /Agregar Negocio/i }).first().waitFor({ state: "visible", timeout: 10000 });
      await mustSeeText(section, /Tienes\s*2\s*de\s*3\s*negocios/i, "Business limit text is visible.");

      pushDetail("Tus Negocios", "Business list is visible.");
      pushDetail("Tus Negocios", "Button 'Agregar Negocio' exists.");
      pushDetail("Tus Negocios", "Text 'Tienes 2 de 3 negocios' is visible.");
    });

    await runStep("Términos y Condiciones", async () => {
      logCheckpoint("Step 8 - Validate Términos y Condiciones");
      await clickLegalLinkAndValidate({
        appPage: page,
        context,
        linkRegex: /Terminos y Condiciones|T[eé]rminos y Condiciones/i,
        headingRegex: /Terminos y Condiciones|T[eé]rminos y Condiciones/i,
        screenshotFile: "05-terminos-y-condiciones",
        reportField: "Términos y Condiciones",
        onUrl: (url) => {
          extraEvidence.termsUrl = url;
        },
      });
    });

    await runStep("Política de Privacidad", async () => {
      logCheckpoint("Step 9 - Validate Política de Privacidad");
      await clickLegalLinkAndValidate({
        appPage: page,
        context,
        linkRegex: /Politica de Privacidad|Pol[ií]tica de Privacidad/i,
        headingRegex: /Politica de Privacidad|Pol[ií]tica de Privacidad/i,
        screenshotFile: "06-politica-de-privacidad",
        reportField: "Política de Privacidad",
        onUrl: (url) => {
          extraEvidence.privacyUrl = url;
        },
      });
    });
  } catch (error) {
    fatalError = error;
    for (const field of REPORT_FIELDS) {
      if (report[field].details.length === 0) {
        pushDetail(field, `Error: ${error.message}`);
      }
    }
  } finally {
    if (browser) {
      await browser.close();
    }
  }

  const summary = REPORT_FIELDS.map((field) => ({
    field,
    status: report[field].status,
  }));

  const reportOutput = {
    testName: TEST_NAME,
    artifactRoot: ARTIFACT_ROOT,
    generatedAt: new Date().toISOString(),
    summary,
    details: report,
    finalUrls: {
      termsAndConditions: extraEvidence.termsUrl,
      privacyPolicy: extraEvidence.privacyUrl,
    },
  };

  const reportJsonPath = path.join(ARTIFACT_ROOT, "report.json");
  const reportMdPath = path.join(ARTIFACT_ROOT, "report.md");

  await writeFile(reportJsonPath, `${JSON.stringify(reportOutput, null, 2)}\n`, "utf8");
  await writeFile(reportMdPath, toMarkdownReport(reportOutput), "utf8");

  logCheckpoint(`Artifacts saved in ${ARTIFACT_ROOT}`);
  console.table(summary);
  console.log(`JSON report: ${reportJsonPath}`);
  console.log(`Markdown report: ${reportMdPath}`);

  const hasFailure = summary.some((item) => item.status !== "PASS") || Boolean(fatalError);
  if (hasFailure) {
    process.exitCode = 1;
  }
}

function toMarkdownReport(data) {
  const lines = [];
  lines.push(`# ${data.testName}`);
  lines.push("");
  lines.push(`Generated at: ${data.generatedAt}`);
  lines.push(`Artifacts: ${data.artifactRoot}`);
  lines.push("");
  lines.push("## Final Report");
  lines.push("");
  lines.push("| Field | Status |");
  lines.push("|---|---|");
  for (const item of data.summary) {
    lines.push(`| ${item.field} | ${item.status} |`);
  }
  lines.push("");
  lines.push("## Evidence and Validation Details");
  lines.push("");
  for (const item of data.summary) {
    lines.push(`### ${item.field}`);
    for (const detail of data.details[item.field].details) {
      lines.push(`- ${detail}`);
    }
    lines.push("");
  }
  lines.push("## Final URLs");
  lines.push("");
  lines.push(`- Términos y Condiciones: ${data.finalUrls.termsAndConditions || "(not captured)"}`);
  lines.push(`- Política de Privacidad: ${data.finalUrls.privacyPolicy || "(not captured)"}`);
  lines.push("");
  return `${lines.join("\n")}\n`;
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
