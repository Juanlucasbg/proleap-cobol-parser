#!/usr/bin/env python3
"""
Environment-agnostic SaleADS Mi Negocio E2E workflow test.

Usage examples:
  SALEADS_START_URL="https://<env>/login" python3 scripts/saleads_mi_negocio_full_test.py
  SALEADS_CDP_URL="http://127.0.0.1:9222" python3 scripts/saleads_mi_negocio_full_test.py
"""

from __future__ import annotations

import json
import os
import re
import sys
from dataclasses import dataclass, asdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable, Optional

try:
    from playwright.sync_api import (
        Browser,
        BrowserContext,
        Page,
        TimeoutError as PlaywrightTimeoutError,
        sync_playwright,
    )
except ImportError:
    print(
        "Playwright is not installed. Install with:\n"
        "  pip install playwright\n"
        "  python -m playwright install chromium",
        file=sys.stderr,
    )
    sys.exit(2)


WAIT_TIMEOUT_MS = int(os.getenv("SALEADS_WAIT_TIMEOUT_MS", "25000"))
DEFAULT_EMAIL = "juanlucasbarbiergarzon@gmail.com"
SCREENSHOT_ROOT = Path("artifacts") / "saleads_mi_negocio_full_test"
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
    name: str
    passed: bool
    details: str
    screenshot: Optional[str] = None
    url: Optional[str] = None


def utc_stamp() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


def slugify(value: str) -> str:
    value = value.lower()
    value = re.sub(r"[^a-z0-9]+", "-", value)
    return value.strip("-")


def wait_for_ui(page: Page) -> None:
    page.wait_for_load_state("domcontentloaded", timeout=WAIT_TIMEOUT_MS)
    try:
        page.wait_for_load_state("networkidle", timeout=5000)
    except PlaywrightTimeoutError:
        # Some views keep background requests active; DOM readiness is enough.
        pass


def click_visible_text(page: Page, text: str, exact: bool = True) -> None:
    target = page.get_by_text(text, exact=exact).first
    target.wait_for(state="visible", timeout=WAIT_TIMEOUT_MS)
    target.click()
    wait_for_ui(page)


def ensure_visible_text(page: Page, text: str, exact: bool = False) -> None:
    locator = page.get_by_text(text, exact=exact).first
    locator.wait_for(state="visible", timeout=WAIT_TIMEOUT_MS)


def save_screenshot(page: Page, step_name: str, directory: Path, full_page: bool = False) -> str:
    filename = f"{slugify(step_name)}.png"
    path = directory / filename
    page.screenshot(path=str(path), full_page=full_page)
    return str(path)


def run_step(
    results: list[StepResult],
    step_name: str,
    fn: Callable[[], StepResult],
) -> StepResult:
    try:
        result = fn()
    except Exception as exc:  # noqa: BLE001
        result = StepResult(name=step_name, passed=False, details=str(exc))
    results.append(result)
    status = "PASS" if result.passed else "FAIL"
    print(f"[{status}] {step_name}: {result.details}")
    return result


def get_first_existing_page(context: BrowserContext) -> Page:
    pages = context.pages
    if pages:
        return pages[0]
    return context.new_page()


def connect_browser() -> tuple[BrowserContext, Page, Optional[Browser], bool]:
    """
    Returns: context, page, launched_browser, owns_context
    """
    start_url = os.getenv("SALEADS_START_URL")
    cdp_url = os.getenv("SALEADS_CDP_URL")
    headless = os.getenv("SALEADS_HEADLESS", "false").lower() in ("1", "true", "yes")

    playwright = sync_playwright().start()
    launched_browser: Optional[Browser] = None
    owns_context = True

    if cdp_url:
        browser = playwright.chromium.connect_over_cdp(cdp_url)
        if not browser.contexts:
            context = browser.new_context()
        else:
            context = browser.contexts[0]
            owns_context = False
        page = get_first_existing_page(context)
        return context, page, browser, owns_context

    if not start_url:
        raise ValueError("Set SALEADS_START_URL (or SALEADS_CDP_URL) to run this workflow.")

    launched_browser = playwright.chromium.launch(headless=headless)
    context = launched_browser.new_context()
    page = context.new_page()
    page.goto(start_url, wait_until="domcontentloaded")
    wait_for_ui(page)
    return context, page, launched_browser, owns_context


def try_google_selector(page: Page, email: str) -> None:
    account_item = page.get_by_text(email, exact=True).first
    if account_item.count() > 0 and account_item.is_visible():
        account_item.click()
        wait_for_ui(page)
        return

    account_item_ci = page.get_by_text(re.compile(re.escape(email), re.IGNORECASE)).first
    if account_item_ci.count() > 0 and account_item_ci.is_visible():
        account_item_ci.click()
        wait_for_ui(page)


def open_or_click_login(page: Page) -> Optional[Page]:
    labels = [
        "Sign in with Google",
        "Iniciar sesión con Google",
        "Continuar con Google",
        "Acceder con Google",
    ]

    popup_page: Optional[Page] = None

    for label in labels:
        button = page.get_by_role("button", name=re.compile(re.escape(label), re.IGNORECASE)).first
        if button.count() > 0 and button.is_visible():
            try:
                with page.expect_popup(timeout=6000) as popup_info:
                    button.click()
                popup_page = popup_info.value
            except PlaywrightTimeoutError:
                button.click()
            wait_for_ui(page)
            return popup_page

    google_text_link = page.get_by_text(re.compile("google", re.IGNORECASE)).first
    if google_text_link.count() > 0 and google_text_link.is_visible():
        try:
            with page.expect_popup(timeout=6000) as popup_info:
                google_text_link.click()
            popup_page = popup_info.value
        except PlaywrightTimeoutError:
            google_text_link.click()
        wait_for_ui(page)
        return popup_page

    raise RuntimeError("Could not find login button or Google sign-in trigger.")


def ensure_sidebar_visible(page: Page) -> None:
    sidebar = page.locator("aside, nav").first
    sidebar.wait_for(state="visible", timeout=WAIT_TIMEOUT_MS)


def validate_legal_link(
    app_page: Page,
    link_text: str,
    expected_heading: str,
    screenshot_name: str,
    artifacts_dir: Path,
) -> tuple[str, str]:
    context = app_page.context
    legal_page: Page = app_page
    opened_new_tab = False
    old_url = app_page.url

    link = app_page.get_by_text(link_text, exact=False).first
    link.wait_for(state="visible", timeout=WAIT_TIMEOUT_MS)

    try:
        with app_page.expect_popup(timeout=6000) as popup_info:
            link.click()
        legal_page = popup_info.value
        opened_new_tab = True
    except PlaywrightTimeoutError:
        link.click()

    wait_for_ui(legal_page)
    ensure_visible_text(legal_page, expected_heading, exact=False)
    legal_content = legal_page.locator("body")
    body_text = legal_content.inner_text(timeout=WAIT_TIMEOUT_MS).strip()
    if len(body_text) < 120:
        raise AssertionError("Legal content looks too short.")

    screenshot_path = save_screenshot(legal_page, screenshot_name, artifacts_dir, full_page=True)
    final_url = legal_page.url

    if opened_new_tab:
        legal_page.close()
        app_page.bring_to_front()
        wait_for_ui(app_page)
    elif app_page.url != old_url:
        app_page.go_back(wait_until="domcontentloaded")
        wait_for_ui(app_page)

    # Validate we are back in the app.
    if app_page.is_closed():
        raise AssertionError("Application tab was closed unexpectedly.")
    _ = context.pages
    return screenshot_path, final_url


def main() -> int:
    email = os.getenv("SALEADS_GOOGLE_EMAIL", DEFAULT_EMAIL)
    artifacts_dir = SCREENSHOT_ROOT / utc_stamp()
    artifacts_dir.mkdir(parents=True, exist_ok=True)
    print(f"Artifacts directory: {artifacts_dir}")

    results: list[StepResult] = []
    context: Optional[BrowserContext] = None
    app_page: Optional[Page] = None
    browser: Optional[Browser] = None
    owns_context = True
    fatal_error: Optional[str] = None

    try:
        context, app_page, browser, owns_context = connect_browser()

        def step_login() -> StepResult:
            popup = open_or_click_login(app_page)
            if popup is not None:
                wait_for_ui(popup)
                try_google_selector(popup, email)
                try:
                    popup.wait_for_event("close", timeout=15000)
                except PlaywrightTimeoutError:
                    # If popup remains open after account selection, continue anyway.
                    pass

            # Google selector can sometimes appear in the same tab.
            try_google_selector(app_page, email)
            wait_for_ui(app_page)
            ensure_sidebar_visible(app_page)
            screenshot = save_screenshot(app_page, "01-dashboard-loaded", artifacts_dir, full_page=True)
            return StepResult(
                name="Login",
                passed=True,
                details="Main interface and left sidebar are visible.",
                screenshot=screenshot,
                url=app_page.url,
            )

        def step_menu() -> StepResult:
            ensure_visible_text(app_page, "Negocio", exact=False)
            click_visible_text(app_page, "Mi Negocio", exact=False)
            ensure_visible_text(app_page, "Agregar Negocio", exact=False)
            ensure_visible_text(app_page, "Administrar Negocios", exact=False)
            screenshot = save_screenshot(app_page, "02-mi-negocio-expanded", artifacts_dir)
            return StepResult(
                name="Mi Negocio menu",
                passed=True,
                details="Menu expanded and both submenu options are visible.",
                screenshot=screenshot,
            )

        def step_modal() -> StepResult:
            click_visible_text(app_page, "Agregar Negocio", exact=False)
            ensure_visible_text(app_page, "Crear Nuevo Negocio", exact=False)
            ensure_visible_text(app_page, "Nombre del Negocio", exact=False)
            ensure_visible_text(app_page, "Tienes 2 de 3 negocios", exact=False)
            ensure_visible_text(app_page, "Cancelar", exact=False)
            ensure_visible_text(app_page, "Crear Negocio", exact=False)

            field = app_page.get_by_label("Nombre del Negocio").first
            if field.count() == 0:
                field = app_page.get_by_placeholder(re.compile("Nombre del Negocio", re.IGNORECASE)).first
            if field.count() > 0 and field.is_visible():
                field.click()
                field.fill("Negocio Prueba Automatización")

            screenshot = save_screenshot(app_page, "03-agregar-negocio-modal", artifacts_dir)
            click_visible_text(app_page, "Cancelar", exact=False)
            return StepResult(
                name="Agregar Negocio modal",
                passed=True,
                details="Modal validated and closed using Cancelar.",
                screenshot=screenshot,
            )

        def step_admin() -> StepResult:
            if app_page.get_by_text("Administrar Negocios", exact=False).first.count() == 0:
                click_visible_text(app_page, "Mi Negocio", exact=False)
            click_visible_text(app_page, "Administrar Negocios", exact=False)
            ensure_visible_text(app_page, "Información General", exact=False)
            ensure_visible_text(app_page, "Detalles de la Cuenta", exact=False)
            ensure_visible_text(app_page, "Tus Negocios", exact=False)
            ensure_visible_text(app_page, "Sección Legal", exact=False)
            screenshot = save_screenshot(app_page, "04-administrar-negocios", artifacts_dir, full_page=True)
            return StepResult(
                name="Administrar Negocios view",
                passed=True,
                details="All required account sections are visible.",
                screenshot=screenshot,
                url=app_page.url,
            )

        def step_info_general() -> StepResult:
            ensure_visible_text(app_page, "Información General", exact=False)
            ensure_visible_text(app_page, "BUSINESS PLAN", exact=False)
            ensure_visible_text(app_page, "Cambiar Plan", exact=False)
            email_locator = app_page.locator(r"text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/").first
            email_locator.wait_for(state="visible", timeout=WAIT_TIMEOUT_MS)

            info_block = app_page.get_by_text("Información General", exact=False).first.locator("xpath=..")
            info_text = info_block.inner_text(timeout=WAIT_TIMEOUT_MS).strip()
            if len(info_text.split()) < 6:
                raise AssertionError("Información General appears incomplete (user name not detected).")

            return StepResult(
                name="Información General",
                passed=True,
                details="Name/email context, BUSINESS PLAN and Cambiar Plan are visible.",
            )

        def step_detalles() -> StepResult:
            ensure_visible_text(app_page, "Detalles de la Cuenta", exact=False)
            ensure_visible_text(app_page, "Cuenta creada", exact=False)
            ensure_visible_text(app_page, "Estado activo", exact=False)
            ensure_visible_text(app_page, "Idioma seleccionado", exact=False)
            return StepResult(
                name="Detalles de la Cuenta",
                passed=True,
                details="All requested account details are visible.",
            )

        def step_negocios() -> StepResult:
            ensure_visible_text(app_page, "Tus Negocios", exact=False)
            ensure_visible_text(app_page, "Agregar Negocio", exact=False)
            ensure_visible_text(app_page, "Tienes 2 de 3 negocios", exact=False)
            return StepResult(
                name="Tus Negocios",
                passed=True,
                details="Business list area and usage limit text are visible.",
            )

        def step_terms() -> StepResult:
            screenshot, final_url = validate_legal_link(
                app_page=app_page,
                link_text="Términos y Condiciones",
                expected_heading="Términos y Condiciones",
                screenshot_name="08-terminos-y-condiciones",
                artifacts_dir=artifacts_dir,
            )
            return StepResult(
                name="Términos y Condiciones",
                passed=True,
                details="Legal page heading/content validated and application tab restored.",
                screenshot=screenshot,
                url=final_url,
            )

        def step_privacy() -> StepResult:
            screenshot, final_url = validate_legal_link(
                app_page=app_page,
                link_text="Política de Privacidad",
                expected_heading="Política de Privacidad",
                screenshot_name="09-politica-de-privacidad",
                artifacts_dir=artifacts_dir,
            )
            return StepResult(
                name="Política de Privacidad",
                passed=True,
                details="Legal page heading/content validated and application tab restored.",
                screenshot=screenshot,
                url=final_url,
            )

        run_step(results, "Login", step_login)
        run_step(results, "Mi Negocio menu", step_menu)
        run_step(results, "Agregar Negocio modal", step_modal)
        run_step(results, "Administrar Negocios view", step_admin)
        run_step(results, "Información General", step_info_general)
        run_step(results, "Detalles de la Cuenta", step_detalles)
        run_step(results, "Tus Negocios", step_negocios)
        run_step(results, "Términos y Condiciones", step_terms)
        run_step(results, "Política de Privacidad", step_privacy)

    except Exception as exc:  # noqa: BLE001
        fatal_error = str(exc)
        print(f"Fatal error: {fatal_error}", file=sys.stderr)
    finally:
        collected = {item.name: item for item in results}
        ordered_results: list[StepResult] = []
        for field in REPORT_FIELDS:
            if field in collected:
                ordered_results.append(collected[field])
            else:
                reason = fatal_error or "Not executed due to a previous failed step."
                ordered_results.append(
                    StepResult(
                        name=field,
                        passed=False,
                        details=reason,
                    )
                )

        report = {
            "name": "saleads_mi_negocio_full_test",
            "generated_at_utc": utc_stamp(),
            "artifacts_dir": str(artifacts_dir),
            "results": [asdict(item) for item in ordered_results],
        }

        report_path = artifacts_dir / "final_report.json"
        report_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
        print(f"Final report: {report_path}")
        print(json.dumps(report, indent=2, ensure_ascii=False))

        if context and owns_context:
            context.close()
        if browser:
            browser.close()

    all_passed = all(item.passed for item in ordered_results)
    return 0 if all_passed else 1


if __name__ == "__main__":
    sys.exit(main())
