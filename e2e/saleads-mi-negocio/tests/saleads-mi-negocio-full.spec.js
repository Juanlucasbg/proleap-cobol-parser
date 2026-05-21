import fs from "node:fs/promises";
import path from "node:path";
import { expect, test } from "@playwright/test";

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

function createInitialReport() {
  return Object.fromEntries(
    REPORT_FIELDS.map((field) => [field, { status: "FAIL", details: "Not executed" }]),
  );
}

function stripAnsi(input) {
  return input.replace(/\u001B\[[0-9;]*m/g, "");
}

function toFileSlug(value) {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
}

async function waitAfterClick(page) {
  try {
    await page.waitForLoadState("networkidle", { timeout: 10000 });
  } catch {
    await page.waitForTimeout(1200);
  }
}

async function findFirstVisible(candidates, timeout = 5000) {
  for (const locator of candidates) {
    if (await locator.first().isVisible({ timeout }).catch(() => false)) {
      return locator.first();
    }
  }

  throw new Error("No candidate locator became visible.");
}

async function validateLegalPage({
  appPage,
  linkText,
  headingRegex,
  screenshotName,
  testInfo,
}) {
  const popupPromise = appPage.context().waitForEvent("page", { timeout: 8000 }).catch(() => null);

  const legalLink = await findFirstVisible(
    [
      appPage.getByRole("link", { name: new RegExp(`^${linkText}$`, "i") }),
      appPage.getByText(new RegExp(`^${linkText}$`, "i")),
    ],
    8000,
  );

  await legalLink.click();
  await waitAfterClick(appPage);

  const popup = await popupPromise;
  const targetPage = popup ?? appPage;
  await targetPage.waitForLoadState("domcontentloaded", { timeout: 30000 });

  const heading = await findFirstVisible(
    [
      targetPage.getByRole("heading", { name: headingRegex }),
      targetPage.getByText(headingRegex),
    ],
    15000,
  );
  await expect(heading).toBeVisible();

  const bodyText = await targetPage.locator("body").innerText();
  if (bodyText.trim().length < 180) {
    throw new Error(`Expected legal content for "${linkText}" but page text was too short.`);
  }

  await targetPage.screenshot({
    path: testInfo.outputPath(screenshotName),
    fullPage: true,
  });

  const finalUrl = targetPage.url();

  if (popup) {
    await popup.close({ runBeforeUnload: true }).catch(() => {});
    await appPage.bringToFront();
  } else if (appPage.url() !== finalUrl) {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await appPage.waitForTimeout(1000);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = createInitialReport();

  const runStep = async (field, callback) => {
    const slug = toFileSlug(field);

    try {
      const details = await callback();
      report[field] = { status: "PASS", ...(details ?? {}) };
    } catch (error) {
      await page.screenshot({
        path: testInfo.outputPath(`fail-${slug}.png`),
        fullPage: true,
      });

      report[field] = {
        status: "FAIL",
        details: stripAnsi(error instanceof Error ? error.message : String(error)),
      };
    }
  };

  const startUrl =
    process.env.SALEADS_LOGIN_URL ?? process.env.SALEADS_START_URL ?? process.env.BASE_URL ?? "";

  await runStep("Login", async () => {
    if (startUrl) {
      await page.goto(startUrl, { waitUntil: "domcontentloaded" });
      await page.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => {});
    }

    if (!startUrl && page.url().startsWith("about:blank")) {
      throw new Error(
        "No start URL provided. Set SALEADS_LOGIN_URL or SALEADS_START_URL to the login page.",
      );
    }

    const clickEntrySignInIfPresent = async () => {
      const entrySignIn = await findFirstVisible(
        [
          page.getByRole("button", { name: /^Sign in$/i }),
          page.getByRole("link", { name: /^Sign in$/i }),
          page.getByRole("button", { name: /^Iniciar sesión$/i }),
          page.getByRole("link", { name: /^Iniciar sesión$/i }),
        ],
        5000,
      ).catch(() => null);

      if (entrySignIn) {
        await entrySignIn.click();
        await waitAfterClick(page);
      }
    };

    let signInButton = await findFirstVisible(
      [
        page.getByRole("button", { name: /google/i }),
        page.getByRole("link", { name: /google/i }),
      ],
      8000,
    ).catch(() => null);

    if (!signInButton) {
      await clickEntrySignInIfPresent();
      signInButton = await findFirstVisible(
        [
          page.getByRole("button", { name: /google/i }),
          page.getByRole("link", { name: /google/i }),
        ],
        15000,
      );
    }

    const popupPromise = page.context().waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await signInButton.click();
    await waitAfterClick(page);

    const popup = await popupPromise;
    if (popup) {
      await popup.waitForLoadState("domcontentloaded", { timeout: 20000 });
      const emailOption = popup.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true });
      if (await emailOption.isVisible({ timeout: 10000 }).catch(() => false)) {
        await emailOption.click();
        await popup.waitForLoadState("networkidle", { timeout: 20000 }).catch(() => {});
      }
    } else {
      const accountOption = page.getByText("juanlucasbarbiergarzon@gmail.com", { exact: true });
      if (await accountOption.isVisible({ timeout: 5000 }).catch(() => false)) {
        await accountOption.click();
        await waitAfterClick(page);
      }
    }

    await expect(page.locator("aside, nav").first()).toBeVisible({ timeout: 45000 });
    await expect(page.getByText(/Negocio/i).first()).toBeVisible({ timeout: 45000 });

    await page.screenshot({
      path: testInfo.outputPath("01-dashboard-loaded.png"),
      fullPage: true,
    });

    return { details: "Dashboard and sidebar were visible after login." };
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioSection = page.getByText(/^Negocio$/i).first();
    if (await negocioSection.isVisible({ timeout: 5000 }).catch(() => false)) {
      await negocioSection.click();
      await waitAfterClick(page);
    }

    const miNegocio = await findFirstVisible([page.getByText(/^Mi Negocio$/i)], 10000);
    await miNegocio.click();
    await waitAfterClick(page);

    await expect(page.getByText(/^Agregar Negocio$/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/^Administrar Negocios$/i)).toBeVisible({ timeout: 15000 });

    await page.screenshot({
      path: testInfo.outputPath("02-mi-negocio-expanded.png"),
      fullPage: true,
    });
  });

  await runStep("Agregar Negocio modal", async () => {
    await findFirstVisible([page.getByText(/^Agregar Negocio$/i)], 10000).then((el) => el.click());
    await waitAfterClick(page);

    await expect(page.getByText(/^Crear Nuevo Negocio$/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/^Tienes 2 de 3 negocios$/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible({ timeout: 15000 });
    await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible({
      timeout: 15000,
    });

    const negocioInput = await findFirstVisible(
      [
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
        page.locator("input[name*='nombre' i]"),
      ],
      10000,
    );
    await expect(negocioInput).toBeVisible();
    await negocioInput.click();
    await negocioInput.fill("Negocio Prueba Automatizacion");

    await page.screenshot({
      path: testInfo.outputPath("03-agregar-negocio-modal.png"),
      fullPage: true,
    });

    await page.getByRole("button", { name: /^Cancelar$/i }).click();
    await waitAfterClick(page);
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarVisible = await page
      .getByText(/^Administrar Negocios$/i)
      .first()
      .isVisible({ timeout: 5000 })
      .catch(() => false);

    if (!administrarVisible) {
      const miNegocio = await findFirstVisible([page.getByText(/^Mi Negocio$/i)], 8000);
      await miNegocio.click();
      await waitAfterClick(page);
    }

    await findFirstVisible([page.getByText(/^Administrar Negocios$/i)], 10000).then((el) =>
      el.click(),
    );
    await waitAfterClick(page);

    await expect(page.getByText(/^Información General$/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/^Detalles de la Cuenta$/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/^Tus Negocios$/i)).toBeVisible({ timeout: 20000 });
    await expect(page.getByText(/^Sección Legal$/i)).toBeVisible({ timeout: 20000 });

    await page.screenshot({
      path: testInfo.outputPath("04-administrar-negocios.png"),
      fullPage: true,
    });
  });

  await runStep("Información General", async () => {
    const section = page.locator("section, div").filter({ hasText: /Información General/i }).first();
    await expect(section).toBeVisible({ timeout: 15000 });

    const sectionText = (await section.innerText()).split("\n").map((line) => line.trim());
    const emailLine = sectionText.find((line) => /@/.test(line));
    if (!emailLine) {
      throw new Error("User email was not visible in Información General.");
    }

    const nameLine = sectionText.find(
      (line) =>
        line.length > 2 &&
        !/@/.test(line) &&
        !/información general|business plan|cambiar plan/i.test(line),
    );
    if (!nameLine) {
      throw new Error("User name was not clearly visible in Información General.");
    }

    await expect(page.getByText(/BUSINESS PLAN/i)).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole("button", { name: /^Cambiar Plan$/i })).toBeVisible({
      timeout: 10000,
    });
  });

  await runStep("Detalles de la Cuenta", async () => {
    await expect(page.getByText(/^Cuenta creada$/i)).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/^Estado activo$/i)).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/^Idioma seleccionado$/i)).toBeVisible({ timeout: 10000 });
  });

  await runStep("Tus Negocios", async () => {
    await expect(page.getByText(/^Tus Negocios$/i)).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible({
      timeout: 10000,
    });
    await expect(page.getByText(/^Tienes 2 de 3 negocios$/i)).toBeVisible({ timeout: 10000 });
  });

  await runStep("Términos y Condiciones", async () => {
    const url = await validateLegalPage({
      appPage: page,
      linkText: "Términos y Condiciones",
      headingRegex: /Términos y Condiciones/i,
      screenshotName: "05-terminos-y-condiciones.png",
      testInfo,
    });
    return { details: `Validated legal page URL: ${url}` };
  });

  await runStep("Política de Privacidad", async () => {
    const url = await validateLegalPage({
      appPage: page,
      linkText: "Política de Privacidad",
      headingRegex: /Política de Privacidad/i,
      screenshotName: "06-politica-de-privacidad.png",
      testInfo,
    });
    return { details: `Validated legal page URL: ${url}` };
  });

  const reportPath = testInfo.outputPath("saleads-mi-negocio-report.json");
  await fs.writeFile(reportPath, JSON.stringify(report, null, 2), "utf-8");

  await testInfo.attach("saleads-mi-negocio-report", {
    body: JSON.stringify(report, null, 2),
    contentType: "application/json",
  });

  const failedFields = Object.entries(report)
    .filter(([, result]) => result.status === "FAIL")
    .map(([field]) => field);

  expect(
    failedFields,
    `Workflow validations failed for: ${failedFields.join(", ")}. Report saved at ${path.basename(
      reportPath,
    )}.`,
  ).toEqual([]);
});
