import fs from 'node:fs/promises';
import path from 'node:path';

import { expect, Locator, Page, TestInfo, test } from '@playwright/test';

type StepStatus = 'PASS' | 'FAIL';

type StepKey =
  | 'Login'
  | 'Mi Negocio menu'
  | 'Agregar Negocio modal'
  | 'Administrar Negocios view'
  | 'Informacion General'
  | 'Detalles de la Cuenta'
  | 'Tus Negocios'
  | 'Terminos y Condiciones'
  | 'Politica de Privacidad';

const REPORT_OUTPUT_DIR = path.join(process.cwd(), 'test-results', 'saleads-mi-negocio');

function emptyReport(): Record<StepKey, StepStatus> {
  return {
    Login: 'FAIL',
    'Mi Negocio menu': 'FAIL',
    'Agregar Negocio modal': 'FAIL',
    'Administrar Negocios view': 'FAIL',
    'Informacion General': 'FAIL',
    'Detalles de la Cuenta': 'FAIL',
    'Tus Negocios': 'FAIL',
    'Terminos y Condiciones': 'FAIL',
    'Politica de Privacidad': 'FAIL',
  };
}

async function waitForUiToSettle(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded').catch(() => {});
  await page.waitForLoadState('networkidle', { timeout: 6_000 }).catch(() => {});
  await page.waitForTimeout(700);
}

async function firstVisible(candidates: Locator[], timeoutMs = 20_000): Promise<Locator> {
  if (!candidates.length) {
    throw new Error('No locator candidates were provided.');
  }

  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      const locator = candidate.first();
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }
    await candidates[0].page().waitForTimeout(250);
  }

  throw new Error('None of the provided locators became visible in time.');
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiToSettle(page);
}

function slugify(value: string): string {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

async function checkpoint(page: Page, testInfo: TestInfo, name: string, fullPage = false): Promise<void> {
  const screenshotPath = testInfo.outputPath(`${slugify(name)}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(name, {
    path: screenshotPath,
    contentType: 'image/png',
  });
}

function sectionByHeading(page: Page, headingPattern: RegExp): Locator {
  const heading = page.getByRole('heading', { name: headingPattern }).first();
  return page.locator('section, article, div').filter({ has: heading }).first();
}

async function validateLegalDestination(
  appPage: Page,
  clickable: Locator,
  expectedHeading: RegExp,
  screenshotName: string,
  testInfo: TestInfo,
): Promise<string> {
  const context = appPage.context();
  const appUrlBeforeClick = appPage.url();
  const popupPromise = context.waitForEvent('page', { timeout: 8_000 }).catch(() => null);

  await clickable.click();
  await waitForUiToSettle(appPage);

  const popup = await popupPromise;
  const legalPage = popup ?? appPage;
  await waitForUiToSettle(legalPage);

  const legalHeading = legalPage.getByRole('heading', { name: expectedHeading }).first();
  if (await legalHeading.isVisible().catch(() => false)) {
    await expect(legalHeading).toBeVisible();
  } else {
    await expect(legalPage.getByText(expectedHeading).first()).toBeVisible();
  }

  const bodyText = (await legalPage.locator('body').innerText()).replace(/\s+/g, ' ').trim();
  if (bodyText.length < 120) {
    throw new Error('Legal destination did not contain enough visible content text.');
  }

  await checkpoint(legalPage, testInfo, screenshotName, true);

  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUiToSettle(appPage);
  } else if (appPage.url() !== appUrlBeforeClick) {
    await appPage.goBack().catch(() => {});
    await waitForUiToSettle(appPage);
  }

  return finalUrl;
}

test('saleads_mi_negocio_full_test', async ({ page }, testInfo) => {
  const report = emptyReport();
  const legalUrls: Record<string, string> = {};
  let appReady = false;
  let miNegocioReady = false;
  let administrarReady = false;

  await fs.mkdir(REPORT_OUTPUT_DIR, { recursive: true });

  const loginUrl = process.env.SALEADS_LOGIN_URL?.trim();
  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: 'domcontentloaded' });
    await waitForUiToSettle(page);
  }

  // Step 1: Login with Google
  await test.step('Step 1 - Login with Google', async () => {
    try {
      const loginButton = await firstVisible([
        page.getByRole('button', {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|ingresar con google/i,
        }),
        page.getByRole('link', {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|ingresar con google/i,
        }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google|ingresar con google/i),
      ]);

      await clickAndWait(loginButton, page);

      const accountSelector = page
        .getByText(/juanlucasbarbiergarzon@gmail\.com/i)
        .or(page.getByRole('button', { name: /juanlucasbarbiergarzon@gmail\.com/i }))
        .first();

      if (await accountSelector.isVisible({ timeout: 12_000 }).catch(() => false)) {
        await clickAndWait(accountSelector, page);
      }

      const leftSidebar = await firstVisible([
        page.locator('aside').filter({ hasText: /negocio|mi negocio|dashboard|inicio/i }),
        page.getByRole('navigation').filter({ hasText: /negocio|mi negocio|dashboard|inicio/i }),
      ]);

      await expect(leftSidebar).toBeVisible();
      await checkpoint(page, testInfo, '01-dashboard-loaded');
      appReady = true;
      report.Login = 'PASS';
    } catch (error) {
      report.Login = 'FAIL';
    }
  });

  // Step 2: Open Mi Negocio menu
  await test.step('Step 2 - Open Mi Negocio menu', async () => {
    try {
      if (!appReady) {
        throw new Error('Application shell is not ready after login.');
      }

      const miNegocioMenu = await firstVisible([
        page.getByRole('button', { name: /mi negocio/i }),
        page.getByRole('link', { name: /mi negocio/i }),
        page.getByRole('menuitem', { name: /mi negocio/i }),
        page.getByText(/mi negocio/i),
      ]);

      await clickAndWait(miNegocioMenu, page);

      await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
      await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();
      await checkpoint(page, testInfo, '02-mi-negocio-menu-expanded');

      miNegocioReady = true;
      report['Mi Negocio menu'] = 'PASS';
    } catch (error) {
      report['Mi Negocio menu'] = 'FAIL';
    }
  });

  // Step 3: Validate Agregar Negocio modal
  await test.step('Step 3 - Validate Agregar Negocio modal', async () => {
    try {
      if (!miNegocioReady) {
        throw new Error('Mi Negocio menu is not expanded.');
      }

      const addBusinessFromMenu = await firstVisible([
        page.getByRole('link', { name: /agregar negocio/i }),
        page.getByRole('button', { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i),
      ]);
      await clickAndWait(addBusinessFromMenu, page);

      const modal = await firstVisible([
        page.getByRole('dialog').filter({ hasText: /crear nuevo negocio/i }),
        page.locator('[role="dialog"], .modal, [data-testid*="modal"]').filter({ hasText: /crear nuevo negocio/i }),
      ]);

      await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();

      const businessNameInput = await firstVisible(
        [
          modal.getByLabel(/nombre del negocio/i),
          modal.getByPlaceholder(/nombre del negocio/i),
          modal.locator('input').first(),
        ],
        10_000,
      );

      await expect(businessNameInput).toBeVisible();
      await expect(modal.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
      await expect(modal.getByRole('button', { name: /cancelar/i })).toBeVisible();
      await expect(modal.getByRole('button', { name: /crear negocio/i })).toBeVisible();
      await checkpoint(page, testInfo, '03-agregar-negocio-modal');

      await businessNameInput.click();
      await businessNameInput.fill('Negocio Prueba Automatizacion');
      await clickAndWait(modal.getByRole('button', { name: /cancelar/i }), page);

      report['Agregar Negocio modal'] = 'PASS';
    } catch (error) {
      report['Agregar Negocio modal'] = 'FAIL';
    }
  });

  // Step 4: Open Administrar Negocios
  await test.step('Step 4 - Open Administrar Negocios', async () => {
    try {
      const administrarNegociosOption = page.getByText(/administrar negocios/i).first();
      if (!(await administrarNegociosOption.isVisible().catch(() => false))) {
        const miNegocioMenu = await firstVisible([
          page.getByRole('button', { name: /mi negocio/i }),
          page.getByRole('link', { name: /mi negocio/i }),
          page.getByText(/mi negocio/i),
        ]);
        await clickAndWait(miNegocioMenu, page);
      }

      await clickAndWait(page.getByText(/administrar negocios/i).first(), page);

      await expect(page.getByRole('heading', { name: /informaci[oó]n general/i })).toBeVisible();
      await expect(page.getByRole('heading', { name: /detalles de la cuenta/i })).toBeVisible();
      await expect(page.getByRole('heading', { name: /tus negocios/i })).toBeVisible();
      await expect(page.getByRole('heading', { name: /secci[oó]n legal/i })).toBeVisible();

      await checkpoint(page, testInfo, '04-administrar-negocios-view', true);
      administrarReady = true;
      report['Administrar Negocios view'] = 'PASS';
    } catch (error) {
      report['Administrar Negocios view'] = 'FAIL';
    }
  });

  // Step 5: Validate Informacion General
  await test.step('Step 5 - Validate Informacion General', async () => {
    try {
      if (!administrarReady) {
        throw new Error('Administrar Negocios view did not load.');
      }

      const infoSection = sectionByHeading(page, /informaci[oó]n general/i);
      await expect(infoSection).toBeVisible();

      const emailInSection = infoSection.locator('text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/').first();
      await expect(emailInSection).toBeVisible();

      const hasNameLabel = await infoSection
        .getByText(/nombre|usuario|perfil/i)
        .first()
        .isVisible()
        .catch(() => false);
      const hasLikelyNameValue = await infoSection
        .locator('p, span, div')
        .filter({ hasText: /^[A-Za-z][A-Za-z'\- ]{2,}$/ })
        .first()
        .isVisible()
        .catch(() => false);
      if (!hasNameLabel && !hasLikelyNameValue) {
        throw new Error('User name was not visible in Informacion General.');
      }

      await expect(infoSection.getByText(/business plan/i)).toBeVisible();
      await expect(infoSection.getByRole('button', { name: /cambiar plan/i })).toBeVisible();

      report['Informacion General'] = 'PASS';
    } catch (error) {
      report['Informacion General'] = 'FAIL';
    }
  });

  // Step 6: Validate Detalles de la Cuenta
  await test.step('Step 6 - Validate Detalles de la Cuenta', async () => {
    try {
      const accountDetailsSection = sectionByHeading(page, /detalles de la cuenta/i);
      await expect(accountDetailsSection).toBeVisible();
      await expect(accountDetailsSection.getByText(/cuenta creada/i)).toBeVisible();
      await expect(accountDetailsSection.getByText(/estado activo/i)).toBeVisible();
      await expect(accountDetailsSection.getByText(/idioma seleccionado/i)).toBeVisible();

      report['Detalles de la Cuenta'] = 'PASS';
    } catch (error) {
      report['Detalles de la Cuenta'] = 'FAIL';
    }
  });

  // Step 7: Validate Tus Negocios
  await test.step('Step 7 - Validate Tus Negocios', async () => {
    try {
      const businessSection = sectionByHeading(page, /tus negocios/i);
      await expect(businessSection).toBeVisible();

      const businessList = await firstVisible([
        businessSection.locator('ul, ol, table, [role="list"], [role="table"]'),
        businessSection.locator('div').filter({ hasText: /negocio/i }),
      ]);
      await expect(businessList).toBeVisible();

      await expect(businessSection.getByRole('button', { name: /agregar negocio/i })).toBeVisible();
      await expect(businessSection.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();

      report['Tus Negocios'] = 'PASS';
    } catch (error) {
      report['Tus Negocios'] = 'FAIL';
    }
  });

  // Step 8: Validate Terminos y Condiciones
  await test.step('Step 8 - Validate Terminos y Condiciones', async () => {
    try {
      const legalSection = sectionByHeading(page, /secci[oó]n legal/i);
      await expect(legalSection).toBeVisible();

      const termsLink = await firstVisible([
        legalSection.getByRole('link', { name: /t[eé]rminos y condiciones/i }),
        legalSection.getByRole('button', { name: /t[eé]rminos y condiciones/i }),
        legalSection.getByText(/t[eé]rminos y condiciones/i),
      ]);

      legalUrls.termsAndConditions = await validateLegalDestination(
        page,
        termsLink,
        /t[eé]rminos y condiciones/i,
        '05-terminos-y-condiciones',
        testInfo,
      );
      report['Terminos y Condiciones'] = 'PASS';
    } catch (error) {
      report['Terminos y Condiciones'] = 'FAIL';
    }
  });

  // Step 9: Validate Politica de Privacidad
  await test.step('Step 9 - Validate Politica de Privacidad', async () => {
    try {
      const legalSection = sectionByHeading(page, /secci[oó]n legal/i);
      await expect(legalSection).toBeVisible();

      const privacyLink = await firstVisible([
        legalSection.getByRole('link', { name: /pol[ií]tica de privacidad/i }),
        legalSection.getByRole('button', { name: /pol[ií]tica de privacidad/i }),
        legalSection.getByText(/pol[ií]tica de privacidad/i),
      ]);

      legalUrls.privacyPolicy = await validateLegalDestination(
        page,
        privacyLink,
        /pol[ií]tica de privacidad/i,
        '06-politica-de-privacidad',
        testInfo,
      );
      report['Politica de Privacidad'] = 'PASS';
    } catch (error) {
      report['Politica de Privacidad'] = 'FAIL';
    }
  });

  const reportPayload = {
    workflow: 'saleads_mi_negocio_full_test',
    generatedAt: new Date().toISOString(),
    results: report,
    legalUrls,
  };

  const reportJson = JSON.stringify(reportPayload, null, 2);
  const reportPath = path.join(REPORT_OUTPUT_DIR, 'final-report.json');
  const legalUrlsPath = path.join(REPORT_OUTPUT_DIR, 'legal-urls.json');

  await fs.writeFile(reportPath, reportJson, 'utf8');
  await fs.writeFile(legalUrlsPath, JSON.stringify(legalUrls, null, 2), 'utf8');
  await testInfo.attach('final-report.json', {
    body: Buffer.from(reportJson, 'utf8'),
    contentType: 'application/json',
  });

  // Step 10: Final report
  console.log('FINAL_REPORT:', reportJson);

  const failedSteps = Object.entries(report)
    .filter(([, status]) => status === 'FAIL')
    .map(([step]) => step);
  if (failedSteps.length > 0) {
    throw new Error(`Workflow validations failed: ${failedSteps.join(', ')}`);
  }
});
