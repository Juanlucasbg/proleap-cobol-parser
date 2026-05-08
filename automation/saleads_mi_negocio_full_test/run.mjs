import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright";

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

const ACCOUNT_EMAIL =
  process.env.GOOGLE_ACCOUNT_EMAIL ?? "juanlucasbarbiergarzon@gmail.com";
const HEADLESS = process.env.HEADLESS !== "false";
const SALEADS_URL = process.env.SALEADS_URL ?? "";
const RUN_ID = new Date().toISOString().replace(/[:.]/g, "-");
const ARTIFACTS_DIR = path.join(
  process.cwd(),
  "artifacts",
  "saleads_mi_negocio_full_test",
  RUN_ID,
);

const finalReport = {
  runId: RUN_ID,
  startedAt: new Date().toISOString(),
  environment: {
    saleadsUrl: SALEADS_URL || null,
    headless: HEADLESS,
    accountEmail: ACCOUNT_EMAIL,
  },
  "Login": "FAIL",
  "Mi Negocio menu": "FAIL",
  "Agregar Negocio modal": "FAIL",
  "Administrar Negocios view": "FAIL",
  "Información General": "FAIL",
  "Detalles de la Cuenta": "FAIL",
  "Tus Negocios": "FAIL",
  "Términos y Condiciones": "FAIL",
  "Política de Privacidad": "FAIL",
  evidence: {
    screenshots: [],
    finalUrls: {},
  },
  failures: [],
};

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});
  await page.waitForTimeout(500);
}

async function takeScreenshot(page, name, fullPage = true) {
  const fileName = `${name.replaceAll(/\s+/g, "_").toLowerCase()}.png`;
  const filePath = path.join(ARTIFACTS_DIR, fileName);
  await page.screenshot({ path: filePath, fullPage });
  finalReport.evidence.screenshots.push(filePath);
  return filePath;
}

async function firstVisibleLocator(page, builders) {
  for (const build of builders) {
    const locator = build(page).first();
    try {
      await locator.waitFor({ state: "visible", timeout: 6_000 });
      return locator;
    } catch {
      // try next selector strategy
    }
  }
  return null;
}

async function assertVisible(page, builders, label) {
  const locator = await firstVisibleLocator(page, builders);
  if (!locator) {
    throw new Error(`Could not find visible element: ${label}`);
  }
  return locator;
}

async function clickAndWait(page, locator, stepDescription) {
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click({ timeout: 15_000 });
  await waitForUi(page);
  console.log(`Clicked: ${stepDescription}`);
}

async function clickByText(page, textRegex, label) {
  const locator = await assertVisible(
    page,
    [
      (p) => p.getByRole("button", { name: textRegex }),
      (p) => p.getByRole("link", { name: textRegex }),
      (p) => p.getByRole("menuitem", { name: textRegex }),
      (p) => p.getByText(textRegex),
    ],
    label,
  );
  await clickAndWait(page, locator, label);
}

async function hasVisible(page, builders, timeoutMs = 2_000) {
  for (const build of builders) {
    const locator = build(page).first();
    try {
      await locator.waitFor({ state: "visible", timeout: timeoutMs });
      return true;
    } catch {
      // continue
    }
  }
  return false;
}

async function openLegalLink({
  appPage,
  linkPattern,
  headingPattern,
  reportKey,
  screenshotName,
}) {
  const linkLocator = await assertVisible(
    appPage,
    [
      (p) => p.getByRole("link", { name: linkPattern }),
      (p) => p.getByText(linkPattern),
      (p) => p.getByRole("button", { name: linkPattern }),
    ],
    reportKey,
  );

  const popupPromise = appPage.context().waitForEvent("page", { timeout: 10_000 }).catch(() => null);
  await clickAndWait(appPage, linkLocator, reportKey);
  const popup = await popupPromise;

  const legalPage = popup ?? appPage;
  await legalPage.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
  await legalPage.waitForLoadState("networkidle", { timeout: 20_000 }).catch(() => {});

  await assertVisible(
    legalPage,
    [
      (p) => p.getByRole("heading", { name: headingPattern }),
      (p) => p.getByText(headingPattern),
    ],
    `${reportKey} heading`,
  );

  const legalContentLocator = await assertVisible(
    legalPage,
    [
      (p) => p.locator("main p"),
      (p) => p.locator("article p"),
      (p) => p.locator("p"),
    ],
    `${reportKey} legal content`,
  );

  const legalText = (await legalContentLocator.innerText()).trim();
  if (legalText.length < 20) {
    throw new Error(`${reportKey} legal content is too short.`);
  }

  await takeScreenshot(legalPage, screenshotName, true);
  finalReport.evidence.finalUrls[reportKey] = legalPage.url();

  if (popup) {
    await popup.close().catch(() => {});
    await appPage.bringToFront();
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded", timeout: 15_000 }).catch(() => {});
    await waitForUi(appPage);
  }
}

async function runStep(stepName, fn, pageRef) {
  try {
    await fn();
    finalReport[stepName] = "PASS";
    console.log(`[PASS] ${stepName}`);
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    finalReport[stepName] = "FAIL";
    finalReport.failures.push({ step: stepName, error: message });
    console.error(`[FAIL] ${stepName}: ${message}`);
    if (pageRef.current) {
      await takeScreenshot(
        pageRef.current,
        `failure_${stepName.replaceAll(/[^a-zA-Z0-9]+/g, "_")}`,
        true,
      ).catch(() => {});
    }
  }
}

async function ensureOnLoginPage(page) {
  if (SALEADS_URL) {
    await page.goto(SALEADS_URL, { waitUntil: "domcontentloaded", timeout: 60_000 });
    await waitForUi(page);
    return;
  }

  if (page.url() === "about:blank") {
    console.log(
      "SALEADS_URL is not set. Waiting up to 90s for manual navigation to a SaleADS login page...",
    );
    await page
      .waitForFunction(() => window.location.href !== "about:blank", null, { timeout: 90_000 })
      .catch(() => {});
    await waitForUi(page);
  }

  if (page.url() === "about:blank") {
    throw new Error(
      "No SaleADS login page detected. Set SALEADS_URL or navigate manually before execution.",
    );
  }
}

async function main() {
  await fs.mkdir(ARTIFACTS_DIR, { recursive: true });

  const browser = await chromium.launch({ headless: HEADLESS });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();
  const pageRef = { current: page };

  try {
    await ensureOnLoginPage(page);

    await runStep(
      "Login",
      async () => {
        const loginLocator = await assertVisible(
          page,
          [
            (p) => p.getByRole("button", { name: /google/i }),
            (p) => p.getByRole("button", { name: /sign in|iniciar sesi.n|acceder|login/i }),
            (p) => p.getByRole("link", { name: /google/i }),
            (p) => p.getByText(/google/i),
          ],
          "Sign in with Google",
        );

        const googlePopupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
        await clickAndWait(page, loginLocator, "Sign in with Google");
        const googlePopup = await googlePopupPromise;

        if (googlePopup) {
          pageRef.current = googlePopup;
          await googlePopup.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});

          const accountVisible = await hasVisible(
            googlePopup,
            [(p) => p.getByText(ACCOUNT_EMAIL), (p) => p.getByRole("button", { name: new RegExp(ACCOUNT_EMAIL, "i") })],
            8_000,
          );

          if (accountVisible) {
            await clickByText(googlePopup, new RegExp(ACCOUNT_EMAIL.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")), ACCOUNT_EMAIL);
          }
          pageRef.current = page;
          await page.bringToFront();
          await waitForUi(page);
        } else {
          const googleInlineVisible = await hasVisible(page, [(p) => p.getByText(ACCOUNT_EMAIL)], 3_000);
          if (googleInlineVisible) {
            await clickByText(
              page,
              new RegExp(ACCOUNT_EMAIL.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")),
              ACCOUNT_EMAIL,
            );
          }
        }

        await assertVisible(
          page,
          [
            (p) => p.locator("aside"),
            (p) => p.locator("nav"),
            (p) => p.getByText(/negocio/i),
          ],
          "main application interface with sidebar",
        );

        await takeScreenshot(page, "01_dashboard_loaded", true);
      },
      pageRef,
    );

    await runStep(
      "Mi Negocio menu",
      async () => {
        await clickByText(page, /negocio/i, "Negocio section");
        await clickByText(page, /mi negocio/i, "Mi Negocio");

        await assertVisible(page, [(p) => p.getByText(/agregar negocio/i)], "Agregar Negocio");
        await assertVisible(
          page,
          [(p) => p.getByText(/administrar negocios/i)],
          "Administrar Negocios",
        );

        await takeScreenshot(page, "02_mi_negocio_expanded_menu", true);
      },
      pageRef,
    );

    await runStep(
      "Agregar Negocio modal",
      async () => {
        await clickByText(page, /agregar negocio/i, "Agregar Negocio menu option");

        await assertVisible(
          page,
          [(p) => p.getByRole("heading", { name: /crear nuevo negocio/i }), (p) => p.getByText(/crear nuevo negocio/i)],
          "Crear Nuevo Negocio modal title",
        );
        const inputNombre = await assertVisible(
          page,
          [
            (p) => p.getByLabel(/nombre del negocio/i),
            (p) => p.getByPlaceholder(/nombre del negocio/i),
            (p) => p.locator("input[type='text'], input:not([type])"),
          ],
          "Nombre del Negocio input",
        );

        await assertVisible(
          page,
          [(p) => p.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)],
          "Tienes 2 de 3 negocios",
        );
        await assertVisible(page, [(p) => p.getByRole("button", { name: /cancelar/i })], "Cancelar");
        await assertVisible(
          page,
          [(p) => p.getByRole("button", { name: /crear negocio/i })],
          "Crear Negocio",
        );

        await inputNombre.fill("Negocio Prueba Automatización");
        await waitForUi(page);

        await takeScreenshot(page, "03_agregar_negocio_modal", true);

        await clickByText(page, /cancelar/i, "Cancelar modal");
      },
      pageRef,
    );

    await runStep(
      "Administrar Negocios view",
      async () => {
        const adminVisible = await hasVisible(page, [(p) => p.getByText(/administrar negocios/i)], 2_000);
        if (!adminVisible) {
          await clickByText(page, /mi negocio/i, "Mi Negocio re-open");
        }

        await clickByText(page, /administrar negocios/i, "Administrar Negocios");
        await assertVisible(page, [(p) => p.getByText(/informaci.n general/i)], "Información General");
        await assertVisible(
          page,
          [(p) => p.getByText(/detalles de la cuenta/i)],
          "Detalles de la Cuenta",
        );
        await assertVisible(page, [(p) => p.getByText(/tus negocios/i)], "Tus Negocios");
        await assertVisible(page, [(p) => p.getByText(/secci.n legal/i)], "Sección Legal");

        await takeScreenshot(page, "04_administrar_negocios_account_page", true);
      },
      pageRef,
    );

    await runStep(
      "Información General",
      async () => {
        await assertVisible(
          page,
          [(p) => p.getByText(/informaci.n general/i)],
          "Información General section",
        );
        await assertVisible(
          page,
          [
            (p) => p.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i),
          ],
          "user email",
        );
        await assertVisible(
          page,
          [
            (p) => p.getByText(/nombre/i),
            (p) => p.locator("h1, h2, h3").filter({ hasText: /[A-Za-z]{2,}\s+[A-Za-z]{2,}/ }),
          ],
          "user name",
        );
        await assertVisible(page, [(p) => p.getByText(/business plan/i)], "BUSINESS PLAN");
        await assertVisible(
          page,
          [(p) => p.getByRole("button", { name: /cambiar plan/i }), (p) => p.getByText(/cambiar plan/i)],
          "Cambiar Plan button",
        );
      },
      pageRef,
    );

    await runStep(
      "Detalles de la Cuenta",
      async () => {
        await assertVisible(page, [(p) => p.getByText(/cuenta creada/i)], "Cuenta creada");
        await assertVisible(page, [(p) => p.getByText(/estado activo/i)], "Estado activo");
        await assertVisible(
          page,
          [(p) => p.getByText(/idioma seleccionado/i)],
          "Idioma seleccionado",
        );
      },
      pageRef,
    );

    await runStep(
      "Tus Negocios",
      async () => {
        await assertVisible(page, [(p) => p.getByText(/tus negocios/i)], "Tus Negocios section");
        await assertVisible(page, [(p) => p.getByText(/agregar negocio/i)], "Agregar Negocio button");
        await assertVisible(
          page,
          [(p) => p.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)],
          "Tienes 2 de 3 negocios",
        );
      },
      pageRef,
    );

    await runStep(
      "Términos y Condiciones",
      async () => {
        await openLegalLink({
          appPage: page,
          linkPattern: /t.rminos y condiciones/i,
          headingPattern: /t.rminos y condiciones/i,
          reportKey: "Términos y Condiciones",
          screenshotName: "05_terminos_y_condiciones",
        });
      },
      pageRef,
    );

    await runStep(
      "Política de Privacidad",
      async () => {
        await openLegalLink({
          appPage: page,
          linkPattern: /pol.tica de privacidad/i,
          headingPattern: /pol.tica de privacidad/i,
          reportKey: "Política de Privacidad",
          screenshotName: "06_politica_de_privacidad",
        });
      },
      pageRef,
    );
  } finally {
    finalReport.finishedAt = new Date().toISOString();
    const reportPath = path.join(ARTIFACTS_DIR, "final-report.json");
    await fs.writeFile(reportPath, `${JSON.stringify(finalReport, null, 2)}\n`, "utf-8");

    console.log("\n=== Final Validation Report ===");
    console.table(
      REPORT_FIELDS.map((field) => ({
        step: field,
        status: finalReport[field],
      })),
    );
    console.log(`Report JSON: ${reportPath}`);
    console.log(`Artifacts directory: ${ARTIFACTS_DIR}`);

    await context.close().catch(() => {});
    await browser.close().catch(() => {});
  }
}

main()
  .then(() => {
    const hasFailures = REPORT_FIELDS.some((field) => finalReport[field] !== "PASS");
    if (hasFailures) {
      process.exitCode = 1;
    }
  })
  .catch((error) => {
    console.error("Fatal execution error:", error);
    process.exitCode = 1;
  });
