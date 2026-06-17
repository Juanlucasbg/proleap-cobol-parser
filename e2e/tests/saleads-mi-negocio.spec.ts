import { expect, Page, TestInfo, test } from '@playwright/test';
import { promises as fs } from 'fs';

type ReportStatus = 'PASS' | 'FAIL';

type ReportEntry = {
  status: ReportStatus;
  details?: string;
};

const REPORT_FIELDS = [
  'Login',
  'Mi Negocio menu',
  'Agregar Negocio modal',
  'Administrar Negocios view',
  'Informacion General',
  'Detalles de la Cuenta',
  'Tus Negocios',
  'Terminos y Condiciones',
  'Politica de Privacidad'
] as const;

type ReportField = (typeof REPORT_FIELDS)[number];

const EMAIL_TO_SELECT = 'juanlucasbarbiergarzon@gmail.com';

async function waitForUiLoad(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded', { timeout: 10000 }).catch(() => undefined);
  await page.waitForLoadState('networkidle', { timeout: 7000 }).catch(() => undefined);
  await page.waitForTimeout(600);
}

async function isVisible(locator: ReturnType<Page['locator']>, timeout = 1200): Promise<boolean> {
  try {
    await locator.first().waitFor({ state: 'visible', timeout });
    return true;
  } catch {
    return false;
  }
}

async function clickFirstVisible(
  page: Page,
  candidates: Array<ReturnType<Page['locator']>>,
  errorMessage: string
): Promise<void> {
  for (const candidate of candidates) {
    if (await isVisible(candidate)) {
      await candidate.first().click();
      await waitForUiLoad(page);
      return;
    }
  }

  throw new Error(errorMessage);
}

async function takeCheckpoint(page: Page, testInfo: TestInfo, filename: string): Promise<void> {
  const screenshotPath = testInfo.outputPath(filename);
  await page.screenshot({ path: screenshotPath, fullPage: true });
  await testInfo.attach(filename, { path: screenshotPath, contentType: 'image/png' });
}

async function waitForMainInterface(page: Page): Promise<void> {
  await expect
    .poll(
      async () => {
        const sidebarVisible = await isVisible(page.locator('aside'), 600);
        const negocioVisible = await isVisible(page.getByText(/Negocio/i), 600);
        return sidebarVisible || negocioVisible;
      },
      { timeout: 60000 }
    )
    .toBeTruthy();
}

async function ensureStartPage(page: Page, testInfo: TestInfo): Promise<void> {
  if (page.url() !== 'about:blank') {
    return;
  }

  const configuredLoginUrl = process.env.SALEADS_LOGIN_URL;
  const configuredBaseUrl = String(testInfo.project.use.baseURL || '');
  const targetUrl = configuredLoginUrl || configuredBaseUrl;

  if (!targetUrl) {
    throw new Error(
      'Browser started on about:blank. Set SALEADS_LOGIN_URL or SALEADS_BASE_URL/BASE_URL for environment-agnostic navigation.'
    );
  }

  await page.goto(targetUrl, { waitUntil: 'domcontentloaded' });
  await waitForUiLoad(page);
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const agregarVisible = await isVisible(page.getByText(/Agregar Negocio/i));
  const administrarVisible = await isVisible(page.getByText(/Administrar Negocios/i));

  if (agregarVisible && administrarVisible) {
    return;
  }

  const negocioCandidates = [
    page.getByRole('button', { name: /Negocio/i }),
    page.getByRole('link', { name: /Negocio/i }),
    page.getByText(/^Negocio$/i)
  ];
  await clickFirstVisible(page, negocioCandidates, 'No se encontro la seccion Negocio en el sidebar.');

  const miNegocioCandidates = [
    page.getByRole('button', { name: /Mi Negocio/i }),
    page.getByRole('link', { name: /Mi Negocio/i }),
    page.getByText(/Mi Negocio/i)
  ];
  await clickFirstVisible(page, miNegocioCandidates, 'No se encontro la opcion Mi Negocio en el sidebar.');
}

async function runAndRecord(
  report: Record<ReportField, ReportEntry>,
  field: ReportField,
  action: () => Promise<void>
): Promise<void> {
  try {
    await action();
    report[field] = { status: 'PASS' };
  } catch (error) {
    const details = error instanceof Error ? error.message : String(error);
    report[field] = { status: 'FAIL', details };
  }
}

test.describe('SaleADS Mi Negocio workflow', () => {
  test('validates full login and Mi Negocio flow', async ({ page, context }, testInfo) => {
    const report = Object.fromEntries(
      REPORT_FIELDS.map((field) => [field, { status: 'FAIL', details: 'Not executed' }])
    ) as Record<ReportField, ReportEntry>;

    const legalUrls: { terms?: string; privacy?: string } = {};

    await runAndRecord(report, 'Login', async () => {
      await ensureStartPage(page, testInfo);
      await waitForUiLoad(page);

      const mainUiAlreadyVisible =
        (await isVisible(page.locator('aside'))) || (await isVisible(page.getByText(/Negocio/i)));

      if (!mainUiAlreadyVisible) {
        const popupPromise = context.waitForEvent('page', { timeout: 12000 }).catch(() => null);

        await clickFirstVisible(
          page,
          [
            page.getByRole('button', { name: /Google/i }),
            page.getByRole('link', { name: /Google/i }),
            page.getByRole('button', { name: /Sign in/i }),
            page.getByRole('button', { name: /Iniciar sesi[oó]n/i }),
            page.getByText(/Google/i)
          ],
          'No se encontro boton de inicio de sesion con Google.'
        );

        const popup = await popupPromise;
        const authPage = popup || page;

        if (await isVisible(authPage.getByText(EMAIL_TO_SELECT), 7000)) {
          await authPage.getByText(EMAIL_TO_SELECT).first().click();
          await waitForUiLoad(authPage);
        }

        if (popup) {
          await popup.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => undefined);
          await popup.close().catch(() => undefined);
        }
      }

      await waitForMainInterface(page);
      await takeCheckpoint(page, testInfo, 'checkpoint-dashboard-loaded.png');
    });

    await runAndRecord(report, 'Mi Negocio menu', async () => {
      await ensureMiNegocioExpanded(page);

      await expect(page.getByText(/Agregar Negocio/i)).toBeVisible();
      await expect(page.getByText(/Administrar Negocios/i)).toBeVisible();

      await takeCheckpoint(page, testInfo, 'checkpoint-mi-negocio-expanded.png');
    });

    await runAndRecord(report, 'Agregar Negocio modal', async () => {
      await clickFirstVisible(
        page,
        [
          page.getByRole('button', { name: /Agregar Negocio/i }),
          page.getByRole('link', { name: /Agregar Negocio/i }),
          page.getByText(/Agregar Negocio/i)
        ],
        'No se encontro la opcion Agregar Negocio.'
      );

      await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeVisible();

      const inputCandidates = [
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.locator('input[name*="negocio" i]')
      ];

      let selectedInput: ReturnType<Page['locator']> | null = null;
      for (const candidate of inputCandidates) {
        if (await isVisible(candidate)) {
          selectedInput = candidate;
          break;
        }
      }

      if (!selectedInput) {
        throw new Error('No se encontro el campo Nombre del Negocio en el modal.');
      }

      await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(page.getByRole('button', { name: /Cancelar/i })).toBeVisible();
      await expect(page.getByRole('button', { name: /Crear Negocio/i })).toBeVisible();

      await selectedInput.first().click();
      await selectedInput.first().fill('Negocio Prueba Automatizacion');
      await takeCheckpoint(page, testInfo, 'checkpoint-agregar-negocio-modal.png');

      await page.getByRole('button', { name: /Cancelar/i }).click();
      await waitForUiLoad(page);
    });

    await runAndRecord(report, 'Administrar Negocios view', async () => {
      await ensureMiNegocioExpanded(page);
      await clickFirstVisible(
        page,
        [
          page.getByRole('button', { name: /Administrar Negocios/i }),
          page.getByRole('link', { name: /Administrar Negocios/i }),
          page.getByText(/Administrar Negocios/i)
        ],
        'No se encontro la opcion Administrar Negocios.'
      );

      await expect(page.getByText(/Informaci[oó]n General/i)).toBeVisible();
      await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
      await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
      await expect(page.getByText(/Secci[oó]n Legal/i)).toBeVisible();

      await takeCheckpoint(page, testInfo, 'checkpoint-administrar-negocios.png');
    });

    await runAndRecord(report, 'Informacion General', async () => {
      const pageText = await page.locator('body').innerText();
      expect(pageText).toMatch(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
      expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
      expect(page.getByRole('button', { name: /Cambiar Plan/i })).toBeVisible();

      const probableNameRegex = /\b[A-Z][a-z]+(?:\s+[A-Z][a-z]+)+\b/;
      expect(pageText).toMatch(probableNameRegex);
    });

    await runAndRecord(report, 'Detalles de la Cuenta', async () => {
      await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
      await expect(page.getByText(/Estado activo/i)).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
    });

    await runAndRecord(report, 'Tus Negocios', async () => {
      await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
      await expect(page.getByRole('button', { name: /Agregar Negocio/i })).toBeVisible();
      await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();

      const listLikeCount =
        (await page.locator('ul li').count()) +
        (await page.locator('table tbody tr').count()) +
        (await page.locator('[role="listitem"]').count());
      expect(listLikeCount).toBeGreaterThan(0);
    });

    async function validateLegalLink(
      linkName: string,
      headingRegex: RegExp,
      screenshotName: string
    ): Promise<string> {
      const startingUrl = page.url();
      const popupPromise = context.waitForEvent('page', { timeout: 10000 }).catch(() => null);

      await clickFirstVisible(
        page,
        [
          page.getByRole('link', { name: headingRegex }),
          page.getByText(headingRegex),
          page.getByRole('button', { name: headingRegex })
        ],
        `No se encontro el enlace legal ${linkName}.`
      );

      const popup = await popupPromise;
      const legalPage = popup || page;

      await waitForUiLoad(legalPage);
      await expect(legalPage.getByText(headingRegex)).toBeVisible();

      const legalContent = await legalPage.locator('body').innerText();
      expect(legalContent.trim().length).toBeGreaterThan(120);

      await takeCheckpoint(legalPage, testInfo, screenshotName);
      const finalUrl = legalPage.url();

      if (popup) {
        await popup.close().catch(() => undefined);
        await page.bringToFront();
      } else {
        await page.goBack({ waitUntil: 'domcontentloaded' }).catch(async () => {
          await page.goto(startingUrl, { waitUntil: 'domcontentloaded' });
        });
        await waitForUiLoad(page);
      }

      return finalUrl;
    }

    await runAndRecord(report, 'Terminos y Condiciones', async () => {
      legalUrls.terms = await validateLegalLink(
        'Terminos y Condiciones',
        /T[eé]rminos y Condiciones/i,
        'checkpoint-terminos-y-condiciones.png'
      );
    });

    await runAndRecord(report, 'Politica de Privacidad', async () => {
      legalUrls.privacy = await validateLegalLink(
        'Politica de Privacidad',
        /Pol[ií]tica de Privacidad/i,
        'checkpoint-politica-de-privacidad.png'
      );
    });

    const finalReport = {
      testName: 'saleads_mi_negocio_full_test',
      generatedAt: new Date().toISOString(),
      results: report,
      legalUrls
    };

    const reportPath = testInfo.outputPath('saleads-mi-negocio-report.json');
    await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), 'utf8');
    await testInfo.attach('saleads-mi-negocio-report.json', {
      path: reportPath,
      contentType: 'application/json'
    });

    const failedEntries = Object.entries(report).filter(([, value]) => value.status === 'FAIL');
    expect(failedEntries, `Some workflow validations failed: ${JSON.stringify(failedEntries, null, 2)}`).toEqual([]);
  });
});
