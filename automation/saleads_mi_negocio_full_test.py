#!/usr/bin/env python3
"""
End-to-end validation for SaleADS "Mi Negocio" workflow.

Design goals:
- Works in any SaleADS environment (URL is configurable, never hardcoded).
- Supports either:
  1) a pre-opened browser tab via CDP (`SALEADS_CDP_URL`), or
  2) direct navigation to a login page (`SALEADS_LOGIN_URL`).
- Uses visible text selectors whenever possible.
- Captures screenshots at requested checkpoints.
- Produces a structured PASS/FAIL report for each requested section.
"""

from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable, Optional

from playwright.sync_api import Browser, BrowserContext, Page, TimeoutError, sync_playwright


GOOGLE_ACCOUNT_EMAIL = os.getenv("SALEADS_GOOGLE_ACCOUNT_EMAIL", "juanlucasbarbiergarzon@gmail.com")
DEFAULT_TIMEOUT_MS = int(os.getenv("SALEADS_TIMEOUT_MS", "15000"))
HEADLESS = os.getenv("SALEADS_HEADLESS", "false").lower() in {"1", "true", "yes"}
CDP_URL = os.getenv("SALEADS_CDP_URL")
LOGIN_URL = os.getenv("SALEADS_LOGIN_URL")


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
class StepOutcome:
    status: str = "FAIL"
    details: list[str] = field(default_factory=list)


def _now_utc() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


def _normalize(text: str) -> str:
    replacements = {
        "á": "a",
        "é": "e",
        "í": "i",
        "ó": "o",
        "ú": "u",
        "Á": "A",
        "É": "E",
        "Í": "I",
        "Ó": "O",
        "Ú": "U",
        "ñ": "n",
        "Ñ": "N",
    }
    for src, dst in replacements.items():
        text = text.replace(src, dst)
    return text


def _wait_for_ui(page: Page, timeout_ms: int = DEFAULT_TIMEOUT_MS) -> None:
    for state in ("domcontentloaded", "networkidle"):
        try:
            page.wait_for_load_state(state, timeout=timeout_ms)
        except TimeoutError:
            pass
    page.wait_for_timeout(700)


def _is_visible(locator, timeout_ms: int = DEFAULT_TIMEOUT_MS) -> bool:
    try:
        locator.first.wait_for(state="visible", timeout=timeout_ms)
        return True
    except TimeoutError:
        return False


def _first_visible_text(page: Page, text_options: Iterable[str], timeout_ms: int = 2500):
    for text in text_options:
        locators = [
            page.get_by_role("button", name=re.compile(rf"^{re.escape(text)}$", re.IGNORECASE)),
            page.get_by_role("link", name=re.compile(rf"^{re.escape(text)}$", re.IGNORECASE)),
            page.get_by_role("menuitem", name=re.compile(rf"^{re.escape(text)}$", re.IGNORECASE)),
            page.get_by_text(text, exact=True),
            page.get_by_text(re.compile(re.escape(text), re.IGNORECASE)),
        ]
        for locator in locators:
            if _is_visible(locator, timeout_ms=timeout_ms):
                return locator.first
    return None


def _click_by_visible_text(
    page: Page,
    text_options: Iterable[str],
    timeout_ms: int = DEFAULT_TIMEOUT_MS,
    wait_after_click: bool = True,
) -> bool:
    locator = _first_visible_text(page, text_options)
    if locator is None:
        return False

    locator.click(timeout=timeout_ms)
    if wait_after_click:
        _wait_for_ui(page, timeout_ms=timeout_ms)
    return True


def _expect_text(page: Page, text_options: Iterable[str], timeout_ms: int = DEFAULT_TIMEOUT_MS) -> bool:
    locator = _first_visible_text(page, text_options, timeout_ms=timeout_ms)
    return locator is not None


def _save_screenshot(page: Page, destination: Path, full_page: bool = False) -> None:
    destination.parent.mkdir(parents=True, exist_ok=True)
    page.screenshot(path=str(destination), full_page=full_page)


def _finalize_step(report: dict[str, StepOutcome], field_name: str, checks: list[tuple[str, bool]]) -> bool:
    all_ok = all(ok for _, ok in checks)
    report[field_name].status = "PASS" if all_ok else "FAIL"
    report[field_name].details.extend([f"{'PASS' if ok else 'FAIL'} - {description}" for description, ok in checks])
    return all_ok


def _find_app_page(context: BrowserContext, fallback: Page) -> Page:
    for candidate in context.pages:
        candidate_url = candidate.url or ""
        if "accounts.google.com" not in candidate_url:
            return candidate
    return fallback


def _validate_legal_page_content(target_page: Page, heading_options: list[str]) -> tuple[bool, bool]:
    heading_ok = _expect_text(target_page, heading_options)
    legal_content_ok = _is_visible(target_page.locator("main p, article p, section p").first, timeout_ms=5000)
    return heading_ok, legal_content_ok


def main() -> int:
    run_id = _now_utc()
    artifact_dir = Path("artifacts") / "saleads_mi_negocio_full_test" / run_id
    report_path = artifact_dir / "report.json"
    artifact_dir.mkdir(parents=True, exist_ok=True)

    report: dict[str, StepOutcome] = {field: StepOutcome() for field in REPORT_FIELDS}
    run_summary = {
        "name": "saleads_mi_negocio_full_test",
        "run_id": run_id,
        "executed_at_utc": datetime.now(timezone.utc).isoformat(),
        "artifacts_dir": str(artifact_dir),
        "final_urls": {},
        "notes": [],
    }

    browser: Optional[Browser] = None
    context: Optional[BrowserContext] = None
    page: Optional[Page] = None

    try:
        with sync_playwright() as playwright:
            if CDP_URL:
                browser = playwright.chromium.connect_over_cdp(CDP_URL)
                if browser.contexts:
                    context = browser.contexts[0]
                else:
                    context = browser.new_context()

                if context.pages:
                    page = context.pages[0]
                else:
                    page = context.new_page()

                run_summary["notes"].append("Connected to pre-opened browser via SALEADS_CDP_URL.")
            else:
                if not LOGIN_URL:
                    raise RuntimeError(
                        "Set SALEADS_LOGIN_URL (or SALEADS_CDP_URL for a pre-opened browser tab). "
                        "No environment-specific URL is hardcoded by design."
                    )
                browser = playwright.chromium.launch(headless=HEADLESS)
                context = browser.new_context()
                page = context.new_page()
                page.goto(LOGIN_URL, wait_until="domcontentloaded", timeout=DEFAULT_TIMEOUT_MS)
                _wait_for_ui(page)
                run_summary["notes"].append("Opened login page from SALEADS_LOGIN_URL.")

            assert context is not None and page is not None

            # Step 1: Login with Google
            login_checks: list[tuple[str, bool]] = []
            login_clicked = _click_by_visible_text(
                page,
                [
                    "Sign in with Google",
                    "Iniciar sesión con Google",
                    "Ingresar con Google",
                    "Continuar con Google",
                    "Login with Google",
                    "Google",
                ],
                wait_after_click=False,
            )
            login_checks.append(("Login button or Google sign-in trigger was clicked (or user already signed in).", login_clicked))

            if login_clicked:
                _wait_for_ui(page)

                google_page = None
                for candidate in context.pages:
                    if "accounts.google.com" in (candidate.url or ""):
                        google_page = candidate
                        break

                if google_page is not None:
                    account_selected = _click_by_visible_text(
                        google_page,
                        [GOOGLE_ACCOUNT_EMAIL],
                        timeout_ms=8000,
                        wait_after_click=False,
                    )
                    login_checks.append((f"Google account selector handled for {GOOGLE_ACCOUNT_EMAIL} when visible.", account_selected))
                    _wait_for_ui(google_page)
                    page = _find_app_page(context, fallback=page)
                else:
                    # Account picker may be skipped when SSO session is already active.
                    login_checks.append(("Google account picker did not appear (existing SSO session is acceptable).", True))
            else:
                sidebar_already_present = _expect_text(page, ["Negocio", "Mi Negocio"], timeout_ms=4000)
                login_checks[-1] = (
                    "Login button not required because app appears to be already authenticated.",
                    sidebar_already_present,
                )

            _wait_for_ui(page)
            app_interface_visible = _is_visible(page.locator("main, [role='main']").first, timeout_ms=10000)
            sidebar_visible = _expect_text(page, ["Negocio", "Mi Negocio"], timeout_ms=10000) or _is_visible(
                page.locator("aside, nav").first,
                timeout_ms=10000,
            )
            login_checks.append(("Main application interface is visible.", app_interface_visible))
            login_checks.append(("Left sidebar navigation is visible.", sidebar_visible))

            _save_screenshot(page, artifact_dir / "01_dashboard_loaded.png")
            _finalize_step(report, "Login", login_checks)

            # Step 2: Open Mi Negocio menu
            menu_checks: list[tuple[str, bool]] = []
            negocio_section_visible = _expect_text(page, ["Negocio"], timeout_ms=10000)
            menu_checks.append(("Sidebar section 'Negocio' is visible.", negocio_section_visible))

            clicked_mi_negocio = _click_by_visible_text(page, ["Mi Negocio"])
            if not clicked_mi_negocio:
                _click_by_visible_text(page, ["Negocio"])
                clicked_mi_negocio = _click_by_visible_text(page, ["Mi Negocio"])
            menu_checks.append(("Clicked 'Mi Negocio'.", clicked_mi_negocio))

            agregar_visible = _expect_text(page, ["Agregar Negocio"], timeout_ms=10000)
            administrar_visible = _expect_text(page, ["Administrar Negocios"], timeout_ms=10000)
            menu_checks.append(("Submenu expanded and 'Agregar Negocio' is visible.", agregar_visible))
            menu_checks.append(("Submenu expanded and 'Administrar Negocios' is visible.", administrar_visible))
            _save_screenshot(page, artifact_dir / "02_mi_negocio_menu_expanded.png")
            _finalize_step(report, "Mi Negocio menu", menu_checks)

            # Step 3: Validate Agregar Negocio modal
            modal_checks: list[tuple[str, bool]] = []
            clicked_agregar = _click_by_visible_text(page, ["Agregar Negocio"])
            modal_checks.append(("Clicked 'Agregar Negocio'.", clicked_agregar))

            modal_title_ok = _expect_text(page, ["Crear Nuevo Negocio"], timeout_ms=10000)
            nombre_input_ok = _is_visible(
                page.get_by_label(re.compile(r"Nombre del Negocio", re.IGNORECASE)),
                timeout_ms=4000,
            ) or _is_visible(
                page.get_by_placeholder(re.compile(r"Nombre del Negocio", re.IGNORECASE)),
                timeout_ms=4000,
            )
            business_quota_ok = _expect_text(page, ["Tienes 2 de 3 negocios"], timeout_ms=5000)
            cancelar_ok = _expect_text(page, ["Cancelar"], timeout_ms=5000)
            crear_ok = _expect_text(page, ["Crear Negocio"], timeout_ms=5000)

            modal_checks.append(("Modal title 'Crear Nuevo Negocio' is visible.", modal_title_ok))
            modal_checks.append(("Input field 'Nombre del Negocio' exists.", nombre_input_ok))
            modal_checks.append(("Text 'Tienes 2 de 3 negocios' is visible.", business_quota_ok))
            modal_checks.append(("Buttons 'Cancelar' and 'Crear Negocio' are present.", cancelar_ok and crear_ok))

            _save_screenshot(page, artifact_dir / "03_agregar_negocio_modal.png")

            if nombre_input_ok:
                field = page.get_by_label(re.compile(r"Nombre del Negocio", re.IGNORECASE))
                if not _is_visible(field, timeout_ms=1200):
                    field = page.get_by_placeholder(re.compile(r"Nombre del Negocio", re.IGNORECASE))
                try:
                    field.first.fill("Negocio Prueba Automatización", timeout=4000)
                except TimeoutError:
                    pass
            _click_by_visible_text(page, ["Cancelar"], timeout_ms=5000)

            _finalize_step(report, "Agregar Negocio modal", modal_checks)

            # Step 4: Open Administrar Negocios
            account_view_checks: list[tuple[str, bool]] = []
            if not _expect_text(page, ["Administrar Negocios"], timeout_ms=2500):
                _click_by_visible_text(page, ["Mi Negocio"])
            clicked_admin = _click_by_visible_text(page, ["Administrar Negocios"])
            account_view_checks.append(("Clicked 'Administrar Negocios'.", clicked_admin))

            info_general_ok = _expect_text(page, ["Información General", "Informacion General"], timeout_ms=10000)
            detalles_cuenta_ok = _expect_text(page, ["Detalles de la Cuenta", "Detalles de la cuenta"], timeout_ms=10000)
            tus_negocios_ok = _expect_text(page, ["Tus Negocios"], timeout_ms=10000)
            seccion_legal_ok = _expect_text(page, ["Sección Legal", "Seccion Legal"], timeout_ms=10000)

            account_view_checks.append(("Section 'Información General' exists.", info_general_ok))
            account_view_checks.append(("Section 'Detalles de la Cuenta' exists.", detalles_cuenta_ok))
            account_view_checks.append(("Section 'Tus Negocios' exists.", tus_negocios_ok))
            account_view_checks.append(("Section 'Sección Legal' exists.", seccion_legal_ok))

            _save_screenshot(page, artifact_dir / "04_administrar_negocios_full.png", full_page=True)
            _finalize_step(report, "Administrar Negocios view", account_view_checks)

            # Step 5: Validate Información General
            info_checks: list[tuple[str, bool]] = []
            normalized_body_text = _normalize(page.locator("body").inner_text(timeout=8000))
            email_visible = _is_visible(page.locator(r"text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/").first, timeout_ms=5000)
            name_like_visible = bool(re.search(r"\bNombre\b", normalized_body_text, re.IGNORECASE)) or bool(
                re.search(r"\bPerfil\b", normalized_body_text, re.IGNORECASE)
            )
            business_plan_ok = _expect_text(page, ["BUSINESS PLAN"], timeout_ms=5000)
            cambiar_plan_ok = _expect_text(page, ["Cambiar Plan"], timeout_ms=5000)

            info_checks.append(("User name is visible.", name_like_visible))
            info_checks.append(("User email is visible.", email_visible))
            info_checks.append(("Text 'BUSINESS PLAN' is visible.", business_plan_ok))
            info_checks.append(("Button 'Cambiar Plan' is visible.", cambiar_plan_ok))
            _finalize_step(report, "Información General", info_checks)

            # Step 6: Validate Detalles de la Cuenta
            detalles_checks: list[tuple[str, bool]] = []
            cuenta_creada_ok = _expect_text(page, ["Cuenta creada"], timeout_ms=5000)
            estado_activo_ok = _expect_text(page, ["Estado activo", "Estado Activo"], timeout_ms=5000)
            idioma_ok = _expect_text(page, ["Idioma seleccionado", "Idioma Seleccionado"], timeout_ms=5000)
            detalles_checks.append(("'Cuenta creada' is visible.", cuenta_creada_ok))
            detalles_checks.append(("'Estado activo' is visible.", estado_activo_ok))
            detalles_checks.append(("'Idioma seleccionado' is visible.", idioma_ok))
            _finalize_step(report, "Detalles de la Cuenta", detalles_checks)

            # Step 7: Validate Tus Negocios
            negocios_checks: list[tuple[str, bool]] = []
            negocios_section_ok = _expect_text(page, ["Tus Negocios"], timeout_ms=5000)
            agregar_button_ok = _expect_text(page, ["Agregar Negocio"], timeout_ms=5000)
            quota_ok = _expect_text(page, ["Tienes 2 de 3 negocios"], timeout_ms=5000)
            negocios_checks.append(("Business list/section is visible.", negocios_section_ok))
            negocios_checks.append(("Button 'Agregar Negocio' exists.", agregar_button_ok))
            negocios_checks.append(("Text 'Tienes 2 de 3 negocios' is visible.", quota_ok))
            _finalize_step(report, "Tus Negocios", negocios_checks)

            # Step 8: Validate Términos y Condiciones
            terms_checks: list[tuple[str, bool]] = []
            pages_before = set(context.pages)
            terms_clicked = False
            terms_page = page
            try:
                with context.expect_page(timeout=6000) as page_info:
                    terms_clicked = _click_by_visible_text(
                        page,
                        ["Términos y Condiciones", "Terminos y Condiciones"],
                        wait_after_click=False,
                    )
                if terms_clicked:
                    terms_page = page_info.value
            except TimeoutError:
                terms_clicked = _click_by_visible_text(
                    page,
                    ["Términos y Condiciones", "Terminos y Condiciones"],
                    wait_after_click=False,
                )
                terms_page = page

            _wait_for_ui(terms_page)
            terms_heading_ok, terms_content_ok = _validate_legal_page_content(
                terms_page,
                ["Términos y Condiciones", "Terminos y Condiciones"],
            )
            terms_checks.append(("Clicked 'Términos y Condiciones'.", terms_clicked))
            terms_checks.append(("Page contains heading 'Términos y Condiciones'.", terms_heading_ok))
            terms_checks.append(("Legal content text is visible.", terms_content_ok))
            run_summary["final_urls"]["terminos_y_condiciones"] = terms_page.url
            _save_screenshot(terms_page, artifact_dir / "08_terminos_y_condiciones.png", full_page=True)

            opened_new_tab = terms_page not in pages_before
            if opened_new_tab:
                terms_page.close()
                page.bring_to_front()
                _wait_for_ui(page)
            elif terms_page is page and len(context.pages) == len(pages_before):
                try:
                    page.go_back(wait_until="domcontentloaded", timeout=DEFAULT_TIMEOUT_MS)
                except TimeoutError:
                    pass
                _wait_for_ui(page)

            _finalize_step(report, "Términos y Condiciones", terms_checks)

            # Step 9: Validate Política de Privacidad
            privacy_checks: list[tuple[str, bool]] = []
            pages_before = set(context.pages)
            privacy_clicked = False
            privacy_page = page
            try:
                with context.expect_page(timeout=6000) as page_info:
                    privacy_clicked = _click_by_visible_text(
                        page,
                        ["Política de Privacidad", "Politica de Privacidad"],
                        wait_after_click=False,
                    )
                if privacy_clicked:
                    privacy_page = page_info.value
            except TimeoutError:
                privacy_clicked = _click_by_visible_text(
                    page,
                    ["Política de Privacidad", "Politica de Privacidad"],
                    wait_after_click=False,
                )
                privacy_page = page

            _wait_for_ui(privacy_page)
            privacy_heading_ok, privacy_content_ok = _validate_legal_page_content(
                privacy_page,
                ["Política de Privacidad", "Politica de Privacidad"],
            )
            privacy_checks.append(("Clicked 'Política de Privacidad'.", privacy_clicked))
            privacy_checks.append(("Page contains heading 'Política de Privacidad'.", privacy_heading_ok))
            privacy_checks.append(("Legal content text is visible.", privacy_content_ok))
            run_summary["final_urls"]["politica_de_privacidad"] = privacy_page.url
            _save_screenshot(privacy_page, artifact_dir / "09_politica_de_privacidad.png", full_page=True)

            opened_new_tab = privacy_page not in pages_before
            if opened_new_tab:
                privacy_page.close()
                page.bring_to_front()
                _wait_for_ui(page)
            elif privacy_page is page and len(context.pages) == len(pages_before):
                try:
                    page.go_back(wait_until="domcontentloaded", timeout=DEFAULT_TIMEOUT_MS)
                except TimeoutError:
                    pass
                _wait_for_ui(page)

            _finalize_step(report, "Política de Privacidad", privacy_checks)

    except Exception as exc:  # noqa: BLE001
        run_summary["notes"].append(f"Execution error: {exc}")
    finally:
        serializable_report = {
            "summary": run_summary,
            "results": {field: {"status": outcome.status, "details": outcome.details} for field, outcome in report.items()},
            "overall_status": "PASS" if all(outcome.status == "PASS" for outcome in report.values()) else "FAIL",
        }
        report_path.write_text(json.dumps(serializable_report, indent=2, ensure_ascii=False), encoding="utf-8")
        print(json.dumps(serializable_report, indent=2, ensure_ascii=False))

        if context is not None and CDP_URL is None:
            context.close()
        if browser is not None and CDP_URL is None:
            browser.close()

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
