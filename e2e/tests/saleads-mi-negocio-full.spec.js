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
  "Política de Privacidad",
];

const DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function initReport() {
  return REPORT_FIELDS.reduce((acc, field) => {
    acc[field] = { status: "PENDING", details: "" };
    return acc;
  }, {});
}

function errorMessage(error) {
  if (!error) {
    return "Unknown error";
  }
  if (typeof error === "string") {
    return error;
  }
  return error.message || String(error);
}

async function clickAndWait(targetPage, locator) {
  await locator.click();
  await targetPage.waitForLoadState("domcontentloaded").catch(() => {});
  await targetPage.waitForTimeout(800);
}

async function waitForFirstVisible(candidates, timeoutMs = 15000) {
  const pollMs = 250;
  const attempts = Math.ceil(timeoutMs / pollMs);

  for (let i = 0; i < attempts; i += 1) {
    for (const candidate of candidates) {
      const visible = await candidate.locator.isVisible().catch(() => false);
      if (visible) {
        return candidate;
      }
    }
    await candidates[0].locator.page().waitForTimeout(pollMs);
  }

  const labels = candidates.map((candidate) => candidate.label).join(", ");
  throw new Error(`None of the candidate elements became visible: ${labels}`);
}

async function ensureContainsEmail(textSource) {
  const match = textSource.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);
  if (!match) {
    throw new Error("Expected a visible user email but none was found.");
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = initReport();
  const googleEmail = process.env.GOOGLE_ACCOUNT_EMAIL || DEFAULT_GOOGLE_EMAIL;
  let appPage = page;

  const setStatus = (field, status, details = "") => {
    report[field] = { status, details };
  };

  const screenshot = async (name, targetPage = appPage, fullPage = false) => {
    const shotPath = testInfo.outputPath(
      `${name.toLowerCase().replace(/[^a-z0-9]+/gi, "-")}.png`,
    );
    await targetPage.screenshot({ path: shotPath, fullPage });
    await testInfo.attach(name, { path: shotPath, contentType: "image/png" });
  };

  const guardedStep = async (field, dependencies, stepFn) => {
    const blockedBy = dependencies.filter(
      (dep) => report[dep] && report[dep].status !== "PASS",
    );
    if (blockedBy.length > 0) {
      setStatus(field, "FAIL", `Blocked by: ${blockedBy.join(", ")}`);
      return;
    }

    try {
      await stepFn();
      if (report[field].status === "PENDING") {
        setStatus(field, "PASS");
      }
    } catch (error) {
      setStatus(field, "FAIL", errorMessage(error));
    }
  };

  await guardedStep("Login", [], async () => {
    if (process.env.SALEADS_LOGIN_URL) {
      await page.goto(process.env.SALEADS_LOGIN_URL, {
        waitUntil: "domcontentloaded",
      });
    }

    const loginButton = await waitForFirstVisible(
      [
        {
          label: "Sign in with Google button",
          locator: page
            .getByRole("button", { name: /sign in with google|google/i })
            .first(),
        },
        {
          label: "Iniciar sesión con Google button",
          locator: page
            .getByRole("button", {
              name: /iniciar sesi[oó]n con google|continuar con google/i,
            })
            .first(),
        },
        {
          label: "Google text action",
          locator: page.getByText(/sign in with google|google/i).first(),
        },
      ],
      25000,
    );

    const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await clickAndWait(page, loginButton.locator);
    const popup = await popupPromise;

    const googlePage = popup || page;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
    }

    const accountCandidate = googlePage.getByText(googleEmail, { exact: true }).first();
    const accountVisible = await accountCandidate.isVisible().catch(() => false);
    if (accountVisible) {
      await clickAndWait(googlePage, accountCandidate);
    }

    await page.waitForLoadState("domcontentloaded");
    await page.waitForTimeout(1000);

    const mainInterface = await waitForFirstVisible(
      [
        { label: "Sidebar aside", locator: page.locator("aside").first() },
        {
          label: "Navigation landmark",
          locator: page.getByRole("navigation").first(),
        },
        {
          label: "Negocio sidebar label",
          locator: page.getByText(/negocio/i).first(),
        },
      ],
      45000,
    );

    expect(await mainInterface.locator.isVisible()).toBeTruthy();
    await screenshot("checkpoint-dashboard-loaded", page);
  });

  await guardedStep("Mi Negocio menu", ["Login"], async () => {
    await page.bringToFront();

    const negocioSection = await waitForFirstVisible(
      [
        { label: "Negocio menu label", locator: page.getByText(/^Negocio$/i).first() },
        {
          label: "Negocio text",
          locator: page.getByText(/negocio/i).first(),
        },
      ],
      15000,
    );
    expect(await negocioSection.locator.isVisible()).toBeTruthy();

    const miNegocioTrigger = await waitForFirstVisible(
      [
        {
          label: "Mi Negocio sidebar item (button)",
          locator: page.getByRole("button", { name: /mi negocio/i }).first(),
        },
        {
          label: "Mi Negocio sidebar item",
          locator: page.getByText(/^Mi Negocio$/i).first(),
        },
      ],
      15000,
    );
    await clickAndWait(page, miNegocioTrigger.locator);

    await expect(page.getByText(/^Agregar Negocio$/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/^Administrar Negocios$/i)).toBeVisible({ timeout: 15000 });
    await screenshot("checkpoint-mi-negocio-expanded", page);
  });

  await guardedStep("Agregar Negocio modal", ["Mi Negocio menu"], async () => {
    const addBusinessSidebar = await waitForFirstVisible(
      [
        {
          label: "Agregar Negocio submenu action",
          locator: page.getByText(/^Agregar Negocio$/i).first(),
        },
        {
          label: "Agregar Negocio button",
          locator: page.getByRole("button", { name: /^Agregar Negocio$/i }).first(),
        },
      ],
      15000,
    );

    await clickAndWait(page, addBusinessSidebar.locator);
    await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByLabel(/Nombre del Negocio/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible({
      timeout: 10000,
    });
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible({
      timeout: 10000,
    });
    await screenshot("checkpoint-crear-nuevo-negocio-modal", page);

    const nameInput = page.getByLabel(/Nombre del Negocio/i).first();
    await nameInput.click();
    await nameInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page, page.getByRole("button", { name: /^Cancelar$/i }).first());
  });

  await guardedStep("Administrar Negocios view", ["Mi Negocio menu"], async () => {
    const adminOption = page.getByText(/^Administrar Negocios$/i).first();
    const adminVisible = await adminOption.isVisible().catch(() => false);

    if (!adminVisible) {
      const miNegocioTrigger = await waitForFirstVisible(
        [
          {
            label: "Mi Negocio sidebar item (button)",
            locator: page.getByRole("button", { name: /mi negocio/i }).first(),
          },
          {
            label: "Mi Negocio sidebar item",
            locator: page.getByText(/^Mi Negocio$/i).first(),
          },
        ],
        15000,
      );
      await clickAndWait(page, miNegocioTrigger.locator);
    }

    await clickAndWait(page, page.getByText(/^Administrar Negocios$/i).first());
    await expect(page.getByText(/^Información General$/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/^Detalles de la Cuenta$/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/^Tus Negocios$/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/Secci[oó]n Legal/i)).toBeVisible({ timeout: 20000 });
    await screenshot("checkpoint-administrar-negocios", page, true);
  });

  await guardedStep("Información General", ["Administrar Negocios view"], async () => {
    const infoSection = page
      .locator("section, div")
      .filter({ hasText: /Información General/i })
      .first();
    await expect(infoSection).toBeVisible({ timeout: 15000 });

    const infoText = await infoSection.innerText();
    await ensureContainsEmail(infoText);
    expect(infoText).toMatch(/BUSINESS PLAN/i);
    await expect(page.getByRole("button", { name: /Cambiar Plan/i }).first()).toBeVisible({
      timeout: 15000,
    });

    // There can be UI variations, so this accepts either explicit "Nombre"
    // labels or a profile heading near account details.
    const hasNameLabel = /Nombre|Name/i.test(infoText);
    const profileHeadingVisible = await page
      .locator("h1, h2, h3")
      .filter({ hasText: /[A-Za-z]{2,}\s+[A-Za-z]{2,}/ })
      .first()
      .isVisible()
      .catch(() => false);

    expect(hasNameLabel || profileHeadingVisible).toBeTruthy();
  });

  await guardedStep("Detalles de la Cuenta", ["Administrar Negocios view"], async () => {
    const detailsSection = page
      .locator("section, div")
      .filter({ hasText: /Detalles de la Cuenta/i })
      .first();
    await expect(detailsSection).toBeVisible({ timeout: 15000 });
    await expect(detailsSection.getByText(/Cuenta creada/i)).toBeVisible({ timeout: 10000 });
    await expect(detailsSection.getByText(/Estado activo/i)).toBeVisible({ timeout: 10000 });
    await expect(detailsSection.getByText(/Idioma seleccionado/i)).toBeVisible({ timeout: 10000 });
  });

  await guardedStep("Tus Negocios", ["Administrar Negocios view"], async () => {
    const businessSection = page
      .locator("section, div")
      .filter({ hasText: /^Tus Negocios$/i })
      .first();
    await expect(businessSection).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i }).first()).toBeVisible({
      timeout: 10000,
    });
    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible({ timeout: 10000 });
  });

  await guardedStep("Términos y Condiciones", ["Administrar Negocios view"], async () => {
    const termsLink = await waitForFirstVisible(
      [
        {
          label: "Términos y Condiciones link",
          locator: page.getByRole("link", { name: /Términos y Condiciones/i }).first(),
        },
        {
          label: "Términos y Condiciones text",
          locator: page.getByText(/Términos y Condiciones/i).first(),
        },
      ],
      15000,
    );

    const newTabPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await clickAndWait(page, termsLink.locator);
    let legalPage = await newTabPromise;
    let openedInNewTab = true;
    if (!legalPage) {
      openedInNewTab = false;
      legalPage = page;
    } else {
      await legalPage.waitForLoadState("domcontentloaded");
    }

    await expect(
      legalPage.getByRole("heading", { name: /Términos y Condiciones/i }).first(),
    ).toBeVisible({ timeout: 20000 });

    const legalText = await legalPage.locator("body").innerText();
    if (legalText.trim().length < 120) {
      throw new Error("Legal content for Términos y Condiciones looks too short.");
    }

    const termsUrl = legalPage.url();
    setStatus("Términos y Condiciones", "PASS", `URL: ${termsUrl}`);
    await screenshot("checkpoint-terminos-y-condiciones", legalPage, true);

    if (openedInNewTab) {
      await legalPage.close();
      await page.bringToFront();
    } else {
      await page.goBack().catch(() => {});
      await page.waitForLoadState("domcontentloaded").catch(() => {});
    }
  });

  await guardedStep("Política de Privacidad", ["Administrar Negocios view"], async () => {
    const privacyLink = await waitForFirstVisible(
      [
        {
          label: "Política de Privacidad link",
          locator: page.getByRole("link", { name: /Política de Privacidad/i }).first(),
        },
        {
          label: "Política de Privacidad text",
          locator: page.getByText(/Política de Privacidad/i).first(),
        },
      ],
      15000,
    );

    const newTabPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await clickAndWait(page, privacyLink.locator);
    let legalPage = await newTabPromise;
    let openedInNewTab = true;
    if (!legalPage) {
      openedInNewTab = false;
      legalPage = page;
    } else {
      await legalPage.waitForLoadState("domcontentloaded");
    }

    await expect(
      legalPage.getByRole("heading", { name: /Política de Privacidad/i }).first(),
    ).toBeVisible({ timeout: 20000 });

    const privacyText = await legalPage.locator("body").innerText();
    if (privacyText.trim().length < 120) {
      throw new Error("Legal content for Política de Privacidad looks too short.");
    }

    const privacyUrl = legalPage.url();
    setStatus("Política de Privacidad", "PASS", `URL: ${privacyUrl}`);
    await screenshot("checkpoint-politica-de-privacidad", legalPage, true);

    if (openedInNewTab) {
      await legalPage.close();
      await page.bringToFront();
    } else {
      await page.goBack().catch(() => {});
      await page.waitForLoadState("domcontentloaded").catch(() => {});
    }
  });

  await testInfo.attach("final-report.json", {
    body: Buffer.from(JSON.stringify(report, null, 2), "utf-8"),
    contentType: "application/json",
  });

  // eslint-disable-next-line no-console
  console.table(
    REPORT_FIELDS.map((field) => ({
      step: field,
      status: report[field].status,
      details: report[field].details,
    })),
  );

  const failed = REPORT_FIELDS.filter((field) => report[field].status === "FAIL");
  expect(
    failed,
    `One or more workflow validations failed:\n${JSON.stringify(report, null, 2)}`,
  ).toEqual([]);
});
