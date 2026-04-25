const fs = require('fs');
const path = require('path');
const { test, expect } = require('@playwright/test');

const STEP_FIELDS = [
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

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

async function waitForUiToSettle(page) {
  await page.waitForTimeout(250);
  await Promise.allSettled([
    page.waitForLoadState('domcontentloaded', { timeout: 7000 }),
    page.waitForLoadState('networkidle', { timeout: 7000 })
  ]);
  await page.waitForTimeout(250);
}

async function firstVisibleLocator(page, labels, options = {}) {
  const escaped = labels.map((label) => new RegExp(`^\\s*${escapeRegExp(label)}\\s*$`, 'i'));
  const roleCandidates = options.roles || ['button', 'link', 'menuitem', 'tab', 'heading'];

  for (const role of roleCandidates) {
    for (const regex of escaped) {
      const locator = page.getByRole(role, { name: regex }).first();
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }
  }

  for (const regex of escaped) {
    const locator = page.getByText(regex).first();
    if (await locator.isVisible().catch(() => false)) {
      return locator;
    }
  }

  return null;
}

async function clickVisibleTextAndWait(page, labels, options = {}) {
  const locator = await firstVisibleLocator(page, labels, options);
  if (!locator) {
    throw new Error(`Could not find visible element with text: ${labels.join(' | ')}`);
  }

  await locator.click({ timeout: 15000 });
  await waitForUiToSettle(page);
  return locator;
}

async function capture(page, evidenceDir, name, fullPage = false) {
  const screenshotPath = path.join(evidenceDir, `${name}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

async function resolveAccountEmailSelection(context, email) {
  const pages = context.pages();

  for (const candidate of pages) {
    const emailChoice = candidate.getByText(new RegExp(escapeRegExp(email), 'i')).first();
    if (await emailChoice.isVisible().catch(() => false)) {
      await emailChoice.click({ timeout: 15000 });
      await waitForUiToSettle(candidate);
      return true;
    }
  }

  return false;
}

async function findSidebar(page) {
  const withNegocio = page.locator('aside, nav, [role="navigation"]').filter({
    hasText: /Negocio|Mi Negocio/i
  });
  if (await withNegocio.first().isVisible().catch(() => false)) {
    return withNegocio.first();
  }

  const genericSidebar = page.locator('aside, nav, [role="navigation"]').first();
  if (await genericSidebar.isVisible().catch(() => false)) {
    return genericSidebar;
  }

  return null;
}

async function findSectionContainer(page, headingText) {
  const heading = page.getByRole('heading', {
    name: new RegExp(escapeRegExp(headingText), 'i')
  }).first();

  if (await heading.isVisible().catch(() => false)) {
    return heading.locator('xpath=ancestor::*[self::section or self::div][1]');
  }

  const rawText = page.getByText(new RegExp(`^\\s*${escapeRegExp(headingText)}\\s*$`, 'i')).first();
  if (await rawText.isVisible().catch(() => false)) {
    return rawText.locator('xpath=ancestor::*[self::section or self::div][1]');
  }

  return null;
}

async function validateLegalPage({ page, context, linkText, headingText, evidenceDir, screenshotName }) {
  const popupPromise = page.waitForEvent('popup', { timeout: 8000 }).catch(() => null);

  await clickVisibleTextAndWait(page, [linkText], { roles: ['link', 'button'] });
  let legalPage = await popupPromise;
  let openedNewTab = true;

  if (!legalPage) {
    legalPage = page;
    openedNewTab = false;
  }

  await legalPage.waitForLoadState('domcontentloaded', { timeout: 20000 }).catch(() => {});
  await waitForUiToSettle(legalPage);

  const headingLocator = legalPage
    .getByRole('heading', { name: new RegExp(escapeRegExp(headingText), 'i') })
    .first();
  const fallbackHeading = legalPage
    .getByText(new RegExp(escapeRegExp(headingText), 'i'))
    .first();
  const legalTextHint = legalPage
    .locator('main, article, body')
    .filter({ hasText: /Términos|Condiciones|Privacidad|Datos personales|responsabilidad|uso/i })
    .first();

  const hasHeading =
    (await headingLocator.isVisible().catch(() => false)) ||
    (await fallbackHeading.isVisible().catch(() => false));
  const hasLegalText = await legalTextHint.isVisible().catch(() => false);

  const finalUrl = legalPage.url();
  const screenshotPath = await capture(legalPage, evidenceDir, screenshotName, true);

  if (openedNewTab) {
    await legalPage.close().catch(() => {});
    await page.bringToFront();
    await waitForUiToSettle(page);
  } else {
    await page.goBack({ waitUntil: 'domcontentloaded', timeout: 20000 }).catch(() => {});
    await waitForUiToSettle(page);
  }

  return {
    hasHeading,
    hasLegalText,
    finalUrl,
    screenshotPath,
    openedNewTab
  };
}

test('saleads_mi_negocio_full_test', async ({ page, context }, testInfo) => {
  const evidenceDir = path.join(testInfo.outputDir, 'evidence');
  fs.mkdirSync(evidenceDir, { recursive: true });

  const report = Object.fromEntries(
    STEP_FIELDS.map((field) => [field, { status: 'FAIL', details: 'Not executed.' }])
  );
  const extraEvidence = {
    termsUrl: null,
    privacyUrl: null
  };

  const setPass = (field, details) => {
    report[field] = { status: 'PASS', details };
  };
  const setFail = (field, details) => {
    report[field] = { status: 'FAIL', details };
  };
  const failDueToPrerequisite = (field, prerequisite) => {
    setFail(field, `Skipped because prerequisite failed: ${prerequisite}`);
  };

  const configuredLoginUrl =
    process.env.SALEADS_LOGIN_URL ||
    process.env.saleads_login_url ||
    process.env.npm_config_saleads_login_url ||
    null;

  if (configuredLoginUrl) {
    await page.goto(configuredLoginUrl, { waitUntil: 'domcontentloaded' });
    await waitForUiToSettle(page);
  }

  try {
    // Step 1: Login with Google
    const loginButtonLabels = [
      'Sign in with Google',
      'Iniciar sesión con Google',
      'Continuar con Google',
      'Login with Google'
    ];
    const loginButton = await firstVisibleLocator(page, loginButtonLabels, {
      roles: ['button', 'link']
    });

    if (!loginButton) {
      throw new Error(
        [
          `Could not find visible element with text: ${loginButtonLabels.join(' | ')}`,
          `Current page URL: ${page.url()}`,
          configuredLoginUrl
            ? 'Configured login URL was used, but expected login CTA was not present.'
            : 'No SALEADS_LOGIN_URL configured. Provide SALEADS_LOGIN_URL or ensure the browser starts at the SaleADS login page.'
        ].join(' ')
      );
    }

    await loginButton.click({ timeout: 15000 });
    await waitForUiToSettle(page);

    await resolveAccountEmailSelection(context, 'juanlucasbarbiergarzon@gmail.com');
    await waitForUiToSettle(page);

    const sidebar = await findSidebar(page);
    const mainSurfaceVisible = await page.locator('main, [role="main"], section').first().isVisible();
    const sidebarVisible = sidebar ? await sidebar.isVisible() : false;

    await capture(page, evidenceDir, '01-dashboard-loaded', true);

    if (mainSurfaceVisible && sidebarVisible) {
      setPass('Login', 'Main app interface and left sidebar are visible after Google login.');
    } else {
      throw new Error('Dashboard main interface or sidebar not visible after login.');
    }
  } catch (error) {
    await capture(page, evidenceDir, '01-login-step-failed', true).catch(() => {});
    setFail('Login', `Login validation failed: ${error.message}`);
  }

  try {
    // Step 2: Open Mi Negocio menu
    if (report['Login'].status !== 'PASS') {
      throw new Error('Login step failed.');
    }

    await clickVisibleTextAndWait(page, ['Mi Negocio'], { roles: ['link', 'button', 'menuitem'] });

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible({ timeout: 15000 });

    await capture(page, evidenceDir, '02-mi-negocio-menu-expanded', true);
    setPass(
      'Mi Negocio menu',
      "Menu expanded successfully; 'Agregar Negocio' and 'Administrar Negocios' are visible."
    );
  } catch (error) {
    setFail('Mi Negocio menu', `Mi Negocio menu validation failed: ${error.message}`);
  }

  try {
    // Step 3: Validate Agregar Negocio modal
    if (report['Mi Negocio menu'].status !== 'PASS') {
      throw new Error('Mi Negocio menu step failed.');
    }

    await clickVisibleTextAndWait(page, ['Agregar Negocio'], { roles: ['link', 'button', 'menuitem'] });

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Nombre del Negocio/i).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({
      timeout: 15000
    });
    await expect(page.getByRole('button', { name: /Cancelar/i }).first()).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole('button', { name: /Crear Negocio/i }).first()).toBeVisible({ timeout: 15000 });

    await capture(page, evidenceDir, '03-agregar-negocio-modal', true);

    const businessNameInput = page
      .getByLabel(/Nombre del Negocio/i)
      .first()
      .or(page.getByPlaceholder(/Nombre del Negocio/i).first());

    if (await businessNameInput.isVisible().catch(() => false)) {
      await businessNameInput.click({ timeout: 10000 });
      await businessNameInput.fill('Negocio Prueba Automatización');
    }

    await clickVisibleTextAndWait(page, ['Cancelar'], { roles: ['button'] });
    setPass('Agregar Negocio modal', "Modal content validated and closed with 'Cancelar'.");
  } catch (error) {
    setFail('Agregar Negocio modal', `Agregar Negocio modal validation failed: ${error.message}`);
  }

  try {
    // Step 4: Open Administrar Negocios
    if (report['Mi Negocio menu'].status !== 'PASS') {
      throw new Error('Mi Negocio menu step failed.');
    }

    const administrarVisible = await page.getByText(/Administrar Negocios/i).first().isVisible();
    if (!administrarVisible) {
      await clickVisibleTextAndWait(page, ['Mi Negocio'], { roles: ['button', 'link', 'menuitem'] });
    }

    await clickVisibleTextAndWait(page, ['Administrar Negocios'], {
      roles: ['link', 'button', 'menuitem']
    });

    await expect(page.getByText(/Información General/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible({ timeout: 20000 });

    await capture(page, evidenceDir, '04-administrar-negocios-account-page', true);
    setPass('Administrar Negocios view', 'All expected account sections are visible.');
  } catch (error) {
    setFail('Administrar Negocios view', `Administrar Negocios page validation failed: ${error.message}`);
  }

  try {
    // Step 5: Validate Información General
    if (report['Administrar Negocios view'].status !== 'PASS') {
      throw new Error('Administrar Negocios view step failed.');
    }

    const infoSection = await findSectionContainer(page, 'Información General');
    const scope = infoSection || page;

    const emailVisible =
      (await scope.getByText(/juanlucasbarbiergarzon@gmail\.com/i).first().isVisible().catch(() => false)) ||
      (await scope
        .getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/)
        .first()
        .isVisible()
        .catch(() => false));
    const businessPlanVisible = await scope.getByText(/BUSINESS PLAN/i).first().isVisible().catch(() => false);
    const changePlanVisible = await scope
      .getByRole('button', { name: /Cambiar Plan/i })
      .first()
      .isVisible()
      .catch(async () => scope.getByText(/Cambiar Plan/i).first().isVisible().catch(() => false));

    const fullText = (await scope.textContent().catch(() => '')) || '';
    const hasLikelyName = /\b(Nombre|Usuario|Perfil|Juan|Lucas|Barbier)\b/i.test(fullText);

    if (emailVisible && businessPlanVisible && changePlanVisible && hasLikelyName) {
      setPass('Información General', 'Name/user info, email, BUSINESS PLAN, and Cambiar Plan are visible.');
    } else {
      throw new Error(
        `Missing expected fields in Información General (name=${hasLikelyName}, email=${emailVisible}, plan=${businessPlanVisible}, cambiar=${changePlanVisible}).`
      );
    }
  } catch (error) {
    setFail('Información General', `Información General validation failed: ${error.message}`);
  }

  try {
    // Step 6: Validate Detalles de la Cuenta
    if (report['Administrar Negocios view'].status !== 'PASS') {
      throw new Error('Administrar Negocios view step failed.');
    }

    const detailsSection = await findSectionContainer(page, 'Detalles de la Cuenta');
    const scope = detailsSection || page;

    await expect(scope.getByText(/Cuenta creada/i).first()).toBeVisible({ timeout: 15000 });
    await expect(scope.getByText(/Estado activo/i).first()).toBeVisible({ timeout: 15000 });
    await expect(scope.getByText(/Idioma seleccionado/i).first()).toBeVisible({ timeout: 15000 });

    setPass('Detalles de la Cuenta', 'Cuenta creada, Estado activo, and Idioma seleccionado are visible.');
  } catch (error) {
    setFail('Detalles de la Cuenta', `Detalles de la Cuenta validation failed: ${error.message}`);
  }

  try {
    // Step 7: Validate Tus Negocios
    if (report['Administrar Negocios view'].status !== 'PASS') {
      throw new Error('Administrar Negocios view step failed.');
    }

    const businessSection = await findSectionContainer(page, 'Tus Negocios');
    const scope = businessSection || page;

    await expect(scope.getByText(/Tus Negocios/i).first()).toBeVisible({ timeout: 15000 });
    await expect(scope.getByRole('button', { name: /Agregar Negocio/i }).first()).toBeVisible({
      timeout: 15000
    });
    await expect(scope.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({ timeout: 15000 });

    const hasBusinessItems =
      (await scope.locator('[role="listitem"], li, tbody tr, [class*="business"]').first().isVisible().catch(
        () => false
      )) ||
      (await scope.getByText(/Negocio/i).first().isVisible().catch(() => false));

    if (!hasBusinessItems) {
      throw new Error('No business list items were detected in Tus Negocios.');
    }

    setPass('Tus Negocios', 'Business list, Agregar Negocio button, and usage text are visible.');
  } catch (error) {
    setFail('Tus Negocios', `Tus Negocios validation failed: ${error.message}`);
  }

  try {
    // Step 8: Validate Términos y Condiciones
    if (report['Administrar Negocios view'].status !== 'PASS') {
      throw new Error('Administrar Negocios view step failed.');
    }

    const terms = await validateLegalPage({
      page,
      context,
      linkText: 'Términos y Condiciones',
      headingText: 'Términos y Condiciones',
      evidenceDir,
      screenshotName: '08-terminos-y-condiciones'
    });

    extraEvidence.termsUrl = terms.finalUrl;

    if (terms.hasHeading && terms.hasLegalText) {
      setPass(
        'Términos y Condiciones',
        `Legal page validated (newTab=${terms.openedNewTab}) with final URL: ${terms.finalUrl}`
      );
    } else {
      throw new Error(
        `Legal page validation failed (heading=${terms.hasHeading}, legalText=${terms.hasLegalText}, url=${terms.finalUrl}).`
      );
    }
  } catch (error) {
    setFail('Términos y Condiciones', `Términos y Condiciones validation failed: ${error.message}`);
  }

  try {
    // Step 9: Validate Política de Privacidad
    if (report['Administrar Negocios view'].status !== 'PASS') {
      throw new Error('Administrar Negocios view step failed.');
    }

    const privacy = await validateLegalPage({
      page,
      context,
      linkText: 'Política de Privacidad',
      headingText: 'Política de Privacidad',
      evidenceDir,
      screenshotName: '09-politica-de-privacidad'
    });

    extraEvidence.privacyUrl = privacy.finalUrl;

    if (privacy.hasHeading && privacy.hasLegalText) {
      setPass(
        'Política de Privacidad',
        `Legal page validated (newTab=${privacy.openedNewTab}) with final URL: ${privacy.finalUrl}`
      );
    } else {
      throw new Error(
        `Legal page validation failed (heading=${privacy.hasHeading}, legalText=${privacy.hasLegalText}, url=${privacy.finalUrl}).`
      );
    }
  } catch (error) {
    setFail('Política de Privacidad', `Política de Privacidad validation failed: ${error.message}`);
  }

  // Ensure dependent steps are explicitly marked when prerequisites fail.
  if (report['Login'].status !== 'PASS') {
    for (const field of [
      'Mi Negocio menu',
      'Agregar Negocio modal',
      'Administrar Negocios view',
      'Información General',
      'Detalles de la Cuenta',
      'Tus Negocios',
      'Términos y Condiciones',
      'Política de Privacidad'
    ]) {
      if (report[field].details === 'Not executed.') {
        failDueToPrerequisite(field, 'Login');
      }
    }
  }
  if (report['Administrar Negocios view'].status !== 'PASS') {
    for (const field of [
      'Información General',
      'Detalles de la Cuenta',
      'Tus Negocios',
      'Términos y Condiciones',
      'Política de Privacidad'
    ]) {
      if (report[field].details === 'Not executed.') {
        failDueToPrerequisite(field, 'Administrar Negocios view');
      }
    }
  }

  const finalReport = {
    testName: 'saleads_mi_negocio_full_test',
    timestampUtc: new Date().toISOString(),
    loginUrlUsed: configuredLoginUrl,
    steps: report,
    evidence: extraEvidence
  };

  const reportPath = path.join(evidenceDir, 'final-report.json');
  fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), 'utf-8');
  await testInfo.attach('saleads-mi-negocio-final-report', {
    path: reportPath,
    contentType: 'application/json'
  });

  for (const field of STEP_FIELDS) {
    console.log(`[FINAL REPORT] ${field}: ${report[field].status} - ${report[field].details}`);
  }
  console.log(`[FINAL REPORT] Términos y Condiciones URL: ${extraEvidence.termsUrl || 'N/A'}`);
  console.log(`[FINAL REPORT] Política de Privacidad URL: ${extraEvidence.privacyUrl || 'N/A'}`);

  const failedSteps = STEP_FIELDS.filter((field) => report[field].status !== 'PASS');
  expect(failedSteps, `One or more workflow validations failed: ${failedSteps.join(', ')}`).toEqual([]);
});
