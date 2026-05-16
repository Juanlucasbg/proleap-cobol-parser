#!/usr/bin/env node

const fs = require("fs/promises");
const path = require("path");
const { chromium } = require("playwright");

const TEST_NAME = "saleads_mi_negocio_full_test";
const GOOGLE_ACCOUNT_EMAIL = process.env.GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const SALEADS_LOGIN_URL = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL;
const HEADLESS = process.env.HEADLESS !== "false";

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

async function ensureDirectory(targetPath) {
  await fs.mkdir(targetPath, { recursive: true });
}

function sanitizeName(name) {
  return name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
}

async function isVisible(locator, timeout = 3000) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function findVisible(candidates, timeout = 5000) {
  for (const candidate of candidates) {
    if (await isVisible(candidate, timeout)) {
      return candidate.first();
    }
  }

  return null;
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => {});
  await page.waitForTimeout(700);
}

async function clickAndWait(page, locator) {
  await locator.waitFor({ state: "visible", timeout: 20000 });
  await locator.click();
  await waitForUi(page);
}

async function expectText(page, textOrRegex, timeout = 20000) {
  const locator = typeof textOrRegex === "string"
    ? page.getByText(textOrRegex, { exact: false }).first()
    : page.getByText(textOrRegex).first();
  await locator.waitFor({ state: "visible", timeout });
  return locator;
}

async function getSectionByHeading(page, headingRegex) {
  const section = page
    .locator("section, div")
    .filter({ has: page.getByText(headingRegex) })
    .first();
  await section.waitFor({ state: "visible", timeout: 20000 });
  return section;
}

async function writeJson(filePath, value) {
  await fs.writeFile(filePath, `${JSON.stringify(value, null, 2)}\n`, "utf8");
}

async function run() {
  if (!SALEADS_LOGIN_URL) {
    throw new Error("Set SALEADS_LOGIN_URL (or SALEADS_URL) to the current environment login page.");
  }

  const timestamp = new Date().toISOString().replace(/[.:]/g, "-");
  const outputRoot = process.env.SALEADS_TEST_OUTPUT_DIR || path.join(process.cwd(), "artifacts", TEST_NAME);
  const outputDir = path.join(outputRoot, timestamp);
  await ensureDirectory(outputDir);

  let screenshotCounter = 0;
  const screenshots = [];
  const legalUrls = {};
  const report = Object.fromEntries(REPORT_FIELDS.map((name) => [name, "FAIL"]));
  const details = {};

  const browser = await chromium.launch({ headless: HEADLESS });
  const context = await browser.newContext({ viewport: { width: 1920, height: 1080 } });
  const page = await context.newPage();

  const capture = async (targetPage, label, fullPage = false) => {
    screenshotCounter += 1;
    const fileName = `${String(screenshotCounter).padStart(2, "0")}-${sanitizeName(label)}.png`;
    const filePath = path.join(outputDir, fileName);
    await targetPage.screenshot({ path: filePath, fullPage });
    screenshots.push(filePath);
    return filePath;
  };

  const runStep = async (stepName, action) => {
    try {
      await action();
      report[stepName] = "PASS";
    } catch (error) {
      report[stepName] = "FAIL";
      details[stepName] = error instanceof Error ? error.message : String(error);
    }
  };

  const skipStep = (stepName, reason) => {
    report[stepName] = "FAIL";
    details[stepName] = reason;
  };

  const ensureMiNegocioExpanded = async () => {
    const agregar = page.getByText(/^Agregar Negocio$/i).first();
    const administrar = page.getByText(/^Administrar Negocios$/i).first();
    if (await isVisible(agregar, 2000) && await isVisible(administrar, 2000)) {
      return;
    }

    const miNegocio = await findVisible([
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i)
    ], 6000);

    if (!miNegocio) {
      throw new Error("No se encontró la opción 'Mi Negocio' en la barra lateral.");
    }

    await clickAndWait(page, miNegocio);
    await expectText(page, /^Agregar Negocio$/i);
    await expectText(page, /^Administrar Negocios$/i);
  };

  const ensureAccountPage = async () => {
    if (await isVisible(page.getByText(/Información General/i).first(), 2500)) {
      return;
    }
    await ensureMiNegocioExpanded();
    await clickAndWait(
      page,
      await findVisible([
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i)
      ], 6000)
    );
    await expectText(page, /Información General/i);
  };

  const validateLegalPage = async (linkRegex, headingRegex, reportKey) => {
    await ensureAccountPage();
    const applicationUrlBeforeClick = page.url();
    const link = await findVisible([
      page.getByRole("link", { name: linkRegex }),
      page.getByText(linkRegex)
    ], 10000);

    if (!link) {
      throw new Error(`No se encontró el enlace legal ${String(linkRegex)}.`);
    }

    const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
    await link.click();
    await waitForUi(page);
    let legalPage = await popupPromise;

    if (!legalPage) {
      legalPage = page;
    } else {
      await legalPage.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => {});
      await waitForUi(legalPage);
    }

    await expectText(legalPage, headingRegex, 30000);
    const bodyText = (await legalPage.locator("body").innerText()).trim();
    if (bodyText.length < 120) {
      throw new Error("El contenido legal visible parece incompleto.");
    }

    await capture(legalPage, reportKey, true);
    legalUrls[reportKey] = legalPage.url();

    if (legalPage !== page) {
      await legalPage.close();
      await page.bringToFront();
    } else if (page.url() !== applicationUrlBeforeClick) {
      await page.goBack({ waitUntil: "domcontentloaded", timeout: 20000 }).catch(async () => {
        await page.goto(applicationUrlBeforeClick, { waitUntil: "domcontentloaded" });
      });
      await waitForUi(page);
    }
  };

  try {
    await page.goto(SALEADS_LOGIN_URL, { waitUntil: "domcontentloaded", timeout: 60000 });
    await waitForUi(page);

    await runStep("Login", async () => {
      const loginButton = await findVisible([
        page.getByRole("button", { name: /google/i }),
        page.getByRole("link", { name: /google/i }),
        page.getByText(/sign in with google|iniciar sesión con google|continuar con google/i)
      ], 15000);

      if (!loginButton) {
        throw new Error("No se encontró botón de login con Google.");
      }

      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await loginButton.click();
      await waitForUi(page);
      const popup = await popupPromise;
      const authPage = popup || page;

      const accountOption = await findVisible([
        authPage.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
        authPage.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i"))
      ], 10000);

      if (accountOption) {
        await accountOption.click();
      }

      if (popup) {
        await popup.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
        await page.bringToFront();
      }

      await waitForUi(page);
      await expectText(page, /Negocio|Mi Negocio/i, 45000);

      const sidebar = await findVisible([
        page.locator("aside"),
        page.locator("nav").filter({ hasText: /Negocio|Mi Negocio/i })
      ], 10000);

      if (!sidebar) {
        throw new Error("No se encontró la barra lateral tras el login.");
      }

      await capture(page, "dashboard-loaded");
    });

    if (report.Login !== "PASS") {
      const reason = "Paso omitido porque el login no se completó.";
      skipStep("Mi Negocio menu", reason);
      skipStep("Agregar Negocio modal", reason);
      skipStep("Administrar Negocios view", reason);
      skipStep("Información General", reason);
      skipStep("Detalles de la Cuenta", reason);
      skipStep("Tus Negocios", reason);
      skipStep("Términos y Condiciones", reason);
      skipStep("Política de Privacidad", reason);
    } else {
      await runStep("Mi Negocio menu", async () => {
        const negocioSection = await findVisible([
          page.getByRole("button", { name: /^Negocio$/i }),
          page.getByRole("link", { name: /^Negocio$/i }),
          page.getByText(/^Negocio$/i)
        ], 12000);

        if (negocioSection) {
          await clickAndWait(page, negocioSection);
        }

        const miNegocio = await findVisible([
          page.getByRole("button", { name: /^Mi Negocio$/i }),
          page.getByRole("link", { name: /^Mi Negocio$/i }),
          page.getByText(/^Mi Negocio$/i)
        ], 12000);

        if (!miNegocio) {
          throw new Error("No se encontró el menú 'Mi Negocio'.");
        }

        await clickAndWait(page, miNegocio);
        await expectText(page, /^Agregar Negocio$/i, 10000);
        await expectText(page, /^Administrar Negocios$/i, 10000);
        await capture(page, "mi-negocio-menu-expanded");
      });

      if (report["Mi Negocio menu"] !== "PASS") {
        const reason = "Paso omitido porque 'Mi Negocio menu' falló.";
        skipStep("Agregar Negocio modal", reason);
        skipStep("Administrar Negocios view", reason);
        skipStep("Información General", reason);
        skipStep("Detalles de la Cuenta", reason);
        skipStep("Tus Negocios", reason);
        skipStep("Términos y Condiciones", reason);
        skipStep("Política de Privacidad", reason);
      } else {
        await runStep("Agregar Negocio modal", async () => {
          await ensureMiNegocioExpanded();
          const agregar = await findVisible([
            page.getByRole("link", { name: /^Agregar Negocio$/i }),
            page.getByRole("button", { name: /^Agregar Negocio$/i }),
            page.getByText(/^Agregar Negocio$/i)
          ], 10000);

          if (!agregar) {
            throw new Error("No se encontró la opción 'Agregar Negocio'.");
          }

          await clickAndWait(page, agregar);

          const modalTitle = await expectText(page, /Crear Nuevo Negocio/i, 15000);
          const modal = modalTitle.locator("xpath=ancestor-or-self::*[self::div or self::section][1]");

          await expectText(page, /Nombre del Negocio/i, 10000);
          await expectText(page, /Tienes\s*2\s*de\s*3\s*negocios/i, 10000);
          await expectText(page, /^Cancelar$/i, 10000);
          await expectText(page, /Crear Negocio/i, 10000);
          await capture(page, "agregar-negocio-modal");

          const nameField = await findVisible([
            page.getByLabel(/Nombre del Negocio/i),
            page.getByPlaceholder(/Nombre del Negocio/i),
            modal.locator("input")
          ], 5000);
          if (nameField) {
            await nameField.fill("Negocio Prueba Automatización");
          }

          const cancelButton = await findVisible([
            page.getByRole("button", { name: /^Cancelar$/i }),
            page.getByText(/^Cancelar$/i)
          ], 8000);
          if (cancelButton) {
            await clickAndWait(page, cancelButton);
          }
        });

        await runStep("Administrar Negocios view", async () => {
          await ensureMiNegocioExpanded();
          const administrar = await findVisible([
            page.getByRole("link", { name: /^Administrar Negocios$/i }),
            page.getByRole("button", { name: /^Administrar Negocios$/i }),
            page.getByText(/^Administrar Negocios$/i)
          ], 10000);

          if (!administrar) {
            throw new Error("No se encontró la opción 'Administrar Negocios'.");
          }

          await clickAndWait(page, administrar);
          await expectText(page, /Información General/i, 20000);
          await expectText(page, /Detalles de la Cuenta/i, 20000);
          await expectText(page, /Tus Negocios/i, 20000);
          await expectText(page, /Sección Legal/i, 20000);
          await capture(page, "administrar-negocios-page", true);
        });

        if (report["Administrar Negocios view"] !== "PASS") {
          const reason = "Paso omitido porque 'Administrar Negocios view' falló.";
          skipStep("Información General", reason);
          skipStep("Detalles de la Cuenta", reason);
          skipStep("Tus Negocios", reason);
          skipStep("Términos y Condiciones", reason);
          skipStep("Política de Privacidad", reason);
        } else {
          await runStep("Información General", async () => {
            await ensureAccountPage();
            const section = await getSectionByHeading(page, /Información General/i);
            const text = await section.innerText();

            if (!/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/.test(text)) {
              throw new Error("No se detectó email visible en 'Información General'.");
            }

            const lines = text
              .split("\n")
              .map((line) => line.trim())
              .filter(Boolean);
            const maybeName = lines.find((line) => {
              const normalized = line.toLowerCase();
              return !normalized.includes("información general")
                && !normalized.includes("business plan")
                && !normalized.includes("cambiar plan")
                && !line.includes("@")
                && line.length >= 3;
            });
            if (!maybeName) {
              throw new Error("No se pudo validar un nombre de usuario visible en 'Información General'.");
            }

            await expectText(page, /BUSINESS PLAN/i, 10000);
            await expectText(page, /Cambiar Plan/i, 10000);
          });

          await runStep("Detalles de la Cuenta", async () => {
            await ensureAccountPage();
            await expectText(page, /Cuenta creada/i, 10000);
            await expectText(page, /Estado activo/i, 10000);
            await expectText(page, /Idioma seleccionado/i, 10000);
          });

          await runStep("Tus Negocios", async () => {
            await ensureAccountPage();
            const section = await getSectionByHeading(page, /Tus Negocios/i);
            const sectionText = await section.innerText();
            await expectText(page, /^Agregar Negocio$/i, 10000);
            await expectText(page, /Tienes\s*2\s*de\s*3\s*negocios/i, 10000);

            const hasBusinessItem = sectionText
              .split("\n")
              .map((line) => line.trim())
              .some((line) => line.length > 2 && !/tus negocios|agregar negocio|tienes\s*2\s*de\s*3/i.test(line.toLowerCase()));
            if (!hasBusinessItem) {
              throw new Error("No se detectaron elementos visibles en la lista de negocios.");
            }
          });

          await runStep("Términos y Condiciones", async () => {
            await validateLegalPage(/Términos y Condiciones/i, /Términos y Condiciones/i, "Términos y Condiciones");
          });

          await runStep("Política de Privacidad", async () => {
            await validateLegalPage(/Política de Privacidad/i, /Política de Privacidad/i, "Política de Privacidad");
          });
        }
      }
    }
  } finally {
    const output = {
      testName: TEST_NAME,
      executedAt: new Date().toISOString(),
      loginUrl: SALEADS_LOGIN_URL,
      googleAccount: GOOGLE_ACCOUNT_EMAIL,
      screenshots,
      legalUrls,
      report,
      details
    };
    await writeJson(path.join(outputDir, "final-report.json"), output);
    console.log(JSON.stringify(output, null, 2));
    await browser.close();
  }

  const failed = Object.values(report).some((status) => status !== "PASS");
  process.exitCode = failed ? 1 : 0;
}

run().catch((error) => {
  console.error(error);
  process.exit(1);
});
