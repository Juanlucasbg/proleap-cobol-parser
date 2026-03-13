const fs = require("node:fs/promises");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
const REPORT_KEYS = [
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

function createInitialReport() {
  const report = {};
  for (const key of REPORT_KEYS) {
    report[key] = "NOT RUN";
  }
  return report;
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 8_000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function firstVisible(candidates, timeoutMs = 12_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    for (const locator of candidates) {
      const visible = await locator.first().isVisible().catch(() => false);
      if (visible) {
        return locator.first();
      }
    }
    await candidates[0].page().waitForTimeout(300);
  }
  return null;
}

async function expectAnyVisible(page, candidates, timeoutMs = 20_000) {
  const candidate = await firstVisible(candidates, timeoutMs);
  if (!candidate) {
    throw new Error("None of the expected visible-text elements were found.");
  }
  return candidate;
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function checkpoint(page, testInfo, name, fullPage = false) {
  const dir = path.join(testInfo.outputDir, "checkpoints");
  await fs.mkdir(dir, { recursive: true });
  const filePath = path.join(dir, `${name}.png`);
  await page.screenshot({ path: filePath, fullPage });
  await testInfo.attach(`checkpoint-${name}`, {
    path: filePath,
    contentType: "image/png",
  });
}

async function ensureLoginPage(page) {
  if (process.env.SALEADS_LOGIN_URL) {
    await page.goto(process.env.SALEADS_LOGIN_URL, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
    return;
  }

  if (process.env.SALEADS_BASE_URL || process.env.BASE_URL) {
    await page.goto("/", { waitUntil: "domcontentloaded" });
    await waitForUi(page);
    return;
  }

  if (page.url() === "about:blank") {
    throw new Error(
      "No login URL configured. Set SALEADS_LOGIN_URL or SALEADS_BASE_URL, or run against an existing logged-in page context.",
    );
  }
}

async function openAndValidateLegalLink({
  appPage,
  linkRegex,
  headingRegex,
  screenshotName,
  testInfo,
}) {
  const context = appPage.context();
  const initialUrl = appPage.url();

  const link = await expectAnyVisible(appPage, [
    appPage.getByRole("link", { name: linkRegex }),
    appPage.getByText(linkRegex),
  ]);

  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await link.click();
  await waitForUi(appPage);

  let targetPage = await popupPromise;
  if (!targetPage) {
    targetPage = appPage;
  } else {
    await targetPage.waitForLoadState("domcontentloaded");
    await waitForUi(targetPage);
  }

  await expectAnyVisible(targetPage, [
    targetPage.getByRole("heading", { name: headingRegex }),
    targetPage.getByText(headingRegex),
  ]);
  await expect(targetPage.locator("p").first()).toBeVisible();

  await checkpoint(targetPage, testInfo, screenshotName, true);
  const finalUrl = targetPage.url();

  if (targetPage !== appPage) {
    await targetPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else if (appPage.url() !== initialUrl) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = createInitialReport();
  const failures = [];
  const legalUrls = {};
  let loginCompleted = false;
  let accountViewLoaded = false;

  const runValidation = async (reportKey, fn) => {
    try {
      await fn();
      report[reportKey] = "PASS";
    } catch (error) {
      report[reportKey] = `FAIL: ${error.message}`;
      failures.push(`${reportKey}: ${error.message}`);
      await checkpoint(page, testInfo, `failure-${reportKey.replace(/\s+/g, "_")}`).catch(() => {});
    }
  };

  await runValidation("Login", async () => {
    await ensureLoginPage(page);

    const appAlreadyVisible = await firstVisible(
      [page.getByText(/mi negocio/i), page.getByText(/^Negocio$/i), page.locator("aside")],
      3_000,
    );

    if (!appAlreadyVisible) {
      const loginButton = await expectAnyVisible(page, [
        page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
        page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
      ]);

      const popupPromise = page.context().waitForEvent("page", { timeout: 10_000 }).catch(() => null);
      await clickAndWait(page, loginButton);
      const googlePage = await popupPromise;

      if (googlePage) {
        await googlePage.waitForLoadState("domcontentloaded").catch(() => {});
        const accountOption = await firstVisible(
          [
            googlePage.getByText(GOOGLE_ACCOUNT, { exact: true }),
            googlePage.getByRole("button", { name: GOOGLE_ACCOUNT }),
          ],
          8_000,
        );
        if (accountOption) {
          await accountOption.click();
          await googlePage.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
        }
      } else {
        const accountOnMainPage = await firstVisible(
          [
            page.getByText(GOOGLE_ACCOUNT, { exact: true }),
            page.getByRole("button", { name: GOOGLE_ACCOUNT }),
          ],
          5_000,
        );
        if (accountOnMainPage) {
          await accountOnMainPage.click();
        }
      }
    }

    await expectAnyVisible(page, [page.locator("aside"), page.getByText(/mi negocio|negocio/i)], 60_000);
    await expect(page.locator("aside")).toBeVisible();
    await checkpoint(page, testInfo, "01_dashboard_loaded", true);
    loginCompleted = true;
  });

  await runValidation("Mi Negocio menu", async () => {
    if (!loginCompleted) {
      throw new Error("Blocked because Login failed.");
    }

    const negocioSection = await firstVisible(
      [
        page.getByRole("button", { name: /^Negocio$/i }),
        page.getByRole("link", { name: /^Negocio$/i }),
        page.getByText(/^Negocio$/i),
      ],
      20_000,
    );
    if (negocioSection) {
      await clickAndWait(page, negocioSection);
    }

    const miNegocioOption = await expectAnyVisible(page, [
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i),
    ]);
    await clickAndWait(page, miNegocioOption);

    await expect(page.getByText(/Agregar Negocio/i)).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i)).toBeVisible();
    await checkpoint(page, testInfo, "02_mi_negocio_expanded");
  });

  await runValidation("Agregar Negocio modal", async () => {
    if (!loginCompleted) {
      throw new Error("Blocked because Login failed.");
    }

    const addBusiness = await expectAnyVisible(page, [
      page.getByRole("button", { name: /agregar negocio/i }),
      page.getByRole("link", { name: /agregar negocio/i }),
      page.getByText(/agregar negocio/i),
    ]);
    await clickAndWait(page, addBusiness);

    await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
    const businessNameInput = await expectAnyVisible(page, [
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
    ]);
    await expect(businessNameInput).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();
    await checkpoint(page, testInfo, "03_agregar_negocio_modal");

    await businessNameInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page, page.getByRole("button", { name: /Cancelar/i }));
  });

  await runValidation("Administrar Negocios view", async () => {
    if (!loginCompleted) {
      throw new Error("Blocked because Login failed.");
    }

    const administrarOptionVisible = await firstVisible([page.getByText(/Administrar Negocios/i)], 2_500);
    if (!administrarOptionVisible) {
      const miNegocioOption = await expectAnyVisible(page, [
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i),
      ]);
      await clickAndWait(page, miNegocioOption);
    }

    const adminOption = await expectAnyVisible(page, [
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i),
    ]);
    await clickAndWait(page, adminOption);

    await expect(page.getByText(/Información General/i)).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Sección Legal/i)).toBeVisible();
    await checkpoint(page, testInfo, "04_administrar_negocios_view", true);
    accountViewLoaded = true;
  });

  await runValidation("Información General", async () => {
    if (!accountViewLoaded) {
      throw new Error("Blocked because Administrar Negocios view failed.");
    }

    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
    await expect(page.getByText(/@/i)).toBeVisible();
    await expect(page.locator("h1, h2, h3").first()).toBeVisible();
  });

  await runValidation("Detalles de la Cuenta", async () => {
    if (!accountViewLoaded) {
      throw new Error("Blocked because Administrar Negocios view failed.");
    }

    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runValidation("Tus Negocios", async () => {
    if (!accountViewLoaded) {
      throw new Error("Blocked because Administrar Negocios view failed.");
    }

    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
  });

  await runValidation("Términos y Condiciones", async () => {
    if (!accountViewLoaded) {
      throw new Error("Blocked because Administrar Negocios view failed.");
    }

    legalUrls["Términos y Condiciones"] = await openAndValidateLegalLink({
      appPage: page,
      linkRegex: /T[ée]rminos y Condiciones/i,
      headingRegex: /T[ée]rminos y Condiciones/i,
      screenshotName: "05_terminos_y_condiciones",
      testInfo,
    });
  });

  await runValidation("Política de Privacidad", async () => {
    if (!accountViewLoaded) {
      throw new Error("Blocked because Administrar Negocios view failed.");
    }

    legalUrls["Política de Privacidad"] = await openAndValidateLegalLink({
      appPage: page,
      linkRegex: /Pol[ií]tica de Privacidad/i,
      headingRegex: /Pol[ií]tica de Privacidad/i,
      screenshotName: "06_politica_de_privacidad",
      testInfo,
    });
  });

  const resultsDir = path.join(process.cwd(), "test-results");
  await fs.mkdir(resultsDir, { recursive: true });
  const reportPath = path.join(resultsDir, "saleads_mi_negocio_full_test-report.json");
  const reportPayload = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    report,
    legalUrls,
    failures,
  };
  await fs.writeFile(reportPath, `${JSON.stringify(reportPayload, null, 2)}\n`, "utf8");
  await testInfo.attach("final-report-json", { path: reportPath, contentType: "application/json" });

  console.log("Final validation report:", report);
  console.log("Captured legal URLs:", legalUrls);

  expect(failures, `Validation failures:\n${failures.join("\n")}`).toEqual([]);
});
