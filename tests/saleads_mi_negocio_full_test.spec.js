const { test, expect } = require('@playwright/test');

const GOOGLE_ACCOUNT_EMAIL = 'juanlucasbarbiergarzon@gmail.com';
const STEP_STATUSES = {
  PASS: 'PASS',
  FAIL: 'FAIL',
};

/**
 * Waits for page UI to settle after an action.
 */
async function waitForUi(page) {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
}

/**
 * Save a screenshot checkpoint in test-results.
 */
async function checkpoint(page, testInfo, name, fullPage = false) {
  await page.screenshot({
    path: testInfo.outputPath(`${name}.png`),
    fullPage,
  });
}

/**
 * Mark a report field pass/fail and keep details.
 */
function setReport(report, field, ok, details) {
  report[field] = {
    status: ok ? STEP_STATUSES.PASS : STEP_STATUSES.FAIL,
    details,
  };
}

/**
 * Utility for clicking an element by visible text.
 */
async function clickByText(page, text, exact = true) {
  const candidate = page.getByText(text, { exact }).first();
  await expect(candidate).toBeVisible();
  await candidate.click();
  await waitForUi(page);
}

/**
 * Clicks an element by visible text and waits for UI.
 */
async function clickByTextOptions(page, textCandidates) {
  for (const text of textCandidates) {
    const candidate = page.getByText(text, { exact: true }).first();
    if (await candidate.isVisible().catch(() => false)) {
      await candidate.click();
      await waitForUi(page);
      return;
    }
  }

  throw new Error(`None of these text options were visible: ${textCandidates.join(', ')}`);
}

/**
 * Validate legal page in same tab or popup, then return to app.
 */
async function validateLegalLink(page, context, linkText, headingText, checkpointName, testInfo) {
  const linkLocator = page.getByText(linkText, { exact: true }).first();
  await expect(linkLocator).toBeVisible();

  const [popup] = await Promise.all([
    context.waitForEvent('page', { timeout: 5000 }).catch(() => null),
    linkLocator.click(),
  ]);

  const legalPage = popup || page;
  await legalPage.waitForLoadState('domcontentloaded');
  await legalPage.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});

  await expect(legalPage.getByRole('heading', { name: headingText })).toBeVisible();
  await expect(legalPage.getByText(headingText, { exact: false })).toBeVisible();

  await checkpoint(legalPage, testInfo, checkpointName);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  } else {
    await page.goBack().catch(() => {});
    await waitForUi(page);
  }

  return finalUrl;
}

test('saleads_mi_negocio_full_test', async ({ page, context }, testInfo) => {
  const report = {};

  // Step 1: Login with Google (starts on existing login page).
  let loginPass = true;
  let loginDetails = 'Main app and left sidebar are visible after Google login.';
  try {
    const configuredBaseUrl = process.env.SALEADS_BASE_URL || process.env.BASE_URL;
    if (page.url() === 'about:blank' && configuredBaseUrl) {
      await page.goto(configuredBaseUrl, { waitUntil: 'domcontentloaded' });
      await waitForUi(page);
    }

    const signInWithGoogle = page.getByText('Sign in with Google', { exact: false }).first();
    const loginWithGoogle = page.getByText('Iniciar con Google', { exact: false }).first();
    const loginButton = page.getByRole('button', { name: /google|iniciar sesi[oó]n|sign in/i }).first();
    let googlePopup = null;

    if (await signInWithGoogle.isVisible().catch(() => false)) {
      [googlePopup] = await Promise.all([
        context.waitForEvent('page', { timeout: 5000 }).catch(() => null),
        signInWithGoogle.click(),
      ]);
    } else if (await loginWithGoogle.isVisible().catch(() => false)) {
      [googlePopup] = await Promise.all([
        context.waitForEvent('page', { timeout: 5000 }).catch(() => null),
        loginWithGoogle.click(),
      ]);
    } else if (await loginButton.isVisible().catch(() => false)) {
      [googlePopup] = await Promise.all([
        context.waitForEvent('page', { timeout: 5000 }).catch(() => null),
        loginButton.click(),
      ]);
    }

    await waitForUi(page);

    const authPage = googlePopup || page;
    await authPage.waitForLoadState('domcontentloaded');
    await authPage.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});

    const googleAccountChoice = authPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
    if (await googleAccountChoice.isVisible().catch(() => false)) {
      await googleAccountChoice.click();
      await authPage.waitForLoadState('domcontentloaded');
      await authPage.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
    }

    if (googlePopup && !googlePopup.isClosed()) {
      await googlePopup.close().catch(() => {});
    }

    await page.bringToFront();
    await waitForUi(page);

    const sidebar = page.locator('aside').first();
    await expect(sidebar).toBeVisible();
    await checkpoint(page, testInfo, '01-dashboard-loaded');
  } catch (error) {
    loginPass = false;
    loginDetails = `Login flow failed: ${error.message}`;
  }
  setReport(report, 'Login', loginPass, loginDetails);

  // Step 2: Open Mi Negocio menu.
  let miNegocioMenuPass = true;
  let miNegocioMenuDetails = 'Negocio > Mi Negocio expanded and submenu items are visible.';
  try {
    await clickByTextOptions(page, ['Negocio', 'Business']);
    await clickByTextOptions(page, ['Mi Negocio', 'My Business']);
    await expect(page.getByText('Agregar Negocio', { exact: true })).toBeVisible();
    await expect(page.getByText('Administrar Negocios', { exact: true })).toBeVisible();
    await checkpoint(page, testInfo, '02-mi-negocio-menu-expanded');
  } catch (error) {
    miNegocioMenuPass = false;
    miNegocioMenuDetails = `Mi Negocio menu validation failed: ${error.message}`;
  }
  setReport(report, 'Mi Negocio menu', miNegocioMenuPass, miNegocioMenuDetails);

  // Step 3: Validate Agregar Negocio modal.
  let agregarModalPass = true;
  let agregarModalDetails = 'Crear Nuevo Negocio modal and expected controls are visible.';
  try {
    await clickByText(page, 'Agregar Negocio', false);
    await expect(page.getByRole('heading', { name: 'Crear Nuevo Negocio' })).toBeVisible();
    const businessNameInputByLabel = page.getByLabel('Nombre del Negocio').first();
    const businessNameInputByPlaceholder = page.getByPlaceholder('Nombre del Negocio').first();
    const inputByLabelVisible = await businessNameInputByLabel.isVisible().catch(() => false);
    const businessNameInput = inputByLabelVisible ? businessNameInputByLabel : businessNameInputByPlaceholder;
    await expect(businessNameInput).toBeVisible();
    await expect(page.getByText('Tienes 2 de 3 negocios', { exact: false })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Cancelar' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Crear Negocio' })).toBeVisible();
    await checkpoint(page, testInfo, '03-agregar-negocio-modal');

    // Optional action: type sample business name and cancel modal.
    await businessNameInput.fill('Negocio Prueba Automatización');
    await page.getByRole('button', { name: 'Cancelar' }).click();
    await waitForUi(page);
  } catch (error) {
    agregarModalPass = false;
    agregarModalDetails = `Agregar Negocio modal validation failed: ${error.message}`;
  }
  setReport(report, 'Agregar Negocio modal', agregarModalPass, agregarModalDetails);

  // Step 4: Open Administrar Negocios.
  let adminViewPass = true;
  let adminViewDetails = 'Administrar Negocios view loaded with all expected sections.';
  try {
    // Re-expand menu if collapsed.
    if (!(await page.getByText('Administrar Negocios', { exact: true }).first().isVisible().catch(() => false))) {
      await clickByTextOptions(page, ['Mi Negocio', 'My Business']);
    }

    await clickByText(page, 'Administrar Negocios', false);
    await expect(page.getByText('Información General', { exact: true })).toBeVisible();
    await expect(page.getByText('Detalles de la Cuenta', { exact: true })).toBeVisible();
    await expect(page.getByText('Tus Negocios', { exact: true })).toBeVisible();
    await expect(page.getByText('Sección Legal', { exact: true })).toBeVisible();
    await checkpoint(page, testInfo, '04-administrar-negocios', true);
  } catch (error) {
    adminViewPass = false;
    adminViewDetails = `Administrar Negocios page validation failed: ${error.message}`;
  }
  setReport(report, 'Administrar Negocios view', adminViewPass, adminViewDetails);

  // Step 5: Validate Información General.
  let infoGeneralPass = true;
  let infoGeneralDetails = 'User name/email, BUSINESS PLAN, and Cambiar Plan are visible.';
  try {
    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByRole('button', { name: 'Cambiar Plan' })).toBeVisible();

    const emailVisible = await page.getByText(/.+@.+\..+/).first().isVisible().catch(() => false);
    const infoGeneralSection = page
      .locator('section, div')
      .filter({ has: page.getByText('Información General', { exact: true }) })
      .first();
    const usernameVisible = await infoGeneralSection
      .locator('h1, h2, h3, h4, p, span, [data-testid*="name"], [class*="name"]')
      .first()
      .isVisible()
      .catch(() => false);
    expect(emailVisible).toBeTruthy();
    expect(usernameVisible).toBeTruthy();
  } catch (error) {
    infoGeneralPass = false;
    infoGeneralDetails = `Información General validation failed: ${error.message}`;
  }
  setReport(report, 'Información General', infoGeneralPass, infoGeneralDetails);

  // Step 6: Validate Detalles de la Cuenta.
  let detallesPass = true;
  let detallesDetails = 'Cuenta creada, Estado activo, and Idioma seleccionado are visible.';
  try {
    await expect(page.getByText('Cuenta creada', { exact: false })).toBeVisible();
    await expect(page.getByText('Estado activo', { exact: false })).toBeVisible();
    await expect(page.getByText('Idioma seleccionado', { exact: false })).toBeVisible();
  } catch (error) {
    detallesPass = false;
    detallesDetails = `Detalles de la Cuenta validation failed: ${error.message}`;
  }
  setReport(report, 'Detalles de la Cuenta', detallesPass, detallesDetails);

  // Step 7: Validate Tus Negocios.
  let tusNegociosPass = true;
  let tusNegociosDetails = 'Business list, Agregar Negocio button, and 2/3 text are visible.';
  try {
    await expect(page.getByText('Tus Negocios', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Agregar Negocio' })).toBeVisible();
    await expect(page.getByText('Tienes 2 de 3 negocios', { exact: false })).toBeVisible();
  } catch (error) {
    tusNegociosPass = false;
    tusNegociosDetails = `Tus Negocios validation failed: ${error.message}`;
  }
  setReport(report, 'Tus Negocios', tusNegociosPass, tusNegociosDetails);

  // Step 8: Validate Términos y Condiciones.
  let terminosPass = true;
  let terminosDetails = 'Legal page rendered correctly.';
  try {
    const url = await validateLegalLink(
      page,
      context,
      'Términos y Condiciones',
      'Términos y Condiciones',
      '08-terminos-y-condiciones',
      testInfo
    );
    terminosDetails = `Legal content visible. Final URL: ${url}`;
  } catch (error) {
    terminosPass = false;
    terminosDetails = `Términos y Condiciones validation failed: ${error.message}`;
  }
  setReport(report, 'Términos y Condiciones', terminosPass, terminosDetails);

  // Step 9: Validate Política de Privacidad.
  let privacidadPass = true;
  let privacidadDetails = 'Privacy page rendered correctly.';
  try {
    const url = await validateLegalLink(
      page,
      context,
      'Política de Privacidad',
      'Política de Privacidad',
      '09-politica-de-privacidad',
      testInfo
    );
    privacidadDetails = `Legal content visible. Final URL: ${url}`;
  } catch (error) {
    privacidadPass = false;
    privacidadDetails = `Política de Privacidad validation failed: ${error.message}`;
  }
  setReport(report, 'Política de Privacidad', privacidadPass, privacidadDetails);

  // Step 10: Final report.
  await testInfo.attach('final-report.json', {
    body: JSON.stringify(report, null, 2),
    contentType: 'application/json',
  });

  const failingFields = Object.entries(report)
    .filter(([, value]) => value.status === STEP_STATUSES.FAIL)
    .map(([field]) => field);

  expect(
    failingFields,
    `Some workflow validations failed: ${failingFields.length ? failingFields.join(', ') : 'none'}`
  ).toEqual([]);
});
