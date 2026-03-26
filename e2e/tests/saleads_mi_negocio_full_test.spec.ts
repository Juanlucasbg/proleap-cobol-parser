import { expect, type Page, test } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

type StepKey =
  | 'Login'
  | 'Mi Negocio menu'
  | 'Agregar Negocio modal'
  | 'Administrar Negocios view'
  | 'Información General'
  | 'Detalles de la Cuenta'
  | 'Tus Negocios'
  | 'Términos y Condiciones'
  | 'Política de Privacidad';

type StepResult = {
  status: 'PASS' | 'FAIL';
  detail: string;
};

const SCREENSHOT_DIR = path.resolve(__dirname, '../artifacts/screenshots');

async function ensureEvidenceDir(): Promise<void> {
  await fs.mkdir(SCREENSHOT_DIR, { recursive: true });
}

function normalizeText(input: string | null | undefined): string {
  return (input ?? '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim();
}

async function clickByVisibleText(page: Page, text: string): Promise<void> {
  const roleCandidate = page
    .getByRole('button', { name: new RegExp(`^${text}$`, 'i') })
    .or(page.getByRole('link', { name: new RegExp(`^${text}$`, 'i') }))
    .first();

  if (await roleCandidate.isVisible().catch(() => false)) {
    await roleCandidate.click();
    await waitForUiToLoad(page);
    return;
  }

  const textCandidate = page.getByText(new RegExp(`^${text}$`, 'i')).first();
  await expect(textCandidate).toBeVisible();
  await textCandidate.click();
  await waitForUiToLoad(page);
}

async function clickByRoleNameAndWait(
  page: Page,
  role: 'button' | 'link',
  name: RegExp | string
): Promise<void> {
  const candidate = page.getByRole(role, { name }).first();
  await expect(candidate).toBeVisible();
  await candidate.click();
  await waitForUiToLoad(page);
}

async function waitForUiToLoad(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(900);
}

async function saveShot(page: Page, fileName: string, fullPage = false): Promise<string> {
  await ensureEvidenceDir();
  const filePath = path.join(SCREENSHOT_DIR, fileName);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function openLegalAndValidate(
  appPage: Page,
  legalLinkRegex: RegExp,
  expectedHeadingRegex: RegExp,
  screenshotName: string
): Promise<{ url: string; screenshotPath: string }> {
  const linkLocator = appPage
    .getByRole('link', { name: legalLinkRegex })
    .or(appPage.getByRole('button', { name: legalLinkRegex }))
    .or(appPage.getByText(legalLinkRegex))
    .first();
  await expect(linkLocator).toBeVisible();

  let legalPage = appPage;
  const popupPromise = appPage.context().waitForEvent('page', { timeout: 7_000 }).catch(() => null);
  await linkLocator.click();
  await waitForUiToLoad(appPage);

  const popup = await popupPromise;
  if (popup) {
    legalPage = popup;
    await legalPage.waitForLoadState('domcontentloaded');
  }

  await expect(
    legalPage.getByRole('heading', { name: expectedHeadingRegex }).first()
  ).toBeVisible();

  const bodyText = normalizeText(await legalPage.locator('body').innerText());
  expect(bodyText.length).toBeGreaterThan(120);

  const screenshotPath = await saveShot(legalPage, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await legalPage.close();
    await appPage.bringToFront();
    await waitForUiToLoad(appPage);
  }

  return { url: finalUrl, screenshotPath };
}

test('saleads_mi_negocio_full_test', async ({ page }) => {
  const baseUrl = process.env.SALEADS_BASE_URL;

  if (baseUrl) {
    await page.goto(baseUrl, { waitUntil: 'domcontentloaded' });
  } else {
    // The workflow may start from an already open login page in any environment.
    await waitForUiToLoad(page);
  }

  const results: Record<StepKey, StepResult> = {
    Login: { status: 'FAIL', detail: 'Not executed' },
    'Mi Negocio menu': { status: 'FAIL', detail: 'Not executed' },
    'Agregar Negocio modal': { status: 'FAIL', detail: 'Not executed' },
    'Administrar Negocios view': { status: 'FAIL', detail: 'Not executed' },
    'Información General': { status: 'FAIL', detail: 'Not executed' },
    'Detalles de la Cuenta': { status: 'FAIL', detail: 'Not executed' },
    'Tus Negocios': { status: 'FAIL', detail: 'Not executed' },
    'Términos y Condiciones': { status: 'FAIL', detail: 'Not executed' },
    'Política de Privacidad': { status: 'FAIL', detail: 'Not executed' }
  };

  const legalEvidence: { terminosUrl?: string; politicaUrl?: string } = {};

  // Step 1: Login with Google.
  try {
    const googleTrigger = page
      .getByRole('button', { name: /google|sign in with google|inicia(r|r sesi[oó]n) con google/i })
      .first();
    await expect(googleTrigger).toBeVisible();

    const oauthPopupPromise = page.context().waitForEvent('page', { timeout: 7_000 }).catch(() => null);
    await googleTrigger.click();
    await waitForUiToLoad(page);
    const oauthPopup = await oauthPopupPromise;

    if (oauthPopup) {
      await oauthPopup.waitForLoadState('domcontentloaded');
      const popupAccountOption = oauthPopup
        .getByText('juanlucasbarbiergarzon@gmail.com', { exact: true })
        .first();
      if (await popupAccountOption.isVisible().catch(() => false)) {
        await popupAccountOption.click();
      }

      await oauthPopup.waitForTimeout(1_500);
      if (!oauthPopup.isClosed()) {
        await oauthPopup.close().catch(() => undefined);
      }
      await page.bringToFront();
      await waitForUiToLoad(page);
    }

    const accountOption = page.getByText('juanlucasbarbiergarzon@gmail.com', { exact: true }).first();
    if (await accountOption.isVisible().catch(() => false)) {
      await accountOption.click();
      await waitForUiToLoad(page);
    }

    await waitForUiToLoad(page);

    const sidebar = page
      .locator('aside, nav')
      .filter({ hasText: /negocio|mi negocio|dashboard|inicio/i })
      .first();
    await expect(sidebar).toBeVisible();
    const dashboardShot = await saveShot(page, '01-dashboard-after-login.png', true);
    results.Login = { status: 'PASS', detail: `Dashboard loaded. Screenshot: ${dashboardShot}` };
  } catch (error) {
    results.Login = { status: 'FAIL', detail: `Login flow failed: ${String(error)}` };
    throw error;
  }

  // Step 2: Open Mi Negocio menu.
  try {
    await clickByVisibleText(page, 'Negocio');
    await clickByVisibleText(page, 'Mi Negocio');

    await expect(page.getByText('Agregar Negocio', { exact: true }).first()).toBeVisible();
    await expect(page.getByText('Administrar Negocios', { exact: true }).first()).toBeVisible();

    const menuShot = await saveShot(page, '02-mi-negocio-expanded-menu.png');
    results['Mi Negocio menu'] = { status: 'PASS', detail: `Menu expanded. Screenshot: ${menuShot}` };
  } catch (error) {
    results['Mi Negocio menu'] = { status: 'FAIL', detail: `Menu validation failed: ${String(error)}` };
    throw error;
  }

  // Step 3: Validate Agregar Negocio modal.
  try {
    await clickByVisibleText(page, 'Agregar Negocio');

    await expect(page.getByRole('heading', { name: 'Crear Nuevo Negocio' })).toBeVisible();
    await expect(page.getByLabel('Nombre del Negocio').first()).toBeVisible();
    await expect(page.getByText('Tienes 2 de 3 negocios', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Cancelar' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Crear Negocio' })).toBeVisible();

    await page.getByLabel('Nombre del Negocio').first().click();
    await page.getByLabel('Nombre del Negocio').first().fill('Negocio Prueba Automatizacion');

    const modalShot = await saveShot(page, '03-crear-nuevo-negocio-modal.png');
    await clickByRoleNameAndWait(page, 'button', 'Cancelar');

    results['Agregar Negocio modal'] = { status: 'PASS', detail: `Modal validated. Screenshot: ${modalShot}` };
  } catch (error) {
    results['Agregar Negocio modal'] = { status: 'FAIL', detail: `Modal validation failed: ${String(error)}` };
    throw error;
  }

  // Step 4: Open Administrar Negocios view.
  try {
    if (!(await page.getByText('Administrar Negocios', { exact: true }).first().isVisible().catch(() => false))) {
      await clickByVisibleText(page, 'Mi Negocio');
    }

    await clickByVisibleText(page, 'Administrar Negocios');

    await expect(page.getByRole('heading', { name: /informaci[oó]n general/i }).first()).toBeVisible();
    await expect(page.getByRole('heading', { name: /detalles de la cuenta/i }).first()).toBeVisible();
    await expect(page.getByRole('heading', { name: /tus negocios/i }).first()).toBeVisible();
    await expect(page.getByRole('heading', { name: /secci[oó]n legal/i }).first()).toBeVisible();

    const accountShot = await saveShot(page, '04-administrar-negocios-cuenta.png', true);
    results['Administrar Negocios view'] = {
      status: 'PASS',
      detail: `Account view sections found. Screenshot: ${accountShot}`
    };
  } catch (error) {
    results['Administrar Negocios view'] = {
      status: 'FAIL',
      detail: `Administrar Negocios validation failed: ${String(error)}`
    };
    throw error;
  }

  // Step 5: Validate Información General.
  try {
    const infoSection = page
      .locator('section,div')
      .filter({ has: page.getByRole('heading', { name: /informaci[oó]n general/i }) })
      .first();
    await expect(infoSection).toContainText(/@/);
    await expect(infoSection).toContainText(/business plan/i);
    await expect(infoSection.getByRole('button', { name: /cambiar plan/i })).toBeVisible();
    results['Información General'] = { status: 'PASS', detail: 'Name/email/plan/button are visible.' };
  } catch (error) {
    results['Información General'] = {
      status: 'FAIL',
      detail: `Información General failed: ${String(error)}`
    };
    throw error;
  }

  // Step 6: Validate Detalles de la Cuenta.
  try {
    const detailsSection = page
      .locator('section,div')
      .filter({ has: page.getByRole('heading', { name: /detalles de la cuenta/i }) })
      .first();
    await expect(detailsSection).toContainText(/cuenta creada/i);
    await expect(detailsSection).toContainText(/estado activo/i);
    await expect(detailsSection).toContainText(/idioma seleccionado/i);
    results['Detalles de la Cuenta'] = { status: 'PASS', detail: 'Account details labels are visible.' };
  } catch (error) {
    results['Detalles de la Cuenta'] = {
      status: 'FAIL',
      detail: `Detalles de la Cuenta failed: ${String(error)}`
    };
    throw error;
  }

  // Step 7: Validate Tus Negocios.
  try {
    const businessesSection = page
      .locator('section,div')
      .filter({ has: page.getByRole('heading', { name: /tus negocios/i }) })
      .first();
    await expect(businessesSection).toBeVisible();
    await expect(businessesSection.getByRole('button', { name: /^agregar negocio$/i })).toBeVisible();
    await expect(businessesSection).toContainText(/tienes 2 de 3 negocios/i);
    results['Tus Negocios'] = { status: 'PASS', detail: 'Businesses section and limits text are visible.' };
  } catch (error) {
    results['Tus Negocios'] = { status: 'FAIL', detail: `Tus Negocios failed: ${String(error)}` };
    throw error;
  }

  // Step 8: Validate Términos y Condiciones.
  try {
    const { url, screenshotPath } = await openLegalAndValidate(
      page,
      /t[eé]rminos y condiciones/i,
      /t[eé]rminos y condiciones/i,
      '05-terminos-y-condiciones.png'
    );
    legalEvidence.terminosUrl = url;
    results['Términos y Condiciones'] = {
      status: 'PASS',
      detail: `Legal page validated. URL: ${url}. Screenshot: ${screenshotPath}`
    };
  } catch (error) {
    results['Términos y Condiciones'] = {
      status: 'FAIL',
      detail: `Términos y Condiciones failed: ${String(error)}`
    };
    throw error;
  }

  // Step 9: Validate Política de Privacidad.
  try {
    const { url, screenshotPath } = await openLegalAndValidate(
      page,
      /pol[ií]tica de privacidad/i,
      /pol[ií]tica de privacidad/i,
      '06-politica-de-privacidad.png'
    );
    legalEvidence.politicaUrl = url;
    results['Política de Privacidad'] = {
      status: 'PASS',
      detail: `Privacy page validated. URL: ${url}. Screenshot: ${screenshotPath}`
    };
  } catch (error) {
    results['Política de Privacidad'] = {
      status: 'FAIL',
      detail: `Política de Privacidad failed: ${String(error)}`
    };
    throw error;
  }

  // Step 10: Final report.
  await test.info().attach('mi-negocio-final-report.json', {
    contentType: 'application/json',
    body: Buffer.from(JSON.stringify({ report: results, legalEvidence }, null, 2), 'utf-8')
  });

  for (const [step, result] of Object.entries(results)) {
    expect(result.status, `${step}: ${result.detail}`).toBe('PASS');
  }
});
