import { expect, Locator, Page, test } from "@playwright/test";
import { writeFile } from "node:fs/promises";

type StepKey =
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
  details: string[];
  screenshot?: string;
  finalUrl?: string;
};

async function waitForUi(page: Page): Promise<void> {
  await page.waitForTimeout(400);
  try {
    await page.waitForLoadState("domcontentloaded", { timeout: 10_000 });
  } catch {
    // Some interactions do not trigger document state changes.
  }
  try {
    await page.waitForLoadState("networkidle", { timeout: 8_000 });
  } catch {
    // Network can stay active on SPAs; continue after timeout.
  }
  await page.waitForTimeout(400);
}

async function waitForAnyVisible(
  candidates: Array<() => Locator>,
  description: string,
): Promise<Locator> {
  for (const makeLocator of candidates) {
    const locator = makeLocator().first();
    try {
      await locator.waitFor({ state: "visible", timeout: 8_000 });
      return locator;
    } catch {
      // Try next candidate.
    }
  }

  throw new Error(`No visible element found for: ${description}`);
}

async function capture(
  page: Page,
  fileName: string,
  fullPage = false,
): Promise<string> {
  const path = test.info().outputPath(fileName);
  await page.screenshot({ path, fullPage });
  return path;
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  const report: Record<StepKey, StepResult> = {
    Login: { status: "FAIL", details: ["Step not executed."] },
    "Mi Negocio menu": { status: "FAIL", details: ["Step not executed."] },
    "Agregar Negocio modal": {
      status: "FAIL",
      details: ["Step not executed."],
    },
    "Administrar Negocios view": {
      status: "FAIL",
      details: ["Step not executed."],
    },
    "Información General": { status: "FAIL", details: ["Step not executed."] },
    "Detalles de la Cuenta": {
      status: "FAIL",
      details: ["Step not executed."],
    },
    "Tus Negocios": { status: "FAIL", details: ["Step not executed."] },
    "Términos y Condiciones": {
      status: "FAIL",
      details: ["Step not executed."],
    },
    "Política de Privacidad": {
      status: "FAIL",
      details: ["Step not executed."],
    },
  };

  const setPass = (key: StepKey, details: string[], extra?: Partial<StepResult>) => {
    report[key] = { status: "PASS", details, ...extra };
  };

  const setFail = (key: StepKey, details: string[]) => {
    report[key] = { status: "FAIL", details };
  };

  let appUrlAfterLogin = "";

  // Step 1 - Login with Google
  try {
    const details: string[] = [];
    const loginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_URL;
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
      details.push(`Opened login URL from environment variable: ${loginUrl}`);
    } else {
      details.push(`Starting from current page URL: ${page.url()}`);
    }

    if (page.url() === "about:blank") {
      throw new Error(
        "Browser is on about:blank. Provide SALEADS_LOGIN_URL/SALEADS_URL or pre-open the SaleADS login page.",
      );
    }

    const loginButton = await waitForAnyVisible(
      [
        () => page.getByRole("button", { name: /sign in with google/i }),
        () => page.getByRole("button", { name: /iniciar sesi[oó]n con google/i }),
        () => page.getByRole("button", { name: /continuar con google/i }),
        () => page.getByText(/google/i),
      ],
      "Google login button",
    );

    const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);
    await loginButton.click();
    await waitForUi(page);
    details.push("Clicked Google login button.");

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await waitForUi(popup);
      const accountOption = popup.getByText("juanlucasbarbiergarzon@gmail.com", {
        exact: true,
      });
      if (await accountOption.isVisible().catch(() => false)) {
        await accountOption.click();
        await waitForUi(popup);
        details.push("Selected Google account juanlucasbarbiergarzon@gmail.com in popup.");
      } else {
        details.push("Google account selector was not shown in popup.");
      }
    } else {
      const accountOption = page.getByText("juanlucasbarbiergarzon@gmail.com", {
        exact: true,
      });
      if (await accountOption.isVisible().catch(() => false)) {
        await accountOption.click();
        await waitForUi(page);
        details.push("Selected Google account juanlucasbarbiergarzon@gmail.com in current tab.");
      } else {
        details.push("Google account selector was not shown in current tab.");
      }
    }

    const sidebar = await waitForAnyVisible(
      [
        () => page.getByRole("navigation"),
        () => page.locator("aside"),
        () => page.getByText(/mi negocio|negocio/i),
      ],
      "main app interface with left sidebar",
    );
    await expect(sidebar).toBeVisible();
    appUrlAfterLogin = page.url();

    const screenshot = await capture(page, "01-dashboard-loaded.png");
    details.push("Main application interface and left sidebar are visible.");
    setPass("Login", details, { screenshot, finalUrl: appUrlAfterLogin });
  } catch (error) {
    setFail("Login", [`${error}`]);
  }

  // Step 2 - Open Mi Negocio menu
  try {
    const details: string[] = [];
    const miNegocioToggle = await waitForAnyVisible(
      [
        () => page.getByRole("button", { name: /mi negocio/i }),
        () => page.getByRole("link", { name: /mi negocio/i }),
        () => page.getByText(/^mi negocio$/i),
      ],
      "Mi Negocio menu option",
    );
    await miNegocioToggle.click();
    await waitForUi(page);

    const agregarNegocio = await waitForAnyVisible(
      [
        () => page.getByRole("button", { name: /agregar negocio/i }),
        () => page.getByRole("link", { name: /agregar negocio/i }),
        () => page.getByText(/^agregar negocio$/i),
      ],
      "Agregar Negocio submenu option",
    );
    const administrarNegocios = await waitForAnyVisible(
      [
        () => page.getByRole("button", { name: /administrar negocios/i }),
        () => page.getByRole("link", { name: /administrar negocios/i }),
        () => page.getByText(/^administrar negocios$/i),
      ],
      "Administrar Negocios submenu option",
    );
    await expect(agregarNegocio).toBeVisible();
    await expect(administrarNegocios).toBeVisible();

    const screenshot = await capture(page, "02-mi-negocio-expanded.png");
    details.push("Mi Negocio submenu expanded with Agregar and Administrar options visible.");
    setPass("Mi Negocio menu", details, { screenshot });
  } catch (error) {
    setFail("Mi Negocio menu", [`${error}`]);
  }

  // Step 3 - Validate Agregar Negocio modal
  try {
    const details: string[] = [];
    const agregarNegocio = await waitForAnyVisible(
      [
        () => page.getByRole("button", { name: /agregar negocio/i }),
        () => page.getByRole("link", { name: /agregar negocio/i }),
        () => page.getByText(/^agregar negocio$/i),
      ],
      "Agregar Negocio action",
    );

    await agregarNegocio.click();
    await waitForUi(page);

    await expect(page.getByRole("heading", { name: /crear nuevo negocio/i })).toBeVisible();
    await expect(page.getByLabel(/nombre del negocio/i)).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /^cancelar$/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    const inputNombreNegocio = page.getByLabel(/nombre del negocio/i);
    await inputNombreNegocio.click();
    await inputNombreNegocio.fill("Negocio Prueba Automatización");
    await waitForUi(page);
    details.push("Optional input interaction completed with sample business name.");

    const screenshot = await capture(page, "03-crear-nuevo-negocio-modal.png");
    const cancelar = page.getByRole("button", { name: /^cancelar$/i });
    await cancelar.click();
    await waitForUi(page);
    details.push("Modal validated and closed with Cancelar.");
    setPass("Agregar Negocio modal", details, { screenshot });
  } catch (error) {
    setFail("Agregar Negocio modal", [`${error}`]);
  }

  // Step 4 - Open Administrar Negocios
  try {
    const details: string[] = [];
    const administrarNegocios = await waitForAnyVisible(
      [
        () => page.getByRole("button", { name: /administrar negocios/i }),
        () => page.getByRole("link", { name: /administrar negocios/i }),
        () => page.getByText(/^administrar negocios$/i),
      ],
      "Administrar Negocios action",
    );

    await administrarNegocios.click();
    await waitForUi(page);

    await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible();
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible();

    appUrlAfterLogin = page.url();
    const screenshot = await capture(page, "04-administrar-negocios-page.png", true);
    details.push("Administrar Negocios view loaded with all expected sections.");
    setPass("Administrar Negocios view", details, {
      screenshot,
      finalUrl: appUrlAfterLogin,
    });
  } catch (error) {
    setFail("Administrar Negocios view", [`${error}`]);
  }

  // Step 5 - Validate Información General
  try {
    const details: string[] = [];
    await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible();
    await expect(page.getByText(/@/)).toBeVisible();
    await expect(page.getByText(/business plan/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible();
    details.push("User identity, email, BUSINESS PLAN, and Cambiar Plan are visible.");
    setPass("Información General", details);
  } catch (error) {
    setFail("Información General", [`${error}`]);
  }

  // Step 6 - Validate Detalles de la Cuenta
  try {
    const details: string[] = [];
    await expect(page.getByText(/cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/estado activo|activo/i)).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i)).toBeVisible();
    details.push("Cuenta creada, Estado activo, and Idioma seleccionado are visible.");
    setPass("Detalles de la Cuenta", details);
  } catch (error) {
    setFail("Detalles de la Cuenta", [`${error}`]);
  }

  // Step 7 - Validate Tus Negocios
  try {
    const details: string[] = [];
    await expect(page.getByText(/tus negocios/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /agregar negocio/i })).toBeVisible();
    await expect(page.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
    details.push("Business list section, Agregar Negocio button, and business limit text are visible.");
    setPass("Tus Negocios", details);
  } catch (error) {
    setFail("Tus Negocios", [`${error}`]);
  }

  // Step 8 - Validate Términos y Condiciones
  try {
    const details: string[] = [];
    const termsLink = await waitForAnyVisible(
      [
        () => page.getByRole("link", { name: /t[eé]rminos y condiciones/i }),
        () => page.getByText(/t[eé]rminos y condiciones/i),
      ],
      "Términos y Condiciones link",
    );

    const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);
    await termsLink.click();
    await waitForUi(page);

    const popup = await popupPromise;
    const legalPage = popup ?? page;
    await legalPage.waitForLoadState("domcontentloaded");
    await waitForUi(legalPage);

    const heading = await waitForAnyVisible(
      [
        () => legalPage.getByRole("heading", { name: /t[eé]rminos y condiciones/i }),
        () => legalPage.getByText(/t[eé]rminos y condiciones/i),
      ],
      "Términos y Condiciones heading",
    );
    await expect(heading).toBeVisible();

    const legalText = (await legalPage.locator("body").innerText()).trim();
    expect(legalText.length).toBeGreaterThan(120);

    const screenshot = await capture(legalPage, "08-terminos-y-condiciones.png", true);
    const finalUrl = legalPage.url();
    details.push(`Validated legal page content. Final URL: ${finalUrl}`);

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
    } else if (appUrlAfterLogin && page.url() !== appUrlAfterLogin) {
      await page.goto(appUrlAfterLogin, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    setPass("Términos y Condiciones", details, { screenshot, finalUrl });
  } catch (error) {
    setFail("Términos y Condiciones", [`${error}`]);
  }

  // Step 9 - Validate Política de Privacidad
  try {
    const details: string[] = [];
    const privacyLink = await waitForAnyVisible(
      [
        () => page.getByRole("link", { name: /pol[ií]tica de privacidad/i }),
        () => page.getByText(/pol[ií]tica de privacidad/i),
      ],
      "Política de Privacidad link",
    );

    const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);
    await privacyLink.click();
    await waitForUi(page);

    const popup = await popupPromise;
    const legalPage = popup ?? page;
    await legalPage.waitForLoadState("domcontentloaded");
    await waitForUi(legalPage);

    const heading = await waitForAnyVisible(
      [
        () => legalPage.getByRole("heading", { name: /pol[ií]tica de privacidad/i }),
        () => legalPage.getByText(/pol[ií]tica de privacidad/i),
      ],
      "Política de Privacidad heading",
    );
    await expect(heading).toBeVisible();

    const legalText = (await legalPage.locator("body").innerText()).trim();
    expect(legalText.length).toBeGreaterThan(120);

    const screenshot = await capture(legalPage, "09-politica-de-privacidad.png", true);
    const finalUrl = legalPage.url();
    details.push(`Validated legal page content. Final URL: ${finalUrl}`);

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
    } else if (appUrlAfterLogin && page.url() !== appUrlAfterLogin) {
      await page.goto(appUrlAfterLogin, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }

    setPass("Política de Privacidad", details, { screenshot, finalUrl });
  } catch (error) {
    setFail("Política de Privacidad", [`${error}`]);
  }

  // Step 10 - Final Report
  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    results: report,
  };

  const reportPath = test.info().outputPath("final-report.json");
  await writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await test.info().attach("final-report.json", {
    path: reportPath,
    contentType: "application/json",
  });

  const failedSteps = Object.entries(report).filter(([, value]) => value.status === "FAIL");
  if (failedSteps.length > 0) {
    console.log("Final validation report:", JSON.stringify(finalReport, null, 2));
  }

  expect.soft(failedSteps, "One or more workflow validations failed").toHaveLength(0);
});
