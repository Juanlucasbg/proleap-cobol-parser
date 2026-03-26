const { test } = require("@playwright/test");
const path = require("path");
const fs = require("fs/promises");

const CHECKPOINT_DIR = path.join(__dirname, "..", "artifacts", "screenshots");

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio workflow", async ({ page, context }, testInfo) => {
    const results = {
      Login: { pass: true, details: [] },
      "Mi Negocio menu": { pass: true, details: [] },
      "Agregar Negocio modal": { pass: true, details: [] },
      "Administrar Negocios view": { pass: true, details: [] },
      "Información General": { pass: true, details: [] },
      "Detalles de la Cuenta": { pass: true, details: [] },
      "Tus Negocios": { pass: true, details: [] },
      "Términos y Condiciones": { pass: true, details: [] },
      "Política de Privacidad": { pass: true, details: [] }
    };

    const legalEvidence = {
      terminosUrl: null,
      privacidadUrl: null
    };

    await fs.mkdir(CHECKPOINT_DIR, { recursive: true });

    const safeSlug = (value) =>
      value
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .replace(/[^a-zA-Z0-9]+/g, '-')
        .replace(/^-+|-+$/g, '')
        .toLowerCase();

    const screenshotCheckpoint = async (name, targetPage = page, fullPage = false) => {
      const fileName = `${Date.now()}-${safeSlug(name)}.png`;
      const filePath = path.join(CHECKPOINT_DIR, fileName);
      await targetPage.screenshot({ path: filePath, fullPage });
      await testInfo.attach(name, {
        path: filePath,
        contentType: "image/png"
      });
    };

    const markFailure = (section, reason) => {
      results[section].pass = false;
      results[section].details.push(reason);
    };

    const note = (section, msg) => {
      results[section].details.push(msg);
    };

    const settleUi = async (targetPage = page) => {
      await targetPage.waitForTimeout(700).catch(() => {});
      await targetPage.waitForLoadState("domcontentloaded").catch(() => {});
      await targetPage.waitForLoadState("networkidle", { timeout: 8000 }).catch(() => {});
    };

    const locatorForVisibleText = (text, scopePage = page) => {
      const options = {
        hasText: new RegExp(text, "i")
      };
      return scopePage.getByRole("button", options)
        .or(scopePage.getByRole("link", options))
        .or(scopePage.getByText(new RegExp(text, "i")))
        .first();
    };

    const clickByVisibleText = async (text, section, scopePage = page) => {
      const loc = locatorForVisibleText(text, scopePage);
      try {
        await loc.waitFor({ state: "visible", timeout: 6000 });
        await loc.scrollIntoViewIfNeeded().catch(() => {});
        await loc.click({ timeout: 8000 });
        await settleUi(scopePage);
        note(section, `Clicked "${text}".`);
        return true;
      } catch (error) {
        markFailure(section, `Failed to click "${text}": ${error.message}`);
        return false;
      }
    };

    const assertVisibleText = async (text, section) => {
      const loc = page.getByText(new RegExp(text, "i")).first();
      try {
        await loc.waitFor({ state: "visible", timeout: 10000 });
        note(section, `Visible text confirmed: "${text}".`);
        return true;
      } catch {
        markFailure(section, `Expected text not visible: "${text}".`);
        return false;
      }
    };

    const assertAnyVisible = async (selectors, section, label) => {
      for (const locator of selectors) {
        try {
          await locator.first().waitFor({ state: "visible", timeout: 5000 });
          note(section, `Visible: ${label}.`);
          return true;
        } catch {
          // try next
        }
      }
      markFailure(section, `Expected element not visible: ${label}.`);
      return false;
    };

    // If Playwright opens a blank page but a baseURL is configured, navigate there.
    const configuredBaseUrl = testInfo.project.use.baseURL;
    if (page.url() === "about:blank" && configuredBaseUrl) {
      await page.goto(configuredBaseUrl, { waitUntil: "domcontentloaded" });
      await settleUi();
      note("Login", `Navigated to configured environment URL: ${configuredBaseUrl}`);
    }

    // ---- Step 1: Login with Google ----
    await settleUi();
    const clickedLogin =
      (await clickByVisibleText("Sign in with Google", "Login")) ||
      (await clickByVisibleText("Iniciar sesión con Google", "Login")) ||
      (await clickByVisibleText("Iniciar sesion con Google", "Login")) ||
      (await clickByVisibleText("Google", "Login")) ||
      (await clickByVisibleText("Login", "Login")) ||
      (await clickByVisibleText("Iniciar sesión", "Login")) ||
      (await clickByVisibleText("Iniciar sesion", "Login"));

    if (!clickedLogin) {
      note("Login", "Login click could not be completed; remaining flow may fail.");
    } else {
      const accountOption = page.getByText(/juanlucasbarbiergarzon@gmail\.com/i).first();
      const accountVisible = await accountOption.isVisible().catch(() => false);
      if (accountVisible) {
        await accountOption.click();
        await settleUi();
        note("Login", "Selected Google account juanlucasbarbiergarzon@gmail.com.");
      } else {
        note("Login", "Google account selector not shown; continuing.");
      }
    }

    await assertAnyVisible(
      [
        page.getByRole("navigation"),
        page.getByText(/Negocio/i),
        page.getByText(/Mi Negocio/i)
      ],
      "Login",
      "main app interface and sidebar navigation"
    );
    await screenshotCheckpoint("dashboard-loaded");

    // ---- Step 2: Open Mi Negocio menu ----
    await assertAnyVisible([page.getByRole("navigation")], "Mi Negocio menu", "left sidebar");
    await clickByVisibleText("Negocio", "Mi Negocio menu");
    await clickByVisibleText("Mi Negocio", "Mi Negocio menu");
    await assertVisibleText("Agregar Negocio", "Mi Negocio menu");
    await assertVisibleText("Administrar Negocios", "Mi Negocio menu");
    await screenshotCheckpoint("mi-negocio-expanded-menu");

    // ---- Step 3: Validate Agregar Negocio modal ----
    await clickByVisibleText("Agregar Negocio", "Agregar Negocio modal");
    await assertVisibleText("Crear Nuevo Negocio", "Agregar Negocio modal");
    await assertAnyVisible(
      [
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.locator("input[name*=negocio i], input[id*=negocio i]")
      ],
      "Agregar Negocio modal",
      "Nombre del Negocio input"
    );
    await assertVisibleText("Tienes 2 de 3 negocios", "Agregar Negocio modal");
    await assertAnyVisible(
      [page.getByRole("button", { name: /Cancelar/i })],
      "Agregar Negocio modal",
      "Cancelar button"
    );
    await assertAnyVisible(
      [page.getByRole("button", { name: /Crear Negocio/i })],
      "Agregar Negocio modal",
      "Crear Negocio button"
    );
    await screenshotCheckpoint("agregar-negocio-modal");

    const businessInput =
      page.getByLabel(/Nombre del Negocio/i).first()
        .or(page.getByPlaceholder(/Nombre del Negocio/i).first());
    if (await businessInput.count()) {
      await businessInput.fill("Negocio Prueba Automatización").catch(() => {});
      note("Agregar Negocio modal", "Optional input text entered in Nombre del Negocio.");
    } else {
      note("Agregar Negocio modal", "Optional input action skipped (field not interactable).");
    }
    await clickByVisibleText("Cancelar", "Agregar Negocio modal");

    // ---- Step 4: Open Administrar Negocios ----
    await clickByVisibleText("Mi Negocio", "Administrar Negocios view");
    await clickByVisibleText("Administrar Negocios", "Administrar Negocios view");
    await assertAnyVisible(
      [page.getByText(/Información General|Informacion General/i)],
      "Administrar Negocios view",
      "Información General section"
    );
    await assertAnyVisible(
      [page.getByText(/Detalles de la Cuenta/i)],
      "Administrar Negocios view",
      "Detalles de la Cuenta section"
    );
    await assertAnyVisible(
      [page.getByText(/Tus Negocios/i)],
      "Administrar Negocios view",
      "Tus Negocios section"
    );
    await assertAnyVisible(
      [page.getByText(/Sección Legal|Seccion Legal/i)],
      "Administrar Negocios view",
      "Sección Legal section"
    );
    await screenshotCheckpoint("administrar-negocios-page-full", page, true);

    // ---- Step 5: Validate Información General ----
    await assertAnyVisible(
      [page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/i)],
      "Información General",
      "user email"
    );
    await assertAnyVisible(
      [
        page.getByText(/Nombre|Name|Usuario/i),
        page.locator('[class*="user"], [data-testid*="user"], [class*="profile"], [data-testid*="profile"]')
      ],
      "Información General",
      "user name area"
    );
    await assertVisibleText("BUSINESS PLAN", "Información General");
    await assertAnyVisible(
      [page.getByRole("button", { name: /Cambiar Plan/i })],
      "Información General",
      "Cambiar Plan button"
    );

    // ---- Step 6: Validate Detalles de la Cuenta ----
    await assertAnyVisible(
      [page.getByText(/Cuenta creada/i)],
      "Detalles de la Cuenta",
      "Cuenta creada"
    );
    await assertAnyVisible(
      [page.getByText(/Estado activo/i)],
      "Detalles de la Cuenta",
      "Estado activo"
    );
    await assertAnyVisible(
      [page.getByText(/Idioma seleccionado/i)],
      "Detalles de la Cuenta",
      "Idioma seleccionado"
    );

    // ---- Step 7: Validate Tus Negocios ----
    await assertAnyVisible(
      [
        page.getByText(/Tus Negocios/i),
        page.locator('[class*="business"], [data-testid*="business"]')
      ],
      "Tus Negocios",
      "business list section"
    );
    await assertAnyVisible(
      [page.getByRole("button", { name: /Agregar Negocio/i })],
      "Tus Negocios",
      "Agregar Negocio button"
    );
    await assertVisibleText("Tienes 2 de 3 negocios", "Tus Negocios");

    // ---- Step 8: Validate Términos y Condiciones ----
    const termsPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
    const termsClicked = await clickByVisibleText("Terminos y Condiciones", "Términos y Condiciones")
      || await clickByVisibleText("Términos y Condiciones", "Términos y Condiciones");
    let termsPage = await termsPromise;
    if (!termsPage && termsClicked) {
      await settleUi(page);
      termsPage = page;
    }

    if (termsPage) {
      let termsOpenedSameTab = false;
      if (termsPage !== page) {
        await settleUi(termsPage);
      } else {
        termsOpenedSameTab = true;
      }
      const termsHeading = termsPage.getByRole("heading", { name: /Terminos y Condiciones|Términos y Condiciones/i }).first();
      const termsText = termsPage.getByText(/Terminos y Condiciones|Términos y Condiciones/i).first();
      const termsHeadingVisible = await termsHeading.isVisible().catch(() => false);
      const termsTextVisible = await termsText.isVisible().catch(() => false);
      if (!termsHeadingVisible && !termsTextVisible) {
        markFailure("Términos y Condiciones", "Legal heading/content for Terms not visible.");
      } else {
        note("Términos y Condiciones", "Terms legal heading/content is visible.");
      }
      legalEvidence.terminosUrl = termsPage.url();
      await screenshotCheckpoint("terminos-y-condiciones-page", termsPage, true);
      if (termsPage !== page) {
        await termsPage.close().catch(() => {});
        await page.bringToFront();
      } else if (termsOpenedSameTab) {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await settleUi(page);
      }
    } else {
      markFailure("Términos y Condiciones", "Could not open Terms page.");
    }

    // ---- Step 9: Validate Política de Privacidad ----
    const privacyPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
    const privacyClicked = await clickByVisibleText("Politica de Privacidad", "Política de Privacidad")
      || await clickByVisibleText("Política de Privacidad", "Política de Privacidad");
    let privacyPage = await privacyPromise;
    if (!privacyPage && privacyClicked) {
      await settleUi(page);
      privacyPage = page;
    }

    if (privacyPage) {
      let privacyOpenedSameTab = false;
      if (privacyPage !== page) {
        await settleUi(privacyPage);
      } else {
        privacyOpenedSameTab = true;
      }
      const privacyHeading = privacyPage.getByRole("heading", { name: /Politica de Privacidad|Política de Privacidad/i }).first();
      const privacyText = privacyPage.getByText(/Politica de Privacidad|Política de Privacidad/i).first();
      const privacyHeadingVisible = await privacyHeading.isVisible().catch(() => false);
      const privacyTextVisible = await privacyText.isVisible().catch(() => false);
      if (!privacyHeadingVisible && !privacyTextVisible) {
        markFailure("Política de Privacidad", "Legal heading/content for Privacy not visible.");
      } else {
        note("Política de Privacidad", "Privacy legal heading/content is visible.");
      }
      legalEvidence.privacidadUrl = privacyPage.url();
      await screenshotCheckpoint("politica-de-privacidad-page", privacyPage, true);
      if (privacyPage !== page) {
        await privacyPage.close().catch(() => {});
        await page.bringToFront();
      } else if (privacyOpenedSameTab) {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await settleUi(page);
      }
    } else {
      markFailure("Política de Privacidad", "Could not open Privacy page.");
    }

    // ---- Step 10: Final report ----
    const summary = Object.entries(results).map(([name, data]) => ({
      name,
      status: data.pass ? 'PASS' : 'FAIL',
      details: data.details
    }));

    const finalReport = {
      workflow: "saleads_mi_negocio_full_test",
      reportFields: [
        "Login",
        "Mi Negocio menu",
        "Agregar Negocio modal",
        "Administrar Negocios view",
        "Información General",
        "Detalles de la Cuenta",
        "Tus Negocios",
        "Términos y Condiciones",
        "Política de Privacidad"
      ],
      summary,
      legalUrls: legalEvidence
    };

    const reportPath = testInfo.outputPath("saleads-mi-negocio-report.json");
    await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
    await testInfo.attach("final-report", {
      path: reportPath,
      contentType: "application/json"
    });

    const failed = summary.filter((item) => item.status === "FAIL");
    if (failed.length) {
      throw new Error(
        `Workflow failed in ${failed.length} step(s): ${failed.map((f) => f.name).join(', ')}. See final-report attachment.`
      );
    }
  });
});
