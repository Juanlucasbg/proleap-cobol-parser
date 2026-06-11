import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright";

const DEFAULT_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
const DEFAULT_TIMEOUT_MS = Number(process.env.SALEADS_TIMEOUT_MS ?? "20000");
const BASE_URL = process.env.SALEADS_BASE_URL ?? process.env.SALEADS_URL ?? null;
const GOOGLE_ACCOUNT = process.env.SALEADS_GOOGLE_ACCOUNT ?? DEFAULT_GOOGLE_ACCOUNT;
const CDP_ENDPOINT = process.env.SALEADS_CDP_URL ?? null;
const ARTIFACT_ROOT =
  process.env.SALEADS_ARTIFACT_DIR ?? path.join("artifacts", "saleads-mi-negocio");

const STEP_LABELS = [
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

function timestampLabel() {
  return new Date().toISOString().replace(/[:.]/g, "-");
}

function sanitizeFilename(value) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "");
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function passFail(value) {
  return value ? "PASS" : "FAIL";
}

async function ensureDir(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

async function isVisible(locator) {
  try {
    return await locator.first().isVisible({ timeout: 1500 });
  } catch {
    return false;
  }
}

async function waitForUiLoad(page) {
  try {
    await page.waitForLoadState("domcontentloaded", { timeout: DEFAULT_TIMEOUT_MS });
  } catch {
    // No-op: keep progressing if event already passed.
  }

  try {
    await page.waitForLoadState("networkidle", { timeout: 8000 });
  } catch {
    // No-op: network activity may be continuous in SPA apps.
  }
}

async function firstVisible(candidates) {
  for (const locator of candidates) {
    if (await isVisible(locator)) {
      return locator.first();
    }
  }

  return null;
}

async function waitForVisible(candidates, timeoutMs, errorMessage) {
  const start = Date.now();

  while (Date.now() - start < timeoutMs) {
    const visible = await firstVisible(candidates);
    if (visible) {
      return visible;
    }

    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  throw new Error(errorMessage);
}

async function main() {
  const runDir = path.join(ARTIFACT_ROOT, timestampLabel());
  await ensureDir(runDir);

  const report = {
    runAt: new Date().toISOString(),
    environment: {
      baseUrl: BASE_URL,
      usedCdpConnection: Boolean(CDP_ENDPOINT),
      googleAccount: GOOGLE_ACCOUNT
    },
    steps: Object.fromEntries(STEP_LABELS.map((label) => [label, { status: "PENDING" }])),
    evidence: {
      screenshots: [],
      legalUrls: {}
    }
  };

  let screenshotIndex = 0;
  let browser;
  let context;
  let page;

  const markStep = (label, success, details) => {
    report.steps[label] = {
      status: passFail(success),
      details
    };
  };

  const capture = async (name, targetPage = page, fullPage = false) => {
    screenshotIndex += 1;
    const fileName = `${String(screenshotIndex).padStart(2, "0")}-${sanitizeFilename(name)}.png`;
    const filePath = path.join(runDir, fileName);

    await targetPage.screenshot({ path: filePath, fullPage });
    report.evidence.screenshots.push(filePath);
    return filePath;
  };

  const runStep = async (label, callback) => {
    try {
      await callback();
      markStep(label, true, "Completed validations successfully.");
    } catch (error) {
      markStep(label, false, error instanceof Error ? error.message : String(error));
      if (page && !page.isClosed()) {
        try {
          await capture(`${label}-failure`, page, true);
        } catch {
          // Ignore screenshot failures.
        }
      }
    }
  };

  const requireVisibleText = async (targetPage, regex, contextLabel) => {
    const locator = await waitForVisible(
      [
        targetPage.getByRole("heading", { name: regex }),
        targetPage.getByText(regex),
        targetPage.locator(`text=${regex.source}`)
      ],
      DEFAULT_TIMEOUT_MS,
      `${contextLabel} not visible (${regex}).`
    );

    await locator.waitFor({ state: "visible", timeout: DEFAULT_TIMEOUT_MS });
  };

  const openLegalPage = async (linkRegex, headingRegex, evidenceName, reportKey) => {
    const link = await waitForVisible(
      [page.getByRole("link", { name: linkRegex }), page.getByText(linkRegex)],
      DEFAULT_TIMEOUT_MS,
      `Legal link not found: ${linkRegex}.`
    );

    const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
    await link.click();
    await waitForUiLoad(page);

    let legalPage = await popupPromise;
    const openedNewTab = Boolean(legalPage);

    if (!legalPage) {
      legalPage = page;
    }

    await waitForUiLoad(legalPage);
    await requireVisibleText(legalPage, headingRegex, "Legal heading");

    const legalText = (await legalPage.locator("body").innerText()).trim();
    if (legalText.length < 60) {
      throw new Error("Legal content appears too short or empty.");
    }

    report.evidence.legalUrls[reportKey] = legalPage.url();
    await capture(evidenceName, legalPage, true);

    if (openedNewTab) {
      await legalPage.close();
      await page.bringToFront();
      await waitForUiLoad(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded", timeout: DEFAULT_TIMEOUT_MS });
      await waitForUiLoad(page);
    }
  };

  try {
    if (CDP_ENDPOINT) {
      browser = await chromium.connectOverCDP(CDP_ENDPOINT);
      context = browser.contexts()[0] ?? (await browser.newContext());
      page = context.pages()[0] ?? (await context.newPage());
    } else {
      browser = await chromium.launch({
        headless: process.env.SALEADS_HEADLESS !== "false"
      });
      context = await browser.newContext();
      page = await context.newPage();
    }

    if (BASE_URL) {
      await page.goto(BASE_URL, { waitUntil: "domcontentloaded", timeout: DEFAULT_TIMEOUT_MS });
      await waitForUiLoad(page);
    } else if (page.url() === "about:blank") {
      throw new Error(
        "No URL available. Set SALEADS_BASE_URL/SALEADS_URL or connect to an existing browser with SALEADS_CDP_URL."
      );
    }

    await runStep("Login", async () => {
      const loginButton = await waitForVisible(
        [
          page.getByRole("button", {
            name: /sign in with google|inicia sesi[oó]n con google|continuar con google|ingresar con google/i
          }),
          page.getByText(/sign in with google|inicia sesi[oó]n con google|continuar con google|ingresar con google/i)
        ],
        DEFAULT_TIMEOUT_MS,
        "Google login button was not found."
      );

      const popupPromise = page.waitForEvent("popup", { timeout: 7000 }).catch(() => null);
      await loginButton.click();
      await waitForUiLoad(page);

      const authPage = (await popupPromise) ?? page;
      await waitForUiLoad(authPage);

      const accountLocator = await firstVisible([
        authPage.getByText(new RegExp(`^${escapeRegex(GOOGLE_ACCOUNT)}$`, "i")),
        authPage.getByText(new RegExp(escapeRegex(GOOGLE_ACCOUNT), "i"))
      ]);

      if (accountLocator) {
        await accountLocator.click();
        await waitForUiLoad(authPage);
      }

      await waitForVisible(
        [
          page.getByText(/^Negocio$/i),
          page.getByRole("navigation"),
          page.locator("aside")
        ],
        30000,
        "Main app interface with left sidebar did not appear after login."
      );

      await capture("dashboard-loaded", page, true);
    });

    await runStep("Mi Negocio menu", async () => {
      const negocioItem = await waitForVisible(
        [
          page.getByRole("button", { name: /^Negocio$/i }),
          page.getByRole("link", { name: /^Negocio$/i }),
          page.getByText(/^Negocio$/i)
        ],
        DEFAULT_TIMEOUT_MS,
        "Sidebar item 'Negocio' was not found."
      );
      await negocioItem.click();
      await waitForUiLoad(page);

      const miNegocioItem = await waitForVisible(
        [
          page.getByRole("button", { name: /^Mi Negocio$/i }),
          page.getByRole("link", { name: /^Mi Negocio$/i }),
          page.getByText(/^Mi Negocio$/i)
        ],
        DEFAULT_TIMEOUT_MS,
        "'Mi Negocio' option was not found."
      );
      await miNegocioItem.click();
      await waitForUiLoad(page);

      await waitForVisible(
        [page.getByText(/^Agregar Negocio$/i), page.getByRole("button", { name: /^Agregar Negocio$/i })],
        DEFAULT_TIMEOUT_MS,
        "'Agregar Negocio' is not visible after expanding menu."
      );
      await waitForVisible(
        [page.getByText(/^Administrar Negocios$/i), page.getByRole("link", { name: /^Administrar Negocios$/i })],
        DEFAULT_TIMEOUT_MS,
        "'Administrar Negocios' is not visible after expanding menu."
      );

      await capture("mi-negocio-expanded-menu", page);
    });

    await runStep("Agregar Negocio modal", async () => {
      const agregarNegocio = await waitForVisible(
        [page.getByRole("button", { name: /^Agregar Negocio$/i }), page.getByText(/^Agregar Negocio$/i)],
        DEFAULT_TIMEOUT_MS,
        "Could not find 'Agregar Negocio' action."
      );
      await agregarNegocio.click();
      await waitForUiLoad(page);

      await waitForVisible(
        [page.getByRole("heading", { name: /^Crear Nuevo Negocio$/i }), page.getByText(/^Crear Nuevo Negocio$/i)],
        DEFAULT_TIMEOUT_MS,
        "Modal title 'Crear Nuevo Negocio' is not visible."
      );

      await waitForVisible(
        [page.getByLabel(/Nombre del Negocio/i), page.getByPlaceholder(/Nombre del Negocio/i)],
        DEFAULT_TIMEOUT_MS,
        "Input 'Nombre del Negocio' is missing."
      );
      await requireVisibleText(page, /Tienes 2 de 3 negocios/i, "Business quota text");
      await waitForVisible(
        [page.getByRole("button", { name: /^Cancelar$/i }), page.getByText(/^Cancelar$/i)],
        DEFAULT_TIMEOUT_MS,
        "Button 'Cancelar' is missing."
      );
      await waitForVisible(
        [page.getByRole("button", { name: /^Crear Negocio$/i }), page.getByText(/^Crear Negocio$/i)],
        DEFAULT_TIMEOUT_MS,
        "Button 'Crear Negocio' is missing."
      );

      await capture("agregar-negocio-modal", page, true);

      const nameInput = await firstVisible([page.getByLabel(/Nombre del Negocio/i), page.getByPlaceholder(/Nombre del Negocio/i)]);
      if (nameInput) {
        await nameInput.fill("Negocio Prueba Automatización");
      }

      const cancelButton = await waitForVisible(
        [page.getByRole("button", { name: /^Cancelar$/i }), page.getByText(/^Cancelar$/i)],
        DEFAULT_TIMEOUT_MS,
        "Button 'Cancelar' not found to close modal."
      );
      await cancelButton.click();
      await waitForUiLoad(page);
    });

    await runStep("Administrar Negocios view", async () => {
      const administrarNegociosCandidates = [
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i)
      ];

      let administrarNegocios = await firstVisible(administrarNegociosCandidates);
      if (!administrarNegocios) {
        const miNegocio = await waitForVisible(
          [page.getByText(/^Mi Negocio$/i), page.getByRole("button", { name: /^Mi Negocio$/i })],
          DEFAULT_TIMEOUT_MS,
          "'Mi Negocio' option not found while reopening menu."
        );
        await miNegocio.click();
        await waitForUiLoad(page);
        administrarNegocios = await waitForVisible(
          administrarNegociosCandidates,
          DEFAULT_TIMEOUT_MS,
          "'Administrar Negocios' option is still not visible."
        );
      }

      await administrarNegocios.click();
      await waitForUiLoad(page);

      await requireVisibleText(page, /Informaci[oó]n General/i, "Section");
      await requireVisibleText(page, /Detalles de la Cuenta/i, "Section");
      await requireVisibleText(page, /Tus Negocios/i, "Section");
      await requireVisibleText(page, /Secci[oó]n Legal/i, "Section");

      await capture("administrar-negocios-account-page", page, true);
    });

    await runStep("Información General", async () => {
      await requireVisibleText(page, /BUSINESS PLAN/i, "Business plan label");
      await waitForVisible(
        [page.getByRole("button", { name: /Cambiar Plan/i }), page.getByText(/Cambiar Plan/i)],
        DEFAULT_TIMEOUT_MS,
        "Button 'Cambiar Plan' was not found."
      );

      const emailRegex = /\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/i;
      const accountText = await page.locator("body").innerText();
      if (!emailRegex.test(accountText)) {
        throw new Error("User email is not visible in Información General.");
      }

      const userNameIndicator = await firstVisible([
        page.getByText(/Hola|Bienvenido|Perfil|Usuario/i),
        page.locator("h1, h2, h3").filter({ hasText: /[A-Za-z]/ })
      ]);
      if (!userNameIndicator) {
        throw new Error("Unable to confirm that user name is visible.");
      }
    });

    await runStep("Detalles de la Cuenta", async () => {
      await requireVisibleText(page, /Cuenta creada/i, "Detalles de la Cuenta");
      await requireVisibleText(page, /Estado activo/i, "Detalles de la Cuenta");
      await requireVisibleText(page, /Idioma seleccionado/i, "Detalles de la Cuenta");
    });

    await runStep("Tus Negocios", async () => {
      await requireVisibleText(page, /Tus Negocios/i, "Tus Negocios");
      await waitForVisible(
        [page.getByRole("button", { name: /^Agregar Negocio$/i }), page.getByText(/^Agregar Negocio$/i)],
        DEFAULT_TIMEOUT_MS,
        "Button 'Agregar Negocio' is missing in Tus Negocios."
      );
      await requireVisibleText(page, /Tienes 2 de 3 negocios/i, "Tus Negocios quota");

      const businessList = await firstVisible([
        page.locator("ul, table, [role='list']").filter({ hasText: /negocio|business/i }),
        page.getByText(/Negocio/i)
      ]);
      if (!businessList) {
        throw new Error("Business list is not visible in Tus Negocios.");
      }
    });

    await runStep("Términos y Condiciones", async () => {
      await openLegalPage(
        /T[eé]rminos y Condiciones/i,
        /T[eé]rminos y Condiciones/i,
        "terminos-y-condiciones",
        "terminosYCondicionesUrl"
      );
    });

    await runStep("Política de Privacidad", async () => {
      await openLegalPage(
        /Pol[ií]tica de Privacidad/i,
        /Pol[ií]tica de Privacidad/i,
        "politica-de-privacidad",
        "politicaDePrivacidadUrl"
      );
    });
  } finally {
    if (browser) {
      await browser.close();
    }
  }

  const finalReport = {
    Login: report.steps["Login"].status,
    "Mi Negocio menu": report.steps["Mi Negocio menu"].status,
    "Agregar Negocio modal": report.steps["Agregar Negocio modal"].status,
    "Administrar Negocios view": report.steps["Administrar Negocios view"].status,
    "Información General": report.steps["Información General"].status,
    "Detalles de la Cuenta": report.steps["Detalles de la Cuenta"].status,
    "Tus Negocios": report.steps["Tus Negocios"].status,
    "Términos y Condiciones": report.steps["Términos y Condiciones"].status,
    "Política de Privacidad": report.steps["Política de Privacidad"].status
  };

  const output = {
    ...report,
    finalReport
  };

  const reportPath = path.join(runDir, "final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(output, null, 2), "utf8");

  process.stdout.write(`${JSON.stringify(output, null, 2)}\n`);
}

main().catch((error) => {
  const message = error instanceof Error ? error.message : String(error);
  process.stderr.write(`saleads_mi_negocio_full_test failed: ${message}\n`);
  process.exitCode = 1;
});
