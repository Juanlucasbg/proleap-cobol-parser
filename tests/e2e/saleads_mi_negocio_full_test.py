#!/usr/bin/env python3
"""End-to-end workflow validation for SaleADS Mi Negocio module."""

from __future__ import annotations

import json
import os
import re
import sys
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable

from playwright.sync_api import (
    BrowserContext,
    Locator,
    Page,
    TimeoutError as PlaywrightTimeoutError,
    sync_playwright,
)


DEFAULT_TIMEOUT_MS = int(os.getenv("SALEADS_TIMEOUT_MS", "20000"))
HEADLESS = os.getenv("HEADLESS", "false").strip().lower() in {"1", "true", "yes"}
BASE_URL = os.getenv("SALEADS_BASE_URL", "").strip()
GOOGLE_ACCOUNT_EMAIL = os.getenv(
    "GOOGLE_ACCOUNT_EMAIL", "juanlucasbarbiergarzon@gmail.com"
).strip()
RUN_ID = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
ARTIFACTS_DIR = Path(
    os.getenv("SALEADS_ARTIFACTS_DIR", f"tests/e2e/artifacts/{RUN_ID}")
).resolve()
SCREENSHOTS_DIR = ARTIFACTS_DIR / "screenshots"


@dataclass
class ValidationResult:
    description: str
    passed: bool
    details: str = ""


@dataclass
class StepResult:
    name: str
    validations: list[ValidationResult] = field(default_factory=list)
    evidence: dict[str, object] = field(default_factory=dict)
    error: str = ""
    passed: bool = False

    def add_validation(self, description: str, passed: bool, details: str = "") -> None:
        self.validations.append(
            ValidationResult(description=description, passed=passed, details=details)
        )

    def finalize(self) -> None:
        if self.error:
            self.passed = False
            return

        if not self.validations:
            self.passed = True
            return

        self.passed = all(result.passed for result in self.validations)


def wait_for_ui(page: Page) -> None:
    for state in ("domcontentloaded", "networkidle"):
        try:
            page.wait_for_load_state(state, timeout=DEFAULT_TIMEOUT_MS)
        except PlaywrightTimeoutError:
            # Some screens keep persistent network activity. Do not block the flow.
            continue


def text_patterns(texts: Iterable[str]) -> list[re.Pattern[str]]:
    return [re.compile(re.escape(text), re.IGNORECASE) for text in texts]


def locator_is_visible(locator: Locator, timeout_ms: int = 3500) -> bool:
    try:
        locator.first.wait_for(state="visible", timeout=timeout_ms)
        return True
    except PlaywrightTimeoutError:
        return False


def find_target(page: Page, texts: Iterable[str]) -> Locator:
    patterns = text_patterns(texts)
    role_candidates = ("button", "link", "menuitem", "tab")

    for pattern in patterns:
        for role in role_candidates:
            role_locator = page.get_by_role(role, name=pattern)
            if locator_is_visible(role_locator):
                return role_locator.first

        text_locator = page.get_by_text(pattern)
        if locator_is_visible(text_locator):
            return text_locator.first

    joined = ", ".join(texts)
    raise RuntimeError(f"Could not find visible element with text: {joined}")


def safe_click(page: Page, locator: Locator) -> None:
    locator.click(timeout=DEFAULT_TIMEOUT_MS)
    wait_for_ui(page)


def click_by_text(page: Page, texts: Iterable[str]) -> Locator:
    target = find_target(page, texts)
    safe_click(page, target)
    return target


def is_any_text_visible(page: Page, texts: Iterable[str], timeout_ms: int = 4500) -> bool:
    for pattern in text_patterns(texts):
        if locator_is_visible(page.get_by_text(pattern), timeout_ms=timeout_ms):
            return True
    return False


def is_heading_visible(page: Page, texts: Iterable[str], timeout_ms: int = 4500) -> bool:
    for pattern in text_patterns(texts):
        heading = page.get_by_role("heading", name=pattern)
        if locator_is_visible(heading, timeout_ms=timeout_ms):
            return True
        if locator_is_visible(page.get_by_text(pattern), timeout_ms=timeout_ms):
            return True
    return False


def find_business_name_input(page: Page) -> Locator | None:
    candidates = [
        page.get_by_label(re.compile(r"Nombre del Negocio", re.IGNORECASE)),
        page.get_by_placeholder(re.compile(r"Nombre del Negocio", re.IGNORECASE)),
        page.locator("input[name*='negocio' i], input[id*='negocio' i]"),
    ]
    for candidate in candidates:
        if locator_is_visible(candidate, timeout_ms=2500):
            return candidate.first
    return None


def capture_screenshot(
    page: Page, step: StepResult, file_name: str, full_page: bool = False
) -> None:
    SCREENSHOTS_DIR.mkdir(parents=True, exist_ok=True)
    screenshot_path = SCREENSHOTS_DIR / file_name
    page.screenshot(path=str(screenshot_path), full_page=full_page)
    current = step.evidence.setdefault("screenshots", [])
    if isinstance(current, list):
        current.append(str(screenshot_path))


def get_sidebar_visible(page: Page) -> bool:
    aside_or_nav = page.locator("aside, nav")
    try:
        aside_or_nav.first.wait_for(state="visible", timeout=DEFAULT_TIMEOUT_MS)
        return True
    except PlaywrightTimeoutError:
        return False


def bootstrap(context: BrowserContext) -> Page:
    page = context.new_page()
    page.set_default_timeout(DEFAULT_TIMEOUT_MS)

    if BASE_URL:
        page.goto(BASE_URL, wait_until="domcontentloaded")
        wait_for_ui(page)
    else:
        # Prompt allows any environment; URL comes from variable to avoid hardcoding domains.
        page.goto("about:blank")
    return page


def step_login_with_google(page: Page) -> StepResult:
    step = StepResult(name="Login")
    try:
        if not BASE_URL:
            raise RuntimeError(
                "SALEADS_BASE_URL is not set. Provide the environment login URL."
            )

        login_texts = (
            "Sign in with Google",
            "Iniciar con Google",
            "Iniciar sesión con Google",
            "Continuar con Google",
            "Google",
        )
        login_target = find_target(page, login_texts)

        popup_page: Page | None = None
        try:
            with page.expect_popup(timeout=5000) as popup_info:
                safe_click(page, login_target)
            popup_page = popup_info.value
            wait_for_ui(popup_page)
        except PlaywrightTimeoutError:
            safe_click(page, login_target)

        google_page = popup_page if popup_page else page
        if is_any_text_visible(google_page, (GOOGLE_ACCOUNT_EMAIL,), timeout_ms=5000):
            click_by_text(google_page, (GOOGLE_ACCOUNT_EMAIL,))

        if popup_page is not None:
            try:
                popup_page.wait_for_event("close", timeout=20000)
            except PlaywrightTimeoutError:
                # Continue even if popup remains open due to account already selected.
                pass
            page.bring_to_front()
            wait_for_ui(page)

        main_interface_visible = is_any_text_visible(
            page,
            ("Dashboard", "Panel", "Inicio", "Mi Negocio", "Negocio"),
            timeout_ms=15000,
        )
        sidebar_visible = get_sidebar_visible(page)

        step.add_validation(
            "Confirm the main application interface appears.",
            main_interface_visible,
            "Expected dashboard or navigation text after login.",
        )
        step.add_validation(
            "Confirm the left sidebar navigation is visible.",
            sidebar_visible,
            "Expected an aside/nav container visible in the app shell.",
        )
        capture_screenshot(page, step, "01_dashboard_loaded.png")
    except Exception as error:  # pragma: no cover - defensive flow reporting
        step.error = str(error)

    step.finalize()
    return step


def step_open_mi_negocio_menu(page: Page) -> StepResult:
    step = StepResult(name="Mi Negocio menu")
    try:
        if is_any_text_visible(page, ("Negocio",), timeout_ms=5000):
            click_by_text(page, ("Negocio",))

        click_by_text(page, ("Mi Negocio",))

        submenu_expanded = is_any_text_visible(
            page, ("Agregar Negocio", "Administrar Negocios"), timeout_ms=10000
        )
        add_business_visible = is_any_text_visible(page, ("Agregar Negocio",))
        manage_business_visible = is_any_text_visible(page, ("Administrar Negocios",))

        step.add_validation("Confirm the submenu expands.", submenu_expanded)
        step.add_validation(
            "Confirm 'Agregar Negocio' is visible.", add_business_visible
        )
        step.add_validation(
            "Confirm 'Administrar Negocios' is visible.", manage_business_visible
        )
        capture_screenshot(page, step, "02_mi_negocio_expanded_menu.png")
    except Exception as error:
        step.error = str(error)

    step.finalize()
    return step


def step_validate_agregar_negocio_modal(page: Page) -> StepResult:
    step = StepResult(name="Agregar Negocio modal")
    try:
        click_by_text(page, ("Agregar Negocio",))

        modal_visible = is_heading_visible(page, ("Crear Nuevo Negocio",), timeout_ms=10000)
        business_name_input = find_business_name_input(page)
        input_visible = business_name_input is not None
        quota_visible = is_any_text_visible(page, ("Tienes 2 de 3 negocios",))
        cancel_visible = is_any_text_visible(page, ("Cancelar",))
        create_visible = is_any_text_visible(page, ("Crear Negocio",))

        step.add_validation("Modal title 'Crear Nuevo Negocio' is visible.", modal_visible)
        step.add_validation(
            "Input field 'Nombre del Negocio' exists.", input_visible
        )
        step.add_validation(
            "Text 'Tienes 2 de 3 negocios' is visible.", quota_visible
        )
        step.add_validation("Buttons 'Cancelar' and 'Crear Negocio' are present.", cancel_visible and create_visible)

        capture_screenshot(page, step, "03_agregar_negocio_modal.png")

        if business_name_input is not None:
            try:
                business_name_input.fill("Negocio Prueba Automatización")
            except Exception:
                # Optional action only.
                pass

        if cancel_visible:
            click_by_text(page, ("Cancelar",))
    except Exception as error:
        step.error = str(error)

    step.finalize()
    return step


def step_open_administrar_negocios(page: Page) -> StepResult:
    step = StepResult(name="Administrar Negocios view")
    try:
        if not is_any_text_visible(page, ("Administrar Negocios",), timeout_ms=2000):
            click_by_text(page, ("Mi Negocio",))

        click_by_text(page, ("Administrar Negocios",))

        info_general_visible = is_any_text_visible(page, ("Información General",), timeout_ms=15000)
        account_details_visible = is_any_text_visible(page, ("Detalles de la Cuenta",), timeout_ms=10000)
        business_section_visible = is_any_text_visible(page, ("Tus Negocios",), timeout_ms=10000)
        legal_section_visible = is_any_text_visible(page, ("Sección Legal",), timeout_ms=10000)

        step.add_validation("Section 'Información General' exists.", info_general_visible)
        step.add_validation("Section 'Detalles de la Cuenta' exists.", account_details_visible)
        step.add_validation("Section 'Tus Negocios' exists.", business_section_visible)
        step.add_validation("Section 'Sección Legal' exists.", legal_section_visible)
        capture_screenshot(page, step, "04_administrar_negocios_page.png", full_page=True)
    except Exception as error:
        step.error = str(error)

    step.finalize()
    return step


def step_validate_informacion_general(page: Page) -> StepResult:
    step = StepResult(name="Información General")
    try:
        user_name_visible = is_any_text_visible(
            page,
            ("@", "Nombre", "Usuario", "Perfil"),
            timeout_ms=6000,
        )
        user_email_visible = is_any_text_visible(page, ("@", ".com", ".ai"), timeout_ms=6000)
        business_plan_visible = is_any_text_visible(page, ("BUSINESS PLAN",))
        change_plan_visible = is_any_text_visible(page, ("Cambiar Plan",))

        step.add_validation("User name is visible.", user_name_visible)
        step.add_validation("User email is visible.", user_email_visible)
        step.add_validation("Text 'BUSINESS PLAN' is visible.", business_plan_visible)
        step.add_validation("Button 'Cambiar Plan' is visible.", change_plan_visible)
    except Exception as error:
        step.error = str(error)

    step.finalize()
    return step


def step_validate_detalles_cuenta(page: Page) -> StepResult:
    step = StepResult(name="Detalles de la Cuenta")
    try:
        created_visible = is_any_text_visible(page, ("Cuenta creada",))
        active_visible = is_any_text_visible(page, ("Estado activo",))
        language_visible = is_any_text_visible(page, ("Idioma seleccionado",))

        step.add_validation("'Cuenta creada' is visible.", created_visible)
        step.add_validation("'Estado activo' is visible.", active_visible)
        step.add_validation("'Idioma seleccionado' is visible.", language_visible)
    except Exception as error:
        step.error = str(error)

    step.finalize()
    return step


def step_validate_tus_negocios(page: Page) -> StepResult:
    step = StepResult(name="Tus Negocios")
    try:
        list_visible = is_any_text_visible(page, ("Tus Negocios", "Negocio"), timeout_ms=8000)
        add_button_visible = is_any_text_visible(page, ("Agregar Negocio",))
        quota_visible = is_any_text_visible(page, ("Tienes 2 de 3 negocios",))

        step.add_validation("Business list is visible.", list_visible)
        step.add_validation("Button 'Agregar Negocio' exists.", add_button_visible)
        step.add_validation(
            "Text 'Tienes 2 de 3 negocios' is visible.", quota_visible
        )
    except Exception as error:
        step.error = str(error)

    step.finalize()
    return step


def validate_legal_content(target_page: Page, expected_heading: str) -> tuple[bool, bool]:
    heading_visible = is_heading_visible(target_page, (expected_heading,), timeout_ms=15000)
    legal_content_visible = False

    if locator_is_visible(target_page.locator("main, article, section, p"), timeout_ms=6000):
        legal_content_visible = True
    else:
        try:
            body_text = target_page.locator("body").inner_text(timeout=6000)
            legal_content_visible = len(body_text.strip()) > 150
        except Exception:
            legal_content_visible = False

    return heading_visible, legal_content_visible


def navigate_legal_link(
    page: Page,
    step_name: str,
    link_text: str,
    expected_heading: str,
    screenshot_name: str,
) -> StepResult:
    step = StepResult(name=step_name)
    app_page = page

    try:
        popup_page: Page | None = None
        target_page: Page = app_page
        current_url = app_page.url

        link_target = find_target(app_page, (link_text,))
        try:
            with app_page.expect_popup(timeout=5000) as popup_info:
                safe_click(app_page, link_target)
            popup_page = popup_info.value
            target_page = popup_page
            wait_for_ui(target_page)
        except PlaywrightTimeoutError:
            safe_click(app_page, link_target)
            wait_for_ui(app_page)
            target_page = app_page

        heading_visible, content_visible = validate_legal_content(
            target_page, expected_heading
        )
        step.add_validation(
            f"The page contains the heading '{expected_heading}'.", heading_visible
        )
        step.add_validation("Legal content text is visible.", content_visible)

        capture_screenshot(target_page, step, screenshot_name, full_page=True)
        step.evidence["final_url"] = target_page.url
        step.evidence["opened_new_tab"] = popup_page is not None

        if popup_page is not None:
            popup_page.close()
            app_page.bring_to_front()
            wait_for_ui(app_page)
        elif app_page.url != current_url:
            app_page.go_back(wait_until="domcontentloaded")
            wait_for_ui(app_page)
    except Exception as error:
        step.error = str(error)

    step.finalize()
    return step


def write_report(results: list[StepResult]) -> Path:
    ARTIFACTS_DIR.mkdir(parents=True, exist_ok=True)
    report_path = ARTIFACTS_DIR / "final_report.json"

    report = {
        "test_name": "saleads_mi_negocio_full_test",
        "run_id": RUN_ID,
        "timestamp_utc": datetime.now(timezone.utc).isoformat(),
        "base_url": BASE_URL,
        "results": {step.name: asdict(step) for step in results},
        "summary": {
            step.name: "PASS" if step.passed else "FAIL" for step in results
        },
        "all_passed": all(step.passed for step in results),
    }

    report_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    return report_path


def skipped_step(
    name: str, validations: list[str], reason: str
) -> StepResult:
    step = StepResult(name=name, error=reason)
    for description in validations:
        step.add_validation(description, False, reason)
    step.finalize()
    return step


def print_summary(results: list[StepResult], report_path: Path) -> None:
    print("\nFinal Report")
    for step in results:
        status = "PASS" if step.passed else "FAIL"
        print(f"- {step.name}: {status}")
        if step.error:
            print(f"  error: {step.error}")

    print(f"\nArtifacts directory: {ARTIFACTS_DIR}")
    print(f"Report JSON: {report_path}")


def main() -> int:
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=HEADLESS)
        context = browser.new_context()

        try:
            page = bootstrap(context)
            results: list[StepResult] = []

            login_step = step_login_with_google(page)
            results.append(login_step)

            if login_step.passed:
                results.extend(
                    [
                        step_open_mi_negocio_menu(page),
                        step_validate_agregar_negocio_modal(page),
                        step_open_administrar_negocios(page),
                        step_validate_informacion_general(page),
                        step_validate_detalles_cuenta(page),
                        step_validate_tus_negocios(page),
                        navigate_legal_link(
                            page=page,
                            step_name="Términos y Condiciones",
                            link_text="Términos y Condiciones",
                            expected_heading="Términos y Condiciones",
                            screenshot_name="08_terminos_y_condiciones.png",
                        ),
                        navigate_legal_link(
                            page=page,
                            step_name="Política de Privacidad",
                            link_text="Política de Privacidad",
                            expected_heading="Política de Privacidad",
                            screenshot_name="09_politica_de_privacidad.png",
                        ),
                    ]
                )
            else:
                skip_reason = (
                    "Blocked by failed login step; downstream workflow not executable."
                )
                results.extend(
                    [
                        skipped_step(
                            "Mi Negocio menu",
                            [
                                "Confirm the submenu expands.",
                                "Confirm 'Agregar Negocio' is visible.",
                                "Confirm 'Administrar Negocios' is visible.",
                            ],
                            skip_reason,
                        ),
                        skipped_step(
                            "Agregar Negocio modal",
                            [
                                "Modal title 'Crear Nuevo Negocio' is visible.",
                                "Input field 'Nombre del Negocio' exists.",
                                "Text 'Tienes 2 de 3 negocios' is visible.",
                                "Buttons 'Cancelar' and 'Crear Negocio' are present.",
                            ],
                            skip_reason,
                        ),
                        skipped_step(
                            "Administrar Negocios view",
                            [
                                "Section 'Información General' exists.",
                                "Section 'Detalles de la Cuenta' exists.",
                                "Section 'Tus Negocios' exists.",
                                "Section 'Sección Legal' exists.",
                            ],
                            skip_reason,
                        ),
                        skipped_step(
                            "Información General",
                            [
                                "User name is visible.",
                                "User email is visible.",
                                "Text 'BUSINESS PLAN' is visible.",
                                "Button 'Cambiar Plan' is visible.",
                            ],
                            skip_reason,
                        ),
                        skipped_step(
                            "Detalles de la Cuenta",
                            [
                                "'Cuenta creada' is visible.",
                                "'Estado activo' is visible.",
                                "'Idioma seleccionado' is visible.",
                            ],
                            skip_reason,
                        ),
                        skipped_step(
                            "Tus Negocios",
                            [
                                "Business list is visible.",
                                "Button 'Agregar Negocio' exists.",
                                "Text 'Tienes 2 de 3 negocios' is visible.",
                            ],
                            skip_reason,
                        ),
                        skipped_step(
                            "Términos y Condiciones",
                            [
                                "The page contains the heading 'Términos y Condiciones'.",
                                "Legal content text is visible.",
                            ],
                            skip_reason,
                        ),
                        skipped_step(
                            "Política de Privacidad",
                            [
                                "The page contains the heading 'Política de Privacidad'.",
                                "Legal content text is visible.",
                            ],
                            skip_reason,
                        ),
                    ]
                )
            report_path = write_report(results)
            print_summary(results, report_path)
            return 0 if all(step.passed for step in results) else 1
        finally:
            context.close()
            browser.close()


if __name__ == "__main__":
    sys.exit(main())
