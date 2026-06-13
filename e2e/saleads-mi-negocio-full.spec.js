const { test, expect } = require("@playwright/test");

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

async function waitForUiToSettle(page, expectNavigation = false) {
  if (expectNavigation) {
    await page.waitForLoadState("domcontentloaded", { timeout: 30000 }).catch(() => {});
  }

  await page.waitForLoadState("networkidle", { timeout: 5000 }).catch(() => {});
  await page.waitForTimeout(600);
}

async function firstVisibleLocator(candidates) {
  for (const candidate of candidates) {
    if (await candidate.first().isVisible().catch(() => false)) {
      return candidate.first();
    }
  }

  return null;
}

async function clickAndWait(locator, page, expectNavigation = false) {
  await expect(locator).toBeVisible();
  await locator.click();
  await waitForUiToSettle(page, expectNavigation);
}

async function markStep({ key, fn, page, testInfo, report, failures }) {
  try {
    await fn();
    report[key] = "PASS";
  } catch (error) {
    report[key] = "FAIL";
    failures.push(`${key}: ${error.message}`);

    await page
      .screenshot({
        path: testInfo.outputPath(`failure-${key.replace(/[^\w-]+/g, "-").toLowerCase()}.png`),
        fullPage: true,
      })
      .catch(() => {});
  }
}

function findLikelyNameFromInfoSection(text) {
  const ignoredPatterns = [
    /información general/i,
    /business plan/i,
    /cambiar plan/i,
    /@/,
    /^cuenta/i,
    /^estado/i,
    /^idioma/i,
  ];

  const lines = text
    .split("\n")
    .map((line) => line.trim())
    .filter((line) => line.length > 0);

  return lines.find((line) => {
    if (ignoredPatterns.some((pattern) => pattern.test(line))) {
      return false;
    }

    return /^[\p{L}][\p{L}\s.'-]{2,}$/u.test(line);
  });
}

async function validateLegalLink({
  page,
  context,
  linkText,
  headingText,
  screenshotName,
  testInfo,
}) {
  const link = await firstVisibleLocator([
    page.getByRole("link", { name: new RegExp(linkText, "i") }),
    page.getByText(new RegExp(linkText, "i")),
  ]);

  if (!link) {
    throw new Error(`No se encontró el enlace legal "${linkText}".`);
  }

  const newTabPromise = context.waitForEvent("page", { timeout: 10000 }).catch(() => null);
  await link.click();

  const newTab = await newTabPromise;
  const legalPage = newTab || page;

  await waitForUiToSettle(legalPage, true);

  const heading = legalPage
    .getByRole("heading", { name: new RegExp(headingText, "i") })
    .first();
  await expect(heading).toBeVisible();

  const legalBodyText = (await legalPage.locator("body").innerText()).replace(/\s+/g, " ").trim();
  if (legalBodyText.length < 100) {
    throw new Error(`El contenido legal de "${linkText}" parece incompleto.`);
  }

  const finalUrl = legalPage.url();
  await legalPage.screenshot({
    path: testInfo.outputPath(screenshotName),
    fullPage: true,
  });

  if (newTab) {
    await newTab.close();
    await page.bringToFront();
    await waitForUiToSettle(page, false);
  } else {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUiToSettle(page, true);
  }

  return finalUrl;
}

test("SaleADS Mi Negocio full workflow", async ({ page, context }, testInfo) => {
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "NOT_RUN"]));
  const failures = [];
  const legalUrls = {};

  const startUrl = process.env.SALEADS_START_URL;
  if (startUrl) {
    await page.goto(startUrl, { waitUntil: "domcontentloaded" });
    await waitForUiToSettle(page, true);
  } else if (page.url() === "about:blank") {
    throw new Error(
      "Define SALEADS_START_URL para ejecutar la prueba en cualquier entorno SaleADS sin hardcodear dominio."
    );
  }

  await markStep({
    key: "Login",
    page,
    testInfo,
    report,
    failures,
    fn: async () => {
      const sidebar = page.locator("aside, nav").filter({ hasText: /Negocio|Mi Negocio/i }).first();
      const alreadyLoggedIn = await sidebar.isVisible().catch(() => false);

      if (!alreadyLoggedIn) {
        const loginButton = await firstVisibleLocator([
          page.getByRole("button", { name: /google|iniciar sesión|sign in/i }),
          page.getByRole("link", { name: /google|iniciar sesión|sign in/i }),
          page.getByText(/sign in with google|continuar con google|iniciar con google/i),
        ]);

        if (!loginButton) {
          throw new Error("No se encontró el botón de login con Google.");
        }

        const popupPromise = page.waitForEvent("popup", { timeout: 10000 }).catch(() => null);
        await clickAndWait(loginButton, page, false);
        const popup = await popupPromise;

        if (popup) {
          await waitForUiToSettle(popup, true);

          const accountOption = await firstVisibleLocator([
            popup.getByRole("button", { name: /juanlucasbarbiergarzon@gmail\.com/i }),
            popup.getByRole("link", { name: /juanlucasbarbiergarzon@gmail\.com/i }),
            popup.getByText(/juanlucasbarbiergarzon@gmail\.com/i),
          ]);

          if (accountOption) {
            await accountOption.click();
            await waitForUiToSettle(popup, true);
          }

          await popup.waitForEvent("close", { timeout: 30000 }).catch(() => {});
          await waitForUiToSettle(page, true);
        } else {
          await waitForUiToSettle(page, true);
        }
      }

      await expect(page.locator("aside, nav").first()).toBeVisible();
      await page.screenshot({
        path: testInfo.outputPath("01-dashboard-loaded.png"),
        fullPage: true,
      });
    },
  });

  await markStep({
    key: "Mi Negocio menu",
    page,
    testInfo,
    report,
    failures,
    fn: async () => {
      const miNegocio = await firstVisibleLocator([
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/^Mi Negocio$/i),
      ]);

      if (!miNegocio) {
        throw new Error('No se encontró la opción "Mi Negocio".');
      }

      await clickAndWait(miNegocio, page, false);

      await expect(page.getByText(/^Agregar Negocio$/i).first()).toBeVisible();
      await expect(page.getByText(/^Administrar Negocios$/i).first()).toBeVisible();

      await page.screenshot({
        path: testInfo.outputPath("02-mi-negocio-expanded.png"),
        fullPage: true,
      });
    },
  });

  await markStep({
    key: "Agregar Negocio modal",
    page,
    testInfo,
    report,
    failures,
    fn: async () => {
      const agregarNegocioOption = await firstVisibleLocator([
        page.getByRole("button", { name: /^Agregar Negocio$/i }),
        page.getByRole("link", { name: /^Agregar Negocio$/i }),
        page.getByText(/^Agregar Negocio$/i),
      ]);

      if (!agregarNegocioOption) {
        throw new Error('No se encontró la opción "Agregar Negocio".');
      }

      await clickAndWait(agregarNegocioOption, page, false);

      await expect(page.getByRole("heading", { name: /Crear Nuevo Negocio/i })).toBeVisible();

      const negocioInput = await firstVisibleLocator([
        page.getByLabel(/Nombre del Negocio/i),
        page.getByPlaceholder(/Nombre del Negocio/i),
      ]);

      if (!negocioInput) {
        throw new Error('No se encontró el campo "Nombre del Negocio".');
      }

      await expect(page.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(page.getByRole("button", { name: /^Cancelar$/i })).toBeVisible();
      await expect(page.getByRole("button", { name: /^Crear Negocio$/i })).toBeVisible();

      await page.screenshot({
        path: testInfo.outputPath("03-agregar-negocio-modal.png"),
        fullPage: true,
      });

      await negocioInput.click();
      await negocioInput.fill("Negocio Prueba Automatización");
      await clickAndWait(page.getByRole("button", { name: /^Cancelar$/i }), page, false);
    },
  });

  await markStep({
    key: "Administrar Negocios view",
    page,
    testInfo,
    report,
    failures,
    fn: async () => {
      const administrarVisible = await page.getByText(/^Administrar Negocios$/i).first().isVisible().catch(() => false);
      if (!administrarVisible) {
        const miNegocio = await firstVisibleLocator([
          page.getByRole("button", { name: /mi negocio/i }),
          page.getByRole("link", { name: /mi negocio/i }),
          page.getByText(/^Mi Negocio$/i),
        ]);

        if (!miNegocio) {
          throw new Error('No fue posible expandir "Mi Negocio".');
        }

        await clickAndWait(miNegocio, page, false);
      }

      const administrar = await firstVisibleLocator([
        page.getByRole("link", { name: /^Administrar Negocios$/i }),
        page.getByRole("button", { name: /^Administrar Negocios$/i }),
        page.getByText(/^Administrar Negocios$/i),
      ]);

      if (!administrar) {
        throw new Error('No se encontró "Administrar Negocios".');
      }

      await clickAndWait(administrar, page, true);

      await expect(page.getByText(/Información General/i)).toBeVisible();
      await expect(page.getByText(/Detalles de la Cuenta/i)).toBeVisible();
      await expect(page.getByText(/Tus Negocios/i)).toBeVisible();
      await expect(page.getByText(/Sección Legal/i)).toBeVisible();

      await page.screenshot({
        path: testInfo.outputPath("04-administrar-negocios-view.png"),
        fullPage: true,
      });
    },
  });

  await markStep({
    key: "Información General",
    page,
    testInfo,
    report,
    failures,
    fn: async () => {
      const infoSection = page.locator("section, div").filter({ hasText: /Información General/i }).first();
      await expect(infoSection).toBeVisible();

      const infoText = await infoSection.innerText();

      if (!/@/.test(infoText)) {
        throw new Error("No se detectó correo del usuario en Información General.");
      }

      const likelyName = findLikelyNameFromInfoSection(infoText);
      if (!likelyName) {
        throw new Error("No se detectó un nombre de usuario visible en Información General.");
      }

      await expect(infoSection.getByText(/BUSINESS PLAN/i)).toBeVisible();
      await expect(infoSection.getByRole("button", { name: /Cambiar Plan/i })).toBeVisible();
    },
  });

  await markStep({
    key: "Detalles de la Cuenta",
    page,
    testInfo,
    report,
    failures,
    fn: async () => {
      const detailsSection = page.locator("section, div").filter({ hasText: /Detalles de la Cuenta/i }).first();
      await expect(detailsSection).toBeVisible();
      await expect(detailsSection.getByText(/Cuenta creada/i)).toBeVisible();
      await expect(detailsSection.getByText(/Estado activo/i)).toBeVisible();
      await expect(detailsSection.getByText(/Idioma seleccionado/i)).toBeVisible();
    },
  });

  await markStep({
    key: "Tus Negocios",
    page,
    testInfo,
    report,
    failures,
    fn: async () => {
      const businessesSection = page.locator("section, div").filter({ hasText: /Tus Negocios/i }).first();
      await expect(businessesSection).toBeVisible();
      await expect(businessesSection.getByText(/Tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(businessesSection.getByRole("button", { name: /^Agregar Negocio$/i })).toBeVisible();

      const sectionText = await businessesSection.innerText();
      if (sectionText.trim().length < 40) {
        throw new Error("La lista de negocios no parece estar visible o completa.");
      }
    },
  });

  await markStep({
    key: "Términos y Condiciones",
    page,
    testInfo,
    report,
    failures,
    fn: async () => {
      legalUrls.terminos = await validateLegalLink({
        page,
        context,
        linkText: "Términos y Condiciones",
        headingText: "Términos y Condiciones",
        screenshotName: "05-terminos-y-condiciones.png",
        testInfo,
      });
    },
  });

  await markStep({
    key: "Política de Privacidad",
    page,
    testInfo,
    report,
    failures,
    fn: async () => {
      legalUrls.politica = await validateLegalLink({
        page,
        context,
        linkText: "Política de Privacidad",
        headingText: "Política de Privacidad",
        screenshotName: "06-politica-de-privacidad.png",
        testInfo,
      });
    },
  });

  const finalReport = {
    report,
    legalUrls,
    failures,
  };

  await testInfo.attach("saleads-mi-negocio-final-report", {
    body: Buffer.from(`${JSON.stringify(finalReport, null, 2)}\n`, "utf-8"),
    contentType: "application/json",
  });

  console.log("Final report (SaleADS Mi Negocio workflow):");
  console.table(report);
  if (legalUrls.terminos) {
    console.log(`Términos y Condiciones URL: ${legalUrls.terminos}`);
  }
  if (legalUrls.politica) {
    console.log(`Política de Privacidad URL: ${legalUrls.politica}`);
  }

  if (failures.length > 0) {
    throw new Error(`Validaciones fallidas:\n- ${failures.join("\n- ")}`);
  }
});
