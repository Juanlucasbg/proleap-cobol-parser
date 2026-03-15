const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

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

function ensureDir(dirPath) {
  fs.mkdirSync(dirPath, { recursive: true });
}

function escapeRegex(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function flattenWhitespace(value) {
  return value.replace(/\s+/g, " ").trim();
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {});
  await page.waitForTimeout(700);
}

async function firstVisible(candidates, timeout = 8000) {
  for (const candidate of candidates) {
    const locator = candidate.first();
    try {
      await locator.waitFor({ state: "visible", timeout });
      return locator;
    } catch {
      // try next candidate
    }
  }
  return null;
}

async function findByText(page, text) {
  const exactPattern = new RegExp(`^\\s*${escapeRegex(text)}\\s*$`, "i");
  return firstVisible([
    page.getByRole("button", { name: exactPattern }),
    page.getByRole("link", { name: exactPattern }),
    page.getByRole("menuitem", { name: exactPattern }),
    page.getByRole("tab", { name: exactPattern }),
    page.getByText(exactPattern),
    page.getByText(text, { exact: false })
  ]);
}

async function findByRegex(page, regex) {
  return firstVisible([
    page.getByRole("button", { name: regex }),
    page.getByRole("link", { name: regex }),
    page.getByRole("menuitem", { name: regex }),
    page.getByRole("tab", { name: regex }),
    page.getByText(regex)
  ]);
}

async function screenshot(page, folder, name, fullPage = false) {
  const filePath = path.join(folder, `${name}.png`);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function openMiNegocioMenu(page) {
  const negocioOption = await findByRegex(page, /negocio/i);
  if (negocioOption) {
    await negocioOption.click();
    await waitForUi(page);
  }

  const miNegocioOption = await findByRegex(page, /mi negocio/i);
  if (!miNegocioOption) {
    throw new Error("No se encontró la opción 'Mi Negocio' en la barra lateral.");
  }

  await miNegocioOption.click();
  await waitForUi(page);
}

async function findSectionByHeading(page, headingRegex) {
  const heading = await firstVisible([
    page.getByRole("heading", { name: headingRegex }),
    page.getByText(headingRegex)
  ]);
  if (!heading) {
    throw new Error(`No se encontró el encabezado de sección: ${headingRegex}`);
  }

  const section = heading.locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
  return { heading, section };
}

async function validateLegalLink({
  page,
  linkRegex,
  headingRegex,
  evidenceFolder,
  screenshotName
}) {
  const legalLink = await findByRegex(page, linkRegex);
  if (!legalLink) {
    throw new Error(`No se encontró el enlace legal: ${linkRegex}`);
  }

  const appPage = page;
  const previousUrl = appPage.url();

  const popupPromise = appPage.waitForEvent("popup", { timeout: 10000 }).catch(() => null);
  const navigationPromise = appPage
    .waitForNavigation({ waitUntil: "domcontentloaded", timeout: 15000 })
    .catch(() => null);

  await legalLink.click();

  const popup = await popupPromise;
  const legalPage = popup || appPage;

  if (!popup) {
    await navigationPromise;
  }

  await waitForUi(legalPage);

  const heading = await firstVisible([
    legalPage.getByRole("heading", { name: headingRegex }),
    legalPage.getByText(headingRegex)
  ]);
  if (!heading) {
    throw new Error(`No se encontró el encabezado legal esperado: ${headingRegex}`);
  }

  const bodyText = flattenWhitespace(await legalPage.locator("body").innerText());
  if (bodyText.length < 120) {
    throw new Error("No se detectó suficiente contenido legal visible en la página.");
  }

  const screenshotPath = await screenshot(legalPage, evidenceFolder, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await legalPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else if (appPage.url() !== previousUrl) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(appPage);
  }

  return { finalUrl, screenshotPath };
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const runId = new Date().toISOString().replace(/[:.]/g, "-");
  const evidenceFolder = path.join(
    process.cwd(),
    "artifacts",
    "saleads_mi_negocio_full_test",
    runId
  );
  ensureDir(evidenceFolder);

  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const errors = {};
  const evidence = {};

  async function runValidation(field, fn) {
    try {
      await fn();
      report[field] = "PASS";
    } catch (error) {
      report[field] = "FAIL";
      errors[field] = error instanceof Error ? error.message : String(error);
    }
  }

  function markFailedDueToDependency(fields, reason) {
    for (const field of fields) {
      report[field] = "FAIL";
      if (!errors[field]) {
        errors[field] = reason;
      }
    }
  }

  await runValidation("Login", async () => {
    const startUrl =
      process.env.SALEADS_START_URL ||
      process.env.BASE_URL ||
      process.env.PLAYWRIGHT_TEST_BASE_URL;

    if (startUrl) {
      await page.goto(startUrl, { waitUntil: "domcontentloaded" });
    }

    await waitForUi(page);

    if (page.url().startsWith("about:blank")) {
      throw new Error(
        "No hay URL inicial cargada. Define SALEADS_START_URL o BASE_URL para ejecutar el login."
      );
    }

    const loginButton = await findByRegex(
      page,
      /sign in with google|continue with google|iniciar sesi[oó]n con google|google/i
    );
    if (!loginButton) {
      throw new Error("No se encontró el botón de login con Google.");
    }

    const popupPromise = page.waitForEvent("popup", { timeout: 10000 }).catch(() => null);
    await loginButton.click();

    const googlePage = await popupPromise;
    if (googlePage) {
      await waitForUi(googlePage);
      const accountSelector = await firstVisible(
        [
          googlePage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }),
          googlePage.getByRole("button", {
            name: new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i")
          }),
          googlePage.getByRole("link", {
            name: new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i")
          })
        ],
        10000
      );

      if (accountSelector) {
        await accountSelector.click();
      }

      await Promise.race([
        googlePage.waitForEvent("close", { timeout: 20000 }),
        page.waitForLoadState("domcontentloaded", { timeout: 20000 })
      ]).catch(() => {});
    } else {
      const inlineAccount = await firstVisible(
        [page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false })],
        5000
      );
      if (inlineAccount) {
        await inlineAccount.click();
      }
    }

    await waitForUi(page);

    const sidebar = await firstVisible([
      page.locator("aside"),
      page.getByRole("navigation"),
      page.locator("nav")
    ]);
    if (!sidebar) {
      throw new Error("No se detectó la barra lateral luego del login.");
    }

    const appText = await firstVisible([
      page.getByText(/mi negocio|negocio|dashboard|panel/i),
      page.locator("main")
    ]);
    if (!appText) {
      throw new Error("No se detectó la interfaz principal luego del login.");
    }

    evidence.dashboardScreenshot = await screenshot(
      page,
      evidenceFolder,
      "01_dashboard_loaded",
      true
    );
  });

  if (report["Login"] === "FAIL") {
    markFailedDueToDependency(
      REPORT_FIELDS.filter((field) => field !== "Login"),
      "No ejecutado por dependencia: falló el paso de Login."
    );
  } else {
    await runValidation("Mi Negocio menu", async () => {
      await openMiNegocioMenu(page);

      await expect(page.getByText(/agregar negocio/i)).toBeVisible();
      await expect(page.getByText(/administrar negocios/i)).toBeVisible();

      evidence.miNegocioMenuScreenshot = await screenshot(
        page,
        evidenceFolder,
        "02_mi_negocio_menu_expanded",
        true
      );
    });

    if (report["Mi Negocio menu"] === "FAIL") {
      markFailedDueToDependency(
        REPORT_FIELDS.filter((field) => !["Login", "Mi Negocio menu"].includes(field)),
        "No ejecutado por dependencia: falló la expansión del menú Mi Negocio."
      );
    } else {
      await runValidation("Agregar Negocio modal", async () => {
        const agregarNegocioOption = await findByText(page, "Agregar Negocio");
        if (!agregarNegocioOption) {
          throw new Error("No se encontró la opción 'Agregar Negocio'.");
        }
        await agregarNegocioOption.click();
        await waitForUi(page);

        await expect(page.getByText(/crear nuevo negocio/i)).toBeVisible();
        const nombreInput = await firstVisible([
          page.getByLabel(/nombre del negocio/i),
          page.getByPlaceholder(/nombre del negocio/i),
          page.locator("input[name*='nombre' i], input[id*='nombre' i]")
        ]);
        if (!nombreInput) {
          throw new Error("No se encontró el campo 'Nombre del Negocio'.");
        }
        await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
        await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
        await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();

        evidence.agregarModalScreenshot = await screenshot(
          page,
          evidenceFolder,
          "03_agregar_negocio_modal",
          true
        );

        await nombreInput.click();
        await nombreInput.fill("Negocio Prueba Automatización");
        await page.getByRole("button", { name: /cancelar/i }).click();
        await waitForUi(page);
      });

      await runValidation("Administrar Negocios view", async () => {
        await openMiNegocioMenu(page);

        const administrarNegocios = await findByText(page, "Administrar Negocios");
        if (!administrarNegocios) {
          throw new Error("No se encontró la opción 'Administrar Negocios'.");
        }
        await administrarNegocios.click();
        await waitForUi(page);

        await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible();
        await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
        await expect(page.getByText(/tus negocios/i)).toBeVisible();
        await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible();

        evidence.accountPageScreenshot = await screenshot(
          page,
          evidenceFolder,
          "04_administrar_negocios_view",
          true
        );
      });

      if (report["Administrar Negocios view"] === "FAIL") {
        markFailedDueToDependency(
          [
            "Información General",
            "Detalles de la Cuenta",
            "Tus Negocios",
            "Términos y Condiciones",
            "Política de Privacidad"
          ],
          "No ejecutado por dependencia: falló la vista de Administrar Negocios."
        );
      } else {
        await runValidation("Información General", async () => {
          const { section } = await findSectionByHeading(page, /informaci[oó]n general/i);
          const sectionText = flattenWhitespace(await section.innerText());

          const hasEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(sectionText);
          if (!hasEmail) {
            throw new Error("No se encontró email de usuario en 'Información General'.");
          }

          const lines = (await section.innerText())
            .split("\n")
            .map((line) => line.trim())
            .filter(Boolean);
          const hasProbableUserName = lines.some((line) => {
            return (
              /^[A-Za-zÀ-ÿ' -]{3,}$/.test(line) &&
              !/informaci[oó]n|business plan|cambiar plan|correo|email|cuenta/i.test(line)
            );
          });
          if (!hasProbableUserName) {
            throw new Error("No se detectó un nombre de usuario visible en 'Información General'.");
          }

          await expect(section.getByText(/business plan/i)).toBeVisible();
          await expect(section.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
        });

        await runValidation("Detalles de la Cuenta", async () => {
          const { section } = await findSectionByHeading(page, /detalles de la cuenta/i);
          await expect(section.getByText(/cuenta creada/i)).toBeVisible();
          await expect(section.getByText(/estado activo/i)).toBeVisible();
          await expect(section.getByText(/idioma seleccionado/i)).toBeVisible();
        });

        await runValidation("Tus Negocios", async () => {
          const { section } = await findSectionByHeading(page, /tus negocios/i);
          await expect(section.getByRole("button", { name: /agregar negocio/i })).toBeVisible();
          await expect(section.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();

          const listItems = await section.locator("li, tr, article, [role='listitem']").count();
          const sectionText = flattenWhitespace(await section.innerText());
          if (listItems < 1 && !/negocio/i.test(sectionText)) {
            throw new Error("No se detectó una lista de negocios visible.");
          }
        });

        await runValidation("Términos y Condiciones", async () => {
          const result = await validateLegalLink({
            page,
            linkRegex: /t[eé]rminos y condiciones|terminos y condiciones/i,
            headingRegex: /t[eé]rminos y condiciones|terminos y condiciones/i,
            evidenceFolder,
            screenshotName: "05_terminos_y_condiciones"
          });
          evidence.terminosScreenshot = result.screenshotPath;
          evidence.terminosFinalUrl = result.finalUrl;
        });

        await runValidation("Política de Privacidad", async () => {
          const result = await validateLegalLink({
            page,
            linkRegex: /pol[ií]tica de privacidad|politica de privacidad/i,
            headingRegex: /pol[ií]tica de privacidad|politica de privacidad/i,
            evidenceFolder,
            screenshotName: "06_politica_de_privacidad"
          });
          evidence.politicaScreenshot = result.screenshotPath;
          evidence.politicaFinalUrl = result.finalUrl;
        });
      }
    }
  }

  const reportPayload = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    environment: {
      startUrl:
        process.env.SALEADS_START_URL ||
        process.env.BASE_URL ||
        process.env.PLAYWRIGHT_TEST_BASE_URL ||
        page.url()
    },
    results: report,
    errors,
    evidence
  };

  const reportPath = path.join(evidenceFolder, "final_report.json");
  fs.writeFileSync(reportPath, JSON.stringify(reportPayload, null, 2), "utf-8");

  await testInfo.attach("final_report.json", {
    path: reportPath,
    contentType: "application/json"
  });

  const failedFields = Object.entries(report)
    .filter(([, status]) => status === "FAIL")
    .map(([field]) => field);

  expect(
    failedFields,
    `Validaciones fallidas: ${failedFields.join(", ")}. Revisa ${reportPath}`
  ).toEqual([]);
});
