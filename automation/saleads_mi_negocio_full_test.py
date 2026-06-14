#!/usr/bin/env python3
import argparse
import json
import os
import re
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple

from playwright.sync_api import Error, Page, Playwright, TimeoutError, sync_playwright


REPORT_FIELDS: Sequence[str] = (
    "Login",
    "Mi Negocio menu",
    "Agregar Negocio modal",
    "Administrar Negocios view",
    "Información General",
    "Detalles de la Cuenta",
    "Tus Negocios",
    "Términos y Condiciones",
    "Política de Privacidad",
)


@dataclass
class StepResult:
    status: str = "FAIL"
    details: str = "Not executed."
    screenshot: Optional[str] = None
    final_url: Optional[str] = None


class SaleadsMiNegocioWorkflowRunner:
    def __init__(self, login_url: Optional[str], google_email: str, output_dir: Path, headless: bool) -> None:
        self.login_url = login_url
        self.google_email = google_email
        self.output_dir = output_dir
        self.headless = headless
        self.results: Dict[str, StepResult] = {field: StepResult() for field in REPORT_FIELDS}
        self.started_at = datetime.now(timezone.utc).isoformat()

    def wait_ui(self, page: Page) -> None:
        for state in ("domcontentloaded", "networkidle"):
            try:
                page.wait_for_load_state(state, timeout=12000)
            except TimeoutError:
                pass
        page.wait_for_timeout(1200)

    def capture(self, page: Page, name: str, full_page: bool = True) -> str:
        path = self.output_dir / f"{name}.png"
        page.screenshot(path=str(path), full_page=full_page)
        return str(path)

    def first_visible_locator(self, page: Page, patterns: Sequence[str]):
        frames = [page.main_frame] + [frame for frame in page.frames if frame != page.main_frame]

        for frame in frames:
            for pattern in patterns:
                regex = re.compile(pattern, re.IGNORECASE)
                locators = (
                    frame.get_by_role("button", name=regex).first,
                    frame.get_by_role("link", name=regex).first,
                    frame.get_by_role("menuitem", name=regex).first,
                    frame.get_by_text(regex).first,
                )

                for locator in locators:
                    try:
                        if locator.count() > 0 and locator.is_visible(timeout=500):
                            return locator
                    except Error:
                        continue
        return None

    def set_pass(self, field: str, details: str, screenshot: Optional[str] = None, final_url: Optional[str] = None) -> None:
        self.results[field] = StepResult("PASS", details, screenshot, final_url)

    def set_fail(self, field: str, details: str, screenshot: Optional[str] = None, final_url: Optional[str] = None) -> None:
        self.results[field] = StepResult("FAIL", details, screenshot, final_url)

    def fail_remaining_with_prerequisite(self, reason: str) -> None:
        for field in REPORT_FIELDS[1:]:
            if self.results[field].details == "Not executed.":
                self.set_fail(field, f"Prerequisite failed: {reason}")

    def is_visible_text(self, page: Page, text_pattern: str, timeout_ms: float = 3000) -> bool:
        try:
            return page.get_by_text(re.compile(text_pattern, re.IGNORECASE)).first.is_visible(timeout=timeout_ms)
        except Error:
            return False

    def run(self) -> int:
        if not self.login_url:
            self.set_fail("Login", "Missing login URL. Set SALEADS_LOGIN_URL or use --login-url.")
            self.fail_remaining_with_prerequisite("Login step failed.")
            self.write_reports()
            return 1

        self.output_dir.mkdir(parents=True, exist_ok=True)

        with sync_playwright() as playwright:
            return self._execute_workflow(playwright)

    def _execute_workflow(self, playwright: Playwright) -> int:
        browser = playwright.chromium.launch(headless=self.headless)
        context = browser.new_context(viewport={"width": 1440, "height": 1024})
        page = context.new_page()

        try:
            login_ok = self.step_login(page)
            if not login_ok:
                self.fail_remaining_with_prerequisite("Login step failed.")
                self.write_reports()
                return 1

            self.step_mi_negocio_menu(page)
            self.step_agregar_negocio_modal(page)
            self.step_administrar_negocios(page)
            self.step_informacion_general(page)
            self.step_detalles_cuenta(page)
            self.step_tus_negocios(page)
            self.step_legal_link(
                app_page=page,
                report_field="Términos y Condiciones",
                link_patterns=(r"Términos y Condiciones",),
                heading_pattern=r"Términos y Condiciones",
                screenshot_name="step8_terminos_condiciones",
            )
            self.step_legal_link(
                app_page=page,
                report_field="Política de Privacidad",
                link_patterns=(r"Política de Privacidad",),
                heading_pattern=r"Política de Privacidad",
                screenshot_name="step9_politica_privacidad",
            )
        finally:
            try:
                self.capture(page, "final_state")
            except Error:
                pass
            browser.close()

        self.write_reports()
        all_pass = all(result.status == "PASS" for result in self.results.values())
        return 0 if all_pass else 1

    def step_login(self, page: Page) -> bool:
        try:
            page.goto(self.login_url or "", wait_until="domcontentloaded", timeout=45000)
            self.wait_ui(page)
        except TimeoutError:
            screenshot = self.capture(page, "step1_login_timeout", full_page=True)
            self.set_fail("Login", "Could not load login page.", screenshot=screenshot, final_url=page.url)
            return False

        login_button = self.first_visible_locator(
            page,
            patterns=(
                r"Sign in with Google",
                r"Iniciar sesión con Google",
                r"Inicia sesión con Google",
                r"Continuar con Google",
                r"Sign in",
                r"Inicia sesión",
                r"Login",
            ),
        )

        if not login_button:
            screenshot = self.capture(page, "step1_login_button_not_found")
            self.set_fail(
                "Login",
                "Could not find login button or Google sign-in button by visible text.",
                screenshot=screenshot,
                final_url=page.url,
            )
            return False

        login_button.click()
        self.wait_ui(page)

        # If the Google account selector appears, choose the requested account.
        account_choice = self.first_visible_locator(page, patterns=(re.escape(self.google_email),))
        if account_choice:
            account_choice.click()
            self.wait_ui(page)

        main_interface_visible = (
            page.locator("main").first.is_visible(timeout=5000)
            or self.is_visible_text(page, r"Dashboard|Inicio|Panel")
            or self.is_visible_text(page, r"Mi\s*Negocio|Negocio")
        )

        sidebar_visible = False
        try:
            sidebar_visible = page.locator("aside").first.is_visible(timeout=5000)
        except Error:
            sidebar_visible = self.is_visible_text(page, r"Mi\s*Negocio|Negocio")

        screenshot = self.capture(page, "step1_dashboard_loaded")
        if main_interface_visible and sidebar_visible:
            self.set_pass(
                "Login",
                "Main interface and left sidebar are visible after login.",
                screenshot=screenshot,
                final_url=page.url,
            )
            return True

        self.set_fail(
            "Login",
            "Login click executed, but main interface/sidebar validation failed.",
            screenshot=screenshot,
            final_url=page.url,
        )
        return False

    def step_mi_negocio_menu(self, page: Page) -> None:
        mi_negocio = self.first_visible_locator(page, (r"Mi\s*Negocio", r"Negocio"))
        if not mi_negocio:
            self.set_fail("Mi Negocio menu", "Could not find 'Negocio'/'Mi Negocio' option in sidebar.", final_url=page.url)
            return

        mi_negocio.click()
        self.wait_ui(page)

        agregar_visible = self.is_visible_text(page, r"Agregar Negocio")
        administrar_visible = self.is_visible_text(page, r"Administrar Negocios")
        screenshot = self.capture(page, "step2_mi_negocio_expanded")

        if agregar_visible and administrar_visible:
            self.set_pass(
                "Mi Negocio menu",
                "Menu expanded and both submenu options are visible.",
                screenshot=screenshot,
                final_url=page.url,
            )
        else:
            self.set_fail(
                "Mi Negocio menu",
                "Menu clicked but expected submenu options were not fully visible.",
                screenshot=screenshot,
                final_url=page.url,
            )

    def step_agregar_negocio_modal(self, page: Page) -> None:
        agregar = self.first_visible_locator(page, (r"Agregar Negocio",))
        if not agregar:
            self.set_fail("Agregar Negocio modal", "Could not find 'Agregar Negocio' action.", final_url=page.url)
            return

        agregar.click()
        self.wait_ui(page)

        checks = [
            self.is_visible_text(page, r"Crear Nuevo Negocio"),
            self.is_visible_text(page, r"Nombre del Negocio"),
            self.is_visible_text(page, r"Tienes 2 de 3 negocios"),
            self.is_visible_text(page, r"Cancelar"),
            self.is_visible_text(page, r"Crear Negocio"),
        ]
        screenshot = self.capture(page, "step3_agregar_negocio_modal")

        if all(checks):
            # Optional action: type sample business name then cancel.
            try:
                nombre_input = page.get_by_label(re.compile(r"Nombre del Negocio", re.IGNORECASE)).first
                if nombre_input.count() > 0:
                    nombre_input.fill("Negocio Prueba Automatizacion")
            except Error:
                pass

            cancelar = self.first_visible_locator(page, (r"Cancelar",))
            if cancelar:
                cancelar.click()
                self.wait_ui(page)

            self.set_pass(
                "Agregar Negocio modal",
                "Modal validated with all required fields and actions.",
                screenshot=screenshot,
                final_url=page.url,
            )
        else:
            self.set_fail(
                "Agregar Negocio modal",
                "Modal opened but one or more required fields/text/buttons are missing.",
                screenshot=screenshot,
                final_url=page.url,
            )

    def step_administrar_negocios(self, page: Page) -> None:
        mi_negocio = self.first_visible_locator(page, (r"Mi\s*Negocio", r"Negocio"))
        if mi_negocio and not self.is_visible_text(page, r"Administrar Negocios", timeout_ms=1000):
            mi_negocio.click()
            self.wait_ui(page)

        administrar = self.first_visible_locator(page, (r"Administrar Negocios",))
        if not administrar:
            self.set_fail("Administrar Negocios view", "Could not find 'Administrar Negocios'.", final_url=page.url)
            return

        administrar.click()
        self.wait_ui(page)

        checks = [
            self.is_visible_text(page, r"Informaci[oó]n General"),
            self.is_visible_text(page, r"Detalles de la Cuenta"),
            self.is_visible_text(page, r"Tus Negocios"),
            self.is_visible_text(page, r"Secci[oó]n Legal"),
        ]
        screenshot = self.capture(page, "step4_administrar_negocios", full_page=True)

        if all(checks):
            self.set_pass(
                "Administrar Negocios view",
                "Account page loaded with all expected sections.",
                screenshot=screenshot,
                final_url=page.url,
            )
        else:
            self.set_fail(
                "Administrar Negocios view",
                "Account page opened but required sections are missing.",
                screenshot=screenshot,
                final_url=page.url,
            )

    def step_informacion_general(self, page: Page) -> None:
        checks = [
            self.is_visible_text(page, r"BUSINESS PLAN"),
            self.is_visible_text(page, r"Cambiar Plan"),
        ]

        email_visible = self.is_visible_text(page, r"@")
        user_name_visible = bool(page.locator("h1,h2,h3,strong").count() > 0)

        if all(checks) and email_visible and user_name_visible:
            self.set_pass("Información General", "Información General shows user info and plan controls.", final_url=page.url)
        else:
            self.set_fail(
                "Información General",
                "Missing at least one required element (name, email, BUSINESS PLAN, Cambiar Plan).",
                final_url=page.url,
            )

    def step_detalles_cuenta(self, page: Page) -> None:
        checks = [
            self.is_visible_text(page, r"Cuenta creada"),
            self.is_visible_text(page, r"Estado activo"),
            self.is_visible_text(page, r"Idioma seleccionado"),
        ]
        if all(checks):
            self.set_pass("Detalles de la Cuenta", "All Detalles de la Cuenta fields are visible.", final_url=page.url)
        else:
            self.set_fail("Detalles de la Cuenta", "Missing one or more account detail fields.", final_url=page.url)

    def step_tus_negocios(self, page: Page) -> None:
        checks = [
            self.is_visible_text(page, r"Tus Negocios"),
            self.is_visible_text(page, r"Agregar Negocio"),
            self.is_visible_text(page, r"Tienes 2 de 3 negocios"),
        ]
        if all(checks):
            self.set_pass("Tus Negocios", "Business list section and required controls are visible.", final_url=page.url)
        else:
            self.set_fail("Tus Negocios", "Missing list, add button, or business usage text.", final_url=page.url)

    def step_legal_link(
        self,
        app_page: Page,
        report_field: str,
        link_patterns: Tuple[str, ...],
        heading_pattern: str,
        screenshot_name: str,
    ) -> None:
        link = self.first_visible_locator(app_page, link_patterns)
        if not link:
            self.set_fail(report_field, f"Could not find legal link: {link_patterns[0]}.", final_url=app_page.url)
            return

        target_page = app_page
        opened_new_tab = False

        try:
            with app_page.context.expect_page(timeout=10000) as popup_info:
                link.click()
            target_page = popup_info.value
            opened_new_tab = True
            self.wait_ui(target_page)
        except TimeoutError:
            # Link navigated in the same tab.
            self.wait_ui(app_page)
            target_page = app_page

        heading_visible = self.is_visible_text(target_page, heading_pattern)
        legal_text_visible = target_page.locator("p,article,section").count() > 0
        screenshot = self.capture(target_page, screenshot_name, full_page=True)

        if heading_visible and legal_text_visible:
            self.set_pass(
                report_field,
                f"Legal page loaded correctly ({'new tab' if opened_new_tab else 'same tab'}).",
                screenshot=screenshot,
                final_url=target_page.url,
            )
        else:
            self.set_fail(
                report_field,
                f"Legal page validation failed ({'new tab' if opened_new_tab else 'same tab'}).",
                screenshot=screenshot,
                final_url=target_page.url,
            )

        # Return to application tab/page.
        if opened_new_tab:
            target_page.close()
            app_page.bring_to_front()
            self.wait_ui(app_page)
        else:
            try:
                app_page.go_back(wait_until="domcontentloaded", timeout=12000)
                self.wait_ui(app_page)
            except TimeoutError:
                pass

    def write_reports(self) -> None:
        self.output_dir.mkdir(parents=True, exist_ok=True)
        report_json = self.output_dir / "report.json"
        report_md = self.output_dir / "report.md"

        payload = {
            "name": "saleads_mi_negocio_full_test",
            "started_at": self.started_at,
            "finished_at": datetime.now(timezone.utc).isoformat(),
            "login_url": self.login_url,
            "results": {field: asdict(result) for field, result in self.results.items()},
        }
        report_json.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")

        lines = [
            "# SaleADS Mi Negocio Full Test Report",
            "",
            f"- Started at (UTC): `{payload['started_at']}`",
            f"- Finished at (UTC): `{payload['finished_at']}`",
            f"- Login URL: `{self.login_url}`",
            "",
            "| Field | Status | Details | Screenshot | Final URL |",
            "|---|---|---|---|---|",
        ]

        for field in REPORT_FIELDS:
            result = self.results[field]
            screenshot = result.screenshot or ""
            final_url = result.final_url or ""
            lines.append(
                f"| {field} | {result.status} | {result.details} | {screenshot} | {final_url} |"
            )

        report_md.write_text("\n".join(lines) + "\n", encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="SaleADS Mi Negocio full workflow validation.")
    parser.add_argument("--login-url", default=os.getenv("SALEADS_LOGIN_URL"), help="SaleADS login page URL.")
    parser.add_argument(
        "--google-email",
        default=os.getenv("SALEADS_GOOGLE_EMAIL", "juanlucasbarbiergarzon@gmail.com"),
        help="Google account to select if account chooser appears.",
    )
    parser.add_argument(
        "--output-dir",
        default=os.getenv("SALEADS_OUTPUT_DIR"),
        help="Directory for screenshots and reports. Defaults to /tmp/saleads-mi-negocio-<timestamp>.",
    )
    parser.add_argument(
        "--headed",
        action="store_true",
        help="Run browser in headed mode.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    output_dir = Path(args.output_dir) if args.output_dir else Path(f"/tmp/saleads-mi-negocio-{timestamp}")

    runner = SaleadsMiNegocioWorkflowRunner(
        login_url=args.login_url,
        google_email=args.google_email,
        output_dir=output_dir,
        headless=not args.headed,
    )
    exit_code = runner.run()
    print(f"SaleADS report: {output_dir / 'report.md'}")
    print(f"SaleADS json report: {output_dir / 'report.json'}")
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
