import { expect, test, type BrowserContext, type Locator, type Page } from "@playwright/test";

type ReportKey =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Informacion General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Terminos y Condiciones"
  | "Politica de Privacidad";

type ReportStatus = "PASS" | "FAIL";
type FinalReport = Record<ReportKey, ReportStatus>;

const REPORT_KEYS: ReportKey[] = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informacion General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Terminos y Condiciones",
  "Politica de Privacidad",
];

const EMAIL_UNDER_TEST = "juanlucasbarbiergarzon@gmail.com";

function buildInitialReport(): FinalReport {
  return REPORT_KEYS.reduce((accumulator, key) => {
    accumulator[key] = "FAIL";
    return accumulator;
  }, {} as FinalReport);
}

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => undefined);
  await page.waitForTimeout(500);
}

async function firstVisible(locators: Locator[], timeoutMs = 8_000): Promise<Locator | null> {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const locator of locators) {
      const candidate = locator.first();
      if (await candidate.isVisible().catch(() => false)) {
        return candidate;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  return null;
}

async function clickFirstVisible(
  page: Page,
  locators: Locator[],
  missingMessage: string,
): Promise<Locator> {
  const selected = await firstVisible(locators);
  expect(selected, missingMessage).not.toBeNull();
  await selected!.click();
  await waitForUi(page);
  return selected!;
}

async function captureCheckpoint(page: Page, fileName: string, fullPage = false): Promise<void> {
  await page.screenshot({
    path: test.info().outputPath(fileName),
    fullPage,
  });
}

async function selectGoogleAccountIfPresent(candidatePages: Page[], accountEmail: string): Promise<void> {
  const emailPattern = new RegExp(escapeRegex(accountEmail), "i");

  for (const currentPage of candidatePages) {
    const accountLocator = currentPage.getByText(emailPattern).first();
    if (await accountLocator.isVisible().catch(() => false)) {
      await accountLocator.click();
      await waitForUi(currentPage);
      return;
    }
  }
}

async function getSectionByHeading(page: Page, headingPattern: RegExp): Promise<Locator> {
  const heading = await firstVisible([
    page.getByRole("heading", { name: headingPattern }),
    page.getByText(headingPattern),
  ]);

  expect(heading, `Expected section heading ${headingPattern} to be visible`).not.toBeNull();
  const section = heading!.locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
  await expect(section).toBeVisible();
  return section;
}

async function openLegalPageAndReturn(
  appPage: Page,
  context: BrowserContext,
  linkPattern: RegExp,
): Promise<Page> {
  const link = await firstVisible([
    appPage.getByRole("link", { name: linkPattern }),
    appPage.getByText(linkPattern),
  ]);
  expect(link, `Expected legal link ${linkPattern} to be visible`).not.toBeNull();

  const popupPromise = context.waitForEvent("page", { timeout: 7_000 }).catch(() => null);
  await link!.click();
  await waitForUi(appPage);
  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await popup.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => undefined);
    return popup;
  }

  await appPage.waitForLoadState("domcontentloaded");
  await appPage.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => undefined);
  return appPage;
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  test.setTimeout(300_000);

  const finalReport = buildInitialReport();
  const failureMessages: string[] = [];
  const legalUrls: Record<string, string> = {};

  const markStep = async (key: ReportKey, stepRunner: () => Promise<void>): Promise<boolean> => {
    try {
      await stepRunner();
      finalReport[key] = "PASS";
      return true;
    } catch (error) {
      finalReport[key] = "FAIL";
      failureMessages.push(`${key}: ${error instanceof Error ? error.message : String(error)}`);
      return false;
    }
  };

  const loginSucceeded = await markStep("Login", async () => {
    const loginUrl =
      process.env.SALEADS_LOGIN_URL ??
      process.env.SALEADS_BASE_URL ??
      process.env.BASE_URL ??
      process.env.PLAYWRIGHT_TEST_BASE_URL;

    if (page.url() === "about:blank") {
      expect(
        loginUrl,
        "No page is open. Set SALEADS_LOGIN_URL (or SALEADS_BASE_URL / BASE_URL) to the login page URL.",
      ).toBeTruthy();
      await page.goto(loginUrl!, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    await clickFirstVisible(
      page,
      [
        page.getByRole("button", { name: /google|sign in|iniciar sesi[oó]n/i }),
        page.getByRole("link", { name: /google|sign in|iniciar sesi[oó]n/i }),
        page.getByText(/google/i),
      ],
      "Could not find the Sign in with Google trigger.",
    );

    const loginPopup = await context.waitForEvent("page", { timeout: 5_000 }).catch(() => null);
    if (loginPopup) {
      await loginPopup.waitForLoadState("domcontentloaded");
      await selectGoogleAccountIfPresent([loginPopup], EMAIL_UNDER_TEST);
    } else {
      await selectGoogleAccountIfPresent([page], EMAIL_UNDER_TEST);
    }

    await page.bringToFront();
    await waitForUi(page);

    const mainInterface = await firstVisible([
      page.locator("main"),
      page.getByRole("navigation"),
      page.locator("aside"),
    ]);
    expect(mainInterface, "Main application interface did not appear after login.").not.toBeNull();

    const sidebar = await firstVisible([
      page.locator("aside"),
      page.getByRole("navigation"),
      page.locator('[data-testid*="sidebar"]'),
    ]);
    expect(sidebar, "Left sidebar navigation is not visible after login.").not.toBeNull();

    await captureCheckpoint(page, "01-dashboard-loaded.png");
  });

  if (!loginSucceeded) {
    for (const blockedKey of REPORT_KEYS.filter((key) => key !== "Login")) {
      failureMessages.push(`${blockedKey}: blocked because login did not complete.`);
    }
  } else {
    const menuSucceeded = await markStep("Mi Negocio menu", async () => {
      await clickFirstVisible(
        page,
        [page.getByText(/^Negocio$/i), page.getByRole("button", { name: /^Negocio$/i }), page.getByText(/Negocio/i)],
        "Could not find the Negocio section in the left sidebar.",
      );

      await clickFirstVisible(
        page,
        [page.getByText(/Mi Negocio/i), page.getByRole("button", { name: /Mi Negocio/i }), page.getByRole("link", { name: /Mi Negocio/i })],
        "Could not find the Mi Negocio option.",
      );

      await expect(page.getByText(/Agregar Negocio/i)).toBeVisible();
      await expect(page.getByText(/Administrar Negocios/i)).toBeVisible();

      await captureCheckpoint(page, "02-mi-negocio-menu-expanded.png");
    });

    if (!menuSucceeded) {
      for (const blockedKey of REPORT_KEYS.filter((key) => !["Login", "Mi Negocio menu"].includes(key))) {
        failureMessages.push(`${blockedKey}: blocked because Mi Negocio menu could not be opened.`);
      }
    } else {
      await markStep("Agregar Negocio modal", async () => {
        await clickFirstVisible(
          page,
          [page.getByRole("button", { name: /Agregar Negocio/i }), page.getByRole("link", { name: /Agregar Negocio/i }), page.getByText(/Agregar Negocio/i)],
          "Could not find Agregar Negocio in the expanded submenu.",
        );

        await expect(page.getByRole("heading", { name: /Crear Nuevo Negocio/i })).toBeVisible();
        await expect(
          firstVisible([
            page.getByLabel(/Nombre del Negocio/i),
            page.getByPlaceholder(/Nombre del Negocio/i),
            page.locator('input[name*="negocio" i]'),
          ]),
        ).resolves.not.toBeNull();
        await expect(page.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
        await expect(page.getByRole("button", { name: /Cancelar/i })).toBeVisible();
        await expect(page.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

        await captureCheckpoint(page, "03-agregar-negocio-modal.png");

        const businessNameInput = await firstVisible([
          page.getByLabel(/Nombre del Negocio/i),
          page.getByPlaceholder(/Nombre del Negocio/i),
          page.locator('input[name*="negocio" i]'),
        ]);
        expect(businessNameInput, "Nombre del Negocio input should exist").not.toBeNull();
        await businessNameInput!.click();
        await businessNameInput!.fill("Negocio Prueba Automatizacion");
        await waitForUi(page);

        await clickFirstVisible(
          page,
          [page.getByRole("button", { name: /Cancelar/i }), page.getByText(/^Cancelar$/i)],
          "Could not click Cancelar in Agregar Negocio modal.",
        );
      });

      const adminViewSucceeded = await markStep("Administrar Negocios view", async () => {
        const miNegocioEntry = await firstVisible([
          page.getByText(/Mi Negocio/i),
          page.getByRole("button", { name: /Mi Negocio/i }),
          page.getByRole("link", { name: /Mi Negocio/i }),
        ]);
        expect(miNegocioEntry, "Mi Negocio entry should be visible before opening Administrar Negocios.").not.toBeNull();
        await miNegocioEntry!.click();
        await waitForUi(page);

        await clickFirstVisible(
          page,
          [page.getByText(/Administrar Negocios/i), page.getByRole("button", { name: /Administrar Negocios/i }), page.getByRole("link", { name: /Administrar Negocios/i })],
          "Could not open Administrar Negocios.",
        );

        await expect(page.getByText(/Informaci[oó]n General/i)).toBeVisible();
        await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
        await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
        await expect(page.getByText(/Secci[oó]n Legal/i)).toBeVisible();

        await captureCheckpoint(page, "04-administrar-negocios-page.png", true);
      });

      if (!adminViewSucceeded) {
        for (const blockedKey of [
          "Informacion General",
          "Detalles de la Cuenta",
          "Tus Negocios",
          "Terminos y Condiciones",
          "Politica de Privacidad",
        ] as ReportKey[]) {
          failureMessages.push(`${blockedKey}: blocked because Administrar Negocios view was unavailable.`);
        }
      } else {
        await markStep("Informacion General", async () => {
          const infoSection = await getSectionByHeading(page, /Informaci[oó]n General/i);
          const infoText = await infoSection.innerText();

          expect(infoText, "Expected user email to be visible in Informacion General.").toMatch(
            /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i,
          );
          expect(infoText, "Expected user name to be visible in Informacion General.").toMatch(
            /[A-Za-zÁÉÍÓÚÑáéíóúñ]{3,}\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}/,
          );
          expect(infoText).toMatch(/BUSINESS PLAN/i);

          const changePlanButton = await firstVisible([
            infoSection.getByRole("button", { name: /Cambiar Plan/i }),
            infoSection.getByRole("link", { name: /Cambiar Plan/i }),
            page.getByRole("button", { name: /Cambiar Plan/i }),
          ]);
          expect(changePlanButton, "Cambiar Plan button is not visible.").not.toBeNull();
        });

        await markStep("Detalles de la Cuenta", async () => {
          const detailsSection = await getSectionByHeading(page, /Detalles de la Cuenta/i);
          await expect(detailsSection.getByText(/Cuenta creada/i)).toBeVisible();
          await expect(detailsSection.getByText(/Estado activo/i)).toBeVisible();
          await expect(detailsSection.getByText(/Idioma seleccionado/i)).toBeVisible();
        });

        await markStep("Tus Negocios", async () => {
          const businessesSection = await getSectionByHeading(page, /Tus Negocios/i);
          const businessListCandidate = await firstVisible([
            businessesSection.locator("ul li"),
            businessesSection.locator("table tbody tr"),
            businessesSection.locator('[role="row"]'),
            businessesSection.locator('[class*="negocio" i]'),
            businessesSection.locator('[class*="business" i]'),
          ]);
          expect(businessListCandidate, "Business list is not visible in Tus Negocios section.").not.toBeNull();

          const addBusinessButton = await firstVisible([
            businessesSection.getByRole("button", { name: /Agregar Negocio/i }),
            businessesSection.getByRole("link", { name: /Agregar Negocio/i }),
            page.getByRole("button", { name: /Agregar Negocio/i }),
          ]);
          expect(addBusinessButton, "Agregar Negocio button is missing in Tus Negocios.").not.toBeNull();

          await expect(businessesSection.getByText(/Tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible();
        });

        await markStep("Terminos y Condiciones", async () => {
          const legalPage = await openLegalPageAndReturn(page, context, /T[eé]rminos y Condiciones/i);

          const heading = await firstVisible([
            legalPage.getByRole("heading", { name: /T[eé]rminos y Condiciones/i }),
            legalPage.getByText(/T[eé]rminos y Condiciones/i),
          ]);
          expect(heading, "Expected Términos y Condiciones heading on legal page.").not.toBeNull();

          const legalContent = await firstVisible([
            legalPage.locator("main p"),
            legalPage.locator("article p"),
            legalPage.locator("p"),
          ]);
          expect(legalContent, "Expected legal content text for Términos y Condiciones.").not.toBeNull();

          await captureCheckpoint(legalPage, "05-terminos-y-condiciones.png", true);
          legalUrls["Terminos y Condiciones URL"] = legalPage.url();

          if (legalPage !== page) {
            await legalPage.close();
            await page.bringToFront();
            await waitForUi(page);
          } else {
            await page.goBack().catch(() => undefined);
            await waitForUi(page);
          }
        });

        await markStep("Politica de Privacidad", async () => {
          const legalPage = await openLegalPageAndReturn(page, context, /Pol[ií]tica de Privacidad/i);

          const heading = await firstVisible([
            legalPage.getByRole("heading", { name: /Pol[ií]tica de Privacidad/i }),
            legalPage.getByText(/Pol[ií]tica de Privacidad/i),
          ]);
          expect(heading, "Expected Política de Privacidad heading on legal page.").not.toBeNull();

          const legalContent = await firstVisible([
            legalPage.locator("main p"),
            legalPage.locator("article p"),
            legalPage.locator("p"),
          ]);
          expect(legalContent, "Expected legal content text for Política de Privacidad.").not.toBeNull();

          await captureCheckpoint(legalPage, "06-politica-de-privacidad.png", true);
          legalUrls["Politica de Privacidad URL"] = legalPage.url();

          if (legalPage !== page) {
            await legalPage.close();
            await page.bringToFront();
            await waitForUi(page);
          } else {
            await page.goBack().catch(() => undefined);
            await waitForUi(page);
          }
        });
      }
    }
  }

  const finalPrintableReport = {
    Login: finalReport.Login,
    "Mi Negocio menu": finalReport["Mi Negocio menu"],
    "Agregar Negocio modal": finalReport["Agregar Negocio modal"],
    "Administrar Negocios view": finalReport["Administrar Negocios view"],
    "Informacion General": finalReport["Informacion General"],
    "Detalles de la Cuenta": finalReport["Detalles de la Cuenta"],
    "Tus Negocios": finalReport["Tus Negocios"],
    "Terminos y Condiciones": finalReport["Terminos y Condiciones"],
    "Politica de Privacidad": finalReport["Politica de Privacidad"],
    ...legalUrls,
  };

  const reportBody = `${JSON.stringify(finalPrintableReport, null, 2)}\n`;
  console.log("SaleADS Mi Negocio final report:");
  console.log(reportBody);
  test.info().attach("saleads-mi-negocio-final-report", {
    body: reportBody,
    contentType: "application/json",
  });

  expect(
    failureMessages,
    `One or more validations failed.\n${failureMessages.map((message) => `- ${message}`).join("\n")}`,
  ).toEqual([]);
});
