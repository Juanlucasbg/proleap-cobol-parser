const { test, expect } = require("@playwright/test");

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

function initReport() {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
}

function slugify(value) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
}

async function waitForUiToSettle(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(400);
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible();
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click();
  await waitForUiToSettle(page);
}

async function waitFirstVisible(candidates, timeout = 5000, context = "locator") {
  for (const candidate of candidates) {
    const locator = candidate.first();
    try {
      await locator.waitFor({ state: "visible", timeout });
      return locator;
    } catch (error) {
      // Try next candidate.
    }
  }
  throw new Error(`No visible ${context} found.`);
}

async function attachCheckpoint(page, testInfo, name, fullPage = false) {
  const filePath = testInfo.outputPath(`${Date.now()}-${slugify(name)}.png`);
  await page.screenshot({ path: filePath, fullPage });
  await testInfo.attach(name, {
    path: filePath,
    contentType: "image/png"
  });
}

async function captureLegalPageAndReturn(appPage, linkRegex, headingRegex, checkpointName) {
  const context = appPage.context();
  const link = await waitFirstVisible(
    [
      appPage.getByRole("link", { name: linkRegex }),
      appPage.getByText(linkRegex)
    ],
    7000,
    `legal link ${linkRegex}`
  );

  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await clickAndWait(appPage, link);

  let targetPage = await popupPromise;
  if (!targetPage) {
    targetPage = appPage;
  } else {
    await targetPage.waitForLoadState("domcontentloaded");
    await targetPage.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  }

  await waitFirstVisible(
    [
      targetPage.getByRole("heading", { name: headingRegex }),
      targetPage.getByText(headingRegex)
    ],
    10000,
    `legal heading ${headingRegex}`
  );

  const legalText = (await targetPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
  if (legalText.length < 150) {
    throw new Error(`Legal content looks too short for ${checkpointName}.`);
  }

  const finalUrl = targetPage.url();

  if (targetPage !== appPage) {
    await targetPage.bringToFront();
  }

  return { finalUrl, targetPage };
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = initReport();
  const failures = [];
  const legalUrls = {};
  let appPage = page;

  async function runSection(field, sectionFn) {
    try {
      await sectionFn();
      report[field] = "PASS";
    } catch (error) {
      report[field] = "FAIL";
      failures.push(`${field}: ${error.message}`);
    }
  }

  await runSection("Login", async () => {
    const startUrl = process.env.SALEADS_URL || process.env.TARGET_URL || process.env.BASE_URL;
    if (page.url() === "about:blank") {
      if (!startUrl) {
        throw new Error(
          "Set SALEADS_URL (or TARGET_URL / BASE_URL) to the SaleADS login page for the current environment."
        );
      }
      await page.goto(startUrl, { waitUntil: "domcontentloaded" });
    }

    await waitForUiToSettle(page);

    const loginButton = await waitFirstVisible(
      [
        page.getByRole("button", {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google/i
        }),
        page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i),
        page.getByRole("button", { name: /iniciar sesi[oó]n|sign in|login|entrar/i }),
        page.getByRole("link", { name: /iniciar sesi[oó]n|sign in|login|entrar/i })
      ],
      15000,
      "login button"
    );

    const popupPromise = page.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickAndWait(page, loginButton);

    const popup = await popupPromise;
    const accountRegex = /juanlucasbarbiergarzon@gmail\.com/i;

    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      const googleAccount = await waitFirstVisible(
        [
          popup.getByText(accountRegex),
          popup.getByRole("button", { name: accountRegex }),
          popup.getByRole("link", { name: accountRegex })
        ],
        6000,
        "Google account option"
      ).catch(() => null);

      if (googleAccount) {
        await googleAccount.click();
      }

      await popup.waitForEvent("close", { timeout: 30000 }).catch(() => {});
      await page.bringToFront();
    } else {
      const inPageAccount = await waitFirstVisible(
        [
          page.getByText(accountRegex),
          page.getByRole("button", { name: accountRegex }),
          page.getByRole("link", { name: accountRegex })
        ],
        5000,
        "Google account option in same page"
      ).catch(() => null);

      if (inPageAccount) {
        await clickAndWait(page, inPageAccount);
      }
    }

    await waitForUiToSettle(page);

    await waitFirstVisible(
      [
        page.getByRole("navigation"),
        page.locator("aside"),
        page.locator("nav")
      ],
      15000,
      "main navigation"
    );

    await expect(page.getByText(/negocio/i).first()).toBeVisible();
    await attachCheckpoint(page, testInfo, "dashboard-loaded", false);
  });

  await runSection("Mi Negocio menu", async () => {
    const negocioLabel = await waitFirstVisible(
      [
        page.getByText(/^Negocio$/i),
        page.getByRole("button", { name: /^Negocio$/i }),
        page.getByRole("link", { name: /^Negocio$/i }),
        page.getByText(/negocio/i)
      ],
      10000,
      "Negocio section"
    );
    await expect(negocioLabel).toBeVisible();

    const miNegocioOption = await waitFirstVisible(
      [
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i)
      ],
      10000,
      "Mi Negocio option"
    );
    await clickAndWait(page, miNegocioOption);

    await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
    await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();
    await attachCheckpoint(page, testInfo, "mi-negocio-menu-expanded", false);
  });

  await runSection("Agregar Negocio modal", async () => {
    const addBusinessOption = await waitFirstVisible(
      [
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByRole("link", { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i)
      ],
      10000,
      "Agregar Negocio option"
    );
    await clickAndWait(page, addBusinessOption);

    const modal = await waitFirstVisible(
      [
        page.getByRole("dialog"),
        page.locator("[role='dialog']"),
        page.locator(".modal")
      ],
      10000,
      "Crear Nuevo Negocio modal"
    );

    await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();

    const businessNameInput = await waitFirstVisible(
      [
        modal.getByLabel(/nombre del negocio/i),
        modal.getByPlaceholder(/nombre del negocio/i),
        modal.locator("input[type='text']")
      ],
      5000,
      "Nombre del Negocio input"
    );

    await expect(modal.getByText(/tienes\s+2\s+de\s+3\s+negocios/i)).toBeVisible();
    await expect(modal.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /crear negocio/i })).toBeVisible();

    await attachCheckpoint(page, testInfo, "agregar-negocio-modal", false);

    await businessNameInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page, modal.getByRole("button", { name: /cancelar/i }));
    await modal.waitFor({ state: "hidden", timeout: 10000 }).catch(() => {});
  });

  await runSection("Administrar Negocios view", async () => {
    const adminVisible = await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false);
    if (!adminVisible) {
      const miNegocioOption = await waitFirstVisible(
        [
          page.getByRole("button", { name: /mi negocio/i }),
          page.getByRole("link", { name: /mi negocio/i }),
          page.getByText(/mi negocio/i)
        ],
        10000,
        "Mi Negocio re-open option"
      );
      await clickAndWait(page, miNegocioOption);
    }

    const adminOption = await waitFirstVisible(
      [
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i)
      ],
      10000,
      "Administrar Negocios option"
    );
    await clickAndWait(page, adminOption);
    await waitForUiToSettle(page);

    await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible();
    await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
    await expect(page.getByText(/secci[oó]n legal/i).first()).toBeVisible();

    await attachCheckpoint(page, testInfo, "administrar-negocios-account-page", true);
  });

  await runSection("Información General", async () => {
    await expect(page.getByText(/business plan/i).first()).toBeVisible();
    await expect(page.getByRole("button", { name: /cambiar plan/i }).first()).toBeVisible();
    await expect(page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i).first()).toBeVisible();

    const nameCandidate = await waitFirstVisible(
      [
        page.getByText(/juan|lucas|barbier|garzon/i),
        page.getByText(/[a-záéíóúñ]{3,}\s+[a-záéíóúñ]{3,}/i)
      ],
      10000,
      "user name"
    );
    await expect(nameCandidate).toBeVisible();
  });

  await runSection("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
    await expect(page.getByText(/estado activo/i).first()).toBeVisible();
    await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible();
  });

  await runSection("Tus Negocios", async () => {
    await expect(page.getByText(/tus negocios/i).first()).toBeVisible();

    const addBusinessButton = await waitFirstVisible(
      [
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByRole("link", { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i)
      ],
      10000,
      "Agregar Negocio button in list"
    );
    await expect(addBusinessButton).toBeVisible();

    await expect(page.getByText(/tienes\s+2\s+de\s+3\s+negocios/i).first()).toBeVisible();

    const businessContainer = page.locator("section,article,div").filter({
      has: page.getByText(/tus negocios/i)
    }).first();
    const businessText = (await businessContainer.innerText().catch(() => "")).trim();
    if (businessText.length < 20) {
      throw new Error("Business list container is empty or not visible enough.");
    }
  });

  await runSection("Términos y Condiciones", async () => {
    const legalResult = await captureLegalPageAndReturn(
      appPage,
      /t[ée]rminos y condiciones/i,
      /t[ée]rminos y condiciones/i,
      "terminos-y-condiciones"
    );

    legalUrls.termsAndConditions = legalResult.finalUrl;
    await attachCheckpoint(legalResult.targetPage, testInfo, "terminos-y-condiciones-page", true);

    if (legalResult.targetPage !== appPage) {
      await legalResult.targetPage.close();
      await appPage.bringToFront();
    } else {
      await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
      await waitForUiToSettle(appPage);
    }
  });

  await runSection("Política de Privacidad", async () => {
    const legalResult = await captureLegalPageAndReturn(
      appPage,
      /pol[ií]tica de privacidad/i,
      /pol[ií]tica de privacidad/i,
      "politica-de-privacidad"
    );

    legalUrls.privacyPolicy = legalResult.finalUrl;
    await attachCheckpoint(legalResult.targetPage, testInfo, "politica-de-privacidad-page", true);

    if (legalResult.targetPage !== appPage) {
      await legalResult.targetPage.close();
      await appPage.bringToFront();
    } else {
      await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
      await waitForUiToSettle(appPage);
    }
  });

  const finalReport = {
    reportName: "saleads_mi_negocio_full_test",
    report,
    legalUrls,
    failures
  };

  await testInfo.attach("mi-negocio-final-report", {
    body: Buffer.from(JSON.stringify(finalReport, null, 2), "utf-8"),
    contentType: "application/json"
  });

  console.log("FINAL_REPORT_JSON");
  console.log(JSON.stringify(finalReport, null, 2));

  if (failures.length > 0) {
    throw new Error(`One or more validations failed:\n- ${failures.join("\n- ")}`);
  }
});
