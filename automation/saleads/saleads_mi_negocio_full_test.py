#!/usr/bin/env python3
"""
End-to-end SaleADS Mi Negocio workflow validation.

This script is environment-agnostic:
- It never hardcodes a SaleADS domain.
- It accepts the login URL via CLI arg or env var.
- It validates by visible text whenever possible.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Pattern, Sequence, Tuple

PLAYWRIGHT_IMPORT_ERROR: Optional[Exception] = None
try:
    from playwright.sync_api import (
        BrowserContext,
        Locator,
        Page,
        TimeoutError as PlaywrightTimeoutError,
        sync_playwright,
    )
except ModuleNotFoundError as exc:
    BrowserContext = object  # type: ignore[assignment]
    Locator = object  # type: ignore[assignment]
    Page = object  # type: ignore[assignment]
    PlaywrightTimeoutError = TimeoutError  # type: ignore[assignment]
    sync_playwright = None  # type: ignore[assignment]
    PLAYWRIGHT_IMPORT_ERROR = exc


GOOGLE_ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com"
TEST_NAME = "saleads_mi_negocio_full_test"
REPORT_FIELDS: List[str] = [
    "Login",
    "Mi Negocio menu",
    "Agregar Negocio modal",
    "Administrar Negocios view",
    "Informaci\u00f3n General",
    "Detalles de la Cuenta",
    "Tus Negocios",
    "T\u00e9rminos y Condiciones",
    "Pol\u00edtica de Privacidad",
]


def rx(pattern: str) -> Pattern[str]:
    return re.compile(pattern, re.IGNORECASE)


@dataclass
class StepResult:
    name: str
    status: str = "FAIL"
    details: str = ""
    screenshots: List[str] = field(default_factory=list)
    final_url: Optional[str] = None


class SaleadsMiNegocioWorkflow:
    def __init__(self, login_url: Optional[str], output_dir: Path, headless: bool, timeout_ms: int) -> None:
        self.login_url = login_url
        self.headless = headless
        self.timeout_ms = timeout_ms
        self.run_timestamp = datetime.now(timezone.utc).strftime("%Y%m%d_%H%M%S")
        self.run_dir = output_dir / self.run_timestamp
        self.run_dir.mkdir(parents=True, exist_ok=True)
        self.report: Dict[str, StepResult] = {name: StepResult(name=name) for name in REPORT_FIELDS}

    def run(self) -> bool:
        app_page: Optional[Page] = None

        if sync_playwright is None:
            self.fail_step(
                "Login",
                "Playwright dependency is missing. Install requirements from automation/saleads/requirements.txt.",
            )
            self.fail_remaining_as_prerequisite("Login")
            self.write_reports()
            return False

        with sync_playwright() as p:
            browser = p.chromium.launch(headless=self.headless, slow_mo=120 if not self.headless else 0)
            context = browser.new_context(viewport={"width": 1440, "height": 900})
            page = context.new_page()

            if not self.login_url:
                self.fail_step(
                    "Login",
                    "Missing login URL. Provide --login-url or SALEADS_LOGIN_URL.",
                )
                self.fail_remaining_as_prerequisite("Login")
                browser.close()
                self.write_reports()
                return False

            try:
                page.goto(self.login_url, wait_until="domcontentloaded", timeout=self.timeout_ms)
                self.wait_for_ui(page)
            except Exception as exc:
                self.fail_step("Login", f"Failed to open login page '{self.login_url}': {exc}")
                self.fail_remaining_as_prerequisite("Login")
                browser.close()
                self.write_reports()
                return False

            app_page = self.step_1_login_with_google(context=context, initial_page=page)
            if not self.passed("Login"):
                self.fail_remaining_as_prerequisite("Login")
                browser.close()
                self.write_reports()
                return False

            assert app_page is not None

            self.step_2_open_mi_negocio_menu(app_page)
            if not self.passed("Mi Negocio menu"):
                self.fail_as_prerequisite(
                    [
                        "Agregar Negocio modal",
                        "Administrar Negocios view",
                        "Informaci\u00f3n General",
                        "Detalles de la Cuenta",
                        "Tus Negocios",
                        "T\u00e9rminos y Condiciones",
                        "Pol\u00edtica de Privacidad",
                    ],
                    "Mi Negocio menu",
                )
                browser.close()
                self.write_reports()
                return False

            self.step_3_agregar_negocio_modal(app_page)
            self.step_4_open_administrar_negocios(app_page)

            if not self.passed("Administrar Negocios view"):
                self.fail_as_prerequisite(
                    [
                        "Informaci\u00f3n General",
                        "Detalles de la Cuenta",
                        "Tus Negocios",
                        "T\u00e9rminos y Condiciones",
                        "Pol\u00edtica de Privacidad",
                    ],
                    "Administrar Negocios view",
                )
                browser.close()
                self.write_reports()
                return False

            self.step_5_validate_informacion_general(app_page)
            self.step_6_validate_detalles_cuenta(app_page)
            self.step_7_validate_tus_negocios(app_page)
            self.step_8_validate_terminos(app_page)
            self.step_9_validate_politica(app_page)

            browser.close()

        self.write_reports()
        return all(step.status == "PASS" for step in self.report.values())

    # --------------------------
    # Step implementations
    # --------------------------

    def step_1_login_with_google(self, context: BrowserContext, initial_page: Page) -> Optional[Page]:
        step_name = "Login"
        try:
            login_button = self.find_visible(
                initial_page,
                [
                    rx(r"sign in with google"),
                    rx(r"(inicia|iniciar)\s+sesi[o\u00f3]n.*google"),
                    rx(r"continuar con google"),
                    rx(r"google"),
                    rx(r"sign in"),
                ],
                timeout_ms=self.timeout_ms,
            )
            auth_page = self.click_and_capture_popup(initial_page, login_button)
            self.wait_for_ui(auth_page)

            account_clicked = self.click_google_account_if_present(context.pages, GOOGLE_ACCOUNT_EMAIL)
            if account_clicked:
                self.wait_for_ui(auth_page)

            app_page = self.wait_for_application_shell(context, timeout_ms=max(self.timeout_ms, 30000))
            if app_page is None:
                if self.auth_blocked_by_google(context.pages):
                    self.fail_step(
                        step_name,
                        (
                            "Google OAuth requires additional credentials/passkey and no authenticated session "
                            f"was available for {GOOGLE_ACCOUNT_EMAIL}."
                        ),
                    )
                else:
                    current_urls = ", ".join(sorted({p.url for p in context.pages if p.url}))
                    self.fail_step(
                        step_name,
                        f"Application shell with left sidebar did not appear. Observed URLs: {current_urls}",
                    )
                return None

            screenshot = self.capture_screenshot(app_page, "step1_dashboard_loaded.png", full_page=True)
            self.pass_step(
                step_name,
                details="Dashboard and left sidebar are visible after Google sign-in.",
                screenshots=[screenshot],
            )
            return app_page
        except Exception as exc:
            self.fail_step(step_name, f"Login with Google failed: {exc}")
            return None

    def step_2_open_mi_negocio_menu(self, page: Page) -> None:
        step_name = "Mi Negocio menu"
        try:
            negocio = self.find_visible(page, [rx(r"Negocio")], timeout_ms=self.timeout_ms)
            self.click_and_wait(page, negocio)

            mi_negocio = self.find_visible(page, [rx(r"Mi Negocio")], timeout_ms=self.timeout_ms)
            self.click_and_wait(page, mi_negocio)

            self.find_visible(page, [rx(r"Agregar Negocio")], timeout_ms=self.timeout_ms)
            self.find_visible(page, [rx(r"Administrar Negocios")], timeout_ms=self.timeout_ms)

            screenshot = self.capture_screenshot(page, "step2_mi_negocio_expanded.png", full_page=True)
            self.pass_step(
                step_name,
                details="Mi Negocio submenu expanded with Agregar Negocio and Administrar Negocios.",
                screenshots=[screenshot],
            )
        except Exception as exc:
            self.fail_step(step_name, f"Unable to open/validate Mi Negocio submenu: {exc}")

    def step_3_agregar_negocio_modal(self, page: Page) -> None:
        step_name = "Agregar Negocio modal"
        screenshots: List[str] = []
        try:
            agregar = self.find_visible(page, [rx(r"Agregar Negocio")], timeout_ms=self.timeout_ms)
            self.click_and_wait(page, agregar)

            self.find_visible(page, [rx(r"Crear Nuevo Negocio")], timeout_ms=self.timeout_ms)
            self.find_input_with_label(page, [rx(r"Nombre del Negocio")], timeout_ms=self.timeout_ms)
            self.find_visible(page, [rx(r"Tienes\s+2\s+de\s+3\s+negocios")], timeout_ms=self.timeout_ms)
            self.find_visible(page, [rx(r"Cancelar")], timeout_ms=self.timeout_ms)
            self.find_visible(page, [rx(r"Crear Negocio")], timeout_ms=self.timeout_ms)

            screenshots.append(self.capture_screenshot(page, "step3_agregar_negocio_modal.png", full_page=True))

            # Optional actions requested by the workflow
            nombre_input = self.find_input_with_label(page, [rx(r"Nombre del Negocio")], timeout_ms=self.timeout_ms)
            nombre_input.click(timeout=self.timeout_ms)
            nombre_input.fill("Negocio Prueba Automatizacion")
            cancel = self.find_visible(page, [rx(r"Cancelar")], timeout_ms=self.timeout_ms)
            self.click_and_wait(page, cancel)

            self.pass_step(
                step_name,
                details="Crear Nuevo Negocio modal validated and closed with Cancelar.",
                screenshots=screenshots,
            )
        except Exception as exc:
            # Best effort cleanup so subsequent steps can proceed.
            self.try_close_modal(page)
            self.fail_step(step_name, f"Agregar Negocio modal validation failed: {exc}", screenshots=screenshots)

    def step_4_open_administrar_negocios(self, page: Page) -> None:
        step_name = "Administrar Negocios view"
        try:
            if not self.is_any_visible(page, [rx(r"Administrar Negocios")], timeout_ms=1500):
                mi_negocio = self.find_visible(page, [rx(r"Mi Negocio")], timeout_ms=self.timeout_ms)
                self.click_and_wait(page, mi_negocio)

            administrar = self.find_visible(page, [rx(r"Administrar Negocios")], timeout_ms=self.timeout_ms)
            self.click_and_wait(page, administrar)

            self.find_visible(page, [rx(r"Informaci[o\u00f3]n General")], timeout_ms=self.timeout_ms)
            self.find_visible(page, [rx(r"Detalles de la Cuenta")], timeout_ms=self.timeout_ms)
            self.find_visible(page, [rx(r"Tus Negocios")], timeout_ms=self.timeout_ms)
            self.find_visible(page, [rx(r"Secci[o\u00f3]n Legal")], timeout_ms=self.timeout_ms)

            screenshot = self.capture_screenshot(page, "step4_administrar_negocios_full.png", full_page=True)
            self.pass_step(
                step_name,
                details="Administrar Negocios page loaded with all required sections visible.",
                screenshots=[screenshot],
            )
        except Exception as exc:
            self.fail_step(step_name, f"Administrar Negocios page validation failed: {exc}")

    def step_5_validate_informacion_general(self, page: Page) -> None:
        step_name = "Informaci\u00f3n General"
        try:
            self.find_visible(page, [rx(r"Informaci[o\u00f3]n General")], timeout_ms=self.timeout_ms)

            name_label_ok = self.is_any_visible(
                page,
                [rx(r"Nombre"), rx(r"Usuario"), rx(r"Name")],
                timeout_ms=3000,
            )
            email_ok = self.is_email_visible(page, timeout_ms=3000)
            self.find_visible(page, [rx(r"BUSINESS PLAN")], timeout_ms=self.timeout_ms)
            self.find_visible(page, [rx(r"Cambiar Plan")], timeout_ms=self.timeout_ms)

            if not name_label_ok:
                raise AssertionError("User name label/value was not visible in Informacion General.")
            if not email_ok:
                raise AssertionError("User email was not visible in Informacion General.")

            self.pass_step(step_name, "Informacion General section fields are visible.")
        except Exception as exc:
            self.fail_step(step_name, f"Informacion General validation failed: {exc}")

    def step_6_validate_detalles_cuenta(self, page: Page) -> None:
        step_name = "Detalles de la Cuenta"
        try:
            self.find_visible(page, [rx(r"Cuenta creada")], timeout_ms=self.timeout_ms)
            self.find_visible(page, [rx(r"Estado activo")], timeout_ms=self.timeout_ms)
            self.find_visible(page, [rx(r"Idioma seleccionado")], timeout_ms=self.timeout_ms)
            self.pass_step(step_name, "Detalles de la Cuenta labels are visible.")
        except Exception as exc:
            self.fail_step(step_name, f"Detalles de la Cuenta validation failed: {exc}")

    def step_7_validate_tus_negocios(self, page: Page) -> None:
        step_name = "Tus Negocios"
        try:
            self.find_visible(page, [rx(r"Tus Negocios")], timeout_ms=self.timeout_ms)
            self.find_visible(page, [rx(r"Agregar Negocio")], timeout_ms=self.timeout_ms)
            self.find_visible(page, [rx(r"Tienes\s+2\s+de\s+3\s+negocios")], timeout_ms=self.timeout_ms)

            has_list = False
            for selector in ["table", "[role='listitem']", "ul li", "[data-testid*='business']"]:
                try:
                    loc = page.locator(selector)
                    if loc.count() > 0 and loc.first.is_visible(timeout=1000):
                        has_list = True
                        break
                except Exception:
                    continue

            if not has_list:
                raise AssertionError("Business list container was not clearly visible.")

            self.pass_step(step_name, "Tus Negocios list and controls are visible.")
        except Exception as exc:
            self.fail_step(step_name, f"Tus Negocios validation failed: {exc}")

    def step_8_validate_terminos(self, page: Page) -> None:
        self.validate_legal_link(
            page=page,
            step_name="T\u00e9rminos y Condiciones",
            link_patterns=[rx(r"T[e\u00e9]rminos y Condiciones"), rx(r"Terminos y Condiciones")],
            heading_patterns=[rx(r"T[e\u00e9]rminos y Condiciones"), rx(r"Terminos y Condiciones")],
            screenshot_name="step8_terminos_condiciones.png",
        )

    def step_9_validate_politica(self, page: Page) -> None:
        self.validate_legal_link(
            page=page,
            step_name="Pol\u00edtica de Privacidad",
            link_patterns=[rx(r"Pol[i\u00ed]tica de Privacidad"), rx(r"Politica de Privacidad")],
            heading_patterns=[rx(r"Pol[i\u00ed]tica de Privacidad"), rx(r"Politica de Privacidad")],
            screenshot_name="step9_politica_privacidad.png",
        )

    # --------------------------
    # Legal links helper
    # --------------------------

    def validate_legal_link(
        self,
        page: Page,
        step_name: str,
        link_patterns: Sequence[Pattern[str]],
        heading_patterns: Sequence[Pattern[str]],
        screenshot_name: str,
    ) -> None:
        try:
            self.find_visible(page, [rx(r"Secci[o\u00f3]n Legal")], timeout_ms=self.timeout_ms)
            legal_link = self.find_visible(page, link_patterns, timeout_ms=self.timeout_ms)
            app_url_before = page.url

            legal_page = self.click_and_capture_popup(page, legal_link)
            self.wait_for_ui(legal_page)

            self.find_visible(legal_page, heading_patterns, timeout_ms=max(self.timeout_ms, 20000))

            body_text = legal_page.locator("body").inner_text(timeout=self.timeout_ms)
            if len(body_text.strip()) < 120:
                raise AssertionError("Legal content text appears too short.")

            screenshot = self.capture_screenshot(legal_page, screenshot_name, full_page=True)
            final_url = legal_page.url
            details = f"Legal page loaded with heading/content. Final URL: {final_url}"

            # Cleanup: return to app tab/page.
            if legal_page is not page:
                legal_page.close()
                self.wait_for_ui(page)
            else:
                try:
                    page.go_back(wait_until="domcontentloaded", timeout=self.timeout_ms)
                    self.wait_for_ui(page)
                except Exception:
                    page.goto(app_url_before, wait_until="domcontentloaded", timeout=self.timeout_ms)
                    self.wait_for_ui(page)

            self.pass_step(step_name, details=details, screenshots=[screenshot], final_url=final_url)
        except Exception as exc:
            self.fail_step(step_name, f"Legal page validation failed: {exc}")

    # --------------------------
    # Generic helpers
    # --------------------------

    def click_google_account_if_present(self, pages: Sequence[Page], email: str) -> bool:
        email_rx = rx(re.escape(email))
        for page in pages:
            try:
                account = self.find_visible(page, [email_rx], timeout_ms=3500)
                self.click_and_wait(page, account)
                return True
            except Exception:
                continue
        return False

    def auth_blocked_by_google(self, pages: Sequence[Page]) -> bool:
        blockers = [
            rx(r"Enter your password"),
            rx(r"Wrong password"),
            rx(r"Try another way"),
            rx(r"Use your passkey"),
            rx(r"accounts\.google\.com"),
            rx(r"Sign in with Google"),
        ]
        for page in pages:
            if re.search(r"accounts\.google\.com", page.url or "", flags=re.IGNORECASE):
                return True
            if self.is_any_visible(page, blockers, timeout_ms=500):
                return True
        return False

    def wait_for_application_shell(self, context: BrowserContext, timeout_ms: int) -> Optional[Page]:
        deadline = time.time() + timeout_ms / 1000.0
        while time.time() < deadline:
            for page in context.pages:
                if re.search(r"accounts\.google\.com|keycloak", page.url or "", flags=re.IGNORECASE):
                    continue

                has_sidebar = False
                for selector in ["aside", "nav", "[class*='sidebar' i]", "[data-testid*='sidebar' i]"]:
                    try:
                        if page.locator(selector).first.is_visible(timeout=400):
                            has_sidebar = True
                            break
                    except Exception:
                        continue

                has_negocio = self.is_any_visible(page, [rx(r"Negocio"), rx(r"Mi Negocio")], timeout_ms=600)
                if has_sidebar and has_negocio:
                    return page
            time.sleep(1.0)
        return None

    def find_visible(self, page: Page, patterns: Sequence[Pattern[str]], timeout_ms: int) -> Locator:
        probe_timeout = max(600, min(1800, timeout_ms))
        attempts = max(2, int(timeout_ms / max(500, probe_timeout)))

        for _ in range(attempts):
            for pattern in patterns:
                candidates = [
                    page.get_by_role("button", name=pattern).first,
                    page.get_by_role("link", name=pattern).first,
                    page.get_by_role("menuitem", name=pattern).first,
                    page.get_by_role("tab", name=pattern).first,
                    page.get_by_role("heading", name=pattern).first,
                    page.get_by_label(pattern).first,
                    page.get_by_text(pattern).first,
                ]
                for locator in candidates:
                    try:
                        locator.wait_for(state="visible", timeout=probe_timeout)
                        return locator
                    except PlaywrightTimeoutError:
                        continue
                    except Exception:
                        continue
            self.wait_for_ui(page, timeout_ms=1200)

        patterns_str = ", ".join(p.pattern for p in patterns)
        raise PlaywrightTimeoutError(f"Unable to find visible element for: {patterns_str}")

    def find_input_with_label(self, page: Page, patterns: Sequence[Pattern[str]], timeout_ms: int) -> Locator:
        probe_timeout = max(700, min(1800, timeout_ms))
        attempts = max(2, int(timeout_ms / max(500, probe_timeout)))
        text_input = page.locator("input, textarea").first

        for _ in range(attempts):
            for pattern in patterns:
                candidates = [
                    page.get_by_label(pattern).first,
                    page.get_by_placeholder(pattern).first,
                    page.locator("input, textarea").filter(has_text=pattern).first,
                    text_input,
                ]
                for locator in candidates:
                    try:
                        locator.wait_for(state="visible", timeout=probe_timeout)
                        return locator
                    except Exception:
                        continue
            self.wait_for_ui(page, timeout_ms=1200)

        patterns_str = ", ".join(p.pattern for p in patterns)
        raise PlaywrightTimeoutError(f"Unable to find visible input for: {patterns_str}")

    def is_any_visible(self, page: Page, patterns: Sequence[Pattern[str]], timeout_ms: int) -> bool:
        try:
            self.find_visible(page, patterns, timeout_ms=timeout_ms)
            return True
        except Exception:
            return False

    def is_email_visible(self, page: Page, timeout_ms: int) -> bool:
        email_pattern = rx(r"[A-Z0-9._%+\-]+@[A-Z0-9.\-]+\.[A-Z]{2,}")
        return self.is_any_visible(page, [email_pattern], timeout_ms=timeout_ms)

    def click_and_wait(self, page: Page, locator: Locator) -> None:
        locator.scroll_into_view_if_needed(timeout=self.timeout_ms)
        locator.click(timeout=self.timeout_ms)
        self.wait_for_ui(page)

    def click_and_capture_popup(self, page: Page, locator: Locator) -> Page:
        try:
            with page.expect_popup(timeout=5000) as popup_info:
                locator.click(timeout=self.timeout_ms)
            popup = popup_info.value
            self.wait_for_ui(popup)
            return popup
        except PlaywrightTimeoutError:
            locator.click(timeout=self.timeout_ms)
            self.wait_for_ui(page)
            return page

    def wait_for_ui(self, page: Page, timeout_ms: Optional[int] = None) -> None:
        timeout = timeout_ms if timeout_ms is not None else self.timeout_ms
        try:
            page.wait_for_load_state("networkidle", timeout=min(timeout, 9000))
        except Exception:
            try:
                page.wait_for_load_state("domcontentloaded", timeout=min(timeout, 9000))
            except Exception:
                pass
        page.wait_for_timeout(800)

    def try_close_modal(self, page: Page) -> None:
        for label in [rx(r"Cancelar"), rx(r"Close"), rx(r"Cerrar")]:
            try:
                locator = self.find_visible(page, [label], timeout_ms=1200)
                locator.click(timeout=1200)
                self.wait_for_ui(page, timeout_ms=1500)
                return
            except Exception:
                continue
        try:
            page.keyboard.press("Escape")
            self.wait_for_ui(page, timeout_ms=1000)
        except Exception:
            pass

    def capture_screenshot(self, page: Page, filename: str, full_page: bool) -> str:
        destination = self.run_dir / filename
        page.screenshot(path=str(destination), full_page=full_page)
        return str(destination.relative_to(self.run_dir.parent))

    # --------------------------
    # Report handling
    # --------------------------

    def passed(self, step_name: str) -> bool:
        return self.report[step_name].status == "PASS"

    def pass_step(
        self,
        step_name: str,
        details: str,
        screenshots: Optional[Iterable[str]] = None,
        final_url: Optional[str] = None,
    ) -> None:
        step = self.report[step_name]
        step.status = "PASS"
        step.details = details
        if screenshots:
            step.screenshots.extend(screenshots)
        if final_url:
            step.final_url = final_url

    def fail_step(
        self,
        step_name: str,
        details: str,
        screenshots: Optional[Iterable[str]] = None,
        final_url: Optional[str] = None,
    ) -> None:
        step = self.report[step_name]
        step.status = "FAIL"
        step.details = details
        if screenshots:
            step.screenshots.extend(screenshots)
        if final_url:
            step.final_url = final_url

    def fail_as_prerequisite(self, step_names: Sequence[str], prerequisite: str) -> None:
        for step_name in step_names:
            if self.report[step_name].status == "PASS":
                continue
            self.fail_step(step_name, f"Prerequisite failed: {prerequisite}.")

    def fail_remaining_as_prerequisite(self, prerequisite: str) -> None:
        remaining = [name for name in REPORT_FIELDS if name != prerequisite and self.report[name].status != "PASS"]
        self.fail_as_prerequisite(remaining, prerequisite=prerequisite)

    def write_reports(self) -> None:
        generated_at = datetime.now(timezone.utc).isoformat()
        json_report = {
            "name": TEST_NAME,
            "generated_at_utc": generated_at,
            "login_url": self.login_url,
            "run_directory": str(self.run_dir),
            "steps": {
                name: {
                    "status": result.status,
                    "details": result.details,
                    "screenshots": result.screenshots,
                    "final_url": result.final_url,
                }
                for name, result in self.report.items()
            },
        }

        report_json_path = self.run_dir / "report.json"
        report_md_path = self.run_dir / "report.md"
        report_json_path.write_text(json.dumps(json_report, indent=2, ensure_ascii=True) + "\n", encoding="utf-8")

        lines = [
            f"# {TEST_NAME}",
            "",
            f"- Generated at (UTC): {generated_at}",
            f"- Login URL: {self.login_url}",
            "",
            "## Final Report",
            "",
            "| Field | Status | Details | Final URL |",
            "|---|---|---|---|",
        ]
        for field_name in REPORT_FIELDS:
            result = self.report[field_name]
            safe_details = result.details.replace("|", "\\|")
            safe_url = (result.final_url or "").replace("|", "\\|")
            lines.append(f"| {field_name} | {result.status} | {safe_details} | {safe_url} |")

        lines.append("")
        lines.append("## Screenshots")
        lines.append("")
        for field_name in REPORT_FIELDS:
            result = self.report[field_name]
            if not result.screenshots:
                continue
            lines.append(f"### {field_name}")
            for shot in result.screenshots:
                lines.append(f"- `{shot}`")
            lines.append("")

        report_md_path.write_text("\n".join(lines).rstrip() + "\n", encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run SaleADS Mi Negocio workflow validation.")
    parser.add_argument(
        "--login-url",
        default=None,
        help="SaleADS login page URL for the current environment (or set SALEADS_LOGIN_URL).",
    )
    parser.add_argument(
        "--output-dir",
        default="artifacts/saleads_mi_negocio",
        help="Directory where screenshots and reports will be generated.",
    )
    parser.add_argument(
        "--headless",
        action="store_true",
        help="Run browser in headless mode.",
    )
    parser.add_argument(
        "--timeout-ms",
        type=int,
        default=15000,
        help="Default timeout (ms) for UI operations.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    login_url = args.login_url or os.environ.get("SALEADS_LOGIN_URL")
    workflow = SaleadsMiNegocioWorkflow(
        login_url=login_url,
        output_dir=Path(args.output_dir).resolve(),
        headless=args.headless,
        timeout_ms=args.timeout_ms,
    )
    ok = workflow.run()

    report_path = workflow.run_dir / "report.md"
    print(f"[{TEST_NAME}] Report: {report_path}")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
