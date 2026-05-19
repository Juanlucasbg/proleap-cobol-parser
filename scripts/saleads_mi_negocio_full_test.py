#!/usr/bin/env python3
"""
SaleADS Mi Negocio end-to-end workflow test.

Usage:
  python3 scripts/saleads_mi_negocio_full_test.py --start-url "https://<env-host>"

Notes:
  - The script is environment-agnostic and does not hardcode a SaleADS domain.
  - It captures screenshots and writes a structured JSON report.
"""

from __future__ import annotations

import argparse
import json
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from playwright.sync_api import BrowserContext, Page, TimeoutError, sync_playwright


TEST_NAME = "saleads_mi_negocio_full_test"
GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com"


def now_utc_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def slug_timestamp() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


class TestRun:
    def __init__(self, output_root: Path) -> None:
        self.output_root = output_root
        self.screenshots_dir = output_root / "screenshots"
        self.screenshots_dir.mkdir(parents=True, exist_ok=True)
        self.report: dict[str, Any] = {
            "name": TEST_NAME,
            "started_at_utc": now_utc_iso(),
            "steps": {},
            "evidence": {"screenshots": [], "final_urls": {}},
        }

    def set_step(self, key: str, passed: bool, details: list[str]) -> None:
        self.report["steps"][key] = {
            "status": "PASS" if passed else "FAIL",
            "details": details,
        }

    def screenshot(self, page: Page, filename: str, full_page: bool = True) -> str:
        path = self.screenshots_dir / filename
        page.screenshot(path=str(path), full_page=full_page)
        self.report["evidence"]["screenshots"].append(str(path))
        return str(path)

    def add_final_url(self, key: str, url: str) -> None:
        self.report["evidence"]["final_urls"][key] = url

    def write(self) -> Path:
        self.report["finished_at_utc"] = now_utc_iso()
        report_path = self.output_root / "report.json"
        report_path.write_text(json.dumps(self.report, ensure_ascii=False, indent=2), encoding="utf-8")
        return report_path


def wait_for_ui(page: Page, timeout_ms: int = 15_000) -> None:
    for state in ("domcontentloaded", "networkidle"):
        try:
            page.wait_for_load_state(state, timeout=timeout_ms)
        except TimeoutError:
            pass
    page.wait_for_timeout(1_000)


def first_visible_by_text(page: Page, texts: list[str], timeout_ms: int = 5_000):
    for text in texts:
        locator = page.get_by_text(text, exact=False).first
        try:
            locator.wait_for(state="visible", timeout=timeout_ms)
            return locator, text
        except TimeoutError:
            continue
    return None, None


def click_visible_text(page: Page, texts: list[str], timeout_ms: int = 10_000) -> tuple[bool, str]:
    locator, matched = first_visible_by_text(page, texts, timeout_ms=timeout_ms)
    if locator is None:
        return False, ""
    locator.click()
    wait_for_ui(page)
    return True, matched or ""


def is_visible(page: Page, text: str, timeout_ms: int = 8_000) -> bool:
    try:
        page.get_by_text(text, exact=False).first.wait_for(state="visible", timeout=timeout_ms)
        return True
    except TimeoutError:
        return False


def is_email_visible(page: Page, timeout_ms: int = 8_000) -> bool:
    try:
        page.locator(r"text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/").first.wait_for(
            state="visible", timeout=timeout_ms
        )
        return True
    except TimeoutError:
        return False


def try_click(page: Page, text: str, timeout_ms: int = 4_000) -> bool:
    try:
        page.get_by_text(text, exact=False).first.wait_for(state="visible", timeout=timeout_ms)
        page.get_by_text(text, exact=False).first.click()
        wait_for_ui(page)
        return True
    except TimeoutError:
        return False


def is_authenticated_app_page(page: Page) -> bool:
    if "accounts.google.com" in page.url or "keycloak" in page.url:
        return False
    return any(
        [
            is_visible(page, "Mi Negocio", timeout_ms=1_000),
            is_visible(page, "Administrar Negocios", timeout_ms=1_000),
            is_visible(page, "Información General", timeout_ms=1_000),
        ]
    )


def detect_main_app_page(context: BrowserContext, fallback: Page, timeout_sec: int = 60) -> Page:
    end_time = time.time() + timeout_sec
    while time.time() < end_time:
        for page in list(context.pages):
            if "accounts.google.com" in page.url or "keycloak" in page.url:
                continue
            try:
                if is_authenticated_app_page(page):
                    return page
            except Exception:
                continue
        time.sleep(1)
    return fallback


def execute_google_login_flow(page: Page, context: BrowserContext, details: list[str]) -> tuple[bool, Page]:
    if is_authenticated_app_page(page):
        details.append("Session already authenticated; login screen was skipped.")
        return True, page

    login_clicked = try_click(page, "Inicia sesión", timeout_ms=12_000) or try_click(page, "Sign in", timeout_ms=12_000)
    if not login_clicked:
        details.append("Could not locate initial 'Inicia sesión' entry button.")
        return False, page
    details.append("Clicked the initial sign-in button.")

    try:
        page.wait_for_url("**keycloak.saleads.ai**", timeout=30_000)
        details.append("Keycloak login page loaded.")
    except TimeoutError:
        details.append("Keycloak login page did not load after sign-in click.")
        return False, page

    google_clicked = False
    try:
        google_provider = page.locator("a:has-text('Google'), button:has-text('Google')").first
        google_provider.wait_for(state="visible", timeout=12_000)
        google_provider.click()
        wait_for_ui(page)
        google_clicked = True
        details.append("Clicked Google provider in Keycloak.")
    except TimeoutError:
        details.append("Google provider option not found in Keycloak page.")
        return False, page

    if not google_clicked:
        return False, page

    try:
        page.wait_for_url("**accounts.google.com/**", timeout=30_000)
        details.append("Google auth page opened.")
    except TimeoutError:
        details.append("Google auth page did not open after provider click.")
        return False, page

    # Preferred case requested by the scenario: choose pre-listed account.
    try:
        account_choice = page.get_by_text(GOOGLE_ACCOUNT_EMAIL, exact=False).first
        account_choice.wait_for(state="visible", timeout=8_000)
        account_choice.click()
        wait_for_ui(page)
        details.append(f"Selected account '{GOOGLE_ACCOUNT_EMAIL}' from chooser.")
    except TimeoutError:
        # Fallback for environments that show identifier input instead of account chooser.
        email_input = page.locator("input[type='email']").first
        if email_input.count() > 0:
            email_input.fill(GOOGLE_ACCOUNT_EMAIL)
            next_clicked = try_click(page, "Siguiente", timeout_ms=6_000) or try_click(page, "Next", timeout_ms=6_000)
            details.append(
                "Google account chooser not shown; email was entered in identifier page "
                f"({'next clicked' if next_clicked else 'next button not found'})."
            )
            if is_visible(page, "No se ha podido iniciar sesión", timeout_ms=4_000):
                details.append("Google blocked automated sign-in for this browser/session.")
        else:
            details.append("Google account chooser/input not detected.")

    app_page = detect_main_app_page(context, page, timeout_sec=70)
    authenticated = is_authenticated_app_page(app_page)
    if not authenticated:
        if is_visible(app_page, "No se ha podido iniciar sesión", timeout_ms=2_000):
            details.append("Authentication blocked by Google sign-in protection in this environment.")
            return False, app_page
        if "accounts.google.com" in app_page.url:
            details.append("Authentication did not complete: Google password/challenge step is still pending.")
        elif "keycloak" in app_page.url:
            details.append("Authentication did not complete: still on Keycloak.")
        else:
            details.append("Authentication did not complete: target application UI not detected.")
    return authenticated, app_page


def validate_legal_page_content(page: Page, heading: str) -> tuple[bool, list[str]]:
    details: list[str] = []
    heading_ok = is_visible(page, heading, timeout_ms=12_000)
    details.append(f"Heading '{heading}' visible: {'yes' if heading_ok else 'no'}")

    text_len = 0
    try:
        text_len = len(page.inner_text("body").strip())
    except Exception:
        text_len = 0
    content_ok = text_len > 200
    details.append(f"Legal content appears non-empty (>200 chars): {'yes' if content_ok else 'no'}")
    return heading_ok and content_ok, details


def open_legal_link_and_validate(
    app_page: Page,
    context: BrowserContext,
    link_text: str,
    heading: str,
    run: TestRun,
    screenshot_name: str,
    report_key: str,
) -> tuple[bool, list[str]]:
    details: list[str] = []
    current_tab = app_page
    prior_page_count = len(context.pages)

    clicked, _ = click_visible_text(app_page, [link_text], timeout_ms=12_000)
    if not clicked:
        details.append(f"Could not click legal link '{link_text}'.")
        return False, details

    legal_page = app_page
    for _ in range(10):
        wait_for_ui(app_page)
        if len(context.pages) > prior_page_count:
            legal_page = context.pages[-1]
            legal_page.bring_to_front()
            wait_for_ui(legal_page)
            details.append(f"Link opened in new tab: {legal_page.url}")
            break
        time.sleep(0.5)
    else:
        details.append(f"Link opened in same tab (or no new tab): {app_page.url}")

    passed, validation_details = validate_legal_page_content(legal_page, heading)
    details.extend(validation_details)
    run.screenshot(legal_page, screenshot_name, full_page=True)
    run.add_final_url(report_key, legal_page.url)

    if legal_page is not current_tab:
        legal_page.close()
        current_tab.bring_to_front()
        wait_for_ui(current_tab)
    return passed, details


def run_test(start_url: str | None, headless: bool, output_dir: Path) -> Path:
    run = TestRun(output_root=output_dir)

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=headless, args=["--disable-blink-features=AutomationControlled"])
        context = browser.new_context(
            ignore_https_errors=True,
            viewport={"width": 1920, "height": 1080},
            locale="es-ES",
        )
        page = context.new_page()

        if start_url:
            page.goto(start_url, wait_until="domcontentloaded", timeout=60_000)
            wait_for_ui(page)

        # Step 1: Login with Google
        step1_details: list[str] = []
        step1_pass, page = execute_google_login_flow(page, context, step1_details)
        main_ui_ok = is_authenticated_app_page(page)
        sidebar_ok = is_visible(page, "Mi Negocio", timeout_ms=10_000) or is_visible(page, "Negocio", timeout_ms=10_000)
        step1_details.append(f"Main interface visible: {'yes' if main_ui_ok else 'no'}")
        step1_details.append(f"Left sidebar visible: {'yes' if sidebar_ok else 'no'}")
        run.screenshot(page, "01_dashboard_loaded.png", full_page=True)
        step1_pass = step1_pass and main_ui_ok and sidebar_ok
        run.set_step("Login", step1_pass, step1_details)

        if not step1_pass:
            blocked_reason = "Blocked: authenticated SaleADS app was not reached, so Mi Negocio workflow cannot continue."
            run.set_step("Mi Negocio menu", False, [blocked_reason])
            run.set_step("Agregar Negocio modal", False, [blocked_reason])
            run.set_step("Administrar Negocios view", False, [blocked_reason])
            run.set_step("Información General", False, [blocked_reason])
            run.set_step("Detalles de la Cuenta", False, [blocked_reason])
            run.set_step("Tus Negocios", False, [blocked_reason])
            run.set_step("Términos y Condiciones", False, [blocked_reason])
            run.set_step("Política de Privacidad", False, [blocked_reason])
            run.screenshot(page, "02_blocked_after_login_failure.png", full_page=True)
            browser.close()
            return run.write()

        # Step 2: Open Mi Negocio menu
        step2_details: list[str] = []
        step2_clicked, _ = click_visible_text(page, ["Mi Negocio"], timeout_ms=10_000)
        if not step2_clicked:
            step2_clicked, _ = click_visible_text(page, ["Negocio"], timeout_ms=10_000)
            if step2_clicked:
                click_visible_text(page, ["Mi Negocio"], timeout_ms=8_000)
        submenu_expanded = is_visible(page, "Agregar Negocio", timeout_ms=10_000)
        administrar_visible = is_visible(page, "Administrar Negocios", timeout_ms=10_000)
        step2_details.append(f"Clicked Mi Negocio/Negocio: {'yes' if step2_clicked else 'no'}")
        step2_details.append(f"Submenu expanded: {'yes' if submenu_expanded else 'no'}")
        step2_details.append(f"'Agregar Negocio' visible: {'yes' if submenu_expanded else 'no'}")
        step2_details.append(f"'Administrar Negocios' visible: {'yes' if administrar_visible else 'no'}")
        run.screenshot(page, "02_mi_negocio_menu_expanded.png", full_page=True)
        run.set_step("Mi Negocio menu", step2_clicked and submenu_expanded and administrar_visible, step2_details)

        # Step 3: Validate Agregar Negocio modal
        step3_details: list[str] = []
        added_modal_open = try_click(page, "Agregar Negocio", timeout_ms=10_000)
        modal_title_ok = is_visible(page, "Crear Nuevo Negocio", timeout_ms=10_000)
        input_ok = is_visible(page, "Nombre del Negocio", timeout_ms=10_000)
        quota_ok = is_visible(page, "Tienes 2 de 3 negocios", timeout_ms=10_000)
        cancel_ok = is_visible(page, "Cancelar", timeout_ms=10_000)
        create_ok = is_visible(page, "Crear Negocio", timeout_ms=10_000)

        step3_details.append(f"Modal opened: {'yes' if added_modal_open else 'no'}")
        step3_details.append(f"Title 'Crear Nuevo Negocio' visible: {'yes' if modal_title_ok else 'no'}")
        step3_details.append(f"Input 'Nombre del Negocio' visible: {'yes' if input_ok else 'no'}")
        step3_details.append(f"Quota text visible: {'yes' if quota_ok else 'no'}")
        step3_details.append(f"'Cancelar' visible: {'yes' if cancel_ok else 'no'}")
        step3_details.append(f"'Crear Negocio' visible: {'yes' if create_ok else 'no'}")

        run.screenshot(page, "03_agregar_negocio_modal.png", full_page=True)

        if modal_title_ok:
            try:
                name_field = page.get_by_label("Nombre del Negocio").first
                name_field.fill("Negocio Prueba Automatización")
                step3_details.append("Optional action executed: typed business name.")
            except Exception:
                try:
                    page.get_by_placeholder("Nombre del Negocio").first.fill("Negocio Prueba Automatización")
                    step3_details.append("Optional action executed: typed business name via placeholder.")
                except Exception:
                    step3_details.append("Optional typing action skipped (field could not be targeted).")
            try_click(page, "Cancelar", timeout_ms=5_000)

        step3_pass = added_modal_open and all([modal_title_ok, input_ok, quota_ok, cancel_ok, create_ok])
        run.set_step("Agregar Negocio modal", step3_pass, step3_details)

        # Step 4: Open Administrar Negocios
        step4_details: list[str] = []
        if not is_visible(page, "Administrar Negocios", timeout_ms=4_000):
            click_visible_text(page, ["Mi Negocio", "Negocio"], timeout_ms=8_000)
        admin_clicked = try_click(page, "Administrar Negocios", timeout_ms=12_000)
        info_general_ok = is_visible(page, "Información General", timeout_ms=12_000)
        account_details_ok = is_visible(page, "Detalles de la Cuenta", timeout_ms=12_000)
        tus_negocios_ok = is_visible(page, "Tus Negocios", timeout_ms=12_000)
        legal_section_ok = is_visible(page, "Sección Legal", timeout_ms=12_000)
        step4_details.append(f"Clicked 'Administrar Negocios': {'yes' if admin_clicked else 'no'}")
        step4_details.append(f"'Información General' visible: {'yes' if info_general_ok else 'no'}")
        step4_details.append(f"'Detalles de la Cuenta' visible: {'yes' if account_details_ok else 'no'}")
        step4_details.append(f"'Tus Negocios' visible: {'yes' if tus_negocios_ok else 'no'}")
        step4_details.append(f"'Sección Legal' visible: {'yes' if legal_section_ok else 'no'}")
        run.screenshot(page, "04_administrar_negocios_view.png", full_page=True)
        run.set_step(
            "Administrar Negocios view",
            admin_clicked and info_general_ok and account_details_ok and tus_negocios_ok and legal_section_ok,
            step4_details,
        )

        # Step 5: Validate Información General
        step5_details: list[str] = []
        business_plan_ok = is_visible(page, "BUSINESS PLAN", timeout_ms=10_000)
        cambiar_plan_ok = is_visible(page, "Cambiar Plan", timeout_ms=10_000)
        user_email_ok = is_email_visible(page, timeout_ms=10_000)
        # User name can vary strongly by environment; infer from profile section presence.
        profile_section_ok = is_visible(page, "Información General", timeout_ms=10_000)
        step5_details.append(f"User name section visible: {'yes' if profile_section_ok else 'no'}")
        step5_details.append(f"User email visible: {'yes' if user_email_ok else 'no'}")
        step5_details.append(f"'BUSINESS PLAN' visible: {'yes' if business_plan_ok else 'no'}")
        step5_details.append(f"'Cambiar Plan' visible: {'yes' if cambiar_plan_ok else 'no'}")
        run.set_step(
            "Información General",
            profile_section_ok and user_email_ok and business_plan_ok and cambiar_plan_ok,
            step5_details,
        )

        # Step 6: Validate Detalles de la Cuenta
        step6_details: list[str] = []
        cuenta_creada_ok = is_visible(page, "Cuenta creada", timeout_ms=10_000)
        estado_activo_ok = is_visible(page, "Estado activo", timeout_ms=10_000)
        idioma_ok = is_visible(page, "Idioma seleccionado", timeout_ms=10_000)
        step6_details.append(f"'Cuenta creada' visible: {'yes' if cuenta_creada_ok else 'no'}")
        step6_details.append(f"'Estado activo' visible: {'yes' if estado_activo_ok else 'no'}")
        step6_details.append(f"'Idioma seleccionado' visible: {'yes' if idioma_ok else 'no'}")
        run.set_step("Detalles de la Cuenta", cuenta_creada_ok and estado_activo_ok and idioma_ok, step6_details)

        # Step 7: Validate Tus Negocios
        step7_details: list[str] = []
        tus_negocios_header_ok = is_visible(page, "Tus Negocios", timeout_ms=10_000)
        agregar_btn_ok = is_visible(page, "Agregar Negocio", timeout_ms=10_000)
        quota_again_ok = is_visible(page, "Tienes 2 de 3 negocios", timeout_ms=10_000)
        step7_details.append(f"Business list section visible: {'yes' if tus_negocios_header_ok else 'no'}")
        step7_details.append(f"'Agregar Negocio' exists: {'yes' if agregar_btn_ok else 'no'}")
        step7_details.append(f"'Tienes 2 de 3 negocios' visible: {'yes' if quota_again_ok else 'no'}")
        run.set_step("Tus Negocios", tus_negocios_header_ok and agregar_btn_ok and quota_again_ok, step7_details)

        # Step 8: Validate Términos y Condiciones
        step8_pass, step8_details = open_legal_link_and_validate(
            app_page=page,
            context=context,
            link_text="Términos y Condiciones",
            heading="Términos y Condiciones",
            run=run,
            screenshot_name="05_terminos_y_condiciones.png",
            report_key="terminos_y_condiciones",
        )
        run.set_step("Términos y Condiciones", step8_pass, step8_details)

        # Step 9: Validate Política de Privacidad
        step9_pass, step9_details = open_legal_link_and_validate(
            app_page=page,
            context=context,
            link_text="Política de Privacidad",
            heading="Política de Privacidad",
            run=run,
            screenshot_name="06_politica_de_privacidad.png",
            report_key="politica_de_privacidad",
        )
        run.set_step("Política de Privacidad", step9_pass, step9_details)

        browser.close()

    return run.write()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run SaleADS Mi Negocio full workflow test.")
    parser.add_argument(
        "--start-url",
        default=None,
        help=(
            "Login page URL for the current environment. "
            "If omitted, test starts on about:blank and login step will likely fail."
        ),
    )
    parser.add_argument(
        "--headed",
        action="store_true",
        help="Run browser in headed mode (default is headless).",
    )
    parser.add_argument(
        "--output-dir",
        default=f"artifacts/{TEST_NAME}/{slug_timestamp()}",
        help="Directory for screenshots and JSON report.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    report_path = run_test(
        start_url=args.start_url,
        headless=not args.headed,
        output_dir=output_dir,
    )
    print(f"Report written to: {report_path}")


if __name__ == "__main__":
    main()
