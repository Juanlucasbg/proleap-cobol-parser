const fs = require('node:fs/promises');
const { test, expect } = require('@playwright/test');

const REPORT_FIELDS = [
  'Login',
  'Mi Negocio menu',
  'Agregar Negocio modal',
  'Administrar Negocios view',
  'Información General',
  'Detalles de la Cuenta',
  'Tus Negocios',
  'Términos y Condiciones',
  'Política de Privacidad',
];

async function waitForUiLoad(page) {
  await page.waitForLoadState('domcontentloaded', { timeout: 45000 }).catch(() => {});
  await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function isVisible(locator, timeout = 2000) {
  try {
    await locator.waitFor({ state: 'visible', timeout });
    return true;
  } catch (_error) {
    return false;
  }
}

async function clickByVisibleText(page, textMatcher, description) {
  const candidates = [
    page.getByRole('button', { name: textMatcher }).first(),
    page.getByRole('link', { name: textMatcher }).first(),
    page.getByRole('menuitem', { name: textMatcher }).first(),
    page.getByRole('tab', { name: textMatcher }).first(),
    page.getByText(textMatcher).first(),
  ];

  for (const locator of candidates) {
    if (await isVisible(locator)) {
      await locator.click();
      await waitForUiLoad(page);
      return;
    }
  }

  throw new Error(`Could not find clickable element for: ${description}`);
}

async function captureCheckpoint(page, testInfo, checkpointName, fullPage = false) {
  const screenshotPath = testInfo.outputPath(`${checkpointName}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(checkpointName, { path: screenshotPath, contentType: 'image/png' });
}

async function withStepReport(report, failures, fieldName, action) {
  try {
    await action();
    report[fieldName] = 'PASS';
  } catch (error) {
    report[fieldName] = 'FAIL';
    failures.push(`${fieldName}: ${error.message}`);
  }
}

async function openLegalDocumentAndReturn(appPage, testInfo, linkPattern, headingPattern, screenshotName) {
  const popupPromise = appPage.context().waitForEvent('page', { timeout: 8000 }).catch(() => null);
  await clickByVisibleText(appPage, linkPattern, `${linkPattern} link`);

  let legalPage = await popupPromise;
  if (!legalPage) {
    legalPage = appPage;
  }

  await waitForUiLoad(legalPage);

  const legalHeading = legalPage.getByRole('heading', { name: headingPattern }).first();
  if (await isVisible(legalHeading, 4000)) {
    await expect(legalHeading).toBeVisible();
  } else {
    await expect(legalPage.getByText(headingPattern).first()).toBeVisible();
  }

  const legalText = (await legalPage.locator('body').innerText()).replace(/\s+/g, ' ').trim();
  expect(legalText.length, 'Legal content text should be visible').toBeGreaterThan(120);
  await captureCheckpoint(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();

  if (legalPage !== appPage) {
    await legalPage.close();
    await appPage.bringToFront();
    await waitForUiLoad(appPage);
  } else {
    await appPage.goBack({ waitUntil: 'domcontentloaded' }).catch(() => {});
    await waitForUiLoad(appPage);
  }

  return finalUrl;
}

test('saleads_mi_negocio_full_test', async ({ page }, testInfo) => {
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, 'FAIL']));
  const failures = [];
  const evidence = {};

  const configuredLoginUrl = process.env.SALEADS_LOGIN_URL;
  if (configuredLoginUrl) {
    await page.goto(configuredLoginUrl, { waitUntil: 'domcontentloaded' });
    await waitForUiLoad(page);
  } else if (page.url() === 'about:blank') {
    failures.push(
      'Login: Set SALEADS_LOGIN_URL or start the test with a browser already opened on the SaleADS login page.',
    );
  }

  await withStepReport(report, failures, 'Login', async () => {
    await clickByVisibleText(
      page,
      /Sign in with Google|Iniciar sesi[oó]n con Google|Continuar con Google|Google/i,
      'Google login button',
    );

    const accountOption = page.getByText('juanlucasbarbiergarzon@gmail.com').first();
    if (await isVisible(accountOption, 10000)) {
      await accountOption.click();
      await waitForUiLoad(page);
    }

    const sidebar = page.locator('aside, nav, [class*="sidebar"], [data-testid*="sidebar"]').first();
    await expect(sidebar).toBeVisible({ timeout: 45000 });
    await expect(page.getByText(/Negocio/i).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, '01-dashboard-loaded', true);
  });

  await withStepReport(report, failures, 'Mi Negocio menu', async () => {
    await clickByVisibleText(page, /Mi Negocio/i, 'Mi Negocio menu');
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, '02-mi-negocio-menu-expanded');
  });

  await withStepReport(report, failures, 'Agregar Negocio modal', async () => {
    await clickByVisibleText(page, /^Agregar Negocio$/i, 'Agregar Negocio option');
    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Nombre del Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /Cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /Crear Negocio/i }).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, '03-agregar-negocio-modal');

    const negocioInput = page.getByLabel(/Nombre del Negocio/i).first();
    if (await isVisible(negocioInput, 3000)) {
      await negocioInput.fill('Negocio Prueba Automatizacion');
      await page.waitForTimeout(200);
    }

    await clickByVisibleText(page, /^Cancelar$/i, 'Cancelar modal');
  });

  await withStepReport(report, failures, 'Administrar Negocios view', async () => {
    if (!(await isVisible(page.getByText(/Administrar Negocios/i).first(), 3000))) {
      await clickByVisibleText(page, /Mi Negocio/i, 'Mi Negocio re-expand');
    }

    await clickByVisibleText(page, /Administrar Negocios/i, 'Administrar Negocios option');
    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, '04-administrar-negocios-full-page', true);
  });

  await withStepReport(report, failures, 'Información General', async () => {
    const infoSection = page
      .getByText(/Informaci[oó]n General/i)
      .first()
      .locator('xpath=ancestor::*[self::section or self::div][1]');

    await expect(infoSection).toBeVisible();
    await expect(infoSection.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/).first()).toBeVisible();
    await expect(infoSection.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(infoSection.getByRole('button', { name: /Cambiar Plan/i }).first()).toBeVisible();

    const infoText = (await infoSection.innerText()).split('\n').map((text) => text.trim());
    const nameCandidate = infoText.find(
      (text) =>
        text &&
        !text.includes('@') &&
        !/Informaci[oó]n General|BUSINESS PLAN|Cambiar Plan/i.test(text) &&
        text.length > 2,
    );

    expect(nameCandidate, 'User name should be visible in Información General section').toBeTruthy();
  });

  await withStepReport(report, failures, 'Detalles de la Cuenta', async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await withStepReport(report, failures, 'Tus Negocios', async () => {
    const negociosSection = page
      .getByText(/Tus Negocios/i)
      .first()
      .locator('xpath=ancestor::*[self::section or self::div][1]');

    await expect(negociosSection).toBeVisible();
    await expect(negociosSection.getByRole('button', { name: /Agregar Negocio/i }).first()).toBeVisible();
    await expect(negociosSection.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
  });

  await withStepReport(report, failures, 'Términos y Condiciones', async () => {
    evidence.terminosUrl = await openLegalDocumentAndReturn(
      page,
      testInfo,
      /T[eé]rminos y Condiciones/i,
      /T[eé]rminos y Condiciones/i,
      '05-terminos-y-condiciones',
    );
  });

  await withStepReport(report, failures, 'Política de Privacidad', async () => {
    evidence.privacidadUrl = await openLegalDocumentAndReturn(
      page,
      testInfo,
      /Pol[ií]tica de Privacidad/i,
      /Pol[ií]tica de Privacidad/i,
      '06-politica-de-privacidad',
    );
  });

  const finalReport = {
    testName: 'saleads_mi_negocio_full_test',
    generatedAt: new Date().toISOString(),
    results: report,
    evidence,
    failures,
  };

  const reportPath = testInfo.outputPath('saleads_mi_negocio_full_report.json');
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), 'utf8');
  await testInfo.attach('saleads-mi-negocio-final-report', {
    path: reportPath,
    contentType: 'application/json',
  });

  console.log(`FINAL_SALEADS_REPORT: ${JSON.stringify(finalReport)}`);
  expect(failures, `Validation failures:\n${failures.join('\n')}`).toEqual([]);
});
