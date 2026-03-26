const { test, expect } = require('@playwright/test');
const fs = require('fs');

const ACCOUNT_EMAIL = 'juanlucasbarbiergarzon@gmail.com';

const STEP_NAMES = {
  login: 'Login',
  menu: 'Mi Negocio menu',
  agregarModal: 'Agregar Negocio modal',
  administrarView: 'Administrar Negocios view',
  infoGeneral: 'Informaci\u00f3n General',
  detallesCuenta: 'Detalles de la Cuenta',
  tusNegocios: 'Tus Negocios',
  terminos: 'T\u00e9rminos y Condiciones',
  privacidad: 'Pol\u00edtica de Privacidad',
};

const STEP_ORDER = [
  STEP_NAMES.login,
  STEP_NAMES.menu,
  STEP_NAMES.agregarModal,
  STEP_NAMES.administrarView,
  STEP_NAMES.infoGeneral,
  STEP_NAMES.detallesCuenta,
  STEP_NAMES.tusNegocios,
  STEP_NAMES.terminos,
  STEP_NAMES.privacidad,
];

const TXT = {
  signInGoogle: [
    /sign in with google/i,
    /login with google/i,
    /continue with google/i,
    /iniciar sesion con google/i,
    /continuar con google/i,
  ],
  negocio: /negocio/i,
  miNegocio: /mi negocio/i,
  agregarNegocio: /agregar negocio/i,
  administrarNegocios: /administrar negocios/i,
  crearNuevoNegocio: /crear nuevo negocio/i,
  nombreDelNegocio: /nombre del negocio/i,
  tienesDosTresNegocios: /tienes\s*2\s*de\s*3\s*negocios/i,
  informacionGeneral: /informaci[oó]n general/i,
  detallesCuenta: /detalles de la cuenta/i,
  tusNegocios: /tus negocios/i,
  seccionLegal: /secci[oó]n legal/i,
  businessPlan: /business plan/i,
  cambiarPlan: /cambiar plan/i,
  cuentaCreada: /cuenta creada/i,
  estadoActivo: /estado activo/i,
  idiomaSeleccionado: /idioma seleccionado/i,
  terminos: /t[eé]rminos y condiciones/i,
  privacidad: /pol[ií]tica de privacidad/i,
};

function createStepMap() {
  return STEP_ORDER.reduce((acc, stepName) => {
    acc[stepName] = { status: 'FAIL', details: 'Not executed' };
    return acc;
  }, {});
}

async function waitForUiIdle(page) {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForLoadState('networkidle').catch(() => {});
  await page.waitForTimeout(700);
}

function buildClickCandidates(scope, label) {
  return [
    scope.getByRole('button', { name: label }),
    scope.getByRole('link', { name: label }),
    scope.getByRole('menuitem', { name: label }),
    scope.getByText(label),
    scope.locator('[aria-label]').filter({ hasText: label }),
  ];
}

async function clickByVisibleLabel(scope, label, timeout = 20000) {
  const start = Date.now();
  while (Date.now() - start < timeout) {
    const candidates = buildClickCandidates(scope, label);
    for (const locator of candidates) {
      const target = locator.first();
      const isVisible = await target.isVisible().catch(() => false);
      if (isVisible) {
        await target.click();
        await waitForUiIdle(scope.page ? scope.page() : scope);
        return;
      }
    }
    await (scope.page ? scope.page() : scope).waitForTimeout(300);
  }
  throw new Error(`Could not click visible element for label pattern: ${label}`);
}

async function clickByAnyLabel(scope, labels, timeout = 20000) {
  let lastError = null;
  for (const label of labels) {
    try {
      await clickByVisibleLabel(scope, label, timeout);
      return;
    } catch (error) {
      lastError = error;
    }
  }
  throw lastError || new Error('Could not click with any provided labels.');
}

async function clickIfVisible(scope, label, timeout = 5000) {
  const start = Date.now();
  while (Date.now() - start < timeout) {
    const candidates = buildClickCandidates(scope, label);
    for (const locator of candidates) {
      const target = locator.first();
      const isVisible = await target.isVisible().catch(() => false);
      if (isVisible) {
        await target.click();
        await waitForUiIdle(scope.page ? scope.page() : scope);
        return true;
      }
    }
    await (scope.page ? scope.page() : scope).waitForTimeout(250);
  }
  return false;
}

async function maybeSelectGoogleAccount(page, desiredEmail) {
  const accountOption = page.getByText(desiredEmail, { exact: false });
  const shouldChoose = await accountOption.first().isVisible().catch(() => false);
  if (shouldChoose) {
    await accountOption.first().click();
    await waitForUiIdle(page);
  }
}

async function clickGoogleLoginAndHandlePopup(page, context) {
  const popupPromise = context.waitForEvent('page', { timeout: 12000 }).catch(() => null);
  await clickByAnyLabel(page, TXT.signInGoogle, 30000);
  const popup = await popupPromise;
  const activePage = popup || page;
  await waitForUiIdle(activePage);
  await maybeSelectGoogleAccount(activePage, ACCOUNT_EMAIL);
  if (popup) {
    await Promise.race([
      popup.waitForEvent('close', { timeout: 30000 }).catch(() => null),
      page.waitForLoadState('domcontentloaded', { timeout: 30000 }).catch(() => null),
    ]);
  }
}

async function switchToNewPageOrCurrent(context, currentPage, action) {
  const newPagePromise = context.waitForEvent('page', { timeout: 6000 }).catch(() => null);
  await action();
  const newPage = await newPagePromise;
  if (newPage) {
    await newPage.waitForLoadState('domcontentloaded');
    await newPage.waitForLoadState('networkidle').catch(() => {});
    return { page: newPage, openedNewTab: true };
  }
  await waitForUiIdle(currentPage);
  return { page: currentPage, openedNewTab: false };
}

async function takeCheckpoint(page, testInfo, checkpointName, fullPage = false) {
  await waitForUiIdle(page);
  const screenshotPath = testInfo.outputPath(`${checkpointName}.png`);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(checkpointName, {
    path: screenshotPath,
    contentType: 'image/png',
  });
}

async function openLoginPageIfConfigured(page) {
  const configuredUrl = process.env.SALEADS_URL || process.env.BASE_URL || process.env.APP_URL;
  if (configuredUrl) {
    await page.goto(configuredUrl, { waitUntil: 'domcontentloaded' });
    await waitForUiIdle(page);
  } else if (page.url() === 'about:blank') {
    throw new Error(
      'Current page is about:blank. Provide SALEADS_URL (or BASE_URL/APP_URL) or start with the login page already open.'
    );
  }
}

function renderReport(resultMap, evidence) {
  const lines = ['# saleads_mi_negocio_full_test', '', '## Validation Results', ''];
  for (const step of STEP_ORDER) {
    const result = resultMap[step];
    lines.push(`- ${step}: ${result.status} - ${result.details}`);
  }
  lines.push('', '## Evidence', '');
  lines.push(`- T\u00e9rminos y Condiciones URL: ${evidence.terminosUrl || 'N/A'}`);
  lines.push(`- Pol\u00edtica de Privacidad URL: ${evidence.privacidadUrl || 'N/A'}`);
  return `${lines.join('\n')}\n`;
}

test('saleads_mi_negocio_full_test', async ({ page, context }, testInfo) => {
  const stepResults = createStepMap();
  const evidence = { terminosUrl: '', privacidadUrl: '' };
  const failures = [];
  let blockedBy = '';
  const appPage = page;

  async function runStep(stepName, action, passDetails, critical = false) {
    if (blockedBy) {
      stepResults[stepName] = { status: 'FAIL', details: `Skipped because ${blockedBy}` };
      failures.push(`${stepName}: skipped because ${blockedBy}`);
      return false;
    }
    try {
      await action();
      stepResults[stepName] = { status: 'PASS', details: passDetails };
      return true;
    } catch (error) {
      stepResults[stepName] = { status: 'FAIL', details: error.message };
      failures.push(`${stepName}: ${error.message}`);
      if (critical) {
        blockedBy = `${stepName} failed`;
      }
      return false;
    }
  }

  try {
    await runStep(
      STEP_NAMES.login,
      async () => {
        await openLoginPageIfConfigured(page);
        await waitForUiIdle(page);
        await clickGoogleLoginAndHandlePopup(page, context);
        await expect(page.locator('aside, nav')).toBeVisible({ timeout: 45000 });
        await takeCheckpoint(page, testInfo, '01_dashboard_loaded', true);
      },
      'Main app interface and sidebar are visible.',
      true
    );

    await runStep(
      STEP_NAMES.menu,
      async () => {
        await clickIfVisible(page, TXT.negocio, 6000);
        await clickByVisibleLabel(page, TXT.miNegocio);
        await expect(page.getByText(TXT.agregarNegocio)).toBeVisible();
        await expect(page.getByText(TXT.administrarNegocios)).toBeVisible();
        await takeCheckpoint(page, testInfo, '02_mi_negocio_menu_expanded');
      },
      'Mi Negocio expanded with expected submenu options.',
      true
    );

    await runStep(
      STEP_NAMES.agregarModal,
      async () => {
        await clickByVisibleLabel(page, TXT.agregarNegocio);
        const modal = page.getByRole('dialog');
        await expect(modal).toBeVisible({ timeout: 20000 });
        await expect(modal.getByText(TXT.crearNuevoNegocio)).toBeVisible();
        const nombreInputCandidates = [
          modal.getByLabel(TXT.nombreDelNegocio),
          modal.getByPlaceholder(TXT.nombreDelNegocio),
          modal.locator('input').first(),
        ];
        let nombreInput = null;
        for (const candidate of nombreInputCandidates) {
          if (await candidate.first().isVisible().catch(() => false)) {
            nombreInput = candidate.first();
            break;
          }
        }
        if (!nombreInput) {
          throw new Error('Nombre del Negocio input was not found.');
        }
        await expect(modal.getByText(TXT.tienesDosTresNegocios)).toBeVisible();
        await expect(modal.getByRole('button', { name: /cancelar/i })).toBeVisible();
        await expect(modal.getByRole('button', { name: /crear negocio/i })).toBeVisible();
        await takeCheckpoint(page, testInfo, '03_agregar_negocio_modal');
        await nombreInput.click();
        await nombreInput.fill('Negocio Prueba Automatizacion');
        await clickByVisibleLabel(modal, /cancelar/i);
      },
      'Agregar Negocio modal validated and closed.',
      true
    );

    await runStep(
      STEP_NAMES.administrarView,
      async () => {
        const administrarVisible = await page.getByText(TXT.administrarNegocios).first().isVisible().catch(() => false);
        if (!administrarVisible) {
          await clickByVisibleLabel(page, TXT.miNegocio);
        }
        await clickByVisibleLabel(page, TXT.administrarNegocios);
        await expect(page.getByText(TXT.informacionGeneral)).toBeVisible({ timeout: 30000 });
        await expect(page.getByText(TXT.detallesCuenta)).toBeVisible();
        await expect(page.getByText(TXT.tusNegocios)).toBeVisible();
        await expect(page.getByText(TXT.seccionLegal)).toBeVisible();
        await takeCheckpoint(page, testInfo, '04_administrar_negocios', true);
      },
      'Account page sections are visible.',
      true
    );

    await runStep(
      STEP_NAMES.infoGeneral,
      async () => {
        const knownEmailVisible = await page.getByText(ACCOUNT_EMAIL, { exact: false }).first().isVisible().catch(() => false);
        const emailLocator = knownEmailVisible
          ? page.getByText(ACCOUNT_EMAIL, { exact: false }).first()
          : page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first();
        await expect(emailLocator).toBeVisible();
        const emailContainer = emailLocator.locator(
          'xpath=ancestor-or-self::*[self::div or self::section or self::article][1]'
        );
        const surroundingTextRaw = (await emailContainer.innerText().catch(() => '')) || (await emailLocator.innerText());
        const surroundingText = surroundingTextRaw.replace(/\s+/g, ' ').trim();
        expect(surroundingText, 'Expected a user name near the user email').toMatch(/[A-Za-z]{2,}\s+[A-Za-z]{2,}/);
        await expect(page.getByText(TXT.businessPlan)).toBeVisible();
        await expect(page.getByRole('button', { name: TXT.cambiarPlan })).toBeVisible();
      },
      'Name/email/plan/cambiar plan are visible.'
    );

    await runStep(
      STEP_NAMES.detallesCuenta,
      async () => {
        await expect(page.getByText(TXT.cuentaCreada)).toBeVisible();
        await expect(page.getByText(TXT.estadoActivo)).toBeVisible();
        await expect(page.getByText(TXT.idiomaSeleccionado)).toBeVisible();
      },
      'Detalles de la Cuenta labels are visible.'
    );

    await runStep(
      STEP_NAMES.tusNegocios,
      async () => {
        await expect(page.getByText(TXT.tusNegocios)).toBeVisible();
        await expect(page.getByRole('button', { name: TXT.agregarNegocio })).toBeVisible();
        await expect(page.getByText(TXT.tienesDosTresNegocios)).toBeVisible();
      },
      'Business list block and limits are visible.'
    );

    await runStep(
      STEP_NAMES.terminos,
      async () => {
        const termsResult = await switchToNewPageOrCurrent(context, appPage, async () => {
          await clickByVisibleLabel(appPage, TXT.terminos);
        });
        const termsPage = termsResult.page;
        await expect(termsPage.getByText(TXT.terminos)).toBeVisible({ timeout: 30000 });
        await expect(termsPage.locator('p, article, main, section')).toContainText(/\w+/, { timeout: 30000 });
        evidence.terminosUrl = termsPage.url();
        await takeCheckpoint(termsPage, testInfo, '08_terminos_y_condiciones', true);
        if (termsResult.openedNewTab) {
          await termsPage.close();
          await appPage.bringToFront();
          await waitForUiIdle(appPage);
        } else {
          await appPage.goBack().catch(() => {});
          await waitForUiIdle(appPage);
        }
      },
      'Legal terms page validated.'
    );

    await runStep(
      STEP_NAMES.privacidad,
      async () => {
        const privacyResult = await switchToNewPageOrCurrent(context, appPage, async () => {
          await clickByVisibleLabel(appPage, TXT.privacidad);
        });
        const privacyPage = privacyResult.page;
        await expect(privacyPage.getByText(TXT.privacidad)).toBeVisible({ timeout: 30000 });
        await expect(privacyPage.locator('p, article, main, section')).toContainText(/\w+/, { timeout: 30000 });
        evidence.privacidadUrl = privacyPage.url();
        await takeCheckpoint(privacyPage, testInfo, '09_politica_de_privacidad', true);
        if (privacyResult.openedNewTab) {
          await privacyPage.close();
          await appPage.bringToFront();
          await waitForUiIdle(appPage);
        } else {
          await appPage.goBack().catch(() => {});
          await waitForUiIdle(appPage);
        }
      },
      'Privacy policy page validated.'
    );
  } finally {
    const reportContent = renderReport(stepResults, evidence);
    const reportPath = testInfo.outputPath('final-report.md');
    fs.writeFileSync(reportPath, reportContent, 'utf8');
    await testInfo.attach('final-report', {
      path: reportPath,
      contentType: 'text/markdown',
    });
  }

  expect(failures, `Validation failures:\n${failures.join('\n')}`).toEqual([]);
});
