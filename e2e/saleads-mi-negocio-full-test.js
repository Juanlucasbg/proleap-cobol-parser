#!/usr/bin/env node

const fs = require("fs");
const path = require("path");
const { chromium } = require("playwright");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const SALEADS_URL = process.env.SALEADS_URL || "";
const BROWSER_CDP_URL = process.env.BROWSER_CDP_URL || "";
const HEADLESS = process.env.HEADLESS !== "false";
const CLICK_SETTLE_MS = Number(process.env.CLICK_SETTLE_MS || "1200");
const RUN_ID = new Date().toISOString().replace(/[:.]/g, "-");
const ARTIFACTS_DIR =
  process.env.ARTIFACTS_DIR ||
  path.join("artifacts", "saleads_mi_negocio_full_test", RUN_ID);

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
    { status: "FAIL", detail: "Step did not complete." },
  ]),
);
const screenshots = [];
const legalUrls = {
  "Términos y Condiciones": "",
  "Política de Privacidad": "",
};

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function escapeRegExp(input) {
  return input.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function toRegex(text) {
  return text instanceof RegExp ? text : new RegExp(escapeRegExp(text), "i");
}

async function waitForUi(page) {
  await Promise.allSettled([
    page.waitForLoadState("domcontentloaded", { timeout: 10000 }),
    page.waitForLoadState("networkidle", { timeout: 10000 }),
  ]);
  await page.waitForTimeout(CLICK_SETTLE_MS);
}

async function findVisibleLocator(page, candidates, timeoutMs, label) {
  const deadline = Date.now() + timeoutMs;
  const terms = candidates.map((candidate) => toRegex(candidate));

  while (Date.now() < deadline) {
    for (const term of terms) {
      const locatorCandidates = [
        page.getByRole("button", { name: term }),
        page.getByRole("link", { name: term }),
        page.getByRole("menuitem", { name: term }),
        page.getByRole("tab", { name: term }),
        page.getByRole("heading", { name: term }),
        page.getByText(term),
      ];

      for (const locator of locatorCandidates) {
        const count = await locator.count().catch(() => 0);
        const probeCount = Math.min(count, 5);
        for (let i = 0; i < probeCount; i += 1) {
          const node = locator.nth(i);
          if (await node.isVisible().catch(() => false)) {
            return node;
          }
        }
      }
    }
    await sleep(250);
  }

  const readableCandidates = candidates
    .map((candidate) =>
      candidate instanceof RegExp ? candidate.toString() : candidate,
    )
    .join(", ");
  throw new Error(`Could not find visible ${label}: ${readableCandidates}`);
}

async function maybeClickVisibleText(page, candidates, options = {}) {
  const { timeoutMs = 5000, waitAfterClick = true } = options;
  try {
    const locator = await findVisibleLocator(page, candidates, timeoutMs, "text");
    await locator.click({ timeout: 10000 });
    if (waitAfterClick) {
      await waitForUi(page);
    }
    return true;
  } catch {
    return false;
  }
}

async function clickVisibleText(page, candidates, options = {}) {
  const { timeoutMs = 15000, waitAfterClick = true } = options;
  const locator = await findVisibleLocator(page, candidates, timeoutMs, "text");
  await locator.click({ timeout: 10000 });
  if (waitAfterClick) {
    await waitForUi(page);
  }
}

async function assertVisibleText(page, candidates, timeoutMs = 15000, label = "text") {
  await findVisibleLocator(page, candidates, timeoutMs, label);
}

async function waitForNewPage(context, knownPages, timeoutMs = 8000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    for (const page of context.pages()) {
      if (!knownPages.has(page)) {
        await Promise.allSettled([
          page.waitForLoadState("domcontentloaded", { timeout: 10000 }),
          page.waitForLoadState("networkidle", { timeout: 10000 }),
        ]);
        return page;
      }
    }
    await sleep(200);
  }
  return null;
}

async function findGooglePage(context, timeoutMs = 12000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    for (const page of context.pages()) {
      const pageUrl = page.url();
      if (/accounts\.google\.com/i.test(pageUrl)) {
        return page;
      }
    }
    await sleep(300);
  }
  return null;
}

async function getLatestSaleadsPage(context, fallbackPage) {
  const pages = context.pages();
  const nonGooglePages = pages.filter((page) => !/accounts\.google\.com/i.test(page.url()));
  const preferred = nonGooglePages.find((page) => !page.isClosed() && page.url() !== "about:blank");
  return preferred || fallbackPage;
}

function createReporter() {
  return {
    async run(field, fn) {
      console.log(`\n[STEP] ${field}`);
      try {
        await fn();
        report[field] = { status: "PASS", detail: "" };
        console.log(`[PASS] ${field}`);
      } catch (error) {
        const message =
          error && typeof error.message === "string" ? error.message : String(error);
        report[field] = { status: "FAIL", detail: message };
        console.error(`[FAIL] ${field}: ${message}`);
      }
    },
  };
}

let screenshotCounter = 0;
async function captureScreenshot(page, name, fullPage = false) {
  screenshotCounter += 1;
  const normalizedName = name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
  const filename = `${String(screenshotCounter).padStart(2, "0")}-${normalizedName}.png`;
  const targetPath = path.join(ARTIFACTS_DIR, filename);
  await page.screenshot({ path: targetPath, fullPage });
  screenshots.push(targetPath);
  return targetPath;
}

async function assertSidebarVisible(page) {
  const sidebar = page.locator("aside").first();
  const nav = page.getByRole("navigation").first();
  const hasSidebar = await sidebar.isVisible().catch(() => false);
  const hasNav = await nav.isVisible().catch(() => false);
  if (!hasSidebar && !hasNav) {
    await assertVisibleText(page, ["Negocio", "Mi Negocio"], 12000, "left navigation");
  }
}

async function assertEmailVisible(page) {
  const bodyText = await page.locator("body").innerText();
  const emailRegex = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;
  if (!emailRegex.test(bodyText)) {
    throw new Error("Could not find a visible user email.");
  }
}

async function assertNameVisible(page) {
  const bodyText = await page.locator("body").innerText();
  const lines = bodyText
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean);

  const excludedTokens = [
    "información general",
    "detalles de la cuenta",
    "tus negocios",
    "sección legal",
    "business plan",
    "cambiar plan",
    "cuenta creada",
    "estado activo",
    "idioma seleccionado",
    "agregar negocio",
    "administrar negocios",
    "términos y condiciones",
    "politica de privacidad",
    "política de privacidad",
  ];

  const hasNameLikeLine = lines.some((line) => {
    const normalized = line.toLowerCase();
    if (excludedTokens.some((token) => normalized.includes(token))) {
      return false;
    }
    if (/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(line)) {
      return false;
    }
    return /^[A-Za-zÀ-ÿ][A-Za-zÀ-ÿ' -]{2,}$/.test(line) && line.length <= 60;
  });

  if (!hasNameLikeLine) {
    throw new Error("Could not confidently detect a visible user name.");
  }
}

async function assertBusinessListVisible(page) {
  const tusNegociosBlock = page
    .locator("section,div,article")
    .filter({ hasText: /Tus Negocios/i })
    .first();
  const blockVisible = await tusNegociosBlock.isVisible().catch(() => false);
  if (!blockVisible) {
    throw new Error("Could not locate the 'Tus Negocios' section block.");
  }

  const candidateRows = tusNegociosBlock.locator(
    "li:visible, [role='row']:visible, article:visible, .card:visible, div:visible",
  );
  const rowCount = await candidateRows.count();
  if (rowCount < 3) {
    throw new Error("Business list does not appear populated/visible.");
  }
}

async function openLegalLink({
  appPage,
  linkCandidates,
  headingCandidates,
  reportKey,
  screenshotLabel,
}) {
  const context = appPage.context();
  const knownPages = new Set(context.pages());
  const startingUrl = appPage.url();

  await clickVisibleText(appPage, linkCandidates, { timeoutMs: 15000, waitAfterClick: true });
  let legalPage = await waitForNewPage(context, knownPages, 9000);
  if (!legalPage) {
    legalPage = appPage;
  }

  await legalPage.bringToFront().catch(() => {});
  await waitForUi(legalPage);
  await assertVisibleText(
    legalPage,
    headingCandidates,
    20000,
    `${reportKey} heading`,
  );

  const bodyText = await legalPage.locator("body").innerText();
  if (bodyText.replace(/\s+/g, " ").trim().length < 120) {
    throw new Error(`${reportKey} page content looks too short.`);
  }

  legalUrls[reportKey] = legalPage.url();
  await captureScreenshot(legalPage, screenshotLabel, true);

  if (legalPage !== appPage) {
    await legalPage.close().catch(() => {});
    await appPage.bringToFront().catch(() => {});
    await waitForUi(appPage);
    return appPage;
  }

  if (appPage.url() !== startingUrl) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(appPage);
  }
  return appPage;
}

async function bootstrap() {
  let browser;
  let context;
  let page;
  let launchedBrowserLocally = false;

  if (BROWSER_CDP_URL) {
    browser = await chromium.connectOverCDP(BROWSER_CDP_URL);
    const contexts = browser.contexts();
    context = contexts[0];
    if (!context) {
      throw new Error("Connected browser has no context available.");
    }
    page = context.pages()[0] || (await context.newPage());
    if (SALEADS_URL) {
      await page.goto(SALEADS_URL, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }
  } else {
    if (!SALEADS_URL) {
      throw new Error(
        "SALEADS_URL is required when BROWSER_CDP_URL is not provided.",
      );
    }
    browser = await chromium.launch({ headless: HEADLESS });
    launchedBrowserLocally = true;
    context = await browser.newContext();
    page = await context.newPage();
    await page.goto(SALEADS_URL, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  if (page.url() === "about:blank") {
    throw new Error(
      "Initial page is blank. Provide SALEADS_URL or connect to an already opened SaleADS page via BROWSER_CDP_URL.",
    );
  }

  return { browser, context, page, launchedBrowserLocally };
}

async function main() {
  fs.mkdirSync(ARTIFACTS_DIR, { recursive: true });

  const reporter = createReporter();
  const { browser, context, launchedBrowserLocally } = await bootstrap();
  let appPage = await getLatestSaleadsPage(context, context.pages()[0]);

  try {
    await reporter.run("Login", async () => {
      await clickVisibleText(appPage, [
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Continuar con Google",
        "Login with Google",
      ]);

      const googlePage = await findGooglePage(context, 15000);
      if (googlePage) {
        await googlePage.bringToFront().catch(() => {});
        await waitForUi(googlePage);

        const selectedAccount = await maybeClickVisibleText(
          googlePage,
          [GOOGLE_ACCOUNT_EMAIL],
          { timeoutMs: 12000, waitAfterClick: true },
        );

        if (!selectedAccount) {
          console.log(
            `Google account selector not visible for ${GOOGLE_ACCOUNT_EMAIL}; continuing.`,
          );
        }
      }

      appPage = await getLatestSaleadsPage(context, appPage);
      await appPage.bringToFront().catch(() => {});
      await waitForUi(appPage);

      await assertSidebarVisible(appPage);
      await captureScreenshot(appPage, "dashboard-loaded", true);
    });

    await reporter.run("Mi Negocio menu", async () => {
      await clickVisibleText(appPage, ["Negocio", "Mi Negocio"], {
        timeoutMs: 15000,
        waitAfterClick: true,
      });
      const submenuIsReady = await maybeClickVisibleText(appPage, ["Mi Negocio"], {
        timeoutMs: 5000,
        waitAfterClick: true,
      });

      if (!submenuIsReady) {
        await assertVisibleText(appPage, ["Mi Negocio"], 10000, "Mi Negocio option");
      }
      await assertVisibleText(
        appPage,
        ["Agregar Negocio"],
        10000,
        "Agregar Negocio option",
      );
      await assertVisibleText(
        appPage,
        ["Administrar Negocios"],
        10000,
        "Administrar Negocios option",
      );
      await captureScreenshot(appPage, "mi-negocio-menu-expanded", true);
    });

    await reporter.run("Agregar Negocio modal", async () => {
      await clickVisibleText(appPage, ["Agregar Negocio"], {
        timeoutMs: 15000,
        waitAfterClick: true,
      });

      await assertVisibleText(
        appPage,
        ["Crear Nuevo Negocio"],
        12000,
        "modal title",
      );
      await assertVisibleText(
        appPage,
        ["Nombre del Negocio"],
        12000,
        "Nombre del Negocio label",
      );
      await assertVisibleText(
        appPage,
        ["Tienes 2 de 3 negocios"],
        12000,
        "business quota text",
      );
      await assertVisibleText(appPage, ["Cancelar"], 12000, "Cancelar button");
      await assertVisibleText(
        appPage,
        ["Crear Negocio"],
        12000,
        "Crear Negocio button",
      );
      await captureScreenshot(appPage, "agregar-negocio-modal", true);

      const input = appPage
        .locator("input:visible, textarea:visible")
        .first();
      if (await input.isVisible().catch(() => false)) {
        await input.click();
        await input.fill("Negocio Prueba Automatización");
      }
      await clickVisibleText(appPage, ["Cancelar"], {
        timeoutMs: 10000,
        waitAfterClick: true,
      });
    });

    await reporter.run("Administrar Negocios view", async () => {
      const adminVisible = await maybeClickVisibleText(appPage, ["Administrar Negocios"], {
        timeoutMs: 4000,
        waitAfterClick: true,
      });
      if (!adminVisible) {
        await clickVisibleText(appPage, ["Mi Negocio"], {
          timeoutMs: 10000,
          waitAfterClick: true,
        });
        await clickVisibleText(appPage, ["Administrar Negocios"], {
          timeoutMs: 12000,
          waitAfterClick: true,
        });
      }

      await assertVisibleText(appPage, ["Información General"], 15000, "Información General");
      await assertVisibleText(appPage, ["Detalles de la Cuenta"], 15000, "Detalles de la Cuenta");
      await assertVisibleText(appPage, ["Tus Negocios"], 15000, "Tus Negocios");
      await assertVisibleText(appPage, ["Sección Legal"], 15000, "Sección Legal");
      await captureScreenshot(appPage, "administrar-negocios-view", true);
    });

    await reporter.run("Información General", async () => {
      await assertNameVisible(appPage);
      await assertEmailVisible(appPage);
      await assertVisibleText(appPage, ["BUSINESS PLAN"], 10000, "BUSINESS PLAN");
      await assertVisibleText(appPage, ["Cambiar Plan"], 10000, "Cambiar Plan button");
    });

    await reporter.run("Detalles de la Cuenta", async () => {
      await assertVisibleText(appPage, ["Cuenta creada"], 10000, "Cuenta creada");
      await assertVisibleText(appPage, ["Estado activo"], 10000, "Estado activo");
      await assertVisibleText(
        appPage,
        ["Idioma seleccionado"],
        10000,
        "Idioma seleccionado",
      );
    });

    await reporter.run("Tus Negocios", async () => {
      await assertBusinessListVisible(appPage);
      await assertVisibleText(appPage, ["Agregar Negocio"], 10000, "Agregar Negocio button");
      await assertVisibleText(
        appPage,
        ["Tienes 2 de 3 negocios"],
        10000,
        "business quota text",
      );
    });

    await reporter.run("Términos y Condiciones", async () => {
      appPage = await openLegalLink({
        appPage,
        linkCandidates: ["Términos y Condiciones", "Terminos y Condiciones"],
        headingCandidates: ["Términos y Condiciones", "Terminos y Condiciones"],
        reportKey: "Términos y Condiciones",
        screenshotLabel: "terminos-y-condiciones",
      });
    });

    await reporter.run("Política de Privacidad", async () => {
      appPage = await openLegalLink({
        appPage,
        linkCandidates: ["Política de Privacidad", "Politica de Privacidad"],
        headingCandidates: ["Política de Privacidad", "Politica de Privacidad"],
        reportKey: "Política de Privacidad",
        screenshotLabel: "politica-de-privacidad",
      });
    });
  } finally {
    if (launchedBrowserLocally) {
      await browser.close().catch(() => {});
    }
  }

  const finalStatus = {};
  for (const field of REPORT_FIELDS) {
    finalStatus[field] = report[field].status;
  }

  const summary = {
    runId: RUN_ID,
    screenshots,
    legalUrls,
    report,
    finalStatus,
  };
  const reportPath = path.join(ARTIFACTS_DIR, "report.json");
  fs.writeFileSync(reportPath, `${JSON.stringify(summary, null, 2)}\n`, "utf8");

  console.log("\n=== Final Report ===");
  for (const field of REPORT_FIELDS) {
    const entry = report[field];
    if (entry.status === "PASS") {
      console.log(`${field}: PASS`);
    } else {
      console.log(`${field}: FAIL (${entry.detail})`);
    }
  }
  console.log(`Términos y Condiciones URL: ${legalUrls["Términos y Condiciones"] || "N/A"}`);
  console.log(`Política de Privacidad URL: ${legalUrls["Política de Privacidad"] || "N/A"}`);
  console.log(`Artifacts directory: ${ARTIFACTS_DIR}`);
  console.log(`JSON report: ${reportPath}`);

  const hasFailure = REPORT_FIELDS.some((field) => report[field].status !== "PASS");
  process.exitCode = hasFailure ? 1 : 0;
}

main().catch((error) => {
  const message = error && error.stack ? error.stack : String(error);
  console.error("\nFatal error while executing workflow:");
  console.error(message);
  process.exitCode = 1;
});
