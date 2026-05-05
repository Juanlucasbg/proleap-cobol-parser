import json
import os
import re
import time
from datetime import datetime, timezone
from pathlib import Path

import pytest
from playwright.sync_api import (
    BrowserContext,
    Locator,
    Page,
    TimeoutError as PlaywrightTimeoutError,
    expect,
    sync_playwright,
)


ARTIFACTS_DIR = Path(__file__).parent / "artifacts"
SCREENSHOTS_DIR = ARTIFACTS_DIR / "screenshots"
REPORT_PATH = ARTIFACTS_DIR / "final_report.json"

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


def env_bool(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "y", "on"}


def wait_for_ui_ready(page: Page) -> None:
    try:
        page.wait_for_load_state("domcontentloaded", timeout=15000)
    except PlaywrightTimeoutError:
        pass

    # Many SPA apps keep background requests alive; networkidle can timeout.
    try:
        page.wait_for_load_state("networkidle", timeout=5000)
    except PlaywrightTimeoutError:
        pass

    page.wait_for_timeout(800)


def take_screenshot(page: Page, name: str, full_page: bool = False) -> str:
    SCREENSHOTS_DIR.mkdir(parents=True, exist_ok=True)
    target = SCREENSHOTS_DIR / f"{name}.png"
    page.screenshot(path=str(target), full_page=full_page)
    return str(target)


def is_visible(locator: Locator, timeout: int = 10000) -> bool:
    try:
        expect(locator).to_be_visible(timeout=timeout)
        return True
    except AssertionError:
        return False


def any_text_visible(page: Page, patterns: list[str], timeout: int = 10000) -> bool:
    for text_pattern in patterns:
        if is_visible(page.get_by_text(re.compile(text_pattern, re.IGNORECASE)).first, timeout=timeout):
            return True
    return False


def find_clickable_by_text(page: Page, patterns: list[str], timeout: int = 15000) -> Locator:
    deadline = time.time() + (timeout / 1000)

    while time.time() < deadline:
        for text_pattern in patterns:
            regex = re.compile(text_pattern, re.IGNORECASE)
            candidates = [
                page.get_by_role("button", name=regex).first,
                page.get_by_role("link", name=regex).first,
                page.get_by_text(regex).first,
            ]

            for locator in candidates:
                try:
                    if locator.count() > 0 and locator.is_visible():
                        return locator
                except Exception:
                    continue
        page.wait_for_timeout(300)

    joined = ", ".join(patterns)
    raise AssertionError(f"No clickable element found for patterns: {joined}")


def click_and_wait(page: Page, locator: Locator) -> None:
    locator.click()
    wait_for_ui_ready(page)


def click_google_login_and_select_account(page: Page, context: BrowserContext) -> None:
    login_button = find_clickable_by_text(
        page,
        [
            r"Sign\s*in\s*with\s*Google",
            r"Iniciar\s*sesi[oó]n\s*con\s*Google",
            r"Ingresar\s*con\s*Google",
            r"Continuar\s*con\s*Google",
            r"Google",
        ],
    )

    auth_page = page
    opened_new_tab = False
    try:
        with context.expect_page(timeout=6000) as new_page_info:
            login_button.click()
        auth_page = new_page_info.value
        opened_new_tab = True
        wait_for_ui_ready(auth_page)
    except PlaywrightTimeoutError:
        click_and_wait(page, login_button)
        auth_page = page

    account_option = auth_page.get_by_text(
        re.compile(r"juanlucasbarbiergarzon@gmail\.com", re.IGNORECASE)
    ).first
    if account_option.count() > 0 and account_option.is_visible():
        click_and_wait(auth_page, account_option)

    # The authenticated app is expected on the original page.
    page.bring_to_front()
    wait_for_ui_ready(page)

    if opened_new_tab:
        try:
            auth_page.wait_for_timeout(500)
            if not auth_page.is_closed():
                auth_page.close()
        except Exception:
            pass


def ensure_menu_open(page: Page) -> None:
    if any_text_visible(page, [r"Agregar\s+Negocio", r"Administrar\s+Negocios"], timeout=1500):
        return

    mi_negocio = find_clickable_by_text(page, [r"Mi\s+Negocio"])
    click_and_wait(page, mi_negocio)


def validate_legal_link(
    page: Page,
    context: BrowserContext,
    link_pattern: str,
    heading_pattern: str,
    screenshot_name: str,
) -> tuple[bool, str]:
    link = find_clickable_by_text(page, [link_pattern])

    opened_new_tab = False
    legal_page = page
    try:
        with context.expect_page(timeout=6000) as new_page_info:
            link.click()
        legal_page = new_page_info.value
        opened_new_tab = True
        wait_for_ui_ready(legal_page)
    except PlaywrightTimeoutError:
        click_and_wait(page, link)
        legal_page = page

    heading_ok = is_visible(legal_page.get_by_text(re.compile(heading_pattern, re.IGNORECASE)).first, timeout=20000)
    content_ok = False

    try:
        content_locator = legal_page.locator("p,li,article section,main p").first
        if content_locator.count() > 0:
            text = content_locator.inner_text().strip()
            content_ok = len(text) > 20
    except Exception:
        content_ok = False

    take_screenshot(legal_page, screenshot_name, full_page=True)
    final_url = legal_page.url

    if opened_new_tab:
        legal_page.close()
        page.bring_to_front()
        wait_for_ui_ready(page)
    else:
        try:
            page.go_back(wait_until="domcontentloaded", timeout=15000)
            wait_for_ui_ready(page)
        except PlaywrightTimeoutError:
            pass

    return heading_ok and content_ok, final_url


def test_saleads_mi_negocio_full_workflow() -> None:
    ARTIFACTS_DIR.mkdir(parents=True, exist_ok=True)
    SCREENSHOTS_DIR.mkdir(parents=True, exist_ok=True)

    step_results = {field: False for field in REPORT_FIELDS}
    evidence: dict[str, str] = {}
    legal_urls: dict[str, str] = {}
    errors: dict[str, str] = {}

    login_url = os.getenv("SALEADS_LOGIN_URL", "").strip()
    if not login_url:
        pytest.fail(
            "SALEADS_LOGIN_URL is required. Provide the login page URL for the current "
            "environment so the test can stay domain-agnostic."
        )

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(
            headless=env_bool("SALEADS_HEADLESS", False),
            slow_mo=int(os.getenv("SALEADS_SLOWMO_MS", "250")),
        )
        context = browser.new_context(viewport={"width": 1440, "height": 900})
        page = context.new_page()

        try:
            page.goto(login_url, wait_until="domcontentloaded")
            wait_for_ui_ready(page)

            # Step 1: Login with Google
            try:
                click_google_login_and_select_account(page=page, context=context)

                sidebar_ok = any_text_visible(page, [r"Negocio", r"Mi\s+Negocio"], timeout=30000)
                main_interface_ok = any_text_visible(
                    page, [r"Dashboard", r"Inicio", r"Panel", r"Administrar\s+Negocios"], timeout=30000
                ) or (
                    sidebar_ok
                    and not re.search(r"login|sign[\s-]?in", page.url, re.IGNORECASE)
                )
                step_results["Login"] = main_interface_ok and sidebar_ok
                evidence["dashboard"] = take_screenshot(page, "01_dashboard_loaded")
            except Exception as exc:
                errors["Login"] = str(exc)

            # Step 2: Open Mi Negocio menu
            try:
                negocio_section = find_clickable_by_text(page, [r"Negocio"])
                click_and_wait(page, negocio_section)

                mi_negocio = find_clickable_by_text(page, [r"Mi\s+Negocio"])
                click_and_wait(page, mi_negocio)

                agregar_visible = any_text_visible(page, [r"Agregar\s+Negocio"])
                administrar_visible = any_text_visible(page, [r"Administrar\s+Negocios"])
                submenu_expanded = agregar_visible and administrar_visible

                step_results["Mi Negocio menu"] = submenu_expanded
                evidence["mi_negocio_menu"] = take_screenshot(page, "02_mi_negocio_menu_expanded")
            except Exception as exc:
                errors["Mi Negocio menu"] = str(exc)

            # Step 3: Validate Agregar Negocio modal
            try:
                agregar_negocio = find_clickable_by_text(page, [r"Agregar\s+Negocio"])
                click_and_wait(page, agregar_negocio)

                modal_title_ok = any_text_visible(page, [r"Crear\s+Nuevo\s+Negocio"], timeout=15000)
                negocio_name_input_ok = any(
                    is_visible(locator.first, timeout=5000)
                    for locator in [
                        page.get_by_label(re.compile(r"Nombre\s+del\s+Negocio", re.IGNORECASE)),
                        page.get_by_placeholder(re.compile(r"Nombre\s+del\s+Negocio", re.IGNORECASE)),
                        page.get_by_role("textbox", name=re.compile(r"Nombre\s+del\s+Negocio", re.IGNORECASE)),
                    ]
                )
                quota_ok = any_text_visible(page, [r"Tienes\s+2\s+de\s+3\s+negocios"])
                cancel_ok = is_visible(page.get_by_role("button", name=re.compile(r"Cancelar", re.IGNORECASE)).first)
                create_ok = is_visible(
                    page.get_by_role("button", name=re.compile(r"Crear\s+Negocio", re.IGNORECASE)).first
                )

                if negocio_name_input_ok:
                    try:
                        textbox = page.get_by_label(
                            re.compile(r"Nombre\s+del\s+Negocio", re.IGNORECASE)
                        ).first
                        if not textbox.is_visible():
                            textbox = page.get_by_placeholder(
                                re.compile(r"Nombre\s+del\s+Negocio", re.IGNORECASE)
                            ).first
                        textbox.fill("Negocio Prueba Automatización")
                    except Exception:
                        pass

                evidence["agregar_negocio_modal"] = take_screenshot(page, "03_agregar_negocio_modal")

                cancel_button = page.get_by_role("button", name=re.compile(r"Cancelar", re.IGNORECASE)).first
                if cancel_button.count() > 0 and cancel_button.is_visible():
                    click_and_wait(page, cancel_button)

                step_results["Agregar Negocio modal"] = (
                    modal_title_ok and negocio_name_input_ok and quota_ok and cancel_ok and create_ok
                )
            except Exception as exc:
                errors["Agregar Negocio modal"] = str(exc)

            # Step 4: Open Administrar Negocios view
            try:
                ensure_menu_open(page)
                administrar_negocios = find_clickable_by_text(page, [r"Administrar\s+Negocios"])
                click_and_wait(page, administrar_negocios)

                info_general_ok = any_text_visible(page, [r"Informaci[oó]n\s+General"], timeout=20000)
                detalles_ok = any_text_visible(page, [r"Detalles\s+de\s+la\s+Cuenta"], timeout=20000)
                tus_negocios_ok = any_text_visible(page, [r"Tus\s+Negocios"], timeout=20000)
                legal_ok = any_text_visible(page, [r"Secci[oó]n\s+Legal"], timeout=20000)

                step_results["Administrar Negocios view"] = info_general_ok and detalles_ok and tus_negocios_ok and legal_ok
                evidence["administrar_negocios"] = take_screenshot(page, "04_administrar_negocios", full_page=True)
            except Exception as exc:
                errors["Administrar Negocios view"] = str(exc)

            # Step 5: Validate Información General
            try:
                name_visible = any_text_visible(page, [r"Nombre", r"Usuario"])
                email_visible = is_visible(
                    page.locator(
                        "text=/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/"
                    ).first,
                    timeout=10000,
                )
                business_plan_visible = any_text_visible(page, [r"BUSINESS\s+PLAN"])
                change_plan_visible = is_visible(
                    page.get_by_role("button", name=re.compile(r"Cambiar\s+Plan", re.IGNORECASE)).first,
                    timeout=10000,
                )
                step_results["Información General"] = (
                    name_visible and email_visible and business_plan_visible and change_plan_visible
                )
            except Exception as exc:
                errors["Información General"] = str(exc)

            # Step 6: Validate Detalles de la Cuenta
            try:
                created_visible = any_text_visible(page, [r"Cuenta\s+creada"])
                active_status_visible = any_text_visible(page, [r"Estado\s+activo", r"Activo"])
                language_visible = any_text_visible(page, [r"Idioma\s+seleccionado"])
                step_results["Detalles de la Cuenta"] = created_visible and active_status_visible and language_visible
            except Exception as exc:
                errors["Detalles de la Cuenta"] = str(exc)

            # Step 7: Validate Tus Negocios
            try:
                business_list_visible = any_text_visible(page, [r"Tus\s+Negocios"]) and (
                    page.locator("ul li, table tbody tr, [role='row']").count() > 0
                )
                add_business_button_visible = is_visible(
                    page.get_by_role("button", name=re.compile(r"Agregar\s+Negocio", re.IGNORECASE)).first,
                    timeout=10000,
                )
                quota_visible = any_text_visible(page, [r"Tienes\s+2\s+de\s+3\s+negocios"])
                step_results["Tus Negocios"] = business_list_visible and add_business_button_visible and quota_visible
            except Exception as exc:
                errors["Tus Negocios"] = str(exc)

            # Step 8: Validate Términos y Condiciones
            try:
                terms_ok, terms_url = validate_legal_link(
                    page=page,
                    context=context,
                    link_pattern=r"T[eé]rminos\s+y\s+Condiciones",
                    heading_pattern=r"T[eé]rminos\s+y\s+Condiciones",
                    screenshot_name="08_terminos_y_condiciones",
                )
                step_results["Términos y Condiciones"] = terms_ok
                legal_urls["Términos y Condiciones"] = terms_url
            except Exception as exc:
                errors["Términos y Condiciones"] = str(exc)

            # Step 9: Validate Política de Privacidad
            try:
                privacy_ok, privacy_url = validate_legal_link(
                    page=page,
                    context=context,
                    link_pattern=r"Pol[ií]tica\s+de\s+Privacidad",
                    heading_pattern=r"Pol[ií]tica\s+de\s+Privacidad",
                    screenshot_name="09_politica_de_privacidad",
                )
                step_results["Política de Privacidad"] = privacy_ok
                legal_urls["Política de Privacidad"] = privacy_url
            except Exception as exc:
                errors["Política de Privacidad"] = str(exc)

        finally:
            context.close()
            browser.close()

    report = {
        "name": "saleads_mi_negocio_full_test",
        "timestamp_utc": datetime.now(timezone.utc).isoformat(),
        "environment_note": "Domain-agnostic execution. Target environment is provided via SALEADS_LOGIN_URL.",
        "results": {
            field: {"status": "PASS" if step_results[field] else "FAIL"} for field in REPORT_FIELDS
        },
        "errors": errors,
        "evidence": evidence,
        "captured_urls": legal_urls,
    }
    REPORT_PATH.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")

    failed_steps = [field for field, status in step_results.items() if not status]
    if failed_steps:
        pytest.fail(
            "SaleADS Mi Negocio workflow validations failed for: "
            + ", ".join(failed_steps)
            + f". Review report at {REPORT_PATH}"
        )
