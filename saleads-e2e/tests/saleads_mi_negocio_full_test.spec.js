const { test, expect } = require('@playwright/test');
const fs = require('fs/promises');
const path = require('path');

const GOOGLE_ACCOUNT = 'juanlucasbarbiergarzon@gmail.com';

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function cleanErrorMessage(error) {
  const rawMessage = error && error.message ? error.message : String(error);
  return rawMessage.replace(/\u001b\[[0-9;]*m/g, '').trim();
}

async function ensureDir(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

async function waitUi(page) {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(700);
}

async function firstVisible(locators, timeoutMs = 2_500) {
  for (const locator of locators) {
    try {
      await locator.first().waitFor({ state: 'visible', timeout: timeoutMs });
      return locator.first();
    } catch (_error) {
      // Intentionally try the next candidate locator.
    }
  }

  return null;
}

async function expectVisible(locator, failureMessage) {
  expect(locator, failureMessage).not.toBeNull();
  await expect(locator).toBeVisible();
}

async function screenshot(page, evidenceDir, fileName, fullPage = false) {
  const screenshotPath = path.join(evidenceDir, fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  return screenshotPath;
}

async function maybeChooseGoogleAccount(targetPage) {
  const accountRegex = new RegExp(escapeRegex(GOOGLE_ACCOUNT), 'i');
  const accountLocator = await firstVisible(
    [
      targetPage.getByRole('button', { name: accountRegex }),
      targetPage.getByRole('link', { name: accountRegex }),
      targetPage.getByText(accountRegex)
    ],
    4_000
  );

  if (!accountLocator) {
    return false;
  }

  await accountLocator.click();
  await waitUi(targetPage);
  return true;
}

function createInitialReport() {
  return {
    Login: 'FAIL',
    'Mi Negocio menu': 'FAIL',
    'Agregar Negocio modal': 'FAIL',
    'Administrar Negocios view': 'FAIL',
    'Información General': 'FAIL',
    'Detalles de la Cuenta': 'FAIL',
    'Tus Negocios': 'FAIL',
    'Términos y Condiciones': 'FAIL',
    'Política de Privacidad': 'FAIL'
  };
}

test('saleads_mi_negocio_full_test', async ({ page, context }) => {
  const report = createInitialReport();
  const metadata = {
    legalUrls: {
      terms: null,
      privacy: null
    },
    notes: []
  };

  const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
  const evidenceDir = path.join(
    process.cwd(),
    'artifacts',
    'saleads_mi_negocio_full_test',
    timestamp
  );
  await ensureDir(evidenceDir);

  const startUrl =
    process.env.SALEADS_START_URL ||
    process.env.BASE_URL ||
    process.env.APP_URL ||
    process.env.URL;

  if (startUrl) {
    await page.goto(startUrl, { waitUntil: 'domcontentloaded' });
    await waitUi(page);
  } else if (page.url() === 'about:blank') {
    metadata.notes.push(
      'No start URL was provided and the page is about:blank. Set SALEADS_START_URL (or BASE_URL/APP_URL/URL) or run this test in an environment that pre-opens the login page.'
    );
  }

  // Step 1: Login with Google
  try {
    const googleButton = await firstVisible(
      [
        page.getByRole('button', {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i
        }),
        page.getByRole('link', {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i
        }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i)
      ],
      10_000
    );
    await expectVisible(googleButton, 'Google login button was not found.');

    const popupPromise = context.waitForEvent('page', { timeout: 7_000 }).catch(() => null);
    await googleButton.click();

    const googlePage = await popupPromise;
    if (googlePage) {
      await googlePage.waitForLoadState('domcontentloaded');
      await maybeChooseGoogleAccount(googlePage);

      // Wait briefly for OAuth flow to complete and return to app.
      await googlePage.waitForTimeout(2_000);
      if (!googlePage.isClosed()) {
        await googlePage.close().catch(() => {});
      }
      await page.bringToFront();
    } else {
      await maybeChooseGoogleAccount(page);
    }

    await waitUi(page);
    await expect(
      page.locator('aside, nav').first(),
      'Left sidebar navigation should be visible after login.'
    ).toBeVisible({ timeout: 30_000 });

    await screenshot(page, evidenceDir, '01-dashboard-loaded.png', true);
    report.Login = 'PASS';
  } catch (error) {
    metadata.notes.push(`Login step failed: ${cleanErrorMessage(error)}`);
  }

  // Step 2: Open Mi Negocio menu
  try {
    const negocioSection = await firstVisible(
      [
        page.getByRole('button', { name: /negocio/i }),
        page.getByRole('link', { name: /negocio/i }),
        page.getByText(/^Negocio$/i),
        page.getByText(/Negocio/i)
      ],
      8_000
    );
    await expectVisible(negocioSection, "Section 'Negocio' was not found.");

    const miNegocioOption = await firstVisible(
      [
        page.getByRole('button', { name: /mi negocio/i }),
        page.getByRole('link', { name: /mi negocio/i }),
        page.getByText(/mi negocio/i)
      ],
      3_000
    );
    await expectVisible(miNegocioOption, "Option 'Mi Negocio' was not found.");

    await miNegocioOption.click();
    await waitUi(page);

    await expect(page.getByText(/agregar negocio/i)).toBeVisible();
    await expect(page.getByText(/administrar negocios/i)).toBeVisible();
    await screenshot(page, evidenceDir, '02-mi-negocio-menu-expanded.png', true);
    report['Mi Negocio menu'] = 'PASS';
  } catch (error) {
    metadata.notes.push(`Mi Negocio menu step failed: ${cleanErrorMessage(error)}`);
  }

  // Step 3: Validate Agregar Negocio modal
  try {
    const addBusinessEntry = await firstVisible(
      [
        page.getByRole('button', { name: /agregar negocio/i }),
        page.getByRole('link', { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i)
      ],
      8_000
    );
    await expectVisible(addBusinessEntry, "Option 'Agregar Negocio' was not found.");

    await addBusinessEntry.click();
    await waitUi(page);

    await expect(page.getByText(/crear nuevo negocio/i)).toBeVisible();

    const businessNameField = await firstVisible(
      [
        page.getByLabel(/nombre del negocio/i),
        page.getByPlaceholder(/nombre del negocio/i),
        page.getByRole('textbox', { name: /nombre del negocio/i })
      ],
      5_000
    );
    await expectVisible(businessNameField, "Input 'Nombre del Negocio' was not found.");

    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /cancelar/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /crear negocio/i })).toBeVisible();

    await screenshot(page, evidenceDir, '03-agregar-negocio-modal.png', true);

    await businessNameField.click();
    await businessNameField.fill('Negocio Prueba Automatización');
    await page.getByRole('button', { name: /cancelar/i }).click();
    await waitUi(page);

    report['Agregar Negocio modal'] = 'PASS';
  } catch (error) {
    metadata.notes.push(`Agregar Negocio modal step failed: ${cleanErrorMessage(error)}`);
  }

  // Step 4: Open Administrar Negocios
  try {
    const manageBusinessEntry = await firstVisible(
      [
        page.getByRole('button', { name: /administrar negocios/i }),
        page.getByRole('link', { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i)
      ],
      3_000
    );

    if (!manageBusinessEntry) {
      const miNegocioOption = await firstVisible(
        [
          page.getByRole('button', { name: /mi negocio/i }),
          page.getByRole('link', { name: /mi negocio/i }),
          page.getByText(/mi negocio/i)
        ],
        4_000
      );
      await expectVisible(
        miNegocioOption,
        "Option 'Mi Negocio' was not found while trying to re-expand submenu."
      );
      await miNegocioOption.click();
      await waitUi(page);
    }

    const manageBusinessEntryAfterExpand = await firstVisible(
      [
        page.getByRole('button', { name: /administrar negocios/i }),
        page.getByRole('link', { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i)
      ],
      8_000
    );
    await expectVisible(
      manageBusinessEntryAfterExpand,
      "Option 'Administrar Negocios' was not found."
    );

    await manageBusinessEntryAfterExpand.click();
    await waitUi(page);

    await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible();

    await screenshot(page, evidenceDir, '04-administrar-negocios-view-full.png', true);
    report['Administrar Negocios view'] = 'PASS';
  } catch (error) {
    metadata.notes.push(`Administrar Negocios step failed: ${cleanErrorMessage(error)}`);
  }

  // Step 5: Validate Información General
  try {
    const infoSection = page.locator('section, div').filter({ hasText: /informaci[oó]n general/i }).first();
    await expect(infoSection).toBeVisible({ timeout: 8_000 });

    const infoSectionText = (await infoSection.innerText()) || '';
    const hasEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(infoSectionText);
    const hasNameLikeText =
      /nombre|usuario|user/i.test(infoSectionText) ||
      /[A-Za-zÀ-ÿ]{2,}\s+[A-Za-zÀ-ÿ]{2,}/.test(infoSectionText.replace(/BUSINESS\s*PLAN/i, ''));

    expect(hasEmail, 'User email should be visible in Información General.').toBeTruthy();
    expect(hasNameLikeText, 'User name should be visible in Información General.').toBeTruthy();

    await expect(page.getByText(/business plan/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /cambiar plan/i })).toBeVisible();

    report['Información General'] = 'PASS';
  } catch (error) {
    metadata.notes.push(`Información General validation failed: ${cleanErrorMessage(error)}`);
  }

  // Step 6: Validate Detalles de la Cuenta
  try {
    const accountDetailsSection = page
      .locator('section, div')
      .filter({ hasText: /detalles de la cuenta/i })
      .first();
    await expect(accountDetailsSection).toBeVisible({ timeout: 8_000 });

    await expect(accountDetailsSection.getByText(/cuenta creada/i)).toBeVisible();
    await expect(accountDetailsSection.getByText(/estado activo/i)).toBeVisible();
    await expect(accountDetailsSection.getByText(/idioma seleccionado/i)).toBeVisible();

    report['Detalles de la Cuenta'] = 'PASS';
  } catch (error) {
    metadata.notes.push(`Detalles de la Cuenta validation failed: ${cleanErrorMessage(error)}`);
  }

  // Step 7: Validate Tus Negocios
  try {
    const businessSection = page.locator('section, div').filter({ hasText: /tus negocios/i }).first();
    await expect(businessSection).toBeVisible({ timeout: 8_000 });

    await expect(businessSection.getByRole('button', { name: /agregar negocio/i })).toBeVisible();
    await expect(businessSection.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();

    const listLikeItem = await firstVisible(
      [
        businessSection.locator('li'),
        businessSection.locator('tr'),
        businessSection.locator('[class*="business"]')
      ],
      3_000
    );
    await expectVisible(listLikeItem, 'Business list should be visible in Tus Negocios.');

    report['Tus Negocios'] = 'PASS';
  } catch (error) {
    metadata.notes.push(`Tus Negocios validation failed: ${cleanErrorMessage(error)}`);
  }

  // Helper for legal section links that might open in same tab or a new tab.
  async function validateLegalLink({
    linkNameRegex,
    headingRegex,
    screenshotName,
    reportKey,
    urlField
  }) {
    const legalSection = page.locator('section, div').filter({ hasText: /secci[oó]n legal/i }).first();
    await expect(legalSection).toBeVisible({ timeout: 8_000 });

    const legalLink = await firstVisible(
      [
        legalSection.getByRole('link', { name: linkNameRegex }),
        legalSection.getByRole('button', { name: linkNameRegex }),
        legalSection.getByText(linkNameRegex)
      ],
      6_000
    );
    await expectVisible(legalLink, `Legal link '${linkNameRegex}' was not found.`);

    const appUrlBeforeClick = page.url();
    const popupPromise = context.waitForEvent('page', { timeout: 7_000 }).catch(() => null);
    await legalLink.click();

    const maybePopup = await popupPromise;
    const legalPage = maybePopup || page;
    await legalPage.waitForLoadState('domcontentloaded');
    await waitUi(legalPage);

    await expect(legalPage.getByText(headingRegex)).toBeVisible({ timeout: 15_000 });
    await screenshot(legalPage, evidenceDir, screenshotName, true);

    metadata.legalUrls[urlField] = legalPage.url();

    if (maybePopup) {
      await maybePopup.close().catch(() => {});
      await page.bringToFront();
      await waitUi(page);
    } else {
      const returnedWithBack = await legalPage
        .goBack({ waitUntil: 'domcontentloaded' })
        .then(() => true)
        .catch(() => false);

      if (!returnedWithBack) {
        await legalPage.goto(appUrlBeforeClick, { waitUntil: 'domcontentloaded' });
      }

      await waitUi(page);
    }

    report[reportKey] = 'PASS';
  }

  // Step 8: Validate Términos y Condiciones
  try {
    await validateLegalLink({
      linkNameRegex: /t[eé]rminos y condiciones/i,
      headingRegex: /t[eé]rminos y condiciones/i,
      screenshotName: '08-terminos-y-condiciones.png',
      reportKey: 'Términos y Condiciones',
      urlField: 'terms'
    });
  } catch (error) {
    metadata.notes.push(`Términos y Condiciones validation failed: ${cleanErrorMessage(error)}`);
  }

  // Step 9: Validate Política de Privacidad
  try {
    await validateLegalLink({
      linkNameRegex: /pol[ií]tica de privacidad/i,
      headingRegex: /pol[ií]tica de privacidad/i,
      screenshotName: '09-politica-de-privacidad.png',
      reportKey: 'Política de Privacidad',
      urlField: 'privacy'
    });
  } catch (error) {
    metadata.notes.push(`Política de Privacidad validation failed: ${cleanErrorMessage(error)}`);
  }

  // Step 10: Final report artifact with PASS/FAIL per required field.
  const finalReport = {
    testName: 'saleads_mi_negocio_full_test',
    generatedAt: new Date().toISOString(),
    results: report,
    legalUrls: metadata.legalUrls,
    evidenceDirectory: evidenceDir,
    notes: metadata.notes
  };

  const finalReportPath = path.join(evidenceDir, 'final-report.json');
  await fs.writeFile(finalReportPath, `${JSON.stringify(finalReport, null, 2)}\n`, 'utf8');

  // Print a concise report in the runner logs.
  // eslint-disable-next-line no-console
  console.log(`\nFinal report written to: ${finalReportPath}`);
  // eslint-disable-next-line no-console
  console.table(finalReport.results);
  // eslint-disable-next-line no-console
  console.log(`Términos y Condiciones URL: ${finalReport.legalUrls.terms || 'N/A'}`);
  // eslint-disable-next-line no-console
  console.log(`Política de Privacidad URL: ${finalReport.legalUrls.privacy || 'N/A'}`);

  expect(
    Object.values(report).every((status) => status === 'PASS'),
    `One or more workflow validations failed. See ${finalReportPath}`
  ).toBeTruthy();
});
