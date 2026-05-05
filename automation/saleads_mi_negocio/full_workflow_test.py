#!/usr/bin/env python3
"""
SaleADS.ai Mi Negocio full workflow validation.

This script is environment-agnostic:
- It does not hardcode any SaleADS domain.
- It can start from a provided URL (SALEADS_URL) or attach to an existing browser tab
  via CHROME_CDP_URL.
- It performs login (Google) and continues through the complete Mi Negocio workflow.
"""

from __future__ import annotations

import json
import os
import re
import sys
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Sequence, Tuple

try:
    from playwright.sync_api import Browser, BrowserContext, Error, Page, TimeoutError, sync_playwright
    PLAYWRIGHT_IMPORT_ERROR: Exception | None = None
except ModuleNotFoundError as exc:
    PLAYWRIGHT_IMPORT_ERROR = exc
    Browser = object  # type: ignore[assignment,misc]
    BrowserContext = object  # type: ignore[assignment,misc]
    Page = object  # type: ignore[assignment,misc]

    class TimeoutError(Exception):
        pass

    class Error(Exception):
        pass

    def sync_playwright():
        raise RuntimeError(
            "Playwright is not installed. Install with: pip3 install playwright && python3 -m playwright install chromium"
        )


DEFAULT_EMAIL = "juanlucasbarbiergarzon@gmail.com"
REPORT_FIELDS = [
    "Login",
    "Mi Negocio menu",
    "Agregar Negocio modal",
    "Administrar Negocios view",
    "Informaci\u00f3n General",
    "Detalles de la Cuenta",
    "Tus Negocios",
    "T\u00e9rminos y Condiciones",
    "Pol\u00edtica de Privacidad",
]


@dataclass
class StepReport:
    status: str = "FAIL"
    validations: List[Tuple[str, bool]] = field(default_factory=list)
    evidence: List[str] = field(default_factory=list)
    notes: List[str] = field(default_factory=list)

    @property
    def passed(self) -> bool:
        return self.status == "PASS"

    def add_validation(self, label: str, passed: bool) -> None:
        self.validations.append((label, passed))
        if not passed:
            self.status = "FAIL"

    def finalize(self) -> None:
        self.status = "PASS" if all(v for _, v in self.validations) else "FAIL"


def now_stamp() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


def make_artifacts_dir() -> Path:
    root = Path(__file__).resolve().parent / "artifacts" / now_stamp()
    root.mkdir(parents=True, exist_ok=True)
    return root


def sanitize_filename(name: str) -> str:
    return re.sub(r"[^a-zA-Z0-9._-]+", "_", name).strip("_").lower()


def wait_ui(page: Page, ms: int = 1200) -> None:
    # Rule compliance: always wait for UI load after each click.
    try:
        page.wait_for_load_state("domcontentloaded", timeout=8000)
    except TimeoutError:
        pass
    try:
        page.wait_for_load_state("networkidle", timeout=8000)
    except TimeoutError:
        pass
    page.wait_for_timeout(ms)


def text_visible(page: Page, text: str, timeout_ms: int = 5000) -> bool:
    candidates = [
        page.get_by_text(text, exact=True).first,
        page.get_by_text(text).first,
        page.get_by_role("button", name=text).first,
        page.get_by_role("link", name=text).first,
        page.get_by_role("menuitem", name=text).first,
    ]
    for locator in candidates:
        try:
            locator.wait_for(state="visible", timeout=timeout_ms)
            return True
        except TimeoutError:
            continue
    return False


def click_first_visible(page: Page, labels: Sequence[str], timeout_ms: int = 5000) -> bool:
    for label in labels:
        candidates = [
            page.get_by_role("button", name=label).first,
            page.get_by_role("link", name=label).first,
            page.get_by_role("menuitem", name=label).first,
            page.get_by_text(label, exact=True).first,
            page.get_by_text(label).first,
        ]
        for locator in candidates:
            try:
                locator.wait_for(state="visible", timeout=timeout_ms)
                locator.click(timeout=timeout_ms)
                wait_ui(page)
                return True
            except (TimeoutError, Error):
                continue
    return False


def screenshot(page: Page, artifacts_dir: Path, name: str, full_page: bool = False) -> str:
    file_name = f"{sanitize_filename(name)}.png"
    path = artifacts_dir / file_name
    page.screenshot(path=str(path), full_page=full_page)
    return str(path)


def is_google_selector_page(page: Page) -> bool:
    if "accounts.google.com" in page.url:
        return True

    for marker in (
        "Choose an account",
        "Selecciona una cuenta",
        "Elige una cuenta",
        "Use another account",
        "Usar otra cuenta",
    ):
        if text_visible(page, marker, timeout_ms=400):
            return True
    return False


def choose_google_account(context: BrowserContext, email: str, timeout_seconds: int = 20) -> Tuple[bool, bool]:
    prompt_seen = False
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        for p in context.pages:
            try:
                p.wait_for_load_state("domcontentloaded", timeout=1000)
            except TimeoutError:
                pass

            if is_google_selector_page(p):
                prompt_seen = True

            email_locators = [
                p.get_by_text(email, exact=True).first,
                p.get_by_role("button", name=email).first,
                p.get_by_role("link", name=email).first,
            ]
            for locator in email_locators:
                try:
                    locator.wait_for(state="visible", timeout=800)
                    locator.click(timeout=1200)
                    wait_ui(p, ms=1000)
                    return True, True
                except (TimeoutError, Error):
                    continue
        time.sleep(0.4)
    return prompt_seen, False


def find_email_on_page(page: Page) -> bool:
    text = page.locator("body").inner_text(timeout=6000)
    return bool(re.search(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", text))


def collect_report_field_defaults() -> Dict[str, StepReport]:
    return {field: StepReport(status="FAIL") for field in REPORT_FIELDS}


def validate_login(page: Page, context: BrowserContext, reports: Dict[str, StepReport], artifacts_dir: Path) -> None:
    report = reports["Login"]

    login_clicked = click_first_visible(
        page,
        [
            "Sign in with Google",
            "Continue with Google",
            "Ingresar con Google",
            "Iniciar sesion con Google",
            "Acceder con Google",
            "Google",
        ],
    )
    report.add_validation("Login button or Google sign-in was clicked", login_clicked)

    if login_clicked:
        prompt_seen, account_selected = choose_google_account(context, DEFAULT_EMAIL)
        report.add_validation(
            f"If Google account selector appears, account '{DEFAULT_EMAIL}' is selected",
            (not prompt_seen) or account_selected,
        )
        if prompt_seen and not account_selected:
            report.notes.append("Google account selector appeared but account could not be selected automatically.")
    else:
        report.add_validation("Google account selection step executed", False)

    main_interface_visible = text_visible(page, "Negocio", timeout_ms=15000) or page.locator("aside").first.is_visible()
    report.add_validation("Main application interface appears", main_interface_visible)

    sidebar_visible = page.locator("aside").first.is_visible() or text_visible(page, "Mi Negocio", timeout_ms=7000)
    report.add_validation("Left sidebar navigation is visible", sidebar_visible)

    if main_interface_visible:
        report.evidence.append(screenshot(page, artifacts_dir, "step1_dashboard_loaded"))

    report.finalize()


def validate_mi_negocio_menu(page: Page, reports: Dict[str, StepReport], artifacts_dir: Path) -> None:
    report = reports["Mi Negocio menu"]

    negocio_found = click_first_visible(page, ["Negocio"])
    report.add_validation("Negocio section located and clicked", negocio_found)

    mi_negocio_clicked = click_first_visible(page, ["Mi Negocio"])
    report.add_validation("Mi Negocio option clicked", mi_negocio_clicked)

    add_visible = text_visible(page, "Agregar Negocio")
    admin_visible = text_visible(page, "Administrar Negocios")
    report.add_validation("Submenu expanded showing Agregar Negocio", add_visible)
    report.add_validation("Submenu expanded showing Administrar Negocios", admin_visible)

    if add_visible or admin_visible:
        report.evidence.append(screenshot(page, artifacts_dir, "step2_mi_negocio_menu_expanded"))

    report.finalize()


def validate_agregar_modal(page: Page, reports: Dict[str, StepReport], artifacts_dir: Path) -> None:
    report = reports["Agregar Negocio modal"]

    open_modal_clicked = click_first_visible(page, ["Agregar Negocio"])
    report.add_validation("Agregar Negocio clicked", open_modal_clicked)

    title_visible = text_visible(page, "Crear Nuevo Negocio", timeout_ms=8000)
    name_input = page.get_by_label("Nombre del Negocio").first
    if name_input.count() == 0:
        name_input = page.get_by_placeholder("Nombre del Negocio").first
    try:
        name_input.wait_for(state="visible", timeout=5000)
        input_visible = True
    except TimeoutError:
        input_visible = False

    usage_text_visible = text_visible(page, "Tienes 2 de 3 negocios")
    cancel_visible = text_visible(page, "Cancelar")
    create_visible = text_visible(page, "Crear Negocio")

    report.add_validation("Modal title Crear Nuevo Negocio is visible", title_visible)
    report.add_validation("Input field Nombre del Negocio exists", input_visible)
    report.add_validation("Text Tienes 2 de 3 negocios is visible", usage_text_visible)
    report.add_validation("Buttons Cancelar and Crear Negocio are present", cancel_visible and create_visible)

    if title_visible:
        report.evidence.append(screenshot(page, artifacts_dir, "step3_agregar_negocio_modal"))

    if input_visible:
        try:
            name_input.click(timeout=2000)
            wait_ui(page, ms=600)
            name_input.fill("Negocio Prueba Automatizacion", timeout=3000)
            wait_ui(page, ms=600)
            report.notes.append("Optional modal input fill executed.")
        except (TimeoutError, Error):
            report.notes.append("Optional modal input fill could not be completed.")

    cancelled = click_first_visible(page, ["Cancelar", "Cancel"])
    report.notes.append("Optional modal cancel action executed." if cancelled else "Optional modal cancel action not completed.")
    report.finalize()


def validate_administrar_view(page: Page, reports: Dict[str, StepReport], artifacts_dir: Path) -> None:
    report = reports["Administrar Negocios view"]

    click_first_visible(page, ["Mi Negocio"])
    clicked_admin = click_first_visible(page, ["Administrar Negocios"])
    report.add_validation("Administrar Negocios clicked", clicked_admin)

    info_general = text_visible(page, "Informacion General") or text_visible(page, "Informaci\u00f3n General")
    detalles = text_visible(page, "Detalles de la Cuenta")
    tus_negocios = text_visible(page, "Tus Negocios")
    legal = text_visible(page, "Seccion Legal") or text_visible(page, "Secci\u00f3n Legal")

    report.add_validation("Section Informacion General exists", info_general)
    report.add_validation("Section Detalles de la Cuenta exists", detalles)
    report.add_validation("Section Tus Negocios exists", tus_negocios)
    report.add_validation("Section Seccion Legal exists", legal)

    if info_general or detalles or tus_negocios or legal:
        report.evidence.append(screenshot(page, artifacts_dir, "step4_account_page_full", full_page=True))

    report.finalize()


def validate_info_general(page: Page, reports: Dict[str, StepReport]) -> None:
    report = reports["Informaci\u00f3n General"]

    section_visible = text_visible(page, "Informacion General") or text_visible(page, "Informaci\u00f3n General")
    report.add_validation("Informaci\u00f3n General section is visible", section_visible)

    email_visible = find_email_on_page(page)
    report.add_validation("User email is visible", email_visible)

    business_plan_visible = text_visible(page, "BUSINESS PLAN")
    change_plan_visible = text_visible(page, "Cambiar Plan")
    report.add_validation("Text BUSINESS PLAN is visible", business_plan_visible)
    report.add_validation("Button Cambiar Plan is visible", change_plan_visible)

    body_text = page.locator("body").inner_text(timeout=5000)
    possible_name_lines = [
        line.strip()
        for line in body_text.splitlines()
        if line.strip()
        and "@" not in line
        and line.strip().lower() not in {"informacion general", "informaci\u00f3n general", "business plan", "cambiar plan"}
        and len(line.strip()) >= 3
    ]
    report.add_validation("User name is visible", len(possible_name_lines) > 0)
    report.finalize()


def validate_detalles(page: Page, reports: Dict[str, StepReport]) -> None:
    report = reports["Detalles de la Cuenta"]
    report.add_validation("Cuenta creada is visible", text_visible(page, "Cuenta creada"))
    report.add_validation("Estado activo is visible", text_visible(page, "Estado activo"))
    report.add_validation("Idioma seleccionado is visible", text_visible(page, "Idioma seleccionado"))
    report.finalize()


def validate_tus_negocios(page: Page, reports: Dict[str, StepReport]) -> None:
    report = reports["Tus Negocios"]
    report.add_validation("Business list is visible", text_visible(page, "Tus Negocios"))
    report.add_validation("Agregar Negocio button exists", text_visible(page, "Agregar Negocio"))
    report.add_validation("Text Tienes 2 de 3 negocios is visible", text_visible(page, "Tienes 2 de 3 negocios"))
    report.finalize()


def open_legal_link(
    app_page: Page,
    context: BrowserContext,
    report_name: str,
    click_labels: Sequence[str],
    expected_headings: Sequence[str],
    report: StepReport,
    artifacts_dir: Path,
    screenshot_name: str,
) -> Page:
    existing_pages = set(context.pages)
    previous_url = app_page.url
    clicked = click_first_visible(app_page, click_labels)
    report.add_validation(f"{report_name} link clicked", clicked)
    if not clicked:
        return app_page

    target_page = app_page
    deadline = time.time() + 8
    while time.time() < deadline:
        new_pages = [p for p in context.pages if p not in existing_pages]
        if new_pages:
            target_page = new_pages[-1]
            break
        time.sleep(0.25)

    wait_ui(target_page)

    heading_ok = any(text_visible(target_page, heading, timeout_ms=12000) for heading in expected_headings)
    legal_text = target_page.locator("body").inner_text(timeout=10000)
    legal_content_ok = len(re.sub(r"\s+", " ", legal_text).strip()) > 120

    report.add_validation(f"Heading for '{report_name}' is visible", heading_ok)
    report.add_validation("Legal content text is visible", legal_content_ok)
    report.evidence.append(screenshot(target_page, artifacts_dir, screenshot_name, full_page=True))
    report.notes.append(f"Final URL: {target_page.url}")

    if target_page is not app_page:
        target_page.close()
        wait_ui(app_page, ms=800)
        return app_page

    if target_page.url != previous_url:
        try:
            target_page.go_back(wait_until="domcontentloaded", timeout=8000)
            wait_ui(target_page, ms=800)
        except TimeoutError:
            pass
    return target_page


def validate_terms_and_privacy(page: Page, context: BrowserContext, reports: Dict[str, StepReport], artifacts_dir: Path) -> None:
    terms_report = reports["T\u00e9rminos y Condiciones"]
    page = open_legal_link(
        app_page=page,
        context=context,
        report_name="T\u00e9rminos y Condiciones",
        click_labels=("Terminos y Condiciones", "T\u00e9rminos y Condiciones"),
        expected_headings=("Terminos y Condiciones", "T\u00e9rminos y Condiciones"),
        report=terms_report,
        artifacts_dir=artifacts_dir,
        screenshot_name="step8_terminos_y_condiciones",
    )
    terms_report.finalize()

    privacy_report = reports["Pol\u00edtica de Privacidad"]
    page = open_legal_link(
        app_page=page,
        context=context,
        report_name="Pol\u00edtica de Privacidad",
        click_labels=("Politica de Privacidad", "Pol\u00edtica de Privacidad"),
        expected_headings=("Politica de Privacidad", "Pol\u00edtica de Privacidad"),
        report=privacy_report,
        artifacts_dir=artifacts_dir,
        screenshot_name="step9_politica_de_privacidad",
    )
    privacy_report.finalize()


def write_report(reports: Dict[str, StepReport], artifacts_dir: Path) -> Path:
    report_path = artifacts_dir / "final_report.json"
    payload = {
        "name": "saleads_mi_negocio_full_test",
        "executed_at_utc": datetime.now(timezone.utc).isoformat(),
        "results": {
            k: {
                "status": v.status,
                "validations": [{"name": label, "passed": passed} for label, passed in v.validations],
                "evidence": v.evidence,
                "notes": v.notes,
            }
            for k, v in reports.items()
        },
        "summary": {k: v.status for k, v in reports.items()},
        "overall_status": "PASS" if all(v.passed for v in reports.values()) else "FAIL",
    }
    report_path.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    return report_path


def print_console_summary(reports: Dict[str, StepReport], report_path: Path) -> None:
    print("\n=== SaleADS Mi Negocio Final Report ===")
    for field in REPORT_FIELDS:
        status = reports[field].status
        print(f"- {field}: {status}")
    print(f"- Report JSON: {report_path}")
    print(f"- Overall: {'PASS' if all(v.passed for v in reports.values()) else 'FAIL'}")


def create_browser_and_page(playwright) -> Tuple[Browser, BrowserContext, Page]:
    cdp_url = os.getenv("CHROME_CDP_URL", "").strip()
    saleads_url = os.getenv("SALEADS_URL", "").strip()
    headless = os.getenv("HEADLESS", "false").strip().lower() in {"1", "true", "yes"}

    if cdp_url:
        browser = playwright.chromium.connect_over_cdp(cdp_url)
        context = browser.contexts[0] if browser.contexts else browser.new_context()
        page = context.pages[-1] if context.pages else context.new_page()
        return browser, context, page

    browser = playwright.chromium.launch(headless=headless)
    context = browser.new_context()
    page = context.new_page()

    if saleads_url:
        page.goto(saleads_url, wait_until="domcontentloaded", timeout=45000)
        wait_ui(page, ms=1500)
    else:
        raise RuntimeError(
            "No start point found. Set SALEADS_URL, or set CHROME_CDP_URL to attach to an existing logged-in browser tab."
        )

    return browser, context, page


def run() -> int:
    artifacts_dir = make_artifacts_dir()
    reports = collect_report_field_defaults()
    if PLAYWRIGHT_IMPORT_ERROR is not None:
        install_hint = "Playwright is not installed. Install with: pip3 install playwright && python3 -m playwright install chromium"
        for field in REPORT_FIELDS:
            reports[field].notes.append(install_hint)
            reports[field].status = "FAIL"
        report_path = write_report(reports, artifacts_dir)
        print_console_summary(reports, report_path)
        return 1

    try:
        with sync_playwright() as p:
            browser, context, page = create_browser_and_page(p)
            try:
                validate_login(page, context, reports, artifacts_dir)
                validate_mi_negocio_menu(page, reports, artifacts_dir)
                validate_agregar_modal(page, reports, artifacts_dir)
                validate_administrar_view(page, reports, artifacts_dir)
                validate_info_general(page, reports)
                validate_detalles(page, reports)
                validate_tus_negocios(page, reports)
                validate_terms_and_privacy(page, context, reports, artifacts_dir)
            finally:
                browser.close()

    except Exception as exc:  # pylint: disable=broad-except
        for field in REPORT_FIELDS:
            if not reports[field].validations:
                reports[field].notes.append(f"Execution error before step could run: {exc}")
                reports[field].status = "FAIL"

    report_path = write_report(reports, artifacts_dir)
    print_console_summary(reports, report_path)
    return 0 if all(v.passed for v in reports.values()) else 1


if __name__ == "__main__":
    sys.exit(run())
