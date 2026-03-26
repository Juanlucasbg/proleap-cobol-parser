const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const TEST_NAME = 'saleads_mi_negocio_full_test';
const GOOGLE_ACCOUNT = 'juanlucasbarbiergarzon@gmail.com';
const STEP_KEYS = [
  'Login',
  'Mi Negocio menu',
  'Agregar Negocio modal',
  'Administrar Negocios view',
  'Informaci\u00f3n General',
  'Detalles de la Cuenta',
  'Tus Negocios',
  'T\u00e9rminos y Condiciones',
  'Pol\u00edtica de Privacidad'
];

const TEXT = {
  LOGIN_WITH_GOOGLE: /sign in with google|iniciar sesi[o\u00f3]n con google|continuar con google|login with google/i,
  NEGOCIO: 'Negocio',
  MI_NEGOCIO: 'Mi Negocio',
  AGREGAR_NEGOCIO: 'Agregar Negocio',
  ADMINISTRAR_NEGOCIOS: 'Administrar Negocios',
  CREAR_NUEVO_NEGOCIO: 'Crear Nuevo Negocio',
  NOMBRE_NEGOCIO: 'Nombre del Negocio',
  CUPO_NEGOCIOS: 'Tienes 2 de 3 negocios',
  CANCELAR: 'Cancelar',
  CREAR_NEGOCIO: 'Crear Negocio',
  INFORMACION_GENERAL: 'Informaci\u00f3n General',
  DETALLES_CUENTA: 'Detalles de la Cuenta',
  TUS_NEGOCIOS: 'Tus Negocios',
  SECCION_LEGAL: 'Secci\u00f3n Legal',
  BUSINESS_PLAN: 'BUSINESS PLAN',
  CAMBIAR_PLAN: 'Cambiar Plan',
  CUENTA_CREADA: 'Cuenta creada',
  ESTADO_ACTIVO: 'Estado activo',
  IDIOMA_SELECCIONADO: 'Idioma seleccionado',
  TERMINOS: 'T\u00e9rminos y Condiciones',
  POLITICA: 'Pol\u00edtica de Privacidad'
};

const rootDir = __dirname;
const artifactsDir = path.join(rootDir, 'artifacts');
const timestamp = new Date().toISOString().replace(/[:.]/g, '-');

if (!fs.existsSync(artifactsDir)) {
  fs.mkdirSync(artifactsDir, { recursive: true });
}

function emptyResult() {
  return {
    status: 'SKIPPED',
    details: [],
    evidence: {}
  };
}

function log(message) {
  process.stdout.write(`${message}\n`);
}

function normalizeText(value) {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase();
}

async function waitForUi(page) {
  await Promise.allSettled([
    page.waitForLoadState('domcontentloaded', { timeout: 15000 }),
    page.waitForLoadState('networkidle', { timeout: 15000 })
  ]);
  await page.waitForTimeout(700);
}

async function isVisible(locator, timeoutMs = 3000) {
  try {
    await locator.first().waitFor({ state: 'visible', timeout: timeoutMs });
    return true;
  } catch {
    return false;
  }
}

async function firstVisible(candidates, timeoutMs = 10000) {
  const start = Date.now();

  while (Date.now() - start < timeoutMs) {
    for (const locator of candidates) {
      if (await isVisible(locator, 600)) {
        return locator.first();
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  return null;
}

async function clickAndWait(page, locator) {
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click({ timeout: 10000 });
  await waitForUi(page);
}

async function screenshot(page, name, fullPage = false) {
  const filePath = path.join(artifactsDir, `${timestamp}_${name}.png`);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function getBodyText(page) {
  return page.locator('body').innerText().catch(() => '');
}

function inferNameNearEmail(bodyText) {
  const lines = bodyText
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean);

  const emailRegex = /^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$/i;
  const likelyNameRegex = /^[A-Za-z\u00c0-\u017f]+(?:[\s'-][A-Za-z\u00c0-\u017f]+)+$/;

  for (let i = 0; i < lines.length; i += 1) {
    if (!emailRegex.test(lines[i])) {
      continue;
    }

    for (let j = i - 1; j >= Math.max(0, i - 3); j -= 1) {
      if (likelyNameRegex.test(lines[j])) {
        return lines[j];
      }
    }
  }

  return null;
}

async function openLegalLink(page, label, headingRegex, screenshotName) {
  const result = {
    passed: false,
    details: [],
    url: null,
    screenshotPath: null
  };

  const linkLocator = await firstVisible(
    [
      page.getByRole('link', { name: label }),
      page.getByRole('button', { name: label }),
      page.getByText(label, { exact: true })
    ],
    10000
  );

  if (!linkLocator) {
    result.details.push(`No se encontr\u00f3 el acceso legal: ${label}.`);
    return result;
  }

  const appPage = page;
  const appUrlBefore = appPage.url();
  const popupPromise = appPage.context().waitForEvent('page', { timeout: 5000 }).catch(() => null);

  await clickAndWait(appPage, linkLocator);
  let targetPage = await popupPromise;

  if (targetPage) {
    await targetPage.waitForLoadState('domcontentloaded', { timeout: 20000 }).catch(() => {});
    await targetPage.waitForTimeout(800);
  } else {
    targetPage = appPage;
  }

  const headingVisible = await firstVisible(
    [
      targetPage.getByRole('heading', { name: headingRegex }),
      targetPage.getByText(headingRegex)
    ],
    12000
  );
  const bodyText = await getBodyText(targetPage);
  const legalTextVisible = bodyText.trim().length > 120;

  result.url = targetPage.url();
  result.screenshotPath = await screenshot(targetPage, screenshotName, true).catch(() => null);
  result.passed = Boolean(headingVisible) && legalTextVisible;
  result.details.push(`Heading visible: ${Boolean(headingVisible)}.`);
  result.details.push(`Legal content visible: ${legalTextVisible}.`);
  result.details.push(`Final URL: ${result.url}.`);

  if (targetPage !== appPage) {
    await targetPage.close().catch(() => {});
    await appPage.bringToFront().catch(() => {});
    await waitForUi(appPage);
  } else if (appPage.url() !== appUrlBefore) {
    await appPage.goBack({ waitUntil: 'domcontentloaded' }).catch(async () => {
      if (appUrlBefore && appUrlBefore !== 'about:blank') {
        await appPage.goto(appUrlBefore, { waitUntil: 'domcontentloaded' });
      }
    });
    await waitForUi(appPage);
  }

  return result;
}

async function main() {
  const report = {
    name: TEST_NAME,
    generatedAt: new Date().toISOString(),
    environment: {
      loginUrl: process.env.SALEADS_LOGIN_URL || null,
      cdpUrlProvided: Boolean(process.env.SALEADS_CDP_URL),
      headless: process.env.HEADLESS !== 'false'
    },
    results: Object.fromEntries(STEP_KEYS.map((step) => [step, emptyResult()])),
    evidence: {
      artifactsDirectory: artifactsDir
    }
  };

  let browser;
  let context;
  let page;

  try {
    const storageStatePath = process.env.SALEADS_STORAGE_STATE_PATH;
    const launchOptions = { headless: process.env.HEADLESS !== 'false' };

    if (process.env.SALEADS_CDP_URL) {
      browser = await chromium.connectOverCDP(process.env.SALEADS_CDP_URL);
      context = browser.contexts()[0] || (await browser.newContext());
      page = context.pages()[0] || (await context.newPage());
      report.results.Login.details.push('Connected to existing browser via CDP.');
    } else {
      browser = await chromium.launch(launchOptions);
      context = await browser.newContext(
        storageStatePath && fs.existsSync(storageStatePath)
          ? { storageState: storageStatePath }
          : undefined
      );
      page = await context.newPage();
    }

    if (process.env.SALEADS_LOGIN_URL) {
      await page.goto(process.env.SALEADS_LOGIN_URL, { waitUntil: 'domcontentloaded' });
      await waitForUi(page);
    } else {
      const currentUrl = page.url();
      report.results.Login.details.push(
        `SALEADS_LOGIN_URL not provided. Current page URL: ${currentUrl}.`
      );
    }

    const currentUrl = page.url();
    if (!process.env.SALEADS_LOGIN_URL && currentUrl === 'about:blank') {
      report.results.Login.status = 'FAIL';
      report.results.Login.details.push(
        'No login page available. Provide SALEADS_LOGIN_URL, or attach with SALEADS_CDP_URL to an already-open SaleADS session.'
      );
    } else {
      const alreadySidebar = await firstVisible(
        [
          page.locator('aside:has-text("Negocio")'),
          page.getByRole('navigation'),
          page.locator('aside'),
          page.locator('nav')
        ],
        7000
      );
      const alreadyNegocio = await isVisible(page.getByText(TEXT.NEGOCIO, { exact: true }), 2000);
      const alreadyMiNegocio = await isVisible(
        page.getByText(TEXT.MI_NEGOCIO, { exact: true }),
        2000
      );

      if (alreadySidebar && (alreadyNegocio || alreadyMiNegocio)) {
        report.results.Login.status = 'PASS';
        report.results.Login.details.push(
          'Session was already authenticated; main application and sidebar are visible.'
        );
        report.results.Login.evidence.dashboardScreenshot = await screenshot(
          page,
          '01_dashboard_loaded',
          true
        );
      } else {
      const loginButton = await firstVisible(
        [
          page.getByRole('button', { name: /google/i }),
          page.getByText(TEXT.LOGIN_WITH_GOOGLE),
          page.getByRole('link', { name: /google/i })
        ],
        15000
      );

      if (!loginButton) {
        const sidebarAfterWait = await firstVisible(
          [
            page.locator('aside:has-text("Negocio")'),
            page.getByRole('navigation'),
            page.locator('aside'),
            page.locator('nav')
          ],
          20000
        );
        const negocioAfterWait = await isVisible(page.getByText(TEXT.NEGOCIO, { exact: true }), 3000);
        const miNegocioAfterWait = await isVisible(
          page.getByText(TEXT.MI_NEGOCIO, { exact: true }),
          3000
        );

        if (sidebarAfterWait && (negocioAfterWait || miNegocioAfterWait)) {
          report.results.Login.status = 'PASS';
          report.results.Login.details.push(
            'Google login button not found, but application interface became visible.'
          );
          report.results.Login.evidence.dashboardScreenshot = await screenshot(
            page,
            '01_dashboard_loaded',
            true
          );
        } else {
          report.results.Login.status = 'FAIL';
          report.results.Login.details.push('Google login action was not found on the current page.');
        }
      } else {
        await clickAndWait(page, loginButton);
        report.results.Login.details.push('Clicked Google login action.');

        const accountSelector = await firstVisible(
          [
            page.getByText(GOOGLE_ACCOUNT, { exact: true }),
            page.getByRole('button', { name: GOOGLE_ACCOUNT }),
            page.getByRole('link', { name: GOOGLE_ACCOUNT })
          ],
          9000
        );

        if (accountSelector) {
          await clickAndWait(page, accountSelector);
          report.results.Login.details.push(`Selected Google account ${GOOGLE_ACCOUNT}.`);
        } else {
          report.results.Login.details.push('Google account chooser was not shown (continuing).');
        }

        const sidebar = await firstVisible(
          [
            page.locator('aside:has-text("Negocio")'),
            page.getByRole('navigation'),
            page.locator('aside'),
            page.locator('nav')
          ],
          40000
        );
        const negocioVisible = await isVisible(page.getByText(TEXT.NEGOCIO, { exact: true }), 5000);
        const miNegocioVisible = await isVisible(
          page.getByText(TEXT.MI_NEGOCIO, { exact: true }),
          5000
        );

        if (sidebar && (negocioVisible || miNegocioVisible)) {
          report.results.Login.status = 'PASS';
          report.results.Login.details.push('Main application and left sidebar are visible.');
          report.results.Login.evidence.dashboardScreenshot = await screenshot(
            page,
            '01_dashboard_loaded',
            true
          );
        } else {
          report.results.Login.status = 'FAIL';
          report.results.Login.details.push(
            'Could not verify main app interface and sidebar after login.'
          );
        }
      }
      }
    }

    if (report.results.Login.status === 'PASS') {
      const negocioOption = await firstVisible(
        [
          page.getByText(TEXT.NEGOCIO, { exact: true }),
          page.getByRole('button', { name: TEXT.NEGOCIO }),
          page.getByRole('link', { name: TEXT.NEGOCIO })
        ],
        10000
      );
      if (negocioOption) {
        await clickAndWait(page, negocioOption);
        report.results['Mi Negocio menu'].details.push('Clicked Negocio section.');
      }

      const miNegocioOption = await firstVisible(
        [
          page.getByText(TEXT.MI_NEGOCIO, { exact: true }),
          page.getByRole('button', { name: TEXT.MI_NEGOCIO }),
          page.getByRole('link', { name: TEXT.MI_NEGOCIO })
        ],
        12000
      );

      if (!miNegocioOption) {
        report.results['Mi Negocio menu'].status = 'FAIL';
        report.results['Mi Negocio menu'].details.push('Mi Negocio option was not found.');
      } else {
        await clickAndWait(page, miNegocioOption);
        const agregarVisible = await isVisible(
          page.getByText(TEXT.AGREGAR_NEGOCIO, { exact: true }),
          8000
        );
        const administrarVisible = await isVisible(
          page.getByText(TEXT.ADMINISTRAR_NEGOCIOS, { exact: true }),
          8000
        );

        if (agregarVisible && administrarVisible) {
          report.results['Mi Negocio menu'].status = 'PASS';
          report.results['Mi Negocio menu'].details.push(
            'Mi Negocio submenu expanded with expected options.'
          );
          report.results['Mi Negocio menu'].evidence.expandedMenuScreenshot = await screenshot(
            page,
            '02_mi_negocio_menu_expanded'
          );
        } else {
          report.results['Mi Negocio menu'].status = 'FAIL';
          report.results['Mi Negocio menu'].details.push(
            `Submenu validation failed. Agregar visible: ${agregarVisible}, Administrar visible: ${administrarVisible}.`
          );
        }
      }
    } else {
      report.results['Mi Negocio menu'].status = 'SKIPPED';
      report.results['Mi Negocio menu'].details.push('Skipped because login did not pass.');
    }

    if (report.results['Mi Negocio menu'].status === 'PASS') {
      const agregarMenuOption = await firstVisible(
        [
          page.getByText(TEXT.AGREGAR_NEGOCIO, { exact: true }),
          page.getByRole('button', { name: TEXT.AGREGAR_NEGOCIO }),
          page.getByRole('link', { name: TEXT.AGREGAR_NEGOCIO })
        ],
        10000
      );

      if (!agregarMenuOption) {
        report.results['Agregar Negocio modal'].status = 'FAIL';
        report.results['Agregar Negocio modal'].details.push(
          'Could not find Agregar Negocio entry point.'
        );
      } else {
        await clickAndWait(page, agregarMenuOption);

        const modalTitle = await firstVisible(
          [
            page.getByRole('heading', { name: TEXT.CREAR_NUEVO_NEGOCIO }),
            page.getByText(TEXT.CREAR_NUEVO_NEGOCIO, { exact: true })
          ],
          12000
        );
        const nombreNegocioFieldVisible =
          (await isVisible(page.getByLabel(TEXT.NOMBRE_NEGOCIO), 3000)) ||
          (await isVisible(page.getByPlaceholder(TEXT.NOMBRE_NEGOCIO), 3000)) ||
          (await isVisible(page.getByText(TEXT.NOMBRE_NEGOCIO, { exact: true }), 3000));
        const cupoVisible = await isVisible(page.getByText(TEXT.CUPO_NEGOCIOS, { exact: true }), 3000);
        const cancelarVisible = await isVisible(page.getByRole('button', { name: TEXT.CANCELAR }), 3000);
        const crearVisible = await isVisible(page.getByRole('button', { name: TEXT.CREAR_NEGOCIO }), 3000);

        report.results['Agregar Negocio modal'].evidence.modalScreenshot = await screenshot(
          page,
          '03_agregar_negocio_modal'
        ).catch(() => null);

        if (modalTitle && nombreNegocioFieldVisible && cupoVisible && cancelarVisible && crearVisible) {
          report.results['Agregar Negocio modal'].status = 'PASS';
          report.results['Agregar Negocio modal'].details.push(
            'Agregar Negocio modal validations passed.'
          );

          const inputField = await firstVisible(
            [page.getByLabel(TEXT.NOMBRE_NEGOCIO), page.getByPlaceholder(TEXT.NOMBRE_NEGOCIO)],
            4000
          );
          if (inputField) {
            await inputField.fill('Negocio Prueba Automatizacion').catch(() => {});
          }
          const cancelButton = await firstVisible(
            [page.getByRole('button', { name: TEXT.CANCELAR }), page.getByText(TEXT.CANCELAR, { exact: true })],
            4000
          );
          if (cancelButton) {
            await clickAndWait(page, cancelButton);
          }
        } else {
          report.results['Agregar Negocio modal'].status = 'FAIL';
          report.results['Agregar Negocio modal'].details.push(
            `Modal checks - title:${Boolean(modalTitle)} nombre:${nombreNegocioFieldVisible} cupo:${cupoVisible} cancelar:${cancelarVisible} crear:${crearVisible}.`
          );
        }
      }
    } else {
      report.results['Agregar Negocio modal'].status = 'SKIPPED';
      report.results['Agregar Negocio modal'].details.push(
        'Skipped because Mi Negocio menu validations did not pass.'
      );
    }

    if (report.results['Mi Negocio menu'].status === 'PASS') {
      const miNegocioOption = await firstVisible(
        [
          page.getByText(TEXT.MI_NEGOCIO, { exact: true }),
          page.getByRole('button', { name: TEXT.MI_NEGOCIO }),
          page.getByRole('link', { name: TEXT.MI_NEGOCIO })
        ],
        6000
      );
      if (miNegocioOption) {
        await clickAndWait(page, miNegocioOption);
      }

      const administrarOption = await firstVisible(
        [
          page.getByText(TEXT.ADMINISTRAR_NEGOCIOS, { exact: true }),
          page.getByRole('button', { name: TEXT.ADMINISTRAR_NEGOCIOS }),
          page.getByRole('link', { name: TEXT.ADMINISTRAR_NEGOCIOS })
        ],
        10000
      );

      if (!administrarOption) {
        report.results['Administrar Negocios view'].status = 'FAIL';
        report.results['Administrar Negocios view'].details.push(
          'Administrar Negocios option not found.'
        );
      } else {
        await clickAndWait(page, administrarOption);
        const infoGeneralVisible = await isVisible(
          page.getByText(TEXT.INFORMACION_GENERAL, { exact: true }),
          12000
        );
        const detallesCuentaVisible = await isVisible(
          page.getByText(TEXT.DETALLES_CUENTA, { exact: true }),
          12000
        );
        const tusNegociosVisible = await isVisible(
          page.getByText(TEXT.TUS_NEGOCIOS, { exact: true }),
          12000
        );
        const seccionLegalVisible = await isVisible(
          page.getByText(TEXT.SECCION_LEGAL, { exact: true }),
          12000
        );

        if (infoGeneralVisible && detallesCuentaVisible && tusNegociosVisible && seccionLegalVisible) {
          report.results['Administrar Negocios view'].status = 'PASS';
          report.results['Administrar Negocios view'].details.push(
            'All expected account sections are visible.'
          );
          report.results['Administrar Negocios view'].evidence.accountPageScreenshot = await screenshot(
            page,
            '04_administrar_negocios_account_page',
            true
          );
        } else {
          report.results['Administrar Negocios view'].status = 'FAIL';
          report.results['Administrar Negocios view'].details.push(
            `Section checks failed. Informacion:${infoGeneralVisible} Detalles:${detallesCuentaVisible} TusNegocios:${tusNegociosVisible} Legal:${seccionLegalVisible}.`
          );
        }
      }
    } else {
      report.results['Administrar Negocios view'].status = 'SKIPPED';
      report.results['Administrar Negocios view'].details.push(
        'Skipped because Mi Negocio menu validations did not pass.'
      );
    }

    if (report.results['Administrar Negocios view'].status === 'PASS') {
      const bodyText = await getBodyText(page);
      const normalizedBody = normalizeText(bodyText);
      const emailMatch = bodyText.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
      const inferredName = inferNameNearEmail(bodyText);
      const businessPlanVisible = normalizedBody.includes(normalizeText(TEXT.BUSINESS_PLAN));
      const cambiarPlanVisible = await isVisible(
        page.getByRole('button', { name: TEXT.CAMBIAR_PLAN }),
        5000
      );

      if (Boolean(inferredName) && Boolean(emailMatch) && businessPlanVisible && cambiarPlanVisible) {
        report.results['Informaci\u00f3n General'].status = 'PASS';
        report.results['Informaci\u00f3n General'].details.push(`Detected user name: ${inferredName}.`);
        report.results['Informaci\u00f3n General'].details.push(`Detected user email: ${emailMatch[0]}.`);
      } else {
        report.results['Informaci\u00f3n General'].status = 'FAIL';
        report.results['Informaci\u00f3n General'].details.push(
          `Checks - userName:${Boolean(inferredName)} userEmail:${Boolean(emailMatch)} businessPlan:${businessPlanVisible} cambiarPlan:${cambiarPlanVisible}.`
        );
      }
    } else {
      report.results['Informaci\u00f3n General'].status = 'SKIPPED';
      report.results['Informaci\u00f3n General'].details.push(
        'Skipped because Administrar Negocios view did not pass.'
      );
    }

    if (report.results['Administrar Negocios view'].status === 'PASS') {
      const cuentaCreadaVisible = await isVisible(page.getByText(TEXT.CUENTA_CREADA, { exact: true }), 5000);
      const estadoActivoVisible = await isVisible(page.getByText(TEXT.ESTADO_ACTIVO, { exact: true }), 5000);
      const idiomaVisible = await isVisible(
        page.getByText(TEXT.IDIOMA_SELECCIONADO, { exact: true }),
        5000
      );

      if (cuentaCreadaVisible && estadoActivoVisible && idiomaVisible) {
        report.results['Detalles de la Cuenta'].status = 'PASS';
        report.results['Detalles de la Cuenta'].details.push('Detalles de la Cuenta validated.');
      } else {
        report.results['Detalles de la Cuenta'].status = 'FAIL';
        report.results['Detalles de la Cuenta'].details.push(
          `Checks - cuentaCreada:${cuentaCreadaVisible} estadoActivo:${estadoActivoVisible} idioma:${idiomaVisible}.`
        );
      }
    } else {
      report.results['Detalles de la Cuenta'].status = 'SKIPPED';
      report.results['Detalles de la Cuenta'].details.push(
        'Skipped because Administrar Negocios view did not pass.'
      );
    }

    if (report.results['Administrar Negocios view'].status === 'PASS') {
      const tusNegociosHeaderVisible = await isVisible(
        page.getByText(TEXT.TUS_NEGOCIOS, { exact: true }),
        5000
      );
      const agregarButtonVisible = await isVisible(
        page.getByRole('button', { name: TEXT.AGREGAR_NEGOCIO }),
        5000
      );
      const cupoVisible = await isVisible(page.getByText(TEXT.CUPO_NEGOCIOS, { exact: true }), 5000);
      const bodyText = await getBodyText(page);
      const normalizedBody = normalizeText(bodyText);
      const negociosMentionCount = (normalizedBody.match(/negocio/g) || []).length;
      const businessListVisible = tusNegociosHeaderVisible && negociosMentionCount >= 2;

      if (businessListVisible && agregarButtonVisible && cupoVisible) {
        report.results['Tus Negocios'].status = 'PASS';
        report.results['Tus Negocios'].details.push('Tus Negocios validated.');
      } else {
        report.results['Tus Negocios'].status = 'FAIL';
        report.results['Tus Negocios'].details.push(
          `Checks - businessList:${businessListVisible} agregarButton:${agregarButtonVisible} cupo:${cupoVisible}.`
        );
      }
    } else {
      report.results['Tus Negocios'].status = 'SKIPPED';
      report.results['Tus Negocios'].details.push(
        'Skipped because Administrar Negocios view did not pass.'
      );
    }

    if (report.results['Administrar Negocios view'].status === 'PASS') {
      const legalTerms = await openLegalLink(
        page,
        TEXT.TERMINOS,
        /t[e\u00e9]rminos y condiciones/i,
        '05_terminos_y_condiciones'
      );
      report.results['T\u00e9rminos y Condiciones'].status = legalTerms.passed ? 'PASS' : 'FAIL';
      report.results['T\u00e9rminos y Condiciones'].details.push(...legalTerms.details);
      report.results['T\u00e9rminos y Condiciones'].evidence.screenshot = legalTerms.screenshotPath;
      report.results['T\u00e9rminos y Condiciones'].evidence.finalUrl = legalTerms.url;
    } else {
      report.results['T\u00e9rminos y Condiciones'].status = 'SKIPPED';
      report.results['T\u00e9rminos y Condiciones'].details.push(
        'Skipped because Administrar Negocios view did not pass.'
      );
    }

    if (report.results['Administrar Negocios view'].status === 'PASS') {
      const legalPrivacy = await openLegalLink(
        page,
        TEXT.POLITICA,
        /pol[i\u00ed]tica de privacidad/i,
        '06_politica_de_privacidad'
      );
      report.results['Pol\u00edtica de Privacidad'].status = legalPrivacy.passed ? 'PASS' : 'FAIL';
      report.results['Pol\u00edtica de Privacidad'].details.push(...legalPrivacy.details);
      report.results['Pol\u00edtica de Privacidad'].evidence.screenshot = legalPrivacy.screenshotPath;
      report.results['Pol\u00edtica de Privacidad'].evidence.finalUrl = legalPrivacy.url;
    } else {
      report.results['Pol\u00edtica de Privacidad'].status = 'SKIPPED';
      report.results['Pol\u00edtica de Privacidad'].details.push(
        'Skipped because Administrar Negocios view did not pass.'
      );
    }

    report.evidence.finalAppUrl = page.url();
  } catch (error) {
    const message = error && error.stack ? error.stack : String(error);
    report.runtimeError = message;
    STEP_KEYS.forEach((key) => {
      if (report.results[key].status === 'SKIPPED') {
        report.results[key].status = 'FAIL';
        report.results[key].details.push('Test aborted by runtime error.');
      }
    });
  } finally {
    if (browser) {
      await browser.close().catch(() => {});
    }
  }

  const statusCounters = STEP_KEYS.reduce(
    (accumulator, key) => {
      const status = report.results[key].status;
      accumulator[status] = (accumulator[status] || 0) + 1;
      return accumulator;
    },
    { PASS: 0, FAIL: 0, SKIPPED: 0 }
  );

  report.summary = {
    pass: statusCounters.PASS || 0,
    fail: statusCounters.FAIL || 0,
    skipped: statusCounters.SKIPPED || 0
  };

  const jsonPath = path.join(artifactsDir, `${timestamp}_report.json`);
  fs.writeFileSync(jsonPath, JSON.stringify(report, null, 2), 'utf8');

  const finalLines = [
    `Test: ${TEST_NAME}`,
    `Report: ${jsonPath}`,
    ...STEP_KEYS.map((key) => `- ${key}: ${report.results[key].status}`),
    `Summary: PASS=${report.summary.pass} FAIL=${report.summary.fail} SKIPPED=${report.summary.skipped}`
  ];

  log(finalLines.join('\n'));
  process.exitCode = report.summary.fail > 0 ? 1 : 0;
}

main();
