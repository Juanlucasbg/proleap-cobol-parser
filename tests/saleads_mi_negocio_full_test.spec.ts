import { expect, Locator, Page, TestInfo, test } from '@playwright/test';

const GOOGLE_EMAIL = 'juanlucasbarbiergarzon@gmail.com';
const SCREENSHOT_DIR = 'saleads-mi-negocio';

type ReportKey =
  | 'Login'
  | 'Mi Negocio menu'
  | 'Agregar Negocio modal'
  | 'Administrar Negocios view'
  | 'Información General'
  | 'Detalles de la Cuenta'
  | 'Tus Negocios'
  | 'Términos y Condiciones'
  | 'Política de Privacidad';

type Report = Record<ReportKey, 'PASS' | 'FAIL'>;

function createReport(): Report {
  return {
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
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(800);
}

async function captureCheckpoint(testInfo: TestInfo, page: Page, fileName: string, fullPage = false): Promise<void> {
  const targetPath = testInfo.outputPath(`${SCREENSHOT_DIR}/${fileName}.png`);
  await page.screenshot({ path: targetPath, fullPage });
  await testInfo.attach(fileName, { path: targetPath, contentType: 'image/png' });
}

async function clickFirstVisible(page: Page, locators: Locator[]): Promise<Locator> {
  for (const locator of locators) {
    if (await locator.first().isVisible().catch(() => false)) {
      await locator.first().click();
      await waitForUi(page);
      return locator.first();
    }
  }
  throw new Error('No visible clickable locator found.');
}

async function ensureSidebarVisible(page: Page): Promise<void> {
  const sidebarCandidates = [
    page.locator('aside'),
    page.locator('nav').filter({ hasText: /negocio|mi negocio/i }),
    page.locator('[class*="sidebar"]'),
  ];

  for (const candidate of sidebarCandidates) {
    if (await candidate.first().isVisible().catch(() => false)) {
      return;
    }
  }

  await expect(page.getByText(/negocio|mi negocio/i).first()).toBeVisible({ timeout: 45_000 });
}

async function openMiNegocioMenu(page: Page): Promise<void> {
  if (await page.getByText('Agregar Negocio', { exact: true }).isVisible().catch(() => false)) {
    return;
  }

  await clickFirstVisible(page, [
    page.getByRole('button', { name: /^Mi Negocio$/i }),
    page.getByRole('link', { name: /^Mi Negocio$/i }),
    page.getByText(/^Mi Negocio$/i),
  ]);
}

async function validateLegalDocument(
  page: Page,
  testInfo: TestInfo,
  linkText: string,
  headingRegex: RegExp,
  screenshotName: string,
): Promise<string> {
  const popupPromise = page.context().waitForEvent('page', { timeout: 8_000 }).catch(() => null);
  const currentUrl = page.url();

  await clickFirstVisible(page, [
    page.getByRole('link', { name: new RegExp(`^${linkText}$`, 'i') }),
    page.getByText(new RegExp(`^${linkText}$`, 'i')),
  ]);

  const popup = await popupPromise;
  let targetPage = page;

  if (popup) {
    await popup.waitForLoadState('domcontentloaded');
    targetPage = popup;
  } else if (page.url() === currentUrl) {
    await page.waitForURL((url) => url.toString() !== currentUrl, { timeout: 12_000 }).catch(() => undefined);
  }

  await expect(targetPage.getByRole('heading', { name: headingRegex }).first()).toBeVisible({ timeout: 20_000 });

  const legalText = await targetPage.locator('body').innerText();
  expect(legalText.replace(/\s+/g, ' ').trim().length).toBeGreaterThan(120);

  await captureCheckpoint(testInfo, targetPage, screenshotName, true);
  const finalUrl = targetPage.url();
  await testInfo.attach(`${screenshotName}-url.txt`, {
    body: Buffer.from(finalUrl, 'utf-8'),
    contentType: 'text/plain',
  });

  if (targetPage !== page) {
    await targetPage.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: 'domcontentloaded' });
    await waitForUi(page);
  }

  return finalUrl;
}

test('saleads_mi_negocio_full_test', async ({ page }, testInfo) => {
  const report = createReport();
  const failures: string[] = [];

  const runStep = async (field: ReportKey, fn: () => Promise<void>): Promise<void> => {
    try {
      await fn();
      report[field] = 'PASS';
    } catch (error) {
      report[field] = 'FAIL';
      failures.push(`${field}: ${error instanceof Error ? error.message : String(error)}`);
    }
  };

  const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL;
  if (page.url() === 'about:blank' && loginUrl) {
    await page.goto(loginUrl, { waitUntil: 'domcontentloaded' });
    await waitForUi(page);
  }

  await runStep('Login', async () => {
    if (page.url() === 'about:blank') {
      throw new Error('Missing login page. Set SALEADS_LOGIN_URL or SALEADS_BASE_URL before running the test.');
    }

    const loginPopupPromise = page.context().waitForEvent('page', { timeout: 8_000 }).catch(() => null);
    await clickFirstVisible(page, [
      page.getByRole('button', { name: /google|sign in|iniciar sesi[oó]n/i }),
      page.getByRole('link', { name: /google|sign in|iniciar sesi[oó]n/i }),
      page.getByText(/google|sign in|iniciar sesi[oó]n/i),
    ]);

    const loginPopup = await loginPopupPromise;
    if (loginPopup) {
      await loginPopup.waitForLoadState('domcontentloaded');
      if (await loginPopup.getByText(GOOGLE_EMAIL, { exact: true }).isVisible().catch(() => false)) {
        await loginPopup.getByText(GOOGLE_EMAIL, { exact: true }).click();
      }
      await loginPopup.waitForEvent('close', { timeout: 30_000 }).catch(() => undefined);
    } else if (await page.getByText(GOOGLE_EMAIL, { exact: true }).isVisible().catch(() => false)) {
      await page.getByText(GOOGLE_EMAIL, { exact: true }).click();
      await waitForUi(page);
    }

    await ensureSidebarVisible(page);
    await captureCheckpoint(testInfo, page, '01-dashboard-loaded');
  });

  await runStep('Mi Negocio menu', async () => {
    await clickFirstVisible(page, [
      page.getByRole('button', { name: /^Negocio$/i }),
      page.getByRole('link', { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i),
    ]);

    await openMiNegocioMenu(page);
    await expect(page.getByText('Agregar Negocio', { exact: true })).toBeVisible();
    await expect(page.getByText('Administrar Negocios', { exact: true })).toBeVisible();
    await captureCheckpoint(testInfo, page, '02-mi-negocio-expanded');
  });

  await runStep('Agregar Negocio modal', async () => {
    await clickFirstVisible(page, [
      page.getByRole('button', { name: /^Agregar Negocio$/i }),
      page.getByRole('link', { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);

    await expect(page.getByRole('heading', { name: 'Crear Nuevo Negocio' })).toBeVisible();
    await expect(page.getByLabel('Nombre del Negocio')).toBeVisible();
    await expect(page.getByText('Tienes 2 de 3 negocios', { exact: false })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Cancelar' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Crear Negocio' })).toBeVisible();

    await page.getByLabel('Nombre del Negocio').fill('Negocio Prueba Automatización');
    await clickFirstVisible(page, [page.getByRole('button', { name: 'Cancelar' })]);
    await captureCheckpoint(testInfo, page, '03-agregar-negocio-modal');
  });

  await runStep('Administrar Negocios view', async () => {
    await openMiNegocioMenu(page);
    await clickFirstVisible(page, [
      page.getByRole('button', { name: /^Administrar Negocios$/i }),
      page.getByRole('link', { name: /^Administrar Negocios$/i }),
      page.getByText(/^Administrar Negocios$/i),
    ]);

    await expect(page.getByRole('heading', { name: /Informaci[oó]n General/i })).toBeVisible();
    await expect(page.getByRole('heading', { name: /Detalles de la Cuenta/i })).toBeVisible();
    await expect(page.getByRole('heading', { name: /Tus Negocios/i })).toBeVisible();
    await expect(page.getByRole('heading', { name: /Secci[oó]n Legal/i })).toBeVisible();
    await captureCheckpoint(testInfo, page, '04-administrar-negocios-page', true);
  });

  await runStep('Información General', async () => {
    const infoSection = page.locator('section,div').filter({ hasText: /Informaci[oó]n General/i }).first();
    await expect(infoSection).toBeVisible();
    const infoText = await infoSection.innerText();

    const hasSpecificEmail = infoText.includes(GOOGLE_EMAIL);
    const hasAnyEmail = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/.test(infoText);

    expect(hasSpecificEmail || hasAnyEmail).toBeTruthy();
    await expect(page.getByText('BUSINESS PLAN', { exact: false })).toBeVisible();
    await expect(page.getByRole('button', { name: /Cambiar Plan/i })).toBeVisible();

    const nonHeadingText = infoText
      .split('\n')
      .map((line) => line.trim())
      .filter((line) => line.length > 0 && !/informaci[oó]n general/i.test(line));
    expect(nonHeadingText.length).toBeGreaterThan(1);
  });

  await runStep('Detalles de la Cuenta', async () => {
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runStep('Tus Negocios', async () => {
    const businessesSection = page.locator('section,div').filter({ hasText: /Tus Negocios/i }).first();
    await expect(businessesSection).toBeVisible();
    await expect(page.getByRole('button', { name: /^Agregar Negocio$/i }).first()).toBeVisible();
    await expect(page.getByText('Tienes 2 de 3 negocios', { exact: false }).first()).toBeVisible();
  });

  await runStep('Términos y Condiciones', async () => {
    await validateLegalDocument(
      page,
      testInfo,
      'Términos y Condiciones',
      /T[eé]rminos y Condiciones/i,
      '05-terminos-condiciones',
    );
  });

  await runStep('Política de Privacidad', async () => {
    await validateLegalDocument(
      page,
      testInfo,
      'Política de Privacidad',
      /Pol[ií]tica de Privacidad/i,
      '06-politica-privacidad',
    );
  });

  await testInfo.attach('final-report.json', {
    body: Buffer.from(JSON.stringify(report, null, 2), 'utf-8'),
    contentType: 'application/json',
  });

  expect(
    failures,
    `Validation failures:\n${failures.map((failure, index) => `${index + 1}. ${failure}`).join('\n')}`,
  ).toEqual([]);
});
