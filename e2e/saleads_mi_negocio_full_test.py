#!/usr/bin/env python3
"""SaleADS Mi Negocio end-to-end workflow validation.

This script validates:
1) Login with Google
2) Mi Negocio menu behavior
3) Agregar Negocio modal contents
4) Administrar Negocios sections
5) Informacion General
6) Detalles de la Cuenta
7) Tus Negocios
8) Terminos y Condiciones page
9) Politica de Privacidad page

Artifacts:
- Screenshots at important checkpoints
- JSON report with PASS/FAIL per requested report field
"""

from __future__ import annotations

import json
import os
import re
import sys
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Callable, Sequence

from selenium import webdriver
from selenium.common.exceptions import TimeoutException
from selenium.webdriver import Chrome
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
from selenium.webdriver.remote.webelement import WebElement
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait


DEFAULT_TIMEOUT = int(os.getenv("SALEADS_TIMEOUT_SECONDS", "35"))
DEFAULT_GOOGLE_EMAIL = os.getenv(
    "SALEADS_GOOGLE_EMAIL", "juanlucasbarbiergarzon@gmail.com"
)
TEXT_TRANSLATE_FROM = "ABCDEFGHIJKLMNOPQRSTUVWXYZÁÉÍÓÚÜÑáéíóúüñ"
TEXT_TRANSLATE_TO = "abcdefghijklmnopqrstuvwxyzaeiouunaeiouun"
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


def env_flag(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


TIMESTAMP = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
RUN_ROOT = Path(
    os.getenv(
        "SALEADS_ARTIFACTS_DIR",
        f"target/saleads_mi_negocio_full_test/{TIMESTAMP}",
    )
)
SCREENSHOT_DIR = RUN_ROOT / "screenshots"
REPORT_PATH = Path(os.getenv("SALEADS_REPORT_PATH", str(RUN_ROOT / "report.json")))


@dataclass
class StepResult:
    name: str
    passed: bool
    details: str
    screenshots: list[str] = field(default_factory=list)
    url: str | None = None


class SaleadsMiNegocioWorkflow:
    def __init__(self) -> None:
        self.timeout = DEFAULT_TIMEOUT
        self.results: list[StepResult] = []
        self.legal_urls: dict[str, str] = {}
        SCREENSHOT_DIR.mkdir(parents=True, exist_ok=True)
        REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)

        chrome_options = Options()
        chrome_options.add_argument("--window-size=1920,1080")
        chrome_options.add_argument("--disable-dev-shm-usage")
        chrome_options.add_argument("--no-sandbox")
        if env_flag("SALEADS_HEADLESS", True):
            chrome_options.add_argument("--headless=new")

        self.driver: Chrome = webdriver.Chrome(options=chrome_options)
        self.wait = WebDriverWait(self.driver, self.timeout)
        self.app_window_handle: str | None = None

    def run(self) -> int:
        execution_error: Exception | None = None
        try:
            self.open_login_page_if_configured()
            self.run_all_steps()
        except Exception as exc:  # pylint: disable=broad-exception-caught
            execution_error = exc
            failure_shot = self.take_screenshot("workflow_bootstrap_failed")
            self.results.append(
                StepResult(
                    name="Login",
                    passed=False,
                    details=f"Workflow aborted before completion: {exc}",
                    screenshots=[failure_shot],
                    url=self.safe_current_url(),
                )
            )
        finally:
            self.write_report()
            self.driver.quit()

        if execution_error is not None:
            print(f"[FAIL] Workflow aborted: {execution_error}")
            return 1
        return 0 if all(result.passed for result in self.results) else 1

    # ---------------------------- core flow ----------------------------

    def run_all_steps(self) -> None:
        self.execute_step("Login", self.step_login_with_google)
        self.execute_step("Mi Negocio menu", self.step_open_mi_negocio_menu)
        self.execute_step("Agregar Negocio modal", self.step_validate_agregar_modal)
        self.execute_step(
            "Administrar Negocios view", self.step_open_administrar_negocios
        )
        self.execute_step("Informacion General", self.step_validate_informacion_general)
        self.execute_step("Detalles de la Cuenta", self.step_validate_detalles_cuenta)
        self.execute_step("Tus Negocios", self.step_validate_tus_negocios)
        self.execute_step(
            "Terminos y Condiciones",
            lambda: self.step_validate_legal_link(
                "Terminos y Condiciones", "Terminos y Condiciones", "terms_and_conditions"
            ),
        )
        self.execute_step(
            "Politica de Privacidad",
            lambda: self.step_validate_legal_link(
                "Politica de Privacidad", "Politica de Privacidad", "privacy_policy"
            ),
        )

    def execute_step(self, step_name: str, fn: Callable[[], StepResult]) -> None:
        try:
            step_result = fn()
        except Exception as exc:  # pylint: disable=broad-exception-caught
            failure_shot = self.take_screenshot(f"{step_name}_FAILED")
            step_result = StepResult(
                name=step_name,
                passed=False,
                details=f"Unexpected exception: {exc}",
                screenshots=[failure_shot],
                url=self.safe_current_url(),
            )
        self.results.append(step_result)
        print(
            f"[{'PASS' if step_result.passed else 'FAIL'}] {step_result.name}: "
            f"{step_result.details}"
        )

    # ---------------------------- step methods ----------------------------

    def step_login_with_google(self) -> StepResult:
        self.wait_for_page_ready()
        self.click_by_any_text(
            [
                "Sign in with Google",
                "Iniciar sesion con Google",
                "Iniciar sesión con Google",
                "Continuar con Google",
                "Login with Google",
            ],
            "Google login button",
        )

        # Account selector can appear either in same tab or popup.
        self.try_select_google_account(DEFAULT_GOOGLE_EMAIL)
        self.wait_for_post_click_load()

        self.require_any_visible_text(
            ["Negocio", "Mi Negocio", "Dashboard", "Inicio"],
            "main application interface",
            timeout=50,
        )
        self.require_sidebar_visible()
        self.app_window_handle = self.driver.current_window_handle
        shot = self.take_screenshot("step1_dashboard_loaded")
        return StepResult(
            name="Login",
            passed=True,
            details="Main interface and left sidebar are visible after Google login.",
            screenshots=[shot],
            url=self.safe_current_url(),
        )

    def step_open_mi_negocio_menu(self) -> StepResult:
        self.require_sidebar_visible()

        if not self.any_text_visible(["Mi Negocio"], timeout=4):
            self.click_by_any_text(["Negocio"], "Negocio section")
            self.wait_for_post_click_load()

        self.click_by_any_text(["Mi Negocio"], "Mi Negocio menu")
        self.wait_for_post_click_load()

        self.require_any_visible_text(["Agregar Negocio"], "Agregar Negocio option")
        self.require_any_visible_text(
            ["Administrar Negocios"], "Administrar Negocios option"
        )
        shot = self.take_screenshot("step2_mi_negocio_menu_expanded")
        return StepResult(
            name="Mi Negocio menu",
            passed=True,
            details="Mi Negocio expanded with Agregar Negocio and Administrar Negocios.",
            screenshots=[shot],
            url=self.safe_current_url(),
        )

    def step_validate_agregar_modal(self) -> StepResult:
        self.click_by_any_text(["Agregar Negocio"], "Agregar Negocio")
        self.wait_for_post_click_load()

        self.require_any_visible_text(["Crear Nuevo Negocio"], "modal title")
        self.require_field_or_label("Nombre del Negocio")
        self.require_any_visible_text(["Tienes 2 de 3 negocios"], "business quota text")
        self.require_any_visible_text(["Cancelar"], "Cancelar button")
        self.require_any_visible_text(["Crear Negocio"], "Crear Negocio button")

        modal_shot = self.take_screenshot("step3_agregar_negocio_modal")

        field = self.find_first_visible(
            [
                self.xpath_input_by_label("Nombre del Negocio"),
                "//input[contains(@placeholder,'Nombre del Negocio')]",
                "//input[@name='nombreNegocio']",
            ],
            timeout=4,
        )
        if field is not None:
            field.click()
            field.clear()
            field.send_keys("Negocio Prueba Automatizacion")

        self.click_by_any_text(["Cancelar"], "Cancelar")
        self.wait_for_post_click_load()

        return StepResult(
            name="Agregar Negocio modal",
            passed=True,
            details=(
                "Crear Nuevo Negocio modal validated with required fields and buttons; "
                "optional input typed then cancelled."
            ),
            screenshots=[modal_shot],
            url=self.safe_current_url(),
        )

    def step_open_administrar_negocios(self) -> StepResult:
        if not self.any_text_visible(["Administrar Negocios"], timeout=3):
            self.click_by_any_text(["Mi Negocio"], "Mi Negocio menu re-open")
            self.wait_for_post_click_load()

        self.click_by_any_text(["Administrar Negocios"], "Administrar Negocios")
        self.wait_for_post_click_load()

        self.require_any_visible_text(
            ["Informacion General", "Información General"], "Informacion General"
        )
        self.require_any_visible_text(["Detalles de la Cuenta"], "Detalles de la Cuenta")
        self.require_any_visible_text(["Tus Negocios"], "Tus Negocios")
        self.require_any_visible_text(["Seccion Legal", "Sección Legal"], "Seccion Legal")
        shot = self.take_screenshot("step4_administrar_negocios")
        return StepResult(
            name="Administrar Negocios view",
            passed=True,
            details=(
                "Administrar Negocios page loaded with Informacion General, "
                "Detalles de la Cuenta, Tus Negocios and Seccion Legal."
            ),
            screenshots=[shot],
            url=self.safe_current_url(),
        )

    def step_validate_informacion_general(self) -> StepResult:
        self.require_any_visible_text(
            ["Informacion General", "Información General"], "Informacion General"
        )
        self.require_any_visible_text([DEFAULT_GOOGLE_EMAIL], "user email")
        self.require_any_visible_text(["BUSINESS PLAN"], "BUSINESS PLAN text")
        self.require_any_visible_text(["Cambiar Plan"], "Cambiar Plan button")

        if not self.visible_person_name():
            raise AssertionError(
                "User name was not detected in Informacion General using generic name pattern."
            )

        return StepResult(
            name="Informacion General",
            passed=True,
            details=(
                "User name and email are visible, BUSINESS PLAN is present and "
                "Cambiar Plan button is displayed."
            ),
            url=self.safe_current_url(),
        )

    def step_validate_detalles_cuenta(self) -> StepResult:
        self.require_any_visible_text(["Cuenta creada"], "Cuenta creada")
        self.require_any_visible_text(["Estado activo"], "Estado activo")
        self.require_any_visible_text(["Idioma seleccionado"], "Idioma seleccionado")
        return StepResult(
            name="Detalles de la Cuenta",
            passed=True,
            details=(
                "Cuenta creada, Estado activo, and Idioma seleccionado are visible."
            ),
            url=self.safe_current_url(),
        )

    def step_validate_tus_negocios(self) -> StepResult:
        self.require_any_visible_text(["Tus Negocios"], "Tus Negocios section")
        self.require_any_visible_text(["Agregar Negocio"], "Agregar Negocio button")
        self.require_any_visible_text(["Tienes 2 de 3 negocios"], "quota text")

        business_items = self.driver.find_elements(
            By.XPATH,
            (
                "//*[contains(translate(normalize-space(.),"
                "'ABCDEFGHIJKLMNOPQRSTUVWXYZ',"
                "'abcdefghijklmnopqrstuvwxyz'),'negocio') and not(self::button)]"
            ),
        )
        if not any(item.is_displayed() for item in business_items):
            raise AssertionError("Business list is not visible in Tus Negocios section.")

        return StepResult(
            name="Tus Negocios",
            passed=True,
            details=(
                "Business list area is visible, Agregar Negocio exists, and quota text is shown."
            ),
            url=self.safe_current_url(),
        )

    def step_validate_legal_link(
        self, step_name: str, visible_link_text: str, slug: str
    ) -> StepResult:
        origin = self.driver.current_window_handle
        before_handles = list(self.driver.window_handles)
        before_url = self.safe_current_url()

        self.click_by_any_text([visible_link_text], f"{visible_link_text} link")
        self.wait_for_post_click_load()

        maybe_new_tab = self.wait_for_new_tab_or_navigation(before_handles, before_url)
        switched_to_new_tab = False

        if maybe_new_tab:
            switched_to_new_tab = True
            self.driver.switch_to.window(maybe_new_tab)
            self.wait_for_page_ready()

        heading_options = [step_name]
        if step_name == "Terminos y Condiciones":
            heading_options.append("Términos y Condiciones")
        if step_name == "Politica de Privacidad":
            heading_options.append("Política de Privacidad")

        self.require_any_visible_text(heading_options, f"{step_name} heading", timeout=30)
        if not self.has_visible_legal_content():
            raise AssertionError(f"Legal content text is not visible for {step_name}.")

        shot = self.take_screenshot(f"step_{slug}")
        final_url = self.safe_current_url()
        self.legal_urls[step_name] = final_url

        if switched_to_new_tab:
            self.driver.close()
            self.driver.switch_to.window(origin)
            self.wait_for_page_ready()
        else:
            self.driver.back()
            self.wait_for_page_ready()

        return StepResult(
            name=step_name,
            passed=True,
            details=f"{step_name} page validated with visible legal text.",
            screenshots=[shot],
            url=final_url,
        )

    # ---------------------------- helper methods ----------------------------

    def open_login_page_if_configured(self) -> None:
        start_url = os.getenv("SALEADS_START_URL", "").strip()
        if start_url:
            self.driver.get(start_url)
            self.wait_for_page_ready()
            return

        current_url = self.safe_current_url()
        if current_url in {"about:blank", "data:,"}:
            raise RuntimeError(
                "SALEADS_START_URL is required when the browser is not already on "
                "the SaleADS login page."
            )

    def safe_current_url(self) -> str:
        try:
            return self.driver.current_url
        except Exception:  # pylint: disable=broad-exception-caught
            return ""

    def wait_for_page_ready(self) -> None:
        self.wait.until(
            lambda driver: driver.execute_script("return document.readyState") == "complete"
        )

    def wait_for_post_click_load(self) -> None:
        self.wait_for_page_ready()
        time.sleep(0.6)

    def any_text_visible(self, texts: Sequence[str], timeout: int | None = None) -> bool:
        timeout = timeout or self.timeout
        end_time = time.time() + timeout
        while time.time() < end_time:
            if self.find_visible_text_element(texts) is not None:
                return True
            time.sleep(0.2)
        return False

    def require_any_visible_text(
        self, texts: Sequence[str], context: str, timeout: int | None = None
    ) -> WebElement:
        timeout = timeout or self.timeout
        end_time = time.time() + timeout
        while time.time() < end_time:
            element = self.find_visible_text_element(texts)
            if element is not None:
                return element
            time.sleep(0.2)
        raise AssertionError(f"Unable to find visible text for {context}: {texts}")

    def find_visible_text_element(self, texts: Sequence[str]) -> WebElement | None:
        for text in texts:
            normalized = self.normalize_text(text)
            xpath_candidates = [
                (
                    "//*[contains(translate(normalize-space(.),"
                    f"'{TEXT_TRANSLATE_FROM}',"
                    f"'{TEXT_TRANSLATE_TO}'),"
                    f"'{normalized}') and not(self::script) and not(self::style)]"
                ),
            ]
            for xpath in xpath_candidates:
                elements = self.driver.find_elements(By.XPATH, xpath)
                for element in elements:
                    if element.is_displayed():
                        return element
        return None

    def click_by_any_text(self, texts: Sequence[str], context: str) -> None:
        target = self.require_any_visible_text(texts, context)
        self.scroll_into_view(target)
        self.wait.until(EC.element_to_be_clickable(target))
        target.click()
        self.wait_for_post_click_load()

    def try_select_google_account(self, email: str) -> None:
        account_selected = False

        for handle in list(self.driver.window_handles):
            self.driver.switch_to.window(handle)
            email_element = self.find_visible_text_element([email])
            if email_element is not None:
                self.scroll_into_view(email_element)
                email_element.click()
                account_selected = True
                self.wait_for_post_click_load()
                break

        if not account_selected:
            # Some environments may bypass account chooser if session is already authenticated.
            return

    def require_sidebar_visible(self) -> None:
        sidebar_xpath = (
            "//aside | //nav[.//*[contains(translate(normalize-space(.),"
            "'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'negocio')]]"
        )
        self.wait.until(
            lambda driver: any(
                element.is_displayed()
                for element in driver.find_elements(By.XPATH, sidebar_xpath)
            )
        )

    def require_field_or_label(self, label_text: str) -> None:
        possible_xpaths = [
            f"//label[contains(normalize-space(),'{label_text}')]",
            self.xpath_input_by_label(label_text),
            f"//input[contains(@placeholder,'{label_text}')]",
        ]
        element = self.find_first_visible(possible_xpaths, timeout=6)
        if element is None:
            raise AssertionError(f"Field/label not found for: {label_text}")

    def xpath_input_by_label(self, label_text: str) -> str:
        return (
            f"//label[contains(normalize-space(),'{label_text}')]/following::input[1]"
        )

    def find_first_visible(
        self, xpath_candidates: Sequence[str], timeout: int
    ) -> WebElement | None:
        end_time = time.time() + timeout
        while time.time() < end_time:
            for xpath in xpath_candidates:
                elements = self.driver.find_elements(By.XPATH, xpath)
                for element in elements:
                    if element.is_displayed():
                        return element
            time.sleep(0.2)
        return None

    def scroll_into_view(self, element: WebElement) -> None:
        self.driver.execute_script(
            "arguments[0].scrollIntoView({block:'center', inline:'nearest'});", element
        )

    def wait_for_new_tab_or_navigation(
        self, before_handles: Sequence[str], before_url: str
    ) -> str | None:
        end_time = time.time() + self.timeout
        before_set = set(before_handles)
        while time.time() < end_time:
            current_handles = list(self.driver.window_handles)
            new_handles = [handle for handle in current_handles if handle not in before_set]
            if new_handles:
                return new_handles[-1]
            if self.safe_current_url() != before_url:
                return None
            time.sleep(0.25)
        raise TimeoutException("No new tab or navigation detected after legal link click.")

    def has_visible_legal_content(self) -> bool:
        text_candidates = self.driver.find_elements(By.XPATH, "//p | //article | //section")
        for candidate in text_candidates:
            if not candidate.is_displayed():
                continue
            content = candidate.text.strip()
            if len(content) >= 80:
                return True
        return False

    def visible_person_name(self) -> bool:
        visible_nodes = self.driver.find_elements(
            By.XPATH, "//*[self::span or self::div or self::p or self::h1 or self::h2]"
        )
        person_name_pattern = re.compile(r"^[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?: [A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)+$")
        skip_tokens = {
            "Informacion General",
            "Detalles de la Cuenta",
            "Tus Negocios",
            "Seccion Legal",
            "BUSINESS PLAN",
            "Cambiar Plan",
        }
        for node in visible_nodes:
            if not node.is_displayed():
                continue
            text = node.text.strip()
            if not text or len(text) > 80 or text in skip_tokens:
                continue
            if person_name_pattern.match(text):
                return True
        return False

    def normalize_text(self, value: str) -> str:
        lowered = value.strip().lower()
        translation_table = str.maketrans(
            {
                "á": "a",
                "é": "e",
                "í": "i",
                "ó": "o",
                "ú": "u",
                "ü": "u",
                "ñ": "n",
            }
        )
        return lowered.translate(translation_table)

    def take_screenshot(self, name: str) -> str:
        safe_name = re.sub(r"[^a-zA-Z0-9_-]+", "_", name).strip("_").lower()
        path = SCREENSHOT_DIR / f"{safe_name}.png"
        self.driver.save_screenshot(str(path))
        return str(path)

    # ---------------------------- reporting ----------------------------

    def write_report(self) -> None:
        key_to_report_name = {
            "Login": "Login",
            "Mi Negocio menu": "Mi Negocio menu",
            "Agregar Negocio modal": "Agregar Negocio modal",
            "Administrar Negocios view": "Administrar Negocios view",
            "Informacion General": "Información General",
            "Detalles de la Cuenta": "Detalles de la Cuenta",
            "Tus Negocios": "Tus Negocios",
            "Terminos y Condiciones": "Términos y Condiciones",
            "Politica de Privacidad": "Política de Privacidad",
        }

        summary: dict[str, str] = {field_name: "FAIL" for field_name in REPORT_FIELDS}
        for result in self.results:
            report_name = key_to_report_name.get(result.name, result.name)
            summary[report_name] = "PASS" if result.passed else "FAIL"

        payload = {
            "test_name": "saleads_mi_negocio_full_test",
            "timestamp_utc": datetime.now(timezone.utc).isoformat(),
            "summary": summary,
            "results": [
                {
                    "name": result.name,
                    "status": "PASS" if result.passed else "FAIL",
                    "details": result.details,
                    "screenshots": result.screenshots,
                    "url": result.url,
                }
                for result in self.results
            ],
            "legal_urls": self.legal_urls,
            "artifacts_dir": str(RUN_ROOT),
        }
        REPORT_PATH.write_text(json.dumps(payload, indent=2), encoding="utf-8")


def main() -> int:
    workflow = SaleadsMiNegocioWorkflow()
    return workflow.run()


if __name__ == "__main__":
    sys.exit(main())
