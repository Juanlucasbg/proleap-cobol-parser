#!/usr/bin/env python3
"""SaleADS Mi Negocio full workflow validation using Playwright."""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from playwright.sync_api import Locator, Page, TimeoutError as PlaywrightTimeoutError, sync_playwright


DEFAULT_TIMEOUT_MS = 25000


@dataclass
class StepResult:
    name: str
    status: str = "FAIL"
    validations: list[dict[str, Any]] = field(default_factory=list)
    evidence: list[str] = field(default_factory=list)
    final_url: str | None = None
    error: str | None = None

    def mark_validation(self, label: str, passed: bool) -> None:
        self.validations.append({"label": label, "status": "PASS" if passed else "FAIL"})

    def conclude(self) -> None:
        self.status = "PASS" if all(v["status"] == "PASS" for v in self.validations) else "FAIL"


def utc_timestamp() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


def ensure_ui_loaded(page: Page) -> None:
    page.wait_for_load_state("domcontentloaded", timeout=DEFAULT_TIMEOUT_MS)
    try:
        page.wait_for_load_state("networkidle", timeout=5000)
    except PlaywrightTimeoutError:
        # Some apps keep websocket/network traffic open indefinitely.
        pass


def safe_visible(locator: Locator, timeout_ms: int = 3000) -> bool:
    try:
        locator.first.wait_for(state="visible", timeout=timeout_ms)
        return True
    except PlaywrightTimeoutError:
        return False


def first_visible(locators: list[Locator], timeout_ms: int = 3000) -> Locator | None:
    for candidate in locators:
        if safe_visible(candidate, timeout_ms):
            return candidate.first
    return None


def click_and_wait(page: Page, locator: Locator) -> None:
    locator.click(timeout=DEFAULT_TIMEOUT_MS)
    ensure_ui_loaded(page)


def capture_screenshot(page: Page, output_dir: Path, filename: str, full_page: bool = False) -> str:
    path = output_dir / filename
    page.screenshot(path=str(path), full_page=full_page)
    return str(path)


def find_sidebar(page: Page) -> Locator | None:
    return first_visible(
        [
            page.get_by_role("navigation"),
            page.locator("aside"),
            page.locator("[class*='sidebar']"),
        ],
        timeout_ms=5000,
    )


def click_google_login(page: Page, google_email: str) -> None:
    login_button = first_visible(
        [
            page.get_by_role("button", name=re.compile(r"sign in with google", re.IGNORECASE)),
            page.get_by_role("button", name=re.compile(r"iniciar sesi[oó]n con google", re.IGNORECASE)),
            page.get_by_role("button", name=re.compile(r"continuar con google", re.IGNORECASE)),
            page.get_by_text(re.compile(r"google", re.IGNORECASE)),
        ],
        timeout_ms=7000,
    )
    if not login_button:
        raise RuntimeError("Google login button was not found.")

    popup: Page | None = None
    try:
        with page.context.expect_page(timeout=7000) as page_event:
            login_button.click(timeout=DEFAULT_TIMEOUT_MS)
        popup = page_event.value
        ensure_ui_loaded(popup)
    except PlaywrightTimeoutError:
        click_and_wait(page, login_button)

    account_page = popup or page
    account_selector = first_visible(
        [
            account_page.get_by_text(google_email, exact=True),
            account_page.get_by_role("button", name=re.compile(re.escape(google_email), re.IGNORECASE)),
            account_page.get_by_role("link", name=re.compile(re.escape(google_email), re.IGNORECASE)),
        ],
        timeout_ms=8000,
    )
    if account_selector:
        account_selector.click(timeout=DEFAULT_TIMEOUT_MS)
        ensure_ui_loaded(account_page)

    if popup:
        try:
            popup.wait_for_close(timeout=15000)
        except PlaywrightTimeoutError:
            # Some Google flows do not auto-close and still authenticate.
            pass
        page.bring_to_front()
    ensure_ui_loaded(page)


def step_login(page: Page, output_dir: Path, google_email: str) -> StepResult:
    result = StepResult(name="Login")
    try:
        click_google_login(page, google_email)
        sidebar = find_sidebar(page)
        main_ui_visible = safe_visible(page.locator("main"), timeout_ms=6000) or safe_visible(
            page.get_by_text(re.compile(r"dashboard|inicio|panel|negocio", re.IGNORECASE)),
            timeout_ms=6000,
        )
        result.mark_validation("Main application interface appears", main_ui_visible)
        result.mark_validation("Left sidebar navigation is visible", sidebar is not None)
        result.evidence.append(capture_screenshot(page, output_dir, "01_dashboard_loaded.png", full_page=True))
    except Exception as exc:  # noqa: BLE001
        result.error = str(exc)
        result.mark_validation("Main application interface appears", False)
        result.mark_validation("Left sidebar navigation is visible", False)
    result.conclude()
    return result


def expand_mi_negocio_menu(page: Page) -> None:
    negocio = first_visible(
        [
            page.get_by_role("link", name=re.compile(r"^negocio$", re.IGNORECASE)),
            page.get_by_role("button", name=re.compile(r"^negocio$", re.IGNORECASE)),
            page.get_by_text(re.compile(r"^negocio$", re.IGNORECASE)),
        ],
        timeout_ms=5000,
    )
    if negocio:
        click_and_wait(page, negocio)

    mi_negocio = first_visible(
        [
            page.get_by_role("link", name=re.compile(r"mi negocio", re.IGNORECASE)),
            page.get_by_role("button", name=re.compile(r"mi negocio", re.IGNORECASE)),
            page.get_by_text(re.compile(r"mi negocio", re.IGNORECASE)),
        ],
        timeout_ms=5000,
    )
    if not mi_negocio:
        raise RuntimeError("'Mi Negocio' menu option was not found.")
    click_and_wait(page, mi_negocio)


def step_mi_negocio_menu(page: Page, output_dir: Path) -> StepResult:
    result = StepResult(name="Mi Negocio menu")
    try:
        expand_mi_negocio_menu(page)
        add_business_visible = safe_visible(
            page.get_by_text(re.compile(r"agregar negocio", re.IGNORECASE)),
            timeout_ms=5000,
        )
        manage_business_visible = safe_visible(
            page.get_by_text(re.compile(r"administrar negocios", re.IGNORECASE)),
            timeout_ms=5000,
        )
        result.mark_validation("Submenu expands", add_business_visible or manage_business_visible)
        result.mark_validation("'Agregar Negocio' is visible", add_business_visible)
        result.mark_validation("'Administrar Negocios' is visible", manage_business_visible)
        result.evidence.append(capture_screenshot(page, output_dir, "02_mi_negocio_menu_expanded.png"))
    except Exception as exc:  # noqa: BLE001
        result.error = str(exc)
        result.mark_validation("Submenu expands", False)
        result.mark_validation("'Agregar Negocio' is visible", False)
        result.mark_validation("'Administrar Negocios' is visible", False)
    result.conclude()
    return result


def step_agregar_negocio_modal(page: Page, output_dir: Path) -> StepResult:
    result = StepResult(name="Agregar Negocio modal")
    try:
        add_business = first_visible(
            [
                page.get_by_role("button", name=re.compile(r"agregar negocio", re.IGNORECASE)),
                page.get_by_role("link", name=re.compile(r"agregar negocio", re.IGNORECASE)),
                page.get_by_text(re.compile(r"agregar negocio", re.IGNORECASE)),
            ],
            timeout_ms=5000,
        )
        if not add_business:
            raise RuntimeError("'Agregar Negocio' action was not found.")

        click_and_wait(page, add_business)
        modal = page.get_by_role("dialog")
        modal.wait_for(state="visible", timeout=DEFAULT_TIMEOUT_MS)

        title_ok = safe_visible(modal.get_by_text(re.compile(r"crear nuevo negocio", re.IGNORECASE)), timeout_ms=4000)
        name_input = modal.get_by_label(re.compile(r"nombre del negocio", re.IGNORECASE))
        name_input_ok = safe_visible(name_input, timeout_ms=4000)
        quota_ok = safe_visible(modal.get_by_text(re.compile(r"tienes\s+2\s+de\s+3\s+negocios", re.IGNORECASE)))
        cancel_ok = safe_visible(modal.get_by_role("button", name=re.compile(r"cancelar", re.IGNORECASE)))
        create_ok = safe_visible(modal.get_by_role("button", name=re.compile(r"crear negocio", re.IGNORECASE)))

        result.mark_validation("Modal title 'Crear Nuevo Negocio' is visible", title_ok)
        result.mark_validation("Input field 'Nombre del Negocio' exists", name_input_ok)
        result.mark_validation("Text 'Tienes 2 de 3 negocios' is visible", quota_ok)
        result.mark_validation("Buttons 'Cancelar' and 'Crear Negocio' are present", cancel_ok and create_ok)
        result.evidence.append(capture_screenshot(page, output_dir, "03_agregar_negocio_modal.png"))

        if name_input_ok:
            name_input.click(timeout=DEFAULT_TIMEOUT_MS)
            name_input.fill("Negocio Prueba Automatizacion", timeout=DEFAULT_TIMEOUT_MS)

        cancel_btn = modal.get_by_role("button", name=re.compile(r"cancelar", re.IGNORECASE))
        if safe_visible(cancel_btn, timeout_ms=2000):
            click_and_wait(page, cancel_btn)
    except Exception as exc:  # noqa: BLE001
        result.error = str(exc)
        result.mark_validation("Modal title 'Crear Nuevo Negocio' is visible", False)
        result.mark_validation("Input field 'Nombre del Negocio' exists", False)
        result.mark_validation("Text 'Tienes 2 de 3 negocios' is visible", False)
        result.mark_validation("Buttons 'Cancelar' and 'Crear Negocio' are present", False)
    result.conclude()
    return result


def step_administrar_negocios(page: Page, output_dir: Path) -> StepResult:
    result = StepResult(name="Administrar Negocios view")
    try:
        if not safe_visible(page.get_by_text(re.compile(r"administrar negocios", re.IGNORECASE)), timeout_ms=2500):
            expand_mi_negocio_menu(page)

        manage = first_visible(
            [
                page.get_by_role("link", name=re.compile(r"administrar negocios", re.IGNORECASE)),
                page.get_by_role("button", name=re.compile(r"administrar negocios", re.IGNORECASE)),
                page.get_by_text(re.compile(r"administrar negocios", re.IGNORECASE)),
            ],
            timeout_ms=5000,
        )
        if not manage:
            raise RuntimeError("'Administrar Negocios' action was not found.")

        click_and_wait(page, manage)

        info_general_ok = safe_visible(page.get_by_text(re.compile(r"informaci[oó]n general", re.IGNORECASE)), timeout_ms=6000)
        account_details_ok = safe_visible(page.get_by_text(re.compile(r"detalles de la cuenta", re.IGNORECASE)), timeout_ms=6000)
        your_business_ok = safe_visible(page.get_by_text(re.compile(r"tus negocios", re.IGNORECASE)), timeout_ms=6000)
        legal_ok = safe_visible(page.get_by_text(re.compile(r"secci[oó]n legal", re.IGNORECASE)), timeout_ms=6000)

        result.mark_validation("Section 'Información General' exists", info_general_ok)
        result.mark_validation("Section 'Detalles de la Cuenta' exists", account_details_ok)
        result.mark_validation("Section 'Tus Negocios' exists", your_business_ok)
        result.mark_validation("Section 'Sección Legal' exists", legal_ok)
        result.evidence.append(capture_screenshot(page, output_dir, "04_administrar_negocios_full_page.png", full_page=True))
    except Exception as exc:  # noqa: BLE001
        result.error = str(exc)
        result.mark_validation("Section 'Información General' exists", False)
        result.mark_validation("Section 'Detalles de la Cuenta' exists", False)
        result.mark_validation("Section 'Tus Negocios' exists", False)
        result.mark_validation("Section 'Sección Legal' exists", False)
    result.conclude()
    return result


def step_info_general(page: Page) -> StepResult:
    result = StepResult(name="Información General")
    try:
        user_name_ok = safe_visible(page.locator("h1, h2, h3, p, span").filter(has_text=re.compile(r"[A-Za-z].+[A-Za-z]")))
        email_ok = safe_visible(page.get_by_text(re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")))
        plan_ok = safe_visible(page.get_by_text(re.compile(r"business plan", re.IGNORECASE)))
        change_plan_ok = safe_visible(page.get_by_role("button", name=re.compile(r"cambiar plan", re.IGNORECASE)))
        result.mark_validation("User name is visible", user_name_ok)
        result.mark_validation("User email is visible", email_ok)
        result.mark_validation("Text 'BUSINESS PLAN' is visible", plan_ok)
        result.mark_validation("Button 'Cambiar Plan' is visible", change_plan_ok)
    except Exception as exc:  # noqa: BLE001
        result.error = str(exc)
        result.mark_validation("User name is visible", False)
        result.mark_validation("User email is visible", False)
        result.mark_validation("Text 'BUSINESS PLAN' is visible", False)
        result.mark_validation("Button 'Cambiar Plan' is visible", False)
    result.conclude()
    return result


def step_detalles_cuenta(page: Page) -> StepResult:
    result = StepResult(name="Detalles de la Cuenta")
    try:
        created_ok = safe_visible(page.get_by_text(re.compile(r"cuenta creada", re.IGNORECASE)))
        active_ok = safe_visible(page.get_by_text(re.compile(r"estado activo", re.IGNORECASE)))
        language_ok = safe_visible(page.get_by_text(re.compile(r"idioma seleccionado", re.IGNORECASE)))
        result.mark_validation("'Cuenta creada' is visible", created_ok)
        result.mark_validation("'Estado activo' is visible", active_ok)
        result.mark_validation("'Idioma seleccionado' is visible", language_ok)
    except Exception as exc:  # noqa: BLE001
        result.error = str(exc)
        result.mark_validation("'Cuenta creada' is visible", False)
        result.mark_validation("'Estado activo' is visible", False)
        result.mark_validation("'Idioma seleccionado' is visible", False)
    result.conclude()
    return result


def step_tus_negocios(page: Page) -> StepResult:
    result = StepResult(name="Tus Negocios")
    try:
        list_ok = safe_visible(page.get_by_text(re.compile(r"tus negocios", re.IGNORECASE)))
        add_button_ok = safe_visible(page.get_by_role("button", name=re.compile(r"agregar negocio", re.IGNORECASE)))
        quota_ok = safe_visible(page.get_by_text(re.compile(r"tienes\s+2\s+de\s+3\s+negocios", re.IGNORECASE)))
        result.mark_validation("Business list is visible", list_ok)
        result.mark_validation("Button 'Agregar Negocio' exists", add_button_ok)
        result.mark_validation("Text 'Tienes 2 de 3 negocios' is visible", quota_ok)
    except Exception as exc:  # noqa: BLE001
        result.error = str(exc)
        result.mark_validation("Business list is visible", False)
        result.mark_validation("Button 'Agregar Negocio' exists", False)
        result.mark_validation("Text 'Tienes 2 de 3 negocios' is visible", False)
    result.conclude()
    return result


def step_legal_link(page: Page, output_dir: Path, link_text: str, heading_text: str, screenshot_name: str) -> StepResult:
    result = StepResult(name=link_text)
    try:
        link = first_visible(
            [
                page.get_by_role("link", name=re.compile(link_text, re.IGNORECASE)),
                page.get_by_text(re.compile(link_text, re.IGNORECASE)),
            ],
            timeout_ms=5000,
        )
        if not link:
            raise RuntimeError(f"Legal link '{link_text}' not found.")

        source_url = page.url
        target_page = page
        opened_new_tab = False

        try:
            with page.context.expect_page(timeout=7000) as popup_event:
                link.click(timeout=DEFAULT_TIMEOUT_MS)
            target_page = popup_event.value
            opened_new_tab = True
            ensure_ui_loaded(target_page)
        except PlaywrightTimeoutError:
            click_and_wait(page, link)
            target_page = page

        heading_ok = safe_visible(target_page.get_by_text(re.compile(heading_text, re.IGNORECASE)), timeout_ms=8000)
        content_ok = safe_visible(target_page.locator("main p, article p, p"), timeout_ms=8000)
        result.mark_validation(f"The page contains heading '{heading_text}'", heading_ok)
        result.mark_validation("Legal content text is visible", content_ok)
        result.evidence.append(capture_screenshot(target_page, output_dir, screenshot_name, full_page=True))
        result.final_url = target_page.url

        if opened_new_tab:
            target_page.close()
            page.bring_to_front()
        elif target_page.url != source_url:
            page.go_back(wait_until="domcontentloaded", timeout=DEFAULT_TIMEOUT_MS)
            ensure_ui_loaded(page)
    except Exception as exc:  # noqa: BLE001
        result.error = str(exc)
        result.mark_validation(f"The page contains heading '{heading_text}'", False)
        result.mark_validation("Legal content text is visible", False)
    result.conclude()
    return result


def final_matrix(step_results: list[StepResult]) -> dict[str, str]:
    mapping: dict[str, str] = {}
    for result in step_results:
        mapping[result.name] = result.status
    return mapping


def persist_report(output_dir: Path, step_results: list[StepResult]) -> Path:
    report = {
        "test_name": "saleads_mi_negocio_full_test",
        "executed_at_utc": datetime.now(timezone.utc).isoformat(),
        "results": [
            {
                "name": result.name,
                "status": result.status,
                "validations": result.validations,
                "evidence": result.evidence,
                "final_url": result.final_url,
                "error": result.error,
            }
            for result in step_results
        ],
        "final_report": final_matrix(step_results),
    }
    report_path = output_dir / "final_report.json"
    report_path.write_text(json.dumps(report, indent=2, ensure_ascii=True), encoding="utf-8")
    return report_path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate SaleADS Mi Negocio full workflow.")
    parser.add_argument("--start-url", default=os.getenv("SALEADS_START_URL"), help="Environment login URL")
    parser.add_argument("--cdp-url", default=os.getenv("SALEADS_CDP_URL"), help="Existing browser CDP endpoint")
    parser.add_argument(
        "--google-email",
        default=os.getenv("SALEADS_GOOGLE_EMAIL", "juanlucasbarbiergarzon@gmail.com"),
        help="Google account email to select",
    )
    parser.add_argument("--headed", action="store_true", help="Run with visible browser")
    parser.add_argument("--slow-mo-ms", type=int, default=0, help="Slow down browser actions")
    parser.add_argument(
        "--output-dir",
        default=str(Path("automation") / "artifacts" / f"saleads-mi-negocio-{utc_timestamp()}"),
        help="Directory for screenshots and final report",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    step_results: list[StepResult] = []

    if not args.cdp_url and not args.start_url:
        blocked = StepResult(name="Precondition")
        blocked.mark_validation("Either --start-url or --cdp-url is provided", False)
        blocked.error = "No start URL or CDP endpoint was provided."
        blocked.conclude()
        step_results.append(blocked)
        report_path = persist_report(output_dir, step_results)
        print(json.dumps({"status": "FAIL", "report": str(report_path)}, indent=2))
        return 2

    with sync_playwright() as playwright:
        browser = None
        context = None
        page = None

        try:
            if args.cdp_url:
                browser = playwright.chromium.connect_over_cdp(args.cdp_url)
                context = browser.contexts[0] if browser.contexts else browser.new_context()
                page = context.pages[0] if context.pages else context.new_page()
                ensure_ui_loaded(page)
            else:
                browser = playwright.chromium.launch(headless=not args.headed, slow_mo=args.slow_mo_ms)
                context = browser.new_context(ignore_https_errors=True, viewport={"width": 1440, "height": 900})
                page = context.new_page()
                page.goto(args.start_url, wait_until="domcontentloaded", timeout=DEFAULT_TIMEOUT_MS)
                ensure_ui_loaded(page)

            step_results.append(step_login(page, output_dir, args.google_email))
            step_results.append(step_mi_negocio_menu(page, output_dir))
            step_results.append(step_agregar_negocio_modal(page, output_dir))
            step_results.append(step_administrar_negocios(page, output_dir))
            step_results.append(step_info_general(page))
            step_results.append(step_detalles_cuenta(page))
            step_results.append(step_tus_negocios(page))
            step_results.append(
                step_legal_link(
                    page,
                    output_dir,
                    link_text="Términos y Condiciones",
                    heading_text="Términos y Condiciones",
                    screenshot_name="08_terminos_y_condiciones.png",
                )
            )
            step_results.append(
                step_legal_link(
                    page,
                    output_dir,
                    link_text="Política de Privacidad",
                    heading_text="Política de Privacidad",
                    screenshot_name="09_politica_de_privacidad.png",
                )
            )
        finally:
            report_path = persist_report(output_dir, step_results)
            print(json.dumps({"final_report": final_matrix(step_results), "report_path": str(report_path)}, indent=2))
            if context:
                context.close()
            if browser:
                browser.close()

    return 0 if all(step.status == "PASS" for step in step_results) else 1


if __name__ == "__main__":
    sys.exit(main())
