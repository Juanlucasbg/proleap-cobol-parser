#!/usr/bin/env python3
"""
SaleADS.ai - Mi Negocio full workflow validation.

This script automates the requested end-to-end flow:
1) Google login
2) Mi Negocio menu expansion
3) Agregar Negocio modal validation
4) Administrar Negocios page validation
5) Informacion General checks
6) Detalles de la Cuenta checks
7) Tus Negocios checks
8) Terminos y Condiciones link validation
9) Politica de Privacidad link validation
10) Final PASS/FAIL report
"""

from __future__ import annotations

import argparse
import json
import os
import re
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from playwright.sync_api import BrowserContext, Locator, Page, TimeoutError as PlaywrightTimeoutError, sync_playwright


REPORT_FIELDS = [
    "Login",
    "Mi Negocio menu",
    "Agregar Negocio modal",
    "Administrar Negocios view",
    "Informacion General",
    "Detalles de la Cuenta",
    "Tus Negocios",
    "Terminos y Condiciones",
    "Politica de Privacidad",
]


@dataclass
class StepResult:
    name: str
    passed: bool
    details: str
    screenshots: List[str] = field(default_factory=list)
    final_url: Optional[str] = None


def slugify(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", value.lower()).strip("_")


def wait_for_ui(page: Page, timeout_ms: int) -> None:
    page.wait_for_load_state("domcontentloaded", timeout=timeout_ms)
    try:
        page.wait_for_load_state("load", timeout=timeout_ms)
    except PlaywrightTimeoutError:
        pass
    try:
        page.wait_for_load_state("networkidle", timeout=min(timeout_ms, 7000))
    except PlaywrightTimeoutError:
        pass
    page.wait_for_timeout(500)


def screenshot(page: Page, output_dir: Path, checkpoint: str, full_page: bool = False) -> str:
    output_dir.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    file_path = output_dir / f"{timestamp}_{slugify(checkpoint)}.png"
    page.screenshot(path=str(file_path), full_page=full_page)
    return str(file_path)


def first_visible(locator_candidates, timeout_ms: int = 1500):
    for locator in locator_candidates:
        try:
            if locator.first.is_visible(timeout=timeout_ms):
                return locator.first
        except PlaywrightTimeoutError:
            continue
    return None


def text_locators(root: Page | Locator, text_regex: str):
    pattern = re.compile(text_regex, re.IGNORECASE)
    return [
        root.get_by_role("button", name=pattern),
        root.get_by_role("link", name=pattern),
        root.get_by_role("menuitem", name=pattern),
        root.get_by_role("tab", name=pattern),
        root.get_by_text(pattern),
    ]


def click_by_text(page: Page, text_regex: str, timeout_ms: int, description: str) -> None:
    target = first_visible(text_locators(page, text_regex))
    if target is None:
        raise AssertionError(f"Could not find clickable text for: {description}")
    target.click(timeout=timeout_ms)
    wait_for_ui(page, timeout_ms)


def assert_visible(page: Page, text_regex: str, timeout_ms: int, description: str) -> None:
    target = first_visible(text_locators(page, text_regex), timeout_ms=timeout_ms)
    if target is None:
        raise AssertionError(f"Expected visible text missing: {description}")


def find_sidebar(page: Page, timeout_ms: int):
    return first_visible(
        [
            page.locator("aside"),
            page.locator("[class*='sidebar']"),
            page.locator("[aria-label*='sidebar' i]"),
            page.locator("[data-testid*='sidebar' i]"),
        ],
        timeout_ms=timeout_ms,
    )


def is_main_app_visible(page: Page, timeout_ms: int) -> bool:
    sidebar = find_sidebar(page, timeout_ms)
    if sidebar is None:
        return False
    app_markers = first_visible(
        text_locators(sidebar, r"(Mi\s*Negocio|Administrar\s*Negocios|Agregar\s*Negocio|Dashboard)"),
        timeout_ms=3000,
    )
    return app_markers is not None


def assert_sidebar_visible(page: Page, timeout_ms: int) -> None:
    sidebar = find_sidebar(page, timeout_ms)
    if sidebar is None:
        raise AssertionError("Left sidebar navigation is not visible.")
    if not is_main_app_visible(page, timeout_ms):
        raise AssertionError("Main application sidebar was not detected.")


def mark_result(results: Dict[str, StepResult], field_name: str, passed: bool, details: str, screenshots=None, final_url=None):
    results[field_name] = StepResult(
        name=field_name,
        passed=passed,
        details=details,
        screenshots=screenshots or [],
        final_url=final_url,
    )


def expand_mi_negocio_menu(page: Page, timeout_ms: int) -> None:
    sidebar = find_sidebar(page, timeout_ms)
    if sidebar is None:
        raise AssertionError("Sidebar not found while trying to expand Mi Negocio.")

    # Open Negocio section if it exists in collapsed groups.
    negocio_toggle = first_visible(text_locators(sidebar, r"^Negocio$"))
    if negocio_toggle is not None:
        negocio_toggle.click(timeout=timeout_ms)
        wait_for_ui(page, timeout_ms)

    # Click Mi Negocio and ensure submenu options appear.
    for _ in range(3):
        mi_negocio = first_visible(text_locators(sidebar, r"Mi\s*Negocio"))
        if mi_negocio is None:
            raise AssertionError("Could not find 'Mi Negocio' in sidebar.")
        mi_negocio.click(timeout=timeout_ms)
        wait_for_ui(page, timeout_ms)
        agregar_visible = first_visible(text_locators(sidebar, r"Agregar\s*Negocio")) is not None
        administrar_visible = first_visible(text_locators(sidebar, r"Administrar\s*Negocios")) is not None
        if agregar_visible and administrar_visible:
            return
    raise AssertionError("Mi Negocio submenu did not expand with expected items.")


def open_legal_link_and_validate(
    app_page: Page,
    context: BrowserContext,
    link_text_regex: str,
    heading_regex: str,
    timeout_ms: int,
    output_dir: Path,
    screenshot_name: str,
) -> Tuple[str, str]:
    before_pages = set(context.pages)
    click_by_text(app_page, link_text_regex, timeout_ms, f"Open legal link: {link_text_regex}")
    after_pages = set(context.pages)
    new_pages = [p for p in after_pages if p not in before_pages]
    legal_page = new_pages[-1] if new_pages else app_page

    legal_page.bring_to_front()
    wait_for_ui(legal_page, timeout_ms)
    assert_visible(legal_page, heading_regex, timeout_ms, f"Heading for {link_text_regex}")

    body_text = legal_page.locator("body").inner_text(timeout=timeout_ms)
    condensed = re.sub(r"\s+", " ", body_text).strip()
    if len(condensed) < 120:
        raise AssertionError(f"Legal content appears too short for: {link_text_regex}")

    legal_url = legal_page.url
    legal_screenshot = screenshot(legal_page, output_dir, screenshot_name, full_page=True)

    if legal_page is not app_page:
        legal_page.close()
        app_page.bring_to_front()
        wait_for_ui(app_page, timeout_ms)
    else:
        app_page.go_back(wait_until="domcontentloaded", timeout=timeout_ms)
        wait_for_ui(app_page, timeout_ms)

    return legal_screenshot, legal_url


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Run SaleADS Mi Negocio full validation.")
    parser.add_argument(
        "--base-url",
        default=None,
        help="SaleADS login URL for the current environment (required if SALEADS_BASE_URL is not set).",
    )
    parser.add_argument(
        "--google-account",
        default="juanlucasbarbiergarzon@gmail.com",
        help="Google account email to select if account picker appears.",
    )
    parser.add_argument(
        "--output-dir",
        default="artifacts/saleads_mi_negocio_full_test",
        help="Directory where screenshots and report will be stored.",
    )
    parser.add_argument(
        "--report-json",
        default=None,
        help="Optional explicit report path. Defaults to <output-dir>/final_report.json",
    )
    parser.add_argument(
        "--timeout-ms",
        type=int,
        default=25000,
        help="Timeout in milliseconds for waits and actions.",
    )
    parser.add_argument(
        "--headless",
        action="store_true",
        help="Run browser in headless mode. Omit for headed mode.",
    )
    parser.add_argument(
        "--slow-mo-ms",
        type=int,
        default=150,
        help="Delay between Playwright actions in milliseconds.",
    )
    return parser


def run(args: argparse.Namespace) -> int:
    base_url = args.base_url or ""
    if not base_url:
        raise ValueError("Missing URL. Provide --base-url or export SALEADS_BASE_URL.")

    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    report_path = Path(args.report_json) if args.report_json else output_dir / "final_report.json"
    results: Dict[str, StepResult] = {}

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=args.headless, slow_mo=args.slow_mo_ms)
        context = browser.new_context(viewport={"width": 1440, "height": 900})
        page = context.new_page()
        page.goto(base_url, wait_until="domcontentloaded", timeout=args.timeout_ms)
        wait_for_ui(page, args.timeout_ms)

        # Step 1: Login with Google.
        try:
            if is_main_app_visible(page, args.timeout_ms):
                dashboard_shot = screenshot(page, output_dir, "dashboard_loaded")
                mark_result(
                    results,
                    "Login",
                    True,
                    "Main application sidebar is already visible (existing session).",
                    [dashboard_shot],
                    page.url,
                )
            else:
                login_cta = first_visible(
                    text_locators(
                        page,
                        r"(Sign\s*in\s*with\s*Google|Continuar\s*con\s*Google|Google|Iniciar\s*Sesi[o\u00f3]n)",
                    ),
                    timeout_ms=5000,
                )
                if login_cta is None:
                    raise AssertionError("Google login entry point was not found.")

                before_pages = set(context.pages)
                login_cta.click(timeout=args.timeout_ms)
                wait_for_ui(page, args.timeout_ms)
                after_pages = set(context.pages)
                new_pages = [p for p in after_pages if p not in before_pages]
                google_page = new_pages[-1] if new_pages else page
                google_page.bring_to_front()
                wait_for_ui(google_page, args.timeout_ms)

                account_target = first_visible(text_locators(google_page, re.escape(args.google_account)), timeout_ms=3000)
                if account_target is not None:
                    account_target.click(timeout=args.timeout_ms)
                    wait_for_ui(google_page, args.timeout_ms)

                if google_page is not page:
                    try:
                        google_page.wait_for_event("close", timeout=12000)
                    except PlaywrightTimeoutError:
                        pass
                    page.bring_to_front()
                    wait_for_ui(page, args.timeout_ms)

                assert_sidebar_visible(page, args.timeout_ms)
                dashboard_shot = screenshot(page, output_dir, "dashboard_loaded")
                mark_result(results, "Login", True, "Dashboard and left sidebar are visible after Google login.", [dashboard_shot], page.url)
        except Exception as exc:  # noqa: BLE001
            fail_shot = screenshot(page, output_dir, "login_failure")
            mark_result(results, "Login", False, f"{type(exc).__name__}: {exc}", [fail_shot], page.url)

        def mark_blocked(field_name: str, reason: str):
            mark_result(results, field_name, False, f"Blocked: {reason}", final_url=page.url)

        if not results["Login"].passed:
            block_reason = "Login did not reach the main application interface."
            mark_blocked("Mi Negocio menu", block_reason)
            mark_blocked("Agregar Negocio modal", block_reason)
            mark_blocked("Administrar Negocios view", block_reason)
            mark_blocked("Informacion General", block_reason)
            mark_blocked("Detalles de la Cuenta", block_reason)
            mark_blocked("Tus Negocios", block_reason)
            mark_blocked("Terminos y Condiciones", block_reason)
            mark_blocked("Politica de Privacidad", block_reason)
            browser.close()
        else:
            # Step 2: Open Mi Negocio menu.
            try:
                expand_mi_negocio_menu(page, args.timeout_ms)
                assert_visible(page, r"Agregar\s*Negocio", args.timeout_ms, "Agregar Negocio option")
                assert_visible(page, r"Administrar\s*Negocios", args.timeout_ms, "Administrar Negocios option")
                menu_shot = screenshot(page, output_dir, "mi_negocio_menu_expanded")
                mark_result(results, "Mi Negocio menu", True, "Menu expanded and both submenu options are visible.", [menu_shot], page.url)
            except Exception as exc:  # noqa: BLE001
                fail_shot = screenshot(page, output_dir, "mi_negocio_menu_failure")
                mark_result(results, "Mi Negocio menu", False, f"{type(exc).__name__}: {exc}", [fail_shot], page.url)

            # Step 3: Validate Agregar Negocio modal.
            try:
                click_by_text(page, r"Agregar\s*Negocio", args.timeout_ms, "Agregar Negocio")
                assert_visible(page, r"Crear\s*Nuevo\s*Negocio", args.timeout_ms, "Crear Nuevo Negocio title")
                modal_input = first_visible(
                    [
                        page.get_by_label(re.compile(r"Nombre\s+del\s+Negocio", re.IGNORECASE)),
                        page.get_by_placeholder(re.compile(r"Nombre\s+del\s+Negocio", re.IGNORECASE)),
                    ],
                    timeout_ms=args.timeout_ms,
                )
                if modal_input is None:
                    raise AssertionError("Input field 'Nombre del Negocio' is not visible.")

                assert_visible(page, r"Tienes\s+2\s+de\s+3\s+negocios", args.timeout_ms, "Quota text in modal")
                assert_visible(page, r"Cancelar", args.timeout_ms, "Cancelar button")
                assert_visible(page, r"Crear\s*Negocio", args.timeout_ms, "Crear Negocio button")

                modal_shot = screenshot(page, output_dir, "agregar_negocio_modal")
                modal_input.click(timeout=args.timeout_ms)
                modal_input.fill("Negocio Prueba Automatizacion", timeout=args.timeout_ms)
                click_by_text(page, r"Cancelar", args.timeout_ms, "Cancelar modal")
                mark_result(results, "Agregar Negocio modal", True, "Modal fields, text and actions validated.", [modal_shot], page.url)
            except Exception as exc:  # noqa: BLE001
                fail_shot = screenshot(page, output_dir, "agregar_negocio_modal_failure")
                mark_result(results, "Agregar Negocio modal", False, f"{type(exc).__name__}: {exc}", [fail_shot], page.url)

            # Step 4: Open Administrar Negocios.
            try:
                expand_mi_negocio_menu(page, args.timeout_ms)
                click_by_text(page, r"Administrar\s*Negocios", args.timeout_ms, "Administrar Negocios")
                assert_visible(page, r"Informaci[o\u00f3]n\s*General", args.timeout_ms, "Informacion General section")
                assert_visible(page, r"Detalles\s*de\s*la\s*Cuenta", args.timeout_ms, "Detalles de la Cuenta section")
                assert_visible(page, r"Tus\s*Negocios", args.timeout_ms, "Tus Negocios section")
                assert_visible(page, r"Secci[o\u00f3]n\s*Legal", args.timeout_ms, "Seccion Legal section")
                account_page_shot = screenshot(page, output_dir, "administrar_negocios_page", full_page=True)
                mark_result(results, "Administrar Negocios view", True, "All account sections are visible.", [account_page_shot], page.url)
            except Exception as exc:  # noqa: BLE001
                fail_shot = screenshot(page, output_dir, "administrar_negocios_failure")
                mark_result(results, "Administrar Negocios view", False, f"{type(exc).__name__}: {exc}", [fail_shot], page.url)

            if not results["Administrar Negocios view"].passed:
                block_reason = "Administrar Negocios view was not reached."
                mark_blocked("Informacion General", block_reason)
                mark_blocked("Detalles de la Cuenta", block_reason)
                mark_blocked("Tus Negocios", block_reason)
                mark_blocked("Terminos y Condiciones", block_reason)
                mark_blocked("Politica de Privacidad", block_reason)
                browser.close()
            else:
                # Step 5: Validate Informacion General.
                try:
                    assert_visible(page, r"BUSINESS\s*PLAN", args.timeout_ms, "BUSINESS PLAN badge")
                    assert_visible(page, r"Cambiar\s*Plan", args.timeout_ms, "Cambiar Plan button")
                    email_target = first_visible(
                        [page.get_by_text(re.compile(r"[A-Z0-9._%+\-]+@[A-Z0-9.\-]+\.[A-Z]{2,}", re.IGNORECASE))],
                        timeout_ms=args.timeout_ms,
                    )
                    if email_target is None:
                        raise AssertionError("User email is not visible.")
                    mark_result(results, "Informacion General", True, "User email and plan controls are visible.", final_url=page.url)
                except Exception as exc:  # noqa: BLE001
                    fail_shot = screenshot(page, output_dir, "informacion_general_failure")
                    mark_result(results, "Informacion General", False, f"{type(exc).__name__}: {exc}", [fail_shot], page.url)

                # Step 6: Validate Detalles de la Cuenta.
                try:
                    assert_visible(page, r"Cuenta\s*creada", args.timeout_ms, "Cuenta creada label")
                    assert_visible(page, r"Estado\s*activo", args.timeout_ms, "Estado activo label")
                    assert_visible(page, r"Idioma\s*seleccionado", args.timeout_ms, "Idioma seleccionado label")
                    mark_result(results, "Detalles de la Cuenta", True, "Account details labels are visible.", final_url=page.url)
                except Exception as exc:  # noqa: BLE001
                    fail_shot = screenshot(page, output_dir, "detalles_cuenta_failure")
                    mark_result(results, "Detalles de la Cuenta", False, f"{type(exc).__name__}: {exc}", [fail_shot], page.url)

                # Step 7: Validate Tus Negocios.
                try:
                    assert_visible(page, r"Tus\s*Negocios", args.timeout_ms, "Tus Negocios section title")
                    assert_visible(page, r"Agregar\s*Negocio", args.timeout_ms, "Agregar Negocio button in businesses section")
                    assert_visible(page, r"Tienes\s+2\s+de\s+3\s+negocios", args.timeout_ms, "Quota text in businesses section")
                    business_item = first_visible(
                        [
                            page.locator("[class*='business'], [data-testid*='business'], li, div").filter(has_text=re.compile(r"negocio", re.IGNORECASE)),
                            page.locator("table tr"),
                        ],
                        timeout_ms=2000,
                    )
                    if business_item is None:
                        raise AssertionError("Business list content is not visible.")
                    mark_result(results, "Tus Negocios", True, "Business list, button and quota text are visible.", final_url=page.url)
                except Exception as exc:  # noqa: BLE001
                    fail_shot = screenshot(page, output_dir, "tus_negocios_failure")
                    mark_result(results, "Tus Negocios", False, f"{type(exc).__name__}: {exc}", [fail_shot], page.url)

                # Step 8: Validate Terminos y Condiciones.
                try:
                    terms_shot, terms_url = open_legal_link_and_validate(
                        app_page=page,
                        context=context,
                        link_text_regex=r"T[e\u00e9]rminos\s+y\s+Condiciones",
                        heading_regex=r"T[e\u00e9]rminos\s+y\s+Condiciones",
                        timeout_ms=args.timeout_ms,
                        output_dir=output_dir,
                        screenshot_name="terminos_y_condiciones",
                    )
                    mark_result(results, "Terminos y Condiciones", True, "Terms page heading and legal text are visible.", [terms_shot], terms_url)
                except Exception as exc:  # noqa: BLE001
                    fail_shot = screenshot(page, output_dir, "terminos_failure")
                    mark_result(results, "Terminos y Condiciones", False, f"{type(exc).__name__}: {exc}", [fail_shot], page.url)

                # Step 9: Validate Politica de Privacidad.
                try:
                    privacy_shot, privacy_url = open_legal_link_and_validate(
                        app_page=page,
                        context=context,
                        link_text_regex=r"Pol[i\u00ed]tica\s+de\s+Privacidad",
                        heading_regex=r"Pol[i\u00ed]tica\s+de\s+Privacidad",
                        timeout_ms=args.timeout_ms,
                        output_dir=output_dir,
                        screenshot_name="politica_de_privacidad",
                    )
                    mark_result(results, "Politica de Privacidad", True, "Privacy page heading and legal text are visible.", [privacy_shot], privacy_url)
                except Exception as exc:  # noqa: BLE001
                    fail_shot = screenshot(page, output_dir, "politica_failure")
                    mark_result(results, "Politica de Privacidad", False, f"{type(exc).__name__}: {exc}", [fail_shot], page.url)

                browser.close()

        browser.close()

    # Step 10: Final report.
    final_summary = {field: ("PASS" if results.get(field, StepResult(field, False, "Not executed")).passed else "FAIL") for field in REPORT_FIELDS}
    report_payload = {
        "test_name": "saleads_mi_negocio_full_test",
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "summary": final_summary,
        "steps": [asdict(results[field]) for field in REPORT_FIELDS if field in results],
        "screenshot_count": sum(len(step.screenshots) for step in results.values()),
    }
    report_path.write_text(json.dumps(report_payload, indent=2), encoding="utf-8")
    print(json.dumps(report_payload, indent=2))

    return 0 if all(status == "PASS" for status in final_summary.values()) else 1


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    env_base_url = os.environ.get("SALEADS_BASE_URL", "").strip()
    if not args.base_url and env_base_url:
        args.base_url = env_base_url
    return run(args)


if __name__ == "__main__":
    raise SystemExit(main())
