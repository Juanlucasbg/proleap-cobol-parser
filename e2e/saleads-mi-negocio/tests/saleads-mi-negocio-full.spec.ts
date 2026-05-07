import { expect, Locator, Page, test } from '@playwright/test';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';

type Status = 'PASS' | 'FAIL';

type StepResult = {
  status: Status;
  details?: string;
  evidence?: string[];
  finalUrl?: string;
};

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
  'Política de Privacidad',
] as const;

const report: Record<(typeof REPORT_FIELDS)[number], StepResult> = {
  Login: { status: 'FAIL', details: 'Not executed.' },
  'Mi Negocio menu': { status: 'FAIL', details: 'Not executed.' },
  'Agregar Negocio modal': { status: 'FAIL', details: 'Not executed.' },
  'Administrar Negocios view': { status: 'FAIL', details: 'Not executed.' },
  'Información General': { status: 'FAIL', details: 'Not executed.' },
  'Detalles de la Cuenta': { status: 'FAIL', details: 'Not executed.' },
  'Tus Negocios': { status: 'FAIL', details: 'Not executed.' },
  'Términos y Condiciones': { status: 'FAIL', details: 'Not executed.' },
  'Política de Privacidad': { status: 'FAIL', details: 'Not executed.' },
};

const toErrorMessage = (error: unknown) => {
  const message = error instanceof Error ? error.message : `Unknown error: ${String(error)}`;
  return message.replace(/\u001b\[[0-9;]*m/g, '');
};

const settleUi = async (page: Page) => {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(700);
};

const clickAndWait = async (page: Page, locator: Locator) => {
  await locator.scrollIntoViewIfNeeded().catch(() => {
    // Some elements cannot be scrolled directly; ignore and click.
  });
  await locator.click();
  await settleUi(page);
};

const waitForAnyVisible = async (page: Page, locators: Locator[], timeoutMs = 12_000) => {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const locator of locators) {
      const first = locator.first();
      if (await first.isVisible().catch(() => false)) {
        return first;
      }
    }

    await page.waitForTimeout(400);
  }

  throw new Error('No expected element became visible in time.');
};

const takeEvidence = async (page: Page, testName: string, filename: string) => {
  const outputPath = path.join(process.cwd(), 'test-results', testName, filename);
  await mkdir(path.dirname(outputPath), { recursive: true });
  await page.screenshot({ path: outputPath, fullPage: true });
  return outputPath;
};

const executeStep = async (
  stepName: (typeof REPORT_FIELDS)[number],
  fn: () => Promise<Partial<StepResult> | void>,
) => {
  try {
    const stepResult = (await fn()) ?? {};
    report[stepName] = {
      status: 'PASS',
      ...stepResult,
    };
  } catch (error) {
    report[stepName] = {
      status: 'FAIL',
      details: toErrorMessage(error),
      evidence: report[stepName].evidence,
      finalUrl: report[stepName].finalUrl,
    };
  }
};

const chooseGoogleAccountIfPrompted = async (candidatePage: Page) => {
  const accountOption = candidatePage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
  if (await accountOption.isVisible().catch(() => false)) {
    await accountOption.click();
    await settleUi(candidatePage);
  }
};

const openLegalPage = async (
  appPage: Page,
  linkLabel: RegExp,
  expectedHeading: RegExp,
  screenshotName: string,
) => {
  const link = await waitForAnyVisible(appPage, [
    appPage.getByRole('link', { name: linkLabel }),
    appPage.getByText(linkLabel),
  ]);

  const popupPromise = appPage
    .context()
    .waitForEvent('page', { timeout: 8_000 })
    .catch(() => null);

  const currentUrl = appPage.url();
  await clickAndWait(appPage, link);

  const popup = await popupPromise;
  const legalPage = popup ?? appPage;

  await settleUi(legalPage);
  await expect(legalPage.getByText(expectedHeading).first()).toBeVisible({ timeout: 20_000 });
  await expect(legalPage.locator('body')).toContainText(/[A-Za-zÀ-ÿ]{4,}/);

  const evidencePath = await takeEvidence(legalPage, 'saleads-mi-negocio-full', screenshotName);
  const finalUrl = legalPage.url();

  if (popup && !popup.isClosed()) {
    await popup.close();
    await appPage.bringToFront();
  } else if (!popup && appPage.url() !== currentUrl) {
    await appPage.goBack({ waitUntil: 'domcontentloaded' }).catch(() => {
      // Some environments may block history navigation; keep current page.
    });
    await settleUi(appPage);
  }

  return { evidencePath, finalUrl };
};

test('saleads_mi_negocio_full_test', async ({ page }) => {
  const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL;
  if (!loginUrl) {
    throw new Error(
      'Set SALEADS_LOGIN_URL (preferred) or SALEADS_BASE_URL before running this test.',
    );
  }

  await page.goto(loginUrl, { waitUntil: 'domcontentloaded' });
  await settleUi(page);
  let loginSucceeded = false;

  await executeStep('Login', async () => {
    const sidebarAlreadyVisible = await page
      .getByText(/Mi Negocio|Negocio/i)
      .first()
      .isVisible()
      .catch(() => false);

    if (!sidebarAlreadyVisible) {
      const googleLoginButton = await waitForAnyVisible(page, [
        page.getByRole('button', { name: /Google|Iniciar sesi[oó]n|Sign in/i }),
        page.getByRole('link', { name: /Google|Iniciar sesi[oó]n|Sign in/i }),
        page.getByText(/Google/i),
      ]);

      const popupPromise = page
        .context()
        .waitForEvent('page', { timeout: 8_000 })
        .catch(() => null);

      await clickAndWait(page, googleLoginButton);

      const popup = await popupPromise;
      if (popup) {
        await popup.waitForLoadState('domcontentloaded');
        await chooseGoogleAccountIfPrompted(popup);
      } else {
        await chooseGoogleAccountIfPrompted(page);
      }
    }

    await expect(page.getByText(/Mi Negocio|Negocio/i).first()).toBeVisible({ timeout: 30_000 });
    const dashboardScreenshot = await takeEvidence(
      page,
      'saleads-mi-negocio-full',
      '01-dashboard-loaded.png',
    );

    return {
      details: 'Main interface and left sidebar navigation are visible.',
      evidence: [dashboardScreenshot],
    };
  });
  loginSucceeded = report.Login.status === 'PASS';

  await executeStep('Mi Negocio menu', async () => {
    if (!loginSucceeded) {
      throw new Error('Login step failed; cannot validate Mi Negocio menu.');
    }
    const negocioSection = await waitForAnyVisible(page, [
      page.getByRole('button', { name: /^Negocio$/i }),
      page.getByRole('link', { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i),
    ]);
    await clickAndWait(page, negocioSection);

    const miNegocioEntry = await waitForAnyVisible(page, [
      page.getByRole('button', { name: /^Mi Negocio$/i }),
      page.getByRole('link', { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i),
    ]);
    await clickAndWait(page, miNegocioEntry);

    await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();

    const menuScreenshot = await takeEvidence(
      page,
      'saleads-mi-negocio-full',
      '02-mi-negocio-menu-expanded.png',
    );

    return {
      details: 'Mi Negocio submenu expanded and expected options are visible.',
      evidence: [menuScreenshot],
    };
  });

  await executeStep('Agregar Negocio modal', async () => {
    if (!loginSucceeded) {
      throw new Error('Login step failed; cannot validate Agregar Negocio modal.');
    }
    const addBusinessOption = await waitForAnyVisible(page, [
      page.getByRole('button', { name: /^Agregar Negocio$/i }),
      page.getByRole('link', { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);
    await clickAndWait(page, addBusinessOption);

    await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
    await expect(page.getByLabel(/Nombre del Negocio/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /Cancelar/i }).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /Crear Negocio/i }).first()).toBeVisible();

    const modalScreenshot = await takeEvidence(
      page,
      'saleads-mi-negocio-full',
      '03-agregar-negocio-modal.png',
    );

    const businessNameInput = page.getByLabel(/Nombre del Negocio/i).first();
    if (await businessNameInput.isVisible().catch(() => false)) {
      await businessNameInput.fill('Negocio Prueba Automatización');
    }
    await clickAndWait(page, page.getByRole('button', { name: /Cancelar/i }).first());

    return {
      details: 'Agregar Negocio modal fields and action buttons validated.',
      evidence: [modalScreenshot],
    };
  });

  await executeStep('Administrar Negocios view', async () => {
    if (!loginSucceeded) {
      throw new Error('Login step failed; cannot validate Administrar Negocios view.');
    }
    const administrarVisible = await page
      .getByText(/Administrar Negocios/i)
      .first()
      .isVisible()
      .catch(() => false);

    if (!administrarVisible) {
      const miNegocioEntry = await waitForAnyVisible(page, [
        page.getByRole('button', { name: /^Mi Negocio$/i }),
        page.getByRole('link', { name: /^Mi Negocio$/i }),
        page.getByText(/^Mi Negocio$/i),
      ]);
      await clickAndWait(page, miNegocioEntry);
    }

    const administrarNegocios = await waitForAnyVisible(page, [
      page.getByRole('button', { name: /^Administrar Negocios$/i }),
      page.getByRole('link', { name: /^Administrar Negocios$/i }),
      page.getByText(/^Administrar Negocios$/i),
    ]);
    await clickAndWait(page, administrarNegocios);

    await expect(page.getByText(/Información General/i).first()).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();

    const accountPageScreenshot = await takeEvidence(
      page,
      'saleads-mi-negocio-full',
      '04-administrar-negocios-page.png',
    );

    return {
      details: 'Administrar Negocios account view sections are visible.',
      evidence: [accountPageScreenshot],
    };
  });

  await executeStep('Información General', async () => {
    if (!loginSucceeded) {
      throw new Error('Login step failed; cannot validate Información General.');
    }
    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /Cambiar Plan/i }).first()).toBeVisible();

    const possibleEmail = page.getByText(/@/).first();
    await expect(possibleEmail).toBeVisible();

    const possibleUserName = page
      .locator('section, div')
      .filter({ hasText: /Información General/i })
      .first();
    await expect(possibleUserName).toBeVisible();

    return {
      details: 'User details, plan label, and Cambiar Plan button validated.',
    };
  });

  await executeStep('Detalles de la Cuenta', async () => {
    if (!loginSucceeded) {
      throw new Error('Login step failed; cannot validate Detalles de la Cuenta.');
    }
    await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();

    return {
      details: 'Cuenta creada, Estado activo, and Idioma seleccionado are visible.',
    };
  });

  await executeStep('Tus Negocios', async () => {
    if (!loginSucceeded) {
      throw new Error('Login step failed; cannot validate Tus Negocios.');
    }
    await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
    await expect(page.getByRole('button', { name: /Agregar Negocio/i }).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();

    return {
      details: 'Business list, Add button, and quota text are visible.',
    };
  });

  await executeStep('Términos y Condiciones', async () => {
    if (!loginSucceeded) {
      throw new Error('Login step failed; cannot validate Términos y Condiciones.');
    }
    const { evidencePath, finalUrl } = await openLegalPage(
      page,
      /Términos y Condiciones/i,
      /Términos y Condiciones/i,
      '05-terminos-y-condiciones.png',
    );

    return {
      details: 'Legal page heading and content were validated.',
      evidence: [evidencePath],
      finalUrl,
    };
  });

  await executeStep('Política de Privacidad', async () => {
    if (!loginSucceeded) {
      throw new Error('Login step failed; cannot validate Política de Privacidad.');
    }
    const { evidencePath, finalUrl } = await openLegalPage(
      page,
      /Política de Privacidad/i,
      /Política de Privacidad/i,
      '06-politica-de-privacidad.png',
    );

    return {
      details: 'Privacy page heading and content were validated.',
      evidence: [evidencePath],
      finalUrl,
    };
  });

  const finalReportPath = path.join(
    process.cwd(),
    'test-results',
    'saleads-mi-negocio-full',
    'final-report.json',
  );
  await mkdir(path.dirname(finalReportPath), { recursive: true });
  await writeFile(finalReportPath, JSON.stringify(report, null, 2), 'utf8');

  const failedSteps = Object.entries(report)
    .filter(([, value]) => value.status === 'FAIL')
    .map(([name]) => name);
  expect(
    failedSteps,
    `Failing report fields: ${failedSteps.join(', ')}. Review test-results/saleads-mi-negocio-full/final-report.json`,
  ).toEqual([]);
});
