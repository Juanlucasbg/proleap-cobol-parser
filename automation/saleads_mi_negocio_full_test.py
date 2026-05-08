#!/usr/bin/env python3
"""SaleADS Mi Negocio full workflow validation.

This script runs an environment-agnostic browser test for the requested
SaleADS.ai flow and exports:
  - checkpoint screenshots
  - a structured PASS/FAIL JSON report

Required env var:
  - SALEADS_LOGIN_URL

Optional env vars:
  - SALEADS_GOOGLE_ACCOUNT (default: juanlucasbarbiergarzon@gmail.com)
  - SALEADS_EXPECTED_USER_NAME
  - SALEADS_HEADLESS (default: true)
  - SALEADS_SLOW_MO_MS (default: 0)
  - SALEADS_TIMEOUT_MS (default: 30000)
"""

from __future__ import annotations

import json
import os
import re
import sys
import traceback
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional, Sequence

from playwright.sync_api import BrowserContext, Locator, Page, TimeoutError, sync_playwright

TEST_NAME = "saleads_mi_negocio_full_test"
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


def utc_timestamp() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def slugify(value: str) -> str:
    text = value.strip().lower()
    text = re.sub(r"[^a-z0-9]+", "-", text)
    text = re.sub(r"-+", "-", text).strip("-")
    return text or "checkpoint"


@dataclass
class StepResult:
    status: str = "FAIL"
    details: str = "Not executed."
    evidence: List[str] = field(default_factory=list)
    final_url: Optional[str] = None


class SaleadsMiNegocioWorkflowTest:
    def __init__(self) -> None:
        self.login_url = os.getenv("SALEADS_LOGIN_URL", "").strip()
        self.google_account = os.getenv("SALEADS_GOOGLE_ACCOUNT", "juanlucasbarbiergarzon@gmail.com").strip()
        self.expected_user_name = os.getenv("SALEADS_EXPECTED_USER_NAME", "").strip()
        self.headless = os.getenv("SALEADS_HEADLESS", "true").strip().lower() not in {"0", "false", "no"}
        self.slow_mo = int(os.getenv("SALEADS_SLOW_MO_MS", "0").strip())
        self.timeout_ms = int(os.getenv("SALEADS_TIMEOUT_MS", "30000").strip())

        self.project_root = Path(__file__).resolve().parents[1]
        self.artifacts_dir = self.project_root / "automation" / "artifacts" / TEST_NAME
        self.reports_dir = self.project_root / "automation" / "reports"
        self.report_path = self.reports_dir / f"{TEST_NAME}_report.json"

        self.artifacts_dir.mkdir(parents=True, exist_ok=True)
        self.reports_dir.mkdir(parents=True, exist_ok=True)

        self.results: Dict[str, StepResult] = {
            field_name: StepResult(details="Pending execution.") for field_name in REPORT_FIELDS
        }

        self._playwright = None
        self.context: Optional[BrowserContext] = None
        self.page: Optional[Page] = None

    def run(self) -> int:
        overall_exception = None
        try:
            self._playwright = sync_playwright().start()
            browser = self._playwright.chromium.launch(headless=self.headless, slow_mo=self.slow_mo)
            self.context = browser.new_context()
            self.page = self.context.new_page()
            self.page.set_default_timeout(self.timeout_ms)

            login_ok = self.step_login_with_google()
            if not login_ok:
                self.mark_prerequisite_failed(
                    [
                        "Mi Negocio menu",
                        "Agregar Negocio modal",
                        "Administrar Negocios view",
                        "Información General",
                        "Detalles de la Cuenta",
                        "Tus Negocios",
                        "Términos y Condiciones",
                        "Política de Privacidad",
                    ],
                    "Login validation failed.",
                )
            else:
                menu_ok = self.step_open_mi_negocio_menu()
                if not menu_ok:
                    self.mark_prerequisite_failed(
                        [
                            "Agregar Negocio modal",
                            "Administrar Negocios view",
                            "Información General",
                            "Detalles de la Cuenta",
                            "Tus Negocios",
                            "Términos y Condiciones",
                            "Política de Privacidad",
                        ],
                        "Mi Negocio menu validation failed.",
                    )
                else:
                    self.step_validate_agregar_negocio_modal()
                    admin_ok = self.step_open_administrar_negocios()
                    if not admin_ok:
                        self.mark_prerequisite_failed(
                            [
                                "Información General",
                                "Detalles de la Cuenta",
                                "Tus Negocios",
                                "Términos y Condiciones",
                                "Política de Privacidad",
                            ],
                            "Administrar Negocios view validation failed.",
                        )
                    else:
                        self.step_validate_informacion_general()
                        self.step_validate_detalles_cuenta()
                        self.step_validate_tus_negocios()
                        self.step_validate_legal_page(
                            field_name="Términos y Condiciones",
                            link_text="Términos y Condiciones",
                            expected_heading="Términos y Condiciones",
                            screenshot_name="terminos_y_condiciones",
                        )
                        self.step_validate_legal_page(
                            field_name="Política de Privacidad",
                            link_text="Política de Privacidad",
                            expected_heading="Política de Privacidad",
                            screenshot_name="politica_de_privacidad",
                        )
        except Exception as exc:  # pylint: disable=broad-except
            overall_exception = exc
            root_msg = f"Unexpected test error: {exc}"
            for field_name in REPORT_FIELDS:
                if self.results[field_name].details == "Pending execution.":
                    self.fail(field_name, root_msg)
        finally:
            if self.context is not None:
                self.context.close()
            if self._playwright is not None:
                self._playwright.stop()
            self.write_report(overall_exception=overall_exception)

        failed_count = sum(1 for result in self.results.values() if result.status == "FAIL")
        return 1 if failed_count else 0

    def step_login_with_google(self) -> bool:
        field_name = "Login"
        try:
            if not self.login_url:
                self.fail(field_name, "SALEADS_LOGIN_URL is required and was not provided.")
                return False

            assert self.page is not None
            self.page.goto(self.login_url, wait_until="domcontentloaded")
            self.wait_for_ui(self.page)

            if self.has_any_visible(
                [
                    self.page.get_by_role("navigation"),
                    self.page.locator("aside"),
                    self.page.get_by_text(re.compile(r"Negocio", re.IGNORECASE)),
                ],
                timeout=8000,
            ):
                shot = self.take_screenshot(self.page, "dashboard_loaded")
                self.pass_step(field_name, "Session already authenticated; dashboard and sidebar are visible.", [shot])
                return True

            login_trigger = self.find_by_text(
                self.page,
                [
                    "Sign in with Google",
                    "Iniciar sesión con Google",
                    "Continuar con Google",
                    "Ingresar con Google",
                    "Google",
                ],
            )

            popup = None
            try:
                with self.context.expect_page(timeout=8000) as popup_info:
                    login_trigger.click()
                popup = popup_info.value
            except TimeoutError:
                login_trigger.click()
            self.wait_for_ui(self.page)

            if popup is not None:
                popup.wait_for_load_state("domcontentloaded")
                self.wait_for_ui(popup)
                self.choose_google_account_if_needed(popup)
                try:
                    popup.wait_for_close(timeout=40000)
                except TimeoutError:
                    popup.close()
            elif "accounts.google.com" in self.page.url:
                self.choose_google_account_if_needed(self.page)

            self.wait_until_sidebar_visible(self.page)

            shot = self.take_screenshot(self.page, "dashboard_loaded")
            self.pass_step(field_name, "Dashboard loaded and sidebar is visible after Google login.", [shot])
            return True
        except Exception as exc:  # pylint: disable=broad-except
            self.fail(field_name, f"Login flow failed: {exc}")
            return False

    def step_open_mi_negocio_menu(self) -> bool:
        field_name = "Mi Negocio menu"
        try:
            assert self.page is not None
            self.wait_until_sidebar_visible(self.page)

            self.click_by_text(self.page, "Negocio", required=False)
            self.click_by_text(self.page, "Mi Negocio", required=True)

            self.assert_visible_text(self.page, "Agregar Negocio")
            self.assert_visible_text(self.page, "Administrar Negocios")

            shot = self.take_screenshot(self.page, "mi_negocio_menu_expanded")
            self.pass_step(
                field_name,
                "Mi Negocio submenu expanded with Agregar Negocio and Administrar Negocios visible.",
                [shot],
            )
            return True
        except Exception as exc:  # pylint: disable=broad-except
            self.fail(field_name, f"Mi Negocio menu validation failed: {exc}")
            return False

    def step_validate_agregar_negocio_modal(self) -> bool:
        field_name = "Agregar Negocio modal"
        try:
            assert self.page is not None
            self.click_by_text(self.page, "Agregar Negocio", required=True)

            self.assert_visible_text(self.page, "Crear Nuevo Negocio")
            self.assert_visible_text(self.page, "Tienes 2 de 3 negocios")
            self.assert_visible_text(self.page, "Cancelar")
            self.assert_visible_text(self.page, "Crear Negocio")

            input_locator = self.first_visible(
                [
                    self.page.get_by_label("Nombre del Negocio"),
                    self.page.get_by_placeholder("Nombre del Negocio"),
                    self.page.locator("input"),
                ],
                timeout=8000,
            )
            input_locator.click()
            input_locator.fill("Negocio Prueba Automatización")

            shot = self.take_screenshot(self.page, "agregar_negocio_modal")
            self.click_by_text(self.page, "Cancelar", required=True)

            self.pass_step(
                field_name,
                "Agregar Negocio modal contains title, field, counter, and action buttons.",
                [shot],
            )
            return True
        except Exception as exc:  # pylint: disable=broad-except
            self.fail(field_name, f"Agregar Negocio modal validation failed: {exc}")
            return False

    def step_open_administrar_negocios(self) -> bool:
        field_name = "Administrar Negocios view"
        try:
            assert self.page is not None
            if not self.has_visible_text(self.page, "Administrar Negocios", timeout=4000):
                self.click_by_text(self.page, "Mi Negocio", required=False)

            self.click_by_text(self.page, "Administrar Negocios", required=True)

            self.assert_visible_text(self.page, "Información General")
            self.assert_visible_text(self.page, "Detalles de la Cuenta")
            self.assert_visible_text(self.page, "Tus Negocios")
            self.assert_visible_text(self.page, "Sección Legal")

            shot = self.take_screenshot(self.page, "administrar_negocios_cuenta", full_page=True)
            self.pass_step(
                field_name,
                "Administrar Negocios view loaded with all expected account sections.",
                [shot],
            )
            return True
        except Exception as exc:  # pylint: disable=broad-except
            self.fail(field_name, f"Administrar Negocios view validation failed: {exc}")
            return False

    def step_validate_informacion_general(self) -> bool:
        field_name = "Información General"
        try:
            assert self.page is not None
            self.assert_visible_text(self.page, "BUSINESS PLAN")
            self.assert_visible_text(self.page, "Cambiar Plan")

            email_visible = self.has_any_visible(
                [self.page.get_by_text(re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}"))],
                timeout=6000,
            )
            if not email_visible:
                raise AssertionError("No visible email was found in account information.")

            body_text = self.page.inner_text("body")
            name_visible = self.expected_name_visible(body_text)
            if not name_visible:
                raise AssertionError("Could not identify a visible user name in Información General.")

            self.pass_step(
                field_name,
                "Información General shows user identity, email, BUSINESS PLAN and Cambiar Plan.",
            )
            return True
        except Exception as exc:  # pylint: disable=broad-except
            self.fail(field_name, f"Información General validation failed: {exc}")
            return False

    def step_validate_detalles_cuenta(self) -> bool:
        field_name = "Detalles de la Cuenta"
        try:
            assert self.page is not None
            self.assert_visible_text(self.page, "Cuenta creada")
            self.assert_visible_text(self.page, "Estado activo")
            self.assert_visible_text(self.page, "Idioma seleccionado")

            self.pass_step(field_name, "Detalles de la Cuenta section displays all required labels.")
            return True
        except Exception as exc:  # pylint: disable=broad-except
            self.fail(field_name, f"Detalles de la Cuenta validation failed: {exc}")
            return False

    def step_validate_tus_negocios(self) -> bool:
        field_name = "Tus Negocios"
        try:
            assert self.page is not None
            self.assert_visible_text(self.page, "Tus Negocios")
            self.assert_visible_text(self.page, "Agregar Negocio")
            self.assert_visible_text(self.page, "Tienes 2 de 3 negocios")

            section_text = self.page.inner_text("body")
            if not self.has_business_list_hint(section_text):
                raise AssertionError("Business list content could not be identified.")

            self.pass_step(field_name, "Tus Negocios section shows list, add button, and usage counter.")
            return True
        except Exception as exc:  # pylint: disable=broad-except
            self.fail(field_name, f"Tus Negocios validation failed: {exc}")
            return False

    def step_validate_legal_page(
        self,
        field_name: str,
        link_text: str,
        expected_heading: str,
        screenshot_name: str,
    ) -> bool:
        try:
            assert self.page is not None
            app_page = self.page
            app_url = app_page.url

            link = self.find_by_text(app_page, [link_text])
            legal_page: Page
            opened_new_tab = False

            try:
                with self.context.expect_page(timeout=8000) as popup_info:
                    link.click()
                legal_page = popup_info.value
                opened_new_tab = True
                legal_page.wait_for_load_state("domcontentloaded")
            except TimeoutError:
                link.click()
                legal_page = app_page
                self.wait_for_ui(legal_page)

            self.wait_for_ui(legal_page)
            self.assert_visible_text(legal_page, expected_heading)

            body_text = legal_page.inner_text("body")
            if len(re.findall(r"\w+", body_text)) < 30:
                raise AssertionError("Legal content seems too short or did not load correctly.")

            shot = self.take_screenshot(legal_page, screenshot_name, full_page=True)
            final_url = legal_page.url

            evidence = [shot, f"final_url:{final_url}"]
            self.pass_step(
                field_name,
                f"{expected_heading} page validated (opened_new_tab={opened_new_tab}).",
                evidence,
                final_url=final_url,
            )

            if opened_new_tab:
                legal_page.close()
                app_page.bring_to_front()
            elif app_page.url != app_url:
                app_page.go_back(wait_until="domcontentloaded")
                self.wait_for_ui(app_page)

            return True
        except Exception as exc:  # pylint: disable=broad-except
            self.fail(field_name, f"{field_name} validation failed: {exc}")
            return False

    def expected_name_visible(self, body_text: str) -> bool:
        if self.expected_user_name:
            return self.expected_user_name.lower() in body_text.lower()

        ignored_terms = {
            "información general",
            "detalles de la cuenta",
            "tus negocios",
            "sección legal",
            "business plan",
            "cambiar plan",
            "cuenta creada",
            "estado activo",
            "idioma seleccionado",
            "agregar negocio",
            "administrar negocios",
            "tienes 2 de 3 negocios",
            "términos y condiciones",
            "política de privacidad",
            "mi negocio",
            "negocio",
        }

        lines = [line.strip() for line in body_text.splitlines() if line.strip()]
        for line in lines:
            normalized = line.lower()
            if normalized in ignored_terms:
                continue
            if "@" in line:
                continue
            if re.fullmatch(r"[A-Za-zÁÉÍÓÚÑáéíóúñ]+(?: [A-Za-zÁÉÍÓÚÑáéíóúñ]+){1,3}", line):
                return True
        return False

    @staticmethod
    def has_business_list_hint(body_text: str) -> bool:
        lines = [line.strip() for line in body_text.splitlines() if line.strip()]
        ignored = {
            "tus negocios",
            "agregar negocio",
            "tienes 2 de 3 negocios",
        }
        for line in lines:
            lowered = line.lower()
            if lowered in ignored:
                continue
            if len(line) >= 3 and "negocio" in lowered:
                return True
        return False

    def choose_google_account_if_needed(self, auth_page: Page) -> None:
        self.wait_for_ui(auth_page)
        if "accounts.google.com" not in auth_page.url and "google" not in auth_page.url.lower():
            return

        try:
            account_option = self.first_visible(
                [
                    auth_page.get_by_text(self.google_account, exact=False),
                    auth_page.get_by_role("button", name=re.compile(re.escape(self.google_account), re.IGNORECASE)),
                    auth_page.get_by_role("link", name=re.compile(re.escape(self.google_account), re.IGNORECASE)),
                ],
                timeout=10000,
            )
            account_option.click()
            self.wait_for_ui(auth_page)
        except Exception:
            # If selector is not displayed, the account may already be selected
            # or Google flow may continue automatically.
            pass

    def wait_until_sidebar_visible(self, page: Page) -> None:
        if not self.has_any_visible(
            [
                page.get_by_role("navigation"),
                page.locator("aside"),
                page.get_by_text(re.compile(r"Negocio", re.IGNORECASE)),
            ],
            timeout=25000,
        ):
            raise AssertionError("Sidebar/navigation was not visible after waiting.")

    def click_by_text(self, page: Page, text: str, required: bool = True) -> bool:
        try:
            locator = self.find_by_text(page, [text], timeout=10000)
            locator.click()
            self.wait_for_ui(page)
            return True
        except Exception:
            if required:
                raise
            return False

    def find_by_text(self, page: Page, text_options: Sequence[str], timeout: int = 10000) -> Locator:
        locators: List[Locator] = []
        for text in text_options:
            regex = re.compile(re.escape(text), re.IGNORECASE)
            locators.extend(
                [
                    page.get_by_role("button", name=regex),
                    page.get_by_role("link", name=regex),
                    page.get_by_role("menuitem", name=regex),
                    page.get_by_role("tab", name=regex),
                    page.get_by_text(regex),
                ]
            )
        return self.first_visible(locators, timeout=timeout)

    def assert_visible_text(self, page: Page, text: str, timeout: int = 12000) -> None:
        if not self.has_visible_text(page, text, timeout=timeout):
            raise AssertionError(f"Expected visible text not found: {text}")

    def has_visible_text(self, page: Page, text: str, timeout: int = 12000) -> bool:
        try:
            self.find_by_text(page, [text], timeout=timeout)
            return True
        except Exception:
            return False

    def first_visible(self, locators: Sequence[Locator], timeout: int = 10000) -> Locator:
        errors: List[str] = []
        for locator in locators:
            candidate = locator.first
            try:
                candidate.wait_for(state="visible", timeout=timeout)
                return candidate
            except Exception as exc:  # pylint: disable=broad-except
                errors.append(str(exc))
        raise AssertionError("No visible locator matched. Last errors: " + " | ".join(errors[-3:]))

    def has_any_visible(self, locators: Sequence[Locator], timeout: int) -> bool:
        try:
            self.first_visible(locators, timeout=timeout)
            return True
        except Exception:
            return False

    def wait_for_ui(self, page: Page) -> None:
        try:
            page.wait_for_load_state("domcontentloaded", timeout=self.timeout_ms)
        except TimeoutError:
            pass
        try:
            page.wait_for_load_state("networkidle", timeout=5000)
        except TimeoutError:
            pass
        page.wait_for_timeout(800)

    def take_screenshot(self, page: Page, name: str, full_page: bool = False) -> str:
        filename = f"{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S')}_{slugify(name)}.png"
        destination = self.artifacts_dir / filename
        page.screenshot(path=str(destination), full_page=full_page)
        return str(destination.relative_to(self.project_root))

    def pass_step(
        self,
        field_name: str,
        details: str,
        evidence: Optional[List[str]] = None,
        final_url: Optional[str] = None,
    ) -> None:
        self.results[field_name].status = "PASS"
        self.results[field_name].details = details
        self.results[field_name].evidence = evidence or []
        self.results[field_name].final_url = final_url

    def fail(self, field_name: str, details: str) -> None:
        self.results[field_name].status = "FAIL"
        self.results[field_name].details = details

    def mark_prerequisite_failed(self, field_names: Sequence[str], reason: str) -> None:
        for field_name in field_names:
            self.fail(field_name, f"Prerequisite failed: {reason}")

    def write_report(self, overall_exception: Optional[Exception]) -> None:
        passed = sum(1 for result in self.results.values() if result.status == "PASS")
        failed = sum(1 for result in self.results.values() if result.status == "FAIL")

        report = {
            "name": TEST_NAME,
            "executed_at": utc_timestamp(),
            "environment": {
                "saleads_login_url": self.login_url or None,
                "google_account": self.google_account,
                "headless": self.headless,
                "timeout_ms": self.timeout_ms,
            },
            "summary": {
                "overall_status": "PASS" if failed == 0 else "FAIL",
                "passed_steps": passed,
                "failed_steps": failed,
                "total_steps": len(REPORT_FIELDS),
            },
            "results": {
                field_name: {
                    "status": result.status,
                    "details": result.details,
                    "evidence": result.evidence,
                    "final_url": result.final_url,
                }
                for field_name, result in self.results.items()
            },
            "fatal_error": str(overall_exception) if overall_exception else None,
            "traceback": traceback.format_exc() if overall_exception else None,
        }

        self.report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

        print("\n=== Final Report ===")
        for field_name in REPORT_FIELDS:
            result = self.results[field_name]
            print(f"- {field_name}: {result.status} | {result.details}")
            if result.final_url:
                print(f"  final_url: {result.final_url}")
        print(f"\nReport path: {self.report_path}")


def main() -> int:
    runner = SaleadsMiNegocioWorkflowTest()
    return runner.run()


if __name__ == "__main__":
    sys.exit(main())
