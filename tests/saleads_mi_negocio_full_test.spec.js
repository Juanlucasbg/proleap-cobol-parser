const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
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

const EMAIL_REGEX = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;

function normalizeText(value) {
  return value.replace(/\s+/g, " ").trim();
}

function hasLikelyNameLine(sectionText) {
  return sectionText
    .split("\n")
    .map((line) => line.trim())
    .filter(Boolean)
    .some((line) => {
      if (
        /informaci[oó]n general|business plan|cambiar plan|cuenta creada|estado activo|idioma seleccionado|@/i.test(
          line,
        )
      ) {
        return false;
      }

      return /^[\p{L}][\p{L}\s.'-]{2,}$/u.test(line);
    });
}

async function waitForUiToLoad(page) {
  await page.waitForTimeout(500);
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function firstVisible(candidates) {
  for (const candidate of candidates) {
    const locator = candidate.first();
    const visible = await locator.isVisible({ timeout: 2500 }).catch(() => false);
    if (visible) {
      return locator;
    }
  }
  return null;
}

async function clickAndWait(locator, page) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiToLoad(page);
}

async function captureCheckpoint(page, testInfo, fileName, fullPage = false) {
  const checkpointPath = testInfo.outputPath(fileName);
  await page.screenshot({ path: checkpointPath, fullPage });
  return path.relative(process.cwd(), checkpointPath);
}

async function findSectionByHeading(page, headingRegex) {
  const heading = page.getByText(headingRegex).first();
  await expect(heading).toBeVisible();

  const sectionBySemanticParent = heading.locator("xpath=ancestor::section[1]");
  if ((await sectionBySemanticParent.count()) > 0) {
    return sectionBySemanticParent;
  }

  const sectionByContainerParent = heading.locator(
    "xpath=ancestor::*[self::div or self::article][1]",
  );
  if ((await sectionByContainerParent.count()) > 0) {
    return sectionByContainerParent;
  }

  return page.locator("body");
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const detailedReport = {};
  const failureMessages = [];
  let startupError = null;

  async function runStep(reportKey, work) {
    try {
      if (startupError) {
        throw startupError;
      }

      const data = (await work()) || {};
      detailedReport[reportKey] = { status: "PASS", ...data };
    } catch (error) {
      detailedReport[reportKey] = {
        status: "FAIL",
        error: error instanceof Error ? error.message : String(error),
      };
      failureMessages.push(`${reportKey}: ${detailedReport[reportKey].error}`);
    }
  }

  await waitForUiToLoad(page);
  if (page.url() === "about:blank") {
    const loginUrl =
      process.env.SALEADS_URL ||
      process.env.SALEADS_LOGIN_URL ||
      process.env.BASE_URL ||
      process.env.APP_URL ||
      process.env.TARGET_URL;
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUiToLoad(page);
    } else {
      startupError = new Error(
        "Browser is on about:blank and no start URL was provided. Set SALEADS_URL (or SALEADS_LOGIN_URL/BASE_URL/APP_URL/TARGET_URL) or start from the SaleADS login page before running.",
      );
    }
  }

  await runStep("Login", async () => {
    const sidebarAlreadyVisible = await page
      .getByText(/Negocio/i)
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    if (!sidebarAlreadyVisible) {
      const loginButton = await firstVisible([
        page.getByRole("button", {
          name: /sign in with google|iniciar sesi[oó]n con google|google/i,
        }),
        page.getByRole("link", {
          name: /sign in with google|iniciar sesi[oó]n con google|google/i,
        }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|google/i),
      ]);

      expect(loginButton).not.toBeNull();

      const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
      await clickAndWait(loginButton, page);
      const popup = await popupPromise;
      const authPage = popup || page;

      if (popup) {
        await popup.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
      }

      const accountChooser = authPage.getByText(GOOGLE_ACCOUNT, { exact: false }).first();
      const accountVisible = await accountChooser.isVisible({ timeout: 10000 }).catch(() => false);
      if (accountVisible) {
        await accountChooser.click();
        await waitForUiToLoad(authPage);
      }

      if (popup) {
        await popup.waitForEvent("close", { timeout: 45000 }).catch(() => {});
        await page.bringToFront();
      }
    }

    await expect(page.getByText(/Negocio/i).first()).toBeVisible({ timeout: 60000 });
    const sidebar = await firstVisible([page.locator("aside"), page.getByRole("navigation")]);
    if (sidebar) {
      await expect(sidebar).toBeVisible();
    }

    const screenshot = await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png", true);
    return { screenshot };
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = await firstVisible([
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i),
    ]);

    if (negocioSection) {
      await clickAndWait(negocioSection, page);
    }

    const miNegocio = await firstVisible([
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i),
    ]);
    expect(miNegocio).not.toBeNull();

    await clickAndWait(miNegocio, page);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();

    const screenshot = await captureCheckpoint(page, testInfo, "02-mi-negocio-expanded.png");
    return { screenshot };
  });

  await runStep("Agregar Negocio modal", async () => {
    const addBusiness = await firstVisible([
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);
    expect(addBusiness).not.toBeNull();

    await clickAndWait(addBusiness, page);

    await expect(page.getByText(/^Crear Nuevo Negocio$/i).first()).toBeVisible();
    await expect(page.getByLabel(/Nombre del Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i }).first()).toBeVisible();

    const screenshot = await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");

    const businessNameField = page.getByLabel(/Nombre del Negocio/i).first();
    await businessNameField.click();
    await businessNameField.fill("Negocio Prueba Automatización");
    await clickAndWait(page.getByRole("button", { name: /^Cancelar$/i }).first(), page);

    return { screenshot };
  });

  await runStep("Administrar Negocios view", async () => {
    const miNegocio = await firstVisible([
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i),
    ]);
    if (miNegocio) {
      await clickAndWait(miNegocio, page);
    }

    const manageBusinesses = await firstVisible([
      page.getByRole("button", { name: /^Administrar Negocios$/i }),
      page.getByRole("link", { name: /^Administrar Negocios$/i }),
      page.getByText(/^Administrar Negocios$/i),
    ]);
    expect(manageBusinesses).not.toBeNull();
    await clickAndWait(manageBusinesses, page);

    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();

    const screenshot = await captureCheckpoint(page, testInfo, "04-administrar-negocios.png", true);
    return { screenshot };
  });

  await runStep("Información General", async () => {
    const section = await findSectionByHeading(page, /Informaci[oó]n General/i);
    const sectionText = await section.innerText();
    const normalized = normalizeText(sectionText);

    expect(EMAIL_REGEX.test(normalized)).toBeTruthy();
    expect(hasLikelyNameLine(sectionText)).toBeTruthy();
    await expect(section.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(section.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    const section = await findSectionByHeading(page, /Detalles de la Cuenta/i);
    await expect(section.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(section.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(section.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    const section = await findSectionByHeading(page, /Tus Negocios/i);
    await expect(section.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    await expect(section.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();

    const sectionText = normalizeText(await section.innerText());
    const visibleBusinessCount = await section.locator("li, [role='row'], .card, [data-testid*='business']").count();
    expect(visibleBusinessCount > 0 || /negocio/i.test(sectionText)).toBeTruthy();
  });

  async function validateLegalDocument({
    reportKey,
    linkRegex,
    headingRegex,
    screenshotFile,
  }) {
    await runStep(reportKey, async () => {
      const legalLink = await firstVisible([
        page.getByRole("link", { name: linkRegex }),
        page.getByRole("button", { name: linkRegex }),
        page.getByText(linkRegex),
      ]);
      expect(legalLink).not.toBeNull();

      const originPageUrl = page.url();
      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await clickAndWait(legalLink, page);
      const popup = await popupPromise;

      const legalPage = popup || page;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => {});
        await waitForUiToLoad(popup);
      } else {
        await waitForUiToLoad(page);
      }

      const heading = await firstVisible([
        legalPage.getByRole("heading", { name: headingRegex }),
        legalPage.getByText(headingRegex),
      ]);
      expect(heading).not.toBeNull();
      await expect(heading).toBeVisible();

      const legalContent = normalizeText(await legalPage.locator("body").innerText());
      expect(legalContent.length).toBeGreaterThan(120);

      const screenshot = await captureCheckpoint(legalPage, testInfo, screenshotFile, true);
      const finalUrl = legalPage.url();

      if (popup) {
        await popup.close();
        await page.bringToFront();
        await waitForUiToLoad(page);
      } else if (page.url() !== originPageUrl) {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await waitForUiToLoad(page);
      }

      return { screenshot, finalUrl };
    });
  }

  await validateLegalDocument({
    reportKey: "Términos y Condiciones",
    linkRegex: /T[eé]rminos y Condiciones/i,
    headingRegex: /T[eé]rminos y Condiciones/i,
    screenshotFile: "05-terminos-y-condiciones.png",
  });

  await validateLegalDocument({
    reportKey: "Política de Privacidad",
    linkRegex: /Pol[ií]tica de Privacidad/i,
    headingRegex: /Pol[ií]tica de Privacidad/i,
    screenshotFile: "06-politica-de-privacidad.png",
  });

  const finalReport = {};
  for (const field of REPORT_FIELDS) {
    finalReport[field] = detailedReport[field]?.status || "FAIL";
  }

  const reportPayload = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    results: finalReport,
    details: detailedReport,
  };

  const reportPath = testInfo.outputPath("saleads_mi_negocio_final_report.json");
  fs.writeFileSync(reportPath, JSON.stringify(reportPayload, null, 2), "utf-8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  console.log("saleads_mi_negocio_full_test report:");
  console.log(JSON.stringify(reportPayload, null, 2));

  expect(
    failureMessages,
    `The following workflow validations failed:\n${failureMessages.join("\n")}`,
  ).toEqual([]);
});
