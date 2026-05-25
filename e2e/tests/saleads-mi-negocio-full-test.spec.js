const { test, expect } = require("@playwright/test");
const { writeFile } = require("node:fs/promises");

const TARGET_GOOGLE_ACCOUNT = "juanlucasbarbiergarzon@gmail.com";
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

function buildDefaultReport() {
  return REPORT_FIELDS.reduce((result, field) => {
    result[field] = "FAIL";
    return result;
  }, {});
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

async function waitForUiToLoad(page) {
  await page.waitForLoadState("domcontentloaded", { timeout: 20_000 }).catch(() => {});
  await page.waitForLoadState("networkidle", { timeout: 10_000 }).catch(() => {});
  await page.waitForTimeout(600);
}

async function waitForVisibleAny(locatorFactories, description, timeoutMs = 15_000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    for (const createLocator of locatorFactories) {
      const locator = createLocator().first();
      const isVisible = await locator.isVisible().catch(() => false);
      if (isVisible) {
        return locator;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 250));
  }

  throw new Error(`Could not find visible element for: ${description}`);
}

async function clickByVisibleText(page, text) {
  const exactMatcher = new RegExp(`^\\s*${escapeRegex(text)}\\s*$`, "i");
  const partialMatcher = new RegExp(escapeRegex(text), "i");
  const locator = await waitForVisibleAny(
    [
      () => page.getByRole("button", { name: exactMatcher }),
      () => page.getByRole("link", { name: exactMatcher }),
      () => page.getByRole("menuitem", { name: exactMatcher }),
      () => page.getByRole("tab", { name: exactMatcher }),
      () => page.getByText(exactMatcher),
      () => page.getByText(partialMatcher),
    ],
    `click "${text}"`,
    20_000
  );

  await locator.click();
  await waitForUiToLoad(page);
}

async function clickIfVisible(page, text, timeoutMs = 5_000) {
  const exactMatcher = new RegExp(`^\\s*${escapeRegex(text)}\\s*$`, "i");
  const partialMatcher = new RegExp(escapeRegex(text), "i");
  const locator = await waitForVisibleAny(
    [
      () => page.getByRole("button", { name: exactMatcher }),
      () => page.getByRole("link", { name: exactMatcher }),
      () => page.getByText(exactMatcher),
      () => page.getByText(partialMatcher),
    ],
    `optional click "${text}"`,
    timeoutMs
  ).catch(() => null);

  if (!locator) {
    return false;
  }

  await locator.click();
  await waitForUiToLoad(page);
  return true;
}

function isLikelyNameLine(line) {
  const clean = line.trim();
  if (!clean || clean.length < 3 || clean.length > 80) {
    return false;
  }
  if (clean.includes("@")) {
    return false;
  }

  const lowered = clean.toLowerCase();
  const blockedFragments = [
    "información general",
    "detalles de la cuenta",
    "tus negocios",
    "sección legal",
    "business plan",
    "cambiar plan",
    "cuenta creada",
    "estado activo",
    "idioma seleccionado",
    "agregar negocio",
    "administrar negocios",
    "tienes ",
  ];
  if (blockedFragments.some((fragment) => lowered.includes(fragment))) {
    return false;
  }

  return /[A-Za-zÁÉÍÓÚÜÑáéíóúüñ]{2,}/.test(clean);
}

async function getSectionContainerByHeading(page, headingPattern) {
  const heading = await waitForVisibleAny(
    [
      () => page.getByRole("heading", { name: headingPattern }),
      () => page.getByText(headingPattern),
    ],
    `section heading ${headingPattern}`,
    20_000
  );

  const section = heading.locator("xpath=ancestor::*[self::section or self::article or self::div][1]");
  const sectionVisible = await section.isVisible().catch(() => false);
  return sectionVisible ? section : page.locator("body");
}

async function openLegalDocument({
  appPage,
  linkText,
  headingPattern,
  screenshotName,
  testInfo,
}) {
  const popupPromise = appPage.context().waitForEvent("page", { timeout: 8_000 }).catch(() => null);
  await clickByVisibleText(appPage, linkText);
  let legalPage = await popupPromise;

  if (legalPage) {
    await legalPage.waitForLoadState("domcontentloaded", { timeout: 30_000 }).catch(() => {});
    await waitForUiToLoad(legalPage);
  } else {
    legalPage = appPage;
    await waitForUiToLoad(legalPage);
  }

  await waitForVisibleAny(
    [
      () => legalPage.getByRole("heading", { name: headingPattern }),
      () => legalPage.getByText(headingPattern),
    ],
    `${linkText} heading`,
    30_000
  );

  const legalBodyText = (await legalPage.locator("body").innerText()).trim();
  if (legalBodyText.length < 120) {
    throw new Error(`${linkText} content appears too short to be valid legal text.`);
  }

  await legalPage.screenshot({
    path: testInfo.outputPath(screenshotName),
    fullPage: true,
  });

  const finalUrl = legalPage.url();

  if (legalPage !== appPage) {
    await legalPage.close();
    await appPage.bringToFront();
    await waitForUiToLoad(appPage);
  } else {
    await appPage.goBack({ waitUntil: "domcontentloaded" }).catch(() => {});
    await waitForUiToLoad(appPage);
  }

  return finalUrl;
}

test("saleads_mi_negocio_full_test", async ({ page }, testInfo) => {
  const report = buildDefaultReport();
  const failures = {};
  const evidenceUrls = {
    terminosYCondiciones: null,
    politicaDePrivacidad: null,
  };

  const loginUrl = process.env.SALEADS_LOGIN_URL || process.env.BASE_URL || process.env.PLAYWRIGHT_BASE_URL;
  if (!loginUrl) {
    throw new Error(
      "SALEADS_LOGIN_URL is required. Provide any environment login URL (dev/staging/prod) without hardcoding domains."
    );
  }

  await page.goto(loginUrl, { waitUntil: "domcontentloaded" });
  await waitForUiToLoad(page);

  await test.step("Step 1 - Login with Google", async () => {
    try {
      const signInButton = await waitForVisibleAny(
        [
          () => page.getByRole("button", { name: /sign in with google|iniciar sesión con google|google/i }),
          () => page.getByRole("link", { name: /sign in with google|iniciar sesión con google|google/i }),
          () => page.getByText(/sign in with google|iniciar sesión con google|google/i),
        ],
        "Google login button",
        20_000
      );

      const popupPromise = page.waitForEvent("popup", { timeout: 8_000 }).catch(() => null);
      await signInButton.click();
      await waitForUiToLoad(page);

      let googlePage = await popupPromise;
      if (googlePage) {
        await googlePage.waitForLoadState("domcontentloaded", { timeout: 30_000 }).catch(() => {});
        await waitForUiToLoad(googlePage);
      } else {
        googlePage = page;
      }

      const onGooglePage = /accounts\.google\.com/i.test(googlePage.url());
      if (onGooglePage) {
        await clickIfVisible(googlePage, TARGET_GOOGLE_ACCOUNT, 12_000);
      }

      if (googlePage !== page) {
        await googlePage.waitForEvent("close", { timeout: 45_000 }).catch(() => {});
        await page.bringToFront();
      }

      await waitForUiToLoad(page);

      await waitForVisibleAny(
        [
          () => page.getByRole("navigation"),
          () => page.locator("aside"),
          () => page.getByText(/negocio|mi negocio|dashboard/i),
        ],
        "main application interface",
        45_000
      );

      await waitForVisibleAny(
        [
          () => page.getByRole("navigation").filter({ hasText: /negocio|mi negocio/i }),
          () => page.locator("aside").filter({ hasText: /negocio|mi negocio/i }),
          () => page.getByText(/negocio/i),
        ],
        "left sidebar",
        20_000
      );

      await page.screenshot({
        path: testInfo.outputPath("01-dashboard-loaded.png"),
        fullPage: true,
      });

      report["Login"] = "PASS";
    } catch (error) {
      failures["Login"] = error.message;
    }
  });

  await test.step("Step 2 - Open Mi Negocio menu", async () => {
    try {
      await clickByVisibleText(page, "Negocio");
      await clickByVisibleText(page, "Mi Negocio");

      await waitForVisibleAny(
        [() => page.getByText(/^Agregar Negocio$/i), () => page.getByRole("button", { name: /^Agregar Negocio$/i })],
        "Agregar Negocio submenu option",
        20_000
      );
      await waitForVisibleAny(
        [
          () => page.getByText(/^Administrar Negocios$/i),
          () => page.getByRole("button", { name: /^Administrar Negocios$/i }),
          () => page.getByRole("link", { name: /^Administrar Negocios$/i }),
        ],
        "Administrar Negocios submenu option",
        20_000
      );

      await page.screenshot({
        path: testInfo.outputPath("02-mi-negocio-menu-expanded.png"),
        fullPage: true,
      });

      report["Mi Negocio menu"] = "PASS";
    } catch (error) {
      failures["Mi Negocio menu"] = error.message;
    }
  });

  await test.step("Step 3 - Validate Agregar Negocio modal", async () => {
    try {
      await clickByVisibleText(page, "Agregar Negocio");

      await waitForVisibleAny(
        [() => page.getByText(/^Crear Nuevo Negocio$/i), () => page.getByRole("heading", { name: /^Crear Nuevo Negocio$/i })],
        "Crear Nuevo Negocio modal title",
        20_000
      );
      const businessNameField = await waitForVisibleAny(
        [
          () => page.getByLabel(/Nombre del Negocio/i),
          () => page.getByPlaceholder(/Nombre del Negocio/i),
          () => page.locator('input[name*="nombre" i], input[id*="nombre" i]'),
        ],
        "Nombre del Negocio input",
        20_000
      );
      await waitForVisibleAny([() => page.getByText(/Tienes 2 de 3 negocios/i)], "Tienes 2 de 3 negocios text", 20_000);
      await waitForVisibleAny([() => page.getByRole("button", { name: /^Cancelar$/i })], "Cancelar button", 20_000);
      await waitForVisibleAny([() => page.getByRole("button", { name: /^Crear Negocio$/i })], "Crear Negocio button", 20_000);

      await page.screenshot({
        path: testInfo.outputPath("03-agregar-negocio-modal.png"),
        fullPage: true,
      });

      await businessNameField.click();
      await businessNameField.fill("Negocio Prueba Automatización");
      await clickByVisibleText(page, "Cancelar");

      report["Agregar Negocio modal"] = "PASS";
    } catch (error) {
      failures["Agregar Negocio modal"] = error.message;
    }
  });

  await test.step("Step 4 - Open Administrar Negocios", async () => {
    try {
      const administrarVisible = await page.getByText(/^Administrar Negocios$/i).first().isVisible().catch(() => false);
      if (!administrarVisible) {
        await clickByVisibleText(page, "Mi Negocio");
      }

      await clickByVisibleText(page, "Administrar Negocios");

      await waitForVisibleAny([() => page.getByText(/Información General/i)], "Información General section", 30_000);
      await waitForVisibleAny([() => page.getByText(/Detalles de la Cuenta/i)], "Detalles de la Cuenta section", 30_000);
      await waitForVisibleAny([() => page.getByText(/Tus Negocios/i)], "Tus Negocios section", 30_000);
      await waitForVisibleAny([() => page.getByText(/Sección Legal/i)], "Sección Legal section", 30_000);

      await page.screenshot({
        path: testInfo.outputPath("04-administrar-negocios-page.png"),
        fullPage: true,
      });

      report["Administrar Negocios view"] = "PASS";
    } catch (error) {
      failures["Administrar Negocios view"] = error.message;
    }
  });

  await test.step("Step 5 - Validate Información General", async () => {
    try {
      const infoSection = await getSectionContainerByHeading(page, /Información General/i);
      const sectionText = await infoSection.innerText();
      const lines = sectionText
        .split("\n")
        .map((line) => line.trim())
        .filter(Boolean);

      const likelyName = lines.find((line) => isLikelyNameLine(line));
      if (!likelyName) {
        throw new Error("Could not identify a likely user name in the Información General section.");
      }

      await waitForVisibleAny(
        [
          () => infoSection.getByText(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i),
          () => infoSection.getByText(new RegExp(escapeRegex(TARGET_GOOGLE_ACCOUNT), "i")),
        ],
        "user email in Información General",
        15_000
      );
      await waitForVisibleAny([() => infoSection.getByText(/BUSINESS PLAN/i)], "BUSINESS PLAN text", 15_000);
      await waitForVisibleAny([() => infoSection.getByRole("button", { name: /Cambiar Plan/i })], "Cambiar Plan button", 15_000);

      report["Información General"] = "PASS";
    } catch (error) {
      failures["Información General"] = error.message;
    }
  });

  await test.step("Step 6 - Validate Detalles de la Cuenta", async () => {
    try {
      const detailsSection = await getSectionContainerByHeading(page, /Detalles de la Cuenta/i);
      await waitForVisibleAny([() => detailsSection.getByText(/Cuenta creada/i)], "Cuenta creada text", 15_000);
      await waitForVisibleAny([() => detailsSection.getByText(/Estado activo/i)], "Estado activo text", 15_000);
      await waitForVisibleAny([() => detailsSection.getByText(/Idioma seleccionado/i)], "Idioma seleccionado text", 15_000);

      report["Detalles de la Cuenta"] = "PASS";
    } catch (error) {
      failures["Detalles de la Cuenta"] = error.message;
    }
  });

  await test.step("Step 7 - Validate Tus Negocios", async () => {
    try {
      const businessSection = await getSectionContainerByHeading(page, /Tus Negocios/i);
      await waitForVisibleAny(
        [() => businessSection.getByRole("button", { name: /Agregar Negocio/i }), () => businessSection.getByText(/Agregar Negocio/i)],
        "Agregar Negocio button in Tus Negocios",
        15_000
      );
      await waitForVisibleAny([() => businessSection.getByText(/Tienes 2 de 3 negocios/i)], "Tienes 2 de 3 negocios text", 15_000);

      const sectionText = (await businessSection.innerText()).trim();
      if (sectionText.length < 40) {
        throw new Error("Tus Negocios section content appears empty.");
      }

      report["Tus Negocios"] = "PASS";
    } catch (error) {
      failures["Tus Negocios"] = error.message;
    }
  });

  await test.step("Step 8 - Validate Términos y Condiciones", async () => {
    try {
      evidenceUrls.terminosYCondiciones = await openLegalDocument({
        appPage: page,
        linkText: "Términos y Condiciones",
        headingPattern: /Términos y Condiciones/i,
        screenshotName: "05-terminos-y-condiciones.png",
        testInfo,
      });

      report["Términos y Condiciones"] = "PASS";
    } catch (error) {
      failures["Términos y Condiciones"] = error.message;
    }
  });

  await test.step("Step 9 - Validate Política de Privacidad", async () => {
    try {
      evidenceUrls.politicaDePrivacidad = await openLegalDocument({
        appPage: page,
        linkText: "Política de Privacidad",
        headingPattern: /Política de Privacidad/i,
        screenshotName: "06-politica-de-privacidad.png",
        testInfo,
      });

      report["Política de Privacidad"] = "PASS";
    } catch (error) {
      failures["Política de Privacidad"] = error.message;
    }
  });

  const finalReport = {
    report,
    legalUrls: evidenceUrls,
    failures,
  };

  await writeFile(testInfo.outputPath("final-report.json"), JSON.stringify(finalReport, null, 2), "utf8");

  console.log("==== saleads_mi_negocio_full_test report ====");
  for (const field of REPORT_FIELDS) {
    console.log(`${field}: ${report[field]}`);
  }
  if (evidenceUrls.terminosYCondiciones) {
    console.log(`Términos y Condiciones URL: ${evidenceUrls.terminosYCondiciones}`);
  }
  if (evidenceUrls.politicaDePrivacidad) {
    console.log(`Política de Privacidad URL: ${evidenceUrls.politicaDePrivacidad}`);
  }
  if (Object.keys(failures).length) {
    console.log(`Failure details: ${JSON.stringify(failures, null, 2)}`);
  }

  const failedFields = REPORT_FIELDS.filter((field) => report[field] !== "PASS");
  expect(failedFields, `Failed validations: ${failedFields.join(", ")}`).toEqual([]);
});
