const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");
const path = require("path");

const ARTIFACTS_DIR = path.resolve(process.cwd(), "artifacts", "saleads-mi-negocio");
const FINAL_REPORT_PATH = path.resolve(ARTIFACTS_DIR, "final-report.json");
const TEST_START_URL = process.env.SALEADS_START_URL || process.env.BASE_URL || "";
const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";

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

const report = {
  runAtUtc: new Date().toISOString(),
  startUrl: TEST_START_URL,
  screenshots: [],
  finalUrls: {},
  steps: {
    Login: { pass: false, details: "" },
    "Mi Negocio menu": { pass: false, details: "" },
    "Agregar Negocio modal": { pass: false, details: "" },
    "Administrar Negocios view": { pass: false, details: "" },
    "Información General": { pass: false, details: "" },
    "Detalles de la Cuenta": { pass: false, details: "" },
    "Tus Negocios": { pass: false, details: "" },
    "Términos y Condiciones": { pass: false, details: "" },
    "Política de Privacidad": { pass: false, details: "" },
  },
};

const byTextExact = (page, text) =>
  page.getByText(new RegExp(`^\\s*${escapeRegex(text)}\\s*$`, "i"));

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function slugify(text) {
  return text
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function formatError(error) {
  if (!error) {
    return "Unknown error";
  }
  const message = error.message || String(error);
  return message.length > 500 ? `${message.slice(0, 497)}...` : message;
}

function markNotExecuted(stepKeys, reason) {
  for (const key of stepKeys) {
    if (!report.steps[key].pass && !report.steps[key].details) {
      report.steps[key].details = reason;
    }
  }
}

async function ensureArtifactsDir() {
  await fs.mkdir(ARTIFACTS_DIR, { recursive: true });
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(400);
  await page.waitForLoadState("networkidle", { timeout: 6000 }).catch(() => {});
}

async function checkpointScreenshot(page, fileName) {
  await ensureArtifactsDir();
  const outputPath = path.resolve(ARTIFACTS_DIR, fileName);
  await page.screenshot({ path: outputPath, fullPage: true });
  report.screenshots.push(outputPath);
}

async function findFirstVisibleLocator(candidates) {
  for (const candidate of candidates) {
    if (!candidate) {
      continue;
    }
    const count = await candidate.count();
    if (count < 1) {
      continue;
    }
    const first = candidate.first();
    if (await first.isVisible()) {
      return first;
    }
  }
  return null;
}

async function clickVisible(page, targets) {
  const target = await findFirstVisibleLocator(targets);
  if (!target) {
    return false;
  }
  await target.click();
  await waitForUi(page);
  return true;
}

async function validateVisible(locator, errorMessage) {
  expect(locator, errorMessage).toBeTruthy();
  await expect(locator, errorMessage).toBeVisible();
}

async function runStep(page, stepKey, fn) {
  try {
    await test.step(stepKey, fn);
    return true;
  } catch (error) {
    report.steps[stepKey] = {
      pass: false,
      details: formatError(error),
    };
    try {
      await checkpointScreenshot(page, `fail-${slugify(stepKey)}.png`);
    } catch (_) {
      // ignore screenshot failures on cleanup path
    }
    return false;
  }
}

async function getNombreDelNegocioField(page) {
  const candidates = [
    page.getByLabel(/nombre del negocio/i),
    page.getByPlaceholder(/nombre del negocio/i),
    page.locator('input[name*="nombre"], input[id*="nombre"]'),
    page.locator("input"),
  ];
  return findFirstVisibleLocator(candidates);
}

async function clickLegalLinkAndValidate({
  page,
  linkText,
  expectedHeadingText,
  reportKey,
  screenshotName,
  urlKey,
}) {
  const appUrlBeforeClick = page.url();
  const link = await findFirstVisibleLocator([
    page.getByRole("link", { name: new RegExp(linkText, "i") }),
    page.getByRole("button", { name: new RegExp(linkText, "i") }),
    page.getByText(new RegExp(linkText, "i")),
  ]);
  expect(link, `Missing legal link: ${linkText}`).toBeTruthy();

  const maybePopup = page.waitForEvent("popup", { timeout: 6000 }).catch(() => null);
  await link.click();
  const popup = await maybePopup;

  let legalPage = page;
  if (popup) {
    legalPage = popup;
  }
  await waitForUi(legalPage);

  const headingLocator = legalPage.getByRole("heading", {
    name: new RegExp(expectedHeadingText, "i"),
  });
  const fallbackHeading = legalPage.getByText(new RegExp(expectedHeadingText, "i")).first();
  const headingFound =
    (await headingLocator.count()) > 0 ? headingLocator.first() : fallbackHeading;

  await validateVisible(
    headingFound,
    `Expected heading not visible: ${expectedHeadingText}`
  );

  const legalTextCandidates = [
    legalPage.getByText(/t[eé]rminos|condiciones|pol[ií]tica|privacidad|uso/i),
    legalPage.locator("main, article, section"),
    legalPage.locator("body"),
  ];
  const legalContainer = await findFirstVisibleLocator(legalTextCandidates);
  expect(legalContainer, `Expected legal content on ${linkText}`).toBeTruthy();

  await checkpointScreenshot(legalPage, screenshotName);
  report.finalUrls[urlKey] = legalPage.url();
  report.steps[reportKey] = {
    pass: true,
    details: "Heading and legal content validated; URL captured.",
  };

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
    return;
  }

  // Cleanup when legal page opened in the same tab.
  await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
    await page.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" });
  });
  await waitForUi(page);
}

test.afterAll(async () => {
  const failedSteps = STEP_KEYS.filter((stepKey) => !report.steps[stepKey].pass);
  report.finalStatus = failedSteps.length === 0 ? "PASS" : "FAIL";
  report.failedSteps = failedSteps;
  await ensureArtifactsDir();
  await fs.writeFile(FINAL_REPORT_PATH, JSON.stringify(report, null, 2), "utf8");
});

test("saleads_mi_negocio_full_test", async ({ page }) => {
  test.setTimeout(300000);
  await ensureArtifactsDir();
  if (TEST_START_URL) {
    await page.goto(TEST_START_URL, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  const loginOk = await runStep(page, "Login", async () => {
    const googlePopupPromise = page.waitForEvent("popup", { timeout: 6000 }).catch(() => null);
    const loginClicked = await clickVisible(page, [
      page.getByRole("button", { name: /sign in with google|continuar con google|google/i }),
      page.getByRole("link", { name: /sign in with google|continuar con google|google/i }),
      page.getByText(/sign in with google|continuar con google/i),
    ]);
    expect(loginClicked, "Could not find a Google login trigger").toBeTruthy();

    const googlePopup = await googlePopupPromise;
    if (googlePopup) {
      await waitForUi(googlePopup);
      const accountOption = googlePopup
        .getByText(new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i"))
        .first();
      if ((await accountOption.count()) > 0 && (await accountOption.isVisible())) {
        await accountOption.click();
        await waitForUi(googlePopup);
      }
      await page.bringToFront();
      await waitForUi(page);
    } else {
      const googleOption = page
        .getByText(new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i"))
        .first();
      if ((await googleOption.count()) > 0 && (await googleOption.isVisible())) {
        await googleOption.click();
        await waitForUi(page);
      }
    }

    const sidebar = await findFirstVisibleLocator([
      page.locator("aside"),
      page.locator("nav"),
      page.getByRole("navigation"),
    ]);
    await validateVisible(sidebar, "Left sidebar navigation is not visible after login.");
    await checkpointScreenshot(page, "01-dashboard-loaded.png");
    report.steps.Login = {
      pass: true,
      details: "Main interface and sidebar became visible after Google login.",
    };
  });

  if (!loginOk) {
    markNotExecuted(
      STEP_KEYS.filter((key) => key !== "Login"),
      "Not executed because login step failed."
    );
  } else {
    const menuOk = await runStep(page, "Mi Negocio menu", async () => {
      const negocioClicked = await clickVisible(page, [
        byTextExact(page, "Negocio"),
        byTextExact(page, "Mi Negocio"),
        page.getByRole("button", { name: /negocio|mi negocio/i }),
        page.getByRole("link", { name: /negocio|mi negocio/i }),
      ]);
      expect(negocioClicked, "Could not open Negocio / Mi Negocio section").toBeTruthy();

      await validateVisible(
        page.getByText(/agregar negocio/i).first(),
        "Agregar Negocio is not visible in submenu."
      );
      await validateVisible(
        page.getByText(/administrar negocios/i).first(),
        "Administrar Negocios is not visible in submenu."
      );
      await checkpointScreenshot(page, "02-mi-negocio-menu-expanded.png");
      report.steps["Mi Negocio menu"] = {
        pass: true,
        details: "Submenu expanded with Agregar Negocio and Administrar Negocios.",
      };
    });

    if (!menuOk) {
      markNotExecuted(
        STEP_KEYS.filter((key) => key !== "Login" && key !== "Mi Negocio menu"),
        "Not executed because Mi Negocio menu validation failed."
      );
    } else {
      await runStep(page, "Agregar Negocio modal", async () => {
        await page.getByText(/agregar negocio/i).first().click();
        await waitForUi(page);

        await validateVisible(
          page.getByText(/crear nuevo negocio/i).first(),
          "Crear Nuevo Negocio modal title is not visible."
        );
        const nombreField = await getNombreDelNegocioField(page);
        await validateVisible(nombreField, "Nombre del Negocio field is not visible.");
        await validateVisible(
          page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first(),
          "Business usage text is not visible in modal."
        );
        await validateVisible(
          byTextExact(page, "Cancelar").first(),
          "Cancelar button is missing."
        );
        await validateVisible(
          page.getByRole("button", { name: /crear negocio/i }).first(),
          "Crear Negocio button is missing."
        );

        // Required evidence: screenshot while the modal is visible.
        await checkpointScreenshot(page, "03-agregar-negocio-modal.png");

        await nombreField.click();
        await waitForUi(page);
        await nombreField.fill("Negocio Prueba Automatización");
        await byTextExact(page, "Cancelar").first().click();
        await waitForUi(page);

        report.steps["Agregar Negocio modal"] = {
          pass: true,
          details:
            "Modal validated with required fields/buttons; sample name entered and modal closed with Cancelar.",
        };
      });

      const adminOk = await runStep(page, "Administrar Negocios view", async () => {
        const adminItem = page.getByText(/administrar negocios/i).first();
        if (!(await adminItem.isVisible())) {
          await clickVisible(page, [
            byTextExact(page, "Mi Negocio"),
            byTextExact(page, "Negocio"),
            page.getByRole("button", { name: /mi negocio|negocio/i }),
            page.getByRole("link", { name: /mi negocio|negocio/i }),
          ]);
        }
        await adminItem.click();
        await waitForUi(page);

        await validateVisible(
          page.getByText(/informaci[oó]n general/i).first(),
          "Información General section not visible."
        );
        await validateVisible(
          page.getByText(/detalles de la cuenta/i).first(),
          "Detalles de la Cuenta section not visible."
        );
        await validateVisible(
          page.getByText(/tus negocios/i).first(),
          "Tus Negocios section not visible."
        );
        await validateVisible(
          page.getByText(/secci[oó]n legal/i).first(),
          "Sección Legal section not visible."
        );
        await checkpointScreenshot(page, "04-administrar-negocios-full-page.png");
        report.steps["Administrar Negocios view"] = {
          pass: true,
          details: "Account page loaded with all required sections.",
        };
      });

      if (!adminOk) {
        markNotExecuted(
          [
            "Información General",
            "Detalles de la Cuenta",
            "Tus Negocios",
            "Términos y Condiciones",
            "Política de Privacidad",
          ],
          "Not executed because Administrar Negocios view did not validate."
        );
      } else {
        await runStep(page, "Información General", async () => {
          await validateVisible(
            page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first(),
            "User email not visible in Información General."
          );
          await validateVisible(
            page.getByText(/business plan/i).first(),
            "BUSINESS PLAN label not visible."
          );
          await validateVisible(
            page.getByRole("button", { name: /cambiar plan/i }).first(),
            "Cambiar Plan button not visible."
          );
          const nameCandidate = await findFirstVisibleLocator([
            page.locator("h1, h2, h3, strong, p"),
            page.locator("[data-testid*='name'], [class*='name']"),
          ]);
          await validateVisible(nameCandidate, "User name is not visible.");
          report.steps["Información General"] = {
            pass: true,
            details: "User name, email, BUSINESS PLAN text, and Cambiar Plan validated.",
          };
        });

        await runStep(page, "Detalles de la Cuenta", async () => {
          await validateVisible(
            page.getByText(/cuenta creada/i).first(),
            "Cuenta creada field not visible."
          );
          await validateVisible(
            page.getByText(/estado activo/i).first(),
            "Estado activo field not visible."
          );
          await validateVisible(
            page.getByText(/idioma seleccionado/i).first(),
            "Idioma seleccionado field not visible."
          );
          report.steps["Detalles de la Cuenta"] = {
            pass: true,
            details: "Cuenta creada, Estado activo, and Idioma seleccionado are visible.",
          };
        });

        await runStep(page, "Tus Negocios", async () => {
          await validateVisible(
            page.getByText(/tus negocios/i).first(),
            "Tus Negocios section missing."
          );
          await validateVisible(
            page.getByRole("button", { name: /agregar negocio/i }).first(),
            "Agregar Negocio button missing in Tus Negocios."
          );
          await validateVisible(
            page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first(),
            "Capacity text not visible in Tus Negocios."
          );
          const businessListCandidate = await findFirstVisibleLocator([
            page.locator("ul li"),
            page.locator("table tbody tr"),
            page.locator("[role='row']"),
          ]);
          await validateVisible(businessListCandidate, "Business list is not visible.");
          report.steps["Tus Negocios"] = {
            pass: true,
            details: "Business list, Agregar Negocio button, and usage text validated.",
          };
        });

        await runStep(page, "Términos y Condiciones", async () => {
          await clickLegalLinkAndValidate({
            page,
            linkText: "Términos y Condiciones",
            expectedHeadingText: "Términos y Condiciones",
            reportKey: "Términos y Condiciones",
            screenshotName: "08-terminos-y-condiciones.png",
            urlKey: "terminosYCondiciones",
          });
        });

        await runStep(page, "Política de Privacidad", async () => {
          await clickLegalLinkAndValidate({
            page,
            linkText: "Política de Privacidad",
            expectedHeadingText: "Política de Privacidad",
            reportKey: "Política de Privacidad",
            screenshotName: "09-politica-de-privacidad.png",
            urlKey: "politicaDePrivacidad",
          });
        });
      }
    }
  }

  const failedSteps = STEP_KEYS.filter((stepKey) => !report.steps[stepKey].pass);
  expect(
    failedSteps,
    failedSteps.length
      ? `Workflow validation failures: ${failedSteps.join(", ")}`
      : "All workflow validations passed."
  ).toEqual([]);
});
