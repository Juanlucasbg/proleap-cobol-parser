import { expect, test, type Locator, type Page } from '@playwright/test';
import path from 'node:path';
import fs from 'node:fs';

type StepKey =
  | 'Login'
  | 'Mi Negocio menu'
  | 'Agregar Negocio modal'
  | 'Administrar Negocios view'
  | 'Información General'
  | 'Detalles de la Cuenta'
  | 'Tus Negocios'
  | 'Términos y Condiciones'
  | 'Política de Privacidad';

type StepResult = {
  status: 'PASS' | 'FAIL';
  details: string;
  screenshot?: string;
  finalUrl?: string;
};

type RunReport = Record<StepKey, StepResult>;

const ARTIFACTS_DIR = path.resolve(__dirname, '..', 'artifacts');

const reportTemplate = (): RunReport => ({
  Login: { status: 'FAIL', details: 'Not executed' },
  'Mi Negocio menu': { status: 'FAIL', details: 'Not executed' },
  'Agregar Negocio modal': { status: 'FAIL', details: 'Not executed' },
  'Administrar Negocios view': { status: 'FAIL', details: 'Not executed' },
  'Información General': { status: 'FAIL', details: 'Not executed' },
  'Detalles de la Cuenta': { status: 'FAIL', details: 'Not executed' },
  'Tus Negocios': { status: 'FAIL', details: 'Not executed' },
  'Términos y Condiciones': { status: 'FAIL', details: 'Not executed' },
  'Política de Privacidad': { status: 'FAIL', details: 'Not executed' },
});

const escapeRegExp = (value: string): string => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

async function firstVisible(candidates: Locator[], description: string): Promise<Locator> {
  for (const locator of candidates) {
    if ((await locator.count()) > 0 && (await locator.first().isVisible())) {
      return locator.first();
    }
  }

  throw new Error(`Unable to locate visible element for "${description}"`);
}

async function waitForApplicationStable(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForLoadState('networkidle');
}

async function takeCheckpoint(page: Page, fileName: string): Promise<string> {
  const fullPath = path.join(ARTIFACTS_DIR, fileName);
  await page.screenshot({ path: fullPath, fullPage: true });
  return fullPath;
}

async function clickVisibleText(page: Page, label: string): Promise<void> {
  const escaped = escapeRegExp(label);
  const target = await firstVisible(
    [
    page.getByRole('button', { name: new RegExp(escaped, 'i') }),
    page.getByRole('link', { name: new RegExp(escaped, 'i') }),
    page.getByRole('menuitem', { name: new RegExp(escaped, 'i') }),
    page.getByRole('tab', { name: new RegExp(escaped, 'i') }),
    page.getByText(new RegExp(`^\\s*${escaped}\\s*$`, 'i')),
    page.getByText(new RegExp(escaped, 'i')),
    ],
    label,
  );
  await target.click();
  await waitForApplicationStable(page);
}

async function openLegalLinkAndValidate(
  page: Page,
  linkText: string,
  expectedHeading: string,
): Promise<{ screenshot: string; finalUrl: string }> {
  const maybePopup = page.waitForEvent('popup', { timeout: 5000 }).catch(() => null);
  await clickVisibleText(page, linkText);
  const popup = await maybePopup;

  const targetPage = popup ?? page;
  await waitForApplicationStable(targetPage);
  const headingByRole = targetPage.getByRole('heading', { name: new RegExp(expectedHeading, 'i') });
  if ((await headingByRole.count()) > 0) {
    await expect(headingByRole.first()).toBeVisible();
  } else {
    await expect(targetPage.getByText(new RegExp(expectedHeading, 'i'))).toBeVisible();
  }
  await expect(targetPage.getByText(/(t[eé]rminos|condiciones|privacidad|datos|uso|responsabilidad)/i)).toBeVisible();

  const screenshot = await takeCheckpoint(
    targetPage,
    `${linkText.toLowerCase().replace(/\s+/g, '-')}-${Date.now()}.png`,
  );
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForApplicationStable(page);
  }

  return { screenshot, finalUrl };
}

test.describe('SaleADS Mi Negocio workflow', () => {
  test('saleads_mi_negocio_full_test', async ({ page }) => {
    fs.mkdirSync(ARTIFACTS_DIR, { recursive: true });

    const report = reportTemplate();
    const saleadsBaseUrl = process.env.SALEADS_BASE_URL;
    const googleAccountEmail =
      process.env.GOOGLE_ACCOUNT_EMAIL ?? 'juanlucasbarbiergarzon@gmail.com';
    const googleAccountName = process.env.GOOGLE_ACCOUNT_NAME;
    let workflowError: unknown = null;

    try {
      if (saleadsBaseUrl) {
        await page.goto(saleadsBaseUrl, { waitUntil: 'domcontentloaded' });
        await waitForApplicationStable(page);
      }

      // 1) Login with Google
      try {
        await clickVisibleText(page, 'Sign in with Google').catch(async () =>
          clickVisibleText(page, 'Iniciar con Google'),
        );

        const googleAccount = page
          .getByText(new RegExp(escapeRegExp(googleAccountEmail), 'i'))
          .first();
        if ((await googleAccount.count()) > 0) {
          await googleAccount.click();
        }

        await waitForApplicationStable(page);
        await expect(page.locator('aside')).toBeVisible();
        const loginShot = await takeCheckpoint(page, `01-dashboard-${Date.now()}.png`);

        report.Login = {
          status: 'PASS',
          details: 'Main interface and left sidebar are visible after Google login.',
          screenshot: loginShot,
        };
      } catch (error) {
        report.Login = { status: 'FAIL', details: String(error) };
        throw error;
      }

      // 2) Open Mi Negocio menu
      await clickVisibleText(page, 'Negocio');
      await clickVisibleText(page, 'Mi Negocio');
      await expect(page.getByText(/Agregar Negocio/i)).toBeVisible();
      await expect(page.getByText(/Administrar Negocios/i)).toBeVisible();
      const menuShot = await takeCheckpoint(page, `02-mi-negocio-menu-${Date.now()}.png`);
      report['Mi Negocio menu'] = {
        status: 'PASS',
        details: 'Mi Negocio submenu expanded and required options are visible.',
        screenshot: menuShot,
      };

      // 3) Validate Agregar Negocio modal
      await clickVisibleText(page, 'Agregar Negocio');
      await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
      const businessNameInput = await firstVisible(
        [
          page.getByLabel(/Nombre del Negocio/i),
          page.getByPlaceholder(/Nombre del Negocio/i),
          page.getByRole('textbox', { name: /Nombre del Negocio/i }),
        ],
        'Nombre del Negocio input',
      );
      await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(page.getByRole('button', { name: /Cancelar/i })).toBeVisible();
      await expect(page.getByRole('button', { name: /Crear Negocio/i })).toBeVisible();
      const modalShot = await takeCheckpoint(page, `03-agregar-negocio-modal-${Date.now()}.png`);

      await businessNameInput.fill('Negocio Prueba Automatización');
      await clickVisibleText(page, 'Cancelar');

      report['Agregar Negocio modal'] = {
        status: 'PASS',
        details: 'Crear Nuevo Negocio modal and required controls are present.',
        screenshot: modalShot,
      };

      // 4) Open Administrar Negocios
      if ((await page.getByText(/Administrar Negocios/i).count()) === 0) {
        await clickVisibleText(page, 'Mi Negocio');
      }
      await clickVisibleText(page, 'Administrar Negocios');
      await expect(page.getByText(/Informaci[oó]n General/i)).toBeVisible();
      await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
      await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
      await expect(page.getByText(/Secci[oó]n Legal/i)).toBeVisible();
      const accountShot = await takeCheckpoint(page, `04-administrar-negocios-${Date.now()}.png`);
      report['Administrar Negocios view'] = {
        status: 'PASS',
        details: 'Account page loaded with all major sections.',
        screenshot: accountShot,
      };

      // 5) Validate Información General
      const emailLocator = await firstVisible(
        [page.getByText(new RegExp(escapeRegExp(googleAccountEmail), 'i')), page.getByText(/@/)],
        'user email',
      );
      await expect(emailLocator).toBeVisible();
      await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
      await expect(page.getByRole('button', { name: /Cambiar Plan/i })).toBeVisible();

      if (googleAccountName) {
        await expect(page.getByText(new RegExp(escapeRegExp(googleAccountName), 'i'))).toBeVisible();
      } else {
        const identityContainer = emailLocator.locator(
          'xpath=ancestor::*[self::section or self::article or self::div][1]',
        );
        const identityText = (await identityContainer.innerText()).split('\n').map((line) => line.trim());
        const userNameLikeLine = identityText.find(
          (line) =>
            line.length > 2 &&
            !line.includes('@') &&
            !/business plan|cambiar plan|informaci[oó]n general/i.test(line) &&
            /[a-zA-Záéíóúñ]/i.test(line),
        );
        expect(
          userNameLikeLine,
          'A user name should be visible near account identity details. Set GOOGLE_ACCOUNT_NAME for strict matching.',
        ).toBeTruthy();
      }

      report['Información General'] = {
        status: 'PASS',
        details: 'User name, user email, plan, and change-plan action are visible.',
      };

      // 6) Validate Detalles de la Cuenta
      await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
      await expect(page.getByText(/Estado activo/i)).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
      report['Detalles de la Cuenta'] = {
        status: 'PASS',
        details: 'Expected account details labels are visible.',
      };

      // 7) Validate Tus Negocios
      await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
      await expect(page.getByRole('button', { name: /Agregar Negocio/i })).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
      report['Tus Negocios'] = {
        status: 'PASS',
        details: 'Business list area and limits are visible.',
      };

      // 8) Validate Términos y Condiciones
      const termsResult = await openLegalLinkAndValidate(
        page,
        'Términos y Condiciones',
        'Términos y Condiciones',
      );
      report['Términos y Condiciones'] = {
        status: 'PASS',
        details: 'Legal page loaded with heading and body content.',
        screenshot: termsResult.screenshot,
        finalUrl: termsResult.finalUrl,
      };

      // 9) Validate Política de Privacidad
      const privacyResult = await openLegalLinkAndValidate(
        page,
        'Política de Privacidad',
        'Política de Privacidad',
      );
      report['Política de Privacidad'] = {
        status: 'PASS',
        details: 'Privacy page loaded with heading and body content.',
        screenshot: privacyResult.screenshot,
        finalUrl: privacyResult.finalUrl,
      };
    } catch (error) {
      workflowError = error;
    }

    const reportPath = path.join(ARTIFACTS_DIR, `final-report-${Date.now()}.json`);
    fs.writeFileSync(reportPath, JSON.stringify(report, null, 2), 'utf8');
    test.info().attach('final-report', {
      path: reportPath,
      contentType: 'application/json',
    });

    // 10) Final pass/fail gate
    const failing = Object.entries(report).filter(([, result]) => result.status !== 'PASS');
    expect(
      failing,
      `Workflow has failing steps: ${failing.map(([step]) => step).join(', ') || 'none'}`,
    ).toHaveLength(0);

    if (workflowError) {
      throw workflowError;
    }
  });
});
