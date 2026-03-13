import { test, expect } from "@playwright/test";
import fs from "node:fs/promises";
import path from "node:path";

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const ARTIFACTS_DIR = path.resolve(process.cwd(), "artifacts", "saleads_mi_negocio_full_test");
const REPORT_FILE = path.join(ARTIFACTS_DIR, "final-report.json");

const REPORT_FIELDS = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informaci\u00f3n General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "T\u00e9rminos y Condiciones",
  "Pol\u00edtica de Privacidad"
];

const report = Object.fromEntries(
  REPORT_FIELDS.map((field) => [field, { status: "FAIL", details: "Not executed." }])
);

const evidence = {
  terminosYCondicionesUrl: "",
  politicaDePrivacidadUrl: ""
};

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function ensureArtifactsDir() {
  await fs.mkdir(ARTIFACTS_DIR, { recursive: true });
}

async function waitForUiToSettle(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => {});
  await page.waitForTimeout(900);
}

async function takeCheckpoint(page, fileName, fullPage = false) {
  await page.screenshot({
    path: path.join(ARTIFACTS_DIR, fileName),
    fullPage
  });
}

async function clickByVisibleText(page, textOptions) {
  for (const text of textOptions) {
    const exactPattern = new RegExp(`^\\s*${escapeRegex(text)}\\s*$`, "i");
    const locatorOptions = [
      page.getByRole("button", { name: exactPattern }),
      page.getByRole("link", { name: exactPattern }),
      page.getByRole("menuitem", { name: exactPattern }),
      page.getByRole("tab", { name: exactPattern }),
      page.getByText(exactPattern)
    ];

    for (const locator of locatorOptions) {
      const candidate = locator.first();
      const isVisible = await candidate.isVisible().catch(() => false);
      if (isVisible) {
        await candidate.click();
        await waitForUiToSettle(page);
        return;
      }
    }
  }

  throw new Error(`Could not click any of these visible texts: ${textOptions.join(", ")}`);
}

async function assertTextVisible(page, text, timeout = 20000) {
  const pattern = new RegExp(escapeRegex(text), "i");
  await expect(page.getByText(pattern).first()).toBeVisible({ timeout });
}

async function assertAnyTextVisible(page, textOptions, timeout = 20000) {
  for (const text of textOptions) {
    const locator = page.getByText(new RegExp(escapeRegex(text), "i")).first();
    const visible = await locator.isVisible().catch(() => false);
    if (visible) {
      return text;
    }
  }

  for (const text of textOptions) {
    const locator = page.getByText(new RegExp(escapeRegex(text), "i")).first();
    await locator.waitFor({ state: "visible", timeout }).catch(() => {});
    const visible = await locator.isVisible().catch(() => false);
    if (visible) {
      return text;
    }
  }

  throw new Error(`None of these texts are visible: ${textOptions.join(", ")}`);
}

async function getSectionByHeading(page, headingOptions) {
  for (const heading of headingOptions) {
    const headingLocator = page.getByText(new RegExp(`^\\s*${escapeRegex(heading)}\\s*$`, "i")).first();
    const isVisible = await headingLocator.isVisible().catch(() => false);
    if (!isVisible) {
      continue;
    }

    const sectionLike = page
      .locator("section, article, div")
      .filter({ has: headingLocator })
      .first();
    const sectionVisible = await sectionLike.isVisible().catch(() => false);
    if (sectionVisible) {
      return sectionLike;
    }
  }

  throw new Error(`Unable to find section by headings: ${headingOptions.join(", ")}`);
}

async function runStep(field, callback) {
  try {
    await callback();
    report[field] = { status: "PASS", details: "All validations passed." };
  } catch (error) {
    report[field] = {
      status: "FAIL",
      details: error instanceof Error ? error.message : String(error)
    };
  }
}

async function openLegalDocumentAndReturn(page, context, linkOptions, expectedHeadingOptions, screenshotName) {
  const previousUrl = page.url();
  const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);

  await clickByVisibleText(page, linkOptions);

  const popup = await popupPromise;
  const targetPage = popup ?? page;
  await targetPage.waitForLoadState("domcontentloaded", { timeout: 30000 });
  await waitForUiToSettle(targetPage);

  await assertAnyTextVisible(targetPage, expectedHeadingOptions, 30000);

  const textContent = await targetPage.locator("body").innerText();
  if (textContent.trim().length < 120) {
    throw new Error(`Legal page text appears too short for: ${expectedHeadingOptions.join(" / ")}`);
  }

  await takeCheckpoint(targetPage, screenshotName, true);
  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close();
    await page.bringToFront();
  } else if (page.url() !== previousUrl) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      await page.goto(previousUrl, { waitUntil: "domcontentloaded" });
    });
    await waitForUiToSettle(page);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page, context }) => {
  await ensureArtifactsDir();

  const startUrl =
    process.env.SALEADS_START_URL ??
    process.env.SALEADS_URL ??
    process.env.BASE_URL ??
    process.env.PLAYWRIGHT_TEST_BASE_URL;

  if (startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
    await waitForUiToSettle(page);
  } else if (page.url() === "about:blank") {
    throw new Error(
      "No start URL provided. Set SALEADS_START_URL (or BASE_URL) to the SaleADS login page for the current environment."
    );
  }

  await runStep("Login", async () => {
    const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);
    await clickByVisibleText(page, [
      "Sign in with Google",
      "Iniciar sesi\u00f3n con Google",
      "Login with Google",
      "Acceder con Google"
    ]);

    const popup = await popupPromise;
    const authPage = popup ?? page;
    await authPage.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => {});
    await waitForUiToSettle(authPage);

    const accountLocator = authPage.getByText(
      new RegExp(`^\\s*${escapeRegex(GOOGLE_ACCOUNT_EMAIL)}\\s*$`, "i")
    );
    if (await accountLocator.first().isVisible().catch(() => false)) {
      await accountLocator.first().click();
      await waitForUiToSettle(authPage);
    }

    if (popup) {
      await Promise.race([
        popup.waitForEvent("close", { timeout: 120000 }),
        page.waitForLoadState("domcontentloaded", { timeout: 120000 })
      ]).catch(() => {});
      await page.bringToFront();
    }

    await assertAnyTextVisible(page, ["Negocio", "Dashboard", "Mi Negocio"], 90000);
    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 90000 });
    await takeCheckpoint(page, "step-01-dashboard-loaded.png", true);
  });

  await runStep("Mi Negocio menu", async () => {
    await clickByVisibleText(page, ["Negocio"]);
    await clickByVisibleText(page, ["Mi Negocio"]);

    await assertTextVisible(page, "Agregar Negocio");
    await assertTextVisible(page, "Administrar Negocios");
    await takeCheckpoint(page, "step-02-mi-negocio-menu-expanded.png");
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickByVisibleText(page, ["Agregar Negocio"]);

    await assertTextVisible(page, "Crear Nuevo Negocio");
    await assertAnyTextVisible(page, ["Nombre del Negocio"], 20000);
    await assertTextVisible(page, "Tienes 2 de 3 negocios");
    await assertTextVisible(page, "Cancelar");
    await assertTextVisible(page, "Crear Negocio");

    const nameInput =
      page.getByLabel(/Nombre del Negocio/i).first().or(page.getByPlaceholder(/Nombre del Negocio/i).first());
    if (await nameInput.isVisible().catch(() => false)) {
      await nameInput.click();
      await nameInput.fill("Negocio Prueba Automatizacion");
    }

    await takeCheckpoint(page, "step-03-agregar-negocio-modal.png");
    await clickByVisibleText(page, ["Cancelar"]);
  });

  await runStep("Administrar Negocios view", async () => {
    const adminOption = page.getByText(/^Administrar Negocios$/i).first();
    if (!(await adminOption.isVisible().catch(() => false))) {
      await clickByVisibleText(page, ["Mi Negocio"]);
    }

    await clickByVisibleText(page, ["Administrar Negocios"]);
    await assertAnyTextVisible(page, [
      "Informaci\u00f3n General",
      "Informacion General",
      "Informaci\u00f3n general",
      "Informacion general"
    ]);
    await assertAnyTextVisible(page, ["Detalles de la Cuenta", "Detalles de la cuenta"]);
    await assertAnyTextVisible(page, ["Tus Negocios", "Tus negocios"]);
    await assertAnyTextVisible(page, [
      "Secci\u00f3n Legal",
      "Seccion Legal",
      "Secci\u00f3n legal",
      "Seccion legal"
    ]);
    await takeCheckpoint(page, "step-04-administrar-negocios-view.png", true);
  });

  await runStep("Informaci\u00f3n General", async () => {
    const section = await getSectionByHeading(page, [
      "Informaci\u00f3n General",
      "Informacion General",
      "Informaci\u00f3n general",
      "Informacion general"
    ]);
    const sectionText = await section.innerText();

    if (!sectionText.includes("@")) {
      throw new Error("User email is not visible in Informaci\u00f3n General.");
    }

    const lines = sectionText
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean);

    const userNameLine = lines.find((line) => {
      return (
        /^[A-Za-z][A-Za-z' -]{2,}$/.test(line) &&
        !/@/.test(line) &&
        !/informacion|business plan|cambiar plan/i.test(line)
      );
    });
    if (!userNameLine) {
      throw new Error("Could not identify a user name value in Informaci\u00f3n General.");
    }

    await expect(section.getByText(/BUSINESS PLAN/i)).toBeVisible();
    await expect(section.getByText(/Cambiar Plan/i)).toBeVisible();
  });

  await runStep("Detalles de la Cuenta", async () => {
    const section = await getSectionByHeading(page, ["Detalles de la Cuenta", "Detalles de la cuenta"]);
    await expect(section.getByText(/Cuenta creada/i)).toBeVisible();
    await expect(section.getByText(/Estado activo/i)).toBeVisible();
    await expect(section.getByText(/Idioma seleccionado/i)).toBeVisible();
  });

  await runStep("Tus Negocios", async () => {
    const section = await getSectionByHeading(page, ["Tus Negocios", "Tus negocios"]);
    await expect(section.getByText(/Agregar Negocio/i)).toBeVisible();
    await expect(section.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();

    const sectionText = await section.innerText();
    const lines = sectionText
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean)
      .filter((line) => !/Tus Negocios|Agregar Negocio|Tienes 2 de 3 negocios/i.test(line));

    if (lines.length === 0) {
      throw new Error("Business list is not visible in Tus Negocios section.");
    }
  });

  await runStep("T\u00e9rminos y Condiciones", async () => {
    evidence.terminosYCondicionesUrl = await openLegalDocumentAndReturn(
      page,
      context,
      ["Terminos y Condiciones", "Términos y Condiciones"],
      ["Terminos y Condiciones", "T\u00e9rminos y Condiciones"],
      "step-08-terminos-y-condiciones.png"
    );
  });

  await runStep("Pol\u00edtica de Privacidad", async () => {
    evidence.politicaDePrivacidadUrl = await openLegalDocumentAndReturn(
      page,
      context,
      ["Politica de Privacidad", "Política de Privacidad"],
      ["Politica de Privacidad", "Pol\u00edtica de Privacidad"],
      "step-09-politica-de-privacidad.png"
    );
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    generatedAt: new Date().toISOString(),
    results: report,
    evidence
  };

  await fs.writeFile(REPORT_FILE, `${JSON.stringify(finalReport, null, 2)}\n`, "utf8");
  console.log("Final report:", JSON.stringify(finalReport, null, 2));

  const failedFields = Object.entries(report)
    .filter(([, value]) => value.status !== "PASS")
    .map(([field, value]) => `${field}: ${value.details}`);

  expect(
    failedFields,
    `One or more validations failed:\n${failedFields.join("\n")}\nReport: ${REPORT_FILE}`
  ).toHaveLength(0);
});
