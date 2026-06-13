const fs = require("fs");
const { test, expect } = require("@playwright/test");

const TARGET_EMAIL = process.env.SALEADS_GOOGLE_EMAIL || "juanlucasbarbiergarzon@gmail.com";
const BASE_URL = process.env.SALEADS_BASE_URL;

function normalizeFileName(name) {
  return name.toLowerCase().replace(/[^a-z0-9]+/g, "_");
}

async function waitForUi(page) {
  await Promise.allSettled([
    page.waitForLoadState("domcontentloaded", { timeout: 15000 }),
    page.waitForLoadState("networkidle", { timeout: 8000 })
  ]);
  await page.waitForTimeout(500);
}

async function findFirstVisible(candidates, timeoutMs = 20000) {
  const start = Date.now();

  while (Date.now() - start < timeoutMs) {
    for (const candidate of candidates) {
      const locator = candidate.first();
      if (await locator.isVisible().catch(() => false)) {
        return locator;
      }
    }
    await candidates[0].page().waitForTimeout(250);
  }

  throw new Error("No matching visible element was found.");
}

async function clickAndWait(page, locator) {
  await locator.scrollIntoViewIfNeeded().catch(() => {});
  await locator.click();
  await waitForUi(page);
}

async function checkpoint(page, testInfo, name, fullPage = false) {
  const filePath = testInfo.outputPath(`${normalizeFileName(name)}.png`);
  await page.screenshot({ path: filePath, fullPage });
  await testInfo.attach(name, { path: filePath, contentType: "image/png" });
}

async function setStatus(report, key, run) {
  const existing = report[key] || {};
  try {
    await run();
    report[key] = { ...existing, ...report[key], status: "PASS" };
  } catch (error) {
    report[key] = {
      ...existing,
      status: "FAIL",
      error: error instanceof Error ? error.message : String(error)
    };
  }
}

async function ensureOnLoginPage(page) {
  if (BASE_URL) {
    await page.goto(BASE_URL, { waitUntil: "domcontentloaded" });
    await waitForUi(page);
    return;
  }

  if (page.url() === "about:blank") {
    throw new Error(
      "No login page is open. Set SALEADS_BASE_URL for non-interactive runs."
    );
  }
}

async function clickTextAction(page, regex, timeoutMs = 20000) {
  const locator = await findFirstVisible(
    [
      page.getByRole("button", { name: regex }),
      page.getByRole("link", { name: regex }),
      page.getByRole("menuitem", { name: regex }),
      page.getByText(regex)
    ],
    timeoutMs
  );
  await clickAndWait(page, locator);
  return locator;
}

async function maybeSelectGoogleAccount(page, popupPage) {
  const accountRegex = new RegExp(TARGET_EMAIL.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "i");
  const authPage = popupPage || page;

  const accountLocator = await findFirstVisible(
    [
      authPage.getByText(accountRegex),
      authPage.getByRole("button", { name: accountRegex }),
      authPage.getByRole("link", { name: accountRegex })
    ],
    7000
  ).catch(() => null);

  if (accountLocator) {
    await clickAndWait(authPage, accountLocator);
  }
}

async function openLegalAndReturn({
  page,
  testInfo,
  linkRegex,
  headingRegex,
  screenshotName,
  report,
  reportKey
}) {
  const context = page.context();
  const link = await findFirstVisible(
    [
      page.getByRole("link", { name: linkRegex }),
      page.getByRole("button", { name: linkRegex }),
      page.getByText(linkRegex)
    ],
    20000
  );

  const popupPromise = context.waitForEvent("page", { timeout: 8000 }).catch(() => null);
  const sameTabNavigation = page
    .waitForNavigation({ waitUntil: "domcontentloaded", timeout: 12000 })
    .catch(() => null);

  await clickAndWait(page, link);

  const popup = await popupPromise;
  const legalPage = popup || page;
  if (!popup) {
    await sameTabNavigation;
  }
  await waitForUi(legalPage);

  await expect(legalPage.getByText(headingRegex).first()).toBeVisible({ timeout: 20000 });

  const legalTextLength = await legalPage
    .locator("main, body")
    .first()
    .innerText()
    .then((text) => text.trim().length)
    .catch(() => 0);

  if (legalTextLength < 120) {
    throw new Error("Legal page content looks too short to be valid.");
  }

  await checkpoint(legalPage, testInfo, screenshotName, true);
  report[reportKey] = { status: "PASS", url: legalPage.url() };

  if (popup) {
    await popup.close().catch(() => {});
    await page.bringToFront();
    await waitForUi(page);
    return;
  }

  await page.goBack({ waitUntil: "domcontentloaded" }).catch(async () => {
    if (BASE_URL) {
      await page.goto(BASE_URL, { waitUntil: "domcontentloaded" });
    } else {
      throw new Error(
        "Could not return from legal page in same tab and no SALEADS_BASE_URL was provided."
      );
    }
  });
  await waitForUi(page);
}

test.describe("saleads_mi_negocio_full_test", () => {
  test("Login and validate Mi Negocio workflow", async ({ page }, testInfo) => {
    const report = {
      Login: { status: "FAIL", error: "Not executed" },
      "Mi Negocio menu": { status: "FAIL", error: "Not executed" },
      "Agregar Negocio modal": { status: "FAIL", error: "Not executed" },
      "Administrar Negocios view": { status: "FAIL", error: "Not executed" },
      "Información General": { status: "FAIL", error: "Not executed" },
      "Detalles de la Cuenta": { status: "FAIL", error: "Not executed" },
      "Tus Negocios": { status: "FAIL", error: "Not executed" },
      "Términos y Condiciones": { status: "FAIL", error: "Not executed" },
      "Política de Privacidad": { status: "FAIL", error: "Not executed" }
    };

    await setStatus(report, "Login", async () => {
      await ensureOnLoginPage(page);

      const loginButton = await findFirstVisible(
        [
          page.getByRole("button", { name: /google|sign in|iniciar sesi[oó]n/i }),
          page.getByRole("link", { name: /google|sign in|iniciar sesi[oó]n/i }),
          page.getByText(/google|sign in|iniciar sesi[oó]n/i)
        ],
        20000
      );

      const popupPromise = page.context().waitForEvent("page", { timeout: 10000 }).catch(() => null);
      await clickAndWait(page, loginButton);
      const popupPage = await popupPromise;

      if (popupPage) {
        await waitForUi(popupPage);
      } else {
        await waitForUi(page);
      }

      await maybeSelectGoogleAccount(page, popupPage);

      if (popupPage) {
        await popupPage.waitForEvent("close", { timeout: 25000 }).catch(() => {});
      }

      await waitForUi(page);
      await expect(
        await findFirstVisible(
          [page.locator("aside"), page.getByRole("navigation"), page.getByText(/mi negocio|negocio/i)],
          30000
        )
      ).toBeVisible();

      await checkpoint(page, testInfo, "dashboard_loaded", true);
    });

    await setStatus(report, "Mi Negocio menu", async () => {
      await clickTextAction(page, /negocio/i);
      await clickTextAction(page, /mi negocio/i);

      await expect(page.getByText(/agregar negocio/i).first()).toBeVisible();
      await expect(page.getByText(/administrar negocios/i).first()).toBeVisible();
      await checkpoint(page, testInfo, "mi_negocio_menu_expanded");
    });

    await setStatus(report, "Agregar Negocio modal", async () => {
      await clickTextAction(page, /agregar negocio/i);
      const modal = await findFirstVisible(
        [
          page.getByRole("dialog").filter({ hasText: /crear nuevo negocio/i }),
          page.locator("[role='dialog'], .modal, .ant-modal").filter({ hasText: /crear nuevo negocio/i })
        ],
        15000
      );

      await expect(modal.getByText(/crear nuevo negocio/i)).toBeVisible();
      const nameInput = await findFirstVisible(
        [
          modal.getByLabel(/nombre del negocio/i),
          modal.getByPlaceholder(/nombre del negocio/i),
          modal.locator("input").first()
        ],
        10000
      );

      await expect(nameInput).toBeVisible();
      await expect(modal.getByText(/tienes 2 de 3 negocios/i)).toBeVisible();
      await expect(modal.getByText(/cancelar/i)).toBeVisible();
      await expect(modal.getByText(/crear negocio/i)).toBeVisible();

      await nameInput.click();
      await nameInput.fill("Negocio Prueba Automatización");
      await clickAndWait(page, modal.getByText(/cancelar/i).first());
      await checkpoint(page, testInfo, "agregar_negocio_modal");
    });

    await setStatus(report, "Administrar Negocios view", async () => {
      if (!(await page.getByText(/administrar negocios/i).first().isVisible().catch(() => false))) {
        await clickTextAction(page, /mi negocio/i);
      }

      await clickTextAction(page, /administrar negocios/i);

      await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible();
      await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
      await expect(page.getByText(/secci[oó]n legal/i).first()).toBeVisible();

      await checkpoint(page, testInfo, "administrar_negocios_page", true);
    });

    await setStatus(report, "Información General", async () => {
      const infoSection = page
        .locator("section, article, div")
        .filter({ has: page.getByText(/informaci[oó]n general/i).first() })
        .first();

      await expect(page.getByText(/informaci[oó]n general/i).first()).toBeVisible();

      const identity = await infoSection.evaluate((node) => {
        const textNodes = Array.from(node.querySelectorAll("*"))
          .map((el) => el.textContent || "")
          .map((text) => text.trim())
          .filter(Boolean);

        const hasEmail = textNodes.some((text) => /[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/i.test(text));
        const hasLikelyName = textNodes.some(
          (text) =>
            !/@/.test(text) &&
            !/informaci[oó]n general|business plan|cambiar plan/i.test(text) &&
            /^[A-Za-zÁÉÍÓÚÑáéíóúñ][A-Za-zÁÉÍÓÚÑáéíóúñ '.-]{2,}$/u.test(text)
        );

        return { hasEmail, hasLikelyName };
      });

      if (!identity.hasLikelyName) {
        throw new Error("User name was not detected in Información General.");
      }

      if (!identity.hasEmail) {
        throw new Error("User email was not detected in Información General.");
      }

      await expect(page.getByText(/business plan/i).first()).toBeVisible();
      await expect(
        await findFirstVisible([page.getByRole("button", { name: /cambiar plan/i }), page.getByText(/cambiar plan/i)])
      ).toBeVisible();
    });

    await setStatus(report, "Detalles de la Cuenta", async () => {
      await expect(page.getByText(/detalles de la cuenta/i).first()).toBeVisible();
      await expect(page.getByText(/cuenta creada/i).first()).toBeVisible();
      await expect(page.getByText(/estado activo/i).first()).toBeVisible();
      await expect(page.getByText(/idioma seleccionado/i).first()).toBeVisible();
    });

    await setStatus(report, "Tus Negocios", async () => {
      const businessesSection = page
        .locator("section, article, div")
        .filter({ has: page.getByText(/tus negocios/i).first() })
        .first();

      await expect(page.getByText(/tus negocios/i).first()).toBeVisible();
      await expect(
        await findFirstVisible([page.getByRole("button", { name: /agregar negocio/i }), page.getByText(/agregar negocio/i)])
      ).toBeVisible();
      await expect(page.getByText(/tienes 2 de 3 negocios/i).first()).toBeVisible();

      const hasListLikeElements = await businessesSection.evaluate((node) => {
        const selectors = ["li", "tr", "[role='listitem']", ".card", ".business-card"];
        return selectors.some((selector) => node.querySelectorAll(selector).length > 0);
      });

      if (!hasListLikeElements) {
        throw new Error("Business list structure was not detected in Tus Negocios.");
      }
    });

    await setStatus(report, "Términos y Condiciones", async () => {
      await openLegalAndReturn({
        page,
        testInfo,
        linkRegex: /t[eé]rminos y condiciones/i,
        headingRegex: /t[eé]rminos y condiciones/i,
        screenshotName: "terminos_y_condiciones",
        report,
        reportKey: "Términos y Condiciones"
      });
    });

    await setStatus(report, "Política de Privacidad", async () => {
      await openLegalAndReturn({
        page,
        testInfo,
        linkRegex: /pol[ií]tica de privacidad/i,
        headingRegex: /pol[ií]tica de privacidad/i,
        screenshotName: "politica_de_privacidad",
        report,
        reportKey: "Política de Privacidad"
      });
    });

    const reportPath = testInfo.outputPath("saleads_mi_negocio_full_report.json");
    fs.writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
    await testInfo.attach("saleads_mi_negocio_full_report", {
      path: reportPath,
      contentType: "application/json"
    });

    const failedSteps = Object.entries(report)
      .filter(([, value]) => value.status !== "PASS")
      .map(([name, value]) => `${name}: ${value.error || "Unknown error"}`);

    if (failedSteps.length > 0) {
      throw new Error(`One or more workflow validations failed:\n${failedSteps.join("\n")}`);
    }
  });
});
