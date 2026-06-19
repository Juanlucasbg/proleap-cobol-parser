const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

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

function ensureDir(directoryPath) {
  fs.mkdirSync(directoryPath, { recursive: true });
}

async function waitForUiToLoad(page, waitMs = 800) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForTimeout(waitMs);
}

async function getFirstVisibleLocator(description, locators, timeoutMs = 12000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() <= deadline) {
    for (const locator of locators) {
      const target = locator.first();
      if (await target.isVisible().catch(() => false)) {
        return target;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  throw new Error(`No visible locator found for: ${description}`);
}

async function clickAndWait(page, locator) {
  await locator.click();
  await waitForUiToLoad(page);
}

async function selectGoogleAccountIfVisible(targetPage) {
  const accountLocator = targetPage.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")).first();
  if (await accountLocator.isVisible().catch(() => false)) {
    await accountLocator.click();
    await waitForUiToLoad(targetPage, 1200);
  }
}

async function validateLegalPageAndReturn({
  page,
  context,
  linkText,
  expectedHeading,
  screenshotPath,
}) {
  const legalLink = await getFirstVisibleLocator(linkText, [
    page.getByRole("link", { name: new RegExp(linkText, "i") }),
    page.getByRole("button", { name: new RegExp(linkText, "i") }),
    page.getByText(new RegExp(linkText, "i")),
  ]);

  const originalUrl = page.url();
  const popupPromise = context.waitForEvent("page", { timeout: 6000 }).catch(() => null);
  await legalLink.click();

  const popup = await popupPromise;
  const legalPage = popup || page;
  await legalPage.waitForLoadState("domcontentloaded");
  await legalPage.waitForTimeout(1200);

  await expect(
    legalPage.getByRole("heading", { name: expectedHeading }).first(),
  ).toBeVisible();
  await expect(legalPage.locator("body")).toContainText(
    /(t[eé]rminos|condiciones|privacidad|legal|datos)/i,
  );

  await legalPage.screenshot({ path: screenshotPath, fullPage: true });
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUiToLoad(page);
  } else if (page.url() !== originalUrl) {
    await page.goBack().catch(() => {});
    await waitForUiToLoad(page);
  }

  return finalUrl;
}

test.describe("SaleADS - Mi Negocio full workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page, context }) => {
    const runId = new Date().toISOString().replace(/[:.]/g, "-");
    const artifactsRoot = path.resolve(__dirname, "../artifacts", runId);
    const screenshotsDir = path.join(artifactsRoot, "screenshots");
    ensureDir(screenshotsDir);

    const report = REPORT_FIELDS.reduce((acc, field) => {
      acc[field] = "FAIL";
      return acc;
    }, {});
    const evidence = {
      "Términos y Condiciones URL": "",
      "Política de Privacidad URL": "",
      errors: {},
    };

    async function runValidation(fieldName, fn) {
      try {
        await fn();
        report[fieldName] = "PASS";
      } catch (error) {
        report[fieldName] = "FAIL";
        evidence.errors[fieldName] = error.message;
      }
    }

    const loginUrl = process.env.SALEADS_LOGIN_URL;
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUiToLoad(page, 1000);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "Set SALEADS_LOGIN_URL or start with a browser already positioned on the SaleADS login page.",
      );
    }

    await runValidation("Login", async () => {
      const loginButton = await getFirstVisibleLocator("Google login", [
        page.getByRole("button", {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
        }),
        page.getByRole("link", {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
        }),
        page.getByText(
          /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
        ),
      ]);

      const googlePopupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
      await clickAndWait(page, loginButton);
      const googlePopup = await googlePopupPromise;

      if (googlePopup) {
        await googlePopup.waitForLoadState("domcontentloaded");
        await selectGoogleAccountIfVisible(googlePopup);
      } else {
        await selectGoogleAccountIfVisible(page);
      }

      const sidebar = await getFirstVisibleLocator("main app sidebar", [
        page.locator("aside"),
        page.getByRole("navigation"),
        page.getByText(/negocio|mi negocio/i),
      ]);

      await expect(sidebar).toBeVisible();
      await page.screenshot({
        path: path.join(screenshotsDir, "01-dashboard-loaded.png"),
        fullPage: true,
      });
    });

    await runValidation("Mi Negocio menu", async () => {
      await expect(page.getByText(/negocio/i).first()).toBeVisible();

      const miNegocio = await getFirstVisibleLocator("Mi Negocio menu item", [
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i),
      ]);
      await clickAndWait(page, miNegocio);

      await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
      await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();
      await page.screenshot({
        path: path.join(screenshotsDir, "02-mi-negocio-expanded.png"),
        fullPage: true,
      });
    });

    await runValidation("Agregar Negocio modal", async () => {
      const agregarNegocio = await getFirstVisibleLocator("Agregar Negocio action", [
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByRole("link", { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i),
      ]);
      await clickAndWait(page, agregarNegocio);

      await expect(page.getByText(/crear nuevo negocio/i).first()).toBeVisible();
      const businessNameInput = page
        .getByRole("textbox", { name: /nombre del negocio/i })
        .first();
      await expect(businessNameInput).toBeVisible();
      await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /cancelar/i }).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /crear negocio/i }).first()).toBeVisible();

      await businessNameInput.fill("Negocio Prueba Automatización");
      await page.screenshot({
        path: path.join(screenshotsDir, "03-agregar-negocio-modal.png"),
        fullPage: true,
      });

      await clickAndWait(page, page.getByRole("button", { name: /cancelar/i }).first());
      await expect(page.getByText(/crear nuevo negocio/i).first()).not.toBeVisible();
    });

    await runValidation("Administrar Negocios view", async () => {
      const miNegocio = await getFirstVisibleLocator("Mi Negocio menu item", [
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i),
      ]);
      if (!(await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false))) {
        await clickAndWait(page, miNegocio);
      }

      const administrarNegocios = await getFirstVisibleLocator("Administrar Negocios action", [
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i),
      ]);
      await clickAndWait(page, administrarNegocios);

      await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible();
      await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
      await expect(page.getByText(/secci[oó]n legal/i).first()).toBeVisible();

      await page.screenshot({
        path: path.join(screenshotsDir, "04-administrar-negocios-page.png"),
        fullPage: true,
      });
    });

    await runValidation("Información General", async () => {
      await expect(page.getByText(/business plan/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible();
      await expect(page.getByText(/@/).first()).toBeVisible();
      await expect(page.locator("main")).toContainText(/informaci[oó]n general/i);
    });

    await runValidation("Detalles de la Cuenta", async () => {
      await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
      await expect(page.getByText(/estado activo/i).first()).toBeVisible();
      await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible();
    });

    await runValidation("Tus Negocios", async () => {
      await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
      await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
      await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    });

    await runValidation("Términos y Condiciones", async () => {
      evidence["Términos y Condiciones URL"] = await validateLegalPageAndReturn({
        page,
        context,
        linkText: "Términos y Condiciones",
        expectedHeading: /t[eé]rminos y condiciones/i,
        screenshotPath: path.join(screenshotsDir, "05-terminos-y-condiciones.png"),
      });
    });

    await runValidation("Política de Privacidad", async () => {
      evidence["Política de Privacidad URL"] = await validateLegalPageAndReturn({
        page,
        context,
        linkText: "Política de Privacidad",
        expectedHeading: /pol[ií]tica de privacidad/i,
        screenshotPath: path.join(screenshotsDir, "06-politica-de-privacidad.png"),
      });
    });

    const finalReport = {
      name: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      results: report,
      evidence,
      artifactsRoot,
    };

    const reportPath = path.join(artifactsRoot, "final-report.json");
    fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2));
    await test.info().attach("final-report", {
      body: JSON.stringify(finalReport, null, 2),
      contentType: "application/json",
    });

    const failedSteps = Object.entries(report)
      .filter(([, status]) => status !== "PASS")
      .map(([stepName]) => stepName);

    expect(
      failedSteps,
      `Validation failed for steps: ${failedSteps.join(", ")}. See final-report attachment.`,
    ).toEqual([]);
  });
});
