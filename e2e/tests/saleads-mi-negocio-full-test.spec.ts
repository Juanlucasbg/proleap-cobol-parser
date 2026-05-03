import * as fs from "node:fs/promises";
import * as path from "node:path";
import { expect, Locator, Page, test } from "@playwright/test";

type StepKey =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Informacion General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Terminos y Condiciones"
  | "Politica de Privacidad";

type StepResult = Record<StepKey, "PASS" | "FAIL">;
type StepFailure = Partial<Record<StepKey, string>>;

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const STEP_ORDER: StepKey[] = [
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

const STEP_DISPLAY: Record<StepKey, string> = {
  Login: "Login",
  "Mi Negocio menu": "Mi Negocio menu",
  "Agregar Negocio modal": "Agregar Negocio modal",
  "Administrar Negocios view": "Administrar Negocios view",
  "Informacion General": "Informacion General",
  "Detalles de la Cuenta": "Detalles de la Cuenta",
  "Tus Negocios": "Tus Negocios",
  "Terminos y Condiciones": "Terminos y Condiciones",
  "Politica de Privacidad": "Politica de Privacidad",
};

function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function textMatcher(label: string): RegExp {
  return new RegExp(escapeRegExp(label), "i");
}

function accentInsensitiveMatcher(label: string): RegExp {
  const charMap: Record<string, string> = {
    a: "[aáàäâã]",
    e: "[eéèëê]",
    i: "[iíìïî]",
    o: "[oóòöôõ]",
    u: "[uúùüû]",
    n: "[nñ]",
    c: "[cç]",
  };

  const pattern = label
    .split("")
    .map((char) => {
      if (/\s/.test(char)) {
        return "\\s+";
      }

      const lower = char.toLowerCase();
      return charMap[lower] ?? escapeRegExp(char);
    })
    .join("");

  return new RegExp(pattern, "i");
}

function toSlug(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, "-");
}

function toErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(1000);
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUi(page);
}

async function waitForPageClose(page: Page, timeoutMs = 25_000): Promise<void> {
  const deadline = Date.now() + timeoutMs;

  while (!page.isClosed() && Date.now() < deadline) {
    await page.waitForTimeout(200);
  }
}

async function firstVisibleOrThrow(
  _page: Page,
  locators: Locator[],
  errorMessage: string
): Promise<Locator> {
  for (const locator of locators) {
    if (await locator.first().isVisible().catch(() => false)) {
      return locator.first();
    }
  }
  throw new Error(errorMessage);
}

async function capture(
  page: Page,
  fileName: string,
  options?: { fullPage?: boolean }
): Promise<void> {
  const outputPath = path.join("artifacts", "screenshots", fileName);
  await fs.mkdir(path.dirname(outputPath), { recursive: true });
  await page.screenshot({
    path: outputPath,
    fullPage: options?.fullPage ?? false,
  });
}

async function captureFailure(page: Page, step: StepKey): Promise<void> {
  await capture(page, `zz-failure-${toSlug(step)}.png`, { fullPage: true }).catch(() => undefined);
}

async function runStep(
  page: Page,
  result: StepResult,
  failures: StepFailure,
  key: StepKey,
  action: () => Promise<void>
): Promise<void> {
  try {
    await action();
    result[key] = "PASS";
  } catch (error) {
    result[key] = "FAIL";
    failures[key] = toErrorMessage(error);
    await captureFailure(page, key);
  }
}

async function ensureMiNegocioExpanded(page: Page): Promise<void> {
  const addBusinessSubmenu = page.getByText(accentInsensitiveMatcher("Agregar Negocio")).first();
  const manageBusinessSubmenu = page.getByText(accentInsensitiveMatcher("Administrar Negocios")).first();

  if ((await addBusinessSubmenu.isVisible().catch(() => false)) &&
      (await manageBusinessSubmenu.isVisible().catch(() => false))) {
    return;
  }

  const menu = await firstVisibleOrThrow(
    page,
    [
      page.locator("nav,aside,[role='navigation']").getByRole("button", {
        name: accentInsensitiveMatcher("Mi Negocio"),
      }),
      page.locator("nav,aside,[role='navigation']").getByRole("link", {
        name: accentInsensitiveMatcher("Mi Negocio"),
      }),
      page.getByRole("button", { name: accentInsensitiveMatcher("Mi Negocio") }),
      page.getByRole("link", { name: accentInsensitiveMatcher("Mi Negocio") }),
      page.getByText(accentInsensitiveMatcher("Mi Negocio")),
    ],
    "Could not find 'Mi Negocio' option in sidebar"
  );

  await clickAndWait(menu, page);
  await expect(addBusinessSubmenu).toBeVisible();
  await expect(manageBusinessSubmenu).toBeVisible();
}

async function resolveBusinessNameInput(page: Page): Promise<Locator> {
  return firstVisibleOrThrow(
    page,
    [
      page.getByLabel(accentInsensitiveMatcher("Nombre del Negocio")),
      page.getByRole("textbox", { name: accentInsensitiveMatcher("Nombre del Negocio") }),
      page.getByPlaceholder(accentInsensitiveMatcher("Nombre del Negocio")),
      page.locator("input[aria-label*='Nombre'], input[placeholder*='Nombre']").first(),
    ],
    "Could not locate the input field 'Nombre del Negocio'"
  );
}

async function clickLegalLinkAndValidate(
  page: Page,
  linkLabel: string,
  headingPattern: RegExp,
  screenshotName: string
): Promise<{ finalUrl: string }> {
  const context = page.context();
  const link = await firstVisibleOrThrow(
    page,
    [
      page.getByRole("link", { name: accentInsensitiveMatcher(linkLabel) }),
      page.getByRole("button", { name: accentInsensitiveMatcher(linkLabel) }),
      page.getByText(accentInsensitiveMatcher(linkLabel)),
    ],
    `Could not find legal link: ${linkLabel}`
  );

  const currentUrl = page.url();
  const possiblePopup = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  await link.click();
  await waitForUi(page);

  const popup = await possiblePopup;
  const legalPage = popup ?? page;
  await legalPage.waitForLoadState("domcontentloaded");

  const heading = await firstVisibleOrThrow(
    legalPage,
    [
      legalPage.getByRole("heading", { name: headingPattern }),
      legalPage.getByText(headingPattern),
    ],
    `Could not find legal heading for '${linkLabel}'`
  );
  await expect(heading).toBeVisible();

  const legalContent = legalPage.locator("main,article,section,p,li").filter({ hasText: /.{40,}/ }).first();
  await expect(legalContent).toBeVisible();

  await capture(legalPage, screenshotName, { fullPage: true });
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
    await waitForUi(page);
    return { finalUrl };
  }

  if (page.url() !== currentUrl) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => undefined);
    await waitForUi(page);
  }

  return { finalUrl };
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const result: StepResult = {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Informacion General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Terminos y Condiciones": "FAIL",
    "Politica de Privacidad": "FAIL",
  };
  const failures: StepFailure = {};
  let termsUrl = "N/A";
  let privacyUrl = "N/A";

  await runStep(page, result, failures, "Login", async () => {
    const saleAdsBaseUrl = process.env.SALEADS_BASE_URL;
    if (saleAdsBaseUrl) {
      await page.goto(saleAdsBaseUrl, { waitUntil: "domcontentloaded" });
    } else if (page.url() === "about:blank") {
      throw new Error(
        "SALEADS_BASE_URL is not set and current page is about:blank. " +
          "Set SALEADS_BASE_URL or begin with browser already opened on SaleADS login page."
      );
    }

    await waitForUi(page);

    const loginButton = await firstVisibleOrThrow(
      page,
      [
        page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google/i }),
        page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google/i }),
        page.getByRole("button", { name: /google/i }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google/i),
      ],
      "Could not find Google sign in control"
    );

    const popupPromise = page.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickAndWait(loginButton, page);

    const popup = await popupPromise;
    const authPage = popup ?? page;
    await authPage.waitForLoadState("domcontentloaded");

    const accountOption = authPage.getByText(textMatcher(GOOGLE_ACCOUNT_EMAIL)).first();
    if (await accountOption.isVisible().catch(() => false)) {
      await accountOption.click();
      await authPage.waitForLoadState("domcontentloaded");
      await authPage.waitForTimeout(1000);
    }

    if (popup) {
      await waitForPageClose(popup, 25_000);
      await page.bringToFront();
    }

    await waitForUi(page);
    const sidebar = await firstVisibleOrThrow(
      page,
      [page.locator("nav"), page.locator("aside"), page.getByRole("navigation"), page.getByText(/negocio/i)],
      "Main application interface/sidebar was not visible after login"
    );
    await expect(sidebar).toBeVisible();
    await capture(page, "01-dashboard-loaded.png", { fullPage: true });
  });

  await runStep(page, result, failures, "Mi Negocio menu", async () => {
    const negocioSection = await firstVisibleOrThrow(
      page,
      [
        page.locator("nav,aside,[role='navigation']").getByRole("button", {
          name: accentInsensitiveMatcher("Negocio"),
        }),
        page.locator("nav,aside,[role='navigation']").getByRole("link", {
          name: accentInsensitiveMatcher("Negocio"),
        }),
        page.getByRole("button", { name: accentInsensitiveMatcher("Negocio") }),
        page.getByRole("link", { name: accentInsensitiveMatcher("Negocio") }),
        page.getByText(accentInsensitiveMatcher("Negocio")),
      ],
      "Could not find 'Negocio' section in left sidebar"
    );
    await clickAndWait(negocioSection, page);
    await ensureMiNegocioExpanded(page);
    await capture(page, "02-mi-negocio-menu-expanded.png");
  });

  await runStep(page, result, failures, "Agregar Negocio modal", async () => {
    const addBusiness = await firstVisibleOrThrow(
      page,
      [
        page.getByRole("button", { name: accentInsensitiveMatcher("Agregar Negocio") }),
        page.getByRole("link", { name: accentInsensitiveMatcher("Agregar Negocio") }),
        page.getByText(accentInsensitiveMatcher("Agregar Negocio")),
      ],
      "Could not find 'Agregar Negocio' action"
    );
    await clickAndWait(addBusiness, page);

    await expect(page.getByText(accentInsensitiveMatcher("Crear Nuevo Negocio")).first()).toBeVisible();
    const businessNameInput = await resolveBusinessNameInput(page);
    await expect(businessNameInput).toBeVisible();
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: accentInsensitiveMatcher("Cancelar") })).toBeVisible();
    await expect(
      page.getByRole("button", { name: accentInsensitiveMatcher("Crear Negocio") })
    ).toBeVisible();
    await capture(page, "03-agregar-negocio-modal.png");

    await businessNameInput.click();
    await waitForUi(page);
    await businessNameInput.fill("Negocio Prueba Automatizacion");
    await waitForUi(page);
    await clickAndWait(page.getByRole("button", { name: accentInsensitiveMatcher("Cancelar") }), page);
  });

  await runStep(page, result, failures, "Administrar Negocios view", async () => {
    await ensureMiNegocioExpanded(page);

    const manageBusiness = await firstVisibleOrThrow(
      page,
      [
        page.getByRole("link", { name: accentInsensitiveMatcher("Administrar Negocios") }),
        page.getByRole("button", { name: accentInsensitiveMatcher("Administrar Negocios") }),
        page.getByText(accentInsensitiveMatcher("Administrar Negocios")),
      ],
      "Could not find 'Administrar Negocios'"
    );
    await clickAndWait(manageBusiness, page);

    await expect(page.getByText(accentInsensitiveMatcher("Informacion General")).first()).toBeVisible();
    await expect(page.getByText(accentInsensitiveMatcher("Detalles de la Cuenta")).first()).toBeVisible();
    await expect(page.getByText(accentInsensitiveMatcher("Tus Negocios")).first()).toBeVisible();
    await expect(page.getByText(accentInsensitiveMatcher("Seccion Legal")).first()).toBeVisible();
    await capture(page, "04-administrar-negocios-page.png", { fullPage: true });
  });

  await runStep(page, result, failures, "Informacion General", async () => {
    const infoSection = page
      .locator("section,div,main")
      .filter({ hasText: accentInsensitiveMatcher("Informacion General") })
      .first();

    await expect(infoSection).toBeVisible();
    await expect(infoSection).toContainText(/@/);
    await expect(page.getByText(/business plan/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: accentInsensitiveMatcher("Cambiar Plan") }).first()).toBeVisible();
  });

  await runStep(page, result, failures, "Detalles de la Cuenta", async () => {
    await expect(page.getByText(accentInsensitiveMatcher("Cuenta creada")).first()).toBeVisible();
    await expect(page.getByText(accentInsensitiveMatcher("Estado activo")).first()).toBeVisible();
    await expect(page.getByText(accentInsensitiveMatcher("Idioma seleccionado")).first()).toBeVisible();
  });

  await runStep(page, result, failures, "Tus Negocios", async () => {
    const businessesSection = page
      .locator("section,div,main")
      .filter({ hasText: accentInsensitiveMatcher("Tus Negocios") })
      .first();

    await expect(businessesSection).toBeVisible();
    await expect(businessesSection.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible();
    await expect(
      businessesSection.getByRole("button", { name: accentInsensitiveMatcher("Agregar Negocio") }).first()
    ).toBeVisible();
  });

  await runStep(page, result, failures, "Terminos y Condiciones", async () => {
    const legalResult = await clickLegalLinkAndValidate(
      page,
      "Terminos y Condiciones",
      accentInsensitiveMatcher("Terminos y Condiciones"),
      "05-terminos-y-condiciones.png"
    );
    termsUrl = legalResult.finalUrl;
  });

  await runStep(page, result, failures, "Politica de Privacidad", async () => {
    const legalResult = await clickLegalLinkAndValidate(
      page,
      "Politica de Privacidad",
      accentInsensitiveMatcher("Politica de Privacidad"),
      "06-politica-de-privacidad.png"
    );
    privacyUrl = legalResult.finalUrl;
  });

  const reportLines = STEP_ORDER.map((step) => `${STEP_DISPLAY[step]}: ${result[step]}`);
  reportLines.push(`Terminos y Condiciones URL: ${termsUrl}`);
  reportLines.push(`Politica de Privacidad URL: ${privacyUrl}`);

  if (Object.keys(failures).length > 0) {
    reportLines.push("", "Failure details:");
    for (const step of STEP_ORDER) {
      if (failures[step]) {
        reportLines.push(`- ${STEP_DISPLAY[step]}: ${failures[step]}`);
      }
    }
  }

  const report = reportLines.join("\n");
  console.log("\nSaleADS Mi Negocio Full Test Report");
  console.log(report);

  await testInfo.attach("saleads-mi-negocio-report.txt", {
    body: report,
    contentType: "text/plain",
  });

  const failedSteps = STEP_ORDER.filter((step) => result[step] === "FAIL");
  expect(failedSteps, `Some workflow validations failed.\n${report}`).toEqual([]);
});
