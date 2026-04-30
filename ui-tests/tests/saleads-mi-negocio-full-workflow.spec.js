const { test, expect } = require("@playwright/test");

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

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";

async function waitForUi(page) {
  await page.waitForTimeout(600);
  await page.waitForLoadState("domcontentloaded").catch(() => {});
}

async function firstVisibleLocator(candidates, timeout = 10000) {
  for (const candidate of candidates) {
    try {
      const locator = candidate.first();
      await locator.waitFor({ state: "visible", timeout });
      return locator;
    } catch (_error) {
      // Try next locator candidate.
    }
  }

  throw new Error("No visible locator matched the provided candidates.");
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function captureScreenshot(testInfo, page, name, fullPage = false) {
  await page.screenshot({
    path: testInfo.outputPath(name),
    fullPage,
  });
}

async function maybeSelectGoogleAccount(authPage, accountEmail) {
  const accountLocator = authPage.getByText(accountEmail, { exact: false }).first();
  const visible = await accountLocator.isVisible({ timeout: 10000 }).catch(() => false);

  if (visible) {
    await accountLocator.click();
    await waitForUi(authPage);
  }
}

async function openLegalPageAndValidate({
  appPage,
  testInfo,
  linkTextRegex,
  headingRegex,
  screenshotName,
}) {
  const linkLocator = await firstVisibleLocator([
    appPage.getByRole("link", { name: linkTextRegex }),
    appPage.getByRole("button", { name: linkTextRegex }),
    appPage.getByText(linkTextRegex),
  ]);

  const appUrlBeforeClick = appPage.url();
  const popupPromise = appPage.waitForEvent("popup", { timeout: 8000 }).catch(() => null);

  await clickAndWait(appPage, linkLocator);

  const popup = await popupPromise;
  const documentPage = popup || appPage;

  await documentPage.waitForLoadState("domcontentloaded", { timeout: 30000 });

  const headingLocator = await firstVisibleLocator(
    [
      documentPage.getByRole("heading", { name: headingRegex }),
      documentPage.getByText(headingRegex),
    ],
    20000
  );
  await expect(headingLocator).toBeVisible();

  const bodyHasEnoughText = await documentPage.evaluate(() => {
    const text = (document.body?.innerText || "").replace(/\s+/g, " ").trim();
    return text.length >= 120;
  });
  expect(bodyHasEnoughText).toBeTruthy();

  await captureScreenshot(testInfo, documentPage, screenshotName, true);

  const finalUrl = documentPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
  } else if (appPage.url() !== appUrlBeforeClick) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      await appPage.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" });
    });
    await waitForUi(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  test.setTimeout(300000);

  const report = {};
  const failures = [];

  const markPass = (field, details = {}) => {
    report[field] = { status: "PASS", ...details };
  };

  const markFail = (field, error) => {
    const message = error instanceof Error ? error.message : String(error);
    report[field] = { status: "FAIL", error: message };
    failures.push(`${field}: ${message}`);
  };

  const runStep = async (field, action) => {
    try {
      await action();
      if (!report[field]) {
        markPass(field);
      }
    } catch (error) {
      markFail(field, error);
    }
  };

  await test.step("Step 1: Login with Google", async () => {
    await runStep("Login", async () => {
      const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL || process.env.BASE_URL;

      if (loginUrl) {
        await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
        await waitForUi(page);
      }

      const loginButton = await firstVisibleLocator([
        page.getByRole("button", { name: /sign in with google|iniciar sesión con google|continuar con google/i }),
        page.getByRole("link", { name: /sign in with google|iniciar sesión con google|continuar con google/i }),
        page.getByText(/sign in with google|iniciar sesión con google|continuar con google/i),
      ]);

      const popupPromise = page.waitForEvent("popup", { timeout: 10000 }).catch(() => null);
      await clickAndWait(page, loginButton);

      const popup = await popupPromise;
      let authPage = popup;
      if (!authPage && /accounts\.google\.com/i.test(page.url())) {
        authPage = page;
      }

      if (authPage) {
        await maybeSelectGoogleAccount(authPage, GOOGLE_ACCOUNT_EMAIL);
        if (authPage !== page) {
          await page.bringToFront();
        }
      }

      const sidebar = await firstVisibleLocator(
        [
          page.locator("aside").filter({ hasText: /negocio|mi negocio|dashboard|inicio/i }),
          page.getByRole("navigation"),
          page.locator("aside"),
        ],
        60000
      );
      await expect(sidebar).toBeVisible();

      await captureScreenshot(testInfo, page, "01-dashboard-loaded.png", true);
    });
  });

  await test.step("Step 2: Open Mi Negocio menu", async () => {
    await runStep("Mi Negocio menu", async () => {
      const negocioSection = await firstVisibleLocator(
        [
          page.getByRole("button", { name: /^Negocio$/i }),
          page.getByRole("link", { name: /^Negocio$/i }),
          page.getByText(/^Negocio$/i),
        ],
        12000
      );
      await clickAndWait(page, negocioSection);

      const miNegocioOption = await firstVisibleLocator([
        page.getByRole("button", { name: /^Mi\s*Negocio$/i }),
        page.getByRole("link", { name: /^Mi\s*Negocio$/i }),
        page.getByText(/^Mi\s*Negocio$/i),
      ]);
      await clickAndWait(page, miNegocioOption);

      await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
      await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();

      await captureScreenshot(testInfo, page, "02-mi-negocio-menu-expanded.png", true);
    });
  });

  await test.step("Step 3: Validate Agregar Negocio modal", async () => {
    await runStep("Agregar Negocio modal", async () => {
      const agregarNegocioOption = await firstVisibleLocator([
        page.getByRole("menuitem", { name: /^Agregar Negocio$/i }),
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i),
      ]);
      await clickAndWait(page, agregarNegocioOption);

      const modalTitle = await firstVisibleLocator([
        page.getByRole("heading", { name: /Crear Nuevo Negocio/i }),
        page.getByText(/Crear Nuevo Negocio/i),
      ]);

      const nombreInput = await firstVisibleLocator([
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.locator('input[name*="nombre" i]'),
      ]);

      await expect(modalTitle).toBeVisible();
      await expect(nombreInput).toBeVisible();
      await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /^Cancelar$/i }).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /^Crear Negocio$/i }).first()).toBeVisible();

      await captureScreenshot(testInfo, page, "03-agregar-negocio-modal.png", true);

      await clickAndWait(page, nombreInput);
      await nombreInput.fill("Negocio Prueba Automatización");

      const cancelButton = await firstVisibleLocator([
        page.getByRole("button", { name: /^Cancelar$/i }),
        page.getByText(/^Cancelar$/i),
      ]);
      await clickAndWait(page, cancelButton);
      await expect(modalTitle).toBeHidden({ timeout: 15000 });
    });
  });

  await test.step("Step 4: Open Administrar Negocios", async () => {
    await runStep("Administrar Negocios view", async () => {
      const adminVisible = await page.getByText(/^Administrar Negocios$/i).first().isVisible().catch(() => false);
      if (!adminVisible) {
        const miNegocioOption = await firstVisibleLocator([
          page.getByRole("button", { name: /^Mi\s*Negocio$/i }),
          page.getByRole("link", { name: /^Mi\s*Negocio$/i }),
          page.getByText(/^Mi\s*Negocio$/i),
        ]);
        await clickAndWait(page, miNegocioOption);
      }

      const administrarNegocios = await firstVisibleLocator([
        page.getByRole("menuitem", { name: /^Administrar Negocios$/i }),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i),
      ]);
      await clickAndWait(page, administrarNegocios);

      await expect(page.getByText(/Información General/i).first()).toBeVisible({ timeout: 30000 });
      await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();

      await captureScreenshot(testInfo, page, "04-administrar-negocios-page.png", true);
    });
  });

  await test.step("Step 5: Validate Información General", async () => {
    await runStep("Información General", async () => {
      const emailLocator = await firstVisibleLocator([
        page.locator("text=/[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}/i"),
      ]);
      await expect(emailLocator).toBeVisible();

      const likelyNameVisible = await page.evaluate(() => {
        const textNodes = Array.from(document.querySelectorAll("h1,h2,h3,h4,p,span,div"))
          .map((el) => (el.textContent || "").replace(/\s+/g, " ").trim())
          .filter(Boolean);
        return textNodes.some((text) => {
          if (text.includes("@")) {
            return false;
          }
          if (/información general|business plan|cambiar plan|detalles de la cuenta|tus negocios|sección legal/i.test(text)) {
            return false;
          }
          return /[A-Za-zÁÉÍÓÚÑáéíóúñ]{3,}/.test(text);
        });
      });
      expect(likelyNameVisible).toBeTruthy();

      await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
    });
  });

  await test.step("Step 6: Validate Detalles de la Cuenta", async () => {
    await runStep("Detalles de la Cuenta", async () => {
      await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
      await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
    });
  });

  await test.step("Step 7: Validate Tus Negocios", async () => {
    await runStep("Tus Negocios", async () => {
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible();
      await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();

      const listVisible = await firstVisibleLocator([
        page.locator("li"),
        page.locator("tr"),
        page.locator('[role="row"]'),
        page.locator('[class*="business" i]'),
      ]);
      await expect(listVisible).toBeVisible();
    });
  });

  await test.step("Step 8: Validate Términos y Condiciones", async () => {
    await runStep("Términos y Condiciones", async () => {
      const finalUrl = await openLegalPageAndValidate({
        appPage: page,
        testInfo,
        linkTextRegex: /Términos y Condiciones/i,
        headingRegex: /Términos y Condiciones/i,
        screenshotName: "05-terminos-y-condiciones.png",
      });

      markPass("Términos y Condiciones", { finalUrl });
    });
  });

  await test.step("Step 9: Validate Política de Privacidad", async () => {
    await runStep("Política de Privacidad", async () => {
      const finalUrl = await openLegalPageAndValidate({
        appPage: page,
        testInfo,
        linkTextRegex: /Política de Privacidad/i,
        headingRegex: /Política de Privacidad/i,
        screenshotName: "06-politica-de-privacidad.png",
      });

      markPass("Política de Privacidad", { finalUrl });
    });
  });

  for (const field of REPORT_FIELDS) {
    if (!report[field]) {
      markFail(field, "Step did not execute.");
    }
  }

  const reportJson = JSON.stringify(report, null, 2);
  await testInfo.attach("mi-negocio-final-report", {
    body: reportJson,
    contentType: "application/json",
  });
  console.log("Mi Negocio validation report:\n" + reportJson);

  expect(
    failures,
    `Validation failures detected:\n${failures.map((failure) => `- ${failure}`).join("\n")}`
  ).toHaveLength(0);
});
