import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright";

const report = {
  Login: "FAIL",
  "Mi Negocio menu": "FAIL",
  "Agregar Negocio modal": "FAIL",
  "Administrar Negocios view": "FAIL",
  "Informacion General": "FAIL",
  "Detalles de la Cuenta": "FAIL",
  "Tus Negocios": "FAIL",
  "Terminos y Condiciones": "FAIL",
  "Politica de Privacidad": "FAIL"
};

const evidence = {
  screenshotDir: "",
  terminosUrl: null,
  privacidadUrl: null,
  failures: []
};

function sanitize(name) {
  return name
    .normalize("NFD")
    .replaceAll(/[\u0300-\u036f]/g, "")
    .replaceAll(/[^a-zA-Z0-9_-]+/g, "_")
    .replaceAll(/_+/g, "_")
    .replaceAll(/^_|_$/g, "")
    .toLowerCase();
}

function textRegex(value) {
  const escaped = value.replaceAll(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return new RegExp(escaped, "i");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 10000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 6000 }).catch(() => {});
  await page.waitForTimeout(700);
}

async function firstVisible(page, locatorFactories, timeoutMs = 15000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    for (const factory of locatorFactories) {
      const locator = factory(page).first();
      const visible = await locator.isVisible().catch(() => false);
      if (visible) {
        return locator;
      }
    }
    await page.waitForTimeout(300);
  }
  return null;
}

async function clickAndWait(page, locator) {
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click({ timeout: 10000 });
  await waitForUi(page);
}

async function ensureVisible(page, label, locatorFactories, timeoutMs = 15000) {
  const locator = await firstVisible(page, locatorFactories, timeoutMs);
  if (!locator) {
    throw new Error(`No se encontro: ${label}`);
  }
  return locator;
}

async function checkpointScreenshot(page, dir, name, fullPage = false) {
  const target = path.join(dir, `${sanitize(name)}.png`);
  await page.screenshot({ path: target, fullPage });
  return target;
}

async function openLegalDocument({
  appPage,
  screenshotDir,
  linkText,
  expectedHeading,
  screenshotName
}) {
  const context = appPage.context();
  const linkLocator = await ensureVisible(appPage, linkText, [
    (p) => p.getByRole("link", { name: textRegex(linkText) }),
    (p) => p.getByText(textRegex(linkText))
  ]);

  const popupPromise = context.waitForEvent("page", { timeout: 5000 }).catch(() => null);
  await clickAndWait(appPage, linkLocator);
  const popup = await popupPromise;

  let legalPage = appPage;
  if (popup) {
    legalPage = popup;
    await legalPage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
    await waitForUi(legalPage);
  }

  await ensureVisible(legalPage, expectedHeading, [
    (p) => p.getByRole("heading", { name: textRegex(expectedHeading) }),
    (p) => p.getByText(textRegex(expectedHeading))
  ], 20000);

  const legalContentVisible = await legalPage
    .locator("main, article, section, p")
    .filter({ hasText: /terminos|condiciones|privacidad|datos|uso/i })
    .first()
    .isVisible()
    .catch(() => false);

  if (!legalContentVisible) {
    throw new Error(`No se detecto contenido legal visible para '${linkText}'.`);
  }

  await checkpointScreenshot(legalPage, screenshotDir, screenshotName, true);
  const finalUrl = legalPage.url();

  if (popup) {
    await legalPage.close({ runBeforeUnload: true }).catch(() => {});
    await appPage.bringToFront().catch(() => {});
    await waitForUi(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUi(appPage);
  }

  return finalUrl;
}

async function runStep(name, fn) {
  console.log(`\n=== ${name} ===`);
  try {
    await fn();
    console.log(`PASS: ${name}`);
    return true;
  } catch (error) {
    console.error(`FAIL: ${name}`);
    console.error(error instanceof Error ? error.message : String(error));
    evidence.failures.push({
      step: name,
      reason: error instanceof Error ? error.message : String(error)
    });
    return false;
  }
}

async function main() {
  const stamp = new Date().toISOString().replaceAll(/[:.]/g, "-");
  const screenshotDir = path.resolve(
    process.cwd(),
    "artifacts",
    "saleads-mi-negocio",
    stamp
  );
  evidence.screenshotDir = screenshotDir;
  await fs.mkdir(screenshotDir, { recursive: true });

  const launchOptions = {
    headless: process.env.HEADLESS !== "false"
  };
  const browser = await chromium.launch(launchOptions);
  const context = await browser.newContext();
  const page = await context.newPage();

  try {
    const saleadsUrl = process.env.SALEADS_URL;
    if (!saleadsUrl) {
      throw new Error(
        "Define SALEADS_URL con la URL de login del entorno actual para ejecutar este flujo."
      );
    }

    await page.goto(saleadsUrl, { waitUntil: "domcontentloaded" });
    await waitForUi(page);

    const loginOk = await runStep("Login with Google", async () => {
      const loginButton = await ensureVisible(page, "Boton de login con Google", [
        (p) => p.getByRole("button", { name: /google/i }),
        (p) => p.getByRole("link", { name: /google/i }),
        (p) => p.getByText(/sign in with google|iniciar sesion con google/i)
      ], 20000);

      const popupPromise = page.waitForEvent("popup", { timeout: 8000 }).catch(() => null);
      await clickAndWait(page, loginButton);
      const popup = await popupPromise;
      const googlePage = popup ?? page;

      if (popup) {
        await googlePage.waitForLoadState("domcontentloaded", { timeout: 15000 }).catch(() => {});
        await waitForUi(googlePage);
      }

      const accountLocator = await firstVisible(googlePage, [
        (p) => p.getByText(/juanlucasbarbiergarzon@gmail\.com/i),
        (p) => p.getByRole("button", { name: /juanlucasbarbiergarzon@gmail\.com/i })
      ], 8000);
      if (accountLocator) {
        await clickAndWait(googlePage, accountLocator);
      }

      if (popup) {
        await popup.waitForEvent("close", { timeout: 30000 }).catch(() => {});
        await page.bringToFront().catch(() => {});
        await waitForUi(page);
      } else {
        await waitForUi(page);
      }

      await ensureVisible(page, "Interfaz principal visible", [
        (p) => p.locator("main"),
        (p) => p.getByText(/dashboard|inicio|panel/i)
      ], 30000);

      await ensureVisible(page, "Sidebar visible", [
        (p) => p.locator("aside"),
        (p) => p.getByRole("navigation")
      ], 30000);

      await checkpointScreenshot(page, screenshotDir, "01-dashboard-cargado", true);
      report.Login = "PASS";
    });

    const menuOk = await runStep("Open Mi Negocio menu", async () => {
      const negocio = await ensureVisible(page, "Menu Negocio", [
        (p) => p.getByRole("button", { name: textRegex("Negocio") }),
        (p) => p.getByRole("link", { name: textRegex("Negocio") }),
        (p) => p.getByText(textRegex("Negocio"))
      ]);
      await clickAndWait(page, negocio);

      const miNegocio = await ensureVisible(page, "Opcion Mi Negocio", [
        (p) => p.getByRole("button", { name: textRegex("Mi Negocio") }),
        (p) => p.getByRole("link", { name: textRegex("Mi Negocio") }),
        (p) => p.getByText(textRegex("Mi Negocio"))
      ]);
      await clickAndWait(page, miNegocio);

      await ensureVisible(page, "Agregar Negocio visible", [
        (p) => p.getByRole("button", { name: textRegex("Agregar Negocio") }),
        (p) => p.getByRole("link", { name: textRegex("Agregar Negocio") }),
        (p) => p.getByText(textRegex("Agregar Negocio"))
      ]);
      await ensureVisible(page, "Administrar Negocios visible", [
        (p) => p.getByRole("button", { name: textRegex("Administrar Negocios") }),
        (p) => p.getByRole("link", { name: textRegex("Administrar Negocios") }),
        (p) => p.getByText(textRegex("Administrar Negocios"))
      ]);

      await checkpointScreenshot(page, screenshotDir, "02-menu-mi-negocio-expandido", false);
      report["Mi Negocio menu"] = "PASS";
    });

    const addBusinessModalOk = await runStep("Validate Agregar Negocio modal", async () => {
      if (!menuOk) {
        const miNegocio = await ensureVisible(page, "Reabrir Mi Negocio", [
          (p) => p.getByText(textRegex("Mi Negocio"))
        ]);
        await clickAndWait(page, miNegocio);
      }

      const agregarNegocio = await ensureVisible(page, "Submenu Agregar Negocio", [
        (p) => p.getByRole("button", { name: textRegex("Agregar Negocio") }),
        (p) => p.getByRole("link", { name: textRegex("Agregar Negocio") }),
        (p) => p.getByText(textRegex("Agregar Negocio"))
      ]);
      await clickAndWait(page, agregarNegocio);

      await ensureVisible(page, "Modal Crear Nuevo Negocio", [
        (p) => p.getByRole("heading", { name: textRegex("Crear Nuevo Negocio") }),
        (p) => p.getByText(textRegex("Crear Nuevo Negocio"))
      ]);
      const businessNameField = await ensureVisible(page, "Campo Nombre del Negocio", [
        (p) => p.getByLabel(textRegex("Nombre del Negocio")),
        (p) => p.getByPlaceholder(textRegex("Nombre del Negocio")),
        (p) => p.locator("input").filter({ hasText: textRegex("Nombre del Negocio") })
      ]);
      await ensureVisible(page, "Texto cupo negocios", [
        (p) => p.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)
      ]);
      await ensureVisible(page, "Boton Cancelar", [
        (p) => p.getByRole("button", { name: textRegex("Cancelar") })
      ]);
      await ensureVisible(page, "Boton Crear Negocio", [
        (p) => p.getByRole("button", { name: /crear negocio/i })
      ]);

      await checkpointScreenshot(page, screenshotDir, "03-modal-crear-negocio", false);

      await businessNameField.fill("Negocio Prueba Automatizacion").catch(() => {});
      const cancelButton = await ensureVisible(page, "Cerrar modal con Cancelar", [
        (p) => p.getByRole("button", { name: textRegex("Cancelar") })
      ]);
      await clickAndWait(page, cancelButton);

      report["Agregar Negocio modal"] = "PASS";
    });

    const adminOk = await runStep("Open Administrar Negocios", async () => {
      const administrarNegocios = await firstVisible(page, [
        (p) => p.getByRole("button", { name: textRegex("Administrar Negocios") }),
        (p) => p.getByRole("link", { name: textRegex("Administrar Negocios") }),
        (p) => p.getByText(textRegex("Administrar Negocios"))
      ], 4000);

      if (!administrarNegocios) {
        const miNegocio = await ensureVisible(page, "Expandir Mi Negocio", [
          (p) => p.getByText(textRegex("Mi Negocio"))
        ]);
        await clickAndWait(page, miNegocio);
      }

      const adminTarget = await ensureVisible(page, "Ir a Administrar Negocios", [
        (p) => p.getByRole("button", { name: textRegex("Administrar Negocios") }),
        (p) => p.getByRole("link", { name: textRegex("Administrar Negocios") }),
        (p) => p.getByText(textRegex("Administrar Negocios"))
      ]);
      await clickAndWait(page, adminTarget);

      await ensureVisible(page, "Seccion Informacion General", [
        (p) => p.getByRole("heading", { name: /informacion general/i }),
        (p) => p.getByText(/informacion general/i)
      ], 20000);
      await ensureVisible(page, "Seccion Detalles de la Cuenta", [
        (p) => p.getByRole("heading", { name: /detalles de la cuenta/i }),
        (p) => p.getByText(/detalles de la cuenta/i)
      ], 20000);
      await ensureVisible(page, "Seccion Tus Negocios", [
        (p) => p.getByRole("heading", { name: /tus negocios/i }),
        (p) => p.getByText(/tus negocios/i)
      ], 20000);
      await ensureVisible(page, "Seccion Legal", [
        (p) => p.getByRole("heading", { name: /seccion legal/i }),
        (p) => p.getByText(/seccion legal/i)
      ], 20000);

      await checkpointScreenshot(page, screenshotDir, "04-administrar-negocios", true);
      report["Administrar Negocios view"] = "PASS";
    });

    await runStep("Validate Informacion General", async () => {
      if (!adminOk && !loginOk && !addBusinessModalOk) {
        throw new Error("No se alcanzo la vista de Administrar Negocios.");
      }

      await ensureVisible(page, "Nombre de usuario", [
        (p) => p.locator("text=/^[A-Za-z].{2,}$/").first()
      ], 12000);
      await ensureVisible(page, "Email de usuario", [
        (p) => p.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i)
      ], 12000);
      await ensureVisible(page, "Texto BUSINESS PLAN", [
        (p) => p.getByText(/business plan/i)
      ]);
      await ensureVisible(page, "Boton Cambiar Plan", [
        (p) => p.getByRole("button", { name: /cambiar plan/i }),
        (p) => p.getByText(/cambiar plan/i)
      ]);

      report["Informacion General"] = "PASS";
    });

    await runStep("Validate Detalles de la Cuenta", async () => {
      await ensureVisible(page, "Cuenta creada", [(p) => p.getByText(/cuenta creada/i)]);
      await ensureVisible(page, "Estado activo", [(p) => p.getByText(/estado activo/i)]);
      await ensureVisible(page, "Idioma seleccionado", [
        (p) => p.getByText(/idioma seleccionado/i)
      ]);
      report["Detalles de la Cuenta"] = "PASS";
    });

    await runStep("Validate Tus Negocios", async () => {
      await ensureVisible(page, "Lista de negocios", [
        (p) => p.getByText(/tus negocios/i),
        (p) => p.locator("table, ul, [role='list']")
      ]);
      await ensureVisible(page, "Boton Agregar Negocio", [
        (p) => p.getByRole("button", { name: /agregar negocio/i }),
        (p) => p.getByText(/agregar negocio/i)
      ]);
      await ensureVisible(page, "Texto cupo negocios 2 de 3", [
        (p) => p.getByText(/tienes\s*2\s*de\s*3\s*negocios/i)
      ]);
      report["Tus Negocios"] = "PASS";
    });

    await runStep("Validate Terminos y Condiciones", async () => {
      evidence.terminosUrl = await openLegalDocument({
        appPage: page,
        screenshotDir,
        linkText: "Terminos y Condiciones",
        expectedHeading: "Terminos y Condiciones",
        screenshotName: "05-terminos-y-condiciones"
      });
      report["Terminos y Condiciones"] = "PASS";
    });

    await runStep("Validate Politica de Privacidad", async () => {
      evidence.privacidadUrl = await openLegalDocument({
        appPage: page,
        screenshotDir,
        linkText: "Politica de Privacidad",
        expectedHeading: "Politica de Privacidad",
        screenshotName: "06-politica-de-privacidad"
      });
      report["Politica de Privacidad"] = "PASS";
    });
  } finally {
    await browser.close();
  }

  const summary = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    report,
    evidence
  };

  const reportPath = path.join(evidence.screenshotDir, "final-report.json");
  await fs.writeFile(reportPath, JSON.stringify(summary, null, 2), "utf8");

  console.log("\n=== FINAL REPORT ===");
  for (const [key, value] of Object.entries(report)) {
    console.log(`- ${key}: ${value}`);
  }
  console.log(`- Terminos y Condiciones URL: ${evidence.terminosUrl ?? "N/A"}`);
  console.log(`- Politica de Privacidad URL: ${evidence.privacidadUrl ?? "N/A"}`);
  console.log(`- Evidencia: ${evidence.screenshotDir}`);
  console.log(`- JSON: ${reportPath}`);

  const hasFailures = Object.values(report).includes("FAIL");
  process.exitCode = hasFailures ? 1 : 0;
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
