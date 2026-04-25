const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const USER_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const SCREENSHOT_DIR = path.join(__dirname, "..", "artifacts", "screenshots");
const REPORT_PATH = path.join(__dirname, "..", "artifacts", "final-report.json");
const LEGAL_URLS_PATH = path.join(__dirname, "..", "artifacts", "legal-urls.json");

function sanitizeFileName(name) {
  return name
    .normalize("NFKD")
    .replace(/[^\w\s-]/g, "")
    .trim()
    .replace(/\s+/g, "-")
    .toLowerCase();
}

function escapeRegex(raw) {
  return raw.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function ensureArtifacts() {
  await fs.promises.mkdir(SCREENSHOT_DIR, { recursive: true });
  await fs.promises.mkdir(path.dirname(REPORT_PATH), { recursive: true });
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
}

async function clickByVisibleText(page, ...texts) {
  for (const text of texts) {
    const candidates = [
      page.getByRole("button", { name: new RegExp(escapeRegex(text), "i") }).first(),
      page.getByRole("link", { name: new RegExp(escapeRegex(text), "i") }).first(),
      page.getByRole("menuitem", { name: new RegExp(escapeRegex(text), "i") }).first(),
      page.getByText(text, { exact: false }).first(),
    ];

    for (const candidate of candidates) {
      if (await candidate.isVisible().catch(() => false)) {
        await candidate.click();
        await waitForUi(page);
        return true;
      }
    }
  }

  return false;
}

async function waitForAnyVisible(page, candidateTexts, timeoutMs = 10000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    for (const text of candidateTexts) {
      const byText = page.getByText(text, { exact: false }).first();
      if (await byText.isVisible().catch(() => false)) {
        return true;
      }
    }
    await page.waitForTimeout(300);
  }
  return false;
}

async function goToLoginPageIfNeeded(page) {
  const loginIndicators = [
    "Sign in with Google",
    "Iniciar sesión con Google",
    "Acceder con Google",
    "Continuar con Google",
  ];

  if (await waitForAnyVisible(page, loginIndicators, 4000)) {
    return;
  }

  // If we land on a marketing page, use the visible CTA/login actions to reach auth.
  const ctaTexts = [
    "Iniciar sesión",
    "Iniciar Sesión",
    "Login",
    "Log in",
    "Acceder",
    "Get started",
    "Start now",
    "Activar cuenta",
    "ACTIVAR CUENTA",
    "Select Plan",
  ];

  for (const cta of ctaTexts) {
    const popupPromise = page.waitForEvent("popup", { timeout: 3000 }).catch(() => null);
    const clicked = await clickByVisibleText(page, cta);
    if (!clicked) {
      continue;
    }

    const popupPage = await popupPromise;
    if (popupPage) {
      await popupPage.waitForLoadState("domcontentloaded");
      if (await waitForAnyVisible(popupPage, loginIndicators, 7000)) {
        await popupPage.close().catch(() => Promise.resolve());
        return;
      }
      await popupPage.close().catch(() => Promise.resolve());
      await page.bringToFront();
    }

    if (await waitForAnyVisible(page, loginIndicators, 7000)) {
      return;
    }
  }

  // Last fallback for any environment with custom routing.
  const fallbackPaths = ["/login", "/auth/login", "/signin", "/auth/signin"];
  for (const pathname of fallbackPaths) {
    try {
      const current = new URL(page.url());
      current.pathname = pathname;
      current.search = "";
      current.hash = "";
      await page.goto(current.toString(), { waitUntil: "domcontentloaded" });
      await waitForUi(page);
      if (await waitForAnyVisible(page, loginIndicators, 5000)) {
        return;
      }
    } catch (_error) {
      // Ignore and continue trying the next fallback.
    }
  }
}

async function capture(page, title) {
  const fileName = `${Date.now()}-${sanitizeFileName(title)}.png`;
  const fullPath = path.join(SCREENSHOT_DIR, fileName);
  await page.screenshot({ path: fullPath, fullPage: true });
  return fullPath;
}

function createReport() {
  return {
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
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  test.setTimeout(240000);
  await ensureArtifacts();

  const report = createReport();
  const evidence = {};
  const failures = [];
  const legalUrls = {
    terminosYCondiciones: null,
    politicaDePrivacidad: null,
  };

  const runStep = async (reportKey, step) => {
    try {
      await step();
      report[reportKey] = "PASS";
    } catch (error) {
      report[reportKey] = "FAIL";
      const failureShot = await capture(page, `failure-${reportKey}`).catch(() => null);
      failures.push({
        step: reportKey,
        error: error instanceof Error ? error.message : String(error),
        screenshot: failureShot,
      });
    }
  };

  try {
    const baseUrl = process.env.SALEADS_URL || process.env.BASE_URL;
    if (baseUrl) {
      await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
    }
    await waitForUi(page);
    await goToLoginPageIfNeeded(page);

    await runStep("Login", async () => {
      const loginClicked = await clickByVisibleText(
        page,
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Acceder con Google",
        "Continuar con Google",
        "Continue with Google"
      );
      expect(loginClicked).toBeTruthy();

      const emailOption = page.getByText(USER_EMAIL, { exact: false }).first();
      if (await emailOption.isVisible().catch(() => false)) {
        await emailOption.click();
        await waitForUi(page);
      }

      await expect(page.locator("aside, nav").first()).toBeVisible();
      evidence.dashboard = await capture(page, "dashboard-after-login");
    });

    await runStep("Mi Negocio menu", async () => {
      const negocioClicked = await clickByVisibleText(page, "Negocio");
      expect(negocioClicked).toBeTruthy();

      const miNegocioClicked = await clickByVisibleText(page, "Mi Negocio");
      expect(miNegocioClicked).toBeTruthy();

      await expect(page.getByText("Agregar Negocio", { exact: false }).first()).toBeVisible();
      await expect(page.getByText("Administrar Negocios", { exact: false }).first()).toBeVisible();
      evidence.expandedMenu = await capture(page, "mi-negocio-menu-expanded");
    });

    await runStep("Agregar Negocio modal", async () => {
      const addBusinessClicked = await clickByVisibleText(page, "Agregar Negocio");
      expect(addBusinessClicked).toBeTruthy();

      await expect(page.getByText("Crear Nuevo Negocio", { exact: false }).first()).toBeVisible();

      const businessNameField = page
        .getByLabel("Nombre del Negocio", { exact: false })
        .or(page.getByPlaceholder("Nombre del Negocio"))
        .first();
      await expect(businessNameField).toBeVisible();

      await expect(page.getByText("Tienes 2 de 3 negocios", { exact: false }).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();

      await businessNameField.click();
      await businessNameField.fill("Negocio Prueba Automatización");
      evidence.agregarModal = await capture(page, "agregar-negocio-modal");
      await page.getByRole("button", { name: /Cancelar/i }).first().click();
      await waitForUi(page);
    });

    await runStep("Administrar Negocios view", async () => {
      const adminItem = page.getByText("Administrar Negocios", { exact: false }).first();
      if (!(await adminItem.isVisible().catch(() => false))) {
        await clickByVisibleText(page, "Mi Negocio");
      }

      const manageClicked = await clickByVisibleText(page, "Administrar Negocios");
      expect(manageClicked).toBeTruthy();

      await expect(page.getByText("Información General", { exact: false }).first()).toBeVisible();
      await expect(page.getByText("Detalles de la Cuenta", { exact: false }).first()).toBeVisible();
      await expect(page.getByText("Tus Negocios", { exact: false }).first()).toBeVisible();
      await expect(
        page
          .getByText("Sección Legal", { exact: false })
          .or(page.getByText("Legal", { exact: false }))
          .first()
      ).toBeVisible();
      evidence.accountPage = await capture(page, "administrar-negocios-account-page");
    });

    await runStep("Información General", async () => {
      const infoSection = page
        .locator("section, div")
        .filter({ has: page.getByText("Información General", { exact: false }) })
        .first();
      await expect(infoSection).toBeVisible();

      await expect(page.getByText(USER_EMAIL, { exact: false }).first()).toBeVisible();
      await expect(page.getByText("BUSINESS PLAN", { exact: false }).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();

      // Validate there is user-name-like text beyond static labels and email.
      const sectionText = (await infoSection.innerText()).replace(/\s+/g, " ");
      const inferredName = sectionText
        .replace("Información General", "")
        .replace(USER_EMAIL, "")
        .replace(/BUSINESS\s*PLAN/i, "")
        .replace(/Cambiar Plan/i, "")
        .trim();
      expect(inferredName.length).toBeGreaterThan(0);
    });

    await runStep("Detalles de la Cuenta", async () => {
      await expect(page.getByText("Cuenta creada", { exact: false }).first()).toBeVisible();
      await expect(page.getByText("Estado activo", { exact: false }).first()).toBeVisible();
      await expect(page.getByText("Idioma seleccionado", { exact: false }).first()).toBeVisible();
    });

    await runStep("Tus Negocios", async () => {
      const businessesSection = page
        .locator("section, div")
        .filter({ has: page.getByText("Tus Negocios", { exact: false }) })
        .first();
      await expect(businessesSection).toBeVisible();
      await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
      await expect(page.getByText("Tienes 2 de 3 negocios", { exact: false }).first()).toBeVisible();

      const listContent = (await businessesSection.innerText()).replace(/\s+/g, " ");
      expect(listContent.length).toBeGreaterThan(30);
    });

    await runStep("Términos y Condiciones", async () => {
      const terminosLink = page.getByText("Términos y Condiciones", { exact: false }).first();
      await expect(terminosLink).toBeVisible();

      const popupPromise = page.waitForEvent("popup", { timeout: 5000 }).catch(() => null);
      await terminosLink.click();
      let targetPage = await popupPromise;
      if (!targetPage) {
        targetPage = page;
      }

      await targetPage.waitForLoadState("domcontentloaded");
      await waitForUi(targetPage);
      await expect(targetPage.getByText("Términos y Condiciones", { exact: false }).first()).toBeVisible();

      const legalText = (await targetPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
      expect(legalText.length).toBeGreaterThan(100);

      legalUrls.terminosYCondiciones = targetPage.url();
      evidence.terminos = await capture(targetPage, "terminos-y-condiciones");

      if (targetPage !== page) {
        await targetPage.close();
        await page.bringToFront();
      } else {
        await page.goBack().catch(() => Promise.resolve());
        await waitForUi(page);
      }
    });

    await runStep("Política de Privacidad", async () => {
      const politicaLink = page.getByText("Política de Privacidad", { exact: false }).first();
      await expect(politicaLink).toBeVisible();

      const popupPromise = page.waitForEvent("popup", { timeout: 5000 }).catch(() => null);
      await politicaLink.click();
      let targetPage = await popupPromise;
      if (!targetPage) {
        targetPage = page;
      }

      await targetPage.waitForLoadState("domcontentloaded");
      await waitForUi(targetPage);
      await expect(targetPage.getByText("Política de Privacidad", { exact: false }).first()).toBeVisible();

      const legalText = (await targetPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
      expect(legalText.length).toBeGreaterThan(100);

      legalUrls.politicaDePrivacidad = targetPage.url();
      evidence.politica = await capture(targetPage, "politica-de-privacidad");

      if (targetPage !== page) {
        await targetPage.close();
        await page.bringToFront();
      } else {
        await page.goBack().catch(() => Promise.resolve());
        await waitForUi(page);
      }
    });
  } finally {
    await fs.promises.writeFile(
      REPORT_PATH,
      JSON.stringify(
        {
          test: "saleads_mi_negocio_full_test",
          status: report,
          failures,
          evidence,
        },
        null,
        2
      )
    );
    await fs.promises.writeFile(LEGAL_URLS_PATH, JSON.stringify(legalUrls, null, 2));
  }

  test.info().annotations.push({
    type: "Final Report",
    description: JSON.stringify(report),
  });
  test.info().annotations.push({
    type: "Legal URLs",
    description: JSON.stringify(legalUrls),
  });

  expect(
    failures,
    failures.length ? `Workflow failures:\n${JSON.stringify(failures, null, 2)}` : "No workflow failures."
  ).toHaveLength(0);
});
