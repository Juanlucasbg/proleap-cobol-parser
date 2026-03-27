const { test, expect } = require('@playwright/test');
const fs = require('fs/promises');
const path = require('path');

const TEST_NAME = 'saleads_mi_negocio_full_test';

async function ensureDir(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

function timestamp() {
  return new Date().toISOString().replace(/[:.]/g, '-');
}

async function waitForUiStable(page) {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(600);
}

function createFinalResultTemplate() {
  return {
    Login: 'FAIL',
    'Mi Negocio menu': 'FAIL',
    'Agregar Negocio modal': 'FAIL',
    'Administrar Negocios view': 'FAIL',
    'Información General': 'FAIL',
    'Detalles de la Cuenta': 'FAIL',
    'Tus Negocios': 'FAIL',
    'Términos y Condiciones': 'FAIL',
    'Política de Privacidad': 'FAIL',
  };
}

async function clickByText(page, candidates, options = {}) {
  const exact = options.exact ?? true;
  for (const text of candidates) {
    const locator = page.getByText(text, { exact }).first();
    if (await locator.isVisible().catch(() => false)) {
      await locator.click();
      await waitForUiStable(page);
      return text;
    }
  }
  throw new Error(`No clickable text found for candidates: ${candidates.join(', ')}`);
}

async function fillByLabelOrPlaceholder(page, candidates, value) {
  for (const text of candidates) {
    const byLabel = page.getByLabel(text, { exact: false }).first();
    if (await byLabel.isVisible().catch(() => false)) {
      await byLabel.fill(value);
      return `label:${text}`;
    }

    const byPlaceholder = page.getByPlaceholder(text, { exact: false }).first();
    if (await byPlaceholder.isVisible().catch(() => false)) {
      await byPlaceholder.fill(value);
      return `placeholder:${text}`;
    }
  }

  const fallback = page.locator('input[type="text"], input:not([type])').first();
  if (await fallback.isVisible().catch(() => false)) {
    await fallback.fill(value);
    return 'fallback:first-text-input';
  }

  throw new Error(`No input found for candidates: ${candidates.join(', ')}`);
}

async function safeScreenshot(page, outputDir, fileName, fullPage = false) {
  const finalPath = path.join(outputDir, fileName);
  await page.screenshot({ path: finalPath, fullPage });
  return finalPath;
}

async function openLegalLinkAndValidate({
  page,
  outputDir,
  linkCandidates,
  expectedHeadingCandidates,
  evidencePrefix,
}) {
  let targetPage = page;
  let openedNewTab = false;

  const popupPromise = page.context().waitForEvent('page', { timeout: 5000 }).catch(() => null);
  await clickByText(page, linkCandidates, { exact: true });
  const popupPage = await popupPromise;

  if (popupPage) {
    targetPage = popupPage;
    openedNewTab = true;
  }

  await waitForUiStable(targetPage);

  let headingFound = null;
  for (const heading of expectedHeadingCandidates) {
    const hLocator = targetPage.getByRole('heading', { name: heading, exact: false }).first();
    if (await hLocator.isVisible().catch(() => false)) {
      headingFound = heading;
      break;
    }
  }
  if (!headingFound) {
    for (const heading of expectedHeadingCandidates) {
      const tLocator = targetPage.getByText(heading, { exact: false }).first();
      if (await tLocator.isVisible().catch(() => false)) {
        headingFound = heading;
        break;
      }
    }
  }
  expect(headingFound, `No heading text found: ${expectedHeadingCandidates.join(' / ')}`).toBeTruthy();

  const bodyText = (await targetPage.locator('body').innerText()).trim();
  expect(bodyText.length).toBeGreaterThan(100);

  const screenshotPath = await safeScreenshot(
    targetPage,
    outputDir,
    `${evidencePrefix}_${timestamp()}.png`,
    true
  );

  const finalUrl = targetPage.url();

  if (openedNewTab) {
    await targetPage.close();
    await page.bringToFront();
    await waitForUiStable(page);
  } else {
    await page.goBack().catch(() => Promise.resolve());
    await waitForUiStable(page);
  }

  return {
    openedNewTab,
    headingFound,
    finalUrl,
    screenshotPath,
  };
}

test(TEST_NAME, async ({ page }) => {
  const baseUrl = process.env.SALEADS_BASE_URL || process.env.BASE_URL || '';
  const outputDir = path.join(process.cwd(), 'evidence', TEST_NAME);
  await ensureDir(outputDir);

  const report = {
    testName: TEST_NAME,
    executedAt: new Date().toISOString(),
    baseUrlUsed: baseUrl || 'pre-opened-login-page',
    checkpoints: [],
    finalResult: createFinalResultTemplate(),
    legalUrls: {},
    screenshots: [],
    errors: [],
  };

  function mark(step, status, details = {}) {
    report.checkpoints.push({ step, status, ...details });
    report.finalResult[step] = status;
    if (status === 'FAIL' && details.error) {
      report.errors.push({ step, error: details.error });
    }
  }

  async function runStep(stepName, fn) {
    try {
      const details = (await fn()) || {};
      mark(stepName, 'PASS', details);
      return true;
    } catch (error) {
      mark(stepName, 'FAIL', { error: String(error) });
      return false;
    }
  }

  if (baseUrl) {
    await page.goto(baseUrl, { waitUntil: 'domcontentloaded' });
    await waitForUiStable(page);
  } else {
    await waitForUiStable(page);
  }

  // Step 1: Login with Google.
  const loginOk = await runStep('Login', async () => {
    const popupPromise = page.context().waitForEvent('page', { timeout: 5000 }).catch(() => null);
    await clickByText(page, [
      'Sign in with Google',
      'Iniciar sesión con Google',
      'Continuar con Google',
      'Login with Google',
    ]);
    const popupPage = await popupPromise;

    const accountEmail = 'juanlucasbarbiergarzon@gmail.com';
    if (popupPage) {
      await popupPage.waitForLoadState('domcontentloaded');
      const popupOption = popupPage.getByText(accountEmail, { exact: false }).first();
      if (await popupOption.isVisible().catch(() => false)) {
        await popupOption.click();
      }
      await popupPage.waitForTimeout(1200);
      await page.bringToFront();
      await waitForUiStable(page);
    } else {
      const accountOption = page.getByText(accountEmail, { exact: false }).first();
      if (await accountOption.isVisible().catch(() => false)) {
        await accountOption.click();
        await waitForUiStable(page);
      }
    }

    await expect(page.locator('aside, nav').first()).toBeVisible();
    const dashboardShot = await safeScreenshot(
      page,
      outputDir,
      `01_dashboard_loaded_${timestamp()}.png`,
      true
    );
    report.screenshots.push(dashboardShot);
  });

  // Step 2: Open Mi Negocio menu.
  const menuOk = loginOk && (await runStep('Mi Negocio menu', async () => {
    await clickByText(page, ['Negocio', 'Mi Negocio'], { exact: false });
    await expect(page.getByText('Agregar Negocio', { exact: false }).first()).toBeVisible();
    await expect(page.getByText('Administrar Negocios', { exact: false }).first()).toBeVisible();

    const menuShot = await safeScreenshot(
      page,
      outputDir,
      `02_mi_negocio_menu_expanded_${timestamp()}.png`,
      true
    );
    report.screenshots.push(menuShot);
  }));

  // Step 3: Validate Agregar Negocio modal.
  const modalOk = menuOk && (await runStep('Agregar Negocio modal', async () => {
    await clickByText(page, ['Agregar Negocio'], { exact: false });

    await expect(page.getByText('Crear Nuevo Negocio', { exact: false }).first()).toBeVisible();
    await expect(page.getByText('Nombre del Negocio', { exact: false }).first()).toBeVisible();
    await expect(page.getByText('Tienes 2 de 3 negocios', { exact: false }).first()).toBeVisible();
    await expect(page.getByText('Cancelar', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('Crear Negocio', { exact: false }).first()).toBeVisible();

    const modalShot = await safeScreenshot(
      page,
      outputDir,
      `03_agregar_negocio_modal_${timestamp()}.png`,
      true
    );
    report.screenshots.push(modalShot);

    await fillByLabelOrPlaceholder(
      page,
      ['Nombre del Negocio', 'nombre del negocio', 'Nombre'],
      'Negocio Prueba Automatización'
    );
    await clickByText(page, ['Cancelar'], { exact: true });
  }));

  // Step 4: Open Administrar Negocios.
  const adminOk = menuOk && (await runStep('Administrar Negocios view', async () => {
    if (!(await page.getByText('Administrar Negocios', { exact: false }).first().isVisible().catch(() => false))) {
      await clickByText(page, ['Mi Negocio', 'Negocio'], { exact: false });
    }
    await clickByText(page, ['Administrar Negocios'], { exact: false });

    await expect(page.getByText('Información General', { exact: false }).first()).toBeVisible();
    await expect(page.getByText('Detalles de la Cuenta', { exact: false }).first()).toBeVisible();
    await expect(page.getByText('Tus Negocios', { exact: false }).first()).toBeVisible();
    await expect(
      page
        .getByText(/Sección Legal|Seccion Legal|Legal/i)
        .first()
    ).toBeVisible();

    const adminShot = await safeScreenshot(
      page,
      outputDir,
      `04_administrar_negocios_${timestamp()}.png`,
      true
    );
    report.screenshots.push(adminShot);
  }));

  // Step 5: Validate Información General.
  const generalInfoOk = adminOk && (await runStep('Información General', async () => {
    const bodyText = await page.locator('body').innerText();
    const hasUserInfo = /@/.test(bodyText);
    expect(hasUserInfo).toBeTruthy();
    await expect(page.getByText('BUSINESS PLAN', { exact: false }).first()).toBeVisible();
    await expect(page.getByText('Cambiar Plan', { exact: false }).first()).toBeVisible();
  }));

  // Step 6: Validate Detalles de la Cuenta.
  const accountDetailsOk = adminOk && (await runStep('Detalles de la Cuenta', async () => {
    await expect(page.getByText('Cuenta creada', { exact: false }).first()).toBeVisible();
    await expect(page.getByText('Estado activo', { exact: false }).first()).toBeVisible();
    await expect(page.getByText('Idioma seleccionado', { exact: false }).first()).toBeVisible();
  }));

  // Step 7: Validate Tus Negocios.
  const businessSectionOk = adminOk && (await runStep('Tus Negocios', async () => {
    await expect(page.getByText('Tus Negocios', { exact: false }).first()).toBeVisible();
    await expect(page.getByText('Agregar Negocio', { exact: false }).first()).toBeVisible();
    await expect(page.getByText('Tienes 2 de 3 negocios', { exact: false }).first()).toBeVisible();
  }));

  // Step 8: Validate Términos y Condiciones.
  const termsOk = adminOk && (await runStep('Términos y Condiciones', async () => {
    const termsResult = await openLegalLinkAndValidate({
      page,
      outputDir,
      linkCandidates: ['Términos y Condiciones', 'Terminos y Condiciones'],
      expectedHeadingCandidates: ['Términos y Condiciones', 'Terminos y Condiciones'],
      evidencePrefix: '08_terminos_y_condiciones',
    });
    report.screenshots.push(termsResult.screenshotPath);
    report.legalUrls.terminosYCondiciones = termsResult.finalUrl;
    return {
      finalUrl: termsResult.finalUrl,
      openedNewTab: termsResult.openedNewTab,
    };
  }));

  // Step 9: Validate Política de Privacidad.
  const privacyOk = adminOk && (await runStep('Política de Privacidad', async () => {
    const privacyResult = await openLegalLinkAndValidate({
      page,
      outputDir,
      linkCandidates: ['Política de Privacidad', 'Politica de Privacidad'],
      expectedHeadingCandidates: ['Política de Privacidad', 'Politica de Privacidad'],
      evidencePrefix: '09_politica_de_privacidad',
    });
    report.screenshots.push(privacyResult.screenshotPath);
    report.legalUrls.politicaDePrivacidad = privacyResult.finalUrl;
    return {
      finalUrl: privacyResult.finalUrl,
      openedNewTab: privacyResult.openedNewTab,
    };
  }));

  if (!loginOk) {
    report.checkpoints.push({
      step: 'Workflow dependency',
      status: 'FAIL',
      reason: 'Login failed, dependent sections could not be validated.',
    });
  } else if (!adminOk) {
    report.checkpoints.push({
      step: 'Workflow dependency',
      status: 'FAIL',
      reason: 'Administrar Negocios view was not reachable; dependent sections could not be validated.',
    });
  }

  report.summary = {
    passed: Object.values(report.finalResult).filter((v) => v === 'PASS').length,
    failed: Object.values(report.finalResult).filter((v) => v === 'FAIL').length,
    modalValidated: modalOk || false,
    infoGeneralValidated: generalInfoOk || false,
    accountDetailsValidated: accountDetailsOk || false,
    businessSectionValidated: businessSectionOk || false,
    termsValidated: termsOk || false,
    privacyValidated: privacyOk || false,
  };

  const reportPath = path.join(outputDir, `final_report_${timestamp()}.json`);
  await fs.writeFile(reportPath, JSON.stringify(report, null, 2), 'utf8');
  test.info().attach('final_report_path', {
    body: Buffer.from(reportPath, 'utf8'),
    contentType: 'text/plain',
  });

  expect(report.finalResult.Login, 'Login is mandatory to proceed.').toBe('PASS');
  expect(report.summary.failed, 'One or more workflow validations failed.').toBe(0);
});
