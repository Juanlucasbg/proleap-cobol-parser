const fs = require("node:fs/promises");
const { test, expect } = require("@playwright/test");

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

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function getLoginUrl() {
  const directUrl = process.env.SALEADS_LOGIN_URL;
  if (directUrl) return directUrl;

  const baseUrl = process.env.SALEADS_BASE_URL;
  if (!baseUrl) {
    throw new Error(
      "Set SALEADS_BASE_URL or SALEADS_LOGIN_URL. The test is environment-agnostic and does not hardcode domains."
    );
  }

  const loginPath = process.env.SALEADS_LOGIN_PATH || "/";
  return new URL(loginPath, baseUrl).toString();
}

async function waitAfterClick(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(750);
}

async function clickByTextAndWait(page, regex, allowMissing = false) {
  const candidate = page
    .getByRole("button", { name: regex })
    .or(page.getByRole("link", { name: regex }))
    .or(page.getByText(regex))
    .first();

  const visible = await candidate.isVisible().catch(() => false);
  if (!visible) {
    if (allowMissing) return false;
    throw new Error(`Could not find clickable element by text: ${regex}`);
  }

  await candidate.click();
  await waitAfterClick(page);
  return true;
}

async function optionalGoogleAccountSelection(page) {
  const accountCandidate = page.getByText(ACCOUNT_EMAIL).first();
  if (await accountCandidate.isVisible().catch(() => false)) {
    await accountCandidate.click();
    await waitAfterClick(page);
  }
}

async function ensureSidebarVisible(page) {
  const sidebarByRole = page.getByRole("navigation").first();
  const sidebarByText = page.locator("aside, nav").filter({ hasText: /Negocio|Mi Negocio/i }).first();

  if (await sidebarByText.isVisible().catch(() => false)) return sidebarByText;
  await expect(sidebarByRole).toBeVisible();
  return sidebarByRole;
}

async function openLegalLinkAndValidate({
  page,
  appPage,
  linkTextRegex,
  headingRegex,
  screenshotName,
  report,
  reportKey,
}) {
  const link = page
    .getByRole("link", { name: linkTextRegex })
    .or(page.getByText(linkTextRegex))
    .first();
  await expect(link).toBeVisible();

  const popupPromise = page.context().waitForEvent("page", { timeout: 10_000 }).catch(() => null);
  await link.click();

  let targetPage = await popupPromise;
  if (!targetPage) {
    await waitAfterClick(page);
    targetPage = page;
  } else {
    await targetPage.waitForLoadState("domcontentloaded");
  }

  await expect(targetPage.getByRole("heading", { name: headingRegex }).first()).toBeVisible();
  const body = targetPage.locator("body");
  await expect(body).toContainText(headingRegex);

  const finalUrl = targetPage.url();
  report[reportKey] = `PASS (${finalUrl})`;
  await targetPage.screenshot({ path: screenshotName, fullPage: true });

  if (targetPage !== appPage) {
    await targetPage.close();
    await appPage.bringToFront();
    await waitAfterClick(appPage);
  }
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const failures = [];

  const markFailure = (label, error) => {
    const message = error instanceof Error ? error.message : String(error);
    failures.push(`${label}: ${message}`);
  };

  const screenshotPath = (name) => testInfo.outputPath(name);

  await test.step("1) Login with Google", async () => {
    try {
      await page.goto(getLoginUrl(), { waitUntil: "domcontentloaded" });
      await clickByTextAndWait(page, /Sign in with Google|Iniciar sesi[oó]n con Google|Google/i);
      await optionalGoogleAccountSelection(page);

      await waitAfterClick(page);
      await ensureSidebarVisible(page);
      await expect(page.getByText(/Negocio|Mi Negocio/i).first()).toBeVisible();

      await page.screenshot({ path: screenshotPath("01-dashboard-loaded.png"), fullPage: true });
      report["Login"] = "PASS";
    } catch (error) {
      markFailure("Login", error);
    }
  });

  await test.step("2) Open Mi Negocio menu", async () => {
    try {
      await ensureSidebarVisible(page);

      const negocioButton = page
        .getByRole("button", { name: /Negocio|Mi Negocio/i })
        .or(page.getByRole("link", { name: /Negocio|Mi Negocio/i }))
        .or(page.getByText(/Negocio|Mi Negocio/i))
        .first();
      await expect(negocioButton).toBeVisible();
      await negocioButton.click();
      await waitAfterClick(page);

      await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
      await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

      await page.screenshot({ path: screenshotPath("02-mi-negocio-menu-expanded.png"), fullPage: true });
      report["Mi Negocio menu"] = "PASS";
    } catch (error) {
      markFailure("Mi Negocio menu", error);
    }
  });

  await test.step("3) Validate Agregar Negocio modal", async () => {
    try {
      await clickByTextAndWait(page, /Agregar Negocio/i);
      await expect(page.getByRole("heading", { name: /Crear Nuevo Negocio/i })).toBeVisible();
      await expect(page.getByLabel(/Nombre del Negocio/i).or(page.getByPlaceholder(/Nombre del Negocio/i))).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
      await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

      const input = page.getByLabel(/Nombre del Negocio/i).or(page.getByPlaceholder(/Nombre del Negocio/i)).first();
      await input.click();
      await input.fill("Negocio Prueba Automatización");
      await waitAfterClick(page);

      await page.screenshot({ path: screenshotPath("03-agregar-negocio-modal.png"), fullPage: true });
      await clickByTextAndWait(page, /Cancelar/i);

      report["Agregar Negocio modal"] = "PASS";
    } catch (error) {
      markFailure("Agregar Negocio modal", error);
    }
  });

  await test.step("4) Open Administrar Negocios", async () => {
    try {
      const administrarVisible = await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false);
      if (!administrarVisible) {
        await clickByTextAndWait(page, /Negocio|Mi Negocio/i, true);
      }

      await clickByTextAndWait(page, /Administrar Negocios/i);
      await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();
      await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();

      await page.screenshot({ path: screenshotPath("04-administrar-negocios-account-page.png"), fullPage: true });
      report["Administrar Negocios view"] = "PASS";
    } catch (error) {
      markFailure("Administrar Negocios view", error);
    }
  });

  await test.step("5) Validate Información General", async () => {
    try {
      const emailValue = page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first();
      await expect(emailValue).toBeVisible();

      const userNameCandidate = page
        .locator("h1, h2, h3, p, span")
        .filter({ hasText: /[A-Za-zÁÉÍÓÚáéíóúÑñ]{3,}\s+[A-Za-zÁÉÍÓÚáéíóúÑñ]{2,}/ })
        .first();
      await expect(userNameCandidate).toBeVisible();

      await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /Cambiar Plan/i }).or(page.getByText(/Cambiar Plan/i))).toBeVisible();
      report["Información General"] = "PASS";
    } catch (error) {
      markFailure("Información General", error);
    }
  });

  await test.step("6) Validate Detalles de la Cuenta", async () => {
    try {
      await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
      await expect(page.getByText(/Estado activo/i)).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
      report["Detalles de la Cuenta"] = "PASS";
    } catch (error) {
      markFailure("Detalles de la Cuenta", error);
    }
  });

  await test.step("7) Validate Tus Negocios", async () => {
    try {
      await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
      await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
      report["Tus Negocios"] = "PASS";
    } catch (error) {
      markFailure("Tus Negocios", error);
    }
  });

  await test.step("8) Validate Términos y Condiciones", async () => {
    try {
      await openLegalLinkAndValidate({
        page,
        appPage: page,
        linkTextRegex: /T[eé]rminos y Condiciones/i,
        headingRegex: /T[eé]rminos y Condiciones/i,
        screenshotName: screenshotPath("08-terminos-y-condiciones.png"),
        report,
        reportKey: "Términos y Condiciones",
      });
    } catch (error) {
      markFailure("Términos y Condiciones", error);
    }
  });

  await test.step("9) Validate Política de Privacidad", async () => {
    try {
      await openLegalLinkAndValidate({
        page,
        appPage: page,
        linkTextRegex: /Pol[ií]tica de Privacidad/i,
        headingRegex: /Pol[ií]tica de Privacidad/i,
        screenshotName: screenshotPath("09-politica-de-privacidad.png"),
        report,
        reportKey: "Política de Privacidad",
      });
    } catch (error) {
      markFailure("Política de Privacidad", error);
    }
  });

  await test.step("10) Final Report", async () => {
    const reportPath = testInfo.outputPath("saleads-mi-negocio-report.json");
    await testInfo.attach("saleads-mi-negocio-report", {
      body: JSON.stringify(
        {
          summary: report,
          failures,
        },
        null,
        2
      ),
      contentType: "application/json",
    });

    await fs.writeFile(
      reportPath,
      JSON.stringify(
        {
          summary: report,
          failures,
        },
        null,
        2
      ),
      "utf8"
    );

    console.log("Final validation report:", JSON.stringify(report, null, 2));
    if (failures.length > 0) {
      throw new Error(`Validation failures:\n${failures.join("\n")}`);
    }
  });
});
