const { test, expect } = require("@playwright/test");
const fs = require("fs/promises");
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

function createInitialReport() {
  return Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: "FAIL", details: "Not executed" }])
  );
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(800);
}

async function findFirstVisible(candidates) {
  for (const candidate of candidates) {
    const locator = candidate.first();
    const visible = await locator.isVisible().catch(() => false);
    if (visible) {
      return locator;
    }
  }
  return null;
}

async function clickByVisibleText(page, patterns) {
  const candidates = [];
  for (const pattern of patterns) {
    candidates.push(
      page.getByRole("button", { name: pattern }),
      page.getByRole("link", { name: pattern }),
      page.getByRole("menuitem", { name: pattern }),
      page.getByText(pattern)
    );
  }

  const locator = await findFirstVisible(candidates);
  if (!locator) {
    return null;
  }

  await locator.click();
  return locator;
}

async function takeCheckpoint(page, checkpointDir, name, fullPage = false) {
  const safeName = name.replace(/[^a-zA-Z0-9-_]/g, "_");
  const filePath = path.join(checkpointDir, `${Date.now()}-${safeName}.png`);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function expectVisibleByText(page, pattern, timeout = 20000) {
  const locator = await findFirstVisible([
    page.getByRole("heading", { name: pattern }),
    page.getByRole("button", { name: pattern }),
    page.getByRole("link", { name: pattern }),
    page.getByText(pattern)
  ]);

  if (!locator) {
    throw new Error(`Expected visible text not found: ${pattern}`);
  }

  await expect(locator).toBeVisible({ timeout });
  return locator;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = createInitialReport();
  const failures = [];
  const evidence = {};
  const legalUrls = {};
  const checkpointDir = testInfo.outputPath("checkpoints");
  await fs.mkdir(checkpointDir, { recursive: true });

  const setResult = (field, status, details) => {
    report[field] = { status, details };
  };

  const runStep = async (field, step) => {
    try {
      await step();
      setResult(field, "PASS", "Validated successfully");
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      setResult(field, "FAIL", message);
      failures.push(field);
    }
  };

  const loginUrl =
    process.env.SALEADS_LOGIN_URL ||
    process.env.SALEADS_BASE_URL ||
    process.env.BASE_URL ||
    "";

  await runStep("Login", async () => {
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    }

    await waitForUi(page);

    const loginButton = await findFirstVisible([
      page.getByRole("button", { name: /sign in with google/i }),
      page.getByRole("button", { name: /iniciar sesi[oó]n con google/i }),
      page.getByRole("button", { name: /continuar con google/i }),
      page.getByRole("button", { name: /google/i }),
      page.getByText(/sign in with google/i),
      page.getByText(/iniciar sesi[oó]n con google/i)
    ]);

    if (!loginButton) {
      throw new Error("Google login button was not found on the login page.");
    }

    const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
    await loginButton.click();

    const googlePopup = await popupPromise;
    if (googlePopup) {
      await googlePopup.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => {});

      const accountOption = await findFirstVisible([
        googlePopup.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true }),
        googlePopup.getByRole("button", { name: /juanlucasbarbiergarzon@gmail\.com/i }),
        googlePopup.getByRole("link", { name: /juanlucasbarbiergarzon@gmail\.com/i })
      ]);

      if (accountOption) {
        await accountOption.click();
      }
    }

    await waitForUi(page);

    const appShell = await findFirstVisible([
      page.locator("aside"),
      page.locator("nav"),
      page.getByText(/mi negocio|negocio/i)
    ]);
    if (!appShell) {
      throw new Error("Main application interface was not detected after login.");
    }

    const sidebar = await findFirstVisible([
      page.locator("aside").filter({ hasText: /negocio|mi negocio/i }),
      page.locator("nav").filter({ hasText: /negocio|mi negocio/i }),
      page.getByText(/negocio|mi negocio/i)
    ]);
    if (!sidebar) {
      throw new Error("Left sidebar navigation is not visible.");
    }

    evidence.dashboard = await takeCheckpoint(page, checkpointDir, "01-dashboard-loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    const menuTrigger =
      (await clickByVisibleText(page, [/^mi negocio$/i])) ||
      (await clickByVisibleText(page, [/^negocio$/i]));

    if (!menuTrigger) {
      throw new Error("Could not locate 'Mi Negocio' (or 'Negocio') menu in sidebar.");
    }

    await waitForUi(page);
    await expectVisibleByText(page, /agregar negocio/i);
    await expectVisibleByText(page, /administrar negocios/i);
    evidence.miNegocioMenu = await takeCheckpoint(page, checkpointDir, "02-mi-negocio-menu");
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocioOption = await clickByVisibleText(page, [/^agregar negocio$/i, /agregar negocio/i]);
    if (!agregarNegocioOption) {
      throw new Error("Could not click 'Agregar Negocio'.");
    }

    await waitForUi(page);
    await expectVisibleByText(page, /crear nuevo negocio/i);
    await expectVisibleByText(page, /nombre del negocio/i);
    await expectVisibleByText(page, /tienes\s*2\s*de\s*3\s*negocios/i);
    await expectVisibleByText(page, /^cancelar$/i);
    await expectVisibleByText(page, /crear negocio/i);

    evidence.agregarNegocioModal = await takeCheckpoint(page, checkpointDir, "03-agregar-negocio-modal");

    const nameInput = await findFirstVisible([
      page.getByLabel(/nombre del negocio/i),
      page.getByPlaceholder(/nombre del negocio/i),
      page.locator("input").filter({ hasText: /nombre del negocio/i }),
      page.locator("input[type='text']")
    ]);
    if (nameInput) {
      await nameInput.click();
      await nameInput.fill("Negocio Prueba Automatizacion");
    }

    const cancelButton = await findFirstVisible([
      page.getByRole("button", { name: /^cancelar$/i }),
      page.getByText(/^cancelar$/i)
    ]);
    if (cancelButton) {
      await cancelButton.click();
      await waitForUi(page);
    }
  });

  await runStep("Administrar Negocios view", async () => {
    const menuExpanded = await findFirstVisible([
      page.getByText(/administrar negocios/i),
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByRole("button", { name: /administrar negocios/i })
    ]);

    if (!menuExpanded) {
      await clickByVisibleText(page, [/^mi negocio$/i, /^negocio$/i]);
      await waitForUi(page);
    }

    const administrarNegocios = await clickByVisibleText(page, [/administrar negocios/i]);
    if (!administrarNegocios) {
      throw new Error("Could not click 'Administrar Negocios'.");
    }

    await waitForUi(page);
    await expectVisibleByText(page, /informaci[oó]n general/i);
    await expectVisibleByText(page, /detalles de la cuenta/i);
    await expectVisibleByText(page, /tus negocios/i);
    await expectVisibleByText(page, /secci[oó]n legal/i);

    evidence.administrarNegocios = await takeCheckpoint(
      page,
      checkpointDir,
      "04-administrar-negocios-page",
      true
    );
  });

  await runStep("Información General", async () => {
    const infoSection = await findFirstVisible([
      page.locator("section,div,article").filter({ hasText: /informaci[oó]n general/i }),
      page.getByText(/informaci[oó]n general/i)
    ]);
    if (!infoSection) {
      throw new Error("Section 'Información General' was not found.");
    }

    await expectVisibleByText(page, /business plan/i);
    await expectVisibleByText(page, /cambiar plan/i);

    const emailLocator = page.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
    await expect(emailLocator.first()).toBeVisible();

    const infoText = await infoSection.innerText();
    const lines = infoText
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean);

    const nameCandidate = lines.find(
      (line) =>
        !/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(line) &&
        !/informaci[oó]n general|business plan|cambiar plan/i.test(line)
    );

    if (!nameCandidate) {
      throw new Error("User name was not detected in 'Información General'.");
    }
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expectVisibleByText(page, /detalles de la cuenta/i);
    await expectVisibleByText(page, /cuenta creada/i);
    await expectVisibleByText(page, /estado activo/i);
    await expectVisibleByText(page, /idioma seleccionado/i);
  });

  await runStep("Tus Negocios", async () => {
    await expectVisibleByText(page, /tus negocios/i);
    await expectVisibleByText(page, /agregar negocio/i);
    await expectVisibleByText(page, /tienes\s*2\s*de\s*3\s*negocios/i);

    const businessList = await findFirstVisible([
      page.locator("section,div,article").filter({ hasText: /tus negocios/i }).locator("li, tr, .card, .business"),
      page.locator("li, tr, .card, .business")
    ]);
    if (!businessList) {
      throw new Error("Business list content was not detected.");
    }
  });

  const validateLegalLink = async (field, linkPattern, headingPattern, evidenceKey) => {
    await runStep(field, async () => {
      const legalLink = await findFirstVisible([
        page.getByRole("link", { name: linkPattern }),
        page.getByRole("button", { name: linkPattern }),
        page.getByText(linkPattern)
      ]);

      if (!legalLink) {
        throw new Error(`Could not find legal link: ${linkPattern}`);
      }

      const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
      const appUrlBefore = page.url();

      await legalLink.click();
      const popup = await popupPromise;
      const legalPage = popup || page;

      await legalPage.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => {});
      await legalPage.waitForTimeout(1200);

      await expectVisibleByText(legalPage, headingPattern);

      const legalText = (await legalPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
      if (legalText.length < 120) {
        throw new Error("Legal content text is too short or not visible.");
      }

      evidence[evidenceKey] = await takeCheckpoint(legalPage, checkpointDir, evidenceKey, true);
      legalUrls[field] = legalPage.url();

      if (popup) {
        await popup.close();
        await page.bringToFront();
      } else if (page.url() !== appUrlBefore) {
        await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
      }

      await waitForUi(page);
    });
  };

  await validateLegalLink(
    "Términos y Condiciones",
    /t[eé]rminos y condiciones/i,
    /t[eé]rminos y condiciones/i,
    "08-terminos-y-condiciones"
  );

  await validateLegalLink(
    "Política de Privacidad",
    /pol[ií]tica de privacidad/i,
    /pol[ií]tica de privacidad/i,
    "09-politica-de-privacidad"
  );

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    results: report,
    legalUrls,
    evidence
  };

  const reportPath = testInfo.outputPath("final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("final-report", { path: reportPath, contentType: "application/json" });

  console.log("FINAL_REPORT");
  console.log(JSON.stringify(finalReport, null, 2));

  expect(
    failures,
    failures.length ? `Validation failed for: ${failures.join(", ")}` : undefined
  ).toEqual([]);
});
