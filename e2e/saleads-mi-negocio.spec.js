const { test, expect } = require('@playwright/test');
const fs = require('fs');

const FINAL_REPORT_FIELDS = [
  'Login',
  'Mi Negocio menu',
  'Agregar Negocio modal',
  'Administrar Negocios view',
  'Informacion General',
  'Detalles de la Cuenta',
  'Tus Negocios',
  'Terminos y Condiciones',
  'Politica de Privacidad'
];

const EMAIL_TO_SELECT = 'juanlucasbarbiergarzon@gmail.com';
const UI_WAIT_MS = Number(process.env.SALEADS_UI_WAIT_MS || 1200);

function emptyReport() {
  return Object.fromEntries(FINAL_REPORT_FIELDS.map((field) => [field, 'FAIL']));
}

function slugify(value) {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/(^-|-$)/g, '')
    .slice(0, 80);
}

async function waitForUi(page) {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(UI_WAIT_MS);
}

async function firstVisible(candidates, timeoutMs = 15000) {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeoutMs) {
    for (const locator of candidates) {
      const candidate = locator.first();
      const visible = await candidate.isVisible().catch(() => false);
      if (visible) {
        return candidate;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }
  throw new Error('Could not find a visible element among expected candidates.');
}

async function captureCheckpoint(page, testInfo, label, options = {}) {
  const { fullPage = false } = options;
  const fileName = `${Date.now()}-${slugify(label)}.png`;
  const filePath = testInfo.outputPath(fileName);

  await page.screenshot({ path: filePath, fullPage });
  await testInfo.attach(label, {
    path: filePath,
    contentType: 'image/png'
  });
}

async function clickAndWait(locator, page) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function ensureLoginPage(page) {
  const startUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || process.env.BASE_URL;

  if (startUrl) {
    await page.goto(startUrl, { waitUntil: 'domcontentloaded' });
    await waitForUi(page);
    return;
  }

  if (page.url() === 'about:blank') {
    throw new Error(
      'Browser started on about:blank. Provide SALEADS_LOGIN_URL or SALEADS_BASE_URL so the test can open the current environment login page.'
    );
  }
}

async function ensureMiNegocioExpanded(page) {
  const agregarNegocio = page.getByText(/Agregar Negocio/i).first();
  const administrarNegocios = page.getByText(/Administrar Negocios/i).first();

  const submenuVisible =
    (await agregarNegocio.isVisible().catch(() => false)) &&
    (await administrarNegocios.isVisible().catch(() => false));

  if (submenuVisible) {
    return;
  }

  const trigger = await firstVisible([
    page.getByRole('button', { name: /Mi Negocio/i }),
    page.getByRole('link', { name: /Mi Negocio/i }),
    page.getByText(/Mi Negocio/i),
    page.getByRole('button', { name: /Negocio/i }),
    page.getByRole('link', { name: /Negocio/i }),
    page.getByText(/^Negocio$/i)
  ]);

  await clickAndWait(trigger, page);
  await expect(agregarNegocio).toBeVisible();
  await expect(administrarNegocios).toBeVisible();
}

async function findSectionByHeading(page, headingRegex) {
  const heading = await firstVisible([
    page.getByRole('heading', { name: headingRegex }),
    page.getByText(headingRegex)
  ]);

  const section = heading.locator('xpath=ancestor::section[1]');
  const hasSection = await section.count();
  if (hasSection > 0) {
    return section.first();
  }

  const card = heading.locator('xpath=ancestor::div[1]');
  return card.first();
}

async function openLegalDocument(page, testInfo, linkRegex, headingRegex, screenshotLabel) {
  const link = await firstVisible([
    page.getByRole('link', { name: linkRegex }),
    page.getByText(linkRegex)
  ]);

  const context = page.context();
  const popupPromise = context.waitForEvent('page', { timeout: 8000 }).catch(() => null);
  const navigationPromise = page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 8000 }).catch(() => null);
  const originalUrl = page.url();

  await link.click();

  const popup = await popupPromise;
  const targetPage = popup || page;
  if (popup) {
    await popup.waitForLoadState('domcontentloaded');
  } else {
    await navigationPromise;
  }
  await waitForUi(targetPage);

  const headingVisible = await targetPage
    .getByRole('heading', { name: headingRegex })
    .first()
    .isVisible()
    .catch(() => false);

  if (headingVisible) {
    await expect(targetPage.getByRole('heading', { name: headingRegex }).first()).toBeVisible();
  } else {
    await expect(targetPage.getByText(headingRegex).first()).toBeVisible();
  }

  const bodyText = (await targetPage.locator('body').innerText()).trim();
  if (bodyText.length < 150) {
    throw new Error('Legal content text is not sufficiently visible.');
  }

  await captureCheckpoint(targetPage, testInfo, screenshotLabel, { fullPage: true });
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
  } else if (page.url() !== originalUrl) {
    await page.goBack({ waitUntil: 'domcontentloaded' }).catch(() => null);
    await waitForUi(page);
  }

  return finalUrl;
}

test('saleads mi negocio full workflow', async ({ page }, testInfo) => {
  const report = emptyReport();
  const errors = [];
  const legalUrls = {};

  const runValidation = async (key, action) => {
    try {
      await action();
      report[key] = 'PASS';
      return true;
    } catch (error) {
      report[key] = 'FAIL';
      errors.push(`[${key}] ${error.message}`);
      return false;
    }
  };

  const loginOk = await runValidation('Login', async () => {
    await ensureLoginPage(page);

    const loginButton = await firstVisible(
      [
        page.getByRole('button', { name: /Sign in with Google|Continue with Google|Iniciar sesi.n con Google/i }),
        page.getByRole('link', { name: /Sign in with Google|Continue with Google|Iniciar sesi.n con Google/i }),
        page.getByText(/Sign in with Google|Continue with Google|Iniciar sesi.n con Google/i)
      ],
      30000
    );

    const popupPromise = page.context().waitForEvent('page', { timeout: 9000 }).catch(() => null);
    await clickAndWait(loginButton, page);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState('domcontentloaded');
      await waitForUi(popup);
      const accountOption = popup.getByText(EMAIL_TO_SELECT, { exact: false });
      const accountVisible = await accountOption.isVisible().catch(() => false);
      if (accountVisible) {
        await accountOption.click();
      }
      await popup.waitForEvent('close', { timeout: 30000 }).catch(() => null);
      await page.bringToFront();
    } else {
      const accountOption = page.getByText(EMAIL_TO_SELECT, { exact: false });
      const accountVisible = await accountOption.isVisible({ timeout: 7000 }).catch(() => false);
      if (accountVisible) {
        await accountOption.click();
      }
    }

    await waitForUi(page);
    await firstVisible([page.locator('aside'), page.getByRole('navigation'), page.locator('[class*="sidebar"]')], 30000);
    await firstVisible([page.locator('main'), page.getByRole('main')], 30000);
    await captureCheckpoint(page, testInfo, 'dashboard-loaded', { fullPage: true });
  });

  const miNegocioMenuOk = await runValidation('Mi Negocio menu', async () => {
    if (!loginOk) {
      throw new Error('Blocked because Login failed.');
    }

    await ensureMiNegocioExpanded(page);
    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
    await captureCheckpoint(page, testInfo, 'mi-negocio-menu-expanded');
  });

  const agregarModalOk = await runValidation('Agregar Negocio modal', async () => {
    if (!miNegocioMenuOk) {
      throw new Error('Blocked because Mi Negocio menu step failed.');
    }

    const agregarNegocio = await firstVisible([
      page.getByRole('button', { name: /Agregar Negocio/i }),
      page.getByRole('link', { name: /Agregar Negocio/i }),
      page.getByText(/^Agregar Negocio$/i)
    ]);

    await clickAndWait(agregarNegocio, page);

    const modal = await firstVisible([
      page.getByRole('dialog').filter({ hasText: /Crear Nuevo Negocio/i }),
      page.locator('[role="dialog"]'),
      page.locator('div').filter({ hasText: /Crear Nuevo Negocio/i })
    ]);

    await expect(modal.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();

    const nombreInput = await firstVisible([
      modal.getByLabel(/Nombre del Negocio/i),
      modal.getByPlaceholder(/Nombre del Negocio/i),
      modal.locator('input[name*="nombre" i]'),
      modal.locator('input').first()
    ]);
    await expect(nombreInput).toBeVisible();
    await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(modal.getByRole('button', { name: /Cancelar/i })).toBeVisible();
    await expect(modal.getByRole('button', { name: /Crear Negocio/i })).toBeVisible();

    await nombreInput.click();
    await nombreInput.fill('Negocio Prueba Automatizacion');
    await captureCheckpoint(page, testInfo, 'agregar-negocio-modal');

    await clickAndWait(modal.getByRole('button', { name: /Cancelar/i }), page);
  });

  const administrarViewOk = await runValidation('Administrar Negocios view', async () => {
    if (!agregarModalOk) {
      throw new Error('Blocked because Agregar Negocio modal step failed.');
    }

    await ensureMiNegocioExpanded(page);
    const administrarNegocios = await firstVisible([
      page.getByRole('button', { name: /Administrar Negocios/i }),
      page.getByRole('link', { name: /Administrar Negocios/i }),
      page.getByText(/^Administrar Negocios$/i)
    ]);
    await clickAndWait(administrarNegocios, page);

    await expect(page.getByText(/Informaci.n General/i).first()).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Secci.n Legal/i).first()).toBeVisible();

    await captureCheckpoint(page, testInfo, 'administrar-negocios-full-page', { fullPage: true });
  });

  const infoGeneralOk = await runValidation('Informacion General', async () => {
    if (!administrarViewOk) {
      throw new Error('Blocked because Administrar Negocios view step failed.');
    }

    const infoSection = await findSectionByHeading(page, /Informaci.n General/i);
    const infoText = (await infoSection.innerText()).replace(/\s+/g, ' ').trim();

    const hasEmail = /[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}/i.test(infoText);
    if (!hasEmail) {
      throw new Error('User email is not visible in Informacion General.');
    }

    const hasPlan = /BUSINESS PLAN/i.test(infoText);
    if (!hasPlan) {
      throw new Error('BUSINESS PLAN text is not visible in Informacion General.');
    }

    const hasCambiarPlan = /Cambiar Plan/i.test(infoText);
    if (!hasCambiarPlan) {
      throw new Error('Cambiar Plan button is not visible in Informacion General.');
    }

    const expectedUserName = process.env.SALEADS_EXPECTED_USER_NAME;
    if (expectedUserName) {
      if (!new RegExp(expectedUserName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'i').test(infoText)) {
        throw new Error(`Expected user name "${expectedUserName}" was not found in Informacion General.`);
      }
    } else {
      const nonLabelTokens = infoText
        .split(/\s+/)
        .filter((token) => !/informaci.n|general|business|plan|cambiar|cuenta|estado|idioma|email|correo/i.test(token));
      if (nonLabelTokens.length < 2) {
        throw new Error(
          'Could not confidently confirm user name visibility. Set SALEADS_EXPECTED_USER_NAME to enforce exact validation.'
        );
      }
    }
  });

  await runValidation('Detalles de la Cuenta', async () => {
    if (!infoGeneralOk) {
      throw new Error('Blocked because Informacion General step failed.');
    }

    const detailsSection = await findSectionByHeading(page, /Detalles de la Cuenta/i);
    const detailsText = (await detailsSection.innerText()).replace(/\s+/g, ' ').trim();

    for (const requiredText of [/Cuenta creada/i, /Estado activo/i, /Idioma seleccionado/i]) {
      if (!requiredText.test(detailsText)) {
        throw new Error(`Missing expected text in Detalles de la Cuenta: ${requiredText}`);
      }
    }
  });

  await runValidation('Tus Negocios', async () => {
    if (!administrarViewOk) {
      throw new Error('Blocked because Administrar Negocios view step failed.');
    }

    const negociosSection = await findSectionByHeading(page, /Tus Negocios/i);
    const negociosText = (await negociosSection.innerText()).replace(/\s+/g, ' ').trim();

    if (!/Agregar Negocio/i.test(negociosText)) {
      throw new Error('Agregar Negocio button is not visible in Tus Negocios.');
    }
    if (!/Tienes 2 de 3 negocios/i.test(negociosText)) {
      throw new Error('Business limit text "Tienes 2 de 3 negocios" is not visible in Tus Negocios.');
    }

    const listCandidates = await negociosSection.locator('li, [role="listitem"], [class*="business"], [class*="negocio"]').count();
    if (listCandidates < 1) {
      throw new Error('Business list was not detected in Tus Negocios.');
    }
  });

  await runValidation('Terminos y Condiciones', async () => {
    if (!administrarViewOk) {
      throw new Error('Blocked because Administrar Negocios view step failed.');
    }

    const finalUrl = await openLegalDocument(
      page,
      testInfo,
      /T.rminos y Condiciones/i,
      /T.rminos y Condiciones/i,
      'terminos-y-condiciones'
    );
    legalUrls.terminosYCondiciones = finalUrl;
  });

  await runValidation('Politica de Privacidad', async () => {
    if (!administrarViewOk) {
      throw new Error('Blocked because Administrar Negocios view step failed.');
    }

    const finalUrl = await openLegalDocument(
      page,
      testInfo,
      /Pol.tica de Privacidad/i,
      /Pol.tica de Privacidad/i,
      'politica-de-privacidad'
    );
    legalUrls.politicaDePrivacidad = finalUrl;
  });

  const reportPayload = {
    generatedAt: new Date().toISOString(),
    report,
    legalUrls,
    errors
  };
  const reportPath = testInfo.outputPath('saleads-mi-negocio-final-report.json');
  fs.writeFileSync(reportPath, JSON.stringify(reportPayload, null, 2), 'utf8');
  await testInfo.attach('saleads-mi-negocio-final-report', {
    path: reportPath,
    contentType: 'application/json'
  });

  // Expose the final matrix in stdout for CI logs.
  console.log('SALEADS_MI_NEGOCIO_FINAL_REPORT', JSON.stringify(reportPayload));

  const failures = Object.entries(report).filter(([, status]) => status === 'FAIL');
  expect(
    failures,
    `One or more workflow validations failed:\n${errors.map((entry) => `- ${entry}`).join('\n')}`
  ).toEqual([]);
});
