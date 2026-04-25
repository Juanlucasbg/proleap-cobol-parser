const { test, expect } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

const TEST_NAME = 'saleads_mi_negocio_full_test';
const ARTIFACTS_DIR = path.join(process.cwd(), 'artifacts', TEST_NAME);
const LOGIN_EMAIL = 'juanlucasbarbiergarzon@gmail.com';

function sanitizeLabel(label) {
  return label
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '');
}

async function waitForUiLoad(page) {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForLoadState('networkidle').catch(() => {});
  await page.waitForTimeout(700);
}

async function clickByVisibleText(page, text, options = {}) {
  const roleLocators = [
    page.getByRole('button', { name: text, exact: true }).first(),
    page.getByRole('link', { name: text, exact: true }).first(),
    page.getByRole('menuitem', { name: text, exact: true }).first(),
    page.getByRole('tab', { name: text, exact: true }).first()
  ];
  for (const locator of roleLocators) {
    if (await locator.count()) {
      await locator.scrollIntoViewIfNeeded();
      await locator.click(options);
      await waitForUiLoad(page);
      return;
    }
  }

  const exactLocator = page.getByText(text, { exact: true }).first();
  if (await exactLocator.count()) {
    await exactLocator.scrollIntoViewIfNeeded();
    await exactLocator.click(options);
    await waitForUiLoad(page);
    return;
  }

  const fallbackLocator = page.getByText(text, { exact: false }).first();
  await expect(fallbackLocator, `Unable to find clickable element with text "${text}"`).toBeVisible({
    timeout: 30000
  });
  await fallbackLocator.scrollIntoViewIfNeeded();
  await fallbackLocator.click(options);
  await waitForUiLoad(page);
}

async function captureCheckpoint(page, label, fullPage = false) {
  const filename = `${new Date().toISOString().replace(/[:.]/g, '-')}_${sanitizeLabel(label)}.png`;
  const filePath = path.join(ARTIFACTS_DIR, filename);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

function createReport() {
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

async function setResult(stepFn, report, reportField) {
  try {
    await stepFn();
    report[reportField] = 'PASS';
  } catch (error) {
    report[reportField] = `FAIL: ${error.message}`;
    throw error;
  }
}

async function findGoogleLoginButton(page) {
  const candidateLabels = [
    'Sign in with Google',
    'Iniciar sesión con Google',
    'Continuar con Google',
    'Google'
  ];

  for (const label of candidateLabels) {
    const locator = page.getByText(label, { exact: false }).first();
    if (await locator.count()) {
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }
  }

  const oauthLocator = page.locator(
    'button:has-text("Google"), a:has-text("Google"), [role="button"]:has-text("Google")'
  ).first();
  if (await oauthLocator.count()) {
    return oauthLocator;
  }

  throw new Error('Google login button was not found on the login page.');
}

async function maybeSelectGoogleAccount(popupOrPage) {
  const accountOption = popupOrPage.getByText(LOGIN_EMAIL, { exact: false }).first();
  if (await accountOption.count()) {
    if (await accountOption.isVisible().catch(() => false)) {
      await accountOption.click();
      await popupOrPage.waitForLoadState('domcontentloaded').catch(() => {});
      await popupOrPage.waitForTimeout(500);
    }
  }
}

async function returnToAppPage(context, appPage) {
  const pages = context.pages();
  const legalCandidate = [...pages].reverse().find((p) => p !== appPage);
  if (legalCandidate) {
    await legalCandidate.close().catch(() => {});
  }
  await appPage.bringToFront();
  await waitForUiLoad(appPage);
}

async function assertUserNameVisible(section) {
  const text = (await section.innerText())
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .filter((line) => !/@/.test(line))
    .filter((line) => !/informaci[oó]n general|business plan|cambiar plan/i.test(line));

  const nameLikeLine = text.find((line) => /^[A-Za-zÀ-ÿ' -]{3,}$/.test(line));
  expect(nameLikeLine, 'Unable to find a visible user name in "Información General".').toBeTruthy();
}

test.describe(TEST_NAME, () => {
  test('Login with Google and validate Mi Negocio workflow', async ({ page, context }) => {
    fs.mkdirSync(ARTIFACTS_DIR, { recursive: true });
    const report = createReport();
    const legalUrls = {};

    const baseUrlFromEnv = process.env.SALEADS_BASE_URL || process.env.BASE_URL || process.env.APP_URL;
    if (baseUrlFromEnv) {
      await page.goto(baseUrlFromEnv, { waitUntil: 'domcontentloaded' });
      await waitForUiLoad(page);
    } else if (page.url() === 'about:blank') {
      throw new Error(
        'No login page is loaded. Set SALEADS_BASE_URL (or BASE_URL / APP_URL) to the SaleADS login URL for the active environment.'
      );
    }

    await test.step('1) Login with Google', async () => {
      await setResult(async () => {
        // If session is already authenticated, continue directly.
        const sidebarBeforeLogin = page.locator('aside, nav').first();
        const isAlreadyLoggedIn = await sidebarBeforeLogin.isVisible().catch(() => false);
        if (!isAlreadyLoggedIn) {
          const googleButton = await findGoogleLoginButton(page);
          const [popupOrNav] = await Promise.all([
            context.waitForEvent('page', { timeout: 8000 }).catch(() => null),
            googleButton.click()
          ]);

          if (popupOrNav) {
            await popupOrNav.waitForLoadState('domcontentloaded').catch(() => {});
            await maybeSelectGoogleAccount(popupOrNav);
            await popupOrNav.waitForClose({ timeout: 45000 }).catch(() => {});
            await page.bringToFront();
          } else {
            await maybeSelectGoogleAccount(page);
          }
        }

        await waitForUiLoad(page);

        // Validate app shell loaded after login.
        const sidebar = page.locator('aside, nav').first();
        await expect(sidebar).toBeVisible({ timeout: 60000 });

        await captureCheckpoint(page, '01_dashboard_loaded');
      }, report, 'Login');
    });

    await test.step('2) Open Mi Negocio menu', async () => {
      await setResult(async () => {
        await clickByVisibleText(page, 'Negocio');
        await clickByVisibleText(page, 'Mi Negocio');
        await expect(page.getByText('Agregar Negocio', { exact: false })).toBeVisible({ timeout: 30000 });
        await expect(page.getByText('Administrar Negocios', { exact: false })).toBeVisible({ timeout: 30000 });
        await captureCheckpoint(page, '02_mi_negocio_menu_expanded');
      }, report, 'Mi Negocio menu');
    });

    await test.step('3) Validate Agregar Negocio modal', async () => {
      await setResult(async () => {
        await clickByVisibleText(page, 'Agregar Negocio');
        await expect(page.getByText('Crear Nuevo Negocio', { exact: false })).toBeVisible({ timeout: 30000 });

        const businessNameInput = page.getByLabel('Nombre del Negocio', { exact: false }).first();
        const fallbackInput = page.getByPlaceholder('Nombre del Negocio', { exact: false }).first();
        const fallbackTextbox = page.getByRole('textbox', { name: /Nombre del Negocio/i }).first();

        if (await businessNameInput.count()) {
          await expect(businessNameInput).toBeVisible();
          await businessNameInput.fill('Negocio Prueba Automatización');
        } else {
          const filled = [];
          if (await fallbackInput.count()) {
            await expect(fallbackInput).toBeVisible();
            await fallbackInput.fill('Negocio Prueba Automatización');
            filled.push('placeholder');
          } else if (await fallbackTextbox.count()) {
            await expect(fallbackTextbox).toBeVisible();
            await fallbackTextbox.fill('Negocio Prueba Automatización');
            filled.push('role-textbox');
          }
          expect(filled.length, 'Could not locate "Nombre del Negocio" input field').toBeGreaterThan(0);
        }

        await expect(page.getByText('Tienes 2 de 3 negocios', { exact: false })).toBeVisible({
          timeout: 30000
        });
        await expect(page.getByRole('button', { name: 'Cancelar', exact: true })).toBeVisible({
          timeout: 30000
        });
        await expect(page.getByRole('button', { name: 'Crear Negocio', exact: true })).toBeVisible({
          timeout: 30000
        });
        await captureCheckpoint(page, '03_agregar_negocio_modal');
        await clickByVisibleText(page, 'Cancelar');
      }, report, 'Agregar Negocio modal');
    });

    await test.step('4) Open Administrar Negocios', async () => {
      await setResult(async () => {
        if (!(await page.getByText('Administrar Negocios', { exact: false }).first().isVisible().catch(() => false))) {
          await clickByVisibleText(page, 'Mi Negocio');
        }
        await clickByVisibleText(page, 'Administrar Negocios');

        await expect(page.getByText('Información General', { exact: false })).toBeVisible({ timeout: 30000 });
        await expect(page.getByText('Detalles de la Cuenta', { exact: false })).toBeVisible({ timeout: 30000 });
        await expect(page.getByText('Tus Negocios', { exact: false })).toBeVisible({ timeout: 30000 });
        await expect(page.getByText('Sección Legal', { exact: false })).toBeVisible({ timeout: 30000 });
        await captureCheckpoint(page, '04_administrar_negocios_page_full', true);
      }, report, 'Administrar Negocios view');
    });

    await test.step('5) Validate Información General', async () => {
      await setResult(async () => {
        const section = page.locator('section, div').filter({ hasText: 'Información General' }).first();
        await expect(section).toBeVisible({ timeout: 30000 });
        await assertUserNameVisible(section);
        await expect(section.getByText(/@/, { exact: false })).toBeVisible({ timeout: 30000 });
        await expect(section.getByText('BUSINESS PLAN', { exact: false })).toBeVisible({ timeout: 30000 });
        await expect(section.getByRole('button', { name: 'Cambiar Plan', exact: false })).toBeVisible({
          timeout: 30000
        });
      }, report, 'Información General');
    });

    await test.step('6) Validate Detalles de la Cuenta', async () => {
      await setResult(async () => {
        const section = page.locator('section, div').filter({ hasText: 'Detalles de la Cuenta' }).first();
        await expect(section).toBeVisible({ timeout: 30000 });
        await expect(section.getByText('Cuenta creada', { exact: false })).toBeVisible({ timeout: 30000 });
        await expect(section.getByText('Estado activo', { exact: false })).toBeVisible({ timeout: 30000 });
        await expect(section.getByText('Idioma seleccionado', { exact: false })).toBeVisible({ timeout: 30000 });
      }, report, 'Detalles de la Cuenta');
    });

    await test.step('7) Validate Tus Negocios', async () => {
      await setResult(async () => {
        const section = page.locator('section, div').filter({ hasText: 'Tus Negocios' }).first();
        await expect(section).toBeVisible({ timeout: 30000 });
        await expect(section.getByText('Agregar Negocio', { exact: false })).toBeVisible({ timeout: 30000 });
        await expect(section.getByText('Tienes 2 de 3 negocios', { exact: false })).toBeVisible({
          timeout: 30000
        });
      }, report, 'Tus Negocios');
    });

    await test.step('8) Validate Términos y Condiciones', async () => {
      await setResult(async () => {
        const [newPage] = await Promise.all([
          context.waitForEvent('page', { timeout: 10000 }).catch(() => null),
          clickByVisibleText(page, 'Términos y Condiciones')
        ]);

        const targetPage = newPage || page;
        await waitForUiLoad(targetPage);
        await expect(targetPage.getByText('Términos y Condiciones', { exact: false })).toBeVisible({
          timeout: 30000
        });
        await expect(targetPage.locator('body')).toContainText(/t[eé]rminos|condiciones|legal/i, {
          timeout: 30000
        });
        legalUrls.terminos = targetPage.url();
        await captureCheckpoint(targetPage, '08_terminos_y_condiciones');

        if (newPage) {
          await returnToAppPage(context, page);
        } else {
          await page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => {});
          await waitForUiLoad(page);
        }
      }, report, 'Términos y Condiciones');
    });

    await test.step('9) Validate Política de Privacidad', async () => {
      await setResult(async () => {
        const [newPage] = await Promise.all([
          context.waitForEvent('page', { timeout: 10000 }).catch(() => null),
          clickByVisibleText(page, 'Política de Privacidad')
        ]);

        const targetPage = newPage || page;
        await waitForUiLoad(targetPage);
        await expect(targetPage.getByText('Política de Privacidad', { exact: false })).toBeVisible({
          timeout: 30000
        });
        await expect(targetPage.locator('body')).toContainText(/privacidad|datos|legal/i, {
          timeout: 30000
        });
        legalUrls.privacidad = targetPage.url();
        await captureCheckpoint(targetPage, '09_politica_de_privacidad');

        if (newPage) {
          await returnToAppPage(context, page);
        } else {
          await page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => {});
          await waitForUiLoad(page);
        }
      }, report, 'Política de Privacidad');
    });

    const finalReportPath = path.join(ARTIFACTS_DIR, 'final_report.json');
    fs.writeFileSync(
      finalReportPath,
      JSON.stringify(
        {
          name: TEST_NAME,
          generatedAt: new Date().toISOString(),
          report,
          legalUrls
        },
        null,
        2
      )
    );

    console.log('=== FINAL REPORT ===');
    console.log(JSON.stringify(report, null, 2));
    console.log('Legal URLs:', JSON.stringify(legalUrls, null, 2));
  });
});
