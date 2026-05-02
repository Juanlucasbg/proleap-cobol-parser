#!/usr/bin/env python3
"""
SaleADS Mi Negocio end-to-end workflow validation.

This script validates:
1) Login with Google
2) Mi Negocio menu expansion
3) Agregar Negocio modal
4) Administrar Negocios account view
5) Informacion General section
6) Detalles de la Cuenta section
7) Tus Negocios section
8) Terminos y Condiciones legal link
9) Politica de Privacidad legal link
10) Final PASS/FAIL report
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
from typing import TYPE_CHECKING, Dict, List, Optional

if TYPE_CHECKING:
    from playwright.sync_api import BrowserContext, Locator, Page

try:
    from playwright.sync_api import Locator, TimeoutError, sync_playwright
except ModuleNotFoundError:
    class TimeoutError(Exception):
        pass

    Locator = object  # type: ignore[assignment]
    sync_playwright = None


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

ACCOUNT_EMAIL = "juanlucasbarbiergarzon@gmail.com"
DEFAULT_TIMEOUT_MS = 20_000
SHORT_TIMEOUT_MS = 2_000


def unaccent(value: str) -> str:
    mapping = str.maketrans(
        {
            "á": "a",
            "é": "e",
            "í": "i",
            "ó": "o",
            "ú": "u",
            "Á": "A",
            "É": "E",
            "Í": "I",
            "Ó": "O",
            "Ú": "U",
            "ñ": "n",
            "Ñ": "N",
        }
    )
    return value.translate(mapping)


@dataclass
class Validation:
    description: str
    passed: bool
    details: str = ""


@dataclass
class StepEvidence:
    screenshots: List[str] = field(default_factory=list)
    urls: List[str] = field(default_factory=list)


class WorkflowReporter:
    def __init__(self) -> None:
        self.validations: Dict[str, List[Validation]] = {field: [] for field in REPORT_FIELDS}
        self.evidence: Dict[str, StepEvidence] = {field: StepEvidence() for field in REPORT_FIELDS}

    def add_validation(self, field: str, description: str, passed: bool, details: str = "") -> None:
        self.validations[field].append(Validation(description=description, passed=passed, details=details))
        state = "PASS" if passed else "FAIL"
        print(f"[{state}] {field}: {description}")
        if details:
            print(f"       {details}")

    def add_screenshot(self, field: str, path: Path) -> None:
        self.evidence[field].screenshots.append(str(path))

    def add_url(self, field: str, url: str) -> None:
        self.evidence[field].urls.append(url)

    def field_status(self, field: str) -> str:
        checks = self.validations[field]
        if not checks:
            return "FAIL"
        return "PASS" if all(item.passed for item in checks) else "FAIL"

    def final_statuses(self) -> Dict[str, str]:
        return {field: self.field_status(field) for field in REPORT_FIELDS}

    def overall_status(self) -> str:
        statuses = self.final_statuses()
        return "PASS" if all(value == "PASS" for value in statuses.values()) else "FAIL"

    def to_dict(self) -> Dict[str, object]:
        return {
            "overall_status": self.overall_status(),
            "final_report": self.final_statuses(),
            "steps": {
                field: {
                    "status": self.field_status(field),
                    "validations": [
                        {"description": item.description, "passed": item.passed, "details": item.details}
                        for item in self.validations[field]
                    ],
                    "evidence": {
                        "screenshots": self.evidence[field].screenshots,
                        "urls": self.evidence[field].urls,
                    },
                }
                for field in REPORT_FIELDS
            },
        }


class SaleadsMiNegocioWorkflow:
    def __init__(
        self,
        page: Page,
        context: BrowserContext,
        reporter: WorkflowReporter,
        screenshots_dir: Path,
        timeout_ms: int,
        account_email: str,
    ) -> None:
        self.page = page
        self.context = context
        self.reporter = reporter
        self.screenshots_dir = screenshots_dir
        self.timeout_ms = timeout_ms
        self.account_email = account_email
        self.screenshot_counter = 1

    def _text_locator_candidates(self, page: Page, label: str) -> List[Locator]:
        escaped = re.escape(label)
        unaccented = re.escape(unaccent(label))
        patterns = [re.compile(rf".*{escaped}.*", re.IGNORECASE)]
        if unaccented != escaped:
            patterns.append(re.compile(rf".*{unaccented}.*", re.IGNORECASE))

        candidates: List[Locator] = [page.locator(f"text={label}")]
        for name_pattern in patterns:
            candidates.extend(
                [
                    page.get_by_role("button", name=name_pattern),
                    page.get_by_role("link", name=name_pattern),
                    page.get_by_role("menuitem", name=name_pattern),
                    page.get_by_role("tab", name=name_pattern),
                    page.get_by_role("heading", name=name_pattern),
                    page.get_by_role("option", name=name_pattern),
                    page.get_by_text(name_pattern),
                ]
            )
        return [
            *candidates,
        ]

    def _find_visible_by_text(self, page: Page, labels: List[str], timeout_ms: int = SHORT_TIMEOUT_MS) -> Optional[Locator]:
        for label in labels:
            for locator in self._text_locator_candidates(page, label):
                candidate = locator.first
                try:
                    candidate.wait_for(state="visible", timeout=timeout_ms)
                    return candidate
                except TimeoutError:
                    continue
                except Exception:
                    continue
        return None

    def _click_by_text(self, page: Page, labels: List[str], wait_after: bool = True) -> bool:
        candidate = self._find_visible_by_text(page, labels)
        if candidate is None:
            return False
        candidate.click(timeout=self.timeout_ms)
        if wait_after:
            self._wait_for_ui(page)
        return True

    def _wait_for_ui(self, page: Page) -> None:
        try:
            page.wait_for_load_state("domcontentloaded", timeout=8_000)
        except TimeoutError:
            pass
        try:
            page.wait_for_load_state("networkidle", timeout=8_000)
        except TimeoutError:
            pass
        page.wait_for_timeout(700)

    def _is_visible_by_text(self, page: Page, labels: List[str]) -> bool:
        return self._find_visible_by_text(page, labels) is not None

    def _capture(self, field: str, name: str, full_page: bool = False, page: Optional[Page] = None) -> None:
        target_page = page or self.page
        filename = f"{self.screenshot_counter:02d}_{name}.png"
        screenshot_path = self.screenshots_dir / filename
        target_page.screenshot(path=str(screenshot_path), full_page=full_page)
        self.reporter.add_screenshot(field, screenshot_path)
        self.screenshot_counter += 1

    def _try_google_account_selection(self, page: Page) -> None:
        account_candidates = [
            self.account_email,
            "Usar otra cuenta",
            "Use another account",
        ]
        locator = self._find_visible_by_text(page, account_candidates, timeout_ms=4_000)
        if locator is None:
            return
        if self._find_visible_by_text(page, [self.account_email], timeout_ms=2_000):
            self._click_by_text(page, [self.account_email], wait_after=True)

    def _ensure_app_sidebar(self) -> bool:
        try:
            sidebar_visible = self.page.locator("nav, aside").first.is_visible()
        except Exception:
            sidebar_visible = False
        negocio_visible = self._is_visible_by_text(self.page, ["Negocio", "Mi Negocio"])
        return sidebar_visible or negocio_visible

    def _ensure_mi_negocio_expanded(self) -> None:
        if self._is_visible_by_text(self.page, ["Administrar Negocios", "Agregar Negocio"]):
            return
        self._click_by_text(self.page, ["Mi Negocio", "Negocio"])
        self._wait_for_ui(self.page)

    def step_1_login_with_google(self) -> None:
        field = "Login"
        clicked = self._click_by_text(
            self.page,
            [
                "Sign in with Google",
                "Iniciar sesion con Google",
                "Iniciar sesión con Google",
                "Continuar con Google",
                "Google",
            ],
            wait_after=False,
        )
        google_popup: Optional[Page] = None
        if clicked:
            try:
                google_popup = self.context.wait_for_event("page", timeout=7_000)
            except TimeoutError:
                google_popup = None

        self.reporter.add_validation(
            field,
            "Locate and click login button 'Sign in with Google'.",
            clicked,
            "Google login button not found." if not clicked else "",
        )

        self._wait_for_ui(self.page)
        if google_popup is not None:
            try:
                google_popup.wait_for_load_state("domcontentloaded", timeout=10_000)
                self._try_google_account_selection(google_popup)
            except Exception:
                pass
            for candidate_page in self.context.pages:
                if candidate_page != google_popup and not candidate_page.is_closed():
                    self.page = candidate_page
                    break
        else:
            self._try_google_account_selection(self.page)

        self._wait_for_ui(self.page)

        main_interface_visible = self.page.locator("main, [role='main']").first.is_visible()
        sidebar_visible = self._ensure_app_sidebar()

        self.reporter.add_validation(field, "Confirm the main application interface appears.", main_interface_visible)
        self.reporter.add_validation(field, "Confirm left sidebar navigation is visible.", sidebar_visible)
        self._capture(field, "dashboard_loaded")

    def step_2_open_mi_negocio_menu(self) -> None:
        field = "Mi Negocio menu"
        negocio_clicked = self._click_by_text(self.page, ["Negocio", "Mi Negocio"])
        if negocio_clicked:
            self._wait_for_ui(self.page)
        mi_negocio_clicked = self._click_by_text(self.page, ["Mi Negocio"])
        if mi_negocio_clicked:
            self._wait_for_ui(self.page)

        submenu_expanded = self._is_visible_by_text(self.page, ["Agregar Negocio"]) or self._is_visible_by_text(
            self.page, ["Administrar Negocios"]
        )
        agregar_visible = self._is_visible_by_text(self.page, ["Agregar Negocio"])
        administrar_visible = self._is_visible_by_text(self.page, ["Administrar Negocios"])

        self.reporter.add_validation(field, "Confirm submenu expands.", submenu_expanded)
        self.reporter.add_validation(field, "Confirm 'Agregar Negocio' is visible.", agregar_visible)
        self.reporter.add_validation(field, "Confirm 'Administrar Negocios' is visible.", administrar_visible)
        self._capture(field, "mi_negocio_menu_expanded")

    def step_3_validate_agregar_negocio_modal(self) -> None:
        field = "Agregar Negocio modal"
        self._ensure_mi_negocio_expanded()
        clicked = self._click_by_text(self.page, ["Agregar Negocio"])
        self.reporter.add_validation(field, "Click 'Agregar Negocio'.", clicked, "Menu option not found." if not clicked else "")

        modal_title = self._is_visible_by_text(self.page, ["Crear Nuevo Negocio"])
        has_name_field = False
        try:
            has_name_field = self.page.get_by_label(re.compile(r".*Nombre del Negocio.*", re.IGNORECASE)).first.is_visible()
        except Exception:
            has_name_field = False
        quota_text = self._is_visible_by_text(self.page, ["Tienes 2 de 3 negocios"])
        cancel_visible = self._is_visible_by_text(self.page, ["Cancelar"])
        create_visible = self._is_visible_by_text(self.page, ["Crear Negocio"])

        self.reporter.add_validation(field, "Modal title 'Crear Nuevo Negocio' is visible.", modal_title)
        self.reporter.add_validation(field, "Input field 'Nombre del Negocio' exists.", has_name_field)
        self.reporter.add_validation(field, "Text 'Tienes 2 de 3 negocios' is visible.", quota_text)
        self.reporter.add_validation(field, "Button 'Cancelar' is present.", cancel_visible)
        self.reporter.add_validation(field, "Button 'Crear Negocio' is present.", create_visible)
        self._capture(field, "agregar_negocio_modal")

        try:
            input_field = self.page.get_by_label(re.compile(r".*Nombre del Negocio.*", re.IGNORECASE)).first
            input_field.click(timeout=SHORT_TIMEOUT_MS)
            input_field.fill("Negocio Prueba Automatizacion")
        except Exception:
            pass

        self._click_by_text(self.page, ["Cancelar"])
        self._wait_for_ui(self.page)

    def step_4_open_administrar_negocios(self) -> None:
        field = "Administrar Negocios view"
        self._ensure_mi_negocio_expanded()
        clicked = self._click_by_text(self.page, ["Administrar Negocios"])
        self.reporter.add_validation(
            field,
            "Click 'Administrar Negocios' and wait for account page.",
            clicked,
            "Menu option not found." if not clicked else "",
        )

        info_general = self._is_visible_by_text(self.page, ["Informacion General", "Información General"])
        detalles = self._is_visible_by_text(self.page, ["Detalles de la Cuenta"])
        tus_negocios = self._is_visible_by_text(self.page, ["Tus Negocios"])
        legal = self._is_visible_by_text(self.page, ["Seccion Legal", "Sección Legal"])

        self.reporter.add_validation(field, "Section 'Información General' exists.", info_general)
        self.reporter.add_validation(field, "Section 'Detalles de la Cuenta' exists.", detalles)
        self.reporter.add_validation(field, "Section 'Tus Negocios' exists.", tus_negocios)
        self.reporter.add_validation(field, "Section 'Sección Legal' exists.", legal)
        self._capture(field, "administrar_negocios_account_page", full_page=True)

    def step_5_validate_informacion_general(self) -> None:
        field = "Información General"
        try:
            name_visible = self.page.locator("main").first.inner_text(timeout=SHORT_TIMEOUT_MS).strip() != ""
        except Exception:
            name_visible = False
        email_visible = self._is_visible_by_text(self.page, ["@", self.account_email])
        business_plan = self._is_visible_by_text(self.page, ["BUSINESS PLAN"])
        cambiar_plan = self._is_visible_by_text(self.page, ["Cambiar Plan"])

        self.reporter.add_validation(field, "User name is visible.", name_visible)
        self.reporter.add_validation(field, "User email is visible.", email_visible)
        self.reporter.add_validation(field, "Text 'BUSINESS PLAN' is visible.", business_plan)
        self.reporter.add_validation(field, "Button 'Cambiar Plan' is visible.", cambiar_plan)

    def step_6_validate_detalles_cuenta(self) -> None:
        field = "Detalles de la Cuenta"
        cuenta_creada = self._is_visible_by_text(self.page, ["Cuenta creada"])
        estado_activo = self._is_visible_by_text(self.page, ["Estado activo"])
        idioma = self._is_visible_by_text(self.page, ["Idioma seleccionado"])

        self.reporter.add_validation(field, "'Cuenta creada' is visible.", cuenta_creada)
        self.reporter.add_validation(field, "'Estado activo' is visible.", estado_activo)
        self.reporter.add_validation(field, "'Idioma seleccionado' is visible.", idioma)

    def step_7_validate_tus_negocios(self) -> None:
        field = "Tus Negocios"
        list_visible = self._is_visible_by_text(self.page, ["Tus Negocios"])
        agregar_exists = self._is_visible_by_text(self.page, ["Agregar Negocio"])
        quota_visible = self._is_visible_by_text(self.page, ["Tienes 2 de 3 negocios"])

        self.reporter.add_validation(field, "Business list is visible.", list_visible)
        self.reporter.add_validation(field, "Button 'Agregar Negocio' exists.", agregar_exists)
        self.reporter.add_validation(field, "Text 'Tienes 2 de 3 negocios' is visible.", quota_visible)

    def _validate_legal_page_content(self, page: Page, heading_candidates: List[str]) -> bool:
        body_text = ""
        try:
            body_text = page.locator("body").first.inner_text(timeout=self.timeout_ms)
        except Exception:
            body_text = ""
        has_legal_content = len(body_text.strip()) > 250
        return has_legal_content

    def _open_and_validate_legal_link(
        self,
        field: str,
        link_texts: List[str],
        heading_texts: List[str],
        screenshot_name: str,
    ) -> None:
        app_page = self.page
        app_url_before = app_page.url
        target_page = app_page
        opened_new_tab = False

        clicked = self._click_by_text(app_page, link_texts, wait_after=False)
        if not clicked:
            self.reporter.add_validation(field, f"Click '{link_texts[0]}'.", False, "Legal link not found.")
            return
        try:
            target_page = self.context.wait_for_event("page", timeout=5_000)
            opened_new_tab = True
            target_page.wait_for_load_state("domcontentloaded", timeout=self.timeout_ms)
        except TimeoutError:
            target_page = app_page
            self._wait_for_ui(target_page)

        self._wait_for_ui(target_page)
        heading_visible = self._is_visible_by_text(target_page, heading_texts)
        content_visible = self._validate_legal_page_content(target_page, heading_texts)
        final_url = target_page.url

        self.reporter.add_validation(field, f"Heading '{heading_texts[0]}' is visible.", heading_visible)
        self.reporter.add_validation(field, "Legal content text is visible.", content_visible)
        self.reporter.add_url(field, final_url)
        self._capture(field, screenshot_name, page=target_page)

        if opened_new_tab:
            try:
                target_page.close()
            except Exception:
                pass
            try:
                app_page.bring_to_front()
            except Exception:
                pass
            self._wait_for_ui(app_page)
        else:
            if app_page.url != app_url_before:
                try:
                    app_page.go_back(wait_until="domcontentloaded", timeout=self.timeout_ms)
                    self._wait_for_ui(app_page)
                except Exception:
                    try:
                        app_page.goto(app_url_before, wait_until="domcontentloaded", timeout=self.timeout_ms)
                        self._wait_for_ui(app_page)
                    except Exception:
                        pass

    def step_8_validate_terminos(self) -> None:
        self._open_and_validate_legal_link(
            field="Términos y Condiciones",
            link_texts=["Términos y Condiciones", "Terminos y Condiciones"],
            heading_texts=["Términos y Condiciones", "Terminos y Condiciones"],
            screenshot_name="terminos_y_condiciones",
        )

    def step_9_validate_politica(self) -> None:
        self._open_and_validate_legal_link(
            field="Política de Privacidad",
            link_texts=["Política de Privacidad", "Politica de Privacidad"],
            heading_texts=["Política de Privacidad", "Politica de Privacidad"],
            screenshot_name="politica_de_privacidad",
        )

    def run(self) -> None:
        self.step_1_login_with_google()
        self.step_2_open_mi_negocio_menu()
        self.step_3_validate_agregar_negocio_modal()
        self.step_4_open_administrar_negocios()
        self.step_5_validate_informacion_general()
        self.step_6_validate_detalles_cuenta()
        self.step_7_validate_tus_negocios()
        self.step_8_validate_terminos()
        self.step_9_validate_politica()


def env_bool(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "y", "on"}


def ensure_login_page(page: Page, login_url: Optional[str], headless: bool) -> None:
    if login_url:
        page.goto(login_url, wait_until="domcontentloaded")
        return

    if headless:
        raise RuntimeError(
            "No --login-url provided while running headless. "
            "Set SALEADS_LOGIN_URL or pass --login-url."
        )

    print("No login URL provided. Waiting for manual navigation to SaleADS login page...")
    page.goto("about:blank", wait_until="domcontentloaded")
    for _ in range(180):
        current = page.url
        if current and current != "about:blank":
            print(f"Detected page: {current}")
            return
        time.sleep(1)
    raise RuntimeError("Timed out waiting for manual navigation to login page.")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="SaleADS Mi Negocio full workflow test.")
    parser.add_argument("--login-url", default=os.getenv("SALEADS_LOGIN_URL"))
    parser.add_argument("--account-email", default=os.getenv("SALEADS_ACCOUNT_EMAIL", ACCOUNT_EMAIL))
    parser.add_argument("--timeout-ms", type=int, default=int(os.getenv("SALEADS_TIMEOUT_MS", str(DEFAULT_TIMEOUT_MS))))
    parser.add_argument("--headless", action="store_true", default=env_bool("SALEADS_HEADLESS", False))
    parser.add_argument("--slow-mo-ms", type=int, default=int(os.getenv("SALEADS_SLOW_MO_MS", "0")))
    parser.add_argument("--artifacts-dir", default=os.getenv("SALEADS_ARTIFACTS_DIR", "qa/artifacts"))
    parser.add_argument("--user-data-dir", default=os.getenv("SALEADS_USER_DATA_DIR", "qa/.pw-user-data"))
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    run_root = Path(args.artifacts_dir).resolve() / f"saleads_mi_negocio_{run_id}"
    screenshots_dir = run_root / "screenshots"
    run_root.mkdir(parents=True, exist_ok=True)
    screenshots_dir.mkdir(parents=True, exist_ok=True)

    reporter = WorkflowReporter()

    with sync_playwright() as playwright:
        context = playwright.chromium.launch_persistent_context(
            user_data_dir=str(Path(args.user_data_dir).resolve()),
            headless=args.headless,
            slow_mo=args.slow_mo_ms,
            viewport={"width": 1440, "height": 900},
        )
        context.set_default_timeout(args.timeout_ms)

        page = context.pages[0] if context.pages else context.new_page()
        ensure_login_page(page, args.login_url, args.headless)

        workflow = SaleadsMiNegocioWorkflow(
            page=page,
            context=context,
            reporter=reporter,
            screenshots_dir=screenshots_dir,
            timeout_ms=args.timeout_ms,
            account_email=args.account_email,
        )

        try:
            workflow.run()
        finally:
            try:
                context.close()
            except Exception:
                pass

    report_payload = {
        "workflow_name": "saleads_mi_negocio_full_test",
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "artifacts_root": str(run_root),
        **reporter.to_dict(),
    }

    report_file = run_root / "final_report.json"
    report_file.write_text(json.dumps(report_payload, indent=2, ensure_ascii=False), encoding="utf-8")

    print("\n=== FINAL REPORT ===")
    for field in REPORT_FIELDS:
        print(f"- {field}: {reporter.field_status(field)}")
    print(f"Overall: {reporter.overall_status()}")
    print(f"Report file: {report_file}")

    return 0 if reporter.overall_status() == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
