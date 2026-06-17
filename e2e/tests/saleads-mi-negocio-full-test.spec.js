const { test, expect } = require('@playwright/test');

const TARGET_GOOGLE_ACCOUNT = 'juanlucasbarbiergarzon@gmail.com';
const DEFAULT_TIMEOUT_MS = 20000;

function createFinalReport() {
  return {
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
}

async function settleUi(page) {
  await page.waitForTimeout(500);
  await page.waitForLoadState('domcontentloaded').catch(() => {});
  await page.waitForLoadState('networkidle', { timeout: 5000 }).catch(() => {});
}

async function firstVisible(locator) {
  const count = await locator.count();

  for (let i = 0; i < count; i += 1) {
    const candidate = locator.nth(i);
    if (await candidate.isVisible().catch(() => false)) {
      return candidate;
    }
  }

  return null;
}

async function waitForAnyVisible(locators, timeoutMs = DEFAULT_TIMEOUT_MS) {
  const start = Date.now();

  while (Date.now() - start < timeoutMs) {
    for (const locator of locators) {
      const visible = await firstVisible(locator);
      if (visible) {
        return visible;
      }
    }

    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  return null;
}

async function resolveClickableByText(page, textRegex) {
  const candidateLocators = [
    page.getByRole('button', { name: textRegex }),
    page.getByRole('link', { name: textRegex }),
    page.getByRole('menuitem', { name: textRegex }),
    page.getByRole('tab', { name: textRegex }),
    page.getByText(textRegex),
  ];

  const visible = await waitForAnyVisible(candidateLocators);

  if (!visible) {
    throw new Error(`No visible clickable element was found for pattern: ${textRegex}`);
  }

  return visible;
}

async function clickByVisibleText(page, textRegex) {
  const target = await resolveClickableByText(page, textRegex);
  await target.click();
  await settleUi(page);
}

async function openMiNegocioMenu(page) {
  const agregarVisible = await firstVisible(page.getByText(/agregar negocio/i));
  const administrarVisible = await firstVisible(page.getByText(/administrar negocios/i));
  if (agregarVisible && administrarVisible) {
    return;
  }

  try {
    await clickByVisibleText(page, /mi negocio/i);
  } catch (_error) {
    await clickByVisibleText(page, /negocio/i);
    await clickByVisibleText(page, /mi negocio/i);
  }
}

async function capture(page, testInfo, fileName, fullPage = false) {
  await page.screenshot({
    path: testInfo.outputPath(fileName),
    fullPage,
  });
}

async function validateLegalPageAndReturn({
  appPage,
  context,
  linkRegex,
  headingRegex,
  screenshotName,
  testInfo,
}) {
  const popupPromise = context.waitForEvent('page', { timeout: 8000 }).catch(() => null);

  await clickByVisibleText(appPage, linkRegex);

  const popup = await popupPromise;
  const targetPage = popup || appPage;
  await targetPage.waitForLoadState('domcontentloaded', { timeout: 45000 });
  await settleUi(targetPage);

  const heading = await waitForAnyVisible([
    targetPage.getByRole('heading', { name: headingRegex }),
    targetPage.getByText(headingRegex),
  ]);

  if (!heading) {
    throw new Error(`Expected legal heading not found for ${headingRegex}`);
  }

  const bodyText = await targetPage.locator('body').innerText();
  if (bodyText.trim().length < 120) {
    throw new Error('Expected visible legal content text, but content was too short.');
  }

  await capture(targetPage, testInfo, screenshotName, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close().catch(() => {});
    await appPage.bringToFront();
    await settleUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: 'domcontentloaded' }).catch(() => {});
    await settleUi(appPage);
  }

  return finalUrl;
}

test('saleads_mi_negocio_full_test', async ({ page, context }, testInfo) => {
  const finalReport = createFinalReport();
  const failures = [];
  const evidence = {
    terminosUrl: null,
    privacidadUrl: null,
  };

  const runStep = async (name, action) => {
    try {
      await action();
      finalReport[name] = 'PASS';
    } catch (error) {
      finalReport[name] = 'FAIL';
      failures.push(`${name}: ${error.message}`);
    }
  };

  await runStep('Login', async () => {
    if (page.url() === 'about:blank') {
      const loginUrl = process.env.SALEADS_URL;
      if (!loginUrl) {
        throw new Error('Set SALEADS_URL when the browser is not already on the SaleADS login page.');
      }

      await page.goto(loginUrl, { waitUntil: 'domcontentloaded' });
      await settleUi(page);
    }

    const loginButton = await resolveClickableByText(
      page,
      /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
    );

    const popupPromise = context.waitForEvent('page', { timeout: 8000 }).catch(() => null);
    await loginButton.click();
    await settleUi(page);

    const googlePopup = await popupPromise;
    if (googlePopup) {
      await googlePopup.waitForLoadState('domcontentloaded', { timeout: 45000 });
      await settleUi(googlePopup);

      const accountOption = await waitForAnyVisible(
        [
          googlePopup.getByText(new RegExp(TARGET_GOOGLE_ACCOUNT, 'i')),
          googlePopup.getByRole('button', { name: new RegExp(TARGET_GOOGLE_ACCOUNT, 'i') }),
          googlePopup.getByRole('link', { name: new RegExp(TARGET_GOOGLE_ACCOUNT, 'i') }),
        ],
        12000,
      );

      if (accountOption) {
        await accountOption.click();
        await settleUi(googlePopup);
      }
    } else {
      const accountOptionOnPage = await waitForAnyVisible(
        [
          page.getByText(new RegExp(TARGET_GOOGLE_ACCOUNT, 'i')),
          page.getByRole('button', { name: new RegExp(TARGET_GOOGLE_ACCOUNT, 'i') }),
          page.getByRole('link', { name: new RegExp(TARGET_GOOGLE_ACCOUNT, 'i') }),
        ],
        5000,
      );

      if (accountOptionOnPage) {
        await accountOptionOnPage.click();
        await settleUi(page);
      }
    }

    const sidebar = await waitForAnyVisible(
      [
        page.locator('aside'),
        page.getByRole('navigation'),
        page.getByText(/mi negocio|negocio/i),
      ],
      60000,
    );

    if (!sidebar) {
      throw new Error('Main application interface / left sidebar was not visible after login.');
    }

    await capture(page, testInfo, '01-dashboard-loaded.png', true);
  });

  await runStep('Mi Negocio menu', async () => {
    await openMiNegocioMenu(page);
    await expect(page.getByText(/agregar negocio/i)).toBeVisible();
    await expect(page.getByText(/administrar negocios/i)).toBeVisible();
    await capture(page, testInfo, '02-mi-negocio-expanded-menu.png');
  });

  await runStep('Agregar Negocio modal', async () => {
    await clickByVisibleText(page, /agregar negocio/i);
    await expect(page.getByText(/crear nuevo negocio/i)).toBeVisible();
    await expect(page.getByLabel(/nombre del negocio/i)).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /cancelar/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /crear negocio/i })).toBeVisible();
    await capture(page, testInfo, '03-agregar-negocio-modal.png');

    const nombreInput = page.getByLabel(/nombre del negocio/i);
    await nombreInput.click();
    await nombreInput.fill('Negocio Prueba Automatizacion');
    await clickByVisibleText(page, /cancelar/i);
  });

  await runStep('Administrar Negocios view', async () => {
    await openMiNegocioMenu(page);
    await clickByVisibleText(page, /administrar negocios/i);

    await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible();

    await capture(page, testInfo, '04-administrar-negocios-page.png', true);
  });

  await runStep('Información General', async () => {
    const generalSection = await waitForAnyVisible([
      page.locator('section').filter({ hasText: /informaci[oó]n general/i }),
      page.locator('div').filter({ hasText: /informaci[oó]n general/i }),
    ]);

    if (!generalSection) {
      throw new Error('Información General section was not found.');
    }

    const generalText = await generalSection.innerText();
    if (!/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(generalText)) {
      throw new Error('User email was not visible in Información General.');
    }

    if (!/[A-Za-zÁÉÍÓÚÑ][A-Za-zÁÉÍÓÚÑ'\- ]{1,}/.test(generalText)) {
      throw new Error('User name was not visible in Información General.');
    }

    await expect(generalSection.getByText(/business plan/i)).toBeVisible();
    await expect(generalSection.getByRole('button', { name: /cambiar plan/i })).toBeVisible();
  });

  await runStep('Detalles de la Cuenta', async () => {
    const detailsSection = await waitForAnyVisible([
      page.locator('section').filter({ hasText: /detalles de la cuenta/i }),
      page.locator('div').filter({ hasText: /detalles de la cuenta/i }),
    ]);

    if (!detailsSection) {
      throw new Error('Detalles de la Cuenta section was not found.');
    }

    await expect(detailsSection.getByText(/cuenta creada/i)).toBeVisible();
    await expect(detailsSection.getByText(/estado activo/i)).toBeVisible();
    await expect(detailsSection.getByText(/idioma seleccionado/i)).toBeVisible();
  });

  await runStep('Tus Negocios', async () => {
    const businessSection = await waitForAnyVisible([
      page.locator('section').filter({ hasText: /tus negocios/i }),
      page.locator('div').filter({ hasText: /tus negocios/i }),
    ]);

    if (!businessSection) {
      throw new Error('Tus Negocios section was not found.');
    }

    await expect(businessSection.getByText(/agregar negocio/i)).toBeVisible();
    await expect(businessSection.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();

    const businessSectionText = await businessSection.innerText();
    if (businessSectionText.trim().length < 40) {
      throw new Error('Business list does not look visible in Tus Negocios section.');
    }
  });

  await runStep('Términos y Condiciones', async () => {
    evidence.terminosUrl = await validateLegalPageAndReturn({
      appPage: page,
      context,
      linkRegex: /t[ée]rminos y condiciones/i,
      headingRegex: /t[ée]rminos y condiciones/i,
      screenshotName: '05-terminos-y-condiciones.png',
      testInfo,
    });
  });

  await runStep('Política de Privacidad', async () => {
    evidence.privacidadUrl = await validateLegalPageAndReturn({
      appPage: page,
      context,
      linkRegex: /pol[ií]tica de privacidad/i,
      headingRegex: /pol[ií]tica de privacidad/i,
      screenshotName: '06-politica-de-privacidad.png',
      testInfo,
    });
  });

  await testInfo.attach('final-report.json', {
    body: JSON.stringify(
      {
        report: finalReport,
        evidence,
        failures,
      },
      null,
      2,
    ),
    contentType: 'application/json',
  });

  expect(
    failures,
    `One or more SaleADS Mi Negocio validations failed.\n${failures.join('\n')}`,
  ).toEqual([]);
});
