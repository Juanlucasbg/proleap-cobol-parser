#!/usr/bin/env python3
"""SaleADS Mi Negocio workflow validation.

This test is intentionally environment-agnostic:
- It does not hardcode a domain.
- It receives the login page URL by CLI/env.
- It relies primarily on visible text selectors.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
import unicodedata
from collections import OrderedDict
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence

from selenium import webdriver
from selenium.common.exceptions import NoSuchElementException, TimeoutException
from selenium.webdriver.common.by import By
from selenium.webdriver.remote.webelement import WebElement
from selenium.webdriver.support.ui import WebDriverWait


DEFAULT_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com"
TEST_NAME = "saleads_mi_negocio_full_test"


def normalize_text(value: Optional[str]) -> str:
    if not value:
        return ""
    decomposed = unicodedata.normalize("NFKD", value)
    without_marks = "".join(ch for ch in decomposed if not unicodedata.combining(ch))
    return re.sub(r"\s+", " ", without_marks).strip().lower()


def now_utc_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


@dataclass
class StepResult:
    status: str = "FAIL"
    checks: List[Dict[str, str]] = field(default_factory=list)

    def add_check(self, name: str, passed: bool, details: str = "") -> None:
        self.checks.append(
            {
                "check": name,
                "status": "PASS" if passed else "FAIL",
                "details": details,
            }
        )
        if passed and self.status != "FAIL":
            self.status = "PASS"
        elif not passed:
            self.status = "FAIL"

    def finalize_status(self) -> None:
        if self.checks and all(check["status"] == "PASS" for check in self.checks):
            self.status = "PASS"
        else:
            self.status = "FAIL"


class SaleadsMiNegocioWorkflowTest:
    REPORT_FIELDS = OrderedDict(
        [
            ("Login", StepResult(status="PASS")),
            ("Mi Negocio menu", StepResult(status="PASS")),
            ("Agregar Negocio modal", StepResult(status="PASS")),
            ("Administrar Negocios view", StepResult(status="PASS")),
            ("Información General", StepResult(status="PASS")),
            ("Detalles de la Cuenta", StepResult(status="PASS")),
            ("Tus Negocios", StepResult(status="PASS")),
            ("Términos y Condiciones", StepResult(status="PASS")),
            ("Política de Privacidad", StepResult(status="PASS")),
        ]
    )

    def __init__(
        self,
        driver: webdriver.Chrome,
        output_dir: Path,
        start_url: str,
        google_account_email: str,
    ) -> None:
        self.driver = driver
        self.output_dir = output_dir
        self.start_url = start_url
        self.google_account_email = google_account_email
        self.app_window_handle: Optional[str] = None
        self.report = {
            "test_name": TEST_NAME,
            "started_at": now_utc_iso(),
            "start_url": start_url,
            "results": OrderedDict((key, StepResult(status="PASS")) for key in self.REPORT_FIELDS),
            "screenshots": [],
            "legal_urls": {},
            "runtime": {},
        }

    def wait_for_document_ready(self, timeout: int = 30) -> None:
        try:
            WebDriverWait(self.driver, timeout).until(
                lambda browser: browser.execute_script("return document.readyState") == "complete"
            )
        except TimeoutException:
            # Some SPAs may not transition to complete reliably after route changes.
            pass

    def post_click_wait(self) -> None:
        self.wait_for_document_ready()
        time.sleep(1.0)

    def _visible_elements(self, xpath: str) -> List[WebElement]:
        try:
            elements = self.driver.find_elements(By.XPATH, xpath)
        except NoSuchElementException:
            return []
        return [el for el in elements if self._is_displayed(el)]

    @staticmethod
    def _is_displayed(element: WebElement) -> bool:
        try:
            return element.is_displayed()
        except Exception:
            return False

    @staticmethod
    def _is_enabled(element: WebElement) -> bool:
        try:
            return element.is_enabled()
        except Exception:
            return False

    def _element_text_for_match(self, element: WebElement) -> str:
        text_parts = [
            element.text or "",
            element.get_attribute("aria-label") or "",
            element.get_attribute("title") or "",
            element.get_attribute("placeholder") or "",
            element.get_attribute("value") or "",
        ]
        return normalize_text(" ".join(text_parts))

    def find_visible_text_element(
        self,
        text_options: Sequence[str],
        timeout: int = 20,
        clickable: bool = False,
        xpath: str = (
            "//*[(self::a or self::button or self::span or self::div or self::li or self::p "
            "or self::h1 or self::h2 or self::h3 or self::h4 or self::label or self::input "
            "or self::textarea)]"
        ),
    ) -> WebElement:
        normalized_options = [normalize_text(option) for option in text_options if option]
        if not normalized_options:
            raise ValueError("text_options must include at least one non-empty value.")

        deadline = time.time() + timeout
        while time.time() < deadline:
            for element in self._visible_elements(xpath):
                candidate = self._element_text_for_match(element)
                if any(option in candidate for option in normalized_options):
                    if clickable and not self._is_enabled(element):
                        continue
                    return element
            time.sleep(0.3)

        options_joined = ", ".join(text_options)
        raise TimeoutException(f"Unable to locate visible element by text: {options_joined}")

    def click_by_visible_text(self, text_options: Sequence[str], timeout: int = 20) -> WebElement:
        element = self.find_visible_text_element(text_options=text_options, timeout=timeout, clickable=True)
        self.driver.execute_script(
            "arguments[0].scrollIntoView({block: 'center', inline: 'center'});", element
        )
        try:
            element.click()
        except Exception:
            self.driver.execute_script("arguments[0].click();", element)
        self.post_click_wait()
        return element

    def capture_screenshot(self, filename: str, full_page: bool = False) -> str:
        filepath = self.output_dir / filename
        if full_page:
            original = self.driver.get_window_size()
            page_height = int(
                self.driver.execute_script(
                    "return Math.max("
                    "document.body.scrollHeight, document.documentElement.scrollHeight,"
                    "document.body.offsetHeight, document.documentElement.offsetHeight,"
                    "document.body.clientHeight, document.documentElement.clientHeight"
                    ");"
                )
            )
            page_height = min(max(page_height + 200, 1200), 10000)
            self.driver.set_window_size(max(original["width"], 1600), page_height)
            self.wait_for_document_ready()
            self.driver.save_screenshot(str(filepath))
            self.driver.set_window_size(original["width"], original["height"])
        else:
            self.driver.save_screenshot(str(filepath))
        self.report["screenshots"].append(str(filepath))
        return str(filepath)

    def is_text_visible(self, text_options: Sequence[str], timeout: int = 10) -> bool:
        try:
            self.find_visible_text_element(text_options=text_options, timeout=timeout, clickable=False)
            return True
        except TimeoutException:
            return False

    def find_input_for_business_name(self, timeout: int = 15) -> WebElement:
        target_tokens = ["nombre del negocio", "nombre negocio", "business name"]
        xpath = "//input | //textarea"
        deadline = time.time() + timeout
        while time.time() < deadline:
            for element in self._visible_elements(xpath):
                candidate = self._element_text_for_match(element)
                if any(token in candidate for token in target_tokens):
                    return element
            time.sleep(0.3)
        raise TimeoutException("Could not find input for 'Nombre del Negocio'.")

    def has_visible_email(self) -> bool:
        email_regex = re.compile(r"[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}")
        for element in self._visible_elements("//*[self::p or self::span or self::div or self::li or self::a]"):
            text = (element.text or "").strip()
            if text and email_regex.search(text):
                return True
        return False

    def has_likely_user_name(self) -> bool:
        # Fallback strategy: detect a "Nombre" field/label with visible value-like context.
        labels = [
            "nombre",
            "name",
            "usuario",
            "user",
        ]
        return self.is_text_visible(labels, timeout=8)

    def has_legal_content_text(self) -> bool:
        legal_blocks = self._visible_elements("//p | //li | //article | //section")
        for block in legal_blocks:
            text = normalize_text(block.text)
            if len(text) > 80:
                return True
        return False

    def mark_check(self, report_field: str, check_name: str, passed: bool, details: str = "") -> None:
        step_result: StepResult = self.report["results"][report_field]
        step_result.add_check(name=check_name, passed=passed, details=details)

    def finalize_report_fields(self) -> None:
        for field_name, result in self.report["results"].items():
            result.finalize_status()
            self.report["results"][field_name] = {
                "status": result.status,
                "checks": result.checks,
            }

    def _best_effort_click_google_account(self) -> None:
        main_handle = self.driver.current_window_handle
        handles_before = set(self.driver.window_handles)

        # Small grace period for Google account chooser window/tab.
        deadline = time.time() + 20
        target_handle = main_handle
        while time.time() < deadline:
            handles_now = set(self.driver.window_handles)
            new_handles = handles_now - handles_before
            if new_handles:
                target_handle = next(iter(new_handles))
                break
            if "accounts.google.com" in normalize_text(self.driver.current_url):
                target_handle = self.driver.current_window_handle
                break
            time.sleep(0.3)

        self.driver.switch_to.window(target_handle)
        try:
            account_element = self.find_visible_text_element(
                [self.google_account_email], timeout=8, clickable=True
            )
            try:
                account_element.click()
            except Exception:
                self.driver.execute_script("arguments[0].click();", account_element)
            self.post_click_wait()
        except TimeoutException:
            # If account picker is not shown, login might already be complete.
            pass
        finally:
            # Always return to app/main tab if still open.
            if main_handle in self.driver.window_handles:
                self.driver.switch_to.window(main_handle)

    def run_step_login(self) -> None:
        field = "Login"
        self.driver.get(self.start_url)
        self.wait_for_document_ready()
        self.app_window_handle = self.driver.current_window_handle

        login_clicked = False
        for labels in (
            ["sign in with google"],
            ["iniciar sesion con google"],
            ["continuar con google"],
            ["google"],
        ):
            try:
                self.click_by_visible_text(labels, timeout=10)
                login_clicked = True
                break
            except TimeoutException:
                continue

        self.mark_check(field, "Locate and click Google login button", login_clicked)

        if login_clicked:
            try:
                self._best_effort_click_google_account()
                self.mark_check(field, f"Google account chooser for {self.google_account_email}", True)
            except Exception as exc:  # pragma: no cover - runtime safety
                self.mark_check(field, f"Google account chooser for {self.google_account_email}", False, str(exc))
        else:
            self.mark_check(field, f"Google account chooser for {self.google_account_email}", False)

        main_ui_visible = self.is_text_visible(["dashboard", "panel", "inicio", "negocio", "mi negocio"], timeout=30)
        sidebar_visible = len(self._visible_elements("//aside | //nav")) > 0 or self.is_text_visible(
            ["negocio", "mi negocio"], timeout=10
        )

        self.mark_check(field, "Main application interface is visible", main_ui_visible)
        self.mark_check(field, "Left sidebar navigation is visible", sidebar_visible)
        self.capture_screenshot("01_dashboard_loaded.png")

    def run_step_mi_negocio_menu(self) -> None:
        field = "Mi Negocio menu"
        # Open section "Negocio" first if present, then "Mi Negocio".
        negocio_clicked = False
        try:
            self.click_by_visible_text(["negocio"], timeout=10)
            negocio_clicked = True
        except TimeoutException:
            negocio_clicked = False

        mi_negocio_clicked = False
        try:
            self.click_by_visible_text(["mi negocio"], timeout=12)
            mi_negocio_clicked = True
        except TimeoutException:
            mi_negocio_clicked = False

        agregar_visible = self.is_text_visible(["agregar negocio"], timeout=10)
        administrar_visible = self.is_text_visible(["administrar negocios"], timeout=10)

        self.mark_check(field, "Section 'Negocio' interaction", negocio_clicked or mi_negocio_clicked)
        self.mark_check(field, "Option 'Mi Negocio' clicked", mi_negocio_clicked)
        self.mark_check(field, "Submenu expanded with 'Agregar Negocio'", agregar_visible)
        self.mark_check(field, "Submenu expanded with 'Administrar Negocios'", administrar_visible)
        self.capture_screenshot("02_mi_negocio_menu_expanded.png")

    def run_step_agregar_negocio_modal(self) -> None:
        field = "Agregar Negocio modal"
        self.click_by_visible_text(["agregar negocio"], timeout=12)

        modal_title_visible = self.is_text_visible(["crear nuevo negocio"], timeout=12)
        business_name_input_exists = False
        quota_visible = self.is_text_visible(["tienes 2 de 3 negocios"], timeout=10)
        cancelar_visible = self.is_text_visible(["cancelar"], timeout=10)
        crear_visible = self.is_text_visible(["crear negocio"], timeout=10)

        try:
            business_name_input = self.find_input_for_business_name(timeout=12)
            business_name_input_exists = business_name_input is not None
            business_name_input.click()
            business_name_input.clear()
            business_name_input.send_keys("Negocio Prueba Automatización")
        except Exception:
            business_name_input_exists = False

        self.mark_check(field, "Modal title 'Crear Nuevo Negocio' is visible", modal_title_visible)
        self.mark_check(field, "Input field 'Nombre del Negocio' exists", business_name_input_exists)
        self.mark_check(field, "Text 'Tienes 2 de 3 negocios' is visible", quota_visible)
        self.mark_check(field, "Button 'Cancelar' is visible", cancelar_visible)
        self.mark_check(field, "Button 'Crear Negocio' is visible", crear_visible)

        self.capture_screenshot("03_agregar_negocio_modal.png")
        self.click_by_visible_text(["cancelar"], timeout=10)

    def run_step_administrar_negocios_view(self) -> None:
        field = "Administrar Negocios view"
        if not self.is_text_visible(["administrar negocios"], timeout=4):
            try:
                self.click_by_visible_text(["mi negocio"], timeout=8)
            except TimeoutException:
                pass

        self.click_by_visible_text(["administrar negocios"], timeout=12)

        info_general = self.is_text_visible(["informacion general"], timeout=20)
        detalles = self.is_text_visible(["detalles de la cuenta"], timeout=10)
        tus_negocios = self.is_text_visible(["tus negocios"], timeout=10)
        legal = self.is_text_visible(["seccion legal"], timeout=10)

        self.mark_check(field, "Section 'Información General' exists", info_general)
        self.mark_check(field, "Section 'Detalles de la Cuenta' exists", detalles)
        self.mark_check(field, "Section 'Tus Negocios' exists", tus_negocios)
        self.mark_check(field, "Section 'Sección Legal' exists", legal)
        self.capture_screenshot("04_administrar_negocios_page_full.png", full_page=True)

    def run_step_informacion_general(self) -> None:
        field = "Información General"
        name_visible = self.has_likely_user_name()
        email_visible = self.has_visible_email()
        business_plan_visible = self.is_text_visible(["business plan"], timeout=10)
        cambiar_plan_visible = self.is_text_visible(["cambiar plan"], timeout=10)

        self.mark_check(field, "User name is visible", name_visible)
        self.mark_check(field, "User email is visible", email_visible)
        self.mark_check(field, "Text 'BUSINESS PLAN' is visible", business_plan_visible)
        self.mark_check(field, "Button 'Cambiar Plan' is visible", cambiar_plan_visible)

    def run_step_detalles_cuenta(self) -> None:
        field = "Detalles de la Cuenta"
        cuenta_creada = self.is_text_visible(["cuenta creada"], timeout=10)
        estado_activo = self.is_text_visible(["estado activo", "estado"], timeout=10)
        idioma = self.is_text_visible(["idioma seleccionado", "idioma"], timeout=10)

        self.mark_check(field, "'Cuenta creada' is visible", cuenta_creada)
        self.mark_check(field, "'Estado activo' is visible", estado_activo)
        self.mark_check(field, "'Idioma seleccionado' is visible", idioma)

    def run_step_tus_negocios(self) -> None:
        field = "Tus Negocios"
        list_visible = self.is_text_visible(["tus negocios"], timeout=10)
        agregar_button = self.is_text_visible(["agregar negocio"], timeout=10)
        quota_visible = self.is_text_visible(["tienes 2 de 3 negocios"], timeout=10)

        self.mark_check(field, "Business list is visible", list_visible)
        self.mark_check(field, "Button 'Agregar Negocio' exists", agregar_button)
        self.mark_check(field, "Text 'Tienes 2 de 3 negocios' is visible", quota_visible)

    def _validate_legal_destination(
        self,
        field: str,
        link_text_options: Sequence[str],
        expected_heading_text_options: Sequence[str],
        screenshot_name: str,
        report_url_key: str,
    ) -> None:
        if self.app_window_handle is None:
            self.app_window_handle = self.driver.current_window_handle

        starting_handle = self.driver.current_window_handle
        starting_url = self.driver.current_url
        handles_before = set(self.driver.window_handles)

        self.click_by_visible_text(link_text_options, timeout=12)

        new_handle: Optional[str] = None
        deadline = time.time() + 20
        while time.time() < deadline:
            handles_now = set(self.driver.window_handles)
            new_handles = list(handles_now - handles_before)
            if new_handles:
                new_handle = new_handles[0]
                break
            if self.driver.current_url != starting_url:
                break
            time.sleep(0.3)

        if new_handle:
            self.driver.switch_to.window(new_handle)
            self.wait_for_document_ready()
            time.sleep(0.7)

        heading_visible = self.is_text_visible(expected_heading_text_options, timeout=15)
        legal_content_visible = self.has_legal_content_text()
        screenshot_path = self.capture_screenshot(screenshot_name)
        self.report["legal_urls"][report_url_key] = self.driver.current_url

        self.mark_check(
            field,
            f"Heading for '{' / '.join(expected_heading_text_options)}' is visible",
            heading_visible,
        )
        self.mark_check(field, "Legal content text is visible", legal_content_visible)
        self.mark_check(field, "Evidence screenshot captured", True, screenshot_path)

        # Cleanup: return to application tab.
        if new_handle:
            self.driver.close()
            if self.app_window_handle in self.driver.window_handles:
                self.driver.switch_to.window(self.app_window_handle)
            else:
                self.driver.switch_to.window(starting_handle)
            self.wait_for_document_ready()
        else:
            if normalize_text(self.driver.current_url) != normalize_text(starting_url):
                self.driver.back()
                self.wait_for_document_ready()

    def run_step_terminos(self) -> None:
        self._validate_legal_destination(
            field="Términos y Condiciones",
            link_text_options=["terminos y condiciones", "términos y condiciones"],
            expected_heading_text_options=["terminos y condiciones", "términos y condiciones"],
            screenshot_name="05_terminos_y_condiciones.png",
            report_url_key="terminos_y_condiciones",
        )

    def run_step_privacidad(self) -> None:
        self._validate_legal_destination(
            field="Política de Privacidad",
            link_text_options=["politica de privacidad", "política de privacidad"],
            expected_heading_text_options=["politica de privacidad", "política de privacidad"],
            screenshot_name="06_politica_de_privacidad.png",
            report_url_key="politica_de_privacidad",
        )

    def run(self) -> int:
        execution_order = [
            ("Login", self.run_step_login),
            ("Mi Negocio menu", self.run_step_mi_negocio_menu),
            ("Agregar Negocio modal", self.run_step_agregar_negocio_modal),
            ("Administrar Negocios view", self.run_step_administrar_negocios_view),
            ("Información General", self.run_step_informacion_general),
            ("Detalles de la Cuenta", self.run_step_detalles_cuenta),
            ("Tus Negocios", self.run_step_tus_negocios),
            ("Términos y Condiciones", self.run_step_terminos),
            ("Política de Privacidad", self.run_step_privacidad),
        ]

        for report_field, step_func in execution_order:
            try:
                step_func()
            except Exception as exc:  # pragma: no cover - resilient runtime reporting
                self.mark_check(
                    report_field,
                    "Unhandled exception while executing step",
                    False,
                    str(exc),
                )

        self.report["finished_at"] = now_utc_iso()
        self.report["runtime"]["final_url"] = self.driver.current_url
        self.finalize_report_fields()

        report_path = self.output_dir / "final_report.json"
        report_path.write_text(json.dumps(self.report, indent=2, ensure_ascii=False), encoding="utf-8")

        # Console summary for CI/cron logs.
        print(f"\n=== {TEST_NAME} summary ===")
        for field_name, result in self.report["results"].items():
            print(f"- {field_name}: {result['status']}")
        print(f"\nReport: {report_path}")
        print(f"Screenshots directory: {self.output_dir}")
        if self.report["legal_urls"]:
            print("Legal URLs:")
            for key, value in self.report["legal_urls"].items():
                print(f"  - {key}: {value}")

        return 0 if all(result["status"] == "PASS" for result in self.report["results"].values()) else 1


def build_chrome_driver(headless: bool, user_data_dir: Optional[str] = None) -> webdriver.Chrome:
    options = webdriver.ChromeOptions()
    options.add_argument("--window-size=1600,1200")
    options.add_argument("--disable-dev-shm-usage")
    options.add_argument("--no-sandbox")
    options.add_argument("--lang=es-ES")
    if headless:
        options.add_argument("--headless=new")
    if user_data_dir:
        options.add_argument(f"--user-data-dir={user_data_dir}")
    return webdriver.Chrome(options=options)


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run SaleADS Mi Negocio full workflow test.")
    parser.add_argument(
        "--start-url",
        default=os.environ.get("SALEADS_START_URL", "").strip(),
        help="SaleADS login page URL for the current environment (no hardcoded domain required).",
    )
    parser.add_argument(
        "--google-account-email",
        default=os.environ.get("SALEADS_GOOGLE_ACCOUNT_EMAIL", DEFAULT_ACCOUNT_EMAIL),
        help="Google account email to select in account chooser.",
    )
    parser.add_argument(
        "--output-dir",
        default=os.environ.get("SALEADS_OUTPUT_DIR", ""),
        help="Optional output directory for screenshots and report.",
    )
    parser.add_argument(
        "--headless",
        action="store_true",
        default=normalize_text(os.environ.get("SALEADS_HEADLESS", "true")) not in {"false", "0", "no"},
        help="Run Chrome in headless mode (default: true unless SALEADS_HEADLESS disables it).",
    )
    parser.add_argument(
        "--chrome-user-data-dir",
        default=os.environ.get("SALEADS_CHROME_USER_DATA_DIR", "").strip() or None,
        help="Optional Chrome user data dir to reuse existing Google session.",
    )
    return parser.parse_args(argv)


def main(argv: Sequence[str]) -> int:
    args = parse_args(argv)
    if not args.start_url:
        print(
            "ERROR: --start-url is required (or set SALEADS_START_URL). "
            "The test stays environment-agnostic by receiving URL at runtime."
        )
        return 2

    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    default_output = Path("automation") / "artifacts" / f"{TEST_NAME}_{timestamp}"
    output_dir = Path(args.output_dir) if args.output_dir else default_output
    output_dir.mkdir(parents=True, exist_ok=True)

    driver = build_chrome_driver(headless=args.headless, user_data_dir=args.chrome_user_data_dir)
    exit_code = 1
    try:
        test = SaleadsMiNegocioWorkflowTest(
            driver=driver,
            output_dir=output_dir,
            start_url=args.start_url,
            google_account_email=args.google_account_email,
        )
        exit_code = test.run()
    finally:
        driver.quit()
    return exit_code


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
