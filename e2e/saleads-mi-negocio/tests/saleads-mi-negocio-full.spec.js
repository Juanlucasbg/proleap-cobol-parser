const fs = require("node:fs");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";

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

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function slugify(label) {
  return label
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

async function safeIsVisible(locator) {
  try {
    return await locator.isVisible();
  } catch (_error) {
    return false;
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const runId = new Date().toISOString().replace(/[:.]/g, "-");
  const evidenceDir = path.join(
    process.cwd(),
    "test-results",
    "saleads-mi-negocio",
    runId,
  );
  fs.mkdirSync(evidenceDir, { recursive: true });

  const report = Object.fromEntries(
    REPORT_KEYS.map((key) => [key, { status: "FAIL", detail: "Not executed." }]),
  );
  const legalUrls = {
    "Términos y Condiciones": "",
    "Política de Privacidad": "",
  };

  const screenshot = async (name, targetPage = page, fullPage = true) => {
    const filePath = path.join(evidenceDir, `${name}.png`);
    try {
      await targetPage.screenshot({ path: filePath, fullPage });
    } catch (error) {
      console.error(`Screenshot failed (${name}): ${error.message}`);
    }
  };

  const waitForUi = async (targetPage = page) => {
    await targetPage.waitForLoadState("domcontentloaded");
    await targetPage.waitForTimeout(400);
    await targetPage.waitForLoadState("networkidle").catch(() => {});
  };

  const clickAndWait = async (locator, targetPage = page) => {
    await expect(locator).toBeVisible();
    await locator.click();
    await waitForUi(targetPage);
  };

  const pickVisible = async (candidates, timeoutMs = 15000) => {
    const deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      for (const locator of candidates) {
        if (await safeIsVisible(locator.first())) {
          return locator.first();
        }
      }
      await page.waitForTimeout(300);
    }
    throw new Error("Could not find any visible locator candidate.");
  };

  const markStep = async (name, fn) => {
    try {
      await fn();
      report[name] = { status: "PASS", detail: "All validations passed." };
    } catch (error) {
      report[name] = {
        status: "FAIL",
        detail: error && error.message ? error.message : String(error),
      };
      await screenshot(`fail-${slugify(name)}`, page, true);
    }
  };

  const ensureMiNegocioExpanded = async () => {
    const agregarNegocio = page.getByText("Agregar Negocio", { exact: true });
    const administrarNegocios = page.getByText("Administrar Negocios", {
      exact: true,
    });

    if (
      (await safeIsVisible(agregarNegocio.first())) &&
      (await safeIsVisible(administrarNegocios.first()))
    ) {
      return;
    }

    const negocioSection = await pickVisible(
      [
        page.getByRole("button", { name: /^Negocio$/i }),
        page.getByRole("link", { name: /^Negocio$/i }),
        page.getByText(/^Negocio$/i),
      ],
      10000,
    );
    await clickAndWait(negocioSection);

    const miNegocioOption = await pickVisible(
      [
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i),
      ],
      10000,
    );
    await clickAndWait(miNegocioOption);
  };

  const validateLegalPage = async (linkText, headingText) => {
    const link = await pickVisible(
      [
        page.getByRole("link", { name: new RegExp(escapeRegExp(linkText), "i") }),
        page.getByText(new RegExp(`^${escapeRegExp(linkText)}$`, "i")),
      ],
      15000,
    );

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    const originalUrl = page.url();
    await link.click();

    let legalPage = await popupPromise;
    if (legalPage) {
      await waitForUi(legalPage);
    } else {
      legalPage = page;
      await waitForUi(page);
    }

    const headingLocator = await pickVisible(
      [
        legalPage.getByRole("heading", {
          name: new RegExp(escapeRegExp(headingText), "i"),
        }),
        legalPage.getByText(new RegExp(escapeRegExp(headingText), "i")),
      ],
      20000,
    );
    await expect(headingLocator).toBeVisible();

    const bodyText = (await legalPage.locator("body").innerText()).trim();
    expect(bodyText.length).toBeGreaterThan(120);

    await screenshot(`legal-${slugify(linkText)}`, legalPage, true);
    const finalUrl = legalPage.url();

    if (legalPage !== page) {
      await legalPage.close();
      await page.bringToFront();
      await waitForUi(page);
    } else if (finalUrl !== originalUrl) {
      await page.goBack().catch(() => {});
      await waitForUi(page);
    }

    return finalUrl;
  };

  const configuredUrl =
    process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || process.env.BASE_URL;

  if (configuredUrl) {
    await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
    await waitForUi();
  }

  await markStep("Login", async () => {
    const loginButton = await pickVisible(
      [
        page.getByRole("button", { name: /sign in with google/i }),
        page.getByRole("button", { name: /iniciar sesi[oó]n con google/i }),
        page.getByRole("button", { name: /google/i }),
        page.getByText(/sign in with google/i),
        page.getByText(/iniciar sesi[oó]n con google/i),
      ],
      30000,
    );

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await loginButton.click();
    await waitForUi();

    const googlePage = await popupPromise;
    if (googlePage) {
      await waitForUi(googlePage);
      const accountSelector = googlePage.getByText(
        new RegExp(escapeRegExp(GOOGLE_ACCOUNT_EMAIL), "i"),
      );
      if (await safeIsVisible(accountSelector.first())) {
        await accountSelector.first().click();
        await waitForUi(googlePage);
      }
      await googlePage.bringToFront();
      await page.bringToFront();
    } else {
      const inPageAccountSelector = page.getByText(
        new RegExp(escapeRegExp(GOOGLE_ACCOUNT_EMAIL), "i"),
      );
      if (await safeIsVisible(inPageAccountSelector.first())) {
        await inPageAccountSelector.first().click();
        await waitForUi();
      }
    }

    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 60000 });
    await expect(
      page
        .getByText(/Negocio|Dashboard|Panel|Inicio/i)
        .first(),
    ).toBeVisible({ timeout: 60000 });

    await screenshot("01-dashboard-loaded", page, true);
  });

  await markStep("Mi Negocio menu", async () => {
    await ensureMiNegocioExpanded();
    await expect(page.getByText("Agregar Negocio", { exact: true }).first()).toBeVisible();
    await expect(
      page.getByText("Administrar Negocios", { exact: true }).first(),
    ).toBeVisible();
    await screenshot("02-mi-negocio-expanded", page, false);
  });

  await markStep("Agregar Negocio modal", async () => {
    await ensureMiNegocioExpanded();

    const agregarNegocio = await pickVisible(
      [
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i),
      ],
      10000,
    );
    await clickAndWait(agregarNegocio);

    const modalTitle = page.getByRole("heading", { name: /Crear Nuevo Negocio/i }).first();
    await expect(modalTitle).toBeVisible();
    await expect(page.getByLabel(/Nombre del Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i }).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i }).first()).toBeVisible();

    const businessNameInput = page.getByLabel(/Nombre del Negocio/i).first();
    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page.getByRole("button", { name: /^Cancelar$/i }).first());

    await screenshot("03-agregar-negocio-modal", page, false);
  });

  await markStep("Administrar Negocios view", async () => {
    await ensureMiNegocioExpanded();

    const administrar = await pickVisible(
      [
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i),
      ],
      10000,
    );
    await clickAndWait(administrar);

    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();

    await screenshot("04-administrar-negocios", page, true);
  });

  await markStep("Información General", async () => {
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();

    const pageText = await page.locator("body").innerText();
    const hasEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(pageText);
    expect(hasEmail).toBeTruthy();

    const usernameHint = process.env.SALEADS_EXPECTED_USER_NAME;
    if (usernameHint) {
      await expect(page.getByText(new RegExp(escapeRegExp(usernameHint), "i")).first()).toBeVisible();
    } else {
      const meaningfulLines = pageText
        .split("\n")
        .map((line) => line.trim())
        .filter(Boolean)
        .filter(
          (line) =>
            !/información general|business plan|cambiar plan|detalles de la cuenta|tus negocios|sección legal|@/i.test(
              line,
            ),
        );
      const hasLikelyName = meaningfulLines.some((line) =>
        /^[\p{L}][\p{L}\s.'-]{2,}$/u.test(line),
      );
      expect(hasLikelyName).toBeTruthy();
    }
  });

  await markStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await markStep("Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
  });

  await markStep("Términos y Condiciones", async () => {
    legalUrls["Términos y Condiciones"] = await validateLegalPage(
      "Términos y Condiciones",
      "Términos y Condiciones",
    );
    await ensureMiNegocioExpanded();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();
  });

  await markStep("Política de Privacidad", async () => {
    legalUrls["Política de Privacidad"] = await validateLegalPage(
      "Política de Privacidad",
      "Política de Privacidad",
    );
    await ensureMiNegocioExpanded();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();
  });

  const finalReport = {
    test_name: "saleads_mi_negocio_full_test",
    generated_at: new Date().toISOString(),
    evidence_directory: evidenceDir,
    legal_urls: legalUrls,
    results: report,
  };

  fs.writeFileSync(
    path.join(evidenceDir, "final-report.json"),
    JSON.stringify(finalReport, null, 2),
    "utf8",
  );

  console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT_START");
  console.log(JSON.stringify(finalReport, null, 2));
  console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT_END");

  const failedSteps = Object.entries(report).filter(([, result]) => result.status !== "PASS");
  expect(
    failedSteps,
    `Some workflow validations failed. Report path: ${path.join(evidenceDir, "final-report.json")}`,
  ).toEqual([]);
});
