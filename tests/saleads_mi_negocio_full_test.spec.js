const { test, expect } = require("@playwright/test");
const fs = require("node:fs/promises");
const path = require("node:path");

const GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
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

const EMAIL_REGEX = /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i;

function slugify(value) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(500);
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
}

async function firstVisible(locatorFactories, timeout = 4000) {
  for (const factory of locatorFactories) {
    const locator = factory();
    try {
      await locator.first().waitFor({ state: "visible", timeout });
      return locator.first();
    } catch (error) {
      // try next strategy
    }
  }
  return null;
}

async function assertVisibleByText(page, textOrRegex, description) {
  const textLabel =
    typeof textOrRegex === "string" ? textOrRegex : textOrRegex.toString();
  const target = await firstVisible([
    () =>
      page.getByRole("heading", {
        name: textOrRegex
      }),
    () =>
      page.getByRole("button", {
        name: textOrRegex
      }),
    () =>
      page.getByRole("link", {
        name: textOrRegex
      }),
    () =>
      page.getByRole("menuitem", {
        name: textOrRegex
      }),
    () =>
      page.getByText(textOrRegex, {
        exact: false
      })
  ]);

  if (!target) {
    throw new Error(`Could not find visible text for ${description}: ${textLabel}`);
  }

  await expect(target).toBeVisible();
  return target;
}

async function clickAnyByText(page, patterns, description) {
  for (const pattern of patterns) {
    const target = await firstVisible(
      [
        () =>
          page.getByRole("button", {
            name: pattern
          }),
        () =>
          page.getByRole("link", {
            name: pattern
          }),
        () =>
          page.getByRole("menuitem", {
            name: pattern
          }),
        () =>
          page.getByRole("tab", {
            name: pattern
          }),
        () =>
          page.getByText(pattern, {
            exact: false
          })
      ],
      2500
    );

    if (target) {
      await target.click();
      await waitForUi(page);
      return target;
    }
  }

  throw new Error(`Unable to click ${description}.`);
}

async function capture(page, dir, name, fullPage = false) {
  const screenshotPath = path.join(dir, `${slugify(name)}.png`);
  await page.screenshot({
    path: screenshotPath,
    fullPage
  });
  return screenshotPath;
}

async function maybeSelectGoogleAccount(page) {
  const accountLocator = await firstVisible(
    [
      () =>
        page.getByRole("button", {
          name: GOOGLE_ACCOUNT
        }),
      () =>
        page.getByRole("link", {
          name: GOOGLE_ACCOUNT
        }),
      () => page.getByText(GOOGLE_ACCOUNT, { exact: false })
    ],
    5000
  );

  if (accountLocator) {
    await accountLocator.click();
    await waitForUi(page);
  }
}

async function getSectionByHeading(page, headingRegex) {
  const heading = await firstVisible([
    () =>
      page.getByRole("heading", {
        name: headingRegex
      }),
    () => page.getByText(headingRegex)
  ]);

  if (!heading) {
    throw new Error(`Section heading not found: ${headingRegex}`);
  }

  const section = heading.locator(
    "xpath=ancestor::section[1] | ancestor::article[1] | ancestor::div[contains(@class,'card')][1]"
  );
  const sectionCount = await section.count();
  if (sectionCount > 0) {
    return section.first();
  }

  return heading;
}

async function openAndValidateLegalDocument({
  appPage,
  runDirectory,
  linkPattern,
  headingPattern,
  reportName,
  screenshotName
}) {
  const popupPromise = appPage.context().waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await clickAnyByText(appPage, [linkPattern], reportName);
  const popup = await popupPromise;
  const legalPage = popup || appPage;

  await waitForUi(legalPage);
  await assertVisibleByText(legalPage, headingPattern, reportName);

  const bodyText = await legalPage.locator("body").innerText();
  if (bodyText.replace(/\s+/g, " ").trim().length < 120) {
    throw new Error(`Legal content for ${reportName} appears too short.`);
  }

  const screenshotPath = await capture(legalPage, runDirectory, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close().catch(() => {});
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(appPage);
  }

  return { screenshotPath, finalUrl };
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login and validate full Mi Negocio workflow", async ({ page }) => {
    const runTimestamp = new Date().toISOString().replace(/[:.]/g, "-");
    const runDirectory = path.join(
      process.cwd(),
      "artifacts",
      "saleads_mi_negocio_full_test",
      runTimestamp
    );
    await fs.mkdir(runDirectory, { recursive: true });

    const startUrl =
      process.env.SALEADS_LOGIN_URL || process.env.SALEADS_START_URL || process.env.BASE_URL;

    if (startUrl) {
      await page.goto(startUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "No SALEADS_LOGIN_URL/SALEADS_START_URL/BASE_URL provided and browser is on about:blank."
      );
    }

    const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
    const stepFailures = [];
    const evidence = {
      screenshots: {},
      urls: {}
    };

    async function runStep(reportField, handler) {
      try {
        await handler();
        report[reportField] = "PASS";
      } catch (error) {
        report[reportField] = "FAIL";
        stepFailures.push(`${reportField}: ${error.message}`);
      }
    }

    await runStep("Login", async () => {
      const loginPopupPromise = page.context().waitForEvent("page", { timeout: 8000 }).catch(() => null);
      await clickAnyByText(
        page,
        [
          /sign in with google/i,
          /iniciar sesi[oó]n con google/i,
          /continuar con google/i,
          /acceder con google/i,
          /google/i
        ],
        "Google login button"
      );

      const loginPopup = await loginPopupPromise;
      if (loginPopup) {
        await waitForUi(loginPopup);
        await maybeSelectGoogleAccount(loginPopup);
        await loginPopup.waitForEvent("close", { timeout: 30000 }).catch(() => {});
        await page.bringToFront();
      } else {
        await maybeSelectGoogleAccount(page);
      }

      await waitForUi(page);
      await assertVisibleByText(page, /Negocio|Mi Negocio/i, "main application interface");
      const sidebar = await firstVisible([
        () => page.locator("aside"),
        () => page.locator("nav"),
        () => page.getByRole("navigation")
      ]);
      if (!sidebar) {
        throw new Error("Left sidebar navigation is not visible.");
      }

      evidence.screenshots.dashboard = await capture(page, runDirectory, "01-dashboard-loaded");
    });

    await runStep("Mi Negocio menu", async () => {
      await clickAnyByText(page, [/^Negocio$/i, /Negocio/i], "Negocio section");
      await clickAnyByText(page, [/^Mi Negocio$/i, /Mi Negocio/i], "Mi Negocio menu");

      await assertVisibleByText(page, /^Agregar Negocio$/i, "Agregar Negocio submenu item");
      await assertVisibleByText(page, /^Administrar Negocios$/i, "Administrar Negocios submenu item");

      evidence.screenshots.miNegocioMenu = await capture(
        page,
        runDirectory,
        "02-mi-negocio-menu-expanded"
      );
    });

    await runStep("Agregar Negocio modal", async () => {
      await clickAnyByText(page, [/^Agregar Negocio$/i], "Agregar Negocio menu action");
      await assertVisibleByText(page, /^Crear Nuevo Negocio$/i, "Crear Nuevo Negocio modal title");
      await assertVisibleByText(page, /^Nombre del Negocio$/i, "Nombre del Negocio input label");
      await assertVisibleByText(page, /Tienes 2 de 3 negocios/i, "business quota text");
      await assertVisibleByText(page, /^Cancelar$/i, "Cancelar button");
      await assertVisibleByText(page, /^Crear Negocio$/i, "Crear Negocio button");

      const nameInput = await firstVisible([
        () => page.getByLabel(/Nombre del Negocio/i),
        () => page.getByPlaceholder(/Nombre del Negocio/i),
        () => page.locator("input").first()
      ]);
      if (!nameInput) {
        throw new Error("Nombre del Negocio input field was not detected.");
      }
      await nameInput.click();
      await nameInput.fill("Negocio Prueba Automatización");

      evidence.screenshots.agregarNegocioModal = await capture(
        page,
        runDirectory,
        "03-agregar-negocio-modal"
      );

      await clickAnyByText(page, [/^Cancelar$/i], "Cancelar modal action");
    });

    await runStep("Administrar Negocios view", async () => {
      const administrarVisible = await firstVisible([
        () => page.getByRole("link", { name: /^Administrar Negocios$/i }),
        () => page.getByRole("button", { name: /^Administrar Negocios$/i }),
        () => page.getByText(/^Administrar Negocios$/i)
      ]);
      if (!administrarVisible) {
        await clickAnyByText(page, [/^Mi Negocio$/i, /Mi Negocio/i], "Mi Negocio menu re-open");
      }

      await clickAnyByText(page, [/^Administrar Negocios$/i], "Administrar Negocios option");
      await assertVisibleByText(page, /^Información General$/i, "Información General section");
      await assertVisibleByText(page, /^Detalles de la Cuenta$/i, "Detalles de la Cuenta section");
      await assertVisibleByText(page, /^Tus Negocios$/i, "Tus Negocios section");
      await assertVisibleByText(page, /Sección Legal/i, "Sección Legal section");

      evidence.screenshots.administrarNegocios = await capture(
        page,
        runDirectory,
        "04-administrar-negocios-page",
        true
      );
    });

    await runStep("Información General", async () => {
      const infoSection = await getSectionByHeading(page, /^Información General$/i);
      const infoText = await infoSection.innerText();

      const emailMatch = infoText.match(EMAIL_REGEX);
      if (!emailMatch) {
        throw new Error("User email was not found inside Información General.");
      }

      const lines = infoText
        .split("\n")
        .map((line) => line.trim())
        .filter(Boolean);

      const candidateNameLines = lines.filter((line) => {
        if (EMAIL_REGEX.test(line)) {
          return false;
        }
        return ![
          /información general/i,
          /business plan/i,
          /cambiar plan/i,
          /plan/i,
          /correo/i,
          /email/i,
          /usuario/i,
          /nombre/i
        ].some((pattern) => pattern.test(line));
      });

      if (candidateNameLines.length === 0) {
        throw new Error("A user name-like value was not detected in Información General.");
      }

      await assertVisibleByText(page, /BUSINESS PLAN/i, "BUSINESS PLAN text");
      await assertVisibleByText(page, /^Cambiar Plan$/i, "Cambiar Plan button");
    });

    await runStep("Detalles de la Cuenta", async () => {
      await assertVisibleByText(page, /Cuenta creada/i, "Cuenta creada label");
      await assertVisibleByText(page, /Estado activo/i, "Estado activo label");
      await assertVisibleByText(page, /Idioma seleccionado/i, "Idioma seleccionado label");
    });

    await runStep("Tus Negocios", async () => {
      const businessesSection = await getSectionByHeading(page, /^Tus Negocios$/i);
      const sectionText = await businessesSection.innerText();
      if (sectionText.replace(/\s+/g, " ").trim().length < 40) {
        throw new Error("Business list area appears empty.");
      }

      await assertVisibleByText(page, /^Agregar Negocio$/i, "Agregar Negocio button");
      await assertVisibleByText(page, /Tienes 2 de 3 negocios/i, "business quota text");
    });

    await runStep("Términos y Condiciones", async () => {
      const legalResult = await openAndValidateLegalDocument({
        appPage: page,
        runDirectory,
        linkPattern: /T[ée]rminos y Condiciones/i,
        headingPattern: /T[ée]rminos y Condiciones/i,
        reportName: "Términos y Condiciones",
        screenshotName: "05-terminos-y-condiciones"
      });
      evidence.screenshots.terminosYCondiciones = legalResult.screenshotPath;
      evidence.urls.terminosYCondiciones = legalResult.finalUrl;
    });

    await runStep("Política de Privacidad", async () => {
      const legalResult = await openAndValidateLegalDocument({
        appPage: page,
        runDirectory,
        linkPattern: /Pol[ií]tica de Privacidad/i,
        headingPattern: /Pol[ií]tica de Privacidad/i,
        reportName: "Política de Privacidad",
        screenshotName: "06-politica-de-privacidad"
      });
      evidence.screenshots.politicaDePrivacidad = legalResult.screenshotPath;
      evidence.urls.politicaDePrivacidad = legalResult.finalUrl;
    });

    const finalReport = {
      testName: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      report,
      evidence,
      failures: stepFailures
    };

    const reportPath = path.join(runDirectory, "final-report.json");
    await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf8");
    console.log("Final workflow report:", JSON.stringify(finalReport, null, 2));
    console.log("Report file:", reportPath);

    expect(stepFailures, `Workflow failures:\n${stepFailures.join("\n")}`).toEqual([]);
  });
});
