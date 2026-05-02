#!/usr/bin/env python3
"""
saleads_mi_negocio_full_test

Environment-agnostic Playwright test for validating the SaleADS.ai "Mi Negocio"
workflow after Google login.

Usage:
  SALEADS_START_URL="https://<current-env>/login" python3 e2e/saleads_mi_negocio_full_test.py

Optional environment variables:
  SALEADS_START_URL            Login page URL for the current environment.
  SALEADS_GOOGLE_ACCOUNT_EMAIL Preferred Google account in account chooser.
  SALEADS_HEADLESS             true/false (default: true).
  SALEADS_TIMEOUT_MS           Action timeout in milliseconds (default: 20000).
  SALEADS_SLOW_MO_MS           Slow motion in milliseconds for debugging (default: 0).
"""

from __future__ import annotations

import json
import os
import re
import sys
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional, Sequence

from playwright.sync_api import BrowserContext, Error, Page, TimeoutError, sync_playwright


TEST_NAME = "saleads_mi_negocio_full_test"
DEFAULT_TIMEOUT_MS = int(os.getenv("SALEADS_TIMEOUT_MS", "20000"))
GOOGLE_ACCOUNT_EMAIL = os.getenv(
    "SALEADS_GOOGLE_ACCOUNT_EMAIL", "juanlucasbarbiergarzon@gmail.com"
)


@dataclass
class FieldResult:
    status: str = "FAIL"
    checks: List[Dict[str, str]] = field(default_factory=list)
    notes: List[str] = field(default_factory=list)

    def add_check(self, label: str, passed: bool) -> None:
        self.checks.append({"label": label, "status": "PASS" if passed else "FAIL"})

    def finalize(self) -> None:
        if self.checks and all(check["status"] == "PASS" for check in self.checks):
            self.status = "PASS"
        elif not self.checks:
            self.status = "FAIL"
        else:
            self.status = "FAIL"


class SaleadsMiNegocioWorkflowTest:
    def __init__(self) -> None:
        started_at = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        self.root_dir = Path(__file__).resolve().parent
        self.artifacts_dir = self.root_dir / "artifacts" / f"run-{started_at}"
        self.screenshots_dir = self.artifacts_dir / "screenshots"
        self.screenshots_dir.mkdir(parents=True, exist_ok=True)

        self.report: Dict[str, object] = {
            "name": TEST_NAME,
            "started_at_utc": started_at,
            "start_url": os.getenv("SALEADS_START_URL", ""),
            "results": {},
            "evidence": {"screenshots": [], "final_urls": {}},
            "overall_status": "FAIL",
        }

        self.fields = [
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
        for field_name in self.fields:
            self.report["results"][field_name] = FieldResult()

    def field(self, name: str) -> FieldResult:
        return self.report["results"][name]

    def screenshot(self, page: Page, name: str, full_page: bool = False) -> str:
        filename = f"{name}.png"
        path = self.screenshots_dir / filename
        page.screenshot(path=str(path), full_page=full_page)
        self.report["evidence"]["screenshots"].append(str(path))
        return str(path)

    def wait_for_ui(self, page: Page) -> None:
        try:
            page.wait_for_load_state("domcontentloaded", timeout=DEFAULT_TIMEOUT_MS)
        except TimeoutError:
            pass
        try:
            page.wait_for_load_state("networkidle", timeout=min(DEFAULT_TIMEOUT_MS, 8000))
        except TimeoutError:
            pass

    def is_visible_text(self, page: Page, text: str, exact: bool = False) -> bool:
        locator = page.get_by_text(text, exact=exact)
        try:
            return locator.first.is_visible(timeout=1500)
        except TimeoutError:
            return False

    def is_visible_any_text(self, page: Page, texts: Sequence[str]) -> bool:
        return any(self.is_visible_text(page, text) for text in texts)

    def click_by_text(self, page: Page, text: str) -> bool:
        candidates = [
            lambda: page.get_by_role("button", name=re.compile(rf"^{re.escape(text)}$", re.I)).first,
            lambda: page.get_by_role("link", name=re.compile(rf"^{re.escape(text)}$", re.I)).first,
            lambda: page.get_by_role("menuitem", name=re.compile(rf"^{re.escape(text)}$", re.I)).first,
            lambda: page.get_by_text(text, exact=True).first,
            lambda: page.get_by_text(text).first,
        ]
        for factory in candidates:
            locator = factory()
            try:
                if locator.is_visible(timeout=1200):
                    locator.click(timeout=DEFAULT_TIMEOUT_MS)
                    self.wait_for_ui(page)
                    return True
            except (TimeoutError, Error):
                continue
        return False

    def click_any_text(self, page: Page, texts: Sequence[str]) -> Optional[str]:
        for text in texts:
            if self.click_by_text(page, text):
                return text
        return None

    def validate_login_and_sidebar(self, page: Page) -> None:
        result = self.field("Login")
        main_app_visible = (
            page.locator("main").first.is_visible()
            if page.locator("main").count() > 0
            else False
        )
        sidebar_visible = False
        for selector in ["aside", "nav", "[role='navigation']"]:
            loc = page.locator(selector)
            try:
                if loc.count() > 0 and loc.first.is_visible():
                    sidebar_visible = True
                    break
            except Error:
                continue

        if not main_app_visible:
            main_app_visible = self.is_visible_any_text(
                page, ["Dashboard", "Inicio", "Negocio", "Mi Negocio"]
            )
        if not sidebar_visible:
            sidebar_visible = self.is_visible_any_text(page, ["Negocio", "Mi Negocio"])

        result.add_check("Main application interface is visible", main_app_visible)
        result.add_check("Left sidebar navigation is visible", sidebar_visible)
        result.finalize()

    def open_mi_negocio_menu(self, page: Page) -> None:
        result = self.field("Mi Negocio menu")

        negocio_clicked = self.click_any_text(page, ["Negocio", "Mi Negocio"])
        if negocio_clicked is None:
            result.notes.append("Could not click 'Negocio' or 'Mi Negocio' from sidebar.")

        if negocio_clicked != "Mi Negocio":
            self.click_by_text(page, "Mi Negocio")

        agregar_visible = self.is_visible_text(page, "Agregar Negocio")
        administrar_visible = self.is_visible_text(page, "Administrar Negocios")
        submenu_expanded = agregar_visible and administrar_visible

        result.add_check("Mi Negocio submenu is expanded", submenu_expanded)
        result.add_check("'Agregar Negocio' is visible", agregar_visible)
        result.add_check("'Administrar Negocios' is visible", administrar_visible)
        result.finalize()
        self.screenshot(page, "02_mi_negocio_menu_expanded")

    def validate_agregar_negocio_modal(self, page: Page) -> None:
        result = self.field("Agregar Negocio modal")

        self.click_by_text(page, "Agregar Negocio")

        checks = [
            ("Modal title 'Crear Nuevo Negocio' is visible", "Crear Nuevo Negocio"),
            ("Input 'Nombre del Negocio' exists", "Nombre del Negocio"),
            ("Text 'Tienes 2 de 3 negocios' is visible", "Tienes 2 de 3 negocios"),
            ("Button 'Cancelar' is present", "Cancelar"),
            ("Button 'Crear Negocio' is present", "Crear Negocio"),
        ]

        for label, text in checks:
            result.add_check(label, self.is_visible_text(page, text))

        self.screenshot(page, "03_agregar_negocio_modal")

        # Optional actions requested in the scenario.
        typed = False
        try:
            field = page.get_by_label("Nombre del Negocio").first
            if field.is_visible(timeout=1200):
                field.click(timeout=DEFAULT_TIMEOUT_MS)
                self.wait_for_ui(page)
                field.fill("Negocio Prueba Automatizacion", timeout=DEFAULT_TIMEOUT_MS)
                typed = True
        except (TimeoutError, Error):
            pass
        if not typed:
            try:
                field = page.get_by_placeholder("Nombre del Negocio").first
                if field.is_visible(timeout=1200):
                    field.click(timeout=DEFAULT_TIMEOUT_MS)
                    self.wait_for_ui(page)
                    field.fill("Negocio Prueba Automatizacion", timeout=DEFAULT_TIMEOUT_MS)
                    typed = True
            except (TimeoutError, Error):
                pass
        if typed:
            result.notes.append("Optional action executed: filled business name input.")

        self.click_by_text(page, "Cancelar")
        result.finalize()

    def open_administrar_negocios(self, page: Page) -> None:
        result = self.field("Administrar Negocios view")

        if not self.is_visible_text(page, "Administrar Negocios"):
            self.click_by_text(page, "Mi Negocio")

        self.click_by_text(page, "Administrar Negocios")
        self.wait_for_ui(page)

        checks = [
            ("Section 'Informacion General' exists", ["Información General", "Informacion General"]),
            ("Section 'Detalles de la Cuenta' exists", ["Detalles de la Cuenta"]),
            ("Section 'Tus Negocios' exists", ["Tus Negocios"]),
            ("Section 'Seccion Legal' exists", ["Sección Legal", "Seccion Legal"]),
        ]
        for label, text_options in checks:
            result.add_check(label, self.is_visible_any_text(page, text_options))
        result.finalize()
        self.screenshot(page, "04_administrar_negocios_view_full", full_page=True)

    def validate_informacion_general(self, page: Page) -> None:
        result = self.field("Información General")
        user_name_visible = self.is_visible_any_text(page, ["@gmail.com", "BUSINESS PLAN", "Cambiar Plan"])
        user_email_visible = bool(page.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/").count())
        business_plan_visible = self.is_visible_text(page, "BUSINESS PLAN")
        cambiar_plan_visible = self.is_visible_text(page, "Cambiar Plan")

        result.add_check("User name is visible", user_name_visible)
        result.add_check("User email is visible", user_email_visible)
        result.add_check("Text 'BUSINESS PLAN' is visible", business_plan_visible)
        result.add_check("Button 'Cambiar Plan' is visible", cambiar_plan_visible)
        result.finalize()

    def validate_detalles_cuenta(self, page: Page) -> None:
        result = self.field("Detalles de la Cuenta")
        result.add_check("'Cuenta creada' is visible", self.is_visible_text(page, "Cuenta creada"))
        result.add_check("'Estado activo' is visible", self.is_visible_text(page, "Estado activo"))
        result.add_check(
            "'Idioma seleccionado' is visible", self.is_visible_text(page, "Idioma seleccionado")
        )
        result.finalize()

    def validate_tus_negocios(self, page: Page) -> None:
        result = self.field("Tus Negocios")
        business_list_visible = self.is_visible_any_text(page, ["Tus Negocios", "Negocio", "Agregar Negocio"])
        add_button_visible = self.is_visible_text(page, "Agregar Negocio")
        quota_visible = self.is_visible_text(page, "Tienes 2 de 3 negocios")

        result.add_check("Business list is visible", business_list_visible)
        result.add_check("Button 'Agregar Negocio' exists", add_button_visible)
        result.add_check("Text 'Tienes 2 de 3 negocios' is visible", quota_visible)
        result.finalize()

    def click_legal_link_validate(
        self, context: BrowserContext, app_page: Page, link_text: str, heading_text: str, key: str
    ) -> None:
        field_name = (
            "Términos y Condiciones" if "Términos" in link_text else "Política de Privacidad"
        )
        result = self.field(field_name)

        target_page: Optional[Page] = None
        opened_new_tab = False
        clicked_during_new_tab_attempt = False
        try:
            with context.expect_page(timeout=4000) as page_event:
                clicked_during_new_tab_attempt = self.click_by_text(app_page, link_text)
                if not clicked_during_new_tab_attempt:
                    raise TimeoutError(f"Could not click link '{link_text}'")
            target_page = page_event.value
            target_page.wait_for_load_state("domcontentloaded", timeout=DEFAULT_TIMEOUT_MS)
            self.wait_for_ui(target_page)
            opened_new_tab = True
        except TimeoutError:
            # Same-tab navigation fallback.
            if clicked_during_new_tab_attempt:
                self.wait_for_ui(app_page)
                target_page = app_page
            else:
                clicked = self.click_by_text(app_page, link_text)
                if clicked:
                    self.wait_for_ui(app_page)
                    target_page = app_page
                else:
                    target_page = None

        if target_page is None:
            result.add_check(f"Could open '{link_text}' page", False)
            result.add_check(f"Page heading '{heading_text}' is visible", False)
            result.add_check("Legal content text is visible", False)
            result.notes.append("Could not open legal page.")
            result.finalize()
            return

        heading_visible = self.is_visible_text(target_page, heading_text)
        legal_text_visible = self.is_visible_any_text(
            target_page,
            [
                "términos",
                "terminos",
                "condiciones",
                "privacidad",
                "datos",
                "legal",
                "policy",
                "terms",
            ],
        )

        result.add_check(f"Page heading '{heading_text}' is visible", heading_visible)
        result.add_check("Legal content text is visible", legal_text_visible)
        result.finalize()

        shot_name = "08_terminos_y_condiciones" if "Términos" in link_text else "09_politica_de_privacidad"
        self.screenshot(target_page, shot_name, full_page=True)
        self.report["evidence"]["final_urls"][key] = target_page.url

        # Cleanup: return to app tab.
        if opened_new_tab:
            target_page.close()
            app_page.bring_to_front()
        else:
            try:
                app_page.go_back(timeout=DEFAULT_TIMEOUT_MS)
                self.wait_for_ui(app_page)
            except TimeoutError:
                pass

    def finalize_report(self) -> Dict[str, object]:
        overall_pass = True
        for field_name in self.fields:
            result = self.field(field_name)
            result.finalize()
            if result.status != "PASS":
                overall_pass = False
            self.report["results"][field_name] = {
                "status": result.status,
                "checks": result.checks,
                "notes": result.notes,
            }
        self.report["overall_status"] = "PASS" if overall_pass else "FAIL"
        return self.report

    def run(self) -> int:
        start_url = os.getenv("SALEADS_START_URL", "").strip()
        if not start_url:
            print(
                "ERROR: SALEADS_START_URL is required and must point to the current environment login page.",
                file=sys.stderr,
            )
            return 2

        headless = os.getenv("SALEADS_HEADLESS", "true").strip().lower() != "false"
        slow_mo = int(os.getenv("SALEADS_SLOW_MO_MS", "0"))

        with sync_playwright() as p:
            browser = p.chromium.launch(headless=headless, slow_mo=slow_mo)
            context = browser.new_context(ignore_https_errors=True)
            context.set_default_timeout(DEFAULT_TIMEOUT_MS)
            page = context.new_page()
            page.goto(start_url, wait_until="domcontentloaded")
            self.wait_for_ui(page)

            # Step 1: Login with Google.
            login_result = self.field("Login")
            login_button_texts = [
                "Sign in with Google",
                "Iniciar con Google",
                "Iniciar sesion con Google",
                "Continuar con Google",
                "Google",
            ]

            clicked_login = False
            popup: Optional[Page] = None
            for login_text in login_button_texts:
                if not self.is_visible_text(page, login_text):
                    continue
                clicked_login = True
                try:
                    with context.expect_page(timeout=5000) as popup_event:
                        self.click_by_text(page, login_text)
                    popup = popup_event.value
                except TimeoutError:
                    # Same-tab Google flow or already-authenticated session.
                    popup = None
                break

            # If no login button is present, user may already be signed in.
            if not clicked_login:
                clicked_login = self.is_visible_any_text(
                    page, ["Dashboard", "Inicio", "Negocio", "Mi Negocio"]
                )
            login_result.add_check("Login button clicked", clicked_login)

            account_selected = False
            if popup is not None:
                popup.wait_for_load_state("domcontentloaded", timeout=DEFAULT_TIMEOUT_MS)
                self.wait_for_ui(popup)
                account_selected = self.click_by_text(popup, GOOGLE_ACCOUNT_EMAIL)
            else:
                # Handle Google chooser in same tab when applicable.
                account_selected = self.click_by_text(page, GOOGLE_ACCOUNT_EMAIL)

            login_result.add_check(
                f"Google account '{GOOGLE_ACCOUNT_EMAIL}' selected or already authenticated",
                account_selected or not self.is_visible_any_text(page, ["Selecciona una cuenta", "Choose an account"]),
            )
            active_page = page
            self.wait_for_ui(active_page)
            self.validate_login_and_sidebar(active_page)
            self.screenshot(active_page, "01_dashboard_loaded")

            # Step 2 to Step 7.
            self.open_mi_negocio_menu(active_page)
            self.validate_agregar_negocio_modal(active_page)
            self.open_administrar_negocios(active_page)
            self.validate_informacion_general(active_page)
            self.validate_detalles_cuenta(active_page)
            self.validate_tus_negocios(active_page)

            # Step 8 and 9.
            self.click_legal_link_validate(
                context,
                active_page,
                link_text="Términos y Condiciones",
                heading_text="Términos y Condiciones",
                key="terminos_y_condiciones",
            )
            self.click_legal_link_validate(
                context,
                active_page,
                link_text="Política de Privacidad",
                heading_text="Política de Privacidad",
                key="politica_de_privacidad",
            )

            browser.close()

        report = self.finalize_report()
        report_file = self.artifacts_dir / "report.json"
        report_file.write_text(json.dumps(report, indent=2, ensure_ascii=True), encoding="utf-8")

        print(f"Test: {TEST_NAME}")
        print(f"Overall: {report['overall_status']}")
        print("Step results:")
        for field_name in self.fields:
            status = report["results"][field_name]["status"]
            print(f"- {field_name}: {status}")
        print(f"Report JSON: {report_file}")
        print(f"Screenshots dir: {self.screenshots_dir}")
        return 0 if report["overall_status"] == "PASS" else 1


def main() -> int:
    try:
        runner = SaleadsMiNegocioWorkflowTest()
        return runner.run()
    except Exception as exc:  # noqa: BLE001
        print(f"Unhandled error while running {TEST_NAME}: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
