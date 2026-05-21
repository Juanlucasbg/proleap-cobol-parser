const fs = require("node:fs/promises");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => {});
}

async function findFirstVisible(page, locators, timeoutMs = 15_000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    for (const locator of locators) {
      if (await locator.first().isVisible().catch(() => false)) {
        return locator.first();
      }
    }
    await page.waitForTimeout(250);
  }
  throw new Error("Unable to find any visible locator within timeout.");
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function captureScreenshot(page, testInfo, screenshotsDir, fileName, fullPage = false) {
  const screenshotPath = path.join(screenshotsDir, `${fileName}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(fileName, {
    path: screenshotPath,
    contentType: "image/png",
  });
  return screenshotPath;
}

test("saleads_mi_negocio_full_test", async ({ page, context, baseURL }, testInfo) => {
  const report = {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Información General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Términos y Condiciones": "FAIL",
    "Política de Privacidad": "FAIL",
  };

  const legalUrls = {
    "Términos y Condiciones": "",
    "Política de Privacidad": "",
  };

  const screenshots = {};
  const failures = [];
  const screenshotsDir = path.join(testInfo.outputDir, "screenshots");
  await fs.mkdir(screenshotsDir, { recursive: true });

  const executeStep = async (stepName, action) => {
    try {
      await action();
      report[stepName] = "PASS";
    } catch (error) {
      failures.push({ stepName, message: error.message });
      report[stepName] = "FAIL";
    }
  };

  const openLegalAndValidate = async (linkText, headingText, stepName, screenshotName) => {
    const legalLink = await findFirstVisible(page, [
      page.getByRole("link", { name: new RegExp(linkText, "i") }),
      page.getByRole("button", { name: new RegExp(linkText, "i") }),
      page.getByText(new RegExp(linkText, "i")),
    ]);

    const pendingPopup = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
    await legalLink.click();
    await waitForUi(page);
    const popup = await pendingPopup;

    const targetPage = popup || page;
    await targetPage.waitForLoadState("domcontentloaded");
    await targetPage.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => {});

    await expect(
      await findFirstVisible(targetPage, [
        targetPage.getByRole("heading", { name: new RegExp(headingText, "i") }),
        targetPage.getByText(new RegExp(headingText, "i")),
      ]),
    ).toBeVisible();

    await expect(
      await findFirstVisible(targetPage, [
        targetPage.locator("main p, article p, section p, p").filter({ hasText: /\S{10,}/ }),
        targetPage.locator("main li, article li, section li, li").filter({ hasText: /\S{10,}/ }),
      ]),
    ).toBeVisible();

    screenshots[screenshotName] = await captureScreenshot(
      targetPage,
      testInfo,
      screenshotsDir,
      screenshotName,
      true,
    );

    legalUrls[stepName] = targetPage.url();

    if (popup) {
      await popup.close().catch(() => {});
      await page.bringToFront();
      await waitForUi(page);
      return;
    }

    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
  };

  try {
    if (baseURL) {
      await page.goto(baseURL, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    await executeStep("Login", async () => {
      const googleLoginControl = await findFirstVisible(page, [
        page.getByRole("button", { name: /google/i }),
        page.getByRole("link", { name: /google/i }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google/i),
      ]);

      const pendingPopup = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
      await googleLoginControl.click();
      const popup = await pendingPopup;

      const authPage = popup || page;
      await authPage.waitForLoadState("domcontentloaded");
      await authPage.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => {});

      const accountChoice = authPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
      if (await accountChoice.isVisible({ timeout: 8_000 }).catch(() => false)) {
        await accountChoice.click();
      }

      if (popup) {
        await popup.waitForEvent("close", { timeout: 20_000 }).catch(() => {});
        await page.bringToFront();
      }

      await waitForUi(page);

      await expect(
        await findFirstVisible(page, [
          page.getByRole("navigation"),
          page.locator("aside"),
          page.locator('[class*="sidebar"]'),
        ]),
      ).toBeVisible();

      await expect(
        await findFirstVisible(page, [page.getByText(/negocio/i), page.getByText(/dashboard/i)]),
      ).toBeVisible();

      screenshots.dashboardLoaded = await captureScreenshot(
        page,
        testInfo,
        screenshotsDir,
        "dashboard_loaded",
      );
    });

    await executeStep("Mi Negocio menu", async () => {
      const negocioEntry = await findFirstVisible(page, [
        page.getByRole("button", { name: /^negocio$/i }),
        page.getByRole("link", { name: /^negocio$/i }),
        page.getByText(/^negocio$/i),
      ]);
      await clickAndWait(page, negocioEntry);

      const miNegocioEntry = await findFirstVisible(page, [
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i),
      ]);
      await clickAndWait(page, miNegocioEntry);

      await expect(page.getByText(/agregar negocio/i)).toBeVisible();
      await expect(page.getByText(/administrar negocios/i)).toBeVisible();

      screenshots.miNegocioMenu = await captureScreenshot(
        page,
        testInfo,
        screenshotsDir,
        "mi_negocio_menu_expanded",
      );
    });

    await executeStep("Agregar Negocio modal", async () => {
      await clickAndWait(
        page,
        await findFirstVisible(page, [
          page.getByRole("button", { name: /agregar negocio/i }),
          page.getByRole("link", { name: /agregar negocio/i }),
          page.getByText(/agregar negocio/i),
        ]),
      );

      await expect(page.getByText(/crear nuevo negocio/i)).toBeVisible();
      await expect(page.getByLabel(/nombre del negocio/i)).toBeVisible();
      await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
      await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();

      screenshots.agregarNegocioModal = await captureScreenshot(
        page,
        testInfo,
        screenshotsDir,
        "agregar_negocio_modal",
      );

      const businessNameInput = page.getByLabel(/nombre del negocio/i).first();
      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatización");
      await clickAndWait(page, page.getByRole("button", { name: /cancelar/i }).first());
    });

    await executeStep("Administrar Negocios view", async () => {
      if (!(await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false))) {
        const negocioEntry = await findFirstVisible(page, [
          page.getByRole("button", { name: /^negocio$/i }),
          page.getByRole("link", { name: /^negocio$/i }),
          page.getByText(/^negocio$/i),
        ]);
        await clickAndWait(page, negocioEntry);
      }

      await clickAndWait(
        page,
        await findFirstVisible(page, [
          page.getByRole("button", { name: /administrar negocios/i }),
          page.getByRole("link", { name: /administrar negocios/i }),
          page.getByText(/administrar negocios/i),
        ]),
      );

      await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible();
      await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
      await expect(page.getByText(/tus negocios/i)).toBeVisible();
      await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible();

      screenshots.administrarNegocios = await captureScreenshot(
        page,
        testInfo,
        screenshotsDir,
        "administrar_negocios_view",
        true,
      );
    });

    await executeStep("Información General", async () => {
      await expect(
        await findFirstVisible(page, [page.locator("text=/[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}/")]),
      ).toBeVisible();
      await expect(page.getByText(/business plan/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();

      await expect(
        await findFirstVisible(page, [
          page
            .locator("h1, h2, h3, p, span")
            .filter({ hasNotText: /informaci[oó]n general|business plan|cambiar plan|@/i }),
        ]),
      ).toBeVisible();
    });

    await executeStep("Detalles de la Cuenta", async () => {
      await expect(page.getByText(/cuenta creada/i)).toBeVisible();
      await expect(page.getByText(/estado activo/i)).toBeVisible();
      await expect(page.getByText(/idioma seleccionado/i)).toBeVisible();
    });

    await executeStep("Tus Negocios", async () => {
      await expect(page.getByText(/tus negocios/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible();
      await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(
        await findFirstVisible(page, [
          page.locator("li"),
          page.locator('[role="row"]'),
          page.locator('[class*="business"]'),
          page.locator('[class*="negocio"]'),
        ]),
      ).toBeVisible();
    });

    await executeStep("Términos y Condiciones", async () => {
      await openLegalAndValidate(
        "Términos y Condiciones",
        "Términos y Condiciones",
        "Términos y Condiciones",
        "terminos_y_condiciones",
      );
    });

    await executeStep("Política de Privacidad", async () => {
      await openLegalAndValidate(
        "Política de Privacidad",
        "Política de Privacidad",
        "Política de Privacidad",
        "politica_de_privacidad",
      );
    });
  } finally {
    const finalPayload = {
      testName: "saleads_mi_negocio_full_test",
      statusByStep: report,
      legalUrls,
      screenshots,
      failures,
    };

    const reportPath = path.join(testInfo.outputDir, "saleads-mi-negocio-final-report.json");
    await fs.writeFile(reportPath, `${JSON.stringify(finalPayload, null, 2)}\n`, "utf8");
    await testInfo.attach("saleads-mi-negocio-final-report", {
      path: reportPath,
      contentType: "application/json",
    });

    // Final condensed report in stdout for CI logs.
    console.log("saleads_mi_negocio_full_test report:", JSON.stringify(finalPayload));
  }

  expect(Object.values(report), `Failing steps: ${JSON.stringify(failures, null, 2)}`).toEqual(
    Array(Object.keys(report).length).fill("PASS"),
  );
});
