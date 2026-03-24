const { test, expect } = require('@playwright/test');

const GOOGLE_ACCOUNT_EMAIL = 'juanlucasbarbiergarzon@gmail.com';

const REPORT_FIELDS = [
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

function createInitialReport() {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, 'FAIL']));
}

function sanitizeName(name) {
  return name.toLowerCase().replace(/[^a-z0-9]+/g, '-');
}

async function waitForUi(page) {
  await page.waitForTimeout(600);
  await page.waitForLoadState('domcontentloaded', { timeout: 10_000 }).catch(() => {});
  await page.waitForLoadState('networkidle', { timeout: 5_000 }).catch(() => {});
}

async function captureCheckpoint(page, testInfo, label, fullPage = true) {
  await page.screenshot({
    path: testInfo.outputPath(`${sanitizeName(label)}.png`),
    fullPage,
  });
}

async function isVisible(locator) {
  if ((await locator.count()) === 0) {
    return false;
  }

  return locator.first().isVisible().catch(() => false);
}

async function firstVisibleLocator(candidates) {
  for (const candidate of candidates) {
    if (await isVisible(candidate)) {
      return candidate.first();
    }
  }

  return null;
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function maybeSelectGoogleAccount(targetPage) {
  const accountOption = await firstVisibleLocator([
    targetPage.getByRole('button', { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, 'i') }),
    targetPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }),
  ]);

  if (accountOption) {
    await accountOption.click();
    await waitForUi(targetPage);
  }
}

async function ensureStartPage(page) {
  const providedUrl = process.env.SALEADS_URL || process.env.BASE_URL || process.env.E2E_URL;

  if (providedUrl) {
    await page.goto(providedUrl, { waitUntil: 'domcontentloaded' });
    await waitForUi(page);
    return;
  }

  if (page.url() === 'about:blank') {
    throw new Error(
      'No SaleADS URL configured. Set SALEADS_URL (or BASE_URL/E2E_URL), or start from a pre-opened login page.',
    );
  }

  await waitForUi(page);
}

async function openLegalDocument({
  appPage,
  linkCandidates,
  headingRegex,
  screenshotLabel,
  testInfo,
}) {
  const legalLink = await firstVisibleLocator(linkCandidates);
  if (!legalLink) {
    throw new Error(`Legal link not found for "${screenshotLabel}".`);
  }

  const popupPromise = appPage.context().waitForEvent('page', { timeout: 8_000 }).catch(() => null);
  await legalLink.click();
  await waitForUi(appPage);

  const popup = await popupPromise;
  const documentPage = popup || appPage;

  await documentPage.waitForLoadState('domcontentloaded', { timeout: 15_000 }).catch(() => {});
  await expect(
    firstVisibleLocator([
      documentPage.getByRole('heading', { name: headingRegex }),
      documentPage.getByText(headingRegex),
    ]),
  ).resolves.toBeTruthy();

  const bodyText = (await documentPage.locator('body').innerText()).trim();
  expect(bodyText.length, 'Legal content should be present').toBeGreaterThan(80);

  await captureCheckpoint(documentPage, testInfo, screenshotLabel, true);
  const finalUrl = documentPage.url();

  if (popup) {
    await popup.close().catch(() => {});
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack().catch(() => {});
    await waitForUi(appPage);
  }

  return finalUrl;
}

test('saleads_mi_negocio_full_test', async ({ page }, testInfo) => {
  const report = createInitialReport();
  const failures = [];
  const evidence = {};

  async function runStep(reportField, fn) {
    try {
      await fn();
      report[reportField] = 'PASS';
    } catch (error) {
      report[reportField] = 'FAIL';
      failures.push(`${reportField}: ${error.message}`);
    }
  }

  await runStep('Login', async () => {
    await ensureStartPage(page);

    const googleLoginButton = await firstVisibleLocator([
      page.getByRole('button', { name: /sign in with google/i }),
      page.getByRole('button', { name: /iniciar sesi[oó]n con google/i }),
      page.getByRole('button', { name: /continuar con google/i }),
      page.getByText(/sign in with google/i),
      page.getByText(/iniciar sesi[oó]n con google/i),
      page.getByText(/continuar con google/i),
    ]);

    if (!googleLoginButton) {
      throw new Error('Google login button was not found.');
    }

    const popupPromise = page.context().waitForEvent('page', { timeout: 8_000 }).catch(() => null);
    await googleLoginButton.click();
    await waitForUi(page);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState('domcontentloaded', { timeout: 15_000 }).catch(() => {});
      await maybeSelectGoogleAccount(popup);
      await popup.waitForClose({ timeout: 30_000 }).catch(() => {});
      await page.bringToFront();
    } else {
      await maybeSelectGoogleAccount(page);
    }

    await waitForUi(page);
    await expect(
      firstVisibleLocator([
        page.getByRole('navigation'),
        page.locator('aside'),
        page.locator('[class*="sidebar"]'),
      ]),
    ).resolves.toBeTruthy();
    await expect(page.locator('body')).toContainText(/negocio|mi negocio/i);

    await captureCheckpoint(page, testInfo, '01-dashboard-loaded', true);
  });

  await runStep('Mi Negocio menu', async () => {
    const negocioSection = await firstVisibleLocator([
      page.getByRole('button', { name: /^negocio$/i }),
      page.getByRole('link', { name: /^negocio$/i }),
      page.getByText(/^Negocio$/i),
    ]);

    if (negocioSection) {
      await clickAndWait(page, negocioSection);
    }

    const miNegocioOption = await firstVisibleLocator([
      page.getByRole('button', { name: /^mi negocio$/i }),
      page.getByRole('link', { name: /^mi negocio$/i }),
      page.getByText(/^Mi Negocio$/i),
    ]);

    if (!miNegocioOption) {
      throw new Error('"Mi Negocio" option was not found.');
    }

    await clickAndWait(page, miNegocioOption);
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

    await captureCheckpoint(page, testInfo, '02-mi-negocio-menu-expanded', true);
  });

  await runStep('Agregar Negocio modal', async () => {
    const agregarNegocio = await firstVisibleLocator([
      page.getByRole('button', { name: /^agregar negocio$/i }),
      page.getByRole('link', { name: /^agregar negocio$/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);

    if (!agregarNegocio) {
      throw new Error('"Agregar Negocio" option was not found.');
    }

    await clickAndWait(page, agregarNegocio);

    await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
    await expect(page.getByLabel(/Nombre del Negocio/i)).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /^Cancelar$/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /^Crear Negocio$/i })).toBeVisible();

    await captureCheckpoint(page, testInfo, '03-agregar-negocio-modal', true);

    const nombreNegocioInput = page.getByLabel(/Nombre del Negocio/i);
    await nombreNegocioInput.click();
    await nombreNegocioInput.fill('Negocio Prueba Automatización');
    await clickAndWait(page, page.getByRole('button', { name: /^Cancelar$/i }));
  });

  await runStep('Administrar Negocios view', async () => {
    if (!(await isVisible(page.getByText(/Administrar Negocios/i).first()))) {
      const miNegocio = await firstVisibleLocator([
        page.getByRole('button', { name: /^mi negocio$/i }),
        page.getByRole('link', { name: /^mi negocio$/i }),
        page.getByText(/^Mi Negocio$/i),
      ]);
      if (!miNegocio) {
        throw new Error('"Mi Negocio" was not found to re-expand the menu.');
      }
      await clickAndWait(page, miNegocio);
    }

    const administrarNegocios = await firstVisibleLocator([
      page.getByRole('button', { name: /^administrar negocios$/i }),
      page.getByRole('link', { name: /^administrar negocios$/i }),
      page.getByText(/^Administrar Negocios$/i),
    ]);

    if (!administrarNegocios) {
      throw new Error('"Administrar Negocios" option was not found.');
    }

    await clickAndWait(page, administrarNegocios);

    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();

    await captureCheckpoint(page, testInfo, '04-administrar-negocios-page', true);
  });

  await runStep('Información General', async () => {
    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();
    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /Cambiar Plan/i })).toBeVisible();
    await expect(page.getByText(/Nombre|Usuario|User/i).first()).toBeVisible();
    await expect(page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()).toBeVisible();
  });

  await runStep('Detalles de la Cuenta', async () => {
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep('Tus Negocios', async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /^Agregar Negocio$/i }).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.locator('body')).toContainText(/negocio/i);
  });

  await runStep('Términos y Condiciones', async () => {
    evidence.terminosUrl = await openLegalDocument({
      appPage: page,
      linkCandidates: [
        page.getByRole('link', { name: /T[eé]rminos y Condiciones/i }),
        page.getByRole('button', { name: /T[eé]rminos y Condiciones/i }),
        page.getByText(/T[eé]rminos y Condiciones/i),
      ],
      headingRegex: /T[eé]rminos y Condiciones/i,
      screenshotLabel: '05-terminos-y-condiciones',
      testInfo,
    });
  });

  await runStep('Política de Privacidad', async () => {
    evidence.privacidadUrl = await openLegalDocument({
      appPage: page,
      linkCandidates: [
        page.getByRole('link', { name: /Pol[ií]tica de Privacidad/i }),
        page.getByRole('button', { name: /Pol[ií]tica de Privacidad/i }),
        page.getByText(/Pol[ií]tica de Privacidad/i),
      ],
      headingRegex: /Pol[ií]tica de Privacidad/i,
      screenshotLabel: '06-politica-de-privacidad',
      testInfo,
    });
  });

  const finalReport = {
    report,
    evidence,
    generatedAt: new Date().toISOString(),
  };

  // Keep explicit final report output for CI logs and artifact consumption.
  // eslint-disable-next-line no-console
  console.log('SALEADS_MI_NEGOCIO_FINAL_REPORT', JSON.stringify(finalReport, null, 2));

  await testInfo.attach('saleads-mi-negocio-final-report.json', {
    body: Buffer.from(JSON.stringify(finalReport, null, 2)),
    contentType: 'application/json',
  });

  expect(
    failures,
    `One or more workflow validations failed.\n${failures.join('\n')}\nFinal report: ${JSON.stringify(
      finalReport,
      null,
      2,
    )}`,
  ).toEqual([]);
});
