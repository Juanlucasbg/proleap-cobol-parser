import { expect, test, type Locator, type Page } from '@playwright/test';
import path from 'node:path';
import { promises as fs } from 'node:fs';

type StepState = 'PASS' | 'FAIL';

type StepResult = {
  key: ReportField;
  state: StepState;
  detail: string;
};

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

const ARTIFACTS_DIR = process.env.PW_ARTIFACTS_DIR ?? 'artifacts';
const CHECKPOINTS_DIR = path.join(ARTIFACTS_DIR, 'checkpoints');
const REPORT_DIR = path.join(ARTIFACTS_DIR, 'reports');

const WAIT_UI_MS = 1_500;

async function waitForUi(page: Page, ms = WAIT_UI_MS): Promise<void> {
  await page.waitForTimeout(ms);
}

function normalize(text: string): string {
  return text
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim();
}

async function ensureArtifactsDirs(): Promise<void> {
  await fs.mkdir(CHECKPOINTS_DIR, { recursive: true });
  await fs.mkdir(REPORT_DIR, { recursive: true });
}

async function checkpoint(
  page: Page,
  fileName: string,
  fullPage = false,
): Promise<string> {
  await ensureArtifactsDirs();
  const filePath = path.join(CHECKPOINTS_DIR, fileName);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

function menuItemByText(container: Page | Locator, text: string): Locator {
  const rx = new RegExp(`^\\s*${text}\\s*$`, 'i');
  return container.getByRole('link', { name: rx }).or(container.getByRole('button', { name: rx }));
}

function firstVisible(...locators: Locator[]): Locator {
  return locators.reduce((acc, current) => acc.or(current));
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function validateVisibleText(page: Page, text: string): Promise<void> {
  const primary = page.getByText(new RegExp(text, 'i')).first();
  await expect(primary).toBeVisible();
}

async function validateSectionWithFallbacks(
  page: Page,
  expectedLabel: string,
  fallbacks: string[] = [],
): Promise<void> {
  const candidates = [expectedLabel, ...fallbacks].map((item) =>
    page.getByText(new RegExp(item, 'i')).first(),
  );
  let combined = candidates[0];
  for (let i = 1; i < candidates.length; i += 1) {
    combined = combined.or(candidates[i]);
  }
  await expect(combined).toBeVisible();
}

async function withPopupOrNavigation(
  page: Page,
  action: () => Promise<void>,
): Promise<{ targetPage: Page; usedPopup: boolean }> {
  const popupPromise = page.waitForEvent('popup', { timeout: 7_000 }).catch(() => null);

  await action();

  const popup = await popupPromise;
  if (popup) {
    await popup.waitForLoadState('domcontentloaded');
    await waitForUi(popup);
    return { targetPage: popup, usedPopup: true };
  }

  await page.waitForLoadState('domcontentloaded');
  await waitForUi(page);
  return { targetPage: page, usedPopup: false };
}

async function closeOrReturn(targetPage: Page, appPage: Page, usedPopup: boolean): Promise<void> {
  if (usedPopup) {
    await targetPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
    return;
  }

  await appPage.goBack({ waitUntil: 'domcontentloaded' }).catch(() => undefined);
  await waitForUi(appPage);
}

async function chooseGoogleAccountIfPresent(page: Page, email: string): Promise<void> {
  const emailItem = page.getByText(new RegExp(email.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'i')).first();
  if (await emailItem.isVisible().catch(() => false)) {
    await emailItem.click();
    await waitForUi(page, 2_000);
  }
}

test.describe('SaleADS Mi Negocio full workflow', () => {
  test('saleads_mi_negocio_full_test', async ({ page }) => {
    const results: StepResult[] = [];
    let termsUrl = 'N/A';
    let privacyUrl = 'N/A';

    await test.step('1) Login with Google and validate dashboard', async () => {
      const loginButton = firstVisible(
        page.getByRole('button', { name: /sign in with google|continuar con google|iniciar sesion con google/i }),
        page.getByRole('link', { name: /sign in with google|continuar con google|iniciar sesion con google/i }),
        page.getByText(/sign in with google|continuar con google|iniciar sesion con google/i).first(),
      );

      const loginGate = firstVisible(
        page.getByRole('navigation').first(),
        page.getByText(/negocio|dashboard|panel|inicio/i).first(),
      );

      if (await loginButton.isVisible().catch(() => false)) {
        const popupPromise = page.waitForEvent('popup', { timeout: 15_000 }).catch(() => null);
        await clickAndWait(loginButton, page);

        const popup = await popupPromise;
        if (popup) {
          await popup.waitForLoadState('domcontentloaded');
          await chooseGoogleAccountIfPresent(popup, 'juanlucasbarbiergarzon@gmail.com');
          await popup.close().catch(() => undefined);
          await page.bringToFront();
        } else {
          await chooseGoogleAccountIfPresent(page, 'juanlucasbarbiergarzon@gmail.com');
        }
      }

      await expect(loginGate).toBeVisible();
      const sidebar = page.getByRole('navigation').first();
      await expect(sidebar).toBeVisible();
      await checkpoint(page, '01-dashboard-loaded.png');

      results.push({
        key: 'Login',
        state: 'PASS',
        detail: 'Main application interface loaded and sidebar visible.',
      });
    });

    await test.step('2) Open Mi Negocio menu and validate submenu', async () => {
      const sidebar = page.getByRole('navigation').first();
      await expect(sidebar).toBeVisible();

      const negocioEntry = menuItemByText(sidebar, 'Negocio')
        .or(menuItemByText(page, 'Negocio'))
        .or(page.getByText(/^negocio$/i).first());
      await clickAndWait(negocioEntry, page);

      const miNegocioEntry = menuItemByText(sidebar, 'Mi Negocio')
        .or(menuItemByText(page, 'Mi Negocio'))
        .or(page.getByText(/^mi negocio$/i).first());
      await clickAndWait(miNegocioEntry, page);

      await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
      await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();
      await checkpoint(page, '02-mi-negocio-expanded.png');

      results.push({
        key: 'Mi Negocio menu',
        state: 'PASS',
        detail: 'Mi Negocio expanded with Agregar Negocio and Administrar Negocios entries.',
      });
    });

    await test.step('3) Validate Agregar Negocio modal', async () => {
      const addBusinessMenuItem = page.getByText(/^agregar negocio$/i).first();
      await clickAndWait(addBusinessMenuItem, page);

      const modalTitle = page.getByText(/crear nuevo negocio/i).first();
      await expect(modalTitle).toBeVisible();
      await expect(page.getByLabel(/nombre del negocio/i).or(page.getByPlaceholder(/nombre del negocio/i))).toBeVisible();
      await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();

      const cancelButton = page.getByRole('button', { name: /^cancelar$/i });
      const createButton = page.getByRole('button', { name: /crear negocio/i });
      await expect(cancelButton).toBeVisible();
      await expect(createButton).toBeVisible();

      const businessNameInput = page
        .getByLabel(/nombre del negocio/i)
        .or(page.getByPlaceholder(/nombre del negocio/i))
        .first();
      await businessNameInput.click();
      await businessNameInput.fill('Negocio Prueba Automatización');
      await checkpoint(page, '03-agregar-negocio-modal.png');
      await clickAndWait(cancelButton, page);

      results.push({
        key: 'Agregar Negocio modal',
        state: 'PASS',
        detail:
          'Crear Nuevo Negocio modal validated with Nombre del Negocio, business quota text, and action buttons.',
      });
    });

    await test.step('4) Open Administrar Negocios and validate account sections', async () => {
      const miNegocioEntry = menuItemByText(page, 'Mi Negocio').or(page.getByText(/^mi negocio$/i).first());
      if (!(await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false))) {
        await clickAndWait(miNegocioEntry, page);
      }

      const manageBusinesses = page.getByText(/administrar negocios/i).first();
      await clickAndWait(manageBusinesses, page);
      await page.waitForLoadState('domcontentloaded');
      await waitForUi(page);

      await validateSectionWithFallbacks(page, 'Información General');
      await validateSectionWithFallbacks(page, 'Detalles de la Cuenta');
      await validateSectionWithFallbacks(page, 'Tus Negocios');
      await validateSectionWithFallbacks(page, 'Sección Legal', ['Legal']);

      await checkpoint(page, '04-administrar-negocios-full-page.png', true);
      results.push({
        key: 'Administrar Negocios view',
        state: 'PASS',
        detail: 'All major sections are visible in account management page.',
      });
    });

    await test.step('5) Validate Información General section', async () => {
      await validateSectionWithFallbacks(page, 'Información General');
      await validateSectionWithFallbacks(page, 'BUSINESS PLAN');
      await expect(page.getByRole('button', { name: /cambiar plan/i }).first()).toBeVisible();

      const emailRegex = /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-z]{2,}/i;
      await expect(page.getByText(emailRegex).first()).toBeVisible();

      const headingLike = page
        .locator('h1, h2, h3, h4, h5, h6, p, span, div')
        .filter({ hasNotText: emailRegex })
        .filter({ hasText: /[A-Za-zÁÉÍÓÚáéíóúÑñ]/ })
        .first();
      await expect(headingLike).toBeVisible();

      results.push({
        key: 'Información General',
        state: 'PASS',
        detail: 'Name-like text, email, BUSINESS PLAN and Cambiar Plan were visible.',
      });
    });

    await test.step('6) Validate Detalles de la Cuenta section', async () => {
      await validateSectionWithFallbacks(page, 'Detalles de la Cuenta');
      await validateSectionWithFallbacks(page, 'Cuenta creada');
      await validateSectionWithFallbacks(page, 'Estado activo');
      await validateSectionWithFallbacks(page, 'Idioma seleccionado');

      results.push({
        key: 'Detalles de la Cuenta',
        state: 'PASS',
        detail: 'Cuenta creada, Estado activo and Idioma seleccionado labels are visible.',
      });
    });

    await test.step('7) Validate Tus Negocios section', async () => {
      await validateSectionWithFallbacks(page, 'Tus Negocios');
      await expect(page.getByRole('button', { name: /agregar negocio/i }).first()).toBeVisible();
      await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();

      const businessListRegion = page
        .locator('section,div')
        .filter({ hasText: /tus negocios/i })
        .first();
      await expect(businessListRegion).toBeVisible();

      results.push({
        key: 'Tus Negocios',
        state: 'PASS',
        detail: 'Business list area, Agregar Negocio button and quota text are visible.',
      });
    });

    await test.step('8) Validate Términos y Condiciones', async () => {
      const termsLink = page
        .getByRole('link', { name: /t[eé]rminos y condiciones/i })
        .or(page.getByText(/t[eé]rminos y condiciones/i).first());

      const { targetPage, usedPopup } = await withPopupOrNavigation(page, async () => {
        await clickAndWait(termsLink, page);
      });

      await expect(targetPage.getByText(/t[eé]rminos y condiciones/i).first()).toBeVisible();
      const legalText = await targetPage.locator('main, article, body').innerText();
      expect(normalize(legalText).length).toBeGreaterThan(50);

      await checkpoint(targetPage, '08-terminos-y-condiciones.png', true);
      termsUrl = targetPage.url();

      await closeOrReturn(targetPage, page, usedPopup);
      results.push({
        key: 'Términos y Condiciones',
        state: 'PASS',
        detail: `Legal heading and content visible. URL: ${termsUrl}`,
      });
    });

    await test.step('9) Validate Política de Privacidad', async () => {
      const privacyLink = page
        .getByRole('link', { name: /pol[ií]tica de privacidad/i })
        .or(page.getByText(/pol[ií]tica de privacidad/i).first());

      const { targetPage, usedPopup } = await withPopupOrNavigation(page, async () => {
        await clickAndWait(privacyLink, page);
      });

      await expect(targetPage.getByText(/pol[ií]tica de privacidad/i).first()).toBeVisible();
      const legalText = await targetPage.locator('main, article, body').innerText();
      expect(normalize(legalText).length).toBeGreaterThan(50);

      await checkpoint(targetPage, '09-politica-de-privacidad.png', true);
      privacyUrl = targetPage.url();

      await closeOrReturn(targetPage, page, usedPopup);
      results.push({
        key: 'Política de Privacidad',
        state: 'PASS',
        detail: `Legal heading and content visible. URL: ${privacyUrl}`,
      });
    });

    await test.step('10) Final report with PASS/FAIL by step', async () => {
      const fields: ReportField[] = [
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

      const finalResults = fields.map((field) => {
        const found = results.find((item) => item.key === field);
        return (
          found ?? {
            key: field,
            state: 'FAIL',
            detail: 'Step did not execute or validation did not complete.',
          }
        );
      });

      await ensureArtifactsDirs();
      const report = {
        testName: 'saleads_mi_negocio_full_test',
        generatedAt: new Date().toISOString(),
        legalUrls: {
          termsAndConditions: termsUrl,
          privacyPolicy: privacyUrl,
        },
        results: finalResults,
      };
      const reportPath = path.join(REPORT_DIR, 'saleads_mi_negocio_full_test.report.json');
      await fs.writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, 'utf-8');

      test.info().annotations.push({
        type: 'final-report',
        description: reportPath,
      });

      for (const item of finalResults) {
        expect(item.state, `${item.key}: ${item.detail}`).toBe('PASS');
      }
    });
  });
});
