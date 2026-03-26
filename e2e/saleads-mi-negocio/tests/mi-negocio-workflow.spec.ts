import { BrowserContext, expect, Locator, Page, test } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

type StepStatus = 'PASS' | 'FAIL';

type WorkflowReport = {
  Login: StepStatus;
  'Mi Negocio menu': StepStatus;
  'Agregar Negocio modal': StepStatus;
  'Administrar Negocios view': StepStatus;
  'Información General': StepStatus;
  'Detalles de la Cuenta': StepStatus;
  'Tus Negocios': StepStatus;
  'Términos y Condiciones': StepStatus;
  'Política de Privacidad': StepStatus;
};

const defaultReport: WorkflowReport = {
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

const screenshotsDir = process.env.SCREENSHOTS_DIR || path.join('artifacts', 'screenshots');
const reportFile = process.env.REPORT_FILE || path.join('artifacts', 'reports', 'final-report.json');
const appUrl = process.env.SALEADS_URL || process.env.SALEADS_BASE_URL;
const expectedGoogleAccount =
  process.env.GOOGLE_ACCOUNT_EMAIL || process.env.GOOGLE_ACCOUNT || 'juanlucasbarbiergarzon@gmail.com';

function envOrThrow(name: string, value: string | undefined): string {
  if (!value || !value.trim()) {
    throw new Error(
      `Missing required environment variable ${name}. ` +
        `Set ${name} to the current environment login URL before running the test.`,
    );
  }
  return value;
}

async function waitUi(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(600);
}

async function safeScreenshot(page: Page, fileName: string, fullPage = false): Promise<void> {
  await fs.mkdir(screenshotsDir, { recursive: true });
  await page.screenshot({
    path: path.join(screenshotsDir, fileName),
    fullPage,
  });
}

function escapeRegexLiteral(input: string): string {
  return input.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

async function firstVisible(locators: Locator[]): Promise<Locator | null> {
  for (const locator of locators) {
    const first = locator.first();
    if (await first.isVisible().catch(() => false)) {
      return first;
    }
  }
  return null;
}

async function clickFirstVisibleByText(page: Page, candidates: RegExp[]): Promise<void> {
  for (const re of candidates) {
    const el = await firstVisible([
      page.getByRole('button', { name: re }),
      page.getByRole('link', { name: re }),
      page.getByRole('menuitem', { name: re }),
      page.getByRole('tab', { name: re }),
    ]);
    if (el) {
      await el.click();
      await waitUi(page);
      return;
    }
  }

  for (const re of candidates) {
    const el = await firstVisible([page.getByText(re)]);
    if (el) {
      await el.click();
      await waitUi(page);
      return;
    }
  }

  throw new Error(`Could not find visible element by text among candidates: ${candidates.map(String).join(', ')}`);
}

async function maybeSelectGoogleAccount(page: Page): Promise<void> {
  const accountPattern = new RegExp(escapeRegexLiteral(expectedGoogleAccount), 'i');
  const accountChip = await firstVisible([
    page.getByText(accountPattern),
    page.getByRole('button', { name: accountPattern }),
    page.getByRole('link', { name: accountPattern }),
    page.getByRole('option', { name: accountPattern }),
  ]);
  if (accountChip) {
    await accountChip.click();
    await waitUi(page);
  }
}

async function writeReport(report: WorkflowReport, legalUrls: { terms?: string; privacy?: string }): Promise<void> {
  await fs.mkdir(path.dirname(reportFile), { recursive: true });
  await fs.writeFile(
    reportFile,
    JSON.stringify(
      {
        generatedAt: new Date().toISOString(),
        results: report,
        legalUrls,
      },
      null,
      2,
    ),
    'utf8',
  );
}

async function assertAnyVisible(page: Page, texts: RegExp[]): Promise<void> {
  for (const text of texts) {
    const candidate = page.getByText(text).first();
    if (await candidate.isVisible().catch(() => false)) {
      return;
    }
  }
  throw new Error(`None of these texts were visible: ${texts.map(String).join(', ')}`);
}

async function clickAndResolveTargetPage(
  page: Page,
  context: BrowserContext,
  candidates: RegExp[],
): Promise<Page> {
  const popupPromise = context.waitForEvent('page', { timeout: 7000 }).catch(() => null);
  await clickFirstVisibleByText(page, candidates);
  const popup = await popupPromise;
  const target = popup || page;
  await target.waitForLoadState('domcontentloaded');
  await waitUi(target);
  return target;
}

async function getBusinessNameInput(page: Page): Promise<Locator> {
  const field = await firstVisible([
    page.getByLabel(/nombre del negocio/i),
    page.getByPlaceholder(/nombre del negocio/i),
    page.getByRole('textbox', { name: /nombre del negocio/i }),
  ]);
  if (!field) {
    throw new Error('Could not find input field "Nombre del Negocio".');
  }
  return field;
}

test.describe('SaleADS - Mi Negocio full workflow', () => {
  test('logs in with Google and validates complete module flow', async ({ page, context }) => {
    const report: WorkflowReport = { ...defaultReport };
    const legalUrls: { terms?: string; privacy?: string } = {};
    try {
      const targetUrl = envOrThrow('SALEADS_URL / SALEADS_BASE_URL', appUrl);
      await page.goto(targetUrl, { waitUntil: 'domcontentloaded' });
      await waitUi(page);

      // Step 1: Login with Google
      const loginPopupPromise = context.waitForEvent('page', { timeout: 7000 }).catch(() => null);
      await clickFirstVisibleByText(page, [/sign in with google/i, /iniciar.*google/i, /google/i]);
      const googleAuthPage = (await loginPopupPromise) || page;
      await maybeSelectGoogleAccount(googleAuthPage);
      if (googleAuthPage !== page) {
        await googleAuthPage.waitForClose({ timeout: 15000 }).catch(() => {});
      }
      await page.bringToFront();
      await waitUi(page);

      await expect(page.getByRole('navigation').first()).toBeVisible({ timeout: 30000 });
      await assertAnyVisible(page, [/negocio/i, /mi negocio/i]);
      await safeScreenshot(page, '01-dashboard-loaded.png', true);
      report.Login = 'PASS';

      // Step 2: Open Mi Negocio menu
      await clickFirstVisibleByText(page, [/mi negocio/i, /negocio/i]);
      await waitUi(page);
      if (!(await page.getByText(/agregar negocio/i).first().isVisible().catch(() => false))) {
        await clickFirstVisibleByText(page, [/mi negocio/i]);
        await waitUi(page);
      }
      await expect(page.getByText(/agregar negocio/i)).toBeVisible();
      await expect(page.getByText(/administrar negocios/i)).toBeVisible();
      await safeScreenshot(page, '02-mi-negocio-expanded.png');
      report['Mi Negocio menu'] = 'PASS';

      // Step 3: Validate Agregar Negocio modal
      await clickFirstVisibleByText(page, [/agregar negocio/i]);
      await waitUi(page);

      await expect(page.getByText(/crear nuevo negocio/i)).toBeVisible();
      await expect(await getBusinessNameInput(page)).toBeVisible();
      await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(page.getByRole('button', { name: /cancelar/i })).toBeVisible();
      await expect(page.getByRole('button', { name: /crear negocio/i })).toBeVisible();

      const businessNameInput = await getBusinessNameInput(page);
      await businessNameInput.click();
      await waitUi(page);
      await businessNameInput.fill('Negocio Prueba Automatizacion');
      await waitUi(page);

      await safeScreenshot(page, '03-agregar-negocio-modal.png');
      await page.getByRole('button', { name: /cancelar/i }).click();
      await waitUi(page);
      report['Agregar Negocio modal'] = 'PASS';

      // Step 4: Open Administrar Negocios
      if (!(await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false))) {
        await clickFirstVisibleByText(page, [/mi negocio/i]);
        await waitUi(page);
      }
      await clickFirstVisibleByText(page, [/administrar negocios/i]);
      await waitUi(page);

      await expect(page.getByText(/información general|informacion general/i)).toBeVisible();
      await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
      await expect(page.getByText(/tus negocios/i)).toBeVisible();
      await expect(page.getByText(/sección legal|seccion legal/i)).toBeVisible();
      await safeScreenshot(page, '04-administrar-negocios-page.png', true);
      report['Administrar Negocios view'] = 'PASS';

      // Step 5: Validate Información General
      await expect(page.getByText(/business plan/i)).toBeVisible();
      await expect(page.getByRole('button', { name: /cambiar plan/i })).toBeVisible();
      await assertAnyVisible(page, [/@/, /correo/i, /email/i]);
      report['Información General'] = 'PASS';

      // Step 6: Validate Detalles de la Cuenta
      await expect(page.getByText(/cuenta creada/i)).toBeVisible();
      await expect(page.getByText(/estado activo/i)).toBeVisible();
      await expect(page.getByText(/idioma seleccionado/i)).toBeVisible();
      report['Detalles de la Cuenta'] = 'PASS';

      // Step 7: Validate Tus Negocios
      await expect(page.getByText(/tus negocios/i)).toBeVisible();
      await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(page.getByRole('button', { name: /agregar negocio/i })).toBeVisible();
      report['Tus Negocios'] = 'PASS';

      // Step 8: Validate Términos y Condiciones
      const termsTarget = await clickAndResolveTargetPage(page, context, [/términos y condiciones/i, /terminos y condiciones/i]);
      await expect(termsTarget.getByText(/términos y condiciones|terminos y condiciones/i)).toBeVisible();
      await assertAnyVisible(termsTarget, [/legal/i, /condiciones/i, /términos|terminos/i]);
      legalUrls.terms = termsTarget.url();
      await safeScreenshot(termsTarget, '05-terminos-y-condiciones.png', true);
      report['Términos y Condiciones'] = 'PASS';
      if (termsTarget !== page) {
        await termsTarget.close();
        await page.bringToFront();
        await waitUi(page);
      }

      // Step 9: Validate Política de Privacidad
      const privacyTarget = await clickAndResolveTargetPage(page, context, [/política de privacidad/i, /politica de privacidad/i]);
      await expect(privacyTarget.getByText(/política de privacidad|politica de privacidad/i)).toBeVisible();
      await assertAnyVisible(privacyTarget, [/privacidad/i, /datos/i, /legal/i]);
      legalUrls.privacy = privacyTarget.url();
      await safeScreenshot(privacyTarget, '06-politica-de-privacidad.png', true);
      report['Política de Privacidad'] = 'PASS';
      if (privacyTarget !== page) {
        await privacyTarget.close();
        await page.bringToFront();
        await waitUi(page);
      }
    } catch (error) {
      await safeScreenshot(page, '99-failure-state.png', true).catch(() => {});
      throw error;
    } finally {
      await writeReport(report, legalUrls);
      console.log(`Final workflow report written to: ${reportFile}`);
      console.log(JSON.stringify(report, null, 2));
    }
  });
});
