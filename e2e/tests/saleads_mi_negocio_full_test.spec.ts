import { expect, Locator, Page, test } from '@playwright/test';

const GOOGLE_ACCOUNT_EMAIL = 'juanlucasbarbiergarzon@gmail.com';

const REPORT_FIELDS = [
  'Login',
  'Mi Negocio menu',
  'Agregar Negocio modal',
  'Administrar Negocios view',
  'Informacion General',
  'Detalles de la Cuenta',
  'Tus Negocios',
  'Terminos y Condiciones',
  'Politica de Privacidad',
] as const;

type ReportField = (typeof REPORT_FIELDS)[number];
type StepStatus = 'PASS' | 'FAIL';

interface StepResult {
  status: StepStatus;
  details?: string;
  screenshot?: string;
  url?: string;
}

const asMessage = (error: unknown): string =>
  error instanceof Error ? error.message : String(error);

async function waitForUiToLoad(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForLoadState('networkidle', { timeout: 10_000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible({ timeout: 20_000 });
  await locator.click();
  await waitForUiToLoad(page);
}

async function tryVisible(locator: Locator): Promise<boolean> {
  return locator.isVisible().catch(() => false);
}

async function firstVisible(
  candidates: Locator[],
  timeoutMs = 15_000,
): Promise<Locator> {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    for (const candidate of candidates) {
      if (await tryVisible(candidate)) {
        return candidate;
      }
    }
    await candidates[0].page().waitForTimeout(250);
  }
  throw new Error('No visible element matched the candidate selectors.');
}

async function maybeSelectGoogleAccount(page: Page): Promise<boolean> {
  const accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false });
  if (await tryVisible(accountOption)) {
    await clickAndWait(page, accountOption);
    return true;
  }
  return false;
}

async function ensureOnLoginPage(page: Page): Promise<void> {
  if (page.url() !== 'about:blank') {
    await waitForUiToLoad(page);
    return;
  }

  const configuredBaseUrl = process.env.SALEADS_BASE_URL;
  if (!configuredBaseUrl) {
    throw new Error(
      'Browser started on about:blank and SALEADS_BASE_URL is not set. ' +
        'Set SALEADS_BASE_URL to the current SaleADS environment login URL.',
    );
  }

  await page.goto(configuredBaseUrl, { waitUntil: 'domcontentloaded' });
  await waitForUiToLoad(page);
}

async function openLegalPageAndValidate(
  appPage: Page,
  linkTextPattern: RegExp,
  headingPattern: RegExp,
  screenshotName: string,
  testInfoOutputPath: (path: string) => string,
): Promise<{ screenshotPath: string; finalUrl: string }> {
  const context = appPage.context();

  const trigger = await firstVisible(
    [
      appPage.getByRole('link', { name: linkTextPattern }),
      appPage.getByRole('button', { name: linkTextPattern }),
      appPage.getByText(linkTextPattern),
    ],
    20_000,
  );

  const popupPromise = context.waitForEvent('page', { timeout: 7_000 }).catch(() => null);
  await clickAndWait(appPage, trigger);
  const popup = await popupPromise;
  const legalPage = popup ?? appPage;

  await waitForUiToLoad(legalPage);

  const legalHeading = await firstVisible(
    [
      legalPage.getByRole('heading', { name: headingPattern }),
      legalPage.getByText(headingPattern),
    ],
    20_000,
  );
  await expect(legalHeading).toBeVisible();

  const legalContent = legalPage.locator('main, article, section, p, li').first();
  await expect(legalContent).toBeVisible({ timeout: 20_000 });

  const screenshotPath = testInfoOutputPath(screenshotName);
  await legalPage.screenshot({ path: screenshotPath, fullPage: true });
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUiToLoad(appPage);
  } else {
    await appPage.goBack({ waitUntil: 'domcontentloaded' }).catch(() => {});
    await waitForUiToLoad(appPage);
  }

  return { screenshotPath, finalUrl };
}

test('saleads_mi_negocio_full_test', async ({ page }, testInfo) => {
  const result: Record<ReportField, StepResult> = Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: 'FAIL', details: 'Not executed.' }]),
  ) as Record<ReportField, StepResult>;

  const runStep = async (
    field: ReportField,
    body: () => Promise<Pick<StepResult, 'screenshot' | 'url' | 'details'> | void>,
  ): Promise<void> => {
    try {
      const details = await body();
      result[field] = {
        status: 'PASS',
        ...details,
      };
    } catch (error) {
      result[field] = {
        status: 'FAIL',
        details: asMessage(error),
      };
    }
  };

  await runStep('Login', async () => {
    await ensureOnLoginPage(page);

    const loginButton = await firstVisible(
      [
        page.getByRole('button', { name: /sign in with google|google|iniciar sesi[oó]n|login/i }),
        page.getByRole('link', { name: /sign in with google|google|iniciar sesi[oó]n|login/i }),
        page.getByText(/sign in with google|continuar con google|iniciar sesi[oó]n con google/i),
      ],
      25_000,
    );

    const googlePopupPromise = page.context().waitForEvent('page', { timeout: 10_000 }).catch(() => null);
    await clickAndWait(page, loginButton);
    const googlePopup = await googlePopupPromise;

    if (googlePopup) {
      await waitForUiToLoad(googlePopup);
      await maybeSelectGoogleAccount(googlePopup);
    } else {
      await maybeSelectGoogleAccount(page);
    }

    const sidebar = await firstVisible(
      [
        page.locator('aside'),
        page.getByRole('navigation'),
        page.locator('[class*="sidebar"]'),
      ],
      40_000,
    );

    await expect(sidebar).toBeVisible();

    const dashboardShot = testInfo.outputPath('01_dashboard_loaded.png');
    await page.screenshot({ path: dashboardShot, fullPage: true });

    return { screenshot: dashboardShot };
  });

  await runStep('Mi Negocio menu', async () => {
    const negocioSection = await firstVisible(
      [
        page.getByRole('button', { name: /negocio/i }),
        page.getByRole('link', { name: /negocio/i }),
        page.getByText(/^Negocio$/i),
      ],
      20_000,
    );
    await clickAndWait(page, negocioSection);

    const miNegocio = await firstVisible(
      [
        page.getByRole('button', { name: /mi negocio/i }),
        page.getByRole('link', { name: /mi negocio/i }),
        page.getByText(/mi negocio/i),
      ],
      20_000,
    );
    await clickAndWait(page, miNegocio);

    await expect(page.getByText(/agregar negocio/i)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/administrar negocios/i)).toBeVisible({ timeout: 20_000 });

    const expandedMenuShot = testInfo.outputPath('02_mi_negocio_expanded.png');
    await page.screenshot({ path: expandedMenuShot, fullPage: true });

    return { screenshot: expandedMenuShot };
  });

  await runStep('Agregar Negocio modal', async () => {
    const addBusiness = await firstVisible(
      [
        page.getByRole('button', { name: /^agregar negocio$/i }),
        page.getByRole('link', { name: /^agregar negocio$/i }),
        page.getByText(/^Agregar Negocio$/i),
      ],
      20_000,
    );
    await clickAndWait(page, addBusiness);

    const modal = await firstVisible(
      [page.getByRole('dialog'), page.locator('[role="dialog"]'), page.locator('.modal')],
      20_000,
    );
    await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();
    await expect(modal.getByText(/nombre del negocio/i)).toBeVisible();
    await expect(modal.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    await expect(modal.getByRole('button', { name: /cancelar/i })).toBeVisible();
    await expect(modal.getByRole('button', { name: /crear negocio/i })).toBeVisible();

    const businessInput = modal.getByRole('textbox', { name: /nombre del negocio/i });
    if (await tryVisible(businessInput)) {
      await businessInput.click();
      await businessInput.fill('Negocio Prueba Automatizacion');
    }

    const modalShot = testInfo.outputPath('03_agregar_negocio_modal.png');
    await page.screenshot({ path: modalShot, fullPage: true });

    await clickAndWait(page, modal.getByRole('button', { name: /cancelar/i }));

    return { screenshot: modalShot };
  });

  await runStep('Administrar Negocios view', async () => {
    const administrarVisible = await tryVisible(page.getByText(/administrar negocios/i));
    if (!administrarVisible) {
      const miNegocio = await firstVisible(
        [
          page.getByRole('button', { name: /mi negocio/i }),
          page.getByRole('link', { name: /mi negocio/i }),
          page.getByText(/mi negocio/i),
        ],
        15_000,
      );
      await clickAndWait(page, miNegocio);
    }

    const administrarNegocios = await firstVisible(
      [
        page.getByRole('link', { name: /administrar negocios/i }),
        page.getByRole('button', { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i),
      ],
      20_000,
    );
    await clickAndWait(page, administrarNegocios);

    await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/tus negocios/i)).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible({ timeout: 30_000 });

    const accountPageShot = testInfo.outputPath('04_administrar_negocios_view.png');
    await page.screenshot({ path: accountPageShot, fullPage: true });

    return { screenshot: accountPageShot };
  });

  await runStep('Informacion General', async () => {
    await expect(page.getByText(/business plan/i)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole('button', { name: /cambiar plan/i })).toBeVisible({ timeout: 20_000 });

    const nameCandidate = page
      .locator('main,section,div')
      .filter({ hasText: /nombre|name/i })
      .first();
    const emailCandidate = page.getByText(/@/).first();

    await expect(nameCandidate).toBeVisible({ timeout: 20_000 });
    await expect(emailCandidate).toBeVisible({ timeout: 20_000 });
  });

  await runStep('Detalles de la Cuenta', async () => {
    await expect(page.getByText(/cuenta creada/i)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/estado activo|activo/i)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText(/idioma seleccionado|idioma/i)).toBeVisible({ timeout: 20_000 });
  });

  await runStep('Tus Negocios', async () => {
    await expect(page.getByText(/tus negocios/i)).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole('button', { name: /^agregar negocio$/i })).toBeVisible({
      timeout: 20_000,
    });
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible({ timeout: 20_000 });
  });

  await runStep('Terminos y Condiciones', async () => {
    const details = await openLegalPageAndValidate(
      page,
      /t[eé]rminos\s*y\s*condiciones|terminos\s*y\s*condiciones/i,
      /t[eé]rminos\s*y\s*condiciones|terminos\s*y\s*condiciones/i,
      '05_terminos_y_condiciones.png',
      (filePath) => testInfo.outputPath(filePath),
    );
    return { screenshot: details.screenshotPath, url: details.finalUrl };
  });

  await runStep('Politica de Privacidad', async () => {
    const details = await openLegalPageAndValidate(
      page,
      /pol[ií]tica\s+de\s+privacidad|politica\s+de\s+privacidad/i,
      /pol[ií]tica\s+de\s+privacidad|politica\s+de\s+privacidad/i,
      '06_politica_de_privacidad.png',
      (filePath) => testInfo.outputPath(filePath),
    );
    return { screenshot: details.screenshotPath, url: details.finalUrl };
  });

  const lines = REPORT_FIELDS.map((field) => {
    const step = result[field];
    const parts = [`- ${field}: ${step.status}`];
    if (step.url) parts.push(`url=${step.url}`);
    if (step.screenshot) parts.push(`screenshot=${step.screenshot}`);
    if (step.details && step.status === 'FAIL') parts.push(`details=${step.details}`);
    return parts.join(' | ');
  });

  const finalReport = `# saleads_mi_negocio_full_test report\n\n${lines.join('\n')}\n`;
  await testInfo.attach('final-report', {
    body: finalReport,
    contentType: 'text/markdown',
  });
  console.log(finalReport);

  const failed = REPORT_FIELDS.filter((field) => result[field].status === 'FAIL');
  expect(
    failed,
    `Some validations failed:\n${finalReport}`,
  ).toEqual([]);
});
