const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");
const path = require("node:path");

const TEST_NAME = "saleads_mi_negocio_full_test";
const ARTIFACTS_DIR = process.env.SALEADS_EVIDENCE_DIR || path.join("artifacts", TEST_NAME);
const EXPECTED_EMAIL = process.env.SALEADS_GOOGLE_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const EXPECTED_NAME_REGEX = process.env.SALEADS_EXPECTED_NAME_REGEX
  ? new RegExp(process.env.SALEADS_EXPECTED_NAME_REGEX, "i")
  : /juan/i;
const REPORT_PATH = path.join(ARTIFACTS_DIR, "final_report.json");

function statusLine(status, details = "") {
  return details ? `${status} - ${details}` : status;
}

async function ensureArtifactsDir() {
  await fs.mkdir(ARTIFACTS_DIR, { recursive: true });
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(800);
}

async function firstVisibleLocator(candidates) {
  for (const locator of candidates) {
    const count = await locator.count();
    if (!count) {
      continue;
    }

    for (let i = 0; i < count; i += 1) {
      const element = locator.nth(i);
      if (await element.isVisible().catch(() => false)) {
        return element;
      }
    }
  }

  return null;
}

function scopeList(page) {
  return [page, ...page.frames().filter((frame) => frame !== page.mainFrame())];
}

async function firstVisibleAcrossScopes(page, candidateFactories) {
  for (const scope of scopeList(page)) {
    const target = await firstVisibleLocator(candidateFactories.map((build) => build(scope)));
    if (target) {
      return target;
    }
  }

  return null;
}

async function clickFirstVisible(page, candidates, description) {
  const target = await firstVisibleLocator(candidates);
  if (!target) {
    throw new Error(`Unable to find clickable element for "${description}"`);
  }

  await target.click();
  await waitForUi(page);
  return target;
}

async function screenshot(page, name, fullPage = false) {
  const targetPath = path.join(ARTIFACTS_DIR, name);
  await page.screenshot({ path: targetPath, fullPage });
  return targetPath;
}

async function dismissBlockingDialogs(page) {
  const closeButton = await firstVisibleLocator([
    page.getByRole("button", { name: /close message|cerrar|close|x/i }),
    page.locator("[role='dialog'] button").first()
  ]);

  if (closeButton) {
    await closeButton.click().catch(() => {});
    await waitForUi(page);
  }
}

async function clickFirstVisibleAcrossScopes(page, candidateFactories, description) {
  const target = await firstVisibleAcrossScopes(page, candidateFactories);
  if (!target) {
    throw new Error(`Unable to find clickable element for "${description}"`);
  }

  await target.click();
  await waitForUi(page);
}

async function writeReport(report) {
  await fs.writeFile(REPORT_PATH, JSON.stringify(report, null, 2), "utf8");
}

function sanitizeFileName(value) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .toLowerCase();
}

async function runStep(report, key, fn, page = null) {
  try {
    const details = await fn();
    report[key] = {
      status: "PASS",
      details: details || ""
    };
  } catch (error) {
    let detailMessage = error.message;
    if (page) {
      const failedStepScreenshot = await screenshot(page, `fail_${sanitizeFileName(key)}.png`, true).catch(() => null);
      if (failedStepScreenshot) {
        detailMessage = `${detailMessage} (screenshot: ${failedStepScreenshot})`;
      }
    }

    report[key] = {
      status: "FAIL",
      details: detailMessage
    };
  }
}

async function validateTextVisible(page, textPattern, description) {
  const locator = page.getByText(textPattern).first();
  await expect(locator, `Expected to find ${description}`).toBeVisible({ timeout: 30000 });
}

async function openLegalLinkAndValidate({
  page,
  linkPattern,
  headingPattern,
  evidenceFile,
  fallbackReturnUrl
}) {
  const context = page.context();
  const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);

  await clickFirstVisible(
    page,
    [
      page.getByRole("link", { name: linkPattern }),
      page.getByRole("button", { name: linkPattern }),
      page.getByText(linkPattern)
    ],
    `legal link ${linkPattern}`
  );

  const popup = await popupPromise;
  const legalPage = popup || page;
  await waitForUi(legalPage);

  const headingLocator = await firstVisibleLocator([
    legalPage.getByRole("heading", { name: headingPattern }),
    legalPage.getByText(headingPattern)
  ]);

  if (!headingLocator) {
    throw new Error(`Heading not visible for legal page: ${headingPattern}`);
  }

  const bodyText = await legalPage.locator("body").innerText();
  if (!bodyText || bodyText.trim().length < 120) {
    throw new Error("Legal content appears empty or too short");
  }

  const screenshotPath = await screenshot(legalPage, evidenceFile, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    const restored = await page.goBack({ waitUntil: "domcontentloaded", timeout: 10000 }).catch(() => null);
    if (!restored && fallbackReturnUrl) {
      await page.goto(fallbackReturnUrl, { waitUntil: "domcontentloaded" });
    }
    await waitForUi(page);
  }

  return {
    screenshotPath,
    finalUrl
  };
}

test(TEST_NAME, async ({ page }) => {
  await ensureArtifactsDir();

  const report = {
    Login: { status: "FAIL", details: "Not executed" },
    "Mi Negocio menu": { status: "FAIL", details: "Not executed" },
    "Agregar Negocio modal": { status: "FAIL", details: "Not executed" },
    "Administrar Negocios view": { status: "FAIL", details: "Not executed" },
    "Información General": { status: "FAIL", details: "Not executed" },
    "Detalles de la Cuenta": { status: "FAIL", details: "Not executed" },
    "Tus Negocios": { status: "FAIL", details: "Not executed" },
    "Términos y Condiciones": { status: "FAIL", details: "Not executed" },
    "Política de Privacidad": { status: "FAIL", details: "Not executed" }
  };

  const targetUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL || "";
  let preconditionError = "";
  if (targetUrl) {
    await page.goto(targetUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  } else if (page.url() === "about:blank") {
    preconditionError =
      "No login URL provided. Set SALEADS_LOGIN_URL (or SALEADS_URL) or run against a preloaded browser page.";
  }

  await runStep(
    report,
    "Login",
    async () => {
    if (preconditionError) {
      throw new Error(preconditionError);
    }

    await dismissBlockingDialogs(page);
    const preLoginButton = await firstVisibleLocator([
      page.getByRole("button", { name: /^sign in$/i }),
      page.getByRole("button", { name: /^iniciar sesi[oó]n$/i }),
      page.getByRole("link", { name: /^sign in$/i }),
      page.getByRole("link", { name: /^iniciar sesi[oó]n$/i })
    ]);
    if (preLoginButton) {
      await preLoginButton.click();
      await waitForUi(page);
      await dismissBlockingDialogs(page);
    }

    const popupPromise = page.context().waitForEvent("page", { timeout: 15000 }).catch(() => null);

    await clickFirstVisibleAcrossScopes(
      page,
      [
        (scope) => scope.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
        (scope) => scope.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
        (scope) => scope.getByText(/sign in with google|iniciar sesi[oó]n con google|google/i)
      ],
      "login with Google"
    );

    const maybePopup = await popupPromise;
    if (maybePopup) {
      await waitForUi(maybePopup);
      const accountSelector = await firstVisibleAcrossScopes(maybePopup, [
        (scope) => scope.getByText(EXPECTED_EMAIL, { exact: true }),
        (scope) => scope.getByRole("button", { name: EXPECTED_EMAIL }),
        (scope) => scope.getByRole("link", { name: EXPECTED_EMAIL })
      ]);

      if (accountSelector) {
        await accountSelector.click();
        await waitForUi(maybePopup);
      }
    } else {
      const accountSelector = await firstVisibleAcrossScopes(page, [
        (scope) => scope.getByText(EXPECTED_EMAIL, { exact: true }),
        (scope) => scope.getByRole("button", { name: EXPECTED_EMAIL }),
        (scope) => scope.getByRole("link", { name: EXPECTED_EMAIL })
      ]);

      if (accountSelector) {
        await accountSelector.click();
        await waitForUi(page);
      }
    }

    const sidebar = await firstVisibleLocator([
      page.locator("aside"),
      page.getByRole("navigation"),
      page.getByText(/mi negocio|negocio/i)
    ]);

    if (!sidebar) {
      throw new Error("Main app interface/sidebar not visible after login");
    }

    const screenshotPath = await screenshot(page, "01_dashboard_loaded.png", true);
    return statusLine("Dashboard loaded", `Screenshot: ${screenshotPath}`);
    },
    page
  );

  await runStep(
    report,
    "Mi Negocio menu",
    async () => {
    if (preconditionError) {
      throw new Error(preconditionError);
    }

    await clickFirstVisible(
      page,
      [
        page.getByRole("button", { name: /^negocio$/i }),
        page.getByRole("link", { name: /^negocio$/i }),
        page.getByText(/^Negocio$/i)
      ],
      "Negocio section"
    );

    await clickFirstVisible(
      page,
      [
        page.getByRole("button", { name: /^mi negocio$/i }),
        page.getByRole("link", { name: /^mi negocio$/i }),
        page.getByText(/^Mi Negocio$/i)
      ],
      "Mi Negocio menu"
    );

    await validateTextVisible(page, /^Agregar Negocio$/i, "submenu Agregar Negocio");
    await validateTextVisible(page, /^Administrar Negocios$/i, "submenu Administrar Negocios");
    const screenshotPath = await screenshot(page, "02_mi_negocio_menu_expanded.png");
    return statusLine("Submenu expanded", `Screenshot: ${screenshotPath}`);
    },
    page
  );

  await runStep(
    report,
    "Agregar Negocio modal",
    async () => {
    if (preconditionError) {
      throw new Error(preconditionError);
    }

    await clickFirstVisible(
      page,
      [
        page.getByRole("button", { name: /^agregar negocio$/i }),
        page.getByRole("link", { name: /^agregar negocio$/i }),
        page.getByText(/^Agregar Negocio$/i)
      ],
      "Agregar Negocio option"
    );

    await validateTextVisible(page, /^Crear Nuevo Negocio$/i, "modal title Crear Nuevo Negocio");
    await validateTextVisible(page, /Nombre del Negocio/i, "Nombre del Negocio input");
    await validateTextVisible(page, /Tienes 2 de 3 negocios/i, "limit text");
    await validateTextVisible(page, /^Cancelar$/i, "Cancelar button");
    await validateTextVisible(page, /^Crear Negocio$/i, "Crear Negocio button");

    const nameInput = await firstVisibleLocator([
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.getByLabel(/Nombre del Negocio/i),
      page.locator("[role='dialog'] input").first(),
      page.locator("input").first()
    ]);
    if (nameInput) {
      await nameInput.click();
      await waitForUi(page);
      await nameInput.fill("Negocio Prueba Automatización");
      await waitForUi(page);
    }

    const screenshotPath = await screenshot(page, "03_agregar_negocio_modal.png", true);
    await clickFirstVisible(
      page,
      [page.getByRole("button", { name: /^cancelar$/i }), page.getByText(/^Cancelar$/i)],
      "Cancelar modal"
    );

    return statusLine("Modal validated", `Screenshot: ${screenshotPath}`);
    },
    page
  );

  await runStep(
    report,
    "Administrar Negocios view",
    async () => {
    if (preconditionError) {
      throw new Error(preconditionError);
    }

    await clickFirstVisible(
      page,
      [
        page.getByRole("button", { name: /^mi negocio$/i }),
        page.getByRole("link", { name: /^mi negocio$/i }),
        page.getByText(/^Mi Negocio$/i)
      ],
      "Mi Negocio menu (if collapsed)"
    );

    await clickFirstVisible(
      page,
      [
        page.getByRole("button", { name: /^administrar negocios$/i }),
        page.getByRole("link", { name: /^administrar negocios$/i }),
        page.getByText(/^Administrar Negocios$/i)
      ],
      "Administrar Negocios"
    );

    await validateTextVisible(page, /^Información General$/i, "Información General section");
    await validateTextVisible(page, /^Detalles de la Cuenta$/i, "Detalles de la Cuenta section");
    await validateTextVisible(page, /^Tus Negocios$/i, "Tus Negocios section");
    await validateTextVisible(page, /Sección Legal/i, "Sección Legal section");
    const screenshotPath = await screenshot(page, "04_administrar_negocios_page.png", true);
    return statusLine("Account page validated", `Screenshot: ${screenshotPath}`);
    },
    page
  );

  await runStep(
    report,
    "Información General",
    async () => {
    if (preconditionError) {
      throw new Error(preconditionError);
    }

    const emailLocator = await firstVisibleLocator([page.getByText(EXPECTED_EMAIL, { exact: true }), page.getByText(/@/)]);
    if (!emailLocator) {
      throw new Error(`Expected user email not visible: ${EXPECTED_EMAIL}`);
    }

    const nameLocator = await firstVisibleLocator([page.getByText(EXPECTED_NAME_REGEX)]);
    if (!nameLocator) {
      throw new Error("Expected user name not visible");
    }

    await validateTextVisible(page, /BUSINESS PLAN/i, "BUSINESS PLAN text");
    await validateTextVisible(page, /^Cambiar Plan$/i, "Cambiar Plan button");
    return "User profile and plan info visible";
    },
    page
  );

  await runStep(
    report,
    "Detalles de la Cuenta",
    async () => {
    if (preconditionError) {
      throw new Error(preconditionError);
    }

    await validateTextVisible(page, /Cuenta creada/i, "Cuenta creada text");
    await validateTextVisible(page, /Estado activo/i, "Estado activo text");
    await validateTextVisible(page, /Idioma seleccionado/i, "Idioma seleccionado text");
    return "Account details fields visible";
    },
    page
  );

  await runStep(
    report,
    "Tus Negocios",
    async () => {
    if (preconditionError) {
      throw new Error(preconditionError);
    }

    await validateTextVisible(page, /^Tus Negocios$/i, "Tus Negocios section");
    await validateTextVisible(page, /^Agregar Negocio$/i, "Agregar Negocio button");
    await validateTextVisible(page, /Tienes 2 de 3 negocios/i, "Tienes 2 de 3 negocios text");
    return "Business list and capacity text visible";
    },
    page
  );

  const appReturnUrl = page.url();

  await runStep(
    report,
    "Términos y Condiciones",
    async () => {
    if (preconditionError) {
      throw new Error(preconditionError);
    }

    const legalResult = await openLegalLinkAndValidate({
      page,
      linkPattern: /T[eé]rminos y Condiciones/i,
      headingPattern: /T[eé]rminos y Condiciones/i,
      evidenceFile: "05_terminos_y_condiciones.png",
      fallbackReturnUrl: appReturnUrl
    });

    return statusLine(
      "Legal page validated",
      `Screenshot: ${legalResult.screenshotPath}, URL: ${legalResult.finalUrl}`
    );
    },
    page
  );

  await runStep(
    report,
    "Política de Privacidad",
    async () => {
    if (preconditionError) {
      throw new Error(preconditionError);
    }

    const legalResult = await openLegalLinkAndValidate({
      page,
      linkPattern: /Pol[ií]tica de Privacidad/i,
      headingPattern: /Pol[ií]tica de Privacidad/i,
      evidenceFile: "06_politica_de_privacidad.png",
      fallbackReturnUrl: appReturnUrl
    });

    return statusLine(
      "Privacy page validated",
      `Screenshot: ${legalResult.screenshotPath}, URL: ${legalResult.finalUrl}`
    );
    },
    page
  );

  await writeReport(report);
  // eslint-disable-next-line no-console
  console.table(report);
  // eslint-disable-next-line no-console
  console.log(`Final report saved to: ${REPORT_PATH}`);

  const failedSteps = Object.entries(report)
    .filter(([, value]) => value.status !== "PASS")
    .map(([key]) => key);

  expect(failedSteps, `Workflow failed steps: ${failedSteps.join(", ")}`).toEqual([]);
});
