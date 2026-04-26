import { expect, type Locator, type Page, test } from '@playwright/test';
import fs from 'node:fs';
import path from 'node:path';

type StepStatus = 'PASS' | 'FAIL';

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

const EXPECTED_EMAIL =
  process.env.SALEADS_USER_EMAIL ?? 'juanlucasbarbiergarzon@gmail.com';
const EXPECTED_NAME = process.env.SALEADS_USER_NAME;

const ARTIFACTS_DIR = path.resolve(
  __dirname,
  '../artifacts/saleads_mi_negocio_full_test',
);
const LEGAL_URLS_FILE = path.join(ARTIFACTS_DIR, 'legal-urls.json');
const FINAL_REPORT_FILE = path.join(ARTIFACTS_DIR, 'final-report.json');

function ensureArtifactsDir(): void {
  fs.mkdirSync(ARTIFACTS_DIR, { recursive: true });
}

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function normalizeText(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim();
}

function stepSlug(stepName: ReportKey): string {
  return normalizeText(stepName).replace(/[^a-z0-9]+/g, '-');
}

function buildReport(): Record<ReportKey, StepStatus> {
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

async function waitForUiLoad(page: Page): Promise<void> {
  await page.waitForLoadState('domcontentloaded');
  await page.waitForLoadState('networkidle');
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiLoad(page);
}

async function firstVisible(
  description: string,
  locators: Locator[],
): Promise<Locator> {
  for (const locator of locators) {
    if (await locator.first().isVisible().catch(() => false)) {
      return locator.first();
    }
  }

  throw new Error(`No visible element found for '${description}'.`);
}

async function findClickableByText(page: Page, textCandidates: string[]): Promise<Locator> {
  const locators: Locator[] = [];

  for (const candidate of textCandidates) {
    const regex = new RegExp(escapeRegex(candidate), 'i');
    locators.push(page.getByRole('button', { name: regex }).first());
    locators.push(page.getByRole('link', { name: regex }).first());
    locators.push(page.getByRole('menuitem', { name: regex }).first());
    locators.push(page.getByText(regex).first());
  }

  return firstVisible(textCandidates.join(' | '), locators);
}

async function takeCheckpointScreenshot(page: Page, fileName: string): Promise<void> {
  await page.screenshot({
    path: path.join(ARTIFACTS_DIR, fileName),
    fullPage: true,
  });
}

async function clickLegalLinkAndResolveTarget(
  page: Page,
  linkText: string,
): Promise<{ destinationPage: Page; finalUrl: string; openedInNewTab: boolean }> {
  const link = await firstVisible(`legal link '${linkText}'`, [
    page.getByRole('link', { name: new RegExp(escapeRegex(linkText), 'i') }).first(),
    page.getByText(new RegExp(escapeRegex(linkText), 'i')).first(),
  ]);

  const popupPromise = page.context().waitForEvent('page', { timeout: 5000 }).catch(() => null);

  await link.click();

  const popup = await popupPromise;
  if (popup) {
    await popup.waitForLoadState('domcontentloaded');
    await popup.waitForLoadState('networkidle');
    return {
      destinationPage: popup,
      finalUrl: popup.url(),
      openedInNewTab: true,
    };
  }

  await waitForUiLoad(page);
  return {
    destinationPage: page,
    finalUrl: page.url(),
    openedInNewTab: false,
  };
}

test.describe('saleads_mi_negocio_full_test', () => {
  test('login with Google and validate complete Mi Negocio workflow', async ({ page }) => {
    ensureArtifactsDir();

    const report = buildReport();
    const failures: string[] = [];
    const legalUrls: {
      termsAndConditions: string | null;
      privacyPolicy: string | null;
    } = {
      termsAndConditions: null,
      privacyPolicy: null,
    };

    async function runStep(stepName: ReportKey, action: () => Promise<void>): Promise<void> {
      try {
        await action();
        report[stepName] = 'PASS';
      } catch (error) {
        report[stepName] = 'FAIL';
        const message = error instanceof Error ? error.message : String(error);
        failures.push(`${stepName}: ${message}`);
        await takeCheckpointScreenshot(page, `${stepSlug(stepName)}-failure.png`);
      }
    }

    const appUrl = process.env.SALEADS_APP_URL;
    if (appUrl) {
      await page.goto(appUrl, { waitUntil: 'domcontentloaded' });
      await waitForUiLoad(page);
    }

    await runStep('Login', async () => {
      const loginButton = await findClickableByText(page, [
        'Sign in with Google',
        'Iniciar sesión con Google',
        'Continuar con Google',
        'Google',
      ]);
      const googlePopupPromise = page.context().waitForEvent('page', { timeout: 5000 }).catch(() => null);
      await loginButton.click();
      const googlePopup = await googlePopupPromise;

      if (googlePopup) {
        await googlePopup.waitForLoadState('domcontentloaded');
        await googlePopup.waitForLoadState('networkidle');

        const popupAccountOption = await firstVisible('Google account selector in popup', [
          googlePopup.getByText(new RegExp(escapeRegex(EXPECTED_EMAIL), 'i')).first(),
          googlePopup
            .getByRole('button', { name: new RegExp(escapeRegex(EXPECTED_EMAIL), 'i') })
            .first(),
          googlePopup
            .getByRole('link', { name: new RegExp(escapeRegex(EXPECTED_EMAIL), 'i') })
            .first(),
        ]).catch(() => null);

        if (popupAccountOption) {
          await popupAccountOption.click();
          await Promise.race([
            googlePopup.waitForEvent('close', { timeout: 15000 }).catch(() => null),
            googlePopup.waitForLoadState('networkidle').catch(() => null),
          ]);
        }

        await page.bringToFront();
        await waitForUiLoad(page);
      } else {
        await waitForUiLoad(page);

        const accountOption = await firstVisible('Google account selector', [
          page.getByText(new RegExp(escapeRegex(EXPECTED_EMAIL), 'i')).first(),
          page.getByRole('button', { name: new RegExp(escapeRegex(EXPECTED_EMAIL), 'i') }).first(),
          page.getByRole('link', { name: new RegExp(escapeRegex(EXPECTED_EMAIL), 'i') }).first(),
        ]).catch(() => null);

        if (accountOption) {
          await clickAndWait(page, accountOption);
        }
      }

      await expect(
        page
          .locator('aside, nav')
          .filter({ hasText: /Negocio|Mi Negocio/i })
          .first(),
      ).toBeVisible();

      await takeCheckpointScreenshot(page, '01-dashboard-loaded.png');
    });

    await runStep('Mi Negocio menu', async () => {
      const miNegocio = await findClickableByText(page, ['Mi Negocio', 'Negocio']);
      await clickAndWait(page, miNegocio);

      await expect(page.getByText(/Agregar Negocio/i).first()).toBeVisible();
      await expect(page.getByText(/Administrar Negocios/i).first()).toBeVisible();
      await takeCheckpointScreenshot(page, '02-mi-negocio-expanded.png');
    });

    await runStep('Agregar Negocio modal', async () => {
      await clickAndWait(page, page.getByText(/Agregar Negocio/i).first());

      await expect(page.getByText(/Crear Nuevo Negocio/i).first()).toBeVisible();
      const businessNameInput = await firstVisible('Nombre del Negocio input', [
        page.getByLabel(/Nombre del Negocio/i).first(),
        page.getByPlaceholder(/Nombre del Negocio/i).first(),
        page.getByRole('textbox', { name: /Nombre del Negocio/i }).first(),
      ]);
      await expect(businessNameInput).toBeVisible();

      await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
      await expect(page.getByRole('button', { name: /Cancelar/i }).first()).toBeVisible();
      await expect(page.getByRole('button', { name: /Crear Negocio/i }).first()).toBeVisible();

      await takeCheckpointScreenshot(page, '03-agregar-negocio-modal.png');

      await businessNameInput.fill('Negocio Prueba Automatización');
      await clickAndWait(page, page.getByRole('button', { name: /Cancelar/i }).first());
    });

    await runStep('Administrar Negocios view', async () => {
      const administrarNegocios = page.getByText(/Administrar Negocios/i).first();

      if (!(await administrarNegocios.isVisible().catch(() => false))) {
        const miNegocio = await findClickableByText(page, ['Mi Negocio', 'Negocio']);
        await clickAndWait(page, miNegocio);
      }

      await clickAndWait(page, page.getByText(/Administrar Negocios/i).first());

      await expect(page.getByText(/Información General/i).first()).toBeVisible();
      await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();

      await takeCheckpointScreenshot(page, '04-administrar-negocios-view.png');
    });

    await runStep('Información General', async () => {
      await expect(page.getByText(new RegExp(escapeRegex(EXPECTED_EMAIL), 'i')).first()).toBeVisible();
      await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
      await expect(page.getByRole('button', { name: /Cambiar Plan/i }).first()).toBeVisible();

      if (EXPECTED_NAME) {
        await expect(
          page.getByText(new RegExp(escapeRegex(EXPECTED_NAME), 'i')).first(),
        ).toBeVisible();
      } else {
        const infoText = await page.locator('body').innerText();
        const lines = infoText
          .split('\n')
          .map((line) => line.trim())
          .filter(Boolean);

        const hasLikelyName = lines.some((line) => {
          const candidate = normalizeText(line);
          if (candidate.includes('@')) {
            return false;
          }
          if (
            /business plan|cambiar plan|informacion general|detalles de la cuenta|cuenta creada|estado activo|idioma seleccionado|tus negocios|seccion legal/.test(
              candidate,
            )
          ) {
            return false;
          }
          return /^[a-zA-ZÁÉÍÓÚÜÑáéíóúüñ.'\- ]{5,}$/.test(line);
        });

        expect(
          hasLikelyName,
          'A visible user name-like text should be present. Configure SALEADS_USER_NAME if needed.',
        ).toBeTruthy();
      }
    });

    await runStep('Detalles de la Cuenta', async () => {
      await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
      await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
    });

    await runStep('Tus Negocios', async () => {
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.getByRole('button', { name: /Agregar Negocio/i }).first()).toBeVisible();
      await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    });

    await runStep('Términos y Condiciones', async () => {
      const { destinationPage, finalUrl, openedInNewTab } = await clickLegalLinkAndResolveTarget(
        page,
        'Términos y Condiciones',
      );

      await expect(destinationPage.getByText(/T[ée]rminos y Condiciones/i).first()).toBeVisible();
      const legalText = await destinationPage.locator('body').innerText();
      expect(legalText.trim().length).toBeGreaterThan(200);

      legalUrls.termsAndConditions = finalUrl;
      await takeCheckpointScreenshot(destinationPage, '08-terminos-y-condiciones.png');

      if (openedInNewTab) {
        await destinationPage.close();
        await page.bringToFront();
        await waitForUiLoad(page);
      } else {
        await page.goBack({ waitUntil: 'domcontentloaded' });
        await waitForUiLoad(page);
      }
    });

    await runStep('Política de Privacidad', async () => {
      const { destinationPage, finalUrl, openedInNewTab } = await clickLegalLinkAndResolveTarget(
        page,
        'Política de Privacidad',
      );

      await expect(destinationPage.getByText(/Pol[íi]tica de Privacidad/i).first()).toBeVisible();
      const legalText = await destinationPage.locator('body').innerText();
      expect(legalText.trim().length).toBeGreaterThan(200);

      legalUrls.privacyPolicy = finalUrl;
      await takeCheckpointScreenshot(destinationPage, '09-politica-de-privacidad.png');

      if (openedInNewTab) {
        await destinationPage.close();
        await page.bringToFront();
        await waitForUiLoad(page);
      } else {
        await page.goBack({ waitUntil: 'domcontentloaded' });
        await waitForUiLoad(page);
      }
    });

    fs.writeFileSync(
      LEGAL_URLS_FILE,
      JSON.stringify(legalUrls, null, 2),
      'utf-8',
    );

    fs.writeFileSync(
      FINAL_REPORT_FILE,
      JSON.stringify(
        {
          report,
          failures,
          legalUrls,
        },
        null,
        2,
      ),
      'utf-8',
    );

    // Keep a strict outcome while still producing a full PASS/FAIL report artifact.
    for (const [field, status] of Object.entries(report)) {
      expect(status, `Final report status for '${field}'`).toBe('PASS');
    }
  });
});
