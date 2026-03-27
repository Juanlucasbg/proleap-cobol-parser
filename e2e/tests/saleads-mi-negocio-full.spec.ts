import { expect, test, type BrowserContext, type Page, type TestInfo } from "@playwright/test";

type StepKey =
	| "Login"
	| "Mi Negocio menu"
	| "Agregar Negocio modal"
	| "Administrar Negocios view"
	| "Informacion General"
	| "Detalles de la Cuenta"
	| "Tus Negocios"
	| "Terminos y Condiciones"
	| "Politica de Privacidad";

type StepStatus = "PASS" | "FAIL" | "SKIPPED";

class WorkflowReporter {
	private readonly statuses: Record<StepKey, StepStatus> = {
		Login: "SKIPPED",
		"Mi Negocio menu": "SKIPPED",
		"Agregar Negocio modal": "SKIPPED",
		"Administrar Negocios view": "SKIPPED",
		"Informacion General": "SKIPPED",
		"Detalles de la Cuenta": "SKIPPED",
		"Tus Negocios": "SKIPPED",
		"Terminos y Condiciones": "SKIPPED",
		"Politica de Privacidad": "SKIPPED",
	};

	setStatus(step: StepKey, status: StepStatus): void {
		this.statuses[step] = status;
	}

	async attach(testInfo: TestInfo): Promise<void> {
		const report = {
			login: this.statuses.Login,
			miNegocioMenu: this.statuses["Mi Negocio menu"],
			agregarNegocioModal: this.statuses["Agregar Negocio modal"],
			administrarNegociosView: this.statuses["Administrar Negocios view"],
			informacionGeneral: this.statuses["Informacion General"],
			detallesDeLaCuenta: this.statuses["Detalles de la Cuenta"],
			tusNegocios: this.statuses["Tus Negocios"],
			terminosYCondiciones: this.statuses["Terminos y Condiciones"],
			politicaDePrivacidad: this.statuses["Politica de Privacidad"],
		};

		await testInfo.attach("final-report.json", {
			contentType: "application/json",
			body: Buffer.from(JSON.stringify(report, null, 2), "utf-8"),
		});
	}
}

const googleAccountEmail = "juanlucasbarbiergarzon@gmail.com";
const baseUrlFromEnv = process.env.SALEADS_BASE_URL?.trim();

const waitForUIAfterClick = async (page: Page): Promise<void> => {
	await page.waitForLoadState("domcontentloaded");
	await page.waitForLoadState("networkidle");
};

const visibleTextRegex = (options: string[]): RegExp => {
	const escaped = options.map((o) => o.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"));
	return new RegExp(`^(${escaped.join("|")})$`, "i");
};

const clickAnyVisibleText = async (page: Page, textOptions: string[]): Promise<void> => {
	for (const option of textOptions) {
		const exact = page.getByText(option, { exact: true }).first();
		if (await exact.isVisible().catch(() => false)) {
			await exact.click();
			await waitForUIAfterClick(page);
			return;
		}
	}

	const fuzzy = page.getByText(visibleTextRegex(textOptions)).first();
	await expect(fuzzy).toBeVisible({ timeout: 20000 });
	await fuzzy.click();
	await waitForUIAfterClick(page);
};

const expectAnyHeadingOrText = async (page: Page, options: string[]): Promise<void> => {
	for (const option of options) {
		const heading = page.getByRole("heading", { name: option }).first();
		if (await heading.isVisible().catch(() => false)) {
			return;
		}

		const exactText = page.getByText(option, { exact: true }).first();
		if (await exactText.isVisible().catch(() => false)) {
			return;
		}
	}

	await expect(page.getByText(visibleTextRegex(options)).first()).toBeVisible({ timeout: 20000 });
};

const expandMiNegocioMenu = async (page: Page): Promise<void> => {
	await clickAnyVisibleText(page, ["Mi Negocio", "Negocio"]);
	await expectAnyHeadingOrText(page, ["Agregar Negocio"]);
	await expectAnyHeadingOrText(page, ["Administrar Negocios"]);
};

const clickLegalAndValidate = async (
	context: BrowserContext,
	page: Page,
	linkTextOptions: string[],
	expectedHeadingOptions: string[],
	screenshotName: string,
	testInfo: TestInfo,
): Promise<string> => {
	let link;
	for (const txt of linkTextOptions) {
		link = page.getByText(txt, { exact: true }).first();
		if (await link.isVisible().catch(() => false)) {
			break;
		}
	}
	if (!link || !(await link.isVisible().catch(() => false))) {
		link = page.getByText(visibleTextRegex(linkTextOptions)).first();
	}
	await expect(link).toBeVisible({ timeout: 15000 });

	const [newTabOrNull] = await Promise.all([
		context.waitForEvent("page", { timeout: 6000 }).catch(() => null),
		link.click(),
	]);

	const targetPage = newTabOrNull ?? page;
	await targetPage.waitForLoadState("domcontentloaded");
	await targetPage.waitForLoadState("networkidle");

	await expectAnyHeadingOrText(targetPage, expectedHeadingOptions);
	await expect(
		targetPage
			.locator("main, article, section, body")
			.filter({ hasText: /t[eé]rminos|condiciones|privacidad|datos|informaci[oó]n|legal/i })
			.first(),
	).toBeVisible({ timeout: 15000 });

	await testInfo.attach(`${screenshotName}.png`, {
		contentType: "image/png",
		body: await targetPage.screenshot({ fullPage: true }),
	});

	const finalUrl = targetPage.url();
	await testInfo.attach(`${screenshotName}-url.txt`, {
		contentType: "text/plain",
		body: Buffer.from(finalUrl, "utf-8"),
	});

	if (newTabOrNull) {
		await newTabOrNull.close();
		await page.bringToFront();
		await waitForUIAfterClick(page);
	}

	return finalUrl;
};

test.describe("SaleADS Mi Negocio full workflow", () => {
	test("logs in and validates full Mi Negocio flow", async ({ page, context }, testInfo) => {
		const reporter = new WorkflowReporter();
		let loginReached = false;

		if (baseUrlFromEnv) {
			await page.goto(baseUrlFromEnv, { waitUntil: "domcontentloaded" });
			await page.waitForLoadState("networkidle");
		}

		try {
			// Step 1: Login with Google.
			const loginButton = page
				.getByRole("button", { name: /google|iniciar sesi[oó]n|sign in/i })
				.first()
				.or(page.getByText(/sign in with google|continuar con google|iniciar sesi[oó]n con google/i).first());

			await expect(loginButton).toBeVisible({ timeout: 30000 });
			await loginButton.click();

			const accountTile = page.getByText(googleAccountEmail, { exact: true });
			if (await accountTile.isVisible().catch(() => false)) {
				await accountTile.click();
			}

			await waitForUIAfterClick(page);

			const sidebar = page
				.locator("aside, nav")
				.filter({ hasText: /negocio|mi negocio|dashboard|inicio|menu|men[uú]/i })
				.first();

			await expect(sidebar).toBeVisible({ timeout: 60000 });
			reporter.setStatus("Login", "PASS");
			loginReached = true;

			await testInfo.attach("01-dashboard-loaded.png", {
				contentType: "image/png",
				body: await page.screenshot({ fullPage: true }),
			});
		} catch (error) {
			reporter.setStatus("Login", "FAIL");
			await reporter.attach(testInfo);
			throw error;
		}

		if (!loginReached) {
			await reporter.attach(testInfo);
			throw new Error("Login did not complete; stopping workflow.");
		}

		try {
			// Step 2: Open Mi Negocio menu.
			await expandMiNegocioMenu(page);
			reporter.setStatus("Mi Negocio menu", "PASS");

			await testInfo.attach("02-mi-negocio-menu-expanded.png", {
				contentType: "image/png",
				body: await page.screenshot({ fullPage: true }),
			});
		} catch (error) {
			reporter.setStatus("Mi Negocio menu", "FAIL");
			await reporter.attach(testInfo);
			throw error;
		}

		try {
			// Step 3: Validate Agregar Negocio modal.
			await clickAnyVisibleText(page, ["Agregar Negocio"]);

			await expectAnyHeadingOrText(page, ["Crear Nuevo Negocio"]);
			const nombreField = page.getByLabel("Nombre del Negocio").or(page.getByPlaceholder("Nombre del Negocio"));
			await expect(nombreField).toBeVisible({ timeout: 15000 });
			await expectAnyHeadingOrText(page, ["Tienes 2 de 3 negocios"]);
			await expect(page.getByRole("button", { name: /cancelar/i })).toBeVisible({ timeout: 10000 });
			await expect(page.getByRole("button", { name: /crear negocio/i })).toBeVisible({ timeout: 10000 });

			await nombreField.click();
			await nombreField.fill("Negocio Prueba Automatizacion");

			await testInfo.attach("03-agregar-negocio-modal.png", {
				contentType: "image/png",
				body: await page.screenshot({ fullPage: true }),
			});

			await page.getByRole("button", { name: /cancelar/i }).click();
			await waitForUIAfterClick(page);
			reporter.setStatus("Agregar Negocio modal", "PASS");
		} catch (error) {
			reporter.setStatus("Agregar Negocio modal", "FAIL");
			await reporter.attach(testInfo);
			throw error;
		}

		try {
			// Step 4: Open Administrar Negocios.
			const administrar = page.getByText("Administrar Negocios", { exact: true }).first();
			if (!(await administrar.isVisible().catch(() => false))) {
				await expandMiNegocioMenu(page);
			}

			await clickAnyVisibleText(page, ["Administrar Negocios"]);
			await expectAnyHeadingOrText(page, ["Información General", "Informacion General"]);
			await expectAnyHeadingOrText(page, ["Detalles de la Cuenta"]);
			await expectAnyHeadingOrText(page, ["Tus Negocios"]);
			await expectAnyHeadingOrText(page, ["Sección Legal", "Seccion Legal"]);

			await testInfo.attach("04-administrar-negocios-view.png", {
				contentType: "image/png",
				body: await page.screenshot({ fullPage: true }),
			});
			reporter.setStatus("Administrar Negocios view", "PASS");
		} catch (error) {
			reporter.setStatus("Administrar Negocios view", "FAIL");
			await reporter.attach(testInfo);
			throw error;
		}

		try {
			// Step 5: Validate Informacion General.
			const userName = page.locator("text=/[A-Z][a-z]+\\s+[A-Z][a-z]+/").first();
			const userEmail = page.locator("text=/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}/").first();
			await expect(userName).toBeVisible({ timeout: 15000 });
			await expect(userEmail).toBeVisible({ timeout: 15000 });
			await expectAnyHeadingOrText(page, ["BUSINESS PLAN"]);
			await expect(page.getByRole("button", { name: /cambiar plan/i })).toBeVisible({ timeout: 15000 });
			reporter.setStatus("Informacion General", "PASS");
		} catch (error) {
			reporter.setStatus("Informacion General", "FAIL");
			await reporter.attach(testInfo);
			throw error;
		}

		try {
			// Step 6: Validate Detalles de la Cuenta.
			await expectAnyHeadingOrText(page, ["Cuenta creada"]);
			await expectAnyHeadingOrText(page, ["Estado activo"]);
			await expectAnyHeadingOrText(page, ["Idioma seleccionado"]);
			reporter.setStatus("Detalles de la Cuenta", "PASS");
		} catch (error) {
			reporter.setStatus("Detalles de la Cuenta", "FAIL");
			await reporter.attach(testInfo);
			throw error;
		}

		try {
			// Step 7: Validate Tus Negocios.
			await expectAnyHeadingOrText(page, ["Tus Negocios"]);
			await expect(page.getByRole("button", { name: /agregar negocio/i })).toBeVisible({ timeout: 15000 });
			await expectAnyHeadingOrText(page, ["Tienes 2 de 3 negocios"]);
			reporter.setStatus("Tus Negocios", "PASS");
		} catch (error) {
			reporter.setStatus("Tus Negocios", "FAIL");
			await reporter.attach(testInfo);
			throw error;
		}

		try {
			// Step 8: Validate Terminos y Condiciones.
			await clickLegalAndValidate(
				context,
				page,
				["Términos y Condiciones", "Terminos y Condiciones"],
				["Términos y Condiciones", "Terminos y Condiciones"],
				"08-terminos-y-condiciones",
				testInfo,
			);
			reporter.setStatus("Terminos y Condiciones", "PASS");
		} catch (error) {
			reporter.setStatus("Terminos y Condiciones", "FAIL");
			await reporter.attach(testInfo);
			throw error;
		}

		try {
			// Step 9: Validate Politica de Privacidad.
			await clickLegalAndValidate(
				context,
				page,
				["Política de Privacidad", "Politica de Privacidad"],
				["Política de Privacidad", "Politica de Privacidad"],
				"09-politica-de-privacidad",
				testInfo,
			);
			reporter.setStatus("Politica de Privacidad", "PASS");
		} catch (error) {
			reporter.setStatus("Politica de Privacidad", "FAIL");
			await reporter.attach(testInfo);
			throw error;
		}

		// Step 10: final report.
		await reporter.attach(testInfo);
	});
});
