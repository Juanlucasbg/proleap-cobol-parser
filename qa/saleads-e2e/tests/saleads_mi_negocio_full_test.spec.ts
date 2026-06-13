import { writeFile } from 'node:fs/promises';
import { expect, test, type Locator, type Page, type TestInfo } from '@playwright/test';

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
type FinalReport = Record<ReportField, StepStatus>;

const TEXT_PATTERNS = {
  informacionGeneral: /Informaci(?:o|\u00f3)n General/i,
  detallesCuenta: /Detalles de la Cuenta/i,
  tusNegocios: /Tus Negocios/i,
  seccionLegal: /Secci(?:o|\u00f3)n Legal/i,
  terminosCondiciones: /T(?:e|\u00e9)rminos y Condiciones/i,
  politicaPrivacidad: /Pol(?:i|\u00ed)tica de Privacidad/i,
  crearNuevoNegocio: /Crear Nuevo Negocio/i,
  nombreNegocio: /Nombre del Negocio/i,
} as const;

function createInitialReport(): FinalReport {
  return REPORT_FIELDS.reduce(
    (accumulator, field) => {
      accumulator[field] = 'FAIL';
      return accumulator;
    },
    {} as FinalReport,
  );
}

async function waitForUiToSettle(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded', { timeout: 10_000 }).catch(() => undefined);
  await page.waitForLoadState('networkidle', { timeout: 7_000 }).catch(() => undefined);
  await page.waitForTimeout(300);
}

async function firstVisible(candidates: Locator[], failureMessage: string): Promise<Locator> {
  for (const candidate of candidates) {
    const firstMatch = candidate.first();
    const isVisible = await firstMatch
      .waitFor({ state: 'visible', timeout: 2_500 })
      .then(() => true)
      .catch(() => false);

    if (isVisible) {
      return firstMatch;
    }
  }

  throw new Error(failureMessage);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiToSettle(page);
}

async function checkpoint(page: Page, testInfo: TestInfo, fileName: string, fullPage = false): Promise<void> {
  const screenshotPath = testInfo.outputPath(fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(fileName, { path: screenshotPath, contentType: 'image/png' });
}

async function ensureLoginPage(page: Page): Promise<void> {
  const configuredUrl =
    process.env.SALEADS_URL ?? process.env.BASE_URL ?? process.env.PLAYWRIGHT_TEST_BASE_URL;

  if (configuredUrl) {
    await page.goto(configuredUrl, { waitUntil: 'domcontentloaded' });
    await waitForUiToSettle(page);
    return;
  }

  if (page.url() === 'about:blank') {
    throw new Error(
      'No initial SaleADS page is open. Set SALEADS_URL (or BASE_URL) for cross-environment execution.',
    );
  }

  await waitForUiToSettle(page);
}

async function maybeSelectGoogleAccount(contextPages: Page[]): Promise<void> {
  const pages = [...contextPages].reverse();

  for (const candidatePage of pages) {
    const accountTarget = await firstVisible(
      [
        candidatePage.getByRole('button', { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, 'i') }),
        candidatePage.getByRole('link', { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, 'i') }),
        candidatePage.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, 'i')),
      ],
      'Google account picker not visible on this page.',
    ).catch(() => null);

    if (accountTarget) {
      await clickAndWait(candidatePage, accountTarget);
      return;
    }
  }
}

async function sectionByHeading(page: Page, headingPattern: RegExp, errorMessage: string): Promise<Locator> {
  const heading = await firstVisible(
    [
      page.getByRole('heading', { name: headingPattern }),
      page.getByText(headingPattern),
    ],
    errorMessage,
  );

  const section = heading.locator('xpath=ancestor::*[self::section or self::article or self::div][1]');
  await expect(section).toBeVisible();
  return section;
}

async function runStep(
  field: ReportField,
  report: FinalReport,
  failures: string[],
  action: () => Promise<void>,
): Promise<void> {
  try {
    await test.step(field, action);
    report[field] = 'PASS';
  } catch (error) {
    report[field] = 'FAIL';
    const message = error instanceof Error ? error.message : String(error);
    failures.push(`${field}: ${message}`);
  }
}

async function validateLegalDocument(
  appPage: Page,
  testInfo: TestInfo,
  linkPattern: RegExp,
  headingPattern: RegExp,
  screenshotName: string,
): Promise<string> {
  const legalLink = await firstVisible(
    [
      appPage.getByRole('link', { name: linkPattern }),
      appPage.getByRole('button', { name: linkPattern }),
      appPage.getByText(linkPattern),
    ],
    `Could not find legal link: ${linkPattern.toString()}`,
  );

  const popupPromise = appPage.context().waitForEvent('page', { timeout: 7_000 }).catch(() => null);
  await clickAndWait(appPage, legalLink);
  const popup = await popupPromise;

  const legalPage = popup ?? appPage;
  await waitForUiToSettle(legalPage);

  await firstVisible(
    [
      legalPage.getByRole('heading', { name: headingPattern }),
      legalPage.getByText(headingPattern),
    ],
    `Could not validate heading: ${headingPattern.toString()}`,
  );

  const bodyText = await legalPage.locator('body').innerText();
  expect(bodyText.trim().length).toBeGreaterThan(120);

  await checkpoint(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUiToSettle(appPage);
  } else {
    await appPage.goBack({ waitUntil: 'domcontentloaded' }).catch(async () => {
      const configuredUrl =
        process.env.SALEADS_URL ?? process.env.BASE_URL ?? process.env.PLAYWRIGHT_TEST_BASE_URL;
      if (configuredUrl) {
        await appPage.goto(configuredUrl, { waitUntil: 'domcontentloaded' });
      }
    });
    await waitForUiToSettle(appPage);
  }

  return finalUrl;
}

test('saleads_mi_negocio_full_test', async ({ page }, testInfo) => {
  const report = createInitialReport();
  const failures: string[] = [];
  const legalUrls: Record<string, string> = {};

  await runStep('Login', report, failures, async () => {
    await ensureLoginPage(page);

    const googleLoginControl = await firstVisible(
      [
        page.getByRole('button', { name: /google/i }),
        page.getByRole('link', { name: /google/i }),
        page.getByText(/Sign in with Google|Iniciar sesi(?:o|\u00f3)n con Google|Continuar con Google/i),
      ],
      'Login with Google control not found.',
    );

    const popupPromise = page.context().waitForEvent('page', { timeout: 7_000 }).catch(() => null);
    await clickAndWait(page, googleLoginControl);
    const popup = await popupPromise;

    if (popup) {
      await waitForUiToSettle(popup);
    }

    await maybeSelectGoogleAccount(page.context().pages());

    if (popup) {
      await popup.waitForClose({ timeout: 20_000 }).catch(() => undefined);
      await page.bringToFront();
    }

    await waitForUiToSettle(page);

    await firstVisible(
      [page.locator('main'), page.getByRole('main'), page.getByText(/Dashboard|Panel|Inicio/i)],
      'Main application interface not visible after login.',
    );

    await firstVisible(
      [page.locator('aside'), page.getByRole('navigation'), page.locator('[class*="sidebar"]')],
      'Left sidebar navigation not visible after login.',
    );

    await checkpoint(page, testInfo, '01-dashboard-loaded.png', true);
  });

  await runStep('Mi Negocio menu', report, failures, async () => {
    const miNegocioOption = await firstVisible(
      [
        page.getByRole('button', { name: /Mi Negocio/i }),
        page.getByRole('link', { name: /Mi Negocio/i }),
        page.getByText(/^Mi Negocio$/i),
      ],
      '"Mi Negocio" option is not visible in the left sidebar.',
    );

    await clickAndWait(page, miNegocioOption);

    await firstVisible(
      [
        page.getByRole('button', { name: /Agregar Negocio/i }),
        page.getByRole('link', { name: /Agregar Negocio/i }),
        page.getByText(/^Agregar Negocio$/i),
      ],
      '"Agregar Negocio" is not visible after expanding Mi Negocio.',
    );

    await firstVisible(
      [
        page.getByRole('button', { name: /Administrar Negocios/i }),
        page.getByRole('link', { name: /Administrar Negocios/i }),
        page.getByText(/^Administrar Negocios$/i),
      ],
      '"Administrar Negocios" is not visible after expanding Mi Negocio.',
    );

    await checkpoint(page, testInfo, '02-mi-negocio-expanded.png', false);
  });

  await runStep('Agregar Negocio modal', report, failures, async () => {
    const agregarNegocioOption = await firstVisible(
      [
        page.getByRole('button', { name: /Agregar Negocio/i }),
        page.getByRole('link', { name: /Agregar Negocio/i }),
        page.getByText(/^Agregar Negocio$/i),
      ],
      'Could not find "Agregar Negocio" option.',
    );

    await clickAndWait(page, agregarNegocioOption);

    const modal = await firstVisible(
      [page.getByRole('dialog', { name: TEXT_PATTERNS.crearNuevoNegocio }), page.getByRole('dialog')],
      '"Crear Nuevo Negocio" modal did not appear.',
    );

    await expect(modal.getByText(TEXT_PATTERNS.crearNuevoNegocio)).toBeVisible();

    const nombreNegocioInput = await firstVisible(
      [
        modal.getByLabel(TEXT_PATTERNS.nombreNegocio),
        modal.getByPlaceholder(TEXT_PATTERNS.nombreNegocio),
        modal.locator('input[name*="nombre" i]'),
      ],
      '"Nombre del Negocio" input is not visible in the modal.',
    );

    await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();

    const cancelButton = await firstVisible(
      [modal.getByRole('button', { name: /Cancelar/i }), modal.getByText(/^Cancelar$/i)],
      '"Cancelar" button is missing in modal.',
    );

    await firstVisible(
      [
        modal.getByRole('button', { name: /Crear Negocio/i }),
        modal.getByText(/^Crear Negocio$/i),
      ],
      '"Crear Negocio" button is missing in modal.',
    );

    await checkpoint(page, testInfo, '03-crear-negocio-modal.png', false);

    await nombreNegocioInput.click();
    await nombreNegocioInput.fill('Negocio Prueba Automatizacion');
    await clickAndWait(page, cancelButton);
    await expect(modal).toBeHidden();
  });

  await runStep('Administrar Negocios view', report, failures, async () => {
    const administrarOptions = [
      page.getByRole('button', { name: /Administrar Negocios/i }),
      page.getByRole('link', { name: /Administrar Negocios/i }),
      page.getByText(/^Administrar Negocios$/i),
    ];

    let administrarVisible = false;
    for (const option of administrarOptions) {
      const isOptionVisible = await option
        .first()
        .waitFor({ state: 'visible', timeout: 1_500 })
        .then(() => true)
        .catch(() => false);

      if (isOptionVisible) {
        administrarVisible = true;
        break;
      }
    }

    if (!administrarVisible) {
      const miNegocioOption = await firstVisible(
        [
          page.getByRole('button', { name: /Mi Negocio/i }),
          page.getByRole('link', { name: /Mi Negocio/i }),
          page.getByText(/^Mi Negocio$/i),
        ],
        'Could not re-expand "Mi Negocio" before opening "Administrar Negocios".',
      );

      await clickAndWait(page, miNegocioOption);
    }

    const administrarNegocios = await firstVisible(
      administrarOptions,
      '"Administrar Negocios" option is not visible.',
    );
    await clickAndWait(page, administrarNegocios);

    await firstVisible([page.getByText(TEXT_PATTERNS.informacionGeneral)], 'Missing "Informacion General" section.');
    await firstVisible([page.getByText(TEXT_PATTERNS.detallesCuenta)], 'Missing "Detalles de la Cuenta" section.');
    await firstVisible([page.getByText(TEXT_PATTERNS.tusNegocios)], 'Missing "Tus Negocios" section.');
    await firstVisible([page.getByText(TEXT_PATTERNS.seccionLegal)], 'Missing "Seccion Legal" section.');

    await checkpoint(page, testInfo, '04-administrar-negocios-page.png', true);
  });

  await runStep('Informacion General', report, failures, async () => {
    const informacionSection = await sectionByHeading(
      page,
      TEXT_PATTERNS.informacionGeneral,
      'Could not locate "Informacion General" section.',
    );

    const infoText = await informacionSection.innerText();
    const lines = infoText
      .split('\n')
      .map((line) => line.trim())
      .filter(Boolean);

    const emailRegex = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/;
    expect(lines.some((line) => emailRegex.test(line))).toBeTruthy();

    const possibleNameLines = lines.filter(
      (line) =>
        !emailRegex.test(line) &&
        !/Informaci(?:o|\u00f3)n General|BUSINESS PLAN|Cambiar Plan/i.test(line) &&
        line.length >= 3,
    );
    expect(possibleNameLines.length).toBeGreaterThan(0);

    expect(infoText).toMatch(/BUSINESS PLAN/i);
    await firstVisible(
      [
        informacionSection.getByRole('button', { name: /Cambiar Plan/i }),
        page.getByRole('button', { name: /Cambiar Plan/i }),
        page.getByText(/Cambiar Plan/i),
      ],
      '"Cambiar Plan" button is not visible.',
    );
  });

  await runStep('Detalles de la Cuenta', report, failures, async () => {
    const detallesSection = await sectionByHeading(
      page,
      TEXT_PATTERNS.detallesCuenta,
      'Could not locate "Detalles de la Cuenta" section.',
    );

    const detailsText = await detallesSection.innerText();
    expect(detailsText).toMatch(/Cuenta creada/i);
    expect(detailsText).toMatch(/Estado activo/i);
    expect(detailsText).toMatch(/Idioma seleccionado/i);
  });

  await runStep('Tus Negocios', report, failures, async () => {
    const negociosSection = await sectionByHeading(
      page,
      TEXT_PATTERNS.tusNegocios,
      'Could not locate "Tus Negocios" section.',
    );

    await firstVisible(
      [
        negociosSection.locator('li'),
        negociosSection.locator('tr'),
        negociosSection.locator('[role="row"]'),
        negociosSection.locator('[class*="business"]'),
      ],
      'Business list is not visible in "Tus Negocios".',
    );

    await firstVisible(
      [
        negociosSection.getByRole('button', { name: /Agregar Negocio/i }),
        negociosSection.getByRole('link', { name: /Agregar Negocio/i }),
        page.getByRole('button', { name: /Agregar Negocio/i }),
      ],
      '"Agregar Negocio" button is not visible in "Tus Negocios".',
    );

    await expect(negociosSection.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
  });

  await runStep('Terminos y Condiciones', report, failures, async () => {
    const finalUrl = await validateLegalDocument(
      page,
      testInfo,
      TEXT_PATTERNS.terminosCondiciones,
      TEXT_PATTERNS.terminosCondiciones,
      '05-terminos-y-condiciones.png',
    );
    legalUrls.terminosYCondiciones = finalUrl;
  });

  await runStep('Politica de Privacidad', report, failures, async () => {
    const finalUrl = await validateLegalDocument(
      page,
      testInfo,
      TEXT_PATTERNS.politicaPrivacidad,
      TEXT_PATTERNS.politicaPrivacidad,
      '06-politica-de-privacidad.png',
    );
    legalUrls.politicaDePrivacidad = finalUrl;
  });

  const finalReportArtifact = testInfo.outputPath('final-report.json');
  await writeFile(
    finalReportArtifact,
    JSON.stringify(
      {
        report,
        legalUrls,
        failures,
      },
      null,
      2,
    ),
    'utf-8',
  );
  await testInfo.attach('final-report', { path: finalReportArtifact, contentType: 'application/json' });

  console.log('SaleADS Mi Negocio final report:');
  console.table(report);
  console.log('Legal URLs:', legalUrls);

  expect(
    failures,
    failures.length > 0 ? `One or more workflow validations failed:\n- ${failures.join('\n- ')}` : '',
  ).toEqual([]);
});
