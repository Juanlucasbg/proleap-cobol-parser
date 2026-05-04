#!/usr/bin/env python3
"""End-to-end validation of SaleADS Mi Negocio workflow."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any


STEP_LOGIN = "Login"
STEP_MENU = "Mi Negocio menu"
STEP_MODAL = "Agregar Negocio modal"
STEP_ADMIN_VIEW = "Administrar Negocios view"
STEP_INFO_GENERAL = "Información General"
STEP_ACCOUNT_DETAILS = "Detalles de la Cuenta"
STEP_TUS_NEGOCIOS = "Tus Negocios"
STEP_TERMS = "Términos y Condiciones"
STEP_PRIVACY = "Política de Privacidad"


@dataclass
class StepResult:
    status: str
    details: str


class SaleAdsMiNegocioWorkflow:
    def __init__(self, page: Any, context: Any, artifacts_dir: Path, account_email: str) -> None:
        self.page = page
        self.context = context
        self.artifacts_dir = artifacts_dir
        self.account_email = account_email
        self.report: dict[str, StepResult] = {}
        self.evidence: dict[str, Any] = {"screenshots": {}, "urls": {}}

    def run(self) -> int:
        self._step_login_with_google()
        if self._passed(STEP_LOGIN):
            self._step_open_mi_negocio_menu()
        else:
            self._mark_remaining_as_skipped(STEP_MENU)
            return self._finish()

        if self._passed(STEP_MENU):
            self._step_validate_agregar_modal()
        else:
            self._mark_remaining_as_skipped(STEP_MODAL)
            return self._finish()

        if self._passed(STEP_MODAL):
            self._step_open_administrar_negocios()
        else:
            self._mark_remaining_as_skipped(STEP_ADMIN_VIEW)
            return self._finish()

        if self._passed(STEP_ADMIN_VIEW):
            self._step_validate_informacion_general()
            self._step_validate_detalles_cuenta()
            self._step_validate_tus_negocios()
            self._step_validate_terminos()
            self._step_validate_politica_privacidad()
        else:
            self._mark_remaining_as_skipped(STEP_INFO_GENERAL)
            return self._finish()

        return self._finish()

    def _step_login_with_google(self) -> None:
        step = STEP_LOGIN
        try:
            self._wait_for_ui(self.page)
            button = self._find_clickable(
                self.page,
                [
                    "Sign in with Google",
                    "Iniciar sesión con Google",
                    "Ingresar con Google",
                    "Continuar con Google",
                    "Login with Google",
                ],
            )
            if button is None:
                raise AssertionError("No se encontró el botón de login con Google.")

            popup = None
            try:
                with self.page.expect_popup(timeout=8_000) as popup_info:
                    self._click_and_wait(button)
                popup = popup_info.value
            except Exception:
                self._click_and_wait(button)

            if popup:
                self._wait_for_ui(popup)
                self._select_google_account(popup)
                try:
                    popup.wait_for_event("close", timeout=20_000)
                except Exception:
                    pass
                self.page.bring_to_front()

            self._wait_for_ui(self.page, timeout_ms=30_000)
            sidebar_visible = self._is_sidebar_visible()
            if not sidebar_visible:
                raise AssertionError("No se detectó la interfaz principal (sidebar ausente).")

            shot = self._screenshot("01_dashboard_loaded.png", full_page=True)
            self.evidence["screenshots"][step] = [shot]
            self._pass(step, "Dashboard cargado y sidebar visible.")
        except Exception as exc:
            self._fail(step, f"Error en login con Google: {exc}")

    def _step_open_mi_negocio_menu(self) -> None:
        step = STEP_MENU
        try:
            sidebar = self._sidebar_locator()
            if sidebar is None:
                raise AssertionError("No se encontró el sidebar.")

            negocio = self._find_clickable(sidebar, ["Negocio"])
            if negocio:
                self._click_and_wait(negocio)

            mi_negocio = self._find_clickable(sidebar, ["Mi Negocio"])
            if mi_negocio is None:
                raise AssertionError("No se encontró 'Mi Negocio' en el menú lateral.")
            self._click_and_wait(mi_negocio)

            if not self._is_text_visible(self.page, "Agregar Negocio"):
                raise AssertionError("No se ve 'Agregar Negocio' tras expandir menú.")
            if not self._is_text_visible(self.page, "Administrar Negocios"):
                raise AssertionError("No se ve 'Administrar Negocios' tras expandir menú.")

            shot = self._screenshot("02_mi_negocio_menu_expanded.png", full_page=True)
            self.evidence["screenshots"][step] = [shot]
            self._pass(step, "Submenú Mi Negocio expandido correctamente.")
        except Exception as exc:
            self._fail(step, f"Error al abrir Mi Negocio: {exc}")

    def _step_validate_agregar_modal(self) -> None:
        step = STEP_MODAL
        try:
            agregar = self._find_clickable(self.page, ["Agregar Negocio"])
            if agregar is None:
                raise AssertionError("No se encontró botón/ítem 'Agregar Negocio'.")
            self._click_and_wait(agregar)

            modal_title = self.page.get_by_text("Crear Nuevo Negocio", exact=False)
            modal_title.wait_for(timeout=10_000)

            nombre_input = self.page.get_by_label("Nombre del Negocio", exact=False)
            if nombre_input.count() == 0:
                nombre_input = self.page.get_by_placeholder("Nombre del Negocio")
            if nombre_input.count() == 0:
                raise AssertionError("No se encontró input 'Nombre del Negocio'.")

            if not self._is_text_visible(self.page, "Tienes 2 de 3 negocios"):
                raise AssertionError("No se encontró el texto 'Tienes 2 de 3 negocios'.")
            if self._find_clickable(self.page, ["Cancelar"]) is None:
                raise AssertionError("No se encontró botón 'Cancelar'.")
            if self._find_clickable(self.page, ["Crear Negocio"]) is None:
                raise AssertionError("No se encontró botón 'Crear Negocio'.")

            nombre_input.first.click()
            nombre_input.first.fill("Negocio Prueba Automatización")
            self._wait_for_ui(self.page)

            shot = self._screenshot("03_agregar_negocio_modal.png", full_page=False)
            self.evidence["screenshots"][step] = [shot]

            cancelar = self._find_clickable(self.page, ["Cancelar"])
            if cancelar is not None:
                self._click_and_wait(cancelar)
            self._pass(step, "Modal de Crear Nuevo Negocio validado.")
        except Exception as exc:
            self._fail(step, f"Error al validar modal Agregar Negocio: {exc}")

    def _step_open_administrar_negocios(self) -> None:
        step = STEP_ADMIN_VIEW
        try:
            if not self._is_text_visible(self.page, "Administrar Negocios"):
                sidebar = self._sidebar_locator()
                if sidebar:
                    mi_negocio = self._find_clickable(sidebar, ["Mi Negocio"])
                    if mi_negocio:
                        self._click_and_wait(mi_negocio)

            administrar = self._find_clickable(self.page, ["Administrar Negocios"])
            if administrar is None:
                raise AssertionError("No se encontró 'Administrar Negocios'.")
            self._click_and_wait(administrar)

            required = [
                "Información General",
                "Detalles de la Cuenta",
                "Tus Negocios",
                "Sección Legal",
            ]
            for text in required:
                if not self._is_text_visible(self.page, text):
                    raise AssertionError(f"No se encontró la sección requerida: '{text}'.")

            shot = self._screenshot("04_administrar_negocios_full.png", full_page=True)
            self.evidence["screenshots"][step] = [shot]
            self._pass(step, "Vista Administrar Negocios cargada con secciones esperadas.")
        except Exception as exc:
            self._fail(step, f"Error al abrir Administrar Negocios: {exc}")

    def _step_validate_informacion_general(self) -> None:
        step = STEP_INFO_GENERAL
        try:
            if not self._is_text_visible(self.page, "Información General"):
                raise AssertionError("No se encuentra la sección Información General.")

            page_text = self.page.locator("body").inner_text()
            has_email = bool(re.search(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", page_text))
            if not has_email:
                raise AssertionError("No se visualiza un email de usuario.")

            if not self._is_text_visible(self.page, "BUSINESS PLAN"):
                raise AssertionError("No se visualiza 'BUSINESS PLAN'.")
            if self._find_clickable(self.page, ["Cambiar Plan"]) is None:
                raise AssertionError("No se visualiza el botón 'Cambiar Plan'.")

            general_section = self.page.get_by_text("Información General", exact=False).first
            section_text = general_section.locator("xpath=ancestor::*[self::section or self::div][1]").inner_text()
            non_empty_lines = [line.strip() for line in section_text.splitlines() if line.strip()]
            if len(non_empty_lines) < 3:
                raise AssertionError("No hay suficiente información visible para confirmar nombre de usuario.")

            self._pass(step, "Información General validada (nombre, email, plan y botón).")
        except Exception as exc:
            self._fail(step, f"Error en validación de Información General: {exc}")

    def _step_validate_detalles_cuenta(self) -> None:
        step = STEP_ACCOUNT_DETAILS
        try:
            for expected in ["Cuenta creada", "Estado activo", "Idioma seleccionado"]:
                if not self._is_text_visible(self.page, expected):
                    raise AssertionError(f"No se visualiza '{expected}'.")
            self._pass(step, "Detalles de la Cuenta validados correctamente.")
        except Exception as exc:
            self._fail(step, f"Error en Detalles de la Cuenta: {exc}")

    def _step_validate_tus_negocios(self) -> None:
        step = STEP_TUS_NEGOCIOS
        try:
            if not self._is_text_visible(self.page, "Tus Negocios"):
                raise AssertionError("No se visualiza la sección 'Tus Negocios'.")
            if self._find_clickable(self.page, ["Agregar Negocio"]) is None:
                raise AssertionError("No se visualiza botón 'Agregar Negocio' en Tus Negocios.")
            if not self._is_text_visible(self.page, "Tienes 2 de 3 negocios"):
                raise AssertionError("No se visualiza texto 'Tienes 2 de 3 negocios'.")
            self._pass(step, "Sección Tus Negocios validada.")
        except Exception as exc:
            self._fail(step, f"Error en validación de Tus Negocios: {exc}")

    def _step_validate_terminos(self) -> None:
        step = STEP_TERMS
        self._validate_legal_link(
            step_name=step,
            link_text="Términos y Condiciones",
            expected_heading="Términos y Condiciones",
            screenshot_name="05_terminos_y_condiciones.png",
        )

    def _step_validate_politica_privacidad(self) -> None:
        step = STEP_PRIVACY
        self._validate_legal_link(
            step_name=step,
            link_text="Política de Privacidad",
            expected_heading="Política de Privacidad",
            screenshot_name="06_politica_de_privacidad.png",
        )

    def _validate_legal_link(self, step_name: str, link_text: str, expected_heading: str, screenshot_name: str) -> None:
        app_page = self.page
        legal_page = app_page
        new_tab = False

        try:
            link = self._find_clickable(app_page, [link_text])
            if link is None:
                raise AssertionError(f"No se encontró el enlace '{link_text}'.")

            try:
                with self.context.expect_page(timeout=8_000) as new_page_info:
                    self._click_and_wait(link)
                legal_page = new_page_info.value
                new_tab = True
            except Exception:
                self._click_and_wait(link)
                legal_page = app_page

            self._wait_for_ui(legal_page)

            if not self._is_text_visible(legal_page, expected_heading):
                raise AssertionError(f"No se encontró heading '{expected_heading}' en la página legal.")

            legal_text = legal_page.locator("body").inner_text().strip()
            if len(legal_text) < 120:
                raise AssertionError("No se detectó contenido legal suficiente.")

            screenshot = self._screenshot(screenshot_name, page=legal_page, full_page=True)
            self.evidence["screenshots"][step_name] = [screenshot]
            self.evidence["urls"][step_name] = legal_page.url

            self._pass(step_name, f"Página legal '{expected_heading}' validada.")
        except Exception as exc:
            self._fail(step_name, f"Error en validación legal '{link_text}': {exc}")
        finally:
            try:
                if new_tab and legal_page != app_page:
                    legal_page.close()
                    app_page.bring_to_front()
                    self._wait_for_ui(app_page)
                elif not new_tab and app_page.url != "about:blank":
                    app_page.go_back(timeout=15_000)
                    self._wait_for_ui(app_page)
            except Exception:
                pass

    def _select_google_account(self, popup: Any) -> None:
        if self._is_text_visible(popup, self.account_email):
            account = self._find_clickable(popup, [self.account_email])
            if account:
                self._click_and_wait(account, page=popup)
                return

        if self._is_text_visible(popup, "Use another account"):
            use_other = self._find_clickable(popup, ["Use another account", "Usar otra cuenta"])
            if use_other:
                self._click_and_wait(use_other, page=popup)

        email_input = popup.locator("input[type='email']")
        if email_input.count() > 0:
            email_input.first.fill(self.account_email)
            self._wait_for_ui(popup)
            next_btn = self._find_clickable(popup, ["Next", "Siguiente"])
            if next_btn:
                self._click_and_wait(next_btn, page=popup)

    def _sidebar_locator(self) -> Any | None:
        candidates = [
            self.page.locator("aside"),
            self.page.locator("nav"),
            self.page.get_by_role("navigation"),
        ]
        for candidate in candidates:
            if candidate.count() > 0 and candidate.first.is_visible():
                return candidate.first
        return None

    def _is_sidebar_visible(self) -> bool:
        return self._sidebar_locator() is not None

    def _is_text_visible(self, page: Any, text: str, timeout_ms: int = 5_000) -> bool:
        try:
            page.get_by_text(text, exact=False).first.wait_for(timeout=timeout_ms)
            return True
        except Exception:
            return False

    def _find_clickable(self, page_or_locator: Any, texts: list[str]) -> Any | None:
        role_candidates = ("button", "link", "menuitem", "tab")
        for text in texts:
            for role in role_candidates:
                locator = page_or_locator.get_by_role(role, name=re.compile(re.escape(text), re.IGNORECASE))
                if locator.count() > 0 and locator.first.is_visible():
                    return locator.first

            text_locator = page_or_locator.get_by_text(text, exact=False)
            if text_locator.count() > 0 and text_locator.first.is_visible():
                return text_locator.first
        return None

    def _click_and_wait(self, locator: Any, page: Any | None = None) -> None:
        target_page = page or self.page
        locator.click(timeout=12_000)
        self._wait_for_ui(target_page)

    def _wait_for_ui(self, page: Any, timeout_ms: int = 15_000) -> None:
        try:
            page.wait_for_load_state("domcontentloaded", timeout=timeout_ms)
        except Exception:
            pass
        try:
            page.wait_for_load_state("networkidle", timeout=timeout_ms)
        except Exception:
            pass
        page.wait_for_timeout(700)

    def _screenshot(self, file_name: str, page: Any | None = None, full_page: bool = False) -> str:
        target_page = page or self.page
        target = self.artifacts_dir / file_name
        target_page.screenshot(path=str(target), full_page=full_page)
        return str(target)

    def _pass(self, step: str, details: str) -> None:
        self.report[step] = StepResult(status="PASS", details=details)

    def _fail(self, step: str, details: str) -> None:
        self.report[step] = StepResult(status="FAIL", details=details)

    def _passed(self, step: str) -> bool:
        return self.report.get(step, StepResult("FAIL", "")).status == "PASS"

    def _mark_remaining_as_skipped(self, from_step: str) -> None:
        ordered_steps = [
            STEP_LOGIN,
            STEP_MENU,
            STEP_MODAL,
            STEP_ADMIN_VIEW,
            STEP_INFO_GENERAL,
            STEP_ACCOUNT_DETAILS,
            STEP_TUS_NEGOCIOS,
            STEP_TERMS,
            STEP_PRIVACY,
        ]
        idx = ordered_steps.index(from_step)
        for step in ordered_steps[idx:]:
            if step not in self.report:
                self.report[step] = StepResult(status="FAIL", details="No ejecutado por falla en paso previo.")

    def _finish(self) -> int:
        output = {
            "timestamp_utc": datetime.now(UTC).isoformat(),
            "report": {key: vars(value) for key, value in self.report.items()},
            "evidence": self.evidence,
        }

        report_file = self.artifacts_dir / "final-report.json"
        report_file.write_text(json.dumps(output, indent=2, ensure_ascii=False), encoding="utf-8")

        print(json.dumps(output, indent=2, ensure_ascii=False))
        if all(step.status == "PASS" for step in self.report.values()):
            return 0
        return 1


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Valida el workflow completo del módulo Mi Negocio en SaleADS.ai.",
    )
    parser.add_argument(
        "--start-url",
        default=os.getenv("SALEADS_START_URL", "").strip(),
        help="URL de login del ambiente actual (dev/staging/prod). También puede venir por SALEADS_START_URL.",
    )
    parser.add_argument(
        "--headless",
        default=os.getenv("SALEADS_HEADLESS", "true").strip().lower(),
        choices=["true", "false"],
        help="Ejecutar headless (true/false).",
    )
    parser.add_argument(
        "--account-email",
        default=os.getenv("SALEADS_GOOGLE_ACCOUNT", "juanlucasbarbiergarzon@gmail.com"),
        help="Cuenta a seleccionar en el selector de Google.",
    )
    parser.add_argument(
        "--artifacts-dir",
        default=os.getenv("SALEADS_ARTIFACTS_DIR", "ui-tests/artifacts"),
        help="Directorio para guardar screenshots y reporte final.",
    )
    parser.add_argument(
        "--timeout-ms",
        type=int,
        default=int(os.getenv("SALEADS_TIMEOUT_MS", "90000")),
        help="Timeout base en milisegundos para navegación inicial.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    try:
        from playwright.sync_api import sync_playwright
    except ImportError:
        print(
            "Playwright no está instalado. Ejecuta:\n"
            "  pip3 install -r ui-tests/requirements.txt\n"
            "  python3 -m playwright install chromium",
            file=sys.stderr,
        )
        return 2

    artifacts_dir = Path(args.artifacts_dir)
    artifacts_dir.mkdir(parents=True, exist_ok=True)

    headless = args.headless == "true"
    start_url = args.start_url
    if not start_url:
        print(
            "No se especificó URL de inicio. Define --start-url o SALEADS_START_URL para el ambiente actual.",
            file=sys.stderr,
        )
        return 2

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=headless)
        context = browser.new_context(ignore_https_errors=True)
        page = context.new_page()
        page.set_default_timeout(args.timeout_ms)
        page.goto(start_url, wait_until="domcontentloaded")
        page.wait_for_timeout(1500)

        workflow = SaleAdsMiNegocioWorkflow(
            page=page,
            context=context,
            artifacts_dir=artifacts_dir,
            account_email=args.account_email,
        )
        exit_code = workflow.run()

        # Keep browser open shortly to flush artifacts in remote runners.
        time.sleep(1)
        context.close()
        browser.close()

    return exit_code


if __name__ == "__main__":
    sys.exit(main())
