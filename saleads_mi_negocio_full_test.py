#!/usr/bin/env python3
"""
SaleADS Mi Negocio end-to-end workflow test.

Environment-agnostic behavior:
- Does not hardcode any domain.
- Uses --url argument or SALEADS_URL environment variable.
- If no URL is provided, it still launches the browser and fails clearly.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Pattern, Tuple

from playwright.sync_api import Browser, BrowserContext, Locator, Page, TimeoutError, sync_playwright


REPORT_FIELDS: List[Tuple[str, str]] = [
    ("login", "Login"),
    ("mi_negocio_menu", "Mi Negocio menu"),
    ("agregar_modal", "Agregar Negocio modal"),
    ("administrar_view", "Administrar Negocios view"),
    ("informacion_general", "Informacion General"),
    ("detalles_cuenta", "Detalles de la Cuenta"),
    ("tus_negocios", "Tus Negocios"),
    ("terminos", "Terminos y Condiciones"),
    ("privacidad", "Politica de Privacidad"),
]


@dataclass
class StepResult:
    status: str = "FAIL"
    notes: List[str] = field(default_factory=list)
    artifacts: List[str] = field(default_factory=list)
    url: Optional[str] = None

    def pass_step(self, note: str) -> None:
        self.status = "PASS"
        self.notes.append(note)

    def fail_step(self, note: str) -> None:
        self.status = "FAIL"
        self.notes.append(note)


class SaleadsMiNegocioWorkflow:
    def __init__(self, page: Page, context: BrowserContext, evidence_dir: Path, timeout_ms: int) -> None:
        self.page = page
        self.context = context
        self.evidence_dir = evidence_dir
        self.timeout_ms = timeout_ms
        self.report: Dict[str, StepResult] = {key: StepResult() for key, _ in REPORT_FIELDS}

    def _regex(self, pattern: str) -> Pattern[str]:
        return re.compile(pattern, re.IGNORECASE)

    def _wait_ui(self, page: Optional[Page] = None) -> None:
        target = page or self.page
        try:
            target.wait_for_load_state("networkidle", timeout=self.timeout_ms)
        except TimeoutError:
            target.wait_for_load_state("domcontentloaded", timeout=self.timeout_ms)

    def _screenshot(self, name: str, full_page: bool = False, page: Optional[Page] = None) -> str:
        target = page or self.page
        timestamp = int(time.time() * 1000)
        path = self.evidence_dir / f"{timestamp}_{name}.png"
        target.screenshot(path=str(path), full_page=full_page)
        return str(path)

    def _first_visible(self, locators: List[Locator]) -> Optional[Locator]:
        for locator in locators:
            try:
                if locator.first.is_visible(timeout=2000):
                    return locator.first
            except TimeoutError:
                continue
        return None

    def _click_and_wait(self, locator: Locator, page: Optional[Page] = None) -> None:
        target = page or self.page
        locator.click(timeout=self.timeout_ms)
        self._wait_ui(target)

    def _expect_visible(
        self,
        locator: Locator,
        result_key: str,
        requirement: str,
        timeout_ms: Optional[int] = None,
    ) -> bool:
        wait_timeout = timeout_ms or self.timeout_ms
        try:
            locator.first.wait_for(state="visible", timeout=wait_timeout)
            self.report[result_key].notes.append(f"OK: {requirement}")
            return True
        except TimeoutError:
            self.report[result_key].notes.append(f"MISSING: {requirement}")
            return False

    def _assert_group(self, result_key: str, checks: List[Tuple[Locator, str]], success_note: str) -> None:
        all_ok = True
        for locator, label in checks:
            if not self._expect_visible(locator, result_key, label):
                all_ok = False
        if all_ok:
            self.report[result_key].pass_step(success_note)
        else:
            self.report[result_key].fail_step("One or more required elements were not visible.")

    def _handle_google_account_selector(self, google_page: Page) -> None:
        account_regex = self._regex(r"juanlucasbarbiergarzon@gmail\.com")
        account_locator = google_page.get_by_text(account_regex)
        try:
            if account_locator.first.is_visible(timeout=7000):
                self._click_and_wait(account_locator, page=google_page)
        except TimeoutError:
            pass

    def _click_with_possible_new_tab(self, click_locator: Locator) -> Tuple[Page, bool]:
        try:
            with self.context.expect_page(timeout=5000) as page_info:
                self._click_and_wait(click_locator)
            new_page = page_info.value
            new_page.wait_for_load_state("domcontentloaded", timeout=self.timeout_ms)
            return new_page, True
        except TimeoutError:
            self._wait_ui(self.page)
            return self.page, False

    def step_1_login(self) -> None:
        key = "login"
        login_buttons = [
            self.page.get_by_role("button", name=self._regex(r"(sign in|iniciar sesi[o\u00f3]n|continuar).*(google)")),
            self.page.get_by_text(self._regex(r"sign in with google|iniciar sesi[o\u00f3]n con google")),
            self.page.get_by_role("link", name=self._regex(r".*google.*")),
        ]

        login_btn = self._first_visible(login_buttons)
        if login_btn is None:
            self.report[key].fail_step("Could not find a Google login button or link.")
            return

        google_page: Optional[Page] = None
        try:
            with self.context.expect_page(timeout=5000) as page_info:
                self._click_and_wait(login_btn)
            google_page = page_info.value
            google_page.wait_for_load_state("domcontentloaded", timeout=self.timeout_ms)
        except TimeoutError:
            self._wait_ui(self.page)

        if google_page is not None:
            self._handle_google_account_selector(google_page)
            try:
                google_page.wait_for_close(timeout=45000)
            except TimeoutError:
                pass

        self._wait_ui(self.page)
        sidebar_checks = [
            (
                self.page.locator("aside").or_(self.page.locator("nav")),
                "Main application interface with sidebar navigation",
            ),
            (self.page.get_by_text(self._regex(r"negocio")), "Sidebar visible and contains Negocio"),
        ]
        self._assert_group(key, sidebar_checks, "Dashboard loaded after Google login.")
        shot = self._screenshot("step1_dashboard_loaded")
        self.report[key].artifacts.append(shot)

    def step_2_open_mi_negocio_menu(self) -> None:
        key = "mi_negocio_menu"
        negocio_trigger = self._first_visible(
            [
                self.page.get_by_role("button", name=self._regex(r"^negocio$")),
                self.page.get_by_role("link", name=self._regex(r"^negocio$")),
                self.page.get_by_text(self._regex(r"^negocio$")),
            ]
        )
        if negocio_trigger is not None:
            self._click_and_wait(negocio_trigger)

        mi_negocio_option = self._first_visible(
            [
                self.page.get_by_role("button", name=self._regex(r"mi negocio")),
                self.page.get_by_role("link", name=self._regex(r"mi negocio")),
                self.page.get_by_text(self._regex(r"mi negocio")),
            ]
        )
        if mi_negocio_option is None:
            self.report[key].fail_step("Could not find 'Mi Negocio' option in left sidebar.")
            return

        self._click_and_wait(mi_negocio_option)
        checks = [
            (self.page.get_by_text(self._regex(r"agregar negocio")), "Submenu item 'Agregar Negocio'"),
            (
                self.page.get_by_text(self._regex(r"administrar negocios")),
                "Submenu item 'Administrar Negocios'",
            ),
        ]
        self._assert_group(key, checks, "Mi Negocio submenu expanded correctly.")
        shot = self._screenshot("step2_mi_negocio_expanded")
        self.report[key].artifacts.append(shot)

    def step_3_validate_agregar_modal(self) -> None:
        key = "agregar_modal"
        agregar_option = self._first_visible(
            [
                self.page.get_by_role("button", name=self._regex(r"agregar negocio")),
                self.page.get_by_role("link", name=self._regex(r"agregar negocio")),
                self.page.get_by_text(self._regex(r"agregar negocio")),
            ]
        )
        if agregar_option is None:
            self.report[key].fail_step("Could not find 'Agregar Negocio'.")
            return

        self._click_and_wait(agregar_option)

        dialog = self.page.get_by_role("dialog")
        checks = [
            (
                dialog.get_by_text(self._regex(r"crear nuevo negocio")),
                "Modal title 'Crear Nuevo Negocio'",
            ),
            (
                dialog.get_by_label(self._regex(r"nombre del negocio")).or_(
                    dialog.get_by_placeholder(self._regex(r"nombre del negocio"))
                ),
                "Input field 'Nombre del Negocio'",
            ),
            (
                dialog.get_by_text(self._regex(r"tienes\s+2\s+de\s+3\s+negocios")),
                "Usage text 'Tienes 2 de 3 negocios'",
            ),
            (dialog.get_by_role("button", name=self._regex(r"cancelar")), "Button 'Cancelar'"),
            (dialog.get_by_role("button", name=self._regex(r"crear negocio")), "Button 'Crear Negocio'"),
        ]
        self._assert_group(key, checks, "Agregar Negocio modal validated successfully.")
        shot = self._screenshot("step3_agregar_modal")
        self.report[key].artifacts.append(shot)

        name_input = dialog.get_by_label(self._regex(r"nombre del negocio")).or_(
            dialog.get_by_placeholder(self._regex(r"nombre del negocio"))
        )
        try:
            name_input.first.click(timeout=3000)
            name_input.first.fill("Negocio Prueba Automatizacion", timeout=3000)
        except TimeoutError:
            self.report[key].notes.append("Optional input interaction was skipped.")

        cancel_button = dialog.get_by_role("button", name=self._regex(r"cancelar"))
        try:
            self._click_and_wait(cancel_button)
        except TimeoutError:
            self.report[key].notes.append("Optional modal close by 'Cancelar' did not complete.")

    def step_4_open_administrar_negocios(self) -> None:
        key = "administrar_view"
        mi_negocio_option = self._first_visible(
            [
                self.page.get_by_role("button", name=self._regex(r"mi negocio")),
                self.page.get_by_role("link", name=self._regex(r"mi negocio")),
                self.page.get_by_text(self._regex(r"mi negocio")),
            ]
        )
        if mi_negocio_option is not None:
            self._click_and_wait(mi_negocio_option)

        administrar = self._first_visible(
            [
                self.page.get_by_role("button", name=self._regex(r"administrar negocios")),
                self.page.get_by_role("link", name=self._regex(r"administrar negocios")),
                self.page.get_by_text(self._regex(r"administrar negocios")),
            ]
        )
        if administrar is None:
            self.report[key].fail_step("Could not find 'Administrar Negocios'.")
            return

        self._click_and_wait(administrar)
        checks = [
            (self.page.get_by_text(self._regex(r"informaci[o\u00f3]n general")), "Section 'Informacion General'"),
            (
                self.page.get_by_text(self._regex(r"detalles de la cuenta")),
                "Section 'Detalles de la Cuenta'",
            ),
            (self.page.get_by_text(self._regex(r"tus negocios")), "Section 'Tus Negocios'"),
            (self.page.get_by_text(self._regex(r"secci[o\u00f3]n legal")), "Section 'Seccion Legal'"),
        ]
        self._assert_group(key, checks, "Administrar Negocios page sections are visible.")
        shot = self._screenshot("step4_administrar_negocios_full", full_page=True)
        self.report[key].artifacts.append(shot)

    def step_5_validate_informacion_general(self) -> None:
        key = "informacion_general"
        checks = [
            (
                self.page.locator("text=/@/").or_(self.page.get_by_text(self._regex(r"correo|email"))),
                "User email visible",
            ),
            (
                self.page.locator("h1,h2,h3,p,span,div").filter(has_text=self._regex(r"business plan")),
                "Text 'BUSINESS PLAN'",
            ),
            (self.page.get_by_role("button", name=self._regex(r"cambiar plan")), "Button 'Cambiar Plan'"),
            (
                self.page.locator("h1,h2,h3,p,span,div")
                .filter(has_not_text=self._regex(r"^\s*$"))
                .first,
                "User name or profile text visible",
            ),
        ]
        self._assert_group(key, checks, "Informacion General validated.")

    def step_6_validate_detalles_cuenta(self) -> None:
        key = "detalles_cuenta"
        checks = [
            (self.page.get_by_text(self._regex(r"cuenta creada")), "'Cuenta creada' visible"),
            (self.page.get_by_text(self._regex(r"estado activo|activo")), "'Estado activo' visible"),
            (
                self.page.get_by_text(self._regex(r"idioma seleccionado|idioma")),
                "'Idioma seleccionado' visible",
            ),
        ]
        self._assert_group(key, checks, "Detalles de la Cuenta validated.")

    def step_7_validate_tus_negocios(self) -> None:
        key = "tus_negocios"
        checks = [
            (
                self.page.get_by_text(self._regex(r"tus negocios")).or_(
                    self.page.locator("section,div").filter(has_text=self._regex(r"negocio"))
                ),
                "Business list section visible",
            ),
            (
                self.page.get_by_role("button", name=self._regex(r"agregar negocio")).or_(
                    self.page.get_by_text(self._regex(r"agregar negocio"))
                ),
                "Button 'Agregar Negocio' exists",
            ),
            (
                self.page.get_by_text(self._regex(r"tienes\s+2\s+de\s+3\s+negocios")),
                "Text 'Tienes 2 de 3 negocios' visible",
            ),
        ]
        self._assert_group(key, checks, "Tus Negocios section validated.")

    def _validate_legal_page(
        self,
        result_key: str,
        trigger_pattern: str,
        heading_pattern: str,
        screenshot_name: str,
    ) -> None:
        trigger = self._first_visible(
            [
                self.page.get_by_role("link", name=self._regex(trigger_pattern)),
                self.page.get_by_role("button", name=self._regex(trigger_pattern)),
                self.page.get_by_text(self._regex(trigger_pattern)),
            ]
        )
        if trigger is None:
            self.report[result_key].fail_step(f"Could not find legal action: {trigger_pattern}")
            return

        target_page, opened_new_tab = self._click_with_possible_new_tab(trigger)
        self._wait_ui(target_page)

        checks = [
            (
                target_page.get_by_role("heading", name=self._regex(heading_pattern)).or_(
                    target_page.get_by_text(self._regex(heading_pattern))
                ),
                f"Heading '{heading_pattern}' visible",
            ),
            (
                target_page.locator("main,article,section,p,div")
                .filter(has_not_text=self._regex(r"^\s*$"))
                .first,
                "Legal content text visible",
            ),
        ]

        all_ok = True
        for locator, label in checks:
            try:
                locator.first.wait_for(state="visible", timeout=self.timeout_ms)
                self.report[result_key].notes.append(f"OK: {label}")
            except TimeoutError:
                self.report[result_key].notes.append(f"MISSING: {label}")
                all_ok = False

        shot = self._screenshot(screenshot_name, full_page=True, page=target_page)
        self.report[result_key].artifacts.append(shot)
        self.report[result_key].url = target_page.url

        if all_ok:
            self.report[result_key].pass_step("Legal page validated successfully.")
        else:
            self.report[result_key].fail_step("Legal page validation failed.")

        if opened_new_tab:
            target_page.close()
            self.page.bring_to_front()
            self._wait_ui(self.page)
        else:
            try:
                self.page.go_back(timeout=self.timeout_ms)
                self._wait_ui(self.page)
            except TimeoutError:
                pass

    def step_8_validate_terminos(self) -> None:
        self._validate_legal_page(
            result_key="terminos",
            trigger_pattern=r"t[e\u00e9]rminos y condiciones|terminos y condiciones",
            heading_pattern=r"t[e\u00e9]rminos y condiciones|terminos y condiciones",
            screenshot_name="step8_terminos_condiciones",
        )

    def step_9_validate_privacidad(self) -> None:
        self._validate_legal_page(
            result_key="privacidad",
            trigger_pattern=r"pol[i\u00ed]tica de privacidad|politica de privacidad",
            heading_pattern=r"pol[i\u00ed]tica de privacidad|politica de privacidad",
            screenshot_name="step9_politica_privacidad",
        )

    def execute(self) -> Dict[str, object]:
        self.step_1_login()
        self.step_2_open_mi_negocio_menu()
        self.step_3_validate_agregar_modal()
        self.step_4_open_administrar_negocios()
        self.step_5_validate_informacion_general()
        self.step_6_validate_detalles_cuenta()
        self.step_7_validate_tus_negocios()
        self.step_8_validate_terminos()
        self.step_9_validate_privacidad()
        return self._final_report()

    def _final_report(self) -> Dict[str, object]:
        result_rows = []
        all_pass = True

        for key, label in REPORT_FIELDS:
            row = self.report[key]
            if row.status != "PASS":
                all_pass = False
            result_rows.append(
                {
                    "field": label,
                    "status": row.status,
                    "notes": row.notes,
                    "artifacts": row.artifacts,
                    "url": row.url,
                }
            )

        return {
            "test_name": "saleads_mi_negocio_full_test",
            "overall_status": "PASS" if all_pass else "FAIL",
            "results": result_rows,
            "evidence_dir": str(self.evidence_dir),
        }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run SaleADS Mi Negocio full workflow test.")
    parser.add_argument(
        "--url",
        default=os.getenv("SALEADS_URL", "").strip(),
        help="Login page URL of current SaleADS environment (optional if injected by runner).",
    )
    parser.add_argument(
        "--headless",
        default=os.getenv("HEADLESS", "true").lower(),
        choices=["true", "false"],
        help="Run browser in headless mode (true|false).",
    )
    parser.add_argument(
        "--timeout-ms",
        type=int,
        default=int(os.getenv("SALEADS_TIMEOUT_MS", "30000")),
        help="Element/action timeout in milliseconds.",
    )
    parser.add_argument(
        "--evidence-dir",
        default=os.getenv("SALEADS_EVIDENCE_DIR", "artifacts/saleads_mi_negocio_full_test"),
        help="Directory for screenshots and JSON report.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    evidence_dir = Path(args.evidence_dir)
    evidence_dir.mkdir(parents=True, exist_ok=True)
    report_path = evidence_dir / "final_report.json"

    with sync_playwright() as playwright:
        browser: Browser = playwright.chromium.launch(headless=args.headless == "true")
        context = browser.new_context()
        page = context.new_page()

        if args.url:
            page.goto(args.url, wait_until="domcontentloaded", timeout=args.timeout_ms)
        else:
            browser.close()
            output = {
                "test_name": "saleads_mi_negocio_full_test",
                "overall_status": "FAIL",
                "error": (
                    "No URL provided. Pass --url or set SALEADS_URL to the current environment login page."
                ),
                "results": [],
                "evidence_dir": str(evidence_dir),
            }
            report_path.write_text(json.dumps(output, indent=2), encoding="utf-8")
            print(json.dumps(output, indent=2))
            return 1

        workflow = SaleadsMiNegocioWorkflow(
            page=page,
            context=context,
            evidence_dir=evidence_dir,
            timeout_ms=args.timeout_ms,
        )
        output = workflow.execute()
        report_path.write_text(json.dumps(output, indent=2), encoding="utf-8")
        print(json.dumps(output, indent=2))

        browser.close()
        return 0 if output.get("overall_status") == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
