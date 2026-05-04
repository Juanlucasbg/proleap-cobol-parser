#!/usr/bin/env python3
"""
SaleADS Mi Negocio full workflow validation.

This script validates the workflow requested in the automation payload:
1) Login with Google
2) Mi Negocio menu expansion
3) Agregar Negocio modal
4) Administrar Negocios account page
5) Informacion General
6) Detalles de la Cuenta
7) Tus Negocios
8) Terminos y Condiciones legal page
9) Politica de Privacidad legal page
10) Final PASS/FAIL report
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
import unicodedata
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional, Sequence

from playwright.sync_api import Browser, BrowserContext, Locator, Page, TimeoutError, sync_playwright


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
class StepReport:
    name: str
    passed: bool
    details: List[str]
    screenshot: Optional[str] = None
    url: Optional[str] = None


class WorkflowRunner:
    def __init__(self, context: BrowserContext, page: Page, artifacts_dir: Path, google_email: str) -> None:
        self.context = context
        self.page = page
        self.artifacts_dir = artifacts_dir
        self.google_email = google_email
        self.reports: Dict[str, StepReport] = {}
        self.legal_urls: Dict[str, str] = {}

    def run(self) -> Dict[str, StepReport]:
        self.reports["Login"] = self.step_login_with_google()
        self.reports["Mi Negocio menu"] = self.step_open_mi_negocio_menu()
        self.reports["Agregar Negocio modal"] = self.step_validate_agregar_negocio_modal()
        self.reports["Administrar Negocios view"] = self.step_open_administrar_negocios()
        self.reports["Información General"] = self.step_validate_informacion_general()
        self.reports["Detalles de la Cuenta"] = self.step_validate_detalles_cuenta()
        self.reports["Tus Negocios"] = self.step_validate_tus_negocios()
        self.reports["Términos y Condiciones"] = self.step_validate_terminos()
        self.reports["Política de Privacidad"] = self.step_validate_politica()
        return self.reports

    # -------- helpers --------

    def _wait_for_ui(self, page: Optional[Page] = None) -> None:
        target = page or self.page
        for state in ("domcontentloaded", "networkidle"):
            try:
                target.wait_for_load_state(state, timeout=10_000)
            except TimeoutError:
                pass
        target.wait_for_timeout(750)

    def _screenshot(self, name: str, *, full_page: bool = False, page: Optional[Page] = None) -> str:
        target = page or self.page
        file_path = self.artifacts_dir / f"{name}.png"
        target.screenshot(path=str(file_path), full_page=full_page)
        return str(file_path)

    def _safe_is_visible(self, locator: Locator, timeout: int = 3500) -> bool:
        try:
            return locator.first.is_visible(timeout=timeout)
        except Exception:
            return False

    def _first_visible(self, candidates: Sequence[Locator]) -> Optional[Locator]:
        for candidate in candidates:
            if self._safe_is_visible(candidate):
                return candidate.first
        return None

    def _find_clickable_by_text(self, labels: Sequence[str] | str) -> Optional[Locator]:
        text_candidates = [labels] if isinstance(labels, str) else list(labels)
        for label in text_candidates:
            exact_pattern = re.compile(rf"^{re.escape(label)}$", re.IGNORECASE)
            contains_pattern = re.compile(re.escape(label), re.IGNORECASE)
            candidates = [
                self.page.get_by_role("button", name=exact_pattern),
                self.page.get_by_role("button", name=contains_pattern),
                self.page.get_by_role("link", name=exact_pattern),
                self.page.get_by_role("link", name=contains_pattern),
                self.page.get_by_role("menuitem", name=exact_pattern),
                self.page.get_by_role("menuitem", name=contains_pattern),
                self.page.get_by_text(exact_pattern),
                self.page.get_by_text(contains_pattern),
            ]
            found = self._first_visible(candidates)
            if found is not None:
                return found
        return None

    def _click_and_wait(self, locator: Locator, *, page: Optional[Page] = None) -> None:
        target = page or self.page
        locator.scroll_into_view_if_needed(timeout=10_000)
        locator.click(timeout=12_000)
        self._wait_for_ui(target)

    def _text_visible(self, text: str, *, timeout: int = 5000, page: Optional[Page] = None) -> bool:
        target = page or self.page
        pattern = re.compile(re.escape(text), re.IGNORECASE)
        locator = target.get_by_text(pattern)
        return self._safe_is_visible(locator, timeout=timeout)

    def _normalize_text(self, value: str) -> str:
        normalized = unicodedata.normalize("NFKD", value)
        no_accents = "".join(char for char in normalized if not unicodedata.combining(char))
        return no_accents.casefold()

    def _text_visible_any(
        self, texts: Sequence[str], *, timeout: int = 5000, page: Optional[Page] = None
    ) -> bool:
        target = page or self.page
        for text in texts:
            if self._text_visible(text, timeout=timeout, page=target):
                return True

        try:
            body_text = target.inner_text("body")
        except Exception:
            return False
        normalized_body = self._normalize_text(body_text)
        return any(self._normalize_text(text) in normalized_body for text in texts)

    def _ensure_on_app_page(self, timeout_seconds: int = 90) -> Optional[Page]:
        deadline = time.time() + timeout_seconds
        while time.time() < deadline:
            for p in self.context.pages:
                if self._safe_is_visible(p.locator("aside"), timeout=800):
                    self.page = p
                    return p
                if self._safe_is_visible(p.get_by_role("navigation"), timeout=800):
                    self.page = p
                    return p
                if self._safe_is_visible(p.get_by_text(re.compile(r"Negocio", re.IGNORECASE)), timeout=800):
                    self.page = p
                    return p
            time.sleep(1.0)
        return None

    # -------- step implementations --------

    def step_login_with_google(self) -> StepReport:
        details: List[str] = []
        screenshot: Optional[str] = None

        login_candidates = [
            "Sign in with Google",
            "Iniciar con Google",
            "Continuar con Google",
            "Login with Google",
            "Google",
        ]

        login_button: Optional[Locator] = None
        for label in login_candidates:
            login_button = self._find_clickable_by_text(label)
            if login_button is not None:
                details.append(f"Found login trigger using text: '{label}'.")
                break

        if login_button is None:
            return StepReport(
                name="Login",
                passed=False,
                details=["Could not find a Google login button by visible text."],
            )

        popup: Optional[Page] = None
        try:
            with self.page.expect_popup(timeout=7000) as popup_info:
                self._click_and_wait(login_button)
            popup = popup_info.value
            self._wait_for_ui(popup)
            details.append("Google login opened in popup.")
        except TimeoutError:
            details.append("No popup opened; continuing in current tab.")

        auth_page = popup or self.page
        account_locator = auth_page.get_by_text(self.google_email, exact=True)
        if self._safe_is_visible(account_locator, timeout=7000):
            self._click_and_wait(account_locator, page=auth_page)
            details.append(f"Selected Google account '{self.google_email}'.")
        else:
            details.append(
                f"Google account selector for '{self.google_email}' was not visible; session may already be authenticated."
            )

        app_page = self._ensure_on_app_page(timeout_seconds=120)
        if app_page is None:
            return StepReport(
                name="Login",
                passed=False,
                details=details
                + [
                    "Main application interface/sidebar did not appear after login flow.",
                ],
            )

        self._wait_for_ui(app_page)
        dashboard_ok = self._safe_is_visible(app_page.locator("main"), timeout=4000) or self._safe_is_visible(
            app_page.get_by_role("main"), timeout=4000
        )
        sidebar_ok = self._safe_is_visible(app_page.locator("aside"), timeout=4000) or self._safe_is_visible(
            app_page.get_by_role("navigation"), timeout=4000
        )

        screenshot = self._screenshot("01_dashboard_loaded", page=app_page)
        details.append("Captured dashboard screenshot.")
        details.append(f"Sidebar visible: {sidebar_ok}.")
        details.append(f"Main interface visible: {dashboard_ok}.")

        return StepReport(name="Login", passed=dashboard_ok and sidebar_ok, details=details, screenshot=screenshot)

    def step_open_mi_negocio_menu(self) -> StepReport:
        details: List[str] = []
        screenshot: Optional[str] = None

        negocio_section = self._find_clickable_by_text("Negocio")
        if negocio_section is None and not self._text_visible_any(["Negocio"]):
            return StepReport(
                name="Mi Negocio menu",
                passed=False,
                details=["Could not confirm 'Negocio' section in left sidebar."],
            )

        mi_negocio = self._find_clickable_by_text("Mi Negocio")
        if mi_negocio is None:
            return StepReport(
                name="Mi Negocio menu",
                passed=False,
                details=["Could not find 'Mi Negocio' option in sidebar."],
            )

        self._click_and_wait(mi_negocio)
        details.append("Clicked 'Mi Negocio'.")

        agregar_visible = self._text_visible_any(["Agregar Negocio"], timeout=7000)
        administrar_visible = self._text_visible_any(["Administrar Negocios"], timeout=7000)
        submenu_expanded = agregar_visible and administrar_visible

        details.append(f"'Agregar Negocio' visible: {agregar_visible}.")
        details.append(f"'Administrar Negocios' visible: {administrar_visible}.")
        screenshot = self._screenshot("02_mi_negocio_menu_expanded")
        details.append("Captured expanded menu screenshot.")

        return StepReport(
            name="Mi Negocio menu",
            passed=submenu_expanded,
            details=details,
            screenshot=screenshot,
        )

    def step_validate_agregar_negocio_modal(self) -> StepReport:
        details: List[str] = []
        screenshot: Optional[str] = None

        agregar_negocio = self._find_clickable_by_text("Agregar Negocio")
        if agregar_negocio is None:
            return StepReport(
                name="Agregar Negocio modal",
                passed=False,
                details=["Could not find 'Agregar Negocio' option to open modal."],
            )

        self._click_and_wait(agregar_negocio)
        details.append("Clicked 'Agregar Negocio'.")

        title_ok = self._text_visible_any(["Crear Nuevo Negocio"], timeout=7000)
        name_field = self.page.get_by_label(re.compile(r"Nombre del Negocio", re.IGNORECASE))
        if not self._safe_is_visible(name_field, timeout=3500):
            # fallback when label association is missing
            name_field = self.page.get_by_placeholder(re.compile(r"Nombre del Negocio", re.IGNORECASE))
        name_ok = self._safe_is_visible(name_field, timeout=3500) or self._safe_is_visible(
            self.page.get_by_text(re.compile(r"Nombre del Negocio", re.IGNORECASE)), timeout=3500
        )
        counter_ok = self._text_visible_any(["Tienes 2 de 3 negocios"], timeout=5000)
        cancel_ok = self._safe_is_visible(self.page.get_by_role("button", name=re.compile(r"^Cancelar$", re.IGNORECASE)))
        create_ok = self._safe_is_visible(
            self.page.get_by_role("button", name=re.compile(r"^Crear Negocio$", re.IGNORECASE))
        )

        details.append(f"Modal title visible: {title_ok}.")
        details.append(f"'Nombre del Negocio' field visible: {name_ok}.")
        details.append(f"'Tienes 2 de 3 negocios' visible: {counter_ok}.")
        details.append(f"'Cancelar' button visible: {cancel_ok}.")
        details.append(f"'Crear Negocio' button visible: {create_ok}.")

        screenshot = self._screenshot("03_agregar_negocio_modal")
        details.append("Captured modal screenshot.")

        if self._safe_is_visible(name_field, timeout=2000):
            name_field.click(timeout=5000)
            self._wait_for_ui()
            name_field.fill("Negocio Prueba Automatización", timeout=5000)
            self._wait_for_ui()
            details.append("Filled 'Nombre del Negocio' with optional test value.")

        cancel_button = self.page.get_by_role("button", name=re.compile(r"^Cancelar$", re.IGNORECASE))
        if self._safe_is_visible(cancel_button, timeout=3000):
            self._click_and_wait(cancel_button)
            details.append("Closed modal with 'Cancelar'.")

        passed = title_ok and name_ok and counter_ok and cancel_ok and create_ok
        return StepReport(name="Agregar Negocio modal", passed=passed, details=details, screenshot=screenshot)

    def step_open_administrar_negocios(self) -> StepReport:
        details: List[str] = []
        screenshot: Optional[str] = None

        administrar = self._find_clickable_by_text("Administrar Negocios")
        if administrar is None:
            mi_negocio = self._find_clickable_by_text("Mi Negocio")
            if mi_negocio is not None:
                self._click_and_wait(mi_negocio)
                details.append("Expanded 'Mi Negocio' again.")
            administrar = self._find_clickable_by_text("Administrar Negocios")

        if administrar is None:
            return StepReport(
                name="Administrar Negocios view",
                passed=False,
                details=["Could not find 'Administrar Negocios' option."],
            )

        self._click_and_wait(administrar)
        details.append("Opened 'Administrar Negocios'.")

        info_ok = self._text_visible_any(["Información General", "Informacion General"], timeout=8000)
        detalles_ok = self._text_visible_any(["Detalles de la Cuenta"], timeout=8000)
        negocios_ok = self._text_visible_any(["Tus Negocios"], timeout=8000)
        legal_ok = self._text_visible_any(["Sección Legal", "Seccion Legal"], timeout=8000)

        details.append(f"'Información General' visible: {info_ok}.")
        details.append(f"'Detalles de la Cuenta' visible: {detalles_ok}.")
        details.append(f"'Tus Negocios' visible: {negocios_ok}.")
        details.append(f"'Sección Legal' visible: {legal_ok}.")

        screenshot = self._screenshot("04_administrar_negocios", full_page=True)
        details.append("Captured full screenshot of account page.")

        return StepReport(
            name="Administrar Negocios view",
            passed=info_ok and detalles_ok and negocios_ok and legal_ok,
            details=details,
            screenshot=screenshot,
        )

    def step_validate_informacion_general(self) -> StepReport:
        details: List[str] = []
        email_pattern = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
        page_text = self.page.inner_text("body")

        email_match = email_pattern.search(page_text)
        email_ok = email_match is not None
        business_plan_ok = self._text_visible_any(["BUSINESS PLAN"], timeout=5000)
        change_plan_ok = self._safe_is_visible(
            self.page.get_by_role("button", name=re.compile(r"Cambiar Plan", re.IGNORECASE)), timeout=5000
        ) or self._safe_is_visible(
            self.page.get_by_role("link", name=re.compile(r"Cambiar Plan", re.IGNORECASE)), timeout=5000
        )

        # Best-effort user-name detection with label filtering.
        line_candidates = [line.strip() for line in page_text.splitlines() if line.strip()]
        label_tokens = {
            "información general",
            "informacion general",
            "detalles de la cuenta",
            "tus negocios",
            "sección legal",
            "seccion legal",
            "business plan",
            "cambiar plan",
            "cuenta creada",
            "estado activo",
            "idioma seleccionado",
        }
        username_ok = False
        for line in line_candidates:
            lower = line.lower()
            if any(token in lower for token in label_tokens):
                continue
            if "@" in line:
                continue
            if re.match(r"^[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)+$", line):
                username_ok = True
                break

        details.append(f"User name visible (best effort): {username_ok}.")
        details.append(f"User email visible: {email_ok}.")
        if email_match:
            details.append(f"Detected email: {email_match.group(0)}.")
        details.append(f"'BUSINESS PLAN' visible: {business_plan_ok}.")
        details.append(f"'Cambiar Plan' visible: {change_plan_ok}.")

        return StepReport(
            name="Información General",
            passed=username_ok and email_ok and business_plan_ok and change_plan_ok,
            details=details,
        )

    def step_validate_detalles_cuenta(self) -> StepReport:
        details: List[str] = []
        cuenta_ok = self._text_visible_any(["Cuenta creada"], timeout=5000)
        estado_ok = self._text_visible_any(["Estado activo"], timeout=5000)
        idioma_ok = self._text_visible_any(["Idioma seleccionado"], timeout=5000)

        details.append(f"'Cuenta creada' visible: {cuenta_ok}.")
        details.append(f"'Estado activo' visible: {estado_ok}.")
        details.append(f"'Idioma seleccionado' visible: {idioma_ok}.")

        return StepReport(
            name="Detalles de la Cuenta",
            passed=cuenta_ok and estado_ok and idioma_ok,
            details=details,
        )

    def step_validate_tus_negocios(self) -> StepReport:
        details: List[str] = []
        heading_ok = self._text_visible_any(["Tus Negocios"], timeout=5000)
        add_button_ok = self._safe_is_visible(
            self.page.get_by_role("button", name=re.compile(r"Agregar Negocio", re.IGNORECASE)),
            timeout=5000,
        ) or self._safe_is_visible(
            self.page.get_by_role("link", name=re.compile(r"Agregar Negocio", re.IGNORECASE)),
            timeout=5000,
        )
        counter_ok = self._text_visible_any(["Tienes 2 de 3 negocios"], timeout=5000)

        # Best effort list check: at least one row/card-like container with "Negocio" text.
        business_item_ok = False
        negocio_loc = self.page.get_by_text(re.compile(r"Negocio", re.IGNORECASE))
        try:
            business_item_ok = negocio_loc.count() > 0
        except Exception:
            business_item_ok = False

        details.append(f"'Tus Negocios' heading visible: {heading_ok}.")
        details.append(f"Business list visible (best effort): {business_item_ok}.")
        details.append(f"'Agregar Negocio' exists: {add_button_ok}.")
        details.append(f"'Tienes 2 de 3 negocios' visible: {counter_ok}.")

        return StepReport(
            name="Tus Negocios",
            passed=heading_ok and business_item_ok and add_button_ok and counter_ok,
            details=details,
        )

    def _validate_legal_link(
        self, link_texts: Sequence[str], heading_texts: Sequence[str], report_name: str, screenshot_name: str
    ) -> StepReport:
        details: List[str] = []
        screenshot: Optional[str] = None

        legal_link = self._find_clickable_by_text(link_texts)
        if legal_link is None:
            return StepReport(
                name=report_name,
                passed=False,
                details=[f"Could not find legal link with labels: {', '.join(link_texts)}."],
            )

        original_page = self.page
        original_url = original_page.url
        target_page: Page = original_page

        popup_page: Optional[Page] = None
        try:
            with original_page.expect_popup(timeout=8000) as popup_info:
                self._click_and_wait(legal_link, page=original_page)
            popup_page = popup_info.value
            target_page = popup_page
            self._wait_for_ui(target_page)
            details.append(f"'{link_texts[0]}' opened in new tab.")
        except TimeoutError:
            details.append(f"'{link_texts[0]}' opened in current tab (or no popup).")

        heading_ok = False
        for heading_text in heading_texts:
            heading_pattern = re.compile(re.escape(heading_text), re.IGNORECASE)
            if self._safe_is_visible(target_page.get_by_role("heading", name=heading_pattern), timeout=7000) or (
                self._safe_is_visible(target_page.get_by_text(heading_pattern), timeout=7000)
            ):
                heading_ok = True
                break
        if not heading_ok:
            heading_ok = self._text_visible_any(heading_texts, timeout=7000, page=target_page)
        legal_content_ok = self._safe_is_visible(target_page.locator("p"), timeout=7000) or self._safe_is_visible(
            target_page.locator("article"), timeout=7000
        ) or self._safe_is_visible(target_page.locator("main"), timeout=7000)

        screenshot = self._screenshot(screenshot_name, page=target_page, full_page=True)
        final_url = target_page.url
        self.legal_urls[report_name] = final_url

        details.append(f"Heading '{report_name}' visible: {heading_ok}.")
        details.append(f"Legal content visible: {legal_content_ok}.")
        details.append(f"Captured legal page screenshot: {screenshot}.")
        details.append(f"Final URL: {final_url}.")

        if popup_page is not None:
            popup_page.close()
            original_page.bring_to_front()
            self._wait_for_ui(original_page)
            details.append("Closed legal tab and returned to app tab.")
        else:
            if target_page.url != original_url:
                target_page.go_back(wait_until="domcontentloaded", timeout=12_000)
                self._wait_for_ui(target_page)
            details.append("Returned to application page in same tab.")

        self.page = original_page
        return StepReport(
            name=report_name,
            passed=heading_ok and legal_content_ok,
            details=details,
            screenshot=screenshot,
            url=final_url,
        )

    def step_validate_terminos(self) -> StepReport:
        return self._validate_legal_link(
            link_texts=["Términos y Condiciones", "Terminos y Condiciones"],
            heading_texts=["Términos y Condiciones", "Terminos y Condiciones"],
            report_name="Términos y Condiciones",
            screenshot_name="05_terminos_y_condiciones",
        )

    def step_validate_politica(self) -> StepReport:
        return self._validate_legal_link(
            link_texts=["Política de Privacidad", "Politica de Privacidad"],
            heading_texts=["Política de Privacidad", "Politica de Privacidad"],
            report_name="Política de Privacidad",
            screenshot_name="06_politica_de_privacidad",
        )


def create_artifacts_dir(base_dir: Path) -> Path:
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    artifacts = base_dir / "artifacts" / f"saleads_mi_negocio_{timestamp}"
    artifacts.mkdir(parents=True, exist_ok=True)
    return artifacts


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run SaleADS Mi Negocio full workflow validation.")
    parser.add_argument(
        "--start-url",
        default=os.getenv("SALEADS_START_URL"),
        help="Optional login URL for the active SaleADS environment. If omitted, requires --cdp-url.",
    )
    parser.add_argument(
        "--cdp-url",
        default=os.getenv("SALEADS_CDP_URL"),
        help="Optional Chrome DevTools endpoint to attach to an existing browser page already on login screen.",
    )
    parser.add_argument(
        "--google-email",
        default=os.getenv("SALEADS_GOOGLE_ACCOUNT_EMAIL", "juanlucasbarbiergarzon@gmail.com"),
        help="Google account to select if account chooser appears.",
    )
    parser.add_argument(
        "--headless",
        action="store_true",
        default=os.getenv("SALEADS_HEADLESS", "false").lower() == "true",
        help="Run in headless mode (default false).",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    repo_root = Path(__file__).resolve().parents[2]
    qa_dir = repo_root / "qa"
    artifacts_dir = create_artifacts_dir(qa_dir)

    browser: Optional[Browser] = None
    context: Optional[BrowserContext] = None
    page: Optional[Page] = None

    with sync_playwright() as p:
        if args.cdp_url:
            browser = p.chromium.connect_over_cdp(args.cdp_url)
            if not browser.contexts:
                print("No contexts found in connected browser.", file=sys.stderr)
                return 2
            context = browser.contexts[0]
            if not context.pages:
                page = context.new_page()
            else:
                page = context.pages[0]
        else:
            browser = p.chromium.launch(headless=args.headless)
            context = browser.new_context()
            page = context.new_page()
            if not args.start_url:
                print(
                    "Either --start-url (or SALEADS_START_URL) or --cdp-url (or SALEADS_CDP_URL) is required.",
                    file=sys.stderr,
                )
                return 2
            page.goto(args.start_url, wait_until="domcontentloaded")

        runner = WorkflowRunner(context=context, page=page, artifacts_dir=artifacts_dir, google_email=args.google_email)
        reports = runner.run()

        summary_payload = {
            "workflow": "saleads_mi_negocio_full_test",
            "generated_at_utc": datetime.now(timezone.utc).isoformat(),
            "artifacts_dir": str(artifacts_dir),
            "results": {name: asdict(report) for name, report in reports.items()},
            "all_passed": all(report.passed for report in reports.values()),
            "legal_urls": runner.legal_urls,
        }
        report_file = artifacts_dir / "report.json"
        report_file.write_text(json.dumps(summary_payload, indent=2, ensure_ascii=False), encoding="utf-8")

        print("Final Report")
        print("============")
        for field in REPORT_FIELDS:
            report = reports.get(field)
            if report is None:
                print(f"- {field}: FAIL (missing report)")
                continue
            status = "PASS" if report.passed else "FAIL"
            print(f"- {field}: {status}")
        for legal_key in ("Términos y Condiciones", "Política de Privacidad"):
            if legal_key in runner.legal_urls:
                print(f"  URL {legal_key}: {runner.legal_urls[legal_key]}")
        print(f"Artifacts: {artifacts_dir}")
        print(f"Report JSON: {report_file}")

        # Disconnect from CDP without closing user browser.
        if args.cdp_url:
            browser.close()
        else:
            context.close()
            browser.close()

        return 0 if summary_payload["all_passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
