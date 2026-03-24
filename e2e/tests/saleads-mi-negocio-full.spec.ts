import { expect, Locator, Page, TestInfo, test } from '@playwright/test';

type ReportKey =
  | 'Login'
  | 'Mi Negocio menu'
  | 'Agregar Negocio modal'
  | 'Administrar Negocios view'
  | 'Información General'
  | 'Detalles de la Cuenta'
  | 'Tus Negocios'
  | 'Términos y Condiciones'
  | 'Política de Privacidad';

type ReportEntry = {
  status: 'PASS' | 'FAIL';
  details: string;
};

const DEFAULT_GOOGLE_ACCOUNT = 'juanlucasbarbiergarzon@gmail.com';

const REPORT_ORDER: ReportKey[] = [
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

function createReport(): Record<ReportKey, ReportEntry> {
  return REPORT_ORDER.reduce(
    (acc, key) => {
      acc[key] = { status: 'FAIL', details: 'Not executed' };
      return acc;
    },
    {} as Record<ReportKey, ReportEntry>
  );
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

async function waitForUiToSettle(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForTimeout(800);
}

async function firstVisibleLocator(
  candidates: Locator[],
  timeoutMs = 15_000
): Promise<Locator | null> {
  const deadline = Date.now() + timeoutMs;

  for (const candidate of candidates) {
    const remaining = deadline - Date.now();
    if (remaining <= 0) {
      break;
    }

    try {
      const probe = candidate.first();
      await probe.waitFor({ state: 'visible', timeout: Math.min(remaining, 4_000) });
      return probe;
    } catch {
      // Keep trying with the next locator candidate.
    }
  }

  return null;
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await locator.scrollIntoViewIfNeeded();
  await locator.click();
  await waitForUiToSettle(page);
}

async function takeCheckpoint(
  page: Page,
  testInfo: TestInfo,
  name: string,
  fullPage = false
): Promise<void> {
  await page.screenshot({
    path: testInfo.outputPath(name),
    fullPage
  });
}

async function maybeSelectGoogleAccount(page: Page, email: string): Promise<boolean> {
  const emailPattern = new RegExp(escapeRegExp(email), 'i');
  const emailOption = await firstVisibleLocator(
    [
      page.getByText(emailPattern),
      page.getByRole('button', { name: emailPattern }),
      page.getByRole('link', { name: emailPattern })
    ],
    8_000
  );

  if (!emailOption) {
    return false;
  }

  await clickAndWait(emailOption, page);
  return true;
}

async function ensureAccountPageVisible(page: Page): Promise<void> {
  const legalMarker = await firstVisibleLocator(
    [
      page.getByText(/Sección Legal/i),
      page.getByRole('heading', { name: /Sección Legal/i }),
      page.getByRole('link', { name: /Términos y Condiciones/i })
    ],
    8_000
  );

  if (legalMarker) {
    return;
  }

  await page.goBack({ waitUntil: 'domcontentloaded', timeout: 12_000 }).catch(() => null);
  await waitForUiToSettle(page);
}

async function validateLegalPageFromLink(params: {
  page: Page;
  link: Locator;
  heading: RegExp;
  screenshotName: string;
  testInfo: TestInfo;
}): Promise<string> {
  const { page, link, heading, screenshotName, testInfo } = params;

  const popupPromise = page.waitForEvent('popup', { timeout: 10_000 }).catch(() => null);
  const navPromise = page.waitForNavigation({ waitUntil: 'domcontentloaded', timeout: 12_000 }).catch(() => null);

  await clickAndWait(link, page);

  const popup = await popupPromise;
  const targetPage = popup ?? page;

  if (popup) {
    await targetPage.waitForLoadState('domcontentloaded');
    await targetPage.waitForTimeout(500);
  } else {
    await navPromise;
    await waitForUiToSettle(targetPage);
  }

  const headingLocator = await firstVisibleLocator(
    [
      targetPage.getByRole('heading', { name: heading }),
      targetPage.locator('h1, h2, h3').filter({ hasText: heading }),
      targetPage.getByText(heading)
    ],
    15_000
  );

  if (!headingLocator) {
    throw new Error(`Heading not found for legal page: ${heading}`);
  }

  const bodyText = (await targetPage.locator('body').innerText()).replace(/\s+/g, ' ').trim();
  if (bodyText.length < 250) {
    throw new Error('Legal content appears too short or missing.');
  }

  await takeCheckpoint(targetPage, testInfo, screenshotName, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUiToSettle(page);
  } else {
    await ensureAccountPageVisible(page);
  }

  return finalUrl;
}

test('saleads_mi_negocio_full_test', async ({ page }, testInfo) => {
  const report = createReport();
  const googleAccount = process.env.SALEADS_GOOGLE_ACCOUNT ?? DEFAULT_GOOGLE_ACCOUNT;
  const expectedUserName = process.env.SALEADS_EXPECTED_USER_NAME;
  const configuredUrl = process.env.SALEADS_URL ?? process.env.BASE_URL;

  const setReport = (key: ReportKey, status: 'PASS' | 'FAIL', details: string) => {
    report[key] = { status, details };
  };

  if (configuredUrl) {
    await page.goto(configuredUrl, { waitUntil: 'domcontentloaded' });
    await waitForUiToSettle(page);
  } else {
    await waitForUiToSettle(page);
  }

  await test.step('1) Login with Google and validate dashboard', async () => {
    try {
      const sidebarProbe = await firstVisibleLocator(
        [
          page.getByText(/Mi Negocio|Negocio/i),
          page.locator('aside'),
          page.locator('nav')
        ],
        8_000
      );

      if (!sidebarProbe) {
        const loginTrigger = await firstVisibleLocator(
          [
            page.getByRole('button', { name: /sign in with google|iniciar con google|continuar con google|google/i }),
            page.getByText(/sign in with google|iniciar con google|continuar con google/i),
            page.getByRole('button', { name: /google/i })
          ],
          20_000
        );

        if (!loginTrigger) {
          throw new Error(
            'Google login trigger not found. Set SALEADS_URL or start the browser on the SaleADS login page.'
          );
        }

        const popupPromise = page.waitForEvent('popup', { timeout: 10_000 }).catch(() => null);
        await clickAndWait(loginTrigger, page);
        const popup = await popupPromise;

        if (popup) {
          await popup.waitForLoadState('domcontentloaded');
          await maybeSelectGoogleAccount(popup, googleAccount);
          await popup.waitForEvent('close', { timeout: 90_000 }).catch(() => null);
        } else {
          await maybeSelectGoogleAccount(page, googleAccount);
        }
      }

      await expect(page.getByText(/Mi Negocio|Negocio/i)).toBeVisible({ timeout: 120_000 });
      const sidebar = await firstVisibleLocator([page.locator('aside'), page.locator('nav')], 25_000);
      if (!sidebar) {
        throw new Error('Left sidebar navigation is not visible after login.');
      }

      await takeCheckpoint(page, testInfo, '01-dashboard-loaded.png', true);
      setReport('Login', 'PASS', 'Dashboard and sidebar are visible after login.');
    } catch (error) {
      setReport('Login', 'FAIL', (error as Error).message);
    }
  });

  await test.step('2) Open Mi Negocio menu and validate submenu', async () => {
    try {
      const negocioSection = await firstVisibleLocator(
        [
          page.getByRole('button', { name: /^Negocio$/i }),
          page.getByRole('link', { name: /^Negocio$/i }),
          page.getByText(/^Negocio$/i)
        ],
        20_000
      );
      if (!negocioSection) {
        throw new Error('Sidebar section "Negocio" not found.');
      }
      await clickAndWait(negocioSection, page);

      const miNegocioOption = await firstVisibleLocator(
        [
          page.getByRole('button', { name: /^Mi Negocio$/i }),
          page.getByRole('link', { name: /^Mi Negocio$/i }),
          page.getByText(/^Mi Negocio$/i)
        ],
        15_000
      );
      if (!miNegocioOption) {
        throw new Error('Option "Mi Negocio" not found.');
      }
      await clickAndWait(miNegocioOption, page);

      await expect(page.getByText(/Agregar Negocio/i)).toBeVisible({ timeout: 15_000 });
      await expect(page.getByText(/Administrar Negocios/i)).toBeVisible({ timeout: 15_000 });

      await takeCheckpoint(page, testInfo, '02-mi-negocio-expanded-menu.png', true);
      setReport('Mi Negocio menu', 'PASS', 'Submenu expanded with both options visible.');
    } catch (error) {
      setReport('Mi Negocio menu', 'FAIL', (error as Error).message);
    }
  });

  await test.step('3) Validate Agregar Negocio modal', async () => {
    try {
      const addBusinessEntry = await firstVisibleLocator(
        [
          page.getByRole('menuitem', { name: /Agregar Negocio/i }),
          page.getByRole('button', { name: /Agregar Negocio/i }),
          page.getByRole('link', { name: /Agregar Negocio/i }),
          page.getByText(/Agregar Negocio/i)
        ],
        15_000
      );
      if (!addBusinessEntry) {
        throw new Error('"Agregar Negocio" entry not found.');
      }

      await clickAndWait(addBusinessEntry, page);

      const businessModal = await firstVisibleLocator(
        [
          page.getByRole('dialog').filter({ hasText: /Crear Nuevo Negocio/i }),
          page.locator('[role="dialog"], .modal, .MuiDialog-root').filter({ hasText: /Crear Nuevo Negocio/i })
        ],
        15_000
      );
      if (!businessModal) {
        throw new Error('Modal "Crear Nuevo Negocio" did not appear.');
      }

      await expect(businessModal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
      await expect(businessModal.getByText(/Nombre del Negocio/i)).toBeVisible();
      await expect(businessModal.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
      await expect(businessModal.getByRole('button', { name: /Cancelar/i })).toBeVisible();
      await expect(businessModal.getByRole('button', { name: /Crear Negocio/i })).toBeVisible();

      await takeCheckpoint(page, testInfo, '03-agregar-negocio-modal.png', true);

      const nameInput = await firstVisibleLocator(
        [
          businessModal.getByLabel(/Nombre del Negocio/i),
          businessModal.getByPlaceholder(/Nombre del Negocio/i),
          businessModal.locator('input[type="text"]')
        ],
        8_000
      );
      if (nameInput) {
        await nameInput.fill('Negocio Prueba Automatización');
      }

      const cancelButton = await firstVisibleLocator(
        [
          businessModal.getByRole('button', { name: /Cancelar/i }),
          businessModal.getByText(/^Cancelar$/i)
        ],
        8_000
      );
      if (cancelButton) {
        await clickAndWait(cancelButton, page);
      }

      setReport('Agregar Negocio modal', 'PASS', 'Modal fields, quota text and action buttons are visible.');
    } catch (error) {
      setReport('Agregar Negocio modal', 'FAIL', (error as Error).message);
    }
  });

  await test.step('4) Open Administrar Negocios and validate account sections', async () => {
    try {
      let adminOption = await firstVisibleLocator(
        [
          page.getByRole('menuitem', { name: /Administrar Negocios/i }),
          page.getByRole('button', { name: /Administrar Negocios/i }),
          page.getByRole('link', { name: /Administrar Negocios/i }),
          page.getByText(/Administrar Negocios/i)
        ],
        8_000
      );

      if (!adminOption) {
        const miNegocio = await firstVisibleLocator(
          [
            page.getByRole('button', { name: /^Mi Negocio$/i }),
            page.getByRole('link', { name: /^Mi Negocio$/i }),
            page.getByText(/^Mi Negocio$/i)
          ],
          10_000
        );
        if (miNegocio) {
          await clickAndWait(miNegocio, page);
          adminOption = await firstVisibleLocator(
            [
              page.getByRole('menuitem', { name: /Administrar Negocios/i }),
              page.getByRole('button', { name: /Administrar Negocios/i }),
              page.getByRole('link', { name: /Administrar Negocios/i }),
              page.getByText(/Administrar Negocios/i)
            ],
            10_000
          );
        }
      }

      if (!adminOption) {
        throw new Error('"Administrar Negocios" option not found.');
      }

      await clickAndWait(adminOption, page);

      await expect(page.getByText(/Información General/i)).toBeVisible({ timeout: 20_000 });
      await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible({ timeout: 20_000 });
      await expect(page.getByText(/Tus Negocios/i)).toBeVisible({ timeout: 20_000 });
      await expect(page.getByText(/Sección Legal/i)).toBeVisible({ timeout: 20_000 });

      await takeCheckpoint(page, testInfo, '04-administrar-negocios-account-page.png', true);
      setReport(
        'Administrar Negocios view',
        'PASS',
        'Account page loaded with Información General, Detalles, Tus Negocios and Sección Legal.'
      );
    } catch (error) {
      setReport('Administrar Negocios view', 'FAIL', (error as Error).message);
    }
  });

  await test.step('5) Validate Información General', async () => {
    try {
      await expect(page.getByText(/Información General/i)).toBeVisible({ timeout: 15_000 });

      const emailPattern = new RegExp(escapeRegExp(googleAccount), 'i');
      const emailLocator = await firstVisibleLocator(
        [
          page.getByText(emailPattern),
          page.locator('body').getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)
        ],
        10_000
      );
      if (!emailLocator) {
        throw new Error('User email is not visible in Información General.');
      }

      if (expectedUserName) {
        await expect(page.getByText(new RegExp(escapeRegExp(expectedUserName), 'i'))).toBeVisible({
          timeout: 10_000
        });
      } else {
        const infoSection = page.locator('section, div').filter({ hasText: /Información General/i }).first();
        const sectionText = (await infoSection.innerText()).split('\n').map((line) => line.trim()).filter(Boolean);
        const filteredValues = sectionText.filter(
          (line) =>
            !/información general|business plan|cambiar plan|detalles de la cuenta|tus negocios|sección legal/i.test(line) &&
            !/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(line) &&
            line.length > 2
        );
        if (filteredValues.length === 0) {
          throw new Error('User name value was not detected in Información General section.');
        }
      }

      await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible({ timeout: 15_000 });
      await expect(page.getByRole('button', { name: /Cambiar Plan/i })).toBeVisible({ timeout: 15_000 });

      setReport(
        'Información General',
        'PASS',
        'User name/email, BUSINESS PLAN label and Cambiar Plan button are visible.'
      );
    } catch (error) {
      setReport('Información General', 'FAIL', (error as Error).message);
    }
  });

  await test.step('6) Validate Detalles de la Cuenta', async () => {
    try {
      await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible({ timeout: 15_000 });
      await expect(page.getByText(/Cuenta creada/i)).toBeVisible({ timeout: 15_000 });
      await expect(page.getByText(/Estado activo/i)).toBeVisible({ timeout: 15_000 });
      await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible({ timeout: 15_000 });
      setReport(
        'Detalles de la Cuenta',
        'PASS',
        'Cuenta creada, Estado activo and Idioma seleccionado are visible.'
      );
    } catch (error) {
      setReport('Detalles de la Cuenta', 'FAIL', (error as Error).message);
    }
  });

  await test.step('7) Validate Tus Negocios', async () => {
    try {
      await expect(page.getByText(/Tus Negocios/i)).toBeVisible({ timeout: 15_000 });
      await expect(page.getByText(/Agregar Negocio/i)).toBeVisible({ timeout: 15_000 });
      await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible({ timeout: 15_000 });

      const businessesSection = page.locator('section, div').filter({ hasText: /Tus Negocios/i }).first();
      const sectionContent = (await businessesSection.innerText()).replace(/\s+/g, ' ').trim();
      if (sectionContent.length < 30) {
        throw new Error('Business list content appears empty in Tus Negocios.');
      }

      setReport(
        'Tus Negocios',
        'PASS',
        'Tus Negocios section shows list content, Agregar Negocio and quota text.'
      );
    } catch (error) {
      setReport('Tus Negocios', 'FAIL', (error as Error).message);
    }
  });

  await test.step('8) Validate Términos y Condiciones legal link', async () => {
    try {
      const legalSection = page.locator('section, div').filter({ hasText: /Sección Legal/i }).first();
      const termsLink = await firstVisibleLocator(
        [
          legalSection.getByRole('link', { name: /Términos y Condiciones/i }),
          page.getByRole('link', { name: /Términos y Condiciones/i }),
          legalSection.getByText(/Términos y Condiciones/i)
        ],
        15_000
      );
      if (!termsLink) {
        throw new Error('Link "Términos y Condiciones" not found in legal section.');
      }

      const termsUrl = await validateLegalPageFromLink({
        page,
        link: termsLink,
        heading: /Términos y Condiciones/i,
        screenshotName: '05-terminos-y-condiciones.png',
        testInfo
      });
      setReport('Términos y Condiciones', 'PASS', `Validated legal page. URL: ${termsUrl}`);
    } catch (error) {
      setReport('Términos y Condiciones', 'FAIL', (error as Error).message);
    }
  });

  await test.step('9) Validate Política de Privacidad legal link', async () => {
    try {
      await ensureAccountPageVisible(page);

      const legalSection = page.locator('section, div').filter({ hasText: /Sección Legal/i }).first();
      const privacyLink = await firstVisibleLocator(
        [
          legalSection.getByRole('link', { name: /Política de Privacidad/i }),
          page.getByRole('link', { name: /Política de Privacidad/i }),
          legalSection.getByText(/Política de Privacidad/i)
        ],
        15_000
      );
      if (!privacyLink) {
        throw new Error('Link "Política de Privacidad" not found in legal section.');
      }

      const privacyUrl = await validateLegalPageFromLink({
        page,
        link: privacyLink,
        heading: /Política de Privacidad/i,
        screenshotName: '06-politica-de-privacidad.png',
        testInfo
      });
      setReport('Política de Privacidad', 'PASS', `Validated legal page. URL: ${privacyUrl}`);
    } catch (error) {
      setReport('Política de Privacidad', 'FAIL', (error as Error).message);
    }
  });

  await test.step('10) Final report', async () => {
    const reportText = REPORT_ORDER.map((key) => `${key}: ${report[key].status} - ${report[key].details}`).join('\n');
    await testInfo.attach('saleads-mi-negocio-final-report', {
      body: reportText,
      contentType: 'text/plain'
    });
    console.log('\n=== saleads_mi_negocio_full_test report ===');
    console.log(reportText);

    const failed = REPORT_ORDER.filter((key) => report[key].status === 'FAIL');
    expect(
      failed,
      `One or more required validations failed.\n\n${reportText}`
    ).toHaveLength(0);
  });
});
