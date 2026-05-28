const { chromium } = require("playwright");
const fs = require("fs/promises");
const path = require("path");

const TEST_NAME = "saleads_mi_negocio_full_test";
const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const REQUIRED_REPORT_FIELDS = [
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

function utcStamp() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => undefined);
  await page.waitForTimeout(700);
}

async function isVisible(locator, timeout = 2500) {
  return locator.first().isVisible({ timeout }).catch(() => false);
}

async function clickFirstVisible(page, builders, postClickWait = true) {
  for (const build of builders) {
    const locator = build();
    if (await isVisible(locator)) {
      await locator.first().click();
      if (postClickWait) {
        await waitForUiLoad(page);
      }
      return locator;
    }
  }

  throw new Error("No clickable visible element found for provided selectors.");
}

async function expectVisibleByText(page, text) {
  const byText = page.getByText(text, { exact: true });
  const byTextLoose = page.getByText(text, { exact: false });

  if ((await isVisible(byText, 5000)) || (await isVisible(byTextLoose, 5000))) {
    return;
  }

  throw new Error(`Expected visible text not found: "${text}"`);
}

async function expectAnyVisible(page, builders, timeout = 15000) {
  const started = Date.now();

  while (Date.now() - started <= timeout) {
    for (const build of builders) {
      if (await isVisible(build(), 750)) {
        return;
      }
    }
    await page.waitForTimeout(300);
  }

  throw new Error("Expected UI elements were not visible within timeout.");
}

function findLikelyUserName(text) {
  const excludedLabels = new Set([
    "Información General",
    "Detalles de la Cuenta",
    "Tus Negocios",
    "Sección Legal",
    "BUSINESS PLAN",
    "Cambiar Plan",
    "Cuenta creada",
    "Estado activo",
    "Idioma seleccionado",
    "Agregar Negocio",
    "Administrar Negocios",
    "Términos y Condiciones",
    "Política de Privacidad",
    "Crear Nuevo Negocio",
    "Nombre del Negocio",
  ]);

  return text
    .split("\n")
    .map((line) => line.trim())
    .find(
      (line) =>
        !excludedLabels.has(line) &&
        /^[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}(?:\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}){1,2}$/.test(line)
    );
}

async function maybeClickGoogleAccount(googlePage) {
  const accountRow = googlePage.getByText(ACCOUNT_EMAIL, { exact: true });
  if (await isVisible(accountRow, 5000)) {
    await accountRow.click();
    await waitForUiLoad(googlePage);
  }
}

async function ensureMiNegocioExpanded(page) {
  if (await isVisible(page.getByText("Agregar Negocio", { exact: true }), 1500)) {
    return;
  }

  if (await isVisible(page.getByText("Mi Negocio", { exact: true }), 2000)) {
    await clickFirstVisible(page, [
      () => page.getByRole("button", { name: "Mi Negocio", exact: true }),
      () => page.getByRole("link", { name: "Mi Negocio", exact: true }),
      () => page.getByText("Mi Negocio", { exact: true }),
    ]);
  }

  if (!(await isVisible(page.getByText("Agregar Negocio", { exact: true }), 2000))) {
    await clickFirstVisible(page, [
      () => page.getByRole("button", { name: "Negocio", exact: false }),
      () => page.getByRole("link", { name: "Negocio", exact: false }),
      () => page.getByText("Negocio", { exact: false }),
    ]);
  }
}

async function main() {
  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL;
  if (!loginUrl) {
    throw new Error(
      "Missing SALEADS_LOGIN_URL (or SALEADS_URL). Provide the current environment login URL instead of hardcoding it."
    );
  }

  const headless = process.env.HEADLESS !== "false";
  const outputRoot =
    process.env.SALEADS_ARTIFACTS_DIR ||
    path.join(__dirname, "artifacts", `${TEST_NAME}_${utcStamp()}`);

  await fs.mkdir(outputRoot, { recursive: true });

  const browser = await chromium.launch({ headless });
  const context = await browser.newContext({ viewport: { width: 1680, height: 1000 } });
  const page = await context.newPage();

  const report = Object.fromEntries(
    REQUIRED_REPORT_FIELDS.map((field) => [field, { status: "FAIL", details: "Not executed." }])
  );

  const legalUrls = {
    terminosYCondicionesUrl: null,
    politicaDePrivacidadUrl: null,
  };

  const saveCheckpoint = async (filename, targetPage = page, fullPage = false) => {
    const filePath = path.join(outputRoot, filename);
    await targetPage.screenshot({ path: filePath, fullPage });
  };

  const runField = async (field, action) => {
    try {
      await action();
      report[field] = { status: "PASS", details: "Validation succeeded." };
    } catch (error) {
      report[field] = { status: "FAIL", details: error.message };
    }
  };

  const openLegalLink = async (linkText, expectedHeading, screenshotName, urlField) => {
    const legalPopupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);

    await clickFirstVisible(page, [
      () => page.getByRole("link", { name: linkText, exact: true }),
      () => page.getByRole("button", { name: linkText, exact: true }),
      () => page.getByText(linkText, { exact: true }),
    ]);

    const popup = await legalPopupPromise;
    const legalPage = popup || page;

    if (popup) {
      await waitForUiLoad(legalPage);
    }

    await expectVisibleByText(legalPage, expectedHeading);

    const legalBody = (await legalPage.locator("body").innerText()).trim();
    if (legalBody.replace(/\s+/g, " ").length < 120) {
      throw new Error(`Expected legal content for "${expectedHeading}" was too short or missing.`);
    }

    await saveCheckpoint(screenshotName, legalPage, true);
    legalUrls[urlField] = legalPage.url();

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUiLoad(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
      await waitForUiLoad(page);
    }
  };

  try {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUiLoad(page);

    await runField("Login", async () => {
      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);

      await clickFirstVisible(page, [
        () => page.getByRole("button", { name: "Sign in with Google", exact: true }),
        () => page.getByRole("button", { name: "Iniciar sesión con Google", exact: true }),
        () => page.getByRole("button", { name: "Continuar con Google", exact: true }),
        () => page.getByText("Sign in with Google", { exact: true }),
        () => page.getByText("Iniciar sesión con Google", { exact: true }),
      ]);

      const popup = await popupPromise;
      if (popup) {
        await waitForUiLoad(popup);
        await maybeClickGoogleAccount(popup);
      } else {
        await maybeClickGoogleAccount(page);
      }

      await expectAnyVisible(page, [
        () => page.locator("main"),
        () => page.getByRole("heading"),
      ]);

      await expectAnyVisible(page, [
        () => page.locator("aside"),
        () => page.getByRole("navigation"),
        () => page.getByText("Negocio", { exact: false }),
      ]);

      await saveCheckpoint("01-dashboard-loaded.png", page, true);
    });

    await runField("Mi Negocio menu", async () => {
      await ensureMiNegocioExpanded(page);

      await expectVisibleByText(page, "Agregar Negocio");
      await expectVisibleByText(page, "Administrar Negocios");
      await saveCheckpoint("02-mi-negocio-menu-expanded.png");
    });

    await runField("Agregar Negocio modal", async () => {
      await ensureMiNegocioExpanded(page);

      await clickFirstVisible(page, [
        () => page.getByRole("button", { name: "Agregar Negocio", exact: true }),
        () => page.getByRole("link", { name: "Agregar Negocio", exact: true }),
        () => page.getByText("Agregar Negocio", { exact: true }),
      ]);

      await expectVisibleByText(page, "Crear Nuevo Negocio");
      await expectVisibleByText(page, "Nombre del Negocio");
      await expectVisibleByText(page, "Tienes 2 de 3 negocios");
      await expectVisibleByText(page, "Cancelar");
      await expectVisibleByText(page, "Crear Negocio");

      const nameField = page.getByLabel("Nombre del Negocio", { exact: true });
      if (await isVisible(nameField, 2000)) {
        await nameField.click();
        await nameField.fill("Negocio Prueba Automatización");
      }

      await saveCheckpoint("03-agregar-negocio-modal.png");
      await clickFirstVisible(page, [
        () => page.getByRole("button", { name: "Cancelar", exact: true }),
        () => page.getByText("Cancelar", { exact: true }),
      ]);
    });

    await runField("Administrar Negocios view", async () => {
      await ensureMiNegocioExpanded(page);

      await clickFirstVisible(page, [
        () => page.getByRole("button", { name: "Administrar Negocios", exact: true }),
        () => page.getByRole("link", { name: "Administrar Negocios", exact: true }),
        () => page.getByText("Administrar Negocios", { exact: true }),
      ]);

      await expectVisibleByText(page, "Información General");
      await expectVisibleByText(page, "Detalles de la Cuenta");
      await expectVisibleByText(page, "Tus Negocios");
      await expectVisibleByText(page, "Sección Legal");
      await saveCheckpoint("04-administrar-negocios-view.png", page, true);
    });

    await runField("Información General", async () => {
      await expectVisibleByText(page, "BUSINESS PLAN");
      await expectVisibleByText(page, "Cambiar Plan");

      const expectedUserName = process.env.SALEADS_EXPECTED_USER_NAME;
      if (expectedUserName) {
        await expectVisibleByText(page, expectedUserName);
      } else {
        const screenText = await page.locator("body").innerText();
        const likelyUserName = findLikelyUserName(screenText);

        if (!likelyUserName) {
          throw new Error(
            "User name was not clearly detected. Set SALEADS_EXPECTED_USER_NAME for strict validation."
          );
        }
      }

      const userEmailVisible =
        (await isVisible(page.getByText("@", { exact: false }), 4000)) ||
        (await isVisible(page.locator("text=/[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}/"), 4000));

      if (!userEmailVisible) {
        throw new Error("User email was not detected in Información General.");
      }
    });

    await runField("Detalles de la Cuenta", async () => {
      await expectVisibleByText(page, "Cuenta creada");
      await expectVisibleByText(page, "Estado activo");
      await expectVisibleByText(page, "Idioma seleccionado");
    });

    await runField("Tus Negocios", async () => {
      await expectVisibleByText(page, "Tus Negocios");
      await expectVisibleByText(page, "Agregar Negocio");
      await expectVisibleByText(page, "Tienes 2 de 3 negocios");
    });

    await runField("Términos y Condiciones", async () => {
      await openLegalLink(
        "Términos y Condiciones",
        "Términos y Condiciones",
        "05-terminos-y-condiciones.png",
        "terminosYCondicionesUrl"
      );
    });

    await runField("Política de Privacidad", async () => {
      await openLegalLink(
        "Política de Privacidad",
        "Política de Privacidad",
        "06-politica-de-privacidad.png",
        "politicaDePrivacidadUrl"
      );
    });
  } finally {
    await browser.close();
  }

  const statusSummary = Object.fromEntries(
    Object.entries(report).map(([field, info]) => [field, info.status])
  );

  const finalReport = {
    testName: TEST_NAME,
    executedAt: new Date().toISOString(),
    loginUrl,
    artifactsPath: outputRoot,
    statuses: statusSummary,
    details: report,
    legalUrls,
  };

  const reportPath = path.join(outputRoot, "final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");

  console.log(JSON.stringify(finalReport, null, 2));

  const hasFailures = Object.values(statusSummary).some((value) => value !== "PASS");
  if (hasFailures) {
    process.exitCode = 1;
  }
}

main().catch((error) => {
  console.error("Fatal error executing SaleADS Mi Negocio workflow:", error);
  process.exit(1);
});
