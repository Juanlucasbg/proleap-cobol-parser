import fs from 'node:fs';
import path from 'node:path';
import { expect, type Locator, type Page, test } from '@playwright/test';

const GOOGLE_ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT ?? 'juanlucasbarbiergarzon@gmail.com';

const STEP_FIELDS = [
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

type StepField = (typeof STEP_FIELDS)[number];
type StepStatus = 'PASS' | 'FAIL';

function createStepReport(): Record<StepField, StepStatus> {
  return STEP_FIELDS.reduce(
    (acc, field) => {
      acc[field] = 'FAIL';
      return acc;
    },
    {} as Record<StepField, StepStatus>,
  );
}

function sanitizeFileName(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, '-');
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded', { timeout: 20_000 });
  await page.waitForLoadState('networkidle', { timeout: 5_000 }).catch(() => undefined);
  await page.waitForTimeout(400);
}

async function clickAndWait(page: Page, target: Locator): Promise<void> {
  await expect(target).toBeVisible({ timeout: 20_000 });
  await target.click();
  await waitForUi(page);
}

async function pickFirstVisible(candidates: Locator[], label: string): Promise<Locator> {
  for (const candidate of candidates) {
    const first = candidate.first();
    const visible = await first.isVisible({ timeout: 3_000 }).catch(() => false);
    if (visible) {
      return first;
    }
  }

  throw new Error(`Could not locate a visible element for: ${label}`);
}

function sectionByHeading(page: Page, heading: RegExp): Locator {
  const headingLocator = page.getByText(heading).first();
  return page.locator('section, article, div').filter({ has: headingLocator }).first();
}

function extractMeaningfulLines(content: string): string[] {
  return content
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean);
}

test('saleads_mi_negocio_full_test', async ({ page }, testInfo) => {
  const runId = new Date().toISOString().replace(/[:.]/g, '-');
  const runFolder = path.resolve(__dirname, '..', 'artifacts', runId);
  const screenshotFolder = path.join(runFolder, 'screenshots');
  fs.mkdirSync(screenshotFolder, { recursive: true });

  const report = createStepReport();
  const failures: string[] = [];
  const evidence: Record<string, string> = {};

  const checkpoint = async (
    sourcePage: Page,
    name: string,
    options: { fullPage?: boolean } = {},
  ): Promise<string> => {
    const filePath = path.join(screenshotFolder, `${sanitizeFileName(name)}.png`);
    await sourcePage.screenshot({
      path: filePath,
      fullPage: options.fullPage ?? false,
    });
    return filePath;
  };

  const runStep = async (field: StepField, fn: () => Promise<void>): Promise<void> => {
    try {
      await fn();
      report[field] = 'PASS';
    } catch (error) {
      report[field] = 'FAIL';
      failures.push(`${field}: ${(error as Error).message}`);
    }
  };

  try {
    await runStep('Login', async () => {
      const loginUrl = process.env.SALEADS_LOGIN_URL;
      if (loginUrl) {
        await page.goto(loginUrl, { waitUntil: 'domcontentloaded' });
      } else if (page.url() === 'about:blank') {
        throw new Error(
          'Set SALEADS_LOGIN_URL or pre-load the browser on the SaleADS login page before running this test.',
        );
      }

      await waitForUi(page);

      const loginButton = await pickFirstVisible(
        [
          page.getByRole('button', { name: /sign in with google/i }),
          page.getByRole('button', { name: /iniciar sesión con google/i }),
          page.getByRole('button', { name: /continuar con google/i }),
          page.getByText(/sign in with google/i),
          page.getByText(/iniciar sesión con google/i),
          page.getByText(/continuar con google/i),
        ],
        'Google login button',
      );

      const popupPromise = page.waitForEvent('popup', { timeout: 10_000 }).catch(() => null);
      await clickAndWait(page, loginButton);
      const popup = await popupPromise;

      if (popup) {
        await waitForUi(popup).catch(() => undefined);
        const accountChoice = popup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });
        const canPickAccount = await accountChoice.isVisible({ timeout: 5_000 }).catch(() => false);
        if (canPickAccount) {
          await accountChoice.click();
          await waitForUi(popup).catch(() => undefined);
        }
        await popup.waitForEvent('close', { timeout: 15_000 }).catch(() => undefined);
        await page.bringToFront();
      }

      await waitForUi(page);

      const sidebar = await pickFirstVisible(
        [
          page.locator('aside'),
          page.locator('nav'),
          page.locator('[role="navigation"]'),
        ],
        'Main application sidebar',
      );
      await expect(sidebar).toBeVisible();
      evidence.dashboardScreenshot = await checkpoint(page, '01-dashboard-loaded');
    });

    await runStep('Mi Negocio menu', async () => {
      const negocioTrigger = await pickFirstVisible(
        [
          page.getByRole('button', { name: /^negocio$/i }),
          page.getByRole('link', { name: /^negocio$/i }),
          page.getByText(/^Negocio$/i),
        ],
        'Negocio section',
      );
      await clickAndWait(page, negocioTrigger);

      const miNegocioOption = await pickFirstVisible(
        [
          page.getByRole('button', { name: /mi negocio/i }),
          page.getByRole('link', { name: /mi negocio/i }),
          page.getByText(/^Mi Negocio$/i),
        ],
        'Mi Negocio option',
      );
      await clickAndWait(page, miNegocioOption);

      await expect(page.getByText(/^Agregar Negocio$/i)).toBeVisible();
      await expect(page.getByText(/^Administrar Negocios$/i)).toBeVisible();
      evidence.expandedMenuScreenshot = await checkpoint(page, '02-mi-negocio-expanded-menu');
    });

    await runStep('Agregar Negocio modal', async () => {
      const agregarNegocio = await pickFirstVisible(
        [
          page.getByRole('button', { name: /^Agregar Negocio$/i }),
          page.getByRole('link', { name: /^Agregar Negocio$/i }),
          page.getByText(/^Agregar Negocio$/i),
        ],
        'Agregar Negocio menu option',
      );
      await clickAndWait(page, agregarNegocio);

      await expect(page.getByText(/^Crear Nuevo Negocio$/i)).toBeVisible();
      await expect(page.getByText(/^Nombre del Negocio$/i)).toBeVisible();
      await expect(page.getByText(/^Tienes 2 de 3 negocios$/i)).toBeVisible();
      await expect(page.getByRole('button', { name: /^Cancelar$/i })).toBeVisible();
      await expect(page.getByRole('button', { name: /^Crear Negocio$/i })).toBeVisible();

      evidence.agregarNegocioModalScreenshot = await checkpoint(page, '03-agregar-negocio-modal');

      const businessNameInput = await pickFirstVisible(
        [
          page.getByLabel(/nombre del negocio/i),
          page.getByPlaceholder(/nombre del negocio/i),
          page.locator('input[type="text"], input:not([type])'),
        ],
        'Nombre del Negocio input',
      );
      await businessNameInput.click();
      await businessNameInput.fill('Negocio Prueba Automatización');

      const cancelButton = await pickFirstVisible(
        [
          page.getByRole('button', { name: /^Cancelar$/i }),
          page.getByText(/^Cancelar$/i),
        ],
        'Cancelar button on modal',
      );
      await clickAndWait(page, cancelButton);
    });

    await runStep('Administrar Negocios view', async () => {
      const miNegocioOption = await pickFirstVisible(
        [
          page.getByRole('button', { name: /mi negocio/i }),
          page.getByRole('link', { name: /mi negocio/i }),
          page.getByText(/^Mi Negocio$/i),
        ],
        'Mi Negocio option for re-expansion',
      );

      const adminIsVisible = await page
        .getByText(/^Administrar Negocios$/i)
        .first()
        .isVisible({ timeout: 2_000 })
        .catch(() => false);
      if (!adminIsVisible) {
        await clickAndWait(page, miNegocioOption);
      }

      const adminOption = await pickFirstVisible(
        [
          page.getByRole('link', { name: /^Administrar Negocios$/i }),
          page.getByRole('button', { name: /^Administrar Negocios$/i }),
          page.getByText(/^Administrar Negocios$/i),
        ],
        'Administrar Negocios option',
      );
      await clickAndWait(page, adminOption);

      await expect(page.getByText(/^Información General$/i)).toBeVisible();
      await expect(page.getByText(/^Detalles de la Cuenta$/i)).toBeVisible();
      await expect(page.getByText(/^Tus Negocios$/i)).toBeVisible();
      await expect(page.getByText(/^Sección Legal$/i)).toBeVisible();
      evidence.administrarNegociosScreenshot = await checkpoint(page, '04-administrar-negocios', {
        fullPage: true,
      });
    });

    await runStep('Información General', async () => {
      const infoSection = sectionByHeading(page, /^Información General$/i);
      await expect(infoSection).toBeVisible();

      const email = infoSection.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first();
      await expect(email).toBeVisible();

      await expect(infoSection.getByText(/BUSINESS PLAN/i)).toBeVisible();
      await expect(infoSection.getByRole('button', { name: /Cambiar Plan/i })).toBeVisible();

      const sectionLines = extractMeaningfulLines(await infoSection.innerText());
      const likelyValueLines = sectionLines.filter(
        (line) =>
          !/^información general$/i.test(line) &&
          !/^business plan$/i.test(line) &&
          !/^cambiar plan$/i.test(line) &&
          !/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(line),
      );
      if (likelyValueLines.length === 0) {
        throw new Error('No visible user-name-like text detected in Información General.');
      }
    });

    await runStep('Detalles de la Cuenta', async () => {
      const detailsSection = sectionByHeading(page, /^Detalles de la Cuenta$/i);
      await expect(detailsSection).toBeVisible();
      await expect(detailsSection.getByText(/Cuenta creada/i)).toBeVisible();
      await expect(detailsSection.getByText(/Estado activo/i)).toBeVisible();
      await expect(detailsSection.getByText(/Idioma seleccionado/i)).toBeVisible();
    });

    await runStep('Tus Negocios', async () => {
      const businessesSection = sectionByHeading(page, /^Tus Negocios$/i);
      await expect(businessesSection).toBeVisible();
      await expect(businessesSection.getByText(/^Agregar Negocio$/i)).toBeVisible();
      await expect(businessesSection.getByText(/^Tienes 2 de 3 negocios$/i)).toBeVisible();

      const businessEntries = businessesSection.locator('li, tr, [role="listitem"], .card, .row');
      if ((await businessEntries.count()) === 0) {
        const lines = extractMeaningfulLines(await businessesSection.innerText());
        if (lines.length < 4) {
          throw new Error('Business list content is not visible in Tus Negocios section.');
        }
      }
    });

    const validateLegalLink = async (
      linkName: string,
      headingPattern: RegExp,
      screenshotLabel: string,
      urlEvidenceKey: string,
      screenshotEvidenceKey: string,
    ): Promise<void> => {
      const appUrlBeforeOpen = page.url();
      const link = await pickFirstVisible(
        [
          page.getByRole('link', { name: new RegExp(`^${linkName}$`, 'i') }),
          page.getByText(new RegExp(`^${linkName}$`, 'i')),
        ],
        `Legal link: ${linkName}`,
      );

      const popupPromise = page.waitForEvent('popup', { timeout: 8_000 }).catch(() => null);
      await clickAndWait(page, link);
      const popup = await popupPromise;
      const legalPage = popup ?? page;
      await waitForUi(legalPage);

      await expect(legalPage.getByText(headingPattern).first()).toBeVisible();

      const paragraphVisible = await legalPage
        .locator('main p, article p, p')
        .first()
        .isVisible({ timeout: 5_000 })
        .catch(() => false);
      if (!paragraphVisible) {
        const bodyText = (await legalPage.locator('body').innerText()).trim();
        if (bodyText.length < 120) {
          throw new Error(`${linkName} page does not expose enough legal content text.`);
        }
      }

      evidence[urlEvidenceKey] = legalPage.url();
      evidence[screenshotEvidenceKey] = await checkpoint(legalPage, screenshotLabel, { fullPage: true });

      if (popup) {
        await popup.close().catch(() => undefined);
        await page.bringToFront();
      } else {
        await page.goto(appUrlBeforeOpen, { waitUntil: 'domcontentloaded' });
      }

      await waitForUi(page);
    };

    await runStep('Términos y Condiciones', async () => {
      await validateLegalLink(
        'Términos y Condiciones',
        /Términos y Condiciones/i,
        '05-terminos-y-condiciones',
        'terminosUrl',
        'terminosScreenshot',
      );
    });

    await runStep('Política de Privacidad', async () => {
      await validateLegalLink(
        'Política de Privacidad',
        /Política de Privacidad/i,
        '06-politica-de-privacidad',
        'privacidadUrl',
        'privacidadScreenshot',
      );
    });
  } finally {
    const reportPath = path.join(runFolder, 'saleads_mi_negocio_final_report.json');
    const finalReport = {
      testName: 'saleads_mi_negocio_full_test',
      generatedAt: new Date().toISOString(),
      configuredLoginUrl: process.env.SALEADS_LOGIN_URL ?? '(not set)',
      configuredGoogleAccount: GOOGLE_ACCOUNT_EMAIL,
      results: report,
      evidence,
      failures,
    };

    fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), 'utf-8');
    await testInfo.attach('saleads-mi-negocio-final-report', {
      path: reportPath,
      contentType: 'application/json',
    });
  }

  expect(
    failures,
    `One or more SaleADS Mi Negocio workflow validations failed:\n${failures.join('\n')}`,
  ).toEqual([]);
});
