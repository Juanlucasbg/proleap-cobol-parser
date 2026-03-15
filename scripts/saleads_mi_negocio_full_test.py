#!/usr/bin/env python3
"""SaleADS Mi Negocio full workflow validation.

This script automates the following workflow:
1. Login with Google.
2. Open Mi Negocio menu.
3. Validate Agregar Negocio modal.
4. Open Administrar Negocios.
5. Validate Informacion General.
6. Validate Detalles de la Cuenta.
7. Validate Tus Negocios.
8. Validate Terminos y Condiciones link (same tab or popup).
9. Validate Politica de Privacidad link (same tab or popup).

Design choices aligned with requirements:
- No hard-coded SaleADS domain is required.
- Selectors are primarily based on visible text/roles.
- Screenshots are captured at important checkpoints.
- New tab handling is supported and validated.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
from contextlib import contextmanager
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable

from playwright.sync_api import (
    Browser,
    BrowserContext,
    Error as PlaywrightError,
    Page,
    TimeoutError as PlaywrightTimeoutError,
    sync_playwright,
)


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


def slugify(text: str) -> str:
    normalized = re.sub(r"[^a-zA-Z0-9]+", "_", text.strip().lower())
    return normalized.strip("_") or "step"


def now_stamp() -> str:
    return time.strftime("%Y%m%d_%H%M%S")


@dataclass
class StepResult:
    status: str
    details: str = ""
    screenshot: str | None = None
    url: str | None = None


@dataclass
class WorkflowReport:
    run_name: str = "saleads_mi_negocio_full_test"
    started_at: str = field(default_factory=lambda: time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()))
    steps: dict[str, StepResult] = field(default_factory=dict)
    evidence_dir: str = ""

    def set_step(
        self,
        name: str,
        passed: bool,
        details: str = "",
        screenshot: str | None = None,
        url: str | None = None,
    ) -> None:
        self.steps[name] = StepResult(
            status="PASS" if passed else "FAIL",
            details=details,
            screenshot=screenshot,
            url=url,
        )

    def set_skipped(self, name: str, details: str = "") -> None:
        self.steps[name] = StepResult(status="SKIPPED", details=details)

    def all_passed(self) -> bool:
        return all(self.steps.get(name, StepResult("FAIL")).status == "PASS" for name in REPORT_FIELDS)

    def as_dict(self) -> dict:
        return {
            "name": self.run_name,
            "started_at": self.started_at,
            "evidence_dir": self.evidence_dir,
            "final_status": "PASS" if self.all_passed() else "FAIL",
            "report": {
                field: {
                    "status": self.steps.get(field, StepResult("NOT_RUN")).status,
                    "details": self.steps.get(field, StepResult("NOT_RUN")).details,
                    "screenshot": self.steps.get(field, StepResult("NOT_RUN")).screenshot,
                    "url": self.steps.get(field, StepResult("NOT_RUN")).url,
                }
                for field in REPORT_FIELDS
            },
        }


class SaleadsMiNegocioWorkflow:
    def __init__(
        self,
        page: Page,
        report: WorkflowReport,
        evidence_dir: Path,
        google_email: str,
        business_name: str,
        load_timeout_ms: int,
    ) -> None:
        self.page = page
        self.report = report
        self.evidence_dir = evidence_dir
        self.google_email = google_email
        self.business_name = business_name
        self.load_timeout_ms = load_timeout_ms
        self.main_page = page

    def run(self) -> WorkflowReport:
        login_ok = self._run_step("Login", self.step_login_with_google)
        if not login_ok:
            self._mark_remaining_skipped_after("Login", "Login step failed, downstream validation skipped.")
            return self.report

        menu_ok = self._run_step("Mi Negocio menu", self.step_open_mi_negocio_menu)
        if not menu_ok:
            self._mark_remaining_skipped_after("Mi Negocio menu", "Mi Negocio menu could not be opened.")
            return self.report

        modal_ok = self._run_step("Agregar Negocio modal", self.step_validate_agregar_negocio_modal)
        admin_ok = self._run_step("Administrar Negocios view", self.step_open_administrar_negocios)
        if not admin_ok:
            self._mark_remaining_skipped_after(
                "Administrar Negocios view",
                "Administrar Negocios view was not available.",
            )
            return self.report

        self._run_step("Información General", self.step_validate_informacion_general)
        self._run_step("Detalles de la Cuenta", self.step_validate_detalles_cuenta)
        self._run_step("Tus Negocios", self.step_validate_tus_negocios)
        self._run_step("Términos y Condiciones", self.step_validate_terminos)
        self._run_step("Política de Privacidad", self.step_validate_privacidad)

        # Keep the variable used to avoid lint warnings from static checkers.
        _ = modal_ok
        return self.report

    def _run_step(self, name: str, func: Callable[[], tuple[bool, str, str | None, str | None]]) -> bool:
        try:
            passed, details, screenshot, url = func()
            self.report.set_step(name, passed, details, screenshot=screenshot, url=url)
            return passed
        except Exception as exc:  # noqa: BLE001
            screenshot = self._safe_screenshot(f"{slugify(name)}_error")
            self.report.set_step(
                name,
                False,
                f"Unhandled exception: {exc}",
                screenshot=screenshot,
                url=self._safe_url(self.page),
            )
            return False

    def _mark_remaining_skipped_after(self, after_step: str, reason: str) -> None:
        start_skipping = False
        for field in REPORT_FIELDS:
            if field == after_step:
                start_skipping = True
                continue
            if start_skipping and field not in self.report.steps:
                self.report.set_skipped(field, reason)

    def _safe_url(self, page: Page | None) -> str | None:
        if not page:
            return None
        try:
            return page.url
        except PlaywrightError:
            return None

    def _safe_screenshot(self, label: str, full_page: bool = False) -> str | None:
        filename = f"{slugify(label)}.png"
        output_path = self.evidence_dir / filename
        try:
            self.page.screenshot(path=str(output_path), full_page=full_page)
            return str(output_path)
        except PlaywrightError:
            return None

    def _wait_ui_settle(self) -> None:
        try:
            self.page.wait_for_load_state("domcontentloaded", timeout=self.load_timeout_ms)
        except PlaywrightTimeoutError:
            pass
        try:
            self.page.wait_for_load_state("networkidle", timeout=self.load_timeout_ms)
        except PlaywrightTimeoutError:
            pass
        self.page.wait_for_timeout(700)

    def _click_with_wait(self, locator) -> None:
        locator.first.wait_for(state="visible", timeout=self.load_timeout_ms)
        locator.first.click()
        self._wait_ui_settle()

    def _is_visible(self, locator) -> bool:
        try:
            return locator.first.is_visible(timeout=self.load_timeout_ms)
        except PlaywrightError:
            return False

    def _open_mi_negocio_if_needed(self) -> None:
        if self._is_visible(self.page.get_by_text("Agregar Negocio", exact=False)) and self._is_visible(
            self.page.get_by_text("Administrar Negocios", exact=False)
        ):
            return
        mi_negocio = self.page.get_by_text("Mi Negocio", exact=False)
        self._click_with_wait(mi_negocio)

    def step_login_with_google(self) -> tuple[bool, str, str | None, str | None]:
        login_locator = self.page.get_by_role("button", name=re.compile(r"(sign in|login|google)", re.I))
        if login_locator.count() == 0:
            login_locator = self.page.get_by_text(
                re.compile(r"(iniciar sesion|iniciar sesi[oó]n|sign in with google|google)", re.I)
            )
        self._click_with_wait(login_locator)

        # If Google account chooser appears, select the requested account.
        account_option = self.page.get_by_text(self.google_email, exact=False)
        if self._is_visible(account_option):
            self._click_with_wait(account_option)

        # Validate main app shell is visible.
        sidebar = self.page.locator("aside").first
        if not self._is_visible(sidebar):
            # fallback: common nav roles.
            sidebar = self.page.get_by_role("navigation").first
        passed = self._is_visible(sidebar)
        screenshot = self._safe_screenshot("dashboard_loaded")
        details = "Main interface and sidebar visible." if passed else "Sidebar/navigation was not detected."
        return passed, details, screenshot, self._safe_url(self.page)

    def step_open_mi_negocio_menu(self) -> tuple[bool, str, str | None, str | None]:
        self._open_mi_negocio_if_needed()
        agregar_visible = self._is_visible(self.page.get_by_text("Agregar Negocio", exact=False))
        administrar_visible = self._is_visible(self.page.get_by_text("Administrar Negocios", exact=False))
        passed = agregar_visible and administrar_visible
        screenshot = self._safe_screenshot("mi_negocio_menu_expanded")
        details = (
            "Mi Negocio submenu expanded with expected options."
            if passed
            else "Mi Negocio submenu options were not both visible."
        )
        return passed, details, screenshot, self._safe_url(self.page)

    def step_validate_agregar_negocio_modal(self) -> tuple[bool, str, str | None, str | None]:
        self._open_mi_negocio_if_needed()
        self._click_with_wait(self.page.get_by_text("Agregar Negocio", exact=False))

        title = self.page.get_by_text("Crear Nuevo Negocio", exact=False)
        input_field = self.page.get_by_label("Nombre del Negocio", exact=False)
        quota_text = self.page.get_by_text("Tienes 2 de 3 negocios", exact=False)
        cancelar = self.page.get_by_role("button", name=re.compile(r"cancelar", re.I))
        crear = self.page.get_by_role("button", name=re.compile(r"crear negocio", re.I))

        passed = all(
            [
                self._is_visible(title),
                self._is_visible(input_field),
                self._is_visible(quota_text),
                self._is_visible(cancelar),
                self._is_visible(crear),
            ]
        )

        if self._is_visible(input_field):
            input_field.first.click()
            input_field.first.fill(self.business_name)
            self.page.wait_for_timeout(300)

        screenshot = self._safe_screenshot("agregar_negocio_modal")

        # Optional cleanup action requested by the workflow.
        if self._is_visible(cancelar):
            self._click_with_wait(cancelar)

        details = (
            "Agregar Negocio modal validated."
            if passed
            else "One or more modal elements were not visible."
        )
        return passed, details, screenshot, self._safe_url(self.page)

    def step_open_administrar_negocios(self) -> tuple[bool, str, str | None, str | None]:
        self._open_mi_negocio_if_needed()
        self._click_with_wait(self.page.get_by_text("Administrar Negocios", exact=False))

        info_general = self.page.get_by_text(re.compile(r"Informacion General|Información General", re.I))
        detalles = self.page.get_by_text("Detalles de la Cuenta", exact=False)
        negocios = self.page.get_by_text("Tus Negocios", exact=False)
        legal = self.page.get_by_text(re.compile(r"(Seccion Legal|Sección Legal)", re.I))

        passed = all(
            [
                self._is_visible(info_general),
                self._is_visible(detalles),
                self._is_visible(negocios),
                self._is_visible(legal),
            ]
        )
        screenshot = self._safe_screenshot("administrar_negocios_view_full", full_page=True)
        details = "Administrar Negocios page sections are visible." if passed else "Missing one or more required sections."
        return passed, details, screenshot, self._safe_url(self.page)

    def step_validate_informacion_general(self) -> tuple[bool, str, str | None, str | None]:
        # Flexible checks for user name/email/plan/button in Informacion General section.
        email_like = self.page.locator("text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/").first
        plan = self.page.get_by_text("BUSINESS PLAN", exact=False)
        cambiar_plan = self.page.get_by_role("button", name=re.compile(r"cambiar plan", re.I))
        user_name_hint = self.page.locator("h1, h2, h3, strong").first

        passed = all(
            [
                self._is_visible(email_like),
                self._is_visible(plan),
                self._is_visible(cambiar_plan),
                self._is_visible(user_name_hint),
            ]
        )
        screenshot = self._safe_screenshot("informacion_general")
        details = "Informacion General elements validated." if passed else "Missing one or more Informacion General elements."
        return passed, details, screenshot, self._safe_url(self.page)

    def step_validate_detalles_cuenta(self) -> tuple[bool, str, str | None, str | None]:
        cuenta_creada = self.page.get_by_text("Cuenta creada", exact=False)
        estado_activo = self.page.get_by_text("Estado activo", exact=False)
        idioma = self.page.get_by_text("Idioma seleccionado", exact=False)
        passed = all([self._is_visible(cuenta_creada), self._is_visible(estado_activo), self._is_visible(idioma)])
        screenshot = self._safe_screenshot("detalles_cuenta")
        details = "Detalles de la Cuenta elements validated." if passed else "Missing one or more account detail elements."
        return passed, details, screenshot, self._safe_url(self.page)

    def step_validate_tus_negocios(self) -> tuple[bool, str, str | None, str | None]:
        heading = self.page.get_by_text("Tus Negocios", exact=False)
        add_button = self.page.get_by_role("button", name=re.compile(r"agregar negocio", re.I))
        quota_text = self.page.get_by_text("Tienes 2 de 3 negocios", exact=False)
        business_items = self.page.locator(
            "li:has-text('Negocio'), [data-testid*='business'], [class*='business']"
        )

        passed = (
            self._is_visible(heading)
            and self._is_visible(add_button)
            and self._is_visible(quota_text)
            and business_items.count() > 0
        )
        screenshot = self._safe_screenshot("tus_negocios")
        details = "Tus Negocios validated." if passed else "Business list/button/quota validation failed."
        return passed, details, screenshot, self._safe_url(self.page)

    @contextmanager
    def _maybe_popup(self, click_action: Callable[[], None]):
        popup_page: Page | None = None
        try:
            with self.page.expect_popup(timeout=6000) as popup_info:
                click_action()
            popup_page = popup_info.value
            popup_page.wait_for_load_state("domcontentloaded", timeout=self.load_timeout_ms)
            yield popup_page
        except PlaywrightTimeoutError:
            click_action()
            self._wait_ui_settle()
            yield None

    def _validate_legal_page(
        self,
        link_text_regex: re.Pattern[str],
        heading_regex: re.Pattern[str],
        screenshot_label: str,
    ) -> tuple[bool, str, str | None, str | None]:
        self.main_page.bring_to_front()
        origin_url = self.main_page.url
        link = self.main_page.get_by_text(link_text_regex, exact=False)

        with self._maybe_popup(lambda: self._click_with_wait(link)) as popup:
            legal_page = popup or self.main_page
            legal_page.bring_to_front()
            try:
                legal_page.wait_for_load_state("domcontentloaded", timeout=self.load_timeout_ms)
            except PlaywrightTimeoutError:
                pass

            heading = legal_page.get_by_text(heading_regex, exact=False)
            body_has_text = legal_page.locator("main, article, body")
            passed = self._is_visible(heading) and body_has_text.count() > 0

            screenshot_path = self.evidence_dir / f"{slugify(screenshot_label)}.png"
            legal_page.screenshot(path=str(screenshot_path), full_page=True)
            final_url = legal_page.url

            # Cleanup: return to the application tab.
            if popup:
                popup.close()
                self.main_page.bring_to_front()
                self._wait_ui_settle()
            else:
                if self.main_page.url != origin_url:
                    try:
                        self.main_page.go_back(wait_until="domcontentloaded", timeout=self.load_timeout_ms)
                    except PlaywrightError:
                        pass
                    self._wait_ui_settle()

            details = "Legal page validated." if passed else "Heading/content validation failed on legal page."
            return passed, details, str(screenshot_path), final_url

    def step_validate_terminos(self) -> tuple[bool, str, str | None, str | None]:
        return self._validate_legal_page(
            link_text_regex=re.compile(r"Terminos y Condiciones|Términos y Condiciones", re.I),
            heading_regex=re.compile(r"Terminos y Condiciones|Términos y Condiciones", re.I),
            screenshot_label="terminos_y_condiciones",
        )

    def step_validate_privacidad(self) -> tuple[bool, str, str | None, str | None]:
        return self._validate_legal_page(
            link_text_regex=re.compile(r"Politica de Privacidad|Política de Privacidad", re.I),
            heading_regex=re.compile(r"Politica de Privacidad|Política de Privacidad", re.I),
            screenshot_label="politica_de_privacidad",
        )


def ensure_page_on_login(page: Page, login_url: str | None, timeout_ms: int) -> None:
    if login_url:
        page.goto(login_url, wait_until="domcontentloaded", timeout=timeout_ms)
        return

    # Requirement says the browser is already on the SaleADS login page.
    # If we are still on about:blank, fail fast with actionable guidance.
    if page.url.startswith("about:blank"):
        raise RuntimeError(
            "No login URL was provided and the page is about:blank. "
            "Open the SaleADS login page first (CDP mode) or pass --login-url."
        )


def resolve_context(
    playwright,
    cdp_url: str | None,
    headed: bool,
) -> tuple[Browser | None, BrowserContext, Page]:
    if cdp_url:
        browser = playwright.chromium.connect_over_cdp(cdp_url)
        if not browser.contexts:
            context = browser.new_context()
        else:
            context = browser.contexts[0]

        if context.pages:
            page = context.pages[0]
        else:
            page = context.new_page()
        return browser, context, page

    browser = playwright.chromium.launch(headless=not headed)
    context = browser.new_context()
    page = context.new_page()
    return browser, context, page


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run SaleADS Mi Negocio full workflow.")
    parser.add_argument("--login-url", default=os.getenv("SALEADS_LOGIN_URL"), help="SaleADS login URL (optional).")
    parser.add_argument(
        "--cdp-url",
        default=os.getenv("CHROME_CDP_URL"),
        help="Optional Chrome DevTools URL to attach to an existing browser tab.",
    )
    parser.add_argument(
        "--google-email",
        default=os.getenv("SALEADS_GOOGLE_EMAIL", "juanlucasbarbiergarzon@gmail.com"),
        help="Google account email to select when chooser appears.",
    )
    parser.add_argument(
        "--business-name",
        default=os.getenv("SALEADS_TEST_BUSINESS_NAME", "Negocio Prueba Automatizacion"),
        help="Optional business name used in modal input.",
    )
    parser.add_argument(
        "--headed",
        action="store_true",
        help="Run with a visible browser window if launching a new browser.",
    )
    parser.add_argument(
        "--timeout-ms",
        type=int,
        default=int(os.getenv("SALEADS_TIMEOUT_MS", "15000")),
        help="Default timeout for waiting UI elements.",
    )
    parser.add_argument(
        "--evidence-root",
        default=os.getenv("SALEADS_EVIDENCE_ROOT", "artifacts/saleads_mi_negocio_full_test"),
        help="Directory where screenshots and report will be stored.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    evidence_dir = Path(args.evidence_root) / now_stamp()
    evidence_dir.mkdir(parents=True, exist_ok=True)

    report = WorkflowReport()
    report.evidence_dir = str(evidence_dir)

    browser: Browser | None = None
    context: BrowserContext | None = None
    try:
        with sync_playwright() as playwright:
            browser, context, page = resolve_context(playwright, args.cdp_url, args.headed)
            ensure_page_on_login(page, args.login_url, args.timeout_ms)

            workflow = SaleadsMiNegocioWorkflow(
                page=page,
                report=report,
                evidence_dir=evidence_dir,
                google_email=args.google_email,
                business_name=args.business_name,
                load_timeout_ms=args.timeout_ms,
            )
            workflow.run()
    except Exception as exc:  # noqa: BLE001
        # If initialization fails, mark all unexecuted fields as failed.
        for field in REPORT_FIELDS:
            if field not in report.steps:
                report.set_step(field, False, f"Initialization/runner error: {exc}")
    finally:
        if context:
            try:
                context.close()
            except PlaywrightError:
                pass
        if browser:
            try:
                browser.close()
            except PlaywrightError:
                pass

    report_path = evidence_dir / "report.json"
    report_payload = report.as_dict()
    report_path.write_text(json.dumps(report_payload, indent=2, ensure_ascii=True), encoding="utf-8")

    print(json.dumps(report_payload, indent=2, ensure_ascii=True))
    print(f"\nReport saved to: {report_path}")
    return 0 if report_payload["final_status"] == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
