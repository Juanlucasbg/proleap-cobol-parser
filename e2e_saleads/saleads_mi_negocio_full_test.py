#!/usr/bin/env python3
"""
SaleADS Mi Negocio full workflow validation.

This script is intentionally environment-agnostic:
- It never hardcodes a SaleADS domain.
- It prefers locating UI elements by visible text.
- It captures screenshots at key checkpoints.
- It returns a final PASS/FAIL report per requested validation area.
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
from typing import Dict, Optional, Pattern

from playwright.sync_api import (
    BrowserContext,
    Error as PlaywrightError,
    Locator,
    Page,
    TimeoutError as PlaywrightTimeoutError,
    sync_playwright,
)


ACCOUNT_EMAIL_DEFAULT = "juanlucasbarbiergarzon@gmail.com"
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
class ResultEntry:
    status: str = "FAIL"
    details: str = "Not executed"
    screenshot: Optional[str] = None
    url: Optional[str] = None


@dataclass
class TestReport:
    name: str
    started_at: str
    results: Dict[str, ResultEntry] = field(
        default_factory=lambda: {key: ResultEntry() for key in REPORT_FIELDS}
    )

    def mark(
        self,
        field_name: str,
        status: str,
        details: str,
        screenshot: Optional[str] = None,
        url: Optional[str] = None,
    ) -> None:
        self.results[field_name] = ResultEntry(
            status=status, details=details, screenshot=screenshot, url=url
        )

    def as_json(self) -> str:
        payload = {
            "name": self.name,
            "started_at": self.started_at,
            "finished_at": datetime.now(timezone.utc).isoformat(),
            "results": {
                key: {
                    "status": value.status,
                    "details": value.details,
                    "screenshot": value.screenshot,
                    "url": value.url,
                }
                for key, value in self.results.items()
            },
        }
        return json.dumps(payload, ensure_ascii=False, indent=2)


def now_stamp() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


def visible(locator: Locator, timeout_ms: int = 2500) -> bool:
    try:
        locator.first.wait_for(state="visible", timeout=timeout_ms)
        return True
    except PlaywrightTimeoutError:
        return False


def wait_ui(page: Page, timeout_ms: int = 15000) -> None:
    # "Always wait for the UI to load after each click."
    for state in ("domcontentloaded", "networkidle"):
        try:
            page.wait_for_load_state(state, timeout=timeout_ms)
        except PlaywrightTimeoutError:
            # Some single-page transitions may never reach full network idle.
            pass
    page.wait_for_timeout(700)


def screenshot(page: Page, output_dir: Path, name: str, full_page: bool = False) -> str:
    file_name = f"{name}_{now_stamp()}.png"
    target = output_dir / file_name
    page.screenshot(path=str(target), full_page=full_page)
    return str(target)


def body_text(page: Page) -> str:
    try:
        return page.locator("body").inner_text(timeout=5000)
    except PlaywrightError:
        return ""


def mark_remaining_as_fail(report: TestReport, reason: str) -> None:
    for field_name, entry in report.results.items():
        if entry.details == "Not executed":
            report.mark(
                field_name,
                "FAIL",
                f"Not executed because test aborted: {reason}",
            )


def text_pattern(text: str) -> Pattern[str]:
    return re.compile(rf"\b{re.escape(text)}\b", re.IGNORECASE)


def click_by_text(
    page: Page,
    labels: list[str],
    timeout_ms: int = 4000,
) -> bool:
    for label in labels:
        pattern = text_pattern(label)
        candidates = [
            page.get_by_role("button", name=pattern),
            page.get_by_role("link", name=pattern),
            page.get_by_role("menuitem", name=pattern),
            page.get_by_text(pattern),
        ]
        for candidate in candidates:
            if visible(candidate, timeout_ms=timeout_ms):
                candidate.first.click()
                wait_ui(page)
                return True
    return False


def text_visible(page: Page, label: str, timeout_ms: int = 4500) -> bool:
    return visible(page.get_by_text(text_pattern(label)), timeout_ms=timeout_ms)


def wait_sidebar(page: Page) -> bool:
    candidates = [
        page.locator("aside"),
        page.locator("[class*='sidebar']"),
        page.get_by_role("navigation"),
        page.locator("nav"),
    ]
    for loc in candidates:
        if visible(loc, timeout_ms=8000):
            return True
    return False


def login_with_google(
    page: Page, context: BrowserContext, account_email: str
) -> tuple[bool, str]:
    clicked = click_by_text(
        page,
        [
            "Sign in with Google",
            "Iniciar sesión con Google",
            "Continuar con Google",
            "Google",
        ],
    )
    if not clicked:
        return False, "Could not find Google login button."

    popup_page: Optional[Page] = None
    try:
        popup_page = context.wait_for_event("page", timeout=4500)
        popup_page.wait_for_load_state("domcontentloaded", timeout=10000)
    except PlaywrightTimeoutError:
        popup_page = None

    # Google account chooser may show in popup or same page.
    candidate_pages = [popup_page] if popup_page else [page]
    email_selected = False
    for candidate in candidate_pages:
        if candidate is None:
            continue
        account_pattern = re.compile(re.escape(account_email), re.IGNORECASE)
        if visible(candidate.get_by_text(account_pattern), timeout_ms=5000):
            candidate.get_by_text(account_pattern).first.click()
            wait_ui(candidate)
            email_selected = True
            break
        if visible(candidate.get_by_text(account_email), timeout_ms=5000):
            candidate.get_by_text(account_email).first.click()
            wait_ui(candidate)
            email_selected = True
            break

    if popup_page and not popup_page.is_closed():
        try:
            popup_page.wait_for_close(timeout=15000)
        except PlaywrightTimeoutError:
            pass
    page.bring_to_front()
    wait_ui(page, timeout_ms=20000)

    if not wait_sidebar(page):
        return (
            False,
            f"Login click succeeded but app shell/left sidebar not visible. "
            f"Account chosen: {'yes' if email_selected else 'no'}",
        )
    return True, f"Main interface and left sidebar are visible. Account chosen: {'yes' if email_selected else 'no'}."


def validate_mi_negocio_menu(page: Page) -> tuple[bool, str]:
    # Try directly first; if hidden, expand "Negocio".
    menu_clicked = click_by_text(page, ["Mi Negocio"])
    if not menu_clicked:
        click_by_text(page, ["Negocio"])
        menu_clicked = click_by_text(page, ["Mi Negocio"])
    if not menu_clicked:
        return False, "Could not open 'Mi Negocio' menu."

    agregar = text_visible(page, "Agregar Negocio", timeout_ms=7000)
    administrar = text_visible(page, "Administrar Negocios", timeout_ms=7000)
    if agregar and administrar:
        return True, "Submenu expanded and required options are visible."
    return False, (
        "Submenu validation failed: "
        f"Agregar Negocio={'yes' if agregar else 'no'}, "
        f"Administrar Negocios={'yes' if administrar else 'no'}."
    )


def validate_agregar_negocio_modal(page: Page, output_dir: Path) -> tuple[bool, str, Optional[str]]:
    if not click_by_text(page, ["Agregar Negocio"]):
        return False, "Could not click 'Agregar Negocio'.", None

    title_ok = text_visible(page, "Crear Nuevo Negocio", timeout_ms=7000)
    nombre_ok = text_visible(page, "Nombre del Negocio", timeout_ms=7000)
    quota_ok = text_visible(page, "Tienes 2 de 3 negocios", timeout_ms=7000)
    cancelar_ok = text_visible(page, "Cancelar", timeout_ms=7000)
    crear_ok = text_visible(page, "Crear Negocio", timeout_ms=7000)

    modal_shot = screenshot(page, output_dir, "03_agregar_negocio_modal")

    # Optional actions.
    if nombre_ok:
        nombre_input = page.locator(
            "input[placeholder*='Nombre del Negocio'], input[name*='negocio'], input[id*='negocio']"
        )
        if visible(nombre_input, timeout_ms=2500):
            nombre_input.first.click()
            nombre_input.first.fill("Negocio Prueba Automatización")
            wait_ui(page)

    if cancelar_ok:
        click_by_text(page, ["Cancelar"])

    all_ok = all([title_ok, nombre_ok, quota_ok, cancelar_ok, crear_ok])
    if all_ok:
        return True, "Modal and all required controls are visible.", modal_shot
    return False, (
        "Modal validation failed: "
        f"title={title_ok}, input={nombre_ok}, quota={quota_ok}, cancelar={cancelar_ok}, crear={crear_ok}."
    ), modal_shot


def open_administrar_negocios(page: Page) -> tuple[bool, str]:
    # Re-expand if needed.
    if not text_visible(page, "Administrar Negocios", timeout_ms=2000):
        click_by_text(page, ["Mi Negocio", "Negocio"])

    if not click_by_text(page, ["Administrar Negocios"]):
        return False, "Could not click 'Administrar Negocios'."

    required_sections = [
        "Información General",
        "Detalles de la Cuenta",
        "Tus Negocios",
        "Sección Legal",
    ]
    checks = {section: text_visible(page, section, timeout_ms=12000) for section in required_sections}
    all_ok = all(checks.values())
    if all_ok:
        return True, "Account page loaded with all required sections."
    return False, f"Missing required sections: {[name for name, ok in checks.items() if not ok]}"


def validate_informacion_general(page: Page) -> tuple[bool, str]:
    root_text = body_text(page)
    has_email = bool(re.search(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", root_text))
    has_plan = text_visible(page, "BUSINESS PLAN", timeout_ms=6000)
    has_cambiar = text_visible(page, "Cambiar Plan", timeout_ms=6000)

    # Heuristic for visible username: any non-empty line in the section that isn't a static label.
    section_block = page.locator("section, div, article").filter(has_text=text_pattern("Información General"))
    has_username = False
    if visible(section_block, timeout_ms=5000):
        section_text = section_block.first.inner_text(timeout=5000)
        ignored = {
            "información general",
            "business plan",
            "cambiar plan",
            "nombre",
            "email",
            "correo",
        }
        dynamic_lines = [
            line.strip()
            for line in section_text.splitlines()
            if line.strip() and line.strip().lower() not in ignored
        ]
        has_username = any(
            (not re.search(r"@", line)) and len(line) >= 3 and not line.lower().startswith("plan")
            for line in dynamic_lines
        )

    all_ok = has_username and has_email and has_plan and has_cambiar
    if all_ok:
        return True, "Name, email, BUSINESS PLAN and Cambiar Plan are visible."
    return False, (
        "Información General validation failed: "
        f"name={has_username}, email={has_email}, business_plan={has_plan}, cambiar_plan={has_cambiar}."
    )


def validate_detalles_cuenta(page: Page) -> tuple[bool, str]:
    checks = {
        "Cuenta creada": text_visible(page, "Cuenta creada", timeout_ms=6000),
        "Estado activo": text_visible(page, "Estado activo", timeout_ms=6000),
        "Idioma seleccionado": text_visible(page, "Idioma seleccionado", timeout_ms=6000),
    }
    if all(checks.values()):
        return True, "Detalles de la Cuenta labels are visible."
    return False, f"Missing labels in Detalles de la Cuenta: {[k for k, v in checks.items() if not v]}"


def validate_tus_negocios(page: Page) -> tuple[bool, str]:
    section_ok = text_visible(page, "Tus Negocios", timeout_ms=6000)
    add_button_ok = text_visible(page, "Agregar Negocio", timeout_ms=6000)
    quota_ok = text_visible(page, "Tienes 2 de 3 negocios", timeout_ms=6000)

    section = page.locator("section, div, article").filter(has_text=text_pattern("Tus Negocios"))
    list_items = 0
    if visible(section, timeout_ms=3000):
        list_items = (
            section.first.locator("li, [role='listitem'], table tbody tr, .business-item, [class*='negocio']").count()
        )

    list_ok = list_items > 0
    all_ok = section_ok and add_button_ok and quota_ok and list_ok
    if all_ok:
        return True, "Business list, add button and quota text are visible."
    return False, (
        "Tus Negocios validation failed: "
        f"section={section_ok}, add_button={add_button_ok}, quota={quota_ok}, list_items={list_items}."
    )


def validate_legal_link(
    page: Page,
    context: BrowserContext,
    link_text: str,
    expected_heading: str,
) -> tuple[bool, str, Optional[Page], Optional[str]]:
    new_page: Optional[Page] = None
    current_url_before = page.url

    try:
        with context.expect_page(timeout=5000) as new_page_info:
            clicked = click_by_text(page, [link_text], timeout_ms=5000)
            if not clicked:
                return False, f"Could not click legal link '{link_text}'.", None, None
        new_page = new_page_info.value
        new_page.wait_for_load_state("domcontentloaded", timeout=15000)
        wait_ui(new_page, timeout_ms=15000)
        active_page = new_page
    except PlaywrightTimeoutError:
        # Same-tab navigation fallback.
        clicked = click_by_text(page, [link_text], timeout_ms=5000)
        if not clicked:
            return False, f"Could not click legal link '{link_text}'.", None, None
        active_page = page
        wait_ui(active_page, timeout_ms=15000)

    heading_ok = visible(
        active_page.get_by_role("heading", name=re.compile(expected_heading, re.IGNORECASE)),
        timeout_ms=10000,
    ) or text_visible(active_page, expected_heading, timeout_ms=10000)

    content_text = body_text(active_page)
    content_ok = len(content_text.strip()) >= 300

    final_url = active_page.url
    if heading_ok and content_ok:
        details = f"Heading and legal content visible. URL={final_url}"
        return True, details, new_page, final_url
    return (
        False,
        (
            f"Legal page validation failed for '{link_text}': "
            f"heading={heading_ok}, content={content_ok}, url={final_url} (started from {current_url_before})"
        ),
        new_page,
        final_url,
    )


def cleanup_legal_navigation(main_page: Page, maybe_new_page: Optional[Page]) -> None:
    if maybe_new_page and not maybe_new_page.is_closed():
        maybe_new_page.close()
        main_page.bring_to_front()
        wait_ui(main_page)
        return

    # Same-tab fallback: return to application.
    try:
        main_page.go_back(timeout=15000)
        wait_ui(main_page)
    except PlaywrightError:
        pass


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="SaleADS Mi Negocio full test")
    parser.add_argument(
        "--base-url",
        default=os.getenv("SALEADS_START_URL"),
        help="Environment login URL. Can be provided via SALEADS_START_URL.",
    )
    parser.add_argument(
        "--account-email",
        default=os.getenv("SALEADS_GOOGLE_ACCOUNT", ACCOUNT_EMAIL_DEFAULT),
        help="Google account to select when the account chooser appears.",
    )
    parser.add_argument(
        "--output-dir",
        default="e2e_saleads/screenshots",
        help="Directory where screenshots are saved.",
    )
    parser.add_argument(
        "--report-path",
        default="e2e_saleads/final_report.json",
        help="Final JSON report path.",
    )
    parser.add_argument(
        "--headless",
        action="store_true",
        help="Run Chromium in headless mode.",
    )
    parser.add_argument(
        "--slow-mo-ms",
        type=int,
        default=120,
        help="Playwright slow motion delay in milliseconds.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    report_path = Path(args.report_path)
    report_path.parent.mkdir(parents=True, exist_ok=True)

    report = TestReport(
        name="saleads_mi_negocio_full_test",
        started_at=datetime.now(timezone.utc).isoformat(),
    )

    with sync_playwright() as pw:
        browser = pw.chromium.launch(headless=args.headless, slow_mo=args.slow_mo_ms)
        context = browser.new_context(viewport={"width": 1440, "height": 900})
        page = context.new_page()

        try:
            if not args.base_url:
                raise RuntimeError(
                    "Missing start URL. Provide --base-url (or SALEADS_START_URL) "
                    "for the login page in the current environment."
                )

            page.goto(args.base_url, wait_until="domcontentloaded", timeout=45000)
            wait_ui(page, timeout_ms=20000)

            # Step 1: Login
            ok, details = login_with_google(page, context, args.account_email)
            dashboard_shot = None
            if ok:
                dashboard_shot = screenshot(page, output_dir, "01_dashboard_loaded")
            report.mark("Login", "PASS" if ok else "FAIL", details, screenshot=dashboard_shot)

            # Step 2: Mi Negocio menu
            ok, details = validate_mi_negocio_menu(page)
            menu_shot = None
            if ok:
                menu_shot = screenshot(page, output_dir, "02_mi_negocio_expanded")
            report.mark("Mi Negocio menu", "PASS" if ok else "FAIL", details, screenshot=menu_shot)

            # Step 3: Agregar Negocio modal
            ok, details, modal_shot = validate_agregar_negocio_modal(page, output_dir)
            report.mark(
                "Agregar Negocio modal",
                "PASS" if ok else "FAIL",
                details,
                screenshot=modal_shot,
            )

            # Step 4: Administrar Negocios view
            ok, details = open_administrar_negocios(page)
            admin_shot = None
            if ok:
                admin_shot = screenshot(
                    page, output_dir, "04_administrar_negocios_page", full_page=True
                )
            report.mark(
                "Administrar Negocios view",
                "PASS" if ok else "FAIL",
                details,
                screenshot=admin_shot,
            )

            # Step 5: Información General
            ok, details = validate_informacion_general(page)
            report.mark("Información General", "PASS" if ok else "FAIL", details)

            # Step 6: Detalles de la Cuenta
            ok, details = validate_detalles_cuenta(page)
            report.mark("Detalles de la Cuenta", "PASS" if ok else "FAIL", details)

            # Step 7: Tus Negocios
            ok, details = validate_tus_negocios(page)
            report.mark("Tus Negocios", "PASS" if ok else "FAIL", details)

            # Step 8: Términos y Condiciones
            ok, details, maybe_terms_page, final_url = validate_legal_link(
                page,
                context,
                "Términos y Condiciones",
                r"T[ée]rminos y Condiciones",
            )
            target_for_terms = maybe_terms_page if maybe_terms_page else page
            terms_shot = screenshot(target_for_terms, output_dir, "08_terminos_y_condiciones")
            report.mark(
                "Términos y Condiciones",
                "PASS" if ok else "FAIL",
                details,
                screenshot=terms_shot,
                url=final_url,
            )
            cleanup_legal_navigation(page, maybe_terms_page)

            # Step 9: Política de Privacidad
            ok, details, maybe_priv_page, final_url = validate_legal_link(
                page,
                context,
                "Política de Privacidad",
                r"Pol[íi]tica de Privacidad",
            )
            target_for_privacy = maybe_priv_page if maybe_priv_page else page
            privacy_shot = screenshot(target_for_privacy, output_dir, "09_politica_de_privacidad")
            report.mark(
                "Política de Privacidad",
                "PASS" if ok else "FAIL",
                details,
                screenshot=privacy_shot,
                url=final_url,
            )
            cleanup_legal_navigation(page, maybe_priv_page)

        except Exception as exc:  # broad catch to ensure final report is always written
            mark_remaining_as_fail(report, str(exc))
        finally:
            browser.close()

    report_json = report.as_json()
    report_path.write_text(report_json, encoding="utf-8")
    print(report_json)

    has_failures = any(value.status != "PASS" for value in report.results.values())
    return 1 if has_failures else 0


if __name__ == "__main__":
    sys.exit(main())
