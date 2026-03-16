const fs = require("fs/promises");
const { test, expect } = require("@playwright/test");

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
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

function buildCandidates(page, matcher) {
  return [
    page.getByRole("button", { name: matcher }).first(),
    page.getByRole("link", { name: matcher }).first(),
    page.getByRole("menuitem", { name: matcher }).first(),
    page.getByRole("tab", { name: matcher }).first(),
    page.getByRole("heading", { name: matcher }).first(),
    page.getByText(matcher).first(),
  ];
}

async function findFirstVisible(page, matchers, timeout = 2500) {
  for (const matcher of matchers) {
    const candidates = buildCandidates(page, matcher);
    for (const candidate of candidates) {
      try {
        await candidate.waitFor({ state: "visible", timeout });
        return candidate;
      } catch {
        // Try next candidate.
      }
    }
  }

  return null;
}

async function clickByVisibleText(page, matchers, label) {
  const target = await findFirstVisible(page, matchers, 4000);
  if (!target) {
    throw new Error(`No visible clickable element found for: ${label}`);
  }

  await target.click();
  await waitForUiToLoad(page);
}

async function assertTextVisible(page, matcher, label) {
  const target = await findFirstVisible(page, [matcher], 10_000);
  if (!target) {
    throw new Error(`Expected visible text not found: ${label}`);
  }
}

async function waitForUiToLoad(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(900);
}

async function captureCheckpoint(page, testInfo, fileName, fullPage = true) {
  const path = testInfo.outputPath(fileName);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(fileName, { path, contentType: "image/png" });
}

async function isAppShellVisible(page) {
  const mainVisible = await page
    .locator("main, [role='main']")
    .first()
    .isVisible()
    .catch(() => false);

  const sideNavVisible = await page
    .locator("aside, nav")
    .first()
    .isVisible()
    .catch(() => false);

  return mainVisible && sideNavVisible;
}

async function selectGoogleAccountIfVisible(page) {
  const account = await findFirstVisible(page, [new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")], 8000);
  if (account) {
    await account.click();
    await waitForUiToLoad(page);
  }
}

async function findBusinessNameInput(page) {
  const candidates = [
    page.getByLabel(/nombre del negocio/i).first(),
    page.getByPlaceholder(/nombre del negocio/i).first(),
    page.getByRole("textbox", { name: /nombre del negocio/i }).first(),
    page.locator("input[name*='negocio' i], input[id*='negocio' i]").first(),
    page.locator("input").first(),
  ];

  for (const candidate of candidates) {
    try {
      await candidate.waitFor({ state: "visible", timeout: 2000 });
      return candidate;
    } catch {
      // Try next candidate.
    }
  }

  throw new Error("Nombre del Negocio input was not found.");
}

async function runStep(results, key, fn) {
  try {
    await fn();
    results[key] = { status: "PASS", detail: "" };
  } catch (error) {
    results[key] = {
      status: "FAIL",
      detail: error instanceof Error ? error.message : String(error),
    };
  }
}

async function openLegalLinkAndValidate({
  page,
  context,
  testInfo,
  linkMatcher,
  headingMatcher,
  screenshotName,
  appUrl,
}) {
  const popupPromise = context.waitForEvent("page", { timeout: 6000 }).catch(() => null);
  await clickByVisibleText(page, [linkMatcher], `Legal link ${String(linkMatcher)}`);
  const popup = await popupPromise;

  const targetPage = popup || page;
  if (popup) {
    await popup.waitForLoadState("domcontentloaded");
    await popup.bringToFront();
  } else {
    await page.waitForLoadState("domcontentloaded");
  }

  await assertTextVisible(targetPage, headingMatcher, "Legal page heading");

  const bodyText = (await targetPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
  if (bodyText.length < 200) {
    throw new Error("Legal content looks too short or not loaded.");
  }

  const finalUrl = targetPage.url();
  await captureCheckpoint(targetPage, testInfo, screenshotName, true);

  if (popup) {
    await popup.close().catch(() => {});
    await page.bringToFront();
  } else {
    await page
      .goBack({ waitUntil: "domcontentloaded" })
      .catch(async () => page.goto(appUrl, { waitUntil: "domcontentloaded" }));
  }

  await waitForUiToLoad(page);
  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  test.setTimeout(15 * 60 * 1000);

  const results = Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: "NOT_RUN", detail: "" }]),
  );
  const legalUrls = {};

  const startUrl =
    process.env.SALEADS_START_URL || process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL;
  if (!startUrl) {
    throw new Error(
      "Set SALEADS_START_URL (or SALEADS_LOGIN_URL / SALEADS_URL) to the login page of the target environment.",
    );
  }

  await page.goto(startUrl, { waitUntil: "domcontentloaded" });
  await waitForUiToLoad(page);

  await runStep(results, "Login", async () => {
    const appAlreadyLoaded = await isAppShellVisible(page);

    if (!appAlreadyLoaded) {
      const popupPromise = page.waitForEvent("popup", { timeout: 6000 }).catch(() => null);

      await clickByVisibleText(
        page,
        [
          /iniciar sesi[oó]n con google/i,
          /sign in with google/i,
          /continuar con google/i,
          /google/i,
          /iniciar sesi[oó]n/i,
          /login/i,
        ],
        "Google login button",
      );

      const popup = await popupPromise;
      if (popup) {
        await popup.waitForLoadState("domcontentloaded");
        await selectGoogleAccountIfVisible(popup);
      } else {
        await selectGoogleAccountIfVisible(page);
      }
    }

    await expect
      .poll(async () => isAppShellVisible(page), {
        timeout: 90_000,
        intervals: [1000, 2000, 3000],
      })
      .toBe(true);

    await assertTextVisible(page, /negocio|mi negocio/i, "Sidebar navigation");
    await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png", true);
  });

  await runStep(results, "Mi Negocio menu", async () => {
    const miNegocioVisible = await findFirstVisible(page, [/mi negocio/i], 2500);
    if (miNegocioVisible) {
      await miNegocioVisible.click();
      await waitForUiToLoad(page);
    } else {
      await clickByVisibleText(page, [/negocio/i], "Negocio menu");
      await clickByVisibleText(page, [/mi negocio/i], "Mi Negocio menu");
    }

    await assertTextVisible(page, /agregar negocio/i, "Agregar Negocio");
    await assertTextVisible(page, /administrar negocios/i, "Administrar Negocios");
    await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png", true);
  });

  await runStep(results, "Agregar Negocio modal", async () => {
    await clickByVisibleText(page, [/agregar negocio/i], "Agregar Negocio");
    await assertTextVisible(page, /crear nuevo negocio/i, "Crear Nuevo Negocio modal title");
    await assertTextVisible(page, /nombre del negocio/i, "Nombre del Negocio field");
    await assertTextVisible(page, /tienes\s*2\s*de\s*3\s*negocios/i, "Business quota text");
    await assertTextVisible(page, /cancelar/i, "Cancelar button");
    await assertTextVisible(page, /crear negocio/i, "Crear Negocio button");

    const businessNameInput = await findBusinessNameInput(page);
    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");

    await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png", true);
    await clickByVisibleText(page, [/cancelar/i], "Cancelar");
  });

  await runStep(results, "Administrar Negocios view", async () => {
    const adminVisible = await findFirstVisible(page, [/administrar negocios/i], 2500);
    if (!adminVisible) {
      await clickByVisibleText(page, [/mi negocio/i], "Mi Negocio menu");
    }

    await clickByVisibleText(page, [/administrar negocios/i], "Administrar Negocios");
    await assertTextVisible(page, /informaci[oó]n general/i, "Información General section");
    await assertTextVisible(page, /detalles de la cuenta/i, "Detalles de la Cuenta section");
    await assertTextVisible(page, /tus negocios/i, "Tus Negocios section");
    await assertTextVisible(page, /secci[oó]n legal/i, "Sección Legal section");
    await captureCheckpoint(page, testInfo, "04-administrar-negocios-view.png", true);
  });

  await runStep(results, "Información General", async () => {
    await assertTextVisible(page, /informaci[oó]n general/i, "Información General");

    const emailCandidate = await findFirstVisible(page, [/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i], 5000);
    if (!emailCandidate) {
      throw new Error("User email is not visible.");
    }

    const bodyText = await page.locator("body").innerText();
    const hasNameLikeText = bodyText
      .split("\n")
      .map((line) => line.trim())
      .some((line) => /^[A-Za-zÀ-ÿ][A-Za-zÀ-ÿ' -]{2,}$/.test(line) && !line.includes("@"));

    if (!hasNameLikeText) {
      throw new Error("User name was not detected as visible text.");
    }

    await assertTextVisible(page, /business plan/i, "BUSINESS PLAN");
    await assertTextVisible(page, /cambiar plan/i, "Cambiar Plan button");
  });

  await runStep(results, "Detalles de la Cuenta", async () => {
    await assertTextVisible(page, /detalles de la cuenta/i, "Detalles de la Cuenta");
    await assertTextVisible(page, /cuenta creada/i, "Cuenta creada");
    await assertTextVisible(page, /estado activo/i, "Estado activo");
    await assertTextVisible(page, /idioma seleccionado/i, "Idioma seleccionado");
  });

  await runStep(results, "Tus Negocios", async () => {
    await assertTextVisible(page, /tus negocios/i, "Tus Negocios heading");
    await assertTextVisible(page, /agregar negocio/i, "Tus Negocios Agregar Negocio button");
    await assertTextVisible(page, /tienes\s*2\s*de\s*3\s*negocios/i, "Tus Negocios quota text");

    const businessListCandidates = [
      page.locator("section, div").filter({ hasText: /tus negocios/i }).locator("li"),
      page.locator("section, div").filter({ hasText: /tus negocios/i }).locator("[role='row']"),
      page.locator("section, div").filter({ hasText: /tus negocios/i }).locator("[class*='card']"),
    ];

    let hasList = false;
    for (const candidate of businessListCandidates) {
      if ((await candidate.count()) > 0) {
        hasList = true;
        break;
      }
    }

    if (!hasList) {
      const sectionText = await page.locator("body").innerText();
      if (!/tus negocios[\s\S]*agregar negocio/i.test(sectionText)) {
        throw new Error("Business list content is not clearly visible.");
      }
    }
  });

  await runStep(results, "Términos y Condiciones", async () => {
    legalUrls.terms = await openLegalLinkAndValidate({
      page,
      context,
      testInfo,
      linkMatcher: /t[eé]rminos y condiciones/i,
      headingMatcher: /t[eé]rminos y condiciones/i,
      screenshotName: "05-terminos-y-condiciones.png",
      appUrl: startUrl,
    });
  });

  await runStep(results, "Política de Privacidad", async () => {
    legalUrls.privacy = await openLegalLinkAndValidate({
      page,
      context,
      testInfo,
      linkMatcher: /pol[ií]tica de privacidad/i,
      headingMatcher: /pol[ií]tica de privacidad/i,
      screenshotName: "06-politica-de-privacidad.png",
      appUrl: startUrl,
    });
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    environment: {
      startUrl,
      browser: testInfo.project.name,
    },
    results,
    evidence: {
      termsUrl: legalUrls.terms || "N/A",
      privacyUrl: legalUrls.privacy || "N/A",
    },
  };

  const reportPath = testInfo.outputPath("saleads_mi_negocio_final_report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
  await testInfo.attach("saleads_mi_negocio_final_report", {
    path: reportPath,
    contentType: "application/json",
  });

  const failedFields = REPORT_FIELDS.filter((field) => results[field].status !== "PASS");
  expect(
    failedFields,
    `Final report contains failed validations: ${failedFields
      .map((field) => `${field} => ${results[field].detail}`)
      .join(" | ")}`,
  ).toEqual([]);
});
