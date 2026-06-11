import { expect, Locator, Page, test } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";

type Status = "PASS" | "FAIL";

type StepResult = {
  name: string;
  status: Status;
  details: string[];
  evidence: string[];
  finalUrl?: string;
};

const GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com";
const WAIT_AFTER_CLICK_MS = 1200;
const runId = new Date().toISOString().replace(/[:.]/g, "-");
const artifactsDir = path.resolve(process.cwd(), "artifacts", runId);
const screenshotsDir = path.join(artifactsDir, "screenshots");
const reportJsonPath = path.join(artifactsDir, "final-report.json");
const reportMarkdownPath = path.join(artifactsDir, "final-report.md");

const reportOrder = [
  "Login",
  "Mi Negocio menu",
  "Agregar Negocio modal",
  "Administrar Negocios view",
  "Informacion General",
  "Detalles de la Cuenta",
  "Tus Negocios",
  "Terminos y Condiciones",
  "Politica de Privacidad"
];

function normalizeName(value: string): string {
  return value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .trim();
}

function ensureArtifactDirs(): void {
  fs.mkdirSync(screenshotsDir, { recursive: true });
}

async function waitUiLoaded(page: Page): Promise<void> {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(WAIT_AFTER_CLICK_MS);
}

async function isVisible(locator: Locator): Promise<boolean> {
  try {
    return await locator.first().isVisible({ timeout: 3000 });
  } catch {
    return false;
  }
}

async function firstVisibleLocator(candidates: Locator[]): Promise<Locator | null> {
  for (const candidate of candidates) {
    if (await isVisible(candidate)) {
      return candidate.first();
    }
  }

  return null;
}

async function clickFirstVisible(candidates: Locator[], page: Page): Promise<boolean> {
  const locator = await firstVisibleLocator(candidates);
  if (!locator) {
    return false;
  }

  await locator.click();
  await waitUiLoaded(page);
  return true;
}

async function capture(page: Page, checkpoint: string, fullPage = false): Promise<string> {
  const safeName = checkpoint.toLowerCase().replace(/[^a-z0-9]+/g, "-");
  const filePath = path.join(screenshotsDir, `${safeName}.png`);
  await page.screenshot({ path: filePath, fullPage });
  return filePath;
}

function buildStep(name: string): StepResult {
  return { name, status: "PASS", details: [], evidence: [] };
}

function fail(step: StepResult, message: string): void {
  step.status = "FAIL";
  step.details.push(message);
}

function pass(step: StepResult, message: string): void {
  step.details.push(message);
}

async function findSidebar(page: Page): Promise<boolean> {
  const sidebarCandidates = [
    page.getByRole("navigation"),
    page.locator("aside"),
    page.getByText(/Negocio/i)
  ];

  const sidebar = await firstVisibleLocator(sidebarCandidates);
  return sidebar !== null;
}

async function clickGoogleAccountIfPrompted(page: Page): Promise<void> {
  const accountCandidates = [
    page.getByText(new RegExp(GOOGLE_ACCOUNT_EMAIL, "i")),
    page.getByRole("button", { name: new RegExp(GOOGLE_ACCOUNT_EMAIL, "i") }),
    page.locator(`[data-identifier="${GOOGLE_ACCOUNT_EMAIL}"]`)
  ];

  const account = await firstVisibleLocator(accountCandidates);
  if (account) {
    await account.click();
    await waitUiLoaded(page);
  }
}

async function openLegalLinkAndValidate(
  appPage: Page,
  labelRegex: RegExp,
  headingRegex: RegExp,
  screenshotName: string
): Promise<{ ok: boolean; url?: string; screenshot?: string; details: string[] }> {
  const details: string[] = [];
  const linkCandidates = [
    appPage.getByRole("link", { name: labelRegex }),
    appPage.getByRole("button", { name: labelRegex }),
    appPage.getByText(labelRegex)
  ];

  const link = await firstVisibleLocator(linkCandidates);
  if (!link) {
    return { ok: false, details: [`No se encontro enlace/boton para ${labelRegex}`] };
  }

  const popupPromise = appPage.context().waitForEvent("page", { timeout: 8000 }).catch(() => null);
  await link.click();
  await waitUiLoaded(appPage);

  const popup = await popupPromise;
  const targetPage = popup ?? appPage;
  await waitUiLoaded(targetPage);

  const headingVisible = await isVisible(targetPage.getByRole("heading", { name: headingRegex }));
  const textVisible = await isVisible(targetPage.getByText(headingRegex));
  const bodyVisible = (await targetPage.locator("body").innerText()).trim().length > 0;

  if (!headingVisible && !textVisible) {
    return {
      ok: false,
      details: [`No se encontro el encabezado esperado ${headingRegex} en la pagina legal`]
    };
  }

  if (!bodyVisible) {
    return { ok: false, details: ["No se detecto contenido legal visible en la pagina"] };
  }

  const screenshot = await capture(targetPage, screenshotName, true);
  details.push(`Contenido legal validado en URL: ${targetPage.url()}`);

  if (popup) {
    await popup.close();
    await appPage.bringToFront();
    await waitUiLoaded(appPage);
  }

  return { ok: true, url: targetPage.url(), screenshot, details };
}

function writeFinalReport(results: StepResult[]): void {
  const ordered = reportOrder.map((name) => {
    const match = results.find((result) => normalizeName(result.name) === normalizeName(name));
    return (
      match || {
        name,
        status: "FAIL",
        details: ["No se ejecuto la validacion de este paso"],
        evidence: []
      }
    );
  });

  fs.writeFileSync(
    reportJsonPath,
    JSON.stringify(
      {
        generatedAt: new Date().toISOString(),
        runId,
        results: ordered
      },
      null,
      2
    )
  );

  const lines = [
    "# SaleADS Mi Negocio Full Test",
    "",
    `- Generated at: ${new Date().toISOString()}`,
    `- Run ID: ${runId}`,
    "",
    "## Final Report",
    ""
  ];

  for (const result of ordered) {
    lines.push(`### ${result.name}: ${result.status}`);
    if (result.finalUrl) {
      lines.push(`- Final URL: ${result.finalUrl}`);
    }
    for (const detail of result.details) {
      lines.push(`- ${detail}`);
    }
    for (const evidence of result.evidence) {
      lines.push(`- Evidence: ${evidence}`);
    }
    lines.push("");
  }

  fs.writeFileSync(reportMarkdownPath, lines.join("\n"));
}

test("saleads_mi_negocio_full_test", async ({ page }) => {
  ensureArtifactDirs();

  const results: StepResult[] = [
    buildStep("Login"),
    buildStep("Mi Negocio menu"),
    buildStep("Agregar Negocio modal"),
    buildStep("Administrar Negocios view"),
    buildStep("Informacion General"),
    buildStep("Detalles de la Cuenta"),
    buildStep("Tus Negocios"),
    buildStep("Terminos y Condiciones"),
    buildStep("Politica de Privacidad")
  ];

  const loginStep = results[0];
  const menuStep = results[1];
  const modalStep = results[2];
  const adminStep = results[3];
  const infoStep = results[4];
  const detailStep = results[5];
  const businessStep = results[6];
  const termsStep = results[7];
  const privacyStep = results[8];

  const saleadsUrl = process.env.SALEADS_LOGIN_URL || process.env.SALEADS_BASE_URL || process.env.BASE_URL;
  if (saleadsUrl) {
    await page.goto(saleadsUrl, { waitUntil: "domcontentloaded" });
    await waitUiLoaded(page);
  } else {
    fail(
      loginStep,
      "No se definio SALEADS_LOGIN_URL ni SALEADS_BASE_URL. Configure la URL del ambiente para ejecutar el flujo."
    );
  }

  if (loginStep.status !== "FAIL") {
    const loginCandidates = [
      page.getByRole("button", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
      page.getByRole("link", { name: /sign in with google|iniciar sesi[oó]n con google|google/i }),
      page.getByText(/sign in with google|iniciar sesi[oó]n con google|google/i)
    ];
    const loginLocator = await firstVisibleLocator(loginCandidates);
    let maybePopup: Page | null = null;
    let loginClicked = false;

    if (loginLocator) {
      const popupPromise = page.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await loginLocator.click();
      await waitUiLoaded(page);
      maybePopup = await popupPromise;
      loginClicked = true;
    }

    if (!loginClicked) {
      fail(loginStep, "No se encontro el boton de inicio de sesion con Google.");
    } else {
      pass(loginStep, "Boton de login con Google encontrado y clicado.");

      if (maybePopup) {
        await maybePopup.waitForLoadState("domcontentloaded");
        await clickGoogleAccountIfPrompted(maybePopup);
      } else {
        await clickGoogleAccountIfPrompted(page);
      }

      await page.bringToFront();
      await waitUiLoaded(page);

      const sidebarVisible = await findSidebar(page);
      if (!sidebarVisible) {
        fail(loginStep, "No se detecto la interfaz principal ni sidebar tras el login.");
      } else {
        pass(loginStep, "Interfaz principal y sidebar visibles tras login.");
        loginStep.evidence.push(await capture(page, "dashboard-after-login", true));
      }
    }
  }

  const negocioClicked = await clickFirstVisible(
    [
      page.getByRole("button", { name: /negocio/i }),
      page.getByRole("link", { name: /negocio/i }),
      page.getByText(/^Negocio$/i)
    ],
    page
  );

  if (!negocioClicked) {
    fail(menuStep, "No se encontro la seccion Negocio en el sidebar.");
  } else {
    const miNegocioClicked = await clickFirstVisible(
      [
        page.getByRole("button", { name: /mi negocio/i }),
        page.getByRole("link", { name: /mi negocio/i }),
        page.getByText(/mi negocio/i)
      ],
      page
    );

    if (!miNegocioClicked) {
      fail(menuStep, "No se encontro la opcion Mi Negocio.");
    } else {
      const agregarVisible = await isVisible(page.getByText(/agregar negocio/i));
      const administrarVisible = await isVisible(page.getByText(/administrar negocios/i));

      if (!agregarVisible || !administrarVisible) {
        fail(menuStep, "No se ven ambas opciones: Agregar Negocio y Administrar Negocios.");
      } else {
        pass(menuStep, "Submenu Mi Negocio expandido con opciones esperadas.");
        menuStep.evidence.push(await capture(page, "mi-negocio-expanded-menu"));
      }
    }
  }

  const agregarClicked = await clickFirstVisible(
    [
      page.getByRole("button", { name: /agregar negocio/i }),
      page.getByRole("link", { name: /agregar negocio/i }),
      page.getByText(/agregar negocio/i)
    ],
    page
  );

  if (!agregarClicked) {
    fail(modalStep, "No fue posible abrir Agregar Negocio.");
  } else {
    const modalTitle = page.getByText(/crear nuevo negocio/i);
    const nombreField = page.getByLabel(/nombre del negocio/i).or(page.getByPlaceholder(/nombre del negocio/i));
    const quotaText = page.getByText(/tienes 2 de 3 negocios/i);
    const cancelBtn = page.getByRole("button", { name: /cancelar/i });
    const createBtn = page.getByRole("button", { name: /crear negocio/i });

    if (!(await isVisible(modalTitle))) {
      fail(modalStep, "No se encontro el titulo 'Crear Nuevo Negocio'.");
    }
    if (!(await isVisible(nombreField))) {
      fail(modalStep, "No se encontro el campo 'Nombre del Negocio'.");
    }
    if (!(await isVisible(quotaText))) {
      fail(modalStep, "No se encontro el texto 'Tienes 2 de 3 negocios'.");
    }
    if (!(await isVisible(cancelBtn)) || !(await isVisible(createBtn))) {
      fail(modalStep, "No se encontraron los botones 'Cancelar' y 'Crear Negocio'.");
    }

    if (await isVisible(nombreField)) {
      await nombreField.click();
      await nombreField.fill("Negocio Prueba Automatizacion");
      pass(modalStep, "Campo Nombre del Negocio editable.");
    }

    modalStep.evidence.push(await capture(page, "agregar-negocio-modal"));

    if (await isVisible(cancelBtn)) {
      await cancelBtn.click();
      await waitUiLoaded(page);
      pass(modalStep, "Modal cerrado con boton Cancelar.");
    }
  }

  const miNegocioReopen = await clickFirstVisible(
    [
      page.getByRole("button", { name: /mi negocio/i }),
      page.getByRole("link", { name: /mi negocio/i }),
      page.getByText(/mi negocio/i)
    ],
    page
  );

  if (!miNegocioReopen) {
    fail(adminStep, "No fue posible reexpandir Mi Negocio antes de Administrar Negocios.");
  }

  const administrarClicked = await clickFirstVisible(
    [
      page.getByRole("button", { name: /administrar negocios/i }),
      page.getByRole("link", { name: /administrar negocios/i }),
      page.getByText(/administrar negocios/i)
    ],
    page
  );

  if (!administrarClicked) {
    fail(adminStep, "No fue posible abrir Administrar Negocios.");
  } else {
    await waitUiLoaded(page);

    const infoGeneral = await isVisible(page.getByText(/informaci[oó]n general/i));
    const detallesCuenta = await isVisible(page.getByText(/detalles de la cuenta/i));
    const tusNegocios = await isVisible(page.getByText(/tus negocios/i));
    const legalSection = await isVisible(page.getByText(/secci[oó]n legal|legal/i));

    if (!infoGeneral || !detallesCuenta || !tusNegocios || !legalSection) {
      fail(adminStep, "Faltan secciones esperadas en Administrar Negocios.");
    } else {
      pass(adminStep, "Secciones principales de Administrar Negocios visibles.");
    }
    adminStep.evidence.push(await capture(page, "administrar-negocios-account-page", true));
  }

  if (!(await isVisible(page.getByText(/informaci[oó]n general/i)))) {
    fail(infoStep, "No se encontro la seccion Informacion General.");
  }
  if (!(await isVisible(page.getByText(/business plan/i)))) {
    fail(infoStep, "No se encontro el texto BUSINESS PLAN.");
  }
  if (!(await isVisible(page.getByRole("button", { name: /cambiar plan/i })))) {
    fail(infoStep, "No se encontro el boton Cambiar Plan.");
  }
  const emailVisible = await isVisible(page.getByText(/@/i));
  if (!emailVisible) {
    fail(infoStep, "No se encontro un correo visible en Informacion General.");
  }
  const usernameCandidates = [
    page.locator("h1"),
    page.locator("h2"),
    page.locator("[data-testid*='name']"),
    page.getByText(/[A-Za-z]{3,}\s+[A-Za-z]{3,}/)
  ];
  if (!(await firstVisibleLocator(usernameCandidates))) {
    fail(infoStep, "No se detecto nombre de usuario visible.");
  }
  if (infoStep.status === "PASS") {
    pass(infoStep, "Informacion General validada.");
  }

  if (!(await isVisible(page.getByText(/detalles de la cuenta/i)))) {
    fail(detailStep, "No se encontro la seccion Detalles de la Cuenta.");
  }
  if (!(await isVisible(page.getByText(/cuenta creada/i)))) {
    fail(detailStep, "No se encontro texto Cuenta creada.");
  }
  if (!(await isVisible(page.getByText(/estado activo/i)))) {
    fail(detailStep, "No se encontro texto Estado activo.");
  }
  if (!(await isVisible(page.getByText(/idioma seleccionado/i)))) {
    fail(detailStep, "No se encontro texto Idioma seleccionado.");
  }
  if (detailStep.status === "PASS") {
    pass(detailStep, "Detalles de la Cuenta validados.");
  }

  if (!(await isVisible(page.getByText(/tus negocios/i)))) {
    fail(businessStep, "No se encontro la seccion Tus Negocios.");
  }
  if (!(await isVisible(page.getByRole("button", { name: /agregar negocio/i })))) {
    fail(businessStep, "No se encontro boton Agregar Negocio en Tus Negocios.");
  }
  if (!(await isVisible(page.getByText(/tienes 2 de 3 negocios/i)))) {
    fail(businessStep, "No se encontro texto Tienes 2 de 3 negocios.");
  }
  if (businessStep.status === "PASS") {
    pass(businessStep, "Seccion Tus Negocios validada.");
  }

  const terms = await openLegalLinkAndValidate(
    page,
    /t[eé]rminos y condiciones/i,
    /t[eé]rminos y condiciones/i,
    "terminos-y-condiciones"
  );
  if (!terms.ok) {
    for (const detail of terms.details) {
      fail(termsStep, detail);
    }
  } else {
    termsStep.finalUrl = terms.url;
    terms.details.forEach((detail) => pass(termsStep, detail));
    if (terms.screenshot) {
      termsStep.evidence.push(terms.screenshot);
    }
  }

  const privacy = await openLegalLinkAndValidate(
    page,
    /pol[ií]tica de privacidad/i,
    /pol[ií]tica de privacidad/i,
    "politica-de-privacidad"
  );
  if (!privacy.ok) {
    for (const detail of privacy.details) {
      fail(privacyStep, detail);
    }
  } else {
    privacyStep.finalUrl = privacy.url;
    privacy.details.forEach((detail) => pass(privacyStep, detail));
    if (privacy.screenshot) {
      privacyStep.evidence.push(privacy.screenshot);
    }
  }

  writeFinalReport(results);

  const failed = results.filter((step) => step.status === "FAIL");
  expect(
    failed,
    `Se encontraron pasos fallidos. Reporte: ${reportJsonPath}\n${failed
      .map((step) => `- ${step.name}: ${step.details.join(" | ")}`)
      .join("\n")}`
  ).toEqual([]);
});
