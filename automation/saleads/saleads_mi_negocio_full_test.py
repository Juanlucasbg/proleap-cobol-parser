#!/usr/bin/env python3
"""
SaleADS end-to-end workflow validation for Mi Negocio module.

This script is environment-agnostic:
- It never hardcodes a domain.
- Base URL is provided via --url or SALEADS_BASE_URL.
- It relies primarily on visible text selectors.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable, Optional

from playwright.sync_api import Browser, BrowserContext, Error, Locator, Page, TimeoutError, sync_playwright


DEFAULT_TIMEOUT_MS = 15000
UI_SETTLE_MS = 600


@dataclass
class StepResult:
    key: str
    title: str
    passed: bool = True
    notes: list[str] = field(default_factory=list)
    screenshots: list[str] = field(default_factory=list)
    final_url: Optional[str] = None

    def fail(self, message: str) -> None:
        self.passed = False
        self.notes.append(message)

    def pass_note(self, message: str) -> None:
        self.notes.append(message)


class SaleadsMiNegocioWorkflow:
    def __init__(self, page: Page, context: BrowserContext, artifacts_dir: Path, timeout_ms: int) -> None:
        self.page = page
        self.context = context
        self.artifacts_dir = artifacts_dir
        self.timeout_ms = timeout_ms
        self.results: list[StepResult] = []

    def run(self) -> dict:
        self.step_login_with_google()
        self.step_open_mi_negocio_menu()
        self.step_validate_agregar_negocio_modal()
        self.step_open_administrar_negocios()
        self.step_validate_informacion_general()
        self.step_validate_detalles_cuenta()
        self.step_validate_tus_negocios()
        self.step_validate_terminos_condiciones()
        self.step_validate_politica_privacidad()
        return self.build_report()

    def build_report(self) -> dict:
        mapping = [
            ("login", "Login"),
            ("mi_negocio_menu", "Mi Negocio menu"),
            ("agregar_negocio_modal", "Agregar Negocio modal"),
            ("administrar_negocios_view", "Administrar Negocios view"),
            ("informacion_general", "Informacion General"),
            ("detalles_cuenta", "Detalles de la Cuenta"),
            ("tus_negocios", "Tus Negocios"),
            ("terminos_condiciones", "Terminos y Condiciones"),
            ("politica_privacidad", "Politica de Privacidad"),
        ]
        by_key = {result.key: result for result in self.results}
        final = []
        all_passed = True
        for key, label in mapping:
            result = by_key.get(key)
            if result is None:
                final.append({"field": label, "status": "FAIL", "notes": ["Step was not executed."]})
                all_passed = False
                continue
            status = "PASS" if result.passed else "FAIL"
            if status == "FAIL":
                all_passed = False
            final.append(
                {
                    "field": label,
                    "status": status,
                    "notes": result.notes,
                    "screenshots": result.screenshots,
                    "final_url": result.final_url,
                }
            )

        return {
            "name": "saleads_mi_negocio_full_test",
            "overall_status": "PASS" if all_passed else "FAIL",
            "generated_at_utc": datetime.now(timezone.utc).isoformat(),
            "artifacts_dir": str(self.artifacts_dir),
            "results": final,
        }

    def wait_for_ui(self, page: Optional[Page] = None) -> None:
        target = page or self.page
        try:
            target.wait_for_load_state("domcontentloaded", timeout=self.timeout_ms)
        except TimeoutError:
            pass
        try:
            target.wait_for_load_state("networkidle", timeout=self.timeout_ms)
        except TimeoutError:
            pass
        target.wait_for_timeout(UI_SETTLE_MS)

    def find_first_visible(self, builders: list[Callable[[], Locator]], timeout_ms: int = 3000) -> Optional[Locator]:
        for build in builders:
            locator = build()
            try:
                locator.first.wait_for(state="visible", timeout=timeout_ms)
                return locator.first
            except TimeoutError:
                continue
            except Error:
                continue
        return None

    def expect_visible(self, result: StepResult, message: str, builders: list[Callable[[], Locator]]) -> Optional[Locator]:
        locator = self.find_first_visible(builders)
        if locator is None:
            result.fail(f"Not visible: {message}")
        else:
            result.pass_note(f"Visible: {message}")
        return locator

    def click_and_wait(self, result: StepResult, click_label: str, locator: Locator, page: Optional[Page] = None) -> None:
        try:
            locator.click()
            self.wait_for_ui(page=page)
            result.pass_note(f"Clicked: {click_label}")
        except Exception as exc:  # broad for resilient automation logging
            result.fail(f"Failed click '{click_label}': {exc}")

    def screenshot(self, result: StepResult, file_stem: str, full_page: bool = False, page: Optional[Page] = None) -> None:
        target_page = page or self.page
        path = self.artifacts_dir / f"{file_stem}.png"
        target_page.screenshot(path=str(path), full_page=full_page)
        result.screenshots.append(str(path))

    def step_login_with_google(self) -> None:
        result = StepResult(key="login", title="Login with Google")
        self.results.append(result)

        sidebar_or_app = self.find_first_visible(
            [
                lambda: self.page.get_by_role("link", name=re.compile(r"Mi\s*Negocio", re.I)),
                lambda: self.page.get_by_role("button", name=re.compile(r"Mi\s*Negocio", re.I)),
                lambda: self.page.get_by_text(re.compile(r"Negocio", re.I)),
            ],
            timeout_ms=2500,
        )

        if sidebar_or_app is None:
            google_button = self.expect_visible(
                result,
                "Google login button",
                [
                    lambda: self.page.get_by_role("button", name=re.compile(r"Google", re.I)),
                    lambda: self.page.get_by_role("link", name=re.compile(r"Google", re.I)),
                    lambda: self.page.get_by_text(re.compile(r"Sign in with Google|Iniciar.*Google|Continuar.*Google", re.I)),
                ],
            )

            if google_button is not None:
                popup_page: Optional[Page] = None
                try:
                    with self.context.expect_page(timeout=5000) as popup_info:
                        google_button.click()
                    popup_page = popup_info.value
                    self.wait_for_ui(popup_page)
                    result.pass_note("Google auth opened in popup tab.")
                except TimeoutError:
                    self.wait_for_ui(self.page)
                    result.pass_note("Google auth proceeded in current tab or redirect flow.")

                auth_page = popup_page or self.page
                account = self.find_first_visible(
                    [
                        lambda: auth_page.get_by_text("juanlucasbarbiergarzon@gmail.com", exact=True),
                        lambda: auth_page.get_by_role("button", name=re.compile(r"juanlucasbarbiergarzon@gmail\.com", re.I)),
                    ],
                    timeout_ms=5000,
                )
                if account is not None:
                    self.click_and_wait(result, "Google account selection", account, page=auth_page)
                else:
                    result.pass_note("Google account chooser did not appear or account already selected.")

                if popup_page is not None:
                    try:
                        popup_page.wait_for_event("close", timeout=30000)
                    except TimeoutError:
                        popup_page.close()
                    self.page.bring_to_front()
                    self.wait_for_ui(self.page)
        else:
            result.pass_note("Session appears already logged in; skipped explicit Google login click.")

        self.expect_visible(
            result,
            "Main interface visible",
            [
                lambda: self.page.get_by_text(re.compile(r"Dashboard|Negocio|Mi\s*Negocio", re.I)),
                lambda: self.page.get_by_role("main"),
            ],
        )
        self.expect_visible(
            result,
            "Left sidebar navigation visible",
            [
                lambda: self.page.get_by_role("navigation"),
                lambda: self.page.locator("aside"),
            ],
        )
        self.screenshot(result, "01_dashboard_loaded")

    def step_open_mi_negocio_menu(self) -> None:
        result = StepResult(key="mi_negocio_menu", title="Open Mi Negocio menu")
        self.results.append(result)

        negocio = self.find_first_visible(
            [
                lambda: self.page.get_by_role("button", name=re.compile(r"Negocio", re.I)),
                lambda: self.page.get_by_role("link", name=re.compile(r"Negocio", re.I)),
                lambda: self.page.get_by_text(re.compile(r"^Negocio$", re.I)),
            ]
        )
        if negocio is not None:
            self.click_and_wait(result, "Negocio section", negocio)
        else:
            result.pass_note("Negocio section control not explicitly found; continuing with Mi Negocio option.")

        mi_negocio = self.expect_visible(
            result,
            "Mi Negocio option",
            [
                lambda: self.page.get_by_role("button", name=re.compile(r"Mi\s*Negocio", re.I)),
                lambda: self.page.get_by_role("link", name=re.compile(r"Mi\s*Negocio", re.I)),
                lambda: self.page.get_by_text(re.compile(r"Mi\s*Negocio", re.I)),
            ],
        )
        if mi_negocio is not None:
            self.click_and_wait(result, "Mi Negocio option", mi_negocio)

        self.expect_visible(
            result,
            "Agregar Negocio visible in submenu",
            [
                lambda: self.page.get_by_role("button", name=re.compile(r"Agregar\s+Negocio", re.I)),
                lambda: self.page.get_by_role("link", name=re.compile(r"Agregar\s+Negocio", re.I)),
                lambda: self.page.get_by_text(re.compile(r"Agregar\s+Negocio", re.I)),
            ],
        )
        self.expect_visible(
            result,
            "Administrar Negocios visible in submenu",
            [
                lambda: self.page.get_by_role("button", name=re.compile(r"Administrar\s+Negocios", re.I)),
                lambda: self.page.get_by_role("link", name=re.compile(r"Administrar\s+Negocios", re.I)),
                lambda: self.page.get_by_text(re.compile(r"Administrar\s+Negocios", re.I)),
            ],
        )
        self.screenshot(result, "02_mi_negocio_menu_expanded")

    def step_validate_agregar_negocio_modal(self) -> None:
        result = StepResult(key="agregar_negocio_modal", title="Validate Agregar Negocio modal")
        self.results.append(result)

        agregar_negocio = self.expect_visible(
            result,
            "Agregar Negocio action",
            [
                lambda: self.page.get_by_role("button", name=re.compile(r"Agregar\s+Negocio", re.I)),
                lambda: self.page.get_by_role("link", name=re.compile(r"Agregar\s+Negocio", re.I)),
                lambda: self.page.get_by_text(re.compile(r"Agregar\s+Negocio", re.I)),
            ],
        )
        if agregar_negocio is not None:
            self.click_and_wait(result, "Agregar Negocio", agregar_negocio)

        self.expect_visible(
            result,
            "Modal title 'Crear Nuevo Negocio'",
            [lambda: self.page.get_by_text(re.compile(r"Crear\s+Nuevo\s+Negocio", re.I))],
        )
        name_field = self.expect_visible(
            result,
            "Input field 'Nombre del Negocio'",
            [
                lambda: self.page.get_by_label(re.compile(r"Nombre\s+del\s+Negocio", re.I)),
                lambda: self.page.get_by_role("textbox", name=re.compile(r"Nombre\s+del\s+Negocio", re.I)),
                lambda: self.page.get_by_placeholder(re.compile(r"Nombre\s+del\s+Negocio", re.I)),
                lambda: self.page.locator("input[name*='nombre' i], input[placeholder*='Nombre' i]"),
            ],
        )
        self.expect_visible(
            result,
            "Text 'Tienes 2 de 3 negocios'",
            [lambda: self.page.get_by_text(re.compile(r"Tienes\s+2\s+de\s+3\s+negocios", re.I))],
        )
        cancel_button = self.expect_visible(
            result,
            "Button 'Cancelar'",
            [lambda: self.page.get_by_role("button", name=re.compile(r"Cancelar", re.I))],
        )
        self.expect_visible(
            result,
            "Button 'Crear Negocio'",
            [lambda: self.page.get_by_role("button", name=re.compile(r"Crear\s+Negocio", re.I))],
        )

        self.screenshot(result, "03_agregar_negocio_modal")

        if name_field is not None:
            try:
                name_field.fill("Negocio Prueba Automatizacion")
                result.pass_note("Optional action: wrote test business name.")
            except Exception as exc:  # broad for resilient automation logging
                result.fail(f"Optional action failed while typing business name: {exc}")

        if cancel_button is not None:
            self.click_and_wait(result, "Cancelar button", cancel_button)
        else:
            self.page.keyboard.press("Escape")
            self.wait_for_ui()

    def step_open_administrar_negocios(self) -> None:
        result = StepResult(key="administrar_negocios_view", title="Open Administrar Negocios")
        self.results.append(result)

        administrar = self.find_first_visible(
            [
                lambda: self.page.get_by_role("button", name=re.compile(r"Administrar\s+Negocios", re.I)),
                lambda: self.page.get_by_role("link", name=re.compile(r"Administrar\s+Negocios", re.I)),
                lambda: self.page.get_by_text(re.compile(r"Administrar\s+Negocios", re.I)),
            ],
            timeout_ms=2500,
        )

        if administrar is None:
            mi_negocio = self.find_first_visible(
                [
                    lambda: self.page.get_by_role("button", name=re.compile(r"Mi\s*Negocio", re.I)),
                    lambda: self.page.get_by_role("link", name=re.compile(r"Mi\s*Negocio", re.I)),
                    lambda: self.page.get_by_text(re.compile(r"Mi\s*Negocio", re.I)),
                ]
            )
            if mi_negocio is not None:
                self.click_and_wait(result, "Re-expand Mi Negocio", mi_negocio)
            administrar = self.find_first_visible(
                [
                    lambda: self.page.get_by_role("button", name=re.compile(r"Administrar\s+Negocios", re.I)),
                    lambda: self.page.get_by_role("link", name=re.compile(r"Administrar\s+Negocios", re.I)),
                    lambda: self.page.get_by_text(re.compile(r"Administrar\s+Negocios", re.I)),
                ]
            )

        if administrar is None:
            result.fail("Could not find Administrar Negocios option.")
        else:
            self.click_and_wait(result, "Administrar Negocios", administrar)

        self.expect_visible(
            result,
            "Section 'Informacion General'",
            [lambda: self.page.get_by_text(re.compile(r"Informaci[oó]n\s+General", re.I))],
        )
        self.expect_visible(
            result,
            "Section 'Detalles de la Cuenta'",
            [lambda: self.page.get_by_text(re.compile(r"Detalles\s+de\s+la\s+Cuenta", re.I))],
        )
        self.expect_visible(
            result,
            "Section 'Tus Negocios'",
            [lambda: self.page.get_by_text(re.compile(r"Tus\s+Negocios", re.I))],
        )
        self.expect_visible(
            result,
            "Section 'Seccion Legal'",
            [lambda: self.page.get_by_text(re.compile(r"Secci[oó]n\s+Legal", re.I))],
        )
        self.screenshot(result, "04_administrar_negocios_page_full", full_page=True)

    def step_validate_informacion_general(self) -> None:
        result = StepResult(key="informacion_general", title="Validate Informacion General")
        self.results.append(result)

        self.expect_visible(
            result,
            "User name visible",
            [
                lambda: self.page.get_by_text(re.compile(r"Nombre", re.I)),
                lambda: self.page.get_by_text(re.compile(r"Juan|Lucas|Barbier|Garzon", re.I)),
            ],
        )
        self.expect_visible(
            result,
            "User email visible",
            [
                lambda: self.page.get_by_text("juanlucasbarbiergarzon@gmail.com", exact=False),
                lambda: self.page.get_by_text(re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")),
            ],
        )
        self.expect_visible(
            result,
            "Text 'BUSINESS PLAN'",
            [lambda: self.page.get_by_text(re.compile(r"BUSINESS\s+PLAN", re.I))],
        )
        self.expect_visible(
            result,
            "Button 'Cambiar Plan'",
            [lambda: self.page.get_by_role("button", name=re.compile(r"Cambiar\s+Plan", re.I))],
        )

    def step_validate_detalles_cuenta(self) -> None:
        result = StepResult(key="detalles_cuenta", title="Validate Detalles de la Cuenta")
        self.results.append(result)

        self.expect_visible(
            result,
            "'Cuenta creada' visible",
            [lambda: self.page.get_by_text(re.compile(r"Cuenta\s+creada", re.I))],
        )
        self.expect_visible(
            result,
            "'Estado activo' visible",
            [lambda: self.page.get_by_text(re.compile(r"Estado\s+activo|Activo", re.I))],
        )
        self.expect_visible(
            result,
            "'Idioma seleccionado' visible",
            [lambda: self.page.get_by_text(re.compile(r"Idioma\s+seleccionado", re.I))],
        )

    def step_validate_tus_negocios(self) -> None:
        result = StepResult(key="tus_negocios", title="Validate Tus Negocios")
        self.results.append(result)

        self.expect_visible(
            result,
            "Business list visible",
            [
                lambda: self.page.get_by_text(re.compile(r"Tus\s+Negocios", re.I)),
                lambda: self.page.locator("li, [role='listitem'], [role='row']").first,
            ],
        )
        self.expect_visible(
            result,
            "Button 'Agregar Negocio' exists",
            [lambda: self.page.get_by_role("button", name=re.compile(r"Agregar\s+Negocio", re.I))],
        )
        self.expect_visible(
            result,
            "Text 'Tienes 2 de 3 negocios' is visible",
            [lambda: self.page.get_by_text(re.compile(r"Tienes\s+2\s+de\s+3\s+negocios", re.I))],
        )

    def _validate_legal_link(
        self,
        *,
        step_key: str,
        step_title: str,
        link_text_pattern: str,
        heading_pattern: str,
        screenshot_name: str,
    ) -> None:
        result = StepResult(key=step_key, title=step_title)
        self.results.append(result)

        app_page = self.page
        origin_url = app_page.url
        legal_link = self.expect_visible(
            result,
            f"Legal link '{link_text_pattern}'",
            [
                lambda: app_page.get_by_role("link", name=re.compile(link_text_pattern, re.I)),
                lambda: app_page.get_by_role("button", name=re.compile(link_text_pattern, re.I)),
                lambda: app_page.get_by_text(re.compile(link_text_pattern, re.I)),
            ],
        )

        if legal_link is None:
            return

        target_page: Page = app_page
        opened_popup = False

        try:
            with self.context.expect_page(timeout=5000) as popup_info:
                legal_link.click()
            target_page = popup_info.value
            opened_popup = True
            self.wait_for_ui(target_page)
            result.pass_note("Legal page opened in new tab.")
        except TimeoutError:
            self.wait_for_ui(app_page)
            target_page = app_page
            result.pass_note("Legal page opened in current tab.")

        self.expect_visible(
            result,
            f"Heading '{heading_pattern}' visible",
            [lambda: target_page.get_by_text(re.compile(heading_pattern, re.I))],
        )

        body_text = ""
        try:
            body_text = target_page.locator("body").inner_text(timeout=self.timeout_ms).strip()
        except Exception:
            body_text = ""

        if len(body_text) < 80:
            result.fail("Legal content text is not sufficiently visible.")
        else:
            result.pass_note("Legal content text is visible.")

        result.final_url = target_page.url
        self.screenshot(result, screenshot_name, full_page=True, page=target_page)

        if opened_popup:
            target_page.close()
            app_page.bring_to_front()
            self.wait_for_ui(app_page)
        else:
            if app_page.url != origin_url:
                try:
                    app_page.go_back(wait_until="domcontentloaded")
                    self.wait_for_ui(app_page)
                except Exception as exc:
                    result.fail(f"Could not navigate back to application tab: {exc}")

    def step_validate_terminos_condiciones(self) -> None:
        self._validate_legal_link(
            step_key="terminos_condiciones",
            step_title="Validate Terminos y Condiciones",
            link_text_pattern=r"T[eé]rminos\s+y\s+Condiciones",
            heading_pattern=r"T[eé]rminos\s+y\s+Condiciones",
            screenshot_name="08_terminos_y_condiciones",
        )

    def step_validate_politica_privacidad(self) -> None:
        self._validate_legal_link(
            step_key="politica_privacidad",
            step_title="Validate Politica de Privacidad",
            link_text_pattern=r"Pol[ií]tica\s+de\s+Privacidad",
            heading_pattern=r"Pol[ií]tica\s+de\s+Privacidad",
            screenshot_name="09_politica_de_privacidad",
        )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="SaleADS Mi Negocio full workflow test.")
    parser.add_argument(
        "--url",
        default=None,
        help="Environment URL for SaleADS login. If omitted, uses SALEADS_BASE_URL env var.",
    )
    parser.add_argument("--headed", action="store_true", help="Run browser in headed mode.")
    parser.add_argument("--timeout-ms", type=int, default=DEFAULT_TIMEOUT_MS, help="Default action timeout.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    base_url = args.url
    base_url = base_url or os.environ.get("SALEADS_BASE_URL")
    if not base_url:
        print("ERROR: Missing URL. Provide --url or SALEADS_BASE_URL environment variable.")
        return 2

    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    artifacts_dir = Path("automation/saleads/artifacts") / run_id
    artifacts_dir.mkdir(parents=True, exist_ok=True)

    report_path = artifacts_dir / "final_report.json"

    with sync_playwright() as p:
        browser: Browser = p.chromium.launch(headless=not args.headed)
        context = browser.new_context(ignore_https_errors=True)
        page = context.new_page()
        page.set_default_timeout(args.timeout_ms)
        page.goto(base_url, wait_until="domcontentloaded")

        workflow = SaleadsMiNegocioWorkflow(
            page=page,
            context=context,
            artifacts_dir=artifacts_dir,
            timeout_ms=args.timeout_ms,
        )
        report = workflow.run()
        report_path.write_text(json.dumps(report, indent=2, ensure_ascii=True), encoding="utf-8")
        print(json.dumps(report, indent=2, ensure_ascii=True))

        context.close()
        browser.close()

    return 0


if __name__ == "__main__":
    sys.exit(main())
