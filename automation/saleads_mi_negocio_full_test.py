#!/usr/bin/env python3
"""
saleads_mi_negocio_full_test

Environment-agnostic Playwright test for validating SaleADS "Mi Negocio" workflow.
It never hardcodes a domain and can run against any environment URL.
"""

from __future__ import annotations

import argparse
import json
import re
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable, Optional

from playwright.sync_api import BrowserContext, Error, Page, TimeoutError, sync_playwright


DEFAULT_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com"


@dataclass
class StepResult:
    name: str
    passed: bool
    details: str


class SaleadsMiNegocioWorkflow:
    def __init__(self, page: Page, context: BrowserContext, output_dir: Path, account_email: str) -> None:
        self.page = page
        self.context = context
        self.output_dir = output_dir
        self.account_email = account_email
        self.report: dict[str, StepResult] = {}
        self.legal_urls: dict[str, str] = {}
        self.app_page = page

    def run(self) -> dict[str, str]:
        self.output_dir.mkdir(parents=True, exist_ok=True)

        self._run_step("Login", self.step_login_with_google)
        self._run_step("Mi Negocio menu", self.step_open_mi_negocio_menu)
        self._run_step("Agregar Negocio modal", self.step_validate_agregar_negocio_modal)
        self._run_step("Administrar Negocios view", self.step_open_administrar_negocios)
        self._run_step("Información General", self.step_validate_informacion_general)
        self._run_step("Detalles de la Cuenta", self.step_validate_detalles_cuenta)
        self._run_step("Tus Negocios", self.step_validate_tus_negocios)
        self._run_step("Términos y Condiciones", self.step_validate_terminos_y_condiciones)
        self._run_step("Política de Privacidad", self.step_validate_politica_privacidad)

        final_report = {name: "PASS" if result.passed else "FAIL" for name, result in self.report.items()}
        report_payload = {
            "executed_at_utc": datetime.now(timezone.utc).isoformat(),
            "results": final_report,
            "details": {name: result.details for name, result in self.report.items()},
            "legal_urls": self.legal_urls,
        }
        report_path = self.output_dir / "final_report.json"
        report_path.write_text(json.dumps(report_payload, indent=2, ensure_ascii=False), encoding="utf-8")
        return final_report

    def _run_step(self, name: str, fn: Callable[[], str]) -> None:
        try:
            details = fn()
            self.report[name] = StepResult(name=name, passed=True, details=details)
        except Exception as exc:  # noqa: BLE001 - we need a full report even when one step fails.
            self.report[name] = StepResult(name=name, passed=False, details=str(exc))

    def wait_for_ui_load(self) -> None:
        self.page.wait_for_load_state("domcontentloaded")
        self.page.wait_for_load_state("networkidle")

    def screenshot(self, label: str, full_page: bool = False) -> str:
        timestamp = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
        path = self.output_dir / f"{timestamp}_{label}.png"
        self.page.screenshot(path=str(path), full_page=full_page)
        return str(path)

    def click_visible_text(self, text_options: list[str], timeout_ms: int = 15000) -> str:
        """
        Prefer visible text selectors and click the first matching option.
        """
        selectors = []
        for text in text_options:
            escaped = re.escape(text)
            selectors.extend(
                [
                    f"role=button[name=/{escaped}/i]",
                    f"role=link[name=/{escaped}/i]",
                    f"text=/{escaped}/i",
                ]
            )

        deadline = datetime.now().timestamp() + (timeout_ms / 1000.0)
        last_error: Optional[Exception] = None
        while datetime.now().timestamp() < deadline:
            for idx, selector in enumerate(selectors):
                try:
                    locator = self.page.locator(selector).first
                    if locator.is_visible(timeout=300):
                        locator.click()
                        self.wait_for_ui_load()
                        return text_options[idx // 3]
                except Exception as exc:  # noqa: BLE001
                    last_error = exc
            self.page.wait_for_timeout(300)

        if last_error:
            raise AssertionError(f"Unable to click any of {text_options}: {last_error}") from last_error
        raise AssertionError(f"Unable to click any of {text_options}")

    def assert_visible_text(self, expected_text: str, timeout_ms: int = 15000) -> None:
        escaped = re.escape(expected_text)
        locator = self.page.locator(f"text=/{escaped}/i").first
        locator.wait_for(state="visible", timeout=timeout_ms)

    def assert_any_visible_text(self, expected_texts: list[str], timeout_ms: int = 15000) -> str:
        last_error: Optional[Exception] = None
        for text in expected_texts:
            try:
                self.assert_visible_text(text, timeout_ms=timeout_ms)
                return text
            except Exception as exc:  # noqa: BLE001
                last_error = exc
        if last_error:
            raise AssertionError(f"None of texts are visible: {expected_texts}") from last_error
        raise AssertionError(f"None of texts are visible: {expected_texts}")

    def step_login_with_google(self) -> str:
        self.click_visible_text(["Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google"])

        # If Google account chooser appears, pick the required account.
        try:
            account_button = self.page.locator(f"text=/{re.escape(self.account_email)}/i").first
            if account_button.is_visible(timeout=8000):
                account_button.click()
                self.wait_for_ui_load()
        except Exception:
            # Some environments auto-login and do not show account chooser.
            pass

        self.assert_any_visible_text(["Negocio", "Dashboard", "Inicio", "Panel"], timeout_ms=30000)
        # Left sidebar validation.
        sidebar = self.page.locator("aside, nav").first
        if not sidebar.is_visible(timeout=10000):
            raise AssertionError("Main application sidebar is not visible after login.")

        self.screenshot("01_dashboard_loaded")
        self.app_page = self.page
        return "Dashboard loaded and sidebar visible."

    def step_open_mi_negocio_menu(self) -> str:
        self.click_visible_text(["Negocio"])
        self.click_visible_text(["Mi Negocio"])
        self.assert_visible_text("Agregar Negocio")
        self.assert_visible_text("Administrar Negocios")
        self.screenshot("02_mi_negocio_menu_expanded")
        return "Mi Negocio submenu expanded with expected options."

    def step_validate_agregar_negocio_modal(self) -> str:
        self.click_visible_text(["Agregar Negocio"])
        self.assert_visible_text("Crear Nuevo Negocio")
        self.assert_visible_text("Nombre del Negocio")
        self.assert_visible_text("Tienes 2 de 3 negocios")
        self.assert_visible_text("Cancelar")
        self.assert_visible_text("Crear Negocio")

        # Optional interaction requested by the flow.
        input_locator = self.page.get_by_label("Nombre del Negocio")
        if not input_locator.count():
            input_locator = self.page.locator("input[placeholder*='Nombre del Negocio' i]").first
        input_locator.fill("Negocio Prueba Automatización")

        self.screenshot("03_agregar_negocio_modal")
        self.click_visible_text(["Cancelar"])
        return "Agregar Negocio modal validated and closed."

    def step_open_administrar_negocios(self) -> str:
        # Re-expand if needed.
        if not self.page.locator("text=/Administrar Negocios/i").first.is_visible():
            try:
                self.click_visible_text(["Mi Negocio"])
            except Exception:
                self.click_visible_text(["Negocio"])
                self.click_visible_text(["Mi Negocio"])

        self.click_visible_text(["Administrar Negocios"])
        self.assert_visible_text("Información General", timeout_ms=30000)
        self.assert_visible_text("Detalles de la Cuenta")
        self.assert_visible_text("Tus Negocios")
        self.assert_visible_text("Sección Legal")
        self.screenshot("04_administrar_negocios_page", full_page=True)
        return "Administrar Negocios page loaded with expected sections."

    def step_validate_informacion_general(self) -> str:
        self.assert_visible_text("Información General")

        page_text = self.page.inner_text("body")
        if not re.search(r"[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}", page_text):
            raise AssertionError("User email is not visible in Información General.")

        self.assert_visible_text("BUSINESS PLAN")
        self.assert_visible_text("Cambiar Plan")
        return "Información General contains user info, plan and action button."

    def step_validate_detalles_cuenta(self) -> str:
        self.assert_visible_text("Detalles de la Cuenta")
        self.assert_visible_text("Cuenta creada")
        self.assert_visible_text("Estado activo")
        self.assert_visible_text("Idioma seleccionado")
        return "Detalles de la Cuenta section validated."

    def step_validate_tus_negocios(self) -> str:
        self.assert_visible_text("Tus Negocios")
        self.assert_visible_text("Agregar Negocio")
        self.assert_visible_text("Tienes 2 de 3 negocios")

        # Validate a list/grid container exists in this section.
        section = self.page.locator("section, div").filter(has_text=re.compile("Tus Negocios", re.IGNORECASE)).first
        if not section.is_visible():
            raise AssertionError("Tus Negocios section is not visible.")

        return "Tus Negocios list and quota information are visible."

    def _open_legal_link_and_validate(
        self,
        link_text: str,
        heading_text: str,
        screenshot_name: str,
        url_key: str,
    ) -> str:
        original_page = self.app_page
        original_context_page_count = len(self.context.pages)

        try:
            with self.context.expect_page(timeout=8000) as popup_info:
                self.click_visible_text([link_text])
            legal_page = popup_info.value
            legal_page.wait_for_load_state("domcontentloaded")
            legal_page.wait_for_load_state("networkidle")
            self.page = legal_page
        except (TimeoutError, Error):
            # Same-tab navigation fallback.
            self.click_visible_text([link_text])
            legal_page = self.page

        self.assert_visible_text(heading_text, timeout_ms=20000)
        body_text = legal_page.inner_text("body")
        if len(body_text.strip()) < 100:
            raise AssertionError(f"Legal content seems too short for {heading_text}.")

        self.screenshot(screenshot_name, full_page=True)
        self.legal_urls[url_key] = legal_page.url

        # Cleanup: return to app tab.
        if len(self.context.pages) > original_context_page_count:
            legal_page.close()
            self.page = original_page
            self.page.bring_to_front()
            self.wait_for_ui_load()
        else:
            legal_page.go_back()
            legal_page.wait_for_load_state("domcontentloaded")
            self.page = original_page

        return f"{heading_text} validated at {self.legal_urls[url_key]}"

    def step_validate_terminos_y_condiciones(self) -> str:
        return self._open_legal_link_and_validate(
            link_text="Términos y Condiciones",
            heading_text="Términos y Condiciones",
            screenshot_name="05_terminos_y_condiciones",
            url_key="terminos_y_condiciones",
        )

    def step_validate_politica_privacidad(self) -> str:
        return self._open_legal_link_and_validate(
            link_text="Política de Privacidad",
            heading_text="Política de Privacidad",
            screenshot_name="06_politica_privacidad",
            url_key="politica_privacidad",
        )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="SaleADS Mi Negocio full workflow E2E test.")
    parser.add_argument(
        "--start-url",
        default="",
        help=(
            "Login page URL of the current SaleADS environment. "
            "No domain is hardcoded; pass the environment URL at runtime."
        ),
    )
    parser.add_argument(
        "--headed",
        action="store_true",
        help="Run browser in headed mode (helpful for Google sign-in).",
    )
    parser.add_argument(
        "--slow-mo-ms",
        type=int,
        default=0,
        help="Playwright slow motion delay in milliseconds between actions.",
    )
    parser.add_argument(
        "--output-dir",
        default="automation/output/saleads_mi_negocio_full_test",
        help="Directory where screenshots and final report will be stored.",
    )
    parser.add_argument(
        "--account-email",
        default=DEFAULT_ACCOUNT_EMAIL,
        help="Google account email to select when Google chooser appears.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=not args.headed, slow_mo=args.slow_mo_ms)
        context = browser.new_context(viewport={"width": 1440, "height": 900})
        page = context.new_page()

        if args.start_url:
            page.goto(args.start_url, wait_until="domcontentloaded")
        else:
            raise ValueError(
                "A start URL is required. Pass --start-url with the current environment login page URL."
            )

        workflow = SaleadsMiNegocioWorkflow(
            page=page,
            context=context,
            output_dir=output_dir,
            account_email=args.account_email,
        )
        results = workflow.run()
        browser.close()

    print(json.dumps(results, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
