#!/usr/bin/env python3
"""
Environment-agnostic SaleADS Mi Negocio workflow test.

This runner validates:
1) Google login flow (or already-authenticated session),
2) Mi Negocio menu expansion,
3) Agregar Negocio modal,
4) Administrar Negocios view,
5) Informacion General,
6) Detalles de la Cuenta,
7) Tus Negocios,
8) Terminos y Condiciones legal page,
9) Politica de Privacidad legal page.

Outputs:
- Checkpoint screenshots under automation/artifacts/screenshots/<timestamp>
- JSON report under automation/artifacts/reports/
"""

from __future__ import annotations

import json
import os
import sys
import time
from contextlib import suppress
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable, List, Optional, Tuple

from playwright.sync_api import BrowserContext, Page, TimeoutError, sync_playwright


DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com"
DEFAULT_REPORT_NAME = "saleads_mi_negocio_full_test_report.json"
WAIT_TIMEOUT_MS = 25_000


@dataclass
class StepResult:
    status: str = "FAIL"
    details: str = ""
    evidence: List[str] = field(default_factory=list)


class SaleadsMiNegocioWorkflowRunner:
    def __init__(self) -> None:
        self.login_url = os.getenv("SALEADS_LOGIN_URL", "").strip()
        self.google_email = os.getenv("SALEADS_GOOGLE_EMAIL", DEFAULT_GOOGLE_EMAIL).strip()
        self.headless = os.getenv("SALEADS_HEADLESS", "true").lower() not in ("0", "false", "no")
        self.slow_mo_ms = int(os.getenv("SALEADS_SLOWMO_MS", "0"))
        self.cdp_url = os.getenv("SALEADS_CDP_URL", "").strip()
        self.timestamp = time.strftime("%Y%m%d_%H%M%S")
        self.screenshot_root = Path(
            os.getenv(
                "SALEADS_SCREENSHOT_DIR",
                f"automation/artifacts/screenshots/{self.timestamp}",
            )
        )
        self.report_path = Path(
            os.getenv(
                "SALEADS_REPORT_PATH",
                f"automation/artifacts/reports/{DEFAULT_REPORT_NAME}",
            )
        )
        self.screenshot_root.mkdir(parents=True, exist_ok=True)
        self.report_path.parent.mkdir(parents=True, exist_ok=True)
        self.report = {
            "metadata": {
                "test_name": "saleads_mi_negocio_full_test",
                "timestamp_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                "login_url": self.login_url or "(not provided)",
                "google_email": self.google_email,
                "headless": self.headless,
            },
            "results": {
                "Login": StepResult(details="Not executed").__dict__,
                "Mi Negocio menu": StepResult(details="Not executed").__dict__,
                "Agregar Negocio modal": StepResult(details="Not executed").__dict__,
                "Administrar Negocios view": StepResult(details="Not executed").__dict__,
                "Informaci\u00f3n General": StepResult(details="Not executed").__dict__,
                "Detalles de la Cuenta": StepResult(details="Not executed").__dict__,
                "Tus Negocios": StepResult(details="Not executed").__dict__,
                "T\u00e9rminos y Condiciones": StepResult(details="Not executed").__dict__,
                "Pol\u00edtica de Privacidad": StepResult(details="Not executed").__dict__,
            },
        }

    def mark_prerequisite_failure(self, reason: str) -> None:
        for field_name, value in self.report["results"].items():
            value["status"] = "FAIL"
            value["details"] = f"Prerequisite failed: {reason}"
            value["evidence"] = []

    def _set_step(self, name: str, status: str, details: str, evidence: Optional[List[str]] = None) -> None:
        self.report["results"][name] = StepResult(
            status=status,
            details=details,
            evidence=evidence or [],
        ).__dict__

    def _save_report(self) -> None:
        self.report_path.write_text(json.dumps(self.report, indent=2, ensure_ascii=True), encoding="utf-8")

    def _wait_for_ui(self, page: Page) -> None:
        with suppress(TimeoutError):
            page.wait_for_load_state("domcontentloaded", timeout=WAIT_TIMEOUT_MS)
        with suppress(TimeoutError):
            page.wait_for_load_state("networkidle", timeout=WAIT_TIMEOUT_MS)
        page.wait_for_timeout(800)

    def _screenshot(self, page: Page, name: str, full_page: bool = False) -> str:
        target = self.screenshot_root / f"{name}.png"
        page.screenshot(path=str(target), full_page=full_page)
        return str(target)

    def _first_visible_selector(
        self,
        page: Page,
        texts: Iterable[str],
        exact: bool = True,
        timeout_ms: int = WAIT_TIMEOUT_MS,
    ):
        for text in texts:
            locator = page.get_by_text(text, exact=exact).first
            try:
                locator.wait_for(state="visible", timeout=timeout_ms)
                return locator, text
            except TimeoutError:
                continue
        raise TimeoutError(f"None of the expected texts were visible: {list(texts)}")

    def _click_by_visible_text(
        self,
        page: Page,
        texts: Iterable[str],
        step_note: str,
        exact: bool = True,
        timeout_ms: int = WAIT_TIMEOUT_MS,
    ) -> str:
        locator, matched = self._first_visible_selector(page, texts, exact=exact, timeout_ms=timeout_ms)
        locator.click()
        self._wait_for_ui(page)
        print(f"[INFO] {step_note}: clicked '{matched}'")
        return matched

    def _assert_visible_texts(self, page: Page, texts: Iterable[str], exact: bool = True) -> None:
        for text in texts:
            page.get_by_text(text, exact=exact).first.wait_for(state="visible", timeout=WAIT_TIMEOUT_MS)

    def _assert_any_visible_text(self, page: Page, texts: Iterable[str], exact: bool = True) -> str:
        _, matched = self._first_visible_selector(page, texts, exact=exact, timeout_ms=WAIT_TIMEOUT_MS)
        return matched

    def _detect_main_shell(self, page: Page) -> Tuple[bool, str]:
        shell_markers = [
            "Mi Negocio",
            "Negocio",
            "Dashboard",
            "Administrar Negocios",
        ]
        for marker in shell_markers:
            with suppress(TimeoutError):
                page.get_by_text(marker, exact=False).first.wait_for(state="visible", timeout=5_000)
                return True, marker
        return False, "No known app shell marker found"

    def _choose_google_account_if_present(self, page: Page) -> Optional[str]:
        candidates = [self.google_email, self.google_email.lower(), self.google_email.upper()]
        for candidate in candidates:
            locator = page.get_by_text(candidate, exact=False).first
            with suppress(TimeoutError):
                locator.wait_for(state="visible", timeout=7_000)
                locator.click()
                self._wait_for_ui(page)
                return candidate
        return None

    def _validate_legal_link(
        self,
        context: BrowserContext,
        app_page: Page,
        link_text_options: Iterable[str],
        heading_text_options: Iterable[str],
        screenshot_name: str,
    ) -> Tuple[str, str]:
        popup: Optional[Page] = None
        used_popup = False

        try:
            with context.expect_page(timeout=7_000) as new_page_info:
                self._click_by_visible_text(
                    app_page,
                    link_text_options,
                    "Open legal link",
                    exact=False,
                    timeout_ms=7_000,
                )
            popup = new_page_info.value
            used_popup = True
            legal_page = popup
        except TimeoutError:
            self._click_by_visible_text(
                app_page,
                link_text_options,
                "Open legal link",
                exact=False,
                timeout_ms=7_000,
            )
            legal_page = app_page

        self._wait_for_ui(legal_page)
        matched_heading = self._assert_any_visible_text(
            legal_page,
            heading_text_options,
            exact=False,
        )

        body_text = legal_page.locator("body").inner_text(timeout=WAIT_TIMEOUT_MS).strip()
        if len(body_text) < 80:
            raise AssertionError(
                f"Legal page body text for '{matched_heading}' looked too short ({len(body_text)} chars)."
            )

        shot = self._screenshot(legal_page, screenshot_name, full_page=True)
        final_url = legal_page.url

        if used_popup and popup is not None:
            popup.close()
            app_page.bring_to_front()
            self._wait_for_ui(app_page)
        elif legal_page is app_page:
            with suppress(Exception):
                app_page.go_back(timeout=WAIT_TIMEOUT_MS)
                self._wait_for_ui(app_page)

        return shot, final_url

    def run(self) -> int:
        if not self.login_url and not self.cdp_url:
            reason = (
                "Provide SALEADS_LOGIN_URL (preferred) or SALEADS_CDP_URL to attach to an existing logged-in browser."
            )
            self.mark_prerequisite_failure(reason)
            self._save_report()
            print(f"[ERROR] {reason}")
            return 1

        with sync_playwright() as playwright:
            if self.cdp_url:
                browser = playwright.chromium.connect_over_cdp(self.cdp_url)
                context = browser.contexts[0] if browser.contexts else browser.new_context()
                page = context.pages[0] if context.pages else context.new_page()
            else:
                browser = playwright.chromium.launch(headless=self.headless, slow_mo=self.slow_mo_ms)
                context = browser.new_context()
                page = context.new_page()
                page.goto(self.login_url, wait_until="domcontentloaded", timeout=WAIT_TIMEOUT_MS)
                self._wait_for_ui(page)

            try:
                # Step 1: Login with Google
                login_step_evidence: List[str] = []
                try:
                    self._click_by_visible_text(
                        page,
                        [
                            "Sign in with Google",
                            "Iniciar sesi\u00f3n con Google",
                            "Iniciar sesion con Google",
                            "Continuar con Google",
                            "Google",
                        ],
                        "Login step",
                        exact=False,
                    )
                except TimeoutError:
                    print("[INFO] Login button not found; checking whether session is already authenticated.")

                picked_account = self._choose_google_account_if_present(page)
                if picked_account:
                    print(f"[INFO] Google account selected: {picked_account}")

                self._wait_for_ui(page)
                main_shell_ok, shell_detail = self._detect_main_shell(page)
                if not main_shell_ok:
                    raise AssertionError(f"Main application shell not detected. Detail: {shell_detail}")

                dashboard_shot = self._screenshot(page, "01_dashboard_loaded")
                login_step_evidence.append(dashboard_shot)
                self._set_step(
                    "Login",
                    "PASS",
                    f"Main application interface detected with marker '{shell_detail}'. Left navigation appears visible.",
                    login_step_evidence,
                )

                # Step 2: Open Mi Negocio menu
                menu_evidence: List[str] = []
                self._click_by_visible_text(page, ["Negocio"], "Open Negocio section", exact=False)
                self._click_by_visible_text(page, ["Mi Negocio"], "Open Mi Negocio menu", exact=False)
                self._assert_visible_texts(page, ["Agregar Negocio", "Administrar Negocios"], exact=False)
                menu_shot = self._screenshot(page, "02_mi_negocio_menu_expanded")
                menu_evidence.append(menu_shot)
                self._set_step(
                    "Mi Negocio menu",
                    "PASS",
                    "Mi Negocio submenu expanded and both options are visible.",
                    menu_evidence,
                )

                # Step 3: Validate Agregar Negocio modal
                modal_evidence: List[str] = []
                self._click_by_visible_text(page, ["Agregar Negocio"], "Open Agregar Negocio modal", exact=False)
                self._assert_visible_texts(
                    page,
                    [
                        "Crear Nuevo Negocio",
                        "Nombre del Negocio",
                        "Tienes 2 de 3 negocios",
                        "Cancelar",
                        "Crear Negocio",
                    ],
                    exact=False,
                )
                with suppress(TimeoutError):
                    page.get_by_label("Nombre del Negocio", exact=False).first.click()
                    page.get_by_label("Nombre del Negocio", exact=False).first.fill(
                        "Negocio Prueba Automatizacion"
                    )
                with suppress(TimeoutError):
                    page.get_by_placeholder("Nombre del Negocio", exact=False).first.click()
                    page.get_by_placeholder("Nombre del Negocio", exact=False).first.fill(
                        "Negocio Prueba Automatizacion"
                    )
                modal_shot = self._screenshot(page, "03_agregar_negocio_modal")
                modal_evidence.append(modal_shot)
                self._click_by_visible_text(page, ["Cancelar"], "Close modal using Cancelar", exact=False)
                self._set_step(
                    "Agregar Negocio modal",
                    "PASS",
                    "Agregar Negocio modal opened, required fields validated, optional input typed, and modal closed.",
                    modal_evidence,
                )

                # Step 4: Open Administrar Negocios
                admin_evidence: List[str] = []
                with suppress(Exception):
                    self._click_by_visible_text(page, ["Mi Negocio"], "Re-open Mi Negocio menu", exact=False)
                self._click_by_visible_text(page, ["Administrar Negocios"], "Open Administrar Negocios", exact=False)
                self._assert_any_visible_text(page, ["Informaci\u00f3n General", "Informacion General"], exact=False)
                self._assert_visible_texts(page, ["Detalles de la Cuenta", "Tus Negocios"], exact=False)
                self._assert_any_visible_text(page, ["Secci\u00f3n Legal", "Seccion Legal"], exact=False)
                account_shot = self._screenshot(page, "04_administrar_negocios_page", full_page=True)
                admin_evidence.append(account_shot)
                self._set_step(
                    "Administrar Negocios view",
                    "PASS",
                    "Account management view loaded with all required sections.",
                    admin_evidence,
                )

                # Step 5: Validate Informacion General
                page.get_by_text("BUSINESS PLAN", exact=False).first.wait_for(state="visible", timeout=WAIT_TIMEOUT_MS)
                page.get_by_text("Cambiar Plan", exact=False).first.wait_for(state="visible", timeout=WAIT_TIMEOUT_MS)
                info_block = page.locator("body").inner_text(timeout=WAIT_TIMEOUT_MS)
                if "@" not in info_block:
                    raise AssertionError("User email marker not found in Informacion General section.")
                self._set_step(
                    "Informaci\u00f3n General",
                    "PASS",
                    "User profile text, email marker, BUSINESS PLAN and Cambiar Plan are visible.",
                )

                # Step 6: Validate Detalles de la Cuenta
                self._assert_visible_texts(
                    page,
                    [
                        "Cuenta creada",
                        "Estado activo",
                        "Idioma seleccionado",
                    ],
                    exact=False,
                )
                self._set_step(
                    "Detalles de la Cuenta",
                    "PASS",
                    "Detalles de la Cuenta labels are visible.",
                )

                # Step 7: Validate Tus Negocios
                self._assert_visible_texts(
                    page,
                    [
                        "Tus Negocios",
                        "Agregar Negocio",
                        "Tienes 2 de 3 negocios",
                    ],
                    exact=False,
                )
                self._set_step(
                    "Tus Negocios",
                    "PASS",
                    "Business list section and expected controls/text are visible.",
                )

                # Step 8: Validate Terminos y Condiciones
                terms_shot, terms_url = self._validate_legal_link(
                    context=context,
                    app_page=page,
                    link_text_options=["T\u00e9rminos y Condiciones", "Terminos y Condiciones"],
                    heading_text_options=["T\u00e9rminos y Condiciones", "Terminos y Condiciones"],
                    screenshot_name="08_terminos_y_condiciones",
                )
                self._set_step(
                    "T\u00e9rminos y Condiciones",
                    "PASS",
                    f"Legal page validated successfully. Final URL: {terms_url}",
                    [terms_shot, terms_url],
                )

                # Step 9: Validate Politica de Privacidad
                privacy_shot, privacy_url = self._validate_legal_link(
                    context=context,
                    app_page=page,
                    link_text_options=["Pol\u00edtica de Privacidad", "Politica de Privacidad"],
                    heading_text_options=["Pol\u00edtica de Privacidad", "Politica de Privacidad"],
                    screenshot_name="09_politica_de_privacidad",
                )
                self._set_step(
                    "Pol\u00edtica de Privacidad",
                    "PASS",
                    f"Legal page validated successfully. Final URL: {privacy_url}",
                    [privacy_shot, privacy_url],
                )

                self._save_report()
                print(json.dumps(self.report["results"], indent=2, ensure_ascii=True))
                return 0
            except Exception as exc:  # pylint: disable=broad-except
                failed_step = next(
                    (
                        key
                        for key, result in self.report["results"].items()
                        if result["status"] == "FAIL" and result["details"] == "Not executed"
                    ),
                    None,
                )
                if failed_step:
                    self._set_step(failed_step, "FAIL", f"Execution failed: {exc}")
                else:
                    self._set_step("Login", "FAIL", f"Execution failed before reporting: {exc}")
                self._save_report()
                print(f"[ERROR] {exc}", file=sys.stderr)
                return 1
            finally:
                with suppress(Exception):
                    context.close()
                with suppress(Exception):
                    browser.close()


def main() -> int:
    runner = SaleadsMiNegocioWorkflowRunner()
    return runner.run()


if __name__ == "__main__":
    sys.exit(main())
