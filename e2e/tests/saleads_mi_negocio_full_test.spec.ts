import { expect, Locator, Page, test, TestInfo } from '@playwright/test';

type ValidationStatus = 'PASS' | 'FAIL';

type ValidationResult = {
  field: string;
  status: ValidationStatus;
  details?: string;
};

const REPORT_FIELDS = [
  'Login',
  'Mi Negocio menu',
  'Agregar Negocio modal',
  'Administrar Negocios view',
  'Información General',
  'Detalles de la Cuenta',
  'Tus Negocios',
  'Términos y Condiciones',
  'Política de Privacidad',
] as const;

const CHECKPOINT_PREFIX: Record<string, string> = {
  Login: '01-dashboard-loaded',
  'Mi Negocio menu': '02-mi-negocio-menu',
  'Agregar Negocio modal': '03-agregar-negocio-modal',
  'Administrar Negocios view': '04-administrar-negocios',
  'Términos y Condiciones': '08-terminos-y-condiciones',
  'Política de Privacidad': '09-politica-de-privacidad',
};

function sanitizeFileName(input: string): string {
  return input
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-zA-Z0-9-_]+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '')
    .toLowerCase();
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(750);
}

async function resolveVisibleLocator(
  candidates: Array<() => Locator>,
  description: string,
): Promise<Locator> {
  for (const candidate of candidates) {
    const locator = candidate().first();
    try {
      await locator.waitFor({ state: 'visible', timeout: 6000 });
      return locator;
    } catch {
      // Try next candidate.
    }
  }

  throw new Error(`Could not locate ${description} using visible text selectors.`);
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function takeCheckpoint(
  page: Page,
  testInfo: TestInfo,
  checkpointName: string,
  fullPage = false,
): Promise<void> {
  await page.screenshot({
    path: testInfo.outputPath(`${sanitizeFileName(checkpointName)}.png`),
    fullPage,
  });
}

function readErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

test('saleads_mi_negocio_full_test', async ({ page, context }, testInfo) => {
  const report = new Map<string, ValidationResult>();
  let appPageUrl = '';
  let terminosUrl = '';
  let privacidadUrl = '';

  const markResult = (field: string, status: ValidationStatus, details?: string): void => {
    report.set(field, { field, status, details });
  };

  const runValidation = async (field: string, fn: () => Promise<void>): Promise<void> => {
    try {
      await fn();
      markResult(field, 'PASS');
    } catch (error) {
      markResult(field, 'FAIL', readErrorMessage(error));
      await takeCheckpoint(page, testInfo, `${field}-failure`, true);
    }
  };

  const openLegalLink = async (linkNameRegex: RegExp, checkpointName: string): Promise<string> => {
    const popupPromise = context.waitForEvent('page', { timeout: 10000 }).catch(() => null);

    const legalLink = await resolveVisibleLocator(
      [() => page.getByRole('link', { name: linkNameRegex }), () => page.getByText(linkNameRegex)],
      `legal link ${linkNameRegex}`,
    );

    await clickAndWait(legalLink, page);

    const popup = await popupPromise;
    const legalPage = popup ?? page;

    await legalPage.waitForLoadState('domcontentloaded');
    await waitForUi(legalPage);
    await expect(legalPage.getByText(linkNameRegex)).toBeVisible();

    const bodyTextLength = (
      await legalPage.locator('body').innerText()
    ).trim().replace(/\s+/g, ' ').length;
    expect(bodyTextLength).toBeGreaterThan(150);

    await takeCheckpoint(legalPage, testInfo, checkpointName, true);
    const finalUrl = legalPage.url();

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
    } else if (page.url() !== appPageUrl) {
      await page.goBack().catch(async () => {
        if (appPageUrl) {
          await page.goto(appPageUrl, { waitUntil: 'domcontentloaded' });
        }
      });
      await waitForUi(page);
    }

    return finalUrl;
  };

  await runValidation('Login', async () => {
    const configuredUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL;

    if (configuredUrl) {
      await page.goto(configuredUrl, { waitUntil: 'domcontentloaded' });
      await waitForUi(page);
    } else if (page.url() === 'about:blank') {
      throw new Error(
        'No login page loaded. Provide SALEADS_LOGIN_URL/SALEADS_BASE_URL or start this test with SaleADS login already open.',
      );
    }

    const loginButton = await resolveVisibleLocator(
      [
        () => page.getByRole('button', { name: /sign in with google|login with google|continuar con google/i }),
        () => page.getByText(/sign in with google|login with google|continuar con google/i),
        () => page.getByRole('button', { name: /google/i }),
      ],
      'Google login button',
    );

    const popupPromise = context.waitForEvent('page', { timeout: 12000 }).catch(() => null);
    await clickAndWait(loginButton, page);

    const googlePopup = await popupPromise;
    const accountRegex = /juanlucasbarbiergarzon@gmail\.com/i;

    if (googlePopup) {
      await googlePopup.waitForLoadState('domcontentloaded');
      await waitForUi(googlePopup);
      const accountOption = googlePopup.getByText(accountRegex).first();
      if (await accountOption.isVisible({ timeout: 7000 }).catch(() => false)) {
        await accountOption.click();
        await waitForUi(googlePopup);
      }
      await googlePopup.waitForEvent('close', { timeout: 45000 }).catch(() => null);
    } else {
      const accountOption = page.getByText(accountRegex).first();
      if (await accountOption.isVisible({ timeout: 7000 }).catch(() => false)) {
        await clickAndWait(accountOption, page);
      }
    }

    await page.bringToFront();
    await waitForUi(page);

    const navigationVisible = await page.getByRole('navigation').first().isVisible().catch(() => false);
    const sidebarVisible = navigationVisible || (await page.locator('aside').first().isVisible().catch(() => false));
    expect(sidebarVisible).toBeTruthy();
    await expect(page.getByText(/negocio/i).first()).toBeVisible();

    await takeCheckpoint(page, testInfo, CHECKPOINT_PREFIX.Login);
  });

  await runValidation('Mi Negocio menu', async () => {
    const negocioSection = await resolveVisibleLocator(
      [() => page.getByText(/^Negocio$/i), () => page.getByRole('button', { name: /^Negocio$/i })],
      'Negocio section',
    );
    await clickAndWait(negocioSection, page);

    const miNegocio = await resolveVisibleLocator(
      [() => page.getByRole('button', { name: /^Mi Negocio$/i }), () => page.getByText(/^Mi Negocio$/i)],
      'Mi Negocio sidebar item',
    );
    await clickAndWait(miNegocio, page);

    await expect(page.getByText(/^Agregar Negocio$/i)).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i)).toBeVisible();

    await takeCheckpoint(page, testInfo, CHECKPOINT_PREFIX['Mi Negocio menu']);
  });

  await runValidation('Agregar Negocio modal', async () => {
    const agregarNegocio = await resolveVisibleLocator(
      [() => page.getByRole('button', { name: /^Agregar Negocio$/i }), () => page.getByText(/^Agregar Negocio$/i)],
      'Agregar Negocio submenu item',
    );
    await clickAndWait(agregarNegocio, page);

    await expect(page.getByText(/^Crear Nuevo Negocio$/i)).toBeVisible();
    await expect(page.getByText(/^Tienes 2 de 3 negocios$/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /^Cancelar$/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /^Crear Negocio$/i })).toBeVisible();

    const businessNameInput = await resolveVisibleLocator(
      [() => page.getByLabel(/^Nombre del Negocio$/i), () => page.getByPlaceholder(/^Nombre del Negocio$/i)],
      'Nombre del Negocio input',
    );

    await takeCheckpoint(page, testInfo, CHECKPOINT_PREFIX['Agregar Negocio modal']);

    await businessNameInput.click();
    await waitForUi(page);
    await businessNameInput.fill('Negocio Prueba Automatización');

    const cancelButton = page.getByRole('button', { name: /^Cancelar$/i });
    await clickAndWait(cancelButton, page);
    await expect(page.getByText(/^Crear Nuevo Negocio$/i)).not.toBeVisible();
  });

  await runValidation('Administrar Negocios view', async () => {
    const agregarVisible = await page.getByText(/^Agregar Negocio$/i).isVisible().catch(() => false);
    const administrarVisible = await page.getByText(/^Administrar Negocios$/i).isVisible().catch(() => false);

    if (!agregarVisible || !administrarVisible) {
      const miNegocio = await resolveVisibleLocator(
        [() => page.getByRole('button', { name: /^Mi Negocio$/i }), () => page.getByText(/^Mi Negocio$/i)],
        'Mi Negocio sidebar item',
      );
      await clickAndWait(miNegocio, page);
    }

    const administrarNegocios = await resolveVisibleLocator(
      [
        () => page.getByRole('button', { name: /^Administrar Negocios$/i }),
        () => page.getByText(/^Administrar Negocios$/i),
      ],
      'Administrar Negocios submenu item',
    );
    await clickAndWait(administrarNegocios, page);

    await expect(page.getByText(/Información General/i)).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Sección Legal/i)).toBeVisible();

    appPageUrl = page.url();
    await takeCheckpoint(page, testInfo, CHECKPOINT_PREFIX['Administrar Negocios view'], true);
  });

  await runValidation('Información General', async () => {
    await expect(page.getByText(/Información General/i)).toBeVisible();

    const emailLocator = page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first();
    await expect(emailLocator).toBeVisible();

    const hasNameLabel = await page.getByText(/^Nombre$/i).first().isVisible().catch(() => false);
    const hasUsuarioLabel = await page.getByText(/^Usuario$/i).first().isVisible().catch(() => false);
    expect(hasNameLabel || hasUsuarioLabel).toBeTruthy();

    await expect(page.getByText(/^BUSINESS PLAN$/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /^Cambiar Plan$/i })).toBeVisible();
  });

  await runValidation('Detalles de la Cuenta', async () => {
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runValidation('Tus Negocios', async () => {
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /^Agregar Negocio$/i })).toBeVisible();
    await expect(page.getByText(/^Tienes 2 de 3 negocios$/i)).toBeVisible();

    const businessListVisible = await page.locator('li, tr, [data-testid*="business"], [class*="business"]').count();
    expect(businessListVisible).toBeGreaterThan(0);
  });

  await runValidation('Términos y Condiciones', async () => {
    terminosUrl = await openLegalLink(/Términos y Condiciones|Terminos y Condiciones/i, CHECKPOINT_PREFIX['Términos y Condiciones']);
    const current = report.get('Términos y Condiciones');
    markResult('Términos y Condiciones', current?.status ?? 'PASS', `Final URL: ${terminosUrl}`);
  });

  await runValidation('Política de Privacidad', async () => {
    privacidadUrl = await openLegalLink(/Política de Privacidad|Politica de Privacidad/i, CHECKPOINT_PREFIX['Política de Privacidad']);
    const current = report.get('Política de Privacidad');
    markResult('Política de Privacidad', current?.status ?? 'PASS', `Final URL: ${privacidadUrl}`);
  });

  const finalReport: ValidationResult[] = REPORT_FIELDS.map((field) => {
    return report.get(field) ?? { field, status: 'FAIL', details: 'Not executed.' };
  });

  await testInfo.attach('saleads-mi-negocio-final-report', {
    body: Buffer.from(JSON.stringify(finalReport, null, 2), 'utf-8'),
    contentType: 'application/json',
  });

  // Keep URLs visible in console output for external report collectors.
  // eslint-disable-next-line no-console
  console.log(
    JSON.stringify(
      {
        testName: 'saleads_mi_negocio_full_test',
        terminosFinalUrl: terminosUrl || null,
        privacidadFinalUrl: privacidadUrl || null,
        validations: finalReport,
      },
      null,
      2,
    ),
  );

  const failed = finalReport.filter((item) => item.status === 'FAIL');
  expect(failed, `Validation failures: ${JSON.stringify(failed, null, 2)}`).toEqual([]);
});
