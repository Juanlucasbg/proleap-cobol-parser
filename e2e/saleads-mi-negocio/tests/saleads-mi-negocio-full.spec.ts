import { expect, type Locator, type Page, type TestInfo, test } from "@playwright/test";
import { writeFile } from "node:fs/promises";

type ReportStatus = "PASS" | "FAIL";

type WorkflowReport = {
  Login: ReportStatus;
  "Mi Negocio menu": ReportStatus;
  "Agregar Negocio modal": ReportStatus;
  "Administrar Negocios view": ReportStatus;
  "Informacion General": ReportStatus;
  "Detalles de la Cuenta": ReportStatus;
  "Tus Negocios": ReportStatus;
  "Terminos y Condiciones": ReportStatus;
  "Politica de Privacidad": ReportStatus;
};

const ACCOUNT_EMAIL =
  process.env.SALEADS_GOOGLE_ACCOUNT_EMAIL ??
  "juanlucasbarbiergarzon@gmail.com";
const LOGIN_URL = process.env.SALEADS_LOGIN_URL;

const workflowReport: WorkflowReport = {
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

function escapeRegex(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUi(page: Page): Promise<void> {
  await Promise.allSettled([
    page.waitForLoadState("domcontentloaded", { timeout: 10_000 }),
    page.waitForLoadState("networkidle", { timeout: 5_000 }),
  ]);
  await page.waitForTimeout(500);
}

async function firstVisible(candidates: Locator[]): Promise<Locator | null> {
  for (const candidate of candidates) {
    const current = candidate.first();
    if (await current.isVisible().catch(() => false)) {
      return current;
    }
  }

  for (const candidate of candidates) {
    const current = candidate.first();
    try {
      await current.waitFor({ state: "visible", timeout: 5_000 });
      return current;
    } catch {
      // Continue searching.
    }
  }

  return null;
}

async function captureCheckpoint(
  page: Page,
  checkpointName: string,
  fullPage: boolean,
  testInfo: TestInfo,
): Promise<void> {
  const screenshotPath = testInfo.outputPath(
    `${checkpointName.toLowerCase().replace(/[^a-z0-9]+/g, "-")}.png`,
  );
  await page.screenshot({ path: screenshotPath, fullPage });
  await testInfo.attach(checkpointName, {
    path: screenshotPath,
    contentType: "image/png",
  });
}

test("saleads_mi_negocio_full_test", async ({ context, page }, testInfo) => {
  const failures: string[] = [];
  const finalUrls: { terminos: string | null; privacidad: string | null } = {
    terminos: null,
    privacidad: null,
  };

  const runStep = async (
    reportField: keyof WorkflowReport,
    label: string,
    action: () => Promise<void>,
  ): Promise<void> => {
    await test.step(label, async () => {
      try {
        await action();
        workflowReport[reportField] = "PASS";
      } catch (error) {
        workflowReport[reportField] = "FAIL";
        failures.push(
          `${label}: ${error instanceof Error ? error.message : String(error)}`,
        );
      }
    });
  };

  await runStep("Login", "1) Login with Google", async () => {
    if (LOGIN_URL) {
      await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded" });
    } else if (page.url() === "about:blank") {
      throw new Error(
        "Set SALEADS_LOGIN_URL when running headless automation from a blank page.",
      );
    }

    await waitForUi(page);

    const loginButton = await firstVisible([
      page.getByRole("button", {
        name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i,
      }),
      page.getByRole("link", {
        name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i,
      }),
      page.getByText(
        /sign in with google|iniciar sesi[oó]n con google|continuar con google/i,
      ),
    ]);

    if (!loginButton) {
      throw new Error("Google login button was not found.");
    }

    const [popup] = await Promise.all([
      context.waitForEvent("page", { timeout: 8_000 }).catch(() => null),
      loginButton.click(),
    ]);

    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      const accountOption = popup.getByText(ACCOUNT_EMAIL, { exact: false });
      if (await accountOption.first().isVisible().catch(() => false)) {
        await accountOption.first().click();
      }
    } else {
      const accountOption = page.getByText(ACCOUNT_EMAIL, { exact: false });
      if (await accountOption.first().isVisible().catch(() => false)) {
        await accountOption.first().click();
      }
    }

    await waitForUi(page);

    const sidebar = await firstVisible([
      page.locator("aside"),
      page.getByRole("navigation"),
      page.locator("nav"),
    ]);

    if (!sidebar) {
      throw new Error("Main interface sidebar is not visible after login.");
    }

    await captureCheckpoint(page, "dashboard-loaded", false, testInfo);
  });

  await runStep("Mi Negocio menu", "2) Open Mi Negocio menu", async () => {
    const sidebar = await firstVisible([
      page.locator("aside"),
      page.getByRole("navigation"),
      page.locator("nav"),
    ]);
    if (!sidebar) {
      throw new Error("Sidebar was not found.");
    }

    const negocioSection = await firstVisible([
      sidebar.getByText(/negocio/i),
      page.getByText(/negocio/i),
    ]);
    if (negocioSection) {
      await negocioSection.click();
      await waitForUi(page);
    }

    const miNegocioEntry = await firstVisible([
      sidebar.getByText(/^Mi Negocio$/i),
      page.getByText(/^Mi Negocio$/i),
    ]);
    if (!miNegocioEntry) {
      throw new Error("'Mi Negocio' option was not found.");
    }

    await miNegocioEntry.click();
    await waitForUi(page);

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();

    await captureCheckpoint(page, "mi-negocio-menu-expanded", false, testInfo);
  });

  await runStep(
    "Agregar Negocio modal",
    "3) Validate Agregar Negocio modal",
    async () => {
      const addBusiness = await firstVisible([
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i),
      ]);
      if (!addBusiness) {
        throw new Error("'Agregar Negocio' action was not found.");
      }

      await addBusiness.click();
      await waitForUi(page);

      const modalTitle = page.getByText(/^Crear Nuevo Negocio$/i).first();
      await expect(modalTitle).toBeVisible();

      const businessNameInput = await firstVisible([
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
      ]);
      if (!businessNameInput) {
        throw new Error("'Nombre del Negocio' field was not found.");
      }

      await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
      await expect(
        page.getByRole("button", { name: /^Cancelar$/i }).first(),
      ).toBeVisible();
      await expect(
        page.getByRole("button", { name: /^Crear Negocio$/i }).first(),
      ).toBeVisible();

      await businessNameInput.click();
      await businessNameInput.fill("Negocio Prueba Automatizacion");

      await captureCheckpoint(page, "crear-nuevo-negocio-modal", false, testInfo);

      await page.getByRole("button", { name: /^Cancelar$/i }).first().click();
      await waitForUi(page);
    },
  );

  await runStep(
    "Administrar Negocios view",
    "4) Open Administrar Negocios view",
    async () => {
      const administrarNegocios = await firstVisible([
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i),
      ]);
      if (!administrarNegocios) {
        const miNegocioEntry = await firstVisible([
          page.getByText(/^Mi Negocio$/i),
          page.getByRole("button", { name: /^Mi Negocio$/i }),
        ]);
        if (!miNegocioEntry) {
          throw new Error(
            "'Mi Negocio' was not found to expand submenu before navigating.",
          );
        }
        await miNegocioEntry.click();
        await waitForUi(page);
      }

      const administrarNegociosAfterExpand = await firstVisible([
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i),
      ]);
      if (!administrarNegociosAfterExpand) {
        throw new Error("'Administrar Negocios' option is not visible.");
      }

      await administrarNegociosAfterExpand.click();
      await waitForUi(page);

      await expect(page.getByText(/Informaci[oó]n General/i).first()).toBeVisible();
      await expect(
        page.getByText(/Detalles de la Cuenta/i).first(),
      ).toBeVisible();
      await expect(page.getByText(/^Tus Negocios$/i).first()).toBeVisible();
      await expect(page.getByText(/Secci[oó]n Legal/i).first()).toBeVisible();

      await captureCheckpoint(page, "administrar-negocios-page", true, testInfo);
    },
  );

  await runStep("Informacion General", "5) Validate Informacion General", async () => {
    await expect(
      page.getByText(new RegExp(escapeRegex(ACCOUNT_EMAIL), "i")).first(),
    ).toBeVisible();

    await expect(
      page
        .getByText(/juan\s*lucas|barbier|garzon|juanlucasbarbiergarzon/i)
        .first(),
    ).toBeVisible();

    await expect(page.getByText(/BUSINESS PLAN/i).first()).toBeVisible();
    await expect(
      page.getByRole("button", { name: /Cambiar Plan/i }).first(),
    ).toBeVisible();
  });

  await runStep(
    "Detalles de la Cuenta",
    "6) Validate Detalles de la Cuenta",
    async () => {
      await expect(page.getByText(/Cuenta creada/i).first()).toBeVisible();
      await expect(page.getByText(/Estado activo/i).first()).toBeVisible();
      await expect(page.getByText(/Idioma seleccionado/i).first()).toBeVisible();
    },
  );

  await runStep("Tus Negocios", "7) Validate Tus Negocios", async () => {
    const businessSection = page
      .locator("section, div, article")
      .filter({ hasText: /Tus Negocios/i })
      .first();
    await expect(businessSection).toBeVisible();

    await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
    await expect(page.getByText(/Tienes 2 de 3 negocios/i).first()).toBeVisible();
  });

  await runStep(
    "Terminos y Condiciones",
    "8) Validate Terminos y Condiciones",
    async () => {
      const termsEntry = await firstVisible([
        page.getByRole("link", { name: /T[eé]rminos y Condiciones/i }),
        page.getByRole("button", { name: /T[eé]rminos y Condiciones/i }),
        page.getByText(/T[eé]rminos y Condiciones/i),
      ]);
      if (!termsEntry) {
        throw new Error("'Términos y Condiciones' action was not found.");
      }

      const [newTab] = await Promise.all([
        context.waitForEvent("page", { timeout: 8_000 }).catch(() => null),
        termsEntry.click(),
      ]);

      const legalPage = newTab ?? page;
      await waitForUi(legalPage);

      await expect(
        legalPage.getByText(/T[eé]rminos y Condiciones/i).first(),
      ).toBeVisible();
      await expect(
        legalPage
          .locator("h1, h2, h3, p, article, section, div")
          .filter({ hasText: /t[eé]rminos|condiciones|uso|servicio|legal/i })
          .first(),
      ).toBeVisible();

      finalUrls.terminos = legalPage.url();
      await captureCheckpoint(legalPage, "terminos-y-condiciones", true, testInfo);

      if (newTab) {
        await newTab.close();
        await page.bringToFront();
      } else {
        await page.goBack().catch(() => undefined);
        await waitForUi(page);
      }
    },
  );

  await runStep(
    "Politica de Privacidad",
    "9) Validate Politica de Privacidad",
    async () => {
      const privacyEntry = await firstVisible([
        page.getByRole("link", { name: /Pol[ií]tica de Privacidad/i }),
        page.getByRole("button", { name: /Pol[ií]tica de Privacidad/i }),
        page.getByText(/Pol[ií]tica de Privacidad/i),
      ]);
      if (!privacyEntry) {
        throw new Error("'Política de Privacidad' action was not found.");
      }

      const [newTab] = await Promise.all([
        context.waitForEvent("page", { timeout: 8_000 }).catch(() => null),
        privacyEntry.click(),
      ]);

      const legalPage = newTab ?? page;
      await waitForUi(legalPage);

      await expect(
        legalPage.getByText(/Pol[ií]tica de Privacidad/i).first(),
      ).toBeVisible();
      await expect(
        legalPage
          .locator("h1, h2, h3, p, article, section, div")
          .filter({
            hasText: /privacidad|datos personales|informaci[oó]n|legal/i,
          })
          .first(),
      ).toBeVisible();

      finalUrls.privacidad = legalPage.url();
      await captureCheckpoint(legalPage, "politica-de-privacidad", true, testInfo);

      if (newTab) {
        await newTab.close();
        await page.bringToFront();
      } else {
        await page.goBack().catch(() => undefined);
        await waitForUi(page);
      }
    },
  );

  await test.step("10) Build final PASS/FAIL report", async () => {
    const reportBody = {
      reportName: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      finalUrls,
      results: workflowReport,
      failures,
    };

    const reportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
    await writeFile(reportPath, JSON.stringify(reportBody, null, 2), "utf8");
    await testInfo.attach("final-report", {
      path: reportPath,
      contentType: "application/json",
    });
  });

  expect(
    failures,
    failures.length
      ? `Some validations failed:\n${failures.join("\n")}`
      : "All validations passed.",
  ).toEqual([]);
});
