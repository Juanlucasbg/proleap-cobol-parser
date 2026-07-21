#!/usr/bin/env python3
"""SaleADS Mi Negocio end-to-end UI workflow validation."""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable, Iterable, Optional

from selenium import webdriver
from selenium.common.exceptions import TimeoutException
from selenium.webdriver import ChromeOptions
from selenium.webdriver.common.by import By
from selenium.webdriver.remote.webelement import WebElement
from selenium.webdriver.support import expected_conditions as ec
from selenium.webdriver.support.ui import WebDriverWait


class ValidationError(RuntimeError):
    """Raised when a required UI assertion fails."""


@dataclass
class StepResult:
    status: str
    details: str
    screenshot: Optional[str] = None
    final_url: Optional[str] = None


class SaleadsMiNegocioWorkflow:
    def __init__(
        self,
        login_url: Optional[str],
        email: str,
        timeout_seconds: int,
        headless: bool,
        output_root: Path,
    ) -> None:
        self.login_url = login_url
        self.email = email
        self.timeout_seconds = timeout_seconds
        self.output_root = output_root
        self.results: dict[str, StepResult] = {}
        self.terms_url: Optional[str] = None
        self.privacy_url: Optional[str] = None

        options = ChromeOptions()
        options.add_argument("--window-size=1920,1080")
        options.add_argument("--disable-dev-shm-usage")
        options.add_argument("--no-sandbox")
        if headless:
            options.add_argument("--headless=new")
        self.driver = webdriver.Chrome(options=options)
        self.wait = WebDriverWait(self.driver, timeout_seconds)

    def run(self) -> int:
        try:
            self._navigate_to_login()
            self._run_step("Login", self._step_login_with_google)
            self._run_step("Mi Negocio menu", self._step_open_mi_negocio_menu)
            self._run_step("Agregar Negocio modal", self._step_validate_agregar_negocio_modal)
            self._run_step("Administrar Negocios view", self._step_open_administrar_negocios)
            self._run_step("Información General", self._step_validate_informacion_general)
            self._run_step("Detalles de la Cuenta", self._step_validate_detalles_cuenta)
            self._run_step("Tus Negocios", self._step_validate_tus_negocios)
            self._run_step("Términos y Condiciones", self._step_validate_terminos_y_condiciones)
            self._run_step("Política de Privacidad", self._step_validate_politica_privacidad)
        finally:
            report_paths = self._write_reports()
            self.driver.quit()

        summary = {name: result.status for name, result in self.results.items()}
        print(json.dumps({"summary": summary, "reports": report_paths}, ensure_ascii=False, indent=2))
        return 0 if all(result.status == "PASS" for result in self.results.values()) else 1

    def _run_step(self, name: str, step_fn: Callable[[], str | dict[str, str]]) -> None:
        try:
            step_output = step_fn()
            if isinstance(step_output, dict):
                details = step_output["details"]
                screenshot = step_output.get("screenshot") or self._take_screenshot(name)
                final_url = step_output.get("final_url")
            else:
                details = step_output
                screenshot = self._take_screenshot(name)
                final_url = None
            self.results[name] = StepResult(
                status="PASS",
                details=details,
                screenshot=screenshot,
                final_url=final_url,
            )
        except Exception as exc:  # pylint: disable=broad-except
            screenshot = self._take_screenshot(f"{name}_failure")
            self.results[name] = StepResult(
                status="FAIL",
                details=str(exc),
                screenshot=screenshot,
                final_url=self.driver.current_url,
            )

    def _navigate_to_login(self) -> None:
        if self.login_url:
            self.driver.get(self.login_url)
            self._wait_for_ui_load()
            return

        # Required to keep the script environment-agnostic.
        raise ValidationError(
            "A login URL is required. Pass --login-url for the current SaleADS environment."
        )

    def _step_login_with_google(self) -> str:
        self._assert_not_blocked()
        login_button = self._find_first_visible(
            [
                (By.XPATH, "//button[contains(., 'Google')]"),
                (By.XPATH, "//a[contains(., 'Google')]"),
                (By.XPATH, "//*[self::button or self::a][contains(., 'Sign in with Google')]"),
            ]
        )
        self._click(login_button)
        self._wait_for_ui_load()

        account_matchers = [
            (By.XPATH, f"//*[contains(normalize-space(.), '{self.email}')]"),
            (By.XPATH, f"//*[contains(@data-email, '{self.email}')]"),
        ]
        account = self._find_first_visible(account_matchers, timeout_seconds=8, required=False)
        if account is not None:
            self._click(account)
            self._wait_for_ui_load()

        self._assert_visible(
            [
                (By.XPATH, "//aside"),
                (By.XPATH, "//nav"),
                (By.XPATH, "//*[contains(., 'Negocio')]"),
            ],
            "Main interface and left sidebar are not visible after login.",
        )
        return "Google login flow completed and main interface detected."

    def _step_open_mi_negocio_menu(self) -> str:
        negocio = self._find_first_visible(
            [
                (By.XPATH, "//*[self::a or self::button or self::span][normalize-space()='Negocio']"),
                (By.XPATH, "//*[self::a or self::button or self::span][contains(normalize-space(.), 'Negocio')]"),
            ]
        )
        self._click(negocio)
        self._wait_for_ui_load()

        mi_negocio = self._find_first_visible(
            [
                (By.XPATH, "//*[self::a or self::button or self::span][normalize-space()='Mi Negocio']"),
                (By.XPATH, "//*[self::a or self::button][contains(normalize-space(.), 'Mi Negocio')]"),
            ]
        )
        self._click(mi_negocio)
        self._wait_for_ui_load()

        self._expect_text_visible(["Agregar Negocio"], "Agregar Negocio option is not visible.")
        self._expect_text_visible(["Administrar Negocios"], "Administrar Negocios option is not visible.")
        return "Mi Negocio menu expanded with required submenu options."

    def _step_validate_agregar_negocio_modal(self) -> str:
        self._click_by_text(["Agregar Negocio"])
        self._wait_for_ui_load()

        self._expect_text_visible(
            ["Crear Nuevo Negocio"],
            "Crear Nuevo Negocio modal title is not visible.",
        )
        self._assert_visible(
            [
                (By.XPATH, "//input[@placeholder='Nombre del Negocio']"),
                (By.XPATH, "//input[contains(@aria-label, 'Nombre del Negocio')]"),
                (By.XPATH, "//label[contains(., 'Nombre del Negocio')]"),
            ],
            "Nombre del Negocio field is missing.",
        )
        self._expect_text_visible(
            ["Tienes 2 de 3 negocios"],
            "Business quota text is missing.",
        )
        self._expect_text_visible(["Cancelar"], "Cancelar button is missing.")
        self._expect_text_visible(["Crear Negocio"], "Crear Negocio button is missing.")

        field = self._find_first_visible(
            [
                (By.XPATH, "//input[@placeholder='Nombre del Negocio']"),
                (By.XPATH, "//input[contains(@aria-label, 'Nombre del Negocio')]"),
                (By.XPATH, "//input"),
            ]
        )
        field.click()
        field.clear()
        field.send_keys("Negocio Prueba Automatización")
        self._wait_for_ui_load()

        self._click_by_text(["Cancelar"])
        self._wait_for_ui_load()
        return "Agregar Negocio modal validated and canceled."

    def _step_open_administrar_negocios(self) -> str:
        administrar = self._find_first_visible(
            [(By.XPATH, "//*[self::a or self::button][contains(normalize-space(.), 'Administrar Negocios')]")],
            timeout_seconds=4,
            required=False,
        )
        if administrar is None:
            self._click_by_text(["Mi Negocio"])
            self._wait_for_ui_load()

        self._click_by_text(["Administrar Negocios"])
        self._wait_for_ui_load()

        self._expect_text_visible(
            ["Información General", "Informacion General"],
            "Información General section is not visible.",
        )
        self._expect_text_visible(
            ["Detalles de la Cuenta", "Detalles de la cuenta"],
            "Detalles de la Cuenta section is not visible.",
        )
        self._expect_text_visible(
            ["Tus Negocios"],
            "Tus Negocios section is not visible.",
        )
        self._expect_text_visible(
            ["Sección Legal", "Seccion Legal"],
            "Sección Legal section is not visible.",
        )
        return "Administrar Negocios page loaded with required sections."

    def _step_validate_informacion_general(self) -> str:
        self._expect_text_visible([self.email], "User email is not visible.")
        self._expect_text_visible(["BUSINESS PLAN"], "BUSINESS PLAN text is not visible.")
        self._expect_text_visible(["Cambiar Plan"], "Cambiar Plan button is not visible.")

        name_candidates = self.driver.find_elements(
            By.XPATH,
            "//*[self::h1 or self::h2 or self::h3 or self::span or self::p][string-length(normalize-space(.)) > 2]",
        )
        has_name = any("@" not in element.text and re.search(r"[A-Za-zÁÉÍÓÚáéíóúÑñ]", element.text) for element in name_candidates)
        if not has_name:
            raise ValidationError("User name is not clearly visible in Información General.")

        return "Información General fields are visible."

    def _step_validate_detalles_cuenta(self) -> str:
        self._expect_text_visible(
            ["Cuenta creada"],
            "'Cuenta creada' is not visible.",
        )
        self._expect_text_visible(
            ["Estado activo", "Activo"],
            "'Estado activo' is not visible.",
        )
        self._expect_text_visible(
            ["Idioma seleccionado", "Idioma"],
            "'Idioma seleccionado' is not visible.",
        )
        return "Detalles de la Cuenta fields are visible."

    def _step_validate_tus_negocios(self) -> str:
        self._expect_text_visible(
            ["Tus Negocios"],
            "Tus Negocios title is not visible.",
        )
        self._expect_text_visible(["Agregar Negocio"], "Agregar Negocio button is missing in Tus Negocios.")
        self._expect_text_visible(
            ["Tienes 2 de 3 negocios"],
            "Business quota text is missing in Tus Negocios.",
        )
        business_items = self.driver.find_elements(
            By.XPATH,
            "//*[contains(@class, 'business') or contains(@class, 'negocio') or self::li or self::tr]",
        )
        if not business_items:
            raise ValidationError("Business list is not visible in Tus Negocios.")
        return "Tus Negocios list and controls are visible."

    def _step_validate_terminos_y_condiciones(self) -> dict[str, str]:
        details, final_url, screenshot = self._validate_legal_link(
            link_labels=["Términos y Condiciones", "Terminos y Condiciones"],
            heading_labels=["Términos y Condiciones", "Terminos y Condiciones"],
        )
        self.terms_url = final_url
        return {"details": details, "final_url": final_url, "screenshot": screenshot}

    def _step_validate_politica_privacidad(self) -> dict[str, str]:
        details, final_url, screenshot = self._validate_legal_link(
            link_labels=["Política de Privacidad", "Politica de Privacidad"],
            heading_labels=["Política de Privacidad", "Politica de Privacidad"],
        )
        self.privacy_url = final_url
        return {"details": details, "final_url": final_url, "screenshot": screenshot}

    def _validate_legal_link(self, link_labels: list[str], heading_labels: list[str]) -> tuple[str, str, str]:
        app_window = self.driver.current_window_handle
        initial_handles = set(self.driver.window_handles)
        self._click_by_text(link_labels)
        self._wait_for_ui_load()

        new_handles = set(self.driver.window_handles) - initial_handles
        opened_new_tab = len(new_handles) > 0
        if opened_new_tab:
            self.driver.switch_to.window(new_handles.pop())
            self._wait_for_ui_load()

        self._expect_text_visible(heading_labels, "Legal heading is not visible.")
        legal_text_elements = self.driver.find_elements(
            By.XPATH, "//p[string-length(normalize-space(.)) > 40] | //div[string-length(normalize-space(.)) > 80]"
        )
        if not legal_text_elements:
            raise ValidationError("Legal content text is not visible.")

        final_url = self.driver.current_url
        legal_screenshot = self._take_screenshot(f"{heading_labels[0]}_page")

        if opened_new_tab:
            self.driver.close()
            self.driver.switch_to.window(app_window)
        else:
            self.driver.back()
        self._wait_for_ui_load()
        return f"Validated legal page '{heading_labels[0]}'.", final_url, legal_screenshot

    def _assert_not_blocked(self) -> None:
        blocked_markers = [
            "sorry, you have been blocked",
            "checking your browser before accessing",
            "attention required",
            "cloudflare",
        ]
        source_lower = self.driver.page_source.lower()
        for marker in blocked_markers:
            if marker in source_lower:
                raise ValidationError(f"Access blocked before login interaction ({marker}).")

    def _wait_for_ui_load(self) -> None:
        self.wait.until(lambda driver: driver.execute_script("return document.readyState") == "complete")
        time.sleep(1)

    def _click(self, element: WebElement) -> None:
        self.driver.execute_script("arguments[0].scrollIntoView({block: 'center'});", element)
        self.wait.until(lambda _: element.is_displayed() and element.is_enabled())
        try:
            element.click()
        except Exception:  # pylint: disable=broad-except
            self.driver.execute_script("arguments[0].click();", element)
        time.sleep(1)

    def _click_by_text(self, labels: Iterable[str]) -> None:
        options: list[tuple[str, str]] = []
        for label in labels:
            options.append((By.XPATH, f"//*[self::a or self::button or self::span][normalize-space()='{label}']"))
            options.append((By.XPATH, f"//*[self::a or self::button][contains(normalize-space(.), '{label}')]"))
            options.append((By.XPATH, f"//*[contains(normalize-space(.), '{label}')]"))
        element = self._find_first_visible(options)
        self._click(element)

    def _assert_visible(
        self,
        locators: list[tuple[str, str]],
        error_message: str,
        timeout_seconds: Optional[int] = None,
    ) -> WebElement:
        element = self._find_first_visible(locators, timeout_seconds=timeout_seconds, required=False)
        if element is None:
            raise ValidationError(error_message)
        return element

    def _find_first_visible(
        self,
        locators: list[tuple[str, str]],
        timeout_seconds: Optional[int] = None,
        required: bool = True,
    ) -> Optional[WebElement]:
        timeout = timeout_seconds if timeout_seconds is not None else self.timeout_seconds
        deadline = time.time() + timeout
        while time.time() < deadline:
            for locator in locators:
                elements = self.driver.find_elements(*locator)
                for element in elements:
                    if element.is_displayed():
                        return element
            time.sleep(0.25)
        if required:
            raise TimeoutException(f"No visible element found for locators: {locators}")
        return None

    def _expect_text_visible(self, texts: list[str], error_message: str) -> None:
        for text in texts:
            locator = (By.XPATH, f"//*[contains(normalize-space(.), '{text}')]")
            if self._find_first_visible([locator], timeout_seconds=4, required=False) is not None:
                return
        raise ValidationError(error_message)

    def _take_screenshot(self, label: str) -> str:
        safe_label = re.sub(r"[^A-Za-z0-9._-]+", "_", label).strip("_") or "screenshot"
        path = self.output_root / f"{safe_label}.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        self.driver.save_screenshot(str(path))
        return str(path)

    def _write_reports(self) -> dict[str, str]:
        payload = {
            "generated_at_utc": datetime.now(timezone.utc).isoformat(),
            "login_url": self.login_url,
            "email": self.email,
            "results": {name: asdict(result) for name, result in self.results.items()},
            "terms_final_url": self.terms_url,
            "privacy_final_url": self.privacy_url,
        }
        json_path = self.output_root / "report.json"
        md_path = self.output_root / "report.md"
        self.output_root.mkdir(parents=True, exist_ok=True)
        json_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

        lines = [
            "# SaleADS Mi Negocio workflow report",
            "",
            f"- Generated at (UTC): {payload['generated_at_utc']}",
            f"- Login URL: {self.login_url}",
            f"- Google account: {self.email}",
            "",
            "| Step | Status | Details | Screenshot | Final URL |",
            "| --- | --- | --- | --- | --- |",
        ]
        for step_name, result in self.results.items():
            screenshot = result.screenshot or ""
            final_url = result.final_url or ""
            details = result.details.replace("|", "\\|")
            lines.append(f"| {step_name} | {result.status} | {details} | {screenshot} | {final_url} |")
        md_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
        return {"json": str(json_path), "markdown": str(md_path)}


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate SaleADS Mi Negocio workflow using Selenium."
    )
    parser.add_argument(
        "--login-url",
        help="Current environment login URL. Required to keep the test environment-agnostic.",
    )
    parser.add_argument(
        "--email",
        default="juanlucasbarbiergarzon@gmail.com",
        help="Google account to pick if account selector appears.",
    )
    parser.add_argument(
        "--timeout-seconds",
        type=int,
        default=20,
        help="Explicit UI wait timeout for each action.",
    )
    parser.add_argument(
        "--headless",
        action="store_true",
        help="Run the browser in headless mode.",
    )
    parser.add_argument(
        "--artifacts-dir",
        default="target/saleads-mi-negocio",
        help="Directory where screenshots and reports are stored.",
    )
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    output_root = Path(args.artifacts_dir) / timestamp
    workflow = SaleadsMiNegocioWorkflow(
        login_url=args.login_url,
        email=args.email,
        timeout_seconds=args.timeout_seconds,
        headless=args.headless,
        output_root=output_root,
    )
    return workflow.run()


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
