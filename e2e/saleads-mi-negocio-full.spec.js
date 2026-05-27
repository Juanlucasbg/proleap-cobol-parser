const fs = require("node:fs");
const path = require("node:path");
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
  "Política de Privacidad",
];

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
  await page.waitForTimeout(400);
}

async function findVisibleLocator(locators, timeoutMs = 3_000) {
  for (const locator of locators) {
    const candidate = locator.first();
    if ((await candidate.count()) === 0) {
      continue;
    }

    if (await candidate.isVisible().catch(() => false)) {
      return candidate;
    }

    const becameVisible = await candidate
      .waitFor({ state: "visible", timeout: timeoutMs })
      .then(() => true)
      .catch(() => false);

    if (becameVisible) {
      return candidate;
    }
  }

  return null;
}

async function clickFirstVisible(locators, errorMessage) {
  const target = await findVisibleLocator(locators, 4_000);
  if (!target) {
    throw new Error(errorMessage);
  }

  await target.click();
  return target;
}

async function takeCheckpoint(testInfo, page, evidenceDir, name, fullPage = false) {
  const safeName = name.replace(/[^\w.-]/g, "_");
  const screenshotPath = path.join(evidenceDir, `${safeName}.png`);

  await page.screenshot({
    path: screenshotPath,
    fullPage,
  });

  await testInfo.attach(name, {
    path: screenshotPath,
    contentType: "image/png",
  });
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const evidenceDir = path.join(testInfo.outputDir, "checkpoints");
  fs.mkdirSync(evidenceDir, { recursive: true });

  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const failures = [];
  const legalUrls = {};

  const runValidation = async (reportField, fn) => {
    try {
      await fn();
      report[reportField] = "PASS";
    } catch (error) {
      report[reportField] = "FAIL";
      failures.push(`${reportField}: ${error.message}`);
    }
  };

  const baseUrl = process.env.SALEADS_BASE_URL || process.env.PLAYWRIGHT_TEST_BASE_URL;
  const loginUrl = process.env.SALEADS_LOGIN_URL;
  const loginPath = process.env.SALEADS_LOGIN_PATH || "/";

  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  } else if (baseUrl) {
    await page.goto(loginPath, { waitUntil: "domcontentloaded" });
  } else if (page.url() === "about:blank") {
    throw new Error(
      "No login page is open. Set SALEADS_LOGIN_URL or SALEADS_BASE_URL (optionally SALEADS_LOGIN_PATH)."
    );
  }
  await waitForUi(page);

  await runValidation("Login", async () => {
    const popupPromise = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);

    await clickFirstVisible(
      [
        page.getByRole("button", {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i,
        }),
        page.getByRole("link", {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i,
        }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
      ],
      "Google login button was not found."
    );
    await waitForUi(page);

    const authPage = await popupPromise;
    const googleTarget = authPage || page;

    if (authPage) {
      await authPage.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
    }

    const accountOption = await findVisibleLocator(
      [
        googleTarget.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }),
        googleTarget.locator(`[data-identifier="${GOOGLE_ACCOUNT_EMAIL}"]`),
        googleTarget.locator(`text=${GOOGLE_ACCOUNT_EMAIL}`),
      ],
      8_000
    );

    if (accountOption) {
      await accountOption.click();
      await waitForUi(googleTarget);
    }

    if (authPage) {
      await authPage.waitForEvent("close", { timeout: 25_000 }).catch(() => {});
      if (!authPage.isClosed()) {
        await authPage.close().catch(() => {});
      }
    }

    await page.bringToFront();
    await waitForUi(page);

    await expect(page.locator("main, [role='main']").first()).toBeVisible();
    await expect(
      page.locator("aside, nav").filter({ hasText: /negocio|mi negocio/i }).first()
    ).toBeVisible();

    await takeCheckpoint(testInfo, page, evidenceDir, "01-dashboard-loaded");
  });

  await runValidation("Mi Negocio menu", async () => {
    const negocioSection = await findVisibleLocator(
      [
        page.getByRole("button", { name: /negocio/i }),
        page.getByRole("link", { name: /negocio/i }),
        page.getByText(/^Negocio$/i),
      ],
      4_000
    );

    if (negocioSection) {
      await negocioSection.click().catch(() => {});
      await waitForUi(page);
    }

    await clickFirstVisible(
      [
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/^Mi Negocio$/i),
      ],
      "The 'Mi Negocio' option was not found."
    );
    await waitForUi(page);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();

    await takeCheckpoint(testInfo, page, evidenceDir, "02-mi-negocio-expanded");
  });

  await runValidation("Agregar Negocio modal", async () => {
    await clickFirstVisible(
      [
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i),
      ],
      "The 'Agregar Negocio' option was not found."
    );
    await waitForUi(page);

    await expect(page.getByText(/^Crear Nuevo Negocio$/i).first()).toBeVisible();

    const nameInput = await findVisibleLocator(
      [
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.locator("input[name*='nombre'], input[id*='nombre']"),
      ],
      5_000
    );
    if (!nameInput) {
      throw new Error("The 'Nombre del Negocio' input field was not found.");
    }

    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible();

    await takeCheckpoint(testInfo, page, evidenceDir, "03-agregar-negocio-modal");

    await nameInput.fill("Negocio Prueba Automatización");
    await clickFirstVisible(
      [
        page.getByRole("button", { name: /^Cancelar$/i }),
        page.getByText(/^Cancelar$/i),
      ],
      "Could not close 'Crear Nuevo Negocio' modal with 'Cancelar'."
    );
    await waitForUi(page);
  });

  await runValidation("Administrar Negocios view", async () => {
    const adminOption = page.getByText(/^Administrar Negocios$/i).first();
    if (!(await adminOption.isVisible().catch(() => false))) {
      await clickFirstVisible(
        [
          page.getByRole("button", { name: /mi negocio/i }),
          page.getByRole("link", { name: /mi negocio/i }),
          page.getByText(/^Mi Negocio$/i),
        ],
        "Could not re-open 'Mi Negocio' menu."
      );
      await waitForUi(page);
    }

    await clickFirstVisible(
      [
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i),
      ],
      "The 'Administrar Negocios' option was not found."
    );
    await waitForUi(page);

    await expect(page.getByText(/^Información General$/i).first()).toBeVisible();
    await expect(page.getByText(/^Detalles de la Cuenta$/i).first()).toBeVisible();
    await expect(page.getByText(/^Tus Negocios$/i).first()).toBeVisible();
    await expect(page.getByText(/^Sección Legal$/i).first()).toBeVisible();

    await takeCheckpoint(testInfo, page, evidenceDir, "04-administrar-negocios-view", true);
  });

  await runValidation("Información General", async () => {
    const infoSection = page
      .locator("section, article, div")
      .filter({ hasText: /Información General/i })
      .first();
    await expect(infoSection).toBeVisible();

    const emailLocator = page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first();
    await expect(emailLocator).toBeVisible();

    const hasLikelyName = await infoSection.evaluate((element) => {
      const candidates = Array.from(
        element.querySelectorAll("h1, h2, h3, h4, p, span, strong, div")
      )
        .map((node) => (node.textContent || "").trim())
        .filter(Boolean);

      return candidates.some((text) => {
        return (
          /^[A-Za-zÀ-ÿ]+(?:\s+[A-Za-zÀ-ÿ]+)+$/.test(text) &&
          !/informaci[oó]n general|business plan|cambiar plan/i.test(text)
        );
      });
    });
    expect(hasLikelyName).toBeTruthy();

    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
  });

  await runValidation("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runValidation("Tus Negocios", async () => {
    const negociosSection = page
      .locator("section, article, div")
      .filter({ hasText: /Tus Negocios/i })
      .first();
    await expect(negociosSection).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible();

    const hasBusinessList = await negociosSection.evaluate((element) => {
      const listLikeCount = element.querySelectorAll("li, tr, [role='listitem'], [role='row']").length;
      if (listLikeCount > 0) {
        return true;
      }

      const text = (element.textContent || "").toLowerCase();
      return text.includes("negocio");
    });
    expect(hasBusinessList).toBeTruthy();
  });

  const openAndValidateLegalPage = async (linkText, reportField, screenshotName) => {
    await runValidation(reportField, async () => {
      const legalLink = await findVisibleLocator(
        [
          page.getByRole("link", { name: linkText }),
          page.getByRole("button", { name: linkText }),
          page.getByText(linkText),
        ],
        4_000
      );

      if (!legalLink) {
        throw new Error(`Could not find legal link '${linkText}'.`);
      }

      const popupPromise = context.waitForEvent("page", { timeout: 6_000 }).catch(() => null);
      await legalLink.click();
      await waitForUi(page);

      let legalPage = await popupPromise;
      if (!legalPage) {
        legalPage = page;
      } else {
        await legalPage.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
      }
      await waitForUi(legalPage);

      await expect(legalPage.getByRole("heading", { name: linkText }).first()).toBeVisible();
      await expect(legalPage.locator("main, article, p").first()).toBeVisible();

      legalUrls[linkText] = legalPage.url();
      await takeCheckpoint(testInfo, legalPage, evidenceDir, screenshotName, true);

      if (legalPage !== page) {
        await legalPage.close().catch(() => {});
        await page.bringToFront();
        await waitForUi(page);
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await waitForUi(page);
      }
    });
  };

  await openAndValidateLegalPage("Términos y Condiciones", "Términos y Condiciones", "08-terminos");
  await openAndValidateLegalPage("Política de Privacidad", "Política de Privacidad", "09-politica");

  const finalReport = {
    generatedAt: new Date().toISOString(),
    report,
    legalUrls,
    failures,
  };

  const finalReportPath = path.join(testInfo.outputDir, "saleads-mi-negocio-final-report.json");
  fs.writeFileSync(finalReportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("final-report", {
    path: finalReportPath,
    contentType: "application/json",
  });

  console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT");
  console.table(report);
  console.log(JSON.stringify(finalReport, null, 2));

  expect(failures, `Validation failures:\n${failures.join("\n")}`).toEqual([]);
});
