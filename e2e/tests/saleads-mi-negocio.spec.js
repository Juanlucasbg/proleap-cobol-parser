const fs = require("fs");
const { test, expect } = require("@playwright/test");

const DEFAULT_EMAIL = "juanlucasbarbiergarzon@gmail.com";

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function cleanError(error) {
  return String(error && error.message ? error.message : error).split("\n")[0];
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 10000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10000 }).catch(() => {});
  await page.waitForTimeout(700);
}

async function requireVisible(locator, message, timeout = 15000) {
  await locator.first().waitFor({ state: "visible", timeout }).catch(() => {
    throw new Error(message);
  });
}

async function pickFirstVisible(candidates) {
  for (const locator of candidates) {
    if (await locator.first().isVisible().catch(() => false)) {
      return locator.first();
    }
  }
  return null;
}

async function clickAndWait(locator, page) {
  await locator.click();
  await waitForUi(page);
}

async function checkpoint(page, testInfo, name, fullPage = false) {
  const safeName = name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/(^-|-$)/g, "");
  await page.screenshot({
    path: testInfo.outputPath(`${safeName}.png`),
    fullPage,
  });
}

function setResult(report, field, pass, detail) {
  report[field] = {
    status: pass ? "PASS" : "FAIL",
    detail: detail || "",
  };
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const loginUrl = process.env.SALEADS_START_URL || "";
  const googleEmail = process.env.SALEADS_GOOGLE_EMAIL || DEFAULT_EMAIL;
  const expectedUserName = process.env.SALEADS_USER_NAME || "";
  const legalUrls = {};
  const report = {
    Login: { status: "FAIL", detail: "Not executed" },
    "Mi Negocio menu": { status: "FAIL", detail: "Not executed" },
    "Agregar Negocio modal": { status: "FAIL", detail: "Not executed" },
    "Administrar Negocios view": { status: "FAIL", detail: "Not executed" },
    "Información General": { status: "FAIL", detail: "Not executed" },
    "Detalles de la Cuenta": { status: "FAIL", detail: "Not executed" },
    "Tus Negocios": { status: "FAIL", detail: "Not executed" },
    "Términos y Condiciones": { status: "FAIL", detail: "Not executed" },
    "Política de Privacidad": { status: "FAIL", detail: "Not executed" },
  };

  const runStep = async (field, fn) => {
    try {
      await fn();
      setResult(report, field, true);
    } catch (error) {
      setResult(report, field, false, cleanError(error));
    }
  };

  await runStep("Login", async () => {
    if (loginUrl) {
      await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
      await waitForUi(page);
    } else if (page.url() === "about:blank") {
      throw new Error("Set SALEADS_START_URL or start from login page.");
    }

    const alreadyLoggedIn = await pickFirstVisible([
      page.getByRole("navigation"),
      page.locator("aside"),
      page.getByText(/mi negocio/i),
      page.getByText(/negocio/i),
    ]);
    if (alreadyLoggedIn) {
      await checkpoint(page, testInfo, "01-dashboard-loaded");
      return;
    }

    const signInWithGoogle = await pickFirstVisible([
      page.getByRole("button", { name: /sign in with google/i }),
      page.getByRole("button", { name: /iniciar sesi[oó]n con google/i }),
      page.getByRole("button", { name: /continuar con google/i }),
      page.getByText(/sign in with google/i),
      page.getByText(/iniciar sesi[oó]n con google/i),
      page.getByText(/continuar con google/i),
    ]);

    if (!signInWithGoogle) {
      throw new Error("Google login button was not found.");
    }

    const popupPromise = page.waitForEvent("popup", { timeout: 8000 }).catch(() => null);
    await signInWithGoogle.click();
    const popup = await popupPromise;
    const activePage = popup || page;
    await waitForUi(activePage);

    const accountChoice = activePage.getByText(new RegExp(escapeRegex(googleEmail), "i")).first();
    const accountShown = await accountChoice
      .waitFor({ state: "visible", timeout: 12000 })
      .then(() => true)
      .catch(() => false);
    if (accountShown) {
      await accountChoice.click();
      await waitForUi(activePage);
    }

    if (popup) {
      await page.bringToFront();
      await waitForUi(page);
    }

    const sidebar = await pickFirstVisible([
      page.getByRole("navigation"),
      page.locator("aside"),
      page.getByText(/negocio/i),
      page.getByText(/mi negocio/i),
    ]);
    if (!sidebar) {
      throw new Error("Sidebar navigation is not visible after login.");
    }

    await checkpoint(page, testInfo, "01-dashboard-loaded");
  });

  await runStep("Mi Negocio menu", async () => {
    const negocioEntry = await pickFirstVisible([
      page.getByRole("button", { name: /^Negocio$/i }),
      page.getByText(/^Negocio$/i),
      page.getByText(/Negocio/i),
    ]);
    if (negocioEntry) {
      await clickAndWait(negocioEntry, page);
    }

    const miNegocioEntry = await pickFirstVisible([
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i),
    ]);
    if (!miNegocioEntry) {
      throw new Error("Mi Negocio menu option is not visible.");
    }
    await clickAndWait(miNegocioEntry, page);

    await requireVisible(
      page.getByText(/^Agregar Negocio$/i),
      "Agregar Negocio option is not visible in submenu."
    );
    await requireVisible(
      page.getByText(/^Administrar Negocios$/i),
      "Administrar Negocios option is not visible in submenu."
    );

    await checkpoint(page, testInfo, "02-mi-negocio-menu-expanded");
  });

  await runStep("Agregar Negocio modal", async () => {
    const addBusiness = await pickFirstVisible([
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      page.getByRole("link", { name: /^Agregar Negocio$/i }),
      page.getByText(/^Agregar Negocio$/i),
    ]);
    if (!addBusiness) {
      throw new Error("Agregar Negocio action is not available.");
    }
    await clickAndWait(addBusiness, page);

    await requireVisible(
      page.getByText(/Crear Nuevo Negocio/i),
      'Modal title "Crear Nuevo Negocio" is not visible.'
    );
    const businessNameInput = await pickFirstVisible([
      page.getByLabel(/Nombre del Negocio/i),
      page.getByPlaceholder(/Nombre del Negocio/i),
      page.locator("input").filter({ hasText: /Nombre del Negocio/i }),
      page.locator("input[name*='negocio' i]"),
    ]);
    if (!businessNameInput) {
      throw new Error('Input "Nombre del Negocio" is not visible.');
    }
    await requireVisible(
      page.getByText(/Tienes 2 de 3 negocios/i),
      'Text "Tienes 2 de 3 negocios" is not visible.'
    );
    await requireVisible(page.getByRole("button", { name: /^Cancelar$/i }), "Cancelar button is missing.");
    await requireVisible(
      page.getByRole("button", { name: /^Crear Negocio$/i }),
      "Crear Negocio button is missing."
    );

    await checkpoint(page, testInfo, "03-agregar-negocio-modal");

    await businessNameInput.click();
    await businessNameInput.fill("Negocio Prueba Automatización");
    await clickAndWait(page.getByRole("button", { name: /^Cancelar$/i }), page);
  });

  await runStep("Administrar Negocios view", async () => {
    const miNegocioEntry = await pickFirstVisible([
      page.getByRole("button", { name: /^Mi Negocio$/i }),
      page.getByRole("link", { name: /^Mi Negocio$/i }),
      page.getByText(/^Mi Negocio$/i),
    ]);
    if (miNegocioEntry) {
      await clickAndWait(miNegocioEntry, page);
    }

    const manageBusinesses = await pickFirstVisible([
      page.getByRole("button", { name: /^Administrar Negocios$/i }),
      page.getByRole("link", { name: /^Administrar Negocios$/i }),
      page.getByText(/^Administrar Negocios$/i),
    ]);
    if (!manageBusinesses) {
      throw new Error("Administrar Negocios option is not visible.");
    }
    await clickAndWait(manageBusinesses, page);

    await requireVisible(page.getByText(/^Informaci[oó]n General$/i), "Información General section is missing.");
    await requireVisible(
      page.getByText(/^Detalles de la Cuenta$/i),
      "Detalles de la Cuenta section is missing."
    );
    await requireVisible(page.getByText(/^Tus Negocios$/i), "Tus Negocios section is missing.");
    await requireVisible(
      page.getByText(/Secci[oó]n Legal/i),
      "Sección Legal section is missing."
    );

    await checkpoint(page, testInfo, "04-administrar-negocios", true);
  });

  await runStep("Información General", async () => {
    await requireVisible(page.getByText(new RegExp(escapeRegex(googleEmail), "i")), "User email is not visible.");
    await requireVisible(page.getByText(/BUSINESS PLAN/i), "BUSINESS PLAN text is missing.");
    await requireVisible(
      page.getByRole("button", { name: /Cambiar Plan/i }),
      "Cambiar Plan button is missing."
    );

    if (expectedUserName) {
      await requireVisible(
        page.getByText(new RegExp(escapeRegex(expectedUserName), "i")),
        "Configured user name is not visible."
      );
    } else {
      const allText = (await page.locator("body").innerText()).split("\n").map((line) => line.trim());
      const likelyName = allText.find((line) =>
        /^[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,}(?:\s+[A-Za-zÁÉÍÓÚÑáéíóúñ]{2,})+$/.test(line)
      );
      if (!likelyName) {
        throw new Error("User name is not clearly visible.");
      }
    }
  });

  await runStep("Detalles de la Cuenta", async () => {
    await requireVisible(page.getByText(/Cuenta creada/i), '"Cuenta creada" is missing.');
    await requireVisible(page.getByText(/Estado activo/i), '"Estado activo" is missing.');
    await requireVisible(page.getByText(/Idioma seleccionado/i), '"Idioma seleccionado" is missing.');
  });

  await runStep("Tus Negocios", async () => {
    await requireVisible(page.getByText(/^Tus Negocios$/i), "Tus Negocios section title is missing.");
    await requireVisible(
      page.getByRole("button", { name: /^Agregar Negocio$/i }),
      "Agregar Negocio button in business list is missing."
    );
    await requireVisible(
      page.getByText(/Tienes 2 de 3 negocios/i),
      '"Tienes 2 de 3 negocios" is missing in Tus Negocios.'
    );
  });

  await runStep("Términos y Condiciones", async () => {
    const appPage = page;
    const appUrl = appPage.url();
    const termsLink = await pickFirstVisible([
      appPage.getByRole("link", { name: /T[ée]rminos y Condiciones/i }),
      appPage.getByText(/T[ée]rminos y Condiciones/i),
    ]);
    if (!termsLink) {
      throw new Error("Términos y Condiciones link is missing.");
    }

    const popupPromise = appPage.waitForEvent("popup", { timeout: 8000 }).catch(() => null);
    const navPromise = appPage.waitForNavigation({ waitUntil: "domcontentloaded", timeout: 8000 }).catch(
      () => null
    );

    await termsLink.click();
    const popup = await popupPromise;
    await navPromise.catch(() => {});

    const legalPage = popup || appPage;
    await waitForUi(legalPage);

    await requireVisible(
      legalPage.getByRole("heading", { name: /T[ée]rminos y Condiciones/i }),
      "Heading Términos y Condiciones is missing.",
      10000
    );
    await requireVisible(legalPage.getByText(/T[ée]rminos|Condiciones|legal/i), "Legal terms content is missing.");

    legalUrls.terms = legalPage.url();
    await checkpoint(legalPage, testInfo, "05-terminos-y-condiciones", true);

    if (popup) {
      await popup.close();
      await appPage.bringToFront();
    } else if (appPage.url() !== appUrl) {
      await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
        await appPage.goto(appUrl, { waitUntil: "domcontentloaded" });
      });
      await waitForUi(appPage);
    }
  });

  await runStep("Política de Privacidad", async () => {
    const appPage = page;
    const appUrl = appPage.url();
    const privacyLink = await pickFirstVisible([
      appPage.getByRole("link", { name: /Pol[ií]tica de Privacidad/i }),
      appPage.getByText(/Pol[ií]tica de Privacidad/i),
    ]);
    if (!privacyLink) {
      throw new Error("Política de Privacidad link is missing.");
    }

    const popupPromise = appPage.waitForEvent("popup", { timeout: 8000 }).catch(() => null);
    const navPromise = appPage.waitForNavigation({ waitUntil: "domcontentloaded", timeout: 8000 }).catch(
      () => null
    );

    await privacyLink.click();
    const popup = await popupPromise;
    await navPromise.catch(() => {});

    const legalPage = popup || appPage;
    await waitForUi(legalPage);

    await requireVisible(
      legalPage.getByRole("heading", { name: /Pol[ií]tica de Privacidad/i }),
      "Heading Política de Privacidad is missing.",
      10000
    );
    await requireVisible(
      legalPage.getByText(/Privacidad|Datos|informaci[oó]n/i),
      "Privacy legal content is missing."
    );

    legalUrls.privacy = legalPage.url();
    await checkpoint(legalPage, testInfo, "06-politica-de-privacidad", true);

    if (popup) {
      await popup.close();
      await appPage.bringToFront();
    } else if (appPage.url() !== appUrl) {
      await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
        await appPage.goto(appUrl, { waitUntil: "domcontentloaded" });
      });
      await waitForUi(appPage);
    }
  });

  const finalReport = {
    generatedAt: new Date().toISOString(),
    urls: legalUrls,
    steps: report,
  };

  const lines = Object.entries(report).map(
    ([field, result]) => `- ${field}: ${result.status}${result.detail ? ` (${result.detail})` : ""}`
  );
  const finalText = ["Mi Negocio Full Workflow Report", ...lines].join("\n");
  console.log(`\n${finalText}\n`);

  fs.writeFileSync(testInfo.outputPath("mi-negocio-final-report.json"), JSON.stringify(finalReport, null, 2));
  fs.writeFileSync(testInfo.outputPath("mi-negocio-final-report.txt"), `${finalText}\n`);

  const failed = Object.entries(report).filter(([, result]) => result.status === "FAIL");
  expect(
    failed,
    `Some workflow checks failed.\n${failed.map(([name, result]) => `- ${name}: ${result.detail}`).join("\n")}`
  ).toEqual([]);
});
