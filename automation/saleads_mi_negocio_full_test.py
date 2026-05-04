#!/usr/bin/env python3
"""SaleADS Mi Negocio full workflow automation."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import unicodedata
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional, Pattern

from playwright.sync_api import BrowserContext, Locator, Page, TimeoutError, sync_playwright


TEST_NAME = "saleads_mi_negocio_full_test"
DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com"

REPORT_FIELDS = [
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

ACCENT_MAP = {
    "a": "[aáàäâã]",
    "e": "[eéèëê]",
    "i": "[iíìïî]",
    "o": "[oóòöôõ]",
    "u": "[uúùüû]",
    "n": "[nñ]",
}


@dataclass
class StepResult:
    status: str
    details: str


def fuzzy_text_pattern(text: str, exact: bool = False) -> Pattern[str]:
    chunks: List[str] = []
    for char in text:
        lower = char.lower()
        if char.isspace():
            chunks.append(r"\s+")
        elif lower in ACCENT_MAP:
            chunks.append(ACCENT_MAP[lower])
        else:
            chunks.append(re.escape(char))
    body = "".join(chunks)
    if exact:
        body = rf"^\s*{body}\s*$"
    return re.compile(body, re.IGNORECASE)


def normalize_text(text: str) -> str:
    decomposed = unicodedata.normalize("NFD", text)
    filtered = "".join(ch for ch in decomposed if unicodedata.category(ch) != "Mn")
    return filtered.lower()


class SaleAdsMiNegocioWorkflow:
    def __init__(
        self,
        login_url: Optional[str],
        headless: bool,
        timeout_ms: int,
        google_email: str,
        artifacts_dir: Path,
    ) -> None:
        self.login_url = login_url
        self.headless = headless
        self.timeout_ms = timeout_ms
        self.google_email = google_email
        self.artifacts_dir = artifacts_dir
        self.screenshots_dir = artifacts_dir / "screenshots"
        self.screenshots_dir.mkdir(parents=True, exist_ok=True)

        self.results: Dict[str, StepResult] = {
            field: StepResult(status="FAIL", details="Not executed.")
            for field in REPORT_FIELDS
        }
        self.evidence_urls: Dict[str, str] = {}

    def run(self) -> int:
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=self.headless)
            context = browser.new_context(ignore_https_errors=True)
            page = context.new_page()
            page.set_default_timeout(self.timeout_ms)

            try:
                if self.login_url:
                    page.goto(self.login_url, wait_until="domcontentloaded")
                    self.wait_for_ui(page)
                elif page.url in ("", "about:blank"):
                    raise RuntimeError(
                        "Missing SALEADS_LOGIN_URL (or --login-url). "
                        "The run starts from a blank browser and cannot open the login page."
                    )

                login_ok = self.step_login(page, context)
                if not login_ok:
                    self.mark_prerequisite_failed(
                        [
                            "Mi Negocio menu",
                            "Agregar Negocio modal",
                            "Administrar Negocios view",
                            "Información General",
                            "Detalles de la Cuenta",
                            "Tus Negocios",
                            "Términos y Condiciones",
                            "Política de Privacidad",
                        ],
                        "Login did not complete successfully.",
                    )
                    return 1

                step2_ok = self.step_open_mi_negocio_menu(page)
                step3_ok = self.step_validate_agregar_negocio_modal(page) if step2_ok else False
                step4_ok = self.step_open_administrar_negocios(page)

                if step4_ok:
                    self.step_validate_informacion_general(page)
                    self.step_validate_detalles_cuenta(page)
                    self.step_validate_tus_negocios(page)
                    self.step_validate_legal_link(
                        page=page,
                        context=context,
                        link_text="Términos y Condiciones",
                        heading_text="Términos y Condiciones",
                        report_field="Términos y Condiciones",
                        screenshot_name="08_terminos_y_condiciones",
                    )
                    self.step_validate_legal_link(
                        page=page,
                        context=context,
                        link_text="Política de Privacidad",
                        heading_text="Política de Privacidad",
                        report_field="Política de Privacidad",
                        screenshot_name="09_politica_de_privacidad",
                    )
                else:
                    self.mark_prerequisite_failed(
                        [
                            "Información General",
                            "Detalles de la Cuenta",
                            "Tus Negocios",
                            "Términos y Condiciones",
                            "Política de Privacidad",
                        ],
                        "Administrar Negocios view prerequisite failed.",
                    )

                if step2_ok and not step3_ok:
                    self.results["Agregar Negocio modal"] = StepResult(
                        status="FAIL",
                        details=self.results["Agregar Negocio modal"].details,
                    )

            except Exception as exc:
                self.results["Login"] = StepResult(status="FAIL", details=str(exc))
                self.mark_prerequisite_failed(
                    [
                        "Mi Negocio menu",
                        "Agregar Negocio modal",
                        "Administrar Negocios view",
                        "Información General",
                        "Detalles de la Cuenta",
                        "Tus Negocios",
                        "Términos y Condiciones",
                        "Política de Privacidad",
                    ],
                    f"Global prerequisite failed: {exc}",
                )
            finally:
                try:
                    self.capture_screenshot(page, "final_page_state", full_page=True)
                except Exception:
                    pass
                browser.close()

        return 0 if self.all_passed() else 1

    def all_passed(self) -> bool:
        return all(result.status == "PASS" for result in self.results.values())

    def mark_prerequisite_failed(self, fields: List[str], reason: str) -> None:
        for field in fields:
            current = self.results[field]
            if current.details == "Not executed.":
                self.results[field] = StepResult(
                    status="FAIL",
                    details=f"Prerequisite failed: {reason}",
                )

    def wait_for_ui(self, page: Page) -> None:
        for state in ("domcontentloaded", "networkidle"):
            try:
                page.wait_for_load_state(state=state, timeout=self.timeout_ms)
            except TimeoutError:
                continue
        page.wait_for_timeout(700)

    def build_text_locators(self, page: Page, text: str) -> List[Locator]:
        exact_pattern = fuzzy_text_pattern(text, exact=True)
        loose_pattern = fuzzy_text_pattern(text, exact=False)
        return [
            page.get_by_role("button", name=exact_pattern),
            page.get_by_role("link", name=exact_pattern),
            page.get_by_role("menuitem", name=exact_pattern),
            page.get_by_role("tab", name=exact_pattern),
            page.get_by_role("option", name=loose_pattern),
            page.get_by_role("listitem", name=loose_pattern),
            page.get_by_role("button", name=loose_pattern),
            page.get_by_role("link", name=loose_pattern),
            page.get_by_text(exact_pattern),
            page.get_by_text(loose_pattern),
        ]

    def first_visible(self, locators: List[Locator], timeout_ms: int = 2200) -> Optional[Locator]:
        for locator in locators:
            try:
                locator.first.wait_for(state="visible", timeout=timeout_ms)
                return locator.first
            except TimeoutError:
                continue
        return None

    def click_visible_text(self, page: Page, text: str) -> bool:
        locator = self.first_visible(self.build_text_locators(page, text))
        if not locator:
            return False
        locator.scroll_into_view_if_needed(timeout=self.timeout_ms)
        locator.click()
        self.wait_for_ui(page)
        return True

    def text_visible(self, page: Page, text: str, timeout_ms: Optional[int] = None) -> bool:
        timeout = timeout_ms or self.timeout_ms
        pattern = fuzzy_text_pattern(text)
        try:
            page.get_by_text(pattern).first.wait_for(state="visible", timeout=timeout)
            return True
        except TimeoutError:
            return False

    def any_text_visible(self, page: Page, texts: List[str], timeout_ms: Optional[int] = None) -> bool:
        return any(self.text_visible(page, value, timeout_ms=timeout_ms) for value in texts)

    def capture_screenshot(self, page: Page, label: str, full_page: bool = False) -> str:
        safe_name = re.sub(r"[^a-zA-Z0-9._-]+", "_", label).strip("_").lower()
        file_path = self.screenshots_dir / f"{safe_name}.png"
        page.screenshot(path=str(file_path), full_page=full_page)
        return str(file_path)

    def get_new_page(
        self,
        context: BrowserContext,
        known_pages: List[Page],
        timeout_ms: int = 9000,
    ) -> Optional[Page]:
        end_at = datetime.now(timezone.utc).timestamp() + (timeout_ms / 1000.0)
        while datetime.now(timezone.utc).timestamp() < end_at:
            for current in context.pages:
                if current not in known_pages:
                    return current
            known_pages = list(context.pages)
            context.pages[0].wait_for_timeout(250)
        return None

    def set_result(self, field: str, passed: bool, details: str) -> bool:
        self.results[field] = StepResult(status="PASS" if passed else "FAIL", details=details)
        return passed

    def ensure_sidebar_visible(self, page: Page) -> bool:
        sidebar_signals = ["Negocio", "Mi Negocio", "Dashboard", "Inicio", "Panel"]
        return any(self.text_visible(page, signal, timeout_ms=3500) for signal in sidebar_signals)

    def ensure_main_interface(self, page: Page) -> bool:
        app_signals = ["Negocio", "Mi Negocio", "Administrar Negocios", "Información General"]
        return any(self.text_visible(page, signal, timeout_ms=4500) for signal in app_signals)

    def step_login(self, page: Page, context: BrowserContext) -> bool:
        field = "Login"
        try:
            if self.ensure_main_interface(page) and self.ensure_sidebar_visible(page):
                self.capture_screenshot(page, "01_dashboard_loaded")
                return self.set_result(field, True, "Session already authenticated.")

            login_button_texts = [
                "Sign in with Google",
                "Iniciar sesión con Google",
                "Iniciar sesion con Google",
                "Continuar con Google",
                "Google",
            ]

            clicked = False
            known_pages = list(context.pages)
            for text in login_button_texts:
                if self.click_visible_text(page, text):
                    clicked = True
                    break

            if not clicked:
                if self.ensure_main_interface(page) and self.ensure_sidebar_visible(page):
                    self.capture_screenshot(page, "01_dashboard_loaded")
                    return self.set_result(
                        field,
                        True,
                        "Main interface visible; login button not required.",
                    )
                return self.set_result(field, False, "Google login button was not found.")

            popup = self.get_new_page(context, known_pages)
            if popup:
                popup.set_default_timeout(self.timeout_ms)
                self.wait_for_ui(popup)
                account_locator = self.first_visible(
                    self.build_text_locators(popup, self.google_email),
                    timeout_ms=7000,
                )
                if account_locator:
                    account_locator.click()
                    self.wait_for_ui(popup)

                try:
                    popup.wait_for_event("close", timeout=10000)
                except TimeoutError:
                    if not popup.is_closed():
                        try:
                            popup.close()
                        except Exception:
                            pass

            self.wait_for_ui(page)
            main_visible = self.ensure_main_interface(page)
            sidebar_visible = self.ensure_sidebar_visible(page)
            self.capture_screenshot(page, "01_dashboard_loaded")
            return self.set_result(
                field,
                main_visible and sidebar_visible,
                (
                    "Main interface and left sidebar are visible."
                    if main_visible and sidebar_visible
                    else "Main interface/sidebar could not be confirmed after Google login."
                ),
            )
        except Exception as exc:
            return self.set_result(field, False, f"Login step failed: {exc}")

    def step_open_mi_negocio_menu(self, page: Page) -> bool:
        field = "Mi Negocio menu"
        try:
            _ = self.click_visible_text(page, "Negocio")
            clicked_mi_negocio = self.click_visible_text(page, "Mi Negocio")
            if not clicked_mi_negocio:
                return self.set_result(field, False, "Could not click 'Mi Negocio'.")

            agregar_visible = self.text_visible(page, "Agregar Negocio", timeout_ms=5000)
            administrar_visible = self.text_visible(page, "Administrar Negocios", timeout_ms=5000)
            self.capture_screenshot(page, "02_mi_negocio_menu_expanded")
            return self.set_result(
                field,
                agregar_visible and administrar_visible,
                (
                    "Mi Negocio menu expanded and submenu options are visible."
                    if agregar_visible and administrar_visible
                    else "Menu expanded state or submenu options could not be confirmed."
                ),
            )
        except Exception as exc:
            return self.set_result(field, False, f"Mi Negocio menu step failed: {exc}")

    def step_validate_agregar_negocio_modal(self, page: Page) -> bool:
        field = "Agregar Negocio modal"
        try:
            if not self.click_visible_text(page, "Agregar Negocio"):
                return self.set_result(field, False, "Could not click 'Agregar Negocio'.")

            checks = {
                "Crear Nuevo Negocio": self.text_visible(page, "Crear Nuevo Negocio", timeout_ms=6000),
                "Nombre del Negocio": self.text_visible(page, "Nombre del Negocio", timeout_ms=6000),
                "Tienes 2 de 3 negocios": self.text_visible(page, "Tienes 2 de 3 negocios", timeout_ms=6000),
                "Cancelar": self.text_visible(page, "Cancelar", timeout_ms=6000),
                "Crear Negocio": self.text_visible(page, "Crear Negocio", timeout_ms=6000),
            }

            # Optional action requested in the workflow.
            input_locator = self.first_visible(
                [
                    page.get_by_label(fuzzy_text_pattern("Nombre del Negocio")),
                    page.locator("input[placeholder*='Negocio' i]"),
                    page.locator("input[name*='negocio' i]"),
                ],
                timeout_ms=1800,
            )
            if input_locator:
                input_locator.click()
                input_locator.fill("Negocio Prueba Automatización")

            self.capture_screenshot(page, "03_agregar_negocio_modal")
            if not self.click_visible_text(page, "Cancelar"):
                page.keyboard.press("Escape")
                self.wait_for_ui(page)

            return self.set_result(
                field,
                all(checks.values()),
                (
                    "Agregar Negocio modal shows all required fields/buttons."
                    if all(checks.values())
                    else f"Missing modal validations: {[k for k, v in checks.items() if not v]}"
                ),
            )
        except Exception as exc:
            return self.set_result(field, False, f"Agregar Negocio modal step failed: {exc}")

    def step_open_administrar_negocios(self, page: Page) -> bool:
        field = "Administrar Negocios view"
        try:
            if not self.text_visible(page, "Administrar Negocios", timeout_ms=2500):
                _ = self.click_visible_text(page, "Mi Negocio")
            if not self.click_visible_text(page, "Administrar Negocios"):
                return self.set_result(field, False, "Could not click 'Administrar Negocios'.")

            checks = {
                "Información General": self.any_text_visible(
                    page,
                    ["Información General", "Informacion General"],
                    timeout_ms=9000,
                ),
                "Detalles de la Cuenta": self.text_visible(page, "Detalles de la Cuenta", timeout_ms=9000),
                "Tus Negocios": self.text_visible(page, "Tus Negocios", timeout_ms=9000),
                "Sección Legal": self.any_text_visible(
                    page,
                    ["Sección Legal", "Seccion Legal"],
                    timeout_ms=9000,
                ),
            }

            self.capture_screenshot(page, "04_administrar_negocios_view", full_page=True)
            return self.set_result(
                field,
                all(checks.values()),
                (
                    "Administrar Negocios page loaded with all expected sections."
                    if all(checks.values())
                    else f"Missing sections: {[k for k, v in checks.items() if not v]}"
                ),
            )
        except Exception as exc:
            return self.set_result(field, False, f"Administrar Negocios step failed: {exc}")

    def step_validate_informacion_general(self, page: Page) -> bool:
        field = "Información General"
        try:
            body_text = page.locator("body").inner_text(timeout=self.timeout_ms)
            normalized_body = normalize_text(body_text)
            email_visible = bool(re.search(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", body_text))
            name_hint_visible = (
                self.any_text_visible(page, ["Nombre", "Usuario"], timeout_ms=3000)
                or "bienvenido" in normalized_body
            )
            business_plan_visible = self.text_visible(page, "BUSINESS PLAN", timeout_ms=3000)
            cambiar_plan_visible = self.text_visible(page, "Cambiar Plan", timeout_ms=3000)

            passed = all(
                [email_visible, name_hint_visible, business_plan_visible, cambiar_plan_visible]
            )
            return self.set_result(
                field,
                passed,
                (
                    "Información General fields are visible."
                    if passed
                    else "Could not confirm name/email/plan details in Información General."
                ),
            )
        except Exception as exc:
            return self.set_result(field, False, f"Información General validation failed: {exc}")

    def step_validate_detalles_cuenta(self, page: Page) -> bool:
        field = "Detalles de la Cuenta"
        try:
            checks = [
                self.text_visible(page, "Cuenta creada", timeout_ms=3500),
                self.text_visible(page, "Estado activo", timeout_ms=3500),
                self.text_visible(page, "Idioma seleccionado", timeout_ms=3500),
            ]
            passed = all(checks)
            return self.set_result(
                field,
                passed,
                (
                    "Detalles de la Cuenta section is valid."
                    if passed
                    else "One or more required account details are missing."
                ),
            )
        except Exception as exc:
            return self.set_result(field, False, f"Detalles de la Cuenta validation failed: {exc}")

    def step_validate_tus_negocios(self, page: Page) -> bool:
        field = "Tus Negocios"
        try:
            tus_negocios_visible = self.text_visible(page, "Tus Negocios", timeout_ms=3500)
            agregar_visible = self.text_visible(page, "Agregar Negocio", timeout_ms=3500)
            quota_visible = self.text_visible(page, "Tienes 2 de 3 negocios", timeout_ms=3500)

            passed = tus_negocios_visible and agregar_visible and quota_visible
            return self.set_result(
                field,
                passed,
                (
                    "Tus Negocios list and controls are visible."
                    if passed
                    else "Could not validate business list section details."
                ),
            )
        except Exception as exc:
            return self.set_result(field, False, f"Tus Negocios validation failed: {exc}")

    def step_validate_legal_link(
        self,
        page: Page,
        context: BrowserContext,
        link_text: str,
        heading_text: str,
        report_field: str,
        screenshot_name: str,
    ) -> bool:
        initial_url = page.url
        known_pages = list(context.pages)
        try:
            clicked = self.click_visible_text(page, link_text)
            if not clicked:
                return self.set_result(report_field, False, f"Could not click '{link_text}'.")

            popup = self.get_new_page(context, known_pages, timeout_ms=7000)
            target_page = popup if popup else page
            self.wait_for_ui(target_page)

            heading_visible = self.text_visible(target_page, heading_text, timeout_ms=10000)
            body_text = target_page.locator("body").inner_text(timeout=self.timeout_ms)
            legal_content_visible = len(body_text.split()) > 40

            self.capture_screenshot(target_page, screenshot_name, full_page=True)
            self.evidence_urls[report_field] = target_page.url

            if popup:
                popup.close()
                page.bring_to_front()
                self.wait_for_ui(page)
            elif page.url != initial_url:
                page.go_back(wait_until="domcontentloaded")
                self.wait_for_ui(page)

            passed = heading_visible and legal_content_visible
            return self.set_result(
                report_field,
                passed,
                (
                    f"Legal page '{heading_text}' validated. URL: {self.evidence_urls[report_field]}"
                    if passed
                    else f"Failed to validate heading/content for '{heading_text}'."
                ),
            )
        except Exception as exc:
            return self.set_result(report_field, False, f"{link_text} validation failed: {exc}")

    def write_report(self) -> Path:
        report_path = self.artifacts_dir / "report.json"
        summary = {
            "name": TEST_NAME,
            "executed_at_utc": datetime.now(timezone.utc).isoformat(),
            "overall_status": "PASS" if self.all_passed() else "FAIL",
            "artifacts_dir": str(self.artifacts_dir),
            "screenshots_dir": str(self.screenshots_dir),
            "results": {
                field: {"status": result.status, "details": result.details}
                for field, result in self.results.items()
            },
            "final_urls": self.evidence_urls,
        }
        report_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")

        latest_path = self.artifacts_dir.parent / "latest_report.json"
        latest_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")
        return report_path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="SaleADS Mi Negocio full workflow test.")
    parser.add_argument(
        "--login-url",
        default=os.getenv("SALEADS_LOGIN_URL"),
        help="SaleADS login URL for the current environment.",
    )
    parser.add_argument(
        "--headless",
        default=os.getenv("HEADLESS", "true").lower() != "false",
        action=argparse.BooleanOptionalAction,
        help="Run browser in headless mode (default: true).",
    )
    parser.add_argument(
        "--timeout-ms",
        type=int,
        default=int(os.getenv("SALEADS_TIMEOUT_MS", "20000")),
        help="Playwright default timeout in milliseconds.",
    )
    parser.add_argument(
        "--google-email",
        default=os.getenv("SALEADS_GOOGLE_ACCOUNT_EMAIL", DEFAULT_GOOGLE_EMAIL),
        help="Google account email to select when account chooser appears.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    artifacts_dir = Path(__file__).resolve().parent / "artifacts" / run_id
    artifacts_dir.mkdir(parents=True, exist_ok=True)

    workflow = SaleAdsMiNegocioWorkflow(
        login_url=args.login_url,
        headless=args.headless,
        timeout_ms=args.timeout_ms,
        google_email=args.google_email,
        artifacts_dir=artifacts_dir,
    )
    exit_code = workflow.run()
    report_path = workflow.write_report()

    report_content = json.loads(report_path.read_text(encoding="utf-8"))
    print(json.dumps(report_content, indent=2))
    return exit_code


if __name__ == "__main__":
    sys.exit(main())
