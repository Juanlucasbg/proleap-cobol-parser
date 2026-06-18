const fs = require("fs");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT_EMAIL ||
  "juanlucasbarbiergarzon@gmail.com";

const START_URL =
  process.env.SALEADS_START_URL ||
  process.env.SALEADS_BASE_URL ||
  process.env.BASE_URL;

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

const waitForUiToSettle = async (page) => {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForTimeout(700);
};

const clickAndWait = async (page, locator) => {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiToSettle(page);
};

const screenshotCheckpoint = async (page, testInfo, checkpointName, fullPage = false) => {
  const safeName = checkpointName.replace(/\s+/g, "-").toLowerCase();
  const screenshotPath = testInfo.outputPath(`${safeName}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(checkpointName, {
    path: screenshotPath,
    contentType: "image/png",
  });
};

const ensureVisibleByText = async (page, pattern, description) => {
  const locator = page.getByText(pattern).first();
  await expect(locator, `Missing text: ${description}`).toBeVisible();
  return locator;
};

const resolveFirstVisible = async (locators) => {
  for (const locator of locators) {
    if (await locator.first().isVisible().catch(() => false)) {
      return locator.first();
    }
  }
  return null;
};

test("saleads mi negocio full workflow", async ({ page, context }, testInfo) => {
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const evidence = {
    termsUrl: null,
    privacyUrl: null,
  };
  const errors = [];

  const recordFailure = (field, error) => {
    const message = error instanceof Error ? error.message : String(error);
    errors.push(`${field}: ${message}`);
  };

  const runField = async (field, action) => {
    try {
      await action();
      report[field] = "PASS";
    } catch (error) {
      report[field] = "FAIL";
      recordFailure(field, error);
    }
  };

  if (!START_URL) {
    throw new Error(
      "Missing start URL. Set SALEADS_START_URL (or SALEADS_BASE_URL/BASE_URL) to the login page of the target SaleADS environment.",
    );
  }

  await page.goto(START_URL, { waitUntil: "domcontentloaded" });
  await waitForUiToSettle(page);

  await runField("Login", async () => {
    const loginTrigger = await resolveFirstVisible([
      page.getByRole("button", { name: /google|sign in|iniciar sesi[oó]n/i }),
      page.getByRole("link", { name: /google|sign in|iniciar sesi[oó]n/i }),
      page.locator("button:has-text('Google')"),
      page.locator("a:has-text('Google')"),
      page.locator("[role='button']:has-text('Google')"),
    ]);

    if (!loginTrigger) {
      throw new Error("Google login button was not found on the login page.");
    }

    await clickAndWait(page, loginTrigger);

    const accountSelector = await resolveFirstVisible([
      page.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
      page.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")),
    ]);

    if (accountSelector) {
      await clickAndWait(page, accountSelector);
    }

    await ensureVisibleByText(page, /negocio|mi negocio/i, "sidebar navigation");

    const sidebar = await resolveFirstVisible([
      page.locator("aside"),
      page.locator("nav"),
    ]);
    if (!sidebar) {
      throw new Error("Sidebar navigation container is not visible.");
    }

    await screenshotCheckpoint(page, testInfo, "checkpoint-dashboard-loaded");
  });

  await runField("Mi Negocio menu", async () => {
    const negocioMenu = await resolveFirstVisible([
      page.getByRole("button", { name: /^negocio$/i }),
      page.getByText(/^negocio$/i),
      page.getByRole("button", { name: /negocio/i }),
      page.getByText(/negocio/i),
    ]);
    if (!negocioMenu) {
      throw new Error("Negocio menu entry was not found.");
    }
    await clickAndWait(page, negocioMenu);

    const miNegocioOption = await resolveFirstVisible([
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i),
    ]);
    if (!miNegocioOption) {
      throw new Error("'Mi Negocio' option was not found.");
    }
    await clickAndWait(page, miNegocioOption);

    await ensureVisibleByText(page, /agregar negocio/i, "Agregar Negocio");
    await ensureVisibleByText(page, /administrar negocios/i, "Administrar Negocios");
    await screenshotCheckpoint(page, testInfo, "checkpoint-mi-negocio-expanded");
  });

  await runField("Agregar Negocio modal", async () => {
    const addBusinessEntry = await resolveFirstVisible([
      page.getByRole("button", { name: /agregar negocio/i }),
      page.getByRole("link", { name: /agregar negocio/i }),
      page.getByText(/agregar negocio/i),
    ]);
    if (!addBusinessEntry) {
      throw new Error("Unable to find 'Agregar Negocio' entry.");
    }
    await clickAndWait(page, addBusinessEntry);

    await ensureVisibleByText(page, /crear nuevo negocio/i, "Crear Nuevo Negocio modal title");
    const businessNameInput = await resolveFirstVisible([
      page.getByLabel(/nombre del negocio/i),
      page.getByPlaceholder(/nombre del negocio/i),
      page.locator("input[name*='negocio' i]"),
    ]);
    if (!businessNameInput) {
      throw new Error("'Nombre del Negocio' input was not found.");
    }
    await ensureVisibleByText(page, /tienes 2 de 3 negocios/i, "business quota text");

    const cancelButton = await resolveFirstVisible([
      page.getByRole("button", { name: /cancelar/i }),
      page.getByText(/cancelar/i),
    ]);
    const createButton = await resolveFirstVisible([
      page.getByRole("button", { name: /crear negocio/i }),
      page.getByText(/crear negocio/i),
    ]);

    if (!cancelButton || !createButton) {
      throw new Error("Modal action buttons were not fully available.");
    }

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await screenshotCheckpoint(page, testInfo, "checkpoint-crear-negocio-modal");
    await clickAndWait(page, cancelButton);
  });

  await runField("Administrar Negocios view", async () => {
    const adminEntry = await resolveFirstVisible([
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i),
    ]);
    if (!adminEntry) {
      throw new Error("'Administrar Negocios' option was not found.");
    }
    await clickAndWait(page, adminEntry);

    await ensureVisibleByText(page, /informaci[oó]n general/i, "Información General section");
    await ensureVisibleByText(page, /detalles de la cuenta/i, "Detalles de la Cuenta section");
    await ensureVisibleByText(page, /tus negocios/i, "Tus Negocios section");
    await ensureVisibleByText(page, /secci[oó]n legal/i, "Sección Legal section");
    await screenshotCheckpoint(page, testInfo, "checkpoint-administrar-negocios-page", true);
  });

  await runField("Información General", async () => {
    await ensureVisibleByText(page, /informaci[oó]n general/i, "Información General heading");
    await ensureVisibleByText(page, /business plan/i, "BUSINESS PLAN");
    await ensureVisibleByText(page, /cambiar plan/i, "Cambiar Plan button");

    const userEmail = page.locator(
      "text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/",
    ).first();
    await expect(userEmail, "User email was not visible.").toBeVisible();

    const userNameCandidate = await resolveFirstVisible([
      page.getByText(/nombre/i),
      page.locator("h1, h2, h3").filter({ hasNotText: /informaci[oó]n general|business plan/i }),
    ]);
    if (!userNameCandidate) {
      throw new Error("User name was not visible.");
    }
  });

  await runField("Detalles de la Cuenta", async () => {
    await ensureVisibleByText(page, /detalles de la cuenta/i, "Detalles de la Cuenta heading");
    await ensureVisibleByText(page, /cuenta creada/i, "Cuenta creada");
    await ensureVisibleByText(page, /estado activo/i, "Estado activo");
    await ensureVisibleByText(page, /idioma seleccionado/i, "Idioma seleccionado");
  });

  await runField("Tus Negocios", async () => {
    const businessesHeading = await ensureVisibleByText(page, /tus negocios/i, "Tus Negocios heading");
    const businessesContainer = businessesHeading.locator("xpath=ancestor::*[self::section or self::div][1]");

    const listCandidates = businessesContainer.locator(
      "li, tr, [role='row'], [class*='business'], [class*='negocio']",
    );
    const listCount = await listCandidates.count();
    if (listCount === 0) {
      throw new Error("Business list content was not visible.");
    }

    await ensureVisibleByText(page, /agregar negocio/i, "Agregar Negocio button in Tus Negocios");
    await ensureVisibleByText(page, /tienes 2 de 3 negocios/i, "business quota text");
  });

  const validateLegalLink = async (field, linkTextPattern, headingPattern, screenshotName, urlKey) => {
    await runField(field, async () => {
      const legalLink = await resolveFirstVisible([
        page.getByRole("link", { name: linkTextPattern }),
        page.getByRole("button", { name: linkTextPattern }),
        page.getByText(linkTextPattern),
      ]);
      if (!legalLink) {
        throw new Error(`Legal link not found for ${field}.`);
      }

      const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
      await clickAndWait(page, legalLink);
      const popup = await popupPromise;

      const legalPage = popup || page;
      await waitForUiToSettle(legalPage);

      const headingLocator = await resolveFirstVisible([
        legalPage.getByRole("heading", { name: headingPattern }),
        legalPage.getByText(headingPattern),
      ]);
      if (!headingLocator) {
        throw new Error(`Heading '${headingPattern}' was not visible.`);
      }

      const legalBodyText = await legalPage.locator("body").innerText();
      if (legalBodyText.trim().length < 120) {
        throw new Error("Legal content appears too short or missing.");
      }

      evidence[urlKey] = legalPage.url();
      await screenshotCheckpoint(legalPage, testInfo, screenshotName, true);

      if (popup) {
        await popup.close();
        await page.bringToFront();
      } else {
        await page.goBack().catch(() => {});
        await waitForUiToSettle(page);
      }
    });
  };

  await validateLegalLink(
    "Términos y Condiciones",
    /t[eé]rminos y condiciones/i,
    /t[eé]rminos y condiciones/i,
    "checkpoint-terminos-y-condiciones",
    "termsUrl",
  );

  await validateLegalLink(
    "Política de Privacidad",
    /pol[ií]tica de privacidad/i,
    /pol[ií]tica de privacidad/i,
    "checkpoint-politica-de-privacidad",
    "privacyUrl",
  );

  const reportArtifact = {
    workflow: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    finalStatus: errors.length === 0 ? "PASS" : "FAIL",
    results: report,
    legalUrls: {
      terms: evidence.termsUrl,
      privacy: evidence.privacyUrl,
    },
    errors,
  };

  const reportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
  fs.writeFileSync(reportPath, JSON.stringify(reportArtifact, null, 2), "utf-8");
  await testInfo.attach("final-report-json", {
    path: reportPath,
    contentType: "application/json",
  });

  expect(
    errors,
    `Workflow validations failed:\n${errors.map((entry) => `- ${entry}`).join("\n")}`,
  ).toEqual([]);
});
