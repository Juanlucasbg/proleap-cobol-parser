const { test, expect } = require("@playwright/test");

const LOGIN_URL = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL;
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
  "Política de Privacidad"
];

function defaultReport() {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = "FAIL";
    return acc;
  }, {});
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => {});
}

async function clickFirstVisible(page, candidates, timeout = 15000) {
  for (const locator of candidates) {
    if (await locator.first().isVisible({ timeout }).catch(() => false)) {
      await locator.first().click();
      await waitForUi(page);
      return true;
    }
  }
  return false;
}

async function takeCheckpoint(page, testInfo, fileName, fullPage = false) {
  await page.screenshot({
    path: testInfo.outputPath(fileName),
    fullPage
  });
}

async function openExternalOrSameTab(context, page, trigger) {
  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  await trigger();
  const popup = await popupPromise;

  if (popup) {
    await popup.bringToFront();
    await waitForUi(popup);
    return { targetPage: popup, openedNewTab: true };
  }

  await waitForUi(page);
  return { targetPage: page, openedNewTab: false };
}

test.describe("SaleADS Mi Negocio workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
    const report = defaultReport();
    const legalUrls = {
      terms: "",
      privacy: ""
    };
    const failures = [];

    const recordStep = async (name, runStep) => {
      try {
        await runStep();
        report[name] = "PASS";
      } catch (error) {
        report[name] = "FAIL";
        failures.push({ step: name, error: error.message });
      }
    };

    await recordStep("Login", async () => {
      if (!LOGIN_URL) {
        throw new Error("Missing SALEADS_LOGIN_URL or SALEADS_BASE_URL environment variable.");
      }

      await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded" });
      await waitForUi(page);

      const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
      const clickedLogin = await clickFirstVisible(page, [
        page.getByRole("button", { name: /sign in with google/i }),
        page.getByRole("button", { name: /iniciar sesi[oó]n con google/i }),
        page.getByText(/sign in with google/i),
        page.getByText(/iniciar sesi[oó]n con google/i),
        page.getByRole("button", { name: /google/i })
      ]);

      if (!clickedLogin) {
        throw new Error("Could not find Google login button.");
      }

      // Google can open in popup or same tab.
      const googlePage = (await popupPromise) || page;
      await waitForUi(googlePage);

      const accountLocator = googlePage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false });
      if (await accountLocator.first().isVisible({ timeout: 10000 }).catch(() => false)) {
        await accountLocator.first().click();
        await waitForUi(googlePage);
      }

      if (googlePage !== page) {
        await googlePage.waitForEvent("close", { timeout: 15000 }).catch(() => {});
      }

      await waitForUi(page);
      await expect(
        page.locator("aside, nav").filter({ hasText: /negocio|mi negocio/i }).first()
      ).toBeVisible();

      await takeCheckpoint(page, testInfo, "01-dashboard-loaded.png");
    });

    await recordStep("Mi Negocio menu", async () => {
      const openedMenu = await clickFirstVisible(page, [
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/^Mi Negocio$/i)
      ]);

      if (!openedMenu) {
        throw new Error("Could not open 'Mi Negocio' from left sidebar.");
      }

      await waitForUi(page);
      await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
      await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();

      await takeCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png");
    });

    await recordStep("Agregar Negocio modal", async () => {
      await page.getByText(/agregar negocio/i).first().click();
      await waitForUi(page);

      const modal = page.getByRole("dialog").filter({ hasText: /crear nuevo negocio/i }).first();
      const modalInputFallback = modal.locator(
        "input[placeholder*='Nombre'], input[placeholder*='negocio'], input[name*='nombre'], input[id*='nombre']"
      ).first();
      const hasAssociatedLabel = await modal.getByLabel(/nombre del negocio/i).first().isVisible().catch(() => false);
      const hasFallbackInput = await modalInputFallback.isVisible().catch(() => false);

      await expect(modal).toBeVisible();
      await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();
      await expect(hasAssociatedLabel || hasFallbackInput).toBeTruthy();
      await expect(modal.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
      await expect(modal.getByRole("button", { name: /cancelar/i })).toBeVisible();
      await expect(modal.getByRole("button", { name: /crear negocio/i })).toBeVisible();

      await takeCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");

      const nameInput =
        (await modal.getByLabel(/nombre del negocio/i).first().isVisible().catch(() => false))
          ? modal.getByLabel(/nombre del negocio/i).first()
          : modalInputFallback;
      await nameInput.click();
      await nameInput.fill("Negocio Prueba Automatización");
      await modal.getByRole("button", { name: /cancelar/i }).click();
      await expect(modal).toBeHidden();
    });

    await recordStep("Administrar Negocios view", async () => {
      const miNegocioCollapsed = !(await page
        .getByText(/administrar negocios/i)
        .first()
        .isVisible({ timeout: 3000 })
        .catch(() => false));
      if (miNegocioCollapsed) {
        await clickFirstVisible(page, [
          page.getByRole("button", { name: /mi negocio/i }),
          page.getByRole("link", { name: /mi negocio/i }),
          page.getByText(/^Mi Negocio$/i)
        ]);
      }

      await page.getByText(/administrar negocios/i).first().click();
      await waitForUi(page);

      await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible();
      await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
      await expect(page.getByText(/secci[oó]n legal/i).first()).toBeVisible();

      await takeCheckpoint(page, testInfo, "04-administrar-negocios.png", true);
    });

    await recordStep("Información General", async () => {
      const generalSection = page.locator("section, div").filter({ hasText: /informaci[oó]n general/i }).first();
      await expect(generalSection).toBeVisible();
      await expect(generalSection.getByText(/@/)).toBeVisible();
      await expect(generalSection.getByText(/business plan/i)).toBeVisible();
      await expect(generalSection.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
    });

    await recordStep("Detalles de la Cuenta", async () => {
      const accountSection = page.locator("section, div").filter({ hasText: /detalles de la cuenta/i }).first();
      await expect(accountSection).toBeVisible();
      await expect(accountSection.getByText(/cuenta creada/i)).toBeVisible();
      await expect(accountSection.getByText(/estado activo/i)).toBeVisible();
      await expect(accountSection.getByText(/idioma seleccionado/i)).toBeVisible();
    });

    await recordStep("Tus Negocios", async () => {
      const businessesSection = page.locator("section, div").filter({ hasText: /tus negocios/i }).first();
      await expect(businessesSection).toBeVisible();
      await expect(businessesSection.getByRole("button", { name: /agregar negocio/i })).toBeVisible();
      await expect(businessesSection.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    });

    await recordStep("Términos y Condiciones", async () => {
      const link = page.getByRole("link", { name: /t[eé]rminos y condiciones/i }).first();
      if (!(await link.isVisible({ timeout: 10000 }).catch(() => false))) {
        throw new Error("Could not find 'Términos y Condiciones' link.");
      }

      const { targetPage, openedNewTab } = await openExternalOrSameTab(context, page, async () => {
        await link.click();
      });

      await expect(targetPage.getByRole("heading", { name: /t[eé]rminos y condiciones/i }).first()).toBeVisible();
      await expect(targetPage.locator("body")).toContainText(/t[eé]rminos|condiciones/i);

      legalUrls.terms = targetPage.url();
      await takeCheckpoint(targetPage, testInfo, "05-terminos-y-condiciones.png", true);

      if (openedNewTab) {
        await targetPage.close();
        await page.bringToFront();
        await waitForUi(page);
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await waitForUi(page);
      }
    });

    await recordStep("Política de Privacidad", async () => {
      const link = page.getByRole("link", { name: /pol[ií]tica de privacidad/i }).first();
      if (!(await link.isVisible({ timeout: 10000 }).catch(() => false))) {
        throw new Error("Could not find 'Política de Privacidad' link.");
      }

      const { targetPage, openedNewTab } = await openExternalOrSameTab(context, page, async () => {
        await link.click();
      });

      await expect(targetPage.getByRole("heading", { name: /pol[ií]tica de privacidad/i }).first()).toBeVisible();
      await expect(targetPage.locator("body")).toContainText(/privacidad|datos/i);

      legalUrls.privacy = targetPage.url();
      await takeCheckpoint(targetPage, testInfo, "06-politica-de-privacidad.png", true);

      if (openedNewTab) {
        await targetPage.close();
        await page.bringToFront();
        await waitForUi(page);
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await waitForUi(page);
      }
    });

    await testInfo.attach("final-report.json", {
      body: JSON.stringify(
        {
          report,
          urls: legalUrls
        },
        null,
        2
      ),
      contentType: "application/json"
    });

    console.table(report);
    console.log("Terms URL:", legalUrls.terms || "N/A");
    console.log("Privacy URL:", legalUrls.privacy || "N/A");

    if (failures.length > 0) {
      const summary = failures.map((item) => `${item.step}: ${item.error}`).join("\n");
      throw new Error(`Workflow validation failures:\n${summary}`);
    }
  });
});
