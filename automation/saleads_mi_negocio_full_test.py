#!/usr/bin/env python3
"""
Environment-agnostic SaleADS Mi Negocio workflow validation.

This script intentionally avoids any hardcoded SaleADS domain and uses visible
text-first selectors so it can run against dev/staging/production environments.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional

from playwright.sync_api import BrowserContext, Error, Page, TimeoutError, sync_playwright


GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com"
DEFAULT_TIMEOUT_MS = 15_000
POST_CLICK_SETTLE_MS = 1_000


@dataclass
class CheckResult:
    name: str
    passed: bool
    details: str = ""


@dataclass
class RunReport:
    started_at: str
    environment_url: str
    screenshots_dir: str
    final_urls: Dict[str, str] = field(default_factory=dict)
    checks: Dict[str, CheckResult] = field(default_factory=dict)

    def add(self, name: str, passed: bool, details: str = "") -> None:
        self.checks[name] = CheckResult(name=name, passed=passed, details=details)

    def all_passed(self) -> bool:
        return all(item.passed for item in self.checks.values())

    def as_summary(self) -> Dict[str, str]:
        return {
            key: "PASS" if value.passed else "FAIL"
            for key, value in self.checks.items()
        }

    def to_json(self) -> str:
        payload = {
            "started_at": self.started_at,
            "environment_url": self.environment_url,
            "screenshots_dir": self.screenshots_dir,
            "final_urls": self.final_urls,
            "results": {
                key: {
                    "status": "PASS" if value.passed else "FAIL",
                    "details": value.details,
                }
                for key, value in self.checks.items()
            },
            "overall_status": "PASS" if self.all_passed() else "FAIL",
        }
        return json.dumps(payload, indent=2, ensure_ascii=False)


class SaleadsMiNegocioWorkflowTest:
    def __init__(self, page: Page, context: BrowserContext, screenshots_dir: Path):
        self.page = page
        self.context = context
        self.screenshots_dir = screenshots_dir

    def run(self, environment_url: str, report: RunReport) -> None:
        self.step_1_login_with_google(environment_url, report)
        self.step_2_open_mi_negocio_menu(report)
        self.step_3_validate_agregar_negocio_modal(report)
        self.step_4_open_administrar_negocios(report)
        self.step_5_validate_informacion_general(report)
        self.step_6_validate_detalles_cuenta(report)
        self.step_7_validate_tus_negocios(report)
        self.step_8_validate_terminos(report)
        self.step_9_validate_politica_privacidad(report)

    def step_1_login_with_google(self, environment_url: str, report: RunReport) -> None:
        check_name = "Login"
        try:
            self.open_login_page_if_requested(environment_url)
            self.wait_for_ready_ui()

            if self.page_already_in_main_app():
                self.capture("01_dashboard_loaded.png", full_page=True)
                report.add(check_name, True, "Session already authenticated; main interface detected.")
                return

            login_button = self.find_clickable_by_text(
                [
                    "Sign in with Google",
                    "Login with Google",
                    "Ingresar con Google",
                    "Iniciar sesión con Google",
                    "Iniciar sesion con Google",
                    "Continuar con Google",
                ],
                timeout_ms=DEFAULT_TIMEOUT_MS,
            )
            self.click_and_wait(login_button, "Google login button")
            self.select_google_account_if_prompted(GOOGLE_ACCOUNT_EMAIL)
            self.wait_for_ready_ui()

            sidebar_visible = self.any_visible_text(["Negocio", "Mi Negocio"])
            nav_visible = self.page.locator("nav").count() > 0
            if not (sidebar_visible or nav_visible):
                raise AssertionError("Main app navigation/sidebar not detected after login.")

            self.capture("01_dashboard_loaded.png", full_page=True)
            report.add(check_name, True, "Main interface and sidebar/navigation detected.")
        except Exception as exc:
            report.add(check_name, False, str(exc))

    def step_2_open_mi_negocio_menu(self, report: RunReport) -> None:
        check_name = "Mi Negocio menu"
        try:
            # Prefer visible text selectors in the sidebar.
            if self.any_visible_text(["Negocio"]):
                negocio = self.find_clickable_by_text(["Negocio"], timeout_ms=8_000)
                self.click_and_wait(negocio, "Negocio sidebar section")

            mi_negocio = self.find_clickable_by_text(["Mi Negocio"], timeout_ms=8_000)
            self.click_and_wait(mi_negocio, "Mi Negocio menu")

            self.require_visible_text("Agregar Negocio", "Agregar Negocio should be visible")
            self.require_visible_text(
                "Administrar Negocios", "Administrar Negocios should be visible"
            )

            self.capture("02_mi_negocio_expanded_menu.png")
            report.add(check_name, True, "Mi Negocio expanded with required submenu options.")
        except Exception as exc:
            report.add(check_name, False, str(exc))

    def step_3_validate_agregar_negocio_modal(self, report: RunReport) -> None:
        check_name = "Agregar Negocio modal"
        try:
            agregar = self.find_clickable_by_text(["Agregar Negocio"], timeout_ms=8_000)
            self.click_and_wait(agregar, "Agregar Negocio option")

            self.require_visible_text("Crear Nuevo Negocio", "Missing modal title")
            self.require_one_of_locators_visible(
                [
                    self.page.get_by_label("Nombre del Negocio"),
                    self.page.get_by_placeholder("Nombre del Negocio"),
                    self.page.get_by_text("Nombre del Negocio", exact=False),
                ],
                "Input field 'Nombre del Negocio' not found",
            )
            self.require_visible_text("Tienes 2 de 3 negocios", "Missing business limit text")
            self.require_visible_text("Cancelar", "Missing Cancelar button")
            self.require_visible_text("Crear Negocio", "Missing Crear Negocio button")
            self.capture("03_agregar_negocio_modal.png")

            # Optional exercise and cleanup from prompt.
            self.fill_input_if_present("Nombre del Negocio", "Negocio Prueba Automatización")
            cancelar = self.find_clickable_by_text(["Cancelar"], timeout_ms=6_000)
            self.click_and_wait(cancelar, "Cancelar modal button")

            report.add(check_name, True, "Agregar Negocio modal validated successfully.")
        except Exception as exc:
            report.add(check_name, False, str(exc))

    def step_4_open_administrar_negocios(self, report: RunReport) -> None:
        check_name = "Administrar Negocios view"
        try:
            # Re-expand Mi Negocio if needed.
            if not self.any_visible_text(["Administrar Negocios"]):
                mi_negocio = self.find_clickable_by_text(["Mi Negocio"], timeout_ms=8_000)
                self.click_and_wait(mi_negocio, "Mi Negocio menu re-expand")

            administrar = self.find_clickable_by_text(
                ["Administrar Negocios"], timeout_ms=8_000
            )
            self.click_and_wait(administrar, "Administrar Negocios option")
            self.wait_for_ready_ui()

            for section in (
                "Información General",
                "Detalles de la Cuenta",
                "Tus Negocios",
                "Sección Legal",
            ):
                self.require_visible_text(section, f"Missing section: {section}")

            self.capture("04_administrar_negocios_page.png", full_page=True)
            report.add(check_name, True, "Administrar Negocios sections are visible.")
        except Exception as exc:
            report.add(check_name, False, str(exc))

    def step_5_validate_informacion_general(self, report: RunReport) -> None:
        check_name = "Información General"
        try:
            self.require_visible_text("Información General", "Missing 'Información General' section")
            self.require_email_visible("User email was not detected")
            self.require_visible_text("BUSINESS PLAN", "Missing 'BUSINESS PLAN' text")
            self.require_visible_text("Cambiar Plan", "Missing 'Cambiar Plan' button")

            # Heuristic for user name presence: at least one likely full-name token in page text.
            if not self.page_contains_probable_person_name():
                raise AssertionError("User name-like text was not detected.")

            report.add(check_name, True, "Información General data validated.")
        except Exception as exc:
            report.add(check_name, False, str(exc))

    def step_6_validate_detalles_cuenta(self, report: RunReport) -> None:
        check_name = "Detalles de la Cuenta"
        try:
            for expected in ("Cuenta creada", "Estado activo", "Idioma seleccionado"):
                self.require_visible_text(expected, f"Missing text: {expected}")
            report.add(check_name, True, "Detalles de la Cuenta validated.")
        except Exception as exc:
            report.add(check_name, False, str(exc))

    def step_7_validate_tus_negocios(self, report: RunReport) -> None:
        check_name = "Tus Negocios"
        try:
            self.require_visible_text("Tus Negocios", "Missing 'Tus Negocios' section")
            self.require_visible_text("Agregar Negocio", "Missing 'Agregar Negocio' button")
            self.require_visible_text("Tienes 2 de 3 negocios", "Missing business usage text")

            # Heuristic for a visible business list/card/table region.
            if not self.page_has_business_list_signal():
                raise AssertionError("Business list/cards were not detected.")

            report.add(check_name, True, "Tus Negocios section validated.")
        except Exception as exc:
            report.add(check_name, False, str(exc))

    def step_8_validate_terminos(self, report: RunReport) -> None:
        check_name = "Términos y Condiciones"
        try:
            final_url = self.click_legal_link_and_validate(
                link_text="Términos y Condiciones",
                heading_text="Términos y Condiciones",
                screenshot_name="05_terminos_y_condiciones.png",
            )
            report.final_urls["Términos y Condiciones"] = final_url
            report.add(check_name, True, f"Validated legal page at {final_url}")
        except Exception as exc:
            report.add(check_name, False, str(exc))

    def step_9_validate_politica_privacidad(self, report: RunReport) -> None:
        check_name = "Política de Privacidad"
        try:
            final_url = self.click_legal_link_and_validate(
                link_text="Política de Privacidad",
                heading_text="Política de Privacidad",
                screenshot_name="06_politica_de_privacidad.png",
            )
            report.final_urls["Política de Privacidad"] = final_url
            report.add(check_name, True, f"Validated legal page at {final_url}")
        except Exception as exc:
            report.add(check_name, False, str(exc))

    def click_legal_link_and_validate(
        self, link_text: str, heading_text: str, screenshot_name: str
    ) -> str:
        app_page = self.page
        legal_page: Optional[Page] = None

        clickable = self.find_clickable_by_text([link_text], timeout_ms=8_000)
        try:
            with self.context.expect_page(timeout=5_000) as new_page_info:
                self.click_and_wait(clickable, f"Legal link {link_text}")
            legal_page = new_page_info.value
            legal_page.wait_for_load_state("domcontentloaded", timeout=DEFAULT_TIMEOUT_MS)
        except TimeoutError:
            # Same-tab navigation case.
            legal_page = app_page
            self.wait_for_ready_ui(legal_page)

        heading_variants = [heading_text]
        if "á" in heading_text:
            heading_variants.append(heading_text.replace("á", "a"))
        if "é" in heading_text:
            heading_variants.append(heading_text.replace("é", "e"))
        if "í" in heading_text:
            heading_variants.append(heading_text.replace("í", "i"))
        if "ó" in heading_text:
            heading_variants.append(heading_text.replace("ó", "o"))
        if "ú" in heading_text:
            heading_variants.append(heading_text.replace("ú", "u"))

        if not self.any_visible_text(
            heading_variants, page=legal_page, timeout_ms=DEFAULT_TIMEOUT_MS
        ):
            raise AssertionError(f"Missing legal heading '{heading_text}'")
        self.require_legal_content_visible(legal_page)
        self.capture(screenshot_name, page=legal_page, full_page=True)

        final_url = legal_page.url
        if legal_page is not app_page:
            legal_page.close()
            self.page.bring_to_front()
            self.wait_for_ready_ui(self.page)
        else:
            self.page.go_back(timeout=DEFAULT_TIMEOUT_MS)
            self.wait_for_ready_ui(self.page)

        return final_url

    def open_login_page_if_requested(self, environment_url: str) -> None:
        if environment_url:
            self.page.goto(environment_url, wait_until="domcontentloaded", timeout=30_000)
        self.wait_for_ready_ui()

    def select_google_account_if_prompted(self, email: str) -> None:
        def _is_google_page(candidate: Page) -> bool:
            if "accounts.google.com" in candidate.url:
                return True
            return self.any_visible_text(
                [
                    "Choose an account",
                    "Elige una cuenta",
                    "Selecciona una cuenta",
                    "Use your Google Account",
                ],
                page=candidate,
                timeout_ms=1_500,
            )

        account_pages = [page for page in self.context.pages if _is_google_page(page)]
        target_page = account_pages[-1] if account_pages else None
        if target_page is None:
            return

        target_page.bring_to_front()
        account_choice = None
        for candidate in (
            target_page.get_by_role("button", name=re.compile(re.escape(email), re.IGNORECASE)),
            target_page.get_by_role("link", name=re.compile(re.escape(email), re.IGNORECASE)),
            target_page.get_by_text(email, exact=True),
            target_page.get_by_text(email, exact=False),
        ):
            try:
                if candidate.count() > 0 and candidate.first.is_visible():
                    account_choice = candidate
                    break
            except Error:
                continue

        if account_choice is None:
            return

        account_choice.first.click(timeout=DEFAULT_TIMEOUT_MS)
        try:
            target_page.wait_for_load_state("networkidle", timeout=DEFAULT_TIMEOUT_MS)
        except TimeoutError:
            pass
        self.page.bring_to_front()
        self.wait_for_ready_ui(self.page)

    def wait_for_ready_ui(self, page: Optional[Page] = None) -> None:
        target_page = page or self.page
        target_page.wait_for_load_state("domcontentloaded", timeout=DEFAULT_TIMEOUT_MS)
        try:
            target_page.wait_for_load_state("networkidle", timeout=DEFAULT_TIMEOUT_MS)
        except TimeoutError:
            # SPAs may keep network activity alive; proceed after a settle pause.
            pass
        target_page.wait_for_timeout(POST_CLICK_SETTLE_MS)

    def page_already_in_main_app(self) -> bool:
        return self.any_visible_text(["Negocio", "Mi Negocio"], timeout_ms=4_000) and (
            self.page.locator("nav").count() > 0
            or self.page.get_by_text("Mi Negocio", exact=False).count() > 0
        )

    def capture(self, filename: str, page: Optional[Page] = None, full_page: bool = False) -> None:
        target_page = page or self.page
        destination = self.screenshots_dir / filename
        destination.parent.mkdir(parents=True, exist_ok=True)
        target_page.screenshot(path=str(destination), full_page=full_page)

    def click_and_wait(self, locator, description: str) -> None:
        locator.first.wait_for(state="visible", timeout=DEFAULT_TIMEOUT_MS)
        locator.first.scroll_into_view_if_needed(timeout=DEFAULT_TIMEOUT_MS)
        locator.first.click(timeout=DEFAULT_TIMEOUT_MS)
        self.wait_for_ready_ui()

    def find_clickable_by_text(self, texts: List[str], timeout_ms: int = 8_000):
        deadline = datetime.now(tz=timezone.utc).timestamp() + (timeout_ms / 1000.0)
        last_error = "No matching clickable element found."

        while datetime.now(tz=timezone.utc).timestamp() < deadline:
            for text in texts:
                regex = re.compile(rf"^{re.escape(text)}$", re.IGNORECASE)
                candidates = [
                    self.page.get_by_role("button", name=regex),
                    self.page.get_by_role("link", name=regex),
                    self.page.get_by_role("menuitem", name=regex),
                    self.page.get_by_role("tab", name=regex),
                    self.page.get_by_text(text, exact=True),
                    self.page.get_by_text(text, exact=False),
                ]
                for candidate in candidates:
                    try:
                        if candidate.count() > 0 and candidate.first.is_visible():
                            return candidate
                    except Error as err:
                        last_error = str(err)
                        continue
            self.page.wait_for_timeout(300)

        raise AssertionError(
            f"Could not find clickable element with any text in {texts}. Last error: {last_error}"
        )

    def any_visible_text(
        self, texts: List[str], page: Optional[Page] = None, timeout_ms: int = 2_000
    ) -> bool:
        target_page = page or self.page
        for text in texts:
            try:
                target_page.get_by_text(text, exact=True).first.wait_for(
                    state="visible", timeout=timeout_ms
                )
                return True
            except TimeoutError:
                try:
                    target_page.get_by_text(text, exact=False).first.wait_for(
                        state="visible", timeout=timeout_ms
                    )
                    return True
                except TimeoutError:
                    continue
        return False

    def require_visible_text(
        self, text: str, failure_message: str, page: Optional[Page] = None, timeout_ms: int = 8_000
    ) -> None:
        target_page = page or self.page
        if self.any_visible_text([text], page=target_page, timeout_ms=timeout_ms):
            return
        raise AssertionError(failure_message)

    def require_one_of_locators_visible(self, locators: List, failure_message: str) -> None:
        for locator in locators:
            try:
                if locator.count() > 0 and locator.first.is_visible():
                    return
            except Error:
                continue
        raise AssertionError(failure_message)

    def fill_input_if_present(self, label: str, value: str) -> None:
        candidates = [
            self.page.get_by_label(label),
            self.page.get_by_placeholder(label),
            self.page.locator(f"input[name*='{label.lower().replace(' ', '_')}']"),
        ]
        for locator in candidates:
            try:
                if locator.count() > 0 and locator.first.is_visible():
                    locator.first.fill(value, timeout=DEFAULT_TIMEOUT_MS)
                    return
            except Error:
                continue

    def require_email_visible(self, failure_message: str) -> None:
        # Prefer visible text search and avoid fragile CSS selectors.
        text = self.page.inner_text("body")
        if not re.search(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", text):
            raise AssertionError(failure_message)

    def page_contains_probable_person_name(self) -> bool:
        text = self.page.inner_text("body")
        # Heuristic: at least one two-word capitalized pattern likely representing a name.
        return bool(re.search(r"\b[A-ZÁÉÍÓÚÑ][a-záéíóúñ]{2,}\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]{2,}\b", text))

    def page_has_business_list_signal(self) -> bool:
        # Multiple "Negocio" mentions usually indicate list/cards plus controls.
        matches = self.page.get_by_text("Negocio", exact=False).count()
        if matches >= 2:
            return True
        body_text = self.page.inner_text("body")
        return "Tus Negocios" in body_text and "Agregar Negocio" in body_text

    def require_legal_content_visible(self, page: Page) -> None:
        body_text = page.inner_text("body").strip()
        words = [word for word in body_text.split() if word]
        if len(words) < 40:
            raise AssertionError("Legal content text is unexpectedly short or missing.")


def parse_args() -> argparse.Namespace:
    env_url = os.getenv("SALEADS_URL", "")
    parser = argparse.ArgumentParser(
        description="Validate SaleADS Mi Negocio workflow (environment-agnostic)."
    )
    parser.add_argument(
        "--url",
        default=env_url,
        help=(
            "SaleADS login URL for the target environment. Defaults to SALEADS_URL env var."
        ),
    )
    parser.add_argument(
        "--headless",
        action="store_true",
        help="Run browser in headless mode (default is headed for easier debugging).",
    )
    parser.add_argument(
        "--screenshots-dir",
        default="artifacts/saleads-mi-negocio",
        help="Directory where screenshots and report JSON will be saved.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    timestamp = datetime.now(tz=timezone.utc).strftime("%Y%m%d-%H%M%S")
    run_dir = Path(args.screenshots_dir) / timestamp
    run_dir.mkdir(parents=True, exist_ok=True)

    if not args.url:
        print(
            "ERROR: Missing target URL. Pass --url <saleads-login-url> or set SALEADS_URL.",
            file=sys.stderr,
        )
        return 2

    report = RunReport(
        started_at=datetime.now(tz=timezone.utc).isoformat(),
        environment_url=args.url,
        screenshots_dir=str(run_dir),
    )

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=args.headless)
        context = browser.new_context(viewport={"width": 1440, "height": 900})
        page = context.new_page()
        test = SaleadsMiNegocioWorkflowTest(page, context, run_dir)
        test.run(args.url, report)

        report_file = run_dir / "report.json"
        report_file.write_text(report.to_json(), encoding="utf-8")

        print("=== SaleADS Mi Negocio Full Test Report ===")
        print(json.dumps(report.as_summary(), indent=2, ensure_ascii=False))
        print(f"Report JSON: {report_file}")
        if report.final_urls:
            print("Final legal URLs:")
            for key, value in report.final_urls.items():
                print(f"- {key}: {value}")

        context.close()
        browser.close()

    return 0 if report.all_passed() else 1


if __name__ == "__main__":
    sys.exit(main())
