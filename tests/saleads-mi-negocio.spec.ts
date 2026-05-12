import { expect, Page, test } from "@playwright/test";

type StepResult = "PASS" | "FAIL";
type ReportField =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Informacion General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Terminos y Condiciones"
  | "Politica de Privacidad";

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const CAPTURE_DIR = "test-results/screenshots";

async function waitForUiLoad(page: Page): Promise<void> {
  await Promise.race([
    page.waitForLoadState("networkidle", { timeout: 5_000 }),
    page.waitForTimeout(1_200),
  ]).catch(() => {});
}

async function clickVisibleMenuItem(page: Page, label: RegExp): Promise<void> {
  const menuItem = page.getByRole("button", { name: label }).first();
  if (await menuItem.isVisible().catch(() => false)) {
    await menuItem.click();
    await waitForUiLoad(page);
    return;
  }

  const linkItem = page.getByRole("link", { name: label }).first();
  if (await linkItem.isVisible().catch(() => false)) {
    await linkItem.click();
    await waitForUiLoad(page);
    return;
  }

  const genericText = page.getByText(label).first();
  await expect(genericText).toBeVisible();
  await genericText.click();
  await waitForUiLoad(page);
}

async function selectGoogleAccountIfPrompted(page: Page): Promise<void> {
  const accountOption = page.getByText(GOOGLE_ACCOUNT_EMAIL).first();
  if (await accountOption.isVisible().catch(() => false)) {
    await accountOption.click();
    await waitForUiLoad(page);
  }
}

async function validateLegalPage(
  page: Page,
  label: RegExp,
  heading: RegExp,
  screenshotName: string,
): Promise<string> {
  const context = page.context();
  const popupPromise = context.waitForEvent("page").catch(() => null);

  await clickVisibleMenuItem(page, label);

  const popup = await Promise.race([
    popupPromise,
    page.waitForTimeout(4_000).then(() => null),
  ]);
  const targetPage = popup ?? page;

  await targetPage.waitForLoadState("domcontentloaded");
  await expect(targetPage.getByRole("heading", { name: heading }).first()).toBeVisible();
  await expect(targetPage.locator("body")).toContainText(
    /(terminos|condiciones|privacidad|datos|legal)/i,
  );
  await targetPage.screenshot({ path: `${CAPTURE_DIR}/${screenshotName}` });

  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUiLoad(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report: Record<ReportField, StepResult> = {
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

  const failures: string[] = [];
  const legalUrls: Record<"terminos" | "privacidad", string | null> = {
    terminos: null,
    privacidad: null,
  };

  const saleadsUrl = process.env.SALEADS_URL ?? process.env.BASE_URL;
  if (saleadsUrl) {
    await page.goto(saleadsUrl, { waitUntil: "domcontentloaded" });
  }

  const runStep = async (name: ReportField, action: () => Promise<void>) => {
    try {
      await action();
      report[name] = "PASS";
    } catch (error) {
      report[name] = "FAIL";
      failures.push(`${name}: ${String(error)}`);
    }
  };

  await runStep("Login", async () => {
    if (!saleadsUrl && page.url() === "about:blank") {
      throw new Error("Set SALEADS_URL or BASE_URL to open the SaleADS login page.");
    }

    const googleButton = page
      .getByRole("button", { name: /google|sign in|iniciar sesion|login/i })
      .first();
    await expect(googleButton).toBeVisible();

    const popupPromise = page.context().waitForEvent("page").catch(() => null);
    await googleButton.click();
    await waitForUiLoad(page);

    const popup = await Promise.race([
      popupPromise,
      page.waitForTimeout(4_000).then(() => null),
    ]);

    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await selectGoogleAccountIfPrompted(popup);
      await popup.close().catch(() => {});
      await page.bringToFront();
    } else {
      await selectGoogleAccountIfPrompted(page);
    }

    await expect(page.locator("aside, nav").first()).toBeVisible();
    await page.screenshot({ path: `${CAPTURE_DIR}/step1-dashboard-loaded.png` });
  });

  await runStep("Mi Negocio menu", async () => {
    await clickVisibleMenuItem(page, /negocio/i);
    await clickVisibleMenuItem(page, /mi negocio/i);

    await expect(page.getByText(/agregar negocio/i)).toBeVisible();
    await expect(page.getByText(/administrar negocios/i)).toBeVisible();
    await page.screenshot({ path: `${CAPTURE_DIR}/step2-mi-negocio-expanded.png` });
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickVisibleMenuItem(page, /agregar negocio/i);
    await expect(page.getByText(/crear nuevo negocio/i)).toBeVisible();
    await expect(page.getByLabel(/nombre del negocio/i)).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();
    await page.screenshot({ path: `${CAPTURE_DIR}/step3-agregar-negocio-modal.png` });

    const businessNameInput = page.getByLabel(/nombre del negocio/i);
    if (await businessNameInput.isVisible().catch(() => false)) {
      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatizacion");
    }
    await page.getByRole("button", { name: /cancelar/i }).click();
    await waitForUiLoad(page);
  });

  await runStep("Administrar Negocios view", async () => {
    await clickVisibleMenuItem(page, /mi negocio/i);
    await clickVisibleMenuItem(page, /administrar negocios/i);

    await expect(page.getByText(/informacion general/i)).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByText(/seccion legal/i)).toBeVisible();
    await page.screenshot({
      path: `${CAPTURE_DIR}/step4-administrar-negocios.png`,
      fullPage: true,
    });
  });

  await runStep("Informacion General", async () => {
    await expect(page.getByText(/@/)).toBeVisible();
    await expect(page.getByText(/business plan/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/estado activo/i)).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i)).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /agregar negocio/i })).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
  });

  await runStep("Terminos y Condiciones", async () => {
    legalUrls.terminos = await validateLegalPage(
      page,
      /terminos y condiciones/i,
      /terminos y condiciones/i,
      "step8-terminos-y-condiciones.png",
    );
  });

  await runStep("Politica de Privacidad", async () => {
    legalUrls.privacidad = await validateLegalPage(
      page,
      /politica de privacidad/i,
      /politica de privacidad/i,
      "step9-politica-de-privacidad.png",
    );
  });

  const finalReport = {
    reportName: "saleads_mi_negocio_full_test",
    executedAt: new Date().toISOString(),
    results: report,
    legalUrls,
    failures,
  };

  await testInfo.attach("final-report", {
    body: Buffer.from(JSON.stringify(finalReport, null, 2), "utf-8"),
    contentType: "application/json",
  });

  console.log("SaleADS Mi Negocio final report:");
  console.log(JSON.stringify(finalReport, null, 2));

  if (failures.length > 0) {
    throw new Error(`Validation failures:\n${failures.join("\n")}`);
  }
});
