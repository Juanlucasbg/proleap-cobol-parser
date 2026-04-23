const fs = require('fs/promises');
const path = require('path');
const { test, expect } = require('@playwright/test');

const SCREENSHOT_DIR = 'screenshots/saleads_mi_negocio_full_test';
const DEFAULT_GOOGLE_ACCOUNT_EMAIL = 'juanlucasbarbiergarzon@gmail.com';
const GOOGLE_ACCOUNT_EMAIL = process.env.GOOGLE_ACCOUNT_EMAIL || DEFAULT_GOOGLE_ACCOUNT_EMAIL;

function normalize(text) {
  return (text || '')
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()
    .trim();
}

function buildAccentInsensitivePattern(rawLabel) {
  const escaped = rawLabel.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const replacements = [
    ['a', '[aáàäâã]'],
    ['e', '[eéèëê]'],
    ['i', '[iíìïî]'],
    ['o', '[oóòöôõ]'],
    ['u', '[uúùüû]'],
    ['n', '[nñ]'],
  ];
  let patternText = escaped;
  for (const [plain, group] of replacements) {
    patternText = patternText
      .replace(new RegExp(plain, 'gi'), (match) => (match === plain.toUpperCase() ? group.toUpperCase() : group));
  }
  return new RegExp(patternText, 'i');
}

async function waitForUi(page) {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function ensureArtifactsDirectories() {
  await fs.mkdir(path.resolve(process.cwd(), SCREENSHOT_DIR), { recursive: true });
  await fs.mkdir(path.resolve(process.cwd(), 'artifacts'), { recursive: true });
}

async function clickAndWait(locator, page) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function getFirstVisibleLocator(candidates) {
  for (const locator of candidates) {
    if ((await locator.count()) > 0) {
      const first = locator.first();
      if (await first.isVisible()) {
        return first;
      }
    }
  }
  return null;
}

async function hasVisibleLocator(candidates) {
  return (await getFirstVisibleLocator(candidates)) !== null;
}

async function tryFindTextControl(page, label) {
  const regex = buildAccentInsensitivePattern(label);
  const candidates = [
    page.getByRole('button', { name: regex }),
    page.getByRole('link', { name: regex }),
    page.getByRole('menuitem', { name: regex }),
    page.getByText(regex),
  ];
  return getFirstVisibleLocator(candidates);
}

async function findTextControl(page, label) {
  const found = await tryFindTextControl(page, label);
  if (!found) {
    throw new Error(`Could not find visible control with label "${label}".`);
  }
  return found;
}

async function maybeClickByRole(page, role, names) {
  for (const name of names) {
    const locator = page.getByRole(role, { name, exact: false });
    if (await locator.count()) {
      await clickAndWait(locator.first(), page);
      return true;
    }
  }
  return false;
}

async function ensureLoggedIn(page) {
  const signInClicked =
    (await maybeClickByRole(page, 'button', [
      'Sign in with Google',
      'Iniciar sesión con Google',
      'Continuar con Google',
      'Google',
    ])) ||
    (await maybeClickByRole(page, 'link', [
      'Sign in with Google',
      'Iniciar sesión con Google',
      'Continuar con Google',
      'Google',
    ]));

  if (!signInClicked) {
    const alreadyLoggedIn = await hasVisibleLocator([
      page.locator('aside'),
      page.locator('nav'),
      page.getByRole('navigation'),
    ]);
    if (!alreadyLoggedIn) {
      throw new Error('No Google login button/link was found on the login page.');
    }
    return;
  }

  // Handle account chooser if present.
  const googleEmailOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false });
  if (await googleEmailOption.count()) {
    await clickAndWait(googleEmailOption.first(), page);
  }

  await waitForUi(page);
}

function makeStepRecorder(testInfo) {
  const statuses = {
    Login: 'FAIL',
    'Mi Negocio menu': 'FAIL',
    'Agregar Negocio modal': 'FAIL',
    'Administrar Negocios view': 'FAIL',
    'Información General': 'FAIL',
    'Detalles de la Cuenta': 'FAIL',
    'Tus Negocios': 'FAIL',
    'Términos y Condiciones': 'FAIL',
    'Política de Privacidad': 'FAIL',
  };

  const legalUrls = {
    'Términos y Condiciones': '',
    'Política de Privacidad': '',
  };

  return {
    pass(stepName) {
      statuses[stepName] = 'PASS';
    },
    fail(stepName, error) {
      statuses[stepName] = `FAIL - ${error.message}`;
    },
    setLegalUrl(stepName, url) {
      legalUrls[stepName] = url;
    },
    async attachFinalReport() {
      const report = {
        test: 'saleads_mi_negocio_full_test',
        statusByStep: statuses,
        legalUrls,
      };
      const artifactsDir = path.resolve(process.cwd(), 'artifacts');
      const reportPath = path.join(artifactsDir, 'final-report.json');
      await fs.mkdir(artifactsDir, { recursive: true });
      await fs.writeFile(reportPath, JSON.stringify(report, null, 2), 'utf8');

      await testInfo.attach('final-report.json', {
        path: reportPath,
        contentType: 'application/json',
      });
    },
  };
}

async function validateSidebar(page) {
  const sidebarCandidates = [
    page.locator('aside'),
    page.locator('nav'),
    page.getByRole('navigation'),
  ];

  for (const candidate of sidebarCandidates) {
    if (await candidate.count()) {
      await expect(candidate.first()).toBeVisible();
      return;
    }
  }

  throw new Error('Left sidebar navigation was not found.');
}

async function expandMiNegocioMenu(page) {
  const negocioSection = await findTextControl(page, 'Negocio');
  await expect(negocioSection).toBeVisible();

  const miNegocioOption = await findTextControl(page, 'Mi Negocio');
  await clickAndWait(miNegocioOption, page);

  await expect(await findTextControl(page, 'Agregar Negocio')).toBeVisible();
  await expect(await findTextControl(page, 'Administrar Negocios')).toBeVisible();
}

async function withPossiblePopup(page, action, fallbackPage = page) {
  const context = page.context();
  const popupPromise = context.waitForEvent('page', { timeout: 5000 }).catch(() => null);
  await action();
  const popup = await popupPromise;
  if (popup) {
    await popup.waitForLoadState('domcontentloaded');
    await popup.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
    return popup;
  }

  await waitForUi(fallbackPage);
  return fallbackPage;
}

async function clickLegalLinkAndValidate(page, labelRegex, headingRegex, screenshotName) {
  const appPage = page;
  const legalPage = await withPossiblePopup(page, async () => {
    const legalLink = page.getByText(labelRegex).first();
    await expect(legalLink).toBeVisible();
    await legalLink.click();
  });

  const heading = await getFirstVisibleLocator([
    legalPage.getByRole('heading', { name: headingRegex }),
    legalPage.getByText(headingRegex),
  ]);
  if (!heading) {
    throw new Error(`Heading "${headingRegex}" not found in legal page.`);
  }
  await expect(heading).toBeVisible();
  await expect(legalPage.locator('main, article, body').first()).toContainText(/\S+/);
  await legalPage.screenshot({
    path: `${SCREENSHOT_DIR}/${screenshotName}.png`,
    fullPage: true,
  });

  const finalUrl = legalPage.url();
  if (legalPage !== appPage) {
    await legalPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  }

  return finalUrl;
}

test.describe('saleads_mi_negocio_full_test', () => {
  test('Login with Google and validate full Mi Negocio workflow', async ({ page }, testInfo) => {
    const recorder = makeStepRecorder(testInfo);
    try {
      await ensureArtifactsDirectories();

      await test.step('Step 1 - Login with Google', async () => {
        try {
          const baseUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || process.env.BASE_URL;
          if (baseUrl) {
            await page.goto(baseUrl, { waitUntil: 'domcontentloaded' });
          }
          await waitForUi(page);
          await ensureLoggedIn(page);

          await validateSidebar(page);
          await page.screenshot({
            path: `${SCREENSHOT_DIR}/01-dashboard-loaded.png`,
            fullPage: true,
          });
          recorder.pass('Login');
        } catch (error) {
          recorder.fail('Login', error);
          throw error;
        }
      });

      await test.step('Step 2 - Open Mi Negocio menu', async () => {
        try {
          await expandMiNegocioMenu(page);
          await page.screenshot({
            path: `${SCREENSHOT_DIR}/02-mi-negocio-expanded.png`,
            fullPage: true,
          });
          recorder.pass('Mi Negocio menu');
        } catch (error) {
          recorder.fail('Mi Negocio menu', error);
          throw error;
        }
      });

      await test.step('Step 3 - Validate Agregar Negocio modal', async () => {
        try {
          const agregarNegocio = await findTextControl(page, 'Agregar Negocio');
          await clickAndWait(agregarNegocio, page);

          await expect(await findTextControl(page, 'Crear Nuevo Negocio')).toBeVisible();
          const businessNameInput = await getFirstVisibleLocator([
            page.getByLabel(buildAccentInsensitivePattern('Nombre del Negocio')),
            page.getByPlaceholder(buildAccentInsensitivePattern('Nombre del Negocio')),
            page.locator('input[name*="negocio" i], input[id*="negocio" i]'),
          ]);
          if (!businessNameInput) {
            throw new Error('Input "Nombre del Negocio" was not found.');
          }
          await expect(businessNameInput).toBeVisible();
          await expect(await findTextControl(page, 'Tienes 2 de 3 negocios')).toBeVisible();
          await expect(await findTextControl(page, 'Cancelar')).toBeVisible();
          await expect(await findTextControl(page, 'Crear Negocio')).toBeVisible();

          await businessNameInput.fill('Negocio Prueba Automatizacion');
          await page.screenshot({
            path: `${SCREENSHOT_DIR}/03-crear-negocio-modal.png`,
            fullPage: true,
          });
          await clickAndWait(await findTextControl(page, 'Cancelar'), page);

          recorder.pass('Agregar Negocio modal');
        } catch (error) {
          recorder.fail('Agregar Negocio modal', error);
          throw error;
        }
      });

      await test.step('Step 4 - Open Administrar Negocios', async () => {
        try {
          let adminOption = await tryFindTextControl(page, 'Administrar Negocios');
          if (!adminOption) {
            await expandMiNegocioMenu(page);
            adminOption = await findTextControl(page, 'Administrar Negocios');
          }
          await clickAndWait(adminOption, page);

          await expect(await findTextControl(page, 'Información General')).toBeVisible();
          await expect(await findTextControl(page, 'Detalles de la Cuenta')).toBeVisible();
          await expect(await findTextControl(page, 'Tus Negocios')).toBeVisible();
          await expect(await findTextControl(page, 'Sección Legal')).toBeVisible();

          await page.screenshot({
            path: `${SCREENSHOT_DIR}/04-administrar-negocios-view.png`,
            fullPage: true,
          });
          recorder.pass('Administrar Negocios view');
        } catch (error) {
          recorder.fail('Administrar Negocios view', error);
          throw error;
        }
      });

      await test.step('Step 5 - Validate Informacion General', async () => {
        try {
          const pageText = normalize(await page.locator('body').innerText());
          expect(pageText).toContain('business plan');
          await expect(await findTextControl(page, 'Cambiar Plan')).toBeVisible();

          // Generic validations for visible user name and email.
          await expect(page.getByText(/@/).first()).toBeVisible();
          const nonEmptyName = page.locator('h1, h2, h3, [data-testid*=name], [class*=name]').filter({ hasText: /\S+/ });
          await expect(nonEmptyName.first()).toBeVisible();

          recorder.pass('Información General');
        } catch (error) {
          recorder.fail('Información General', error);
          throw error;
        }
      });

      await test.step('Step 6 - Validate Detalles de la Cuenta', async () => {
        try {
          const pageText = normalize(await page.locator('body').innerText());
          expect(pageText).toContain('cuenta creada');
          expect(pageText).toContain('estado activo');
          expect(pageText).toContain('idioma seleccionado');
          recorder.pass('Detalles de la Cuenta');
        } catch (error) {
          recorder.fail('Detalles de la Cuenta', error);
          throw error;
        }
      });

      await test.step('Step 7 - Validate Tus Negocios', async () => {
        try {
          await expect(await findTextControl(page, 'Tus Negocios')).toBeVisible();
          await expect(await findTextControl(page, 'Agregar Negocio')).toBeVisible();
          await expect(await findTextControl(page, 'Tienes 2 de 3 negocios')).toBeVisible();
          recorder.pass('Tus Negocios');
        } catch (error) {
          recorder.fail('Tus Negocios', error);
          throw error;
        }
      });

      await test.step('Step 8 - Validate Terminos y Condiciones', async () => {
        try {
          const finalUrl = await clickLegalLinkAndValidate(
            page,
            buildAccentInsensitivePattern('Términos y Condiciones'),
            buildAccentInsensitivePattern('Términos y Condiciones'),
            '08-terminos-y-condiciones'
          );
          recorder.setLegalUrl('Términos y Condiciones', finalUrl);
          recorder.pass('Términos y Condiciones');
        } catch (error) {
          recorder.fail('Términos y Condiciones', error);
          throw error;
        }
      });

      await test.step('Step 9 - Validate Politica de Privacidad', async () => {
        try {
          const finalUrl = await clickLegalLinkAndValidate(
            page,
            buildAccentInsensitivePattern('Política de Privacidad'),
            buildAccentInsensitivePattern('Política de Privacidad'),
            '09-politica-de-privacidad'
          );
          recorder.setLegalUrl('Política de Privacidad', finalUrl);
          recorder.pass('Política de Privacidad');
        } catch (error) {
          recorder.fail('Política de Privacidad', error);
          throw error;
        }
      });
    } finally {
      await recorder.attachFinalReport();
    }
  });
});
