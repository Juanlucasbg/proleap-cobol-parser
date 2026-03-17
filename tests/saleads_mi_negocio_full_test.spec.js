const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

const reportTemplate = {
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

async function waitForUi(page) {
  await page.waitForTimeout(400);
  await page
    .waitForLoadState("domcontentloaded", { timeout: 10000 })
    .catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(300);
}

async function screenshotCheckpoint(page, testInfo, name, fullPage = false) {
  await page.screenshot({
    path: testInfo.outputPath(`${name}.png`),
    fullPage,
  });
}

async function resolveVisibleLocator(candidates, timeoutMs = 12000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    for (const locator of candidates) {
      const hasCandidate = (await locator.count().catch(() => 0)) > 0;
      if (!hasCandidate) {
        continue;
      }

      const first = locator.first();
      if (await first.isVisible().catch(() => false)) {
        return first;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  throw new Error("No visible locator was found from the provided candidates.");
}

async function clickAndWait(locator, page) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function validateLegalPage({
  appPage,
  context,
  testInfo,
  linkNamePattern,
  headingPattern,
  screenshotName,
}) {
  const legalLink = await resolveVisibleLocator([
    appPage.getByRole("link", { name: linkNamePattern }),
    appPage.getByRole("button", { name: linkNamePattern }),
    appPage.getByText(linkNamePattern),
  ]);

  const popupPromise = context
    .waitForEvent("page", { timeout: 10000 })
    .catch(() => null);

  await legalLink.click();
  await waitForUi(appPage);

  const popup = await popupPromise;
  const legalPage = popup || appPage;
  await legalPage.waitForLoadState("domcontentloaded", { timeout: 20000 });
  await legalPage.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});

  const heading = await resolveVisibleLocator(
    [
      legalPage.getByRole("heading", { name: headingPattern }),
      legalPage.getByText(headingPattern),
    ],
    20000
  );
  await expect(heading).toBeVisible();

  const bodyContent = legalPage.locator("main, article, section, body");
  await expect(bodyContent.first()).toContainText(/\S{10,}/);

  await screenshotCheckpoint(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context, baseURL }, testInfo) => {
  const report = { ...reportTemplate };
  const finalUrls = {
    terminosYCondiciones: "",
    politicaDePrivacidad: "",
  };

  try {
    // Step 1: Login with Google
    if (baseURL) {
      await page.goto(baseURL, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    if (page.url() === "about:blank") {
      throw new Error(
        "Set SALEADS_BASE_URL (or configure baseURL) so the test can start on the SaleADS login page."
      );
    }

    const googleLoginButton = await resolveVisibleLocator([
      page.getByRole("button", { name: /sign in with google|google|iniciar/i }),
      page.getByRole("link", { name: /sign in with google|google|iniciar/i }),
      page.getByText(/sign in with google|continuar con google|iniciar con google/i),
    ]);

    const googlePopupPromise = context
      .waitForEvent("page", { timeout: 12000 })
      .catch(() => null);

    await clickAndWait(googleLoginButton, page);

    const googlePopup = await googlePopupPromise;
    if (googlePopup) {
      await googlePopup.waitForLoadState("domcontentloaded", { timeout: 20000 });
      const accountOption = await resolveVisibleLocator(
        [
          googlePopup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }),
          googlePopup.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
        ],
        12000
      ).catch(() => null);

      if (accountOption) {
        await accountOption.click();
      }

      await googlePopup.waitForEvent("close", { timeout: 45000 }).catch(() => {});
    } else if (/accounts\.google\./i.test(page.url())) {
      const accountOption = await resolveVisibleLocator(
        [
          page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }),
          page.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
        ],
        12000
      ).catch(() => null);

      if (accountOption) {
        await accountOption.click();
      }
    }

    await waitForUi(page);
    const sidebar = await resolveVisibleLocator(
      [
        page.locator("aside"),
        page.getByRole("navigation"),
        page.locator("nav"),
      ],
      45000
    );
    await expect(sidebar).toBeVisible();
    await expect(page.getByText(/negocio/i).first()).toBeVisible({ timeout: 45000 });
    await screenshotCheckpoint(page, testInfo, "01-dashboard-loaded");
    report.Login = "PASS";

    // Step 2: Open Mi Negocio menu
    await expect(page.getByText(/negocio/i).first()).toBeVisible();
    const miNegocioEntry = await resolveVisibleLocator([
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i),
    ]);
    await clickAndWait(miNegocioEntry, page);

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();
    await screenshotCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded");
    report["Mi Negocio menu"] = "PASS";

    // Step 3: Validate Agregar Negocio modal
    const agregarNegocioMenu = await resolveVisibleLocator([
      page.getByRole("link", { name: /^agregar negocio$/i }),
      page.getByRole("button", { name: /^agregar negocio$/i }),
      page.getByText(/^agregar negocio$/i),
    ]);
    await clickAndWait(agregarNegocioMenu, page);

    await expect(page.getByText(/crear nuevo negocio/i).first()).toBeVisible();
    const nombreDelNegocioInput = await resolveVisibleLocator([
      page.getByLabel(/nombre del negocio/i),
      page.getByPlaceholder(/nombre del negocio/i),
      page.locator("input[name*='nombre' i], input[id*='nombre' i]"),
    ]);
    await expect(nombreDelNegocioInput).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i }).first()).toBeVisible();

    await nombreDelNegocioInput.click();
    await nombreDelNegocioInput.fill("Negocio Prueba Automatización");
    await screenshotCheckpoint(page, testInfo, "03-agregar-negocio-modal");
    await clickAndWait(page.getByRole("button", { name: /cancelar/i }).first(), page);
    report["Agregar Negocio modal"] = "PASS";

    // Step 4: Open Administrar Negocios
    const miNegocioEntryAgain = await resolveVisibleLocator([
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i),
    ]);
    await clickAndWait(miNegocioEntryAgain, page);

    const administrarNegocios = await resolveVisibleLocator([
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i),
    ]);
    await clickAndWait(administrarNegocios, page);

    await expect(page.getByText(/información general/i).first()).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByText(/sección legal/i).first()).toBeVisible();
    await screenshotCheckpoint(page, testInfo, "04-administrar-negocios-page", true);
    report["Administrar Negocios view"] = "PASS";

    // Step 5: Validate Información General
    await expect(page.getByText(/business plan/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible();
    await expect(
      page.locator("section, div").filter({ hasText: /información general/i }).first()
    ).toContainText(/@/);
    report["Información General"] = "PASS";

    // Step 6: Validate Detalles de la Cuenta
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible();
    report["Detalles de la Cuenta"] = "PASS";

    // Step 7: Validate Tus Negocios
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
    report["Tus Negocios"] = "PASS";

    // Step 8: Validate Términos y Condiciones
    finalUrls.terminosYCondiciones = await validateLegalPage({
      appPage: page,
      context,
      testInfo,
      linkNamePattern: /términos y condiciones|terminos y condiciones/i,
      headingPattern: /términos y condiciones|terminos y condiciones/i,
      screenshotName: "08-terminos-y-condiciones",
    });
    report["Términos y Condiciones"] = "PASS";

    // Step 9: Validate Política de Privacidad
    finalUrls.politicaDePrivacidad = await validateLegalPage({
      appPage: page,
      context,
      testInfo,
      linkNamePattern: /política de privacidad|politica de privacidad/i,
      headingPattern: /política de privacidad|politica de privacidad/i,
      screenshotName: "09-politica-de-privacidad",
    });
    report["Política de Privacidad"] = "PASS";
  } finally {
    const finalReport = {
      test: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      statusByStep: report,
      evidence: {
        screenshotsPath: testInfo.outputDir,
        finalUrls,
      },
    };

    const reportPath = testInfo.outputPath("final-report.json");
    await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
    await testInfo.attach("final-report", {
      path: reportPath,
      contentType: "application/json",
    });

    // Ensure final report explicitly returns PASS/FAIL for all requested fields.
    for (const [stepName, stepStatus] of Object.entries(report)) {
      expect(
        ["PASS", "FAIL"].some((prefix) => stepStatus.startsWith(prefix)),
        `Step "${stepName}" has unexpected status value "${stepStatus}"`
      ).toBeTruthy();
    }
  }
});
