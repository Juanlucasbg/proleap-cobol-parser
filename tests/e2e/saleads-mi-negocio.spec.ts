import { expect, type Locator, type Page, test } from '@playwright/test';
import fs from 'node:fs/promises';

type StepStatus = 'PASS' | 'FAIL';

type FinalReport = {
  Login: StepStatus;
  'Mi Negocio menu': StepStatus;
  'Agregar Negocio modal': StepStatus;
  'Administrar Negocios view': StepStatus;
  'Información General': StepStatus;
  'Detalles de la Cuenta': StepStatus;
  'Tus Negocios': StepStatus;
  'Términos y Condiciones': StepStatus;
  'Política de Privacidad': StepStatus;
};

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT_EMAIL ?? 'juanlucasbarbiergarzon@gmail.com';
const EXPECTED_USER_EMAIL = process.env.SALEADS_EXPECTED_USER_EMAIL ?? GOOGLE_ACCOUNT_EMAIL;
const EXPECTED_USER_NAME = process.env.SALEADS_EXPECTED_USER_NAME;

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(800);
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await locator.click();
  await waitForUi(page);
}

function byText(page: Page, value: string): Locator {
  return page.getByText(new RegExp(value, 'i'));
}

async function isVisible(locator: Locator): Promise<boolean> {
  return locator.first().isVisible().catch(() => false);
}

async function takeCheckpoint(page: Page, name: string): Promise<void> {
  await page.screenshot({ path: `test-results/${name}.png`, fullPage: true });
}

async function withStepReport(
  report: FinalReport,
  key: keyof FinalReport,
  action: () => Promise<void>
): Promise<void> {
  try {
    await action();
    report[key] = 'PASS';
  } catch (error) {
    report[key] = 'FAIL';
    // Keep test execution going to produce a complete report.
    console.error(`[${String(key)}] failed`, error);
  }
}

async function openMiNegocioSection(page: Page): Promise<void> {
  const negocio = page.getByRole('button', { name: /negocio/i }).first();
  if (await isVisible(negocio)) {
    await clickAndWait(negocio, page);
    return;
  }

  const negocioText = byText(page, 'Negocio').first();
  await clickAndWait(negocioText, page);
}

async function navigateLegalLink(
  page: Page,
  linkName: string,
  headingName: string,
  screenshotName: string
): Promise<string> {
  const context = page.context();
  const link = page.getByRole('link', { name: new RegExp(linkName, 'i') }).first();
  const fallbackClickable = byText(page, linkName).first();
  const currentUrl = page.url();

  const popupPromise = context.waitForEvent('page', { timeout: 5_000 }).catch(() => null);
  if (await isVisible(link)) {
    await link.click();
  } else {
    await fallbackClickable.click();
  }
  await waitForUi(page);

  const popup = await popupPromise;
  const target = popup ?? page;
  await target.waitForLoadState('domcontentloaded');

  await expect(target.getByRole('heading', { name: new RegExp(headingName, 'i') }).first()).toBeVisible({
    timeout: 15_000
  });

  const legalTextVisible = await target
    .locator('main, article, section, body')
    .filter({ hasText: /términos|condiciones|privacidad|datos|uso|información/i })
    .first()
    .isVisible()
    .catch(() => false);
  expect(legalTextVisible).toBeTruthy();

  await takeCheckpoint(target, screenshotName);
  const finalUrl = target.url();

  if (popup) {
    await popup.close();
  } else if (page.url() !== currentUrl) {
    await page.goBack();
    await waitForUi(page);
  }

  return finalUrl;
}

test('saleads_mi_negocio_full_test', async ({ page }) => {
  const report: FinalReport = {
    Login: 'FAIL',
    'Mi Negocio menu': 'FAIL',
    'Agregar Negocio modal': 'FAIL',
    'Administrar Negocios view': 'FAIL',
    'Información General': 'FAIL',
    'Detalles de la Cuenta': 'FAIL',
    'Tus Negocios': 'FAIL',
    'Términos y Condiciones': 'FAIL',
    'Política de Privacidad': 'FAIL'
  };

  const evidence: Record<string, string> = {};
  const loginUrl = process.env.SALEADS_LOGIN_URL;

  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: 'domcontentloaded' });
    await waitForUi(page);
  }

  await withStepReport(report, 'Login', async () => {
    const signInGoogle = page
      .getByRole('button', { name: /sign in with google|continuar con google|google/i })
      .first();
    const signInGoogleFallback = byText(page, 'Sign in with Google').first();

    if (await isVisible(signInGoogle)) {
      await clickAndWait(signInGoogle, page);
    } else if (await isVisible(signInGoogleFallback)) {
      await clickAndWait(signInGoogleFallback, page);
    }

    const accountCandidate = byText(page, GOOGLE_ACCOUNT_EMAIL).first();
    if (await isVisible(accountCandidate)) {
      await clickAndWait(accountCandidate, page);
    }

    await expect(page.locator('aside').first()).toBeVisible({ timeout: 45_000 });
    await takeCheckpoint(page, '01-dashboard-loaded');
  });

  await withStepReport(report, 'Mi Negocio menu', async () => {
    await openMiNegocioSection(page);
    await expect(byText(page, 'Agregar Negocio').first()).toBeVisible({ timeout: 15_000 });
    await expect(byText(page, 'Administrar Negocios').first()).toBeVisible({ timeout: 15_000 });
    await takeCheckpoint(page, '02-mi-negocio-expanded');
  });

  await withStepReport(report, 'Agregar Negocio modal', async () => {
    await clickAndWait(byText(page, 'Agregar Negocio').first(), page);
    await expect(byText(page, 'Crear Nuevo Negocio').first()).toBeVisible({ timeout: 15_000 });
    await expect(
      page.getByRole('textbox', { name: /nombre del negocio/i }).or(page.locator('input[placeholder*="Negocio"]'))
    ).toBeVisible({ timeout: 15_000 });
    await expect(byText(page, 'Tienes 2 de 3 negocios').first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole('button', { name: /cancelar/i }).first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole('button', { name: /crear negocio/i }).first()).toBeVisible({ timeout: 15_000 });

    const businessNameInput = page
      .getByRole('textbox', { name: /nombre del negocio/i })
      .or(page.locator('input[placeholder*="Negocio"]'))
      .first();
    if (await isVisible(businessNameInput)) {
      await businessNameInput.fill('Negocio Prueba Automatización');
    }

    await takeCheckpoint(page, '03-agregar-negocio-modal');
    await clickAndWait(page.getByRole('button', { name: /cancelar/i }).first(), page);
  });

  await withStepReport(report, 'Administrar Negocios view', async () => {
    const administrarNegocios = byText(page, 'Administrar Negocios').first();
    if (!(await isVisible(administrarNegocios))) {
      await openMiNegocioSection(page);
    }

    await clickAndWait(byText(page, 'Administrar Negocios').first(), page);
    await expect(byText(page, 'Información General').first()).toBeVisible({ timeout: 20_000 });
    await expect(byText(page, 'Detalles de la Cuenta').first()).toBeVisible({ timeout: 20_000 });
    await expect(byText(page, 'Tus Negocios').first()).toBeVisible({ timeout: 20_000 });
    await expect(byText(page, 'Sección Legal').first()).toBeVisible({ timeout: 20_000 });
    await takeCheckpoint(page, '04-administrar-negocios');
  });

  await withStepReport(report, 'Información General', async () => {
    await expect(byText(page, 'Información General').first()).toBeVisible({ timeout: 15_000 });
    await expect(byText(page, 'BUSINESS PLAN').first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole('button', { name: /cambiar plan/i }).first()).toBeVisible({ timeout: 15_000 });

    if (EXPECTED_USER_NAME) {
      await expect(byText(page, EXPECTED_USER_NAME).first()).toBeVisible({ timeout: 15_000 });
    } else {
      const maybeNameVisible = await page
        .locator('main, section, article, div')
        .filter({ hasText: /^[A-Za-zÀ-ÿ][A-Za-zÀ-ÿ\s'.-]{2,}$/ })
        .first()
        .isVisible()
        .catch(() => false);
      expect(maybeNameVisible).toBeTruthy();
    }

    await expect(byText(page, EXPECTED_USER_EMAIL).first()).toBeVisible({ timeout: 15_000 });
  });

  await withStepReport(report, 'Detalles de la Cuenta', async () => {
    await expect(byText(page, 'Cuenta creada').first()).toBeVisible({ timeout: 15_000 });
    await expect(byText(page, 'Estado activo').first()).toBeVisible({ timeout: 15_000 });
    await expect(byText(page, 'Idioma seleccionado').first()).toBeVisible({ timeout: 15_000 });
  });

  await withStepReport(report, 'Tus Negocios', async () => {
    await expect(byText(page, 'Tus Negocios').first()).toBeVisible({ timeout: 15_000 });
    await expect(byText(page, 'Agregar Negocio').first()).toBeVisible({ timeout: 15_000 });
    await expect(byText(page, 'Tienes 2 de 3 negocios').first()).toBeVisible({ timeout: 15_000 });
  });

  await withStepReport(report, 'Términos y Condiciones', async () => {
    evidence['Términos y Condiciones URL'] = await navigateLegalLink(
      page,
      'Términos y Condiciones',
      'Términos y Condiciones',
      '05-terminos-y-condiciones'
    );
  });

  await withStepReport(report, 'Política de Privacidad', async () => {
    evidence['Política de Privacidad URL'] = await navigateLegalLink(
      page,
      'Política de Privacidad',
      'Política de Privacidad',
      '06-politica-de-privacidad'
    );
  });

  await fs.mkdir('test-results', { recursive: true });
  await fs.writeFile(
    'test-results/saleads-mi-negocio-final-report.json',
    JSON.stringify(
      {
        name: 'saleads_mi_negocio_full_test',
        report,
        evidence
      },
      null,
      2
    ),
    'utf-8'
  );

  console.log('Final workflow report:', JSON.stringify(report, null, 2));
  expect(Object.values(report).every((value) => value === 'PASS')).toBeTruthy();
});
