const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");
const path = require("node:path");

const ARTIFACT_DIR = path.resolve(__dirname, "..", "artifacts");
const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function exactTextRegex(text) {
  return new RegExp(`^\\s*${escapeRegex(text)}\\s*$`, "i");
}

function compactError(error) {
  const message = String(error && error.message ? error.message : error);
  return message.replace(/\s+/g, " ").trim().slice(0, 300);
}

async function waitForUiToLoad(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 });
  await page.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {});
  await page.waitForTimeout(700);
}

async function ensureDir() {
  await fs.mkdir(ARTIFACT_DIR, { recursive: true });
}

async function screenshot(page, fileName, fullPage = false) {
  await ensureDir();
  const filePath = path.join(ARTIFACT_DIR, fileName);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function isVisible(locator, timeout = 3000) {
  try {
    await locator.waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function clickByVisibleText(page, candidates, contextLabel) {
  const strategies = [
    (label) => page.getByRole("button", { name: exactTextRegex(label) }).first(),
    (label) => page.getByRole("link", { name: exactTextRegex(label) }).first(),
    (label) => page.getByRole("menuitem", { name: exactTextRegex(label) }).first(),
    (label) => page.getByText(exactTextRegex(label)).first(),
    (label) => page.getByText(new RegExp(escapeRegex(label), "i")).first()
  ];

  for (const candidate of candidates) {
    for (const locate of strategies) {
      const locator = locate(candidate);
      if (await isVisible(locator, 2500)) {
        await locator.click();
        await waitForUiToLoad(page);
        return candidate;
      }
    }
  }

  throw new Error(`Could not click ${contextLabel} using: ${candidates.join(", ")}`);
}

async function expectTextVisible(page, texts, contextLabel) {
  for (const text of texts) {
    const candidates = [
      page.getByRole("heading", { name: exactTextRegex(text) }).first(),
      page.getByText(exactTextRegex(text)).first(),
      page.getByText(new RegExp(escapeRegex(text), "i")).first()
    ];

    for (const candidate of candidates) {
      if (await isVisible(candidate, 3000)) {
        return;
      }
    }
  }

  throw new Error(`Expected visible text for ${contextLabel}: ${texts.join(" / ")}`);
}

async function hasSidebar(page) {
  const sidebar = page.locator("aside, nav").first();
  const sidebarVisible = await isVisible(sidebar, 1500);
  const negocioVisible = await isVisible(page.getByText(/Negocio/i).first(), 1500);
  const miNegocioVisible = await isVisible(page.getByText(/Mi Negocio/i).first(), 1500);

  return sidebarVisible && (negocioVisible || miNegocioVisible);
}

async function findMainAppPage(context, timeout = 90000) {
  const started = Date.now();

  while (Date.now() - started < timeout) {
    const pages = context.pages();
    for (const candidate of pages) {
      if (await hasSidebar(candidate)) {
        return candidate;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 1200));
  }

  throw new Error("Main application page with sidebar was not detected after login.");
}

async function validateLegalLink(appPage, linkText, headingText, screenshotName) {
  const context = appPage.context();
  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);

  await clickByVisibleText(appPage, [linkText], linkText);
  const popup = await popupPromise;
  const legalPage = popup || appPage;

  await legalPage.bringToFront();
  await waitForUiToLoad(legalPage);

  const headingLocator = legalPage.getByRole("heading", {
    name: new RegExp(escapeRegex(headingText), "i")
  }).first();

  if (!(await isVisible(headingLocator, 20000))) {
    await expectTextVisible(legalPage, [headingText], `${linkText} heading`);
  }

  const bodyText = (await legalPage.locator("body").innerText()).trim();
  expect(bodyText.length, `${linkText} body appears empty.`).toBeGreaterThan(120);

  await screenshot(legalPage, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUiToLoad(appPage);
  } else {
    await legalPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUiToLoad(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  test.setTimeout(300000);
  await ensureDir();

  const report = {
    Login: "NOT RUN",
    "Mi Negocio menu": "NOT RUN",
    "Agregar Negocio modal": "NOT RUN",
    "Administrar Negocios view": "NOT RUN",
    "Información General": "NOT RUN",
    "Detalles de la Cuenta": "NOT RUN",
    "Tus Negocios": "NOT RUN",
    "Términos y Condiciones": "NOT RUN",
    "Política de Privacidad": "NOT RUN"
  };

  const legalUrls = {
    terminosYCondiciones: null,
    politicaDePrivacidad: null
  };

  const failures = [];
  let appPage = null;

  async function validateStep(field, callback) {
    try {
      await callback();
      report[field] = "PASS";
    } catch (error) {
      const failure = compactError(error);
      report[field] = `FAIL: ${failure}`;
      failures.push(`${field}: ${failure}`);
    }
  }

  await validateStep("Login", async () => {
    const loginUrl = process.env.SALEADS_LOGIN_URL;

    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUiToLoad(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "Set SALEADS_LOGIN_URL (environment-specific) or start the test on an already-open SaleADS login page."
      );
    }

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickByVisibleText(
      page,
      [
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Iniciar sesion con Google",
        "Continuar con Google",
        "Login con Google",
        "Google"
      ],
      "Google login button"
    );

    const authPage = await popupPromise;
    const pageAfterClick = authPage || page;
    await pageAfterClick.bringToFront();
    await waitForUiToLoad(pageAfterClick);

    const accountLocator = pageAfterClick.getByText(new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i")).first();
    if (await isVisible(accountLocator, 10000)) {
      await accountLocator.click();
      await waitForUiToLoad(pageAfterClick);
    }

    appPage = await findMainAppPage(context, 100000);
    await appPage.bringToFront();
    await expect(appPage.locator("aside, nav").first()).toBeVisible();
    await expectTextVisible(appPage, ["Negocio", "Mi Negocio"], "left sidebar navigation");
    await screenshot(appPage, "01-dashboard-loaded.png", true);
  });

  await validateStep("Mi Negocio menu", async () => {
    if (!appPage) {
      throw new Error("Blocked because login did not complete.");
    }

    await clickByVisibleText(appPage, ["Negocio"], "Negocio section");
    await clickByVisibleText(appPage, ["Mi Negocio"], "Mi Negocio option");
    await expectTextVisible(appPage, ["Agregar Negocio"], "Mi Negocio submenu");
    await expectTextVisible(appPage, ["Administrar Negocios"], "Mi Negocio submenu");
    await screenshot(appPage, "02-mi-negocio-menu-expanded.png", true);
  });

  await validateStep("Agregar Negocio modal", async () => {
    if (!appPage) {
      throw new Error("Blocked because login did not complete.");
    }

    await clickByVisibleText(appPage, ["Agregar Negocio"], "Agregar Negocio menu option");
    await expectTextVisible(appPage, ["Crear Nuevo Negocio"], "modal title");
    await expectTextVisible(appPage, ["Nombre del Negocio"], "Nombre del Negocio field label");
    await expectTextVisible(appPage, ["Tienes 2 de 3 negocios"], "business quota text");
    await expectTextVisible(appPage, ["Cancelar"], "Cancelar button");
    await expectTextVisible(appPage, ["Crear Negocio"], "Crear Negocio button");

    const nameInputCandidates = [
      appPage.getByLabel(/Nombre del Negocio/i).first(),
      appPage.getByPlaceholder(/Nombre del Negocio/i).first(),
      appPage.locator("input[type='text']").first()
    ];

    for (const candidate of nameInputCandidates) {
      if (await isVisible(candidate, 1500)) {
        await candidate.click();
        await candidate.fill("Negocio Prueba Automatización");
        break;
      }
    }

    await screenshot(appPage, "03-agregar-negocio-modal.png");
    await clickByVisibleText(appPage, ["Cancelar"], "Cancelar modal button");
  });

  await validateStep("Administrar Negocios view", async () => {
    if (!appPage) {
      throw new Error("Blocked because login did not complete.");
    }

    await clickByVisibleText(appPage, ["Negocio"], "Negocio section");
    await clickByVisibleText(appPage, ["Mi Negocio"], "Mi Negocio option");
    await clickByVisibleText(appPage, ["Administrar Negocios"], "Administrar Negocios option");

    await expectTextVisible(appPage, ["Información General"], "account page section");
    await expectTextVisible(appPage, ["Detalles de la Cuenta"], "account page section");
    await expectTextVisible(appPage, ["Tus Negocios"], "account page section");
    await expectTextVisible(appPage, ["Sección Legal"], "account page section");
    await screenshot(appPage, "04-administrar-negocios-page-full.png", true);
  });

  await validateStep("Información General", async () => {
    if (!appPage) {
      throw new Error("Blocked because login did not complete.");
    }

    await expectTextVisible(appPage, ["Información General"], "Información General heading");
    await expectTextVisible(appPage, ["BUSINESS PLAN"], "plan label");
    await expectTextVisible(appPage, ["Cambiar Plan"], "Cambiar Plan button");

    const hasEmail = await isVisible(appPage.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first(), 4000);
    if (!hasEmail) {
      throw new Error("User email is not visible.");
    }

    const hasPossibleName = await isVisible(
      appPage.getByText(/(Nombre|Usuario|Perfil|Juan|Barbier)/i).first(),
      4000
    );

    if (!hasPossibleName) {
      throw new Error("User name indicator is not visible.");
    }
  });

  await validateStep("Detalles de la Cuenta", async () => {
    if (!appPage) {
      throw new Error("Blocked because login did not complete.");
    }

    await expectTextVisible(appPage, ["Detalles de la Cuenta"], "Detalles de la Cuenta heading");
    await expectTextVisible(appPage, ["Cuenta creada"], "Cuenta creada label");
    await expectTextVisible(appPage, ["Estado activo"], "Estado activo label");
    await expectTextVisible(appPage, ["Idioma seleccionado"], "Idioma seleccionado label");
  });

  await validateStep("Tus Negocios", async () => {
    if (!appPage) {
      throw new Error("Blocked because login did not complete.");
    }

    await expectTextVisible(appPage, ["Tus Negocios"], "Tus Negocios heading");
    await expectTextVisible(appPage, ["Agregar Negocio"], "Agregar Negocio button");
    await expectTextVisible(appPage, ["Tienes 2 de 3 negocios"], "business quota text");
  });

  await validateStep("Términos y Condiciones", async () => {
    if (!appPage) {
      throw new Error("Blocked because login did not complete.");
    }

    legalUrls.terminosYCondiciones = await validateLegalLink(
      appPage,
      "Términos y Condiciones",
      "Términos y Condiciones",
      "05-terminos-y-condiciones.png"
    );
  });

  await validateStep("Política de Privacidad", async () => {
    if (!appPage) {
      throw new Error("Blocked because login did not complete.");
    }

    legalUrls.politicaDePrivacidad = await validateLegalLink(
      appPage,
      "Política de Privacidad",
      "Política de Privacidad",
      "06-politica-de-privacidad.png"
    );
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    executedAt: new Date().toISOString(),
    report,
    legalUrls
  };

  await fs.writeFile(path.join(ARTIFACT_DIR, "final-report.json"), JSON.stringify(finalReport, null, 2), "utf8");
  console.log("FINAL_REPORT", JSON.stringify(finalReport));

  expect(
    failures,
    `Validation failures:\n${failures.map((item, idx) => `${idx + 1}. ${item}`).join("\n")}`
  ).toHaveLength(0);
});
