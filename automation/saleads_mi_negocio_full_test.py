#!/usr/bin/env python3
import json
import os
import re
import sys
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Callable

from playwright.sync_api import Locator
from playwright.sync_api import Page
from playwright.sync_api import TimeoutError as PlaywrightTimeoutError
from playwright.sync_api import sync_playwright


def env_bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def timestamp_slug() -> str:
    return datetime.utcnow().strftime("%Y%m%dT%H%M%SZ")


@dataclass
class StepResult:
    status: str
    details: str
    evidence: list[str] = field(default_factory=list)


class SaleadsMiNegocioWorkflow:
    def __init__(self) -> None:
        self.login_url = os.getenv("SALEADS_LOGIN_URL")
        self.cdp_url = os.getenv("SALEADS_CDP_URL")
        self.google_email = os.getenv(
            "SALEADS_GOOGLE_EMAIL", "juanlucasbarbiergarzon@gmail.com"
        )
        self.timeout_ms = int(os.getenv("SALEADS_TIMEOUT_MS", "20000"))
        self.headless = env_bool("SALEADS_HEADLESS", False)

        base_artifacts = Path(os.getenv("SALEADS_ARTIFACTS_DIR", "artifacts"))
        self.run_dir = base_artifacts / f"saleads_mi_negocio_{timestamp_slug()}"
        self.screenshots_dir = self.run_dir / "screenshots"
        self.run_dir.mkdir(parents=True, exist_ok=True)
        self.screenshots_dir.mkdir(parents=True, exist_ok=True)

        self.results: dict[str, StepResult] = {}
        self.legal_urls: dict[str, str] = {}

    def run(self) -> int:
        try:
            with sync_playwright() as playwright:
                if self.cdp_url:
                    browser = playwright.chromium.connect_over_cdp(self.cdp_url)
                    context = browser.contexts[0] if browser.contexts else browser.new_context()
                    page = context.pages[0] if context.pages else context.new_page()
                    page.set_default_timeout(self.timeout_ms)
                    self.execute_workflow(page, auto_navigate=False)
                    browser.close()
                else:
                    if not self.login_url:
                        self.results["Login"] = StepResult(
                            status="FAIL",
                            details=(
                                "Provide SALEADS_LOGIN_URL or SALEADS_CDP_URL "
                                "to start the workflow."
                            ),
                        )
                        self.mark_remaining_failed("Missing login URL/CDP configuration")
                    else:
                        browser = playwright.chromium.launch(headless=self.headless)
                        context = browser.new_context(
                            viewport={"width": 1600, "height": 1000},
                            ignore_https_errors=True,
                        )
                        page = context.new_page()
                        page.set_default_timeout(self.timeout_ms)
                        self.execute_workflow(page, auto_navigate=True)
                        browser.close()
        except Exception as exc:  # pylint: disable=broad-except
            self.results["Framework"] = StepResult(
                status="FAIL",
                details=f"Unexpected framework error: {exc}",
            )

        self.write_report()
        self.print_summary()
        return 0 if self.all_passed() else 1

    def execute_workflow(self, page: Page, auto_navigate: bool) -> None:
        if auto_navigate:
            page.goto(self.login_url, wait_until="domcontentloaded")
            self.wait_for_ui(page)
        elif self.login_url and page.url in {"about:blank", "chrome://new-tab-page/"}:
            page.goto(self.login_url, wait_until="domcontentloaded")
            self.wait_for_ui(page)

        if not self.run_step(
            "Login",
            lambda: self.step_login_with_google(page),
        ):
            self.mark_remaining_failed("Login step failed")
            return

        self.run_step("Mi Negocio menu", lambda: self.step_open_mi_negocio_menu(page))
        self.run_step(
            "Agregar Negocio modal", lambda: self.step_validate_agregar_negocio_modal(page)
        )
        self.run_step(
            "Administrar Negocios view",
            lambda: self.step_open_administrar_negocios(page),
        )
        self.run_step(
            "Información General", lambda: self.step_validate_informacion_general(page)
        )
        self.run_step(
            "Detalles de la Cuenta", lambda: self.step_validate_detalles_cuenta(page)
        )
        self.run_step("Tus Negocios", lambda: self.step_validate_tus_negocios(page))
        self.run_step(
            "Términos y Condiciones",
            lambda: self.step_validate_legal_link(
                page, "Términos y Condiciones", "Terminos_y_Condiciones"
            ),
        )
        self.run_step(
            "Política de Privacidad",
            lambda: self.step_validate_legal_link(
                page, "Política de Privacidad", "Politica_de_Privacidad"
            ),
        )

    def run_step(self, name: str, fn: Callable[[], StepResult]) -> bool:
        try:
            result = fn()
            self.results[name] = result
            return result.status == "PASS"
        except Exception as exc:  # pylint: disable=broad-except
            self.results[name] = StepResult(status="FAIL", details=str(exc))
            return False

    def step_login_with_google(self, page: Page) -> StepResult:
        login_trigger = self.get_clickable_by_text(
            page,
            [
                "Sign in with Google",
                "Iniciar sesión con Google",
                "Continuar con Google",
                "Login con Google",
                "Acceder con Google",
                "Google",
            ],
        )
        if login_trigger is None:
            raise AssertionError("Google login button was not found.")

        login_trigger.click()
        self.wait_for_ui(page)

        account_locator = self.get_visible_by_text(page, [self.google_email])
        if account_locator is not None:
            account_locator.click()
            self.wait_for_ui(page)

        self.require_visible(page, ["Negocio"], "Sidebar section 'Negocio' not found.")
        self.require_visible(
            page,
            ["Mi Negocio"],
            "Main app interface did not display 'Mi Negocio'.",
        )

        screenshot = self.capture("01_dashboard_loaded", page, full_page=True)
        return StepResult(
            status="PASS",
            details="Main interface and left sidebar are visible after Google login.",
            evidence=[screenshot],
        )

    def step_open_mi_negocio_menu(self, page: Page) -> StepResult:
        self.open_mi_negocio_menu(page)
        self.require_visible(page, ["Agregar Negocio"], "'Agregar Negocio' is not visible.")
        self.require_visible(
            page,
            ["Administrar Negocios"],
            "'Administrar Negocios' is not visible.",
        )

        screenshot = self.capture("02_mi_negocio_menu_expanded", page, full_page=False)
        return StepResult(
            status="PASS",
            details=(
                "Mi Negocio menu expanded successfully with "
                "'Agregar Negocio' and 'Administrar Negocios'."
            ),
            evidence=[screenshot],
        )

    def step_validate_agregar_negocio_modal(self, page: Page) -> StepResult:
        agregar = self.get_clickable_by_text(page, ["Agregar Negocio"])
        if agregar is None:
            raise AssertionError("'Agregar Negocio' option was not found.")
        agregar.click()
        self.wait_for_ui(page)

        self.require_visible(page, ["Crear Nuevo Negocio"], "Modal title not found.")
        self.require_visible(page, ["Nombre del Negocio"], "Input label not found.")
        self.require_visible(
            page,
            ["Tienes 2 de 3 negocios"],
            "Capacity text 'Tienes 2 de 3 negocios' is not visible.",
        )
        self.require_visible(page, ["Cancelar"], "'Cancelar' button is missing.")
        self.require_visible(page, ["Crear Negocio"], "'Crear Negocio' button is missing.")

        input_field = self.get_input_by_label_or_placeholder(
            page, ["Nombre del Negocio", "Nombre"]
        )
        if input_field is not None:
            input_field.click()
            input_field.fill("Negocio Prueba Automatización")
            self.wait_for_ui(page)

        screenshot = self.capture("03_agregar_negocio_modal", page, full_page=False)

        cancelar = self.get_clickable_by_text(page, ["Cancelar"])
        if cancelar is not None:
            cancelar.click()
            self.wait_for_ui(page)

        return StepResult(
            status="PASS",
            details="Agregar Negocio modal validations completed successfully.",
            evidence=[screenshot],
        )

    def step_open_administrar_negocios(self, page: Page) -> StepResult:
        self.open_mi_negocio_menu(page)

        administrar = self.get_clickable_by_text(page, ["Administrar Negocios"])
        if administrar is None:
            raise AssertionError("'Administrar Negocios' option was not found.")
        administrar.click()
        self.wait_for_ui(page)

        self.require_visible(page, ["Información General"], "Section missing: Información General.")
        self.require_visible(page, ["Detalles de la Cuenta"], "Section missing: Detalles de la Cuenta.")
        self.require_visible(page, ["Tus Negocios"], "Section missing: Tus Negocios.")
        self.require_visible(page, ["Sección Legal"], "Section missing: Sección Legal.")

        screenshot = self.capture("04_administrar_negocios_page", page, full_page=True)
        return StepResult(
            status="PASS",
            details="Administrar Negocios account page loaded with all required sections.",
            evidence=[screenshot],
        )

    def step_validate_informacion_general(self, page: Page) -> StepResult:
        self.require_visible(
            page,
            ["@", ".com", ".net", ".org"],
            "User email was not identified in Información General.",
        )
        self.require_visible(page, ["BUSINESS PLAN"], "Text 'BUSINESS PLAN' is not visible.")
        self.require_visible(page, ["Cambiar Plan"], "Button 'Cambiar Plan' is not visible.")

        user_name_candidate = self.find_user_name_candidate(page)
        if not user_name_candidate:
            raise AssertionError("User name is not clearly visible in Información General.")

        return StepResult(
            status="PASS",
            details="Información General section shows user name, email, plan and action button.",
        )

    def step_validate_detalles_cuenta(self, page: Page) -> StepResult:
        self.require_visible(page, ["Cuenta creada"], "'Cuenta creada' is not visible.")
        self.require_visible(page, ["Estado activo"], "'Estado activo' is not visible.")
        self.require_visible(
            page,
            ["Idioma seleccionado"],
            "'Idioma seleccionado' is not visible.",
        )
        return StepResult(
            status="PASS",
            details="Detalles de la Cuenta section contains all expected fields.",
        )

    def step_validate_tus_negocios(self, page: Page) -> StepResult:
        self.require_visible(page, ["Tus Negocios"], "Section title 'Tus Negocios' is not visible.")
        self.require_visible(page, ["Agregar Negocio"], "Button 'Agregar Negocio' is missing.")
        self.require_visible(
            page,
            ["Tienes 2 de 3 negocios"],
            "Text 'Tienes 2 de 3 negocios' is not visible in Tus Negocios.",
        )
        if self.count_business_candidates(page) < 1:
            raise AssertionError("Business list was not detected in 'Tus Negocios'.")

        return StepResult(
            status="PASS",
            details="Tus Negocios section displays list, add button and limit text.",
        )

    def step_validate_legal_link(
        self,
        page: Page,
        link_text: str,
        evidence_name: str,
    ) -> StepResult:
        target = self.get_clickable_by_text(page, [link_text])
        if target is None:
            raise AssertionError(f"Legal link '{link_text}' was not found.")

        legal_page = page
        opened_new_tab = False
        try:
            with page.context.expect_page(timeout=5000) as new_page_info:
                target.click()
            legal_page = new_page_info.value
            legal_page.wait_for_load_state("domcontentloaded")
            opened_new_tab = True
        except PlaywrightTimeoutError:
            target.click()
            self.wait_for_ui(page)
            legal_page = page

        heading = self.get_visible_by_text(legal_page, [link_text])
        if heading is None:
            raise AssertionError(f"Heading '{link_text}' is not visible on legal page.")

        if not self.has_long_text_content(legal_page):
            raise AssertionError("Legal content text was not detected.")

        screenshot = self.capture(f"05_{evidence_name}", legal_page, full_page=True)
        final_url = legal_page.url
        self.legal_urls[link_text] = final_url

        if opened_new_tab:
            legal_page.close()
            page.bring_to_front()
            self.wait_for_ui(page)

        return StepResult(
            status="PASS",
            details=f"Validated '{link_text}' legal page.",
            evidence=[screenshot, f"URL: {final_url}"],
        )

    def open_mi_negocio_menu(self, page: Page) -> None:
        menu = self.get_clickable_by_text(page, ["Mi Negocio"])
        if menu is None:
            raise AssertionError("'Mi Negocio' menu item was not found.")

        if self.get_visible_by_text(page, ["Agregar Negocio"]) is None:
            menu.click()
            self.wait_for_ui(page)

    def get_clickable_by_text(self, page: Page, labels: list[str]) -> Locator | None:
        for label in labels:
            regex = re.compile(rf"^\s*{re.escape(label)}\s*$", re.IGNORECASE)
            locator_sets = [
                page.get_by_role("button", name=regex),
                page.get_by_role("link", name=regex),
                page.get_by_role("menuitem", name=regex),
                page.get_by_text(regex),
            ]
            for locator in locator_sets:
                candidate = locator.first
                try:
                    if candidate.is_visible(timeout=1500):
                        return candidate
                except PlaywrightTimeoutError:
                    continue
        return None

    def get_visible_by_text(self, page: Page, labels: list[str]) -> Locator | None:
        for label in labels:
            regex = re.compile(re.escape(label), re.IGNORECASE)
            candidate = page.get_by_text(regex).first
            try:
                if candidate.is_visible(timeout=1500):
                    return candidate
            except PlaywrightTimeoutError:
                continue
        return None

    def get_input_by_label_or_placeholder(
        self, page: Page, names: list[str]
    ) -> Locator | None:
        for name in names:
            regex = re.compile(re.escape(name), re.IGNORECASE)
            for locator in [
                page.get_by_label(regex),
                page.get_by_placeholder(regex),
            ]:
                candidate = locator.first
                try:
                    if candidate.is_visible(timeout=1500):
                        return candidate
                except PlaywrightTimeoutError:
                    continue
        return None

    def require_visible(self, page: Page, labels: list[str], fail_message: str) -> None:
        if self.get_visible_by_text(page, labels) is None:
            raise AssertionError(fail_message)

    def count_business_candidates(self, page: Page) -> int:
        candidates = [
            page.locator("table tbody tr"),
            page.locator("[data-testid*=business]"),
            page.locator("ul li"),
            page.locator("[role='row']"),
        ]
        for locator in candidates:
            try:
                count = locator.count()
                if count > 0:
                    return count
            except Exception:  # pylint: disable=broad-except
                continue
        return 0

    def find_user_name_candidate(self, page: Page) -> bool:
        likely_name = page.locator("h1, h2, h3, strong, b, [class*=name]")
        try:
            count = likely_name.count()
        except Exception:  # pylint: disable=broad-except
            return False

        for idx in range(min(count, 25)):
            text = likely_name.nth(idx).inner_text().strip()
            if (
                text
                and "@" not in text
                and len(text.split()) >= 2
                and len(text) <= 50
                and not re.search(r"informaci|detalles|negocio|legal", text, re.IGNORECASE)
            ):
                return True
        return False

    def has_long_text_content(self, page: Page) -> bool:
        text_blocks = page.locator("p, li, article, section")
        try:
            count = text_blocks.count()
        except Exception:  # pylint: disable=broad-except
            return False

        for idx in range(min(count, 60)):
            text = text_blocks.nth(idx).inner_text().strip()
            if len(text) >= 80:
                return True
        return False

    def capture(self, name: str, page: Page, full_page: bool) -> str:
        file_path = self.screenshots_dir / f"{name}.png"
        page.screenshot(path=str(file_path), full_page=full_page)
        return str(file_path)

    def wait_for_ui(self, page: Page) -> None:
        for state in ["domcontentloaded", "networkidle"]:
            try:
                page.wait_for_load_state(state, timeout=5000)
            except PlaywrightTimeoutError:
                continue
        page.wait_for_timeout(700)

    def mark_remaining_failed(self, reason: str) -> None:
        ordered_fields = [
            "Mi Negocio menu",
            "Agregar Negocio modal",
            "Administrar Negocios view",
            "Información General",
            "Detalles de la Cuenta",
            "Tus Negocios",
            "Términos y Condiciones",
            "Política de Privacidad",
        ]
        for field in ordered_fields:
            if field not in self.results:
                self.results[field] = StepResult(
                    status="FAIL",
                    details=f"Not executed: {reason}",
                )

    def all_passed(self) -> bool:
        expected_fields = [
            "Login",
            "Mi Negocio menu",
            "Agregar Negocio modal",
            "Administrar Negocios view",
            "Información General",
            "Detalles de la Cuenta",
            "Tus Negocios",
            "Términos y Condiciones",
            "Política de Privacidad",
        ]
        return all(self.results.get(field, StepResult("FAIL", "")).status == "PASS" for field in expected_fields)

    def write_report(self) -> None:
        ordered_fields = [
            "Login",
            "Mi Negocio menu",
            "Agregar Negocio modal",
            "Administrar Negocios view",
            "Información General",
            "Detalles de la Cuenta",
            "Tus Negocios",
            "Términos y Condiciones",
            "Política de Privacidad",
        ]

        ordered_report = {}
        for field in ordered_fields:
            result = self.results.get(
                field,
                StepResult(status="FAIL", details="No result was recorded."),
            )
            ordered_report[field] = {
                "status": result.status,
                "details": result.details,
                "evidence": result.evidence,
            }

        report = {
            "test_name": "saleads_mi_negocio_full_test",
            "generated_at_utc": datetime.utcnow().isoformat() + "Z",
            "login_url": self.login_url,
            "cdp_url_configured": bool(self.cdp_url),
            "google_email": self.google_email,
            "artifacts_dir": str(self.run_dir),
            "legal_urls": self.legal_urls,
            "final_status": "PASS" if self.all_passed() else "FAIL",
            "results": ordered_report,
        }

        report_file = self.run_dir / "final_report.json"
        report_file.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")

    def print_summary(self) -> None:
        ordered_fields = [
            "Login",
            "Mi Negocio menu",
            "Agregar Negocio modal",
            "Administrar Negocios view",
            "Información General",
            "Detalles de la Cuenta",
            "Tus Negocios",
            "Términos y Condiciones",
            "Política de Privacidad",
        ]
        print("==== SaleADS Mi Negocio Workflow Report ====")
        for field in ordered_fields:
            result = self.results.get(field, StepResult("FAIL", "No result was recorded."))
            print(f"{field}: {result.status} - {result.details}")
        print(f"Final status: {'PASS' if self.all_passed() else 'FAIL'}")
        print(f"Artifacts directory: {self.run_dir}")


def main() -> int:
    runner = SaleadsMiNegocioWorkflow()
    return runner.run()


if __name__ == "__main__":
    sys.exit(main())
