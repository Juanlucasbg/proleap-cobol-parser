#!/usr/bin/env python3
"""SaleADS Mi Negocio end-to-end workflow automation.

This script validates the full Mi Negocio workflow:
1. Login with Google
2. Open Mi Negocio menu
3. Validate Agregar Negocio modal
4. Open Administrar Negocios view
5. Validate Informacion General
6. Validate Detalles de la Cuenta
7. Validate Tus Negocios
8. Validate Terminos y Condiciones
9. Validate Politica de Privacidad

The login URL is never hardcoded; pass it with --login-url or SALEADS_LOGIN_URL.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable, Dict, Iterable, List, Optional, Tuple

from selenium import webdriver
from selenium.common.exceptions import TimeoutException, WebDriverException
from selenium.webdriver import Chrome
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
from selenium.webdriver.remote.webelement import WebElement
from selenium.webdriver.support import expected_conditions as ec
from selenium.webdriver.support.ui import WebDriverWait


REPORT_FIELDS: List[str] = [
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
    status: str
    details: str
    screenshot: Optional[str]
    url: Optional[str]


class WorkflowError(Exception):
    """Raised when one workflow step cannot be validated."""


class SaleadsWorkflowRunner:
    def __init__(self, login_url: str, artifacts_root: Path, headless: bool, timeout_seconds: int):
        self.login_url = login_url
        self.artifacts_root = artifacts_root
        self.headless = headless
        self.timeout_seconds = timeout_seconds
        self.run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        self.run_dir = artifacts_root / self.run_id
        self.screenshots_dir = self.run_dir / "screenshots"
        self.results: Dict[str, StepResult] = {}
        self.driver: Optional[Chrome] = None
        self.wait: Optional[WebDriverWait] = None

    def setup(self) -> None:
        self.screenshots_dir.mkdir(parents=True, exist_ok=True)
        chrome_options = Options()
        if self.headless:
            chrome_options.add_argument("--headless=new")
        chrome_options.add_argument("--window-size=1600,1800")
        chrome_options.add_argument("--no-sandbox")
        chrome_options.add_argument("--disable-dev-shm-usage")
        chrome_options.add_argument("--disable-gpu")
        chrome_options.add_argument("--lang=es-ES")
        self.driver = webdriver.Chrome(options=chrome_options)
        self.wait = WebDriverWait(self.driver, self.timeout_seconds)

    def teardown(self) -> None:
        if self.driver is not None:
            self.driver.quit()

    @property
    def active_driver(self) -> Chrome:
        if self.driver is None:
            raise RuntimeError("WebDriver not initialized.")
        return self.driver

    @property
    def active_wait(self) -> WebDriverWait:
        if self.wait is None:
            raise RuntimeError("WebDriverWait not initialized.")
        return self.wait

    def _xpath_literal(self, value: str) -> str:
        if "'" not in value:
            return f"'{value}'"
        if '"' not in value:
            return f'"{value}"'
        parts = value.split("'")
        return "concat(" + ", \"'\", ".join(f"'{part}'" for part in parts) + ")"

    def _contains_ci_xpath(self, needle: str) -> str:
        lower = needle.lower()
        literal = self._xpath_literal(lower)
        return (
            "contains("
            "translate(normalize-space(.),"
            "'ABCDEFGHIJKLMNOPQRSTUVWXYZ',"
            "'abcdefghijklmnopqrstuvwxyz'),"
            f"{literal})"
        )

    def _wait_for_document_ready(self) -> None:
        self.active_wait.until(lambda d: d.execute_script("return document.readyState") == "complete")

    def _wait_after_click(self) -> None:
        self._wait_for_document_ready()
        time.sleep(0.8)

    def _click_and_wait(self, element: WebElement) -> None:
        self.active_driver.execute_script("arguments[0].scrollIntoView({block: 'center'});", element)
        try:
            element.click()
        except WebDriverException:
            self.active_driver.execute_script("arguments[0].click();", element)
        self._wait_after_click()

    def _first_visible_element(self, xpaths: Iterable[str], timeout: Optional[int] = None) -> WebElement:
        wait = WebDriverWait(self.active_driver, timeout or self.timeout_seconds)
        last_error: Optional[Exception] = None
        for xpath in xpaths:
            try:
                return wait.until(ec.visibility_of_element_located((By.XPATH, xpath)))
            except Exception as err:  # pylint: disable=broad-except
                last_error = err
        raise WorkflowError(f"Could not locate visible element for any XPath. Last error: {last_error}")

    def _first_clickable_element(self, xpaths: Iterable[str], timeout: Optional[int] = None) -> WebElement:
        wait = WebDriverWait(self.active_driver, timeout or self.timeout_seconds)
        last_error: Optional[Exception] = None
        for xpath in xpaths:
            try:
                return wait.until(ec.element_to_be_clickable((By.XPATH, xpath)))
            except Exception as err:  # pylint: disable=broad-except
                last_error = err
        raise WorkflowError(f"Could not locate clickable element for any XPath. Last error: {last_error}")

    def _click_by_visible_text(self, labels: Iterable[str], scope_xpath: Optional[str] = None) -> None:
        xpaths: List[str] = []
        for label in labels:
            literal = self._xpath_literal(label)
            label_ci = self._contains_ci_xpath(label)
            prefix = f"{scope_xpath}//" if scope_xpath else "//"
            xpaths.extend(
                [
                    f"{prefix}button[normalize-space()={literal}]",
                    f"{prefix}a[normalize-space()={literal}]",
                    f"{prefix}*[self::button or self::a or @role='button'][{label_ci}]",
                    f"{prefix}*[{label_ci}][self::span or self::div]/ancestor::*[self::button or self::a][1]",
                ]
            )
        element = self._first_clickable_element(xpaths)
        self._click_and_wait(element)

    def _text_visible(self, labels: Iterable[str], timeout: Optional[int] = None) -> bool:
        for label in labels:
            literal = self._xpath_literal(label)
            label_ci = self._contains_ci_xpath(label)
            xpaths = [
                f"//*[normalize-space()={literal}]",
                f"//*[{label_ci}]",
            ]
            try:
                self._first_visible_element(xpaths, timeout=timeout)
                return True
            except WorkflowError:
                continue
        return False

    def _assert_text_visible(self, labels: Iterable[str], description: str) -> None:
        if not self._text_visible(labels):
            raise WorkflowError(f"Expected text not visible: {description}")

    def _capture_screenshot(self, checkpoint_name: str) -> str:
        screenshot_path = self.screenshots_dir / f"{checkpoint_name}.png"
        self.active_driver.save_screenshot(str(screenshot_path))
        return str(screenshot_path)

    def _record_result(
        self,
        field: str,
        status: str,
        details: str,
        screenshot: Optional[str] = None,
        url: Optional[str] = None,
    ) -> None:
        self.results[field] = StepResult(
            name=field,
            status=status,
            details=details,
            screenshot=screenshot,
            url=url or self.active_driver.current_url,
        )

    def _record_pass(self, field: str, details: str, screenshot: Optional[str] = None, url: Optional[str] = None) -> None:
        self._record_result(field, "PASS", details, screenshot, url)

    def _record_fail(self, field: str, details: str, screenshot: Optional[str] = None, url: Optional[str] = None) -> None:
        self._record_result(field, "FAIL", details, screenshot, url)

    def _cloudflare_blocked(self) -> bool:
        page_text = self.active_driver.page_source.lower()
        indicators = [
            "sorry, you have been blocked",
            "attention required",
            "cloudflare",
            "ray id",
        ]
        return any(indicator in page_text for indicator in indicators)

    def _skip_remaining(self, reason: str) -> None:
        for field in REPORT_FIELDS:
            if field not in self.results:
                self._record_fail(field, f"Skipped because prerequisite failed: {reason}")

    def _run_step(
        self,
        field: str,
        handler: Callable[[], Tuple[str, Optional[str], Optional[str]]],
        dependency: Optional[str] = None,
    ) -> bool:
        if dependency and self.results.get(dependency, StepResult(dependency, "FAIL", "", None, None)).status != "PASS":
            self._record_fail(field, f"Skipped because dependency '{dependency}' did not pass.")
            return False
        try:
            details, screenshot, url = handler()
            self._record_pass(field, details, screenshot=screenshot, url=url)
            return True
        except Exception as err:  # pylint: disable=broad-except
            fail_shot = self._capture_screenshot(f"{self._slug(field)}-failure")
            self._record_fail(field, str(err), screenshot=fail_shot)
            return False

    def _slug(self, value: str) -> str:
        return re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-")

    def run(self) -> int:
        self.setup()
        try:
            self.active_driver.get(self.login_url)
            self._wait_for_document_ready()

            if self._cloudflare_blocked():
                blocked_shot = self._capture_screenshot("01-login-blocked-cloudflare")
                self._record_fail(
                    "Login",
                    "Access blocked before login interaction (Cloudflare protection page detected).",
                    screenshot=blocked_shot,
                )
                self._skip_remaining("Login blocked by Cloudflare.")
                return self._finalize()

            login_ok = self._run_step("Login", self._step_login_google)
            menu_ok = self._run_step("Mi Negocio menu", self._step_open_mi_negocio_menu, dependency="Login")
            self._run_step("Agregar Negocio modal", self._step_validate_agregar_negocio_modal, dependency="Mi Negocio menu")
            admin_ok = self._run_step("Administrar Negocios view", self._step_open_administrar_negocios, dependency="Mi Negocio menu")

            self._run_step("Información General", self._step_validate_informacion_general, dependency="Administrar Negocios view")
            self._run_step("Detalles de la Cuenta", self._step_validate_detalles_cuenta, dependency="Administrar Negocios view")
            self._run_step("Tus Negocios", self._step_validate_tus_negocios, dependency="Administrar Negocios view")

            self._run_step("Términos y Condiciones", self._step_validate_terminos, dependency="Administrar Negocios view")
            self._run_step("Política de Privacidad", self._step_validate_politica, dependency="Administrar Negocios view")

            if not login_ok:
                self._skip_remaining("Login step failed.")
            elif not menu_ok:
                self._skip_remaining("Mi Negocio menu step failed.")
            elif not admin_ok:
                self._skip_remaining("Administrar Negocios view step failed.")

            return self._finalize()
        finally:
            self.teardown()

    def _step_login_google(self) -> Tuple[str, Optional[str], Optional[str]]:
        if self._text_visible(["Negocio"], timeout=5):
            if not self._text_visible(["Mi Negocio"], timeout=5):
                self._click_by_visible_text(["Negocio"])
            screenshot = self._capture_screenshot("01-dashboard-loaded")
            self._assert_text_visible(["Mi Negocio", "Negocio"], "left sidebar navigation")
            return (
                "Main application interface and sidebar are visible (existing authenticated session).",
                screenshot,
                self.active_driver.current_url,
            )

        current_handles = set(self.active_driver.window_handles)
        self._click_by_visible_text(
            ["Sign in with Google", "Iniciar sesion con Google", "Iniciar sesión con Google", "Continuar con Google"]
        )

        new_handle = self._wait_for_new_window(current_handles, timeout=15)
        original_handle = self.active_driver.current_window_handle
        if new_handle:
            self.active_driver.switch_to.window(new_handle)
            self._wait_for_document_ready()
            self._select_google_account_if_present()
            self._wait_for_document_ready()
            self.active_driver.switch_to.window(original_handle)
        else:
            self._select_google_account_if_present()

        self._wait_for_document_ready()
        self._assert_text_visible(["Negocio"], "main application loaded")
        self._assert_text_visible(["Mi Negocio", "Negocio"], "left sidebar visible")
        screenshot = self._capture_screenshot("01-dashboard-loaded")
        return ("Login completed and sidebar validated.", screenshot, self.active_driver.current_url)

    def _wait_for_new_window(self, previous_handles: set[str], timeout: int) -> Optional[str]:
        wait = WebDriverWait(self.active_driver, timeout)
        try:
            wait.until(lambda d: len(set(d.window_handles) - previous_handles) > 0)
        except TimeoutException:
            return None
        current = set(self.active_driver.window_handles)
        new_handles = list(current - previous_handles)
        if not new_handles:
            return None
        return new_handles[0]

    def _select_google_account_if_present(self) -> None:
        account_xpath = (
            "//*[contains(translate(normalize-space(.),"
            "'ABCDEFGHIJKLMNOPQRSTUVWXYZ',"
            "'abcdefghijklmnopqrstuvwxyz'),"
            "'juanlucasbarbiergarzon@gmail.com')]"
        )
        try:
            account = WebDriverWait(self.active_driver, 8).until(ec.element_to_be_clickable((By.XPATH, account_xpath)))
            self._click_and_wait(account)
        except TimeoutException:
            # Account picker may not appear if session is already authenticated.
            return

    def _step_open_mi_negocio_menu(self) -> Tuple[str, Optional[str], Optional[str]]:
        self._assert_text_visible(["Negocio"], "sidebar section Negocio")
        self._click_by_visible_text(["Negocio"])
        self._click_by_visible_text(["Mi Negocio"])
        self._assert_text_visible(["Agregar Negocio"], "submenu item Agregar Negocio")
        self._assert_text_visible(["Administrar Negocios"], "submenu item Administrar Negocios")
        screenshot = self._capture_screenshot("02-mi-negocio-menu-expanded")
        return ("Mi Negocio submenu expanded with required options.", screenshot, self.active_driver.current_url)

    def _step_validate_agregar_negocio_modal(self) -> Tuple[str, Optional[str], Optional[str]]:
        self._click_by_visible_text(["Agregar Negocio"])
        self._assert_text_visible(["Crear Nuevo Negocio"], "modal title Crear Nuevo Negocio")

        nombre_input = self._first_visible_element(
            [
                "//label[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1]",
                "//input[contains(@placeholder, 'Nombre del Negocio')]",
                "//input[contains(@aria-label, 'Nombre del Negocio')]",
            ]
        )
        self._assert_text_visible(["Tienes 2 de 3 negocios"], "quota text")
        self._assert_text_visible(["Cancelar"], "Cancelar button")
        self._assert_text_visible(["Crear Negocio"], "Crear Negocio button")
        screenshot = self._capture_screenshot("03-agregar-negocio-modal")

        # Optional action required by prompt.
        nombre_input.click()
        nombre_input.clear()
        nombre_input.send_keys("Negocio Prueba Automatizacion")
        self._click_by_visible_text(["Cancelar"])

        return ("Agregar Negocio modal validated and dismissed via Cancelar.", screenshot, self.active_driver.current_url)

    def _step_open_administrar_negocios(self) -> Tuple[str, Optional[str], Optional[str]]:
        if not self._text_visible(["Administrar Negocios"], timeout=4):
            self._click_by_visible_text(["Negocio"])
            self._click_by_visible_text(["Mi Negocio"])

        self._click_by_visible_text(["Administrar Negocios"])
        self._assert_text_visible(["Informacion General", "Información General"], "Informacion General section")
        self._assert_text_visible(["Detalles de la Cuenta"], "Detalles de la Cuenta section")
        self._assert_text_visible(["Tus Negocios"], "Tus Negocios section")
        self._assert_text_visible(["Seccion Legal", "Sección Legal"], "Seccion Legal section")
        screenshot = self._capture_screenshot("04-administrar-negocios")
        return ("Administrar Negocios page loaded with all required sections.", screenshot, self.active_driver.current_url)

    def _section_text(self, section_titles: Iterable[str]) -> str:
        for title in section_titles:
            literal = self._xpath_literal(title)
            xpath = (
                "//*[self::section or self::div]"
                f"[.//*[normalize-space()={literal}]]"
            )
            elements = self.active_driver.find_elements(By.XPATH, xpath)
            if elements:
                return elements[0].text
        return self.active_driver.find_element(By.TAG_NAME, "body").text

    def _step_validate_informacion_general(self) -> Tuple[str, Optional[str], Optional[str]]:
        section_text = self._section_text(["Informacion General", "Información General"])

        email_match = re.search(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}", section_text)
        if not email_match:
            raise WorkflowError("User email was not found in Informacion General.")

        lines = [line.strip() for line in section_text.splitlines() if line.strip()]
        name_visible = any(
            "@" not in line
            and "informacion general" not in line.lower()
            and "business plan" not in line.lower()
            and "cambiar plan" not in line.lower()
            and len(line) >= 3
            for line in lines
        )
        if not name_visible:
            raise WorkflowError("User name was not identified in Informacion General.")

        self._assert_text_visible(["BUSINESS PLAN"], "BUSINESS PLAN text")
        self._assert_text_visible(["Cambiar Plan"], "Cambiar Plan button")
        screenshot = self._capture_screenshot("05-informacion-general")
        return ("Informacion General contains user identity and plan details.", screenshot, self.active_driver.current_url)

    def _step_validate_detalles_cuenta(self) -> Tuple[str, Optional[str], Optional[str]]:
        self._assert_text_visible(["Cuenta creada"], "Cuenta creada text")
        self._assert_text_visible(["Estado activo"], "Estado activo text")
        self._assert_text_visible(["Idioma seleccionado"], "Idioma seleccionado text")
        screenshot = self._capture_screenshot("06-detalles-cuenta")
        return ("Detalles de la Cuenta validated.", screenshot, self.active_driver.current_url)

    def _step_validate_tus_negocios(self) -> Tuple[str, Optional[str], Optional[str]]:
        self._assert_text_visible(["Tus Negocios"], "Tus Negocios section")
        self._assert_text_visible(["Agregar Negocio"], "Agregar Negocio button")
        self._assert_text_visible(["Tienes 2 de 3 negocios"], "quota text in Tus Negocios")
        body_text = self.active_driver.find_element(By.TAG_NAME, "body").text
        if "Tus Negocios" not in body_text:
            raise WorkflowError("Business list section text is not visible.")
        screenshot = self._capture_screenshot("07-tus-negocios")
        return ("Tus Negocios content and quota validated.", screenshot, self.active_driver.current_url)

    def _validate_legal_page(
        self,
        link_labels: Iterable[str],
        expected_heading_options: Iterable[str],
        screenshot_name: str,
        report_label: str,
    ) -> Tuple[str, Optional[str], Optional[str]]:
        previous_url = self.active_driver.current_url
        origin_handle = self.active_driver.current_window_handle
        previous_handles = set(self.active_driver.window_handles)

        self._click_by_visible_text(link_labels, scope_xpath="//*[contains(., 'Legal')]")
        new_handle = self._wait_for_new_window(previous_handles, timeout=10)
        opened_new_tab = new_handle is not None

        if opened_new_tab:
            self.active_driver.switch_to.window(new_handle)
            self._wait_for_document_ready()
        elif self.active_driver.current_url == previous_url:
            self._wait_after_click()

        self._assert_text_visible(expected_heading_options, f"{report_label} heading")
        page_text = self.active_driver.find_element(By.TAG_NAME, "body").text
        if len(page_text.strip()) < 120:
            raise WorkflowError(f"Legal page for '{report_label}' does not contain enough visible text.")

        final_url = self.active_driver.current_url
        screenshot = self._capture_screenshot(screenshot_name)

        if opened_new_tab:
            self.active_driver.close()
            self.active_driver.switch_to.window(origin_handle)
            self._wait_for_document_ready()
        else:
            self.active_driver.back()
            self._wait_for_document_ready()

        return (f"{report_label} validated at URL: {final_url}", screenshot, final_url)

    def _step_validate_terminos(self) -> Tuple[str, Optional[str], Optional[str]]:
        return self._validate_legal_page(
            link_labels=["Términos y Condiciones", "Terminos y Condiciones"],
            expected_heading_options=["Terminos y Condiciones", "Términos y Condiciones"],
            screenshot_name="08-terminos-y-condiciones",
            report_label="Términos y Condiciones",
        )

    def _step_validate_politica(self) -> Tuple[str, Optional[str], Optional[str]]:
        return self._validate_legal_page(
            link_labels=["Política de Privacidad", "Politica de Privacidad"],
            expected_heading_options=["Politica de Privacidad", "Política de Privacidad"],
            screenshot_name="09-politica-de-privacidad",
            report_label="Política de Privacidad",
        )

    def _finalize(self) -> int:
        for field in REPORT_FIELDS:
            if field not in self.results:
                self._record_fail(field, "Step result missing unexpectedly.")

        report_payload = {
            "test_name": "saleads_mi_negocio_full_test",
            "executed_at_utc": datetime.now(timezone.utc).isoformat(),
            "login_url": self.login_url,
            "results": [asdict(self.results[field]) for field in REPORT_FIELDS],
        }

        json_path = self.run_dir / "report.json"
        json_path.write_text(json.dumps(report_payload, indent=2, ensure_ascii=False), encoding="utf-8")

        markdown_lines = [
            "# SaleADS Mi Negocio Workflow Report",
            "",
            f"- Executed at (UTC): {report_payload['executed_at_utc']}",
            f"- Login URL: {self.login_url}",
            f"- Artifacts folder: `{self.run_dir}`",
            "",
            "## Step status",
            "",
            "| Step | Status | Details | Final URL | Screenshot |",
            "|---|---|---|---|---|",
        ]
        for field in REPORT_FIELDS:
            result = self.results[field]
            details = result.details.replace("\n", " ").strip()
            final_url = result.url or ""
            screenshot = result.screenshot or ""
            markdown_lines.append(f"| {field} | {result.status} | {details} | {final_url} | {screenshot} |")

        markdown_path = self.run_dir / "report.md"
        markdown_path.write_text("\n".join(markdown_lines), encoding="utf-8")

        print(json.dumps(report_payload, indent=2, ensure_ascii=False))
        return 0 if all(self.results[field].status == "PASS" for field in REPORT_FIELDS) else 1


def parse_args(argv: List[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run SaleADS Mi Negocio full workflow validation.")
    parser.add_argument(
        "--login-url",
        default=None,
        help="Login page URL for the current SaleADS environment. Can also come from SALEADS_LOGIN_URL.",
    )
    parser.add_argument(
        "--artifacts-root",
        default="target/saleads-mi-negocio",
        help="Directory where reports and screenshots will be saved.",
    )
    parser.add_argument("--headless", action="store_true", help="Run browser in headless mode.")
    parser.add_argument("--timeout-seconds", type=int, default=30, help="Explicit wait timeout in seconds.")
    return parser.parse_args(argv)


def main(argv: List[str]) -> int:
    args = parse_args(argv)
    login_url = args.login_url or None
    if not login_url:
        login_url = __import__("os").environ.get("SALEADS_LOGIN_URL")
    if not login_url:
        print(
            "Missing login URL. Pass --login-url or set SALEADS_LOGIN_URL.",
            file=sys.stderr,
        )
        return 2

    runner = SaleadsWorkflowRunner(
        login_url=login_url,
        artifacts_root=Path(args.artifacts_root),
        headless=args.headless,
        timeout_seconds=args.timeout_seconds,
    )
    return runner.run()


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
