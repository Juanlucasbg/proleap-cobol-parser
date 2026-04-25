const { test, expect } = require('@playwright/test');
const fs = require('fs/promises');

const REPORT_NAME = 'saleads-mi-negocio-full-report.json';
const GOOGLE_ACCOUNT_EMAIL = 'juanlucasbarbiergarzon@gmail.com';

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

function createReport() {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, 'FAIL']));
}

async function waitForUi(page) {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {
    // Some environments keep websocket/network activity alive.
  });
}

function safeName(raw) {
  return raw
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-|-$)/g, '');
}

async function checkpoint(page, testInfo, name, fullPage = false) {
  const fileName = `${testInfo.retry}-${safeName(name)}.png`;
  await page.screenshot({
    path: testInfo.outputPath(fileName),
    fullPage,
  });
}

async function clickByVisibleText(page, candidates) {
  const options = Array.isArray(candidates) ? candidates : [candidates];
  for (const text of options) {
    const link = page.getByRole('link', { name: text, exact: true });
    if (await link.first().isVisible().catch(() => false)) {
      await link.first().click();
      await waitForUi(page);
      return true;
    }

    const button = page.getByRole('button', { name: text, exact: true });
    if (await button.first().isVisible().catch(() => false)) {
      await button.first().click();
      await waitForUi(page);
      return true;
    }

    const generic = page.getByText(text, { exact: true });
    if (await generic.first().isVisible().catch(() => false)) {
      await generic.first().click();
      await waitForUi(page);
      return true;
    }
  }

  return false;
}

async function firstVisibleLocator(locators) {
  for (const locator of locators) {
    if (await locator.first().isVisible().catch(() => false)) {
      return locator.first();
    }
  }
  return null;
}

async function fillBusinessNameField(page, value) {
  const input = await firstVisibleLocator([
    page.getByLabel('Nombre del Negocio'),
    page.getByPlaceholder('Nombre del Negocio'),
    page.getByRole('textbox', { name: /Nombre del Negocio/i }),
  ]);

  expect(input, 'Nombre del Negocio input field was not found').toBeTruthy();
  await input.fill(value);
}

async function completeGoogleSelectionIfNeeded(candidatePage) {
  const googleAccountOption = candidatePage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });
  if (await googleAccountOption.first().isVisible().catch(() => false)) {
    await googleAccountOption.first().click();
    await waitForUi(candidatePage);
    return true;
  }

  return false;
}

async function ensureExpandedMiNegocio(page) {
  const hasAgregar = await page.getByText('Agregar Negocio', { exact: true }).first().isVisible().catch(() => false);
  const hasAdmin = await page
    .getByText('Administrar Negocios', { exact: true })
    .first()
    .isVisible()
    .catch(() => false);

  if (hasAgregar && hasAdmin) {
    return;
  }

  await clickByVisibleText(page, ['Mi Negocio', 'Negocio']);
  await expect(page.getByText('Agregar Negocio', { exact: true })).toBeVisible();
  await expect(page.getByText('Administrar Negocios', { exact: true })).toBeVisible();
}

async function openLegalDocument(page, linkText, headingText, testInfo, screenshotLabel) {
  const appPage = page;
  let targetPage = appPage;

  const popupPromise = appPage.waitForEvent('popup', { timeout: 7000 }).catch(() => null);
  const clicked = await clickByVisibleText(appPage, linkText);
  expect(clicked, `Could not click legal link: ${linkText}`).toBeTruthy();

  const popup = await popupPromise;
  if (popup) {
    targetPage = popup;
  }

  await waitForUi(targetPage);
  const legalHeading = await firstVisibleLocator([
    targetPage.getByRole('heading', { name: headingText }),
    targetPage.getByText(headingText, { exact: true }),
  ]);
  expect(legalHeading, `Could not find heading "${headingText}"`).toBeTruthy();

  // Validate that the legal page has meaningful content body.
  const bodyText = (await targetPage.locator('body').innerText()).trim();
  expect(bodyText.length).toBeGreaterThan(80);

  await checkpoint(targetPage, testInfo, screenshotLabel, true);
  const finalUrl = targetPage.url();

  if (targetPage !== appPage) {
    await targetPage.close();
    await appPage.bringToFront();
  } else {
    await appPage.goBack().catch(() => {});
    await waitForUi(appPage);
  }

  return finalUrl;
}

test.describe('saleads_mi_negocio_full_test', () => {
  test('Login to SaleADS via Google and validate Mi Negocio module workflow', async ({ page }, testInfo) => {
    const report = createReport();
    const evidence = {
      termsUrl: null,
      privacyUrl: null,
    };
    try {
      // Step 1: Login with Google
      await waitForUi(page);
      const loginPopupPromise = page.waitForEvent('popup', { timeout: 7000 }).catch(() => null);
      const loginClicked = await clickByVisibleText(page, [
        'Sign in with Google',
        'Iniciar sesión con Google',
        'Continuar con Google',
        'Google',
      ]);
      expect(loginClicked, 'Google login button not found').toBeTruthy();

      // If Google account chooser appears, pick configured account.
      const googlePopup = await loginPopupPromise;
      if (googlePopup) {
        await waitForUi(googlePopup);
        await completeGoogleSelectionIfNeeded(googlePopup);
        await page.bringToFront();
        await waitForUi(page);
      } else {
        await completeGoogleSelectionIfNeeded(page);
      }

      await expect(page.locator('aside')).toBeVisible();
      report.Login = 'PASS';
      await checkpoint(page, testInfo, 'dashboard-loaded');

      // Step 2: Open Mi Negocio menu
      await clickByVisibleText(page, ['Negocio', 'Mi Negocio']);
      await expect(page.getByText('Agregar Negocio', { exact: true })).toBeVisible();
      await expect(page.getByText('Administrar Negocios', { exact: true })).toBeVisible();
      report['Mi Negocio menu'] = 'PASS';
      await checkpoint(page, testInfo, 'mi-negocio-expanded-menu');

      // Step 3: Validate Agregar Negocio modal
      await clickByVisibleText(page, 'Agregar Negocio');
      await expect(page.getByText('Crear Nuevo Negocio', { exact: true })).toBeVisible();
      await fillBusinessNameField(page, '');
      await expect(page.getByText('Tienes 2 de 3 negocios', { exact: true })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Cancelar', exact: true })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Crear Negocio', exact: true })).toBeVisible();
      await checkpoint(page, testInfo, 'agregar-negocio-modal');

      await fillBusinessNameField(page, 'Negocio Prueba Automatizacion');
      await page.getByRole('button', { name: 'Cancelar', exact: true }).click();
      await waitForUi(page);
      report['Agregar Negocio modal'] = 'PASS';

      // Step 4: Open Administrar Negocios
      await ensureExpandedMiNegocio(page);
      await clickByVisibleText(page, 'Administrar Negocios');
      await expect(page.getByText('Información General', { exact: true })).toBeVisible();
      await expect(page.getByText('Detalles de la Cuenta', { exact: true })).toBeVisible();
      await expect(page.getByText('Tus Negocios', { exact: true })).toBeVisible();
      await expect(page.getByText('Sección Legal', { exact: true })).toBeVisible();
      report['Administrar Negocios view'] = 'PASS';
      await checkpoint(page, testInfo, 'administrar-negocios-view', true);

      // Step 5: Validate Información General
      await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
      await expect(page.getByRole('button', { name: 'Cambiar Plan', exact: true })).toBeVisible();

      const emailVisible = await page.getByText(/@/).first().isVisible().catch(() => false);
      expect(emailVisible, 'Expected a visible user email in Información General').toBeTruthy();

      // Name heuristic: presence of a non-empty heading/value in account area.
      const infoGeneralSection = page.locator('section, div').filter({ hasText: 'Información General' }).first();
      await expect(infoGeneralSection).toBeVisible();
      const infoText = (await infoGeneralSection.innerText()).trim();
      expect(infoText.length).toBeGreaterThan(20);
      report['Información General'] = 'PASS';

      // Step 6: Validate Detalles de la Cuenta
      await expect(page.getByText('Cuenta creada', { exact: false })).toBeVisible();
      await expect(page.getByText('Estado activo', { exact: false })).toBeVisible();
      await expect(page.getByText('Idioma seleccionado', { exact: false })).toBeVisible();
      report['Detalles de la Cuenta'] = 'PASS';

      // Step 7: Validate Tus Negocios
      await expect(page.getByText('Tus Negocios', { exact: true })).toBeVisible();
      await expect(page.getByRole('button', { name: 'Agregar Negocio', exact: true })).toBeVisible();
      await expect(page.getByText('Tienes 2 de 3 negocios', { exact: true })).toBeVisible();
      report['Tus Negocios'] = 'PASS';

      // Step 8: Validate Términos y Condiciones
      evidence.termsUrl = await openLegalDocument(
        page,
        'Términos y Condiciones',
        'Términos y Condiciones',
        testInfo,
        'terminos-y-condiciones'
      );
      report['Términos y Condiciones'] = 'PASS';

      // Step 9: Validate Política de Privacidad
      evidence.privacyUrl = await openLegalDocument(
        page,
        'Política de Privacidad',
        'Política de Privacidad',
        testInfo,
        'politica-de-privacidad'
      );
      report['Política de Privacidad'] = 'PASS';
    } finally {
      // Step 10: Final report output
      const finalReport = {
        testName: 'saleads_mi_negocio_full_test',
        generatedAt: new Date().toISOString(),
        results: report,
        evidence,
      };

      const reportPath = testInfo.outputPath(REPORT_NAME);
      await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), 'utf8');

      await testInfo.attach('final-report', {
        path: reportPath,
        contentType: 'application/json',
      });
    }
  });
});
