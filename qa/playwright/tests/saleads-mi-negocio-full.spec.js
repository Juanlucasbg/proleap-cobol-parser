const { test, expect } = require('@playwright/test');

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
];

async function waitForUi(page) {
  await page.waitForLoadState('domcontentloaded');
  try {
    await page.waitForLoadState('networkidle', { timeout: 7000 });
  } catch (error) {
    // Some pages keep background requests alive; continue with a short settle delay.
  }
  await page.waitForTimeout(500);
}

async function clickAndWait(locator, page) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function firstVisible(locators) {
  for (const locator of locators) {
    if (await locator.isVisible().catch(() => false)) {
      return locator;
    }
  }
  throw new Error('None of the candidate locators is visible.');
}

async function markCheckpoint(page, testInfo, fileName, fullPage = false) {
  await page.screenshot({
    path: testInfo.outputPath(fileName),
    fullPage,
  });
}

test('saleads_mi_negocio_full_test', async ({ page, context }, testInfo) => {
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, 'FAIL']));
  const failures = [];
  const evidenceUrls = {};

  const setPass = (field) => {
    report[field] = 'PASS';
  };

  const setFail = (field, error) => {
    report[field] = 'FAIL';
    failures.push(`${field}: ${error.message}`);
  };

  // Environment-agnostic bootstrap: do not hardcode domain.
  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.BASE_URL || process.env.SALEADS_URL;
  if (page.url() === 'about:blank' && loginUrl) {
    await page.goto(loginUrl, { waitUntil: 'domcontentloaded' });
  }
  await waitForUi(page);

  try {
    const sidebarAlreadyVisible = await firstVisible([
      page.locator('aside'),
      page.getByRole('navigation'),
    ])
      .then(async (loc) => loc.isVisible())
      .catch(() => false);

    if (!sidebarAlreadyVisible) {
      const googleButton = await firstVisible([
        page.getByRole('button', { name: /sign in with google/i }),
        page.getByRole('button', { name: /iniciar sesion con google/i }),
        page.getByRole('button', { name: /iniciar sesión con google/i }),
        page.getByRole('button', { name: /continuar con google/i }),
        page.getByText(/google/i).locator('..'),
      ]);

      const popupPromise = context.waitForEvent('page', { timeout: 10000 }).catch(() => null);
      await clickAndWait(googleButton, page);
      const popup = await popupPromise;

      if (popup) {
        await popup.waitForLoadState('domcontentloaded');
        const accountOption = popup.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });
        if (await accountOption.isVisible().catch(() => false)) {
          await accountOption.click();
        }
      } else {
        const accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true });
        if (await accountOption.isVisible().catch(() => false)) {
          await clickAndWait(accountOption, page);
        }
      }
    }

    await expect(firstVisible([page.locator('aside'), page.getByRole('navigation')])).resolves.toBeDefined();
    await markCheckpoint(page, testInfo, '01-dashboard-loaded.png', true);
    setPass('Login');
  } catch (error) {
    setFail('Login', error);
  }

  try {
    const miNegocio = await firstVisible([
      page.getByRole('button', { name: /^mi negocio$/i }),
      page.getByText(/^mi negocio$/i),
    ]);
    await clickAndWait(miNegocio, page);

    await expect(page.getByText(/agregar negocio/i)).toBeVisible();
    await expect(page.getByText(/administrar negocios/i)).toBeVisible();
    await markCheckpoint(page, testInfo, '02-mi-negocio-menu-expanded.png');
    setPass('Mi Negocio menu');
  } catch (error) {
    setFail('Mi Negocio menu', error);
  }

  try {
    const agregarNegocio = await firstVisible([
      page.getByRole('menuitem', { name: /agregar negocio/i }),
      page.getByRole('button', { name: /agregar negocio/i }),
      page.getByText(/^agregar negocio$/i),
    ]);
    await clickAndWait(agregarNegocio, page);

    await expect(page.getByText(/crear nuevo negocio/i)).toBeVisible();
    const businessNameInput = await firstVisible([
      page.getByLabel(/nombre del negocio/i),
      page.getByPlaceholder(/nombre del negocio/i),
      page.locator('input').filter({ hasText: /nombre del negocio/i }),
      page.locator('input[type="text"]').first(),
    ]);
    await expect(businessNameInput).toBeVisible();

    await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /cancelar/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /crear negocio/i })).toBeVisible();

    await businessNameInput.click();
    await businessNameInput.fill('Negocio Prueba Automatizacion');
    await clickAndWait(page.getByRole('button', { name: /cancelar/i }), page);

    await markCheckpoint(page, testInfo, '03-agregar-negocio-modal.png');
    setPass('Agregar Negocio modal');
  } catch (error) {
    setFail('Agregar Negocio modal', error);
  }

  try {
    const miNegocio = await firstVisible([
      page.getByRole('button', { name: /^mi negocio$/i }),
      page.getByText(/^mi negocio$/i),
    ]);
    if (!(await page.getByText(/administrar negocios/i).isVisible().catch(() => false))) {
      await clickAndWait(miNegocio, page);
    }

    await clickAndWait(page.getByText(/administrar negocios/i), page);

    await expect(page.getByText(/informacion general/i)).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByText(/seccion legal/i)).toBeVisible();

    await markCheckpoint(page, testInfo, '04-administrar-negocios-page.png', true);
    setPass('Administrar Negocios view');
  } catch (error) {
    setFail('Administrar Negocios view', error);
  }

  try {
    await expect(page.getByText(/business plan/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /cambiar plan/i })).toBeVisible();
    await expect(page.getByText(/@/)).toBeVisible();
    setPass('Informacion General');
  } catch (error) {
    setFail('Informacion General', error);
  }

  try {
    await expect(page.getByText(/cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/estado activo/i)).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i)).toBeVisible();
    setPass('Detalles de la Cuenta');
  } catch (error) {
    setFail('Detalles de la Cuenta', error);
  }

  try {
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /agregar negocio/i })).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
    setPass('Tus Negocios');
  } catch (error) {
    setFail('Tus Negocios', error);
  }

  try {
    const appPage = page;
    const termsLink = await firstVisible([
      appPage.getByRole('link', { name: /terminos y condiciones/i }),
      appPage.getByText(/terminos y condiciones/i),
      appPage.getByText(/términos y condiciones/i),
    ]);

    const popupPromise = context.waitForEvent('page', { timeout: 8000 }).catch(() => null);
    await termsLink.click();

    let legalPage = await popupPromise;
    if (!legalPage) {
      legalPage = appPage;
    }

    await waitForUi(legalPage);
    await expect(
      firstVisible([
        legalPage.getByRole('heading', { name: /terminos y condiciones/i }),
        legalPage.getByRole('heading', { name: /términos y condiciones/i }),
        legalPage.getByText(/terminos y condiciones/i),
        legalPage.getByText(/términos y condiciones/i),
      ]),
    ).resolves.toBeDefined();
    await expect(legalPage.locator('body')).not.toHaveText(/^(\s*)$/);

    evidenceUrls.terminosYCondicionesUrl = legalPage.url();
    await markCheckpoint(legalPage, testInfo, '08-terminos-y-condiciones.png', true);

    if (legalPage !== appPage) {
      await legalPage.close();
      await appPage.bringToFront();
      await waitForUi(appPage);
    }

    setPass('Terminos y Condiciones');
  } catch (error) {
    setFail('Terminos y Condiciones', error);
  }

  try {
    const appPage = page;
    const privacyLink = await firstVisible([
      appPage.getByRole('link', { name: /politica de privacidad/i }),
      appPage.getByText(/politica de privacidad/i),
      appPage.getByText(/política de privacidad/i),
    ]);

    const popupPromise = context.waitForEvent('page', { timeout: 8000 }).catch(() => null);
    await privacyLink.click();

    let legalPage = await popupPromise;
    if (!legalPage) {
      legalPage = appPage;
    }

    await waitForUi(legalPage);
    await expect(
      firstVisible([
        legalPage.getByRole('heading', { name: /politica de privacidad/i }),
        legalPage.getByRole('heading', { name: /política de privacidad/i }),
        legalPage.getByText(/politica de privacidad/i),
        legalPage.getByText(/política de privacidad/i),
      ]),
    ).resolves.toBeDefined();
    await expect(legalPage.locator('body')).not.toHaveText(/^(\s*)$/);

    evidenceUrls.politicaDePrivacidadUrl = legalPage.url();
    await markCheckpoint(legalPage, testInfo, '09-politica-de-privacidad.png', true);

    if (legalPage !== appPage) {
      await legalPage.close();
      await appPage.bringToFront();
      await waitForUi(appPage);
    }

    setPass('Politica de Privacidad');
  } catch (error) {
    setFail('Politica de Privacidad', error);
  }

  const finalReport = {
    testName: 'saleads_mi_negocio_full_test',
    goal: 'Login with Google and validate Mi Negocio workflow',
    report,
    evidenceUrls,
  };

  await testInfo.attach('final-report.json', {
    body: Buffer.from(JSON.stringify(finalReport, null, 2)),
    contentType: 'application/json',
  });

  if (failures.length > 0) {
    throw new Error(`Validation failures:\n- ${failures.join('\n- ')}`);
  }
});
