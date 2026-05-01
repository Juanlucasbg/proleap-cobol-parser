#!/usr/bin/env python3
"""End-to-end validation for the SaleADS "Mi Negocio" workflow.

This script is environment-agnostic by design. It never hardcodes a SaleADS
domain and instead receives the login URL at runtime.
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
from typing import Any, Iterable

from playwright.sync_api import BrowserContext, Locator, Page, TimeoutError, sync_playwright

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
class CheckResult:
    name: str
    passed: bool
    detail: str


@dataclass
class FieldResult:
    name: str
    checks: list[CheckResult] = field(default_factory=list)
    artifacts: list[str] = field(default_factory=list)
    metadata: dict[str, Any] = field(default_factory=dict)

    @property
    def status(self) -> str:
        return "PASS" if self.checks and all(check.passed for check in self.checks) else "FAIL"


@dataclass
class WorkflowReport:
    fields: dict[str, FieldResult] = field(
        default_factory=lambda: {name: FieldResult(name=name) for name in REPORT_FIELDS}
    )

    def add_check(self, field_name: str, check_name: str, passed: bool, detail: str) -> None:
        self.fields[field_name].checks.append(CheckResult(name=check_name, passed=passed, detail=detail))

    def add_artifact(self, field_name: str, path: Path) -> None:
        self.fields[field_name].artifacts.append(str(path))

    def add_metadata(self, field_name: str, key: str, value: Any) -> None:
        self.fields[field_name].metadata[key] = value

    @property
    def overall_status(self) -> str:
        return "PASS" if all(field.status == "PASS" for field in self.fields.values()) else "FAIL"

    def as_dict(self) -> dict[str, Any]:
        return {
            "overall_status": self.overall_status,
            "generated_at_utc": datetime.now(timezone.utc).isoformat(),
            "results": {
                name: {
                    "status": field.status,
                    "checks": [
                        {"name": check.name, "passed": check.passed, "detail": check.detail}
                        for check in field.checks
                    ],
                    "artifacts": field.artifacts,
                    "metadata": field.metadata,
                }
                for name, field in self.fields.items()
            },
        }


def wait_for_ui_load(page: Page, timeout_ms: int) -> None:
    for state in ("domcontentloaded", "networkidle"):
        try:
            page.wait_for_load_state(state, timeout=timeout_ms)
        except Exception:
            pass
    page.wait_for_timeout(500)


def is_visible(locator: Locator, timeout_ms: int = 2500) -> bool:
    try:
        return locator.first.is_visible(timeout=timeout_ms)
    except Exception:
        return False


def text_locators(page: Page, text: str) -> list[Locator]:
    exact = re.compile(rf"^\s*{re.escape(text)}\s*$", re.IGNORECASE)
    fuzzy = re.compile(re.escape(text), re.IGNORECASE)
    return [
        page.get_by_role("button", name=exact),
        page.get_by_role("button", name=fuzzy),
        page.get_by_role("link", name=exact),
        page.get_by_role("link", name=fuzzy),
        page.get_by_role("menuitem", name=exact),
        page.get_by_role("menuitem", name=fuzzy),
        page.get_by_role("tab", name=exact),
        page.get_by_role("heading", name=exact),
        page.get_by_text(exact),
        page.get_by_text(fuzzy),
    ]


def click_by_visible_text(
    page: Page, labels: Iterable[str], timeout_ms: int, wait_after: bool = True
) -> tuple[bool, str]:
    labels_list = list(labels)
    if not labels_list:
        return False, "No labels were provided."

    deadline = datetime.now().timestamp() + (timeout_ms / 1000.0)
    last_error = "No visible matching element."
    while datetime.now().timestamp() < deadline:
        for label in labels_list:
            for locator in text_locators(page, label):
                if is_visible(locator, timeout_ms=700):
                    try:
                        locator.first.click(timeout=timeout_ms)
                        if wait_after:
                            wait_for_ui_load(page, timeout_ms)
                        return True, f"Clicked element matching text '{label}'."
                    except Exception as exc:
                        last_error = f"Found '{label}', but click failed: {exc}"
        page.wait_for_timeout(250)

    return False, last_error


def text_visible(page: Page, text: str, timeout_ms: int) -> bool:
    for locator in text_locators(page, text):
        if is_visible(locator, timeout_ms=timeout_ms):
            return True
    return False


def take_screenshot(
    page: Page,
    file_name: str,
    screenshot_dir: Path,
    report: WorkflowReport,
    field_name: str,
    full_page: bool = False,
) -> None:
    output_path = screenshot_dir / file_name
    output_path.parent.mkdir(parents=True, exist_ok=True)
    page.screenshot(path=str(output_path), full_page=full_page)
    report.add_artifact(field_name, output_path)


def ensure_mi_negocio_menu_expanded(page: Page, timeout_ms: int) -> tuple[bool, str]:
    if text_visible(page, "Agregar Negocio", timeout_ms=1000) and text_visible(
        page, "Administrar Negocios", timeout_ms=1000
    ):
        return True, "Mi Negocio menu is already expanded."

    clicked, detail = click_by_visible_text(page, ["Mi Negocio"], timeout_ms=timeout_ms)
    if not clicked:
        click_by_visible_text(page, ["Negocio"], timeout_ms=timeout_ms)
        clicked, detail = click_by_visible_text(page, ["Mi Negocio"], timeout_ms=timeout_ms)
        if not clicked:
            return False, f"Could not expand 'Mi Negocio': {detail}"

    expanded = text_visible(page, "Agregar Negocio", timeout_ms=3000) and text_visible(
        page, "Administrar Negocios", timeout_ms=3000
    )
    if expanded:
        return True, "Mi Negocio menu expanded successfully."
    return False, "Clicked Mi Negocio but submenu options were not visible."


def any_visible(page: Page, locators: list[Locator], timeout_ms: int = 2500) -> bool:
    return any(is_visible(locator, timeout_ms=timeout_ms) for locator in locators)


def validate_main_app_and_sidebar(page: Page, timeout_ms: int) -> tuple[bool, bool]:
    main_visible = any_visible(
        page,
        [
            page.get_by_role("main"),
            page.locator("main"),
            page.get_by_text(re.compile("Dashboard|Panel|Inicio", re.IGNORECASE)),
            page.get_by_text("Negocio"),
        ],
        timeout_ms=timeout_ms,
    )

    sidebar_visible = any_visible(
        page,
        [
            page.locator("aside"),
            page.get_by_role("navigation"),
            page.locator("[class*='sidebar']"),
            page.locator("[class*='sidenav']"),
        ],
        timeout_ms=timeout_ms,
    )
    return main_visible, sidebar_visible


def maybe_select_google_account(
    page: Page, google_email: str, timeout_ms: int
) -> tuple[bool, str]:
    # If the account chooser appears, click the requested account.
    candidates = [
        page.get_by_text(google_email, exact=True),
        page.get_by_role("link", name=re.compile(re.escape(google_email), re.IGNORECASE)),
        page.get_by_role("button", name=re.compile(re.escape(google_email), re.IGNORECASE)),
    ]

    for locator in candidates:
        if is_visible(locator, timeout_ms=4500):
            try:
                locator.first.click(timeout=timeout_ms)
                wait_for_ui_load(page, timeout_ms)
                return True, f"Selected Google account '{google_email}'."
            except Exception as exc:
                return False, f"Google account was visible but click failed: {exc}"

    return True, "Google account selector not shown; continued without explicit account selection."


def validate_nonempty_legal_content(page: Page) -> bool:
    try:
        body_text = page.locator("body").inner_text(timeout=4000)
    except Exception:
        return False

    condensed = re.sub(r"\s+", " ", body_text).strip()
    return len(condensed) >= 120


def open_legal_link_and_validate(
    app_page: Page,
    context: BrowserContext,
    click_label: str,
    heading_text: str,
    screenshot_name: str,
    screenshot_dir: Path,
    report: WorkflowReport,
    field_name: str,
    timeout_ms: int,
) -> None:
    legal_page: Page | None = None
    opened_new_tab = False

    try:
        with context.expect_page(timeout=5000) as new_page_event:
            clicked, detail = click_by_visible_text(
                app_page, [click_label], timeout_ms=timeout_ms, wait_after=False
            )
            report.add_check(
                field_name,
                f"Click '{click_label}'",
                clicked,
                detail if clicked else f"Could not click legal link '{click_label}'. {detail}",
            )
            if not clicked:
                return
        legal_page = new_page_event.value
        opened_new_tab = True
    except TimeoutError:
        clicked, detail = click_by_visible_text(app_page, [click_label], timeout_ms=timeout_ms)
        report.add_check(field_name, f"Click '{click_label}'", clicked, detail)
        if not clicked:
            return
        legal_page = app_page
    except Exception as exc:
        report.add_check(field_name, f"Click '{click_label}'", False, f"Unexpected click error: {exc}")
        return

    wait_for_ui_load(legal_page, timeout_ms=timeout_ms)

    heading_visible = text_visible(legal_page, heading_text, timeout_ms=timeout_ms)
    report.add_check(
        field_name,
        f"Heading '{heading_text}' visible",
        heading_visible,
        f"Heading check on URL: {legal_page.url}",
    )

    content_visible = validate_nonempty_legal_content(legal_page)
    report.add_check(
        field_name,
        "Legal content text visible",
        content_visible,
        "Page body contains legal text content." if content_visible else "Page body text looked too short.",
    )

    try:
        take_screenshot(legal_page, screenshot_name, screenshot_dir, report, field_name, full_page=True)
    except Exception as exc:
        report.add_check(field_name, "Screenshot captured", False, f"Screenshot failed: {exc}")
    else:
        report.add_check(field_name, "Screenshot captured", True, "Screenshot stored successfully.")

    report.add_metadata(field_name, "final_url", legal_page.url)
    report.add_metadata(field_name, "opened_new_tab", opened_new_tab)

    # Cleanup: always return to application tab.
    if opened_new_tab:
        try:
            legal_page.close()
        except Exception:
            pass
        app_page.bring_to_front()
        wait_for_ui_load(app_page, timeout_ms=timeout_ms)
    else:
        try:
            app_page.go_back(timeout=timeout_ms)
            wait_for_ui_load(app_page, timeout_ms=timeout_ms)
        except Exception:
            pass


def run_test(args: argparse.Namespace) -> tuple[int, WorkflowReport, Path]:
    report = WorkflowReport()
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    run_dir = Path(args.artifacts_dir) / "saleads_mi_negocio_full_test" / timestamp
    screenshots_dir = run_dir / "screenshots"
    run_dir.mkdir(parents=True, exist_ok=True)

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=args.headless, slow_mo=args.slow_mo_ms)
        context = browser.new_context(viewport={"width": 1440, "height": 900})
        page = context.new_page()

        if args.base_url:
            page.goto(args.base_url, wait_until="domcontentloaded", timeout=args.timeout_ms)
            wait_for_ui_load(page, args.timeout_ms)

        # Step 1: Login with Google
        login_clicked = False
        popup_page: Page | None = None
        login_labels = [
            "Sign in with Google",
            "Iniciar sesión con Google",
            "Login with Google",
            "Continuar con Google",
            "Google",
        ]

        try:
            with page.expect_popup(timeout=5000) as popup_event:
                login_clicked, detail = click_by_visible_text(
                    page, login_labels, timeout_ms=args.timeout_ms, wait_after=False
                )
                report.add_check("Login", "Click login button", login_clicked, detail)
                if not login_clicked:
                    popup_page = None
            if login_clicked:
                popup_page = popup_event.value
        except TimeoutError:
            login_clicked, detail = click_by_visible_text(page, login_labels, timeout_ms=args.timeout_ms)
            report.add_check("Login", "Click login button", login_clicked, detail)
        except Exception as exc:
            report.add_check("Login", "Click login button", False, f"Unexpected login click error: {exc}")

        if login_clicked:
            google_surface = popup_page if popup_page is not None else page
            selected, detail = maybe_select_google_account(
                google_surface, args.google_email, timeout_ms=args.timeout_ms
            )
            report.add_check("Login", "Choose Google account if selector appears", selected, detail)

            if popup_page is not None:
                # Wait for app page to update after auth flow from popup.
                popup_page.wait_for_timeout(1000)
                wait_for_ui_load(page, args.timeout_ms)
                if not popup_page.is_closed():
                    try:
                        popup_page.close()
                    except Exception:
                        pass

        main_visible, sidebar_visible = validate_main_app_and_sidebar(page, args.timeout_ms)
        report.add_check(
            "Login",
            "Main application interface appears",
            main_visible,
            "Main app containers are visible." if main_visible else "Main app containers not detected.",
        )
        report.add_check(
            "Login",
            "Left sidebar navigation is visible",
            sidebar_visible,
            "Sidebar/navigation was detected." if sidebar_visible else "Sidebar/navigation not detected.",
        )

        try:
            take_screenshot(
                page,
                "01_dashboard_loaded.png",
                screenshots_dir,
                report,
                "Login",
                full_page=True,
            )
            report.add_check("Login", "Dashboard screenshot captured", True, "Screenshot stored successfully.")
        except Exception as exc:
            report.add_check("Login", "Dashboard screenshot captured", False, f"Screenshot failed: {exc}")

        # Step 2: Open Mi Negocio menu
        menu_expanded, detail = ensure_mi_negocio_menu_expanded(page, timeout_ms=args.timeout_ms)
        report.add_check("Mi Negocio menu", "Submenu expands", menu_expanded, detail)

        add_visible = text_visible(page, "Agregar Negocio", timeout_ms=args.timeout_ms)
        report.add_check(
            "Mi Negocio menu",
            "'Agregar Negocio' visible",
            add_visible,
            "Found submenu option 'Agregar Negocio'." if add_visible else "Did not find 'Agregar Negocio'.",
        )

        manage_visible = text_visible(page, "Administrar Negocios", timeout_ms=args.timeout_ms)
        report.add_check(
            "Mi Negocio menu",
            "'Administrar Negocios' visible",
            manage_visible,
            "Found submenu option 'Administrar Negocios'."
            if manage_visible
            else "Did not find 'Administrar Negocios'.",
        )

        try:
            take_screenshot(
                page,
                "02_mi_negocio_menu_expanded.png",
                screenshots_dir,
                report,
                "Mi Negocio menu",
            )
            report.add_check("Mi Negocio menu", "Expanded menu screenshot captured", True, "Screenshot stored.")
        except Exception as exc:
            report.add_check("Mi Negocio menu", "Expanded menu screenshot captured", False, f"{exc}")

        # Step 3: Validate Agregar Negocio modal
        clicked, detail = click_by_visible_text(page, ["Agregar Negocio"], timeout_ms=args.timeout_ms)
        report.add_check("Agregar Negocio modal", "Click 'Agregar Negocio'", clicked, detail)
        wait_for_ui_load(page, args.timeout_ms)

        modal_title_visible = text_visible(page, "Crear Nuevo Negocio", timeout_ms=args.timeout_ms)
        report.add_check(
            "Agregar Negocio modal",
            "Modal title 'Crear Nuevo Negocio' visible",
            modal_title_visible,
            "Modal title was visible." if modal_title_visible else "Modal title not visible.",
        )

        name_field = any_visible(
            page,
            [
                page.get_by_label("Nombre del Negocio"),
                page.get_by_placeholder("Nombre del Negocio"),
                page.get_by_role("textbox", name=re.compile("Nombre del Negocio", re.IGNORECASE)),
            ],
            timeout_ms=args.timeout_ms,
        )
        report.add_check(
            "Agregar Negocio modal",
            "Input field 'Nombre del Negocio' exists",
            name_field,
            "Business name field found." if name_field else "Business name field not found.",
        )

        quota_visible = text_visible(page, "Tienes 2 de 3 negocios", timeout_ms=args.timeout_ms)
        report.add_check(
            "Agregar Negocio modal",
            "Text 'Tienes 2 de 3 negocios' visible",
            quota_visible,
            "Quota text visible." if quota_visible else "Quota text not visible.",
        )

        cancel_visible = text_visible(page, "Cancelar", timeout_ms=args.timeout_ms)
        create_visible = text_visible(page, "Crear Negocio", timeout_ms=args.timeout_ms)
        report.add_check(
            "Agregar Negocio modal",
            "Button 'Cancelar' present",
            cancel_visible,
            "'Cancelar' visible." if cancel_visible else "'Cancelar' not visible.",
        )
        report.add_check(
            "Agregar Negocio modal",
            "Button 'Crear Negocio' present",
            create_visible,
            "'Crear Negocio' visible." if create_visible else "'Crear Negocio' not visible.",
        )

        try:
            take_screenshot(
                page,
                "03_agregar_negocio_modal.png",
                screenshots_dir,
                report,
                "Agregar Negocio modal",
            )
            report.add_check("Agregar Negocio modal", "Modal screenshot captured", True, "Screenshot stored.")
        except Exception as exc:
            report.add_check("Agregar Negocio modal", "Modal screenshot captured", False, f"{exc}")

        # Optional modal actions
        if name_field:
            for locator in [
                page.get_by_label("Nombre del Negocio"),
                page.get_by_placeholder("Nombre del Negocio"),
                page.get_by_role("textbox", name=re.compile("Nombre del Negocio", re.IGNORECASE)),
            ]:
                if is_visible(locator, timeout_ms=800):
                    try:
                        locator.first.click(timeout=args.timeout_ms)
                        wait_for_ui_load(page, args.timeout_ms)
                        locator.first.fill("Negocio Prueba Automatización", timeout=args.timeout_ms)
                        break
                    except Exception:
                        continue

        click_by_visible_text(page, ["Cancelar"], timeout_ms=args.timeout_ms)
        wait_for_ui_load(page, args.timeout_ms)

        # Step 4: Open Administrar Negocios
        ensure_mi_negocio_menu_expanded(page, timeout_ms=args.timeout_ms)
        clicked, detail = click_by_visible_text(
            page, ["Administrar Negocios"], timeout_ms=args.timeout_ms
        )
        report.add_check("Administrar Negocios view", "Click 'Administrar Negocios'", clicked, detail)
        wait_for_ui_load(page, args.timeout_ms)

        for section_name in [
            "Información General",
            "Detalles de la Cuenta",
            "Tus Negocios",
            "Sección Legal",
        ]:
            visible = text_visible(page, section_name, timeout_ms=args.timeout_ms)
            report.add_check(
                "Administrar Negocios view",
                f"Section '{section_name}' exists",
                visible,
                f"Section '{section_name}' is visible." if visible else f"Section '{section_name}' missing.",
            )

        try:
            take_screenshot(
                page,
                "04_administrar_negocios_view_full.png",
                screenshots_dir,
                report,
                "Administrar Negocios view",
                full_page=True,
            )
            report.add_check(
                "Administrar Negocios view", "Account page screenshot captured", True, "Screenshot stored."
            )
        except Exception as exc:
            report.add_check("Administrar Negocios view", "Account page screenshot captured", False, f"{exc}")

        # Step 5: Validate Información General
        email_visible = is_visible(
            page.get_by_text(re.compile(r"[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}")),
            timeout_ms=args.timeout_ms,
        )
        report.add_check(
            "Información General",
            "User email is visible",
            email_visible,
            "Detected at least one email address on page." if email_visible else "No visible email address found.",
        )

        name_visible = any_visible(
            page,
            [
                page.get_by_role(
                    "heading",
                    name=re.compile(r"[A-Za-zÁÉÍÓÚÑáéíóúñ'\-]+\s+[A-Za-zÁÉÍÓÚÑáéíóúñ'\-]+"),
                ),
                page.get_by_text(
                    re.compile(r"^[A-Za-zÁÉÍÓÚÑáéíóúñ'\-]+\s+[A-Za-zÁÉÍÓÚÑáéíóúñ'\-]+$"),
                ),
            ],
            timeout_ms=args.timeout_ms,
        )
        report.add_check(
            "Información General",
            "User name is visible",
            name_visible,
            "Name-like text is visible in profile view."
            if name_visible
            else "Could not confidently detect a visible user name.",
        )

        business_plan_visible = text_visible(page, "BUSINESS PLAN", timeout_ms=args.timeout_ms)
        report.add_check(
            "Información General",
            "Text 'BUSINESS PLAN' is visible",
            business_plan_visible,
            "'BUSINESS PLAN' visible." if business_plan_visible else "'BUSINESS PLAN' not visible.",
        )

        change_plan_visible = text_visible(page, "Cambiar Plan", timeout_ms=args.timeout_ms)
        report.add_check(
            "Información General",
            "Button 'Cambiar Plan' is visible",
            change_plan_visible,
            "'Cambiar Plan' visible." if change_plan_visible else "'Cambiar Plan' not visible.",
        )

        # Step 6: Validate Detalles de la Cuenta
        for label in ["Cuenta creada", "Estado activo", "Idioma seleccionado"]:
            visible = text_visible(page, label, timeout_ms=args.timeout_ms)
            report.add_check(
                "Detalles de la Cuenta",
                f"'{label}' is visible",
                visible,
                f"'{label}' was found." if visible else f"'{label}' was not found.",
            )

        # Step 7: Validate Tus Negocios
        list_visible = any_visible(
            page,
            [
                page.locator("ul li"),
                page.locator("table tbody tr"),
                page.locator("[class*='business']"),
                page.locator("[class*='negocio']"),
            ],
            timeout_ms=args.timeout_ms,
        )
        report.add_check(
            "Tus Negocios",
            "Business list is visible",
            list_visible,
            "Detected list/table/card-like business container."
            if list_visible
            else "No list/table/card business container detected.",
        )

        add_business_visible = text_visible(page, "Agregar Negocio", timeout_ms=args.timeout_ms)
        report.add_check(
            "Tus Negocios",
            "Button 'Agregar Negocio' exists",
            add_business_visible,
            "'Agregar Negocio' visible." if add_business_visible else "'Agregar Negocio' not visible.",
        )

        quota_visible = text_visible(page, "Tienes 2 de 3 negocios", timeout_ms=args.timeout_ms)
        report.add_check(
            "Tus Negocios",
            "Text 'Tienes 2 de 3 negocios' is visible",
            quota_visible,
            "Business quota text visible." if quota_visible else "Business quota text not visible.",
        )

        # Step 8: Validate Términos y Condiciones
        open_legal_link_and_validate(
            app_page=page,
            context=context,
            click_label="Términos y Condiciones",
            heading_text="Términos y Condiciones",
            screenshot_name="08_terminos_y_condiciones.png",
            screenshot_dir=screenshots_dir,
            report=report,
            field_name="Términos y Condiciones",
            timeout_ms=args.timeout_ms,
        )

        # Step 9: Validate Política de Privacidad
        open_legal_link_and_validate(
            app_page=page,
            context=context,
            click_label="Política de Privacidad",
            heading_text="Política de Privacidad",
            screenshot_name="09_politica_de_privacidad.png",
            screenshot_dir=screenshots_dir,
            report=report,
            field_name="Política de Privacidad",
            timeout_ms=args.timeout_ms,
        )

        browser.close()

    return (0 if report.overall_status == "PASS" else 1), report, run_dir


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run the SaleADS Mi Negocio full workflow validation."
    )
    parser.add_argument(
        "--base-url",
        default=os.getenv("SALEADS_BASE_URL"),
        help="Environment login URL (e.g., current dev/staging/prod login page).",
    )
    parser.add_argument(
        "--google-email",
        default="juanlucasbarbiergarzon@gmail.com",
        help="Google account email to choose if account selector appears.",
    )
    parser.add_argument(
        "--timeout-ms",
        type=int,
        default=int(os.getenv("SALEADS_TIMEOUT_MS", "20000")),
        help="Default timeout in milliseconds for page actions.",
    )
    parser.add_argument(
        "--artifacts-dir",
        default=os.getenv("SALEADS_ARTIFACTS_DIR", "e2e/artifacts"),
        help="Directory where screenshots and final reports are stored.",
    )
    parser.add_argument(
        "--headed",
        action="store_true",
        help="Run browser in headed mode.",
    )
    parser.add_argument(
        "--slow-mo-ms",
        type=int,
        default=int(os.getenv("SALEADS_SLOW_MO_MS", "0")),
        help="Add delay between Playwright actions (milliseconds).",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    args.headless = not args.headed

    if not args.base_url:
        print(
            "Missing base URL. Provide --base-url or SALEADS_BASE_URL so the script can open "
            "the login page in the current environment.",
            file=sys.stderr,
        )
        return 2

    exit_code, report, run_dir = run_test(args)
    report_path = run_dir / "final_report.json"
    report_data = report.as_dict()
    report_path.write_text(json.dumps(report_data, indent=2, ensure_ascii=False), encoding="utf-8")

    print("saleads_mi_negocio_full_test result")
    print(f"Overall: {report.overall_status}")
    for field_name in REPORT_FIELDS:
        field_status = report.fields[field_name].status
        print(f"- {field_name}: {field_status}")
    print(f"Artifacts directory: {run_dir}")
    print(f"Report file: {report_path}")

    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
