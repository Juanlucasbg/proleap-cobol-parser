const fs = require("fs/promises");
const path = require("path");
const { test, expect } = require("@playwright/test");

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

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => undefined);
  await page.waitForTimeout(500);
}

async function firstVisible(locator) {
  const count = await locator.count();
  for (let i = 0; i < count; i += 1) {
    const item = locator.nth(i);
    if (await item.isVisible().catch(() => false)) {
      return item;
    }
  }
  return null;
}

async function findVisibleByText(page, textOrRegex) {
  return firstVisible(page.getByText(textOrRegex, { exact: false }));
}

async function clickFirstVisibleByText(page, patterns, options = {}) {
  const roles = options.roles ?? ["button", "link", "menuitem"];
  const waitAfterClick = options.waitAfterClick ?? true;

  for (const pattern of patterns) {
    for (const role of roles) {
      const locator = await firstVisible(page.getByRole(role, { name: pattern }));
      if (locator) {
        await locator.click();
        if (waitAfterClick) {
          await waitForUiLoad(page);
        }
        return locator;
      }
    }

    const fallback = await findVisibleByText(page, pattern);
    if (fallback) {
      await fallback.click();
      if (waitAfterClick) {
        await waitForUiLoad(page);
      }
      return fallback;
    }
  }

  throw new Error(
    `Could not find a visible element with text patterns: ${patterns
      .map((entry) => entry.toString())
      .join(", ")}`
  );
}

async function ensureVisibleText(page, textOrRegex, message) {
  const locator = await findVisibleByText(page, textOrRegex);
  expect(locator, message).not.toBeNull();
}

async function attachScreenshot(page, testInfo, fileName, fullPage = false) {
  const screenshotPath = testInfo.outputPath(fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(fileName, {
    path: screenshotPath,
    contentType: "image/png"
  });
}

async function openLegalPage({
  page,
  context,
  linkPattern,
  headingPattern,
  screenshotName,
  testInfo
}) {
  const previousUrl = page.url();
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);

  await clickFirstVisibleByText(page, [linkPattern], {
    roles: ["link", "button"],
    waitAfterClick: false
  });

  const popup = await popupPromise;
  const legalPage = popup ?? page;
  await waitForUiLoad(legalPage);

  await ensureVisibleText(
    legalPage,
    headingPattern,
    `Expected heading ${headingPattern.toString()} in legal page`
  );

  const legalText = await legalPage.locator("body").innerText();
  expect(
    legalText.replace(/\s+/g, " ").trim().length,
    "Expected legal content text to be visible"
  ).toBeGreaterThan(120);

  await attachScreenshot(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUiLoad(page);
  } else if (page.url() !== previousUrl) {
    await page.goBack().catch(() => undefined);
    await waitForUiLoad(page);
  }

  return finalUrl;
}

test("saleads mi negocio full workflow", async ({ page, context }, testInfo) => {
  const report = Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: "FAIL", details: "Not executed" }])
  );

  const markPass = (field, details = "Validation passed") => {
    report[field] = { status: "PASS", details };
  };

  const markFail = (field, error) => {
    const message = error instanceof Error ? error.message : String(error);
    report[field] = { status: "FAIL", details: message };
  };

  const loginUrl = process.env.SALEADS_LOGIN_URL;

  if (page.url() === "about:blank") {
    if (!loginUrl) {
      throw new Error(
        "No active SaleADS login page detected. Provide SALEADS_LOGIN_URL to run this test in any environment."
      );
    }
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUiLoad(page);
  }

  let loginPassed = false;

  try {
    await clickFirstVisibleByText(page, [
      /sign in with google/i,
      /iniciar sesi[óo]n con google/i,
      /continuar con google/i,
      /google/i
    ]);

    const accountChoice = await findVisibleByText(page, /juanlucasbarbiergarzon@gmail\.com/i);
    if (accountChoice) {
      await accountChoice.click();
      await waitForUiLoad(page);
    }

    const sidebarCandidates = [
      page.locator("aside"),
      page.getByRole("navigation"),
      page.locator("[class*='sidebar'], [id*='sidebar']")
    ];

    let sidebarVisible = false;
    for (const candidate of sidebarCandidates) {
      const visible = await firstVisible(candidate);
      if (visible) {
        sidebarVisible = true;
        break;
      }
    }

    expect(sidebarVisible, "Expected left sidebar navigation to be visible").toBeTruthy();
    await attachScreenshot(page, testInfo, "01-dashboard-loaded.png", true);
    markPass("Login");
    loginPassed = true;
  } catch (error) {
    markFail("Login", error);
  }

  if (!loginPassed) {
    for (const field of REPORT_FIELDS) {
      if (field !== "Login") {
        report[field] = {
          status: "FAIL",
          details: "Skipped because login did not complete."
        };
      }
    }

    const reportPath = testInfo.outputPath("saleads-mi-negocio-report.json");
    await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");
    await testInfo.attach("saleads-mi-negocio-report", {
      path: reportPath,
      contentType: "application/json"
    });

    expect(loginPassed, "Login is required to continue workflow validations").toBeTruthy();
    return;
  }

  try {
    await clickFirstVisibleByText(page, [/Negocio/i], { roles: ["button", "link"] }).catch(
      () => undefined
    );
    await clickFirstVisibleByText(page, [/Mi Negocio/i], { roles: ["button", "link", "menuitem"] });
    await ensureVisibleText(page, /Agregar Negocio/i, "Expected 'Agregar Negocio' to be visible");
    await ensureVisibleText(
      page,
      /Administrar Negocios/i,
      "Expected 'Administrar Negocios' to be visible"
    );
    await attachScreenshot(page, testInfo, "02-mi-negocio-menu-expanded.png");
    markPass("Mi Negocio menu");
  } catch (error) {
    markFail("Mi Negocio menu", error);
  }

  try {
    await clickFirstVisibleByText(page, [/Agregar Negocio/i], { roles: ["button", "link", "menuitem"] });
    await ensureVisibleText(page, /Crear Nuevo Negocio/i, "Expected modal title 'Crear Nuevo Negocio'");
    await ensureVisibleText(page, /Nombre del Negocio/i, "Expected input label 'Nombre del Negocio'");
    await ensureVisibleText(page, /Tienes 2 de 3 negocios/i, "Expected business quota text");
    await ensureVisibleText(page, /Cancelar/i, "Expected 'Cancelar' button");
    await ensureVisibleText(page, /Crear Negocio/i, "Expected 'Crear Negocio' button");

    const businessInput =
      (await firstVisible(page.getByLabel(/Nombre del Negocio/i))) ||
      (await firstVisible(page.getByPlaceholder(/Nombre del Negocio/i))) ||
      (await firstVisible(page.locator("input")));
    expect(businessInput, "Expected business name input").not.toBeNull();

    await businessInput.fill("Negocio Prueba Automatización");
    await clickFirstVisibleByText(page, [/Cancelar/i], { roles: ["button", "link"] });
    await attachScreenshot(page, testInfo, "03-agregar-negocio-modal.png");
    markPass("Agregar Negocio modal");
  } catch (error) {
    markFail("Agregar Negocio modal", error);
  }

  try {
    await clickFirstVisibleByText(page, [/Mi Negocio/i], { roles: ["button", "link", "menuitem"] }).catch(
      () => undefined
    );
    await clickFirstVisibleByText(page, [/Administrar Negocios/i], {
      roles: ["button", "link", "menuitem"]
    });
    await ensureVisibleText(page, /Informaci[óo]n General/i, "Expected 'Información General' section");
    await ensureVisibleText(page, /Detalles de la Cuenta/i, "Expected 'Detalles de la Cuenta' section");
    await ensureVisibleText(page, /Tus Negocios/i, "Expected 'Tus Negocios' section");
    await ensureVisibleText(page, /Secci[óo]n Legal/i, "Expected 'Sección Legal' section");
    await attachScreenshot(page, testInfo, "04-administrar-negocios.png", true);
    markPass("Administrar Negocios view");
  } catch (error) {
    markFail("Administrar Negocios view", error);
  }

  try {
    await ensureVisibleText(page, /BUSINESS PLAN/i, "Expected plan text 'BUSINESS PLAN'");
    await ensureVisibleText(page, /Cambiar Plan/i, "Expected 'Cambiar Plan' button");

    const userEmail = await findVisibleByText(page, /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/);
    expect(userEmail, "Expected user email to be visible").not.toBeNull();

    const userNameHint = await findVisibleByText(
      page,
      /[A-ZÁÉÍÓÚÑ][A-Za-zÁÉÍÓÚÑáéíóúñ]+(?:\s+[A-ZÁÉÍÓÚÑ][A-Za-zÁÉÍÓÚÑáéíóúñ]+)+/
    );
    expect(userNameHint, "Expected user name to be visible").not.toBeNull();

    markPass("Información General");
  } catch (error) {
    markFail("Información General", error);
  }

  try {
    await ensureVisibleText(page, /Cuenta creada/i, "Expected 'Cuenta creada' text");
    await ensureVisibleText(page, /Estado activo/i, "Expected 'Estado activo' text");
    await ensureVisibleText(page, /Idioma seleccionado/i, "Expected 'Idioma seleccionado' text");
    markPass("Detalles de la Cuenta");
  } catch (error) {
    markFail("Detalles de la Cuenta", error);
  }

  try {
    await ensureVisibleText(page, /Tus Negocios/i, "Expected 'Tus Negocios' title");
    await ensureVisibleText(page, /Agregar Negocio/i, "Expected 'Agregar Negocio' button in business section");
    await ensureVisibleText(page, /Tienes 2 de 3 negocios/i, "Expected business quota in section");
    markPass("Tus Negocios");
  } catch (error) {
    markFail("Tus Negocios", error);
  }

  try {
    const termsUrl = await openLegalPage({
      page,
      context,
      linkPattern: /T[ée]rminos y Condiciones/i,
      headingPattern: /T[ée]rminos y Condiciones/i,
      screenshotName: "05-terminos-y-condiciones.png",
      testInfo
    });
    markPass("Términos y Condiciones", `Final URL: ${termsUrl}`);
  } catch (error) {
    markFail("Términos y Condiciones", error);
  }

  try {
    const privacyUrl = await openLegalPage({
      page,
      context,
      linkPattern: /Pol[íi]tica de Privacidad/i,
      headingPattern: /Pol[íi]tica de Privacidad/i,
      screenshotName: "06-politica-de-privacidad.png",
      testInfo
    });
    markPass("Política de Privacidad", `Final URL: ${privacyUrl}`);
  } catch (error) {
    markFail("Política de Privacidad", error);
  }

  const reportPath = testInfo.outputPath("saleads-mi-negocio-report.json");
  await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf8");
  await testInfo.attach("saleads-mi-negocio-report", {
    path: reportPath,
    contentType: "application/json"
  });

  const failedChecks = Object.entries(report)
    .filter(([, result]) => result.status === "FAIL")
    .map(([field, result]) => `${field}: ${result.details}`);

  expect(
    failedChecks,
    `Validation failures:\n${failedChecks.length ? failedChecks.join("\n") : "none"}`
  ).toEqual([]);
});
