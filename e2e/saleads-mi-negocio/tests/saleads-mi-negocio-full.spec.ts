import { expect, type BrowserContext, type Locator, type Page, test, type TestInfo } from '@playwright/test';

type ReportStatus = 'PASS' | 'FAIL';
type Report = Record<string, ReportStatus>;

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

const GOOGLE_ACCOUNT_EMAIL = 'juanlucasbarbiergarzon@gmail.com';

async function waitForUi(page: Page): Promise<void> {
  await page.waitForTimeout(500);
  await page.waitForLoadState('domcontentloaded').catch(() => undefined);
  await page.waitForLoadState('networkidle', { timeout: 7000 }).catch(() => undefined);
}

async function anyVisible(locators: Locator[], timeout = 3000): Promise<Locator | null> {
  for (const locator of locators) {
    const target = locator.first();
    const visible = await target.isVisible({ timeout }).catch(() => false);
    if (visible) {
      return target;
    }
  }
  return null;
}

async function expectVisible(locators: Locator[], message: string): Promise<Locator> {
  const visible = await anyVisible(locators, 6000);
  expect.soft(Boolean(visible), message).toBeTruthy();

  if (!visible) {
    throw new Error(message);
  }

  return visible;
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await locator.waitFor({ state: 'visible', timeout: 20_000 });
  await locator.click();
  await waitForUi(page);
}

async function captureCheckpoint(page: Page, testInfo: TestInfo, name: string, fullPage = false): Promise<void> {
  const path = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(name, { path, contentType: 'image/png' });
}

async function selectGoogleAccountIfPrompted(page: Page): Promise<void> {
  const emailPattern = new RegExp(GOOGLE_ACCOUNT_EMAIL.replace('.', '\\.'), 'i');
  const accountOption = await anyVisible(
    [
      page.getByRole('button', { name: emailPattern }),
      page.getByRole('link', { name: emailPattern }),
      page.getByText(emailPattern)
    ],
    12_000
  );

  if (!accountOption) {
    return;
  }

  await accountOption.click();
  await waitForUi(page);
}

async function expandMiNegocioMenu(page: Page): Promise<void> {
  let miNegocioOption = await anyVisible(
    [
      page.getByRole('button', { name: /mi negocio/i }),
      page.getByRole('link', { name: /mi negocio/i }),
      page.getByText(/mi negocio/i)
    ],
    2000
  );

  if (!miNegocioOption) {
    const negocioSection = await expectVisible(
      [
        page.getByRole('button', { name: /^negocio$/i }),
        page.getByRole('link', { name: /^negocio$/i }),
        page.getByText(/^negocio$/i)
      ],
      "Could not find sidebar section 'Negocio'."
    );
    await clickAndWait(negocioSection, page);

    miNegocioOption = await expectVisible(
      [
        page.getByRole('button', { name: /mi negocio/i }),
        page.getByRole('link', { name: /mi negocio/i }),
        page.getByText(/mi negocio/i)
      ],
      "Could not find option 'Mi Negocio'."
    );
  }

  await clickAndWait(miNegocioOption, page);

  const submenuVisible = await anyVisible(
    [page.getByText(/agregar negocio/i), page.getByText(/administrar negocios/i)],
    1500
  );

  if (!submenuVisible) {
    await clickAndWait(miNegocioOption, page);
  }
}

async function validateLegalDocument(
  page: Page,
  context: BrowserContext,
  linkText: string,
  headingText: RegExp,
  screenshotName: string,
  testInfo: TestInfo
): Promise<string> {
  const appUrlBeforeClick = page.url();
  const link = await expectVisible(
    [
      page.getByRole('link', { name: new RegExp(linkText, 'i') }),
      page.getByRole('button', { name: new RegExp(linkText, 'i') }),
      page.getByText(new RegExp(linkText, 'i'))
    ],
    `Could not find legal link '${linkText}'.`
  );

  const [popup] = await Promise.all([
    context.waitForEvent('page', { timeout: 5000 }).catch(() => null),
    clickAndWait(link, page)
  ]);

  const legalPage = popup ?? page;
  await waitForUi(legalPage);

  await expectVisible(
    [legalPage.getByRole('heading', { name: headingText }), legalPage.getByText(headingText)],
    `Could not find heading '${headingText}'.`
  );

  await expectVisible(
    [
      legalPage.locator('main p'),
      legalPage.locator('article p'),
      legalPage.getByText(/t[eé]rminos|condiciones|privacidad|datos personales|uso/i)
    ],
    `Could not find legal content text for '${linkText}'.`
  );

  await captureCheckpoint(legalPage, testInfo, screenshotName, true);

  const finalUrl = legalPage.url();
  await testInfo.attach(`${screenshotName}-url`, {
    body: finalUrl,
    contentType: 'text/plain'
  });

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
    return finalUrl;
  }

  if (page.url() !== appUrlBeforeClick) {
    await page.goBack().catch(() => undefined);
    await waitForUi(page);
  }

  return finalUrl;
}

test('saleads_mi_negocio_full_test', async ({ context, page }, testInfo) => {
  const report: Report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, 'FAIL'])) as Report;
  const reportNotes: Record<string, string> = {};

  const runStep = async (field: string, action: () => Promise<void>): Promise<void> => {
    try {
      await action();
      report[field] = 'PASS';
      reportNotes[field] = 'OK';
    } catch (error) {
      report[field] = 'FAIL';
      reportNotes[field] = error instanceof Error ? error.message : String(error);
    }
  };

  await runStep('Login', async () => {
    const envUrl = process.env.SALEADS_URL;
    if (envUrl) {
      await page.goto(envUrl, { waitUntil: 'domcontentloaded' });
      await waitForUi(page);
    }

    if (page.url() === 'about:blank') {
      throw new Error(
        'No application URL loaded. Set SALEADS_URL before running, or open the SaleADS login page first in an attached session.'
      );
    }

    const loginButton = await expectVisible(
      [
        page.getByRole('button', { name: /google/i }),
        page.getByRole('link', { name: /google/i }),
        page.getByRole('button', { name: /sign in|iniciar sesi[oó]n|login/i }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google/i)
      ],
      "Could not find Google login button."
    );

    const [popup] = await Promise.all([
      context.waitForEvent('page', { timeout: 7000 }).catch(() => null),
      clickAndWait(loginButton, page)
    ]);

    if (popup) {
      await waitForUi(popup);
      await selectGoogleAccountIfPrompted(popup);
      await waitForUi(page);
    } else if (page.url().includes('accounts.google.com')) {
      await selectGoogleAccountIfPrompted(page);
      await waitForUi(page);
    }

    await expectVisible(
      [page.locator('aside'), page.getByRole('navigation'), page.getByText(/negocio|dashboard|panel/i)],
      'Main application shell/sidebar is not visible after login.'
    );

    await captureCheckpoint(page, testInfo, '01-dashboard-loaded');
  });

  await runStep('Mi Negocio menu', async () => {
    await expectVisible([page.locator('aside'), page.getByRole('navigation')], 'Sidebar not visible.');
    await expandMiNegocioMenu(page);

    await expectVisible([page.getByText(/agregar negocio/i)], "Submenu item 'Agregar Negocio' not visible.");
    await expectVisible([page.getByText(/administrar negocios/i)], "Submenu item 'Administrar Negocios' not visible.");

    await captureCheckpoint(page, testInfo, '02-mi-negocio-menu-expanded');
  });

  await runStep('Agregar Negocio modal', async () => {
    const agregarNegocio = await expectVisible(
      [
        page.getByRole('menuitem', { name: /agregar negocio/i }),
        page.getByRole('link', { name: /agregar negocio/i }),
        page.getByRole('button', { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i)
      ],
      "Could not find 'Agregar Negocio' button."
    );
    await clickAndWait(agregarNegocio, page);

    const modalTitle = await expectVisible(
      [page.getByRole('heading', { name: /crear nuevo negocio/i }), page.getByText(/crear nuevo negocio/i)],
      "Modal title 'Crear Nuevo Negocio' was not found."
    );
    await expectVisible([page.getByLabel(/nombre del negocio/i), page.getByPlaceholder(/nombre del negocio/i)], "Input 'Nombre del Negocio' not found.");
    await expectVisible([page.getByText(/tienes 2 de 3 negocios/i)], "Text 'Tienes 2 de 3 negocios' not found in modal.");
    await expectVisible([page.getByRole('button', { name: /cancelar/i })], "Button 'Cancelar' not found in modal.");
    await expectVisible([page.getByRole('button', { name: /crear negocio/i })], "Button 'Crear Negocio' not found in modal.");

    await captureCheckpoint(page, testInfo, '03-agregar-negocio-modal');

    const nombreNegocioInput = await expectVisible(
      [page.getByLabel(/nombre del negocio/i), page.getByPlaceholder(/nombre del negocio/i)],
      "Could not focus on 'Nombre del Negocio' field."
    );
    await nombreNegocioInput.click();
    await nombreNegocioInput.fill('Negocio Prueba Automatización');
    await waitForUi(page);

    const cancelButton = await expectVisible([page.getByRole('button', { name: /cancelar/i })], "Could not find 'Cancelar' button.");
    await clickAndWait(cancelButton, page);

    await expect(modalTitle).toBeHidden({ timeout: 10_000 });
  });

  await runStep('Administrar Negocios view', async () => {
    await expandMiNegocioMenu(page);

    const administrarNegocios = await expectVisible(
      [
        page.getByRole('menuitem', { name: /administrar negocios/i }),
        page.getByRole('link', { name: /administrar negocios/i }),
        page.getByRole('button', { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i)
      ],
      "Could not find 'Administrar Negocios'."
    );
    await clickAndWait(administrarNegocios, page);

    await expectVisible(
      [page.getByRole('heading', { name: /informaci[oó]n general/i }), page.getByText(/informaci[oó]n general/i)],
      "Section 'Información General' not found."
    );
    await expectVisible(
      [page.getByRole('heading', { name: /detalles de la cuenta/i }), page.getByText(/detalles de la cuenta/i)],
      "Section 'Detalles de la Cuenta' not found."
    );
    await expectVisible(
      [page.getByRole('heading', { name: /tus negocios/i }), page.getByText(/tus negocios/i)],
      "Section 'Tus Negocios' not found."
    );
    await expectVisible(
      [page.getByRole('heading', { name: /secci[oó]n legal/i }), page.getByText(/secci[oó]n legal/i)],
      "Section 'Sección Legal' not found."
    );

    await captureCheckpoint(page, testInfo, '04-administrar-negocios-account-page', true);
  });

  await runStep('Información General', async () => {
    const informacionHeading = await expectVisible(
      [page.getByRole('heading', { name: /informaci[oó]n general/i }), page.getByText(/informaci[oó]n general/i)],
      "Could not find heading 'Información General'."
    );

    const section = informacionHeading.locator('xpath=ancestor::*[self::section or self::div][1]');
    const emailRegex = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;
    await expectVisible([section.getByText(emailRegex), page.getByText(emailRegex)], 'User email is not visible.');

    const sectionText = (await section.innerText().catch(() => '')) || '';
    const sanitizedText = sectionText.replace(/informaci[oó]n general|business plan|cambiar plan/gi, ' ');
    const hasLikelyName = /\b[A-ZÁÉÍÓÚÑ][a-záéíóúñ]{2,}(?:\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]{2,})+\b/.test(sanitizedText);
    expect.soft(hasLikelyName, 'User name is not clearly visible in Información General.').toBeTruthy();
    if (!hasLikelyName) {
      throw new Error('User name is not clearly visible in Información General.');
    }

    await expectVisible([section.getByText(/business plan/i), page.getByText(/business plan/i)], "Text 'BUSINESS PLAN' is not visible.");
    await expectVisible(
      [section.getByRole('button', { name: /cambiar plan/i }), page.getByRole('button', { name: /cambiar plan/i })],
      "Button 'Cambiar Plan' is not visible."
    );
  });

  await runStep('Detalles de la Cuenta', async () => {
    const detallesHeading = await expectVisible(
      [page.getByRole('heading', { name: /detalles de la cuenta/i }), page.getByText(/detalles de la cuenta/i)],
      "Could not find heading 'Detalles de la Cuenta'."
    );
    const section = detallesHeading.locator('xpath=ancestor::*[self::section or self::div][1]');

    await expectVisible([section.getByText(/cuenta creada/i), page.getByText(/cuenta creada/i)], "Text 'Cuenta creada' is not visible.");
    await expectVisible([section.getByText(/estado activo/i), page.getByText(/estado activo/i)], "Text 'Estado activo' is not visible.");
    await expectVisible(
      [section.getByText(/idioma seleccionado/i), page.getByText(/idioma seleccionado/i)],
      "Text 'Idioma seleccionado' is not visible."
    );
  });

  await runStep('Tus Negocios', async () => {
    const tusNegociosHeading = await expectVisible(
      [page.getByRole('heading', { name: /tus negocios/i }), page.getByText(/tus negocios/i)],
      "Could not find heading 'Tus Negocios'."
    );
    const section = tusNegociosHeading.locator('xpath=ancestor::*[self::section or self::div][1]');

    await expectVisible([section.locator('li'), section.locator('[role="listitem"]'), section.locator('table tbody tr')], 'Business list is not visible.');
    await expectVisible(
      [
        section.getByRole('button', { name: /agregar negocio/i }),
        section.getByRole('link', { name: /agregar negocio/i }),
        page.getByRole('button', { name: /agregar negocio/i })
      ],
      "Button 'Agregar Negocio' is not visible in Tus Negocios."
    );
    await expectVisible([section.getByText(/tienes 2 de 3 negocios/i), page.getByText(/tienes 2 de 3 negocios/i)], "Text 'Tienes 2 de 3 negocios' is not visible.");
  });

  await runStep('Términos y Condiciones', async () => {
    await validateLegalDocument(
      page,
      context,
      'Términos y Condiciones',
      /t[eé]rminos y condiciones/i,
      '08-terminos-y-condiciones',
      testInfo
    );
  });

  await runStep('Política de Privacidad', async () => {
    await validateLegalDocument(
      page,
      context,
      'Política de Privacidad',
      /pol[ií]tica de privacidad/i,
      '09-politica-de-privacidad',
      testInfo
    );
  });

  const markdownLines = [
    '# saleads_mi_negocio_full_test - Final Report',
    '',
    '| Field | Status | Notes |',
    '| --- | --- | --- |',
    ...REPORT_FIELDS.map((field) => `| ${field} | ${report[field]} | ${reportNotes[field] ?? ''} |`)
  ];
  const markdownReport = markdownLines.join('\n');
  await testInfo.attach('10-final-report', { body: markdownReport, contentType: 'text/markdown' });

  const failed = REPORT_FIELDS.filter((field) => report[field] === 'FAIL');
  expect(
    failed,
    `One or more workflow validations failed.\n${markdownReport}`
  ).toEqual([]);
});
