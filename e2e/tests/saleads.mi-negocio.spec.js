const fs = require('fs/promises');
const path = require('path');
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
  'Política de Privacidad'
];

function initializeReport() {
  return REPORT_FIELDS.reduce((acc, key) => {
    acc[key] = 'FAIL';
    return acc;
  }, {});
}

async function waitForUi(page) {
  await page.waitForLoadState('domcontentloaded', { timeout: 20000 }).catch(() => {});
  await page.waitForLoadState('networkidle', { timeout: 7000 }).catch(() => {});
  await page.waitForTimeout(700);
}

async function findVisibleLocator(page, locatorBuilders, timeout = 3000) {
  for (const buildLocator of locatorBuilders) {
    const locator = buildLocator().first();
    const hasElements = (await locator.count().catch(() => 0)) > 0;

    if (!hasElements) {
      continue;
    }

    const isVisible = await locator
      .waitFor({ state: 'visible', timeout })
      .then(() => true)
      .catch(() => false);

    if (isVisible) {
      return locator;
    }
  }

  return null;
}

async function clickByVisibleText(page, textRegex, description) {
  const locator = await findVisibleLocator(page, [
    () => page.getByRole('button', { name: textRegex }),
    () => page.getByRole('link', { name: textRegex }),
    () => page.getByRole('menuitem', { name: textRegex }),
    () => page.getByRole('tab', { name: textRegex }),
    () => page.getByText(textRegex)
  ]);

  if (!locator) {
    throw new Error(`No se encontró "${description}" usando texto visible.`);
  }

  await locator.click({ timeout: 15000 });
  await waitForUi(page);
}

async function expectVisibleText(page, textRegex, description) {
  const locator = await findVisibleLocator(page, [
    () => page.getByRole('heading', { name: textRegex }),
    () => page.getByRole('button', { name: textRegex }),
    () => page.getByRole('link', { name: textRegex }),
    () => page.getByText(textRegex)
  ]);

  if (!locator) {
    throw new Error(`No se visualiza "${description}".`);
  }
}

test('saleads_mi_negocio_full_test', async ({ page, context }, testInfo) => {
  const report = initializeReport();
  const failures = [];
  const legalUrls = {
    terminos: null,
    privacidad: null
  };

  const artifactDir = path.join(process.cwd(), 'artifacts');
  await fs.mkdir(artifactDir, { recursive: true });

  const capture = async (name, options = {}) => {
    const fullPage = Boolean(options.fullPage);
    const filePath = path.join(artifactDir, name);
    await page.screenshot({ path: filePath, fullPage });
    await testInfo.attach(name, { path: filePath, contentType: 'image/png' });
  };

  const runStep = async (key, fn) => {
    try {
      await fn();
      report[key] = 'PASS';
    } catch (error) {
      report[key] = 'FAIL';
      failures.push(`${key}: ${error.message}`);
    }
  };

  const startUrl = process.env.SALEADS_URL || process.env.BASE_URL || process.env.TARGET_URL;

  await runStep('Login', async () => {
    if (startUrl) {
      await page.goto(startUrl, { waitUntil: 'domcontentloaded' });
      await waitForUi(page);
    } else if (page.url() === 'about:blank') {
      throw new Error(
        'La prueba necesita SALEADS_URL, BASE_URL o TARGET_URL para abrir la pantalla de login.'
      );
    }

    const popupPromise = context.waitForEvent('page', { timeout: 10000 }).catch(() => null);
    await clickByVisibleText(
      page,
      /sign in with google|iniciar sesi[oó]n con google|continuar con google|acceder con google|google/i,
      'Sign in with Google'
    );

    const authPage = await popupPromise;
    const targetAuthPage = authPage || page;
    await waitForUi(targetAuthPage);

    const accountOption = await findVisibleLocator(targetAuthPage, [
      () => targetAuthPage.getByRole('button', { name: /juanlucasbarbiergarzon@gmail\.com/i }),
      () => targetAuthPage.getByRole('link', { name: /juanlucasbarbiergarzon@gmail\.com/i }),
      () => targetAuthPage.getByText(/juanlucasbarbiergarzon@gmail\.com/i)
    ]);

    if (accountOption) {
      await accountOption.click({ timeout: 15000 });
      await waitForUi(targetAuthPage);
    }

    if (authPage) {
      await authPage.waitForEvent('close', { timeout: 20000 }).catch(() => {});
      await page.bringToFront();
      await waitForUi(page);
    }

    await expectVisibleText(page, /negocio/i, 'navegación lateral');
    await capture('01-dashboard.png', { fullPage: true });
  });

  await runStep('Mi Negocio menu', async () => {
    const addBusinessVisible = Boolean(
      await findVisibleLocator(page, [
        () => page.getByRole('button', { name: /agregar negocio/i }),
        () => page.getByRole('link', { name: /agregar negocio/i }),
        () => page.getByText(/agregar negocio/i)
      ])
    );

    if (!addBusinessVisible) {
      const negocioVisible = Boolean(
        await findVisibleLocator(page, [
          () => page.getByRole('link', { name: /^negocio$/i }),
          () => page.getByRole('button', { name: /^negocio$/i }),
          () => page.getByText(/^negocio$/i)
        ])
      );

      if (negocioVisible) {
        await clickByVisibleText(page, /^negocio$/i, 'Negocio');
      }
    }

    await clickByVisibleText(page, /mi negocio/i, 'Mi Negocio');
    await expectVisibleText(page, /agregar negocio/i, 'Agregar Negocio');
    await expectVisibleText(page, /administrar negocios/i, 'Administrar Negocios');
    await capture('02-mi-negocio-menu-expanded.png');
  });

  await runStep('Agregar Negocio modal', async () => {
    await clickByVisibleText(page, /agregar negocio/i, 'Agregar Negocio');
    await expectVisibleText(page, /crear nuevo negocio/i, 'Crear Nuevo Negocio');
    await expectVisibleText(page, /nombre del negocio/i, 'Nombre del Negocio');
    await expectVisibleText(page, /tienes\s*2\s*de\s*3\s*negocios/i, 'Tienes 2 de 3 negocios');
    await expectVisibleText(page, /cancelar/i, 'Cancelar');
    await expectVisibleText(page, /crear negocio/i, 'Crear Negocio');
    await capture('03-agregar-negocio-modal.png');

    const businessNameInput = await findVisibleLocator(page, [
      () => page.getByLabel(/nombre del negocio/i),
      () => page.getByPlaceholder(/nombre del negocio/i),
      () => page.locator('input[type="text"]')
    ]);

    if (businessNameInput) {
      await businessNameInput.fill('Negocio Prueba Automatización');
      await waitForUi(page);
    }

    await clickByVisibleText(page, /cancelar/i, 'Cancelar');
  });

  await runStep('Administrar Negocios view', async () => {
    const adminVisible = Boolean(
      await findVisibleLocator(page, [
        () => page.getByRole('link', { name: /administrar negocios/i }),
        () => page.getByRole('button', { name: /administrar negocios/i }),
        () => page.getByText(/administrar negocios/i)
      ])
    );

    if (!adminVisible) {
      await clickByVisibleText(page, /mi negocio/i, 'Mi Negocio');
    }

    await clickByVisibleText(page, /administrar negocios/i, 'Administrar Negocios');
    await expectVisibleText(page, /informaci[oó]n general/i, 'Información General');
    await expectVisibleText(page, /detalles de la cuenta/i, 'Detalles de la Cuenta');
    await expectVisibleText(page, /tus negocios/i, 'Tus Negocios');
    await expectVisibleText(page, /secci[oó]n legal/i, 'Sección Legal');
    await capture('04-administrar-negocios-page.png', { fullPage: true });
  });

  await runStep('Información General', async () => {
    await expectVisibleText(page, /informaci[oó]n general/i, 'Información General');

    const emailLocator = await findVisibleLocator(page, [
      () => page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)
    ]);
    if (!emailLocator) {
      throw new Error('No se visualiza el email del usuario.');
    }

    const userNameHint = await findVisibleLocator(page, [
      () => page.getByText(/nombre/i),
      () => page.getByText(/usuario/i),
      () => page.getByText(/perfil/i)
    ]);
    if (!userNameHint) {
      throw new Error('No se detecta una referencia visible al nombre del usuario.');
    }

    await expectVisibleText(page, /business plan/i, 'BUSINESS PLAN');
    await expectVisibleText(page, /cambiar plan/i, 'Cambiar Plan');
  });

  await runStep('Detalles de la Cuenta', async () => {
    await expectVisibleText(page, /cuenta creada/i, 'Cuenta creada');
    await expectVisibleText(page, /estado activo/i, 'Estado activo');
    await expectVisibleText(page, /idioma seleccionado/i, 'Idioma seleccionado');
  });

  await runStep('Tus Negocios', async () => {
    await expectVisibleText(page, /tus negocios/i, 'Tus Negocios');
    await expectVisibleText(page, /agregar negocio/i, 'Agregar Negocio');
    await expectVisibleText(page, /tienes\s*2\s*de\s*3\s*negocios/i, 'Tienes 2 de 3 negocios');
  });

  const validateLegalLink = async ({ linkRegex, headingRegex, reportKey, screenshotName, urlKey }) => {
    await runStep(reportKey, async () => {
      const popupPromise = context.waitForEvent('page', { timeout: 10000 }).catch(() => null);
      await clickByVisibleText(page, linkRegex, reportKey);

      const popupPage = await popupPromise;
      const legalPage = popupPage || page;
      await waitForUi(legalPage);

      await expectVisibleText(legalPage, headingRegex, reportKey);

      const legalBody = await legalPage.locator('body').innerText();
      if (!legalBody || legalBody.trim().length < 150) {
        throw new Error(`No se encontró contenido legal suficiente para ${reportKey}.`);
      }

      legalUrls[urlKey] = legalPage.url();

      const legalPath = path.join(artifactDir, screenshotName);
      await legalPage.screenshot({ path: legalPath, fullPage: true });
      await testInfo.attach(screenshotName, { path: legalPath, contentType: 'image/png' });

      if (popupPage) {
        await popupPage.close().catch(() => {});
        await page.bringToFront();
        await waitForUi(page);
      }
    });
  };

  await validateLegalLink({
    linkRegex: /t[ée]rminos y condiciones/i,
    headingRegex: /t[ée]rminos y condiciones/i,
    reportKey: 'Términos y Condiciones',
    screenshotName: '05-terminos-y-condiciones.png',
    urlKey: 'terminos'
  });

  await validateLegalLink({
    linkRegex: /pol[ií]tica de privacidad/i,
    headingRegex: /pol[ií]tica de privacidad/i,
    reportKey: 'Política de Privacidad',
    screenshotName: '06-politica-de-privacidad.png',
    urlKey: 'privacidad'
  });

  const finalReport = {
    name: 'saleads_mi_negocio_full_test',
    results: report,
    legalUrls,
    failures
  };

  const reportPath = path.join(artifactDir, 'saleads_mi_negocio_report.json');
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), 'utf8');
  await testInfo.attach('saleads-mi-negocio-final-report', {
    path: reportPath,
    contentType: 'application/json'
  });

  expect(failures, `Validaciones fallidas:\n${failures.join('\n')}`).toEqual([]);
});
