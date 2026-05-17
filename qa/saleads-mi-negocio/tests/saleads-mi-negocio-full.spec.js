const fs = require("fs/promises");
const path = require("path");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";

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

function createReportSkeleton() {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
}

function escapeRegExp(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForTimeout(900);
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function findVisibleLocator(page, description, candidates, timeoutMs = 20000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      const locator = candidate.first();
      const isVisible = await locator.isVisible().catch(() => false);
      if (isVisible) {
        return locator;
      }
    }
    await page.waitForTimeout(250);
  }

  throw new Error(`Unable to find visible element for "${description}"`);
}

async function maybeFindVisibleLocator(page, candidates, timeoutMs = 5000) {
  try {
    return await findVisibleLocator(page, "optional element", candidates, timeoutMs);
  } catch {
    return null;
  }
}

async function validateLegalLink({
  page,
  appPage,
  label,
  headingRegex,
  screenshotName,
  linkCandidates,
  context,
  urls
}) {
  const link = await findVisibleLocator(page, label, linkCandidates);
  const popupPromise = context.waitForEvent("page", { timeout: 15000 }).catch(() => null);

  await clickAndWait(page, link);

  const popup = await popupPromise;
  const legalPage = popup || page;

  await legalPage.waitForLoadState("domcontentloaded").catch(() => {});
  await legalPage.waitForTimeout(1200);

  const heading = await findVisibleLocator(
    legalPage,
    `${label} heading`,
    [
      legalPage.getByRole("heading", { name: headingRegex }),
      legalPage.getByText(headingRegex)
    ],
    25000
  );
  await expect(heading).toBeVisible();

  const legalText = await findVisibleLocator(
    legalPage,
    `${label} legal content`,
    [
      legalPage.getByText(
        /t[eé]rminos|condiciones|privacidad|datos personales|usuario|servicio|acepta|cookies/i
      ),
      legalPage.locator("main p, article p, div p")
    ],
    25000
  );
  await expect(legalText).toBeVisible();

  urls[label] = legalPage.url();
  await legalPage.screenshot({ path: screenshotName, fullPage: true });

  if (popup) {
    await popup.close({ runBeforeUnload: true }).catch(() => {});
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await legalPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(legalPage);
  }
}

test("SaleADS Mi Negocio full workflow", async ({ page, context }, testInfo) => {
  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.BASE_URL || "";
  const report = createReportSkeleton();
  const failures = [];
  const legalUrls = {};

  async function runStep(reportField, fn) {
    try {
      await fn();
      report[reportField] = "PASS";
    } catch (error) {
      report[reportField] = "FAIL";
      failures.push({
        step: reportField,
        message: error instanceof Error ? error.message : String(error)
      });
      // Continue execution to gather as much evidence as possible.
      console.error(`Step failed [${reportField}]`, error);
    }
  }

  await runStep("Login", async () => {
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "No SALEADS_LOGIN_URL/BASE_URL provided. Set one so the test can start on the SaleADS login page."
      );
    }

    const directGoogleButton = await maybeFindVisibleLocator(page, [
      page.getByRole("button", { name: /google/i }),
      page.getByRole("link", { name: /google/i }),
      page.locator("button, [role='button'], a").filter({ hasText: /google/i })
    ]);

    const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);

    if (directGoogleButton) {
      await clickAndWait(page, directGoogleButton);
    } else {
      const loginButton = await findVisibleLocator(page, "login button", [
        page.getByRole("button", {
          name: /iniciar sesi[oó]n|sign in|login|continuar|acceder/i
        }),
        page.getByRole("link", {
          name: /iniciar sesi[oó]n|sign in|login|continuar|acceder/i
        })
      ]);
      await clickAndWait(page, loginButton);

      const googleOption = await findVisibleLocator(page, "Google sign-in option", [
        page.getByRole("button", { name: /google/i }),
        page.getByRole("link", { name: /google/i }),
        page.locator("button, [role='button'], a").filter({ hasText: /google/i })
      ]);
      await clickAndWait(page, googleOption);
    }

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded").catch(() => {});
      await popup.bringToFront();
      await waitForUi(popup);

      const escapedEmailRegex = new RegExp(escapeRegExp(GOOGLE_ACCOUNT_EMAIL), "i");
      const accountSelector = await maybeFindVisibleLocator(popup, [
        popup.getByRole("button", { name: escapedEmailRegex }),
        popup.getByText(escapedEmailRegex)
      ]);
      if (accountSelector) {
        await clickAndWait(popup, accountSelector);
      }

      await popup.waitForEvent("close", { timeout: 45000 }).catch(() => {});
      await page.bringToFront();
      await waitForUi(page);
    } else {
      const escapedEmailRegex = new RegExp(escapeRegExp(GOOGLE_ACCOUNT_EMAIL), "i");
      const accountSelectorInPage = await maybeFindVisibleLocator(page, [
        page.getByRole("button", { name: escapedEmailRegex }),
        page.getByText(escapedEmailRegex)
      ]);
      if (accountSelectorInPage) {
        await clickAndWait(page, accountSelectorInPage);
      }
    }

    await findVisibleLocator(page, "main application interface", [
      page.locator("aside"),
      page.locator("nav"),
      page.getByText(/mi negocio|negocio|dashboard|inicio/i)
    ], 60000);

    const sidebar = await findVisibleLocator(page, "left sidebar navigation", [
      page.locator("aside"),
      page.locator("nav")
    ]);
    await expect(sidebar).toBeVisible();
    await page.screenshot({
      path: testInfo.outputPath("01-dashboard-loaded.png"),
      fullPage: true
    });
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = await findVisibleLocator(page, "Negocio section", [
      page.getByRole("button", { name: /^negocio$/i }),
      page.getByRole("link", { name: /^negocio$/i }),
      page.getByText(/^negocio$/i)
    ]);
    await clickAndWait(page, negocioSection);

    const miNegocioOption = await findVisibleLocator(page, "Mi Negocio option", [
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i)
    ]);
    await clickAndWait(page, miNegocioOption);

    await expect(page.getByText(/agregar negocio/i)).toBeVisible();
    await expect(page.getByText(/administrar negocios/i)).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath("02-mi-negocio-expanded-menu.png"),
      fullPage: true
    });
  });

  await runStep("Agregar Negocio modal", async () => {
    const addBusinessOption = await findVisibleLocator(page, "Agregar Negocio", [
      page.getByRole("button", { name: /agregar negocio/i }),
      page.getByRole("link", { name: /agregar negocio/i }),
      page.getByText(/agregar negocio/i)
    ]);
    await clickAndWait(page, addBusinessOption);

    const modalTitle = await findVisibleLocator(page, "Crear Nuevo Negocio title", [
      page.getByRole("heading", { name: /crear nuevo negocio/i }),
      page.getByText(/crear nuevo negocio/i)
    ]);
    await expect(modalTitle).toBeVisible();

    const businessNameInput = await findVisibleLocator(page, "Nombre del Negocio input", [
      page.getByLabel(/nombre del negocio/i),
      page.getByPlaceholder(/nombre del negocio/i),
      page.locator("input[name*='nombre' i], input[id*='nombre' i]")
    ]);
    await expect(businessNameInput).toBeVisible();

    await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");

    await page.screenshot({
      path: testInfo.outputPath("03-agregar-negocio-modal.png"),
      fullPage: true
    });

    const cancelButton = await findVisibleLocator(page, "Cancelar button", [
      page.getByRole("button", { name: /cancelar/i }),
      page.getByText(/^cancelar$/i)
    ]);
    await clickAndWait(page, cancelButton);
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarVisible = await maybeFindVisibleLocator(page, [
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i)
    ]);

    if (!administrarVisible) {
      const miNegocioOption = await findVisibleLocator(page, "Mi Negocio option", [
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i)
      ]);
      await clickAndWait(page, miNegocioOption);
    }

    const administrarNegociosOption = await findVisibleLocator(page, "Administrar Negocios", [
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i)
    ]);
    await clickAndWait(page, administrarNegociosOption);

    await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath("04-administrar-negocios-account-page.png"),
      fullPage: true
    });
  });

  await runStep("Información General", async () => {
    const accountToken = GOOGLE_ACCOUNT_EMAIL.split("@")[0];
    await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible();
    await expect(
      await findVisibleLocator(page, "user name", [
        page.getByText(new RegExp(escapeRegExp(accountToken), "i")),
        page.getByText(/nombre/i),
        page.getByText(/usuario/i)
      ], 12000)
    ).toBeVisible();
    await expect(page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/)).toBeVisible();
    await expect(page.getByText(/business plan/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
    await expect(page.getByText(/cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/estado activo/i)).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i)).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /agregar negocio/i })).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
  });

  await runStep("Términos y Condiciones", async () => {
    await validateLegalLink({
      page,
      appPage: page,
      label: "Términos y Condiciones",
      headingRegex: /t[eé]rminos y condiciones/i,
      screenshotName: testInfo.outputPath("05-terminos-y-condiciones.png"),
      linkCandidates: [
        page.getByRole("link", { name: /t[eé]rminos y condiciones/i }),
        page.getByRole("button", { name: /t[eé]rminos y condiciones/i }),
        page.getByText(/t[eé]rminos y condiciones/i)
      ],
      context,
      urls: legalUrls
    });
  });

  await runStep("Política de Privacidad", async () => {
    await validateLegalLink({
      page,
      appPage: page,
      label: "Política de Privacidad",
      headingRegex: /pol[ií]tica de privacidad/i,
      screenshotName: testInfo.outputPath("06-politica-de-privacidad.png"),
      linkCandidates: [
        page.getByRole("link", { name: /pol[ií]tica de privacidad/i }),
        page.getByRole("button", { name: /pol[ií]tica de privacidad/i }),
        page.getByText(/pol[ií]tica de privacidad/i)
      ],
      context,
      urls: legalUrls
    });
  });

  const artifactsDir = path.resolve(__dirname, "../artifacts");
  await fs.mkdir(artifactsDir, { recursive: true });

  const finalReport = {
    workflow: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    loginUrlUsed: loginUrl || null,
    finalAppUrl: page.url(),
    legalUrls,
    results: report,
    failures
  };

  const reportJson = JSON.stringify(finalReport, null, 2);
  await fs.writeFile(path.join(artifactsDir, "saleads-mi-negocio-final-report.json"), reportJson);
  await fs.writeFile(testInfo.outputPath("saleads-mi-negocio-final-report.json"), reportJson);

  await testInfo.attach("saleads-mi-negocio-final-report", {
    body: reportJson,
    contentType: "application/json"
  });

  console.log("Final validation report:");
  console.table(report);
  console.log("Captured legal URLs:", legalUrls);

  if (failures.length > 0) {
    throw new Error(
      `Mi Negocio workflow has ${failures.length} failing validation step(s). See final report for details.`
    );
  }
});
