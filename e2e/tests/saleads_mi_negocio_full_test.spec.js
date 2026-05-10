const fs = require("node:fs/promises");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

const TEST_NAME = "saleads_mi_negocio_full_test";
const DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
const STEP_FIELDS = [
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

function createReport(loginUrl, accountEmail, runStamp) {
  const results = {};
  for (const field of STEP_FIELDS) {
    results[field] = {
      status: "FAIL",
      details: "Not executed",
      evidence: {
        screenshots: []
      }
    };
  }

  return {
    name: TEST_NAME,
    generatedAt: new Date().toISOString(),
    runStamp,
    environment: {
      loginUrl: loginUrl || null,
      googleAccount: accountEmail
    },
    results
  };
}

function updateResult(report, stepName, status, details, evidence = {}) {
  report.results[stepName] = {
    status,
    details,
    evidence: {
      screenshots: evidence.screenshots || [],
      finalUrl: evidence.finalUrl || null,
      openedInNewTab: evidence.openedInNewTab || false
    }
  };
}

function markPrerequisiteFailure(report, stepName, prerequisiteStep) {
  updateResult(
    report,
    stepName,
    "FAIL",
    `Prerequisite failed: ${prerequisiteStep}`,
    { screenshots: [] }
  );
}

function summarize(report) {
  const entries = Object.entries(report.results);
  const passed = entries.filter(([, value]) => value.status === "PASS").length;
  const failed = entries.length - passed;
  report.summary = {
    total: entries.length,
    passed,
    failed
  };
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(350);
}

async function waitForVisibleCandidate(candidates, description, timeoutMs = 15000) {
  const startedAt = Date.now();

  while (Date.now() - startedAt <= timeoutMs) {
    for (const candidate of candidates) {
      const locator = candidate.first();
      try {
        if (await locator.isVisible()) {
          return locator;
        }
      } catch (error) {
        // Ignore transient selector evaluation errors while polling.
      }
    }

    await new Promise((resolve) => setTimeout(resolve, 200));
  }

  throw new Error(`Could not find a visible element for "${description}" in ${timeoutMs}ms.`);
}

async function clickAndWait(locator, page) {
  await expect(locator).toBeVisible({ timeout: 15000 });
  await locator.click();
  await waitForUi(page);
}

async function captureScreenshot(page, screenshotsDir, filename, options = {}) {
  const filePath = path.join(screenshotsDir, filename);
  await page.screenshot({
    path: filePath,
    fullPage: options.fullPage || false
  });
  return filePath;
}

async function ensureMenuExpanded(page) {
  const administrarVisible = await page
    .getByText("Administrar Negocios", { exact: true })
    .first()
    .isVisible()
    .catch(() => false);

  if (administrarVisible) {
    return;
  }

  const miNegocioToggle = await waitForVisibleCandidate(
    [
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText("Mi Negocio", { exact: true })
    ],
    "Mi Negocio toggle"
  );

  await clickAndWait(miNegocioToggle, page);
}

async function validateLegalLink(appPage, options) {
  const { linkText, headingText, screenshotName, screenshotsDir } = options;
  const link = await waitForVisibleCandidate(
    [
      appPage.getByRole("link", { name: new RegExp(escapeRegExp(linkText), "i") }),
      appPage.getByRole("button", { name: new RegExp(escapeRegExp(linkText), "i") }),
      appPage.getByText(linkText, { exact: true })
    ],
    linkText
  );

  const previousUrl = appPage.url();
  const popupPromise = appPage.waitForEvent("popup", { timeout: 8000 }).catch(() => null);

  await link.click();
  await waitForUi(appPage);

  const popup = await popupPromise;
  const openedInNewTab = Boolean(popup);
  const legalPage = popup || appPage;

  if (!openedInNewTab) {
    await appPage
      .waitForURL((url) => url.toString() !== previousUrl, { timeout: 15000 })
      .catch(() => {});
  }

  await waitForUi(legalPage);

  await expect(
    legalPage.getByRole("heading", {
      name: new RegExp(escapeRegExp(headingText), "i")
    })
  ).toBeVisible({ timeout: 20000 });

  const bodyText = await legalPage.locator("body").innerText();
  if (bodyText.trim().length < 80) {
    throw new Error(`Legal content for "${headingText}" appears too short.`);
  }

  const screenshot = await captureScreenshot(legalPage, screenshotsDir, screenshotName, {
    fullPage: true
  });
  const finalUrl = legalPage.url();

  if (openedInNewTab) {
    await legalPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(appPage);
  }

  return {
    screenshots: [screenshot],
    finalUrl,
    openedInNewTab
  };
}

test(TEST_NAME, async ({ page }, testInfo) => {
  const loginUrl = process.env.SALEADS_LOGIN_URL || testInfo.project.use.baseURL || "";
  const googleAccount = process.env.SALEADS_GOOGLE_ACCOUNT || DEFAULT_GOOGLE_ACCOUNT;
  const runStamp = new Date().toISOString().replace(/[:.]/g, "-");
  const artifactsDir = path.resolve(__dirname, "..", "artifacts", TEST_NAME, runStamp);
  const screenshotsDir = path.join(artifactsDir, "screenshots");
  const reportPath = path.join(artifactsDir, `${TEST_NAME}.report.json`);
  const report = createReport(loginUrl, googleAccount, runStamp);

  await fs.mkdir(screenshotsDir, { recursive: true });

  const runStep = async (stepName, handler) => {
    try {
      const evidence = await handler();
      updateResult(report, stepName, "PASS", "Validation completed successfully.", evidence);
      return true;
    } catch (error) {
      updateResult(report, stepName, "FAIL", error.message, { screenshots: [] });
      return false;
    }
  };

  const loginOk = await runStep("Login", async () => {
    if (!loginUrl) {
      throw new Error("SALEADS_LOGIN_URL (or Playwright baseURL) is required to open the login page.");
    }

    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    const signInButton = await waitForVisibleCandidate(
      [
        page.getByRole("button", { name: /google/i }),
        page.getByRole("link", { name: /google/i }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i)
      ],
      "Google login button"
    );

    const popupPromise = page.waitForEvent("popup", { timeout: 8000 }).catch(() => null);
    await signInButton.click();
    await waitForUi(page);

    const popup = await popupPromise;
    const authPage = popup || page;
    await waitForUi(authPage);

    if (/accounts\.google\.com/i.test(authPage.url())) {
      const accountOption = await waitForVisibleCandidate(
        [
          authPage.getByRole("button", {
            name: new RegExp(escapeRegExp(googleAccount), "i")
          }),
          authPage.getByRole("link", {
            name: new RegExp(escapeRegExp(googleAccount), "i")
          }),
          authPage.getByText(googleAccount)
        ],
        `Google account option ${googleAccount}`,
        10000
      ).catch(() => null);

      if (accountOption) {
        await accountOption.click();
        await waitForUi(authPage);
      }
    }

    if (popup) {
      await popup.close().catch(() => {});
      await page.bringToFront();
    }

    await waitForUi(page);

    const sidebar = await waitForVisibleCandidate(
      [
        page.locator("aside"),
        page.getByRole("navigation"),
        page.locator("[class*='sidebar']")
      ],
      "main sidebar",
      30000
    );
    await expect(sidebar).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/negocio/i)).toBeVisible({ timeout: 30000 });

    const screenshot = await captureScreenshot(page, screenshotsDir, "01-dashboard-loaded.png", {
      fullPage: true
    });

    return {
      screenshots: [screenshot],
      finalUrl: page.url()
    };
  });

  const menuOk = loginOk
    ? await runStep("Mi Negocio menu", async () => {
        const negocioMenu = await waitForVisibleCandidate(
          [
            page.getByRole("button", { name: /^negocio$/i }),
            page.getByRole("link", { name: /^negocio$/i }),
            page.getByText("Negocio", { exact: true })
          ],
          "Negocio menu"
        );
        await clickAndWait(negocioMenu, page);

        const miNegocio = await waitForVisibleCandidate(
          [
            page.getByRole("button", { name: /mi negocio/i }),
            page.getByRole("link", { name: /mi negocio/i }),
            page.getByText("Mi Negocio", { exact: true })
          ],
          "Mi Negocio option"
        );
        await clickAndWait(miNegocio, page);

        await expect(page.getByText("Agregar Negocio", { exact: true })).toBeVisible({
          timeout: 15000
        });
        await expect(page.getByText("Administrar Negocios", { exact: true })).toBeVisible({
          timeout: 15000
        });

        const screenshot = await captureScreenshot(
          page,
          screenshotsDir,
          "02-mi-negocio-menu-expanded.png",
          { fullPage: true }
        );

        return {
          screenshots: [screenshot],
          finalUrl: page.url()
        };
      })
    : (markPrerequisiteFailure(report, "Mi Negocio menu", "Login"), false);

  const agregarNegocioOk = menuOk
    ? await runStep("Agregar Negocio modal", async () => {
        const agregarNegocio = await waitForVisibleCandidate(
          [
            page.getByRole("button", { name: /^agregar negocio$/i }),
            page.getByRole("link", { name: /^agregar negocio$/i }),
            page.getByText("Agregar Negocio", { exact: true })
          ],
          "Agregar Negocio action"
        );
        await clickAndWait(agregarNegocio, page);

        const modalTitle = page.getByRole("heading", { name: "Crear Nuevo Negocio" });
        await expect(modalTitle).toBeVisible({ timeout: 15000 });
        await expect(page.getByLabel("Nombre del Negocio")).toBeVisible({ timeout: 15000 });
        await expect(page.getByText("Tienes 2 de 3 negocios", { exact: false })).toBeVisible({
          timeout: 15000
        });
        await expect(page.getByRole("button", { name: /^cancelar$/i })).toBeVisible({
          timeout: 15000
        });
        await expect(page.getByRole("button", { name: /^crear negocio$/i })).toBeVisible({
          timeout: 15000
        });

        const screenshot = await captureScreenshot(page, screenshotsDir, "03-agregar-negocio-modal.png");

        const nombreField = page.getByLabel("Nombre del Negocio");
        await clickAndWait(nombreField, page);
        await nombreField.fill("Negocio Prueba Automatización");
        await clickAndWait(page.getByRole("button", { name: /^cancelar$/i }), page);

        return {
          screenshots: [screenshot],
          finalUrl: page.url()
        };
      })
    : (markPrerequisiteFailure(report, "Agregar Negocio modal", "Mi Negocio menu"), false);

  const administrarNegociosOk = menuOk
    ? await runStep("Administrar Negocios view", async () => {
        await ensureMenuExpanded(page);

        const administrarNegocios = await waitForVisibleCandidate(
          [
            page.getByRole("link", { name: /^administrar negocios$/i }),
            page.getByRole("button", { name: /^administrar negocios$/i }),
            page.getByText("Administrar Negocios", { exact: true })
          ],
          "Administrar Negocios action"
        );
        await clickAndWait(administrarNegocios, page);

        await expect(page.getByText("Información General", { exact: true })).toBeVisible({
          timeout: 20000
        });
        await expect(page.getByText("Detalles de la Cuenta", { exact: true })).toBeVisible({
          timeout: 20000
        });
        await expect(page.getByText("Tus Negocios", { exact: true })).toBeVisible({
          timeout: 20000
        });
        await expect(page.getByText("Sección Legal", { exact: true })).toBeVisible({
          timeout: 20000
        });

        const screenshot = await captureScreenshot(page, screenshotsDir, "04-administrar-negocios.png", {
          fullPage: true
        });

        return {
          screenshots: [screenshot],
          finalUrl: page.url()
        };
      })
    : (markPrerequisiteFailure(report, "Administrar Negocios view", "Mi Negocio menu"), false);

  if (!administrarNegociosOk) {
    markPrerequisiteFailure(report, "Información General", "Administrar Negocios view");
    markPrerequisiteFailure(report, "Detalles de la Cuenta", "Administrar Negocios view");
    markPrerequisiteFailure(report, "Tus Negocios", "Administrar Negocios view");
    markPrerequisiteFailure(report, "Términos y Condiciones", "Administrar Negocios view");
    markPrerequisiteFailure(report, "Política de Privacidad", "Administrar Negocios view");
  } else {
    await runStep("Información General", async () => {
      const email = page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first();
      await expect(email).toBeVisible({ timeout: 15000 });

      await expect(page.getByText("BUSINESS PLAN", { exact: false })).toBeVisible({ timeout: 15000 });
      await expect(page.getByRole("button", { name: /^cambiar plan$/i })).toBeVisible({
        timeout: 15000
      });

      const hasPotentialName = await page
        .locator("body")
        .evaluate((node) => {
          const text = node.innerText || "";
          const lines = text
            .split("\n")
            .map((line) => line.trim())
            .filter(Boolean);
          return lines.some(
            (line) =>
              /^[A-Za-zÁÉÍÓÚÑáéíóúñ]+(?:\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]+)+$/.test(line) &&
              !line.toLowerCase().includes("business plan") &&
              !line.toLowerCase().includes("información general") &&
              !line.includes("@")
          );
        });

      if (!hasPotentialName) {
        throw new Error("Could not confirm a visible user name in Información General.");
      }

      return {
        screenshots: [],
        finalUrl: page.url()
      };
    });

    await runStep("Detalles de la Cuenta", async () => {
      await expect(page.getByText("Cuenta creada", { exact: false })).toBeVisible({ timeout: 15000 });
      await expect(page.getByText("Estado activo", { exact: false })).toBeVisible({ timeout: 15000 });
      await expect(page.getByText("Idioma seleccionado", { exact: false })).toBeVisible({
        timeout: 15000
      });

      return {
        screenshots: [],
        finalUrl: page.url()
      };
    });

    await runStep("Tus Negocios", async () => {
      await expect(page.getByText("Tus Negocios", { exact: true })).toBeVisible({ timeout: 15000 });
      await expect(page.getByRole("button", { name: /^agregar negocio$/i })).toBeVisible({
        timeout: 15000
      });
      await expect(page.getByText("Tienes 2 de 3 negocios", { exact: false })).toBeVisible({
        timeout: 15000
      });

      return {
        screenshots: [],
        finalUrl: page.url()
      };
    });

    await runStep("Términos y Condiciones", async () =>
      validateLegalLink(page, {
        linkText: "Términos y Condiciones",
        headingText: "Términos y Condiciones",
        screenshotName: "05-terminos-y-condiciones.png",
        screenshotsDir
      })
    );

    await runStep("Política de Privacidad", async () =>
      validateLegalLink(page, {
        linkText: "Política de Privacidad",
        headingText: "Política de Privacidad",
        screenshotName: "06-politica-de-privacidad.png",
        screenshotsDir
      })
    );
  }

  summarize(report);
  await fs.writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  await testInfo.attach("saleads-mi-negocio-report", {
    path: reportPath,
    contentType: "application/json"
  });

  expect(report.summary.failed, JSON.stringify(report.results, null, 2)).toBe(0);
});
