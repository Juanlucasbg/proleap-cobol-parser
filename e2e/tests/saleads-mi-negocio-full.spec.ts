import { expect, Page, test } from '@playwright/test';

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

type ReportStatus = 'PASS' | 'FAIL' | 'NOT_RUN';

const reportFields: ReportKey[] = [
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

const uiWait = async (page: Page): Promise<void> => {
  await page.waitForLoadState('domcontentloaded', { timeout: 20_000 }).catch(() => undefined);
  await page.waitForLoadState('networkidle', { timeout: 20_000 }).catch(() => undefined);
  await page.waitForTimeout(400);
};

const clickAndWait = async (page: Page, target: ReturnType<Page['locator']>): Promise<void> => {
  await expect(target).toBeVisible();
  await expect(target).toBeEnabled();
  await target.click();
  await uiWait(page);
};

const firstVisible = async (page: Page, selectors: string[]): Promise<ReturnType<Page['locator']>> => {
  for (const selector of selectors) {
    const locator = page.locator(selector).first();
    if (await locator.isVisible().catch(() => false)) {
      return locator;
    }
  }
  throw new Error(`No visible selector found. Tried: ${selectors.join(', ')}`);
};

const clickByTextAndWait = async (page: Page, patterns: RegExp[]): Promise<void> => {
  for (const pattern of patterns) {
    const roleButton = page.getByRole('button', { name: pattern }).first();
    if (await roleButton.isVisible().catch(() => false)) {
      await clickAndWait(page, roleButton);
      return;
    }

    const roleLink = page.getByRole('link', { name: pattern }).first();
    if (await roleLink.isVisible().catch(() => false)) {
      await clickAndWait(page, roleLink);
      return;
    }

    const textNode = page.getByText(pattern).first();
    if (await textNode.isVisible().catch(() => false)) {
      await clickAndWait(page, textNode);
      return;
    }
  }

  throw new Error(`No clickable element found for patterns: ${patterns.map((p) => p.source).join(', ')}`);
};

test('saleads_mi_negocio_full_test', async ({ page, context }, testInfo) => {
  const report: Record<ReportKey, ReportStatus> = Object.fromEntries(
    reportFields.map((field) => [field, 'NOT_RUN']),
  ) as Record<ReportKey, ReportStatus>;
  const reportNotes: Partial<Record<ReportKey, string>> = {};
  const legalUrls: Partial<Record<'terms' | 'privacy', string>> = {};

  const setPass = (key: ReportKey): void => {
    report[key] = 'PASS';
  };

  const setFail = (key: ReportKey, error: unknown): void => {
    report[key] = 'FAIL';
    reportNotes[key] = error instanceof Error ? error.message : String(error);
  };

  const runStep = async (key: ReportKey, step: () => Promise<void>): Promise<void> => {
    try {
      await step();
      setPass(key);
    } catch (error) {
      setFail(key, error);
    }
  };

  await test.step('Bootstrapping login page', async () => {
    if (process.env.SALEADS_URL) {
      await page.goto(process.env.SALEADS_URL, { waitUntil: 'domcontentloaded' });
      await uiWait(page);
      return;
    }

    if (page.url() === 'about:blank') {
      throw new Error(
        'SALEADS_URL is not defined and browser is on about:blank. Set SALEADS_URL to the environment login page URL.',
      );
    }

    await uiWait(page);
  });

  await runStep('Login', async () => {
    const navAlreadyVisible = await page.locator('aside, nav').first().isVisible().catch(() => false);
    if (!navAlreadyVisible) {
      const googlePopupPromise = context.waitForEvent('page', { timeout: 8_000 }).catch(() => null);

      const googleLoginControl = await firstVisible(page, [
        'button:has-text("Sign in with Google")',
        'button:has-text("Iniciar sesión con Google")',
        'button:has-text("Continuar con Google")',
        'button:has-text("Google")',
        '[role="button"]:has-text("Google")',
        'a:has-text("Google")',
        '[role="link"]:has-text("Google")',
      ]);
      await clickAndWait(page, googleLoginControl);

      const popupPage = await googlePopupPromise;
      const authPage = popupPage ?? page;
      await uiWait(authPage);

      const accountOption = authPage.getByText('juanlucasbarbiergarzon@gmail.com').first();
      if (await accountOption.isVisible().catch(() => false)) {
        await clickAndWait(authPage, accountOption);
      }

      if (popupPage) {
        await popupPage.waitForClose({ timeout: 45_000 }).catch(() => undefined);
        await page.bringToFront();
      }
    }

    await uiWait(page);
    await expect(page.locator('aside, nav').first()).toBeVisible({ timeout: 90_000 });
    await expect(page.getByText(/Negocio|Mi Negocio/i).first()).toBeVisible({ timeout: 90_000 });

    await page.screenshot({
      path: testInfo.outputPath('01-dashboard-loaded.png'),
      fullPage: true,
    });
  });

  await runStep('Mi Negocio menu', async () => {
    await clickByTextAndWait(page, [/Negocio/i]);
    await clickByTextAndWait(page, [/Mi Negocio/i]);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath('02-mi-negocio-menu-expanded.png'),
      fullPage: true,
    });
  });

  await runStep('Agregar Negocio modal', async () => {
    await clickByTextAndWait(page, [/Agregar Negocio/i]);
    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();

    const hasPlaceholder = await page.getByPlaceholder(/Nombre del Negocio/i).first().isVisible().catch(() => false);
    if (hasPlaceholder) {
      await expect(page.getByPlaceholder(/Nombre del Negocio/i).first()).toBeVisible();
    } else {
      await expect(page.getByLabel(/Nombre del Negocio/i).first()).toBeVisible();
    }
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /Cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /Crear Negocio/i }).first()).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath('03-agregar-negocio-modal.png'),
      fullPage: true,
    });

    const businessInput = await firstVisible(page, [
      'input[placeholder*="Nombre del Negocio"]',
      'input[name*="nombre"]',
      'input[id*="nombre"]',
    ]);
    await businessInput.click();
    await businessInput.fill('Negocio Prueba Automatización');
    await clickByTextAndWait(page, [/Cancelar/i]);
  });

  await runStep('Administrar Negocios view', async () => {
    if (!(await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false))) {
      await clickByTextAndWait(page, [/Mi Negocio/i]);
    }

    await clickByTextAndWait(page, [/Administrar Negocios/i]);
    await expect(page.getByText(/Información General/i).first()).toBeVisible({ timeout: 60_000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 60_000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 60_000 });
    await expect(page.getByText(/Sección Legal|Seccion Legal/i).first()).toBeVisible({ timeout: 60_000 });

    await page.screenshot({
      path: testInfo.outputPath('04-administrar-negocios-page.png'),
      fullPage: true,
    });
  });

  await runStep('Información General', async () => {
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /Cambiar Plan/i }).first()).toBeVisible();
    await expect(page.getByText(/@/).first()).toBeVisible();
    await expect(page.locator('h1, h2, h3, p, span').filter({ hasText: /[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\s[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}/ }).first()).toBeVisible();
  });

  await runStep('Detalles de la Cuenta', async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep('Tus Negocios', async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.locator('table, [role="table"], ul, [data-testid*="business"], [class*="business"]').first()).toBeVisible();
  });

  const validateLegalLink = async (
    key: 'Términos y Condiciones' | 'Política de Privacidad',
    linkMatcher: RegExp,
    headingMatcher: RegExp,
    outputName: string,
    legalUrlKey: 'terms' | 'privacy',
  ): Promise<void> => {
    await runStep(key, async () => {
      const previousUrl = page.url();
      const popupPromise = context.waitForEvent('page', { timeout: 8_000 }).catch(() => null);

      await clickByTextAndWait(page, [linkMatcher]);

      const popupPage = await popupPromise;
      const legalPage = popupPage ?? page;
      await uiWait(legalPage);

      await expect(legalPage.getByRole('heading', { name: headingMatcher }).first()).toBeVisible({
        timeout: 60_000,
      });

      const legalBodyText = await legalPage.locator('body').innerText();
      if (legalBodyText.trim().length < 80) {
        throw new Error(`Expected legal content text for ${key}, but body text seems too short.`);
      }

      legalUrls[legalUrlKey] = legalPage.url();
      await legalPage.screenshot({
        path: testInfo.outputPath(outputName),
        fullPage: true,
      });

      if (popupPage) {
        await popupPage.close();
        await page.bringToFront();
        await uiWait(page);
      } else {
        await page.goto(previousUrl, { waitUntil: 'domcontentloaded' });
        await uiWait(page);
      }
    });
  };

  await validateLegalLink(
    'Términos y Condiciones',
    /Términos y Condiciones/i,
    /Términos y Condiciones/i,
    '05-terminos-y-condiciones.png',
    'terms',
  );

  await validateLegalLink(
    'Política de Privacidad',
    /Política de Privacidad/i,
    /Política de Privacidad/i,
    '06-politica-de-privacidad.png',
    'privacy',
  );

  const finalReport = {
    testName: 'saleads_mi_negocio_full_test',
    statusByStep: report,
    notesByStep: reportNotes,
    finalUrls: {
      terminosYCondiciones: legalUrls.terms ?? null,
      politicaDePrivacidad: legalUrls.privacy ?? null,
    },
  };

  await testInfo.attach('final-report.json', {
    body: Buffer.from(JSON.stringify(finalReport, null, 2), 'utf-8'),
    contentType: 'application/json',
  });

  const reportLines = reportFields.map((field) => `- ${field}: ${report[field]}`);
  console.log('\nFinal Report');
  console.log(reportLines.join('\n'));
  console.log(`- Términos y Condiciones URL: ${finalReport.finalUrls.terminosYCondiciones ?? 'N/A'}`);
  console.log(`- Política de Privacidad URL: ${finalReport.finalUrls.politicaDePrivacidad ?? 'N/A'}\n`);

  const failedSteps = reportFields.filter((field) => report[field] !== 'PASS');
  expect(
    failedSteps,
    `Expected all validations to pass. Failed/Not run steps: ${failedSteps.join(', ')}`,
  ).toEqual([]);
});
