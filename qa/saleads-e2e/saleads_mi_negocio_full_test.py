#!/usr/bin/env python3
"""
SaleADS.ai Mi Negocio workflow validation.

This script automates the end-to-end flow requested in automation task
"saleads_mi_negocio_full_test" using Playwright (Python).
"""

from __future__ import annotations

import argparse
import json
import os
import re
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, List, Optional, Pattern

from playwright.sync_api import Browser, BrowserContext, Page, Playwright, TimeoutError, sync_playwright


EMAIL_TO_SELECT = "juanlucasbarbiergarzon@gmail.com"

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
class ValidationResult:
    description: str
    passed: bool
    details: str = ""


@dataclass
class StepResult:
    name: str
    validations: List[ValidationResult] = field(default_factory=list)
    evidence: List[str] = field(default_factory=list)
    metadata: Dict[str, str] = field(default_factory=dict)

    @property
    def status(self) -> str:
        if not self.validations:
            return "FAIL"
        return "PASS" if all(item.passed for item in self.validations) else "FAIL"


class MiNegocioWorkflowRunner:
    def __init__(self, output_dir: Path, timeout_ms: int) -> None:
        self.output_dir = output_dir
        self.timeout_ms = timeout_ms
        self.screenshots_dir = output_dir / "screenshots"
        self.screenshots_dir.mkdir(parents=True, exist_ok=True)
        self.steps: Dict[str, StepResult] = {name: StepResult(name=name) for name in REPORT_FIELDS}

    def add_validation(self, step_name: str, description: str, passed: bool, details: str = "") -> None:
        self.steps[step_name].validations.append(
            ValidationResult(description=description, passed=passed, details=details)
        )

    def add_evidence(self, step_name: str, file_path: Path) -> None:
        self.steps[step_name].evidence.append(str(file_path))

    def add_metadata(self, step_name: str, key: str, value: str) -> None:
        self.steps[step_name].metadata[key] = value

    @staticmethod
    def wait_ui(page: Page, settle_ms: int = 800) -> None:
        for state in ("domcontentloaded", "load"):
            try:
                page.wait_for_load_state(state=state, timeout=15_000)
            except TimeoutError:
                pass
        try:
            page.wait_for_load_state(state="networkidle", timeout=4_000)
        except TimeoutError:
            pass
        page.wait_for_timeout(settle_ms)

    @staticmethod
    def _locator_visible(locator, timeout_ms: int) -> bool:
        try:
            locator.first.wait_for(state="visible", timeout=timeout_ms)
            return True
        except Exception:
            return False

    def text_visible(self, page: Page, pattern: Pattern[str], timeout_ms: Optional[int] = None) -> bool:
        return self._locator_visible(page.get_by_text(pattern), timeout_ms or self.timeout_ms)

    def click_by_pattern(self, page: Page, pattern: Pattern[str], timeout_ms: int = 3_000) -> bool:
        candidates = [
            page.get_by_role("button", name=pattern),
            page.get_by_role("link", name=pattern),
            page.get_by_role("menuitem", name=pattern),
            page.get_by_text(pattern),
        ]
        for candidate in candidates:
            if self._locator_visible(candidate, timeout_ms):
                candidate.first.click()
                self.wait_ui(page)
                return True
        return False

    def save_screenshot(self, page: Page, filename: str, full_page: bool = False) -> Path:
        path = self.screenshots_dir / filename
        page.screenshot(path=str(path), full_page=full_page)
        return path

    @staticmethod
    def _find_google_popup(context: BrowserContext, current_page: Page) -> Optional[Page]:
        for p in context.pages:
            if p != current_page and "accounts.google.com" in p.url:
                return p
        return None

    def _select_google_account_if_present(self, page: Page) -> bool:
        account_locator = page.get_by_text(re.compile(re.escape(EMAIL_TO_SELECT), re.IGNORECASE))
        if self._locator_visible(account_locator, 6_000):
            account_locator.first.click()
            self.wait_ui(page)
            return True
        return False

    def _is_sidebar_visible(self, page: Page) -> bool:
        nav_candidates = [
            page.locator("aside"),
            page.locator("nav"),
            page.locator("[data-testid*='sidebar']"),
            page.locator("[class*='sidebar']"),
        ]
        if any(self._locator_visible(loc, 3_000) for loc in nav_candidates):
            return True
        # Fallback to common menu text visibility.
        return self.text_visible(page, re.compile(r"Mi\s*Negocio|Negocio", re.IGNORECASE), timeout_ms=4_000)

    def run(self, login_url: Optional[str], headed: bool, slow_mo: int) -> Dict[str, object]:
        started_at = datetime.now(tz=timezone.utc)

        with sync_playwright() as playwright:
            browser = self._launch_browser(playwright, headed, slow_mo)
            context = browser.new_context(viewport={"width": 1440, "height": 960})
            page = context.new_page()
            page.set_default_timeout(self.timeout_ms)

            self._step_login(page, context, login_url)
            self._step_open_mi_negocio_menu(page)
            self._step_validate_agregar_negocio_modal(page)
            self._step_open_administrar_negocios(page)
            self._step_validate_informacion_general(page)
            self._step_validate_detalles_cuenta(page)
            self._step_validate_tus_negocios(page)
            self._step_validate_terminos(page, context)
            self._step_validate_politica(page, context)

            browser.close()

        ended_at = datetime.now(tz=timezone.utc)
        return {
            "started_at": started_at.isoformat(),
            "ended_at": ended_at.isoformat(),
            "overall_status": "PASS" if all(step.status == "PASS" for step in self.steps.values()) else "FAIL",
            "results": {name: step.status for name, step in self.steps.items()},
            "details": {
                name: {
                    "status": step.status,
                    "validations": [asdict(item) for item in step.validations],
                    "evidence": step.evidence,
                    "metadata": step.metadata,
                }
                for name, step in self.steps.items()
            },
        }

    @staticmethod
    def _launch_browser(playwright: Playwright, headed: bool, slow_mo: int) -> Browser:
        return playwright.chromium.launch(headless=not headed, slow_mo=slow_mo)

    def _step_login(self, page: Page, context: BrowserContext, login_url: Optional[str]) -> None:
        step = "Login"
        if login_url:
            page.goto(login_url, wait_until="domcontentloaded")
            self.wait_ui(page)
            self.add_metadata(step, "initial_url", page.url)
        else:
            self.add_validation(
                step,
                "Login URL is provided via --login-url or SALEADS_LOGIN_URL",
                False,
                "Missing login URL. Provide --login-url or SALEADS_LOGIN_URL.",
            )
            return

        sidebar_already_visible = self._is_sidebar_visible(page)
        google_click_ok = True
        account_select_ok = True

        if not sidebar_already_visible:
            google_click_ok = self.click_by_pattern(
                page,
                re.compile(r"Sign in with Google|Iniciar sesi[oó]n con Google|Continuar con Google|Google", re.IGNORECASE),
            )
            # Give any popup/tab a chance to open.
            page.wait_for_timeout(1500)
            popup = self._find_google_popup(context, page)
            if popup is not None:
                popup.bring_to_front()
                self.wait_ui(popup)
                account_select_ok = self._select_google_account_if_present(popup)
                self.wait_ui(popup)
                try:
                    popup.wait_for_event("close", timeout=8_000)
                except TimeoutError:
                    # Some environments may keep popup open until redirect finishes.
                    pass
                page.bring_to_front()
                self.wait_ui(page)
            elif "accounts.google.com" in page.url:
                account_select_ok = self._select_google_account_if_present(page)
                self.wait_ui(page)

        app_interface_visible = self.text_visible(
            page, re.compile(r"Mi\s*Negocio|Administrar\s+Negocios|Dashboard|Inicio", re.IGNORECASE), timeout_ms=30_000
        )
        sidebar_visible = self._is_sidebar_visible(page)

        self.add_validation(step, "Google login action completed", google_click_ok or sidebar_already_visible)
        self.add_validation(step, f"Google account '{EMAIL_TO_SELECT}' selected when shown", account_select_ok)
        self.add_validation(step, "Main application interface appears", app_interface_visible)
        self.add_validation(step, "Left sidebar navigation is visible", sidebar_visible)

        if app_interface_visible:
            screenshot = self.save_screenshot(page, "step1_dashboard_loaded.png")
            self.add_evidence(step, screenshot)

    def _step_open_mi_negocio_menu(self, page: Page) -> None:
        step = "Mi Negocio menu"
        sidebar_visible = self._is_sidebar_visible(page)
        negocio_text_visible = self.text_visible(page, re.compile(r"\bNegocio\b", re.IGNORECASE), timeout_ms=8_000)
        click_ok = self.click_by_pattern(page, re.compile(r"Mi\s*Negocio", re.IGNORECASE))
        agregar_visible = self.text_visible(page, re.compile(r"Agregar\s+Negocio", re.IGNORECASE), timeout_ms=8_000)
        administrar_visible = self.text_visible(page, re.compile(r"Administrar\s+Negocios", re.IGNORECASE), timeout_ms=8_000)

        self.add_validation(step, "Left sidebar navigation is visible", sidebar_visible)
        self.add_validation(step, "Section labeled 'Negocio' is visible", negocio_text_visible)
        self.add_validation(step, "Option 'Mi Negocio' is clickable", click_ok)
        self.add_validation(step, "Submenu displays 'Agregar Negocio'", agregar_visible)
        self.add_validation(step, "Submenu displays 'Administrar Negocios'", administrar_visible)

        if agregar_visible or administrar_visible:
            screenshot = self.save_screenshot(page, "step2_mi_negocio_expanded.png")
            self.add_evidence(step, screenshot)

    def _step_validate_agregar_negocio_modal(self, page: Page) -> None:
        step = "Agregar Negocio modal"
        click_ok = self.click_by_pattern(page, re.compile(r"Agregar\s+Negocio", re.IGNORECASE))
        modal_title_visible = self.text_visible(page, re.compile(r"Crear\s+Nuevo\s+Negocio", re.IGNORECASE), timeout_ms=10_000)

        nombre_input_visible = False
        for locator in [
            page.get_by_label(re.compile(r"Nombre\s+del\s+Negocio", re.IGNORECASE)),
            page.get_by_placeholder(re.compile(r"Nombre\s+del\s+Negocio", re.IGNORECASE)),
            page.get_by_text(re.compile(r"Nombre\s+del\s+Negocio", re.IGNORECASE)),
        ]:
            if self._locator_visible(locator, 3_000):
                nombre_input_visible = True
                break

        limit_text_visible = self.text_visible(page, re.compile(r"Tienes\s+2\s+de\s+3\s+negocios", re.IGNORECASE), timeout_ms=8_000)
        cancelar_visible = self.text_visible(page, re.compile(r"Cancelar", re.IGNORECASE), timeout_ms=8_000)
        crear_visible = self.text_visible(page, re.compile(r"Crear\s+Negocio", re.IGNORECASE), timeout_ms=8_000)

        self.add_validation(step, "Click on 'Agregar Negocio' opens modal", click_ok and modal_title_visible)
        self.add_validation(step, "Modal title 'Crear Nuevo Negocio' is visible", modal_title_visible)
        self.add_validation(step, "Input field 'Nombre del Negocio' exists", nombre_input_visible)
        self.add_validation(step, "Text 'Tienes 2 de 3 negocios' is visible", limit_text_visible)
        self.add_validation(step, "Buttons 'Cancelar' and 'Crear Negocio' are present", cancelar_visible and crear_visible)

        if modal_title_visible:
            screenshot = self.save_screenshot(page, "step3_agregar_negocio_modal.png")
            self.add_evidence(step, screenshot)

            # Optional action requested in task definition.
            nombre_input = page.get_by_label(re.compile(r"Nombre\s+del\s+Negocio", re.IGNORECASE))
            if self._locator_visible(nombre_input, 2_000):
                nombre_input.first.click()
                nombre_input.first.fill("Negocio Prueba Automatización")
                self.wait_ui(page)
            if self.click_by_pattern(page, re.compile(r"Cancelar", re.IGNORECASE)):
                self.wait_ui(page)

    def _step_open_administrar_negocios(self, page: Page) -> None:
        step = "Administrar Negocios view"

        # Re-expand menu in case it collapsed after closing modal.
        if not self.text_visible(page, re.compile(r"Administrar\s+Negocios", re.IGNORECASE), timeout_ms=2_500):
            self.click_by_pattern(page, re.compile(r"Mi\s*Negocio", re.IGNORECASE))

        click_ok = self.click_by_pattern(page, re.compile(r"Administrar\s+Negocios", re.IGNORECASE))
        self.wait_ui(page, settle_ms=1_100)

        info_general = self.text_visible(page, re.compile(r"Informaci[oó]n\s+General", re.IGNORECASE), timeout_ms=12_000)
        detalles = self.text_visible(page, re.compile(r"Detalles\s+de\s+la\s+Cuenta", re.IGNORECASE), timeout_ms=12_000)
        negocios = self.text_visible(page, re.compile(r"Tus\s+Negocios", re.IGNORECASE), timeout_ms=12_000)
        legal = self.text_visible(page, re.compile(r"Secci[oó]n\s+Legal", re.IGNORECASE), timeout_ms=12_000)

        self.add_validation(step, "Navigated to 'Administrar Negocios'", click_ok)
        self.add_validation(step, "Section 'Información General' exists", info_general)
        self.add_validation(step, "Section 'Detalles de la Cuenta' exists", detalles)
        self.add_validation(step, "Section 'Tus Negocios' exists", negocios)
        self.add_validation(step, "Section 'Sección Legal' exists", legal)

        if click_ok:
            screenshot = self.save_screenshot(page, "step4_administrar_negocios_full.png", full_page=True)
            self.add_evidence(step, screenshot)

    def _section_text(self, page: Page, section_title_pattern: Pattern[str]) -> str:
        section = page.locator("section, div, article").filter(has_text=section_title_pattern).first
        if self._locator_visible(section, 3_000):
            return section.inner_text()
        return page.locator("body").inner_text()

    @staticmethod
    def _contains_name_like_text(section_text: str) -> bool:
        blocked = re.compile(
            r"INFORMACI[ÓO]N GENERAL|BUSINESS PLAN|CAMBIAR PLAN|DETALLES|CUENTA CREADA|ESTADO ACTIVO|IDIOMA",
            re.IGNORECASE,
        )
        for raw_line in section_text.splitlines():
            line = " ".join(raw_line.split()).strip()
            if not line or blocked.search(line) or "@" in line:
                continue
            if len(line) >= 5 and len(line.split()) >= 2:
                return True
        return False

    def _step_validate_informacion_general(self, page: Page) -> None:
        step = "Información General"
        section_text = self._section_text(page, re.compile(r"Informaci[oó]n\s+General", re.IGNORECASE))

        email_visible = bool(re.search(r"[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}", section_text, re.IGNORECASE))
        name_visible = self._contains_name_like_text(section_text)
        business_plan_visible = self.text_visible(page, re.compile(r"BUSINESS\s+PLAN", re.IGNORECASE), timeout_ms=8_000)
        cambiar_plan_visible = self.text_visible(page, re.compile(r"Cambiar\s+Plan", re.IGNORECASE), timeout_ms=8_000)

        self.add_validation(step, "User name is visible", name_visible)
        self.add_validation(step, "User email is visible", email_visible)
        self.add_validation(step, "Text 'BUSINESS PLAN' is visible", business_plan_visible)
        self.add_validation(step, "Button 'Cambiar Plan' is visible", cambiar_plan_visible)

    def _step_validate_detalles_cuenta(self, page: Page) -> None:
        step = "Detalles de la Cuenta"
        cuenta_creada = self.text_visible(page, re.compile(r"Cuenta\s+creada", re.IGNORECASE), timeout_ms=8_000)
        estado_activo = self.text_visible(page, re.compile(r"Estado\s+activo", re.IGNORECASE), timeout_ms=8_000)
        idioma = self.text_visible(page, re.compile(r"Idioma\s+seleccionado", re.IGNORECASE), timeout_ms=8_000)

        self.add_validation(step, "'Cuenta creada' is visible", cuenta_creada)
        self.add_validation(step, "'Estado activo' is visible", estado_activo)
        self.add_validation(step, "'Idioma seleccionado' is visible", idioma)

    def _step_validate_tus_negocios(self, page: Page) -> None:
        step = "Tus Negocios"
        section_text = self._section_text(page, re.compile(r"Tus\s+Negocios", re.IGNORECASE))

        section_visible = self.text_visible(page, re.compile(r"Tus\s+Negocios", re.IGNORECASE), timeout_ms=8_000)
        add_button = self.text_visible(page, re.compile(r"Agregar\s+Negocio", re.IGNORECASE), timeout_ms=8_000)
        limit_text = bool(re.search(r"Tienes\s+2\s+de\s+3\s+negocios", section_text, re.IGNORECASE))

        business_list_visible = False
        list_like_count = 0
        for selector in ("li", "[role='listitem']", ".card", "tr"):
            try:
                list_like_count += page.locator(selector).count()
            except Exception:
                pass
        if list_like_count > 0 or len(section_text.strip()) > 40:
            business_list_visible = True

        self.add_validation(step, "Business list is visible", section_visible and business_list_visible)
        self.add_validation(step, "Button 'Agregar Negocio' exists", add_button)
        self.add_validation(step, "Text 'Tienes 2 de 3 negocios' is visible", limit_text)

    def _open_legal_link(
        self,
        page: Page,
        context: BrowserContext,
        link_pattern: Pattern[str],
        heading_pattern: Pattern[str],
        screenshot_name: str,
    ) -> Dict[str, object]:
        original_page = page
        original_url = original_page.url

        # Ensure legal section is visible before clicking.
        self.text_visible(original_page, re.compile(r"Secci[oó]n\s+Legal", re.IGNORECASE), timeout_ms=5_000)

        clicked = False
        link_locator_candidates = [
            original_page.get_by_role("link", name=link_pattern),
            original_page.get_by_role("button", name=link_pattern),
            original_page.get_by_text(link_pattern),
        ]
        selected_locator = None
        for locator in link_locator_candidates:
            if self._locator_visible(locator, 2_500):
                selected_locator = locator.first
                break

        if selected_locator:
            pre_pages = set(context.pages)
            selected_locator.click()
            clicked = True
            self.wait_ui(original_page)
            original_page.wait_for_timeout(1_500)
            post_pages = [p for p in context.pages if p not in pre_pages]
        else:
            post_pages = []

        target_page = post_pages[0] if post_pages else original_page
        new_tab_opened = target_page != original_page
        if new_tab_opened:
            target_page.bring_to_front()
        self.wait_ui(target_page, settle_ms=1_000)

        heading_visible = self.text_visible(target_page, heading_pattern, timeout_ms=10_000)
        body_text = target_page.locator("body").inner_text(timeout=8_000)
        legal_text_visible = len(" ".join(body_text.split())) > 120
        final_url = target_page.url

        screenshot = self.save_screenshot(target_page, screenshot_name, full_page=True)

        # Cleanup: return to application tab.
        if new_tab_opened:
            target_page.close()
            original_page.bring_to_front()
            self.wait_ui(original_page)
        elif target_page.url != original_url:
            try:
                target_page.go_back(wait_until="domcontentloaded")
                self.wait_ui(target_page)
            except Exception:
                pass

        return {
            "clicked": clicked,
            "heading_visible": heading_visible,
            "legal_text_visible": legal_text_visible,
            "final_url": final_url,
            "new_tab_opened": new_tab_opened,
            "screenshot": screenshot,
        }

    def _step_validate_terminos(self, page: Page, context: BrowserContext) -> None:
        step = "Términos y Condiciones"
        result = self._open_legal_link(
            page=page,
            context=context,
            link_pattern=re.compile(r"T[ée]rminos\s+y\s+Condiciones", re.IGNORECASE),
            heading_pattern=re.compile(r"T[ée]rminos\s+y\s+Condiciones", re.IGNORECASE),
            screenshot_name="step8_terminos_y_condiciones.png",
        )
        self.add_validation(step, "Link click was executed", bool(result["clicked"]))
        self.add_validation(step, "Heading 'Términos y Condiciones' is visible", bool(result["heading_visible"]))
        self.add_validation(step, "Legal content text is visible", bool(result["legal_text_visible"]))
        self.add_metadata(step, "final_url", str(result["final_url"]))
        self.add_metadata(step, "opened_new_tab", str(result["new_tab_opened"]))
        self.add_evidence(step, result["screenshot"])  # type: ignore[arg-type]

    def _step_validate_politica(self, page: Page, context: BrowserContext) -> None:
        step = "Política de Privacidad"
        result = self._open_legal_link(
            page=page,
            context=context,
            link_pattern=re.compile(r"Pol[ií]tica\s+de\s+Privacidad", re.IGNORECASE),
            heading_pattern=re.compile(r"Pol[ií]tica\s+de\s+Privacidad", re.IGNORECASE),
            screenshot_name="step9_politica_de_privacidad.png",
        )
        self.add_validation(step, "Link click was executed", bool(result["clicked"]))
        self.add_validation(step, "Heading 'Política de Privacidad' is visible", bool(result["heading_visible"]))
        self.add_validation(step, "Legal content text is visible", bool(result["legal_text_visible"]))
        self.add_metadata(step, "final_url", str(result["final_url"]))
        self.add_metadata(step, "opened_new_tab", str(result["new_tab_opened"]))
        self.add_evidence(step, result["screenshot"])  # type: ignore[arg-type]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run SaleADS Mi Negocio full workflow validation.")
    parser.add_argument(
        "--login-url",
        default=os.getenv("SALEADS_LOGIN_URL") or os.getenv("SALEADS_URL"),
        help="Environment login URL. Must point to the current environment login page.",
    )
    parser.add_argument("--headed", action="store_true", help="Run browser in headed mode.")
    parser.add_argument("--slow-mo", type=int, default=200, help="Delay between Playwright actions (ms).")
    parser.add_argument("--timeout-ms", type=int, default=15_000, help="Default Playwright timeout (ms).")
    parser.add_argument(
        "--output-dir",
        default=f"artifacts/{datetime.now(tz=timezone.utc).strftime('%Y%m%d_%H%M%S')}",
        help="Directory where report and screenshots are stored.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    output_dir = Path(args.output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)

    runner = MiNegocioWorkflowRunner(output_dir=output_dir, timeout_ms=args.timeout_ms)
    report = runner.run(login_url=args.login_url, headed=args.headed, slow_mo=args.slow_mo)

    report_path = output_dir / "final_report.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"Final report saved at: {report_path}")
    print("Step status summary:")
    for field in REPORT_FIELDS:
        status = report["results"][field]  # type: ignore[index]
        print(f"- {field}: {status}")

    return 0 if report["overall_status"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
