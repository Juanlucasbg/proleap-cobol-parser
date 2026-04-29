import { test, expect, type Locator, type Page } from '@playwright/test';
import * as fs from 'node:fs';
import * as path from 'node:path';

type StepStatus = 'PASS' | 'FAIL';

interface StepResult {
  status: StepStatus;
  details: string;
  evidence?: string[];
}

type Report = {
  Login: StepResult;
  'Mi Negocio menu': StepResult;
  'Agregar Negocio modal': StepResult;
  'Administrar Negocios view': StepResult;
  'Información General': StepResult;
  'Detalles de la Cuenta': StepResult;
  'Tus Negocios': StepResult;
  'Términos y Condiciones': StepResult;
  'Política de Privacidad': StepResult;
};

const REQUIRED_REPORT_KEYS: Array<keyof Report> = [
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

const CHECKPOINTS_DIR = path.resolve(process.cwd(), 'artifacts', 'saleads_mi_negocio_full_test');
const REPORT_FILE = path.join(CHECKPOINTS_DIR, 'final-report.json');

const defaultStep = (): StepResult => ({ status: 'FAIL', details: 'Not executed.' });

function initReport(): Report {
  return {
    Login: defaultStep(),
    'Mi Negocio menu': defaultStep(),
    'Agregar Negocio modal': defaultStep(),
    'Administrar Negocios view': defaultStep(),
    'Información General': defaultStep(),
    'Detalles de la Cuenta': defaultStep(),
    'Tus Negocios': defaultStep(),
    'Términos y Condiciones': defaultStep(),
    'Política de Privacidad': defaultStep()
  };
}

function writeReport(report: Report): void {
  fs.mkdirSync(CHECKPOINTS_DIR, { recursive: true });
  fs.writeFileSync(REPORT_FILE, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
}

async function waitForUiAfterClick(page: Page, timeout = 10_000): Promise<void> {
  await page.waitForLoadState('domcontentloaded', { timeout }).catch(() => undefined);
  await page.waitForLoadState('networkidle', { timeout }).catch(() => undefined);
  await page.waitForTimeout(500);
}

function anyMatchLocator(page: Page, texts: string[], exact = false): Locator {
  return page.locator(
    texts
      .map((text) => {
        const normalized = text.replace(/"/g, '\\"');
        return exact ? `text="${normalized}"` : `text=${normalized}`;
      })
      .join(', ')
  );
}

async function clickFirstVisible(locator: Locator): Promise<void> {
  const total = await locator.count();
  for (let i = 0; i < total; i += 1) {
    const current = locator.nth(i);
    if (await current.isVisible().catch(() => false)) {
      await current.click();
      return;
    }
  }
  throw new Error('No visible element found for locator.');
}

async function ensureVisible(page: Page, candidates: string[], timeout = 8_000): Promise<void> {
  const locator = anyMatchLocator(page, candidates);
  await expect(locator.first()).toBeVisible({ timeout });
}

async function clickByText(page: Page, candidates: string[], timeout = 10_000): Promise<void> {
  const locator = anyMatchLocator(page, candidates);
  await expect(locator.first()).toBeVisible({ timeout });
  await clickFirstVisible(locator);
  await waitForUiAfterClick(page);
}

async function captureCheckpoint(page: Page, name: string, fullPage = false): Promise<string> {
  fs.mkdirSync(CHECKPOINTS_DIR, { recursive: true });
  const safeName = name.replace(/[^a-zA-Z0-9-_]+/g, '_');
  const filePath = path.join(CHECKPOINTS_DIR, `${safeName}.png`);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function resolvePopupOrSameTabNavigation(page: Page, timeout = 12_000): Promise<{
  targetPage: Page;
  openedInNewTab: boolean;
}> {
  const popupPromise = page.waitForEvent('popup', { timeout }).catch(() => null);
  const navPromise = page
    .waitForNavigation({ timeout, waitUntil: 'domcontentloaded' })
    .then(() => true)
    .catch(() => false);

  const [popup, navigated] = await Promise.all([popupPromise, navPromise]);
  if (popup) {
    await popup.waitForLoadState('domcontentloaded', { timeout }).catch(() => undefined);
    await popup.waitForLoadState('networkidle', { timeout }).catch(() => undefined);
    return { targetPage: popup, openedInNewTab: true };
  }

  if (navigated) {
    await page.waitForLoadState('networkidle', { timeout }).catch(() => undefined);
    return { targetPage: page, openedInNewTab: false };
  }

  throw new Error('No popup or page navigation detected after legal link click.');
}

function markPrerequisiteFailure(report: Report, key: keyof Report, prerequisite: keyof Report): void {
  report[key] = {
    status: 'FAIL',
    details: `Prerequisite failed: ${prerequisite}.`
  };
}

test.describe('SaleADS Mi Negocio Full Workflow', () => {
  test('saleads_mi_negocio_full_test', async ({ page }) => {
    const report = initReport();
    const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.saleads_login_url;
    const googleEmail = 'juanlucasbarbiergarzon@gmail.com';

    let inAppPage: Page = page;

    // Step 1: Login with Google
    try {
      if (loginUrl) {
        await page.goto(loginUrl, { waitUntil: 'domcontentloaded' });
        await page.waitForLoadState('networkidle').catch(() => undefined);
      } else if (page.url() === 'about:blank') {
        throw new Error(
          'Missing SALEADS_LOGIN_URL and browser is on about:blank. Provide login URL or pre-open SaleADS login page.'
        );
      }

      const loginButtonCandidates = [
        'Sign in with Google',
        'Continuar con Google',
        'Iniciar sesion con Google',
        'Inicia sesion con Google',
        'Google'
      ];

      const loginButton = anyMatchLocator(page, loginButtonCandidates);
      await expect(loginButton.first()).toBeVisible({ timeout: 20_000 });

      const popupPromise = page.waitForEvent('popup', { timeout: 15_000 }).catch(() => null);
      await clickFirstVisible(loginButton);
      await waitForUiAfterClick(page);
      const loginPopup = await popupPromise;

      if (loginPopup) {
        await loginPopup.waitForLoadState('domcontentloaded', { timeout: 15_000 }).catch(() => undefined);

        const accountLocator = anyMatchLocator(loginPopup, [googleEmail], true);
        if (await accountLocator.first().isVisible().catch(() => false)) {
          await accountLocator.first().click();
          await waitForUiAfterClick(loginPopup);
        }

        const useAnotherLocator = anyMatchLocator(loginPopup, ['Use another account', 'Usar otra cuenta']);
        if (await useAnotherLocator.first().isVisible().catch(() => false)) {
          throw new Error(`Google account selector did not show ${googleEmail}. Manual auth state required.`);
        }

        await loginPopup.close().catch(() => undefined);
      } else {
        const accountLocator = anyMatchLocator(page, [googleEmail], true);
        if (await accountLocator.first().isVisible().catch(() => false)) {
          await accountLocator.first().click();
          await waitForUiAfterClick(page);
        }
      }

      // Wait for main app signals.
      await ensureVisible(page, ['Negocio', 'Dashboard', 'Panel', 'Mi Negocio'], 45_000);
      const sidebarSignals = anyMatchLocator(page, ['Negocio', 'Mi Negocio', 'Dashboard', 'Configuracion']);
      await expect(sidebarSignals.first()).toBeVisible({ timeout: 45_000 });

      const dashboardShot = await captureCheckpoint(page, '01_dashboard_loaded');
      report.Login = {
        status: 'PASS',
        details: 'Main application interface and left sidebar are visible after Google login.',
        evidence: [dashboardShot]
      };
    } catch (error) {
      report.Login = {
        status: 'FAIL',
        details: `Login validation failed: ${String(error)}`
      };
      for (const key of REQUIRED_REPORT_KEYS) {
        if (key === 'Login') {
          continue;
        }
        markPrerequisiteFailure(report, key, 'Login');
      }
      writeReport(report);
      throw error;
    }

    // Step 2: Open Mi Negocio menu
    try {
      await ensureVisible(inAppPage, ['Negocio'], 15_000);

      // Ensure we can click either direct item or under Negocio section.
      const miNegocioCandidates = ['Mi Negocio', 'Mi negocio'];
      await clickByText(inAppPage, miNegocioCandidates, 15_000);

      await ensureVisible(inAppPage, ['Agregar Negocio', 'Agregar negocio'], 12_000);
      await ensureVisible(inAppPage, ['Administrar Negocios', 'Administrar negocios'], 12_000);

      const expandedMenuShot = await captureCheckpoint(inAppPage, '02_mi_negocio_menu_expanded');
      report['Mi Negocio menu'] = {
        status: 'PASS',
        details: "Submenu expanded with 'Agregar Negocio' and 'Administrar Negocios'.",
        evidence: [expandedMenuShot]
      };
    } catch (error) {
      report['Mi Negocio menu'] = {
        status: 'FAIL',
        details: `Mi Negocio menu validation failed: ${String(error)}`
      };
      markPrerequisiteFailure(report, 'Agregar Negocio modal', 'Mi Negocio menu');
      markPrerequisiteFailure(report, 'Administrar Negocios view', 'Mi Negocio menu');
      markPrerequisiteFailure(report, 'Información General', 'Administrar Negocios view');
      markPrerequisiteFailure(report, 'Detalles de la Cuenta', 'Administrar Negocios view');
      markPrerequisiteFailure(report, 'Tus Negocios', 'Administrar Negocios view');
      markPrerequisiteFailure(report, 'Términos y Condiciones', 'Administrar Negocios view');
      markPrerequisiteFailure(report, 'Política de Privacidad', 'Administrar Negocios view');
      writeReport(report);
      throw error;
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      await clickByText(inAppPage, ['Agregar Negocio', 'Agregar negocio'], 12_000);
      await ensureVisible(inAppPage, ['Crear Nuevo Negocio', 'Crear nuevo negocio'], 10_000);
      await ensureVisible(inAppPage, ['Nombre del Negocio', 'Nombre del negocio'], 10_000);
      await ensureVisible(inAppPage, ['Tienes 2 de 3 negocios'], 10_000);
      await ensureVisible(inAppPage, ['Cancelar'], 10_000);
      await ensureVisible(inAppPage, ['Crear Negocio', 'Crear negocio'], 10_000);

      const nameInput = inAppPage.getByPlaceholder(/Nombre del Negocio|Nombre del negocio/i);
      if (await nameInput.first().isVisible().catch(() => false)) {
        await nameInput.first().click();
        await nameInput.first().fill('Negocio Prueba Automatizacion');
      } else {
        const labelInput = inAppPage.locator('input').filter({ hasText: '' }).first();
        if (await labelInput.isVisible().catch(() => false)) {
          await labelInput.fill('Negocio Prueba Automatizacion');
        }
      }

      const modalShot = await captureCheckpoint(inAppPage, '03_agregar_negocio_modal');
      await clickByText(inAppPage, ['Cancelar'], 10_000);

      report['Agregar Negocio modal'] = {
        status: 'PASS',
        details: 'Modal shows expected title, fields, usage text and action buttons.',
        evidence: [modalShot]
      };
    } catch (error) {
      report['Agregar Negocio modal'] = {
        status: 'FAIL',
        details: `Agregar Negocio modal validation failed: ${String(error)}`
      };
      markPrerequisiteFailure(report, 'Administrar Negocios view', 'Agregar Negocio modal');
      markPrerequisiteFailure(report, 'Información General', 'Administrar Negocios view');
      markPrerequisiteFailure(report, 'Detalles de la Cuenta', 'Administrar Negocios view');
      markPrerequisiteFailure(report, 'Tus Negocios', 'Administrar Negocios view');
      markPrerequisiteFailure(report, 'Términos y Condiciones', 'Administrar Negocios view');
      markPrerequisiteFailure(report, 'Política de Privacidad', 'Administrar Negocios view');
      writeReport(report);
      throw error;
    }

    // Step 4: Open Administrar Negocios
    try {
      const administrarLocator = anyMatchLocator(inAppPage, ['Administrar Negocios', 'Administrar negocios']);
      if (!(await administrarLocator.first().isVisible().catch(() => false))) {
        await clickByText(inAppPage, ['Mi Negocio', 'Mi negocio'], 12_000);
      }

      await clickByText(inAppPage, ['Administrar Negocios', 'Administrar negocios'], 12_000);

      await ensureVisible(inAppPage, ['Informacion General', 'Información General'], 12_000);
      await ensureVisible(inAppPage, ['Detalles de la Cuenta', 'Detalles de la cuenta'], 12_000);
      await ensureVisible(inAppPage, ['Tus Negocios', 'Tus negocios'], 12_000);
      await ensureVisible(inAppPage, ['Seccion Legal', 'Sección Legal'], 12_000);

      const accountPageShot = await captureCheckpoint(inAppPage, '04_administrar_negocios_full', true);
      report['Administrar Negocios view'] = {
        status: 'PASS',
        details: 'Account page loaded with all required sections.',
        evidence: [accountPageShot]
      };
    } catch (error) {
      report['Administrar Negocios view'] = {
        status: 'FAIL',
        details: `Administrar Negocios view validation failed: ${String(error)}`
      };
      markPrerequisiteFailure(report, 'Información General', 'Administrar Negocios view');
      markPrerequisiteFailure(report, 'Detalles de la Cuenta', 'Administrar Negocios view');
      markPrerequisiteFailure(report, 'Tus Negocios', 'Administrar Negocios view');
      markPrerequisiteFailure(report, 'Términos y Condiciones', 'Administrar Negocios view');
      markPrerequisiteFailure(report, 'Política de Privacidad', 'Administrar Negocios view');
      writeReport(report);
      throw error;
    }

    // Step 5: Validate Informacion General
    try {
      await ensureVisible(inAppPage, ['Informacion General', 'Información General'], 10_000);
      await ensureVisible(inAppPage, ['BUSINESS PLAN'], 10_000);
      await ensureVisible(inAppPage, ['Cambiar Plan', 'Cambiar plan'], 10_000);
      await expect(inAppPage.locator('text=@').first()).toBeVisible({ timeout: 10_000 });

      report['Información General'] = {
        status: 'PASS',
        details: 'User info area, email, BUSINESS PLAN and Cambiar Plan are visible.'
      };
    } catch (error) {
      report['Información General'] = {
        status: 'FAIL',
        details: `Informacion General validation failed: ${String(error)}`
      };
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      await ensureVisible(inAppPage, ['Cuenta creada'], 10_000);
      await ensureVisible(inAppPage, ['Estado activo'], 10_000);
      await ensureVisible(inAppPage, ['Idioma seleccionado'], 10_000);

      report['Detalles de la Cuenta'] = {
        status: 'PASS',
        details: "Found 'Cuenta creada', 'Estado activo' and 'Idioma seleccionado'."
      };
    } catch (error) {
      report['Detalles de la Cuenta'] = {
        status: 'FAIL',
        details: `Detalles de la Cuenta validation failed: ${String(error)}`
      };
    }

    // Step 7: Validate Tus Negocios
    try {
      await ensureVisible(inAppPage, ['Tus Negocios', 'Tus negocios'], 10_000);
      await ensureVisible(inAppPage, ['Agregar Negocio', 'Agregar negocio'], 10_000);
      await ensureVisible(inAppPage, ['Tienes 2 de 3 negocios'], 10_000);

      report['Tus Negocios'] = {
        status: 'PASS',
        details: "Business list section includes 'Agregar Negocio' and plan usage text."
      };
    } catch (error) {
      report['Tus Negocios'] = {
        status: 'FAIL',
        details: `Tus Negocios validation failed: ${String(error)}`
      };
    }

    // Step 8: Validate Terminos y Condiciones
    try {
      await clickByText(inAppPage, ['Terminos y Condiciones', 'Términos y Condiciones'], 10_000);
      const termsNav = await resolvePopupOrSameTabNavigation(inAppPage, 15_000);
      const termsPage = termsNav.targetPage;

      await ensureVisible(termsPage, ['Terminos y Condiciones', 'Términos y Condiciones'], 15_000);
      const legalContent = termsPage.locator('main, article, section, body').first();
      await expect(legalContent).toBeVisible({ timeout: 10_000 });

      const termsShot = await captureCheckpoint(termsPage, '08_terminos_y_condiciones');
      const termsUrl = termsPage.url();

      report['Términos y Condiciones'] = {
        status: 'PASS',
        details: `Legal page validated. Final URL: ${termsUrl}`,
        evidence: [termsShot, termsUrl]
      };

      if (termsNav.openedInNewTab) {
        await termsPage.close().catch(() => undefined);
      } else {
        await inAppPage.goBack({ waitUntil: 'domcontentloaded' }).catch(() => undefined);
        await waitForUiAfterClick(inAppPage);
      }
    } catch (error) {
      report['Términos y Condiciones'] = {
        status: 'FAIL',
        details: `Terminos y Condiciones validation failed: ${String(error)}`
      };
    }

    // Step 9: Validate Politica de Privacidad
    try {
      await clickByText(inAppPage, ['Politica de Privacidad', 'Política de Privacidad'], 10_000);
      const privacyNav = await resolvePopupOrSameTabNavigation(inAppPage, 15_000);
      const privacyPage = privacyNav.targetPage;

      await ensureVisible(privacyPage, ['Politica de Privacidad', 'Política de Privacidad'], 15_000);
      const legalContent = privacyPage.locator('main, article, section, body').first();
      await expect(legalContent).toBeVisible({ timeout: 10_000 });

      const privacyShot = await captureCheckpoint(privacyPage, '09_politica_de_privacidad');
      const privacyUrl = privacyPage.url();

      report['Política de Privacidad'] = {
        status: 'PASS',
        details: `Legal page validated. Final URL: ${privacyUrl}`,
        evidence: [privacyShot, privacyUrl]
      };

      if (privacyNav.openedInNewTab) {
        await privacyPage.close().catch(() => undefined);
      } else {
        await inAppPage.goBack({ waitUntil: 'domcontentloaded' }).catch(() => undefined);
        await waitForUiAfterClick(inAppPage);
      }
    } catch (error) {
      report['Política de Privacidad'] = {
        status: 'FAIL',
        details: `Politica de Privacidad validation failed: ${String(error)}`
      };
    }

    writeReport(report);

    const failedSteps = Object.entries(report).filter(([, value]) => value.status === 'FAIL');
    expect(
      failedSteps,
      `Some workflow validations failed:\n${failedSteps.map(([k, v]) => `- ${k}: ${v.details}`).join('\n')}`
    ).toHaveLength(0);
  });
});
