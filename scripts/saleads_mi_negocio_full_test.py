#!/usr/bin/env python3
"""SaleADS.ai Mi Negocio full workflow validation.

This script automates the following workflow:
1) Login with Google
2) Open Mi Negocio menu
3) Validate Agregar Negocio modal
4) Open Administrar Negocios
5) Validate Informacion General
6) Validate Detalles de la Cuenta
7) Validate Tus Negocios
8) Validate Terminos y Condiciones
9) Validate Politica de Privacidad
10) Produce PASS/FAIL report
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import re
import sys
import time
from collections import OrderedDict
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable, Optional

from selenium import webdriver
from selenium.common.exceptions import (
    ElementClickInterceptedException,
    NoSuchElementException,
    StaleElementReferenceException,
    TimeoutException,
    WebDriverException,
)
from selenium.webdriver.common.by import By
from selenium.webdriver.remote.webelement import WebElement
from selenium.webdriver.support import expected_conditions as ec
from selenium.webdriver.support.ui import WebDriverWait


REPORT_FIELDS = [
    "Login",
    "Mi Negocio menu",
    "Agregar Negocio modal",
    "Administrar Negocios view",
    "Informacion General",
    "Detalles de la Cuenta",
    "Tus Negocios",
    "Terminos y Condiciones",
    "Politica de Privacidad",
]

EMAIL_REGEX = re.compile(r"[^@\s]+@[^@\s]+\.[^@\s]+")


def xpath_literal(value: str) -> str:
    """Return a safe XPath literal for arbitrary text."""
    if "'" not in value:
        return f"'{value}'"
    if '"' not in value:
        return f'"{value}"'
    parts = value.split("'")
    quoted_parts = [f"'{part}'" for part in parts]
    return "concat(" + ", \"'\", ".join(quoted_parts) + ")"


def slugify(value: str) -> str:
    lowered = value.lower().strip()
    replaced = re.sub(r"[^a-z0-9]+", "-", lowered)
    return re.sub(r"-{2,}", "-", replaced).strip("-")


@dataclass
class StepState:
    status: str = "NOT_RUN"
    details: list[str] = field(default_factory=list)
    screenshot: Optional[str] = None
    url: Optional[str] = None

    def to_dict(self) -> dict[str, object]:
        payload: dict[str, object] = {"status": self.status, "details": self.details}
        if self.screenshot:
            payload["screenshot"] = self.screenshot
        if self.url:
            payload["url"] = self.url
        return payload


class SaleadsMiNegocioWorkflow:
    def __init__(
        self,
        login_url: Optional[str],
        google_account_email: str,
        artifacts_root: Path,
        headless: bool,
    ) -> None:
        timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        self.artifacts_dir = artifacts_root / timestamp
        self.artifacts_dir.mkdir(parents=True, exist_ok=True)
        self.login_url = login_url
        self.google_account_email = google_account_email
        self.headless = headless
        self.report: "OrderedDict[str, StepState]" = OrderedDict(
            (field_name, StepState()) for field_name in REPORT_FIELDS
        )
        self.driver: Optional[webdriver.Chrome] = None
        self.wait: Optional[WebDriverWait] = None
        self.app_handle: Optional[str] = None

    @property
    def web(self) -> webdriver.Chrome:
        if self.driver is None:
            raise RuntimeError("WebDriver has not been initialized.")
        return self.driver

    @property
    def waiter(self) -> WebDriverWait:
        if self.wait is None:
            raise RuntimeError("WebDriverWait has not been initialized.")
        return self.wait

    def run(self) -> int:
        self.setup_driver()
        try:
            self.execute_step("Login", self.step_login)
            if self.report["Login"].status != "PASS":
                self.fail_remaining_steps_because_login_failed()
            else:
                self.execute_step("Mi Negocio menu", self.step_mi_negocio_menu)
                self.execute_step("Agregar Negocio modal", self.step_agregar_negocio_modal)
                self.execute_step("Administrar Negocios view", self.step_administrar_negocios)
                self.execute_step("Informacion General", self.step_informacion_general)
                self.execute_step("Detalles de la Cuenta", self.step_detalles_cuenta)
                self.execute_step("Tus Negocios", self.step_tus_negocios)
                self.execute_step("Terminos y Condiciones", self.step_terminos_condiciones)
                self.execute_step("Politica de Privacidad", self.step_politica_privacidad)
        finally:
            self.write_report_files()
            if self.driver is not None:
                self.driver.quit()

        self.print_summary()
        all_passed = all(state.status == "PASS" for state in self.report.values())
        return 0 if all_passed else 1

    def setup_driver(self) -> None:
        options = webdriver.ChromeOptions()
        options.add_argument("--window-size=1600,1200")
        options.add_argument("--disable-dev-shm-usage")
        options.add_argument("--no-sandbox")
        if self.headless:
            options.add_argument("--headless=new")

        self.driver = webdriver.Chrome(options=options)
        self.wait = WebDriverWait(self.driver, 30)

    def execute_step(self, report_key: str, step_callable) -> None:  # type: ignore[no-untyped-def]
        try:
            details = step_callable()
            if not isinstance(details, list):
                details = []
            self.report[report_key].status = "PASS"
            self.report[report_key].details.extend(details)
        except Exception as exc:  # noqa: BLE001 - collect result and continue
            self.report[report_key].status = "FAIL"
            message = str(exc) or exc.__class__.__name__
            self.report[report_key].details.append(message)
            try:
                self.report[report_key].url = self.web.current_url
                if not self.report[report_key].screenshot:
                    failure_shot = self.screenshot(f"failure-{report_key}")
                    self.report[report_key].screenshot = failure_shot
                    self.report[report_key].details.append(f"Failure screenshot: {failure_shot}")
            except Exception:  # noqa: BLE001 - never mask original error
                pass

    def fail_remaining_steps_because_login_failed(self) -> None:
        reason = "Login step failed."
        if self.report["Login"].details:
            reason = self.report["Login"].details[-1]

        for field_name in REPORT_FIELDS[1:]:
            if self.report[field_name].status != "NOT_RUN":
                continue
            self.report[field_name].status = "FAIL"
            self.report[field_name].details.append(
                f"Skipped because Login failed: {reason}"
            )
            try:
                self.report[field_name].url = self.web.current_url
            except Exception:  # noqa: BLE001
                pass

    def detect_access_blocker(self) -> Optional[str]:
        blocker_markers = [
            "sorry, you have been blocked",
            "attention required",
            "just a moment",
            "verify you are human",
            "cloudflare",
            "access denied",
        ]
        try:
            page_text = self.web.find_element(By.TAG_NAME, "body").text.lower()
        except Exception:  # noqa: BLE001
            page_text = ""
        title = self.web.title.lower() if self.web.title else ""
        for marker in blocker_markers:
            if marker in page_text or marker in title:
                return marker
        return None

    def wait_for_ui(self, extra_sleep_seconds: float = 1.0) -> None:
        self.waiter.until(lambda d: d.execute_script("return document.readyState") in {"interactive", "complete"})
        if extra_sleep_seconds > 0:
            time.sleep(extra_sleep_seconds)

    def find_visible_by_text(
        self,
        text: str,
        timeout_seconds: float = 20,
        exact_match: bool = True,
    ) -> WebElement:
        literal = xpath_literal(text.strip())
        xpaths = [f"//*[normalize-space()={literal}]"]
        if not exact_match:
            xpaths.insert(0, f"//*[contains(normalize-space(), {literal})]")
        else:
            xpaths.append(f"//*[contains(normalize-space(), {literal})]")

        end = time.time() + timeout_seconds
        last_error: Optional[Exception] = None
        while time.time() < end:
            for xpath in xpaths:
                try:
                    elements = self.web.find_elements(By.XPATH, xpath)
                    for element in elements:
                        if element.is_displayed():
                            return element
                except (StaleElementReferenceException, WebDriverException) as err:
                    last_error = err
            time.sleep(0.25)

        if last_error:
            raise TimeoutException(f'Unable to locate visible text "{text}": {last_error}') from last_error
        raise TimeoutException(f'Unable to locate visible text "{text}" within {timeout_seconds} seconds.')

    def is_text_visible(self, text: str, exact_match: bool = True, timeout_seconds: float = 3) -> bool:
        try:
            self.find_visible_by_text(text, timeout_seconds=timeout_seconds, exact_match=exact_match)
            return True
        except TimeoutException:
            return False

    def resolve_click_target(self, element: WebElement) -> WebElement:
        if element.tag_name.lower() in {"button", "a"}:
            return element

        ancestor_xpath = (
            "./ancestor-or-self::*[self::button or self::a or @role='button' or contains(@class, 'btn')][1]"
        )
        try:
            candidate = element.find_element(By.XPATH, ancestor_xpath)
            return candidate
        except NoSuchElementException:
            return element

    def click_element_and_wait(self, element: WebElement) -> None:
        clickable = self.resolve_click_target(element)
        self.web.execute_script("arguments[0].scrollIntoView({block:'center'});", clickable)
        time.sleep(0.2)
        try:
            self.waiter.until(ec.element_to_be_clickable(clickable))
            clickable.click()
        except (ElementClickInterceptedException, TimeoutException, WebDriverException):
            self.web.execute_script("arguments[0].click();", clickable)
        self.wait_for_ui()

    def click_text(
        self,
        candidate_texts: Iterable[str],
        exact_match: bool = True,
        timeout_seconds: float = 15,
    ) -> str:
        for text in candidate_texts:
            try:
                element = self.find_visible_by_text(
                    text=text,
                    timeout_seconds=timeout_seconds,
                    exact_match=exact_match,
                )
                self.click_element_and_wait(element)
                return text
            except TimeoutException:
                continue

        options = ", ".join(candidate_texts)
        raise TimeoutException(f"Could not click any candidate text: {options}")

    def find_any_visible_text(
        self,
        candidate_texts: Iterable[str],
        timeout_seconds: float = 20,
    ) -> tuple[str, WebElement]:
        for text in candidate_texts:
            try:
                return text, self.find_visible_by_text(
                    text=text,
                    timeout_seconds=timeout_seconds,
                    exact_match=False,
                )
            except TimeoutException:
                continue
        options = ", ".join(candidate_texts)
        raise TimeoutException(f"Could not find any candidate text: {options}")

    def screenshot(self, label: str, full_page: bool = False) -> str:
        file_name = f"{slugify(label)}.png"
        destination = self.artifacts_dir / file_name
        if full_page:
            try:
                data = self.web.execute_cdp_cmd(
                    "Page.captureScreenshot",
                    {"format": "png", "captureBeyondViewport": True, "fromSurface": True},
                )
                destination.write_bytes(base64.b64decode(data["data"]))
                return str(destination)
            except Exception:  # noqa: BLE001 - fallback to viewport screenshot
                pass

        self.web.save_screenshot(str(destination))
        return str(destination)

    def step_login(self) -> list[str]:
        details: list[str] = []
        if self.login_url:
            self.web.get(self.login_url)
            details.append(f"Opened login URL: {self.login_url}")
        else:
            current_url = self.web.current_url
            if current_url in {"about:blank", "data:,"}:
                raise RuntimeError(
                    "No login URL available. Provide --login-url or SALEADS_LOGIN_URL to run in any environment."
                )
            details.append(f"Using current browser URL as login page: {current_url}")

        self.wait_for_ui()
        self.app_handle = self.web.current_window_handle

        blocker = self.detect_access_blocker()
        if blocker:
            raise RuntimeError(f"Access blocked before login interaction ({blocker}).")

        try:
            clicked_text = self.click_text(
                [
                    "Sign in with Google",
                    "Iniciar sesion con Google",
                    "Iniciar sesión con Google",
                    "Continuar con Google",
                    "Login with Google",
                ],
                exact_match=False,
                timeout_seconds=20,
            )
        except TimeoutException:
            generic_google = self.find_visible_by_text("Google", timeout_seconds=8, exact_match=False)
            self.click_element_and_wait(generic_google)
            clicked_text = "Google (generic text match)"
        details.append(f'Clicked login entry point: "{clicked_text}"')

        self.try_select_google_account()
        self.wait_until_main_interface_visible()

        if not self.web.find_elements(By.XPATH, "//aside | //nav"):
            if not self.is_text_visible("Negocio", exact_match=False, timeout_seconds=20):
                raise RuntimeError("Main interface loaded, but left sidebar navigation could not be confirmed.")

        screenshot_path = self.screenshot("step-1-dashboard-loaded")
        self.report["Login"].screenshot = screenshot_path
        details.append(f"Dashboard screenshot: {screenshot_path}")
        return details

    def try_select_google_account(self) -> None:
        deadline = time.time() + 45
        while time.time() < deadline:
            handles = self.web.window_handles
            google_handle = None
            for handle in handles:
                self.web.switch_to.window(handle)
                if "accounts.google." in self.web.current_url:
                    google_handle = handle
                    break

            if google_handle is None:
                if self.is_text_visible("Negocio", exact_match=False, timeout_seconds=2):
                    if self.app_handle:
                        self.web.switch_to.window(self.app_handle)
                    return
                time.sleep(0.5)
                continue

            if self.is_text_visible(self.google_account_email, exact_match=False, timeout_seconds=6):
                account_text = self.find_visible_by_text(
                    self.google_account_email,
                    timeout_seconds=6,
                    exact_match=False,
                )
                self.click_element_and_wait(account_text)
            break

        if self.app_handle and self.app_handle in self.web.window_handles:
            self.web.switch_to.window(self.app_handle)
        self.wait_for_ui()

    def wait_until_main_interface_visible(self) -> None:
        self.wait_for_ui()
        marker_texts = ["Mi Negocio", "Negocio", "Dashboard", "Panel", "Inicio"]
        end = time.time() + 90
        while time.time() < end:
            for marker in marker_texts:
                if self.is_text_visible(marker, exact_match=False, timeout_seconds=2):
                    return
            time.sleep(0.5)
        raise TimeoutException("Main SaleADS interface did not appear after Google login.")

    def step_mi_negocio_menu(self) -> list[str]:
        details: list[str] = []
        self.click_text(["Negocio"], exact_match=False, timeout_seconds=15)
        clicked = self.click_text(["Mi Negocio"], exact_match=False, timeout_seconds=15)
        details.append(f'Opened section through "{clicked}"')

        self.find_visible_by_text("Agregar Negocio", timeout_seconds=20, exact_match=False)
        self.find_visible_by_text("Administrar Negocios", timeout_seconds=20, exact_match=False)

        screenshot_path = self.screenshot("step-2-mi-negocio-menu-expanded")
        self.report["Mi Negocio menu"].screenshot = screenshot_path
        details.append(f"Expanded menu screenshot: {screenshot_path}")
        return details

    def step_agregar_negocio_modal(self) -> list[str]:
        details: list[str] = []
        self.click_text(["Agregar Negocio"], exact_match=False, timeout_seconds=20)
        self.find_visible_by_text("Crear Nuevo Negocio", timeout_seconds=20, exact_match=False)
        self.find_visible_by_text("Nombre del Negocio", timeout_seconds=20, exact_match=False)
        self.find_visible_by_text("Tienes 2 de 3 negocios", timeout_seconds=20, exact_match=False)
        self.find_visible_by_text("Cancelar", timeout_seconds=20, exact_match=False)
        self.find_visible_by_text("Crear Negocio", timeout_seconds=20, exact_match=False)
        details.append("Validated modal title, quota text, input field label, and action buttons.")

        screenshot_path = self.screenshot("step-3-agregar-negocio-modal")
        self.report["Agregar Negocio modal"].screenshot = screenshot_path
        details.append(f"Modal screenshot: {screenshot_path}")

        input_candidates = self.web.find_elements(
            By.XPATH,
            (
                "//input[contains(@placeholder, 'Nombre del Negocio')]"
                " | //label[contains(normalize-space(), 'Nombre del Negocio')]/following::input[1]"
            ),
        )
        if input_candidates:
            field = input_candidates[0]
            self.web.execute_script("arguments[0].scrollIntoView({block:'center'});", field)
            field.clear()
            field.send_keys("Negocio Prueba Automatizacion")
            details.append("Typed optional business name in modal input.")

        self.click_text(["Cancelar"], exact_match=False, timeout_seconds=10)
        details.append("Closed modal with Cancelar.")
        return details

    def step_administrar_negocios(self) -> list[str]:
        details: list[str] = []
        if not self.is_text_visible("Administrar Negocios", exact_match=False, timeout_seconds=4):
            self.click_text(["Mi Negocio"], exact_match=False, timeout_seconds=10)
        self.click_text(["Administrar Negocios"], exact_match=False, timeout_seconds=20)

        self.find_any_visible_text(["Información General", "Informacion General"], timeout_seconds=30)
        self.find_visible_by_text("Detalles de la Cuenta", timeout_seconds=30, exact_match=False)
        self.find_visible_by_text("Tus Negocios", timeout_seconds=30, exact_match=False)
        self.find_any_visible_text(["Sección Legal", "Seccion Legal"], timeout_seconds=30)
        details.append("Validated all required account sections are visible.")

        screenshot_path = self.screenshot("step-4-administrar-negocios", full_page=True)
        self.report["Administrar Negocios view"].screenshot = screenshot_path
        details.append(f"Account page screenshot: {screenshot_path}")
        return details

    def step_informacion_general(self) -> list[str]:
        details: list[str] = []
        self.find_visible_by_text("BUSINESS PLAN", timeout_seconds=20, exact_match=False)
        self.find_visible_by_text("Cambiar Plan", timeout_seconds=20, exact_match=False)

        account_email_found = self.is_text_visible(
            self.google_account_email,
            exact_match=False,
            timeout_seconds=8,
        )
        if not account_email_found:
            mail_elements = self.web.find_elements(By.XPATH, "//*[contains(normalize-space(), '@')]")
            account_email_found = any(element.is_displayed() for element in mail_elements)
        if not account_email_found:
            raise RuntimeError("User email is not visible in Informacion General.")

        body_text = self.web.find_element(By.TAG_NAME, "body").text
        non_label_candidates = []
        for raw_line in body_text.splitlines():
            line = raw_line.strip()
            if not line:
                continue
            if EMAIL_REGEX.search(line):
                continue
            upper = line.upper()
            if any(
                token in upper
                for token in [
                    "INFORMACION GENERAL",
                    "DETALLES DE LA CUENTA",
                    "BUSINESS PLAN",
                    "CAMBIAR PLAN",
                    "ESTADO ACTIVO",
                    "IDIOMA SELECCIONADO",
                    "SECCION LEGAL",
                ]
            ):
                continue
            if len(line) >= 5 and re.match(r"^[A-Za-zÀ-ÿ\s'.-]+$", line):
                non_label_candidates.append(line)
            if len(non_label_candidates) >= 1:
                break
        if not non_label_candidates:
            raise RuntimeError("Could not confirm a visible user name in Informacion General.")

        details.append("Validated user name, user email, BUSINESS PLAN, and Cambiar Plan.")
        return details

    def step_detalles_cuenta(self) -> list[str]:
        self.find_visible_by_text("Cuenta creada", timeout_seconds=20, exact_match=False)
        self.find_visible_by_text("Estado activo", timeout_seconds=20, exact_match=False)
        self.find_visible_by_text("Idioma seleccionado", timeout_seconds=20, exact_match=False)
        return ["Validated Cuenta creada, Estado activo, and Idioma seleccionado."]

    def step_tus_negocios(self) -> list[str]:
        details: list[str] = []
        self.find_visible_by_text("Tus Negocios", timeout_seconds=20, exact_match=False)
        self.find_visible_by_text("Agregar Negocio", timeout_seconds=20, exact_match=False)
        self.find_visible_by_text("Tienes 2 de 3 negocios", timeout_seconds=20, exact_match=False)

        section = self.find_visible_by_text("Tus Negocios", timeout_seconds=20, exact_match=False)
        container = section.find_element(By.XPATH, "./ancestor::*[self::section or self::div][1]")
        content_text = container.text.strip()
        line_count = len([line for line in content_text.splitlines() if line.strip()])
        if line_count < 3:
            raise RuntimeError("Business list content was not detected in Tus Negocios section.")

        details.append("Validated business list, Agregar Negocio button, and business quota text.")
        return details

    def step_terminos_condiciones(self) -> list[str]:
        return self.validate_legal_link(
            click_label="Términos y Condiciones",
            expected_heading="Términos y Condiciones",
            report_key="Terminos y Condiciones",
            screenshot_label="step-8-terminos-condiciones",
        )

    def step_politica_privacidad(self) -> list[str]:
        return self.validate_legal_link(
            click_label="Política de Privacidad",
            expected_heading="Política de Privacidad",
            report_key="Politica de Privacidad",
            screenshot_label="step-9-politica-privacidad",
        )

    def validate_legal_link(
        self,
        click_label: str,
        expected_heading: str,
        report_key: str,
        screenshot_label: str,
    ) -> list[str]:
        details: list[str] = []
        original_handle = self.web.current_window_handle
        original_url = self.web.current_url
        handles_before = set(self.web.window_handles)

        self.click_text([click_label], exact_match=False, timeout_seconds=20)
        new_tab_handle = self.wait_for_new_tab(handles_before, timeout_seconds=15)
        if new_tab_handle:
            self.web.switch_to.window(new_tab_handle)
            self.wait_for_ui()
            details.append(f"Opened legal link in new tab for {click_label}.")
        else:
            self.wait_for_ui()
            details.append(f"Opened legal link in current tab for {click_label}.")

        self.find_visible_by_text(expected_heading, timeout_seconds=30, exact_match=False)
        page_text = self.web.find_element(By.TAG_NAME, "body").text.strip()
        if len(page_text) < 120:
            raise RuntimeError(f"Legal content for {expected_heading} appears too short to validate.")

        final_url = self.web.current_url
        screenshot_path = self.screenshot(screenshot_label)
        self.report[report_key].screenshot = screenshot_path
        self.report[report_key].url = final_url
        details.append(f"Screenshot: {screenshot_path}")
        details.append(f"Final URL: {final_url}")

        if new_tab_handle:
            self.web.close()
            self.web.switch_to.window(original_handle)
            self.wait_for_ui()
            details.append("Returned to SaleADS application tab.")
        else:
            self.web.back()
            self.wait_for_ui()
            if self.web.current_url == original_url:
                details.append("Navigated back to SaleADS application in same tab.")
            else:
                if self.app_handle and self.app_handle in self.web.window_handles:
                    self.web.switch_to.window(self.app_handle)
                    self.wait_for_ui()
                    details.append("Recovered SaleADS tab after same-tab legal navigation.")

        return details

    def wait_for_new_tab(self, existing_handles: set[str], timeout_seconds: float) -> Optional[str]:
        end = time.time() + timeout_seconds
        while time.time() < end:
            current = set(self.web.window_handles)
            new_handles = current - existing_handles
            if new_handles:
                return next(iter(new_handles))
            time.sleep(0.2)
        return None

    def write_report_files(self) -> None:
        report_payload = {
            "generated_at_utc": datetime.now(timezone.utc).isoformat(),
            "artifacts_dir": str(self.artifacts_dir),
            "results": {name: state.to_dict() for name, state in self.report.items()},
        }
        report_json_path = self.artifacts_dir / "final_report.json"
        report_json_path.write_text(json.dumps(report_payload, indent=2, ensure_ascii=False), encoding="utf-8")

        markdown_lines = [
            "# SaleADS Mi Negocio Workflow Report",
            "",
            f"- Generated at (UTC): {report_payload['generated_at_utc']}",
            f"- Artifacts directory: `{self.artifacts_dir}`",
            "",
            "| Step | Status | Evidence | URL |",
            "| --- | --- | --- | --- |",
        ]
        for name, state in self.report.items():
            evidence = state.screenshot or "-"
            url = state.url or "-"
            markdown_lines.append(f"| {name} | {state.status} | {evidence} | {url} |")
        markdown_lines.append("")
        markdown_lines.append("## Step details")
        markdown_lines.append("")
        for name, state in self.report.items():
            markdown_lines.append(f"### {name} ({state.status})")
            if state.details:
                for detail in state.details:
                    markdown_lines.append(f"- {detail}")
            else:
                markdown_lines.append("- No additional details recorded.")
            markdown_lines.append("")
        (self.artifacts_dir / "final_report.md").write_text("\n".join(markdown_lines), encoding="utf-8")

    def print_summary(self) -> None:
        print("\nSaleADS Mi Negocio workflow summary:")
        for name, state in self.report.items():
            print(f"- {name}: {state.status}")
            for detail in state.details:
                print(f"  * {detail}")
            if state.url:
                print(f"  * URL: {state.url}")
            if state.screenshot:
                print(f"  * Screenshot: {state.screenshot}")
        print(f"\nArtifacts saved to: {self.artifacts_dir}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate SaleADS Mi Negocio workflow across environments.",
    )
    parser.add_argument(
        "--login-url",
        default=None,
        help="SaleADS login page URL for the target environment. Also supports SALEADS_LOGIN_URL env var.",
    )
    parser.add_argument(
        "--google-account-email",
        default="juanlucasbarbiergarzon@gmail.com",
        help="Google account email to select when account chooser appears.",
    )
    parser.add_argument(
        "--artifacts-root",
        default="target/saleads-mi-negocio",
        help="Directory where screenshots and report files are written.",
    )
    parser.add_argument(
        "--headless",
        action="store_true",
        help="Run Chrome in headless mode.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    login_url = args.login_url
    if not login_url:
        login_url = os.environ.get("SALEADS_LOGIN_URL")

    workflow = SaleadsMiNegocioWorkflow(
        login_url=login_url,
        google_account_email=args.google_account_email,
        artifacts_root=Path(args.artifacts_root),
        headless=args.headless,
    )
    return workflow.run()


if __name__ == "__main__":
    sys.exit(main())
