const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const LOGIN_ENTRY_REGEX = /iniciar sesi[oó]n|login|sign in|acceder/i;
const GOOGLE_ENTRY_REGEX = /google|continuar con google|sign in with google|iniciar con google/i;
const CREATE_BUSINESS_TITLE_REGEX = /Crear Nuevo Negocio|Create New Business/i;
const BUSINESS_NAME_FIELD_REGEX = /Nombre del Negocio|Business Name/i;
const BUSINESS_LIMIT_REGEX = /Tienes 2 de 3 negocios|You have 2 of 3 businesses/i;
const INFO_HEADING_REGEX = /Informaci[oó]n General|General Information/i;
const DETAILS_HEADING_REGEX = /Detalles de la Cuenta|Account Details/i;
const BUSINESSES_HEADING_REGEX = /Tus Negocios|Your Businesses/i;
const LEGAL_HEADING_REGEX = /Secci[oó]n Legal|Legal Section/i;
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

function getTimestampLabel() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {});
  await page.waitForTimeout(800);
}

async function firstVisible(locators) {
  for (const locator of locators) {
    const candidate = locator.first();
    const visible = await candidate.isVisible().catch(() => false);
    if (visible) {
      return candidate;
    }
  }
  return null;
}

async function getSidebar(page) {
  return firstVisible([
    page.locator("aside"),
    page.locator("[class*='sidebar']"),
    page.getByRole("navigation").filter({ hasText: /Mi Negocio|Administrar Negocios|Agregar Negocio|Tus Negocios/i }),
    page.locator("nav").filter({ hasText: /Mi Negocio|Administrar Negocios|Agregar Negocio|Tus Negocios/i })
  ]);
}

function createEmptyReport() {
  const base = {};
  for (const field of REPORT_FIELDS) {
    base[field] = "FAIL";
  }
  base.termsFinalUrl = "";
  base.privacyFinalUrl = "";
  base.errors = [];
  return base;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const loginUrl =
    process.env.SALEADS_LOGIN_URL ||
    process.env.SALEADS_URL ||
    process.env.BASE_URL ||
    "";

  if (!loginUrl) {
    throw new Error(
      "Missing SALEADS_LOGIN_URL (or SALEADS_URL / BASE_URL). Set it to the current environment login page URL."
    );
  }

  const runDir = path.resolve(__dirname, "..", "artifacts", getTimestampLabel());
  fs.mkdirSync(runDir, { recursive: true });

  const report = createEmptyReport();
  let appPage = page;

  const pass = (field) => {
    report[field] = "PASS";
  };

  const fail = (field, error) => {
    report[field] = "FAIL";
    report.errors.push(`${field}: ${error?.message || String(error)}`);
  };

  const runStep = async (field, fn) => {
    try {
      await fn();
      pass(field);
    } catch (error) {
      fail(field, error);
    }
  };

  await runStep("Login", async () => {
    await appPage.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(appPage);

    let sidebar = await getSidebar(appPage);
    if (!sidebar) {
      const loginEntry = await firstVisible([
        appPage.getByRole("button", { name: LOGIN_ENTRY_REGEX }),
        appPage.getByRole("link", { name: LOGIN_ENTRY_REGEX }),
        appPage.locator("button").filter({ hasText: LOGIN_ENTRY_REGEX }),
        appPage.locator("a").filter({ hasText: LOGIN_ENTRY_REGEX })
      ]);

      if (loginEntry) {
        await loginEntry.click();
        await waitForUi(appPage);
      }
    }

    sidebar = await getSidebar(appPage);
    if (!sidebar) {
      const googleAction = await firstVisible([
        appPage.getByRole("button", { name: GOOGLE_ENTRY_REGEX }),
        appPage.getByRole("link", { name: GOOGLE_ENTRY_REGEX }),
        appPage.locator("button").filter({ hasText: GOOGLE_ENTRY_REGEX }),
        appPage.locator("a").filter({ hasText: GOOGLE_ENTRY_REGEX })
      ]);

      if (!googleAction) {
        throw new Error("Could not find Google login action (button/link).");
      }

      const googlePopupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
      await googleAction.click();

      let googlePage = await googlePopupPromise;
      if (!googlePage) {
        googlePage = appPage;
      }

      await googlePage.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => {});
      await waitForUi(googlePage);

      if (/accounts\.google\.com/i.test(googlePage.url())) {
        const accountOption = await firstVisible([
          googlePage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: false }),
          googlePage.locator(`[data-email="${GOOGLE_ACCOUNT_EMAIL}"]`),
          googlePage.locator(`[data-identifier="${GOOGLE_ACCOUNT_EMAIL}"]`)
        ]);

        if (accountOption) {
          await accountOption.click();
          await waitForUi(googlePage);
        } else {
          const emailInput = await firstVisible([
            googlePage.locator("input[type='email']"),
            googlePage.getByLabel(/email|correo|phone|tel[eé]fono/i)
          ]);
          if (emailInput) {
            await emailInput.fill(GOOGLE_ACCOUNT_EMAIL);
            const nextButton = await firstVisible([
              googlePage.getByRole("button", { name: /next|siguiente/i }),
              googlePage.getByText(/next|siguiente/i)
            ]);
            if (nextButton) {
              await nextButton.click();
              await waitForUi(googlePage);
            }
          }
        }

        const passwordRequired = await googlePage.locator("input[type='password']").isVisible().catch(() => false);
        if (passwordRequired) {
          await googlePage.screenshot({ path: path.join(runDir, "login-password-required.png"), fullPage: true });
          throw new Error(
            "Google sign-in requires password entry. Use a pre-authenticated browser/session where account chooser is available."
          );
        }
      }
    }

    await appPage.bringToFront();
    await waitForUi(appPage);

    for (let i = 0; i < 60; i += 1) {
      sidebar = await getSidebar(appPage);
      if (sidebar) {
        break;
      }
      await appPage.waitForTimeout(1000);
    }

    if (!sidebar) {
      const googleBlocked = await firstVisible([
        appPage.getByText(/this browser or app may not be secure/i),
        appPage.getByText(/couldn.?t sign you in|no pudimos iniciar sesi[oó]n/i)
      ]);
      if (googleBlocked) {
        await appPage.screenshot({ path: path.join(runDir, "login-google-blocked.png"), fullPage: true });
        throw new Error(
          "Google blocked automated sign-in in this runtime (unsafe browser/app). Run with an approved interactive browser session."
        );
      }

      throw new Error("Main app sidebar/navigation is not visible after login.");
    }

    await expect(sidebar).toBeVisible({ timeout: 45000 });
    await appPage.screenshot({ path: path.join(runDir, "01-dashboard-loaded.png"), fullPage: true });
  });

  if (report.Login !== "PASS") {
    for (const field of REPORT_FIELDS.slice(1)) {
      report[field] = "FAIL";
      if (!report.errors.some((entry) => entry.startsWith(`${field}:`))) {
        report.errors.push(`${field}: Skipped due login failure.`);
      }
    }
  } else {
    await runStep("Mi Negocio menu", async () => {
    const miNegocioTrigger = await firstVisible([
      appPage.getByRole("button", { name: /mi negocio/i }),
      appPage.getByRole("link", { name: /mi negocio/i }),
      appPage.getByText(/^Mi Negocio$/i),
      appPage.getByText(/Negocio/i)
    ]);

    if (!miNegocioTrigger) {
      throw new Error("Could not find 'Mi Negocio' option.");
    }

    await miNegocioTrigger.click();
    await waitForUi(appPage);

    await expect(appPage.getByText(/Agregar Negocio|Add Business/i).first()).toBeVisible({ timeout: 20000 });
    await expect(appPage.getByText(/Administrar Negocios|Manage Businesses/i).first()).toBeVisible({ timeout: 20000 });
    await appPage.screenshot({ path: path.join(runDir, "02-mi-negocio-expanded.png"), fullPage: true });
  });

    await runStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await firstVisible([
      appPage.getByRole("menuitem", { name: /agregar negocio|add business/i }),
      appPage.locator("nav").getByText(/Agregar Negocio|Add Business/i),
      appPage.getByRole("button", { name: /agregar negocio|add business/i }),
      appPage.getByRole("link", { name: /agregar negocio|add business/i }),
      appPage.getByText(/^Agregar Negocio$/i)
    ]);

    if (!agregarNegocio) {
      throw new Error("Could not find 'Agregar Negocio' action.");
    }

    await agregarNegocio.click();
    await waitForUi(appPage);

    const modal = await firstVisible([
      appPage.getByRole("dialog").filter({ hasText: CREATE_BUSINESS_TITLE_REGEX }),
      appPage.locator("div[role='dialog']").filter({ hasText: CREATE_BUSINESS_TITLE_REGEX }),
      appPage.locator("div").filter({ hasText: CREATE_BUSINESS_TITLE_REGEX })
    ]);

    if (!modal) {
      throw new Error("Expected modal 'Crear Nuevo Negocio' was not found.");
    }

    await expect(modal).toBeVisible({ timeout: 20000 });
    await expect(modal.getByText(CREATE_BUSINESS_TITLE_REGEX)).toBeVisible();
    await expect(modal.getByText(BUSINESS_NAME_FIELD_REGEX)).toBeVisible();
    await expect(modal.getByText(BUSINESS_LIMIT_REGEX)).toBeVisible();
    await expect(modal.getByRole("button", { name: /Cancelar|Cancel/i })).toBeVisible();
    await expect(modal.getByRole("button", { name: /Crear Negocio|Create Business/i })).toBeVisible();
    await appPage.screenshot({ path: path.join(runDir, "03-agregar-negocio-modal.png"), fullPage: true });

    const nameInput = await firstVisible([
      modal.getByLabel(BUSINESS_NAME_FIELD_REGEX),
      modal.getByPlaceholder(BUSINESS_NAME_FIELD_REGEX),
      modal.locator("input").first()
    ]);

    if (nameInput) {
      await nameInput.click();
      await nameInput.fill("Negocio Prueba Automatización");
    }

    await modal.getByRole("button", { name: /Cancelar|Cancel/i }).click();
    await waitForUi(appPage);
  });

    await runStep("Administrar Negocios view", async () => {
    const administrarVisible = await appPage
      .getByText(/Administrar Negocios|Manage Businesses/i)
      .first()
      .isVisible()
      .catch(() => false);
    if (!administrarVisible) {
      const miNegocioAgain = await firstVisible([
        appPage.getByRole("button", { name: /mi negocio/i }),
        appPage.getByRole("link", { name: /mi negocio/i }),
        appPage.getByText(/^Mi Negocio$/i)
      ]);
      if (miNegocioAgain) {
        await miNegocioAgain.click();
        await waitForUi(appPage);
      }
    }

    const administrar = await firstVisible([
      appPage.getByRole("link", { name: /administrar negocios|manage businesses/i }),
      appPage.getByRole("button", { name: /administrar negocios|manage businesses/i }),
      appPage.getByText(/Administrar Negocios|Manage Businesses/i)
    ]);

    if (!administrar) {
      throw new Error("Could not find 'Administrar Negocios' option.");
    }

    await administrar.click();
    await waitForUi(appPage);

    await expect(appPage.getByText(INFO_HEADING_REGEX).first()).toBeVisible({ timeout: 25000 });
    await expect(appPage.getByText(DETAILS_HEADING_REGEX).first()).toBeVisible({ timeout: 25000 });
    await expect(appPage.getByText(BUSINESSES_HEADING_REGEX).first()).toBeVisible({ timeout: 25000 });
    await expect(appPage.getByText(LEGAL_HEADING_REGEX).first()).toBeVisible({ timeout: 25000 });
    await appPage.screenshot({ path: path.join(runDir, "04-administrar-negocios-page.png"), fullPage: true });
  });

    await runStep("Información General", async () => {
    const infoSection = appPage.locator("section,article,div").filter({ hasText: INFO_HEADING_REGEX }).first();
    await expect(infoSection).toBeVisible({ timeout: 20000 });

    const infoText = await infoSection.innerText();
    const hasEmail = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/.test(infoText);
    if (!hasEmail) {
      throw new Error("User email was not found in 'Información General'.");
    }

    const hasLikelyName = /\b[A-Za-zÀ-ÿ]{3,}\s+[A-Za-zÀ-ÿ]{3,}\b/.test(infoText);
    if (!hasLikelyName) {
      throw new Error("User name was not detected in 'Información General'.");
    }

    await expect(infoSection.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(infoSection.getByRole("button", { name: /Cambiar Plan|Change Plan/i })).toBeVisible();
  });

    await runStep("Detalles de la Cuenta", async () => {
    const detailsSection = appPage.locator("section,article,div").filter({ hasText: DETAILS_HEADING_REGEX }).first();
    await expect(detailsSection).toBeVisible({ timeout: 20000 });
    await expect(detailsSection.getByText(/Cuenta creada|Account created/i)).toBeVisible();
    await expect(detailsSection.getByText(/Estado activo|Active status/i)).toBeVisible();
    await expect(detailsSection.getByText(/Idioma seleccionado|Selected language/i)).toBeVisible();
  });

    await runStep("Tus Negocios", async () => {
    const businessSection = appPage.locator("section,article,div").filter({ hasText: BUSINESSES_HEADING_REGEX }).first();
    await expect(businessSection).toBeVisible({ timeout: 20000 });
    const addBusiness = await firstVisible([
      businessSection.getByRole("button", { name: /Agregar Negocio|Add Business/i }),
      businessSection.getByRole("link", { name: /Agregar Negocio|Add Business/i }),
      businessSection.getByText(/Agregar Negocio|Add Business/i)
    ]);
    if (!addBusiness) {
      throw new Error("Add business action is not visible in 'Tus Negocios'.");
    }
    await expect(addBusiness).toBeVisible();
    await expect(businessSection.getByText(BUSINESS_LIMIT_REGEX)).toBeVisible();

    const businessItemCount = await businessSection
      .locator("li, tr, [role='row'], [data-testid*='business'], [class*='business']")
      .count();
    const businessText = (await businessSection.innerText()).trim();

    if (businessItemCount === 0 && businessText.length < 80) {
      throw new Error("Business list is not clearly visible in 'Tus Negocios'.");
    }
  });

    async function validateLegalPage(linkRegex, headingRegex, screenshotName, reportUrlField) {
      await expect(appPage.getByText(LEGAL_HEADING_REGEX).first()).toBeVisible({ timeout: 10000 });

      const legalLink = await firstVisible([
        appPage.getByRole("link", { name: linkRegex }),
        appPage.getByText(linkRegex)
      ]);

      if (!legalLink) {
        throw new Error(`Could not find legal link: ${linkRegex}`);
      }

      const newTabPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
      await legalLink.click();

      let legalPage = await newTabPromise;
      const openedInNewTab = Boolean(legalPage);
      if (!legalPage) {
        legalPage = appPage;
      }

      await legalPage.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => {});
      await waitForUi(legalPage);

      const heading = await firstVisible([
        legalPage.getByRole("heading", { name: headingRegex }),
        legalPage.getByText(headingRegex)
      ]);

      if (!heading) {
        throw new Error(`Heading not found for legal page ${headingRegex}`);
      }

      await expect(heading).toBeVisible({ timeout: 20000 });
      const legalBodyText = (await legalPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
      if (legalBodyText.length < 200) {
        throw new Error("Legal content text appears too short.");
      }

      report[reportUrlField] = legalPage.url();
      await legalPage.screenshot({ path: path.join(runDir, screenshotName), fullPage: true });

      if (openedInNewTab) {
        await appPage.bringToFront();
        await waitForUi(appPage);
      } else {
        await legalPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
        await waitForUi(appPage);
      }
    }

    await runStep("Términos y Condiciones", async () => {
      await validateLegalPage(
        /T[eé]rminos y Condiciones|T[eé]rminos|Terms( and Conditions)?/i,
        /T[eé]rminos y Condiciones|Terms( and Conditions)?/i,
        "05-terminos-y-condiciones.png",
        "termsFinalUrl"
      );
    });

    await runStep("Política de Privacidad", async () => {
      await validateLegalPage(
        /Pol[ií]tica de Privacidad|Privacy Policy/i,
        /Pol[ií]tica de Privacidad|Privacy Policy/i,
        "06-politica-de-privacidad.png",
        "privacyFinalUrl"
      );
    });
  }

  const reportPath = path.join(runDir, "final-report.json");
  fs.writeFileSync(reportPath, JSON.stringify(report, null, 2));
  await testInfo.attach("final-report", { path: reportPath, contentType: "application/json" });

  const failedFields = REPORT_FIELDS.filter((field) => report[field] !== "PASS");
  if (failedFields.length > 0) {
    throw new Error(`Validation failures in: ${failedFields.join(", ")}. Report: ${reportPath}`);
  }
});
