const { test, expect } = require('@playwright/test');
const fs = require('fs/promises');

const GOOGLE_ACCOUNT_EMAIL = 'juanlucasbarbiergarzon@gmail.com';
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

test('saleads_mi_negocio_full_test', async ({ page, context }, testInfo) => {
  const statusReport = Object.fromEntries(REPORT_FIELDS.map((field) => [field, 'FAIL']));
  const legalUrls = {};
  const failures = [];

  const asMessage = (error) => (error instanceof Error ? error.message : String(error));

  const waitForUiLoad = async (targetPage = page) => {
    await targetPage.waitForLoadState('domcontentloaded', { timeout: 15000 }).catch(() => {});
    await targetPage.waitForLoadState('networkidle', { timeout: 12000 }).catch(() => {});
    await targetPage.waitForTimeout(500);
  };

  const maybeGotoLogin = async () => {
    const configuredUrl = process.env.SALEADS_LOGIN_URL || process.env.BASE_URL || '';
    if (/^about:blank/i.test(page.url()) && configuredUrl) {
      await page.goto(configuredUrl, { waitUntil: 'domcontentloaded' });
    }
    await waitForUiLoad(page);
  };

  const firstVisible = async (locators) => {
    for (const locator of locators) {
      const candidate = locator.first();
      if ((await candidate.count()) > 0 && (await candidate.isVisible().catch(() => false))) {
        return candidate;
      }
    }
    return null;
  };

  const checkpoint = async (fileName, targetPage = page, fullPage = false) => {
    await waitForUiLoad(targetPage);
    await targetPage.screenshot({
      path: testInfo.outputPath(fileName),
      fullPage
    });
  };

  const runValidation = async (fieldName, action) => {
    try {
      await action();
      statusReport[fieldName] = 'PASS';
    } catch (error) {
      failures.push(`${fieldName}: ${asMessage(error)}`);
    }
  };

  const clickAndWait = async (locator, targetPage = page) => {
    await expect(locator).toBeVisible();
    await locator.click();
    await waitForUiLoad(targetPage);
  };

  const expectVisibleByRoleOrText = async (targetPage, textRegex) => {
    const heading = targetPage.getByRole('heading', { name: textRegex }).first();
    if ((await heading.count()) > 0) {
      await expect(heading).toBeVisible();
      return;
    }

    const text = targetPage.getByText(textRegex).first();
    await expect(text).toBeVisible();
  };

  const openLegalPage = async (linkRegex, headingRegex, screenshotName, reportKey) => {
    const appPage = page;
    const legalLink = await firstVisible([
      appPage.getByRole('link', { name: linkRegex }),
      appPage.getByText(linkRegex),
      appPage.locator('button', { hasText: linkRegex })
    ]);
    expect(legalLink, `No legal link found for ${linkRegex}`).not.toBeNull();

    const popupPromise = context.waitForEvent('page', { timeout: 8000 }).catch(() => null);
    await legalLink.click();
    await waitForUiLoad(appPage);

    const popup = await popupPromise;
    const legalPage = popup || appPage;
    if (popup) {
      await waitForUiLoad(legalPage);
    }

    await expectVisibleByRoleOrText(legalPage, headingRegex);

    const legalContent = legalPage.locator('main, article, body').first();
    await expect(legalContent).toContainText(/t[eé]rminos|condiciones|pol[ií]tica|privacidad/i);

    legalUrls[reportKey] = legalPage.url();
    await checkpoint(screenshotName, legalPage, true);

    if (popup) {
      await popup.close();
      await appPage.bringToFront();
      await waitForUiLoad(appPage);
    } else {
      await appPage.goBack({ waitUntil: 'domcontentloaded' }).catch(() => {});
      await waitForUiLoad(appPage);
    }
  };

  await maybeGotoLogin();

  await runValidation('Login', async () => {
    const loginButton = await firstVisible([
      page.getByRole('button', { name: /sign in with google|continuar con google|iniciar con google|google/i }),
      page.getByText(/sign in with google|continuar con google|iniciar con google/i),
      page.getByRole('button', { name: /iniciar sesi[oó]n|ingresar|login|acceder/i })
    ]);
    expect(loginButton, 'Login button is not visible').not.toBeNull();

    const popupPromise = context.waitForEvent('page', { timeout: 7000 }).catch(() => null);
    await clickAndWait(loginButton);
    const popup = await popupPromise;

    const googlePage = popup || (/accounts\.google\.com/i.test(page.url()) ? page : null);
    if (googlePage) {
      await waitForUiLoad(googlePage);
      const accountLocator = await firstVisible([
        googlePage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }),
        googlePage.getByRole('link', { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, 'i') }),
        googlePage.getByRole('button', { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, 'i') })
      ]);

      if (accountLocator) {
        await clickAndWait(accountLocator, googlePage);
      }

      if (popup) {
        await popup.waitForEvent('close', { timeout: 45000 }).catch(() => {});
        await page.bringToFront();
      }
    }

    await waitForUiLoad(page);

    const leftSidebar = await firstVisible([
      page.locator('aside'),
      page.getByRole('navigation'),
      page.locator('nav')
    ]);
    expect(leftSidebar, 'Left sidebar was not visible after login').not.toBeNull();
    await expect(leftSidebar).toBeVisible();

    await checkpoint('01-dashboard-loaded.png', page, true);
  });

  await runValidation('Mi Negocio menu', async () => {
    const negocioSection = await firstVisible([
      page.getByText(/^Negocio$/i),
      page.getByRole('button', { name: /negocio/i }),
      page.getByRole('link', { name: /negocio/i })
    ]);
    expect(negocioSection, 'Negocio section was not visible').not.toBeNull();
    await clickAndWait(negocioSection);

    const miNegocioOption = await firstVisible([
      page.getByText(/mi negocio/i),
      page.getByRole('button', { name: /mi negocio/i }),
      page.getByRole('link', { name: /mi negocio/i })
    ]);
    expect(miNegocioOption, 'Mi Negocio option was not visible').not.toBeNull();
    await clickAndWait(miNegocioOption);

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();

    await checkpoint('02-mi-negocio-expanded.png');
  });

  await runValidation('Agregar Negocio modal', async () => {
    const addBusinessMenuItem = await firstVisible([
      page.getByRole('button', { name: /agregar negocio/i }),
      page.getByRole('link', { name: /agregar negocio/i }),
      page.getByText(/agregar negocio/i)
    ]);
    expect(addBusinessMenuItem, 'Agregar Negocio menu item was not visible').not.toBeNull();
    await clickAndWait(addBusinessMenuItem);

    await expect(page.getByText(/crear nuevo negocio/i).first()).toBeVisible();
    const businessNameInput = page.getByLabel(/nombre del negocio/i).first();
    await expect(businessNameInput).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /crear negocio/i }).first()).toBeVisible();

    await checkpoint('03-agregar-negocio-modal.png');

    await businessNameInput.click();
    await businessNameInput.fill('Negocio Prueba Automatizacion');
    await clickAndWait(page.getByRole('button', { name: /cancelar/i }).first());
  });

  await runValidation('Administrar Negocios view', async () => {
    const administrarLink = await firstVisible([
      page.getByRole('link', { name: /administrar negocios/i }),
      page.getByRole('button', { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i)
    ]);
    if (!administrarLink) {
      const miNegocioOption = await firstVisible([
        page.getByText(/mi negocio/i),
        page.getByRole('button', { name: /mi negocio/i }),
        page.getByRole('link', { name: /mi negocio/i })
      ]);
      expect(miNegocioOption, 'Mi Negocio option was not visible for re-expansion').not.toBeNull();
      await clickAndWait(miNegocioOption);
    }

    const adminTarget = await firstVisible([
      page.getByRole('link', { name: /administrar negocios/i }),
      page.getByRole('button', { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i)
    ]);
    expect(adminTarget, 'Administrar Negocios option was not visible').not.toBeNull();
    await clickAndWait(adminTarget);

    await expectVisibleByRoleOrText(page, /informaci[oó]n general/i);
    await expectVisibleByRoleOrText(page, /detalles de la cuenta/i);
    await expectVisibleByRoleOrText(page, /tus negocios/i);
    await expectVisibleByRoleOrText(page, /secci[oó]n legal/i);

    await checkpoint('04-administrar-negocios-view.png', page, true);
  });

  await runValidation('Información General', async () => {
    await expect(page.locator('body')).toContainText(/@/);
    await expect(page.locator('body')).toContainText(/business plan/i);
    await expect(page.getByRole('button', { name: /cambiar plan/i }).first()).toBeVisible();
  });

  await runValidation('Detalles de la Cuenta', async () => {
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible();
  });

  await runValidation('Tus Negocios', async () => {
    await expectVisibleByRoleOrText(page, /tus negocios/i);
    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();
  });

  await runValidation('Términos y Condiciones', async () => {
    await openLegalPage(
      /t[eé]rminos y condiciones/i,
      /t[eé]rminos y condiciones/i,
      '05-terminos-y-condiciones.png',
      'terminosYCondicionesUrl'
    );
  });

  await runValidation('Política de Privacidad', async () => {
    await openLegalPage(
      /pol[ií]tica de privacidad/i,
      /pol[ií]tica de privacidad/i,
      '06-politica-de-privacidad.png',
      'politicaDePrivacidadUrl'
    );
  });

  const finalReport = {
    testName: 'saleads_mi_negocio_full_test',
    generatedAt: new Date().toISOString(),
    results: statusReport,
    legalUrls,
    failures
  };

  const reportPath = testInfo.outputPath('final-report.json');
  await fs.writeFile(reportPath, `${JSON.stringify(finalReport, null, 2)}\n`, 'utf8');
  await testInfo.attach('saleads-mi-negocio-final-report', {
    path: reportPath,
    contentType: 'application/json'
  });

  expect(failures, failures.join('\n')).toEqual([]);
});
