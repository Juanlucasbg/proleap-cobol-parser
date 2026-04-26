const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');

const OUTPUT_DIR = path.resolve(__dirname, '..', 'artifacts');
const SCREENSHOT_DIR = path.join(OUTPUT_DIR, 'screenshots');
const REPORT_FILE = path.join(OUTPUT_DIR, 'final-report.json');

const STEP_KEYS = [
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

const STEP_REPORT = Object.fromEntries(STEP_KEYS.map((key) => [key, { status: 'NOT_RUN' }]));

const LOGIN_BUTTON_PATTERNS = [
  /sign in with google/i,
  /iniciar sesión con google/i,
  /login with google/i,
  /google/i,
];

async function waitForAppReady(page) {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(1000);
}

async function clickAndWait(locator, page) {
  await expect(locator).toBeVisible({ timeout: 20000 });
  await locator.click();
  await waitForAppReady(page);
}

async function saveCheckpoint(page, name, options = {}) {
  const safeName = name
    .normalize('NFKD')
    .replace(/[^\w\s.-]/g, '')
    .trim()
    .replace(/\s+/g, '-')
    .toLowerCase();
  const fileName = `${String(options.index ?? '').padStart(2, '0')}-${safeName}.png`;
  const filePath = path.join(SCREENSHOT_DIR, fileName);
  await page.screenshot({ path: filePath, fullPage: Boolean(options.fullPage) });
  return filePath;
}

async function markStep(stepName, fn) {
  try {
    const details = await fn();
    STEP_REPORT[stepName] = { status: 'PASS', ...(details || {}) };
  } catch (error) {
    STEP_REPORT[stepName] = {
      status: 'FAIL',
      error: error instanceof Error ? error.message : String(error),
    };
    throw error;
  }
}

function locatorByText(page, text) {
  return page.getByText(text, { exact: false });
}

async function clickFirstVisible(candidates) {
  for (const locator of candidates) {
    if (await locator.count()) {
      const first = locator.first();
      if (await first.isVisible()) {
        await first.click();
        return true;
      }
    }
  }
  return false;
}

test.describe('SaleADS - Mi Negocio full workflow', () => {
  test('login with Google and validate complete Mi Negocio flow', async ({ page }) => {
    const targetUrl = process.env.SALEADS_URL || process.env.BASE_URL;

    fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
    let runError = null;

    try {
      if (!targetUrl) {
        throw new Error('SALEADS_URL (or BASE_URL) environment variable is required for this test run.');
      }
      await page.goto(targetUrl, { waitUntil: 'domcontentloaded' });
      await waitForAppReady(page);

      // Step 1: Login with Google
      await markStep('Login', async () => {
        let loginClicked = false;
        const authPopupPromise = page.waitForEvent('popup', { timeout: 8000 }).catch(() => null);

        for (const pattern of LOGIN_BUTTON_PATTERNS) {
          const byRoleButton = page.getByRole('button', { name: pattern });
          if (await byRoleButton.count()) {
            const first = byRoleButton.first();
            if (await first.isVisible()) {
              await clickAndWait(first, page);
              loginClicked = true;
              break;
            }
          }
        }

        if (!loginClicked) {
          loginClicked = await clickFirstVisible([
            page.getByRole('link', { name: /google/i }),
            page.locator('button:has-text("Google")'),
            page.locator('[aria-label*="Google" i]'),
          ]);
          await waitForAppReady(page);
        }

        if (!loginClicked) {
          throw new Error('Could not find a login button for Google sign-in.');
        }

        const authPopup = await authPopupPromise;
        if (authPopup) {
          await authPopup.waitForLoadState('domcontentloaded');
          const popupAccountChoice = authPopup.getByText('juanlucasbarbiergarzon@gmail.com', { exact: true });
          if (await popupAccountChoice.count()) {
            await popupAccountChoice.first().click();
            await authPopup.waitForTimeout(1000);
          }
          await page.bringToFront();
        } else {
          const accountChoice = page.getByText('juanlucasbarbiergarzon@gmail.com', { exact: true });
          if (await accountChoice.count()) {
            await clickAndWait(accountChoice.first(), page);
          }
        }

        await expect(page.locator('aside')).toBeVisible({ timeout: 60000 });
        await expect(page.locator('aside')).toContainText(/negocio|mi negocio/i, { timeout: 60000 });

        const screenshot = await saveCheckpoint(page, 'dashboard-loaded', { index: 1, fullPage: true });
        return { screenshot };
      });

      // Step 2: Open Mi Negocio menu
      await markStep('Mi Negocio menu', async () => {
        const negocioSection = page.getByText('Negocio', { exact: false }).first();
        await clickAndWait(negocioSection, page);

        const miNegocioOption = page.getByRole('link', { name: /mi negocio/i }).first();
        if (await miNegocioOption.count()) {
          await clickAndWait(miNegocioOption, page);
        } else {
          await clickAndWait(page.getByText('Mi Negocio', { exact: false }).first(), page);
        }

        await expect(locatorByText(page, 'Agregar Negocio')).toBeVisible({ timeout: 30000 });
        await expect(locatorByText(page, 'Administrar Negocios')).toBeVisible({ timeout: 30000 });

        const screenshot = await saveCheckpoint(page, 'mi-negocio-expanded-menu', {
          index: 2,
          fullPage: true,
        });
        return { screenshot };
      });

      // Step 3: Validate Agregar Negocio modal
      await markStep('Agregar Negocio modal', async () => {
        await clickAndWait(locatorByText(page, 'Agregar Negocio').first(), page);

        await expect(locatorByText(page, 'Crear Nuevo Negocio')).toBeVisible({ timeout: 30000 });
        await expect(page.getByLabel('Nombre del Negocio')).toBeVisible({ timeout: 30000 });
        await expect(locatorByText(page, 'Tienes 2 de 3 negocios')).toBeVisible({ timeout: 30000 });
        await expect(page.getByRole('button', { name: 'Cancelar' })).toBeVisible({ timeout: 30000 });
        await expect(page.getByRole('button', { name: 'Crear Negocio' })).toBeVisible({ timeout: 30000 });

        await page.getByLabel('Nombre del Negocio').fill('Negocio Prueba Automatización');

        const screenshot = await saveCheckpoint(page, 'agregar-negocio-modal', {
          index: 3,
          fullPage: true,
        });

        await clickAndWait(page.getByRole('button', { name: 'Cancelar' }), page);
        await expect(locatorByText(page, 'Crear Nuevo Negocio')).toHaveCount(0, { timeout: 15000 });

        return { screenshot };
      });

      // Step 4: Open Administrar Negocios
      await markStep('Administrar Negocios view', async () => {
        const adminOption = locatorByText(page, 'Administrar Negocios').first();
        if (!(await adminOption.isVisible())) {
          await clickAndWait(locatorByText(page, 'Mi Negocio').first(), page);
        }

        await clickAndWait(adminOption, page);

        await expect(locatorByText(page, 'Información General')).toBeVisible({ timeout: 60000 });
        await expect(locatorByText(page, 'Detalles de la Cuenta')).toBeVisible({ timeout: 60000 });
        await expect(locatorByText(page, 'Tus Negocios')).toBeVisible({ timeout: 60000 });
        await expect(locatorByText(page, 'Sección Legal')).toBeVisible({ timeout: 60000 });

        const screenshot = await saveCheckpoint(page, 'administrar-negocios-view', {
          index: 4,
          fullPage: true,
        });
        return { screenshot };
      });

      // Step 5: Validate Información General
      await markStep('Información General', async () => {
        const infoSection = locatorByText(page, 'Información General').first();
        await expect(infoSection).toBeVisible({ timeout: 30000 });

        const knownEmail = locatorByText(page, 'juanlucasbarbiergarzon@gmail.com').first();
        if (await knownEmail.count()) {
          await expect(knownEmail).toBeVisible({ timeout: 30000 });
        } else {
          await expect(page.locator('main, body')).toContainText(
            /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i,
            { timeout: 30000 },
          );
        }

        const nameHint = page.getByText(/juan|lucas|barbier|garzon/i).first();
        if (await nameHint.count()) {
          await expect(nameHint).toBeVisible({ timeout: 30000 });
        } else {
          await expect(page.locator('main h1, main h2, main h3, main p, main span').first()).toBeVisible({
            timeout: 30000,
          });
        }

        await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible({ timeout: 30000 });
        await expect(page.getByRole('button', { name: /Cambiar Plan/i })).toBeVisible({ timeout: 30000 });

        return {
          notes: 'User identity details, plan badge, and Cambiar Plan button visible.',
        };
      });

      // Step 6: Validate Detalles de la Cuenta
      await markStep('Detalles de la Cuenta', async () => {
        await expect(locatorByText(page, 'Cuenta creada')).toBeVisible({ timeout: 30000 });
        await expect(locatorByText(page, 'Estado activo')).toBeVisible({ timeout: 30000 });
        await expect(locatorByText(page, 'Idioma seleccionado')).toBeVisible({ timeout: 30000 });
        return { notes: 'Account details labels are visible.' };
      });

      // Step 7: Validate Tus Negocios
      await markStep('Tus Negocios', async () => {
        await expect(locatorByText(page, 'Tus Negocios')).toBeVisible({ timeout: 30000 });
        await expect(locatorByText(page, 'Agregar Negocio')).toBeVisible({ timeout: 30000 });
        await expect(locatorByText(page, 'Tienes 2 de 3 negocios')).toBeVisible({ timeout: 30000 });
        return { notes: 'Business list and quota label are visible.' };
      });

      // Step 8: Validate Términos y Condiciones
      await markStep('Términos y Condiciones', async () => {
        const termsLink = page.getByRole('link', { name: /Términos y Condiciones/i }).first();
        await expect(termsLink).toBeVisible({ timeout: 30000 });

        const popupPromise = page.waitForEvent('popup', { timeout: 8000 }).catch(() => null);
        await termsLink.click();

        const popup = await popupPromise;
        const legalPage = popup ?? page;
        await legalPage.waitForLoadState('domcontentloaded');
        await legalPage.waitForTimeout(1000);

        await expect(legalPage.getByRole('heading', { name: /Términos y Condiciones/i })).toBeVisible({
          timeout: 30000,
        });
        await expect(legalPage.locator('body')).toContainText(/\S+/, { timeout: 30000 });

        const screenshot = await saveCheckpoint(legalPage, 'terminos-y-condiciones', {
          index: 8,
          fullPage: true,
        });
        const finalUrl = legalPage.url();

        if (popup) {
          await popup.close();
          await page.bringToFront();
          await waitForAppReady(page);
        } else {
          await page.goBack().catch(() => {});
          await waitForAppReady(page);
        }

        return { screenshot, finalUrl };
      });

      // Step 9: Validate Política de Privacidad
      await markStep('Política de Privacidad', async () => {
        const privacyLink = page.getByRole('link', { name: /Política de Privacidad/i }).first();
        await expect(privacyLink).toBeVisible({ timeout: 30000 });

        const popupPromise = page.waitForEvent('popup', { timeout: 8000 }).catch(() => null);
        await privacyLink.click();

        const popup = await popupPromise;
        const legalPage = popup ?? page;
        await legalPage.waitForLoadState('domcontentloaded');
        await legalPage.waitForTimeout(1000);

        await expect(legalPage.getByRole('heading', { name: /Política de Privacidad/i })).toBeVisible({
          timeout: 30000,
        });
        await expect(legalPage.locator('body')).toContainText(/\S+/, { timeout: 30000 });

        const screenshot = await saveCheckpoint(legalPage, 'politica-de-privacidad', {
          index: 9,
          fullPage: true,
        });
        const finalUrl = legalPage.url();

        if (popup) {
          await popup.close();
          await page.bringToFront();
          await waitForAppReady(page);
        } else {
          await page.goBack().catch(() => {});
          await waitForAppReady(page);
        }

        return { screenshot, finalUrl };
      });
    } catch (error) {
      runError = error;
    } finally {
      for (const key of STEP_KEYS) {
        if (STEP_REPORT[key].status === 'NOT_RUN') {
          STEP_REPORT[key] = {
            status: 'FAIL',
            error:
              runError instanceof Error
                ? `Not executed due to an earlier failure in the workflow: ${runError.message}`
                : 'Not executed due to an earlier failure in the workflow.',
          };
        }
      }
    }

    if (runError) {
      throw runError;
    }
  });

  test.afterAll(async () => {
    fs.mkdirSync(OUTPUT_DIR, { recursive: true });
    fs.writeFileSync(
      REPORT_FILE,
      JSON.stringify(
        {
          generatedAt: new Date().toISOString(),
          workflow: 'saleads_mi_negocio_full_test',
          report: STEP_REPORT,
        },
        null,
        2,
      ),
    );
  });
});
