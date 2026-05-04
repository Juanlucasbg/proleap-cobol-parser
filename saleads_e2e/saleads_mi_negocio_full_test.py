#!/usr/bin/env python3
"""SaleADS Mi Negocio full workflow test.

This script is environment-agnostic and does not hardcode any SaleADS domain.
It can either:
1) connect to an already running Chromium instance via CDP, or
2) launch a fresh browser and open a URL provided at runtime.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import traceback
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable

from playwright.sync_api import Browser, BrowserContext, Locator, Page, TimeoutError, sync_playwright


SCREENSHOT_VIEWPORT = {"width": 1600, "height": 1200}
DEFAULT_TIMEOUT_MS = 15000
EMAIL_TO_SELECT = "juanlucasbarbiergarzon@gmail.com"
REPORT_FIELDS: list[tuple[str, str]] = [
    ("Login", "login"),
    ("Mi Negocio menu", "mi_negocio_menu"),
    ("Agregar Negocio modal", "agregar_negocio_modal"),
    ("Administrar Negocios view", "administrar_negocios_view"),
    ("Informacion General", "informacion_general"),
    ("Detalles de la Cuenta", "detalles_cuenta"),
    ("Tus Negocios", "tus_negocios"),
    ("Terminos y Condiciones", "terminos_condiciones"),
    ("Politica de Privacidad", "politica_privacidad"),
]


@dataclass
class StepResult:
    name: str
    status: str
    details: str = ""


class WorkflowRecorder:
    def __init__(self, artifacts_dir: Path) -> None:
        self.artifacts_dir = artifacts_dir
        self.timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        self.screenshots_dir = artifacts_dir / f"screenshots_{self.timestamp}"
        self.screenshots_dir.mkdir(parents=True, exist_ok=True)
        self.results: dict[str, StepResult] = {}
        self.evidence: dict[str, str] = {}

    def mark_pass(self, key: str, name: str, details: str = "") -> None:
        self.results[key] = StepResult(name=name, status="PASS", details=details)

    def mark_fail(self, key: str, name: str, error: Exception) -> None:
        self.results[key] = StepResult(name=name, status="FAIL", details=str(error))

    def capture(self, page: Page, label: str, full_page: bool = False) -> str:
        sanitized = re.sub(r"[^a-zA-Z0-9_-]+", "_", label).strip("_").lower()
        path = self.screenshots_dir / f"{sanitized}.png"
        page.screenshot(path=str(path), full_page=full_page)
        self.evidence[label] = str(path)
        return str(path)

    def write_report(self) -> Path:
        field_summary = []
        for report_name, key in REPORT_FIELDS:
            result = self.results.get(key)
            field_summary.append(
                {
                    "field": report_name,
                    "status": result.status if result else "FAIL",
                    "details": result.details if result else "Step did not execute.",
                }
            )

        overall_status = "PASS" if all(item["status"] == "PASS" for item in field_summary) else "FAIL"
        report = {
            "name": "saleads_mi_negocio_full_test",
            "generated_at_utc": datetime.now(timezone.utc).isoformat(),
            "overall_status": overall_status,
            "final_report": field_summary,
            "results": {
                key: {
                    "name": result.name,
                    "status": result.status,
                    "details": result.details,
                }
                for key, result in self.results.items()
            },
            "evidence": self.evidence,
        }
        path = self.artifacts_dir / f"report_{self.timestamp}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
        return path


def wait_for_ui(page: Page) -> None:
    # We use both states because some SaleADS pages rely on async components.
    page.wait_for_load_state("domcontentloaded", timeout=DEFAULT_TIMEOUT_MS)
    try:
        page.wait_for_load_state("networkidle", timeout=DEFAULT_TIMEOUT_MS)
    except TimeoutError:
        # Not all SPA routes become network-idle quickly; DOM loaded is enough as fallback.
        pass


def click_and_wait(locator, page: Page) -> None:
    locator.wait_for(state="visible", timeout=DEFAULT_TIMEOUT_MS)
    locator.click()
    wait_for_ui(page)


def first_visible(page: Page, locators: list[Callable[[], Locator]]) -> Locator:
    last_error: Exception | None = None
    for locator_factory in locators:
        locator = locator_factory()
        try:
            locator.first.wait_for(state="visible", timeout=2500)
            return locator.first
        except Exception as exc:  # noqa: BLE001
            last_error = exc
    raise RuntimeError(f"Could not find a visible locator. Last error: {last_error}")


def validate_visible_text(page: Page, text: str) -> None:
    page.get_by_text(text, exact=False).first.wait_for(state="visible", timeout=DEFAULT_TIMEOUT_MS)


def validate_visible_pattern(page: Page, pattern: str) -> None:
    page.get_by_text(re.compile(pattern, re.I)).first.wait_for(state="visible", timeout=DEFAULT_TIMEOUT_MS)


def validate_sidebar_visible(page: Page) -> None:
    sidebar = first_visible(
        page,
        [
            lambda: page.get_by_role("navigation"),
            lambda: page.locator("aside"),
            lambda: page.locator("[data-testid*='sidebar'], [class*='sidebar']"),
        ],
    )
    sidebar.wait_for(state="visible", timeout=DEFAULT_TIMEOUT_MS)


def run_login_step(page: Page, recorder: WorkflowRecorder) -> None:
    step_key = "login"
    step_name = "Login"
    try:
        login_button = first_visible(
            page,
            [
                lambda: page.get_by_role("button", name=re.compile(r"sign in with google|google|iniciar", re.I)),
                lambda: page.get_by_text(re.compile(r"sign in with google|google|iniciar", re.I)),
            ],
        )
        click_and_wait(login_button, page)

        # Optional Google account selector handling.
        account_option = page.get_by_text(EMAIL_TO_SELECT, exact=False).first
        try:
            account_option.wait_for(state="visible", timeout=5000)
            click_and_wait(account_option, page)
        except TimeoutError:
            pass

        validate_sidebar_visible(page)
        recorder.capture(page, "checkpoint_dashboard_loaded")
        recorder.mark_pass(step_key, step_name, "Main interface and sidebar are visible.")
    except Exception as exc:  # noqa: BLE001
        recorder.capture(page, "error_login_state")
        recorder.mark_fail(step_key, step_name, exc)


def ensure_mi_negocio_expanded(page: Page) -> None:
    negocio = first_visible(
        page,
        [
            lambda: page.get_by_role("button", name=re.compile(r"negocio|mi negocio", re.I)),
            lambda: page.get_by_role("link", name=re.compile(r"negocio|mi negocio", re.I)),
            lambda: page.get_by_text(re.compile(r"^Negocio$|Mi Negocio", re.I)),
        ],
    )
    click_and_wait(negocio, page)

    validate_visible_text(page, "Agregar Negocio")
    validate_visible_text(page, "Administrar Negocios")


def run_mi_negocio_menu_step(page: Page, recorder: WorkflowRecorder) -> None:
    step_key = "mi_negocio_menu"
    step_name = "Mi Negocio menu"
    try:
        ensure_mi_negocio_expanded(page)
        recorder.capture(page, "checkpoint_mi_negocio_expanded")
        recorder.mark_pass(step_key, step_name, "Mi Negocio expanded with expected submenu items.")
    except Exception as exc:  # noqa: BLE001
        recorder.capture(page, "error_mi_negocio_menu")
        recorder.mark_fail(step_key, step_name, exc)


def run_agregar_negocio_modal_step(page: Page, recorder: WorkflowRecorder) -> None:
    step_key = "agregar_negocio_modal"
    step_name = "Agregar Negocio modal"
    try:
        add_business = first_visible(
            page,
            [
                lambda: page.get_by_role("button", name=re.compile(r"agregar negocio", re.I)),
                lambda: page.get_by_role("link", name=re.compile(r"agregar negocio", re.I)),
                lambda: page.get_by_text(re.compile(r"agregar negocio", re.I)),
            ],
        )
        click_and_wait(add_business, page)

        validate_visible_text(page, "Crear Nuevo Negocio")
        name_input = first_visible(
            page,
            [
                lambda: page.get_by_label(re.compile(r"nombre del negocio", re.I)),
                lambda: page.get_by_placeholder(re.compile(r"nombre del negocio", re.I)),
                lambda: page.locator("input[name*='negocio'], input[id*='negocio']"),
            ],
        )
        name_input.wait_for(state="visible", timeout=DEFAULT_TIMEOUT_MS)
        validate_visible_text(page, "Tienes 2 de 3 negocios")
        validate_visible_text(page, "Cancelar")
        validate_visible_text(page, "Crear Negocio")
        recorder.capture(page, "checkpoint_agregar_negocio_modal")

        # Optional interaction from the user request.
        name_input.click()
        name_input.fill("Negocio Prueba Automatización")
        cancel_button = first_visible(
            page,
            [
                lambda: page.get_by_role("button", name=re.compile(r"cancelar", re.I)),
                lambda: page.get_by_text(re.compile(r"cancelar", re.I)),
            ],
        )
        click_and_wait(cancel_button, page)
        recorder.mark_pass(step_key, step_name, "Agregar Negocio modal validated and closed.")
    except Exception as exc:  # noqa: BLE001
        recorder.capture(page, "error_agregar_negocio_modal")
        recorder.mark_fail(step_key, step_name, exc)


def run_administrar_negocios_step(page: Page, recorder: WorkflowRecorder) -> None:
    step_key = "administrar_negocios_view"
    step_name = "Administrar Negocios view"
    try:
        ensure_mi_negocio_expanded(page)
        manage_businesses = first_visible(
            page,
            [
                lambda: page.get_by_role("button", name=re.compile(r"administrar negocios", re.I)),
                lambda: page.get_by_role("link", name=re.compile(r"administrar negocios", re.I)),
                lambda: page.get_by_text(re.compile(r"administrar negocios", re.I)),
            ],
        )
        click_and_wait(manage_businesses, page)

        validate_visible_text(page, "Información General")
        validate_visible_text(page, "Detalles de la Cuenta")
        validate_visible_text(page, "Tus Negocios")
        validate_visible_text(page, "Sección Legal")
        recorder.capture(page, "checkpoint_administrar_negocios_account_page", full_page=True)
        recorder.mark_pass(step_key, step_name, "Account sections are visible.")
    except Exception as exc:  # noqa: BLE001
        recorder.capture(page, "error_administrar_negocios")
        recorder.mark_fail(step_key, step_name, exc)


def run_informacion_general_step(page: Page, recorder: WorkflowRecorder) -> None:
    step_key = "informacion_general"
    step_name = "Información General"
    try:
        # Email visibility is a practical proxy for user account identity.
        page.get_by_text(re.compile(r"@")).first.wait_for(state="visible", timeout=DEFAULT_TIMEOUT_MS)
        validate_visible_text(page, "BUSINESS PLAN")
        validate_visible_text(page, "Cambiar Plan")
        # Name can vary by environment; use a non-email profile text check in this section.
        profile_region = first_visible(
            page,
            [
                lambda: page.locator("section").filter(has_text=re.compile(r"Información General", re.I)).locator("h1, h2, h3, p, span"),
                lambda: page.locator("[class*='profile'], [data-testid*='profile']").locator("h1, h2, h3, p, span"),
            ],
        )
        profile_region.wait_for(state="visible", timeout=DEFAULT_TIMEOUT_MS)
        region_text = profile_region.inner_text(timeout=DEFAULT_TIMEOUT_MS)
        if len(region_text.strip().replace("@", "").replace(".", "")) < 3:
            raise RuntimeError("Could not detect a visible user-name-like text in Informacion General.")
        recorder.mark_pass(step_key, step_name, "Name/email/plan controls validated.")
    except Exception as exc:  # noqa: BLE001
        recorder.capture(page, "error_informacion_general")
        recorder.mark_fail(step_key, step_name, exc)


def run_detalles_cuenta_step(page: Page, recorder: WorkflowRecorder) -> None:
    step_key = "detalles_cuenta"
    step_name = "Detalles de la Cuenta"
    try:
        validate_visible_pattern(page, r"cuenta\s+creada")
        validate_visible_pattern(page, r"estado\s+activo")
        validate_visible_pattern(page, r"idioma\s+seleccionado")
        recorder.mark_pass(step_key, step_name, "Detalles de la Cuenta labels are visible.")
    except Exception as exc:  # noqa: BLE001
        recorder.capture(page, "error_detalles_cuenta")
        recorder.mark_fail(step_key, step_name, exc)


def run_tus_negocios_step(page: Page, recorder: WorkflowRecorder) -> None:
    step_key = "tus_negocios"
    step_name = "Tus Negocios"
    try:
        validate_visible_text(page, "Tus Negocios")
        validate_visible_text(page, "Agregar Negocio")
        validate_visible_text(page, "Tienes 2 de 3 negocios")
        # Business list can be rendered as table/list/cards.
        first_visible(
            page,
            [
                lambda: page.locator("table"),
                lambda: page.locator("ul, ol"),
                lambda: page.locator("[data-testid*='business'], [class*='business']"),
            ],
        )
        recorder.mark_pass(step_key, step_name, "Business list and quota are visible.")
    except Exception as exc:  # noqa: BLE001
        recorder.capture(page, "error_tus_negocios")
        recorder.mark_fail(step_key, step_name, exc)


def validate_legal_page_content(legal_page: Page, heading_text: str) -> None:
    legal_page.get_by_text(heading_text, exact=False).first.wait_for(state="visible", timeout=DEFAULT_TIMEOUT_MS)
    # Loose but practical validation that there is substantive legal text.
    legal_page.locator("main, article, body").first.wait_for(state="visible", timeout=DEFAULT_TIMEOUT_MS)
    body_text = legal_page.locator("body").inner_text(timeout=DEFAULT_TIMEOUT_MS)
    if len(body_text.strip()) < 200:
        raise RuntimeError(f"Legal page for '{heading_text}' seems too short to be valid.")


def open_and_validate_legal_link(
    page: Page,
    context: BrowserContext,
    link_text: str,
    heading_text: str,
    screenshot_label: str,
    recorder: WorkflowRecorder,
) -> str:
    app_url = page.url
    link = first_visible(
        page,
        [
            lambda: page.get_by_role("link", name=re.compile(link_text, re.I)),
            lambda: page.get_by_text(re.compile(link_text, re.I)),
        ],
    )

    new_page: Page | None = None
    link.wait_for(state="visible", timeout=DEFAULT_TIMEOUT_MS)
    try:
        with context.expect_page(timeout=5000) as new_page_event:
            link.click()
        new_page = new_page_event.value
    except TimeoutError:
        # Click already happened; no new page was opened.
        new_page = None
    wait_for_ui(page)

    if new_page is not None:
        wait_for_ui(new_page)
        validate_legal_page_content(new_page, heading_text)
        recorder.capture(new_page, screenshot_label, full_page=True)
        final_url = new_page.url
        new_page.close()
        page.bring_to_front()
        wait_for_ui(page)
        return final_url

    # Same-tab navigation.
    wait_for_ui(page)
    validate_legal_page_content(page, heading_text)
    recorder.capture(page, screenshot_label, full_page=True)
    final_url = page.url

    try:
        page.go_back(timeout=DEFAULT_TIMEOUT_MS)
        wait_for_ui(page)
    except TimeoutError:
        pass
    if page.url == final_url:
        page.goto(app_url, wait_until="domcontentloaded")
        wait_for_ui(page)
    return final_url


def run_terminos_step(page: Page, context: BrowserContext, recorder: WorkflowRecorder) -> None:
    step_key = "terminos_condiciones"
    step_name = "Términos y Condiciones"
    try:
        final_url = open_and_validate_legal_link(
            page=page,
            context=context,
            link_text="Términos y Condiciones",
            heading_text="Términos y Condiciones",
            screenshot_label="checkpoint_terminos_y_condiciones",
            recorder=recorder,
        )
        recorder.evidence["terminos_final_url"] = final_url
        recorder.mark_pass(step_key, step_name, f"Legal page validated at URL: {final_url}")
    except Exception as exc:  # noqa: BLE001
        recorder.capture(page, "error_terminos_y_condiciones")
        recorder.mark_fail(step_key, step_name, exc)


def run_politica_step(page: Page, context: BrowserContext, recorder: WorkflowRecorder) -> None:
    step_key = "politica_privacidad"
    step_name = "Política de Privacidad"
    try:
        final_url = open_and_validate_legal_link(
            page=page,
            context=context,
            link_text="Política de Privacidad",
            heading_text="Política de Privacidad",
            screenshot_label="checkpoint_politica_privacidad",
            recorder=recorder,
        )
        recorder.evidence["politica_final_url"] = final_url
        recorder.mark_pass(step_key, step_name, f"Legal page validated at URL: {final_url}")
    except Exception as exc:  # noqa: BLE001
        recorder.capture(page, "error_politica_privacidad")
        recorder.mark_fail(step_key, step_name, exc)


def resolve_starting_page(args, context: BrowserContext) -> Page:
    if context.pages:
        page = context.pages[-1]
        page.set_default_timeout(DEFAULT_TIMEOUT_MS)
        wait_for_ui(page)
        return page

    page = context.new_page()
    page.set_viewport_size(SCREENSHOT_VIEWPORT)
    page.set_default_timeout(DEFAULT_TIMEOUT_MS)

    if args.saleads_url:
        page.goto(args.saleads_url, wait_until="domcontentloaded")
        wait_for_ui(page)
        return page

    raise RuntimeError(
        "No existing page found. Provide --saleads-url or connect with --cdp-url to a browser already "
        "on the SaleADS login page."
    )


def execute_workflow(args) -> tuple[int, Path]:
    artifacts_dir = Path(args.artifacts_dir).resolve()
    artifacts_dir.mkdir(parents=True, exist_ok=True)
    recorder = WorkflowRecorder(artifacts_dir=artifacts_dir)

    browser: Browser | None = None
    context: BrowserContext | None = None
    playwright_context = sync_playwright().start()
    exit_code = 0

    try:
        if args.cdp_url:
            browser = playwright_context.chromium.connect_over_cdp(args.cdp_url)
            if not browser.contexts:
                raise RuntimeError("Connected browser has no context/pages.")
            context = browser.contexts[0]
        else:
            browser = playwright_context.chromium.launch(headless=args.headless)
            context = browser.new_context(viewport=SCREENSHOT_VIEWPORT)

        page = resolve_starting_page(args, context)

        run_login_step(page, recorder)
        run_mi_negocio_menu_step(page, recorder)
        run_agregar_negocio_modal_step(page, recorder)
        run_administrar_negocios_step(page, recorder)
        run_informacion_general_step(page, recorder)
        run_detalles_cuenta_step(page, recorder)
        run_tus_negocios_step(page, recorder)
        run_terminos_step(page, context, recorder)
        run_politica_step(page, context, recorder)

        # Any FAIL in required report fields marks non-zero exit.
        if any(item.status == "FAIL" for item in recorder.results.values()):
            exit_code = 1
    except Exception:  # noqa: BLE001
        exit_code = 2
        recorder.results["workflow_runtime"] = StepResult(
            name="Workflow runtime",
            status="FAIL",
            details=traceback.format_exc(),
        )
    finally:
        report_path = recorder.write_report()
        if context and not args.cdp_url:
            context.close()
        if browser and not args.cdp_url:
            browser.close()
        playwright_context.stop()

    return exit_code, report_path


def parse_args():
    parser = argparse.ArgumentParser(description="SaleADS Mi Negocio full workflow test")
    parser.add_argument(
        "--cdp-url",
        default=os.getenv("SALEADS_CDP_URL", ""),
        help="Chromium CDP websocket/http endpoint to attach to an existing browser session.",
    )
    parser.add_argument(
        "--saleads-url",
        default=os.getenv("SALEADS_URL", ""),
        help="SaleADS login URL for the current environment (optional if attaching via CDP).",
    )
    parser.add_argument(
        "--artifacts-dir",
        default=os.getenv("SALEADS_ARTIFACTS_DIR", "saleads_e2e/artifacts"),
        help="Directory to store screenshots and reports.",
    )
    parser.add_argument(
        "--headless",
        action="store_true",
        default=os.getenv("SALEADS_HEADLESS", "false").lower() in ("1", "true", "yes"),
        help="Launch browser headlessly when not attaching through CDP.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    code, report_path = execute_workflow(args)
    print(f"Workflow finished with exit code {code}.")
    print(f"Report: {report_path}")
    return code


if __name__ == "__main__":
    raise SystemExit(main())
