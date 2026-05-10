#!/usr/bin/env python3
"""SaleADS Mi Negocio end-to-end workflow validation.

This script is environment-agnostic and does not hardcode SaleADS domains.
Use SALEADS_LOGIN_URL to point to the login page of the target environment.
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
from typing import Dict, List, Optional

from playwright.sync_api import (
    BrowserContext,
    Page,
    Playwright,
    TimeoutError as PlaywrightTimeoutError,
    sync_playwright,
)

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


@dataclass
class StepResult:
    status: str = "FAIL"
    details: List[str] = field(default_factory=list)
    evidence: List[str] = field(default_factory=list)
    final_url: Optional[str] = None


class Report:
    def __init__(self, output_dir: Path) -> None:
        self.output_dir = output_dir
        self.started_at = datetime.now(timezone.utc).isoformat()
        self.steps: Dict[str, StepResult] = {
            field: StepResult(details=["Not executed."]) for field in REPORT_FIELDS
        }

    def pass_step(self, field_name: str, detail: str) -> None:
        step = self.steps[field_name]
        step.status = "PASS"
        if step.details == ["Not executed."]:
            step.details = []
        step.details.append(detail)

    def fail_step(self, field_name: str, detail: str) -> None:
        step = self.steps[field_name]
        if step.details == ["Not executed."]:
            step.details = []
        step.details.append(detail)

    def add_evidence(self, field_name: str, evidence_path: Path) -> None:
        self.steps[field_name].evidence.append(str(evidence_path))

    def set_final_url(self, field_name: str, final_url: str) -> None:
        self.steps[field_name].final_url = final_url

    def fail_remaining(self, from_field: str, reason: str) -> None:
        start_index = REPORT_FIELDS.index(from_field)
        for field_name in REPORT_FIELDS[start_index:]:
            self.fail_step(field_name, f"Prerequisite failed: {reason}")

    def write(self) -> Path:
        finished_at = datetime.now(timezone.utc).isoformat()
        overall_status = (
            "PASS" if all(step.status == "PASS" for step in self.steps.values()) else "FAIL"
        )
        payload = {
            "test_name": TEST_NAME,
            "started_at": self.started_at,
            "finished_at": finished_at,
            "overall_status": overall_status,
            "steps": {
                field: {
                    "status": result.status,
                    "details": result.details,
                    "evidence": result.evidence,
                    "final_url": result.final_url,
                }
                for field, result in self.steps.items()
            },
        }
        report_path = self.output_dir / "report.json"
        report_path.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
        return report_path


def utc_slug() -> str:
    return datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")


def make_output_dir() -> Path:
    output_dir = Path("automation") / "artifacts" / utc_slug()
    output_dir.mkdir(parents=True, exist_ok=True)
    return output_dir


def wait_for_ui(page: Page, pause_ms: int = 900) -> None:
    try:
        page.wait_for_load_state("domcontentloaded", timeout=20000)
    except PlaywrightTimeoutError:
        pass
    try:
        page.wait_for_load_state("networkidle", timeout=8000)
    except PlaywrightTimeoutError:
        pass
    page.wait_for_timeout(pause_ms)


def slugify(value: str) -> str:
    sanitized = re.sub(r"[^a-zA-Z0-9]+", "-", value.strip()).strip("-").lower()
    return sanitized or "checkpoint"


def capture(page: Page, output_dir: Path, name: str, full_page: bool = False) -> Path:
    file_name = f"{int(time.time() * 1000)}-{slugify(name)}.png"
    target = output_dir / file_name
    page.screenshot(path=str(target), full_page=full_page)
    return target


def wait_visible_text(page: Page, patterns: List[str], timeout: int = 15000) -> None:
    last_error: Optional[Exception] = None
    for pattern in patterns:
        try:
            page.get_by_text(re.compile(pattern, re.IGNORECASE)).first.wait_for(
                state="visible", timeout=timeout
            )
            return
        except Exception as exc:  # noqa: BLE001
            last_error = exc
    raise AssertionError(f"No visible text matched patterns={patterns}") from last_error


def resolve_clickable(page: Page, patterns: List[str], timeout: int = 12000):
    last_error: Optional[Exception] = None
    for pattern in patterns:
        regex = re.compile(pattern, re.IGNORECASE)
        candidates = [
            page.get_by_role("button", name=regex).first,
            page.get_by_role("link", name=regex).first,
            page.get_by_role("menuitem", name=regex).first,
            page.get_by_role("tab", name=regex).first,
            page.get_by_text(regex).first,
        ]
        for locator in candidates:
            try:
                locator.wait_for(state="visible", timeout=timeout)
                return locator
            except Exception as exc:  # noqa: BLE001
                last_error = exc
    raise AssertionError(f"No clickable element found for patterns={patterns}") from last_error


def click_by_text(page: Page, patterns: List[str], timeout: int = 12000) -> None:
    locator = resolve_clickable(page, patterns, timeout=timeout)
    locator.click()
    wait_for_ui(page)


def detect_new_page(context: BrowserContext, existing: List[Page], timeout_seconds: int = 8) -> Optional[Page]:
    end = time.time() + timeout_seconds
    while time.time() < end:
        for page in context.pages:
            if page not in existing:
                return page
        time.sleep(0.15)
    return None


def sidebar_is_visible(page: Page) -> bool:
    try:
        page.locator("aside").first.wait_for(state="visible", timeout=4000)
        return True
    except PlaywrightTimeoutError:
        pass

    try:
        page.get_by_role("navigation").first.wait_for(state="visible", timeout=4000)
        return True
    except PlaywrightTimeoutError:
        return False


def choose_google_account_if_prompted(login_page: Page) -> None:
    chooser_patterns = [
        r"choose an account",
        r"elige una cuenta",
        r"selecciona una cuenta",
    ]
    if login_page.url.startswith("https://accounts.google.com"):
        wait_for_ui(login_page)

    chooser_visible = False
    for pattern in chooser_patterns:
        if login_page.get_by_text(re.compile(pattern, re.IGNORECASE)).first.is_visible():
            chooser_visible = True
            break

    if chooser_visible or login_page.url.startswith("https://accounts.google.com"):
        click_by_text(login_page, [r"juanlucasbarbiergarzon@gmail\.com"], timeout=10000)


def validate_user_name_visible(page: Page, expected_name: str = "") -> None:
    if expected_name:
        wait_visible_text(page, [re.escape(expected_name)], timeout=15000)
        return

    info_heading = page.get_by_text(re.compile(r"informaci[oó]n general", re.IGNORECASE)).first
    info_heading.wait_for(state="visible", timeout=15000)
    container = info_heading.locator("xpath=ancestor::*[self::section or self::div][1]").first
    section_text = container.inner_text(timeout=5000)
    lines = [line.strip() for line in section_text.splitlines() if line.strip()]

    excluded = {
        "información general",
        "business plan",
        "cambiar plan",
    }
    user_name_pattern = re.compile(
        r"^[A-ZÁÉÍÓÚÜÑ][a-záéíóúüñ]+(?:\s+[A-ZÁÉÍÓÚÜÑ][a-záéíóúüñ]+)+$"
    )

    for line in lines:
        normalized = re.sub(r"\s+", " ", line).strip()
        lowered = normalized.lower()
        if lowered in excluded:
            continue
        if "@" in normalized:
            continue
        if user_name_pattern.match(normalized):
            return

    raise AssertionError(
        "User name was not detected in Información General. "
        "Set SALEADS_EXPECTED_USER_NAME for strict matching in this environment."
    )


def validate_legal_link(
    context: BrowserContext,
    app_page: Page,
    output_dir: Path,
    link_text_patterns: List[str],
    heading_patterns: List[str],
    report: Report,
    report_field: str,
    screenshot_name: str,
) -> None:
    existing_pages = list(context.pages)
    link = resolve_clickable(app_page, link_text_patterns, timeout=12000)
    link.click()
    wait_for_ui(app_page)

    possible_new_page = detect_new_page(context, existing_pages, timeout_seconds=10)
    legal_page = possible_new_page or app_page
    wait_for_ui(legal_page)

    wait_visible_text(legal_page, heading_patterns, timeout=20000)

    legal_content_patterns = [r"t[eé]rmin", r"condiciones", r"privacidad", r"datos"]
    wait_visible_text(legal_page, legal_content_patterns, timeout=20000)

    legal_shot = capture(legal_page, output_dir, screenshot_name, full_page=True)
    report.add_evidence(report_field, legal_shot)
    report.set_final_url(report_field, legal_page.url)

    if possible_new_page:
        legal_page.close()
        app_page.bring_to_front()
        wait_for_ui(app_page)
    else:
        app_page.go_back()
        wait_for_ui(app_page)


def run(playwright: Playwright) -> int:
    output_dir = make_output_dir()
    report = Report(output_dir=output_dir)

    login_url = os.getenv("SALEADS_LOGIN_URL", "").strip()
    expected_user_name = os.getenv("SALEADS_EXPECTED_USER_NAME", "").strip()
    headless = os.getenv("SALEADS_HEADLESS", "true").lower() != "false"
    slow_mo_ms = int(os.getenv("SALEADS_SLOW_MO_MS", "0"))

    if not login_url:
        report.fail_remaining(
            "Login",
            "SALEADS_LOGIN_URL is required to open the environment login page.",
        )
        report_path = report.write()
        print(f"Report generated at: {report_path}")
        print("Result: FAIL (missing SALEADS_LOGIN_URL)")
        return 1

    browser = playwright.chromium.launch(headless=headless, slow_mo=slow_mo_ms)
    context = browser.new_context(viewport={"width": 1440, "height": 900})
    page = context.new_page()
    app_page = page

    try:
        # Step 1: Login with Google
        page.goto(login_url, wait_until="domcontentloaded")
        wait_for_ui(page)
        click_by_text(page, [r"sign in with google", r"iniciar sesi[oó]n con google", r"google"])

        google_page = detect_new_page(context, [page], timeout_seconds=8)
        if google_page:
            wait_for_ui(google_page)
            choose_google_account_if_prompted(google_page)
            try:
                google_page.wait_for_event("close", timeout=30000)
            except PlaywrightTimeoutError:
                pass

        # Confirm dashboard and sidebar
        dashboard_page = None
        for candidate in context.pages:
            try:
                wait_visible_text(candidate, [r"mi negocio", r"negocio", r"dashboard", r"inicio"], timeout=20000)
                if sidebar_is_visible(candidate):
                    dashboard_page = candidate
                    break
            except Exception:  # noqa: BLE001
                continue

        if dashboard_page is None:
            raise AssertionError("Main application interface or sidebar did not appear after Google login.")

        app_page = dashboard_page
        app_page.bring_to_front()
        wait_for_ui(app_page)
        report.pass_step("Login", "Main application interface and left sidebar are visible after login.")
        step1_shot = capture(app_page, output_dir, "dashboard-loaded")
        report.add_evidence("Login", step1_shot)

        # Step 2: Open Mi Negocio menu
        click_by_text(app_page, [r"\bnegocio\b"], timeout=12000)
        click_by_text(app_page, [r"mi negocio"], timeout=12000)
        wait_visible_text(app_page, [r"agregar negocio"], timeout=15000)
        wait_visible_text(app_page, [r"administrar negocios"], timeout=15000)
        report.pass_step("Mi Negocio menu", "Mi Negocio submenu expanded with required options.")
        step2_shot = capture(app_page, output_dir, "mi-negocio-menu-expanded")
        report.add_evidence("Mi Negocio menu", step2_shot)

        # Step 3: Validate Agregar Negocio modal
        click_by_text(app_page, [r"agregar negocio"], timeout=12000)
        wait_visible_text(app_page, [r"crear nuevo negocio"], timeout=15000)
        wait_visible_text(app_page, [r"nombre del negocio"], timeout=15000)
        wait_visible_text(app_page, [r"tienes\s*2\s*de\s*3\s*negocios"], timeout=15000)
        wait_visible_text(app_page, [r"cancelar"], timeout=15000)
        wait_visible_text(app_page, [r"crear negocio"], timeout=15000)

        name_input = app_page.get_by_label(re.compile(r"nombre del negocio", re.IGNORECASE)).first
        try:
            name_input.wait_for(state="visible", timeout=5000)
        except PlaywrightTimeoutError:
            name_input = app_page.get_by_placeholder(re.compile(r"nombre del negocio", re.IGNORECASE)).first
            name_input.wait_for(state="visible", timeout=5000)
        name_input.click()
        name_input.fill("Negocio Prueba Automatización")
        wait_for_ui(app_page, pause_ms=300)
        step3_shot = capture(app_page, output_dir, "agregar-negocio-modal")
        report.add_evidence("Agregar Negocio modal", step3_shot)
        click_by_text(app_page, [r"cancelar"], timeout=12000)
        report.pass_step(
            "Agregar Negocio modal",
            "Modal fields and controls validated; optional input/cancel flow executed.",
        )

        # Step 4: Open Administrar Negocios
        click_by_text(app_page, [r"mi negocio"], timeout=12000)
        click_by_text(app_page, [r"administrar negocios"], timeout=12000)
        wait_visible_text(app_page, [r"informaci[oó]n general"], timeout=20000)
        wait_visible_text(app_page, [r"detalles de la cuenta"], timeout=20000)
        wait_visible_text(app_page, [r"tus negocios"], timeout=20000)
        wait_visible_text(app_page, [r"secci[oó]n legal"], timeout=20000)
        report.pass_step("Administrar Negocios view", "Account page and required sections are visible.")
        step4_shot = capture(app_page, output_dir, "administrar-negocios-page", full_page=True)
        report.add_evidence("Administrar Negocios view", step4_shot)

        # Step 5: Validate Información General
        validate_user_name_visible(app_page, expected_name=expected_user_name)
        wait_visible_text(app_page, [r"business plan"], timeout=15000)
        wait_visible_text(app_page, [r"cambiar plan"], timeout=15000)
        email_regex = re.compile(r"[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}", re.IGNORECASE)
        app_page.get_by_text(email_regex).first.wait_for(state="visible", timeout=15000)
        report.pass_step(
            "Información General",
            "User email, BUSINESS PLAN text and Cambiar Plan button are visible.",
        )

        # Step 6: Validate Detalles de la Cuenta
        wait_visible_text(app_page, [r"cuenta creada"], timeout=15000)
        wait_visible_text(app_page, [r"estado activo"], timeout=15000)
        wait_visible_text(app_page, [r"idioma seleccionado"], timeout=15000)
        report.pass_step("Detalles de la Cuenta", "Detalles de la Cuenta labels are visible.")

        # Step 7: Validate Tus Negocios
        wait_visible_text(app_page, [r"tus negocios"], timeout=15000)
        wait_visible_text(app_page, [r"agregar negocio"], timeout=15000)
        wait_visible_text(app_page, [r"tienes\s*2\s*de\s*3\s*negocios"], timeout=15000)
        report.pass_step("Tus Negocios", "Business list and quota text are visible.")

        # Step 8: Validate Términos y Condiciones
        validate_legal_link(
            context=context,
            app_page=app_page,
            output_dir=output_dir,
            link_text_patterns=[r"t[eé]rminos y condiciones"],
            heading_patterns=[r"t[eé]rminos y condiciones"],
            report=report,
            report_field="Términos y Condiciones",
            screenshot_name="terminos-y-condiciones",
        )
        report.pass_step(
            "Términos y Condiciones",
            "Legal page opened, heading/content validated, screenshot and URL captured.",
        )

        # Step 9: Validate Política de Privacidad
        validate_legal_link(
            context=context,
            app_page=app_page,
            output_dir=output_dir,
            link_text_patterns=[r"pol[ií]tica de privacidad"],
            heading_patterns=[r"pol[ií]tica de privacidad"],
            report=report,
            report_field="Política de Privacidad",
            screenshot_name="politica-de-privacidad",
        )
        report.pass_step(
            "Política de Privacidad",
            "Privacy page opened, heading/content validated, screenshot and URL captured.",
        )

    except Exception as exc:  # noqa: BLE001
        failing_step = next((name for name in REPORT_FIELDS if report.steps[name].status != "PASS"), "Login")
        report.fail_step(failing_step, f"Exception: {exc}")
        try:
            fail_shot = capture(app_page, output_dir, "failure-state", full_page=True)
            report.add_evidence(failing_step, fail_shot)
        except Exception:  # noqa: BLE001
            pass
        next_index = REPORT_FIELDS.index(failing_step) + 1
        if next_index < len(REPORT_FIELDS):
            for field_name in REPORT_FIELDS[next_index:]:
                if report.steps[field_name].status != "PASS":
                    report.fail_step(field_name, f"Prerequisite failed because '{failing_step}' did not pass.")
    finally:
        context.close()
        browser.close()

    report_path = report.write()
    print(f"Report generated at: {report_path}")
    for field_name in REPORT_FIELDS:
        step = report.steps[field_name]
        print(f"- {field_name}: {step.status}")

    return 0 if all(result.status == "PASS" for result in report.steps.values()) else 1


def main() -> int:
    with sync_playwright() as playwright:
        return run(playwright)


if __name__ == "__main__":
    sys.exit(main())
