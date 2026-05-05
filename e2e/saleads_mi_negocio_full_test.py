#!/usr/bin/env python3
"""
SaleADS Mi Negocio full workflow validation.

How to use:
  1) Open SaleADS login page manually in a Chromium browser started by this script.
  2) Run:
       python3 e2e/saleads_mi_negocio_full_test.py
     Optional env vars:
       SALEADS_HEADLESS=true|false (default: false)
       SALEADS_TIMEOUT_MS=15000 (default: 15000)
       SALEADS_SCREENSHOT_DIR=e2e/artifacts/screenshots
       SALEADS_REPORT_PATH=e2e/artifacts/saleads_mi_negocio_report.json

Notes:
  - This test intentionally does not hardcode any SaleADS URL/domain.
  - It uses visible-text-first selectors wherever possible.
  - It captures screenshots at major checkpoints and produces a PASS/FAIL report.
"""

from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from playwright.sync_api import (
    Browser,
    BrowserContext,
    Error as PlaywrightError,
    Locator,
    Page,
    TimeoutError as PlaywrightTimeoutError,
    expect,
    sync_playwright,
)


def env_bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


def ensure_dir(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)


def slugify(text: str) -> str:
    lowered = text.lower().strip()
    lowered = re.sub(r"[^a-z0-9]+", "-", lowered)
    lowered = re.sub(r"-+", "-", lowered).strip("-")
    return lowered or "checkpoint"


def first_visible(page: Page, selectors: List[str], timeout_ms: int = 5000) -> Locator:
    last_error: Optional[Exception] = None
    for selector in selectors:
        locator = page.locator(selector).first
        try:
            expect(locator).to_be_visible(timeout=timeout_ms)
            return locator
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            continue
    if last_error:
        raise last_error
    raise AssertionError(f"No visible locator found among selectors: {selectors}")


def wait_for_ui_settle(page: Page, timeout_ms: int) -> None:
    # Network idle catches route transitions and fetch bursts without app-specific hooks.
    page.wait_for_load_state("domcontentloaded", timeout=timeout_ms)
    try:
        page.wait_for_load_state("networkidle", timeout=timeout_ms)
    except PlaywrightTimeoutError:
        # Some SPAs keep long polling sockets; domcontentloaded above is enough fallback.
        pass


def click_visible_text(page: Page, text: str, timeout_ms: int) -> None:
    candidates = [
        f"button:has-text('{text}')",
        f"[role='button']:has-text('{text}')",
        f"a:has-text('{text}')",
        f"text={text}",
    ]
    locator = None
    for selector in candidates:
        current = page.locator(selector).first
        try:
            expect(current).to_be_visible(timeout=timeout_ms)
            locator = current
            break
        except PlaywrightTimeoutError:
            continue
    if locator is None:
        raise AssertionError(f"Unable to find visible clickable text: {text}")

    locator.click(timeout=timeout_ms)
    wait_for_ui_settle(page, timeout_ms)


def assert_any_visible(page: Page, selectors: List[str], timeout_ms: int, label: str) -> None:
    errors: List[str] = []
    for selector in selectors:
        locator = page.locator(selector).first
        try:
            expect(locator).to_be_visible(timeout=timeout_ms)
            return
        except Exception as exc:  # noqa: BLE001
            errors.append(f"{selector}: {exc}")
    detail = "; ".join(errors)
    raise AssertionError(f"Validation failed for '{label}'. Tried selectors: {detail}")


@dataclass
class StepResult:
    name: str
    passed: bool
    details: List[str] = field(default_factory=list)
    screenshots: List[str] = field(default_factory=list)
    url: Optional[str] = None


class SaleAdsMiNegocioWorkflow:
    def __init__(self, page: Page, screenshot_dir: Path, timeout_ms: int):
        self.page = page
        self.screenshot_dir = screenshot_dir
        self.timeout_ms = timeout_ms
        self.results: Dict[str, StepResult] = {}

    def screenshot(self, step_name: str, checkpoint: str, full_page: bool = False) -> str:
        filename = f"{slugify(step_name)}__{slugify(checkpoint)}.png"
        path = self.screenshot_dir / filename
        self.page.screenshot(path=str(path), full_page=full_page)
        return str(path)

    def record(self, key: str, result: StepResult) -> None:
        self.results[key] = result

    def run(self) -> Dict[str, StepResult]:
        ordered_steps = [
            self.step_1_login_with_google,
            self.step_2_open_mi_negocio_menu,
            self.step_3_validate_agregar_negocio_modal,
            self.step_4_open_administrar_negocios,
            self.step_5_validate_informacion_general,
            self.step_6_validate_detalles_cuenta,
            self.step_7_validate_tus_negocios,
            self.step_8_validate_terminos,
            self.step_9_validate_politica_privacidad,
        ]

        for step in ordered_steps:
            try:
                step()
            except Exception:  # noqa: BLE001
                # Keep executing and gather a full PASS/FAIL report.
                continue
        return self.results

    def step_1_login_with_google(self) -> None:
        key = "Login"
        step_name = "Login with Google"
        details: List[str] = []
        screenshots: List[str] = []
        try:
            wait_for_ui_settle(self.page, self.timeout_ms)
            click_visible_text(self.page, "Google", self.timeout_ms)
            details.append("Clicked Google sign-in entry point.")

            google_selector = self.page.locator("text=juanlucasbarbiergarzon@gmail.com").first
            try:
                expect(google_selector).to_be_visible(timeout=5000)
                google_selector.click(timeout=self.timeout_ms)
                details.append("Selected Google account juanlucasbarbiergarzon@gmail.com.")
                wait_for_ui_settle(self.page, self.timeout_ms)
            except PlaywrightTimeoutError:
                details.append("Google account picker did not appear; continuing with existing session.")

            assert_any_visible(
                self.page,
                [
                    "aside",
                    "nav",
                    "[role='navigation']",
                    "text=Negocio",
                ],
                self.timeout_ms,
                "main application sidebar",
            )
            details.append("Main interface and left sidebar are visible.")
            screenshots.append(self.screenshot(step_name, "dashboard-loaded", full_page=True))

            self.record(
                key,
                StepResult(name=step_name, passed=True, details=details, screenshots=screenshots, url=self.page.url),
            )
        except Exception as exc:  # noqa: BLE001
            screenshots.append(self.screenshot(step_name, "failure", full_page=True))
            self.record(
                key,
                StepResult(
                    name=step_name,
                    passed=False,
                    details=details + [f"Error: {exc}"],
                    screenshots=screenshots,
                    url=self.page.url,
                ),
            )
            raise

    def step_2_open_mi_negocio_menu(self) -> None:
        key = "Mi Negocio menu"
        step_name = "Open Mi Negocio menu"
        details: List[str] = []
        screenshots: List[str] = []
        try:
            click_visible_text(self.page, "Negocio", self.timeout_ms)
            click_visible_text(self.page, "Mi Negocio", self.timeout_ms)
            details.append("Opened Mi Negocio from left navigation.")

            assert_any_visible(self.page, ["text=Agregar Negocio"], self.timeout_ms, "Agregar Negocio")
            assert_any_visible(self.page, ["text=Administrar Negocios"], self.timeout_ms, "Administrar Negocios")
            details.append("Mi Negocio submenu expanded with required options.")
            screenshots.append(self.screenshot(step_name, "menu-expanded"))

            self.record(
                key,
                StepResult(name=step_name, passed=True, details=details, screenshots=screenshots, url=self.page.url),
            )
        except Exception as exc:  # noqa: BLE001
            screenshots.append(self.screenshot(step_name, "failure", full_page=True))
            self.record(
                key,
                StepResult(
                    name=step_name,
                    passed=False,
                    details=details + [f"Error: {exc}"],
                    screenshots=screenshots,
                    url=self.page.url,
                ),
            )
            raise

    def step_3_validate_agregar_negocio_modal(self) -> None:
        key = "Agregar Negocio modal"
        step_name = "Validate Agregar Negocio modal"
        details: List[str] = []
        screenshots: List[str] = []
        try:
            click_visible_text(self.page, "Agregar Negocio", self.timeout_ms)
            assert_any_visible(self.page, ["text=Crear Nuevo Negocio"], self.timeout_ms, "modal title")
            assert_any_visible(
                self.page,
                [
                    "input[placeholder*='Nombre del Negocio']",
                    "label:has-text('Nombre del Negocio')",
                    "text=Nombre del Negocio",
                ],
                self.timeout_ms,
                "Nombre del Negocio field",
            )
            assert_any_visible(self.page, ["text=Tienes 2 de 3 negocios"], self.timeout_ms, "quota text")
            assert_any_visible(self.page, ["button:has-text('Cancelar')"], self.timeout_ms, "Cancelar button")
            assert_any_visible(self.page, ["button:has-text('Crear Negocio')"], self.timeout_ms, "Crear Negocio button")
            details.append("Agregar Negocio modal fields and actions validated.")
            screenshots.append(self.screenshot(step_name, "modal-visible"))

            # Optional action set requested by user prompt.
            possible_name_input = first_visible(
                self.page,
                [
                    "input[placeholder*='Nombre del Negocio']",
                    "input[aria-label*='Nombre del Negocio']",
                    "input[name*='nombre']",
                ],
            )
            possible_name_input.click(timeout=self.timeout_ms)
            possible_name_input.fill("Negocio Prueba Automatización", timeout=self.timeout_ms)
            details.append("Filled Nombre del Negocio optional sample text.")

            click_visible_text(self.page, "Cancelar", self.timeout_ms)
            details.append("Closed modal using Cancelar.")

            self.record(
                key,
                StepResult(name=step_name, passed=True, details=details, screenshots=screenshots, url=self.page.url),
            )
        except Exception as exc:  # noqa: BLE001
            screenshots.append(self.screenshot(step_name, "failure", full_page=True))
            self.record(
                key,
                StepResult(
                    name=step_name,
                    passed=False,
                    details=details + [f"Error: {exc}"],
                    screenshots=screenshots,
                    url=self.page.url,
                ),
            )
            raise

    def step_4_open_administrar_negocios(self) -> None:
        key = "Administrar Negocios view"
        step_name = "Open Administrar Negocios"
        details: List[str] = []
        screenshots: List[str] = []
        try:
            # Re-expand in case it collapsed.
            try:
                click_visible_text(self.page, "Mi Negocio", self.timeout_ms)
            except Exception:  # noqa: BLE001
                pass
            click_visible_text(self.page, "Administrar Negocios", self.timeout_ms)
            wait_for_ui_settle(self.page, self.timeout_ms)

            assert_any_visible(self.page, ["text=Información General"], self.timeout_ms, "Información General")
            assert_any_visible(self.page, ["text=Detalles de la Cuenta"], self.timeout_ms, "Detalles de la Cuenta")
            assert_any_visible(self.page, ["text=Tus Negocios"], self.timeout_ms, "Tus Negocios")
            assert_any_visible(self.page, ["text=Sección Legal", "text=Legal"], self.timeout_ms, "Sección Legal")
            details.append("Administrar Negocios page sections are visible.")
            screenshots.append(self.screenshot(step_name, "account-page", full_page=True))

            self.record(
                key,
                StepResult(name=step_name, passed=True, details=details, screenshots=screenshots, url=self.page.url),
            )
        except Exception as exc:  # noqa: BLE001
            screenshots.append(self.screenshot(step_name, "failure", full_page=True))
            self.record(
                key,
                StepResult(
                    name=step_name,
                    passed=False,
                    details=details + [f"Error: {exc}"],
                    screenshots=screenshots,
                    url=self.page.url,
                ),
            )
            raise

    def step_5_validate_informacion_general(self) -> None:
        key = "Información General"
        step_name = "Validate Información General"
        details: List[str] = []
        try:
            assert_any_visible(
                self.page,
                [
                    "[data-testid*='user-name']",
                    "text=/@/",
                ],
                self.timeout_ms,
                "user name or profile identity",
            )
            assert_any_visible(
                self.page,
                [
                    "text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/",
                ],
                self.timeout_ms,
                "user email",
            )
            assert_any_visible(self.page, ["text=BUSINESS PLAN"], self.timeout_ms, "BUSINESS PLAN")
            assert_any_visible(self.page, ["button:has-text('Cambiar Plan')"], self.timeout_ms, "Cambiar Plan")
            details.append("Información General values validated.")
            self.record(key, StepResult(name=step_name, passed=True, details=details, url=self.page.url))
        except Exception as exc:  # noqa: BLE001
            self.record(key, StepResult(name=step_name, passed=False, details=details + [f"Error: {exc}"], url=self.page.url))
            raise

    def step_6_validate_detalles_cuenta(self) -> None:
        key = "Detalles de la Cuenta"
        step_name = "Validate Detalles de la Cuenta"
        details: List[str] = []
        try:
            assert_any_visible(self.page, ["text=Cuenta creada"], self.timeout_ms, "Cuenta creada")
            assert_any_visible(self.page, ["text=Estado activo", "text=Activo"], self.timeout_ms, "Estado activo")
            assert_any_visible(
                self.page,
                ["text=Idioma seleccionado", "text=Idioma"],
                self.timeout_ms,
                "Idioma seleccionado",
            )
            details.append("Detalles de la Cuenta values validated.")
            self.record(key, StepResult(name=step_name, passed=True, details=details, url=self.page.url))
        except Exception as exc:  # noqa: BLE001
            self.record(key, StepResult(name=step_name, passed=False, details=details + [f"Error: {exc}"], url=self.page.url))
            raise

    def step_7_validate_tus_negocios(self) -> None:
        key = "Tus Negocios"
        step_name = "Validate Tus Negocios"
        details: List[str] = []
        try:
            assert_any_visible(
                self.page,
                [
                    "text=Tus Negocios",
                    "[data-testid*='business-list']",
                ],
                self.timeout_ms,
                "business list section",
            )
            assert_any_visible(self.page, ["button:has-text('Agregar Negocio')"], self.timeout_ms, "Agregar Negocio")
            assert_any_visible(self.page, ["text=Tienes 2 de 3 negocios"], self.timeout_ms, "quota text")
            details.append("Tus Negocios section validated.")
            self.record(key, StepResult(name=step_name, passed=True, details=details, url=self.page.url))
        except Exception as exc:  # noqa: BLE001
            self.record(key, StepResult(name=step_name, passed=False, details=details + [f"Error: {exc}"], url=self.page.url))
            raise

    def _open_legal_link_and_validate(
        self,
        step_name: str,
        report_key: str,
        link_text: str,
        expected_heading: str,
    ) -> None:
        details: List[str] = []
        screenshots: List[str] = []
        opened_page: Optional[Page] = None
        starting_page = self.page
        starting_url = self.page.url

        try:
            legal_link = first_visible(
                self.page,
                [
                    f"a:has-text('{link_text}')",
                    f"text={link_text}",
                    f"button:has-text('{link_text}')",
                ],
                timeout_ms=self.timeout_ms,
            )

            try:
                with self.page.context.expect_page(timeout=5000) as popup_info:
                    legal_link.click(timeout=self.timeout_ms)
                opened_page = popup_info.value
                opened_page.wait_for_load_state("domcontentloaded", timeout=self.timeout_ms)
                try:
                    opened_page.wait_for_load_state("networkidle", timeout=self.timeout_ms)
                except PlaywrightTimeoutError:
                    pass
                details.append(f"Opened '{link_text}' in a new tab.")
            except PlaywrightTimeoutError:
                wait_for_ui_settle(self.page, self.timeout_ms)
                opened_page = self.page
                details.append(f"Opened '{link_text}' in the current tab.")

            assert_any_visible(opened_page, [f"text={expected_heading}"], self.timeout_ms, expected_heading)
            assert_any_visible(
                opened_page,
                [
                    "main",
                    "article",
                    "text=/\\w{40,}/",
                ],
                self.timeout_ms,
                "legal content",
            )
            screenshots.append(self._screenshot_from_page(opened_page, step_name, "legal-page", full_page=True))
            final_url = opened_page.url
            details.append(f"Captured final legal URL: {final_url}")

            # Return to application tab/page.
            if opened_page is not starting_page:
                opened_page.close()
                starting_page.bring_to_front()
                wait_for_ui_settle(starting_page, self.timeout_ms)
                details.append("Returned to application tab.")
            else:
                starting_page.go_back(timeout=self.timeout_ms)
                wait_for_ui_settle(starting_page, self.timeout_ms)
                details.append("Returned to application page via browser back.")

            self.record(
                report_key,
                StepResult(
                    name=step_name,
                    passed=True,
                    details=details,
                    screenshots=screenshots,
                    url=final_url,
                ),
            )
        except Exception as exc:  # noqa: BLE001
            current_page = opened_page or self.page
            screenshots.append(self._screenshot_from_page(current_page, step_name, "failure", full_page=True))
            self.record(
                report_key,
                StepResult(
                    name=step_name,
                    passed=False,
                    details=details + [f"Error: {exc}", f"Started from: {starting_url}"],
                    screenshots=screenshots,
                    url=current_page.url,
                ),
            )
            raise

    def _screenshot_from_page(self, page: Page, step_name: str, checkpoint: str, full_page: bool = False) -> str:
        filename = f"{slugify(step_name)}__{slugify(checkpoint)}.png"
        path = self.screenshot_dir / filename
        page.screenshot(path=str(path), full_page=full_page)
        return str(path)

    def step_8_validate_terminos(self) -> None:
        self._open_legal_link_and_validate(
            step_name="Validate Términos y Condiciones",
            report_key="Términos y Condiciones",
            link_text="Términos y Condiciones",
            expected_heading="Términos y Condiciones",
        )

    def step_9_validate_politica_privacidad(self) -> None:
        self._open_legal_link_and_validate(
            step_name="Validate Política de Privacidad",
            report_key="Política de Privacidad",
            link_text="Política de Privacidad",
            expected_heading="Política de Privacidad",
        )


def serialize_results(results: Dict[str, StepResult]) -> Dict[str, object]:
    order = [
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
    report = {
        "summary": {},
        "steps": {},
    }
    for key in order:
        result = results.get(key)
        if result is None:
            report["summary"][key] = "FAIL"
            report["steps"][key] = {
                "name": key,
                "passed": False,
                "details": ["Step was not executed due to earlier failure."],
                "screenshots": [],
                "url": None,
            }
            continue
        report["summary"][key] = "PASS" if result.passed else "FAIL"
        report["steps"][key] = {
            "name": result.name,
            "passed": result.passed,
            "details": result.details,
            "screenshots": result.screenshots,
            "url": result.url,
        }
    report["all_passed"] = all(v == "PASS" for v in report["summary"].values())
    return report


def run_workflow() -> int:
    timeout_ms = int(os.getenv("SALEADS_TIMEOUT_MS", "15000"))
    headless = env_bool("SALEADS_HEADLESS", False)
    screenshot_dir = Path(os.getenv("SALEADS_SCREENSHOT_DIR", "e2e/artifacts/screenshots"))
    report_path = Path(os.getenv("SALEADS_REPORT_PATH", "e2e/artifacts/saleads_mi_negocio_report.json"))
    ensure_dir(screenshot_dir)
    ensure_dir(report_path.parent)

    browser: Optional[Browser] = None
    context: Optional[BrowserContext] = None
    page: Optional[Page] = None
    workflow: Optional[SaleAdsMiNegocioWorkflow] = None

    try:
        with sync_playwright() as playwright:
            browser = playwright.chromium.launch(headless=headless)
            context = browser.new_context(viewport={"width": 1440, "height": 900})
            page = context.new_page()
            page.set_default_timeout(timeout_ms)

            # URL intentionally omitted: operator starts from current SaleADS login page.
            # For unattended automation, env var can optionally provide initial URL.
            initial_url = os.getenv("SALEADS_START_URL", "").strip()
            if initial_url:
                page.goto(initial_url, wait_until="domcontentloaded", timeout=timeout_ms)

            workflow = SaleAdsMiNegocioWorkflow(page=page, screenshot_dir=screenshot_dir, timeout_ms=timeout_ms)
            results = workflow.run()
            report = serialize_results(results)

            report_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
            print(json.dumps(report["summary"], ensure_ascii=False))
            return 0 if report["all_passed"] else 1
    except Exception as exc:  # noqa: BLE001
        fallback_results = workflow.results if workflow else {}
        report = serialize_results(fallback_results)
        report["fatal_error"] = str(exc)
        report_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
        print(json.dumps(report.get("summary", {}), ensure_ascii=False))
        print(f"Fatal error: {exc}")
        return 1
    finally:
        try:
            if context:
                context.close()
        except PlaywrightError:
            pass
        try:
            if browser:
                browser.close()
        except PlaywrightError:
            pass


if __name__ == "__main__":
    raise SystemExit(run_workflow())
