const fs = require("node:fs");
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

function createReport() {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = { status: "FAIL", details: [] };
    return acc;
  }, {});
}

function markPass(report, stepName, detail) {
  report[stepName].status = "PASS";
  if (detail) {
    report[stepName].details.push(detail);
  }
}

function markFail(report, stepName, error) {
  report[stepName].status = "FAIL";
  report[stepName].details.push(error instanceof Error ? error.message : String(error));
}

function slugify(name) {
  return name
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
}

async function waitForUiToSettle(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
}

async function takeCheckpoint(page, testInfo, label, fullPage = false) {
  const path = testInfo.outputPath(`${slugify(label)}.png`);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(label, { path, contentType: "image/png" });
}

async function resolveVisibleLocator(candidates, timeout = 6000) {
  for (const locator of candidates) {
    try {
      await locator.first().waitFor({ state: "visible", timeout });
      return locator.first();
    } catch (error) {
      // try next locator
    }
  }

  throw new Error("No visible element found for any candidate locator.");
}

async function validateLegalLink({
  page,
  testInfo,
  report,
  reportField,
  linkRegex,
  headingRegex,
  screenshotLabel,
}) {
  const link = await resolveVisibleLocator([
    page.getByRole("link", { name: linkRegex }),
    page.getByRole("button", { name: linkRegex }),
    page.getByText(linkRegex),
  ]);

  const appUrlBefore = page.url();
  const popupPromise = page.waitForEvent("popup", { timeout: 8000 }).catch(() => null);

  await link.click();
  await waitForUiToSettle(page);

  const popup = await popupPromise;
  const legalPage = popup || page;
  await waitForUiToSettle(legalPage);

  try {
    await expect(legalPage.getByRole("heading", { name: headingRegex }).first()).toBeVisible({ timeout: 30000 });
  } catch (error) {
    await expect(legalPage.getByText(headingRegex).first()).toBeVisible({ timeout: 30000 });
  }

  const legalBodyText = await legalPage.locator("body").innerText();
  expect(legalBodyText.trim().length).toBeGreaterThan(150);

  await takeCheckpoint(legalPage, testInfo, screenshotLabel, true);
  const finalUrl = legalPage.url();
  markPass(report, reportField, `Final URL: ${finalUrl}`);

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUiToSettle(page);
  } else if (page.url() !== appUrlBefore) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      await page.goto(appUrlBefore, { waitUntil: "domcontentloaded" });
    });
    await waitForUiToSettle(page);
  }
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  test.slow();

  const report = createReport();
  const startupBlockerMessage =
    "The browser opened on about:blank. Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL / BASE_URL) to the current environment login page.";

  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || process.env.BASE_URL;
  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  }
  const startupBlocked = !loginUrl && page.url() === "about:blank";

  await test.step("1) Login with Google", async () => {
    try {
      if (startupBlocked) {
        throw new Error(startupBlockerMessage);
      }

      const signInWithGoogle = await resolveVisibleLocator([
        page.getByRole("button", { name: /google/i }),
        page.getByRole("link", { name: /google/i }),
        page.getByText(/iniciar sesion con google|sign in with google|continuar con google/i),
      ]);

      const popupPromise = page.waitForEvent("popup", { timeout: 8000 }).catch(() => null);
      await signInWithGoogle.click();
      await waitForUiToSettle(page);

      const authPopup = await popupPromise;
      const authPage = authPopup || page;
      await waitForUiToSettle(authPage);

      const googleAccount = authPage.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true });
      if (await googleAccount.isVisible({ timeout: 10000 }).catch(() => false)) {
        await googleAccount.click();
        await waitForUiToSettle(authPage);
      }

      if (authPopup) {
        await authPopup.waitForClose({ timeout: 60000 }).catch(() => {});
        await page.bringToFront();
      }

      await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 60000 });
      await expect(page.getByText(/negocio|mi negocio|dashboard|inicio/i).first()).toBeVisible({ timeout: 60000 });
      await takeCheckpoint(page, testInfo, "dashboard loaded");

      markPass(report, "Login", "Dashboard and sidebar are visible after Google login.");
    } catch (error) {
      markFail(report, "Login", error);
    }
  });

  await test.step("2) Open Mi Negocio menu", async () => {
    try {
      if (startupBlocked) {
        throw new Error(startupBlockerMessage);
      }

      const negocioSection = await resolveVisibleLocator([
        page.getByRole("button", { name: /^Negocio$/i }),
        page.getByRole("link", { name: /^Negocio$/i }),
        page.getByText(/^Negocio$/i),
      ]);

      await negocioSection.click();
      await waitForUiToSettle(page);

      const miNegocioOption = await resolveVisibleLocator([
        page.getByRole("button", { name: /Mi Negocio/i }),
        page.getByRole("link", { name: /Mi Negocio/i }),
        page.getByText(/Mi Negocio/i),
      ]);

      await miNegocioOption.click();
      await waitForUiToSettle(page);

      await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
      await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
      await takeCheckpoint(page, testInfo, "mi negocio expanded menu");

      markPass(report, "Mi Negocio menu", "Mi Negocio submenu expanded with expected options.");
    } catch (error) {
      markFail(report, "Mi Negocio menu", error);
    }
  });

  await test.step("3) Validate Agregar Negocio modal", async () => {
    try {
      if (startupBlocked) {
        throw new Error(startupBlockerMessage);
      }

      const agregarNegocio = await resolveVisibleLocator([
        page.getByRole("button", { name: /Agregar Negocio/i }),
        page.getByRole("link", { name: /Agregar Negocio/i }),
        page.getByText(/^Agregar Negocio$/i),
      ]);

      await agregarNegocio.click();
      await waitForUiToSettle(page);

      await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
      await expect(page.getByLabel(/Nombre del Negocio/i).first()).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Cancelar/i }).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Crear Negocio/i }).first()).toBeVisible();
      await takeCheckpoint(page, testInfo, "agregar negocio modal");

      const nombreDelNegocio = page.getByLabel(/Nombre del Negocio/i).first();
      await nombreDelNegocio.click();
      await nombreDelNegocio.fill("Negocio Prueba Automatización");
      await page.getByRole("button", { name: /Cancelar/i }).first().click();
      await waitForUiToSettle(page);

      markPass(report, "Agregar Negocio modal", "Modal validated and cancelled after optional field input.");
    } catch (error) {
      markFail(report, "Agregar Negocio modal", error);
    }
  });

  await test.step("4) Open Administrar Negocios", async () => {
    try {
      if (startupBlocked) {
        throw new Error(startupBlockerMessage);
      }

      const administrarVisible = await page
        .getByText(/Administrar Negocios/i)
        .first()
        .isVisible()
        .catch(() => false);

      if (!administrarVisible) {
        const miNegocio = await resolveVisibleLocator([
          page.getByRole("button", { name: /Mi Negocio/i }),
          page.getByRole("link", { name: /Mi Negocio/i }),
          page.getByText(/Mi Negocio/i),
        ]);

        await miNegocio.click();
        await waitForUiToSettle(page);
      }

      const administrarNegocios = await resolveVisibleLocator([
        page.getByRole("button", { name: /Administrar Negocios/i }),
        page.getByRole("link", { name: /Administrar Negocios/i }),
        page.getByText(/Administrar Negocios/i),
      ]);

      await administrarNegocios.click();
      await waitForUiToSettle(page);

      await expect(page.getByText(/Informacion General|Información General/i).first()).toBeVisible({ timeout: 30000 });
      await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.getByText(/Seccion Legal|Sección Legal/i).first()).toBeVisible();
      await takeCheckpoint(page, testInfo, "administrar negocios account page", true);

      markPass(report, "Administrar Negocios view", "All account page sections are visible.");
    } catch (error) {
      markFail(report, "Administrar Negocios view", error);
    }
  });

  await test.step("5) Validate Información General", async () => {
    try {
      if (startupBlocked) {
        throw new Error(startupBlockerMessage);
      }

      await expect(page.getByText(/Informacion General|Información General/i).first()).toBeVisible();

      const userNameHint = await resolveVisibleLocator([
        page.getByText(/Nombre/i),
        page.getByText(/Usuario/i),
        page.getByText(/Perfil/i),
      ]);
      await expect(userNameHint).toBeVisible();

      await expect(page.locator("text=/[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}/i").first()).toBeVisible();
      await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible();

      markPass(report, "Información General", "Nombre/usuario, email, plan and Cambiar Plan are visible.");
    } catch (error) {
      markFail(report, "Información General", error);
    }
  });

  await test.step("6) Validate Detalles de la Cuenta", async () => {
    try {
      if (startupBlocked) {
        throw new Error(startupBlockerMessage);
      }

      await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
      await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();

      markPass(report, "Detalles de la Cuenta", "Account details labels are visible.");
    } catch (error) {
      markFail(report, "Detalles de la Cuenta", error);
    }
  });

  await test.step("7) Validate Tus Negocios", async () => {
    try {
      if (startupBlocked) {
        throw new Error(startupBlockerMessage);
      }

      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.getByRole("button", { name: /Agregar Negocio/i }).first()).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();

      const negocioOccurrences = await page.locator("text=/Negocio/i").count();
      expect(negocioOccurrences).toBeGreaterThan(2);

      markPass(report, "Tus Negocios", "Business list context and controls are visible.");
    } catch (error) {
      markFail(report, "Tus Negocios", error);
    }
  });

  await test.step("8) Validate Términos y Condiciones", async () => {
    try {
      if (startupBlocked) {
        throw new Error(startupBlockerMessage);
      }

      await validateLegalLink({
        page,
        testInfo,
        report,
        reportField: "Términos y Condiciones",
        linkRegex: /Terminos y Condiciones|Términos y Condiciones/i,
        headingRegex: /Terminos y Condiciones|Términos y Condiciones/i,
        screenshotLabel: "terminos y condiciones page",
      });
    } catch (error) {
      markFail(report, "Términos y Condiciones", error);
    }
  });

  await test.step("9) Validate Política de Privacidad", async () => {
    try {
      if (startupBlocked) {
        throw new Error(startupBlockerMessage);
      }

      await validateLegalLink({
        page,
        testInfo,
        report,
        reportField: "Política de Privacidad",
        linkRegex: /Politica de Privacidad|Política de Privacidad/i,
        headingRegex: /Politica de Privacidad|Política de Privacidad/i,
        screenshotLabel: "politica de privacidad page",
      });
    } catch (error) {
      markFail(report, "Política de Privacidad", error);
    }
  });

  await test.step("10) Final report", async () => {
    const reportJson = JSON.stringify(report, null, 2);
    const reportPath = testInfo.outputPath("mi-negocio-final-report.json");
    fs.writeFileSync(reportPath, reportJson, "utf8");
    await testInfo.attach("final-report", { body: reportJson, contentType: "application/json" });
    await page.context().storageState({ path: testInfo.outputPath("storage-state.json") });

    console.log("Mi Negocio workflow final report:");
    console.log(reportJson);
    console.log(`Report saved at: ${reportPath}`);
  });

  const failedSteps = Object.entries(report)
    .filter(([, entry]) => entry.status !== "PASS")
    .map(([name]) => name);

  expect(
    failedSteps,
    failedSteps.length
      ? `The following workflow validations failed: ${failedSteps.join(", ")}`
      : "All workflow validations passed."
  ).toEqual([]);
});
