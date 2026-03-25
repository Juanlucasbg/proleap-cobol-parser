const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const REPORT_FIELDS = [
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

const ARTIFACTS_DIR = path.resolve(__dirname, "..", "artifacts");
const SCREENSHOTS_DIR = path.join(ARTIFACTS_DIR, "screenshots");
const REPORT_PATH = path.join(ARTIFACTS_DIR, "saleads_mi_negocio_full_test_report.json");

function ensureArtifactDirs() {
  fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForTimeout(800);
}

async function firstVisible(locators, timeoutMs = 7000) {
  for (const locator of locators) {
    const first = locator.first();
    const visible = await first.isVisible({ timeout: timeoutMs }).catch(() => false);
    if (visible) {
      return first;
    }
  }
  return null;
}

async function clickAndWait(locator, page) {
  await locator.click();
  await waitForUi(page);
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  ensureArtifactDirs();

  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, { status: "FAIL" }]));
  const errors = [];
  const legalUrls = {};

  async function checkpoint(name, currentPage = page, fullPage = false) {
    await currentPage.screenshot({
      path: path.join(SCREENSHOTS_DIR, `${name}.png`),
      fullPage
    });
  }

  async function runStep(field, fn) {
    try {
      await fn();
      report[field] = { status: "PASS" };
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      report[field] = { status: "FAIL", error: message };
      errors.push(`${field}: ${message}`);
      await checkpoint(`${field.replace(/\s+/g, "_").toLowerCase()}_failure`).catch(() => {});
    }
  }

  async function ensureOnLoginPageOrGoToProvidedUrl() {
    const targetUrl = process.env.SALEADS_URL;
    if (targetUrl) {
      await page.goto(targetUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
      return;
    }

    if (page.url() === "about:blank") {
      throw new Error(
        "No SaleADS page is loaded. Provide SALEADS_URL or start the test from an already-open login page context."
      );
    }
  }

  async function openAndValidateLegalPage(linkText, headingRegex, screenshotName) {
    const legalLink = await firstVisible([
      page.getByRole("link", { name: new RegExp(linkText, "i") }),
      page.getByRole("button", { name: new RegExp(linkText, "i") }),
      page.getByText(new RegExp(linkText, "i"))
    ]);

    if (!legalLink) {
      throw new Error(`Could not find legal link: ${linkText}`);
    }

    const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
    await legalLink.click();

    const popup = await popupPromise;
    const targetPage = popup || page;
    await waitForUi(targetPage);

    const heading = await firstVisible(
      [
        targetPage.getByRole("heading", { name: headingRegex }),
        targetPage.getByText(headingRegex)
      ],
      15000
    );

    if (!heading) {
      throw new Error(`Missing expected heading for ${linkText}`);
    }

    const bodyText = (await targetPage.locator("body").innerText()).trim();
    if (bodyText.length < 120) {
      throw new Error(`Legal content appears too short for ${linkText}`);
    }

    legalUrls[linkText] = targetPage.url();
    await checkpoint(screenshotName, targetPage, true);

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await page.goBack().catch(() => {});
      await waitForUi(page);
    }
  }

  await runStep("Login", async () => {
    await ensureOnLoginPageOrGoToProvidedUrl();

    const googleLoginTrigger = await firstVisible([
      page.getByRole("button", { name: /google/i }),
      page.getByRole("link", { name: /google/i }),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i)
    ]);

    if (!googleLoginTrigger) {
      throw new Error("Google login button/link not found.");
    }

    const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
    await clickAndWait(googleLoginTrigger, page);
    const popup = await popupPromise;
    const authPage = popup || page;

    const accountOption = await firstVisible(
      [
        authPage.getByText("juanlucasbarbiergarzon@gmail.com"),
        authPage.getByRole("button", { name: /juanlucasbarbiergarzon@gmail\.com/i }),
        authPage.getByRole("link", { name: /juanlucasbarbiergarzon@gmail\.com/i })
      ],
      5000
    );

    if (accountOption) {
      await clickAndWait(accountOption, authPage);
    }

    if (popup) {
      await popup.waitForEvent("close", { timeout: 60000 }).catch(() => {});
      await page.bringToFront();
    }

    await waitForUi(page);

    const sidebar = await firstVisible([
      page.locator("aside"),
      page.getByRole("navigation"),
      page.locator("nav")
    ]);

    if (!sidebar) {
      throw new Error("Main interface loaded but sidebar/navigation was not found.");
    }

    const negocioLabel = await firstVisible([
      page.getByText(/negocio/i),
      sidebar.getByText(/negocio/i)
    ]);
    if (!negocioLabel) {
      throw new Error("Sidebar is visible but Negocio option was not found.");
    }

    await checkpoint("step_1_dashboard_loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    const negocio = await firstVisible([
      page.getByRole("button", { name: /^negocio$/i }),
      page.getByRole("link", { name: /^negocio$/i }),
      page.getByText(/^negocio$/i)
    ]);
    if (!negocio) {
      throw new Error("Negocio section was not found in sidebar.");
    }

    await clickAndWait(negocio, page);

    const miNegocio = await firstVisible([
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i)
    ]);
    if (!miNegocio) {
      throw new Error("Mi Negocio option was not found.");
    }

    await clickAndWait(miNegocio, page);

    await expect(page.getByText(/agregar negocio/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/administrar negocios/i)).toBeVisible({ timeout: 15000 });

    await checkpoint("step_2_mi_negocio_expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await firstVisible([
      page.getByRole("button", { name: /^agregar negocio$/i }),
      page.getByRole("link", { name: /^agregar negocio$/i }),
      page.getByText(/^agregar negocio$/i)
    ]);
    if (!agregarNegocio) {
      throw new Error("Agregar Negocio option was not found.");
    }

    await clickAndWait(agregarNegocio, page);

    await expect(page.getByText(/crear nuevo negocio/i)).toBeVisible({ timeout: 15000 });
    const businessNameInput = await firstVisible([
      page.getByRole("textbox", { name: /nombre del negocio/i }),
      page.getByPlaceholder(/nombre del negocio/i),
      page.locator("input[name*='nombre' i], input[id*='nombre' i]")
    ]);
    if (!businessNameInput) {
      throw new Error("Nombre del Negocio input was not found in modal.");
    }
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible({ timeout: 15000 });

    await businessNameInput.fill("Negocio Prueba Automatización");
    await checkpoint("step_3_agregar_negocio_modal");

    await clickAndWait(page.getByRole("button", { name: /cancelar/i }), page);
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarNegocios = await firstVisible([
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i)
    ]);

    if (!administrarNegocios) {
      const miNegocioToggle = await firstVisible([
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i)
      ]);
      if (miNegocioToggle) {
        await clickAndWait(miNegocioToggle, page);
      }
    }

    const administrarNegociosAfterExpand = administrarNegocios
      || (await firstVisible([
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i)
      ]));

    if (!administrarNegociosAfterExpand) {
      throw new Error("Administrar Negocios option was not found.");
    }

    await clickAndWait(administrarNegociosAfterExpand, page);

    await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/tus negocios/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/secci[oó]n legal/i)).toBeVisible({ timeout: 20000 });

    await checkpoint("step_4_administrar_negocios_view", page, true);
  });

  await runStep("Información General", async () => {
    await expect(page.getByText(/informaci[oó]n general/i)).toBeVisible({ timeout: 15000 });

    const possibleUserName = await firstVisible([
      page.getByText(/nombre( del usuario)?/i),
      page.getByText(/usuario/i),
      page.locator("text=/[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}/")
    ]);
    if (!possibleUserName) {
      throw new Error("User name was not detected in Información General.");
    }

    const email = page.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/").first();
    await expect(email).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/business plan/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible({ timeout: 15000 });
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/detalles de la cuenta/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/cuenta creada/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/estado activo/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/idioma seleccionado/i)).toBeVisible({ timeout: 15000 });
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/tus negocios/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /agregar negocio/i })).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)).toBeVisible({ timeout: 15000 });
  });

  await runStep("Términos y Condiciones", async () => {
    await openAndValidateLegalPage(
      "Términos y Condiciones",
      /t[eé]rminos y condiciones/i,
      "step_8_terminos_y_condiciones"
    );
  });

  await runStep("Política de Privacidad", async () => {
    await openAndValidateLegalPage(
      "Política de Privacidad",
      /pol[ií]tica de privacidad/i,
      "step_9_politica_de_privacidad"
    );
  });

  const summary = {};
  for (const key of REPORT_FIELDS) {
    summary[key] = report[key]?.status || "FAIL";
  }

  fs.writeFileSync(
    REPORT_PATH,
    JSON.stringify(
      {
        testName: "saleads_mi_negocio_full_test",
        generatedAt: new Date().toISOString(),
        startUrl: process.env.SALEADS_URL || "provided-by-existing-browser-context",
        finalAppUrl: page.url(),
        legalUrls,
        summary,
        details: report
      },
      null,
      2
    )
  );

  console.log("Final PASS/FAIL report:", JSON.stringify(summary, null, 2));

  if (errors.length > 0) {
    throw new Error(`Workflow validation failed:\n- ${errors.join("\n- ")}`);
  }
});
