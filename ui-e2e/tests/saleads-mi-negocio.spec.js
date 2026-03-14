const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login with Google and validate Mi Negocio workflow", async ({ page, context }, testInfo) => {
    const report = {
      Login: { status: "FAIL" },
      "Mi Negocio menu": { status: "FAIL" },
      "Agregar Negocio modal": { status: "FAIL" },
      "Administrar Negocios view": { status: "FAIL" },
      "Información General": { status: "FAIL" },
      "Detalles de la Cuenta": { status: "FAIL" },
      "Tus Negocios": { status: "FAIL" },
      "Términos y Condiciones": { status: "FAIL" },
      "Política de Privacidad": { status: "FAIL" }
    };
    const failedSteps = [];
    const orderedSteps = [
      "Login",
      "Mi Negocio menu",
      "Agregar Negocio modal",
      "Administrar Negocios view",
      "Información General",
      "Detalles de la Cuenta",
      "Tus Negocios",
      "Términos y Condiciones",
      "Política de Privacidad"
    ];

    const uiPause = async (ms = 1200) => {
      await page.waitForLoadState("domcontentloaded").catch(() => {});
      await page.waitForTimeout(ms);
    };

    const markPass = (key, extras = {}) => {
      report[key] = { status: "PASS", ...extras };
    };

    const markFail = (key, error) => {
      const reason = error instanceof Error ? error.message : String(error);
      report[key] = { status: "FAIL", reason };
      if (!failedSteps.includes(key)) {
        failedSteps.push(key);
      }
    };

    const evidenceShot = async (name, fullPage = false, targetPage = page) => {
      const shotPath = testInfo.outputPath(name);
      await targetPage.screenshot({ path: shotPath, fullPage });
      await testInfo.attach(name, { path: shotPath, contentType: "image/png" });
    };

    const firstVisible = async (locators, timeoutMs = 12000) => {
      const stopAt = Date.now() + timeoutMs;
      while (Date.now() < stopAt) {
        for (const locator of locators) {
          const candidate = locator.first();
          if (await candidate.isVisible().catch(() => false)) {
            return candidate;
          }
        }
        await page.waitForTimeout(250);
      }
      throw new Error("Could not find any visible element from the expected locator set.");
    };

    const getSectionByTitle = (titleRegex) =>
      page
        .locator("section, article, div")
        .filter({
          has: page.getByText(titleRegex)
        })
        .first();

    const runStep = async (stepName, fn) => {
      try {
        await fn();
      } catch (error) {
        markFail(stepName, error);
      }
    };

    const blockFollowingSteps = (fromStep, reason) => {
      const fromIndex = orderedSteps.indexOf(fromStep);
      for (let i = fromIndex + 1; i < orderedSteps.length; i += 1) {
        const step = orderedSteps[i];
        if (report[step].status !== "PASS" && !report[step].reason) {
          markFail(step, reason);
        }
      }
    };

    if (page.url() === "about:blank" && process.env.SALEADS_START_URL) {
      await page.goto(process.env.SALEADS_START_URL, { waitUntil: "domcontentloaded" });
      await uiPause();
    }

    await runStep("Login", async () => {
      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);

      const signInGoogle = await firstVisible([
        page.getByRole("button", { name: /sign in with google|ingresar con google|google/i }),
        page.getByRole("link", { name: /sign in with google|ingresar con google|google/i }),
        page.getByText(/sign in with google|ingresar con google/i),
        page.locator("button, a").filter({ hasText: /google/i })
      ]);

      await signInGoogle.click();
      await uiPause();

      const popup = await popupPromise;
      const googleContextPage = popup || page;
      await googleContextPage.waitForLoadState("domcontentloaded").catch(() => {});

      const accountOption = googleContextPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }).first();
      if (await accountOption.isVisible().catch(() => false)) {
        await accountOption.click();
        await googleContextPage.waitForLoadState("domcontentloaded").catch(() => {});
      }

      if (popup) {
        await popup.waitForClose({ timeout: 60000 }).catch(() => {});
      }

      await page.bringToFront();
      await page.waitForLoadState("domcontentloaded").catch(() => {});
      await page.waitForTimeout(2000);

      const mainLayout = await firstVisible([
        page.locator("main"),
        page.getByRole("main"),
        page.locator("body")
      ]);
      await expect(mainLayout).toBeVisible();

      const leftSidebar = await firstVisible([
        page.locator("aside"),
        page.locator("nav").filter({ hasText: /negocio|dashboard|mi negocio/i }),
        page.locator("[class*='sidebar'], [data-testid*='sidebar']")
      ]);
      await expect(leftSidebar).toBeVisible();

      await evidenceShot("01-dashboard-loaded.png", true);
      markPass("Login");
    });

    if (report.Login.status !== "PASS") {
      blockFollowingSteps("Login", "Blocked because login did not reach the main application.");
    } else {
      await runStep("Mi Negocio menu", async () => {
      const negocioSection = page.getByText(/^Negocio$/i).first();
      if (await negocioSection.isVisible().catch(() => false)) {
        await negocioSection.click();
        await uiPause(800);
      }

      const miNegocio = await firstVisible([
        page.getByText(/^Mi Negocio$/i),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByRole("button", { name: /mi negocio/i })
      ]);
      await miNegocio.click();
      await uiPause();

      const agregarNegocio = page.getByText(/^Agregar Negocio$/i).first();
      const administrarNegocios = page.getByText(/^Administrar Negocios$/i).first();

      await expect(agregarNegocio).toBeVisible();
      await expect(administrarNegocios).toBeVisible();

      await evidenceShot("02-mi-negocio-menu-expanded.png");
      markPass("Mi Negocio menu");
    });

      await runStep("Agregar Negocio modal", async () => {
      const agregarNegocioTrigger = await firstVisible([
        page.getByText(/^Agregar Negocio$/i),
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByRole("link", { name: /agregar negocio/i })
      ]);
      await agregarNegocioTrigger.click();
      await uiPause();

      const modal = await firstVisible([
        page.getByRole("dialog").filter({ hasText: /Crear Nuevo Negocio/i }),
        page.locator("[role='dialog'], .modal, [class*='modal']").filter({ hasText: /Crear Nuevo Negocio/i })
      ]);

      await expect(modal.getByText(/Crear Nuevo Negocio/i)).toBeVisible();
      await expect(modal.getByText(/Nombre del Negocio/i)).toBeVisible();
      await expect(modal.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(modal.getByRole("button", { name: /Cancelar/i })).toBeVisible();
      await expect(modal.getByRole("button", { name: /Crear Negocio/i })).toBeVisible();

      const businessNameInput = modal.getByRole("textbox", { name: /Nombre del Negocio/i }).first();
      if (await businessNameInput.isVisible().catch(() => false)) {
        await businessNameInput.click();
        await businessNameInput.fill("Negocio Prueba Automatización");
      }

      await evidenceShot("03-agregar-negocio-modal.png");

      const cancelButton = modal.getByRole("button", { name: /Cancelar/i }).first();
      await cancelButton.click();
      await uiPause();

      markPass("Agregar Negocio modal");
    });

      await runStep("Administrar Negocios view", async () => {
      const administrarNegocios = page.getByText(/^Administrar Negocios$/i).first();
      if (!(await administrarNegocios.isVisible().catch(() => false))) {
        const miNegocio = await firstVisible([
          page.getByText(/^Mi Negocio$/i),
          page.getByRole("link", { name: /mi negocio/i }),
          page.getByRole("button", { name: /mi negocio/i })
        ]);
        await miNegocio.click();
        await uiPause(800);
      }

      await page.getByText(/^Administrar Negocios$/i).first().click();
      await uiPause(1800);

      await expect(page.getByText(/Información General/i).first()).toBeVisible();
      await expect(page.getByText(/Detalles de la Cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/Tus Negocios/i).first()).toBeVisible();
      await expect(page.getByText(/Sección Legal/i).first()).toBeVisible();

      await evidenceShot("04-administrar-negocios-view.png", true);
      markPass("Administrar Negocios view");
    });

      await runStep("Información General", async () => {
      const infoGeneralSection = getSectionByTitle(/Información General/i);
      await expect(infoGeneralSection).toBeVisible();

      const fullInfoText = await infoGeneralSection.innerText();
      const hasEmail = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/.test(fullInfoText);
      if (!hasEmail) {
        throw new Error("No user email found in 'Información General'.");
      }

      const hasLikelyUserName = fullInfoText
        .split("\n")
        .map((line) => line.trim())
        .some(
          (line) =>
            line.length >= 3 &&
            /[A-Za-zÁÉÍÓÚÑáéíóúñ]{3,}/.test(line) &&
            !/@/.test(line) &&
            !/información general|business plan|cambiar plan/i.test(line)
        );
      if (!hasLikelyUserName) {
        throw new Error("No likely user name found in 'Información General'.");
      }

      await expect(infoGeneralSection.getByText(/BUSINESS PLAN/i)).toBeVisible();
      await expect(infoGeneralSection.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();

      markPass("Información General");
    });

      await runStep("Detalles de la Cuenta", async () => {
      const detailsSection = getSectionByTitle(/Detalles de la Cuenta/i);
      await expect(detailsSection).toBeVisible();
      await expect(detailsSection.getByText(/Cuenta creada/i)).toBeVisible();
      await expect(detailsSection.getByText(/Estado activo/i)).toBeVisible();
      await expect(detailsSection.getByText(/Idioma seleccionado/i)).toBeVisible();

      markPass("Detalles de la Cuenta");
    });

      await runStep("Tus Negocios", async () => {
      const businessesSection = getSectionByTitle(/Tus Negocios/i);
      await expect(businessesSection).toBeVisible();
      await expect(businessesSection.getByText(/Agregar Negocio/i)).toBeVisible();
      await expect(businessesSection.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();

      const businessesText = await businessesSection.innerText();
      const hasBusinessList = businessesText
        .split("\n")
        .map((line) => line.trim())
        .some((line) => line.length > 2 && !/Tus Negocios|Agregar Negocio|Tienes 2 de 3 negocios/i.test(line));
      if (!hasBusinessList) {
        throw new Error("Business list is not visible in 'Tus Negocios'.");
      }

      markPass("Tus Negocios");
    });

    const validateLegalLink = async (linkText, headingRegex, reportKey, screenshotName) => {
      const link = await firstVisible([
        page.getByRole("link", { name: new RegExp(linkText, "i") }),
        page.getByText(new RegExp(`^${linkText}$`, "i"))
      ]);

      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
      const sameTabNavigationPromise = page.waitForNavigation({ timeout: 12000 }).catch(() => null);

      await link.click();
      await uiPause(1000);

      const popup = await popupPromise;
      const targetPage = popup || page;
      await targetPage.waitForLoadState("domcontentloaded").catch(() => {});
      await targetPage.waitForTimeout(1000);
      await sameTabNavigationPromise;

      const heading = targetPage.getByRole("heading", { name: headingRegex }).first();
      if (!(await heading.isVisible().catch(() => false))) {
        await expect(targetPage.getByText(headingRegex).first()).toBeVisible();
      } else {
        await expect(heading).toBeVisible();
      }

      const bodyText = (await targetPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
      if (bodyText.length < 120) {
        throw new Error(`${linkText}: legal content appears too short.`);
      }

      await evidenceShot(screenshotName, true, targetPage);
      const finalUrl = targetPage.url();

      if (popup) {
        await popup.close();
        await page.bringToFront();
      } else {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await uiPause(1000);
      }

      markPass(reportKey, { finalUrl });
    };

      await runStep("Términos y Condiciones", async () => {
      await validateLegalLink(
        "Términos y Condiciones",
        /Términos y Condiciones/i,
        "Términos y Condiciones",
        "05-terminos-y-condiciones.png"
      );
    });

      await runStep("Política de Privacidad", async () => {
      await validateLegalLink(
        "Política de Privacidad",
        /Política de Privacidad/i,
        "Política de Privacidad",
        "06-politica-de-privacidad.png"
      );
    });
    }

    const reportPath = testInfo.outputPath("saleads-mi-negocio-report.json");
    await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf-8");
    await testInfo.attach("saleads-mi-negocio-report", {
      path: reportPath,
      contentType: "application/json"
    });

    // Final report in test logs with PASS/FAIL status per requested field.
    console.table(
      Object.entries(report).map(([step, details]) => ({
        step,
        status: details.status,
        reason: details.reason || "",
        finalUrl: details.finalUrl || ""
      }))
    );

    expect(
      failedSteps,
      `One or more workflow blocks failed. Detailed report: ${JSON.stringify(report)}`
    ).toEqual([]);
  });
});
