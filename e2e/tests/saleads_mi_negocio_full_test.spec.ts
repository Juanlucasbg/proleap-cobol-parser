import { expect, test, type Locator, type Page, type TestInfo } from '@playwright/test';
import { promises as fs } from 'node:fs';

type StepResult = {
  status: 'PASS' | 'FAIL';
  details: string;
  finalUrl?: string;
};

const REPORT_FIELDS = [
  'Login',
  'Mi Negocio menu',
  'Agregar Negocio modal',
  'Administrar Negocios view',
  'Información General',
  'Detalles de la Cuenta',
  'Tus Negocios',
  'Términos y Condiciones',
  'Política de Privacidad'
] as const;

type ReportField = (typeof REPORT_FIELDS)[number];
type FinalReport = Record<ReportField, StepResult>;

const sanitize = (value: string): string => value.toLowerCase().replace(/[^a-z0-9]+/g, '_');

const waitForUiAfterClick = async (page: Page): Promise<void> => {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForLoadState('networkidle', { timeout: 10_000 }).catch(() => undefined);
  await page.waitForTimeout(700);
};

const clickAndWait = async (locator: Locator, page: Page): Promise<void> => {
  await locator.first().click({ timeout: 20_000 });
  await waitForUiAfterClick(page);
};

const firstVisible = async (candidates: Locator[], timeoutMs = 20_000): Promise<Locator> => {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      const current = candidate.first();
      const visible = await current.isVisible().catch(() => false);

      if (visible) {
        return current;
      }
    }

    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  throw new Error(`None of the ${candidates.length} candidate locators became visible in ${timeoutMs}ms.`);
};

const captureCheckpoint = async (
  page: Page,
  testInfo: TestInfo,
  name: string,
  fullPage = false
): Promise<void> => {
  const path = testInfo.outputPath(`${sanitize(name)}.png`);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(name, { path, contentType: 'image/png' });
};

test('saleads_mi_negocio_full_test', async ({ page }, testInfo) => {
  const finalReport = REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = { status: 'FAIL', details: 'Not executed.' };
    return acc;
  }, {} as FinalReport);

  const runStep = async (field: ReportField, action: () => Promise<void>): Promise<boolean> => {
    try {
      await action();
      finalReport[field] = { status: 'PASS', details: 'Validation completed successfully.' };
      return true;
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      finalReport[field] = { status: 'FAIL', details: message };
      return false;
    }
  };

  const markDependencyFailure = (field: ReportField, dependency: ReportField): void => {
    finalReport[field] = {
      status: 'FAIL',
      details: `Skipped because required step "${dependency}" failed.`
    };
  };

  const finalizeAndAssert = async (): Promise<void> => {
    const reportPath = testInfo.outputPath('saleads_mi_negocio_final_report.json');
    await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), 'utf-8');
    await testInfo.attach('saleads_mi_negocio_final_report', {
      path: reportPath,
      contentType: 'application/json'
    });

    const failedValidations = Object.entries(finalReport).filter(([, result]) => result.status === 'FAIL');
    expect(
      failedValidations,
      `One or more validations failed:\n${JSON.stringify(finalReport, null, 2)}`
    ).toHaveLength(0);
  };

  const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_URL ?? process.env.BASE_URL;

  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: 'domcontentloaded' });
    await waitForUiAfterClick(page);
  } else {
    await testInfo.attach('execution_note', {
      contentType: 'text/plain',
      body: Buffer.from(
        'No SALEADS_LOGIN_URL/SALEADS_URL/BASE_URL set. The test assumes the browser was pre-opened on the SaleADS login page.'
      )
    });
  }

  const loginPassed = await runStep('Login', async () => {
    if (page.url() === 'about:blank') {
      throw new Error('Browser is on about:blank. Provide SALEADS_LOGIN_URL (or SALEADS_URL/BASE_URL).');
    }

    const loginButton = await firstVisible([
      page.getByRole('button', {
        name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i
      }),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i)
    ]);

    const popupPromise = page.waitForEvent('popup', { timeout: 10_000 }).catch(() => null);
    await clickAndWait(loginButton, page);
    const googlePopup = await popupPromise;

    if (googlePopup) {
      await googlePopup.waitForLoadState('domcontentloaded');

      const accountCandidate = await firstVisible(
        [
          googlePopup.getByText('juanlucasbarbiergarzon@gmail.com', { exact: false }),
          googlePopup.getByRole('button', { name: /juanlucasbarbiergarzon@gmail\.com/i }),
          googlePopup.getByRole('link', { name: /juanlucasbarbiergarzon@gmail\.com/i })
        ],
        8_000
      ).catch(() => null);

      if (accountCandidate) {
        await accountCandidate.click();
      }

      await googlePopup.waitForLoadState('networkidle', { timeout: 15_000 }).catch(() => undefined);
      await googlePopup.close().catch(() => undefined);
      await page.bringToFront();
    }

    await page.waitForLoadState('networkidle', { timeout: 30_000 }).catch(() => undefined);

    const appLoaded = await firstVisible(
      [
        page.locator('aside'),
        page.locator('nav'),
        page.getByText(/mi negocio|negocio|dashboard|inicio/i)
      ],
      30_000
    );
    await expect(appLoaded).toBeVisible();

    await captureCheckpoint(page, testInfo, '01_dashboard_loaded', true);
  });

  if (!loginPassed) {
    for (const field of REPORT_FIELDS) {
      if (field !== 'Login') {
        markDependencyFailure(field, 'Login');
      }
    }
    await finalizeAndAssert();
    return;
  }

  await runStep('Mi Negocio menu', async () => {
    const negocioSection = await firstVisible(
      [page.getByRole('button', { name: /negocio/i }), page.getByText(/^negocio$/i), page.getByText(/negocio/i)],
      20_000
    );
    await clickAndWait(negocioSection, page);

    const miNegocioOption = await firstVisible(
      [page.getByRole('link', { name: /mi negocio/i }), page.getByRole('button', { name: /mi negocio/i }), page.getByText(/mi negocio/i)],
      20_000
    );
    await clickAndWait(miNegocioOption, page);

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible({ timeout: 20_000 });

    await captureCheckpoint(page, testInfo, '02_mi_negocio_menu_expanded');
  });

  await runStep('Agregar Negocio modal', async () => {
    const agregarNegocio = await firstVisible(
      [page.getByRole('button', { name: /agregar negocio/i }), page.getByRole('link', { name: /agregar negocio/i }), page.getByText(/agregar negocio/i)],
      20_000
    );
    await clickAndWait(agregarNegocio, page);

    await expect(page.getByText(/crear nuevo negocio/i)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByLabel(/nombre del negocio/i).or(page.getByPlaceholder(/nombre del negocio/i))).toBeVisible({
      timeout: 20_000
    });
    await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole('button', { name: /cancelar/i })).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole('button', { name: /crear negocio/i })).toBeVisible({ timeout: 20_000 });

    await captureCheckpoint(page, testInfo, '03_agregar_negocio_modal');

    const nombreDelNegocio = page.getByLabel(/nombre del negocio/i).or(page.getByPlaceholder(/nombre del negocio/i));
    await nombreDelNegocio.fill('Negocio Prueba Automatización');
    await clickAndWait(page.getByRole('button', { name: /cancelar/i }), page);
  });

  await runStep('Administrar Negocios view', async () => {
    const administrarVisible = await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      const miNegocioOption = await firstVisible(
        [page.getByRole('link', { name: /mi negocio/i }), page.getByRole('button', { name: /mi negocio/i }), page.getByText(/mi negocio/i)],
        15_000
      );
      await clickAndWait(miNegocioOption, page);
    }

    const administrarNegocios = await firstVisible(
      [
        page.getByRole('link', { name: /administrar negocios/i }),
        page.getByRole('button', { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i)
      ],
      20_000
    );
    await clickAndWait(administrarNegocios, page);

    await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/tus negocios/i)).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible({ timeout: 30_000 });

    await captureCheckpoint(page, testInfo, '04_administrar_negocios_page', true);
  });

  await runStep('Información General', async () => {
    await expect(page.getByText(/business plan/i)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole('button', { name: /cambiar plan/i }).or(page.getByRole('link', { name: /cambiar plan/i }))).toBeVisible({
      timeout: 20_000
    });
    await expect(page.getByText(/@/).first()).toBeVisible({ timeout: 20_000 });
    await expect(page.locator('section,div').filter({ hasText: /informaci[oó]n general/i }).first()).toBeVisible({
      timeout: 20_000
    });
  });

  await runStep('Detalles de la Cuenta', async () => {
    await expect(page.getByText(/cuenta creada/i)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/estado activo/i)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/idioma seleccionado/i)).toBeVisible({ timeout: 20_000 });
  });

  await runStep('Tus Negocios', async () => {
    await expect(page.getByText(/tus negocios/i)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole('button', { name: /agregar negocio/i }).or(page.getByRole('link', { name: /agregar negocio/i }))).toBeVisible({
      timeout: 20_000
    });
    await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible({ timeout: 20_000 });
  });

  const validateLegalPage = async (
    reportField: ReportField,
    linkTextRegex: RegExp,
    headingRegex: RegExp,
    screenshotName: string
  ): Promise<void> => {
    await runStep(reportField, async () => {
      const legalLink = await firstVisible(
        [page.getByRole('link', { name: linkTextRegex }), page.getByText(linkTextRegex)],
        20_000
      );

      const popupPromise = page.waitForEvent('popup', { timeout: 8_000 }).catch(() => null);
      await legalLink.click();
      await waitForUiAfterClick(page);

      const popup = await popupPromise;
      const legalPage = popup ?? page;

      await legalPage.waitForLoadState('domcontentloaded');
      await legalPage.waitForLoadState('networkidle', { timeout: 15_000 }).catch(() => undefined);

      await expect(legalPage.getByRole('heading', { name: headingRegex }).first()).toBeVisible({ timeout: 20_000 });
      const legalBody = await legalPage.locator('body').innerText();
      expect(legalBody.trim().length).toBeGreaterThan(150);

      await captureCheckpoint(legalPage, testInfo, screenshotName, true);
      finalReport[reportField].finalUrl = legalPage.url();

      if (popup) {
        await popup.close().catch(() => undefined);
        await page.bringToFront();
      } else {
        await page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => undefined);
      }

      await waitForUiAfterClick(page);
    });
  };

  await validateLegalPage(
    'Términos y Condiciones',
    /t[ée]rminos y condiciones/i,
    /t[ée]rminos y condiciones/i,
    '08_terminos_y_condiciones'
  );
  await validateLegalPage(
    'Política de Privacidad',
    /pol[íi]tica de privacidad/i,
    /pol[íi]tica de privacidad/i,
    '09_politica_de_privacidad'
  );

  await finalizeAndAssert();
});
