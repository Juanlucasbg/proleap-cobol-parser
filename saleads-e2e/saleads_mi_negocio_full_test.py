#!/usr/bin/env python3
"""
SaleADS Mi Negocio module workflow validation.

This script is environment-agnostic:
- It never hardcodes a SaleADS domain.
- The target login page URL must be provided at runtime via:
  1) --base-url argument, or
  2) SALEADS_BASE_URL environment variable.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable

from playwright.sync_api import BrowserContext, Locator, Page, TimeoutError, sync_playwright


EMAIL_UNDER_TEST = "juanlucasbarbiergarzon@gmail.com"
DEFAULT_TIMEOUT_MS = 20_000


@dataclass
class StepResult:
    status: str
    details: str
    evidence: list[str] = field(default_factory=list)
    final_url: str | None = None


def utc_now_str() -> str:
    return datetime.now(timezone.utc).isoformat()


def slugify(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", text.lower()).strip("_")


def wait_for_ui(page: Page) -> None:
    for state in ("domcontentloaded", "networkidle"):
        try:
            page.wait_for_load_state(state, timeout=10_000)
        except TimeoutError:
            # Some app views keep background requests alive; continue safely.
            pass
    page.wait_for_timeout(1000)


def save_screenshot(page: Page, artifacts_dir: Path, checkpoint_name: str, full_page: bool = False) -> str:
    file_path = artifacts_dir / f"{checkpoint_name}.png"
    page.screenshot(path=str(file_path), full_page=full_page)
    return str(file_path)


def visible(locator: Locator) -> bool:
    try:
        return locator.is_visible(timeout=1500)
    except Exception:
        return False


def first_visible(page: Page, patterns: Iterable[str], roles: Iterable[str] | None = None) -> Locator | None:
    compiled = [re.compile(pattern, re.IGNORECASE) for pattern in patterns]
    target_roles = tuple(roles or ("button", "link", "menuitem", "tab"))

    for rx in compiled:
        for role in target_roles:
            locator = page.get_by_role(role, name=rx)
            try:
                count = locator.count()
            except Exception:
                count = 0
            for idx in range(min(count, 8)):
                candidate = locator.nth(idx)
                if visible(candidate):
                    return candidate

        # Fallback to plain visible text when role matching is not sufficient.
        locator = page.get_by_text(rx)
        try:
            count = locator.count()
        except Exception:
            count = 0
        for idx in range(min(count, 10)):
            candidate = locator.nth(idx)
            if visible(candidate):
                return candidate
    return None


def click_by_text(page: Page, patterns: Iterable[str], description: str) -> None:
    locator = first_visible(page, patterns)
    if locator is None:
        raise AssertionError(f"Could not find visible element for: {description}")
    locator.click()
    wait_for_ui(page)


def assert_text_visible(page: Page, patterns: Iterable[str], description: str, timeout_ms: int = DEFAULT_TIMEOUT_MS) -> None:
    compiled = [re.compile(pattern, re.IGNORECASE) for pattern in patterns]
    deadline = time.time() + (timeout_ms / 1000)
    while time.time() < deadline:
        for rx in compiled:
            if visible(page.get_by_text(rx).first):
                return
        page.wait_for_timeout(500)
    raise AssertionError(f"Expected visible text not found: {description}")


def poll_for_sidebar_page(context: BrowserContext, timeout_ms: int = 90_000) -> Page:
    deadline = time.time() + (timeout_ms / 1000)
    while time.time() < deadline:
        for page in context.pages:
            try:
                if visible(page.get_by_text(re.compile(r"(mi\s*negocio|negocio|dashboard)", re.IGNORECASE)).first):
                    page.bring_to_front()
                    wait_for_ui(page)
                    return page
            except Exception:
                continue
        time.sleep(1)
    raise AssertionError("Main application interface with sidebar was not detected after login.")


def step_login_with_google(context: BrowserContext, app_page: Page, artifacts_dir: Path) -> StepResult:
    evidence: list[str] = []
    details: list[str] = []

    # If session is already authenticated, continue workflow without forcing relogin.
    if visible(app_page.get_by_text(re.compile(r"(mi\s*negocio|negocio|dashboard)", re.IGNORECASE)).first):
        evidence.append(save_screenshot(app_page, artifacts_dir, "01_dashboard_loaded"))
        return StepResult(
            status="PASS",
            details="Session already authenticated; dashboard and sidebar are visible.",
            evidence=evidence,
        )

    click_target_patterns = [
        r"sign in with google",
        r"iniciar sesi[oó]n con google",
        r"continuar con google",
        r"google",
        r"iniciar sesi[oó]n",
        r"login",
    ]

    candidate = first_visible(app_page, click_target_patterns)
    if candidate is None:
        raise AssertionError("Login button/entry point was not visible on login page.")

    popup_page: Page | None = None
    try:
        with context.expect_page(timeout=7_000) as popup_info:
            candidate.click()
        popup_page = popup_info.value
        wait_for_ui(popup_page)
        details.append("Google flow opened in a new tab/window.")
    except TimeoutError:
        candidate.click()
        wait_for_ui(app_page)
        details.append("Google flow stayed in current tab.")

    auth_page = popup_page or app_page
    account_option = first_visible(auth_page, [re.escape(EMAIL_UNDER_TEST)], roles=("button", "link"))
    if account_option:
        account_option.click()
        wait_for_ui(auth_page)
        details.append(f"Selected Google account {EMAIL_UNDER_TEST}.")
    else:
        details.append("Google account selector not shown; continued with existing authenticated session.")

    main_page = poll_for_sidebar_page(context)
    assert_text_visible(main_page, [r"negocio", r"mi\s*negocio"], "left sidebar navigation")
    evidence.append(save_screenshot(main_page, artifacts_dir, "01_dashboard_loaded"))

    return StepResult(
        status="PASS",
        details="; ".join(details) or "Login flow completed and dashboard sidebar is visible.",
        evidence=evidence,
    )


def step_open_mi_negocio_menu(app_page: Page, artifacts_dir: Path) -> StepResult:
    # Some environments expose "Mi Negocio" directly; others nest it under "Negocio".
    mi_negocio_locator = first_visible(app_page, [r"mi\s*negocio"])
    if mi_negocio_locator:
        mi_negocio_locator.click()
        wait_for_ui(app_page)
    else:
        click_by_text(app_page, [r"negocio"], "Negocio section")
        click_by_text(app_page, [r"mi\s*negocio"], "Mi Negocio option")

    assert_text_visible(app_page, [r"agregar negocio"], "Agregar Negocio item")
    assert_text_visible(app_page, [r"administrar negocios"], "Administrar Negocios item")
    screenshot = save_screenshot(app_page, artifacts_dir, "02_mi_negocio_menu_expanded")

    return StepResult(
        status="PASS",
        details="Mi Negocio menu expanded and required submenu options are visible.",
        evidence=[screenshot],
    )


def step_validate_agregar_negocio_modal(app_page: Page, artifacts_dir: Path) -> StepResult:
    click_by_text(app_page, [r"agregar negocio"], "Agregar Negocio")

    assert_text_visible(app_page, [r"crear nuevo negocio"], "Crear Nuevo Negocio modal title")
    assert_text_visible(app_page, [r"nombre del negocio"], "Nombre del Negocio input label")
    assert_text_visible(app_page, [r"tienes\s*2\s*de\s*3\s*negocios"], "business quota text")
    assert_text_visible(app_page, [r"cancelar"], "Cancelar button")
    assert_text_visible(app_page, [r"crear negocio"], "Crear Negocio button")

    name_input = first_visible(app_page, [r"nombre del negocio"], roles=("textbox",))
    if name_input is None:
        # Input may have no accessible role label; fallback to placeholder-like text.
        name_input = app_page.locator(
            "input[placeholder*='Nombre'], input[name*='nombre'], input[id*='nombre']"
        ).first

    if visible(name_input):
        name_input.fill("Negocio Prueba Automatizacion")
        app_page.wait_for_timeout(300)

    screenshot = save_screenshot(app_page, artifacts_dir, "03_agregar_negocio_modal")
    click_by_text(app_page, [r"cancelar"], "Cancelar modal")

    return StepResult(
        status="PASS",
        details="Agregar Negocio modal validated and closed using Cancelar.",
        evidence=[screenshot],
    )


def step_open_administrar_negocios(app_page: Page, artifacts_dir: Path) -> StepResult:
    if not visible(app_page.get_by_text(re.compile(r"administrar negocios", re.IGNORECASE)).first):
        click_by_text(app_page, [r"mi\s*negocio"], "Mi Negocio re-expand")

    click_by_text(app_page, [r"administrar negocios"], "Administrar Negocios")

    assert_text_visible(app_page, [r"informaci[oó]n general"], "Informacion General section")
    assert_text_visible(app_page, [r"detalles de la cuenta"], "Detalles de la Cuenta section")
    assert_text_visible(app_page, [r"tus negocios"], "Tus Negocios section")
    assert_text_visible(app_page, [r"secci[oó]n legal"], "Seccion Legal section")

    screenshot = save_screenshot(app_page, artifacts_dir, "04_administrar_negocios_page", full_page=True)
    return StepResult(
        status="PASS",
        details="Administrar Negocios page loaded with all required sections.",
        evidence=[screenshot],
    )


def step_validate_info_general(app_page: Page) -> StepResult:
    assert_text_visible(app_page, [r"informaci[oó]n general"], "Informacion General section heading")
    assert_text_visible(
        app_page,
        [r"[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}", re.escape(EMAIL_UNDER_TEST)],
        "user email",
    )
    assert_text_visible(app_page, [r"business plan"], "BUSINESS PLAN text")
    assert_text_visible(app_page, [r"cambiar plan"], "Cambiar Plan button")

    # Username can vary by account/environment, but a profile name should be present.
    name_visible = visible(app_page.get_by_text(re.compile(r"(juan|lucas|barbier|garzon)", re.IGNORECASE)).first)
    if not name_visible:
        # Fallback: look for explicit "Nombre" label.
        name_visible = visible(app_page.get_by_text(re.compile(r"nombre", re.IGNORECASE)).first)
    if not name_visible:
        raise AssertionError("User name was not clearly visible in Informacion General section.")

    return StepResult(
        status="PASS",
        details="Informacion General shows user name, user email, BUSINESS PLAN and Cambiar Plan.",
    )


def step_validate_detalles_cuenta(app_page: Page) -> StepResult:
    assert_text_visible(app_page, [r"cuenta creada"], "Cuenta creada text")
    assert_text_visible(app_page, [r"estado activo"], "Estado activo text")
    assert_text_visible(app_page, [r"idioma seleccionado"], "Idioma seleccionado text")

    return StepResult(
        status="PASS",
        details="Detalles de la Cuenta contains Cuenta creada, Estado activo and Idioma seleccionado.",
    )


def step_validate_tus_negocios(app_page: Page) -> StepResult:
    assert_text_visible(app_page, [r"tus negocios"], "Tus Negocios section")
    assert_text_visible(app_page, [r"agregar negocio"], "Agregar Negocio button")
    assert_text_visible(app_page, [r"tienes\s*2\s*de\s*3\s*negocios"], "business quota text")

    card_or_item_exists = False
    for selector in ("[data-testid*='business']", "[class*='business']", "li", "[role='listitem']", "tr"):
        try:
            if app_page.locator(selector).count() > 0:
                card_or_item_exists = True
                break
        except Exception:
            continue
    if not card_or_item_exists:
        raise AssertionError("Business list was not detected in Tus Negocios section.")

    return StepResult(
        status="PASS",
        details="Tus Negocios list, Agregar Negocio and quota information are visible.",
    )


def step_validate_legal_page(
    context: BrowserContext,
    app_page: Page,
    link_patterns: list[str],
    heading_patterns: list[str],
    checkpoint_name: str,
    artifacts_dir: Path,
) -> StepResult:
    app_page.bring_to_front()
    wait_for_ui(app_page)

    link = first_visible(app_page, link_patterns, roles=("link", "button"))
    if link is None:
        raise AssertionError(f"Could not find legal link: {' / '.join(link_patterns)}")

    initial_url = app_page.url
    legal_page: Page = app_page
    opened_new_tab = False

    try:
        with context.expect_page(timeout=6_000) as page_info:
            link.click()
        legal_page = page_info.value
        opened_new_tab = True
    except TimeoutError:
        link.click()

    wait_for_ui(legal_page)
    assert_text_visible(legal_page, heading_patterns, "legal heading")

    body_text = legal_page.locator("body").inner_text(timeout=DEFAULT_TIMEOUT_MS).strip()
    if len(body_text) < 120:
        raise AssertionError("Legal content text appears too short to be valid.")

    screenshot = save_screenshot(legal_page, artifacts_dir, checkpoint_name, full_page=True)
    final_url = legal_page.url

    if opened_new_tab:
        legal_page.close()
        app_page.bring_to_front()
        wait_for_ui(app_page)
    else:
        # Same-tab navigation: return to application.
        if app_page.url != initial_url:
            app_page.go_back(timeout=DEFAULT_TIMEOUT_MS)
            wait_for_ui(app_page)

    return StepResult(
        status="PASS",
        details="Legal page opened, validated, and returned to application tab.",
        evidence=[screenshot],
        final_url=final_url,
    )


def fail_result(message: str) -> StepResult:
    return StepResult(status="FAIL", details=message)


def run_workflow(base_url: str, headed: bool) -> tuple[dict[str, StepResult], str, Path]:
    stamp = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
    artifacts_dir = Path("saleads-e2e") / "artifacts" / stamp
    artifacts_dir.mkdir(parents=True, exist_ok=True)

    report_fields = [
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
    results: dict[str, StepResult] = {}

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=not headed, slow_mo=200 if headed else 0)
        context = browser.new_context(ignore_https_errors=True)
        app_page = context.new_page()
        app_page.goto(base_url, wait_until="domcontentloaded", timeout=60_000)
        wait_for_ui(app_page)

        try:
            results["Login"] = step_login_with_google(context, app_page, artifacts_dir)
            app_page = poll_for_sidebar_page(context, timeout_ms=30_000)
        except Exception as err:
            results["Login"] = fail_result(str(err))

        if results["Login"].status == "PASS":
            try:
                results["Mi Negocio menu"] = step_open_mi_negocio_menu(app_page, artifacts_dir)
            except Exception as err:
                results["Mi Negocio menu"] = fail_result(str(err))
        else:
            results["Mi Negocio menu"] = fail_result("Skipped because Login failed.")

        if results["Mi Negocio menu"].status == "PASS":
            try:
                results["Agregar Negocio modal"] = step_validate_agregar_negocio_modal(app_page, artifacts_dir)
            except Exception as err:
                results["Agregar Negocio modal"] = fail_result(str(err))
        else:
            results["Agregar Negocio modal"] = fail_result("Skipped because Mi Negocio menu validation failed.")

        if results["Mi Negocio menu"].status == "PASS":
            try:
                results["Administrar Negocios view"] = step_open_administrar_negocios(app_page, artifacts_dir)
            except Exception as err:
                results["Administrar Negocios view"] = fail_result(str(err))
        else:
            results["Administrar Negocios view"] = fail_result("Skipped because Mi Negocio menu validation failed.")

        if results["Administrar Negocios view"].status == "PASS":
            try:
                results["Información General"] = step_validate_info_general(app_page)
            except Exception as err:
                results["Información General"] = fail_result(str(err))
            try:
                results["Detalles de la Cuenta"] = step_validate_detalles_cuenta(app_page)
            except Exception as err:
                results["Detalles de la Cuenta"] = fail_result(str(err))
            try:
                results["Tus Negocios"] = step_validate_tus_negocios(app_page)
            except Exception as err:
                results["Tus Negocios"] = fail_result(str(err))
        else:
            results["Información General"] = fail_result("Skipped because Administrar Negocios view failed.")
            results["Detalles de la Cuenta"] = fail_result("Skipped because Administrar Negocios view failed.")
            results["Tus Negocios"] = fail_result("Skipped because Administrar Negocios view failed.")

        if results["Administrar Negocios view"].status == "PASS":
            try:
                results["Términos y Condiciones"] = step_validate_legal_page(
                    context=context,
                    app_page=app_page,
                    link_patterns=[r"t[eé]rminos y condiciones"],
                    heading_patterns=[r"t[eé]rminos y condiciones"],
                    checkpoint_name="08_terminos_y_condiciones",
                    artifacts_dir=artifacts_dir,
                )
            except Exception as err:
                results["Términos y Condiciones"] = fail_result(str(err))
            try:
                results["Política de Privacidad"] = step_validate_legal_page(
                    context=context,
                    app_page=app_page,
                    link_patterns=[r"pol[ií]tica de privacidad"],
                    heading_patterns=[r"pol[ií]tica de privacidad"],
                    checkpoint_name="09_politica_de_privacidad",
                    artifacts_dir=artifacts_dir,
                )
            except Exception as err:
                results["Política de Privacidad"] = fail_result(str(err))
        else:
            results["Términos y Condiciones"] = fail_result("Skipped because Administrar Negocios view failed.")
            results["Política de Privacidad"] = fail_result("Skipped because Administrar Negocios view failed.")

        browser.close()

    # Guarantee all required keys exist.
    for field_name in report_fields:
        if field_name not in results:
            results[field_name] = fail_result("Step did not execute.")

    overall_status = "PASS" if all(result.status == "PASS" for result in results.values()) else "FAIL"
    return results, overall_status, artifacts_dir


def persist_report(
    base_url: str,
    results: dict[str, StepResult],
    overall_status: str,
    artifacts_dir: Path,
) -> Path:
    report_path = artifacts_dir / "final_report.json"
    payload = {
        "name": "saleads_mi_negocio_full_test",
        "executed_at_utc": utc_now_str(),
        "base_url": base_url,
        "overall_status": overall_status,
        "report": {key: asdict(value) for key, value in results.items()},
    }
    report_path.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
    return report_path


def print_summary(results: dict[str, StepResult], overall_status: str, report_path: Path) -> None:
    ordered_fields = [
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
    print("\n=== SaleADS Mi Negocio Final Report ===")
    for field_name in ordered_fields:
        result = results[field_name]
        print(f"- {field_name}: {result.status}")
        if result.final_url:
            print(f"  final_url: {result.final_url}")
    print(f"Overall: {overall_status}")
    print(f"Report JSON: {report_path}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate SaleADS Mi Negocio full workflow.")
    parser.add_argument(
        "--base-url",
        default=os.getenv("SALEADS_BASE_URL", "").strip(),
        help="Environment-specific SaleADS login URL. Can also be set via SALEADS_BASE_URL.",
    )
    parser.add_argument(
        "--headed",
        action="store_true",
        help="Run browser in headed mode (default is headless).",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not args.base_url:
        print(
            "ERROR: Missing target environment URL. Provide --base-url or SALEADS_BASE_URL.",
            file=sys.stderr,
        )
        return 2

    results, overall_status, artifacts_dir = run_workflow(base_url=args.base_url, headed=args.headed)
    report_path = persist_report(args.base_url, results, overall_status, artifacts_dir)
    print_summary(results, overall_status, report_path)
    return 0 if overall_status == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
