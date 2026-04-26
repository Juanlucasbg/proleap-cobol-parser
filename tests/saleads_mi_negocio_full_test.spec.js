const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const OUTPUT_DIR = path.join(process.cwd(), "test-results", "saleads-mi-negocio");
const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const START_URL =
  process.env.SALEADS_START_URL || process.env.SALEADS_URL || process.env.BASE_URL || "";

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

function slugify(value) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .toLowerCase();
}

async function waitForUiToLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle").catch(() => undefined);
}

async function clickByText(page, labels) {
  for (const label of labels) {
    const buttonLocator = page.getByRole("button", { name: label }).first();
    if ((await buttonLocator.count()) > 0) {
      await buttonLocator.click();
      await waitForUiToLoad(page);
      return;
    }

    const textLocator = page.getByText(label, { exact: false }).first();
    if ((await textLocator.count()) > 0) {
      await textLocator.click();
      await waitForUiToLoad(page);
      return;
    }
  }
  throw new Error(`Unable to click any label from: ${labels.join(", ")}`);
}

async function clickByTextWithOptionalPopup(page, context, labels) {
  for (const label of labels) {
    const buttonLocator = page.getByRole("button", { name: label }).first();
    if ((await buttonLocator.count()) > 0) {
      const [popup] = await Promise.all([
        context.waitForEvent("page", { timeout: 7000 }).catch(() => null),
        buttonLocator.click(),
      ]);
      await waitForUiToLoad(page);
      return popup;
    }

    const textLocator = page.getByText(label, { exact: false }).first();
    if ((await textLocator.count()) > 0) {
      const [popup] = await Promise.all([
        context.waitForEvent("page", { timeout: 7000 }).catch(() => null),
        textLocator.click(),
      ]);
      await waitForUiToLoad(page);
      return popup;
    }
  }

  throw new Error(`Unable to click any label from: ${labels.join(", ")}`);
}

async function captureCheckpoint(page, name, fullPage = false) {
  fs.mkdirSync(OUTPUT_DIR, { recursive: true });
  const filePath = path.join(OUTPUT_DIR, `${slugify(name)}.png`);
  await page.screenshot({ path: filePath, fullPage });
}

async function openLegalLinkAndValidate({
  page,
  context,
  linkTexts,
  headingRegex,
  screenshotName,
  stepLabel,
  reportState,
}) {
  let legalPage;

  const openByLink = async () => {
    for (const linkText of linkTexts) {
      const link = page.getByRole("link", { name: linkText }).first();
      if ((await link.count()) > 0) {
        const [newPage] = await Promise.all([
          context.waitForEvent("page", { timeout: 5000 }).catch(() => null),
          link.click(),
        ]);
        if (newPage) {
          legalPage = newPage;
          await legalPage.waitForLoadState("domcontentloaded");
          await legalPage.waitForLoadState("networkidle").catch(() => undefined);
          return true;
        }
        await waitForUiToLoad(page);
        legalPage = page;
        return true;
      }
    }
    return false;
  };

  const openByText = async () => {
    for (const linkText of linkTexts) {
      const item = page.getByText(linkText, { exact: false }).first();
      if ((await item.count()) > 0) {
        const [newPage] = await Promise.all([
          context.waitForEvent("page", { timeout: 5000 }).catch(() => null),
          item.click(),
        ]);
        if (newPage) {
          legalPage = newPage;
          await legalPage.waitForLoadState("domcontentloaded");
          await legalPage.waitForLoadState("networkidle").catch(() => undefined);
          return true;
        }
        await waitForUiToLoad(page);
        legalPage = page;
        return true;
      }
    }
    return false;
  };

  const wasOpened = (await openByLink()) || (await openByText());
  expect(wasOpened).toBeTruthy();

  const activePage = legalPage || page;
  await expect(activePage.getByRole("heading", { name: headingRegex })).toBeVisible();

  const legalContent = activePage
    .locator("main, article, section, body")
    .filter({ hasText: /t[eé]rminos|condiciones|privacidad|datos|uso/i })
    .first();
  await expect(legalContent).toBeVisible();

  await captureCheckpoint(activePage, screenshotName, true);
  reportState.urls[stepLabel] = activePage.url();

  if (activePage !== page) {
    await activePage.close();
    await page.bringToFront();
    await waitForUiToLoad(page);
  }
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio workflow", async ({ page, context }, testInfo) => {
    fs.mkdirSync(OUTPUT_DIR, { recursive: true });
    const reportState = {
      steps: Object.fromEntries(REPORT_FIELDS.map((step) => [step, "FAIL"])),
      urls: {},
    };

    await test.step("1) Login with Google", async () => {
      if (START_URL && /about:blank/i.test(page.url())) {
        await page.goto(START_URL, { waitUntil: "domcontentloaded" });
      }
      expect(
        /about:blank/i.test(page.url()),
        "Provide SALEADS_START_URL if the page is not preloaded on SaleADS login."
      ).toBeFalsy();

      await waitForUiToLoad(page);
      const loginPopup = await clickByTextWithOptionalPopup(page, context, [
        "Sign in with Google",
        "Iniciar sesion con Google",
        "Continuar con Google",
        "Login with Google",
      ]);

      const googleSurface = loginPopup || page;
      await waitForUiToLoad(googleSurface);
      const googleAccountOption = googleSurface.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false });
      if ((await googleAccountOption.count()) > 0) {
        await googleAccountOption.first().click();
        await waitForUiToLoad(googleSurface);
      }
      await page.bringToFront();
      await waitForUiToLoad(page);

      const sidebar = page.locator("aside, nav").filter({ hasText: /negocio|dashboard|inicio/i }).first();
      await expect(sidebar).toBeVisible();
      reportState.steps["Login"] = "PASS";
      await captureCheckpoint(page, "01_dashboard_loaded");
    });

    await test.step("2) Open Mi Negocio menu", async () => {
      const negocioSection = page.getByText("Negocio", { exact: false }).first();
      await expect(negocioSection).toBeVisible();
      await negocioSection.click();
      await waitForUiToLoad(page);

      const miNegocioOption = page.getByText("Mi Negocio", { exact: false }).first();
      await expect(miNegocioOption).toBeVisible();
      await miNegocioOption.click();
      await waitForUiToLoad(page);

      await expect(page.getByText("Agregar Negocio", { exact: false }).first()).toBeVisible();
      await expect(page.getByText("Administrar Negocios", { exact: false }).first()).toBeVisible();
      reportState.steps["Mi Negocio menu"] = "PASS";
      await captureCheckpoint(page, "02_mi_negocio_menu_expanded");
    });

    await test.step("3) Validate Agregar Negocio modal", async () => {
      await page.getByText("Agregar Negocio", { exact: false }).first().click();
      await waitForUiToLoad(page);

      const modalTitle = page.getByText("Crear Nuevo Negocio", { exact: false }).first();
      await expect(modalTitle).toBeVisible();
      const businessNameInput = page
        .getByLabel("Nombre del Negocio", { exact: false })
        .or(page.getByPlaceholder("Nombre del Negocio", { exact: false }))
        .first();
      await expect(businessNameInput).toBeVisible();
      await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
      await expect(page.getByRole("button", { name: "Cancelar" })).toBeVisible();
      await expect(page.getByRole("button", { name: "Crear Negocio" })).toBeVisible();

      await businessNameInput.fill("Negocio Prueba Automatizacion");
      await captureCheckpoint(page, "03_agregar_negocio_modal");
      await page.getByRole("button", { name: "Cancelar" }).click();
      await waitForUiToLoad(page);
      reportState.steps["Agregar Negocio modal"] = "PASS";
    });

    await test.step("4) Open Administrar Negocios", async () => {
      const administrarOption = page.getByText("Administrar Negocios", { exact: false }).first();
      if ((await administrarOption.count()) === 0) {
        const miNegocioOption = page.getByText("Mi Negocio", { exact: false }).first();
        await miNegocioOption.click();
        await waitForUiToLoad(page);
      }

      await page.getByText("Administrar Negocios", { exact: false }).first().click();
      await waitForUiToLoad(page);

      const informacionGeneral = page
        .getByText("Informacion General", { exact: false })
        .first()
        .or(page.getByText("Información General", { exact: false }).first());
      await expect(informacionGeneral).toBeVisible();
      await expect(page.getByText("Detalles de la Cuenta", { exact: false }).first()).toBeVisible();
      await expect(page.getByText("Tus Negocios", { exact: false }).first()).toBeVisible();
      const seccionLegal = page
        .getByText("Seccion Legal", { exact: false })
        .first()
        .or(page.getByText("Sección Legal", { exact: false }).first());
      await expect(seccionLegal).toBeVisible();

      reportState.steps["Administrar Negocios view"] = "PASS";
      await captureCheckpoint(page, "04_administrar_negocios_view", true);
    });

    await test.step("5) Validate Informacion General", async () => {
      const nameOrUser = page
        .locator("section, div")
        .filter({ hasText: /informaci[oó]n general/i })
        .first();
      await expect(nameOrUser).toBeVisible();
      await expect(page.getByText(/@/).first()).toBeVisible();
      await expect(page.getByText("BUSINESS PLAN", { exact: false })).toBeVisible();
      await expect(page.getByRole("button", { name: "Cambiar Plan" })).toBeVisible();
      reportState.steps["Información General"] = "PASS";
    });

    await test.step("6) Validate Detalles de la Cuenta", async () => {
      await expect(page.getByText("Cuenta creada", { exact: false })).toBeVisible();
      await expect(page.getByText("Estado activo", { exact: false })).toBeVisible();
      await expect(page.getByText("Idioma seleccionado", { exact: false })).toBeVisible();
      reportState.steps["Detalles de la Cuenta"] = "PASS";
    });

    await test.step("7) Validate Tus Negocios", async () => {
      await expect(page.getByText("Tus Negocios", { exact: false })).toBeVisible();
      const addBusiness = page
        .getByRole("button", { name: "Agregar Negocio" })
        .first()
        .or(page.getByText("Agregar Negocio", { exact: false }).first());
      await expect(addBusiness).toBeVisible();
      await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
      reportState.steps["Tus Negocios"] = "PASS";
    });

    await test.step("8) Validate Terminos y Condiciones", async () => {
      await openLegalLinkAndValidate({
        page,
        context,
        linkTexts: ["Terminos y Condiciones", "Términos y Condiciones"],
        headingRegex: /t[eé]rminos y condiciones/i,
        screenshotName: "08_terminos_y_condiciones",
        stepLabel: "Términos y Condiciones",
        reportState,
      });
      reportState.steps["Términos y Condiciones"] = "PASS";
    });

    await test.step("9) Validate Politica de Privacidad", async () => {
      await openLegalLinkAndValidate({
        page,
        context,
        linkTexts: ["Politica de Privacidad", "Política de Privacidad"],
        headingRegex: /pol[ií]tica de privacidad/i,
        screenshotName: "09_politica_de_privacidad",
        stepLabel: "Política de Privacidad",
        reportState,
      });
      reportState.steps["Política de Privacidad"] = "PASS";
    });

    const finalReport = {
      testName: "saleads_mi_negocio_full_test",
      statusByStep: reportState.steps,
      legalUrls: reportState.urls,
      artifactsDir: OUTPUT_DIR,
      finishedAt: new Date().toISOString(),
      run: {
        project: testInfo.project.name,
        title: testInfo.title,
      },
    };

    const reportPath = path.join(OUTPUT_DIR, "final-report.json");
    fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
    console.log("FINAL_REPORT", JSON.stringify(finalReport));

    for (const field of REPORT_FIELDS) {
      expect(
        reportState.steps[field],
        `Expected ${field} validation to pass but got ${reportState.steps[field]}`
      ).toBe("PASS");
    }
  });
});
