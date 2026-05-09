const fs = require('fs');
const path = require('path');
const { test, expect } = require('@playwright/test');

const ACCOUNT_EMAIL = 'juanlucasbarbiergarzon@gmail.com';
const ARTIFACTS_DIR = path.resolve(__dirname, '..', 'artifacts');
const SCREENSHOTS_DIR = path.join(ARTIFACTS_DIR, 'screenshots');
const FINAL_REPORT_PATH = path.join(ARTIFACTS_DIR, 'saleads_mi_negocio_full_test_report.json');

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

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function sanitizeFileName(value) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
}

function initReport() {
  const result = {};
  for (const field of REPORT_FIELDS) {
    result[field] = {
      status: 'FAIL',
      details: 'Step did not run.',
      evidence: [],
      url: null
    };
  }

  return {
    name: 'saleads_mi_negocio_full_test',
    generatedAt: new Date().toISOString(),
    results: result,
    overallStatus: 'FAIL'
  };
}

function setStepResult(report, field, payload) {
  report.results[field] = {
    status: payload.status,
    details: payload.details,
    evidence: payload.evidence || [],
    url: payload.url || null
  };
}

async function waitForUi(page) {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(800);
}

async function firstVisible(candidates, timeoutMs = 30000) {
  const start = Date.now();

  while (Date.now() - start < timeoutMs) {
    for (const candidate of candidates) {
      const locator = candidate.first();
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }
    await sleep(250);
  }

  throw new Error('Unable to find any visible locator from provided candidates.');
}

async function captureCheckpoint(page, name) {
  const fileName = `${sanitizeFileName(name)}.png`;
  const filePath = path.join(SCREENSHOTS_DIR, fileName);

  await waitForUi(page);
  await page.screenshot({ path: filePath, fullPage: true });

  return path.relative(path.resolve(__dirname, '..'), filePath);
}

async function executeStep(report, stepName, action) {
  try {
    const result = await action();
    setStepResult(report, stepName, {
      status: 'PASS',
      details: result?.details || 'All validations passed.',
      evidence: result?.evidence || [],
      url: result?.url || null
    });
  } catch (error) {
    setStepResult(report, stepName, {
      status: 'FAIL',
      details: error instanceof Error ? error.message : String(error),
      evidence: []
    });
  }
}

async function selectGoogleAccountIfPrompted(targetPage) {
  const accountCandidate = await firstVisible(
    [
      targetPage.getByText(ACCOUNT_EMAIL),
      targetPage.getByRole('button', { name: new RegExp(ACCOUNT_EMAIL, 'i') }),
      targetPage.getByRole('link', { name: new RegExp(ACCOUNT_EMAIL, 'i') })
    ],
    8000
  ).catch(() => null);

  if (!accountCandidate) {
    return false;
  }

  await accountCandidate.click();
  await waitForUi(targetPage);

  return true;
}

async function ensureMiNegocioExpanded(page) {
  const agregarVisible = await page.getByText(/^Agregar Negocio$/i).first().isVisible().catch(() => false);
  const adminVisible = await page.getByText(/^Administrar Negocios$/i).first().isVisible().catch(() => false);
  if (agregarVisible && adminVisible) {
    return;
  }

  const negocioSection = await firstVisible(
    [
      page.getByRole('button', { name: /^Negocio$/i }),
      page.getByRole('link', { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i)
    ],
    10000
  );

  await negocioSection.click();
  await waitForUi(page);

  const miNegocio = await firstVisible(
    [
      page.getByRole('button', { name: /^Mi Negocio$/i }),
      page.getByRole('link', { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i)
    ],
    15000
  );

  await miNegocio.click();
  await waitForUi(page);
}

async function validateLegalDocument({ page, context, linkText, headingRegex, checkpointName }) {
  const appPageUrlBeforeClick = page.url();
  const link = await firstVisible(
    [
      page.getByRole('link', { name: new RegExp(linkText, 'i') }),
      page.getByText(new RegExp(linkText, 'i'))
    ],
    15000
  );

  const popupPromise = context.waitForEvent('page', { timeout: 10000 }).catch(() => null);
  const navigationPromise = page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 10000 }).catch(() => null);

  await link.click();

  const popupPage = await popupPromise;
  const targetPage = popupPage || page;

  if (popupPage) {
    await popupPage.waitForLoadState('domcontentloaded');
  } else {
    await navigationPromise;
    await waitForUi(page);
  }

  const heading = await firstVisible(
    [
      targetPage.getByRole('heading', { name: headingRegex }),
      targetPage.getByText(headingRegex)
    ],
    30000
  );
  await expect(heading).toBeVisible();

  const legalContent = await firstVisible(
    [
      targetPage.locator('main p'),
      targetPage.locator('article p'),
      targetPage.locator('body p')
    ],
    30000
  );
  await expect(legalContent).toBeVisible();

  const screenshot = await captureCheckpoint(targetPage, checkpointName);
  const finalUrl = targetPage.url();

  if (popupPage) {
    await popupPage.close();
    await page.bringToFront();
  } else if (page.url() !== appPageUrlBeforeClick) {
    await page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => {});
    await waitForUi(page);
  }

  return { screenshot, finalUrl };
}

test('saleads_mi_negocio_full_test', async ({ page, context }) => {
  fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });

  const report = initReport();
  const configuredLoginUrl = process.env.SALEADS_LOGIN_URL?.trim();

  if (configuredLoginUrl) {
    await page.goto(configuredLoginUrl, { waitUntil: 'domcontentloaded' });
  }

  if (page.url() === 'about:blank') {
    setStepResult(report, 'Login', {
      status: 'FAIL',
      details: 'Set SALEADS_LOGIN_URL to the active environment login page before running this test.',
      evidence: []
    });
  } else {
    await executeStep(report, 'Login', async () => {
      const loginButton = await firstVisible(
        [
          page.getByRole('button', { name: /google/i }),
          page.getByRole('link', { name: /google/i }),
          page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i)
        ],
        45000
      );

      const popupPromise = page.waitForEvent('popup', { timeout: 10000 }).catch(() => null);
      await loginButton.click();

      const popupPage = await popupPromise;
      if (popupPage) {
        await popupPage.waitForLoadState('domcontentloaded');
        await selectGoogleAccountIfPrompted(popupPage);
        await popupPage.waitForClose({ timeout: 90000 }).catch(() => {});
      } else {
        await selectGoogleAccountIfPrompted(page);
      }

      await waitForUi(page);

      const sidebar = await firstVisible(
        [
          page.locator('aside'),
          page.getByRole('navigation'),
          page.locator('[class*="sidebar"]')
        ],
        60000
      );
      await expect(sidebar).toBeVisible();

      const negocioText = await firstVisible(
        [
          page.getByText(/^Negocio$/i),
          page.getByText(/^Mi Negocio$/i)
        ],
        60000
      );
      await expect(negocioText).toBeVisible();

      const screenshot = await captureCheckpoint(page, '01-dashboard-loaded');

      return {
        details: 'Main application loaded and left sidebar is visible.',
        evidence: [screenshot]
      };
    });
  }

  if (report.results.Login.status === 'PASS') {
    await executeStep(report, 'Mi Negocio menu', async () => {
    await ensureMiNegocioExpanded(page);

    await expect(page.getByText(/^Agregar Negocio$/i)).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i)).toBeVisible();

    const screenshot = await captureCheckpoint(page, '02-mi-negocio-expanded');
    return {
      details: 'Mi Negocio submenu expanded with both options visible.',
      evidence: [screenshot]
    };
  });

  await executeStep(report, 'Agregar Negocio modal', async () => {
    const agregarNegocio = await firstVisible(
      [
        page.getByRole('button', { name: /^Agregar Negocio$/i }),
        page.getByRole('link', { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i)
      ],
      20000
    );

    await agregarNegocio.click();
    await waitForUi(page);

    await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeVisible();

    const businessNameInput = await firstVisible(
      [
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.locator('input[name*="negocio" i]'),
        page.locator('input[id*="negocio" i]')
      ],
      20000
    );
    await expect(businessNameInput).toBeVisible();

    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /^Cancelar$/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /^Crear Negocio$/i })).toBeVisible();

    const screenshot = await captureCheckpoint(page, '03-agregar-negocio-modal');

    await businessNameInput.fill('Negocio Prueba Automatización');
    const cancelButton = await firstVisible([page.getByRole('button', { name: /^Cancelar$/i }), page.getByText(/^Cancelar$/i)]);
    await cancelButton.click();
    await waitForUi(page);

    return {
      details: 'Agregar Negocio modal validated and closed with Cancelar.',
      evidence: [screenshot]
    };
  });

  await executeStep(report, 'Administrar Negocios view', async () => {
    await ensureMiNegocioExpanded(page);

    const adminNegocios = await firstVisible(
      [
        page.getByRole('link', { name: /^Administrar Negocios$/i }),
        page.getByRole('button', { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i)
      ],
      20000
    );
    await adminNegocios.click();
    await waitForUi(page);

    await expect(page.getByText(/Informaci[oó]n General/i)).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Secci[oó]n Legal/i)).toBeVisible();

    const screenshot = await captureCheckpoint(page, '04-administrar-negocios');
    return {
      details: 'Administrar Negocios page loaded with all account sections.',
      evidence: [screenshot]
    };
  });

  await executeStep(report, 'Información General', async () => {
    const userName = await firstVisible(
      [
        page.locator('[data-testid*="name" i]'),
        page.locator('[class*="name" i]'),
        page.locator('h1, h2')
      ],
      15000
    );
    await expect(userName).toBeVisible();

    const email = await firstVisible(
      [
        page.getByText(new RegExp(ACCOUNT_EMAIL, 'i')),
        page.locator('text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/')
      ],
      15000
    );
    await expect(email).toBeVisible();

    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /Cambiar Plan/i })).toBeVisible();

    return {
      details: 'Información General section validated.'
    };
  });

  await executeStep(report, 'Detalles de la Cuenta', async () => {
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();

    return {
      details: 'Detalles de la Cuenta values are visible.'
    };
  });

  await executeStep(report, 'Tus Negocios', async () => {
    const businessList = await firstVisible(
      [
        page.locator('[class*="business" i]'),
        page.locator('table'),
        page.locator('[role="list"]')
      ],
      15000
    );
    await expect(businessList).toBeVisible();

    const addBusinessAction = await firstVisible(
      [
        page.getByRole('button', { name: /^Agregar Negocio$/i }),
        page.getByRole('link', { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i)
      ],
      15000
    );
    await expect(addBusinessAction).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();

    return {
      details: 'Tus Negocios section validated.'
    };
  });

  await executeStep(report, 'Términos y Condiciones', async () => {
    const { screenshot, finalUrl } = await validateLegalDocument({
      page,
      context,
      linkText: 'Términos y Condiciones',
      headingRegex: /Términos y Condiciones/i,
      checkpointName: '05-terminos-y-condiciones'
    });

    return {
      details: 'Legal terms page opened and content is visible.',
      evidence: [screenshot],
      url: finalUrl
    };
  });

  await executeStep(report, 'Política de Privacidad', async () => {
    const { screenshot, finalUrl } = await validateLegalDocument({
      page,
      context,
      linkText: 'Política de Privacidad',
      headingRegex: /Política de Privacidad/i,
      checkpointName: '06-politica-de-privacidad'
    });

    return {
      details: 'Privacy policy page opened and content is visible.',
      evidence: [screenshot],
      url: finalUrl
    };
  });
  } else {
    for (const field of REPORT_FIELDS.filter((field) => field !== 'Login')) {
      setStepResult(report, field, {
        status: 'FAIL',
        details: 'Skipped because login did not complete successfully.',
        evidence: []
      });
    }
  }

  const failedSteps = REPORT_FIELDS.filter((field) => report.results[field].status !== 'PASS');
  report.overallStatus = failedSteps.length === 0 ? 'PASS' : 'FAIL';
  report.generatedAt = new Date().toISOString();

  fs.mkdirSync(path.dirname(FINAL_REPORT_PATH), { recursive: true });
  fs.writeFileSync(FINAL_REPORT_PATH, `${JSON.stringify(report, null, 2)}\n`, 'utf8');

  expect(
    failedSteps,
    `One or more workflow sections failed. Report available at ${path.relative(path.resolve(__dirname, '..'), FINAL_REPORT_PATH)}`
  ).toEqual([]);
});
