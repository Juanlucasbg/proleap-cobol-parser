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

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 10_000 });
  } catch (error) {
    // Some views keep background requests open; do not fail for this.
  }
  await page.waitForTimeout(600);
}

async function locatorIsVisible(locator, timeout = 2_500) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch (error) {
    return false;
  }
}

async function firstVisible(candidates, description) {
  for (const candidate of candidates) {
    if (await locatorIsVisible(candidate)) {
      return candidate.first();
    }
  }

  throw new Error(`Unable to find visible element for: ${description}`);
}

async function clickAndWait(locator, page) {
  await locator.click();
  await waitForUi(page);
}

async function captureCheckpoint(testInfo, page, name, fullPage = true) {
  const screenshotPath = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(name, { path: screenshotPath, contentType: "image/png" });
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const legalUrls = {};
  const failures = [];

  async function runStep(field, runner) {
    try {
      await runner();
      report[field] = "PASS";
    } catch (error) {
      report[field] = "FAIL";
      failures.push(`${field}: ${error.message}`);
    }
  }

  const miNegocioRegex = /^Mi Negocio$/i;
  const agregarNegocioRegex = /^Agregar Negocio$/i;
  const administrarNegociosRegex = /^Administrar Negocios$/i;

  async function ensureMiNegocioExpanded() {
    const agregarVisible = await locatorIsVisible(page.getByText(agregarNegocioRegex));
    const administrarVisible = await locatorIsVisible(page.getByText(administrarNegociosRegex));

    if (agregarVisible && administrarVisible) {
      return;
    }

    const miNegocioTrigger = await firstVisible(
      [
        page.locator("aside, nav").getByText(miNegocioRegex),
        page.getByRole("button", { name: miNegocioRegex }),
        page.getByText(miNegocioRegex),
      ],
      "Mi Negocio menu trigger",
    );

    await clickAndWait(miNegocioTrigger, page);
    await expect(page.getByText(agregarNegocioRegex)).toBeVisible();
    await expect(page.getByText(administrarNegociosRegex)).toBeVisible();
  }

  async function openLegalDocument(linkText, headingRegex, screenshotName) {
    const legalHeading = page.getByText(/^Sección Legal$/i).first();
    await expect(legalHeading).toBeVisible();

    const legalSectionCandidate = legalHeading.locator(
      "xpath=ancestor::*[self::section or self::article or self::div][1]",
    );
    const legalSection =
      (await legalSectionCandidate.count()) > 0 ? legalSectionCandidate : page.locator("body");

    const linkRegex = new RegExp(`^${escapeRegExp(linkText)}$`, "i");
    const legalLink = await firstVisible(
      [
        legalSection.getByRole("link", { name: linkRegex }),
        legalSection.getByText(linkRegex),
        page.getByRole("link", { name: linkRegex }),
      ],
      `${linkText} legal link`,
    );

    const previousUrl = page.url();
    const popupPromise = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);
    await clickAndWait(legalLink, page);

    const popup = await popupPromise;
    const targetPage = popup || page;
    await waitForUi(targetPage);

    const headingLocator = targetPage.getByRole("heading", { name: headingRegex });
    if (await locatorIsVisible(headingLocator, 10_000)) {
      await expect(headingLocator.first()).toBeVisible();
    } else {
      await expect(targetPage.getByText(headingRegex).first()).toBeVisible();
    }

    const bodyText = (await targetPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
    expect(bodyText.length, `${linkText} content should not be empty`).toBeGreaterThan(200);

    legalUrls[linkText] = targetPage.url();
    await captureCheckpoint(testInfo, targetPage, screenshotName);

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
      return;
    }

    if (page.url() !== previousUrl) {
      await page.goBack({ waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }
  }

  await runStep("Login", async () => {
    if (page.url() === "about:blank") {
      const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.BASE_URL;
      if (!loginUrl) {
        throw new Error(
          "Set SALEADS_LOGIN_URL (or BASE_URL) when the browser does not start on login page.",
        );
      }

      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    const googleLoginRegex =
      /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i;
    const googleLoginTrigger = await firstVisible(
      [
        page.getByRole("button", { name: googleLoginRegex }),
        page.getByRole("link", { name: googleLoginRegex }),
        page.locator("button, a, [role='button']").filter({ hasText: googleLoginRegex }),
      ],
      "Sign in with Google button",
    );

    const popupPromise = context.waitForEvent("page", { timeout: 12_000 }).catch(() => null);
    await clickAndWait(googleLoginTrigger, page);
    const popup = await popupPromise;

    let googlePage = popup;
    if (!googlePage && /accounts\.google\.com/i.test(page.url())) {
      googlePage = page;
    }

    if (googlePage) {
      await waitForUi(googlePage);
      const accountOption = googlePage.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true });
      if (await locatorIsVisible(accountOption, 15_000)) {
        await clickAndWait(accountOption, googlePage);
      }
    }

    if (popup) {
      await popup.waitForClose({ timeout: 45_000 }).catch(async () => {
        await popup.close();
      });
      await page.bringToFront();
    }

    await waitForUi(page);
    await expect(page.locator("aside, nav").first()).toBeVisible();
    await expect(page.getByText(/Negocio/i).first()).toBeVisible();
    await captureCheckpoint(testInfo, page, "01-dashboard-loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    await expect(page.locator("aside, nav").first()).toBeVisible();
    await expect(page.getByText(/^Negocio$/i).first()).toBeVisible();

    await ensureMiNegocioExpanded();

    await expect(page.getByText(agregarNegocioRegex).first()).toBeVisible();
    await expect(page.getByText(administrarNegociosRegex).first()).toBeVisible();
    await captureCheckpoint(testInfo, page, "02-mi-negocio-menu-expanded", false);
  });

  await runStep("Agregar Negocio modal", async () => {
    await ensureMiNegocioExpanded();

    const agregarNegocioItem = await firstVisible(
      [
        page.locator("aside, nav").getByText(agregarNegocioRegex),
        page.getByText(agregarNegocioRegex),
      ],
      "Agregar Negocio menu item",
    );

    await clickAndWait(agregarNegocioItem, page);

    const modalTitle = page.getByText(/^Crear Nuevo Negocio$/i).first();
    await expect(modalTitle).toBeVisible();

    let nombreNegocioInput = page.getByLabel(/Nombre del Negocio/i).first();
    if (!(await locatorIsVisible(nombreNegocioInput, 4_000))) {
      nombreNegocioInput = page.getByPlaceholder(/Nombre del Negocio/i).first();
    }

    await expect(nombreNegocioInput).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i }).first()).toBeVisible();

    await captureCheckpoint(testInfo, page, "03-agregar-negocio-modal");

    await nombreNegocioInput.click();
    await waitForUi(page);
    await nombreNegocioInput.fill("Negocio Prueba Automatización");
    await waitForUi(page);

    const cancelButton = await firstVisible(
      [
        page.getByRole("button", { name: /^Cancelar$/i }),
        page.getByText(/^Cancelar$/i),
      ],
      "Cancelar button",
    );
    await clickAndWait(cancelButton, page);
    await expect(modalTitle).not.toBeVisible({ timeout: 10_000 });
  });

  await runStep("Administrar Negocios view", async () => {
    await ensureMiNegocioExpanded();

    const administrarNegociosItem = await firstVisible(
      [
        page.locator("aside, nav").getByText(administrarNegociosRegex),
        page.getByText(administrarNegociosRegex),
      ],
      "Administrar Negocios menu item",
    );
    await clickAndWait(administrarNegociosItem, page);

    await expect(page.getByText(/^Información General$/i).first()).toBeVisible();
    await expect(page.getByText(/^Detalles de la Cuenta$/i).first()).toBeVisible();
    await expect(page.getByText(/^Tus Negocios$/i).first()).toBeVisible();
    await expect(page.getByText(/^Sección Legal$/i).first()).toBeVisible();
    await captureCheckpoint(testInfo, page, "04-administrar-negocios-page");
  });

  await runStep("Información General", async () => {
    const heading = page.getByText(/^Información General$/i).first();
    await expect(heading).toBeVisible();

    const sectionCandidate = heading.locator(
      "xpath=ancestor::*[self::section or self::article or self::div][1]",
    );
    const section = (await sectionCandidate.count()) > 0 ? sectionCandidate : page.locator("body");

    await expect(section.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(section.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();

    const sectionText = (await section.innerText()).replace(/\s+/g, " ").trim();
    const hasEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(sectionText);
    expect(hasEmail, "User email should be visible in Información General").toBeTruthy();

    const userNameSignal = sectionText
      .replace(/información general/gi, "")
      .replace(/business plan/gi, "")
      .replace(/cambiar plan/gi, "")
      .replace(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi, "")
      .trim();
    expect(userNameSignal.length, "User name-like text should be visible").toBeGreaterThan(1);
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/^Detalles de la Cuenta$/i).first()).toBeVisible();
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    const heading = page.getByText(/^Tus Negocios$/i).first();
    await expect(heading).toBeVisible();

    const sectionCandidate = heading.locator(
      "xpath=ancestor::*[self::section or self::article or self::div][1]",
    );
    const section = (await sectionCandidate.count()) > 0 ? sectionCandidate : page.locator("body");

    await expect(section.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible();
    await expect(section.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();

    const sectionText = (await section.innerText()).replace(/\s+/g, " ").trim();
    expect(sectionText.length, "Business list content should be visible").toBeGreaterThan(40);
  });

  await runStep("Términos y Condiciones", async () => {
    await openLegalDocument(
      "Términos y Condiciones",
      /Términos y Condiciones/i,
      "08-terminos-y-condiciones",
    );
  });

  await runStep("Política de Privacidad", async () => {
    await openLegalDocument(
      "Política de Privacidad",
      /Política de Privacidad/i,
      "09-politica-de-privacidad",
    );
  });

  const finalReport = {
    report,
    legalUrls,
    failures,
  };

  await testInfo.attach("final-report", {
    body: Buffer.from(JSON.stringify(finalReport, null, 2), "utf-8"),
    contentType: "application/json",
  });

  console.log("Final workflow report:");
  console.log(JSON.stringify(finalReport, null, 2));

  const failedFields = Object.entries(report)
    .filter(([, status]) => status !== "PASS")
    .map(([field]) => field);
  expect(
    failedFields,
    `One or more workflow steps failed:\n${failures.join("\n")}`,
  ).toEqual([]);
});
