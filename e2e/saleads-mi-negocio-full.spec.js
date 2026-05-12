const { test, expect } = require('@playwright/test');

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

const EMAIL_TO_SELECT = 'juanlucasbarbiergarzon@gmail.com';

async function waitForUi(page) {
  await page.waitForLoadState('domcontentloaded');
  try {
    await page.waitForLoadState('networkidle', { timeout: 8000 });
  } catch (error) {
    // Some SPAs never reach networkidle; domcontentloaded is enough fallback.
  }
  await page.waitForTimeout(600);
}

async function firstVisible(locators) {
  for (const locator of locators) {
    try {
      const candidate = locator.first();
      if (await candidate.isVisible({ timeout: 2500 })) {
        return candidate;
      }
    } catch (error) {
      // Keep trying alternatives.
    }
  }
  return null;
}

async function clickAndWait(locator, page) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function captureCheckpoint(testInfo, page, name, fullPage = false) {
  const path = testInfo.outputPath(`${Date.now()}-${name}.png`);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(name, { path, contentType: 'image/png' });
}

async function openLegalLinkAndValidate({ page, testInfo, linkText, headingRegex, reportNotes }) {
  const link = await firstVisible([
    page.getByRole('link', { name: new RegExp(linkText, 'i') }),
    page.getByRole('button', { name: new RegExp(linkText, 'i') }),
    page.getByText(new RegExp(linkText, 'i')),
  ]);

  expect(link, `No se encontró el enlace/botón legal: ${linkText}`).toBeTruthy();

  const popupPromise = page.context().waitForEvent('page', { timeout: 5000 }).catch(() => null);
  await link.click();
  const popup = await popupPromise;
  const targetPage = popup || page;

  await waitForUi(targetPage);
  await expect(targetPage.getByRole('heading', { name: headingRegex })).toBeVisible();

  const bodyText = await targetPage.locator('body').innerText();
  expect(bodyText.trim().length).toBeGreaterThan(120);

  await captureCheckpoint(
    testInfo,
    targetPage,
    `legal-${linkText.toLowerCase().replace(/\s+/g, '-')}`,
    true,
  );
  reportNotes.push(`${linkText} URL: ${targetPage.url()}`);

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: 'domcontentloaded' });
    await waitForUi(page);
  }
}

test('saleads_mi_negocio_full_test', async ({ page }, testInfo) => {
  test.setTimeout(300000);

  const report = Object.fromEntries(REPORT_FIELDS.map((name) => [name, 'FAIL']));
  const reportNotes = [];

  async function runStep(reportKey, action) {
    try {
      await action();
      report[reportKey] = 'PASS';
    } catch (error) {
      report[reportKey] = `FAIL: ${error.message}`;
    }
  }

  await runStep('Login', async () => {
    const startUrl =
      process.env.SALEADS_LOGIN_URL ||
      process.env.SALEADS_URL ||
      process.env.BASE_URL ||
      process.env.PLAYWRIGHT_BASE_URL;

    if (startUrl) {
      await page.goto(startUrl, { waitUntil: 'domcontentloaded' });
      await waitForUi(page);
    } else {
      const currentUrl = page.url();
      expect(
        currentUrl && currentUrl !== 'about:blank',
        'Provide SALEADS_LOGIN_URL or start the test with the login page already open.',
      ).toBeTruthy();
    }

    const sidebarMaybeVisible = await firstVisible([
      page.locator('aside').filter({ hasText: /Negocio|Mi Negocio/i }),
      page.locator('nav').filter({ hasText: /Negocio|Mi Negocio/i }),
    ]);

    if (!sidebarMaybeVisible) {
      const loginButton = await firstVisible([
        page.getByRole('button', { name: /sign in with google|iniciar sesión con google|continuar con google/i }),
        page.getByText(/sign in with google|iniciar sesión con google|continuar con google/i),
      ]);

      expect(loginButton, 'No se encontró el botón de login con Google.').toBeTruthy();

      const popupPromise = page.context().waitForEvent('page', { timeout: 8000 }).catch(() => null);
      await loginButton.click();
      const popup = await popupPromise;
      const authPage = popup || page;
      await waitForUi(authPage);

      const accountOption = await firstVisible([
        authPage.getByText(EMAIL_TO_SELECT, { exact: false }),
        authPage.getByRole('button', { name: new RegExp(EMAIL_TO_SELECT, 'i') }),
        authPage.getByRole('link', { name: new RegExp(EMAIL_TO_SELECT, 'i') }),
      ]);

      if (accountOption) {
        await accountOption.click();
        await waitForUi(authPage);
      }

      if (popup) {
        await popup.waitForTimeout(1500);
      }
    }

    const mainInterface = await firstVisible([
      page.locator('main'),
      page.locator('[role="main"]'),
      page.locator('body'),
    ]);
    expect(mainInterface, 'La interfaz principal no está visible.').toBeTruthy();

    const sidebar = await firstVisible([
      page.locator('aside').filter({ hasText: /Negocio|Mi Negocio/i }),
      page.locator('nav').filter({ hasText: /Negocio|Mi Negocio/i }),
      page.getByText(/Mi Negocio/i),
    ]);
    expect(sidebar, 'No se encontró la barra lateral tras el login.').toBeTruthy();

    await captureCheckpoint(testInfo, page, 'dashboard-loaded');
  });

  await runStep('Mi Negocio menu', async () => {
    const negocioSection = await firstVisible([
      page.getByRole('button', { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i),
    ]);

    if (negocioSection) {
      await clickAndWait(negocioSection, page);
    }

    const miNegocio = await firstVisible([
      page.getByRole('button', { name: /Mi Negocio/i }),
      page.getByText(/Mi Negocio/i),
    ]);
    expect(miNegocio, 'No se encontró el menú "Mi Negocio".').toBeTruthy();
    await clickAndWait(miNegocio, page);

    await expect(page.getByText(/Agregar Negocio/i)).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i)).toBeVisible();
    await captureCheckpoint(testInfo, page, 'mi-negocio-menu-expanded');
  });

  await runStep('Agregar Negocio modal', async () => {
    const agregarNegocio = await firstVisible([
      page.getByRole('button', { name: /Agregar Negocio/i }),
      page.getByRole('link', { name: /Agregar Negocio/i }),
      page.getByText(/Agregar Negocio/i),
    ]);
    expect(agregarNegocio, 'No se encontró "Agregar Negocio".').toBeTruthy();
    await clickAndWait(agregarNegocio, page);

    await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
    await expect(page.getByLabel(/Nombre del Negocio/i)).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /Cancelar/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /Crear Negocio/i })).toBeVisible();
    await captureCheckpoint(testInfo, page, 'agregar-negocio-modal');

    const nombreInput = page.getByLabel(/Nombre del Negocio/i);
    await nombreInput.fill('Negocio Prueba Automatización');
    await clickAndWait(page.getByRole('button', { name: /Cancelar/i }), page);
  });

  await runStep('Administrar Negocios view', async () => {
    const miNegocio = await firstVisible([
      page.getByRole('button', { name: /Mi Negocio/i }),
      page.getByText(/Mi Negocio/i),
    ]);
    if (miNegocio) {
      const administrarVisible = await page.getByText(/Administrar Negocios/i).first().isVisible().catch(() => false);
      if (!administrarVisible) {
        await clickAndWait(miNegocio, page);
      }
    }

    const administrarNegocios = await firstVisible([
      page.getByRole('button', { name: /Administrar Negocios/i }),
      page.getByRole('link', { name: /Administrar Negocios/i }),
      page.getByText(/Administrar Negocios/i),
    ]);
    expect(administrarNegocios, 'No se encontró "Administrar Negocios".').toBeTruthy();
    await clickAndWait(administrarNegocios, page);

    await expect(page.getByText(/Información General/i)).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Sección Legal/i)).toBeVisible();
    await captureCheckpoint(testInfo, page, 'administrar-negocios', true);
  });

  await runStep('Información General', async () => {
    await expect(page.getByText(/Información General/i)).toBeVisible();
    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /Cambiar Plan/i })).toBeVisible();

    const bodyText = await page.locator('body').innerText();
    const emailRegex = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/;
    expect(emailRegex.test(bodyText), 'No se encontró un email visible.').toBeTruthy();

    const hasLikelyName =
      /Nombre|Usuario|Perfil/i.test(bodyText) ||
      /[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}/.test(bodyText);
    expect(hasLikelyName, 'No se identificó el nombre del usuario en pantalla.').toBeTruthy();
  });

  await runStep('Detalles de la Cuenta', async () => {
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runStep('Tus Negocios', async () => {
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /Agregar Negocio/i })).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();

    const listCandidates = page.locator(
      'section:has-text("Tus Negocios") li, section:has-text("Tus Negocios") [role="row"], section:has-text("Tus Negocios") [data-testid*="business"]',
    );
    const listCount = await listCandidates.count();
    expect(listCount > 0, 'No se detectaron elementos en la lista de negocios.').toBeTruthy();
  });

  await runStep('Términos y Condiciones', async () => {
    await openLegalLinkAndValidate({
      page,
      testInfo,
      linkText: 'Términos y Condiciones',
      headingRegex: /Términos y Condiciones/i,
      reportNotes,
    });
  });

  await runStep('Política de Privacidad', async () => {
    await openLegalLinkAndValidate({
      page,
      testInfo,
      linkText: 'Política de Privacidad',
      headingRegex: /Política de Privacidad/i,
      reportNotes,
    });
  });

  const summary = JSON.stringify({ report, notes: reportNotes }, null, 2);
  await testInfo.attach('final-report', {
    body: Buffer.from(summary, 'utf8'),
    contentType: 'application/json',
  });
  console.log('Final report:\n', summary);

  const failed = Object.entries(report).filter(([, status]) => !status.startsWith('PASS'));
  expect(failed, `Validation failed in: ${failed.map(([name]) => name).join(', ')}`).toEqual([]);
});
