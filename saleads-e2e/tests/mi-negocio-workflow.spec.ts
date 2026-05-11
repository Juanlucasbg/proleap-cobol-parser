import { promises as fs } from 'node:fs';
import { expect, Locator, Page, TestInfo, test } from '@playwright/test';

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

type ReportStatus = 'PASS' | 'FAIL';

const reportFields: ReportField[] = [
  'Login',
  'Mi Negocio menu',
  'Agregar Negocio modal',
  'Administrar Negocios view',
  'Información General',
  'Detalles de la Cuenta',
  'Tus Negocios',
  'Términos y Condiciones',
  'Política de Privacidad'
];

function toRegex(label: string | RegExp): RegExp {
  if (label instanceof RegExp) {
    return label;
  }

  const escaped = label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  return new RegExp(`^\\s*${escaped}\\s*$`, 'i');
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(600);
}

async function pickFirstVisible(
  page: Page,
  locators: Locator[],
  timeoutMs = 15000
): Promise<Locator> {
  const end = Date.now() + timeoutMs;

  while (Date.now() < end) {
    for (const locator of locators) {
      const candidate = locator.first();

      if (await candidate.isVisible().catch(() => false)) {
        return candidate;
      }
    }

    await page.waitForTimeout(250);
  }

  throw new Error('No visible locator matched within timeout.');
}

async function findTextBasedClickable(page: Page, label: string | RegExp): Promise<Locator> {
  const pattern = toRegex(label);
  return pickFirstVisible(page, [
    page.getByRole('button', { name: pattern }),
    page.getByRole('link', { name: pattern }),
    page.getByRole('menuitem', { name: pattern }),
    page.getByRole('tab', { name: pattern }),
    page.getByText(pattern),
    page.getByText(label instanceof RegExp ? label : new RegExp(label, 'i'))
  ]);
}

async function clickByVisibleText(page: Page, label: string | RegExp): Promise<void> {
  const target = await findTextBasedClickable(page, label);
  await target.click();
  await waitForUi(page);
}

async function takeCheckpoint(
  page: Page,
  testInfo: TestInfo,
  fileName: string,
  fullPage = false
): Promise<string> {
  const safeName = fileName.replace(/\s+/g, '-').toLowerCase();
  const outputPath = testInfo.outputPath(`${safeName}.png`);
  await page.screenshot({ path: outputPath, fullPage });
  await testInfo.attach(fileName, { path: outputPath, contentType: 'image/png' });
  return outputPath;
}

function initReport(): Record<ReportField, ReportStatus> {
  return reportFields.reduce(
    (acc, key) => {
      acc[key] = 'FAIL';
      return acc;
    },
    {} as Record<ReportField, ReportStatus>
  );
}

test.describe('SaleADS - Mi Negocio full workflow', () => {
  test('validates login + Mi Negocio module end-to-end flow', async ({ page }, testInfo) => {
    const statuses = initReport();
    const errors: Partial<Record<ReportField, string>> = {};
    const screenshotPaths: Record<string, string> = {};
    const legalUrls: Partial<Record<'Términos y Condiciones' | 'Política de Privacidad', string>> = {};

    const runStep = async (field: ReportField, action: () => Promise<void>) => {
      try {
        await test.step(field, action);
        statuses[field] = 'PASS';
      } catch (error) {
        statuses[field] = 'FAIL';
        errors[field] = error instanceof Error ? error.message : String(error);
      }
    };

    const loginUrl =
      process.env.SALEADS_LOGIN_URL ??
      process.env.SALEADS_BASE_URL ??
      process.env.BASE_URL ??
      undefined;

    await runStep('Login', async () => {
      if (loginUrl) {
        await page.goto(loginUrl, { waitUntil: 'domcontentloaded' });
      }

      if (page.url() === 'about:blank') {
        throw new Error(
          'No login page available. Set SALEADS_LOGIN_URL/SALEADS_BASE_URL/BASE_URL to point to the current SaleADS environment login page.'
        );
      }

      await waitForUi(page);

      const googleButton = await findTextBasedClickable(
        page,
        /sign in with google|iniciar sesión con google|continuar con google|google/i
      );

      const popupPromise = page.waitForEvent('popup', { timeout: 6000 }).catch(() => null);
      await googleButton.click();
      const popup = await popupPromise;

      if (popup) {
        await popup.waitForLoadState('domcontentloaded');

        const accountOption = popup
          .getByText('juanlucasbarbiergarzon@gmail.com', { exact: false })
          .first();
        if (await accountOption.isVisible().catch(() => false)) {
          await accountOption.click();
          await popup.waitForLoadState('domcontentloaded').catch(() => undefined);
        }
      } else {
        const accountOption = page
          .getByText('juanlucasbarbiergarzon@gmail.com', { exact: false })
          .first();
        if (await accountOption.isVisible().catch(() => false)) {
          await accountOption.click();
        }
      }

      await waitForUi(page);

      const sidebar = await pickFirstVisible(page, [page.locator('aside'), page.locator('nav')], 60000);
      await expect(sidebar).toBeVisible();
      await expect(page.getByText(/Negocio/i).first()).toBeVisible({ timeout: 60000 });

      screenshotPaths.dashboard = await takeCheckpoint(page, testInfo, 'dashboard-loaded');
    });

    await runStep('Mi Negocio menu', async () => {
      const negocioOption = await findTextBasedClickable(page, /Negocio/i);
      await negocioOption.click();
      await waitForUi(page);

      await clickByVisibleText(page, /Mi Negocio/i);

      await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 15000 });
      await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 15000 });

      screenshotPaths.miNegocioMenu = await takeCheckpoint(page, testInfo, 'mi-negocio-menu-expanded');
    });

    await runStep('Agregar Negocio modal', async () => {
      await clickByVisibleText(page, /Agregar Negocio/i);

      await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible({ timeout: 15000 });

      const businessNameInput = await pickFirstVisible(page, [
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.locator('input[name*="negocio" i]'),
        page.locator('input[placeholder*="Negocio" i]')
      ]);

      await expect(businessNameInput).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
      await expect(page.getByRole('button', { name: /Cancelar/i })).toBeVisible();
      await expect(page.getByRole('button', { name: /Crear Negocio/i })).toBeVisible();

      screenshotPaths.agregarNegocioModal = await takeCheckpoint(
        page,
        testInfo,
        'agregar-negocio-modal'
      );

      await businessNameInput.click();
      await businessNameInput.fill('Negocio Prueba Automatización');
      await clickByVisibleText(page, /Cancelar/i);
      await expect(page.getByText(/Crear Nuevo Negocio/i).first()).not.toBeVisible({ timeout: 10000 });
    });

    await runStep('Administrar Negocios view', async () => {
      const miNegocioVisible = await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false);
      if (!miNegocioVisible) {
        await clickByVisibleText(page, /Mi Negocio/i);
      }

      await clickByVisibleText(page, /Administrar Negocios/i);

      await expect(page.getByText(/Información General/i).first()).toBeVisible({ timeout: 20000 });
      await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 20000 });
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20000 });
      await expect(page.getByText(/Sección Legal/i).first()).toBeVisible({ timeout: 20000 });

      screenshotPaths.administrarNegocios = await takeCheckpoint(
        page,
        testInfo,
        'administrar-negocios-page',
        true
      );
    });

    await runStep('Información General', async () => {
      const infoHeading = page.getByText(/Información General/i).first();
      await expect(infoHeading).toBeVisible();
      const infoSection = infoHeading.locator(
        'xpath=ancestor::*[self::section or self::article or self::div][1]'
      );

      const emailInSection = infoSection
        .getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)
        .first();
      await expect(emailInSection).toBeVisible();

      const textNodes = infoSection.locator('h1, h2, h3, h4, p, span, strong');
      const forbidden = /Información General|BUSINESS PLAN|Cambiar Plan|Cuenta creada|Estado activo|Idioma seleccionado/i;
      const maxNodes = Math.min(await textNodes.count(), 30);
      let hasUserNameLikeText = false;

      for (let i = 0; i < maxNodes; i += 1) {
        const text = (await textNodes.nth(i).innerText()).trim();
        if (!text || forbidden.test(text)) {
          continue;
        }

        if (/^[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}(?:\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,})+$/.test(text)) {
          hasUserNameLikeText = true;
          break;
        }
      }

      expect(hasUserNameLikeText).toBeTruthy();
      await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
      await expect(page.getByRole('button', { name: /Cambiar Plan/i })).toBeVisible();
    });

    await runStep('Detalles de la Cuenta', async () => {
      await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
      await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
    });

    await runStep('Tus Negocios', async () => {
      const businessHeading = page.getByText(/Tus Negocios/i).first();
      await expect(businessHeading).toBeVisible();

      const businessSection = businessHeading.locator(
        'xpath=ancestor::*[self::section or self::article or self::div][1]'
      );
      await expect(
        businessSection.getByRole('button', { name: /Agregar Negocio/i }).first()
      ).toBeVisible();
      await expect(businessSection.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();

      const sectionTexts = businessSection.locator('h1, h2, h3, h4, p, span, li, td, strong');
      const maxNodes = Math.min(await sectionTexts.count(), 40);
      let hasBusinessEntryLikeText = false;

      for (let i = 0; i < maxNodes; i += 1) {
        const text = (await sectionTexts.nth(i).innerText()).trim();
        if (!text) {
          continue;
        }

        if (
          /Tus Negocios|Agregar Negocio|Tienes 2 de 3 negocios|Sección Legal|Detalles de la Cuenta|Información General/i.test(
            text
          )
        ) {
          continue;
        }

        hasBusinessEntryLikeText = true;
        break;
      }

      expect(hasBusinessEntryLikeText).toBeTruthy();
    });

    const validateLegalLink = async (
      field: 'Términos y Condiciones' | 'Política de Privacidad',
      headingRegex: RegExp
    ) => {
      const appUrl = page.url();
      const popupPromise = page.waitForEvent('popup', { timeout: 7000 }).catch(() => null);

      await clickByVisibleText(page, field);
      const popup = await popupPromise;

      if (popup) {
        await popup.waitForLoadState('domcontentloaded');
        await expect(popup.getByText(headingRegex).first()).toBeVisible({ timeout: 20000 });

        const legalBody = popup.locator('article, main, section, p, li').first();
        await expect(legalBody).toBeVisible({ timeout: 15000 });

        screenshotPaths[field] = await takeCheckpoint(
          popup,
          testInfo,
          `${field}-legal-page`,
          true
        );
        legalUrls[field] = popup.url();

        await popup.close();
        await page.bringToFront();
        await waitForUi(page);
        return;
      }

      await expect(page.getByText(headingRegex).first()).toBeVisible({ timeout: 20000 });
      await expect(page.locator('article, main, section, p, li').first()).toBeVisible({ timeout: 15000 });

      screenshotPaths[field] = await takeCheckpoint(page, testInfo, `${field}-legal-page`, true);
      legalUrls[field] = page.url();

      if (page.url() !== appUrl) {
        await page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => undefined);
        await waitForUi(page);
      }
    };

    await runStep('Términos y Condiciones', async () => {
      await validateLegalLink('Términos y Condiciones', /Términos y Condiciones/i);
    });

    await runStep('Política de Privacidad', async () => {
      await validateLegalLink('Política de Privacidad', /Política de Privacidad/i);
    });

    const finalReport = {
      generatedAt: new Date().toISOString(),
      statuses,
      errors,
      legalUrls,
      screenshots: screenshotPaths
    };

    const reportPath = testInfo.outputPath('mi-negocio-final-report.json');
    await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), 'utf-8');
    await testInfo.attach('Mi Negocio Final Report', {
      path: reportPath,
      contentType: 'application/json'
    });

    const failedFields = reportFields.filter((field) => statuses[field] === 'FAIL');
    expect(
      failedFields,
      `Validation failures detected: ${failedFields.map((field) => `${field}: ${errors[field] ?? 'Unknown error'}`).join(' | ')}`
    ).toEqual([]);
  });
});
