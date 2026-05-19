#!/usr/bin/env python3
import json
import os
import re
import sys
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable

from playwright.sync_api import TimeoutError as PlaywrightTimeoutError
from playwright.sync_api import sync_playwright


ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com"
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
    status: str = "FAIL"
    details: str = ""
    evidence: list[str] = field(default_factory=list)
    extra: dict[str, str] = field(default_factory=dict)


class SaleadsMiNegocioRunner:
    def __init__(self) -> None:
        ts = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
        self.output_dir = Path("target") / "saleads_mi_negocio_full_test" / ts
        self.output_dir.mkdir(parents=True, exist_ok=True)
        self.results: dict[str, StepResult] = {}
        self.final_report_path = self.output_dir / "final_report.json"
        self.browser = None
        self.context = None
        self.page = None
        self.headless = os.getenv("SALEADS_HEADLESS", "true").lower() != "false"
        self.login_url = (
            os.getenv("SALEADS_LOGIN_URL")
            or os.getenv("SALEADS_BASE_URL")
            or os.getenv("SALEADS_URL")
        )
        self.cdp_url = os.getenv("SALEADS_CDP_URL")

    def run(self) -> int:
        with sync_playwright() as playwright:
            try:
                self._start_browser(playwright)
            except Exception as exc:
                error_message = f"Startup failed: {exc}"
                for field in REPORT_FIELDS:
                    self.results[field] = StepResult(name=field, status="FAIL", details=error_message)
                self._write_report()
                return 1

            self._run_step("Login", self.step_login_with_google)
            self._run_step("Mi Negocio menu", self.step_open_mi_negocio_menu)
            self._run_step("Agregar Negocio modal", self.step_validate_agregar_negocio_modal)
            self._run_step("Administrar Negocios view", self.step_open_administrar_negocios)
            self._run_step("Información General", self.step_validate_informacion_general)
            self._run_step("Detalles de la Cuenta", self.step_validate_detalles_cuenta)
            self._run_step("Tus Negocios", self.step_validate_tus_negocios)
            self._run_step("Términos y Condiciones", self.step_validate_terminos)
            self._run_step("Política de Privacidad", self.step_validate_politica)
            self._write_report()
            self._close_browser()

        return 0 if self._all_passed() else 1

    def _start_browser(self, playwright) -> None:
        if self.cdp_url:
            self.browser = playwright.chromium.connect_over_cdp(self.cdp_url)
            self.context = self.browser.contexts[0] if self.browser.contexts else self.browser.new_context()
            self.page = self.context.pages[0] if self.context.pages else self.context.new_page()
            return

        self.browser = playwright.chromium.launch(headless=self.headless)
        self.context = self.browser.new_context()
        self.page = self.context.new_page()

        if not self.login_url:
            raise RuntimeError(
                "Missing target page. Set SALEADS_LOGIN_URL/SALEADS_BASE_URL/SALEADS_URL "
                "or connect to an existing browser with SALEADS_CDP_URL."
            )

        self.page.goto(self.login_url, wait_until="domcontentloaded", timeout=60000)
        self._wait_for_ui()

    def _close_browser(self) -> None:
        if self.browser:
            self.browser.close()

    def _run_step(self, name: str, fn: Callable[[], StepResult]) -> None:
        try:
            result = fn()
            result.name = name
            result.status = "PASS"
            self.results[name] = result
            print(f"[PASS] {name}")
        except Exception as exc:
            failed = StepResult(name=name, status="FAIL", details=str(exc))
            if self.page:
                screenshot_name = re.sub(r"[^a-z0-9]+", "_", name.lower()).strip("_")
                try:
                    failed.evidence.append(self._screenshot(f"fail_{screenshot_name}", full_page=True))
                except Exception:
                    pass
            self.results[name] = failed
            print(f"[FAIL] {name}: {exc}")

    def _all_passed(self) -> bool:
        return all(result.status == "PASS" for result in self.results.values())

    def _wait_for_ui(self, page=None) -> None:
        current_page = page or self.page
        try:
            current_page.wait_for_load_state("domcontentloaded", timeout=15000)
        except PlaywrightTimeoutError:
            pass
        current_page.wait_for_timeout(900)

    def _is_cloudflare_525(self, page=None) -> bool:
        current_page = page or self.page
        try:
            body_text = current_page.locator("body").inner_text(timeout=8000)
        except Exception:
            return False
        return "SSL handshake failed" in body_text and "Error code 525" in body_text

    def _is_visible(self, locator, timeout: int = 10000) -> bool:
        try:
            return locator.first.is_visible(timeout=timeout)
        except Exception:
            return False

    def _require_visible(self, locator, message: str, timeout: int = 10000) -> None:
        if not self._is_visible(locator, timeout=timeout):
            raise AssertionError(message)

    def _screenshot(self, name: str, full_page: bool = False, page=None) -> str:
        current_page = page or self.page
        file_path = self.output_dir / f"{name}.png"
        current_page.screenshot(path=str(file_path), full_page=full_page)
        return str(file_path)

    def _click_text(self, text: str, exact: bool = True) -> None:
        locator = self.page.get_by_text(text, exact=exact).first
        self._require_visible(locator, f"Could not find clickable text: {text}")
        locator.click()
        self._wait_for_ui()

    def _click_legal_link_and_validate(self, link_text: str, heading_text: str, screenshot_name: str) -> StepResult:
        link = self.page.get_by_text(link_text, exact=True).first
        self._require_visible(link, f"Link '{link_text}' is not visible", timeout=15000)

        opened_new_tab = False
        legal_page = self.page
        app_url_before = self.page.url

        try:
            with self.context.expect_page(timeout=8000) as new_page_info:
                link.click()
            legal_page = new_page_info.value
            opened_new_tab = True
            self._wait_for_ui(legal_page)
        except PlaywrightTimeoutError:
            link.click()
            self._wait_for_ui(self.page)
            legal_page = self.page

        heading = legal_page.get_by_text(re.compile(heading_text, re.IGNORECASE)).first
        self._require_visible(heading, f"Heading '{heading_text}' is not visible on legal page", timeout=20000)

        body_text = legal_page.locator("body").inner_text(timeout=15000).strip()
        if len(body_text) < 100:
            raise AssertionError(f"Legal content for '{link_text}' seems too short")

        shot = self._screenshot(screenshot_name, full_page=True, page=legal_page)
        final_url = legal_page.url

        if opened_new_tab:
            legal_page.close()
            self.page.bring_to_front()
            self._wait_for_ui(self.page)
        elif final_url != app_url_before:
            self.page.go_back(wait_until="domcontentloaded")
            self._wait_for_ui()

        result = StepResult(name="")
        result.evidence.append(shot)
        result.extra["final_url"] = final_url
        return result

    def step_login_with_google(self) -> StepResult:
        result = StepResult(name="")
        result.evidence.append(self._screenshot("00_initial_page", full_page=True))

        if self._is_cloudflare_525():
            raise AssertionError(
                "App is unreachable due to Cloudflare 525 SSL handshake failure. "
                "Use another SaleADS environment URL or a healthy SALEADS_CDP_URL session."
            )

        sign_in_entry = self.page.get_by_role(
            "link",
            name=re.compile(r"sign in|inicia sesi[oó]n", re.IGNORECASE),
        ).first
        if self._is_visible(sign_in_entry, timeout=4000):
            sign_in_entry.click()
            self._wait_for_ui()

        login_button = self.page.get_by_role(
            "button",
            name=re.compile(
                r"sign in with google|iniciar sesi[oó]n con google|continuar con google|google",
                re.IGNORECASE,
            ),
        ).first
        if not self._is_visible(login_button, timeout=10000):
            login_button = self.page.get_by_role("link", name=re.compile("google", re.IGNORECASE)).first
        if not self._is_visible(login_button, timeout=4000):
            login_button = self.page.get_by_text(re.compile("google", re.IGNORECASE)).first

        self._require_visible(login_button, "Login button with Google is not visible")

        popup_page = None
        try:
            with self.context.expect_page(timeout=8000) as new_page_info:
                login_button.click()
            popup_page = new_page_info.value
            self._wait_for_ui(popup_page)
        except PlaywrightTimeoutError:
            login_button.click()
            self._wait_for_ui(self.page)

        account_page = popup_page or self.page
        account_locator = account_page.get_by_text(ACCOUNT_EMAIL, exact=True).first
        if self._is_visible(account_locator, timeout=12000):
            account_locator.click()
            self._wait_for_ui(account_page)

        if popup_page:
            try:
                popup_page.wait_for_close(timeout=20000)
            except PlaywrightTimeoutError:
                pass

        if self._is_cloudflare_525():
            raise AssertionError(
                "Reached Cloudflare 525 SSL handshake error after attempting login. "
                "The target SaleADS app environment is unavailable."
            )

        sidebar = self.page.locator("aside").first
        self._require_visible(sidebar, "Main app sidebar is not visible after login", timeout=30000)
        negocio_text = self.page.get_by_text(re.compile("Negocio", re.IGNORECASE)).first
        self._require_visible(negocio_text, "Could not confirm main app interface after login", timeout=20000)

        result.evidence.append(self._screenshot("01_dashboard_loaded", full_page=True))
        return result

    def step_open_mi_negocio_menu(self) -> StepResult:
        result = StepResult(name="")
        self._click_text("Negocio")
        self._click_text("Mi Negocio")

        agregar = self.page.get_by_text("Agregar Negocio", exact=True).first
        administrar = self.page.get_by_text("Administrar Negocios", exact=True).first
        self._require_visible(agregar, "'Agregar Negocio' is not visible")
        self._require_visible(administrar, "'Administrar Negocios' is not visible")

        result.evidence.append(self._screenshot("02_mi_negocio_menu_expanded"))
        return result

    def step_validate_agregar_negocio_modal(self) -> StepResult:
        result = StepResult(name="")
        self._click_text("Agregar Negocio")

        modal_title = self.page.get_by_text("Crear Nuevo Negocio", exact=True).first
        self._require_visible(modal_title, "Modal title 'Crear Nuevo Negocio' is not visible", timeout=15000)

        name_input = self.page.get_by_label(re.compile("Nombre del Negocio", re.IGNORECASE)).first
        if not self._is_visible(name_input):
            name_input = self.page.get_by_placeholder(re.compile("Nombre del Negocio", re.IGNORECASE)).first
        self._require_visible(name_input, "Input 'Nombre del Negocio' is not visible")

        business_limit = self.page.get_by_text("Tienes 2 de 3 negocios", exact=True).first
        self._require_visible(business_limit, "Text 'Tienes 2 de 3 negocios' is not visible")

        cancel_btn = self.page.get_by_role("button", name="Cancelar").first
        create_btn = self.page.get_by_role("button", name="Crear Negocio").first
        self._require_visible(cancel_btn, "Button 'Cancelar' is missing")
        self._require_visible(create_btn, "Button 'Crear Negocio' is missing")

        name_input.click()
        name_input.fill("Negocio Prueba Automatizacion")
        result.evidence.append(self._screenshot("03_agregar_negocio_modal"))

        cancel_btn.click()
        self._wait_for_ui()
        return result

    def step_open_administrar_negocios(self) -> StepResult:
        result = StepResult(name="")
        self._click_text("Mi Negocio")
        self._click_text("Administrar Negocios")

        self._require_visible(
            self.page.get_by_text("Información General", exact=True).first,
            "Section 'Información General' is missing",
            timeout=25000,
        )
        self._require_visible(
            self.page.get_by_text("Detalles de la Cuenta", exact=True).first,
            "Section 'Detalles de la Cuenta' is missing",
        )
        self._require_visible(
            self.page.get_by_text("Tus Negocios", exact=True).first,
            "Section 'Tus Negocios' is missing",
        )
        self._require_visible(
            self.page.get_by_text("Sección Legal", exact=True).first,
            "Section 'Sección Legal' is missing",
        )

        result.evidence.append(self._screenshot("04_administrar_negocios_page", full_page=True))
        return result

    def step_validate_informacion_general(self) -> StepResult:
        result = StepResult(name="")
        self._require_visible(
            self.page.get_by_text("Información General", exact=True).first,
            "'Información General' heading is not visible",
        )
        self._require_visible(self.page.get_by_text("BUSINESS PLAN", exact=True).first, "Text 'BUSINESS PLAN' missing")
        self._require_visible(
            self.page.get_by_role("button", name="Cambiar Plan").first,
            "Button 'Cambiar Plan' is not visible",
        )

        page_text = self.page.locator("body").inner_text()
        email_match = re.search(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", page_text)
        if not email_match:
            raise AssertionError("User email is not visible")

        if ACCOUNT_EMAIL.lower() not in page_text.lower():
            raise AssertionError(f"Expected user email '{ACCOUNT_EMAIL}' is not visible")

        if not re.search(r"juan|lucas|barbier|garzon", page_text, re.IGNORECASE):
            raise AssertionError("User name is not clearly visible")

        return result

    def step_validate_detalles_cuenta(self) -> StepResult:
        result = StepResult(name="")
        self._require_visible(self.page.get_by_text("Cuenta creada", exact=True).first, "'Cuenta creada' missing")
        self._require_visible(self.page.get_by_text("Estado activo", exact=True).first, "'Estado activo' missing")
        self._require_visible(
            self.page.get_by_text("Idioma seleccionado", exact=True).first,
            "'Idioma seleccionado' missing",
        )
        return result

    def step_validate_tus_negocios(self) -> StepResult:
        result = StepResult(name="")
        self._require_visible(self.page.get_by_text("Tus Negocios", exact=True).first, "'Tus Negocios' heading missing")
        self._require_visible(
            self.page.get_by_role("button", name="Agregar Negocio").first,
            "Button 'Agregar Negocio' in businesses section missing",
        )
        self._require_visible(
            self.page.get_by_text("Tienes 2 de 3 negocios", exact=True).first,
            "Business limit text is missing in 'Tus Negocios'",
        )

        possible_items = self.page.locator("li, tr, [class*='business'], [data-testid*='business']")
        if possible_items.count() < 1:
            raise AssertionError("Business list is not visibly populated")

        return result

    def step_validate_terminos(self) -> StepResult:
        return self._click_legal_link_and_validate(
            link_text="Términos y Condiciones",
            heading_text=r"Términos y Condiciones",
            screenshot_name="08_terminos_y_condiciones",
        )

    def step_validate_politica(self) -> StepResult:
        return self._click_legal_link_and_validate(
            link_text="Política de Privacidad",
            heading_text=r"Política de Privacidad",
            screenshot_name="09_politica_de_privacidad",
        )

    def _write_report(self) -> None:
        report = {
            "test_name": "saleads_mi_negocio_full_test",
            "generated_at_utc": datetime.now(timezone.utc).isoformat(),
            "output_dir": str(self.output_dir),
            "summary": {field: self.results.get(field, StepResult(field)).status for field in REPORT_FIELDS},
            "details": {
                field: {
                    "status": self.results.get(field, StepResult(field)).status,
                    "details": self.results.get(field, StepResult(field)).details,
                    "evidence": self.results.get(field, StepResult(field)).evidence,
                    "extra": self.results.get(field, StepResult(field)).extra,
                }
                for field in REPORT_FIELDS
            },
            "overall_status": "PASS" if self._all_passed() else "FAIL",
        }

        self.final_report_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
        print(f"\nFinal report written to: {self.final_report_path}")
        print(json.dumps(report["summary"], indent=2, ensure_ascii=False))


def main() -> int:
    runner = SaleadsMiNegocioRunner()
    return runner.run()


if __name__ == "__main__":
    sys.exit(main())
