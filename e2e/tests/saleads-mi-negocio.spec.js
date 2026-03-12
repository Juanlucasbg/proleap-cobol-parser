const fs = require("node:fs/promises");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

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

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 10000 });
  } catch (_error) {
    // Some app views keep long-lived requests; domcontentloaded is enough there.
  }
}

async function clickVisibleByText(page, textOptions) {
  const options = Array.isArray(textOptions) ? textOptions : [textOptions];

  for (const option of options) {
    const matcher =
      option instanceof RegExp ? option : new RegExp(`\\b${escapeRegex(option)}\\b`, "i");

    const candidates = [
      page.getByRole("button", { name: matcher }).first(),
      page.getByRole("link", { name: matcher }).first(),
      page.getByRole("menuitem", { name: matcher }).first(),
      page.getByText(matcher).first()
    ];

    for (const locator of candidates) {
      try {
        await locator.waitFor({ state: "visible", timeout: 3500 });
        await locator.click();
        await waitForUi(page);
        return;
      } catch (_error) {
        // Try the next candidate.
      }
    }
  }

  throw new Error(`Unable to click any of these labels: ${options.join(", ")}`);
}

async function assertVisibleByText(page, value) {
  const matcher = value instanceof RegExp ? value : new RegExp(`\\b${escapeRegex(value)}\\b`, "i");
  await expect(page.getByText(matcher).first()).toBeVisible();
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const report = {};
  const runId = new Date().toISOString().replace(/[:.]/g, "-");
  const evidenceDir = path.join(__dirname, "..", "artifacts", "evidence", runId);
  await fs.mkdir(evidenceDir, { recursive: true });

  const capture = async (name, targetPage = page, fullPage = false) => {
    const fileName = name.replace(/\s+/g, "-").toLowerCase();
    const filePath = path.join(evidenceDir, `${fileName}.png`);
    await targetPage.screenshot({ path: filePath, fullPage });
    return filePath;
  };

  const runSection = async (name, fn) => {
    try {
      const details = (await fn()) || {};
      report[name] = { status: "PASS", ...details };
    } catch (error) {
      report[name] = { status: "FAIL", error: error.message };
    }
  };

  let appPage = page;
  let accountViewUrl = "";

  await runSection("Login", async () => {
    const configuredUrl = process.env.SALEADS_LOGIN_URL;
    if (configuredUrl) {
      await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    } else if (page.url().startsWith("about:blank")) {
      throw new Error(
        "Set SALEADS_LOGIN_URL when starting from a blank page to stay environment-agnostic."
      );
    }

    const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await clickVisibleByText(page, [
      /sign in with google/i,
      /iniciar sesi[oó]n con google/i,
      /continuar con google/i,
      /google/i
    ]);

    const oauthPage = await popupPromise;
    const authPage = oauthPage || page;
    await waitForUi(authPage);

    const accountChoice = authPage.getByText("juanlucasbarbiergarzon@gmail.com").first();
    if (await accountChoice.isVisible().catch(() => false)) {
      await accountChoice.click();
      await waitForUi(authPage);
    }

    const deadline = Date.now() + 120000;
    while (Date.now() < deadline) {
      for (const candidate of context.pages()) {
        const sidebarVisible = await candidate
          .locator("aside, nav")
          .first()
          .isVisible()
          .catch(() => false);
        const negocioVisible = await candidate
          .getByText(/negocio/i)
          .first()
          .isVisible()
          .catch(() => false);

        if (sidebarVisible || negocioVisible) {
          appPage = candidate;
          await appPage.bringToFront();
          await waitForUi(appPage);
          await expect(appPage.locator("aside, nav").first()).toBeVisible();
          await capture("01-dashboard-loaded", appPage, true);
          return { screenshot: "01-dashboard-loaded.png" };
        }
      }

      await page.waitForTimeout(1500);
    }

    throw new Error("Main application interface with left sidebar did not appear after login.");
  });

  await runSection("Mi Negocio menu", async () => {
    await clickVisibleByText(appPage, [/negocio/i]);
    await clickVisibleByText(appPage, [/mi negocio/i]);
    await assertVisibleByText(appPage, "Agregar Negocio");
    await assertVisibleByText(appPage, "Administrar Negocios");
    await capture("02-mi-negocio-expanded-menu", appPage, true);
    return { screenshot: "02-mi-negocio-expanded-menu.png" };
  });

  await runSection("Agregar Negocio modal", async () => {
    await clickVisibleByText(appPage, [/agregar negocio/i]);
    await assertVisibleByText(appPage, "Crear Nuevo Negocio");
    await expect(appPage.getByLabel(/nombre del negocio/i)).toBeVisible();
    await assertVisibleByText(appPage, "Tienes 2 de 3 negocios");
    await assertVisibleByText(appPage, "Cancelar");
    await assertVisibleByText(appPage, "Crear Negocio");
    await capture("03-agregar-negocio-modal", appPage);

    await appPage.getByLabel(/nombre del negocio/i).click();
    await appPage.getByLabel(/nombre del negocio/i).fill("Negocio Prueba Automatizacion");
    await clickVisibleByText(appPage, [/cancelar/i]);
    return { screenshot: "03-agregar-negocio-modal.png" };
  });

  await runSection("Administrar Negocios view", async () => {
    const administrarVisible = await appPage
      .getByText(/administrar negocios/i)
      .first()
      .isVisible()
      .catch(() => false);
    if (!administrarVisible) {
      await clickVisibleByText(appPage, [/mi negocio/i, /negocio/i]);
      await clickVisibleByText(appPage, [/mi negocio/i]);
    }

    await clickVisibleByText(appPage, [/administrar negocios/i]);
    await assertVisibleByText(appPage, /informaci[oó]n general/i);
    await assertVisibleByText(appPage, "Detalles de la Cuenta");
    await assertVisibleByText(appPage, "Tus Negocios");
    await assertVisibleByText(appPage, /secci[oó]n legal/i);
    await capture("04-administrar-negocios-view", appPage, true);
    accountViewUrl = appPage.url();
    return { screenshot: "04-administrar-negocios-view.png" };
  });

  await runSection("Informacion General", async () => {
    await expect(appPage.getByText(/@/).first()).toBeVisible();
    await expect(appPage.locator("h1, h2, h3, p, span").filter({ hasText: /business plan/i }).first()).toBeVisible();
    await assertVisibleByText(appPage, "Cambiar Plan");
  });

  await runSection("Detalles de la Cuenta", async () => {
    await assertVisibleByText(appPage, "Cuenta creada");
    await assertVisibleByText(appPage, "Estado activo");
    await assertVisibleByText(appPage, "Idioma seleccionado");
  });

  await runSection("Tus Negocios", async () => {
    await assertVisibleByText(appPage, "Tus Negocios");
    await assertVisibleByText(appPage, "Agregar Negocio");
    await assertVisibleByText(appPage, "Tienes 2 de 3 negocios");
  });

  const validateLegalPage = async (sectionName, linkMatcher, headingMatcher, screenshotName) => {
    await runSection(sectionName, async () => {
      const popupPromise = context.waitForEvent("page", { timeout: 6000 }).catch(() => null);
      await clickVisibleByText(appPage, [linkMatcher]);
      const popup = await popupPromise;
      const legalPage = popup || appPage;

      await waitForUi(legalPage);
      await assertVisibleByText(legalPage, headingMatcher);
      const legalContent = legalPage.locator("main p, article p, .content p, p");
      const legalParagraphCount = await legalContent.count();
      if (legalParagraphCount < 1) {
        throw new Error(`No legal content paragraphs found for ${sectionName}.`);
      }

      await capture(screenshotName, legalPage, true);
      const finalUrl = legalPage.url();

      if (popup) {
        await popup.close();
      } else if (accountViewUrl && appPage.url() !== accountViewUrl) {
        await appPage.goto(accountViewUrl, { waitUntil: "domcontentloaded" });
        await waitForUi(appPage);
      }

      await appPage.bringToFront();
      return { screenshot: `${screenshotName}.png`, finalUrl };
    });
  };

  await validateLegalPage(
    "Terminos y Condiciones",
    /t[eé]rminos y condiciones/i,
    /t[eé]rminos y condiciones/i,
    "05-terminos-y-condiciones"
  );

  await validateLegalPage(
    "Politica de Privacidad",
    /pol[ií]tica de privacidad/i,
    /pol[ií]tica de privacidad/i,
    "06-politica-de-privacidad"
  );

  for (const field of REPORT_FIELDS) {
    if (!report[field]) {
      report[field] = { status: "FAIL", error: "Section was not executed." };
    }
  }

  const reportPath = path.join(evidenceDir, "final-report.json");
  await fs.writeFile(
    reportPath,
    `${JSON.stringify(
      {
        testName: "saleads_mi_negocio_full_test",
        generatedAt: new Date().toISOString(),
        result: report
      },
      null,
      2
    )}\n`,
    "utf8"
  );

  // Step 10 explicit pass/fail output for every requested section.
  console.log("FINAL REPORT");
  for (const field of REPORT_FIELDS) {
    const entry = report[field];
    const suffix = entry.finalUrl ? ` | URL: ${entry.finalUrl}` : "";
    const failure = entry.error ? ` | ERROR: ${entry.error}` : "";
    console.log(`${field}: ${entry.status}${suffix}${failure}`);
  }
  console.log(`Evidence directory: ${evidenceDir}`);
  console.log(`Report file: ${reportPath}`);

  const failedSections = Object.entries(report).filter(([, value]) => value.status !== "PASS");
  expect(
    failedSections,
    `Workflow has failing sections: ${failedSections.map(([key]) => key).join(", ")}`
  ).toEqual([]);
});
