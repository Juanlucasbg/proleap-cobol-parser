const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function createCheckpointName(label) {
  return label
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
}

async function waitForUiIdle(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle").catch(() => {
    // Some apps keep long-lived requests; DOM readiness is enough fallback.
  });
}

async function safeClick(locator, page) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiIdle(page);
}

async function clickByText(page, text) {
  const button = page.getByRole("button", { name: new RegExp(text, "i") });
  const link = page.getByRole("link", { name: new RegExp(text, "i") });
  const anyText = page.getByText(new RegExp(`^\\s*${escapeRegExp(text)}\\s*$`, "i"));

  if (await button.count()) {
    await safeClick(button.first(), page);
    return;
  }

  if (await link.count()) {
    await safeClick(link.first(), page);
    return;
  }

  await safeClick(anyText.first(), page);
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function attachCheckpoint(page, testInfo, label, fullPage = false) {
  const fileName = `${String(testInfo.attachments.length + 1).padStart(2, "0")}-${createCheckpointName(label)}.png`;
  const screenshotPath = testInfo.outputPath(fileName);

  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(label, {
    path: screenshotPath,
    contentType: "image/png",
  });
}

function reportRow(name, status, details) {
  return { name, status, details };
}

test.describe("SaleADS - Mi Negocio full workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
    const validations = [];
    const providedUrl = process.env.SALEADS_BASE_URL || process.env.BASE_URL;

    test.skip(
      !providedUrl,
      "Set SALEADS_BASE_URL (or BASE_URL) to run this environment-agnostic workflow."
    );

    await test.step("1) Login with Google", async () => {
      await page.goto(providedUrl, { waitUntil: "domcontentloaded" });
      await waitForUiIdle(page);

      const signInWithGoogle = page.getByRole("button", { name: /sign in with google|google/i });
      const popupPromise = context.waitForEvent("page", { timeout: 15000 }).catch(() => null);
      await safeClick(signInWithGoogle.first(), page);

      const googlePopup = await popupPromise;

      if (googlePopup) {
        await googlePopup.waitForLoadState("domcontentloaded");

        const accountOption = googlePopup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false });
        if (await accountOption.count()) {
          await accountOption.first().click();
        }

        await waitForUiIdle(googlePopup);
        await googlePopup.close().catch(() => {
          // Keep running if popup remains managed by browser.
        });
      } else {
        const accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false });
        if (await accountOption.count()) {
          await accountOption.first().click();
          await waitForUiIdle(page);
        }
      }

      await expect(page.locator("aside")).toBeVisible();
      await attachCheckpoint(page, testInfo, "dashboard-loaded");
      validations.push(reportRow("Login", "PASS", "Main app and left sidebar are visible."));
    });

    await test.step("2) Open Mi Negocio menu", async () => {
      await clickByText(page, "Negocio");
      await clickByText(page, "Mi Negocio");

      await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
      await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
      await attachCheckpoint(page, testInfo, "mi-negocio-menu-expanded");
      validations.push(
        reportRow(
          "Mi Negocio menu",
          "PASS",
          "Submenu expanded and both options are visible."
        )
      );
    });

    await test.step("3) Validate Agregar Negocio modal", async () => {
      await clickByText(page, "Agregar Negocio");

      const modal = page.getByRole("dialog").first();
      await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
      await expect(modal.getByLabel(/Nombre del Negocio/i)).toBeVisible();
      await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
      await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

      const nameInput = modal.getByLabel(/Nombre del Negocio/i);
      await nameInput.fill("Negocio Prueba Automatización");
      await attachCheckpoint(page, testInfo, "agregar-negocio-modal");
      await safeClick(modal.getByRole("button", { name: /Cancelar/i }), page);
      validations.push(
        reportRow(
          "Agregar Negocio modal",
          "PASS",
          "Required title, fields, quota text, and action buttons validated."
        )
      );
    });

    await test.step("4) Open Administrar Negocios", async () => {
      if (!(await page.getByText(/Administrar Negocios/i).first().isVisible())) {
        await clickByText(page, "Mi Negocio");
      }
      await clickByText(page, "Administrar Negocios");
      await waitForUiIdle(page);

      await expect(page.getByText(/Informaci[oó]n General/i)).toBeVisible();
      await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
      await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
      await expect(page.getByText(/Secci[oó]n Legal/i)).toBeVisible();
      await attachCheckpoint(page, testInfo, "administrar-negocios-account-page", true);

      validations.push(
        reportRow(
          "Administrar Negocios view",
          "PASS",
          "All required account sections are visible."
        )
      );
    });

    await test.step("5) Validate Información General", async () => {
      const infoGeneralSection = page.locator("section,div").filter({
        has: page.getByText(/Informaci[oó]n General/i),
      }).first();

      await expect(infoGeneralSection.getByText(/@/)).toBeVisible();
      await expect(infoGeneralSection.getByText(/BUSINESS PLAN/i)).toBeVisible();
      await expect(infoGeneralSection.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
      validations.push(
        reportRow(
          "Información General",
          "PASS",
          "User identity, BUSINESS PLAN and Cambiar Plan are visible."
        )
      );
    });

    await test.step("6) Validate Detalles de la Cuenta", async () => {
      const detailsSection = page.locator("section,div").filter({
        has: page.getByText(/Detalles de la Cuenta/i),
      }).first();

      await expect(detailsSection.getByText(/Cuenta creada/i)).toBeVisible();
      await expect(detailsSection.getByText(/Estado activo/i)).toBeVisible();
      await expect(detailsSection.getByText(/Idioma seleccionado/i)).toBeVisible();
      validations.push(
        reportRow(
          "Detalles de la Cuenta",
          "PASS",
          "Cuenta creada, Estado activo and Idioma seleccionado are visible."
        )
      );
    });

    await test.step("7) Validate Tus Negocios", async () => {
      const businessesSection = page.locator("section,div").filter({
        has: page.getByText(/Tus Negocios/i),
      }).first();

      await expect(businessesSection.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(
        businessesSection.getByRole("button", { name: /Agregar Negocio/i })
      ).toBeVisible();

      await expect(
        businessesSection.locator("li,article,div").filter({ hasText: /negocio|business/i }).first()
      ).toBeVisible();

      validations.push(
        reportRow(
          "Tus Negocios",
          "PASS",
          "Business list, add button and quota text are visible."
        )
      );
    });

    async function validateLegalLink(linkText, reportName, headingRegex, screenshotLabel) {
      const appUrlBeforeClick = page.url();
      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await clickByText(page, linkText);
      const popup = await popupPromise;

      const legalPage = popup || page;
      await legalPage.waitForLoadState("domcontentloaded");
      await waitForUiIdle(legalPage);

      await expect(legalPage.getByRole("heading", { name: headingRegex }).first()).toBeVisible();
      await expect(legalPage.locator("main,article,section,body")).toContainText(/[A-Za-z]{20,}/);

      await attachCheckpoint(legalPage, testInfo, screenshotLabel, true);
      const finalUrl = legalPage.url();

      validations.push(reportRow(reportName, "PASS", `Validated legal page at ${finalUrl}`));

      if (popup) {
        await popup.close();
        await page.bringToFront();
      } else if (page.url() !== appUrlBeforeClick) {
        await page.goBack().catch(() => {});
        await waitForUiIdle(page);
      }
    }

    await test.step("8) Validate Términos y Condiciones", async () => {
      await validateLegalLink(
        "Términos y Condiciones",
        "Términos y Condiciones",
        /T[eé]rminos y Condiciones/i,
        "terminos-y-condiciones-page"
      );
    });

    await test.step("9) Validate Política de Privacidad", async () => {
      await validateLegalLink(
        "Política de Privacidad",
        "Política de Privacidad",
        /Pol[ií]tica de Privacidad/i,
        "politica-de-privacidad-page"
      );
    });

    await test.step("10) Final Report", async () => {
      const report = {
        workflow: "saleads_mi_negocio_full_test",
        results: validations,
      };
      const reportJson = JSON.stringify(report, null, 2);
      console.log(reportJson);

      await testInfo.attach("final-report.json", {
        body: Buffer.from(reportJson, "utf-8"),
        contentType: "application/json",
      });

      for (const item of validations) {
        expect(item.status, `${item.name} should be PASS`).toBe("PASS");
      }
    });
  });
});
