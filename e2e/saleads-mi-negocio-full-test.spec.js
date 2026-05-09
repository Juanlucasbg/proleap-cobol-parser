const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");
const path = require("node:path");

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informaci\u00f3n General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "T\u00e9rminos y Condiciones",
  "Pol\u00edtica de Privacidad",
];

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});
  await page.waitForTimeout(600);
}

async function isVisible(locator, timeout = 1_500) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function findFirstVisible(page, candidates, description, timeout = 20_000) {
  const deadline = Date.now() + timeout;

  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      if (await isVisible(candidate, 500)) {
        return candidate.first();
      }
    }
    await page.waitForTimeout(250);
  }

  throw new Error(`Could not find visible element: ${description}`);
}

async function takeCheckpoint(page, checkpointDir, fileName, fullPage = false) {
  await fs.mkdir(checkpointDir, { recursive: true });
  const filePath = path.join(checkpointDir, fileName);
  await page.screenshot({ path: filePath, fullPage });
}

function buildReport() {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = { status: "FAIL", details: "Not executed." };
    return acc;
  }, {});
}

function markPass(report, field, details) {
  report[field] = { status: "PASS", details };
}

function markFail(report, field, error) {
  report[field] = { status: "FAIL", details: error instanceof Error ? error.message : String(error) };
}

function markRemainingAsBlocked(report) {
  for (const field of REPORT_FIELDS) {
    if (report[field].details === "Not executed.") {
      report[field] = { status: "FAIL", details: "Blocked by a previous failed step." };
    }
  }
}

async function selectGoogleAccountIfVisible(page, email) {
  const accountCandidate = page.getByText(new RegExp(email.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i"));
  if (await isVisible(accountCandidate, 8_000)) {
    await accountCandidate.first().click();
    await waitForUi(page);
  }
}

async function assertTextVisible(page, regex, timeout = 20_000) {
  await expect(page.getByText(regex).first()).toBeVisible({ timeout });
}

async function openLegalLinkAndValidate({
  appPage,
  context,
  checkpointDir,
  linkRegex,
  headingRegex,
  screenshotName,
}) {
  const link = await findFirstVisible(
    appPage,
    [
      appPage.getByRole("link", { name: linkRegex }),
      appPage.getByRole("button", { name: linkRegex }),
      appPage.getByText(linkRegex),
    ],
    `Legal link ${linkRegex}`,
    15_000,
  );

  const appUrlBeforeClick = appPage.url();
  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);

  await link.click();
  await waitForUi(appPage);

  const popup = await popupPromise;
  const legalPage = popup || appPage;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
    await waitForUi(popup);
  }

  const heading = await findFirstVisible(
    legalPage,
    [legalPage.getByRole("heading", { name: headingRegex }), legalPage.getByText(headingRegex)],
    `Heading ${headingRegex}`,
    20_000,
  );
  await expect(heading).toBeVisible();

  const legalText = (await legalPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
  if (legalText.length < 120) {
    throw new Error("Legal content is too short; expected full legal text to be visible.");
  }

  await takeCheckpoint(legalPage, checkpointDir, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close().catch(() => {});
    await appPage.bringToFront().catch(() => {});
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      await appPage.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" });
    });
    await waitForUi(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const checkpointDir = path.resolve(
    process.env.SALEADS_EVIDENCE_DIR || "artifacts/saleads_mi_negocio_full_test",
  );
  const report = buildReport();
  const evidence = {};

  let appPage = page;

  try {
    try {
      const loginUrl = process.env.SALEADS_LOGIN_URL;
      if (loginUrl) {
        await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
        await waitForUi(page);
      } else if (page.url() === "about:blank") {
        throw new Error(
          "SALEADS_LOGIN_URL is required when starting from a blank page. Set SALEADS_LOGIN_URL to the current environment login page URL.",
        );
      }

      const loginButton = await findFirstVisible(
        page,
        [
          page.getByRole("button", {
            name: /sign in with google|continue with google|iniciar sesi[o\u00f3]n con google|continuar con google|google/i,
          }),
          page.getByRole("link", {
            name: /sign in with google|continue with google|iniciar sesi[o\u00f3]n con google|continuar con google|google/i,
          }),
          page.getByText(/sign in with google|continue with google|iniciar sesi[o\u00f3]n con google|continuar con google/i),
        ],
        "Google login button",
      );

      const popupPromise = context.waitForEvent("page", { timeout: 12_000 }).catch(() => null);
      await loginButton.click();
      await waitForUi(page);

      const popup = await popupPromise;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
        await waitForUi(popup);
        await selectGoogleAccountIfVisible(popup, ACCOUNT_EMAIL);

        const popupLooksLikeApp = await isVisible(popup.getByText(/mi negocio|negocio/i), 8_000);
        if (popupLooksLikeApp) {
          appPage = popup;
        }
      } else {
        await selectGoogleAccountIfVisible(page, ACCOUNT_EMAIL);
      }

      await assertTextVisible(appPage, /mi negocio|negocio/i, 45_000);
      const sidebar = await findFirstVisible(
        appPage,
        [appPage.locator("aside"), appPage.locator("nav"), appPage.getByText(/mi negocio|negocio/i)],
        "Left sidebar navigation",
        30_000,
      );
      await expect(sidebar).toBeVisible();

      await takeCheckpoint(appPage, checkpointDir, "01-dashboard-loaded.png");
      markPass(report, "Login", "Dashboard and left sidebar are visible after Google login.");
    } catch (error) {
      markFail(report, "Login", error);
      throw error;
    }

    try {
      const negocioSection = await findFirstVisible(
        appPage,
        [
          appPage.getByRole("button", { name: /^Negocio$/i }),
          appPage.getByRole("link", { name: /^Negocio$/i }),
          appPage.getByText(/^Negocio$/i),
        ],
        "Negocio section",
      );
      await negocioSection.click();
      await waitForUi(appPage);

      const miNegocio = await findFirstVisible(
        appPage,
        [
          appPage.getByRole("button", { name: /mi negocio/i }),
          appPage.getByRole("link", { name: /mi negocio/i }),
          appPage.getByText(/mi negocio/i),
        ],
        "Mi Negocio option",
      );
      await miNegocio.click();
      await waitForUi(appPage);

      await assertTextVisible(appPage, /Agregar Negocio/i);
      await assertTextVisible(appPage, /Administrar Negocios/i);

      await takeCheckpoint(appPage, checkpointDir, "02-mi-negocio-expanded-menu.png");
      markPass(report, "Mi Negocio menu", "Mi Negocio submenu expanded with required options.");
    } catch (error) {
      markFail(report, "Mi Negocio menu", error);
      throw error;
    }

    try {
      const addBusinessMenuOption = await findFirstVisible(
        appPage,
        [
          appPage.getByRole("button", { name: /^Agregar Negocio$/i }),
          appPage.getByRole("link", { name: /^Agregar Negocio$/i }),
          appPage.getByText(/^Agregar Negocio$/i),
        ],
        "Agregar Negocio menu option",
      );

      await addBusinessMenuOption.click();
      await waitForUi(appPage);

      await assertTextVisible(appPage, /Crear Nuevo Negocio/i);
      const businessNameInput = await findFirstVisible(
        appPage,
        [
          appPage.getByLabel(/Nombre del Negocio/i),
          appPage.getByPlaceholder(/Nombre del Negocio/i),
          appPage.locator("input[name*=nombre i], input[placeholder*=Nombre i]"),
        ],
        "Nombre del Negocio input",
      );
      await expect(businessNameInput).toBeVisible();
      await assertTextVisible(appPage, /Tienes\s*2\s*de\s*3\s*negocios/i);
      await assertTextVisible(appPage, /^Cancelar$/i);
      await assertTextVisible(appPage, /^Crear Negocio$/i);

      await takeCheckpoint(appPage, checkpointDir, "03-agregar-negocio-modal.png");

      await businessNameInput.fill("Negocio Prueba Automatizacion");
      const cancelButton = await findFirstVisible(
        appPage,
        [appPage.getByRole("button", { name: /^Cancelar$/i }), appPage.getByText(/^Cancelar$/i)],
        "Cancelar button in modal",
      );
      await cancelButton.click();
      await waitForUi(appPage);

      markPass(report, "Agregar Negocio modal", "Modal fields and actions were validated.");
    } catch (error) {
      markFail(report, "Agregar Negocio modal", error);
      throw error;
    }

    try {
      const administrarNegocios = appPage.getByText(/Administrar Negocios/i).first();
      if (!(await isVisible(administrarNegocios, 2_000))) {
        const miNegocioToggle = await findFirstVisible(
          appPage,
          [
            appPage.getByRole("button", { name: /mi negocio/i }),
            appPage.getByRole("link", { name: /mi negocio/i }),
            appPage.getByText(/mi negocio/i),
          ],
          "Mi Negocio toggle",
          12_000,
        );
        await miNegocioToggle.click();
        await waitForUi(appPage);
      }

      const adminOption = await findFirstVisible(
        appPage,
        [
          appPage.getByRole("button", { name: /Administrar Negocios/i }),
          appPage.getByRole("link", { name: /Administrar Negocios/i }),
          appPage.getByText(/Administrar Negocios/i),
        ],
        "Administrar Negocios option",
      );
      await adminOption.click();
      await waitForUi(appPage);

      await assertTextVisible(appPage, /Informaci[o\u00f3]n General/i, 30_000);
      await assertTextVisible(appPage, /Detalles de la Cuenta/i, 30_000);
      await assertTextVisible(appPage, /Tus Negocios/i, 30_000);
      await assertTextVisible(appPage, /Secci[o\u00f3]n Legal/i, 30_000);

      await takeCheckpoint(appPage, checkpointDir, "04-administrar-negocios-account-page.png", true);
      markPass(report, "Administrar Negocios view", "All account page sections are visible.");
    } catch (error) {
      markFail(report, "Administrar Negocios view", error);
      throw error;
    }

    try {
      await assertTextVisible(appPage, /Informaci[o\u00f3]n General/i);
      await assertTextVisible(appPage, /BUSINESS PLAN/i);
      await assertTextVisible(appPage, /Cambiar Plan/i);

      const bodyText = await appPage.locator("body").innerText();
      const hasUserEmail =
        bodyText.includes(ACCOUNT_EMAIL) ||
        /\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/i.test(bodyText);
      if (!hasUserEmail) {
        throw new Error("User email is not visible in Informacion General.");
      }

      const textWithoutKnownLabels = bodyText
        .replace(/Informaci[o\u00f3]n General/gi, "")
        .replace(/BUSINESS PLAN/gi, "")
        .replace(/Cambiar Plan/gi, "")
        .replace(/Detalles de la Cuenta/gi, "")
        .replace(/Tus Negocios/gi, "")
        .replace(/Secci[o\u00f3]n Legal/gi, "");
      const hasLikelyUserName = /[A-Za-z][A-Za-z'.-]{1,}\s+[A-Za-z][A-Za-z'.-]{1,}/.test(textWithoutKnownLabels);
      if (!hasLikelyUserName) {
        throw new Error("A likely user name was not found in the visible account content.");
      }

      markPass(report, "Informaci\u00f3n General", "User name, user email, BUSINESS PLAN, and Cambiar Plan were validated.");
    } catch (error) {
      markFail(report, "Informaci\u00f3n General", error);
      throw error;
    }

    try {
      await assertTextVisible(appPage, /Detalles de la Cuenta/i);
      await assertTextVisible(appPage, /Cuenta creada/i);
      await assertTextVisible(appPage, /Estado activo/i);
      await assertTextVisible(appPage, /Idioma seleccionado/i);

      markPass(report, "Detalles de la Cuenta", "All required account detail labels are visible.");
    } catch (error) {
      markFail(report, "Detalles de la Cuenta", error);
      throw error;
    }

    try {
      await assertTextVisible(appPage, /Tus Negocios/i);
      await assertTextVisible(appPage, /^Agregar Negocio$/i);
      await assertTextVisible(appPage, /Tienes\s*2\s*de\s*3\s*negocios/i);

      const businessListIndicators = appPage.locator("li, [role='listitem'], tr, article, [class*=business i]");
      const indicatorsCount = await businessListIndicators.count();
      if (indicatorsCount === 0) {
        throw new Error("Business list indicators were not found in Tus Negocios.");
      }

      markPass(report, "Tus Negocios", "Business list section, button, and quota text were validated.");
    } catch (error) {
      markFail(report, "Tus Negocios", error);
      throw error;
    }

    try {
      evidence.terminosFinalUrl = await openLegalLinkAndValidate({
        appPage,
        context,
        checkpointDir,
        linkRegex: /T[e\u00e9]rminos y Condiciones/i,
        headingRegex: /T[e\u00e9]rminos y Condiciones/i,
        screenshotName: "05-terminos-y-condiciones.png",
      });
      markPass(
        report,
        "T\u00e9rminos y Condiciones",
        `Legal page validated. Final URL: ${evidence.terminosFinalUrl}`,
      );
    } catch (error) {
      markFail(report, "T\u00e9rminos y Condiciones", error);
      throw error;
    }

    try {
      evidence.politicaFinalUrl = await openLegalLinkAndValidate({
        appPage,
        context,
        checkpointDir,
        linkRegex: /Pol[i\u00ed]tica de Privacidad/i,
        headingRegex: /Pol[i\u00ed]tica de Privacidad/i,
        screenshotName: "06-politica-de-privacidad.png",
      });
      markPass(report, "Pol\u00edtica de Privacidad", `Legal page validated. Final URL: ${evidence.politicaFinalUrl}`);
    } catch (error) {
      markFail(report, "Pol\u00edtica de Privacidad", error);
      throw error;
    }
  } catch {
    markRemainingAsBlocked(report);
  } finally {
    await fs.mkdir(checkpointDir, { recursive: true });
    const reportPayload = {
      name: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      report,
      evidence,
      outputDir: checkpointDir,
    };
    const reportPath = path.join(checkpointDir, "final-report.json");
    await fs.writeFile(reportPath, `${JSON.stringify(reportPayload, null, 2)}\n`, "utf8");

    await testInfo.attach("final-report", {
      path: reportPath,
      contentType: "application/json",
    });
    console.log("SALEADS FINAL REPORT");
    console.log(JSON.stringify(reportPayload, null, 2));
  }

  const failed = Object.entries(report).filter(([, value]) => value.status === "FAIL");
  expect(
    failed,
    `Failing validations: ${failed.map(([step]) => step).join(", ") || "none"}. See final-report.json for details.`,
  ).toEqual([]);
});
