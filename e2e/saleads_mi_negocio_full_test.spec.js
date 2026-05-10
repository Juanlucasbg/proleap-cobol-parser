const fs = require("node:fs/promises");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

const TEST_NAME = "saleads_mi_negocio_full_test";
const ARTIFACTS_DIR = path.join(process.cwd(), "evidence", TEST_NAME);
const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_EMAIL || "juanlucasbarbiergarzon@gmail.com";

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

function createStepReport() {
  return REPORT_FIELDS.reduce((acc, key) => {
    acc[key] = "FAIL";
    return acc;
  }, {});
}

function safeName(input) {
  return input.toLowerCase().replace(/[^a-z0-9]+/g, "_").replace(/^_|_$/g, "");
}

async function ensureArtifactsDir() {
  await fs.mkdir(ARTIFACTS_DIR, { recursive: true });
}

async function waitForUi(pageLike) {
  await pageLike.waitForLoadState("domcontentloaded");
  await pageLike.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {});
  await pageLike.waitForTimeout(500);
}

async function firstVisible(locators, timeoutMs = 6000) {
  for (const locator of locators) {
    const candidate = locator.first();
    const isVisible = await candidate.isVisible({ timeout: timeoutMs }).catch(() => false);
    if (isVisible) {
      return candidate;
    }
  }

  return null;
}

async function clickAndWait(locator, pageLike) {
  await expect(locator).toBeVisible({ timeout: 30000 });
  await locator.click();
  await waitForUi(pageLike);
}

async function takeCheckpoint(pageLike, label, fullPage = false) {
  const filePath = path.join(
    ARTIFACTS_DIR,
    `${new Date().toISOString().replace(/[:.]/g, "-")}_${safeName(label)}.png`
  );
  await pageLike.screenshot({ path: filePath, fullPage });
  return path.relative(process.cwd(), filePath);
}

function loginUrlFromEnv() {
  return (
    process.env.SALEADS_LOGIN_URL ||
    process.env.SALEADS_URL ||
    process.env.BASE_URL ||
    process.env.APP_URL ||
    null
  );
}

async function openLoginPage(page) {
  const loginUrl = loginUrlFromEnv();

  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
    return loginUrl;
  }

  if (page.url() === "about:blank") {
    throw new Error(
      "No login URL available. Set SALEADS_LOGIN_URL (or SALEADS_URL / BASE_URL / APP_URL)."
    );
  }

  return page.url();
}

async function maybeSelectGoogleAccount(pageLike) {
  const accountOption = await firstVisible(
    [
      pageLike.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
      pageLike.getByRole("link", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
      pageLike.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i"))
    ],
    5000
  );

  if (accountOption) {
    await clickAndWait(accountOption, pageLike);
    return true;
  }

  return false;
}

async function findSidebar(page) {
  const sidebar = await firstVisible(
    [
      page.locator("aside"),
      page.locator("nav").filter({ has: page.getByText(/Negocio|Mi Negocio|Dashboard/i) }),
      page.locator("nav")
    ],
    10000
  );

  if (!sidebar) {
    throw new Error("Left sidebar navigation is not visible.");
  }

  return sidebar;
}

async function getSectionContainer(page, headingPattern) {
  const heading = await firstVisible(
    [page.getByRole("heading", { name: headingPattern }), page.getByText(headingPattern)],
    15000
  );

  if (!heading) {
    throw new Error(`Section heading not found: ${headingPattern}`);
  }

  return heading.locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
}

async function openLegalDocument({
  context,
  page,
  legalSection,
  linkPattern,
  expectedHeading,
  screenshotLabel
}) {
  const legalLink = await firstVisible(
    [
      legalSection.getByRole("link", { name: linkPattern }),
      legalSection.getByText(linkPattern),
      page.getByRole("link", { name: linkPattern }),
      page.getByText(linkPattern)
    ],
    12000
  );

  if (!legalLink) {
    throw new Error(`Legal link not found: ${linkPattern}`);
  }

  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await legalLink.click();
  await waitForUi(page);

  let targetPage = await popupPromise;
  if (!targetPage) {
    targetPage = page;
  } else {
    await targetPage.waitForLoadState("domcontentloaded");
    await waitForUi(targetPage);
  }

  const heading = await firstVisible(
    [
      targetPage.getByRole("heading", { name: expectedHeading }),
      targetPage.getByText(expectedHeading)
    ],
    15000
  );
  if (!heading) {
    throw new Error(`Expected heading not visible: ${expectedHeading}`);
  }

  const legalBody = (await targetPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
  if (legalBody.length < 150) {
    throw new Error("Legal content text is too short or missing.");
  }

  const screenshot = await takeCheckpoint(targetPage, screenshotLabel, true);
  const finalUrl = targetPage.url();

  if (targetPage !== page) {
    await targetPage.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
  }

  return { screenshot, finalUrl };
}

test(TEST_NAME, async ({ page, context }) => {
  await ensureArtifactsDir();

  const report = createStepReport();
  const failures = [];
  const screenshots = {};
  const legalUrls = {};
  let resolvedLoginUrl = null;

  async function runStep(key, action) {
    try {
      await action();
      report[key] = "PASS";
    } catch (error) {
      report[key] = "FAIL";
      failures.push(`${key}: ${error.message}`);
    }
  }

  await runStep("Login", async () => {
    resolvedLoginUrl = await openLoginPage(page);

    const loginButton = await firstVisible(
      [
        page.getByRole("button", {
          name: /sign in with google|iniciar sesion con google|continuar con google|google/i
        }),
        page.getByRole("link", {
          name: /sign in with google|iniciar sesion con google|continuar con google|google/i
        }),
        page.getByText(/sign in with google|iniciar sesion con google|continuar con google/i)
      ],
      20000
    );

    if (!loginButton) {
      throw new Error("Google login button is not visible.");
    }

    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickAndWait(loginButton, page);

    const googlePage = await popupPromise;
    if (googlePage) {
      await googlePage.waitForLoadState("domcontentloaded");
      await waitForUi(googlePage);
      await maybeSelectGoogleAccount(googlePage);
      await googlePage.waitForEvent("close", { timeout: 25000 }).catch(() => {});
      await page.bringToFront();
    } else {
      await maybeSelectGoogleAccount(page);
    }

    await waitForUi(page);
    await findSidebar(page);
    screenshots.dashboard = await takeCheckpoint(page, "dashboard_loaded", true);
  });

  await runStep("Mi Negocio menu", async () => {
    const sidebar = await findSidebar(page);

    const negocioEntry = await firstVisible(
      [
        sidebar.getByRole("button", { name: /^Negocio$/i }),
        sidebar.getByRole("link", { name: /^Negocio$/i }),
        sidebar.getByText(/^Negocio$/i)
      ],
      8000
    );
    if (negocioEntry) {
      await clickAndWait(negocioEntry, page);
    }

    const miNegocioEntry = await firstVisible(
      [
        sidebar.getByRole("button", { name: /Mi Negocio/i }),
        sidebar.getByRole("link", { name: /Mi Negocio/i }),
        sidebar.getByText(/Mi Negocio/i)
      ],
      12000
    );
    if (!miNegocioEntry) {
      throw new Error("Mi Negocio option was not found in sidebar.");
    }
    await clickAndWait(miNegocioEntry, page);

    await expect(sidebar.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 12000 });
    await expect(sidebar.getByText(/Administrar Negocios/i).first()).toBeVisible({
      timeout: 12000
    });

    screenshots.menuExpanded = await takeCheckpoint(page, "mi_negocio_expanded_menu");
  });

  await runStep("Agregar Negocio modal", async () => {
    const sidebar = await findSidebar(page);
    const addBusinessOption = await firstVisible(
      [
        sidebar.getByRole("button", { name: /Agregar Negocio/i }),
        sidebar.getByRole("link", { name: /Agregar Negocio/i }),
        sidebar.getByText(/Agregar Negocio/i)
      ],
      12000
    );

    if (!addBusinessOption) {
      throw new Error("Agregar Negocio option not found.");
    }

    await clickAndWait(addBusinessOption, page);

    const modal = page.getByRole("dialog").first();
    await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible({ timeout: 12000 });
    await expect(modal.getByLabel(/Nombre del Negocio/i)).toBeVisible({ timeout: 12000 });
    await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible({ timeout: 12000 });
    await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible({ timeout: 12000 });
    await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible({
      timeout: 12000
    });

    screenshots.modal = await takeCheckpoint(page, "agregar_negocio_modal");

    const businessNameInput = modal.getByLabel(/Nombre del Negocio/i);
    await businessNameInput.fill("Negocio Prueba Automatizacion");
    await clickAndWait(modal.getByRole("button", { name: /Cancelar/i }), page);
    await expect(modal).toBeHidden({ timeout: 10000 });
  });

  await runStep("Administrar Negocios view", async () => {
    const sidebar = await findSidebar(page);

    const adminOption = await firstVisible(
      [
        sidebar.getByRole("button", { name: /Administrar Negocios/i }),
        sidebar.getByRole("link", { name: /Administrar Negocios/i }),
        sidebar.getByText(/Administrar Negocios/i)
      ],
      12000
    );

    if (!adminOption) {
      const miNegocioEntry = await firstVisible(
        [
          sidebar.getByRole("button", { name: /Mi Negocio/i }),
          sidebar.getByRole("link", { name: /Mi Negocio/i }),
          sidebar.getByText(/Mi Negocio/i)
        ],
        8000
      );

      if (!miNegocioEntry) {
        throw new Error("Could not find Mi Negocio to expand the menu.");
      }

      await clickAndWait(miNegocioEntry, page);
    }

    const refreshedAdminOption = await firstVisible(
      [
        sidebar.getByRole("button", { name: /Administrar Negocios/i }),
        sidebar.getByRole("link", { name: /Administrar Negocios/i }),
        sidebar.getByText(/Administrar Negocios/i)
      ],
      12000
    );
    if (!refreshedAdminOption) {
      throw new Error("Administrar Negocios option not available.");
    }

    await clickAndWait(refreshedAdminOption, page);

    await expect(page.getByText(/Informacion General|Información General/i).first()).toBeVisible({
      timeout: 20000
    });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Seccion Legal|Sección Legal/i).first()).toBeVisible({
      timeout: 20000
    });

    screenshots.accountPage = await takeCheckpoint(page, "administrar_negocios_view", true);
  });

  await runStep("Información General", async () => {
    const section = await getSectionContainer(page, /Informacion General|Información General/i);
    const sectionText = (await section.innerText()).trim();
    const lines = sectionText
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean);

    const emailLine = lines.find((line) =>
      /^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$/i.test(line)
    );
    if (!emailLine) {
      throw new Error("User email is not visible in Informacion General.");
    }

    const nameLine = lines.find((line) => {
      return (
        !line.includes("@") &&
        /[A-Za-z]/.test(line) &&
        !/informacion general|información general|business plan|cambiar plan/i.test(line)
      );
    });
    if (!nameLine) {
      throw new Error("User name is not clearly visible in Informacion General.");
    }

    await expect(section.getByText(/BUSINESS PLAN/i)).toBeVisible({ timeout: 10000 });

    const changePlanButton = await firstVisible(
      [
        section.getByRole("button", { name: /Cambiar Plan/i }),
        section.getByRole("link", { name: /Cambiar Plan/i }),
        section.getByText(/Cambiar Plan/i)
      ],
      10000
    );
    if (!changePlanButton) {
      throw new Error("Cambiar Plan button is not visible.");
    }
  });

  await runStep("Detalles de la Cuenta", async () => {
    const section = await getSectionContainer(page, /Detalles de la Cuenta/i);
    await expect(section.getByText(/Cuenta creada/i)).toBeVisible({ timeout: 10000 });
    await expect(section.getByText(/Estado activo/i)).toBeVisible({ timeout: 10000 });
    await expect(section.getByText(/Idioma seleccionado/i)).toBeVisible({ timeout: 10000 });
  });

  await runStep("Tus Negocios", async () => {
    const section = await getSectionContainer(page, /Tus Negocios/i);
    await expect(section.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible({ timeout: 10000 });

    const addBusinessButton = await firstVisible(
      [
        section.getByRole("button", { name: /Agregar Negocio/i }),
        section.getByRole("link", { name: /Agregar Negocio/i }),
        section.getByText(/Agregar Negocio/i)
      ],
      10000
    );
    if (!addBusinessButton) {
      throw new Error("Agregar Negocio button is not visible in Tus Negocios.");
    }

    const listItem = await firstVisible(
      [
        section.locator("li"),
        section.locator("[role='row']"),
        section.locator("article"),
        section.locator(".card")
      ],
      6000
    );

    if (!listItem) {
      const sectionText = (await section.innerText()).trim();
      if (!/negocio/i.test(sectionText)) {
        throw new Error("Business list content is not visible in Tus Negocios.");
      }
    }
  });

  await runStep("Términos y Condiciones", async () => {
    const legalSection = await getSectionContainer(page, /Seccion Legal|Sección Legal/i);
    const result = await openLegalDocument({
      context,
      page,
      legalSection,
      linkPattern: /Terminos y Condiciones|Términos y Condiciones/i,
      expectedHeading: /Terminos y Condiciones|Términos y Condiciones/i,
      screenshotLabel: "terminos_y_condiciones"
    });
    screenshots.terms = result.screenshot;
    legalUrls.termsAndConditions = result.finalUrl;
  });

  await runStep("Política de Privacidad", async () => {
    const legalSection = await getSectionContainer(page, /Seccion Legal|Sección Legal/i);
    const result = await openLegalDocument({
      context,
      page,
      legalSection,
      linkPattern: /Politica de Privacidad|Política de Privacidad/i,
      expectedHeading: /Politica de Privacidad|Política de Privacidad/i,
      screenshotLabel: "politica_de_privacidad"
    });
    screenshots.privacy = result.screenshot;
    legalUrls.privacyPolicy = result.finalUrl;
  });

  const finalReport = {
    testName: TEST_NAME,
    generatedAt: new Date().toISOString(),
    loginUrl: resolvedLoginUrl,
    googleEmail: GOOGLE_ACCOUNT_EMAIL,
    results: report,
    screenshots,
    legalUrls,
    failures
  };

  const reportPath = path.join(ARTIFACTS_DIR, "final-report.json");
  await fs.writeFile(reportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf-8");
  await test.info().attach("final-report", {
    path: reportPath,
    contentType: "application/json"
  });

  expect(failures, `Validation failures:\n${failures.join("\n")}`).toEqual([]);
});
