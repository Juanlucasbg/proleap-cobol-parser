import { expect, test, type Locator, type Page } from '@playwright/test';
import * as fs from 'node:fs/promises';
import * as path from 'node:path';

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

type ReportStatus = 'PASS' | 'FAIL';

const REPORT_KEYS: ReportKey[] = [
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

function slugify(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-zA-Z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .toLowerCase();
}

async function ensureVisible(locator: Locator, message: string): Promise<void> {
  await expect(locator, message).toBeVisible();
}

async function waitAfterClick(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(750);
}

function textLocator(page: Page, text: string | RegExp): Locator {
  return page.getByText(text, { exact: false });
}

function clickableByText(page: Page, text: string | RegExp): Locator {
  return page
    .getByRole('button', { name: text, exact: false })
    .or(page.getByRole('link', { name: text, exact: false }))
    .or(textLocator(page, text));
}

async function clickByText(page: Page, text: string | RegExp): Promise<void> {
  const locator = clickableByText(page, text).first();
  await ensureVisible(locator, `Expected clickable element with text "${text}"`);
  await locator.click();
  await waitAfterClick(page);
}

async function takeCheckpoint(page: Page, dir: string, name: string, fullPage = false): Promise<void> {
  await fs.mkdir(dir, { recursive: true });
  const fileName = `${slugify(name)}.png`;
  await page.screenshot({
    path: path.join(dir, fileName),
    fullPage,
  });
  // Small pause to avoid overlapping transitions on very dynamic UIs.
  await page.waitForTimeout(250);
}

async function openLegalAndValidate(
  appPage: Page,
  linkText: 'Términos y Condiciones' | 'Política de Privacidad',
  expectedHeading: 'Términos y Condiciones' | 'Política de Privacidad',
  screenshotDir: string,
): Promise<string> {
  const legalTrigger = clickableByText(appPage, linkText).first();
  await ensureVisible(legalTrigger, `Expected legal link "${linkText}" to be visible`);

  const context = appPage.context();
  let targetPage: Page;

  const newPagePromise = context.waitForEvent('page', { timeout: 2500 }).catch(() => null);
  await legalTrigger.click();
  await waitAfterClick(appPage);
  const maybeNewPage = await newPagePromise;

  if (maybeNewPage) {
    targetPage = maybeNewPage;
    await targetPage.waitForLoadState('domcontentloaded');
  } else {
    targetPage = appPage;
  }

  await ensureVisible(
    textLocator(targetPage, expectedHeading).first(),
    `Expected heading "${expectedHeading}" to be visible`,
  );

  const legalParagraphs = targetPage.locator('p');
  await expect(legalParagraphs.first(), `Expected legal content on ${linkText}`).toBeVisible();

  await takeCheckpoint(targetPage, screenshotDir, `${linkText} page`, true);
  const finalUrl = targetPage.url();

  if (targetPage !== appPage) {
    await targetPage.close();
    await appPage.bringToFront();
    await appPage.waitForLoadState('domcontentloaded');
    await appPage.waitForTimeout(400);
  }

  return finalUrl;
}

async function isVisible(locator: Locator): Promise<boolean> {
  return locator.first().isVisible().catch(() => false);
}

async function firstVisible(candidates: Locator[]): Promise<Locator | null> {
  for (const candidate of candidates) {
    if (await isVisible(candidate)) {
      return candidate.first();
    }
  }
  return null;
}

test('saleads_mi_negocio_full_test', async ({ page }, testInfo) => {
  const runDir = path.join(process.cwd(), 'e2e-artifacts', 'saleads_mi_negocio_full_test', String(Date.now()));
  const screenshotDir = path.join(runDir, 'evidence');
  await fs.mkdir(screenshotDir, { recursive: true });

  const report: Record<ReportKey, ReportStatus> = Object.fromEntries(
    REPORT_KEYS.map((key) => [key, 'FAIL']),
  ) as Record<ReportKey, ReportStatus>;
  const failures: string[] = [];

  const entryUrl = process.env.SALEADS_ENTRY_URL ?? process.env.BASE_URL ?? process.env.URL;
  if (entryUrl) {
    await page.goto(entryUrl, { waitUntil: 'domcontentloaded' });
  }

  const executeStep = async (key: ReportKey, action: () => Promise<void>): Promise<void> => {
    try {
      await action();
      report[key] = 'PASS';
    } catch (error) {
      report[key] = 'FAIL';
      const message = error instanceof Error ? error.message : String(error);
      failures.push(`${key}: ${message}`);
      await takeCheckpoint(page, screenshotDir, `failure-${key}`);
    }
  };

  // Step 1: Login with Google
  await executeStep('Login', async () => {
    const sidebar = page.locator('aside').first();
    if (!(await isVisible(sidebar))) {
      const googleButton = await firstVisible([
        clickableByText(page, /google/i),
        clickableByText(page, /sign in with google/i),
        clickableByText(page, /iniciar sesi[oó]n con google/i),
        clickableByText(page, /continuar con google/i),
      ]);
      expect(googleButton, 'Expected Google login button or link').not.toBeNull();

      const popupPromise = page.context().waitForEvent('page', { timeout: 5000 }).catch(() => null);
      await googleButton!.click();
      await waitAfterClick(page);

      const googlePopup = await popupPromise;
      const accountEmail = 'juanlucasbarbiergarzon@gmail.com';

      if (googlePopup) {
        await googlePopup.waitForLoadState('domcontentloaded');
        const popupAccount = googlePopup
          .getByRole('button', { name: accountEmail, exact: false })
          .or(googlePopup.getByText(accountEmail, { exact: false }))
          .first();
        if (await isVisible(popupAccount)) {
          await popupAccount.click();
          await waitAfterClick(googlePopup);
        }
      }

      const inlineAccount = page.getByText(accountEmail, { exact: false }).first();
      if (await isVisible(inlineAccount)) {
        await inlineAccount.click();
        await waitAfterClick(page);
      }
    }

    await ensureVisible(page.locator('aside').first(), 'Expected left sidebar to be visible after login');
    await takeCheckpoint(page, screenshotDir, 'dashboard loaded');
  });

  // Step 2: Open Mi Negocio menu
  await executeStep('Mi Negocio menu', async () => {
    const negocio = clickableByText(page, 'Negocio').first();
    if (await isVisible(negocio)) {
      await negocio.click();
      await waitAfterClick(page);
    }
    await clickByText(page, 'Mi Negocio');
    await ensureVisible(textLocator(page, 'Agregar Negocio').first(), 'Expected "Agregar Negocio" in submenu');
    await ensureVisible(
      textLocator(page, 'Administrar Negocios').first(),
      'Expected "Administrar Negocios" in submenu',
    );
    await takeCheckpoint(page, screenshotDir, 'mi negocio expanded menu');
  });

  // Step 3: Validate Agregar Negocio modal
  await executeStep('Agregar Negocio modal', async () => {
    await clickByText(page, 'Agregar Negocio');
    await ensureVisible(textLocator(page, 'Crear Nuevo Negocio').first(), 'Expected modal title');
    await ensureVisible(textLocator(page, 'Nombre del Negocio').first(), 'Expected "Nombre del Negocio" field');
    await ensureVisible(textLocator(page, 'Tienes 2 de 3 negocios').first(), 'Expected usage text');
    await ensureVisible(clickableByText(page, 'Cancelar').first(), 'Expected "Cancelar" button');
    await ensureVisible(clickableByText(page, 'Crear Negocio').first(), 'Expected "Crear Negocio" button');
    await takeCheckpoint(page, screenshotDir, 'agregar negocio modal');

    const nameInput = page
      .getByRole('textbox', { name: /Nombre del Negocio/i })
      .or(page.getByPlaceholder('Nombre del Negocio'))
      .or(page.locator('input').first())
      .first();
    await nameInput.click();
    await nameInput.fill('Negocio Prueba Automatización');
    await clickByText(page, 'Cancelar');
  });

  // Step 4: Open Administrar Negocios
  await executeStep('Administrar Negocios view', async () => {
    const adminVisible = await textLocator(page, 'Administrar Negocios')
      .first()
      .isVisible()
      .catch(() => false);
    if (!adminVisible) {
      await clickByText(page, 'Mi Negocio');
    }
    await clickByText(page, 'Administrar Negocios');

    await ensureVisible(textLocator(page, 'Información General').first(), 'Expected "Información General" section');
    await ensureVisible(
      textLocator(page, 'Detalles de la Cuenta').first(),
      'Expected "Detalles de la Cuenta" section',
    );
    await ensureVisible(textLocator(page, 'Tus Negocios').first(), 'Expected "Tus Negocios" section');
    await ensureVisible(textLocator(page, 'Sección Legal').first(), 'Expected "Sección Legal" section');
    await takeCheckpoint(page, screenshotDir, 'administrar negocios account page', true);
  });

  // Step 5: Validate Información General
  await executeStep('Información General', async () => {
    const infoSection = page
      .locator('section, div')
      .filter({ has: textLocator(page, 'Información General').first() })
      .first();

    const userNameCandidate = infoSection
      .locator('h1, h2, h3, h4, strong, span, p')
      .filter({ hasNotText: /@/ })
      .first();
    await ensureVisible(userNameCandidate, 'Expected user name-like text to be visible');

    const possibleEmail = page.getByText(/@/, { exact: false }).first();
    await ensureVisible(possibleEmail, 'Expected user email to be visible in information section');
    await ensureVisible(textLocator(page, 'BUSINESS PLAN').first(), 'Expected BUSINESS PLAN text');
    await ensureVisible(clickableByText(page, 'Cambiar Plan').first(), 'Expected Cambiar Plan button');
  });

  // Step 6: Validate Detalles de la Cuenta
  await executeStep('Detalles de la Cuenta', async () => {
    await ensureVisible(textLocator(page, 'Cuenta creada').first(), 'Expected Cuenta creada text');
    await ensureVisible(textLocator(page, 'Estado activo').first(), 'Expected Estado activo text');
    await ensureVisible(textLocator(page, 'Idioma seleccionado').first(), 'Expected Idioma seleccionado text');
  });

  // Step 7: Validate Tus Negocios
  await executeStep('Tus Negocios', async () => {
    await ensureVisible(textLocator(page, 'Tus Negocios').first(), 'Expected Tus Negocios title');
    await ensureVisible(textLocator(page, 'Agregar Negocio').first(), 'Expected Agregar Negocio button');
    await ensureVisible(textLocator(page, 'Tienes 2 de 3 negocios').first(), 'Expected usage limit text');
  });

  // Step 8: Validate Términos y Condiciones
  let termsUrl = '';
  await executeStep('Términos y Condiciones', async () => {
    termsUrl = await openLegalAndValidate(
      page,
      'Términos y Condiciones',
      'Términos y Condiciones',
      screenshotDir,
    );
  });

  // Step 9: Validate Política de Privacidad
  let privacyUrl = '';
  await executeStep('Política de Privacidad', async () => {
    privacyUrl = await openLegalAndValidate(
      page,
      'Política de Privacidad',
      'Política de Privacidad',
      screenshotDir,
    );
  });

  // Step 10: Final report
  const reportPayload = {
    testName: 'saleads_mi_negocio_full_test',
    statuses: report,
    legalUrls: {
      terminosYCondiciones: termsUrl,
      politicaDePrivacidad: privacyUrl,
    },
    evidenceDirectory: screenshotDir,
    failures,
  };

  const reportFile = path.join(runDir, 'final-report.json');
  await fs.writeFile(reportFile, `${JSON.stringify(reportPayload, null, 2)}\n`, 'utf8');

  await testInfo.attach('final-report.json', {
    body: JSON.stringify(reportPayload, null, 2),
    contentType: 'application/json',
  });

  expect(failures, failures.join('\n')).toEqual([]);
});
