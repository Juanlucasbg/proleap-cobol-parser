const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");
const path = require("path");

const ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const TEST_NAME = "saleads_mi_negocio_full_test";
const checkpointsDir = path.resolve(__dirname, "..", "artifacts", "checkpoints");
const reportsDir = path.resolve(__dirname, "..", "artifacts", "reports");

function slug(text) {
  return text
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
}

async function ensureDirs() {
  await fs.mkdir(checkpointsDir, { recursive: true });
  await fs.mkdir(reportsDir, { recursive: true });
}

async function clickByText(page, texts, options = {}) {
  const {
    exact = false,
    timeout = 20000,
    allowRoleButton = true,
    allowLink = true,
    allowTextLocator = true,
  } = options;

  const candidates = [];
  for (const text of texts) {
    if (allowRoleButton) {
      candidates.push(page.getByRole("button", { name: text, exact }));
      candidates.push(page.getByRole("menuitem", { name: text, exact }));
    }
    if (allowLink) {
      candidates.push(page.getByRole("link", { name: text, exact }));
    }
    if (allowTextLocator) {
      candidates.push(page.getByText(text, { exact }));
    }
  }

  const start = Date.now();
  while (Date.now() - start < timeout) {
    for (const locator of candidates) {
      try {
        const count = await locator.count();
        if (count > 0 && (await locator.first().isVisible())) {
          await locator.first().click();
          await waitForUi(page);
          return true;
        }
      } catch {
        // try next locator
      }
    }
    await page.waitForTimeout(250);
  }

  return false;
}

async function waitForUi(page) {
  await Promise.allSettled([
    page.waitForLoadState("domcontentloaded", { timeout: 15000 }),
    page.waitForLoadState("networkidle", { timeout: 15000 }),
  ]);
  await page.waitForTimeout(700);
}

async function expectVisible(page, possibleTexts, timeout = 20000) {
  const start = Date.now();
  while (Date.now() - start < timeout) {
    for (const text of possibleTexts) {
      const visibleText = page.getByText(text, { exact: false });
      if ((await visibleText.count()) > 0 && (await visibleText.first().isVisible())) {
        return { ok: true, matched: text };
      }

      const visibleHeading = page.getByRole("heading", { name: text, exact: false });
      if ((await visibleHeading.count()) > 0 && (await visibleHeading.first().isVisible())) {
        return { ok: true, matched: text };
      }
    }
    await page.waitForTimeout(250);
  }
  return { ok: false, matched: null };
}

async function attachStepScreenshot(page, testInfo, stepName, fullPage = false) {
  const fileName = `${String(testInfo.retry)}-${slug(stepName)}.png`;
  const targetPath = path.join(checkpointsDir, fileName);
  await page.screenshot({ path: targetPath, fullPage });
  await testInfo.attach(stepName, {
    path: targetPath,
    contentType: "image/png",
  });
  return targetPath;
}

function createResult() {
  return {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Información General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Términos y Condiciones": "FAIL",
    "Política de Privacidad": "FAIL",
    evidence: {
      screenshots: [],
      urls: {},
    },
    notes: [],
  };
}

async function maybeSelectGoogleAccount(page, result) {
  const accountLocator = page.getByText(ACCOUNT_EMAIL, { exact: false });
  try {
    if ((await accountLocator.count()) > 0 && (await accountLocator.first().isVisible())) {
      await accountLocator.first().click();
      await waitForUi(page);
      result.notes.push(`Selected Google account: ${ACCOUNT_EMAIL}`);
      return true;
    }
  } catch {
    // no-op
  }

  const useAnotherAccount = page.getByText("Use another account", { exact: false });
  if ((await useAnotherAccount.count()) > 0 && (await useAnotherAccount.first().isVisible())) {
    result.notes.push(
      "Google account selector showed 'Use another account'. Specific account tile was not visible."
    );
  }
  return false;
}

async function openLegalAndCapture(page, labelCandidates, headingCandidates, result, reportKey, testInfo) {
  const appUrlBefore = page.url();
  const popupPromise = page.waitForEvent("popup", { timeout: 6000 }).catch(() => null);
  const clicked = await clickByText(page, labelCandidates, {
    exact: false,
    timeout: 15000,
    allowRoleButton: true,
    allowLink: true,
    allowTextLocator: true,
  });
  expect(clicked).toBeTruthy();

  const popup = await popupPromise;
  let legalPage = page;

  if (popup) {
    legalPage = popup;
    await waitForUi(legalPage);
  } else {
    await waitForUi(page);
  }

  const headingFound = await expectVisible(legalPage, headingCandidates, 20000);
  const contentFound = await expectVisible(
    legalPage,
    [
      "términos",
      "condiciones",
      "privacidad",
      "información",
      "datos",
      "usuario",
      "servicio",
    ],
    20000
  );

  const finalUrl = legalPage.url();
  result.evidence.urls[reportKey] = finalUrl;
  result.evidence.screenshots.push(
    await attachStepScreenshot(legalPage, testInfo, `${reportKey} legal page`, true)
  );
  expect(headingFound.ok).toBeTruthy();
  expect(contentFound.ok).toBeTruthy();

  result[reportKey] = "PASS";

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    const wentBack = await page
      .goBack({ waitUntil: "domcontentloaded", timeout: 15000 })
      .then(() => true)
      .catch(() => false);

    if (!wentBack && appUrlBefore && page.url() !== appUrlBefore) {
      await page.goto(appUrlBefore, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }
  }
}

async function writeReport(result) {
  const jsonPath = path.join(reportsDir, `${TEST_NAME}.json`);
  const mdPath = path.join(reportsDir, `${TEST_NAME}.md`);

  const summaryLines = [
    `# ${TEST_NAME}`,
    "",
    "| Checkpoint | Status |",
    "| --- | --- |",
    `| Login | ${result.Login} |`,
    `| Mi Negocio menu | ${result["Mi Negocio menu"]} |`,
    `| Agregar Negocio modal | ${result["Agregar Negocio modal"]} |`,
    `| Administrar Negocios view | ${result["Administrar Negocios view"]} |`,
    `| Información General | ${result["Información General"]} |`,
    `| Detalles de la Cuenta | ${result["Detalles de la Cuenta"]} |`,
    `| Tus Negocios | ${result["Tus Negocios"]} |`,
    `| Términos y Condiciones | ${result["Términos y Condiciones"]} |`,
    `| Política de Privacidad | ${result["Política de Privacidad"]} |`,
    "",
    "## Captured URLs",
    "",
    `- Términos y Condiciones: ${result.evidence.urls["Términos y Condiciones"] || "N/A"}`,
    `- Política de Privacidad: ${result.evidence.urls["Política de Privacidad"] || "N/A"}`,
    "",
    "## Notes",
    "",
    ...(result.notes.length ? result.notes.map((n) => `- ${n}`) : ["- No additional notes"]),
  ];

  await fs.writeFile(jsonPath, JSON.stringify(result, null, 2), "utf8");
  await fs.writeFile(mdPath, summaryLines.join("\n"), "utf8");
}

test.describe(TEST_NAME, () => {
  test("Login to SaleADS and validate Mi Negocio module workflow", async ({ page }, testInfo) => {
    await ensureDirs();
    const result = createResult();

    const baseUrl = process.env.SALEADS_BASE_URL;
    const loginPath = process.env.SALEADS_LOGIN_PATH || "/login";
    if (baseUrl) {
      const targetUrl = new URL(loginPath, baseUrl).toString();
      await page.goto(targetUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
      result.notes.push(`Navigated using SALEADS_BASE_URL and SALEADS_LOGIN_PATH: ${targetUrl}`);
    } else {
      result.notes.push("No SALEADS_BASE_URL provided. Test assumes browser starts at login page.");
    }

    // Step 1: Login with Google
    try {
      const googlePopupPromise = page.waitForEvent("popup", { timeout: 6000 }).catch(() => null);
      const loginClicked = await clickByText(page, [
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Continuar con Google",
        "Iniciar con Google",
        "Login with Google",
      ]);
      expect(loginClicked).toBeTruthy();

      const googlePopup = await googlePopupPromise;
      if (googlePopup) {
        await waitForUi(googlePopup);
        await maybeSelectGoogleAccount(googlePopup, result);
      } else {
        await maybeSelectGoogleAccount(page, result);
      }

      await waitForUi(page);

      expect((await expectVisible(page, ["Negocio", "Dashboard", "Panel", "Mi Negocio"], 45000)).ok).toBeTruthy();
      expect((await expectVisible(page, ["Mi Negocio", "Agregar Negocio", "Administrar Negocios"], 45000)).ok).toBeTruthy();

      result.Login = "PASS";
      result.evidence.screenshots.push(
        await attachStepScreenshot(page, testInfo, "dashboard loaded", true)
      );
    } catch (error) {
      result.notes.push(`Login step failed: ${error.message}`);
      await writeReport(result);
      throw error;
    }

    // Step 2: Open Mi Negocio menu
    try {
      const negocioVisible = await expectVisible(page, ["Negocio"], 20000);
      expect(negocioVisible.ok).toBeTruthy();

      const miNegocioClicked = await clickByText(page, ["Mi Negocio"], {
        exact: false,
        timeout: 15000,
      });
      expect(miNegocioClicked).toBeTruthy();

      expect((await expectVisible(page, ["Agregar Negocio"], 15000)).ok).toBeTruthy();
      expect((await expectVisible(page, ["Administrar Negocios"], 15000)).ok).toBeTruthy();

      result["Mi Negocio menu"] = "PASS";
      result.evidence.screenshots.push(
        await attachStepScreenshot(page, testInfo, "mi negocio expanded menu")
      );
    } catch (error) {
      result.notes.push(`Mi Negocio menu validation failed: ${error.message}`);
      await writeReport(result);
      throw error;
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      const addBusinessClicked = await clickByText(page, ["Agregar Negocio"], {
        exact: false,
        timeout: 15000,
      });
      expect(addBusinessClicked).toBeTruthy();

      expect((await expectVisible(page, ["Crear Nuevo Negocio"], 15000)).ok).toBeTruthy();
      expect((await expectVisible(page, ["Nombre del Negocio"], 15000)).ok).toBeTruthy();
      expect((await expectVisible(page, ["Tienes 2 de 3 negocios"], 15000)).ok).toBeTruthy();
      expect((await expectVisible(page, ["Cancelar"], 15000)).ok).toBeTruthy();
      expect((await expectVisible(page, ["Crear Negocio"], 15000)).ok).toBeTruthy();

      let nameInput = page.getByRole("textbox", { name: /Nombre del Negocio/i });
      if ((await nameInput.count()) === 0) {
        nameInput = page.getByPlaceholder("Nombre del Negocio");
      }

      if ((await nameInput.count()) > 0 && (await nameInput.first().isVisible())) {
        await nameInput.first().click();
        await nameInput.first().fill("Negocio Prueba Automatización");
        await waitForUi(page);
      }

      result.evidence.screenshots.push(await attachStepScreenshot(page, testInfo, "agregar negocio modal"));
      await clickByText(page, ["Cancelar"], { exact: false, timeout: 10000 });
      result["Agregar Negocio modal"] = "PASS";
    } catch (error) {
      result.notes.push(`Agregar Negocio modal validation failed: ${error.message}`);
      await writeReport(result);
      throw error;
    }

    // Step 4: Open Administrar Negocios
    try {
      if (!(await expectVisible(page, ["Administrar Negocios"], 3000)).ok) {
        await clickByText(page, ["Mi Negocio"], { exact: false, timeout: 10000 });
      }

      const administrarClicked = await clickByText(page, ["Administrar Negocios"], {
        exact: false,
        timeout: 15000,
      });
      expect(administrarClicked).toBeTruthy();

      expect((await expectVisible(page, ["Información General"], 20000)).ok).toBeTruthy();
      expect((await expectVisible(page, ["Detalles de la Cuenta"], 20000)).ok).toBeTruthy();
      expect((await expectVisible(page, ["Tus Negocios"], 20000)).ok).toBeTruthy();
      expect((await expectVisible(page, ["Sección Legal"], 20000)).ok).toBeTruthy();

      result["Administrar Negocios view"] = "PASS";
      result.evidence.screenshots.push(
        await attachStepScreenshot(page, testInfo, "administrar negocios page", true)
      );
    } catch (error) {
      result.notes.push(`Administrar Negocios view validation failed: ${error.message}`);
      await writeReport(result);
      throw error;
    }

    // Step 5: Validate Información General
    try {
      expect(
        (
          await expectVisible(
            page,
            ["Juan", "juan", "Lucas", "lucas", "Barbier", "Garzon", "juanlucasbarbiergarzon"],
            20000
          )
        ).ok
      ).toBeTruthy();
      expect((await expectVisible(page, [ACCOUNT_EMAIL], 20000)).ok).toBeTruthy();
      expect((await expectVisible(page, ["BUSINESS PLAN"], 20000)).ok).toBeTruthy();
      expect((await expectVisible(page, ["Cambiar Plan"], 20000)).ok).toBeTruthy();
      result["Información General"] = "PASS";
    } catch (error) {
      result.notes.push(`Información General validation failed: ${error.message}`);
      await writeReport(result);
      throw error;
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      expect((await expectVisible(page, ["Cuenta creada", "Cuenta Creada"], 20000)).ok).toBeTruthy();
      expect((await expectVisible(page, ["Estado activo", "Estado Activo"], 20000)).ok).toBeTruthy();
      expect(
        (await expectVisible(page, ["Idioma seleccionado", "Idioma Seleccionado"], 20000)).ok
      ).toBeTruthy();
      result["Detalles de la Cuenta"] = "PASS";
    } catch (error) {
      result.notes.push(`Detalles de la Cuenta validation failed: ${error.message}`);
      await writeReport(result);
      throw error;
    }

    // Step 7: Validate Tus Negocios
    try {
      expect((await expectVisible(page, ["Tus Negocios"], 20000)).ok).toBeTruthy();
      expect((await expectVisible(page, ["Agregar Negocio"], 20000)).ok).toBeTruthy();
      expect((await expectVisible(page, ["Tienes 2 de 3 negocios"], 20000)).ok).toBeTruthy();
      result["Tus Negocios"] = "PASS";
    } catch (error) {
      result.notes.push(`Tus Negocios validation failed: ${error.message}`);
      await writeReport(result);
      throw error;
    }

    // Step 8: Validate Términos y Condiciones
    try {
      await openLegalAndCapture(
        page,
        ["Términos y Condiciones", "Terminos y Condiciones"],
        ["Términos y Condiciones", "Terminos y Condiciones"],
        result,
        "Términos y Condiciones",
        testInfo
      );
    } catch (error) {
      result.notes.push(`Términos y Condiciones validation failed: ${error.message}`);
      await writeReport(result);
      throw error;
    }

    // Step 9: Validate Política de Privacidad
    try {
      await openLegalAndCapture(
        page,
        ["Política de Privacidad", "Politica de Privacidad"],
        ["Política de Privacidad", "Politica de Privacidad"],
        result,
        "Política de Privacidad",
        testInfo
      );
    } catch (error) {
      result.notes.push(`Política de Privacidad validation failed: ${error.message}`);
      await writeReport(result);
      throw error;
    }

    await writeReport(result);
  });
});
