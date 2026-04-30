const { test, expect } = require("@playwright/test");
const fs = require("fs");
const path = require("path");

const SCREENSHOT_DIR = path.resolve(__dirname, "..", "screenshots");

function normalize(text) {
  return text
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .replace(/\s+/g, " ")
    .trim()
    .toLowerCase();
}

function textRegex(text) {
  return new RegExp(
    text
      .split(/\s+/)
      .map((part) => part.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"))
      .join("\\s+"),
    "i",
  );
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForLoadState("networkidle");
}

async function clickAndWait(page, locator) {
  await expect(locator).toBeVisible({ timeout: 20000 });
  await locator.click();
  await waitForUi(page);
}

async function takeCheckpoint(page, name, fullPage = false) {
  fs.mkdirSync(SCREENSHOT_DIR, { recursive: true });
  await page.screenshot({
    path: path.join(SCREENSHOT_DIR, `${name}.png`),
    fullPage,
  });
}

async function findByVisibleText(container, options) {
  for (const option of options) {
    const locator = container.getByText(option.regex, { exact: false }).first();
    if (await locator.count()) {
      return locator;
    }
  }
  return null;
}

function buildReport() {
  return {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Información General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Términos y Condiciones": "FAIL",
    "Política de Privacidad": "FAIL",
  };
}

async function resolveGoogleButton(page) {
  const candidates = [
    page.getByRole("button", { name: /google/i }),
    page.getByRole("link", { name: /google/i }),
    page.getByText(/sign in with google/i),
    page.getByText(/iniciar sesion con google/i),
    page.getByText(/continuar con google/i),
  ];

  for (const candidate of candidates) {
    if (await candidate.first().count()) {
      return candidate.first();
    }
  }
  return null;
}

async function maybePickGoogleAccount(page) {
  const targetEmail = page.getByText("juanlucasbarbiergarzon@gmail.com", {
    exact: false,
  });
  if (await targetEmail.count()) {
    await targetEmail.first().click();
    await waitForUi(page);
  }
}

async function expectLeftSidebar(page) {
  const sidebarCandidates = [
    page.locator("aside"),
    page.getByRole("navigation"),
    page.locator('[class*="sidebar" i]'),
    page.locator('[data-testid*="sidebar" i]'),
  ];

  for (const candidate of sidebarCandidates) {
    if (await candidate.first().count()) {
      await expect(candidate.first()).toBeVisible({ timeout: 30000 });
      return candidate.first();
    }
  }
  throw new Error("Left sidebar navigation not found.");
}

async function openMiNegocio(page, sidebar) {
  const miNegocioVisible = await findByVisibleText(sidebar, [
    { regex: textRegex("Mi Negocio") },
    { regex: /my business/i },
  ]);

  if (miNegocioVisible && (await miNegocioVisible.isVisible().catch(() => false))) {
    await clickAndWait(page, miNegocioVisible);
    return;
  }

  const negocio = await findByVisibleText(sidebar, [
    { regex: /negocio/i },
    { regex: /business/i },
  ]);
  if (!negocio) throw new Error("Sidebar section 'Negocio' was not found.");
  await clickAndWait(page, negocio);

  const miNegocio = await findByVisibleText(sidebar, [
    { regex: textRegex("Mi Negocio") },
    { regex: /my business/i },
  ]);
  if (!miNegocio) throw new Error("Menu option 'Mi Negocio' was not found.");
  await clickAndWait(page, miNegocio);
}

async function assertMenuExpanded(page, sidebar) {
  const agregarNegocio = await findByVisibleText(sidebar, [
    { regex: textRegex("Agregar Negocio") },
    { regex: /add business/i },
  ]);
  const administrarNegocios = await findByVisibleText(sidebar, [
    { regex: textRegex("Administrar Negocios") },
    { regex: /manage businesses/i },
  ]);

  if (!agregarNegocio || !administrarNegocios) {
    throw new Error(
      "Expected submenu entries 'Agregar Negocio' and 'Administrar Negocios' are missing.",
    );
  }

  await expect(agregarNegocio).toBeVisible();
  await expect(administrarNegocios).toBeVisible();
}

async function assertModal(page) {
  const dialog =
    page.getByRole("dialog").first().or(page.locator('[role="presentation"]'));
  await expect(
    page.getByText(textRegex("Crear Nuevo Negocio"), { exact: false }),
  ).toBeVisible({
    timeout: 20000,
  });
  await expect(
    page.getByLabel(textRegex("Nombre del Negocio"), { exact: false }),
  ).toBeVisible();
  await expect(
    page.getByText(textRegex("Tienes 2 de 3 negocios"), { exact: false }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: textRegex("Cancelar") }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: textRegex("Crear Negocio") }),
  ).toBeVisible();

  const field = page
    .getByLabel(textRegex("Nombre del Negocio"), { exact: false })
    .or(page.getByPlaceholder(textRegex("Nombre del Negocio"), { exact: false }))
    .first();
  const cancelButton = page
    .getByRole("button", { name: textRegex("Cancelar") })
    .first();
  return { field, cancelButton, dialog };
}

async function clickMaybeInNewTab(page, labelRegex) {
  const currentPage = page;
  const link = currentPage.getByText(labelRegex, { exact: false }).first();
  await expect(link).toBeVisible({ timeout: 20000 });

  const [popup] = await Promise.all([
    currentPage.context().waitForEvent("page", { timeout: 5000 }).catch(() => null),
    link.click(),
  ]);

  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await popup.waitForLoadState("networkidle");
    return { page: popup, openedNewTab: true };
  }

  await waitForUi(currentPage);
  return { page: currentPage, openedNewTab: false };
}

async function assertLegalPage(targetPage, headingText, screenshotName) {
  const heading = targetPage
    .getByRole("heading", { name: textRegex(headingText) })
    .first()
    .or(targetPage.getByText(textRegex(headingText), { exact: false }).first());
  await expect(heading).toBeVisible({ timeout: 30000 });
  const bodyText = normalize(await targetPage.locator("body").innerText());
  if (!bodyText.includes(normalize(headingText))) {
    throw new Error(`Legal content for '${headingText}' not found on page.`);
  }
  await takeCheckpoint(targetPage, screenshotName, true);
  return targetPage.url();
}

test.describe("SaleADS Mi Negocio full workflow", () => {
  test("login with Google and validate Mi Negocio flow", async ({ page }) => {
    const report = buildReport();
    const evidence = {};
    let sidebar;
    let failure;

    try {
      // Step 1: Login with Google (assume already on login page)
      const googleLoginButton = await resolveGoogleButton(page);
      expect(
        googleLoginButton,
        "Could not find Google login/sign-in button by visible text.",
      ).not.toBeNull();
      const [googlePopup] = await Promise.all([
        page.context().waitForEvent("page", { timeout: 5000 }).catch(() => null),
        googleLoginButton.click(),
      ]);
      if (googlePopup) {
        await waitForUi(googlePopup);
        await maybePickGoogleAccount(googlePopup);
        await googlePopup.waitForEvent("close", { timeout: 60000 }).catch(() => {});
        await page.bringToFront();
      } else {
        await waitForUi(page);
        await maybePickGoogleAccount(page);
      }
      sidebar = await expectLeftSidebar(page);
      report.Login = "PASS";
      await takeCheckpoint(page, "01-dashboard-loaded", true);
      evidence.dashboardScreenshot = path.join(
        SCREENSHOT_DIR,
        "01-dashboard-loaded.png",
      );

      // Step 2: Open Mi Negocio menu and validate expansion
      await openMiNegocio(page, sidebar);
      await assertMenuExpanded(page, sidebar);
      report["Mi Negocio menu"] = "PASS";
      await takeCheckpoint(page, "02-mi-negocio-menu-expanded", true);
      evidence.menuExpandedScreenshot = path.join(
        SCREENSHOT_DIR,
        "02-mi-negocio-menu-expanded.png",
      );

      // Step 3: Validate "Agregar Negocio" modal
      await clickAndWait(
        page,
        sidebar.getByText(textRegex("Agregar Negocio"), { exact: false }).first(),
      );
      const modalControls = await assertModal(page);
      await takeCheckpoint(page, "03-agregar-negocio-modal", true);
      evidence.modalScreenshot = path.join(
        SCREENSHOT_DIR,
        "03-agregar-negocio-modal.png",
      );
      await modalControls.field.fill("Negocio Prueba Automatización");
      await clickAndWait(page, modalControls.cancelButton);
      await expect(modalControls.dialog).toBeHidden({ timeout: 15000 }).catch(() => {});
      report["Agregar Negocio modal"] = "PASS";

      // Step 4: Open Administrar Negocios and validate sections
      const adminOption = sidebar
        .getByText(textRegex("Administrar Negocios"), { exact: false })
        .first();
      if (!(await adminOption.isVisible().catch(() => false))) {
        await openMiNegocio(page, sidebar);
      }
      await clickAndWait(page, adminOption);
      await expect(
        page.getByText(textRegex("Información General"), { exact: false }),
      ).toBeVisible({ timeout: 30000 });
      await expect(
        page.getByText(textRegex("Detalles de la Cuenta"), { exact: false }),
      ).toBeVisible();
      await expect(
        page.getByText(textRegex("Tus Negocios"), { exact: false }),
      ).toBeVisible();
      await expect(
        page.getByText(textRegex("Sección Legal"), { exact: false }),
      ).toBeVisible();
      report["Administrar Negocios view"] = "PASS";
      await takeCheckpoint(page, "04-administrar-negocios-view", true);
      evidence.accountPageScreenshot = path.join(
        SCREENSHOT_DIR,
        "04-administrar-negocios-view.png",
      );

      // Step 5: Validate Información General
      const infoSection = page
        .locator("section,div")
        .filter({ hasText: textRegex("Información General") })
        .first();
      await expect(
        infoSection.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/),
      ).toBeVisible();
      await expect(
        infoSection.getByText(textRegex("BUSINESS PLAN"), { exact: false }),
      ).toBeVisible();
      await expect(
        infoSection.getByRole("button", { name: textRegex("Cambiar Plan") }),
      ).toBeVisible();
      report["Información General"] = "PASS";

      // Step 6: Validate Detalles de la Cuenta
      const detailsSection = page
        .locator("section,div")
        .filter({ hasText: textRegex("Detalles de la Cuenta") })
        .first();
      await expect(
        detailsSection.getByText(textRegex("Cuenta creada"), { exact: false }),
      ).toBeVisible();
      await expect(
        detailsSection.getByText(textRegex("Estado activo"), { exact: false }),
      ).toBeVisible();
      await expect(
        detailsSection.getByText(textRegex("Idioma seleccionado"), { exact: false }),
      ).toBeVisible();
      report["Detalles de la Cuenta"] = "PASS";

      // Step 7: Validate Tus Negocios
      const businessesSection = page
        .locator("section,div")
        .filter({ hasText: textRegex("Tus Negocios") })
        .first();
      await expect(businessesSection).toBeVisible();
      await expect(
        businessesSection.getByRole("button", {
          name: textRegex("Agregar Negocio"),
        }),
      ).toBeVisible();
      await expect(
        businessesSection.getByText(textRegex("Tienes 2 de 3 negocios"), {
          exact: false,
        }),
      ).toBeVisible();
      report["Tus Negocios"] = "PASS";

      // Step 8: Validate Términos y Condiciones
      const termsResult = await clickMaybeInNewTab(
        page,
        textRegex("Términos y Condiciones"),
      );
      const termsUrl = await assertLegalPage(
        termsResult.page,
        "Términos y Condiciones",
        "05-terminos-y-condiciones",
      );
      evidence.termsUrl = termsUrl;
      report["Términos y Condiciones"] = "PASS";
      if (termsResult.openedNewTab) {
        await termsResult.page.close();
        await page.bringToFront();
        await waitForUi(page);
      }

      // Step 9: Validate Política de Privacidad
      const privacyResult = await clickMaybeInNewTab(
        page,
        textRegex("Política de Privacidad"),
      );
      const privacyUrl = await assertLegalPage(
        privacyResult.page,
        "Política de Privacidad",
        "06-politica-de-privacidad",
      );
      evidence.privacyUrl = privacyUrl;
      report["Política de Privacidad"] = "PASS";
      if (privacyResult.openedNewTab) {
        await privacyResult.page.close();
        await page.bringToFront();
        await waitForUi(page);
      }
    } catch (error) {
      failure = error;
    } finally {
      // Step 10: Final report evidence in test output
      test.info().annotations.push({
        type: "workflow-report",
        description: JSON.stringify({ report, evidence }, null, 2),
      });
      console.log(
        "Final workflow report:",
        JSON.stringify({ report, evidence }, null, 2),
      );
    }

    if (failure) {
      throw failure;
    }
  });
});
