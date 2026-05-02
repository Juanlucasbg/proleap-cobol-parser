#!/usr/bin/env python3
"""
SaleADS Mi Negocio full workflow validation.

Environment-agnostic usage:
- Do not hardcode a domain.
- Option A: provide SALEADS_LOGIN_URL to open the login page.
- Option B: provide SALEADS_CDP_ENDPOINT to attach to an already-open browser
  where the login page is already loaded.
"""

from __future__ import annotations

import argparse
import json
import os
import re
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable

from playwright.sync_api import BrowserContext, Error, Page, TimeoutError, sync_playwright


DEFAULT_TIMEOUT_MS = 25000
ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com"


@dataclass
class StepResult:
    name: str
    status: str
    details: str


class SaleadsMiNegocioFlow:
    def __init__(self, page: Page, context: BrowserContext, evidence_dir: Path) -> None:
        self.page = page
        self.context = context
        self.evidence_dir = evidence_dir
        self.results: list[StepResult] = []
        self.legal_urls: dict[str, str] = {}
        self.page.set_default_timeout(DEFAULT_TIMEOUT_MS)

    def run(self) -> int:
        ordered_steps: list[tuple[str, Callable[[], None]]] = [
            ("Login", self.step_login_google),
            ("Mi Negocio menu", self.step_open_mi_negocio_menu),
            ("Agregar Negocio modal", self.step_validate_agregar_negocio_modal),
            ("Administrar Negocios view", self.step_open_administrar_negocios),
            ("Información General", self.step_validate_informacion_general),
            ("Detalles de la Cuenta", self.step_validate_detalles_cuenta),
            ("Tus Negocios", self.step_validate_tus_negocios),
            ("Términos y Condiciones", self.step_validate_terminos_condiciones),
            ("Política de Privacidad", self.step_validate_politica_privacidad),
        ]

        for report_name, step_fn in ordered_steps:
            try:
                step_fn()
                self.results.append(StepResult(report_name, "PASS", "All validations passed."))
            except Exception as exc:  # noqa: BLE001
                self.results.append(StepResult(report_name, "FAIL", str(exc)))

        self._write_report()
        return 0 if all(item.status == "PASS" for item in self.results) else 1

    def step_login_google(self) -> None:
        self._wait_after_action()
        login_trigger = self._first_visible_by_text(
            [
                r"Sign in with Google",
                r"Iniciar sesión con Google",
                r"Continuar con Google",
                r"Google",
            ],
            scope=self.page,
        )

        popup_page = None
        try:
            with self.context.expect_page(timeout=5000) as popup_info:
                self._click_and_wait(login_trigger)
            popup_page = popup_info.value
        except TimeoutError:
            self._click_and_wait(login_trigger)
            try:
                # Some flows open account selection in the same tab.
                same_tab_account = self.page.get_by_text(ACCOUNT_EMAIL, exact=True)
                same_tab_account.wait_for(state="visible", timeout=6000)
                self._click_and_wait(same_tab_account)
            except TimeoutError:
                pass

        if popup_page is not None:
            popup_page.wait_for_load_state("domcontentloaded")
            try:
                account = popup_page.get_by_text(ACCOUNT_EMAIL, exact=True)
                account.wait_for(state="visible", timeout=10000)
                self._click_and_wait(account, page=popup_page)
            except TimeoutError:
                # Some environments may already have an active Google session.
                pass

            try:
                popup_page.wait_for_event("close", timeout=15000)
            except TimeoutError:
                # If the popup is still open, continue and validate main app.
                pass

        self._wait_for_main_interface()
        self._screenshot("01_dashboard_loaded.png", full_page=True)

    def step_open_mi_negocio_menu(self) -> None:
        self._ensure_sidebar_visible()
        if not self._is_text_visible(r"\bMi Negocio\b", timeout=2500):
            negocio = self._first_visible_by_text([r"\bNegocio\b"], scope=self.page)
            self._click_and_wait(negocio)

        if not self._is_text_visible(r"Agregar Negocio", timeout=2500):
            mi_negocio = self._first_visible_by_text([r"\bMi Negocio\b"], scope=self.page)
            self._click_and_wait(mi_negocio)

        self._assert_text_visible(r"Agregar Negocio")
        self._assert_text_visible(r"Administrar Negocios")
        self._screenshot("02_mi_negocio_menu_expanded.png")

    def step_validate_agregar_negocio_modal(self) -> None:
        add_business = self._first_visible_by_text([r"Agregar Negocio"], scope=self.page)
        self._click_and_wait(add_business)

        modal = self.page.get_by_role("dialog")
        modal.wait_for(state="visible")

        self._assert_text_visible(r"Crear Nuevo Negocio", scope=modal)
        self._assert_text_visible(r"Nombre del Negocio", scope=modal)
        self._assert_text_visible(r"Tienes 2 de 3 negocios", scope=modal)
        self._assert_text_visible(r"Cancelar", scope=modal)
        self._assert_text_visible(r"Crear Negocio", scope=modal)

        try:
            business_name_input = modal.get_by_label(re.compile(r"Nombre del Negocio", re.IGNORECASE)).first
            business_name_input.wait_for(state="visible", timeout=5000)
        except TimeoutError:
            business_name_input = modal.get_by_role("textbox").first
            business_name_input.wait_for(state="visible", timeout=5000)
        business_name_input.click()
        self._wait_after_action()
        business_name_input.fill("Negocio Prueba Automatizacion")
        self._screenshot("03_agregar_negocio_modal.png")

        cancel_button = self._first_visible_by_text([r"Cancelar"], scope=modal)
        self._click_and_wait(cancel_button)

    def step_open_administrar_negocios(self) -> None:
        try:
            self._assert_text_visible(r"Administrar Negocios", timeout=3000)
        except Exception:
            mi_negocio = self._first_visible_by_text([r"Mi Negocio"], scope=self.page)
            self._click_and_wait(mi_negocio)

        admin_business = self._first_visible_by_text([r"Administrar Negocios"], scope=self.page)
        self._click_and_wait(admin_business)

        self._assert_text_visible(r"Informaci[oó]n General")
        self._assert_text_visible(r"Detalles de la Cuenta")
        self._assert_text_visible(r"Tus Negocios")
        self._assert_text_visible(r"Secci[oó]n Legal")
        self._screenshot("04_administrar_negocios_page.png", full_page=True)

    def step_validate_informacion_general(self) -> None:
        info_heading = self.page.get_by_text(re.compile(r"Informaci[oó]n General", re.IGNORECASE)).first
        info_heading.wait_for(state="visible")
        info_section = info_heading.locator("xpath=ancestor::section[1]").first
        info_block = info_section if info_section.count() else info_heading.locator("xpath=ancestor::div[1]").first

        email_locator = self.page.get_by_text(re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")).first
        email_locator.wait_for(state="visible")
        email_text = email_locator.inner_text().strip()
        if "@" not in email_text:
            raise AssertionError("User email was not detected in Informacion General.")

        block_text = info_block.inner_text().strip() if info_block.count() else self.page.inner_text("body")
        candidate_lines = [line.strip() for line in block_text.splitlines() if line.strip()]
        filtered_name_lines = [
            line
            for line in candidate_lines
            if "@"
            not in line
            and "INFORMACI" not in line.upper()
            and "BUSINESS PLAN" not in line.upper()
            and "CAMBIAR PLAN" not in line.upper()
            and len(line) >= 3
        ]
        if not filtered_name_lines:
            raise AssertionError("User name was not clearly visible in Informacion General.")

        self._assert_text_visible(r"BUSINESS PLAN")
        self._assert_text_visible(r"Cambiar Plan")

    def step_validate_detalles_cuenta(self) -> None:
        self._assert_text_visible(r"Detalles de la Cuenta")
        self._assert_text_visible(r"Cuenta creada")
        self._assert_text_visible(r"Estado activo")
        self._assert_text_visible(r"Idioma seleccionado")

    def step_validate_tus_negocios(self) -> None:
        heading = self.page.get_by_text(re.compile(r"Tus Negocios", re.IGNORECASE)).first
        heading.wait_for(state="visible")
        section_wrapper = heading.locator("xpath=ancestor::section[1]").first
        section = section_wrapper if section_wrapper.count() else heading.locator("xpath=ancestor::div[1]").first
        section_text = section.inner_text().strip() if section.count() else ""
        if len(section_text) < 20:
            raise AssertionError("Business list does not appear to be visible.")

        self._assert_text_visible(r"Agregar Negocio")
        self._assert_text_visible(r"Tienes 2 de 3 negocios")

    def step_validate_terminos_condiciones(self) -> None:
        self._validate_legal_link(
            link_text_regex=r"T[ée]rminos y Condiciones",
            expected_heading_regex=r"T[ée]rminos y Condiciones",
            screenshot_name="05_terminos_y_condiciones.png",
            url_key="terminos_y_condiciones",
        )

    def step_validate_politica_privacidad(self) -> None:
        self._validate_legal_link(
            link_text_regex=r"Pol[ií]tica de Privacidad",
            expected_heading_regex=r"Pol[ií]tica de Privacidad",
            screenshot_name="06_politica_de_privacidad.png",
            url_key="politica_de_privacidad",
        )

    def _validate_legal_link(
        self,
        link_text_regex: str,
        expected_heading_regex: str,
        screenshot_name: str,
        url_key: str,
    ) -> None:
        app_page = self.page
        link = self._first_visible_by_text([link_text_regex], scope=app_page)

        opened_new_tab = False
        legal_page = app_page
        try:
            with self.context.expect_page(timeout=5000) as popup_info:
                self._click_and_wait(link)
            legal_page = popup_info.value
            opened_new_tab = True
        except TimeoutError:
            self._click_and_wait(link)
            app_page.wait_for_load_state("domcontentloaded")

        legal_page.wait_for_load_state("domcontentloaded")
        heading = legal_page.get_by_text(re.compile(expected_heading_regex, re.IGNORECASE)).first
        heading.wait_for(state="visible")

        body_text = legal_page.locator("body").inner_text().strip()
        if len(body_text) < 120:
            raise AssertionError("Legal content text is too short or not visible.")

        legal_page.screenshot(path=str(self.evidence_dir / screenshot_name), full_page=True)
        self.legal_urls[url_key] = legal_page.url

        if opened_new_tab:
            legal_page.close()
            app_page.bring_to_front()
            app_page.wait_for_load_state("domcontentloaded")
        else:
            app_page.go_back(wait_until="domcontentloaded")
            app_page.wait_for_load_state("domcontentloaded")

    def _wait_for_main_interface(self) -> None:
        self.page.wait_for_load_state("domcontentloaded")
        self.page.wait_for_timeout(900)

        sidebar_candidates = [
            self.page.locator("aside"),
            self.page.get_by_role("navigation"),
            self.page.get_by_text(re.compile(r"Mi Negocio|Negocio|Dashboard", re.IGNORECASE)),
        ]
        for candidate in sidebar_candidates:
            try:
                candidate.first.wait_for(state="visible", timeout=15000)
                return
            except TimeoutError:
                continue
        raise AssertionError("Main interface was not detected after login.")

    def _ensure_sidebar_visible(self) -> None:
        aside = self.page.locator("aside").first
        nav = self.page.get_by_role("navigation").first
        for candidate in [aside, nav]:
            try:
                candidate.wait_for(state="visible", timeout=6000)
                return
            except TimeoutError:
                continue
        raise AssertionError("Left sidebar navigation is not visible.")

    def _first_visible_by_text(self, regex_patterns: list[str], scope: Page | object):
        errors: list[str] = []
        for pattern in regex_patterns:
            compiled = re.compile(pattern, re.IGNORECASE)
            candidates = [
                scope.get_by_role("button", name=compiled).first,
                scope.get_by_role("link", name=compiled).first,
                scope.get_by_text(compiled).first,
            ]
            for locator in candidates:
                try:
                    locator.wait_for(state="visible", timeout=3000)
                    return locator
                except TimeoutError as exc:
                    errors.append(str(exc))
                    continue
        raise AssertionError(f"Could not find visible element by text patterns: {regex_patterns}.")

    def _assert_text_visible(self, regex_text: str, scope: Page | object | None = None, timeout: int = 10000) -> None:
        target_scope = scope or self.page
        locator = target_scope.get_by_text(re.compile(regex_text, re.IGNORECASE)).first
        locator.wait_for(state="visible", timeout=timeout)

    def _is_text_visible(self, regex_text: str, timeout: int = 3000) -> bool:
        locator = self.page.get_by_text(re.compile(regex_text, re.IGNORECASE)).first
        try:
            locator.wait_for(state="visible", timeout=timeout)
            return True
        except TimeoutError:
            return False

    def _click_and_wait(self, locator, page: Page | None = None) -> None:
        target_page = page or self.page
        locator.click()
        self._wait_after_action(page=target_page)

    def _wait_after_action(self, page: Page | None = None) -> None:
        target_page = page or self.page
        try:
            target_page.wait_for_load_state("domcontentloaded", timeout=8000)
        except TimeoutError:
            pass
        try:
            target_page.wait_for_load_state("networkidle", timeout=4000)
        except TimeoutError:
            pass
        target_page.wait_for_timeout(600)

    def _screenshot(self, file_name: str, full_page: bool = False) -> None:
        self.page.screenshot(path=str(self.evidence_dir / file_name), full_page=full_page)

    def _write_report(self) -> None:
        rows = []
        for item in self.results:
            rows.append(
                {
                    "step": item.name,
                    "status": item.status,
                    "details": item.details,
                }
            )

        report = {
            "test_name": "saleads_mi_negocio_full_test",
            "generated_at_utc": datetime.now(timezone.utc).isoformat(),
            "results": rows,
            "legal_urls": self.legal_urls,
        }
        (self.evidence_dir / "final_report.json").write_text(
            json.dumps(report, ensure_ascii=True, indent=2),
            encoding="utf-8",
        )

        markdown_lines = [
            "# SaleADS Mi Negocio Full Test Report",
            "",
            "| Validation | Status | Details |",
            "|---|---|---|",
        ]
        for item in self.results:
            markdown_lines.append(f"| {item.name} | {item.status} | {item.details} |")

        if self.legal_urls:
            markdown_lines.extend(
                [
                    "",
                    "## Final Legal URLs",
                    "",
                ]
            )
            for key, value in self.legal_urls.items():
                markdown_lines.append(f"- **{key}**: {value}")

        (self.evidence_dir / "final_report.md").write_text("\n".join(markdown_lines), encoding="utf-8")


def _build_evidence_dir(base: Path | None = None) -> Path:
    root = base or (Path(__file__).resolve().parent / "artifacts")
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    evidence_dir = root / f"saleads_mi_negocio_full_test_{timestamp}"
    evidence_dir.mkdir(parents=True, exist_ok=True)
    return evidence_dir


def _get_or_create_page(context: BrowserContext) -> Page:
    if context.pages:
        return context.pages[0]
    return context.new_page()


def main() -> int:
    parser = argparse.ArgumentParser(description="Run SaleADS Mi Negocio full workflow test.")
    parser.add_argument(
        "--evidence-dir",
        default="",
        help="Optional explicit directory for screenshots and report output.",
    )
    parser.add_argument(
        "--headless",
        action="store_true",
        help="Force headless mode when launching a local browser.",
    )
    args = parser.parse_args()

    evidence_dir = _build_evidence_dir(Path(args.evidence_dir) if args.evidence_dir else None)
    login_url = os.getenv("SALEADS_LOGIN_URL", "").strip()
    cdp_endpoint = os.getenv("SALEADS_CDP_ENDPOINT", "").strip()

    with sync_playwright() as playwright:
        if cdp_endpoint:
            browser = playwright.chromium.connect_over_cdp(cdp_endpoint)
            context = browser.contexts[0] if browser.contexts else browser.new_context()
            page = _get_or_create_page(context)
        else:
            browser = playwright.chromium.launch(headless=args.headless or False)
            context = browser.new_context(viewport={"width": 1440, "height": 900})
            page = context.new_page()
            if not login_url:
                raise ValueError(
                    "Set SALEADS_LOGIN_URL or SALEADS_CDP_ENDPOINT. "
                    "No fixed domain is used by this test."
                )
            page.goto(login_url, wait_until="domcontentloaded")

        flow = SaleadsMiNegocioFlow(page=page, context=context, evidence_dir=evidence_dir)
        try:
            return flow.run()
        finally:
            try:
                context.close()
            except Error:
                pass
            browser.close()


if __name__ == "__main__":
    raise SystemExit(main())
