const { test, expect } = require("@playwright/test");

const STEP_KEYS = [
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

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const TEST_BUSINESS_NAME = "Negocio Prueba Automatización";

function makeReport() {
  return Object.fromEntries(STEP_KEYS.map((key) => [key, "FAIL"]));
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 6000 }).catch(() => {});
  await page.waitForTimeout(600);
}

async function firstVisible(candidates, timeout = 20000) {
  const deadline = Date.now() + timeout;

  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      if ((await candidate.count()) > 0 && (await candidate.first().isVisible())) {
        return candidate.first();
      }
    }
    await candidates[0].page().waitForTimeout(250);
  }

  throw new Error("No candidate locator became visible in time.");
}

async function clickAndWait(locator, page) {
  await expect(locator).toBeVisible({ timeout: 20000 });
  await locator.click();
  await waitForUi(page);
}

async function captureCheckpoint(page, testInfo, fileName, fullPage = false) {
  await page.screenshot({
    path: testInfo.outputPath(fileName),
    fullPage
  });
}

async function runStep(report, stepName, body) {
  try {
    await body();
    report[stepName] = "PASS";
  } catch (error) {
    // Continue running the rest of the workflow to produce a complete final report.
    report[stepName] = `FAIL: ${error.message}`;
  }
}

async function ensureMiNegocioExpanded(page) {
  const administrarNegocios = page.getByText("Administrar Negocios", { exact: true });
  if ((await administrarNegocios.count()) > 0 && (await administrarNegocios.first().isVisible())) {
    return;
  }

  const miNegocioButton = await firstVisible(
    [
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByText("Mi Negocio", { exact: true })
    ],
    10000
  );

  await clickAndWait(miNegocioButton, page);
}

async function openAndValidateLegalDocument({
  page,
  testInfo,
  linkText,
  expectedHeading,
  screenshotName
}) {
  const originalUrl = page.url();
  const context = page.context();
  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  const sameTabNavigationPromise = page.waitForNavigation({ waitUntil: "domcontentloaded", timeout: 8000 }).catch(
    () => null
  );

  const legalLink = await firstVisible(
    [page.getByRole("link", { name: linkText }), page.getByText(linkText, { exact: true })],
    10000
  );

  await legalLink.click();

  const popup = await popupPromise;
  let legalPage = page;
  let openedInNewTab = false;

  if (popup) {
    legalPage = popup;
    openedInNewTab = true;
    await waitForUi(legalPage);
  } else {
    await sameTabNavigationPromise;
    await waitForUi(page);
  }

  const headingByRole = legalPage.getByRole("heading", { name: expectedHeading });
  if ((await headingByRole.count()) > 0) {
    await expect(headingByRole.first()).toBeVisible({ timeout: 20000 });
  } else {
    await expect(legalPage.getByText(expectedHeading).first()).toBeVisible({ timeout: 20000 });
  }

  const legalText = (await legalPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
  expect(legalText.length).toBeGreaterThan(120);
  await captureCheckpoint(legalPage, testInfo, screenshotName, true);

  const finalUrl = legalPage.url();

  if (openedInNewTab) {
    await legalPage.close();
    await page.bringToFront();
    await waitForUi(page);
  } else if (page.url() !== originalUrl) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = makeReport();
  const expectedName = process.env.SALEADS_EXPECTED_USER_NAME;
  const expectedEmail = process.env.SALEADS_EXPECTED_USER_EMAIL;

  await test.step("Step 1: Login with Google", async () => {
    await runStep(report, "Login", async () => {
      const skipNavigation = process.env.SALEADS_SKIP_NAVIGATION === "true";
      if (!skipNavigation) {
        if (!process.env.SALEADS_URL) {
          throw new Error("Set SALEADS_URL, or set SALEADS_SKIP_NAVIGATION=true with a preloaded login page.");
        }
        await page.goto(process.env.SALEADS_URL, { waitUntil: "domcontentloaded" });
      }

      await waitForUi(page);

      const googleButton = await firstVisible(
        [
          page.getByRole("button", { name: /sign in with google/i }),
          page.getByRole("button", { name: /iniciar sesión con google/i }),
          page.getByRole("button", { name: /continuar con google/i }),
          page.getByRole("link", { name: /sign in with google/i }),
          page.getByRole("link", { name: /iniciar sesión con google/i }),
          page.getByText(/google/i)
        ],
        25000
      );

      const popupPromise = page.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await googleButton.click();
      await waitForUi(page);

      const googlePopup = await popupPromise;
      if (googlePopup) {
        await waitForUi(googlePopup);
        const accountChoice = googlePopup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });
        if ((await accountChoice.count()) > 0 && (await accountChoice.first().isVisible())) {
          await accountChoice.first().click();
          await waitForUi(googlePopup);
        }
      } else {
        const accountChoice = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });
        if ((await accountChoice.count()) > 0 && (await accountChoice.first().isVisible())) {
          await accountChoice.first().click();
          await waitForUi(page);
        }
      }

      await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 60000 });
      await expect(
        firstVisible(
          [
            page.getByText("Mi Negocio", { exact: true }),
            page.getByText("Negocio", { exact: true }),
            page.getByRole("link", { name: /Mi Negocio/i })
          ],
          30000
        )
      ).resolves.toBeTruthy();

      await captureCheckpoint(page, testInfo, "step-1-dashboard-loaded.png", true);
    });
  });

  await test.step("Step 2: Open Mi Negocio menu", async () => {
    await runStep(report, "Mi Negocio menu", async () => {
      const negocioOption = await firstVisible(
        [
          page.getByRole("button", { name: /^Negocio$/i }),
          page.getByRole("link", { name: /^Negocio$/i }),
          page.getByText("Negocio", { exact: true })
        ],
        20000
      );
      await clickAndWait(negocioOption, page);

      const miNegocioOption = await firstVisible(
        [
          page.getByRole("button", { name: /^Mi Negocio$/i }),
          page.getByRole("link", { name: /^Mi Negocio$/i }),
          page.getByText("Mi Negocio", { exact: true })
        ],
        15000
      );
      await clickAndWait(miNegocioOption, page);

      await expect(page.getByText("Agregar Negocio", { exact: true })).toBeVisible({ timeout: 15000 });
      await expect(page.getByText("Administrar Negocios", { exact: true })).toBeVisible({ timeout: 15000 });

      await captureCheckpoint(page, testInfo, "step-2-mi-negocio-expanded.png", true);
    });
  });

  await test.step("Step 3: Validate Agregar Negocio modal", async () => {
    await runStep(report, "Agregar Negocio modal", async () => {
      await ensureMiNegocioExpanded(page);
      await clickAndWait(page.getByText("Agregar Negocio", { exact: true }).first(), page);

      const modal = page.getByRole("dialog");
      await expect(modal).toBeVisible({ timeout: 20000 });
      await expect(modal.getByText("Crear Nuevo Negocio", { exact: true })).toBeVisible();
      await expect(modal.getByLabel("Nombre del Negocio")).toBeVisible();
      await expect(modal.getByText("Tienes 2 de 3 negocios", { exact: true })).toBeVisible();
      await expect(modal.getByRole("button", { name: "Cancelar" })).toBeVisible();
      await expect(modal.getByRole("button", { name: "Crear Negocio" })).toBeVisible();

      await captureCheckpoint(page, testInfo, "step-3-agregar-negocio-modal.png");

      const negocioInput = modal.getByLabel("Nombre del Negocio");
      await negocioInput.click();
      await negocioInput.fill(TEST_BUSINESS_NAME);
      await clickAndWait(modal.getByRole("button", { name: "Cancelar" }), page);
      await expect(modal).not.toBeVisible({ timeout: 10000 });
    });
  });

  await test.step("Step 4: Open Administrar Negocios", async () => {
    await runStep(report, "Administrar Negocios view", async () => {
      await ensureMiNegocioExpanded(page);
      await clickAndWait(page.getByText("Administrar Negocios", { exact: true }).first(), page);

      await expect(page.getByRole("heading", { name: /Información General/i })).toBeVisible({ timeout: 25000 });
      await expect(page.getByRole("heading", { name: /Detalles de la Cuenta/i })).toBeVisible({ timeout: 25000 });
      await expect(page.getByRole("heading", { name: /Tus Negocios/i })).toBeVisible({ timeout: 25000 });
      await expect(page.getByText("Sección Legal", { exact: true })).toBeVisible({ timeout: 25000 });

      await captureCheckpoint(page, testInfo, "step-4-administrar-negocios-page.png", true);
    });
  });

  await test.step("Step 5: Validate Información General", async () => {
    await runStep(report, "Información General", async () => {
      if (expectedName) {
        await expect(page.getByText(expectedName, { exact: true })).toBeVisible({ timeout: 10000 });
      } else {
        await expect(page.getByRole("heading", { name: /Información General/i })).toBeVisible();
      }

      if (expectedEmail) {
        await expect(page.getByText(expectedEmail, { exact: true })).toBeVisible({ timeout: 10000 });
      } else {
        await expect(page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()).toBeVisible({ timeout: 10000 });
      }

      await expect(page.getByText("BUSINESS PLAN", { exact: true })).toBeVisible({ timeout: 10000 });
      await expect(page.getByRole("button", { name: "Cambiar Plan" })).toBeVisible({ timeout: 10000 });
    });
  });

  await test.step("Step 6: Validate Detalles de la Cuenta", async () => {
    await runStep(report, "Detalles de la Cuenta", async () => {
      await expect(page.getByText("Cuenta creada", { exact: true })).toBeVisible({ timeout: 10000 });
      await expect(page.getByText("Estado activo", { exact: true })).toBeVisible({ timeout: 10000 });
      await expect(page.getByText("Idioma seleccionado", { exact: true })).toBeVisible({ timeout: 10000 });
    });
  });

  await test.step("Step 7: Validate Tus Negocios", async () => {
    await runStep(report, "Tus Negocios", async () => {
      await expect(page.getByRole("heading", { name: /Tus Negocios/i })).toBeVisible({ timeout: 10000 });
      await expect(page.getByRole("button", { name: "Agregar Negocio" })).toBeVisible({ timeout: 10000 });
      await expect(page.getByText("Tienes 2 de 3 negocios", { exact: true })).toBeVisible({ timeout: 10000 });

      const negociosSection = page.locator("section,div").filter({ hasText: "Tus Negocios" }).first();
      await expect(negociosSection).toBeVisible({ timeout: 10000 });
      const negociosText = (await negociosSection.innerText()).trim();
      expect(negociosText.length).toBeGreaterThan(20);
    });
  });

  await test.step("Step 8: Validate Términos y Condiciones", async () => {
    await runStep(report, "Términos y Condiciones", async () => {
      const termsUrl = await openAndValidateLegalDocument({
        page,
        testInfo,
        linkText: "Términos y Condiciones",
        expectedHeading: /Términos y Condiciones/i,
        screenshotName: "step-8-terminos-y-condiciones.png"
      });
      testInfo.annotations.push({ type: "Términos y Condiciones URL", description: termsUrl });
    });
  });

  await test.step("Step 9: Validate Política de Privacidad", async () => {
    await runStep(report, "Política de Privacidad", async () => {
      const privacyUrl = await openAndValidateLegalDocument({
        page,
        testInfo,
        linkText: "Política de Privacidad",
        expectedHeading: /Política de Privacidad/i,
        screenshotName: "step-9-politica-de-privacidad.png"
      });
      testInfo.annotations.push({ type: "Política de Privacidad URL", description: privacyUrl });
    });
  });

  await test.step("Step 10: Final Report", async () => {
    // eslint-disable-next-line no-console
    console.table(report);

    await testInfo.attach("saleads-mi-negocio-final-report", {
      body: Buffer.from(`${JSON.stringify(report, null, 2)}\n`, "utf-8"),
      contentType: "application/json"
    });

    const failed = Object.entries(report)
      .filter(([, status]) => status !== "PASS")
      .map(([name, status]) => `${name} -> ${status}`);

    expect(
      failed,
      `One or more workflow validations failed:\n${failed.join("\n")}`
    ).toEqual([]);
  });
});
