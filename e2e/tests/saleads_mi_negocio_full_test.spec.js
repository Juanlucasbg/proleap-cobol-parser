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

async function waitForUiToLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(800);
}

async function findVisible(candidates, description, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const candidate of candidates) {
      const locator = candidate.first();
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  throw new Error(`Could not find visible element: ${description}`);
}

async function runValidation(report, failures, stepName, fn) {
  try {
    await fn();
    report[stepName] = "PASS";
  } catch (error) {
    report[stepName] = "FAIL";
    failures.push(
      `${stepName}: ${error instanceof Error ? error.message : String(error)}`,
    );
  }
}

async function sectionTextByHeading(page, headingRegex) {
  const heading = await findVisible(
    [
      page.getByRole("heading", { name: headingRegex }),
      page.getByText(headingRegex),
    ],
    `section heading ${headingRegex}`,
    20000,
  );

  const section = heading.locator("xpath=ancestor::section[1]").first();
  if (await section.isVisible().catch(() => false)) {
    return section.innerText();
  }

  const container = heading
    .locator("xpath=ancestor::*[self::div or self::article][1]")
    .first();
  return container.innerText();
}

async function openLegalPageAndReturn({
  page,
  context,
  linkRegex,
  headingRegex,
  screenshotName,
  testInfo,
}) {
  const appUrlBeforeClick = page.url();
  const link = await findVisible(
    [
      page.getByRole("link", { name: linkRegex }),
      page.getByText(linkRegex),
      page.getByRole("button", { name: linkRegex }),
    ],
    `legal link ${linkRegex}`,
  );

  const newTabPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
  await link.click();

  let legalPage = await newTabPromise;
  if (legalPage) {
    await legalPage.waitForLoadState("domcontentloaded");
  } else {
    legalPage = page;
    await waitForUiToLoad(legalPage);
  }

  const heading = await findVisible(
    [
      legalPage.getByRole("heading", { name: headingRegex }),
      legalPage.getByText(headingRegex),
    ],
    `legal heading ${headingRegex}`,
    20000,
  );
  await expect(heading).toBeVisible();

  const legalContent = await findVisible(
    [legalPage.locator("main"), legalPage.locator("article"), legalPage.locator("body")],
    "legal content container",
  );
  await expect(legalContent).toBeVisible();

  const finalUrl = legalPage.url();
  await legalPage.screenshot({
    path: testInfo.outputPath(screenshotName),
    fullPage: true,
  });

  if (legalPage !== page) {
    await legalPage.close();
    await page.bringToFront();
    await waitForUiToLoad(page);
  } else {
    await page
      .goBack({ waitUntil: "domcontentloaded" })
      .catch(async () => page.goto(appUrlBeforeClick, { waitUntil: "domcontentloaded" }));
    await waitForUiToLoad(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = Object.fromEntries(REPORT_FIELDS.map((name) => [name, "FAIL"]));
  const failures = [];
  const evidence = {
    terminosUrl: null,
    privacidadUrl: null,
  };

  const startUrl =
    process.env.SALEADS_URL ||
    process.env.SALEADS_LOGIN_URL ||
    process.env.BASE_URL ||
    "";

  const missingStartContext = !startUrl && page.url() === "about:blank";
  if (startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
  }

  await runValidation(report, failures, "Login", async () => {
    if (missingStartContext) {
      throw new Error(
        "Provide SALEADS_URL (or SALEADS_LOGIN_URL / BASE_URL) when running headless automation.",
      );
    }

    const googleLoginButton = await findVisible(
      [
        page.getByRole("button", {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
        }),
        page.getByRole("link", {
          name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i,
        }),
        page.getByText(
          /sign in with google|iniciar sesi[oó]n con google|continuar con google/i,
        ),
      ],
      "Google login button",
      30000,
    );

    const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await googleLoginButton.click();
    const popup = await popupPromise;

    const authPage = popup || page;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
    } else {
      await waitForUiToLoad(authPage);
    }

    const accountCandidate = authPage
      .getByText("juanlucasbarbiergarzon@gmail.com", { exact: false })
      .first();
    if (await accountCandidate.isVisible().catch(() => false)) {
      await accountCandidate.click();
    }

    if (popup) {
      await popup.waitForEvent("close", { timeout: 20000 }).catch(() => undefined);
      await page.bringToFront();
    }

    await waitForUiToLoad(page);
    await expect(page).not.toHaveURL(/accounts\.google\.com/);

    const sidebar = await findVisible(
      [page.getByRole("navigation"), page.locator("aside"), page.locator("nav")],
      "left sidebar navigation",
      30000,
    );
    await expect(sidebar).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath("01-dashboard-loaded.png"),
      fullPage: true,
    });
  });

  await runValidation(report, failures, "Mi Negocio menu", async () => {
    const negocioSection = await findVisible(
      [page.getByText(/^Negocio$/i), page.getByText(/Negocio/i)],
      "Negocio section",
    );
    await expect(negocioSection).toBeVisible();

    const miNegocio = await findVisible(
      [
        page.getByRole("button", { name: /Mi Negocio/i }),
        page.getByRole("link", { name: /Mi Negocio/i }),
        page.getByText(/Mi Negocio/i),
      ],
      "Mi Negocio option",
    );
    await miNegocio.click();
    await waitForUiToLoad(page);

    await expect(
      await findVisible(
        [
          page.getByRole("menuitem", { name: /Agregar Negocio/i }),
          page.getByRole("button", { name: /Agregar Negocio/i }),
          page.getByRole("link", { name: /Agregar Negocio/i }),
          page.getByText(/Agregar Negocio/i),
        ],
        "Agregar Negocio submenu option",
      ),
    ).toBeVisible();

    await expect(
      await findVisible(
        [
          page.getByRole("menuitem", { name: /Administrar Negocios/i }),
          page.getByRole("button", { name: /Administrar Negocios/i }),
          page.getByRole("link", { name: /Administrar Negocios/i }),
          page.getByText(/Administrar Negocios/i),
        ],
        "Administrar Negocios submenu option",
      ),
    ).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath("02-mi-negocio-expanded.png"),
      fullPage: true,
    });
  });

  await runValidation(report, failures, "Agregar Negocio modal", async () => {
    const addBusiness = await findVisible(
      [
        page.getByRole("menuitem", { name: /Agregar Negocio/i }),
        page.getByRole("button", { name: /Agregar Negocio/i }),
        page.getByRole("link", { name: /Agregar Negocio/i }),
        page.getByText(/Agregar Negocio/i),
      ],
      "Agregar Negocio entry",
    );
    await addBusiness.click();
    await waitForUiToLoad(page);

    const modalTitle = await findVisible(
      [page.getByRole("heading", { name: /Crear Nuevo Negocio/i }), page.getByText(/Crear Nuevo Negocio/i)],
      "Crear Nuevo Negocio modal title",
    );
    await expect(modalTitle).toBeVisible();

    const businessNameInput = await findVisible(
      [
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.locator("input"),
      ],
      "Nombre del Negocio input",
    );
    await expect(businessNameInput).toBeVisible();

    await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
    await expect(
      await findVisible([page.getByRole("button", { name: /Cancelar/i }), page.getByText(/^Cancelar$/i)], "Cancelar button"),
    ).toBeVisible();
    await expect(
      await findVisible(
        [page.getByRole("button", { name: /Crear Negocio/i }), page.getByText(/Crear Negocio/i)],
        "Crear Negocio button",
      ),
    ).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath("03-crear-negocio-modal.png"),
      fullPage: true,
    });

    await businessNameInput.fill("Negocio Prueba Automatización");
    const cancelButton = await findVisible(
      [page.getByRole("button", { name: /Cancelar/i }), page.getByText(/^Cancelar$/i)],
      "Cancelar modal button",
    );
    await cancelButton.click();
    await expect(modalTitle).toBeHidden({ timeout: 10000 });
    await waitForUiToLoad(page);
  });

  await runValidation(report, failures, "Administrar Negocios view", async () => {
    const adminOptionVisible = await page
      .getByText(/Administrar Negocios/i)
      .first()
      .isVisible()
      .catch(() => false);

    if (!adminOptionVisible) {
      const miNegocio = await findVisible(
        [
          page.getByRole("button", { name: /Mi Negocio/i }),
          page.getByRole("link", { name: /Mi Negocio/i }),
          page.getByText(/Mi Negocio/i),
        ],
        "Mi Negocio option for re-expansion",
      );
      await miNegocio.click();
      await waitForUiToLoad(page);
    }

    const adminOption = await findVisible(
      [
        page.getByRole("menuitem", { name: /Administrar Negocios/i }),
        page.getByRole("button", { name: /Administrar Negocios/i }),
        page.getByRole("link", { name: /Administrar Negocios/i }),
        page.getByText(/Administrar Negocios/i),
      ],
      "Administrar Negocios entry",
    );
    await adminOption.click();
    await waitForUiToLoad(page);

    await expect(
      await findVisible([page.getByText(/Informaci[oó]n General/i)], "Información General section heading"),
    ).toBeVisible();
    await expect(
      await findVisible([page.getByText(/Detalles de la Cuenta/i)], "Detalles de la Cuenta section heading"),
    ).toBeVisible();
    await expect(await findVisible([page.getByText(/Tus Negocios/i)], "Tus Negocios section heading")).toBeVisible();
    await expect(
      await findVisible([page.getByText(/Secci[oó]n Legal/i), page.getByText(/Legal/i)], "Sección Legal heading"),
    ).toBeVisible();

    await page.screenshot({
      path: testInfo.outputPath("04-administrar-negocios-view.png"),
      fullPage: true,
    });
  });

  await runValidation(report, failures, "Información General", async () => {
    const infoText = await sectionTextByHeading(page, /Informaci[oó]n General/i);

    const hasEmail = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/.test(infoText);
    const hasLikelyName = /[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}/.test(infoText);

    expect(hasLikelyName, "User name should be visible").toBeTruthy();
    expect(hasEmail, "User email should be visible").toBeTruthy();
    expect(/BUSINESS PLAN/i.test(infoText), "BUSINESS PLAN text should be visible").toBeTruthy();
    expect(/Cambiar Plan/i.test(infoText), "Cambiar Plan button text should be visible").toBeTruthy();
  });

  await runValidation(report, failures, "Detalles de la Cuenta", async () => {
    const detailsText = await sectionTextByHeading(page, /Detalles de la Cuenta/i);

    expect(/Cuenta creada/i.test(detailsText), "'Cuenta creada' should be visible").toBeTruthy();
    expect(/Estado\s*activo|Estado.*Activo/i.test(detailsText), "'Estado activo' should be visible").toBeTruthy();
    expect(
      /Idioma seleccionado|Idioma.*seleccionado/i.test(detailsText),
      "'Idioma seleccionado' should be visible",
    ).toBeTruthy();
  });

  await runValidation(report, failures, "Tus Negocios", async () => {
    const businessText = await sectionTextByHeading(page, /Tus Negocios/i);

    expect(/Agregar Negocio/i.test(businessText), "Agregar Negocio should exist in Tus Negocios").toBeTruthy();
    expect(/Tienes 2 de 3 negocios/i.test(businessText), "Business usage text should be visible").toBeTruthy();
    expect(
      /Negocio|Business|Lista|Administrar/i.test(businessText),
      "Business list content should be visible",
    ).toBeTruthy();
  });

  await runValidation(report, failures, "Términos y Condiciones", async () => {
    evidence.terminosUrl = await openLegalPageAndReturn({
      page,
      context,
      linkRegex: /T[ée]rminos y Condiciones/i,
      headingRegex: /T[ée]rminos y Condiciones/i,
      screenshotName: "05-terminos-y-condiciones.png",
      testInfo,
    });
  });

  await runValidation(report, failures, "Política de Privacidad", async () => {
    evidence.privacidadUrl = await openLegalPageAndReturn({
      page,
      context,
      linkRegex: /Pol[íi]tica de Privacidad/i,
      headingRegex: /Pol[íi]tica de Privacidad/i,
      screenshotName: "06-politica-de-privacidad.png",
      testInfo,
    });
  });

  const finalReport = {
    ...report,
    evidence,
  };

  await testInfo.attach("final-report", {
    body: JSON.stringify(finalReport, null, 2),
    contentType: "application/json",
  });

  console.log("FINAL REPORT (saleads_mi_negocio_full_test)");
  for (const field of REPORT_FIELDS) {
    console.log(`${field}: ${report[field]}`);
  }
  if (evidence.terminosUrl) {
    console.log(`Términos y Condiciones URL final: ${evidence.terminosUrl}`);
  }
  if (evidence.privacidadUrl) {
    console.log(`Política de Privacidad URL final: ${evidence.privacidadUrl}`);
  }

  const failedSteps = REPORT_FIELDS.filter((field) => report[field] !== "PASS");
  expect(
    failedSteps,
    `The following validations failed:\n${failures.length ? failures.join("\n") : "(none)"}`,
  ).toEqual([]);
});
