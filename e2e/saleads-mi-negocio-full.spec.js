const { test, expect } = require("@playwright/test");
const fs = require("node:fs");
const path = require("node:path");

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

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

function runStamp() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

function createRunPaths() {
  const rootDir = path.join(
    "e2e-artifacts",
    "saleads_mi_negocio_full_test",
    runStamp()
  );
  fs.mkdirSync(rootDir, { recursive: true });
  return {
    rootDir,
    reportPath: path.join(rootDir, "final-report.json")
  };
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
}

async function locatorIsVisible(locator) {
  try {
    if ((await locator.count()) < 1) {
      return false;
    }
    return await locator.first().isVisible();
  } catch {
    return false;
  }
}

async function hasAnyVisibleLocator(locators, timeoutMs = 6000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    for (const locator of locators) {
      if (await locatorIsVisible(locator)) {
        return true;
      }
    }
    await locators[0].page().waitForTimeout(250);
  }
  return false;
}

async function hasVisibleText(page, text, timeoutMs = 6000) {
  const locator = page.getByText(text, { exact: false }).first();
  try {
    await locator.waitFor({ state: "visible", timeout: timeoutMs });
    return true;
  } catch {
    return false;
  }
}

async function hasAnyVisibleText(page, texts, timeoutMs = 6000) {
  for (const text of texts) {
    if (await hasVisibleText(page, text, timeoutMs)) {
      return true;
    }
  }
  return false;
}

async function clickByVisibleText(page, texts, timeoutMs = 10000) {
  const candidates = Array.isArray(texts) ? texts : [texts];
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const text of candidates) {
      const locators = [
        page.getByRole("button", { name: text, exact: true }).first(),
        page.getByRole("link", { name: text, exact: true }).first(),
        page.getByRole("menuitem", { name: text, exact: true }).first(),
        page.getByText(text, { exact: true }).first(),
        page.getByText(text, { exact: false }).first()
      ];

      for (const locator of locators) {
        if (!(await locatorIsVisible(locator))) {
          continue;
        }

        try {
          await locator.click({ timeout: 2500 });
          await waitForUi(page);
          return { ok: true, selectedText: text };
        } catch {
          // Continue trying alternate selectors/candidates.
        }
      }
    }
    await page.waitForTimeout(250);
  }
  return { ok: false, selectedText: null };
}

function toFileSafeName(name) {
  return name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");
}

async function checkpoint(page, runDir, report, checkpointName, fullPage = false) {
  const fileName = `${toFileSafeName(checkpointName)}.png`;
  const fullPath = path.join(runDir, fileName);
  await page.screenshot({ path: fullPath, fullPage });
  report.checkpoints[checkpointName] = fullPath;
}

async function chooseGoogleAccountIfPresent(context, appPage) {
  const pages = context.pages();
  const pagesToCheck = [...pages].reverse();

  for (const openPage of pagesToCheck) {
    await openPage.bringToFront();
    if (await hasVisibleText(openPage, ACCOUNT_EMAIL, 2500)) {
      const accountClick = await clickByVisibleText(openPage, ACCOUNT_EMAIL, 5000);
      if (accountClick.ok) {
        await waitForUi(openPage);
      }
    }
  }

  await appPage.bringToFront();
}

async function readSectionTextsByTitle(page, title) {
  const heading = page.getByText(title, { exact: false }).first();
  if (!(await locatorIsVisible(heading))) {
    return [];
  }
  const section = heading.locator("xpath=ancestor::*[self::section or self::div][1]");
  if (!(await locatorIsVisible(section))) {
    return [];
  }

  const values = await section.locator("xpath=.//*[normalize-space(text())!='']").allInnerTexts();
  return values.map((entry) => entry.trim()).filter(Boolean);
}

async function openLegalLinkAndValidate(
  page,
  context,
  report,
  runDir,
  linkText,
  headingText,
  reportKey
) {
  const originalPage = page;
  const existingPages = context.pages().length;
  const oldUrl = originalPage.url();

  const newPagePromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  const clickResult = await clickByVisibleText(originalPage, linkText, 10000);
  if (!clickResult.ok) {
    return false;
  }

  const openedPage = await newPagePromise;
  let activePage = originalPage;
  let usedNewTab = false;

  if (openedPage) {
    activePage = openedPage;
    usedNewTab = true;
    await activePage.waitForLoadState("domcontentloaded");
    await activePage.waitForTimeout(800);
  } else if (context.pages().length > existingPages) {
    activePage = context.pages()[context.pages().length - 1];
    usedNewTab = true;
    await activePage.waitForLoadState("domcontentloaded");
    await activePage.waitForTimeout(800);
  } else {
    await waitForUi(activePage);
  }

  const headingVisible = await hasVisibleText(activePage, headingText, 12000);
  const legalContentVisible = await hasAnyVisibleLocator(
    [
      activePage.locator("article p"),
      activePage.locator("main p"),
      activePage.locator("section p"),
      activePage.locator("p")
    ],
    8000
  );

  report.legalUrls[reportKey] = activePage.url();
  await checkpoint(activePage, runDir, report, `${reportKey}-legal-page`);

  if (usedNewTab) {
    await activePage.close();
    await originalPage.bringToFront();
  } else if (activePage.url() !== oldUrl) {
    await activePage.goBack().catch(() => {});
    await waitForUi(activePage);
  }

  return headingVisible && legalContentVisible;
}

async function runStep(field, report, fn) {
  try {
    const ok = await fn();
    report.validations[field] = ok ? "PASS" : "FAIL";
    return ok;
  } catch (error) {
    report.validations[field] = "FAIL";
    report.errors.push(`${field}: ${error.message}`);
    return false;
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const runPaths = createRunPaths();
  const report = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    environment: {
      startUrl: process.env.SALEADS_START_URL || process.env.BASE_URL || null,
      initialPageUrl: null,
      finalAppUrl: null
    },
    validations: Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"])),
    legalUrls: {},
    checkpoints: {},
    errors: []
  };

  try {
    const startUrl = process.env.SALEADS_START_URL || process.env.BASE_URL;
    if (startUrl) {
      await page.goto(startUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    report.environment.initialPageUrl = page.url();
    if (page.url() === "about:blank") {
      report.errors.push(
        "No login page was provided. Set SALEADS_START_URL or BASE_URL to the current SaleADS login page."
      );
    }

    await runStep("Login", report, async () => {
      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
      const loginClick = await clickByVisibleText(
        page,
        [
          "Sign in with Google",
          "Iniciar sesion con Google",
          "Iniciar sesi\u00f3n con Google",
          "Continuar con Google",
          "Login with Google",
          "Google"
        ],
        12000
      );

      if (!loginClick.ok) {
        return false;
      }

      const popup = await popupPromise;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        await popup.waitForTimeout(1000);
      }

      await chooseGoogleAccountIfPresent(context, page);
      await waitForUi(page);

      const mainInterfaceVisible = await hasAnyVisibleLocator(
        [page.locator("main"), page.locator("header"), page.locator("div")],
        12000
      );
      const sidebarVisible = await hasAnyVisibleLocator(
        [page.locator("aside"), page.locator("nav"), page.locator("[class*='sidebar']")],
        12000
      );
      const negocioVisible = await hasAnyVisibleText(page, ["Negocio", "Mi Negocio"], 12000);

      const ok = mainInterfaceVisible && (sidebarVisible || negocioVisible);
      if (ok) {
        await checkpoint(page, runPaths.rootDir, report, "dashboard-loaded");
      }
      return ok;
    });

    await runStep("Mi Negocio menu", report, async () => {
      const negocioClick = await clickByVisibleText(page, ["Negocio", "Mi Negocio"], 10000);
      if (!negocioClick.ok) {
        return false;
      }

      if (!(await hasVisibleText(page, "Agregar Negocio", 2500))) {
        await clickByVisibleText(page, "Mi Negocio", 5000);
      }

      const agregarVisible = await hasVisibleText(page, "Agregar Negocio", 10000);
      const administrarVisible = await hasVisibleText(page, "Administrar Negocios", 10000);
      const ok = agregarVisible && administrarVisible;

      if (ok) {
        await checkpoint(page, runPaths.rootDir, report, "mi-negocio-expanded-menu");
      }
      return ok;
    });

    await runStep("Agregar Negocio modal", report, async () => {
      const agregarClick = await clickByVisibleText(page, "Agregar Negocio", 10000);
      if (!agregarClick.ok) {
        return false;
      }

      const titleVisible = await hasVisibleText(page, "Crear Nuevo Negocio", 10000);
      const usageVisible = await hasVisibleText(page, "Tienes 2 de 3 negocios", 10000);
      const cancelVisible = await hasVisibleText(page, "Cancelar", 10000);
      const createVisible = await hasVisibleText(page, "Crear Negocio", 10000);

      const nameInput = page
        .getByLabel("Nombre del Negocio", { exact: false })
        .or(page.getByPlaceholder("Nombre del Negocio", { exact: false }))
        .first();

      const inputVisible = await locatorIsVisible(nameInput);
      if (inputVisible) {
        await nameInput.fill("Negocio Prueba Automatizacion");
      }

      if (titleVisible) {
        await checkpoint(page, runPaths.rootDir, report, "agregar-negocio-modal");
      }

      if (cancelVisible) {
        await clickByVisibleText(page, "Cancelar", 5000);
      }

      return titleVisible && inputVisible && usageVisible && cancelVisible && createVisible;
    });

    await runStep("Administrar Negocios view", report, async () => {
      if (!(await hasVisibleText(page, "Administrar Negocios", 3000))) {
        await clickByVisibleText(page, "Mi Negocio", 5000);
      }

      const adminClick = await clickByVisibleText(page, "Administrar Negocios", 10000);
      if (!adminClick.ok) {
        return false;
      }

      const infoGeneral = await hasVisibleText(page, "Informacion General", 12000);
      const infoGeneralAccent = await hasVisibleText(page, "Informaci\u00f3n General", 2000);
      const detallesCuenta = await hasAnyVisibleText(
        page,
        ["Detalles de la Cuenta", "Detalles de la cuenta"],
        12000
      );
      const tusNegocios = await hasVisibleText(page, "Tus Negocios", 12000);
      const legalSection = await hasAnyVisibleText(
        page,
        ["Seccion Legal", "Secci\u00f3n Legal"],
        12000
      );

      const ok = (infoGeneral || infoGeneralAccent) && detallesCuenta && tusNegocios && legalSection;
      if (ok) {
        await checkpoint(page, runPaths.rootDir, report, "administrar-negocios-view", true);
      }
      return ok;
    });

    await runStep("Informacion General", report, async () => {
      const sectionTexts = await readSectionTextsByTitle(page, "Informacion General");
      const sectionTextsAccent = await readSectionTextsByTitle(page, "Informaci\u00f3n General");
      const merged = [...sectionTexts, ...sectionTextsAccent];

      const emailVisible =
        (await hasVisibleText(page, ACCOUNT_EMAIL, 6000)) ||
        (await locatorIsVisible(
          page.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/").first()
        ));
      const planVisible = await hasVisibleText(page, "BUSINESS PLAN", 6000);
      const changePlanVisible = await hasVisibleText(page, "Cambiar Plan", 6000);

      const usernameVisible = merged.some((line) => {
        const normalized = line.trim();
        if (!normalized) {
          return false;
        }
        if (normalized.includes("@")) {
          return false;
        }
        if (/informaci/i.test(normalized)) {
          return false;
        }
        if (/business plan/i.test(normalized)) {
          return false;
        }
        if (/cambiar plan/i.test(normalized)) {
          return false;
        }
        return normalized.length > 2;
      });

      return usernameVisible && emailVisible && planVisible && changePlanVisible;
    });

    await runStep("Detalles de la Cuenta", report, async () => {
      const createdVisible = await hasVisibleText(page, "Cuenta creada", 8000);
      const activeVisible = await hasAnyVisibleText(page, ["Estado activo", "Activo"], 8000);
      const languageVisible = await hasAnyVisibleText(
        page,
        ["Idioma seleccionado", "Idioma"],
        8000
      );
      return createdVisible && activeVisible && languageVisible;
    });

    await runStep("Tus Negocios", report, async () => {
      const sectionVisible = await hasVisibleText(page, "Tus Negocios", 8000);
      const addBusinessVisible = await hasVisibleText(page, "Agregar Negocio", 8000);
      const usageVisible = await hasVisibleText(page, "Tienes 2 de 3 negocios", 8000);

      const businessListVisible = await hasAnyVisibleLocator(
        [
          page.locator("section:has-text('Tus Negocios') li"),
          page.locator("section:has-text('Tus Negocios') tr"),
          page.locator("section:has-text('Tus Negocios') [class*='card']"),
          page.locator("div:has-text('Tus Negocios') li"),
          page.locator("div:has-text('Tus Negocios') tr"),
          page.locator("div:has-text('Tus Negocios') [class*='card']")
        ],
        8000
      );

      return sectionVisible && businessListVisible && addBusinessVisible && usageVisible;
    });

    await runStep("Terminos y Condiciones", report, async () => {
      return openLegalLinkAndValidate(
        page,
        context,
        report,
        runPaths.rootDir,
        "T\u00e9rminos y Condiciones",
        "T\u00e9rminos y Condiciones",
        "terminosYCondiciones"
      );
    });

    await runStep("Politica de Privacidad", report, async () => {
      return openLegalLinkAndValidate(
        page,
        context,
        report,
        runPaths.rootDir,
        "Pol\u00edtica de Privacidad",
        "Pol\u00edtica de Privacidad",
        "politicaDePrivacidad"
      );
    });
  } finally {
    report.environment.finalAppUrl = page.url();
    fs.writeFileSync(runPaths.reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
    await test.info().attach("saleads-final-report", {
      path: runPaths.reportPath,
      contentType: "application/json"
    });
  }

  const failed = Object.entries(report.validations)
    .filter(([, status]) => status !== "PASS")
    .map(([field]) => field);

  expect(
    failed,
    `One or more required validations failed. See ${runPaths.reportPath} for details.`
  ).toEqual([]);
});
