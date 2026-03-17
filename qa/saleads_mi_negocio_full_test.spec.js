const { test, expect } = require("@playwright/test");

const UI_TIMEOUT_MS = 20000;
const POST_CLICK_STABILIZE_MS = 900;

function ownerPage(scope) {
  return typeof scope.waitForTimeout === "function" ? scope : scope.page();
}

async function findFirstVisible(scope, candidateBuilders, timeoutMs = UI_TIMEOUT_MS) {
  const startedAt = Date.now();
  const page = ownerPage(scope);

  while (Date.now() - startedAt < timeoutMs) {
    for (const buildCandidate of candidateBuilders) {
      const candidate = buildCandidate(scope).first();
      const isVisible = await candidate.isVisible().catch(() => false);
      if (isVisible) {
        return candidate;
      }
    }

    await page.waitForTimeout(250);
  }

  throw new Error("Unable to find a visible element for the requested candidates.");
}

async function clickAndWaitForUi(page, locator) {
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click({ timeout: UI_TIMEOUT_MS });
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForTimeout(POST_CLICK_STABILIZE_MS);
}

async function checkpoint(page, testInfo, filename, fullPage = false) {
  await testInfo.attach(filename, {
    body: await page.screenshot({ fullPage }),
    contentType: "image/png",
  });
}

async function openLegalPageAndReturn({
  page,
  context,
  testInfo,
  linkPattern,
  headingPattern,
  screenshotName,
}) {
  const link = await findFirstVisible(page, [
    (scope) => scope.getByRole("link", { name: linkPattern }),
    (scope) => scope.getByText(linkPattern),
  ]);

  const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  await clickAndWaitForUi(page, link);
  const popup = await popupPromise;

  const legalPage = popup || page;
  await legalPage.waitForLoadState("domcontentloaded").catch(() => {});

  const heading = await findFirstVisible(legalPage, [
    (scope) => scope.getByRole("heading", { name: headingPattern }),
    (scope) => scope.getByText(headingPattern),
  ]);
  await expect(heading).toBeVisible();

  const legalBodyText = (await legalPage.locator("body").innerText()).trim();
  expect(legalBodyText.length).toBeGreaterThan(150);

  await checkpoint(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await page.waitForTimeout(POST_CLICK_STABILIZE_MS);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = {
    Login: "FAIL",
    "Mi Negocio menu": "FAIL",
    "Agregar Negocio modal": "FAIL",
    "Administrar Negocios view": "FAIL",
    "Informacion General": "FAIL",
    "Detalles de la Cuenta": "FAIL",
    "Tus Negocios": "FAIL",
    "Terminos y Condiciones": "FAIL",
    "Politica de Privacidad": "FAIL",
  };

  const evidence = {
    terminosUrl: "N/A",
    politicaPrivacidadUrl: "N/A",
  };

  const failures = [];
  const startUrl =
    process.env.SALEADS_START_URL || process.env.SALEADS_LOGIN_URL || process.env.BASE_URL || "";

  if (startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
  } else {
    // Keeps compatibility with runners that pre-open the login page.
    await page.waitForLoadState("domcontentloaded").catch(() => {});
  }

  const runStep = async (stepName, work) => {
    try {
      await work();
      report[stepName] = "PASS";
    } catch (error) {
      report[stepName] = "FAIL";
      failures.push(`${stepName}: ${error.message}`);
    }
  };

  await runStep("Login", async () => {
    const loginButton = await findFirstVisible(page, [
      (scope) =>
        scope.getByRole("button", {
          name: /sign in with google|iniciar sesi[o\u00f3]n con google|continuar con google|google/i,
        }),
      (scope) =>
        scope.getByRole("link", {
          name: /sign in with google|iniciar sesi[o\u00f3]n con google|continuar con google|google/i,
        }),
      (scope) => scope.locator("button:has-text('Google')"),
    ]);

    const googlePopupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await clickAndWaitForUi(page, loginButton);
    const googlePopup = await googlePopupPromise;

    if (googlePopup) {
      await googlePopup.waitForLoadState("domcontentloaded").catch(() => {});
      const accountChoice = await findFirstVisible(googlePopup, [
        (scope) => scope.getByText("juanlucasbarbiergarzon@gmail.com"),
        (scope) => scope.locator("div[role='link']:has-text('juanlucasbarbiergarzon@gmail.com')"),
      ]).catch(() => null);

      if (accountChoice) {
        await clickAndWaitForUi(googlePopup, accountChoice);
      }

      await googlePopup.waitForClose({ timeout: 30000 }).catch(() => {});
    } else {
      const accountChoiceSameTab = await findFirstVisible(page, [
        (scope) => scope.getByText("juanlucasbarbiergarzon@gmail.com"),
      ], 4000).catch(() => null);

      if (accountChoiceSameTab) {
        await clickAndWaitForUi(page, accountChoiceSameTab);
      }
    }

    const sidebar = await findFirstVisible(page, [
      (scope) => scope.locator("aside"),
      (scope) => scope.getByRole("navigation"),
      (scope) => scope.locator("nav"),
    ], 30000);
    await expect(sidebar).toBeVisible();

    await checkpoint(page, testInfo, "01-dashboard-loaded.png", true);
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = await findFirstVisible(page, [
      (scope) => scope.getByRole("button", { name: /negocio/i }),
      (scope) => scope.getByRole("link", { name: /negocio/i }),
      (scope) => scope.getByText(/negocio/i),
    ]);
    await clickAndWaitForUi(page, negocioSection);

    const miNegocioOption = await findFirstVisible(page, [
      (scope) => scope.getByRole("button", { name: /mi negocio/i }),
      (scope) => scope.getByRole("link", { name: /mi negocio/i }),
      (scope) => scope.getByText(/mi negocio/i),
    ]);
    await clickAndWaitForUi(page, miNegocioOption);

    const agregarNegocio = await findFirstVisible(page, [
      (scope) => scope.getByRole("button", { name: /agregar negocio/i }),
      (scope) => scope.getByRole("link", { name: /agregar negocio/i }),
      (scope) => scope.getByText(/agregar negocio/i),
    ]);
    const administrarNegocios = await findFirstVisible(page, [
      (scope) => scope.getByRole("button", { name: /administrar negocios/i }),
      (scope) => scope.getByRole("link", { name: /administrar negocios/i }),
      (scope) => scope.getByText(/administrar negocios/i),
    ]);

    await expect(agregarNegocio).toBeVisible();
    await expect(administrarNegocios).toBeVisible();
    await checkpoint(page, testInfo, "02-mi-negocio-menu-expanded.png");
  });

  await runStep("Agregar Negocio modal", async () => {
    const agregarNegocio = await findFirstVisible(page, [
      (scope) => scope.getByRole("button", { name: /^agregar negocio$/i }),
      (scope) => scope.getByRole("link", { name: /^agregar negocio$/i }),
      (scope) => scope.getByText(/^agregar negocio$/i),
    ]);
    await clickAndWaitForUi(page, agregarNegocio);

    const modal = await findFirstVisible(page, [
      (scope) => scope.getByRole("dialog"),
      (scope) => scope.locator("[role='dialog']"),
    ]);
    await expect(modal).toBeVisible();

    await expect(
      await findFirstVisible(modal, [(scope) => scope.getByText(/crear nuevo negocio/i)])
    ).toBeVisible();
    await expect(
      await findFirstVisible(modal, [
        (scope) => scope.getByLabel(/nombre del negocio/i),
        (scope) => scope.getByPlaceholder(/nombre del negocio/i),
      ])
    ).toBeVisible();
    await expect(
      await findFirstVisible(modal, [(scope) => scope.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)])
    ).toBeVisible();
    await expect(
      await findFirstVisible(modal, [(scope) => scope.getByRole("button", { name: /cancelar/i })])
    ).toBeVisible();
    await expect(
      await findFirstVisible(modal, [(scope) => scope.getByRole("button", { name: /crear negocio/i })])
    ).toBeVisible();

    const nombreNegocio = await findFirstVisible(modal, [
      (scope) => scope.getByLabel(/nombre del negocio/i),
      (scope) => scope.getByPlaceholder(/nombre del negocio/i),
    ]);
    await nombreNegocio.click();
    await nombreNegocio.fill("Negocio Prueba Automatizacion");
    await checkpoint(page, testInfo, "03-agregar-negocio-modal.png");

    const cancelar = await findFirstVisible(modal, [
      (scope) => scope.getByRole("button", { name: /cancelar/i }),
    ]);
    await clickAndWaitForUi(page, cancelar);
  });

  await runStep("Administrar Negocios view", async () => {
    const miNegocioOption = await findFirstVisible(page, [
      (scope) => scope.getByRole("button", { name: /mi negocio/i }),
      (scope) => scope.getByRole("link", { name: /mi negocio/i }),
      (scope) => scope.getByText(/mi negocio/i),
    ]).catch(() => null);

    if (miNegocioOption) {
      await clickAndWaitForUi(page, miNegocioOption);
    }

    const administrarNegocios = await findFirstVisible(page, [
      (scope) => scope.getByRole("button", { name: /administrar negocios/i }),
      (scope) => scope.getByRole("link", { name: /administrar negocios/i }),
      (scope) => scope.getByText(/administrar negocios/i),
    ]);
    await clickAndWaitForUi(page, administrarNegocios);

    await expect(await findFirstVisible(page, [(scope) => scope.getByText(/informaci[o\u00f3]n general/i)])).toBeVisible();
    await expect(await findFirstVisible(page, [(scope) => scope.getByText(/detalles de la cuenta/i)])).toBeVisible();
    await expect(await findFirstVisible(page, [(scope) => scope.getByText(/tus negocios/i)])).toBeVisible();
    await expect(await findFirstVisible(page, [(scope) => scope.getByText(/secci[o\u00f3]n legal/i)])).toBeVisible();

    await checkpoint(page, testInfo, "04-administrar-negocios-view.png", true);
  });

  await runStep("Informacion General", async () => {
    await expect(await findFirstVisible(page, [(scope) => scope.getByText(/informaci[o\u00f3]n general/i)])).toBeVisible();
    await expect(page.getByText(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/)).toBeVisible();
    await expect(await findFirstVisible(page, [(scope) => scope.getByText(/business plan/i)])).toBeVisible();
    await expect(await findFirstVisible(page, [(scope) => scope.getByRole("button", { name: /cambiar plan/i })])).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(await findFirstVisible(page, [(scope) => scope.getByText(/detalles de la cuenta/i)])).toBeVisible();
    await expect(await findFirstVisible(page, [(scope) => scope.getByText(/cuenta creada/i)])).toBeVisible();
    await expect(await findFirstVisible(page, [(scope) => scope.getByText(/estado activo/i)])).toBeVisible();
    await expect(await findFirstVisible(page, [(scope) => scope.getByText(/idioma seleccionado/i)])).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    await expect(await findFirstVisible(page, [(scope) => scope.getByText(/tus negocios/i)])).toBeVisible();
    await expect(await findFirstVisible(page, [(scope) => scope.getByRole("button", { name: /agregar negocio/i })])).toBeVisible();
    await expect(await findFirstVisible(page, [(scope) => scope.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)])).toBeVisible();
  });

  await runStep("Terminos y Condiciones", async () => {
    evidence.terminosUrl = await openLegalPageAndReturn({
      page,
      context,
      testInfo,
      linkPattern: /t[e\u00e9]rminos y condiciones/i,
      headingPattern: /t[e\u00e9]rminos y condiciones/i,
      screenshotName: "05-terminos-y-condiciones.png",
    });
  });

  await runStep("Politica de Privacidad", async () => {
    evidence.politicaPrivacidadUrl = await openLegalPageAndReturn({
      page,
      context,
      testInfo,
      linkPattern: /pol[i\u00ed]tica de privacidad/i,
      headingPattern: /pol[i\u00ed]tica de privacidad/i,
      screenshotName: "06-politica-de-privacidad.png",
    });
  });

  const finalReport = {
    ...report,
    terminosUrl: evidence.terminosUrl,
    politicaPrivacidadUrl: evidence.politicaPrivacidadUrl,
    failures,
  };

  await testInfo.attach("final-report.json", {
    body: Buffer.from(JSON.stringify(finalReport, null, 2), "utf8"),
    contentType: "application/json",
  });

  expect(failures, `Validation failures:\n${failures.join("\n")}`).toHaveLength(0);
});
