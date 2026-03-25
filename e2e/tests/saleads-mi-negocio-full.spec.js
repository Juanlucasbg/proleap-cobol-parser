const fs = require('fs');
const path = require('path');
const { test, expect } = require('@playwright/test');

const GOOGLE_ACCOUNT_EMAIL = 'juanlucasbarbiergarzon@gmail.com';
const TEST_BUSINESS_NAME = 'Negocio Prueba Automatización';
const SCREENSHOTS_DIR = 'test-results/screenshots';

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function normalizeText(value) {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim();
}

function makeReport() {
  return {
    Login: 'FAIL',
    'Mi Negocio menu': 'FAIL',
    'Agregar Negocio modal': 'FAIL',
    'Administrar Negocios view': 'FAIL',
    'Información General': 'FAIL',
    'Detalles de la Cuenta': 'FAIL',
    'Tus Negocios': 'FAIL',
    'Términos y Condiciones': 'FAIL',
    'Política de Privacidad': 'FAIL'
  };
}

async function waitForUiSettled(page) {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(750);
}

function ensureArtifactsDirectories() {
  fs.mkdirSync(path.resolve(process.cwd(), 'test-results'), { recursive: true });
  fs.mkdirSync(path.resolve(process.cwd(), SCREENSHOTS_DIR), { recursive: true });
}

async function clickByVisibleText(page, text, options = {}) {
  const locator = page.getByText(text, { exact: options.exact ?? true }).first();
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiSettled(page);
}

async function clickByRoleButton(page, nameRegex) {
  const button = page.getByRole('button', { name: nameRegex }).first();
  await expect(button).toBeVisible();
  await button.click();
  await waitForUiSettled(page);
}

async function ensureOnLoginPage(page) {
  const googleButton = page
    .locator('button, a, [role="button"]')
    .filter({ hasText: /Google|Sign in|Iniciar sesi[oó]n/i })
    .first();
  await expect(googleButton).toBeVisible();
}

async function doGoogleLogin(page) {
  const loginButton = page
    .locator('button, a, [role="button"]')
    .filter({ hasText: /Google|Sign in with Google|Continuar con Google|Iniciar sesi[oó]n con Google/i })
    .first();

  await expect(loginButton).toBeVisible();

  const popupPromise = page.waitForEvent('popup', { timeout: 10000 }).catch(() => null);
  await loginButton.click();
  await waitForUiSettled(page);

  const popup = await popupPromise;
  const googlePage = popup || page;

  await Promise.race([
    googlePage.waitForURL(/google\./i, { timeout: 10000 }).catch(() => null),
    googlePage.waitForTimeout(3000)
  ]);

  const googleAccount = googlePage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
  if (await googleAccount.isVisible().catch(() => false)) {
    await googleAccount.click();
  }

  if (popup) {
    await Promise.race([popup.waitForEvent('close', { timeout: 20000 }).catch(() => null), page.waitForTimeout(2000)]);
    await page.bringToFront();
  }

  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(1000);
}

async function expandMiNegocioMenu(page) {
  const negocioSection = page.getByText('Negocio', { exact: true }).first();
  await expect(negocioSection).toBeVisible();
  await negocioSection.click();
  await waitForUiSettled(page);

  const miNegocioTrigger = page.getByText('Mi Negocio', { exact: true }).first();
  await expect(miNegocioTrigger).toBeVisible();
  await miNegocioTrigger.click();
  await waitForUiSettled(page);
}

async function validateSectionExists(page, name) {
  const heading = page.getByText(name, { exact: true }).first();
  await expect(heading).toBeVisible();
}

async function clickLegalLinkAndCapture(page, linkName, screenshotName) {
  const appPage = page;
  const link = appPage.getByRole('link', { name: new RegExp(`^${linkName}$`, 'i') }).first();
  await expect(link).toBeVisible();

  const popupPromise = appPage.waitForEvent('popup', { timeout: 10000 }).catch(() => null);
  await link.click();
  await waitForUiSettled(appPage);

  let legalPage = await popupPromise;
  if (!legalPage) {
    legalPage = appPage;
  }

  await legalPage.waitForLoadState('domcontentloaded');
  await legalPage.waitForTimeout(1200);

  const mainHeading = legalPage.getByRole('heading', { name: new RegExp(linkName, 'i') }).first();
  if (await mainHeading.isVisible().catch(() => false)) {
    await expect(mainHeading).toBeVisible();
  } else {
    await expect(legalPage.getByText(linkName, { exact: false }).first()).toBeVisible();
  }

  const pageText = normalizeText(await legalPage.locator('body').innerText());
  expect(pageText.length).toBeGreaterThan(200);

  await legalPage.screenshot({
    path: `test-results/screenshots/${screenshotName}`,
    fullPage: true
  });

  const finalUrl = legalPage.url();
  test.info().attachments.push({
    name: `${screenshotName}.url.txt`,
    contentType: 'text/plain',
    body: Buffer.from(finalUrl, 'utf-8')
  });

  if (legalPage !== appPage) {
    await legalPage.close();
    await appPage.bringToFront();
    await waitForUiSettled(appPage);
  }
}

test('saleads_mi_negocio_full_test', async ({ page }) => {
  const report = makeReport();
  ensureArtifactsDirectories();

  try {
    const saleadsLoginUrl = process.env.SALEADS_LOGIN_URL;
    if (page.url() === 'about:blank' && saleadsLoginUrl) {
      await page.goto(saleadsLoginUrl, { waitUntil: 'domcontentloaded' });
      await waitForUiSettled(page);
    }

    // Step 1: Login with Google.
    await ensureOnLoginPage(page);
    await doGoogleLogin(page);
    await expect(page.locator('aside')).toBeVisible();

    await page.screenshot({
      path: `${SCREENSHOTS_DIR}/01-dashboard-loaded.png`,
      fullPage: true
    });
    report.Login = 'PASS';

    // Step 2: Open Mi Negocio menu.
    await expandMiNegocioMenu(page);
    await expect(page.getByText('Agregar Negocio', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('Administrar Negocios', { exact: true }).first()).toBeVisible();

    await page.screenshot({
      path: `${SCREENSHOTS_DIR}/02-mi-negocio-menu-expanded.png`,
      fullPage: true
    });
    report['Mi Negocio menu'] = 'PASS';

    // Step 3: Validate Agregar Negocio modal.
    await clickByVisibleText(page, 'Agregar Negocio');
    await expect(page.getByText('Crear Nuevo Negocio', { exact: true })).toBeVisible();
    await expect(page.getByLabel('Nombre del Negocio', { exact: true })).toBeVisible();
    await expect(page.getByText('Tienes 2 de 3 negocios', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: /^Cancelar$/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /^Crear Negocio$/i })).toBeVisible();

    await page.getByLabel('Nombre del Negocio', { exact: true }).fill(TEST_BUSINESS_NAME);

    await page.screenshot({
      path: `${SCREENSHOTS_DIR}/03-agregar-negocio-modal.png`,
      fullPage: true
    });

    await clickByRoleButton(page, /^Cancelar$/i);
    report['Agregar Negocio modal'] = 'PASS';

    // Step 4: Open Administrar Negocios.
    if (!(await page.getByText('Administrar Negocios', { exact: true }).first().isVisible().catch(() => false))) {
      await expandMiNegocioMenu(page);
    }
    await clickByVisibleText(page, 'Administrar Negocios');

    await validateSectionExists(page, 'Información General');
    await validateSectionExists(page, 'Detalles de la Cuenta');
    await validateSectionExists(page, 'Tus Negocios');
    await validateSectionExists(page, 'Sección Legal');

    await page.screenshot({
      path: `${SCREENSHOTS_DIR}/04-administrar-negocios.png`,
      fullPage: true
    });
    report['Administrar Negocios view'] = 'PASS';

    // Step 5: Validate Información General.
    const infoSection = page
      .locator('section, div')
      .filter({ hasText: /Informaci[oó]n General/i })
      .first();
    await expect(infoSection).toContainText(/BUSINESS PLAN/i);
    await expect(page.getByRole('button', { name: /Cambiar Plan/i }).first()).toBeVisible();
    await expect(infoSection).toContainText(/@/);
    const infoSectionText = await infoSection.innerText();
    const normalizedInfo = normalizeText(infoSectionText);
    const emailUserPart = normalizeText(GOOGLE_ACCOUNT_EMAIL.split('@')[0]);
    const hasLikelyNameLine = infoSectionText
      .split('\n')
      .map((line) => line.trim())
      .some(
        (line) =>
          line.length >= 3 &&
          !line.includes('@') &&
          !/informaci[oó]n general|business plan|cambiar plan/i.test(line)
      );
    const hasGoogleStem = new RegExp(escapeRegExp(emailUserPart.slice(0, 8)), 'i').test(normalizedInfo);
    expect(hasLikelyNameLine || hasGoogleStem).toBeTruthy();
    report['Información General'] = 'PASS';

    // Step 6: Validate Detalles de la Cuenta.
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
    report['Detalles de la Cuenta'] = 'PASS';

    // Step 7: Validate Tus Negocios.
    const negociosSection = page
      .locator('section, div')
      .filter({ hasText: /Tus Negocios/i })
      .first();
    await expect(negociosSection).toBeVisible();
    await expect(negociosSection.getByRole('button', { name: /Agregar Negocio/i }).first()).toBeVisible();
    await expect(negociosSection.getByText('Tienes 2 de 3 negocios', { exact: true }).first()).toBeVisible();
    report['Tus Negocios'] = 'PASS';

    // Step 8: Validate Términos y Condiciones.
    await clickLegalLinkAndCapture(
      page,
      'Términos y Condiciones',
      '05-terminos-y-condiciones.png'
    );
    report['Términos y Condiciones'] = 'PASS';

    // Step 9: Validate Política de Privacidad.
    await clickLegalLinkAndCapture(
      page,
      'Política de Privacidad',
      '06-politica-de-privacidad.png'
    );
    report['Política de Privacidad'] = 'PASS';
  } finally {
    // Step 10: Final report.
    const reportBody = JSON.stringify(report, null, 2);
    test.info().attachments.push({
      name: 'final-report.json',
      contentType: 'application/json',
      body: Buffer.from(reportBody, 'utf-8')
    });
    // eslint-disable-next-line no-console
    console.log(`Final Report\n${reportBody}`);
  }
});
