const fs = require("fs");
const { test, expect } = require("@playwright/test");

const DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
const GOOGLE_ACCOUNT =
  process.env.SALEADS_EXPECTED_GOOGLE_ACCOUNT || DEFAULT_GOOGLE_ACCOUNT;
const BUSINESS_NAME =
  process.env.SALEADS_TEST_BUSINESS_NAME || "Negocio Prueba Automatizacion";

const REPORT_KEYS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informacion General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Terminos y Condiciones",
  "Politica de Privacidad",
];

function newReport() {
  return REPORT_KEYS.reduce((acc, key) => {
    acc[key] = { status: "FAIL", details: "Not executed" };
    return acc;
  }, {});
}

function normalizeDetails(error) {
  if (!error) {
    return "";
  }

  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUiLoad(page) {
  await page.waitForLoadState("domcontentloaded");

  try {
    await page.waitForLoadState("networkidle", { timeout: 10000 });
  } catch {
    // Some environments keep background requests alive permanently.
  }

  await page.waitForTimeout(350);
}

function clickableCandidates(page, labelRegex) {
  return [
    page.getByRole("button", { name: labelRegex }),
    page.getByRole("link", { name: labelRegex }),
    page.getByRole("menuitem", { name: labelRegex }),
    page.getByRole("tab", { name: labelRegex }),
    page.getByText(labelRegex),
  ];
}

async function resolveVisible(page, locators, errorMessage, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const locator of locators) {
      const first = locator.first();

      if (await first.isVisible().catch(() => false)) {
        return first;
      }
    }

    await page.waitForTimeout(250);
  }

  throw new Error(errorMessage);
}

async function clickByVisibleText(page, labelRegex, errorMessage) {
  const target = await resolveVisible(
    page,
    clickableCandidates(page, labelRegex),
    errorMessage
  );
  await target.click();
  await waitForUiLoad(page);
}

async function assertVisibleByText(page, labelRegex, errorMessage) {
  await resolveVisible(
    page,
    [page.getByText(labelRegex), page.getByRole("heading", { name: labelRegex })],
    errorMessage
  );
}

async function screenshotCheckpoint(page, testInfo, name, fullPage = false) {
  const path = testInfo.outputPath(`${name}.png`);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(name, {
    path,
    contentType: "image/png",
  });
}

async function maybeChooseGoogleAccount(page, accountEmail) {
  const accountRegex = new RegExp(escapeRegex(accountEmail), "i");
  const useAccountRegex = new RegExp(`usar|continuar|continue|${accountEmail}`, "i");

  const accountLocator = await resolveVisible(
    page,
    [
      page.getByText(accountRegex),
      page.getByRole("button", { name: accountRegex }),
      page.getByRole("link", { name: accountRegex }),
      page.getByRole("button", { name: useAccountRegex }),
    ],
    `Google account selector not visible for ${accountEmail}`,
    8000
  ).catch(() => null);

  if (accountLocator) {
    await accountLocator.click();
    await waitForUiLoad(page);
    return true;
  }

  return false;
}

async function validateLegalLink({
  page,
  testInfo,
  linkRegex,
  headingRegex,
  reportLabel,
  screenshotName,
}) {
  const popupPromise = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);

  await clickByVisibleText(page, linkRegex, `Could not click legal link ${reportLabel}`);
  const popup = await popupPromise;

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await waitForUiLoad(popup);
    await assertVisibleByText(
      popup,
      headingRegex,
      `Legal heading not found in popup for ${reportLabel}`
    );

    const popupBody = (await popup.locator("body").innerText()).trim();
    if (popupBody.length < 80) {
      throw new Error(`Legal content for ${reportLabel} seems too short.`);
    }

    await screenshotCheckpoint(popup, testInfo, screenshotName);
    console.log(`[LEGAL_URL] ${reportLabel}: ${popup.url()}`);
    await popup.close();
    await page.bringToFront();
    await waitForUiLoad(page);
    return;
  }

  await assertVisibleByText(
    page,
    headingRegex,
    `Legal heading not found after navigation for ${reportLabel}`
  );
  const bodyText = (await page.locator("body").innerText()).trim();
  if (bodyText.length < 80) {
    throw new Error(`Legal content for ${reportLabel} seems too short.`);
  }

  await screenshotCheckpoint(page, testInfo, screenshotName);
  console.log(`[LEGAL_URL] ${reportLabel}: ${page.url()}`);

  await page.goBack().catch(() => null);
  await waitForUiLoad(page);
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("should login and validate full Mi Negocio flow", async ({ page, baseURL }, testInfo) => {
    const report = newReport();

    const runStep = async (reportKey, action) => {
      try {
        const detail = await action();
        report[reportKey] = {
          status: "PASS",
          details: detail || "Validated",
        };
        return true;
      } catch (error) {
        report[reportKey] = {
          status: "FAIL",
          details: normalizeDetails(error),
        };
        return false;
      }
    };

    // Step 1: Login with Google.
    const loginOk = await runStep("Login", async () => {
      if (page.url() === "about:blank") {
        if (!baseURL) {
          throw new Error(
            "Browser is blank and SALEADS_URL is not set. Provide SALEADS_URL or preload login page."
          );
        }

        await page.goto(baseURL, { waitUntil: "domcontentloaded" });
        await waitForUiLoad(page);
      }

      const popupPromise = page.context().waitForEvent("page", { timeout: 8000 }).catch(() => null);
      await clickByVisibleText(
        page,
        /sign in with google|iniciar sesion con google|ingresar con google|continuar con google|google/i,
        "Google login button was not found on login page."
      );

      const popup = await popupPromise;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        await maybeChooseGoogleAccount(popup, GOOGLE_ACCOUNT);
        await popup.waitForTimeout(1000).catch(() => null);
      } else {
        await maybeChooseGoogleAccount(page, GOOGLE_ACCOUNT);
      }

      await waitForUiLoad(page);
      await resolveVisible(
        page,
        [
          page.locator("aside"),
          page.getByRole("navigation"),
          page.getByText(/mi negocio|negocio/i),
        ],
        "Main interface did not load after login."
      );

      await screenshotCheckpoint(page, testInfo, "01-dashboard-loaded");
      return "Main interface and sidebar are visible";
    });

    // Step 2: Open Mi Negocio menu.
    const menuOk = await runStep("Mi Negocio menu", async () => {
      if (!loginOk) {
        throw new Error("Login failed, cannot continue with Mi Negocio menu validations.");
      }

      await clickByVisibleText(
        page,
        /negocio/i,
        "Negocio section was not found in sidebar."
      );
      await clickByVisibleText(
        page,
        /mi negocio/i,
        "Mi Negocio option was not found in sidebar."
      );

      await assertVisibleByText(page, /agregar negocio/i, "Agregar Negocio is not visible.");
      await assertVisibleByText(
        page,
        /administrar negocios/i,
        "Administrar Negocios is not visible."
      );

      await screenshotCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded");
      return "Mi Negocio submenu is expanded with required options";
    });

    // Step 3: Validate Agregar Negocio modal.
    const addBusinessModalOk = await runStep("Agregar Negocio modal", async () => {
      if (!menuOk) {
        throw new Error("Mi Negocio menu validation failed, cannot validate modal.");
      }

      await clickByVisibleText(page, /agregar negocio/i, "Agregar Negocio option was not clickable.");

      await assertVisibleByText(page, /crear nuevo negocio/i, "Modal title Crear Nuevo Negocio missing.");
      await assertVisibleByText(page, /nombre del negocio/i, "Nombre del Negocio field missing.");
      await assertVisibleByText(page, /tienes 2 de 3 negocios/i, "Business quota text missing.");
      await assertVisibleByText(page, /cancelar/i, "Cancelar button missing in modal.");
      await assertVisibleByText(page, /crear negocio/i, "Crear Negocio button missing in modal.");

      const businessNameInput = await resolveVisible(
        page,
        [
          page.getByLabel(/nombre del negocio/i),
          page.getByPlaceholder(/nombre del negocio/i),
          page.locator('input[type="text"]'),
        ],
        "Nombre del Negocio input could not be located.",
        7000
      ).catch(() => null);

      if (businessNameInput) {
        await businessNameInput.click();
        await businessNameInput.fill(BUSINESS_NAME);
      }

      await screenshotCheckpoint(page, testInfo, "03-agregar-negocio-modal");
      await clickByVisibleText(page, /cancelar/i, "Cancelar button could not be clicked.");
      return "Agregar Negocio modal contains all required controls";
    });

    // Step 4: Open Administrar Negocios view.
    const manageBusinessOk = await runStep("Administrar Negocios view", async () => {
      if (!menuOk) {
        throw new Error("Mi Negocio menu validation failed, cannot open Administrar Negocios.");
      }

      const manageVisible = await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false);
      if (!manageVisible) {
        await clickByVisibleText(page, /mi negocio/i, "Mi Negocio could not be re-expanded.");
      }

      await clickByVisibleText(
        page,
        /administrar negocios/i,
        "Administrar Negocios option could not be clicked."
      );

      await assertVisibleByText(page, /informacion general|información general/i, "Informacion General section missing.");
      await assertVisibleByText(page, /detalles de la cuenta/i, "Detalles de la Cuenta section missing.");
      await assertVisibleByText(page, /tus negocios/i, "Tus Negocios section missing.");
      await assertVisibleByText(page, /seccion legal|sección legal/i, "Seccion Legal section missing.");

      await screenshotCheckpoint(page, testInfo, "04-administrar-negocios-full", true);
      return "Administrar Negocios account page loaded with all main sections";
    });

    // Step 5: Validate Informacion General.
    await runStep("Informacion General", async () => {
      if (!manageBusinessOk) {
        throw new Error("Administrar Negocios view not available.");
      }

      await assertVisibleByText(page, /business plan/i, "BUSINESS PLAN text not visible.");
      await resolveVisible(
        page,
        clickableCandidates(page, /cambiar plan/i),
        "Cambiar Plan button not visible."
      );

      const emailVisible = await page
        .getByText(new RegExp(`${escapeRegex(GOOGLE_ACCOUNT)}|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}`))
        .first()
        .isVisible()
        .catch(() => false);
      if (!emailVisible) {
        throw new Error("User email is not visible in Informacion General.");
      }

      const nameVisible = await page
        .getByText(/juanlucas|usuario|perfil|nombre/i)
        .first()
        .isVisible()
        .catch(() => false);
      if (!nameVisible) {
        throw new Error("User name hint is not visible in Informacion General.");
      }

      return "Informacion General data validated";
    });

    // Step 6: Validate Detalles de la Cuenta.
    await runStep("Detalles de la Cuenta", async () => {
      if (!manageBusinessOk) {
        throw new Error("Administrar Negocios view not available.");
      }

      await assertVisibleByText(page, /cuenta creada/i, "Cuenta creada is missing.");
      await assertVisibleByText(page, /estado activo|activo/i, "Estado activo is missing.");
      await assertVisibleByText(page, /idioma seleccionado/i, "Idioma seleccionado is missing.");
      return "Detalles de la Cuenta labels validated";
    });

    // Step 7: Validate Tus Negocios section.
    await runStep("Tus Negocios", async () => {
      if (!manageBusinessOk) {
        throw new Error("Administrar Negocios view not available.");
      }

      await assertVisibleByText(page, /tus negocios/i, "Tus Negocios title is missing.");
      await assertVisibleByText(page, /agregar negocio/i, "Agregar Negocio button is missing.");
      await assertVisibleByText(page, /tienes 2 de 3 negocios/i, "Business quota text missing.");
      return "Tus Negocios section validated";
    });

    // Step 8: Validate Terminos y Condiciones.
    await runStep("Terminos y Condiciones", async () => {
      if (!manageBusinessOk && !addBusinessModalOk) {
        throw new Error("Cannot validate legal links without Administrar Negocios context.");
      }

      await validateLegalLink({
        page,
        testInfo,
        linkRegex: /terminos y condiciones|términos y condiciones/i,
        headingRegex: /terminos y condiciones|términos y condiciones/i,
        reportLabel: "Terminos y Condiciones",
        screenshotName: "08-terminos-y-condiciones",
      });

      return "Legal page heading/content validated and URL captured in logs";
    });

    // Step 9: Validate Politica de Privacidad.
    await runStep("Politica de Privacidad", async () => {
      if (!manageBusinessOk && !addBusinessModalOk) {
        throw new Error("Cannot validate legal links without Administrar Negocios context.");
      }

      await validateLegalLink({
        page,
        testInfo,
        linkRegex: /politica de privacidad|política de privacidad/i,
        headingRegex: /politica de privacidad|política de privacidad/i,
        reportLabel: "Politica de Privacidad",
        screenshotName: "09-politica-de-privacidad",
      });

      return "Legal page heading/content validated and URL captured in logs";
    });

    // Step 10: Final report.
    const rows = REPORT_KEYS.map((field) => ({
      Step: field,
      Status: report[field].status,
      Details: report[field].details,
    }));
    console.table(rows);

    const reportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
    fs.writeFileSync(reportPath, JSON.stringify({ results: report }, null, 2), "utf8");
    await testInfo.attach("saleads-mi-negocio-final-report", {
      path: reportPath,
      contentType: "application/json",
    });

    const failed = rows.filter((row) => row.Status !== "PASS");
    expect(
      failed,
      `Workflow finished with failed validations: ${failed
        .map((item) => `${item.Step}: ${item.Details}`)
        .join(" | ")}`
    ).toEqual([]);
  });
});
