import { BrowserContext, Locator, Page, TestInfo, test } from "@playwright/test";
import { promises as fs } from "node:fs";

type ReportField =
  | "Login"
  | "Mi Negocio menu"
  | "Agregar Negocio modal"
  | "Administrar Negocios view"
  | "Información General"
  | "Detalles de la Cuenta"
  | "Tus Negocios"
  | "Términos y Condiciones"
  | "Política de Privacidad";

type StepStatus = "PASS" | "FAIL";

const reportFields: ReportField[] = [
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

function makeReport(): Record<ReportField, StepStatus> {
  return reportFields.reduce(
    (acc, field) => {
      acc[field] = "FAIL";
      return acc;
    },
    {} as Record<ReportField, StepStatus>
  );
}

async function waitForUi(page: Page): Promise<void> {
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(async () => {
    await page.waitForLoadState("domcontentloaded", { timeout: 5_000 }).catch(() => undefined);
    await page.waitForTimeout(1_000);
  });
}

async function firstVisible(candidates: Locator[]): Promise<Locator | null> {
  for (const candidate of candidates) {
    const first = candidate.first();
    if (await first.isVisible().catch(() => false)) {
      return first;
    }
  }
  return null;
}

async function clickAndWait(locator: Locator, page: Page): Promise<void> {
  await locator.click();
  await waitForUi(page);
}

async function saveScreenshot(page: Page, testInfo: TestInfo, name: string, fullPage = false): Promise<void> {
  await page.screenshot({
    path: testInfo.outputPath(name),
    fullPage
  });
}

async function openAndValidateLegalLink(
  page: Page,
  context: BrowserContext,
  linkText: "Términos y Condiciones" | "Política de Privacidad",
  headingRegex: RegExp,
  screenshotName: string,
  testInfo: TestInfo,
  appReturnUrl: string
): Promise<{ passed: boolean; finalUrl: string; details: string[] }> {
  const details: string[] = [];
  const link = await firstVisible([
    page.getByRole("link", { name: new RegExp(linkText, "i") }),
    page.getByRole("button", { name: new RegExp(linkText, "i") }),
    page.getByText(new RegExp(`^${linkText}$`, "i"))
  ]);

  if (!link) {
    details.push(`No se encontró el enlace/botón "${linkText}".`);
    return { passed: false, finalUrl: page.url(), details };
  }

  const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await link.click();
  const popup = await popupPromise;
  const legalPage = popup ?? page;
  await waitForUi(legalPage);

  const heading = await firstVisible([
    legalPage.getByRole("heading", { name: headingRegex }),
    legalPage.getByText(headingRegex)
  ]);
  const headingVisible = Boolean(heading && (await heading.isVisible().catch(() => false)));

  const bodyText = await legalPage.locator("body").innerText().catch(() => "");
  const hasLegalContent = bodyText.replace(/\s+/g, " ").trim().length > 140;

  await saveScreenshot(legalPage, testInfo, screenshotName);
  const finalUrl = legalPage.url();

  if (popup) {
    await popup.close().catch(() => undefined);
    await page.bringToFront().catch(() => undefined);
  } else if (page.url() !== appReturnUrl) {
    await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
      await page.goto(appReturnUrl, { waitUntil: "domcontentloaded" }).catch(() => undefined);
    });
    await waitForUi(page);
  }

  if (!headingVisible) {
    details.push(`No se encontró encabezado válido para "${linkText}".`);
  }
  if (!hasLegalContent) {
    details.push(`No se detectó contenido legal suficiente para "${linkText}".`);
  }

  return {
    passed: headingVisible && hasLegalContent,
    finalUrl,
    details
  };
}

test("saleads_mi_negocio_full_test", async ({ page, context, baseURL }, testInfo) => {
  const report = makeReport();
  const notes: string[] = [];
  const evidence: Record<string, string> = {};
  const googleEmail = "juanlucasbarbiergarzon@gmail.com";
  let appReturnUrl = "";

  if (page.url() === "about:blank") {
    if (!baseURL) {
      throw new Error(
        "El navegador está en about:blank. Define SALEADS_BASE_URL o BASE_URL para abrir el login del entorno actual."
      );
    }
    await page.goto(baseURL, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  // Step 1 - Login with Google
  try {
    const sidebarBeforeLogin = await firstVisible([
      page.locator("aside"),
      page.getByRole("navigation").filter({ hasText: /negocio|mi negocio/i }),
      page.locator("nav").filter({ hasText: /negocio|mi negocio/i })
    ]);

    if (!sidebarBeforeLogin) {
      const loginButton = await firstVisible([
        page.getByRole("button", { name: /google|iniciar sesión|sign in/i }),
        page.getByRole("link", { name: /google|iniciar sesión|sign in/i }),
        page.getByText(/google/i)
      ]);

      if (!loginButton) {
        throw new Error("No se encontró botón de login con Google.");
      }

      const popupPromise = context.waitForEvent("page", { timeout: 8_000 }).catch(() => null);
      await clickAndWait(loginButton, page);
      const googlePopup = await popupPromise;
      const authPage = googlePopup ?? page;
      await waitForUi(authPage);

      const accountOption = await firstVisible([
        authPage.getByRole("button", { name: new RegExp(googleEmail, "i") }),
        authPage.getByRole("link", { name: new RegExp(googleEmail, "i") }),
        authPage.getByText(new RegExp(googleEmail, "i"))
      ]);

      if (accountOption) {
        await accountOption.click();
        await waitForUi(authPage);
      }

      if (googlePopup) {
        await googlePopup.waitForLoadState("domcontentloaded").catch(() => undefined);
        await page.bringToFront().catch(() => undefined);
      }
    }

    const mainSidebar = await firstVisible([
      page.locator("aside"),
      page.getByRole("navigation"),
      page.locator("nav").filter({ hasText: /negocio|mi negocio/i })
    ]);
    const hasSidebar = Boolean(mainSidebar && (await mainSidebar.isVisible().catch(() => false)));
    const hasNegocioReference = await page.getByText(/negocio|mi negocio/i).first().isVisible().catch(() => false);

    if (!hasSidebar || !hasNegocioReference) {
      throw new Error("No se confirmó interfaz principal con navegación lateral.");
    }

    await saveScreenshot(page, testInfo, "01_dashboard_loaded.png");
    report["Login"] = "PASS";
  } catch (error) {
    notes.push(`Login: ${(error as Error).message}`);
  }

  // Step 2 - Open Mi Negocio menu
  try {
    const miNegocio = await firstVisible([
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i)
    ]);

    if (!miNegocio) {
      throw new Error("No se encontró la opción Mi Negocio.");
    }

    await clickAndWait(miNegocio, page);

    const agregarNegocioMenu = await firstVisible([
      page.getByRole("link", { name: /agregar negocio/i }),
      page.getByRole("button", { name: /agregar negocio/i }),
      page.getByText(/agregar negocio/i)
    ]);
    const administrarNegociosMenu = await firstVisible([
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i)
    ]);

    const menuExpanded = Boolean(
      agregarNegocioMenu &&
        administrarNegociosMenu &&
        (await agregarNegocioMenu.isVisible().catch(() => false)) &&
        (await administrarNegociosMenu.isVisible().catch(() => false))
    );

    if (!menuExpanded) {
      throw new Error("El submenú de Mi Negocio no mostró Agregar/Administrar Negocios.");
    }

    await saveScreenshot(page, testInfo, "02_mi_negocio_menu_expanded.png");
    report["Mi Negocio menu"] = "PASS";
  } catch (error) {
    notes.push(`Mi Negocio menu: ${(error as Error).message}`);
  }

  // Step 3 - Validate Agregar Negocio modal
  try {
    const agregarNegocio = await firstVisible([
      page.getByRole("link", { name: /agregar negocio/i }),
      page.getByRole("button", { name: /agregar negocio/i }),
      page.getByText(/agregar negocio/i)
    ]);

    if (!agregarNegocio) {
      throw new Error("No se encontró Agregar Negocio en el menú.");
    }

    await clickAndWait(agregarNegocio, page);

    const modalTitle = await firstVisible([
      page.getByRole("heading", { name: /crear nuevo negocio/i }),
      page.getByText(/crear nuevo negocio/i)
    ]);

    const nombreNegocioField = await firstVisible([
      page.getByLabel(/nombre del negocio/i),
      page.getByPlaceholder(/nombre del negocio/i)
    ]);
    const quotaText = await firstVisible([page.getByText(/tienes 2 de 3 negocios/i)]);
    const cancelarButton = await firstVisible([
      page.getByRole("button", { name: /cancelar/i }),
      page.getByText(/^cancelar$/i)
    ]);
    const crearNegocioButton = await firstVisible([
      page.getByRole("button", { name: /crear negocio/i }),
      page.getByText(/crear negocio/i)
    ]);

    const modalIsValid = Boolean(
      modalTitle &&
        nombreNegocioField &&
        quotaText &&
        cancelarButton &&
        crearNegocioButton &&
        (await modalTitle.isVisible().catch(() => false)) &&
        (await nombreNegocioField.isVisible().catch(() => false)) &&
        (await quotaText.isVisible().catch(() => false)) &&
        (await cancelarButton.isVisible().catch(() => false)) &&
        (await crearNegocioButton.isVisible().catch(() => false))
    );

    if (!modalIsValid) {
      throw new Error("El modal de Crear Nuevo Negocio no presentó todos los elementos esperados.");
    }

    await nombreNegocioField?.fill("Negocio Prueba Automatización").catch(() => undefined);
    await saveScreenshot(page, testInfo, "03_agregar_negocio_modal.png");
    await cancelarButton?.click().catch(() => undefined);
    await waitForUi(page);
    report["Agregar Negocio modal"] = "PASS";
  } catch (error) {
    notes.push(`Agregar Negocio modal: ${(error as Error).message}`);
  }

  // Step 4 - Open Administrar Negocios
  try {
    const administrarNegocios = await firstVisible([
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i)
    ]);

    if (!administrarNegocios) {
      const miNegocio = await firstVisible([
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i)
      ]);
      if (miNegocio) {
        await clickAndWait(miNegocio, page);
      }
    }

    const administrarNegociosResolved = await firstVisible([
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i)
    ]);
    if (!administrarNegociosResolved) {
      throw new Error("No se encontró Administrar Negocios.");
    }

    await clickAndWait(administrarNegociosResolved, page);
    appReturnUrl = page.url();

    const infoGeneral = await firstVisible([page.getByText(/información general/i)]);
    const detallesCuenta = await firstVisible([page.getByText(/detalles de la cuenta/i)]);
    const tusNegocios = await firstVisible([page.getByText(/tus negocios/i)]);
    const seccionLegal = await firstVisible([page.getByText(/sección legal/i)]);

    const accountSectionsVisible = Boolean(
      infoGeneral &&
        detallesCuenta &&
        tusNegocios &&
        seccionLegal &&
        (await infoGeneral.isVisible().catch(() => false)) &&
        (await detallesCuenta.isVisible().catch(() => false)) &&
        (await tusNegocios.isVisible().catch(() => false)) &&
        (await seccionLegal.isVisible().catch(() => false))
    );

    if (!accountSectionsVisible) {
      throw new Error("No se validaron todas las secciones de Administrar Negocios.");
    }

    await saveScreenshot(page, testInfo, "04_administrar_negocios_full_page.png", true);
    report["Administrar Negocios view"] = "PASS";
  } catch (error) {
    notes.push(`Administrar Negocios view: ${(error as Error).message}`);
  }

  // Step 5 - Validate Información General
  try {
    const emailVisible =
      (await page.getByText(new RegExp(googleEmail, "i")).first().isVisible().catch(() => false)) ||
      (await page
        .locator("text=/[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}/i")
        .first()
        .isVisible()
        .catch(() => false));

    const userNameVisible = await firstVisible([
      page.getByText(/nombre/i),
      page.locator("h1, h2, h3, strong").filter({ hasText: /juan|lucas|barbier|garzon/i })
    ]);
    const businessPlan = await firstVisible([page.getByText(/business plan/i)]);
    const cambiarPlan = await firstVisible([
      page.getByRole("button", { name: /cambiar plan/i }),
      page.getByRole("link", { name: /cambiar plan/i }),
      page.getByText(/cambiar plan/i)
    ]);

    const infoGeneralValid = Boolean(
      emailVisible &&
        userNameVisible &&
        businessPlan &&
        cambiarPlan &&
        (await businessPlan.isVisible().catch(() => false)) &&
        (await cambiarPlan.isVisible().catch(() => false))
    );

    if (!infoGeneralValid) {
      throw new Error("Información General incompleta (usuario/email/plan/cambiar plan).");
    }

    report["Información General"] = "PASS";
  } catch (error) {
    notes.push(`Información General: ${(error as Error).message}`);
  }

  // Step 6 - Validate Detalles de la Cuenta
  try {
    const cuentaCreada = await firstVisible([page.getByText(/cuenta creada/i)]);
    const estadoActivo = await firstVisible([page.getByText(/estado activo/i)]);
    const idiomaSeleccionado = await firstVisible([page.getByText(/idioma seleccionado/i)]);

    const detallesValid = Boolean(
      cuentaCreada &&
        estadoActivo &&
        idiomaSeleccionado &&
        (await cuentaCreada.isVisible().catch(() => false)) &&
        (await estadoActivo.isVisible().catch(() => false)) &&
        (await idiomaSeleccionado.isVisible().catch(() => false))
    );

    if (!detallesValid) {
      throw new Error("Detalles de la Cuenta incompletos.");
    }

    report["Detalles de la Cuenta"] = "PASS";
  } catch (error) {
    notes.push(`Detalles de la Cuenta: ${(error as Error).message}`);
  }

  // Step 7 - Validate Tus Negocios
  try {
    const negociosTitle = await firstVisible([page.getByText(/tus negocios/i)]);
    const agregarNegocioButton = await firstVisible([
      page.getByRole("button", { name: /agregar negocio/i }),
      page.getByRole("link", { name: /agregar negocio/i }),
      page.getByText(/agregar negocio/i)
    ]);
    const quotaText = await firstVisible([page.getByText(/tienes 2 de 3 negocios/i)]);

    const tusNegociosValid = Boolean(
      negociosTitle &&
        agregarNegocioButton &&
        quotaText &&
        (await negociosTitle.isVisible().catch(() => false)) &&
        (await agregarNegocioButton.isVisible().catch(() => false)) &&
        (await quotaText.isVisible().catch(() => false))
    );

    if (!tusNegociosValid) {
      throw new Error("Sección Tus Negocios incompleta.");
    }

    report["Tus Negocios"] = "PASS";
  } catch (error) {
    notes.push(`Tus Negocios: ${(error as Error).message}`);
  }

  // Step 8 - Validate Términos y Condiciones
  try {
    const legalResult = await openAndValidateLegalLink(
      page,
      context,
      "Términos y Condiciones",
      /términos y condiciones/i,
      "05_terminos_y_condiciones.png",
      testInfo,
      appReturnUrl || page.url()
    );
    evidence["terminos_url"] = legalResult.finalUrl;
    if (!legalResult.passed) {
      throw new Error(legalResult.details.join(" "));
    }
    report["Términos y Condiciones"] = "PASS";
  } catch (error) {
    notes.push(`Términos y Condiciones: ${(error as Error).message}`);
  }

  // Step 9 - Validate Política de Privacidad
  try {
    const legalResult = await openAndValidateLegalLink(
      page,
      context,
      "Política de Privacidad",
      /política de privacidad/i,
      "06_politica_de_privacidad.png",
      testInfo,
      appReturnUrl || page.url()
    );
    evidence["politica_privacidad_url"] = legalResult.finalUrl;
    if (!legalResult.passed) {
      throw new Error(legalResult.details.join(" "));
    }
    report["Política de Privacidad"] = "PASS";
  } catch (error) {
    notes.push(`Política de Privacidad: ${(error as Error).message}`);
  }

  const finalReport = {
    test_name: "saleads_mi_negocio_full_test",
    timestamp_utc: new Date().toISOString(),
    report,
    evidence,
    notes
  };

  const reportPath = testInfo.outputPath("saleads_mi_negocio_report.json");
  await fs.writeFile(reportPath, JSON.stringify(finalReport, null, 2), "utf-8");
  await testInfo.attach("saleads-mi-negocio-report", {
    contentType: "application/json",
    path: reportPath
  });

  const hasFailures = Object.values(report).some((status) => status === "FAIL");
  if (hasFailures) {
    throw new Error(`Validaciones fallidas: ${JSON.stringify(report)}. Detalles: ${notes.join(" | ")}`);
  }
});
