import { mkdir } from 'node:fs/promises';
import { test, expect, type BrowserContext, type Locator, type Page, type TestInfo } from '@playwright/test';

type Status = 'PASS' | 'FAIL';

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

type ReportRow = {
  status: Status;
  details: string;
};

const REPORT_FIELDS: ReportField[] = [
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

const GOOGLE_ACCOUNT_EMAIL = 'juanlucasbarbiergarzon@gmail.com';

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

async function waitForUiLoad(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForLoadState('networkidle', { timeout: 10_000 }).catch(() => undefined);
  await page.waitForTimeout(500);
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await locator.click();
  await waitForUiLoad(page);
}

async function firstVisible(locators: Locator[], timeout = 20_000): Promise<Locator> {
  const deadline = Date.now() + timeout;

  while (Date.now() < deadline) {
    for (const locator of locators) {
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }

    await new Promise((resolve) => {
      setTimeout(resolve, 250);
    });
  }

  throw new Error('No expected visible locator found.');
}

async function ensureScreenshotDir(testInfo: TestInfo): Promise<string> {
  const folder = `${testInfo.outputDir}/screenshots`;
  await mkdir(folder, { recursive: true });
  return folder;
}

async function captureCheckpoint(page: Page, screenshotDir: string, name: string): Promise<void> {
  await page.screenshot({
    path: `${screenshotDir}/${name}.png`,
    fullPage: true
  });
}

async function navigateToLoginIfConfigured(page: Page): Promise<void> {
  const baseUrl = process.env.SALEADS_BASE_URL?.trim();

  if (!baseUrl) {
    return;
  }

  // URL is environment-driven so the same test can run in dev/staging/prod.
  await page.goto(baseUrl, { waitUntil: 'domcontentloaded' });
  await waitForUiLoad(page);
}

async function clickGoogleSignIn(page: Page, context: BrowserContext): Promise<Page> {
  const loginCandidates = [
    page.getByRole('button', { name: /sign in with google/i }).first(),
    page.getByRole('button', { name: /iniciar sesión con google/i }).first(),
    page.getByRole('button', { name: /continuar con google/i }).first(),
    page.getByRole('link', { name: /google/i }).first(),
    page.getByText(/sign in with google|iniciar sesión con google|continuar con google/i).first()
  ];

  const loginButton = await firstVisible(loginCandidates);

  const popupPromise = context.waitForEvent('page', { timeout: 12_000 }).catch(() => null);
  await clickAndWait(loginButton, page);
  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState('domcontentloaded');
    await waitForUiLoad(popup);
    return popup;
  }

  return page;
}

async function selectGoogleAccountIfPrompted(googlePage: Page): Promise<void> {
  const accountLocator = googlePage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
  if (await accountLocator.isVisible().catch(() => false)) {
    await clickAndWait(accountLocator, googlePage);
  }
}

async function openMiNegocioMenu(page: Page): Promise<void> {
  const negocioSection = await firstVisible([
    page.getByRole('button', { name: /^Negocio$/i }).first(),
    page.getByText(/^Negocio$/i).first()
  ]);
  await clickAndWait(negocioSection, page);

  const miNegocioOption = await firstVisible([
    page.getByRole('button', { name: /^Mi Negocio$/i }).first(),
    page.getByText(/^Mi Negocio$/i).first()
  ]);
  await clickAndWait(miNegocioOption, page);
}

async function openLegalDocumentAndReturn(
  page: Page,
  context: BrowserContext,
  linkText: string,
  headingText: string,
  screenshotDir: string,
  screenshotName: string
): Promise<string> {
  const legalLink = await firstVisible([
    page.getByRole('link', { name: new RegExp(escapeRegex(linkText), 'i') }).first(),
    page.getByRole('button', { name: new RegExp(escapeRegex(linkText), 'i') }).first(),
    page.getByText(new RegExp(escapeRegex(linkText), 'i')).first()
  ]);

  const popupPromise = context.waitForEvent('page', { timeout: 10_000 }).catch(() => null);
  await clickAndWait(legalLink, page);

  let legalPage = await popupPromise;
  let openedInNewTab = true;

  if (!legalPage) {
    legalPage = page;
    openedInNewTab = false;
  } else {
    await legalPage.waitForLoadState('domcontentloaded');
    await waitForUiLoad(legalPage);
  }

  await expect(
    await firstVisible(
      [
        legalPage.getByRole('heading', { name: new RegExp(escapeRegex(headingText), 'i') }).first(),
        legalPage.getByText(new RegExp(escapeRegex(headingText), 'i')).first()
      ],
      30_000
    )
  ).toBeVisible();

  const legalContent = legalPage.locator('main, article, section, p').filter({ hasText: /\S+/ }).first();
  await expect(legalContent).toBeVisible();

  await captureCheckpoint(legalPage, screenshotDir, screenshotName);
  const finalUrl = legalPage.url();

  if (openedInNewTab) {
    await legalPage.close();
    await page.bringToFront();
    await waitForUiLoad(page);
  } else {
    await page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => undefined);
    await waitForUiLoad(page);
  }

  return finalUrl;
}

test('saleads_mi_negocio_full_test', async ({ page, context }, testInfo) => {
  const screenshotDir = await ensureScreenshotDir(testInfo);
  const report: Record<ReportField, ReportRow> = Object.fromEntries(
    REPORT_FIELDS.map((field) => [
      field,
      {
        status: 'FAIL',
        details: 'Not executed.'
      }
    ])
  ) as Record<ReportField, ReportRow>;

  const failures: string[] = [];
  let termsFinalUrl = '';
  let privacyFinalUrl = '';

  const runSection = async (field: ReportField, fn: () => Promise<void>): Promise<void> => {
    try {
      await fn();
      report[field] = { status: 'PASS', details: 'All validations passed.' };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      report[field] = { status: 'FAIL', details: message };
      failures.push(`${field}: ${message}`);
    }
  };

  await runSection('Login', async () => {
    await navigateToLoginIfConfigured(page);

    const googlePage = await clickGoogleSignIn(page, context);
    await selectGoogleAccountIfPrompted(googlePage);

    if (googlePage !== page) {
      await page.bringToFront();
      await waitForUiLoad(page);
    }

    await expect(
      await firstVisible([page.locator('aside').first(), page.getByRole('navigation').first()], 30_000)
    ).toBeVisible();
    await expect(page.getByText(/Negocio/i).first()).toBeVisible();

    await captureCheckpoint(page, screenshotDir, '01-dashboard-loaded');
  });

  await runSection('Mi Negocio menu', async () => {
    await openMiNegocioMenu(page);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();

    await captureCheckpoint(page, screenshotDir, '02-mi-negocio-menu-expanded');
  });

  await runSection('Agregar Negocio modal', async () => {
    const agregarNegocio = await firstVisible([
      page.getByRole('button', { name: /^Agregar Negocio$/i }).first(),
      page.getByText(/^Agregar Negocio$/i).first()
    ]);

    await clickAndWait(agregarNegocio, page);

    await expect(page.getByText(/^Crear Nuevo Negocio$/i).first()).toBeVisible();
    const negocioInput = await firstVisible([
      page.getByLabel(/^Nombre del Negocio$/i).first(),
      page.getByPlaceholder(/Nombre del Negocio/i).first(),
      page.locator('input[placeholder*="Nombre"], input[name*="nombre"], input[id*="nombre"]').first()
    ]);
    await expect(negocioInput).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /^Cancelar$/i }).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /^Crear Negocio$/i }).first()).toBeVisible();

    await captureCheckpoint(page, screenshotDir, '03-agregar-negocio-modal');

    await negocioInput.fill('Negocio Prueba Automatización');
    await clickAndWait(page.getByRole('button', { name: /^Cancelar$/i }).first(), page);
  });

  await runSection('Administrar Negocios view', async () => {
    if (!(await page.getByText(/^Administrar Negocios$/i).first().isVisible().catch(() => false))) {
      await openMiNegocioMenu(page);
    }

    await clickAndWait(page.getByText(/^Administrar Negocios$/i).first(), page);

    await expect(page.getByText(/^Información General$/i).first()).toBeVisible();
    await expect(page.getByText(/^Detalles de la Cuenta$/i).first()).toBeVisible();
    await expect(page.getByText(/^Tus Negocios$/i).first()).toBeVisible();
    await expect(page.getByText(/^Sección Legal$/i).first()).toBeVisible();

    await captureCheckpoint(page, screenshotDir, '04-administrar-negocios-page');
  });

  await runSection('Información General', async () => {
    const infoSection = page
      .locator('section, div')
      .filter({ has: page.getByText(/^Información General$/i).first() })
      .first();

    await expect(infoSection.getByText(/@/).first()).toBeVisible();
    await expect(infoSection.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(infoSection.getByRole('button', { name: /Cambiar Plan/i }).first()).toBeVisible();
    await expect(
      infoSection
        .locator('h1, h2, h3, h4, p, span')
        .filter({ hasNotText: /@|BUSINESS PLAN|Cambiar Plan|Información General/i })
        .first()
    ).toBeVisible();
  });

  await runSection('Detalles de la Cuenta', async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runSection('Tus Negocios', async () => {
    const businessesSection = page
      .locator('section, div')
      .filter({ has: page.getByText(/^Tus Negocios$/i).first() })
      .first();

    await expect(businessesSection).toBeVisible();
    await expect(businessesSection.getByRole('button', { name: /^Agregar Negocio$/i }).first()).toBeVisible();
    await expect(businessesSection.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();

    const businessList = await firstVisible([
      businessesSection.getByRole('list').locator('li').first(),
      businessesSection.getByRole('table').first(),
      businessesSection.locator('li, tr, [data-testid*="business"], [class*="business"]').first()
    ]);
    await expect(businessList).toBeVisible();
  });

  await runSection('Términos y Condiciones', async () => {
    termsFinalUrl = await openLegalDocumentAndReturn(
      page,
      context,
      'Términos y Condiciones',
      'Términos y Condiciones',
      screenshotDir,
      '05-terminos-y-condiciones'
    );
  });

  await runSection('Política de Privacidad', async () => {
    privacyFinalUrl = await openLegalDocumentAndReturn(
      page,
      context,
      'Política de Privacidad',
      'Política de Privacidad',
      screenshotDir,
      '06-politica-de-privacidad'
    );
  });

  const reportPayload = {
    reportName: 'saleads_mi_negocio_full_test',
    finalStatus: failures.length === 0 ? 'PASS' : 'FAIL',
    validations: report,
    legalUrls: {
      termsAndConditions: termsFinalUrl || 'N/A',
      privacyPolicy: privacyFinalUrl || 'N/A'
    }
  };

  await testInfo.attach('final-report.json', {
    contentType: 'application/json',
    body: Buffer.from(JSON.stringify(reportPayload, null, 2), 'utf-8')
  });

  if (failures.length > 0) {
    throw new Error(`Workflow failed:\n- ${failures.join('\n- ')}`);
  }
});
