import { expect, Locator, Page, TestInfo, test } from '@playwright/test';

type StepStatus = 'PASS' | 'FAIL';

const EMAIL_REGEX = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;

async function waitForUi(page: Page): Promise<void> {
  await page.waitForTimeout(400);
  await page.waitForLoadState('domcontentloaded', { timeout: 15_000 }).catch(() => {});
  await page.waitForLoadState('networkidle', { timeout: 5_000 }).catch(() => {});
}

async function screenshot(page: Page, testInfo: TestInfo, name: string, fullPage = false): Promise<void> {
  await page.screenshot({
    path: testInfo.outputPath(`${name}.png`),
    fullPage,
  });
}

async function firstVisible(locators: Locator[]): Promise<Locator | null> {
  for (const locator of locators) {
    const candidate = locator.first();
    const isVisible = await candidate.isVisible().catch(() => false);
    if (isVisible) {
      return candidate;
    }
  }

  return null;
}

async function clickVisible(locators: Locator[], page: Page, label: string): Promise<void> {
  const target = await firstVisible(locators);
  expect(target, `No visible element found for ${label}`).not.toBeNull();
  await target!.click();
  await waitForUi(page);
}

function sectionByHeading(page: Page, headingText: RegExp): Locator {
  return page.locator('section, div, article').filter({ has: page.getByText(headingText) }).first();
}

test.describe('SaleADS Mi Negocio full workflow', () => {
  test('logs in with Google and validates the full Mi Negocio flow', async ({ page, context }, testInfo) => {
    const result: Record<string, StepStatus> = {
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

    const errors: string[] = [];

    const runStep = async (name: keyof typeof result, fn: () => Promise<void>) => {
      try {
        await fn();
        result[name] = 'PASS';
      } catch (error) {
        result[name] = 'FAIL';
        errors.push(`${name}: ${(error as Error).message}`);
      }
    };

    const configuredUrl = process.env.SALEADS_URL || process.env.BASE_URL;
    if (page.url() === 'about:blank' && configuredUrl) {
      await page.goto(configuredUrl, { waitUntil: 'domcontentloaded' });
      await waitForUi(page);
    }

    await runStep('Login', async () => {
      expect(page.url(), 'Expected browser to be on login page or SALEADS_URL/BASE_URL to be provided').not.toBe(
        'about:blank',
      );

      const googleTrigger = await firstVisible([
        page.getByRole('button', { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
        page.getByRole('link', { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google/i),
      ]);

      expect(googleTrigger, 'Google login trigger is not visible').not.toBeNull();

      const popupPromise = context.waitForEvent('page', { timeout: 10_000 }).catch(() => null);
      await googleTrigger!.click();
      await waitForUi(page);

      const popup = await popupPromise;
      const googlePage = popup ?? page;
      await googlePage.waitForLoadState('domcontentloaded', { timeout: 20_000 }).catch(() => {});

      const account = await firstVisible([
        googlePage.getByText('juanlucasbarbiergarzon@gmail.com', { exact: true }),
        googlePage.getByRole('button', { name: /juanlucasbarbiergarzon@gmail\.com/i }),
        googlePage.getByRole('link', { name: /juanlucasbarbiergarzon@gmail\.com/i }),
      ]);

      if (account) {
        await account.click();
      }

      if (popup) {
        await popup.waitForClose({ timeout: 30_000 }).catch(() => {});
        await page.bringToFront();
      }

      await waitForUi(page);
      await expect(page.locator('aside, nav').first()).toBeVisible({ timeout: 45_000 });
      await expect(page.getByText(/negocio|mi negocio/i).first()).toBeVisible({ timeout: 45_000 });
      await screenshot(page, testInfo, '01-dashboard-loaded');
    });

    await runStep('Mi Negocio menu', async () => {
      await clickVisible(
        [
          page.getByRole('button', { name: /^Negocio$/i }),
          page.getByRole('link', { name: /^Negocio$/i }),
          page.getByText(/^Negocio$/i),
        ],
        page,
        'Negocio section',
      );

      await clickVisible(
        [
          page.getByRole('button', { name: /^Mi Negocio$/i }),
          page.getByRole('link', { name: /^Mi Negocio$/i }),
          page.getByText(/^Mi Negocio$/i),
        ],
        page,
        'Mi Negocio menu',
      );

      await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 20_000 });
      await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 20_000 });
      await screenshot(page, testInfo, '02-mi-negocio-menu-expanded');
    });

    await runStep('Agregar Negocio modal', async () => {
      await clickVisible(
        [
          page.getByRole('button', { name: /^Agregar Negocio$/i }),
          page.getByRole('link', { name: /^Agregar Negocio$/i }),
          page.getByText(/^Agregar Negocio$/i),
        ],
        page,
        'Agregar Negocio',
      );

      const modal = page.locator('[role="dialog"]').first();
      await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible({ timeout: 20_000 });
      await expect(modal.getByLabel(/Nombre del Negocio/i)).toBeVisible();
      await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(modal.getByRole('button', { name: /^Cancelar$/i })).toBeVisible();
      await expect(modal.getByRole('button', { name: /^Crear Negocio$/i })).toBeVisible();

      await screenshot(page, testInfo, '03-agregar-negocio-modal');

      const businessNameInput = modal.getByLabel(/Nombre del Negocio/i);
      await businessNameInput.fill('Negocio Prueba Automatizacion');
      await modal.getByRole('button', { name: /^Cancelar$/i }).click();
      await waitForUi(page);
      await expect(modal).toBeHidden({ timeout: 10_000 });
    });

    await runStep('Administrar Negocios view', async () => {
      const miNegocioVisible = await page.getByText(/^Mi Negocio$/i).first().isVisible().catch(() => false);
      if (!miNegocioVisible) {
        await clickVisible(
          [
            page.getByRole('button', { name: /^Negocio$/i }),
            page.getByRole('link', { name: /^Negocio$/i }),
            page.getByText(/^Negocio$/i),
          ],
          page,
          'Negocio section (re-expand)',
        );
      }

      await clickVisible(
        [
          page.getByRole('button', { name: /^Administrar Negocios$/i }),
          page.getByRole('link', { name: /^Administrar Negocios$/i }),
          page.getByText(/^Administrar Negocios$/i),
        ],
        page,
        'Administrar Negocios',
      );

      await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible({ timeout: 30_000 });
      await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 30_000 });
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 30_000 });
      await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible({ timeout: 30_000 });
      await screenshot(page, testInfo, '04-administrar-negocios-page', true);
    });

    await runStep('Información General', async () => {
      const infoSection = sectionByHeading(page, /Informaci[oó]n General/i);
      await expect(infoSection).toBeVisible({ timeout: 20_000 });

      const textContent = (await infoSection.innerText()).split('\n').map((line) => line.trim()).filter(Boolean);
      const hasEmail = textContent.some((line) => EMAIL_REGEX.test(line));
      expect(hasEmail, 'No user email found in Información General').toBeTruthy();

      const hasUserName = textContent.some(
        (line) =>
          !EMAIL_REGEX.test(line) &&
          !/informaci[oó]n general|business plan|cambiar plan|plan|correo|email/i.test(line) &&
          line.length >= 3,
      );
      expect(hasUserName, 'No user name-like text found in Información General').toBeTruthy();

      await expect(infoSection.getByText(/BUSINESS PLAN/i)).toBeVisible();
      await expect(infoSection.getByRole('button', { name: /Cambiar Plan/i })).toBeVisible();
    });

    await runStep('Detalles de la Cuenta', async () => {
      const detailsSection = sectionByHeading(page, /Detalles de la Cuenta/i);
      await expect(detailsSection).toBeVisible({ timeout: 20_000 });
      await expect(detailsSection.getByText(/Cuenta creada/i)).toBeVisible();
      await expect(detailsSection.getByText(/Estado activo/i)).toBeVisible();
      await expect(detailsSection.getByText(/Idioma seleccionado/i)).toBeVisible();
    });

    await runStep('Tus Negocios', async () => {
      const businessesSection = sectionByHeading(page, /Tus Negocios/i);
      await expect(businessesSection).toBeVisible({ timeout: 20_000 });

      const businessEntries = businessesSection.locator('li, tr, [data-testid*="business"], [class*="business"]');
      const entriesCount = await businessEntries.count();
      expect(entriesCount, 'Business list is not visible').toBeGreaterThan(0);

      await expect(
        firstVisible([
          businessesSection.getByRole('button', { name: /^Agregar Negocio$/i }),
          businessesSection.getByRole('link', { name: /^Agregar Negocio$/i }),
          businessesSection.getByText(/^Agregar Negocio$/i),
        ]),
      ).resolves.not.toBeNull();

      await expect(businessesSection.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    });

    const validateLegalLink = async (
      label: 'Términos y Condiciones' | 'Política de Privacidad',
      heading: RegExp,
      screenshotName: string,
    ) => {
      const link = await firstVisible([
        page.getByRole('link', { name: new RegExp(label, 'i') }),
        page.getByRole('button', { name: new RegExp(label, 'i') }),
        page.getByText(new RegExp(label, 'i')),
      ]);
      expect(link, `${label} link is not visible`).not.toBeNull();

      const popupPromise = context.waitForEvent('page', { timeout: 10_000 }).catch(() => null);
      await link!.click();
      await waitForUi(page);

      const popup = await popupPromise;
      const targetPage = popup ?? page;
      await targetPage.waitForLoadState('domcontentloaded', { timeout: 20_000 }).catch(() => {});

      const headingLocator = await firstVisible([
        targetPage.getByRole('heading', { name: heading }),
        targetPage.getByText(heading),
      ]);

      expect(headingLocator, `${label} heading was not found`).not.toBeNull();
      await expect(headingLocator!).toBeVisible({ timeout: 20_000 });

      const bodyText = (await targetPage.locator('body').innerText()).trim();
      expect(bodyText.length, `${label} legal content text was not found`).toBeGreaterThan(120);

      await screenshot(targetPage, testInfo, screenshotName, true);

      const finalUrl = targetPage.url();
      await testInfo.attach(`${label}-url.txt`, {
        body: finalUrl,
        contentType: 'text/plain',
      });

      if (popup) {
        await popup.close();
        await page.bringToFront();
      } else {
        await page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => {});
        await waitForUi(page);
      }
    };

    await runStep('Términos y Condiciones', async () => {
      await validateLegalLink('Términos y Condiciones', /T[ée]rminos y Condiciones/i, '08-terminos-y-condiciones');
    });

    await runStep('Política de Privacidad', async () => {
      await validateLegalLink('Política de Privacidad', /Pol[ií]tica de Privacidad/i, '09-politica-de-privacidad');
    });

    await testInfo.attach('final-report.json', {
      body: JSON.stringify({ result, errors }, null, 2),
      contentType: 'application/json',
    });

    expect(
      Object.values(result).every((status) => status === 'PASS'),
      `One or more workflow validations failed:\n${errors.join('\n')}`,
    ).toBeTruthy();
  });
});
