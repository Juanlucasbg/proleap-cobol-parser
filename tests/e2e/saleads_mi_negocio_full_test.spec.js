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

const GOOGLE_EMAIL = 'juanlucasbarbiergarzon@gmail.com';

function normalizeFileName(name) {
  return name.toLowerCase().replace(/[^a-z0-9]+/g, '-');
}

async function waitForUi(page) {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForLoadState('networkidle', { timeout: 7000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function firstVisibleLocator(candidates, timeout = 5000) {
  for (const candidate of candidates) {
    const locator = candidate.first();
    try {
      await locator.waitFor({ state: 'visible', timeout });
      return locator;
    } catch (error) {
      // try next fallback locator
    }
  }
  throw new Error('No visible locator found from provided candidates.');
}

async function clickAndWait(locator, page) {
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click();
  await waitForUi(page);
}

test('saleads_mi_negocio_full_test', async ({ page, context }, testInfo) => {
  const report = Object.fromEntries(
    REPORT_FIELDS.map((name) => [name, { status: 'FAIL', details: '' }]),
  );
  const failures = [];
  const legalUrls = {};

  async function checkpoint(targetPage, label, fullPage = false) {
    const filePath = testInfo.outputPath(`${normalizeFileName(label)}.png`);
    await targetPage.screenshot({ path: filePath, fullPage });
    await testInfo.attach(label, { path: filePath, contentType: 'image/png' });
  }

  async function runStep(stepName, fn) {
    try {
      await fn();
      report[stepName].status = 'PASS';
    } catch (error) {
      report[stepName].status = 'FAIL';
      report[stepName].details = error.message;
      failures.push(`${stepName}: ${error.message}`);
      // Continue running so the final report includes all checkpoints.
    }
  }

  async function ensureOnLoginPage() {
    if (page.url() !== 'about:blank') {
      return;
    }

    const targetUrl =
      process.env.SALEADS_LOGIN_URL ||
      process.env.SALEADS_BASE_URL ||
      process.env.BASE_URL;

    if (!targetUrl) {
      throw new Error(
        'No SaleADS URL available. Open the login page first or set SALEADS_LOGIN_URL/SALEADS_BASE_URL/BASE_URL.',
      );
    }

    await page.goto(targetUrl, { waitUntil: 'domcontentloaded' });
    await waitForUi(page);
  }

  async function maybePickGoogleAccount(targetPage) {
    const emailOption = targetPage
      .getByText(GOOGLE_EMAIL, { exact: false })
      .first();
    if (await emailOption.isVisible().catch(() => false)) {
      await clickAndWait(emailOption, targetPage);
      return true;
    }
    return false;
  }

  async function ensureMiNegocioExpanded() {
    const miNegocio = await firstVisibleLocator([
      page.getByRole('button', { name: /mi negocio/i }),
      page.getByRole('link', { name: /mi negocio/i }),
      page.getByText(/^mi negocio$/i),
    ]);

    const addBusinessVisible = await page
      .getByText(/agregar negocio/i)
      .first()
      .isVisible()
      .catch(() => false);

    if (!addBusinessVisible) {
      await clickAndWait(miNegocio, page);
    }
  }

  async function clickLegalAndValidate(linkText, headingRegex, reportKey) {
    const appPage = page;
    const legalLink = await firstVisibleLocator([
      appPage.getByRole('link', { name: new RegExp(linkText, 'i') }),
      appPage.getByRole('button', { name: new RegExp(linkText, 'i') }),
      appPage.getByText(new RegExp(linkText, 'i')),
    ]);

    const popupPromise = context.waitForEvent('page', { timeout: 6000 }).catch(() => null);
    await clickAndWait(legalLink, appPage);
    const popup = await popupPromise;

    if (popup) {
      await popup.waitForLoadState('domcontentloaded');
      await waitForUi(popup);
      await expect(popup.getByRole('heading', { name: headingRegex }).first()).toBeVisible();
      await expect(popup.locator('main, article, body').first()).toContainText(/\S+/);
      legalUrls[reportKey] = popup.url();
      await checkpoint(popup, `${reportKey} page`);
      await popup.close();
      await appPage.bringToFront();
      await waitForUi(appPage);
      return;
    }

    await expect(appPage.getByRole('heading', { name: headingRegex }).first()).toBeVisible();
    await expect(appPage.locator('main, article, body').first()).toContainText(/\S+/);
    legalUrls[reportKey] = appPage.url();
    await checkpoint(appPage, `${reportKey} page`);
    await appPage.goBack().catch(() => {});
    await waitForUi(appPage);
  }

  await runStep('Login', async () => {
    await ensureOnLoginPage();

    const loginButton = await firstVisibleLocator([
      page.getByRole('button', { name: /sign in with google|iniciar sesi[oó]n con google/i }),
      page.getByRole('link', { name: /sign in with google|iniciar sesi[oó]n con google/i }),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google/i),
      page.getByText(/google/i),
    ]);

    const popupPromise = context.waitForEvent('page', { timeout: 6000 }).catch(() => null);
    await clickAndWait(loginButton, page);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState('domcontentloaded');
      await maybePickGoogleAccount(popup);
      await popup.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});
    } else {
      await maybePickGoogleAccount(page);
    }

    await waitForUi(page);

    const sidebar = await firstVisibleLocator([
      page.locator('aside').filter({ hasText: /negocio|mi negocio|dashboard/i }),
      page.getByRole('navigation').filter({ hasText: /negocio|mi negocio|dashboard/i }),
      page.getByText(/negocio|mi negocio/i),
    ]);
    await expect(sidebar).toBeVisible();
    await checkpoint(page, 'dashboard-loaded');
  });

  await runStep('Mi Negocio menu', async () => {
    const negocioSection = await firstVisibleLocator([
      page.getByRole('button', { name: /^negocio$/i }),
      page.getByRole('link', { name: /^negocio$/i }),
      page.getByText(/^negocio$/i),
    ]);
    await clickAndWait(negocioSection, page);

    const miNegocioOption = await firstVisibleLocator([
      page.getByRole('button', { name: /mi negocio/i }),
      page.getByRole('link', { name: /mi negocio/i }),
      page.getByText(/^mi negocio$/i),
    ]);
    await clickAndWait(miNegocioOption, page);

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();
    await checkpoint(page, 'mi-negocio-menu-expanded');
  });

  await runStep('Agregar Negocio modal', async () => {
    await ensureMiNegocioExpanded();
    const addBusinessOption = await firstVisibleLocator([
      page.getByRole('button', { name: /agregar negocio/i }),
      page.getByRole('link', { name: /agregar negocio/i }),
      page.getByText(/agregar negocio/i),
    ]);

    await clickAndWait(addBusinessOption, page);

    const modalTitle = page.getByText(/crear nuevo negocio/i).first();
    await expect(modalTitle).toBeVisible();
    await expect(page.getByLabel(/nombre del negocio/i).first()).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /crear negocio/i }).first()).toBeVisible();

    await checkpoint(page, 'agregar-negocio-modal');

    const businessNameInput = page.getByLabel(/nombre del negocio/i).first();
    await businessNameInput.click();
    await businessNameInput.fill('Negocio Prueba Automatización');
    await clickAndWait(page.getByRole('button', { name: /cancelar/i }).first(), page);
  });

  await runStep('Administrar Negocios view', async () => {
    await ensureMiNegocioExpanded();

    const manageBusinesses = await firstVisibleLocator([
      page.getByRole('button', { name: /administrar negocios/i }),
      page.getByRole('link', { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i),
    ]);
    await clickAndWait(manageBusinesses, page);

    await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByText(/secci[oó]n legal/i).first()).toBeVisible();
    await checkpoint(page, 'administrar-negocios-page', true);
  });

  await runStep('Información General', async () => {
    await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible();
    await expect(page.getByText(/business plan/i).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /cambiar plan/i }).first()).toBeVisible();

    const bodyText = await page.locator('body').innerText();
    if (!bodyText.includes(GOOGLE_EMAIL) && !/\b[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}\b/.test(bodyText)) {
      throw new Error('Could not detect a visible user email in Información General.');
    }
    if (!/[A-Za-zÁÉÍÓÚáéíóúÑñ]{2,}\s+[A-Za-zÁÉÍÓÚáéíóúÑñ]{2,}/.test(bodyText)) {
      throw new Error('Could not detect a visible user name-like text in Información General.');
    }
  });

  await runStep('Detalles de la Cuenta', async () => {
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep('Tus Negocios', async () => {
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /agregar negocio/i }).first()).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();

    const businessRow = page.locator('li, tr, [role="row"], [data-testid*="business"]').first();
    await expect(businessRow).toBeVisible();
  });

  await runStep('Términos y Condiciones', async () => {
    await clickLegalAndValidate(
      'Términos y Condiciones',
      /t[eé]rminos y condiciones/i,
      'Términos y Condiciones',
    );
  });

  await runStep('Política de Privacidad', async () => {
    await clickLegalAndValidate(
      'Política de Privacidad',
      /pol[ií]tica de privacidad/i,
      'Política de Privacidad',
    );
  });

  const finalReport = {
    name: 'saleads_mi_negocio_full_test',
    generatedAt: new Date().toISOString(),
    results: report,
    legalUrls,
  };

  await testInfo.attach('final-report.json', {
    body: Buffer.from(JSON.stringify(finalReport, null, 2), 'utf-8'),
    contentType: 'application/json',
  });
  console.table(
    Object.entries(report).map(([step, data]) => ({
      step,
      status: data.status,
      details: data.details,
    })),
  );
  if (Object.keys(legalUrls).length > 0) {
    console.log('Validated legal URLs:', legalUrls);
  }

  expect(failures, `Failed steps:\n${failures.join('\n')}`).toEqual([]);
});
