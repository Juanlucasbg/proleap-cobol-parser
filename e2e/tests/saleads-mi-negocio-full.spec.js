const { test, expect } = require('@playwright/test');
const path = require('path');
const fs = require('fs');

const GOOGLE_ACCOUNT_EMAIL = 'juanlucasbarbiergarzon@gmail.com';
const START_URL_ENV_KEYS = ['SALEADS_START_URL', 'SALEADS_URL', 'E2E_START_URL'];

function emptyReport() {
  return {
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
}

function getStartUrl() {
  for (const key of START_URL_ENV_KEYS) {
    const value = process.env[key];
    if (value && value.trim()) {
      return value.trim();
    }
  }

  return null;
}

async function waitForUi(page) {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function clickAndWait(locator, page) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function clickFirstAvailable(page, candidates) {
  for (const locator of candidates) {
    if (await locator.first().isVisible().catch(() => false)) {
      await clickAndWait(locator.first(), page);
      return true;
    }
  }

  return false;
}

async function capture(page, name) {
  await page.screenshot({ path: path.join('test-results', `${name}.png`), fullPage: true });
}

async function findSidebar(page) {
  const candidates = [
    page.getByRole('navigation'),
    page.locator('aside'),
    page.locator('[class*="sidebar"]'),
  ];

  for (const locator of candidates) {
    if (await locator.first().isVisible().catch(() => false)) {
      return locator.first();
    }
  }

  return null;
}

async function openLegalDoc(page, context, linkText, headingRegex, screenshotName) {
  const appPage = page;
  const popupPromise = context.waitForEvent('page', { timeout: 7000 }).catch(() => null);
  const legalLink = page.getByRole('link', { name: new RegExp(linkText, 'i') }).first();

  await clickAndWait(legalLink, page);
  const popup = await popupPromise;

  const legalPage = popup || page;
  await legalPage.waitForLoadState('domcontentloaded');
  await legalPage.waitForLoadState('networkidle', { timeout: 10000 }).catch(() => {});

  const heading = legalPage.getByRole('heading', { name: headingRegex }).first();
  await expect(heading).toBeVisible();
  await expect(legalPage.locator('body')).toContainText(/.{40,}/);

  await capture(legalPage, screenshotName);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await legalPage.goBack({ waitUntil: 'domcontentloaded' }).catch(() => {});
    await appPage.bringToFront();
    await waitForUi(appPage);
  }

  return finalUrl;
}

test.describe('SaleADS Mi Negocio full workflow', () => {
  test('logs in with Google and validates Mi Negocio module', async ({ page, context }) => {
    const report = emptyReport();
    const legalUrls = {
      terminos: '',
      privacidad: '',
    };

    fs.mkdirSync('test-results', { recursive: true });

    const startUrl = getStartUrl();
    if (!startUrl) {
      throw new Error(
        'Start URL is required. Set SALEADS_START_URL, SALEADS_URL, or E2E_START_URL to the current environment login page.',
      );
    }

    await page.goto(startUrl, { waitUntil: 'domcontentloaded' });
    await waitForUi(page);

    await test.step('Step 1 - Login with Google', async () => {
      const loginClicked = await clickFirstAvailable(page, [
        page.getByRole('button', { name: /sign in with google/i }),
        page.getByRole('button', { name: /iniciar sesion con google/i }),
        page.getByRole('button', { name: /continuar con google/i }),
        page.getByRole('link', { name: /google/i }),
      ]);

      expect(loginClicked).toBeTruthy();

      const accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
      if (await accountOption.isVisible().catch(() => false)) {
        await clickAndWait(accountOption, page);
      }

      const sidebar = await findSidebar(page);
      const negocioInSidebar = page.getByText(/negocio/i).first();

      await expect(sidebar || negocioInSidebar).toBeVisible();
      await expect(negocioInSidebar).toBeVisible();

      await capture(page, 'step-1-dashboard-loaded');
      report.Login = 'PASS';
    });

    await test.step('Step 2 - Open Mi Negocio menu', async () => {
      const negocioSection = page.getByText(/^Negocio$/i).first();
      if (await negocioSection.isVisible().catch(() => false)) {
        await clickAndWait(negocioSection, page);
      }

      const miNegocio = page.getByText(/^Mi Negocio$/i).first();
      await clickAndWait(miNegocio, page);

      const agregarNegocio = page.getByText(/^Agregar Negocio$/i).first();
      const administrarNegocios = page.getByText(/^Administrar Negocios$/i).first();

      await expect(agregarNegocio).toBeVisible();
      await expect(administrarNegocios).toBeVisible();

      await capture(page, 'step-2-mi-negocio-menu-expanded');
      report['Mi Negocio menu'] = 'PASS';
    });

    await test.step('Step 3 - Validate Agregar Negocio modal', async () => {
      const agregarNegocio = page.getByText(/^Agregar Negocio$/i).first();
      await clickAndWait(agregarNegocio, page);

      const modalTitle = page.getByRole('heading', { name: /Crear Nuevo Negocio/i }).first();
      await expect(modalTitle).toBeVisible();
      await expect(page.getByLabel(/Nombre del Negocio/i).first()).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
      await expect(page.getByRole('button', { name: /^Cancelar$/i }).first()).toBeVisible();
      await expect(page.getByRole('button', { name: /^Crear Negocio$/i }).first()).toBeVisible();

      await capture(page, 'step-3-agregar-negocio-modal');

      const nombreInput = page.getByLabel(/Nombre del Negocio/i).first();
      await nombreInput.click();
      await waitForUi(page);
      await nombreInput.fill('Negocio Prueba Automatizacion');
      await waitForUi(page);
      await clickAndWait(page.getByRole('button', { name: /^Cancelar$/i }).first(), page);

      report['Agregar Negocio modal'] = 'PASS';
    });

    await test.step('Step 4 - Open Administrar Negocios', async () => {
      const administrarNegocios = page.getByText(/^Administrar Negocios$/i).first();
      if (!(await administrarNegocios.isVisible().catch(() => false))) {
        await clickAndWait(page.getByText(/^Mi Negocio$/i).first(), page);
      }

      await clickAndWait(page.getByText(/^Administrar Negocios$/i).first(), page);

      await expect(page.getByText(/Informacion General|Información General/i).first()).toBeVisible();
      await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.getByText(/Seccion Legal|Sección Legal/i).first()).toBeVisible();

      await capture(page, 'step-4-administrar-negocios-page');
      report['Administrar Negocios view'] = 'PASS';
    });

    await test.step('Step 5 - Validate Informacion General', async () => {
      await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
      await expect(page.getByRole('button', { name: /Cambiar Plan/i }).first()).toBeVisible();

      const nameEmailBlock = page.locator('body');
      await expect(nameEmailBlock).toContainText(/@/);
      await expect(nameEmailBlock).toContainText(/[A-Za-z].*[A-Za-z]/);

      report['Informacion General'] = 'PASS';
    });

    await test.step('Step 6 - Validate Detalles de la Cuenta', async () => {
      await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
      await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();

      report['Detalles de la Cuenta'] = 'PASS';
    });

    await test.step('Step 7 - Validate Tus Negocios', async () => {
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.getByRole('button', { name: /^Agregar Negocio$/i }).first()).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();

      report['Tus Negocios'] = 'PASS';
    });

    await test.step('Step 8 - Validate Terminos y Condiciones', async () => {
      legalUrls.terminos = await openLegalDoc(
        page,
        context,
        'Terminos y Condiciones|Términos y Condiciones',
        /Terminos y Condiciones|Términos y Condiciones/i,
        'step-8-terminos-y-condiciones',
      );

      report['Terminos y Condiciones'] = 'PASS';
    });

    await test.step('Step 9 - Validate Politica de Privacidad', async () => {
      legalUrls.privacidad = await openLegalDoc(
        page,
        context,
        'Politica de Privacidad|Política de Privacidad',
        /Politica de Privacidad|Política de Privacidad/i,
        'step-9-politica-de-privacidad',
      );

      report['Politica de Privacidad'] = 'PASS';
    });

    const finalReport = {
      report,
      evidence: {
        dashboard: 'test-results/step-1-dashboard-loaded.png',
        menu: 'test-results/step-2-mi-negocio-menu-expanded.png',
        modal: 'test-results/step-3-agregar-negocio-modal.png',
        administrarNegocios: 'test-results/step-4-administrar-negocios-page.png',
        terminos: 'test-results/step-8-terminos-y-condiciones.png',
        privacidad: 'test-results/step-9-politica-de-privacidad.png',
      },
      urls: {
        terminosYCondiciones: legalUrls.terminos,
        politicaDePrivacidad: legalUrls.privacidad,
      },
    };

    fs.writeFileSync('test-results/saleads-mi-negocio-final-report.json', JSON.stringify(finalReport, null, 2));

    console.log('\n===== SALEADS MI NEGOCIO FINAL REPORT =====');
    console.log(JSON.stringify(finalReport, null, 2));
  });
});
