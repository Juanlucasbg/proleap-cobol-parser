const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");

const GOOGLE_ACCOUNT_EMAIL = process.env.SALEADS_GOOGLE_ACCOUNT || "juanlucasbarbiergarzon@gmail.com";
const DEFAULT_WAIT_MS = 600;

function slugify(value) {
  return value
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");

  try {
    await page.waitForLoadState("networkidle", { timeout: 5000 });
  } catch (_error) {
    // Some SPA screens keep active connections alive forever.
  }

  await page.waitForTimeout(DEFAULT_WAIT_MS);
}

async function saveCheckpoint(page, testInfo, checkpointName, fullPage = false) {
  const screenshotPath = testInfo.outputPath(`${slugify(checkpointName)}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
}

async function firstVisibleLocator(candidates, errorMessage) {
  for (const candidate of candidates) {
    const item = candidate.first();
    if (await item.isVisible().catch(() => false)) {
      return item;
    }
  }

  throw new Error(errorMessage);
}

async function findClickableByVisibleText(scope, textRegex) {
  return firstVisibleLocator(
    [
      scope.getByRole("button", { name: textRegex }),
      scope.getByRole("link", { name: textRegex }),
      scope.locator("[role='button']").filter({ hasText: textRegex }),
      scope.locator("button, a, [role='button']").filter({ hasText: textRegex }),
      scope.locator("li, div, span").filter({ hasText: textRegex })
    ],
    `Unable to find clickable element with text pattern ${textRegex}`
  );
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function getSectionByHeading(page, headingRegex) {
  const heading = await firstVisibleLocator(
    [
      page.getByRole("heading", { name: headingRegex }),
      page.locator("h1, h2, h3, h4").filter({ hasText: headingRegex }),
      page.getByText(headingRegex)
    ],
    `Unable to locate section heading ${headingRegex}`
  );

  const containerCandidates = [
    page.locator("section, article, div").filter({ has: heading }),
    heading.locator("xpath=ancestor::section[1]"),
    heading.locator("xpath=ancestor::article[1]"),
    heading.locator("xpath=ancestor::div[1]")
  ];

  return firstVisibleLocator(containerCandidates, `Unable to locate container for heading ${headingRegex}`);
}

async function executeStep(report, field, callback) {
  try {
    await callback();
    report[field] = "PASS";
  } catch (error) {
    report[field] = "FAIL";
    console.error(`[${field}] ${error instanceof Error ? error.stack : error}`);
  }
}

async function navigateToLoginIfConfigured(page) {
  const configuredUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || process.env.BASE_URL;

  if (configuredUrl) {
    await page.goto(configuredUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
    return;
  }

  if (page.url() === "about:blank") {
    throw new Error(
      "No login URL was provided. Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL/BASE_URL) to the SaleADS login page."
    );
  }
}

async function clickLegalLinkAndValidate({
  page,
  section,
  linkPattern,
  headingPattern,
  checkpointName,
  testInfo
}) {
  const initialUrl = page.url();
  const popupPromise = page.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);
  const legalLink = await findClickableByVisibleText(section, linkPattern);

  await legalLink.click();
  const popup = await popupPromise;
  const targetPage = popup || page;

  await waitForUi(targetPage);

  const legalHeading = await firstVisibleLocator(
    [targetPage.getByRole("heading", { name: headingPattern }), targetPage.getByText(headingPattern)],
    `Unable to locate legal heading ${headingPattern}`
  );
  await expect(legalHeading).toBeVisible();

  const contentRegion = await firstVisibleLocator(
    [targetPage.locator("main"), targetPage.locator("article"), targetPage.locator("body")],
    "Unable to locate legal content container"
  );
  await expect(contentRegion).toContainText(/\S+/);

  await saveCheckpoint(targetPage, testInfo, checkpointName, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  } else if (page.url() !== initialUrl) {
    await page.goBack();
    await waitForUi(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Información General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Términos y Condiciones": "FAIL",
    "Política de Privacidad": "FAIL"
  };

  const legalUrls = {
    terminosYCondicionesUrl: null,
    politicaDePrivacidadUrl: null
  };

  await navigateToLoginIfConfigured(page);

  await executeStep(report, "Login", async () => {
    const loginButton = await findClickableByVisibleText(
      page,
      /sign in with google|inicia sesi[oó]n con google|iniciar sesi[oó]n con google|continuar con google|google/i
    );

    const popupPromise = page.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickAndWait(page, loginButton);
    const authPage = (await popupPromise) || page;

    const accountCandidate = authPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
    if (await accountCandidate.isVisible().catch(() => false)) {
      await clickAndWait(authPage, accountCandidate);
    }

    if (authPage !== page) {
      await authPage.waitForEvent("close", { timeout: 120000 }).catch(() => null);
      await waitForUi(page);
    }

    const sidebar = await firstVisibleLocator(
      [page.locator("aside"), page.locator("nav"), page.locator("[class*='sidebar']")],
      "Main navigation sidebar not visible after login"
    );
    await expect(sidebar).toBeVisible();
    await saveCheckpoint(page, testInfo, "dashboard-loaded", true);
  });

  await executeStep(report, "Mi Negocio menu", async () => {
    const sidebar = await firstVisibleLocator(
      [page.locator("aside"), page.locator("nav"), page.locator("[class*='sidebar']")],
      "Sidebar not visible"
    );
    await expect(sidebar).toBeVisible();

    const negocioOption = await findClickableByVisibleText(sidebar, /Negocio/i);
    await clickAndWait(page, negocioOption);

    const miNegocioOption = await findClickableByVisibleText(sidebar, /Mi Negocio/i);
    await clickAndWait(page, miNegocioOption);

    await expect(sidebar.getByText(/Agregar Negocio/i)).toBeVisible();
    await expect(sidebar.getByText(/Administrar Negocios/i)).toBeVisible();
    await saveCheckpoint(page, testInfo, "mi-negocio-menu-expanded");
  });

  await executeStep(report, "Agregar Negocio modal", async () => {
    const agregarNegocio = await findClickableByVisibleText(page, /Agregar Negocio/i);
    await clickAndWait(page, agregarNegocio);

    const modal = await firstVisibleLocator(
      [
        page.getByRole("dialog").filter({ hasText: /Crear Nuevo Negocio/i }),
        page.locator("[role='dialog'], .modal, [class*='modal']").filter({ hasText: /Crear Nuevo Negocio/i })
      ],
      "Crear Nuevo Negocio modal did not appear"
    );

    await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
    const nombreInput = await firstVisibleLocator(
      [
        modal.getByLabel(/Nombre del Negocio/i),
        modal.getByPlaceholder(/Nombre del Negocio/i),
        modal.locator("input")
      ],
      "Nombre del Negocio input not found"
    );
    await expect(nombreInput).toBeVisible();
    await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();
    await saveCheckpoint(page, testInfo, "crear-nuevo-negocio-modal");

    await nombreInput.click();
    await nombreInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page, modal.getByRole("button", { name: /Cancelar/i }));
    await expect(modal).toBeHidden();
  });

  await executeStep(report, "Administrar Negocios view", async () => {
    const administrarVisible = await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      const miNegocioOption = await findClickableByVisibleText(page, /Mi Negocio/i);
      await clickAndWait(page, miNegocioOption);
    }

    const administrarNegocios = await findClickableByVisibleText(page, /Administrar Negocios/i);
    await clickAndWait(page, administrarNegocios);

    await expect(page.getByText(/Informaci[oó]n General/i)).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i)).toBeVisible();
    await saveCheckpoint(page, testInfo, "administrar-negocios-account-page", true);
  });

  await executeStep(report, "Información General", async () => {
    const infoSection = await getSectionByHeading(page, /Informaci[oó]n General/i);
    const infoText = await infoSection.innerText();

    expect(infoText).toMatch(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
    expect(infoText).toMatch(/BUSINESS PLAN/i);
    await expect(infoSection.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

    const hasLikelyUserName = infoText
      .split("\n")
      .map((line) => line.trim())
      .some(
        (line) =>
          line.length >= 3 &&
          /^[A-Za-zÀ-ÿ' -]+$/.test(line) &&
          !/informaci[oó]n general|business plan|cambiar plan/i.test(line)
      );
    expect(hasLikelyUserName).toBeTruthy();
  });

  await executeStep(report, "Detalles de la Cuenta", async () => {
    const detailsSection = await getSectionByHeading(page, /Detalles de la Cuenta/i);
    const detailsText = await detailsSection.innerText();

    expect(detailsText).toMatch(/Cuenta creada/i);
    expect(detailsText).toMatch(/Estado\s*activo|activo\s*estado/i);
    expect(detailsText).toMatch(/Idioma seleccionado/i);
  });

  await executeStep(report, "Tus Negocios", async () => {
    const businessSection = await getSectionByHeading(page, /Tus Negocios/i);
    const businessText = await businessSection.innerText();

    await expect(businessSection.getByRole("button", { name: /Agregar Negocio/i })).toBeVisible();
    expect(businessText).toMatch(/Tienes 2 de 3 negocios/i);

    const listItems = businessSection.locator("li, tr, [class*='business'], [class*='negocio']");
    const listCount = await listItems.count();
    expect(listCount).toBeGreaterThan(0);
  });

  await executeStep(report, "Términos y Condiciones", async () => {
    const legalSection = await getSectionByHeading(page, /Secci[oó]n Legal/i);
    legalUrls.terminosYCondicionesUrl = await clickLegalLinkAndValidate({
      page,
      section: legalSection,
      linkPattern: /T[eé]rminos y Condiciones/i,
      headingPattern: /T[eé]rminos y Condiciones/i,
      checkpointName: "terminos-y-condiciones-page",
      testInfo
    });
  });

  await executeStep(report, "Política de Privacidad", async () => {
    const legalSection = await getSectionByHeading(page, /Secci[oó]n Legal/i);
    legalUrls.politicaDePrivacidadUrl = await clickLegalLinkAndValidate({
      page,
      section: legalSection,
      linkPattern: /Pol[ií]tica de Privacidad/i,
      headingPattern: /Pol[ií]tica de Privacidad/i,
      checkpointName: "politica-de-privacidad-page",
      testInfo
    });
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    report,
    legalUrls
  };
  const reportPath = testInfo.outputPath("final-report.json");
  await fs.writeFile(reportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf-8");
  console.log(`FINAL_REPORT ${JSON.stringify(finalReport)}`);

  const failedSteps = Object.entries(report)
    .filter(([, status]) => status === "FAIL")
    .map(([name]) => name);
  expect(
    failedSteps,
    `Workflow failed for: ${failedSteps.join(", ")}. Review final-report.json and checkpoint screenshots.`
  ).toEqual([]);
});
