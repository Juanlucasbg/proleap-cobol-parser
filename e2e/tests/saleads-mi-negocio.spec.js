const fs = require("fs");
const path = require("path");
const { test, expect } = require("@playwright/test");

const SCREENSHOTS_DIR = path.join(__dirname, "..", "artifacts", "screenshots");
const REPORTS_DIR = path.join(__dirname, "..", "artifacts", "reports");

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

function ensureArtifactsDirs() {
  fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
  fs.mkdirSync(REPORTS_DIR, { recursive: true });
}

async function waitForUiToLoad(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 7_000 }).catch(() => {});
  await page.waitForTimeout(400);
}

async function clickAndWait(page, locator) {
  await locator.click({ timeout: 20_000 });
  await waitForUiToLoad(page);
}

async function isVisible(locator, timeout = 8_000) {
  return locator.first().isVisible({ timeout }).catch(() => false);
}

async function firstVisible(candidates, timeout = 8_000) {
  for (const locator of candidates) {
    if (await isVisible(locator, timeout)) {
      return locator.first();
    }
  }
  return null;
}

function nowStamp() {
  const d = new Date();
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getUTCFullYear()}${pad(d.getUTCMonth() + 1)}${pad(d.getUTCDate())}-${pad(d.getUTCHours())}${pad(d.getUTCMinutes())}${pad(d.getUTCSeconds())}`;
}

async function checkpoint(page, name, options = {}) {
  const fileName = `${nowStamp()}-${name.replace(/\s+/g, "-").toLowerCase()}.png`;
  const filePath = path.join(SCREENSHOTS_DIR, fileName);
  await page.screenshot({ path: filePath, fullPage: Boolean(options.fullPage) });
  return filePath;
}

async function recordCheck(report, key, assertionFn) {
  try {
    await assertionFn();
    report[key] = "PASS";
  } catch (error) {
    report[key] = `FAIL: ${error.message}`;
  }
}

async function trySelectGoogleAccountIfNeeded(googlePage) {
  const accountText = "juanlucasbarbiergarzon@gmail.com";

  const accountCandidates = [
    googlePage.getByText(accountText, { exact: true }),
    googlePage.getByRole("link", { name: accountText, exact: true }),
    googlePage.getByRole("button", { name: accountText, exact: true }),
  ];

  const account = await firstVisible(accountCandidates, 10_000);
  if (account) {
    await account.click({ timeout: 10_000 });
    await googlePage.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
    return true;
  }

  return false;
}

async function ensureLoginPageReady(page) {
  const configuredUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL;
  const currentUrl = page.url();
  const isBlank = currentUrl === "about:blank";

  if (isBlank && configuredUrl) {
    await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
    await waitForUiToLoad(page);
    return;
  }

  if (isBlank && !configuredUrl) {
    throw new Error(
      "Browser started on about:blank. Provide SALEADS_LOGIN_URL (or SALEADS_URL) so the test can open the current environment login page.",
    );
  }
}

async function openLegalLinkAndValidate({
  page,
  context,
  report,
  reportKey,
  linkPattern,
  headingPattern,
  screenshotBaseName,
  evidence,
}) {
  const link = await firstVisible(
    [
      page.getByRole("link", { name: linkPattern }),
      page.getByRole("button", { name: linkPattern }),
      page.getByText(linkPattern),
    ],
    10_000,
  );

  if (!link) {
    report[reportKey] = "FAIL: Legal link not found.";
    return;
  }

  const appPage = page;
  const appUrlBefore = appPage.url();
  const popupPromise = context.waitForEvent("page", { timeout: 6_000 }).catch(() => null);

  await link.click({ timeout: 15_000 });
  await waitForUiToLoad(appPage);

  const popup = await popupPromise;
  const legalPage = popup || appPage;

  await legalPage.waitForLoadState("domcontentloaded", { timeout: 25_000 }).catch(() => {});
  await legalPage.waitForLoadState("networkidle", { timeout: 7_000 }).catch(() => {});

  await recordCheck(report, reportKey, async () => {
    await expect(legalPage.getByRole("heading", { name: headingPattern }).first()).toBeVisible({ timeout: 20_000 });

    const legalContent = legalPage.getByText(/t[eé]rminos|condiciones|privacidad|datos personales|aceptaci[oó]n|uso/i);
    await expect(legalContent.first()).toBeVisible({ timeout: 20_000 });
  });

  const screenshotPath = await checkpoint(legalPage, screenshotBaseName, { fullPage: true });
  const finalUrl = legalPage.url();

  evidence[reportKey] = {
    screenshot: screenshotPath,
    finalUrl,
  };

  if (popup) {
    await popup.close().catch(() => {});
    await appPage.bringToFront();
    await waitForUiToLoad(appPage);
  } else if (appPage.url() !== appUrlBefore) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUiToLoad(appPage);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  ensureArtifactsDirs();

  const report = Object.fromEntries(REPORT_KEYS.map((key) => [key, "FAIL: Not executed."]));
  const evidence = {};

  await ensureLoginPageReady(page);

  // Step 1 - Login with Google
  await recordCheck(report, "Login", async () => {
    const loginButton = await firstVisible(
      [
        page.getByRole("button", {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
        }),
        page.getByRole("link", {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
        }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
        page.getByRole("button", { name: /iniciar sesi[oó]n|login|acceder/i }),
      ],
      15_000,
    );

    if (!loginButton) {
      throw new Error("Login button / Google sign-in action not found.");
    }

    const popupPromise = context.waitForEvent("page", { timeout: 6_000 }).catch(() => null);
    await clickAndWait(page, loginButton);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
      await trySelectGoogleAccountIfNeeded(popup);
      await popup.waitForTimeout(1_000);
    } else {
      await trySelectGoogleAccountIfNeeded(page).catch(() => {});
    }

    await waitForUiToLoad(page);

    const sidebar = await firstVisible(
      [
        page.locator("aside").filter({ hasText: /negocio|mi negocio|dashboard/i }),
        page.locator("nav").filter({ hasText: /negocio|mi negocio|dashboard/i }),
        page.getByText(/negocio|mi negocio/i),
      ],
      35_000,
    );

    await expect(sidebar).not.toBeNull();
    await expect(sidebar).toBeVisible({ timeout: 20_000 });
  });

  evidence.Login = {
    screenshot: await checkpoint(page, "dashboard-loaded", { fullPage: true }),
  };

  // Step 2 - Open Mi Negocio menu
  await recordCheck(report, "Mi Negocio menu", async () => {
    const negocio = await firstVisible(
      [
        page.getByRole("button", { name: /^Negocio$/i }),
        page.getByRole("link", { name: /^Negocio$/i }),
        page.getByText(/^Negocio$/i),
      ],
      15_000,
    );

    if (negocio) {
      await clickAndWait(page, negocio);
    }

    const miNegocio = await firstVisible(
      [
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i),
      ],
      15_000,
    );

    if (!miNegocio) {
      throw new Error("'Mi Negocio' option not found in sidebar.");
    }

    await clickAndWait(page, miNegocio);

    await expect(page.getByText("Agregar Negocio", { exact: true }).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText("Administrar Negocios", { exact: true }).first()).toBeVisible({ timeout: 20_000 });
  });

  evidence["Mi Negocio menu"] = {
    screenshot: await checkpoint(page, "mi-negocio-expanded-menu"),
  };

  // Step 3 - Validate Agregar Negocio modal
  await recordCheck(report, "Agregar Negocio modal", async () => {
    const agregarNegocio = await firstVisible(
      [
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i),
      ],
      15_000,
    );

    if (!agregarNegocio) {
      throw new Error("'Agregar Negocio' action not found.");
    }

    await clickAndWait(page, agregarNegocio);

    await expect(page.getByRole("heading", { name: "Crear Nuevo Negocio" }).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByLabel(/Nombre del Negocio/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /^Cancelar$/i }).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i }).first()).toBeVisible({ timeout: 20_000 });

    const nombreInput = await firstVisible(
      [page.getByLabel(/Nombre del Negocio/i), page.getByPlaceholder(/Nombre del Negocio/i), page.locator("input").first()],
      5_000,
    );
    if (nombreInput) {
      await nombreInput.click({ timeout: 5_000 });
      await nombreInput.fill("Negocio Prueba Automatización");
      await page.waitForTimeout(250);
    }

    evidence["Agregar Negocio modal"] = {
      screenshot: await checkpoint(page, "agregar-negocio-modal"),
    };

    const cancelar = await firstVisible(
      [page.getByRole("button", { name: /^Cancelar$/i }), page.getByText(/^Cancelar$/i)],
      8_000,
    );
    if (cancelar) {
      await clickAndWait(page, cancelar);
    }
  });

  if (!evidence["Agregar Negocio modal"]) {
    evidence["Agregar Negocio modal"] = {
      screenshot: await checkpoint(page, "agregar-negocio-modal-fallback"),
    };
  }

  // Step 4 - Open Administrar Negocios
  await recordCheck(report, "Administrar Negocios view", async () => {
    if (!(await isVisible(page.getByText("Administrar Negocios", { exact: true }).first(), 4_000))) {
      const miNegocio = await firstVisible(
        [page.getByRole("button", { name: /^Mi Negocio$/i }), page.getByRole("link", { name: /^Mi Negocio$/i }), page.getByText(/^Mi Negocio$/i)],
        8_000,
      );
      if (miNegocio) {
        await clickAndWait(page, miNegocio);
      }
    }

    const administrar = await firstVisible(
      [
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i),
      ],
      15_000,
    );

    if (!administrar) {
      throw new Error("'Administrar Negocios' option not found.");
    }

    await clickAndWait(page, administrar);

    await expect(page.getByText("Información General", { exact: true }).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText("Detalles de la Cuenta", { exact: true }).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText("Tus Negocios", { exact: true }).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText("Sección Legal", { exact: true }).first()).toBeVisible({ timeout: 20_000 });
  });

  evidence["Administrar Negocios view"] = {
    screenshot: await checkpoint(page, "administrar-negocios-view", { fullPage: true }),
  };

  // Step 5 - Validate Información General
  await recordCheck(report, "Información General", async () => {
    await expect(page.getByText("Información General", { exact: true }).first()).toBeVisible({ timeout: 20_000 });

    const emailCandidate = await firstVisible(
      [page.getByText(/juanlucasbarbiergarzon@gmail\.com/i), page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)],
      10_000,
    );
    if (!emailCandidate) {
      throw new Error("User email not visible in Información General.");
    }

    const userNameCandidate = await firstVisible(
      [
        page.getByText(/juan|lucas|barbier|garzon/i),
        page.locator("section,div").filter({ hasText: "Información General" }).locator("p,h4,h5,span,strong").first(),
      ],
      8_000,
    );
    if (!userNameCandidate) {
      throw new Error("User name not visible in Información General.");
    }

    await expect(page.getByText("BUSINESS PLAN", { exact: true }).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /^Cambiar Plan$/i }).first()).toBeVisible({ timeout: 20_000 });
  });

  // Step 6 - Validate Detalles de la Cuenta
  await recordCheck(report, "Detalles de la Cuenta", async () => {
    await expect(page.getByText("Detalles de la Cuenta", { exact: true }).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 20_000 });
  });

  // Step 7 - Validate Tus Negocios
  await recordCheck(report, "Tus Negocios", async () => {
    await expect(page.getByText("Tus Negocios", { exact: true }).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: 20_000 });

    const businessListCandidate = await firstVisible(
      [
        page.locator("section,div").filter({ hasText: "Tus Negocios" }).locator("li"),
        page.locator("section,div").filter({ hasText: "Tus Negocios" }).locator("table tr"),
        page.locator("section,div").filter({ hasText: "Tus Negocios" }).locator("[data-testid*='business']"),
      ],
      10_000,
    );
    if (!businessListCandidate) {
      throw new Error("Business list is not visible in 'Tus Negocios'.");
    }
  });

  // Step 8 - Validate Términos y Condiciones
  await openLegalLinkAndValidate({
    page,
    context,
    report,
    reportKey: "Términos y Condiciones",
    linkPattern: /T[eé]rminos y Condiciones/i,
    headingPattern: /T[eé]rminos y Condiciones/i,
    screenshotBaseName: "terminos-y-condiciones",
    evidence,
  });

  // Step 9 - Validate Política de Privacidad
  await openLegalLinkAndValidate({
    page,
    context,
    report,
    reportKey: "Política de Privacidad",
    linkPattern: /Pol[ií]tica de Privacidad/i,
    headingPattern: /Pol[ií]tica de Privacidad/i,
    screenshotBaseName: "politica-de-privacidad",
    evidence,
  });

  // Step 10 - Final report (always generated)
  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    validations: report,
    evidence,
  };

  const reportPath = path.join(REPORTS_DIR, "mi-negocio-final-report.json");
  fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  console.log(`Final report written to ${reportPath}`);
  console.log(JSON.stringify(finalReport, null, 2));

  const failedValidations = Object.entries(report).filter(([, status]) => !String(status).startsWith("PASS"));
  expect(failedValidations, `One or more validations failed: ${JSON.stringify(failedValidations)}`).toEqual([]);
});
