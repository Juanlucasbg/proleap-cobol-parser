import { expect, test, type BrowserContext, type Locator, type Page, type TestInfo } from '@playwright/test';
import fs from 'fs/promises';
import path from 'path';

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

type StepStatus = 'PASS' | 'FAIL';

type StepResult = {
  status: StepStatus;
  details?: string;
};

const CHECKPOINT_DIR = 'checkpoints';

const STEP_ORDER: StepKey[] = [
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

function createReport(): Record<StepKey, StepResult> {
  return {
    Login: { status: 'FAIL', details: 'Not executed' },
    'Mi Negocio menu': { status: 'FAIL', details: 'Not executed' },
    'Agregar Negocio modal': { status: 'FAIL', details: 'Not executed' },
    'Administrar Negocios view': { status: 'FAIL', details: 'Not executed' },
    'Información General': { status: 'FAIL', details: 'Not executed' },
    'Detalles de la Cuenta': { status: 'FAIL', details: 'Not executed' },
    'Tus Negocios': { status: 'FAIL', details: 'Not executed' },
    'Términos y Condiciones': { status: 'FAIL', details: 'Not executed' },
    'Política de Privacidad': { status: 'FAIL', details: 'Not executed' },
  };
}

async function waitForUiAfterClick(page: Page): Promise<void> {
  // Keep tests deterministic after interactions on slower environments.
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(800);
}

function normalizeVisibleText(text: string): string {
  return text.replace(/\s+/g, ' ').trim();
}

async function firstVisible(page: Page, candidates: string[]): Promise<Locator> {
  for (const candidate of candidates) {
    const locator = page.getByText(candidate, { exact: false });
    const count = await locator.count();
    for (let i = 0; i < count; i++) {
      const item = locator.nth(i);
      if (await item.isVisible()) {
        return item;
      }
    }
  }

  throw new Error(`No visible locator found for candidates: ${candidates.join(', ')}`);
}

async function clickByVisibleText(page: Page, candidates: string[]): Promise<void> {
  const target = await firstVisible(page, candidates);
  await target.click();
  await waitForUiAfterClick(page);
}

async function assertAnyVisibleText(page: Page, candidates: string[]): Promise<void> {
  const target = await firstVisible(page, candidates);
  await expect(target).toBeVisible();
}

async function takeCheckpoint(page: Page, testInfo: TestInfo, fileName: string, fullPage = false): Promise<void> {
  const outputPath = testInfo.outputPath(path.join(CHECKPOINT_DIR, fileName));
  await page.screenshot({ path: outputPath, fullPage });
  await testInfo.attach(fileName, { path: outputPath, contentType: 'image/png' });
}

async function chooseGoogleAccountIfPrompted(page: Page): Promise<void> {
  const accountText = 'juanlucasbarbiergarzon@gmail.com';
  const accountOption = page.getByText(accountText, { exact: false });
  if (await accountOption.first().isVisible().catch(() => false)) {
    await accountOption.first().click();
    await waitForUiAfterClick(page);
  }
}

async function findAppPage(context: BrowserContext, fallback: Page): Promise<Page> {
  const deadline = Date.now() + 60_000;

  while (Date.now() < deadline) {
    const pages = context.pages();
    for (const candidate of pages) {
      const sidebarVisible = await candidate.locator('aside').first().isVisible().catch(() => false);
      if (sidebarVisible) {
        return candidate;
      }

      const businessEntryVisible = await candidate.getByText('Mi Negocio', { exact: false }).first().isVisible().catch(() => false);
      if (businessEntryVisible) {
        return candidate;
      }
    }

    await fallback.waitForTimeout(1_000);
  }

  return fallback;
}

async function withPotentialNewTab(
  context: BrowserContext,
  currentPage: Page,
  trigger: () => Promise<void>,
): Promise<{ legalPage: Page; finalUrl: string; previousUrl: string; usedNewTab: boolean }> {
  const previousUrl = currentPage.url();
  const previousPageCount = context.pages().length;
  let popupPage: Page | null = null;

  const popupPromise = currentPage.waitForEvent('popup', { timeout: 6_000 }).catch(() => null);
  await trigger();
  popupPage = await popupPromise;

  if (popupPage) {
    await popupPage.waitForLoadState('domcontentloaded');
    await popupPage.waitForTimeout(700);
    return { legalPage: popupPage, finalUrl: popupPage.url(), previousUrl, usedNewTab: true };
  }

  const currentPageCount = context.pages().length;
  if (currentPageCount > previousPageCount) {
    const newPage = context.pages()[currentPageCount - 1];
    await newPage.waitForLoadState('domcontentloaded');
    await newPage.waitForTimeout(700);
    return { legalPage: newPage, finalUrl: newPage.url(), previousUrl, usedNewTab: true };
  }

  await currentPage.waitForLoadState('domcontentloaded');
  await currentPage.waitForTimeout(700);
  return { legalPage: currentPage, finalUrl: currentPage.url(), previousUrl, usedNewTab: false };
}

async function validateLegalPage(
  context: BrowserContext,
  appPage: Page,
  testInfo: TestInfo,
  linkLabels: string[],
  headingCandidates: string[],
  checkpointName: string,
): Promise<{ finalUrl: string }> {
  const { legalPage, finalUrl, previousUrl, usedNewTab } = await withPotentialNewTab(context, appPage, async () => {
    await clickByVisibleText(appPage, linkLabels);
  });

  await assertAnyVisibleText(legalPage, headingCandidates);
  const pageText = normalizeVisibleText(await legalPage.locator('body').innerText());
  expect(pageText.length).toBeGreaterThan(200);

  await takeCheckpoint(legalPage, testInfo, checkpointName, true);

  if (usedNewTab) {
    await legalPage.close();
    await appPage.bringToFront();
    await waitForUiAfterClick(appPage);
  } else if (legalPage === appPage) {
    const historyNavigation = await appPage.goBack({ waitUntil: 'domcontentloaded' }).catch(() => null);
    if (!historyNavigation && previousUrl) {
      await appPage.goto(previousUrl, { waitUntil: 'domcontentloaded' });
    }
    await waitForUiAfterClick(appPage);
    await assertAnyVisibleText(appPage, ['Sección Legal', 'Tus Negocios', 'Información General']);
  } else if (legalPage !== appPage) {
    await appPage.bringToFront();
    await waitForUiAfterClick(appPage);
  }

  return { finalUrl };
}

function markPass(report: Record<StepKey, StepResult>, step: StepKey, details?: string): void {
  report[step] = { status: 'PASS', details };
}

function markFail(report: Record<StepKey, StepResult>, step: StepKey, error: unknown): void {
  const message = error instanceof Error ? error.message : String(error);
  report[step] = { status: 'FAIL', details: message };
}

async function writeFinalReport(
  testInfo: TestInfo,
  report: Record<StepKey, StepResult>,
  tcUrl: string,
  ppUrl: string,
): Promise<void> {
  const lines: string[] = [];
  lines.push('# SaleADS Mi Negocio Full Test Report');
  lines.push('');
  lines.push('## Result Matrix');
  lines.push('');
  lines.push('| Validation Step | Status | Details |');
  lines.push('|---|---|---|');

  STEP_ORDER.forEach((key) => {
    const entry = report[key];
    lines.push(`| ${key} | ${entry.status} | ${entry.details ?? ''} |`);
  });

  lines.push('');
  lines.push('## Captured URLs');
  lines.push('');
  lines.push(`- Términos y Condiciones final URL: ${tcUrl || 'N/A'}`);
  lines.push(`- Política de Privacidad final URL: ${ppUrl || 'N/A'}`);

  const reportText = lines.join('\n');
  const reportPath = testInfo.outputPath('saleads-mi-negocio-report.md');
  await fs.writeFile(reportPath, reportText, 'utf-8');
  await testInfo.attach('saleads-mi-negocio-report', { path: reportPath, contentType: 'text/markdown' });
}

test.describe('SaleADS Mi Negocio workflow', () => {
  test('saleads_mi_negocio_full_test', async ({ page, context }, testInfo) => {
    const report = createReport();
    let appPage = page;
    let termsAndConditionsUrl = '';
    let privacyPolicyUrl = '';
    let workflowBlocked = false;
    const failedSteps: StepKey[] = [];

    const runStep = async (
      step: StepKey,
      action: () => Promise<void>,
      options?: { blockedByCriticalFailure?: boolean; criticalOnFailure?: boolean },
    ): Promise<void> => {
      if (options?.blockedByCriticalFailure && workflowBlocked) {
        markFail(report, step, new Error('Skipped due to a previous critical step failure.'));
        failedSteps.push(step);
        return;
      }

      try {
        await action();
      } catch (error) {
        markFail(report, step, error);
        failedSteps.push(step);
        if (options?.criticalOnFailure) {
          workflowBlocked = true;
        }
      }
    };

    const appLoginUrl = process.env.SALEADS_URL?.trim();
    if (appLoginUrl) {
      await appPage.goto(appLoginUrl, { waitUntil: 'domcontentloaded' });
      await appPage.waitForTimeout(1_000);
    }

    await appPage.waitForLoadState('domcontentloaded');

    try {
      await runStep(
        'Login',
        async () => {
          const googleLoginVisible = await appPage
            .getByText(/Sign in with Google|Iniciar sesión con Google|Continuar con Google/i)
            .first()
            .isVisible()
            .catch(() => false);

          if (googleLoginVisible) {
            await clickByVisibleText(appPage, ['Sign in with Google', 'Iniciar sesión con Google', 'Continuar con Google']);

            const googleAuthPage =
              context
                .pages()
                .find((openPage) => normalizeVisibleText(openPage.url()).includes('accounts.google.com')) ?? appPage;
            await chooseGoogleAccountIfPrompted(googleAuthPage);
          }

          appPage = await findAppPage(context, appPage);
          await assertAnyVisibleText(appPage, ['Negocio', 'Mi Negocio', 'Dashboard']);
          await expect(appPage.locator('aside').first()).toBeVisible();
          await takeCheckpoint(appPage, testInfo, '01-dashboard-loaded.png');
          markPass(report, 'Login', 'Dashboard and left sidebar visible after login.');
        },
        { criticalOnFailure: true },
      );

      await runStep(
        'Mi Negocio menu',
        async () => {
          await clickByVisibleText(appPage, ['Mi Negocio', 'Negocio']);
          await assertAnyVisibleText(appPage, ['Agregar Negocio']);
          await assertAnyVisibleText(appPage, ['Administrar Negocios']);
          await takeCheckpoint(appPage, testInfo, '02-mi-negocio-menu-expanded.png');
          markPass(report, 'Mi Negocio menu', 'Mi Negocio submenu expanded with expected entries.');
        },
        { blockedByCriticalFailure: true, criticalOnFailure: true },
      );

      await runStep(
        'Agregar Negocio modal',
        async () => {
          await clickByVisibleText(appPage, ['Agregar Negocio']);
          await assertAnyVisibleText(appPage, ['Crear Nuevo Negocio']);
          await assertAnyVisibleText(appPage, ['Nombre del Negocio']);
          await assertAnyVisibleText(appPage, ['Tienes 2 de 3 negocios']);
          await assertAnyVisibleText(appPage, ['Cancelar']);
          await assertAnyVisibleText(appPage, ['Crear Negocio']);

          const candidateFields: Locator[] = [
            appPage.getByLabel('Nombre del Negocio', { exact: false }),
            appPage.getByPlaceholder('Nombre del Negocio'),
            appPage.locator('input[name*="nombre" i], input[id*="nombre" i]'),
          ];

          for (const field of candidateFields) {
            if (await field.first().isVisible().catch(() => false)) {
              await field.first().fill('Negocio Prueba Automatización');
              break;
            }
          }

          await takeCheckpoint(appPage, testInfo, '03-agregar-negocio-modal.png');
          await clickByVisibleText(appPage, ['Cancelar']);
          markPass(report, 'Agregar Negocio modal', 'Modal fields and controls validated successfully.');
        },
        { blockedByCriticalFailure: true },
      );

      await runStep(
        'Administrar Negocios view',
        async () => {
          if (!(await appPage.getByText('Administrar Negocios', { exact: false }).first().isVisible().catch(() => false))) {
            await clickByVisibleText(appPage, ['Mi Negocio', 'Negocio']);
          }

          await clickByVisibleText(appPage, ['Administrar Negocios']);
          await assertAnyVisibleText(appPage, ['Información General']);
          await assertAnyVisibleText(appPage, ['Detalles de la Cuenta']);
          await assertAnyVisibleText(appPage, ['Tus Negocios']);
          await assertAnyVisibleText(appPage, ['Sección Legal']);
          await takeCheckpoint(appPage, testInfo, '04-administrar-negocios.png', true);
          markPass(report, 'Administrar Negocios view', 'Account management sections are visible.');
        },
        { blockedByCriticalFailure: true, criticalOnFailure: true },
      );

      await runStep(
        'Información General',
        async () => {
          await assertAnyVisibleText(appPage, ['BUSINESS PLAN']);
          await assertAnyVisibleText(appPage, ['Cambiar Plan']);
          await assertAnyVisibleText(appPage, ['Nombre', 'Usuario']);
          await expect(appPage.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/).first()).toBeVisible();
          markPass(report, 'Información General', 'Plan, user name, email and change plan action are visible.');
        },
        { blockedByCriticalFailure: true },
      );

      await runStep(
        'Detalles de la Cuenta',
        async () => {
          await assertAnyVisibleText(appPage, ['Cuenta creada']);
          await assertAnyVisibleText(appPage, ['Estado activo', 'Estado Activo']);
          await assertAnyVisibleText(appPage, ['Idioma seleccionado']);
          markPass(report, 'Detalles de la Cuenta', 'Expected account details labels are visible.');
        },
        { blockedByCriticalFailure: true },
      );

      await runStep(
        'Tus Negocios',
        async () => {
          await assertAnyVisibleText(appPage, ['Tus Negocios']);
          await assertAnyVisibleText(appPage, ['Agregar Negocio']);
          await assertAnyVisibleText(appPage, ['Tienes 2 de 3 negocios']);
          markPass(report, 'Tus Negocios', 'Business list and plan usage text are visible.');
        },
        { blockedByCriticalFailure: true },
      );

      await runStep(
        'Términos y Condiciones',
        async () => {
          const tcResult = await validateLegalPage(
            context,
            appPage,
            testInfo,
            ['Términos y Condiciones'],
            ['Términos y Condiciones'],
            '08-terminos-y-condiciones.png',
          );
          termsAndConditionsUrl = tcResult.finalUrl;
          markPass(report, 'Términos y Condiciones', `Validated legal page. URL: ${termsAndConditionsUrl}`);
        },
        { blockedByCriticalFailure: true },
      );

      await runStep(
        'Política de Privacidad',
        async () => {
          const ppResult = await validateLegalPage(
            context,
            appPage,
            testInfo,
            ['Política de Privacidad'],
            ['Política de Privacidad'],
            '09-politica-de-privacidad.png',
          );
          privacyPolicyUrl = ppResult.finalUrl;
          markPass(report, 'Política de Privacidad', `Validated legal page. URL: ${privacyPolicyUrl}`);
        },
        { blockedByCriticalFailure: true },
      );
    } finally {
      await writeFinalReport(testInfo, report, termsAndConditionsUrl, privacyPolicyUrl);
    }

    expect(failedSteps, `Failed validation steps: ${failedSteps.join(', ')}`).toEqual([]);
  });
});
