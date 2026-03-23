import { expect, test, type Locator, type Page, type TestInfo } from '@playwright/test';

type StepStatus = 'PASS' | 'FAIL';

type StepResult = {
  label: string;
  status: StepStatus;
  details: string[];
};

const ACCOUNT_EMAIL = 'juanlucasbarbiergarzon@gmail.com';
const TEST_BUSINESS_NAME = 'Negocio Prueba Automatizacion';

const STEP_LABELS = {
  login: 'Login',
  menu: 'Mi Negocio menu',
  modal: 'Agregar Negocio modal',
  adminView: 'Administrar Negocios view',
  infoGeneral: 'Información General',
  accountDetails: 'Detalles de la Cuenta',
  businesses: 'Tus Negocios',
  terms: 'Términos y Condiciones',
  privacy: 'Política de Privacidad',
} as const;

const DEFAULT_TIMEOUT = 20000;

const DIACRITIC_MAP: Record<string, string> = {
  a: 'aáàäâã',
  e: 'eéèëê',
  i: 'iíìïî',
  o: 'oóòöôõ',
  u: 'uúùüû',
  n: 'nñ',
  c: 'cç',
};

function normalizeText(value: string): string {
  return value.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase();
}

function escapeRegexChar(character: string): string {
  return character.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function buildTextRegex(text: string): RegExp {
  let regex = '';

  for (const char of text) {
    if (/\s/.test(char)) {
      regex += '\\s+';
      continue;
    }

    const lower = char.toLowerCase();
    const variants = DIACRITIC_MAP[lower];
    if (variants) {
      regex += `[${variants}]`;
      continue;
    }

    regex += escapeRegexChar(char);
  }

  return new RegExp(regex, 'i');
}

async function visibleByText(page: Page, text: string, scope?: Locator): Promise<Locator> {
  const container = scope ?? page.locator('body');
  const regex = buildTextRegex(text);
  const candidates = container.getByText(regex);
  const count = await candidates.count();

  for (let index = 0; index < count; index += 1) {
    const candidate = candidates.nth(index);
    if (await candidate.isVisible()) {
      return candidate;
    }
  }

  throw new Error(`No visible element found for text "${text}".`);
}

async function clickByVisibleText(page: Page, text: string, scope?: Locator): Promise<void> {
  const target = await visibleByText(page, text, scope);
  await target.click();
}

async function waitForUiSettle(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded').catch(() => Promise.resolve());
  await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => Promise.resolve());
  await page.waitForTimeout(350);
}

async function attachCheckpoint(
  page: Page,
  testInfo: TestInfo,
  checkpointName: string,
  fullPage = false
): Promise<void> {
  const screenshot = await page.screenshot({ fullPage });
  await testInfo.attach(`checkpoint-${checkpointName}`, {
    body: screenshot,
    contentType: 'image/png',
  });
}

async function markStep(
  results: StepResult[],
  label: string,
  fn: () => Promise<void>
): Promise<boolean> {
  const step: StepResult = { label, status: 'PASS', details: [] };

  try {
    await fn();
  } catch (error) {
    step.status = 'FAIL';
    const message = error instanceof Error ? error.message : String(error);
    step.details.push(message);
  } finally {
    results.push(step);
  }

  return step.status === 'PASS';
}

async function expectTextsVisible(page: Page, texts: string[], scope?: Locator): Promise<void> {
  for (const text of texts) {
    const target = await visibleByText(page, text, scope);
    await expect(target).toBeVisible({ timeout: DEFAULT_TIMEOUT });
  }
}

async function pickGoogleAccountIfPrompted(contextPage: Page): Promise<void> {
  const accountPicker = contextPage.getByText(buildTextRegex(ACCOUNT_EMAIL));
  if (await accountPicker.first().isVisible({ timeout: 5000 }).catch(() => false)) {
    await accountPicker.first().click();
    await waitForUiSettle(contextPage);
  }
}

async function locateAppPageAfterLogin(startPage: Page): Promise<Page> {
  const context = startPage.context();
  const deadline = Date.now() + 60000;

  while (Date.now() < deadline) {
    const pages = context.pages().filter((candidate) => !candidate.isClosed());
    for (const candidate of pages.reverse()) {
      const shell = candidate.locator('aside, nav').first();
      if (await shell.isVisible({ timeout: 500 }).catch(() => false)) {
        await candidate.bringToFront();
        await waitForUiSettle(candidate);
        return candidate;
      }
    }
    await startPage.waitForTimeout(500);
  }

  throw new Error('Main application shell not found after Google login.');
}

async function fillBusinessNameIfPossible(page: Page): Promise<void> {
  const candidates: Locator[] = [
    page.getByLabel(buildTextRegex('Nombre del Negocio')).first(),
    page.getByPlaceholder(buildTextRegex('Nombre del Negocio')).first(),
    page.getByRole('textbox', { name: buildTextRegex('Nombre del Negocio') }).first(),
    page.locator('input[type="text"], textarea').first(),
  ];

  for (const candidate of candidates) {
    if (await candidate.isVisible().catch(() => false)) {
      await candidate.fill(TEST_BUSINESS_NAME);
      return;
    }
  }
}

async function clickLegalLinkAndValidate(
  page: Page,
  linkText: string,
  headingText: string,
  testInfo: TestInfo
): Promise<{ finalUrl: string }> {
  const context = page.context();
  const originUrl = page.url();
  const popupPromise = context.waitForEvent('page', { timeout: 8000 }).catch(() => null);

  await clickByVisibleText(page, linkText);
  await waitForUiSettle(page);

  const popup = await popupPromise;
  const targetPage = popup ?? page;

  if (popup) {
    await popup.waitForLoadState('domcontentloaded').catch(() => Promise.resolve());
    await popup.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => Promise.resolve());
  }

  await expectTextsVisible(targetPage, [headingText]);

  const legalBody = targetPage.locator('main, article, body').first();
  await expect(legalBody).toContainText(/\S+/, { timeout: DEFAULT_TIMEOUT });

  await attachCheckpoint(targetPage, testInfo, `legal-${normalizeText(linkText)}`, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close().catch(() => Promise.resolve());
    await page.bringToFront();
    await waitForUiSettle(page);
  } else if (page.url() !== originUrl) {
    await page
      .goBack({ waitUntil: 'domcontentloaded' })
      .catch(async () =>
        page.goto(originUrl, {
          waitUntil: 'domcontentloaded',
        })
      );
    await waitForUiSettle(page);
  }

  return { finalUrl };
}

test.describe('saleads_mi_negocio_full_test', () => {
  test('Login and validate complete Mi Negocio workflow', async ({ page }, testInfo) => {
    test.setTimeout(12 * 60 * 1000);

    const baseUrl =
      process.env.SALEADS_START_URL ||
      process.env.SALEADS_BASE_URL ||
      process.env.BASE_URL ||
      process.env.URL;
    if (!baseUrl) {
      throw new Error(
        'Missing environment URL. Set SALEADS_START_URL (or SALEADS_BASE_URL / BASE_URL / URL) to the SaleADS login page.'
      );
    }

    const results: StepResult[] = [];
    const fatalErrors: string[] = [];
    let termsUrl = '';
    let privacyUrl = '';
    let appPage: Page = page;

    try {
      await appPage.goto(baseUrl, { waitUntil: 'domcontentloaded' });
      await waitForUiSettle(appPage);

      await markStep(results, STEP_LABELS.login, async () => {
        const googleButtonCandidates = [
          'Sign in with Google',
          'Continuar con Google',
          'Iniciar sesion con Google',
          'Ingresar con Google',
          'Google',
        ];

        const popupPromise = appPage.context().waitForEvent('page', { timeout: 10000 }).catch(() => null);

        let clicked = false;
        for (const candidate of googleButtonCandidates) {
          try {
            await clickByVisibleText(appPage, candidate);
            clicked = true;
            break;
          } catch {
            // Try next candidate.
          }
        }

        if (!clicked) {
          throw new Error('Could not find a login action for Google.');
        }

        const googlePopup = await popupPromise;
        if (googlePopup) {
          await googlePopup.waitForLoadState('domcontentloaded').catch(() => Promise.resolve());
          await pickGoogleAccountIfPrompted(googlePopup);
          await googlePopup.waitForClose({ timeout: 40000 }).catch(() => Promise.resolve());
        } else {
          await pickGoogleAccountIfPrompted(appPage);
        }

        appPage = await locateAppPageAfterLogin(appPage);
        await expect(appPage.locator('aside, nav').first()).toBeVisible({ timeout: DEFAULT_TIMEOUT });

        await attachCheckpoint(appPage, testInfo, 'dashboard-loaded', true);
      });

      await markStep(results, STEP_LABELS.menu, async () => {
        await clickByVisibleText(appPage, 'Negocio');
        await waitForUiSettle(appPage);

        await clickByVisibleText(appPage, 'Mi Negocio');
        await waitForUiSettle(appPage);

        await expectTextsVisible(appPage, ['Agregar Negocio', 'Administrar Negocios']);
        await attachCheckpoint(appPage, testInfo, 'mi-negocio-menu-expanded', false);
      });

      await markStep(results, STEP_LABELS.modal, async () => {
        await clickByVisibleText(appPage, 'Agregar Negocio');
        await waitForUiSettle(appPage);

        await expectTextsVisible(appPage, [
          'Crear Nuevo Negocio',
          'Nombre del Negocio',
          'Tienes 2 de 3 negocios',
          'Cancelar',
          'Crear Negocio',
        ]);

        await fillBusinessNameIfPossible(appPage);
        await attachCheckpoint(appPage, testInfo, 'agregar-negocio-modal', false);
        await clickByVisibleText(appPage, 'Cancelar');
        await waitForUiSettle(appPage);
      });

      await markStep(results, STEP_LABELS.adminView, async () => {
        const manageVisible = await appPage
          .getByText(buildTextRegex('Administrar Negocios'))
          .first()
          .isVisible()
          .catch(() => false);
        if (!manageVisible) {
          await clickByVisibleText(appPage, 'Mi Negocio');
          await waitForUiSettle(appPage);
        }

        await clickByVisibleText(appPage, 'Administrar Negocios');
        await waitForUiSettle(appPage);

        await expectTextsVisible(appPage, [
          'Informacion General',
          'Detalles de la Cuenta',
          'Tus Negocios',
          'Seccion Legal',
        ]);

        await attachCheckpoint(appPage, testInfo, 'administrar-negocios-page', true);
      });

      await markStep(results, STEP_LABELS.infoGeneral, async () => {
        await expectTextsVisible(appPage, ['BUSINESS PLAN', 'Cambiar Plan']);
        const emailElement = await visibleByText(appPage, '@');
        await expect(emailElement).toBeVisible({ timeout: DEFAULT_TIMEOUT });
      });

      await markStep(results, STEP_LABELS.accountDetails, async () => {
        await expectTextsVisible(appPage, ['Cuenta creada', 'Estado activo', 'Idioma seleccionado']);
      });

      await markStep(results, STEP_LABELS.businesses, async () => {
        await expectTextsVisible(appPage, ['Tus Negocios', 'Agregar Negocio', 'Tienes 2 de 3 negocios']);
      });

      await markStep(results, STEP_LABELS.terms, async () => {
        const result = await clickLegalLinkAndValidate(
          appPage,
          'Terminos y Condiciones',
          'Terminos y Condiciones',
          testInfo
        );
        termsUrl = result.finalUrl;
      });

      await markStep(results, STEP_LABELS.privacy, async () => {
        const result = await clickLegalLinkAndValidate(
          appPage,
          'Politica de Privacidad',
          'Politica de Privacidad',
          testInfo
        );
        privacyUrl = result.finalUrl;
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      fatalErrors.push(message);
    } finally {
      const reportLines: string[] = [];
      reportLines.push('Final Report - saleads_mi_negocio_full_test');
      reportLines.push('');
      for (const label of Object.values(STEP_LABELS)) {
        const row = results.find((entry) => entry.label === label);
        const status = row?.status ?? 'FAIL';
        reportLines.push(`- ${label}: ${status}`);
        if (row?.details?.length) {
          for (const detail of row.details) {
            reportLines.push(`  - detail: ${detail}`);
          }
        }
      }

      reportLines.push('');
      reportLines.push(`- Terminos y Condiciones URL: ${termsUrl || 'NOT_CAPTURED'}`);
      reportLines.push(`- Politica de Privacidad URL: ${privacyUrl || 'NOT_CAPTURED'}`);
      if (fatalErrors.length > 0) {
        reportLines.push('- Fatal Errors:');
        for (const error of fatalErrors) {
          reportLines.push(`  - ${error}`);
        }
      }

      await testInfo.attach('final-report', {
        body: Buffer.from(reportLines.join('\n'), 'utf8'),
        contentType: 'text/plain',
      });
    }

    const failedSteps = results.filter((entry) => entry.status === 'FAIL').map((entry) => entry.label);
    expect(
      failedSteps.length + fatalErrors.length,
      `Failures detected. Steps failed: ${failedSteps.join(', ') || 'none'}. Fatal: ${fatalErrors.join(' | ') || 'none'}`
    ).toBe(0);
  });
});
