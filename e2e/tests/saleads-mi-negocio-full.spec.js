const { test, expect } = require("@playwright/test");

const REPORT_FIELDS = [
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

const LOGIN_URL = process.env.SALEADS_LOGIN_URL || process.env.BASE_URL;

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function toRegex(textOrRegex) {
  return textOrRegex instanceof RegExp ? textOrRegex : new RegExp(escapeRegExp(textOrRegex), "i");
}

async function waitForUi(page) {
  await page.waitForLoadState("domcontentloaded");
  await page.waitForTimeout(700);
}

async function findVisibleTarget(page, labels) {
  const regexes = labels.map(toRegex);

  for (const pattern of regexes) {
    const candidates = [
      page.getByRole("button", { name: pattern }).first(),
      page.getByRole("link", { name: pattern }).first(),
      page.getByRole("menuitem", { name: pattern }).first(),
      page.getByRole("tab", { name: pattern }).first(),
      page.getByText(pattern).first()
    ];

    for (const candidate of candidates) {
      const visible = await candidate.isVisible().catch(() => false);
      if (visible) {
        return candidate;
      }
    }
  }

  throw new Error(`Could not find a visible element for labels: ${labels.join(", ")}`);
}

async function clickByVisibleText(page, labels) {
  const target = await findVisibleTarget(page, labels);
  await target.click();
  await waitForUi(page);
}

async function assertTextVisible(page, textOrRegex) {
  const pattern = toRegex(textOrRegex);
  await expect(page.getByText(pattern).first()).toBeVisible();
}

async function captureCheckpoint(page, testInfo, fileName, fullPage = false) {
  await page.screenshot({
    path: testInfo.outputPath(fileName),
    fullPage
  });
}

async function chooseGoogleAccountIfShown(pageOrPopup) {
  const accountEmail = "juanlucasbarbiergarzon@gmail.com";
  const accountTarget = pageOrPopup.getByText(accountEmail, { exact: false }).first();
  const isVisible = await accountTarget.isVisible().catch(() => false);

  if (isVisible) {
    await accountTarget.click();
    await waitForUi(pageOrPopup);
  }
}

test("saleads_mi_negocio_full_test", async ({ page, context }, testInfo) => {
  const report = Object.fromEntries(REPORT_FIELDS.map((field) => [field, "FAIL"]));
  const notes = {
    terminosUrl: "",
    politicaUrl: ""
  };
  const errors = [];

  const runStep = async (field, action) => {
    try {
      await action();
      report[field] = "PASS";
    } catch (error) {
      report[field] = "FAIL";
      errors.push(`${field}: ${error.message}`);
    }
  };

  if (page.url() === "about:blank") {
    if (!LOGIN_URL) {
      throw new Error(
        "No login page detected. Set SALEADS_LOGIN_URL (or BASE_URL) so the test can run in the current environment."
      );
    }

    await page.goto(LOGIN_URL, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
  }

  await runStep("Login", async () => {
    const popupPromise = context.waitForEvent("page", { timeout: 12000 }).catch(() => null);

    await clickByVisibleText(page, [
      /sign in with google/i,
      /iniciar sesi.n con google/i,
      /continuar con google/i,
      /google/i
    ]);

    const googlePopup = await popupPromise;
    if (googlePopup) {
      await googlePopup.waitForLoadState("domcontentloaded");
      await chooseGoogleAccountIfShown(googlePopup);
      await googlePopup.waitForTimeout(1000);
      await page.bringToFront();
    } else {
      await chooseGoogleAccountIfShown(page);
    }

    await waitForUi(page);
    await expect(page.locator("aside, nav").first()).toBeVisible();
    await assertTextVisible(page, /negocio/i);
    await captureCheckpoint(page, testInfo, "01-dashboard-loaded.png", true);
  });

  await runStep("Mi Negocio menu", async () => {
    await clickByVisibleText(page, [/negocio/i]);
    await clickByVisibleText(page, [/mi negocio/i]);

    await assertTextVisible(page, /agregar negocio/i);
    await assertTextVisible(page, /administrar negocios/i);
    await captureCheckpoint(page, testInfo, "02-mi-negocio-menu-expanded.png");
  });

  await runStep("Agregar Negocio modal", async () => {
    await clickByVisibleText(page, [/agregar negocio/i]);

    await assertTextVisible(page, /crear nuevo negocio/i);
    await expect(page.getByLabel(/nombre del negocio/i)).toBeVisible();
    await assertTextVisible(page, /tienes 2 de 3 negocios/i);
    await assertTextVisible(page, /cancelar/i);
    await assertTextVisible(page, /crear negocio/i);
    await captureCheckpoint(page, testInfo, "03-agregar-negocio-modal.png");

    const nameInput = page.getByLabel(/nombre del negocio/i);
    await nameInput.click();
    await nameInput.fill("Negocio Prueba Automatizacion");
    await clickByVisibleText(page, [/cancelar/i]);
  });

  await runStep("Administrar Negocios view", async () => {
    const administrarVisible = await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false);
    if (!administrarVisible) {
      await clickByVisibleText(page, [/mi negocio/i]);
    }

    await clickByVisibleText(page, [/administrar negocios/i]);

    await assertTextVisible(page, /informaci.n general/i);
    await assertTextVisible(page, /detalles de la cuenta/i);
    await assertTextVisible(page, /tus negocios/i);
    await assertTextVisible(page, /secci.n legal/i);
    await captureCheckpoint(page, testInfo, "04-administrar-negocios-page.png", true);
  });

  await runStep("Informacion General", async () => {
    const bodyText = await page.locator("body").innerText();
    const emailMatch = bodyText.match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i);

    if (!emailMatch) {
      throw new Error("No user email was found in Informacion General.");
    }

    const prefix = bodyText.slice(Math.max(0, bodyText.indexOf(emailMatch[0]) - 120), bodyText.indexOf(emailMatch[0]));
    const hasNameLikeText = prefix
      .split("\n")
      .map((line) => line.trim())
      .some((line) => line.length >= 3 && !/informaci.n|general|business plan|cambiar/i.test(line));

    if (!hasNameLikeText) {
      throw new Error("Could not identify a visible user name near the email.");
    }

    await assertTextVisible(page, /business plan/i);
    await assertTextVisible(page, /cambiar plan/i);
  });

  await runStep("Detalles de la Cuenta", async () => {
    await assertTextVisible(page, /cuenta creada/i);
    await assertTextVisible(page, /estado activo/i);
    await assertTextVisible(page, /idioma seleccionado/i);
  });

  await runStep("Tus Negocios", async () => {
    await assertTextVisible(page, /tus negocios/i);
    await assertTextVisible(page, /agregar negocio/i);
    await assertTextVisible(page, /tienes 2 de 3 negocios/i);

    const tusNegociosHeading = page.getByText(/tus negocios/i).first();
    const sectionText = await tusNegociosHeading.evaluate((element) => {
      const container = element.closest("section") || element.parentElement;
      return container ? container.innerText : "";
    });

    if (!sectionText || sectionText.length < 80) {
      throw new Error("Business list content was not detected in Tus Negocios.");
    }
  });

  await runStep("Terminos y Condiciones", async () => {
    const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await clickByVisibleText(page, [/t.rminos y condiciones/i]);
    const popup = await popupPromise;
    const legalPage = popup || page;

    await legalPage.waitForLoadState("domcontentloaded");
    await waitForUi(legalPage);
    await assertTextVisible(legalPage, /t.rminos y condiciones/i);

    const legalContent = await legalPage.locator("body").innerText();
    if (!legalContent || legalContent.length < 120) {
      throw new Error("Legal content text is too short for Terminos y Condiciones.");
    }

    notes.terminosUrl = legalPage.url();
    await captureCheckpoint(legalPage, testInfo, "05-terminos-y-condiciones.png", true);

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
      await waitForUi(page);
    }
  });

  await runStep("Politica de Privacidad", async () => {
    const popupPromise = context.waitForEvent("page", { timeout: 7000 }).catch(() => null);
    await clickByVisibleText(page, [/pol.tica de privacidad/i]);
    const popup = await popupPromise;
    const legalPage = popup || page;

    await legalPage.waitForLoadState("domcontentloaded");
    await waitForUi(legalPage);
    await assertTextVisible(legalPage, /pol.tica de privacidad/i);

    const legalContent = await legalPage.locator("body").innerText();
    if (!legalContent || legalContent.length < 120) {
      throw new Error("Legal content text is too short for Politica de Privacidad.");
    }

    notes.politicaUrl = legalPage.url();
    await captureCheckpoint(legalPage, testInfo, "06-politica-de-privacidad.png", true);

    if (popup) {
      await popup.close();
      await page.bringToFront();
      await waitForUi(page);
    } else {
      await page.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
      await waitForUi(page);
    }
  });

  const finalReport = {
    testName: "saleads_mi_negocio_full_test",
    statusByField: report,
    evidence: {
      dashboardScreenshot: "01-dashboard-loaded.png",
      menuScreenshot: "02-mi-negocio-menu-expanded.png",
      modalScreenshot: "03-agregar-negocio-modal.png",
      administrarNegociosScreenshot: "04-administrar-negocios-page.png",
      terminosScreenshot: "05-terminos-y-condiciones.png",
      politicaScreenshot: "06-politica-de-privacidad.png"
    },
    urls: {
      terminosYCondiciones: notes.terminosUrl,
      politicaDePrivacidad: notes.politicaUrl
    },
    errors
  };

  await testInfo.attach("final-report.json", {
    body: Buffer.from(JSON.stringify(finalReport, null, 2), "utf-8"),
    contentType: "application/json"
  });

  // Keep this visible in CLI logs for automation runs.
  console.log(`Final report: ${JSON.stringify(finalReport, null, 2)}`);

  if (errors.length > 0) {
    throw new Error(`Validation failures:\n- ${errors.join("\n- ")}`);
  }
});
