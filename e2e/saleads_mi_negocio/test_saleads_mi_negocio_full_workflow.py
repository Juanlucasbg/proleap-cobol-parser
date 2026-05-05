import json
import os
import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import pytest
from playwright.sync_api import BrowserContext, Page, TimeoutError as PlaywrightTimeoutError, expect


CHECKPOINT_DIR = Path(__file__).resolve().parent / "artifacts" / "screenshots"
REPORT_PATH = Path(__file__).resolve().parent / "artifacts" / "saleads_mi_negocio_report.json"


@dataclass
class StepResult:
    name: str
    passed: bool
    details: list[str] = field(default_factory=list)
    screenshot_paths: list[str] = field(default_factory=list)
    urls: list[str] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        return {
            "name": self.name,
            "passed": self.passed,
            "details": self.details,
            "screenshots": self.screenshot_paths,
            "urls": self.urls,
        }


@dataclass
class WorkflowReport:
    step_results: list[StepResult] = field(default_factory=list)

    def add(self, result: StepResult) -> None:
        self.step_results.append(result)

    def summary(self) -> dict[str, bool]:
        mapping = {
            "Login": "Login with Google",
            "Mi Negocio menu": "Open Mi Negocio menu",
            "Agregar Negocio modal": "Validate Agregar Negocio modal",
            "Administrar Negocios view": "Open Administrar Negocios",
            "Información General": "Validate Información General",
            "Detalles de la Cuenta": "Validate Detalles de la Cuenta",
            "Tus Negocios": "Validate Tus Negocios",
            "Términos y Condiciones": "Validate Términos y Condiciones",
            "Política de Privacidad": "Validate Política de Privacidad",
        }
        by_name = {result.name: result.passed for result in self.step_results}
        return {label: by_name.get(step_name, False) for label, step_name in mapping.items()}

    def to_dict(self) -> dict[str, Any]:
        return {
            "summary": self.summary(),
            "steps": [result.to_dict() for result in self.step_results],
        }


def _safe_name(value: str) -> str:
    return re.sub(r"[^a-zA-Z0-9_-]+", "_", value.strip()).strip("_").lower()


def _wait_for_idle_ui(page: Page) -> None:
    page.wait_for_load_state("domcontentloaded")
    page.wait_for_load_state("networkidle")


def _shot(page: Page, name: str) -> str:
    CHECKPOINT_DIR.mkdir(parents=True, exist_ok=True)
    screenshot_path = CHECKPOINT_DIR / f"{_safe_name(name)}.png"
    page.screenshot(path=str(screenshot_path), full_page=True)
    return str(screenshot_path.relative_to(Path(__file__).resolve().parent))


def _visible_text_exists(page: Page, text: str, timeout: int = 20000) -> bool:
    loc = page.get_by_text(re.compile(text, re.IGNORECASE), exact=False)
    expect(loc.first).to_be_visible(timeout=timeout)
    return True


def _click_by_text(page: Page, text_pattern: str) -> None:
    candidate = page.get_by_role("button", name=re.compile(text_pattern, re.IGNORECASE))
    if candidate.count() > 0 and candidate.first.is_visible():
        candidate.first.click()
        _wait_for_idle_ui(page)
        return

    candidate = page.get_by_text(re.compile(text_pattern, re.IGNORECASE), exact=False)
    expect(candidate.first).to_be_visible()
    candidate.first.click()
    _wait_for_idle_ui(page)


def _ensure_sidebar(page: Page) -> None:
    sidebar_candidates = [
        page.locator("aside"),
        page.get_by_role("navigation"),
        page.locator("[class*='sidebar'], [id*='sidebar'], [data-testid*='sidebar']"),
    ]
    for loc in sidebar_candidates:
        if loc.count() > 0 and loc.first.is_visible():
            return
    # fall back to common left-menu labels
    assert (
        page.get_by_text(re.compile("Negocio|Mi\\s+Negocio", re.IGNORECASE)).first.is_visible()
    ), "Left sidebar navigation was not visible"


def _append_failure(step: StepResult, exc: Exception) -> None:
    step.details.append(f"Failure: {type(exc).__name__}: {exc}")


def _write_report(report: WorkflowReport) -> None:
    REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
    REPORT_PATH.write_text(json.dumps(report.to_dict(), indent=2, ensure_ascii=False), encoding="utf-8")


@pytest.fixture(scope="session")
def browser_context_args(browser_context_args: dict[str, Any]) -> dict[str, Any]:
    return {
        **browser_context_args,
        "viewport": {"width": 1440, "height": 900},
    }


def _handle_possible_google_account_picker(page: Page) -> bool:
    account_email = "juanlucasbarbiergarzon@gmail.com"
    account_match = page.get_by_text(re.compile(re.escape(account_email), re.IGNORECASE))
    if account_match.count() > 0 and account_match.first.is_visible():
        account_match.first.click()
        _wait_for_idle_ui(page)
        return True
    return False


def _click_google_login_and_handle_account_picker(context: BrowserContext, page: Page) -> None:
    login_locator = page.get_by_role(
        "button",
        name=re.compile("Sign in with Google|Iniciar con Google|Continuar con Google|Google", re.IGNORECASE),
    )
    if login_locator.count() == 0:
        login_locator = page.get_by_text(
            re.compile("Sign in with Google|Iniciar con Google|Continuar con Google|Google", re.IGNORECASE),
            exact=False,
        )
    expect(login_locator.first).to_be_visible(timeout=20000)

    google_page: Page | None = None
    try:
        with context.expect_page(timeout=7000) as popup:
            login_locator.first.click()
        google_page = popup.value
    except PlaywrightTimeoutError:
        login_locator.first.click()
    _wait_for_idle_ui(page)

    target_page = google_page if google_page else page
    _handle_possible_google_account_picker(target_page)

    if google_page:
        try:
            google_page.wait_for_close(timeout=30000)
        except PlaywrightTimeoutError:
            # If popup did not close itself, continue validation from app page.
            pass
        page.bring_to_front()
        _wait_for_idle_ui(page)


def _open_legal_link(context: BrowserContext, page: Page, link_label: str) -> tuple[Page, str]:
    link_locator = page.get_by_role("link", name=re.compile(link_label, re.IGNORECASE))
    if link_locator.count() == 0:
        link_locator = page.get_by_text(re.compile(link_label, re.IGNORECASE))
    expect(link_locator.first).to_be_visible(timeout=15000)

    existing_pages = list(context.pages)
    link_locator.first.click()
    page.wait_for_timeout(500)
    _wait_for_idle_ui(page)

    if len(context.pages) > len(existing_pages):
        new_page = context.pages[-1]
        new_page.wait_for_load_state("domcontentloaded")
        new_page.wait_for_load_state("networkidle")
        return new_page, new_page.url
    return page, page.url


def test_saleads_mi_negocio_full_workflow(page: Page, context: BrowserContext) -> None:
    """
    Environment-agnostic workflow test for SaleADS Mi Negocio module.

    Required runtime assumptions:
    - Browser is already on SaleADS login page, OR
    - SALEADS_BASE_URL is provided so the test can navigate to login/dashboard.
    """
    report = WorkflowReport()

    base_url = os.getenv("SALEADS_BASE_URL", "").strip()
    if base_url:
        page.goto(base_url)
        _wait_for_idle_ui(page)

    step_1 = StepResult(name="Login with Google", passed=False)
    try:
        _click_google_login_and_handle_account_picker(context, page)
        _ensure_sidebar(page)
        step_1.details.extend(
            [
                "Main application interface visible after login.",
                "Left sidebar navigation is visible.",
            ]
        )
        step_1.screenshot_paths.append(_shot(page, "01_dashboard_loaded"))
        step_1.passed = True
    except Exception as exc:  # pragma: no cover
        _append_failure(step_1, exc)
    finally:
        report.add(step_1)

    step_2 = StepResult(name="Open Mi Negocio menu", passed=False)
    try:
        _click_by_text(page, "Mi Negocio")
        _visible_text_exists(page, "Agregar Negocio")
        _visible_text_exists(page, "Administrar Negocios")
        step_2.details.extend(
            [
                "Mi Negocio submenu expanded.",
                "Agregar Negocio visible.",
                "Administrar Negocios visible.",
            ]
        )
        step_2.screenshot_paths.append(_shot(page, "02_mi_negocio_menu_expanded"))
        step_2.passed = True
    except Exception as exc:  # pragma: no cover
        _append_failure(step_2, exc)
    finally:
        report.add(step_2)

    step_3 = StepResult(name="Validate Agregar Negocio modal", passed=False)
    try:
        _click_by_text(page, "Agregar Negocio")
        _visible_text_exists(page, "Crear Nuevo Negocio")
        _visible_text_exists(page, "Nombre del Negocio")
        _visible_text_exists(page, "Tienes 2 de 3 negocios")
        _visible_text_exists(page, "Cancelar")
        _visible_text_exists(page, "Crear Negocio")

        input_locator = page.get_by_label(re.compile("Nombre del Negocio", re.IGNORECASE))
        if input_locator.count() == 0:
            input_locator = page.get_by_placeholder(re.compile("Nombre del Negocio", re.IGNORECASE))
        if input_locator.count() > 0:
            input_locator.first.fill("Negocio Prueba Automatización")

        step_3.screenshot_paths.append(_shot(page, "03_agregar_negocio_modal"))
        _click_by_text(page, "Cancelar")
        step_3.details.extend(
            [
                "Modal title Crear Nuevo Negocio visible.",
                "Nombre del Negocio input visible.",
                "Usage text Tienes 2 de 3 negocios visible.",
                "Cancelar and Crear Negocio buttons visible.",
                "Optional input fill executed, then modal canceled.",
            ]
        )
        step_3.passed = True
    except Exception as exc:  # pragma: no cover
        _append_failure(step_3, exc)
    finally:
        report.add(step_3)

    step_4 = StepResult(name="Open Administrar Negocios", passed=False)
    try:
        mi_negocio = page.get_by_text(re.compile("Mi\\s+Negocio", re.IGNORECASE))
        if mi_negocio.count() > 0 and mi_negocio.first.is_visible():
            mi_negocio.first.click()
            _wait_for_idle_ui(page)

        _click_by_text(page, "Administrar Negocios")
        _visible_text_exists(page, "Información General")
        _visible_text_exists(page, "Detalles de la Cuenta")
        _visible_text_exists(page, "Tus Negocios")
        _visible_text_exists(page, "Sección Legal")
        step_4.screenshot_paths.append(_shot(page, "04_administrar_negocios_view"))
        step_4.details.extend(
            [
                "Información General visible.",
                "Detalles de la Cuenta visible.",
                "Tus Negocios visible.",
                "Sección Legal visible.",
            ]
        )
        step_4.passed = True
    except Exception as exc:  # pragma: no cover
        _append_failure(step_4, exc)
    finally:
        report.add(step_4)

    step_5 = StepResult(name="Validate Información General", passed=False)
    try:
        _visible_text_exists(page, "BUSINESS PLAN")
        _visible_text_exists(page, "Cambiar Plan")
        # User name / email can vary; use broad patterns under the section.
        info_section = page.get_by_text("Información General", exact=False).first.locator("xpath=ancestor::*[1]")
        section_text = info_section.inner_text()
        assert "@" in section_text, "Expected a visible user email in Información General"
        step_5.details.extend(
            [
                "User name/email block visible in Información General section.",
                "User email visible in Información General section.",
                "BUSINESS PLAN visible.",
                "Cambiar Plan visible.",
            ]
        )
        step_5.passed = True
    except Exception as exc:  # pragma: no cover
        _append_failure(step_5, exc)
    finally:
        report.add(step_5)

    step_6 = StepResult(name="Validate Detalles de la Cuenta", passed=False)
    try:
        _visible_text_exists(page, "Cuenta creada")
        _visible_text_exists(page, "Estado activo")
        _visible_text_exists(page, "Idioma seleccionado")
        step_6.details.extend(
            [
                "Cuenta creada visible.",
                "Estado activo visible.",
                "Idioma seleccionado visible.",
            ]
        )
        step_6.passed = True
    except Exception as exc:  # pragma: no cover
        _append_failure(step_6, exc)
    finally:
        report.add(step_6)

    step_7 = StepResult(name="Validate Tus Negocios", passed=False)
    try:
        _visible_text_exists(page, "Tus Negocios")
        _visible_text_exists(page, "Agregar Negocio")
        _visible_text_exists(page, "Tienes 2 de 3 negocios")
        step_7.details.extend(
            [
                "Business list section visible.",
                "Agregar Negocio button visible.",
                "Tienes 2 de 3 negocios visible.",
            ]
        )
        step_7.passed = True
    except Exception as exc:  # pragma: no cover
        _append_failure(step_7, exc)
    finally:
        report.add(step_7)

    step_8 = StepResult(name="Validate Términos y Condiciones", passed=False)
    legal_terms_page: Page | None = None
    try:
        legal_terms_page, terms_url = _open_legal_link(context, page, "Términos y Condiciones")
        _visible_text_exists(legal_terms_page, "Términos y Condiciones")
        legal_terms_page.locator("body").first.wait_for(state="visible")
        step_8.urls.append(terms_url)
        step_8.screenshot_paths.append(_shot(legal_terms_page, "08_terminos_y_condiciones"))
        step_8.details.extend(
            [
                "Términos y Condiciones heading visible.",
                "Legal content is visible on page.",
                f"Final URL: {terms_url}",
            ]
        )
        step_8.passed = True
    except Exception as exc:  # pragma: no cover
        _append_failure(step_8, exc)
    finally:
        if legal_terms_page and legal_terms_page != page:
            legal_terms_page.close()
            page.bring_to_front()
            _wait_for_idle_ui(page)
        report.add(step_8)

    step_9 = StepResult(name="Validate Política de Privacidad", passed=False)
    legal_privacy_page: Page | None = None
    try:
        legal_privacy_page, privacy_url = _open_legal_link(context, page, "Política de Privacidad")
        _visible_text_exists(legal_privacy_page, "Política de Privacidad")
        legal_privacy_page.locator("body").first.wait_for(state="visible")
        step_9.urls.append(privacy_url)
        step_9.screenshot_paths.append(_shot(legal_privacy_page, "09_politica_de_privacidad"))
        step_9.details.extend(
            [
                "Política de Privacidad heading visible.",
                "Legal content is visible on page.",
                f"Final URL: {privacy_url}",
            ]
        )
        step_9.passed = True
    except Exception as exc:  # pragma: no cover
        _append_failure(step_9, exc)
    finally:
        if legal_privacy_page and legal_privacy_page != page:
            legal_privacy_page.close()
            page.bring_to_front()
            _wait_for_idle_ui(page)
        report.add(step_9)

    _write_report(report)

    failures = [result.name for result in report.step_results if not result.passed]
    assert not failures, f"Workflow had failing steps: {', '.join(failures)}. See report at {REPORT_PATH}"
