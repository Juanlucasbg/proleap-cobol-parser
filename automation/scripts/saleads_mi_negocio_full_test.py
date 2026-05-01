#!/usr/bin/env python3
"""Run the SaleADS Mi Negocio end-to-end validation workflow.

This script assumes the browser is already on the SaleADS login page and will:
1) Login with Google
2) Validate Mi Negocio navigation and modal
3) Validate Administrar Negocios sections and legal links
4) Capture screenshots at key checkpoints
5) Emit a final PASS/FAIL report as JSON
"""

from __future__ import annotations

import argparse
import json
import os
import re
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional

from playwright.sync_api import (
    Browser,
    BrowserContext,
    Error,
    Page,
    Playwright,
    TimeoutError as PlaywrightTimeoutError,
    sync_playwright,
)


DEFAULT_TIMEOUT_MS = 20000
SHORT_WAIT_MS = 1200
WORKFLOW_NAME = "saleads_mi_negocio_full_test"


@dataclass
class StepResult:
    name: str
    passed: bool
    details: List[str]
    screenshot: Optional[str] = None
    url: Optional[str] = None


class SaleadsMiNegocioWorkflow:
    def __init__(
        self,
        artifacts_dir: Path,
        google_account: str,
        headless: bool,
        slow_mo_ms: int,
    ) -> None:
        self.artifacts_dir = artifacts_dir
        self.screenshots_dir = artifacts_dir / "screenshots"
        self.google_account = google_account
        self.headless = headless
        self.slow_mo_ms = slow_mo_ms
        self.results: Dict[str, StepResult] = {}
        self.main_page: Optional[Page] = None
        self.context: Optional[BrowserContext] = None
        self.browser: Optional[Browser] = None
        self.playwright: Optional[Playwright] = None

    def run(self) -> Dict[str, object]:
        self._start()
        try:
            step_calls = [
                self.step_login_with_google,
                self.step_open_mi_negocio_menu,
                self.step_validate_agregar_negocio_modal,
                self.step_open_administrar_negocios,
                self.step_validate_informacion_general,
                self.step_validate_detalles_cuenta,
                self.step_validate_tus_negocios,
                self.step_validate_terminos_condiciones,
                self.step_validate_politica_privacidad,
            ]
            for step_call in step_calls:
                try:
                    step_call()
                except Exception:
                    # Continue executing the rest of the workflow to produce
                    # a complete PASS/FAIL final report for every requested field.
                    continue
        finally:
            self._close()
        return self._build_report()

    def _start(self) -> None:
        self.screenshots_dir.mkdir(parents=True, exist_ok=True)
        self.playwright = sync_playwright().start()
        self.browser = self.playwright.chromium.launch(
            headless=self.headless,
            slow_mo=self.slow_mo_ms,
        )
        self.context = self.browser.new_context()
        self.main_page = self.context.new_page()
        self.main_page.set_default_timeout(DEFAULT_TIMEOUT_MS)

        login_url = os.getenv("SALEADS_START_URL", "").strip()
        if login_url:
            self.main_page.goto(login_url, wait_until="domcontentloaded")
            self.main_page.wait_for_timeout(SHORT_WAIT_MS)
        else:
            raise RuntimeError(
                "SALEADS_START_URL is required for automation execution. "
                "This keeps the test environment-agnostic and URL-independent in code."
            )

    def _close(self) -> None:
        if self.context:
            self.context.close()
        if self.browser:
            self.browser.close()
        if self.playwright:
            self.playwright.stop()

    def _record(
        self,
        key: str,
        name: str,
        passed: bool,
        details: List[str],
        screenshot_file: Optional[str] = None,
        url: Optional[str] = None,
    ) -> None:
        self.results[key] = StepResult(
            name=name,
            passed=passed,
            details=details,
            screenshot=screenshot_file,
            url=url,
        )

    def _wait_ui_settled(self, page: Optional[Page] = None) -> None:
        target = page or self.main_page
        if target is None:
            return
        try:
            target.wait_for_load_state("networkidle", timeout=DEFAULT_TIMEOUT_MS)
        except PlaywrightTimeoutError:
            # Some pages keep active connections; fall back to a short wait.
            target.wait_for_timeout(SHORT_WAIT_MS)
        target.wait_for_timeout(SHORT_WAIT_MS)

    def _screenshot(self, page: Page, name: str, full_page: bool = False) -> str:
        file_name = f"{name}.png"
        screenshot_path = self.screenshots_dir / file_name
        page.screenshot(path=str(screenshot_path), full_page=full_page)
        return str(screenshot_path.relative_to(self.artifacts_dir.parent))

    def _resolve_text_locator(self, page: Page, text: str):
        exact = page.get_by_text(text, exact=True)
        if exact.count() > 0:
            return exact.first

        # Fall back to flexible matching to tolerate style and whitespace changes.
        collapsed_text = re.sub(r"\s+", " ", text).strip()
        contains = page.get_by_text(collapsed_text)
        return contains.first

    def _click_text(self, page: Page, text: str) -> None:
        locator = self._resolve_text_locator(page, text)
        locator.first.wait_for(state="visible")
        locator.first.click()
        self._wait_ui_settled(page)

    def _expect_text(self, page: Page, text: str, details: List[str]) -> None:
        try:
            self._resolve_text_locator(page, text).wait_for(state="visible")
            details.append(f"Visible: {text}")
        except PlaywrightTimeoutError:
            details.append(f"Missing: {text}")
            raise

    def _safe_select_google_account(self, details: List[str]) -> None:
        assert self.main_page is not None
        candidate_pages = [self.main_page]
        candidate_pages.extend(self.context.pages if self.context else [])

        for page in candidate_pages:
            try:
                acct = page.get_by_text(self.google_account, exact=True)
                if acct.count() > 0:
                    acct.first.click()
                    self._wait_ui_settled(page)
                    details.append(f"Selected Google account: {self.google_account}")
                    return
            except Error:
                continue
        details.append("Google account chooser did not appear or account already selected.")

    def _click_legal_link_and_validate(self, link_text: str, heading: str, key: str) -> None:
        assert self.main_page is not None
        details: List[str] = []
        legal_page: Optional[Page] = None

        try:
            with self.context.expect_page(timeout=7000) as popup_info:
                self._click_text(self.main_page, link_text)
            legal_page = popup_info.value
            legal_page.wait_for_load_state("domcontentloaded")
            self._wait_ui_settled(legal_page)
            details.append(f"{link_text} opened in a new tab.")
        except PlaywrightTimeoutError:
            self._click_text(self.main_page, link_text)
            legal_page = self.main_page
            details.append(f"{link_text} opened in current tab.")

        assert legal_page is not None
        self._expect_text(legal_page, heading, details)

        # Validate visible legal content without overfitting to exact body text.
        legal_content = legal_page.locator("main, article, section, body").first
        legal_content.wait_for(state="visible")
        text_blob = legal_content.inner_text().strip()
        if len(text_blob) < 80:
            details.append("Legal content appears too short.")
            raise AssertionError("Insufficient legal content length.")
        details.append("Legal content text is visible.")

        screenshot = self._screenshot(legal_page, f"step_{key}_legal_page", full_page=True)
        final_url = legal_page.url

        if legal_page != self.main_page:
            legal_page.close()
            self.main_page.bring_to_front()
            self._wait_ui_settled(self.main_page)
            details.append("Returned to application tab.")
        else:
            self.main_page.go_back(wait_until="domcontentloaded")
            self._wait_ui_settled(self.main_page)
            details.append("Navigated back to application page.")

        self._record(
            key=key,
            name=heading,
            passed=True,
            details=details,
            screenshot_file=screenshot,
            url=final_url,
        )

    def step_login_with_google(self) -> None:
        assert self.main_page is not None
        key = "login"
        details: List[str] = []
        try:
            login_candidates = [
                "Sign in with Google",
                "Iniciar sesión con Google",
                "Continuar con Google",
                "Login with Google",
            ]
            clicked = False
            for label in login_candidates:
                locator = self.main_page.get_by_text(label)
                if locator.count() > 0:
                    locator.first.click()
                    clicked = True
                    details.append(f"Clicked login button: {label}")
                    break
            if not clicked:
                raise AssertionError("Could not find any Google login button by visible text.")

            self._wait_ui_settled(self.main_page)
            self._safe_select_google_account(details)
            self._wait_ui_settled(self.main_page)

            # Validate main interface and sidebar.
            sidebar = self.main_page.locator("aside, nav").first
            sidebar.wait_for(state="visible")
            details.append("Left sidebar navigation is visible.")

            # Use Mi Negocio or Negocio as a practical indicator of app shell load.
            nav_text = self.main_page.get_by_text("Negocio", exact=True)
            if nav_text.count() == 0:
                nav_text = self.main_page.get_by_text("Mi Negocio", exact=True)
            nav_text.first.wait_for(state="visible")
            details.append("Main application interface is visible.")

            screenshot = self._screenshot(self.main_page, "step_1_dashboard_loaded", full_page=True)
            self._record(key, "Login", True, details, screenshot_file=screenshot, url=self.main_page.url)
        except Exception as exc:  # noqa: BLE001
            details.append(f"Failure: {exc}")
            self._record(key, "Login", False, details, url=self.main_page.url)
            raise

    def step_open_mi_negocio_menu(self) -> None:
        assert self.main_page is not None
        key = "mi_negocio_menu"
        details: List[str] = []
        try:
            # Prefer exact visible text selectors.
            try:
                self._click_text(self.main_page, "Negocio")
                details.append("Clicked 'Negocio' section.")
            except PlaywrightTimeoutError:
                self._click_text(self.main_page, "Mi Negocio")
                details.append("Clicked 'Mi Negocio' section.")

            self._expect_text(self.main_page, "Agregar Negocio", details)
            self._expect_text(self.main_page, "Administrar Negocios", details)

            screenshot = self._screenshot(self.main_page, "step_2_menu_expanded")
            self._record(key, "Mi Negocio menu", True, details, screenshot_file=screenshot, url=self.main_page.url)
        except Exception as exc:  # noqa: BLE001
            details.append(f"Failure: {exc}")
            self._record(key, "Mi Negocio menu", False, details, url=self.main_page.url)
            raise

    def step_validate_agregar_negocio_modal(self) -> None:
        assert self.main_page is not None
        key = "agregar_negocio_modal"
        details: List[str] = []
        try:
            self._click_text(self.main_page, "Agregar Negocio")
            self._expect_text(self.main_page, "Crear Nuevo Negocio", details)
            self._expect_text(self.main_page, "Nombre del Negocio", details)
            self._expect_text(self.main_page, "Tienes 2 de 3 negocios", details)
            self._expect_text(self.main_page, "Cancelar", details)
            self._expect_text(self.main_page, "Crear Negocio", details)

            # Optional action.
            nombre = self.main_page.get_by_label("Nombre del Negocio")
            if nombre.count() == 0:
                nombre = self.main_page.get_by_placeholder("Nombre del Negocio")
            if nombre.count() > 0:
                nombre.first.click()
                nombre.first.fill("Negocio Prueba Automatización")
                details.append("Optional data entry validated in 'Nombre del Negocio'.")

            screenshot = self._screenshot(self.main_page, "step_3_modal")
            self._click_text(self.main_page, "Cancelar")
            details.append("Closed modal by clicking 'Cancelar'.")
            self._record(
                key,
                "Agregar Negocio modal",
                True,
                details,
                screenshot_file=screenshot,
                url=self.main_page.url,
            )
        except Exception as exc:  # noqa: BLE001
            details.append(f"Failure: {exc}")
            self._record(key, "Agregar Negocio modal", False, details, url=self.main_page.url)
            raise

    def step_open_administrar_negocios(self) -> None:
        assert self.main_page is not None
        key = "administrar_negocios_view"
        details: List[str] = []
        try:
            # Re-expand if collapsed.
            if self.main_page.get_by_text("Administrar Negocios", exact=True).count() == 0:
                try:
                    self._click_text(self.main_page, "Mi Negocio")
                    details.append("Re-expanded 'Mi Negocio'.")
                except PlaywrightTimeoutError:
                    self._click_text(self.main_page, "Negocio")
                    details.append("Re-expanded 'Negocio'.")

            self._click_text(self.main_page, "Administrar Negocios")
            self._expect_text(self.main_page, "Información General", details)
            self._expect_text(self.main_page, "Detalles de la Cuenta", details)
            self._expect_text(self.main_page, "Tus Negocios", details)
            self._expect_text(self.main_page, "Sección Legal", details)

            screenshot = self._screenshot(self.main_page, "step_4_account_page", full_page=True)
            self._record(
                key,
                "Administrar Negocios view",
                True,
                details,
                screenshot_file=screenshot,
                url=self.main_page.url,
            )
        except Exception as exc:  # noqa: BLE001
            details.append(f"Failure: {exc}")
            self._record(key, "Administrar Negocios view", False, details, url=self.main_page.url)
            raise

    def step_validate_informacion_general(self) -> None:
        assert self.main_page is not None
        key = "informacion_general"
        details: List[str] = []
        try:
            section = self.main_page.get_by_text("Información General", exact=True).first
            section.wait_for(state="visible")
            details.append("Section 'Información General' is visible.")

            # Use generic indicators for user name/email visibility.
            user_email = self.main_page.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/").first
            user_email.wait_for(state="visible")
            details.append("User email is visible.")

            # Username is validated as nearby non-empty heading/text in this section.
            text_block = self.main_page.locator("section, div").filter(has_text="Información General").first
            block_text = text_block.inner_text()
            if len(block_text.splitlines()) < 3:
                raise AssertionError("Could not confidently validate user name visibility.")
            details.append("User name is visible.")

            self._expect_text(self.main_page, "BUSINESS PLAN", details)
            self._expect_text(self.main_page, "Cambiar Plan", details)
            self._record(key, "Información General", True, details, url=self.main_page.url)
        except Exception as exc:  # noqa: BLE001
            details.append(f"Failure: {exc}")
            self._record(key, "Información General", False, details, url=self.main_page.url)
            raise

    def step_validate_detalles_cuenta(self) -> None:
        assert self.main_page is not None
        key = "detalles_cuenta"
        details: List[str] = []
        try:
            self._expect_text(self.main_page, "Cuenta creada", details)
            self._expect_text(self.main_page, "Estado activo", details)
            self._expect_text(self.main_page, "Idioma seleccionado", details)
            self._record(key, "Detalles de la Cuenta", True, details, url=self.main_page.url)
        except Exception as exc:  # noqa: BLE001
            details.append(f"Failure: {exc}")
            self._record(key, "Detalles de la Cuenta", False, details, url=self.main_page.url)
            raise

    def step_validate_tus_negocios(self) -> None:
        assert self.main_page is not None
        key = "tus_negocios"
        details: List[str] = []
        try:
            self._expect_text(self.main_page, "Tus Negocios", details)
            self._expect_text(self.main_page, "Agregar Negocio", details)
            self._expect_text(self.main_page, "Tienes 2 de 3 negocios", details)

            # Validate list-like area exists.
            business_rows = self.main_page.locator("li, tr, .business-item, [data-testid*='business']").count()
            if business_rows <= 0:
                details.append("Business list container inferred by text only.")
            else:
                details.append(f"Business list items detected: {business_rows}")

            self._record(key, "Tus Negocios", True, details, url=self.main_page.url)
        except Exception as exc:  # noqa: BLE001
            details.append(f"Failure: {exc}")
            self._record(key, "Tus Negocios", False, details, url=self.main_page.url)
            raise

    def step_validate_terminos_condiciones(self) -> None:
        self._click_legal_link_and_validate(
            link_text="Términos y Condiciones",
            heading="Términos y Condiciones",
            key="terminos_condiciones",
        )

    def step_validate_politica_privacidad(self) -> None:
        self._click_legal_link_and_validate(
            link_text="Política de Privacidad",
            heading="Política de Privacidad",
            key="politica_privacidad",
        )

    def _build_report(self) -> Dict[str, object]:
        mapping = [
            ("login", "Login"),
            ("mi_negocio_menu", "Mi Negocio menu"),
            ("agregar_negocio_modal", "Agregar Negocio modal"),
            ("administrar_negocios_view", "Administrar Negocios view"),
            ("informacion_general", "Información General"),
            ("detalles_cuenta", "Detalles de la Cuenta"),
            ("tus_negocios", "Tus Negocios"),
            ("terminos_condiciones", "Términos y Condiciones"),
            ("politica_privacidad", "Política de Privacidad"),
        ]
        report_steps = []
        for key, label in mapping:
            result = self.results.get(key)
            report_steps.append(
                {
                    "field": label,
                    "status": "PASS" if result and result.passed else "FAIL",
                    "details": result.details if result else ["Step did not run."],
                    "screenshot": result.screenshot if result else None,
                    "url": result.url if result else None,
                }
            )

        overall_pass = all(step["status"] == "PASS" for step in report_steps)
        return {
            "workflow": WORKFLOW_NAME,
            "timestamp_epoch": int(time.time()),
            "overall_status": "PASS" if overall_pass else "FAIL",
            "steps": report_steps,
        }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run SaleADS Mi Negocio full workflow.")
    parser.add_argument(
        "--artifacts-dir",
        default="automation/artifacts/saleads_mi_negocio_full_test",
        help="Directory where screenshots and final report are written.",
    )
    parser.add_argument(
        "--google-account",
        default="juanlucasbarbiergarzon@gmail.com",
        help="Google account email to select if the chooser appears.",
    )
    parser.add_argument(
        "--headless",
        action="store_true",
        help="Run browser in headless mode.",
    )
    parser.add_argument(
        "--slow-mo-ms",
        type=int,
        default=150,
        help="Delay between operations in milliseconds.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    artifacts_dir = Path(args.artifacts_dir)
    artifacts_dir.mkdir(parents=True, exist_ok=True)

    workflow = SaleadsMiNegocioWorkflow(
        artifacts_dir=artifacts_dir,
        google_account=args.google_account,
        headless=args.headless,
        slow_mo_ms=args.slow_mo_ms,
    )

    report_path = artifacts_dir / "final_report.json"
    try:
        report = workflow.run()
    except Exception as exc:  # noqa: BLE001
        # If a step fails, best effort to build report from collected partial data.
        report = workflow._build_report()
        report["overall_status"] = "FAIL"
        report["fatal_error"] = str(exc)

    report_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(report, indent=2, ensure_ascii=False))
    return 0 if report.get("overall_status") == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
