const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const REPORT_KEYS = [
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

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await sleep(350);
}

async function firstVisibleLocator(candidates, timeoutPerLocator = 2000) {
  for (const locator of candidates) {
    const target = locator.first();
    const visible = await target.isVisible({ timeout: timeoutPerLocator }).catch(() => false);
    if (visible) {
      return target;
    }
  }
  return null;
}

async function mustFindVisible(candidates, description, timeoutMs = 30000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    const found = await firstVisibleLocator(candidates, 1200);
    if (found) {
      return found;
    }
    await sleep(250);
  }

  throw new Error(`Could not find visible element for: ${description}`);
}

async function clickAndWait(page, candidates, description) {
  const target = await mustFindVisible(candidates, description);
  await target.click();
  await waitForUiLoad(page);
  return target;
}

async function captureCheckpoint(page, testInfo, fileName, fullPage = false) {
  const filePath = testInfo.outputPath(fileName);
  await page.screenshot({ path: filePath, fullPage });
  await testInfo.attach(fileName, {
    path: filePath,
    contentType: "image/png"
  });
}

async function maybeSelectGoogleAccount(authPage) {
  const emailRegex = new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i");
  const account = await firstVisibleLocator(
    [
      authPage.getByRole("button", { name: emailRegex }),
      authPage.getByRole("link", { name: emailRegex }),
      authPage.getByText(emailRegex),
      authPage.locator(`[data-email="${GOOGLE_ACCOUNT_EMAIL}"]`)
    ],
    3000
  );

  if (account) {
    await account.click();
    await waitForUiLoad(authPage);
  }
}

async function validateLegalPage({
  page,
  testInfo,
  reportNotes,
  urls,
  linkRegex,
  headingRegex,
  screenshotName,
  reportKey
}) {
  const link = await mustFindVisible(
    [
      page.getByRole("link", { name: linkRegex }),
      page.getByRole("button", { name: linkRegex }),
      page.getByText(linkRegex)
    ],
    `${reportKey} link`
  );

  const popupPromise = page.context().waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await link.click();
  await waitForUiLoad(page);

  const popup = await popupPromise;
  const legalPage = popup || page;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded", { timeout: 15000 });
    await waitForUiLoad(popup);
  }

  await mustFindVisible(
    [
      legalPage.getByRole("heading", { name: headingRegex }),
      legalPage.getByText(headingRegex)
    ],
    `${reportKey} heading`,
    25000
  );

  await mustFindVisible(
    [
      legalPage.locator("main p"),
      legalPage.locator("article p"),
      legalPage.locator("body p"),
      legalPage.getByText(/datos|informaci[oó]n|uso|servicio|privacidad|t[eé]rminos|condiciones/i)
    ],
    `${reportKey} legal content`,
    25000
  );

  urls[reportKey] = legalPage.url();
  reportNotes[`${reportKey}_url`] = legalPage.url();

  await captureCheckpoint(legalPage, testInfo, screenshotName, true);

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUiLoad(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUiLoad(page);
  }
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  test.setTimeout(240000);

  const report = Object.fromEntries(REPORT_KEYS.map((key) => [key, "FAIL"]));
  const reportNotes = {};
  const urls = {};

  async function runSection(key, fn) {
    try {
      await fn();
      report[key] = "PASS";
    } catch (error) {
      report[key] = "FAIL";
      reportNotes[key] = error instanceof Error ? error.message : String(error);
    }
  }

  await runSection("Login", async () => {
    const configuredUrl =
      process.env.SALEADS_START_URL || process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL;

    if (configuredUrl) {
      await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
      await waitForUiLoad(page);
    } else if (page.url() === "about:blank") {
      throw new Error("Set SALEADS_START_URL (or SALEADS_LOGIN_URL / SALEADS_BASE_URL) before running.");
    }

    const popupPromise = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);

    await clickAndWait(
      page,
      [
        page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i }),
        page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
        page.getByRole("button", { name: /iniciar sesi[oó]n|login|entrar/i })
      ],
      "login button"
    );

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded", { timeout: 15000 });
      await maybeSelectGoogleAccount(popup);
      await popup.waitForClose({ timeout: 45000 }).catch(() => {});
      await page.bringToFront();
      await waitForUiLoad(page);
    } else {
      await maybeSelectGoogleAccount(page);
      await waitForUiLoad(page);
    }

    await mustFindVisible(
      [
        page.getByText(/mi negocio|administrar negocios|dashboard|panel/i),
        page.locator("aside"),
        page.getByRole("navigation")
      ],
      "main app interface",
      45000
    );

    await mustFindVisible(
      [
        page.getByText(/^negocio$/i),
        page.getByRole("button", { name: /negocio/i }),
        page.getByRole("link", { name: /negocio/i })
      ],
      "left sidebar navigation",
      45000
    );

    await captureCheckpoint(page, testInfo, "01-dashboard.png", true);
  });

  await runSection("Mi Negocio menu", async () => {
    await clickAndWait(
      page,
      [
        page.getByRole("button", { name: /negocio/i }),
        page.getByRole("link", { name: /negocio/i }),
        page.getByText(/^negocio$/i)
      ],
      "Negocio section"
    );

    await clickAndWait(
      page,
      [
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i)
      ],
      "Mi Negocio option"
    );

    await mustFindVisible(
      [
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByRole("link", { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i)
      ],
      "Agregar Negocio option"
    );

    await mustFindVisible(
      [
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i)
      ],
      "Administrar Negocios option"
    );

    await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png");
  });

  await runSection("Agregar Negocio modal", async () => {
    await clickAndWait(
      page,
      [
        page.getByRole("button", { name: /^agregar negocio$/i }),
        page.getByRole("link", { name: /^agregar negocio$/i }),
        page.getByText(/^agregar negocio$/i)
      ],
      "Agregar Negocio menu action"
    );

    await mustFindVisible(
      [
        page.getByRole("heading", { name: /crear nuevo negocio/i }),
        page.getByText(/crear nuevo negocio/i)
      ],
      "Crear Nuevo Negocio title"
    );

    const nameField = await mustFindVisible(
      [
        page.getByLabel(/nombre del negocio/i),
        page.getByPlaceholder(/nombre del negocio/i),
        page.locator("input[type='text'], input:not([type])")
      ],
      "Nombre del Negocio field"
    );

    await mustFindVisible([page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)], "business usage text");
    await mustFindVisible([page.getByRole("button", { name: /cancelar/i })], "Cancelar button");
    await mustFindVisible([page.getByRole("button", { name: /crear negocio/i })], "Crear Negocio button");

    await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");

    await nameField.click();
    await nameField.fill("Negocio Prueba Automatizacion");
    await clickAndWait(page, [page.getByRole("button", { name: /cancelar/i })], "Cancelar modal button");
  });

  await runSection("Administrar Negocios view", async () => {
    await clickAndWait(
      page,
      [
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i)
      ],
      "Mi Negocio section reopen"
    );

    await clickAndWait(
      page,
      [
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i)
      ],
      "Administrar Negocios option"
    );

    await mustFindVisible([page.getByText(/informaci[oó]n general/i)], "Informacion General section", 30000);
    await mustFindVisible([page.getByText(/detalles de la cuenta/i)], "Detalles de la Cuenta section", 30000);
    await mustFindVisible([page.getByText(/tus negocios/i)], "Tus Negocios section", 30000);
    await mustFindVisible(
      [page.getByText(/secci[oó]n legal|t[eé]rminos y condiciones|pol[ií]tica de privacidad/i)],
      "Seccion Legal section",
      30000
    );

    await captureCheckpoint(page, testInfo, "04-administrar-negocios-cuenta.png", true);
  });

  await runSection("Informacion General", async () => {
    await mustFindVisible([page.getByText(/juan|lucas|barbier|garzon/i)], "user name");
    await mustFindVisible([page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)], "user email");
    await mustFindVisible([page.getByText(/business plan/i)], "BUSINESS PLAN text");
    await mustFindVisible(
      [page.getByRole("button", { name: /cambiar plan/i }), page.getByText(/cambiar plan/i)],
      "Cambiar Plan button"
    );
  });

  await runSection("Detalles de la Cuenta", async () => {
    await mustFindVisible([page.getByText(/cuenta creada/i)], "Cuenta creada");
    await mustFindVisible([page.getByText(/estado activo/i)], "Estado activo");
    await mustFindVisible([page.getByText(/idioma seleccionado/i)], "Idioma seleccionado");
  });

  await runSection("Tus Negocios", async () => {
    await mustFindVisible(
      [
        page.locator("ul, table, [role='table']").filter({ hasText: /negocio/i }),
        page.getByText(/tus negocios/i)
      ],
      "business list"
    );
    await mustFindVisible(
      [
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByRole("link", { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i)
      ],
      "Agregar Negocio button"
    );
    await mustFindVisible([page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)], "Tienes 2 de 3 negocios");
  });

  await runSection("Terminos y Condiciones", async () => {
    await validateLegalPage({
      page,
      testInfo,
      reportNotes,
      urls,
      linkRegex: /t[eé]rminos y condiciones/i,
      headingRegex: /t[eé]rminos y condiciones/i,
      screenshotName: "05-terminos-y-condiciones.png",
      reportKey: "Terminos y Condiciones"
    });
  });

  await runSection("Politica de Privacidad", async () => {
    await validateLegalPage({
      page,
      testInfo,
      reportNotes,
      urls,
      linkRegex: /pol[ií]tica de privacidad/i,
      headingRegex: /pol[ií]tica de privacidad/i,
      screenshotName: "06-politica-de-privacidad.png",
      reportKey: "Politica de Privacidad"
    });
  });

  const finalReport = {
    Login: report["Login"],
    "Mi Negocio menu": report["Mi Negocio menu"],
    "Agregar Negocio modal": report["Agregar Negocio modal"],
    "Administrar Negocios view": report["Administrar Negocios view"],
    "Información General": report["Informacion General"],
    "Detalles de la Cuenta": report["Detalles de la Cuenta"],
    "Tus Negocios": report["Tus Negocios"],
    "Términos y Condiciones": report["Terminos y Condiciones"],
    "Política de Privacidad": report["Politica de Privacidad"]
  };

  const structuredResult = {
    finalReport,
    urls,
    notes: reportNotes
  };

  console.log("SALEADS MI NEGOCIO FINAL REPORT");
  console.table(finalReport);
  console.log("URLs:", urls);
  if (Object.keys(reportNotes).length > 0) {
    console.log("Notes:", reportNotes);
  }

  await testInfo.attach("saleads_mi_negocio_final_report.json", {
    body: Buffer.from(JSON.stringify(structuredResult, null, 2)),
    contentType: "application/json"
  });

  const allPassed = Object.values(finalReport).every((status) => status === "PASS");
  expect(allPassed, `Validation failures: ${JSON.stringify(structuredResult, null, 2)}`).toBeTruthy();
});
