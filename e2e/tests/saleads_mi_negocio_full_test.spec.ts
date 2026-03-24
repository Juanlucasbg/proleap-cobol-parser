import { expect, type Locator, type Page, test } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

type StepStatus = "PASS" | "FAIL";

type StepResult = {
  name: string;
  status: StepStatus;
  details?: string;
};

const REPORT_FIELDS = [
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

const GOAL_TEST_NAME = "saleads_mi_negocio_full_test";
const ARTIFACTS_DIR = path.join(process.cwd(), "test-results", GOAL_TEST_NAME);

function normalize(text: string): string {
  return text.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase();
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle");
}

async function clickFirstVisible(candidates: Locator[]): Promise<boolean> {
  for (const candidate of candidates) {
    if (await candidate.first().isVisible().catch(() => false)) {
      await candidate.first().click();
      return true;
    }
  }
  return false;
}

async function ensureVisible(locator: Locator, message: string): Promise<void> {
  await expect(locator, message).toBeVisible({ timeout: 30_000 });
}

async function captureCheckpoint(
  page: Page,
  checkpointName: string,
): Promise<string> {
  const safeName = checkpointName
    .replace(/[^a-zA-Z0-9-_]/g, "_")
    .replace(/_+/g, "_")
    .replace(/^_|_$/g, "");
  const dir = ARTIFACTS_DIR;
  fs.mkdirSync(dir, { recursive: true });
  const filename = `${Date.now()}_${safeName}.png`;
  const fullPath = path.join(dir, filename);
  await page.screenshot({ path: fullPath, fullPage: true });
  return fullPath;
}

async function openNegocioMenu(page: Page): Promise<void> {
  const negocio = page
    .getByRole("button", { name: /negocio/i })
    .or(page.getByRole("link", { name: /negocio/i }))
    .or(page.getByText(/^negocio$/i));
  await negocio.first().click();
  await waitForUi(page);
}

function withDetails(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  return String(error);
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("Login with Google and validate Mi Negocio workflow", async ({
    page,
    context,
  }) => {
    test.setTimeout(240_000);
    const stepResults: Record<string, StepResult> = {};

    for (const field of REPORT_FIELDS) {
      stepResults[field] = { name: field, status: "FAIL" };
    }

    await test.step("1) Login with Google", async () => {
      try {
        await waitForUi(page);

        const loginClicked = await clickFirstVisible([
          page.getByRole("button", { name: /sign in with google/i }),
          page.getByRole("link", { name: /sign in with google/i }),
          page.getByText(/sign in with google/i),
          page.getByRole("button", { name: /google/i }),
          page.getByRole("link", { name: /google/i }),
          page.getByRole("button", { name: /iniciar sesion con google/i }),
          page.getByRole("button", { name: /iniciar sesión con google/i }),
        ]);

        if (!loginClicked) {
          // Some runs may already be authenticated.
          const sidebarExisting = page
            .getByRole("navigation")
            .or(page.locator("aside"))
            .or(page.getByText(/negocio/i));
          await ensureVisible(
            sidebarExisting.first(),
            "Login button not found and no authenticated sidebar detected.",
          );
        } else {
          await waitForUi(page);
        }

        const googleAccount = page.getByText("juanlucasbarbiergarzon@gmail.com");
        if (await googleAccount.first().isVisible().catch(() => false)) {
          await googleAccount.first().click();
          await waitForUi(page);
        }

        const sidebar = page
          .getByRole("navigation")
          .or(page.locator("aside"))
          .or(page.getByText(/negocio/i));
        await ensureVisible(
          sidebar.first(),
          "Main interface/sidebar did not appear after login.",
        );
        await captureCheckpoint(page, "step1_dashboard_loaded");
        stepResults["Login"] = { name: "Login", status: "PASS" };
      } catch (error) {
        stepResults["Login"] = {
          name: "Login",
          status: "FAIL",
          details: withDetails(error),
        };
      }
    });

    await test.step("2) Open Mi Negocio menu", async () => {
      try {
        await openNegocioMenu(page);
        await ensureVisible(
          page.getByText(/agregar negocio/i),
          "'Agregar Negocio' was not visible.",
        );
        await ensureVisible(
          page.getByText(/administrar negocios/i),
          "'Administrar Negocios' was not visible.",
        );
        await captureCheckpoint(page, "step2_mi_negocio_expanded");
        stepResults["Mi Negocio menu"] = {
          name: "Mi Negocio menu",
          status: "PASS",
        };
      } catch (error) {
        stepResults["Mi Negocio menu"] = {
          name: "Mi Negocio menu",
          status: "FAIL",
          details: withDetails(error),
        };
      }
    });

    await test.step("3) Validate Agregar Negocio modal", async () => {
      try {
        await ensureVisible(
          page.getByText(/agregar negocio/i).first(),
          "Agregar Negocio option is not visible.",
        );
        await page.getByText(/agregar negocio/i).first().click();
        await waitForUi(page);

        const modal = page
          .getByRole("dialog")
          .filter({ hasText: /crear nuevo negocio/i })
          .first();
        await ensureVisible(modal, "Crear Nuevo Negocio modal was not visible.");
        await ensureVisible(
          modal.getByText(/crear nuevo negocio/i),
          "Modal title was not visible.",
        );
        await ensureVisible(
          modal.getByLabel(/nombre del negocio/i).or(
            modal.getByPlaceholder(/nombre del negocio/i),
          ),
          "'Nombre del Negocio' field is missing.",
        );
        await ensureVisible(
          modal.getByText(/tienes 2 de 3 negocios/i),
          "Business limit text is missing.",
        );
        await ensureVisible(
          modal.getByRole("button", { name: /cancelar/i }),
          "Cancelar button missing.",
        );
        await ensureVisible(
          modal.getByRole("button", { name: /crear negocio/i }),
          "Crear Negocio button missing.",
        );

        const businessNameInput = modal
          .getByLabel(/nombre del negocio/i)
          .or(modal.getByPlaceholder(/nombre del negocio/i))
          .first();
        if (await businessNameInput.isVisible().catch(() => false)) {
          await businessNameInput.click();
          await businessNameInput.fill("Negocio Prueba Automatización");
        }

        await captureCheckpoint(page, "step3_agregar_negocio_modal");
        await modal.getByRole("button", { name: /cancelar/i }).click();
        await waitForUi(page);

        stepResults["Agregar Negocio modal"] = {
          name: "Agregar Negocio modal",
          status: "PASS",
        };
      } catch (error) {
        stepResults["Agregar Negocio modal"] = {
          name: "Agregar Negocio modal",
          status: "FAIL",
          details: withDetails(error),
        };
      }
    });

    await test.step("4) Open Administrar Negocios", async () => {
      try {
        const adminOption = page.getByText(/administrar negocios/i).first();
        if (!(await adminOption.isVisible().catch(() => false))) {
          await openNegocioMenu(page);
        }
        await page.getByText(/administrar negocios/i).first().click();
        await waitForUi(page);

        await ensureVisible(
          page.getByText(/informacion general|información general/i),
          "Información General section missing.",
        );
        await ensureVisible(
          page.getByText(/detalles de la cuenta/i),
          "Detalles de la Cuenta section missing.",
        );
        await ensureVisible(
          page.getByText(/tus negocios/i),
          "Tus Negocios section missing.",
        );
        await ensureVisible(
          page.getByText(/seccion legal|sección legal/i),
          "Sección Legal section missing.",
        );

        await captureCheckpoint(page, "step4_administrar_negocios_view");
        stepResults["Administrar Negocios view"] = {
          name: "Administrar Negocios view",
          status: "PASS",
        };
      } catch (error) {
        stepResults["Administrar Negocios view"] = {
          name: "Administrar Negocios view",
          status: "FAIL",
          details: withDetails(error),
        };
      }
    });

    await test.step("5) Validate Información General", async () => {
      try {
        const pageText = normalize(await page.locator("body").innerText());
        expect(pageText.includes("business plan")).toBeTruthy();
        expect(pageText.includes("cambiar plan")).toBeTruthy();
        expect(pageText.includes("@")).toBeTruthy();

        // Ensure there is at least one non-empty heading/value for user name.
        const profileBlock = page
          .locator("section,div")
          .filter({ hasText: /informacion general|información general/i })
          .first();
        await ensureVisible(profileBlock, "Información General block not visible.");

        await ensureVisible(
          page.getByRole("button", { name: /cambiar plan/i }),
          "Cambiar Plan button missing.",
        );

        stepResults["Información General"] = {
          name: "Información General",
          status: "PASS",
        };
      } catch (error) {
        stepResults["Información General"] = {
          name: "Información General",
          status: "FAIL",
          details: withDetails(error),
        };
      }
    });

    await test.step("6) Validate Detalles de la Cuenta", async () => {
      try {
        await ensureVisible(
          page.getByText(/cuenta creada/i),
          "'Cuenta creada' is missing.",
        );
        await ensureVisible(
          page.getByText(/estado activo|activo/i),
          "'Estado activo' is missing.",
        );
        await ensureVisible(
          page.getByText(/idioma seleccionado|idioma/i),
          "'Idioma seleccionado' is missing.",
        );
        stepResults["Detalles de la Cuenta"] = {
          name: "Detalles de la Cuenta",
          status: "PASS",
        };
      } catch (error) {
        stepResults["Detalles de la Cuenta"] = {
          name: "Detalles de la Cuenta",
          status: "FAIL",
          details: withDetails(error),
        };
      }
    });

    await test.step("7) Validate Tus Negocios", async () => {
      try {
        await ensureVisible(
          page.getByText(/tus negocios/i),
          "Tus Negocios title missing.",
        );
        await ensureVisible(
          page.getByRole("button", { name: /agregar negocio/i }).first(),
          "Agregar Negocio button missing in Tus Negocios.",
        );
        await ensureVisible(
          page.getByText(/tienes 2 de 3 negocios/i),
          "Business limit text missing in Tus Negocios.",
        );
        stepResults["Tus Negocios"] = {
          name: "Tus Negocios",
          status: "PASS",
        };
      } catch (error) {
        stepResults["Tus Negocios"] = {
          name: "Tus Negocios",
          status: "FAIL",
          details: withDetails(error),
        };
      }
    });

    const legalEvidence: Record<"terminosUrl" | "politicaUrl", string> = {
      terminosUrl: "",
      politicaUrl: "",
    };

    async function validateLegalLink(
      linkText: string,
      headingRegex: RegExp,
      checkpointName: string,
      urlKey: "terminosUrl" | "politicaUrl",
    ): Promise<void> {
      const previousPage = page;
      const link = previousPage.getByText(linkText, { exact: false }).first();
      await ensureVisible(link, `${linkText} link is not visible.`);

      let destinationPage: Page = previousPage;

      const popupPromise = context.waitForEvent("page", { timeout: 4_000 });
      await link.click();
      await waitForUi(previousPage);

      try {
        const popup = await popupPromise;
        await popup.waitForLoadState("domcontentloaded");
        await popup.waitForLoadState("networkidle");
        destinationPage = popup;
      } catch {
        destinationPage = previousPage;
      }

      await ensureVisible(
        destinationPage.getByRole("heading", { name: headingRegex }).or(
          destinationPage.getByText(headingRegex),
        ),
        `Expected legal heading for "${linkText}" not found.`,
      );

      const legalContent = await destinationPage.locator("body").innerText();
      expect(legalContent.trim().length).toBeGreaterThan(50);
      await captureCheckpoint(destinationPage, checkpointName);
      legalEvidence[urlKey] = destinationPage.url();

      if (destinationPage !== previousPage) {
        await destinationPage.close();
        await previousPage.bringToFront();
        await waitForUi(previousPage);
      }
    }

    await test.step("8) Validate Términos y Condiciones", async () => {
      try {
        await validateLegalLink(
          "Términos y Condiciones",
          /terminos y condiciones|términos y condiciones/i,
          "step8_terminos_y_condiciones",
          "terminosUrl",
        );
        stepResults["Términos y Condiciones"] = {
          name: "Términos y Condiciones",
          status: "PASS",
          details: `URL: ${legalEvidence.terminosUrl}`,
        };
      } catch (error) {
        stepResults["Términos y Condiciones"] = {
          name: "Términos y Condiciones",
          status: "FAIL",
          details: withDetails(error),
        };
      }
    });

    await test.step("9) Validate Política de Privacidad", async () => {
      try {
        await validateLegalLink(
          "Política de Privacidad",
          /politica de privacidad|política de privacidad/i,
          "step9_politica_de_privacidad",
          "politicaUrl",
        );
        stepResults["Política de Privacidad"] = {
          name: "Política de Privacidad",
          status: "PASS",
          details: `URL: ${legalEvidence.politicaUrl}`,
        };
      } catch (error) {
        stepResults["Política de Privacidad"] = {
          name: "Política de Privacidad",
          status: "FAIL",
          details: withDetails(error),
        };
      }
    });

    await test.step("10) Final report", async () => {
      const reportPath = path.join(ARTIFACTS_DIR, "final-report.json");
      fs.mkdirSync(path.dirname(reportPath), { recursive: true });
      const report = {
        test: GOAL_TEST_NAME,
        generatedAt: new Date().toISOString(),
        results: REPORT_FIELDS.map((field) => stepResults[field]),
      };
      fs.writeFileSync(reportPath, JSON.stringify(report, null, 2), "utf-8");

      const failed = REPORT_FIELDS.filter(
        (field) => stepResults[field].status === "FAIL",
      );
      expect(
        failed,
        `One or more workflow steps failed: ${failed.join(", ")}`,
      ).toEqual([]);
    });
  });
});
