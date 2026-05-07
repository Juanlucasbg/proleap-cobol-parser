const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

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

function normalizeLabel(input) {
  return input
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 7000 }).catch(() => {});
  await page.waitForTimeout(700);
}

async function findFirstVisible(page, locators, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const locator of locators) {
      const count = await locator.count().catch(() => 0);
      if (count === 0) {
        continue;
      }

      const candidate = locator.first();
      const visible = await candidate.isVisible().catch(() => false);
      if (visible) {
        return candidate;
      }
    }

    await page.waitForTimeout(250);
  }

  return null;
}

async function clickAndWait(locator, page) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = Object.fromEntries(STEP_KEYS.map((step) => [step, { status: "NOT_EXECUTED" }]));
  const failures = [];

  const evidenceDir = path.join(process.cwd(), "evidence", "saleads_mi_negocio_full_test");
  fs.mkdirSync(evidenceDir, { recursive: true });

  const capture = async (name, targetPage = page, fullPage = false) => {
    const fileName = `${normalizeLabel(name)}.png`;
    const filePath = path.join(evidenceDir, fileName);
    await targetPage.screenshot({ path: filePath, fullPage });
    await testInfo.attach(name, { path: filePath, contentType: "image/png" });
    return filePath;
  };

  const writeFinalReport = async () => {
    const timestamp = new Date().toISOString();
    const payload = {
      test_name: "saleads_mi_negocio_full_test",
      timestamp,
      summary: {
        passed: Object.values(report).filter((entry) => entry.status === "PASS").length,
        failed: Object.values(report).filter((entry) => entry.status === "FAIL").length,
        not_executed: Object.values(report).filter((entry) => entry.status === "NOT_EXECUTED").length,
      },
      steps: report,
    };

    const jsonPath = path.join(evidenceDir, "final_report.json");
    fs.writeFileSync(jsonPath, JSON.stringify(payload, null, 2), "utf-8");
    await testInfo.attach("final_report.json", { path: jsonPath, contentType: "application/json" });
  };

  const runStep = async (stepName, action) => {
    try {
      const details = await action();
      report[stepName] = { status: "PASS", ...(details || {}) };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      report[stepName] = { status: "FAIL", error: message };
      failures.push(`${stepName}: ${message}`);
    }
  };

  const startUrl = process.env.SALEADS_START_URL || process.env.BASE_URL || testInfo.project.use.baseURL;
  if (!startUrl) {
    throw new Error(
      "Missing start URL. Set SALEADS_START_URL (or BASE_URL) to the SaleADS login page for the active environment.",
    );
  }

  await page.goto(startUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  await runStep("Login", async () => {
    const sidebarCandidate = await findFirstVisible(page, [
      page.locator("aside"),
      page.getByRole("navigation"),
      page.getByText(/mi negocio|negocio/i),
    ]);

    if (!sidebarCandidate) {
      const loginButton = await findFirstVisible(page, [
        page.getByRole("button", { name: /sign in with google/i }),
        page.getByRole("button", { name: /iniciar sesi[oó]n con google/i }),
        page.getByRole("button", { name: /continuar con google/i }),
        page.getByRole("button", { name: /google/i }),
        page.getByText(/sign in with google/i),
        page.getByText(/iniciar sesi[oó]n con google/i),
      ]);

      if (!loginButton) {
        throw new Error("Could not find Google login button.");
      }

      const popupPromise = page.waitForEvent("popup", { timeout: 10000 }).catch(() => null);
      await clickAndWait(loginButton, page);

      const popup = await popupPromise;
      const accountTargets = [
        page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }),
        page.getByRole("link", { name: GOOGLE_ACCOUNT_EMAIL }),
        page.getByRole("button", { name: GOOGLE_ACCOUNT_EMAIL }),
      ];

      if (popup) {
        await popup.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
        const popupAccount = await findFirstVisible(popup, [
          popup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }),
          popup.getByRole("link", { name: GOOGLE_ACCOUNT_EMAIL }),
          popup.getByRole("button", { name: GOOGLE_ACCOUNT_EMAIL }),
        ], 8000);

        if (popupAccount) {
          await clickAndWait(popupAccount, popup);
        }
      } else {
        const account = await findFirstVisible(page, accountTargets, 8000);
        if (account) {
          await clickAndWait(account, page);
        }
      }

      await page.bringToFront();
      await waitForUi(page);
    }

    const mainUi = await findFirstVisible(page, [
      page.locator("aside"),
      page.getByRole("navigation"),
    ], 20000);
    if (!mainUi) {
      throw new Error("Main interface did not load after login.");
    }

    const sidebar = await findFirstVisible(page, [
      page.locator("aside"),
      page.getByRole("navigation"),
      page.getByText(/mi negocio|negocio/i),
    ], 10000);
    if (!sidebar) {
      throw new Error("Left sidebar navigation is not visible.");
    }

    const dashboardShot = await capture("step_1_dashboard_loaded", page, true);
    return { screenshot: dashboardShot };
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = await findFirstVisible(page, [
      page.getByRole("link", { name: /^negocio$/i }),
      page.getByRole("button", { name: /^negocio$/i }),
      page.getByText(/^negocio$/i),
    ], 15000);

    if (negocioSection) {
      await clickAndWait(negocioSection, page);
    }

    const miNegocio = await findFirstVisible(page, [
      page.getByRole("link", { name: /^mi negocio$/i }),
      page.getByRole("button", { name: /^mi negocio$/i }),
      page.getByText(/^mi negocio$/i),
    ], 15000);

    if (!miNegocio) {
      throw new Error("Could not find 'Mi Negocio' in sidebar.");
    }

    await clickAndWait(miNegocio, page);

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible({ timeout: 15000 });

    const menuShot = await capture("step_2_mi_negocio_expanded", page, true);
    return { screenshot: menuShot };
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await findFirstVisible(page, [
      page.getByRole("link", { name: /^agregar negocio$/i }),
      page.getByRole("button", { name: /^agregar negocio$/i }),
      page.getByText(/^agregar negocio$/i),
    ], 15000);

    if (!agregarNegocio) {
      throw new Error("Could not find 'Agregar Negocio' option.");
    }

    await clickAndWait(agregarNegocio, page);

    await expect(page.getByText(/crear nuevo negocio/i).first()).toBeVisible({ timeout: 15000 });
    const businessNameInput = page.getByLabel(/nombre del negocio/i).first();
    await expect(businessNameInput).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/tienes\s+2\s+de\s+3\s+negocios/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /cancelar/i }).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /crear negocio/i }).first()).toBeVisible({ timeout: 15000 });

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatizacion");

    const modalShot = await capture("step_3_agregar_negocio_modal", page);

    const cancelBtn = page.getByRole("button", { name: /cancelar/i }).first();
    await clickAndWait(cancelBtn, page);
    await expect(page.getByText(/crear nuevo negocio/i).first()).toBeHidden({ timeout: 10000 });

    return { screenshot: modalShot };
  });

  await runStep("Administrar Negocios view", async () => {
    const miNegocio = await findFirstVisible(page, [
      page.getByRole("link", { name: /^mi negocio$/i }),
      page.getByRole("button", { name: /^mi negocio$/i }),
      page.getByText(/^mi negocio$/i),
    ], 10000);

    if (miNegocio) {
      const adminVisible = await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false);
      if (!adminVisible) {
        await clickAndWait(miNegocio, page);
      }
    }

    const administrarNegocios = await findFirstVisible(page, [
      page.getByRole("link", { name: /^administrar negocios$/i }),
      page.getByRole("button", { name: /^administrar negocios$/i }),
      page.getByText(/^administrar negocios$/i),
    ], 15000);

    if (!administrarNegocios) {
      throw new Error("Could not find 'Administrar Negocios' option.");
    }

    await clickAndWait(administrarNegocios, page);
    await waitForUi(page);

    await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/secci[oó]n legal/i).first()).toBeVisible({ timeout: 15000 });

    const accountShot = await capture("step_4_administrar_negocios_page", page, true);
    return { screenshot: accountShot };
  });

  await runStep("Información General", async () => {
    const infoGeneral = page.getByText(/informaci[oó]n general/i).first();
    await expect(infoGeneral).toBeVisible({ timeout: 15000 });

    const emailVisible = await findFirstVisible(page, [
      page.getByText(/@/),
    ], 15000);
    if (!emailVisible) {
      throw new Error("User email is not visible in Información General.");
    }

    const headingTexts = (await page.locator("h1, h2, h3, strong").allInnerTexts())
      .map((text) => text.trim())
      .filter(Boolean);

    const userNameVisible = headingTexts.some((text) => {
      const likelyName = /^[A-Za-zÀ-ÿ]{2,}(?:\s+[A-Za-zÀ-ÿ]{2,}){1,3}$/.test(text);
      const notSectionTitle = !/informaci[oó]n|detalles|negocios|secci[oó]n|business plan/i.test(text);
      return likelyName && notSectionTitle;
    });

    if (!userNameVisible) {
      throw new Error("User name is not clearly visible in Información General.");
    }

    await expect(page.getByText(/business plan/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible({ timeout: 15000 });

    return {};
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/estado activo/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible({ timeout: 15000 });
    return {};
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/tienes\s+2\s+de\s+3\s+negocios/i).first()).toBeVisible({ timeout: 15000 });
    return {};
  });

  const validateLegalLink = async ({ stepName, linkRegex, headingRegex, screenshotName }) => {
    await runStep(stepName, async () => {
      const appUrlBeforeClick = page.url();
      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);

      const link = await findFirstVisible(page, [
        page.getByRole("link", { name: linkRegex }),
        page.getByRole("button", { name: linkRegex }),
        page.getByText(linkRegex),
      ], 15000);

      if (!link) {
        throw new Error(`Could not find legal link for ${stepName}.`);
      }

      await clickAndWait(link, page);
      const popup = await popupPromise;

      let legalPage = page;
      let openedInNewTab = false;

      if (popup) {
        openedInNewTab = true;
        legalPage = popup;
        await legalPage.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
        await waitForUi(legalPage);
      } else {
        await waitForUi(page);
      }

      const heading = await findFirstVisible(legalPage, [
        legalPage.getByRole("heading", { name: headingRegex }),
        legalPage.getByText(headingRegex),
      ], 20000);

      if (!heading) {
        throw new Error(`Heading validation failed for ${stepName}.`);
      }

      const legalText = (await legalPage.locator("body").innerText()).trim();
      if (legalText.length < 120) {
        throw new Error(`Legal content seems too short for ${stepName}.`);
      }

      const legalShot = await capture(screenshotName, legalPage, true);
      const finalUrl = legalPage.url();

      if (openedInNewTab) {
        await legalPage.close().catch(() => {});
        await page.bringToFront();
        await waitForUi(page);
      } else if (page.url() !== appUrlBeforeClick) {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await waitForUi(page);
      }

      return {
        screenshot: legalShot,
        final_url: finalUrl,
      };
    });
  };

  await validateLegalLink({
    stepName: "Términos y Condiciones",
    linkRegex: /t[eé]rminos y condiciones/i,
    headingRegex: /t[eé]rminos y condiciones/i,
    screenshotName: "step_8_terminos_y_condiciones",
  });

  await validateLegalLink({
    stepName: "Política de Privacidad",
    linkRegex: /pol[ií]tica de privacidad/i,
    headingRegex: /pol[ií]tica de privacidad/i,
    screenshotName: "step_9_politica_de_privacidad",
  });

  await writeFinalReport();

  if (failures.length > 0) {
    throw new Error(`One or more workflow validations failed:\n${failures.join("\n")}`);
  }
});
