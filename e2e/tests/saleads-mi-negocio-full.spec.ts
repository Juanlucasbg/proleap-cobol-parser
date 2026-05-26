import { expect, type Locator, type Page, test } from '@playwright/test';

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
type StepStatus = 'PASS' | 'FAIL';

type StepResult = {
  detail: string;
  status: StepStatus;
  url?: string;
};

type Report = Record<ReportField, StepResult>;

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForLoadState('networkidle').catch(() => {
    // Some environments keep background requests open; domcontentloaded is enough.
  });
}

async function firstVisible(locators: Locator[], timeoutMs = 15000): Promise<Locator> {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeoutMs) {
    for (const locator of locators) {
      if (await locator.first().isVisible().catch(() => false)) {
        return locator.first();
      }
    }
    await locators[0].page().waitForTimeout(250);
  }
  throw new Error('No expected visible element was found.');
}

async function firstVisibleOrNull(locators: Locator[], timeoutMs = 3000): Promise<Locator | null> {
  try {
    return await firstVisible(locators, timeoutMs);
  } catch {
    return null;
  }
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible({ timeout: 15000 });
  await locator.click();
  await waitForUi(page);
}

async function ensureInitialLoginPage(page: Page): Promise<void> {
  if (page.url() !== 'about:blank') {
    await waitForUi(page);
    return;
  }

  const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL;
  if (!loginUrl) {
    throw new Error(
      'The browser started on about:blank. Provide SALEADS_LOGIN_URL (or SALEADS_BASE_URL) to run in this environment.'
    );
  }

  await page.goto(loginUrl, { waitUntil: 'domcontentloaded' });
  await waitForUi(page);
}

async function clickLoginWithGoogle(page: Page): Promise<void> {
  const googleTrigger = await firstVisible([
    page.getByRole('button', { name: /google/i }),
    page.getByRole('link', { name: /google/i }),
    page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i)
  ]);

  const popupPromise = page.context().waitForEvent('page', { timeout: 8000 }).catch(() => null);
  await googleTrigger.click();
  await waitForUi(page);

  const popup = await popupPromise;
  if (popup) {
    await popup.waitForLoadState('domcontentloaded');
    const accountOption = popup.getByText('juanlucasbarbiergarzon@gmail.com').first();
    if (await accountOption.isVisible().catch(() => false)) {
      await accountOption.click();
      await waitForUi(popup);
    }

    await popup.waitForEvent('close', { timeout: 60000 }).catch(async () => {
      // In some Google flows the popup might remain open; close it once account selection is complete.
      await popup.close().catch(() => undefined);
    });
    await page.bringToFront();
  } else {
    const accountOption = page.getByText('juanlucasbarbiergarzon@gmail.com').first();
    if (await accountOption.isVisible({ timeout: 8000 }).catch(() => false)) {
      await clickAndWait(page, accountOption);
    }
  }

  await waitForUi(page);
}

async function locateSidebar(page: Page): Promise<Locator> {
  return firstVisible([
    page.locator('aside').first(),
    page.locator('nav').first(),
    page.getByRole('navigation').first()
  ]);
}

async function openLegalLink(
  page: Page,
  linkLabel: RegExp,
  expectedHeading: RegExp,
  screenshotPath: string
): Promise<string> {
  const appUrlBeforeLegal = page.url();
  const link = await firstVisible([
    page.getByRole('link', { name: linkLabel }),
    page.getByRole('button', { name: linkLabel }),
    page.getByText(linkLabel)
  ]);

  const popupPromise = page.context().waitForEvent('page', { timeout: 7000 }).catch(() => null);
  await link.click();

  const popup = await popupPromise;
  const legalPage = popup ?? page;
  await waitForUi(legalPage);

  const legalHeading = await firstVisible([
    legalPage.getByRole('heading', { name: expectedHeading }),
    legalPage.getByText(expectedHeading)
  ]);
  await expect(legalHeading).toBeVisible({ timeout: 20000 });
  await expect(
    legalPage.getByText(
      /t[eé]rminos|condiciones|privacidad|protecci[oó]n de datos|informaci[oó]n personal|responsabilidad/i
    ).first()
  ).toBeVisible({ timeout: 20000 });

  await legalPage.screenshot({ fullPage: true, path: screenshotPath });
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
  } else if (page.url() !== appUrlBeforeLegal) {
    await page.goBack({ waitUntil: 'domcontentloaded' }).catch(async () => {
      await page.goto(appUrlBeforeLegal, { waitUntil: 'domcontentloaded' });
    });
  }

  await waitForUi(page);
  return finalUrl;
}

test('saleads_mi_negocio_full_test', async ({ page }, testInfo) => {
  const report = REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = { status: 'FAIL', detail: 'Not executed.' };
    return acc;
  }, {} as Report);

  const setStepPass = (field: ReportField, detail = 'Validated successfully.', url?: string): void => {
    report[field] = { status: 'PASS', detail, url };
  };

  const setStepFail = (field: ReportField, error: unknown): void => {
    const detail = error instanceof Error ? error.message : String(error);
    report[field] = { status: 'FAIL', detail };
  };

  const runStep = async (
    field: ReportField,
    body: () => Promise<void | { detail?: string; url?: string }>
  ): Promise<void> => {
    try {
      const result = await body();
      setStepPass(field, result?.detail ?? 'Validated successfully.', result?.url);
    } catch (error) {
      setStepFail(field, error);
    }
  };

  await runStep('Login', async () => {
    await ensureInitialLoginPage(page);
    await clickLoginWithGoogle(page);

    const sidebar = await locateSidebar(page);
    await expect(sidebar).toBeVisible({ timeout: 30000 });
    await page.getByText(/negocio|dashboard|inicio/i).first().waitFor({ state: 'visible', timeout: 30000 });
    await page.screenshot({ fullPage: true, path: testInfo.outputPath('01-dashboard-loaded.png') });
  });

  await runStep('Mi Negocio menu', async () => {
    const negocioSection = await firstVisibleOrNull([
      page.getByRole('button', { name: /^negocio$/i }),
      page.getByRole('link', { name: /^negocio$/i }),
      page.getByText(/^negocio$/i)
    ]);
    if (negocioSection) {
      await clickAndWait(page, negocioSection);
    }

    const miNegocioOption = await firstVisible([
      page.getByRole('button', { name: /mi negocio/i }),
      page.getByRole('link', { name: /mi negocio/i }),
      page.getByText(/mi negocio/i)
    ]);
    await clickAndWait(page, miNegocioOption);

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible({ timeout: 15000 });
    await page.screenshot({ fullPage: true, path: testInfo.outputPath('02-mi-negocio-expanded.png') });
  });

  await runStep('Agregar Negocio modal', async () => {
    const addBusinessAction = await firstVisible([
      page.getByRole('button', { name: /agregar negocio/i }),
      page.getByRole('link', { name: /agregar negocio/i }),
      page.getByText(/agregar negocio/i)
    ]);
    await clickAndWait(page, addBusinessAction);

    await expect(page.getByText(/crear nuevo negocio/i).first()).toBeVisible({ timeout: 15000 });
    const businessNameInput = await firstVisible([
      page.getByLabel(/nombre del negocio/i),
      page.getByPlaceholder(/nombre del negocio/i),
      page.locator('input[name*="nombre" i], input[id*="nombre" i], input[placeholder*="nombre" i]').first()
    ]);
    await expect(businessNameInput).toBeVisible({ timeout: 15000 });

    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole('button', { name: /cancelar/i })).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole('button', { name: /crear negocio/i })).toBeVisible({ timeout: 15000 });
    await page.screenshot({ fullPage: true, path: testInfo.outputPath('03-crear-negocio-modal.png') });

    await businessNameInput.fill('Negocio Prueba Automatización');
    await clickAndWait(page, page.getByRole('button', { name: /cancelar/i }));
  });

  await runStep('Administrar Negocios view', async () => {
    const miNegocioOption = await firstVisible([
      page.getByRole('button', { name: /mi negocio/i }),
      page.getByRole('link', { name: /mi negocio/i }),
      page.getByText(/mi negocio/i)
    ]);
    if (!(await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false))) {
      await clickAndWait(page, miNegocioOption);
    }

    const manageBusinesses = await firstVisible([
      page.getByRole('button', { name: /administrar negocios/i }),
      page.getByRole('link', { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i)
    ]);
    await clickAndWait(page, manageBusinesses);

    await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/secci[oó]n legal/i).first()).toBeVisible({ timeout: 20000 });
    await page.screenshot({ fullPage: true, path: testInfo.outputPath('04-administrar-negocios-page.png') });
  });

  await runStep('Información General', async () => {
    await expect(page.getByText(/business plan/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole('button', { name: /cambiar plan/i })).toBeVisible({ timeout: 15000 });

    // User name and email are expected to appear in account overview.
    await expect(page.getByText(/@/).first()).toBeVisible({ timeout: 15000 });
    await expect(
      page.getByText(/[A-Za-zÀ-ÿ]+\s+[A-Za-zÀ-ÿ]+/).first()
    ).toBeVisible({ timeout: 15000 });
  });

  await runStep('Detalles de la Cuenta', async () => {
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/estado activo/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible({ timeout: 15000 });
  });

  await runStep('Tus Negocios', async () => {
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole('button', { name: /agregar negocio/i })).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({ timeout: 15000 });
  });

  await runStep('Términos y Condiciones', async () => {
    const finalUrl = await openLegalLink(
      page,
      /t[eé]rminos y condiciones/i,
      /t[eé]rminos y condiciones/i,
      testInfo.outputPath('05-terminos-y-condiciones.png')
    );
    return { detail: `Validated legal page. URL: ${finalUrl}`, url: finalUrl };
  });

  await runStep('Política de Privacidad', async () => {
    const finalUrl = await openLegalLink(
      page,
      /pol[ií]tica de privacidad/i,
      /pol[ií]tica de privacidad/i,
      testInfo.outputPath('06-politica-de-privacidad.png')
    );
    return { detail: `Validated legal page. URL: ${finalUrl}`, url: finalUrl };
  });

  const formattedReport = Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, report[field]])
  );

  await testInfo.attach('saleads-mi-negocio-final-report', {
    body: Buffer.from(JSON.stringify(formattedReport, null, 2), 'utf-8'),
    contentType: 'application/json'
  });

  console.log('\nFinal Report:');
  for (const field of REPORT_FIELDS) {
    const result = report[field];
    console.log(`- ${field}: ${result.status} (${result.detail})`);
    if (result.url) {
      console.log(`  URL: ${result.url}`);
    }
  }

  const failedChecks = REPORT_FIELDS.filter((field) => report[field].status === 'FAIL');
  expect(
    failedChecks,
    failedChecks.length
      ? `One or more validation groups failed: ${failedChecks.join(', ')}. Check the attached final report.`
      : 'All validation groups passed.'
  ).toEqual([]);
});
