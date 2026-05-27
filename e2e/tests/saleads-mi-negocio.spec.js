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
  'Política de Privacidad'
];

function slugify(value) {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function shortError(error) {
  return String(error?.message || error || 'Unknown error').split('\n')[0].slice(0, 240);
}

async function waitForUi(page) {
  await page.waitForLoadState('domcontentloaded', { timeout: 30000 });
  try {
    await page.waitForLoadState('networkidle', { timeout: 10000 });
  } catch (_) {
    // Some pages keep network requests open; domcontentloaded is enough here.
  }
  await page.waitForTimeout(700);
}

async function isVisible(locator, timeoutMs = 3000) {
  try {
    await locator.first().waitFor({ state: 'visible', timeout: timeoutMs });
    return true;
  } catch (_) {
    return false;
  }
}

async function findVisible(candidates, timeoutMs = 8000) {
  const eachTimeout = Math.max(1200, Math.floor(timeoutMs / Math.max(candidates.length, 1)));

  for (const locator of candidates) {
    if (await isVisible(locator, eachTimeout)) {
      return locator.first();
    }
  }

  return null;
}

async function clickAndWait(page, locator) {
  await locator.click();
  await waitForUi(page);
}

async function capture(page, screenshotDir, evidence, key, fullPage = false) {
  const screenshotName = `${Date.now()}-${slugify(key)}.png`;
  const screenshotPath = path.join(screenshotDir, screenshotName);
  await page.screenshot({ path: screenshotPath, fullPage });
  evidence.screenshots[key] = screenshotPath;
  return screenshotPath;
}

function mark(report, field, status, details) {
  report[field] = { status, details };
}

async function openLegalDocument({
  page,
  context,
  linkText,
  headingText,
  reportField,
  screenshotKey,
  screenshotDir,
  evidence,
  appPage
}) {
  const popupPromise = context.waitForEvent('page', { timeout: 9000 }).catch(() => null);
  const link = await findVisible(
    [
      page.getByRole('link', { name: new RegExp(`^${escapeRegex(linkText)}$`, 'i') }),
      page.getByText(new RegExp(`^${escapeRegex(linkText)}$`, 'i'))
    ],
    10000
  );

  if (!link) {
    throw new Error(`No se encontró el enlace "${linkText}"`);
  }

  await link.click();
  const popup = await popupPromise;
  const legalPage = popup || page;

  await waitForUi(legalPage);

  const heading = await findVisible(
    [
      legalPage.getByRole('heading', { name: new RegExp(escapeRegex(headingText), 'i') }),
      legalPage.getByText(new RegExp(escapeRegex(headingText), 'i'))
    ],
    12000
  );

  if (!heading) {
    throw new Error(`No se encontró el heading "${headingText}"`);
  }

  const legalText = await legalPage.locator('body').innerText();
  if (legalText.trim().length < 120) {
    throw new Error(`Contenido legal insuficiente para "${headingText}"`);
  }

  await capture(legalPage, screenshotDir, evidence, screenshotKey, true);
  evidence.urls[reportField] = legalPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await page.goBack({ waitUntil: 'domcontentloaded' });
    await waitForUi(page);
  }
}

test('SaleADS - flujo completo Mi Negocio con evidencia', async ({ page, context }) => {
  const runId = new Date().toISOString().replace(/[:.]/g, '-');
  const screenshotDir = path.join(process.cwd(), 'artifacts', 'saleads-mi-negocio', runId);
  fs.mkdirSync(screenshotDir, { recursive: true });

  const report = Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: 'FAIL', details: 'Not executed' }])
  );

  const evidence = {
    screenshots: {},
    urls: {}
  };

  const appPage = page;
  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL;

  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: 'domcontentloaded' });
    await waitForUi(page);
  }

  try {
    if (page.url() === 'about:blank') {
      throw new Error(
        'No hay URL de inicio. Define SALEADS_LOGIN_URL o SALEADS_URL para abrir la página de login del entorno actual.'
      );
    }

    const googleLoginButton = await findVisible(
      [
        page.getByRole('button', { name: /google|sign in|iniciar sesión/i }),
        page.getByRole('link', { name: /google|sign in|iniciar sesión/i }),
        page.getByText(/google|sign in|iniciar sesión/i)
      ],
      15000
    );

    if (!googleLoginButton) {
      throw new Error('No se encontró el botón de login con Google.');
    }

    await clickAndWait(page, googleLoginButton);

    const accountSelector = page.getByText('juanlucasbarbiergarzon@gmail.com', { exact: true });
    if (await isVisible(accountSelector, 12000)) {
      await clickAndWait(page, accountSelector);
    }

    const sidebar = await findVisible(
      [
        page.getByRole('navigation'),
        page.locator('aside'),
        page.locator('[class*="sidebar"], [id*="sidebar"]')
      ],
      20000
    );

    if (!sidebar) {
      throw new Error('No se detectó la navegación lateral después del login.');
    }

    await capture(page, screenshotDir, evidence, 'dashboard-loaded', true);
    mark(report, 'Login', 'PASS', 'Interfaz principal y sidebar visibles.');
  } catch (error) {
    mark(report, 'Login', 'FAIL', shortError(error));
  }

  try {
    const negocioSection = await findVisible(
      [
        page.getByRole('button', { name: /^negocio$/i }),
        page.getByRole('link', { name: /^negocio$/i }),
        page.getByText(/^negocio$/i)
      ],
      12000
    );

    if (!negocioSection) {
      throw new Error('No se encontró la sección "Negocio" en el menú lateral.');
    }

    await clickAndWait(page, negocioSection);

    const miNegocioOption = await findVisible(
      [
        page.getByRole('button', { name: /^mi negocio$/i }),
        page.getByRole('link', { name: /^mi negocio$/i }),
        page.getByText(/^mi negocio$/i)
      ],
      10000
    );

    if (!miNegocioOption) {
      throw new Error('No se encontró la opción "Mi Negocio".');
    }

    await clickAndWait(page, miNegocioOption);

    const agregarNegocio = await findVisible(
      [
        page.getByRole('button', { name: /^agregar negocio$/i }),
        page.getByRole('link', { name: /^agregar negocio$/i }),
        page.getByText(/^agregar negocio$/i)
      ],
      10000
    );

    const administrarNegocios = await findVisible(
      [
        page.getByRole('link', { name: /^administrar negocios$/i }),
        page.getByRole('button', { name: /^administrar negocios$/i }),
        page.getByText(/^administrar negocios$/i)
      ],
      10000
    );

    if (!agregarNegocio || !administrarNegocios) {
      throw new Error('No se visualizaron "Agregar Negocio" y "Administrar Negocios" en el submenú.');
    }

    await capture(page, screenshotDir, evidence, 'mi-negocio-expanded-menu', false);
    mark(report, 'Mi Negocio menu', 'PASS', 'Submenú expandido con opciones requeridas.');
  } catch (error) {
    mark(report, 'Mi Negocio menu', 'FAIL', shortError(error));
  }

  try {
    const agregarNegocioAction = await findVisible(
      [
        page.getByRole('button', { name: /^agregar negocio$/i }),
        page.getByRole('link', { name: /^agregar negocio$/i }),
        page.getByText(/^agregar negocio$/i)
      ],
      10000
    );

    if (!agregarNegocioAction) {
      throw new Error('No se encontró el trigger "Agregar Negocio".');
    }

    await clickAndWait(page, agregarNegocioAction);

    const modalTitle = await findVisible(
      [
        page.getByRole('heading', { name: /crear nuevo negocio/i }),
        page.getByText(/crear nuevo negocio/i)
      ],
      12000
    );

    if (!modalTitle) {
      throw new Error('No se mostró el modal "Crear Nuevo Negocio".');
    }

    const nombreInput = await findVisible(
      [
        page.getByLabel(/nombre del negocio/i),
        page.getByPlaceholder(/nombre del negocio/i),
        page.locator('label:has-text("Nombre del Negocio")').locator('xpath=following::input[1]'),
        page.getByRole('textbox')
      ],
      10000
    );

    if (!nombreInput) {
      throw new Error('No se encontró el input "Nombre del Negocio".');
    }

    const quotaText = await findVisible([page.getByText(/tienes 2 de 3 negocios/i)], 5000);
    const cancelButton = await findVisible([page.getByRole('button', { name: /^cancelar$/i })], 5000);
    const createButton = await findVisible([page.getByRole('button', { name: /^crear negocio$/i })], 5000);

    if (!quotaText || !cancelButton || !createButton) {
      throw new Error('Faltan validaciones del modal (cuota o botones).');
    }

    await capture(page, screenshotDir, evidence, 'agregar-negocio-modal', false);

    await nombreInput.fill('Negocio Prueba Automatización');
    await clickAndWait(page, cancelButton);

    mark(report, 'Agregar Negocio modal', 'PASS', 'Modal validado, campo completado y cierre con cancelar.');
  } catch (error) {
    mark(report, 'Agregar Negocio modal', 'FAIL', shortError(error));
  }

  try {
    const administrarNegocios = await findVisible(
      [
        page.getByRole('link', { name: /^administrar negocios$/i }),
        page.getByRole('button', { name: /^administrar negocios$/i }),
        page.getByText(/^administrar negocios$/i)
      ],
      6000
    );

    if (!administrarNegocios) {
      const miNegocioOption = await findVisible(
        [
          page.getByRole('button', { name: /^mi negocio$/i }),
          page.getByRole('link', { name: /^mi negocio$/i }),
          page.getByText(/^mi negocio$/i)
        ],
        10000
      );

      if (!miNegocioOption) {
        throw new Error('No se pudo expandir "Mi Negocio" para llegar a "Administrar Negocios".');
      }

      await clickAndWait(page, miNegocioOption);
    }

    const administrarNegociosFinal = await findVisible(
      [
        page.getByRole('link', { name: /^administrar negocios$/i }),
        page.getByRole('button', { name: /^administrar negocios$/i }),
        page.getByText(/^administrar negocios$/i)
      ],
      10000
    );

    if (!administrarNegociosFinal) {
      throw new Error('No se encontró la opción "Administrar Negocios".');
    }

    await clickAndWait(page, administrarNegociosFinal);

    const requiredSections = [
      /información general/i,
      /detalles de la cuenta/i,
      /tus negocios/i,
      /sección legal/i
    ];

    for (const sectionRegex of requiredSections) {
      const sectionTitle = await findVisible(
        [page.getByRole('heading', { name: sectionRegex }), page.getByText(sectionRegex)],
        12000
      );

      if (!sectionTitle) {
        throw new Error(`No se encontró la sección "${sectionRegex}" en Administrar Negocios.`);
      }
    }

    await capture(page, screenshotDir, evidence, 'administrar-negocios-account-page-full', true);
    mark(report, 'Administrar Negocios view', 'PASS', 'Secciones de cuenta visibles.');
  } catch (error) {
    mark(report, 'Administrar Negocios view', 'FAIL', shortError(error));
  }

  try {
    const bodyText = await page.locator('body').innerText();
    const emailRegex = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;
    if (!emailRegex.test(bodyText)) {
      throw new Error('No se detectó un email visible en Información General.');
    }

    const expectedName = process.env.SALEADS_EXPECTED_USER_NAME;
    if (expectedName) {
      const expectedNameRegex = new RegExp(escapeRegex(expectedName), 'i');
      if (!expectedNameRegex.test(bodyText)) {
        throw new Error(`No se encontró el nombre esperado: ${expectedName}`);
      }
    } else {
      const textWithoutEmails = bodyText.replace(emailRegex, ' ');
      const genericNameRegex = /\b[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\b/;
      if (!genericNameRegex.test(textWithoutEmails)) {
        throw new Error('No se pudo validar un nombre visible (definir SALEADS_EXPECTED_USER_NAME para validación estricta).');
      }
    }

    const planText = await findVisible([page.getByText(/business plan/i)], 10000);
    const changePlanButton = await findVisible([page.getByRole('button', { name: /cambiar plan/i })], 10000);

    if (!planText || !changePlanButton) {
      throw new Error('No se encontraron "BUSINESS PLAN" y/o el botón "Cambiar Plan".');
    }

    mark(report, 'Información General', 'PASS', 'Nombre, email, plan y botón de cambio validados.');
  } catch (error) {
    mark(report, 'Información General', 'FAIL', shortError(error));
  }

  try {
    const requiredAccountDetails = [/cuenta creada/i, /estado activo/i, /idioma seleccionado/i];

    for (const detailRegex of requiredAccountDetails) {
      const detail = await findVisible([page.getByText(detailRegex)], 10000);
      if (!detail) {
        throw new Error(`No se encontró el texto requerido: ${detailRegex}`);
      }
    }

    mark(report, 'Detalles de la Cuenta', 'PASS', 'Validaciones de detalles de cuenta completas.');
  } catch (error) {
    mark(report, 'Detalles de la Cuenta', 'FAIL', shortError(error));
  }

  try {
    const tusNegociosSection = await findVisible(
      [page.getByRole('heading', { name: /tus negocios/i }), page.getByText(/tus negocios/i)],
      10000
    );
    const addBusinessButton = await findVisible([page.getByRole('button', { name: /^agregar negocio$/i })], 10000);
    const businessQuota = await findVisible([page.getByText(/tienes 2 de 3 negocios/i)], 10000);

    const businessListCandidates = page.locator(
      '[role="list"], [role="table"], table, ul, ol, [data-testid*="business"], [class*="business"]'
    );
    const listLikeVisible = (await businessListCandidates.count()) > 0;

    if (!tusNegociosSection || !addBusinessButton || !businessQuota || !listLikeVisible) {
      throw new Error('No se validó completamente la sección "Tus Negocios".');
    }

    mark(report, 'Tus Negocios', 'PASS', 'Lista, botón y cuota validados.');
  } catch (error) {
    mark(report, 'Tus Negocios', 'FAIL', shortError(error));
  }

  try {
    await openLegalDocument({
      page,
      context,
      linkText: 'Términos y Condiciones',
      headingText: 'Términos y Condiciones',
      reportField: 'Términos y Condiciones',
      screenshotKey: 'terminos-y-condiciones-page',
      screenshotDir,
      evidence,
      appPage
    });
    mark(report, 'Términos y Condiciones', 'PASS', 'Documento legal validado y URL capturada.');
  } catch (error) {
    mark(report, 'Términos y Condiciones', 'FAIL', shortError(error));
  }

  try {
    await openLegalDocument({
      page,
      context,
      linkText: 'Política de Privacidad',
      headingText: 'Política de Privacidad',
      reportField: 'Política de Privacidad',
      screenshotKey: 'politica-de-privacidad-page',
      screenshotDir,
      evidence,
      appPage
    });
    mark(report, 'Política de Privacidad', 'PASS', 'Documento legal validado y URL capturada.');
  } catch (error) {
    mark(report, 'Política de Privacidad', 'FAIL', shortError(error));
  }

  const finalReport = {
    generatedAt: new Date().toISOString(),
    currentUrl: page.url(),
    report,
    evidence
  };

  const reportPath = path.join(screenshotDir, 'final-report.json');
  fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), 'utf-8');
  console.log(`Reporte final guardado en: ${reportPath}`);
  console.table(
    Object.entries(report).map(([step, value]) => ({
      step,
      status: value.status,
      details: value.details
    }))
  );

  const failed = Object.entries(report).filter(([, value]) => value.status !== 'PASS').map(([field]) => field);
  expect(failed, `Fallaron validaciones: ${failed.join(', ') || 'ninguna'}`).toHaveLength(0);
});
