#!/usr/bin/env python3
"""
SaleADS Mi Negocio full workflow test.

This script is intentionally URL-agnostic:
- It accepts the login URL from CLI (--start-url) or env (SALEADS_START_URL).
- It never hardcodes a specific domain.
"""

from __future__ import annotations

import argparse
import json
import os
import re
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable

from playwright.sync_api import BrowserContext, Locator, Page, TimeoutError, sync_playwright


DEFAULT_TIMEOUT_MS = 20_000
CHECKPOINTS_DIR = "artifacts/saleads_mi_negocio_full_test"


@dataclass
class StepOutcome:
    name: str
    status: str = "FAIL"
    checks: list[str] = field(default_factory=list)
    evidence: list[str] = field(default_factory=list)
    notes: list[str] = field(default_factory=list)


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def ensure_dir(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)


def screenshot(page: Page, output_dir: Path, filename: str, full_page: bool = False) -> str:
    target = output_dir / filename
    page.screenshot(path=str(target), full_page=full_page)
    return str(target)


def safe_visible(locator: Locator, timeout_ms: int = 2_500) -> bool:
    try:
        return locator.first.is_visible(timeout=timeout_ms)
    except Exception:
        return False


def wait_for_ui(page: Page, timeout_ms: int = DEFAULT_TIMEOUT_MS) -> None:
    page.wait_for_load_state("domcontentloaded", timeout=timeout_ms)
    try:
        page.wait_for_load_state("networkidle", timeout=4_000)
    except TimeoutError:
        # Some SPAs keep a connection open; domcontentloaded is enough in this case.
        pass


def first_visible(locators: list[Locator], timeout_ms: int = 3_000) -> Locator | None:
    for locator in locators:
        if safe_visible(locator, timeout_ms):
            return locator.first
    return None


def click_and_wait(page: Page, locator: Locator) -> None:
    try:
        locator.click(timeout=8_000)
    except TimeoutError:
        # Some animated controls are visible but not "actionable" for normal click.
        locator.click(force=True, timeout=8_000)
    wait_for_ui(page)


def visible_text(page: Page, text: str) -> bool:
    return safe_visible(page.get_by_text(text, exact=False))


def validate_all(outcome: StepOutcome, validations: list[tuple[str, Callable[[], bool]]]) -> None:
    failures: list[str] = []
    for label, checker in validations:
        ok = False
        try:
            ok = checker()
        except Exception as exc:
            outcome.notes.append(f"Validation error on '{label}': {exc}")
        outcome.checks.append(f"{'PASS' if ok else 'FAIL'} - {label}")
        if not ok:
            failures.append(label)
    if not failures:
        outcome.status = "PASS"
    else:
        outcome.status = "FAIL"
        outcome.notes.append("Missing/failed validations: " + ", ".join(failures))


def find_login_button(page: Page) -> Locator | None:
    return first_visible(
        [
            page.get_by_role("button", name=re.compile(r"sign in with google", re.I)),
            page.get_by_role("button", name=re.compile(r"iniciar sesi[oó]n con google", re.I)),
            page.get_by_role("button", name=re.compile(r"google", re.I)),
            page.get_by_role("link", name=re.compile(r"sign in with google", re.I)),
            page.get_by_role("link", name=re.compile(r"iniciar sesi[oó]n con google", re.I)),
            page.get_by_text(re.compile(r"sign in with google|iniciar sesi[oó]n con google", re.I)),
        ],
        timeout_ms=4_000,
    )


def find_generic_login_button(page: Page) -> Locator | None:
    return first_visible(
        [
            page.get_by_role("button", name=re.compile(r"iniciar sesi[oó]n|sign in|login|log in|acceder", re.I)),
            page.get_by_role("link", name=re.compile(r"iniciar sesi[oó]n|sign in|login|log in|acceder", re.I)),
            page.get_by_text(re.compile(r"iniciar sesi[oó]n|sign in|login|log in|acceder", re.I)),
        ],
        timeout_ms=4_000,
    )


def sidebar_ready(page: Page) -> bool:
    nav = safe_visible(page.locator("aside nav, aside, nav").first, 5_000)
    menu = visible_text(page, "Negocio") or visible_text(page, "Mi Negocio")
    return nav and menu


def click_sidebar_item(page: Page, text: str) -> None:
    target = first_visible(
        [
            page.get_by_role("link", name=re.compile(rf"^{re.escape(text)}$", re.I)),
            page.get_by_role("button", name=re.compile(rf"^{re.escape(text)}$", re.I)),
            page.get_by_role("menuitem", name=re.compile(rf"^{re.escape(text)}$", re.I)),
            page.get_by_text(re.compile(rf"^{re.escape(text)}$", re.I)),
        ],
        timeout_ms=4_000,
    )
    if target is None:
        raise RuntimeError(f"Sidebar item '{text}' not found.")
    click_and_wait(page, target)


def maybe_choose_google_account(popup: Page | None, target_email: str) -> bool:
    pages = [p for p in [popup] if p is not None]
    for pg in pages:
        wait_for_ui(pg)
        account = first_visible(
            [
                pg.get_by_text(target_email, exact=False),
                pg.get_by_role("button", name=re.compile(re.escape(target_email), re.I)),
                pg.get_by_role("link", name=re.compile(re.escape(target_email), re.I)),
            ],
            timeout_ms=5_000,
        )
        if account is not None:
            account.click()
            wait_for_ui(pg)
            return True
    return False


def click_google_sign_in(page: Page, context: BrowserContext) -> Page:
    candidates = [
        page.get_by_role("button", name=re.compile(r"google|sign in with google|iniciar sesi[oó]n con google", re.I)),
        page.get_by_role("link", name=re.compile(r"google|sign in with google|iniciar sesi[oó]n con google", re.I)),
        page.get_by_text(re.compile(r"google|sign in with google|iniciar sesi[oó]n con google", re.I)),
    ]
    errors: list[str] = []

    for candidate in candidates:
        if not safe_visible(candidate, 1_500):
            continue

        target = candidate.first
        before = page.url
        try:
            with context.expect_page(timeout=5_000) as popup_info:
                click_and_wait(page, target)
            popup = popup_info.value
            wait_for_ui(popup)
            return popup
        except TimeoutError:
            # Same-tab navigations are valid for this flow.
            wait_for_ui(page)
            if page.url != before:
                return page
            try:
                click_and_wait(page, target)
                return page
            except Exception as exc:
                errors.append(str(exc))
        except Exception as exc:
            errors.append(str(exc))

    detail = errors[0] if errors else "Google sign-in control was not found."
    raise RuntimeError(detail)


def click_legal_link_and_validate(
    context: BrowserContext,
    app_page: Page,
    link_text: str,
    expected_heading: str,
    output_dir: Path,
    screenshot_name: str,
) -> tuple[bool, str | None, str]:
    opened_new_tab = False
    legal_page: Page = app_page
    captured_url: str | None = None

    link = first_visible(
        [
            app_page.get_by_role("link", name=re.compile(rf"^{re.escape(link_text)}$", re.I)),
            app_page.get_by_text(re.compile(rf"^{re.escape(link_text)}$", re.I)),
        ],
        timeout_ms=5_000,
    )
    if link is None:
        fallback = screenshot(app_page, output_dir, screenshot_name, full_page=True)
        return False, None, fallback

    try:
        with context.expect_page(timeout=5_000) as new_page_info:
            link.click()
        legal_page = new_page_info.value
        opened_new_tab = True
        wait_for_ui(legal_page)
    except TimeoutError:
        # Navigated in same tab.
        link.click()
        wait_for_ui(app_page)

    heading_ok = safe_visible(legal_page.get_by_role("heading", name=re.compile(expected_heading, re.I)), 8_000) or safe_visible(
        legal_page.get_by_text(re.compile(expected_heading, re.I), exact=False), 8_000
    )
    body_ok = safe_visible(legal_page.locator("main, article, section, body").first, 8_000)
    screenshot_path = screenshot(legal_page, output_dir, screenshot_name, full_page=True)
    captured_url = legal_page.url

    if opened_new_tab:
        legal_page.close()
        app_page.bring_to_front()
        wait_for_ui(app_page)
    else:
        app_page.go_back(wait_until="domcontentloaded")
        wait_for_ui(app_page)

    if heading_ok and body_ok:
        return True, captured_url, screenshot_path
    return False, captured_url, screenshot_path


def run(args: argparse.Namespace) -> int:
    start_url = args.start_url or os.getenv("SALEADS_START_URL")
    account_email = args.google_email
    output_dir = Path(args.output_dir)
    ensure_dir(output_dir)

    report: dict = {
        "name": "saleads_mi_negocio_full_test",
        "started_at": now_iso(),
        "input": {
            "start_url": start_url,
            "google_email": account_email,
            "headless": args.headless,
        },
        "results": {},
        "final_urls": {},
    }

    fields = [
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
    outcomes = {field: StepOutcome(field) for field in fields}

    if not start_url:
        msg = "No start URL provided. Use --start-url or SALEADS_START_URL."
        for field in outcomes.values():
            field.notes.append(msg)
        report["ended_at"] = now_iso()
        report["status"] = "FAIL"
        report["results"] = {k: vars(v) for k, v in outcomes.items()}
        report_path = output_dir / "report.json"
        report_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
        print(json.dumps(report, indent=2, ensure_ascii=False))
        return 1

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=args.headless)
        context = browser.new_context(viewport={"width": 1440, "height": 1024})
        page = context.new_page()

        try:
            page.goto(start_url, wait_until="domcontentloaded", timeout=45_000)
            wait_for_ui(page)
            initial_capture = screenshot(page, output_dir, "00_initial_page.png", full_page=True)

            # 1) Login with Google
            login = outcomes["Login"]
            action_taken = False

            working_page = page
            try:
                # Path A: page already shows "Sign in with Google".
                direct_google = find_login_button(page)
                if direct_google is not None:
                    working_page = click_google_sign_in(page, context)
                    action_taken = True
                else:
                    # Path B: click generic login first, then click Google in IdP page.
                    generic_login = find_generic_login_button(page)
                    if generic_login is None:
                        login.notes.append("Could not find login button or direct Google sign-in.")
                    else:
                        click_and_wait(page, generic_login)
                        action_taken = True
                        working_page = click_google_sign_in(page, context)
            except Exception as exc:
                login.notes.append(f"Login navigation failed: {exc}")

            # If account selector appears, select target account.
            selected = maybe_choose_google_account(working_page, account_email)
            if selected:
                login.notes.append(f"Google account selected: {account_email}")
            elif action_taken:
                login.notes.append("Google account selector not shown or prior session was not available.")

            # Bring app page to front if Google flow opened another tab.
            try:
                page.bring_to_front()
                wait_for_ui(page)
            except Exception:
                pass

            validate_all(
                login,
                [
                    (
                        "Main application interface appears",
                        lambda: sidebar_ready(page),
                    ),
                    (
                        "Left sidebar navigation is visible",
                        lambda: sidebar_ready(page),
                    ),
                ],
            )
            if not action_taken:
                login.status = "FAIL"
                login.notes.append("No login action was executed.")

            if login.status == "PASS":
                login.evidence.append(screenshot(page, output_dir, "01_dashboard_loaded.png", full_page=True))
            else:
                login.evidence.append(screenshot(page, output_dir, "01_post_login_attempt.png", full_page=True))
            login.notes.append(f"Initial page evidence: {initial_capture}")

            # 2) Open Mi Negocio menu
            menu = outcomes["Mi Negocio menu"]
            try:
                click_sidebar_item(page, "Negocio")
            except Exception:
                menu.notes.append("Sidebar section 'Negocio' was not directly clickable; continuing.")
            try:
                click_sidebar_item(page, "Mi Negocio")
            except Exception as exc:
                menu.notes.append(str(exc))

            validate_all(
                menu,
                [
                    ("Submenu expands", lambda: visible_text(page, "Agregar Negocio") or visible_text(page, "Administrar Negocios")),
                    ("'Agregar Negocio' is visible", lambda: visible_text(page, "Agregar Negocio")),
                    ("'Administrar Negocios' is visible", lambda: visible_text(page, "Administrar Negocios")),
                ],
            )
            if menu.status == "PASS":
                menu.evidence.append(screenshot(page, output_dir, "02_mi_negocio_menu_expanded.png", full_page=True))
            else:
                menu.evidence.append(screenshot(page, output_dir, "02_mi_negocio_menu_attempt.png", full_page=True))

            # 3) Validate Agregar Negocio modal
            modal = outcomes["Agregar Negocio modal"]
            try:
                click_sidebar_item(page, "Agregar Negocio")
            except Exception as exc:
                modal.notes.append(str(exc))

            validate_all(
                modal,
                [
                    ("Modal title 'Crear Nuevo Negocio' is visible", lambda: visible_text(page, "Crear Nuevo Negocio")),
                    ("Input field 'Nombre del Negocio' exists", lambda: safe_visible(page.get_by_label("Nombre del Negocio"), 4_000)
                     or safe_visible(page.get_by_placeholder(re.compile(r"nombre del negocio", re.I)), 4_000)),
                    ("Text 'Tienes 2 de 3 negocios' is visible", lambda: visible_text(page, "Tienes 2 de 3 negocios")),
                    ("Buttons 'Cancelar' and 'Crear Negocio' are present", lambda: safe_visible(page.get_by_role("button", name=re.compile("Cancelar", re.I)), 4_000)
                     and safe_visible(page.get_by_role("button", name=re.compile("Crear Negocio", re.I)), 4_000)),
                ],
            )
            if modal.status == "PASS":
                modal.evidence.append(screenshot(page, output_dir, "03_agregar_negocio_modal.png", full_page=True))
                # Optional actions
                field = first_visible(
                    [
                        page.get_by_label("Nombre del Negocio"),
                        page.get_by_placeholder(re.compile(r"nombre del negocio", re.I)),
                    ]
                )
                if field is not None:
                    field.click()
                    field.fill("Negocio Prueba Automatización")
                cancel_button = first_visible([page.get_by_role("button", name=re.compile("Cancelar", re.I))])
                if cancel_button is not None:
                    click_and_wait(page, cancel_button)
            else:
                modal.evidence.append(screenshot(page, output_dir, "03_agregar_negocio_modal_attempt.png", full_page=True))

            # 4) Open Administrar Negocios
            admin = outcomes["Administrar Negocios view"]
            try:
                if not visible_text(page, "Administrar Negocios"):
                    click_sidebar_item(page, "Mi Negocio")
                click_sidebar_item(page, "Administrar Negocios")
            except Exception as exc:
                admin.notes.append(str(exc))

            validate_all(
                admin,
                [
                    ("Section 'Información General' exists", lambda: visible_text(page, "Información General")),
                    ("Section 'Detalles de la Cuenta' exists", lambda: visible_text(page, "Detalles de la Cuenta")),
                    ("Section 'Tus Negocios' exists", lambda: visible_text(page, "Tus Negocios")),
                    ("Section 'Sección Legal' exists", lambda: visible_text(page, "Sección Legal")),
                ],
            )
            if admin.status == "PASS":
                admin.evidence.append(screenshot(page, output_dir, "04_administrar_negocios_page.png", full_page=True))
            else:
                admin.evidence.append(screenshot(page, output_dir, "04_administrar_negocios_attempt.png", full_page=True))

            # 5) Información General
            info = outcomes["Información General"]
            validate_all(
                info,
                [
                    ("User name is visible", lambda: safe_visible(page.locator("section").filter(has_text=re.compile("Informaci[oó]n General", re.I)).locator("h1, h2, h3, p, span, div").nth(1), 4_000)
                     or safe_visible(page.locator("text=/@/"), 2_500)),
                    ("User email is visible", lambda: safe_visible(page.locator("text=/[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}/i").first, 4_000)),
                    ("Text 'BUSINESS PLAN' is visible", lambda: visible_text(page, "BUSINESS PLAN")),
                    ("Button 'Cambiar Plan' is visible", lambda: safe_visible(page.get_by_role("button", name=re.compile("Cambiar Plan", re.I)), 4_000)),
                ],
            )

            # 6) Detalles de la Cuenta
            details = outcomes["Detalles de la Cuenta"]
            validate_all(
                details,
                [
                    ("'Cuenta creada' is visible", lambda: visible_text(page, "Cuenta creada")),
                    ("'Estado activo' is visible", lambda: visible_text(page, "Estado activo")),
                    ("'Idioma seleccionado' is visible", lambda: visible_text(page, "Idioma seleccionado")),
                ],
            )

            # 7) Tus Negocios
            business = outcomes["Tus Negocios"]
            validate_all(
                business,
                [
                    ("Business list is visible", lambda: safe_visible(page.locator("section").filter(has_text=re.compile("Tus Negocios", re.I)), 4_000)),
                    ("Button 'Agregar Negocio' exists", lambda: safe_visible(page.get_by_role("button", name=re.compile("Agregar Negocio", re.I)), 4_000)
                     or safe_visible(page.get_by_role("link", name=re.compile("Agregar Negocio", re.I)), 4_000)),
                    ("Text 'Tienes 2 de 3 negocios' is visible", lambda: visible_text(page, "Tienes 2 de 3 negocios")),
                ],
            )

            # 8) Términos y Condiciones
            terms = outcomes["Términos y Condiciones"]
            terms_ok, terms_url, terms_evidence = click_legal_link_and_validate(
                context=context,
                app_page=page,
                link_text="Términos y Condiciones",
                expected_heading="Términos y Condiciones",
                output_dir=output_dir,
                screenshot_name="05_terminos_y_condiciones.png",
            )
            terms.status = "PASS" if terms_ok else "FAIL"
            terms.checks.extend(
                [
                    f"{'PASS' if terms_ok else 'FAIL'} - The page contains heading 'Términos y Condiciones'",
                    f"{'PASS' if terms_ok else 'FAIL'} - Legal content text is visible",
                ]
            )
            terms.evidence.append(terms_evidence)
            report["final_urls"]["Términos y Condiciones"] = terms_url
            if not terms_ok:
                terms.notes.append("Terms page validation failed.")

            # 9) Política de Privacidad
            privacy = outcomes["Política de Privacidad"]
            privacy_ok, privacy_url, privacy_evidence = click_legal_link_and_validate(
                context=context,
                app_page=page,
                link_text="Política de Privacidad",
                expected_heading="Política de Privacidad",
                output_dir=output_dir,
                screenshot_name="06_politica_de_privacidad.png",
            )
            privacy.status = "PASS" if privacy_ok else "FAIL"
            privacy.checks.extend(
                [
                    f"{'PASS' if privacy_ok else 'FAIL'} - The page contains heading 'Política de Privacidad'",
                    f"{'PASS' if privacy_ok else 'FAIL'} - Legal content text is visible",
                ]
            )
            privacy.evidence.append(privacy_evidence)
            report["final_urls"]["Política de Privacidad"] = privacy_url
            if not privacy_ok:
                privacy.notes.append("Privacy page validation failed.")

        finally:
            context.close()
            browser.close()

    report["ended_at"] = now_iso()
    report["results"] = {k: vars(v) for k, v in outcomes.items()}
    report["status"] = "PASS" if all(v.status == "PASS" for v in outcomes.values()) else "FAIL"

    report_path = output_dir / "report.json"
    report_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")

    print(json.dumps(report, indent=2, ensure_ascii=False))
    print(f"\nReport saved: {report_path}")
    return 0 if report["status"] == "PASS" else 1


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="SaleADS Mi Negocio full workflow test")
    parser.add_argument("--start-url", help="SaleADS login URL for the current environment.")
    parser.add_argument("--google-email", default="juanlucasbarbiergarzon@gmail.com", help="Google account to select if chooser appears.")
    parser.add_argument("--output-dir", default=CHECKPOINTS_DIR, help="Artifacts directory for screenshots and report.")
    parser.add_argument("--headless", action="store_true", help="Run browser in headless mode.")
    return parser.parse_args()


if __name__ == "__main__":
    raise SystemExit(run(parse_args()))
