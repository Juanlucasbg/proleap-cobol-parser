import { expect, Page, test, type BrowserContext, type TestInfo } from '@playwright/test';

type ReportStatus = 'PASS' | 'FAIL';

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

type Report = Record<ReportField, ReportStatus>;

const ACCOUNT_EMAIL = 'juanlucasbarbiergarzon@gmail.com';

function headingRegex(text: string): RegExp {
  return new RegExp(text, 'i');
}

async function waitForUiAfterClick(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded').catch(() => {});
  await page.waitForLoadState('networkidle').catch(() => {});
  await page.waitForTimeout(750);
}

async function captureScreenshot(
  page: Page,
  testInfo: TestInfo,
  name: string,
  fullPage = false,
): Promise<void> {
  const path = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(name, {
    path,
    contentType: 'image/png',
  });
}

async function getVisibleLocator(page: Page, candidates: string[]) {
  for (const candidate of candidates) {
    const byRoleButton = page.getByRole('button', { name: headingRegex(candidate) }).first();
    if (await byRoleButton.isVisible().catch(() => false)) {
      return byRoleButton;
    }

    const byRoleLink = page.getByRole('link', { name: headingRegex(candidate) }).first();
    if (await byRoleLink.isVisible().catch(() => false)) {
      return byRoleLink;
    }

    const byText = page.getByText(headingRegex(candidate)).first();
    if (await byText.isVisible().catch(() => false)) {
      return byText;
    }
  }

  return page.getByText(headingRegex(candidates[0])).first();
}

async function clickGoogleAndHandleAccountSelection(page: Page, context: BrowserContext): Promise<void> {
  const loginButton = await getVisibleLocator(page, [
    'Sign in with Google',
    'Iniciar con Google',
    'Iniciar sesión con Google',
    'Google',
    'Login',
  ]);

  await expect(loginButton).toBeVisible();

  const popupPromise = context.waitForEvent('page', { timeout: 8000 }).catch(() => null);
  await loginButton.click();

  const popup = await popupPromise;
  const authPage = popup ?? page;
  await waitForUiAfterClick(authPage);

  const accountOption = authPage.getByText(ACCOUNT_EMAIL, { exact: false }).first();
  if (await accountOption.isVisible({ timeout: 10_000 }).catch(() => false)) {
    await accountOption.click();
    await waitForUiAfterClick(authPage);
  }

  if (popup) {
    await popup.waitForEvent('close', { timeout: 30_000 }).catch(() => {});
    await page.bringToFront();
  }
}

async function validateLegalLink(
  page: Page,
  context: BrowserContext,
  testInfo: TestInfo,
  linkLabel: string,
  headingLabel: string,
): Promise<string> {
  const link = await getVisibleLocator(page, [linkLabel]);
  await expect(link).toBeVisible();

  const previousUrl = page.url();
  const popupPromise = context.waitForEvent('page', { timeout: 8000 }).catch(() => null);

  await link.click();

  const popup = await popupPromise;
  const legalPage = popup ?? page;
  await waitForUiAfterClick(legalPage);

  await expect(legalPage.getByRole('heading', { name: headingRegex(headingLabel) }).first()).toBeVisible();
  await expect(legalPage.locator('body')).toContainText(headingRegex(headingLabel));

  await captureScreenshot(legalPage, testInfo, `${linkLabel.toLowerCase().replaceAll(' ', '-')}-page`, true);
  const finalUrl = legalPage.url();

  await testInfo.attach(`${linkLabel}-url`, {
    body: Buffer.from(finalUrl, 'utf-8'),
    contentType: 'text/plain',
  });

  if (popup) {
    await popup.close().catch(() => {});
    await page.bringToFront();
  } else if (page.url() !== previousUrl) {
    await page.goBack().catch(() => {});
    await waitForUiAfterClick(page);
  }

  return finalUrl;
}

test('saleads_mi_negocio_full_test', async ({ page, context }, testInfo) => {
  const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL ?? process.env.BASE_URL;
  test.skip(!loginUrl, 'Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL / BASE_URL) for the target environment.');

  const report: Report = {
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

  const legalUrls: Record<'Términos y Condiciones' | 'Política de Privacidad', string> = {
    'Términos y Condiciones': '',
    'Política de Privacidad': '',
  };

  const runStep = async (reportField: ReportField, runner: () => Promise<void>) => {
    try {
      await runner();
      report[reportField] = 'PASS';
    } catch (error) {
      report[reportField] = 'FAIL';
      await testInfo.attach(`${reportField}-error`, {
        body: Buffer.from(String(error), 'utf-8'),
        contentType: 'text/plain',
      });
    }
  };

  await page.goto(loginUrl as string, { waitUntil: 'domcontentloaded' });
  await waitForUiAfterClick(page);

  await runStep('Login', async () => {
    await clickGoogleAndHandleAccountSelection(page, context);

    await expect(page.locator('aside, [role="navigation"]').first()).toBeVisible();
    await expect(page.getByText(headingRegex('Negocio')).first()).toBeVisible();

    await captureScreenshot(page, testInfo, 'checkpoint-01-dashboard', true);
  });

  await runStep('Mi Negocio menu', async () => {
    await expect(page.getByText(headingRegex('Negocio')).first()).toBeVisible();

    const miNegocioOption = await getVisibleLocator(page, ['Mi Negocio']);
    await miNegocioOption.click();
    await waitForUiAfterClick(page);

    await expect(page.getByText(headingRegex('Agregar Negocio')).first()).toBeVisible();
    await expect(page.getByText(headingRegex('Administrar Negocios')).first()).toBeVisible();

    await captureScreenshot(page, testInfo, 'checkpoint-02-mi-negocio-expanded');
  });

  await runStep('Agregar Negocio modal', async () => {
    const agregarNegocio = await getVisibleLocator(page, ['Agregar Negocio']);
    await agregarNegocio.click();
    await waitForUiAfterClick(page);

    await expect(page.getByText(headingRegex('Crear Nuevo Negocio')).first()).toBeVisible();
    await expect(page.getByLabel(headingRegex('Nombre del Negocio')).first()).toBeVisible();
    await expect(page.getByText(headingRegex('Tienes 2 de 3 negocios')).first()).toBeVisible();
    await expect(page.getByRole('button', { name: headingRegex('Cancelar') }).first()).toBeVisible();
    await expect(page.getByRole('button', { name: headingRegex('Crear Negocio') }).first()).toBeVisible();

    const nombreInput = page.getByLabel(headingRegex('Nombre del Negocio')).first();
    await nombreInput.click();
    await nombreInput.fill('Negocio Prueba Automatización');

    await captureScreenshot(page, testInfo, 'checkpoint-03-agregar-negocio-modal');

    await page.getByRole('button', { name: headingRegex('Cancelar') }).first().click();
    await waitForUiAfterClick(page);
  });

  await runStep('Administrar Negocios view', async () => {
    const miNegocioOption = await getVisibleLocator(page, ['Mi Negocio']);
    if (!(await page.getByText(headingRegex('Administrar Negocios')).first().isVisible().catch(() => false))) {
      await miNegocioOption.click();
      await waitForUiAfterClick(page);
    }

    const administrarNegocios = await getVisibleLocator(page, ['Administrar Negocios']);
    await administrarNegocios.click();
    await waitForUiAfterClick(page);

    await expect(page.getByText(headingRegex('Información General')).first()).toBeVisible();
    await expect(page.getByText(headingRegex('Detalles de la Cuenta')).first()).toBeVisible();
    await expect(page.getByText(headingRegex('Tus Negocios')).first()).toBeVisible();
    await expect(page.getByText(headingRegex('Sección Legal')).first()).toBeVisible();

    await captureScreenshot(page, testInfo, 'checkpoint-04-administrar-negocios-full', true);
  });

  await runStep('Información General', async () => {
    const generalSection = page
      .locator('section, article, div')
      .filter({ has: page.getByText(headingRegex('Información General')).first() })
      .first();

    await expect(generalSection).toBeVisible();

    const sectionText = await generalSection.innerText();
    expect(
      sectionText,
      'Expected a visible user email inside Información General.',
    ).toMatch(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);

    const cleanedLines = sectionText
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter((line) => line.length > 0);
    const possibleName = cleanedLines.find(
      (line) =>
        !line.includes('@') &&
        !/información general|business plan|cambiar plan|cuenta creada|estado activo|idioma/i.test(line),
    );
    expect(possibleName, 'Expected a visible user name inside Información General.').toBeTruthy();

    await expect(page.getByText(headingRegex('BUSINESS PLAN')).first()).toBeVisible();
    await expect(page.getByRole('button', { name: headingRegex('Cambiar Plan') }).first()).toBeVisible();
  });

  await runStep('Detalles de la Cuenta', async () => {
    await expect(page.getByText(headingRegex('Cuenta creada')).first()).toBeVisible();
    await expect(page.getByText(headingRegex('Estado activo')).first()).toBeVisible();
    await expect(page.getByText(headingRegex('Idioma seleccionado')).first()).toBeVisible();
  });

  await runStep('Tus Negocios', async () => {
    const businessesSection = page
      .locator('section, article, div')
      .filter({ has: page.getByText(headingRegex('Tus Negocios')).first() })
      .first();

    await expect(businessesSection).toBeVisible();
    await expect(
      businessesSection.locator('ul li, [role="listitem"], [role="row"]').first(),
    ).toBeVisible();
    await expect(page.getByText(headingRegex('Agregar Negocio')).first()).toBeVisible();
    await expect(page.getByText(headingRegex('Tienes 2 de 3 negocios')).first()).toBeVisible();
  });

  await runStep('Términos y Condiciones', async () => {
    legalUrls['Términos y Condiciones'] = await validateLegalLink(
      page,
      context,
      testInfo,
      'Términos y Condiciones',
      'Términos y Condiciones',
    );
  });

  await runStep('Política de Privacidad', async () => {
    legalUrls['Política de Privacidad'] = await validateLegalLink(
      page,
      context,
      testInfo,
      'Política de Privacidad',
      'Política de Privacidad',
    );
  });

  const finalReport = {
    test_name: 'saleads_mi_negocio_full_test',
    results: report,
    evidence: {
      legal_urls: legalUrls,
    },
  };

  await testInfo.attach('final-report.json', {
    body: Buffer.from(JSON.stringify(finalReport, null, 2), 'utf-8'),
    contentType: 'application/json',
  });

  const failedFields = Object.entries(report)
    .filter(([, status]) => status === 'FAIL')
    .map(([field]) => field);

  expect(
    failedFields,
    `Final report contains failures in: ${failedFields.join(', ') || 'none'}`,
  ).toEqual([]);
});
