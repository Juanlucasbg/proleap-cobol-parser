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

function createReport() {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
}

function resolveLoginUrl(baseURL) {
  return process.env.SALEADS_LOGIN_URL || baseURL || process.env.SALEADS_BASE_URL;
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => {});
  await page.waitForTimeout(700);
}

async function firstVisible(locators, timeout = 3000) {
  for (const locator of locators) {
    try {
      await locator.first().waitFor({ state: "visible", timeout });
      return locator.first();
    } catch (error) {
      // Continue to next candidate locator.
    }
  }

  throw new Error("No candidate locator became visible.");
}

async function assertLegalContent(legalPage, headingRegex) {
  const heading = await firstVisible(
    [
      legalPage.getByRole("heading", { name: headingRegex }),
      legalPage.getByText(headingRegex),
    ],
    10000,
  );

  await expect(heading).toBeVisible();

  const bodyTextLength = await legalPage
    .locator("body")
    .innerText()
    .then((text) => text.trim().length);
  expect(bodyTextLength).toBeGreaterThan(150);
}

async function validateUserNameVisible(page) {
  const isNameVisible = await page.evaluate(() => {
    const excluded = [
      /informaci[oó]n general/i,
      /detalles de la cuenta/i,
      /tus negocios/i,
      /secci[oó]n legal/i,
      /business plan/i,
      /cambiar plan/i,
      /cuenta creada/i,
      /estado activo/i,
      /idioma seleccionado/i,
      /agregar negocio/i,
      /administrar negocios/i,
      /t[eé]rminos y condiciones/i,
      /pol[ií]tica de privacidad/i,
      /negocio/i,
    ];

    const candidates = Array.from(document.querySelectorAll("h1,h2,h3,h4,p,span,strong,div"))
      .map((element) => (element.textContent || "").trim())
      .filter((text) => text.length >= 5 && text.length <= 60);

    return candidates.some((text) => {
      if (excluded.some((rule) => rule.test(text))) return false;
      if (text.includes("@")) return false;
      if (/\d/.test(text)) return false;
      return /^[A-Za-zÀ-ÿ'.-]+(?:\s+[A-Za-zÀ-ÿ'.-]+)+$/.test(text);
    });
  });

  expect(isNameVisible).toBeTruthy();
}

async function openAndValidateLegalLink({
  page,
  testInfo,
  linkText,
  headingRegex,
  screenshotName,
  legalUrls,
}) {
  const appUrlBeforeClick = page.url();
  const popupPromise = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);

  const link = await firstVisible(
    [
      page.getByRole("link", { name: new RegExp(linkText, "i") }),
      page.getByText(new RegExp(linkText, "i")),
    ],
    10000,
  );

  await link.click();
  await waitForUi(page);

  const popup = await popupPromise;
  const legalPage = popup || page;

  await legalPage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await assertLegalContent(legalPage, headingRegex);

  legalUrls[linkText] = legalPage.url();
  console.log(`Final URL (${linkText}): ${legalPage.url()}`);

  await legalPage.screenshot({
    path: testInfo.outputPath(screenshotName),
    fullPage: true,
  });

  if (popup) {
    await popup.close({ runBeforeUnload: true }).catch(() => {});
    await page.bringToFront();
    await waitForUi(page);
    return;
  }

  if (page.url() !== appUrlBeforeClick) {
    await page
      .goBack({ waitUntil: "domcontentloaded", timeout: 15000 })
      .catch(async () => {
        if (appUrlBeforeClick && /^https?:\/\//.test(appUrlBeforeClick)) {
          await page.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" });
        }
      });
    await waitForUi(page);
  }
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("logs in with Google and validates Mi Negocio workflow", async ({ page, baseURL }, testInfo) => {
    const report = createReport();
    const legalUrls = {};
    const failures = [];

    async function runStep(field, executor) {
      try {
        await executor();
        report[field] = "PASS";
      } catch (error) {
        report[field] = "FAIL";
        failures.push({ field, error: String(error) });
        console.error(`Step failed: ${field}`, error);
      }
    }

    const loginUrl = resolveLoginUrl(baseURL);
    if (!loginUrl) {
      throw new Error(
        "Missing target URL. Set SALEADS_LOGIN_URL (preferred) or SALEADS_BASE_URL before running this test.",
      );
    }

    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    await runStep("Login", async () => {
      const googleLoginButton = await firstVisible(
        [
          page.getByRole("button", { name: /google/i }),
          page.getByRole("link", { name: /google/i }),
          page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
        ],
        12000,
      );

      const popupPromise = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);

      await googleLoginButton.click();
      await waitForUi(page);

      const popup = await popupPromise;
      const googlePage = popup || (/accounts\.google\.com/.test(page.url()) ? page : null);

      if (googlePage) {
        await googlePage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});

        const accountOption = googlePage.getByText("juanlucasbarbiergarzon@gmail.com", { exact: false });
        if (await accountOption.first().isVisible().catch(() => false)) {
          await accountOption.first().click();
        }

        if (popup) {
          await popup.waitForEvent("close", { timeout: 30000 }).catch(() => {});
        }
      }

      await waitForUi(page);

      const sidebarHint = await firstVisible(
        [
          page.getByText(/negocio/i),
          page.getByText(/mi negocio/i),
          page.locator("aside, nav").first(),
        ],
        20000,
      );
      await expect(sidebarHint).toBeVisible();

      await page.screenshot({
        path: testInfo.outputPath("01-dashboard-loaded.png"),
        fullPage: true,
      });
    });

    await runStep("Mi Negocio menu", async () => {
      const negocioSection = await firstVisible(
        [page.getByRole("link", { name: /negocio/i }), page.getByText(/^Negocio$/i), page.getByText(/negocio/i)],
        12000,
      );
      await negocioSection.click();
      await waitForUi(page);

      const miNegocioOption = await firstVisible(
        [page.getByRole("link", { name: /mi negocio/i }), page.getByText(/mi negocio/i)],
        12000,
      );
      await miNegocioOption.click();
      await waitForUi(page);

      await expect(page.getByText(/agregar negocio/i)).toBeVisible();
      await expect(page.getByText(/administrar negocios/i)).toBeVisible();

      await page.screenshot({
        path: testInfo.outputPath("02-mi-negocio-menu-expanded.png"),
        fullPage: true,
      });
    });

    await runStep("Agregar Negocio modal", async () => {
      const addBusiness = await firstVisible(
        [page.getByRole("link", { name: /agregar negocio/i }), page.getByRole("button", { name: /agregar negocio/i }), page.getByText(/agregar negocio/i)],
        12000,
      );
      await addBusiness.click();
      await waitForUi(page);

      await expect(await firstVisible([page.getByRole("heading", { name: /crear nuevo negocio/i }), page.getByText(/crear nuevo negocio/i)])).toBeVisible();
      await expect(await firstVisible([page.getByLabel(/nombre del negocio/i), page.getByPlaceholder(/nombre del negocio/i), page.getByText(/nombre del negocio/i)])).toBeVisible();
      await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
      await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();

      const businessNameInput = await firstVisible(
        [page.getByLabel(/nombre del negocio/i), page.getByPlaceholder(/nombre del negocio/i), page.locator("input").first()],
        6000,
      );
      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatización");
      await waitForUi(page);

      await page.screenshot({
        path: testInfo.outputPath("03-agregar-negocio-modal.png"),
        fullPage: true,
      });

      await page.getByRole("button", { name: /cancelar/i }).click();
      await waitForUi(page);
    });

    await runStep("Administrar Negocios view", async () => {
      const miNegocioOption = await firstVisible(
        [page.getByRole("link", { name: /mi negocio/i }), page.getByText(/mi negocio/i)],
        12000,
      );
      if (!(await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false))) {
        await miNegocioOption.click();
        await waitForUi(page);
      }

      const manageBusiness = await firstVisible(
        [page.getByRole("link", { name: /administrar negocios/i }), page.getByText(/administrar negocios/i)],
        12000,
      );
      await manageBusiness.click();
      await waitForUi(page);

      await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible();
      await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
      await expect(page.getByText(/tus negocios/i)).toBeVisible();
      await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible();

      await page.screenshot({
        path: testInfo.outputPath("04-administrar-negocios-account-page.png"),
        fullPage: true,
      });
    });

    await runStep("Información General", async () => {
      await validateUserNameVisible(page);
      await expect(page.getByText(/[\w.%+-]+@[\w.-]+\.[a-z]{2,}/i).first()).toBeVisible();
      await expect(page.getByText(/business plan/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
    });

    await runStep("Detalles de la Cuenta", async () => {
      await expect(page.getByText(/cuenta creada/i)).toBeVisible();
      await expect(page.getByText(/estado activo/i)).toBeVisible();
      await expect(page.getByText(/idioma seleccionado/i)).toBeVisible();
    });

    await runStep("Tus Negocios", async () => {
      const businessesSection = await firstVisible(
        [page.locator("section,div").filter({ hasText: /tus negocios/i }), page.getByText(/tus negocios/i)],
        10000,
      );
      await expect(businessesSection).toBeVisible();
      await expect(await firstVisible([page.getByRole("button", { name: /agregar negocio/i }), page.getByText(/agregar negocio/i)], 10000)).toBeVisible();
      await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
    });

    await runStep("Términos y Condiciones", async () => {
      await openAndValidateLegalLink({
        page,
        testInfo,
        linkText: "Términos y Condiciones",
        headingRegex: /t[eé]rminos y condiciones/i,
        screenshotName: "05-terminos-y-condiciones.png",
        legalUrls,
      });
    });

    await runStep("Política de Privacidad", async () => {
      await openAndValidateLegalLink({
        page,
        testInfo,
        linkText: "Política de Privacidad",
        headingRegex: /pol[ií]tica de privacidad/i,
        screenshotName: "06-politica-de-privacidad.png",
        legalUrls,
      });
    });

    console.log("\nFinal report (PASS/FAIL):");
    console.table(report);
    console.log("Captured legal URLs:", legalUrls);

    await testInfo.attach("saleads-mi-negocio-final-report.json", {
      body: JSON.stringify(
        {
          report,
          legalUrls,
          timestamp: new Date().toISOString(),
        },
        null,
        2,
      ),
      contentType: "application/json",
    });

    expect(failures, `One or more workflow validations failed:\n${JSON.stringify(failures, null, 2)}`).toEqual([]);
  });
});
