const { test, expect } = require('@playwright/test');
const fs = require('fs/promises');
const path = require('path');

const outputRoot = path.resolve(__dirname, 'artifacts');
const screenshotDir = path.join(outputRoot, 'screenshots');
const reportDir = path.join(outputRoot, 'reports');

const APP_URL = process.env.SALEADS_URL;
const GOOGLE_ACCOUNT_EMAIL = process.env.SALEADS_GOOGLE_ACCOUNT || 'juanlucasbarbiergarzon@gmail.com';

const stepStatus = {
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

async function ensureDirs() {
  await fs.mkdir(screenshotDir, { recursive: true });
  await fs.mkdir(reportDir, { recursive: true });
}

async function checkpoint(page, name, fullPage = false) {
  const fileName = `${name}.png`.replace(/[^\w.-]+/g, '_');
  await page.screenshot({
    path: path.join(screenshotDir, fileName),
    fullPage,
  });
}

async function waitForStableUI(page) {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(600);
}

async function clickByText(page, text, opts = {}) {
  const locator = page.getByText(text, { exact: opts.exact ?? false }).first();
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForStableUI(page);
}

function sectionByHeading(page, headingText) {
  const heading = page.getByText(headingText, { exact: false }).first();
  return heading.locator('xpath=ancestor-or-self::*[self::section or self::div][1]');
}

async function maybeHandleGooglePopup(context) {
  const popup = context
    .pages()
    .find((p) => /accounts\.google\.com/i.test(p.url()) || /google/i.test(p.url()));
  if (!popup) return;

  await popup.bringToFront();
  const accountChoice = popup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
  if (await accountChoice.isVisible().catch(() => false)) {
    await accountChoice.click();
    await popup.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => {});
  }
}

async function saveFinalReport(extra = {}) {
  const report = {
    generatedAt: new Date().toISOString(),
    scenario: 'saleads_mi_negocio_full_test',
    appUrl: APP_URL || null,
    steps: stepStatus,
    ...extra,
  };

  await fs.writeFile(path.join(reportDir, 'saleads-mi-negocio-final-report.json'), JSON.stringify(report, null, 2), 'utf-8');
}

test.describe('SaleADS Mi Negocio full workflow', () => {
  test('login with Google and validate Mi Negocio module', async ({ page, context }) => {
    test.skip(!APP_URL, 'Set SALEADS_URL to run this test in the current environment.');
    await ensureDirs();

    const legalUrls = {
      terminos: null,
      privacidad: null,
    };

    try {
      // Step 1: Login with Google
      await page.goto(APP_URL, { waitUntil: 'domcontentloaded' });
      await waitForStableUI(page);

      const googleLoginButton = page
        .getByRole('button', { name: /google|sign in with google|continuar con google|iniciar sesi[oó]n/i })
        .first();
      await expect(googleLoginButton).toBeVisible();
      await googleLoginButton.click();
      await waitForStableUI(page);
      await maybeHandleGooglePopup(context);

      const dashboardReady = page.locator('aside, nav').first();
      await expect(dashboardReady).toBeVisible({ timeout: 120000 });
      await checkpoint(page, '01-dashboard-loaded');
      stepStatus.Login = 'PASS';

      // Step 2: Open Mi Negocio menu
      await clickByText(page, 'Negocio');
      await clickByText(page, 'Mi Negocio');
      await expect(page.getByText('Agregar Negocio', { exact: false }).first()).toBeVisible();
      await expect(page.getByText('Administrar Negocios', { exact: false }).first()).toBeVisible();
      await checkpoint(page, '02-mi-negocio-menu-expanded');
      stepStatus['Mi Negocio menu'] = 'PASS';

      // Step 3: Validate Agregar Negocio modal
      await clickByText(page, 'Agregar Negocio');
      await expect(page.getByText('Crear Nuevo Negocio', { exact: false }).first()).toBeVisible();
      const nombreInput = page.getByLabel('Nombre del Negocio', { exact: false }).or(page.getByPlaceholder('Nombre del Negocio'));
      await expect(nombreInput.first()).toBeVisible();
      await expect(page.getByText('Tienes 2 de 3 negocios', { exact: false }).first()).toBeVisible();
      await expect(page.getByRole('button', { name: 'Cancelar' }).first()).toBeVisible();
      await expect(page.getByRole('button', { name: 'Crear Negocio' }).first()).toBeVisible();
      await checkpoint(page, '03-agregar-negocio-modal');

      // Optional action requested by spec.
      await nombreInput.first().click();
      await nombreInput.first().fill('Negocio Prueba Automatización');
      await page.getByRole('button', { name: 'Cancelar' }).first().click();
      await waitForStableUI(page);
      stepStatus['Agregar Negocio modal'] = 'PASS';

      // Step 4: Open Administrar Negocios
      const administrarNegocios = page.getByText('Administrar Negocios', { exact: false }).first();
      if (!(await administrarNegocios.isVisible().catch(() => false))) {
        await clickByText(page, 'Mi Negocio');
      }

      await administrarNegocios.click();
      await waitForStableUI(page);

      await expect(page.getByText('Información General', { exact: false }).first()).toBeVisible();
      await expect(page.getByText('Detalles de la Cuenta', { exact: false }).first()).toBeVisible();
      await expect(page.getByText('Tus Negocios', { exact: false }).first()).toBeVisible();
      await expect(page.getByText('Sección Legal', { exact: false }).first()).toBeVisible();
      await checkpoint(page, '04-administrar-negocios-view', true);
      stepStatus['Administrar Negocios view'] = 'PASS';

      // Step 5: Validate Información General
      const infoGeneralSection = sectionByHeading(page, 'Información General');
      await expect(infoGeneralSection.getByText(/@/, { exact: false }).first()).toBeVisible();
      // Name should appear as normal text in this card, separate from the email value.
      await expect(infoGeneralSection.locator('p,span,h1,h2,h3,h4,h5,div').filter({ hasNotText: /@/ }).first()).toBeVisible();
      await expect(page.getByText('BUSINESS PLAN', { exact: false }).first()).toBeVisible();
      await expect(page.getByRole('button', { name: 'Cambiar Plan' }).first()).toBeVisible();
      stepStatus['Información General'] = 'PASS';

      // Step 6: Validate Detalles de la Cuenta
      await expect(page.getByText('Cuenta creada', { exact: false }).first()).toBeVisible();
      await expect(page.getByText('Estado activo', { exact: false }).first()).toBeVisible();
      await expect(page.getByText('Idioma seleccionado', { exact: false }).first()).toBeVisible();
      stepStatus['Detalles de la Cuenta'] = 'PASS';

      // Step 7: Validate Tus Negocios
      await expect(page.getByText('Tus Negocios', { exact: false }).first()).toBeVisible();
      await expect(page.getByRole('button', { name: 'Agregar Negocio' }).first()).toBeVisible();
      await expect(page.getByText('Tienes 2 de 3 negocios', { exact: false }).first()).toBeVisible();
      stepStatus['Tus Negocios'] = 'PASS';

      // Step 8: Validate Términos y Condiciones
      const appPage = page;
      const appUrlBeforeTerms = appPage.url();
      const termsPagePromise = context.waitForEvent('page', { timeout: 5000 }).catch(() => null);
      await clickByText(page, 'Términos y Condiciones');
      const termsPage = await termsPagePromise;

      if (termsPage) {
        await termsPage.waitForLoadState('domcontentloaded');
        await expect(termsPage.getByText('Términos y Condiciones', { exact: false }).first()).toBeVisible();
        await expect(termsPage.locator('body')).toContainText(/t[eé]rminos|condiciones/i);
        legalUrls.terminos = termsPage.url();
        await checkpoint(termsPage, '08-terminos-y-condiciones');
        await appPage.bringToFront();
      } else {
        await expect(page.getByText('Términos y Condiciones', { exact: false }).first()).toBeVisible();
        await expect(page.locator('body')).toContainText(/t[eé]rminos|condiciones/i);
        legalUrls.terminos = page.url();
        await checkpoint(page, '08-terminos-y-condiciones');
        if (page.url() !== appUrlBeforeTerms) {
          await page.goBack().catch(() => {});
          await waitForStableUI(page);
        }
      }
      stepStatus['Términos y Condiciones'] = 'PASS';

      // Step 9: Validate Política de Privacidad
      const appUrlBeforePrivacy = appPage.url();
      const privacyPagePromise = context.waitForEvent('page', { timeout: 5000 }).catch(() => null);
      await clickByText(page, 'Política de Privacidad');
      const privacyPage = await privacyPagePromise;

      if (privacyPage) {
        await privacyPage.waitForLoadState('domcontentloaded');
        await expect(privacyPage.getByText('Política de Privacidad', { exact: false }).first()).toBeVisible();
        await expect(privacyPage.locator('body')).toContainText(/privacidad|datos/i);
        legalUrls.privacidad = privacyPage.url();
        await checkpoint(privacyPage, '09-politica-de-privacidad');
        await appPage.bringToFront();
      } else {
        await expect(page.getByText('Política de Privacidad', { exact: false }).first()).toBeVisible();
        await expect(page.locator('body')).toContainText(/privacidad|datos/i);
        legalUrls.privacidad = page.url();
        await checkpoint(page, '09-politica-de-privacidad');
        if (page.url() !== appUrlBeforePrivacy) {
          await page.goBack().catch(() => {});
          await waitForStableUI(page);
        }
      }
      stepStatus['Política de Privacidad'] = 'PASS';

      // Step 10: Final report
      await saveFinalReport({ legalUrls });
    } catch (error) {
      await saveFinalReport({
        legalUrls,
        error: error instanceof Error ? error.message : String(error),
      });
      throw error;
    }
  });
});
