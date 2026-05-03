#!/usr/bin/env python3
"""End-to-end validation for the SaleADS.ai Mi Negocio workflow."""

from __future__ import annotations

import json
import os
import re
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from playwright.sync_api import Locator, Page, Playwright, TimeoutError, sync_playwright


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


@dataclass
class StepResult:
    status: str = "FAIL"
    checks: List[Dict[str, str]] = field(default_factory=list)
    evidence: Dict[str, str] = field(default_factory=dict)

    def add_check(self, name: str, passed: bool, details: str = "") -> None:
        self.checks.append(
            {
                "check": name,
                "status": "PASS" if passed else "FAIL",
                "details": details,
            }
        )

    def finalize(self) -> None:
        self.status = "PASS" if self.checks and all(c["status"] == "PASS" for c in self.checks) else "FAIL"


class MiNegocioWorkflowTest:
    def __init__(
        self,
        playwright: Playwright,
        start_url: str,
        google_email: str,
        artifacts_dir: Path,
        headless: bool = True,
        slow_mo_ms: int = 0,
    ) -> None:
        self.playwright = playwright
        self.start_url = start_url
        self.google_email = google_email
        self.artifacts_dir = artifacts_dir
        self.headless = headless
        self.slow_mo_ms = slow_mo_ms

        self.browser = None
        self.context = None
        self.page = None

        self.results: Dict[str, StepResult] = {field: StepResult() for field in REPORT_FIELDS}
        self.legal_urls: Dict[str, str] = {}

    def run(self) -> Dict[str, object]:
        self._start_browser()
        try:
            self.step_login_with_google()
            self.step_open_mi_negocio_menu()
            self.step_validate_agregar_negocio_modal()
            self.step_open_administrar_negocios()
            self.step_validate_informacion_general()
            self.step_validate_detalles_de_cuenta()
            self.step_validate_tus_negocios()
            self.step_validate_terminos_condiciones()
            self.step_validate_politica_privacidad()

            for field_name in REPORT_FIELDS:
                self.results[field_name].finalize()

            report = {
                "generated_at_utc": datetime.now(timezone.utc).isoformat(),
                "start_url": self.start_url,
                "google_email": self.google_email,
                "screenshots_dir": str(self.artifacts_dir),
                "final_urls": self.legal_urls,
                "steps": {
                    field_name: {
                        "status": self.results[field_name].status,
                        "checks": self.results[field_name].checks,
                        "evidence": self.results[field_name].evidence,
                    }
                    for field_name in REPORT_FIELDS
                },
            }
            return report
        finally:
            if self.browser is not None:
                self.browser.close()

    def _start_browser(self) -> None:
        self.browser = self.playwright.chromium.launch(headless=self.headless, slow_mo=self.slow_mo_ms)
        self.context = self.browser.new_context(locale="es-ES")
        self.page = self.context.new_page()
        self.page.goto(self.start_url, wait_until="domcontentloaded")
        self._wait_ui(self.page)

    @staticmethod
    def _wait_ui(page: Page) -> None:
        try:
            page.wait_for_load_state("domcontentloaded", timeout=10000)
        except TimeoutError:
            pass
        try:
            page.wait_for_load_state("networkidle", timeout=7000)
        except TimeoutError:
            pass

    def _save_screenshot(self, page: Page, filename: str, full_page: bool = False) -> str:
        path = self.artifacts_dir / filename
        page.screenshot(path=str(path), full_page=full_page)
        return str(path)

    @staticmethod
    def _candidate_locators(scope: Page | Locator, label: str) -> List[Locator]:
        return [
            scope.get_by_role("button", name=label, exact=True),
            scope.get_by_role("button", name=label, exact=False),
            scope.get_by_role("link", name=label, exact=True),
            scope.get_by_role("link", name=label, exact=False),
            scope.get_by_role("menuitem", name=label, exact=True),
            scope.get_by_role("menuitem", name=label, exact=False),
            scope.get_by_role("tab", name=label, exact=True),
            scope.get_by_role("tab", name=label, exact=False),
            scope.get_by_text(label, exact=True),
            scope.get_by_text(label, exact=False),
        ]

    def _find_visible_locator(
        self,
        labels: List[str],
        scope: Optional[Page | Locator] = None,
        timeout_ms: int = 1800,
    ) -> Tuple[Optional[Locator], Optional[str]]:
        search_scope: Page | Locator = scope if scope is not None else self.page
        for label in labels:
            for candidate in self._candidate_locators(search_scope, label):
                try:
                    first = candidate.first
                    first.wait_for(state="visible", timeout=timeout_ms)
                    return first, label
                except TimeoutError:
                    continue
        return None, None

    def _click_text(self, labels: List[str], step_key: str, check_name: str) -> bool:
        locator, label_used = self._find_visible_locator(labels)
        if locator is None:
            self.results[step_key].add_check(check_name, False, f"No se encontró ningún elemento: {labels}")
            return False
        locator.click()
        self._wait_ui(self.page)
        self.results[step_key].add_check(check_name, True, f"Clic exitoso usando texto: '{label_used}'")
        return True

    def _assert_visible_text(self, page: Page, step_key: str, check_name: str, labels: List[str]) -> bool:
        locator, label_used = self._find_visible_locator(labels, scope=page, timeout_ms=2500)
        passed = locator is not None
        details = f"Visible: '{label_used}'" if passed else f"No visible: {labels}"
        self.results[step_key].add_check(check_name, passed, details)
        return passed

    def _assert_regex_text(self, page: Page, step_key: str, check_name: str, pattern: str) -> bool:
        regex = re.compile(pattern)
        try:
            locator = page.get_by_text(regex).first
            locator.wait_for(state="visible", timeout=3000)
            self.results[step_key].add_check(check_name, True, f"Coincide con regex: {pattern}")
            return True
        except TimeoutError:
            self.results[step_key].add_check(check_name, False, f"No coincide con regex: {pattern}")
            return False

    def _is_sidebar_visible(self) -> bool:
        selectors = ["aside", "nav", "[class*='sidebar']", "[id*='sidebar']"]
        for selector in selectors:
            try:
                if self.page.locator(selector).first.is_visible():
                    return True
            except Exception:
                continue
        return False

    def _main_app_loaded(self) -> bool:
        if not self._is_sidebar_visible():
            return False
        locator, _ = self._find_visible_locator(["Negocio", "Mi Negocio", "Dashboard", "Inicio"], timeout_ms=1200)
        return locator is not None

    def step_login_with_google(self) -> None:
        step = "Login"
        click_ok = self._click_text(
            [
                "Sign in with Google",
                "Iniciar sesión con Google",
                "Continuar con Google",
                "Login with Google",
                "Google",
            ],
            step,
            "Click en login con Google",
        )
        if not click_ok:
            return

        # If Google account selector appears (same tab or popup), choose requested account.
        popup_page = None
        known_pages = set(self.context.pages)
        for _ in range(10):
            current_pages = set(self.context.pages)
            new_pages = [page for page in current_pages if page not in known_pages]
            if new_pages:
                popup_page = new_pages[-1]
                break
            time.sleep(0.4)

        google_page = popup_page if popup_page is not None else self.page
        self._wait_ui(google_page)

        email_selected = False
        google_selector_visible = False
        try:
            # Detect account chooser to enforce selection only when it is actually shown.
            chooser = google_page.get_by_text(re.compile(r"(elige una cuenta|choose an account)", re.I)).first
            chooser.wait_for(state="visible", timeout=1800)
            google_selector_visible = True
        except TimeoutError:
            google_selector_visible = False

        email_locator, _ = self._find_visible_locator([self.google_email], scope=google_page, timeout_ms=2500)
        if email_locator is not None:
            email_locator.click()
            self._wait_ui(google_page)
            email_selected = True

        self.results[step].add_check(
            "Seleccionar cuenta de Google",
            (not google_selector_visible) or email_selected,
            (
                "Selector de cuenta no visible; el flujo continuó sin selección manual."
                if not google_selector_visible
                else f"Selector visible. Cuenta seleccionada: {email_selected}"
            ),
        )

        # Wait for dashboard/main app to load.
        loaded = False
        for _ in range(45):
            if self._main_app_loaded():
                loaded = True
                break
            time.sleep(1)

        self.results[step].add_check(
            "Interfaz principal visible",
            loaded,
            "Se detectó interfaz principal y navegación lateral." if loaded else "No se detectó dashboard a tiempo.",
        )
        self.results[step].add_check(
            "Sidebar visible",
            self._is_sidebar_visible(),
            "La barra lateral está visible." if self._is_sidebar_visible() else "No se detectó barra lateral.",
        )

        screenshot = self._save_screenshot(self.page, "step-1-dashboard-load.png")
        self.results[step].evidence["dashboard_screenshot"] = screenshot

    def step_open_mi_negocio_menu(self) -> None:
        step = "Mi Negocio menu"
        self._click_text(["Negocio"], step, "Click sección Negocio")
        self._click_text(["Mi Negocio"], step, "Click opción Mi Negocio")

        self._assert_visible_text(self.page, step, "Submenú expandido", ["Agregar Negocio", "Administrar Negocios"])
        self._assert_visible_text(self.page, step, "Opción Agregar Negocio visible", ["Agregar Negocio"])
        self._assert_visible_text(self.page, step, "Opción Administrar Negocios visible", ["Administrar Negocios"])

        screenshot = self._save_screenshot(self.page, "step-2-mi-negocio-expanded.png")
        self.results[step].evidence["expanded_menu_screenshot"] = screenshot

    def step_validate_agregar_negocio_modal(self) -> None:
        step = "Agregar Negocio modal"
        if not self._click_text(["Agregar Negocio"], step, "Click Agregar Negocio"):
            return

        self._assert_visible_text(self.page, step, "Título del modal", ["Crear Nuevo Negocio"])
        self._assert_visible_text(self.page, step, "Campo Nombre del Negocio", ["Nombre del Negocio"])
        self._assert_visible_text(self.page, step, "Texto de límite de negocios", ["Tienes 2 de 3 negocios"])
        self._assert_visible_text(self.page, step, "Botón Cancelar", ["Cancelar"])
        self._assert_visible_text(self.page, step, "Botón Crear Negocio", ["Crear Negocio"])

        screenshot = self._save_screenshot(self.page, "step-3-agregar-negocio-modal.png")
        self.results[step].evidence["modal_screenshot"] = screenshot

        field_locator, _ = self._find_visible_locator(["Nombre del Negocio"])
        if field_locator is not None:
            try:
                field_locator.click()
                self._wait_ui(self.page)
                input_locator = self.page.locator("input").first
                input_locator.fill("Negocio Prueba Automatización")
                self.results[step].add_check("Acción opcional: escribir nombre", True, "Texto escrito en input.")
            except Exception as exc:
                self.results[step].add_check("Acción opcional: escribir nombre", False, str(exc))
        else:
            self.results[step].add_check("Acción opcional: escribir nombre", False, "No se encontró el campo.")

        self._click_text(["Cancelar"], step, "Cerrar modal con Cancelar")

    def step_open_administrar_negocios(self) -> None:
        step = "Administrar Negocios view"

        # Re-expand if needed.
        add_visible, _ = self._find_visible_locator(["Agregar Negocio"], timeout_ms=1200)
        admin_visible, _ = self._find_visible_locator(["Administrar Negocios"], timeout_ms=1200)
        if add_visible is None or admin_visible is None:
            self._click_text(["Mi Negocio", "Negocio"], step, "Re-expand Mi Negocio")

        self._click_text(["Administrar Negocios"], step, "Click Administrar Negocios")

        self._assert_visible_text(self.page, step, "Sección Información General", ["Información General"])
        self._assert_visible_text(self.page, step, "Sección Detalles de la Cuenta", ["Detalles de la Cuenta"])
        self._assert_visible_text(self.page, step, "Sección Tus Negocios", ["Tus Negocios"])
        self._assert_visible_text(self.page, step, "Sección Sección Legal", ["Sección Legal"])

        screenshot = self._save_screenshot(self.page, "step-4-administrar-negocios-full.png", full_page=True)
        self.results[step].evidence["account_page_screenshot"] = screenshot

    def step_validate_informacion_general(self) -> None:
        step = "Información General"

        self._assert_regex_text(
            self.page,
            step,
            "Nombre de usuario visible",
            r"[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+){2,}",
        )
        self._assert_regex_text(
            self.page,
            step,
            "Correo de usuario visible",
            r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}",
        )
        self._assert_visible_text(self.page, step, "Texto BUSINESS PLAN", ["BUSINESS PLAN"])
        self._assert_visible_text(self.page, step, "Botón Cambiar Plan", ["Cambiar Plan"])

    def step_validate_detalles_de_cuenta(self) -> None:
        step = "Detalles de la Cuenta"
        self._assert_visible_text(self.page, step, "Cuenta creada visible", ["Cuenta creada"])
        self._assert_visible_text(self.page, step, "Estado activo visible", ["Estado activo"])
        self._assert_visible_text(self.page, step, "Idioma seleccionado visible", ["Idioma seleccionado"])

    def step_validate_tus_negocios(self) -> None:
        step = "Tus Negocios"
        self._assert_visible_text(self.page, step, "Lista de negocios visible", ["Tus Negocios"])
        self._assert_visible_text(self.page, step, "Botón Agregar Negocio existe", ["Agregar Negocio"])
        self._assert_visible_text(self.page, step, "Texto de límite visible", ["Tienes 2 de 3 negocios"])

    def _open_legal_page_and_validate(
        self,
        report_key: str,
        link_labels: List[str],
        expected_heading_labels: List[str],
        screenshot_name: str,
        url_key: str,
    ) -> None:
        locator, label_used = self._find_visible_locator(link_labels, timeout_ms=3000)
        if locator is None:
            self.results[report_key].add_check("Click link legal", False, f"No se encontró: {link_labels}")
            return

        known_pages = set(self.context.pages)
        locator.click()
        self._wait_ui(self.page)
        self.results[report_key].add_check("Click link legal", True, f"Clic en '{label_used}'")

        legal_page = self.page
        opened_new_tab = False
        for _ in range(14):
            current_pages = set(self.context.pages)
            new_pages = [page for page in current_pages if page not in known_pages]
            if new_pages:
                legal_page = new_pages[-1]
                opened_new_tab = True
                break
            time.sleep(0.35)

        self._wait_ui(legal_page)
        self._assert_visible_text(legal_page, report_key, "Encabezado legal visible", expected_heading_labels)

        content_visible = False
        try:
            body_text = legal_page.locator("body").inner_text(timeout=5000)
            content_visible = len(body_text.strip()) > 180
        except Exception:
            content_visible = False
        self.results[report_key].add_check(
            "Contenido legal visible",
            content_visible,
            "Se detectó texto legal en la página." if content_visible else "No se detectó contenido suficiente.",
        )

        screenshot = self._save_screenshot(legal_page, screenshot_name, full_page=True)
        self.results[report_key].evidence["legal_page_screenshot"] = screenshot

        final_url = legal_page.url
        self.legal_urls[url_key] = final_url
        self.results[report_key].evidence["final_url"] = final_url

        if opened_new_tab:
            legal_page.close()
            self.page.bring_to_front()
            self._wait_ui(self.page)
            self.results[report_key].add_check("Cleanup regresar a app", True, "Se cerró la nueva pestaña.")
        else:
            try:
                self.page.go_back(wait_until="domcontentloaded")
                self._wait_ui(self.page)
                self.results[report_key].add_check("Cleanup regresar a app", True, "Se regresó con navegación atrás.")
            except Exception as exc:
                self.results[report_key].add_check("Cleanup regresar a app", False, str(exc))

    def step_validate_terminos_condiciones(self) -> None:
        self._open_legal_page_and_validate(
            report_key="Términos y Condiciones",
            link_labels=["Términos y Condiciones", "Terminos y Condiciones"],
            expected_heading_labels=["Términos y Condiciones", "Terminos y Condiciones"],
            screenshot_name="step-8-terminos-condiciones.png",
            url_key="terminos_y_condiciones",
        )

    def step_validate_politica_privacidad(self) -> None:
        self._open_legal_page_and_validate(
            report_key="Política de Privacidad",
            link_labels=["Política de Privacidad", "Politica de Privacidad"],
            expected_heading_labels=["Política de Privacidad", "Politica de Privacidad"],
            screenshot_name="step-9-politica-privacidad.png",
            url_key="politica_de_privacidad",
        )


def print_summary(report: Dict[str, object]) -> None:
    print("\n==== FINAL REPORT: saleads_mi_negocio_full_test ====")
    steps = report["steps"]
    for field in REPORT_FIELDS:
        print(f"- {field}: {steps[field]['status']}")

    print("\nFinal URLs:")
    for key, value in report.get("final_urls", {}).items():
        print(f"  {key}: {value}")

    print(f"\nScreenshots directory: {report['screenshots_dir']}")


def main() -> None:
    start_url = os.getenv("SALEADS_START_URL", "").strip()
    if not start_url:
        raise ValueError("Define SALEADS_START_URL con la URL del login del entorno actual.")

    google_email = os.getenv("GOOGLE_ACCOUNT_EMAIL", "juanlucasbarbiergarzon@gmail.com")
    headless = os.getenv("HEADLESS", "false").lower() in {"1", "true", "yes"}
    slow_mo_ms = int(os.getenv("SLOW_MO_MS", "200"))

    timestamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    artifacts_dir = Path(os.getenv("ARTIFACTS_DIR", f"e2e/saleads-mi-negocio/artifacts/{timestamp}"))
    artifacts_dir.mkdir(parents=True, exist_ok=True)

    with sync_playwright() as playwright:
        workflow = MiNegocioWorkflowTest(
            playwright=playwright,
            start_url=start_url,
            google_email=google_email,
            artifacts_dir=artifacts_dir,
            headless=headless,
            slow_mo_ms=slow_mo_ms,
        )
        report = workflow.run()

    report_path = artifacts_dir / "report.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print_summary(report)
    print(f"\nJSON report: {report_path}")


if __name__ == "__main__":
    main()
