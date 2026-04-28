import { expect, test, type Locator, type Page } from "@playwright/test";

type StepName =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad";

type StepResult = {
  status: "PASS" | "FAIL";
  details: string;
};

const stepResults: Record<StepName, StepResult> = {
  Login: { status: "FAIL", details: "Not executed." },
  "Mi Negocio menu": { status: "FAIL", details: "Not executed." },
  "Agregar Negocio modal": { status: "FAIL", details: "Not executed." },
  "Administrar Negocios view": { status: "FAIL", details: "Not executed." },
  "Información General": { status: "FAIL", details: "Not executed." },
  "Detalles de la Cuenta": { status: "FAIL", details: "Not executed." },
  "Tus Negocios": { status: "FAIL", details: "Not executed." },
  "Términos y Condiciones": { status: "FAIL", details: "Not executed." },
  "Política de Privacidad": { status: "FAIL", details: "Not executed." },
};

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(600);
}

function firstVisible(...locators: Locator[]): Locator {
  return {
    async click(options?: Parameters<Locator["click"]>[0]) {
      for (const locator of locators) {
        if (await locator.count()) {
          const first = locator.first();
          if (await first.isVisible()) {
            await first.click(options);
            return;
          }
        }
      }
      throw new Error("No visible locator found for click.");
    },
    async fill(value: string) {
      for (const locator of locators) {
        if (await locator.count()) {
          const first = locator.first();
          if (await first.isVisible()) {
            await first.fill(value);
            return;
          }
        }
      }
      throw new Error("No visible locator found for fill.");
    },
    async expectVisible() {
      for (const locator of locators) {
        if (await locator.count()) {
          const first = locator.first();
          if (await first.isVisible()) {
            await expect(first).toBeVisible();
            return;
          }
        }
      }
      throw new Error("No visible locator found for expectVisible.");
    },
  } as unknown as Locator;
}

async function withStep(
  step: StepName,
  action: () => Promise<void>,
): Promise<void> {
  try {
    await action();
    stepResults[step] = { status: "PASS", details: "Validation completed." };
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    stepResults[step] = { status: "FAIL", details: message };
    throw error;
  }
}

async function openLegalLinkAndValidate(
  page: Page,
  linkText: RegExp,
  expectedHeading: RegExp,
  screenshotName: string,
): Promise<{ finalUrl: string; sameTabNavigation: boolean }> {
  const originalUrl = page.url();
  const [popup] = await Promise.all([
    page.waitForEvent("popup").catch(() => null),
    page.getByRole("link", { name: linkText }).first().click(),
  ]);

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await expect(popup.getByRole("heading", { name: expectedHeading })).toBeVisible({
      timeout: 15_000,
    });
    await expect(popup.locator("body")).toContainText(/[A-Za-zÁÉÍÓÚáéíóú]{20,}/, {
      timeout: 15_000,
    });
    await popup.screenshot({ path: `screenshots/${screenshotName}.png`, fullPage: true });
    const finalUrl = popup.url();
    await popup.close();
    return { finalUrl, sameTabNavigation: false };
  }

  await waitForUi(page);
  await expect(page.getByRole("heading", { name: expectedHeading })).toBeVisible({
    timeout: 15_000,
  });
  await expect(page.locator("body")).toContainText(/[A-Za-zÁÉÍÓÚáéíóú]{20,}/, {
    timeout: 15_000,
  });
  await page.screenshot({ path: `screenshots/${screenshotName}.png`, fullPage: true });
  return { finalUrl: page.url(), sameTabNavigation: page.url() !== originalUrl };
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  const legalUrls: Record<string, string> = {};
  let appUrlForReturn = page.url();

  test.info().annotations.push({
    type: "note",
    description:
      "Assumes browser starts on SaleADS login page for current environment.",
  });

  const baseUrl = process.env.SALEADS_BASE_URL;
  if (!page.url() || page.url() === "about:blank") {
    if (!baseUrl) {
      throw new Error(
        "Browser started on about:blank and SALEADS_BASE_URL is not set. Provide SALEADS_BASE_URL or pre-open the login page.",
      );
    }
    await page.goto(baseUrl, { waitUntil: "domcontentloaded" });
  }

  await waitForUi(page);

  await withStep("Login", async () => {
    const signInButton = firstVisible(
      page.getByRole("button", { name: /sign in with google/i }),
      page.getByRole("button", { name: /iniciar sesi[oó]n con google/i }),
      page.getByText(/sign in with google/i),
      page.getByText(/iniciar sesi[oó]n con google/i),
    );

    const googlePopupPromise = page.waitForEvent("popup").catch(() => null);
    await (signInButton as unknown as { click: () => Promise<void> }).click();

    const googlePopup = await googlePopupPromise;
    if (googlePopup) {
      await googlePopup.waitForLoadState("domcontentloaded");

      const accountOption = googlePopup
        .getByText("juanlucasbarbiergarzon@gmail.com", { exact: true })
        .first();
      if (await accountOption.count()) {
        await accountOption.click();
      }
    } else {
      const inlineAccountOption = page
        .getByText("juanlucasbarbiergarzon@gmail.com", { exact: true })
        .first();
      if (await inlineAccountOption.count()) {
        await inlineAccountOption.click();
      }
    }

    await waitForUi(page);

    const sidebar = firstVisible(
      page.getByRole("navigation").first(),
      page.locator("aside").first(),
      page.getByText(/negocio/i).first(),
    );
    await (sidebar as unknown as { expectVisible: () => Promise<void> }).expectVisible();
    appUrlForReturn = page.url();
    await page.screenshot({ path: "screenshots/01-dashboard-loaded.png", fullPage: true });
  });

  await withStep("Mi Negocio menu", async () => {
    const negocioSection = firstVisible(
      page.getByRole("button", { name: /^negocio$/i }),
      page.getByRole("link", { name: /^negocio$/i }),
      page.getByText(/^negocio$/i),
    );
    await (negocioSection as unknown as { click: () => Promise<void> }).click();
    await waitForUi(page);

    const miNegocio = firstVisible(
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i),
    );
    await (miNegocio as unknown as { click: () => Promise<void> }).click();
    await waitForUi(page);

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible({
      timeout: 10_000,
    });
    await page.screenshot({ path: "screenshots/02-mi-negocio-expanded.png", fullPage: true });
  });

  await withStep("Agregar Negocio modal", async () => {
    await page.getByText(/agregar negocio/i).first().click();
    await waitForUi(page);

    await expect(page.getByText(/crear nuevo negocio/i).first()).toBeVisible({
      timeout: 10_000,
    });
    const nombreInput = firstVisible(
      page.getByLabel(/nombre del negocio/i),
      page.getByPlaceholder(/nombre del negocio/i),
      page.locator("input").filter({ hasText: "" }).first(),
    );
    await (nombreInput as unknown as { expectVisible: () => Promise<void> }).expectVisible();
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.getByRole("button", { name: /cancelar/i }).first()).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.getByRole("button", { name: /crear negocio/i }).first()).toBeVisible({
      timeout: 10_000,
    });
    await page.screenshot({ path: "screenshots/03-agregar-negocio-modal.png", fullPage: true });

    await (nombreInput as unknown as { fill: (value: string) => Promise<void> }).fill(
      "Negocio Prueba Automatización",
    );
    await waitForUi(page);
    await page.getByRole("button", { name: /cancelar/i }).first().click();
    await waitForUi(page);
  });

  await withStep("Administrar Negocios view", async () => {
    const miNegocio = firstVisible(
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i),
    );
    if (!(await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false))) {
      await (miNegocio as unknown as { click: () => Promise<void> }).click();
      await waitForUi(page);
    }

    await page.getByText(/administrar negocios/i).first().click();
    await waitForUi(page);

    await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.getByText(/secci[oó]n legal/i).first()).toBeVisible({
      timeout: 15_000,
    });
    await page.screenshot({ path: "screenshots/04-administrar-negocios.png", fullPage: true });
  });

  await withStep("Información General", async () => {
    await expect(page.getByText(/business plan/i).first()).toBeVisible({ timeout: 10_000 });
    await expect(page.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible({
      timeout: 10_000,
    });

    const emailText = page.getByText(/@/).first();
    await expect(emailText).toBeVisible({ timeout: 10_000 });

    const infoGeneralSection = page.locator("section,div").filter({
      has: page.getByText(/informaci[oó]n general/i).first(),
    }).first();
    await expect(infoGeneralSection).toContainText(/[A-Za-zÁÉÍÓÚáéíóú]{3,}/, {
      timeout: 10_000,
    });
  });

  await withStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText(/estado activo/i).first()).toBeVisible({ timeout: 10_000 });
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible({
      timeout: 10_000,
    });
  });

  await withStep("Tus Negocios", async () => {
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible({ timeout: 10_000 });
    await expect(page.getByRole("button", { name: /agregar negocio/i }).first()).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i).first()).toBeVisible({
      timeout: 10_000,
    });
  });

  await withStep("Términos y Condiciones", async () => {
    const result = await openLegalLinkAndValidate(
      page,
      /t[eé]rminos y condiciones/i,
      /t[eé]rminos y condiciones/i,
      "05-terminos-y-condiciones",
    );
    legalUrls["Términos y Condiciones"] = result.finalUrl;
    if (result.sameTabNavigation) {
      await page.goBack({ waitUntil: "domcontentloaded" });
    }
    await waitForUi(page);
  });

  await withStep("Política de Privacidad", async () => {
    const result = await openLegalLinkAndValidate(
      page,
      /pol[ií]tica de privacidad/i,
      /pol[ií]tica de privacidad/i,
      "06-politica-de-privacidad",
    );
    legalUrls["Política de Privacidad"] = result.finalUrl;
    if (result.sameTabNavigation) {
      await page.goBack({ waitUntil: "domcontentloaded" });
    }
    await waitForUi(page);
  });

  if (page.url() !== appUrlForReturn) {
    await page.goto(appUrlForReturn, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  const summaryLines = (Object.keys(stepResults) as StepName[]).map(
    (step) => `${step}: ${stepResults[step].status} - ${stepResults[step].details}`,
  );

  if (Object.keys(legalUrls).length) {
    summaryLines.push(`Términos URL: ${legalUrls["Términos y Condiciones"] ?? "N/A"}`);
    summaryLines.push(`Privacidad URL: ${legalUrls["Política de Privacidad"] ?? "N/A"}`);
  }

  test.info().annotations.push({
    type: "final-report",
    description: summaryLines.join("\n"),
  });

  // Final assertion enforces a clear PASS/FAIL outcome for CI.
  expect(summaryLines.some((line) => line.includes(": FAIL"))).toBeFalsy();
});
