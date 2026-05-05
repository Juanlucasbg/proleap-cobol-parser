#!/usr/bin/env python3
"""End-to-end workflow validation for SaleADS Mi Negocio module.

This script is intentionally environment-agnostic:
- It does not hardcode a SaleADS domain.
- It accepts URL/runtime settings from arguments or env vars.
- It uses visible-text driven locators as the primary strategy.
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
from typing import Optional

from playwright.sync_api import BrowserContext, Page, TimeoutError as PlaywrightTimeoutError, sync_playwright


DEFAULT_EMAIL = "juanlucasbarbiergarzon@gmail.com"
DEFAULT_TIMEOUT_MS = 30_000


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def slug(label: str) -> str:
    clean = re.sub(r"[^a-zA-Z0-9]+", "_", label.strip().lower()).strip("_")
    return clean or "step"


@dataclass
class StepResult:
    status: str = "FAIL"
    details: list[str] = field(default_factory=list)
    screenshots: list[str] = field(default_factory=list)
    final_url: Optional[str] = None


class Reporter:
    def __init__(self, output_dir: Path) -> None:
        self.output_dir = output_dir
        self.screenshots_dir = output_dir / "screenshots"
        self.screenshots_dir.mkdir(parents=True, exist_ok=True)
        self.results: dict[str, StepResult] = {
            "Login": StepResult(),
            "Mi Negocio menu": StepResult(),
            "Agregar Negocio modal": StepResult(),
            "Administrar Negocios view": StepResult(),
            "Información General": StepResult(),
            "Detalles de la Cuenta": StepResult(),
            "Tus Negocios": StepResult(),
            "Términos y Condiciones": StepResult(),
            "Política de Privacidad": StepResult(),
        }

    def add_detail(self, field_name: str, detail: str) -> None:
        self.results[field_name].details.append(detail)

    def set_status(self, field_name: str, ok: bool) -> None:
        self.results[field_name].status = "PASS" if ok else "FAIL"

    def add_screenshot(self, field_name: str, screenshot_name: str) -> None:
        self.results[field_name].screenshots.append(f"screenshots/{screenshot_name}")

    def set_final_url(self, field_name: str, value: str) -> None:
        self.results[field_name].final_url = value

    def write(self, metadata: dict) -> Path:
        payload = {
            "name": "saleads_mi_negocio_full_test",
            "generated_at": now_iso(),
            "metadata": metadata,
            "results": {k: v.__dict__ for k, v in self.results.items()},
        }
        report_path = self.output_dir / "report.json"
        report_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
        return report_path


def wait_for_ui(page: Page) -> None:
    page.wait_for_load_state("domcontentloaded", timeout=DEFAULT_TIMEOUT_MS)
    try:
        page.wait_for_load_state("networkidle", timeout=7_500)
    except PlaywrightTimeoutError:
        # Some UIs are chatty and never become fully idle.
        pass
    page.wait_for_timeout(600)


def visible_text_regex(label: str) -> re.Pattern[str]:
    escaped = re.escape(label)
    return re.compile(rf"^\s*{escaped}\s*$", re.IGNORECASE)


def click_visible_text(page: Page, labels: list[str], within: Optional[Page] = None) -> bool:
    scope = within or page
    for label in labels:
        pattern = visible_text_regex(label)
        candidates = [
            scope.get_by_role("button", name=pattern),
            scope.get_by_role("link", name=pattern),
            scope.get_by_role("menuitem", name=pattern),
            scope.get_by_role("tab", name=pattern),
            scope.get_by_role("heading", name=pattern),
            scope.get_by_text(pattern),
        ]
        for locator in candidates:
            try:
                locator.first.click(timeout=4_500)
                wait_for_ui(page)
                return True
            except Exception:
                continue
    return False


def text_visible(page: Page, label: str, timeout_ms: int = 6_000) -> bool:
    pattern = re.compile(re.escape(label), re.IGNORECASE)
    try:
        page.get_by_text(pattern).first.wait_for(state="visible", timeout=timeout_ms)
        return True
    except PlaywrightTimeoutError:
        return False


def save_screenshot(page: Page, reporter: Reporter, field_name: str, screenshot_name: str, full_page: bool = False) -> None:
    path = reporter.screenshots_dir / screenshot_name
    page.screenshot(path=str(path), full_page=full_page)
    reporter.add_screenshot(field_name, screenshot_name)


def safe_capture(page: Page, reporter: Reporter, field_name: str, screenshot_name: str, full_page: bool = False) -> None:
    try:
        save_screenshot(page, reporter, field_name, screenshot_name, full_page=full_page)
    except Exception:
        reporter.add_detail(field_name, f"FAIL: Could not capture screenshot '{screenshot_name}'")


def validate_any(page: Page, checks: list[str]) -> tuple[bool, list[str]]:
    details: list[str] = []
    ok = True
    for label in checks:
        exists = text_visible(page, label)
        details.append(f"{'PASS' if exists else 'FAIL'}: '{label}' visible")
        ok = ok and exists
    return ok, details


def select_google_account_if_present(page: Page, email: str) -> bool:
    candidate_patterns = [
        re.compile(re.escape(email), re.IGNORECASE),
        re.compile(r"choose an account", re.IGNORECASE),
        re.compile(r"elije una cuenta|elige una cuenta", re.IGNORECASE),
    ]
    for pattern in candidate_patterns:
        try:
            page.get_by_text(pattern).first.wait_for(state="visible", timeout=4_000)
            break
        except PlaywrightTimeoutError:
            continue
    else:
        return False

    try:
        page.get_by_text(re.compile(re.escape(email), re.IGNORECASE)).first.click(timeout=6_000)
        wait_for_ui(page)
        return True
    except Exception:
        return False


def open_legal_link(page: Page, context: BrowserContext, labels: list[str]) -> tuple[Page, bool]:
    existing_pages = set(context.pages)
    clicked = click_visible_text(page, labels)
    if not clicked:
        return page, False

    page.wait_for_timeout(1200)
    new_pages = [p for p in context.pages if p not in existing_pages]
    if new_pages:
        target = new_pages[-1]
        target.bring_to_front()
        wait_for_ui(target)
        return target, True

    return page, False


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate SaleADS Mi Negocio workflow end-to-end.")
    parser.add_argument(
        "--url",
        default=os.getenv("SALEADS_LOGIN_URL", ""),
        help="SaleADS login URL for the current environment. Optional, but required for unattended runs.",
    )
    parser.add_argument(
        "--email",
        default=os.getenv("SALEADS_GOOGLE_EMAIL", DEFAULT_EMAIL),
        help="Google account email to select if chooser appears.",
    )
    parser.add_argument(
        "--headless",
        action="store_true",
        default=os.getenv("SALEADS_HEADLESS", "").lower() in {"1", "true", "yes"},
        help="Run browser in headless mode.",
    )
    parser.add_argument(
        "--output-dir",
        default="automation/saleads/artifacts/latest",
        help="Directory where screenshots and report.json will be written.",
    )
    args = parser.parse_args()

    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    reporter = Reporter(output_dir)

    metadata = {
        "started_at": now_iso(),
        "headless": args.headless,
        "provided_url": args.url if args.url else "not provided",
        "google_email": args.email,
    }

    try:
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=args.headless)
            context = browser.new_context(viewport={"width": 1600, "height": 1000})
            page = context.new_page()

            # Step 1: Login with Google.
            login_ok = True
            if args.url:
                page.goto(args.url, wait_until="domcontentloaded", timeout=DEFAULT_TIMEOUT_MS)
                wait_for_ui(page)
                reporter.add_detail("Login", f"PASS: Opened login URL: {args.url}")
            else:
                login_ok = False
                reporter.add_detail(
                    "Login",
                    "FAIL: No --url provided. This unattended script needs the environment login URL.",
                )

            if login_ok:
                clicked_login = click_visible_text(
                    page,
                    [
                        "Sign in with Google",
                        "Login with Google",
                        "Iniciar sesión con Google",
                        "Continuar con Google",
                    ],
                )
                reporter.add_detail("Login", f"{'PASS' if clicked_login else 'FAIL'}: Clicked Google login button")
                login_ok = login_ok and clicked_login

            if login_ok:
                selected_account = select_google_account_if_present(page, args.email)
                if selected_account:
                    reporter.add_detail("Login", f"PASS: Selected Google account '{args.email}'")
                else:
                    reporter.add_detail(
                        "Login",
                        "PASS: Google account selector did not appear (likely already authenticated).",
                    )

            if login_ok:
                sidebar_visible = text_visible(page, "Negocio", timeout_ms=20_000) or text_visible(
                    page, "Mi Negocio", timeout_ms=20_000
                )
                reporter.add_detail(
                    "Login",
                    f"{'PASS' if sidebar_visible else 'FAIL'}: Left sidebar navigation is visible",
                )
                login_ok = login_ok and sidebar_visible

            if login_ok:
                safe_capture(page, reporter, "Login", "01_dashboard_loaded.png")
            reporter.set_status("Login", login_ok)

            if not login_ok:
                for field in reporter.results:
                    if field == "Login":
                        continue
                    reporter.add_detail(field, "FAIL: Skipped because login did not complete successfully.")
                    reporter.set_status(field, False)
                browser.close()
                metadata["finished_at"] = now_iso()
                report_path = reporter.write(metadata)
                print(f"Report written to: {report_path}")
                print(json.dumps({k: v.status for k, v in reporter.results.items()}, ensure_ascii=False, indent=2))
                return 1

            # Step 2: Open Mi Negocio menu.
            menu_ok = True
            negocio_click = click_visible_text(page, ["Negocio"])
            reporter.add_detail("Mi Negocio menu", f"{'PASS' if negocio_click else 'FAIL'}: Clicked 'Negocio'")
            menu_ok = menu_ok and negocio_click

            mi_negocio_click = click_visible_text(page, ["Mi Negocio"])
            reporter.add_detail("Mi Negocio menu", f"{'PASS' if mi_negocio_click else 'FAIL'}: Clicked 'Mi Negocio'")
            menu_ok = menu_ok and mi_negocio_click

            submenu_ok, submenu_details = validate_any(page, ["Agregar Negocio", "Administrar Negocios"])
            for detail in submenu_details:
                reporter.add_detail("Mi Negocio menu", detail)
            menu_ok = menu_ok and submenu_ok

            safe_capture(page, reporter, "Mi Negocio menu", "02_mi_negocio_menu_expanded.png")
            reporter.set_status("Mi Negocio menu", menu_ok)

            # Step 3: Validate Agregar Negocio modal.
            modal_ok = True
            clicked_agregar = click_visible_text(page, ["Agregar Negocio"])
            reporter.add_detail("Agregar Negocio modal", f"{'PASS' if clicked_agregar else 'FAIL'}: Clicked 'Agregar Negocio'")
            modal_ok = modal_ok and clicked_agregar

            modal_checks = [
                "Crear Nuevo Negocio",
                "Nombre del Negocio",
                "Tienes 2 de 3 negocios",
                "Cancelar",
                "Crear Negocio",
            ]
            check_ok, modal_details = validate_any(page, modal_checks)
            for detail in modal_details:
                reporter.add_detail("Agregar Negocio modal", detail)
            modal_ok = modal_ok and check_ok

            safe_capture(page, reporter, "Agregar Negocio modal", "03_agregar_negocio_modal.png")

            # Optional actions requested in prompt.
            try:
                input_field = page.get_by_label(re.compile(r"nombre del negocio", re.IGNORECASE)).first
                input_field.click(timeout=4_000)
                wait_for_ui(page)
                input_field.fill("Negocio Prueba Automatización", timeout=4_000)
                reporter.add_detail("Agregar Negocio modal", "PASS: Optional input fill completed")
            except Exception:
                reporter.add_detail("Agregar Negocio modal", "FAIL: Optional input fill not completed")

            cancel_clicked = click_visible_text(page, ["Cancelar"])
            reporter.add_detail(
                "Agregar Negocio modal",
                f"{'PASS' if cancel_clicked else 'FAIL'}: Clicked 'Cancelar' to close modal",
            )
            modal_ok = modal_ok and cancel_clicked
            reporter.set_status("Agregar Negocio modal", modal_ok)

            # Step 4: Open Administrar Negocios.
            admin_ok = True
            click_visible_text(page, ["Mi Negocio"])  # expand again if collapsed
            clicked_admin = click_visible_text(page, ["Administrar Negocios"])
            reporter.add_detail(
                "Administrar Negocios view",
                f"{'PASS' if clicked_admin else 'FAIL'}: Clicked 'Administrar Negocios'",
            )
            admin_ok = admin_ok and clicked_admin

            section_checks = ["Información General", "Detalles de la Cuenta", "Tus Negocios", "Sección Legal"]
            sections_ok, section_details = validate_any(page, section_checks)
            for detail in section_details:
                reporter.add_detail("Administrar Negocios view", detail)
            admin_ok = admin_ok and sections_ok

            safe_capture(page, reporter, "Administrar Negocios view", "04_administrar_negocios_page.png", full_page=True)
            reporter.set_status("Administrar Negocios view", admin_ok)

            # Step 5: Información General.
            info_ok, info_details = validate_any(page, ["BUSINESS PLAN", "Cambiar Plan"])
            for detail in info_details:
                reporter.add_detail("Información General", detail)

            # Generic checks for user name and email visibility.
            name_visible = (
                text_visible(page, "Nombre", timeout_ms=2_500)
                or text_visible(page, "Usuario", timeout_ms=2_500)
                or text_visible(page, "Name", timeout_ms=2_500)
                or text_visible(page, "Perfil", timeout_ms=2_500)
            )
            reporter.add_detail(
                "Información General",
                f"{'PASS' if name_visible else 'FAIL'}: User name or profile label visible",
            )

            body_text = page.inner_text("body")
            email_visible = text_visible(page, args.email, timeout_ms=2_500) or bool(re.search(r"\b\S+@\S+\.\S+\b", body_text))
            reporter.add_detail("Información General", f"{'PASS' if email_visible else 'FAIL'}: User email visible")
            info_ok = info_ok and email_visible
            info_ok = info_ok and name_visible
            reporter.set_status("Información General", info_ok)

            # Step 6: Detalles de la Cuenta.
            details_ok, account_details = validate_any(page, ["Cuenta creada", "Estado activo", "Idioma seleccionado"])
            for detail in account_details:
                reporter.add_detail("Detalles de la Cuenta", detail)
            reporter.set_status("Detalles de la Cuenta", details_ok)

            # Step 7: Tus Negocios.
            negocios_ok, negocios_details = validate_any(page, ["Tus Negocios", "Agregar Negocio", "Tienes 2 de 3 negocios"])
            for detail in negocios_details:
                reporter.add_detail("Tus Negocios", detail)
            reporter.set_status("Tus Negocios", negocios_ok)

            # Step 8: Términos y Condiciones.
            terms_ok = True
            app_page = page
            terms_page, opened_new_tab_terms = open_legal_link(page, context, ["Términos y Condiciones", "Terminos y Condiciones"])
            if terms_page is page and not opened_new_tab_terms:
                # click may fail if link element is not role-detected; fallback to text click.
                try:
                    page.get_by_text(re.compile(r"t[eé]rminos y condiciones", re.IGNORECASE)).first.click(timeout=6_000)
                    wait_for_ui(page)
                    terms_page = page
                    reporter.add_detail("Términos y Condiciones", "PASS: Clicked legal link via text fallback")
                except Exception:
                    terms_ok = False
                    reporter.add_detail("Términos y Condiciones", "FAIL: Could not click 'Términos y Condiciones'")
            else:
                reporter.add_detail(
                    "Términos y Condiciones",
                    f"PASS: Opened terms link ({'new tab' if opened_new_tab_terms else 'same tab'})",
                )

            if terms_ok:
                heading_visible = text_visible(terms_page, "Términos y Condiciones") or text_visible(
                    terms_page, "Terminos y Condiciones"
                )
                reporter.add_detail(
                    "Términos y Condiciones",
                    f"{'PASS' if heading_visible else 'FAIL'}: Terms heading visible",
                )
                terms_ok = terms_ok and heading_visible

                body_text = terms_page.inner_text("body")
                legal_text_visible = len(body_text.strip()) > 200
                reporter.add_detail(
                    "Términos y Condiciones",
                    f"{'PASS' if legal_text_visible else 'FAIL'}: Legal content text visible",
                )
                terms_ok = terms_ok and legal_text_visible

                safe_capture(terms_page, reporter, "Términos y Condiciones", "05_terminos_y_condiciones.png")
                reporter.set_final_url("Términos y Condiciones", terms_page.url)

            if opened_new_tab_terms and terms_page is not app_page:
                terms_page.close()
                app_page.bring_to_front()
                wait_for_ui(app_page)
            elif terms_page is app_page:
                try:
                    app_page.go_back(timeout=10_000)
                    wait_for_ui(app_page)
                except Exception:
                    pass
            reporter.set_status("Términos y Condiciones", terms_ok)

            # Step 9: Política de Privacidad.
            privacy_ok = True
            privacy_page, opened_new_tab_privacy = open_legal_link(
                app_page, context, ["Política de Privacidad", "Politica de Privacidad"]
            )
            if privacy_page is app_page and not opened_new_tab_privacy:
                try:
                    app_page.get_by_text(re.compile(r"pol[íi]tica de privacidad", re.IGNORECASE)).first.click(timeout=6_000)
                    wait_for_ui(app_page)
                    privacy_page = app_page
                    reporter.add_detail("Política de Privacidad", "PASS: Clicked privacy link via text fallback")
                except Exception:
                    privacy_ok = False
                    reporter.add_detail("Política de Privacidad", "FAIL: Could not click 'Política de Privacidad'")
            else:
                reporter.add_detail(
                    "Política de Privacidad",
                    f"PASS: Opened privacy link ({'new tab' if opened_new_tab_privacy else 'same tab'})",
                )

            if privacy_ok:
                heading_visible = text_visible(privacy_page, "Política de Privacidad") or text_visible(
                    privacy_page, "Politica de Privacidad"
                )
                reporter.add_detail("Política de Privacidad", f"{'PASS' if heading_visible else 'FAIL'}: Privacy heading visible")
                privacy_ok = privacy_ok and heading_visible

                body_text = privacy_page.inner_text("body")
                legal_text_visible = len(body_text.strip()) > 200
                reporter.add_detail(
                    "Política de Privacidad",
                    f"{'PASS' if legal_text_visible else 'FAIL'}: Legal content text visible",
                )
                privacy_ok = privacy_ok and legal_text_visible

                safe_capture(privacy_page, reporter, "Política de Privacidad", "06_politica_de_privacidad.png")
                reporter.set_final_url("Política de Privacidad", privacy_page.url)

            if opened_new_tab_privacy and privacy_page is not app_page:
                privacy_page.close()
                app_page.bring_to_front()
                wait_for_ui(app_page)
            elif privacy_page is app_page:
                try:
                    app_page.go_back(timeout=10_000)
                    wait_for_ui(app_page)
                except Exception:
                    pass
            reporter.set_status("Política de Privacidad", privacy_ok)

            browser.close()
    except Exception as exc:  # noqa: BLE001
        metadata["fatal_error"] = str(exc)

    metadata["finished_at"] = now_iso()
    report_path = reporter.write(metadata)
    print(f"Report written to: {report_path}")

    overall_ok = all(step.status == "PASS" for step in reporter.results.values())
    print(json.dumps({k: v.status for k, v in reporter.results.items()}, ensure_ascii=False, indent=2))
    return 0 if overall_ok else 1


if __name__ == "__main__":
    sys.exit(main())
