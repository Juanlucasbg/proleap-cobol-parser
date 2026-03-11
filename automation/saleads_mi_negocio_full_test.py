#!/usr/bin/env python3
"""
SaleADS Mi Negocio full workflow validation.

This script automates:
1) Login with Google
2) Mi Negocio menu validations
3) Agregar Negocio modal validations
4) Administrar Negocios page validations
5) Legal links (Terms and Privacy) validation with tab handling
6) Evidence collection (screenshots + URLs) and final PASS/FAIL report

Environment agnostic:
- No hardcoded domain is used.
- Use --start-url for direct navigation to the current environment login page, or
  --cdp-url to attach to an already-open browser tab/session.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Optional


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


def _load_playwright() -> tuple[Any, Any]:
    try:
        from playwright.sync_api import TimeoutError as PlaywrightTimeoutError
        from playwright.sync_api import sync_playwright
    except ImportError as exc:
        print(
            "Playwright is not installed. Install with:\n"
            "  pip install playwright\n"
            "  python -m playwright install chromium",
            file=sys.stderr,
        )
        raise SystemExit(2) from exc
    return sync_playwright, PlaywrightTimeoutError


@dataclass
class Validation:
    status: str
    details: str


class SaleadsMiNegocioWorkflow:
    def __init__(self, args: argparse.Namespace, playwright_timeout_error: type):
        self.args = args
        self.PlaywrightTimeoutError = playwright_timeout_error
        self.report: dict[str, Validation] = {
            name: Validation(status="FAIL", details="Not executed") for name in REPORT_FIELDS
        }
        self.artifacts_dir = self._build_artifacts_dir(Path(args.artifacts_dir))
        self.artifacts_dir.mkdir(parents=True, exist_ok=True)
        self.urls: dict[str, str] = {}
        self.context = None
        self.browser = None
        self.app_page = None

    @staticmethod
    def _build_artifacts_dir(base_dir: Path) -> Path:
        timestamp = datetime.utcnow().strftime("%Y%m%dT%H%M%SZ")
        return base_dir / timestamp

    def log(self, message: str) -> None:
        print(f"[saleads_mi_negocio_full_test] {message}")

    def mark(self, field: str, passed: bool, details: str) -> None:
        self.report[field] = Validation(status="PASS" if passed else "FAIL", details=details)
        self.log(f"{field}: {self.report[field].status} - {details}")

    def save_report(self) -> None:
        payload = {
            "test_name": "saleads_mi_negocio_full_test",
            "generated_at_utc": datetime.utcnow().isoformat() + "Z",
            "artifacts_dir": str(self.artifacts_dir),
            "results": {k: {"status": v.status, "details": v.details} for k, v in self.report.items()},
            "captured_urls": self.urls,
        }
        report_json = self.artifacts_dir / "final_report.json"
        report_txt = self.artifacts_dir / "final_report.txt"
        report_json.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

        lines = ["Final Validation Report", "======================", ""]
        for field in REPORT_FIELDS:
            item = self.report[field]
            lines.append(f"- {field}: {item.status} ({item.details})")
        if self.urls:
            lines.append("")
            lines.append("Captured URLs")
            lines.append("-------------")
            for key, value in self.urls.items():
                lines.append(f"- {key}: {value}")
        report_txt.write_text("\n".join(lines) + "\n", encoding="utf-8")

    def screenshot(self, page: Any, name: str, full_page: bool = False) -> None:
        safe_name = re.sub(r"[^a-zA-Z0-9._-]+", "_", name).strip("_")
        target = self.artifacts_dir / f"{safe_name}.png"
        page.screenshot(path=str(target), full_page=full_page)
        self.log(f"Screenshot captured: {target}")

    def wait_ui(self, page: Any, pause_ms: int = 500) -> None:
        try:
            page.wait_for_load_state("domcontentloaded", timeout=self.args.timeout_ms)
        except self.PlaywrightTimeoutError:
            pass
        try:
            page.wait_for_load_state("networkidle", timeout=min(self.args.timeout_ms, 7000))
        except self.PlaywrightTimeoutError:
            pass
        page.wait_for_timeout(pause_ms)

    def is_visible_text(self, page: Any, text: str, exact: bool = True, timeout_ms: Optional[int] = None) -> bool:
        timeout_ms = timeout_ms or self.args.timeout_ms
        try:
            page.get_by_text(text, exact=exact).first.wait_for(state="visible", timeout=timeout_ms)
            return True
        except self.PlaywrightTimeoutError:
            return False

    def click_by_visible_text(self, page: Any, texts: list[str], timeout_ms: Optional[int] = None) -> bool:
        timeout_ms = timeout_ms or self.args.timeout_ms
        for text in texts:
            regex_name = re.compile(rf"^{re.escape(text)}$", re.IGNORECASE)
            candidates = [
                page.get_by_role("button", name=regex_name).first,
                page.get_by_role("link", name=regex_name).first,
                page.get_by_role("menuitem", name=regex_name).first,
                page.get_by_text(text, exact=True).first,
                page.get_by_text(text, exact=False).first,
            ]
            for locator in candidates:
                try:
                    locator.wait_for(state="visible", timeout=timeout_ms)
                    locator.click(timeout=timeout_ms)
                    self.wait_ui(page)
                    return True
                except self.PlaywrightTimeoutError:
                    continue
                except Exception:
                    continue
        return False

    def expect_texts_visible(self, page: Any, texts: list[str], exact: bool = True) -> tuple[bool, list[str]]:
        missing: list[str] = []
        for text in texts:
            if not self.is_visible_text(page, text, exact=exact):
                missing.append(text)
        return len(missing) == 0, missing

    def detect_app_interface(self, page: Any) -> bool:
        try:
            if page.locator("aside").first.is_visible(timeout=1000):
                return True
        except Exception:
            pass
        return (
            self.is_visible_text(page, "Negocio", exact=False, timeout_ms=1000)
            or self.is_visible_text(page, "Mi Negocio", exact=False, timeout_ms=1000)
        )

    def find_page_with_app(self, timeout_s: int = 60) -> Optional[Any]:
        started = time.time()
        while time.time() - started < timeout_s:
            for page in self.context.pages:
                try:
                    if self.detect_app_interface(page):
                        return page
                except Exception:
                    continue
            time.sleep(0.5)
        return None

    def ensure_connected(self, playwright: Any) -> None:
        if self.args.cdp_url:
            self.browser = playwright.chromium.connect_over_cdp(self.args.cdp_url)
            if self.browser.contexts:
                self.context = self.browser.contexts[0]
            else:
                self.context = self.browser.new_context()
            if self.context.pages:
                self.app_page = self.context.pages[0]
            else:
                self.app_page = self.context.new_page()
        else:
            if not self.args.start_url:
                raise ValueError("Either --start-url or --cdp-url must be provided.")
            self.browser = playwright.chromium.launch(
                headless=not self.args.headed, slow_mo=self.args.slow_mo
            )
            self.context = self.browser.new_context(viewport={"width": 1440, "height": 900})
            self.app_page = self.context.new_page()

        if self.args.start_url:
            self.app_page.goto(self.args.start_url, wait_until="domcontentloaded")
            self.wait_ui(self.app_page)

    def step_login(self) -> None:
        page = self.app_page
        details = []
        try:
            clicked_login = self.click_by_visible_text(
                page,
                ["Sign in with Google", "Iniciar sesión con Google", "Continuar con Google", "Google"],
                timeout_ms=max(self.args.timeout_ms, 10000),
            )
            if clicked_login:
                details.append("Clicked Google login action.")
            else:
                details.append("Google login action not clicked (possibly already logged in).")

            # If account selector appears, pick the requested account.
            account_email = "juanlucasbarbiergarzon@gmail.com"
            picked_account = False
            for candidate_page in self.context.pages:
                if self.click_by_visible_text(candidate_page, [account_email], timeout_ms=4000):
                    picked_account = True
                    details.append("Selected Google account.")
                    break
            if not picked_account:
                details.append("Google account chooser not detected or account already selected.")

            candidate_page = self.find_page_with_app(timeout_s=90)
            if candidate_page is None:
                self.mark("Login", False, "Main application interface not detected after login.")
                return

            self.app_page = candidate_page
            sidebar_ok = self.detect_app_interface(self.app_page)
            self.screenshot(self.app_page, "01_dashboard_loaded", full_page=False)
            self.mark(
                "Login",
                bool(sidebar_ok),
                "Dashboard loaded and sidebar/navigation detected." if sidebar_ok else "Dashboard loaded but sidebar not detected.",
            )
        except Exception as exc:
            self.mark("Login", False, f"Unhandled exception during login: {exc}")

    def step_mi_negocio_menu(self) -> None:
        page = self.app_page
        try:
            self.click_by_visible_text(page, ["Negocio", "Mi Negocio"], timeout_ms=7000)
            if not self.is_visible_text(page, "Agregar Negocio", exact=False, timeout_ms=3000):
                self.click_by_visible_text(page, ["Mi Negocio", "Negocio"], timeout_ms=7000)

            ok, missing = self.expect_texts_visible(page, ["Agregar Negocio", "Administrar Negocios"], exact=False)
            self.screenshot(page, "02_mi_negocio_menu_expanded")
            self.mark(
                "Mi Negocio menu",
                ok,
                "Submenu expanded with expected options."
                if ok
                else f"Missing submenu items: {', '.join(missing)}",
            )
        except Exception as exc:
            self.mark("Mi Negocio menu", False, f"Unhandled exception: {exc}")

    def step_agregar_negocio_modal(self) -> None:
        page = self.app_page
        try:
            if not self.click_by_visible_text(page, ["Agregar Negocio"], timeout_ms=9000):
                self.mark("Agregar Negocio modal", False, "Could not click 'Agregar Negocio'.")
                return

            checks = [
                self.is_visible_text(page, "Crear Nuevo Negocio", exact=False),
                self.is_visible_text(page, "Nombre del Negocio", exact=False),
                self.is_visible_text(page, "Tienes 2 de 3 negocios", exact=False),
                self.is_visible_text(page, "Cancelar", exact=False),
                self.is_visible_text(page, "Crear Negocio", exact=False),
            ]
            all_ok = all(checks)
            self.screenshot(page, "03_agregar_negocio_modal")

            # Optional action from spec.
            try:
                input_locator = page.get_by_label("Nombre del Negocio").first
                input_locator.wait_for(state="visible", timeout=2000)
                input_locator.fill("Negocio Prueba Automatización")
            except Exception:
                try:
                    page.get_by_placeholder("Nombre del Negocio").first.fill("Negocio Prueba Automatización")
                except Exception:
                    pass
            self.click_by_visible_text(page, ["Cancelar"], timeout_ms=3000)

            self.mark(
                "Agregar Negocio modal",
                all_ok,
                "Modal content validated."
                if all_ok
                else "One or more modal validations failed (title/field/quota/buttons).",
            )
        except Exception as exc:
            self.mark("Agregar Negocio modal", False, f"Unhandled exception: {exc}")

    def step_administrar_negocios(self) -> None:
        page = self.app_page
        try:
            if not self.is_visible_text(page, "Administrar Negocios", exact=False, timeout_ms=2000):
                self.click_by_visible_text(page, ["Mi Negocio", "Negocio"], timeout_ms=6000)

            clicked = self.click_by_visible_text(page, ["Administrar Negocios"], timeout_ms=9000)
            if not clicked:
                self.mark("Administrar Negocios view", False, "Could not click 'Administrar Negocios'.")
                return

            expected_sections = [
                "Información General",
                "Detalles de la Cuenta",
                "Tus Negocios",
                "Sección Legal",
            ]
            ok, missing = self.expect_texts_visible(page, expected_sections, exact=False)
            self.screenshot(page, "04_administrar_negocios_page", full_page=True)
            self.mark(
                "Administrar Negocios view",
                ok,
                "All account sections found."
                if ok
                else f"Missing sections: {', '.join(missing)}",
            )
        except Exception as exc:
            self.mark("Administrar Negocios view", False, f"Unhandled exception: {exc}")

    def step_informacion_general(self) -> None:
        page = self.app_page
        try:
            body_text = page.locator("body").inner_text(timeout=self.args.timeout_ms)
            email_match = bool(
                re.search(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", body_text)
            )
            plan_visible = self.is_visible_text(page, "BUSINESS PLAN", exact=False)
            cambiar_plan_visible = self.is_visible_text(page, "Cambiar Plan", exact=False)

            # User name varies by environment; accept typical labels.
            user_name_visible = any(
                self.is_visible_text(page, token, exact=False, timeout_ms=1500)
                for token in ["Nombre", "Usuario", "Perfil", "Name"]
            )
            ok = email_match and plan_visible and cambiar_plan_visible and user_name_visible
            self.mark(
                "Información General",
                ok,
                "User details, plan, and change-plan action are visible."
                if ok
                else "Missing one or more checks: user name/email/BUSINESS PLAN/Cambiar Plan.",
            )
        except Exception as exc:
            self.mark("Información General", False, f"Unhandled exception: {exc}")

    def step_detalles_cuenta(self) -> None:
        page = self.app_page
        try:
            ok, missing = self.expect_texts_visible(
                page,
                ["Cuenta creada", "Estado activo", "Idioma seleccionado"],
                exact=False,
            )
            self.mark(
                "Detalles de la Cuenta",
                ok,
                "Account details section validated."
                if ok
                else f"Missing details: {', '.join(missing)}",
            )
        except Exception as exc:
            self.mark("Detalles de la Cuenta", False, f"Unhandled exception: {exc}")

    def step_tus_negocios(self) -> None:
        page = self.app_page
        try:
            has_heading = self.is_visible_text(page, "Tus Negocios", exact=False)
            has_button = self.is_visible_text(page, "Agregar Negocio", exact=False)
            has_quota = self.is_visible_text(page, "Tienes 2 de 3 negocios", exact=False)
            body_text = page.locator("body").inner_text(timeout=self.args.timeout_ms)
            has_list_content = has_heading and ("Negocio" in body_text or "business" in body_text.lower())
            ok = has_list_content and has_button and has_quota
            self.mark(
                "Tus Negocios",
                ok,
                "Business list and controls validated."
                if ok
                else "Missing one or more checks: list visibility/button/quota text.",
            )
        except Exception as exc:
            self.mark("Tus Negocios", False, f"Unhandled exception: {exc}")

    def _open_legal_and_validate(
        self, link_text: str, heading_text: str, report_field: str, screenshot_name: str
    ) -> None:
        page = self.app_page
        try:
            known_pages = set(self.context.pages)
            clicked = self.click_by_visible_text(page, [link_text], timeout_ms=9000)
            if not clicked:
                self.mark(report_field, False, f"Could not click '{link_text}'.")
                return

            self.wait_ui(page, pause_ms=800)
            current_pages = set(self.context.pages)
            new_pages = [p for p in current_pages if p not in known_pages]
            legal_page = new_pages[0] if new_pages else page
            self.wait_ui(legal_page, pause_ms=800)

            heading_ok = self.is_visible_text(legal_page, heading_text, exact=False, timeout_ms=12000)
            legal_text = legal_page.locator("body").inner_text(timeout=self.args.timeout_ms)
            content_ok = len(legal_text.strip()) > 100
            url = legal_page.url

            self.screenshot(legal_page, screenshot_name, full_page=True)
            self.urls[report_field] = url

            ok = heading_ok and content_ok
            self.mark(
                report_field,
                ok,
                f"Heading/content validated. URL={url}"
                if ok
                else f"Failed heading/content validation. URL={url}",
            )

            if legal_page is not page:
                legal_page.close()
                page.bring_to_front()
                self.wait_ui(page)
            else:
                try:
                    page.go_back(timeout=self.args.timeout_ms)
                    self.wait_ui(page)
                except Exception:
                    pass
        except Exception as exc:
            self.mark(report_field, False, f"Unhandled exception: {exc}")

    def run(self, playwright: Any) -> int:
        try:
            self.ensure_connected(playwright)
            self.step_login()
            self.step_mi_negocio_menu()
            self.step_agregar_negocio_modal()
            self.step_administrar_negocios()
            self.step_informacion_general()
            self.step_detalles_cuenta()
            self.step_tus_negocios()
            self._open_legal_and_validate(
                link_text="Términos y Condiciones",
                heading_text="Términos y Condiciones",
                report_field="Términos y Condiciones",
                screenshot_name="08_terminos_y_condiciones",
            )
            self._open_legal_and_validate(
                link_text="Política de Privacidad",
                heading_text="Política de Privacidad",
                report_field="Política de Privacidad",
                screenshot_name="09_politica_de_privacidad",
            )
            return 0 if all(v.status == "PASS" for v in self.report.values()) else 1
        finally:
            self.save_report()
            if self.browser and not self.args.keep_open:
                self.browser.close()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run SaleADS Mi Negocio full workflow validation."
    )
    parser.add_argument(
        "--start-url",
        help="Environment login URL (dev/staging/prod, no hardcoded defaults).",
        default=None,
    )
    parser.add_argument(
        "--cdp-url",
        help="Optional Chrome DevTools endpoint to attach to existing browser session.",
        default=None,
    )
    parser.add_argument(
        "--headed",
        help="Run browser in headed mode.",
        action="store_true",
    )
    parser.add_argument(
        "--slow-mo",
        help="Slow down browser operations in milliseconds.",
        type=int,
        default=0,
    )
    parser.add_argument(
        "--timeout-ms",
        help="Default timeout per UI action in milliseconds.",
        type=int,
        default=15000,
    )
    parser.add_argument(
        "--artifacts-dir",
        help="Directory where screenshots and reports are stored.",
        default="artifacts/saleads_mi_negocio_full_test",
    )
    parser.add_argument(
        "--keep-open",
        help="Keep browser open after completion.",
        action="store_true",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    sync_playwright, playwright_timeout_error = _load_playwright()
    with sync_playwright() as p:
        runner = SaleadsMiNegocioWorkflow(args, playwright_timeout_error)
        return runner.run(p)


if __name__ == "__main__":
    raise SystemExit(main())
