import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright";

const STEP_FIELDS = [
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

function parseCliArgs(argv) {
  const parsed = {};

  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];

    if (arg.startsWith("--start-url=")) {
      parsed.startUrl = arg.split("=").slice(1).join("=");
    } else if (arg === "--start-url") {
      parsed.startUrl = argv[i + 1];
      i += 1;
    } else if (arg.startsWith("--output-dir=")) {
      parsed.outputDir = arg.split("=").slice(1).join("=");
    } else if (arg === "--output-dir") {
      parsed.outputDir = argv[i + 1];
      i += 1;
    } else if (arg === "--headed") {
      parsed.headless = false;
    }
  }

  return parsed;
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function resultRecord(status = "FAIL", details = "Not executed.") {
  return { status, details };
}

async function waitForUi(page, waitMs = 1200) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(waitMs);
}

async function firstVisible(page, locators, timeoutMs = 15000) {
  const end = Date.now() + timeoutMs;

  while (Date.now() < end) {
    for (const locator of locators) {
      try {
        if (await locator.first().isVisible({ timeout: 300 })) {
          return locator.first();
        }
      } catch {
        // Continue until we find a visible match.
      }
    }

    await page.waitForTimeout(250);
  }

  return null;
}

async function expectVisible(page, locator, message, timeoutMs = 15000) {
  try {
    await locator.first().waitFor({ state: "visible", timeout: timeoutMs });
    return true;
  } catch {
    throw new Error(message);
  }
}

async function clickVisible(page, locators, description, timeoutMs = 15000) {
  const locator = await firstVisible(page, locators, timeoutMs);
  if (!locator) {
    throw new Error(`Could not find clickable element for: ${description}`);
  }

  await locator.click();
  await waitForUi(page);
}

async function chooseGoogleAccountIfVisible(targetPage, email) {
  const accountLocators = [
    targetPage.getByText(new RegExp(escapeRegex(email), "i")),
    targetPage.getByRole("button", { name: new RegExp(escapeRegex(email), "i") }),
    targetPage.locator(`[data-email="${email}"]`),
    targetPage.locator(`text=${email}`)
  ];

  const account = await firstVisible(targetPage, accountLocators, 10000);
  if (account) {
    await account.click();
    await waitForUi(targetPage, 1800);
    return true;
  }

  return false;
}

async function verifyTextVisible(page, textRegex, failureMessage) {
  const locator = page.getByText(textRegex).first();
  await expectVisible(page, locator, failureMessage);
}

async function main() {
  const cli = parseCliArgs(process.argv.slice(2));
  const startUrl = cli.startUrl ?? process.env.SALEADS_START_URL ?? process.env.SALEADS_LOGIN_URL;

  if (!startUrl) {
    throw new Error(
      "A start URL is required. Provide --start-url <url> or SALEADS_START_URL/SALEADS_LOGIN_URL."
    );
  }

  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
  const outputDir =
    cli.outputDir ?? path.resolve(process.cwd(), "artifacts", "saleads-mi-negocio", timestamp);
  const screenshotsDir = path.join(outputDir, "screenshots");

  await fs.mkdir(screenshotsDir, { recursive: true });

  const results = Object.fromEntries(STEP_FIELDS.map((field) => [field, resultRecord()]));
  const evidence = {
    screenshots: [],
    urls: {}
  };

  const browser = await chromium.launch({
    headless: cli.headless ?? process.env.HEADLESS !== "false"
  });
  const context = await browser.newContext();
  const page = await context.newPage();

  try {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    // Step 1: Login with Google.
    const googleLoginButton = await firstVisible(page, [
      page.getByRole("button", {
        name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i
      }),
      page.getByRole("link", {
        name: /sign in with google|iniciar sesi[oó]n con google|continuar con google|google/i
      }),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google|continuar con google/i)
    ]);

    if (!googleLoginButton) {
      throw new Error("Google sign-in button was not found on login page.");
    }

    const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
    await googleLoginButton.click();
    await waitForUi(page, 2000);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded");
      await chooseGoogleAccountIfVisible(popup, "juanlucasbarbiergarzon@gmail.com");
      await popup.waitForTimeout(2000);
    } else {
      await chooseGoogleAccountIfVisible(page, "juanlucasbarbiergarzon@gmail.com");
    }

    const mainInterfaceIndicators = [
      page.getByRole("navigation"),
      page.getByText(/mi negocio|negocio|dashboard|inicio/i),
      page.getByRole("link", { name: /mi negocio|negocio/i })
    ];

    const sidebarOrApp = await firstVisible(page, mainInterfaceIndicators, 30000);
    if (!sidebarOrApp) {
      throw new Error("Main application interface and left sidebar did not appear after login.");
    }

    const dashboardScreenshot = path.join(screenshotsDir, "01-dashboard-loaded.png");
    await page.screenshot({ path: dashboardScreenshot, fullPage: true });
    evidence.screenshots.push(dashboardScreenshot);
    results.Login = resultRecord("PASS", "Dashboard loaded and left sidebar/main interface visible.");

    // Step 2: Open Mi Negocio menu.
    await clickVisible(
      page,
      [
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByText(/^mi negocio$/i)
      ],
      "Mi Negocio menu"
    );

    const negocioSection = await firstVisible(page, [
      page.getByText(/^negocio$/i),
      page.getByRole("heading", { name: /negocio/i }),
      page.getByRole("link", { name: /negocio/i })
    ]);
    if (!negocioSection) {
      throw new Error("Section labeled 'Negocio' was not found in the left sidebar.");
    }

    await verifyTextVisible(
      page,
      /agregar negocio/i,
      "Submenu item 'Agregar Negocio' was not visible after expanding Mi Negocio."
    );
    await verifyTextVisible(
      page,
      /administrar negocios/i,
      "Submenu item 'Administrar Negocios' was not visible after expanding Mi Negocio."
    );

    const menuScreenshot = path.join(screenshotsDir, "02-mi-negocio-menu-expanded.png");
    await page.screenshot({ path: menuScreenshot, fullPage: true });
    evidence.screenshots.push(menuScreenshot);
    results["Mi Negocio menu"] = resultRecord(
      "PASS",
      "Mi Negocio expanded with Agregar Negocio and Administrar Negocios visible."
    );

    // Step 3: Validate Agregar Negocio modal.
    await clickVisible(
      page,
      [
        page.getByRole("link", { name: /agregar negocio/i }),
        page.getByRole("button", { name: /agregar negocio/i }),
        page.getByText(/^agregar negocio$/i)
      ],
      "Agregar Negocio"
    );

    await verifyTextVisible(page, /crear nuevo negocio/i, "Modal title 'Crear Nuevo Negocio' not found.");
    await verifyTextVisible(page, /nombre del negocio/i, "Input label 'Nombre del Negocio' not found.");
    await verifyTextVisible(
      page,
      /tienes\s*2\s*de\s*3\s*negocios/i,
      "Usage text 'Tienes 2 de 3 negocios' not found."
    );
    await verifyTextVisible(page, /cancelar/i, "Button 'Cancelar' not found in modal.");
    await verifyTextVisible(page, /crear negocio/i, "Button 'Crear Negocio' not found in modal.");

    const input = await firstVisible(page, [
      page.getByRole("textbox", { name: /nombre del negocio/i }),
      page.getByPlaceholder(/nombre del negocio/i),
      page.locator("input").filter({ hasText: "" })
    ]);
    if (input) {
      await input.fill("Negocio Prueba Automatización");
      await waitForUi(page, 800);
    }

    const modalScreenshot = path.join(screenshotsDir, "03-agregar-negocio-modal.png");
    await page.screenshot({ path: modalScreenshot, fullPage: true });
    evidence.screenshots.push(modalScreenshot);

    await clickVisible(
      page,
      [page.getByRole("button", { name: /cancelar/i }), page.getByText(/^cancelar$/i)],
      "Cancelar modal"
    );

    results["Agregar Negocio modal"] = resultRecord(
      "PASS",
      "Crear Nuevo Negocio modal validated with all required fields and controls."
    );

    // Step 4: Open Administrar Negocios.
    if (!(await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false))) {
      await clickVisible(
        page,
        [
          page.getByRole("link", { name: /mi negocio/i }),
          page.getByRole("button", { name: /mi negocio/i }),
          page.getByText(/^mi negocio$/i)
        ],
        "Re-expand Mi Negocio"
      );
    }

    await clickVisible(
      page,
      [
        page.getByRole("link", { name: /administrar negocios/i }),
        page.getByRole("button", { name: /administrar negocios/i }),
        page.getByText(/^administrar negocios$/i)
      ],
      "Administrar Negocios"
    );

    await verifyTextVisible(page, /informaci[oó]n general/i, "Section 'Información General' not found.");
    await verifyTextVisible(page, /detalles de la cuenta/i, "Section 'Detalles de la Cuenta' not found.");
    await verifyTextVisible(page, /tus negocios/i, "Section 'Tus Negocios' not found.");
    await verifyTextVisible(page, /secci[oó]n legal/i, "Section 'Sección Legal' not found.");

    const accountPageScreenshot = path.join(screenshotsDir, "04-administrar-negocios-page.png");
    await page.screenshot({ path: accountPageScreenshot, fullPage: true });
    evidence.screenshots.push(accountPageScreenshot);
    results["Administrar Negocios view"] = resultRecord(
      "PASS",
      "Account page loaded with Información General, Detalles de la Cuenta, Tus Negocios and Sección Legal."
    );

    // Step 5: Validate Información General.
    await verifyTextVisible(
      page,
      /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/,
      "User email is not visible in Información General."
    );
    await verifyTextVisible(page, /business plan/i, "Text 'BUSINESS PLAN' not visible.");
    await verifyTextVisible(page, /cambiar plan/i, "Button 'Cambiar Plan' not visible.");

    const visibleName = await firstVisible(page, [
      page.getByText(/bienvenido|hola|usuario|nombre/i),
      page.locator("h1, h2, h3")
    ]);
    if (!visibleName) {
      throw new Error("User name was not detected in Información General.");
    }
    results["Información General"] = resultRecord(
      "PASS",
      "User name, email, BUSINESS PLAN and Cambiar Plan are visible."
    );

    // Step 6: Validate Detalles de la Cuenta.
    await verifyTextVisible(page, /cuenta creada/i, "'Cuenta creada' is not visible.");
    await verifyTextVisible(page, /estado activo/i, "'Estado activo' is not visible.");
    await verifyTextVisible(page, /idioma seleccionado/i, "'Idioma seleccionado' is not visible.");
    results["Detalles de la Cuenta"] = resultRecord(
      "PASS",
      "'Cuenta creada', 'Estado activo' and 'Idioma seleccionado' were found."
    );

    // Step 7: Validate Tus Negocios.
    await verifyTextVisible(page, /tus negocios/i, "Business list section is not visible.");
    await verifyTextVisible(page, /agregar negocio/i, "Button 'Agregar Negocio' is not visible in Tus Negocios.");
    await verifyTextVisible(
      page,
      /tienes\s*2\s*de\s*3\s*negocios/i,
      "Text 'Tienes 2 de 3 negocios' is not visible in Tus Negocios."
    );
    results["Tus Negocios"] = resultRecord(
      "PASS",
      "Business list, Agregar Negocio button and usage text are visible."
    );

    // Step 8: Validate Términos y Condiciones.
    const beforeTermsUrl = page.url();
    const termsPopupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickVisible(
      page,
      [
        page.getByRole("link", { name: /t[eé]rminos y condiciones/i }),
        page.getByText(/t[eé]rminos y condiciones/i)
      ],
      "Términos y Condiciones link"
    );
    const termsPopup = await termsPopupPromise;
    const termsPage = termsPopup ?? page;

    await waitForUi(termsPage, 1800);
    await verifyTextVisible(
      termsPage,
      /t[eé]rminos y condiciones/i,
      "Heading 'Términos y Condiciones' not found on legal page."
    );
    await verifyTextVisible(
      termsPage,
      /t[eé]rminos|condiciones|legal|privacidad/i,
      "Legal content text not visible on Términos y Condiciones page."
    );

    const termsScreenshot = path.join(screenshotsDir, "05-terminos-y-condiciones.png");
    await termsPage.screenshot({ path: termsScreenshot, fullPage: true });
    evidence.screenshots.push(termsScreenshot);
    evidence.urls.terminosYCondiciones = termsPage.url();

    if (termsPopup) {
      await termsPopup.close();
      await page.bringToFront();
    } else if (page.url() !== beforeTermsUrl) {
      await page.goBack({ waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }
    results["Términos y Condiciones"] = resultRecord(
      "PASS",
      `Validated legal page and captured URL: ${evidence.urls.terminosYCondiciones}`
    );

    // Step 9: Validate Política de Privacidad.
    const beforePrivacyUrl = page.url();
    const privacyPopupPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
    await clickVisible(
      page,
      [
        page.getByRole("link", { name: /pol[ií]tica de privacidad/i }),
        page.getByText(/pol[ií]tica de privacidad/i)
      ],
      "Política de Privacidad link"
    );
    const privacyPopup = await privacyPopupPromise;
    const privacyPage = privacyPopup ?? page;

    await waitForUi(privacyPage, 1800);
    await verifyTextVisible(
      privacyPage,
      /pol[ií]tica de privacidad/i,
      "Heading 'Política de Privacidad' not found on legal page."
    );
    await verifyTextVisible(
      privacyPage,
      /privacidad|datos|legal|informaci[oó]n/i,
      "Legal content text not visible on Política de Privacidad page."
    );

    const privacyScreenshot = path.join(screenshotsDir, "06-politica-de-privacidad.png");
    await privacyPage.screenshot({ path: privacyScreenshot, fullPage: true });
    evidence.screenshots.push(privacyScreenshot);
    evidence.urls.politicaDePrivacidad = privacyPage.url();

    if (privacyPopup) {
      await privacyPopup.close();
      await page.bringToFront();
    } else if (page.url() !== beforePrivacyUrl) {
      await page.goBack({ waitUntil: "domcontentloaded" });
      await waitForUi(page);
    }
    results["Política de Privacidad"] = resultRecord(
      "PASS",
      `Validated legal page and captured URL: ${evidence.urls.politicaDePrivacidad}`
    );
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    const screenshotPath = path.join(screenshotsDir, "error-state.png");
    await page.screenshot({ path: screenshotPath, fullPage: true }).catch(() => {});
    evidence.screenshots.push(screenshotPath);

    for (const field of STEP_FIELDS) {
      if (results[field].status !== "PASS") {
        results[field] = resultRecord("FAIL", message);
      }
    }
  } finally {
    const report = {
      testName: "saleads_mi_negocio_full_test",
      generatedAt: new Date().toISOString(),
      startUrl,
      outputDir,
      results,
      evidence
    };

    const reportPath = path.join(outputDir, "report.json");
    await fs.writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");

    console.log("=== SaleADS Mi Negocio Full Test Report ===");
    for (const field of STEP_FIELDS) {
      console.log(`${field}: ${results[field].status} - ${results[field].details}`);
    }
    console.log(`Report JSON: ${reportPath}`);

    await browser.close();

    const hasFailures = STEP_FIELDS.some((field) => results[field].status !== "PASS");
    process.exitCode = hasFailures ? 1 : 0;
  }
}

main().catch((error) => {
  const message = error instanceof Error ? error.stack ?? error.message : String(error);
  console.error(message);
  process.exitCode = 1;
});
