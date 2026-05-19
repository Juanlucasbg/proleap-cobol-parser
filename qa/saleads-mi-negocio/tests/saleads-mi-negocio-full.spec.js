const fs = require('fs');
const path = require('path');
const { test, expect } = require('@playwright/test');

const TEST_NAME = 'saleads_mi_negocio_full_test';
const GOOGLE_ACCOUNT_EMAIL = 'juanlucasbarbiergarzon@gmail.com';
const CHECKPOINT_DIR = path.resolve(__dirname, '../checkpoints');
const REPORT_DIR = path.resolve(__dirname, '../test-results');
const REPORT_PATH = path.join(REPORT_DIR, `${TEST_NAME}_report.json`);

const RESULT_KEYS = [
  'Login',
  'Mi Negocio menu',
  'Agregar Negocio modal',
  'Administrar Negocios view',
  'Información General',
  'Detalles de la Cuenta',
  'Tus Negocios',
  'Términos y Condiciones',
  'Política de Privacidad'
];

function initResultMap() {
  return Object.fromEntries(RESULT_KEYS.map((key) => [key, 'FAIL']));
}

async function waitForUiLoad(page) {
  await page.waitForLoadState('domcontentloaded').catch(() => {});
  await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiLoad(page);
}

async function captureCheckpoint(page, fileName, fullPage = false) {
  fs.mkdirSync(CHECKPOINT_DIR, { recursive: true });
  await page.screenshot({
    path: path.join(CHECKPOINT_DIR, fileName),
    fullPage
  });
}

function normalizeText(value) {
  return value.replace(/\s+/g, ' ').trim();
}

function toFileSafeName(value) {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-zA-Z0-9]+/g, '-')
    .replace(/(^-|-$)/g, '')
    .toLowerCase();
}

async function findFirstVisible(locators) {
  for (const locator of locators) {
    if (await locator.first().isVisible().catch(() => false)) {
      return locator.first();
    }
  }
  return null;
}

function sectionByHeading(page, headingText) {
  return page
    .locator('section,div,article')
    .filter({ has: page.getByText(new RegExp(`^${headingText}$`, 'i')) })
    .first();
}

async function pickGoogleAccountIfPresent(targetPage) {
  const accountOption = await findFirstVisible([
    targetPage.getByRole('button', { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, 'i') }),
    targetPage.getByText(new RegExp(`^${GOOGLE_ACCOUNT_EMAIL}$`, 'i'))
  ]);

  if (accountOption) {
    await clickAndWait(targetPage, accountOption);
  }
}

async function findGoogleLoginEntry(page) {
  return findFirstVisible([
    page.getByRole('button', { name: /sign in with google|continuar con google|google/i }),
    page.getByRole('link', { name: /sign in with google|continuar con google|google/i }),
    page.getByText(/sign in with google|continuar con google|google/i)
  ]);
}

async function openLegalDocumentAndReturn({
  page,
  linkText,
  expectedHeading,
  screenshotFileName
}) {
  const link = page.getByRole('link', { name: new RegExp(linkText, 'i') }).first();
  await expect(link).toBeVisible();

  const popupPromise = page.context().waitForEvent('page', { timeout: 10000 }).catch(() => null);

  await link.click();
  await waitForUiLoad(page);

  const popup = await popupPromise;
  const targetPage = popup || page;

  if (popup) {
    await popup.waitForLoadState('domcontentloaded');
    await popup.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});
  }

  const headingLocator = await findFirstVisible([
    targetPage.getByRole('heading', { name: new RegExp(expectedHeading, 'i') }),
    targetPage.getByText(new RegExp(expectedHeading, 'i'))
  ]);
  if (!headingLocator) {
    throw new Error(`Heading "${expectedHeading}" was not found in legal document.`);
  }
  await expect(headingLocator).toBeVisible();

  await captureCheckpoint(targetPage, screenshotFileName, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUiLoad(page);
  } else {
    await page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => {});
    await waitForUiLoad(page);
  }

  return finalUrl;
}

test('Login with Google and validate Mi Negocio module workflow', async ({ page }) => {
  const baseUrl = process.env.SALEADS_BASE_URL || process.env.BASE_URL;
  const results = initResultMap();
  const errors = {};
  const legalUrls = {};

  fs.mkdirSync(REPORT_DIR, { recursive: true });
  await captureCheckpoint(page, '00-browser-opened.png', true);

  const runStep = async (resultKey, action) => {
    try {
      await action();
      results[resultKey] = 'PASS';
    } catch (error) {
      errors[resultKey] = error instanceof Error ? error.message : String(error);
      results[resultKey] = 'FAIL';
      await captureCheckpoint(page, `fail-${toFileSafeName(resultKey)}.png`, true).catch(() => {});
    }
  };

  await runStep('Login', async () => {
    if (!baseUrl) {
      throw new Error(
        'SALEADS_BASE_URL (or BASE_URL) is required so the test can open the current SaleADS environment login page.'
      );
    }

    await page.goto(baseUrl, { waitUntil: 'domcontentloaded' });
    await waitForUiLoad(page);
    await captureCheckpoint(page, '00-initial-saleads-page.png', true);

    let loginButton = await findGoogleLoginEntry(page);
    if (!loginButton) {
      const accessAppButton = await findFirstVisible([
        page.getByRole('button', { name: /log in|iniciar sesion|iniciar sesión|acceder|start free|empieza gratis/i }),
        page.getByRole('link', { name: /log in|iniciar sesion|iniciar sesión|acceder|start free|empieza gratis/i }),
        page.getByText(/log in|iniciar sesion|iniciar sesión|acceder|start free|empieza gratis/i)
      ]);

      if (accessAppButton) {
        await clickAndWait(page, accessAppButton);
      }

      loginButton = await findGoogleLoginEntry(page);
    }

    if (!loginButton) {
      throw new Error('Google login button was not found.');
    }

    const popupPromise = page.context().waitForEvent('page', { timeout: 15000 }).catch(() => null);
    await loginButton.click();
    await waitForUiLoad(page);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState('domcontentloaded');
      await pickGoogleAccountIfPresent(popup);
      await popup.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});
    } else {
      await pickGoogleAccountIfPresent(page);
    }

    await waitForUiLoad(page);

    const sidebar = await findFirstVisible([
      page.locator('aside'),
      page.getByText(/negocio/i)
    ]);
    if (!sidebar) {
      throw new Error('Main app interface did not show a visible sidebar.');
    }
    await expect(sidebar).toBeVisible();

    await captureCheckpoint(page, '01-dashboard-loaded.png', true);
  });

  await runStep('Mi Negocio menu', async () => {
    const negocioEntry = await findFirstVisible([
      page.getByRole('button', { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i)
    ]);
    if (!negocioEntry) {
      throw new Error('Sidebar item "Negocio" was not found.');
    }
    await clickAndWait(page, negocioEntry);

    const miNegocioEntry = await findFirstVisible([
      page.getByRole('button', { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i)
    ]);
    if (!miNegocioEntry) {
      throw new Error('Option "Mi Negocio" was not found.');
    }
    await clickAndWait(page, miNegocioEntry);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

    await captureCheckpoint(page, '02-mi-negocio-menu-expanded.png');
  });

  await runStep('Agregar Negocio modal', async () => {
    const agregarNegocioOption = page.getByText(/^Agregar Negocio$/i).first();
    await clickAndWait(page, agregarNegocioOption);

    const modalTitle = page.getByText(/Crear Nuevo Negocio/i).first();
    await expect(modalTitle).toBeVisible();
    await expect(page.getByText(/Nombre del Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /Cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /Crear Negocio/i }).first()).toBeVisible();

    await captureCheckpoint(page, '03-agregar-negocio-modal.png');

    const businessNameInput = page.getByPlaceholder(/Nombre del Negocio/i).first();
    if (await businessNameInput.isVisible().catch(() => false)) {
      await businessNameInput.fill('Negocio Prueba Automatizacion');
    }
    await clickAndWait(page, page.getByRole('button', { name: /Cancelar/i }).first());
  });

  await runStep('Administrar Negocios view', async () => {
    if (!(await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false))) {
      const miNegocioEntry = await findFirstVisible([
        page.getByRole('button', { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i)
      ]);
      if (miNegocioEntry) {
        await clickAndWait(page, miNegocioEntry);
      }
    }

    await clickAndWait(page, page.getByText(/Administrar Negocios/i).first());

    await expect(page.getByText(/Informacion General|Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Seccion Legal|Sección Legal/i).first()).toBeVisible();

    await captureCheckpoint(page, '04-administrar-negocios-page-full.png', true);
  });

  await runStep('Información General', async () => {
    const infoSection = sectionByHeading(page, 'Información General');
    await expect(infoSection).toBeVisible();

    const emailLocator = page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/).first();
    await expect(emailLocator).toBeVisible();

    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /Cambiar Plan/i }).first()).toBeVisible();

    const visibleTexts = (await infoSection.locator('*').allTextContents()).map(normalizeText);
    const hasNameLikeText = visibleTexts.some(
      (entry) =>
        /^[A-Za-z]+(?: [A-Za-z]+)+$/.test(entry) &&
        !/informacion general|información general|business plan|cambiar plan/i.test(entry)
    );

    if (!hasNameLikeText) {
      throw new Error('Could not confirm a visible user name in "Información General".');
    }
  });

  await runStep('Detalles de la Cuenta', async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep('Tus Negocios', async () => {
    const negociosSection = sectionByHeading(page, 'Tus Negocios');
    await expect(negociosSection).toBeVisible();

    await expect(negociosSection.getByRole('button', { name: /Agregar Negocio/i }).first()).toBeVisible();
    await expect(negociosSection.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();

    const listLike = await findFirstVisible([
      negociosSection.locator('ul li').first(),
      negociosSection.locator('table tbody tr').first(),
      negociosSection.locator('[data-testid*="business"], [class*="business"]').first()
    ]);

    if (!listLike) {
      throw new Error('Business list could not be confirmed in "Tus Negocios".');
    }
  });

  await runStep('Términos y Condiciones', async () => {
    legalUrls.terminosYCondiciones = await openLegalDocumentAndReturn({
      page,
      linkText: 'Términos y Condiciones',
      expectedHeading: 'Términos y Condiciones',
      screenshotFileName: '05-terminos-y-condiciones.png'
    });
  });

  await runStep('Política de Privacidad', async () => {
    legalUrls.politicaDePrivacidad = await openLegalDocumentAndReturn({
      page,
      linkText: 'Política de Privacidad',
      expectedHeading: 'Política de Privacidad',
      screenshotFileName: '06-politica-de-privacidad.png'
    });
  });

  const report = {
    name: TEST_NAME,
    executedAt: new Date().toISOString(),
    environment: {
      baseUrl: baseUrl || null
    },
    results,
    legalUrls,
    errors
  };

  fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), 'utf-8');

  const failedSteps = Object.entries(results)
    .filter(([, status]) => status === 'FAIL')
    .map(([step]) => step);

  expect(
    failedSteps,
    `Validation failures in steps: ${failedSteps.join(', ')}. See ${REPORT_PATH} for details.`
  ).toEqual([]);
});
