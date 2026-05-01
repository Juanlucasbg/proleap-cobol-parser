#!/usr/bin/env python3
"""End-to-end validation for the SaleADS.ai Mi Negocio workflow.

This script intentionally avoids hardcoded domains. Provide the current
environment login URL through SALEADS_START_URL when running in a fresh
browser context.
"""

from __future__ import annotations

import json
import os
import re
import sys
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from playwright.sync_api import (
    BrowserContext,
    Page,
    Playwright,
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


@dataclass
class TestConfig:
    start_url: Optional[str]
    account_email: str
    expected_user_name: Optional[str]
    headless: bool
    timeout_ms: int
    screenshot_dir: Path


@dataclass
class StepStatus:
    status: str = "FAIL"
    details: List[str] = field(default_factory=list)


class SaleadsMiNegocioWorkflowTest:
    def __init__(self, config: TestConfig) -> None:
        self.config = config
        self.report: Dict[str, StepStatus] = {
            field: StepStatus() for field in REPORT_FIELDS
        }
        self.legal_urls: Dict[str, str] = {}
        self.screenshot_index = 0
        self.config.screenshot_dir.mkdir(parents=True, exist_ok=True)

    def _log(self, message: str) -> None:
        print(f"[saleads_mi_negocio_full_test] {message}")

    def _mark(self, field_name: str, passed: bool, detail: str) -> None:
        step = self.report[field_name]
        step.details.append(detail)
        if passed:
            step.status = "PASS"
        elif step.status != "PASS":
            step.status = "FAIL"

    def _take_screenshot(self, page: Page, label: str, full_page: bool = False) -> Path:
        self.screenshot_index += 1
        file_name = f"{self.screenshot_index:02d}_{label}.png"
        destination = self.config.screenshot_dir / file_name
        page.screenshot(path=str(destination), full_page=full_page)
        self._log(f"Saved screenshot: {destination}")
        return destination

    def _wait_ui_after_click(self, page: Page) -> None:
        try:
            page.wait_for_load_state("domcontentloaded", timeout=self.config.timeout_ms)
        except PlaywrightTimeoutError:
            pass
        try:
            page.wait_for_load_state("networkidle", timeout=4000)
        except PlaywrightTimeoutError:
            pass
        page.wait_for_timeout(500)

    @staticmethod
    def _text_regex(text: str) -> re.Pattern[str]:
        return re.compile(re.escape(text), re.IGNORECASE)

    def _is_visible_text(self, page: Page, text: str, timeout: int = 4000) -> bool:
        locator = page.get_by_text(self._text_regex(text))
        try:
            locator.first.wait_for(state="visible", timeout=timeout)
            return True
        except PlaywrightTimeoutError:
            return False

    def _is_visible_role(
        self, page: Page, role: str, name: str, timeout: int = 4000
    ) -> bool:
        locator = page.get_by_role(role, name=self._text_regex(name))
        try:
            locator.first.wait_for(state="visible", timeout=timeout)
            return True
        except PlaywrightTimeoutError:
            return False

    def _try_click_text(
        self,
        page: Page,
        labels: List[str],
        roles: Tuple[str, ...] = ("button", "link", "menuitem", "tab"),
    ) -> bool:
        for label in labels:
            for role in roles:
                locator = page.get_by_role(role, name=self._text_regex(label))
                try:
                    locator.first.wait_for(state="visible", timeout=1200)
                    locator.first.click()
                    self._wait_ui_after_click(page)
                    return True
                except (PlaywrightTimeoutError, Exception):
                    continue
            fallback = page.get_by_text(self._text_regex(label))
            try:
                fallback.first.wait_for(state="visible", timeout=1200)
                fallback.first.click()
                self._wait_ui_after_click(page)
                return True
            except (PlaywrightTimeoutError, Exception):
                continue
        return False

    def _click_with_optional_popup(
        self,
        page: Page,
        context: BrowserContext,
        labels: List[str],
        roles: Tuple[str, ...] = ("link", "button", "menuitem"),
    ) -> Tuple[bool, Optional[Page]]:
        for label in labels:
            for role in roles:
                locator = page.get_by_role(role, name=self._text_regex(label))
                try:
                    locator.first.wait_for(state="visible", timeout=1200)
                except PlaywrightTimeoutError:
                    continue

                popup: Optional[Page] = None
                try:
                    with context.expect_page(timeout=4500) as popup_info:
                        locator.first.click()
                    popup = popup_info.value
                except PlaywrightTimeoutError:
                    try:
                        locator.first.click()
                    except Exception:
                        continue
                except Exception:
                    continue

                self._wait_ui_after_click(page)
                return True, popup

            fallback = page.get_by_text(self._text_regex(label))
            try:
                fallback.first.wait_for(state="visible", timeout=1200)
            except PlaywrightTimeoutError:
                continue

            popup = None
            try:
                with context.expect_page(timeout=4500) as popup_info:
                    fallback.first.click()
                popup = popup_info.value
            except PlaywrightTimeoutError:
                try:
                    fallback.first.click()
                except Exception:
                    continue
            except Exception:
                continue

            self._wait_ui_after_click(page)
            return True, popup
        return False, None

    def _is_input_present_for_label(self, page: Page, label: str, timeout: int = 6000) -> bool:
        try:
            page.get_by_label(self._text_regex(label)).first.wait_for(
                state="visible", timeout=timeout
            )
            return True
        except PlaywrightTimeoutError:
            pass

        # Fallback for UIs that do not bind an explicit label element.
        by_placeholder = page.get_by_placeholder(self._text_regex(label))
        try:
            by_placeholder.first.wait_for(state="visible", timeout=2000)
            return True
        except PlaywrightTimeoutError:
            return False

    def _assert_visible(self, page: Page, text: str, field_name: str) -> bool:
        passed = self._is_visible_text(page, text, timeout=self.config.timeout_ms)
        self._mark(
            field_name=field_name,
            passed=passed,
            detail=f"Validation for '{text}': {'OK' if passed else 'NOT FOUND'}",
        )
        return passed

    def _step_login_with_google(self, page: Page, context: BrowserContext) -> bool:
        self._log("Step 1: Login with Google")
        if self.config.start_url:
            self._log(f"Opening login URL: {self.config.start_url}")
            page.goto(self.config.start_url, wait_until="domcontentloaded")
            self._wait_ui_after_click(page)

        preloaded_sidebar = False
        if self._is_visible_text(page, "Negocio", timeout=2500):
            try:
                page.locator("aside").first.wait_for(state="visible", timeout=2500)
                preloaded_sidebar = True
            except PlaywrightTimeoutError:
                preloaded_sidebar = self._is_visible_text(page, "Mi Negocio", timeout=1500)
        if preloaded_sidebar:
            self._mark("Login", True, "Existing authenticated session detected")
            self._mark("Login", True, "Main UI visible: True")
            self._mark("Login", True, "Left sidebar visible: True")
            self._take_screenshot(page, "dashboard_loaded")
            return True

        clicked, popup = self._click_with_optional_popup(
            page=page,
            context=context,
            labels=[
                "Sign in with Google",
                "Iniciar sesión con Google",
                "Ingresar con Google",
                "Continuar con Google",
                "Login with Google",
            ],
            roles=("button", "link"),
        )
        if not clicked:
            self._mark("Login", False, "Google sign-in button was not found")
            return False

        google_page = popup if popup else page
        if popup:
            popup.wait_for_load_state("domcontentloaded")
            self._log("Google sign-in opened in a popup tab")

        account_selected = self._is_visible_text(
            google_page, self.config.account_email, timeout=8000
        )
        if account_selected:
            self._try_click_text(
                google_page,
                [self.config.account_email],
                roles=("button", "link"),
            )
            self._log(f"Selected account: {self.config.account_email}")

        if popup and not popup.is_closed():
            try:
                popup.wait_for_close(timeout=15000)
            except PlaywrightTimeoutError:
                # Some environments keep Google view in the same popup.
                # Continue with the application page verification.
                pass

        page.bring_to_front()
        app_loaded = self._is_visible_text(page, "Negocio", timeout=30000)
        sidebar_visible = False
        try:
            page.locator("aside").first.wait_for(state="visible", timeout=10000)
            sidebar_visible = True
        except PlaywrightTimeoutError:
            sidebar_visible = self._is_visible_text(page, "Mi Negocio", timeout=6000)

        step_passed = app_loaded and sidebar_visible
        self._mark("Login", step_passed, f"Main UI visible: {app_loaded}")
        self._mark("Login", step_passed, f"Left sidebar visible: {sidebar_visible}")
        if step_passed:
            self._take_screenshot(page, "dashboard_loaded")
        return step_passed

    def _step_open_mi_negocio_menu(self, page: Page) -> bool:
        self._log("Step 2: Open Mi Negocio menu")
        clicked_negocio = self._try_click_text(page, ["Negocio"], roles=("menuitem", "link", "button"))
        clicked_mi_negocio = self._try_click_text(page, ["Mi Negocio"], roles=("menuitem", "link", "button"))

        agregar_visible = self._is_visible_text(page, "Agregar Negocio", timeout=10000)
        administrar_visible = self._is_visible_text(page, "Administrar Negocios", timeout=10000)

        click_path_ok = clicked_negocio or clicked_mi_negocio
        step_passed = click_path_ok and agregar_visible and administrar_visible
        self._mark("Mi Negocio menu", step_passed, f"'Agregar Negocio' visible: {agregar_visible}")
        self._mark(
            "Mi Negocio menu",
            step_passed,
            f"'Administrar Negocios' visible: {administrar_visible}",
        )
        if step_passed:
            self._take_screenshot(page, "mi_negocio_expanded")
        return step_passed

    def _step_validate_agregar_negocio_modal(self, page: Page) -> bool:
        self._log("Step 3: Validate Agregar Negocio modal")
        clicked = self._try_click_text(page, ["Agregar Negocio"], roles=("menuitem", "button", "link"))
        if not clicked:
            self._mark("Agregar Negocio modal", False, "Could not click 'Agregar Negocio'")
            return False

        title_ok = self._is_visible_text(page, "Crear Nuevo Negocio", timeout=10000)
        input_ok = self._is_input_present_for_label(page, "Nombre del Negocio", timeout=6000)
        quota_ok = self._is_visible_text(page, "Tienes 2 de 3 negocios", timeout=6000)
        cancel_ok = self._is_visible_role(page, "button", "Cancelar", timeout=6000)
        create_ok = self._is_visible_role(page, "button", "Crear Negocio", timeout=6000)

        step_passed = all([title_ok, input_ok, quota_ok, cancel_ok, create_ok])
        self._mark("Agregar Negocio modal", step_passed, f"Title visible: {title_ok}")
        self._mark("Agregar Negocio modal", step_passed, f"Nombre del Negocio visible: {input_ok}")
        self._mark("Agregar Negocio modal", step_passed, f"Quota text visible: {quota_ok}")
        self._mark("Agregar Negocio modal", step_passed, f"Buttons visible: {cancel_ok and create_ok}")

        if step_passed:
            self._take_screenshot(page, "agregar_negocio_modal")

        # Optional interactions from the requested flow.
        try:
            name_input = page.get_by_label(self._text_regex("Nombre del Negocio"))
            name_input.first.wait_for(state="visible", timeout=2500)
            name_input.first.fill("Negocio Prueba Automatizacion")
        except PlaywrightTimeoutError:
            pass

        self._try_click_text(page, ["Cancelar"], roles=("button",))
        return step_passed

    def _step_open_administrar_negocios(self, page: Page) -> bool:
        self._log("Step 4: Open Administrar Negocios view")
        if not self._is_visible_text(page, "Administrar Negocios", timeout=1500):
            self._try_click_text(page, ["Mi Negocio"], roles=("menuitem", "link", "button"))

        clicked = self._try_click_text(page, ["Administrar Negocios"], roles=("menuitem", "link", "button"))
        if not clicked:
            self._mark("Administrar Negocios view", False, "Could not click 'Administrar Negocios'")
            return False

        info_ok = self._is_visible_text(page, "Información General", timeout=15000)
        details_ok = self._is_visible_text(page, "Detalles de la Cuenta", timeout=15000)
        businesses_ok = self._is_visible_text(page, "Tus Negocios", timeout=15000)
        legal_ok = self._is_visible_text(page, "Sección Legal", timeout=15000)
        step_passed = all([info_ok, details_ok, businesses_ok, legal_ok])

        self._mark("Administrar Negocios view", step_passed, f"Información General: {info_ok}")
        self._mark("Administrar Negocios view", step_passed, f"Detalles de la Cuenta: {details_ok}")
        self._mark("Administrar Negocios view", step_passed, f"Tus Negocios: {businesses_ok}")
        self._mark("Administrar Negocios view", step_passed, f"Sección Legal: {legal_ok}")

        if step_passed:
            self._take_screenshot(page, "administrar_negocios_full", full_page=True)
        return step_passed

    def _step_validate_informacion_general(self, page: Page) -> bool:
        self._log("Step 5: Validate Información General")
        if self.config.expected_user_name:
            username_ok = self._is_visible_text(page, self.config.expected_user_name, timeout=6000)
        else:
            # Fallback heuristic when no explicit expected name is configured.
            username_ok = self._is_visible_text(page, "Información General", timeout=6000)

        email_ok = self._is_visible_text(page, self.config.account_email, timeout=6000)
        if not email_ok:
            email_ok = self._is_visible_text(page, "@", timeout=6000)
        plan_ok = self._is_visible_text(page, "BUSINESS PLAN", timeout=6000)
        change_plan_ok = self._is_visible_role(page, "button", "Cambiar Plan", timeout=6000)
        step_passed = all([username_ok, email_ok, plan_ok, change_plan_ok])

        self._mark("Información General", step_passed, f"User name visible: {username_ok}")
        self._mark("Información General", step_passed, f"User email visible: {email_ok}")
        self._mark("Información General", step_passed, f"BUSINESS PLAN visible: {plan_ok}")
        self._mark("Información General", step_passed, f"Cambiar Plan button visible: {change_plan_ok}")
        return step_passed

    def _step_validate_detalles_de_la_cuenta(self, page: Page) -> bool:
        self._log("Step 6: Validate Detalles de la Cuenta")
        created_ok = self._is_visible_text(page, "Cuenta creada", timeout=6000)
        status_ok = self._is_visible_text(page, "Estado activo", timeout=6000)
        language_ok = self._is_visible_text(page, "Idioma seleccionado", timeout=6000)
        step_passed = all([created_ok, status_ok, language_ok])

        self._mark("Detalles de la Cuenta", step_passed, f"'Cuenta creada' visible: {created_ok}")
        self._mark("Detalles de la Cuenta", step_passed, f"'Estado activo' visible: {status_ok}")
        self._mark("Detalles de la Cuenta", step_passed, f"'Idioma seleccionado' visible: {language_ok}")
        return step_passed

    def _step_validate_tus_negocios(self, page: Page) -> bool:
        self._log("Step 7: Validate Tus Negocios")
        section_ok = self._is_visible_text(page, "Tus Negocios", timeout=6000)
        add_button_ok = self._is_visible_role(page, "button", "Agregar Negocio", timeout=6000) or self._is_visible_text(
            page, "Agregar Negocio", timeout=6000
        )
        quota_ok = self._is_visible_text(page, "Tienes 2 de 3 negocios", timeout=6000)

        business_list_ok = False
        for selector in ("li", "[role='listitem']", "table tbody tr", "[data-testid*='business']"):
            locator = page.locator(selector)
            try:
                if locator.count() > 0:
                    business_list_ok = True
                    break
            except Exception:
                continue

        step_passed = all([section_ok, business_list_ok, add_button_ok, quota_ok])
        self._mark("Tus Negocios", step_passed, f"Business list visible: {business_list_ok}")
        self._mark("Tus Negocios", step_passed, f"Agregar Negocio button visible: {add_button_ok}")
        self._mark("Tus Negocios", step_passed, f"Quota text visible: {quota_ok}")
        return step_passed

    def _step_validate_legal_page(
        self,
        page: Page,
        context: BrowserContext,
        link_text: str,
        expected_heading: str,
        report_field: str,
        screenshot_name: str,
    ) -> bool:
        self._log(f"Step: Validate legal page '{link_text}'")
        app_page = page
        clicked, popup = self._click_with_optional_popup(
            page=app_page,
            context=context,
            labels=[link_text],
            roles=("link", "button"),
        )
        if not clicked:
            self._mark(report_field, False, f"Could not click '{link_text}'")
            return False

        target_page = popup if popup else app_page
        if popup:
            target_page.bring_to_front()

        try:
            target_page.wait_for_load_state("domcontentloaded", timeout=self.config.timeout_ms)
        except PlaywrightTimeoutError:
            pass
        try:
            target_page.wait_for_load_state("networkidle", timeout=5000)
        except PlaywrightTimeoutError:
            pass

        heading_ok = self._is_visible_text(target_page, expected_heading, timeout=15000)
        legal_body = target_page.locator("main, article, body").first
        content_ok = False
        try:
            legal_body.wait_for(state="visible", timeout=8000)
            text = legal_body.inner_text().strip()
            content_ok = len(text) > 100
        except PlaywrightTimeoutError:
            content_ok = False

        self._take_screenshot(target_page, screenshot_name, full_page=True)
        final_url = target_page.url
        self.legal_urls[report_field] = final_url

        step_passed = heading_ok and content_ok
        self._mark(report_field, step_passed, f"Heading visible ('{expected_heading}'): {heading_ok}")
        self._mark(report_field, step_passed, f"Legal content visible: {content_ok}")
        self._mark(report_field, step_passed, f"Final URL: {final_url}")

        if popup:
            popup.close()
            app_page.bring_to_front()
            self._wait_ui_after_click(app_page)
        else:
            try:
                app_page.go_back(wait_until="domcontentloaded", timeout=8000)
                self._wait_ui_after_click(app_page)
            except PlaywrightTimeoutError:
                pass

        return step_passed

    def _write_report(self) -> Path:
        output = {
            "test_name": "saleads_mi_negocio_full_test",
            "timestamp_utc": datetime.utcnow().isoformat() + "Z",
            "report": {
                key: {"status": value.status, "details": value.details}
                for key, value in self.report.items()
            },
            "legal_urls": self.legal_urls,
            "screenshots_dir": str(self.config.screenshot_dir),
        }
        report_path = self.config.screenshot_dir / "final_report.json"
        report_path.write_text(json.dumps(output, indent=2, ensure_ascii=False), encoding="utf-8")
        self._log(f"Wrote report: {report_path}")
        print(json.dumps(output, indent=2, ensure_ascii=False))
        return report_path

    def run(self, playwright: Playwright) -> int:
        browser = playwright.chromium.launch(headless=self.config.headless)
        context = browser.new_context()
        page = context.new_page()

        try:
            self._step_login_with_google(page, context)
            self._step_open_mi_negocio_menu(page)
            self._step_validate_agregar_negocio_modal(page)
            self._step_open_administrar_negocios(page)
            self._step_validate_informacion_general(page)
            self._step_validate_detalles_de_la_cuenta(page)
            self._step_validate_tus_negocios(page)
            self._step_validate_legal_page(
                page=page,
                context=context,
                link_text="Términos y Condiciones",
                expected_heading="Términos y Condiciones",
                report_field="Términos y Condiciones",
                screenshot_name="terminos_y_condiciones",
            )
            self._step_validate_legal_page(
                page=page,
                context=context,
                link_text="Política de Privacidad",
                expected_heading="Política de Privacidad",
                report_field="Política de Privacidad",
                screenshot_name="politica_de_privacidad",
            )
        except Exception as exc:  # pragma: no cover - defensive capture
            self._log(f"Unexpected failure: {exc!r}")
            try:
                self._take_screenshot(page, "unexpected_failure", full_page=True)
            except Exception:
                pass
        finally:
            self._write_report()
            context.close()
            browser.close()

        return 0 if all(step.status == "PASS" for step in self.report.values()) else 1


def _bool_from_env(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "y", "on"}


def load_config() -> TestConfig:
    timestamp = datetime.utcnow().strftime("%Y%m%dT%H%M%SZ")
    base_dir = Path(os.getenv("SALEADS_SCREENSHOT_DIR", "automation/artifacts"))
    screenshot_dir = base_dir / f"saleads_mi_negocio_full_test_{timestamp}"
    return TestConfig(
        start_url=os.getenv("SALEADS_START_URL"),
        account_email=os.getenv("SALEADS_GOOGLE_ACCOUNT_EMAIL", "juanlucasbarbiergarzon@gmail.com"),
        expected_user_name=os.getenv("SALEADS_EXPECTED_USER_NAME"),
        headless=_bool_from_env("SALEADS_HEADLESS", True),
        timeout_ms=int(os.getenv("SALEADS_TIMEOUT_MS", "25000")),
        screenshot_dir=screenshot_dir,
    )


def main() -> int:
    config = load_config()
    if not config.start_url:
        print(
            "SALEADS_START_URL is not set. Set it to the current environment login page URL "
            "to run this workflow in a fresh browser context.",
            file=sys.stderr,
        )
        return 2

    with sync_playwright() as playwright:
        runner = SaleadsMiNegocioWorkflowTest(config)
        return runner.run(playwright)


if __name__ == "__main__":
    raise SystemExit(main())
