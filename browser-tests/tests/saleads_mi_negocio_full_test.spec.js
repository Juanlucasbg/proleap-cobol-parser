const fs = require("node:fs");
const path = require("node:path");
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
  "Política de Privacidad"
];

function sanitizeError(error) {
  if (!error) {
    return "Unknown error";
  }

  if (typeof error === "string") {
    return error;
  }

  return String(error.message || error).slice(0, 1200);
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(800);
}

async function captureCheckpoint(page, evidenceDir, fileName, fullPage = false) {
  await page.screenshot({
    path: path.join(evidenceDir, fileName),
    fullPage
  });
}

async function firstVisibleLocator(locators, errorMessage) {
  for (const locator of locators) {
    const visible = await locator.isVisible({ timeout: 3000 }).catch(() => false);
    if (visible) {
      return locator;
    }
  }

  throw new Error(errorMessage);
}

async function clickAndWait(locator, page) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function selectGoogleAccountIfPresent(page) {
  const accountOption = page.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true });
  const isVisible = await accountOption.isVisible({ timeout: 8000 }).catch(() => false);

  if (isVisible) {
    await accountOption.click();
    await waitForUi(page);
  }
}

async function resolveApplicationPage(context, fallbackPage) {
  const deadline = Date.now() + 120000;

  while (Date.now() < deadline) {
    const openPages = [...context.pages()].reverse();

    for (const candidatePage of openPages) {
      const sidebarCandidate = candidatePage
        .locator("aside, nav")
        .filter({ hasText: /Negocio|Mi Negocio|Administrar Negocios/i })
        .first();
      const sidebarVisible = await sidebarCandidate.isVisible({ timeout: 1200 }).catch(() => false);
      const negocioTextVisible = await candidatePage
        .getByText(/Mi Negocio|Administrar Negocios/i)
        .first()
        .isVisible({ timeout: 1200 })
        .catch(() => false);

      if (sidebarVisible || negocioTextVisible) {
        await candidatePage.bringToFront().catch(() => {});
        return candidatePage;
      }
    }

    await fallbackPage.waitForTimeout(1500);
  }

  return fallbackPage;
}

async function openAndValidateLegalPage({
  appPage,
  context,
  linkPattern,
  headingPattern,
  evidenceDir,
  screenshotName
}) {
  const legalLink = await firstVisibleLocator(
    [
      appPage.getByRole("link", { name: linkPattern }).first(),
      appPage.getByRole("button", { name: linkPattern }).first(),
      appPage.getByText(linkPattern).first()
    ],
    `Could not find legal link matching ${linkPattern}`
  );

  const appUrlBeforeClick = appPage.url();
  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);

  await legalLink.click();
  await waitForUi(appPage);

  let legalPage = await popupPromise;
  if (!legalPage) {
    legalPage = appPage;
  }

  await legalPage.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});

  const legalHeading = await firstVisibleLocator(
    [
      legalPage.getByRole("heading", { name: headingPattern }).first(),
      legalPage.getByText(headingPattern).first()
    ],
    `Could not find heading matching ${headingPattern}`
  );
  await expect(legalHeading).toBeVisible();

  const legalContent = legalPage.locator("main, article, p, li").filter({ hasText: /\S+/ }).first();
  await expect(legalContent).toBeVisible();

  await captureCheckpoint(legalPage, evidenceDir, screenshotName, true);

  const finalUrl = legalPage.url();

  if (legalPage !== appPage) {
    await legalPage.close({ runBeforeUnload: true }).catch(() => {});
    await appPage.bringToFront();
  } else if (finalUrl !== appUrlBeforeClick) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      await appPage.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" });
    });
    await waitForUi(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const evidenceDir = testInfo.outputPath("evidence");
  fs.mkdirSync(evidenceDir, { recursive: true });

  const results = Object.fromEntries(
    REPORT_FIELDS.map((fieldName) => [
      fieldName,
      {
        status: "FAIL",
        details: "Not executed"
      }
    ])
  );
  const urls = {};
  const setPass = (fieldName, details) => {
    results[fieldName] = { status: "PASS", details: details || "Validated successfully" };
  };
  const setFail = (fieldName, error) => {
    results[fieldName] = { status: "FAIL", details: sanitizeError(error) };
  };

  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL;
  const skipNavigation = process.env.SALEADS_SKIP_NAVIGATION === "true";

  if (!skipNavigation) {
    if (!loginUrl) {
      throw new Error(
        "Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL), or set SALEADS_SKIP_NAVIGATION=true to start from an already-open login page."
      );
    }

    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  let appPage = page;

  try {
    const loginButton = await firstVisibleLocator(
      [
        page.getByRole("button", { name: /Sign in with Google|Iniciar sesión con Google|Continuar con Google|Google/i }).first(),
        page.getByRole("link", { name: /Sign in with Google|Iniciar sesión con Google|Continuar con Google|Google/i }).first(),
        page.getByText(/Sign in with Google|Iniciar sesión con Google|Continuar con Google|Google/i).first()
      ],
      "Could not find the Google login button"
    );

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickAndWait(loginButton, page);

    const authPopup = await popupPromise;
    if (authPopup) {
      await authPopup.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
      await selectGoogleAccountIfPresent(authPopup);
    } else {
      await selectGoogleAccountIfPresent(page);
    }

    appPage = await resolveApplicationPage(context, page);
    const sidebar = await firstVisibleLocator(
      [
        appPage.locator("aside, nav").filter({ hasText: /Negocio|Mi Negocio|Administrar Negocios/i }).first(),
        appPage.getByText(/Mi Negocio|Negocio/i).first()
      ],
      "Main application sidebar is not visible after login"
    );
    await expect(sidebar).toBeVisible({ timeout: 120000 });

    await captureCheckpoint(appPage, evidenceDir, "01-dashboard-loaded.png", true);
    setPass("Login");
  } catch (error) {
    setFail("Login", error);
  }

  try {
    const sidebar = appPage.locator("aside, nav").first();
    await expect(sidebar).toBeVisible();

    const negocioSection = appPage.getByText(/^Negocio$/i).first();
    if (await negocioSection.isVisible({ timeout: 3000 }).catch(() => false)) {
      await clickAndWait(negocioSection, appPage);
    }

    const miNegocio = await firstVisibleLocator(
      [
        appPage.getByRole("button", { name: /Mi Negocio/i }).first(),
        appPage.getByRole("link", { name: /Mi Negocio/i }).first(),
        appPage.getByText(/Mi Negocio/i).first()
      ],
      "Could not find Mi Negocio menu option"
    );
    await clickAndWait(miNegocio, appPage);

    await expect(appPage.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(appPage.getByText(/Administrar Negocios/i).first()).toBeVisible();

    await captureCheckpoint(appPage, evidenceDir, "02-mi-negocio-expanded-menu.png", true);
    setPass("Mi Negocio menu");
  } catch (error) {
    setFail("Mi Negocio menu", error);
  }

  try {
    const agregarNegocio = await firstVisibleLocator(
      [
        appPage.getByRole("button", { name: /Agregar Negocio/i }).first(),
        appPage.getByRole("link", { name: /Agregar Negocio/i }).first(),
        appPage.getByText(/Agregar Negocio/i).first()
      ],
      "Could not find Agregar Negocio action"
    );
    await clickAndWait(agregarNegocio, appPage);

    await expect(appPage.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
    await expect(appPage.getByText(/Nombre del Negocio/i).first()).toBeVisible();
    await expect(appPage.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(appPage.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
    await expect(appPage.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();

    const nombreField = appPage.getByLabel(/Nombre del Negocio/i).first();
    if (await nombreField.isVisible({ timeout: 2000 }).catch(() => false)) {
      await nombreField.fill("Negocio Prueba Automatización");
    }

    await captureCheckpoint(appPage, evidenceDir, "03-agregar-negocio-modal.png", true);
    await clickAndWait(appPage.getByRole("button", { name: /Cancelar/i }).first(), appPage);
    setPass("Agregar Negocio modal");
  } catch (error) {
    setFail("Agregar Negocio modal", error);
  }

  try {
    if (!(await appPage.getByText(/Administrar Negocios/i).first().isVisible({ timeout: 3000 }).catch(() => false))) {
      const miNegocio = await firstVisibleLocator(
        [
          appPage.getByRole("button", { name: /Mi Negocio/i }).first(),
          appPage.getByRole("link", { name: /Mi Negocio/i }).first(),
          appPage.getByText(/Mi Negocio/i).first()
        ],
        "Could not re-open Mi Negocio menu"
      );
      await clickAndWait(miNegocio, appPage);
    }

    const administrarNegocios = await firstVisibleLocator(
      [
        appPage.getByRole("button", { name: /Administrar Negocios/i }).first(),
        appPage.getByRole("link", { name: /Administrar Negocios/i }).first(),
        appPage.getByText(/Administrar Negocios/i).first()
      ],
      "Could not find Administrar Negocios option"
    );
    await clickAndWait(administrarNegocios, appPage);

    await expect(appPage.getByText(/Información General/i).first()).toBeVisible();
    await expect(appPage.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(appPage.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(appPage.getByText(/Sección Legal/i).first()).toBeVisible();

    await captureCheckpoint(appPage, evidenceDir, "04-administrar-negocios-cuenta.png", true);
    setPass("Administrar Negocios view");
  } catch (error) {
    setFail("Administrar Negocios view", error);
  }

  try {
    await expect(appPage.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(appPage.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();

    const possibleUserName = appPage.locator("h1, h2, h3, p, span").filter({ hasText: /\S+/ }).first();
    await expect(possibleUserName).toBeVisible();
    await expect(appPage.getByText(/@/).first()).toBeVisible();

    setPass("Información General");
  } catch (error) {
    setFail("Información General", error);
  }

  try {
    await expect(appPage.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(appPage.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(appPage.getByText(/Idioma seleccionado/i).first()).toBeVisible();
    setPass("Detalles de la Cuenta");
  } catch (error) {
    setFail("Detalles de la Cuenta", error);
  }

  try {
    await expect(appPage.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(appPage.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
    await expect(appPage.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    setPass("Tus Negocios");
  } catch (error) {
    setFail("Tus Negocios", error);
  }

  try {
    urls.terminosYCondiciones = await openAndValidateLegalPage({
      appPage,
      context,
      linkPattern: /Términos y Condiciones|Terminos y Condiciones/i,
      headingPattern: /Términos y Condiciones|Terminos y Condiciones/i,
      evidenceDir,
      screenshotName: "05-terminos-y-condiciones.png"
    });
    setPass("Términos y Condiciones", `URL: ${urls.terminosYCondiciones}`);
  } catch (error) {
    setFail("Términos y Condiciones", error);
  }

  try {
    urls.politicaDePrivacidad = await openAndValidateLegalPage({
      appPage,
      context,
      linkPattern: /Política de Privacidad|Politica de Privacidad/i,
      headingPattern: /Política de Privacidad|Politica de Privacidad/i,
      evidenceDir,
      screenshotName: "06-politica-de-privacidad.png"
    });
    setPass("Política de Privacidad", `URL: ${urls.politicaDePrivacidad}`);
  } catch (error) {
    setFail("Política de Privacidad", error);
  }

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    goal: "Login to SaleADS.ai using Google and validate the Mi Negocio module workflow.",
    executedAt: new Date().toISOString(),
    urls,
    results
  };

  const reportPath = path.join(evidenceDir, "final-report.json");
  fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), "utf-8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json"
  });

  const failedFields = Object.entries(results)
    .filter(([, value]) => value.status === "FAIL")
    .map(([fieldName]) => fieldName);
  expect(failedFields, `Failed validations: ${failedFields.join(", ")}`).toEqual([]);
});
