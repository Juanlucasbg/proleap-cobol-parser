const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");
const path = require("path");

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

function buildInitialReport() {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
}

function resolveSaleadsUrl() {
  const envCandidates = ["SALEADS_URL", "SALEADS_BASE_URL", "BASE_URL", "APP_URL"];
  const firstNonEmpty = envCandidates
    .map((name) => process.env[name])
    .find((value) => typeof value === "string" && value.trim().length > 0);
  return firstNonEmpty ? firstNonEmpty.trim() : null;
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
}

async function pickFirstVisible(candidates) {
  for (const locator of candidates) {
    const first = locator.first();
    const count = await first.count().catch(() => 0);
    if (!count) {
      continue;
    }

    const visible = await first.isVisible().catch(() => false);
    if (visible) {
      return first;
    }
  }

  return null;
}

async function requireVisible(candidates, description) {
  const locator = await pickFirstVisible(candidates);
  if (!locator) {
    throw new Error(`Could not find visible element: ${description}`);
  }
  await expect(locator, `Element not visible: ${description}`).toBeVisible();
  return locator;
}

async function clickAndWait(page, locator) {
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click();
  await waitForUi(page);
}

async function runValidation(report, field, failures, fn) {
  try {
    await fn();
    report[field] = "PASS";
  } catch (error) {
    report[field] = "FAIL";
    failures.push(`${field}: ${error instanceof Error ? error.message : String(error)}`);
  }
}

async function openLegalDocument({
  page,
  context,
  linkRegex,
  headingRegex,
  screenshotPath,
  appReturnLocator,
}) {
  const legalLink = await requireVisible(
    [page.getByRole("link", { name: linkRegex }), page.getByText(linkRegex)],
    `Legal link ${linkRegex}`
  );

  const previousUrl = page.url();
  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await clickAndWait(page, legalLink);

  let targetPage = await popupPromise;
  const openedInNewTab = Boolean(targetPage);

  if (openedInNewTab) {
    await targetPage.waitForLoadState("domcontentloaded", { timeout: 20000 });
    await targetPage.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  } else {
    targetPage = page;
    await waitForUi(targetPage);
  }

  await requireVisible(
    [targetPage.getByRole("heading", { name: headingRegex }), targetPage.getByText(headingRegex)],
    `Heading ${headingRegex}`
  );

  const legalText = (await targetPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
  if (legalText.length < 120) {
    throw new Error("Expected legal content text to be visible and non-trivial.");
  }

  await targetPage.screenshot({ path: screenshotPath, fullPage: true });
  const finalUrl = targetPage.url();

  if (openedInNewTab) {
    await targetPage.close().catch(() => {});
    await page.bringToFront();
    await waitForUi(page);
  } else if (page.url() !== previousUrl) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(page);
  }

  if (appReturnLocator) {
    await expect(appReturnLocator).toBeVisible();
  }

  return finalUrl;
}

test.describe("SaleADS - Mi Negocio full workflow", () => {
  test("login with Google and validate full Mi Negocio workflow", async ({ page, context }) => {
    test.setTimeout(300000);

    const report = buildInitialReport();
    const failures = [];
    const legalUrls = {
      terminosYCondiciones: null,
      politicaDePrivacidad: null,
    };

    const artifactDir = path.join("e2e-artifacts", "saleads-mi-negocio");
    await fs.mkdir(artifactDir, { recursive: true });

    const configuredUrl = resolveSaleadsUrl();
    if (configuredUrl) {
      await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "No SaleADS URL detected. Set SALEADS_URL (or SALEADS_BASE_URL/BASE_URL/APP_URL) to run in any environment."
      );
    }

    await runValidation(report, "Login", failures, async () => {
      const loginButton = await requireVisible(
        [
          page.getByRole("button", { name: /google|sign in with google|iniciar sesión con google/i }),
          page.getByRole("link", { name: /google|sign in with google|iniciar sesión con google/i }),
          page.getByText(/google/i),
        ],
        "Login with Google button"
      );

      const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await clickAndWait(page, loginButton);
      const googlePage = await popupPromise;

      if (googlePage) {
        await googlePage.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
        await googlePage.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});

        const accountOption = await pickFirstVisible([
          googlePage.getByText("juanlucasbarbiergarzon@gmail.com", { exact: false }),
          googlePage.getByRole("button", { name: /juanlucasbarbiergarzon@gmail\.com/i }),
        ]);
        if (accountOption) {
          await clickAndWait(googlePage, accountOption);
        }

        await googlePage.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
      } else {
        const accountOption = await pickFirstVisible([
          page.getByText("juanlucasbarbiergarzon@gmail.com", { exact: false }),
          page.getByRole("button", { name: /juanlucasbarbiergarzon@gmail\.com/i }),
        ]);
        if (accountOption) {
          await clickAndWait(page, accountOption);
        }
      }

      await waitForUi(page);

      const sidebar = await requireVisible(
        [
          page.getByRole("navigation").filter({ hasText: /negocio|mi negocio/i }),
          page.locator("aside").filter({ hasText: /negocio|mi negocio/i }),
          page.getByText(/mi negocio|negocio/i),
        ],
        "Main app sidebar navigation"
      );
      await expect(sidebar).toBeVisible();
      await page.screenshot({ path: path.join(artifactDir, "01-dashboard-loaded.png"), fullPage: true });
    });

    await runValidation(report, "Mi Negocio menu", failures, async () => {
      const negocioMenu = await requireVisible(
        [
          page.getByRole("button", { name: /^Negocio$/i }),
          page.getByRole("link", { name: /^Negocio$/i }),
          page.getByText(/^Negocio$/i),
        ],
        "Negocio menu section"
      );
      await clickAndWait(page, negocioMenu);

      const miNegocioOption = await requireVisible(
        [
          page.getByRole("button", { name: /^Mi Negocio$/i }),
          page.getByRole("link", { name: /^Mi Negocio$/i }),
          page.getByText(/^Mi Negocio$/i),
        ],
        "Mi Negocio option"
      );
      await clickAndWait(page, miNegocioOption);

      await expect(page.getByText("Agregar Negocio", { exact: false })).toBeVisible();
      await expect(page.getByText("Administrar Negocios", { exact: false })).toBeVisible();

      await page.screenshot({ path: path.join(artifactDir, "02-mi-negocio-expanded-menu.png"), fullPage: true });
    });

    await runValidation(report, "Agregar Negocio modal", failures, async () => {
      const addBusinessOption = await requireVisible(
        [
          page.getByRole("button", { name: /^Agregar Negocio$/i }),
          page.getByRole("link", { name: /^Agregar Negocio$/i }),
          page.getByText(/^Agregar Negocio$/i),
        ],
        "Agregar Negocio option"
      );
      await clickAndWait(page, addBusinessOption);

      const modalTitle = await requireVisible(
        [
          page.getByRole("heading", { name: "Crear Nuevo Negocio" }),
          page.getByText("Crear Nuevo Negocio", { exact: false }),
        ],
        "Crear Nuevo Negocio modal title"
      );
      await expect(modalTitle).toBeVisible();
      await expect(page.getByLabel("Nombre del Negocio", { exact: false })).toBeVisible();
      await expect(page.getByText("Tienes 2 de 3 negocios", { exact: false })).toBeVisible();
      await expect(page.getByRole("button", { name: "Cancelar", exact: false })).toBeVisible();
      await expect(page.getByRole("button", { name: "Crear Negocio", exact: false })).toBeVisible();

      await page.screenshot({ path: path.join(artifactDir, "03-agregar-negocio-modal.png"), fullPage: true });

      const businessNameInput = await requireVisible(
        [
          page.getByLabel("Nombre del Negocio", { exact: false }),
          page.getByPlaceholder("Nombre del Negocio", { exact: false }),
          page.locator("input[type='text'], input:not([type]), textarea"),
        ],
        "Nombre del Negocio input"
      );
      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatización");
      await waitForUi(page);

      const cancelButton = await requireVisible(
        [page.getByRole("button", { name: "Cancelar", exact: false })],
        "Cancelar modal button"
      );
      await clickAndWait(page, cancelButton);
    });

    await runValidation(report, "Administrar Negocios view", failures, async () => {
      const miNegocioOption = await requireVisible(
        [
          page.getByRole("button", { name: /^Mi Negocio$/i }),
          page.getByRole("link", { name: /^Mi Negocio$/i }),
          page.getByText(/^Mi Negocio$/i),
        ],
        "Mi Negocio option before Administrar Negocios"
      );
      await clickAndWait(page, miNegocioOption);

      const adminBusinesses = await requireVisible(
        [
          page.getByRole("link", { name: /^Administrar Negocios$/i }),
          page.getByRole("button", { name: /^Administrar Negocios$/i }),
          page.getByText(/^Administrar Negocios$/i),
        ],
        "Administrar Negocios option"
      );
      await clickAndWait(page, adminBusinesses);

      await expect(page.getByRole("heading", { name: /Información General/i })).toBeVisible();
      await expect(page.getByRole("heading", { name: /Detalles de la Cuenta/i })).toBeVisible();
      await expect(page.getByRole("heading", { name: /Tus Negocios/i })).toBeVisible();
      await expect(page.getByRole("heading", { name: /Sección Legal/i })).toBeVisible();

      await page.screenshot({ path: path.join(artifactDir, "04-administrar-negocios-page.png"), fullPage: true });
    });

    await runValidation(report, "Información General", failures, async () => {
      await expect(page.getByRole("heading", { name: /Información General/i })).toBeVisible();
      await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

      const bodyText = await page.locator("body").innerText();
      const emailMatch = bodyText.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
      if (!emailMatch) {
        throw new Error("Expected user email to be visible in Información General.");
      }

      const knownLabels = /Información General|BUSINESS PLAN|Cambiar Plan|Detalles de la Cuenta|Tus Negocios|Sección Legal/i;
      const possibleNameLine = bodyText
        .split("\n")
        .map((line) => line.trim())
        .find((line) => {
          if (!line || knownLabels.test(line) || line.includes("@")) {
            return false;
          }
          return /^[A-Za-zÀ-ÿ' -]{3,}$/.test(line);
        });

      if (!possibleNameLine) {
        throw new Error("Expected user name to be visible in Información General.");
      }
    });

    await runValidation(report, "Detalles de la Cuenta", failures, async () => {
      await expect(page.getByRole("heading", { name: /Detalles de la Cuenta/i })).toBeVisible();
      await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
      await expect(page.getByText(/Estado activo/i)).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
    });

    await runValidation(report, "Tus Negocios", failures, async () => {
      const businessesHeading = await requireVisible(
        [page.getByRole("heading", { name: /Tus Negocios/i }), page.getByText(/Tus Negocios/i)],
        "Tus Negocios section heading"
      );
      await expect(businessesHeading).toBeVisible();
      await expect(page.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible();
      await expect(page.getByText("Tienes 2 de 3 negocios", { exact: false })).toBeVisible();

      const sectionContainer = businessesHeading.locator(
        "xpath=ancestor::*[self::section or self::div][1]"
      );
      const structuredItemsCount = await sectionContainer
        .locator("li, [role='row'], article, [data-testid*='business']")
        .count()
        .catch(() => 0);

      if (structuredItemsCount === 0) {
        const sectionText = await sectionContainer.innerText().catch(() => "");
        const lines = sectionText
          .split("\n")
          .map((line) => line.trim())
          .filter(Boolean)
          .filter(
            (line) => !/Tus Negocios|Agregar Negocio|Tienes 2 de 3 negocios|BUSINESS PLAN|Cambiar Plan/i.test(line)
          );
        if (lines.length === 0) {
          throw new Error("Expected at least one visible business entry in Tus Negocios.");
        }
      }
    });

    const legalSectionLocator = page.getByRole("heading", { name: /Sección Legal/i });

    await runValidation(report, "Términos y Condiciones", failures, async () => {
      legalUrls.terminosYCondiciones = await openLegalDocument({
        page,
        context,
        linkRegex: /Términos y Condiciones/i,
        headingRegex: /Términos y Condiciones/i,
        screenshotPath: path.join(artifactDir, "05-terminos-y-condiciones.png"),
        appReturnLocator: legalSectionLocator,
      });
    });

    await runValidation(report, "Política de Privacidad", failures, async () => {
      legalUrls.politicaDePrivacidad = await openLegalDocument({
        page,
        context,
        linkRegex: /Política de Privacidad/i,
        headingRegex: /Política de Privacidad/i,
        screenshotPath: path.join(artifactDir, "06-politica-de-privacidad.png"),
        appReturnLocator: legalSectionLocator,
      });
    });

    const finalReport = {
      generatedAt: new Date().toISOString(),
      report,
      legalUrls,
      failures,
    };

    await fs.writeFile(
      path.join(artifactDir, "final-report.json"),
      `${JSON.stringify(finalReport, null, 2)}\n`,
      "utf8"
    );

    console.log("SaleADS Mi Negocio final report:");
    console.table(report);
    console.log("Legal URLs:", legalUrls);
    if (failures.length > 0) {
      console.log("Failures:", failures);
    }

    expect(
      failures,
      `One or more SaleADS Mi Negocio validations failed:\n${failures.join("\n")}`
    ).toHaveLength(0);
  });
});
