import { expect, test, type BrowserContext, type Locator, type Page } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

type RequiredReportField =
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
  details: string[];
};

const REPORT_FIELDS: RequiredReportField[] = [
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

const SCREENSHOT_DIR = path.resolve(process.cwd(), 'test-results', 'saleads-mi-negocio');
const REPORT_FILE = path.join(SCREENSHOT_DIR, 'final-report.json');
const PREREQ_PREFIX = 'Prerequisite failed:';

function initReport(): Record<RequiredReportField, StepResult> {
  return REPORT_FIELDS.reduce(
    (acc, key) => {
      acc[key] = { status: 'FAIL', details: [] };
      return acc;
    },
    {} as Record<RequiredReportField, StepResult>,
  );
}

async function ensureVisible(locators: Locator[], timeout = 20000): Promise<Locator> {
  for (const locator of locators) {
    if (await locator.first().isVisible({ timeout }).catch(() => false)) {
      return locator.first();
    }
  }
  throw new Error('No visible locator candidate found.');
}

async function waitForUiIdle(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(700);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.scrollIntoViewIfNeeded();
  await locator.click();
  await waitForUiIdle(page);
}

async function openGoogleLogin(page: Page): Promise<Page> {
  const candidates = [
    page.getByRole('button', { name: /sign in with google/i }),
    page.getByRole('button', { name: /iniciar sesi[oó]n con google/i }),
    page.getByText(/sign in with google/i),
    page.getByText(/iniciar sesi[oó]n con google/i),
    page.locator('button:has-text("Google")'),
    page.locator('[aria-label*="Google"]'),
  ];

  const loginButton = await ensureVisible(candidates, 25000);
  const popupPromise = page.waitForEvent('popup', { timeout: 7000 }).catch(() => null);
  await clickAndWait(page, loginButton);
  const popup = await popupPromise;
  const targetPage = popup ?? page;
  await waitForUiIdle(targetPage);

  return targetPage;
}

async function selectGoogleAccountIfPrompted(page: Page): Promise<void> {
  const email = 'juanlucasbarbiergarzon@gmail.com';
  const accountOption = page.getByText(email, { exact: true });

  if (await accountOption.isVisible({ timeout: 15000 }).catch(() => false)) {
    await clickAndWait(page, accountOption);
  }
}

async function expandMiNegocioMenu(page: Page): Promise<void> {
  const miNegocioOption = await ensureVisible(
    [
      page.getByRole('link', { name: /mi negocio/i }),
      page.getByRole('button', { name: /mi negocio/i }),
      page.getByText(/^mi negocio$/i),
      page.getByText(/mi negocio/i),
    ],
    25000,
  );
  await clickAndWait(page, miNegocioOption);
}

function markPrerequisiteFailures(
  report: Record<RequiredReportField, StepResult>,
  fields: RequiredReportField[],
  reason: string,
): void {
  for (const field of fields) {
    if (report[field].status === 'PASS') {
      continue;
    }
    report[field] = {
      status: 'FAIL',
      details: [`${PREREQ_PREFIX} ${reason}`],
    };
  }
}

async function validateLegalLink(
  context: BrowserContext,
  appPage: Page,
  linkLabel: string,
  headingPattern: RegExp,
  screenshotName: string,
): Promise<{ finalUrl: string }> {
  const link = await ensureVisible(
    [
      appPage.getByRole('link', { name: new RegExp(linkLabel, 'i') }),
      appPage.getByRole('button', { name: new RegExp(linkLabel, 'i') }),
      appPage.getByText(new RegExp(linkLabel, 'i')),
    ],
    20000,
  );

  const popupPromise = appPage.waitForEvent('popup', { timeout: 7000 }).catch(() => null);
  await clickAndWait(appPage, link);
  const popup = await popupPromise;
  const targetPage = popup ?? appPage;

  await targetPage.waitForLoadState('domcontentloaded');
  const heading = await ensureVisible(
    [targetPage.getByRole('heading', { name: headingPattern }), targetPage.getByText(headingPattern)],
    30000,
  );
  await expect(heading).toBeVisible({ timeout: 30000 });

  const legalKeywords = /(t[eé]rminos|condiciones|privacidad|datos personales|aceptaci[oó]n|usuario)/i;
  await expect(targetPage.getByText(legalKeywords)).toBeVisible({ timeout: 30000 });

  await targetPage.screenshot({ path: path.join(SCREENSHOT_DIR, screenshotName), fullPage: true });
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUiIdle(appPage);
  } else {
    await appPage.goBack({ waitUntil: 'domcontentloaded' }).catch(() => Promise.resolve(null));
    await waitForUiIdle(appPage);
  }

  // Ensure we are back on the app tab.
  const appTabs = context.pages();
  const frontTab = appTabs.find((tab) => tab.url() === appPage.url()) ?? appPage;
  await frontTab.bringToFront();

  return { finalUrl };
}

test('saleads_mi_negocio_full_test', async ({ page, context }) => {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
  const report = initReport();
  const legalUrls: Record<string, string> = {};

  const loginUrl = process.env.SALEADS_LOGIN_URL;
  let canContinue = true;
  let loginReady = true;
  let menuReady = true;
  let accountPageReady = true;

  try {
    await test.step('Step 1: Login with Google', async () => {
      try {
        if (loginUrl) {
          await page.goto(loginUrl, { waitUntil: 'domcontentloaded' });
          await waitForUiIdle(page);
        } else if (page.url() === 'about:blank') {
          throw new Error(
            'No URL available. Set SALEADS_LOGIN_URL or pre-open SaleADS login page before test start.',
          );
        }

        const googlePage = await openGoogleLogin(page);
        await selectGoogleAccountIfPrompted(googlePage);

        if (googlePage !== page) {
          await googlePage.waitForTimeout(1200);
          if (!googlePage.isClosed()) {
            await page.bringToFront();
          }
        }

        const sidebar = await ensureVisible(
          [page.locator('aside'), page.getByRole('navigation'), page.getByText(/negocio/i)],
          45000,
        );
        await expect(sidebar).toBeVisible({ timeout: 25000 });

        await page.screenshot({ path: path.join(SCREENSHOT_DIR, '01-dashboard-loaded.png'), fullPage: true });
        report.Login = {
          status: 'PASS',
          details: ['Main interface and left sidebar are visible after Google login.'],
        };
      } catch (error) {
        canContinue = false;
        loginReady = false;
        menuReady = false;
        accountPageReady = false;
        report.Login = { status: 'FAIL', details: [`Login workflow failed: ${String(error)}`] };
      }
    });

    await test.step('Step 2: Open Mi Negocio menu', async () => {
      if (!loginReady) {
        markPrerequisiteFailures(report, ['Mi Negocio menu'], 'Login step did not complete successfully.');
        menuReady = false;
        return;
      }

      try {
        await ensureVisible(
          [page.getByText(/^negocio$/i), page.getByRole('link', { name: /negocio/i }), page.getByRole('button', { name: /negocio/i })],
          20000,
        );

        await expandMiNegocioMenu(page);
        await expect(page.getByText(/agregar negocio/i)).toBeVisible({ timeout: 20000 });
        await expect(page.getByText(/administrar negocios/i)).toBeVisible({ timeout: 20000 });

        await page.screenshot({ path: path.join(SCREENSHOT_DIR, '02-mi-negocio-expanded.png'), fullPage: true });
        report['Mi Negocio menu'] = {
          status: 'PASS',
          details: ['Mi Negocio expanded and submenu options are visible.'],
        };
      } catch (error) {
        menuReady = false;
        accountPageReady = false;
        report['Mi Negocio menu'] = { status: 'FAIL', details: [`Menu validation failed: ${String(error)}`] };
      }
    });

    await test.step('Step 3: Validate Agregar Negocio modal', async () => {
      if (!menuReady) {
        markPrerequisiteFailures(report, ['Agregar Negocio modal'], 'Mi Negocio menu step did not complete successfully.');
        return;
      }

      try {
        const agregarNegocio = await ensureVisible([
          page.getByRole('link', { name: /agregar negocio/i }),
          page.getByRole('button', { name: /agregar negocio/i }),
          page.getByText(/^agregar negocio$/i),
        ]);
        await clickAndWait(page, agregarNegocio);

        await expect(page.getByText(/crear nuevo negocio/i)).toBeVisible({ timeout: 20000 });
        const businessInput = await ensureVisible([
          page.getByLabel(/nombre del negocio/i),
          page.getByPlaceholder(/nombre del negocio/i),
        ]);
        await expect(businessInput).toBeVisible({ timeout: 20000 });
        await expect(page.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible({ timeout: 20000 });
        await expect(page.getByRole('button', { name: /cancelar/i })).toBeVisible({ timeout: 20000 });
        await expect(page.getByRole('button', { name: /crear negocio/i })).toBeVisible({ timeout: 20000 });

        await businessInput.fill('Negocio Prueba Automatización');
        await page.screenshot({ path: path.join(SCREENSHOT_DIR, '03-agregar-negocio-modal.png'), fullPage: true });
        await clickAndWait(page, page.getByRole('button', { name: /cancelar/i }));

        report['Agregar Negocio modal'] = {
          status: 'PASS',
          details: ['Crear Nuevo Negocio modal fields and actions validated.'],
        };
      } catch (error) {
        report['Agregar Negocio modal'] = { status: 'FAIL', details: [`Modal validation failed: ${String(error)}`] };
      }
    });

    await test.step('Step 4: Open Administrar Negocios', async () => {
      if (!menuReady) {
        markPrerequisiteFailures(
          report,
          ['Administrar Negocios view'],
          'Mi Negocio menu step did not complete successfully.',
        );
        accountPageReady = false;
        return;
      }

      try {
        if (!(await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false))) {
          await expandMiNegocioMenu(page);
        }

        const administrarNegocios = await ensureVisible([
          page.getByRole('link', { name: /administrar negocios/i }),
          page.getByRole('button', { name: /administrar negocios/i }),
          page.getByText(/administrar negocios/i),
        ]);
        await clickAndWait(page, administrarNegocios);

        await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible({ timeout: 30000 });
        await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible({ timeout: 30000 });
        await expect(page.getByText(/tus negocios/i)).toBeVisible({ timeout: 30000 });
        await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible({ timeout: 30000 });

        await page.screenshot({ path: path.join(SCREENSHOT_DIR, '04-administrar-negocios-page.png'), fullPage: true });
        report['Administrar Negocios view'] = {
          status: 'PASS',
          details: ['Account/administration view sections are visible.'],
        };
      } catch (error) {
        accountPageReady = false;
        report['Administrar Negocios view'] = {
          status: 'FAIL',
          details: [`Administrar Negocios page validation failed: ${String(error)}`],
        };
      }
    });

    await test.step('Step 5: Validate Información General', async () => {
      if (!accountPageReady) {
        markPrerequisiteFailures(
          report,
          ['Información General'],
          'Administrar Negocios view did not load successfully.',
        );
        return;
      }

      try {
        const infoSection = page.locator('section, div').filter({ hasText: /informaci[oó]n general/i }).first();
        await expect(infoSection).toBeVisible({ timeout: 20000 });
        await expect(infoSection.getByText(/@/)).toBeVisible({ timeout: 20000 });
        await expect(infoSection.getByText(/business plan/i)).toBeVisible({ timeout: 20000 });
        await expect(infoSection.getByRole('button', { name: /cambiar plan/i })).toBeVisible({ timeout: 20000 });

        const potentialName = infoSection
          .locator('h1, h2, h3, p, span, strong')
          .filter({ hasNotText: /informaci[oó]n general|business plan|cambiar plan|@/i })
          .first();
        await expect(potentialName).toBeVisible({ timeout: 20000 });

        report['Información General'] = {
          status: 'PASS',
          details: ['Name, email, BUSINESS PLAN, and Cambiar Plan are visible.'],
        };
      } catch (error) {
        report['Información General'] = {
          status: 'FAIL',
          details: [`Información General validation failed: ${String(error)}`],
        };
      }
    });

    await test.step('Step 6: Validate Detalles de la Cuenta', async () => {
      if (!accountPageReady) {
        markPrerequisiteFailures(
          report,
          ['Detalles de la Cuenta'],
          'Administrar Negocios view did not load successfully.',
        );
        return;
      }

      try {
        const detailsSection = page.locator('section, div').filter({ hasText: /detalles de la cuenta/i }).first();
        await expect(detailsSection).toBeVisible({ timeout: 20000 });
        await expect(detailsSection.getByText(/cuenta creada/i)).toBeVisible({ timeout: 20000 });
        await expect(detailsSection.getByText(/estado activo/i)).toBeVisible({ timeout: 20000 });
        await expect(detailsSection.getByText(/idioma seleccionado/i)).toBeVisible({ timeout: 20000 });

        report['Detalles de la Cuenta'] = {
          status: 'PASS',
          details: ['Cuenta creada, Estado activo, and Idioma seleccionado are visible.'],
        };
      } catch (error) {
        report['Detalles de la Cuenta'] = {
          status: 'FAIL',
          details: [`Detalles de la Cuenta validation failed: ${String(error)}`],
        };
      }
    });

    await test.step('Step 7: Validate Tus Negocios', async () => {
      if (!accountPageReady) {
        markPrerequisiteFailures(report, ['Tus Negocios'], 'Administrar Negocios view did not load successfully.');
        return;
      }

      try {
        const businessSection = page.locator('section, div').filter({ hasText: /tus negocios/i }).first();
        await expect(businessSection).toBeVisible({ timeout: 20000 });
        const addBusinessButton = await ensureVisible([
          businessSection.getByRole('button', { name: /agregar negocio/i }),
          businessSection.getByRole('link', { name: /agregar negocio/i }),
          businessSection.getByText(/agregar negocio/i),
        ]);
        await expect(addBusinessButton).toBeVisible({ timeout: 20000 });
        await expect(businessSection.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible({ timeout: 20000 });

        const businessRows = businessSection.locator('li, tr, [role="row"], [data-testid*="business"]');
        const businessCount = await businessRows.count();
        expect(businessCount).toBeGreaterThanOrEqual(1);

        report['Tus Negocios'] = {
          status: 'PASS',
          details: ['Business list, Agregar Negocio button, and quota text validated.'],
        };
      } catch (error) {
        report['Tus Negocios'] = {
          status: 'FAIL',
          details: [`Tus Negocios validation failed: ${String(error)}`],
        };
      }
    });

    await test.step('Step 8: Validate Términos y Condiciones', async () => {
      if (!accountPageReady) {
        markPrerequisiteFailures(
          report,
          ['Términos y Condiciones'],
          'Administrar Negocios view did not load successfully.',
        );
        return;
      }

      try {
        const result = await validateLegalLink(
          context,
          page,
          'Términos y Condiciones',
          /t[eé]rminos y condiciones/i,
          '05-terminos-y-condiciones.png',
        );
        legalUrls['Términos y Condiciones'] = result.finalUrl;
        report['Términos y Condiciones'] = {
          status: 'PASS',
          details: [`Legal page opened successfully. Final URL: ${result.finalUrl}`],
        };
      } catch (error) {
        report['Términos y Condiciones'] = {
          status: 'FAIL',
          details: [`Términos y Condiciones validation failed: ${String(error)}`],
        };
      }
    });

    await test.step('Step 9: Validate Política de Privacidad', async () => {
      if (!accountPageReady) {
        markPrerequisiteFailures(
          report,
          ['Política de Privacidad'],
          'Administrar Negocios view did not load successfully.',
        );
        return;
      }

      try {
        const result = await validateLegalLink(
          context,
          page,
          'Política de Privacidad',
          /pol[ií]tica de privacidad/i,
          '06-politica-de-privacidad.png',
        );
        legalUrls['Política de Privacidad'] = result.finalUrl;
        report['Política de Privacidad'] = {
          status: 'PASS',
          details: [`Legal page opened successfully. Final URL: ${result.finalUrl}`],
        };
      } catch (error) {
        report['Política de Privacidad'] = {
          status: 'FAIL',
          details: [`Política de Privacidad validation failed: ${String(error)}`],
        };
      }
    });
  } finally {
    if (!canContinue) {
      markPrerequisiteFailures(
        report,
        [
          'Mi Negocio menu',
          'Agregar Negocio modal',
          'Administrar Negocios view',
          'Información General',
          'Detalles de la Cuenta',
          'Tus Negocios',
          'Términos y Condiciones',
          'Política de Privacidad',
        ],
        'Login step did not complete successfully.',
      );
    }
    fs.writeFileSync(REPORT_FILE, JSON.stringify({ results: report, legalUrls }, null, 2), 'utf-8');
  }

  const failedSteps = Object.entries(report).filter(([, result]) => result.status === 'FAIL');
  if (failedSteps.length > 0) {
    const summary = failedSteps.map(([name, result]) => `${name}: ${result.details.join(' | ')}`).join('\n');
    throw new Error(`Final report contains failed validations:\n${summary}`);
  }
});
