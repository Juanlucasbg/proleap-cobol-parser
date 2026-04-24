import { expect, type Locator, type Page, test } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const BUSINESS_NAME = "Negocio Prueba Automatizacion";

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

type ReportStatus = {
  status: "PASS" | "FAIL";
  details?: string;
};

const reportKeyLabels: Record<ReportKey, string> = {
  Login: "Login",
  "Mi Negocio menu": "Mi Negocio menu",
  "Agregar Negocio modal": "Agregar Negocio modal",
  "Administrar Negocios view": "Administrar Negocios view",
  "Información General": "Información General",
  "Detalles de la Cuenta": "Detalles de la Cuenta",
  "Tus Negocios": "Tus Negocios",
  "Términos y Condiciones": "Términos y Condiciones",
  "Política de Privacidad": "Política de Privacidad",
};

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const finalReport: Record<ReportKey, ReportStatus> = {
    Login: { status: "FAIL", details: "Not executed" },
    "Mi Negocio menu": { status: "FAIL", details: "Not executed" },
    "Agregar Negocio modal": { status: "FAIL", details: "Not executed" },
    "Administrar Negocios view": { status: "FAIL", details: "Not executed" },
    "Información General": { status: "FAIL", details: "Not executed" },
    "Detalles de la Cuenta": { status: "FAIL", details: "Not executed" },
    "Tus Negocios": { status: "FAIL", details: "Not executed" },
    "Términos y Condiciones": { status: "FAIL", details: "Not executed" },
    "Política de Privacidad": { status: "FAIL", details: "Not executed" },
  };

  const legalUrls = {
    terminosYCondiciones: "",
    politicaDePrivacidad: "",
  };
  const failures: string[] = [];
  let loginCompleted = false;
  let miNegocioExpanded = false;
  let administrarNegociosOpened = false;

  const markPass = (key: ReportKey) => {
    finalReport[key] = { status: "PASS" };
  };

  const markFail = (key: ReportKey, error: unknown) => {
    const details = error instanceof Error ? error.message : String(error);
    finalReport[key] = { status: "FAIL", details };
    failures.push(`${reportKeyLabels[key]}: ${details}`);
  };

  const captureCheckpoint = async (
    targetPage: Page,
    checkpointName: string,
    fullPage = false,
  ) => {
    const fileName = checkpointName
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, "_")
      .replace(/^_+|_+$/g, "");
    const outputPath = testInfo.outputPath(`screenshots/${fileName}.png`);
    await fs.mkdir(path.dirname(outputPath), { recursive: true });
    await targetPage.screenshot({ path: outputPath, fullPage });
    await testInfo.attach(checkpointName, {
      path: outputPath,
      contentType: "image/png",
    });
  };

  const waitForUiToSettle = async (targetPage: Page) => {
    await targetPage
      .waitForLoadState("domcontentloaded", { timeout: 20_000 })
      .catch(() => null);
    await targetPage
      .waitForLoadState("networkidle", { timeout: 10_000 })
      .catch(() => null);
    await targetPage.waitForTimeout(400);
  };

  const clickAndWait = async (locator: Locator, clickPage: Page = page) => {
    await expect(locator).toBeVisible({ timeout: 20_000 });
    await locator.click();
    await waitForUiToSettle(clickPage);
  };

  const pickVisible = async (
    targetPage: Page,
    candidates: Locator[],
    description: string,
    timeoutMs = 20_000,
  ): Promise<Locator> => {
    const started = Date.now();
    while (Date.now() - started < timeoutMs) {
      for (const candidate of candidates) {
        const first = candidate.first();
        if (await first.isVisible().catch(() => false)) {
          return first;
        }
      }
      await targetPage.waitForTimeout(250);
    }
    throw new Error(`Could not find visible element: ${description}`);
  };

  const maybeSelectGoogleAccount = async (authPage: Page) => {
    const accountOption = authPage
      .getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true })
      .first();
    if (await accountOption.isVisible().catch(() => false)) {
      await clickAndWait(accountOption, authPage);
      return;
    }

    const chooseAccountText = authPage
      .getByText(/Elige una cuenta|Choose an account/i)
      .first();
    if (await chooseAccountText.isVisible().catch(() => false)) {
      const fallbackOption = await pickVisible(
        authPage,
        [
          authPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }),
          authPage.getByText(/@gmail\.com/i),
        ],
        "Google account option",
        20_000,
      );
      await clickAndWait(fallbackOption, authPage);
    }
  };

  const ensureSidebarVisible = async () => {
    await pickVisible(
      page,
      [
        page.locator("aside"),
        page.getByRole("navigation"),
        page.locator('[class*="sidebar"]'),
      ],
      "left sidebar navigation",
      60_000,
    );
  };

  const openLegalPageAndReturn = async (
    linkPattern: RegExp,
    headingPattern: RegExp,
    screenshotName: string,
  ) => {
    const legalLink = await pickVisible(
      page,
      [
        page.getByRole("link", { name: linkPattern }),
        page.getByRole("button", { name: linkPattern }),
        page.getByText(linkPattern),
      ],
      `Legal link: ${linkPattern.source}`,
    );

    const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);
    await legalLink.click();
    await waitForUiToSettle(page);
    const popupPage = await popupPromise;
    const legalPage = popupPage ?? page;

    await waitForUiToSettle(legalPage);
    await pickVisible(
      legalPage,
      [
        legalPage.getByRole("heading", { name: headingPattern }),
        legalPage.getByText(headingPattern),
      ],
      `Legal heading: ${headingPattern.source}`,
      20_000,
    );

    const legalText = await legalPage.locator("body").innerText();
    if (legalText.replace(/\s+/g, " ").trim().length < 120) {
      throw new Error("Legal content text not visible enough");
    }

    await captureCheckpoint(legalPage, screenshotName, true);
    const finalUrl = legalPage.url();

    if (popupPage) {
      await popupPage.close();
      await page.bringToFront();
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
      await waitForUiToSettle(page);
    }

    return finalUrl;
  };

  const executeStep = async (key: ReportKey, fn: () => Promise<void>) => {
    try {
      await fn();
      markPass(key);
    } catch (error) {
      markFail(key, error);
      await captureCheckpoint(page, `failure_${key}`, true).catch(() => null);
    }
  };

  try {
    await executeStep("Login", async () => {
      const configuredLoginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_BASE_URL;
      if (configuredLoginUrl) {
        await page.goto(configuredLoginUrl, { waitUntil: "domcontentloaded" });
      }

      let loginButton: Locator;
      try {
        loginButton = await pickVisible(
          page,
          [
            page.getByRole("button", {
              name: /Sign in with Google|Iniciar sesion con Google|Continuar con Google/i,
            }),
            page.getByRole("link", {
              name: /Sign in with Google|Iniciar sesion con Google|Continuar con Google/i,
            }),
            page.getByText(/Sign in with Google|Iniciar sesion con Google|Continuar con Google/i),
          ],
          "Google login button",
          12_000,
        );
      } catch {
        throw new Error(
          "Login page not detected. Start on SaleADS login page or set SALEADS_LOGIN_URL/SALEADS_BASE_URL.",
        );
      }

      const popupPromise = page.waitForEvent("popup", { timeout: 10_000 }).catch(() => null);
      await clickAndWait(loginButton);
      const authPopup = await popupPromise;

      if (authPopup) {
        await waitForUiToSettle(authPopup);
        await maybeSelectGoogleAccount(authPopup);
        await authPopup.waitForEvent("close", { timeout: 120_000 }).catch(() => null);
        await page.bringToFront();
      } else {
        await maybeSelectGoogleAccount(page).catch(() => null);
      }

      await ensureSidebarVisible();
      await captureCheckpoint(page, "dashboard_loaded");
      loginCompleted = true;
    });

    await executeStep("Mi Negocio menu", async () => {
      if (!loginCompleted) {
        throw new Error("Skipped because Login validation failed.");
      }
      const sidebar = await pickVisible(
        page,
        [
          page.locator("aside"),
          page.getByRole("navigation"),
          page.locator('[class*="sidebar"]'),
        ],
        "left sidebar",
      );

      const negocioEntry = await pickVisible(
        page,
        [
          sidebar.getByRole("button", { name: /Negocio/i }),
          sidebar.getByRole("link", { name: /Negocio/i }),
          sidebar.getByText(/Negocio/i),
          page.getByRole("button", { name: /Negocio/i }),
          page.getByText(/Negocio/i),
        ],
        "Negocio section",
      );
      await clickAndWait(negocioEntry);

      const miNegocioOption = await pickVisible(
        page,
        [
          sidebar.getByRole("button", { name: /Mi Negocio/i }),
          sidebar.getByRole("link", { name: /Mi Negocio/i }),
          sidebar.getByText(/Mi Negocio/i),
          page.getByRole("button", { name: /Mi Negocio/i }),
          page.getByText(/Mi Negocio/i),
        ],
        "Mi Negocio option",
      );
      await clickAndWait(miNegocioOption);

      await pickVisible(page, [page.getByText(/Agregar Negocio/i)], "Agregar Negocio submenu");
      await pickVisible(
        page,
        [page.getByText(/Administrar Negocios/i)],
        "Administrar Negocios submenu",
      );
      await captureCheckpoint(page, "mi_negocio_expanded_menu");
      miNegocioExpanded = true;
    });

    await executeStep("Agregar Negocio modal", async () => {
      if (!miNegocioExpanded) {
        throw new Error("Skipped because Mi Negocio menu validation failed.");
      }
      const agregarNegocioOption = await pickVisible(
        page,
        [
          page.getByRole("button", { name: /Agregar Negocio/i }),
          page.getByRole("link", { name: /Agregar Negocio/i }),
          page.getByText(/Agregar Negocio/i),
        ],
        "Agregar Negocio option",
      );
      await clickAndWait(agregarNegocioOption);

      const modal = await pickVisible(
        page,
        [
          page.getByRole("dialog"),
          page.locator('[role="dialog"]'),
          page.locator('[class*="modal"]'),
        ],
        "Crear Nuevo Negocio modal",
      );

      await pickVisible(
        page,
        [modal.getByText(/Crear Nuevo Negocio/i), page.getByText(/Crear Nuevo Negocio/i)],
        "Modal title",
      );
      const businessNameInput = await pickVisible(
        page,
        [
          modal.getByLabel(/Nombre del Negocio/i),
          modal.getByPlaceholder(/Nombre del Negocio/i),
          modal.locator("input[name*='nombre' i]"),
          modal.locator("input").first(),
        ],
        "Nombre del Negocio input",
      );
      await pickVisible(
        page,
        [
          modal.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i),
          page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i),
        ],
        "Business quota text",
      );
      await pickVisible(
        page,
        [modal.getByRole("button", { name: /Cancelar/i }), page.getByText(/Cancelar/i)],
        "Cancelar button",
      );
      await pickVisible(
        page,
        [modal.getByRole("button", { name: /Crear Negocio/i }), page.getByText(/Crear Negocio/i)],
        "Crear Negocio button",
      );

      await businessNameInput.click();
      await businessNameInput.fill(BUSINESS_NAME);
      await captureCheckpoint(page, "agregar_negocio_modal");

      const cancelButton = await pickVisible(
        page,
        [modal.getByRole("button", { name: /Cancelar/i }), modal.getByText(/Cancelar/i)],
        "Cancelar button in modal",
      );
      await clickAndWait(cancelButton);
    });

    await executeStep("Administrar Negocios view", async () => {
      if (!miNegocioExpanded) {
        throw new Error("Skipped because Mi Negocio menu validation failed.");
      }
      const administrarNegocios = page.getByText(/Administrar Negocios/i).first();
      if (!(await administrarNegocios.isVisible().catch(() => false))) {
        const miNegocioToggle = await pickVisible(
          page,
          [
            page.getByRole("button", { name: /Mi Negocio/i }),
            page.getByRole("link", { name: /Mi Negocio/i }),
            page.getByText(/Mi Negocio/i),
          ],
          "Mi Negocio toggle",
        );
        await clickAndWait(miNegocioToggle);
      }

      const administrarOption = await pickVisible(
        page,
        [
          page.getByRole("link", { name: /Administrar Negocios/i }),
          page.getByRole("button", { name: /Administrar Negocios/i }),
          page.getByText(/Administrar Negocios/i),
        ],
        "Administrar Negocios option",
      );
      await clickAndWait(administrarOption);

      await pickVisible(
        page,
        [page.getByText(/Informaci[oó]n General/i)],
        "Informacion General section",
      );
      await pickVisible(
        page,
        [page.getByText(/Detalles de la Cuenta/i)],
        "Detalles de la Cuenta section",
      );
      await pickVisible(page, [page.getByText(/Tus Negocios/i)], "Tus Negocios section");
      await pickVisible(page, [page.getByText(/Secci[oó]n Legal/i)], "Seccion Legal section");
      await captureCheckpoint(page, "administrar_negocios_account_page", true);
      administrarNegociosOpened = true;
    });

    await executeStep("Información General", async () => {
      if (!administrarNegociosOpened) {
        throw new Error("Skipped because Administrar Negocios view validation failed.");
      }
      await pickVisible(page, [page.getByText(/Informaci[oó]n General/i)], "Informacion General title");
      await pickVisible(
        page,
        [page.getByText(/\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/i)],
        "User email",
      );
      await pickVisible(
        page,
        [
          page.getByText(/Nombre|Usuario|Perfil/i),
          page.locator("h1, h2, h3, strong, [class*='name']").first(),
        ],
        "User name",
      );
      await pickVisible(page, [page.getByText(/BUSINESS PLAN/i)], "Business plan text");
      await pickVisible(
        page,
        [page.getByRole("button", { name: /Cambiar Plan/i }), page.getByText(/Cambiar Plan/i)],
        "Cambiar Plan button",
      );
    });

    await executeStep("Detalles de la Cuenta", async () => {
      if (!administrarNegociosOpened) {
        throw new Error("Skipped because Administrar Negocios view validation failed.");
      }
      await pickVisible(page, [page.getByText(/Detalles de la Cuenta/i)], "Detalles de la Cuenta title");
      await pickVisible(page, [page.getByText(/Cuenta creada/i)], "Cuenta creada text");
      await pickVisible(page, [page.getByText(/Estado activo/i)], "Estado activo text");
      await pickVisible(page, [page.getByText(/Idioma seleccionado/i)], "Idioma seleccionado text");
    });

    await executeStep("Tus Negocios", async () => {
      if (!administrarNegociosOpened) {
        throw new Error("Skipped because Administrar Negocios view validation failed.");
      }
      await pickVisible(page, [page.getByText(/Tus Negocios/i)], "Tus Negocios title");
      await pickVisible(
        page,
        [page.getByRole("button", { name: /Agregar Negocio/i }), page.getByText(/Agregar Negocio/i)],
        "Agregar Negocio button in list",
      );
      await pickVisible(
        page,
        [page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i)],
        "Business quota text in list",
      );
      const businessListOrCards = await pickVisible(
        page,
        [
          page.locator("[data-testid*='business']").first(),
          page.locator("table tbody tr").first(),
          page.locator("[class*='business']").first(),
        ],
        "Business list",
      );
      await expect(businessListOrCards).toBeVisible();
    });

    await executeStep("Términos y Condiciones", async () => {
      if (!administrarNegociosOpened) {
        throw new Error("Skipped because Administrar Negocios view validation failed.");
      }
      const termsUrl = await openLegalPageAndReturn(
        /T[eé]rminos y Condiciones/i,
        /T[eé]rminos y Condiciones/i,
        "terminos_y_condiciones",
      );
      legalUrls.terminosYCondiciones = termsUrl;
    });

    await executeStep("Política de Privacidad", async () => {
      if (!administrarNegociosOpened) {
        throw new Error("Skipped because Administrar Negocios view validation failed.");
      }
      const privacyUrl = await openLegalPageAndReturn(
        /Pol[ií]tica de Privacidad/i,
        /Pol[ií]tica de Privacidad/i,
        "politica_de_privacidad",
      );
      legalUrls.politicaDePrivacidad = privacyUrl;
    });
  } finally {
    const serializedReport = {
      generatedAt: new Date().toISOString(),
      testName: "saleads_mi_negocio_full_test",
      results: {
        Login: finalReport.Login,
        "Mi Negocio menu": finalReport["Mi Negocio menu"],
        "Agregar Negocio modal": finalReport["Agregar Negocio modal"],
        "Administrar Negocios view": finalReport["Administrar Negocios view"],
        "Información General": finalReport["Información General"],
        "Detalles de la Cuenta": finalReport["Detalles de la Cuenta"],
        "Tus Negocios": finalReport["Tus Negocios"],
        "Términos y Condiciones": finalReport["Términos y Condiciones"],
        "Política de Privacidad": finalReport["Política de Privacidad"],
      },
      legalUrls,
    };

    const reportPath = testInfo.outputPath("mi_negocio_final_report.json");
    await fs.writeFile(reportPath, JSON.stringify(serializedReport, null, 2), "utf8");
    await testInfo.attach("mi_negocio_final_report", {
      path: reportPath,
      contentType: "application/json",
    });

    console.log("FINAL_REPORT_START");
    console.log(JSON.stringify(serializedReport, null, 2));
    console.log("FINAL_REPORT_END");
  }

  if (failures.length > 0) {
    throw new Error(`One or more workflow validations failed:\n- ${failures.join("\n- ")}`);
  }
});
