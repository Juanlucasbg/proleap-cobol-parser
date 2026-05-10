const fs = require("node:fs");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const LOGIN_URL = process.env.SALEADS_LOGIN_URL || process.env.BASE_URL || "";
const WAIT_AFTER_CLICK_MS = Number(process.env.SALEADS_WAIT_AFTER_CLICK_MS || "900");

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

function createReport() {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => null);
  await page.waitForLoadState("networkidle", { timeout: 7000 }).catch(() => null);
  await page.waitForTimeout(WAIT_AFTER_CLICK_MS);
}

async function firstVisibleLocator(candidates) {
  for (const candidate of candidates) {
    const first = candidate.first();
    if (await first.isVisible().catch(() => false)) {
      return first;
    }
  }

  return null;
}

async function clickAndWait(page, locator, options = {}) {
  await expect(locator).toBeVisible();

  if (options.waitForDomContentLoaded) {
    await Promise.all([
      page.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => null),
      locator.click(),
    ]);
  } else {
    await locator.click();
  }

  await waitForUi(page);
}

async function captureCheckpoint(page, testInfo, filename, options = {}) {
  const screenshotPath = testInfo.outputPath(filename);
  await page.screenshot({
    path: screenshotPath,
    fullPage: options.fullPage || false,
  });
  await testInfo.attach(filename, {
    path: screenshotPath,
    contentType: "image/png",
  });
}

async function getSidebar(page) {
  return firstVisibleLocator([
    page.locator("aside"),
    page.getByRole("navigation"),
    page.locator('[class*="sidebar"]'),
  ]);
}

async function getGoogleLoginButton(page) {
  return firstVisibleLocator([
    page.getByRole("button", {
      name: /iniciar sesi[oó]n con google|continuar con google|sign in with google/i,
    }),
    page.getByRole("button", { name: /google/i }),
    page.getByText(/iniciar sesi[oó]n con google|continuar con google|sign in with google/i),
    page.locator("button").filter({ hasText: /google/i }),
    page.locator("[role='button']").filter({ hasText: /google/i }),
  ]);
}

async function maybeSelectGoogleAccount(page) {
  const accountRegex = new RegExp(`^${escapeRegExp(GOOGLE_ACCOUNT_EMAIL)}$`, "i");
  const accountOption = await firstVisibleLocator([
    page.getByText(accountRegex),
    page.locator(`[data-identifier="${GOOGLE_ACCOUNT_EMAIL}"]`),
    page.locator(`[data-email="${GOOGLE_ACCOUNT_EMAIL}"]`),
  ]);

  if (!accountOption) {
    return false;
  }

  await clickAndWait(page, accountOption, { waitForDomContentLoaded: true });
  return true;
}

async function getSectionContainer(page, headingRegex) {
  const heading = await firstVisibleLocator([
    page.getByRole("heading", { name: headingRegex }),
    page.getByText(headingRegex),
  ]);

  if (!heading) {
    return null;
  }

  const section = heading.locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
  return (await section.isVisible().catch(() => false)) ? section : heading;
}

async function openLegalLinkAndValidate({
  page,
  context,
  linkNameRegex,
  headingRegex,
  screenshotName,
  testInfo,
}) {
  const legalLink = await firstVisibleLocator([
    page.getByRole("link", { name: linkNameRegex }),
    page.getByText(linkNameRegex),
  ]);

  if (!legalLink) {
    throw new Error(`Legal link not found for ${linkNameRegex}`);
  }

  await expect(legalLink).toBeVisible();

  const maybeNewPagePromise = context.waitForEvent("page", { timeout: 9000 }).catch(() => null);
  await legalLink.click();
  const maybeNewPage = await maybeNewPagePromise;

  const legalPage = maybeNewPage || page;
  await waitForUi(legalPage);

  const legalHeading = await firstVisibleLocator([
    legalPage.getByRole("heading", { name: headingRegex }),
    legalPage.getByText(headingRegex),
  ]);

  if (!legalHeading) {
    throw new Error(`Heading not found: ${headingRegex}`);
  }

  await expect(legalHeading).toBeVisible();

  const legalText = (await legalPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
  if (legalText.length < 180) {
    throw new Error("Legal content text was too short.");
  }

  await captureCheckpoint(legalPage, testInfo, screenshotName, { fullPage: true });
  const finalUrl = legalPage.url();

  if (maybeNewPage) {
    await maybeNewPage.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await legalPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
    await waitForUi(legalPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createReport();
  const legalUrls = {};
  const failures = [];

  const registerStepResult = (field, passed, errorMessage = "") => {
    report[field] = passed ? "PASS" : "FAIL";
    if (!passed) {
      failures.push(`${field}: ${errorMessage}`);
    }
  };

  if (LOGIN_URL) {
    await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else if (page.url() === "about:blank") {
    throw new Error(
      "Browser is on about:blank. Provide SALEADS_LOGIN_URL or open the SaleADS login page before running."
    );
  }

  try {
    const loginButton = await getGoogleLoginButton(page);
    if (!loginButton) {
      throw new Error("Could not find Google login button.");
    }

    const maybeGooglePopupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickAndWait(page, loginButton, { waitForDomContentLoaded: true });
    const googlePopup = await maybeGooglePopupPromise;

    if (googlePopup) {
      await waitForUi(googlePopup);
      await maybeSelectGoogleAccount(googlePopup);
      await waitForUi(page);
    } else {
      await maybeSelectGoogleAccount(page);
      await waitForUi(page);
    }

    const sidebar = await getSidebar(page);
    if (!sidebar) {
      throw new Error("Main app sidebar was not visible after login.");
    }

    await expect(sidebar).toBeVisible({ timeout: 45000 });
    await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png", { fullPage: true });
    registerStepResult("Login", true);
  } catch (error) {
    registerStepResult("Login", false, error.message);
  }

  if (report.Login === "PASS") {
    try {
      const sidebar = await getSidebar(page);
      if (!sidebar) {
        throw new Error("Sidebar is not visible.");
      }

      const negocioSection = await firstVisibleLocator([
        sidebar.getByText(/negocio/i),
        page.getByText(/negocio/i),
      ]);
      if (!negocioSection) {
        throw new Error("Could not locate 'Negocio' section.");
      }

      const miNegocioOption = await firstVisibleLocator([
        sidebar.getByRole("button", { name: /^mi negocio$/i }),
        sidebar.getByText(/^mi negocio$/i),
        page.getByRole("button", { name: /^mi negocio$/i }),
        page.getByText(/^mi negocio$/i),
      ]);
      if (!miNegocioOption) {
        throw new Error("Could not locate 'Mi Negocio' option.");
      }

      await clickAndWait(page, miNegocioOption);

      const agregarNegocioOption = await firstVisibleLocator([
        page.getByRole("button", { name: /^agregar negocio$/i }),
        page.getByRole("link", { name: /^agregar negocio$/i }),
        page.getByText(/^agregar negocio$/i),
      ]);
      const administrarNegociosOption = await firstVisibleLocator([
        page.getByRole("button", { name: /^administrar negocios$/i }),
        page.getByRole("link", { name: /^administrar negocios$/i }),
        page.getByText(/^administrar negocios$/i),
      ]);

      if (!agregarNegocioOption || !administrarNegociosOption) {
        throw new Error("Mi Negocio submenu did not expand correctly.");
      }

      await expect(agregarNegocioOption).toBeVisible();
      await expect(administrarNegociosOption).toBeVisible();

      await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png");
      registerStepResult("Mi Negocio menu", true);
    } catch (error) {
      registerStepResult("Mi Negocio menu", false, error.message);
    }
  } else {
    registerStepResult("Mi Negocio menu", false, "Skipped because Login failed.");
  }

  if (report["Mi Negocio menu"] === "PASS") {
    try {
      const agregarNegocioOption = await firstVisibleLocator([
        page.getByRole("button", { name: /^agregar negocio$/i }),
        page.getByRole("link", { name: /^agregar negocio$/i }),
        page.getByText(/^agregar negocio$/i),
      ]);
      if (!agregarNegocioOption) {
        throw new Error("Could not find 'Agregar Negocio' submenu option.");
      }

      await clickAndWait(page, agregarNegocioOption);

      const modalTitle = await firstVisibleLocator([
        page.getByRole("heading", { name: /crear nuevo negocio/i }),
        page.getByText(/crear nuevo negocio/i),
      ]);
      if (!modalTitle) {
        throw new Error("Modal title 'Crear Nuevo Negocio' was not visible.");
      }

      await expect(modalTitle).toBeVisible();

      const nombreInput = await firstVisibleLocator([
        page.getByLabel(/nombre del negocio/i),
        page.getByPlaceholder(/nombre del negocio/i),
        page.locator("input").filter({ hasText: /nombre del negocio/i }),
      ]);
      if (!nombreInput) {
        throw new Error("Input field 'Nombre del Negocio' was not found.");
      }

      await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /^cancelar$/i })).toBeVisible();
      await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();

      await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");

      await clickAndWait(page, nombreInput);
      await nombreInput.fill("Negocio Prueba Automatización");

      const cancelarButton = page.getByRole("button", { name: /^cancelar$/i }).first();
      await clickAndWait(page, cancelarButton);
      await expect(modalTitle).not.toBeVisible({ timeout: 10000 });

      registerStepResult("Agregar Negocio modal", true);
    } catch (error) {
      registerStepResult("Agregar Negocio modal", false, error.message);
    }
  } else {
    registerStepResult("Agregar Negocio modal", false, "Skipped because Mi Negocio menu failed.");
  }

  if (report["Mi Negocio menu"] === "PASS") {
    try {
      const administrarNegociosOption = await firstVisibleLocator([
        page.getByRole("button", { name: /^administrar negocios$/i }),
        page.getByRole("link", { name: /^administrar negocios$/i }),
        page.getByText(/^administrar negocios$/i),
      ]);

      if (!administrarNegociosOption) {
        const miNegocioOption = await firstVisibleLocator([
          page.getByRole("button", { name: /^mi negocio$/i }),
          page.getByText(/^mi negocio$/i),
        ]);
        if (miNegocioOption) {
          await clickAndWait(page, miNegocioOption);
        }
      }

      const administrarOptionReady = await firstVisibleLocator([
        page.getByRole("button", { name: /^administrar negocios$/i }),
        page.getByRole("link", { name: /^administrar negocios$/i }),
        page.getByText(/^administrar negocios$/i),
      ]);
      if (!administrarOptionReady) {
        throw new Error("Could not find 'Administrar Negocios' option.");
      }

      await clickAndWait(page, administrarOptionReady, { waitForDomContentLoaded: true });

      await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible();
      await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
      await expect(page.getByText(/tus negocios/i)).toBeVisible();
      await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible();

      await captureCheckpoint(page, testInfo, "04-administrar-negocios-view.png", { fullPage: true });
      registerStepResult("Administrar Negocios view", true);
    } catch (error) {
      registerStepResult("Administrar Negocios view", false, error.message);
    }
  } else {
    registerStepResult("Administrar Negocios view", false, "Skipped because Mi Negocio menu failed.");
  }

  if (report["Administrar Negocios view"] === "PASS") {
    try {
      const infoGeneralSection = await getSectionContainer(page, /informaci[oó]n general/i);
      if (!infoGeneralSection) {
        throw new Error("Section 'Información General' not found.");
      }

      const infoText = (await infoGeneralSection.innerText()).replace(/\s+/g, " ").trim();
      if (!/[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}/i.test(infoText)) {
        throw new Error("User email is not visible in 'Información General'.");
      }

      if (!/[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}/.test(infoText)) {
        throw new Error("User name is not visible in 'Información General'.");
      }

      await expect(page.getByText(/business plan/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();

      registerStepResult("Información General", true);
    } catch (error) {
      registerStepResult("Información General", false, error.message);
    }
  } else {
    registerStepResult("Información General", false, "Skipped because Administrar Negocios view failed.");
  }

  if (report["Administrar Negocios view"] === "PASS") {
    try {
      await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
      await expect(page.getByText(/cuenta creada/i)).toBeVisible();
      await expect(page.getByText(/estado activo/i)).toBeVisible();
      await expect(page.getByText(/idioma seleccionado/i)).toBeVisible();

      registerStepResult("Detalles de la Cuenta", true);
    } catch (error) {
      registerStepResult("Detalles de la Cuenta", false, error.message);
    }
  } else {
    registerStepResult("Detalles de la Cuenta", false, "Skipped because Administrar Negocios view failed.");
  }

  if (report["Administrar Negocios view"] === "PASS") {
    try {
      const negociosSection = await getSectionContainer(page, /tus negocios/i);
      if (!negociosSection) {
        throw new Error("Section 'Tus Negocios' not found.");
      }

      await expect(page.getByRole("button", { name: /^agregar negocio$/i })).toBeVisible();
      await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();

      const candidateList = await firstVisibleLocator([
        negociosSection.locator("li"),
        negociosSection.locator("[role='row']"),
        negociosSection.locator("article"),
        negociosSection.locator("table"),
        negociosSection.locator("ul"),
      ]);
      if (!candidateList) {
        throw new Error("Business list is not visible in 'Tus Negocios'.");
      }

      registerStepResult("Tus Negocios", true);
    } catch (error) {
      registerStepResult("Tus Negocios", false, error.message);
    }
  } else {
    registerStepResult("Tus Negocios", false, "Skipped because Administrar Negocios view failed.");
  }

  if (report["Administrar Negocios view"] === "PASS") {
    try {
      legalUrls.terminosYCondiciones = await openLegalLinkAndValidate({
        page,
        context,
        linkNameRegex: /t[eé]rminos y condiciones/i,
        headingRegex: /t[eé]rminos y condiciones/i,
        screenshotName: "05-terminos-y-condiciones.png",
        testInfo,
      });
      registerStepResult("Términos y Condiciones", true);
    } catch (error) {
      registerStepResult("Términos y Condiciones", false, error.message);
    }
  } else {
    registerStepResult("Términos y Condiciones", false, "Skipped because Administrar Negocios view failed.");
  }

  if (report["Administrar Negocios view"] === "PASS") {
    try {
      legalUrls.politicaDePrivacidad = await openLegalLinkAndValidate({
        page,
        context,
        linkNameRegex: /pol[ií]tica de privacidad/i,
        headingRegex: /pol[ií]tica de privacidad/i,
        screenshotName: "06-politica-de-privacidad.png",
        testInfo,
      });
      registerStepResult("Política de Privacidad", true);
    } catch (error) {
      registerStepResult("Política de Privacidad", false, error.message);
    }
  } else {
    registerStepResult("Política de Privacidad", false, "Skipped because Administrar Negocios view failed.");
  }

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    goal: "Login to SaleADS.ai using Google and validate Mi Negocio module workflow.",
    environmentNote:
      "Environment agnostic test. It does not depend on a specific domain and can use SALEADS_LOGIN_URL or an already-open login page.",
    results: report,
    legalUrls,
    failures,
  };

  const finalReportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
  fs.writeFileSync(finalReportPath, JSON.stringify(finalReport, null, 2), "utf-8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: finalReportPath,
    contentType: "application/json",
  });

  // Log concise report for CI output readability.
  console.table(report);
  if (Object.keys(legalUrls).length > 0) {
    console.log("Legal URLs:", legalUrls);
  }

  expect(failures, `Validation failures:\n${failures.join("\n")}`).toEqual([]);
});
