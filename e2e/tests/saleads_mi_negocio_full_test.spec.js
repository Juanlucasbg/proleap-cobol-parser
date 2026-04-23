const { test, expect } = require("@playwright/test");

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const TEST_NAME = "saleads_mi_negocio_full_test";

const resultState = {
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

function visibleTextRegex(text) {
  return new RegExp(`^\\s*${escapeRegex(text)}\\s*$`, "i");
}

function containsTextRegex(text) {
  return new RegExp(escapeRegex(text), "i");
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUiAfterClick(pageOrFrame, locator) {
  await expect(locator).toBeVisible({ timeout: 20000 });
  await locator.click();
  await pageOrFrame.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => {});
  await pageOrFrame.waitForTimeout(500);
}

async function clickFirstVisible(page, locatorCandidates) {
  for (const locator of locatorCandidates) {
    if (await locator.first().isVisible().catch(() => false)) {
      await waitForUiAfterClick(page, locator.first());
      return true;
    }
  }
  return false;
}

async function clickFirstVisibleWithPopup(page, context, locatorCandidates) {
  for (const locator of locatorCandidates) {
    const candidate = locator.first();
    if (await candidate.isVisible().catch(() => false)) {
      const popupPromise = page.waitForEvent("popup", { timeout: 8000 }).catch(() => null);
      await candidate.click();
      const popup = await popupPromise;
      const activePage = popup || page;
      await activePage.waitForLoadState("domcontentloaded", { timeout: 30000 });
      await activePage.waitForLoadState("networkidle", { timeout: 30000 }).catch(() => {});
      return { clicked: true, activePage, context };
    }
  }
  return { clicked: false, activePage: page, context };
}

async function openSection(page, sectionName) {
  const sectionLocator = page.getByRole("button", {
    name: containsTextRegex(sectionName),
  });
  if (await sectionLocator.first().isVisible().catch(() => false)) {
    await waitForUiAfterClick(page, sectionLocator.first());
    return;
  }

  const genericClickable = page.locator("a, button, [role='menuitem'], [role='treeitem'], div")
    .filter({ hasText: containsTextRegex(sectionName) })
    .first();

  await waitForUiAfterClick(page, genericClickable);
}

async function clickMenuItemByText(page, itemText) {
  const candidates = [
    page.getByRole("button", { name: visibleTextRegex(itemText) }),
    page.getByRole("link", { name: visibleTextRegex(itemText) }),
    page.getByRole("menuitem", { name: visibleTextRegex(itemText) }),
    page.locator("button, a, [role='menuitem'], [role='treeitem'], li, div")
      .filter({ hasText: visibleTextRegex(itemText) }),
  ];

  const clicked = await clickFirstVisible(page, candidates);
  if (!clicked) {
    throw new Error(`No se encontró un elemento clickeable para "${itemText}".`);
  }
}

async function assertSectionVisible(page, sectionTitle) {
  const sectionByHeading = page.getByRole("heading", { name: containsTextRegex(sectionTitle) }).first();
  if (await sectionByHeading.isVisible().catch(() => false)) {
    await expect(sectionByHeading).toBeVisible({ timeout: 20000 });
    return;
  }

  const sectionByText = page.getByText(containsTextRegex(sectionTitle)).first();
  await expect(sectionByText).toBeVisible({ timeout: 20000 });
}

async function openLegalLinkAndValidate({
  page,
  context,
  linkText,
  headingText,
  screenshotPath,
  testInfo,
}) {
  const legalLinkCandidates = [
    page.getByRole("link", { name: containsTextRegex(linkText) }),
    page.getByRole("button", { name: containsTextRegex(linkText) }),
    page.locator("a, button").filter({ hasText: containsTextRegex(linkText) }),
  ];
  let legalPage = page;
  let openedNewTab = false;
  let clicked = false;

  for (const locator of legalLinkCandidates) {
    const candidate = locator.first();
    if (await candidate.isVisible().catch(() => false)) {
      const newPagePromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
      await candidate.click();
      const maybeNewPage = await newPagePromise;
      clicked = true;

      if (maybeNewPage) {
        openedNewTab = true;
        legalPage = maybeNewPage;
      } else {
        await page.waitForLoadState("domcontentloaded", { timeout: 30000 });
        await page.waitForLoadState("networkidle", { timeout: 30000 }).catch(() => {});
      }
      break;
    }
  }

  if (!clicked) {
    throw new Error(`No se encontró el enlace legal "${linkText}".`);
  }
  if (openedNewTab) {
    await legalPage.waitForLoadState("domcontentloaded", { timeout: 30000 });
    await legalPage.waitForLoadState("networkidle", { timeout: 30000 }).catch(() => {});
  }

  const heading = legalPage.getByRole("heading", { name: containsTextRegex(headingText) }).first();
  if (await heading.isVisible().catch(() => false)) {
    await expect(heading).toBeVisible({ timeout: 20000 });
  } else {
    await expect(legalPage.getByText(containsTextRegex(headingText)).first()).toBeVisible({ timeout: 20000 });
  }

  const legalBody = legalPage.locator("main, article, [role='main'], body").first();
  await expect(legalBody).toContainText(/\S{10,}/, { timeout: 20000 });

  const finalUrl = legalPage.url();
  await legalPage.screenshot({
    path: `${testInfo.outputDir}/${screenshotPath}`,
    fullPage: true,
  });

  if (openedNewTab) {
    await legalPage.close();
    await page.bringToFront();
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await page.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => {});
  }

  return finalUrl;
}

test.describe(TEST_NAME, () => {
  test("Login con Google y validación completa de Mi Negocio", async ({ page, context }, testInfo) => {
    test.setTimeout(300000);

    await test.step("1) Login with Google", async () => {
      if (!process.env.SALEADS_LOGIN_URL) {
        throw new Error("Debes definir SALEADS_LOGIN_URL con la URL de login del entorno actual.");
      }

      await page.goto(process.env.SALEADS_LOGIN_URL, { waitUntil: "domcontentloaded", timeout: 60000 });
      await page.waitForLoadState("domcontentloaded", { timeout: 30000 });
      await page.waitForLoadState("networkidle", { timeout: 30000 }).catch(() => {});

      const loginResult = await clickFirstVisibleWithPopup(page, context, [
        page.getByRole("button", { name: /sign in with google|google|iniciar con google/i }),
        page.getByRole("link", { name: /sign in with google|google|iniciar con google/i }),
        page.locator("button, a").filter({ hasText: /sign in with google|google|iniciar con google/i }),
      ]);

      if (!loginResult.clicked) {
        throw new Error("No se encontró el botón de login con Google.");
      }
      const authPage = loginResult.activePage;

      const accountCandidate = authPage.getByText(containsTextRegex(ACCOUNT_EMAIL)).first();
      if (await accountCandidate.isVisible().catch(() => false)) {
        await waitForUiAfterClick(authPage, accountCandidate);
      } else {
        const accountInPopup = authPage
          .locator("div[role='button'], li, div")
          .filter({ hasText: containsTextRegex(ACCOUNT_EMAIL) })
          .first();

        if (await accountInPopup.isVisible().catch(() => false)) {
          await waitForUiAfterClick(authPage, accountInPopup);
        }
      }

      if (authPage !== page) {
        await page.bringToFront();
      }

      const sidebar = page.locator("aside, nav").first();
      await expect(sidebar).toBeVisible({ timeout: 60000 });
      resultState.Login = "PASS";

      await page.screenshot({
        path: `${testInfo.outputDir}/checkpoint-dashboard-loaded.png`,
        fullPage: true,
      });
    });

    await test.step("2) Open Mi Negocio menu", async () => {
      await openSection(page, "Negocio");
      await clickMenuItemByText(page, "Mi Negocio");

      await expect(page.getByText(containsTextRegex("Agregar Negocio")).first()).toBeVisible({
        timeout: 20000,
      });
      await expect(page.getByText(containsTextRegex("Administrar Negocios")).first()).toBeVisible({
        timeout: 20000,
      });

      resultState["Mi Negocio menu"] = "PASS";
      await page.screenshot({
        path: `${testInfo.outputDir}/checkpoint-mi-negocio-expanded.png`,
        fullPage: true,
      });
    });

    await test.step("3) Validate Agregar Negocio modal", async () => {
      await clickMenuItemByText(page, "Agregar Negocio");

      const modal = page.getByRole("dialog").first();
      await expect(modal).toBeVisible({ timeout: 20000 });
      await expect(modal.getByText(containsTextRegex("Crear Nuevo Negocio")).first()).toBeVisible();
      await expect(modal.getByLabel(containsTextRegex("Nombre del Negocio")).first()).toBeVisible();
      await expect(modal.getByText(containsTextRegex("Tienes 2 de 3 negocios")).first()).toBeVisible();
      await expect(modal.getByRole("button", { name: containsTextRegex("Cancelar") }).first()).toBeVisible();
      await expect(modal.getByRole("button", { name: containsTextRegex("Crear Negocio") }).first()).toBeVisible();

      await modal.screenshot({
        path: `${testInfo.outputDir}/checkpoint-agregar-negocio-modal.png`,
      });

      const businessName = modal.getByLabel(containsTextRegex("Nombre del Negocio")).first();
      await businessName.click();
      await businessName.fill("Negocio Prueba Automatización");
      await waitForUiAfterClick(page, modal.getByRole("button", { name: containsTextRegex("Cancelar") }).first());
      await expect(modal).not.toBeVisible({ timeout: 20000 });

      resultState["Agregar Negocio modal"] = "PASS";
    });

    await test.step("4) Open Administrar Negocios", async () => {
      const adminVisible = await page.getByText(containsTextRegex("Administrar Negocios")).first().isVisible().catch(() => false);
      if (!adminVisible) {
        await openSection(page, "Negocio");
        await clickMenuItemByText(page, "Mi Negocio");
      }

      await clickMenuItemByText(page, "Administrar Negocios");
      await page.waitForLoadState("networkidle", { timeout: 30000 }).catch(() => {});

      await assertSectionVisible(page, "Información General");
      await assertSectionVisible(page, "Detalles de la Cuenta");
      await assertSectionVisible(page, "Tus Negocios");
      await assertSectionVisible(page, "Sección Legal");

      resultState["Administrar Negocios view"] = "PASS";
      await page.screenshot({
        path: `${testInfo.outputDir}/checkpoint-administrar-negocios-page.png`,
        fullPage: true,
      });
    });

    await test.step("5) Validate Información General", async () => {
      await assertSectionVisible(page, "Información General");
      await expect(page.getByText(/\S+\s+\S+/).first()).toBeVisible({ timeout: 20000 });
      await expect(page.getByText(/@/).first()).toBeVisible({ timeout: 20000 });
      await expect(page.getByText(containsTextRegex("BUSINESS PLAN")).first()).toBeVisible({
        timeout: 20000,
      });
      await expect(
        page.getByRole("button", { name: containsTextRegex("Cambiar Plan") }).first(),
      ).toBeVisible({ timeout: 20000 });

      resultState["Información General"] = "PASS";
    });

    await test.step("6) Validate Detalles de la Cuenta", async () => {
      await assertSectionVisible(page, "Detalles de la Cuenta");
      await expect(page.getByText(containsTextRegex("Cuenta creada")).first()).toBeVisible({
        timeout: 20000,
      });
      await expect(page.getByText(containsTextRegex("Estado activo")).first()).toBeVisible({
        timeout: 20000,
      });
      await expect(page.getByText(containsTextRegex("Idioma seleccionado")).first()).toBeVisible({
        timeout: 20000,
      });

      resultState["Detalles de la Cuenta"] = "PASS";
    });

    await test.step("7) Validate Tus Negocios", async () => {
      await assertSectionVisible(page, "Tus Negocios");
      await expect(page.getByText(containsTextRegex("Tienes 2 de 3 negocios")).first()).toBeVisible({
        timeout: 20000,
      });
      await expect(
        page
          .locator("section, div")
          .filter({ hasText: containsTextRegex("Tus Negocios") })
          .first(),
      ).toBeVisible({ timeout: 20000 });
      await expect(
        page.getByRole("button", { name: visibleTextRegex("Agregar Negocio") }).first(),
      ).toBeVisible({ timeout: 20000 });

      resultState["Tus Negocios"] = "PASS";
    });

    await test.step("8) Validate Términos y Condiciones", async () => {
      await assertSectionVisible(page, "Sección Legal");
      const termsUrl = await openLegalLinkAndValidate({
        page,
        context,
        linkText: "Términos y Condiciones",
        headingText: "Términos y Condiciones",
        screenshotPath: "checkpoint-terminos-y-condiciones.png",
        testInfo,
      });
      resultState["Términos y Condiciones"] = "PASS";
      await testInfo.attach("final-url-terminos-y-condiciones.txt", {
        body: termsUrl,
        contentType: "text/plain",
      });
    });

    await test.step("9) Validate Política de Privacidad", async () => {
      await assertSectionVisible(page, "Sección Legal");
      const privacyUrl = await openLegalLinkAndValidate({
        page,
        context,
        linkText: "Política de Privacidad",
        headingText: "Política de Privacidad",
        screenshotPath: "checkpoint-politica-de-privacidad.png",
        testInfo,
      });
      resultState["Política de Privacidad"] = "PASS";
      await testInfo.attach("final-url-politica-de-privacidad.txt", {
        body: privacyUrl,
        contentType: "text/plain",
      });
    });

    await test.step("10) Final Report", async () => {
      const reportLines = Object.entries(resultState).map(([name, status]) => `${name}: ${status}`);
      const reportBody = `${TEST_NAME}\n${reportLines.join("\n")}\n`;

      await testInfo.attach("final-report.txt", {
        body: reportBody,
        contentType: "text/plain",
      });

      console.log(`\n==== ${TEST_NAME} final report ====`);
      for (const [name, status] of Object.entries(resultState)) {
        console.log(`${name}: ${status}`);
      }
      console.log("===================================\n");

      const failed = Object.entries(resultState).filter(([, status]) => status !== "PASS");
      expect(
        failed,
        `Se encontraron pasos en FAIL:\n${failed.map(([name, status]) => `${name}: ${status}`).join("\n")}`,
      ).toHaveLength(0);
    });
  });
});
