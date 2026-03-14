import { expect, test, type BrowserContext, type Locator, type Page, type TestInfo } from '@playwright/test';

const UI_WAIT_MS = 800;
const SHORT_TIMEOUT_MS = 3_000;
const STEP_TIMEOUT_MS = 20_000;
const LONG_TIMEOUT_MS = 60_000;
const GOOGLE_ACCOUNT_EMAIL = 'juanlucasbarbiergarzon@gmail.com';

const LOGIN_URL_ENV_VARS = ['SALEADS_LOGIN_URL', 'SALEADS_URL', 'SALEADS_BASE_URL', 'BASE_URL'] as const;

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

type ReportField = (typeof REPORT_FIELDS)[number];
type StepStatus = 'PASS' | 'FAIL';
type FinalReport = Record<ReportField, StepStatus>;

function resolveLoginUrl(): string {
  for (const envVarName of LOGIN_URL_ENV_VARS) {
    const value = process.env[envVarName];
    if (value) {
      return value;
    }
  }

  throw new Error(
    `No login URL configured. Set one of: ${LOGIN_URL_ENV_VARS.join(', ')}. ` +
      'The test is environment-agnostic and does not hardcode domains.',
  );
}

function asErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(UI_WAIT_MS);
}

async function isVisible(locator: Locator, timeout = SHORT_TIMEOUT_MS): Promise<boolean> {
  try {
    await locator.first().waitFor({ state: 'visible', timeout });
    return true;
  } catch {
    return false;
  }
}

async function clickLocator(locator: Locator, page: Page): Promise<void> {
  await expect(locator.first()).toBeVisible({ timeout: STEP_TIMEOUT_MS });
  await locator.first().click();
  await waitForUi(page);
}

async function clickByVisibleText(page: Page, matcher: string | RegExp): Promise<void> {
  const textLocator = typeof matcher === 'string' ? page.getByText(matcher, { exact: true }) : page.getByText(matcher);
  const candidates: Locator[] = [
    page.getByRole('button', { name: matcher }).first(),
    page.getByRole('link', { name: matcher }).first(),
    textLocator.first(),
  ];

  for (const candidate of candidates) {
    if (await isVisible(candidate)) {
      await candidate.click();
      await waitForUi(page);
      return;
    }
  }

  throw new Error(`Could not find a visible element with text matcher: ${String(matcher)}`);
}

async function clickSidebarOption(page: Page, matcher: string | RegExp): Promise<void> {
  const sidebar = page.locator('aside, nav').first();
  if (!(await isVisible(sidebar, STEP_TIMEOUT_MS))) {
    throw new Error('Sidebar navigation is not visible.');
  }

  const textLocator = typeof matcher === 'string' ? sidebar.getByText(matcher, { exact: true }) : sidebar.getByText(matcher);
  const sidebarCandidates: Locator[] = [
    sidebar.getByRole('button', { name: matcher }).first(),
    sidebar.getByRole('link', { name: matcher }).first(),
    textLocator.first(),
  ];

  for (const candidate of sidebarCandidates) {
    if (await isVisible(candidate)) {
      await candidate.click();
      await waitForUi(page);
      return;
    }
  }

  await clickByVisibleText(page, matcher);
}

async function checkpoint(page: Page, testInfo: TestInfo, name: string): Promise<void> {
  const path = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path, fullPage: true });
  await testInfo.attach(name, { path, contentType: 'image/png' });
}

async function openAndValidateLegalPage(
  page: Page,
  context: BrowserContext,
  testInfo: TestInfo,
  linkText: string,
  headingPattern: RegExp,
  screenshotName: string,
): Promise<string> {
  const newPagePromise = context.waitForEvent('page', { timeout: 7_000 }).catch(() => null);
  await clickByVisibleText(page, new RegExp(`^${escapeRegExp(linkText)}$`, 'i'));

  const popupPage = await newPagePromise;
  const targetPage = popupPage ?? page;
  await targetPage.waitForLoadState('domcontentloaded');
  await targetPage.waitForTimeout(UI_WAIT_MS);

  await expect(targetPage.getByText(headingPattern).first()).toBeVisible({ timeout: STEP_TIMEOUT_MS });
  const legalBody = (await targetPage.locator('body').innerText()).replace(/\s+/g, ' ').trim();
  expect(legalBody.length).toBeGreaterThan(200);

  await checkpoint(targetPage, testInfo, screenshotName);
  const finalUrl = targetPage.url();

  if (popupPage) {
    await popupPage.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => null);
    await waitForUi(page);
  }

  return finalUrl;
}

test('saleads_mi_negocio_full_test', async ({ context, page }, testInfo) => {
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, 'FAIL'])) as FinalReport;
  const failures: string[] = [];
  const legalUrls: { termsAndConditions?: string; privacyPolicy?: string } = {};

  const runStep = async (field: ReportField, action: () => Promise<void>): Promise<void> => {
    try {
      await action();
      report[field] = 'PASS';
    } catch (error) {
      report[field] = 'FAIL';
      failures.push(`${field}: ${asErrorMessage(error)}`);
    }
  };

  await runStep('Login', async () => {
    await page.goto(resolveLoginUrl(), { waitUntil: 'domcontentloaded' });
    await waitForUi(page);

    const googleMatcher = /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i;
    const directGoogleButtonVisible =
      (await isVisible(page.getByRole('button', { name: googleMatcher }).first(), 5_000)) ||
      (await isVisible(page.getByRole('link', { name: googleMatcher }).first(), 5_000)) ||
      (await isVisible(page.getByText(googleMatcher).first(), 5_000));

    const popupPromise = page.waitForEvent('popup', { timeout: 10_000 }).catch(() => null);

    if (directGoogleButtonVisible) {
      await clickByVisibleText(page, googleMatcher);
    } else {
      await clickByVisibleText(page, /iniciar sesi[oó]n|sign in|login|acceder/i);
      await clickByVisibleText(page, googleMatcher);
    }

    const popup = await popupPromise;
    const authPage = popup ?? page;
    const accountSelector = authPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();

    if (await isVisible(accountSelector, 12_000)) {
      await accountSelector.click();
      if (popup) {
        await popup.waitForEvent('close', { timeout: 120_000 }).catch(() => null);
      }
    }

    await waitForUi(page);
    await expect(page.locator('aside, nav').first()).toBeVisible({ timeout: LONG_TIMEOUT_MS });
    await expect(page.getByText(/mi negocio|negocio/i).first()).toBeVisible({ timeout: LONG_TIMEOUT_MS });
    await checkpoint(page, testInfo, '01-dashboard-loaded');
  });

  await runStep('Mi Negocio menu', async () => {
    await clickSidebarOption(page, /^Negocio$/i);
    await clickSidebarOption(page, /^Mi Negocio$/i);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible({ timeout: STEP_TIMEOUT_MS });
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible({ timeout: STEP_TIMEOUT_MS });
    await checkpoint(page, testInfo, '02-mi-negocio-menu-expanded');
  });

  await runStep('Agregar Negocio modal', async () => {
    await clickSidebarOption(page, /^Agregar Negocio$/i);

    const modal = page.getByRole('dialog').filter({ hasText: /Crear Nuevo Negocio/i }).first();
    const inModalScope = (await isVisible(modal, 10_000)) ? modal : page;

    await expect(inModalScope.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible({ timeout: STEP_TIMEOUT_MS });
    await expect(inModalScope.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible({ timeout: STEP_TIMEOUT_MS });
    await expect(inModalScope.getByRole('button', { name: /Cancelar/i }).first()).toBeVisible({ timeout: STEP_TIMEOUT_MS });
    await expect(inModalScope.getByRole('button', { name: /Crear Negocio/i }).first()).toBeVisible({ timeout: STEP_TIMEOUT_MS });

    let businessNameInput = inModalScope.getByLabel(/Nombre del Negocio/i).first();
    if (!(await isVisible(businessNameInput))) {
      businessNameInput = inModalScope.getByPlaceholder(/Nombre del Negocio/i).first();
    }
    if (!(await isVisible(businessNameInput))) {
      businessNameInput = inModalScope.locator('input').first();
    }

    await expect(businessNameInput).toBeVisible({ timeout: STEP_TIMEOUT_MS });
    await checkpoint(page, testInfo, '03-agregar-negocio-modal');

    await businessNameInput.click();
    await businessNameInput.fill('Negocio Prueba Automatización');
    await clickLocator(inModalScope.getByRole('button', { name: /Cancelar/i }).first(), page);
  });

  await runStep('Administrar Negocios view', async () => {
    if (!(await isVisible(page.getByText(/^Administrar Negocios$/i).first()))) {
      await clickSidebarOption(page, /^Mi Negocio$/i);
    }
    await clickSidebarOption(page, /^Administrar Negocios$/i);

    await expect(page.getByText(/Información General/i).first()).toBeVisible({ timeout: STEP_TIMEOUT_MS });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: STEP_TIMEOUT_MS });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: STEP_TIMEOUT_MS });
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible({ timeout: STEP_TIMEOUT_MS });
    await checkpoint(page, testInfo, '04-administrar-negocios-page');
  });

  await runStep('Información General', async () => {
    const infoSection = page.locator('section, div').filter({ hasText: /Información General/i }).first();
    await expect(infoSection).toBeVisible({ timeout: STEP_TIMEOUT_MS });

    const infoText = await infoSection.innerText();
    const emailPattern = /\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/i;
    expect(infoText).toMatch(emailPattern);
    expect(infoText).toMatch(/BUSINESS PLAN/i);

    const lines = infoText
      .split('\n')
      .map((line) => line.trim())
      .filter(Boolean);
    const excluded = /información general|business plan|cambiar plan|plan/i;
    const hasLikelyName = lines.some(
      (line) => !excluded.test(line.toLowerCase()) && !emailPattern.test(line) && /^[A-Za-zÀ-ÿ][A-Za-zÀ-ÿ' -]{2,}$/.test(line),
    );
    expect(hasLikelyName).toBeTruthy();

    if (await isVisible(page.getByRole('button', { name: /Cambiar Plan/i }).first())) {
      await expect(page.getByRole('button', { name: /Cambiar Plan/i }).first()).toBeVisible({ timeout: STEP_TIMEOUT_MS });
    } else {
      await expect(page.getByText(/Cambiar Plan/i).first()).toBeVisible({ timeout: STEP_TIMEOUT_MS });
    }
  });

  await runStep('Detalles de la Cuenta', async () => {
    const accountDetailsSection = page.locator('section, div').filter({ hasText: /Detalles de la Cuenta/i }).first();
    await expect(accountDetailsSection).toBeVisible({ timeout: STEP_TIMEOUT_MS });

    const text = await accountDetailsSection.innerText();
    expect(text).toMatch(/Cuenta creada/i);
    expect(text).toMatch(/Estado activo|Activo/i);
    expect(text).toMatch(/Idioma seleccionado|Idioma/i);
  });

  await runStep('Tus Negocios', async () => {
    const businessSection = page.locator('section, div').filter({ hasText: /Tus Negocios/i }).first();
    await expect(businessSection).toBeVisible({ timeout: STEP_TIMEOUT_MS });

    const text = await businessSection.innerText();
    expect(text).toMatch(/Agregar Negocio/i);
    expect(text).toMatch(/Tienes 2 de 3 negocios/i);
  });

  await runStep('Términos y Condiciones', async () => {
    legalUrls.termsAndConditions = await openAndValidateLegalPage(
      page,
      context,
      testInfo,
      'Términos y Condiciones',
      /Términos y Condiciones/i,
      '05-terminos-y-condiciones',
    );
  });

  await runStep('Política de Privacidad', async () => {
    legalUrls.privacyPolicy = await openAndValidateLegalPage(
      page,
      context,
      testInfo,
      'Política de Privacidad',
      /Política de Privacidad/i,
      '06-politica-de-privacidad',
    );
  });

  const finalReport = {
    report,
    urls: legalUrls,
    generatedAt: new Date().toISOString(),
  };

  // Step 10: final PASS/FAIL report for each required validation area.
  await testInfo.attach('10-final-report.json', {
    body: Buffer.from(JSON.stringify(finalReport, null, 2), 'utf-8'),
    contentType: 'application/json',
  });

  console.log('saleads_mi_negocio_full_test - Final Report');
  for (const field of REPORT_FIELDS) {
    console.log(`${field}: ${report[field]}`);
  }
  if (legalUrls.termsAndConditions) {
    console.log(`Términos y Condiciones URL: ${legalUrls.termsAndConditions}`);
  }
  if (legalUrls.privacyPolicy) {
    console.log(`Política de Privacidad URL: ${legalUrls.privacyPolicy}`);
  }

  expect(
    failures,
    `Validation failures:\n${failures.join('\n')}\n\nFinal report:\n${JSON.stringify(finalReport, null, 2)}`,
  ).toHaveLength(0);
});
