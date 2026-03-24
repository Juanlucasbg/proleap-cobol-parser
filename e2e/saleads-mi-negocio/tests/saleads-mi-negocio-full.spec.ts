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
  name: StepName;
  status: "PASS" | "FAIL";
  details?: string;
};

const report: StepResult[] = [];
const orderedStepNames: StepName[] = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Información General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Términos y Condiciones",
  "Política de Privacidad",
];

const userEmail =
  process.env.SALEADS_GOOGLE_ACCOUNT ??
  process.env.GOOGLE_ACCOUNT_EMAIL ??
  "juanlucasbarbiergarzon@gmail.com";
const configuredLoginUrl = process.env.SALEADS_LOGIN_URL ?? process.env.BASE_URL;

async function waitForUiSettle(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  try {
    await page.waitForLoadState("networkidle", { timeout: 5000 });
  } catch {
    // Some apps keep websocket traffic alive, so networkidle may not happen.
  }
  await page.waitForTimeout(500);
}

async function clickByPriority(candidates: Locator[]): Promise<void> {
  for (const locator of candidates) {
    try {
      const visibleCount = await locator.count();
      if (visibleCount === 0) continue;
      const first = locator.first();
      if (await first.isVisible()) {
        await first.click();
        return;
      }
    } catch {
      // continue trying fallbacks
    }
  }
  throw new Error("Could not click any candidate locator.");
}

async function clickByPriorityAndWait(page: Page, candidates: Locator[]): Promise<void> {
  await clickByPriority(candidates);
  await waitForUiSettle(page);
}

async function tryClickByPriorityAndWait(
  page: Page,
  candidates: Locator[],
): Promise<boolean> {
  try {
    await clickByPriority(candidates);
    await waitForUiSettle(page);
    return true;
  } catch {
    return false;
  }
}

function pushResult(name: StepName, status: "PASS" | "FAIL", details?: string) {
  report.push({ name, status, details });
}

async function assertSectionVisible(page: Page, text: string): Promise<void> {
  await expect(page.getByText(text, { exact: false }).first()).toBeVisible({
    timeout: 20000,
  });
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("login with Google and validate Mi Negocio module", async ({
    page,
  }, testInfo) => {
    const legalUrls: Partial<Record<StepName, string>> = {};

    try {
      // Step 1: Login with Google
      if (configuredLoginUrl) {
        await page.goto(configuredLoginUrl, { waitUntil: "domcontentloaded" });
      } else if (page.url() === "about:blank") {
        throw new Error(
          "Set SALEADS_LOGIN_URL or BASE_URL, or start this test from an already-opened login page.",
        );
      }
      await waitForUiSettle(page);

      const signInWithGoogle = page.getByRole("button", {
        name: /sign in with google|iniciar sesión con google|google/i,
      });
      const loginButton = page.getByRole("button", {
        name: /login|log in|iniciar sesión|acceder/i,
      });
      const googleTextLink = page.getByText(
        /sign in with google|iniciar sesión con google/i,
      );

      await clickByPriorityAndWait(page, [signInWithGoogle, loginButton, googleTextLink]);

      // If Google account picker appears, choose requested account.
      try {
        const account = page
          .getByText(new RegExp(userEmail.replace(".", "\\."), "i"))
          .first();
        if (await account.isVisible({ timeout: 6000 })) {
          await account.click();
          await waitForUiSettle(page);
        }
      } catch {
        // Account selector may not appear if session already exists.
      }

      // Validate dashboard/main app + left sidebar.
      await expect(
        page.getByText(/negocio|dashboard|inicio|mi negocio/i).first(),
      ).toBeVisible({ timeout: 30000 });
      const sidebar = page.locator("aside").first();
      await expect(sidebar).toBeVisible({ timeout: 20000 });

      await page.screenshot({
        path: testInfo.outputPath("01-dashboard-loaded.png"),
        fullPage: true,
      });
      pushResult("Login", "PASS");
    } catch (error) {
      pushResult("Login", "FAIL", String(error));
      throw error;
    }

    try {
      // Step 2: Open Mi Negocio menu
      const negocioSectionCandidates = [
        page.getByRole("button", { name: /^negocio$/i }),
        page.getByRole("link", { name: /^negocio$/i }),
        page.getByText(/^negocio$/i),
      ];
      await tryClickByPriorityAndWait(page, negocioSectionCandidates);

      const miNegocioCandidates = [
        page.getByRole("button", { name: /negocio/i }),
        page.getByRole("link", { name: /negocio/i }),
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i),
      ];
      await clickByPriorityAndWait(page, miNegocioCandidates);

      await expect(page.getByText(/agregar negocio/i).first()).toBeVisible({
        timeout: 20000,
      });
      await expect(page.getByText(/administrar negocios/i).first()).toBeVisible({
        timeout: 20000,
      });

      await page.screenshot({
        path: testInfo.outputPath("02-mi-negocio-expanded.png"),
        fullPage: true,
      });
      pushResult("Mi Negocio menu", "PASS");
    } catch (error) {
      pushResult("Mi Negocio menu", "FAIL", String(error));
      throw error;
    }

    try {
      // Step 3: Validate Agregar Negocio modal
      await clickByPriorityAndWait(page, [
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByRole("link", { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i),
      ]);

      await expect(page.getByText(/crear nuevo negocio/i).first()).toBeVisible({
        timeout: 20000,
      });
      const businessNameInput = page.getByPlaceholder(/nombre del negocio/i);
      if ((await businessNameInput.count()) > 0) {
        await expect(businessNameInput.first()).toBeVisible({ timeout: 10000 });
      } else {
        await expect(
          page.getByLabel(/nombre del negocio/i).first(),
        ).toBeVisible({ timeout: 10000 });
      }

      await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible({
        timeout: 20000,
      });
      await expect(page.getByRole("button", { name: /cancelar/i }).first()).toBeVisible({
        timeout: 20000,
      });
      await expect(
        page.getByRole("button", { name: /crear negocio/i }).first(),
      ).toBeVisible({ timeout: 20000 });

      // Optional actions
      if ((await businessNameInput.count()) > 0) {
        await businessNameInput.first().fill("Negocio Prueba Automatización");
      } else {
        await page
          .getByLabel(/nombre del negocio/i)
          .first()
          .fill("Negocio Prueba Automatización");
      }
      await page.screenshot({
        path: testInfo.outputPath("03-agregar-negocio-modal.png"),
        fullPage: true,
      });
      await page.getByRole("button", { name: /cancelar/i }).first().click();
      await waitForUiSettle(page);
      pushResult("Agregar Negocio modal", "PASS");
    } catch (error) {
      pushResult("Agregar Negocio modal", "FAIL", String(error));
      throw error;
    }

    try {
      // Step 4: Open Administrar Negocios
      if (!(await page.getByText(/administrar negocios/i).first().isVisible())) {
        await clickByPriorityAndWait(page, [
          page.getByRole("button", { name: /mi negocio/i }),
          page.getByRole("link", { name: /mi negocio/i }),
          page.getByText(/mi negocio/i),
        ]);
      }

      await clickByPriorityAndWait(page, [
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i),
      ]);

      await assertSectionVisible(page, "Información General");
      await assertSectionVisible(page, "Detalles de la Cuenta");
      await assertSectionVisible(page, "Tus Negocios");
      await assertSectionVisible(page, "Sección Legal");

      await page.screenshot({
        path: testInfo.outputPath("04-administrar-negocios-page.png"),
        fullPage: true,
      });
      pushResult("Administrar Negocios view", "PASS");
    } catch (error) {
      pushResult("Administrar Negocios view", "FAIL", String(error));
      throw error;
    }

    try {
      // Step 5: Validate Información General
      await expect(page.getByText(/business plan/i).first()).toBeVisible({
        timeout: 20000,
      });
      await expect(page.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible({
        timeout: 20000,
      });
      // User name / email are validated with resilient selectors.
      await expect(page.locator("body")).toContainText(/@\S+\.\S+/);
      await expect(
        page.locator("section,div").filter({ hasText: /información general/i }).first(),
      ).toBeVisible({ timeout: 20000 });
      pushResult("Información General", "PASS");
    } catch (error) {
      pushResult("Información General", "FAIL", String(error));
      throw error;
    }

    try {
      // Step 6: Validate Detalles de la Cuenta
      await expect(page.getByText(/cuenta creada/i).first()).toBeVisible({
        timeout: 20000,
      });
      await expect(page.getByText(/estado activo/i).first()).toBeVisible({
        timeout: 20000,
      });
      await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible({
        timeout: 20000,
      });
      pushResult("Detalles de la Cuenta", "PASS");
    } catch (error) {
      pushResult("Detalles de la Cuenta", "FAIL", String(error));
      throw error;
    }

    try {
      // Step 7: Validate Tus Negocios
      await expect(page.getByText(/tus negocios/i).first()).toBeVisible({
        timeout: 20000,
      });
      await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible({
        timeout: 20000,
      });
      await expect(
        page.getByRole("button", { name: /agregar negocio/i }).first(),
      ).toBeVisible({ timeout: 20000 });
      pushResult("Tus Negocios", "PASS");
    } catch (error) {
      pushResult("Tus Negocios", "FAIL", String(error));
      throw error;
    }

    // Steps 8-9 helper
    const validateLegalLink = async (
      linkText: RegExp,
      headingText: RegExp,
      screenshotName: string,
      reportName: StepName,
    ) => {
      try {
        const legalLink = page.getByRole("link", { name: linkText }).first();
        await expect(legalLink).toBeVisible({ timeout: 20000 });

        const [newPageOrNull] = await Promise.all([
          page.context().waitForEvent("page", { timeout: 5000 }).catch(() => null),
          legalLink.click(),
        ]);
        await waitForUiSettle(page);

        const legalPage = newPageOrNull ?? page;
        if (newPageOrNull) {
          await legalPage.waitForLoadState("domcontentloaded");
          await waitForUiSettle(legalPage);
        }

        await expect(legalPage.getByRole("heading", { name: headingText }).first()).toBeVisible({
          timeout: 30000,
        });
        await expect(legalPage.locator("main,article,body")).toContainText(/\S+/);

        await legalPage.screenshot({
          path: testInfo.outputPath(screenshotName),
          fullPage: true,
        });
        // eslint-disable-next-line no-console
        console.log(`[LEGAL_URL] ${reportName}: ${legalPage.url()}`);
        legalUrls[reportName] = legalPage.url();

        if (newPageOrNull) {
          await legalPage.close();
          await page.bringToFront();
          await waitForUiSettle(page);
        }

        pushResult(reportName, "PASS");
      } catch (error) {
        pushResult(reportName, "FAIL", String(error));
        throw error;
      }
    };

    await validateLegalLink(
      /términos y condiciones|terminos y condiciones/i,
      /términos y condiciones|terminos y condiciones/i,
      "08-terminos-y-condiciones.png",
      "Términos y Condiciones",
    );

    await validateLegalLink(
      /política de privacidad|politica de privacidad/i,
      /política de privacidad|politica de privacidad/i,
      "09-politica-de-privacidad.png",
      "Política de Privacidad",
    );

    await testInfo.attach("legal-urls.txt", {
      body: Buffer.from(
        [
          `Terminos y Condiciones: ${legalUrls["Términos y Condiciones"] ?? "N/A"}`,
          `Politica de Privacidad: ${legalUrls["Política de Privacidad"] ?? "N/A"}`,
        ].join("\n"),
      ),
      contentType: "text/plain",
    });
  });

  test.afterAll(async () => {
    // eslint-disable-next-line no-console
    console.log("=== FINAL REPORT ===");
    for (const stepName of orderedStepNames) {
      const item = [...report].reverse().find((step) => step.name === stepName);
      if (!item) {
        // eslint-disable-next-line no-console
        console.log(`${stepName}: FAIL | Not executed`);
        continue;
      }
      // eslint-disable-next-line no-console
      console.log(`${item.name}: ${item.status}${item.details ? ` | ${item.details}` : ""}`);
    }
  });
});
