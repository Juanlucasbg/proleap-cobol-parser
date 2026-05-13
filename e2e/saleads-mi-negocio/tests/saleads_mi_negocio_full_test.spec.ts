import fs from 'node:fs';
import { expect, test, type BrowserContext, type Locator, type Page, type TestInfo } from '@playwright/test';

const GOOGLE_ACCOUNT = 'juanlucasbarbiergarzon@gmail.com';

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

type ReportStatus = 'PASS' | 'FAIL';

const REPORT_FIELDS: ReportField[] = [
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

function initReport(): Record<ReportField, ReportStatus> {
  const report = {} as Record<ReportField, ReportStatus>;

  for (const field of REPORT_FIELDS) {
    report[field] = 'FAIL';
  }

  return report;
}

async function waitForUiToSettle(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(800);
}

function buildTextSelectors(text: string): string[] {
  return [
    `button:has-text("${text}")`,
    `[role="button"]:has-text("${text}")`,
    `a:has-text("${text}")`,
    `[role="menuitem"]:has-text("${text}")`,
    `li:has-text("${text}")`,
    `span:has-text("${text}")`,
    `text=${text}`,
  ];
}

async function firstVisibleLocator(page: Page, selectors: string[], timeoutMs = 15_000): Promise<Locator> {
  const start = Date.now();

  while (Date.now() - start < timeoutMs) {
    for (const selector of selectors) {
      const candidate = page.locator(selector).first();
      if (await candidate.isVisible().catch(() => false)) {
        return candidate;
      }
    }

    await page.waitForTimeout(250);
  }

  throw new Error(`Could not find visible locator for selectors: ${selectors.join(' | ')}`);
}

async function clickVisibleText(page: Page, textVariants: string[]): Promise<Locator> {
  const selectors = textVariants.flatMap((text) => buildTextSelectors(text));
  const locator = await firstVisibleLocator(page, selectors);
  await locator.click();
  await waitForUiToSettle(page);
  return locator;
}

async function checkpoint(page: Page, testInfo: TestInfo, filename: string, fullPage = false): Promise<void> {
  const filePath = testInfo.outputPath(filename);
  await page.screenshot({ path: filePath, fullPage });
  await testInfo.attach(filename, {
    path: filePath,
    contentType: 'image/png',
  });
}

async function chooseGoogleAccountIfVisible(targetPage: Page): Promise<void> {
  const accountOption = targetPage.getByText(GOOGLE_ACCOUNT, { exact: true }).first();

  if (await accountOption.isVisible().catch(() => false)) {
    await accountOption.click();
    await waitForUiToSettle(targetPage);
  }
}

async function ensureStartingPage(page: Page): Promise<void> {
  const startUrlFromEnv = process.env.SALEADS_LOGIN_URL;

  if (page.url() === 'about:blank') {
    if (!startUrlFromEnv) {
      throw new Error(
        'Page started at about:blank. Provide SALEADS_LOGIN_URL, or start from a browser session already on the SaleADS login page.',
      );
    }

    await page.goto(startUrlFromEnv, { waitUntil: 'domcontentloaded' });
    await waitForUiToSettle(page);
  }
}

async function validateLegalDocument(params: {
  appPage: Page;
  context: BrowserContext;
  linkText: string;
  headingPattern: RegExp;
  screenshotName: string;
  testInfo: TestInfo;
}): Promise<string> {
  const { appPage, context, headingPattern, linkText, screenshotName, testInfo } = params;
  const popupPromise = context.waitForEvent('page', { timeout: 6_000 }).catch(() => null);

  await clickVisibleText(appPage, [linkText]);
  const popup = await popupPromise;
  const targetPage = popup ?? appPage;
  await waitForUiToSettle(targetPage);

  const headingByRole = targetPage.getByRole('heading', { name: headingPattern }).first();
  if (await headingByRole.isVisible().catch(() => false)) {
    await expect(headingByRole).toBeVisible();
  } else {
    await expect(targetPage.getByText(headingPattern).first()).toBeVisible();
  }

  const bodyText = await targetPage.locator('body').innerText();
  if (bodyText.trim().length < 120) {
    throw new Error(`Legal content appears too short for ${linkText}.`);
  }

  await checkpoint(targetPage, testInfo, screenshotName, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
  } else {
    await appPage.goBack({ waitUntil: 'domcontentloaded' });
    await waitForUiToSettle(appPage);
  }

  return finalUrl;
}

test('saleads_mi_negocio_full_test', async ({ page, context }, testInfo) => {
  const report = initReport();
  const failures: string[] = [];
  let termsUrl = '';
  let privacyUrl = '';

  const evaluateStep = async (field: ReportField, action: () => Promise<void>) => {
    try {
      await action();
      report[field] = 'PASS';
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      report[field] = 'FAIL';
      failures.push(`${field}: ${message}`);
    }
  };

  await evaluateStep('Login', async () => {
    await ensureStartingPage(page);

    const popupPromise = context.waitForEvent('page', { timeout: 6_000 }).catch(() => null);
    await clickVisibleText(page, [
      'Sign in with Google',
      'Iniciar sesión con Google',
      'Iniciar sesion con Google',
      'Continuar con Google',
      'Google',
    ]);

    const googlePage = await popupPromise;
    if (googlePage) {
      await waitForUiToSettle(googlePage);
      await chooseGoogleAccountIfVisible(googlePage);
    } else {
      await chooseGoogleAccountIfVisible(page);
    }

    await expect(page.locator('aside, nav').first()).toBeVisible();
    await expect(page.getByText(/Negocio/i).first()).toBeVisible();
    await checkpoint(page, testInfo, '01-dashboard-loaded.png', true);
  });

  await evaluateStep('Mi Negocio menu', async () => {
    await clickVisibleText(page, ['Mi Negocio']);
    await expect(page.getByText('Agregar Negocio').first()).toBeVisible();
    await expect(page.getByText('Administrar Negocios').first()).toBeVisible();
    await checkpoint(page, testInfo, '02-mi-negocio-expanded.png');
  });

  await evaluateStep('Agregar Negocio modal', async () => {
    await clickVisibleText(page, ['Agregar Negocio']);
    await expect(page.getByText('Crear Nuevo Negocio').first()).toBeVisible();
    await expect(page.getByText('Nombre del Negocio').first()).toBeVisible();
    await expect(page.getByText('Tienes 2 de 3 negocios').first()).toBeVisible();
    await expect(page.getByText('Cancelar').first()).toBeVisible();
    await expect(page.getByText('Crear Negocio').first()).toBeVisible();

    await checkpoint(page, testInfo, '03-crear-negocio-modal.png');

    const dialog = page.locator('[role="dialog"]').filter({ hasText: 'Crear Nuevo Negocio' }).first();
    const scope = (await dialog.isVisible().catch(() => false)) ? dialog : page;
    const nameInputByLabel = scope.getByLabel('Nombre del Negocio').first();

    if (await nameInputByLabel.isVisible().catch(() => false)) {
      await nameInputByLabel.fill('Negocio Prueba Automatización');
    } else {
      const fallbackInput = scope.locator('input').first();
      await expect(fallbackInput).toBeVisible();
      await fallbackInput.fill('Negocio Prueba Automatización');
    }

    await clickVisibleText(page, ['Cancelar']);
    await expect(page.getByText('Crear Nuevo Negocio').first()).not.toBeVisible({ timeout: 8_000 });
  });

  await evaluateStep('Administrar Negocios view', async () => {
    await clickVisibleText(page, ['Mi Negocio']);
    await clickVisibleText(page, ['Administrar Negocios']);
    await expect(page.getByText('Información General').first()).toBeVisible();
    await expect(page.getByText('Detalles de la Cuenta').first()).toBeVisible();
    await expect(page.getByText('Tus Negocios').first()).toBeVisible();
    await expect(page.getByText('Sección Legal').first()).toBeVisible();
    await checkpoint(page, testInfo, '04-administrar-negocios.png', true);
  });

  await evaluateStep('Información General', async () => {
    await expect(page.getByText(/@/).first()).toBeVisible();
    await expect(page.getByText('BUSINESS PLAN').first()).toBeVisible();
    await expect(page.getByText('Cambiar Plan').first()).toBeVisible();
  });

  await evaluateStep('Detalles de la Cuenta', async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await evaluateStep('Tus Negocios', async () => {
    await expect(page.getByText('Tus Negocios').first()).toBeVisible();
    await expect(page.getByText('Agregar Negocio').first()).toBeVisible();
    await expect(page.getByText('Tienes 2 de 3 negocios').first()).toBeVisible();
  });

  await evaluateStep('Términos y Condiciones', async () => {
    termsUrl = await validateLegalDocument({
      appPage: page,
      context,
      linkText: 'Términos y Condiciones',
      headingPattern: /Términos y Condiciones/i,
      screenshotName: '05-terminos-y-condiciones.png',
      testInfo,
    });
  });

  await evaluateStep('Política de Privacidad', async () => {
    privacyUrl = await validateLegalDocument({
      appPage: page,
      context,
      linkText: 'Política de Privacidad',
      headingPattern: /Política de Privacidad/i,
      screenshotName: '06-politica-de-privacidad.png',
      testInfo,
    });
  });

  const finalReport = {
    report,
    evidence: {
      termsAndConditionsUrl: termsUrl,
      privacyPolicyUrl: privacyUrl,
    },
    failures,
  };

  const reportFile = testInfo.outputPath('final-report.json');
  fs.writeFileSync(reportFile, JSON.stringify(finalReport, null, 2));
  await testInfo.attach('final-report.json', {
    path: reportFile,
    contentType: 'application/json',
  });

  const reportLines = REPORT_FIELDS.map((field) => `${field}: ${report[field]}`);
  const printable = [
    'Final Report',
    ...reportLines,
    `Términos y Condiciones URL: ${termsUrl || 'N/A'}`,
    `Política de Privacidad URL: ${privacyUrl || 'N/A'}`,
  ].join('\n');

  console.log(printable);

  if (failures.length > 0) {
    throw new Error(`Workflow validations failed:\n${failures.join('\n')}`);
  }
});
