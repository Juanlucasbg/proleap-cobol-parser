const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

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

function slug(text) {
  return text
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
}

async function firstVisible(locators, timeout = 10000) {
  for (const locator of locators) {
    const candidate = locator.first();
    const visible = await candidate.isVisible({ timeout }).catch(() => false);
    if (visible) {
      return candidate;
    }
  }
  throw new Error("No visible locator matched the expected options.");
}

async function maybeVisible(locators, timeout = 3000) {
  for (const locator of locators) {
    const candidate = locator.first();
    const visible = await candidate.isVisible({ timeout }).catch(() => false);
    if (visible) {
      return candidate;
    }
  }
  return null;
}

async function clickAndWait(locator, page) {
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click();
  await waitForUi(page);
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const results = Object.fromEntries(REPORT_KEYS.map((k) => [k, "FAIL"]));
  const errors = {};
  const legalUrls = {};
  const runId = new Date().toISOString().replace(/[:.]/g, "-");
  const artifactDir = path.resolve(__dirname, "test-artifacts", `saleads-mi-negocio-${runId}`);
  fs.mkdirSync(artifactDir, { recursive: true });
  let screenshotIndex = 1;

  async function checkpoint(label, targetPage = page, fullPage = false) {
    const filename = `${String(screenshotIndex).padStart(2, "0")}-${slug(label)}.png`;
    screenshotIndex += 1;
    await targetPage.screenshot({
      path: path.join(artifactDir, filename),
      fullPage,
    });
  }

  async function runStep(reportKey, callback) {
    try {
      await callback();
      results[reportKey] = "PASS";
    } catch (error) {
      results[reportKey] = "FAIL";
      errors[reportKey] = error instanceof Error ? error.message : String(error);
      await checkpoint(`${reportKey}-failure`, page, true).catch(() => {});
    }
  }

  function requirePassed(stepName) {
    if (results[stepName] !== "PASS") {
      throw new Error(`Cannot continue because "${stepName}" failed.`);
    }
  }

  async function openLegalPage(linkRegex, headingRegex, screenshotLabel) {
    const link = await firstVisible(
      [
        page.getByRole("link", { name: linkRegex }),
        page.getByText(linkRegex),
      ],
      15000
    );

    const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
    const navPromise = page.waitForNavigation({ waitUntil: "domcontentloaded", timeout: 12000 }).catch(() => null);

    await link.scrollIntoViewIfNeeded().catch(() => {});
    await link.click();

    const popup = await popupPromise;
    let targetPage = page;
    let openedNewTab = false;

    if (popup) {
      targetPage = popup;
      openedNewTab = true;
      await targetPage.waitForLoadState("domcontentloaded");
    } else {
      await navPromise;
    }

    await waitForUi(targetPage);

    const heading = await maybeVisible(
      [
        targetPage.getByRole("heading", { name: headingRegex }),
        targetPage.getByText(headingRegex),
      ],
      20000
    );
    if (!heading) {
      throw new Error(`Expected legal heading ${headingRegex} was not found.`);
    }

    const bodyText = (await targetPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
    if (bodyText.length < 120) {
      throw new Error("Legal page content appears too short.");
    }

    await checkpoint(screenshotLabel, targetPage, true);
    const finalUrl = targetPage.url();

    if (openedNewTab) {
      await targetPage.close();
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
      await waitForUi(page);
    }

    return finalUrl;
  }

  await runStep("Login", async () => {
    const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL || process.env.BASE_URL;
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    if (page.url() === "about:blank") {
      throw new Error(
        "No login URL provided. Set SALEADS_LOGIN_URL (or SALEADS_URL / BASE_URL) to the current environment login page."
      );
    }

    const googleLoginButton = await firstVisible(
      [
        page.getByRole("button", { name: /sign in with google/i }),
        page.getByRole("button", { name: /iniciar sesi[oó]n con google/i }),
        page.getByRole("button", { name: /continuar con google/i }),
        page.getByRole("button", { name: /ingresar con google/i }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
      ],
      15000
    );

    const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
    await clickAndWait(googleLoginButton, page);

    const googlePopup = await popupPromise;
    if (googlePopup) {
      await googlePopup.waitForLoadState("domcontentloaded");
      const accountOption = await maybeVisible([googlePopup.getByText(GOOGLE_ACCOUNT_EMAIL)], 8000);
      if (accountOption) {
        await accountOption.click();
      }
      await googlePopup.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
    } else {
      const accountOption = await maybeVisible([page.getByText(GOOGLE_ACCOUNT_EMAIL)], 8000);
      if (accountOption) {
        await accountOption.click();
      }
    }

    await waitForUi(page);

    const sidebar = await firstVisible(
      [
        page.locator("aside"),
        page.getByRole("navigation"),
        page.getByText(/Mi Negocio/i),
        page.getByText(/Negocio/i),
      ],
      30000
    );
    await expect(sidebar).toBeVisible();
    await checkpoint("dashboard-loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    requirePassed("Login");

    const miNegocio = await firstVisible(
      [
        page.getByRole("button", { name: /Mi Negocio/i }),
        page.getByRole("link", { name: /Mi Negocio/i }),
        page.getByText(/^Mi Negocio$/i),
        page.getByText(/Mi Negocio/i),
      ],
      15000
    );
    await clickAndWait(miNegocio, page);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
    await checkpoint("mi-negocio-menu-expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    requirePassed("Mi Negocio menu");

    const agregarNegocio = await firstVisible(
      [
        page.getByRole("button", { name: /Agregar Negocio/i }),
        page.getByRole("link", { name: /Agregar Negocio/i }),
        page.getByText(/^Agregar Negocio$/i),
      ],
      12000
    );
    await clickAndWait(agregarNegocio, page);

    await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
    await expect(
      page
        .getByRole("textbox", { name: /Nombre del Negocio/i })
        .or(page.getByPlaceholder(/Nombre del Negocio/i))
        .first()
    ).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();
    await checkpoint("agregar-negocio-modal");

    const nombreInput = await maybeVisible(
      [
        page.getByRole("textbox", { name: /Nombre del Negocio/i }),
        page.getByPlaceholder(/Nombre del Negocio/i),
      ],
      6000
    );
    if (nombreInput) {
      await nombreInput.click();
      await nombreInput.fill("Negocio Prueba Automatización");
    }

    const cancelar = await firstVisible(
      [
        page.getByRole("button", { name: /Cancelar/i }),
        page.getByText(/^Cancelar$/i),
      ],
      8000
    );
    await clickAndWait(cancelar, page);
  });

  await runStep("Administrar Negocios view", async () => {
    requirePassed("Mi Negocio menu");

    const administrarVisible = await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      const miNegocio = await firstVisible([page.getByText(/Mi Negocio/i)], 10000);
      await clickAndWait(miNegocio, page);
    }

    const administrarNegocios = await firstVisible(
      [
        page.getByRole("button", { name: /Administrar Negocios/i }),
        page.getByRole("link", { name: /Administrar Negocios/i }),
        page.getByText(/^Administrar Negocios$/i),
      ],
      12000
    );
    await clickAndWait(administrarNegocios, page);

    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();
    await checkpoint("administrar-negocios-page", page, true);
  });

  await runStep("Información General", async () => {
    requirePassed("Administrar Negocios view");

    const infoSection = page.locator("section,div").filter({ has: page.getByText(/Informaci[oó]n General/i).first() }).first();
    await expect(infoSection).toBeVisible();

    const infoText = (await infoSection.innerText()).replace(/\s+/g, " ");
    const hasNameLikeText =
      /juanlucasbarbiergarzon/i.test(infoText) ||
      /\b[A-Za-zÀ-ÿ]{2,}\s+[A-Za-zÀ-ÿ]{2,}\b/.test(infoText.replace(/BUSINESS PLAN/gi, ""));
    if (!hasNameLikeText) {
      throw new Error("Could not validate that the user name is visible in Información General.");
    }

    await expect(infoSection.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()).toBeVisible();
    await expect(infoSection.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(infoSection.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    requirePassed("Administrar Negocios view");

    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    requirePassed("Administrar Negocios view");

    const negociosSection = page.locator("section,div").filter({ has: page.getByText(/Tus Negocios/i).first() }).first();
    await expect(negociosSection).toBeVisible();

    const potentialItems = await negociosSection
      .locator("li, tr, [role='row'], [class*='negocio'], [class*='business']")
      .count();
    const sectionText = (await negociosSection.innerText()).replace(/\s+/g, " ").trim();
    if (potentialItems === 0 && sectionText.length < 35) {
      throw new Error("Business list is not clearly visible in Tus Negocios section.");
    }

    await expect(negociosSection.getByRole("button", { name: /Agregar Negocio/i }).or(page.getByText(/Agregar Negocio/i)).first()).toBeVisible();
    await expect(negociosSection.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
  });

  await runStep("Términos y Condiciones", async () => {
    requirePassed("Administrar Negocios view");
    legalUrls["Términos y Condiciones"] = await openLegalPage(
      /T[eé]rminos y Condiciones/i,
      /T[eé]rminos y Condiciones/i,
      "terminos-y-condiciones"
    );
  });

  await runStep("Política de Privacidad", async () => {
    requirePassed("Administrar Negocios view");
    legalUrls["Política de Privacidad"] = await openLegalPage(
      /Pol[ií]tica de Privacidad/i,
      /Pol[ií]tica de Privacidad/i,
      "politica-de-privacidad"
    );
  });

  const finalReport = {
    generatedAt: new Date().toISOString(),
    results,
    errors,
    legalUrls,
  };
  const reportFilePath = path.join(artifactDir, "final-report.json");
  fs.writeFileSync(reportFilePath, JSON.stringify(finalReport, null, 2), "utf8");

  // eslint-disable-next-line no-console
  console.table(results);
  // eslint-disable-next-line no-console
  console.log(`Final report written to: ${reportFilePath}`);
  if (Object.keys(legalUrls).length > 0) {
    // eslint-disable-next-line no-console
    console.log("Captured legal URLs:", legalUrls);
  }

  const failedSteps = Object.entries(results)
    .filter(([, status]) => status !== "PASS")
    .map(([name]) => name);
  expect(
    failedSteps,
    failedSteps.length === 0
      ? "All validation steps passed."
      : `Failed steps: ${failedSteps.join(", ")}. Details: ${JSON.stringify(errors, null, 2)}`
  ).toHaveLength(0);
});
