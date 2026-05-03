#!/usr/bin/env python3
"""
SaleADS Mi Negocio full workflow test.

This script is environment-agnostic and does not hardcode any SaleADS domain.
Provide the login URL through SALEADS_LOGIN_URL, or connect to an existing
browser page with SALEADS_CDP_URL where the login page is already open.
"""

from __future__ import annotations

import json
import os
import re
import sys
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable

from playwright.sync_api import Locator, Page, TimeoutError as PlaywrightTimeoutError
from playwright.sync_api import sync_playwright


TEST_NAME = "saleads_mi_negocio_full_test"
DEFAULT_GOOGLE_EMAIL = "juanlucasbarbiergarzon@gmail.com"
STEP_FIELDS = [
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
class StepState:
    status: str = "FAIL"
    details: list[str] = field(default_factory=list)


def env_flag(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


def now_utc() -> str:
    return datetime.now(timezone.utc).isoformat()


def safe_wait(page: Page, timeout_ms: int) -> None:
    try:
        page.wait_for_load_state("domcontentloaded", timeout=timeout_ms)
    except PlaywrightTimeoutError:
        pass
    try:
        page.wait_for_load_state("networkidle", timeout=min(timeout_ms, 6000))
    except PlaywrightTimeoutError:
        pass


def click_and_wait(locator: Locator, page: Page, timeout_ms: int) -> None:
    locator.first.scroll_into_view_if_needed()
    locator.first.click()
    safe_wait(page, timeout_ms)


def first_visible(getters: list[Callable[[], Locator]], timeout_ms: int) -> Locator | None:
    for get_locator in getters:
        locator = get_locator()
        try:
            locator.first.wait_for(state="visible", timeout=timeout_ms)
            return locator.first
        except PlaywrightTimeoutError:
            continue
    return None


def visible_text_present(page: Page, pattern: str, timeout_ms: int) -> bool:
    try:
        page.get_by_text(re.compile(pattern, re.IGNORECASE)).first.wait_for(
            state="visible", timeout=timeout_ms
        )
        return True
    except PlaywrightTimeoutError:
        return False


def fail_with_prerequisite(
    results: dict[str, StepState], required_step: str, failed_step: str
) -> None:
    results[failed_step].status = "FAIL"
    results[failed_step].details.append(
        f"Prerequisite failed: {required_step} did not pass."
    )


def mark_step(
    results: dict[str, StepState], step: str, passed: bool, message: str
) -> None:
    results[step].status = "PASS" if passed else "FAIL"
    results[step].details.append(message)


def screenshot(page: Page, target_dir: Path, name: str) -> str:
    target_dir.mkdir(parents=True, exist_ok=True)
    file_path = target_dir / f"{name}.png"
    page.screenshot(path=str(file_path), full_page=True)
    return str(file_path)


def legal_navigation(
    app_page: Page,
    link_pattern: str,
    heading_pattern: str,
    screenshot_name: str,
    screenshot_dir: Path,
    timeout_ms: int,
) -> tuple[bool, str, str]:
    current_url = app_page.url
    link = first_visible(
        [
            lambda: app_page.get_by_role("link", name=re.compile(link_pattern, re.IGNORECASE)),
            lambda: app_page.get_by_text(re.compile(link_pattern, re.IGNORECASE)),
        ],
        timeout_ms=timeout_ms,
    )
    if link is None:
        return False, "", f"Legal link not found: {link_pattern}"

    legal_page = app_page
    opened_popup = False
    try:
        with app_page.expect_popup(timeout=min(timeout_ms, 5000)) as popup_info:
            click_and_wait(link, app_page, timeout_ms)
        legal_page = popup_info.value
        legal_page.wait_for_load_state("domcontentloaded", timeout=timeout_ms)
        safe_wait(legal_page, timeout_ms)
        opened_popup = True
    except PlaywrightTimeoutError:
        click_and_wait(link, app_page, timeout_ms)
        legal_page = app_page

    body_text = ""
    try:
        legal_page.locator("body").first.wait_for(state="visible", timeout=timeout_ms)
        body_text = legal_page.locator("body").first.inner_text(timeout=timeout_ms).strip()
    except PlaywrightTimeoutError:
        pass

    heading_found = visible_text_present(legal_page, heading_pattern, timeout_ms)
    has_content = len(body_text) > 120
    screenshot_path = screenshot(legal_page, screenshot_dir, screenshot_name)
    final_url = legal_page.url

    if opened_popup:
        legal_page.close()
        app_page.bring_to_front()
    else:
        try:
            app_page.go_back(wait_until="domcontentloaded", timeout=timeout_ms)
            safe_wait(app_page, timeout_ms)
        except PlaywrightTimeoutError:
            app_page.goto(current_url, wait_until="domcontentloaded", timeout=timeout_ms)
            safe_wait(app_page, timeout_ms)

    if not heading_found:
        return (
            False,
            final_url,
            f"Heading not found for pattern '{heading_pattern}'. Screenshot: {screenshot_path}",
        )
    if not has_content:
        return (
            False,
            final_url,
            f"Legal content appears too short. Screenshot: {screenshot_path}",
        )

    return True, final_url, f"Validated legal page. Screenshot: {screenshot_path}"


def perform_google_login(
    page: Page, google_button: Locator, google_email: str, timeout_ms: int
) -> None:
    active_page = page
    try:
        with page.expect_popup(timeout=min(timeout_ms, 5000)) as popup_info:
            google_button.first.scroll_into_view_if_needed()
            google_button.first.click()
        active_page = popup_info.value
        active_page.wait_for_load_state("domcontentloaded", timeout=timeout_ms)
        safe_wait(active_page, timeout_ms)
    except PlaywrightTimeoutError:
        click_and_wait(google_button, page, timeout_ms)
        active_page = page

    email_choice = first_visible(
        [
            lambda: active_page.get_by_text(
                re.compile(re.escape(google_email), re.IGNORECASE)
            ),
            lambda: active_page.get_by_role(
                "button",
                name=re.compile(re.escape(google_email), re.IGNORECASE),
            ),
            lambda: active_page.get_by_role(
                "link",
                name=re.compile(re.escape(google_email), re.IGNORECASE),
            ),
        ],
        timeout_ms=5000,
    )

    if email_choice:
        click_and_wait(email_choice, active_page, timeout_ms)

    if active_page != page:
        try:
            active_page.wait_for_event("close", timeout=min(timeout_ms, 15000))
        except PlaywrightTimeoutError:
            pass

    safe_wait(page, timeout_ms)


def main() -> int:
    timeout_ms = int(os.getenv("SALEADS_TIMEOUT_MS", "25000"))
    headless = env_flag("SALEADS_HEADLESS", True)
    login_url = os.getenv("SALEADS_LOGIN_URL", "").strip()
    cdp_url = os.getenv("SALEADS_CDP_URL", "").strip()
    google_email = os.getenv("SALEADS_GOOGLE_EMAIL", DEFAULT_GOOGLE_EMAIL).strip()

    screenshots_dir = Path(
        os.getenv("SALEADS_SCREENSHOTS_DIR", "/workspace/automation/screenshots")
    )
    report_path = Path(
        os.getenv(
            "SALEADS_REPORT_PATH",
            "/workspace/automation/saleads_mi_negocio_full_test_report.json",
        )
    )

    results: dict[str, StepState] = {field: StepState() for field in STEP_FIELDS}
    evidence: dict[str, str] = {}
    legal_urls: dict[str, str] = {}

    with sync_playwright() as playwright:
        browser = None
        created_context = False
        try:
            if cdp_url:
                browser = playwright.chromium.connect_over_cdp(cdp_url)
                context = browser.contexts[0] if browser.contexts else browser.new_context()
                page = context.pages[0] if context.pages else context.new_page()
            else:
                browser = playwright.chromium.launch(headless=headless)
                context = browser.new_context()
                page = context.new_page()
                created_context = True

            if login_url and page.url in {"about:blank", "chrome://new-tab-page/", "chrome://newtab/"}:
                page.goto(login_url, wait_until="domcontentloaded", timeout=timeout_ms)
                safe_wait(page, timeout_ms)

            if page.url in {"about:blank", "chrome://new-tab-page/", "chrome://newtab/"}:
                mark_step(
                    results,
                    "Login",
                    False,
                    "No active SaleADS page detected. Set SALEADS_LOGIN_URL or SALEADS_CDP_URL.",
                )
            else:
                # Step 1: Login with Google and continue.
                google_button = first_visible(
                    [
                        lambda: page.get_by_role(
                            "button", name=re.compile(r"google", re.IGNORECASE)
                        ),
                        lambda: page.get_by_role(
                            "link", name=re.compile(r"google", re.IGNORECASE)
                        ),
                        lambda: page.get_by_text(re.compile(r"google", re.IGNORECASE)),
                    ],
                    timeout_ms=7000,
                )

                if google_button:
                    perform_google_login(page, google_button, google_email, timeout_ms)

                main_interface = first_visible(
                    [
                        lambda: page.get_by_role("navigation"),
                        lambda: page.locator("aside"),
                        lambda: page.get_by_text(re.compile(r"Negocio|Dashboard", re.IGNORECASE)),
                    ],
                    timeout_ms=timeout_ms,
                )
                sidebar_visible = first_visible(
                    [
                        lambda: page.locator("aside"),
                        lambda: page.get_by_role("navigation"),
                    ],
                    timeout_ms=timeout_ms,
                )

                if main_interface and sidebar_visible:
                    evidence["dashboard"] = screenshot(page, screenshots_dir, "01_dashboard_loaded")
                    mark_step(
                        results,
                        "Login",
                        True,
                        "Main interface and left sidebar are visible.",
                    )
                else:
                    mark_step(
                        results,
                        "Login",
                        False,
                        "Main interface or left sidebar was not detected after login action.",
                    )

            # Step 2: Open Mi Negocio menu.
            if results["Login"].status != "PASS":
                fail_with_prerequisite(results, "Login", "Mi Negocio menu")
            else:
                negocio_entry = first_visible(
                    [
                        lambda: page.get_by_role(
                            "button", name=re.compile(r"(Mi )?Negocio", re.IGNORECASE)
                        ),
                        lambda: page.get_by_role(
                            "link", name=re.compile(r"(Mi )?Negocio", re.IGNORECASE)
                        ),
                        lambda: page.get_by_text(re.compile(r"(Mi )?Negocio", re.IGNORECASE)),
                    ],
                    timeout_ms=timeout_ms,
                )

                agregar_menu = first_visible(
                    [lambda: page.get_by_text(re.compile(r"Agregar Negocio", re.IGNORECASE))],
                    timeout_ms=3000,
                )
                admin_menu = first_visible(
                    [lambda: page.get_by_text(re.compile(r"Administrar Negocios", re.IGNORECASE))],
                    timeout_ms=3000,
                )

                if negocio_entry and (not agregar_menu or not admin_menu):
                    click_and_wait(negocio_entry, page, timeout_ms)
                    agregar_menu = first_visible(
                        [lambda: page.get_by_text(re.compile(r"Agregar Negocio", re.IGNORECASE))],
                        timeout_ms=timeout_ms,
                    )
                    admin_menu = first_visible(
                        [lambda: page.get_by_text(re.compile(r"Administrar Negocios", re.IGNORECASE))],
                        timeout_ms=timeout_ms,
                    )

                if agregar_menu and admin_menu:
                    evidence["mi_negocio_menu"] = screenshot(
                        page, screenshots_dir, "02_mi_negocio_expanded"
                    )
                    mark_step(
                        results,
                        "Mi Negocio menu",
                        True,
                        "Mi Negocio submenu expanded with Agregar and Administrar options visible.",
                    )
                else:
                    mark_step(
                        results,
                        "Mi Negocio menu",
                        False,
                        "Could not confirm expanded Mi Negocio submenu entries.",
                    )

            # Step 3: Validate Agregar Negocio modal.
            if results["Mi Negocio menu"].status != "PASS":
                fail_with_prerequisite(results, "Mi Negocio menu", "Agregar Negocio modal")
            else:
                agregar_action = first_visible(
                    [lambda: page.get_by_text(re.compile(r"Agregar Negocio", re.IGNORECASE))],
                    timeout_ms=timeout_ms,
                )
                if agregar_action:
                    click_and_wait(agregar_action, page, timeout_ms)

                modal = first_visible(
                    [
                        lambda: page.get_by_role(
                            "dialog",
                            name=re.compile(r"Crear Nuevo Negocio", re.IGNORECASE),
                        ),
                        lambda: page.locator("[role='dialog']"),
                        lambda: page.get_by_text(re.compile(r"Crear Nuevo Negocio", re.IGNORECASE)),
                    ],
                    timeout_ms=timeout_ms,
                )

                title_ok = visible_text_present(page, r"Crear Nuevo Negocio", timeout_ms)
                input_ok = first_visible(
                    [
                        lambda: page.get_by_label(re.compile(r"Nombre del Negocio", re.IGNORECASE)),
                        lambda: page.get_by_placeholder(
                            re.compile(r"Nombre del Negocio", re.IGNORECASE)
                        ),
                    ],
                    timeout_ms=3000,
                )
                slots_ok = visible_text_present(page, r"Tienes 2 de 3 negocios", timeout_ms)
                cancelar_ok = first_visible(
                    [lambda: page.get_by_role("button", name=re.compile(r"Cancelar", re.IGNORECASE))],
                    timeout_ms=3000,
                )
                crear_ok = first_visible(
                    [
                        lambda: page.get_by_role(
                            "button", name=re.compile(r"Crear Negocio", re.IGNORECASE)
                        )
                    ],
                    timeout_ms=3000,
                )

                if modal and title_ok and input_ok and slots_ok and cancelar_ok and crear_ok:
                    evidence["agregar_modal"] = screenshot(
                        page, screenshots_dir, "03_agregar_negocio_modal"
                    )
                    input_ok.click()
                    input_ok.fill("Negocio Prueba Automatizacion")
                    click_and_wait(cancelar_ok, page, timeout_ms)
                    mark_step(
                        results,
                        "Agregar Negocio modal",
                        True,
                        "Crear Nuevo Negocio modal validated with required fields and buttons.",
                    )
                else:
                    mark_step(
                        results,
                        "Agregar Negocio modal",
                        False,
                        "Agregar Negocio modal missing one or more required elements.",
                    )

            # Step 4: Open Administrar Negocios page.
            if results["Mi Negocio menu"].status != "PASS":
                fail_with_prerequisite(results, "Mi Negocio menu", "Administrar Negocios view")
            else:
                admin_action = first_visible(
                    [lambda: page.get_by_text(re.compile(r"Administrar Negocios", re.IGNORECASE))],
                    timeout_ms=5000,
                )
                if admin_action is None:
                    negocio_entry = first_visible(
                        [lambda: page.get_by_text(re.compile(r"(Mi )?Negocio", re.IGNORECASE))],
                        timeout_ms=5000,
                    )
                    if negocio_entry:
                        click_and_wait(negocio_entry, page, timeout_ms)
                        admin_action = first_visible(
                            [lambda: page.get_by_text(re.compile(r"Administrar Negocios", re.IGNORECASE))],
                            timeout_ms=timeout_ms,
                        )

                if admin_action:
                    click_and_wait(admin_action, page, timeout_ms)

                info_general = visible_text_present(page, r"Informaci[oó]n General", timeout_ms)
                detalles = visible_text_present(page, r"Detalles de la Cuenta", timeout_ms)
                negocios = visible_text_present(page, r"Tus Negocios", timeout_ms)
                legal = visible_text_present(page, r"Secci[oó]n Legal", timeout_ms)

                if info_general and detalles and negocios and legal:
                    evidence["administrar_negocios"] = screenshot(
                        page, screenshots_dir, "04_administrar_negocios"
                    )
                    mark_step(
                        results,
                        "Administrar Negocios view",
                        True,
                        "Administrar Negocios page loaded with all expected sections.",
                    )
                else:
                    mark_step(
                        results,
                        "Administrar Negocios view",
                        False,
                        "Missing one or more required sections in Administrar Negocios.",
                    )

            # Step 5: Informacion General
            if results["Administrar Negocios view"].status != "PASS":
                fail_with_prerequisite(results, "Administrar Negocios view", "Información General")
            else:
                name_visible = first_visible(
                    [
                        lambda: page.locator(
                            "h1, h2, h3, p, span"
                        ).filter(has_text=re.compile(r".+\s.+"))
                    ],
                    timeout_ms=3000,
                )
                email_visible = visible_text_present(
                    page, r"[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}", timeout_ms
                )
                plan_visible = visible_text_present(page, r"BUSINESS PLAN", timeout_ms)
                cambiar_plan = first_visible(
                    [lambda: page.get_by_role("button", name=re.compile(r"Cambiar Plan", re.IGNORECASE))],
                    timeout_ms=timeout_ms,
                )
                ok = bool(name_visible) and email_visible and plan_visible and bool(cambiar_plan)
                mark_step(
                    results,
                    "Información General",
                    ok,
                    "Validated user name/email, BUSINESS PLAN, and Cambiar Plan button."
                    if ok
                    else "Informacion General validation failed for one or more required elements.",
                )

            # Step 6: Detalles de la Cuenta
            if results["Administrar Negocios view"].status != "PASS":
                fail_with_prerequisite(
                    results, "Administrar Negocios view", "Detalles de la Cuenta"
                )
            else:
                created_ok = visible_text_present(page, r"Cuenta creada", timeout_ms)
                active_ok = visible_text_present(page, r"Estado activo", timeout_ms)
                lang_ok = visible_text_present(page, r"Idioma seleccionado", timeout_ms)
                ok = created_ok and active_ok and lang_ok
                mark_step(
                    results,
                    "Detalles de la Cuenta",
                    ok,
                    "Validated Cuenta creada, Estado activo, and Idioma seleccionado."
                    if ok
                    else "Detalles de la Cuenta missing required values.",
                )

            # Step 7: Tus Negocios
            if results["Administrar Negocios view"].status != "PASS":
                fail_with_prerequisite(results, "Administrar Negocios view", "Tus Negocios")
            else:
                list_visible = first_visible(
                    [
                        lambda: page.locator("table, ul, [role='list']").filter(
                            has_text=re.compile(r"Negocio", re.IGNORECASE)
                        ),
                        lambda: page.get_by_text(re.compile(r"Tus Negocios", re.IGNORECASE)),
                    ],
                    timeout_ms=timeout_ms,
                )
                add_button = first_visible(
                    [lambda: page.get_by_role("button", name=re.compile(r"Agregar Negocio", re.IGNORECASE))],
                    timeout_ms=timeout_ms,
                )
                slots_ok = visible_text_present(page, r"Tienes 2 de 3 negocios", timeout_ms)
                ok = bool(list_visible) and bool(add_button) and slots_ok
                mark_step(
                    results,
                    "Tus Negocios",
                    ok,
                    "Validated business list, Agregar Negocio button, and business quota text."
                    if ok
                    else "Tus Negocios validation failed for required elements.",
                )

            # Step 8: Terminos y Condiciones
            if results["Administrar Negocios view"].status != "PASS":
                fail_with_prerequisite(
                    results, "Administrar Negocios view", "Términos y Condiciones"
                )
            else:
                terms_ok, terms_url, terms_msg = legal_navigation(
                    app_page=page,
                    link_pattern=r"T[eé]rminos y Condiciones",
                    heading_pattern=r"T[eé]rminos y Condiciones",
                    screenshot_name="05_terminos_y_condiciones",
                    screenshot_dir=screenshots_dir,
                    timeout_ms=timeout_ms,
                )
                if terms_url:
                    legal_urls["Términos y Condiciones"] = terms_url
                mark_step(results, "Términos y Condiciones", terms_ok, terms_msg)

            # Step 9: Politica de Privacidad
            if results["Administrar Negocios view"].status != "PASS":
                fail_with_prerequisite(
                    results, "Administrar Negocios view", "Política de Privacidad"
                )
            else:
                privacy_ok, privacy_url, privacy_msg = legal_navigation(
                    app_page=page,
                    link_pattern=r"Pol[ií]tica de Privacidad",
                    heading_pattern=r"Pol[ií]tica de Privacidad",
                    screenshot_name="06_politica_de_privacidad",
                    screenshot_dir=screenshots_dir,
                    timeout_ms=timeout_ms,
                )
                if privacy_url:
                    legal_urls["Política de Privacidad"] = privacy_url
                mark_step(results, "Política de Privacidad", privacy_ok, privacy_msg)

        finally:
            if browser and created_context:
                browser.close()

    report = {
        "name": TEST_NAME,
        "generated_at": now_utc(),
        "results": {step: results[step].status for step in STEP_FIELDS},
        "details": {step: results[step].details for step in STEP_FIELDS},
        "evidence": {"screenshots": evidence, "legal_urls": legal_urls},
    }

    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps(report, indent=2))

    return 0 if all(results[step].status == "PASS" for step in STEP_FIELDS) else 1


if __name__ == "__main__":
    sys.exit(main())
