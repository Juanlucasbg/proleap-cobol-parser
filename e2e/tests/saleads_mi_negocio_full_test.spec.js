const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");
const path = require("path");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const REQUIRED_REPORT_FIELDS = [
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

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const loginUrl =
    process.env.SALEADS_LOGIN_URL ||
    process.env.SALEADS_URL ||
    process.env.BASE_URL;

  const report = Object.fromEntries(
    REQUIRED_REPORT_FIELDS.map((name) => [
      name,
      { status: "FAIL", details: "Not executed" },
    ]),
  );

  const evidence = {
    screenshots: [],
    finalUrls: {},
  };

  const markStep = (name, passed, details) => {
    report[name] = {
      status: passed ? "PASS" : "FAIL",
      details,
    };
  };

  const runStep = async (name, fn) => {
    try {
      await fn();
      markStep(name, true, "Validation completed successfully.");
    } catch (error) {
      markStep(name, false, error.message);
    }
  };

  const checkpointScreenshot = async (label, currentPage = page) => {
    const safe = label.toLowerCase().replace(/[^a-z0-9]+/g, "-");
    const screenshotPath = testInfo.outputPath(`${safe}.png`);
    await currentPage.screenshot({ path: screenshotPath, fullPage: true });
    evidence.screenshots.push({
      checkpoint: label,
      path: screenshotPath,
    });
  };

  try {
    if (!loginUrl) {
      throw new Error(
        "No login URL provided. Set SALEADS_LOGIN_URL, SALEADS_URL, or BASE_URL.",
      );
    }

    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUiLoad(page);

    await runStep("Login", async () => {
      const sidebarMarker = page.getByText("Negocio", { exact: true }).first();
      if (!(await isVisible(sidebarMarker, 3000))) {
        const googleButton = await getByVisibleText(page, [
          "Sign in with Google",
          "Iniciar sesión con Google",
          "Continuar con Google",
          "Google",
        ]);

        const popupPromise = context
          .waitForEvent("page", { timeout: 8000 })
          .catch(() => null);

        await clickAndWait(googleButton, page);
        const popup = await popupPromise;

        if (popup) {
          await popup.waitForLoadState("domcontentloaded");

          const accountOption = popup
            .getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true })
            .first();
          if (await isVisible(accountOption, 5000)) {
            await accountOption.click();
          }

          await popup.waitForTimeout(2000);
          await page.bringToFront();
        } else {
          const inlineAccountOption = page
            .getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true })
            .first();
          if (await isVisible(inlineAccountOption, 5000)) {
            await clickAndWait(inlineAccountOption, page);
          }
        }
      }

      await expect(
        page.getByText("Negocio", { exact: true }).first(),
      ).toBeVisible({ timeout: 60000 });

      const sidebar = page.locator("aside, nav").filter({ hasText: "Negocio" });
      await expect(sidebar.first()).toBeVisible({ timeout: 30000 });

      await checkpointScreenshot("dashboard-loaded");
    });

    await runStep("Mi Negocio menu", async () => {
      const miNegocio = await getByVisibleText(page, ["Mi Negocio"]);
      await clickAndWait(miNegocio, page);

      await expect(page.getByText("Agregar Negocio", { exact: true })).toBeVisible(
        { timeout: 15000 },
      );
      await expect(
        page.getByText("Administrar Negocios", { exact: true }),
      ).toBeVisible({ timeout: 15000 });

      await checkpointScreenshot("mi-negocio-menu-expanded");
    });

    await runStep("Agregar Negocio modal", async () => {
      const agregarNegocioMenu = page.getByText("Agregar Negocio", {
        exact: true,
      });
      await clickAndWait(agregarNegocioMenu.first(), page);

      await expect(
        page.getByText("Crear Nuevo Negocio", { exact: true }),
      ).toBeVisible({ timeout: 15000 });
      await expect(
        page.getByRole("textbox", { name: "Nombre del Negocio" }),
      ).toBeVisible({ timeout: 15000 });
      await expect(page.getByText("Tienes 2 de 3 negocios", { exact: true })).toBeVisible(
        { timeout: 15000 },
      );
      await expect(page.getByText("Cancelar", { exact: true })).toBeVisible();
      await expect(page.getByText("Crear Negocio", { exact: true })).toBeVisible();

      await checkpointScreenshot("agregar-negocio-modal");

      const nombreInput = page.getByRole("textbox", { name: "Nombre del Negocio" });
      await nombreInput.click();
      await nombreInput.fill("Negocio Prueba Automatización");

      await clickAndWait(page.getByText("Cancelar", { exact: true }).first(), page);
      await expect(
        page.getByText("Crear Nuevo Negocio", { exact: true }),
      ).toBeHidden({ timeout: 15000 });
    });

    await runStep("Administrar Negocios view", async () => {
      if (!(await isVisible(page.getByText("Administrar Negocios", { exact: true }), 2000))) {
        const miNegocio = await getByVisibleText(page, ["Mi Negocio"]);
        await clickAndWait(miNegocio, page);
      }

      await clickAndWait(
        page.getByText("Administrar Negocios", { exact: true }).first(),
        page,
      );

      await expect(page.getByText("Información General", { exact: true })).toBeVisible({
        timeout: 30000,
      });
      await expect(
        page.getByText("Detalles de la Cuenta", { exact: true }),
      ).toBeVisible();
      await expect(page.getByText("Tus Negocios", { exact: true })).toBeVisible();
      await expect(page.getByText("Sección Legal", { exact: true })).toBeVisible();

      await checkpointScreenshot("administrar-negocios-account-page");
    });

    await runStep("Información General", async () => {
      await expect(page.getByText("Información General", { exact: true })).toBeVisible();

      const bodyText = (await page.locator("body").innerText()).replace(/\s+/g, " ");
      if (!/\b\S+@\S+\.\S+\b/.test(bodyText)) {
        throw new Error("User email was not detected on the page.");
      }

      await expect(page.getByText("BUSINESS PLAN", { exact: true })).toBeVisible();
      await expect(page.getByText("Cambiar Plan", { exact: true })).toBeVisible();
    });

    await runStep("Detalles de la Cuenta", async () => {
      await expect(page.getByText("Cuenta creada", { exact: true })).toBeVisible();
      await expect(page.getByText("Estado activo", { exact: true })).toBeVisible();
      await expect(page.getByText("Idioma seleccionado", { exact: true })).toBeVisible();
    });

    await runStep("Tus Negocios", async () => {
      await expect(page.getByText("Tus Negocios", { exact: true })).toBeVisible();
      await expect(page.getByText("Agregar Negocio", { exact: true })).toBeVisible();
      await expect(page.getByText("Tienes 2 de 3 negocios", { exact: true })).toBeVisible();
    });

    await runStep("Términos y Condiciones", async () => {
      const finalUrl = await validateLegalPage({
        page,
        context,
        linkText: "Términos y Condiciones",
        headingPattern: /Términos y Condiciones/i,
        screenshotName: "terminos-y-condiciones",
        checkpointScreenshot,
      });
      evidence.finalUrls["Términos y Condiciones"] = finalUrl;
    });

    await runStep("Política de Privacidad", async () => {
      const finalUrl = await validateLegalPage({
        page,
        context,
        linkText: "Política de Privacidad",
        headingPattern: /Política de Privacidad/i,
        screenshotName: "politica-de-privacidad",
        checkpointScreenshot,
      });
      evidence.finalUrls["Política de Privacidad"] = finalUrl;
    });
  } finally {
    const failedSections = Object.entries(report)
      .filter(([, value]) => value.status === "FAIL")
      .map(([name]) => name);

    const finalPayload = {
      name: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      report,
      evidence,
      overallStatus: failedSections.length === 0 ? "PASS" : "FAIL",
      failedSections,
    };

    const reportPath = testInfo.outputPath("saleads-mi-negocio-report.json");
    await fs.writeFile(reportPath, JSON.stringify(finalPayload, null, 2), "utf8");
    await testInfo.attach("saleads-mi-negocio-report", {
      path: reportPath,
      contentType: "application/json",
    });

    const artifactDir = path.resolve(process.cwd(), "e2e", "artifacts");
    await fs.mkdir(artifactDir, { recursive: true });
    await fs.writeFile(
      path.join(artifactDir, "saleads-mi-negocio-latest-report.json"),
      JSON.stringify(finalPayload, null, 2),
      "utf8",
    );
  }

  const failed = Object.values(report).filter((value) => value.status === "FAIL");
  expect(
    failed,
    "One or more validations failed. Check the attached JSON report and screenshots.",
  ).toHaveLength(0);
});

async function clickAndWait(locator, page) {
  await expect(locator.first()).toBeVisible({ timeout: 15000 });
  await locator.first().click();
  await waitForUiLoad(page);
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function isVisible(locator, timeoutMs = 3000) {
  try {
    await expect(locator).toBeVisible({ timeout: timeoutMs });
    return true;
  } catch {
    return false;
  }
}

async function getByVisibleText(page, labels) {
  for (const label of labels) {
    const byRoleButton = page.getByRole("button", { name: new RegExp(label, "i") });
    if (await isVisible(byRoleButton.first(), 1000)) {
      return byRoleButton.first();
    }

    const byRoleLink = page.getByRole("link", { name: new RegExp(label, "i") });
    if (await isVisible(byRoleLink.first(), 1000)) {
      return byRoleLink.first();
    }

    const byText = page.getByText(label, { exact: true });
    if (await isVisible(byText.first(), 1000)) {
      return byText.first();
    }
  }

  throw new Error(`Unable to find a visible element using labels: ${labels.join(", ")}`);
}

async function validateLegalPage({
  page,
  context,
  linkText,
  headingPattern,
  screenshotName,
  checkpointScreenshot,
}) {
  const appPage = page;
  const existingPages = new Set(context.pages());
  const originalUrl = appPage.url();

  const link = await getByVisibleText(appPage, [linkText]);
  await clickAndWait(link, appPage);

  await appPage.waitForTimeout(1500);
  const newTab = context.pages().find((candidate) => !existingPages.has(candidate));
  const targetPage = newTab || appPage;
  await targetPage.waitForLoadState("domcontentloaded");
  await waitForUiLoad(targetPage);

  const headingByRole = targetPage.getByRole("heading", { name: headingPattern }).first();
  if (await isVisible(headingByRole, 5000)) {
    await expect(headingByRole).toBeVisible();
  } else {
    await expect(targetPage.getByText(headingPattern).first()).toBeVisible({ timeout: 10000 });
  }

  await expect(targetPage.locator("body")).toContainText(
    /(legal|política|privacidad|términos|condiciones)/i,
  );

  await checkpointScreenshot(screenshotName, targetPage);

  const finalUrl = targetPage.url();

  if (newTab) {
    await newTab.close();
    await appPage.bringToFront();
    await waitForUiLoad(appPage);
  } else if (appPage.url() !== originalUrl) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUiLoad(appPage);
  }

  return finalUrl;
}
