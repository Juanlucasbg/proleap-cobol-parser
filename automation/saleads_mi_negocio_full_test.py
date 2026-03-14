#!/usr/bin/env python3
"""
SaleADS Mi Negocio workflow end-to-end check.

This script validates the full flow:
1) Login with Google (or verify already logged in)
2) Mi Negocio menu expansion
3) Agregar Negocio modal
4) Administrar Negocios view
5) Informacion General validations
6) Detalles de la Cuenta validations
7) Tus Negocios validations
8) Terminos y Condiciones link validation (same tab or new tab)
9) Politica de Privacidad link validation (same tab or new tab)
10) Final PASS/FAIL report output

The script avoids hardcoded domains; provide a start URL with --start-url (or env)
if a fresh browser instance is launched. It can also attach to an existing browser
context via CDP where the page is already open on the current environment login.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Dict, Iterable, Optional, Tuple, Union

from playwright.sync_api import Browser, BrowserContext, Locator, Page, Playwright, TimeoutError, expect, sync_playwright


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


def env_bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "t", "yes", "y", "on"}


def wait_for_ui(page: Page, timeout_ms: int = 15000) -> None:
    page.wait_for_load_state("domcontentloaded", timeout=timeout_ms)
    try:
        page.wait_for_load_state("networkidle", timeout=5000)
    except TimeoutError:
        # Some SPA pages keep background requests active; DOM loaded is enough.
        pass


def ensure_visible(locator: Locator, timeout_ms: int = 10000) -> None:
    expect(locator).to_be_visible(timeout=timeout_ms)


def is_visible(locator: Locator, timeout_ms: int = 1500) -> bool:
    try:
        expect(locator).to_be_visible(timeout=timeout_ms)
        return True
    except AssertionError:
        return False


def click_first_visible(page: Page, candidates: Iterable[Tuple[str, Locator]], description: str) -> str:
    for label, locator in candidates:
        current = locator.first
        if is_visible(current):
            current.click()
            wait_for_ui(page)
            return label
    raise AssertionError(f"Could not find clickable element for: {description}")


def assert_text_visible(page: Page, text: str, timeout_ms: int = 10000) -> None:
    exact = page.get_by_text(text, exact=True).first
    if is_visible(exact, timeout_ms=1200):
        ensure_visible(exact, timeout_ms=timeout_ms)
        return

    # Fallback for whitespace/case differences.
    fuzzy = page.get_by_text(re.compile(re.escape(text), re.IGNORECASE)).first
    ensure_visible(fuzzy, timeout_ms=timeout_ms)


def assert_any_text_visible(page: Page, texts: Iterable[str], timeout_ms: int = 10000) -> str:
    last_error: Optional[Exception] = None
    for text in texts:
        try:
            assert_text_visible(page, text, timeout_ms=timeout_ms)
            return text
        except Exception as exc:  # pylint: disable=broad-except
            last_error = exc
    raise AssertionError(f"None of the expected texts are visible: {list(texts)}") from last_error


def first_nonempty_line(value: str) -> str:
    for line in (ln.strip() for ln in value.splitlines()):
        if line:
            return line
    return ""


def section_text(page: Page, section_title: str) -> str:
    section = page.locator("section,article,div").filter(has_text=re.compile(re.escape(section_title), re.IGNORECASE)).first
    if is_visible(section, timeout_ms=2000):
        return section.inner_text()
    return ""


def capture(page: Page, path: Path, full_page: bool = False) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    page.screenshot(path=str(path), full_page=full_page)


def current_main_page(context: BrowserContext) -> Page:
    if context.pages:
        page = context.pages[-1]
        page.bring_to_front()
        return page
    return context.new_page()


def find_sidebar_visible(page: Page) -> bool:
    sidebar_candidates = [
        page.locator("aside").first,
        page.locator("[class*='sidebar']").first,
        page.locator("nav").first,
    ]
    return any(is_visible(candidate, timeout_ms=1200) for candidate in sidebar_candidates)


def select_google_account_if_prompted(context: BrowserContext, email: str) -> bool:
    end_time = time.time() + 20

    while time.time() < end_time:
        for p in list(context.pages):
            email_target_candidates = [
                p.get_by_text(email, exact=False),
                p.get_by_role("button", name=re.compile(re.escape(email), re.IGNORECASE)),
                p.get_by_role("link", name=re.compile(re.escape(email), re.IGNORECASE)),
            ]
            for candidate in email_target_candidates:
                target = candidate.first
                if is_visible(target, timeout_ms=700):
                    target.click()
                    wait_for_ui(p)
                    return True
        time.sleep(0.4)

    return False


def click_link_with_new_tab_support(
    context: BrowserContext,
    page: Page,
    link_name: Union[str, Iterable[str]],
) -> Tuple[Page, bool]:
    link_names = [link_name] if isinstance(link_name, str) else list(link_name)
    link_candidates = []
    for name in link_names:
        link_candidates.extend(
            [
                page.get_by_role("link", name=re.compile(re.escape(name), re.IGNORECASE)),
                page.get_by_role("button", name=re.compile(re.escape(name), re.IGNORECASE)),
                page.get_by_text(re.compile(re.escape(name), re.IGNORECASE)),
            ]
        )

    selected: Optional[Locator] = None
    for locator in link_candidates:
        if is_visible(locator.first):
            selected = locator.first
            break

    if selected is None:
        raise AssertionError(f"Could not find legal link: {link_names}")

    opened_new_page = False
    target_page = page
    try:
        with context.expect_page(timeout=5000) as page_event:
            selected.click()
        target_page = page_event.value
        opened_new_page = True
    except TimeoutError:
        # Click already happened in the same tab.
        target_page = page

    wait_for_ui(target_page, timeout_ms=20000)
    return target_page, opened_new_page


@dataclass
class WorkflowResult:
    status: Dict[str, str] = field(default_factory=lambda: {field_name: "FAIL" for field_name in REPORT_FIELDS})
    details: Dict[str, str] = field(default_factory=dict)
    urls: Dict[str, str] = field(default_factory=dict)

    def pass_step(self, field_name: str, detail: str = "") -> None:
        self.status[field_name] = "PASS"
        if detail:
            self.details[field_name] = detail

    def fail_step(self, field_name: str, err: Exception) -> None:
        self.status[field_name] = "FAIL"
        self.details[field_name] = f"{type(err).__name__}: {err}"


def run_workflow(
    context: BrowserContext,
    page: Page,
    artifacts_dir: Path,
    google_account_email: str,
) -> WorkflowResult:
    result = WorkflowResult()

    # Step 1 - Login with Google
    try:
        login_button_candidates = [
            (
                "google button",
                page.get_by_role("button", name=re.compile(r"(sign in|iniciar|continuar).*(google)|google", re.IGNORECASE)),
            ),
            ("google text", page.get_by_text(re.compile(r"google", re.IGNORECASE))),
        ]

        clicked = False
        for _, locator in login_button_candidates:
            if is_visible(locator.first):
                locator.first.click()
                wait_for_ui(page)
                clicked = True
                break

        if clicked:
            select_google_account_if_prompted(context, google_account_email)

        # Validate dashboard/main app interface and sidebar.
        # We accept either a visible sidebar container or navigation text "Negocio".
        sidebar_ok = find_sidebar_visible(page) or is_visible(page.get_by_text(re.compile(r"negocio", re.IGNORECASE)).first, 4000)
        if not sidebar_ok:
            raise AssertionError("Sidebar/main navigation is not visible after login.")

        capture(page, artifacts_dir / "01_dashboard_loaded.png", full_page=True)
        result.pass_step("Login", "Main interface and sidebar visible.")
    except Exception as exc:  # pylint: disable=broad-except
        result.fail_step("Login", exc)
        return result

    # Step 2 - Open Mi Negocio menu
    try:
        click_first_visible(
            page,
            [
                ("mi negocio link", page.get_by_role("link", name=re.compile(r"mi\s+negocio", re.IGNORECASE))),
                ("mi negocio button", page.get_by_role("button", name=re.compile(r"mi\s+negocio", re.IGNORECASE))),
                ("mi negocio text", page.get_by_text(re.compile(r"mi\s+negocio", re.IGNORECASE))),
            ],
            "Mi Negocio menu",
        )

        assert_text_visible(page, "Agregar Negocio")
        assert_text_visible(page, "Administrar Negocios")
        capture(page, artifacts_dir / "02_mi_negocio_expanded.png", full_page=True)
        result.pass_step("Mi Negocio menu", "Submenu expanded with expected entries.")
    except Exception as exc:  # pylint: disable=broad-except
        result.fail_step("Mi Negocio menu", exc)
        return result

    # Step 3 - Validate Agregar Negocio modal
    try:
        click_first_visible(
            page,
            [
                ("agregar negocio menu item", page.get_by_role("link", name=re.compile(r"agregar\s+negocio", re.IGNORECASE))),
                ("agregar negocio button", page.get_by_role("button", name=re.compile(r"agregar\s+negocio", re.IGNORECASE))),
                ("agregar negocio text", page.get_by_text(re.compile(r"agregar\s+negocio", re.IGNORECASE))),
            ],
            "Agregar Negocio action",
        )

        assert_text_visible(page, "Crear Nuevo Negocio")
        assert_text_visible(page, "Nombre del Negocio")
        assert_text_visible(page, "Tienes 2 de 3 negocios")
        assert_text_visible(page, "Cancelar")
        assert_text_visible(page, "Crear Negocio")

        capture(page, artifacts_dir / "03_agregar_negocio_modal.png", full_page=True)

        # Optional action from requested workflow.
        input_candidates = [
            page.get_by_label(re.compile(r"Nombre del Negocio", re.IGNORECASE)),
            page.get_by_placeholder(re.compile(r"Nombre del Negocio", re.IGNORECASE)),
        ]
        for locator in input_candidates:
            if is_visible(locator.first):
                locator.first.click()
                locator.first.fill("Negocio Prueba Automatizacion")
                break

        click_first_visible(
            page,
            [
                ("cancelar button", page.get_by_role("button", name=re.compile(r"cancelar", re.IGNORECASE))),
                ("cancelar text", page.get_by_text(re.compile(r"cancelar", re.IGNORECASE))),
            ],
            "Close Crear Nuevo Negocio modal",
        )
        result.pass_step("Agregar Negocio modal", "Modal content validated and closed with Cancelar.")
    except Exception as exc:  # pylint: disable=broad-except
        result.fail_step("Agregar Negocio modal", exc)
        return result

    # Step 4 - Open Administrar Negocios
    try:
        if not is_visible(page.get_by_text(re.compile(r"administrar\s+negocios", re.IGNORECASE)).first, 1200):
            click_first_visible(
                page,
                [
                    ("mi negocio link", page.get_by_role("link", name=re.compile(r"mi\s+negocio", re.IGNORECASE))),
                    ("mi negocio button", page.get_by_role("button", name=re.compile(r"mi\s+negocio", re.IGNORECASE))),
                    ("mi negocio text", page.get_by_text(re.compile(r"mi\s+negocio", re.IGNORECASE))),
                ],
                "Re-open Mi Negocio menu",
            )

        click_first_visible(
            page,
            [
                (
                    "administrar negocios link",
                    page.get_by_role("link", name=re.compile(r"administrar\s+negocios", re.IGNORECASE)),
                ),
                (
                    "administrar negocios button",
                    page.get_by_role("button", name=re.compile(r"administrar\s+negocios", re.IGNORECASE)),
                ),
                ("administrar negocios text", page.get_by_text(re.compile(r"administrar\s+negocios", re.IGNORECASE))),
            ],
            "Open Administrar Negocios",
        )

        assert_any_text_visible(page, ["Informacion General", "Información General"])
        assert_text_visible(page, "Detalles de la Cuenta")
        assert_text_visible(page, "Tus Negocios")
        assert_any_text_visible(page, ["Seccion Legal", "Sección Legal"])
        capture(page, artifacts_dir / "04_administrar_negocios.png", full_page=True)
        result.pass_step("Administrar Negocios view", "All four account sections are visible.")
    except Exception as exc:  # pylint: disable=broad-except
        result.fail_step("Administrar Negocios view", exc)
        return result

    # Step 5 - Validate Informacion General
    try:
        info_text = section_text(page, "Informacion General")
        if not info_text.strip():
            info_text = section_text(page, "Información General")
        if not info_text.strip():
            info_text = page.locator("body").inner_text()
        email_match = re.search(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", info_text)
        if not email_match:
            raise AssertionError("Could not detect a user email in Informacion General section.")

        filtered_lines = []
        for line in info_text.splitlines():
            candidate = line.strip()
            if not candidate:
                continue
            lower = candidate.lower()
            if "informacion general" in lower:
                continue
            if "business plan" in lower:
                continue
            if "cambiar plan" in lower:
                continue
            if "@" in candidate:
                continue
            filtered_lines.append(candidate)

        if not filtered_lines:
            raise AssertionError("Could not detect a probable user name in Informacion General section.")

        assert_text_visible(page, "BUSINESS PLAN")
        click_target = page.get_by_role("button", name=re.compile(r"cambiar\s+plan", re.IGNORECASE)).first
        if not is_visible(click_target, timeout_ms=1200):
            click_target = page.get_by_text(re.compile(r"cambiar\s+plan", re.IGNORECASE)).first
        ensure_visible(click_target)

        result.pass_step(
            "Informacion General",
            f"Detected email {email_match.group(0)} and probable name '{first_nonempty_line(chr(10).join(filtered_lines))}'.",
        )
    except Exception as exc:  # pylint: disable=broad-except
        result.fail_step("Informacion General", exc)

    # Step 6 - Validate Detalles de la Cuenta
    try:
        assert_text_visible(page, "Cuenta creada")
        assert_any_text_visible(page, ["Estado activo", "Estado Activo"])
        assert_any_text_visible(page, ["Idioma seleccionado", "Idioma Seleccionado"])
        result.pass_step("Detalles de la Cuenta", "Required account details labels are visible.")
    except Exception as exc:  # pylint: disable=broad-except
        result.fail_step("Detalles de la Cuenta", exc)

    # Step 7 - Validate Tus Negocios
    try:
        assert_text_visible(page, "Tus Negocios")
        assert_text_visible(page, "Agregar Negocio")
        assert_text_visible(page, "Tienes 2 de 3 negocios")

        negocios_section = page.locator("section,article,div").filter(
            has_text=re.compile(r"tus\s+negocios", re.IGNORECASE)
        ).first
        if is_visible(negocios_section, timeout_ms=2000):
            if len(negocios_section.inner_text().strip()) < 10:
                raise AssertionError("Tus Negocios section appears empty.")

        result.pass_step("Tus Negocios", "Business section and limits are visible.")
    except Exception as exc:  # pylint: disable=broad-except
        result.fail_step("Tus Negocios", exc)

    # Step 8 - Validate Terminos y Condiciones
    try:
        legal_page, opened_new_tab = click_link_with_new_tab_support(
            context,
            page,
            ["Terminos y Condiciones", "Términos y Condiciones"],
        )
        assert_any_text_visible(legal_page, ["Terminos y Condiciones", "Términos y Condiciones"], timeout_ms=20000)
        legal_text = legal_page.locator("body").inner_text().strip()
        if len(legal_text) < 120:
            raise AssertionError("Legal content text is too short for Terminos y Condiciones page.")
        capture(legal_page, artifacts_dir / "08_terminos_y_condiciones.png", full_page=True)
        result.urls["Terminos y Condiciones"] = legal_page.url
        result.pass_step("Terminos y Condiciones", f"Validated legal content at: {legal_page.url}")

        if opened_new_tab:
            legal_page.close()
            page.bring_to_front()
        else:
            try:
                page.go_back(timeout=15000)
                wait_for_ui(page)
            except TimeoutError:
                pass
    except Exception as exc:  # pylint: disable=broad-except
        result.fail_step("Terminos y Condiciones", exc)

    # Step 9 - Validate Politica de Privacidad
    try:
        legal_page, opened_new_tab = click_link_with_new_tab_support(
            context,
            page,
            ["Politica de Privacidad", "Política de Privacidad"],
        )
        assert_any_text_visible(legal_page, ["Politica de Privacidad", "Política de Privacidad"], timeout_ms=20000)
        legal_text = legal_page.locator("body").inner_text().strip()
        if len(legal_text) < 120:
            raise AssertionError("Legal content text is too short for Politica de Privacidad page.")
        capture(legal_page, artifacts_dir / "09_politica_de_privacidad.png", full_page=True)
        result.urls["Politica de Privacidad"] = legal_page.url
        result.pass_step("Politica de Privacidad", f"Validated legal content at: {legal_page.url}")

        if opened_new_tab:
            legal_page.close()
            page.bring_to_front()
        else:
            try:
                page.go_back(timeout=15000)
                wait_for_ui(page)
            except TimeoutError:
                pass
    except Exception as exc:  # pylint: disable=broad-except
        result.fail_step("Politica de Privacidad", exc)

    return result


def create_context(playwright: Playwright, args: argparse.Namespace) -> Tuple[Browser, BrowserContext]:
    if args.ws_endpoint:
        browser = playwright.chromium.connect_over_cdp(args.ws_endpoint)
        if browser.contexts:
            return browser, browser.contexts[0]
        return browser, browser.new_context()

    browser = playwright.chromium.launch(headless=not args.headed, slow_mo=args.slow_mo_ms)
    return browser, browser.new_context()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run SaleADS Mi Negocio full workflow validation.")
    parser.add_argument("--start-url", default=os.getenv("SALEADS_START_URL"), help="Optional SaleADS login URL for current environment.")
    parser.add_argument("--google-account", default=os.getenv("SALEADS_GOOGLE_ACCOUNT", "juanlucasbarbiergarzon@gmail.com"))
    parser.add_argument("--headed", action="store_true", default=env_bool("SALEADS_HEADED", False))
    parser.add_argument("--slow-mo-ms", type=int, default=int(os.getenv("SALEADS_SLOW_MO_MS", "150")))
    parser.add_argument("--output-dir", default=os.getenv("SALEADS_OUTPUT_DIR", "automation/artifacts"))
    parser.add_argument("--ws-endpoint", default=os.getenv("PW_WS_ENDPOINT"), help="Optional CDP endpoint to attach existing browser.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    run_id = datetime.utcnow().strftime("%Y%m%dT%H%M%SZ")
    artifacts_dir = Path(args.output_dir) / f"saleads_mi_negocio_full_test_{run_id}"
    artifacts_dir.mkdir(parents=True, exist_ok=True)

    browser: Optional[Browser] = None
    try:
        with sync_playwright() as playwright:
            browser, context = create_context(playwright, args)
            page = current_main_page(context)

            if args.start_url:
                page.goto(args.start_url, wait_until="domcontentloaded")
                wait_for_ui(page)
            elif page.url in {"", "about:blank"}:
                print(
                    "Warning: Browser opened without --start-url and current tab is blank. "
                    "Provide --start-url or attach to an existing session using --ws-endpoint.",
                    file=sys.stderr,
                )

            result = run_workflow(context, page, artifacts_dir, args.google_account)

            report_payload = {
                "name": "saleads_mi_negocio_full_test",
                "generated_at_utc": datetime.utcnow().isoformat(timespec="seconds") + "Z",
                "artifacts_dir": str(artifacts_dir),
                "report": result.status,
                "urls": result.urls,
                "details": result.details,
            }
            report_file = artifacts_dir / "final_report.json"
            report_file.write_text(json.dumps(report_payload, indent=2, ensure_ascii=False), encoding="utf-8")

            print(json.dumps(report_payload, indent=2, ensure_ascii=False))

            all_pass = all(value == "PASS" for value in result.status.values())
            return 0 if all_pass else 1
    finally:
        if browser is not None:
            browser.close()


if __name__ == "__main__":
    sys.exit(main())
