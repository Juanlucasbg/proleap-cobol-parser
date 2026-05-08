import { expect, type BrowserContext, type Locator, type Page, test } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

const GOOGLE_ACCOUNT_EMAIL = process.env.GOOGLE_ACCOUNT_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";
const SALEADS_LOGIN_URL = process.env.SALEADS_LOGIN_URL;

const reportFields = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad",
] as const;

type ReportField = (typeof reportFields)[number];
type StepStatus = "PASS" | "FAIL";

type StepReport = {
  status: StepStatus;
  notes: string[];
  evidence: string[];
};

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function slugify(value: string): string {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 80);
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => undefined);
  await page.waitForTimeout(350);
}

async function pickVisible(candidates: Locator[], timeoutMs = 2_500): Promise<Locator | null> {
  for (const candidate of candidates) {
    const firstCandidate = candidate.first();
    const isVisible = await firstCandidate.isVisible({ timeout: timeoutMs }).catch(() => false);
    if (isVisible) {
      return firstCandidate;
    }
  }
  return null;
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function captureCheckpoint(
  page: Page,
  evidenceDir: string,
  label: string,
  order: number,
  fullPage = false,
): Promise<string> {
  const fileName = `${String(order).padStart(2, "0")}-${slugify(label)}.png`;
  const filePath = path.join(evidenceDir, fileName);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function ensureOnLoginPage(page: Page): Promise<void> {
  if (page.url() !== "about:blank") {
    return;
  }

  if (!SALEADS_LOGIN_URL) {
    throw new Error(
      "No login page is open. Set SALEADS_LOGIN_URL so the test can navigate to the current environment login page.",
    );
  }

  await page.goto(SALEADS_LOGIN_URL, { waitUntil: "domcontentloaded" });
  await waitForUi(page);
}

async function chooseGoogleAccountIfPrompted(page: Page): Promise<void> {
  const chooseAccountSignals = [
    page.getByText(/elige una cuenta|choose an account|select an account/i),
    page.getByText(new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i")),
    page.locator('input[type="email"]'),
  ];

  const accountPromptVisible = await pickVisible(chooseAccountSignals, 7_000);
  if (!accountPromptVisible) {
    return;
  }

  const accountOption = page.getByText(new RegExp(escapeRegex(GOOGLE_ACCOUNT_EMAIL), "i")).first();
  if (await accountOption.isVisible({ timeout: 5_000 }).catch(() => false)) {
    await accountOption.click();
    await waitForUi(page);
    return;
  }

  const emailInput = page.locator('input[type="email"]').first();
  if (await emailInput.isVisible({ timeout: 3_000 }).catch(() => false)) {
    await emailInput.fill(GOOGLE_ACCOUNT_EMAIL);
    const nextButton = await pickVisible(
      [
        page.getByRole("button", { name: /siguiente|next/i }),
        page.getByText(/siguiente|next/i),
      ],
      3_000,
    );

    if (nextButton) {
      await nextButton.click();
      await waitForUi(page);
    }
  }
}

async function openLegalLinkAndValidate(params: {
  appPage: Page;
  context: BrowserContext;
  linkPattern: RegExp;
  headingPattern: RegExp;
  evidenceDir: string;
  screenshotOrder: number;
  screenshotLabel: string;
}): Promise<{ url: string; screenshot: string }> {
  const { appPage, context, linkPattern, headingPattern, evidenceDir, screenshotOrder, screenshotLabel } = params;

  const linkCandidates = [
    appPage
      .locator("section,article,div")
      .filter({ hasText: /secci[oó]n legal/i })
      .getByRole("link", { name: linkPattern }),
    appPage
      .locator("section,article,div")
      .filter({ hasText: /secci[oó]n legal/i })
      .getByText(linkPattern),
    appPage.getByRole("link", { name: linkPattern }),
    appPage.getByText(linkPattern),
  ];

  const legalLink = await pickVisible(linkCandidates, 8_000);
  if (!legalLink) {
    throw new Error(`Could not find legal link matching pattern ${linkPattern}.`);
  }

  const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
  await legalLink.click();

  const popupPage = await popupPromise;
  const destinationPage = popupPage ?? appPage;
  await waitForUi(destinationPage);

  const heading = await pickVisible(
    [
      destinationPage.getByRole("heading", { name: headingPattern }),
      destinationPage.getByText(headingPattern),
    ],
    12_000,
  );
  if (!heading) {
    throw new Error(`Heading ${headingPattern} was not found on legal destination.`);
  }

  const bodyText = await destinationPage.locator("body").innerText();
  if (bodyText.replace(/\s+/g, " ").trim().length < 120) {
    throw new Error("Legal page content is too short to be considered valid.");
  }

  const screenshot = await captureCheckpoint(destinationPage, evidenceDir, screenshotLabel, screenshotOrder, true);
  const finalUrl = destinationPage.url();

  if (popupPage) {
    await popupPage.close();
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUi(appPage);
  }

  return { url: finalUrl, screenshot };
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page, context }) => {
    const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
    const evidenceDir = path.join("artifacts", "saleads-mi-negocio", timestamp);
    await fs.mkdir(evidenceDir, { recursive: true });

    const report = reportFields.reduce<Record<ReportField, StepReport>>(
      (accumulator, field) => ({
        ...accumulator,
        [field]: { status: "FAIL", notes: [], evidence: [] },
      }),
      {} as Record<ReportField, StepReport>,
    );

    const legalUrls: Record<"terminos" | "privacidad", string> = {
      terminos: "",
      privacidad: "",
    };

    let screenshotOrder = 1;

    async function runStep(field: ReportField, callback: () => Promise<void>): Promise<void> {
      try {
        await callback();
        report[field].status = "PASS";
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        report[field].status = "FAIL";
        report[field].notes.push(message);
      }
    }

    await runStep("Login", async () => {
      await ensureOnLoginPage(page);

      const googleLoginButton = await pickVisible(
        [
          page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
          page.getByText(/sign in with google|iniciar sesi[oó]n con google|google/i),
          page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
        ],
        12_000,
      );

      if (!googleLoginButton) {
        throw new Error("Google login button was not found.");
      }

      const popupPromise = context.waitForEvent("page", { timeout: 10_000 }).catch(() => null);
      await googleLoginButton.click();
      await waitForUi(page);

      const popupPage = await popupPromise;
      const authPage = popupPage ?? page;
      await waitForUi(authPage);
      await chooseGoogleAccountIfPrompted(authPage);

      if (popupPage) {
        await popupPage.waitForEvent("close", { timeout: 45_000 }).catch(() => undefined);
        await page.bringToFront();
      }

      await waitForUi(page);

      const sidebarCandidate = await pickVisible(
        [
          page.locator("aside, nav").filter({ hasText: /negocio|mi negocio/i }),
          page.getByText(/mi negocio|negocio/i),
        ],
        45_000,
      );
      if (!sidebarCandidate) {
        throw new Error("Main application interface did not appear after Google login.");
      }

      const dashboardShot = await captureCheckpoint(page, evidenceDir, "dashboard-loaded", screenshotOrder++);
      report["Login"].evidence.push(dashboardShot);
    });

    await runStep("Mi Negocio menu", async () => {
      const sidebar = await pickVisible(
        [
          page.locator("aside, nav").filter({ hasText: /mi negocio|negocio/i }),
          page.getByText(/mi negocio|negocio/i),
        ],
        15_000,
      );
      if (!sidebar) {
        throw new Error("Left sidebar is not visible.");
      }

      const miNegocioOption = await pickVisible(
        [
          sidebar.getByRole("button", { name: /mi negocio/i }),
          sidebar.getByRole("link", { name: /mi negocio/i }),
          page.getByRole("button", { name: /mi negocio/i }),
          page.getByRole("link", { name: /mi negocio/i }),
          page.getByText(/mi negocio/i),
        ],
        8_000,
      );

      if (!miNegocioOption) {
        throw new Error("'Mi Negocio' option was not found.");
      }

      await clickAndWait(miNegocioOption, page);
      await expect(page.getByText(/agregar negocio/i)).toBeVisible();
      await expect(page.getByText(/administrar negocios/i)).toBeVisible();

      const expandedMenuShot = await captureCheckpoint(page, evidenceDir, "mi-negocio-expanded-menu", screenshotOrder++);
      report["Mi Negocio menu"].evidence.push(expandedMenuShot);
    });

    await runStep("Agregar Negocio modal", async () => {
      const addBusinessEntry = await pickVisible(
        [
          page
            .locator("aside, nav")
            .filter({ hasText: /mi negocio|negocio/i })
            .getByRole("button", { name: /agregar negocio/i }),
          page
            .locator("aside, nav")
            .filter({ hasText: /mi negocio|negocio/i })
            .getByRole("link", { name: /agregar negocio/i }),
          page.getByText(/agregar negocio/i),
        ],
        8_000,
      );

      if (!addBusinessEntry) {
        throw new Error("'Agregar Negocio' entry was not found.");
      }

      await clickAndWait(addBusinessEntry, page);

      await expect(page.getByText(/crear nuevo negocio/i)).toBeVisible();
      await expect(page.getByLabel(/nombre del negocio/i)).toBeVisible();
      await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
      await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();

      const modalShot = await captureCheckpoint(page, evidenceDir, "agregar-negocio-modal", screenshotOrder++);
      report["Agregar Negocio modal"].evidence.push(modalShot);

      const businessNameInput = page.getByLabel(/nombre del negocio/i).first();
      await businessNameInput.fill("Negocio Prueba Automatizacion");
      await clickAndWait(page.getByRole("button", { name: /cancelar/i }), page);
    });

    await runStep("Administrar Negocios view", async () => {
      const adminEntryVisible = await page.getByText(/administrar negocios/i).isVisible({ timeout: 2_500 }).catch(() => false);
      if (!adminEntryVisible) {
        const miNegocioOption = await pickVisible(
          [
            page.getByRole("button", { name: /mi negocio/i }),
            page.getByRole("link", { name: /mi negocio/i }),
            page.getByText(/mi negocio/i),
          ],
          6_000,
        );
        if (miNegocioOption) {
          await clickAndWait(miNegocioOption, page);
        }
      }

      const adminEntry = await pickVisible(
        [
          page.getByRole("button", { name: /administrar negocios/i }),
          page.getByRole("link", { name: /administrar negocios/i }),
          page.getByText(/administrar negocios/i),
        ],
        10_000,
      );

      if (!adminEntry) {
        throw new Error("'Administrar Negocios' was not found.");
      }

      await clickAndWait(adminEntry, page);
      await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible();
      await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
      await expect(page.getByText(/tus negocios/i)).toBeVisible();
      await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible();

      const accountViewShot = await captureCheckpoint(page, evidenceDir, "administrar-negocios-view", screenshotOrder++, true);
      report["Administrar Negocios view"].evidence.push(accountViewShot);
    });

    await runStep("Información General", async () => {
      const infoSection = page.locator("section,article,div").filter({ hasText: /informaci[oó]n general/i }).first();
      await expect(infoSection).toBeVisible();

      const sectionText = (await infoSection.innerText()).replace(/\s+/g, " ").trim();
      const hasEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(sectionText);
      const hasLikelyName = sectionText
        .split(/\s+/)
        .some((token) => token.length >= 3 && !token.includes("@") && !/business|plan|cambiar|informaci[oó]n|general/i.test(token));

      if (!hasEmail) {
        throw new Error("User email was not found in 'Información General'.");
      }
      if (!hasLikelyName) {
        throw new Error("User name was not found in 'Información General'.");
      }

      await expect(infoSection.getByText(/business plan/i)).toBeVisible();
      await expect(infoSection.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
    });

    await runStep("Detalles de la Cuenta", async () => {
      const detailsSection = page.locator("section,article,div").filter({ hasText: /detalles de la cuenta/i }).first();
      await expect(detailsSection).toBeVisible();
      await expect(detailsSection.getByText(/cuenta creada/i)).toBeVisible();
      await expect(detailsSection.getByText(/estado activo/i)).toBeVisible();
      await expect(detailsSection.getByText(/idioma seleccionado/i)).toBeVisible();
    });

    await runStep("Tus Negocios", async () => {
      const businessSection = page.locator("section,article,div").filter({ hasText: /tus negocios/i }).first();
      await expect(businessSection).toBeVisible();
      await expect(businessSection.getByText(/agregar negocio/i)).toBeVisible();
      await expect(businessSection.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
    });

    await runStep("Términos y Condiciones", async () => {
      const result = await openLegalLinkAndValidate({
        appPage: page,
        context,
        linkPattern: /t[ée]rminos y condiciones/i,
        headingPattern: /t[ée]rminos y condiciones/i,
        evidenceDir,
        screenshotOrder: screenshotOrder++,
        screenshotLabel: "terminos-y-condiciones",
      });
      legalUrls.terminos = result.url;
      report["Términos y Condiciones"].evidence.push(result.screenshot);
      report["Términos y Condiciones"].notes.push(`Final URL: ${result.url}`);
    });

    await runStep("Política de Privacidad", async () => {
      const result = await openLegalLinkAndValidate({
        appPage: page,
        context,
        linkPattern: /pol[ií]tica de privacidad/i,
        headingPattern: /pol[ií]tica de privacidad/i,
        evidenceDir,
        screenshotOrder: screenshotOrder++,
        screenshotLabel: "politica-de-privacidad",
      });
      legalUrls.privacidad = result.url;
      report["Política de Privacidad"].evidence.push(result.screenshot);
      report["Política de Privacidad"].notes.push(`Final URL: ${result.url}`);
    });

    const results = Object.fromEntries(
      reportFields.map((field) => [field, report[field].status]),
    ) as Record<ReportField, StepStatus>;

    const finalReport = {
      testName: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      environment: {
        saleadsLoginUrl: SALEADS_LOGIN_URL ?? "pre-opened login page",
        googleAccountEmail: GOOGLE_ACCOUNT_EMAIL,
      },
      results,
      details: report,
      legalUrls,
      evidenceDirectory: evidenceDir,
    };

    const reportPath = path.join(evidenceDir, "final-report.json");
    await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
    console.log("SALEADS_MI_NEGOCIO_FINAL_REPORT");
    console.log(JSON.stringify(finalReport, null, 2));

    const failedSteps = reportFields.filter((field) => report[field].status === "FAIL");
    expect(
      failedSteps,
      `At least one validation failed. Review ${reportPath} for full details.`,
    ).toEqual([]);
  });
});
