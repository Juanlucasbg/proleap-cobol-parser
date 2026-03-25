import { expect, Page, test } from '@playwright/test';
import * as fs from 'fs';
import * as path from 'path';

type ReportField =
  | 'Login'
  | 'Mi Negocio menu'
  | 'Agregar Negocio modal'
  | 'Administrar Negocios view'
  | 'Información General'
  | 'Detalles de la Cuenta'
  | 'Tus Negocios'
  | 'Términos y Condiciones'
  | 'Política de Privacidad';

type StepStatus = 'PASS' | 'FAIL';

const SCREENSHOT_DIR = path.resolve(__dirname, 'screenshots');
const REPORT_DIR = path.resolve(__dirname, 'reports');

const report: Record<ReportField, StepStatus> = {
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

function ensureArtifactsDirs(): void {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
  fs.mkdirSync(REPORT_DIR, { recursive: true });
}

async function waitForUiLoad(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForLoadState('networkidle', { timeout: 20_000 }).catch(() => undefined);
}

async function clickByText(page: Page, text: string, exact = true): Promise<void> {
  const locator = page.getByText(text, { exact }).first();
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiLoad(page);
}

async function clickSidebarItem(page: Page, text: string): Promise<void> {
  const sidebar = page.locator('aside, nav').first();
  await expect(sidebar).toBeVisible();
  const target = sidebar.getByText(text, { exact: true }).first();
  await expect(target).toBeVisible();
  await target.click();
  await waitForUiLoad(page);
}

async function openLegalLink(page: Page, linkText: string): Promise<{ url: string; contextPage: Page }> {
  const legalLink = page.getByText(linkText, { exact: true }).first();
  await expect(legalLink).toBeVisible();

  const maybePopup = page.waitForEvent('popup', { timeout: 5_000 }).catch(() => null);
  await legalLink.click();
  const popup = await maybePopup;

  if (popup) {
    await waitForUiLoad(popup);
    return { url: popup.url(), contextPage: popup };
  }

  await waitForUiLoad(page);
  return { url: page.url(), contextPage: page };
}

async function takeCheckpoint(page: Page, name: string, fullPage = false): Promise<void> {
  const fileName = `${Date.now()}-${name.replace(/\s+/g, '_')}.png`;
  await page.screenshot({
    path: path.join(SCREENSHOT_DIR, fileName),
    fullPage,
  });
}

function writeFinalReport(): void {
  const payload = {
    test: 'saleads_mi_negocio_full_test',
    generatedAt: new Date().toISOString(),
    results: report,
  };

  fs.writeFileSync(path.join(REPORT_DIR, 'saleads-mi-negocio-final-report.json'), JSON.stringify(payload, null, 2));
}

test.describe('SaleADS Mi Negocio full workflow', () => {
  test.afterEach(async () => {
    writeFinalReport();
  });

  test('Login + Mi Negocio + legal pages validation', async ({ page }) => {
    ensureArtifactsDirs();

    // Step 1: Login with Google (starting from current login page)
    const googleSignInButton = page
      .getByText(/(login|iniciar sesión|sign in with google|continuar con google)/i)
      .first();
    await expect(googleSignInButton).toBeVisible();
    await googleSignInButton.click();
    await waitForUiLoad(page);

    const googleAccount = page.getByText('juanlucasbarbiergarzon@gmail.com', { exact: true }).first();
    if (await googleAccount.isVisible().catch(() => false)) {
      await googleAccount.click();
      await waitForUiLoad(page);
    }

    const sidebar = page.locator('aside, nav').first();
    await expect(sidebar).toBeVisible();
    report.Login = 'PASS';
    await takeCheckpoint(page, 'dashboard_loaded');

    // Step 2: Open Mi Negocio menu
    await clickSidebarItem(page, 'Negocio');
    await clickSidebarItem(page, 'Mi Negocio');
    await expect(page.getByText('Agregar Negocio', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('Administrar Negocios', { exact: true }).first()).toBeVisible();
    report['Mi Negocio menu'] = 'PASS';
    await takeCheckpoint(page, 'mi_negocio_menu_expanded');

    // Step 3: Validate Agregar Negocio modal
    await clickByText(page, 'Agregar Negocio');
    await expect(page.getByText('Crear Nuevo Negocio', { exact: true }).first()).toBeVisible();
    await expect(page.getByLabel('Nombre del Negocio', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('Tienes 2 de 3 negocios', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('Cancelar', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('Crear Negocio', { exact: true }).first()).toBeVisible();
    await takeCheckpoint(page, 'agregar_negocio_modal');

    const businessNameInput = page.getByLabel('Nombre del Negocio', { exact: true }).first();
    await businessNameInput.click();
    await businessNameInput.fill('Negocio Prueba Automatización');
    await clickByText(page, 'Cancelar');

    report['Agregar Negocio modal'] = 'PASS';

    // Step 4: Open Administrar Negocios
    const administrarNegociosEntry = page.getByText('Administrar Negocios', { exact: true }).first();
    if (!(await administrarNegociosEntry.isVisible().catch(() => false))) {
      await clickSidebarItem(page, 'Mi Negocio');
    }

    await clickByText(page, 'Administrar Negocios');
    await expect(page.getByText('Información General', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('Detalles de la Cuenta', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('Tus Negocios', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('Sección Legal', { exact: true }).first()).toBeVisible();
    report['Administrar Negocios view'] = 'PASS';
    await takeCheckpoint(page, 'administrar_negocios_view', true);

    // Step 5: Validate Información General
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByText('Cambiar Plan', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('@').first()).toBeVisible();
    const visibleTextSnapshot = await page.locator('body').innerText();
    expect(visibleTextSnapshot.trim().length).toBeGreaterThan(0);
    report['Información General'] = 'PASS';

    // Step 6: Validate Detalles de la Cuenta
    await expect(page.getByText('Cuenta creada', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('Estado activo', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('Idioma seleccionado', { exact: true }).first()).toBeVisible();
    report['Detalles de la Cuenta'] = 'PASS';

    // Step 7: Validate Tus Negocios
    await expect(page.getByText('Tus Negocios', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('Agregar Negocio', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('Tienes 2 de 3 negocios', { exact: true }).first()).toBeVisible();
    report['Tus Negocios'] = 'PASS';

    // Step 8: Validate Términos y Condiciones
    const appPage = page;
    const terms = await openLegalLink(appPage, 'Términos y Condiciones');
    await expect(terms.contextPage.getByText('Términos y Condiciones', { exact: true }).first()).toBeVisible();
    await expect(terms.contextPage.locator('body')).toContainText(/términos|condiciones/i);
    report['Términos y Condiciones'] = 'PASS';
    await takeCheckpoint(terms.contextPage, 'terminos_y_condiciones');
    fs.writeFileSync(path.join(REPORT_DIR, 'terminos-url.txt'), `${terms.url}\n`);
    if (terms.contextPage !== appPage) {
      await terms.contextPage.close();
      await appPage.bringToFront();
      await waitForUiLoad(appPage);
    } else {
      await appPage.goBack({ waitUntil: 'domcontentloaded' }).catch(() => undefined);
      await waitForUiLoad(appPage);
    }

    // Step 9: Validate Política de Privacidad
    const privacy = await openLegalLink(appPage, 'Política de Privacidad');
    await expect(privacy.contextPage.getByText('Política de Privacidad', { exact: true }).first()).toBeVisible();
    await expect(privacy.contextPage.locator('body')).toContainText(/privacidad|datos/i);
    report['Política de Privacidad'] = 'PASS';
    await takeCheckpoint(privacy.contextPage, 'politica_de_privacidad');
    fs.writeFileSync(path.join(REPORT_DIR, 'politica-url.txt'), `${privacy.url}\n`);
    if (privacy.contextPage !== appPage) {
      await privacy.contextPage.close();
      await appPage.bringToFront();
      await waitForUiLoad(appPage);
    } else {
      await appPage.goBack({ waitUntil: 'domcontentloaded' }).catch(() => undefined);
      await waitForUiLoad(appPage);
    }
  });
});
