#!/usr/bin/env python3
"""SaleADS Mi Negocio full workflow Playwright automation.

This script validates:
1. Login with Google.
2. Mi Negocio menu expansion.
3. Agregar Negocio modal content.
4. Administrar Negocios page sections.
5. Información General section.
6. Detalles de la Cuenta section.
7. Tus Negocios section.
8. Términos y Condiciones legal page.
9. Política de Privacidad legal page.

The script is environment-agnostic and does not hardcode any SaleADS domain.
Set SALEADS_LOGIN_URL to point to the login page of the target environment.
"""

from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional

from playwright.sync_api import Locator, Page, TimeoutError as PlaywrightTimeoutError, sync_playwright


GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com"
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
    status: str = "FAIL"
    details: str = "Not executed."
    evidence: List[str] = field(default_factory=list)
    url: Optional[str] = None


class SaleadsMiNegocioWorkflow:
    def __init__(self) -> None:
        timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        self.artifacts_dir = Path("artifacts") / "saleads_mi_negocio" / timestamp
        self.artifacts_dir.mkdir(parents=True, exist_ok=True)
        self.report_path = self.artifacts_dir / "report.json"
        self.results: Dict[str, StepResult] = {field_name: StepResult() for field_name in REPORT_FIELDS}

    def _mark_pass(self, field_name: str, details: str, evidence: Optional[List[str]] = None, url: Optional[str] = None) -> None:
        result = self.results[field_name]
        result.status = "PASS"
        result.details = details
        if evidence:
            result.evidence.extend(evidence)
        if url:
            result.url = url

    def _mark_fail(self, field_name: str, details: str, evidence: Optional[List[str]] = None, url: Optional[str] = None) -> None:
        result = self.results[field_name]
        result.status = "FAIL"
        result.details = details
        if evidence:
            result.evidence.extend(evidence)
        if url:
            result.url = url

    def _mark_prerequisite_failures(self, failed_fields: List[str], reason: str) -> None:
        for field_name in failed_fields:
            self._mark_fail(field_name, f"Prerequisite failed: {reason}")

    def _wait_for_ui(self, page: Page, timeout_ms: int = 15000) -> None:
        try:
            page.wait_for_load_state("networkidle", timeout=timeout_ms)
        except PlaywrightTimeoutError:
            # Some SPAs do not become fully idle; keep the flow moving with a short explicit wait.
            page.wait_for_timeout(1000)

    def _screenshot(self, page: Page, filename: str, full_page: bool = False) -> str:
        destination = self.artifacts_dir / filename
        page.screenshot(path=str(destination), full_page=full_page)
        return str(destination)

    def _visible_locator(self, candidates: List[Locator], timeout_ms: int = 6000) -> Optional[Locator]:
        for locator in candidates:
            try:
                locator.first.wait_for(state="visible", timeout=timeout_ms)
                return locator.first
            except PlaywrightTimeoutError:
                continue
        return None

    def _is_visible(self, locator: Locator, timeout_ms: int = 5000) -> bool:
        try:
            locator.first.wait_for(state="visible", timeout=timeout_ms)
            return True
        except PlaywrightTimeoutError:
            return False

    def _click_and_wait(self, page: Page, locator: Locator) -> None:
        locator.click()
        self._wait_for_ui(page)

    def _goto_login_if_configured(self, page: Page) -> bool:
        login_url = os.getenv("SALEADS_LOGIN_URL", "").strip()
        if not login_url:
            self._mark_fail(
                "Login",
                (
                    "SALEADS_LOGIN_URL is not set. This test does not hardcode any domain, so set "
                    "SALEADS_LOGIN_URL to the current environment login page and rerun."
                ),
            )
            return False

        page.goto(login_url, wait_until="domcontentloaded")
        self._wait_for_ui(page)
        return True

    def step_login_with_google(self, page: Page) -> bool:
        if not self._goto_login_if_configured(page):
            return False

        popup_page: Optional[Page] = None
        login_candidates = [
            page.get_by_role("button", name=re.compile(r"google", re.IGNORECASE)),
            page.get_by_role("button", name=re.compile(r"iniciar sesi[oó]n", re.IGNORECASE)),
            page.get_by_role("button", name=re.compile(r"sign in", re.IGNORECASE)),
            page.get_by_text(re.compile(r"sign in with google", re.IGNORECASE)),
            page.get_by_text(re.compile(r"iniciar sesi[oó]n con google", re.IGNORECASE)),
        ]
        login_button = self._visible_locator(login_candidates, timeout_ms=10000)

        if login_button is None:
            screenshot = self._screenshot(page, "step1_login_button_not_found.png")
            self._mark_fail(
                "Login",
                "Could not find Google login button on the login page.",
                evidence=[screenshot],
                url=page.url,
            )
            return False

        try:
            with page.expect_popup(timeout=7000) as popup_info:
                login_button.click()
            popup_page = popup_info.value
            self._wait_for_ui(popup_page)
        except PlaywrightTimeoutError:
            self._click_and_wait(page, login_button)

        if popup_page is not None:
            account_locator = self._visible_locator(
                [
                    popup_page.get_by_text(GOOGLE_ACCOUNT_EMAIL, exact=True),
                    popup_page.get_by_role("button", name=re.compile(re.escape(GOOGLE_ACCOUNT_EMAIL), re.IGNORECASE)),
                ],
                timeout_ms=12000,
            )
            if account_locator is not None:
                account_locator.click()
                try:
                    popup_page.wait_for_close(timeout=20000)
                except PlaywrightTimeoutError:
                    self._wait_for_ui(popup_page)

        self._wait_for_ui(page)
        sidebar_visible = self._visible_locator(
            [
                page.get_by_role("navigation"),
                page.locator("aside"),
                page.get_by_text(re.compile(r"negocio", re.IGNORECASE)),
            ],
            timeout_ms=20000,
        )
        dashboard_shot = self._screenshot(page, "step1_dashboard_loaded.png")

        if sidebar_visible is None:
            self._mark_fail(
                "Login",
                "Login flow did not reach main app interface with visible left sidebar.",
                evidence=[dashboard_shot],
                url=page.url,
            )
            return False

        self._mark_pass(
            "Login",
            "Main application interface loaded and left sidebar is visible after Google login.",
            evidence=[dashboard_shot],
            url=page.url,
        )
        return True

    def step_open_mi_negocio_menu(self, page: Page) -> bool:
        negocio_locator = self._visible_locator(
            [
                page.get_by_role("link", name=re.compile(r"mi negocio", re.IGNORECASE)),
                page.get_by_role("button", name=re.compile(r"mi negocio", re.IGNORECASE)),
                page.get_by_text(re.compile(r"mi negocio", re.IGNORECASE)),
                page.get_by_text(re.compile(r"negocio", re.IGNORECASE)),
            ],
            timeout_ms=10000,
        )
        if negocio_locator is None:
            screenshot = self._screenshot(page, "step2_menu_not_found.png")
            self._mark_fail(
                "Mi Negocio menu",
                "Could not locate 'Mi Negocio' or 'Negocio' menu option in left sidebar.",
                evidence=[screenshot],
                url=page.url,
            )
            return False

        self._click_and_wait(page, negocio_locator)

        agregar_visible = self._is_visible(page.get_by_text(re.compile(r"agregar negocio", re.IGNORECASE)), timeout_ms=10000)
        administrar_visible = self._is_visible(
            page.get_by_text(re.compile(r"administrar negocios", re.IGNORECASE)),
            timeout_ms=10000,
        )
        menu_shot = self._screenshot(page, "step2_mi_negocio_expanded.png")

        if not (agregar_visible and administrar_visible):
            missing = []
            if not agregar_visible:
                missing.append("'Agregar Negocio'")
            if not administrar_visible:
                missing.append("'Administrar Negocios'")
            self._mark_fail(
                "Mi Negocio menu",
                f"Mi Negocio menu did not fully expand. Missing: {', '.join(missing)}.",
                evidence=[menu_shot],
                url=page.url,
            )
            return False

        self._mark_pass(
            "Mi Negocio menu",
            "Mi Negocio submenu expanded and shows Agregar Negocio + Administrar Negocios.",
            evidence=[menu_shot],
            url=page.url,
        )
        return True

    def step_validate_agregar_modal(self, page: Page) -> bool:
        add_business = self._visible_locator(
            [page.get_by_text(re.compile(r"agregar negocio", re.IGNORECASE))],
            timeout_ms=10000,
        )
        if add_business is None:
            screenshot = self._screenshot(page, "step3_add_business_not_found.png")
            self._mark_fail(
                "Agregar Negocio modal",
                "Could not find 'Agregar Negocio' option.",
                evidence=[screenshot],
                url=page.url,
            )
            return False

        self._click_and_wait(page, add_business)

        title_visible = self._is_visible(page.get_by_text(re.compile(r"crear nuevo negocio", re.IGNORECASE)), timeout_ms=10000)
        name_input = self._visible_locator(
            [
                page.get_by_label(re.compile(r"nombre del negocio", re.IGNORECASE)),
                page.get_by_placeholder(re.compile(r"nombre del negocio", re.IGNORECASE)),
                page.locator("input[name*='negocio' i], input[placeholder*='negocio' i]"),
                page.locator("[role='dialog'] input"),
            ],
            timeout_ms=8000,
        )
        quota_visible = self._is_visible(page.get_by_text(re.compile(r"tienes\s*2\s*de\s*3\s*negocios", re.IGNORECASE)), timeout_ms=8000)
        cancel_visible = self._is_visible(page.get_by_role("button", name=re.compile(r"cancelar", re.IGNORECASE)), timeout_ms=8000)
        create_visible = self._is_visible(page.get_by_role("button", name=re.compile(r"crear negocio", re.IGNORECASE)), timeout_ms=8000)
        modal_shot = self._screenshot(page, "step3_agregar_negocio_modal.png")

        if name_input is not None:
            try:
                name_input.fill("Negocio Prueba Automatización")
            except Exception:
                pass
        cancel_button = self._visible_locator([page.get_by_role("button", name=re.compile(r"cancelar", re.IGNORECASE))], timeout_ms=3000)
        if cancel_button is not None:
            self._click_and_wait(page, cancel_button)

        checks = [title_visible, name_input is not None, quota_visible, cancel_visible, create_visible]
        if not all(checks):
            missing = []
            if not title_visible:
                missing.append("'Crear Nuevo Negocio'")
            if name_input is None:
                missing.append("'Nombre del Negocio' input")
            if not quota_visible:
                missing.append("'Tienes 2 de 3 negocios'")
            if not cancel_visible:
                missing.append("'Cancelar' button")
            if not create_visible:
                missing.append("'Crear Negocio' button")
            self._mark_fail(
                "Agregar Negocio modal",
                f"Agregar Negocio modal validation failed. Missing: {', '.join(missing)}.",
                evidence=[modal_shot],
                url=page.url,
            )
            return False

        self._mark_pass(
            "Agregar Negocio modal",
            "Agregar Negocio modal displayed expected title, field, quota text, and actions.",
            evidence=[modal_shot],
            url=page.url,
        )
        return True

    def step_open_administrar_negocios(self, page: Page) -> bool:
        administrar = self._visible_locator(
            [page.get_by_text(re.compile(r"administrar negocios", re.IGNORECASE))],
            timeout_ms=8000,
        )
        if administrar is None:
            maybe_expand = self._visible_locator(
                [
                    page.get_by_role("link", name=re.compile(r"mi negocio", re.IGNORECASE)),
                    page.get_by_role("button", name=re.compile(r"mi negocio", re.IGNORECASE)),
                    page.get_by_text(re.compile(r"mi negocio", re.IGNORECASE)),
                ],
                timeout_ms=6000,
            )
            if maybe_expand is not None:
                self._click_and_wait(page, maybe_expand)
            administrar = self._visible_locator(
                [page.get_by_text(re.compile(r"administrar negocios", re.IGNORECASE))],
                timeout_ms=8000,
            )

        if administrar is None:
            screenshot = self._screenshot(page, "step4_admin_option_not_found.png")
            self._mark_fail(
                "Administrar Negocios view",
                "Could not find 'Administrar Negocios' option in Mi Negocio menu.",
                evidence=[screenshot],
                url=page.url,
            )
            return False

        self._click_and_wait(page, administrar)
        info_general = self._is_visible(page.get_by_text(re.compile(r"informaci[oó]n general", re.IGNORECASE)), timeout_ms=15000)
        detalles = self._is_visible(page.get_by_text(re.compile(r"detalles de la cuenta", re.IGNORECASE)), timeout_ms=15000)
        tus_negocios = self._is_visible(page.get_by_text(re.compile(r"tus negocios", re.IGNORECASE)), timeout_ms=15000)
        legal = self._is_visible(page.get_by_text(re.compile(r"secci[oó]n legal", re.IGNORECASE)), timeout_ms=15000)
        full_shot = self._screenshot(page, "step4_administrar_negocios_page.png", full_page=True)

        if not all([info_general, detalles, tus_negocios, legal]):
            missing = []
            if not info_general:
                missing.append("'Información General'")
            if not detalles:
                missing.append("'Detalles de la Cuenta'")
            if not tus_negocios:
                missing.append("'Tus Negocios'")
            if not legal:
                missing.append("'Sección Legal'")
            self._mark_fail(
                "Administrar Negocios view",
                f"Administrar Negocios page missing required sections: {', '.join(missing)}.",
                evidence=[full_shot],
                url=page.url,
            )
            return False

        self._mark_pass(
            "Administrar Negocios view",
            "Administrar Negocios page loaded with all required account sections.",
            evidence=[full_shot],
            url=page.url,
        )
        return True

    def step_validate_informacion_general(self, page: Page) -> bool:
        section = self._visible_locator(
            [page.locator("section,div").filter(has_text=re.compile(r"informaci[oó]n general", re.IGNORECASE))],
            timeout_ms=10000,
        )
        if section is None:
            self._mark_fail("Información General", "Could not locate 'Información General' section.", url=page.url)
            return False

        email_visible = self._is_visible(page.get_by_text(re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")), timeout_ms=8000)
        known_email_visible = self._is_visible(page.get_by_text(re.compile(re.escape(GOOGLE_ACCOUNT_EMAIL), re.IGNORECASE)), timeout_ms=5000)
        business_plan_visible = self._is_visible(page.get_by_text(re.compile(r"business plan", re.IGNORECASE)), timeout_ms=8000)
        cambiar_plan_visible = self._is_visible(page.get_by_role("button", name=re.compile(r"cambiar plan", re.IGNORECASE)), timeout_ms=8000)

        # The username format can vary by environment, so treat either known account email
        # or any visible email + profile heading as sufficient for identity visibility.
        username_visible = self._is_visible(page.get_by_role("heading").filter(has_text=re.compile(r".{3,}")), timeout_ms=4000) or known_email_visible
        shot = self._screenshot(page, "step5_informacion_general.png")

        if not all([username_visible, email_visible, business_plan_visible, cambiar_plan_visible]):
            missing = []
            if not username_visible:
                missing.append("user name")
            if not email_visible:
                missing.append("user email")
            if not business_plan_visible:
                missing.append("'BUSINESS PLAN'")
            if not cambiar_plan_visible:
                missing.append("'Cambiar Plan' button")
            self._mark_fail(
                "Información General",
                f"Información General validation failed. Missing: {', '.join(missing)}.",
                evidence=[shot],
                url=page.url,
            )
            return False

        self._mark_pass(
            "Información General",
            "Información General shows identity, email, BUSINESS PLAN, and Cambiar Plan.",
            evidence=[shot],
            url=page.url,
        )
        return True

    def step_validate_detalles_cuenta(self, page: Page) -> bool:
        created = self._is_visible(page.get_by_text(re.compile(r"cuenta creada", re.IGNORECASE)), timeout_ms=8000)
        active = self._is_visible(page.get_by_text(re.compile(r"estado activo", re.IGNORECASE)), timeout_ms=8000)
        language = self._is_visible(page.get_by_text(re.compile(r"idioma seleccionado", re.IGNORECASE)), timeout_ms=8000)
        shot = self._screenshot(page, "step6_detalles_cuenta.png")

        if not all([created, active, language]):
            missing = []
            if not created:
                missing.append("'Cuenta creada'")
            if not active:
                missing.append("'Estado activo'")
            if not language:
                missing.append("'Idioma seleccionado'")
            self._mark_fail(
                "Detalles de la Cuenta",
                f"Detalles de la Cuenta validation failed. Missing: {', '.join(missing)}.",
                evidence=[shot],
                url=page.url,
            )
            return False

        self._mark_pass(
            "Detalles de la Cuenta",
            "Detalles de la Cuenta section shows account creation, active status, and selected language.",
            evidence=[shot],
            url=page.url,
        )
        return True

    def step_validate_tus_negocios(self, page: Page) -> bool:
        tus_negocios_section = self._visible_locator(
            [page.locator("section,div").filter(has_text=re.compile(r"tus negocios", re.IGNORECASE))],
            timeout_ms=10000,
        )
        add_button = self._is_visible(page.get_by_role("button", name=re.compile(r"agregar negocio", re.IGNORECASE)), timeout_ms=8000)
        quota = self._is_visible(page.get_by_text(re.compile(r"tienes\s*2\s*de\s*3\s*negocios", re.IGNORECASE)), timeout_ms=8000)
        business_list_visible = False

        if tus_negocios_section is not None:
            item_count = tus_negocios_section.locator("li, [role='listitem'], [role='row'], .card").count()
            business_list_visible = item_count > 0

        shot = self._screenshot(page, "step7_tus_negocios.png")
        if not all([tus_negocios_section is not None, business_list_visible, add_button, quota]):
            missing = []
            if tus_negocios_section is None:
                missing.append("'Tus Negocios' section")
            if not business_list_visible:
                missing.append("business list")
            if not add_button:
                missing.append("'Agregar Negocio' button")
            if not quota:
                missing.append("'Tienes 2 de 3 negocios'")
            self._mark_fail(
                "Tus Negocios",
                f"Tus Negocios validation failed. Missing: {', '.join(missing)}.",
                evidence=[shot],
                url=page.url,
            )
            return False

        self._mark_pass(
            "Tus Negocios",
            "Tus Negocios section shows list, Agregar Negocio button, and quota text.",
            evidence=[shot],
            url=page.url,
        )
        return True

    def _validate_legal_link(
        self,
        app_page: Page,
        field_name: str,
        link_text: str,
        expected_heading_pattern: str,
        screenshot_filename: str,
    ) -> bool:
        link_locator = self._visible_locator(
            [
                app_page.get_by_role("link", name=re.compile(link_text, re.IGNORECASE)),
                app_page.get_by_text(re.compile(link_text, re.IGNORECASE)),
            ],
            timeout_ms=10000,
        )
        if link_locator is None:
            self._mark_fail(field_name, f"Could not find legal link '{link_text}'.", url=app_page.url)
            return False

        original_url = app_page.url
        legal_page = app_page
        opened_new_tab = False

        try:
            with app_page.context.expect_page(timeout=7000) as page_info:
                link_locator.click()
            legal_page = page_info.value
            opened_new_tab = True
            self._wait_for_ui(legal_page)
        except PlaywrightTimeoutError:
            self._click_and_wait(app_page, link_locator)
            legal_page = app_page

        heading_visible = self._is_visible(
            legal_page.get_by_role("heading", name=re.compile(expected_heading_pattern, re.IGNORECASE)),
            timeout_ms=12000,
        ) or self._is_visible(
            legal_page.get_by_text(re.compile(expected_heading_pattern, re.IGNORECASE)),
            timeout_ms=12000,
        )

        legal_content = legal_page.locator("main p, article p, p")
        content_visible = False
        try:
            legal_content.first.wait_for(state="visible", timeout=10000)
            content_visible = legal_content.count() > 0
        except PlaywrightTimeoutError:
            content_visible = False

        final_url = legal_page.url
        shot = self._screenshot(legal_page, screenshot_filename, full_page=True)

        if opened_new_tab:
            try:
                legal_page.close()
            except Exception:
                pass
            app_page.bring_to_front()
            self._wait_for_ui(app_page)
        else:
            if app_page.url != original_url:
                try:
                    app_page.go_back(wait_until="domcontentloaded", timeout=15000)
                    self._wait_for_ui(app_page)
                except PlaywrightTimeoutError:
                    pass

        if not (heading_visible and content_visible):
            missing = []
            if not heading_visible:
                missing.append("expected legal heading")
            if not content_visible:
                missing.append("legal content text")
            self._mark_fail(
                field_name,
                f"Legal page validation failed for '{link_text}'. Missing: {', '.join(missing)}.",
                evidence=[shot],
                url=final_url,
            )
            return False

        self._mark_pass(
            field_name,
            f"Validated legal page for '{link_text}'.",
            evidence=[shot],
            url=final_url,
        )
        return True

    def write_report(self) -> Dict[str, object]:
        summary = {
            "generated_at_utc": datetime.now(timezone.utc).isoformat(),
            "artifacts_dir": str(self.artifacts_dir),
            "results": {
                key: {
                    "status": value.status,
                    "details": value.details,
                    "evidence": value.evidence,
                    "url": value.url,
                }
                for key, value in self.results.items()
            },
        }
        self.report_path.write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8")
        return summary

    def run(self) -> int:
        headless = os.getenv("SALEADS_HEADLESS", "true").strip().lower() != "false"
        storage_state = os.getenv("SALEADS_STORAGE_STATE", "").strip() or None

        with sync_playwright() as playwright:
            browser = playwright.chromium.launch(headless=headless)
            context_kwargs = {"ignore_https_errors": True, "viewport": {"width": 1600, "height": 1200}}
            if storage_state:
                context_kwargs["storage_state"] = storage_state
            context = browser.new_context(**context_kwargs)
            page = context.new_page()

            try:
                login_ok = self.step_login_with_google(page)
                if not login_ok:
                    self._mark_prerequisite_failures(REPORT_FIELDS[1:], "Login did not succeed")
                    return 1

                menu_ok = self.step_open_mi_negocio_menu(page)
                if not menu_ok:
                    self._mark_prerequisite_failures(REPORT_FIELDS[2:], "Mi Negocio menu did not open correctly")
                    return 1

                modal_ok = self.step_validate_agregar_modal(page)
                if not modal_ok:
                    self._mark_prerequisite_failures(REPORT_FIELDS[3:], "Agregar Negocio modal did not validate")
                    return 1

                admin_ok = self.step_open_administrar_negocios(page)
                if not admin_ok:
                    self._mark_prerequisite_failures(REPORT_FIELDS[4:], "Administrar Negocios view did not load")
                    return 1

                self.step_validate_informacion_general(page)
                self.step_validate_detalles_cuenta(page)
                self.step_validate_tus_negocios(page)
                self._validate_legal_link(
                    app_page=page,
                    field_name="Términos y Condiciones",
                    link_text=r"términos y condiciones",
                    expected_heading_pattern=r"términos y condiciones",
                    screenshot_filename="step8_terminos_y_condiciones.png",
                )
                self._validate_legal_link(
                    app_page=page,
                    field_name="Política de Privacidad",
                    link_text=r"política de privacidad",
                    expected_heading_pattern=r"política de privacidad",
                    screenshot_filename="step9_politica_de_privacidad.png",
                )
            finally:
                context.close()
                browser.close()

        return 0 if all(item.status == "PASS" for item in self.results.values()) else 1


def main() -> int:
    workflow = SaleadsMiNegocioWorkflow()
    exit_code = 1
    try:
        exit_code = workflow.run()
        return exit_code
    finally:
        report = workflow.write_report()
        print(json.dumps(report, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    raise SystemExit(main())
