const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const STEP_KEYS = [
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

function timestampForPath() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

function normalizePathSegment(value) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .toLowerCase();
}

function initializeReport() {
  return STEP_KEYS.reduce((accumulator, key) => {
    accumulator[key] = "FAIL";
    return accumulator;
  }, {});
}

function createArtifactsDir() {
  const folderFromEnv = process.env.SALEADS_SCREENSHOT_DIR;
  const targetDir = folderFromEnv
    ? path.resolve(folderFromEnv)
    : path.join(__dirname, "artifacts", `saleads-mi-negocio-${timestampForPath()}`);

  fs.mkdirSync(targetDir, { recursive: true });
  return targetDir;
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(700);
}

async function clickFirstVisible(locators, description) {
  for (const locator of locators) {
    const firstMatch = locator.first();
    const isVisible = await firstMatch.isVisible().catch(() => false);
    if (isVisible) {
      await firstMatch.click();
      return;
    }
  }
  throw new Error(`No visible element found for: ${description}`);
}

async function screenshot(page, artifactDir, name, fullPage = false) {
  const fileName = `${name}.png`;
  await page.screenshot({
    path: path.join(artifactDir, fileName),
    fullPage,
  });
}

async function markStep(stepKey, fn, stepErrors, report) {
  try {
    await fn();
    report[stepKey] = "PASS";
  } catch (error) {
    stepErrors.push(`${stepKey}: ${error.message}`);
    report[stepKey] = "FAIL";
  }
}

async function clickWithOptionalPopup(page, clickAction) {
  const popupPromise = page.context().waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await clickAction();
  const popup = await popupPromise;
  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await popup.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
  } else {
    await waitForUi(page);
  }
  return popup;
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  test.setTimeout(240000);

  const artifactDir = createArtifactsDir();
  const report = initializeReport();
  const stepErrors = [];
  const evidence = {
    terminosUrl: "",
    politicaUrl: "",
  };

  const googleAccountEmail = process.env.SALEADS_GOOGLE_ACCOUNT_EMAIL || DEFAULT_GOOGLE_EMAIL;
  const loginUrl = process.env.SALEADS_LOGIN_URL;

  if (!loginUrl) {
    throw new Error(
      "SALEADS_LOGIN_URL is required. Use the current environment login URL instead of hardcoding a domain."
    );
  }

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  await markStep(
    "Login",
    async () => {
      const googleButtonCandidates = [
        page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i }),
        page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
      ];

      const authPopup = await clickWithOptionalPopup(page, async () => {
        await clickFirstVisible(googleButtonCandidates, "Google login button");
      });

      if (authPopup) {
        const emailOption = authPopup.getByText(new RegExp(googleAccountEmail.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i"));
        const selectAccountVisible = await emailOption.first().isVisible().catch(() => false);
        if (selectAccountVisible) {
          await emailOption.first().click();
          await waitForUi(authPopup);
        }
      } else {
        const accountFromMainPage = page.getByText(new RegExp(googleAccountEmail.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i"));
        if (await accountFromMainPage.first().isVisible().catch(() => false)) {
          await accountFromMainPage.first().click();
          await waitForUi(page);
        }
      }

      await waitForUi(page);
      await expect(page.getByText(/negocio|dashboard|inicio/i).first()).toBeVisible({ timeout: 20000 });
      await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 20000 });
      await screenshot(page, artifactDir, "01_dashboard_loaded");
    },
    stepErrors,
    report
  );

  await markStep(
    "Mi Negocio menu",
    async () => {
      await clickFirstVisible(
        [
          page.getByRole("button", { name: /mi negocio/i }),
          page.getByRole("link", { name: /mi negocio/i }),
          page.getByText(/mi negocio/i),
        ],
        "Mi Negocio option"
      );
      await waitForUi(page);

      await expect(page.getByText(/agregar negocio/i)).toBeVisible();
      await expect(page.getByText(/administrar negocios/i)).toBeVisible();
      await screenshot(page, artifactDir, "02_mi_negocio_menu_expanded");
    },
    stepErrors,
    report
  );

  await markStep(
    "Agregar Negocio modal",
    async () => {
      await clickFirstVisible(
        [
          page.getByRole("button", { name: /agregar negocio/i }),
          page.getByRole("link", { name: /agregar negocio/i }),
          page.getByText(/agregar negocio/i),
        ],
        "Agregar Negocio"
      );

      const modal = page.getByRole("dialog");
      await expect(modal).toBeVisible({ timeout: 10000 });
      await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();
      await expect(modal.getByLabel(/nombre del negocio/i)).toBeVisible();
      await expect(modal.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
      await expect(modal.getByRole("button", { name: /cancelar/i })).toBeVisible();
      await expect(modal.getByRole("button", { name: /crear negocio/i })).toBeVisible();
      await screenshot(page, artifactDir, "03_agregar_negocio_modal");

      await modal.getByLabel(/nombre del negocio/i).fill("Negocio Prueba Automatizacion");
      await modal.getByRole("button", { name: /cancelar/i }).click();
      await waitForUi(page);
      await expect(modal).not.toBeVisible();
    },
    stepErrors,
    report
  );

  await markStep(
    "Administrar Negocios view",
    async () => {
      const administrarVisible = await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false);
      if (!administrarVisible) {
        await clickFirstVisible(
          [
            page.getByRole("button", { name: /mi negocio/i }),
            page.getByRole("link", { name: /mi negocio/i }),
            page.getByText(/mi negocio/i),
          ],
          "Mi Negocio option re-expand"
        );
        await waitForUi(page);
      }

      await clickFirstVisible(
        [
          page.getByRole("button", { name: /administrar negocios/i }),
          page.getByRole("link", { name: /administrar negocios/i }),
          page.getByText(/administrar negocios/i),
        ],
        "Administrar Negocios"
      );
      await waitForUi(page);

      await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible();
      await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
      await expect(page.getByText(/tus negocios/i)).toBeVisible();
      await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible();
      await screenshot(page, artifactDir, "04_administrar_negocios", true);
    },
    stepErrors,
    report
  );

  await markStep(
    "Información General",
    async () => {
      await expect(page.getByText(/@/).first()).toBeVisible();
      await expect(page.getByText(/business plan/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
      await screenshot(page, artifactDir, "05_informacion_general");
    },
    stepErrors,
    report
  );

  await markStep(
    "Detalles de la Cuenta",
    async () => {
      await expect(page.getByText(/cuenta creada/i)).toBeVisible();
      await expect(page.getByText(/estado activo/i)).toBeVisible();
      await expect(page.getByText(/idioma seleccionado/i)).toBeVisible();
      await screenshot(page, artifactDir, "06_detalles_cuenta");
    },
    stepErrors,
    report
  );

  await markStep(
    "Tus Negocios",
    async () => {
      await expect(page.getByText(/tus negocios/i)).toBeVisible();
      await expect(page.getByText(/agregar negocio/i)).toBeVisible();
      await expect(page.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
      await screenshot(page, artifactDir, "07_tus_negocios");
    },
    stepErrors,
    report
  );

  await markStep(
    "Términos y Condiciones",
    async () => {
      const popup = await clickWithOptionalPopup(page, async () => {
        await clickFirstVisible(
          [
            page.getByRole("link", { name: /t[eé]rminos y condiciones/i }),
            page.getByText(/t[eé]rminos y condiciones/i),
          ],
          "Términos y Condiciones"
        );
      });

      const legalPage = popup || page;
      await expect(legalPage.getByText(/t[eé]rminos y condiciones/i).first()).toBeVisible({ timeout: 15000 });
      await expect(legalPage.locator("p, li").first()).toBeVisible();
      await screenshot(legalPage, artifactDir, "08_terminos_y_condiciones", true);
      evidence.terminosUrl = legalPage.url();

      if (popup) {
        await popup.close();
        await page.bringToFront();
      } else {
        await page.goBack().catch(() => {});
        await waitForUi(page);
      }
    },
    stepErrors,
    report
  );

  await markStep(
    "Política de Privacidad",
    async () => {
      const popup = await clickWithOptionalPopup(page, async () => {
        await clickFirstVisible(
          [
            page.getByRole("link", { name: /pol[ií]tica de privacidad/i }),
            page.getByText(/pol[ií]tica de privacidad/i),
          ],
          "Política de Privacidad"
        );
      });

      const legalPage = popup || page;
      await expect(legalPage.getByText(/pol[ií]tica de privacidad/i).first()).toBeVisible({ timeout: 15000 });
      await expect(legalPage.locator("p, li").first()).toBeVisible();
      await screenshot(legalPage, artifactDir, "09_politica_privacidad", true);
      evidence.politicaUrl = legalPage.url();

      if (popup) {
        await popup.close();
        await page.bringToFront();
      } else {
        await page.goBack().catch(() => {});
        await waitForUi(page);
      }
    },
    stepErrors,
    report
  );

  const reportLines = [
    "SALEADS MI NEGOCIO - FINAL REPORT",
    ...STEP_KEYS.map((key) => `${key}: ${report[key]}`),
    `Términos y Condiciones URL: ${evidence.terminosUrl || "NOT CAPTURED"}`,
    `Política de Privacidad URL: ${evidence.politicaUrl || "NOT CAPTURED"}`,
    `Screenshots directory: ${artifactDir}`,
  ];

  const reportFilePath = path.join(artifactDir, "final-report.txt");
  fs.writeFileSync(reportFilePath, `${reportLines.join("\n")}\n`, "utf8");
  console.log(reportLines.join("\n"));

  if (stepErrors.length > 0) {
    throw new Error(`One or more workflow validations failed:\n- ${stepErrors.join("\n- ")}`);
  }
});
