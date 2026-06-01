import { expect, Locator, Page, test } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

type ReportStatus = "PASS" | "FAIL";
type ReportKey =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad";

const SCREENSHOT_DIR = path.resolve(__dirname, "..", "artifacts", "screenshots");
const REPORT_PATH = path.resolve(__dirname, "..", "artifacts", "saleads-mi-negocio-report.json");
const GOOGLE_ACCOUNT_EMAIL = process.env.GOOGLE_ACCOUNT_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";
const SALEADS_LOGIN_URL = process.env.SALEADS_LOGIN_URL;
const STEPS: ReportKey[] = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad",
];

function slugify(value: string): string {
  return value
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function toMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

async function waitForUiLoad(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => undefined);
  await page.waitForTimeout(1200);
}

async function captureCheckpoint(page: Page, label: string, fullPage = true): Promise<string> {
  await fs.mkdir(SCREENSHOT_DIR, { recursive: true });
  const filePath = path.join(SCREENSHOT_DIR, `${Date.now()}-${slugify(label)}.png`);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function findFirstVisible(page: Page, candidates: Locator[], timeoutMs = 20000): Promise<Locator> {
  const start = Date.now();

  while (Date.now() - start < timeoutMs) {
    for (const locator of candidates) {
      const first = locator.first();
      try {
        await first.waitFor({ state: "visible", timeout: 500 });
        return first;
      } catch {
        // try next selector candidate
      }
    }
    await page.waitForTimeout(250);
  }

  throw new Error("No visible element found for provided selectors.");
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await locator.click();
  await waitForUiLoad(page);
}

async function sectionTextByHeading(page: Page, headingPattern: RegExp): Promise<string> {
  const heading = await findFirstVisible(page, [page.getByText(headingPattern), page.getByRole("heading", { name: headingPattern })], 20000);
  const section = heading.locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
  return section.innerText();
}

async function selectGoogleAccountIfVisible(page: Page): Promise<void> {
  const accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }).first();
  const chooserHeader = page.getByText(/choose an account|elige una cuenta|selecciona una cuenta/i).first();

  const accountIsVisible = await accountOption.isVisible().catch(() => false);
  const chooserIsVisible = await chooserHeader.isVisible().catch(() => false);

  if (accountIsVisible) {
    await clickAndWait(page, accountOption);
    return;
  }

  if (chooserIsVisible) {
    throw new Error(`Google account chooser is visible but account ${GOOGLE_ACCOUNT_EMAIL} was not found.`);
  }
}

async function openLegalPageAndReturn(
  page: Page,
  linkLabel: string,
  headingPattern: RegExp,
  screenshotLabel: string
): Promise<string> {
  const link = await findFirstVisible(page, [page.getByRole("link", { name: new RegExp(linkLabel, "i") }), page.getByText(new RegExp(linkLabel, "i"))], 20000);

  const popupPromise = page.context().waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await clickAndWait(page, link);

  const popupPage = await popupPromise;
  const legalPage = popupPage ?? page;

  await legalPage.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => undefined);
  await waitForUiLoad(legalPage);

  const legalHeading = await findFirstVisible(
    legalPage,
    [legalPage.getByRole("heading", { name: headingPattern }), legalPage.getByText(headingPattern)],
    20000
  );
  await expect(legalHeading).toBeVisible();

  const bodyText = await legalPage.locator("body").innerText();
  expect(bodyText.trim().length).toBeGreaterThan(150);

  await captureCheckpoint(legalPage, screenshotLabel, true);
  const finalUrl = legalPage.url();

  if (popupPage) {
    await popupPage.close();
    await page.bringToFront();
    await waitForUiLoad(page);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUiLoad(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  const report = Object.fromEntries(STEPS.map((step) => [step, "FAIL"])) as Record<ReportKey, ReportStatus>;
  const detailLog: Record<ReportKey, string> = {} as Record<ReportKey, string>;
  const screenshots: string[] = [];
  let termsUrl = "";
  let privacyUrl = "";

  async function runStep(step: ReportKey, action: () => Promise<void>): Promise<void> {
    try {
      await action();
      report[step] = "PASS";
      detailLog[step] = "Step completed.";
    } catch (error) {
      report[step] = "FAIL";
      detailLog[step] = toMessage(error);
    }
  }

  await runStep("Login", async () => {
    if (SALEADS_LOGIN_URL) {
      await page.goto(SALEADS_LOGIN_URL, { waitUntil: "domcontentloaded" });
      await waitForUiLoad(page);
    }

    const googleLoginButton = await findFirstVisible(
      page,
      [
        page.getByRole("button", { name: /sign in with google|iniciar sesión con google|continuar con google|google/i }),
        page.getByRole("link", { name: /sign in with google|iniciar sesión con google|continuar con google|google/i }),
        page.getByText(/sign in with google|iniciar sesión con google|continuar con google/i),
      ],
      45000
    );

    const googlePopupPromise = page.context().waitForEvent("page", { timeout: 8000 }).catch(() => null);
    await clickAndWait(page, googleLoginButton);

    const googlePopup = await googlePopupPromise;
    if (googlePopup) {
      await googlePopup.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => undefined);
      await waitForUiLoad(googlePopup);
      await selectGoogleAccountIfVisible(googlePopup);
      await googlePopup.waitForEvent("close", { timeout: 120000 }).catch(() => undefined);
      await page.bringToFront();
      await waitForUiLoad(page);
    } else {
      await selectGoogleAccountIfVisible(page);
    }

    await expect(
      await findFirstVisible(page, [page.getByRole("navigation"), page.locator("aside"), page.locator('[class*="sidebar"]')], 60000)
    ).toBeVisible();
    await expect(page.getByText(/negocio/i)).toBeVisible({ timeout: 60000 });

    screenshots.push(await captureCheckpoint(page, "dashboard-loaded", true));
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = await findFirstVisible(
      page,
      [page.getByRole("button", { name: /negocio/i }), page.getByText(/^negocio$/i), page.getByText(/negocio/i)],
      30000
    );
    await clickAndWait(page, negocioSection);

    const miNegocioOption = await findFirstVisible(
      page,
      [page.getByRole("button", { name: /mi negocio/i }), page.getByText(/mi negocio/i)],
      30000
    );
    await clickAndWait(page, miNegocioOption);

    await expect(page.getByText(/agregar negocio/i)).toBeVisible({ timeout: 30000 });
    await expect(page.getByText(/administrar negocios/i)).toBeVisible({ timeout: 30000 });

    screenshots.push(await captureCheckpoint(page, "mi-negocio-menu-expanded"));
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocioAction = await findFirstVisible(
      page,
      [page.getByRole("button", { name: /agregar negocio/i }), page.getByText(/agregar negocio/i)],
      30000
    );
    await clickAndWait(page, agregarNegocioAction);

    await expect(page.getByText(/crear nuevo negocio/i)).toBeVisible({ timeout: 30000 });
    const businessNameInput = await findFirstVisible(
      page,
      [page.getByLabel(/nombre del negocio/i), page.getByPlaceholder(/nombre del negocio/i), page.locator("input[type='text']")],
      30000
    );
    await expect(businessNameInput).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible({ timeout: 30000 });

    const cancelButton = await findFirstVisible(page, [page.getByRole("button", { name: /cancelar/i }), page.getByText(/^cancelar$/i)], 20000);
    const createButton = await findFirstVisible(
      page,
      [page.getByRole("button", { name: /crear negocio/i }), page.getByText(/crear negocio/i)],
      20000
    );

    await expect(cancelButton).toBeVisible();
    await expect(createButton).toBeVisible();
    screenshots.push(await captureCheckpoint(page, "agregar-negocio-modal"));

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page, cancelButton);
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarVisible = await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      const miNegocioOption = await findFirstVisible(page, [page.getByText(/mi negocio/i), page.getByRole("button", { name: /mi negocio/i })], 20000);
      await clickAndWait(page, miNegocioOption);
    }

    const administrarNegocios = await findFirstVisible(page, [page.getByText(/administrar negocios/i), page.getByRole("button", { name: /administrar negocios/i })], 30000);
    await clickAndWait(page, administrarNegocios);

    await expect(page.getByText(/información general/i)).toBeVisible({ timeout: 45000 });
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible({ timeout: 45000 });
    await expect(page.getByText(/tus negocios/i)).toBeVisible({ timeout: 45000 });
    await expect(page.getByText(/sección legal/i)).toBeVisible({ timeout: 45000 });

    screenshots.push(await captureCheckpoint(page, "administrar-negocios-page", true));
  });

  await runStep("Información General", async () => {
    const infoText = await sectionTextByHeading(page, /información general/i);

    const emailMatch = infoText.match(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/);
    expect(emailMatch).not.toBeNull();

    const hasNameCandidate = infoText
      .split("\n")
      .map((line) => line.trim())
      .some((line) => {
        if (!line || line.includes("@")) {
          return false;
        }
        return !/información general|business plan|cambiar plan/i.test(line) && /[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}/.test(line);
      });

    expect(hasNameCandidate).toBeTruthy();
    await expect(page.getByText(/business plan/i)).toBeVisible({ timeout: 20000 });
    const changePlanControl = await findFirstVisible(
      page,
      [page.getByRole("button", { name: /cambiar plan/i }), page.getByRole("link", { name: /cambiar plan/i }), page.getByText(/cambiar plan/i)],
      20000
    );
    await expect(changePlanControl).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/cuenta creada/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/estado activo/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/idioma seleccionado/i)).toBeVisible({ timeout: 20000 });
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/tus negocios/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByRole("button", { name: /agregar negocio/i })).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible({ timeout: 20000 });

    const businessSectionText = await sectionTextByHeading(page, /tus negocios/i);
    const businessLines = businessSectionText
      .split("\n")
      .map((line) => line.trim())
      .filter((line) => line && !/tus negocios|agregar negocio|tienes 2 de 3 negocios/i.test(line));
    expect(businessLines.length).toBeGreaterThan(0);
  });

  await runStep("Términos y Condiciones", async () => {
    termsUrl = await openLegalPageAndReturn(
      page,
      "Términos y Condiciones",
      /términos y condiciones/i,
      "terminos-y-condiciones"
    );
    expect(termsUrl.length).toBeGreaterThan(0);
  });

  await runStep("Política de Privacidad", async () => {
    privacyUrl = await openLegalPageAndReturn(page, "Política de Privacidad", /política de privacidad/i, "politica-de-privacidad");
    expect(privacyUrl.length).toBeGreaterThan(0);
  });

  await fs.mkdir(path.dirname(REPORT_PATH), { recursive: true });
  await fs.writeFile(
    REPORT_PATH,
    JSON.stringify(
      {
        name: "saleads_mi_negocio_full_test",
        generatedAt: new Date().toISOString(),
        loginUrl: SALEADS_LOGIN_URL ?? "not provided",
        googleAccountEmail: GOOGLE_ACCOUNT_EMAIL,
        statusByField: report,
        evidence: {
          screenshots,
          termsAndConditionsUrl: termsUrl,
          privacyPolicyUrl: privacyUrl,
        },
        details: detailLog,
      },
      null,
      2
    ),
    "utf-8"
  );

  console.table(report);
  const failedSteps = Object.entries(report)
    .filter(([, status]) => status === "FAIL")
    .map(([step]) => step);
  expect(
    failedSteps,
    `Failed steps: ${failedSteps.join(", ")}. See ${REPORT_PATH} for details.`
  ).toEqual([]);
});
