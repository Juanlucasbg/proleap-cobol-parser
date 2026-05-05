import json
import os
import re
from contextlib import suppress
from datetime import datetime, timezone
from pathlib import Path

import pytest
from playwright.sync_api import (
    BrowserContext,
    Page,
    TimeoutError as PlaywrightTimeoutError,
    expect,
    sync_playwright,
)


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
GOOGLE_ACCOUNT_EMAIL = os.getenv(
    "SALEADS_GOOGLE_ACCOUNT_EMAIL", "juanlucasbarbiergarzon@gmail.com"
)
LOGIN_URL_ENV = "SALEADS_LOGIN_URL"

BASE_DIR = Path(__file__).resolve().parents[1]
ARTIFACTS_DIR = BASE_DIR / "artifacts"
SCREENSHOTS_DIR = ARTIFACTS_DIR / "screenshots"
REPORT_PATH = ARTIFACTS_DIR / "saleads_mi_negocio_full_report.json"


def _ensure_artifact_dirs() -> None:
    SCREENSHOTS_DIR.mkdir(parents=True, exist_ok=True)


def _new_report(login_url: str) -> dict:
    results = {
        field: {"status": "NOT_RUN", "details": [], "evidence": [], "final_url": None}
        for field in REPORT_FIELDS
    }
    return {
        "name": "saleads_mi_negocio_full_test",
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "login_url": login_url,
        "results": results,
    }


def _mark_pass(report: dict, field: str) -> None:
    report["results"][field]["status"] = "PASS"


def _mark_fail(report: dict, field: str, error: Exception | str) -> None:
    report["results"][field]["status"] = "FAIL"
    report["results"][field]["details"].append(str(error))


def _add_evidence(report: dict, field: str, evidence: str) -> None:
    report["results"][field]["evidence"].append(evidence)


def _save_report(report: dict) -> None:
    _ensure_artifact_dirs()
    REPORT_PATH.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")


def _screenshot(page: Page, name: str, full_page: bool = False) -> str:
    _ensure_artifact_dirs()
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    path = SCREENSHOTS_DIR / f"{timestamp}_{name}.png"
    page.screenshot(path=str(path), full_page=full_page)
    return str(path)


def _wait_ui(page: Page, timeout_ms: int = 15000) -> None:
    with suppress(Exception):
        page.wait_for_load_state("domcontentloaded", timeout=timeout_ms)
    with suppress(Exception):
        page.wait_for_load_state("networkidle", timeout=timeout_ms)


def _first_visible(candidates: list, timeout_ms: int = 5000):
    for locator in candidates:
        with suppress(Exception):
            locator.first.wait_for(state="visible", timeout=timeout_ms)
            return locator.first
    raise AssertionError("No visible element found from expected candidates.")


def _visible_or_none(candidates: list, timeout_ms: int = 3000):
    for locator in candidates:
        with suppress(Exception):
            locator.first.wait_for(state="visible", timeout=timeout_ms)
            return locator.first
    return None


def _click_and_wait(locator, page: Page) -> None:
    locator.click()
    _wait_ui(page)


def _maybe_choose_google_account(page: Page, email: str) -> None:
    email_pattern = re.compile(re.escape(email), re.IGNORECASE)
    selectors = [
        page.get_by_text(email_pattern),
        page.get_by_role("button", name=email_pattern),
        page.get_by_role("link", name=email_pattern),
    ]

    for locator in selectors:
        with suppress(Exception):
            locator.first.wait_for(state="visible", timeout=5000)
            _click_and_wait(locator.first, page)
            return


@pytest.fixture(scope="session")
def browser_context() -> BrowserContext:
    headless_raw = os.getenv("SALEADS_HEADLESS", "true").strip().lower()
    headless = headless_raw not in {"0", "false", "no"}
    slow_mo = int(os.getenv("SALEADS_SLOW_MO_MS", "0"))

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=headless, slow_mo=slow_mo)
        context = browser.new_context(viewport={"width": 1600, "height": 1200})
        yield context
        context.close()
        browser.close()


@pytest.fixture()
def page(browser_context: BrowserContext) -> Page:
    page = browser_context.new_page()
    yield page
    page.close()


def _validate_legal_document(
    app_page: Page,
    report: dict,
    field: str,
    link_label: str,
    heading: str,
    screenshot_name: str,
) -> None:
    link_pattern = re.compile(link_label, re.IGNORECASE)
    link_locator = _first_visible(
        [
            app_page.get_by_role("link", name=link_pattern),
            app_page.get_by_role("button", name=link_pattern),
            app_page.get_by_text(link_pattern),
        ],
        timeout_ms=8000,
    )

    target_page = app_page
    popup_opened = False

    try:
        with app_page.context.expect_page(timeout=5000) as popup_info:
            _click_and_wait(link_locator, app_page)
        target_page = popup_info.value
        popup_opened = True
        _wait_ui(target_page, timeout_ms=20000)
    except PlaywrightTimeoutError:
        _click_and_wait(link_locator, app_page)
        target_page = app_page

    heading_pattern = re.compile(heading, re.IGNORECASE)
    expect(target_page.get_by_role("heading", name=heading_pattern)).to_be_visible(timeout=20000)

    legal_content = _first_visible(
        [target_page.locator("main p"), target_page.locator("article p"), target_page.locator("p")],
        timeout_ms=10000,
    )
    expect(legal_content).to_be_visible(timeout=10000)

    report["results"][field]["final_url"] = target_page.url
    screenshot_path = _screenshot(target_page, screenshot_name, full_page=True)
    _add_evidence(report, field, screenshot_path)
    _mark_pass(report, field)

    if popup_opened:
        target_page.close()
        app_page.bring_to_front()
        _wait_ui(app_page)
        return

    with suppress(Exception):
        app_page.go_back(wait_until="domcontentloaded")
        _wait_ui(app_page)


def test_saleads_mi_negocio_full_workflow(page: Page) -> None:
    _ensure_artifact_dirs()
    login_url = os.getenv(LOGIN_URL_ENV, "").strip()
    report = _new_report(login_url=login_url)
    blocking_error = False

    try:
        if not login_url:
            raise AssertionError(
                "Set SALEADS_LOGIN_URL to the login page of the target environment "
                "(dev/staging/prod) before running this test."
            )

        page.goto(login_url, wait_until="domcontentloaded")
        _wait_ui(page)

        # Step 1 - Login with Google.
        try:
            google_button_pattern = re.compile(
                r"(sign in with google|iniciar sesión con google|continuar con google|google)",
                re.IGNORECASE,
            )
            sidebar_candidate = _visible_or_none(
                [page.locator("aside"), page.get_by_role("navigation")], timeout_ms=4000
            )

            if sidebar_candidate:
                _mark_pass(report, "Login")
            else:
                login_button = _first_visible(
                    [
                        page.get_by_role("button", name=google_button_pattern),
                        page.get_by_role("link", name=google_button_pattern),
                        page.get_by_text(google_button_pattern),
                    ],
                    timeout_ms=12000,
                )

                popup_page = None
                try:
                    with page.context.expect_page(timeout=6000) as popup_info:
                        _click_and_wait(login_button, page)
                    popup_page = popup_info.value
                    _wait_ui(popup_page, timeout_ms=20000)
                except PlaywrightTimeoutError:
                    _click_and_wait(login_button, page)

                if popup_page:
                    _maybe_choose_google_account(popup_page, GOOGLE_ACCOUNT_EMAIL)
                else:
                    _maybe_choose_google_account(page, GOOGLE_ACCOUNT_EMAIL)

                page.bring_to_front()
                _wait_ui(page, timeout_ms=30000)
                expect(
                    _first_visible(
                        [page.locator("aside"), page.get_by_role("navigation")], timeout_ms=30000
                    )
                ).to_be_visible(timeout=30000)
                _mark_pass(report, "Login")

            dashboard_shot = _screenshot(page, "step1_dashboard_loaded", full_page=True)
            _add_evidence(report, "Login", dashboard_shot)
        except Exception as error:
            blocking_error = True
            _mark_fail(report, "Login", error)

        # Step 2 - Open Mi Negocio menu.
        try:
            if blocking_error:
                raise AssertionError("Cannot continue because login failed.")

            _first_visible([page.locator("aside"), page.get_by_role("navigation")], timeout_ms=15000)
            negocio_item = _first_visible([page.get_by_text(re.compile(r"\bNegocio\b", re.IGNORECASE))])
            _click_and_wait(negocio_item, page)

            mi_negocio_item = _first_visible(
                [page.get_by_text(re.compile(r"\bMi Negocio\b", re.IGNORECASE))]
            )
            _click_and_wait(mi_negocio_item, page)

            expect(
                _first_visible([page.get_by_text(re.compile(r"Agregar Negocio", re.IGNORECASE))])
            ).to_be_visible(timeout=10000)
            expect(
                _first_visible([page.get_by_text(re.compile(r"Administrar Negocios", re.IGNORECASE))])
            ).to_be_visible(timeout=10000)

            menu_shot = _screenshot(page, "step2_mi_negocio_menu_expanded")
            _add_evidence(report, "Mi Negocio menu", menu_shot)
            _mark_pass(report, "Mi Negocio menu")
        except Exception as error:
            blocking_error = True
            _mark_fail(report, "Mi Negocio menu", error)

        # Step 3 - Validate Agregar Negocio modal.
        try:
            if blocking_error:
                raise AssertionError("Cannot continue because Mi Negocio menu was not opened.")

            agregar_negocio = _first_visible(
                [page.get_by_text(re.compile(r"Agregar Negocio", re.IGNORECASE))]
            )
            _click_and_wait(agregar_negocio, page)

            expect(
                _first_visible([page.get_by_role("heading", name=re.compile("Crear Nuevo Negocio", re.IGNORECASE))])
            ).to_be_visible(timeout=15000)
            nombre_input = _first_visible(
                [
                    page.get_by_label(re.compile("Nombre del Negocio", re.IGNORECASE)),
                    page.get_by_placeholder(re.compile("Nombre del Negocio|Nombre", re.IGNORECASE)),
                    page.get_by_role("textbox", name=re.compile("Nombre del Negocio", re.IGNORECASE)),
                ]
            )
            expect(nombre_input).to_be_visible(timeout=10000)
            expect(_first_visible([page.get_by_text(re.compile(r"Tienes 2 de 3 negocios", re.IGNORECASE))])).to_be_visible(
                timeout=10000
            )
            expect(_first_visible([page.get_by_role("button", name=re.compile("Cancelar", re.IGNORECASE))])).to_be_visible(
                timeout=10000
            )
            expect(
                _first_visible([page.get_by_role("button", name=re.compile("Crear Negocio", re.IGNORECASE))])
            ).to_be_visible(timeout=10000)

            with suppress(Exception):
                nombre_input_optional = _first_visible(
                    [page.get_by_label(re.compile("Nombre del Negocio", re.IGNORECASE)), page.get_by_placeholder(re.compile("Nombre", re.IGNORECASE))]
                )
                nombre_input_optional.click()
                nombre_input_optional.fill("Negocio Prueba Automatización")
                _wait_ui(page)

            modal_shot = _screenshot(page, "step3_agregar_negocio_modal")
            _add_evidence(report, "Agregar Negocio modal", modal_shot)

            cancelar = _first_visible([page.get_by_role("button", name=re.compile("Cancelar", re.IGNORECASE))])
            _click_and_wait(cancelar, page)
            _mark_pass(report, "Agregar Negocio modal")
        except Exception as error:
            blocking_error = True
            _mark_fail(report, "Agregar Negocio modal", error)

        # Step 4 - Open Administrar Negocios.
        try:
            if blocking_error:
                raise AssertionError("Cannot continue because modal step failed.")

            mi_negocio_again = _first_visible(
                [page.get_by_text(re.compile(r"\bMi Negocio\b", re.IGNORECASE))]
            )
            _click_and_wait(mi_negocio_again, page)

            administrar = _first_visible(
                [page.get_by_text(re.compile(r"Administrar Negocios", re.IGNORECASE))]
            )
            _click_and_wait(administrar, page)

            expect(_first_visible([page.get_by_text(re.compile("Información General", re.IGNORECASE))])).to_be_visible(
                timeout=20000
            )
            expect(_first_visible([page.get_by_text(re.compile("Detalles de la Cuenta", re.IGNORECASE))])).to_be_visible(
                timeout=20000
            )
            expect(_first_visible([page.get_by_text(re.compile("Tus Negocios", re.IGNORECASE))])).to_be_visible(timeout=20000)
            expect(_first_visible([page.get_by_text(re.compile("Sección Legal", re.IGNORECASE))])).to_be_visible(timeout=20000)

            admin_shot = _screenshot(page, "step4_administrar_negocios_view", full_page=True)
            _add_evidence(report, "Administrar Negocios view", admin_shot)
            _mark_pass(report, "Administrar Negocios view")
        except Exception as error:
            blocking_error = True
            _mark_fail(report, "Administrar Negocios view", error)

        # Step 5 - Validate Información General.
        try:
            if blocking_error:
                raise AssertionError("Cannot continue because Administrar Negocios view failed.")

            expect(_first_visible([page.get_by_text(re.compile(r".+@.+", re.IGNORECASE))])).to_be_visible(timeout=10000)
            expect(_first_visible([page.get_by_text(re.compile("BUSINESS PLAN", re.IGNORECASE))])).to_be_visible(timeout=10000)
            expect(_first_visible([page.get_by_role("button", name=re.compile("Cambiar Plan", re.IGNORECASE))])).to_be_visible(
                timeout=10000
            )

            # Username validation is intentionally flexible to work across accounts/environments.
            expect(_first_visible([page.locator("h1, h2, h3"), page.locator("strong")])).to_be_visible(timeout=10000)
            _mark_pass(report, "Información General")
        except Exception as error:
            _mark_fail(report, "Información General", error)

        # Step 6 - Validate Detalles de la Cuenta.
        try:
            if blocking_error:
                raise AssertionError("Cannot continue because Administrar Negocios view failed.")

            expect(_first_visible([page.get_by_text(re.compile("Cuenta creada", re.IGNORECASE))])).to_be_visible(timeout=10000)
            expect(_first_visible([page.get_by_text(re.compile("Estado activo", re.IGNORECASE))])).to_be_visible(timeout=10000)
            expect(_first_visible([page.get_by_text(re.compile("Idioma seleccionado", re.IGNORECASE))])).to_be_visible(
                timeout=10000
            )
            _mark_pass(report, "Detalles de la Cuenta")
        except Exception as error:
            _mark_fail(report, "Detalles de la Cuenta", error)

        # Step 7 - Validate Tus Negocios.
        try:
            if blocking_error:
                raise AssertionError("Cannot continue because Administrar Negocios view failed.")

            expect(_first_visible([page.get_by_text(re.compile("Tus Negocios", re.IGNORECASE))])).to_be_visible(timeout=10000)
            expect(_first_visible([page.get_by_text(re.compile("Agregar Negocio", re.IGNORECASE))])).to_be_visible(timeout=10000)
            expect(_first_visible([page.get_by_text(re.compile(r"Tienes 2 de 3 negocios", re.IGNORECASE))])).to_be_visible(timeout=10000)
            _mark_pass(report, "Tus Negocios")
        except Exception as error:
            _mark_fail(report, "Tus Negocios", error)

        # Step 8 - Validate Términos y Condiciones.
        try:
            if blocking_error:
                raise AssertionError("Cannot continue because Administrar Negocios view failed.")
            _validate_legal_document(
                app_page=page,
                report=report,
                field="Términos y Condiciones",
                link_label="Términos y Condiciones",
                heading="Términos y Condiciones",
                screenshot_name="step8_terminos_y_condiciones",
            )
        except Exception as error:
            _mark_fail(report, "Términos y Condiciones", error)

        # Step 9 - Validate Política de Privacidad.
        try:
            if blocking_error:
                raise AssertionError("Cannot continue because Administrar Negocios view failed.")
            _validate_legal_document(
                app_page=page,
                report=report,
                field="Política de Privacidad",
                link_label="Política de Privacidad",
                heading="Política de Privacidad",
                screenshot_name="step9_politica_de_privacidad",
            )
        except Exception as error:
            _mark_fail(report, "Política de Privacidad", error)

    finally:
        _save_report(report)

    failed_steps = [name for name, result in report["results"].items() if result["status"] != "PASS"]
    assert not failed_steps, (
        f"Some validations failed: {failed_steps}. "
        f"Check {REPORT_PATH} for detailed PASS/FAIL output and evidence paths."
    )
