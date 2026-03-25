import { expect, test, type Page, type Locator } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

type StepStatus = 'PASS' | 'FAIL';

type StepResult = {
  name: string;
  status: StepStatus;
  details: string[];
  evidence: string[];
};

const REPORT_DIR = path.resolve(process.cwd(), 'artifacts', 'saleads-mi-negocio');

function ensureReportDir(): void {
  fs.mkdirSync(REPORT_DIR, { recursive: true });
}

function sanitizeFileName(name: string): string {
  return name
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(800);
}

function containsAny(locator: Locator, values: string[]): Promise<boolean> {
  return locator
    .allTextContents()
    .then((texts) =>
      texts.some((text) =>
        values.some((value) => text.toLowerCase().includes(value.toLowerCase())),
      ),
    );
}

test.describe('SaleADS Mi Negocio full workflow', () => {
  test('saleads_mi_negocio_full_test', async ({ page }, testInfo) => {
    ensureReportDir();

    const results: Record<string, StepResult> = {
      Login: { name: 'Login', status: 'FAIL', details: [], evidence: [] },
      'Mi Negocio menu': { name: 'Mi Negocio menu', status: 'FAIL', details: [], evidence: [] },
      'Agregar Negocio modal': { name: 'Agregar Negocio modal', status: 'FAIL', details: [], evidence: [] },
      'Administrar Negocios view': {
        name: 'Administrar Negocios view',
        status: 'FAIL',
        details: [],
        evidence: [],
      },
      'Información General': { name: 'Información General', status: 'FAIL', details: [], evidence: [] },
      'Detalles de la Cuenta': { name: 'Detalles de la Cuenta', status: 'FAIL', details: [], evidence: [] },
      'Tus Negocios': { name: 'Tus Negocios', status: 'FAIL', details: [], evidence: [] },
      'Términos y Condiciones': {
        name: 'Términos y Condiciones',
        status: 'FAIL',
        details: [],
        evidence: [],
      },
      'Política de Privacidad': {
        name: 'Política de Privacidad',
        status: 'FAIL',
        details: [],
        evidence: [],
      },
    };

    const urls: Record<'terms' | 'privacy', string> = {
      terms: '',
      privacy: '',
    };

    const screenshot = async (checkpoint: string, target: Page = page): Promise<string> => {
      const fileName = `${String(testInfo.retry)}-${sanitizeFileName(checkpoint)}.png`;
      const absolute = path.join(REPORT_DIR, fileName);
      await target.screenshot({ path: absolute, fullPage: true });
      return absolute;
    };

    // Step 1: Login with Google
    {
      await test.step('Step 1: Login with Google', async () => {
        await waitForUi(page);

        const loginButton = page
          .getByRole('button', { name: /google|sign in|iniciar sesi[oó]n/i })
          .first();
        await expect(loginButton).toBeVisible({ timeout: 60_000 });
        await loginButton.click();
        await waitForUi(page);

        const googleSelector = page.getByText('juanlucasbarbiergarzon@gmail.com', { exact: false });
        if (await googleSelector.isVisible({ timeout: 8_000 }).catch(() => false)) {
          await googleSelector.click();
          await waitForUi(page);
        }

        const sidebar = page.locator('aside, nav').first();
        await expect(sidebar).toBeVisible({ timeout: 120_000 });
        results.Login.details.push('Main application interface and left sidebar are visible.');

        const evidence = await screenshot('01-dashboard-loaded');
        results.Login.evidence.push(evidence);
        results.Login.status = 'PASS';
      });
    }

    // Step 2: Open Mi Negocio menu
    {
      await test.step('Step 2: Open Mi Negocio menu', async () => {
        const negocioMenu = page
          .getByRole('button', { name: /mi negocio|negocio/i })
          .or(page.getByRole('link', { name: /mi negocio|negocio/i }))
          .first();
        await expect(negocioMenu).toBeVisible({ timeout: 45_000 });
        await negocioMenu.click();
        await waitForUi(page);

        const agregarNegocioOption = page.getByText(/agregar negocio/i).first();
        const administrarNegociosOption = page.getByText(/administrar negocios/i).first();
        await expect(agregarNegocioOption).toBeVisible({ timeout: 30_000 });
        await expect(administrarNegociosOption).toBeVisible({ timeout: 30_000 });

        results['Mi Negocio menu'].details.push(
          "Submenu expanded with 'Agregar Negocio' and 'Administrar Negocios' visible.",
        );
        const evidence = await screenshot('02-mi-negocio-menu-expanded');
        results['Mi Negocio menu'].evidence.push(evidence);
        results['Mi Negocio menu'].status = 'PASS';
      });
    }

    // Step 3: Validate Agregar Negocio modal
    {
      await test.step('Step 3: Validate Agregar Negocio modal', async () => {
        await page.getByText(/agregar negocio/i).first().click();
        await waitForUi(page);

        const modal = page.getByRole('dialog').first();
        await expect(modal).toBeVisible({ timeout: 30_000 });
        await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();
        await expect(modal.getByLabel(/nombre del negocio/i)).toBeVisible();
        await expect(modal.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
        await expect(modal.getByRole('button', { name: /cancelar/i })).toBeVisible();
        await expect(modal.getByRole('button', { name: /crear negocio/i })).toBeVisible();

        const nameField = modal.getByLabel(/nombre del negocio/i);
        await nameField.click();
        await nameField.fill('Negocio Prueba Automatización');
        await waitForUi(page);

        const evidence = await screenshot('03-agregar-negocio-modal');
        results['Agregar Negocio modal'].evidence.push(evidence);
        results['Agregar Negocio modal'].details.push(
          "Modal validated: title, input, quota text, and 'Cancelar'/'Crear Negocio' buttons.",
        );

        await modal.getByRole('button', { name: /cancelar/i }).click();
        await expect(modal).toBeHidden({ timeout: 15_000 });
        results['Agregar Negocio modal'].status = 'PASS';
      });
    }

    // Step 4: Open Administrar Negocios
    {
      await test.step('Step 4: Open Administrar Negocios', async () => {
        const administrarNegociosOption = page.getByText(/administrar negocios/i).first();
        if (!(await administrarNegociosOption.isVisible().catch(() => false))) {
          const negocioMenu = page
            .getByRole('button', { name: /mi negocio|negocio/i })
            .or(page.getByRole('link', { name: /mi negocio|negocio/i }))
            .first();
          await negocioMenu.click();
          await waitForUi(page);
        }

        await page.getByText(/administrar negocios/i).first().click();
        await waitForUi(page);

        await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible({ timeout: 45_000 });
        await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible({ timeout: 45_000 });
        await expect(page.getByText(/tus negocios/i)).toBeVisible({ timeout: 45_000 });
        await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible({ timeout: 45_000 });

        const evidence = await screenshot('04-administrar-negocios-view');
        results['Administrar Negocios view'].evidence.push(evidence);
        results['Administrar Negocios view'].details.push(
          'Account page loaded with Información General, Detalles de la Cuenta, Tus Negocios, and Sección Legal.',
        );
        results['Administrar Negocios view'].status = 'PASS';
      });
    }

    // Step 5: Validate Información General
    {
      await test.step('Step 5: Validate Información General', async () => {
        const section = page.locator('section,div').filter({ hasText: /informaci[oó]n general/i }).first();
        await expect(section).toBeVisible({ timeout: 30_000 });
        await expect(section.getByText(/@/)).toBeVisible();
        await expect(section.getByText(/business plan/i)).toBeVisible();
        await expect(section.getByRole('button', { name: /cambiar plan/i })).toBeVisible();

        const nonEmptyText = ((await section.allTextContents()).join(' ').replace(/\s+/g, ' ').trim().length > 0);
        expect(nonEmptyText).toBeTruthy();

        results['Información General'].details.push(
          "User info visible, 'BUSINESS PLAN' text visible, and 'Cambiar Plan' button present.",
        );
        results['Información General'].status = 'PASS';
      });
    }

    // Step 6: Validate Detalles de la Cuenta
    {
      await test.step('Step 6: Validate Detalles de la Cuenta', async () => {
        const section = page.locator('section,div').filter({ hasText: /detalles de la cuenta/i }).first();
        await expect(section).toBeVisible({ timeout: 30_000 });
        await expect(section.getByText(/cuenta creada/i)).toBeVisible();
        await expect(section.getByText(/estado activo/i)).toBeVisible();
        await expect(section.getByText(/idioma seleccionado/i)).toBeVisible();

        results['Detalles de la Cuenta'].details.push(
          "'Cuenta creada', 'Estado activo', and 'Idioma seleccionado' validated.",
        );
        results['Detalles de la Cuenta'].status = 'PASS';
      });
    }

    // Step 7: Validate Tus Negocios
    {
      await test.step('Step 7: Validate Tus Negocios', async () => {
        const section = page.locator('section,div').filter({ hasText: /tus negocios/i }).first();
        await expect(section).toBeVisible({ timeout: 30_000 });
        await expect(section.getByRole('button', { name: /agregar negocio/i })).toBeVisible();
        await expect(section.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();

        const listVisible =
          (await containsAny(section.locator('li, div, p, span'), ['negocio', 'business']).catch(() => false)) ||
          (await section.locator('li').count()) > 0;
        expect(listVisible).toBeTruthy();

        results['Tus Negocios'].details.push(
          "Business list area and 'Agregar Negocio' with quota text 'Tienes 2 de 3 negocios' validated.",
        );
        results['Tus Negocios'].status = 'PASS';
      });
    }

    // Step 8: Validate Términos y Condiciones
    {
      await test.step('Step 8: Validate Términos y Condiciones', async () => {
        const termsLink = page
          .getByRole('link', { name: /t[eé]rminos y condiciones/i })
          .or(page.getByText(/t[eé]rminos y condiciones/i))
          .first();
        await expect(termsLink).toBeVisible({ timeout: 30_000 });

        const popupPromise = page.waitForEvent('popup', { timeout: 10_000 }).catch(() => null);
        await termsLink.click();
        await waitForUi(page);

        const popup = await popupPromise;
        const legalPage = popup ?? page;
        await legalPage.waitForLoadState('domcontentloaded');
        await legalPage.waitForTimeout(800);

        await expect(legalPage.getByText(/t[eé]rminos y condiciones/i)).toBeVisible({ timeout: 30_000 });
        const legalBodyVisible =
          (await legalPage.locator('main,article,body').first().textContent())?.trim().length ?? 0;
        expect(legalBodyVisible).toBeGreaterThan(50);

        const evidence = await screenshot('08-terminos-y-condiciones', legalPage);
        urls.terms = legalPage.url();
        results['Términos y Condiciones'].evidence.push(evidence, urls.terms);
        results['Términos y Condiciones'].details.push('Heading and legal content are visible.');
        results['Términos y Condiciones'].status = 'PASS';

        if (popup) {
          await popup.close();
        } else {
          await page.goBack().catch(() => undefined);
          await waitForUi(page);
        }
      });
    }

    // Step 9: Validate Política de Privacidad
    {
      await test.step('Step 9: Validate Política de Privacidad', async () => {
        const privacyLink = page
          .getByRole('link', { name: /pol[ií]tica de privacidad/i })
          .or(page.getByText(/pol[ií]tica de privacidad/i))
          .first();
        await expect(privacyLink).toBeVisible({ timeout: 30_000 });

        const popupPromise = page.waitForEvent('popup', { timeout: 10_000 }).catch(() => null);
        await privacyLink.click();
        await waitForUi(page);

        const popup = await popupPromise;
        const legalPage = popup ?? page;
        await legalPage.waitForLoadState('domcontentloaded');
        await legalPage.waitForTimeout(800);

        await expect(legalPage.getByText(/pol[ií]tica de privacidad/i)).toBeVisible({ timeout: 30_000 });
        const legalBodyVisible =
          (await legalPage.locator('main,article,body').first().textContent())?.trim().length ?? 0;
        expect(legalBodyVisible).toBeGreaterThan(50);

        const evidence = await screenshot('09-politica-de-privacidad', legalPage);
        urls.privacy = legalPage.url();
        results['Política de Privacidad'].evidence.push(evidence, urls.privacy);
        results['Política de Privacidad'].details.push('Heading and legal content are visible.');
        results['Política de Privacidad'].status = 'PASS';

        if (popup) {
          await popup.close();
        } else {
          await page.goBack().catch(() => undefined);
          await waitForUi(page);
        }
      });
    }

    // Step 10: Final report
    const finalReport = {
      testName: 'saleads_mi_negocio_full_test',
      generatedAt: new Date().toISOString(),
      report: results,
      urls,
    };

    const reportPath = path.join(REPORT_DIR, `report-${sanitizeFileName(testInfo.project.name)}.json`);
    fs.writeFileSync(reportPath, `${JSON.stringify(finalReport, null, 2)}\n`, 'utf-8');
    await testInfo.attach('saleads-mi-negocio-report', {
      body: Buffer.from(JSON.stringify(finalReport, null, 2), 'utf-8'),
      contentType: 'application/json',
    });
  });
});
