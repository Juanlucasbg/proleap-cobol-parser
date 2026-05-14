const { test, expect } = require('@playwright/test');
const fs = require('fs');
const path = require('path');

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

function createInitialReport(loginUrl, googleEmail) {
  const results = {};
  for (const field of REPORT_FIELDS) {
    results[field] = {
      status: 'FAIL',
      details: 'Step not executed.',
    };
  }

  return {
    testName: 'saleads_mi_negocio_full_test',
    executedAt: new Date().toISOString(),
    metadata: {
      loginUrl: loginUrl || null,
      googleEmail,
    },
    results,
    evidence: {
      screenshots: [],
      terminosUrl: null,
      politicaUrl: null,
    },
  };
}

function updateStep(report, field, status, details) {
  report.results[field] = { status, details };
}

function sanitizeErrorMessage(error) {
  const rawMessage = error instanceof Error ? error.message : String(error);
  return rawMessage.replace(/\u001b\[[0-9;]*m/g, '').trim();
}

async function safeUiWait(page) {
  await page.waitForLoadState('domcontentloaded', { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => {});
  await page.waitForTimeout(750);
}

async function clickAndWait(locator, page) {
  await locator.first().click();
  await safeUiWait(page);
}

async function firstVisibleLocator(candidates, timeoutMs = 6000) {
  for (const candidate of candidates) {
    try {
      await candidate.first().waitFor({ state: 'visible', timeout: timeoutMs });
      return candidate.first();
    } catch (error) {
      // Try next candidate.
    }
  }

  return null;
}

async function captureScreenshot(page, runDir, fileName, report) {
  const screenshotPath = path.join(runDir, fileName);
  await page.screenshot({ path: screenshotPath, fullPage: true });
  report.evidence.screenshots.push(screenshotPath);
}

async function validateLegalPage({
  appPage,
  context,
  linkText,
  expectedHeading,
  screenshotName,
  report,
  runDir,
}) {
  const legalLink = await firstVisibleLocator(
    [
      appPage.getByRole('link', { name: new RegExp(linkText, 'i') }),
      appPage.getByRole('button', { name: new RegExp(linkText, 'i') }),
      appPage.getByText(new RegExp(linkText, 'i')),
    ],
    7000,
  );

  expect(legalLink, `Could not find legal link "${linkText}"`).not.toBeNull();

  const popupPromise = context.waitForEvent('page', { timeout: 8000 }).catch(() => null);
  const previousUrl = appPage.url();

  await clickAndWait(legalLink, appPage);

  let legalPage = await popupPromise;
  if (legalPage) {
    await safeUiWait(legalPage);
  } else {
    legalPage = appPage;
    if (legalPage.url() === previousUrl) {
      await legalPage.waitForTimeout(1500);
    }
  }

  const heading = await firstVisibleLocator(
    [
      legalPage.getByRole('heading', { name: new RegExp(expectedHeading, 'i') }),
      legalPage.getByText(new RegExp(expectedHeading, 'i')),
    ],
    12000,
  );
  expect(heading, `Expected heading "${expectedHeading}" is not visible.`).not.toBeNull();

  const legalBodyText = await firstVisibleLocator(
    [
      legalPage.locator('main p'),
      legalPage.locator('article p'),
      legalPage.locator('section p'),
      legalPage.locator('p'),
      legalPage.locator('div').filter({ hasText: /términos|condiciones|privacidad|datos/i }),
    ],
    12000,
  );
  expect(legalBodyText, 'Expected legal content text is not visible.').not.toBeNull();

  await captureScreenshot(legalPage, runDir, screenshotName, report);

  const finalUrl = legalPage.url();

  if (legalPage !== appPage) {
    await legalPage.close().catch(() => {});
    await appPage.bringToFront();
    await safeUiWait(appPage);
  } else if (appPage.url() !== previousUrl) {
    await appPage.goBack({ waitUntil: 'domcontentloaded' }).catch(() => {});
    await safeUiWait(appPage);
  }

  return finalUrl;
}

test('saleads_mi_negocio_full_test', async ({ page, context }) => {
  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL || '';
  const googleEmail = process.env.SALEADS_GOOGLE_ACCOUNT_EMAIL || 'juanlucasbarbiergarzon@gmail.com';
  const expectedUserName = process.env.SALEADS_EXPECTED_USER_NAME || '';
  const runDir = path.join(
    process.cwd(),
    'artifacts',
    'saleads-mi-negocio',
    new Date().toISOString().replace(/[:.]/g, '-'),
  );
  fs.mkdirSync(runDir, { recursive: true });

  const report = createInitialReport(loginUrl, googleEmail);

  const runStep = async (field, fn) => {
    try {
      await fn();
      updateStep(report, field, 'PASS', 'Validation completed successfully.');
      return true;
    } catch (error) {
      updateStep(report, field, 'FAIL', sanitizeErrorMessage(error));
      return false;
    }
  };

  const markRemainingAsBlocked = (details) => {
    for (const field of REPORT_FIELDS) {
      if (report.results[field].details === 'Step not executed.') {
        updateStep(report, field, 'FAIL', details);
      }
    }
  };

  let appPage = page;
  const loginOk = await runStep('Login', async () => {
    if (loginUrl) {
      await appPage.goto(loginUrl, { waitUntil: 'domcontentloaded' });
      await safeUiWait(appPage);
    } else if (appPage.url() === 'about:blank') {
      throw new Error('Set SALEADS_LOGIN_URL (or SALEADS_URL) because no active login page is open.');
    }

    const loginButton = await firstVisibleLocator(
      [
        appPage.getByRole('button', { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i }),
        appPage.getByRole('link', { name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i }),
        appPage.getByRole('button', { name: /google/i }),
        appPage.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
      ],
      12000,
    );
    expect(loginButton, 'Google login button is not visible.').not.toBeNull();

    const popupPromise = context.waitForEvent('page', { timeout: 12000 }).catch(() => null);
    await clickAndWait(loginButton, appPage);
    const maybeGooglePopup = await popupPromise;

    if (maybeGooglePopup) {
      await safeUiWait(maybeGooglePopup);

      const accountOption = await firstVisibleLocator(
        [maybeGooglePopup.getByText(googleEmail, { exact: false }), maybeGooglePopup.getByRole('button', { name: new RegExp(googleEmail, 'i') })],
        7000,
      );
      if (accountOption) {
        await clickAndWait(accountOption, maybeGooglePopup);
      }
    } else {
      const accountOption = await firstVisibleLocator(
        [appPage.getByText(googleEmail, { exact: false }), appPage.getByRole('button', { name: new RegExp(googleEmail, 'i') })],
        7000,
      );
      if (accountOption) {
        await clickAndWait(accountOption, appPage);
      }
    }

    const pageWithSidebar = await Promise.any(
      context.pages().map(async (candidatePage) => {
        await candidatePage.getByText(/negocio/i).first().waitFor({ state: 'visible', timeout: 45000 });
        return candidatePage;
      }),
    );
    appPage = pageWithSidebar;
    await appPage.bringToFront();

    await expect(appPage.getByText(/negocio/i).first()).toBeVisible({ timeout: 10000 });
    await expect(appPage.locator('aside, nav').first()).toBeVisible({ timeout: 10000 });
    await captureScreenshot(appPage, runDir, '01-dashboard-loaded.png', report);
  });

  if (!loginOk) {
    markRemainingAsBlocked('Blocked because login validation failed.');
  } else {
    await runStep('Mi Negocio menu', async () => {
      const negocioSection = await firstVisibleLocator(
        [
          appPage.getByRole('button', { name: /negocio/i }),
          appPage.getByRole('link', { name: /negocio/i }),
          appPage.getByText(/^Negocio$/i),
          appPage.getByText(/negocio/i),
        ],
        10000,
      );
      expect(negocioSection, 'Sidebar section "Negocio" is not visible.').not.toBeNull();
      await clickAndWait(negocioSection, appPage);

      const miNegocioOption = await firstVisibleLocator(
        [
          appPage.getByRole('button', { name: /mi negocio/i }),
          appPage.getByRole('link', { name: /mi negocio/i }),
          appPage.getByText(/mi negocio/i),
        ],
        10000,
      );
      expect(miNegocioOption, '"Mi Negocio" option is not visible.').not.toBeNull();
      await clickAndWait(miNegocioOption, appPage);

      await expect(appPage.getByText(/agregar negocio/i).first()).toBeVisible({ timeout: 10000 });
      await expect(appPage.getByText(/administrar negocios/i).first()).toBeVisible({ timeout: 10000 });
      await captureScreenshot(appPage, runDir, '02-mi-negocio-menu-expanded.png', report);
    });

    await runStep('Agregar Negocio modal', async () => {
      const agregarNegocio = await firstVisibleLocator(
        [
          appPage.getByRole('button', { name: /^agregar negocio$/i }),
          appPage.getByRole('link', { name: /^agregar negocio$/i }),
          appPage.getByText(/^Agregar Negocio$/i),
        ],
        10000,
      );
      expect(agregarNegocio, '"Agregar Negocio" action is not visible.').not.toBeNull();

      await clickAndWait(agregarNegocio, appPage);

      await expect(appPage.getByText(/crear nuevo negocio/i).first()).toBeVisible({ timeout: 10000 });

      const nombreField = await firstVisibleLocator(
        [
          appPage.getByLabel(/nombre del negocio/i),
          appPage.getByPlaceholder(/nombre del negocio/i),
          appPage.locator('input[name*="nombre" i]'),
          appPage.locator('input').filter({ has: appPage.locator('xpath=ancestor::*[contains(., "Nombre del Negocio")]') }),
        ],
        10000,
      );
      expect(nombreField, 'Input "Nombre del Negocio" is not visible.').not.toBeNull();

      await expect(appPage.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({ timeout: 10000 });
      await expect(appPage.getByRole('button', { name: /cancelar/i }).first()).toBeVisible({ timeout: 10000 });
      await expect(appPage.getByRole('button', { name: /crear negocio/i }).first()).toBeVisible({ timeout: 10000 });

      await captureScreenshot(appPage, runDir, '03-agregar-negocio-modal.png', report);

      await nombreField.click();
      await nombreField.fill('Negocio Prueba Automatización');

      const cancelarButton = appPage.getByRole('button', { name: /cancelar/i }).first();
      await clickAndWait(cancelarButton, appPage);
    });

    await runStep('Administrar Negocios view', async () => {
      const miNegocioOption = await firstVisibleLocator(
        [
          appPage.getByRole('button', { name: /mi negocio/i }),
          appPage.getByRole('link', { name: /mi negocio/i }),
          appPage.getByText(/mi negocio/i),
        ],
        8000,
      );
      if (miNegocioOption) {
        await clickAndWait(miNegocioOption, appPage);
      }

      const administrarNegociosOption = await firstVisibleLocator(
        [
          appPage.getByRole('button', { name: /administrar negocios/i }),
          appPage.getByRole('link', { name: /administrar negocios/i }),
          appPage.getByText(/administrar negocios/i),
        ],
        10000,
      );
      expect(administrarNegociosOption, '"Administrar Negocios" option is not visible.').not.toBeNull();

      await clickAndWait(administrarNegociosOption, appPage);
      await safeUiWait(appPage);

      await expect(appPage.getByText(/informaci[oó]n general/i).first()).toBeVisible({ timeout: 15000 });
      await expect(appPage.getByText(/detalles de la cuenta/i).first()).toBeVisible({ timeout: 15000 });
      await expect(appPage.getByText(/tus negocios/i).first()).toBeVisible({ timeout: 15000 });
      await expect(appPage.getByText(/secci[oó]n legal/i).first()).toBeVisible({ timeout: 15000 });

      await captureScreenshot(appPage, runDir, '04-administrar-negocios-page.png', report);
    });

    await runStep('Información General', async () => {
      if (expectedUserName) {
        await expect(appPage.getByText(new RegExp(expectedUserName, 'i')).first()).toBeVisible({ timeout: 10000 });
      } else {
        const infoSection = appPage.locator('section, div').filter({ hasText: /informaci[oó]n general/i }).first();
        await expect(infoSection).toBeVisible({ timeout: 10000 });
        const fallbackName = infoSection.locator('h1, h2, h3, p, span, strong').filter({ hasNotText: /informaci[oó]n general|business plan|cambiar plan|@/i }).first();
        await expect(fallbackName).toBeVisible({ timeout: 10000 });
      }

      await expect(appPage.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()).toBeVisible({ timeout: 10000 });
      await expect(appPage.getByText(/business plan/i).first()).toBeVisible({ timeout: 10000 });
      await expect(appPage.getByRole('button', { name: /cambiar plan/i }).first()).toBeVisible({ timeout: 10000 });
    });

    await runStep('Detalles de la Cuenta', async () => {
      await expect(appPage.getByText(/cuenta creada/i).first()).toBeVisible({ timeout: 10000 });
      await expect(appPage.getByText(/estado activo/i).first()).toBeVisible({ timeout: 10000 });
      await expect(appPage.getByText(/idioma seleccionado/i).first()).toBeVisible({ timeout: 10000 });
    });

    await runStep('Tus Negocios', async () => {
      await expect(appPage.getByText(/tus negocios/i).first()).toBeVisible({ timeout: 10000 });
      await expect(appPage.getByRole('button', { name: /agregar negocio/i }).first()).toBeVisible({ timeout: 10000 });
      await expect(appPage.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({ timeout: 10000 });
    });

    await runStep('Términos y Condiciones', async () => {
      const finalUrl = await validateLegalPage({
        appPage,
        context,
        linkText: 'Términos y Condiciones',
        expectedHeading: 'Términos y Condiciones',
        screenshotName: '08-terminos-y-condiciones.png',
        report,
        runDir,
      });
      report.evidence.terminosUrl = finalUrl;
    });

    await runStep('Política de Privacidad', async () => {
      const finalUrl = await validateLegalPage({
        appPage,
        context,
        linkText: 'Política de Privacidad',
        expectedHeading: 'Política de Privacidad',
        screenshotName: '09-politica-de-privacidad.png',
        report,
        runDir,
      });
      report.evidence.politicaUrl = finalUrl;
    });
  }

  const finalReportPath = path.join(runDir, 'final-report.json');
  fs.writeFileSync(finalReportPath, JSON.stringify(report, null, 2), 'utf8');
  const latestReportPath = path.join(process.cwd(), 'artifacts', 'saleads-mi-negocio', 'latest-final-report.json');
  fs.writeFileSync(latestReportPath, JSON.stringify(report, null, 2), 'utf8');

  // Required final report output for automation logs.
  // eslint-disable-next-line no-console
  console.table(
    Object.fromEntries(
      REPORT_FIELDS.map((field) => [field, report.results[field].status]),
    ),
  );
  // eslint-disable-next-line no-console
  console.log(`Final report: ${finalReportPath}`);
  // eslint-disable-next-line no-console
  console.log(`Términos URL: ${report.evidence.terminosUrl || 'N/A'}`);
  // eslint-disable-next-line no-console
  console.log(`Política URL: ${report.evidence.politicaUrl || 'N/A'}`);

  const failedSteps = REPORT_FIELDS.filter((field) => report.results[field].status !== 'PASS');
  expect(failedSteps, `Validation failures: ${failedSteps.join(', ')}`).toEqual([]);
});
