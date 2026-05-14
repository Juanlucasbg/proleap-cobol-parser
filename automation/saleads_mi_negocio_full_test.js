const fs = require("node:fs/promises");
const path = require("node:path");
const { chromium } = require("playwright");

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

const EMAIL_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";

function createStep(label) {
  return {
    label,
    status: "FAIL",
    validations: [],
    evidence: {}
  };
}

function addValidation(step, message, passed, detail = "") {
  step.validations.push({ message, passed, detail });
}

function finalizeStep(step) {
  step.status = step.validations.length > 0 && step.validations.every((item) => item.passed) ? "PASS" : "FAIL";
}

function nowStamp() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

function normalizeFileName(name) {
  return name
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-zA-Z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "")
    .toLowerCase();
}

async function waitForUi(page, waitMs = 700) {
  await page.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {});
  await page.waitForTimeout(waitMs);
}

async function firstVisibleLocator(candidates) {
  for (const locator of candidates) {
    const first = locator.first();
    try {
      await first.waitFor({ state: "visible", timeout: 2500 });
      return first;
    } catch {
      // Try next candidate.
    }
  }
  return null;
}

async function isVisible(locator, timeout = 7000) {
  try {
    await locator.first().waitFor({ state: "visible", timeout });
    return true;
  } catch {
    return false;
  }
}

async function saveCheckpoint(page, runDir, stepIndex, label, fullPage = false) {
  const fileName = `${String(stepIndex).padStart(2, "0")}-${normalizeFileName(label)}.png`;
  const filePath = path.join(runDir, fileName);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

async function openFromCurrentEnvironment(page, startUrl) {
  if (startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
    return;
  }

  const current = page.url();
  if (!current || current === "about:blank") {
    throw new Error(
      "No login URL available. Provide SALEADS_START_URL for your current environment " +
        "or pre-open SaleADS login in the browser profile."
    );
  }

  await waitForUi(page);
}

async function run() {
  const runId = nowStamp();
  const runDir = path.join(process.cwd(), "artifacts", "saleads-mi-negocio", runId);
  await fs.mkdir(runDir, { recursive: true });

  const headless = process.env.HEADLESS !== "false";
  const userDataDir = process.env.PLAYWRIGHT_USER_DATA_DIR || path.join(process.cwd(), ".playwright-user");
  const startUrl = process.env.SALEADS_START_URL || process.env.SALEADS_URL || "";

  const context = await chromium.launchPersistentContext(userDataDir, {
    headless,
    viewport: { width: 1600, height: 1000 }
  });

  let page = context.pages()[0];
  if (!page) {
    page = await context.newPage();
  }

  const steps = {
    login: createStep("Login"),
    menu: createStep("Mi Negocio menu"),
    modal: createStep("Agregar Negocio modal"),
    manageView: createStep("Administrar Negocios view"),
    infoGeneral: createStep("Información General"),
    accountDetails: createStep("Detalles de la Cuenta"),
    businessList: createStep("Tus Negocios"),
    terms: createStep("Términos y Condiciones"),
    privacy: createStep("Política de Privacidad")
  };

  const finalReport = {
    name: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    runId,
    startUrl: startUrl || page.url(),
    artifactsDirectory: runDir,
    fields: {},
    details: steps
  };

  try {
    await openFromCurrentEnvironment(page, startUrl);

    // Step 1: Login with Google.
    const loginControl = await firstVisibleLocator([
      page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
      page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google/i)
    ]);

    addValidation(steps.login, "Login button or Sign in with Google is visible", !!loginControl);

    if (loginControl) {
      const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
      await loginControl.click();
      await waitForUi(page);

      const popup = await popupPromise;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
        const accountOption = await firstVisibleLocator([
          popup.getByText(EMAIL_ACCOUNT, { exact: true }),
          popup.getByRole("button", { name: EMAIL_ACCOUNT }),
          popup.getByRole("link", { name: EMAIL_ACCOUNT })
        ]);

        if (accountOption) {
          await accountOption.click();
          await popup.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {});
        }
      } else {
        const accountOptionInPage = await firstVisibleLocator([
          page.getByText(EMAIL_ACCOUNT, { exact: true }),
          page.getByRole("button", { name: EMAIL_ACCOUNT }),
          page.getByRole("link", { name: EMAIL_ACCOUNT })
        ]);
        if (accountOptionInPage) {
          await accountOptionInPage.click();
          await waitForUi(page);
        }
      }
    }

    const appSurfaceVisible = await isVisible(
      page
        .locator("aside, nav")
        .filter({ hasText: /mi negocio|administrar negocios|negocio|campa[ñn]a|dashboard|inicio/i })
    );
    const sidebarVisible = appSurfaceVisible;
    addValidation(steps.login, "Main application interface appears", appSurfaceVisible);
    addValidation(steps.login, "Left sidebar navigation is visible", sidebarVisible);
    steps.login.evidence.dashboardScreenshot = await saveCheckpoint(page, runDir, 1, "dashboard-loaded");
    finalizeStep(steps.login);

    // Step 2: Open Mi Negocio menu.
    const negocioSection = await firstVisibleLocator([
      page.getByText(/^Negocio$/i),
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByRole("link", { name: /^Negocio$/i })
    ]);
    if (negocioSection) {
      await negocioSection.click();
      await waitForUi(page);
    }
    const miNegocioOption = await firstVisibleLocator([
      page.getByText(/^Mi Negocio$/i),
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i })
    ]);
    addValidation(steps.menu, "Mi Negocio option is visible", !!miNegocioOption);
    if (miNegocioOption) {
      await miNegocioOption.click();
      await waitForUi(page);
    }

    const agregarNegocioVisible = await isVisible(page.getByText(/^Agregar Negocio$/i));
    const administrarNegociosVisible = await isVisible(page.getByText(/^Administrar Negocios$/i));
    addValidation(steps.menu, "Submenu expands", agregarNegocioVisible || administrarNegociosVisible);
    addValidation(steps.menu, "Agregar Negocio is visible", agregarNegocioVisible);
    addValidation(steps.menu, "Administrar Negocios is visible", administrarNegociosVisible);
    steps.menu.evidence.expandedMenuScreenshot = await saveCheckpoint(page, runDir, 2, "mi-negocio-expanded");
    finalizeStep(steps.menu);

    // Step 3: Validate Agregar Negocio modal.
    const agregarNegocioAction = await firstVisibleLocator([
      page.getByRole("menuitem", { name: /^Agregar Negocio$/i }),
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i)
    ]);
    addValidation(steps.modal, "Agregar Negocio action is available", !!agregarNegocioAction);
    if (agregarNegocioAction) {
      await agregarNegocioAction.click();
      await waitForUi(page);
    }

    const modalTitle = page.getByText(/Crear Nuevo Negocio/i);
    const modalVisible = await isVisible(modalTitle);
    const nombreNegocioInput = page.getByLabel(/Nombre del Negocio/i).or(page.getByPlaceholder(/Nombre del Negocio/i));
    const quotaVisible = await isVisible(page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i));
    const cancelVisible = await isVisible(page.getByRole("button", { name: /^Cancelar$/i }));
    const createVisible = await isVisible(page.getByRole("button", { name: /^Crear Negocio$/i }));

    addValidation(steps.modal, "Modal title Crear Nuevo Negocio is visible", modalVisible);
    addValidation(steps.modal, "Input field Nombre del Negocio exists", await isVisible(nombreNegocioInput));
    addValidation(steps.modal, "Text Tienes 2 de 3 negocios is visible", quotaVisible);
    addValidation(steps.modal, "Buttons Cancelar and Crear Negocio are present", cancelVisible && createVisible);

    if (await isVisible(nombreNegocioInput, 3000)) {
      await nombreNegocioInput.click();
      await nombreNegocioInput.fill("Negocio Prueba Automatización");
    }
    if (cancelVisible) {
      await page.getByRole("button", { name: /^Cancelar$/i }).click();
      await waitForUi(page);
    }

    steps.modal.evidence.modalScreenshot = await saveCheckpoint(page, runDir, 3, "agregar-negocio-modal");
    finalizeStep(steps.modal);

    // Step 4: Open Administrar Negocios.
    if (!(await isVisible(page.getByText(/^Administrar Negocios$/i), 2500))) {
      const reopenMenu = await firstVisibleLocator([
        page.getByText(/^Mi Negocio$/i),
        page.getByRole("button", { name: /^Mi Negocio$/i }),
        page.getByRole("link", { name: /^Mi Negocio$/i })
      ]);
      if (reopenMenu) {
        await reopenMenu.click();
        await waitForUi(page);
      }
    }

    const administrarNegociosAction = await firstVisibleLocator([
      page.getByRole("menuitem", { name: /^Administrar Negocios$/i }),
      page.getByRole("button", { name: /^Administrar Negocios$/i }),
      page.getByRole("link", { name: /^Administrar Negocios$/i }),
      page.getByText(/^Administrar Negocios$/i)
    ]);
    addValidation(steps.manageView, "Administrar Negocios action is available", !!administrarNegociosAction);
    if (administrarNegociosAction) {
      await administrarNegociosAction.click();
      await waitForUi(page, 1200);
    }

    const infoGeneralVisible = await isVisible(page.getByText(/^Información General$/i));
    const accountDetailsVisible = await isVisible(page.getByText(/^Detalles de la Cuenta$/i));
    const businessesSectionVisible = await isVisible(page.getByText(/^Tus Negocios$/i));
    const legalSectionVisible = await isVisible(page.getByText(/Sección Legal/i));
    addValidation(steps.manageView, "Section Información General exists", infoGeneralVisible);
    addValidation(steps.manageView, "Section Detalles de la Cuenta exists", accountDetailsVisible);
    addValidation(steps.manageView, "Section Tus Negocios exists", businessesSectionVisible);
    addValidation(steps.manageView, "Section Sección Legal exists", legalSectionVisible);
    steps.manageView.evidence.accountPageScreenshot = await saveCheckpoint(page, runDir, 4, "administrar-negocios-account-page", true);
    finalizeStep(steps.manageView);

    // Step 5: Validate Información General.
    addValidation(
      steps.infoGeneral,
      "User name is visible",
      await isVisible(page.locator("section,div").filter({ hasText: /Nombre|Usuario|Perfil/i }))
    );
    addValidation(
      steps.infoGeneral,
      "User email is visible",
      await isVisible(page.locator("section,div").filter({ hasText: /@/i }))
    );
    addValidation(steps.infoGeneral, "Text BUSINESS PLAN is visible", await isVisible(page.getByText(/BUSINESS PLAN/i)));
    addValidation(
      steps.infoGeneral,
      "Button Cambiar Plan is visible",
      await isVisible(page.getByRole("button", { name: /Cambiar Plan/i }))
    );
    finalizeStep(steps.infoGeneral);

    // Step 6: Validate Detalles de la Cuenta.
    addValidation(steps.accountDetails, "Cuenta creada is visible", await isVisible(page.getByText(/Cuenta creada/i)));
    addValidation(steps.accountDetails, "Estado activo is visible", await isVisible(page.getByText(/Estado activo/i)));
    addValidation(
      steps.accountDetails,
      "Idioma seleccionado is visible",
      await isVisible(page.getByText(/Idioma seleccionado/i))
    );
    finalizeStep(steps.accountDetails);

    // Step 7: Validate Tus Negocios.
    addValidation(
      steps.businessList,
      "Business list is visible",
      await isVisible(page.locator("section,div").filter({ hasText: /Tus Negocios/i }))
    );
    addValidation(
      steps.businessList,
      "Button Agregar Negocio exists",
      await isVisible(page.getByRole("button", { name: /^Agregar Negocio$/i }))
    );
    addValidation(
      steps.businessList,
      "Text Tienes 2 de 3 negocios is visible",
      await isVisible(page.getByText(/Tienes\s+2\s+de\s+3\s+negocios/i))
    );
    finalizeStep(steps.businessList);

    // Step 8: Validate Términos y Condiciones.
    if (steps.manageView.status !== "PASS") {
      addValidation(steps.terms, "Términos y Condiciones link is visible", false, "Prerequisite not met: Administrar Negocios view did not load.");
      addValidation(steps.terms, "Page contains heading Términos y Condiciones", false, "Prerequisite not met.");
      addValidation(steps.terms, "Legal content text is visible", false, "Prerequisite not met.");
    } else {
      const termsLink = await firstVisibleLocator([
        page.getByRole("link", { name: /Términos y Condiciones/i }),
        page.getByText(/Términos y Condiciones/i)
      ]);
      addValidation(steps.terms, "Términos y Condiciones link is visible", !!termsLink);
      let termsPage = page;
      if (termsLink) {
        const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
        await termsLink.click();
        const maybePopup = await popupPromise;
        if (maybePopup) {
          termsPage = maybePopup;
          await termsPage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
        } else {
          await waitForUi(page);
        }
      }

      const termsHeadingVisible = await isVisible(
        termsPage
          .getByRole("heading", { name: /Términos y Condiciones/i })
          .or(termsPage.getByText(/Términos y Condiciones/i))
      );
      addValidation(steps.terms, "Page contains heading Términos y Condiciones", termsHeadingVisible);
      const termsText = await termsPage.locator("body").innerText().catch(() => "");
      addValidation(
        steps.terms,
        "Legal content text is visible",
        termsHeadingVisible && termsText.replace(/\s+/g, " ").trim().length > 200
      );
      steps.terms.evidence.screenshot = await saveCheckpoint(termsPage, runDir, 8, "terminos-y-condiciones", true);
      steps.terms.evidence.finalUrl = termsPage.url();
      if (termsPage !== page) {
        await termsPage.close().catch(() => {});
        await page.bringToFront();
        await waitForUi(page);
      }
    }
    finalizeStep(steps.terms);

    // Step 9: Validate Política de Privacidad.
    if (steps.manageView.status !== "PASS") {
      addValidation(steps.privacy, "Política de Privacidad link is visible", false, "Prerequisite not met: Administrar Negocios view did not load.");
      addValidation(steps.privacy, "Page contains heading Política de Privacidad", false, "Prerequisite not met.");
      addValidation(steps.privacy, "Legal content text is visible", false, "Prerequisite not met.");
    } else {
      const privacyLink = await firstVisibleLocator([
        page.getByRole("link", { name: /Política de Privacidad/i }),
        page.getByText(/Política de Privacidad/i)
      ]);
      addValidation(steps.privacy, "Política de Privacidad link is visible", !!privacyLink);
      let privacyPage = page;
      if (privacyLink) {
        const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
        await privacyLink.click();
        const maybePopup = await popupPromise;
        if (maybePopup) {
          privacyPage = maybePopup;
          await privacyPage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
        } else {
          await waitForUi(page);
        }
      }

      const privacyHeadingVisible = await isVisible(
        privacyPage
          .getByRole("heading", { name: /Política de Privacidad/i })
          .or(privacyPage.getByText(/Política de Privacidad/i))
      );
      addValidation(steps.privacy, "Page contains heading Política de Privacidad", privacyHeadingVisible);
      const privacyText = await privacyPage.locator("body").innerText().catch(() => "");
      addValidation(
        steps.privacy,
        "Legal content text is visible",
        privacyHeadingVisible && privacyText.replace(/\s+/g, " ").trim().length > 200
      );
      steps.privacy.evidence.screenshot = await saveCheckpoint(privacyPage, runDir, 9, "politica-de-privacidad", true);
      steps.privacy.evidence.finalUrl = privacyPage.url();
      if (privacyPage !== page) {
        await privacyPage.close().catch(() => {});
        await page.bringToFront();
        await waitForUi(page);
      }
    }
    finalizeStep(steps.privacy);
  } catch (error) {
    addValidation(steps.login, "Execution-level error", false, String(error?.message || error));
    finalizeStep(steps.login);
  } finally {
    for (const field of REPORT_FIELDS) {
      const key = Object.keys(steps).find((candidate) => steps[candidate].label === field);
      finalReport.fields[field] = key ? steps[key].status : "FAIL";
    }

    const reportPath = path.join(runDir, "final-report.json");
    await fs.writeFile(reportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf8");
    await context.close();

    console.log("\n=== SaleADS Mi Negocio Final Report ===");
    console.log(`Artifacts directory: ${runDir}`);
    console.log(`Report file: ${reportPath}`);
    for (const field of REPORT_FIELDS) {
      console.log(`- ${field}: ${finalReport.fields[field]}`);
    }
  }
}

run().catch((error) => {
  console.error("Fatal error running SaleADS Mi Negocio test:", error);
  process.exitCode = 1;
});
