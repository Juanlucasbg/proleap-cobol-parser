import { expect, Locator, Page, test, TestInfo } from '@playwright/test';

type StepName =
  | 'Login'
  | 'Mi Negocio menu'
  | 'Agregar Negocio modal'
  | 'Administrar Negocios view'
  | 'Informacion General'
  | 'Detalles de la Cuenta'
  | 'Tus Negocios'
  | 'Terminos y Condiciones'
  | 'Politica de Privacidad';

const GOOGLE_ACCOUNT_EMAIL = 'juanlucasbarbiergarzon@gmail.com';

async function waitForUiToSettle(page: Page): Promise<void> {
  await page.waitForTimeout(500);
  await page.waitForLoadState('domcontentloaded');
  await page.waitForLoadState('networkidle', { timeout: 7_000 }).catch(() => undefined);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.click();
  await waitForUiToSettle(page);
}

async function firstVisible(candidates: Locator[], timeoutMs = 10_000): Promise<Locator | null> {
  for (const locator of candidates) {
    const isVisible = await locator.first().isVisible({ timeout: timeoutMs }).catch(() => false);
    if (isVisible) {
      return locator.first();
    }
  }

  return null;
}

async function checkpoint(testInfo: TestInfo, page: Page, name: string, fullPage = false): Promise<void> {
  await page.screenshot({
    path: testInfo.outputPath(name),
    fullPage,
  });
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const agregarNegocioVisible = await page.getByText(/^Agregar Negocio$/i).first().isVisible().catch(() => false);
  const administrarNegociosVisible = await page.getByText(/^Administrar Negocios$/i).first().isVisible().catch(() => false);
  if (agregarNegocioVisible && administrarNegociosVisible) {
    return;
  }

  const negocio = await firstVisible([
    page.locator('aside').getByText(/^Negocio$/i),
    page.getByRole('button', { name: /^Negocio$/i }),
    page.getByText(/^Negocio$/i),
  ]);
  if (negocio) {
    await clickAndWait(page, negocio);
  }

  const miNegocio = await firstVisible([
    page.locator('aside').getByText(/^Mi Negocio$/i),
    page.getByRole('button', { name: /^Mi Negocio$/i }),
    page.getByText(/^Mi Negocio$/i),
  ]);

  if (!miNegocio) {
    throw new Error('No se encontro el menu "Mi Negocio".');
  }

  await clickAndWait(page, miNegocio);
}

async function validateLegalDocument(params: {
  linkName: RegExp;
  heading: RegExp;
  screenshotName: string;
  appPage: Page;
  testInfo: TestInfo;
}): Promise<string> {
  const { linkName, heading, screenshotName, appPage, testInfo } = params;
  const link = await firstVisible([
    appPage.getByRole('link', { name: linkName }),
    appPage.getByText(linkName),
  ]);

  if (!link) {
    throw new Error(`No se encontro el enlace legal para ${linkName.toString()}.`);
  }

  const appUrlBefore = appPage.url();
  const popupPromise = appPage.waitForEvent('popup', { timeout: 8_000 }).catch(() => null);
  await clickAndWait(appPage, link);

  const popup = await popupPromise;
  const targetPage = popup ?? appPage;

  await targetPage.waitForLoadState('domcontentloaded');
  await expect(targetPage.getByText(heading).first()).toBeVisible();
  await expect(targetPage.locator('p, li').first()).toBeVisible();

  await checkpoint(testInfo, targetPage, screenshotName);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
  } else if (targetPage.url() !== appUrlBefore) {
    await targetPage.goBack({ waitUntil: 'domcontentloaded' }).catch(async () => {
      await targetPage.goto(appUrlBefore, { waitUntil: 'domcontentloaded' });
    });
    await waitForUiToSettle(targetPage);
  }

  return finalUrl;
}

function stepErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

test('SaleADS Mi Negocio full workflow', async ({ page }, testInfo) => {
  const baseUrl = process.env.SALEADS_URL;
  if (!baseUrl) {
    throw new Error('SALEADS_URL es requerido. Debe apuntar al login de SaleADS del ambiente actual.');
  }

  const results: Record<StepName, 'PASS' | 'FAIL'> = {
    Login: 'FAIL',
    'Mi Negocio menu': 'FAIL',
    'Agregar Negocio modal': 'FAIL',
    'Administrar Negocios view': 'FAIL',
    'Informacion General': 'FAIL',
    'Detalles de la Cuenta': 'FAIL',
    'Tus Negocios': 'FAIL',
    'Terminos y Condiciones': 'FAIL',
    'Politica de Privacidad': 'FAIL',
  };
  const urls: Partial<Record<'terms' | 'privacy', string>> = {};
  const failures: string[] = [];

  const runStep = async (name: StepName, stepBody: () => Promise<void>) => {
    try {
      await stepBody();
      results[name] = 'PASS';
    } catch (error) {
      results[name] = 'FAIL';
      failures.push(`${name}: ${stepErrorMessage(error)}`);
    }
  };

  await page.goto(baseUrl, { waitUntil: 'domcontentloaded' });
  await waitForUiToSettle(page);

  await runStep('Login', async () => {
    const loginWithGoogle = await firstVisible(
      [
        page.getByRole('button', { name: /sign in with google|iniciar sesion con google|google/i }),
        page.getByRole('link', { name: /sign in with google|iniciar sesion con google|google/i }),
        page.getByText(/sign in with google|iniciar sesion con google|google/i),
      ],
      20_000,
    );
    if (!loginWithGoogle) {
      throw new Error('No se encontro boton de login con Google.');
    }

    await clickAndWait(page, loginWithGoogle);

    const googleAccountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
    const hasAccountPicker = await googleAccountOption.isVisible({ timeout: 8_000 }).catch(() => false);
    if (hasAccountPicker) {
      await clickAndWait(page, googleAccountOption);
    }

    await expect(page.locator('main, [role="main"]').first()).toBeVisible({ timeout: 45_000 });
    await expect(page.locator('aside, nav').first()).toBeVisible({ timeout: 45_000 });
    await checkpoint(testInfo, page, '01-dashboard-loaded.png');
  });

  await runStep('Mi Negocio menu', async () => {
    await ensureMiNegocioExpanded(page);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();
    await checkpoint(testInfo, page, '02-mi-negocio-menu-expanded.png');
  });

  await runStep('Agregar Negocio modal', async () => {
    const agregarNegocio = await firstVisible([
      page.locator('aside').getByText(/^Agregar Negocio$/i),
      page.getByRole('menuitem', { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);
    if (!agregarNegocio) {
      throw new Error('No se encontro la opcion "Agregar Negocio".');
    }

    await clickAndWait(page, agregarNegocio);

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
    const nombreInput = await firstVisible([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator('input[name*="negocio" i], input[id*="negocio" i]').first(),
    ]);

    if (!nombreInput) {
      throw new Error('No se encontro el input "Nombre del Negocio".');
    }

    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /Cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /Crear Negocio/i }).first()).toBeVisible();
    await checkpoint(testInfo, page, '03-agregar-negocio-modal.png');

    await nombreInput.fill('Negocio Prueba Automatizacion');
    const cancelar = page.getByRole('button', { name: /Cancelar/i }).first();
    await clickAndWait(page, cancelar);
  });

  await runStep('Administrar Negocios view', async () => {
    await ensureMiNegocioExpanded(page);

    const administrarNegocios = await firstVisible([
      page.locator('aside').getByText(/^Administrar Negocios$/i),
      page.getByRole('menuitem', { name: /^Administrar Negocios$/i }),
      page.getByText(/^Administrar Negocios$/i),
    ]);

    if (!administrarNegocios) {
      throw new Error('No se encontro "Administrar Negocios".');
    }

    await clickAndWait(page, administrarNegocios);
    await expect(page.getByText(/Informacion General|Informaci[oó]n General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Seccion Legal|Secci[oó]n Legal/i).first()).toBeVisible();
    await checkpoint(testInfo, page, '04-administrar-negocios-full-page.png', true);
  });

  await runStep('Informacion General', async () => {
    await expect(page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/).first()).toBeVisible();
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /Cambiar Plan/i }).first()).toBeVisible();

    const potentialUserName = await firstVisible([
      page.getByText(/Nombre/i),
      page.locator('[data-testid*="name" i]').first(),
      page.locator('h1, h2').first(),
    ]);
    if (!potentialUserName) {
      throw new Error('No se pudo confirmar visibilidad del nombre de usuario.');
    }
  });

  await runStep('Detalles de la Cuenta', async () => {
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
  });

  await runStep('Tus Negocios', async () => {
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    const addBusinessControl = await firstVisible([
      page.getByRole('button', { name: /Agregar Negocio/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);
    if (!addBusinessControl) {
      throw new Error('No se encontro el boton o acceso de "Agregar Negocio" en la seccion Tus Negocios.');
    }
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
  });

  await runStep('Terminos y Condiciones', async () => {
    urls.terms = await validateLegalDocument({
      linkName: /Terminos y Condiciones|T[eé]rminos y Condiciones/i,
      heading: /Terminos y Condiciones|T[eé]rminos y Condiciones/i,
      screenshotName: '05-terminos-y-condiciones.png',
      appPage: page,
      testInfo,
    });
    await ensureMiNegocioExpanded(page);
  });

  await runStep('Politica de Privacidad', async () => {
    urls.privacy = await validateLegalDocument({
      linkName: /Politica de Privacidad|Pol[ií]tica de Privacidad/i,
      heading: /Politica de Privacidad|Pol[ií]tica de Privacidad/i,
      screenshotName: '06-politica-de-privacidad.png',
      appPage: page,
      testInfo,
    });
  });

  const report = {
    login: results.Login,
    miNegocioMenu: results['Mi Negocio menu'],
    agregarNegocioModal: results['Agregar Negocio modal'],
    administrarNegociosView: results['Administrar Negocios view'],
    informacionGeneral: results['Informacion General'],
    detallesDeLaCuenta: results['Detalles de la Cuenta'],
    tusNegocios: results['Tus Negocios'],
    terminosYCondiciones: results['Terminos y Condiciones'],
    politicaDePrivacidad: results['Politica de Privacidad'],
    urls,
    failures,
  };

  await testInfo.attach('saleads-mi-negocio-final-report', {
    body: JSON.stringify(report, null, 2),
    contentType: 'application/json',
  });

  expect.soft(results.Login, 'Login').toBe('PASS');
  expect.soft(results['Mi Negocio menu'], 'Mi Negocio menu').toBe('PASS');
  expect.soft(results['Agregar Negocio modal'], 'Agregar Negocio modal').toBe('PASS');
  expect.soft(results['Administrar Negocios view'], 'Administrar Negocios view').toBe('PASS');
  expect.soft(results['Informacion General'], 'Informacion General').toBe('PASS');
  expect.soft(results['Detalles de la Cuenta'], 'Detalles de la Cuenta').toBe('PASS');
  expect.soft(results['Tus Negocios'], 'Tus Negocios').toBe('PASS');
  expect.soft(results['Terminos y Condiciones'], 'Terminos y Condiciones').toBe('PASS');
  expect.soft(results['Politica de Privacidad'], 'Politica de Privacidad').toBe('PASS');

  expect(failures, `Fallas detectadas:\n${failures.join('\n')}`).toEqual([]);
});
