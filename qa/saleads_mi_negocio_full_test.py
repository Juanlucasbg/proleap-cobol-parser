#!/usr/bin/env python3
"""
End-to-end validation for the SaleADS.ai "Mi Negocio" workflow.

This script is environment-agnostic:
- It never hardcodes a SaleADS domain.
- It can run against any environment URL passed at runtime.
"""

from __future__ import annotations

import argparse
import json
import os
import re
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable

from playwright.sync_api import BrowserContext, Page, TimeoutError as PlaywrightTimeoutError, sync_playwright


class SaleadsMiNegocioFullTest:
    ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com"

    def __init__(self, page: Page, context: BrowserContext, output_dir: Path) -> None:
        self.page = page
        self.context = context
        self.output_dir = output_dir
        self.results: dict[str, dict[str, str]] = {}
        self.legal_urls: dict[str, str] = {}

    @staticmethod
    def _text_regex(text: str) -> re.Pattern[str]:
        return re.compile(rf"{re.escape(text)}", re.IGNORECASE)

    def _timestamped_name(self, label: str) -> str:
        safe_label = re.sub(r"[^a-zA-Z0-9_-]+", "_", label).strip("_").lower()
        stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        return f"{stamp}_{safe_label}.png"

    def _wait_ui_ready(self, target_page: Page | None = None) -> None:
        page = target_page or self.page
        page.wait_for_load_state("domcontentloaded")
        try:
            page.wait_for_load_state("networkidle", timeout=7000)
        except PlaywrightTimeoutError:
            # Some environments keep long-lived connections; DOM readiness is enough.
            pass
        page.wait_for_timeout(750)

    def _try_click_by_text(self, texts: list[str]) -> bool:
        locators = []
        for text in texts:
            regex = self._text_regex(text)
            locators.extend(
                [
                    self.page.get_by_role("button", name=regex).first,
                    self.page.get_by_role("link", name=regex).first,
                    self.page.get_by_role("menuitem", name=regex).first,
                    self.page.get_by_role("tab", name=regex).first,
                    self.page.get_by_text(regex).first,
                ]
            )

        for locator in locators:
            try:
                locator.wait_for(state="visible", timeout=1200)
                locator.click()
                self._wait_ui_ready()
                return True
            except PlaywrightTimeoutError:
                continue
        return False

    def _assert_visible_text(self, text: str, timeout: int = 8000) -> None:
        self.page.get_by_text(self._text_regex(text)).first.wait_for(state="visible", timeout=timeout)

    def _capture(self, label: str, full_page: bool = False, target_page: Page | None = None) -> str:
        self.output_dir.mkdir(parents=True, exist_ok=True)
        screenshot_path = self.output_dir / self._timestamped_name(label)
        (target_page or self.page).screenshot(path=str(screenshot_path), full_page=full_page)
        return str(screenshot_path)

    def _record_step(self, key: str, name: str, passed: bool, details: str) -> None:
        self.results[key] = {
            "name": name,
            "status": "PASS" if passed else "FAIL",
            "details": details,
        }

    def _run_step(self, key: str, name: str, action: Callable[[], str]) -> None:
        try:
            details = action()
            self._record_step(key, name, True, details)
        except Exception as exc:  # noqa: BLE001 - we want full step-level reporting.
            self._record_step(key, name, False, str(exc))

    def step_1_login(self) -> str:
        self._wait_ui_ready()

        clicked = self._try_click_by_text(
            [
                "Sign in with Google",
                "Iniciar sesión con Google",
                "Continuar con Google",
                "Login with Google",
                "Google",
            ]
        )
        if not clicked:
            raise AssertionError("No se encontró un botón/enlace de login con Google.")

        # If Google account selector appears, choose the requested account.
        if self.page.get_by_text(self._text_regex(self.ACCOUNT_EMAIL)).first.is_visible(timeout=4000):
            self.page.get_by_text(self._text_regex(self.ACCOUNT_EMAIL)).first.click()
            self._wait_ui_ready()
        else:
            # Fallback: account chooser may appear in a popup.
            for popup in self.context.pages:
                if popup is self.page:
                    continue
                try:
                    popup.bring_to_front()
                    popup.get_by_text(self._text_regex(self.ACCOUNT_EMAIL)).first.click(timeout=4000)
                    self._wait_ui_ready(target_page=popup)
                    break
                except Exception:
                    continue
                finally:
                    self.page.bring_to_front()

        # Validate app interface and left sidebar.
        sidebar_found = False
        sidebar_locators = [
            self.page.get_by_role("navigation").first,
            self.page.locator("aside").first,
            self.page.get_by_text(self._text_regex("Mi Negocio")).first,
            self.page.get_by_text(self._text_regex("Negocio")).first,
        ]
        for locator in sidebar_locators:
            try:
                locator.wait_for(state="visible", timeout=7000)
                sidebar_found = True
                break
            except PlaywrightTimeoutError:
                continue

        if not sidebar_found:
            raise AssertionError("No se detectó interfaz principal con sidebar visible tras login.")

        screenshot = self._capture("step1_dashboard_loaded")
        return f"Dashboard y sidebar visibles. Screenshot: {screenshot}"

    def step_2_open_mi_negocio_menu(self) -> str:
        self._wait_ui_ready()

        # Try opening parent "Negocio" section first, then "Mi Negocio".
        self._try_click_by_text(["Negocio"])
        clicked_mi_negocio = self._try_click_by_text(["Mi Negocio"])
        if not clicked_mi_negocio:
            raise AssertionError("No se pudo hacer click en 'Mi Negocio'.")

        self._assert_visible_text("Agregar Negocio")
        self._assert_visible_text("Administrar Negocios")

        screenshot = self._capture("step2_menu_expanded")
        return f"Submenú de Mi Negocio expandido y validado. Screenshot: {screenshot}"

    def step_3_validate_agregar_negocio_modal(self) -> str:
        clicked = self._try_click_by_text(["Agregar Negocio"])
        if not clicked:
            raise AssertionError("No se pudo abrir 'Agregar Negocio'.")

        self._assert_visible_text("Crear Nuevo Negocio")
        self._assert_visible_text("Nombre del Negocio")
        self._assert_visible_text("Tienes 2 de 3 negocios")
        self._assert_visible_text("Cancelar")
        self._assert_visible_text("Crear Negocio")

        # Optional actions requested by the flow.
        name_field = self.page.get_by_label(self._text_regex("Nombre del Negocio")).first
        if not name_field.is_visible(timeout=2500):
            name_field = self.page.get_by_placeholder(self._text_regex("Nombre del Negocio")).first
        name_field.click()
        name_field.fill("Negocio Prueba Automatización")
        self._wait_ui_ready()

        screenshot = self._capture("step3_agregar_negocio_modal")
        self._try_click_by_text(["Cancelar"])
        return f"Modal validado y cerrado. Screenshot: {screenshot}"

    def step_4_open_administrar_negocios(self) -> str:
        # Re-open menu in case it collapsed after modal close.
        self._try_click_by_text(["Mi Negocio"])

        clicked = self._try_click_by_text(["Administrar Negocios"])
        if not clicked:
            raise AssertionError("No se pudo acceder a 'Administrar Negocios'.")

        self._assert_visible_text("Información General")
        self._assert_visible_text("Detalles de la Cuenta")
        self._assert_visible_text("Tus Negocios")
        self._assert_visible_text("Sección Legal")

        screenshot = self._capture("step4_administrar_negocios_view", full_page=True)
        return f"Vista de cuenta validada. Screenshot: {screenshot}"

    def step_5_validate_informacion_general(self) -> str:
        self._assert_visible_text("Información General")
        self._assert_visible_text("BUSINESS PLAN")
        self._assert_visible_text("Cambiar Plan")

        full_text = self.page.inner_text("body")
        email_match = re.search(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", full_text)
        if not email_match:
            raise AssertionError("No se encontró email visible en 'Información General'.")

        lines = [line.strip() for line in full_text.splitlines() if line.strip()]
        ignored = {"información general", "business plan", "cambiar plan"}
        likely_name = next(
            (
                line
                for line in lines
                if "@" not in line
                and line.lower() not in ignored
                and len(line.split()) >= 2
                and len(line) <= 80
            ),
            None,
        )
        if not likely_name:
            raise AssertionError("No se detectó un nombre de usuario visible.")

        return f"Nombre visible: '{likely_name}'. Email visible: '{email_match.group(0)}'."

    def step_6_validate_detalles_cuenta(self) -> str:
        self._assert_visible_text("Detalles de la Cuenta")
        self._assert_visible_text("Cuenta creada")
        self._assert_visible_text("Estado activo")
        self._assert_visible_text("Idioma seleccionado")
        return "Se validaron 'Cuenta creada', 'Estado activo' e 'Idioma seleccionado'."

    def step_7_validate_tus_negocios(self) -> str:
        self._assert_visible_text("Tus Negocios")
        self._assert_visible_text("Agregar Negocio")
        self._assert_visible_text("Tienes 2 de 3 negocios")
        return "Lista de negocios y contador de límite validados."

    def _validate_legal_page(self, link_text: str, expected_heading: str, screenshot_label: str) -> str:
        app_url_before = self.page.url
        opened_new_tab = False
        target_page = self.page

        try:
            with self.context.expect_page(timeout=5000) as new_page_event:
                clicked = self._try_click_by_text([link_text])
                if not clicked:
                    raise AssertionError(f"No se pudo hacer click en '{link_text}'.")
            target_page = new_page_event.value
            opened_new_tab = True
        except PlaywrightTimeoutError:
            # Link click was performed but no new tab appeared, so it likely opened in same tab.
            target_page = self.page

        target_page.bring_to_front()
        self._wait_ui_ready(target_page=target_page)

        target_page.get_by_text(self._text_regex(expected_heading)).first.wait_for(state="visible", timeout=12000)
        legal_text = target_page.locator("main, body").inner_text()
        if len(re.sub(r"\s+", "", legal_text)) < 120:
            raise AssertionError(f"No se detectó contenido legal suficiente para '{link_text}'.")

        screenshot = self._capture(screenshot_label, full_page=True, target_page=target_page)
        self.legal_urls[link_text] = target_page.url

        if opened_new_tab:
            target_page.close()
            self.page.bring_to_front()
            self._wait_ui_ready()
        else:
            # Cleanup for same-tab behavior.
            if self.page.url != app_url_before:
                self.page.go_back()
                self._wait_ui_ready()

        return f"{link_text} validado. URL final: {self.legal_urls[link_text]}. Screenshot: {screenshot}"

    def step_8_validate_terminos(self) -> str:
        return self._validate_legal_page(
            link_text="Términos y Condiciones",
            expected_heading="Términos y Condiciones",
            screenshot_label="step8_terminos_y_condiciones",
        )

    def step_9_validate_privacidad(self) -> str:
        return self._validate_legal_page(
            link_text="Política de Privacidad",
            expected_heading="Política de Privacidad",
            screenshot_label="step9_politica_de_privacidad",
        )

    def run(self) -> dict[str, object]:
        self._run_step("login", "Login", self.step_1_login)
        self._run_step("mi_negocio_menu", "Mi Negocio menu", self.step_2_open_mi_negocio_menu)
        self._run_step("agregar_modal", "Agregar Negocio modal", self.step_3_validate_agregar_negocio_modal)
        self._run_step("administrar_view", "Administrar Negocios view", self.step_4_open_administrar_negocios)
        self._run_step("informacion_general", "Información General", self.step_5_validate_informacion_general)
        self._run_step("detalles_cuenta", "Detalles de la Cuenta", self.step_6_validate_detalles_cuenta)
        self._run_step("tus_negocios", "Tus Negocios", self.step_7_validate_tus_negocios)
        self._run_step("terminos", "Términos y Condiciones", self.step_8_validate_terminos)
        self._run_step("privacidad", "Política de Privacidad", self.step_9_validate_privacidad)

        report = {
            "test_name": "saleads_mi_negocio_full_test",
            "generated_at_utc": datetime.now(timezone.utc).isoformat(),
            "results": self.results,
            "legal_urls": self.legal_urls,
        }
        return report


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="SaleADS Mi Negocio full workflow validation.")
    parser.add_argument(
        "--url",
        default=os.getenv("SALEADS_START_URL", "").strip(),
        help="SaleADS login URL for the target environment (dev/staging/prod).",
    )
    parser.add_argument(
        "--artifacts-dir",
        default="qa/artifacts/saleads_mi_negocio_full_test",
        help="Directory where screenshots and report JSON will be stored.",
    )
    parser.add_argument(
        "--headless",
        action="store_true",
        help="Run browser in headless mode (default: headed).",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not args.url:
        raise SystemExit("Missing --url (or SALEADS_START_URL env var).")

    artifacts_dir = Path(args.artifacts_dir)
    artifacts_dir.mkdir(parents=True, exist_ok=True)

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=args.headless)
        context = browser.new_context()
        page = context.new_page()
        page.goto(args.url, wait_until="domcontentloaded")

        runner = SaleadsMiNegocioFullTest(page=page, context=context, output_dir=artifacts_dir)
        report = runner.run()

        report_path = artifacts_dir / "report.json"
        report_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
        print(json.dumps(report, indent=2, ensure_ascii=False))
        print(f"\nReporte guardado en: {report_path}")

        context.close()
        browser.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
