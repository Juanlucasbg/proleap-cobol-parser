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

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function asWordRegex(value) {
  return new RegExp(escapeRegex(value), "i");
}

function asExactRegex(value) {
  return new RegExp(`^${escapeRegex(value)}$`, "i");
}

function slugify(value) {
  return value
    .toLowerCase()
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
}

async function waitForUi(page) {
  await Promise.allSettled([
    page.waitForLoadState("domcontentloaded", { timeout: 8000 }),
    page.waitForLoadState("networkidle", { timeout: 8000 }),
  ]);
  await page.waitForTimeout(900);
}

async function firstVisibleLocator(candidates, timeoutMs = 2500) {
  for (const candidate of candidates) {
    const locator = candidate().first();
    const visible = await locator.isVisible({ timeout: timeoutMs }).catch(() => false);
    if (visible) {
      return locator;
    }
  }

  throw new Error("Could not find a visible locator among provided candidates.");
}

async function clickByVisibleText(scope, texts, contextLabel, pageForWait) {
  const candidates = [];

  for (const text of texts) {
    const exact = asExactRegex(text);
    const fuzzy = asWordRegex(text);
    candidates.push(() => scope.getByRole("button", { name: exact }));
    candidates.push(() => scope.getByRole("button", { name: fuzzy }));
    candidates.push(() => scope.getByRole("link", { name: exact }));
    candidates.push(() => scope.getByRole("link", { name: fuzzy }));
    candidates.push(() => scope.getByRole("menuitem", { name: exact }));
    candidates.push(() => scope.getByRole("menuitem", { name: fuzzy }));
    candidates.push(() => scope.getByText(exact));
    candidates.push(() => scope.getByText(fuzzy));
  }

  const locator = await firstVisibleLocator(candidates);
  await locator.click();
  const waitTarget =
    pageForWait ||
    (scope && typeof scope.waitForLoadState === "function" ? scope : null);
  if (waitTarget) {
    await waitForUi(waitTarget);
  }
  return locator;
}

async function assertVisibleText(scope, text) {
  const exact = asExactRegex(text);
  const fuzzy = asWordRegex(text);
  const locator = await firstVisibleLocator(
    [
      () => scope.getByRole("heading", { name: exact }),
      () => scope.getByRole("heading", { name: fuzzy }),
      () => scope.getByRole("button", { name: exact }),
      () => scope.getByRole("button", { name: fuzzy }),
      () => scope.getByRole("link", { name: exact }),
      () => scope.getByRole("link", { name: fuzzy }),
      () => scope.getByLabel(exact),
      () => scope.getByLabel(fuzzy),
      () => scope.getByText(exact),
      () => scope.getByText(fuzzy),
    ],
    4000
  );

  await expect(locator).toBeVisible();
}

async function checkpoint(page, testInfo, name, fullPage = false) {
  const path = testInfo.outputPath(`${slugify(name)}.png`);
  await page.screenshot({ path, fullPage });
  await testInfo.attach(name, {
    path,
    contentType: "image/png",
  });
}

async function runValidation(report, errors, field, validationFn) {
  try {
    await validationFn();
    report[field] = "PASS";
  } catch (error) {
    report[field] = "FAIL";
    errors.push(`${field}: ${error.message}`);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const evidence = {
    urls: {
      terminosYCondiciones: "",
      politicaDePrivacidad: "",
    },
  };
  const errors = [];

  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_URL || process.env.BASE_URL || "";
  if (page.url() === "about:blank") {
    if (!loginUrl) {
      throw new Error(
        "No login page available. Provide SALEADS_LOGIN_URL (or SALEADS_URL/BASE_URL) to run this environment-agnostic test."
      );
    }

    await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  }
  await waitForUi(page);

  await runValidation(report, errors, "Login", async () => {
    const loginButtonCandidates = [
      () => page.getByRole("button", { name: /sign in with google/i }),
      () => page.getByRole("button", { name: /continuar con google/i }),
      () => page.getByRole("button", { name: /iniciar con google/i }),
      () => page.getByRole("link", { name: /google/i }),
      () => page.getByText(/google/i),
    ];

    const loginButton = await firstVisibleLocator(loginButtonCandidates, 5000);
    const popupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await loginButton.click();
    await waitForUi(page);

    const googlePopup = await popupPromise;
    if (googlePopup) {
      await googlePopup.waitForLoadState("domcontentloaded", { timeout: 15000 });
      await waitForUi(googlePopup);

      const accountLocator = await firstVisibleLocator(
        [
          () => googlePopup.getByText(asExactRegex(GOOGLE_ACCOUNT_EMAIL)),
          () => googlePopup.getByRole("button", { name: asWordRegex(GOOGLE_ACCOUNT_EMAIL) }),
          () => googlePopup.getByRole("link", { name: asWordRegex(GOOGLE_ACCOUNT_EMAIL) }),
        ],
        5000
      ).catch(() => null);

      if (accountLocator) {
        await accountLocator.click();
      }

      await Promise.allSettled([
        googlePopup.waitForEvent("close", { timeout: 20000 }),
        page.waitForLoadState("domcontentloaded", { timeout: 20000 }),
      ]);
    } else {
      const inlineAccountLocator = await firstVisibleLocator(
        [
          () => page.getByText(asExactRegex(GOOGLE_ACCOUNT_EMAIL)),
          () => page.getByRole("button", { name: asWordRegex(GOOGLE_ACCOUNT_EMAIL) }),
        ],
        5000
      ).catch(() => null);

      if (inlineAccountLocator) {
        await inlineAccountLocator.click();
      }
    }

    await waitForUi(page);

    const sidebar = await firstVisibleLocator(
      [
        () => page.locator("aside"),
        () => page.getByRole("navigation"),
        () => page.locator("nav"),
      ],
      10000
    );

    await expect(sidebar).toBeVisible();
    await assertVisibleText(page, "Negocio");
    await checkpoint(page, testInfo, "01-dashboard-loaded", true);
  });

  await runValidation(report, errors, "Mi Negocio menu", async () => {
    await assertVisibleText(page, "Negocio");
    await clickByVisibleText(page, ["Mi Negocio"], "Mi Negocio menu");
    await assertVisibleText(page, "Agregar Negocio");
    await assertVisibleText(page, "Administrar Negocios");
    await checkpoint(page, testInfo, "02-mi-negocio-expanded-menu", true);
  });

  await runValidation(report, errors, "Agregar Negocio modal", async () => {
    await clickByVisibleText(page, ["Agregar Negocio"], "Agregar Negocio trigger");
    await waitForUi(page);

    const modal = await firstVisibleLocator(
      [
        () => page.getByRole("dialog"),
        () => page.locator('[role="dialog"]'),
      ],
      8000
    );

    await expect(modal).toBeVisible();
    await assertVisibleText(modal, "Crear Nuevo Negocio");
    await assertVisibleText(modal, "Nombre del Negocio");
    await assertVisibleText(modal, "Tienes 2 de 3 negocios");
    await assertVisibleText(modal, "Cancelar");
    await assertVisibleText(modal, "Crear Negocio");

    const input = await firstVisibleLocator(
      [
        () => modal.getByLabel(/nombre del negocio/i),
        () => modal.getByPlaceholder(/nombre del negocio/i),
        () => modal.locator("input[type='text']"),
      ],
      4000
    );
    await input.fill("Negocio Prueba Automatización");
    await checkpoint(page, testInfo, "03-agregar-negocio-modal", true);
    await clickByVisibleText(modal, ["Cancelar"], "Cancelar modal", page);
    await waitForUi(page);
  });

  await runValidation(report, errors, "Administrar Negocios view", async () => {
    const administrarVisible = await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      await clickByVisibleText(page, ["Mi Negocio"], "Expand Mi Negocio again");
    }

    await clickByVisibleText(page, ["Administrar Negocios"], "Administrar Negocios");
    await assertVisibleText(page, "Información General");
    await assertVisibleText(page, "Detalles de la Cuenta");
    await assertVisibleText(page, "Tus Negocios");
    await assertVisibleText(page, "Sección Legal");
    await checkpoint(page, testInfo, "04-administrar-negocios-page", true);
  });

  await runValidation(report, errors, "Información General", async () => {
    await assertVisibleText(page, "Información General");
    await assertVisibleText(page, "BUSINESS PLAN");
    await assertVisibleText(page, "Cambiar Plan");

    const emailPattern = /[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}/i;
    const emailText = page.getByText(emailPattern);
    await expect(emailText.first()).toBeVisible();

    const businessPlanCard = page.getByText(/business plan/i).first();
    await expect(businessPlanCard).toBeVisible();

    const infoSection = page
      .locator("section, div")
      .filter({ hasText: /información general/i })
      .first();
    const infoText = (await infoSection.innerText()).trim();
    const cleanLines = infoText
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean)
      .filter((line) => !/información general|business plan|cambiar plan/i.test(line))
      .filter((line) => !emailPattern.test(line));
    expect(cleanLines.length).toBeGreaterThan(0);
  });

  await runValidation(report, errors, "Detalles de la Cuenta", async () => {
    await assertVisibleText(page, "Detalles de la Cuenta");
    await assertVisibleText(page, "Cuenta creada");
    await assertVisibleText(page, "Estado activo");
    await assertVisibleText(page, "Idioma seleccionado");
  });

  await runValidation(report, errors, "Tus Negocios", async () => {
    await assertVisibleText(page, "Tus Negocios");
    await assertVisibleText(page, "Agregar Negocio");
    await assertVisibleText(page, "Tienes 2 de 3 negocios");

    const businessSection = page
      .locator("section, div")
      .filter({ hasText: /tus negocios/i })
      .first();
    const listLikeItems = businessSection.locator(
      "li, [role='listitem'], article, [data-testid*='business'], [class*='business']"
    );
    const itemCount = await listLikeItems.count();
    expect(itemCount).toBeGreaterThan(0);
  });

  await runValidation(report, errors, "Términos y Condiciones", async () => {
    const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
    await clickByVisibleText(page, ["Términos y Condiciones"], "Términos y Condiciones link");
    const legalPage = (await popupPromise) || page;

    await legalPage.waitForLoadState("domcontentloaded", { timeout: 15000 });
    await waitForUi(legalPage);

    await assertVisibleText(legalPage, "Términos y Condiciones");
    const legalText = (await legalPage.locator("body").innerText()).trim();
    expect(legalText.length).toBeGreaterThan(120);

    evidence.urls.terminosYCondiciones = legalPage.url();
    await checkpoint(legalPage, testInfo, "05-terminos-y-condiciones", true);

    if (legalPage !== page) {
      await legalPage.close();
      await page.bringToFront();
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
      await waitForUi(page);
    }
  });

  await runValidation(report, errors, "Política de Privacidad", async () => {
    const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
    await clickByVisibleText(page, ["Política de Privacidad"], "Política de Privacidad link");
    const legalPage = (await popupPromise) || page;

    await legalPage.waitForLoadState("domcontentloaded", { timeout: 15000 });
    await waitForUi(legalPage);

    await assertVisibleText(legalPage, "Política de Privacidad");
    const legalText = (await legalPage.locator("body").innerText()).trim();
    expect(legalText.length).toBeGreaterThan(120);

    evidence.urls.politicaDePrivacidad = legalPage.url();
    await checkpoint(legalPage, testInfo, "06-politica-de-privacidad", true);

    if (legalPage !== page) {
      await legalPage.close();
      await page.bringToFront();
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => null);
      await waitForUi(page);
    }
  });

  console.log(
    "saleads_mi_negocio_full_test_report",
    JSON.stringify(
      {
        report,
        urls: evidence.urls,
        errors,
      },
      null,
      2
    )
  );

  const failedFields = Object.entries(report)
    .filter(([, status]) => status === "FAIL")
    .map(([field]) => field);

  expect(
    failedFields,
    `One or more validations failed.\n${JSON.stringify(
      {
        report,
        urls: evidence.urls,
        errors,
      },
      null,
      2
    )}`
  ).toEqual([]);
});
