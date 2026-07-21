import { promises as fs } from 'node:fs';
import { expect, Locator, Page, test } from '@playwright/test';

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
] as const;

type ReportField = (typeof REPORT_FIELDS)[number];
type StepStatus = 'PASS' | 'FAIL';
type FinalReport = Record<ReportField, StepStatus>;

function buildFinalReport(): FinalReport {
  return REPORT_FIELDS.reduce((result, field) => {
    result[field] = 'FAIL';
    return result;
  }, {} as FinalReport);
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function slug(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-zA-Z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .toLowerCase();
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(700);
}

function textCandidates(page: Page, text: string): Locator[] {
  const exactText = new RegExp(`^\\s*${escapeRegExp(text)}\\s*$`, 'i');
  const fuzzyText = new RegExp(escapeRegExp(text), 'i');

  return [
    page.getByRole('button', { name: exactText }),
    page.getByRole('link', { name: exactText }),
    page.getByRole('menuitem', { name: exactText }),
    page.getByRole('tab', { name: exactText }),
    page.getByText(exactText),
    page.getByText(fuzzyText)
  ];
}

async function isVisible(locator: Locator, timeout = 1200): Promise<boolean> {
  try {
    await locator.first().waitFor({ state: 'visible', timeout });
    return true;
  } catch {
    return false;
  }
}

async function clickByVisibleText(page: Page, labels: string[]): Promise<void> {
  for (const label of labels) {
    for (const locator of textCandidates(page, label)) {
      if ((await locator.count()) > 0 && (await isVisible(locator))) {
        await locator.first().click();
        await waitForUi(page);
        return;
      }
    }
  }

  throw new Error(`Unable to click any of: ${labels.join(', ')}`);
}

async function expectTextVisible(page: Page, label: string): Promise<void> {
  const fuzzyText = new RegExp(escapeRegExp(label), 'i');
  await expect(page.getByText(fuzzyText).first()).toBeVisible({ timeout: 20_000 });
}

async function captureCheckpoint(
  page: Page,
  screenshotName: string,
  fullPage = false
): Promise<void> {
  const path = test.info().outputPath(screenshotName);
  await page.screenshot({ path, fullPage });
}

async function selectGoogleAccountIfPrompted(page: Page): Promise<void> {
  const emailOption = page.getByText(new RegExp(escapeRegExp(GOOGLE_ACCOUNT_EMAIL), 'i')).first();
  if (await isVisible(emailOption, 7000)) {
    await emailOption.click();
    await waitForUi(page);
  }
}

async function ensureMiNegocioMenuVisible(page: Page): Promise<void> {
  const administrarVisible = await isVisible(
    page.getByText(/Administrar Negocios/i).first(),
    1200
  );
  const agregarVisible = await isVisible(page.getByText(/Agregar Negocio/i).first(), 1200);

  if (administrarVisible && agregarVisible) {
    return;
  }

  await clickByVisibleText(page, ['Negocio']);
  await clickByVisibleText(page, ['Mi Negocio']);
}

async function openLegalLinkAndValidate(
  page: Page,
  linkText: string,
  headingText: string,
  screenshotName: string
): Promise<string> {
  const popupPromise = page.context().waitForEvent('page', { timeout: 4000 }).catch(() => null);
  await clickByVisibleText(page, [linkText]);

  const popup = await popupPromise;
  const legalPage = popup ?? page;

  await legalPage.waitForLoadState('domcontentloaded');
  await legalPage.waitForTimeout(500);

  const headingRegex = new RegExp(escapeRegExp(headingText), 'i');
  const headingByRole = legalPage.getByRole('heading', { name: headingRegex }).first();
  const headingByText = legalPage.getByText(headingRegex).first();

  if ((await headingByRole.count()) > 0) {
    await expect(headingByRole).toBeVisible({ timeout: 20_000 });
  } else {
    await expect(headingByText).toBeVisible({ timeout: 20_000 });
  }

  await expect(legalPage.locator('body')).toContainText(/\S+\s+\S+/, {
    timeout: 20_000
  });

  await captureCheckpoint(legalPage, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await legalPage.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack();
    await waitForUi(page);
  }

  return finalUrl;
}

test('saleads_mi_negocio_full_test', async ({ page }) => {
  const report = buildFinalReport();
  const failures: string[] = [];
  const legalUrls: Record<string, string> = {};

  async function executeStep(field: ReportField, fn: () => Promise<void>): Promise<void> {
    try {
      await fn();
      report[field] = 'PASS';
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      report[field] = 'FAIL';
      failures.push(`${field}: ${message}`);
      console.error(`[${field}] failed:`, message);
    }
  }

  await executeStep('Login', async () => {
    const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_URL;
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: 'domcontentloaded' });
      await waitForUi(page);
    } else if (page.url().startsWith('about:blank')) {
      throw new Error(
        'No login URL detected. Set SALEADS_LOGIN_URL (or SALEADS_URL) for environment-agnostic execution.'
      );
    }

    const authPopupPromise = page.context().waitForEvent('page', { timeout: 6000 }).catch(() => null);
    await clickByVisibleText(page, [
      'Sign in with Google',
      'Iniciar sesión con Google',
      'Continuar con Google',
      'Google'
    ]);

    const authPopup = await authPopupPromise;
    const authPage = authPopup ?? page;

    await selectGoogleAccountIfPrompted(authPage);

    if (authPopup) {
      await authPopup.waitForEvent('close', { timeout: 40_000 }).catch(() => undefined);
      await page.bringToFront();
    }

    await page.waitForLoadState('domcontentloaded');
    await page.waitForLoadState('networkidle').catch(() => undefined);

    await expect(page.locator('aside, nav').first()).toBeVisible({ timeout: 40_000 });
    await expect(page.getByText(/Negocio|Dashboard|Inicio/i).first()).toBeVisible({
      timeout: 40_000
    });

    await captureCheckpoint(page, '01-dashboard-loaded.png', true);
  });

  await executeStep('Mi Negocio menu', async () => {
    await expect(page.locator('aside, nav').first()).toBeVisible({ timeout: 20_000 });
    await clickByVisibleText(page, ['Negocio']);
    await clickByVisibleText(page, ['Mi Negocio']);

    await expectTextVisible(page, 'Agregar Negocio');
    await expectTextVisible(page, 'Administrar Negocios');

    await captureCheckpoint(page, '02-mi-negocio-menu-expanded.png');
  });

  await executeStep('Agregar Negocio modal', async () => {
    await clickByVisibleText(page, ['Agregar Negocio']);

    await expectTextVisible(page, 'Crear Nuevo Negocio');
    await expectTextVisible(page, 'Nombre del Negocio');
    await expectTextVisible(page, 'Tienes 2 de 3 negocios');
    await expect(page.getByRole('button', { name: /Cancelar/i }).first()).toBeVisible({
      timeout: 20_000
    });
    await expect(page.getByRole('button', { name: /Crear Negocio/i }).first()).toBeVisible({
      timeout: 20_000
    });

    const nameInputByLabel = page.getByLabel(/Nombre del Negocio/i).first();
    const nameInputByPlaceholder = page.getByPlaceholder(/Nombre del Negocio/i).first();
    const nameInput = (await isVisible(nameInputByLabel, 1500))
      ? nameInputByLabel
      : nameInputByPlaceholder;

    if (await isVisible(nameInput, 1500)) {
      await nameInput.click();
      await nameInput.fill('Negocio Prueba Automatización');
      await waitForUi(page);
    }

    await captureCheckpoint(page, '03-agregar-negocio-modal.png');
    await clickByVisibleText(page, ['Cancelar']);
  });

  await executeStep('Administrar Negocios view', async () => {
    await ensureMiNegocioMenuVisible(page);
    await clickByVisibleText(page, ['Administrar Negocios']);

    await expectTextVisible(page, 'Información General');
    await expectTextVisible(page, 'Detalles de la Cuenta');
    await expectTextVisible(page, 'Tus Negocios');
    await expectTextVisible(page, 'Sección Legal');

    await captureCheckpoint(page, '04-administrar-negocios-view.png', true);
  });

  await executeStep('Información General', async () => {
    await expectTextVisible(page, 'Información General');
    await expect(page.locator('body')).toContainText(/BUSINESS PLAN/i, { timeout: 20_000 });
    await expect(page.getByRole('button', { name: /Cambiar Plan/i }).first()).toBeVisible({
      timeout: 20_000
    });

    const hasExpectedEmail = await isVisible(page.getByText(GOOGLE_ACCOUNT_EMAIL).first(), 1500);
    if (!hasExpectedEmail) {
      await expect(page.locator('body')).toContainText(
        /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i,
        { timeout: 20_000 }
      );
    }

    await expect(page.locator('body')).toContainText(/Nombre|Usuario|Name/i, { timeout: 20_000 });
  });

  await executeStep('Detalles de la Cuenta', async () => {
    await expectTextVisible(page, 'Cuenta creada');
    await expectTextVisible(page, 'Estado activo');
    await expectTextVisible(page, 'Idioma seleccionado');
  });

  await executeStep('Tus Negocios', async () => {
    await expectTextVisible(page, 'Tus Negocios');
    await expect(page.getByRole('button', { name: /Agregar Negocio/i }).first()).toBeVisible({
      timeout: 20_000
    });
    await expectTextVisible(page, 'Tienes 2 de 3 negocios');

    const businessItems = page.locator('li, [role="listitem"], table tbody tr, [data-testid*="business"]');
    if ((await businessItems.count()) > 0) {
      await expect(businessItems.first()).toBeVisible({ timeout: 20_000 });
    } else {
      await expect(page.locator('body')).toContainText(/Negocio/i, { timeout: 20_000 });
    }
  });

  await executeStep('Términos y Condiciones', async () => {
    const finalUrl = await openLegalLinkAndValidate(
      page,
      'Términos y Condiciones',
      'Términos y Condiciones',
      '05-terminos-y-condiciones.png'
    );
    legalUrls['Términos y Condiciones'] = finalUrl;
    console.log(`Términos y Condiciones URL: ${finalUrl}`);
  });

  await executeStep('Política de Privacidad', async () => {
    const finalUrl = await openLegalLinkAndValidate(
      page,
      'Política de Privacidad',
      'Política de Privacidad',
      '06-politica-de-privacidad.png'
    );
    legalUrls['Política de Privacidad'] = finalUrl;
    console.log(`Política de Privacidad URL: ${finalUrl}`);
  });

  const finalReport = {
    testName: 'saleads_mi_negocio_full_test',
    validationReport: report,
    legalUrls,
    failures
  };

  const reportFile = test.info().outputPath(`final-report-${slug(test.info().title)}.json`);
  await fs.writeFile(reportFile, JSON.stringify(finalReport, null, 2), 'utf8');
  await test.info().attach('final-report', { path: reportFile, contentType: 'application/json' });

  console.log('Final validation report:', JSON.stringify(finalReport, null, 2));

  expect(
    failures,
    `Validation failures detected:\n${failures.map((failure) => `- ${failure}`).join('\n')}`
  ).toEqual([]);
});
