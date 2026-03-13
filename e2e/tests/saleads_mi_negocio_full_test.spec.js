const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');

const STEP_KEYS = [
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

const GOOGLE_ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL || 'juanlucasbarbiergarzon@gmail.com';

async function waitForUi(pageLike) {
  await pageLike.waitForLoadState('domcontentloaded', { timeout: 15_000 }).catch(() => {});
  await pageLike.waitForLoadState('networkidle', { timeout: 12_000 }).catch(() => {});
  await pageLike.waitForTimeout(400);
}

async function pickVisible(locators, timeout = 5_000) {
  for (const locator of locators) {
    const candidate = locator.first();
    try {
      await candidate.waitFor({ state: 'visible', timeout });
      return candidate;
    } catch (error) {
      // Try next locator candidate.
    }
  }

  return null;
}

async function clickAndSettle(locator, pageLike) {
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click();
  await waitForUi(pageLike);
}

test('saleads_mi_negocio_full_test', async ({ page, context }, testInfo) => {
  test.setTimeout(8 * 60 * 1000);
  const outDir = path.resolve(process.cwd(), 'test-results', 'saleads_mi_negocio_full_test');
  fs.mkdirSync(outDir, { recursive: true });

  const evidence = {};
  const results = Object.fromEntries(
    STEP_KEYS.map((step) => [step, { status: 'FAIL', details: 'Step not executed' }]),
  );

  const saveScreenshot = async (fileName, pageLike = page, fullPage = false) => {
    const filePath = path.join(outDir, `${fileName}.png`);
    await pageLike.screenshot({ path: filePath, fullPage });
    return filePath;
  };

  const runStep = async (name, fn) => {
    try {
      await fn();
      results[name] = { status: 'PASS', details: '' };
    } catch (error) {
      results[name] = { status: 'FAIL', details: error.message };
      await saveScreenshot(`${name.toLowerCase().replace(/\s+/g, '_')}_failure`).catch(() => {});
    }
  };

  const runBlockedStep = async (name, fn) => {
    if (results.Login.status === 'FAIL') {
      results[name] = {
        status: 'FAIL',
        details: 'Blocked: Login step failed, downstream workflow could not be executed.',
      };
      return;
    }

    await runStep(name, fn);
  };

  const getLegalLink = async (labelRegex) =>
    pickVisible(
      [
        page.getByRole('link', { name: labelRegex }),
        page.getByRole('button', { name: labelRegex }),
        page.getByText(labelRegex),
      ],
      10_000,
    );

  const openAndValidateLegalPage = async ({ linkLabel, headingRegex, screenshotName, evidenceKey }) => {
    const legalLink = await getLegalLink(linkLabel);
    expect(legalLink, `Could not find legal link ${linkLabel}`).not.toBeNull();

    const currentUrl = page.url();
    const popupPromise = context.waitForEvent('page', { timeout: 10_000 }).catch(() => null);
    await clickAndSettle(legalLink, page);
    let legalPage = await popupPromise;

    if (legalPage) {
      await waitForUi(legalPage);
    } else {
      legalPage = page;
      await waitForUi(page);
    }

    const legalHeading = await pickVisible(
      [legalPage.getByRole('heading', { name: headingRegex }), legalPage.getByText(headingRegex)],
      15_000,
    );
    expect(legalHeading, `Heading not found for ${headingRegex}`).not.toBeNull();

    const legalText = await pickVisible(
      [legalPage.locator('main p, article p, section p, p'), legalPage.locator('main li, article li, li')],
      10_000,
    );
    expect(legalText, `Legal content is not visible for ${headingRegex}`).not.toBeNull();

    evidence[evidenceKey] = {
      screenshot: await saveScreenshot(screenshotName, legalPage, true),
      finalUrl: legalPage.url(),
    };

    if (legalPage !== page) {
      await legalPage.close().catch(() => {});
      await page.bringToFront();
      await waitForUi(page);
      return;
    }

    if (page.url() !== currentUrl) {
      await page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => {});
      await waitForUi(page);
    }
  };

  await runStep('Login', async () => {
    const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.APP_URL;

    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: 'domcontentloaded' });
      await waitForUi(page);
    } else if (page.url() === 'about:blank') {
      throw new Error(
        'No login URL is available. Set SALEADS_LOGIN_URL (or APP_URL) unless running with a pre-opened login page.',
      );
    }

    const signInWithGoogle = await pickVisible(
      [
        page.getByRole('button', { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
        page.getByRole('link', { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
      ],
      15_000,
    );
    expect(signInWithGoogle, 'Google login control was not found').not.toBeNull();

    const popupPromise = context.waitForEvent('page', { timeout: 10_000 }).catch(() => null);
    await clickAndSettle(signInWithGoogle, page);
    const googlePage = await popupPromise;

    if (googlePage) {
      await waitForUi(googlePage);
      const accountItem = await pickVisible(
        [
          googlePage.getByRole('button', { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, 'i') }),
          googlePage.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, 'i')),
        ],
        10_000,
      );

      if (accountItem) {
        await clickAndSettle(accountItem, googlePage);
      }
    } else {
      const accountItemInSameTab = await pickVisible(
        [page.getByRole('button', { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, 'i') }), page.getByText(GOOGLE_ACCOUNT_EMAIL)],
        7_000,
      );

      if (accountItemInSameTab) {
        await clickAndSettle(accountItemInSameTab, page);
      }
    }

    await waitForUi(page);

    const sidebar = await pickVisible(
      [
        page.locator('aside'),
        page.getByRole('navigation'),
        page.locator('nav').filter({ hasText: /negocio|mi negocio|dashboard/i }),
      ],
      20_000,
    );

    expect(sidebar, 'Main application or left sidebar did not appear after login').not.toBeNull();
    evidence.dashboard = await saveScreenshot('01_dashboard_loaded');
  });

  await runBlockedStep('Mi Negocio menu', async () => {
    const negocioSection = await pickVisible(
      [
        page.getByRole('button', { name: /^Negocio$/i }),
        page.getByRole('link', { name: /^Negocio$/i }),
        page.getByText(/^Negocio$/i),
      ],
      10_000,
    );

    if (negocioSection) {
      await clickAndSettle(negocioSection, page);
    }

    const miNegocio = await pickVisible(
      [
        page.getByRole('button', { name: /mi negocio/i }),
        page.getByRole('link', { name: /mi negocio/i }),
        page.getByText(/mi negocio/i),
      ],
      10_000,
    );
    expect(miNegocio, 'Mi Negocio entry was not found in sidebar').not.toBeNull();
    await clickAndSettle(miNegocio, page);

    const agregarNegocio = await pickVisible(
      [
        page.getByRole('button', { name: /agregar negocio/i }),
        page.getByRole('link', { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i),
      ],
      10_000,
    );
    const administrarNegocios = await pickVisible(
      [
        page.getByRole('button', { name: /administrar negocios/i }),
        page.getByRole('link', { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i),
      ],
      10_000,
    );

    expect(agregarNegocio, 'Agregar Negocio is not visible after opening Mi Negocio').not.toBeNull();
    expect(administrarNegocios, 'Administrar Negocios is not visible after opening Mi Negocio').not.toBeNull();

    evidence.miNegocioMenu = await saveScreenshot('02_mi_negocio_menu_expanded');
  });

  await runBlockedStep('Agregar Negocio modal', async () => {
    const agregarNegocio = await pickVisible(
      [
        page.getByRole('button', { name: /agregar negocio/i }),
        page.getByRole('link', { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i),
      ],
      10_000,
    );
    expect(agregarNegocio, 'Agregar Negocio menu option was not found').not.toBeNull();

    await clickAndSettle(agregarNegocio, page);

    const modalTitle = await pickVisible(
      [page.getByRole('heading', { name: /crear nuevo negocio/i }), page.getByText(/crear nuevo negocio/i)],
      10_000,
    );
    expect(modalTitle, 'Modal title "Crear Nuevo Negocio" is missing').not.toBeNull();

    const businessNameInput = await pickVisible(
      [
        page.getByLabel(/nombre del negocio/i),
        page.getByPlaceholder(/nombre del negocio/i),
        page.getByRole('textbox', { name: /nombre del negocio/i }),
      ],
      10_000,
    );
    expect(businessNameInput, 'Input "Nombre del Negocio" is missing').not.toBeNull();

    const quotaText = await pickVisible([page.getByText(/tienes 2 de 3 negocios/i)], 10_000);
    expect(quotaText, 'Expected text "Tienes 2 de 3 negocios" was not found').not.toBeNull();

    const cancelarButton = await pickVisible([page.getByRole('button', { name: /cancelar/i })], 10_000);
    const crearNegocioButton = await pickVisible(
      [page.getByRole('button', { name: /crear negocio/i }), page.getByText(/crear negocio/i)],
      10_000,
    );
    expect(cancelarButton, 'Button "Cancelar" is missing').not.toBeNull();
    expect(crearNegocioButton, 'Button "Crear Negocio" is missing').not.toBeNull();

    await businessNameInput.fill('Negocio Prueba Automatización');
    await waitForUi(page);

    evidence.agregarNegocioModal = await saveScreenshot('03_agregar_negocio_modal');
    await clickAndSettle(cancelarButton, page);
  });

  await runBlockedStep('Administrar Negocios view', async () => {
    let administrarNegocios = await pickVisible(
      [
        page.getByRole('button', { name: /administrar negocios/i }),
        page.getByRole('link', { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i),
      ],
      5_000,
    );

    if (!administrarNegocios) {
      const miNegocio = await pickVisible(
        [
          page.getByRole('button', { name: /mi negocio/i }),
          page.getByRole('link', { name: /mi negocio/i }),
          page.getByText(/mi negocio/i),
        ],
        10_000,
      );
      expect(miNegocio, 'Mi Negocio menu is required to find Administrar Negocios').not.toBeNull();
      await clickAndSettle(miNegocio, page);

      administrarNegocios = await pickVisible(
        [
          page.getByRole('button', { name: /administrar negocios/i }),
          page.getByRole('link', { name: /administrar negocios/i }),
          page.getByText(/administrar negocios/i),
        ],
        10_000,
      );
    }

    expect(administrarNegocios, 'Administrar Negocios option was not found').not.toBeNull();
    await clickAndSettle(administrarNegocios, page);

    const informacionGeneral = await pickVisible([page.getByText(/informaci[oó]n general/i)], 15_000);
    const detallesCuenta = await pickVisible([page.getByText(/detalles de la cuenta/i)], 15_000);
    const tusNegocios = await pickVisible([page.getByText(/tus negocios/i)], 15_000);
    const seccionLegal = await pickVisible([page.getByText(/secci[oó]n legal/i)], 15_000);

    expect(informacionGeneral, 'Section "Información General" is missing').not.toBeNull();
    expect(detallesCuenta, 'Section "Detalles de la Cuenta" is missing').not.toBeNull();
    expect(tusNegocios, 'Section "Tus Negocios" is missing').not.toBeNull();
    expect(seccionLegal, 'Section "Sección Legal" is missing').not.toBeNull();

    evidence.administrarNegocios = await saveScreenshot('04_administrar_negocios', page, true);
  });

  await runBlockedStep('Información General', async () => {
    const userName = await pickVisible(
      [page.getByText(/juan|lucas|barbier|garzon/i), page.locator('h1, h2, h3, strong, span')],
      10_000,
    );
    const userEmail = await pickVisible([page.getByText(/@/i)], 10_000);
    const businessPlan = await pickVisible([page.getByText(/business plan/i)], 10_000);
    const cambiarPlan = await pickVisible([page.getByRole('button', { name: /cambiar plan/i })], 10_000);

    expect(userName, 'User name is not visible in Información General').not.toBeNull();
    expect(userEmail, 'User email is not visible in Información General').not.toBeNull();
    expect(businessPlan, 'Text "BUSINESS PLAN" is not visible').not.toBeNull();
    expect(cambiarPlan, 'Button "Cambiar Plan" is not visible').not.toBeNull();
  });

  await runBlockedStep('Detalles de la Cuenta', async () => {
    const cuentaCreada = await pickVisible([page.getByText(/cuenta creada/i)], 10_000);
    const estadoActivo = await pickVisible([page.getByText(/estado activo/i)], 10_000);
    const idiomaSeleccionado = await pickVisible([page.getByText(/idioma seleccionado/i)], 10_000);

    expect(cuentaCreada, '"Cuenta creada" is not visible').not.toBeNull();
    expect(estadoActivo, '"Estado activo" is not visible').not.toBeNull();
    expect(idiomaSeleccionado, '"Idioma seleccionado" is not visible').not.toBeNull();
  });

  await runBlockedStep('Tus Negocios', async () => {
    const listContainer = await pickVisible(
      [page.getByText(/tus negocios/i), page.locator('[class*="business"], [id*="business"], ul, table')],
      10_000,
    );
    const addBusinessButton = await pickVisible(
      [page.getByRole('button', { name: /agregar negocio/i }), page.getByText(/agregar negocio/i)],
      10_000,
    );
    const quotaText = await pickVisible([page.getByText(/tienes 2 de 3 negocios/i)], 10_000);

    expect(listContainer, 'Business list area is not visible').not.toBeNull();
    expect(addBusinessButton, 'Button "Agregar Negocio" is missing in Tus Negocios').not.toBeNull();
    expect(quotaText, 'Text "Tienes 2 de 3 negocios" is missing in Tus Negocios').not.toBeNull();
  });

  await runBlockedStep('Términos y Condiciones', async () => {
    await openAndValidateLegalPage({
      linkLabel: /t[ée]rminos y condiciones/i,
      headingRegex: /t[ée]rminos y condiciones/i,
      screenshotName: '05_terminos_y_condiciones',
      evidenceKey: 'terminosYCondiciones',
    });
  });

  await runBlockedStep('Política de Privacidad', async () => {
    await openAndValidateLegalPage({
      linkLabel: /pol[ií]tica de privacidad/i,
      headingRegex: /pol[ií]tica de privacidad/i,
      screenshotName: '06_politica_de_privacidad',
      evidenceKey: 'politicaDePrivacidad',
    });
  });

  const finalReport = {
    name: 'saleads_mi_negocio_full_test',
    generatedAt: new Date().toISOString(),
    environment: {
      providedLoginUrl: process.env.SALEADS_LOGIN_URL || process.env.APP_URL || null,
      finalApplicationUrl: page.url(),
      googleAccountAttempted: GOOGLE_ACCOUNT_EMAIL,
    },
    evidence,
    results,
  };

  const reportPath = path.join(outDir, 'final_report.json');
  fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), 'utf8');
  await testInfo.attach('saleads-mi-negocio-final-report', {
    path: reportPath,
    contentType: 'application/json',
  });

  const failingSteps = Object.entries(results)
    .filter(([, result]) => result.status === 'FAIL')
    .map(([step, result]) => `${step}: ${result.details}`);

  expect(
    failingSteps,
    failingSteps.length ? `One or more validation steps failed:\n${failingSteps.join('\n')}` : '',
  ).toEqual([]);
});
