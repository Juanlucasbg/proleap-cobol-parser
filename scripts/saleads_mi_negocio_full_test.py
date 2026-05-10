#!/usr/bin/env python3
"""End-to-end validation for SaleADS Mi Negocio workflow.

The script is environment-agnostic:
- It never hardcodes a specific SaleADS domain.
- It relies on visible text selectors whenever possible.
- It captures screenshots at key checkpoints.

Usage:
  python3 scripts/saleads_mi_negocio_full_test.py --base-url "https://<env>/login"
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from playwright.sync_api import BrowserContext, Locator, Page, TimeoutError, sync_playwright


GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com"

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
    status: str
    details: str
    screenshot: Optional[str] = None
    final_url: Optional[str] = None


class SaleadsMiNegocioWorkflow:
    def __init__(self, base_url: str, headless: bool, output_root: Path, timeout_ms: int):
        self.base_url = base_url
        self.output_root = output_root
        self.timeout_ms = timeout_ms
        self.output_root.mkdir(parents=True, exist_ok=True)

        self.results: Dict[str, StepResult] = {
            field: StepResult(status="FAIL", details="Step did not complete") for field in REPORT_FIELDS
        }

        self.playwright = sync_playwright().start()
        self.browser = self.playwright.chromium.launch(headless=headless)
        self.context: BrowserContext = self.browser.new_context(ignore_https_errors=True)
        self.page: Page = self.context.new_page()

    def shutdown(self) -> None:
        self.context.close()
        self.browser.close()
        self.playwright.stop()

    def record(
        self,
        field: str,
        status: str,
        details: str,
        screenshot: Optional[Path] = None,
        final_url: Optional[str] = None,
    ) -> None:
        self.results[field] = StepResult(
            status=status,
            details=details,
            screenshot=str(screenshot) if screenshot else None,
            final_url=final_url,
        )

    def wait_for_ui_settle(self, page: Optional[Page] = None, extra_wait_ms: int = 1000) -> None:
        target = page or self.page
        try:
            target.wait_for_load_state("domcontentloaded", timeout=self.timeout_ms)
        except TimeoutError:
            pass
        try:
            target.wait_for_load_state("networkidle", timeout=5000)
        except TimeoutError:
            pass
        target.wait_for_timeout(extra_wait_ms)

    def screenshot(self, name: str, page: Optional[Page] = None, full_page: bool = False) -> Path:
        ts = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
        path = self.output_root / f"{ts}_{name}.png"
        (page or self.page).screenshot(path=str(path), full_page=full_page)
        return path

    def _first_visible(self, candidates: List[Locator], timeout_ms: int = 8000) -> Optional[Locator]:
        deadline = time.time() + (timeout_ms / 1000.0)
        while time.time() < deadline:
            for candidate in candidates:
                try:
                    item = candidate.first
                    if item.is_visible():
                        return item
                except Exception:
                    continue
            time.sleep(0.2)
        return None

    def _click_and_wait(self, locator: Locator, page: Optional[Page] = None) -> None:
        locator.click()
        self.wait_for_ui_settle(page=page)

    def _text_candidates(self, page: Page, visible_text: str) -> List[Locator]:
        escaped = re.escape(visible_text)
        regex = re.compile(escaped, re.IGNORECASE)
        return [
            page.get_by_role("button", name=regex),
            page.get_by_role("link", name=regex),
            page.get_by_text(regex, exact=False),
        ]

    def _click_by_visible_text(self, page: Page, text_value: str, timeout_ms: int = 10000) -> bool:
        target = self._first_visible(self._text_candidates(page, text_value), timeout_ms=timeout_ms)
        if not target:
            return False
        self._click_and_wait(target, page=page)
        return True

    def _assert_visible_text(self, page: Page, text_value: str, timeout_ms: int = 10000) -> bool:
        try:
            page.get_by_text(re.compile(re.escape(text_value), re.IGNORECASE), exact=False).first.wait_for(
                state="visible",
                timeout=timeout_ms,
            )
            return True
        except TimeoutError:
            return False

    def _ensure_login(self) -> Tuple[bool, str, Optional[Path]]:
        self.page.goto(self.base_url, wait_until="domcontentloaded", timeout=self.timeout_ms)
        self.wait_for_ui_settle()

        google_patterns = re.compile(
            r"(sign in with google|iniciar sesi[oó]n con google|continuar con google|google)",
            re.IGNORECASE,
        )
        login_button = self._first_visible(
            [
                self.page.get_by_role("button", name=google_patterns),
                self.page.get_by_role("link", name=google_patterns),
                self.page.get_by_text(google_patterns, exact=False),
            ],
            timeout_ms=12000,
        )

        if login_button:
            popup_page: Optional[Page] = None
            try:
                with self.context.expect_page(timeout=6000) as popup_info:
                    self._click_and_wait(login_button)
                popup_page = popup_info.value
            except TimeoutError:
                self._click_and_wait(login_button)

            if popup_page:
                self.wait_for_ui_settle(page=popup_page, extra_wait_ms=1500)
                account_candidates = [
                    popup_page.get_by_text(GOOGLE_ACCOUNT_EMAIL, exact=False),
                    popup_page.get_by_role("button", name=re.compile(re.escape(GOOGLE_ACCOUNT_EMAIL), re.IGNORECASE)),
                ]
                account_entry = self._first_visible(account_candidates, timeout_ms=10000)
                if account_entry:
                    account_entry.click()
                    self.wait_for_ui_settle(page=popup_page, extra_wait_ms=1000)

                # Google auth can close popup automatically after account selection.
                try:
                    popup_page.wait_for_event("close", timeout=15000)
                except TimeoutError:
                    pass

            self.page.bring_to_front()
            self.wait_for_ui_settle(extra_wait_ms=1500)

        # Validate dashboard/main app and sidebar are visible.
        app_loaded = self._first_visible(
            [
                self.page.locator("aside"),
                self.page.locator("nav"),
                self.page.get_by_text(re.compile(r"(dashboard|panel|inicio|negocio)", re.IGNORECASE), exact=False),
            ],
            timeout_ms=15000,
        )
        sidebar_visible = self._first_visible(
            [
                self.page.locator("aside"),
                self.page.get_by_text(re.compile(r"(negocio|mi negocio|inicio)", re.IGNORECASE), exact=False),
            ],
            timeout_ms=10000,
        )
        mi_negocio_hint = self._first_visible(
            [
                self.page.get_by_text(re.compile(r"(mi negocio|negocio)", re.IGNORECASE), exact=False),
            ],
            timeout_ms=8000,
        )

        if app_loaded and sidebar_visible and mi_negocio_hint:
            shot = self.screenshot("01_dashboard_loaded")
            return True, "Main application and left sidebar are visible.", shot
        shot = self.screenshot("01_login_validation_failed")
        return False, "Could not confirm authenticated app state with Mi Negocio navigation.", shot

    def _open_mi_negocio(self) -> Tuple[bool, str, Optional[Path]]:
        open_ok = self._click_by_visible_text(self.page, "Mi Negocio", timeout_ms=9000)
        if not open_ok:
            open_ok = self._click_by_visible_text(self.page, "Negocio", timeout_ms=9000)
        self.wait_for_ui_settle()

        agregar = self._assert_visible_text(self.page, "Agregar Negocio", timeout_ms=9000)
        administrar = self._assert_visible_text(self.page, "Administrar Negocios", timeout_ms=9000)

        if open_ok and agregar and administrar:
            shot = self.screenshot("02_mi_negocio_expanded")
            return True, "Mi Negocio submenu expanded with expected options.", shot
        return False, "Could not verify expanded submenu with Agregar/Administrar.", None

    def _validate_agregar_modal(self) -> Tuple[bool, str, Optional[Path]]:
        if not self._click_by_visible_text(self.page, "Agregar Negocio", timeout_ms=9000):
            return False, "Could not click 'Agregar Negocio'.", None

        title_ok = self._assert_visible_text(self.page, "Crear Nuevo Negocio", timeout_ms=12000)
        input_ok = self._assert_visible_text(self.page, "Nombre del Negocio", timeout_ms=9000)
        quota_ok = self._assert_visible_text(self.page, "Tienes 2 de 3 negocios", timeout_ms=9000)
        cancel_ok = self._assert_visible_text(self.page, "Cancelar", timeout_ms=9000)
        create_ok = self._assert_visible_text(self.page, "Crear Negocio", timeout_ms=9000)

        shot = self.screenshot("03_agregar_negocio_modal")

        # Optional action flow requested by test definition.
        self._click_by_visible_text(self.page, "Nombre del Negocio", timeout_ms=2000)
        try:
            field = self.page.get_by_label(re.compile(r"nombre del negocio", re.IGNORECASE))
            field.first.fill("Negocio Prueba Automatización")
        except Exception:
            try:
                self.page.get_by_placeholder(re.compile(r"nombre del negocio", re.IGNORECASE)).first.fill(
                    "Negocio Prueba Automatización"
                )
            except Exception:
                pass

        self._click_by_visible_text(self.page, "Cancelar", timeout_ms=6000)

        passed = all([title_ok, input_ok, quota_ok, cancel_ok, create_ok])
        if passed:
            return True, "Crear Nuevo Negocio modal validated successfully.", shot
        return False, "Modal validations failed for one or more required elements.", shot

    def _open_administrar_negocios(self) -> Tuple[bool, str, Optional[Path]]:
        # Re-open menu if collapsed.
        if not self._assert_visible_text(self.page, "Administrar Negocios", timeout_ms=3000):
            self._click_by_visible_text(self.page, "Mi Negocio", timeout_ms=5000)
            self.wait_for_ui_settle()

        if not self._click_by_visible_text(self.page, "Administrar Negocios", timeout_ms=9000):
            return False, "Could not open 'Administrar Negocios'.", None

        section_titles = [
            "Información General",
            "Detalles de la Cuenta",
            "Tus Negocios",
            "Sección Legal",
        ]
        checks = [self._assert_visible_text(self.page, title, timeout_ms=12000) for title in section_titles]
        shot = self.screenshot("04_administrar_negocios", full_page=True)
        if all(checks):
            return True, "Administrar Negocios page loaded with all required sections.", shot
        return False, "Missing one or more expected account sections.", shot

    def _validate_info_general(self) -> Tuple[bool, str]:
        name_like = self._first_visible(
            [
                self.page.locator("h1"),
                self.page.locator("h2"),
                self.page.get_by_text(re.compile(r"@[a-z0-9_.-]+|[A-Za-z]{2,}\\s+[A-Za-z]{2,}", re.IGNORECASE), exact=False),
            ],
            timeout_ms=9000,
        )
        email_visible = self._first_visible(
            [
                self.page.get_by_text(re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+", re.IGNORECASE), exact=False),
            ],
            timeout_ms=9000,
        )
        plan_ok = self._assert_visible_text(self.page, "BUSINESS PLAN", timeout_ms=9000)
        change_plan_ok = self._assert_visible_text(self.page, "Cambiar Plan", timeout_ms=9000)

        passed = all([name_like is not None, email_visible is not None, plan_ok, change_plan_ok])
        if passed:
            return True, "Información General values are visible."
        return False, "Missing name/email/plan data in Información General."

    def _validate_detalles_cuenta(self) -> Tuple[bool, str]:
        created_ok = self._assert_visible_text(self.page, "Cuenta creada", timeout_ms=9000)
        status_ok = self._assert_visible_text(self.page, "Estado activo", timeout_ms=9000)
        language_ok = self._assert_visible_text(self.page, "Idioma seleccionado", timeout_ms=9000)
        if all([created_ok, status_ok, language_ok]):
            return True, "Detalles de la Cuenta fields are visible."
        return False, "Missing one or more account detail fields."

    def _validate_tus_negocios(self) -> Tuple[bool, str]:
        list_visible = self._first_visible(
            [
                self.page.locator("table"),
                self.page.locator("ul"),
                self.page.get_by_text(re.compile(r"negocio", re.IGNORECASE), exact=False),
            ],
            timeout_ms=9000,
        )
        add_button = self._assert_visible_text(self.page, "Agregar Negocio", timeout_ms=9000)
        quota_ok = self._assert_visible_text(self.page, "Tienes 2 de 3 negocios", timeout_ms=9000)
        if list_visible and add_button and quota_ok:
            return True, "Tus Negocios list, button, and quota text are visible."
        return False, "Tus Negocios validations failed."

    def _validate_legal_link(self, link_text: str, heading_text: str, screenshot_name: str) -> Tuple[bool, str, Optional[Path], Optional[str]]:
        origin_page = self.page
        pages_before = list(self.context.pages)
        clicked = self._click_by_visible_text(origin_page, link_text, timeout_ms=7000)
        if not clicked:
            return False, f"Could not click '{link_text}'.", None, None

        self.wait_for_ui_settle(page=origin_page, extra_wait_ms=800)
        pages_after = list(self.context.pages)
        new_pages = [candidate for candidate in pages_after if candidate not in pages_before]
        legal_page = new_pages[0] if new_pages else origin_page

        self.wait_for_ui_settle(page=legal_page, extra_wait_ms=1200)

        heading_ok = False
        content_ok = False
        try:
            heading_ok = self._assert_visible_text(legal_page, heading_text, timeout_ms=12000)
            content_ok = self._first_visible(
                [
                    legal_page.locator("p"),
                    legal_page.locator("article"),
                    legal_page.get_by_text(re.compile(r"(condiciones|privacidad|datos|términos|terminos)", re.IGNORECASE), exact=False),
                ],
                timeout_ms=10000,
            ) is not None
        except Exception:
            pass

        shot = self.screenshot(screenshot_name, page=legal_page, full_page=True)
        final_url = legal_page.url

        # Return to app tab/page.
        if legal_page is not origin_page:
            try:
                legal_page.close()
            except Exception:
                pass
            origin_page.bring_to_front()
            self.wait_for_ui_settle(page=origin_page)
        else:
            try:
                origin_page.go_back(wait_until="domcontentloaded", timeout=self.timeout_ms)
            except TimeoutError:
                pass
            self.wait_for_ui_settle(page=origin_page)

        if heading_ok and content_ok:
            return True, f"Validated legal page '{heading_text}'.", shot, final_url
        return False, f"Legal page validation failed for '{heading_text}'.", shot, final_url

    def run(self) -> int:
        # Step 1
        login_ok, login_details, login_shot = self._ensure_login()
        self.record("Login", "PASS" if login_ok else "FAIL", login_details, screenshot=login_shot)

        # Step 2
        menu_ok, menu_details, menu_shot = self._open_mi_negocio()
        self.record("Mi Negocio menu", "PASS" if menu_ok else "FAIL", menu_details, screenshot=menu_shot)
        if self.results["Login"].status == "PASS" and not menu_ok:
            self.record(
                "Login",
                "FAIL",
                "Authenticated sidebar could not be confirmed because Mi Negocio navigation was unavailable.",
                screenshot=login_shot,
            )

        # Step 3
        modal_ok, modal_details, modal_shot = self._validate_agregar_modal()
        self.record("Agregar Negocio modal", "PASS" if modal_ok else "FAIL", modal_details, screenshot=modal_shot)

        # Step 4
        admin_ok, admin_details, admin_shot = self._open_administrar_negocios()
        self.record("Administrar Negocios view", "PASS" if admin_ok else "FAIL", admin_details, screenshot=admin_shot)

        # Step 5
        info_ok, info_details = self._validate_info_general()
        self.record("Información General", "PASS" if info_ok else "FAIL", info_details)

        # Step 6
        details_ok, details_details = self._validate_detalles_cuenta()
        self.record("Detalles de la Cuenta", "PASS" if details_ok else "FAIL", details_details)

        # Step 7
        business_ok, business_details = self._validate_tus_negocios()
        self.record("Tus Negocios", "PASS" if business_ok else "FAIL", business_details)

        # Step 8
        if admin_ok:
            terms_ok, terms_details, terms_shot, terms_url = self._validate_legal_link(
                link_text="Términos y Condiciones",
                heading_text="Términos y Condiciones",
                screenshot_name="08_terminos_condiciones",
            )
            self.record(
                "Términos y Condiciones",
                "PASS" if terms_ok else "FAIL",
                terms_details,
                screenshot=terms_shot,
                final_url=terms_url,
            )
        else:
            self.record(
                "Términos y Condiciones",
                "FAIL",
                "Prerequisite not met: Administrar Negocios view was not available.",
            )

        # Step 9
        if admin_ok:
            privacy_ok, privacy_details, privacy_shot, privacy_url = self._validate_legal_link(
                link_text="Política de Privacidad",
                heading_text="Política de Privacidad",
                screenshot_name="09_politica_privacidad",
            )
            self.record(
                "Política de Privacidad",
                "PASS" if privacy_ok else "FAIL",
                privacy_details,
                screenshot=privacy_shot,
                final_url=privacy_url,
            )
        else:
            self.record(
                "Política de Privacidad",
                "FAIL",
                "Prerequisite not met: Administrar Negocios view was not available.",
            )

        report_payload = {
            "test_name": "saleads_mi_negocio_full_test",
            "executed_at_utc": datetime.now(timezone.utc).isoformat(),
            "base_url": self.base_url,
            "results": {field: self.results[field].__dict__ for field in REPORT_FIELDS},
            "artifacts_directory": str(self.output_root),
        }
        report_path = self.output_root / "report.json"
        report_path.write_text(json.dumps(report_payload, ensure_ascii=False, indent=2), encoding="utf-8")

        print(json.dumps(report_payload, ensure_ascii=False, indent=2))
        print(f"\nReport written to: {report_path}")

        all_passed = all(result.status == "PASS" for result in self.results.values())
        return 0 if all_passed else 1


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="SaleADS Mi Negocio workflow validation script.")
    parser.add_argument(
        "--base-url",
        default="",
        help="SaleADS login URL for the current environment. If empty, SALEADS_BASE_URL env var is used.",
    )
    parser.add_argument(
        "--output-dir",
        default="artifacts/saleads_mi_negocio_full_test",
        help="Directory to store screenshots and report.json.",
    )
    parser.add_argument(
        "--headless",
        action="store_true",
        help="Run browser in headless mode.",
    )
    parser.add_argument(
        "--timeout-ms",
        default=30000,
        type=int,
        help="Default timeout in milliseconds.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    base_url = args.base_url.strip() or os.environ.get("SALEADS_BASE_URL", "").strip()

    if not base_url:
        print(
            "Missing SaleADS URL. Provide --base-url or SALEADS_BASE_URL for the current environment.",
            file=sys.stderr,
        )
        return 2

    output_root = Path(args.output_dir)
    runner = SaleadsMiNegocioWorkflow(
        base_url=base_url,
        headless=args.headless,
        output_root=output_root,
        timeout_ms=args.timeout_ms,
    )
    try:
        return runner.run()
    finally:
        runner.shutdown()


if __name__ == "__main__":
    raise SystemExit(main())
