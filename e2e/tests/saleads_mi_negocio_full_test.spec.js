const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL || "juanlucasbarbiergarzon@gmail.com";

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

function buildInitialReport() {
  return Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
}

function normalizeText(value) {
  return (value || "").replace(/\s+/g, " ").trim();
}

function formatError(error) {
  if (!error) {
    return "Unknown error";
  }

  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

async function waitForAnyVisible(candidates, timeoutMs = 30000) {
  const startedAt = Date.now();

  while (Date.now() - startedAt < timeoutMs) {
    for (const candidate of candidates) {
      const locator = candidate.first();
      const isVisible = await locator.isVisible().catch(() => false);

      if (isVisible) {
        return locator;
      }
    }

    await new Promise((resolve) => setTimeout(resolve, 350));
  }

  throw new Error("None of the expected UI elements became visible in time.");
}

async function clickAndWait(page, locator) {
  await locator.click();
  await page.waitForLoadState("domcontentloaded").catch(() => {});
  await page.waitForTimeout(800);
}

async function captureCheckpoint(page, testInfo, fileName, fullPage = false) {
  await page.screenshot({
    path: testInfo.outputPath(fileName),
    fullPage
  });
}

async function runValidationStep(stepName, report, failures, fn) {
  try {
    await test.step(stepName, fn);
    report[stepName] = "PASS";
  } catch (error) {
    report[stepName] = "FAIL";
    failures.push(`${stepName}: ${formatError(error)}`);
  }
}

async function selectGoogleAccountIfVisible(targetPage) {
  const accountOption = await waitForAnyVisible(
    [
      targetPage.getByText(GOOGLE_ACCOUNT_EMAIL, { exact: true }),
      targetPage.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
      targetPage.locator(`[data-email="${GOOGLE_ACCOUNT_EMAIL}"]`)
    ],
    9000
  ).catch(() => null);

  if (accountOption) {
    await accountOption.click();
    await targetPage.waitForLoadState("domcontentloaded").catch(() => {});
    await targetPage.waitForLoadState("networkidle").catch(() => {});
  }
}

async function openLegalDocument({
  page,
  linkLocator,
  expectedHeadingRegex,
  screenshotName,
  testInfo
}) {
  const popupPromise = page.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);
  const navigationPromise = page.waitForNavigation({ timeout: 10000 }).catch(() => null);

  await linkLocator.click();
  await page.waitForTimeout(900);

  const popupPage = await popupPromise;
  let legalPage = page;
  let openedInPopup = false;

  if (popupPage) {
    legalPage = popupPage;
    openedInPopup = true;
    await legalPage.waitForLoadState("domcontentloaded");
  } else {
    await navigationPromise;
    await legalPage.waitForLoadState("domcontentloaded").catch(() => {});
  }

  await waitForAnyVisible(
    [
      legalPage.getByRole("heading", { name: expectedHeadingRegex }),
      legalPage.getByText(expectedHeadingRegex)
    ],
    30000
  );

  const legalBodyText = normalizeText(await legalPage.locator("body").innerText());
  if (legalBodyText.length < 80) {
    throw new Error("Legal page content appears too short or missing.");
  }

  await captureCheckpoint(legalPage, testInfo, screenshotName, true);
  const finalUrl = legalPage.url();

  if (openedInPopup) {
    await legalPage.close();
    await page.bringToFront();
    await page.waitForLoadState("domcontentloaded").catch(() => {});
  } else {
    await page.goBack().catch(() => {});
    await page.waitForLoadState("domcontentloaded").catch(() => {});
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = buildInitialReport();
  const failures = [];
  const evidence = {
    terminosYCondicionesUrl: "",
    politicaDePrivacidadUrl: ""
  };

  await runValidationStep("Login", report, failures, async () => {
    const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.BASE_URL;
    if (page.url() === "about:blank") {
      if (!loginUrl) {
        throw new Error(
          "Browser started on about:blank. Set SALEADS_LOGIN_URL (or BASE_URL) to the current environment login page."
        );
      }

      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
    }

    const signInButton = await waitForAnyVisible(
      [
        page.getByRole("button", { name: /sign in with google/i }),
        page.getByRole("button", { name: /google/i }),
        page.getByText(/sign in with google/i),
        page.getByText(/continuar con google/i)
      ],
      45000
    );

    const popupPromise = page.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickAndWait(page, signInButton);
    const popupPage = await popupPromise;

    if (popupPage) {
      await popupPage.waitForLoadState("domcontentloaded");
      await selectGoogleAccountIfVisible(popupPage);
      await popupPage.waitForEvent("close", { timeout: 30000 }).catch(() => {});
    } else {
      await selectGoogleAccountIfVisible(page);
    }

    await waitForAnyVisible(
      [
        page.locator("aside"),
        page.getByRole("navigation"),
        page.getByText(/mi negocio|negocio/i)
      ],
      60000
    );

    await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png", true);
  });

  await runValidationStep("Mi Negocio menu", report, failures, async () => {
    await waitForAnyVisible([page.locator("aside"), page.getByRole("navigation")], 30000);

    const negocioSection = await waitForAnyVisible(
      [
        page.getByRole("button", { name: /^Negocio$/i }),
        page.getByText(/^Negocio$/i),
        page.getByRole("link", { name: /^Negocio$/i })
      ],
      20000
    );
    await clickAndWait(page, negocioSection);

    const miNegocioOption = await waitForAnyVisible(
      [
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i)
      ],
      20000
    );
    await clickAndWait(page, miNegocioOption);

    await waitForAnyVisible(
      [
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByRole("link", { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i)
      ],
      20000
    );

    await waitForAnyVisible(
      [
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i)
      ],
      20000
    );

    await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png", true);
  });

  await runValidationStep("Agregar Negocio modal", report, failures, async () => {
    const agregarNegocioOption = await waitForAnyVisible(
      [
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByRole("link", { name: /agregar negocio/i }),
        page.getByText(/agregar negocio/i)
      ],
      20000
    );
    await clickAndWait(page, agregarNegocioOption);

    await waitForAnyVisible(
      [
        page.getByRole("heading", { name: /crear nuevo negocio/i }),
        page.getByText(/crear nuevo negocio/i)
      ],
      15000
    );

    const nombreNegocioInput = await waitForAnyVisible(
      [
        page.getByLabel(/nombre del negocio/i),
        page.getByPlaceholder(/nombre del negocio/i),
        page.locator("input").first()
      ],
      15000
    );

    await waitForAnyVisible([page.getByText(/tienes 2 de 3 negocios/i)], 15000);
    await waitForAnyVisible([page.getByRole("button", { name: /cancelar/i })], 15000);
    await waitForAnyVisible([page.getByRole("button", { name: /crear negocio/i })], 15000);

    await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png", true);

    await nombreNegocioInput.click();
    await page.waitForTimeout(350);
    await nombreNegocioInput.fill("Negocio Prueba Automatización");
    await page.waitForTimeout(350);

    const cancelarButton = await waitForAnyVisible([page.getByRole("button", { name: /cancelar/i })], 10000);
    await clickAndWait(page, cancelarButton);
  });

  await runValidationStep("Administrar Negocios view", report, failures, async () => {
    const administrarOption = await waitForAnyVisible(
      [
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByText(/administrar negocios/i)
      ],
      6000
    ).catch(async () => {
      const miNegocioToggle = await waitForAnyVisible(
        [
          page.getByRole("button", { name: /mi negocio/i }),
          page.getByRole("link", { name: /mi negocio/i }),
          page.getByText(/mi negocio/i)
        ],
        15000
      );

      await clickAndWait(page, miNegocioToggle);
      return waitForAnyVisible(
        [
          page.getByRole("button", { name: /administrar negocios/i }),
          page.getByRole("link", { name: /administrar negocios/i }),
          page.getByText(/administrar negocios/i)
        ],
        20000
      );
    });

    await clickAndWait(page, administrarOption);

    await waitForAnyVisible([page.getByText(/información general/i)], 30000);
    await waitForAnyVisible([page.getByText(/detalles de la cuenta/i)], 30000);
    await waitForAnyVisible([page.getByText(/tus negocios/i)], 30000);
    await waitForAnyVisible([page.getByText(/sección legal/i)], 30000);

    await captureCheckpoint(page, testInfo, "04-administrar-negocios-page.png", true);
  });

  await runValidationStep("Información General", report, failures, async () => {
    const infoSection = await waitForAnyVisible(
      [
        page.locator("section").filter({ hasText: /información general/i }),
        page.locator("div").filter({ hasText: /información general/i })
      ],
      20000
    );

    const infoText = normalizeText(await infoSection.innerText());
    const hasEmail = /\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/i.test(infoText);
    if (!hasEmail) {
      throw new Error("User email is not visible in 'Información General'.");
    }

    const nameCandidateText = normalizeText(
      infoText
        .replace(/información general/gi, "")
        .replace(/business plan/gi, "")
        .replace(/cambiar plan/gi, "")
        .replace(/\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/gi, "")
    );
    if (!/[A-Za-zÁÉÍÓÚÑáéíóúñ]{3,}/.test(nameCandidateText)) {
      throw new Error("User name is not clearly visible in 'Información General'.");
    }

    if (!/business plan/i.test(infoText)) {
      throw new Error("Text 'BUSINESS PLAN' is not visible.");
    }

    await waitForAnyVisible([page.getByRole("button", { name: /cambiar plan/i })], 10000);
  });

  await runValidationStep("Detalles de la Cuenta", report, failures, async () => {
    await waitForAnyVisible([page.getByText(/detalles de la cuenta/i)], 20000);
    await waitForAnyVisible([page.getByText(/cuenta creada/i)], 20000);
    await waitForAnyVisible([page.getByText(/estado activo/i)], 20000);
    await waitForAnyVisible([page.getByText(/idioma seleccionado/i)], 20000);
  });

  await runValidationStep("Tus Negocios", report, failures, async () => {
    const negociosSection = await waitForAnyVisible(
      [
        page.locator("section").filter({ hasText: /tus negocios/i }),
        page.locator("div").filter({ hasText: /tus negocios/i })
      ],
      20000
    );

    await waitForAnyVisible([page.getByRole("button", { name: /agregar negocio/i })], 15000);
    await waitForAnyVisible([page.getByText(/tienes 2 de 3 negocios/i)], 15000);

    const sectionText = normalizeText(await negociosSection.innerText());
    const businessListText = normalizeText(
      sectionText
        .replace(/tus negocios/gi, "")
        .replace(/agregar negocio/gi, "")
        .replace(/tienes 2 de 3 negocios/gi, "")
    );
    if (businessListText.length < 3) {
      throw new Error("Business list is not clearly visible in 'Tus Negocios'.");
    }
  });

  await runValidationStep("Términos y Condiciones", report, failures, async () => {
    const link = await waitForAnyVisible(
      [
        page.getByRole("link", { name: /términos y condiciones|terminos y condiciones/i }),
        page.getByText(/términos y condiciones|terminos y condiciones/i)
      ],
      20000
    );

    evidence.terminosYCondicionesUrl = await openLegalDocument({
      page,
      linkLocator: link,
      expectedHeadingRegex: /términos y condiciones|terminos y condiciones/i,
      screenshotName: "05-terminos-y-condiciones.png",
      testInfo
    });
  });

  await runValidationStep("Política de Privacidad", report, failures, async () => {
    const link = await waitForAnyVisible(
      [
        page.getByRole("link", { name: /política de privacidad|politica de privacidad/i }),
        page.getByText(/política de privacidad|politica de privacidad/i)
      ],
      20000
    );

    evidence.politicaDePrivacidadUrl = await openLegalDocument({
      page,
      linkLocator: link,
      expectedHeadingRegex: /política de privacidad|politica de privacidad/i,
      screenshotName: "06-politica-de-privacidad.png",
      testInfo
    });
  });

  await testInfo.attach("mi-negocio-final-report.json", {
    contentType: "application/json",
    body: Buffer.from(
      JSON.stringify(
        {
          report,
          evidence,
          failures
        },
        null,
        2
      )
    )
  });

  expect(
    failures,
    `Final report contains failing steps:\n${failures.join("\n")}`
  ).toEqual([]);
});
