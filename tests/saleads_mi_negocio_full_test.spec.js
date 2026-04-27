const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const BUSINESS_NAME = "Negocio Prueba Automatizacion";
const REPORT_LABELS = [
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

function buildStepReporter() {
  const results = new Map(REPORT_LABELS.map((label) => [label, "FAIL"]));
  return {
    pass(stepLabel) {
      results.set(stepLabel, "PASS");
    },
    fail(stepLabel, error) {
      results.set(stepLabel, `FAIL (${error.message})`);
    },
    dump() {
      return REPORT_LABELS.map((label) => `${label}: ${results.get(label)}`).join("\n");
    },
  };
}

async function waitForUiAfterClick(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
}

async function clickByText(page, text, options = {}) {
  const locator = page.getByText(text, { exact: options.exact ?? true }).first();
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiAfterClick(page);
}

async function expectAnyVisible(candidates, message) {
  for (const candidate of candidates) {
    if (await candidate.first().isVisible().catch(() => false)) {
      await expect(candidate.first()).toBeVisible();
      return;
    }
  }
  throw new Error(message);
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login via Google and validate Mi Negocio workflow", async ({ page, context }) => {
    const report = buildStepReporter();

    // Assume browser is already on login page when no URL is provided.
    if (process.env.SALEADS_BASE_URL) {
      await page.goto(process.env.SALEADS_BASE_URL, { waitUntil: "domcontentloaded" });
      await waitForUiAfterClick(page);
    } else {
      await waitForUiAfterClick(page);
    }

    let termsUrl = "";
    let privacyUrl = "";
    try {
      // STEP 1: Login with Google
      try {
        const signInTriggers = [
          page.getByRole("button", { name: /sign in with google/i }),
          page.getByRole("button", { name: /google/i }),
          page.getByRole("link", { name: /sign in with google/i }),
          page.getByText("Sign in with Google", { exact: false }),
          page.getByText(/iniciar sesi[oó]n con google/i),
        ];

        let clickPerformed = false;
        for (const trigger of signInTriggers) {
          if (await trigger.first().isVisible().catch(() => false)) {
            await trigger.first().click();
            clickPerformed = true;
            break;
          }
        }

        if (!clickPerformed) {
          throw new Error("No Google login trigger was visible.");
        }

        await waitForUiAfterClick(page);

        // Handle Google account picker when it opens in same tab.
        const accountOptionSameTab = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });
        if (await accountOptionSameTab.first().isVisible().catch(() => false)) {
          await accountOptionSameTab.first().click();
          await waitForUiAfterClick(page);
        }

        // Handle Google account picker in popup tab/window.
        for (const popupPage of context.pages()) {
          if (popupPage === page) {
            continue;
          }
          const accountOptionPopup = popupPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });
          if (await accountOptionPopup.first().isVisible().catch(() => false)) {
            await accountOptionPopup.first().click();
            await popupPage.waitForLoadState("domcontentloaded");
            await popupPage.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
          }
        }

        // Validate dashboard + left sidebar presence.
        const sidebarCandidate = page.locator("aside").first();
        await expect(sidebarCandidate).toBeVisible({ timeout: 60_000 });
        await expect(page.locator("main").first()).toBeVisible();
        await page.screenshot({ path: "test-results/step-1-dashboard-loaded.png", fullPage: true });
        report.pass("Login");
      } catch (error) {
        report.fail("Login", error);
        throw error;
      }

      // STEP 2: Open Mi Negocio menu
      try {
        await clickByText(page, "Negocio");
        await clickByText(page, "Mi Negocio");

        await expect(page.getByText("Agregar Negocio", { exact: true })).toBeVisible();
        await expect(page.getByText("Administrar Negocios", { exact: true })).toBeVisible();
        await page.screenshot({ path: "test-results/step-2-mi-negocio-expanded.png", fullPage: true });
        report.pass("Mi Negocio menu");
      } catch (error) {
        report.fail("Mi Negocio menu", error);
        throw error;
      }

      // STEP 3: Validate Agregar Negocio modal
      try {
        await clickByText(page, "Agregar Negocio");
        const modalTitle = page.getByText("Crear Nuevo Negocio", { exact: true });
        await expect(modalTitle).toBeVisible();
        await expect(page.getByLabel("Nombre del Negocio", { exact: true })).toBeVisible();
        await expect(page.getByText("Tienes 2 de 3 negocios", { exact: true })).toBeVisible();
        await expect(page.getByRole("button", { name: "Cancelar", exact: true })).toBeVisible();
        await expect(page.getByRole("button", { name: "Crear Negocio", exact: true })).toBeVisible();

        const nombreInput = page.getByLabel("Nombre del Negocio", { exact: true });
        await nombreInput.click();
        await nombreInput.fill(BUSINESS_NAME);

        await page.screenshot({ path: "test-results/step-3-agregar-negocio-modal.png", fullPage: true });
        await page.getByRole("button", { name: "Cancelar", exact: true }).click();
        await waitForUiAfterClick(page);
        report.pass("Agregar Negocio modal");
      } catch (error) {
        report.fail("Agregar Negocio modal", error);
        throw error;
      }

      // STEP 4: Open Administrar Negocios
      try {
        if (!(await page.getByText("Administrar Negocios", { exact: true }).isVisible().catch(() => false))) {
          await clickByText(page, "Mi Negocio");
        }

        await clickByText(page, "Administrar Negocios");
        await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible();
        await expect(page.getByText("Detalles de la Cuenta", { exact: true })).toBeVisible();
        await expect(page.getByText("Tus Negocios", { exact: true })).toBeVisible();
        await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible();

        await page.screenshot({ path: "test-results/step-4-administrar-negocios.png", fullPage: true });
        report.pass("Administrar Negocios view");
      } catch (error) {
        report.fail("Administrar Negocios view", error);
        throw error;
      }

      // STEP 5: Validate Información General
      try {
        await expectAnyVisible(
          [
            page.getByText(/nombre/i),
            page.getByText(/usuario/i),
            page.getByLabel(/nombre/i),
            page.getByPlaceholder(/nombre/i),
          ],
          "User name indicator is not visible in Informacion General."
        );
        await expect(page.getByText(/@/, { exact: false })).toBeVisible();
        await expect(page.getByText("BUSINESS PLAN", { exact: true })).toBeVisible();
        await expect(page.getByRole("button", { name: "Cambiar Plan", exact: true })).toBeVisible();
        report.pass("Información General");
      } catch (error) {
        report.fail("Información General", error);
        throw error;
      }

      // STEP 6: Validate Detalles de la Cuenta
      try {
        await expect(page.getByText("Cuenta creada", { exact: false })).toBeVisible();
        await expect(page.getByText("Estado activo", { exact: false })).toBeVisible();
        await expect(page.getByText("Idioma seleccionado", { exact: false })).toBeVisible();
        report.pass("Detalles de la Cuenta");
      } catch (error) {
        report.fail("Detalles de la Cuenta", error);
        throw error;
      }

      // STEP 7: Validate Tus Negocios
      try {
        await expect(page.getByText("Tus Negocios", { exact: true })).toBeVisible();
        await expect(page.getByRole("button", { name: "Agregar Negocio", exact: true })).toBeVisible();
        await expect(page.getByText("Tienes 2 de 3 negocios", { exact: true })).toBeVisible();
        report.pass("Tus Negocios");
      } catch (error) {
        report.fail("Tus Negocios", error);
        throw error;
      }

      // STEP 8: Validate Términos y Condiciones
      try {
        const termsLink = page.getByText(/t[eé]rminos y condiciones/i).first();
        await expect(termsLink).toBeVisible();

        const popupPromise = page.waitForEvent("popup", { timeout: 5_000 }).catch(() => null);
        await termsLink.click();
        await waitForUiAfterClick(page);
        const termsPopup = await popupPromise;

        const termsPage = termsPopup ?? page;
        await termsPage.waitForLoadState("domcontentloaded");
        await termsPage.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
        await expect(termsPage.getByText(/t[eé]rminos y condiciones/i)).toBeVisible();
        const termsBodyText = (await termsPage.locator("body").innerText()).trim();
        expect(termsBodyText.length).toBeGreaterThan(120);
        await termsPage.screenshot({ path: "test-results/step-8-terminos-y-condiciones.png", fullPage: true });
        termsUrl = termsPage.url();
        test.info().annotations.push({ type: "terms-url", description: termsUrl });

        if (termsPopup) {
          await termsPopup.close();
        } else {
          await termsPage.goBack({ waitUntil: "domcontentloaded" });
          await waitForUiAfterClick(termsPage);
        }
        report.pass("Términos y Condiciones");
      } catch (error) {
        report.fail("Términos y Condiciones", error);
        throw error;
      }

      // STEP 9: Validate Política de Privacidad
      try {
        const privacyLink = page.getByText(/pol[ií]tica de privacidad/i).first();
        await expect(privacyLink).toBeVisible();

        const popupPromise = page.waitForEvent("popup", { timeout: 5_000 }).catch(() => null);
        await privacyLink.click();
        await waitForUiAfterClick(page);
        const privacyPopup = await popupPromise;

        const privacyPage = privacyPopup ?? page;
        await privacyPage.waitForLoadState("domcontentloaded");
        await privacyPage.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
        await expect(privacyPage.getByText(/pol[ií]tica de privacidad/i)).toBeVisible();
        const privacyBodyText = (await privacyPage.locator("body").innerText()).trim();
        expect(privacyBodyText.length).toBeGreaterThan(120);
        await privacyPage.screenshot({ path: "test-results/step-9-politica-de-privacidad.png", fullPage: true });
        privacyUrl = privacyPage.url();
        test.info().annotations.push({ type: "privacy-url", description: privacyUrl });

        if (privacyPopup) {
          await privacyPopup.close();
        } else {
          await privacyPage.goBack({ waitUntil: "domcontentloaded" });
          await waitForUiAfterClick(privacyPage);
        }
        report.pass("Política de Privacidad");
      } catch (error) {
        report.fail("Política de Privacidad", error);
        throw error;
      }
    } finally {
      // STEP 10: Final report
      const finalReport = [
        "SaleADS Mi Negocio Workflow Result",
        report.dump(),
        `Terminos y Condiciones URL: ${termsUrl || "N/A"}`,
        `Politica de Privacidad URL: ${privacyUrl || "N/A"}`,
      ].join("\n");

      await test.info().attach("final-report.txt", {
        body: Buffer.from(finalReport, "utf-8"),
        contentType: "text/plain",
      });
    }
  });
});
