import { expect, Locator, Page, test, TestInfo } from '@playwright/test';
import { writeFile } from 'node:fs/promises';

type ReportField =
  | 'Login'
  | 'Mi Negocio menu'
  | 'Agregar Negocio modal'
  | 'Administrar Negocios view'
  | 'Información General'
  | 'Detalles de la Cuenta'
  | 'Tus Negocios'
  | 'Términos y Condiciones'
  | 'Política de Privacidad';

const REPORT_ORDER: ReportField[] = [
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

const GOOGLE_ACCOUNT_EMAIL = 'juanlucasbarbiergarzon@gmail.com';

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded').catch(() => undefined);
  await page.waitForLoadState('networkidle', { timeout: 10_000 }).catch(() => undefined);
}

async function getVisibleLocator(page: Page, namePattern: RegExp): Promise<Locator> {
  const candidates: Locator[] = [
    page.getByRole('button', { name: namePattern }),
    page.getByRole('link', { name: namePattern }),
    page.getByRole('menuitem', { name: namePattern }),
    page.getByRole('tab', { name: namePattern }),
    page.getByRole('heading', { name: namePattern }),
    page.getByText(namePattern)
  ];

  for (const locator of candidates) {
    const count = await locator.count();
    for (let index = 0; index < count; index += 1) {
      const candidate = locator.nth(index);
      if (await candidate.isVisible().catch(() => false)) {
        return candidate;
      }
    }
  }

  throw new Error(`No visible element found for ${namePattern}`);
}

async function getClickableVisibleLocator(page: Page, namePattern: RegExp): Promise<Locator> {
  const candidates: Locator[] = [
    page.getByRole('button', { name: namePattern }),
    page.getByRole('link', { name: namePattern }),
    page.getByRole('menuitem', { name: namePattern }),
    page.getByRole('tab', { name: namePattern }),
    page.locator('a, button, [role="button"], [role="link"]').filter({ hasText: namePattern }),
    page.getByText(namePattern)
  ];

  for (const locator of candidates) {
    const count = await locator.count();
    for (let index = 0; index < count; index += 1) {
      const candidate = locator.nth(index);
      if (await candidate.isVisible().catch(() => false)) {
        return candidate;
      }
    }
  }

  throw new Error(`No visible clickable element found for ${namePattern}`);
}

async function tryGetVisibleLocator(page: Page, namePattern: RegExp): Promise<Locator | null> {
  try {
    return await getVisibleLocator(page, namePattern);
  } catch {
    return null;
  }
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible({ timeout: 20_000 });
  await locator.click();
  await waitForUi(page);
}

async function clickByText(page: Page, namePattern: RegExp): Promise<void> {
  const locator = await getClickableVisibleLocator(page, namePattern);
  await clickAndWait(locator, page);
}

async function getSectionContext(
  page: Page,
  headingPattern: RegExp
): Promise<{ heading: Locator; container: Locator; sectionText: string }> {
  const heading = await getVisibleLocator(page, headingPattern);
  await expect(heading).toBeVisible({ timeout: 20_000 });

  const containerCandidates = [
    heading.locator('xpath=ancestor::section[1]'),
    heading.locator('xpath=ancestor::article[1]'),
    heading.locator('xpath=ancestor::*[@role="region"][1]'),
    heading.locator('xpath=ancestor::div[1]')
  ];

  for (const candidateLocator of containerCandidates) {
    const candidate = candidateLocator.first();
    const count = await candidate.count();
    if (count === 0) {
      continue;
    }

    if (await candidate.isVisible().catch(() => false)) {
      const sectionText = (await candidate.innerText()).replace(/\s+/g, ' ').trim();
      return { heading, container: candidate, sectionText };
    }
  }

  const body = page.locator('body');
  const sectionText = (await body.innerText()).replace(/\s+/g, ' ').trim();
  return { heading, container: body, sectionText };
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const hasExpandedItems = async (): Promise<boolean> => {
    const agregar = await tryGetVisibleLocator(page, /Agregar Negocio/i);
    const administrar = await tryGetVisibleLocator(page, /Administrar Negocios/i);
    return Boolean(agregar && administrar);
  };

  if (await hasExpandedItems()) {
    return;
  }

  const expandCandidates = [/Mi Negocio/i, /^Negocio$/i, /Negocio/i];
  for (const pattern of expandCandidates) {
    const locator = await tryGetVisibleLocator(page, pattern);
    if (!locator) {
      continue;
    }

    await clickAndWait(locator, page);
    if (await hasExpandedItems()) {
      return;
    }
  }

  throw new Error('Could not expand Mi Negocio menu.');
}

async function captureCheckpoint(
  page: Page,
  testInfo: TestInfo,
  checkpointName: string,
  fullPage = false
): Promise<void> {
  const fileName = `checkpoint-${checkpointName.replace(/\s+/g, '-').toLowerCase()}.png`;
  const screenshotPath = testInfo.outputPath(fileName);
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(checkpointName, {
    path: screenshotPath,
    contentType: 'image/png'
  });
}

async function pickGoogleAccountIfVisible(page: Page): Promise<void> {
  await waitForUi(page);

  const accountPatterns = [
    new RegExp(`^${GOOGLE_ACCOUNT_EMAIL.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`, 'i'),
    /Choose an account/i,
    /Elige una cuenta/i,
    /Selecciona una cuenta/i
  ];

  let hasGoogleUi = false;
  for (const pattern of accountPatterns) {
    const locator = await tryGetVisibleLocator(page, pattern);
    if (locator) {
      hasGoogleUi = true;
      break;
    }
  }
  if (!hasGoogleUi) {
    return;
  }

  const accountLocator = await tryGetVisibleLocator(page, new RegExp(GOOGLE_ACCOUNT_EMAIL, 'i'));
  if (accountLocator) {
    await clickAndWait(accountLocator, page);
    await waitForUi(page);
  }
}

async function validateLegalLink(
  page: Page,
  testInfo: TestInfo,
  linkText: RegExp,
  headingText: RegExp,
  reportLabel: ReportField,
  legalUrls: Partial<Record<ReportField, string>>
): Promise<void> {
  const appUrlBeforeClick = page.url();
  const popupPromise = page.context().waitForEvent('page', { timeout: 8_000 }).catch(() => null);

  await clickByText(page, linkText);

  const popup = await popupPromise;
  const targetPage = popup ?? page;
  await targetPage.waitForLoadState('domcontentloaded', { timeout: 20_000 }).catch(() => undefined);
  await targetPage.waitForLoadState('networkidle', { timeout: 12_000 }).catch(() => undefined);

  const heading = await getVisibleLocator(targetPage, headingText);
  await expect(heading).toBeVisible({ timeout: 20_000 });

  const bodyText = (await targetPage.locator('body').innerText()).replace(/\s+/g, ' ').trim();
  expect(bodyText.length).toBeGreaterThan(200);

  const screenshotLabel = `${reportLabel} page`;
  const screenshotPath = testInfo.outputPath(
    `checkpoint-${reportLabel.replace(/\s+/g, '-').toLowerCase().replace(/[^\w-]/g, '')}.png`
  );
  await targetPage.screenshot({ path: screenshotPath, fullPage: true });
  await testInfo.attach(screenshotLabel, {
    path: screenshotPath,
    contentType: 'image/png'
  });

  legalUrls[reportLabel] = targetPage.url();
  await testInfo.attach(`${reportLabel} URL`, {
    body: targetPage.url(),
    contentType: 'text/plain'
  });

  if (popup) {
    await popup.close();
    await page.bringToFront();
  } else if (page.url() !== appUrlBeforeClick) {
    await page.goBack({ waitUntil: 'domcontentloaded' }).catch(async () => {
      await page.goto(appUrlBeforeClick, { waitUntil: 'domcontentloaded' });
    });
  }

  await waitForUi(page);
}

test('saleads_mi_negocio_full_test', async ({ page }, testInfo) => {
  const report = new Map<ReportField, 'PASS' | 'FAIL'>();
  const legalUrls: Partial<Record<ReportField, string>> = {};

  const recordStep = async (field: ReportField, action: () => Promise<void>): Promise<void> => {
    try {
      await action();
      report.set(field, 'PASS');
    } catch (error) {
      report.set(field, 'FAIL');
      const errorMessage = error instanceof Error ? error.message : String(error);
      await testInfo.attach(`${field} failure`, {
        body: errorMessage,
        contentType: 'text/plain'
      });

      const failureShotPath = testInfo.outputPath(
        `failure-${field.replace(/\s+/g, '-').toLowerCase().replace(/[^\w-]/g, '')}.png`
      );
      await page.screenshot({ path: failureShotPath, fullPage: true }).catch(() => undefined);
      await testInfo.attach(`${field} failure screenshot`, {
        path: failureShotPath,
        contentType: 'image/png'
      });
    }
  };

  const loginUrl = process.env.SALEADS_URL ?? process.env.BASE_URL;
  if (!loginUrl) {
    throw new Error(
      'Set SALEADS_URL (or BASE_URL) to the current SaleADS login URL. The test does not hardcode any domain.'
    );
  }

  await page.goto(loginUrl, { waitUntil: 'domcontentloaded' });
  await waitForUi(page);

  await recordStep('Login', async () => {
    const popupPromise = page.context().waitForEvent('page', { timeout: 10_000 }).catch(() => null);

    const googleButtonPatterns = [
      /Sign in with Google/i,
      /Iniciar sesión con Google/i,
      /Continuar con Google/i,
      /Google/i
    ];
    const genericLoginPatterns = [/Iniciar sesión/i, /Login/i, /Log in/i, /Ingresar/i, /Entrar/i];

    let loginClicked = false;
    for (const pattern of googleButtonPatterns) {
      const locator = await tryGetVisibleLocator(page, pattern);
      if (!locator) {
        continue;
      }
      await clickAndWait(locator, page);
      loginClicked = true;
      break;
    }

    if (!loginClicked) {
      for (const pattern of genericLoginPatterns) {
        const locator = await tryGetVisibleLocator(page, pattern);
        if (!locator) {
          continue;
        }
        await clickAndWait(locator, page);
        loginClicked = true;
        break;
      }
    }

    expect(loginClicked).toBeTruthy();

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState('domcontentloaded', { timeout: 20_000 }).catch(() => undefined);
      await pickGoogleAccountIfVisible(popup);
      await popup.waitForClose({ timeout: 120_000 }).catch(() => undefined);
      await page.bringToFront();
    } else {
      await pickGoogleAccountIfVisible(page);
    }

    await page.waitForURL((url) => !url.hostname.includes('google.com'), { timeout: 120_000 }).catch(() => undefined);
    await waitForUi(page);

    const sidebar = page.locator('aside, nav').first();
    await expect(sidebar).toBeVisible({ timeout: 30_000 });
    await expect(await getVisibleLocator(page, /Negocio|Mi Negocio/i)).toBeVisible({ timeout: 30_000 });

    await captureCheckpoint(page, testInfo, 'dashboard-loaded');
  });

  await recordStep('Mi Negocio menu', async () => {
    await clickByText(page, /^Negocio$/i).catch(async () => {
      await clickByText(page, /Negocio/i);
    });

    await clickByText(page, /Mi Negocio/i);
    await expect(await getVisibleLocator(page, /Agregar Negocio/i)).toBeVisible({ timeout: 20_000 });
    await expect(await getVisibleLocator(page, /Administrar Negocios/i)).toBeVisible({ timeout: 20_000 });

    await captureCheckpoint(page, testInfo, 'mi-negocio-menu-expanded');
  });

  await recordStep('Agregar Negocio modal', async () => {
    await clickByText(page, /Agregar Negocio/i);

    const modal = page.getByRole('dialog').filter({ hasText: /Crear Nuevo Negocio/i }).first();
    await expect(modal).toBeVisible({ timeout: 20_000 });
    await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();

    const nameInput = modal.getByLabel(/Nombre del Negocio/i).or(modal.getByPlaceholder(/Nombre del Negocio/i));
    await expect(nameInput).toBeVisible();
    await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(modal.getByRole('button', { name: /Cancelar/i })).toBeVisible();
    await expect(modal.getByRole('button', { name: /Crear Negocio/i })).toBeVisible();

    await captureCheckpoint(page, testInfo, 'agregar-negocio-modal');

    await nameInput.fill('Negocio Prueba Automatización');
    await clickAndWait(modal.getByRole('button', { name: /Cancelar/i }), page);
    await expect(modal).not.toBeVisible({ timeout: 15_000 });
  });

  await recordStep('Administrar Negocios view', async () => {
    await ensureMiNegocioExpanded(page);
    await clickByText(page, /Administrar Negocios/i);

    await expect(await getVisibleLocator(page, /Información General/i)).toBeVisible({ timeout: 30_000 });
    await expect(await getVisibleLocator(page, /Detalles de la Cuenta/i)).toBeVisible({ timeout: 30_000 });
    await expect(await getVisibleLocator(page, /Tus Negocios/i)).toBeVisible({ timeout: 30_000 });
    await expect(await getVisibleLocator(page, /Sección Legal/i)).toBeVisible({ timeout: 30_000 });

    await captureCheckpoint(page, testInfo, 'administrar-negocios-account-page', true);
  });

  await recordStep('Información General', async () => {
    const { sectionText } = await getSectionContext(page, /Información General/i);
    const hasEmail = /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[A-Za-z]{2,}/.test(sectionText);
    const hasBusinessPlan = /BUSINESS PLAN/i.test(sectionText);
    const hasPlanButton = (await tryGetVisibleLocator(page, /Cambiar Plan/i)) !== null;
    const hasUserNameLabel = /Nombre(\s+de(l)?\s+usuario)?/i.test(sectionText);
    const sectionLines = sectionText
      .split(/\s{2,}|(?<=\.)\s+/)
      .map((line) => line.trim())
      .filter(Boolean);
    const candidateNameLine = sectionLines.find((line) => {
      if (/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[A-Za-z]{2,}/.test(line)) {
        return false;
      }
      if (/Información General|BUSINESS PLAN|Cambiar Plan|Cuenta|Detalles|Sección Legal|Tus Negocios/i.test(line)) {
        return false;
      }

      return /^[\p{L}][\p{L}\s.'-]{2,}$/u.test(line);
    });
    const hasUserName = hasUserNameLabel || Boolean(candidateNameLine);

    expect(hasEmail).toBeTruthy();
    expect(hasUserName).toBeTruthy();
    expect(hasBusinessPlan).toBeTruthy();
    expect(hasPlanButton).toBeTruthy();
  });

  await recordStep('Detalles de la Cuenta', async () => {
    const { sectionText } = await getSectionContext(page, /Detalles de la Cuenta/i);
    expect(/Cuenta creada|Creada el|Fecha de creación/i.test(sectionText)).toBeTruthy();
    expect(/Estado\s*activo|Estado\s*:\s*Activo/i.test(sectionText)).toBeTruthy();
    expect(/Idioma seleccionado|Idioma actual|Language/i.test(sectionText)).toBeTruthy();
  });

  await recordStep('Tus Negocios', async () => {
    const { container, sectionText } = await getSectionContext(page, /Tus Negocios/i);
    expect(/Agregar Negocio/i.test(sectionText)).toBeTruthy();
    expect(/Tienes 2 de 3 negocios/i.test(sectionText)).toBeTruthy();

    const listCandidates = container.locator('li, [role="listitem"], table tbody tr');
    const hasBusinessKeywordInSection = /Negocio/i.test(sectionText);
    const hasBusinessList = (await listCandidates.count()) > 0 || hasBusinessKeywordInSection;
    expect(hasBusinessList).toBeTruthy();
  });

  await recordStep('Términos y Condiciones', async () => {
    await validateLegalLink(
      page,
      testInfo,
      /Términos y Condiciones/i,
      /Términos y Condiciones/i,
      'Términos y Condiciones',
      legalUrls
    );
  });

  await recordStep('Política de Privacidad', async () => {
    await validateLegalLink(
      page,
      testInfo,
      /Política de Privacidad/i,
      /Política de Privacidad/i,
      'Política de Privacidad',
      legalUrls
    );
  });

  const finalReport = REPORT_ORDER.map((field) => ({
    field,
    status: report.get(field) ?? 'FAIL'
  }));

  await testInfo.attach('Final report', {
    body: JSON.stringify({ report: finalReport, legalUrls }, null, 2),
    contentType: 'application/json'
  });

  const reportPath = testInfo.outputPath('saleads-mi-negocio-final-report.json');
  await writeFile(reportPath, JSON.stringify({ report: finalReport, legalUrls }, null, 2), 'utf8');

  const failedSteps = finalReport.filter((entry) => entry.status === 'FAIL');
  expect(
    failedSteps,
    `Validation failures:\n${failedSteps.map((entry) => `- ${entry.field}`).join('\n') || 'none'}`
  ).toEqual([]);
});
