const { test, expect } = require('@playwright/test');
const fs = require('fs/promises');
const path = require('path');

const GOOGLE_ACCOUNT = 'juanlucasbarbiergarzon@gmail.com';
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

function escapeRegExp(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function slugify(text) {
  return text
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

async function waitForUi(page) {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForLoadState('networkidle', { timeout: 20_000 }).catch(() => {});
}

async function saveCheckpoint(page, testInfo, name, fullPage = false) {
  const artifactsDir = path.join(process.cwd(), 'saleads-artifacts');
  await fs.mkdir(artifactsDir, { recursive: true });

  const fileName = `${Date.now()}-${slugify(name)}.png`;
  const outputPath = path.join(artifactsDir, fileName);
  await page.screenshot({ path: outputPath, fullPage });
  await testInfo.attach(name, { path: outputPath, contentType: 'image/png' });
  return outputPath;
}

async function visibleLocator(page, label, scope) {
  const root = scope || page;
  const pattern = new RegExp(escapeRegExp(label), 'i');
  const candidates = [
    root.getByRole('button', { name: pattern }).first(),
    root.getByRole('link', { name: pattern }).first(),
    root.getByRole('menuitem', { name: pattern }).first(),
    root.getByRole('tab', { name: pattern }).first(),
    root.getByRole('heading', { name: pattern }).first(),
    root.getByText(pattern).first(),
  ];

  for (const locator of candidates) {
    if (await locator.isVisible().catch(() => false)) {
      return locator;
    }
  }

  return null;
}

async function clickByLabels(page, labels, options = {}) {
  const { scope, waitAfterClick = true } = options;

  for (const label of labels) {
    const locator = await visibleLocator(page, label, scope);
    if (locator) {
      await locator.click();
      if (waitAfterClick) {
        await waitForUi(page);
      }
      return;
    }
  }

  throw new Error(`Unable to find clickable element. Tried labels: ${labels.join(', ')}`);
}

async function expectTextVisible(page, textOrRegex, scope) {
  const root = scope || page;
  const locator =
    textOrRegex instanceof RegExp ? root.getByText(textOrRegex).first() : root.getByText(new RegExp(escapeRegExp(textOrRegex), 'i')).first();
  await expect(locator).toBeVisible();
}

async function findBusinessNameInput(page) {
  const candidates = [
    page.getByLabel(/Nombre del Negocio/i).first(),
    page.getByPlaceholder(/Nombre del Negocio/i).first(),
    page.locator('input[name*="negocio" i]').first(),
    page.locator('input[id*="negocio" i]').first(),
  ];

  for (const locator of candidates) {
    if (await locator.isVisible().catch(() => false)) {
      return locator;
    }
  }

  throw new Error('Input "Nombre del Negocio" was not found.');
}

async function clickLegalLinkAndValidate({
  page,
  context,
  testInfo,
  label,
  expectedHeading,
  screenshotName,
}) {
  const popupPromise = context.waitForEvent('page', { timeout: 7_000 }).catch(() => null);
  await clickByLabels(page, [label], { waitAfterClick: false });
  const popup = await popupPromise;
  const targetPage = popup || page;

  await waitForUi(targetPage);
  await expectTextVisible(targetPage, expectedHeading);

  const legalBody = (await targetPage.locator('body').innerText()).trim();
  if (legalBody.length < 80) {
    throw new Error(`The legal page content for "${label}" looks unexpectedly short.`);
  }

  await saveCheckpoint(targetPage, testInfo, screenshotName, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await targetPage.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack().catch(() => {});
    await waitForUi(page);
  }

  return finalUrl;
}

test('saleads_mi_negocio_full_test', async ({ page, context, baseURL }, testInfo) => {
  const result = Object.fromEntries(REPORT_FIELDS.map((field) => [field, { status: 'FAIL', details: '' }]));
  const evidence = {
    termsUrl: '',
    privacyUrl: '',
  };

  const runValidation = async (field, stepFn) => {
    try {
      await stepFn();
      result[field] = { status: 'PASS', details: '' };
    } catch (error) {
      result[field] = { status: 'FAIL', details: error.message };
      await saveCheckpoint(page, testInfo, `${field} - failure`, true).catch(() => {});
    }
  };

  if (baseURL) {
    await page.goto(baseURL, { waitUntil: 'domcontentloaded' });
  } else if (page.url() === 'about:blank') {
    throw new Error(
      'No URL was provided and the page is blank. Set SALEADS_BASE_URL (or SALEADS_URL/BASE_URL) or pre-open the SaleADS login page.',
    );
  }

  await waitForUi(page);

  await runValidation('Login', async () => {
    const popupPromise = context.waitForEvent('page', { timeout: 7_000 }).catch(() => null);
    await clickByLabels(page, [
      'Sign in with Google',
      'Iniciar sesión con Google',
      'Continuar con Google',
      'Login with Google',
      'Google',
    ]);
    const popup = await popupPromise;
    const googlePage = popup || page;

    if (await googlePage.getByText(new RegExp(escapeRegExp(GOOGLE_ACCOUNT), 'i')).first().isVisible().catch(() => false)) {
      await googlePage.getByText(new RegExp(escapeRegExp(GOOGLE_ACCOUNT), 'i')).first().click();
      await waitForUi(googlePage);
    }

    if (popup) {
      await popup.waitForEvent('close', { timeout: 30_000 }).catch(() => {});
      await page.bringToFront();
    }

    await waitForUi(page);
    await expect(page.locator('nav, aside').first()).toBeVisible();
    await expectTextVisible(page, /Negocio|Mi Negocio/i);
    await saveCheckpoint(page, testInfo, '01-dashboard-loaded', true);
  });

  await runValidation('Mi Negocio menu', async () => {
    await clickByLabels(page, ['Negocio', 'Mi Negocio']);
    await clickByLabels(page, ['Mi Negocio']);
    await expectTextVisible(page, 'Agregar Negocio');
    await expectTextVisible(page, 'Administrar Negocios');
    await saveCheckpoint(page, testInfo, '02-mi-negocio-menu-expanded');
  });

  await runValidation('Agregar Negocio modal', async () => {
    await clickByLabels(page, ['Agregar Negocio']);
    await expectTextVisible(page, 'Crear Nuevo Negocio');
    await expect(await findBusinessNameInput(page)).toBeVisible();
    await expectTextVisible(page, 'Tienes 2 de 3 negocios');
    await expectTextVisible(page, 'Cancelar');
    await expectTextVisible(page, 'Crear Negocio');
    await saveCheckpoint(page, testInfo, '03-agregar-negocio-modal');

    const businessNameInput = await findBusinessNameInput(page);
    await businessNameInput.click();
    await businessNameInput.fill('Negocio Prueba Automatización');
    await clickByLabels(page, ['Cancelar']);
  });

  await runValidation('Administrar Negocios view', async () => {
    if (!(await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false))) {
      await clickByLabels(page, ['Mi Negocio']);
    }

    await clickByLabels(page, ['Administrar Negocios']);
    await expectTextVisible(page, 'Información General');
    await expectTextVisible(page, 'Detalles de la Cuenta');
    await expectTextVisible(page, 'Tus Negocios');
    await expectTextVisible(page, 'Sección Legal');
    await saveCheckpoint(page, testInfo, '04-administrar-negocios-view', true);
  });

  await runValidation('Información General', async () => {
    await expectTextVisible(page, 'Información General');
    await expectTextVisible(page, /BUSINESS PLAN/i);
    await expectTextVisible(page, /Cambiar Plan/i);
    await expectTextVisible(page, /\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/i);
    await expectTextVisible(page, /Nombre|Usuario|Profile|Cuenta/i);
  });

  await runValidation('Detalles de la Cuenta', async () => {
    await expectTextVisible(page, /Cuenta creada/i);
    await expectTextVisible(page, /Estado activo/i);
    await expectTextVisible(page, /Idioma seleccionado/i);
  });

  await runValidation('Tus Negocios', async () => {
    await expectTextVisible(page, /Tus Negocios/i);
    await expectTextVisible(page, 'Agregar Negocio');
    await expectTextVisible(page, 'Tienes 2 de 3 negocios');

    const businessEntries = page.locator('li, tr, [data-testid*="business"], [class*="business"]');
    if ((await businessEntries.count()) === 0) {
      await expectTextVisible(page, /negocio/i);
    }
  });

  await runValidation('Términos y Condiciones', async () => {
    evidence.termsUrl = await clickLegalLinkAndValidate({
      page,
      context,
      testInfo,
      label: 'Términos y Condiciones',
      expectedHeading: /Términos y Condiciones/i,
      screenshotName: '08-terminos-y-condiciones',
    });
  });

  await runValidation('Política de Privacidad', async () => {
    evidence.privacyUrl = await clickLegalLinkAndValidate({
      page,
      context,
      testInfo,
      label: 'Política de Privacidad',
      expectedHeading: /Política de Privacidad/i,
      screenshotName: '09-politica-de-privacidad',
    });
  });

  const report = {
    name: 'saleads_mi_negocio_full_test',
    generatedAt: new Date().toISOString(),
    environment: {
      baseURL: baseURL || null,
      currentURL: page.url(),
    },
    results: REPORT_FIELDS.map((field) => ({ field, ...result[field] })),
    evidence,
  };

  const artifactsDir = path.join(process.cwd(), 'saleads-artifacts');
  await fs.mkdir(artifactsDir, { recursive: true });
  const reportPath = path.join(artifactsDir, 'saleads-mi-negocio-final-report.json');
  await fs.writeFile(reportPath, JSON.stringify(report, null, 2), 'utf8');
  await testInfo.attach('saleads-final-report', { path: reportPath, contentType: 'application/json' });

  const failedSteps = report.results.filter((item) => item.status === 'FAIL');
  expect(failedSteps, `Validation failures: ${JSON.stringify(failedSteps, null, 2)}`).toEqual([]);
});
