#!/usr/bin/env python3
"""
SaleADS Mi Negocio full workflow validation.

This script assumes the browser starts on the SaleADS login page of the
currently active environment (dev/staging/prod) and does not hardcode domains.
It captures screenshots and writes both JSON and Markdown reports.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Callable

from playwright.sync_api import BrowserContext, Error, Page, TimeoutError, sync_playwright


GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com"


def now_epoch_ms() -> int:
    return int(time.time() * 1000)


def sanitize_filename(value: str) -> str:
    safe = re.sub(r"[^a-zA-Z0-9-_]+", "-", value.strip()).strip("-").lower()
    return safe or "checkpoint"


@dataclass
class StepResult:
    key: str
    title: str
    passed: bool = True
    details: list[str] = field(default_factory=list)
    screenshot: str | None = None
    url: str | None = None

    def add_ok(self, message: str) -> None:
        self.details.append(f"PASS: {message}")

    def add_fail(self, message: str) -> None:
        self.passed = False
        self.details.append(f"FAIL: {message}")


class SaleadsMiNegocioWorkflow:
    def __init__(self, page: Page, output_dir: Path, headless: bool, slow_mo_ms: int):
        self.page = page
        self.output_dir = output_dir
        self.screenshots_dir = output_dir / "screenshots"
        self.screenshots_dir.mkdir(parents=True, exist_ok=True)
        self.headless = headless
        self.slow_mo_ms = slow_mo_ms
        self.results: list[StepResult] = []

    def run(self) -> int:
        try:
            self.step_login_with_google()
            self.step_open_mi_negocio_menu()
            self.step_validate_agregar_negocio_modal()
            self.step_open_administrar_negocios()
            self.step_validate_informacion_general()
            self.step_validate_detalles_cuenta()
            self.step_validate_tus_negocios()
            self.step_validate_terminos_condiciones()
            self.step_validate_politica_privacidad()
        except Exception as ex:  # noqa: BLE001
            panic = StepResult(key="panic", title="Unexpected workflow failure", passed=False)
            panic.add_fail(f"Unhandled exception: {type(ex).__name__}: {ex}")
            self.results.append(panic)

        self.write_reports()
        return 0 if all(result.passed for result in self.results) else 1

    def step(self, key: str, title: str, fn: Callable[[StepResult], None]) -> None:
        result = StepResult(key=key, title=title)
        try:
            fn(result)
        except Exception as ex:  # noqa: BLE001
            result.add_fail(f"Unhandled step exception: {type(ex).__name__}: {ex}")
        self.results.append(result)

    def wait_ui(self) -> None:
        try:
            self.page.wait_for_load_state("domcontentloaded", timeout=30_000)
        except TimeoutError:
            pass
        try:
            self.page.wait_for_load_state("networkidle", timeout=8_000)
        except TimeoutError:
            pass
        self.page.wait_for_timeout(max(self.slow_mo_ms, 350))

    def click_by_visible_text(self, text: str, exact: bool = True, timeout_ms: int = 12_000) -> bool:
        selectors = [
            self.page.get_by_role("button", name=text, exact=exact),
            self.page.get_by_role("link", name=text, exact=exact),
            self.page.get_by_text(text, exact=exact),
        ]
        for locator in selectors:
            try:
                locator.first.wait_for(state="visible", timeout=timeout_ms)
                locator.first.click(timeout=timeout_ms)
                self.wait_ui()
                return True
            except TimeoutError:
                continue
            except Error:
                continue
        return False

    def assert_text_visible(self, result: StepResult, text: str, timeout_ms: int = 10_000) -> bool:
        try:
            self.page.get_by_text(text, exact=False).first.wait_for(state="visible", timeout=timeout_ms)
            result.add_ok(f"Visible text found: {text}")
            return True
        except TimeoutError:
            result.add_fail(f"Expected visible text not found: {text}")
            return False

    def assert_role_visible(
        self,
        result: StepResult,
        role: str,
        text: str,
        exact: bool = True,
        timeout_ms: int = 10_000,
    ) -> bool:
        try:
            locator = self.page.get_by_role(role, name=text, exact=exact)
            locator.first.wait_for(state="visible", timeout=timeout_ms)
            result.add_ok(f"Visible {role} found: {text}")
            return True
        except TimeoutError:
            result.add_fail(f"Expected visible {role} not found: {text}")
            return False

    def assert_selector_visible(self, result: StepResult, selector: str, label: str, timeout_ms: int = 8_000) -> bool:
        try:
            self.page.locator(selector).first.wait_for(state="visible", timeout=timeout_ms)
            result.add_ok(f"Visible element found: {label}")
            return True
        except TimeoutError:
            result.add_fail(f"Expected element missing: {label} ({selector})")
            return False

    def capture_step_screenshot(self, result: StepResult, name: str, full_page: bool = False) -> str:
        filename = f"{len(self.results)+1:02d}-{sanitize_filename(name)}.png"
        path = self.screenshots_dir / filename
        self.page.screenshot(path=str(path), full_page=full_page)
        result.screenshot = str(path.relative_to(self.output_dir))
        return str(path)

    def step_login_with_google(self) -> None:
        def action(result: StepResult) -> None:
            self.wait_ui()

            # Click an explicit Google login control when present.
            clicked = False
            for label in ("Sign in with Google", "Continuar con Google", "Iniciar con Google", "Google"):
                if self.click_by_visible_text(label, exact=False):
                    result.add_ok(f"Clicked login control using text: {label}")
                    clicked = True
                    break

            if not clicked:
                result.add_fail("Could not locate a Google login control by visible text.")

            # If Google account chooser appears, select the requested account.
            try:
                chooser = self.page.get_by_text(GOOGLE_ACCOUNT_EMAIL, exact=False)
                chooser.first.wait_for(state="visible", timeout=8_000)
                chooser.first.click(timeout=8_000)
                self.wait_ui()
                result.add_ok(f"Selected Google account: {GOOGLE_ACCOUNT_EMAIL}")
            except TimeoutError:
                result.add_ok("Google account chooser did not appear (already authenticated or redirected).")
            except Error as ex:
                result.add_fail(f"Google account chooser interaction failed: {ex}")

            # Validate app shell/dashboard appears.
            sidebar_visible = False
            sidebar_candidates = [
                "[role='navigation']",
                "aside",
                ".sidebar",
                "[class*='sidebar']",
                "[data-testid*='sidebar']",
            ]
            for selector in sidebar_candidates:
                try:
                    self.page.locator(selector).first.wait_for(state="visible", timeout=6_000)
                    sidebar_visible = True
                    result.add_ok(f"Sidebar visible via selector: {selector}")
                    break
                except TimeoutError:
                    continue

            if not sidebar_visible:
                result.add_fail("Main app sidebar/navigation did not become visible after login.")

            self.capture_step_screenshot(result, "dashboard-after-login", full_page=True)
            result.add_ok("Captured dashboard screenshot.")

        self.step("login", "Login", action)

    def step_open_mi_negocio_menu(self) -> None:
        def action(result: StepResult) -> None:
            self.wait_ui()

            if self.click_by_visible_text("Negocio", exact=False):
                result.add_ok("Clicked 'Negocio' section.")
            else:
                result.add_fail("Could not click 'Negocio' section.")

            if self.click_by_visible_text("Mi Negocio", exact=False):
                result.add_ok("Clicked 'Mi Negocio'.")
            else:
                result.add_fail("Could not click 'Mi Negocio'.")

            self.assert_text_visible(result, "Agregar Negocio")
            self.assert_text_visible(result, "Administrar Negocios")
            self.capture_step_screenshot(result, "mi-negocio-menu-expanded", full_page=False)
            result.add_ok("Captured expanded menu screenshot.")

        self.step("mi_negocio_menu", "Mi Negocio menu", action)

    def step_validate_agregar_negocio_modal(self) -> None:
        def action(result: StepResult) -> None:
            if self.click_by_visible_text("Agregar Negocio", exact=False):
                result.add_ok("Clicked 'Agregar Negocio'.")
            else:
                result.add_fail("Could not click 'Agregar Negocio'.")

            self.assert_text_visible(result, "Crear Nuevo Negocio")
            self.assert_text_visible(result, "Nombre del Negocio")
            self.assert_text_visible(result, "Tienes 2 de 3 negocios")
            self.assert_role_visible(result, "button", "Cancelar", exact=False)
            self.assert_role_visible(result, "button", "Crear Negocio", exact=False)

            # Optional actions from spec.
            try:
                input_box = self.page.get_by_label("Nombre del Negocio", exact=False).first
                input_box.wait_for(state="visible", timeout=8_000)
                input_box.click(timeout=8_000)
                input_box.fill("Negocio Prueba Automatización", timeout=8_000)
                result.add_ok("Filled 'Nombre del Negocio' optional input.")
            except TimeoutError:
                # Fallback to placeholder/textbox heuristics.
                try:
                    tb = self.page.get_by_role("textbox", name="Nombre del Negocio", exact=False).first
                    tb.wait_for(state="visible", timeout=5_000)
                    tb.click(timeout=5_000)
                    tb.fill("Negocio Prueba Automatización", timeout=5_000)
                    result.add_ok("Filled textbox 'Nombre del Negocio' with role fallback.")
                except Exception as ex:  # noqa: BLE001
                    result.add_fail(f"Optional input fill failed: {type(ex).__name__}: {ex}")

            self.capture_step_screenshot(result, "agregar-negocio-modal", full_page=False)
            result.add_ok("Captured modal screenshot.")

            if self.click_by_visible_text("Cancelar", exact=False):
                result.add_ok("Clicked 'Cancelar' to close modal.")
            else:
                result.add_fail("Could not click 'Cancelar' to close modal.")

        self.step("agregar_negocio_modal", "Agregar Negocio modal", action)

    def _open_administrar_negocios_view(self, result: StepResult) -> None:
        # Ensure menu items are visible; if not, expand again.
        try:
            self.page.get_by_text("Administrar Negocios", exact=False).first.wait_for(state="visible", timeout=3_000)
        except TimeoutError:
            self.click_by_visible_text("Mi Negocio", exact=False)

        if self.click_by_visible_text("Administrar Negocios", exact=False):
            result.add_ok("Clicked 'Administrar Negocios'.")
        else:
            result.add_fail("Could not click 'Administrar Negocios'.")

        self.assert_text_visible(result, "Información General")
        self.assert_text_visible(result, "Detalles de la Cuenta")
        self.assert_text_visible(result, "Tus Negocios")
        self.assert_text_visible(result, "Sección Legal")
        self.capture_step_screenshot(result, "administrar-negocios-view", full_page=True)
        result.add_ok("Captured account page screenshot.")

    def step_open_administrar_negocios(self) -> None:
        self.step("administrar_negocios_view", "Administrar Negocios view", self._open_administrar_negocios_view)

    def step_validate_informacion_general(self) -> None:
        def action(result: StepResult) -> None:
            self.assert_text_visible(result, "Información General")
            self.assert_text_visible(result, "@")
            self.assert_text_visible(result, "BUSINESS PLAN")
            self.assert_role_visible(result, "button", "Cambiar Plan", exact=False)

        self.step("informacion_general", "Información General", action)

    def step_validate_detalles_cuenta(self) -> None:
        def action(result: StepResult) -> None:
            self.assert_text_visible(result, "Cuenta creada")
            self.assert_text_visible(result, "Estado activo")
            self.assert_text_visible(result, "Idioma seleccionado")

        self.step("detalles_cuenta", "Detalles de la Cuenta", action)

    def step_validate_tus_negocios(self) -> None:
        def action(result: StepResult) -> None:
            self.assert_text_visible(result, "Tus Negocios")
            self.assert_text_visible(result, "Agregar Negocio")
            self.assert_text_visible(result, "Tienes 2 de 3 negocios")

        self.step("tus_negocios", "Tus Negocios", action)

    def _open_legal_link_validate(
        self,
        result: StepResult,
        link_text: str,
        expected_heading: str,
        screenshot_name: str,
    ) -> None:
        app_page = self.page
        app_url_before = app_page.url

        legal_page = app_page
        pages_before = list(app_page.context.pages)
        clicked = self.click_by_visible_text(link_text, exact=False)
        if not clicked:
            result.add_fail(f"Could not click legal link: {link_text}")
            return

        pages_after = list(app_page.context.pages)
        new_pages = [candidate for candidate in pages_after if candidate not in pages_before]
        new_tab_opened = len(new_pages) > 0
        if new_tab_opened:
            legal_page = new_pages[-1]
            legal_page.wait_for_load_state("domcontentloaded", timeout=20_000)
            try:
                legal_page.wait_for_load_state("networkidle", timeout=8_000)
            except TimeoutError:
                pass
            result.add_ok(f"'{link_text}' opened in a new tab.")
        else:
            legal_page = app_page
            result.add_ok(f"'{link_text}' opened in same tab.")

        try:
            legal_page.get_by_text(expected_heading, exact=False).first.wait_for(state="visible", timeout=15_000)
            result.add_ok(f"Found heading '{expected_heading}'.")
        except TimeoutError:
            result.add_fail(f"Missing heading '{expected_heading}'.")

        # Light content check, but avoid hardcoding complete legal body.
        body_text = legal_page.locator("body").inner_text(timeout=8_000)
        if len(body_text.strip()) > 120:
            result.add_ok("Legal content text is visible.")
        else:
            result.add_fail("Legal content text appears empty/too short.")

        result.url = legal_page.url
        result.add_ok(f"Captured final URL: {legal_page.url}")

        # Capture screenshot from the legal page.
        screenshot_path = self.screenshots_dir / f"{len(self.results)+1:02d}-{sanitize_filename(screenshot_name)}.png"
        legal_page.screenshot(path=str(screenshot_path), full_page=True)
        result.screenshot = str(screenshot_path.relative_to(self.output_dir))
        result.add_ok("Captured legal page screenshot.")

        # Cleanup: return to app tab/page.
        if new_tab_opened:
            legal_page.close()
            app_page.bring_to_front()
            self.wait_ui()
            result.add_ok("Returned to application tab.")
        else:
            if legal_page.url != app_url_before:
                legal_page.go_back(wait_until="domcontentloaded", timeout=20_000)
                self.wait_ui()
            result.add_ok("Returned to application page from same tab.")

    def step_validate_terminos_condiciones(self) -> None:
        def action(result: StepResult) -> None:
            self._open_legal_link_validate(
                result=result,
                link_text="Términos y Condiciones",
                expected_heading="Términos y Condiciones",
                screenshot_name="terminos-y-condiciones",
            )

        self.step("terminos_condiciones", "Términos y Condiciones", action)

    def step_validate_politica_privacidad(self) -> None:
        def action(result: StepResult) -> None:
            self._open_legal_link_validate(
                result=result,
                link_text="Política de Privacidad",
                expected_heading="Política de Privacidad",
                screenshot_name="politica-de-privacidad",
            )

        self.step("politica_privacidad", "Política de Privacidad", action)

    def write_reports(self) -> None:
        report_json = {
            "name": "saleads_mi_negocio_full_test",
            "timestamp_ms": now_epoch_ms(),
            "headless": self.headless,
            "results": [asdict(result) for result in self.results],
            "all_passed": all(result.passed for result in self.results),
            "final_report": {
                "Login": self._status_for("login"),
                "Mi Negocio menu": self._status_for("mi_negocio_menu"),
                "Agregar Negocio modal": self._status_for("agregar_negocio_modal"),
                "Administrar Negocios view": self._status_for("administrar_negocios_view"),
                "Información General": self._status_for("informacion_general"),
                "Detalles de la Cuenta": self._status_for("detalles_cuenta"),
                "Tus Negocios": self._status_for("tus_negocios"),
                "Términos y Condiciones": self._status_for("terminos_condiciones"),
                "Política de Privacidad": self._status_for("politica_privacidad"),
            },
        }
        json_path = self.output_dir / "report.json"
        json_path.write_text(json.dumps(report_json, ensure_ascii=False, indent=2), encoding="utf-8")

        md_lines: list[str] = [
            "# SaleADS Mi Negocio Full Test Report",
            "",
            f"- Overall: **{'PASS' if report_json['all_passed'] else 'FAIL'}**",
            f"- Headless: `{self.headless}`",
            "",
            "## Final Report",
            "",
            "| Check | Status |",
            "|---|---|",
        ]
        for key, value in report_json["final_report"].items():
            md_lines.append(f"| {key} | {value} |")

        md_lines.append("")
        md_lines.append("## Step Details")
        md_lines.append("")
        for result in self.results:
            md_lines.append(f"### {result.title} — {'PASS' if result.passed else 'FAIL'}")
            if result.url:
                md_lines.append(f"- URL: `{result.url}`")
            if result.screenshot:
                md_lines.append(f"- Screenshot: `{result.screenshot}`")
            for detail in result.details:
                md_lines.append(f"- {detail}")
            md_lines.append("")

        md_path = self.output_dir / "report.md"
        md_path.write_text("\n".join(md_lines), encoding="utf-8")

    def _status_for(self, key: str) -> str:
        for result in self.results:
            if result.key == key:
                return "PASS" if result.passed else "FAIL"
        return "FAIL"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run SaleADS Mi Negocio full workflow test.")
    parser.add_argument(
        "--base-url",
        default=os.getenv("SALEADS_BASE_URL", ""),
        help=(
            "Optional base URL for the active SaleADS environment login page. "
            "If omitted, script expects an already-open login page."
        ),
    )
    parser.add_argument("--headless", action="store_true", help="Run browser in headless mode.")
    parser.add_argument("--slow-mo-ms", type=int, default=350, help="Delay after actions for UI stabilization.")
    parser.add_argument(
        "--output-dir",
        default=f"artifacts/saleads-mi-negocio-{now_epoch_ms()}",
        help="Directory for screenshots and reports.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=args.headless, slow_mo=args.slow_mo_ms)
        context: BrowserContext = browser.new_context()
        page = context.new_page()

        if args.base_url:
            page.goto(args.base_url, wait_until="domcontentloaded", timeout=45_000)

        workflow = SaleadsMiNegocioWorkflow(
            page=page,
            output_dir=output_dir,
            headless=args.headless,
            slow_mo_ms=args.slow_mo_ms,
        )
        exit_code = workflow.run()
        context.close()
        browser.close()
        return exit_code


if __name__ == "__main__":
    sys.exit(main())
