import { expect, test, type BrowserContext, type Locator, type Page } from '@playwright/test';

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

type ReportEntry = {
  status: 'PASS' | 'FAIL';
  details?: string;
};

const REPORT_ORDER: ReportField[] = [
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

const DEPENDENCIES: Record<ReportField, ReportField[]> = {
  Login: [],
  'Mi Negocio menu': ['Login'],
  'Agregar Negocio modal': ['Mi Negocio menu'],
  'Administrar Negocios view': ['Mi Negocio menu'],
  'Información General': ['Administrar Negocios view'],
  'Detalles de la Cuenta': ['Administrar Negocios view'],
  'Tus Negocios': ['Administrar Negocios view'],
  'Términos y Condiciones': ['Administrar Negocios view'],
  'Política de Privacidad': ['Administrar Negocios view'],
};

function newReport(): Record<ReportField, ReportEntry> {
  return REPORT_ORDER.reduce(
    (acc, item) => {
      acc[item] = { status: 'FAIL', details: 'Not executed' };
      return acc;
    },
    {} as Record<ReportField, ReportEntry>,
  );
}

function reportToText(report: Record<ReportField, ReportEntry>): string {
  return REPORT_ORDER.map((key) => `${key}: ${report[key].status}${report[key].details ? ` (${report[key].details})` : ''}`).join(
    '\n',
  );
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(400);
  try {
    await page.waitForLoadState('networkidle', { timeout: 12_000 });
  } catch {
    // Keep running when apps keep persistent network connections.
  }
}

async function clickVisibleByText(scope: Page | Locator, label: string | RegExp): Promise<void> {
  const matcher = typeof label === 'string' ? new RegExp(label, 'i') : label;
  const candidates = [
    scope.getByRole('button', { name: matcher }).first(),
    scope.getByRole('link', { name: matcher }).first(),
    scope.getByRole('menuitem', { name: matcher }).first(),
    scope.getByRole('tab', { name: matcher }).first(),
    scope.getByText(matcher).first(),
  ];

  for (const candidate of candidates) {
    if (await candidate.isVisible().catch(() => false)) {
      await candidate.click();
      return;
    }
  }

  throw new Error(`Could not find clickable element with visible text: ${matcher}`);
}

async function sectionByHeading(page: Page, headingText: string | RegExp): Promise<Locator> {
  const heading = page.getByRole('heading', { name: headingText }).first();
  await expect(heading).toBeVisible();
  return page.locator('section,article,div').filter({ has: heading }).first();
}

async function captureCheckpoint(page: Page, fileName: string, fullPage = true): Promise<void> {
  await page.screenshot({ path: test.info().outputPath(fileName), fullPage });
}

async function openLegalDocumentAndReturn(
  context: BrowserContext,
  appPage: Page,
  legalSection: Locator,
  linkText: string | RegExp,
  headingText: string | RegExp,
  screenshotName: string,
): Promise<string> {
  const popupPromise = context.waitForEvent('page', { timeout: 8_000 }).catch(() => null);
  await clickVisibleByText(legalSection, linkText);

  let targetPage: Page = appPage;
  const popup = await popupPromise;

  if (popup) {
    targetPage = popup;
    await popup.waitForLoadState('domcontentloaded');
    await popup.bringToFront();
  }

  await waitForUi(targetPage);
  await expect(targetPage.getByRole('heading', { name: headingText }).first()).toBeVisible();

  const legalText = (await targetPage.locator('body').innerText()).replace(/\s+/g, ' ').trim();
  expect(legalText.length).toBeGreaterThan(300);

  await targetPage.screenshot({ path: test.info().outputPath(screenshotName), fullPage: true });
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: 'domcontentloaded' }).catch(() => undefined);
    await waitForUi(appPage);
  }

  return finalUrl;
}

function firstLine(error: unknown): string {
  if (error instanceof Error && error.message) {
    return error.message.split('\n')[0];
  }
  return String(error);
}

test('saleads_mi_negocio_full_test', async ({ page, context }) => {
  const report = newReport();
  const evidence: { terminosUrl?: string; privacidadUrl?: string } = {};

  const runStep = async (name: ReportField, stepBody: () => Promise<void>) => {
    const blockedBy = DEPENDENCIES[name].filter((dependency) => report[dependency].status !== 'PASS');
    if (blockedBy.length > 0) {
      report[name] = {
        status: 'FAIL',
        details: `Blocked by: ${blockedBy.join(', ')}`,
      };
      return;
    }

    try {
      await stepBody();
      report[name] = { status: 'PASS' };
    } catch (error) {
      report[name] = {
        status: 'FAIL',
        details: firstLine(error),
      };
    }
  };

  await runStep('Login', async () => {
    const baseUrl = test.info().project.use.baseURL as string | undefined;
    if (baseUrl) {
      await page.goto(baseUrl, { waitUntil: 'domcontentloaded' });
    } else if (page.url() === 'about:blank') {
      throw new Error('No environment URL configured. Set SALEADS_BASE_URL (or BASE_URL/APP_URL).');
    }
    await waitForUi(page);

    const popupPromise = context.waitForEvent('page', { timeout: 10_000 }).catch(() => null);
    await clickVisibleByText(page, /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i);
    await waitForUi(page);

    const googlePopup = await popupPromise;
    const accountEmail = 'juanlucasbarbiergarzon@gmail.com';

    if (googlePopup) {
      await googlePopup.waitForLoadState('domcontentloaded');
      const accountOption = googlePopup.getByText(accountEmail).first();
      if (await accountOption.isVisible({ timeout: 10_000 }).catch(() => false)) {
        await accountOption.click();
      }
      await waitForUi(googlePopup);
    } else {
      const accountOption = page.getByText(accountEmail).first();
      if (await accountOption.isVisible({ timeout: 6_000 }).catch(() => false)) {
        await accountOption.click();
      }
    }

    await expect(page.locator('aside, nav').filter({ hasText: /Negocio/i }).first()).toBeVisible({
      timeout: 45_000,
    });
    await captureCheckpoint(page, '01-dashboard-loaded.png');
  });

  await runStep('Mi Negocio menu', async () => {
    await expect(page.locator('aside, nav').first()).toBeVisible();
    await clickVisibleByText(page, /Mi Negocio/i);
    await waitForUi(page);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
    await captureCheckpoint(page, '02-mi-negocio-menu-expanded.png');
  });

  await runStep('Agregar Negocio modal', async () => {
    await clickVisibleByText(page, /^Agregar Negocio$/i);
    await waitForUi(page);

    const modalTitle = page.getByRole('heading', { name: /Crear Nuevo Negocio/i }).first();
    await expect(modalTitle).toBeVisible();

    let nombreInput = page.getByLabel(/Nombre del Negocio/i).first();
    if (!(await nombreInput.isVisible().catch(() => false))) {
      nombreInput = page.getByPlaceholder(/Nombre del Negocio/i).first();
    }
    await expect(nombreInput).toBeVisible();

    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /Cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /Crear Negocio/i }).first()).toBeVisible();

    await captureCheckpoint(page, '03-agregar-negocio-modal.png');

    await nombreInput.click();
    await nombreInput.fill('Negocio Prueba Automatización');
    await clickVisibleByText(page, /^Cancelar$/i);
    await waitForUi(page);
  });

  await runStep('Administrar Negocios view', async () => {
    const administrarVisible = await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      await clickVisibleByText(page, /Mi Negocio/i);
      await waitForUi(page);
    }

    await clickVisibleByText(page, /Administrar Negocios/i);
    await waitForUi(page);

    await expect(page.getByRole('heading', { name: /Información General/i }).first()).toBeVisible();
    await expect(page.getByRole('heading', { name: /Detalles de la Cuenta/i }).first()).toBeVisible();
    await expect(page.getByRole('heading', { name: /Tus Negocios/i }).first()).toBeVisible();
    await expect(page.getByRole('heading', { name: /Sección Legal/i }).first()).toBeVisible();

    await captureCheckpoint(page, '04-administrar-negocios-page.png');
  });

  await runStep('Información General', async () => {
    const generalSection = await sectionByHeading(page, /Información General/i);
    const sectionText = await generalSection.innerText();

    expect(sectionText).toMatch(/BUSINESS PLAN/i);
    await expect(page.getByRole('button', { name: /Cambiar Plan/i }).first()).toBeVisible();
    expect(sectionText).toMatch(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);

    const nameCandidate = sectionText
      .split('\n')
      .map((line) => line.trim())
      .filter((line) => line.length >= 3)
      .filter((line) => !/@/.test(line))
      .filter((line) => !/información general|business plan|cambiar plan/i.test(line))
      .find((line) => /^[A-Za-zÀ-ÿ' -]+$/.test(line));

    expect(nameCandidate, 'No visible user name candidate found').toBeTruthy();
  });

  await runStep('Detalles de la Cuenta', async () => {
    const detailsSection = await sectionByHeading(page, /Detalles de la Cuenta/i);
    const text = await detailsSection.innerText();
    expect(text).toMatch(/Cuenta creada/i);
    expect(text).toMatch(/Estado activo/i);
    expect(text).toMatch(/Idioma seleccionado/i);
  });

  await runStep('Tus Negocios', async () => {
    const businessSection = await sectionByHeading(page, /Tus Negocios/i);
    await expect(businessSection).toBeVisible();

    await expect(businessSection.getByRole('button', { name: /Agregar Negocio/i }).first()).toBeVisible();
    await expect(businessSection.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();

    const text = (await businessSection.innerText()).replace(/\s+/g, ' ').trim();
    expect(text.length).toBeGreaterThan(40);
  });

  await runStep('Términos y Condiciones', async () => {
    const legalSection = await sectionByHeading(page, /Sección Legal/i);
    evidence.terminosUrl = await openLegalDocumentAndReturn(
      context,
      page,
      legalSection,
      /Términos y Condiciones/i,
      /Términos y Condiciones/i,
      '05-terminos-y-condiciones.png',
    );
  });

  await runStep('Política de Privacidad', async () => {
    const legalSection = await sectionByHeading(page, /Sección Legal/i);
    evidence.privacidadUrl = await openLegalDocumentAndReturn(
      context,
      page,
      legalSection,
      /Política de Privacidad/i,
      /Política de Privacidad/i,
      '06-politica-de-privacidad.png',
    );
  });

  const reportText = reportToText(report);
  const finalArtifact = JSON.stringify(
    {
      report,
      evidence,
    },
    null,
    2,
  );

  test.info().annotations.push({ type: 'Final Report', description: reportText });
  await test.info().attach('final-report.json', {
    body: Buffer.from(finalArtifact, 'utf8'),
    contentType: 'application/json',
  });

  console.log('\nFinal validation report:\n');
  for (const field of REPORT_ORDER) {
    const statusLine = `${field.padEnd(28, ' ')} ${report[field].status}${report[field].details ? ` - ${report[field].details}` : ''}`;
    console.log(statusLine);
  }
  if (evidence.terminosUrl) {
    console.log(`Términos y Condiciones URL: ${evidence.terminosUrl}`);
  }
  if (evidence.privacidadUrl) {
    console.log(`Política de Privacidad URL: ${evidence.privacidadUrl}`);
  }

  const failedSteps = REPORT_ORDER.filter((field) => report[field].status === 'FAIL');
  expect(
    failedSteps,
    `One or more workflow validations failed.\n${reportText}\nTérminos URL: ${evidence.terminosUrl ?? 'N/A'}\nPrivacidad URL: ${
      evidence.privacidadUrl ?? 'N/A'
    }`,
  ).toHaveLength(0);
});
