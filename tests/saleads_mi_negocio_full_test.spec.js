const fs = require('node:fs');
const path = require('node:path');
const { test, expect } = require('@playwright/test');

const REQUIRED_REPORT_FIELDS = [
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

const GOOGLE_ACCOUNT_EMAIL = 'juanlucasbarbiergarzon@gmail.com';

function createReport(baseUrl, screenshotsDir) {
  const validations = {};
  for (const field of REQUIRED_REPORT_FIELDS) {
    validations[field] = 'NOT_RUN';
  }

  return {
    testName: 'saleads_mi_negocio_full_test',
    generatedAt: new Date().toISOString(),
    baseUrl,
    screenshotsDirectory: screenshotsDir,
    validations,
    details: {},
    evidence: {
      termsAndConditionsUrl: '',
      privacyPolicyUrl: '',
    },
  };
}

async function waitForUi(page) {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});
  const busyIndicators = page.locator(
    '[aria-busy="true"], [role="progressbar"], .spinner, .loading, .ant-spin-spinning'
  );
  await busyIndicators.first().waitFor({ state: 'hidden', timeout: 5000 }).catch(() => {});
  await page.waitForTimeout(400);
}

async function waitForVisibleLocator(candidates, timeoutMs = 15000) {
  const endTime = Date.now() + timeoutMs;
  while (Date.now() <= endTime) {
    for (const candidate of candidates) {
      const first = candidate.first();
      if (await first.isVisible().catch(() => false)) {
        return first;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  return null;
}

function textLocator(page, value) {
  return page.getByText(value, { exact: true });
}

function textContainsLocator(page, value) {
  return page.getByText(value, { exact: false });
}

async function clickAndWait(page, locator) {
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click();
  await waitForUi(page);
}

function setResult(report, field, status, detail = '') {
  report.validations[field] = status;
  if (detail) {
    report.details[field] = detail;
  }
}

function markBlocked(report, field, reason) {
  if (report.validations[field] === 'NOT_RUN') {
    setResult(report, field, 'BLOCKED', reason);
  }
}

async function capture(page, screenshotsDir, fileName, fullPage = false) {
  const screenshotPath = path.join(screenshotsDir, fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
}

async function ensureMiNegocioExpanded(page) {
  const submenuAlreadyVisible =
    (await textLocator(page, 'Agregar Negocio').first().isVisible().catch(() => false)) &&
    (await textLocator(page, 'Administrar Negocios').first().isVisible().catch(() => false));

  if (submenuAlreadyVisible) {
    return;
  }

  const miNegocioEntry = await waitForVisibleLocator(
    [
      textLocator(page, 'Mi Negocio'),
      page.getByRole('button', { name: /mi negocio/i }),
      textContainsLocator(page, 'Mi Negocio'),
    ],
    15000
  );

  if (!miNegocioEntry) {
    throw new Error('Could not find "Mi Negocio" option in sidebar.');
  }

  await clickAndWait(page, miNegocioEntry);
  await expect(textContainsLocator(page, 'Agregar Negocio').first()).toBeVisible({ timeout: 15000 });
  await expect(textContainsLocator(page, 'Administrar Negocios').first()).toBeVisible({ timeout: 15000 });
}

async function findSectionContainer(page, headingMatcher) {
  const headingPattern =
    headingMatcher instanceof RegExp ? headingMatcher : new RegExp(String(headingMatcher), 'i');
  const heading = await waitForVisibleLocator(
    [
      page.getByRole('heading', { name: headingPattern }),
      textContainsLocator(page, headingPattern),
    ],
    15000
  );

  if (!heading) {
    throw new Error(`Section heading "${headingPattern}" is not visible.`);
  }

  return heading.locator('xpath=ancestor::*[self::section or self::article or self::div][1]');
}

async function openLegalLinkAndReturn({
  page,
  context,
  linkPattern,
  expectedHeadingPattern,
  reportEvidenceKey,
  report,
  screenshotsDir,
  screenshotFile,
}) {
  const trigger = await waitForVisibleLocator(
    [
      page.getByRole('link', { name: linkPattern }),
      textContainsLocator(page, linkPattern),
    ],
    15000
  );

  if (!trigger) {
    throw new Error(`Could not find legal link using pattern "${linkPattern}".`);
  }

  const popupPromise = context.waitForEvent('page', { timeout: 8000 }).catch(() => null);
  await clickAndWait(page, trigger);
  const popup = await popupPromise;

  const legalPage = popup || page;
  await legalPage.bringToFront().catch(() => {});
  await waitForUi(legalPage);

  const heading = await waitForVisibleLocator(
    [
      legalPage.getByRole('heading', { name: expectedHeadingPattern }),
      legalPage.getByText(expectedHeadingPattern),
    ],
    15000
  );

  if (!heading) {
    throw new Error(`Expected legal heading ${expectedHeadingPattern} not found.`);
  }

  const legalText = legalPage.locator('main p, article p, section p, p');
  await expect(legalText.first()).toBeVisible({ timeout: 15000 });

  await capture(legalPage, screenshotsDir, screenshotFile, true);
  report.evidence[reportEvidenceKey] = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => {});
    await waitForUi(page);
  }
}

test('saleads_mi_negocio_full_test', async ({ page, context }) => {
  const baseUrl = process.env.SALEADS_URL || process.env.SALEADS_LOGIN_URL || process.env.BASE_URL;
  if (!baseUrl) {
    throw new Error(
      'Set SALEADS_URL (or SALEADS_LOGIN_URL / BASE_URL) to run this workflow without hardcoding a domain.'
    );
  }

  const artifactsRoot = path.resolve('target', 'saleads_mi_negocio_full_test');
  const screenshotsDir = path.join(artifactsRoot, 'screenshots');
  fs.mkdirSync(screenshotsDir, { recursive: true });

  const report = createReport(baseUrl, screenshotsDir);
  let workflowBlocked = false;

  try {
    await page.goto(baseUrl, { waitUntil: 'domcontentloaded' });
    await waitForUi(page);

    // Step 1: Login with Google
    try {
      const googleButton = await waitForVisibleLocator(
        [
          page.getByRole('button', { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
          page.getByRole('link', { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
          textContainsLocator(page, 'Sign in with Google'),
          textContainsLocator(page, 'Iniciar sesion con Google'),
          textContainsLocator(page, 'Iniciar sesión con Google'),
        ],
        20000
      );

      if (!googleButton) {
        throw new Error('Google login button is not visible.');
      }

      const authPopupPromise = context.waitForEvent('page', { timeout: 10000 }).catch(() => null);
      await clickAndWait(page, googleButton);
      const authPopup = await authPopupPromise;

      const authPage = authPopup || page;
      await waitForUi(authPage);

      const accountOption = await waitForVisibleLocator(
        [
          authPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }),
          authPage.getByRole('button', { name: GOOGLE_ACCOUNT_EMAIL }),
          authPage.getByRole('link', { name: GOOGLE_ACCOUNT_EMAIL }),
        ],
        8000
      );

      if (accountOption) {
        await clickAndWait(authPage, accountOption);
      }

      if (authPopup) {
        await authPopup.waitForEvent('close', { timeout: 30000 }).catch(() => {});
      }

      await page.bringToFront();
      await waitForUi(page);

      const sidebar = await waitForVisibleLocator(
        [
          page.locator('aside'),
          page.locator('[data-testid*="sidebar"]'),
          page.locator('nav').filter({ hasText: /negocio|mi negocio/i }),
        ],
        20000
      );

      if (!sidebar) {
        throw new Error('Left sidebar is not visible after login.');
      }

      await expect(textContainsLocator(page, 'Negocio').first()).toBeVisible({ timeout: 20000 });
      await capture(page, screenshotsDir, 'step_1_dashboard_loaded.png', true);
      setResult(report, 'Login', 'PASS');
    } catch (error) {
      setResult(report, 'Login', 'FAIL', error.message);
      workflowBlocked = true;
    }

    if (workflowBlocked) {
      markBlocked(report, 'Mi Negocio menu', 'Blocked by Login failure.');
      markBlocked(report, 'Agregar Negocio modal', 'Blocked by Login failure.');
      markBlocked(report, 'Administrar Negocios view', 'Blocked by Login failure.');
      markBlocked(report, 'Informacion General', 'Blocked by Login failure.');
      markBlocked(report, 'Detalles de la Cuenta', 'Blocked by Login failure.');
      markBlocked(report, 'Tus Negocios', 'Blocked by Login failure.');
      markBlocked(report, 'Terminos y Condiciones', 'Blocked by Login failure.');
      markBlocked(report, 'Politica de Privacidad', 'Blocked by Login failure.');
      return;
    }

    // Step 2: Open Mi Negocio menu
    try {
      await ensureMiNegocioExpanded(page);
      await capture(page, screenshotsDir, 'step_2_mi_negocio_menu_expanded.png', true);
      setResult(report, 'Mi Negocio menu', 'PASS');
    } catch (error) {
      setResult(report, 'Mi Negocio menu', 'FAIL', error.message);
      workflowBlocked = true;
    }

    if (workflowBlocked) {
      markBlocked(report, 'Agregar Negocio modal', 'Blocked by Mi Negocio menu failure.');
      markBlocked(report, 'Administrar Negocios view', 'Blocked by Mi Negocio menu failure.');
      markBlocked(report, 'Informacion General', 'Blocked by Mi Negocio menu failure.');
      markBlocked(report, 'Detalles de la Cuenta', 'Blocked by Mi Negocio menu failure.');
      markBlocked(report, 'Tus Negocios', 'Blocked by Mi Negocio menu failure.');
      markBlocked(report, 'Terminos y Condiciones', 'Blocked by Mi Negocio menu failure.');
      markBlocked(report, 'Politica de Privacidad', 'Blocked by Mi Negocio menu failure.');
      return;
    }

    // Step 3: Validate Agregar Negocio modal
    try {
      const addBusinessMenu = await waitForVisibleLocator(
        [
          textLocator(page, 'Agregar Negocio'),
          page.getByRole('button', { name: /agregar negocio/i }),
          page.getByRole('link', { name: /agregar negocio/i }),
        ],
        15000
      );

      if (!addBusinessMenu) {
        throw new Error('"Agregar Negocio" option is not visible.');
      }

      await clickAndWait(page, addBusinessMenu);

      const modalTitle = await waitForVisibleLocator(
        [
          textContainsLocator(page, 'Crear Nuevo Negocio'),
          page.getByRole('heading', { name: /crear nuevo negocio/i }),
        ],
        15000
      );
      if (!modalTitle) {
        throw new Error('Modal title "Crear Nuevo Negocio" is not visible.');
      }

      const modalRoot = modalTitle.locator(
        'xpath=ancestor::*[@role="dialog" or contains(@class, "modal") or contains(@class, "Modal")][1]'
      );

      await expect(textContainsLocator(page, 'Nombre del Negocio').first()).toBeVisible({ timeout: 15000 });
      await expect(textContainsLocator(page, 'Tienes 2 de 3 negocios').first()).toBeVisible({ timeout: 15000 });
      await expect(textContainsLocator(page, 'Cancelar').first()).toBeVisible({ timeout: 15000 });
      await expect(textContainsLocator(page, 'Crear Negocio').first()).toBeVisible({ timeout: 15000 });
      await capture(page, screenshotsDir, 'step_3_agregar_negocio_modal.png', true);

      const businessNameInput = await waitForVisibleLocator(
        [
          modalRoot.getByLabel(/nombre del negocio/i),
          modalRoot.getByPlaceholder(/nombre del negocio/i),
          modalRoot.locator('input[type="text"]'),
          modalRoot.locator('input:not([type])'),
        ],
        8000
      );

      if (businessNameInput) {
        await businessNameInput.fill('Negocio Prueba Automatizacion');
      }

      const cancelButton = await waitForVisibleLocator(
        [page.getByRole('button', { name: /^cancelar$/i }), textLocator(page, 'Cancelar')],
        10000
      );

      if (!cancelButton) {
        throw new Error('Could not find modal Cancelar button.');
      }

      await clickAndWait(page, cancelButton);
      await expect(textContainsLocator(page, 'Crear Nuevo Negocio').first()).toBeHidden({ timeout: 10000 });

      setResult(report, 'Agregar Negocio modal', 'PASS');
    } catch (error) {
      setResult(report, 'Agregar Negocio modal', 'FAIL', error.message);
      workflowBlocked = true;
    }

    if (workflowBlocked) {
      markBlocked(report, 'Administrar Negocios view', 'Blocked by Agregar Negocio modal failure.');
      markBlocked(report, 'Informacion General', 'Blocked by Agregar Negocio modal failure.');
      markBlocked(report, 'Detalles de la Cuenta', 'Blocked by Agregar Negocio modal failure.');
      markBlocked(report, 'Tus Negocios', 'Blocked by Agregar Negocio modal failure.');
      markBlocked(report, 'Terminos y Condiciones', 'Blocked by Agregar Negocio modal failure.');
      markBlocked(report, 'Politica de Privacidad', 'Blocked by Agregar Negocio modal failure.');
      return;
    }

    // Step 4: Open Administrar Negocios
    try {
      await ensureMiNegocioExpanded(page);

      const manageBusinesses = await waitForVisibleLocator(
        [
          textLocator(page, 'Administrar Negocios'),
          page.getByRole('button', { name: /administrar negocios/i }),
          page.getByRole('link', { name: /administrar negocios/i }),
        ],
        15000
      );

      if (!manageBusinesses) {
        throw new Error('"Administrar Negocios" option is not visible.');
      }

      await clickAndWait(page, manageBusinesses);

      await expect(textContainsLocator(page, /informaci[oó]n general/i).first()).toBeVisible({
        timeout: 20000,
      });
      await expect(textContainsLocator(page, 'Detalles de la Cuenta').first()).toBeVisible({ timeout: 20000 });
      await expect(textContainsLocator(page, 'Tus Negocios').first()).toBeVisible({ timeout: 20000 });
      await expect(textContainsLocator(page, /secci[oó]n legal/i).first()).toBeVisible({ timeout: 20000 });

      await capture(page, screenshotsDir, 'step_4_administrar_negocios_page_full.png', true);
      setResult(report, 'Administrar Negocios view', 'PASS');
    } catch (error) {
      setResult(report, 'Administrar Negocios view', 'FAIL', error.message);
      workflowBlocked = true;
    }

    if (workflowBlocked) {
      markBlocked(report, 'Informacion General', 'Blocked by Administrar Negocios view failure.');
      markBlocked(report, 'Detalles de la Cuenta', 'Blocked by Administrar Negocios view failure.');
      markBlocked(report, 'Tus Negocios', 'Blocked by Administrar Negocios view failure.');
      markBlocked(report, 'Terminos y Condiciones', 'Blocked by Administrar Negocios view failure.');
      markBlocked(report, 'Politica de Privacidad', 'Blocked by Administrar Negocios view failure.');
      return;
    }

    // Step 5: Validate Informacion General
    try {
      const infoSection = await findSectionContainer(page, /informaci[oó]n general/i);
      const infoText = (await infoSection.innerText()).replace(/\s+/g, ' ');

      if (!/\S+@\S+\.\S+/.test(infoText)) {
        throw new Error('User email is not visible in Informacion General.');
      }
      if (!infoText.toUpperCase().includes('BUSINESS PLAN')) {
        throw new Error('"BUSINESS PLAN" text is not visible in Informacion General.');
      }
      if (!/CAMBIAR PLAN/i.test(infoText)) {
        throw new Error('"Cambiar Plan" button is not visible in Informacion General.');
      }

      setResult(report, 'Informacion General', 'PASS');
    } catch (error) {
      setResult(report, 'Informacion General', 'FAIL', error.message);
    }

    // Step 6: Validate Detalles de la Cuenta
    try {
      const detailsSection = await findSectionContainer(page, 'Detalles de la Cuenta');
      const detailsText = (await detailsSection.innerText()).replace(/\s+/g, ' ');

      if (!/Cuenta creada/i.test(detailsText)) {
        throw new Error('"Cuenta creada" is not visible.');
      }
      if (!/Estado activo/i.test(detailsText)) {
        throw new Error('"Estado activo" is not visible.');
      }
      if (!/Idioma seleccionado/i.test(detailsText)) {
        throw new Error('"Idioma seleccionado" is not visible.');
      }

      setResult(report, 'Detalles de la Cuenta', 'PASS');
    } catch (error) {
      setResult(report, 'Detalles de la Cuenta', 'FAIL', error.message);
    }

    // Step 7: Validate Tus Negocios
    try {
      const businessesSection = await findSectionContainer(page, 'Tus Negocios');
      const businessesText = (await businessesSection.innerText()).replace(/\s+/g, ' ');

      const businessEntries = businessesSection.locator('li, tr, [data-testid*="business"], [class*="business"]');
      const hasRows = (await businessEntries.count()) > 0;
      if (!hasRows) {
        throw new Error('Business list is not visible.');
      }
      if (!/Agregar Negocio/i.test(businessesText)) {
        throw new Error('"Agregar Negocio" button is not visible in Tus Negocios.');
      }
      if (!/Tienes 2 de 3 negocios/i.test(businessesText)) {
        throw new Error('"Tienes 2 de 3 negocios" text is not visible in Tus Negocios.');
      }

      setResult(report, 'Tus Negocios', 'PASS');
    } catch (error) {
      setResult(report, 'Tus Negocios', 'FAIL', error.message);
    }

    // Step 8: Validate Terminos y Condiciones
    try {
      await openLegalLinkAndReturn({
        page,
        context,
        linkPattern: /t[eé]rminos y condiciones/i,
        expectedHeadingPattern: /t[eé]rminos y condiciones/i,
        reportEvidenceKey: 'termsAndConditionsUrl',
        report,
        screenshotsDir,
        screenshotFile: 'step_8_terminos_y_condiciones.png',
      });

      setResult(report, 'Terminos y Condiciones', 'PASS');
    } catch (error) {
      setResult(report, 'Terminos y Condiciones', 'FAIL', error.message);
    }

    // Step 9: Validate Politica de Privacidad
    try {
      await openLegalLinkAndReturn({
        page,
        context,
        linkPattern: /pol[ií]tica de privacidad/i,
        expectedHeadingPattern: /pol[ií]tica de privacidad/i,
        reportEvidenceKey: 'privacyPolicyUrl',
        report,
        screenshotsDir,
        screenshotFile: 'step_9_politica_de_privacidad.png',
      });

      setResult(report, 'Politica de Privacidad', 'PASS');
    } catch (error) {
      setResult(report, 'Politica de Privacidad', 'FAIL', error.message);
    }
  } finally {
    const reportPath = path.join(artifactsRoot, 'final_report.json');
    fs.writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`, 'utf-8');
    // Keep report visible in CI logs for quick inspection.
    console.log(JSON.stringify(report, null, 2));
    const failedOrBlocked = Object.entries(report.validations).filter(([, value]) => value !== 'PASS');
    expect(
      failedOrBlocked,
      `One or more workflow validations failed or were blocked: ${JSON.stringify(failedOrBlocked)}`
    ).toEqual([]);
  }
});
