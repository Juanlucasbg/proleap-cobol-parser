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
  'Política de Privacidad'
];

function configuredStartUrl() {
  return process.env.SALEADS_START_URL || process.env.SALEADS_URL || process.env.BASE_URL || '';
}

async function waitForUi(page) {
  await page.waitForLoadState('domcontentloaded', { timeout: 30000 });
  await page.waitForTimeout(600);
}

async function captureCheckpoint(page, testInfo, name, fullPage = false) {
  await testInfo.attach(name, {
    body: await page.screenshot({ fullPage }),
    contentType: 'image/png'
  });
}

async function findFirstVisible(candidates, elementDescription) {
  for (const candidate of candidates) {
    const locator = candidate.first();
    try {
      await locator.waitFor({ state: 'visible', timeout: 4000 });
      return locator;
    } catch (error) {
      // Try next locator strategy.
    }
  }

  throw new Error(`Unable to find visible element: ${elementDescription}`);
}

async function clickAndWait(locator, page) {
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click();
  await waitForUi(page);
}

async function expandMiNegocioMenu(page) {
  const miNegocioAlreadyVisible = await page.getByText(/Mi Negocio/i).first().isVisible().catch(() => false);
  if (!miNegocioAlreadyVisible) {
    const negocioEntry = await findFirstVisible(
      [
        page.getByRole('button', { name: /^Negocio$/i }),
        page.getByText(/^Negocio$/i),
        page.getByText(/Negocio/i)
      ],
      'Negocio menu entry'
    );

    await clickAndWait(negocioEntry, page);
  }

  const miNegocioEntry = await findFirstVisible(
    [page.getByRole('button', { name: /Mi Negocio/i }), page.getByText(/Mi Negocio/i)],
    'Mi Negocio menu option'
  );

  await clickAndWait(miNegocioEntry, page);
}

async function openLegalDocument({
  page,
  context,
  linkRegex,
  headingRegex,
  evidenceName,
  testInfo
}) {
  const link = await findFirstVisible(
    [page.getByRole('link', { name: linkRegex }), page.getByText(linkRegex)],
    `legal link ${linkRegex}`
  );

  const popupPromise = context.waitForEvent('page', { timeout: 8000 }).catch(() => null);
  await link.click();
  const popup = await popupPromise;

  const legalPage = popup || page;
  await waitForUi(legalPage);

  const heading = await findFirstVisible(
    [legalPage.getByRole('heading', { name: headingRegex }), legalPage.getByText(headingRegex)],
    `legal heading ${headingRegex}`
  );
  await expect(heading).toBeVisible();

  const legalText = (await legalPage.locator('body').innerText()).replace(/\s+/g, ' ').trim();
  expect(legalText.length).toBeGreaterThan(120);

  await captureCheckpoint(legalPage, testInfo, evidenceName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => {});
    await waitForUi(page);
  }

  return finalUrl;
}

test('saleads_mi_negocio_full_test', async ({ page, context }, testInfo) => {
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, 'FAIL']));
  const errors = [];
  const legalUrls = {
    terminosYCondiciones: 'N/A',
    politicaDePrivacidad: 'N/A'
  };

  async function runValidation(field, callback) {
    try {
      await callback();
      report[field] = 'PASS';
    } catch (error) {
      report[field] = 'FAIL';
      errors.push(`${field}: ${error.message}`);
    }
  }

  await runValidation('Login', async () => {
    const startUrl = configuredStartUrl();
    if (page.url() === 'about:blank') {
      if (!startUrl) {
        throw new Error(
          'No starting URL configured. Set SALEADS_START_URL (or SALEADS_URL/BASE_URL) for the target environment.'
        );
      }

      await page.goto(startUrl, { waitUntil: 'domcontentloaded' });
      await waitForUi(page);
    }

    const googleSignIn = await findFirstVisible(
      [
        page.getByRole('button', {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i
        }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
        page.getByRole('link', { name: /google/i })
      ],
      'Google sign-in button'
    );

    await clickAndWait(googleSignIn, page);

    const accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
    if (await accountOption.isVisible({ timeout: 6000 }).catch(() => false)) {
      await clickAndWait(accountOption, page);
    }

    const sidebar = await findFirstVisible(
      [page.locator('aside'), page.getByRole('navigation')],
      'left sidebar navigation'
    );
    await expect(sidebar).toBeVisible();
    await expect(page.getByText(/Negocio/i).first()).toBeVisible();

    await captureCheckpoint(page, testInfo, '01-dashboard-loaded');
  });

  await runValidation('Mi Negocio menu', async () => {
    await expandMiNegocioMenu(page);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

    await captureCheckpoint(page, testInfo, '02-mi-negocio-menu-expanded');
  });

  await runValidation('Agregar Negocio modal', async () => {
    const agregarNegocioOption = await findFirstVisible(
      [page.getByRole('button', { name: /Agregar Negocio/i }), page.getByText(/Agregar Negocio/i)],
      'Agregar Negocio option'
    );
    await clickAndWait(agregarNegocioOption, page);

    const modalTitle = page.getByRole('heading', { name: /Crear Nuevo Negocio/i }).first();
    await expect(modalTitle).toBeVisible();

    const nombreInput = await findFirstVisible(
      [
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.locator(
          "xpath=//label[contains(normalize-space(.), 'Nombre del Negocio')]/following::input[1]"
        )
      ],
      'Nombre del Negocio input'
    );

    await expect(nombreInput).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();

    const cancelButton = await findFirstVisible(
      [page.getByRole('button', { name: /Cancelar/i }), page.getByText(/^Cancelar$/i)],
      'Cancelar button'
    );
    const crearButton = await findFirstVisible(
      [page.getByRole('button', { name: /Crear Negocio/i }), page.getByText(/Crear Negocio/i)],
      'Crear Negocio button'
    );
    await expect(cancelButton).toBeVisible();
    await expect(crearButton).toBeVisible();

    await captureCheckpoint(page, testInfo, '03-agregar-negocio-modal');

    await nombreInput.fill('Negocio Prueba Automatizacion');
    await clickAndWait(cancelButton, page);
    await expect(modalTitle).toBeHidden({ timeout: 10000 });
  });

  await runValidation('Administrar Negocios view', async () => {
    const administrarNegociosOption = page.getByText(/Administrar Negocios/i).first();
    if (!(await administrarNegociosOption.isVisible({ timeout: 3000 }).catch(() => false))) {
      await expandMiNegocioMenu(page);
    }

    await clickAndWait(
      await findFirstVisible([page.getByText(/Administrar Negocios/i)], 'Administrar Negocios option'),
      page
    );

    await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();

    await captureCheckpoint(page, testInfo, '04-administrar-negocios-page', true);
  });

  await runValidation('Información General', async () => {
    const section = page.locator('section, div').filter({ hasText: /Informaci[oó]n General/i }).first();
    await expect(section).toBeVisible();

    const sectionText = await section.innerText();
    expect(sectionText.trim().length).toBeGreaterThan(20);
    expect(sectionText).toMatch(/@/);
    await expect(section.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(section.getByRole('button', { name: /Cambiar Plan/i })).toBeVisible();
  });

  await runValidation('Detalles de la Cuenta', async () => {
    const section = page.locator('section, div').filter({ hasText: /Detalles de la Cuenta/i }).first();
    await expect(section).toBeVisible();
    await expect(section.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(section.getByText(/Estado activo/i)).toBeVisible();
    await expect(section.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runValidation('Tus Negocios', async () => {
    const section = page.locator('section, div').filter({ hasText: /Tus Negocios/i }).first();
    await expect(section).toBeVisible();
    await expect(section.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(section.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
  });

  await runValidation('Términos y Condiciones', async () => {
    legalUrls.terminosYCondiciones = await openLegalDocument({
      page,
      context,
      linkRegex: /T[eé]rminos y Condiciones/i,
      headingRegex: /T[eé]rminos y Condiciones/i,
      evidenceName: '08-terminos-y-condiciones',
      testInfo
    });
  });

  await runValidation('Política de Privacidad', async () => {
    legalUrls.politicaDePrivacidad = await openLegalDocument({
      page,
      context,
      linkRegex: /Pol[ií]tica de Privacidad/i,
      headingRegex: /Pol[ií]tica de Privacidad/i,
      evidenceName: '09-politica-de-privacidad',
      testInfo
    });
  });

  const finalReport = {
    ...report,
    'Términos y Condiciones URL': legalUrls.terminosYCondiciones,
    'Política de Privacidad URL': legalUrls.politicaDePrivacidad
  };

  await testInfo.attach('10-final-report', {
    body: Buffer.from(JSON.stringify(finalReport, null, 2), 'utf-8'),
    contentType: 'application/json'
  });

  console.log('SaleADS Mi Negocio final report:', JSON.stringify(finalReport, null, 2));

  expect(errors, `Validation failures:\n${errors.join('\n')}`).toEqual([]);
});
