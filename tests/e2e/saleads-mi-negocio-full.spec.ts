import { expect, Locator, Page, TestInfo, test } from '@playwright/test';

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

const GOOGLE_ACCOUNT_EMAIL = 'juanlucasbarbiergarzon@gmail.com';

const LOGIN_BUTTON_REGEX = /google|sign in|iniciar sesi[oó]n|continuar con google/i;
const NEGOCIO_REGEX = /^Negocio$/i;
const MI_NEGOCIO_REGEX = /^Mi Negocio$/i;
const AGREGAR_NEGOCIO_REGEX = /^Agregar Negocio$/i;
const ADMINISTRAR_NEGOCIOS_REGEX = /^Administrar Negocios$/i;

function toErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded', { timeout: 15_000 }).catch(() => undefined);
  await page.waitForLoadState('networkidle', { timeout: 8_000 }).catch(() => undefined);
  await page.waitForTimeout(500);
}

async function findFirstExistingOrVisible(
  candidates: Locator[],
  description: string,
): Promise<Locator> {
  let firstExisting: Locator | null = null;

  for (const candidate of candidates) {
    if ((await candidate.count()) === 0) {
      continue;
    }

    const first = candidate.first();
    if (!firstExisting) {
      firstExisting = first;
    }

    if (await first.isVisible().catch(() => false)) {
      return first;
    }
  }

  if (firstExisting) {
    return firstExisting;
  }

  throw new Error(`Could not find element: ${description}`);
}

async function clickTextDrivenElement(
  page: Page,
  textRegex: RegExp,
  description: string,
): Promise<void> {
  const target = await findFirstExistingOrVisible(
    [
      page.getByRole('button', { name: textRegex }),
      page.getByRole('link', { name: textRegex }),
      page.getByRole('menuitem', { name: textRegex }),
      page.getByRole('treeitem', { name: textRegex }),
      page.getByText(textRegex),
    ],
    description,
  );

  await target.click();
  await waitForUi(page);
}

async function expectAnyVisible(
  candidates: Locator[],
  description: string,
  timeoutMs = 15_000,
): Promise<void> {
  const timeoutPerCandidate = Math.max(Math.floor(timeoutMs / Math.max(1, candidates.length)), 2500);
  const errors: string[] = [];

  for (const candidate of candidates) {
    try {
      await expect(candidate.first()).toBeVisible({ timeout: timeoutPerCandidate });
      return;
    } catch (error) {
      errors.push(toErrorMessage(error));
    }
  }

  throw new Error(`Expected visible element for ${description}. Details: ${errors.join(' | ')}`);
}

async function captureCheckpoint(
  page: Page,
  testInfo: TestInfo,
  fileName: string,
  fullPage = false,
): Promise<void> {
  await page.screenshot({
    path: testInfo.outputPath(fileName),
    fullPage,
  });
}

async function selectGoogleAccountIfShown(targetPage: Page): Promise<void> {
  const accountOption = targetPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
  await accountOption.waitFor({ state: 'visible', timeout: 10_000 }).catch(() => undefined);

  if (await accountOption.isVisible().catch(() => false)) {
    await accountOption.click();
    await waitForUi(targetPage);
  }
}

async function runLegalLinkValidation(
  page: Page,
  testInfo: TestInfo,
  linkLabel: string,
  headingRegex: RegExp,
  screenshotName: string,
): Promise<string> {
  const context = page.context();
  const appUrlBeforeClick = page.url();
  const popupPromise = context.waitForEvent('page', { timeout: 10_000 }).catch(() => null);

  await clickTextDrivenElement(page, new RegExp(linkLabel, 'i'), `${linkLabel} link`);

  const popup = await popupPromise;
  const legalPage = popup ?? page;

  await legalPage.waitForLoadState('domcontentloaded', { timeout: 20_000 }).catch(() => undefined);
  await expect(legalPage.getByRole('heading', { name: headingRegex }).first()).toBeVisible({
    timeout: 20_000,
  });

  const legalText = (await legalPage.locator('body').innerText()).trim();
  expect(legalText.length, `${linkLabel} page should contain legal body text`).toBeGreaterThan(120);

  await captureCheckpoint(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => undefined);
    if (appUrlBeforeClick !== page.url()) {
      await page.goto(appUrlBeforeClick, { waitUntil: 'domcontentloaded' }).catch(() => undefined);
    }
    await waitForUi(page);
  }

  return finalUrl;
}

test('saleads_mi_negocio_full_test', async ({ page }, testInfo) => {
  const statuses = new Map<ReportField, StepStatus>();
  const failures: string[] = [];
  let termsUrl = 'N/A';
  let privacyUrl = 'N/A';

  const executeStep = async (field: ReportField, action: () => Promise<void>): Promise<void> => {
    try {
      await test.step(field, action);
      statuses.set(field, 'PASS');
    } catch (error) {
      statuses.set(field, 'FAIL');
      failures.push(`${field}: ${toErrorMessage(error)}`);
    }
  };

  await executeStep('Login', async () => {
    const startUrl =
      process.env.SALEADS_URL ?? process.env.SALEADS_LOGIN_URL ?? process.env.PLAYWRIGHT_BASE_URL;

    if (startUrl) {
      await page.goto(startUrl, { waitUntil: 'domcontentloaded' });
      await waitForUi(page);
    } else if (page.url() === 'about:blank') {
      throw new Error(
        'No starting URL detected. Provide SALEADS_URL (or SALEADS_LOGIN_URL) or launch with a preloaded SaleADS login page.',
      );
    }

    const popupPromise = page.context().waitForEvent('page', { timeout: 10_000 }).catch(() => null);
    await clickTextDrivenElement(page, LOGIN_BUTTON_REGEX, 'Google sign-in button');

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState('domcontentloaded', { timeout: 20_000 }).catch(() => undefined);
      await selectGoogleAccountIfShown(popup);
      await popup.waitForClose({ timeout: 45_000 }).catch(() => undefined);
      await page.bringToFront();
    }

    await selectGoogleAccountIfShown(page);

    await expectAnyVisible(
      [
        page.getByRole('navigation'),
        page.locator('aside'),
        page.getByText(/Mi Negocio|Negocio|Dashboard|Panel/i),
      ],
      'main application interface / left sidebar',
      45_000,
    );

    await captureCheckpoint(page, testInfo, '01-dashboard-loaded.png', true);
  });

  await executeStep('Mi Negocio menu', async () => {
    await clickTextDrivenElement(page, NEGOCIO_REGEX, 'Negocio section');
    await clickTextDrivenElement(page, MI_NEGOCIO_REGEX, 'Mi Negocio option');

    await expect(page.getByText(AGREGAR_NEGOCIO_REGEX).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(ADMINISTRAR_NEGOCIOS_REGEX).first()).toBeVisible({
      timeout: 15_000,
    });

    await captureCheckpoint(page, testInfo, '02-mi-negocio-menu-expanded.png');
  });

  await executeStep('Agregar Negocio modal', async () => {
    await clickTextDrivenElement(page, AGREGAR_NEGOCIO_REGEX, 'Agregar Negocio option');

    await expect(page.getByRole('heading', { name: /Crear Nuevo Negocio/i }).first()).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.getByLabel(/Nombre del Negocio/i).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.getByRole('button', { name: /Cancelar/i }).first()).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.getByRole('button', { name: /Crear Negocio/i }).first()).toBeVisible({
      timeout: 15_000,
    });

    await captureCheckpoint(page, testInfo, '03-agregar-negocio-modal.png');

    const businessNameInput = page.getByLabel(/Nombre del Negocio/i).first();
    await businessNameInput.click();
    await businessNameInput.fill('Negocio Prueba Automatización');
    await clickTextDrivenElement(page, /Cancelar/i, 'Cancelar button in modal');
  });

  await executeStep('Administrar Negocios view', async () => {
    // Ensure submenu is available before opening "Administrar Negocios".
    if (!(await page.getByText(ADMINISTRAR_NEGOCIOS_REGEX).first().isVisible().catch(() => false))) {
      await clickTextDrivenElement(page, NEGOCIO_REGEX, 'Negocio section');
      await clickTextDrivenElement(page, MI_NEGOCIO_REGEX, 'Mi Negocio option');
    }

    await clickTextDrivenElement(page, ADMINISTRAR_NEGOCIOS_REGEX, 'Administrar Negocios option');

    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible({ timeout: 20_000 });

    await captureCheckpoint(page, testInfo, '04-administrar-negocios-page.png', true);
  });

  await executeStep('Información General', async () => {
    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible({ timeout: 15_000 });
    await expect(
      page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first(),
    ).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole('button', { name: /Cambiar Plan/i }).first()).toBeVisible({
      timeout: 15_000,
    });

    const infoSectionText = await page.locator('body').innerText();
    const hasUserNameHint =
      /Nombre|Usuario|Name/i.test(infoSectionText) ||
      /[A-Za-zÀ-ÿ]{2,}\s+[A-Za-zÀ-ÿ]{2,}/.test(infoSectionText);
    expect(hasUserNameHint, 'Expected user name or user-name hint to be visible').toBeTruthy();
  });

  await executeStep('Detalles de la Cuenta', async () => {
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 15_000 });
  });

  await executeStep('Tus Negocios', async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole('button', { name: AGREGAR_NEGOCIO_REGEX }).first()).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({
      timeout: 15_000,
    });

    await expectAnyVisible(
      [
        page.getByText(/Tus Negocios/i),
        page.locator('table'),
        page.locator('[role="list"]'),
        page.locator('ul'),
      ],
      'business list',
    );
  });

  await executeStep('Términos y Condiciones', async () => {
    termsUrl = await runLegalLinkValidation(
      page,
      testInfo,
      'Términos y Condiciones',
      /T[ée]rminos y Condiciones/i,
      '05-terminos-y-condiciones.png',
    );
  });

  await executeStep('Política de Privacidad', async () => {
    privacyUrl = await runLegalLinkValidation(
      page,
      testInfo,
      'Política de Privacidad',
      /Pol[ií]tica de Privacidad/i,
      '06-politica-de-privacidad.png',
    );
  });

  for (const field of REPORT_FIELDS) {
    if (!statuses.has(field)) {
      statuses.set(field, 'FAIL');
      failures.push(`${field}: Step was not executed.`);
    }
  }

  const reportLines = [
    '# SaleADS Mi Negocio Workflow Report',
    '',
    `- Triggered test: ${testInfo.title}`,
    '',
    '| Validation | Result |',
    '| --- | --- |',
    ...REPORT_FIELDS.map((field) => `| ${field} | ${statuses.get(field)} |`),
    '',
    `- Final URL (Términos y Condiciones): ${termsUrl}`,
    `- Final URL (Política de Privacidad): ${privacyUrl}`,
  ];

  const reportBody = reportLines.join('\n');
  await testInfo.attach('saleads-mi-negocio-final-report', {
    body: Buffer.from(reportBody, 'utf-8'),
    contentType: 'text/markdown',
  });

  // Keep this visible in CI logs for quick triage.
  console.log(reportBody);

  expect(failures, `One or more validations failed:\n${failures.join('\n')}`).toEqual([]);
});
