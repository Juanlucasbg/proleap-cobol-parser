const { test, expect } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

const ARTIFACTS_DIR = path.join(__dirname, '..', 'artifacts');
const SCREENSHOTS_DIR = path.join(ARTIFACTS_DIR, 'screenshots');
const REPORT_PATH = path.join(ARTIFACTS_DIR, 'saleads_mi_negocio_full_test.report.json');
const USER_EMAIL = 'juanlucasbarbiergarzon@gmail.com';

function ensureArtifactsDir() {
  fs.mkdirSync(ARTIFACTS_DIR, { recursive: true });
  fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
}

function normalizeText(text) {
  if (!text) return '';
  return text.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase();
}

async function waitForUiLoad(page) {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(750);
}

async function safeScreenshot(page, testInfo, name, options = {}) {
  ensureArtifactsDir();
  const filePath = path.join(SCREENSHOTS_DIR, `${Date.now()}-${name}.png`);
  const image = await page.screenshot({ path: filePath, fullPage: !!options.fullPage });

  await testInfo.attach(name, {
    body: image,
    contentType: 'image/png',
  });
}

function newReport() {
  return {
    testName: 'saleads_mi_negocio_full_test',
    generatedAt: new Date().toISOString(),
    environment: {
      startUrl: process.env.SALEADS_START_URL || null,
      finalAppUrl: null,
      termsUrl: null,
      privacyUrl: null,
    },
    results: {
      Login: 'FAIL',
      'Mi Negocio menu': 'FAIL',
      'Agregar Negocio modal': 'FAIL',
      'Administrar Negocios view': 'FAIL',
      'Información General': 'FAIL',
      'Detalles de la Cuenta': 'FAIL',
      'Tus Negocios': 'FAIL',
      'Términos y Condiciones': 'FAIL',
      'Política de Privacidad': 'FAIL',
    },
    notes: [],
  };
}

async function writeReport(report) {
  ensureArtifactsDir();
  fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2));
}

async function clickByVisibleText(page, candidates, options = {}) {
  for (const candidate of candidates) {
    const button = page.getByRole('button', { name: candidate, exact: false }).first();
    if (await button.isVisible().catch(() => false)) {
      await button.click();
      if (options.waitAfterClick !== false) {
        await waitForUiLoad(page);
      }
      return true;
    }

    const link = page.getByRole('link', { name: candidate, exact: false }).first();
    if (await link.isVisible().catch(() => false)) {
      await link.click();
      if (options.waitAfterClick !== false) {
        await waitForUiLoad(page);
      }
      return true;
    }

    const textNode = page.getByText(candidate, { exact: false }).first();
    if (await textNode.isVisible().catch(() => false)) {
      await textNode.click();
      if (options.waitAfterClick !== false) {
        await waitForUiLoad(page);
      }
      return true;
    }
  }
  return false;
}

async function clickTermsOrPrivacy(page, label) {
  const lowerLabel = normalizeText(label);
  const pagePromise = page.context().waitForEvent('page', { timeout: 5000 }).catch(() => null);

  const clicked = await clickByVisibleText(page, [label], { waitAfterClick: false });
  if (!clicked) {
    throw new Error(`No se encontró enlace/botón para: ${label}`);
  }

  const newPage = await pagePromise;
  if (newPage) {
    await newPage.waitForLoadState('domcontentloaded');
    await newPage.waitForTimeout(800);
    return { legalPage: newPage, openedInNewTab: true };
  }

  await waitForUiLoad(page);
  const bodyText = await page.locator('body').innerText().catch(() => '');
  const matchesLegalPage =
    normalizeText(bodyText).includes(lowerLabel) ||
    normalizeText(await page.title().catch(() => '')).includes(lowerLabel);

  if (!matchesLegalPage) {
    throw new Error(`No se detectó navegación a página legal para: ${label}`);
  }

  return { legalPage: page, openedInNewTab: false };
}

async function isLikelyUserIdentityVisible(page) {
  const text = await page.locator('body').innerText().catch(() => '');
  const hasEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(text);
  // Lightweight signal for a visible person-like name.
  const hasNameLike = /\b[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+){1,2}\b/.test(text);
  return hasEmail && hasNameLike;
}

test('saleads_mi_negocio_full_test', async ({ page }, testInfo) => {
  const report = newReport();
  ensureArtifactsDir();
  await writeReport(report);

  const startUrl =
    process.env.SALEADS_START_URL ||
    process.env.SALEADS_BASE_URL ||
    process.env.BASE_URL ||
    process.env.APP_BASE_URL;
  report.environment.startUrl = startUrl || null;

  if (startUrl) {
    await page.goto(startUrl, { waitUntil: 'domcontentloaded' });
    await waitForUiLoad(page);
  }

  // Step 1: Login with Google
  {
    const clickedLogin = await clickByVisibleText(page, [
      'Sign in with Google',
      'Iniciar sesión con Google',
      'Continuar con Google',
      'Google',
      'Iniciar sesión',
      'Ingresar',
      'Login',
    ]);

    if (!clickedLogin) {
      throw new Error('No se encontró botón de login o Google en la pantalla inicial.');
    }

    // If account picker appears in same page or popup/new tab, try selecting target account.
    const contextPages = page.context().pages();
    for (const p of contextPages) {
      const emailNode = p.getByText(USER_EMAIL, { exact: false }).first();
      if (await emailNode.isVisible().catch(() => false)) {
        await emailNode.click();
        await p.waitForTimeout(500);
      }
    }

    await waitForUiLoad(page);

    // Validate app shell / sidebar appears.
    const sidebarVisible = await Promise.any([
      page.getByRole('navigation').first().isVisible(),
      page.locator('aside').first().isVisible(),
      page.getByText('Negocio', { exact: false }).first().isVisible(),
    ]).catch(() => false);

    expect(sidebarVisible).toBeTruthy();
    report.results.Login = 'PASS';
    report.environment.finalAppUrl = page.url();
    await safeScreenshot(page, testInfo, '01-dashboard-loaded');
    await writeReport(report);
  }

  // Step 2: Open Mi Negocio menu
  {
    const negocioClicked = await clickByVisibleText(page, ['Negocio']);
    expect(negocioClicked).toBeTruthy();

    const miNegocioClicked = await clickByVisibleText(page, ['Mi Negocio']);
    expect(miNegocioClicked).toBeTruthy();

    await expect(page.getByText('Agregar Negocio', { exact: false }).first()).toBeVisible();
    await expect(page.getByText('Administrar Negocios', { exact: false }).first()).toBeVisible();

    report.results['Mi Negocio menu'] = 'PASS';
    await safeScreenshot(page, testInfo, '02-mi-negocio-expanded');
    await writeReport(report);
  }

  // Step 3: Validate Agregar Negocio modal
  {
    const addBusinessClicked = await clickByVisibleText(page, ['Agregar Negocio']);
    expect(addBusinessClicked).toBeTruthy();

    await expect(page.getByText('Crear Nuevo Negocio', { exact: false }).first()).toBeVisible();
    await expect(page.getByText('Nombre del Negocio', { exact: false }).first()).toBeVisible();
    await expect(page.getByText('Tienes 2 de 3 negocios', { exact: false }).first()).toBeVisible();
    await expect(page.getByRole('button', { name: 'Cancelar', exact: false }).first()).toBeVisible();
    await expect(page.getByRole('button', { name: 'Crear Negocio', exact: false }).first()).toBeVisible();

    const input = page
      .getByRole('textbox', { name: /Nombre del Negocio/i })
      .or(page.locator('input[placeholder*="Nombre del Negocio"]'))
      .first();
    if (await input.isVisible().catch(() => false)) {
      await input.click();
      await input.fill('Negocio Prueba Automatización');
    }

    await clickByVisibleText(page, ['Cancelar']);
    await waitForUiLoad(page);

    report.results['Agregar Negocio modal'] = 'PASS';
    await safeScreenshot(page, testInfo, '03-agregar-negocio-modal');
    await writeReport(report);
  }

  // Step 4: Open Administrar Negocios
  {
    const miNegocioVisible = await page.getByText('Administrar Negocios', { exact: false }).first().isVisible().catch(() => false);
    if (!miNegocioVisible) {
      await clickByVisibleText(page, ['Mi Negocio']);
    }

    const manageClicked = await clickByVisibleText(page, ['Administrar Negocios']);
    expect(manageClicked).toBeTruthy();

    await expect(page.getByText('Información General', { exact: false }).first()).toBeVisible();
    await expect(page.getByText('Detalles de la Cuenta', { exact: false }).first()).toBeVisible();
    await expect(page.getByText('Tus Negocios', { exact: false }).first()).toBeVisible();
    await expect(page.getByText('Sección Legal', { exact: false }).first()).toBeVisible();

    report.results['Administrar Negocios view'] = 'PASS';
    await safeScreenshot(page, testInfo, '04-administrar-negocios', { fullPage: true });
    await writeReport(report);
  }

  // Step 5: Validate Información General
  {
    const identityVisible = await isLikelyUserIdentityVisible(page);
    expect(identityVisible).toBeTruthy();
    await expect(page.getByText('BUSINESS PLAN', { exact: false }).first()).toBeVisible();
    await expect(page.getByRole('button', { name: 'Cambiar Plan', exact: false }).first()).toBeVisible();
    report.results['Información General'] = 'PASS';
    await writeReport(report);
  }

  // Step 6: Validate Detalles de la Cuenta
  {
    await expect(page.getByText('Cuenta creada', { exact: false }).first()).toBeVisible();
    await expect(page.getByText('Estado activo', { exact: false }).first()).toBeVisible();
    await expect(page.getByText('Idioma seleccionado', { exact: false }).first()).toBeVisible();
    report.results['Detalles de la Cuenta'] = 'PASS';
    await writeReport(report);
  }

  // Step 7: Validate Tus Negocios
  {
    await expect(page.getByText('Tus Negocios', { exact: false }).first()).toBeVisible();
    await expect(page.getByText('Agregar Negocio', { exact: false }).first()).toBeVisible();
    await expect(page.getByText('Tienes 2 de 3 negocios', { exact: false }).first()).toBeVisible();
    report.results['Tus Negocios'] = 'PASS';
    await writeReport(report);
  }

  // Step 8: Validate Términos y Condiciones
  {
    const { legalPage, openedInNewTab } = await clickTermsOrPrivacy(page, 'Términos y Condiciones');
    await expect(legalPage.getByText('Términos y Condiciones', { exact: false }).first()).toBeVisible();

    const legalText = (await legalPage.locator('body').innerText()).trim();
    expect(legalText.length).toBeGreaterThan(80);

    report.results['Términos y Condiciones'] = 'PASS';
    report.environment.termsUrl = legalPage.url();
    await safeScreenshot(legalPage, testInfo, '08-terminos-y-condiciones');
    await writeReport(report);

    if (openedInNewTab) {
      await legalPage.close();
      await page.bringToFront();
      await waitForUiLoad(page);
    } else {
      await page.goBack().catch(() => Promise.resolve());
      await waitForUiLoad(page);
    }
  }

  // Step 9: Validate Política de Privacidad
  {
    const { legalPage, openedInNewTab } = await clickTermsOrPrivacy(page, 'Política de Privacidad');
    await expect(legalPage.getByText('Política de Privacidad', { exact: false }).first()).toBeVisible();

    const legalText = (await legalPage.locator('body').innerText()).trim();
    expect(legalText.length).toBeGreaterThan(80);

    report.results['Política de Privacidad'] = 'PASS';
    report.environment.privacyUrl = legalPage.url();
    await safeScreenshot(legalPage, testInfo, '09-politica-de-privacidad');
    await writeReport(report);

    if (openedInNewTab) {
      await legalPage.close();
      await page.bringToFront();
      await waitForUiLoad(page);
    } else {
      await page.goBack().catch(() => Promise.resolve());
      await waitForUiLoad(page);
    }
  }

  report.generatedAt = new Date().toISOString();
  await writeReport(report);
  await testInfo.attach('final-report-json', {
    path: REPORT_PATH,
    contentType: 'application/json',
  });
});
