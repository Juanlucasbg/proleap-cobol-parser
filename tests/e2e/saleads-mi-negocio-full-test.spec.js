const { test, expect } = require('@playwright/test');
const fs = require('node:fs/promises');
const path = require('node:path');

const REPORT_DIR = path.resolve('artifacts', 'saleads-mi-negocio');
const STEP_IDS = {
  LOGIN: 'Login',
  MENU: 'Mi Negocio menu',
  MODAL: 'Agregar Negocio modal',
  ADMIN_VIEW: 'Administrar Negocios view',
  INFO_GENERAL: 'Información General',
  ACCOUNT_DETAILS: 'Detalles de la Cuenta',
  BUSINESS_LIST: 'Tus Negocios',
  TERMS: 'Términos y Condiciones',
  PRIVACY: 'Política de Privacidad'
};

function normalizeText(value) {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase();
}

async function waitForUi(page) {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(500);
}

async function clickFirstVisible(page, candidateTexts) {
  for (const text of candidateTexts) {
    const locator = page.getByText(new RegExp(`^${text}$`, 'i')).first();
    if (await locator.isVisible().catch(() => false)) {
      await locator.click();
      await waitForUi(page);
      return text;
    }
  }

  throw new Error(`No visible candidate found among: ${candidateTexts.join(', ')}`);
}

async function clickMenuItemByText(page, text) {
  const locator = page.getByRole('link', { name: new RegExp(text, 'i') }).first();
  if (await locator.isVisible().catch(() => false)) {
    await locator.click();
    await waitForUi(page);
    return;
  }

  const buttonLocator = page.getByRole('button', { name: new RegExp(text, 'i') }).first();
  if (await buttonLocator.isVisible().catch(() => false)) {
    await buttonLocator.click();
    await waitForUi(page);
    return;
  }

  const textLocator = page.getByText(new RegExp(`^${text}$`, 'i')).first();
  await expect(textLocator, `Expected "${text}" to be visible before click`).toBeVisible({ timeout: 20000 });
  await textLocator.click();
  await waitForUi(page);
}

async function assertVisibleText(page, value, message) {
  const locator = page.getByText(new RegExp(value, 'i')).first();
  await expect(locator, message).toBeVisible({ timeout: 20000 });
}

async function clickLegalAndCapture(page, context, linkText, slug) {
  const linkLocator = page.getByRole('link', { name: new RegExp(linkText, 'i') }).first();
  const buttonLocator = page.getByRole('button', { name: new RegExp(linkText, 'i') }).first();
  const target = (await linkLocator.count()) > 0 ? linkLocator : buttonLocator;

  let legalPage = page;
  const beforeUrl = page.url();
  let usedNewTab = false;

  const popupPromise = context.waitForEvent('page', { timeout: 5000 }).catch(() => null);
  await target.click();
  await waitForUi(page);
  const popup = await popupPromise;

  if (popup) {
    legalPage = popup;
    usedNewTab = true;
    await legalPage.waitForLoadState('domcontentloaded');
  } else if (page.url() !== beforeUrl) {
    legalPage = page;
  } else {
    // Some UIs navigate without changing URL fragments immediately.
    await page.waitForTimeout(1500);
    legalPage = page;
  }

  const normalizedNeedle = normalizeText(linkText);
  await expect
    .poll(
      async () => {
        const title = normalizeText(await legalPage.title().catch(() => ''));
        const h1 = normalizeText(await legalPage.locator('h1').first().innerText().catch(() => ''));
        const body = normalizeText(await legalPage.locator('body').innerText().catch(() => ''));
        return [title, h1, body].some((part) => part.includes(normalizedNeedle));
      },
      {
        timeout: 20000,
        message: `Expected legal page to include "${linkText}"`
      }
    )
    .toBeTruthy();

  const bodyText = legalPage.locator('body');
  await expect(bodyText).toBeVisible();
  await expect
    .poll(async () => {
      const text = await bodyText.innerText().catch(() => '');
      return text.trim().length > 120;
    }, { timeout: 15000, message: `Expected legal content for "${linkText}"` })
    .toBeTruthy();

  await legalPage.screenshot({
    path: path.join(REPORT_DIR, `checkpoint-${slug}.png`),
    fullPage: true
  });

  const finalUrl = legalPage.url();
  if (usedNewTab) {
    await legalPage.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => undefined);
    await waitForUi(page);
  }

  return finalUrl;
}

test.describe('SaleADS Mi Negocio full workflow', () => {
  test('saleads_mi_negocio_full_test', async ({ page, context }, testInfo) => {
    await fs.mkdir(REPORT_DIR, { recursive: true });

    const baseUrl = process.env.SALEADS_BASE_URL;
    if (baseUrl) {
      await page.goto(baseUrl, { waitUntil: 'domcontentloaded' });
      await waitForUi(page);
    }

    const results = {
      [STEP_IDS.LOGIN]: 'FAIL',
      [STEP_IDS.MENU]: 'FAIL',
      [STEP_IDS.MODAL]: 'FAIL',
      [STEP_IDS.ADMIN_VIEW]: 'FAIL',
      [STEP_IDS.INFO_GENERAL]: 'FAIL',
      [STEP_IDS.ACCOUNT_DETAILS]: 'FAIL',
      [STEP_IDS.BUSINESS_LIST]: 'FAIL',
      [STEP_IDS.TERMS]: 'FAIL',
      [STEP_IDS.PRIVACY]: 'FAIL'
    };

    const evidence = {
      screenshots: {},
      finalUrls: {}
    };

    try {
      // Step 1: Login with Google
      await clickFirstVisible(page, [
        'Sign in with Google',
        'Iniciar sesión con Google',
        'Continuar con Google',
        'Login with Google'
      ]);

      const accountChoice = page.getByText(/juanlucasbarbiergarzon@gmail\.com/i).first();
      if (await accountChoice.isVisible().catch(() => false)) {
        await accountChoice.click();
      }

      await waitForUi(page);
      await expect
        .poll(async () => {
          const sidebars = await Promise.all([
            page.locator('aside').first().isVisible().catch(() => false),
            page.locator('[class*="sidebar"]').first().isVisible().catch(() => false),
            page.getByText(/negocio/i).first().isVisible().catch(() => false)
          ]);
          return sidebars.some(Boolean);
        }, { timeout: 30000, message: 'Main interface with sidebar should be visible after login' })
        .toBeTruthy();

      await page.screenshot({ path: path.join(REPORT_DIR, 'checkpoint-dashboard.png'), fullPage: true });
      evidence.screenshots.dashboard = path.join('artifacts', 'saleads-mi-negocio', 'checkpoint-dashboard.png');
      results[STEP_IDS.LOGIN] = 'PASS';

      // Step 2: Open Mi Negocio menu
      await clickMenuItemByText(page, 'Mi Negocio');
      await assertVisibleText(page, 'Agregar Negocio', '"Agregar Negocio" should be visible');
      await assertVisibleText(page, 'Administrar Negocios', '"Administrar Negocios" should be visible');
      await page.screenshot({ path: path.join(REPORT_DIR, 'checkpoint-mi-negocio-menu.png'), fullPage: true });
      evidence.screenshots.miNegocioMenu = path.join('artifacts', 'saleads-mi-negocio', 'checkpoint-mi-negocio-menu.png');
      results[STEP_IDS.MENU] = 'PASS';

      // Step 3: Validate Agregar Negocio modal
      await clickMenuItemByText(page, 'Agregar Negocio');
      await assertVisibleText(page, 'Crear Nuevo Negocio', 'Modal title should be visible');
      await expect(page.getByLabel(/Nombre del Negocio/i).or(page.getByPlaceholder(/Nombre del Negocio/i))).toBeVisible({
        timeout: 20000
      });
      await assertVisibleText(page, 'Tienes 2 de 3 negocios', 'Quota text should be visible');
      await expect(page.getByRole('button', { name: /Cancelar/i })).toBeVisible();
      await expect(page.getByRole('button', { name: /Crear Negocio/i })).toBeVisible();

      const businessNameField =
        page.getByLabel(/Nombre del Negocio/i).first().or(page.getByPlaceholder(/Nombre del Negocio/i).first());
      await businessNameField.click();
      await businessNameField.fill('Negocio Prueba Automatización');
      await page.screenshot({ path: path.join(REPORT_DIR, 'checkpoint-agregar-negocio-modal.png'), fullPage: true });
      evidence.screenshots.agregarNegocioModal = path.join(
        'artifacts',
        'saleads-mi-negocio',
        'checkpoint-agregar-negocio-modal.png'
      );
      await page.getByRole('button', { name: /Cancelar/i }).click();
      await waitForUi(page);
      results[STEP_IDS.MODAL] = 'PASS';

      // Step 4: Open Administrar Negocios
      if (!(await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false))) {
        await clickMenuItemByText(page, 'Mi Negocio');
      }
      await clickMenuItemByText(page, 'Administrar Negocios');
      await assertVisibleText(page, 'Información General', 'Información General section should be visible');
      await assertVisibleText(page, 'Detalles de la Cuenta', 'Detalles de la Cuenta section should be visible');
      await assertVisibleText(page, 'Tus Negocios', 'Tus Negocios section should be visible');
      await assertVisibleText(page, 'Sección Legal', 'Sección Legal section should be visible');
      await page.screenshot({ path: path.join(REPORT_DIR, 'checkpoint-administrar-negocios.png'), fullPage: true });
      evidence.screenshots.administrarNegocios = path.join(
        'artifacts',
        'saleads-mi-negocio',
        'checkpoint-administrar-negocios.png'
      );
      results[STEP_IDS.ADMIN_VIEW] = 'PASS';

      // Step 5: Validate Información General
      await expect
        .poll(async () => {
          const hasBusinessPlan = await page.getByText(/BUSINESS PLAN/i).first().isVisible().catch(() => false);
          const hasChangePlan = await page.getByRole('button', { name: /Cambiar Plan/i }).isVisible().catch(() => false);
          const emailVisible = await page.locator('text=/@/').first().isVisible().catch(() => false);
          const nameCandidateVisible =
            (await page.locator('h1, h2, h3, [data-testid*="name"]').first().isVisible().catch(() => false)) || false;
          return hasBusinessPlan && hasChangePlan && emailVisible && nameCandidateVisible;
        }, { timeout: 20000, message: 'Información General validations failed' })
        .toBeTruthy();
      results[STEP_IDS.INFO_GENERAL] = 'PASS';

      // Step 6: Validate Detalles de la Cuenta
      await assertVisibleText(page, 'Cuenta creada', '"Cuenta creada" should be visible');
      await assertVisibleText(page, 'Estado activo', '"Estado activo" should be visible');
      await assertVisibleText(page, 'Idioma seleccionado', '"Idioma seleccionado" should be visible');
      results[STEP_IDS.ACCOUNT_DETAILS] = 'PASS';

      // Step 7: Validate Tus Negocios
      await assertVisibleText(page, 'Tus Negocios', '"Tus Negocios" heading should be visible');
      await expect(page.getByRole('button', { name: /Agregar Negocio/i })).toBeVisible({ timeout: 20000 });
      await assertVisibleText(page, 'Tienes 2 de 3 negocios', 'Quota text in business list should be visible');
      results[STEP_IDS.BUSINESS_LIST] = 'PASS';

      // Step 8: Validate Términos y Condiciones
      const termsUrl = await clickLegalAndCapture(page, context, 'Términos y Condiciones', 'terminos');
      evidence.screenshots.terminos = path.join('artifacts', 'saleads-mi-negocio', 'checkpoint-terminos.png');
      evidence.finalUrls.terminos = termsUrl;
      results[STEP_IDS.TERMS] = 'PASS';

      // Step 9: Validate Política de Privacidad
      const privacyUrl = await clickLegalAndCapture(page, context, 'Política de Privacidad', 'privacidad');
      evidence.screenshots.privacidad = path.join('artifacts', 'saleads-mi-negocio', 'checkpoint-privacidad.png');
      evidence.finalUrls.privacidad = privacyUrl;
      results[STEP_IDS.PRIVACY] = 'PASS';
    } finally {
      const payload = {
        testName: 'saleads_mi_negocio_full_test',
        startedAt: new Date(testInfo.startTime).toISOString(),
        finishedAt: new Date().toISOString(),
        status: Object.values(results).every((value) => value === 'PASS') ? 'PASS' : 'FAIL',
        report: results,
        evidence
      };

      await fs.writeFile(path.join(REPORT_DIR, 'final-report.json'), `${JSON.stringify(payload, null, 2)}\n`, 'utf8');
      await testInfo.attach('saleads-mi-negocio-report', {
        body: JSON.stringify(payload, null, 2),
        contentType: 'application/json'
      });
    }

    for (const [field, status] of Object.entries(results)) {
      expect.soft(status, `Expected ${field} to pass`).toBe('PASS');
    }
  });
});
