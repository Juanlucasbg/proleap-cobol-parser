import { BrowserContext, expect, Locator, Page, test, TestInfo } from '@playwright/test';

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
type ReportResult = { status: 'PASS' | 'FAIL'; details: string };

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function toErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

async function waitForUiLoad(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForLoadState('networkidle', { timeout: 15_000 }).catch(() => {
    // Some UI interactions do not trigger network idle. Keep test resilient.
  });
  await page.waitForTimeout(350);
}

async function captureCheckpoint(page: Page, testInfo: TestInfo, fileName: string): Promise<void> {
  await page.screenshot({
    path: testInfo.outputPath(fileName),
    fullPage: true
  });
}

async function firstVisible(candidates: Locator[]): Promise<Locator | null> {
  for (const candidate of candidates) {
    const current = candidate.first();
    const count = await current.count();

    if (count === 0) {
      continue;
    }

    if (await current.isVisible().catch(() => false)) {
      return current;
    }
  }

  return null;
}

function candidatesByVisibleText(scope: Page | Locator, label: string): Locator[] {
  const exact = new RegExp(`^\\s*${escapeRegex(label)}\\s*$`, 'i');
  const contains = new RegExp(escapeRegex(label), 'i');

  return [
    scope.getByRole('button', { name: exact }),
    scope.getByRole('link', { name: exact }),
    scope.getByRole('menuitem', { name: exact }),
    scope.getByRole('tab', { name: exact }),
    scope.getByText(exact),
    scope.getByText(contains)
  ];
}

async function findVisibleByText(scope: Page | Locator, labels: string[]): Promise<Locator | null> {
  for (const label of labels) {
    const found = await firstVisible(candidatesByVisibleText(scope, label));
    if (found) {
      return found;
    }
  }

  return null;
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiLoad(page);
}

async function clickByVisibleText(page: Page, scope: Page | Locator, labels: string[], step: string): Promise<Locator> {
  const found = await findVisibleByText(scope, labels);

  if (!found) {
    throw new Error(`${step}: could not find visible element for labels [${labels.join(', ')}].`);
  }

  await clickAndWait(page, found);
  return found;
}

async function expandMiNegocioMenu(page: Page): Promise<void> {
  const negocioNode = await findVisibleByText(page, ['Negocio']);
  if (negocioNode) {
    await clickAndWait(page, negocioNode);
  }

  const miNegocioNode = await findVisibleByText(page, ['Mi Negocio']);
  if (!miNegocioNode) {
    throw new Error('Mi Negocio option was not found in the left navigation.');
  }

  await clickAndWait(page, miNegocioNode);
}

async function findTextWithRetry(page: Page, text: string, timeoutMs: number): Promise<Locator | null> {
  const start = Date.now();

  while (Date.now() - start < timeoutMs) {
    const found = await findVisibleByText(page, [text]);
    if (found) {
      return found;
    }

    await page.waitForTimeout(300);
  }

  return null;
}

async function openLegalLinkAndValidate(
  page: Page,
  context: BrowserContext,
  linkText: string,
  headingPattern: RegExp,
  testInfo: TestInfo,
  screenshotName: string
): Promise<string> {
  const knownPages = new Set(context.pages());
  const legalLink = await findVisibleByText(page, [linkText]);

  if (!legalLink) {
    throw new Error(`Link "${linkText}" was not found.`);
  }

  await legalLink.click();
  await waitForUiLoad(page);

  const newPage = context.pages().find((item) => !knownPages.has(item));
  const targetPage = newPage ?? page;

  await waitForUiLoad(targetPage);
  await expect(targetPage.getByText(headingPattern).first()).toBeVisible();

  const legalContent = await targetPage.locator('main, article, body').first().innerText();
  expect(legalContent.trim().length).toBeGreaterThan(120);

  const finalUrl = targetPage.url();
  await captureCheckpoint(targetPage, testInfo, screenshotName);

  if (newPage) {
    await newPage.close();
    await page.bringToFront();
    await waitForUiLoad(page);
  } else {
    await page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => {
      // If no history entry exists, we still continue from current page context.
    });
    await waitForUiLoad(page);
  }

  return finalUrl;
}

test('saleads_mi_negocio_full_test', async ({ page, context }, testInfo) => {
  const report: Record<ReportField, ReportResult> = {
    Login: { status: 'FAIL', details: 'Not executed' },
    'Mi Negocio menu': { status: 'FAIL', details: 'Not executed' },
    'Agregar Negocio modal': { status: 'FAIL', details: 'Not executed' },
    'Administrar Negocios view': { status: 'FAIL', details: 'Not executed' },
    'Información General': { status: 'FAIL', details: 'Not executed' },
    'Detalles de la Cuenta': { status: 'FAIL', details: 'Not executed' },
    'Tus Negocios': { status: 'FAIL', details: 'Not executed' },
    'Términos y Condiciones': { status: 'FAIL', details: 'Not executed' },
    'Política de Privacidad': { status: 'FAIL', details: 'Not executed' }
  };

  let termsUrl = '';
  let privacyUrl = '';

  const runStep = async (field: ReportField, action: () => Promise<void>): Promise<void> => {
    try {
      await action();
      report[field] = { status: 'PASS', details: 'Validated successfully.' };
    } catch (error) {
      report[field] = { status: 'FAIL', details: toErrorMessage(error) };
    }
  };

  const startUrl = process.env.SALEADS_START_URL ?? process.env.SALEADS_BASE_URL ?? process.env.BASE_URL;
  if (startUrl) {
    await page.goto(startUrl, { waitUntil: 'domcontentloaded' });
    await waitForUiLoad(page);
  } else {
    throw new Error(
      'Set SALEADS_START_URL (or SALEADS_BASE_URL / BASE_URL) to the current environment login page.'
    );
  }

  await runStep('Login', async () => {
    const knownPages = new Set(context.pages());
    await clickByVisibleText(
      page,
      page,
      ['Sign in with Google', 'Iniciar sesión con Google', 'Continuar con Google', 'Google'],
      'Login'
    );

    const loginPopup = context.pages().find((item) => !knownPages.has(item));
    const accountEmail = 'juanlucasbarbiergarzon@gmail.com';

    if (loginPopup) {
      await waitForUiLoad(loginPopup);
      const popupAccount = await findTextWithRetry(loginPopup, accountEmail, 12_000);
      if (popupAccount) {
        await clickAndWait(loginPopup, popupAccount);
      }
      await page.bringToFront();
      await waitForUiLoad(page);
    } else {
      const accountSelector = await findTextWithRetry(page, accountEmail, 7_000);
      if (accountSelector) {
        await clickAndWait(page, accountSelector);
      }
    }

    await expect(page.locator('aside, nav').first()).toBeVisible();
    await expect(page.getByText(/Mi Negocio|Negocio|Dashboard|Inicio/i).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, '01_dashboard_loaded.png');
  });

  await runStep('Mi Negocio menu', async () => {
    await expandMiNegocioMenu(page);
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, '02_mi_negocio_menu_expanded.png');
  });

  await runStep('Agregar Negocio modal', async () => {
    await clickByVisibleText(page, page, ['Agregar Negocio'], 'Agregar Negocio modal');

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
    const modal = page
      .locator('[role="dialog"], .modal, .ant-modal, [data-testid*="modal"]')
      .filter({ hasText: /Crear Nuevo Negocio/i })
      .first();

    const nameInput = await firstVisible([
      modal.getByLabel(/Nombre del Negocio/i),
      page.getByLabel(/Nombre del Negocio/i),
      modal.getByPlaceholder(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator('input[name*="negocio" i], input[id*="negocio" i]').first()
    ]);
    if (!nameInput) {
      throw new Error('Nombre del Negocio input was not found.');
    }

    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Cancelar/i).first()).toBeVisible();
    await expect(page.getByText(/Crear Negocio/i).first()).toBeVisible();

    await nameInput.fill('Negocio Prueba Automatización');
    await captureCheckpoint(page, testInfo, '03_agregar_negocio_modal.png');

    const cancelButton = (await findVisibleByText(modal, ['Cancelar'])) ?? (await findVisibleByText(page, ['Cancelar']));
    if (!cancelButton) {
      throw new Error('Cancelar button inside modal was not found.');
    }

    await clickAndWait(page, cancelButton);
  });

  await runStep('Administrar Negocios view', async () => {
    const manageOption = await findVisibleByText(page, ['Administrar Negocios']);
    if (!manageOption) {
      await expandMiNegocioMenu(page);
    }

    await clickByVisibleText(page, page, ['Administrar Negocios'], 'Administrar Negocios view');

    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, '04_administrar_negocios_view.png');
  });

  await runStep('Información General', async () => {
    const infoSection = page.locator('section, article, div').filter({ hasText: /Información General/i }).first();
    await expect(infoSection).toBeVisible();

    const emailInSection = infoSection.locator('text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/').first();
    await expect(emailInSection).toBeVisible();

    const infoText = await infoSection.innerText();
    const hasUserName = infoText
      .split('\n')
      .map((line) => line.trim())
      .some(
        (line) =>
          /^[A-Za-zÁÉÍÓÚÑÜáéíóúñü][A-Za-zÁÉÍÓÚÑÜáéíóúñü' -]{2,}$/.test(line) &&
          !line.includes('@') &&
          !/Información General|BUSINESS PLAN|Cambiar Plan/i.test(line)
      );
    expect(hasUserName).toBeTruthy();

    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByText(/Cambiar Plan/i).first()).toBeVisible();
  });

  await runStep('Detalles de la Cuenta', async () => {
    const accountDetailsSection = page
      .locator('section, article, div')
      .filter({ hasText: /Detalles de la Cuenta/i })
      .first();
    await expect(accountDetailsSection).toBeVisible();
    await expect(accountDetailsSection.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(accountDetailsSection.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(accountDetailsSection.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep('Tus Negocios', async () => {
    const businessSection = page.locator('section, article, div').filter({ hasText: /Tus Negocios/i }).first();
    await expect(businessSection).toBeVisible();

    const businessesList = businessSection.locator('li, tr, [data-testid*="business"], .business-item');
    expect(await businessesList.count()).toBeGreaterThan(0);

    await expect(businessSection.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(businessSection.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
  });

  await runStep('Términos y Condiciones', async () => {
    termsUrl = await openLegalLinkAndValidate(
      page,
      context,
      'Términos y Condiciones',
      /Términos y Condiciones/i,
      testInfo,
      '05_terminos_y_condiciones.png'
    );
  });

  await runStep('Política de Privacidad', async () => {
    privacyUrl = await openLegalLinkAndValidate(
      page,
      context,
      'Política de Privacidad',
      /Política de Privacidad/i,
      testInfo,
      '06_politica_de_privacidad.png'
    );
  });

  const reportLines: string[] = ['Final Validation Report'];
  for (const field of REPORT_FIELDS) {
    const result = report[field];
    reportLines.push(`- ${field}: ${result.status} (${result.details})`);
  }
  reportLines.push(`- Términos y Condiciones URL: ${termsUrl || 'N/A'}`);
  reportLines.push(`- Política de Privacidad URL: ${privacyUrl || 'N/A'}`);

  await testInfo.attach('saleads-mi-negocio-report.txt', {
    body: Buffer.from(reportLines.join('\n'), 'utf-8'),
    contentType: 'text/plain'
  });

  for (const line of reportLines) {
    console.log(line);
  }

  const failures = REPORT_FIELDS.filter((field) => report[field].status === 'FAIL');
  expect(failures, `Validation failures:\n${reportLines.join('\n')}`).toEqual([]);
});
