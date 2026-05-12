#!/usr/bin/env python3
"""SaleADS Mi Negocio full workflow Playwright test."""

from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from playwright.sync_api import BrowserContext, Locator, Page, TimeoutError, sync_playwright


TEST_NAME = "saleads_mi_negocio_full_test"
GOOGLE_EMAIL = os.getenv("SALEADS_GOOGLE_ACCOUNT", "juanlucasbarbiergarzon@gmail.com")
LOGIN_URL = os.getenv("SALEADS_LOGIN_URL", "").strip()
TIMEOUT_MS = int(os.getenv("SALEADS_TIMEOUT_MS", "30000"))
HEADLESS = os.getenv("SALEADS_HEADLESS", "true").strip().lower() not in {"0", "false", "no"}
OUTPUT_DIR = Path(os.getenv("SALEADS_OUTPUT_DIR", f"artifacts/{TEST_NAME}")).resolve()
SCREENSHOTS_DIR = OUTPUT_DIR / "screenshots"
REPORT_PATH = OUTPUT_DIR / "report.json"

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
    status: str = "FAIL"
    details: str = "Not executed."
    evidence: Dict[str, str] = field(default_factory=dict)


def ensure_output_dirs() -> None:
    SCREENSHOTS_DIR.mkdir(parents=True, exist_ok=True)


def screenshot(page: Page, name: str, full_page: bool = False) -> str:
    file_name = f"{name}.png"
    path = SCREENSHOTS_DIR / file_name
    page.screenshot(path=str(path), full_page=full_page)
    return str(path.relative_to(OUTPUT_DIR))


def mark_failure(results: Dict[str, StepResult], step_name: str, details: str) -> None:
    results[step_name].status = "FAIL"
    results[step_name].details = details


def mark_pass(
    results: Dict[str, StepResult],
    step_name: str,
    details: str,
    evidence: Optional[Dict[str, str]] = None,
) -> None:
    results[step_name].status = "PASS"
    results[step_name].details = details
    if evidence:
        results[step_name].evidence.update(evidence)


def wait_ui(page: Page) -> None:
    page.wait_for_load_state("domcontentloaded", timeout=TIMEOUT_MS)
    try:
        page.wait_for_load_state("networkidle", timeout=5000)
    except TimeoutError:
        # networkidle is best effort because some apps keep background traffic open.
        pass


def first_visible(candidates: List[Tuple[str, Locator]]) -> Optional[Tuple[str, Locator]]:
    for candidate_name, candidate in candidates:
        try:
            if candidate.first.is_visible(timeout=2000):
                return candidate_name, candidate.first
        except TimeoutError:
            continue
    return None


def require_visible(locator: Locator, label: str) -> None:
    locator.first.wait_for(state="visible", timeout=TIMEOUT_MS)
    if not locator.first.is_visible():
        raise AssertionError(f"Expected '{label}' to be visible.")


def click_with_wait(locator: Locator, page: Page) -> None:
    locator.first.click(timeout=TIMEOUT_MS)
    wait_ui(page)


def click_text_target(page: Page, pattern: str) -> Locator:
    regex = re.compile(pattern, re.IGNORECASE)
    candidates = [
        ("button", page.get_by_role("button", name=regex)),
        ("link", page.get_by_role("link", name=regex)),
        ("menuitem", page.get_by_role("menuitem", name=regex)),
        ("text", page.get_by_text(regex)),
    ]
    visible = first_visible(candidates)
    if not visible:
        raise AssertionError(f"Could not find visible clickable target matching: {pattern}")
    return visible[1]


def run_login_step(page: Page, context: BrowserContext, results: Dict[str, StepResult]) -> bool:
    if LOGIN_URL:
        page.goto(LOGIN_URL, wait_until="domcontentloaded", timeout=TIMEOUT_MS)
        wait_ui(page)
    elif page.url == "about:blank":
        mark_failure(
            results,
            "Login",
            "Missing precondition: SALEADS_LOGIN_URL is not set and no initial login page is open.",
        )
        return False

    login_button = click_text_target(
        page,
        r"sign in with google|iniciar sesi[oó]n con google|continuar con google|google",
    )

    popup_page: Optional[Page] = None
    try:
        with context.expect_page(timeout=5000) as page_info:
            login_button.click(timeout=TIMEOUT_MS)
        popup_page = page_info.value
    except TimeoutError:
        wait_ui(page)

    active_google_page = popup_page if popup_page else page
    if "accounts.google" in active_google_page.url.lower():
        account_locator = active_google_page.get_by_text(GOOGLE_EMAIL, exact=True)
        require_visible(account_locator, GOOGLE_EMAIL)
        account_locator.first.click(timeout=TIMEOUT_MS)
        wait_ui(active_google_page)
        if popup_page:
            try:
                popup_page.wait_for_event("close", timeout=10000)
            except TimeoutError:
                popup_page.close()
        wait_ui(page)

    sidebar_candidates = [
        ("aside", page.locator("aside")),
        ("navigation", page.get_by_role("navigation")),
        ("negocio-text", page.get_by_text(re.compile(r"negocio", re.IGNORECASE))),
    ]
    visible_sidebar = first_visible(sidebar_candidates)
    if not visible_sidebar:
        mark_failure(
            results,
            "Login",
            "Login flow did not reach the application shell with visible sidebar/navigation.",
        )
        return False

    dashboard_shot = screenshot(page, "01_dashboard_loaded", full_page=True)
    mark_pass(
        results,
        "Login",
        "Dashboard loaded and sidebar navigation is visible after Google login.",
        {"dashboard_screenshot": dashboard_shot},
    )
    return True


def run_menu_step(page: Page, results: Dict[str, StepResult]) -> bool:
    negocio_item = click_text_target(page, r"^Negocio$|Negocio")
    click_with_wait(negocio_item, page)

    mi_negocio_item = click_text_target(page, r"Mi Negocio")
    click_with_wait(mi_negocio_item, page)

    agregar_negocio = page.get_by_text(re.compile(r"Agregar Negocio", re.IGNORECASE))
    administrar_negocios = page.get_by_text(re.compile(r"Administrar Negocios", re.IGNORECASE))
    require_visible(agregar_negocio, "Agregar Negocio")
    require_visible(administrar_negocios, "Administrar Negocios")

    menu_shot = screenshot(page, "02_mi_negocio_menu_expanded")
    mark_pass(
        results,
        "Mi Negocio menu",
        "Mi Negocio submenu expanded with both required options visible.",
        {"menu_screenshot": menu_shot},
    )
    return True


def run_agregar_negocio_modal_step(page: Page, results: Dict[str, StepResult]) -> bool:
    agregar_negocio = click_text_target(page, r"Agregar Negocio")
    click_with_wait(agregar_negocio, page)

    modal_title = page.get_by_text(re.compile(r"Crear Nuevo Negocio", re.IGNORECASE))
    nombre_input = page.get_by_label(re.compile(r"Nombre del Negocio", re.IGNORECASE))
    quota_text = page.get_by_text(re.compile(r"Tienes\s+2\s+de\s+3\s+negocios", re.IGNORECASE))
    cancelar_button = page.get_by_role("button", name=re.compile(r"Cancelar", re.IGNORECASE))
    crear_button = page.get_by_role("button", name=re.compile(r"Crear Negocio", re.IGNORECASE))

    require_visible(modal_title, "Crear Nuevo Negocio")
    require_visible(nombre_input, "Nombre del Negocio")
    require_visible(quota_text, "Tienes 2 de 3 negocios")
    require_visible(cancelar_button, "Cancelar")
    require_visible(crear_button, "Crear Negocio")

    nombre_input.first.click(timeout=TIMEOUT_MS)
    nombre_input.first.fill("Negocio Prueba Automatización", timeout=TIMEOUT_MS)

    modal_shot = screenshot(page, "03_agregar_negocio_modal")
    cancelar_button.first.click(timeout=TIMEOUT_MS)
    wait_ui(page)

    mark_pass(
        results,
        "Agregar Negocio modal",
        "Agregar Negocio modal validated and closed with Cancelar.",
        {"modal_screenshot": modal_shot},
    )
    return True


def run_administrar_negocios_step(page: Page, results: Dict[str, StepResult]) -> bool:
    mi_negocio_item = click_text_target(page, r"Mi Negocio")
    click_with_wait(mi_negocio_item, page)

    administrar_negocios = click_text_target(page, r"Administrar Negocios")
    click_with_wait(administrar_negocios, page)

    info_general = page.get_by_text(re.compile(r"Información General", re.IGNORECASE))
    detalles_cuenta = page.get_by_text(re.compile(r"Detalles de la Cuenta", re.IGNORECASE))
    tus_negocios = page.get_by_text(re.compile(r"Tus Negocios", re.IGNORECASE))
    legal_section = page.get_by_text(re.compile(r"Sección Legal", re.IGNORECASE))

    require_visible(info_general, "Información General")
    require_visible(detalles_cuenta, "Detalles de la Cuenta")
    require_visible(tus_negocios, "Tus Negocios")
    require_visible(legal_section, "Sección Legal")

    account_shot = screenshot(page, "04_administrar_negocios_view", full_page=True)
    mark_pass(
        results,
        "Administrar Negocios view",
        "Administrar Negocios page loaded with all expected sections.",
        {"account_page_screenshot": account_shot},
    )
    return True


def run_info_general_step(page: Page, results: Dict[str, StepResult]) -> bool:
    candidates = [
        page.get_by_text(re.compile(r"BUSINESS PLAN", re.IGNORECASE)),
        page.get_by_role("button", name=re.compile(r"Cambiar Plan", re.IGNORECASE)),
    ]
    for candidate in candidates:
        require_visible(candidate, "Información General field")

    any_email = page.get_by_text(re.compile(r"@", re.IGNORECASE))
    require_visible(any_email, "User email")

    possible_name = page.locator("h1, h2, h3, p, span").filter(has_not_text="@")
    if possible_name.count() == 0:
        raise AssertionError("Expected user name text in Información General section.")

    mark_pass(
        results,
        "Información General",
        "Información General shows user identity, BUSINESS PLAN label, and Cambiar Plan button.",
    )
    return True


def run_detalles_cuenta_step(page: Page, results: Dict[str, StepResult]) -> bool:
    require_visible(page.get_by_text(re.compile(r"Cuenta creada", re.IGNORECASE)), "Cuenta creada")
    require_visible(page.get_by_text(re.compile(r"Estado activo", re.IGNORECASE)), "Estado activo")
    require_visible(
        page.get_by_text(re.compile(r"Idioma seleccionado", re.IGNORECASE)),
        "Idioma seleccionado",
    )
    mark_pass(
        results,
        "Detalles de la Cuenta",
        "Detalles de la Cuenta includes creation date, active status, and selected language.",
    )
    return True


def run_tus_negocios_step(page: Page, results: Dict[str, StepResult]) -> bool:
    require_visible(page.get_by_text(re.compile(r"Tus Negocios", re.IGNORECASE)), "Tus Negocios")
    require_visible(page.get_by_text(re.compile(r"Agregar Negocio", re.IGNORECASE)), "Agregar Negocio")
    require_visible(
        page.get_by_text(re.compile(r"Tienes\s+2\s+de\s+3\s+negocios", re.IGNORECASE)),
        "Tienes 2 de 3 negocios",
    )
    mark_pass(
        results,
        "Tus Negocios",
        "Tus Negocios list and business quota information are visible.",
    )
    return True


def validate_legal_link(
    page: Page,
    context: BrowserContext,
    link_text_pattern: str,
    expected_heading_pattern: str,
    screenshot_name: str,
) -> Dict[str, str]:
    app_page = page
    origin_url = app_page.url
    link_locator = click_text_target(app_page, link_text_pattern)

    popup_page: Optional[Page] = None
    opened_new_tab = False
    try:
        with context.expect_page(timeout=5000) as page_info:
            link_locator.click(timeout=TIMEOUT_MS)
        popup_page = page_info.value
        opened_new_tab = True
    except TimeoutError:
        wait_ui(app_page)

    target_page = popup_page if popup_page else app_page
    wait_ui(target_page)

    heading = target_page.get_by_text(re.compile(expected_heading_pattern, re.IGNORECASE))
    require_visible(heading, expected_heading_pattern)

    body_text = target_page.locator("main, article, body")
    require_visible(body_text, "Legal content text")

    shot = screenshot(target_page, screenshot_name, full_page=True)
    final_url = target_page.url

    if opened_new_tab and popup_page:
        popup_page.close()
        app_page.bring_to_front()
        wait_ui(app_page)
    else:
        try:
            app_page.go_back(wait_until="domcontentloaded", timeout=TIMEOUT_MS)
            wait_ui(app_page)
        except Exception:
            app_page.goto(origin_url, wait_until="domcontentloaded", timeout=TIMEOUT_MS)
            wait_ui(app_page)

    return {
        "screenshot": shot,
        "final_url": final_url,
        "opened_new_tab": str(opened_new_tab),
    }


def write_report(results: Dict[str, StepResult]) -> None:
    summary_passed = sum(1 for step in results.values() if step.status == "PASS")
    summary_failed = sum(1 for step in results.values() if step.status == "FAIL")
    report_payload = {
        "test_name": TEST_NAME,
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "config": {
            "headless": HEADLESS,
            "login_url_provided": bool(LOGIN_URL),
            "google_account": GOOGLE_EMAIL,
            "timeout_ms": TIMEOUT_MS,
        },
        "results": {
            step_name: {
                "status": step_result.status,
                "details": step_result.details,
                "evidence": step_result.evidence,
            }
            for step_name, step_result in results.items()
        },
        "summary": {
            "passed": summary_passed,
            "failed": summary_failed,
            "overall": "PASS" if summary_failed == 0 else "FAIL",
        },
    }
    REPORT_PATH.write_text(json.dumps(report_payload, indent=2, ensure_ascii=False), encoding="utf-8")


def fail_remaining(results: Dict[str, StepResult], start_at: int, reason: str) -> None:
    for step_name in REPORT_FIELDS[start_at:]:
        if results[step_name].details == "Not executed.":
            mark_failure(results, step_name, f"Prerequisite failed: {reason}")


def main() -> int:
    ensure_output_dirs()
    results: Dict[str, StepResult] = {field: StepResult() for field in REPORT_FIELDS}

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=HEADLESS)
        context = browser.new_context()
        page = context.new_page()
        page.set_default_timeout(TIMEOUT_MS)

        try:
            login_ok = run_login_step(page, context, results)
            if not login_ok:
                fail_remaining(results, 1, "Login step failed")
                write_report(results)
                return 1

            menu_ok = run_menu_step(page, results)
            if not menu_ok:
                fail_remaining(results, 2, "Mi Negocio menu step failed")
                write_report(results)
                return 1

            modal_ok = run_agregar_negocio_modal_step(page, results)
            if not modal_ok:
                fail_remaining(results, 3, "Agregar Negocio modal step failed")
                write_report(results)
                return 1

            admin_ok = run_administrar_negocios_step(page, results)
            if not admin_ok:
                fail_remaining(results, 4, "Administrar Negocios step failed")
                write_report(results)
                return 1

            run_info_general_step(page, results)
            run_detalles_cuenta_step(page, results)
            run_tus_negocios_step(page, results)

            terms_evidence = validate_legal_link(
                page=page,
                context=context,
                link_text_pattern=r"Términos y Condiciones",
                expected_heading_pattern=r"Términos y Condiciones",
                screenshot_name="05_terminos_y_condiciones",
            )
            mark_pass(
                results,
                "Términos y Condiciones",
                "Legal terms page opened and content validated.",
                terms_evidence,
            )

            privacy_evidence = validate_legal_link(
                page=page,
                context=context,
                link_text_pattern=r"Política de Privacidad",
                expected_heading_pattern=r"Política de Privacidad",
                screenshot_name="06_politica_de_privacidad",
            )
            mark_pass(
                results,
                "Política de Privacidad",
                "Privacy policy page opened and content validated.",
                privacy_evidence,
            )

        except Exception as error:  # pylint: disable=broad-except
            first_not_passed = next(
                (step_name for step_name in REPORT_FIELDS if results[step_name].status != "PASS"),
                REPORT_FIELDS[0],
            )
            mark_failure(results, first_not_passed, f"Unhandled error: {error}")
            fail_remaining(results, REPORT_FIELDS.index(first_not_passed) + 1, str(error))
        finally:
            browser.close()

    write_report(results)
    return 0 if all(result.status == "PASS" for result in results.values()) else 1


if __name__ == "__main__":
    raise SystemExit(main())
