import { expect, Locator, Page, TestInfo, test } from '@playwright/test';

type StepStatus = 'PASS' | 'FAIL';

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

const EMAIL_REGEX = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;

const sleep = (ms: number): Promise<void> =>
  new Promise((resolve) => setTimeout(resolve, ms));

function createInitialReport(): Record<ReportField, StepStatus> {
  return REPORT_FIELDS.reduce(
    (acc, field) => ({ ...acc, [field]: 'FAIL' as StepStatus }),
    {} as Record<ReportField, StepStatus>,
  );
}

async function waitForUiToSettle(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded', { timeout: 15_000 }).catch(() => undefined);
  await page.waitForLoadState('networkidle', { timeout: 10_000 }).catch(() => undefined);
  await page.waitForTimeout(600);
}

async function captureCheckpoint(
  page: Page,
  testInfo: TestInfo,
  filename: string,
  fullPage = false,
): Promise<void> {
  await page.screenshot({
    path: testInfo.outputPath(filename),
    fullPage,
  });
}

async function findFirstVisible(candidates: Locator[]): Promise<Locator | null> {
  for (const candidate of candidates) {
    const target = candidate.first();
    if (await target.isVisible().catch(() => false)) {
      return target;
    }
  }

  return null;
}

async function requireVisibleLocator(
  candidates: Locator[],
  description: string,
  timeoutMs = 20_000,
): Promise<Locator> {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    const found = await findFirstVisible(candidates);
    if (found) {
      return found;
    }
    await sleep(500);
  }

  throw new Error(`Could not find visible element: ${description}.`);
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await locator.scrollIntoViewIfNeeded().catch(() => undefined);
  await locator.click();
  await waitForUiToSettle(page);
}

function byVisibleText(page: Page, pattern: RegExp): Locator[] {
  return [
    page.getByRole('button', { name: pattern }),
    page.getByRole('link', { name: pattern }),
    page.getByRole('menuitem', { name: pattern }),
    page.getByRole('tab', { name: pattern }),
    page.getByText(pattern),
  ];
}

async function openLegalLinkAndValidate(params: {
  page: Page;
  section: Locator;
  linkPattern: RegExp;
  headingPattern: RegExp;
  screenshotName: string;
  appReturnUrl: string;
  testInfo: TestInfo;
}): Promise<string> {
  const { page, section, linkPattern, headingPattern, screenshotName, appReturnUrl, testInfo } = params;
  const context = page.context();

  const link = await requireVisibleLocator(
    [
      section.getByRole('link', { name: linkPattern }),
      section.getByRole('button', { name: linkPattern }),
      section.getByText(linkPattern),
      ...byVisibleText(page, linkPattern),
    ],
    `legal link ${linkPattern.source}`,
  );

  const newTabPromise = context.waitForEvent('page', { timeout: 8_000 }).catch(() => null);
  await clickAndWait(link, page);
  const newTab = await newTabPromise;

  const targetPage = newTab ?? page;
  await waitForUiToSettle(targetPage);

  const heading = await requireVisibleLocator(
    [targetPage.getByRole('heading', { name: headingPattern }), targetPage.getByText(headingPattern)],
    `heading ${headingPattern.source}`,
  );
  await expect(heading).toBeVisible();

  const legalContent = (
    await targetPage.locator('main, article, body').first().innerText().catch(() => '')
  ).replace(/\s+/g, ' ');
  if (legalContent.trim().length < 80) {
    throw new Error(`Legal content for ${headingPattern.source} appears too short.`);
  }

  await captureCheckpoint(targetPage, testInfo, screenshotName, true);
  const finalUrl = targetPage.url();

  if (newTab) {
    await newTab.close();
    await page.bringToFront();
  } else if (page.url() !== appReturnUrl) {
    await page
      .goBack({ waitUntil: 'domcontentloaded' })
      .catch(async () => page.goto(appReturnUrl, { waitUntil: 'domcontentloaded' }));
    await waitForUiToSettle(page);
  }

  return finalUrl;
}

test('saleads_mi_negocio_full_test', async ({ page }, testInfo) => {
  const report = createInitialReport();
  const failures: string[] = [];

  let termsUrl = '';
  let privacyUrl = '';
  let accountPageUrl = '';

  const runStep = async (field: ReportField, action: () => Promise<void>): Promise<void> => {
    try {
      await test.step(field, action);
      report[field] = 'PASS';
    } catch (error) {
      report[field] = 'FAIL';
      failures.push(`${field}: ${error instanceof Error ? error.message : String(error)}`);
    }
  };

  const requestedLoginUrl = process.env.SALEADS_LOGIN_URL;
  if (requestedLoginUrl) {
    await page.goto(requestedLoginUrl, { waitUntil: 'domcontentloaded' });
  }
  await waitForUiToSettle(page);

  await runStep('Login', async () => {
    const sidebarAlreadyVisible = await findFirstVisible([
      page.locator('aside').filter({ hasText: /Negocio/i }),
      page.locator('nav').filter({ hasText: /Negocio/i }),
      page.getByText(/^Negocio$/i),
    ]);

    if (!sidebarAlreadyVisible) {
      const googleLoginButton = await requireVisibleLocator(
        [
          ...byVisibleText(page, /sign in with google|iniciar sesión con google|continuar con google/i),
          ...byVisibleText(page, /google/i),
        ],
        'Google login button',
      );

      const popupPromise = page.context().waitForEvent('page', { timeout: 12_000 }).catch(() => null);
      await clickAndWait(googleLoginButton, page);
      const popup = await popupPromise;

      if (popup) {
        await waitForUiToSettle(popup);
        const accountOption = await requireVisibleLocator(
          [
            popup.getByText(/juanlucasbarbiergarzon@gmail\.com/i),
            popup.getByRole('button', { name: /juanlucasbarbiergarzon@gmail\.com/i }),
            popup.getByRole('link', { name: /juanlucasbarbiergarzon@gmail\.com/i }),
          ],
          'Google account selector',
          18_000,
        );
        await clickAndWait(accountOption, popup);
        await page.bringToFront();
      }
    }

    const sidebar = await requireVisibleLocator(
      [page.locator('aside'), page.locator('nav').filter({ hasText: /Negocio/i }), page.getByText(/Negocio/i)],
      'left sidebar navigation',
    );
    await expect(sidebar).toBeVisible();

    await captureCheckpoint(page, testInfo, '01-dashboard-loaded.png');
  });

  await runStep('Mi Negocio menu', async () => {
    const negocio = await requireVisibleLocator(byVisibleText(page, /^Negocio$/i), 'Negocio menu');
    await clickAndWait(negocio, page);

    const miNegocio = await requireVisibleLocator(byVisibleText(page, /^Mi Negocio$/i), 'Mi Negocio submenu');
    await clickAndWait(miNegocio, page);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

    await captureCheckpoint(page, testInfo, '02-mi-negocio-expanded.png');
  });

  await runStep('Agregar Negocio modal', async () => {
    const agregarNegocio = await requireVisibleLocator(
      byVisibleText(page, /^Agregar Negocio$/i),
      'Agregar Negocio option',
    );
    await clickAndWait(agregarNegocio, page);

    const modalTitle = await requireVisibleLocator(
      [page.getByRole('heading', { name: /Crear Nuevo Negocio/i }), page.getByText(/Crear Nuevo Negocio/i)],
      'Crear Nuevo Negocio modal title',
    );
    await expect(modalTitle).toBeVisible();

    const businessNameInput = await requireVisibleLocator(
      [
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.locator('input[name*="negocio" i]'),
        page.locator('input[placeholder*="negocio" i]'),
      ],
      'Nombre del Negocio input',
    );
    await expect(businessNameInput).toBeVisible();
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();

    const cancelButton = await requireVisibleLocator(
      byVisibleText(page, /^Cancelar$/i),
      'Cancelar button in modal',
    );
    const createButton = await requireVisibleLocator(
      byVisibleText(page, /^Crear Negocio$/i),
      'Crear Negocio button in modal',
    );
    await expect(cancelButton).toBeVisible();
    await expect(createButton).toBeVisible();

    await captureCheckpoint(page, testInfo, '03-agregar-negocio-modal.png');

    await businessNameInput.click();
    await waitForUiToSettle(page);
    await businessNameInput.fill('Negocio Prueba Automatización');
    await waitForUiToSettle(page);
    await clickAndWait(cancelButton, page);

    await expect(modalTitle).toBeHidden({ timeout: 10_000 });
  });

  await runStep('Administrar Negocios view', async () => {
    const administrarVisible = await findFirstVisible([page.getByText(/^Administrar Negocios$/i)]);
    if (!administrarVisible) {
      const miNegocio = await requireVisibleLocator(byVisibleText(page, /^Mi Negocio$/i), 'Mi Negocio toggle');
      await clickAndWait(miNegocio, page);
    }

    const administrarNegocios = await requireVisibleLocator(
      byVisibleText(page, /^Administrar Negocios$/i),
      'Administrar Negocios option',
    );
    await clickAndWait(administrarNegocios, page);

    await expect(page.getByText(/Información General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();

    accountPageUrl = page.url();
    await captureCheckpoint(page, testInfo, '04-administrar-negocios.png', true);
  });

  await runStep('Información General', async () => {
    const section = await requireVisibleLocator(
      [page.locator('section, div').filter({ hasText: /Información General/i }), page.getByText(/Información General/i)],
      'Información General section',
    );

    const sectionText = await section.innerText();
    const emailMatch = sectionText.match(EMAIL_REGEX);
    if (!emailMatch) {
      throw new Error('Expected a user email in Información General.');
    }

    const lines = sectionText
      .split('\n')
      .map((line) => line.trim())
      .filter(Boolean);
    const userNameLine = lines.find(
      (line) =>
        !EMAIL_REGEX.test(line) &&
        !/información general|business plan|cambiar plan/i.test(line) &&
        line.length > 2,
    );
    if (!userNameLine) {
      throw new Error('Expected a user name in Información General.');
    }

    await expect(section.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(section.getByText(/Cambiar Plan/i)).toBeVisible();
  });

  await runStep('Detalles de la Cuenta', async () => {
    const section = await requireVisibleLocator(
      [page.locator('section, div').filter({ hasText: /Detalles de la Cuenta/i }), page.getByText(/Detalles de la Cuenta/i)],
      'Detalles de la Cuenta section',
    );

    await expect(section.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(section.getByText(/Estado activo/i)).toBeVisible();
    await expect(section.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runStep('Tus Negocios', async () => {
    const section = await requireVisibleLocator(
      [page.locator('section, div').filter({ hasText: /Tus Negocios/i }), page.getByText(/Tus Negocios/i)],
      'Tus Negocios section',
    );

    await expect(section.getByText(/Agregar Negocio/i)).toBeVisible();
    await expect(section.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();

    const itemRows = section.locator('li, tr, [data-testid*="business"], [class*="business"]');
    const rowsCount = await itemRows.count();
    const sectionText = await section.innerText();
    if (rowsCount === 0 && !/negocio/i.test(sectionText)) {
      throw new Error('Business list was not detected in Tus Negocios.');
    }
  });

  await runStep('Términos y Condiciones', async () => {
    const legalSection = await requireVisibleLocator(
      [page.locator('section, div').filter({ hasText: /Sección Legal/i }), page.getByText(/Sección Legal/i)],
      'Sección Legal section',
    );

    termsUrl = await openLegalLinkAndValidate({
      page,
      section: legalSection,
      linkPattern: /Términos y Condiciones/i,
      headingPattern: /Términos y Condiciones/i,
      screenshotName: '05-terminos-y-condiciones.png',
      appReturnUrl: accountPageUrl || page.url(),
      testInfo,
    });
  });

  await runStep('Política de Privacidad', async () => {
    const legalSection = await requireVisibleLocator(
      [page.locator('section, div').filter({ hasText: /Sección Legal/i }), page.getByText(/Sección Legal/i)],
      'Sección Legal section',
    );

    privacyUrl = await openLegalLinkAndValidate({
      page,
      section: legalSection,
      linkPattern: /Política de Privacidad/i,
      headingPattern: /Política de Privacidad/i,
      screenshotName: '06-politica-de-privacidad.png',
      appReturnUrl: accountPageUrl || page.url(),
      testInfo,
    });
  });

  const finalReport = {
    test_name: 'saleads_mi_negocio_full_test',
    generated_at: new Date().toISOString(),
    results: report,
    evidence: {
      screenshots: [
        '01-dashboard-loaded.png',
        '02-mi-negocio-expanded.png',
        '03-agregar-negocio-modal.png',
        '04-administrar-negocios.png',
        '05-terminos-y-condiciones.png',
        '06-politica-de-privacidad.png',
      ],
      final_urls: {
        terminos_y_condiciones: termsUrl || 'NOT_CAPTURED',
        politica_de_privacidad: privacyUrl || 'NOT_CAPTURED',
      },
    },
    failures,
  };

  await testInfo.attach('saleads-mi-negocio-final-report', {
    body: Buffer.from(JSON.stringify(finalReport, null, 2), 'utf-8'),
    contentType: 'application/json',
  });

  // Helps when running through CI logs.
  console.log('SALEADS_MI_NEGOCIO_FINAL_REPORT');
  console.log(JSON.stringify(finalReport, null, 2));

  expect(
    failures,
    `One or more validation steps failed:\n${failures.map((entry) => `- ${entry}`).join('\n')}`,
  ).toEqual([]);
});
