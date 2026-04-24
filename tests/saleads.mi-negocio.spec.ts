import { expect, test, type Locator } from "@playwright/test";

type StepResult = "PASS" | "FAIL";

type ReportField =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad";

const REPORT_ORDER: ReportField[] = [
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

test.describe("SaleADS - Mi Negocio full workflow", () => {
  test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
    const results = new Map<ReportField, StepResult>();
    const legalUrls = new Map<"Términos y Condiciones" | "Política de Privacidad", string>();

    const markPass = (field: ReportField) => results.set(field, "PASS");
    const markFail = (field: ReportField) => results.set(field, "FAIL");

    const checkpoint = async (name: string, fullPage = false) => {
      await page.waitForLoadState("domcontentloaded");
      await page.waitForTimeout(350);
      const safeName = name.toLowerCase().replace(/[^a-z0-9]+/g, "-");
      const filePath = testInfo.outputPath(`${safeName}.png`);
      await page.screenshot({ path: filePath, fullPage });
      await testInfo.attach(name, {
        path: filePath,
        contentType: "image/png",
      });
    };

    const waitAfterClick = async (target: Locator) => {
      await expect(target).toBeVisible();
      await target.click();
      await page.waitForLoadState("domcontentloaded");
      await page.waitForTimeout(500);
    };

    const resolveVisible = async (
      locators: Locator[],
      timeoutMs = 15_000,
      description = "element"
    ): Promise<Locator> => {
      if (locators.length === 0) {
        throw new Error(`No locators supplied for ${description}.`);
      }

      const deadline = Date.now() + timeoutMs;
      while (Date.now() < deadline) {
        for (const locator of locators) {
          const candidate = locator.first();
          if (await candidate.isVisible().catch(() => false)) {
            return candidate;
          }
        }
        await page.waitForTimeout(200);
      }

      throw new Error(`Could not locate visible ${description} within ${timeoutMs}ms.`);
    };

    const clickIfVisible = async (locators: Locator[]) => {
      const maybeTarget = await resolveVisible(locators, 2_000).catch(() => null);
      if (!maybeTarget) {
        return false;
      }
      await waitAfterClick(maybeTarget);
      return true;
    };

    const waitForMainAppShell = async () => {
      const sidebarCandidates = [
        page.getByRole("navigation"),
        page.locator("aside"),
        page.getByText("Negocio", { exact: false }),
      ];
      const shellCandidates = [
        page.getByText("Dashboard", { exact: false }),
        page.getByText("Negocio", { exact: false }),
        page.getByRole("main"),
      ];
      await resolveVisible(shellCandidates, 60_000, "main application shell");
      await resolveVisible(sidebarCandidates, 60_000, "sidebar navigation");
    };

    const ensureMiNegocioExpanded = async () => {
      const agregarLocators = [
        page.getByRole("link", { name: "Agregar Negocio", exact: false }),
        page.getByRole("button", { name: "Agregar Negocio", exact: false }),
        page.getByText("Agregar Negocio", { exact: false }),
      ];
      const administrarLocators = [
        page.getByRole("link", { name: "Administrar Negocios", exact: false }),
        page.getByRole("button", { name: "Administrar Negocios", exact: false }),
        page.getByText("Administrar Negocios", { exact: false }),
      ];

      const agregarVisible = await resolveVisible(
        agregarLocators,
        1_500,
        "Agregar Negocio entry"
      ).catch(() => null);
      if (agregarVisible) {
        return;
      }
      const administrarVisible = await resolveVisible(
        administrarLocators,
        1_500,
        "Administrar Negocios entry"
      ).catch(() => null);
      if (administrarVisible) {
        return;
      }

      const miNegocioToggle = await resolveVisible([
        page.getByRole("button", { name: "Mi Negocio", exact: false }),
        page.getByRole("link", { name: "Mi Negocio", exact: false }),
        page.getByText("Mi Negocio", { exact: false }),
      ], 15_000, "Mi Negocio menu");
      await waitAfterClick(miNegocioToggle);
      await resolveVisible(
        [...agregarLocators, ...administrarLocators],
        15_000,
        "Mi Negocio submenu entries"
      );
    };

    const legalLinkLocators = (linkText: string): Locator[] => [
      page.getByRole("link", { name: linkText, exact: false }),
      page.getByRole("button", { name: linkText, exact: false }),
      page.getByText(linkText, { exact: false }),
    ];

    const openAndValidateLegal = async (
      linkText: "Términos y Condiciones" | "Política de Privacidad",
      expectedHeading: string
    ) => {
      const origin = new URL(page.url()).origin;
      const opener = await resolveVisible(legalLinkLocators(linkText), 20_000, `${linkText} link`);

      const popupPromise = page.context().waitForEvent("page", { timeout: 7_500 }).catch(() => null);
      await waitAfterClick(opener);
      const popup = await popupPromise;

      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        await popup.waitForTimeout(500);
        await resolveVisible(
          [
            popup.getByRole("heading", { name: expectedHeading, exact: false }),
            popup.getByText(expectedHeading, { exact: false }),
          ],
          20_000,
          `${linkText} heading`
        );
        await expect(popup.locator("body")).toContainText(expectedHeading, { timeout: 20_000 });
        await expect(popup.locator("body")).toContainText(/\S.{20,}/, { timeout: 20_000 });
        const screenshotPath = testInfo.outputPath(
          `${linkText.toLowerCase().replace(/[^a-z0-9]+/g, "-")}.png`
        );
        await popup.screenshot({ path: screenshotPath, fullPage: true });
        await testInfo.attach(`${linkText} screenshot`, {
          path: screenshotPath,
          contentType: "image/png",
        });
        legalUrls.set(linkText, popup.url());
        await popup.close();
        await page.bringToFront();
        await expect(page).toHaveURL(new RegExp(`^${origin.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}`), {
          timeout: 20_000,
        });
      } else {
        await resolveVisible(
          [
            page.getByRole("heading", { name: expectedHeading, exact: false }),
            page.getByText(expectedHeading, { exact: false }),
          ],
          20_000,
          `${linkText} heading`
        );
        await expect(page.locator("body")).toContainText(expectedHeading, { timeout: 20_000 });
        await expect(page.locator("body")).toContainText(/\S.{20,}/, { timeout: 20_000 });
        await checkpoint(`${linkText} legal page`, true);
        legalUrls.set(linkText, page.url());
        await page.goBack({ waitUntil: "domcontentloaded" });
        await page.waitForTimeout(500);
      }
    };

    const accountEmailPattern = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;

    try {
      if (page.url() === "about:blank" && process.env.SALEADS_BASE_URL) {
        await page.goto(process.env.SALEADS_BASE_URL, { waitUntil: "domcontentloaded" });
      }
      await page.waitForLoadState("domcontentloaded");

      // Step 1: Login with Google and validate app shell.
      const loginLocators = [
        page.getByRole("button", { name: "Sign in with Google", exact: false }),
        page.getByRole("button", { name: "Iniciar sesión con Google", exact: false }),
        page.getByRole("button", { name: "Continuar con Google", exact: false }),
        page.getByText("Sign in with Google", { exact: false }),
        page.getByText("Iniciar sesión con Google", { exact: false }),
        page.getByText("Continuar con Google", { exact: false }),
      ];
      const appAlreadyLoaded = await resolveVisible(
        [
          page.getByRole("navigation"),
          page.locator("aside"),
          page.getByText("Negocio", { exact: false }),
        ],
        7_500,
        "main app or sidebar"
      ).catch(() => null);

      if (!appAlreadyLoaded) {
        const loginButton = await resolveVisible(loginLocators, 30_000, "Google login button");
        await waitAfterClick(loginButton);

        const googleAccountOption = page.getByText("juanlucasbarbiergarzon@gmail.com", {
          exact: false,
        });
        if (await googleAccountOption.isVisible().catch(() => false)) {
          await waitAfterClick(googleAccountOption);
        }
      }

      await waitForMainAppShell();
      await checkpoint("dashboard-loaded");
      markPass("Login");

      // Step 2: Open Mi Negocio and validate submenu options.
      const negocioLocators = [
        page.getByRole("button", { name: "Negocio", exact: false }),
        page.getByRole("link", { name: "Negocio", exact: false }),
        page.getByText("Negocio", { exact: false }),
      ];
      await clickIfVisible(negocioLocators);

      await ensureMiNegocioExpanded();
      await resolveVisible(
        [
          page.getByRole("link", { name: "Agregar Negocio", exact: false }),
          page.getByRole("button", { name: "Agregar Negocio", exact: false }),
          page.getByText("Agregar Negocio", { exact: false }),
        ],
        15_000,
        "Agregar Negocio submenu"
      );
      await resolveVisible(
        [
          page.getByRole("link", { name: "Administrar Negocios", exact: false }),
          page.getByRole("button", { name: "Administrar Negocios", exact: false }),
          page.getByText("Administrar Negocios", { exact: false }),
        ],
        15_000,
        "Administrar Negocios submenu"
      );
      await checkpoint("mi-negocio-expanded-menu");
      markPass("Mi Negocio menu");

      // Step 3: Open and validate Agregar Negocio modal.
      await waitAfterClick(
        await resolveVisible(
          [
          page.getByRole("link", { name: "Agregar Negocio", exact: false }),
          page.getByRole("button", { name: "Agregar Negocio", exact: false }),
          page.getByText("Agregar Negocio", { exact: false }),
          ],
          15_000,
          "Agregar Negocio action"
        )
      );

      const modal = await resolveVisible(
        [page.getByRole("dialog"), page.locator("[role='dialog']"), page.locator(".modal:visible")],
        20_000,
        "Crear Nuevo Negocio modal"
      );
      await expect(modal.getByText("Crear Nuevo Negocio", { exact: false })).toBeVisible();
      const nombreInput = await resolveVisible(
        [
          modal.getByLabel("Nombre del Negocio", { exact: false }),
          modal.getByPlaceholder("Nombre del Negocio"),
          modal.locator("input").first(),
        ],
        15_000,
        "Nombre del Negocio input"
      );
      await expect(modal.getByText("Tienes 2 de 3 negocios", { exact: false })).toBeVisible();
      await expect(modal.getByRole("button", { name: "Cancelar", exact: false })).toBeVisible();
      await expect(modal.getByRole("button", { name: "Crear Negocio", exact: false })).toBeVisible();
      await nombreInput.fill("Negocio Prueba Automatización");
      await checkpoint("agregar-negocio-modal");
      await waitAfterClick(modal.getByRole("button", { name: "Cancelar", exact: false }));
      markPass("Agregar Negocio modal");

      // Step 4: Open Administrar Negocios and validate sections.
      await ensureMiNegocioExpanded();
      await waitAfterClick(
        await resolveVisible(
          [
          page.getByRole("link", { name: "Administrar Negocios", exact: false }),
          page.getByRole("button", { name: "Administrar Negocios", exact: false }),
          page.getByText("Administrar Negocios", { exact: false }),
          ],
          15_000,
          "Administrar Negocios action"
        )
      );
      await expect(page.getByText("Información General", { exact: false })).toBeVisible({ timeout: 20_000 });
      await expect(page.getByText("Detalles de la Cuenta", { exact: false })).toBeVisible({ timeout: 20_000 });
      await expect(page.getByText("Tus Negocios", { exact: false })).toBeVisible({ timeout: 20_000 });
      await expect(page.getByText("Sección Legal", { exact: false })).toBeVisible({ timeout: 20_000 });
      await checkpoint("administrar-negocios-account-page", true);
      markPass("Administrar Negocios view");

      // Step 5: Información General validations.
      await expect(page.locator("body")).toContainText(accountEmailPattern, { timeout: 20_000 });
      await expect(page.getByText("BUSINESS PLAN", { exact: false })).toBeVisible();
      await expect(page.getByRole("button", { name: "Cambiar Plan", exact: false })).toBeVisible();
      await resolveVisible(
        [
          page.getByTestId("user-name"),
          page
            .locator("h1, h2, h3")
            .filter({
              hasNotText: "Información General",
            })
            .first(),
          page.locator("[class*='name']").first(),
        ],
        15_000,
        "user name display"
      );
      markPass("Información General");

      // Step 6: Detalles de la Cuenta validations.
      await expect(page.getByText("Cuenta creada", { exact: false })).toBeVisible();
      await expect(page.getByText("Estado activo", { exact: false })).toBeVisible();
      await expect(page.getByText("Idioma seleccionado", { exact: false })).toBeVisible();
      markPass("Detalles de la Cuenta");

      // Step 7: Tus Negocios validations.
      const businessSection = await resolveVisible(
        [
          page.locator("section").filter({ hasText: "Tus Negocios" }).first(),
          page.locator("div").filter({ hasText: "Tus Negocios" }).first(),
        ],
        20_000,
        "Tus Negocios section"
      );
      await resolveVisible(
        [
          businessSection.getByRole("button", { name: "Agregar Negocio", exact: false }),
          businessSection.getByRole("link", { name: "Agregar Negocio", exact: false }),
          businessSection.getByText("Agregar Negocio", { exact: false }),
        ],
        15_000,
        "Tus Negocios Agregar Negocio button"
      );
      await expect(businessSection.getByText("Tienes 2 de 3 negocios", { exact: false })).toBeVisible();
      markPass("Tus Negocios");

      // Step 8: Términos y Condiciones.
      await openAndValidateLegal("Términos y Condiciones", "Términos y Condiciones");
      markPass("Términos y Condiciones");

      // Step 9: Política de Privacidad.
      await openAndValidateLegal("Política de Privacidad", "Política de Privacidad");
      markPass("Política de Privacidad");
    } catch (error) {
      for (const field of REPORT_ORDER) {
        if (!results.has(field)) {
          markFail(field);
        }
      }
      throw error;
    } finally {
      for (const field of REPORT_ORDER) {
        if (!results.has(field)) {
          markFail(field);
        }
      }

      const report = REPORT_ORDER.map((field) => ({
        field,
        result: results.get(field) ?? "FAIL",
      }));

      await testInfo.attach("final-report.json", {
        body: JSON.stringify(
          {
            testName: "saleads_mi_negocio_full_test",
            environment: page.url(),
            report,
            legalUrls: Object.fromEntries(legalUrls.entries()),
          },
          null,
          2
        ),
        contentType: "application/json",
      });

      await testInfo.attach("final-report.md", {
        body: [
          "# Final Report",
          "",
          ...report.map((item) => `- ${item.field}: ${item.result}`),
          "",
          "## Legal URLs",
          `- Términos y Condiciones: ${legalUrls.get("Términos y Condiciones") ?? "N/A"}`,
          `- Política de Privacidad: ${legalUrls.get("Política de Privacidad") ?? "N/A"}`,
        ].join("\n"),
        contentType: "text/markdown",
      });
    }
  });
});
