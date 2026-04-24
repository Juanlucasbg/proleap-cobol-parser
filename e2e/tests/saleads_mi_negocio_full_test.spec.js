const fs = require("node:fs/promises");
const path = require("node:path");
const { test, expect } = require("@playwright/test");

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const SCREENSHOTS_DIR = path.resolve(__dirname, "..", "artifacts", "screenshots");
const REPORTS_DIR = path.resolve(__dirname, "..", "artifacts", "reports");

function nowStamp() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

function normalizeText(value) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/\s+/g, " ")
    .trim();
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForStableUI(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(400);
}

async function clickFirstVisible(page, candidates, options = {}) {
  for (const candidate of candidates) {
    const locator = candidate(page).first();
    const visible = await locator.isVisible().catch(() => false);
    if (!visible) {
      continue;
    }

    await locator.click(options);
    await waitForStableUI(page);
    return true;
  }

  return false;
}

async function assertAnyVisible(page, candidates, message) {
  for (const candidate of candidates) {
    const locator = candidate(page).first();
    const visible = await locator.isVisible().catch(() => false);
    if (visible) {
      await expect(locator, message).toBeVisible({ timeout: 10000 });
      return;
    }
  }

  throw new Error(message);
}

async function capture(page, name, report) {
  await fs.mkdir(SCREENSHOTS_DIR, { recursive: true });
  const filename = `${nowStamp()}-${name}.png`;
  const filepath = path.join(SCREENSHOTS_DIR, filename);
  await page.screenshot({ path: filepath, fullPage: true });
  report.screenshots.push(filepath);
}

async function runStep(report, key, fn) {
  const step = report.steps[key];
  try {
    await fn();
    step.status = "PASS";
  } catch (error) {
    step.status = "FAIL";
    step.details = error instanceof Error ? error.message : String(error);
  }
}

async function writeReport(report) {
  await fs.mkdir(REPORTS_DIR, { recursive: true });
  const filepath = path.join(REPORTS_DIR, `saleads_mi_negocio_full_test-${nowStamp()}.json`);
  await fs.writeFile(filepath, JSON.stringify(report, null, 2), "utf8");
  return filepath;
}

async function clickLegalLinkAndValidate({
  page,
  linkCandidates,
  headingCandidates,
  report,
  stepKey,
}) {
  const step = report.steps[stepKey];
  let usedNewTab = false;
  let legalPage = page;

  const popupPromise = page.waitForEvent("popup", { timeout: 5000 }).catch(() => null);
  const clicked = await clickFirstVisible(page, linkCandidates);
  if (!clicked) {
    throw new Error("No se encontro el enlace legal por texto visible.");
  }

  const popup = await popupPromise;
  if (popup) {
    usedNewTab = true;
    legalPage = popup;
    await waitForStableUI(legalPage);
  } else {
    await waitForStableUI(page);
  }

  let headingVisible = false;
  for (const value of headingCandidates) {
    const roleHeading = legalPage.getByRole("heading", { name: new RegExp(value, "i") }).first();
    const visibleByRole = await roleHeading.isVisible().catch(() => false);
    if (visibleByRole) {
      await expect(roleHeading).toBeVisible({ timeout: 10000 });
      headingVisible = true;
      break;
    }

    const textHeading = legalPage.getByText(new RegExp(value, "i")).first();
    const visibleByText = await textHeading.isVisible().catch(() => false);
    if (visibleByText) {
      await expect(textHeading).toBeVisible({ timeout: 10000 });
      headingVisible = true;
      break;
    }
  }

  if (!headingVisible) {
    throw new Error("No se encontro el encabezado esperado en la pagina legal.");
  }

  const pageBody = normalizeText((await legalPage.locator("body").innerText()).slice(0, 4000));
  if (pageBody.length < 80) {
    throw new Error("No se detecto contenido legal suficiente en la pagina.");
  }

  await capture(legalPage, stepKey, report);
  step.finalUrl = legalPage.url();
  step.usedNewTab = usedNewTab;

  if (usedNewTab) {
    await legalPage.close();
    await page.bringToFront();
    await waitForStableUI(page);
  }
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  const report = {
    testName: "saleads_mi_negocio_full_test",
    timestamp: new Date().toISOString(),
    environment: {
      baseURL: test.info().project.use.baseURL || null,
      currentURLAtStart: page.url() || null,
    },
    steps: {
      Login: { status: "PENDING", details: "" },
      "Mi Negocio menu": { status: "PENDING", details: "" },
      "Agregar Negocio modal": { status: "PENDING", details: "" },
      "Administrar Negocios view": { status: "PENDING", details: "" },
      "Información General": { status: "PENDING", details: "" },
      "Detalles de la Cuenta": { status: "PENDING", details: "" },
      "Tus Negocios": { status: "PENDING", details: "" },
      "Términos y Condiciones": { status: "PENDING", details: "", finalUrl: null, usedNewTab: null },
      "Política de Privacidad": { status: "PENDING", details: "", finalUrl: null, usedNewTab: null },
    },
    screenshots: [],
    finalStatus: "PENDING",
  };

  await test.step("Step 1 - Login with Google", async () => {
    await runStep(report, "Login", async () => {
      const currentURL = page.url();
      if (!currentURL || currentURL === "about:blank") {
        const configuredURL = process.env.SALEADS_LOGIN_URL || test.info().project.use.baseURL;
        if (!configuredURL) {
          throw new Error(
            "No hay URL de login disponible. Provee SALEADS_LOGIN_URL o baseURL antes de ejecutar."
          );
        }
        await page.goto(configuredURL, { waitUntil: "domcontentloaded" });
      }

      await waitForStableUI(page);

      const popupPromise = page.waitForEvent("popup", { timeout: 5000 }).catch(() => null);
      const clicked = await clickFirstVisible(page, [
        (p) => p.getByRole("button", { name: /sign in with google/i }),
        (p) => p.getByRole("button", { name: /iniciar sesion con google/i }),
        (p) => p.getByText(/sign in with google/i),
        (p) => p.getByText(/iniciar sesion con google/i),
        (p) => p.getByRole("button", { name: /google/i }),
      ]);

      if (clicked) {
        const popup = await popupPromise;
        const loginPage = popup || page;
        if (popup) {
          await waitForStableUI(loginPage);
        }

        const accountChoice = loginPage.getByText(ACCOUNT_EMAIL, { exact: false }).first();
        const accountVisible = await accountChoice.isVisible({ timeout: 10000 }).catch(() => false);
        if (accountVisible) {
          await accountChoice.click();
          await waitForStableUI(loginPage);
        }

        if (popup) {
          await page.bringToFront();
          await waitForStableUI(page);
        }
      }

      await assertAnyVisible(
        page,
        [
          (p) => p.locator("aside"),
          (p) => p.getByText(/mi negocio|negocio/i),
          (p) => p.getByRole("navigation").first(),
        ],
        "No se detecto la interfaz principal con barra lateral despues del login."
      );

      await capture(page, "dashboard-loaded", report);
    });
  });

  const loginPassed = report.steps.Login.status === "PASS";

  await test.step("Step 2 - Open Mi Negocio menu", async () => {
    await runStep(report, "Mi Negocio menu", async () => {
      if (!loginPassed) {
        throw new Error("Omitido porque el login no fue exitoso.");
      }

      const clickedNegocio = await clickFirstVisible(page, [
        (p) => p.getByRole("link", { name: /^negocio$/i }),
        (p) => p.getByRole("button", { name: /^negocio$/i }),
        (p) => p.getByText(/^negocio$/i),
      ]);

      if (!clickedNegocio) {
        await clickFirstVisible(page, [
          (p) => p.getByText(/mi negocio/i),
          (p) => p.getByRole("link", { name: /mi negocio/i }),
          (p) => p.getByRole("button", { name: /mi negocio/i }),
        ]);
      }

      const openedMiNegocio = await clickFirstVisible(page, [
        (p) => p.getByRole("link", { name: /mi negocio/i }),
        (p) => p.getByRole("button", { name: /mi negocio/i }),
        (p) => p.getByText(/mi negocio/i),
      ]);

      if (!openedMiNegocio) {
        throw new Error("No se pudo abrir la opcion 'Mi Negocio'.");
      }

      await assertAnyVisible(
        page,
        [(p) => p.getByText(/agregar negocio/i), (p) => p.getByRole("link", { name: /agregar negocio/i })],
        "No se encontro 'Agregar Negocio' en el submenu."
      );

      await assertAnyVisible(
        page,
        [
          (p) => p.getByText(/administrar negocios/i),
          (p) => p.getByRole("link", { name: /administrar negocios/i }),
        ],
        "No se encontro 'Administrar Negocios' en el submenu."
      );

      await capture(page, "mi-negocio-menu-expanded", report);
    });
  });

  const miNegocioPassed = report.steps["Mi Negocio menu"].status === "PASS";

  await test.step("Step 3 - Validate Agregar Negocio modal", async () => {
    await runStep(report, "Agregar Negocio modal", async () => {
      if (!miNegocioPassed) {
        throw new Error("Omitido porque el menu Mi Negocio no quedo validado.");
      }

      const clicked = await clickFirstVisible(page, [
        (p) => p.getByRole("link", { name: /agregar negocio/i }),
        (p) => p.getByRole("button", { name: /agregar negocio/i }),
        (p) => p.getByText(/agregar negocio/i),
      ]);

      if (!clicked) {
        throw new Error("No se pudo hacer click en 'Agregar Negocio'.");
      }

      const modalTitle = page.getByText(/crear nuevo negocio/i).first();
      await expect(modalTitle).toBeVisible({ timeout: 10000 });
      await expect(page.getByLabel(/nombre del negocio/i)).toBeVisible({ timeout: 10000 });
      await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible({ timeout: 10000 });
      await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible({ timeout: 10000 });
      await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible({ timeout: 10000 });

      const nameField = page.getByLabel(/nombre del negocio/i).first();
      await nameField.click();
      await nameField.fill("Negocio Prueba Automatizacion");
      await waitForStableUI(page);

      await capture(page, "agregar-negocio-modal", report);

      await page.getByRole("button", { name: /cancelar/i }).click();
      await waitForStableUI(page);
      await expect(modalTitle).not.toBeVisible({ timeout: 10000 });
    });
  });

  await test.step("Step 4 - Open Administrar Negocios", async () => {
    await runStep(report, "Administrar Negocios view", async () => {
      if (!miNegocioPassed) {
        throw new Error("Omitido porque el menu Mi Negocio no quedo validado.");
      }

      const administrarVisible = await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false);
      if (!administrarVisible) {
        const reopened = await clickFirstVisible(page, [
          (p) => p.getByRole("link", { name: /mi negocio/i }),
          (p) => p.getByRole("button", { name: /mi negocio/i }),
          (p) => p.getByText(/mi negocio/i),
        ]);
        if (!reopened) {
          throw new Error("No fue posible reabrir 'Mi Negocio' antes de entrar a Administrar Negocios.");
        }
      }

      const clicked = await clickFirstVisible(page, [
        (p) => p.getByRole("link", { name: /administrar negocios/i }),
        (p) => p.getByRole("button", { name: /administrar negocios/i }),
        (p) => p.getByText(/administrar negocios/i),
      ]);
      if (!clicked) {
        throw new Error("No se pudo abrir 'Administrar Negocios'.");
      }

      await expect(page.getByText(/informacion general|información general/i)).toBeVisible({ timeout: 10000 });
      await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible({ timeout: 10000 });
      await expect(page.getByText(/tus negocios/i)).toBeVisible({ timeout: 10000 });
      await expect(page.getByText(/seccion legal|sección legal/i)).toBeVisible({ timeout: 10000 });

      await capture(page, "administrar-negocios-view", report);
    });
  });

  const administrarPassed = report.steps["Administrar Negocios view"].status === "PASS";

  await test.step("Step 5 - Validate Informacion General", async () => {
    await runStep(report, "Información General", async () => {
      if (!administrarPassed) {
        throw new Error("Omitido porque 'Administrar Negocios' no se valido.");
      }

      await expect(page.getByText(new RegExp(ACCOUNT_EMAIL, "i"))).toBeVisible({ timeout: 10000 });
      await expect(page.getByText(/business plan/i)).toBeVisible({ timeout: 10000 });
      await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible({ timeout: 10000 });

      const bodyText = await page.locator("body").innerText();
      const bodyTextNormalized = normalizeText(bodyText);
      if (!bodyTextNormalized.includes(normalizeText(ACCOUNT_EMAIL))) {
        throw new Error("No se encontro el correo del usuario en Informacion General.");
      }

      // Validate that user name is visible by using nearby content around the email line.
      const emailRegex = new RegExp(`([\\s\\S]{0,200})${escapeRegExp(ACCOUNT_EMAIL)}([\\s\\S]{0,50})`, "i");
      const emailContextMatch = bodyText.match(emailRegex);
      const emailContext = normalizeText(emailContextMatch ? emailContextMatch[1] : "");
      const hasNameNearEmail = /\b[a-z]{2,}\s+[a-z]{2,}\b/i.test(emailContext);
      const hasNombreLabel = /nombre\s*(de(l)?\s*usuario)?/i.test(bodyText);

      if (!hasNameNearEmail && !hasNombreLabel) {
        throw new Error(
          "No se pudo confirmar visualmente el nombre del usuario (solo se detecto email/plan)."
        );
      }
    });
  });

  await test.step("Step 6 - Validate Detalles de la Cuenta", async () => {
    await runStep(report, "Detalles de la Cuenta", async () => {
      if (!administrarPassed) {
        throw new Error("Omitido porque 'Administrar Negocios' no se valido.");
      }

      await expect(page.getByText(/cuenta creada/i)).toBeVisible({ timeout: 10000 });
      await expect(page.getByText(/estado activo/i)).toBeVisible({ timeout: 10000 });
      await expect(page.getByText(/idioma seleccionado/i)).toBeVisible({ timeout: 10000 });
    });
  });

  await test.step("Step 7 - Validate Tus Negocios", async () => {
    await runStep(report, "Tus Negocios", async () => {
      if (!administrarPassed) {
        throw new Error("Omitido porque 'Administrar Negocios' no se valido.");
      }

      await expect(page.getByText(/tus negocios/i)).toBeVisible({ timeout: 10000 });
      await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible({ timeout: 10000 });

      await assertAnyVisible(
        page,
        [
          (p) => p.getByRole("button", { name: /agregar negocio/i }),
          (p) => p.getByRole("link", { name: /agregar negocio/i }),
          (p) => p.getByText(/agregar negocio/i),
        ],
        "No se encontro el boton 'Agregar Negocio' en Tus Negocios."
      );
    });
  });

  await test.step("Step 8 - Validate Terminos y Condiciones", async () => {
    await runStep(report, "Términos y Condiciones", async () => {
      if (!administrarPassed) {
        throw new Error("Omitido porque 'Administrar Negocios' no se valido.");
      }

      await clickLegalLinkAndValidate({
        page,
        linkCandidates: [
          (p) => p.getByRole("link", { name: /terminos y condiciones|términos y condiciones/i }),
          (p) => p.getByText(/terminos y condiciones|términos y condiciones/i),
        ],
        headingCandidates: ["Terminos y Condiciones", "Términos y Condiciones"],
        report,
        stepKey: "Términos y Condiciones",
      });
    });
  });

  await test.step("Step 9 - Validate Politica de Privacidad", async () => {
    await runStep(report, "Política de Privacidad", async () => {
      if (!administrarPassed) {
        throw new Error("Omitido porque 'Administrar Negocios' no se valido.");
      }

      await clickLegalLinkAndValidate({
        page,
        linkCandidates: [
          (p) => p.getByRole("link", { name: /politica de privacidad|política de privacidad/i }),
          (p) => p.getByText(/politica de privacidad|política de privacidad/i),
        ],
        headingCandidates: ["Politica de Privacidad", "Política de Privacidad"],
        report,
        stepKey: "Política de Privacidad",
      });
    });
  });

  const statuses = Object.values(report.steps).map((step) => step.status);
  report.finalStatus = statuses.every((status) => status === "PASS") ? "PASS" : "FAIL";
  const reportPath = await writeReport(report);

  await test.info().attach("saleads-mi-negocio-report", {
    path: reportPath,
    contentType: "application/json",
  });

  expect(
    report.finalStatus,
    `Resultado final FAIL. Reporte: ${reportPath}\n${JSON.stringify(report.steps, null, 2)}`
  ).toBe("PASS");
});
