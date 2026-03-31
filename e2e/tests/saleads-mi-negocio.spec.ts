import { expect, type BrowserContext, type Locator, type Page, test } from "@playwright/test";
import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";

type StepStatus = "PASS" | "FAIL";

type WorkflowReport = {
  Login: StepStatus;
  "Mi Negocio menu": StepStatus;
  "Agregar Negocio modal": StepStatus;
  "Administrar Negocios view": StepStatus;
  "Información General": StepStatus;
  "Detalles de la Cuenta": StepStatus;
  "Tus Negocios": StepStatus;
  "Términos y Condiciones": StepStatus;
  "Política de Privacidad": StepStatus;
};

const GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const RUN_ID = new Date().toISOString().replace(/[:.]/g, "-");
const SCREENSHOT_DIR = path.join(process.cwd(), "artifacts", "screenshots", RUN_ID);
const REPORT_DIR = path.join(process.cwd(), "artifacts", "reports");

const LEGAL_URLS: { terms: string; privacy: string } = {
  terms: "",
  privacy: "",
};

const report: WorkflowReport = {
  Login: "FAIL",
  "Mi Negocio menu": "FAIL",
  "Agregar Negocio modal": "FAIL",
  "Administrar Negocios view": "FAIL",
  "Información General": "FAIL",
  "Detalles de la Cuenta": "FAIL",
  "Tus Negocios": "FAIL",
  "Términos y Condiciones": "FAIL",
  "Política de Privacidad": "FAIL",
};

const failures: string[] = [];

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 7_000 }).catch(() => {
    // Some environments keep websocket traffic open; domcontentloaded is sufficient.
  });
  await page.waitForTimeout(350);
}

async function clickAndWait(page: Page, locator: Locator): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function takeScreenshot(page: Page, name: string, fullPage = false): Promise<void> {
  await mkdir(SCREENSHOT_DIR, { recursive: true });
  const safeName = name.toLowerCase().replace(/[^a-z0-9-_]+/g, "-");
  await page.screenshot({
    path: path.join(SCREENSHOT_DIR, `${safeName}.png`),
    fullPage,
  });
}

async function pickVisible(locatorCandidates: Locator[]): Promise<Locator | null> {
  for (const locator of locatorCandidates) {
    if (await locator.first().isVisible().catch(() => false)) {
      return locator.first();
    }
  }
  return null;
}

async function findSectionContainer(page: Page, headingPattern: RegExp): Promise<Locator> {
  const heading = page.getByText(headingPattern).first();
  await expect(heading).toBeVisible();

  const sectionCandidate = heading.locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
  if (await sectionCandidate.isVisible().catch(() => false)) {
    return sectionCandidate;
  }

  return page.locator("body");
}

async function ensureOnLoginPage(page: Page): Promise<void> {
  if (page.url() !== "about:blank") {
    return;
  }

  const startUrl =
    process.env.SALEADS_START_URL ?? process.env.BASE_URL ?? process.env.PLAYWRIGHT_TEST_BASE_URL;

  if (!startUrl) {
    throw new Error(
      "No start URL available. Set SALEADS_START_URL/BASE_URL/PLAYWRIGHT_TEST_BASE_URL to the current SaleADS environment login page."
    );
  }

  await page.goto(startUrl, { waitUntil: "domcontentloaded" });
  await waitForUi(page);
}

async function clickGoogleLogin(page: Page, context: BrowserContext): Promise<void> {
  const googleButton = await pickVisible([
    page.getByRole("button", { name: /sign in with google/i }),
    page.getByRole("button", { name: /iniciar sesi[oó]n con google/i }),
    page.getByRole("button", { name: /continuar con google/i }),
    page.getByRole("link", { name: /google/i }),
    page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
  ]);

  if (!googleButton) {
    throw new Error("Google login button/link was not found.");
  }

  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await clickAndWait(page, googleButton);
  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    const accountOption = await pickVisible([
      popup.getByText(GOOGLE_EMAIL, { exact: false }),
      popup.getByRole("button", { name: new RegExp(GOOGLE_EMAIL, "i") }),
      popup.getByRole("link", { name: new RegExp(GOOGLE_EMAIL, "i") }),
    ]);

    if (accountOption) {
      await accountOption.click();
      await popup.waitForLoadState("domcontentloaded");
    }
  } else {
    const accountOption = await pickVisible([
      page.getByText(GOOGLE_EMAIL, { exact: false }),
      page.getByRole("button", { name: new RegExp(GOOGLE_EMAIL, "i") }),
      page.getByRole("link", { name: new RegExp(GOOGLE_EMAIL, "i") }),
    ]);

    if (accountOption) {
      await clickAndWait(page, accountOption);
    }
  }

  await page.bringToFront();
  await waitForUi(page);
}

async function withStep(name: keyof WorkflowReport, fn: () => Promise<void>): Promise<void> {
  try {
    await fn();
    report[name] = "PASS";
  } catch (error) {
    report[name] = "FAIL";
    failures.push(`${name}: ${(error as Error).message}`);
  }
}

async function openLegalLinkAndReturn(
  page: Page,
  context: BrowserContext,
  linkName: RegExp,
  headingName: RegExp,
  screenshotName: string,
  urlKey: "terms" | "privacy"
): Promise<void> {
  const appTab = page;
  const legalLink = await pickVisible([
    appTab.getByRole("link", { name: linkName }),
    appTab.getByRole("button", { name: linkName }),
    appTab.getByText(linkName),
  ]);

  if (!legalLink) {
    throw new Error(`Legal link was not found: ${linkName}`);
  }

  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await legalLink.click();
  const popup = await popupPromise;

  const targetPage = popup ?? appTab;
  await targetPage.waitForLoadState("domcontentloaded");
  await waitForUi(targetPage);

  const heading = await pickVisible([
    targetPage.getByRole("heading", { name: headingName }),
    targetPage.getByText(headingName),
  ]);

  if (!heading) {
    throw new Error(`Heading not found for legal page: ${headingName}`);
  }

  await expect(heading).toBeVisible();
  await expect(targetPage.locator("body")).toContainText(/.{80,}/s);
  LEGAL_URLS[urlKey] = targetPage.url();
  await takeScreenshot(targetPage, screenshotName, true);

  if (popup) {
    await popup.close();
    await appTab.bringToFront();
  } else {
    await appTab.goBack().catch(() => {
      // If route-based navigation has no history entry, continue in current tab.
    });
    await appTab.bringToFront();
    await waitForUi(appTab);
  }
}

async function writeReport(): Promise<void> {
  await mkdir(REPORT_DIR, { recursive: true });

  const jsonPath = path.join(REPORT_DIR, `saleads-mi-negocio-${RUN_ID}.json`);
  const mdPath = path.join(REPORT_DIR, `saleads-mi-negocio-${RUN_ID}.md`);

  const jsonContent = JSON.stringify(
    {
      generatedAt: new Date().toISOString(),
      report,
      legalUrls: LEGAL_URLS,
      failures,
      screenshotDir: SCREENSHOT_DIR,
    },
    null,
    2
  );

  const markdownContent = `# SaleADS Mi Negocio Workflow Report

- Generated at: ${new Date().toISOString()}
- Screenshot directory: \`${SCREENSHOT_DIR}\`
- Terms URL: ${LEGAL_URLS.terms || "N/A"}
- Privacy URL: ${LEGAL_URLS.privacy || "N/A"}

## Final Status

| Checkpoint | Result |
| --- | --- |
| Login | ${report.Login} |
| Mi Negocio menu | ${report["Mi Negocio menu"]} |
| Agregar Negocio modal | ${report["Agregar Negocio modal"]} |
| Administrar Negocios view | ${report["Administrar Negocios view"]} |
| Información General | ${report["Información General"]} |
| Detalles de la Cuenta | ${report["Detalles de la Cuenta"]} |
| Tus Negocios | ${report["Tus Negocios"]} |
| Términos y Condiciones | ${report["Términos y Condiciones"]} |
| Política de Privacidad | ${report["Política de Privacidad"]} |

## Failures

${failures.length > 0 ? failures.map((failure) => `- ${failure}`).join("\n") : "- None"}
`;

  await Promise.all([writeFile(jsonPath, jsonContent, "utf8"), writeFile(mdPath, markdownContent, "utf8")]);

  // eslint-disable-next-line no-console
  console.table(report);
  // eslint-disable-next-line no-console
  console.log(`Terms URL: ${LEGAL_URLS.terms || "N/A"}`);
  // eslint-disable-next-line no-console
  console.log(`Privacy URL: ${LEGAL_URLS.privacy || "N/A"}`);
  // eslint-disable-next-line no-console
  console.log(`Report written to: ${jsonPath}`);
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  await ensureOnLoginPage(page);

  await withStep("Login", async () => {
    const sidebarAtStart = await page
      .getByText(/mi negocio|negocio/i)
      .first()
      .isVisible()
      .catch(() => false);

    if (!sidebarAtStart) {
      await clickGoogleLogin(page, context);
    }

    await expect(page.locator("aside, nav").first()).toBeVisible();
    await expect(page.getByText(/mi negocio|negocio/i).first()).toBeVisible();
    await takeScreenshot(page, "dashboard-loaded");
  });

  await withStep("Mi Negocio menu", async () => {
    const negocioSection = await pickVisible([
      page.getByText(/^Negocio$/i),
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i }),
    ]);

    if (negocioSection) {
      await clickAndWait(page, negocioSection);
    }

    const miNegocio = await pickVisible([
      page.getByText(/^Mi Negocio$/i),
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
    ]);

    if (!miNegocio) {
      throw new Error("Mi Negocio option not found in sidebar.");
    }

    await clickAndWait(page, miNegocio);
    await expect(page.getByText(/^Agregar Negocio$/i)).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i)).toBeVisible();
    await takeScreenshot(page, "mi-negocio-menu-expanded");
  });

  await withStep("Agregar Negocio modal", async () => {
    await clickAndWait(page, page.getByText(/^Agregar Negocio$/i).first());
    await expect(page.getByText(/^Crear Nuevo Negocio$/i)).toBeVisible();
    await expect(page.getByLabel(/Nombre del Negocio/i)).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible();

    await page.getByLabel(/Nombre del Negocio/i).fill("Negocio Prueba Automatización");
    await takeScreenshot(page, "agregar-negocio-modal");
    await clickAndWait(page, page.getByRole("button", { name: /^Cancelar$/i }));
  });

  await withStep("Administrar Negocios view", async () => {
    const miNegocio = await pickVisible([
      page.getByText(/^Mi Negocio$/i),
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
    ]);
    if (miNegocio) {
      await clickAndWait(page, miNegocio);
    }

    await clickAndWait(page, page.getByText(/^Administrar Negocios$/i).first());
    await expect(page.getByText(/^Información General$/i)).toBeVisible();
    await expect(page.getByText(/^Detalles de la Cuenta$/i)).toBeVisible();
    await expect(page.getByText(/^Tus Negocios$/i)).toBeVisible();
    await expect(page.getByText(/Sección Legal/i)).toBeVisible();
    await takeScreenshot(page, "administrar-negocios-view", true);
  });

  await withStep("Información General", async () => {
    const infoSection = await findSectionContainer(page, /^Información General$/i);
    await expect(infoSection).toContainText(/BUSINESS PLAN/i);
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

    const visibleText = (await infoSection.innerText()).replace(/\s+/g, " ");
    const hasEmail = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(visibleText);
    if (!hasEmail) {
      throw new Error("User email was not found in Información General.");
    }

    const candidateLines = visibleText
      .split(" ")
      .filter((token) => token.length > 2 && !token.includes("@") && !/\d/.test(token));
    if (candidateLines.length < 2) {
      throw new Error("User name was not detected in Información General.");
    }
  });

  await withStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/^Detalles de la Cuenta$/i)).toBeVisible();
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await withStep("Tus Negocios", async () => {
    await expect(page.getByText(/^Tus Negocios$/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.locator("body")).toContainText(/Negocio|business/i);
  });

  await withStep("Términos y Condiciones", async () => {
    await openLegalLinkAndReturn(
      page,
      context,
      /T[ée]rminos y Condiciones/i,
      /T[ée]rminos y Condiciones/i,
      "terminos-y-condiciones",
      "terms"
    );
  });

  await withStep("Política de Privacidad", async () => {
    await openLegalLinkAndReturn(
      page,
      context,
      /Pol[ií]tica de Privacidad/i,
      /Pol[ií]tica de Privacidad/i,
      "politica-de-privacidad",
      "privacy"
    );
  });

  await writeReport();

  expect(failures, `Workflow failures:\n${failures.join("\n")}`).toEqual([]);
});
