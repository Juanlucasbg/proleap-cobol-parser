const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");
const path = require("path");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
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

function escapeRegex(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 7000 });
  } catch (_) {
    // Some pages keep network requests alive. DOM readiness is enough.
  }
  await page.waitForTimeout(500);
}

async function isVisible(locator) {
  try {
    return await locator.first().isVisible();
  } catch (_) {
    return false;
  }
}

async function findByVisibleText(page, textOrRegex) {
  const textRegex =
    textOrRegex instanceof RegExp
      ? textOrRegex
      : new RegExp(`^\\s*${escapeRegex(textOrRegex)}\\s*$`, "i");

  const candidates = [
    page.getByRole("button", { name: textRegex }),
    page.getByRole("link", { name: textRegex }),
    page.getByRole("menuitem", { name: textRegex }),
    page.getByRole("tab", { name: textRegex }),
    page.getByRole("option", { name: textRegex }),
    page.getByText(textRegex),
  ];

  for (const candidate of candidates) {
    const current = candidate.first();
    if (await isVisible(current)) {
      return current;
    }
  }

  throw new Error(`Could not find visible element with text: ${textRegex}`);
}

async function captureCheckpoint(page, evidenceDir, name, fullPage = false) {
  const filename = `${Date.now()}_${name}.png`;
  const outputPath = path.join(evidenceDir, filename);
  await page.screenshot({ path: outputPath, fullPage });
  return outputPath;
}

async function runStep(report, errors, stepName, fn) {
  try {
    await fn();
    report[stepName] = "PASS";
  } catch (error) {
    report[stepName] = "FAIL";
    errors[stepName] = error.message;
  }
}

async function maybeSelectGoogleAccount(authPage) {
  const emailRegex = new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i");
  const accountCandidate = await findByVisibleText(authPage, emailRegex).catch(
    () => null
  );
  if (!accountCandidate) {
    return false;
  }

  await accountCandidate.click();
  await waitForUi(authPage);
  return true;
}

async function isSidebarVisible(page) {
  const sidebarCandidate = page
    .locator("aside, nav")
    .filter({ hasText: /Negocio|Mi Negocio|Dashboard/i })
    .first();
  return isVisible(sidebarCandidate);
}

async function validateLegalLink({
  page,
  context,
  linkTextPattern,
  expectedHeadingPattern,
  evidenceDir,
  checkpointName,
}) {
  const popupPromise = context
    .waitForEvent("page", { timeout: 10000 })
    .catch(() => null);

  const legalLink = await findByVisibleText(page, linkTextPattern);
  await legalLink.scrollIntoViewIfNeeded();
  await legalLink.click();

  const popup = await popupPromise;
  const legalPage = popup || page;
  const openedNewTab = Boolean(popup);

  await waitForUi(legalPage);
  await expect(legalPage.getByText(expectedHeadingPattern).first()).toBeVisible(
    {
      timeout: 30000,
    }
  );

  const legalBodyText = (await legalPage.locator("body").innerText())
    .replace(/\s+/g, " ")
    .trim();
  if (legalBodyText.length < 120) {
    throw new Error("Legal content text is too short.");
  }

  const screenshot = await captureCheckpoint(
    legalPage,
    evidenceDir,
    checkpointName,
    true
  );
  const finalUrl = legalPage.url();

  if (openedNewTab) {
    await legalPage.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
    await waitForUi(page);
  }

  return { screenshot, finalUrl };
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const baseUrl = process.env.SALEADS_URL || process.env.BASE_URL;
  if (!baseUrl) {
    throw new Error(
      "Set SALEADS_URL or BASE_URL with the current SaleADS login page URL."
    );
  }

  const evidenceDir = path.join(testInfo.outputDir, "saleads-evidence");
  await fs.mkdir(evidenceDir, { recursive: true });

  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const errors = {};
  const evidence = {};
  const legalUrls = {};

  await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page);

  await runStep(report, errors, "Login", async () => {
    if (!(await isSidebarVisible(page))) {
      const popupPromise = context
        .waitForEvent("page", { timeout: 10000 })
        .catch(() => null);

      const loginButton = await findByVisibleText(
        page,
        /Sign in with Google|Iniciar sesi[oó]n con Google|Continuar con Google|Google/i
      );
      await loginButton.click();

      const popup = await popupPromise;
      const authPage = popup || page;

      await waitForUi(authPage);
      await maybeSelectGoogleAccount(authPage);

      if (popup) {
        await popup.waitForClose({ timeout: 45000 }).catch(() => null);
      }
      await page.bringToFront();
      await waitForUi(page);
    }

    await expect(page.locator("main").first()).toBeVisible({ timeout: 60000 });
    const sidebar = page
      .locator("aside, nav")
      .filter({ hasText: /Negocio|Mi Negocio|Dashboard/i })
      .first();
    await expect(sidebar).toBeVisible({ timeout: 60000 });

    evidence.dashboard = await captureCheckpoint(
      page,
      evidenceDir,
      "01_dashboard_loaded"
    );
  });

  await runStep(report, errors, "Mi Negocio menu", async () => {
    const negocioSection = await findByVisibleText(page, /^Negocio$/i).catch(
      () => null
    );
    if (negocioSection) {
      await negocioSection.click();
      await waitForUi(page);
    }

    const miNegocio = await findByVisibleText(page, /^Mi Negocio$/i);
    await miNegocio.click();
    await waitForUi(page);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();

    evidence.miNegocioMenu = await captureCheckpoint(
      page,
      evidenceDir,
      "02_mi_negocio_menu_expanded"
    );
  });

  await runStep(report, errors, "Agregar Negocio modal", async () => {
    const agregarNegocio = await findByVisibleText(page, /^Agregar Negocio$/i);
    await agregarNegocio.click();
    await waitForUi(page);

    const modalTitle = page.getByText(/Crear Nuevo Negocio/i).first();
    await expect(modalTitle).toBeVisible();
    await expect(page.getByText(/Nombre del Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();

    const cancelar = await findByVisibleText(page, /^Cancelar$/i);
    const crearNegocio = await findByVisibleText(page, /^Crear Negocio$/i);
    await expect(cancelar).toBeVisible();
    await expect(crearNegocio).toBeVisible();

    const nombreInputByRole = page
      .getByRole("textbox", { name: /Nombre del Negocio/i })
      .first();
    const nombreInputFallback = page
      .locator("input[name*='nombre' i], input[id*='nombre' i], input")
      .first();
    const nombreInput = (await isVisible(nombreInputByRole))
      ? nombreInputByRole
      : nombreInputFallback;

    await nombreInput.click();
    await nombreInput.fill("Negocio Prueba Automatizacion");

    evidence.agregarNegocioModal = await captureCheckpoint(
      page,
      evidenceDir,
      "03_agregar_negocio_modal"
    );

    await cancelar.click();
    await expect(modalTitle).toBeHidden({ timeout: 10000 });
  });

  await runStep(report, errors, "Administrar Negocios view", async () => {
    const administrarVisible = await isVisible(
      page.getByText(/^Administrar Negocios$/i).first()
    );
    if (!administrarVisible) {
      const miNegocio = await findByVisibleText(page, /^Mi Negocio$/i);
      await miNegocio.click();
      await waitForUi(page);
    }

    const administrar = await findByVisibleText(page, /^Administrar Negocios$/i);
    await administrar.click();
    await waitForUi(page);

    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();

    evidence.administrarNegocios = await captureCheckpoint(
      page,
      evidenceDir,
      "04_administrar_negocios_view",
      true
    );
  });

  await runStep(report, errors, "Información General", async () => {
    const mainText = await page.locator("main").innerText();
    const emailMatch = mainText.match(
      /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i
    );
    if (!emailMatch) {
      throw new Error("User email is not visible.");
    }

    if (!/BUSINESS PLAN/i.test(mainText)) {
      throw new Error("Text BUSINESS PLAN is not visible.");
    }

    const cambiarPlan = await findByVisibleText(page, /^Cambiar Plan$/i);
    await expect(cambiarPlan).toBeVisible();

    const textWithoutEmail = mainText.replace(emailMatch[0], "");
    const fullNamePattern = /\b[A-ZÀ-Ý][a-zà-ý]+(?:\s+[A-ZÀ-Ý][a-zà-ý]+)+\b/;
    const hasNameLabel = /Nombre|Usuario|Perfil/i.test(mainText);
    if (!fullNamePattern.test(textWithoutEmail) && !hasNameLabel) {
      throw new Error("User name is not clearly visible.");
    }
  });

  await runStep(report, errors, "Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep(report, errors, "Tus Negocios", async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();

    const listCandidates = [
      page.locator("main [role='list'] li"),
      page.locator("main table tbody tr"),
      page.locator("main [data-testid*='business']"),
      page.locator("main [class*='business']"),
    ];

    let hasVisibleBusinessList = false;
    for (const candidate of listCandidates) {
      const count = await candidate.count();
      if (count > 0 && (await isVisible(candidate.first()))) {
        hasVisibleBusinessList = true;
        break;
      }
    }

    if (!hasVisibleBusinessList) {
      throw new Error("Business list is not visible.");
    }
  });

  await runStep(report, errors, "Términos y Condiciones", async () => {
    await page.getByText(/Sección Legal/i).first().scrollIntoViewIfNeeded();
    const { screenshot, finalUrl } = await validateLegalLink({
      page,
      context,
      linkTextPattern: /T[eé]rminos y Condiciones/i,
      expectedHeadingPattern: /T[eé]rminos y Condiciones/i,
      evidenceDir,
      checkpointName: "08_terminos_y_condiciones",
    });
    evidence.terminos = screenshot;
    legalUrls.terminosYCondiciones = finalUrl;
  });

  await runStep(report, errors, "Política de Privacidad", async () => {
    await page.getByText(/Sección Legal/i).first().scrollIntoViewIfNeeded();
    const { screenshot, finalUrl } = await validateLegalLink({
      page,
      context,
      linkTextPattern: /Pol[ií]tica de Privacidad/i,
      expectedHeadingPattern: /Pol[ií]tica de Privacidad/i,
      evidenceDir,
      checkpointName: "09_politica_de_privacidad",
    });
    evidence.politica = screenshot;
    legalUrls.politicaDePrivacidad = finalUrl;
  });

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    results: report,
    legalUrls,
    evidence,
    errors,
  };

  const reportPath = path.join(evidenceDir, "final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("saleads-final-report", {
    path: reportPath,
    contentType: "application/json",
  });

  const hasFailures = Object.values(report).some((status) => status === "FAIL");
  expect(hasFailures, "One or more workflow validations failed.").toBeFalsy();
});
