const fs = require("node:fs");
const { test, expect } = require("@playwright/test");

const ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const REQUIRED_BUSINESS_QUOTA_TEXT = "Tienes 2 de 3 negocios";

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informaci\u00f3n General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "T\u00e9rminos y Condiciones",
  "Pol\u00edtica de Privacidad"
];

async function takeCheckpoint(page, testInfo, fileName, fullPage = false) {
  const path = testInfo.outputPath(fileName);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(fileName, {
    path,
    contentType: "image/png"
  });
}

async function waitAfterClick(page) {
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function findFirstVisible(candidates) {
  for (const candidate of candidates) {
    const first = candidate.first();
    if (await first.isVisible().catch(() => false)) {
      return first;
    }
  }

  return null;
}

async function clickVisibleText(page, candidates) {
  const locator = await findFirstVisible(candidates);
  if (!locator) {
    throw new Error(`Unable to find any visible candidate: ${candidates.map((candidate) => candidate.toString()).join(", ")}`);
  }

  await locator.click();
  await waitAfterClick(page);
}

async function openLegalDocumentAndValidate({
  appPage,
  linkTextRegex,
  headingRegex,
  screenshotFileName,
  reportKey,
  legalUrls,
  testInfo
}) {
  const legalLink = await findFirstVisible([
    appPage.getByRole("link", { name: linkTextRegex }),
    appPage.getByText(linkTextRegex)
  ]);

  if (!legalLink) {
    throw new Error(`Legal link not found for ${reportKey}.`);
  }

  let externalPage = null;

  await Promise.all([
    appPage.context().waitForEvent("page", { timeout: 7000 }).then((newPage) => {
      externalPage = newPage;
    }).catch(() => {}),
    legalLink.click()
  ]);

  const targetPage = externalPage || appPage;
  await targetPage.waitForLoadState("domcontentloaded");
  await targetPage.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {});

  const heading = await findFirstVisible([
    targetPage.getByRole("heading", { name: headingRegex }),
    targetPage.getByText(headingRegex)
  ]);

  if (!heading) {
    throw new Error(`Heading not found for ${reportKey}.`);
  }

  await expect(heading).toBeVisible();

  const legalContent = targetPage.locator("main, article, section, p").filter({ hasText: /./ }).first();
  await expect(legalContent).toBeVisible();

  await takeCheckpoint(targetPage, testInfo, screenshotFileName, true);
  legalUrls[reportKey] = targetPage.url();

  if (externalPage) {
    await externalPage.close();
    await appPage.bringToFront();
    await appPage.waitForLoadState("domcontentloaded");
    await appPage.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {});
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await appPage.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {});
  }
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const loginUrl = process.env.SALEADS_LOGIN_URL;
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const failures = [];
  const legalUrls = {};

  const executeStep = async (stepName, fn) => {
    try {
      await test.step(stepName, fn);
      report[stepName] = "PASS";
    } catch (error) {
      report[stepName] = "FAIL";
      failures.push(`[${stepName}] ${error instanceof Error ? error.message : String(error)}`);
    }
  };

  if (loginUrl) {
    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    await page.waitForLoadState("networkidle", { timeout: 12000 }).catch(() => {});
  }

  await executeStep("Login", async () => {
    if (!loginUrl && page.url() === "about:blank") {
      throw new Error(
        "Set SALEADS_LOGIN_URL for automated execution. The test remains environment-agnostic and does not hardcode domain values."
      );
    }

    const loginButton = await findFirstVisible([
      page.getByRole("button", { name: /sign in with google|iniciar sesi[o\u00f3]n con google|continuar con google|google/i }),
      page.getByText(/sign in with google|iniciar sesi[o\u00f3]n con google|continuar con google/i)
    ]);

    if (!loginButton) {
      throw new Error("Google login button not found.");
    }

    let popup = null;

    await Promise.all([
      page.waitForEvent("popup", { timeout: 7000 }).then((newPage) => {
        popup = newPage;
      }).catch(() => {}),
      loginButton.click()
    ]);

    const authPage = popup || page;
    await authPage.waitForLoadState("domcontentloaded");
    await waitAfterClick(authPage);

    const accountOption = await findFirstVisible([
      authPage.getByRole("button", { name: new RegExp(ACCOUNT_EMAIL, "i") }),
      authPage.getByText(new RegExp(ACCOUNT_EMAIL, "i"))
    ]);

    if (accountOption) {
      await accountOption.click();
      await waitAfterClick(authPage);
    }

    if (popup) {
      await popup.waitForEvent("close", { timeout: 20000 }).catch(() => {});
    }

    const mainInterface = await findFirstVisible([
      page.getByRole("navigation"),
      page.locator("aside"),
      page.getByText(/dashboard|panel|inicio|home/i)
    ]);

    if (!mainInterface) {
      throw new Error("Main application interface not detected after login.");
    }

    const leftSidebar = await findFirstVisible([
      page.locator("aside"),
      page.getByRole("navigation")
    ]);

    if (!leftSidebar) {
      throw new Error("Left sidebar navigation is not visible after login.");
    }

    await expect(leftSidebar).toBeVisible();
    await takeCheckpoint(page, testInfo, "checkpoint-01-dashboard-loaded.png");
  });

  await executeStep("Mi Negocio menu", async () => {
    await clickVisibleText(page, [
      page.getByRole("button", { name: /negocio/i }),
      page.getByRole("link", { name: /negocio/i }),
      page.getByText(/^Negocio$/i)
    ]);

    await clickVisibleText(page, [
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i)
    ]);

    await expect(page.getByText("Agregar Negocio")).toBeVisible();
    await expect(page.getByText("Administrar Negocios")).toBeVisible();
    await takeCheckpoint(page, testInfo, "checkpoint-02-mi-negocio-menu-expanded.png");
  });

  await executeStep("Agregar Negocio modal", async () => {
    await clickVisibleText(page, [
      page.getByRole("button", { name: /agregar negocio/i }),
      page.getByRole("link", { name: /agregar negocio/i }),
      page.getByText(/^Agregar Negocio$/i)
    ]);

    await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeVisible();

    const businessNameInput = await findFirstVisible([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input[name*='negocio' i], input[id*='negocio' i]").first()
    ]);

    if (!businessNameInput) {
      throw new Error("'Nombre del Negocio' input field not found.");
    }

    await expect(businessNameInput).toBeVisible();
    await expect(page.getByText(REQUIRED_BUSINESS_QUOTA_TEXT)).toBeVisible();
    await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible();
    await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible();
    await takeCheckpoint(page, testInfo, "checkpoint-03-crear-negocio-modal.png");

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatizacion");
    await page.getByRole("button", { name: /cancelar/i }).click();
    await waitAfterClick(page);
    await expect(page.getByText(/Crear Nuevo Negocio/i)).toBeHidden();
  });

  await executeStep("Administrar Negocios view", async () => {
    if (!(await page.getByText("Administrar Negocios").first().isVisible().catch(() => false))) {
      await clickVisibleText(page, [
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i)
      ]);
    }

    await clickVisibleText(page, [
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i)
    ]);

    await expect(page.getByText(/Informaci[o\u00f3]n General/i)).toBeVisible();
    await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
    await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
    await expect(page.getByText(/Secci[o\u00f3]n Legal/i)).toBeVisible();
    await takeCheckpoint(page, testInfo, "checkpoint-04-account-page.png", true);
  });

  await executeStep("Informaci\u00f3n General", async () => {
    const userIdentity = await findFirstVisible([
      page.locator("[data-testid*='name' i], [data-testid*='user' i]").first(),
      page.locator("text=/@/").first()
    ]);

    if (!userIdentity) {
      throw new Error("User name or identity block was not found.");
    }

    const userEmail = page.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/").first();
    await expect(userIdentity).toBeVisible();
    await expect(userEmail).toBeVisible();
    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(page.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
  });

  await executeStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(page.getByText(/Estado activo/i)).toBeVisible();
    await expect(page.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await executeStep("Tus Negocios", async () => {
    const businessList = await findFirstVisible([
      page.getByText(/Tus Negocios/i),
      page.locator("[data-testid*='business' i], [class*='business' i]").first()
    ]);

    if (!businessList) {
      throw new Error("Business list section is not visible.");
    }

    await expect(page.getByText("Agregar Negocio")).toBeVisible();
    await expect(page.getByText(REQUIRED_BUSINESS_QUOTA_TEXT)).toBeVisible();
  });

  await executeStep("T\u00e9rminos y Condiciones", async () => {
    await openLegalDocumentAndValidate({
      appPage: page,
      linkTextRegex: /T[e\u00e9]rminos y Condiciones/i,
      headingRegex: /T[e\u00e9]rminos y Condiciones/i,
      screenshotFileName: "checkpoint-05-terminos-y-condiciones.png",
      reportKey: "T\u00e9rminos y Condiciones",
      legalUrls,
      testInfo
    });
  });

  await executeStep("Pol\u00edtica de Privacidad", async () => {
    await openLegalDocumentAndValidate({
      appPage: page,
      linkTextRegex: /Pol[i\u00ed]tica de Privacidad/i,
      headingRegex: /Pol[i\u00ed]tica de Privacidad/i,
      screenshotFileName: "checkpoint-06-politica-de-privacidad.png",
      reportKey: "Pol\u00edtica de Privacidad",
      legalUrls,
      testInfo
    });
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    summary: report,
    legalUrls,
    failures
  };

  const reportPath = testInfo.outputPath("saleads-mi-negocio-final-report.json");
  fs.writeFileSync(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("saleads-mi-negocio-final-report", {
    path: reportPath,
    contentType: "application/json"
  });

  if (failures.length > 0) {
    throw new Error(`One or more workflow validations failed.\n${failures.join("\n")}`);
  }
});
