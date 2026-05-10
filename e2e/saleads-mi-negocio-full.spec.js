const { test, expect } = require('@playwright/test');
const { mkdirSync } = require('node:fs');
const path = require('node:path');

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
];

async function waitForUi(page) {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(600);
}

async function findVisibleLocator(candidates, timeoutMs = 15000, errorMessage = 'No visible element found.') {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      const count = await candidate.count();
      if (count < 1) {
        continue;
      }

      const first = candidate.first();
      if (await first.isVisible().catch(() => false)) {
        return first;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  throw new Error(errorMessage);
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function saveCheckpoint(page, testInfo, fileName, fullPage = false) {
  const checkpointsDir = path.join(testInfo.outputDir, 'checkpoints');
  mkdirSync(checkpointsDir, { recursive: true });
  await page.screenshot({ path: path.join(checkpointsDir, fileName), fullPage });
}

async function ensureTermsOrPrivacyPage(targetPage, headingRegex) {
  const heading = await findVisibleLocator(
    [
      targetPage.getByRole('heading', { name: headingRegex }),
      targetPage.getByText(headingRegex),
    ],
    25000,
    'Legal heading was not visible.',
  );
  await expect(heading).toBeVisible();

  const bodyText = (await targetPage.locator('body').innerText()).replace(/\s+/g, ' ').trim();
  expect(bodyText.length, 'Legal content text should be visible.').toBeGreaterThan(120);
}

test('saleads_mi_negocio_full_test', async ({ page, context }, testInfo) => {
  test.setTimeout(300000);

  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, 'SKIPPED']));
  const failures = [];
  const legalUrls = {
    terminosYCondiciones: '',
    politicaDePrivacidad: '',
  };
  const accountEmail = process.env.SALEADS_GOOGLE_ACCOUNT || 'juanlucasbarbiergarzon@gmail.com';

  let blockDependentSteps = false;

  const runStep = async (reportField, stepFn, { critical = false } = {}) => {
    if (blockDependentSteps) {
      report[reportField] = 'SKIPPED';
      return;
    }

    try {
      await stepFn();
      report[reportField] = 'PASS';
    } catch (error) {
      report[reportField] = 'FAIL';
      failures.push(`${reportField}: ${error.message}`);
      if (critical) {
        blockDependentSteps = true;
      }
    }
  };

  await runStep(
    'Login',
    async () => {
      const configuredLoginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL;
      if (configuredLoginUrl) {
        await page.goto(configuredLoginUrl, { waitUntil: 'domcontentloaded' });
      }

      if (page.url() === 'about:blank') {
        throw new Error('Set SALEADS_LOGIN_URL (or SALEADS_URL) for the current environment login page.');
      }

      await waitForUi(page);

      const googleLoginButton = await findVisibleLocator(
        [
          page.getByRole('button', { name: /google/i }),
          page.getByRole('link', { name: /google/i }),
          page.getByText(/sign in with google|iniciar sesi[o\u00f3]n con google|continuar con google/i),
        ],
        30000,
        'Google login button was not found.',
      );

      const popupPromise = context.waitForEvent('page', { timeout: 10000 }).catch(() => null);
      await googleLoginButton.click();
      await waitForUi(page);

      const popup = await popupPromise;
      if (popup) {
        await popup.waitForLoadState('domcontentloaded');
        const accountOption = popup.getByText(accountEmail, { exact: true });
        if (await accountOption.count()) {
          await accountOption.first().click();
          await waitForUi(popup);
        }
      } else {
        const pageAccountOption = page.getByText(accountEmail, { exact: true });
        if (await pageAccountOption.count()) {
          await pageAccountOption.first().click();
          await waitForUi(page);
        }
      }

      const sidebar = await findVisibleLocator(
        [
          page.locator('aside'),
          page.getByRole('navigation'),
          page.locator('[class*="sidebar"]'),
        ],
        90000,
        'Main application sidebar was not visible after login.',
      );
      await expect(sidebar).toBeVisible();
      await expect(page.getByText(/negocio/i)).toBeVisible({ timeout: 30000 });

      await saveCheckpoint(page, testInfo, '01-dashboard-loaded.png');
    },
    { critical: true },
  );

  await runStep(
    'Mi Negocio menu',
    async () => {
      const sidebar = await findVisibleLocator(
        [page.locator('aside'), page.getByRole('navigation'), page.locator('[class*="sidebar"]')],
        20000,
      );

      await expect(
        await findVisibleLocator(
          [
            sidebar.getByText(/negocio/i),
            page.getByRole('button', { name: /^negocio$/i }),
            page.getByText(/^negocio$/i),
          ],
          15000,
          'Negocio section was not found in sidebar.',
        ),
      ).toBeVisible();

      const miNegocioOption = await findVisibleLocator(
        [
          sidebar.getByRole('button', { name: /mi negocio/i }),
          sidebar.getByRole('link', { name: /mi negocio/i }),
          sidebar.getByText(/mi negocio/i),
          page.getByRole('button', { name: /mi negocio/i }),
          page.getByRole('link', { name: /mi negocio/i }),
        ],
        20000,
        'Mi Negocio option was not found.',
      );
      await clickAndWait(page, miNegocioOption);

      await expect(page.getByText(/agregar negocio/i)).toBeVisible();
      await expect(page.getByText(/administrar negocios/i)).toBeVisible();

      await saveCheckpoint(page, testInfo, '02-mi-negocio-menu-expanded.png');
    },
    { critical: true },
  );

  await runStep(
    'Agregar Negocio modal',
    async () => {
      const addBusinessMenuItem = await findVisibleLocator(
        [
          page.getByRole('button', { name: /^agregar negocio$/i }),
          page.getByRole('link', { name: /^agregar negocio$/i }),
          page.getByText(/^agregar negocio$/i),
        ],
        20000,
        'Agregar Negocio option was not found.',
      );
      await clickAndWait(page, addBusinessMenuItem);

      const modal = await findVisibleLocator(
        [page.getByRole('dialog'), page.locator('[role="dialog"]')],
        15000,
        'Crear Nuevo Negocio modal did not appear.',
      );

      await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();
      const nameInput = await findVisibleLocator(
        [
          modal.getByLabel(/nombre del negocio/i),
          modal.getByPlaceholder(/nombre del negocio/i),
          modal.locator('input'),
        ],
        10000,
        'Nombre del Negocio input was not found.',
      );
      await expect(nameInput).toBeVisible();
      await expect(modal.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
      await expect(modal.getByRole('button', { name: /cancelar/i })).toBeVisible();
      await expect(modal.getByRole('button', { name: /crear negocio/i })).toBeVisible();

      await saveCheckpoint(page, testInfo, '03-agregar-negocio-modal.png');

      await nameInput.click();
      await nameInput.fill('Negocio Prueba Automatizacion');
      await clickAndWait(page, modal.getByRole('button', { name: /cancelar/i }));
    },
    { critical: true },
  );

  await runStep(
    'Administrar Negocios view',
    async () => {
      const administrarNegociosOption = page.getByText(/administrar negocios/i);
      if (!(await administrarNegociosOption.first().isVisible().catch(() => false))) {
        const miNegocioOption = await findVisibleLocator(
          [
            page.getByRole('button', { name: /mi negocio/i }),
            page.getByRole('link', { name: /mi negocio/i }),
            page.getByText(/mi negocio/i),
          ],
          15000,
          'Mi Negocio option could not be reopened.',
        );
        await clickAndWait(page, miNegocioOption);
      }

      await clickAndWait(page, administrarNegociosOption.first());

      await expect(page.getByText(/informaci[o\u00f3]n general/i)).toBeVisible({ timeout: 30000 });
      await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
      await expect(page.getByText(/tus negocios/i)).toBeVisible();
      await expect(page.getByText(/secci[o\u00f3]n legal/i)).toBeVisible();

      await saveCheckpoint(page, testInfo, '04-administrar-negocios-account-page.png', true);
    },
    { critical: true },
  );

  await runStep('Informacion General', async () => {
    const infoSection = await findVisibleLocator(
      [
        page.locator('section').filter({ has: page.getByText(/informaci[o\u00f3]n general/i) }),
        page.locator('div').filter({ has: page.getByText(/informaci[o\u00f3]n general/i) }),
      ],
      15000,
      'Informacion General section was not found.',
    );

    const infoText = await infoSection.innerText();
    const hasNameIndicator = /nombre|usuario|user/i.test(infoText) || /\b[A-Za-z]{2,}\s+[A-Za-z]{2,}\b/.test(infoText);
    expect(hasNameIndicator, 'User name was not visible in Informacion General.').toBeTruthy();
    expect(infoText, 'User email was not visible in Informacion General.').toMatch(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);

    await expect(infoSection.getByText(/business plan/i)).toBeVisible();
    await expect(
      await findVisibleLocator(
        [infoSection.getByRole('button', { name: /cambiar plan/i }), infoSection.getByText(/cambiar plan/i)],
        10000,
        'Cambiar Plan action was not visible.',
      ),
    ).toBeVisible();
  });

  await runStep('Detalles de la Cuenta', async () => {
    const detailsSection = await findVisibleLocator(
      [
        page.locator('section').filter({ has: page.getByText(/detalles de la cuenta/i) }),
        page.locator('div').filter({ has: page.getByText(/detalles de la cuenta/i) }),
      ],
      15000,
      'Detalles de la Cuenta section was not found.',
    );
    const detailsText = await detailsSection.innerText();

    expect(detailsText).toMatch(/cuenta creada/i);
    expect(detailsText).toMatch(/estado[^\n\r]*activo/i);
    expect(detailsText).toMatch(/idioma seleccionado/i);
  });

  await runStep('Tus Negocios', async () => {
    const businessesSection = await findVisibleLocator(
      [
        page.locator('section').filter({ has: page.getByText(/tus negocios/i) }),
        page.locator('div').filter({ has: page.getByText(/tus negocios/i) }),
      ],
      15000,
      'Tus Negocios section was not found.',
    );

    await expect(
      await findVisibleLocator(
        [businessesSection.getByRole('button', { name: /agregar negocio/i }), businessesSection.getByText(/agregar negocio/i)],
        10000,
        'Agregar Negocio button in Tus Negocios was not found.',
      ),
    ).toBeVisible();
    await expect(businessesSection.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();

    const sectionText = await businessesSection.innerText();
    expect(sectionText.length, 'Business list area should be visible in Tus Negocios.').toBeGreaterThan(60);
  });

  await runStep('Terminos y Condiciones', async () => {
    const legalSection = await findVisibleLocator(
      [
        page.locator('section').filter({ has: page.getByText(/secci[o\u00f3]n legal/i) }),
        page.locator('div').filter({ has: page.getByText(/secci[o\u00f3]n legal/i) }),
      ],
      15000,
      'Seccion Legal was not visible.',
    );

    const termsLink = await findVisibleLocator(
      [
        legalSection.getByRole('link', { name: /t[\u00e9e]rminos y condiciones/i }),
        legalSection.getByText(/t[\u00e9e]rminos y condiciones/i),
        page.getByRole('link', { name: /t[\u00e9e]rminos y condiciones/i }),
      ],
      15000,
      'Términos y Condiciones link was not found.',
    );

    const popupPromise = context.waitForEvent('page', { timeout: 8000 }).catch(() => null);
    await termsLink.click();

    const popup = await popupPromise;
    const legalPage = popup || page;
    await waitForUi(legalPage);

    await ensureTermsOrPrivacyPage(legalPage, /t[\u00e9e]rminos y condiciones/i);
    await saveCheckpoint(legalPage, testInfo, '05-terminos-y-condiciones.png', true);
    legalUrls.terminosYCondiciones = legalPage.url();

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await page.goBack({ waitUntil: 'domcontentloaded' });
      await waitForUi(page);
    }
  });

  await runStep('Politica de Privacidad', async () => {
    const legalSection = await findVisibleLocator(
      [
        page.locator('section').filter({ has: page.getByText(/secci[o\u00f3]n legal/i) }),
        page.locator('div').filter({ has: page.getByText(/secci[o\u00f3]n legal/i) }),
      ],
      15000,
      'Seccion Legal was not visible before privacy validation.',
    );

    const privacyLink = await findVisibleLocator(
      [
        legalSection.getByRole('link', { name: /pol[\u00edi]tica de privacidad/i }),
        legalSection.getByText(/pol[\u00edi]tica de privacidad/i),
        page.getByRole('link', { name: /pol[\u00edi]tica de privacidad/i }),
      ],
      15000,
      'Política de Privacidad link was not found.',
    );

    const popupPromise = context.waitForEvent('page', { timeout: 8000 }).catch(() => null);
    await privacyLink.click();

    const popup = await popupPromise;
    const legalPage = popup || page;
    await waitForUi(legalPage);

    await ensureTermsOrPrivacyPage(legalPage, /pol[\u00edi]tica de privacidad/i);
    await saveCheckpoint(legalPage, testInfo, '06-politica-de-privacidad.png', true);
    legalUrls.politicaDePrivacidad = legalPage.url();

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await page.goBack({ waitUntil: 'domcontentloaded' });
      await waitForUi(page);
    }
  });

  const finalReport = {
    ...report,
    legalUrls,
  };
  console.table(report);
  console.log(`Términos y Condiciones URL: ${legalUrls.terminosYCondiciones || 'N/A'}`);
  console.log(`Política de Privacidad URL: ${legalUrls.politicaDePrivacidad || 'N/A'}`);

  await testInfo.attach('saleads-mi-negocio-final-report', {
    body: JSON.stringify(finalReport, null, 2),
    contentType: 'application/json',
  });

  if (failures.length) {
    throw new Error(`One or more validations failed:\n${failures.join('\n')}`);
  }
});
