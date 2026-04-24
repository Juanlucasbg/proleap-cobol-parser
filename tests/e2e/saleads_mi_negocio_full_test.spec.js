const { test, expect } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

const OUTPUT_DIR = path.resolve(process.cwd(), 'test-results', 'saleads-mi-negocio');
const REPORT_PATH = path.join(OUTPUT_DIR, 'final-report.json');
const LOGIN_URL = process.env.SALEADS_LOGIN_URL || process.env.saleads_login_url || '';
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
  'Política de Privacidad'
];

function ensureOutputDir() {
  fs.mkdirSync(OUTPUT_DIR, { recursive: true });
}

function createReportMap() {
  return REPORT_FIELDS.reduce((acc, key) => {
    acc[key] = { status: 'PENDING', details: [] };
    return acc;
  }, {});
}

function markPass(report, key, detail) {
  report[key].status = 'PASS';
  if (detail) report[key].details.push(detail);
}

function markFail(report, key, detail) {
  report[key].status = 'FAIL';
  if (detail) report[key].details.push(detail);
}

function markSkipped(report, key, detail) {
  if (report[key].status === 'PENDING') {
    report[key].status = 'SKIPPED';
  }
  if (detail) report[key].details.push(detail);
}

async function waitForUi(page) {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(700);
}

async function tryClickByText(page, text, timeout = 6000) {
  const roleButton = page.getByRole('button', { name: text, exact: false }).first();
  const roleLink = page.getByRole('link', { name: text, exact: false }).first();
  const anyText = page.getByText(text, { exact: false }).first();

  if (await roleButton.isVisible({ timeout }).catch(() => false)) {
    await roleButton.click();
    return true;
  }
  if (await roleLink.isVisible({ timeout }).catch(() => false)) {
    await roleLink.click();
    return true;
  }
  if (await anyText.isVisible({ timeout }).catch(() => false)) {
    await anyText.click();
    return true;
  }
  return false;
}

async function captureCheckpoint(page, testInfo, name, fullPage = false) {
  const safeName = name.replace(/[^a-zA-Z0-9-_]/g, '_');
  await testInfo.attach(`${safeName}.png`, {
    body: await page.screenshot({ fullPage }),
    contentType: 'image/png'
  });
}

async function validateLegalPage({
  page,
  testInfo,
  linkText,
  headingText,
  evidenceName,
  report,
  reportField
}) {
  let legalPage = page;
  let popupOpened = false;
  let finalUrl = '';

  try {
    const popupPromise = page.waitForEvent('popup', { timeout: 5000 }).catch(() => null);
    const clickSuccess = await tryClickByText(page, linkText, 7000);
    if (!clickSuccess) {
      throw new Error(`No se encontró enlace o botón "${linkText}"`);
    }

    const popup = await popupPromise;
    if (popup) {
      popupOpened = true;
      legalPage = popup;
      await legalPage.waitForLoadState('domcontentloaded');
    } else {
      await waitForUi(page);
    }

    await expect(legalPage.getByRole('heading', { name: headingText, exact: false }).first()).toBeVisible({
      timeout: 10000
    });
    const legalContent = legalPage.getByText(/(t[eé]rminos|condiciones|privacidad|datos|uso|legal)/i).first();
    await expect(legalContent).toBeVisible({ timeout: 10000 });

    finalUrl = legalPage.url();
    await captureCheckpoint(legalPage, testInfo, evidenceName, true);
    markPass(report, reportField, `Validada página legal. URL final: ${finalUrl}`);
  } catch (error) {
    markFail(report, reportField, `Fallo validación ${reportField}: ${error.message}`);
  } finally {
    if (popupOpened && !legalPage.isClosed()) {
      await legalPage.close().catch(() => {});
      await page.bringToFront().catch(() => {});
      await waitForUi(page);
    } else {
      await page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => {});
      await waitForUi(page);
    }
  }
}

test.describe('saleads_mi_negocio_full_test', () => {
  test('Login and validate Mi Negocio workflow', async ({ page }, testInfo) => {
    ensureOutputDir();
    const report = createReportMap();

    if (!LOGIN_URL) {
      markFail(report, 'Login', 'Defina SALEADS_LOGIN_URL para ejecutar el flujo en el ambiente actual.');
      for (const field of REPORT_FIELDS.filter((f) => f !== 'Login')) {
        markSkipped(report, field, 'Omitido porque falló el prerrequisito de login.');
      }
      fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), 'utf8');
      test.fail(true, 'SALEADS_LOGIN_URL no fue provisto.');
      return;
    }

    await test.step('1) Login with Google', async () => {
      try {
        await page.goto(LOGIN_URL, { waitUntil: 'domcontentloaded' });
        await waitForUi(page);

        const clickedLogin =
          (await tryClickByText(page, 'Sign in with Google')) ||
          (await tryClickByText(page, 'Iniciar sesión con Google')) ||
          (await tryClickByText(page, 'Google')) ||
          (await tryClickByText(page, 'Login'));

        if (!clickedLogin) {
          throw new Error('No se encontró botón de login con Google.');
        }
        await waitForUi(page);

        const googleAccountChoice = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
        if (await googleAccountChoice.isVisible({ timeout: 5000 }).catch(() => false)) {
          await googleAccountChoice.click();
          await waitForUi(page);
        }

        const sidebar = page.locator('aside, nav').filter({ hasText: /Negocio|Mi Negocio|Dashboard/i }).first();
        await expect(sidebar).toBeVisible({ timeout: 25000 });
        await captureCheckpoint(page, testInfo, '01-dashboard-loaded', true);

        markPass(report, 'Login', 'Login exitoso y sidebar principal visible.');
      } catch (error) {
        markFail(report, 'Login', `Error en login: ${error.message}`);
      }
    });

    const loginOk = report['Login'].status === 'PASS';
    if (!loginOk) {
      for (const field of REPORT_FIELDS.filter((f) => f !== 'Login')) {
        markSkipped(report, field, 'Omitido porque no se completó login.');
      }
      fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), 'utf8');
      expect.soft(report['Login'].status, 'Login debe ser PASS para continuar').toBe('PASS');
      return;
    }

    await test.step('2) Open Mi Negocio menu', async () => {
      try {
        const negocioClicked = await tryClickByText(page, 'Negocio', 10000);
        if (!negocioClicked) {
          throw new Error('No se encontró sección "Negocio".');
        }
        await waitForUi(page);

        const miNegocioClicked = await tryClickByText(page, 'Mi Negocio', 10000);
        if (!miNegocioClicked) {
          throw new Error('No se encontró opción "Mi Negocio".');
        }
        await waitForUi(page);

        await expect(page.getByText('Agregar Negocio', { exact: false }).first()).toBeVisible({ timeout: 10000 });
        await expect(page.getByText('Administrar Negocios', { exact: false }).first()).toBeVisible({ timeout: 10000 });
        await captureCheckpoint(page, testInfo, '02-mi-negocio-menu-expanded', false);

        markPass(report, 'Mi Negocio menu', 'Submenú expandido con Agregar y Administrar visibles.');
      } catch (error) {
        markFail(report, 'Mi Negocio menu', `Error abriendo Mi Negocio: ${error.message}`);
      }
    });

    await test.step('3) Validate Agregar Negocio modal', async () => {
      try {
        const addBusinessClicked = await tryClickByText(page, 'Agregar Negocio', 8000);
        if (!addBusinessClicked) {
          throw new Error('No se pudo hacer click en "Agregar Negocio".');
        }
        await waitForUi(page);

        const modalTitle = page.getByText('Crear Nuevo Negocio', { exact: false }).first();
        await expect(modalTitle).toBeVisible({ timeout: 10000 });
        await expect(page.getByLabel('Nombre del Negocio', { exact: false }).or(page.getByPlaceholder('Nombre del Negocio')).first()).toBeVisible({
          timeout: 10000
        });
        await expect(page.getByText('Tienes 2 de 3 negocios', { exact: false }).first()).toBeVisible({ timeout: 10000 });
        await expect(page.getByRole('button', { name: 'Cancelar', exact: false }).first()).toBeVisible({ timeout: 10000 });
        await expect(page.getByRole('button', { name: 'Crear Negocio', exact: false }).first()).toBeVisible({ timeout: 10000 });

        const businessInput = page.getByLabel('Nombre del Negocio', { exact: false }).or(page.getByPlaceholder('Nombre del Negocio')).first();
        if (await businessInput.isVisible({ timeout: 3000 }).catch(() => false)) {
          await businessInput.click();
          await businessInput.fill('Negocio Prueba Automatización');
        }
        await captureCheckpoint(page, testInfo, '03-agregar-negocio-modal', false);
        await tryClickByText(page, 'Cancelar', 5000);
        await waitForUi(page);

        markPass(report, 'Agregar Negocio modal', 'Modal validado con campos y botones requeridos.');
      } catch (error) {
        markFail(report, 'Agregar Negocio modal', `Error validando modal: ${error.message}`);
      }
    });

    await test.step('4) Open Administrar Negocios', async () => {
      try {
        if (!(await page.getByText('Administrar Negocios', { exact: false }).first().isVisible().catch(() => false))) {
          await tryClickByText(page, 'Mi Negocio', 6000);
          await waitForUi(page);
        }

        const manageClicked = await tryClickByText(page, 'Administrar Negocios', 8000);
        if (!manageClicked) {
          throw new Error('No se pudo abrir "Administrar Negocios".');
        }
        await waitForUi(page);

        await expect(page.getByText('Información General', { exact: false }).first()).toBeVisible({ timeout: 15000 });
        await expect(page.getByText('Detalles de la Cuenta', { exact: false }).first()).toBeVisible({ timeout: 15000 });
        await expect(page.getByText('Tus Negocios', { exact: false }).first()).toBeVisible({ timeout: 15000 });
        await expect(page.getByText(/(Secci[oó]n Legal|Términos y Condiciones|Política de Privacidad)/i).first()).toBeVisible({
          timeout: 15000
        });
        await captureCheckpoint(page, testInfo, '04-administrar-negocios-full-page', true);

        markPass(report, 'Administrar Negocios view', 'Vista de cuenta cargada con secciones principales.');
      } catch (error) {
        markFail(report, 'Administrar Negocios view', `Error en Administrar Negocios: ${error.message}`);
      }
    });

    const accountViewOk = report['Administrar Negocios view'].status === 'PASS';
    if (!accountViewOk) {
      for (const field of ['Información General', 'Detalles de la Cuenta', 'Tus Negocios', 'Términos y Condiciones', 'Política de Privacidad']) {
        markSkipped(report, field, 'Omitido porque no se cargó la vista Administrar Negocios.');
      }
      fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), 'utf8');
      expect.soft(report['Administrar Negocios view'].status).toBe('PASS');
      return;
    }

    await test.step('5) Validate Información General', async () => {
      try {
        await expect(page.getByText(/@/, { exact: false }).first()).toBeVisible({ timeout: 10000 });
        await expect(page.getByText('BUSINESS PLAN', { exact: false }).first()).toBeVisible({ timeout: 10000 });
        await expect(page.getByRole('button', { name: 'Cambiar Plan', exact: false }).first()).toBeVisible({ timeout: 10000 });
        markPass(report, 'Información General', 'Nombre/Email/plan y botón Cambiar Plan visibles.');
      } catch (error) {
        markFail(report, 'Información General', `Error validando información general: ${error.message}`);
      }
    });

    await test.step('6) Validate Detalles de la Cuenta', async () => {
      try {
        await expect(page.getByText('Cuenta creada', { exact: false }).first()).toBeVisible({ timeout: 10000 });
        await expect(page.getByText('Estado activo', { exact: false }).first()).toBeVisible({ timeout: 10000 });
        await expect(page.getByText('Idioma seleccionado', { exact: false }).first()).toBeVisible({ timeout: 10000 });
        markPass(report, 'Detalles de la Cuenta', 'Sección Detalles de la Cuenta validada.');
      } catch (error) {
        markFail(report, 'Detalles de la Cuenta', `Error validando detalles de cuenta: ${error.message}`);
      }
    });

    await test.step('7) Validate Tus Negocios', async () => {
      try {
        await expect(page.getByText('Tus Negocios', { exact: false }).first()).toBeVisible({ timeout: 10000 });
        await expect(page.getByText('Agregar Negocio', { exact: false }).first()).toBeVisible({ timeout: 10000 });
        await expect(page.getByText('Tienes 2 de 3 negocios', { exact: false }).first()).toBeVisible({ timeout: 10000 });
        markPass(report, 'Tus Negocios', 'Lista de negocios y contador validados.');
      } catch (error) {
        markFail(report, 'Tus Negocios', `Error validando Tus Negocios: ${error.message}`);
      }
    });

    await test.step('8) Validate Términos y Condiciones', async () => {
      await validateLegalPage({
        page,
        testInfo,
        linkText: 'Términos y Condiciones',
        headingText: 'Términos y Condiciones',
        evidenceName: '08-terminos-y-condiciones',
        report,
        reportField: 'Términos y Condiciones'
      });
    });

    await test.step('9) Validate Política de Privacidad', async () => {
      await validateLegalPage({
        page,
        testInfo,
        linkText: 'Política de Privacidad',
        headingText: 'Política de Privacidad',
        evidenceName: '09-politica-de-privacidad',
        report,
        reportField: 'Política de Privacidad'
      });
    });

    fs.writeFileSync(REPORT_PATH, JSON.stringify(report, null, 2), 'utf8');
    await testInfo.attach('10-final-report.json', {
      body: JSON.stringify(report, null, 2),
      contentType: 'application/json'
    });

    for (const key of REPORT_FIELDS) {
      expect.soft(report[key].status, `${key} debe estar en PASS`).toBe('PASS');
    }
  });
});
