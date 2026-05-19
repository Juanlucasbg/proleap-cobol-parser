const fs = require('fs');
const path = require('path');
const { test, expect } = require('@playwright/test');

const TEST_NAME = 'saleads_mi_negocio_full_test';
const GOOGLE_ACCOUNT_EMAIL = 'juanlucasbarbiergarzon@gmail.com';
const GOOGLE_LOGIN_TEXT = /sign in with google|continue with google|continuar con google|iniciar sesion con google|iniciar sesión con google/i;
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
    page.getByRole('button', { name: GOOGLE_LOGIN_TEXT }),
    page.getByRole('link', { name: GOOGLE_LOGIN_TEXT }),
    page.getByText(GOOGLE_LOGIN_TEXT)
  ]);
}

async function findPageWithSidebar(pages) {
  for (const candidate of pages) {
    const sidebar = await findFirstVisible([candidate.locator('aside'), candidate.getByText(/negocio/i)]);
    if (sidebar) {
      return { candidate, sidebar };
    }
  }
  return null;
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
  let appPage = page;
  const results = initResultMap();
  const errors = {};
  const legalUrls = {};

  fs.mkdirSync(REPORT_DIR, { recursive: true });
  await captureCheckpoint(appPage, '00-browser-opened.png', true);

  const runStep = async (resultKey, action) => {
    try {
      await action();
      results[resultKey] = 'PASS';
    } catch (error) {
      errors[resultKey] = error instanceof Error ? error.message : String(error);
      results[resultKey] = 'FAIL';
      await captureCheckpoint(appPage, `fail-${toFileSafeName(resultKey)}.png`, true).catch(() => {});
    }
  };

  await runStep('Login', async () => {
    if (!baseUrl) {
      throw new Error(
        'SALEADS_BASE_URL (or BASE_URL) is required so the test can open the current SaleADS environment login page.'
      );
    }

    await appPage.goto(baseUrl, { waitUntil: 'domcontentloaded' });
    await waitForUiLoad(appPage);
    await captureCheckpoint(appPage, '00-initial-saleads-page.png', true);

    let authPage = appPage;
    let loginButton = await findGoogleLoginEntry(authPage);
    if (!loginButton) {
      const accessAppButton = await findFirstVisible([
        authPage.getByRole('button', {
          name: /log in|sign in|iniciar sesion|iniciar sesión|acceder|start free|get started|empieza gratis/i
        }),
        authPage.getByRole('link', {
          name: /log in|sign in|iniciar sesion|iniciar sesión|acceder|start free|get started|empieza gratis/i
        }),
        authPage.getByText(/log in|sign in|iniciar sesion|iniciar sesión|acceder|start free|get started|empieza gratis/i)
      ]);

      if (accessAppButton) {
        const appPopupPromise = appPage.context().waitForEvent('page', { timeout: 10000 }).catch(() => null);
        await accessAppButton.click();
        await waitForUiLoad(authPage);

        const appPopup = await appPopupPromise;
        if (appPopup) {
          authPage = appPopup;
          await authPage.waitForLoadState('domcontentloaded');
          await waitForUiLoad(authPage);
        }
      }

      loginButton = await findGoogleLoginEntry(authPage);
    }

    if (!loginButton) {
      throw new Error('Google login button was not found.');
    }

    const popupPromise = authPage.context().waitForEvent('page', { timeout: 15000 }).catch(() => null);
    await loginButton.click();
    await waitForUiLoad(authPage);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState('domcontentloaded');
      await pickGoogleAccountIfPresent(popup);
      await popup.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});
    } else {
      await pickGoogleAccountIfPresent(authPage);
    }

    await waitForUiLoad(authPage);

    const sidebarPage = await findPageWithSidebar(appPage.context().pages());
    if (!sidebarPage) {
      throw new Error('Main app interface did not show a visible sidebar.');
    }

    await expect(sidebarPage.sidebar).toBeVisible();

    appPage = sidebarPage.candidate;
    await sidebarPage.candidate.bringToFront();
    await waitForUiLoad(appPage);
    await captureCheckpoint(appPage, '01-dashboard-loaded.png', true);
  });

  await runStep('Mi Negocio menu', async () => {
    const negocioEntry = await findFirstVisible([
      appPage.getByRole('button', { name: /^Negocio$/i }),
      appPage.getByText(/^Negocio$/i)
    ]);
    if (!negocioEntry) {
      throw new Error('Sidebar item "Negocio" was not found.');
    }
    await clickAndWait(appPage, negocioEntry);

    const miNegocioEntry = await findFirstVisible([
      appPage.getByRole('button', { name: /^Mi Negocio$/i }),
      appPage.getByText(/^Mi Negocio$/i)
    ]);
    if (!miNegocioEntry) {
      throw new Error('Option "Mi Negocio" was not found.');
    }
    await clickAndWait(appPage, miNegocioEntry);

    await expect(appPage.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(appPage.getByText(/Administrar Negocios/i).first()).toBeVisible();

    await captureCheckpoint(appPage, '02-mi-negocio-menu-expanded.png');
  });

  await runStep('Agregar Negocio modal', async () => {
    const agregarNegocioOption = appPage.getByText(/^Agregar Negocio$/i).first();
    await clickAndWait(appPage, agregarNegocioOption);

    const modalTitle = appPage.getByText(/Crear Nuevo Negocio/i).first();
    await expect(modalTitle).toBeVisible();
    await expect(appPage.getByText(/Nombre del Negocio/i).first()).toBeVisible();
    await expect(appPage.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(appPage.getByRole('button', { name: /Cancelar/i }).first()).toBeVisible();
    await expect(appPage.getByRole('button', { name: /Crear Negocio/i }).first()).toBeVisible();

    await captureCheckpoint(appPage, '03-agregar-negocio-modal.png');

    const businessNameInput = appPage.getByPlaceholder(/Nombre del Negocio/i).first();
    if (await businessNameInput.isVisible().catch(() => false)) {
      await businessNameInput.fill('Negocio Prueba Automatizacion');
    }
    await clickAndWait(appPage, appPage.getByRole('button', { name: /Cancelar/i }).first());
  });

  await runStep('Administrar Negocios view', async () => {
    if (!(await appPage.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false))) {
      const miNegocioEntry = await findFirstVisible([
        appPage.getByRole('button', { name: /^Mi Negocio$/i }),
        appPage.getByText(/^Mi Negocio$/i)
      ]);
      if (miNegocioEntry) {
        await clickAndWait(appPage, miNegocioEntry);
      }
    }

    await clickAndWait(appPage, appPage.getByText(/Administrar Negocios/i).first());

    await expect(appPage.getByText(/Informacion General|Información General/i).first()).toBeVisible();
    await expect(appPage.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(appPage.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(appPage.getByText(/Seccion Legal|Sección Legal/i).first()).toBeVisible();

    await captureCheckpoint(appPage, '04-administrar-negocios-page-full.png', true);
  });

  await runStep('Información General', async () => {
    const infoSection = sectionByHeading(appPage, 'Información General');
    await expect(infoSection).toBeVisible();

    const emailLocator = appPage.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/).first();
    await expect(emailLocator).toBeVisible();

    await expect(appPage.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(appPage.getByRole('button', { name: /Cambiar Plan/i }).first()).toBeVisible();

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
    await expect(appPage.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(appPage.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(appPage.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep('Tus Negocios', async () => {
    const negociosSection = sectionByHeading(appPage, 'Tus Negocios');
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
      page: appPage,
      linkText: 'Términos y Condiciones',
      expectedHeading: 'Términos y Condiciones',
      screenshotFileName: '05-terminos-y-condiciones.png'
    });
  });

  await runStep('Política de Privacidad', async () => {
    legalUrls.politicaDePrivacidad = await openLegalDocumentAndReturn({
      page: appPage,
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
