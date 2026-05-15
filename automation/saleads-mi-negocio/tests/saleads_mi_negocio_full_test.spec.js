import fs from 'node:fs';
import path from 'node:path';
import { expect, test } from '@playwright/test';

const SCREENSHOT_DIR = path.resolve('artifacts', 'screenshots');
const GOOGLE_ACCOUNT_EMAIL = 'juanlucasbarbiergarzon@gmail.com';

const reportTemplate = {
  Login: { status: 'NOT_RUN', details: '' },
  'Mi Negocio menu': { status: 'NOT_RUN', details: '' },
  'Agregar Negocio modal': { status: 'NOT_RUN', details: '' },
  'Administrar Negocios view': { status: 'NOT_RUN', details: '' },
  'Información General': { status: 'NOT_RUN', details: '' },
  'Detalles de la Cuenta': { status: 'NOT_RUN', details: '' },
  'Tus Negocios': { status: 'NOT_RUN', details: '' },
  'Términos y Condiciones': { status: 'NOT_RUN', details: '' },
  'Política de Privacidad': { status: 'NOT_RUN', details: '' }
};

function setPass(report, key, details = '') {
  report[key] = { status: 'PASS', details };
}

function setFail(report, key, error) {
  const details = error instanceof Error ? error.message : String(error);
  report[key] = { status: 'FAIL', details };
}

async function clickAndWaitForUi(page, locator) {
  await expect(locator).toBeVisible();
  await locator.click();
  await page.waitForLoadState('domcontentloaded').catch(() => {});
  await page.waitForLoadState('networkidle', { timeout: 7_000 }).catch(() => {});
  await page.waitForTimeout(700);
}

async function captureCheckpoint(page, name, fullPage = false) {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
  const filePath = path.join(
    SCREENSHOT_DIR,
    `${new Date().toISOString().replace(/[:.]/g, '-')}-${name}.png`
  );
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

function textLocator(page, textRegex) {
  return page.getByText(textRegex).first();
}

async function clickByVisibleText(page, role, nameRegex) {
  const roleLocator = page.getByRole(role, { name: nameRegex }).first();
  if (await roleLocator.isVisible().catch(() => false)) {
    await clickAndWaitForUi(page, roleLocator);
    return;
  }

  const fallback = textLocator(page, nameRegex);
  await clickAndWaitForUi(page, fallback);
}

async function chooseGoogleAccountIfPrompted(context, appPage) {
  const candidatePages = [appPage, ...context.pages().filter((p) => p !== appPage)];
  for (const candidate of candidatePages) {
    const emailByText = candidate.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
    const emailByButton = candidate.getByRole('button', { name: GOOGLE_ACCOUNT_EMAIL }).first();
    const emailByLink = candidate.getByRole('link', { name: GOOGLE_ACCOUNT_EMAIL }).first();

    if (await emailByText.isVisible().catch(() => false)) {
      await emailByText.click();
      await candidate.waitForLoadState('domcontentloaded').catch(() => {});
      await appPage.bringToFront().catch(() => {});
      return true;
    }

    if (await emailByButton.isVisible().catch(() => false)) {
      await emailByButton.click();
      await candidate.waitForLoadState('domcontentloaded').catch(() => {});
      await appPage.bringToFront().catch(() => {});
      return true;
    }

    if (await emailByLink.isVisible().catch(() => false)) {
      await emailByLink.click();
      await candidate.waitForLoadState('domcontentloaded').catch(() => {});
      await appPage.bringToFront().catch(() => {});
      return true;
    }
  }

  return false;
}

async function openAndValidateLegalDocument({
  page,
  context,
  linkNameRegex,
  headingRegex,
  screenshotName
}) {
  const link = page.getByRole('link', { name: linkNameRegex }).first();
  const fallbackLink = page.getByText(linkNameRegex).first();
  const clickable = (await link.isVisible().catch(() => false)) ? link : fallbackLink;

  let targetPage = page;
  const popupPromise = context.waitForEvent('page', { timeout: 6_000 }).catch(() => null);
  await clickAndWaitForUi(page, clickable);
  const popup = await popupPromise;

  if (popup) {
    targetPage = popup;
    await popup.waitForLoadState('domcontentloaded');
    await popup.waitForLoadState('networkidle', { timeout: 8_000 }).catch(() => {});
  } else {
    await page.waitForLoadState('domcontentloaded');
    await page.waitForLoadState('networkidle', { timeout: 8_000 }).catch(() => {});
  }

  const heading = targetPage.getByRole('heading', { name: headingRegex }).first();
  if (await heading.isVisible().catch(() => false)) {
    await expect(heading).toBeVisible();
  } else {
    await expect(targetPage.getByText(headingRegex).first()).toBeVisible();
  }

  await expect(targetPage.locator('body')).toContainText(/\S{20,}/);
  const screenshotPath = await captureCheckpoint(targetPage, screenshotName, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await targetPage.close();
    await page.bringToFront();
  } else {
    await page.goBack().catch(() => {});
    await page.waitForLoadState('domcontentloaded').catch(() => {});
  }

  return { finalUrl, screenshotPath };
}

test('saleads_mi_negocio_full_test', async ({ page, context }) => {
  const report = structuredClone(reportTemplate);
  const evidence = {};

  await test.step('1) Login with Google', async () => {
    try {
      const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.BASE_URL;
      if (loginUrl) {
        await page.goto(loginUrl, { waitUntil: 'domcontentloaded' });
      } else if (page.url() === 'about:blank') {
        throw new Error(
          'Current page is about:blank. Provide SALEADS_LOGIN_URL or BASE_URL to make the test environment-agnostic.'
        );
      }

      const googleButtonByRole = page
        .getByRole('button', {
          name: /sign in with google|iniciar sesión con google|continuar con google|google/i
        })
        .first();
      const googleButtonByText = page.getByText(/sign in with google|google/i).first();
      const googleButton = (await googleButtonByRole.isVisible().catch(() => false))
        ? googleButtonByRole
        : googleButtonByText;

      const oauthPopupPromise = context.waitForEvent('page', { timeout: 7_000 }).catch(() => null);
      await clickAndWaitForUi(page, googleButton);
      const oauthPopup = await oauthPopupPromise;
      if (oauthPopup) {
        await oauthPopup.waitForLoadState('domcontentloaded').catch(() => {});
      }

      await chooseGoogleAccountIfPrompted(context, page);
      await page.waitForLoadState('domcontentloaded').catch(() => {});
      await page.waitForLoadState('networkidle', { timeout: 12_000 }).catch(() => {});

      await expect(page.locator('aside, nav').first()).toBeVisible();
      evidence.dashboard = await captureCheckpoint(page, '01-dashboard-loaded');
      setPass(report, 'Login', `Dashboard screenshot: ${evidence.dashboard}`);
    } catch (error) {
      setFail(report, 'Login', error);
      throw error;
    }
  });

  await test.step('2) Open Mi Negocio menu and validate submenu', async () => {
    try {
      await clickByVisibleText(page, 'button', /^Negocio$/i).catch(async () => {
        await clickByVisibleText(page, 'link', /^Negocio$/i);
      });

      await clickByVisibleText(page, 'button', /^Mi Negocio$/i).catch(async () => {
        await clickByVisibleText(page, 'link', /^Mi Negocio$/i);
      });

      await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
      await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();
      evidence.expandedMenu = await captureCheckpoint(page, '02-mi-negocio-menu-expanded');
      setPass(report, 'Mi Negocio menu', `Expanded menu screenshot: ${evidence.expandedMenu}`);
    } catch (error) {
      setFail(report, 'Mi Negocio menu', error);
      throw error;
    }
  });

  await test.step('3) Validate Agregar Negocio modal', async () => {
    try {
      await clickByVisibleText(page, 'button', /^Agregar Negocio$/i).catch(async () => {
        await clickByVisibleText(page, 'link', /^Agregar Negocio$/i);
      });

      const modal = page.getByRole('dialog').first();
      await expect(modal.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
      const businessNameInputByLabel = modal.getByLabel(/Nombre del Negocio/i).first();
      const businessNameInputByPlaceholder = modal.getByPlaceholder(/Nombre del Negocio/i).first();
      const businessNameInput = (await businessNameInputByLabel.isVisible().catch(() => false))
        ? businessNameInputByLabel
        : businessNameInputByPlaceholder;
      await expect(businessNameInput).toBeVisible();
      await expect(modal.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
      await expect(modal.getByRole('button', { name: /Cancelar/i }).first()).toBeVisible();
      await expect(modal.getByRole('button', { name: /Crear Negocio/i }).first()).toBeVisible();

      await businessNameInput.click();
      await businessNameInput.fill('Negocio Prueba Automatización');
      evidence.agregarModal = await captureCheckpoint(page, '03-agregar-negocio-modal');
      await clickAndWaitForUi(page, modal.getByRole('button', { name: /Cancelar/i }).first());

      setPass(report, 'Agregar Negocio modal', `Modal screenshot: ${evidence.agregarModal}`);
    } catch (error) {
      setFail(report, 'Agregar Negocio modal', error);
      throw error;
    }
  });

  await test.step('4) Open Administrar Negocios and validate account page sections', async () => {
    try {
      const administrarOption = page.getByText(/^Administrar Negocios$/i).first();
      if (!(await administrarOption.isVisible().catch(() => false))) {
        await clickByVisibleText(page, 'button', /^Mi Negocio$/i).catch(async () => {
          await clickByVisibleText(page, 'link', /^Mi Negocio$/i);
        });
      }

      await clickByVisibleText(page, 'button', /^Administrar Negocios$/i).catch(async () => {
        await clickByVisibleText(page, 'link', /^Administrar Negocios$/i);
      });

      await expect(page.getByText(/Información General/i).first()).toBeVisible();
      await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();

      evidence.accountPage = await captureCheckpoint(page, '04-administrar-negocios-account-page', true);
      setPass(report, 'Administrar Negocios view', `Account page screenshot: ${evidence.accountPage}`);
    } catch (error) {
      setFail(report, 'Administrar Negocios view', error);
      throw error;
    }
  });

  await test.step('5) Validate Información General', async () => {
    try {
      const section = page.getByText(/Información General/i).first().locator('xpath=ancestor::*[self::section or self::div][1]');
      await expect(section.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()).toBeVisible();
      await expect(section.getByText(/[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)+/).first()).toBeVisible();
      await expect(section.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
      await expect(section.getByRole('button', { name: /Cambiar Plan/i }).first()).toBeVisible();
      setPass(report, 'Información General');
    } catch (error) {
      setFail(report, 'Información General', error);
      throw error;
    }
  });

  await test.step('6) Validate Detalles de la Cuenta', async () => {
    try {
      const section = page.getByText(/Detalles de la Cuenta/i).first().locator('xpath=ancestor::*[self::section or self::div][1]');
      await expect(section.getByText(/Cuenta creada/i).first()).toBeVisible();
      await expect(section.getByText(/Estado activo/i).first()).toBeVisible();
      await expect(section.getByText(/Idioma seleccionado/i).first()).toBeVisible();
      setPass(report, 'Detalles de la Cuenta');
    } catch (error) {
      setFail(report, 'Detalles de la Cuenta', error);
      throw error;
    }
  });

  await test.step('7) Validate Tus Negocios', async () => {
    try {
      const section = page.getByText(/Tus Negocios/i).first().locator('xpath=ancestor::*[self::section or self::div][1]');
      await expect(section).toBeVisible();
      await expect(section.getByRole('button', { name: /Agregar Negocio/i }).first()).toBeVisible();
      await expect(section.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
      setPass(report, 'Tus Negocios');
    } catch (error) {
      setFail(report, 'Tus Negocios', error);
      throw error;
    }
  });

  await test.step('8) Validate Términos y Condiciones legal page', async () => {
    try {
      const result = await openAndValidateLegalDocument({
        page,
        context,
        linkNameRegex: /Términos y Condiciones/i,
        headingRegex: /Términos y Condiciones/i,
        screenshotName: '08-terminos-y-condiciones'
      });
      setPass(
        report,
        'Términos y Condiciones',
        `Screenshot: ${result.screenshotPath}; Final URL: ${result.finalUrl}`
      );
    } catch (error) {
      setFail(report, 'Términos y Condiciones', error);
      throw error;
    }
  });

  await test.step('9) Validate Política de Privacidad legal page', async () => {
    try {
      const result = await openAndValidateLegalDocument({
        page,
        context,
        linkNameRegex: /Política de Privacidad/i,
        headingRegex: /Política de Privacidad/i,
        screenshotName: '09-politica-de-privacidad'
      });
      setPass(
        report,
        'Política de Privacidad',
        `Screenshot: ${result.screenshotPath}; Final URL: ${result.finalUrl}`
      );
    } catch (error) {
      setFail(report, 'Política de Privacidad', error);
      throw error;
    }
  });

  await test.step('10) Final report', async () => {
    const orderedReport = {
      Login: report.Login,
      'Mi Negocio menu': report['Mi Negocio menu'],
      'Agregar Negocio modal': report['Agregar Negocio modal'],
      'Administrar Negocios view': report['Administrar Negocios view'],
      'Información General': report['Información General'],
      'Detalles de la Cuenta': report['Detalles de la Cuenta'],
      'Tus Negocios': report['Tus Negocios'],
      'Términos y Condiciones': report['Términos y Condiciones'],
      'Política de Privacidad': report['Política de Privacidad']
    };

    console.table(
      Object.entries(orderedReport).map(([step, result]) => ({
        step,
        status: result.status,
        details: result.details
      }))
    );

    await test.info().attach('saleads-mi-negocio-final-report', {
      body: JSON.stringify(orderedReport, null, 2),
      contentType: 'application/json'
    });

    const failed = Object.entries(orderedReport)
      .filter(([, value]) => value.status !== 'PASS')
      .map(([step, value]) => `${step}: ${value.details}`);

    expect(
      failed,
      `One or more SaleADS Mi Negocio validations failed.\n${failed.join('\n')}`
    ).toEqual([]);
  });
});
